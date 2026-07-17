# Phase 108: Módulo Pareceres - Pattern Map

**Mapped:** 2026-07-17
**Files analyzed:** 3 (all modified, none new)
**Analogs found:** 3 / 3 (2 first-of-kind compositions flagged, no direct precedent)

> Note: line numbers below were re-verified against the actual current file state on disk (2026-07-17) and differ slightly from `108-CONTEXT.md`/`108-UI-SPEC.md`'s cited ranges in places (files have drifted a few lines since those docs were written). Use the numbers in this document when writing plans.

## File Classification

| Modified File | Role | Data Flow | Closest Analog | Match Quality |
|----------------|------|-----------|-----------------|---------------|
| `web/src/app/(dashboard)/pareceres/page.tsx` | component (list page + filters) | request-response (client-side draft state → server query params) | `web/src/app/(dashboard)/financeiro/page.tsx` + `web/src/app/(dashboard)/agenda/page.tsx` (Select/todos-sentinel pattern); `web/src/app/(dashboard)/processos/page.tsx` (draft+onApply/onClear structural pattern, own file's existing structure) | role-match (Select pattern is exact; draft/apply structural pattern already exists in this same file, just needs the sentinel-translation update) |
| `web/src/app/(dashboard)/pareceres/nova/page.tsx` | component (RHF create form) | request-response (form submit → mutation) | `web/src/app/(dashboard)/processos/novo/page.tsx` (`cliente_id` dynamic-loading NativeSelect, `isPending`/`isError` handling — exact match for `clienteId`); `web/src/app/(dashboard)/documentos/novo/page.tsx` (fixed-enum NativeSelect, exact match for `prioridade`) | exact |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` | component (detail page: dialog + timeline) | request-response (dialog `<select>` → NativeSelect) + first-of-kind Tooltip+Accordion composition (timeline) | `web/src/app/(dashboard)/pareceres/[id]/page.tsx`'s own `EntregarParecerDialog` (component swap only, no analog needed beyond `native-select.tsx` itself); `web/src/components/ui/accordion.tsx` + `web/src/components/ui/tooltip.tsx` (primitive shipped defaults, zero prior consumer for either the Accordion or the Tooltip-on-a-timeline-marker combination) | role-match (dialog select) / no-analog, first-of-kind (Tooltip+Accordion timeline) |

## Pattern Assignments

### `web/src/app/(dashboard)/pareceres/page.tsx` (component, request-response — 6 Select filters + RBAC fix)

**Current state (verified against source, 2026-07-17):**

- RBAC gate: line 29 — `if (!permissions.isLoading && !canView) {`
- Draft filter state: lines 45-48 (quick filters `draftStatus`/`draftAdvogadoId`/`draftClienteId`, all `useState("")`, plus `filters` state)
- Advanced-search state: lines 50-58 (`pesquisaClienteId`/`pesquisaAdvogadoId`/`pesquisaStatus`, all `useState("")`)
- `onApply` (quick filters, commits straight through with no sentinel check): lines 80-88
- `onClear` (quick filters reset): lines 90-95
- `onPesquisar` (advanced search, has truthy-string checks already): lines 97-114
- `onLimparPesquisa`: lines 116-125
- Quick-filter selects (Estado/Advogado/Cliente): lines 178-188, 196-207, 215-226 (all `<select className="h-10 w-full ... rounded-none ...">`, first `<option value="">Todos</option>`)
- Advanced-search selects (Cliente/Advogado/Estado): lines 259-270, 278-289, 297-307 (identical shape/className to the quick filters)

**Analog 1 — Select-with-`"todos"`-sentinel component pattern:** `web/src/app/(dashboard)/financeiro/page.tsx` and `web/src/app/(dashboard)/agenda/page.tsx`

**Imports** (`financeiro/page.tsx:9`):
```typescript
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
```

**State + filter-check pattern** (`financeiro/page.tsx:143-152`):
```typescript
const [filtroProcesso, setFiltroProcesso] = React.useState("todos");
const [filtroStatus, setFiltroStatus] = React.useState<"todos" | "Pendente" | "Parcialmente Pago" | "Pago">("todos");
...
let filteredList = list;
if (filtroProcesso !== "todos") filteredList = filteredList.filter((h) => h.processoId === filtroProcesso);
if (filtroStatus !== "todos") filteredList = filteredList.filter((h) => calcHonorarioStatus(h.totalPago, h.valorTotal) === filtroStatus);
```

**JSX pattern** (`financeiro/page.tsx:210-225`, dynamic options + sentinel):
```typescript
<Select value={filtroProcesso} onValueChange={setFiltroProcesso}>
  <SelectTrigger size="default">
    <SelectValue />
  </SelectTrigger>
  <SelectContent>
    <SelectItem value="todos">Todos</SelectItem>
    {(processos.data ?? []).map((p) => (
      <SelectItem key={p.id} value={p.id}>
        {p.numero || p.titulo || "Sem número"}
      </SelectItem>
    ))}
  </SelectContent>
</Select>
```

**Clear-filters reset to sentinel** (`agenda/page.tsx:295-299`):
```typescript
onClick={() => {
  setSelectedProcessoId("todos");
  setSelectedCategoria("todos");
  setSelectedConcluido("todos");
}}
```

Neither Financeiro nor Agenda uses a draft-state + explicit "Aplicar" submit button — both filter the already-fetched in-memory list immediately on `onValueChange`. Pareceres' quick filters are **server**-driven (`usePareceres(filters)` refetches on `filters` change) with a draft/apply split, so the sentinel-translation logic must be adapted, not copied verbatim — see Analog 2.

**Analog 2 — draft-state + `onApply`/`onClear` structural pattern (same-file precedent for the shape being kept, sentinel differs):** `web/src/app/(dashboard)/processos/page.tsx:40-113` (own file already uses this exact `draftX` → `onApply` → `setFilters` shape, just with `""` not `"todos"` since it uses `NativeSelect`, not `Select`)
```typescript
const onApply = (e: React.FormEvent) => {
  e.preventDefault();
  setFilters((c) => ({
    ...c,
    q: draftQuery.trim(),
    estado: draftEstado.trim(),
    ...
  }));
};
```

**Required adaptation for Pareceres (not a copy-paste — the sentinel translation is new logic):**
- `onApply` (`page.tsx:80-88`) must translate `"todos"` → omit-the-key (or `""`) before calling `setFilters`, e.g. `status: draftStatus === "todos" ? "" : draftStatus` for all 3 quick-filter fields (the hook's `buildParecerSearch` already drops empty strings — UI-SPEC finding #1).
- `onClear` (`page.tsx:90-95`) must reset the 3 draft states to `"todos"`, not `""`.
- `onPesquisar` (`page.tsx:97-114`) currently does `if (status) next.status = status;` for `clienteId`/`advogadoId`/`status` — must become `if (status && status !== "todos") next.status = status;` for all 3 select-backed fields (texto/dataInicio/dataFim are untouched, not selects).
- `onLimparPesquisa` (`page.tsx:116-125`) must reset `pesquisaClienteId`/`pesquisaAdvogadoId`/`pesquisaStatus` to `"todos"`.

**RBAC fix** (line 29): change `if (!permissions.isLoading && !canView)` → `if (permissions.isFetched && !canView)`, matching `financeiro/page.tsx:104` / `agenda/page.tsx:27` / `processos/page.tsx:25` verbatim:
```typescript
if (permissions.isFetched && !canViewFinanceiro) {
```

**Height + rounding correction (component-identity swap, not a copy from elsewhere):** all 6 legacy `<select>`s are `h-10` with a literal `rounded-none` class already; the 6 new `SelectTrigger`s should render at shipped `size="default"` (`h-9`) plus an explicit `rounded-none` className addendum (`className="w-full rounded-none"` on `SelectTrigger`) — see UI-SPEC findings #2/#3.

---

### `web/src/app/(dashboard)/pareceres/nova/page.tsx` (component, request-response — 4 NativeSelect fields + RBAC fix)

**Current state (verified against source, 2026-07-17):**

- Local `selectClassName` const to eliminate: lines 25-26
- RBAC gates: line 35 (`if (!permissions.isLoading && !canCreatePareceres)`) and line 219 (`permissions.isLoading` inside the submit-button `disabled` expression)
- `clienteId` (dynamic, loading-aware): lines 126-148 — `<select id="clienteId" className={selectClassName} disabled={clientes.isPending || clientes.isError} {...form.register("clienteId")}>`
- `processoId` (dynamic, scoped to selected cliente): lines 150-164
- `prioridade` (fixed enum, default `"MEDIA"`): lines 189-196
- `advogadoId` (dynamic, optional): lines 198-208

**Analog 1 — dynamic loading-aware NativeSelect (exact match for `clienteId`):** `web/src/app/(dashboard)/processos/novo/page.tsx:299-319`
```typescript
<Label htmlFor="cliente_id">Cliente</Label>
<NativeSelect
  id="cliente_id"
  size="default"
  className="w-full"
  disabled={clientes.isPending || clientes.isError}
  {...intakeForm.register("cliente_id")}
>
  <option value="">{clientes.isPending ? "A carregar..." : "Selecionar cliente"}</option>
  {(clientes.data ?? []).map((c) => (
    <option key={c.id} value={c.id}>
      {c.nome}
    </option>
  ))}
</NativeSelect>
{clientes.isError ? (
  <p className="text-sm text-red-600">
    {clientes.error instanceof Error ? clientes.error.message : "Erro ao carregar clientes"}
  </p>
) : null}
```
This is a 1:1 structural match for `nova/page.tsx`'s existing `clienteId` block (lines 126-148) — same `disabled={clientes.isPending || clientes.isError}`, same loading-aware first-option text, same error-paragraph-below pattern. Only the wrapping element (`<select className={selectClassName}>` → `<NativeSelect className="w-full rounded-none">`) changes.

**Analog 2 — fixed-enum NativeSelect (matches `prioridade`'s no-empty-option shape):** `web/src/app/(dashboard)/documentos/novo/page.tsx:230-235`
```typescript
<NativeSelect id="confidencialidade" size="default" className="w-full" {...form.register("confidencialidade")}>
  <option value="PUBLICO">Público</option>
  <option value="INTERNO">Interno</option>
  <option value="CONFIDENCIAL">Confidencial</option>
  <option value="RESTRITO">Restrito</option>
</NativeSelect>
```

**RBAC fix:** line 35 → `if (permissions.isFetched && !canCreatePareceres)`; line 219's `permissions.isLoading` inside the submit `disabled` expression (lines 216-221) → `!permissions.isFetched` (or drop the clause per CONTEXT's "remover... das expressões compostas" option), matching the same fix applied file-wide in Analog 1's source files.

**Elimination:** delete `selectClassName` const (lines 25-26) entirely once all 4 call sites are converted — `NativeSelect`'s own shipped styling becomes the sole source of truth (same resolution `processos/novo/page.tsx` and `documentos/novo/page.tsx` already reached).

---

### `web/src/app/(dashboard)/pareceres/[id]/page.tsx` (component, detail — dialog select + Tooltip/Accordion timeline + RBAC fix)

**Current state (verified against source, 2026-07-17):**

- RBAC-adjacent sites needing the `isLoading` → `isFetched` fix: line 83 (`if (!permissions.isLoading && !canView)`), line 158 (`showNovaVersaoForm`, compound expr `!permissions.isLoading && canEditPareceres && isResponsavelOuAdmin && !isConcluido`), lines 160-164 (`showEntregarTrigger`, same shape), line 217 (`{permissions.isLoading ? (` guarding a skeleton placeholder in the JSX return)
- `isResponsavelOuAdmin` (**do not touch**): lines 153-155
- `EntregarParecerDialog`'s `<select id="versaoFinalId">`: lines 492-504 (inside the function starting at line 428)
- Timeline "Histórico de Versões" block (Card): lines 242-307; the version-mapping loop is lines 265-303; the marker `<span>` is line 271; the per-version wrapper `<div key={versao.id} className="relative flex gap-3 py-4">` is line 269

**Analog 1 — dialog select component swap (no external analog needed; component-identity swap onto `native-select.tsx`'s own shipped defaults):**

Current code (`[id]/page.tsx:492-504`):
```typescript
<select
  id="versaoFinalId"
  className="flex h-9 w-full rounded-md border border-neutral-200 bg-transparent px-3 py-1 text-sm shadow-sm dark:border-neutral-800"
  value={selectedVersaoId ?? ""}
  onChange={(e) => setSelectedVersaoId(e.target.value)}
  disabled={entregar.isPending}
>
  {(versoes ?? []).map((versao) => (
    <option key={versao.id} value={versao.id}>
      Versão {versao.numeroVersao} — {formatDateTime(versao.createdAt)}
    </option>
  ))}
</select>
```
Swap the `<select>` tag for `<NativeSelect>` (import from `@/components/ui/native-select`), keep `id`/`value`/`onChange`/`disabled` verbatim (plain controlled `useState`, not RHF — same wiring style as this file's own `selectedVersaoIdState`). Per UI-SPEC finding #4, **no** `rounded-none` addendum and **no** height change — this control's legacy inline class already matches `NativeSelect`'s shipped `h-9`/`rounded-md` almost exactly, so `className="w-full"` alone is sufficient (dropping the now-redundant inline `className` string entirely).

**Analog 2 — Tooltip on icon-only affordance (established pattern, but never yet on a non-button element):** `web/src/app/(dashboard)/pareceres/columns.tsx:9,88-103` (this same module, already imports `Tooltip`)
```typescript
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
...
<Tooltip>
  <TooltipTrigger asChild>
    <Button asChild size="sm" variant="ghost" aria-label="Ver detalhes" className="h-9 w-9 p-0 ...">
      <Link href={...}><MoreVertical className="h-4 w-4" /></Link>
    </Button>
  </TooltipTrigger>
  <TooltipContent>Ver detalhes</TooltipContent>
</Tooltip>
```
Confirmed by grep: every `TooltipTrigger asChild` in the entire `web/src` (`clientes/columns.tsx`, `clientes/page.tsx`, `documentos/columns.tsx`, `processos/columns.tsx`, `processos/[id]/documentos-columns.tsx`, `settings/page.tsx`, `components/shared/dashboard-shell.tsx`, `data-table-view-options.tsx`) wraps an already-focusable `Button`. Zero existing `tabIndex` usage anywhere in `web/src`. **This phase's marker `<span>` (line 271) is the first non-focusable `TooltipTrigger asChild` child in the project** — the `tabIndex={0}` + `aria-label` requirement (UI-SPEC finding #6) has no precedent to copy; it must be added deliberately:
```typescript
<TooltipTrigger asChild>
  <span
    tabIndex={0}
    aria-label={versaoTooltipLabel(versao, index)}
    className="h-2.5 w-2.5 rounded-full shrink-0 bg-slate-400 dark:bg-slate-500"
  />
</TooltipTrigger>
<TooltipContent>{versaoTooltipLabel(versao, index)}</TooltipContent>
```

**Analog 3 — Accordion primitive (first real consumer in the project; shipped defaults, no adaptation needed beyond composition):** `web/src/components/ui/accordion.tsx` (full file, 82 lines, read in full — small enough for one pass)
```typescript
// accordion.tsx:22-33 — AccordionItem's own divider is a non-issue in this nesting (UI-SPEC finding #7)
function AccordionItem({ className, ...props }: React.ComponentProps<typeof AccordionPrimitive.Item>) {
  return (
    <AccordionPrimitive.Item
      data-slot="accordion-item"
      className={cn("not-last:border-b", className)}
      {...props}
    />
  )
}
// accordion.tsx:35-56 — AccordionTrigger auto-renders its own chevron after children, py-4 shipped default
// accordion.tsx:58-79 — AccordionContent wraps children in a div with pt-0 pb-4 shipped default
```
No prior consumer exists anywhere in `web/src` (confirmed by grep — zero imports of `@/components/ui/accordion` outside the primitive itself). Compose per UI-SPEC's binding structure: one `AccordionItem` (`value={versao.id}`) nested **inside** each existing per-version marker wrapper `<div key={versao.id} className="relative flex gap-3">` (drop the row's own `py-4`, rely on `AccordionTrigger`'s shipped `py-4`; give the marker column `pt-4` instead), `AccordionTrigger` wrapping the existing header row unchanged, `AccordionContent` wrapping the author line + `conteudo` paragraph + `AnexoLink` block. `Accordion` root: `type="single" collapsible defaultValue={defaultOpenVersaoId}` (or `type="multiple" defaultValue={[defaultOpenVersaoId]}` — executor's discretion per CONTEXT).

**Shared derivation needed (new code, not copied from anywhere — first-of-kind computation):**
```typescript
const sorted = [...versoes.data].sort((a, b) => b.numeroVersao - a.numeroVersao);
const defaultOpenVersaoId = isConcluido
  ? (parecer.data.versaoFinalId ?? sorted[0]?.id)
  : sorted[0]?.id;

function versaoTooltipLabel(versao: ParecerVersao, index: number): string {
  if (isConcluido) return versao.id === defaultOpenVersaoId ? "Versão entregue" : "Versão anterior";
  return index === 0 ? "Versão atual" : "Versão anterior";
}
```
(`isConcluido` already exists at line 156; `versoes.data`/`parecer.data.versaoFinalId` are already fetched — no new hook/fetch needed.)

**RBAC fix:** lines 83, 158, 160-164 → `permissions.isFetched` (drop the `!` negation, invert the boolean-sense per the `financeiro/page.tsx:104` pattern already shown above); line 217's `{permissions.isLoading ? (` → `{!permissions.isFetched ? (` (this one guards showing a loading skeleton rather than an access-denied return, so the polarity is inverted from the other 6 sites — same semantic fix, opposite boolean).

**Do NOT touch:** `isResponsavelOuAdmin` (lines 153-155) — pre-existing v2.6 instance-level RBAC logic, unrelated to this migration (per CONTEXT.md and UI-SPEC explicit instruction).

---

## Shared Patterns

### RBAC `isFetched` fix (applies to all 3 files, 7 sites total)
**Source:** `web/src/app/(dashboard)/financeiro/page.tsx:104`, `web/src/app/(dashboard)/agenda/page.tsx:27`, `web/src/app/(dashboard)/processos/page.tsx:25`
```typescript
if (permissions.isFetched && !canViewFinanceiro) {
```
**Apply to:**
- `pareceres/page.tsx:29`
- `pareceres/nova/page.tsx:35,219`
- `pareceres/[id]/page.tsx:83,158,160-164,217`

### Select — Radix, list filters, `"todos"` sentinel (applies to all 6 filter call sites in `pareceres/page.tsx`)
**Source:** `web/src/app/(dashboard)/financeiro/page.tsx:9,143-152,210-225`; `web/src/app/(dashboard)/agenda/page.tsx:12,246-258`
```typescript
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
...
<Select value={draftStatus} onValueChange={setDraftStatus}>
  <SelectTrigger size="default" className="w-full rounded-none">
    <SelectValue />
  </SelectTrigger>
  <SelectContent>
    <SelectItem value="todos">Todos</SelectItem>
    <SelectItem value="PENDENTE">Pendente</SelectItem>
    ...
  </SelectContent>
</Select>
```
**Apply to:** all 6 filter selects in `pareceres/page.tsx` (lines 178-188, 196-207, 215-226, 259-270, 278-289, 297-307), with the `onApply`/`onClear`/`onPesquisar`/`onLimparPesquisa` sentinel-translation logic described above.

### NativeSelect — RHF-bound / single-purpose modal control (applies to 5 call sites across 2 files)
**Source:** `web/src/app/(dashboard)/processos/novo/page.tsx:301-319` (dynamic, loading-aware); `web/src/app/(dashboard)/documentos/novo/page.tsx:230-235` (fixed enum)
```typescript
import { NativeSelect } from "@/components/ui/native-select";
...
<NativeSelect id="..." size="default" className="w-full" {...form.register("...")}>
  <option value="...">...</option>
</NativeSelect>
```
**Apply to:** `nova/page.tsx`'s 4 fields (`clienteId`, `processoId`, `prioridade`, `advogadoId`, all with `className="w-full rounded-none"` per this file's literal-class convention) and `[id]/page.tsx`'s `versaoFinalId` (plain controlled `value`/`onChange`, `className="w-full"` only, no `rounded-none` — see finding #4 above).

### Tooltip — icon-only / non-text affordance (established since Phase 102; this phase extends it to a non-focusable marker for the first time)
**Source:** `web/src/app/(dashboard)/pareceres/columns.tsx:9,88-103` (already imported in this module)
```typescript
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
```
`TooltipProvider` is already mounted globally (`web/src/app/providers.tsx:30`, `delayDuration={700}`) — zero new setup. **Apply to:** the timeline marker `<span>` (`[id]/page.tsx:271`), with the mandatory `tabIndex={0}` + `aria-label` addition documented above (no existing consumer does this — new ground for this project).

### Accordion — first real consumer in the project
**Source:** `web/src/components/ui/accordion.tsx` (primitive itself; zero prior consumers to reference)
**Apply to:** `[id]/page.tsx`'s "Histórico de Versões" block (lines 240-308), per the composition described in Analog 3 above.

## No Analog Found

| File/Feature | Role | Data Flow | Reason |
|---------------|------|-----------|--------|
| Timeline marker `Tooltip` (non-focusable trigger, `tabIndex`/`aria-label`) | component (accessibility pattern) | event-driven (hover/focus) | Every existing `TooltipTrigger asChild` in `web/src` wraps an already-focusable `Button`; this is the first time the trigger child needs manual `tabIndex` — confirmed by project-wide grep (zero `tabIndex` usages anywhere) |
| "Histórico de Versões" → `Accordion` composition (nested inside timeline marker wrapper, `defaultValue` derived from `isConcluido`/`versaoFinalId`) | component (collapsible list) | transform (client-side derive default-open item from already-fetched data) | `Accordion`/`AccordionItem`/`AccordionTrigger`/`AccordionContent` have zero prior consumers anywhere in `web/src` (confirmed by grep) — first real usage since the primitive was installed in Phase 101 |

Both first-of-kind items are still fully specified — by the shipped primitive's own defaults (`accordion.tsx`, `tooltip.tsx`) plus `108-UI-SPEC.md`'s binding composition rules — so "no analog" here means no *codebase* precedent to copy, not an open design question for the planner.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/{pareceres,financeiro,agenda,processos,documentos,clientes}/**`, `web/src/components/ui/{select,native-select,tooltip,accordion}.tsx`
**Files scanned:** 3 target files (full read) + 8 analog/reference files (targeted read/grep) + 2 primitive files (full read, small)
**Pattern extraction date:** 2026-07-17
