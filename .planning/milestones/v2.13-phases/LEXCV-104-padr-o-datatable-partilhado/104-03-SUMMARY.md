---
phase: 104-padr-o-datatable-partilhado
plan: 03
subsystem: ui
tags: [tanstack-table, shadcn, data-table, clientes, processos, badge]

requires:
  - phase: 104-02
    provides: Shared DataTable pattern (web/src/components/shared/data-table/) and DataTableColumnHeader
provides:
  - "web/src/app/(dashboard)/clientes/columns.tsx (ColumnDef<Cliente>[] factory, canEditClientes-gated Ações cell)"
  - "web/src/app/(dashboard)/processos/columns.tsx (ColumnDef<Processo>[] factory, clienteNomeById-threaded Cliente cell)"
  - "Clientes and Processos desktop lists rewired to <DataTable>, decorative non-functional pagers removed"
affects: [104-04, 104-05, 104-06]

tech-stack:
  added: []
  patterns:
    - "columns.tsx as a factory function taking RBAC flags / cross-hook lookup Maps as arguments, since TanStack column defs are plain objects and cannot call hooks"
    - "Per-row hook calls (useDeleteCliente) kept in a small inline cell component (ClienteAcoesCell) rather than lifted into the column def itself"

key-files:
  created:
    - "web/src/app/(dashboard)/clientes/columns.tsx"
    - "web/src/app/(dashboard)/processos/columns.tsx"
  modified:
    - "web/src/app/(dashboard)/clientes/page.tsx"
    - "web/src/app/(dashboard)/processos/page.tsx"

key-decisions:
  - "Clientes' Ações cell kept as a small inline component (ClienteAcoesCell) inside columns.tsx, taking `cliente`/`canEditClientes` props, so it can call useDeleteCliente per row and preserve the window.confirm delete guard verbatim -- matches the plan's own suggested wiring shape."
  - "Processos' columns.tsx exports a columns(clienteNomeById) factory since the Cliente cell must resolve a foreign-key id via a Map built from a sibling useClientes() hook in page.tsx, which columns.tsx cannot call itself."
  - "Deviation (documented below): processos/page.tsx never had a hidden md:block / md:hidden dual-view split -- the desktop Table rendered unconditionally, relying on the Table primitive's own overflow-auto wrapper for narrow viewports. DataTable is wired in the same unconditional way, not wrapped in an invented hidden md:block that would hide the table on mobile with zero fallback."

patterns-established:
  - "columns(...) factory pattern for cross-hook lookups and RBAC-gated cells, reusable by the remaining 3 screens (Pareceres, Financeiro, Documentos) in 104-04/104-05."

requirements-completed: [DTB-02]

duration: ~35min
completed: 2026-07-16
---

# Phase 104: Padrão DataTable Partilhado — Plan 03 Summary

**Clientes and Processos desktop lists migrated to the shared `<DataTable>` (sortable headers, functional client-side pagination, column-visibility toggle), replacing two purely decorative, non-functional pagers, with zero RBAC/badge/data regression**

## Performance

- **Duration:** ~35 min (includes a fresh `pnpm install` — this worktree had no `node_modules`)
- **Completed:** 2026-07-16T11:41:29Z
- **Tasks:** 2
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments
- `clientes/columns.tsx`: `ColumnDef<Cliente>[]` factory porting `ClienteRow`'s cells (Nome/Razão Social with avatar-initials+sub-badges, Tipo, NIF/documento, Contacto, Ações) — Ações kept as a small inline `ClienteAcoesCell` component so the per-row `useDeleteCliente` hook call and `window.confirm("Remover este cliente?")` guard survive unchanged.
- `processos/columns.tsx`: `columns(clienteNomeById)` factory porting the row block (Número do Processo with Entrada/Responsável sub-lines, Cliente via cross-hook Map, Tribunal, Área Jurídica, Estado with the verbatim `estadoVariant` mapping + risco-de-prazo sub-badge via `prazosRiscoToVariant`/`prazosRiscoToLabel`, Ações).
- Both `page.tsx` desktop branches now render `<DataTable columns={...} data={...} />`; both decorative fake `‹ 1 2 3 … ›` pager footers (no `onClick` handlers, no real pagination) are gone, replaced by the shared `DataTablePagination` footer (real client-side sort/paginate).
- `pnpm --dir web build` (full Next build/typecheck) passes cleanly with both screens migrated.

## Task Commits

Each task was committed atomically:

