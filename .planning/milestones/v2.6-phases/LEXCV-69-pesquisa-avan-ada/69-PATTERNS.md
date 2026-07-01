# Phase 69: Pesquisa Avançada - Pattern Map

**Mapped:** 2026-07-01
**Files analyzed:** 2 (1 edit to page, 1 edit to hook)
**Analogs found:** 2 / 2

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|-----------------|----------------|
| `web/src/app/(dashboard)/pareceres/page.tsx` (edit) | component (page, toggle panel + dual-view results) | request-response (submit-triggered fetch) | itself (Phase 65, same file — `advancedOpen` toggle + draft/committed filter split) for the toggle/filter shell; `web/src/app/(dashboard)/financeiro/page.tsx` (lines 141-255) for the native `<input type="date">` range-filter pattern | exact (self) + role-match (date range) |
| `web/src/hooks/use-pareceres.ts` (edit, add `usePesquisarPareceres`) | hook (TanStack Query, CRUD/read) | request-response | `usePareceres` in the same file (lines 22-37) | exact |

## Pattern Assignments

### `web/src/app/(dashboard)/pareceres/page.tsx` (component, request-response)

**Primary analog:** itself — `ParecerPageContent` in Phase 65's committed version (already read in full above, lines 53-278).

**Imports pattern already present** (lines 1-16):
```tsx
"use client";

import Link from "next/link";
import * as React from "react";
import { Filter } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { AccessDeniedState } from "@/components/shared/access-denied-state";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useAdminUsers } from "@/hooks/use-admin";
import { useClientes } from "@/hooks/use-clientes";
import { usePermissions } from "@/hooks/use-permissions";
import { usePareceres, type ParecerSolicitacoesListFilters } from "@/hooks/use-pareceres";
import type { ParecerStatus } from "@/types/pareceres";
```
Add `Search` (and `ChevronDown`/`ChevronUp` or `SlidersHorizontal` per UI-SPEC) to the `lucide-react` import line, and add `usePesquisarPareceres` to the `use-pareceres` import.

**Existing toggle + draft/committed filter-bar pattern to extend, not replace** (lines 56-92):
```tsx
const [advancedOpen, setAdvancedOpen] = React.useState(false);
const [draftStatus, setDraftStatus] = React.useState("");
const [draftAdvogadoId, setDraftAdvogadoId] = React.useState("");
const [draftClienteId, setDraftClienteId] = React.useState("");
const [filters, setFilters] = React.useState<ParecerSolicitacoesListFilters>({});
...
const onApply = (e: React.FormEvent) => {
  e.preventDefault();
  setFilters({
    status: draftStatus.trim(),
    advogadoId: draftAdvogadoId.trim(),
    clienteId: draftClienteId.trim(),
  });
};

const onClear = () => {
  setDraftStatus("");
  setDraftAdvogadoId("");
  setDraftClienteId("");
  setFilters({});
};
```
**Important naming collision to avoid:** the existing `advancedOpen`/`Filtros` toggle (line 56, 111-119) is Phase 65's simple status/advogado/cliente filter reveal — NOT the new "Pesquisa Avançada" toggle from this phase's UI-SPEC. The plan must introduce a **second, distinct** boolean state (e.g. `pesquisaOpen`) and a **second** toggle button with copy "Pesquisa Avançada" / "Ocultar Filtros" (per UI-SPEC section "Copywriting Contract"), styled `variant="outline"` or `variant="ghost"` (never `blue-600`, per UI-SPEC Color section). Do not conflate or rename the existing `Filtros` toggle — it must keep working for the plain list view.

**Select population pattern to reuse verbatim for cliente/advogado in the new panel** (lines 62-71):
```tsx
const clientes = useClientes({});
const adminUsers = useAdminUsers();
const advogados = React.useMemo(
  () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO")),
  [adminUsers.data],
);
const clienteNomeById = React.useMemo(
  () => new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const)),
  [clientes.data],
);
```

