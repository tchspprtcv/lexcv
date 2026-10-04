---
phase: 133-funda-o-fiscal
plan: 01
subsystem: backend-fiscal
tags: [fiscal, efatura, jpa, migration, testcontainers]
requires: []
provides:
  - "Enums RegimeIva, TipoDocumentoFiscal, AmbienteFiscal (SIMULADO only), MotivoIsencaoIva (21 official codes)"
  - "Entities ConfiguracaoFiscal (completa(), NIF_FISCAL_REGEX), ParametroFiscal, SerieFiscal (gerarCodigo)"
  - "ConfiguracaoFiscalRepository, ParametroFiscalRepository, SerieFiscalRepository (bloquear, criarSeNaoExiste, definirLockTimeoutLocal, existsByTenantIdAndUltimoNumeroGreaterThan)"
  - "RecusaFiscalException + GlobalExceptionHandler mapping {message, code, campo?}"
  - "ClockConfig: Clock bean (UTC)"
  - "backend/migrations/133-create-fiscal-foundation-tables.sql + README row 19"
affects: [133-02, 133-03, 133-04, 133-05, 134, 136]
tech-stack:
  added: []
  patterns:
    - "Enum columns via @Convert + AttributeConverter (not @Enumerated) to avoid Hibernate 6.6 CHECK constraints"
    - "Native INSERT ... ON CONFLICT DO NOTHING for race-free series creation"
    - "set_config('lock_timeout','5s',true) as SET LOCAL equivalent"
key-files:
  created:
    - backend/src/main/java/com/lexcv/models/RegimeIva.java
    - backend/src/main/java/com/lexcv/models/TipoDocumentoFiscal.java
    - backend/src/main/java/com/lexcv/models/AmbienteFiscal.java
    - backend/src/main/java/com/lexcv/models/MotivoIsencaoIva.java
    - backend/src/main/java/com/lexcv/models/RegimeIvaConverter.java
    - backend/src/main/java/com/lexcv/models/TipoDocumentoFiscalConverter.java
    - backend/src/main/java/com/lexcv/models/AmbienteFiscalConverter.java
    - backend/src/main/java/com/lexcv/models/ConfiguracaoFiscal.java
    - backend/src/main/java/com/lexcv/models/ParametroFiscal.java
    - backend/src/main/java/com/lexcv/models/SerieFiscal.java
    - backend/src/main/java/com/lexcv/exceptions/RecusaFiscalException.java
    - backend/src/main/java/com/lexcv/config/ClockConfig.java
    - backend/src/main/java/com/lexcv/repositories/ConfiguracaoFiscalRepository.java
    - backend/src/main/java/com/lexcv/repositories/ParametroFiscalRepository.java
    - backend/src/main/java/com/lexcv/repositories/SerieFiscalRepository.java
    - backend/migrations/133-create-fiscal-foundation-tables.sql
    - backend/src/test/java/com/lexcv/models/MotivoIsencaoIvaTest.java
    - backend/src/test/java/com/lexcv/models/SerieFiscalCodigoTest.java
    - backend/src/test/java/com/lexcv/config/GlobalExceptionHandlerRecusaFiscalTest.java
    - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal133IT.java
  modified:
    - backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java
    - backend/migrations/README.md
decisions:
  - "Fiscal enum columns use @Convert(AttributeConverter) instead of @Enumerated(STRING): Hibernate 6.6 generated 3 CHECK constraints (proven by IT), which would force DROP CONSTRAINT for every new enum value (P-15)"
  - "Backend Maven runs in this container need -Dmaven.compiler.release=21 (only JDK 21 installed; pom declares 23) - command line only, pom untouched (v2.18 precedent)"
  - "Testcontainers 1.20.4 vs Docker 29 (min API 1.40): run ITs with ~/.docker-java.properties api.version=1.44 (environment-only, not committed)"
metrics:
  duration: "~10 min"
  completed: 2026-10-04
  tasks: 3
  files: 22
---

# Phase 133 Plan 01: Fundação Fiscal data foundation Summary

Fiscal data foundation: 4 PT-labelled enums (incl. the 21 official eFatura TaxExemptionReasonCode values), 3 entities (`t_configuracao_fiscal` 1:1 tenant, global `t_parametro_fiscal`, `t_serie_fiscal` keyed by tenant+tipo+ano+ambiente) with tenant-scoped repositories (pessimistic lock, `ON CONFLICT DO NOTHING` creation, transaction-local lock timeout), `RecusaFiscalException` mapped to `{message, code, campo?}`, a UTC `Clock` bean, and an idempotent manual migration script proven column-for-column equal to the Hibernate schema against real PostgreSQL.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | Fiscal enums, RecusaFiscalException + handler, Clock bean (TDD) | 80e1a9e (RED), a2565c2 (GREEN) |
| 2 | Entities + repositories (TDD) | f4dd269 (RED), 7e89d22 (GREEN) |
| 3 | Migration script, README row, schema-parity IT | 300d8b3 |

