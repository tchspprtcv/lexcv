---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 11
subsystem: web-data
tags: [fiscal, efatura, frontend, zod, tanstack-query, idempotency, error-mapping, vitest]
requires:
  - "134-08: POST /pagamentos (201/200, documentoFiscal ref), GET /honorarios/{id}/pagamentos with documentoFiscal"
  - "134-09: GET /faturacao/estado-emissao, POST /faturacao/pre-visualizacao, GET /documentos-fiscais(/{id})"
  - "134-10: 409 PAGAMENTO_FATURADO / *_COM_DOCUMENTOS_FISCAIS"
  - "Phase 133: ApiError {status, code, campo, body}, apiFetch semToastParaStatus, use-faturacao hooks"
provides:
  - "types/faturacao.ts: MetodoPagamento, EstadoComunicacaoFiscal, EstadoEmissao, PreVisualizacaoFatura, DocumentoFiscalRef/Resumo/Linha/Detalhe, PaginaDocumentosFiscais, DocumentosFiscaisFiltros; CodigoErroFaturacao + all Phase 134 codes"
  - "types/financeiro.ts: Pagamento.documentoFiscal?, PagamentoCreateRequest.retencaoPercentagem?/chaveIdempotencia?"
  - "schemas/financeiro.ts: METODOS_PAGAMENTO, pagamentoFaturadoFormSchema (+ Input/Values types), paraPedidoPagamentoFaturado"
  - "lib/idempotencia.ts: gerarChaveIdempotencia()"
  - "lib/erros-emissao.ts: interpretarErroEmissao, mensagemGuardaFiscal, construirQueryDocumentosFiscais, COPY_* constants, ErroEmissao union"
  - "hooks/use-faturacao.ts: ESTADO_EMISSAO_KEY, DOCUMENTOS_FISCAIS_KEY, PERMISSAO_LEITURA_FISCAL, podeLerDocumentosFiscais, useEstadoEmissao, usePreVisualizacaoFaturacao, useDocumentosFiscais, useDocumentoFiscal"
  - "hooks/use-financeiro.ts: useCreatePagamento [409,422] + onSettled (6 keys); useDeletePagamento/useDeleteHonorario [409] + onSettled"
affects: [134-12, 134-13, 134-14]
tech-stack:
  added: []
  patterns:
    - "Exact-authority read gate (hasPermission) where the backend checks an exact authority, instead of the scoped fallback"
    - "Pure error-interpretation module returning a discriminated union; screens only decide placement"
key-files:
  created:
    - web/src/lib/idempotencia.ts
    - web/src/lib/idempotencia.test.ts
    - web/src/lib/erros-emissao.ts
    - web/src/lib/erros-emissao.test.ts
    - web/src/schemas/financeiro.test.ts
  modified:
    - web/src/types/faturacao.ts
    - web/src/types/financeiro.ts
    - web/src/schemas/financeiro.ts
    - web/src/hooks/use-faturacao.ts
    - web/src/hooks/use-financeiro.ts
decisions:
  - "podeLerDocumentosFiscais(perms) = hasPermission(perms, 'financeiro:view') (exact, no edit/manage fallback), so the UI gate for estado-emissao / list / detail matches the backend @PreAuthorize (134-09 edge case). Plans 12/13 pass it as the hooks' enabled flag"
  - "interpretarErroEmissao returns null for 400/404 and other non-409/422 4xx: they are not in semToastParaStatus, so apiFetch already toasted them (UI-SPEC: no duplicate inline)"
  - "Any non-ApiError (TypeError from fetch) and any status >= 500 (including 503 FATURACAO_OCUPADA) map to the network banner, whose retry reuses the same key"
  - "ADQUIRENTE_INCOMPLETO without a recognised campo falls back to a generic banner with the backend message instead of guessing a field"
  - "mensagemGuardaFiscal prefers the backend message and falls back to the Surface 5 copy per code (COPY_GUARDA_*)"
  - "useDocumentoFiscal: semToastParaStatus [404], retry false for 404 (max 3 otherwise), staleTime 60 s; useDocumentosFiscais: keepPreviousData, staleTime 15 s"
  - "No requirement marked complete: EMIS-02/04/05/06/07/10/11/12 need the dialog, list and detail screens of plans 12-13"
metrics:
  duration: "~20 min"
  completed: 2026-10-04
  tasks: 2
  files: 10
---

# Phase 134 Plan 11: Frontend data foundation for the Fatura-Recibo Summary

This plan adds the web building blocks that plans 12 and 13 consume. All of them are DOM-free and covered by vitest:
- types mirroring the plan 05/08/09 DTOs
- the billing-on payment form schema
- an idempotency-key generator that also works over plain-http LAN access
- the 409/422 interpreter with the UI-SPEC copy
- the TanStack Query hooks for billing state, preview, listing and detail

The payment mutations now handle 409/422 inline and invalidate their caches even on error.

## What was built

- **Types.**
  - `CodigoErroFaturacao` gains all 16 Phase 134 codes.
  - New interfaces use the exact backend JSON field names, with amounts as `number`.
  - `Pagamento.documentoFiscal` and the two new `PagamentoCreateRequest` fields are optional, so the billing-off call is unchanged.