**Select markup pattern to replicate for the new panel's status/advogado/cliente fields** (lines 136-186, one representative block):
```tsx
<select
  value={draftStatus}
  onChange={(e) => setDraftStatus(e.target.value)}
  className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
>
  <option value="">Todos</option>
  <option value="PENDENTE">Pendente</option>
  <option value="EM_ELABORACAO">Em elaboração</option>
  <option value="EM_REVISAO">Em revisão</option>
  <option value="CONCLUIDO">Concluído</option>
</select>
```

**Native date-range input pattern — analog:** `web/src/app/(dashboard)/financeiro/page.tsx` (lines 141-142 state, 237-255 markup):
```tsx
const [filtroDataDe, setFiltroDataDe] = React.useState("");
const [filtroDataAte, setFiltroDataAte] = React.useState("");
...
<div className="flex flex-col gap-1">
  <label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Data de</label>
  <input
    type="date"
    value={filtroDataDe}
    onChange={(e) => setFiltroDataDe(e.target.value)}
    className="rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-950"
  />
</div>

<div className="flex flex-col gap-1">
  <label className="text-xs font-medium text-neutral-500 dark:text-neutral-400">Data até</label>
  <input
    type="date"
    value={filtroDataAte}
    onChange={(e) => setFiltroDataAte(e.target.value)}
    className="rounded-md border border-neutral-200 bg-white px-3 py-2 text-sm dark:border-neutral-800 dark:bg-neutral-950"
  />
</div>
```
**Style-token note:** `financeiro/page.tsx` uses `rounded-md`/`neutral-*` tokens (older module convention), whereas `pareceres/page.tsx`'s own select inputs (this phase's exact same file) use `rounded-none`/`slate-*` + `focus-visible:ring-2 focus-visible:ring-blue-500` (current convention, matches UI-SPEC "Registry Safety"/spacing declarations). The plan should copy the **behavioral** pattern (controlled `<input type="date">`, plain `value`/`onChange`, no library) from `financeiro/page.tsx` but the **visual classes** from the pareceres select markup above, to stay consistent with this file's own established look. Per UI-SPEC, the date-range pair sits in a 2-column sub-grid with `gap-2` (8px) inside one grid cell of the `grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4` panel.

**Dual-view results rendering to reuse verbatim (do not duplicate) for search results** (lines 206-272, mobile cards + desktop table):
```tsx
{/* Mobile: cards empilhados */}
<div className="md:hidden divide-y divide-slate-200 dark:divide-slate-800">
  {pareceres.data.map((s) => (
    <Link key={s.id} href={`/pareceres/${encodeURIComponent(s.id)}`} className="block p-4 hover:bg-slate-50 dark:hover:bg-slate-900/40 transition-colors">
      <div className="flex items-center justify-between gap-2">
        <span className="font-bold text-slate-900 dark:text-white">
          {clienteNomeById.get(s.clienteId) ?? s.clienteId}
        </span>
        <Badge variant={statusVariant(s.status)} className="rounded-none font-bold tracking-wide">
          {s.status}
        </Badge>
      </div>
      ...
    </Link>
  ))}
</div>

{/* Desktop: tabela */}
<div className="hidden md:block">
  <Table>...</Table>
</div>
```
UI-SPEC mandates this exact JSX be reused for whichever data source (`pareceres.data` vs. search results from `usePesquisarPareceres`) is active — swap the array source, not the row/card markup. A conditional at the top of the results block (`const rows = pesquisaSubmitted ? pesquisa.data : pareceres.data;` or similar) is the minimal-diff way to satisfy "no duplicated rendering logic."

**Distinct empty-state copy required for zero search results** (per UI-SPEC, contrast with the existing empty state at lines 198-204):
```tsx
// Existing (Phase 65, list empty state) — do not reuse for search:
<p className="font-medium text-slate-700 dark:text-slate-300">
  Nenhuma solicitação de parecer encontrada
</p>
<p className="mt-1">Ajuste os filtros ou aguarde a criação de novas solicitações.</p>

// New (search zero-result state), same container styling, different copy:
<p className="font-medium text-slate-700 dark:text-slate-300">
  Nenhum resultado encontrado
</p>
<p className="mt-1">
  Não foram encontrados pareceres para os critérios indicados. Tente ajustar o texto ou os filtros de pesquisa.
</p>
```

