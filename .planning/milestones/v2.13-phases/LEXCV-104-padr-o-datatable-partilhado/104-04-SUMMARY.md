---
phase: 104-padr-o-datatable-partilhado
plan: 04
subsystem: ui
tags: [tanstack-table, shadcn, data-table, badge, pareceres, financeiro]

requires:
  - phase: 104-02
    provides: "Shared DataTable pattern (web/src/components/shared/data-table/) -- DataTable, DataTableColumnHeader, DataTablePagination, DataTableViewOptions"
provides:
  - "web/src/app/(dashboard)/pareceres/columns.tsx -- columns(clienteNomeById) factory"
  - "web/src/app/(dashboard)/financeiro/columns.tsx -- columns(processoById, clienteNomeById) factory, first-ever Table adoption for Financeiro"
  - "Pareceres and Financeiro desktop lists migrated to shared <DataTable>"
affects: [104-06]

tech-stack:
  added: []
  patterns:
    - "columns(...) factory functions accepting cross-hook lookup Maps (clienteNomeById, processoById) as closure arguments, since ColumnDef objects cannot call hooks directly"
    - "Status/type Badge cell renderers reuse the exact variant mapping already established elsewhere in the same file (Pareceres' statusVariant; Financeiro's mobile-card green/blue/amber mapping) rather than inventing new colors"

key-files:
  created:
    - "web/src/app/(dashboard)/pareceres/columns.tsx"
    - "web/src/app/(dashboard)/financeiro/columns.tsx"
  modified:
    - "web/src/app/(dashboard)/pareceres/page.tsx"
    - "web/src/app/(dashboard)/financeiro/page.tsx"

key-decisions:
  - "statusVariant/formatDate (Pareceres) and formatMoneyCVE/calcHonorarioStatus/formatDate (Financeiro) were duplicated verbatim into each columns.tsx rather than imported from page.tsx -- avoids a page.tsx<->columns.tsx circular import while both page.tsx copies remain in use by the untouched mobile card branches"
  - "Financeiro's now-dead statusBadgeClass object was deleted from page.tsx entirely (not just unused) since the raw <table> that consumed it no longer exists -- completes the migration rather than leaving orphaned dead code"
  - "Financeiro's DataTable data prop is fed filteredList (the screen's existing client-side-filtered array), not the full honorarios.data -- preserves the existing filter mechanism unchanged; DataTable itself never configures getFilteredRowModel()"

patterns-established:
  - "Per-screen columns.tsx factory signature: columns(...lookupMaps) => ColumnDef<T>[], with DataTableColumnHeader wrapping every header (sortable and non-sortable alike, since it self-detects column.getCanSort())"

requirements-completed: [DTB-02]

duration: ~20min
completed: 2026-07-16
---

# Phase 104: Padrão DataTable Partilhado — Plan 04 Summary

**Pareceres and Financeiro desktop lists migrated to the shared TanStack-Table-based `<DataTable>`, with Financeiro's first-ever adoption of the reconciled `Table` primitive and a mandatory Badge migration off its hand-rolled `statusBadgeClass` span**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-16T11:34:45Z
- **Tasks:** 2
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments
- Extracted `pareceres/columns.tsx` as a `columns(clienteNomeById)` factory (Estado/Cliente/Prioridade/Prazo/Criado sortable, Ações non-sortable), wired the desktop branch to `<DataTable>`, dual data-source toggle (`usePareceres`/`usePesquisarPareceres`) unchanged
- Extracted `financeiro/columns.tsx` as a `columns(processoById, clienteNomeById)` factory — Financeiro's desktop branch was a raw `<table>`/`<thead>`/`<tbody>` with zero `Table` primitive usage before this plan; it now renders exclusively through `<DataTable>` (which itself renders through the reconciled `Table`/`TableRow`/`TableCell` primitives)
- Financeiro's Estado column now uses the real `Badge` component (`green`/`blue`/`amber`), reusing the exact mapping already implied by this same file's mobile card branch — the hand-rolled `statusBadgeClass` span and its backing object are both gone
- Full `pnpm --dir web build` (Turbopack, TypeScript, static generation for all 24 routes) passes green

## Task Commits

Each task was committed atomically:

