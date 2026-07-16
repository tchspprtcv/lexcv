---
phase: LEXCV-103-m-dulo-dashboard
reviewed: 2026-07-16T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/dashboard/page.tsx
findings:
  critical: 1
  warning: 3
  info: 4
  total: 8
status: issues_found
---

# Phase LEXCV-103: Code Review Report

**Reviewed:** 2026-07-16
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Reviewed `web/src/app/(dashboard)/dashboard/page.tsx` (568 lines) against the phase-103 diff (`git diff cc56071..HEAD`) plus full-file context, cross-referencing `use-me.ts`, `use-permissions.ts`, `use-dashboard-kpis.ts`, `use-eventos.ts`, `use-processos.ts`, `components/ui/empty.tsx`, `components/ui/badge.tsx`, `dashboard-shell.tsx`, `access-denied-state.tsx`, and the phase planning artifacts (`103-CONTEXT.md`, `103-01-PLAN.md`, `103-01-SUMMARY.md`).

The four items the review was specifically asked to verify:
1. **KPI card `isLoading` wiring** — the skeleton/real-card branches are internally consistent (same `canView*` booleans drive both the skeleton count and the real card count), so there's no *skeleton-vs-real-card* count mismatch or KPI-grid layout shift. However, tracing the `isLoading` semantics up through `usePermissions()`/`useMe()` surfaced a materially worse pre-existing defect that this phase's new loading-state code now shares a code path with — see CR-01.
2. **RBAC-gated skeleton count vs RBAC-gated real KPI count** — confirmed correct; both are computed from the identical `[canViewClientes, canViewProcessos, canViewAgenda, canViewFinanceiro].filter(Boolean)` list in the same order (lines 223-228 vs 241-323).
3. **No leftover ad hoc "Sem urgências." (or similar) strings** — confirmed removed; grep across the file for `Sem urg`, `italic`, `Nenhum`, `Sem \w+\.` returns no matches.
4. **`EmptyTitle` className override applied consistently** — confirmed; there is a single shared `EmptyState` helper (lines 38-58) that is the only call site rendering `EmptyTitle`, and it always carries `className="text-sm font-semibold"`. All three consumers (Atividade Recente, Prazos Urgentes, Processos Recentes) route through it, so the override can't drift.

Beyond those four checks, tracing `usePermissions()` → `useMe()` uncovered a real, reproducible defect (CR-01) in the pre-existing top-of-page RBAC gate that this phase's changes did not introduce but do share the same `isLoading` signal family with. Three further Warnings and four Info items are documented below, some pre-existing and some directly tied to this phase's new `isLoading`/empty-state code.

## Critical Issues

