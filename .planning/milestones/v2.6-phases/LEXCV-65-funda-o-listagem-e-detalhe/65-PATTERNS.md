# Phase 65: Fundação — Listagem e Detalhe - Pattern Map

**Mapped:** 2026-07-01
**Files analyzed:** 6 (list page, detail page, types, schemas, hooks, nav item modification)
**Analogs found:** 6 / 6

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `web/src/types/pareceres.ts` | model (types) | request-response | `web/src/types/processos.ts` | role-match (structure), but **do not** copy its dual-field style — see Anti-Pattern note below |
| `web/src/schemas/pareceres.ts` | utility (Zod schemas) | request-response | `web/src/schemas/processos.ts` | role-match |
| `web/src/hooks/use-pareceres.ts` | hook (TanStack Query) | CRUD (read-only subset this phase) | `web/src/hooks/use-processos.ts` (structure) + `web/src/hooks/use-documentos.ts` (no-normalization, presigned download) | exact (structure) / exact (camelCase pass-through + download pattern) |
| `web/src/app/(dashboard)/pareceres/page.tsx` | component (list page) | request-response | `web/src/app/(dashboard)/processos/page.tsx` | exact |
| `web/src/app/(dashboard)/pareceres/[id]/page.tsx` | component (detail page) | request-response | `web/src/app/(dashboard)/processos/[id]/page.tsx` | exact (tab pattern, timeline rendering, empty/error states) |
| `web/src/components/shared/dashboard-shell.tsx` (modified) | component (nav config) | N/A | same file, `NAV` array (lines 40-47) | exact |

## Pattern Assignments

### `web/src/types/pareceres.ts` (model, request-response)

**Analog:** `web/src/types/processos.ts` — but **only for file shape/export style**, not for field-naming strategy.

**Structure to copy** (lines 1-30 of `processos.ts` show the convention: union types first, then interfaces, then Create/Update request types):
```typescript
export type ParecerStatus = "PENDENTE" | "EM_ELABORACAO" | "EM_REVISAO" | "CONCLUIDO";
export type ParecerPrioridade = "ALTA" | "MEDIA" | "BAIXA"; // confirm exact backend enum values during planning

export interface ParecerSolicitacao {
  id: string;
  tenantId: string;
  clienteId: string;
  processoId?: string;
  advogadoId?: string;
  descricao?: string;
  prazo?: string;
  prioridade?: ParecerPrioridade;
  status: ParecerStatus;
  versaoFinalId?: string;
  createdAt: string;
}

export interface ParecerVersao {
  id: string;
  solicitacaoId: string;
  numeroVersao: number;
  conteudo?: string;
  caminhoAnexo?: string;
  criadoPorId: string;
  aprovado: boolean;
  aprovadoPorId?: string;
  aprovadoEm?: string;
  createdAt: string;
}
```

**CRITICAL — do not copy `ProcessoApi` dual-field pattern** (`processos.ts` lines 9-30 combined with `use-processos.ts` lines 28-101, the `ProcessoApi`/`normalizeProcesso` snake_case↔camelCase bridge). Per RESEARCH.md Anti-Pattern 1: `ParecerSolicitacao`/`ParecerVersao` have zero `@JsonProperty` overrides — types must be pure camelCase, 1:1 with the Java entity fields, no `snake_case` alternate keys.

---

### `web/src/schemas/pareceres.ts` (utility, request-response)

**Analog:** `web/src/schemas/processos.ts` lines 1-24 (top-of-file helper + first schema)

**Imports + reusable helper pattern** (lines 1-7):
```typescript
import { z } from "zod";

const optionalTrimmedString = z
  .string()
  .trim()
  .transform((v) => (v.length ? v : undefined))
  .optional();
```

