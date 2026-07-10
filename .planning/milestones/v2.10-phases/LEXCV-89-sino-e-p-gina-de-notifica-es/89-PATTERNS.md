# Phase 89: Sino e Página de Notificações - Pattern Map

**Mapped:** 2026-07-10
**Files analyzed:** 5 (4 new, 1 full-internals rewrite)
**Analogs found:** 5 / 5 (whole-file level) — 4 sub-mechanisms flagged as novel (see "Novel Patterns / No Local Analog")

This phase is 100% frontend (`web/`), consumes the Phase 86 `/notificacoes` API verbatim (see `89-UI-SPEC.md` API contract section — do not re-derive), and touches no backend file.

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `web/src/types/notificacoes.ts` | model | transform | `web/src/types/eventos.ts` / `web/src/types/pareceres.ts` | role-match (plain-camelCase entity shape); pagination wrapper shape has no analog |
| `web/src/lib/notificacao-categoria.ts` | utility | transform | `web/src/lib/prazos.ts` | exact (identical dual `toVariant`/`toLabel` map pattern) |
| `web/src/hooks/use-notificacoes.ts` | hook | CRUD | `web/src/hooks/use-pareceres.ts` (list) + `web/src/hooks/use-dashboard-kpis.ts` (count) + `web/src/hooks/use-processos.ts` (PATCH mutations) | role-match (composite); polling override has no analog |
| `web/src/app/(dashboard)/notificacoes/page.tsx` | route | CRUD | `web/src/app/(dashboard)/documentos/page.tsx` | role-match (page shell, RBAC gate, mobile-card list); pagination controls have no analog |
| `web/src/components/shared/notification-bell.tsx` | component | event-driven | itself (current version, in-place rewrite) | exact (Popover shell + null-link fallback pattern preserved; data source swapped) |

**Do not modify (explicitly locked by CONTEXT.md / UI-SPEC):** `web/src/components/shared/dashboard-shell.tsx` (NAV array, call site at line 281) and `web/src/components/shared/bottom-nav.tsx` (BOTTOM_NAV array) — no entry is added to either. Verified both files today; `NotificationBell` is already imported and rendered at `dashboard-shell.tsx:281` (`<NotificationBell />` between `<ThemeToggle />` and the divider) and needs no call-site change since the export name is preserved.

---

## Pattern Assignments

### `web/src/types/notificacoes.ts` (model, transform)

**Analog:** `web/src/types/eventos.ts` (whole file, 53 lines) and `web/src/types/pareceres.ts` (whole file, 31 lines)

**Plain-camelCase entity + enum-union pattern** (`web/src/types/eventos.ts:1-19`):
```typescript
export type EventoPrioridade = "BAIXA" | "MEDIA" | "ALTA";

export interface Evento {
  id: number;
  tenantId: string;
  processoId?: string;
  tipo?: string;
  titulo: string;
  descricao?: string;
  dataInicio: string;
  dataFim: string;
  prioridade: EventoPrioridade;
  concluido: boolean;
  ...
}
```
`Notificacao` follows the exact same shape convention: a string-literal union type for the enum field (`NotificacaoCategoria`), then a flat interface, all camelCase, no `snake_case` and no `normalize*` mapper function needed (verified directly against `Notificacao.java` per UI-SPEC — this is notable because `Processo`/`Cliente` types in this codebase always need a `normalize*` translation layer for their snake_case backend fields; `Notificacao` is the first entity type file that does **not**).

**Target shape (verbatim from `89-UI-SPEC.md` API contract — do not re-derive):**
```typescript
export type NotificacaoCategoria =
  | "FASE_ENTRADA" | "DOCUMENTO_NOVO" | "PROCESSO_ATRIBUIDO" | "PARECER_ATRIBUIDO"
  | "PRAZO_PROXIMO" | "PRAZO_VENCIDO" | "EVENTO_PROXIMO" | "EVENTO_VENCIDO" | "HONORARIO_ATRASADO";

export interface Notificacao {
  id: string;
  categoria: NotificacaoCategoria;
  entidadeTipo: string;
  entidadeId: string;
  titulo: string;
  mensagem: string;
  linkUrl: string | null;
  lida: boolean;
  createdAt: string;
}
```