### CR-01: `usePermissions().isLoading` is `false` while permissions are still unresolved on first render, causing a false "Acesso negado" flash on every dashboard load

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:70` (root cause chain: `web/src/hooks/use-permissions.ts:9`, `web/src/hooks/use-me.ts:8`)
**Issue:**
The gate at line 70 is:
```tsx
if (!permissions.isLoading && !canViewAny) {
  return <AccessDeniedState description="Não tem permissões suficientes para aceder ao dashboard." backHref="/login" backLabel="Ir para login" />;
}
```
`permissions` is `usePermissions()`, which spreads `useMe()` (`use-permissions.ts:24-28`), and `useMe()` gates its query with `const enabled = typeof window !== "undefined";` (`use-me.ts:8`).

On the server (SSR — this route is under `(dashboard)/layout.tsx`, a Server Component wrapping a `"use client"` `DashboardShell`/`DashboardPage`, so the client tree is still rendered to HTML on the first pass), `window` is undefined, so the `/auth/me` query is `enabled: false`. TanStack Query v5's `isLoading` is defined as `isPending && isFetching` (`@tanstack/query-core` `queryObserver.js:307-310`). For a disabled query that has never fetched: `status` is `"pending"` (`isPending = true`) but `fetchStatus` is `"idle"` (`isFetching = false`), so `isLoading = true && false = false`.

Consequently, on the very first render (SSR output, and the first client render before the query-mount effect fires a fetch):
- `permissions.isLoading` is `false` (even though `me.data` is `undefined` and permissions genuinely haven't loaded).
- `permissions = me.data?.permissions ?? []` is `[]`, so every `canView*` flag is `false` and `canViewAny` is `false`.
- `!permissions.isLoading && !canViewAny` evaluates to `true`.

The result: **every user who navigates to `/dashboard` (including admins with full permissions) sees the "Acesso negado" card with an "Ir para login" button flash on screen** before the real dashboard appears, because the code cannot distinguish "permissions confirmed empty" from "permissions not fetched yet." This is not a hypothetical edge case — it fires on every fresh page load / hard refresh, since the query is disabled precisely during the window where this check runs.

This predates phase 103 (the gate itself is unchanged in the reviewed diff), but it directly undermines the same `isLoading` contract that phase 103's new KPI/Atividade-Recente loading branches now also rely on, and it's the most severe thing this review verifies against the "isLoading wiring... doesn't cause a flash" ask.

**Fix:** Don't rely on `isLoading` (which is conflated with "disabled") to mean "we know the answer." Use a signal that only becomes true once the query has actually settled at least once, e.g. `isFetched`/`isSuccess`, or explicit `data !== undefined`:
```tsx
// permissions.isFetched is true once the /auth/me query has completed
// (success or error) at least once — unlike isLoading, it is NOT
// conflated with "query is currently disabled".
if (permissions.isFetched && !canViewAny) {
  return (
    <AccessDeniedState
      description="Não tem permissões suficientes para aceder ao dashboard."
      backHref="/login"
      backLabel="Ir para login"
    />
  );
}
```
The same fix should be considered for the analogous `kpis.isLoading` gate on `DashboardKpis` (line 230) and any other component gating on a disabled-by-default query's `isLoading`, since the same "disabled ⇒ isLoading=false" trap applies whenever `enabled: typeof window !== "undefined"` is used (it appears in every hook in `web/src/hooks/`).

## Warnings

### WR-01: Query errors are rendered identically to "no data" everywhere in this file

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:230-328` (`DashboardKpis`), `:388-429` (`PrazosUrgentesCard`), `:443-567` (`RecentProcessosCard*`)
**Issue:** None of the components check `.isError`. If `useDashboardKpis()`, `useEventos()`, or `useProcessos()`/`useClientes()` fail (network error, 500, expired session, etc.), `isLoading` becomes `false` while `.data` stays `undefined`. The UI then falls straight into the "settled, zero data" branches:
- `DashboardKpis` shows `"—"` for every KPI number (line 257, 277, 297, 317) — indistinguishable from "not yet answered."
- `PrazosUrgentesCard` renders the `EmptyState` "Sem prazos urgentes" (line 424-428) — telling the user there are no urgent deadlines when the real answer is "we don't know, the request failed."
- `RecentProcessosCard` renders `EmptyState` "Sem processos recentes" (line 500-507) for the same reason.

A user relying on "Prazos Urgentes" being empty to mean "nothing due" during an outage would incorrectly believe there's nothing pending.

**Fix:** Check `.isError` before the zero-data branch and render a distinct error affordance, e.g.:
```tsx
) : urgentes.isError ? (
  <p className="text-sm text-red-600">Não foi possível carregar os prazos. Tente novamente.</p>
) : urgentes.isLoading ? null : (
  <EmptyState icon={CalendarCheck} title="Sem prazos urgentes" description="Não há eventos urgentes nos próximos dias." />
)}
```

### WR-02: "Prazos Urgentes" doesn't sort or filter by actual urgency, and hardcodes a "HOJE" badge regardless of the event's real date

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:389-390, 411-413`
**Issue:**
```tsx
const urgentes = useEventos({ concluido: false });
const urgentEventos = (urgentes.data ?? []).slice(0, 2);
...
<Badge variant="red" className="rounded-none">HOJE</Badge>
```
`useEventos({ concluido: false })` fetches **all** non-completed events, unsorted, and the component simply takes whichever two happen to be first in the API's response order — not the two soonest by `dataInicio`. Every one of those two is then labeled "HOJE" (Today) unconditionally, even if `e.dataInicio` (rendered a few lines below at line 419) is weeks away. A card titled "Prazos Urgentes" can therefore show non-urgent events tagged as due today. (Pre-existing behavior, not touched by this phase's diff, but directly inside the component this phase's Empty-state work targeted.)
**Fix:** Sort by `dataInicio` ascending before slicing, and derive the badge from the actual date instead of a literal:
```tsx
const urgentEventos = (urgentes.data ?? [])
  .slice()
  .sort((a, b) => new Date(a.dataInicio).getTime() - new Date(b.dataInicio).getTime())
  .slice(0, 2);
// ...
<Badge variant="red" className="rounded-none">
  {isSameDay(new Date(e.dataInicio), new Date()) ? "HOJE" : formatShortDate(e.dataInicio)}