1. **Task 1: Clientes — extract columns.tsx and rewire the desktop branch to DataTable** — `6acb956` (feat)
2. **Task 2: Processos — extract columns.tsx and rewire the desktop branch to DataTable** — `7b0e67f` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/columns.tsx` — `ColumnDef<Cliente>[]` factory + `ClienteAcoesCell` inline component (Ver/Imprimir/Editar/Eliminar, RBAC-gated, delete guard preserved)
- `web/src/app/(dashboard)/clientes/page.tsx` — desktop branch now `<DataTable columns={clienteColumns} data={clientes.data} />`; `ClienteRow` removed; unused `Table`/`useDeleteCliente`/`cn`/`Printer`/`Trash2` imports removed; mobile branch, filter Card, `useClientes`/filters/onSubmit/onClear, outer guard all byte-unchanged
- `web/src/app/(dashboard)/processos/columns.tsx` — `columns(clienteNomeById)` factory (Número/Cliente/Tribunal/Área Jurídica/Estado+risco/Ações)
- `web/src/app/(dashboard)/processos/page.tsx` — desktop Table replaced with `<DataTable columns={processoColumns} data={processos.data} />` and the decorative pager `CardContent` block removed; unused `Table`/`Tooltip`/`AlertCircle`/`MoreVertical`/`prazosRisco*` imports removed; filter Card, `useProcessos`/filters, outer guard byte-unchanged

## Decisions Made
- Kept Clientes' Ações cell as a small inline component (`ClienteAcoesCell`) rather than lifting the delete handler into a `meta`/closure — mirrors the previous `ClienteRow` shape and is the simpler of the two wiring options the plan explicitly allowed.
- Exported Processos' columns as a `columns(clienteNomeById)` factory (not a plain array) since the Cliente cell needs a lookup Map built from a sibling `useClientes()` hook call in `page.tsx` — column defs cannot call hooks themselves.
- Did not memoize `columns(...)`/`processoColumns` with `React.useMemo` in Processos' `page.tsx` (matches the codebase's existing non-memoized `clienteNomeById` construction one line above it); Clientes' `page.tsx` does use `React.useMemo` for `clienteColumns` since it's gated only by the stable `canEditClientes` boolean, a cheap and clearly-correct memoization key.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug/plan-reality mismatch] `processos/page.tsx` has no `hidden md:block` / `md:hidden` dual-view split**
- **Found during:** Task 2, re-reading the full file before editing
- **Issue:** The plan's frontmatter `must_haves` and Task 2's `<action>` text both assume Processos has an existing `hidden md:block` desktop branch (paralleling Clientes) to replace, and a `md:hidden` mobile-card branch to leave untouched. A full re-read of `processos/page.tsx` (all 419 lines, confirmed via `grep -n "md:hidden\|hidden md:block"` returning zero matches) shows this screen never had that split — its `Table` rendered unconditionally at all viewport widths, relying only on the `Table` primitive's own internal `overflow-auto` wrapper for narrow-screen horizontal scroll (the "scroll horizontal em tabelas complexas" pattern from the v2.3 responsiveness decision, applied here instead of a card view).
- **Fix:** Wired `<DataTable columns={processoColumns} data={processos.data} />` in the exact same unconditional position the old `<Table>` occupied, rather than inventing a `hidden md:block` wrapper with no corresponding `md:hidden` fallback — doing the latter would have hidden the entire processos list on mobile viewports, a real regression the plan's own stated intent ("mobile card branches ... byte-unchanged") clearly did not intend to introduce.
- **Files modified:** `web/src/app/(dashboard)/processos/page.tsx`
- **Verification:** `pnpm --dir web build` succeeds; `grep -c "hidden md:block" processos/page.tsx` is 0 both before and after this plan, confirming no new inconsistent partial-responsive markup was introduced.
- **Committed in:** `7b0e67f` (Task 2 commit)

**2. [Rule 3 - Blocking, infra only] Fresh `pnpm install` required in this worktree**
- **Found during:** Task 1 verification (`pnpm --dir web exec tsc --noEmit`)
- **Issue:** This git worktree had no `node_modules` at all (worktrees don't share installed dependencies with the main checkout) — `tsc`/`next` binaries were unavailable, blocking every verification command.
- **Fix:** Ran `pnpm install` in `web/` (installed the exact versions already locked in the committed `pnpm-lock.yaml` — no dependency changes, no `package.json`/`pnpm-lock.yaml` diff). Also created a local, gitignored `web/.env.local` (from `web/.env.example`) since `next build` requires `BACKEND_API_ORIGIN`/`NEXT_PUBLIC_API_BASE_PATH` to be set and none existed in this fresh worktree.
- **Files modified:** none tracked by git (`node_modules/` is gitignored; `web/.env.local` is gitignored, confirmed via `git check-ignore -v`)
- **Verification:** `pnpm --dir web exec tsc --noEmit` and `pnpm --dir web build` both run successfully afterward.
- **Committed in:** N/A (no tracked files changed by this step)

---

**Total deviations:** 2 (1 plan/reality mismatch avoided via Rule 1, 1 infra-only blocking fix via Rule 3)
**Impact on plan:** Both were necessary to complete verification without introducing a real UX regression (item 1) or being blocked entirely (item 2). No scope creep — no files outside the plan's `files_modified` list were touched.

## Issues Encountered
- `pnpm --dir web exec tsc --noEmit` reports the same 3 pre-existing failures already logged in `.planning/phases/LEXCV-104-padr-o-datatable-partilhado/deferred-items.md` (unrelated `*.test.ts` files importing an unconfigured `vitest` module, committed in Phase 97-02, long before this phase) — confirmed unchanged by this plan's diff, not fixed, per the executor scope-boundary rule.
- `pnpm --dir web lint` reports pre-existing errors/warnings entirely in files this plan never touched (`data-table.tsx` from 104-02, `dashboard-shell.tsx`, `processos/[id]/page.tsx`, `pareceres/nova/page.tsx`, `processos/novo/page.tsx`, `settings/page.tsx`, `user-profile-form.tsx`) — confirmed via targeted grep that zero lint findings reference `clientes/page.tsx`, `clientes/columns.tsx`, `processos/page.tsx`, or `processos/columns.tsx`. Out of this plan's scope, not fixed.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- The `columns(...)` factory pattern (RBAC-gated inline cell component for Clientes, cross-hook lookup Map argument for Processos) is proven and ready for 104-04 (Pareceres, Financeiro) and 104-05 (Documentos, `/notificacoes` Pagination swap) to reuse — both screens need the same cross-hook lookup Map pattern already established here for Processos' Cliente column.
- No blockers. `pnpm --dir web build` is green with both migrated screens included in the full route manifest.

## Self-Check: PASSED

Both created files (`web/src/app/(dashboard)/clientes/columns.tsx`, `web/src/app/(dashboard)/processos/columns.tsx`) confirmed present on disk; both task commit hashes (`6acb956`, `7b0e67f`) confirmed present in `git log --oneline`.

---
*Phase: 104-padr-o-datatable-partilhado*
*Completed: 2026-07-16*