**Read-only schemas needed this phase** (mirror the enum + interface export style, lines 34, 24 of `processos.ts`):
```typescript
export const parecerStatusSchema = z.enum(["PENDENTE", "EM_ELABORACAO", "EM_REVISAO", "CONCLUIDO"]);

export const parecerSolicitacaoSchema = z.object({
  id: z.string(),
  tenantId: z.string(),
  clienteId: z.string(),
  processoId: z.string().optional(),
  advogadoId: z.string().optional(),
  status: parecerStatusSchema,
  createdAt: z.string(),
  // ...remaining fields per types/pareceres.ts
});
export type ParecerSolicitacaoFormValues = z.infer<typeof parecerSolicitacaoSchema>;
```
Note: this phase is read-only, so full create/update schemas (mirroring `processoFormSchema`, lines 9-22) are deferred to Phase 66-67; only shape validation for read data is needed now, if any (list/detail pages can consume types directly without runtime Zod parsing, following the same lightweight approach `processos`/`documentos` list pages already use — they don't Zod-parse API responses, they trust `apiFetch<T>`'s generic).

---

### `web/src/hooks/use-pareceres.ts` (hook, CRUD — read subset)

**Analog 1 (query key / staleTime / enabled-guard conventions):** `web/src/hooks/use-processos.ts`

**Analog 2 (no normalization layer, direct camelCase pass-through, presigned download):** `web/src/hooks/use-documentos.ts`

**Imports pattern** (from `use-processos.ts` lines 1-4, `use-documentos.ts` lines 1-3):
```typescript
import { useQuery } from "@tanstack/react-query";

import { apiFetch } from "@/lib/api";

import type { ParecerSolicitacao, ParecerVersao } from "@/types/pareceres";
```

**List filters + search-builder pattern** (`use-processos.ts` lines 70-128, adapted — no snake_case, filters map directly to backend query params per CONTEXT.md):
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

**List + detail query pattern** (direct pass-through, no `normalizeX` — mirrors `use-documentos.ts` lines 20-43 exactly, NOT `use-processos.ts`'s `normalizeProcesso` wrapping):
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

export function useParecer(id: string) {
  const enabled = typeof window !== "undefined" && Boolean(id);

  return useQuery({
    queryKey: ["pareceres", "detail", id],
    queryFn: () => apiFetch<ParecerSolicitacao>(`/pareceres/solicitacoes/${encodeURIComponent(id)}`),
    enabled,
    staleTime: 30_000,
  });
}
```

**Nested-resource (versões) pattern** (mirrors `use-processos.ts`'s `useProcessoPartes`/`useProcessoFases`, lines 232-241):
```typescript
export function useParecerVersoes(solicitacaoId: string) {
  const enabled = typeof window !== "undefined" && Boolean(solicitacaoId);

  return useQuery({
    queryKey: ["pareceres", "versoes", solicitacaoId],
    queryFn: () =>
      apiFetch<ParecerVersao[]>(`/pareceres/solicitacoes/${encodeURIComponent(solicitacaoId)}/versoes`),
    enabled,
    staleTime: 15_000,
  });
}
```

**Anexo download (presigned URL, one-shot mutation)** — copy `useDownloadDocumento` verbatim shape (`use-documentos.ts` lines 87-94):
```typescript
export function useDownloadParecerAnexo(solicitacaoId: string, versaoId: string) {
  return useMutation({
    mutationFn: () =>
      apiFetch<{ url: string; expiresIn: number }>(
        `/pareceres/solicitacoes/${encodeURIComponent(solicitacaoId)}/versoes/${encodeURIComponent(versaoId)}/anexo`,
      ),
  });
}
```
(Requires adding `useMutation` to the import from `@tanstack/react-query` alongside `useQuery`.)

---

### `web/src/app/(dashboard)/pareceres/page.tsx` (component, request-response)

**Analog:** `web/src/app/(dashboard)/processos/page.tsx` (full file, 413 lines)

**Access-guard wrapper pattern** (lines 19-34):
```typescript
export default function ParecerPage() {
  const permissions = usePermissions();
  const canView = permissions.can.view("pareceres");

  if (!permissions.isLoading && !canView) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar o módulo de pareceres."
        backHref="/dashboard"
      />
    );
  }

  return <ParecerPageContent />;
}
```

**Filter-bar pattern** (lines 179-224, 244-290): debounced text `Input` + `<select>` dropdowns (status/advogado/cliente) inside a `Card`/`form onSubmit={onApply}`, draft-state + committed-filters split (`draftX` state vs `filters` state applied on submit, lines 39-108). Reuse this exact structure — status select instead of `estado` select (values `PENDENTE`/`EM_ELABORACAO`/`EM_REVISAO`/`CONCLUIDO` per UI-SPEC), advogado select populated from an ADVOGADO-role-filtered user list (check `useAdminUsers` from `use-admin.ts`, filter client-side by role), cliente select from `useClientes({})`.

**Table + status badge pattern** (lines 310-392): `<Table>` with `TableHeader`/`TableBody`, each row a `Link` to `/pareceres/${id}`, status badge via ternary mapping (`estadoVariant`, lines 323-333) — for Pareceres use the badge-variant map from UI-SPEC:
```typescript
const statusVariant =
  status === "PENDENTE" ? "gray"
  : status === "EM_ELABORACAO" ? "blue"
  : status === "EM_REVISAO" ? "amber"
  : status === "CONCLUIDO" ? "green"
  : "secondary";
