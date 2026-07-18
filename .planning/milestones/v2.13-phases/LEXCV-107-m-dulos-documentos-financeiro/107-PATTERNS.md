# Phase 107: Módulos Documentos + Financeiro - Pattern Map

**Mapped:** 2026-07-16
**Files analyzed:** 9 (8 modified route/component files + 1 new shared component)
**Analogs found:** 9 / 9

All line numbers below were re-verified directly against the current source tree (post-Phase-105 state for `processos/[id]/page.tsx` and `clientes/[id]/page.tsx`), not copied blindly from `107-CONTEXT.md`. They matched `107-CONTEXT.md`/`107-UI-SPEC.md` exactly in every case checked (no intervening phase touched the specific line ranges in scope here).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `web/src/components/shared/combobox.tsx` (NEW) | component (shared composition) | request-response (client-side select/search, no network) | `web/src/components/shared/date-picker-field.tsx` | exact (only prior from-scratch Popover-composition precedent in the project) |
| `web/src/app/(dashboard)/documentos/page.tsx` | route/page (list + filters) | CRUD (read + client-side filter submit) | `web/src/app/(dashboard)/agenda/page.tsx` (Select filter + sentinel pattern); itself for RBAC | role-match |
| `web/src/app/(dashboard)/documentos/novo/page.tsx` | route/page (create form, file upload) | CRUD (create) + file-I/O (multipart upload w/ progress) | `web/src/app/(dashboard)/processos/novo/page.tsx` (NativeSelect); itself for Progress/RBAC | role-match |
| `web/src/app/(dashboard)/documentos/[id]/page.tsx` | route/page (detail) | CRUD (read/delete) | `web/src/app/(dashboard)/agenda/page.tsx` (RBAC `isFetched` fix) | role-match (RBAC-only change) |
| `web/src/app/(dashboard)/processos/[id]/page.tsx` → `ProcessoDocumentosTab` | component (tab, dialog form) | CRUD (create) + file-I/O (upload w/ progress) | `clientes/[id]/page.tsx` → `ClienteDocumentosEntreguesTab` (byte-identical sibling); `date-picker-field.tsx` for Combobox | exact (sibling duplicate) |
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` → `ClienteDocumentosEntreguesTab` | component (tab, dialog form) | CRUD (create) + file-I/O (upload w/ progress) | `processos/[id]/page.tsx` → `ProcessoDocumentosTab` (byte-identical sibling); `date-picker-field.tsx` for Combobox | exact (sibling duplicate) |
| `web/src/app/(dashboard)/financeiro/page.tsx` | route/page (list + filters + KPIs) | CRUD (read + client-side filter) | `web/src/app/(dashboard)/agenda/page.tsx` (Select filter + `"todos"` sentinel + predicate fix, exact same shape) | exact |
| `web/src/app/(dashboard)/financeiro/novo/page.tsx` | route/page (create form) | CRUD (create) | `web/src/app/(dashboard)/processos/novo/page.tsx` (NativeSelect + `register()` wiring) | exact |
| `web/src/app/(dashboard)/financeiro/[id]/page.tsx` | route/page (detail + nested forms/dialogs) | CRUD (read/update/delete) | `web/src/app/(dashboard)/agenda/page.tsx` (RBAC `isFetched` fix) | role-match (RBAC-only change, no UI migration) |

## Pattern Assignments

### `web/src/components/shared/combobox.tsx` (NEW component)

**Analog:** `web/src/components/shared/date-picker-field.tsx` (full file, 96 lines — read in one pass, this is the established "compose a new field from `Popover` primitives, wire via plain `value`/`onChange` props for `Controller` compatibility" precedent). This is the **only** prior from-scratch Popover-based composition in the codebase; `Command`/`command.tsx` has **zero** prior consumers, so there is no existing Combobox-shaped analog beyond this structural precedent — `command.tsx`/`popover.tsx` themselves supply the primitive-level contract (already read in full, see below).

**Imports pattern** (`date-picker-field.tsx` lines 1-11 — adapt to Combobox's own icon/primitives):
```typescript
"use client";

