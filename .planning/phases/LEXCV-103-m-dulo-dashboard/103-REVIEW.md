---
phase: LEXCV-103-m-dulo-dashboard
reviewed: 2026-07-16T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/dashboard/page.tsx
findings:
  critical: 0
  warning: 1
  info: 7
  total: 8
status: issues_found
---

# Phase LEXCV-103: Code Review Report

**Reviewed:** 2026-07-16
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Re-review after the fix pass recorded in `103-REVIEW-FIX.md` (commits `575acbf`, `36968bf`, `d4d3be8`, `99e0e58`). All four in-scope findings from the prior `103-REVIEW.md` (CR-01, WR-01, WR-02, WR-03) were re-read directly in the current file, cross-referenced against `use-me.ts`, `use-permissions.ts`, `use-dashboard-kpis.ts`, `use-eventos.ts`, `use-processos.ts`, `use-clientes.ts`, and `components/ui/badge.tsx`, and confirmed against `git show` for each fix commit. `npx tsc --noEmit` and `npx eslint` were run against the file: no errors specific to this file (the only `tsc` errors in the project are pre-existing missing-`vitest`-types errors in unrelated `*.test.ts` files, not this page).

**Fix verification (all 4 confirmed correct):**

1. **CR-01 — fixed.** Line 70 now reads `if (permissions.isFetched && !canViewAny)`. `usePermissions()` spreads the full `useMe()` query result (`use-permissions.ts:24-28`), so `isFetched` is available and — unlike `isLoading` — is not conflated with "query currently disabled" for `useMe()`'s `enabled: typeof window !== "undefined"` gate (`use-me.ts:8`). Traced SSR/first-paint behavior: on the server and on the very first client render, `enabled` is `false` and the query has never settled, so `isFetched` is `false` on both passes — the gate does not fire and the same (empty-but-not-"denied") shell renders on both server and client, so there's no hydration mismatch either. Once the `/auth/me` query resolves, `isFetched` flips to `true` and the gate now evaluates against a real `canViewAny`. Confirmed no more false "Acesso negado" flash for permitted users, matching the reported live-browser verification.
2. **WR-01 — fixed.** `.isError` branches were added and are correctly ordered *before* the "settled but zero data" branches in all three components: `DashboardKpis` (lines 230-237, checked before `kpis.isLoading` at 239), `PrazosUrgentesCard` (lines 448-451, checked before `urgentes.isLoading` at 452), and `RecentProcessosCard` (new `isError` prop, lines 505/515/533-536, checked before the empty-state branch at 537). `RecentProcessosCardWithClientes` passes `isError={processos.isError || clientes.isError}` (line 484) and `RecentProcessosCardNoClientes` passes `isError={processos.isError}` (line 497). Errors are no longer indistinguishable from "no data." See new WR-01/IN-02 below for two follow-on observations this introduced.
3. **WR-02 — fixed.** `isSameCalendarDay` (lines 397-403) plus `.slice().sort((a,b) => new Date(a.dataInicio).getTime() - new Date(b.dataInicio).getTime()).slice(0, 2)` (lines 407-410) replace the old unsorted `.slice(0, 2)`. The badge (lines 433-437) now renders `"HOJE"` only `isSameCalendarDay(dataInicio, new Date())`, else `dataInicio.toLocaleDateString("pt-CV", { day: "2-digit", month: "2-digit" })` — matches the reported live "20/06" output. See new IN-01 below for a timezone edge case this introduces.
4. **WR-03 — fixed.** Line 483: `isLoading={processos.isLoading || clientes.isLoading}` in `RecentProcessosCardWithClientes`. The name-lookup map can no longer render `"—"` for every row due to `clientes` still being in flight after `processos` resolved.

Tracing through the applied fixes surfaced one genuine new Warning-level trade-off (introduced by the WR-01 fix, not present before this fix pass) and a few Info-level observations. No Critical issues were found. The four Info items already known from the previous review (IN-01 through IN-04 there) remain unfixed, as expected — they were explicitly out of the fixer's `critical_warning` scope — and are carried forward below under new IDs for completeness.

## Warnings

### WR-01: Combining `processos` and `clientes` error states in `RecentProcessosCardWithClientes` hides otherwise-valid processos data when only the enrichment `clientes` query fails

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:472-487` (`RecentProcessosCardWithClientes`), `:533-536` (render)
**Issue:** The WR-01 fix (commit `36968bf`) added:
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
      isError={processos.isError || clientes.isError}
    />
  );
}
```
`isError` is an OR of two independent queries. If `processos` succeeds but `clientes` fails, the card now renders the fixed `"Não foi possível carregar os processos recentes."` message and shows **no table at all** — even though the processos list did load successfully. Before this fix pass, a `clientes` failure only degraded the "Cliente" column to `"—"` per row (via `clienteNomeById?.get(p.cliente_id) ?? "—"`, line 579) while the processos table itself still rendered. The fix (correctly) stops treating "clientes still loading" the same as "clientes failed," but by folding both queries into one boolean it also now suppresses a fully-successful, independently-useful `processos` fetch whenever the secondary enrichment query errors — a net loss of previously-visible data for a failure that doesn't actually affect the primary content.
**Fix:** Don't equate a secondary/enrichment-query failure with a primary-data failure. Gate the hard error state on `processos.isError` only, and degrade gracefully (e.g. a small inline note, or just `"—"` cells as before) when only `clientes.isError` is true:
```tsx
<RecentProcessosCard
  recentProcessos={recentProcessos}
  clienteNomeById={clienteNomeById}
  isLoading={processos.isLoading || clientes.isLoading}
  isError={processos.isError}
  // optionally surface clientes.isError as a non-blocking inline notice
/>
```

