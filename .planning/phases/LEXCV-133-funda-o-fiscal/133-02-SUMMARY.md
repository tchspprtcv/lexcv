---
phase: 133-funda-o-fiscal
plan: 02
subsystem: backend-fiscal
tags: [fiscal, parametros, vigencia, seeder, rbac, testcontainers]
requires:
  - "133-01: ParametroFiscal entity, ParametroFiscalRepository (vigência finder), RecusaFiscalException, Clock bean"
provides:
  - "CodigoParametroFiscal { IVA_TAXA_NORMAL, RETENCAO_SUGERIDA } (persisted as name())"
  - "ParametroFiscalService.valorVigente(codigo, data) / valorVigenteHoje(codigo) - percentage values; 503 PARAMETRO_FISCAL_EM_FALTA when no row applies"
  - "DatabaseSeeder.seedParametrosFiscais(): unconditional, non-destructive insert of IVA 15 / retenção 20 @2000-01-01"
  - "ParametrosFiscaisSemConstantesTest: build-failing grep gate for rate literals in src/main/java"
  - "Demo tenant NIF 500000001; financeiro:manage label 'Gerir Faturação e Eliminar Lançamentos'"
affects: [133-04, 133-05, 134, 136]
tech-stack:
  added: []
  patterns:
    - "Rates as dated rows read through one service; legal change = new row with later vigente_desde"
    - "Cabo Verde civil date via LocalDate.now(clock.withZone(Atlantic/Cape_Verde))"
    - "Source-tree grep gate test (Files.walk over src/main/java)"
key-files:
  created:
    - backend/src/main/java/com/lexcv/models/CodigoParametroFiscal.java
    - backend/src/main/java/com/lexcv/services/fiscal/ParametroFiscalService.java
    - backend/src/test/java/com/lexcv/services/fiscal/ParametroFiscalServiceTest.java
    - backend/src/test/java/com/lexcv/repositories/ParametroFiscalRepositoryIT.java
    - backend/src/test/java/com/lexcv/seed/DatabaseSeederParametrosFiscaisTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/ParametrosFiscaisSemConstantesTest.java
  modified:
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
    - backend/src/test/java/com/lexcv/seed/DatabaseSeederCatalogoPermissoesTest.java
    - backend/src/test/java/com/lexcv/seed/DatabaseSeederInstanciabilidadeMoldesTest.java
    - backend/src/test/java/com/lexcv/seed/DatabaseSeederPlataformaAdminTest.java
decisions:
  - "Fiscal rates are percentages stored in t_parametro_fiscal (IVA_TAXA_NORMAL 15, RETENCAO_SUGERIDA 20, vigente_desde 2000-01-01 as technical start); seeded on every boot, insert-only, never updated"
  - "ParametroFiscalService is the single reader; missing row -> RecusaFiscalException 503 PARAMETRO_FISCAL_EM_FALTA"
  - "Demo tenant NIF changed to 500000001 (000000000 fails the fiscal NIF rule)"
metrics:
  duration: "~8 min"
  completed: 2026-10-04
  tasks: 2
  files: 10
---

# Phase 133 Plan 02: Fiscal parameters with vigência Summary

VAT (15) and suggested withholding (20) are now dated rows in `t_parametro_fiscal`, created on every boot only when missing. `ParametroFiscalService` returns the value in force on a date, using the Cabo Verde civil date for "today". A source-tree test fails the build if a rate literal shows up in production code. The demo tenant now has a valid NIF, and the `financeiro:manage` label says it also covers fiscal configuration.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | CodigoParametroFiscal + ParametroFiscalService + repository IT (TDD) | a45639f (RED), 4361cf7 (GREEN) |
| 2 | Seeder upsert, demo NIF, financeiro:manage label, no-constant gate (TDD) | 9c0e95c (RED), bd90e71 (GREEN) |

## Verification

- `ParametroFiscalServiceTest`: 4/4 pass. Covers the exact codigo/date passed to the repository, 503 `PARAMETRO_FISCAL_EM_FALTA`, and Clock.fixed at 00:30Z and 01:30Z giving 2026-12-31 and 2027-01-01.
- `ParametroFiscalRepositoryIT`: run for real against `postgres:16-alpine` via failsafe, 2/2 pass. Rows 15 @2000-01-01 and 16 @2030-01-01: a query on 2029-12-31 returns 15, on 2030-01-01 returns 16, on 1999-12-31 returns nothing. A duplicate (codigo, vigente_desde) raises `DataIntegrityViolationException`. The logged "duplicate key" ERROR line is expected.
- `DatabaseSeeder*Test` + `ParametrosFiscaisSemConstantesTest`: 20/20 pass.
- Full unit suite `mvn test`: 459 tests, 0 failures, 0 errors. `UserPrincipalCatalogoSyncTest` and `AdminControllerRbacCatalogoTest` are still green.
- `mvn spotbugs:check`: clean.
- Acceptance greps: `seedParametrosFiscais();` is on line 52, right after `seedRbac();` (51). No `"000000000"` literal remains (only a comment mentions it). `"500000001"` and `Gerir Faturação e Eliminar Lançamentos` are present. There are 4 `ParametroFiscalRepository parametroFiscalRepository` mocks across the seeder tests. `grep -rnE '\b0\.(15|20)\b' backend/src/main/java` returns nothing. The service contains `Atlantic/Cape_Verde`, `PARAMETRO_FISCAL_EM_FALTA` and the derived finder, and no bare `LocalDate.now()`.

## Deviations from Plan

None. The plan ran as written.

Notes:
- **TDD RED for the grep gate:** `ParametrosFiscaisSemConstantesTest` passed as soon as it was written, because production code had no rate literals. It is a regression guard, not a test of new behaviour. The RED gate for Task 2 came from `DatabaseSeederParametrosFiscaisTest`, whose 2 tests failed before the seeder change. The gate also checks that it scans more than 10 files, so a wrong working directory cannot make it pass by finding nothing.
- `AdminControllerRbacCatalogoTest:164` still uses the old `financeiro:manage` label inside a mocked fixture. That fixture does not depend on the seeder catalogue, so it was left unchanged.
- Environment: all Maven runs used `-Dmaven.compiler.release=21`, and the IT used `~/.docker-java.properties` (api.version=1.44), as in 133-01.

## Known Stubs

None.

## Threat Flags

None. No new endpoint and no new write path. T-133-06, T-133-07 and T-133-08 are mitigated as planned; T-133-09 is accepted and the label was updated.

## TDD Gate Compliance

Both tasks have a `test(...)` RED commit before the `feat(...)` GREEN commit: a45639f before 4361cf7, and 9c0e95c before bd90e71.

## Self-Check: PASSED
