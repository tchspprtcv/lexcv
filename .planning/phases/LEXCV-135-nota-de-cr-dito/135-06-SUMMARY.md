---
phase: 135-nota-de-cr-dito
plan: 06
subsystem: backend-fiscal
tags: [fiscal, nota-de-credito, transaction, locks, idempotency, tdd]
requires:
  - "135-03: ComposicaoNotaCredito, ValidacaoNotaCredito, NotaCreditoRequest, TipoCredito"
  - "135-05: PreVisualizacaoNotaCreditoResponse, NotaCreditoResponse, ResultadoNotaCredito, registarEmissaoNotaCredito"
  - "135-01: NC finders (findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc)"
provides:
  - "NotaCreditoService.preVisualizar(tenantId, documentoOrigemId, req) @Transactional(readOnly = true)"
  - "NotaCreditoService.emitir(tenantId, autor, documentoOrigemId, req) @Transactional -> ResultadoNotaCredito"
affects: [135-07, 135-09]
tech-stack:
  added: []
  patterns:
    - "Replay of a TOTAL NC request identified by 'stored NC is the latest NC of the FR and the FR is exhausted' (no tipo_credito column)"
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/NotaCreditoService.java
    - backend/src/test/java/com/lexcv/services/fiscal/NotaCreditoServiceTest.java
decisions:
  - "TOTAL replay requires: req.valor null AND FR remaining == 0 AND the stored NC is notas.get(0) (most recent by dataEmissao desc, numero desc). An earlier PARCIAL whose FR was later exhausted by another NC gives 409 CHAVE_REUTILIZADA (plan-checker note a)"
  - "A PARCIAL equal to the exact remainder stays indistinguishable from a TOTAL on replay. It goes through the same composition branch, so the documents are identical. Pinned by a unit test instead of adding a tipo_credito column (a schema change)"
  - "The NC document and line taxaIva use the FR snapshot rate (origem.getTaxaIva()). It is equal in value to the calculo rate, and the PARCIAL ISENTO calculo yields 0 there too"
  - "On replay, origem and notas are loaded only after the cheap tipo/origem/motivo/texto checks pass. A missing origem for a stored NC is an IllegalStateException (an impossible state, since the FR is immutable)"
  - "NC-specific MSG_CHAVE_REUTILIZADA ('... emita a nota de crédito de novo.') lives in NotaCreditoService; the FR copy is unchanged"
metrics:
  duration: "~25 min"
  completed: 2026-10-05
  tasks: 2
  files: 2
---

# Phase 135 Plan 06: NotaCreditoService (preview + atomic idempotent emission) Summary

This plan adds `NotaCreditoService`, written test-first.
- **Preview:** `preVisualizar` is read-only and computes the same values through `ComposicaoNotaCredito.compor`.
- **Emission:** `emitir` is atomic and takes locks in the Phase 134 order (configuração → cliente → processo → conta corrente → série). One transaction writes all of the following:
  - a negative estorno `Pagamento` on the FR's honorário
  - the debit to the conta corrente
  - an NC number from its own series (`proximoNumero(tenantId, TipoDocumentoFiscal.NC, AmbienteFiscal.SIMULADO)`)
  - an immutable document that snapshots the FR's emitente, adquirente, regime and isenção
  - the line, a `PENDENTE` comunicação, and the audit event
- **Replay:** handled under the config lock, before the ativa check.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | Unit test suite for NotaCreditoService (RED) | cafe945 |
| 2 | Implement NotaCreditoService (GREEN) | 9875976 |

## Verification

- `NotaCreditoServiceTest`: 65 tests, from 49 methods including parameterized ones. All green. They cover:
  - an InOrder lock and call sequence
  - never-unlocked reads (`clienteRepo.findById`, `processoRepo.findById`, `configuracaoRepo.findByTenantId`, `ccRepo.findByClienteId`, `honorarioRepo.findById`)
  - estorno, debit, snapshot, line and comunicação effects, and the 20 000 partial vector
  - refusals before any write: 422 chave / NC_SOBRE_NC / request, 404 origem / cliente / honorário, 409 desligada / excede / processo alterado / data, 503 for each lock in 3 exception flavours, and SERIE_INDISPONIVEL passed through
  - 7 replay variants plus 5 replay scenarios, the FR-key refusal, and replay with billing turned off
  - preview: equal to `compor`, with no lock, write or numbering
- `PagamentoFaturadoServiceTest` (61) and `ParametrosFiscaisSemConstantesTest`: green.
- `mvn -DskipTests compile spotbugs:check`: BUILD SUCCESS, no findings.
- Acceptance greps:
  - the `proximoNumero(... NC, SIMULADO)` call and `negate()` are present
  - 0 `ParametroFiscalService` or `SecurityContextHolder`
  - 0 `catch (DataIntegrityViolationException`
  - main is 457 lines and the test is 998 lines (both >= 200)

## TDD Gate Compliance

- **RED:** `test(135-06)` cafe945 failed at compile. The only error is `cannot find symbol: class NotaCreditoService`, which is the expected missing-symbol failure (plan-checker note b).
- **GREEN:** `feat(135-06)` 9875976. No refactor commit was needed.

## Deviations from Plan

### Plan-checker adjustments (requested by the orchestrator)

**1. [Rule 1 - Correctness] TOTAL replay rule tightened**
- **Issue:** the plan's rule ("TOTAL → valor null AND the FR has nothing left to credit") would return an earlier PARCIAL as the replay of a TOTAL request whenever a later NC had exhausted the FR.
- **Fix:** the TOTAL replay also requires the stored NC to be the most recent NC of the FR, so it is the one that closed the FR. Unit tests:
  - `mesmaChaveTotalDeUmaParcialQueOutraNcPosteriorEsgotouRecusaChaveReutilizada`
  - `parcialIgualAoRemanescenteQueFechouAFrEhIndistinguivelDeUmTotal`, which documents the remaining, harmless ambiguity
- **Commit:** cafe945 / 9875976

## Known Stubs

None. The controller wiring (plan 09) and the concurrency and coherence ITs (plan 07) are separate plans.

## Self-Check: PASSED

- `NotaCreditoService.java` and `NotaCreditoServiceTest.java` are present.
- Commits cafe945 and 9875976 are present in `git log`.
