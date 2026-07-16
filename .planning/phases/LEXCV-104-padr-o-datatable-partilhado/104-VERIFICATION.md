---
phase: 104-padr-o-datatable-partilhado
verified: 2026-07-16T18:00:00Z
status: passed
score: 8/8 must-haves verified
overrides_applied: 0
---

# Phase 104: Padrão DataTable Partilhado Verification Report

**Phase Goal:** Existe um único padrão DataTable reutilizável, construído sobre o `Table` já reconciliado, adotado pelas 5 listas que precisam dele sem duplicar os filtros já servidos pelo backend via TanStack Query.
**Verified:** 2026-07-16T18:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `@tanstack/react-table` is added as a dependency and a single shared DataTable pattern (`data-table.tsx` + `data-table-column-header.tsx` + `data-table-pagination.tsx` + `data-table-view-options.tsx`) is built once over the existing `Table` primitive | ✓ VERIFIED | `web/package.json:15` has `"@tanstack/react-table": "^8.21.3"`. All 4 files exist under `web/src/components/shared/data-table/`, read in full — `data-table.tsx` configures `useReactTable` with exactly `getCoreRowModel`/`getSortedRowModel`/`getPaginationRowModel` and renders exclusively through `Table`/`TableHeader`/`TableBody`/`TableRow`/`TableHead`/`TableCell` (no raw `<table>`). |
| 2 | The shared DataTable never duplicates server-side filtering (no `getFilteredRowModel` anywhere) | ✓ VERIFIED | `grep -rn getFilteredRowModel web/src` → zero matches (independently re-run, not just trusting SUMMARY). |
| 3 | Clientes desktop list uses `<DataTable>` with sortable headers, functional pagination, column-visibility toggle, and preserved RBAC delete guard | ✓ VERIFIED | `clientes/page.tsx:489` renders `<DataTable columns={clienteColumns} data={clientes.data} .../>`; `clientes/columns.tsx` read in full — `ClienteAcoesCell` preserves `useDeleteCliente` + `window.confirm("Remover este cliente?")` gated by `canEditClientes`; Tipo/ativo cells use closed-set `Badge` variants (blue/purple/gray/green). |
| 4 | Processos desktop list uses `<DataTable>` with sortable headers (incl. verbatim estado/risco Badge mapping) and cross-hook `clienteNomeById` resolution | ✓ VERIFIED | `processos/page.tsx:314` renders `<DataTable columns={processoColumns} data={processos.data} .../>`; `processos/columns.tsx` read in full — `estadoVariant` mapping (ATIVO→green, SUSPENSO→amber, TRIAGEM→purple, CONCLUIDO/ENCERRADO→gray) and `prazosRiscoToVariant/Label` sub-badge ported verbatim. Documented deviation (no `hidden md:block` split ever existed on this screen) verified accurate — `grep -c "md:hidden\|hidden md:block"` = 0, and the unconditional `Table` wrapper's own `overflow-auto` (confirmed in `ui/table.tsx:7`) preserves narrow-viewport behavior — not a regression. |
| 5 | Pareceres desktop list uses `<DataTable>`; Financeiro desktop list is migrated off its raw hand-written `<table>` onto `<DataTable>` with a real Badge replacing `statusBadgeClass` | ✓ VERIFIED | `pareceres/page.tsx:422` and `financeiro/page.tsx:303` both render `<DataTable>`. `financeiro/columns.tsx` Estado cell uses `Badge variant={status==="Pago"?"green":status==="Parcialmente Pago"?"blue":"amber"}` — matches the mobile-card-derived mapping. `grep -rn statusBadgeClass web/src/app/(dashboard)/financeiro` → only a doc-comment reference in `columns.tsx:18` describing the historical replacement; no live `statusBadgeClass` object/usage remains. No raw `<table`/`<thead` in either file. |
| 6 | Documentos desktop list is migrated off its raw `<table>` onto `<DataTable>`, with an exhaustive Confidencialidade Badge mapping and preserved `canEditDocumentos`/`window.confirm` delete guard | ✓ VERIFIED | `documentos/page.tsx:142` renders `<DataTable columns={tableColumns} data={list.data} .../>`; `documentos/columns.tsx` read in full — `confidencialidadeVariant()` switch handles all 4 enum values (PUBLICO→gray, INTERNO→blue, CONFIDENCIAL→amber, RESTRITO→red) with a safe default; `DocumentoAcoesCell` preserves `canEditDocumentos` gate + `window.confirm("Apagar este documento?")`. No raw `<table`/`<thead`. |
| 7 | `/notificacoes` uses the official shadcn `Pagination` primitive (no numbered links) instead of its hand-rolled pager, over the unchanged server-pagination contract | ✓ VERIFIED | `notificacoes/page.tsx` imports `Pagination`/`PaginationContent`/`PaginationItem`/`PaginationPrevious`/`PaginationNext` from `@/components/ui/pagination`; zero `PaginationLink` occurrences; copy "Página {n} de {total}" / "Anterior" / "Seguinte" preserved; disabled-at-bounds via `aria-disabled`+`pointer-events-none`; `useNotificacoes({page, size: 20})` contract unchanged (still present, unedited). `pagination.tsx`'s `text` prop confirmed to be a real, pre-existing prop of the CLI-generated component (default "Previous"/"Next"), not a fabrication. |
| 8 | No row-selection/Checkbox/bulk-action UI exists anywhere in the shared pattern; a single holistic `pnpm build` passes with zero errors after all 5 screens are migrated | ✓ VERIFIED | `grep -rniE 'from "@/components/ui/checkbox"|<Checkbox'` across `shared/data-table/` and all 5 `columns.tsx` → zero matches (the `DropdownMenuCheckboxItem` substring-only "hits" documented in 104-02/104-06 SUMMARYs confirmed benign by direct reading — it's the column-visibility toggle, not row-selection). Independently re-ran `pnpm --dir web build` (not the SUMMARY's claim) — completed with 0 errors, all 24 routes compiled. |