import { CalendarIcon } from "lucide-react";
import { pt } from "date-fns/locale";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";
```
For `combobox.tsx`, swap `Calendar`/`CalendarIcon` for `Command`/`CommandInput`/`CommandList`/`CommandGroup`/`CommandItem`/`CommandEmpty` (from `@/components/ui/command`) and `ChevronsUpDown` (new, from `lucide-react`) — everything else (the `Button`/`Popover*`/`cn` imports) carries over unchanged.

**Core trigger + open-state pattern** (`date-picker-field.tsx` lines 40-68 — the shape to replicate exactly, only the popover *content* differs):
```typescript
const [open, setOpen] = React.useState(false);
...
<Popover open={open} onOpenChange={setOpen}>
  <PopoverTrigger asChild>
    <Button
      id={id}
      type="button"
      variant="outline"
      className={cn("justify-start text-left font-normal", withTime ? "flex-1 min-w-0" : "w-full")}
    >
      <CalendarIcon className="mr-2 h-4 w-4 opacity-50" />
      {dateValue ? (
        dateValue.toLocaleDateString(...)
      ) : (
        <span className="text-muted-foreground">Selecionar data</span>
      )}
    </Button>
  </PopoverTrigger>
  <PopoverContent className="w-auto p-0" align="start">
    <Calendar
      mode="single"
      selected={dateValue}
      onSelect={(d) => {
        commit(d, timePart);
        setOpen(false);
      }}
      ...
    />
  </PopoverContent>
</Popover>
```

**Combobox-specific deltas from this precedent (per `107-UI-SPEC.md` Component Inventory, binding contract):**
- Trailing icon (`ChevronsUpDown`, not leading like `CalendarIcon`) — `<ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />`
- `role="combobox" aria-expanded={open}` added to the trigger `Button` (not present on `DatePickerField`'s trigger — new a11y attribute this composition needs)
- `PopoverContent className="w-80 p-0" align="start"` (fixed `w-80`, not `w-auto` like `DatePickerField` — Popper trigger-width-matching was not confirmed against this project's `radix-ui` build, so a fixed comfortable width is prescribed instead)
- Content is `<Command shouldFilter={false}><CommandInput value={query} onValueChange={setQuery} .../><CommandList><CommandGroup>{filtered.map(...)}</CommandGroup></CommandList></Command>` instead of `<Calendar>` — manual filtering is required (do not rely on cmdk's built-in fuzzy filter), needed so a synthetic "create new" `CommandItem` can always render in creatable mode regardless of cmdk's own match logic
- `CommandItem` gets `data-checked={option.value === value}` and renders **only** the label as children — `command.tsx`'s `CommandItem` (read in full, lines 146-164 below) already auto-renders a trailing `CheckIcon` gated on that `data-checked` attribute; do **not** additionally render a manual `<Check>` child (that would double the checkmark)

**`command.tsx` full primitive contract already read** (`web/src/components/ui/command.tsx`, 193 lines, single Read pass — no re-read needed):
- `CommandInput` (lines 65-86) is pre-wrapped in `InputGroup`/`InputGroupAddon` with a baked-in leading `SearchIcon` and forced `h-8` — do not pass a height-override className
- `CommandItem` (lines 146-164):
```typescript
function CommandItem({ className, children, ...props }: React.ComponentProps<typeof CommandPrimitive.Item>) {
  return (
    <CommandPrimitive.Item
      data-slot="command-item"
      className={cn("group/command-item relative flex cursor-default items-center gap-2 rounded-sm px-2 py-1.5 text-sm outline-hidden select-none ... data-selected:bg-muted data-selected:text-foreground ...", className)}
      {...props}
    >
      {children}
      <CheckIcon className="ml-auto opacity-0 group-has-data-[slot=command-shortcut]/command-item:hidden group-data-[checked=true]/command-item:opacity-100" />
    </CommandPrimitive.Item>
  )
}
```
- `CommandEmpty` (lines 104-115) — plain `py-6 text-center text-sm` wrapper, use for the "Nenhuma sugestão."/"Nenhum processo encontrado."/"Nenhum cliente encontrado." empty states

**`popover.tsx` full primitive contract already read** (`web/src/components/ui/popover.tsx`, 37 lines): `Popover`/`PopoverTrigger`/`PopoverContent` are thin re-exports of `radix-ui`'s `Popover.Root`/`Trigger`/`Content` wrapped in a `Portal`, default `align="end" sideOffset={8}` (override `align="start"` explicitly, matching `DatePickerField`'s own override) — no other project-specific wiring beyond the animation classes.

**Wiring pattern for RHF consumers** (Controller usage — copy verbatim shape from the existing `DatePickerField` consumer, `web/src/app/(dashboard)/agenda/novo/page.tsx` lines 185-197):
```typescript
<div className="space-y-2 sm:col-span-1">
  <Label htmlFor="dataInicio">Início</Label>
  <Controller
    control={form.control}
    name="dataInicio"
    render={({ field }) => (
      <DatePickerField id="dataInicio" label="Início" value={field.value} onChange={field.onChange} withTime />
    )}
  />
  {form.formState.errors.dataInicio ? (
    <p className="text-sm text-red-600">{form.formState.errors.dataInicio.message}</p>
  ) : null}
