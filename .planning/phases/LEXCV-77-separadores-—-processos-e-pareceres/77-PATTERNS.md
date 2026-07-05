# Phase 77: Separadores — Processos e Pareceres - Pattern Map

**Mapped:** 2026-07-05
**Files analyzed:** 1 (modified) + 2 (hooks, minimal-diff extension candidates)
**Analogs found:** 1 / 1 (plus 2 supporting hook analogs)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `web/src/app/(dashboard)/clientes/[id]/page.tsx` (modify: replace 2 `PlaceholderEmBreve` branches, lines 1055–1058) | component (tab content, request-response listing) | CRUD (read-only list) | `web/src/app/(dashboard)/processos/page.tsx` (Processos branch) and `web/src/app/(dashboard)/pareceres/page.tsx` (Pareceres branch) | exact (table markup + badge mapping copied verbatim) |
| `web/src/hooks/use-processos.ts` (`useProcessos`, potential minimal-diff edit) | hook | request-response | same file, `useProcesso`/`useProcessoPartes` etc. (internal `enabled` pattern) | exact — see "Lazy Fetch Decision" below |
| `web/src/hooks/use-pareceres.ts` (`usePareceres`, potential minimal-diff edit) | hook | request-response | same file, `useParecer`/`useParecerVersoes` (internal `enabled` pattern) | exact — see "Lazy Fetch Decision" below |

Only one file is created/modified as new UI surface (`clientes/[id]/page.tsx`); the hook files are analyzed here only insofar as the plan must decide whether to touch them (see below). No new files are introduced — this phase is pure content-fill into an existing tab shell (Phase 76).

---

## Lazy Fetch Decision (blocking design question resolved)

**Problem:** CONTEXT.md and UI-SPEC.md both require `enabled: tab === "processos"` / `enabled: tab === "pareceres"` semantics, but neither `useProcessos` nor `usePareceres` accepts an external `enabled` override today — both compute it internally:

`web/src/hooks/use-processos.ts` lines 130–131:
```typescript
export function useProcessos(filters: ProcessosListFilters = {}) {
  const enabled = typeof window !== "undefined" ;
  ...
  return useQuery({
    ...
    enabled,
    staleTime: 30_000,
  });
}
```

`web/src/hooks/use-pareceres.ts` lines 22–23:
```typescript
export function usePareceres(filters: ParecerSolicitacoesListFilters = {}) {
  const enabled = typeof window !== "undefined";
  ...
  return useQuery({
    ...
    enabled,
    staleTime: 30_000,
  });
}
```

**Recommended minimal-diff resolution: Option (a) — extend hook signatures with an optional second `options` argument, AND-combined into the existing internal `enabled` computation.** This is lower-risk than Option (b) (conditional sub-component mounting) because:
- It's a strict additive change (optional param, default `undefined` → behaves exactly as today for every existing caller — `/processos/page.tsx`, `/pareceres/page.tsx`, and any other consumer keep working unmodified).
- It keeps the "hook owns its query lifecycle" convention already used everywhere else in this codebase (every other `use*` hook in both files computes `enabled` internally; no existing hook in this codebase takes an external `enabled` override, but adding one is a well-understood React Query idiom and the least invasive way to satisfy the lazy-fetch requirement without restructuring the tab-content architecture established in Phase 76).
- Option (b) (mount a sub-component only when `tab === "processos"` so the hook call itself is skipped on other tabs) is architecturally viable too — `ClienteContactosCard`/`ClienteNotasCard` in the same file (lines 1036–1054) already demonstrate a similar "always call hook in parent, pass data down" split, but crucially those are **not** lazy — `contactos`/`notas` hooks fire unconditionally on mount regardless of active tab (see `ClienteDetailContent`, lines 111–116: `useClienteContactos(id)` / `useClienteNotas(id)` called unconditionally at the top). Replicating that pattern for Processos/Pareceres would violate the lazy-fetch requirement unless the hook call is pushed into a small sub-component that only mounts inside the `tab === "processos"` conditional branch (which JSX conditional rendering with `? ... : null` already achieves for free — React only calls hooks that are part of the currently-rendered tree).

**Concrete recommended implementation (both are valid; plan should pick one explicitly):**