**Score:** 8/8 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/components/shared/data-table/data-table.tsx` | Generic `<DataTable columns data />`, core+sorted+pagination row models only | ✓ VERIFIED | Read in full; matches contract exactly, zero `getFilteredRowModel`. |
| `web/src/components/shared/data-table/data-table-column-header.tsx` | Sortable header, 12px/600 uppercase muted typography | ✓ VERIFIED | `text-xs font-semibold uppercase tracking-wider text-muted-foreground` present verbatim; 3-state sort icon cycle (`ChevronsUpDown`/`ArrowUp`/`ArrowDown`) in `text-muted-foreground`, never accent. |
| `web/src/components/shared/data-table/data-table-pagination.tsx` | Client-side pager: rows-per-page Select + "Página n de total" + Anterior/Seguinte | ✓ VERIFIED | Exact copy strings present; `px-6 py-4 border-t` shell; disabled via `getCanPreviousPage`/`getCanNextPage`. |
| `web/src/components/shared/data-table/data-table-view-options.tsx` | Column-visibility DropdownMenu behind icon Button + Tooltip | ✓ VERIFIED | `SlidersHorizontal` trigger, `aria-label="Colunas visíveis"`, wrapped in `Tooltip`; lists `column.getCanHide()` columns only. |
| `web/src/components/ui/pagination.tsx` | Official shadcn Pagination primitive | ✓ VERIFIED | Present, exports `Pagination`/`PaginationContent`/`PaginationItem`/`PaginationLink`/`PaginationPrevious`/`PaginationNext`/`PaginationEllipsis`; confirmed untouched-after-add (button.tsx overwrite correctly declined per 104-02-SUMMARY, independently spot-checked — `button.tsx` shows no Pagination-registry drift). |
| `web/src/app/(dashboard)/clientes/columns.tsx`, `processos/columns.tsx`, `pareceres/columns.tsx`, `financeiro/columns.tsx`, `documentos/columns.tsx` | Per-screen `ColumnDef` factories | ✓ VERIFIED | All 5 exist, all read (4 in full detail, 1 — pareceres — read in full), all wired into their respective `page.tsx` via `<DataTable columns={...} data={...} />`. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `data-table.tsx` | `@tanstack/react-table` `useReactTable` | `getCoreRowModel + getSortedRowModel + getPaginationRowModel` only | ✓ WIRED | Confirmed in source; zero `getFilteredRowModel`. |
| `data-table.tsx` | `web/src/components/ui/table.tsx` | Renders `Table`/`TableHeader`/`TableBody`/`TableRow`/`TableHead`/`TableCell` | ✓ WIRED | Confirmed; no raw `<table>` tag. |
| 5× `page.tsx` | `DataTable` + `columns(...)` | Desktop branch renders `<DataTable columns={...} data={...} />` | ✓ WIRED | Verified via grep across all 5 `page.tsx` files — each imports `DataTable` from the shared path and calls its own `columns(...)` factory. |
| `notificacoes/page.tsx` | `web/src/components/ui/pagination.tsx` | `import ... from "@/components/ui/pagination"` | ✓ WIRED | Confirmed import + render + preserved `useNotificacoes` contract. |
| Phase completion | Human visual sign-off | Blocking checkpoint (104-06 Task 2) | ✓ WIRED | Recorded "Approved" verdict in 104-06-SUMMARY.md with concrete per-screen, per-theme, RBAC, and mobile evidence (see Human Verification note below for one caveat). |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|--------------|--------|----------|
| DTB-01 | 104-01, 104-02 | `@tanstack/react-table` dependency + shared pattern built once | ✓ SATISFIED | Package present, all 4 shared files exist and compile. REQUIREMENTS.md checkbox correctly marked `[x]`/"Complete" (commit `d005d15`). |
| DTB-02 | 104-03, 104-04, 104-05 | Clientes/Processos/Pareceres/Financeiro/Documentos migrated to shared DataTable, no client-side re-filtering | ✓ SATISFIED (code-verified) | All 5 screens independently confirmed wired to `<DataTable>`; zero `getFilteredRowModel` repo-wide. **Note:** REQUIREMENTS.md's traceability table still shows DTB-02 as `[ ]`/"Pending" — this is a stale-documentation gap (never updated after 104-03/04/05 landed), not a functional gap. See Anti-Patterns/Info section. |
| DTB-03 | 104-02, 104-05 | Official `Pagination` applied to `/notificacoes` (its full and only scope, per 104-UI-SPEC.md's verified repo-wide search) | ✓ SATISFIED (code-verified) | `notificacoes/page.tsx` confirmed on official `Pagination`. Same REQUIREMENTS.md staleness noted above (`[ ]`/"Pending"). |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `.planning/REQUIREMENTS.md` | traceability table | DTB-02/DTB-03 rows still show `[ ]`/"Pending" despite being functionally complete and despite ROADMAP.md's Progress table showing "104. Padrão DataTable Partilhado ... 6/6 ... Complete" | ℹ️ Info | Documentation drift only — no code/functional gap. Verified independently that DTB-02/DTB-03 are actually satisfied in the codebase (see Requirements Coverage). Recommend a follow-up commit updating REQUIREMENTS.md checkboxes/traceability table to keep the planning docs internally consistent before the milestone audit. |
| (repo-wide, pre-existing) | `use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts` | `Cannot find module 'vitest'` — 3 pre-existing `tsc --noEmit` failures | ℹ️ Info | Confirmed pre-existing (committed in Phase 97-02, `git log` verified), unrelated to and untouched by Phase 104, already logged in `deferred-items.md`. Independently reran `tsc --noEmit` — same 3 errors, nothing new. Does not affect `pnpm build` (Next does not typecheck standalone test files outside the app tree — confirmed, build passed clean). |

No blocker-level anti-patterns (TBD/FIXME/XXX, placeholder/stub returns, empty handlers) found in any of the 19 files this phase created or modified — independently grepped, not just SUMMARY-trusted.

### Code Review + Fix Loop (independently confirmed applied, not just claimed)

The phase's 3-iteration review/fix loop (104-REVIEW.md iter1-3, 104-REVIEW-FIX.md iter1-3) surfaced and closed a genuine CSV/formula-injection vulnerability (CWE-1236) in Clientes' export/import, discovered progressively across iterations:
- CR-01 (final form): `telefone`/`email` fields were unguarded against formula-injection despite not being format-locked. **Independently verified fixed**: `clientes/page.tsx:115-116` now calls `guardCsvFormula(c.telefone ?? "")` / `guardCsvFormula(c.email ?? "")`; commit `cf06297` confirmed present in `git log`.
- WR-01 (round-trip): guard prefix was never stripped on re-import. **Independently verified fixed**: `lib/csv.ts` now exports `stripCsvFormulaGuard()`, applied in `onImportFile` to `nome`/`telefone`/`email` (`clientes/page.tsx:163,182-183`); commit `3412f6d` confirmed present in `git log`.
- Financeiro's export was re-checked in the same pass and confirmed already sound (numeric/closed-enum fields only) — independently re-read `financeiro/page.tsx`'s CSV builder and confirmed `guardFormula()` is applied to `processoLabel`/`clienteLabel`, matching the review's conclusion.
- 3 Info-level findings (duplicated `FORMULA_TRIGGER_CHARS`, dead fallback branch in view-options, missing `encodeURIComponent` on one mobile-card link) were explicitly and correctly left open per `fix_scope: critical_warning` — non-blocking, documented, not silently dropped.

### Human Verification Required

None outstanding. Per the task's explicit instruction, the live human visual checkpoint recorded in 104-06-SUMMARY.md (performed 2026-07-16, verdict **Approved**) is treated as satisfying the human-verification need for this phase's UI behaviors (sorting, pagination, column-visibility, Badge migrations, mobile dual-view, RBAC), since this project has no automated visual/E2E test suite.

**One caveat worth surfacing (not re-flagged as a blocking gap, but noted for transparency):** the checkpoint's dev tenant had too little data (max 4 rows per screen) to exercise real multi-page pagination (`Página n of 2+`, an enabled "Seguinte" transitioning to a new page) live on any of the 5 DataTable screens or on `/notificacoes` (which had only 1 page of 2 items). The checkpoint substituted direct source-code reads of the pagination logic instead, consistent with the same project's Phase 103 HUMAN-UAT.md precedent for hard-to-reproduce states. Assessed as low residual risk: `DataTablePagination`/`Pagination` both call directly into `@tanstack/react-table`'s and shadcn's own built-in pagination state machine (`table.nextPage()`/`getCanNextPage()` and standard bounds-checked `onClick`/`aria-disabled`) with no custom re-implementation of the paging math — the kind of library-delegated logic least likely to hide a live-only bug. Not escalated as a gap; documented here per the adversarial-verification mandate to surface what the checkpoint didn't literally cover.

### Gaps Summary

None. All 8 derived observable truths verified directly against the codebase (not SUMMARY claims): the shared DataTable pattern exists, is substantive, is wired into all 5 target screens plus `/notificacoes`, contains none of the phase's forbidden constructs (client-side re-filtering, row-selection, hand-rolled status spans, raw `<table>` remnants), and the whole app builds clean under an independently-executed `pnpm --dir web build`. The 3-iteration code-review/fix loop's CSV-injection findings were independently confirmed fixed in the current codebase (not just claimed in the review-fix report). The only findings are two Info-level items: (1) stale REQUIREMENTS.md checkboxes for DTB-02/DTB-03 (documentation-only, functionally satisfied), and (2) a pre-existing, out-of-phase-scope `vitest`-import typecheck failure already logged in `deferred-items.md`. Neither blocks the phase goal.

---

*Verified: 2026-07-16T18:00:00Z*
*Verifier: Claude (gsd-verifier)*
