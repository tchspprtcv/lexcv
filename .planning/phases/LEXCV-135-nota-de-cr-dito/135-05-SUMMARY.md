---
phase: 135-nota-de-cr-dito
plan: 05
subsystem: backend-fiscal
tags: [fiscal, nota-de-credito, dto, audit, idempotency, tdd]
requires:
  - "135-03: ProjetoNotaCredito, TipoCredito"
  - "135-04: PagamentoComDocumentoResponse 3-arg factory, DocumentoFiscalRef"
  - "Phase 134: AuditoriaFiscalService, PagamentoFaturadoService"
provides:
  - "PreVisualizacaoNotaCreditoResponse (28 components) + static de(DocumentoFiscal origem, ProjetoNotaCredito)"
  - "NotaCreditoResponse(id, numeroFormatado, tipo, documentoOrigemId, documentoOrigemNumero, dataEmissao, totalDocumento, estorno, valorCreditavelRestante)"
  - "ResultadoNotaCredito(novo, resposta) with novo()/repetido()"
  - "AuditoriaFiscalService.ACAO_EMITIR_NC = documento_fiscal_emitir_nc + registarEmissaoNotaCredito(tenantId, autor, documentoId, numeroFormatado, numeroOrigem)"
  - "PagamentoFaturadoService: FR replay refuses keys stored on non-FR documents (409 CHAVE_REUTILIZADA)"
affects: [135-06, 135-07, 135-09, 135-10]
tech-stack:
  added: []
  patterns:
    - "Type guard before request comparison on a shared per-tenant idempotency key space"
key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/PreVisualizacaoNotaCreditoResponse.java
    - backend/src/main/java/com/lexcv/dtos/NotaCreditoResponse.java
    - backend/src/main/java/com/lexcv/services/fiscal/ResultadoNotaCredito.java
  modified:
    - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
    - backend/src/main/java/com/lexcv/services/fiscal/PagamentoFaturadoService.java
    - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceTest.java
    - backend/src/test/java/com/lexcv/dtos/DocumentoFiscalNotaCreditoDtosTest.java
decisions:
  - "Preview taxaIva is null whenever the projected regime is ISENTO (the PARCIAL path of CalculoFiscal yields 0 there)"
  - "Preview regimeIva/tipoCredito/motivoCodigo are enum names as String (contract in the plan), unlike the FR preview which exposes the enum"
  - "The PreVisualizacaoNotaCreditoResponse.de mapping tests live in DocumentoFiscalNotaCreditoDtosTest (the plan's DTO test home since 135-04)"
metrics:
  duration: "~15 min"
  completed: 2026-10-05
  tasks: 2
  files: 8
---

# Phase 135 Plan 05: NC contracts, NC audit event and FR cross-type replay guard Summary

This plan adds the NC response contracts, the NC emission audit event, and a fix to the FR idempotency path.
- **Preview payload:** `PreVisualizacaoNotaCreditoResponse.de` takes the adquirente and the isenção motivo from the origin FR snapshot. `taxaIva` is null when the regime is ISENTO.
- **Emission payload:** `NotaCreditoResponse` and `ResultadoNotaCredito`.
- **Audit event:** `registarEmissaoNotaCredito` writes `documento_fiscal_emitir_nc` with only `autorNome`, `numeroFormatado` and `documentoOrigem`. The free-text motivo is never passed to it.
- **FR replay fix:** an FR request that reuses an NC's idempotency key now gets 409 `CHAVE_REUTILIZADA`. Before, it crashed with an `IllegalStateException` (shown in RED).

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | NC response DTOs, ResultadoNotaCredito and NC audit event | 162d484 (RED), e05f39b (GREEN) |
| 2 | FR idempotent replay refuses keys that belong to an NC | 5dc0c9b (RED), 7928484 (GREEN) |

## Verification

- `AuditoriaFiscalServiceTest`: 17 tests, green (3 new; the reflective "all registar* are MANDATORY" count went from 6 to 7).
- `DocumentoFiscalNotaCreditoDtosTest`: 9 tests, green (2 new: full preview mapping, ISENTO).
- `PagamentoFaturadoServiceTest`: 61 tests, green (3 new). Existing replay tests and the InOrder lock test are unchanged.
- `FaturacaoDesligadaPagamentoInalteradoTest` (CFG-03): 5 tests, green.
- Acceptance greps:
  - `ACAO_EMITIR_NC = "documento_fiscal_emitir_nc"` is present
  - 0 `motivoTexto|motivo_texto` in the audit service
  - the `de(DocumentoFiscal origem, ProjetoNotaCredito` signature is present
  - 0 `chaveIdempotencia` in both DTOs
  - 2 `getTipo() != TipoDocumentoFiscal.FR` in `PagamentoFaturadoService`
  - the `ResourceController` diff is empty

## TDD Gate Compliance

- **RED commits:** `test(135-05)` 162d484 failed at compile (missing symbol). `test(135-05)` 5dc0c9b failed 3 assertions, with an `IllegalStateException` instead of a `RecusaFiscalException`.
- **GREEN commits:** e05f39b (feat) and 7928484 (fix).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Test fixture] `existente()` FR fixture had no `tipo`**
- **Found during:** Task 2.
- **Issue:** the `PagamentoFaturadoServiceTest.existente()` fixture built the stored FR without `tipo`, although the real column is NOT NULL. Without a fix, the new FR-only guard would have rejected the existing replay tests.
- **Fix:** added `.tipo(TipoDocumentoFiscal.FR)` to the fixture. No assertion changed.
- **Files modified:** `backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceTest.java`
- **Commit:** 5dc0c9b

## Known Stubs

None.

## Self-Check: PASSED

- The 3 created files and 5 modified files are present.
- Commits 162d484, e05f39b, 5dc0c9b and 7928484 are present in `git log`.