- **Sub-component mount approach (zero hook-file changes, matches this file's existing conditional-branch idiom most closely):** Define `ClienteProcessosTab({ clienteId }: { clienteId: string })` and `ClienteParecerTab({ clienteId }: { clienteId: string })` as new local components in `clientes/[id]/page.tsx` (same pattern as `ClienteContactosCard`/`ClienteNotasCard`), each calling `useProcessos({ cliente_id: clienteId })` / `usePareceres({ clienteId })` internally. Because they are only rendered inside `tab === "processos" ? <ClienteProcessosTab clienteId={id} /> : ...`, React does not mount them (and therefore does not invoke their hooks / fire their queries) until that branch is active — this achieves true lazy fetch **without touching either hook file**, and is the minimal-diff choice consistent with "no backend touched, no unrelated hook API changes."
  - Caveat: once the tab has been visited once, the query stays in the TanStack Query cache (`staleTime: 30_000`) and unmounting/remounting the sub-component on tab switch will not immediately refetch (react-query keeps cached data), which matches the "avoid unnecessary calls" intent from CONTEXT.md.
- **Hook `enabled` override approach (touches both hook files, more explicit intent-signaling):** Add an optional third param, e.g. `useProcessos(filters: ProcessosListFilters = {}, options?: { enabled?: boolean })`, and change line 131 to `const enabled = (typeof window !== "undefined") && (options?.enabled ?? true);`. Mirror for `usePareceres`. This is a larger diff (touches 2 hook files instead of 0) but centralizes the lazy-fetch semantics in the hook itself rather than relying on mount/unmount side effects.

**Recommendation for the planner:** prefer the **sub-component mount approach** — it is strictly additive to `clientes/[id]/page.tsx` only, touches zero existing hook files (lowest blast radius, matches "reuse, don't invent" and CONTEXT.md's "sem qualquer alteração de backend" framing extended in spirit to hook contracts), and directly reuses the exact `ClienteContactosCard`/`ClienteNotasCard` sub-component idiom already established in this same file one tab over.

---

## Pattern Assignments

### `web/src/app/(dashboard)/clientes/[id]/page.tsx` — Processos tab branch (component, request-response)

**Analog:** `web/src/app/(dashboard)/processos/page.tsx`

**Imports pattern** (existing file already imports `Badge`, `Card`/`CardContent`, `Link`; only new imports needed):
```typescript
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useProcessos } from "@/hooks/use-processos";
```
(`Badge`, `Card`, `CardContent`, `Link`, `React` are already imported at the top of `clientes/[id]/page.tsx`, lines 4, 9, 11.)

**Estado badge variant mapping** (`web/src/app/(dashboard)/processos/page.tsx` lines 323–335) — copy verbatim:
```typescript
const estado = (p.estado ?? "").toUpperCase();
const estadoVariant =
  estado === "ATIVO"
    ? "green"
    : estado === "SUSPENSO"
      ? "amber"
      : estado === "TRIAGEM"
        ? "purple"
        : estado === "CONCLUIDO" || estado === "ENCERRADO"
          ? "gray"
          : "secondary";
const estadoLabel =
  estado === "TRIAGEM" ? "EM TRIAGEM" : (p.estado ?? "—");
```

**Table markup — compact 4-column version** (adapted from `web/src/app/(dashboard)/processos/page.tsx` lines 310–391, dropping Cliente/Tribunal/Ações columns per CONTEXT.md and UI-SPEC.md):
```typescript
<Table>
  <TableHeader className="bg-slate-50/50 dark:bg-slate-900/50">
    <TableRow className="border-b border-slate-200 dark:border-slate-800 hover:bg-transparent">
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">NÚMERO</TableHead>
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ESTADO</TableHead>
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ÁREA JURÍDICA</TableHead>
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">DATA DE INÍCIO</TableHead>
    </TableRow>
  </TableHeader>
  <TableBody>
    {processos.data.map((p) => {
      const estado = (p.estado ?? "").toUpperCase();
      const estadoVariant =
        estado === "ATIVO" ? "green"
        : estado === "SUSPENSO" ? "amber"
        : estado === "TRIAGEM" ? "purple"
        : estado === "CONCLUIDO" || estado === "ENCERRADO" ? "gray"
        : "secondary";
      const estadoLabel = estado === "TRIAGEM" ? "EM TRIAGEM" : (p.estado ?? "—");

      return (
        <TableRow key={p.id} className="border-slate-100 dark:border-slate-800/50 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors">
          <TableCell>
            <Link
              href={`/processos/${encodeURIComponent(p.id)}`}
              className="font-bold text-slate-900 dark:text-white hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
            >
              {p.numero || p.titulo || "Sem número"}
            </Link>
          </TableCell>
          <TableCell>
            <Badge variant={estadoVariant as "green" | "amber" | "gray" | "purple" | "secondary"} className="rounded-none font-bold tracking-wide">
              {estadoLabel}
            </Badge>
          </TableCell>
          <TableCell className="text-slate-500 dark:text-slate-400 font-medium">{p.area_juridica ?? "—"}</TableCell>
          <TableCell className="text-slate-500 dark:text-slate-400 font-medium">
            {p.data_inicio ? new Date(p.data_inicio).toLocaleDateString("pt-CV") : "—"}
          </TableCell>
        </TableRow>
      );
    })}
  </TableBody>
</Table>
```
Note: no `Área Jurídica` `Badge` (plain text per UI-SPEC.md's single-accent-per-row rule) — this deliberately diverges from the full `/processos` page's `<Badge variant="blue">` treatment of that column (line 359 there).

**Loading/error/empty states** (`web/src/app/(dashboard)/processos/page.tsx` lines 295–308, adapted copy per UI-SPEC.md Copywriting Contract):
```typescript
{processos.isLoading ? (
  <div className="p-6 text-sm text-slate-500">A carregar...</div>
) : processos.isError ? (
  <div className="p-6 text-sm text-red-600">
    {processos.error instanceof Error
      ? processos.error.message
      : "Não foi possível carregar os processos deste cliente."}
  </div>
) : !processos.data?.length ? (
  <div className="p-6 text-sm text-slate-500">Nenhum processo associado a este cliente.</div>
) : (
  <Table>...</Table>
)}
```

**Data source call** (per UI-SPEC.md — snake_case filter key, hook-level inconsistency, pre-existing, not in scope to fix):
```typescript
const processos = useProcessos({ cliente_id: clienteId });
```

---

### `web/src/app/(dashboard)/clientes/[id]/page.tsx` — Pareceres tab branch (component, request-response)

**Analog:** `web/src/app/(dashboard)/pareceres/page.tsx`

**Imports pattern** (new imports needed beyond what Processos branch already adds):
```typescript
import { usePareceres } from "@/hooks/use-pareceres";
import { useAdminUsers } from "@/hooks/use-admin";
```

**Status badge variant mapping — `statusVariant()` helper** (`web/src/app/(dashboard)/pareceres/page.tsx` lines 30–40) — copy verbatim:
```typescript
function statusVariant(status: ParecerStatus) {
  return status === "PENDENTE"
    ? "gray"
    : status === "EM_ELABORACAO"
      ? "blue"
      : status === "EM_REVISAO"
        ? "amber"
        : status === "CONCLUIDO"
          ? "green"
          : "secondary";
}
```
(Reuse this exact helper — either import it if exported, or redeclare identically in a local scope; it is currently a module-private function in `pareceres/page.tsx`, not exported, so the compact tab component will need its own copy or the function should be exported and imported — planner's call, minimal-diff favors a local copy to avoid touching `pareceres/page.tsx`.)

**Advogado Responsável name-resolution pattern** (`web/src/app/(dashboard)/pareceres/page.tsx` lines 78–86) — copy verbatim:
```typescript
const adminUsers = useAdminUsers();
const advogados = React.useMemo(
  () => (adminUsers.data ?? []).filter((u) => u.roles?.includes("ADVOGADO")),
  [adminUsers.data],
);
// Then build a lookup map, e.g.:
const advogadoNomeById = React.useMemo(
  () => new Map(advogados.map((u) => [u.id, u.nome] as const)),
  [advogados],
);
```
Check the exact field name for user's display name on the `MockUser`/admin-user type (`u.nome` vs `u.name`) before copying verbatim — confirm against `web/src/hooks/use-admin.ts` `MockUser` type.

**Table markup — compact 4-column version** (adapted from `web/src/app/(dashboard)/pareceres/page.tsx` lines 437–479, desktop `Table` block only — no `md:hidden` mobile card duplication per UI-SPEC.md §3):
```typescript
<Table>
  <TableHeader className="bg-slate-50/50 dark:bg-slate-900/50">
    <TableRow className="border-b border-slate-200 dark:border-slate-800 hover:bg-transparent">
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">NÚMERO/TÍTULO</TableHead>
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ESTADO</TableHead>
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">ADVOGADO RESPONSÁVEL</TableHead>
      <TableHead className="font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider text-[10px]">DATA DE CRIAÇÃO</TableHead>
    </TableRow>
  </TableHeader>
  <TableBody>
    {pareceres.data.map((s) => (
      <TableRow key={s.id} className="border-slate-100 dark:border-slate-800/50 hover:bg-slate-50/50 dark:hover:bg-slate-800/20 transition-colors">
        <TableCell className="font-bold text-slate-700 dark:text-slate-300">
          <Link
            href={`/pareceres/${encodeURIComponent(s.id)}`}
            className="hover:text-blue-600 dark:hover:text-blue-400 transition-colors"
          >
            {s.descricao}
          </Link>
        </TableCell>
        <TableCell>
          <Badge variant={statusVariant(s.status)} className="rounded-none font-bold tracking-wide">
            {s.status}
          </Badge>
        </TableCell>
        <TableCell className="text-slate-500 dark:text-slate-400 font-medium">
          {advogadoNomeById.get(s.advogadoId ?? "") ?? "—"}
        </TableCell>
        <TableCell className="text-slate-500 dark:text-slate-400 font-medium">
          {formatDate(s.createdAt)}
        </TableCell>
      </TableRow>
    ))}
  </TableBody>
</Table>
```

**`formatDate` helper** (`web/src/app/(dashboard)/pareceres/page.tsx` lines 23–28) — copy verbatim (module-private, not exported, redeclare locally or export+import):
```typescript
function formatDate(v: string | undefined) {
  if (!v) return "—";
  const d = new Date(v);
  if (Number.isNaN(d.getTime())) return v;
  return d.toLocaleDateString("pt-CV");
}
```

**Loading/error/empty states** (`web/src/app/(dashboard)/pareceres/page.tsx` lines 376–404, adapted copy per UI-SPEC.md):
```typescript
{pareceres.isLoading ? (
  <div className="p-6 text-sm text-slate-500">A carregar...</div>
) : pareceres.isError ? (
  <div className="p-6 text-sm text-red-600">Não foi possível carregar os pareceres deste cliente.</div>
) : !pareceres.data?.length ? (
  <div className="p-6 text-sm text-slate-500">Nenhum parecer associado a este cliente.</div>
) : (
  <Table>...</Table>
)}
```

**Data source call** (per UI-SPEC.md — camelCase filter key, as the hook already expects):
```typescript
const pareceres = usePareceres({ clienteId: clienteId });
```

---

## Shared Patterns

### Card wrapper for bare table (no page chrome)
**Source:** `web/src/app/(dashboard)/processos/page.tsx` line 294 (`<CardContent className="p-0 ...">`) and `web/src/app/(dashboard)/pareceres/page.tsx` line 375
**Apply to:** Both new tab branches — wrap the `Table` (or its loading/error/empty substitute) in a single `Card`/`CardContent className="p-0"` for consistent border/background, per UI-SPEC.md §1/§2. No search bar, no stat cards, no pagination — only the bare table block.

### Row-click navigation via styled `Link`
**Source:** `web/src/app/(dashboard)/processos/page.tsx` lines 340–345; `web/src/app/(dashboard)/pareceres/page.tsx` lines 458–463
**Apply to:** Both new tab branches — primary cell (Número for Processos, Número/Título for Pareceres) is a `Link` with `hover:text-blue-600 dark:hover:text-blue-400 transition-colors`, navigating to `/processos/${encodeURIComponent(id)}` / `/pareceres/${encodeURIComponent(id)}`. No separate `Ações`/`MoreVertical` column — row click IS the action per CONTEXT.md.

### Sub-component-per-tab pattern (existing precedent in same file)
**Source:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` — `ClienteContactosCard` (line 1355) and `ClienteNotasCard` (line 1557)
**Apply to:** Recommended structure for the new Processos/Pareceres tab content — define local components (`ClienteProcessosTab`, `ClienteParecerTab`) that own their own hook call, mirroring the existing sub-component style already used one tab over (contactos/notas), but — unlike those two — only mounted conditionally to satisfy the lazy-fetch requirement (see "Lazy Fetch Decision" above).

### Estado/Status badge color mapping (cross-cutting, do not invent new mapping)
**Source:** `web/src/app/(dashboard)/processos/page.tsx` lines 323–335 (Processos `estadoVariant`); `web/src/app/(dashboard)/pareceres/page.tsx` lines 30–40 (`statusVariant()`)
**Apply to:** Both new tab branches — reuse mappings exactly, no new colors, no third variant scheme.

---

## No Analog Found

None. Both target tab branches have a 1:1 source-of-truth analog (the full `/processos` and `/pareceres` list pages), and the lazy-fetch integration gap is resolved above via the existing sub-component idiom already present in the same file (`ClienteContactosCard`/`ClienteNotasCard`), requiring zero new architectural patterns.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/processos/page.tsx`, `web/src/app/(dashboard)/pareceres/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `web/src/hooks/use-processos.ts`, `web/src/hooks/use-pareceres.ts`, `web/src/hooks/use-admin.ts`, `web/src/types/pareceres.ts`, `web/src/types/processos.ts`
**Files scanned:** 8
**Pattern extraction date:** 2026-07-05