</Badge>
```

### WR-03: `RecentProcessosCardWithClientes`'s threaded `isLoading` only tracks the `processos` query, not `clientes`

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:443-457, 541-543`
**Issue:** This phase's diff added `isLoading={processos.isLoading}` to both wrapper components (a real, in-scope change). For `RecentProcessosCardWithClientes`, the name-lookup map is built from a *second*, independent query:
```tsx
function RecentProcessosCardWithClientes() {
  const processos = useProcessos();
  const clientes = useClientes({});
  const clienteNomeById = new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const));
  const recentProcessos = (processos.data ?? []).slice(0, 3);
  return (
    <RecentProcessosCard recentProcessos={recentProcessos} clienteNomeById={clienteNomeById} isLoading={processos.isLoading} />
  );
}
```
If `processos` resolves before `clientes` does, `isLoading` is already `false` and the table renders real rows — but `clienteNomeById` is still built from an empty map (`clientes.data` still `undefined`), so every row's "Cliente" cell shows `"—"` (line 542: `clienteNomeById?.get(p.cliente_id) ?? "—"`) — identical to the case where a `cliente_id` genuinely has no match. This is a transient, but user-visible, false "no client found" state on every load where the two queries resolve out of order.
**Fix:**
```tsx
isLoading={processos.isLoading || clientes.isLoading}
```

## Info

### IN-01: `AtividadeRecenteCard`'s defensive empty branch is unreachable given current data

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:164, 187-192`
**Issue:** `const entries = ATIVIDADE_RECENTE_ENTRIES;` is a fixed 3-item compile-time constant (lines 135-160), so `entries.length === 0` (line 187) can never be true — the `EmptyState` branch it guards is dead code today. This is confirmed intentional in `103-01-SUMMARY.md` ("Atividade Recente defensive Empty branch (intentional dead code today)") and `103-01-PLAN.md` (line 215), deferred pending a real activity-feed backend (`DASH-V2`). No action required now; flagging only so the dead branch stays tracked and isn't mistaken for a bug fixed in a future refactor.
**Fix:** N/A — tracked as intentional tech debt. When a real activity-feed hook is introduced, replace `ATIVIDADE_RECENTE_ENTRIES` with live query data so this branch becomes reachable.

### IN-02: `AtividadeRecenteCard`'s loading skeleton is keyed off an unrelated query

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:163, 175`
**Issue:** `AtividadeRecenteCard` calls `const kpis = useDashboardKpis();` purely to read `kpis.isLoading` for its own skeleton-vs-content branch, even though its rendered content (`ATIVIDADE_RECENTE_ENTRIES`) is a static array with no data dependency at all. This is a deliberate, documented choice (`103-01-PLAN.md`/`103-01-SUMMARY.md`: "reuse an existing TanStack Query hook's isLoading... instead of inventing a backend/hook") to avoid inventing a stub endpoint, and it is genuinely free (shared query cache/key). It does mean Atividade Recente's skeleton duration is entirely a function of how long the *KPI* endpoint takes to answer, with no code-level indication of that coupling beyond the shared variable name.
**Fix:** Consider a one-line comment at the `const kpis = useDashboardKpis();` call in `AtividadeRecenteCard` noting that this is intentionally borrowing the KPI query's loading state as a stand-in until a real activity-feed hook exists, so a future change to `useDashboardKpis` (e.g. adding `suspense: true`, changing `staleTime`, or splitting the endpoint) doesn't silently change Atividade Recente's behavior without anyone noticing the dependency.

### IN-03: KPI trend badges are hardcoded and don't reflect the adjacent real figures

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:249-251, 269-271, 289-291, 309-311`
**Issue:** The `Badge` next to each KPI ("+12%", "Estável", "Urgente", "+8%") is a literal string, unrelated to `kpis.data`. E.g. the "Clientes Ativos" card could show "+12%" next to a `total_clientes` value that's actually flat or falling. Pre-existing, not part of this phase's diff.
**Fix:** Either compute the trend from real data (requires a backend delta field) or replace with a neutral, non-quantified label until real trend data exists, to avoid implying false analytics.

### IN-04: Inconsistent JSX indentation in `RecentProcessosCard`'s conditional

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:500-564`
**Issue:** The `else` branch's `<CardContent className="p-0">` (line 509) and its closing `)}` (line 564) are not indented to match the surrounding ternary, unlike the `if` branch above it. Purely cosmetic, no functional impact.
**Fix:** Run the project formatter (e.g. `pnpm lint --fix` / Prettier) over this block.

---

_Reviewed: 2026-07-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