```

**Dual-view (table/cards) requirement from CONTEXT.md:** `processos/page.tsx` as read does NOT show an explicit `hidden md:block`/`md:hidden` split in this exact file (it renders one `Table` unconditionally) — check `web/src/app/(dashboard)/clientes/page.tsx` or `web/src/app/(dashboard)/documentos/page.tsx` for the literal `hidden md:block` table + `md:hidden` card-list pair if CONTEXT.md's dual-view claim needs a second confirming analog; if absent there too, the responsive `Table` component (`components/ui/table.tsx`) may already handle overflow via horizontal scroll (`overflow-x-auto`) and a literal dual-view fork may not be a hard requirement — confirm at planning time by grepping `hidden md:block` across `web/src/app/(dashboard)/`.

**Loading/error/empty states** (lines 295-308):
```typescript
{isLoading ? (
  <div className="p-6 text-sm text-slate-500">A carregar...</div>
) : isError ? (
  <div className="p-6 text-sm text-red-600">
    Não foi possível carregar as solicitações. Verifique a ligação e tente novamente.
  </div>
) : !data?.length ? (
  <div className="p-6 text-sm text-slate-500">
    Nenhuma solicitação de parecer encontrada. Ajuste os filtros ou aguarde a criação de novas solicitações.
  </div>
) : ( /* table */ )}
```

**Page header pattern** (lines 110-131): `h1` with `text-3xl font-bold ... tracking-tight`, no CTA button this phase per UI-SPEC ("Primary CTA ... not applicable this phase").

---

### `web/src/app/(dashboard)/pareceres/[id]/page.tsx` (component, request-response)

**Analog:** `web/src/app/(dashboard)/processos/[id]/page.tsx` (full file, 1447 lines — read in full since access-guard, tab-state, and timeline-rendering sections are all needed)

**Access-guard + params pattern** (lines 89-140):
```typescript
type PageProps = { params: Promise<{ id: string }> };

