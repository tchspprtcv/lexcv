---
phase: 135-nota-de-cr-dito
plan: 04
subsystem: backend-fiscal
tags: [fiscal, nota-de-credito, read-side, dto, mockito]
requires:
  - "135-01: DocumentoFiscal NC columns, NC repository finders"
  - "Phase 134: DocumentoFiscalService, DocumentoFiscalDetalheResponse/ResumoResponse, PagamentoComDocumentoResponse, DocumentoFiscalRef"
provides:
  - "DocumentoFiscalDetalheResponse: documentoOrigem, motivoCodigo, motivoRotulo, motivoTexto, totalCreditado, valorCreditavelRestante, notasCredito (NotaCreditoResumo); 5-arg factory, 3-arg delegates"
  - "DocumentoFiscalResumoResponse: documentoOrigemId, documentoOrigemNumero; 3-arg factory, 2-arg delegates"
  - "PagamentoComDocumentoResponse: estorno component; 3-arg factory, 2-arg delegates"
  - "DocumentoFiscalService.estornosPorPagamento(tenantId, ids), eEstornoDeNotaCredito(tenantId, pagamentoId)"
affects: [135-05, 135-08, 135-10, 135-11, 135-12]
tech-stack:
  added: []
  patterns:
    - "Two batched findByTenantIdAndPagamentoIdIn calls filtered by tipo (FR refs vs NC estornos) instead of a combined return type"
key-files:
  created:
    - backend/src/test/java/com/lexcv/dtos/DocumentoFiscalNotaCreditoDtosTest.java
  modified:
    - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalDetalheResponse.java
    - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalResumoResponse.java
    - backend/src/main/java/com/lexcv/dtos/PagamentoComDocumentoResponse.java
    - backend/src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerPagamentoTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/DocumentoFiscalServiceTest.java
decisions:
  - "DTO factory tests live in a small dedicated DocumentoFiscalNotaCreditoDtosTest (allowed by the plan) instead of the service test"
  - "totalCreditado for an FR is scale-2 (0.00 with no NCs); valorCreditavelRestante is not clamped, so negative data stays visible"
  - "An NC whose origem is not found in the caller's tenant returns documentoOrigem = null (no cross-tenant lookup, no oracle)"
metrics:
  duration: "~15 min"
  completed: 2026-10-05
  tasks: 2
  files: 7
---

# Phase 135 Plan 04: Fiscal read side for Notas de Crédito Summary

This plan extends the fiscal read side for Notas de Crédito. All values are computed on the backend.
- **FR detail:** lists the NCs issued against it, with `totalCreditado` and `valorCreditavelRestante`.
- **NC detail:** exposes the original FR reference and the motivo.
- **Document list rows:** carry the number of the origin FR, loaded in one batched query.
- **Payment refs:** split into `documentoFiscal` (FR only) and `estorno` (NC only).
- **Estorno probe:** a new service probe serves the estorno-specific delete guard.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | DTO extensions (detail, list row, payment) and positional-constructor callers | e6a121f |
| 2 | DocumentoFiscalService NC read side and estorno probes | e933e20 |

## Verification

- `DocumentoFiscalNotaCreditoDtosTest`: 7 tests, green.
- CFG-03 guard and payment suites, all green:
  - `ResourceControllerPagamentoTest`: 15
  - `ResourceControllerListaPagamentosTest`: 7
  - `FaturacaoDesligadaPagamentoInalteradoTest`: 5
- `DocumentoFiscalServiceTest`: 24 tests (10 new), green. The new tests cover:
  - the FR detail and the NC detail
  - an NC whose origem is outside the caller's tenant
  - list origem numbers in one call, and no call on a page without NCs
  - FR-only refs, NC-only estornos, and empty ids
  - the estorno probe
  - `existeParaPagamento` for an estorno
- `DocumentoFiscalControllerTest` (22) and `ResourceControllerDocumentoFiscalGuardasTest` (20): green.
- Full backend surefire suite (`mvn -Dmaven.compiler.release=21 test`): 896 run, 0 failures, 0 errors, 0 skipped.
- Acceptance checks:
  - `ResourceController.java` is untouched (`git diff --quiet`)
  - 0 `SecurityContextHolder` in the service
  - every grep target is present

## Deviations from Plan

None. The plan was executed as written. As the plan allowed, the DTO factory tests went into a small dedicated test class.

## Known Stubs

None. Wiring `estornosPorPagamento` into `ResourceController.listHonorarioPagamentos` and adding the estorno delete guard belong to later plans (05/08). This plan intentionally left `ResourceController` untouched.

## Self-Check: PASSED

- All 7 files are present.
- Commits e6a121f and e933e20 are present in `git log`.