**Error state copy (distinct from list error, per UI-SPEC)** — reuse the same red-text block structure at lines 194-197 but with copy:
```tsx
Não foi possível concluir a pesquisa. Verifique a ligação e tente novamente.
```

**CardTitle rule (if a new Card/CardTitle is introduced for the search panel):** must carry `className="text-lg font-bold"` from the first commit — no existing `CardTitle` exists in this file today (the filter bar is a bare `Card`/`CardContent` with no `CardHeader`/`CardTitle`), so if the plan adds a header/title for the "Pesquisa Avançada" panel, this is a **new** element requiring the explicit class, not an existing one to preserve.

---

### `web/src/hooks/use-pareceres.ts` (hook, request-response)

**Analog:** `usePareceres` in the same file (lines 1-37, already in full context above).

**Filters type + search-builder pattern to replicate for the new endpoint** (lines 7-20):
```typescript
export type ParecerSolicitacoesListFilters = {
  clienteId?: string;
  advogadoId?: string;
  status?: string;
};

function buildParecerSearch(filters: ParecerSolicitacoesListFilters) {
  const sp = new URLSearchParams();
  if (filters.clienteId?.trim()) sp.set("clienteId", filters.clienteId.trim());
  if (filters.advogadoId?.trim()) sp.set("advogadoId", filters.advogadoId.trim());
  if (filters.status?.trim()) sp.set("status", filters.status.trim());
  const qs = sp.toString();
  return qs ? `?${qs}` : "";
}
```
The new `ParecerPesquisaFilters` type must add `texto`, `dataInicio`, `dataFim` fields (per CONTEXT.md's confirmed backend param names) and a distinct `buildParecerPesquisaSearch` (or extend the existing builder to accept the superset — but keep query-key namespaces separate per CONTEXT.md, so a separate builder function is the cleaner, lower-risk choice; do not mutate `ParecerSolicitacoesListFilters` itself since `usePareceres`/`page.tsx`'s existing simple filters depend on its current exact shape).

**Query hook pattern to replicate, with the two required deviations called out in CONTEXT.md** (lines 22-37):
```typescript
export function usePareceres(filters: ParecerSolicitacoesListFilters = {}) {
  const enabled = typeof window !== "undefined";
  const clienteId = filters.clienteId?.trim() ?? "";
  const advogadoId = filters.advogadoId?.trim() ?? "";
  const status = filters.status?.trim() ?? "";

  return useQuery({
    queryKey: ["pareceres", "list", clienteId, advogadoId, status],
    queryFn: () =>
      apiFetch<ParecerSolicitacao[]>(
        `/pareceres/solicitacoes${buildParecerSearch({ clienteId, advogadoId, status })}`,
      ),
    enabled,
    staleTime: 30_000,
  });
}
```
**Deviations required for `usePesquisarPareceres`** (both explicit CONTEXT.md decisions):
1. Query key must be `["pareceres", "pesquisa", ...]`, not `["pareceres", "list", ...]` — distinct namespace, no cache collision with `usePareceres`.
2. URL path is the top-level `/pareceres/pesquisa` (sibling to `/pareceres/solicitacoes`), NOT `/pareceres/solicitacoes/pesquisa` or any nested variant — confirmed from `ParecerController.pesquisarSolicitacoes` during Phase 65 research.
3. Two additional trimmed string params (`texto`) plus two date params (`dataInicio`, `dataFim` — sent as-is from the native `<input type="date">` value, which is already `YYYY-MM-DD`; backend accepts `LocalDateTime` per CONTEXT.md but the existing `financeiro` date-filter convention (lines 149-150 in `financeiro/page.tsx`) does no client-side date-to-datetime conversion, it just string-compares/passes through — follow that same "pass the raw date string" convention unless the backend explicitly rejects a bare date; do not introduce a new date-formatting utility for this single case).