**List filters type pattern** (`web/src/hooks/use-pareceres.ts:7-11`, for the sibling `NotificacoesListFilters` type — planner's choice of file, either here or co-located in the hook per existing precedent):
```typescript
export type ParecerSolicitacoesListFilters = {
  clienteId?: string;
  advogadoId?: string;
  status?: string;
};
```
`NotificacoesListFilters` should mirror this: `{ categoria?: NotificacaoCategoria; lida?: boolean; page?: number; size?: number }`.

**No analog for the paginated response wrapper.** No existing type file in `web/src/types/` models a `{ content, totalElements, totalPages, page, size }` shape (grepped `totalElements|totalPages|Pageable` across `web/src` — zero matches outside this phase's own UI-SPEC). Build `NotificacoesPageResponse` directly from the UI-SPEC's verified contract; there is nothing to copy structurally.

---

### `web/src/lib/notificacao-categoria.ts` (utility, transform)

**Analog:** `web/src/lib/prazos.ts` (whole file, 29 lines) — **exact match**, this is the closest possible precedent in the entire codebase: a lib file exporting two sibling functions, one mapping an enum to a `Badge` variant, one mapping the same enum to a PT label, both via `Record<Enum, T>` with a safe fallback.

**Full pattern to copy** (`web/src/lib/prazos.ts:1-29`):
```typescript
import type { PrazoRisco } from "@/types/processos";

/**
 * Fonte unica de verdade para o mapeamento de risco de prazo -> variant de badge.
 * Usada na lista de prazos no detalhe do processo e na listagem de processos.
 */
export function prazosRiscoToVariant(
  risco: PrazoRisco,
): "green" | "amber" | "red" {
  const map: Record<PrazoRisco, "green" | "amber" | "red"> = {
    ok: "green",
    proximo: "amber",
    vencido: "red",
  };
  return map[risco] ?? "amber";
}

/**
 * Fonte unica de verdade para o mapeamento de risco de prazo -> label de badge.
 * Usada na listagem de processos (apenas proximo/vencido renderizados).
 */
export function prazosRiscoToLabel(risco: PrazoRisco): string {
  const map: Record<PrazoRisco, string> = {
    ok: "PRAZO OK",
    proximo: "PRAZO PRÓXIMO",
    vencido: "PRAZO VENCIDO",
  };
  return map[risco] ?? "PRAZO PRÓXIMO";
}
```

`categoriaToBadgeVariant`/`categoriaToLabel` should follow this exact shape, with the 9-value `Record<NotificacaoCategoria, ...>` maps taken verbatim from the UI-SPEC's "Categoria Badge Color Map" and "Categoria → Label map" tables (both already fully specified — do not invent new copy).

**Secondary analog for the `OPTIONS` array export** (`NOTIFICACAO_CATEGORIA_OPTIONS: {value, label}[]`, needed for the `<select>`): `web/src/lib/cliente-documento-tipo.ts:14-28` shows the `{ value, label }[]` array-building convention:
```typescript
export interface DocumentoTipoOption {
  value: DocumentoTipo;
  label: string;
}
...
const OPTIONS_BY_TIPO: Record<ClienteTipo, DocumentoTipoOption[]> = {
  PARTICULAR: [
    { value: "CNI", label: "CNI" },
    { value: "BI", label: "BI" },
    { value: "PASSAPORTE", label: "Passaporte" },
  ],
  ...
};
```
`NOTIFICACAO_CATEGORIA_OPTIONS` is simpler (a flat array, not keyed by a second dimension) — derive it by mapping the 9 `NotificacaoCategoria` values through `categoriaToLabel`, one array literal, no per-type branching needed.

---

### `web/src/hooks/use-notificacoes.ts` (hook, CRUD)

**Analogs (composite — three source files, one per concern):**

**1. List query with filters → query key → `buildXxxSearch`** — `web/src/hooks/use-pareceres.ts:1-37` (full pattern):
```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch, API_BASE } from "@/lib/api";

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
`useNotificacoes(filters)` follows this exactly: `buildNotificacoesSearch` adds `categoria`, `lida` (only `if (filters.lida !== undefined)`, same guard style as `use-eventos.ts:30`), `page`, `size` to the `URLSearchParams`; query key becomes `["notificacoes", "list", categoria, lida, page, size]`; return type is `NotificacoesPageResponse`, not a bare array (only structural difference from every other list hook in this codebase — flag inline result access as `.content` in the page component).

**2. Simple count-shaped GET query** — `web/src/hooks/use-dashboard-kpis.ts:1-16` (whole file, for `useNotificacoesUnreadCount`):
```typescript
import { useQuery } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { DashboardKpis } from "@/types/dashboard";

export function useDashboardKpis() {
  const enabled = typeof window !== "undefined";

  return useQuery({
    queryKey: ["dashboard", "kpis"],
    queryFn: () => apiFetch<DashboardKpis>("/dashboard"),
    enabled,
    staleTime: 30_000,
  });
}
```
`useNotificacoesUnreadCount()` copies this shape 1:1 (`queryKey: ["notificacoes", "unread-count"]`, `apiFetch<{ count: number }>("/notificacoes/unread-count")`) — **but must add `refetchInterval: 30_000` and `refetchOnWindowFocus: true`**, which this analog does not have (see Novel Patterns below).

**3. Single-id PATCH mutation on a sub-resource, with cache write + prefix invalidation** — `web/src/hooks/use-processos.ts:759-783` (`useTogglePrazoConcluido`, the closest PATCH-shaped mutation in the codebase to `PATCH /notificacoes/{id}/lida`):
```typescript
export function useTogglePrazoConcluido(processoId: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (args: { prazoId: string; concluido: boolean }) =>
      apiFetch<Prazo>(
        `/processos/${encodeURIComponent(processoId)}/prazos/${encodeURIComponent(args.prazoId)}/concluido`,
        {
          method: "PATCH",
          body: JSON.stringify({ concluido: args.concluido }),
        },
      ),
    onSuccess: async (updated) => {
      queryClient.setQueryData<Prazo[] | undefined>(
        ["processos", "prazos", processoId],
        (current) => {
          if (!current) return current;
          return current.map((p) => (p.id === updated.id ? updated : p));
        },
      );
      // Invalidate list so risco_mais_critico and tem_prazo_escalonado badges refresh
      await queryClient.invalidateQueries({ queryKey: ["processos", "list"] });
    },
  });
}
```
`useMarcarNotificacaoLida()` mirrors this: `mutationFn: (id: string) => apiFetch<Notificacao>(\`/notificacoes/${encodeURIComponent(id)}/lida\`, { method: "PATCH" })` (no body — verified in UI-SPEC's API contract, unlike the `concluido` example which does send one), `onSuccess` does the **broad prefix invalidation** required by CONTEXT.md/UI-SPEC (see Shared Patterns → Query Invalidation below) instead of a narrow `setQueryData` — because the same id can appear in both the dropdown's 10-item query and the page's paginated query simultaneously, unlike `Prazo` which only ever lives in one list.

**4. Related PUT mutation for the "act on one id, then refresh everything downstream" shape** — `web/src/hooks/use-processos.ts:650-672` (`useReatribuirResponsavel`) is the UI-SPEC's own cited precedent for the error-copy convention (`"Não foi possível reatribuir..."`) and shows the same `invalidateQueries` + `setQueryData` combo at the call site.

`useMarcarTodasNotificacoesLidas()` has no body either (`POST /notificacoes/ler-todas`), same broad-invalidation `onSuccess`.

**Import block convention (copy verbatim style):**
```typescript
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiFetch } from "@/lib/api";
import type { Notificacao, NotificacaoCategoria, NotificacoesListFilters, NotificacoesPageResponse } from "@/types/notificacoes";
```

---

### `web/src/app/(dashboard)/notificacoes/page.tsx` (route, CRUD)

**Analog:** `web/src/app/(dashboard)/documentos/page.tsx` (whole file, 344 lines) — explicitly named in UI-SPEC as the structural template (adapted slate-not-neutral, and stacked-list-not-table per Known Gaps).

**RBAC gate pattern** (`web/src/app/(dashboard)/documentos/page.tsx:20-38`):
```typescript
export default function DocumentosPage() {
  const permissions = usePermissions();
  const canViewDocumentos = permissions.can.view("documentos");
  const canCreateDocumentos = permissions.can.create("documentos");
  const canEditDocumentos = permissions.can.edit("documentos");

  const form = useForm<DocumentosFiltersFormValues>({ ... });

  if (!permissions.isLoading && !canViewDocumentos) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar documentos."
        backHref="/dashboard"
      />
    );
  }

  return <DocumentosContent ... />;
}
```
`/notificacoes/page.tsx` copies this exactly, gating on `permissions.can.view("notificacoes")` with `<AccessDeniedState description="Não tem permissão para consultar notificações." backHref="/dashboard" />` (copy locked by UI-SPEC's Reuse Map) — no filter form needed here since filters are native `<select>`/chip buttons, not react-hook-form (see Known Gap note in UI-SPEC; drop the `useForm` part of this analog, keep the gate).

**Page header pattern** (`web/src/app/(dashboard)/documentos/page.tsx:70-84`):
```tsx
<div className="flex items-start justify-between gap-4">
  <div>
    <h1 className="text-2xl font-semibold">Documentos</h1>
    <p className="text-sm text-neutral-500 dark:text-neutral-400">
      Upload, consulta e gestão de documentos.
    </p>
  </div>
  {canCreateDocumentos ? ( <Button asChild><Link href="/documentos/novo">Upload</Link></Button> ) : null}
</div>
```
Copy structure, swap text for UI-SPEC's locked copy (`h1: "Notificações"`, subtitle: `"Histórico completo de alertas dos seus processos, documentos e pareceres."`), swap `neutral-*` for `slate-*` per UI-SPEC's color-family decision, and swap the create-button slot for the "Marcar todas como lidas" `Button variant="outline"` + `CheckCheck` icon.

**Loading / error / empty inline-state pattern** (`web/src/app/(dashboard)/documentos/page.tsx:126-136`, same convention independently confirmed in `web/src/app/(dashboard)/pareceres/page.tsx:376-404`):
```tsx
{list.isPending ? (
  <div className="text-sm text-neutral-500 dark:text-neutral-400">A carregar...</div>
) : list.isError ? (
  <div className="text-sm text-red-600">
    {list.error instanceof Error ? list.error.message : "Erro ao carregar"}
  </div>
) : !list.data?.length ? (
  <div className="text-sm text-neutral-500 dark:text-neutral-400">
    Nenhum documento encontrado.
  </div>
) : ( /* rows */ )}
```
No shared `<EmptyState>` component exists anywhere in `web/src/components/` (confirmed by search) — every page inlines its own empty/error copy exactly like this. `/notificacoes` follows the same `isPending`/`isError`/`!data?.content.length` ternary chain, swapping in UI-SPEC's locked copy strings for each of the 4 states (zero-ever, filtered-zero-match, error, loading).

**Stacked mobile-card row pattern (the row template for this phase, per UI-SPEC's "no `Table`" decision)** — `web/src/app/(dashboard)/documentos/page.tsx:195-275` (`DocumentoMobileCard`, full function):
```tsx
function DocumentoMobileCard({ id, nome, tipo, processoId, createdAt, canEditDocumentos }: {...}) {
  const del = useDeleteDocumento(id);
  const [error, setError] = React.useState<string | null>(null);
  ...
  return (
    <div className="p-4 space-y-2">
      <div className="flex items-start justify-between gap-2">
        <Link href={`/documentos/${encodeURIComponent(id)}`} className="font-bold text-slate-900 dark:text-white text-sm leading-tight hover:underline">
          {nome}
        </Link>
        {tipo && <Badge variant="blue" className="rounded-none font-bold text-[10px] flex-shrink-0">{tipo}</Badge>}
      </div>
      ...
      <div className="text-[11px] text-neutral-500 dark:text-neutral-400">
        Enviado: {createdAt ? new Date(createdAt).toLocaleDateString("pt-CV") : "—"}
      </div>
      ...
    </div>
  );
}
```
This is the direct template for each notification row: `p-4 space-y-2` container (matches UI-SPEC's declared `md` spacing token), title + `Badge` (categoria variant instead of `tipo`) in a `flex items-start justify-between` header row, meta line below using the exact same `toLocaleDateString("pt-CV", ...)` call. Divergence from this analog: rows are `divide-y divide-slate-100 dark:divide-slate-800` at **all** breakpoints (not `md:hidden`/`hidden md:block` forked), per UI-SPEC's explicit "stacked-list at all breakpoints" decision — do not fork a desktop `<table>` variant.

**Categoria `<select>` filter — exact className to copy verbatim** (`web/src/app/(dashboard)/processos/[id]/page.tsx:1200` and `:1213`, both `<select>` elements share this className; also confirmed at 8 further call sites across the same file and at `web/src/app/(dashboard)/processos/page.tsx:234` / `web/src/app/(dashboard)/pareceres/page.tsx:197`, so this is a well-established, not one-off, convention):
```tsx
<select
  className="h-10 w-full bg-white dark:bg-[#020617] rounded-none border border-slate-300 dark:border-slate-700 px-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
  value={categoria}
  onChange={(e) => { setCategoria(e.target.value); setPage(0); }}
>
  <option value="">Todas as categorias</option>
  {NOTIFICACAO_CATEGORIA_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
</select>
```
(`draftEstado`/`draftStatus` local-state-then-apply convention from `processos/page.tsx:40,86` and `pareceres/page.tsx:62,100` is the **filter-triggers-immediately** counter-example — those pages use a draft+"Aplicar" button flow; UI-SPEC's Interaction Flow for `/notificacoes` says "changing either filter resets page to 0 and refetches" immediately, no draft/apply step, so copy the `<select>` markup/className only, not the draft-state indirection.)

**Chip toggle button — exact active/inactive className to copy verbatim** (`web/src/app/(dashboard)/processos/[id]/page.tsx:1320-1341`, one of five near-identical chip buttons in the timeline tipo-filter; this one shown in full as the template):
```tsx
<button
  type="button"
  aria-pressed={selectedTipos.has("movimentacao")}
  className={
    selectedTipos.has("movimentacao")
      ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 rounded-none h-8 px-3 text-xs"
      : "border border-neutral-200 dark:border-neutral-800 bg-white dark:bg-transparent text-slate-700 dark:text-slate-300 rounded-none h-8 px-3 text-xs hover:bg-slate-50 dark:hover:bg-slate-800"
  }
  onClick={() => setSelectedTipos((prev) => { const next = new Set(prev); ...; return next; })}
>
  Movimentações
</button>
```
**Important divergence:** this analog is a **multi-select `Set`** (any number of chips can be active at once). UI-SPEC's lida/não-lida control is a **mutually-exclusive 3-way single-select** ("Todas" / "Não lidas" / "Lidas", mapping to `lida: undefined | false | true`) — copy only the two className strings (active/inactive) and the `aria-pressed`/`<button type="button">` markup shape; replace the `Set`-toggle `onClick` logic with a plain `setLidaFilter(value)` single-value setter (three buttons, each calling `setLidaFilter` with its own fixed value, active-check is `lidaFilter === value` not `selectedTipos.has(...)`).

**No analog for real pagination controls.** `web/src/app/(dashboard)/processos/page.tsx:399-406` has page-number buttons (`1`, `2`, `3`, `‹`, `›`) but they are **static decoration** — not wired to `useState`/query params (confirmed by reading the full file: no `page` state variable exists anywhere in it). There is no working "Anterior"/"Seguinte" + "Página X de Y" pattern anywhere in this codebase to copy. Build directly from UI-SPEC's Interaction Flow spec (`disabled={page === 0}` / `disabled={page + 1 >= totalPages}`, plain `useState<number>` for `page`), styled as `Button variant="outline"` per UI-SPEC's Copywriting Contract — this is new ground, not a gap in research.

---

### `web/src/components/shared/notification-bell.tsx` (component, event-driven — in-place rewrite)

**Analog:** itself, current version (whole file, 77 lines) — full rewrite of internals, **exported name, `Popover`/`PopoverTrigger`/`PopoverContent` shell, and bell-button-with-badge-circle markup are preserved unchanged**; only the data source and dropdown body content change.

**Current full file (what is being replaced/extended)** — `web/src/components/shared/notification-bell.tsx:1-77`:
```tsx
"use client";

import { Bell } from "lucide-react";
import Link from "next/link";

import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useUpcomingEventos } from "@/hooks/use-eventos";
import type { UpcomingEvento } from "@/types/eventos";

function NotificationItem({ ev }: { ev: UpcomingEvento }) {
  return (
    <>
      <p className="text-sm font-medium text-slate-900 dark:text-slate-100 truncate">{ev.titulo}</p>
      <p className="text-xs text-slate-500 dark:text-slate-400">
        {new Date(ev.dataInicio).toLocaleDateString("pt-PT", { day: "2-digit", month: "short" })}
        {ev.tipo ? ` · ${ev.tipo}` : ""}
      </p>
    </>
  );
}

export function NotificationBell() {
  const { data, isLoading } = useUpcomingEventos();
  const count = data?.length ?? 0;
  const showBadge = !isLoading && count > 0;

  return (
    <Popover>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" className="h-9 w-9 p-0 rounded-full text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors relative">
          <Bell className="h-[1.1rem] w-[1.1rem]" />
          {showBadge && (
            <span className="absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full bg-red-500 text-[10px] font-bold text-white flex items-center justify-center leading-none">
              {count > 9 ? "9+" : count}
            </span>
          )}
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-80 p-0" align="end">
        <div className="px-4 py-3 border-b border-slate-200 dark:border-slate-800">
          <p className="text-sm font-semibold text-slate-900 dark:text-slate-100">Próximos eventos</p>
        </div>
        {count === 0 ? (
          <p className="px-4 py-6 text-sm text-slate-500 dark:text-slate-400 text-center">Sem eventos nos próximos 7 dias</p>
        ) : (
          <ul className="divide-y divide-slate-100 dark:divide-slate-800 max-h-72 overflow-y-auto">
            {data!.slice(0, 10).map((ev) => (
              <li key={ev.id} className="px-4 py-3 hover:bg-slate-50 dark:hover:bg-slate-900 transition-colors">
                {ev.processoId ? (
                  <Link href={`/processos/${ev.processoId}`} className="block"><NotificationItem ev={ev} /></Link>
                ) : ( <NotificationItem ev={ev} /> )}
              </li>
            ))}
          </ul>
        )}
        <div className="px-4 py-2 border-t border-slate-200 dark:border-slate-800">
          <Link href="/agenda" className="text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium">Ver agenda</Link>
        </div>
      </PopoverContent>
    </Popover>
  );
}

export default NotificationBell;
```

**What to keep verbatim:** the `Popover`/`PopoverTrigger`/`PopoverContent className="w-80 p-0"` shell (line 44), the bell-button markup + badge-circle classNames (lines 31-41, including the `count > 9 ? "9+" : count` cap), the header/body/footer `<div>` border structure (`px-4 py-3 border-b`, `divide-y divide-slate-100 dark:divide-slate-800 max-h-72 overflow-y-auto`, `px-4 py-2 border-t`).

**What changes:**
- `useUpcomingEventos()` → `useNotificacoesUnreadCount()` (for the badge count) + `useNotificacoes({ size: 10 })` (for the dropdown body) — both need the `refetchInterval`/`refetchOnWindowFocus` override (see Novel Patterns).
- Header text `"Próximos eventos"` → `"Notificações"` + inline "Marcar todas como lidas" button (`Button variant="ghost" size="sm"`, disabled when `unreadCount === 0`).
- **The `ev.processoId ? <Link>... : <NotificationItem>` conditional-link fallback (lines 56-62) is the exact precedent for `linkUrl` null-handling** — UI-SPEC explicitly calls this out ("mirrors today's `ev.processoId ? <Link>... : <NotificationItem>` fallback pattern"). Copy this ternary shape 1:1, swapping `ev.processoId` for `n.linkUrl`, with the addition (new behavior, not in the analog) of a small inline `Check` affordance on the null-link branch so an unreachable row can still be marked read.
- Row click handler gains a `.mutate(id)` fire-and-forget call (new — the current version has no click-driven mutation at all, only navigation).
- Footer link text `"Ver agenda"` (`href="/agenda"`) → `"Ver todas as notificações"` (`href="/notificacoes"`), same `text-xs text-blue-600 dark:text-blue-400 hover:underline font-medium` className, per UI-SPEC.
- Empty copy `"Sem eventos nos próximos 7 dias"` → `"Sem notificações por agora."`.

---

## Shared Patterns

### RBAC gate (apply to `/notificacoes/page.tsx`)
**Source:** `web/src/hooks/use-permissions.ts` (whole file, 29 lines) + `web/src/components/shared/access-denied-state.tsx` (whole file, 38 lines)
```typescript
// use-permissions.ts
export function usePermissions() {
  const me = useMe();
  const permissions = useMemo(() => me.data?.permissions ?? [], [me.data?.permissions]);
  const can = useMemo(() => ({
    view: (scope: string) => hasScopedPermission(permissions, scope, "view"),
    create: (scope: string) => hasScopedPermission(permissions, scope, "create"),
    edit: (scope: string) => hasScopedPermission(permissions, scope, "edit"),
    manage: (scope: string) => hasScopedPermission(permissions, scope, "manage"),
    ...
  }), [permissions]);
  return { ...me, permissions, can };
}
```
```tsx
// access-denied-state.tsx usage
<AccessDeniedState description="Não tem permissão para consultar notificações." backHref="/dashboard" />
```
`notificacoes:view` is already seeded for all 4 profiles since Phase 86 — this gate is defense-in-depth, not expected to block anyone.

### Error handling / toast (apply to all mutations)
**Source:** `web/src/lib/api.ts:26-48` (auto-toast on non-401/403 failures, whole `apiFetch` function already handles this — no per-call try/catch needed just to get the toast) + `web/src/hooks/use-toast.ts:219-224`:
```typescript
toast.success = (message: React.ReactNode, options?: Partial<Toast>) =>
  toast({ title: "Sucesso", description: message, variant: "default", ...options });

toast.error = (message: React.ReactNode, options?: Partial<Toast>) =>
  toast({ title: "Erro", description: message, variant: "destructive", ...options });
```
Per UI-SPEC: mark-one has **no** success toast (silent, visual-only feedback); mark-all does call `toast.success("Todas as notificações foram marcadas como lidas.")` explicitly on top of the mutation's own `onSuccess`. Mutation failures need no bespoke inline copy — `apiFetch`'s automatic `toast.error` already covers it.

### Query invalidation — broad prefix match (apply to both mutations in `use-notificacoes.ts`)
**Source pattern:** every existing mutation hook (`use-eventos.ts`, `use-documentos.ts`, `use-processos.ts`) calls `queryClient.invalidateQueries({ queryKey: [...] })` with a 2-3 segment key (e.g. `["eventos", "list"]`, `["processos", "list"]`). TanStack Query's `invalidateQueries` does prefix matching by default — this phase's requirement ("contador do sino atualiza-se de imediato") needs a **single-segment** top-level key, `queryClient.invalidateQueries({ queryKey: ["notificacoes"] })`, which is the same mechanism at a broader prefix, not a new API. This is the first place in the codebase choosing the bare top-level prefix over a `"list"`/`"detail"`-suffixed one — deliberate, per UI-SPEC's cache-invalidation contract, because it must catch `["notificacoes","unread-count"]`, `["notificacoes","list",...]` (dropdown's 10-item query) and `["notificacoes","list",...]` (page's paginated query) all at once.

### Date formatting (apply to both the bell dropdown and the page rows)
**Source:** established convention, 15+ call sites, e.g. `web/src/app/(dashboard)/documentos/page.tsx:248` (`new Date(createdAt).toLocaleDateString("pt-CV")`), `web/src/app/(dashboard)/pareceres/page.tsx:27` (`d.toLocaleDateString("pt-CV")`). **No relative-time helper exists anywhere** — do not add one. Page rows use the fuller `toLocaleString("pt-CV", { day: "2-digit", month: "short", year: "numeric", hour: "2-digit", minute: "2-digit" })`; dropdown rows use the compact `toLocaleDateString("pt-CV", { day: "2-digit", month: "short" })` (matches the current bell's `"pt-PT"` locale exactly except UI-SPEC standardizes on `"pt-CV"` — a deliberate 1-locale-string fix, note it when rewriting `notification-bell.tsx`, whose current code uses `"pt-PT"` at line 16, inconsistent with the rest of the app).

### Label + Badge-variant map convention (apply to `notificacao-categoria.ts`)
**Source:** `web/src/lib/prazos.ts` (see full excerpt above) — this project's established single-source-of-truth convention for enum→display mappings, always exported as two sibling pure functions over a `Record<Enum, T>` literal with a safe `??` fallback to the "middle" or most-common value.

### Hook file conventions (apply to `use-notificacoes.ts`)
**Source:** every hook file in `web/src/hooks/*.ts` — `import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";` then `import { apiFetch } from "@/lib/api";`, a private `buildXxxSearch(filters)` function building a `URLSearchParams`, `enabled: typeof window !== "undefined"` guard on every query (SSR-safety), `staleTime` set explicitly (15-30s range across the codebase).

### Popover dropdown shell (do not modify structurally)
**Source:** `web/src/components/ui/popover.tsx` (whole file, 36 lines) — thin Radix wrapper, `PopoverContent` defaults `align="end" sideOffset={8}`; `notification-bell.tsx` overrides to `className="w-80 p-0"` — keep this override, it is what makes the dropdown a flush 320px panel instead of the padded default.

### Badge color variants (no new variant needed)
**Source:** `web/src/components/ui/badge.tsx:9-23` — `blue`, `green`, `amber`, `red`, `purple`, `gray` variants already exist and cover all 9 `categoria` values per UI-SPEC's color map (`blue`/`purple`/`amber`/`red` — `green`/`gray` unused by this phase but already available). No `Badge` component change required.

---

## Novel Patterns / No Local Analog

These sub-mechanisms have no precedent anywhere in the codebase — implement directly from `89-UI-SPEC.md`'s explicit spec rather than copying a local file:

| Mechanism | Where it's needed | Why no analog |
|---|---|---|
| `refetchInterval: 30_000` + `refetchOnWindowFocus: true` override | `useNotificacoesUnreadCount()`, dropdown's `useNotificacoes({ size: 10 })` | Confirmed by CONTEXT.md itself: "primeiro uso de `refetchInterval` no projeto". `web/src/app/providers.tsx:14` sets the global `refetchOnWindowFocus: false` default that must be overridden per-query — no existing `useQuery` call in this codebase sets either option today (grepped `refetchInterval\|refetchOnWindowFocus` outside `providers.tsx` and this phase's own docs — zero hits). Use TanStack Query's documented per-query override, nothing project-specific to copy. |
| Real pagination controls (`page`/`totalPages` state, "Anterior"/"Seguinte", disabled-at-boundary) | `/notificacoes/page.tsx` | `processos/page.tsx:399-406` has cosmetic-only numbered buttons wired to nothing. No functional pager exists anywhere in `web/src`. |
| `NotificacoesPageResponse` shape (`{content, totalElements, totalPages, page, size}`) | `web/src/types/notificacoes.ts` | No existing type file models a Spring `Pageable` JSON response — every other list endpoint in this app returns a bare array. |
| Mutually-exclusive 3-way single-select chip group (vs. this codebase's existing multi-select `Set`-based chips) | Lida/não-lida filter on `/notificacoes/page.tsx` | `processos/[id]/page.tsx`'s tipo-filter chips (the only chip-toggle precedent) are additive multi-select; reuse only the two className strings, not the `Set` toggle logic — see the dedicated divergence note in the Pattern Assignment above. |

---

## Metadata

**Analog search scope:** `web/src/hooks/`, `web/src/lib/`, `web/src/types/`, `web/src/app/(dashboard)/`, `web/src/components/shared/`, `web/src/components/ui/`
**Files scanned:** ~30 (all hook files, all lib files, all type files, `documentos`/`pareceres`/`processos`/`processos/[id]`/`processos/dashboard` pages, `dashboard-shell.tsx`, `bottom-nav.tsx`, `providers.tsx`, `access-denied-state.tsx`, `badge.tsx`/`popover.tsx`/`button.tsx`/`card.tsx`)
**Confirmed absent (grepped, zero matches in `web/src`):** `refetchInterval`, `refetchOnWindowFocus` (outside `providers.tsx`), `totalPages`/`totalElements`/`Pageable`, `aria-pressed` outside `processos/[id]/page.tsx`, any `components/shared/empty-state.tsx`-style shared component.
**Pattern extraction date:** 2026-07-10
