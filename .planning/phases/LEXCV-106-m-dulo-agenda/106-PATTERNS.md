# Phase 106: Módulo Agenda - Pattern Map

**Mapped:** 2026-07-16
**Files analyzed:** 5 (4 modified + 1 new, recommended)
**Analogs found:** 5 / 5 (RBAC fix and NativeSelect migration have exact in-repo
analogs from Phase 103/105; the Popover+Calendar Date Picker composition has
**no** in-repo analog — it is first-of-kind, so its "analog" is the shadcn
official pattern anatomy already prescribed verbatim in `106-UI-SPEC.md`,
combined with this repo's existing Popover-wiring idiom and its existing
`Controller`-for-non-native-input idiom)

This phase touches 4 existing files. Unlike Phase 105 (which reused patterns
already proven inside the *same* codebase for every migration), this phase has
one migration with zero prior precedent anywhere in the repo (Popover+Calendar).
For that piece, `106-UI-SPEC.md`'s Component Inventory section is itself the
most load-bearing "pattern source" — read it alongside this file, don't treat
this file as a paraphrase of it.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `web/src/app/(dashboard)/agenda/page.tsx` | component (list page, filters + untouched calendar grid) | request-response (filtered list) | RBAC: `web/src/app/(dashboard)/processos/page.tsx` (exact `isFetched` fix, list-page shape). Select: `web/src/components/shared/data-table/data-table-pagination.tsx` (only existing `Select` consumer in the codebase) | exact (RBAC) / role-match (Select — different use case, same primitive wiring) |
| `web/src/app/(dashboard)/agenda/novo/page.tsx` | component (create form) | request-response (CRUD create) | RBAC + NativeSelect: `web/src/app/(dashboard)/processos/novo/page.tsx` (Phase 105 Variant B, identical `selectClassName` string). Date Picker: **no analog** — see Shared Pattern 3 | exact (RBAC, NativeSelect) / no-analog (Date Picker) |
| `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx` | component (edit form) | request-response (CRUD update) | RBAC + NativeSelect: `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` (Phase 105 Variant B, single-select edit form, same `permissions.isFetched` shape). Date Picker: **no analog** | exact (RBAC, NativeSelect) / no-analog (Date Picker) |
| `web/src/app/(dashboard)/agenda/[id]/page.tsx` | component (detail/read page) | request-response (CRUD read) | RBAC: `web/src/app/(dashboard)/clientes/[id]/page.tsx` line 130 (`permissions.isFetched && !canViewClientes`) — identical detail-page shape. No Select/NativeSelect/Calendar work on this file | exact (RBAC only — this file needs nothing else this phase) |
| `web/src/components/shared/date-picker-field.tsx` (**new, Claude's discretion — recommended**) | component (shared composed UI primitive) | none (pure controlled presentational component; no fetch) | No in-repo analog for the composition itself. Built from: `web/src/components/ui/calendar.tsx` + `popover.tsx` (primitives), `web/src/components/shared/notificacao-snooze-control.tsx` (existing Popover open/close orchestration idiom in this repo), `web/src/app/(dashboard)/clientes/novo/page.tsx` lines 166-174/327-335 (existing `Controller` idiom for non-native-input RHF fields) | no-analog (first Popover+Calendar composition) / exact (Controller wiring idiom, Popover open/close idiom) |

---

## Shared Patterns

### 1. RBAC `isFetched` fix — apply verbatim to all 4 files

This bug and its fix are already fully established (Phases 103/105) — this is a
pure find-and-replace, not a new pattern to design.

**Source (list-page shape) — `web/src/app/(dashboard)/processos/page.tsx` lines 20-32:**
```tsx
export default function ProcessosPage() {
  const permissions = usePermissions();
  const canViewProcessos = permissions.can.view("processos");
  const canCreateProcessos = permissions.can.create("processos");

  if (permissions.isFetched && !canViewProcessos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar o módulo de processos."
        backHref="/dashboard"
      />
    );
  }

  return <ProcessosPageContent canCreateProcessos={canCreateProcessos} />;
}
```

**Source (detail-page shape) — `web/src/app/(dashboard)/clientes/[id]/page.tsx` line 130:**
```tsx
if (permissions.isFetched && !canViewClientes) {
```

**Apply — exact line-for-line replacements (only the boolean expression changes, nothing else in each function):**

| File | Line | Before | After |
|---|---|---|---|
| `agenda/page.tsx` | 26 | `if (!permissions.isLoading && !canViewAgenda) {` | `if (permissions.isFetched && !canViewAgenda) {` |
| `agenda/novo/page.tsx` | 31 | `if (!permissions.isLoading && !canCreateAgenda) {` | `if (permissions.isFetched && !canCreateAgenda) {` |
| `agenda/[id]/page.tsx` | 62 | `if (!permissions.isLoading && !canViewAgenda) {` | `if (permissions.isFetched && !canViewAgenda) {` |
| `agenda/[id]/editar/page.tsx` | 65 | `if (!permissions.isLoading && !canEditAgenda) {` | `if (permissions.isFetched && !canEditAgenda) {` |

Do **not** touch the *other* `permissions.isLoading` usages in these files (e.g.
`agenda/novo/page.tsx` line 256's submit-button `disabled={... || permissions.isLoading || ...}`,
`agenda/[id]/editar/page.tsx` line 287 same shape) — those are legitimate
"disable submit while permissions are loading" guards, unrelated to the
access-denied-flash bug, and are out of scope (not part of the locked
correction in `106-CONTEXT.md`).

---

### 2. NativeSelect migration (`<select className={selectClassName}>` → `<NativeSelect>`)

Identical to Phase 105 Pattern 2 Variant B (same `selectClassName` string,
`rounded-md ... ring-neutral-950`, byte-for-byte match with
`processos/novo/page.tsx` and `processos/[id]/editar/page.tsx`). No new
decision needed here — copy Phase 105's resolution directly.

**Source primitive:** `web/src/components/ui/native-select.tsx` (61 lines, read
in full) — `NativeSelect`, `size?: "sm" | "default"` (default `"default"`,
locked value per UI-SPEC), built-in `h-9`/`rounded-md`/`border-input`/
`focus-visible:ring-ring/50`, chevron baked in.
`React.ComponentProps<"select">`-compatible → `{...form.register("field")}`
spreads straight onto it.

**Before — `agenda/novo/page.tsx` lines 119-133 (`processoId`, disabled while loading):**
```tsx
<select
  id="processoId"
  className={selectClassName}
  disabled={processos.isPending || processos.isError}
  {...form.register("processoId")}
>
  <option value="">
    {processos.isPending ? "A carregar..." : processos.isError ? "Erro ao carregar" : "Sem vínculo"}
  </option>
  {(processos.data ?? []).map((p) => (
    <option key={p.id} value={p.id}>{p.numero || p.titulo || "Sem número"}</option>
  ))}
</select>
```

**After:**
```tsx
<NativeSelect
  id="processoId"
  size="default"
  disabled={processos.isPending || processos.isError}
  {...form.register("processoId")}
>
  <option value="">
    {processos.isPending ? "A carregar..." : processos.isError ? "Erro ao carregar" : "Sem vínculo"}
  </option>
  {(processos.data ?? []).map((p) => (
    <option key={p.id} value={p.id}>{p.numero || p.titulo || "Sem número"}</option>
  ))}
</NativeSelect>
```

Plain `<option>` tags inside `NativeSelect` are fine (renders a real `<select>`)
— same as every Phase 105 conversion, `NativeSelectOption` is optional sugar
not used anywhere yet.

**Apply — 7 occurrences total, direct substitution, `className={selectClassName}` deleted at each:**

| File | Field | Lines (before) |
|---|---|---|
| `agenda/novo/page.tsx` | `processoId` | 119-133 |
| `agenda/novo/page.tsx` | `tipo` (Categoria) | 146-157 |
| `agenda/novo/page.tsx` | `prioridade` | 174-178 |
| `agenda/novo/page.tsx` | `recurrenceRule` | 216-225 |
| `agenda/[id]/editar/page.tsx` | `processoId` | 182-196 |
| `agenda/[id]/editar/page.tsx` | `tipo` (Categoria) | 209-220 |
| `agenda/[id]/editar/page.tsx` | `prioridade` | 237-241 |

**Eliminate once every consumer in that file has migrated** (Phase 105's exact
resolution): `const selectClassName = "..."` at `agenda/novo/page.tsx` line 21
and `agenda/[id]/editar/page.tsx` line 25. **Do not** delete `textareaClassName`
(line 24 / line 28 respectively) — it is a separate constant still used by the
`descricao` `<textarea>`, untouched by this phase (same distinction Phase 105
already drew).

**Import to add (both files):** `import { NativeSelect } from "@/components/ui/native-select";`

---

### 3. Select migration for Agenda list filters (`agenda/page.tsx`, first `Select`-as-filter use)

**Source primitive:** `web/src/components/ui/select.tsx` (192 lines, read in
full) — `Select`/`SelectTrigger` (`data-[size=default]:h-9`,
`data-[size=sm]:h-8`, `text-sm`, `border-input`)/`SelectContent`/`SelectItem`/`SelectValue`.
Radix-backed, controlled `value`/`onValueChange` root — **not**
`register()`-compatible (unlike `NativeSelect`), needs explicit
`value`/`onValueChange` wiring same as today's `<select value={...}
onChange={...}>`.

**Only existing consumer in the codebase (structural precedent for imports +
controlled wiring) — `web/src/components/shared/data-table/data-table-pagination.tsx` lines 6-52:**
```tsx
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
// ...
<Select value={`${pageSize}`} onValueChange={(value) => table.setPageSize(Number(value))}>
  <SelectTrigger size="sm" className="w-[70px]">
    <SelectValue placeholder={`${pageSize}`} />
  </SelectTrigger>
  <SelectContent side="top">
    {PAGE_SIZE_OPTIONS.map((size) => (
      <SelectItem key={size} value={`${size}`}>{size}</SelectItem>
    ))}
  </SelectContent>
</Select>
```
This is the shape to copy (controlled root, `SelectTrigger` sized via prop,
`SelectItem` list) — `agenda/page.tsx`'s filters use `size="default"`
(UI-SPEC-locked) instead of `"sm"`, and fixed-width classes on `SelectTrigger`
instead of `w-[70px]`.

**Before — `agenda/page.tsx` lines 243-256 (Processo filter — note the `""` sentinel, must change):**
```tsx
<div className="flex flex-col gap-1.5">
  <span className="text-[10px] font-bold tracking-wider text-slate-500 uppercase">Processo</span>
  <select
    value={selectedProcessoId}
    onChange={(e) => setSelectedProcessoId(e.target.value)}
    className="h-9 w-48 rounded-none border border-slate-300 dark:border-slate-700 bg-white dark:bg-[#020617] px-3 text-xs font-bold text-slate-700 dark:text-slate-300 focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-blue-500"
  >
    <option value="">Todos os Processos</option>
    {(processos.data ?? []).map((p) => (
      <option key={p.id} value={p.id}>{p.numero || p.titulo || "Sem número"}</option>
    ))}
  </select>
</div>
```

**After:**
```tsx
<div className="flex flex-col gap-1.5">
  <span className="text-[10px] font-bold tracking-wider text-slate-500 uppercase">Processo</span>
  <Select value={selectedProcessoId} onValueChange={setSelectedProcessoId}>
    <SelectTrigger size="default" className="w-48">
      <SelectValue />
    </SelectTrigger>
    <SelectContent>
      <SelectItem value="todos">Todos os Processos</SelectItem>
      {(processos.data ?? []).map((p) => (
        <SelectItem key={p.id} value={p.id}>{p.numero || p.titulo || "Sem número"}</SelectItem>
      ))}
    </SelectContent>
  </Select>
</div>
```

**Required logic change (not just a template swap) — the `""` sentinel must
become `"todos"` everywhere it's read/written, per UI-SPEC Scope note #4 (Radix
`Select.Item` throws on `value=""`):**
```tsx
// BEFORE (agenda/page.tsx line 43)
const [selectedProcessoId, setSelectedProcessoId] = React.useState<string>("");
// AFTER
const [selectedProcessoId, setSelectedProcessoId] = React.useState<string>("todos");

// BEFORE (line 122, filter predicate)
if (selectedProcessoId && e.processoId !== selectedProcessoId) { return false; }
// AFTER
if (selectedProcessoId !== "todos" && e.processoId !== selectedProcessoId) { return false; }

// BEFORE ("Limpar Filtros" handler, line 292)
setSelectedProcessoId("");
// AFTER
setSelectedProcessoId("todos");
```

**Categoria (lines 261-271) and Estado (lines 276-284) filters** already use
`"todos"` as their sentinel — no state/logic change needed, just the same
template swap (`<select>`→`Select`/`SelectTrigger`/`SelectContent`/`SelectItem`,
`w-40`/`w-36` on `SelectTrigger`), reusing each `<option>`'s exact copy
(`"Todas"`, `"Todos"`, `"Prazos Criticos"`, `"Audiências"`, `"Diligências"`,
`"Reuniões"`, `"Pendentes"`, `"Concluídos"`) verbatim as `SelectItem` children.

**"Limpar Filtros" button (lines 287-298)** is untouched besides the sentinel
value change shown above — stays `Button variant="ghost"`, same `onClick` shape.

**Import to add:** `import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";`

**Do not touch:** the filter-bar container (`bg-white dark:bg-[#020617]
border-slate-200 dark:border-slate-800 p-4 rounded-none`, line 242) and the
label chips (`text-[10px] font-bold tracking-wider text-slate-500 uppercase`) —
both explicitly out of scope per UI-SPEC.

---

### 4. Popover + Calendar Date Picker composition (first in project — no in-repo analog)

There is genuinely nothing to copy from an existing page for the composition
itself. `106-UI-SPEC.md`'s Component Inventory section (`AGD-36`) is the
primary source of truth for the exact anatomy — read it directly, it is
more precise than any paraphrase here. What **is** available in-repo and
directly reusable:

**(a) The primitives themselves** — `web/src/components/ui/calendar.tsx` (247
lines, read in full: `Calendar` wraps `react-day-picker@9.14.0`, accepts
`locale`/`weekStartsOn` straight through via `{...props}`, default
`buttonVariant="ghost"` for its own nav, `p-3` internal padding, `w-fit` root)
and `web/src/components/ui/popover.tsx` (37 lines, read in full: `Popover` =
`PopoverPrimitive.Root`, `PopoverContent` defaults `align="end"` — **must be
overridden to `align="start"` per UI-SPEC** — `sideOffset={8}`, no `p-0` by
default so callers must pass `className="w-auto p-0"` to cancel the
component's own padding since `Calendar` supplies its own).

**(b) The existing Popover open/close orchestration idiom in this repo** —
`web/src/components/shared/notificacao-snooze-control.tsx` (98 lines, read in
full) is the only other composed (non-trivial) `Popover` usage besides the
plain notification-bell list. Its shape is the direct precedent for
"controlled `open`/`onOpenChange` state + close-on-selection":
```tsx
const [open, setOpen] = React.useState(false);
// ...
<Popover open={open} onOpenChange={setOpen}>
  <PopoverTrigger asChild>
    <Button type="button" variant="ghost" size="sm" className="h-8 w-8 p-0" aria-label="Adiar notificação">
      <Clock className="h-4 w-4" />
    </Button>
  </PopoverTrigger>
  <PopoverContent className="w-56 p-3" align="end">
    {/* ...RadioGroup... */}
    <Button onClick={handleConfirm}>Adiar</Button>
  </PopoverContent>
</Popover>
```
The date-picker composition reuses this exact `useState` + controlled
`open`/`onOpenChange` shape, but closes on `Calendar`'s `onSelect` firing
instead of a separate confirm button (per UI-SPEC's "Close behavior" row —
single click = select + close).

**(c) The existing `Controller`-for-non-native-input idiom** — the composed
date-picker component will not be `register()`-spreadable (it renders a
`Button`+`Calendar`, not a native `<input>`), exactly like this repo's existing
`RadioGroup`/`Switch` fields. `web/src/app/(dashboard)/clientes/novo/page.tsx`
lines 166-174 and 327-335 (read, both `Controller` usages in this file) are the
established in-repo pattern to copy for wiring:
```tsx
import { Controller, useForm } from "react-hook-form";
// ...
<Controller
  control={form.control}
  name="tipo"
  render={({ field }) => (
    <RadioGroup
      value={field.value ?? ""}
      onValueChange={(value) => onTipoChange(value as "PARTICULAR" | "EMPRESA")}
    >
      {/* ... */}
    </RadioGroup>
  )}
/>
```
Apply the same shape for `dataInicio`/`dataFim`/`recurrenceEndDate`:
```tsx
<Controller
  control={form.control}
  name="dataInicio"
  render={({ field }) => (
    <DatePickerField value={field.value} onChange={field.onChange} withTime />
  )}
/>
```

**(d) Reference composed component** (illustrative — exact name/shape is
Claude's discretion per `106-CONTEXT.md`; strongly recommended as one shared
file per UI-SPEC's "Reusability recommendation" rather than 5 inline
duplications across the 2 form files × 3 fields):

```tsx
"use client";

import { CalendarIcon } from "lucide-react";
import { pt } from "date-fns/locale";
import * as React from "react";

import { Button } from "@/components/ui/button";
import { Calendar } from "@/components/ui/calendar";
import { Input } from "@/components/ui/input";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { cn } from "@/lib/utils";

// Do NOT replace with `new Date(v)`: a bare "YYYY-MM-DD" string parses as UTC
// midnight per the ECMA-262 Date Time String Format, while "YYYY-MM-DDTHH:mm"
// parses as local time — that asymmetry shows a day-before off-by-one for any
// negative-UTC-offset timezone (e.g. Cabo Verde, CVT = UTC-01:00) the moment
// recurrenceEndDate is selected. Parsing Y/M/D components directly avoids it
// for both the date-only and date+time variants.
function parseDateOnly(v: string | undefined): Date | undefined {
  if (!v) return undefined;
  const [y, m, d] = v.slice(0, 10).split("-").map(Number);
  if (!y || !m || !d) return undefined;
  return new Date(y, m - 1, d); // local midnight — safe for both date-only and date+time strings
}

export function DatePickerField({
  value,
  onChange,
  withTime = false,
}: {
  value: string | undefined;
  onChange: (v: string) => void;
  withTime?: boolean;
}) {
  const [open, setOpen] = React.useState(false);
  const dateValue = parseDateOnly(value);
  const timePart = withTime && value ? value.slice(11, 16) : "00:00";

  function commit(nextDate: Date | undefined, nextTime: string) {
    if (!nextDate) return;
    const pad = (n: number) => String(n).padStart(2, "0");
    const datePart = `${nextDate.getFullYear()}-${pad(nextDate.getMonth() + 1)}-${pad(nextDate.getDate())}`;
    onChange(withTime ? `${datePart}T${nextTime}` : datePart);
  }

  return (
    <div className={cn(withTime && "flex gap-2")}>
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="outline"
            className={cn("justify-start text-left font-normal", withTime ? "flex-1 min-w-0" : "w-full")}
          >
            <CalendarIcon className="mr-2 h-4 w-4 opacity-50" />
            {dateValue
              ? dateValue.toLocaleDateString("pt-CV", { day: "2-digit", month: "2-digit", year: "numeric" })
              : <span className="text-muted-foreground">Selecionar data</span>}
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-auto p-0" align="start">
          <Calendar
            mode="single"
            selected={dateValue}
            onSelect={(d) => { commit(d, timePart); setOpen(false); }}
            locale={pt}
            weekStartsOn={0}
            initialFocus
          />
        </PopoverContent>
      </Popover>
      {withTime ? (
        <Input
          type="time"
          className="w-24 shrink-0"
          value={timePart}
          onChange={(e) => commit(dateValue, e.target.value)}
        />
      ) : null}
    </div>
  );
}
```

Note: `react-day-picker@9.14.0`'s `onSelect` signature for `mode="single"` is
`(date: Date | undefined) => void` — passing `undefined` (deselect) must be
guarded (the `if (!nextDate) return;` in `commit`), otherwise a stray click
could wipe a required field's RHF value with no validation re-trigger.

**Call sites (3 total):**

| Field | File(s) | `withTime` |
|---|---|---|
| `dataInicio` | `agenda/novo/page.tsx` (lines 184-190), `agenda/[id]/editar/page.tsx` (lines 247-253) | `true` |
| `dataFim` | `agenda/novo/page.tsx` (lines 192-198), `agenda/[id]/editar/page.tsx` (lines 255-261) | `true` |
| `recurrenceEndDate` | `agenda/novo/page.tsx` only (lines 231-239) — **not** `agenda/[id]/editar/page.tsx`, which has no recurrence UI today (UI-SPEC Scope note #1, do not add it) | `false` |

**`toDateTimeLocalValue` helper already in `agenda/[id]/editar/page.tsx` (lines
37-45)** produces the exact `YYYY-MM-DDTHH:mm` shape the new component's
`value` prop expects for `dataInicio`/`dataFim` — no change needed to that
helper, it feeds straight into `field.value` via `form.reset()` (line 100-114)
same as today.

**Imports to add (both form files):** `Controller` (upgrade existing
`import { useForm } from "react-hook-form";` to `import { Controller, useForm } from "react-hook-form";`),
plus the new `DatePickerField` from wherever it's placed (recommended:
`@/components/shared/date-picker-field`).

**Locale requirement — do not skip:** `import { pt } from "date-fns/locale";`
is a first-of-kind import in this repo (confirmed by grep — zero existing
`date-fns` imports anywhere) but the package is already an installed,
Phase-101-vetted dependency (`date-fns@^4.4.0` in `web/package.json`,
`react-day-picker`'s peer dependency) — not a new-package decision.

---

## Pattern Assignments (per file)

### `web/src/app/(dashboard)/agenda/page.tsx` (component, request-response)
**Analogs:** `processos/page.tsx` (RBAC fix), `data-table-pagination.tsx` (Select wiring, only existing consumer).
Applies Shared Pattern 1 (RBAC, line 26) and Shared Pattern 3 (Select ×3 filters + `"todos"` sentinel fix for Processo).
**Imports to add:** `Select, SelectContent, SelectItem, SelectTrigger, SelectValue` from `"@/components/ui/select"`.
**Not touched:** the monthly calendar grid (`buildMonthGrid`/`dayKey`/`cursorMonth`, drag-and-drop) — completely out of scope per `106-CONTEXT.md`.

### `web/src/app/(dashboard)/agenda/novo/page.tsx` (component, request-response)
**Analogs:** `processos/novo/page.tsx` (RBAC + NativeSelect Variant B), Shared Pattern 4's primitives/idioms (no page analog).
Applies Shared Pattern 1 (RBAC, line 31), Shared Pattern 2 (NativeSelect ×4 — `processoId`, `tipo`, `prioridade`, `recurrenceRule`), Shared Pattern 4 (Date Picker ×3 — `dataInicio`/`dataFim` with time, `recurrenceEndDate` date-only).
**Imports to add:** `NativeSelect` from `"@/components/ui/native-select"`; `Controller` added to the existing `react-hook-form` import; `DatePickerField` (new shared component, path at Claude's discretion).
**Eliminate:** `const selectClassName = "..."` (line 21) once all 4 selects migrate. Keep `textareaClassName` (line 24).

### `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx` (component, request-response)
**Analogs:** `processos/[id]/editar/page.tsx` (RBAC + NativeSelect Variant B, single-select edit-form shape), Shared Pattern 4's primitives/idioms.
Applies Shared Pattern 1 (RBAC, line 65), Shared Pattern 2 (NativeSelect ×3 — `processoId`, `tipo`, `prioridade`), Shared Pattern 4 (Date Picker ×2 — `dataInicio`/`dataFim` with time only; **no** `recurrenceEndDate` field here, per UI-SPEC Scope note #1 — do not add one).
**Imports to add:** `NativeSelect` from `"@/components/ui/native-select"`; `Controller` added to the existing `react-hook-form` import; `DatePickerField`.
**Eliminate:** `const selectClassName = "..."` (line 25) once all 3 selects migrate. Keep `textareaClassName` (line 28).

### `web/src/app/(dashboard)/agenda/[id]/page.tsx` (component, request-response)
**Analog:** `clientes/[id]/page.tsx` line 130 (RBAC fix shape).
Applies **only** Shared Pattern 1 (RBAC, line 62) — this file has no `<select>`, no date input, and is explicitly excluded from the Calendar/Select migration scope (read-only detail view). No imports to add.

### `web/src/components/shared/date-picker-field.tsx` (**new, recommended**)
**Analog:** none in-repo for the composition; built from `calendar.tsx` + `popover.tsx` primitives, `notificacao-snooze-control.tsx`'s Popover open/close idiom, and `clientes/novo/page.tsx`'s `Controller` idiom. See Shared Pattern 4(d) for a full reference implementation. Exact file name/path/props shape is Claude's discretion per `106-CONTEXT.md` — a single `withTime` boolean prop (as sketched) satisfies both the date-only and date+time variants without duplicating the trigger/popover/calendar anatomy 3 times.

---

## No Analog Found

| File/Concern | Role | Data Flow | Reason |
|---|---|---|---|
| Popover+Calendar Date Picker composition itself (all 3 call sites) | component (composed UI) | none (controlled presentational) | First such composition in the project — confirmed by grep (zero `date-fns`/`CalendarIcon`/date-picker references anywhere before this phase). `106-UI-SPEC.md`'s Component Inventory (`AGD-36`) is the authoritative source; this file's Shared Pattern 4 packages it with concrete in-repo idioms (Popover wiring, `Controller` wiring) but the composition anatomy itself has no prior codebase instance to diff against. |

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/agenda/`, `web/src/app/(dashboard)/clientes/`, `web/src/app/(dashboard)/processos/`, `web/src/components/ui/`, `web/src/components/shared/`
**Files scanned:** 13 (4 in-scope Agenda files + `select.tsx`, `native-select.tsx`, `calendar.tsx`, `popover.tsx`, `button.tsx`, `input.tsx`, `data-table-pagination.tsx`, `notificacao-snooze-control.tsx`, `clientes/novo/page.tsx` (Controller idiom), `processos/page.tsx`/`clientes/[id]/page.tsx` (RBAC fix), `schemas/eventos.ts`)
**Pattern extraction date:** 2026-07-16
