# Phase 104: Padrão DataTable Partilhado - Pattern Map

**Mapped:** 2026-07-16
**Files analyzed:** 17 (4 shared `data-table/` files, 5 per-screen `columns.tsx`, 5 consumer `page.tsx` modifications, 1 `notificacoes/page.tsx` modification, `pagination.tsx` primitive add, `package.json` dependency add)
**Analogs found:** 12 / 17 (the 4 core `data-table/*` files + 5 `columns.tsx` files have no direct in-repo analog — this is a genuinely new pattern per `ARCHITECTURE.md` Pattern 5 — but each has a strong *compositional* analog documented below; the 5 `page.tsx` files and `pagination.tsx`/`package.json` all have exact/role-match analogs)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `web/package.json` | config | n/a (dependency manifest) | `web/package.json` itself (Phase 101's `@radix-ui/*`/`sonner` additions, same file, prior commits) | exact (same gate/procedure, new entry) |
| `web/src/components/ui/pagination.tsx` | component | event-driven (prev/next click → state) | `web/src/components/ui/tooltip.tsx` / `select.tsx` (shadcn official, CLI-added, Radix-based, untouched-after-add convention) | role-match (official CLI add, no local customization expected) |
| `web/src/components/shared/data-table/data-table.tsx` | component | transform (client-side sort+paginate over an already-fetched, already-server-filtered array) | `web/src/components/ui/table.tsx` (the primitive it wraps) — no existing `useReactTable` composition exists in-repo | no analog (new pattern) — compose from `table.tsx` + official recipe |
| `web/src/components/shared/data-table/data-table-column-header.tsx` | component | event-driven (click → `column.toggleSorting()`) | Row action cell pattern: `Tooltip`+`Button variant="ghost" size="sm"` icon composition, e.g. `web/src/app/(dashboard)/clientes/page.tsx:603-612` | role-match (button+icon composition idiom, not sort-specific) |
| `web/src/components/shared/data-table/data-table-pagination.tsx` | component | transform (client-side page slicing) | Functional behavior: `web/src/app/(dashboard)/notificacoes/page.tsx:228-250` (Anterior/Seguinte + "Página X de Y", the only real working pager in the app). Visual shell: `web/src/app/(dashboard)/clientes/page.tsx:505-517` (decorative `px-6 py-4` footer) | role-match (behavior from notificações, shell spacing from clientes/processos) |
| `web/src/components/shared/data-table/data-table-view-options.tsx` | component | event-driven (checkbox toggle → `column.toggleVisibility()`) | `web/src/components/ui/dropdown-menu.tsx` (`DropdownMenuCheckboxItem` primitive exists, installed, but zero call sites anywhere in `web/src/app` today — confirmed via repo-wide search) | no analog (new pattern) — primitive exists, composition doesn't |
| `web/src/app/(dashboard)/clientes/columns.tsx` | config (column defs) | transform | `ClienteRow` component body, `web/src/app/(dashboard)/clientes/page.tsx:528-661` | exact (cell-by-cell source of truth for the new column defs) |
| `web/src/app/(dashboard)/clientes/page.tsx` | route | CRUD (list + inline delete) | itself, pre-migration (desktop branch `Table` usage, `page.tsx:486-519`) | exact (already on `Table`, smallest migration of the 5) |
| `web/src/app/(dashboard)/processos/columns.tsx` | config (column defs) | transform | Row-rendering block, `web/src/app/(dashboard)/processos/page.tsx:338-394` | exact |
| `web/src/app/(dashboard)/processos/page.tsx` | route | CRUD (list, read-only rows) | itself, pre-migration (desktop `Table`, `page.tsx:311-399`) | exact |
| `web/src/app/(dashboard)/pareceres/columns.tsx` | config (column defs) | transform | Row-rendering block, `web/src/app/(dashboard)/pareceres/page.tsx:450-483` | exact |
| `web/src/app/(dashboard)/pareceres/page.tsx` | route | CRUD (list, dual data source: `usePareceres`/`usePesquisarPareceres`) | itself, pre-migration (desktop `Table`, `page.tsx:438-486`) | exact |
| `web/src/app/(dashboard)/financeiro/columns.tsx` | config (column defs) | transform | Raw `<table>` row component + status logic, `web/src/app/(dashboard)/financeiro/page.tsx:317-367` and `:20-27`/`:92-98` | role-match (source logic exact; base markup is raw `<table>`, not `Table`, so this is a bigger lift — see Scope note #1 in UI-SPEC) |
| `web/src/app/(dashboard)/financeiro/page.tsx` | route | CRUD (list + CSV export) | itself, pre-migration (`page.tsx:300-369`, raw `<table>`) | role-match (first-ever `Table` adoption here, not just DataTable) |
| `web/src/app/(dashboard)/documentos/columns.tsx` | config (column defs) | transform | `DocumentoRow` component, `web/src/app/(dashboard)/documentos/page.tsx:277-344` | role-match (source logic exact; base markup is raw `<table>`) |
| `web/src/app/(dashboard)/documentos/page.tsx` | route | CRUD (list + delete + download) | itself, pre-migration (`page.tsx:138-193`, raw `<table>`) | role-match (first-ever `Table` adoption here, not just DataTable) |
| `web/src/app/(dashboard)/notificacoes/page.tsx` | route | CRUD (server-paginated read + mark-as-read mutations) | itself, pre-migration pager (`page.tsx:228-250`, hand-rolled `Button` prev/next) | exact (component swap only — data contract via `useNotificacoes({page, size})` unchanged) |

## Pattern Assignments

### `web/src/components/shared/data-table/data-table.tsx` (component, transform)

**Analog:** `web/src/components/ui/table.tsx` (primitive being wrapped) — no existing TanStack Table composition in this repo (`ARCHITECTURE.md` Pattern 5 explicitly flags `table.tsx` ≠ `DataTable`).

**Table primitive to render through** (`web/src/components/ui/table.tsx:1-85`, full file, already read in full — do not re-read):
```typescript
import * as React from "react";
import { cn } from "@/lib/utils";

export function Table({ className, ...props }: React.ComponentProps<"table">) {
  return (
    <div className="w-full overflow-auto">
      <table data-slot="table" className={cn("w-full caption-bottom text-sm", className)} {...props} />
    </div>
  );
}
export function TableHeader({ className, ...props }: React.ComponentProps<"thead">) {
  return <thead data-slot="table-header" className={cn("[&_tr]:border-b", className)} {...props} />;
}
export function TableBody(...) { /* [&_tr:last-child]:border-0 */ }
export function TableRow(...) { /* border-b transition-colors hover:bg-muted/50 data-[state=selected]:bg-muted */ }
export function TableHead(...) { /* h-12 px-4 text-left align-middle font-semibold ... */ }
export function TableCell(...) { /* p-4 align-middle ... */ }
```

**Mandatory constraint (locked in `104-CONTEXT.md`):** `useReactTable({ data, columns, getCoreRowModel: getCoreRowModel(), getSortedRowModel: getSortedRowModel(), getPaginationRowModel: getPaginationRowModel(), onSortingChange, state: { sorting } })` — **never** `getFilteredRowModel()`. Filtering stays 100% server-side via each screen's existing `use-*` hook + `useState` filters (see e.g. `clientes/page.tsx:62-106`'s `filters`/`onSubmit`/`onClear` triplet, unchanged by this phase).

**Empty-fallback copy** (per `104-UI-SPEC.md` Copywriting Contract, rarely reached since every screen's outer guard already intercepts true-empty before mounting `DataTable`):
```typescript
"Sem resultados para os filtros aplicados."
```

**Render through existing primitives, never raw `<table>`** — this is the explicit corrective for Financeiro/Documentos (see their entries below), which currently do exactly what this component must not do.

---

### `web/src/components/shared/data-table/data-table-column-header.tsx` (component, event-driven)

**Analog:** icon-button-with-tooltip composition already established for row actions, e.g. `web/src/app/(dashboard)/clientes/page.tsx:603-612`:
```typescript
<Tooltip>
  <TooltipTrigger asChild>
    <Button asChild size="sm" variant="ghost" aria-label="Ver detalhes" className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
      <Link href={`/clientes/${encodeURIComponent(cliente.id)}`}>
        <Eye className="h-4 w-4" />
      </Link>
    </Button>
  </TooltipTrigger>
  <TooltipContent>Ver detalhes</TooltipContent>
</Tooltip>
```
Reuse the `Button variant="ghost" size="sm"` + trailing `lucide-react` icon idiom for the sortable header trigger (`ChevronsUpDown`/`ArrowUp`/`ArrowDown`), calling `column.toggleSorting(column.getIsSorted() === "asc")` on click — simple 3-state cycle, no dropdown (per UI-SPEC Component Inventory).

**Mandatory typography fix (`104-UI-SPEC.md` Typography section, load-bearing — closes a real 3-way inconsistency):**
```tsx
<span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
  {label}
</span>
```
This replaces **both** off-scale patterns found in the current codebase:
- Clientes/Processos/Pareceres `TableHead` override: `text-[10px] font-bold uppercase tracking-wider text-slate-500` (`clientes/page.tsx:491-495`, `processos/page.tsx:314-319`, `pareceres/page.tsx:442-447`) — 10px is below the 12px Label floor.
- Financeiro/Documentos raw `<th className="py-2 pr-4 font-medium">` (`financeiro/page.tsx:306-312`, `documentos/page.tsx:143-152`) — inherits 14px/500-weight from the parent `<table className="text-sm">`, a 3rd weight value.

Non-sortable columns (Ações on all 5 screens; Contacto on Clientes; Ver. on Documentos) render as a plain `TableHead` with the same typography span, no button/icon — do not wrap in the sort-toggle button.

---

### `web/src/components/shared/data-table/data-table-pagination.tsx` (component, transform)

**Behavioral analog (the only real functioning pager in the app today):** `web/src/app/(dashboard)/notificacoes/page.tsx:228-250`:
```typescript
{list.data.totalPages > 1 ? (
  <div className="flex items-center justify-between gap-2 pt-4">
    <Button type="button" variant="outline" disabled={page === 0} onClick={() => setPage((p) => Math.max(0, p - 1))}>
      Anterior
    </Button>
    <span className="text-sm text-slate-500 dark:text-slate-400">
      Página {page + 1} de {list.data.totalPages}
    </span>
    <Button type="button" variant="outline" disabled={page + 1 >= list.data.totalPages} onClick={() => setPage((p) => p + 1)}>
      Seguinte
    </Button>
  </div>
) : null}
```
Copy this exact `Button variant="outline"` + disabled-at-bounds + "Página {n} de {total}" shape for `table.previousPage()`/`table.nextPage()`/`table.getPageCount()`, per the Pagination Contract table in `104-UI-SPEC.md` — copy strings: **"Linhas por página"** (rows-per-page label), **"Página {n} de {total}"**, **"Anterior"** / **"Seguinte"**. No numbered page links (deliberate, see Color section of UI-SPEC).

**Visual shell/spacing analog (decorative but structurally correct footer, reuse the padding, discard the fake buttons):** `web/src/app/(dashboard)/clientes/page.tsx:505-517`:
```typescript
<div className="flex items-center justify-between px-6 py-4 border-t border-slate-200 dark:border-slate-800 bg-slate-50/30 dark:bg-slate-900/20">
  <div className="text-sm text-slate-500 dark:text-slate-400 font-medium">
    A mostrar 1-{clientes.data.length} de {totalClientes.toLocaleString("pt-CV")} clientes
  </div>
  <div className="flex items-center gap-2">
    {/* fake ‹ 1 2 3 … › buttons — DISCARD, no onClick handlers, no real page param anywhere */}
  </div>
</div>
```
Reuse `px-6 py-4` + `border-t` shell (this is the on-scale `lg`/`md` spacing pair per `104-UI-SPEC.md` Spacing Scale); discard the non-functional numbered buttons entirely — Processos has the identical dead pattern at `processos/page.tsx:400-413`.

Add a rows-per-page `Select` (`10`/`20`/`50`, default `10`) using the already-installed `Select`/`SelectTrigger`/`SelectContent`/`SelectItem` from `web/src/components/ui/select.tsx` (full file read, lines 1-193) — no existing in-app call site of `Select` to copy from (it's CLI-added but not yet consumed anywhere in `web/src/app`), so compose directly from the primitive's own exported API (`Select`, `SelectTrigger`, `SelectContent`, `SelectItem`, `SelectValue`).

---

### `web/src/components/shared/data-table/data-table-view-options.tsx` (component, event-driven)

**No analog** — confirmed via repo-wide search: `DropdownMenuCheckboxItem` (and `DropdownMenuTrigger`/`DropdownMenuContent`) have zero call sites anywhere in `web/src/app` today, even though the primitive itself is installed (`web/src/components/ui/dropdown-menu.tsx`, full file read, lines 1-269).

**Primitive API to compose from** (`dropdown-menu.tsx:84-116`):
```typescript
function DropdownMenuCheckboxItem({ className, children, checked, inset, ...props }: ...) {
  return (
    <DropdownMenuPrimitive.CheckboxItem data-slot="dropdown-menu-checkbox-item" checked={checked} {...props}>
      <span className="pointer-events-none absolute right-2 flex items-center justify-center">
        <DropdownMenuPrimitive.ItemIndicator><CheckIcon /></DropdownMenuPrimitive.ItemIndicator>
      </span>
      {children}
    </DropdownMenuPrimitive.CheckboxItem>
  );
}
```
Trigger: icon-only `Button variant="outline" size="sm"` with `SlidersHorizontal` (lucide-react), `aria-label="Colunas visíveis"` + `Tooltip` wrapper — reuse the same `Tooltip`+`Button` composition documented above for `data-table-column-header.tsx`. List every `column.getCanHide()`-eligible column as a `DropdownMenuCheckboxItem` bound to `column.getIsVisible()`/`column.toggleVisibility()`. Set `enableHiding: false` in each screen's `columns.tsx` on the Ações column and the primary identity column (Nome/Número/Cliente/Honorário/Nome, per screen) so the table can never be hidden to zero useful columns (per UI-SPEC Component Inventory).

---

### `web/src/app/(dashboard)/clientes/columns.tsx` (config, transform)

**Analog:** `ClienteRow` (`web/src/app/(dashboard)/clientes/page.tsx:528-661`, full function already read).

**Column source-of-truth excerpts to port into `ColumnDef<Cliente>[]` cells:**
- Nome/Razão Social cell (avatar-initials + name + sub-badges), `page.tsx:558-583`.
- Tipo cell (`Badge` variant mapping `blue`/`purple`/`gray`), `page.tsx:545-547` + `585`:
```typescript
const tipo = (cliente.tipo ?? "").toUpperCase();
const badgeVariant = tipo === "PARTICULAR" ? "blue" : tipo === "EMPRESA" ? "purple" : "gray";
// cell: <Badge variant={badgeVariant} className="rounded-none font-bold tracking-wide">{tipo || "—"}</Badge>
```
- NIF/documento cell (conditional doc-type sub-label), `page.tsx:587-596`.
- Contacto cell (telefone + email stacked, **not sortable** — composite field), `page.tsx:597-600`.
- Ações cell (Ver detalhes / Imprimir / Editar / Eliminar, **not sortable**, `enableHiding: false`), `page.tsx:601-658` — reuse the `Tooltip`+`Button variant="ghost" size="sm"` idiom verbatim, including the `window.confirm()` delete guard (`page.tsx:549-554`) and `useDeleteCliente` hook call — this per-row hook call means `columns.tsx`'s Ações cell must remain a small inline component (mirroring today's `ClienteRow` being a component, not a plain render function), or the delete handler must be lifted into a `meta`/closure passed to `columns()` — confirm the exact wiring shape at implementation time since TanStack column defs are plain objects, not components, and cannot call hooks directly.

`Badge` variants already used and must be preserved exactly: `blue` (tipo=PARTICULAR), `purple` (tipo=EMPRESA), `gray` (tipo unset), `green`/`gray` (ativo), `blue` (numero_cliente), `green` (avençado).

---

### `web/src/app/(dashboard)/processos/columns.tsx` (config, transform)

**Analog:** row-rendering block, `web/src/app/(dashboard)/processos/page.tsx:338-394`.

**Estado badge variant mapping (source of truth, port verbatim), `page.tsx:324-336`:**
```typescript
const estado = (p.estado ?? "").toUpperCase();
const estadoVariant =
  estado === "ATIVO" ? "green"
  : estado === "SUSPENSO" ? "amber"
  : estado === "TRIAGEM" ? "purple"
  : estado === "CONCLUIDO" || estado === "ENCERRADO" ? "gray"
  : "secondary";
const estadoLabel = estado === "TRIAGEM" ? "EM TRIAGEM" : (p.estado ?? "—");
```
Plus the risco-de-prazo sub-badge inside the same Estado cell, using the shared helper `web/src/lib/prazos.ts` (full file read, 30 lines):
```typescript
export function prazosRiscoToVariant(risco: PrazoRisco): "green" | "amber" | "red" { /* ok→green, proximo→amber, vencido→red */ }
export function prazosRiscoToLabel(risco: PrazoRisco): string { /* ok→"PRAZO OK", proximo→"PRAZO PRÓXIMO", vencido→"PRAZO VENCIDO" */ }
```
Only rendered when `p.risco_mais_critico !== "ok"` (`page.tsx:367-379`) — port this conditional into the Estado column's cell renderer unchanged.

Área Jurídica cell: plain `Badge variant="blue"` (`page.tsx:360`). Número do Processo cell carries 2 sub-lines (Entrada date, Responsável) below the link (`page.tsx:341-353`) — keep both in the cell renderer. Cliente cell resolves via `clienteNomeById` Map built from `useClientes` (`page.tsx:56`) — this cross-hook lookup pattern must be passed into `columns(clienteNomeById)` as a factory argument, not hardcoded, since `columns.tsx` cannot call hooks itself. Ações cell: single `Tooltip`+`Button variant="ghost"` (`MoreVertical` icon) linking to detail page, no delete action here (`page.tsx:381-391`) — simpler than Clientes' Ações cell.

---

### `web/src/app/(dashboard)/pareceres/columns.tsx` (config, transform)

**Analog:** row-rendering block, `web/src/app/(dashboard)/pareceres/page.tsx:450-483`, plus the shared helpers already defined at module scope (`page.tsx:24-41`):
```typescript
function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}
function statusVariant(status: ParecerStatus) {
  return status === "PENDENTE" ? "gray"
    : status === "EM_ELABORACAO" ? "blue"
    : status === "EM_REVISAO" ? "amber"
    : status === "CONCLUIDO" ? "green"
    : "secondary";
}
```
Port both helpers unchanged (or import them into `columns.tsx` if kept module-level in `page.tsx`). Estado cell: `<Badge variant={statusVariant(s.status)} className="rounded-none font-bold tracking-wide">{s.status}</Badge>` (`page.tsx:454-456`). Cliente cell resolves via `clienteNomeById` Map (`page.tsx:84-87`), same cross-hook-lookup caveat as Processos — pass as a `columns(clienteNomeById)` factory argument. Prioridade/Prazo/Criado cells are plain formatted text (`page.tsx:466-468`). Ações cell: single `Tooltip`+`Button variant="ghost"` (`MoreVertical`), no delete (`page.tsx:469-480`).

**Note on data source duality:** this screen has two possible row sources (`usePareceres`/`usePesquisarPareceres`, toggled by `searchActive`, `page.tsx:89-95`) — `DataTable` receives whichever `rows` array is currently active; this dual-source logic is unchanged by the migration, it only affects what `data` prop `<DataTable>` receives, not the column defs.

---

### `web/src/app/(dashboard)/financeiro/columns.tsx` (config, transform)

**Analog:** raw `<table>` row body, `web/src/app/(dashboard)/financeiro/page.tsx:317-367`, plus module-scope helpers (`page.tsx:15-27`, `92-105`):
```typescript
function formatMoneyCVE(v: number | null | undefined) {
  if (v == null) return "A confirmar";
  return v.toLocaleString("pt-CV", { style: "currency", currency: "CVE" });
}
type HonorarioStatus = "Pendente" | "Parcialmente Pago" | "Pago";
function calcHonorarioStatus(totalPago: number, valorTotal: number | null): HonorarioStatus {
  if (valorTotal == null) return "Pendente";
  if (totalPago <= 0) return "Pendente";
  if (totalPago < valorTotal) return "Parcialmente Pago";
  return "Pago";
}
```

**Required Badge migration (UI-SPEC mandatory fix — do not invent new colors):** the desktop `<td>` today renders a hand-rolled span (`page.tsx:357-361`):
```typescript
// CURRENT (must be replaced):
const status = calcHonorarioStatus(h.totalPago, h.valorTotal);
return <span className={statusBadgeClass[status]}>{status}</span>;
```
The **actual existing semantic mapping already implied elsewhere in this same file** — the mobile card branch (`page.tsx:384-389`, already using real `Badge`) — is the mapping to carry into `columns.tsx`'s cell renderer verbatim:
```typescript
<Badge variant={status === "Pago" ? "green" : status === "Parcialmente Pago" ? "blue" : "amber"} className="rounded-none font-bold text-[10px] flex-shrink-0">
  {status}
</Badge>
```
(Note: this is `green`/`blue`/`amber`, not `green`/`amber`/`red` — reuse the mobile card's mapping, which is the actual existing precedent in this file, not the illustrative example in `104-UI-SPEC.md` line 71.)

Other cells: Honorário (`#{h.id}` link, `page.tsx:323-330`), Processo (link via `processoById` Map, `:331-341`), Cliente (link via `clienteNomeById` Map, `:343-353`), Total (`formatMoneyCVE`, `:355`), Data do Acordo (`formatDate`, `:356`) — all straightforward cell-renderer ports. `processoById`/`clienteNomeById` Maps must be passed into `columns(processoById, clienteNomeById)` as factory arguments (same cross-hook-lookup pattern as Processos/Pareceres).

**Scope note (per UI-SPEC #1):** this screen's desktop branch is currently a raw `<table>`/`<thead>`/`<tbody>` (`page.tsx:301-369`) — the DataTable adoption here is simultaneously the *first* `Table` primitive adoption and the DataTable migration. There is no existing `Table`/`TableRow`/`TableCell` usage to lift structurally, only the cell *content* logic documented above.

---

### `web/src/app/(dashboard)/documentos/columns.tsx` (config, transform)

**Analog:** `DocumentoRow`, `web/src/app/(dashboard)/documentos/page.tsx:277-344` (raw `<tr>`/`<td>`).

Cells to port: Nome (link, `page.tsx:319-327`), Tipo (plain text today: `{tipo ?? "—"}`, `:328`), Processo/Cliente (plain IDs today, no name resolution, `:329-330`), Confid. (plain text `{confidencialidade ?? "PUBLICO"}`, `:331`), Ver. (`v{versao ?? 1}`, **not sortable**, low-value per UI-SPEC, `:332`), Tamanho (`{size.toLocaleString("pt-CV")} bytes`, `:333`), Criado (`new Date(createdAt).toLocaleString("pt-CV")`, `:334`), Ações (`Apagar` button only, gated by `canEditDocumentos`, with `window.confirm()` guard identical to Clientes' delete pattern, `:335-341`).

**Verified correction to UI-SPEC's Badge claim:** UI-SPEC states "Documentos already imports and uses `Badge` elsewhere in the same file (mobile cards, confidencialidade)" — **verified against the full file read this session: only `tipo` uses `Badge` in the mobile card branch** (`page.tsx:236-240`, `variant="blue"`), and **`confidencialidade` is plain text in both mobile and desktop branches today, never a `Badge`** (`page.tsx:331` desktop; not rendered at all in `DocumentoMobileCard`). There is no existing confidencialidade→variant mapping anywhere in this file or in `documentos/[id]/page.tsx` (confirmed via grep, zero matches). **Executor must invent a reasonable mapping** (e.g. `PUBLICO`→`gray`, `CONFIDENCIAL`→`red`/`amber` — pick from the existing 6-variant set `blue`/`green`/`amber`/`red`/`purple`/`gray`, never a new hand-rolled span) when this column becomes a DataTable cell, and should carry the existing `tipo`→`blue` Badge mapping forward unchanged for the Tipo column.

**Scope note (per UI-SPEC #1):** same as Financeiro — raw `<table>` today (`page.tsx:138-172`), first `Table` primitive adoption + DataTable migration combined.

---

### `web/src/app/(dashboard)/{clientes,processos,pareceres,financeiro,documentos}/page.tsx` (route, CRUD) — shared migration shape

**Analog for all 5:** each page's own current desktop branch (`hidden md:block`, see per-file line ranges in the File Classification table above). The migration shape is identical across all 5:
1. Keep the outer `Card`/`CardContent` shell, filter `Card` (untouched), and the `md:hidden` mobile-cards branch (untouched) exactly as-is.
2. Replace the `hidden md:block` branch's `<Table>...</Table>` (Clientes/Processos/Pareceres) or raw `<table>...</table>` (Financeiro/Documentos) + manual/absent footer with:
   ```tsx
   <div className="hidden md:block">
     <DataTable columns={columns} data={rows} />
   </div>
   ```
3. Move all per-row cell logic into that screen's new `columns.tsx` (see per-screen sections above).
4. Do **not** touch the `use-*` hook calls, `filters` state, `onSubmit`/`onClear` handlers, or the outer `isPending`/`isError`/`!data?.length` guard — these stay exactly as today (locked decision, `104-CONTEXT.md`).

---

### `web/src/app/(dashboard)/notificacoes/page.tsx` (route, CRUD)

**Analog:** itself, pre-swap (`page.tsx:228-250`, full excerpt already shown above in the `data-table-pagination.tsx` section).

**Swap contract:** replace the hand-rolled `Button`-pair pager with the newly-added official `Pagination`/`PaginationContent`/`PaginationItem`/`PaginationPrevious`/`PaginationNext` (from `pnpm dlx shadcn@latest add pagination --diff`, not yet installed — confirmed absent, see Registry Safety in UI-SPEC). Preserve the exact same minimal shape (no numbered `PaginationLink`s), same copy ("Página {n} de {total}"), same disabled-at-bounds logic (`page === 0` / `page + 1 >= totalPages`), same `useNotificacoes({ page, size: 20 })` data contract (`page.tsx:55-60`) — this is a component swap only, not a UX change. `PaginationPrevious`/`PaginationNext` render `<a>` by default per shadcn's docs (confirmed in `RESEARCH.md`/`FEATURES.md`); since this app uses client-side `setPage` state (not URL-driven routing), render them with `asChild={false}` and an `onClick` (or wrap in a `<button>`-rendering variant) rather than a Next.js `<Link>` — verify the exact prop shape against the CLI-generated `pagination.tsx` output at implementation time, since this differs from the `<Link>`-based pattern `FEATURES.md` describes for URL-driven pagination.

---

## Shared Patterns

### Badge variant vocabulary (closed set — never invent new colors)
**Source:** `web/src/components/ui/badge.tsx:6-29` (full file, 37 lines, already read)
**Apply to:** every `columns.tsx` status/type cell across all 5 screens.
```typescript
variant: "default" | "secondary" | "outline" | "blue" | "green" | "amber" | "red" | "purple" | "gray"
```
Every screen's status/type mapping (Clientes' tipo, Processos' estado + risco, Pareceres' status, Financeiro's estado — see per-screen sections above) must resolve to one of these 6 semantic variants (`blue`/`green`/`amber`/`red`/`purple`/`gray`). This closes Financeiro's hand-rolled `statusBadgeClass` span gap as a natural side effect of the `columns.tsx` rewrite (per UI-SPEC point 2).

### Dual-view (mobile cards / desktop DataTable) split — untouched boundary
**Source:** established since v2.3, present verbatim in all 5 screens (e.g. `clientes/page.tsx:419-483` mobile, `:485-519` desktop).
**Apply to:** all 5 `page.tsx` files — the `md:hidden`/`hidden md:block` split itself is not touched by this phase; only the *contents* of the `hidden md:block` branch change (raw `Table`/`<table>` → `<DataTable>`).

### Row-action icon-button idiom (`Tooltip` + `Button variant="ghost" size="sm"` + `lucide-react` icon)
**Source:** `clientes/page.tsx:603-656`, `processos/page.tsx:382-391`, `pareceres/page.tsx:470-479`
**Apply to:** every screen's Ações column cell renderer in the new `columns.tsx`, and the sort-toggle button in `data-table-column-header.tsx`.

### Cross-hook lookup Maps (`clienteNomeById`, `processoById`) passed by closure
**Source:** `processos/page.tsx:56`, `pareceres/page.tsx:84-87`, `financeiro/page.tsx:135-136`
**Apply to:** any `columns.tsx` whose cells need to resolve a foreign-key id to a display name (Processos, Pareceres, Financeiro) — these Maps are built in the `page.tsx` from a sibling `use-*` hook and must be passed into a `columns(...)` factory function, since column defs are plain objects and cannot call hooks directly.

### Server-side-filter / client-side-sort-and-paginate boundary (locked architecture decision)
**Source:** `104-CONTEXT.md` Decisions section; every screen's existing `filters` `useState` + `use-*` hook (e.g. `clientes/page.tsx:62-106`, `processos/page.tsx:45-109`)
**Apply to:** `data-table.tsx` — `useReactTable` must configure only `getCoreRowModel()`, `getSortedRowModel()`, `getPaginationRowModel()`. Never `getFilteredRowModel()`. Each screen's existing filter Card/hooks remain the sole source of "what rows exist"; DataTable only sorts/paginates what it's given.

### Outer loading/error/empty guard (unchanged, precedes DataTable mount)
**Source:** every screen, e.g. `clientes/page.tsx:409-417`, `documentos/page.tsx:126-136`
**Apply to:** all 5 `page.tsx` — the `isPending`/`isError`/`!data?.length` ternary chain stays exactly where it is, wrapping the new `<DataTable>` the same way it wrapped the old `<Table>`/`<table>`. `data-table.tsx`'s own internal "no rows" fallback is defensive only and should rarely render.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `web/src/components/shared/data-table/data-table.tsx` | component | transform | First `useReactTable` composition in the repo — `ARCHITECTURE.md` Pattern 5 explicitly confirms `table.tsx` (existing) and `DataTable` (new) are different things; build from the official shadcn Data Table recipe + the existing `Table` primitive, not from an in-repo precedent. |
| `web/src/components/shared/data-table/data-table-view-options.tsx` | component | event-driven | `DropdownMenuCheckboxItem` is installed (`dropdown-menu.tsx`) but has zero existing call sites anywhere in `web/src/app` — compose directly from the primitive's own API, documented above. |
| `web/src/app/(dashboard)/*/columns.tsx` (all 5, as standalone files) | config | transform | No screen currently separates column definitions from row-rendering JSX into their own file — but each has a strong 1:1 source (the existing `*Row` component or inline row-map block) documented per-screen above; "no analog" here refers only to the *file-splitting* convention, not the cell logic itself. |

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/{clientes,processos,pareceres,financeiro,documentos,notificacoes}/page.tsx` (all read in full), `web/src/components/ui/{table,badge,button,select,dropdown-menu,tooltip}.tsx` (all read in full), `web/src/lib/prazos.ts` (read in full), `web/package.json`, `web/components.json`, repo-wide grep for `totalPages` and `DropdownMenuCheckboxItem`/`confidencialidade` usage.
**Files scanned:** 6 page files + 6 ui primitive files + 1 lib helper + config files + targeted greps = 14 direct reads, 4 grep/glob sweeps.
**Pattern extraction date:** 2026-07-16

---
*Pattern mapping for: Phase 104 — Padrão DataTable Partilhado*
