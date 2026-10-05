---
phase: 135-nota-de-cr-dito
plan: 12
subsystem: web-financeiro
tags: [frontend, nota-de-credito, estorno, documentos-fiscais, source-gate]
requires:
  - "135-08: estorno exposed in honorário payments + 409 PAGAMENTO_ESTORNO"
  - "135-10: Pagamento.estorno, DocumentoFiscalResumo.documentoOrigemNumero, mensagemGuardaFiscal PAGAMENTO_ESTORNO"
  - "135-11: verify:documentos-fiscais Phase 135 section"
provides:
  - "Payments list estorno row (UI-SPEC Surface 3)"
  - "Fiscal documents list Tipo filter FR/NC and 'Corrige {FR}' sub-line (UI-SPEC Surface 4)"
  - "verify:documentos-fiscais assertions for Surfaces 3-4"
affects: [135-13]
tech-stack:
  added: []
  patterns:
    - "URL filter whitelist (tipoParam === 'FR' || tipoParam === 'NC')"
key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/financeiro/[id]/pagamentos-card.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx
    - web/scripts/verify-documentos-fiscais.mjs
decisions:
  - "Per the UI-SPEC checker clarification, 'Estorno (NC n.º …)' lives only in the existing 'Documento fiscal' cell (the table has no 'Descrição / origem' column)"
  - "The estorno branch comes before the documentoFiscal and delete branches in the actions cell, so no ApagarPagamento is ever rendered for an estorno, not even a disabled one"
  - "Besides the planned assertions, the gate also forbids client-side negation of valorPago in the payments card and red styling in the documents columns (UI-SPEC: red only for the estorno in the payments list)"
metrics:
  duration: "~10 min"
  completed: 2026-10-05
  tasks: 2
  files: 4
---

# Phase 135 Plan 12: Estorno row and FR/NC documents list Summary

This plan covers the last two UI-SPEC surfaces. The gate is extended without changing any Phase 134 assertion.

**Payments list (Surface 3).** In the honorário payments list, an NC estorno row shows:
- the backend's negative amount in red (`text-red-600 dark:text-red-400 tabular-nums`) with the formatter's minus sign, never negated on the client
- "—" as the method
- "Estorno (NC n.º SIM-NC-…)", linking to the NC
- "Estorno: não pode ser apagado." in place of any delete control

Legacy rows and FR-faturado rows are unchanged, the `pagamento-{id}` anchor is kept, and totals are still rendered from backend values.

**Fiscal documents list (Surface 4):**
- The list can be filtered by Tipo "Nota de Crédito". The URL `tipo` value is whitelisted to `FR` or `NC`.
- The header copy now mentions notas de crédito.
- NC rows show "Corrige {número da FR}" from the backend field `documentoOrigemNumero`.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | Estorno row in the payments card | 07357fc |
| 2 | Tipo filter FR/NC, NC rows, source-gate extension | 317b13e |

## Verification

- `pnpm verify:documentos-fiscais`: OK. New assertions:
  - The payments card contains "Estorno: não pode ser apagado.", "Estorno (NC n.º" and `p.estorno`, and has no `-p.valorPago` or `valorPago * -1`.
  - `erros-emissao.ts` contains `PAGAMENTO_ESTORNO`.
  - The list contains the NC option, the whitelist and the new description.
  - The columns contain `documentoOrigemNumero` and "Corrige ", and no `text-red-600`.
- **Negative self-checks** (both restored afterwards, and the gate went back to exit 0):
  - Appending `// const v = -p.valorPago;` to the payments card: exit 1, "contem negacao do valor no cliente".
  - Renaming `documentoOrigemNumero` in the columns: exit 1, "falta \"documentoOrigemNumero\"".
- `pnpm verify:faturacao`: OK.
- `npx tsc --noEmit`: clean.
- `pnpm lint`: 0 errors. The 20 warnings are unchanged and all in unrelated files.
- `pnpm exec vitest run`: 12 files, 244 tests, all green.
- **Acceptance greps:**
  - The payments card contains "Estorno: não pode ser apagado.", "Estorno (NC n.º" and `p.estorno`; the negation regex matches 0 times.
  - The list contains the exact `<NativeSelectOption value="NC">Nota de Crédito</NativeSelectOption>` line and the new description.
  - The columns contain `documentoOrigemNumero`.
  - The gate script contains "Estorno (NC n.º", `PAGAMENTO_ESTORNO` and `documentoOrigemNumero`.

## Deviations from Plan

**1. [Rule 2 - Hardening of the gate] Two extra source-gate assertions, beyond the plan's list**
- **No client-side negation:** the gate forbids negating `valorPago` in the payments card. This turns the plan's acceptance grep into a permanent gate.
- **No red in the documents list:** `columns.tsx` must not contain `text-red-600`. This pins the UI-SPEC rule that red is used only for the estorno in the payments list.
- **Commit:** 317b13e

## Threat Flags

None. The changes match the plan's threat model:
- T-135-43: the `tipo` whitelist.
- T-135-44: no delete control for estornos, with the backend 409 as a safety net.

## Known Stubs

None.

## Self-Check: PASSED

- All 4 modified files are present.
- Commits 07357fc and 317b13e are present in `git log`.