**No mutation/invalidation needed** — `usePesquisarPareceres` is read-only (no `useMutation`, no `queryClient.invalidateQueries` calls), matching `usePareceres`'s shape exactly, not the shape of `useCreateParecer`/`useEntregarParecer` (lines 71-84, 146-162) which are irrelevant analogs for this hook.

**Type reuse:** the response type is `ParecerSolicitacao[]` — identical to `usePareceres`'s return type (same `import type { ... ParecerSolicitacao ... } from "@/types/pareceres"` already at the top of the file, line 5). No new type needed for the response shape, only for the filters input.

---

## Shared Patterns

### Draft-state + committed-filters submit convention
**Source:** `web/src/app/(dashboard)/pareceres/page.tsx` lines 56-92 (Phase 65, this same file) and mirrored in `web/src/app/(dashboard)/processos/page.tsx` lines 38-108.
**Apply to:** the new "Pesquisa Avançada" panel's `texto`/`clienteId`/`advogadoId`/`status`/`dataInicio`/`dataFim` fields — all draft state until the "Pesquisar" submit button is clicked (per UI-SPEC Interaction Contract), consistent with the codebase-wide convention and CONTEXT.md's explicit steer away from debounced live-search for this feature.

### Native date input, no library
**Source:** `web/src/app/(dashboard)/financeiro/page.tsx` lines 141-142, 237-255.
**Apply to:** `dataInicio`/`dataFim` fields in the new panel — plain controlled `<input type="date">`, no `date-fns`/`react-day-picker`/similar dependency introduced.

### Query hook shape (filters type + URLSearchParams builder + useQuery, no mutation)
**Source:** `usePareceres` in `web/src/hooks/use-pareceres.ts` lines 7-37.
**Apply to:** the new `usePesquisarPareceres` hook — same `enabled: typeof window !== "undefined"` guard, same `staleTime: 30_000`, same `apiFetch<T[]>` generic usage, distinct query key namespace and URL path per CONTEXT.md.

### Permission gate
**Source:** `web/src/app/(dashboard)/pareceres/page.tsx` lines 37-51 (`usePermissions()` + `permissions.can.view("pareceres")` + `AccessDeniedState`).
**Apply to:** no new gate needed — the existing outer `ParecerPage` wrapper already gates the whole route on `pareceres:view`; the new panel/hook are purely additive inside `ParecerPageContent`, which is already inside that gate.

## No Analog Found

None — both files have strong same-file/same-module analogs; no gap requiring RESEARCH.md fallback patterns.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/pareceres/`, `web/src/app/(dashboard)/agenda/`, `web/src/app/(dashboard)/processos/`, `web/src/app/(dashboard)/financeiro/`, `web/src/hooks/use-pareceres.ts`
**Files scanned:** `pareceres/page.tsx`, `use-pareceres.ts`, `agenda/page.tsx`, `agenda/novo/page.tsx`, `processos/page.tsx`, `financeiro/page.tsx` (full or targeted reads)
**Pattern extraction date:** 2026-07-01

**Correction to phase brief:** the CONTEXT.md/task brief's pointer to `agenda/page.tsx` "lines ~240, 250" for the native date-input pattern does not match current source — `agenda/page.tsx`'s list/calendar view has no `<input type="date">` at all (date range there is handled via `datetime-local` inputs in the separate `agenda/novo/page.tsx` create form, and via drag-drop in the calendar). The actual matching native-date-range-filter analog in this codebase is `web/src/app/(dashboard)/financeiro/page.tsx` (lines 141-142, 237-255), which is a **filter bar** (same use case as this phase) rather than a create-form field. Planner should reference `financeiro/page.tsx`, not `agenda/page.tsx`, for the date-range filter markup.
</content>