1. **Task 1: Pareceres — extract columns.tsx and rewire the desktop branch to DataTable** — `e59bf17` (feat)
2. **Task 2: Financeiro — first Table adoption + DataTable + Badge migration off statusBadgeClass** — `905aae4` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/pareceres/columns.tsx` — `columns(clienteNomeById)` factory: Estado (Badge, `enableHiding:false`), Cliente (link via Map), Prioridade, Prazo, Criado (all sortable), Ações (`Tooltip`+ghost `MoreVertical`, `enableSorting:false`/`enableHiding:false`)
- `web/src/app/(dashboard)/pareceres/page.tsx` — desktop `<Table>` branch replaced with `<DataTable columns={columns(clienteNomeById)} data={rows} />`; `Table`/`TableBody`/`TableCell`/`TableHead`/`TableHeader`/`TableRow`/`Tooltip`/`TooltipContent`/`TooltipTrigger`/`MoreVertical` imports removed (moved into columns.tsx); mobile card branch, filter/pesquisa Cards, hooks, outer guards untouched
- `web/src/app/(dashboard)/financeiro/columns.tsx` — `columns(processoById, clienteNomeById)` factory: Honorário (link, `enableHiding:false`), Processo (link via Map), Cliente (link via Map), Total (`formatMoneyCVE`), Data do Acordo (`formatDate`), Estado (real `Badge`, green/blue/amber) — all sortable
- `web/src/app/(dashboard)/financeiro/page.tsx` — raw `<table>`/`<thead>`/`<tbody>` desktop branch replaced with `<DataTable columns={columns(processoById, clienteNomeById)} data={filteredList} />`; dead `statusBadgeClass` object deleted; CSV export, KPI cards, filter selects, mobile card branch, and outer guards untouched

## Decisions Made
- Duplicated `statusVariant`/`formatDate` (Pareceres) and `formatMoneyCVE`/`calcHonorarioStatus`/`formatDate` (Financeiro) into each `columns.tsx` rather than importing from `page.tsx`, since both page files still need their own copies for the untouched mobile card branches and a `page.tsx` ↔ `columns.tsx` circular import would otherwise result.
- Deleted Financeiro's `statusBadgeClass` object entirely from `page.tsx` (not left as unused dead code) once its only consumer — the raw `<table>` — was removed, completing the Badge migration rather than leaving an orphaned mapping behind.
- Financeiro's `<DataTable>` receives `filteredList` (the screen's pre-existing client-side-filtered array from `filtroProcesso`/`filtroStatus`/`filtroDataDe`/`filtroDataAte`), preserving today's filter mechanism unchanged; `DataTable` itself never configures `getFilteredRowModel()`, matching the locked architecture decision.

## Deviations from Plan

None — plan executed exactly as written. Both tasks' automated verify commands (grep checks + `tsc --noEmit` for Task 1, grep checks + full `pnpm --dir web build` for Task 2) passed without needing any auto-fixes.

## Issues Encountered
- This worktree had no `node_modules` installed (fresh worktree checkout does not carry over `node_modules`, consistent with the Phase 101 lesson recorded in STATE.md). Ran `pnpm install --frozen-lockfile` in `web/` before the Task 2 build gate — the lockfile already had `@tanstack/react-table@8.21.3` from 104-02, so this was a plain install, not a dependency change.
- `pnpm --dir web build` initially failed with `Error: BACKEND_API_ORIGIN is required` (no `.env.local` in this worktree, only `.env.example`). Ran the build with `BACKEND_API_ORIGIN=http://localhost:8080 NEXT_PUBLIC_API_BASE_PATH=/api/v1` set inline (matching `.env.example`'s documented dev values) — this is a build-time env requirement unrelated to plan scope, not a code change.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- Pareceres and Financeiro are both fully migrated to the shared DataTable pattern; no known stubs or gaps.
- Financeiro is now on the reconciled `Table` primitive and the closed Badge vocabulary, closing the gap flagged in `104-UI-SPEC.md` Scope note #1/#2.
- No blockers for 104-03 (Clientes/Processos) or 104-05 (Documentos/Notificações), which run in parallel worktrees with zero file overlap confirmed by the plan-checker.
- DTB-02 is only fully satisfied once 104-03/104-05 also land (Clientes, Processos, Documentos) — this plan covers 2 of the 5 screens.

## Self-Check: PASSED

Both created files (`web/src/app/(dashboard)/pareceres/columns.tsx`, `web/src/app/(dashboard)/financeiro/columns.tsx`) confirmed present on disk; both task commit hashes (`e59bf17`, `905aae4`) confirmed present in `git log`.

---
*Phase: 104-padr-o-datatable-partilhado*
*Completed: 2026-07-16*
