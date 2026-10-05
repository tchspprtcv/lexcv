---
phase: 135-nota-de-cr-dito
plan: 10
subsystem: web-financeiro
tags: [frontend, nota-de-credito, types, hooks, zod, vitest, rbac]
requires:
  - "135-04: DocumentoFiscalDetalhe/Resumo NC fields, PagamentoComDocumentoResponse.estorno"
  - "135-05: PreVisualizacaoNotaCreditoResponse, NotaCreditoResponse contracts"
provides:
  - "types/faturacao.ts: MotivoNotaCredito, TipoCredito, NotaCreditoResumo, NotaCreditoRequest, PreVisualizacaoNotaCredito, NotaCreditoResposta; NC fields on DocumentoFiscalDetalhe/Resumo; 7 new CodigoErroFaturacao codes"
  - "types/financeiro.ts: Pagamento.estorno"
  - "hooks/use-faturacao.ts: PERMISSAO_EMISSAO_NOTA_CREDITO, podeEmitirNotaCredito, usePreVisualizacaoNotaCredito(documentoId), useEmitirNotaCredito(documentoId)"
  - "lib/erros-emissao.ts: COPY_NC_*, COPY_GUARDA_ESTORNO, CampoNotaCredito, ErroNotaCredito, FaseNotaCredito, interpretarErroNotaCredito(error, fase)"
  - "schemas/financeiro.ts: MOTIVOS_NOTA_CREDITO, TIPOS_CREDITO, MOTIVO_TEXTO_MAX, notaCreditoFormSchema, NotaCreditoFormInput/Values, paraPedidoNotaCredito"
affects: [135-11, 135-12]
tech-stack:
  added: []
  patterns:
    - "Phase-aware error interpretation (pre-visualizacao vs emissao network copy)"
    - "PARCIAL-only zod superRefine reusing the existing moneyString rule"
key-files:
  created: []
  modified:
    - web/src/types/faturacao.ts
    - web/src/types/financeiro.ts
    - web/src/hooks/use-faturacao.ts
    - web/src/lib/erros-emissao.ts
    - web/src/lib/erros-emissao.test.ts
    - web/src/schemas/financeiro.ts
    - web/src/schemas/financeiro.test.ts
decisions:
  - "NC mutations use STATUS_INLINE_EMISSAO plus 404 (STATUS_INLINE_NOTA_CREDITO, private to the hooks), so the 404 'nao-encontrado' state is shown without a toast"
  - "Every 503 maps to the fixed UI-SPEC copy 'serviço de faturação ... indisponível', even with a backend message. Other 5xx and network failures map to the phase copy"
  - "The 403 banner is non-definitive (definitivo: false). Only NC_SOBRE_NC and FATURACAO_DESLIGADA are definitive, as the plan lists"
  - "A motivoTexto over 200 chars uses the backend copy '(no máximo 200 caracteres)'. Blank text uses the UI-SPEC copy 'Descreva o motivo da nota de crédito.'"
  - "The podeEmitirNotaCredito tests live in erros-emissao.test.ts, next to the existing podeLer/podeRegistar gate tests (there is no separate hooks test file)"
metrics:
  duration: "~20 min"
  completed: 2026-10-05
  tasks: 2
  files: 7
---

# Phase 135 Plan 10: Web contracts for Nota de Crédito Summary

This plan adds the frontend NC contracts that plans 11 and 12 render.
- **Types:** they mirror the backend NC DTOs. All amounts are positive, except the estorno `valorPago`.
- **Permission gate:** `podeEmitirNotaCredito` requires exactly `financeiro:manage`, the same authority the backend checks.
- **Hooks:** a preview hook and an emission hook. Both use relative paths and handle 409, 422, 5xx and 404 inline. The emission hook invalidates in `onSettled` the documents, honorários, conta-corrente and `["dashboard", "kpis"]` caches.
- **Error interpretation:** `interpretarErroNotaCredito` maps each NC refusal to the UI-SPEC copy, plus the estorno delete-guard copy.
- **Form schema:** `notaCreditoFormSchema` enforces only the UX rules. The PARCIAL valor follows the same format rule as the FR `moneyString`, and the schema never checks a ceiling.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | Types, permission helper and NC hooks | 2fbf89b |
| 2 | NC error interpretation and form schema with vitest | ffef92f (RED), af1bd84 (GREEN) |

## Verification

- `pnpm exec vitest run src/lib/erros-emissao.test.ts src/schemas/financeiro.test.ts src/lib/idempotencia.test.ts`: 143 passed.
- `pnpm test`: 11 files, 233 tests, all green. 45 new cases:
  - 3 for the permission gate
  - 17 for NC errors and the estorno guard
  - 25 for the schema and request builder
- `npx tsc --noEmit`: clean.
- `pnpm lint`: 0 errors. The 20 warnings are unchanged and all in unrelated files.
- `pnpm verify:documentos-fiscais` and `pnpm verify:faturacao`: OK.
- Acceptance greps:
  - `PERMISSAO_EMISSAO_NOTA_CREDITO = "financeiro:manage"` and `hasPermission(permissions, PERMISSAO_EMISSAO_NOTA_CREDITO)` are present
  - 0 `/api/v1` in `use-faturacao.ts`
  - `["dashboard", "kpis"]` is present
  - `valorCreditavelRestante` is in the types and `estorno` is in `financeiro.ts`
  - `export function interpretarErroNotaCredito` and `PAGAMENTO_ESTORNO: COPY_GUARDA_ESTORNO` are present
  - the `STATUS_INLINE_EMISSAO` line is unchanged
  - `export const notaCreditoFormSchema` is present
  - 0 rate constants in `schemas/financeiro.ts`

## TDD Gate Compliance

- **Task 2:** RED commit `test(135-10)` ffef92f; 39 tests failed on the missing exports (undefined constants and functions). GREEN commit `feat(135-10)` af1bd84.
- **Task 1:** this is contract and type work verified by tsc and the verify gates. Its behavioural tests (the permission gate) were committed together with the implementation in 2fbf89b.

## Deviations from Plan

None. The plan was executed as written.

## Known Stubs

None. Plans 11 and 12 consume these contracts in the UI; no component renders them yet, as planned.

## Self-Check: PASSED

- All 7 modified files are present.
- Commits 2fbf89b, ffef92f and af1bd84 are present in `git log`.
