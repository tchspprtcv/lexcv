---
phase: 135-nota-de-cr-dito
plan: 08
subsystem: backend-financeiro
tags: [kpi, estorno, nota-de-credito, cfg-03, alerts, tdd]
requires:
  - "135-04: DocumentoFiscalService.estornosPorPagamento / eEstornoDeNotaCredito, PagamentoComDocumentoResponse 3-arg factory"
provides:
  - "com.lexcv.services.RecebidoNoMes: FUSO_CABO_VERDE + somar(Collection<Pagamento>, YearMonth)"
  - "GET /dashboard valores_recebidos_mes by year+month in Atlantic/Cape_Verde, null-safe, estorno subtracts"
  - "GET /honorarios/{id}/pagamentos: estorno ref per payment"
  - "DELETE /pagamentos/{id}: 409 PAGAMENTO_ESTORNO for an NC estorno"
affects: [135-07, 135-11, 135-12]
tech-stack:
  added: []
  patterns:
    - "Pure static month-sum shared by the controller KPI and the coherence IT"
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/RecebidoNoMes.java
    - backend/src/test/java/com/lexcv/services/RecebidoNoMesTest.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerDashboardKpiTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerListaPagamentosTest.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerDocumentoFiscalGuardasTest.java
    - backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java
decisions:
  - "The estorno guard short-circuits before existeParaPagamento (the test verifies the FR probe is not even called)"
  - "RecebidoNoMes.somar also tolerates null elements, a null collection or a null month (returns 0), so the KPI can never NPE"
metrics:
  duration: "~15 min"
  completed: 2026-10-05
  tasks: 2
  files: 8
---

# Phase 135 Plan 08: KPI, estorno visibility/guard and alert re-eligibility Summary

This plan makes the remaining "pago" readers agree with the NC estorno.
- **Monthly KPI:** `calculateMensalReceived` now delegates to the pure `RecebidoNoMes.somar`. It counts the current year and month in Cabo Verde time, ignores null values, and lets a negative estorno subtract in the month of NC emission. The old code compared the month only.
- **Payments list:** each estorno shows its NC reference in `estorno`.
- **Delete guard:** deleting an estorno gets 409 `PAGAMENTO_ESTORNO` before any conta-corrente write.
- **Overdue alert:** the `HONORARIO_ATRASADO` re-eligibility after a partial NC is now pinned by tests, including the lifetime dedup. The job code is unchanged.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | RecebidoNoMes pure function and KPI fix in ResourceController | ce146cb (RED), 83abf04 (GREEN) |
| 2 | Estorno in payments list, estorno delete guard, alert re-eligibility test | 5adce0c (RED), a8c5734 (GREEN) |

## Verification

- `RecebidoNoMesTest`: 6 tests. `ResourceControllerDashboardKpiTest`: 3 tests. Both green, and they cover:
  - a payment of 500 today, one of 900 in the same month last year, and an estorno of -200 today give 300
  - null values and null dates are ignored
- Other suites, all green:
  - `ResourceControllerListaPagamentosTest`: 10 (3 new)
  - `ResourceControllerDocumentoFiscalGuardasTest`: 24 (4 new, including POST /pagamentos with a negative valor and billing off, which gets 400 with no save)
  - `AlertasDiariosJobTest`: 14 (3 new)
  - `ResourceControllerPagamentoTest`: 15
  - `FaturacaoDesligadaPagamentoInalteradoTest` (CFG-03): 5. The test file is unchanged.
  - `ResourceControllerProveniencaPapelTest` and `ResourceControllerUploadDocumentoTest`: green
- Acceptance greps:
  - 0 `getMonthValue() == LocalDate.now().getMonthValue()`
  - `RecebidoNoMes.somar`, `estornosPorPagamento` and `"code", "PAGAMENTO_ESTORNO"` are present
  - ASCII `faturacao` tokens in ResourceController: 1, unchanged
  - forbidden fiscal tokens (`DocumentoFiscalRepository|ComunicacaoFiscal|NumeracaoService|SerieFiscal|ConfiguracaoFiscal`): 0 before, 0 after
  - `AlertasDiariosJob.java` and `Pagamento.java` are unchanged
- No constructor dependency or field was added to ResourceController.

## TDD Gate Compliance

- **RED commits:** `test(135-08)` ce146cb failed at compile (missing symbol `RecebidoNoMes`). `test(135-08)` 5adce0c failed 3 assertions: the estorno message and the missing estorno ref, twice.
- **GREEN commits:** 83abf04 (fix) and a8c5734 (feat).
- The 3 new `AlertasDiariosJobTest` cases passed at RED. That is by design: they pin existing job behaviour, which the plan says must not change.

## Deviations from Plan

None. The plan was executed as written.

## Known Stubs

None.

## Self-Check: PASSED

- All 8 files are present.
- Commits ce146cb, 83abf04, 5adce0c and a8c5734 are present in `git log`.
