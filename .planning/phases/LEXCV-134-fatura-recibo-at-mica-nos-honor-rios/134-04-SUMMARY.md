---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 04
subsystem: backend-fiscal
tags: [fiscal, efatura, preview, tdd, readonly]
requires:
  - "134-02: CalculoFiscal, ValidacaoEmissao, MetodoPagamento, TextoDocumentoFiscal"
  - "134-03: ConfiguracaoFiscalRepository.ativaPorTenant"
  - "Phase 133: ParametroFiscalService.valorVigente, CodigoParametroFiscal, ConfiguracaoFiscal.completa(), MotivoIsencaoIva, RecusaFiscalException, Clock bean"
provides:
  - "PagamentoRequest(honorarioId, valorPago, dataPagamento, metodo, retencaoPercentagem, chaveIdempotencia) + paraPagamentoLegado()"
  - "ComposicaoFaturaRecibo.compor(cfg, cliente, processo, Integer honorarioId, req, hoje, taxaIvaOuNull) -> ProjetoFaturaRecibo (pure, shared with emission)"
  - "PreVisualizacaoFaturaResponse.de(ProjetoFaturaRecibo); EstadoEmissaoResponse(ativa, ambiente, taxaRetencaoSugerida) + desligada()"
  - "PreVisualizacaoFaturaService.preVisualizar(tenantId, req) and estadoEmissao(tenantId), both @Transactional(readOnly = true)"
  - "Refusal codes: 409 FATURACAO_DESLIGADA, 422 HONORARIO_OBRIGATORIO (campo honorarioId), 404 HONORARIO_NAO_ENCONTRADO, 404 CLIENTE_NAO_ENCONTRADO, 422 CONFIGURACAO_FISCAL_INCOMPLETA"
affects: [134-06, 134-07, 134-08, 134-09, 134-11, 134-12]
tech-stack:
  added: []
  patterns:
    - "One pure composition function for preview and emission (frontend burro, D-01)"
    - "Cabo Verde 'hoje' computed once from the injected Clock and passed both to validation and to the IVA parameter lookup"
key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/PagamentoRequest.java
    - backend/src/main/java/com/lexcv/dtos/PreVisualizacaoFaturaResponse.java
    - backend/src/main/java/com/lexcv/dtos/EstadoEmissaoResponse.java
    - backend/src/main/java/com/lexcv/services/fiscal/ProjetoFaturaRecibo.java
    - backend/src/main/java/com/lexcv/services/fiscal/ComposicaoFaturaRecibo.java
    - backend/src/main/java/com/lexcv/services/fiscal/PreVisualizacaoFaturaService.java
    - backend/src/test/java/com/lexcv/services/fiscal/ComposicaoFaturaReciboTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/PreVisualizacaoFaturaServiceTest.java
  modified: []
decisions:
  - "PreVisualizacaoFaturaResponse.regimeIva is the RegimeIva enum (same as ConfiguracaoFiscalResponse); tipo/ambiente/metodo are enum names as Strings"
  - "Adquirente nome/morada/localidade and emitente firma/morada/localidade are trimmed in the project; blank localidade becomes null; NIFs are kept raw (as validated)"
  - "Refusal messages for the lookup codes (honorário obrigatório/não encontrado, cliente não encontrado) are package-private constants in PreVisualizacaoFaturaService, so plan 06 can reuse them"
  - "ISENTO motivo is resolved with MotivoIsencaoIva.porCodigo; completa() already guarantees it exists"
metrics:
  duration: "~20 min"
  completed: 2026-10-04
  tasks: 2
  files: 8
---

# Phase 134 Plan 04: Preview contract and pure composition Summary

This plan adds the request/response contract of the emission flow and a side-effect-free, read-only preview service. Both are built on one pure `ComposicaoFaturaRecibo.compor`, which plan 06's emission will reuse, so preview and emission cannot diverge in figures or refusals.

## What was built

- **`PagamentoRequest`**: a record carrying only the user's choices: honorário, valor, data, método, retenção and idempotency key. It has no id, tenant or issuer fields. `paraPagamentoLegado()` rebuilds the old four-field `Pagamento` (never with an id) for the disabled path in plan 08.
- **`ComposicaoFaturaRecibo.compor`** (pure, no clock or DB). It checks in order, and the first failure wins:
  1. configuration complete (422 `CONFIGURACAO_FISCAL_INCOMPLETA`, using the UI-SPEC copy)
  2. valor, data, método, retenção
  3. adquirente NIF, nome, morada
  It then calls `CalculoFiscal.calcular` with the controlled line text. It receives only `Integer honorarioId`, never the `Honorario`, so the honorário's free description can never reach the document. Overpayment is deliberately not checked (R-02).
