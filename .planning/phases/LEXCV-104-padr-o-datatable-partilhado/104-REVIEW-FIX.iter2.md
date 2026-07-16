---
phase: LEXCV-104-padr-o-datatable-partilhado
fixed_at: 2026-07-16T12:45:00Z
review_path: .planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 7
skipped: 0
status: all_fixed
---

# Phase LEXCV-104: Code Review Fix Report

**Fixed at:** 2026-07-16T12:45:00Z
**Source review:** .planning/phases/LEXCV-104-padr-o-datatable-partilhado/104-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (1 critical, 6 warnings — Info findings excluded per `fix_scope: critical_warning`)
- Fixed: 7
- Skipped: 0

## Fixed Issues

### CR-01: CSV/Formula-injection in Financeiro export (and shared CSV helper)

**Files modified:** `web/src/app/(dashboard)/financeiro/page.tsx`, `web/src/lib/csv.ts`
**Commit:** `d25949d`
**Applied fix:** Added a `FORMULA_TRIGGER_CHARS` guard (`=`, `+`, `-`, `@`, tab, CR) to both `escapeField()` (Financeiro export) and `escapeCsvValue()` (shared CSV helper used by Clientes export). Any field value starting with one of these characters is now prefixed with a single quote before the existing quote/comma/newline escaping runs, neutralizing formula execution when the CSV is opened in Excel/LibreOffice/Google Sheets, per OWASP CSV-injection guidance.

### WR-01: Documentos desktop Ações column has no Download action

**Files modified:** `web/src/app/(dashboard)/documentos/columns.tsx`
**Commit:** `220a270`
**Applied fix:** Added an icon-only Download link (`/api/v1/documentos/{id}/download`, `encodeURIComponent`-safe, opened in a new tab) to `DocumentoAcoesCell`, ungated by `canEditDocumentos` — matching the mobile card's availability to all viewers. The "Apagar" button remains gated behind `canEditDocumentos`; previously the whole cell (including any download capability) returned `null` for non-editors.

### WR-02: Column-visibility dropdown always shows raw column ids

**Files modified:** `web/src/components/shared/data-table/data-table-view-options.tsx`, `web/src/app/(dashboard)/clientes/columns.tsx`, `web/src/app/(dashboard)/documentos/columns.tsx`, `web/src/app/(dashboard)/financeiro/columns.tsx`, `web/src/app/(dashboard)/pareceres/columns.tsx`, `web/src/app/(dashboard)/processos/columns.tsx`
**Commit:** `b7a5e20`
**Applied fix:** Added `meta: { label: "..." }` to every column definition across all 5 migrated tables (matching each column's `DataTableColumnHeader` title), and updated `DataTableViewOptions` to read `column.columnDef.meta?.label` first, falling back to the string-header check and then `column.id`. The view-options dropdown now shows friendly Portuguese labels ("Processo", "Cliente", "Confidencialidade", etc.) instead of raw ids.

### WR-03: Clientes "NIF" column sorts by a different value than it displays

**Files modified:** `web/src/app/(dashboard)/clientes/columns.tsx`
**Commit:** `ba409f6`
**Applied fix:** Rewrote the `nif` column's `accessorFn` to mirror the cell's own display logic exactly: when `documento_tipo`/`documentoTipo` is set, sort by `documento_numero`/`documentoNumero` (what's actually rendered); otherwise sort by `nif`. Previously the `??` chain always resolved to `nif` (a required, non-optional field) regardless of what the cell displayed.

### WR-04: Inconsistent memoization of column defs across the 5 migrated pages

**Files modified:** `web/src/app/(dashboard)/processos/page.tsx`, `web/src/app/(dashboard)/pareceres/page.tsx`, `web/src/app/(dashboard)/financeiro/page.tsx`
**Commit:** `2504293`
**Applied fix:** Wrapped each page's `clienteNomeById`/`processoById` lookup `Map` construction and its `columns(...)` factory call in `React.useMemo`, keyed on the relevant `.data` (and derived-map) dependencies — matching the pattern already used by Clientes and Documentos. `useReactTable` now receives a stable `columns` array identity across renders instead of rebuilding on every keystroke in sibling filter fields.

### WR-05: Duplicated formatting/status logic between `columns.tsx` and `page.tsx`

**Files modified:** `web/src/lib/financeiro.ts` (new), `web/src/lib/pareceres.ts` (new), `web/src/app/(dashboard)/financeiro/page.tsx`, `web/src/app/(dashboard)/financeiro/columns.tsx`, `web/src/app/(dashboard)/pareceres/page.tsx`, `web/src/app/(dashboard)/pareceres/columns.tsx`
**Commit:** `a627285`
**Applied fix:** Extracted `formatMoneyCVE`, `formatDate`, and `calcHonorarioStatus` (+ `HonorarioStatus` type) into new `web/src/lib/financeiro.ts`, and `formatDate` + `statusVariant` into new `web/src/lib/pareceres.ts`. Both `columns.tsx`/`page.tsx` pairs now import from the shared module instead of maintaining verbatim duplicate definitions, eliminating the drift risk called out in the review. New files were created because the review's fix explicitly required extracting to a shared module.

### WR-06: Shared `DataTable` relies on index-based row identity

**Files modified:** `web/src/components/shared/data-table/data-table.tsx`, `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/documentos/page.tsx`, `web/src/app/(dashboard)/financeiro/page.tsx`, `web/src/app/(dashboard)/pareceres/page.tsx`, `web/src/app/(dashboard)/processos/page.tsx`
**Commit:** `81ecd68`
**Applied fix:** Added an optional `getRowId?: (row: TData) => string` prop to the shared `DataTable` and threaded it into `useReactTable`. Each of the 5 caller pages now passes a stable entity-id-based `getRowId` (e.g. `(c) => c.id`, `(h) => String(h.id)` for Financeiro's numeric `Honorario.id`), so TanStack Table keys rows by their actual identity instead of array index, preventing per-row local state (delete-error messages, pending spinners) from being misattributed after a reorder-preserving-length refetch.

## Skipped Issues

None — all 7 in-scope findings were fixed.

---

_Fixed: 2026-07-16T12:45:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