</div>
```
For `documentos/page.tsx`'s 2 filter Comboboxes (`processo_id`/`cliente_id`), replace `DatePickerField` with `Combobox` in the identical `Controller` shape — `documentosFiltersFormSchema` (see `web/src/schemas/documentos.ts` lines 9-12) already types both fields as plain optional strings, **no schema change needed**.

For the 2 creatable-mode call sites (`ProcessoDocumentosTab`/`ClienteDocumentosEntreguesTab`), the host state is **plain `React.useState`** (`novoTipo`/`setNovoTipo`, confirmed at `processos/[id]/page.tsx:2512` and `clientes/[id]/page.tsx:1246`), not RHF-bound — wire `Combobox` there with plain `value={novoTipo} onChange={setNovoTipo}`, no `Controller`.

---

### `web/src/app/(dashboard)/documentos/page.tsx` (route/page, CRUD list+filter)

**Analog:** `web/src/app/(dashboard)/agenda/page.tsx` for the Select/Combobox-filter shape (this file already imports `Select`); this file itself for the RBAC fix (identical fix shape needed elsewhere in this same phase).

**RBAC fix** (line 35, verified current):
```typescript
if (!permissions.isLoading && !canViewDocumentos) {
```
→
```typescript
if (permissions.isFetched && !canViewDocumentos) {
```
Exact fixed-state precedent already in the codebase (`web/src/app/(dashboard)/agenda/page.tsx:27`):
```typescript
if (permissions.isFetched && !canViewAgenda) {
```

**Filters to migrate to Combobox** (lines 111-126, verified current, form is `<form className="grid gap-4 sm:grid-cols-3" onSubmit={form.handleSubmit(onSubmit)}>` at line 111):
```typescript
            <div className="space-y-2">
              <Label htmlFor="processo_id">Processo ID</Label>
              <Input id="processo_id" {...form.register("processo_id")} placeholder="Ex.: 7c8b..." />
              {form.formState.errors.processo_id ? (
                <p className="text-sm text-red-600">{form.formState.errors.processo_id.message}</p>
              ) : null}
            </div>

            <div className="space-y-2">
              <Label htmlFor="cliente_id">Cliente ID</Label>
              <Input id="cliente_id" {...form.register("cliente_id")} placeholder="Ex.: 1a2b..." />
              {form.formState.errors.cliente_id ? (
                <p className="text-sm text-red-600">{form.formState.errors.cliente_id.message}</p>
              ) : null}
            </div>
```
Replace each `<Input .../>` with a `Controller`-wrapped `<Combobox>` per the wiring pattern above; keep the surrounding `<div className="space-y-2">`/`<Label>`/error-paragraph structure unchanged. Options sourced from `processos.data`/`clientes.data`, already fetched on this page at lines 64-65 (`useProcessos()`, `useClientes({})`) and already mapped into `processoById`/`clienteNomeById` (lines 67-74) — reuse the same label logic (`p.numero ?? p.titulo ?? p.id` / `c.nome`) for the Combobox options, do not add a second fetch.

---

### `web/src/app/(dashboard)/documentos/novo/page.tsx` (route/page, create form + upload)

**Analog:** itself for Progress/RBAC (this is one of the 3 duplicated Progress sites, and one of the 6 RBAC-fix sites); `web/src/app/(dashboard)/processos/novo/page.tsx` for the `NativeSelect` substitution pattern.

**RBAC fix** (line 120, verified current):
```typescript
if (!permissions.isLoading && !canCreateDocumentos) {
```
→ `if (permissions.isFetched && !canCreateDocumentos) {` — same fix as above. **Do not touch line 261** (`disabled={form.formState.isSubmitting || upload.isPending || permissions.isLoading || !canCreateDocumentos}`) — that's the legitimate "disable submit while permissions load" use of `isLoading`, out of scope (per `107-CONTEXT.md`).

**Progress bar migration** (lines 179-192, verified current — one of 3 byte-identical duplicates):
```typescript
              {progresso !== null ? (
                <div className="space-y-1">
                  <div className="flex justify-between text-xs text-neutral-500">
                    <span>A enviar...</span>
                    <span>{progresso}%</span>
                  </div>
                  <div className="h-2 w-full rounded-full bg-neutral-200 dark:bg-neutral-700">
                    <div
                      className="h-2 rounded-full bg-blue-600 transition-all"
                      style={{ width: `${progresso}%` }}
                    />
                  </div>
                </div>
              ) : null}
```
Replace only the inner 2 hand-rolled `<div>`s (the `h-2 w-full rounded-full bg-neutral-200...` wrapper + its child) with:
```typescript
<Progress value={progresso ?? 0} />
```
Keep the `<div className="flex justify-between text-xs text-neutral-500">...</div>` label row (lines 181-184) untouched. Import `Progress` from `@/components/ui/progress` (component itself already read in full — `web/src/components/ui/progress.tsx`, 32 lines, a thin `radix-ui` `Progress.Root`/`Indicator` wrapper, `value` prop maps directly, `bg-muted` track / `bg-primary` indicator, `rounded-full` shipped default).

**NativeSelect migration** (lines 231-246, verified current):
```typescript
            <div className="space-y-2">
              <Label htmlFor="confidencialidade">Confidencialidade</Label>
              <select
                id="confidencialidade"
                className="flex h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300"
                {...form.register("confidencialidade")}
              >
                <option value="PUBLICO">Público</option>
                <option value="INTERNO">Interno</option>
                <option value="CONFIDENCIAL">Confidencial</option>
                <option value="RESTRITO">Restrito</option>
              </select>
              {form.formState.errors.confidencialidade ? (
                <p className="text-sm text-red-600">{form.formState.errors.confidencialidade.message}</p>
              ) : null}
            </div>
```
Migrate per the established `NativeSelect` pattern already used elsewhere (`web/src/app/(dashboard)/processos/novo/page.tsx` lines 301-314 — `cliente_id` select, exact shape to replicate):
```typescript
<NativeSelect
  id="cliente_id"
  size="default"
  className="w-full"
  disabled={clientes.isPending || clientes.isError}
  {...intakeForm.register("cliente_id")}
>
  <option value="">{clientes.isPending ? "A carregar..." : "Selecionar cliente"}</option>
  {(clientes.data ?? []).map((c) => (
    <option key={c.id} value={c.id}>{c.nome}</option>
  ))}
</NativeSelect>
```
For `confidencialidade`: `<NativeSelect id="confidencialidade" size="default" className="w-full" {...form.register("confidencialidade")}>` wrapping the same 4 `<option>`s unchanged (fixed enum, no dynamic `.map`, no `disabled` needed) — delete the inline `className="flex h-9 w-full rounded-md border..."` entirely (`NativeSelect`'s own shipped styling replaces it), and import `NativeSelect` from `@/components/ui/native-select`.

**Do NOT migrate** `tipo` (lines 207-213, plain `<Input>`) — locked scope exclusion, this form stays free-text with no Combobox per `107-CONTEXT.md`/`107-UI-SPEC.md`.

---

### `web/src/app/(dashboard)/documentos/[id]/page.tsx` (route/page, detail — RBAC only)

**Analog:** `web/src/app/(dashboard)/agenda/page.tsx` (RBAC `isFetched` fix pattern).

**RBAC fix** (line 25, verified current):
```typescript
if (!permissions.isLoading && !canViewDocumentos) {
```
→ `if (permissions.isFetched && !canViewDocumentos) {` — only change in this file, everything else untouched.

---

### `web/src/app/(dashboard)/processos/[id]/page.tsx` → `ProcessoDocumentosTab` (component, tab + dialog form)

**Analog:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` → `ClienteDocumentosEntreguesTab` — byte-identical sibling implementation (confirmed by direct read of both), migrate both in lockstep; `web/src/components/shared/date-picker-field.tsx`/`command.tsx` for the Combobox itself.

**Function starts at line 2500** (verified current, confirmed unchanged from `107-CONTEXT.md`'s reference). Relevant state (lines 2511-2517):
```typescript
  const [addDocumentoModal, setAddDocumentoModal] = React.useState(false);
  const [novoTipo, setNovoTipo] = React.useState("");
  const [novoFicheiro, setNovoFicheiro] = React.useState<File | null>(null);
  const [progresso, setProgresso] = React.useState<number | null>(null);
  const [uploadError, setUploadError] = React.useState<string | null>(null);

  const upload = useUploadDocumentoComProgresso({ onProgress: setProgresso });
```
`tipoOptions` (lines 2519-2529) — already-computed Combobox suggestion source, unchanged by this migration:
```typescript
  const tipoOptions = React.useMemo(
    () =>
      Array.from(
        new Set(
          (documentosData ?? [])
            .map((d) => d.tipo?.trim())
            .filter((t): t is string => Boolean(t)),
        ),
      ),
    [documentosData],
  );
```

**`datalist` → creatable Combobox** (lines 2591-2607, verified current):
```typescript
                  <div className="space-y-2">
                    <Label htmlFor={datalistId}>Tipo</Label>
                    <input
                      id={datalistId}
                      list={`${datalistId}-options`}
                      className="flex h-9 w-full rounded-none border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300"
                      placeholder="Selecione ou escreva um tipo"
                      value={novoTipo}
                      onChange={(e) => setNovoTipo(e.target.value)}
                      disabled={upload.isPending}
                    />
                    <datalist id={`${datalistId}-options`}>
                      {tipoOptions.map((t) => (
                        <option key={t} value={t} />
                      ))}
                    </datalist>
                  </div>
```
Replace the `<input list=.../>` + `<datalist>` pair with `<Combobox value={novoTipo} onChange={setNovoTipo} options={tipoOptions.map((t) => ({ value: t, label: t }))} creatable placeholder="Selecionar ou escrever tipo..." searchPlaceholder="Pesquisar ou escrever novo tipo..." triggerClassName="rounded-none" />` — plain-state wiring (no `Controller`, matches the existing `useState` shape). `rounded-none` addendum on the trigger matches the file's own sibling-element convention (Verified-against-source finding #1 in `107-UI-SPEC.md`): the sibling `Button`s in the same Dialog already write `rounded-none` explicitly, e.g. line 2571 (`<Button type="button" variant="outline" size="sm" className="rounded-none">`), line 2628, line 2638.

**Progress bar migration** (lines 2608-2621, verified current — byte-identical to the `documentos/novo/page.tsx` duplicate):
```typescript
                  {progresso !== null ? (
                    <div className="space-y-1">
                      <div className="flex justify-between text-xs text-neutral-500">
                        <span>A enviar...</span>
                        <span>{progresso}%</span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-neutral-200 dark:bg-neutral-700">
                        <div
                          className="h-2 rounded-full bg-blue-600 transition-all"
                          style={{ width: `${progresso}%` }}
                        />
                      </div>
                    </div>
                  ) : null}
```
Same `<Progress value={progresso ?? 0} />` swap as `documentos/novo/page.tsx`, label row untouched.

---

### `web/src/app/(dashboard)/clientes/[id]/page.tsx` → `ClienteDocumentosEntreguesTab` (component, tab + dialog form)

**Analog:** `web/src/app/(dashboard)/processos/[id]/page.tsx` → `ProcessoDocumentosTab` (byte-identical sibling, see above — migrate in lockstep, same excerpts apply 1:1 with only the `datalistId`/mutation-payload-key (`cliente_id` vs `processo_id`) differing).

**Function starts at line 1232** (verified current). State block (lines 1245-1251) is character-for-character identical to `ProcessoDocumentosTab`'s (lines 2511-2517 above) except variable scoping.

**`datalist` → creatable Combobox** (lines 1323-1339, verified current, same shape as the Processo sibling):
```typescript
                  <div className="space-y-2">
                    <Label htmlFor={datalistId}>Tipo</Label>
                    <input
                      id={datalistId}
                      list={`${datalistId}-options`}
                      className="flex h-9 w-full rounded-none border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300"
                      placeholder="Selecione ou escreva um tipo"
                      value={novoTipo}
                      onChange={(e) => setNovoTipo(e.target.value)}
                      disabled={upload.isPending}
                    />
                    <datalist id={`${datalistId}-options`}>
                      {tipoOptions.map((t) => (
                        <option key={t} value={t} />
                      ))}
                    </datalist>
                  </div>
```
Same replacement as `ProcessoDocumentosTab` above — `<Combobox value={novoTipo} onChange={setNovoTipo} options={tipoOptions.map((t) => ({ value: t, label: t }))} creatable ... triggerClassName="rounded-none" />`.

**Progress bar migration** (lines 1340-1353, verified current — byte-identical 3rd duplicate):
```typescript
                  {progresso !== null ? (
                    <div className="space-y-1">
                      <div className="flex justify-between text-xs text-neutral-500">
                        <span>A enviar...</span>
                        <span>{progresso}%</span>
                      </div>
                      <div className="h-2 w-full rounded-full bg-neutral-200 dark:bg-neutral-700">
                        <div
                          className="h-2 rounded-full bg-blue-600 transition-all"
                          style={{ width: `${progresso}%` }}
                        />
                      </div>
                    </div>
                  ) : null}
```
Same `<Progress value={progresso ?? 0} />` swap.

---

### `web/src/app/(dashboard)/financeiro/page.tsx` (route/page, CRUD list+filter+KPIs)

**Analog:** `web/src/app/(dashboard)/agenda/page.tsx` — exact match, this file already establishes the `Select` + `"todos"` sentinel + `!== "todos"` predicate pattern this migration must replicate verbatim.

**RBAC fix** (line 103, verified current):
```typescript
if (!permissions.isLoading && !canViewFinanceiro) {
```
→ `if (permissions.isFetched && !canViewFinanceiro) {`

**Sentinel state defaults to fix** (lines 142-143, verified current):
```typescript
  const [filtroProcesso, setFiltroProcesso] = React.useState("");
  const [filtroStatus, setFiltroStatus] = React.useState<"" | "Pendente" | "Parcialmente Pago" | "Pago">("");
```
→ (matching Agenda's exact precedent, `agenda/page.tsx` lines 44-46: `React.useState<string>("todos")`):
```typescript
  const [filtroProcesso, setFiltroProcesso] = React.useState("todos");
  const [filtroStatus, setFiltroStatus] = React.useState<"todos" | "Pendente" | "Parcialmente Pago" | "Pago">("todos");
```

**Filter predicates to fix** (lines 150-151, verified current):
```typescript
  if (filtroProcesso) filteredList = filteredList.filter((h) => h.processoId === filtroProcesso);
  if (filtroStatus) filteredList = filteredList.filter((h) => calcHonorarioStatus(h.totalPago, h.valorTotal) === filtroStatus);
```
→ (matching Agenda's exact precedent, `agenda/page.tsx` line 123: `if (selectedProcessoId !== "todos" && e.processoId !== selectedProcessoId) {`):
```typescript
  if (filtroProcesso !== "todos") filteredList = filteredList.filter((h) => h.processoId === filtroProcesso);
  if (filtroStatus !== "todos") filteredList = filteredList.filter((h) => calcHonorarioStatus(h.totalPago, h.valorTotal) === filtroStatus);
```
Also update the "clear filters" button's reset condition/handler (lines 260-272, verified current — currently checks truthiness and resets to `""`; must reset to `"todos"` and check `!== "todos"` instead) and `onClear`'s `form.reset(...)`-equivalent state resets.

**Processo filter → `Select`** (lines 209-224, verified current):
```typescript
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Processo</label>
          <select
            value={filtroProcesso}
            onChange={(e) => setFiltroProcesso(e.target.value)}
            className="rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-950"
          >
            <option value="">Todos</option>
            {(processos.data ?? []).map((p) => (
              <option key={p.id} value={p.id}>
                {p.numero ?? p.titulo ?? p.id}
              </option>
            ))}
          </select>
        </div>
```
Replace with the exact `Select` composition already proven at `web/src/app/(dashboard)/agenda/page.tsx` lines 246-258:
```typescript
            <Select value={selectedProcessoId} onValueChange={setSelectedProcessoId}>
              <SelectTrigger size="default" className="w-48">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="todos">Todos os Processos</SelectItem>
                {(processos.data ?? []).map((p) => (
                  <SelectItem key={p.id} value={p.id}>
                    {p.numero || p.titulo || "Sem número"}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
```
Per `107-UI-SPEC.md`, drop the `className="w-48"` width override here (Financeiro's filter row has no prior fixed-width convention, unlike Agenda's) — use `SelectTrigger`'s own shipped `w-fit` default, `<SelectTrigger size="default">`. Keep the surrounding `<div className="flex flex-col gap-1"><label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Processo</label>...</div>` wrapper unchanged. Import `Select, SelectContent, SelectItem, SelectTrigger, SelectValue` from `@/components/ui/select` (component itself already read in full — 193 lines, `web/src/components/ui/select.tsx`, Radix-backed).

**Estado filter → `Select`** (lines 226-238, verified current):
```typescript
        <div className="flex flex-col gap-1">
          <label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Estado</label>
          <select
            value={filtroStatus}
            onChange={(e) => setFiltroStatus(e.target.value as "" | "Pendente" | "Parcialmente Pago" | "Pago")}
            className="rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-950"
          >
            <option value="">Todos</option>
            <option value="Pendente">Pendente</option>
            <option value="Parcialmente Pago">Parcialmente Pago</option>
            <option value="Pago">Pago</option>
          </select>
        </div>
```
Same `Select` composition shape, `SelectItem` values `"todos"`/`"Pendente"`/`"Parcialmente Pago"`/`"Pago"` (exact existing copy reused verbatim per `107-UI-SPEC.md`), `onValueChange={setFiltroStatus}` typed as `(v: "todos" | "Pendente" | "Parcialmente Pago" | "Pago") => void` (or cast at the call site, matching the existing `as` cast style at line 230).

---

### `web/src/app/(dashboard)/financeiro/novo/page.tsx` (route/page, create form)

**Analog:** `web/src/app/(dashboard)/processos/novo/page.tsx` (exact `NativeSelect` + dynamic-option-list + `register()` pattern, including the loading/error states this field also needs).

**RBAC fix** (line 28, verified current):
```typescript
if (!permissions.isLoading && !canCreateFinanceiro) {
```
→ `if (permissions.isFetched && !canCreateFinanceiro) {`. **Do not touch line 175** (`disabled={form.formState.isSubmitting || create.isPending || permissions.isLoading || !canCreateFinanceiro}`) — legitimate submit-guard use of `isLoading`, out of scope.

**`selectClassName` constant to delete entirely** (lines 21-22, verified current):
```typescript
const selectClassName =
  "flex h-9 w-full rounded-md border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";
```
Delete both lines — `NativeSelect`'s own shipped styling becomes the single source of truth (same resolution Phase 105's CLP-03/Phase 106's AGD-37 already established elsewhere).

**`processoId` field migration** (lines 102-133, verified current — the `<select>` itself is lines 104-122):
```typescript
            <div className="space-y-2">
              <Label htmlFor="processoId">Processo</Label>
              <select
                id="processoId"
                className={selectClassName}
                disabled={processos.isPending || processos.isError}
                {...form.register("processoId")}
              >
                <option value="">
                  {processos.isPending
                    ? "A carregar..."
                    : processos.isError
                      ? "Erro ao carregar"
                      : "Selecione um processo"}
                </option>
                {(processos.data ?? []).map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.numero || p.titulo || "Sem número"}
                  </option>
                ))}
              </select>
              {processos.isError ? (
                <p className="text-sm text-red-600">
                  {processos.error instanceof Error
                    ? processos.error.message
                    : "Erro ao carregar processos"}
                </p>
              ) : null}
              {form.formState.errors.processoId ? (
                <p className="text-sm text-red-600">{form.formState.errors.processoId.message}</p>
              ) : null}
            </div>
```
Migrate the `<select className={selectClassName} .../>` to `<NativeSelect id="processoId" size="default" className="w-full" disabled={processos.isPending || processos.isError} {...form.register("processoId")}>` (identical children `<option>`s, `disabled`, and error-paragraph blocks below it — untouched), matching `processos/novo/page.tsx` lines 301-314 verbatim in shape (both are a dynamic-option-list `NativeSelect` bound via `register()` with a loading/error-aware placeholder `<option>`). `className="w-full"` is mandatory (`NativeSelect`'s own wrapper defaults to `w-fit`, confirmed at `native-select.tsx:18`). Import `NativeSelect` from `@/components/ui/native-select`.

---

### `web/src/app/(dashboard)/financeiro/[id]/page.tsx` (route/page, detail + nested forms — RBAC only)

**Analog:** `web/src/app/(dashboard)/agenda/page.tsx` (RBAC `isFetched` fix pattern) — no UI-component migration in this file, confirmed by direct read.

**RBAC fix** (line 80, verified current):
```typescript
if (!permissions.isLoading && !canViewFinanceiro) {
```
→ `if (permissions.isFetched && !canViewFinanceiro) {`

**Confirmed nothing else to migrate:** the edit-honorário `Dialog` (lines 270-329, verified current — `valorTotal`/`dataAcordo`/`descricao`, all plain `<Input>`) has no `processoId` field and no other select-worthy field. **Do not touch line 478** (`disabled={form.formState.isSubmitting || createPagamento.isPending || permissions.isLoading || !canEditFinanceiro}`) — legitimate submit-guard `isLoading` use, out of scope. The `metodo` field (`Pagamento.metodo`, line 466-471, plain `<Input>`) stays free text — out of scope per the locked decision (no enum exists).

## Shared Patterns

### RBAC `isFetched` fix (bundled across 6 gates)
**Source of the fixed pattern:** `web/src/app/(dashboard)/agenda/page.tsx:27`
```typescript
if (permissions.isFetched && !canViewAgenda) {
```
**Apply to (verbatim `!permissions.isLoading && !canX` → `permissions.isFetched && !canX` substitution, 6 sites, all confirmed at these exact current line numbers):**
- `documentos/page.tsx:35`
- `documentos/novo/page.tsx:120`
- `documentos/[id]/page.tsx:25`
- `financeiro/page.tsx:103`
- `financeiro/novo/page.tsx:28`
- `financeiro/[id]/page.tsx:80`

**Do NOT touch** the legitimate "disable submit while permissions load" uses of `permissions.isLoading` (confirmed current, unchanged from `107-CONTEXT.md`):
- `documentos/novo/page.tsx:261`
- `financeiro/novo/page.tsx:175`
- `financeiro/[id]/page.tsx:478`

### `NativeSelect` for RHF-bound enum/dynamic-list fields
**Source:** `web/src/components/ui/native-select.tsx` (61 lines, read in full) + established consumer `web/src/app/(dashboard)/processos/novo/page.tsx:301-341` (3 live examples: dynamic-option `cliente_id`, fixed-enum `tipo_processo`, fixed-enum `origem`)
**Apply to:** `documentos/novo/page.tsx` (`confidencialidade`), `financeiro/novo/page.tsx` (`processoId`)
```typescript
<NativeSelect id="fieldId" size="default" className="w-full" {...form.register("fieldName")}>
  <option value="...">...</option>
</NativeSelect>
```
`className="w-full"` is mandatory in every instance (`native-select.tsx:18` — the wrapper's own `w-fit` default). `size="default"` (→ `h-9`) uniformly, matching the existing raw `<select>`'s height.

### `Select` (Radix) + `"todos"` sentinel for list filters
**Source:** `web/src/app/(dashboard)/agenda/page.tsx:44-46` (state defaults), `:123,132` (predicate fix), `:246-258` (JSX composition)
**Apply to:** `financeiro/page.tsx` (Processo + Estado filters — **2** sentinel/predicate fixes needed here, not 1, since neither filter used `"todos"` before, unlike Agenda where 2 of 3 filters already did)
```typescript
const [filtroX, setFiltroX] = React.useState("todos");
// ...
if (filtroX !== "todos") filteredList = filteredList.filter(...);
// ...
<Select value={filtroX} onValueChange={setFiltroX}>
  <SelectTrigger size="default"><SelectValue /></SelectTrigger>
  <SelectContent>
    <SelectItem value="todos">Todos</SelectItem>
    {/* ...options */}
  </SelectContent>
</Select>
```
No fixed-width `className` on `SelectTrigger` for Financeiro (per `107-UI-SPEC.md`, differs from Agenda's `w-48`/`w-40`/`w-36` — Financeiro's filter row has no prior fixed-width convention).

### `Progress` primitive for upload bars (3 duplicated call sites)
**Source:** `web/src/components/ui/progress.tsx` (32 lines, read in full — thin `radix-ui` wrapper, `value` prop 0-100, `bg-muted` track / `bg-primary` indicator, `h-1.5`/`rounded-full` shipped defaults)
**Apply to:** `documentos/novo/page.tsx:179-192`, `processos/[id]/page.tsx` `ProcessoDocumentosTab:2608-2621`, `clientes/[id]/page.tsx` `ClienteDocumentosEntreguesTab:1340-1353` — all 3 byte-identical, same swap in each:
```typescript
<Progress value={progresso ?? 0} />
```
Replaces only the 2 inner hand-rolled `<div>`s; the `"A enviar..." / {progresso}%` label row above stays untouched in all 3. Zero change to `useUploadDocumentoComProgresso`/`XMLHttpRequest.upload.onprogress` (`web/src/hooks/use-documentos.ts:103-159`, not read this pass — out of scope, confirmed unchanged by `107-CONTEXT.md`).

### `Combobox` (new) for free-typeable-but-suggested / searchable-closed-list fields
**Source:** `web/src/components/shared/date-picker-field.tsx` (structural precedent) + `web/src/components/ui/command.tsx` + `web/src/components/ui/popover.tsx` (both read in full, primitive contracts above)
**Apply to:** `processos/[id]/page.tsx` `ProcessoDocumentosTab` tipo field (creatable), `clientes/[id]/page.tsx` `ClienteDocumentosEntreguesTab` tipo field (creatable), `documentos/page.tsx` Processo filter (closed, RHF `Controller`), `documentos/page.tsx` Cliente filter (closed, RHF `Controller`) — 2 distinct wiring modes (plain `useState` vs. `Controller`), see per-file sections above and the new-component section for the full contract. **Not** applied to `documentos/novo/page.tsx`'s `tipo` field (stays plain `Input`, locked exclusion).

## No Analog Found

None. Every file in scope has at least a role-match analog already in the codebase; the new `Combobox` component has a strong structural precedent (`DatePickerField`) even though no prior `Command`-based composition exists.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/{documentos,financeiro,processos,clientes,agenda}/**`, `web/src/components/{ui,shared}/**`
**Files scanned:** `documentos/page.tsx`, `documentos/novo/page.tsx`, `documentos/[id]/page.tsx`, `processos/[id]/page.tsx` (targeted range 2500-2649), `clientes/[id]/page.tsx` (targeted range 1232-1361), `financeiro/page.tsx`, `financeiro/novo/page.tsx`, `financeiro/[id]/page.tsx` (targeted ranges 1-100, 260-334, 465-489), `agenda/page.tsx` (targeted ranges), `agenda/novo/page.tsx` (targeted range 183-201), `processos/novo/page.tsx` (targeted grep+range), `web/src/components/ui/progress.tsx`, `command.tsx`, `popover.tsx`, `select.tsx`, `native-select.tsx` (all read in full), `web/src/components/shared/date-picker-field.tsx` (read in full), `web/src/schemas/documentos.ts` (read in full), `web/src/hooks/use-processos.ts`/`use-clientes.ts` (signature grep only)
**Pattern extraction date:** 2026-07-16
**Note on out-of-code-scope item:** `.planning/REQUIREMENTS.md:84` (`DOF-V2-01`) is a planning-doc update, not a source file — no pattern mapping applies; flagged here only so the planner remembers it needs closing/updating as part of this phase's Combobox work on the Cliente `datalist` site.
