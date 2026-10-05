---
phase: 135-nota-de-cr-dito
plan: 03
subsystem: backend-fiscal
tags: [fiscal, nota-de-credito, tdd, pure-function, rounding]
requires:
  - "135-01: MotivoNotaCredito, DocumentoFiscal NC getters"
  - "Phase 134: CalculoFiscal, ValidacaoEmissao rules, TextoDocumentoFiscal, RecusaFiscalException"
provides:
  - "record NotaCreditoRequest(String tipo, BigDecimal valor, String motivoCodigo, String motivoTexto, UUID chaveIdempotencia)"
  - "enum TipoCredito { TOTAL, PARCIAL }"
  - "ValidacaoNotaCredito: validarTipo, validarMotivo, validarMotivoTexto, validarValorParcial, exigirFaturaRecibo, MOTIVO_TEXTO_MAX = 200"
  - "record ProjetoNotaCredito (13 components as specified)"
  - "ComposicaoNotaCredito.compor(origem, notasAnteriores, req, hoje)"
  - "TextoDocumentoFiscal.descricaoLinhaNotaCredito(numeroOrigem)"
affects: [135-05, 135-06]
tech-stack:
  added: []
  patterns:
    - "Per-column remainder + clamp (base<=remBase, iva<=remIva, retencao<=remRet) so cumulative NCs never exceed the FR"
key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/NotaCreditoRequest.java
    - backend/src/main/java/com/lexcv/services/fiscal/TipoCredito.java
    - backend/src/main/java/com/lexcv/services/fiscal/ValidacaoNotaCredito.java
    - backend/src/main/java/com/lexcv/services/fiscal/ProjetoNotaCredito.java
    - backend/src/main/java/com/lexcv/services/fiscal/ComposicaoNotaCredito.java
    - backend/src/test/java/com/lexcv/services/fiscal/ValidacaoNotaCreditoTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/ComposicaoNotaCreditoTest.java
  modified:
    - backend/src/main/java/com/lexcv/services/fiscal/TextoDocumentoFiscal.java
decisions:
  - "Both NC_EXCEDE_ORIGINAL variants (over-cap and fully credited) carry campo 'valor', as listed in the plan's refusal table"
  - "PARCIAL path keeps CalculoFiscal's taxaIva (0 for ISENTO) and taxaRetencao; TOTAL/remainder path uses the FR snapshot taxaIva/taxaRetencao"
  - "ValidacaoNotaCredito.recusaValor() is package-private so the composition reuses the exact VALOR_CREDITO_INVALIDO refusal for TOTAL-with-valor"
metrics:
  duration: "~15 min"
  completed: 2026-10-05
  tasks: 2
  files: 8
---

# Phase 135 Plan 03: Pure Nota de Crédito composition Summary

This plan adds a pure `ComposicaoNotaCredito.compor` function, built test-first.
- **Rates:** a partial NC uses the original FR's snapshot rates through `CalculoFiscal`.
- **Total NC:** credits the exact per-column remainders.
- **Clamps (P-13):** keep every cumulative column at or below the FR.
- **Refusals:** a cap refusal (409 `NC_EXCEDE_ORIGINAL`) and a request refusal when the origin is an NC (422 `NC_SOBRE_NC`, checked first).
- **Validators:** the request validators carry the UI-SPEC codes, field names and Portuguese copy.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | Request DTO, TipoCredito, ValidacaoNotaCredito and line text | 3a335e3 (RED), 5ab2e09 (GREEN) |
| 2 | ProjetoNotaCredito and ComposicaoNotaCredito with reference vectors and clamp properties | e996b44 (RED), 1b1ec65 (GREEN) |

## Verification

- `ValidacaoNotaCreditoTest`: 32 tests (nested). `ValidacaoEmissaoTest`: 55 tests. All green. `ValidacaoEmissao.java` is unchanged (`git diff --quiet`).
- `ComposicaoNotaCreditoTest`: 17 tests, green.
  - The 120 000 reference vectors reproduce exactly to the cent:
    - partial 20 000: 17 391.30 / 2 608.70 / 3 478.26 / liquido 16 521.74
    - then total: 86 956.53 / 13 043.47 / 17 391.31 / liquido 82 608.69
  - Also covered: cap refusals, `NC_SOBRE_NC` before any other check, the request-validation order, ISENTO, no retention, and a 12% snapshot rate.
  - Clamp coverage:
    - 100 credits of 0.01 on a 1.00 FR
    - a retention rounding-up case (50% on 0.03 steps)
    - a seeded property loop of 300 random FRs, each split into up to 12 random partials and closed by a TOTAL
- `CalculoFiscalTest` (18), `ComposicaoFaturaReciboTest` (12) and `ParametrosFiscaisSemConstantesTest` (2) stay green.
- Acceptance greps:
  - 0 `ParametroFiscalService`
  - `CalculoFiscal.calcular` is present
  - the reference values appear 4 times in the test
- RED commits come before GREEN commits for both tasks.

## TDD Gate Compliance

RED gate commits are `test(135-03)` 3a335e3 and e996b44; each failed at compile, as expected. GREEN gate commits are `feat(135-03)` 5ab2e09 and 1b1ec65. No refactor commit was needed.

## Deviations from Plan

None. The plan was executed as written.

## Known Stubs

None.

## Self-Check: PASSED

- All 8 files are present.
- Commits 3a335e3, 5ab2e09, e996b44 and 1b1ec65 are present in `git log`.
