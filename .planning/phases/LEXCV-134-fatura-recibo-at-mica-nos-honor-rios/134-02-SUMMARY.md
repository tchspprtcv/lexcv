---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 02
subsystem: backend-fiscal
tags: [fiscal, efatura, tdd, rounding, validation]
requires:
  - "Phase 133: RegimeIva, ConfiguracaoFiscal.NIF_FISCAL_REGEX/MORADA_MAX, RecusaFiscalException, ParametrosFiscaisSemConstantesTest gate"
provides:
  - "CalculoFiscal.calcular(total, regime, taxaIvaPct, taxaRetencaoPctOuNull) -> ResultadoCalculo(base, iva, taxaIva, retencao, taxaRetencao, total, liquidoRecebido)"
  - "ValidacaoEmissao: normalizarValor, validarData(pedida, hoje), validarMetodo, validarRetencao, validarAdquirente(nif, nome, morada), exigirChave; NOME_MIN/NOME_MAX"
  - "MetodoPagamento { DINHEIRO, TRANSFERENCIA, CHEQUE, CARTAO, OUTRO } with rotulo(), codigoMeioPagamento(), porNome(String)"
  - "TextoDocumentoFiscal: DESCRICAO_HONORARIOS, DESCRICAO_MAX, descricaoLinhaHonorarios(numeroProcesso), numeroFormatado(serieCodigo, numero)"
affects: [134-04, 134-05, 134-06, 134-07, 134-08, 136]
tech-stack:
  added: []
  patterns:
    - "Pure static fiscal rules (final class, private constructor, no Spring beans) shared by preview and emission"
    - "Single-division VAT extraction: base = total*100/(100+iva) HALF_UP, iva = total - base (residue to VAT)"
    - "422 refusals via RecusaFiscalException(UNPROCESSABLE_ENTITY, code, verbatim UI-SPEC message, campo)"
key-files:
  created:
    - backend/src/main/java/com/lexcv/models/MetodoPagamento.java
    - backend/src/main/java/com/lexcv/services/fiscal/CalculoFiscal.java
    - backend/src/main/java/com/lexcv/services/fiscal/ValidacaoEmissao.java
    - backend/src/main/java/com/lexcv/services/fiscal/TextoDocumentoFiscal.java
    - backend/src/test/java/com/lexcv/models/MetodoPagamentoTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/CalculoFiscalTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/ValidacaoEmissaoTest.java
  modified: []
decisions:
  - "CalculoFiscal also throws IllegalArgumentException when total or regime is null (caller bug), in addition to NORMAL without taxaIva"
  - "validarRetencao returns the rate normalised to scale 2 (12.5 -> 12.50), as the plan's '20 -> 20.00' requires; refuses scale > 2 after stripTrailingZeros"
  - "Adquirente NIF is matched raw (not trimmed), so the value that is checked is the value that gets snapshotted; nome/morada are checked trimmed"
  - "TextoDocumentoFiscal tests live in a @Nested class inside ValidacaoEmissaoTest (the plan lists only three test files)"
metrics:
  duration: "~12 min"
  completed: 2026-10-04
  tasks: 2
  files: 7
---

# Phase 134 Plan 02: Pure fiscal rules Summary

Pure, test-first fiscal rules for the Fatura-Recibo. The IVA/retenção calculator reproduces all 12 research vectors, including the HALF_UP tie, and keeps its invariants over 200 000 totals. The 422 validations carry code and `campo` with the verbatim UI-SPEC copy. A closed `MetodoPagamento` enum maps each method to an eFatura payment-means code, and the line description is controlled text that never includes the honorário's free text.

## What was built

- **CalculoFiscal**: NORMAL computes `base = total*100/(100+taxa)` (scale 2, HALF_UP, one division) and `iva = total - base`. ISENTO sets base = total, IVA 0.00 and taxa 0. Retenção is computed on the base (HALF_UP); `liquido = total - retencao`. The only literal is `CEM = 100`. A total with more than 2 decimals throws `ArithmeticException` (`UNNECESSARY`).
- **MetodoPagamento**: codes DINHEIRO 10, TRANSFERENCIA 30, CHEQUE 20, CARTAO 48, OUTRO ZZZ (UNCL4461, marked [ASSUMED] until Phase 136). `porNome` trims and ignores case.
- **ValidacaoEmissao**:
  - `VALOR_PAGO_INVALIDO` (`valorPago`): value <= 0, more than 2 decimals, or precision > 19
  - `DATA_PAGAMENTO_RETROATIVA` (`dataPagamento`): any date other than `hoje`; a null date becomes `hoje`
  - `METODO_PAGAMENTO_INVALIDO` (`metodo`)
  - `RETENCAO_INVALIDA` (`retencaoPercentagem`): rate outside 0 < taxa <= 100, or scale > 2
  - `ADQUIRENTE_INCOMPLETO` (`nif` → `nome` → `morada`, first failure wins)
  - `CHAVE_IDEMPOTENCIA_OBRIGATORIA` (`chaveIdempotencia`)
  - The class never reads the clock.
- **TextoDocumentoFiscal**: `"Honorários por serviços jurídicos — Processo n.º {n}"` (at most 200 chars, number truncated if needed), the bare text when there is no number, and `numeroFormatado` producing `SIM-FR-2026/1`.

## Verification

- `CalculoFiscalTest` 18/18 (12 vectors + property loop over 200 000 cents with and without retenção + 5 edge cases)
- `MetodoPagamentoTest` 10/10, `ValidacaoEmissaoTest` 53/53 (all refusals assert status, `getCodigo()` and `getCampo()`)
- `ParametrosFiscaisSemConstantesTest` 2/2 (no rate literal added to src/main/java)
- `compile spotbugs:check` passes
- Acceptance greps:
  - CalculoFiscal: 2 `RoundingMode.HALF_UP` lines; no `HALF_EVEN|1.15|doubleValue|double `; no Spring import
  - ValidacaoEmissao: 0 `LocalDate.now|Instant.now|Clock` matches; the NIF copy is present verbatim
  - TextoDocumentoFiscal: no `honorario.get` on any `descricao` line

## Commits

| Task | Phase | Commit | Description |
|------|-------|--------|-------------|
| 1 | RED | 5442a17 | Failing CalculoFiscalTest + MetodoPagamentoTest |
| 1 | GREEN | ab316fe | CalculoFiscal + MetodoPagamento |
| 2 | RED | 9fc30e9 | Failing ValidacaoEmissaoTest (incl. texts) |
| 2 | GREEN | b376e29 | ValidacaoEmissao + TextoDocumentoFiscal |

## TDD Gate Compliance

For both tasks, a `test(134-02)` commit came first, while the test class failed to compile against the missing production classes. A `feat(134-02)` commit followed with the tests green. No refactor commit was needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Acceptance] NIF message literal kept on one line**
- **Found during:** Task 2
- **Issue:** The first version split the NIF message across two concatenated literals, so the acceptance `grep -F "Corrija o NIF do cliente (9 dígitos, começa por 1 a 9)"` found nothing, even though the runtime message was correct.
- **Fix:** Moved the split point so that sentence is one literal. The runtime text is unchanged and the tests still pass.
- **Files modified:** backend/src/main/java/com/lexcv/services/fiscal/ValidacaoEmissao.java
- **Commit:** b376e29

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: all 7 key files
- FOUND: 5442a17, ab316fe, 9fc30e9, b376e29
