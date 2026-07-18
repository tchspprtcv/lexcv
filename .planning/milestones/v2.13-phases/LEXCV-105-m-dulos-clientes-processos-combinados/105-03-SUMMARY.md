---
phase: 105-m-dulos-clientes-processos-combinados
plan: 03
subsystem: ui
tags: [react, nextjs, shadcn, tanstack-table, table, avatar, data-table]

# Dependency graph
requires:
  - phase: 105-02
    provides: TabsContent wrappers for all 8 Ficha de Processo tabs (Timeline, Partes, Fases, Decisões, Factos, Testemunhas, Documentos, Auditoria), unblocking this plan's inner tab-content migration
provides:
  - "Partes, Fases and Testemunhas tabs on processos/[id]/page.tsx render through reconciled Table/TableHeader/TableBody/TableRow/TableHead/TableCell primitives instead of raw <table>/<thead>/<tbody>/<td> markup"
  - "Testemunhas 'Nome' cell shows an Avatar (size=sm) with derived initials before the name"
  - "Processo Documentos tab renders via the shared <DataTable> (Phase 104 pattern) fed by new documentos-columns.tsx, replacing the <ul>/<li> ProcessoDocumentoRow list"
affects: [105-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "documentos-columns.tsx: columns(canEditDocumentos) ColumnDef<Documento>[] factory scoped to a single processo — models directly on documentos/columns.tsx (Phase 104) but drops the 'Processo' column (redundant, every row already scoped to this processo) and the 'Cliente' column (documents uploaded from this tab only ever carry processo_id, never cliente_id)"
    - "wireSizeAndDate() helper in documentos-columns.tsx carries ProcessoDocumentoRow's tamanho/createdAt wire-shape workaround (backend serializes Java field names, not the frontend Documento type's size/created_at) into the Tamanho/Criado column accessors"
    - "Raw <table>/<thead>/<tbody>/<td> -> Table/TableHeader/TableBody/TableRow/TableHead/TableCell is a markup-only swap: existing outer overflow-x-auto wrapper, Dialog 'Adicionar' flows, RBAC gates, inline-edit state (Fases NativeSelect status + Guardar), and window.confirm delete handlers all preserved verbatim"

key-files:
  created:
    - "web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx"
  modified:
    - "web/src/app/(dashboard)/processos/[id]/page.tsx"

key-decisions:
  - "Dropped both the 'Processo' and 'Cliente' columns from documentos-columns.tsx (UI-SPEC/PATTERNS left both at Claude's discretion) — Processo is redundant since every row is already scoped to processoId via useDocumentos({ processo_id }), and Cliente would always render '—' since ProcessoDocumentosTab's upload flow only ever sets processo_id, never cliente_id."
  - "Removed the now-unused ProcessoDocumentoRow component and its two private helpers (formatDocumentoSize, formatDocumentoDate) after confirming via grep that ProcessoDocumentoRow was their only caller anywhere in the file — dead-code cleanup directly caused by this plan's own DataTable swap, not a separate scope expansion."
  - "Decisões and Factos tabs (also raw <table> in this same file) were deliberately left untouched — neither 105-CONTEXT.md nor 105-UI-SPEC.md/105-PATTERNS.md name them in the Table-primitive migration scope (only Partes/Fases/Testemunhas), unlike Partes/Fases which are explicitly named."
  - "Split the single combined page.tsx edit into two atomic commits matching the plan's task boundaries (Task 1: DataTable swap; Task 2: Table primitives + Avatar) by temporarily reverting Task 2's edits, committing Task 1 in isolation, verifying pnpm build green on that intermediate state, then reapplying Task 2's edits and committing separately — both commits independently build clean."

requirements-completed: [CLP-02, CLP-04]

# Metrics
duration: ~25min
completed: 2026-07-16
---

# Phase 105 Plan 03: Ficha de Processo Partes/Fases/Testemunhas Table Migration + Documentos DataTable Summary

**Partes/Fases/Testemunhas tabs converted from raw `<table>` markup to reconciled `Table` primitives (Testemunhas gains an `Avatar` in the Nome cell), and the Documentos tab's `<ul>` list replaced by the shared `DataTable` fed by a new `documentos-columns.tsx` factory.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-07-16T17:00:00Z (approx, immediately following 105-02's completion)
- **Completed:** 2026-07-16T17:26:08Z
- **Tasks:** 2 completed
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- New `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx` exports `columns(canEditDocumentos: boolean): ColumnDef<Documento>[]`, modeled directly on Phase 104's `documentos/columns.tsx` — reuses `confidencialidadeVariant()` verbatim, ports `ProcessoDocumentoRow`'s existing download/delete/`window.confirm` handlers into a `DocumentoAcoesCell` component, and carries the `tamanho`/`createdAt` wire-shape workaround into the Tamanho/Criado column accessors via a `wireSizeAndDate()` helper. Column set: Nome, Tipo, Confid., Versão, Tamanho, Criado, Ações (Processo and Cliente columns both dropped — see Decisions).
- `ProcessoDocumentosTab`'s populated branch now renders `<DataTable columns={columns(canEditDocumentos)} data={documentos} getRowId={(d) => d.id} />` in place of the `<ul>`/`ProcessoDocumentoRow` list; the `isLoading`/`isError`/empty ("Nenhum documento registado.") guards are unchanged.
- The now-orphaned `ProcessoDocumentoRow` component and its two private helpers (`formatDocumentoSize`, `formatDocumentoDate`) were removed after confirming (via grep) they had no other callers.
- Partes, Fases, and Testemunhas tabs all migrated from raw `<table>/<thead>/<tbody>/<tr>/<th>/<td>` to `Table/TableHeader/TableBody/TableRow/TableHead/TableCell` (imported fresh — this file didn't previously import the `Table` primitives, unlike its `clientes/[id]/page.tsx` sibling). Markup-only swap: the outer `overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0` wrapper, Fases' inline `NativeSelect` status picker + "Guardar" `Button`, all three tabs' "Adicionar" `Dialog` flows, RBAC gates, and the Testemunhas `window.confirm` delete handler are all preserved verbatim.
- Testemunhas' "Nome" `TableCell` now renders `<Avatar size="sm"><AvatarFallback>{deriveInitials(t.nome)}</AvatarFallback></Avatar>` before the name, via a new `deriveInitials()` helper matching the logic already established in `clientes/[id]/page.tsx` (105-01). Tipo/Contacto/Ações cells are unchanged; Partes intentionally has no Avatar (excluded by CLP-04).
- `pnpm build` passes clean (24 routes) both after Task 1 alone and after the full Task 1+2 combined state. `pnpm lint` shows zero new issues — only the 2 pre-existing findings already documented in `deferred-items.md` from 105-02 (`textareaClassName` unused, `react-hooks/set-state-in-effect` on the `?tab=` re-sync effect).

## Task Commits

Each task was committed atomically:

1. **Task 1: New documentos-columns.tsx + swap ProcessoDocumentosTab ul -> DataTable** - `53213b7` (feat)
2. **Task 2: Partes/Fases/Testemunhas raw table -> Table primitives + Testemunhas Avatar** - `78a47f0` (feat)

**Plan metadata:** committed with this SUMMARY (see final commit below).

## Files Created/Modified
- `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx` - New `columns(canEditDocumentos)` ColumnDef factory for the processo-scoped Documentos DataTable
- `web/src/app/(dashboard)/processos/[id]/page.tsx` - Documentos tab `<ul>` -> `<DataTable>`; Partes/Fases/Testemunhas `<table>` -> `Table` primitives; Testemunhas Avatar; `ProcessoDocumentoRow`/`formatDocumentoSize`/`formatDocumentoDate` removed

## Decisions Made
- Dropped the "Processo" and "Cliente" columns from `documentos-columns.tsx` (both left at Claude's discretion by 105-UI-SPEC.md/105-PATTERNS.md) — Processo is redundant since the tab is already scoped to one `processoId`, and Cliente would always render "—" since this tab's upload flow only ever sets `processo_id`.
- Removed `ProcessoDocumentoRow` plus its two now-orphaned helpers (`formatDocumentoSize`, `formatDocumentoDate`) rather than leaving unused dead code, after confirming via grep they had no other callers in the file.
- Left Decisões and Factos tabs (also raw `<table>` in the same file) untouched — genuinely out of scope: neither `105-CONTEXT.md` nor `105-UI-SPEC.md`/`105-PATTERNS.md` name them in the table-primitive migration scope, unlike Partes/Fases/Testemunhas which are explicitly named throughout all three docs.
- Split the combined edit into two atomic, independently-buildable commits matching the plan's task boundaries by temporarily reverting Task 2's edits (Avatar/Table imports, `deriveInitials`, the 3 table swaps), verifying `pnpm build` green on the Task-1-only intermediate state, committing, then reapplying Task 2's edits and committing separately.

## Deviations from Plan

### Plan-accuracy note (not a code deviation)

**1. Task 2's own literal automated verify gate (`grep -c "<table" ... | grep -qx 0`) is inaccurate for this file**
- **Found during:** Task 2 verification
- **Issue:** The plan's automated gate requires zero `<table` occurrences file-wide. After migrating exactly the 3 tables named in scope (Partes, Fases, Testemunhas), 2 raw `<table>` elements remain — the Decisões and Factos tabs, which are genuinely out of scope per `105-CONTEXT.md`'s locked decision ("Abas Partes e Fases... migrar" — only Partes/Fases named for table migration; Testemunhas' scope comes from the plan's own must_haves/PATTERNS.md, tied to the Avatar requirement) and `105-UI-SPEC.md`'s Component Inventory (CLP-02 expanded table lists only Partes/Fases/Documentos, not Decisões/Factos).
- **Resolution:** Verified manually that all 3 in-scope tabs (Partes/Fases/Testemunhas) contain zero raw table markup and render exclusively through `Table` primitives (confirmed via direct read of each tab's JSX), and that the 2 remaining `<table>` occurrences are specifically Decisões (line ~1884) and Factos (line ~2037), not a missed in-scope tab. `pnpm build` (the plan's own stronger gate) passes clean. This mirrors the exact false-negative pattern already documented in 105-02-SUMMARY.md for two of that plan's own automated gates.
- **Files affected:** none (verification-method finding only, same class of issue as 105-02's precedent)

---

**Total deviations:** 0 code auto-fixes; 1 plan/verification-accuracy note recorded for the record (no scope creep — the 2 out-of-scope tables were correctly left untouched per all 3 governing docs).
**Impact on plan:** None on delivered code.

## Issues Encountered
None beyond the plan-accuracy note above.

## Known Stubs

None — no new stubs, placeholders, or hardcoded empty values introduced. The dropped "Cliente" column in `documentos-columns.tsx` is a deliberate design decision (documented above), not a stub: the column was never wired to render meaningful data in this tab's context (documents here only ever carry `processo_id`), so omitting it entirely is more honest than shipping a column that would always show "—".

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `processos/[id]/page.tsx`'s inner tab content is now fully migrated for this plan's scope (Partes/Fases/Testemunhas Table primitives, Documentos DataTable, Testemunhas Avatar) — CLP-02 (expanded) and the Testemunhas half of CLP-04 are complete.
- Decisões and Factos tabs remain on raw `<table>` markup — not a blocker for 105-06 (final closing/verification plan), but noted here as an intentionally out-of-scope pre-existing pattern should a future phase revisit table consistency across all of the Ficha de Processo's tabs.
- `pnpm build` green (24 routes); `pnpm lint` reports only the 2 pre-existing, out-of-scope findings already recorded in `deferred-items.md` from 105-02.

---
*Phase: 105-m-dulos-clientes-processos-combinados*
*Completed: 2026-07-16*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx`
- FOUND: `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-03-SUMMARY.md`
- FOUND commit: `53213b7` (Task 1)
- FOUND commit: `78a47f0` (Task 2)
