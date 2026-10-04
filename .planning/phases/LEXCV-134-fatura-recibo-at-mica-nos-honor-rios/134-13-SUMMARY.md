---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 13
subsystem: web-financeiro
tags: [fiscal, efatura, frontend, data-table, server-pagination, read-only]
requires:
  - "134-11: useDocumentosFiscais, useDocumentoFiscal, podeLerDocumentosFiscais, DocumentoFiscalResumo/Detalhe types"
  - "134-09: GET /documentos-fiscais (page 0-based, size 1..100), GET /documentos-fiscais/{id} (404 for foreign/unknown)"
provides:
  - "DataTable optional manual pagination (manualPagination, pageCount, pagination, onPaginationChange), initialColumnVisibility, emptyMessage"
  - "/financeiro/documentos-fiscais list page (filters + page/size in URL)"
  - "/financeiro/documentos-fiscais/[id] read-only detail page"
  - "Financeiro header 'Documentos fiscais' outline button"
affects: [134-14]
tech-stack:
  added: []
  patterns:
    - "URL search params as the single source of filter/pagination state (router.replace, scroll false) under a Suspense boundary"
key-files:
  created:
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx
  modified:
    - web/src/components/shared/data-table/data-table.tsx
    - web/src/app/(dashboard)/financeiro/page.tsx
decisions:
  - "Pages and entry button are gated with podeLerDocumentosFiscais (exact financeiro:view, per 134-11 and the orchestrator instruction), not can.view(\"financeiro\"); identical for all seeded roles"
  - "URL 'page' is 1-based for humans (omitted on page 1); converted to the backend's 0-based page; size limited to 10/20/50 (omitted at 10); invalid values fall back to defaults"
  - "The Cliente filter (useClientes) renders only with clientes:view, so a financeiro-only role never triggers a 403 toast"
  - "Columns are not client-sortable: the server order (data desc) applies to the whole result, sorting one page would mislead"
  - "Ambiente column hidden below md via initialColumnVisibility computed with matchMedia in a component that mounts only after data arrives (client-only, no hydration mismatch)"
metrics:
  duration: "~25 min"
  completed: 2026-10-04
  tasks: 2
  files: 5
---

# Phase 134 Plan 13: Fiscal documents list and read-only detail Summary

Users who hold `financeiro:view` can now open "Documentos fiscais" from the Financeiro header. The list filters by cliente, period, type and state, and paginates on the server. Each document opens a detail page that shows the emitter and buyer as recorded at emission, the line, the values, the "Pendente" communication state and the simulation mark, which is always visible. Neither page has any mutating action.

## What was built

- **`data-table.tsx`:** new optional props.
  - With `manualPagination`, the table drops `getPaginationRowModel` and uses the caller's `pageCount`, `state.pagination` and `onPaginationChange`.
  - `initialColumnVisibility` and `emptyMessage` (default text unchanged) are also new.
  - The existing callers are untouched and compile. In `financeiro/page.tsx` only the header changed, not its DataTable usage.
- **`financeiro/page.tsx`:** an outline "Documentos fiscais" button with the `Receipt` icon sits next to "Novo honorário". The gate is threaded through as a prop to `FinanceiroContent`.
- **`documentos-fiscais/columns.tsx`:** the columns are Número (`font-mono` link), Data, Cliente, Tipo, Total, Estado and Ambiente.
  - Badges: Tipo is secondary; Estado ("Pendente") and Ambiente ("Simulado") are outline.
  - Total is right-aligned with `tabular-nums`.
- **`documentos-fiscais/page.tsx`:**
  - The content sits inside `<React.Suspense>`, because Next 16 requires it for `useSearchParams` with static prerender (checked in `node_modules/next/dist/docs/.../use-search-params.md`).
  - Breadcrumb, back button, `h1` and description follow the UI-SPEC.
  - Filters: Cliente (Combobox with "Todos os clientes"), De/Até dates, Tipo and Estado (native selects).
    - Every filter change resets the page.
    - "Limpar filtros" appears only while a filter is active.
    - If "Até" is before "De", the page shows the inline period error and skips the query.
  - If the server reports fewer pages than the current one, the page is clamped.
  - States: skeleton while loading; an empty state with no filters; a different empty state with filters plus "Limpar filtros"; an error message with "Tentar novamente".
- **`documentos-fiscais/[id]/page.tsx`:**
  - The page reads `React.use(params)`, with `params` typed as a Promise, and keeps the id as a string; `Number()` is never applied.
  - A 404 shows "Documento fiscal não encontrado" with "Voltar aos documentos fiscais". Any other error shows "Não foi possível carregar…" with "Tentar novamente". Skeleton Cards show while loading.
  - Header: the `h1` is the document number in `font-mono`. The badges are Fatura-Recibo, Pendente (with the helper line, no tooltip) and the Info "Simulação — sem validade fiscal" badge, followed by the neutral notice.
  - Emitente and Adquirente Cards show only snapshot data; the exemption reason appears only when the emitter is ISENTO. The Adquirente Card has the helper line.
  - The "Linha do documento" table, the "Valores" `dl` (retention row only when it is above 0), and "Ligações" links to `#pagamento-{id}`, the honorário and the cliente (the cliente link needs clientes:view).
  - The page ends with the immutability line. There are no mutation hooks.

## Verification

| Check | Result |
|-------|--------|
| `pnpm exec tsc --noEmit` | clean |
| `pnpm lint` | 0 errors; 20 warnings, all pre-existing (the `useReactTable` incompatible-library warning in data-table.tsx predates this plan) |
| `pnpm exec vitest run` | 157/157 |
| `pnpm build` | success; lists `○ /financeiro/documentos-fiscais` and `ƒ /financeiro/documentos-fiscais/[id]` |
| `pnpm verify:faturacao` | OK |
| Detail page mutation grep (`useMutation\|method: "(DELETE\|PUT\|PATCH\|POST)"\|Apagar\|Editar\|Anular`) | no matches |
| `Number(` on the detail page | no matches |
| `git diff` on clientes/processos/documentos/pareceres/plataforma DataTable callers | empty |

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | ba67d2b | DataTable manual pagination, header button, list page and columns |
| 2 | bcfc778 | Read-only detail page |

## Deviations from Plan

**1. [Rule 2 - Correctness] Read gate uses `podeLerDocumentosFiscais`, not `can.view("financeiro")`**
- **Found during:** Task 1. This carries forward the 134-11 note and was explicitly required by the orchestrator.
- **Issue:** The backend checks the exact `financeiro:view` authority. The scoped `can.view` fallback (edit/manage imply view) would let a custom edit-only role open pages whose requests then come back 403.
- **Fix:** Both pages and the header button use the exact gate. As a result, the acceptance grep `can.view("financeiro")` on the list page does not match, and that is intentional. The result is identical for every seeded role.
- **Commit:** ba67d2b, bcfc778

**2. [Rule 2] Cliente filter only rendered with `clientes:view`**
- **Issue:** `useClientes` has no `enabled` flag, so a role without clientes:view would get a 403 toast on every visit.
- **Fix:** The filter is hidden for roles without clientes:view.
- **Commit:** ba67d2b

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: documentos-fiscais/page.tsx, columns.tsx, [id]/page.tsx
- FOUND: ba67d2b, bcfc778
