---
phase: LEXCV-103-m-dulo-dashboard
reviewed: 2026-07-16T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/dashboard/page.tsx
findings:
  critical: 0
  warning: 0
  info: 7
  total: 7
status: issues_found
---

# Phase LEXCV-103: Code Review Report

**Reviewed:** 2026-07-16
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Third and final re-review, verifying the iteration-2 fix recorded in `103-REVIEW-FIX.md` (commit `4334986`) against the single Warning it targeted (`WR-01`: combining `processos` and `clientes` error states in `RecentProcessosCardWithClientes` hid an otherwise-successful processos table whenever only the secondary `clientes` enrichment query failed).

**Fix verification (confirmed correct):** `RecentProcessosCardWithClientes` (lines 472-487) now reads:
```tsx
function RecentProcessosCardWithClientes() {
  const processos = useProcessos();
  const clientes = useClientes({});

  const clienteNomeById = new Map((clientes.data ?? []).map((c) => [c.id, c.nome] as const));
  const recentProcessos = (processos.data ?? []).slice(0, 3);

  return (
    <RecentProcessosCard
      recentProcessos={recentProcessos}
      clienteNomeById={clienteNomeById}
      isLoading={processos.isLoading || clientes.isLoading}
      isError={processos.isError}
    />
  );
}
```
Line 484 is exactly the prescribed fix: `isError` is now gated on `processos.isError` alone, not `processos.isError || clientes.isError`. Confirmed by direct grep of the file that no reference to `clientes.isError` remains anywhere (only `processos.isError` appears at lines 484 and 497, for the with-clientes and no-clientes variants respectively). Traced the three relevant scenarios:
- `processos` fails → `isError=true` → hard error message renders regardless of `clientes` state (correct: primary data genuinely unavailable).
- `processos` succeeds, `clientes` fails → `isError=false`, `cliententeNomeById` is an empty `Map` (since `clientes.data` is `undefined` and `?? []` short-circuits) → table renders with real processos rows and every "Cliente" cell falls back to `"—"` via the pre-existing `clienteNomeById?.get(p.cliente_id) ?? "—"` (line 579) → graceful degradation for the Cliente column is restored, matching the pre-WR-01-v1 behavior the review asked for.
- `processos` succeeds, `clientes` succeeds → both real data render as expected.
`isLoading` was correctly left untouched (`processos.isLoading || clientes.isLoading`, from the WR-03 fix) since that combination was not part of this finding and is still needed to avoid a false "—" flash while `clientes` is still in flight after `processos` resolves. `clientes` remains referenced by both `clienteNomeById` and `isLoading`, so no unused-variable regression was introduced.

`RecentProcessosCardNoClientes` (lines 489-500) was untouched by this fix (it never referenced `clientes` in the first place) and still correctly passes `isError={processos.isError}`.

Re-verified, independently of the prior review's own conclusions, that the earlier iteration-1 fixes (CR-01 at line 70, WR-02's `isSameCalendarDay` + sort at lines 397-410/433-437, WR-03's combined `isLoading` at line 483) are all still intact and unaffected by this latest change — a single-line diff that did not shift any surrounding line numbers, confirmed by cross-checking every previously-cited line reference (permissions gate at 70, KPI error/loading branches at 230/239, prazos error/loading branches at 448/452, table error/empty/loading branches at 533/537) against the current file content, all of which match exactly.

No new Critical or Warning issues were found from an independent adversarial pass over the whole file (permission gating, KPI rendering, prazos sorting/date-badge logic, table rendering, empty/loading/error branch ordering, `?? ` vs `||` usage for zero-vs-nullish KPI values, encodeURIComponent usage on all id-based hrefs, no dangerous patterns, no hardcoded secrets, no console/debugger artifacts). The 7 Info-level items already known from the prior review remain present, unchanged, at the same line numbers, and remain explicitly out of this fix pass's `critical_warning` scope per `103-REVIEW-FIX.md` iteration 2 — carried forward below for completeness.

## Info