- **`schemas/financeiro.ts`.** The existing schemas are byte-for-byte unchanged; the diff only adds lines.
  - `METODOS_PAGAMENTO` uses the backend `rotulo()` labels.
  - `pagamentoFaturadoFormSchema`:
    - `metodo` must be one of the five enum values, otherwise "Escolha o método de pagamento."
    - With `aplicarRetencao`, a `superRefine` accepts a comma or dot decimal with at most 2 places and requires 0 < taxa <= 100, otherwise "A taxa de retenção deve ser superior a 0 e não superior a 100."
  - `paraPedidoPagamentoFaturado` produces a `PagamentoCreateRequest` and never sets the key.
  - No rate literal appears anywhere.
- **`lib/idempotencia.ts`** uses `crypto.randomUUID` when it exists. Otherwise it builds a v4 UUID from `getRandomValues(16)`, setting the version and variant bits. If `crypto` itself is missing, it throws a Portuguese error. A comment explains the secure-context restriction.
- **`lib/erros-emissao.ts`.**
  - `interpretarErroEmissao` returns one of: `campo`, `adquirente`, `chave-reutilizada`, `banner` (optional `codigo`), `rede`, or `null`.
  - `mensagemGuardaFiscal` handles the delete-guard 409s.
  - `construirQueryDocumentosFiscais` builds the query in a fixed key order with blank values omitted.
  - `COPY_*` constants hold the exact UI-SPEC strings.
- **`hooks/use-faturacao.ts`** gets four hooks, two keys and the exact read gate. The Phase 133 hooks and keys are untouched, and paths are relative to `apiFetch`.
- **`hooks/use-financeiro.ts`.**
  - `useCreatePagamento` uses `semToastParaStatus: [409, 422]`. On settle it invalidates the honorário pagamentos/detail/list, conta-corrente, documentos-fiscais and estado-emissao caches.
  - Both delete hooks use `[409]` and invalidate on settle.
  - Every existing caller in `financeiro/[id]/page.tsx` already catches and shows its own toast or inline error, so suppressing the automatic 409 toast hides nothing; it only removes a duplicate.

## Verification

| Check | Result |
|-------|--------|
| Task 1 RED: `vitest run financeiro.test.ts idempotencia.test.ts` | 26 failed (missing exports) |
| Task 1 GREEN | financeiro.test.ts 29/29, idempotencia.test.ts 5/5 |
| Task 2 RED: `vitest run erros-emissao.test.ts` | failed to import (module missing) |
| Task 2 GREEN | erros-emissao.test.ts 33/33 |
| `pnpm exec vitest run` (full) | 11 files, 157/157 |
| `pnpm exec tsc --noEmit` | clean |
| `pnpm lint` | 0 errors; 20 warnings, all in files this plan does not touch |
| `pnpm verify:faturacao` (133 gate) | OK |

**Acceptance greps:**
- `git diff web/src/schemas/financeiro.ts` has no removed lines.
- The rate-literal grep on `schemas/financeiro.ts` and `types/faturacao.ts` finds nothing.
- `semToastParaStatus` appears 3 times in use-financeiro.ts, and `onSettled` 6 times.
- `/api/v1` appears in neither hooks file.

## Commits

| Task | Phase | Commit | Description |
|------|-------|--------|-------------|
| 1 | RED | 089a65e | Failing schema + idempotency tests |
| 1 | GREEN | a7cbd0a | Types, schema, idempotency helper |
| 2 | RED | 93dd1d5 | Failing erros-emissao / query / read-gate tests |
| 2 | GREEN | ffb8935 | erros-emissao + hooks |

## TDD Gate Compliance

Each task has a `test(134-11)` commit that fails, followed by a `feat(134-11)` commit that passes. No refactor commit was needed.

## Deviations from Plan

### Auto-added

**1. [Rule 2 - Correctness] Exact `financeiro:view` read gate (`podeLerDocumentosFiscais`)**
- **Found during:** Task 2, carrying the 134-09 edge case forward
- **Issue:** The backend checks the exact `financeiro:view` authority for estado-emissao, list and detail. The web `hasScopedPermission` fallback treats edit/manage as view, so a custom role with edit but no view would fire requests that come back 403.
- **Fix:** Added `PERMISSAO_LEITURA_FISCAL` and `podeLerDocumentosFiscais` to `use-faturacao.ts`, built on the existing exact `hasPermission`, with a test. Plans 12/13 should pass it as `enabled` instead of `can.view("financeiro")`.
- **Files modified:** web/src/hooks/use-faturacao.ts, web/src/lib/erros-emissao.test.ts
- **Commit:** 93dd1d5, ffb8935

**Note for plans 12/13:** UI-SPEC Surface 3 says `can.view("financeiro")` for the list page. Use `podeLerDocumentosFiscais(me.permissions)` instead so the gate matches the backend; for every seeded role the result is the same.

## Known Stubs

None. Nothing renders yet; plans 12 and 13 wire these modules into the screens.

## Self-Check: PASSED

- FOUND: all 10 key files
- FOUND: 089a65e, a7cbd0a, 93dd1d5, ffb8935