export default function ParecerDetailPage({ params }: PageProps) {
  const { id } = React.use(params);
  const permissions = usePermissions();
  const canView = permissions.can.view("pareceres");

  if (!permissions.isLoading && !canView) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar este módulo de pareceres."
        backHref="/pareceres"
      />
    );
  }

  return <ParecerDetailContent id={id} />;
}
```

**Metadata `dl`/`dd` card pattern** (lines 442-495): `Card` > `CardHeader`/`CardTitle` "Dados" > `CardContent` > `dl className="grid grid-cols-3 gap-x-4 gap-y-3 text-sm"` with `dt`/`dd` pairs per field — copy directly for solicitação metadata (cliente, advogado, prazo, prioridade, status badge, createdAt via `formatDateTime`).

**`formatDateTime`/`formatDate` helpers** (lines 101-113) — copy verbatim, needed for `createdAt`/`aprovadoEm`.

**Tab-state pattern** (per CONTEXT.md: single "Versões" view this phase, no tabs needed yet, but if pre-scaffolding): `TabKey` type + `React.useState<TabKey>` + toggle `Button` row (lines 93, 142, 910-941) — `variant={tab === "x" ? "secondary" : "outline"}` toggle pattern.

**Version timeline rendering — closest structural analog is the Timeline tab** (lines 943-1200): dot+connector-line timeline with icon/color per item type, chronological ordering, actor name (`item.autorNome`), description text. Port this shape for `ParecerVersao` entries: dot color/icon can be a single fixed style (not type-branched, since all entries are the same type `versao`) — each entry shows `numeroVersao`, `criadoPorId` (resolved via `useAdminUsers`/`userNomeById` map, same as line 198), `createdAt` via `formatDateTime`, `conteudo`, and an anexo indicator/link.

**Anexo download link pattern** (adapt from `useDownloadDocumento` consumption — check `documentos/page.tsx` or `documentos/[id]/page.tsx` for the exact `mutateAsync().then(r => window.open(r.url))` call site during planning; not present in `processos/[id]/page.tsx`). Per UI-SPEC copy: "Descarregar anexo" (has `caminhoAnexo`) / "Sem anexo" (null).

**Empty-state pattern for zero versões** (mirrors lines 1110-1116 empty timeline state):
```typescript
<div className="py-12 text-center">
  <p className="text-sm font-medium text-slate-500">Nenhuma versão ainda</p>
  <p className="text-xs text-slate-400 mt-1">Aguarda elaboração pelo advogado atribuído.</p>
</div>
```

**Error state copy** (per UI-SPEC, mirrors line 1107-1109 exactly): "Não foi possível carregar as solicitações. Verifique a ligação e tente novamente." — reuse literally for both list and detail fetch failures.

**RBAC note:** Per CONTEXT.md, no action buttons (create version, entregar, etc.) in this phase — omit the `canEditProcessos`/`canManageProcessos`-gated buttons/dialogs pattern seen in `processos/[id]/page.tsx` (lines 416-420, 723-736, 897-908) entirely; this detail page is pure read-only render.

---

### `web/src/components/shared/dashboard-shell.tsx` (modified — nav item)

**Analog:** same file, `NAV` array (lines 40-47)

**Exact insertion pattern** (add after Financeiro, or in the module's natural position — confirm with icon convention from UI-SPEC: use `ScrollText` or `FileText`-family icon, `Scale`/`Users`/`Calendar`/`FileText`/`Wallet` already taken per-module):
```typescript
import { ScrollText } from "lucide-react"; // add to existing lucide-react import (line 7-20)

const NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: Home },
  { href: "/clientes", label: "Clientes", icon: Users, requiredPermission: "clientes:view" },
  { href: "/processos", label: "Processos", icon: Scale, requiredPermission: "processos:view" },
  { href: "/agenda", label: "Agenda", icon: Calendar, requiredPermission: "agenda:view" },
  { href: "/documentos", label: "Documentos", icon: FileText, requiredPermission: "documentos:view" },
  { href: "/financeiro", label: "Financeiro", icon: Wallet, requiredPermission: "financeiro:view" },
  { href: "/pareceres", label: "Pareceres", icon: ScrollText, requiredPermission: "pareceres:view" },
];
```
Note: `NAV.filter((item) => hasPermission(me.data?.permissions, item.requiredPermission))` (lines 86, 166) already handles gating both in the desktop `aside` and mobile `Sheet` drawer nav — a single array edit propagates to both render sites (this file renders the same `NAV` list twice, once per breakpoint variant; no separate mobile config exists). `BottomNav` (`components/shared/bottom-nav.tsx`, imported line 31) may have its own nav list — check it separately at planning/implementation time since it wasn't read in this pass (likely mirrors a subset of `NAV`, not full parity — verify before assuming Pareceres needs a bottom-nav slot).

---

## Shared Patterns

### Authentication / RBAC
**Source:** `web/src/lib/permissions.ts` (full file read — `pareceres` already in `KNOWN_SCOPES`, line 12) + `web/src/hooks/use-permissions.ts` (not read this pass, but `usePermissions().can.view/create/edit/manage(scope)` is the established call shape used identically in `processos/page.tsx` line 21-22 and `processos/[id]/page.tsx` line 125-127)
**Apply to:** Both `pareceres/page.tsx` and `pareceres/[id]/page.tsx` — gate entire page render behind `permissions.can.view("pareceres")`, show `AccessDeniedState` otherwise.
```typescript
const permissions = usePermissions();
const canView = permissions.can.view("pareceres");
if (!permissions.isLoading && !canView) {
  return <AccessDeniedState description="..." backHref="/dashboard" />;
}
```

### Error Handling
**Source:** consistent inline pattern across `processos/page.tsx` (lines 295-308) and `processos/[id]/page.tsx` (lines 424-439) — no centralized error boundary; each page/section renders `isPending`/`isError` states inline per-query, with `error instanceof Error ? error.message : "fallback copy"` cascades when multiple queries can fail simultaneously.
**Apply to:** All new list/detail render branches — use the exact Portuguese fallback copy specified in UI-SPEC's Copywriting Contract, not generic English fallbacks.

### Status Badge Convention
**Source:** `web/src/components/ui/badge.tsx` (full file) — `variant` prop: `blue`/`green`/`amber`/`red`/`purple`/`gray`/`secondary`/`outline`/`default`. Consuming pattern: `<Badge variant="green" className="rounded-none font-bold tracking-wide">{label}</Badge>` (e.g. `processos/page.tsx` line 362).
**Apply to:** Pareceres status badges per UI-SPEC's resolved mapping: `PENDENTE→gray`, `EM_ELABORACAO→blue`, `EM_REVISAO→amber`, `CONCLUIDO→green`.

### Query Client Invalidation / Key Namespace
**Source:** `web/src/hooks/use-processos.ts` query-key conventions (`["processos","list",...]`, `["processos","detail",id]`, `["processos","partes",id]` etc.) — nested-resource keys always include the parent id as a trailing array element.
**Apply to:** `["pareceres","list",...filters]`, `["pareceres","detail",id]`, `["pareceres","versoes",solicitacaoId]` — exactly as specified in RESEARCH.md's Data Flow section. This phase has no mutations, so no invalidation logic is needed yet, but the key shapes must match what Phases 66-68 will invalidate against.

## No Analog Found

None — this phase is a near-verbatim port of the `processos` module's read-only surface, and every file has a direct, strong analog in the existing codebase.

## Metadata

**Analog search scope:** `web/src/app/(dashboard)/processos/`, `web/src/hooks/use-processos.ts`, `web/src/hooks/use-documentos.ts`, `web/src/types/processos.ts`, `web/src/types/documentos.ts`, `web/src/schemas/processos.ts`, `web/src/components/shared/dashboard-shell.tsx`, `web/src/components/ui/badge.tsx`, `web/src/components/shared/access-denied-state.tsx`, `web/src/lib/permissions.ts`
**Files scanned:** 10 read in full, plus 1 targeted grep (`dashboard-shell.tsx`/`bottom-nav.tsx` for role-gating references)
**Pattern extraction date:** 2026-07-01

**Open follow-ups for planner:**
1. Confirm whether a literal `hidden md:block` / `md:hidden` dual-view fork exists in `clientes/page.tsx` or `documentos/page.tsx` (not confirmed in this pass since `processos/page.tsx` uses a single responsive `Table`) — CONTEXT.md asserts this pattern exists "identical to Clientes/Processos/Documentos," so verify against whichever file actually has it before implementation.
2. Check `web/src/hooks/use-admin.ts` (`useAdminUsers`) for a role filter to scope the advogado picker to ADVOGADO-role users only (per CONTEXT.md's filter requirement) — not read in this pass.
3. Check `web/src/components/shared/bottom-nav.tsx` to confirm whether it needs its own Pareceres entry alongside the `dashboard-shell.tsx` `NAV` array edit.
4. Confirm the literal anexo-download consumption call site (`mutateAsync` + `window.open`) in `documentos/[id]/page.tsx` or `documentos/page.tsx`, not present in `processos/[id]/page.tsx`.
</content>