### IN-01: `isSameCalendarDay`'s "HOJE" boundary is computed in the browser's local timezone, not Cabo Verde's

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:397-403, 433-437`
**Issue:** `isSameCalendarDay(dataInicio, new Date())` compares `getFullYear`/`getMonth`/`getDate` in whatever timezone the *browser* is running in, not the tenant's (Cabo Verde, UTC−01:00). A viewer in a different timezone could see an event mislabeled ("HOJE" vs. a date) right around local midnight.
**Fix:** If cross-timezone viewing is a real scenario, compute "today" in the tenant's fixed offset (e.g. via `Intl.DateTimeFormat` with an explicit `timeZone`) rather than raw `Date` getters. Low priority given LexCV's single-country usage profile.

### IN-02: `RecentProcessosCard`'s error branch shows a fixed generic string instead of the actual query error, inconsistent with its siblings

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:535` vs. `:234` and `:450`
**Issue:** `DashboardKpis` (line 234) and `PrazosUrgentesCard` (line 450) both render `error instanceof Error ? error.message : "..."`, surfacing the real backend/network error when available. `RecentProcessosCard`'s error branch (line 535) always renders the literal `"Não foi possível carregar os processos recentes."`, discarding `processos.error` entirely.
**Fix:** Thread `processos.error` through and apply the same `instanceof Error ? error.message : fallback` pattern used by the other two components.

### IN-03: `AtividadeRecenteCard`'s defensive empty branch is unreachable

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:164, 187-192`
**Issue:** `ATIVIDADE_RECENTE_ENTRIES` is a fixed 3-item constant, so `entries.length === 0` can never be true. Confirmed intentional per `103-01-SUMMARY.md`/`103-01-PLAN.md`, deferred pending a real activity-feed backend.
**Fix:** N/A — tracked tech debt; replace with live query data when a real activity-feed hook exists.

### IN-04: `AtividadeRecenteCard`'s loading skeleton is keyed off the unrelated `useDashboardKpis` query

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:163, 175`
**Issue:** `const kpis = useDashboardKpis();` is called solely to read `kpis.isLoading` for a component whose actual content is a static array. Documented as an intentional stand-in per the phase plan.
**Fix:** Consider a one-line comment at the call site noting the borrowed-loading-state dependency, so future changes to `useDashboardKpis` don't silently change `AtividadeRecenteCard`'s behavior unnoticed.

### IN-05: KPI trend badges remain hardcoded, unrelated to the adjacent real figures

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:258-260, 278-280, 298-300, 318-320`
**Issue:** `"+12%"`, `"Estável"`, `"Urgente"`, `"+8%"` are literals unrelated to `kpis.data`. Pre-existing, deferred to `DASH-V2` per `REQUIREMENTS.md`.
**Fix:** Compute from real trend data when available, or replace with a neutral non-quantified label in the meantime.

### IN-06: Inconsistent JSX indentation in `RecentProcessosCard`'s final ternary branch

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:546, 601`
**Issue:** The `<CardContent className="p-0">` at line 546 (and its closing `)}` at line 601) sit one indent level shallower than the sibling branches directly above them. Purely cosmetic.
**Fix:** Run the project formatter (`pnpm lint --fix` / Prettier) over this block.

### IN-07: `RecentProcessosCard`'s table has no dedicated loading skeleton — briefly renders header-only during fetch

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:533-601`
**Issue:** While `isLoading` is `true` and `isError` is `false`, `recentProcessos.length === 0 && !isLoading` evaluates to `false`, so rendering falls through to the `<Table>` branch with an empty `<TableBody>` — headers render with no rows and no loading indicator until data arrives. Pre-existing gap (the DASH-01 skeleton effort covered only KPI cards and Atividade Recente, not this table); not part of any of the fixed findings.
**Fix:** Add an `isLoading` branch (e.g. row-shaped `Skeleton` placeholders) before the zero-data / table branches, mirroring the pattern already used for `KpiCardSkeleton` and `AtividadeRecenteCard`.

---

_Reviewed: 2026-07-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
