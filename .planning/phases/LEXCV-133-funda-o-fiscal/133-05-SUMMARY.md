---
phase: 133-funda-o-fiscal
plan: 05
subsystem: backend-api
tags: [faturacao, controller, rbac, financeiro-manage, multi-tenant, cfg-03]
requires:
  - phase: 133-01
    provides: RecusaFiscalException + global handler, fiscal entities
  - phase: 133-04
    provides: ConfiguracaoFiscalService, fiscal DTOs
provides:
  - "FaturacaoController: GET/PUT /api/v1/faturacao/configuracao, POST /ativar, POST /desativar, PUT /email-automatico, GET /series, GET /motivos-isencao"
  - "CFG-03 regression guard (FaturacaoDesligadaPagamentoInalteradoTest)"
affects: [133-08, 134]
tech-stack:
  added: []
  patterns:
    - "Dedicated controller with a single class-level @PreAuthorize gate (no method-level override)"
    - "Source-text regression guard for an untouched code path"
key-files:
  created:
    - backend/src/main/java/com/lexcv/controllers/FaturacaoController.java
    - backend/src/test/java/com/lexcv/controllers/FaturacaoControllerTest.java
    - backend/src/test/java/com/lexcv/controllers/FaturacaoControllerAutorizacaoTest.java
    - backend/src/test/java/com/lexcv/controllers/FaturacaoDesligadaPagamentoInalteradoTest.java
  modified: []
decisions:
  - "FaturacaoController is gated only at class level by hasAuthority('financeiro:manage'); no permission was added, and handlers never take UUID, path variables or query params"
  - "Phase 133 adds no fiscal path to payment registration; FaturacaoDesligadaPagamentoInalteradoTest blocks fiscal symbols in ResourceController/Pagamento until Phase 134 changes it on purpose"
metrics:
  duration: 12 min
  completed: 2026-10-04
  tasks: 2
  files: 4
---

# Phase 133 Plan 05: FaturacaoController and CFG-03 Guard Summary

The seven fiscal configuration endpoints now live in `FaturacaoController` under `/api/v1/faturacao`. Each handler is a one-line call into `ConfiguracaoFiscalService`. The whole class is gated by `hasAuthority('financeiro:manage')` and the tenant always comes from the authenticated principal. A source-text guard proves that payment registration (CFG-03) is untouched.

## Tasks

| Task | Name | Commits | Files |
|------|------|---------|-------|
| 1 | FaturacaoController with class-level financeiro:manage gate (TDD) | 578d1af (RED), 7bbcacb (GREEN) | FaturacaoController.java, FaturacaoControllerTest.java, FaturacaoControllerAutorizacaoTest.java |
| 2 | CFG-03 regression guard and full backend gate | fe277af | FaturacaoDesligadaPagamentoInalteradoTest.java |

## What was built

- **FaturacaoController**: matches the Plan 06 hook contract exactly (7 handlers). There is no `@Transactional` because the service owns the transaction and turns refusals into exceptions, which the global handler maps to `{message, code, campo?}`. The two PUT bodies are `@Valid @RequestBody` records.
- **FaturacaoControllerTest** (13 tests): checks delegation to obter/guardar/ativar/desativar/definirEmailAutomatico/listarSeries with the principal's tenant and author. `motivos-isencao` returns 21 entries starting with code "1". A `RecusaFiscalException` from the service passes through unchanged. Reflection guards check that there are exactly 7 handlers, none takes a UUID, `@PathVariable` or `@RequestParam`, the only `@PreAuthorize` is the class one with the exact value, no path contains "audit", and the PUT bodies carry `@Valid` + `@RequestBody`.
- **FaturacaoControllerAutorizacaoTest** (8 tests, real `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` proxy): every handler is refused with no service call for financeiro:view+edit, `ROLE_financeiro:manage` (the hasRole trap), the seeded ADVOGADO / TECNICO / ASSISTENTE defaults, PLATAFORMA_ADMIN, and bare ROLE_ADMIN. With financeiro:manage, all 7 handlers go through with the principal's tenant.
- **FaturacaoDesligadaPagamentoInalteradoTest**: `ResourceController.java` and `Pagamento.java` contain none of `ConfiguracaoFiscal`, `NumeracaoService`, `SerieFiscal`, `faturacao`, `Faturacao`. Neither file has changed since Phase 128 (`git log`).

## Verification

- `mvn -q test -Dtest='FaturacaoController*Test,AuditLogImutabilidadeTest'`: 13 + 8 + 6 passed.
- `mvn test`: **543 tests, 0 failures**.
- `mvn -DskipTests compile spotbugs:check`: no bugs.
- `mvn verify` (Docker available, Testcontainers): **51 ITs executed, 0 failures**, including MigracaoFiscal133IT (7), ParametroFiscalRepositoryIT (2) and NumeracaoServiceConcorrenciaIT (9).
- Acceptance greps: 1 `@PreAuthorize`, 7 mappings, 2 `@Valid @RequestBody`, no `@PathVariable`/`@RequestParam`. `git diff` on ResourceController/Pagamento is empty.
- Every mvn command was run with `-Dmaven.compiler.release=21`.

## Deviations from Plan

**1. [Rule 3 - Blocking] Reworded javadoc so the acceptance greps hold.** The first javadoc used the literal annotation names, so `grep -c '@PreAuthorize'` returned 2 and the `@PathVariable|@RequestParam` grep matched. I reworded it with the same meaning. Fixed before the GREEN commit 7bbcacb.

Otherwise the plan ran as written. `RecusaFiscalException` is in `com.lexcv.exceptions`, not `services.fiscal`, and the test imports were adjusted to match.

## Requirements

- **CFG-01** is complete: the backend endpoint (here), service validation (133-04) and UI form (133-07) are all in place.
- **CFG-02, CFG-06**: the backend is complete. They stay Pending until 133-08 adds the activation/email cards.
- **CFG-03**: the guard is in place, but it is a source-level guard, not an end-to-end proof. 133-08 lists CFG-03 for end-to-end verification, so it stays Pending.

## Threat surface

All registered threats were handled as planned: T-133-21 by the class gate with proxy and reflection tests, T-133-22 by taking the tenant only from the principal (reflection test), T-133-23 by the `@Valid` records, and T-133-25 by the guard test. No new surface beyond the plan.

## Known Stubs

None.

## TDD Gate Compliance

RED `test(133-05)` 578d1af, then GREEN `feat(133-05)` 7bbcacb. No refactor was needed.

## Self-Check: PASSED

- All 4 created files exist; commits 578d1af, 7bbcacb and fe277af are in `git log`.