## Verification

- `MotivoIsencaoIvaTest` (7), `GlobalExceptionHandlerRecusaFiscalTest` (3), `SerieFiscalCodigoTest` (9): green.
- `MigracaoFiscal133IT` executed for real against `postgres:16-alpine` via failsafe: **7/7 pass** (column parity incl. type/length/precision/scale/datetime precision/nullability; UNIQUE column-set parity; idempotent second run; zero CHECK constraints on enum tables; same series key in one tenant refused, in two tenants allowed; two configurations for one tenant refused; repository smoke test of `criarSeNaoExiste` x2 -> 1/0, `definirLockTimeoutLocal` -> "5s", `bloquear`, the issued-documents gate and the ordered list).
- Full unit suite `mvn test`: 451 tests, 0 failures, 0 errors.
- `mvn spotbugs:check`: clean.
- Acceptance greps: 21 `M<n>("` constants; RecusaFiscal handler at line 110 < catch-all at 122; `Clock.systemUTC()` present; 3 named UNIQUE constraints; no `tenant_id` in ParametroFiscal; `PESSIMISTIC_WRITE`, `ON CONFLICT (tenant_id, tipo_documento, ano, ambiente) DO NOTHING`, `set_config('lock_timeout'` present; `Tenant.java` untouched; script has 3 `CREATE TABLE IF NOT EXISTS` and no CHECK outside comments; README mentions the script 4 times and says "9 of 19".

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug, plan-anticipated fallback] Hibernate 6.6 generates CHECK constraints for @Enumerated columns**
- **Found during:** Task 3 (IT Test 3 returned 3 CHECK constraints instead of 0)
- **Fix:** Added `RegimeIvaConverter`, `TipoDocumentoFiscalConverter`, `AmbienteFiscalConverter` (`AttributeConverter<Enum,String>`, `name()`/`valueOf()`, null-safe) in `com.lexcv.models`; replaced `@Enumerated(EnumType.STRING)` with `@Convert(converter = ...)` on the three fields, keeping the same column definitions. JPQL `bloquear` with enum parameters still works (proven by the IT smoke test).
- **Files:** the 3 converters, ConfiguracaoFiscal.java, SerieFiscal.java
- **Commit:** 300d8b3

**2. [Rule 2 - Missing coverage] Repository smoke test added to MigracaoFiscal133IT**
- The native SQL in `SerieFiscalRepository` (`criarSeNaoExiste`, `definirLockTimeoutLocal`) and the JPQL `bloquear` were otherwise unexercised against a real schema until Plan 03; one extra IT method covers them.
- **Commit:** 300d8b3

**3. [Rule 2] README Path A skip bullet for 133** — added alongside the required rows so the fresh-install path lists every script, per the "scan the rest of README" instruction.

**4. [Rule 3 - Blocking, environment only] JDK / Docker API mismatches**
- Only JDK 21 is installed while `pom.xml` declares 23: all Maven runs used `-Dmaven.compiler.release=21` on the command line (v2.18 precedent; pom unchanged).
- Testcontainers 1.20.4 negotiates Docker API 1.32 but the daemon (Docker 29.6.2) requires >= 1.40, so the IT initially failed with "client version 1.32 is too old". Fixed in the environment only by writing `~/.docker-java.properties` with `api.version=1.44` (outside the repo, not committed). Anyone running ITs against Docker >= 29 needs the same, or a Testcontainers upgrade (deferred, not in scope).

## Notes for Downstream Plans

- `createdAt` has no `@PrePersist`: services must set it from the injected `Clock` (the native insert uses `now()`).
- `ConfiguracaoFiscal.NIF_FISCAL_REGEX` and `MORADA_MAX` are public for the Plan 04 DTO.
- Existing `Tenant.plano` (`@Enumerated`) very likely has the same Hibernate CHECK on dev databases created by `ddl-auto`; out of scope, not touched.

## Known Stubs

None. `led_codigo` is intentionally nullable until Phase 136 (documented in the entity and script).

## Threat Flags

None - no new endpoints; all surface is covered by T-133-01..05.

## TDD Gate Compliance

Task 1 and Task 2: `test(...)` RED commits (80e1a9e, f4dd269) precede `feat(...)` GREEN commits (a2565c2, 7e89d22). RED was verified as a compilation failure (missing types).

## Self-Check: PASSED