## Info

### IN-01: `isSameCalendarDay`'s "HOJE" boundary is computed in the browser's local timezone, not Cabo Verde's

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:397-403, 433-437`
**Issue:** `isSameCalendarDay(dataInicio, new Date())` compares `getFullYear`/`getMonth`/`getDate` in whatever timezone the *browser* is running in. LexCV is a Cape Verde platform (tenant timezone is presumably `Atlantic/Cape_Verde`, UTC−01:00); a user viewing the dashboard from a different timezone (e.g. Portugal, UTC±00:00/+01:00) could see an event mislabeled ("HOJE" vs. a date, or vice versa) right around local midnight, since "today" is evaluated against the viewer's clock rather than the tenant's. This risk didn't exist before WR-02's fix (the badge was a hardcoded literal), so it's a new — but low-severity, cosmetic-only — consideration introduced by this round.
**Fix:** If cross-timezone viewing is a real scenario for this tenant base, compute "today" in the tenant's fixed offset rather than the browser's local time (e.g. via `Intl.DateTimeFormat` with an explicit `timeZone`, or a shared date-utils helper) rather than raw `Date` getters. Low priority given LexCV's single-country (Cabo Verde) usage profile.

### IN-02: `RecentProcessosCard`'s new error branch shows a fixed generic string instead of the actual query error, inconsistent with its siblings

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:535` vs. `:234` and `:450`
**Issue:** `DashboardKpis` (line 234) and `PrazosUrgentesCard` (line 450) both render `kpis.error instanceof Error ? kpis.error.message : "..."` / `urgentes.error instanceof Error ? urgentes.error.message : "..."` — i.e. they surface the real backend/network error message when available. `RecentProcessosCard`'s new error branch (line 535) instead always renders the literal `"Não foi possível carregar os processos recentes."`, discarding both `processos.error` and `clientes.error` entirely. This is a minor inconsistency within the very fix that introduced all three error branches in the same commit.
**Fix:** Thread the error object(s) through and apply the same `instanceof Error ? error.message : fallback` pattern used by the other two components, e.g. pick `processos.error ?? clientes.error`.

### IN-03: `AtividadeRecenteCard`'s defensive empty branch is still unreachable (carried over, unchanged)

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:164, 187-192`
**Issue:** Unchanged from the previous review's IN-01. `ATIVIDADE_RECENTE_ENTRIES` is a fixed 3-item constant, so `entries.length === 0` can never be true. Confirmed intentional per `103-01-SUMMARY.md`/`103-01-PLAN.md`, deferred pending a real activity-feed backend. Out of this fix pass's scope (Info-only); no action expected now.
**Fix:** N/A — tracked tech debt; replace with live query data when a real activity-feed hook exists.

### IN-04: `AtividadeRecenteCard`'s loading skeleton is still keyed off the unrelated `useDashboardKpis` query (carried over, unchanged)

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:163, 175`
**Issue:** Unchanged from the previous review's IN-02. `const kpis = useDashboardKpis();` is called solely to read `kpis.isLoading` for a component whose actual content is a static array. Documented as an intentional stand-in per the phase plan.
**Fix:** Consider a one-line comment at the call site noting the borrowed-loading-state dependency, so future changes to `useDashboardKpis` don't silently change `AtividadeRecenteCard`'s behavior unnoticed.

### IN-05: KPI trend badges remain hardcoded, unrelated to the adjacent real figures (carried over, unchanged)

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:258-260, 278-280, 298-300, 318-320`
**Issue:** Unchanged from the previous review's IN-03. `"+12%"`, `"Estável"`, `"Urgente"`, `"+8%"` are literals unrelated to `kpis.data`. Pre-existing, deferred to `DASH-V2` per `REQUIREMENTS.md`.
**Fix:** Compute from real trend data when available, or replace with a neutral non-quantified label in the meantime.

### IN-06: Inconsistent JSX indentation in `RecentProcessosCard`'s final ternary branch (carried over, unchanged)

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:546, 601`
**Issue:** Unchanged from the previous review's IN-04. The `<CardContent className="p-0">` at line 546 (and its closing `)}` at line 601) sit one indent level shallower than the sibling branches directly above them (lines 534, 538, 544 are indented to 8 spaces; 546/601 sit at 6). Purely cosmetic.
**Fix:** Run the project formatter (`pnpm lint --fix` / Prettier) over this block.

### IN-07: `RecentProcessosCard`'s table has no dedicated loading skeleton — briefly renders header-only during fetch

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:533-601`
**Issue:** While `isLoading` is `true` and `isError` is `false`, `recentProcessos.length === 0 && !isLoading` evaluates to `false` (because `!isLoading` is `false`), so rendering falls through to the `<Table>` branch with an empty `<TableBody>` — the table headers ("ID Processo", "Cliente", "Estado", "Ação") render with no rows and no loading indicator until data arrives or the empty-state kicks in. This predates the current fix pass (the DASH-01 skeleton effort, per commit `c927156`, covered only the KPI cards and Atividade Recente, not this table) and was not part of any of the four target fixes, but it's a real, currently-observable gap in the same component this pass modified.
**Fix:** Add a `isLoading` branch (e.g. row-shaped `Skeleton` placeholders) before the zero-data / table branches, mirroring the pattern already used for `KpiCardSkeleton` and `AtividadeRecenteCard`.

---

_Reviewed: 2026-07-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