- **`ProjetoFaturaRecibo`**: the validated project (ids, date, method, line, regime, ISENTO motivo, calculation, emitente and adquirente snapshots). `PreVisualizacaoFaturaResponse.de` exposes it, with type FR and ambiente SIMULADO.
- **`PreVisualizacaoFaturaService.preVisualizar`** (`readOnly`): active config, or 409 `FATURACAO_DESLIGADA`.
  - 422 `HONORARIO_OBRIGATORIO` (`campo honorarioId`).
  - A missing honorário and one from another tenant return the same 404 `HONORARIO_NAO_ENCONTRADO`, so another office's existence never leaks.
  - Tenant-checked cliente, or 404 `CLIENTE_NAO_ENCONTRADO`.
  - Cabo Verde `hoje` comes from the Clock; `valorVigente(IVA_TAXA_NORMAL, hoje)` is read only for NORMAL; then the shared `compor`. No locks and no writes.
- **`estadoEmissao`** (`readOnly`): reads the scalar `ativaPorTenant`. When active it returns `{true, "SIMULADO", valorVigente(RETENCAO_SUGERIDA, hoje)}`; otherwise all fields are null and `ParametroFiscalService` is not called.

## Verification

- `ComposicaoFaturaReciboTest` 11/11:
  - reference vector 120 000 @ 15% with 20% retenção gives 104347.83 / 15652.17 / 20869.57 / 99130.43
  - ISENTO ignores the rate
  - incomplete config is refused before request fields
  - full validation order with `campo`
  - NORMAL without a rate throws `IllegalArgumentException`
  - no `Honorario` parameter
  - blank localidade becomes null
  - response mapping; legacy mapping never copies the id
- `PreVisualizacaoFaturaServiceTest` 11/11:
  - figures computed in the backend
  - `verifyNoMoreInteractions` proves only the four reads plus the IVA rate happen
  - both methods are `readOnly`
  - ISENTO never reads the parameter
  - 409/422/404 cases, including the identical message for a foreign-tenant honorário
  - `Clock.fixed` at 00:59:59Z gives 2026-10-04 and at 01:00:01Z gives 2026-10-05, with the same date used for the IVA lookup
  - `estadoEmissao` on/off
- `ValidacaoEmissaoTest`, `CalculoFiscalTest` and `ParametrosFiscaisSemConstantesTest` are green (84 tests in the Task 1 run).
- `compile spotbugs:check` passes.
- **Acceptance greps:**
  - `PagamentoRequest` has no `tenantId` or `emitente`.
  - `ComposicaoFaturaRecibo` has 0 `LocalDate.now`.
  - `PreVisualizacaoFaturaService` has no `SecurityContextHolder|.save(|bloquear|proximoNumero|criarSeNaoExiste`, and exactly 2 `readOnly = true`.

## Commits

| Task | Phase | Commit | Description |
|------|-------|--------|-------------|
| 1 | RED | 92f4f84 | Failing ComposicaoFaturaReciboTest |
| 1 | GREEN | a5202ca | ComposicaoFaturaRecibo + contract records |
| 2 | RED | c4530d4 | Failing PreVisualizacaoFaturaServiceTest |
| 2 | GREEN | 0f0f546 | PreVisualizacaoFaturaService |

## TDD Gate Compliance

For both tasks, a `test(134-04)` commit came first, while the test class failed to compile against the missing production types. A `feat(134-04)` commit followed with the tests green. No refactor commit was needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Acceptance] Reworded the PagamentoRequest javadoc**
- **Found during:** Task 1
- **Issue:** The javadoc said the record has no "dados do emitente", which tripped the `grep -n "tenantId\|emitente"` acceptance check (it must match nothing).
- **Fix:** Reworded it to "dados de quem emite". The meaning is unchanged.
- **Commit:** a5202ca

### Notes

- The acceptance check `grep -n "Honorario" ComposicaoFaturaRecibo.java | grep -v "^.*\*"` matches one line: the call `TextoDocumentoFiscal.descricaoLinhaHonorarios(...)`. That is the method name from plan 02, not a `Honorario` parameter. The check's intent (no `Honorario` in the signature) holds, and `ComposicaoFaturaReciboTest.composicaoNuncaRecebeOHonorario` pins it by reflection.

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: all 8 key files
- FOUND: 92f4f84, a5202ca, c4530d4, 0f0f546
