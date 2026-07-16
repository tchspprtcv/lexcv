---
phase: 103-m-dulo-dashboard
plan: 01
subsystem: ui
tags: [nextjs, react, shadcn, skeleton, empty, tanstack-query, dashboard]

# Dependency graph
requires:
  - phase: 101-fundacao
    provides: "Skeleton and Empty primitives installed in web/src/components/ui/ (unused until this phase)"
  - phase: 102-reconciliacao-design-system
    provides: "Card/CardContent/CardHeader reconciled onto --card token, safe surface for Skeleton/Empty to render inside"
provides:
  - "DashboardKpis reads useDashboardKpis().isLoading and renders RBAC-count-matched KpiCardSkeleton placeholders"
  - "AtividadeRecenteCard extracted from inline JSX; renders 3 Skeleton rows while loading, real entries otherwise, defensive Empty branch on zero entries"
  - "Local EmptyState helper (Empty/EmptyHeader/EmptyMedia/EmptyTitle/EmptyDescription) with mandatory EmptyTitle text-sm font-semibold override"
  - "Prazos Urgentes renders EmptyState (CalendarCheck) instead of ad hoc 'Sem urgências.' text on settled zero-data"
  - "Processos Recentes threads isLoading through both wrapper components; renders EmptyState (FolderOpen) instead of a blank TableBody on settled zero-data"
affects: [104-padrao-datatable-partilhado, other-module-phases-adopting-skeleton-empty]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Reuse an existing TanStack Query hook's isLoading a second time in a sibling component instead of prop-drilling or duplicating the query (shared cache by queryKey)"
    - "Local EmptyState wrapper component enforcing the mandatory EmptyTitle typography override at a single call site"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/dashboard/page.tsx"

key-decisions:
  - "Task split strictly followed the plan's Skeleton-first (Task 1) then Empty-second (Task 2) order so each task commit builds and typechecks standalone — the AtividadeRecenteCard's defensive Inbox/EmptyState branch was deliberately deferred out of the Task 1 commit into Task 2, since Empty wasn't imported yet"
  - "AtividadeRecenteCard's 3 hardcoded entries represented as a typed array (ATIVIDADE_RECENTE_ENTRIES) rather than left as duplicated JSX, so the Skeleton-row count (3) and the future defensive Empty branch share one render path"
  - "ReactNode imported as a type-only import from 'react' (no React namespace import needed under the jsx: react-jsx transform already configured in tsconfig.json)"

requirements-completed: [DASH-01, DASH-02]

# Metrics
duration: ~10min
completed: 2026-07-16
---

# Phase 103 Plan 01: Módulo Dashboard — Skeleton + Empty States Summary

**Wired the already-installed `Skeleton`/`Empty` primitives into the institutional Dashboard's KPI cards, Atividade Recente, Prazos Urgentes, and Processos Recentes — replacing a discarded `.isLoading` field and one ad hoc "Sem urgências." string with the official loading/zero-data primitives.**

## Performance

- **Duration:** ~10 min
- **Completed:** 2026-07-16
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- `DashboardKpis` now reads `useDashboardKpis().isLoading` (previously discarded) and renders one `KpiCardSkeleton` per RBAC-visible KPI while loading; unchanged `?? "—"` fallback remains the settled zero/error state.
- The 100%-hardcoded inline "Atividade Recente" `<Card>` JSX was extracted into `AtividadeRecenteCard`, now sharing `useDashboardKpis()`'s cache to show 3 Skeleton rows during load with zero extra network requests.
- `PrazosUrgentesCard`'s ad hoc `"Sem urgências."` italic string is fully removed, replaced by the `Empty` primitive (icon `CalendarCheck`) on settled zero-data, suppressed (renders `null`) while its own query is in flight.
- `RecentProcessosCard`'s previously-blank `<TableBody>` on zero rows now renders the `Empty` primitive (icon `FolderOpen`) instead, gated by a newly-threaded `isLoading` prop from both wrapper components so no Empty flash occurs during initial load.
- `AtividadeRecenteCard` carries a defensive (expected-unreachable-today) `Empty` branch (icon `Inbox`) for correctness, with no backend/hook invented — matching the `DASH-V2` deferred pattern.
- Every `EmptyTitle` instance goes through the local `EmptyState` helper's mandatory `className="text-sm font-semibold"` override (14px/600), never the shipped `text-lg font-medium` default.

## Verified-against-source correction (carried from 103-UI-SPEC.md)

The literal string **"A carregar..."** never existed in this file (it only lives in the separate, out-of-scope `processos/dashboard/page.tsx`). The real gap DASH-01 closed was structural, not textual: `DashboardKpis` already called `useDashboardKpis()` but never read its `.isLoading` field — every KPI number fell back to a bare `"—"` em-dash, indistinguishable from a genuine zero. This plan added the missing `isLoading` branch; it did not remove any ad hoc loading text because none existed. The one concrete ad hoc string this plan did remove was `"Sem urgências."` on the Prazos Urgentes card (a real DASH-02 target, confirmed present in the source before this plan ran).

## Atividade Recente defensive Empty branch (intentional dead code today)

"Atividade Recente" remains 100% hardcoded static data (3 fixed entries, no query/hook backing it — confirmed no `atividade`/`recent` hook exists in `web/src/hooks/` and `DashboardKpis`'s type has no activity-feed field). Representing those 3 entries as a typed array (`ATIVIDADE_RECENTE_ENTRIES`) let the `entries.length === 0` branch render the `Inbox` `EmptyState` defensively, for correctness if the data source ever changes — but this branch is expected-unreachable under today's data flow. No backend endpoint or hook was invented to make it reachable; this explicitly matches the `DASH-V2` deferred-scope pattern already established for the "Status dos Processos" chart placeholder.

## `pnpm build` result

`pnpm build` (Next.js 16.2.6, Turbopack) passed cleanly after both tasks — "Compiled successfully", TypeScript finished with zero errors, all 24 routes (including `/dashboard`) generated. `pnpm lint` was also run as an extra check: 6 errors / 17 warnings reported, all in files unrelated to this plan (`dashboard-shell.tsx`, `clientes/[id]/page.tsx`, `documentos/novo/page.tsx`, etc.) — `dashboard/page.tsx` itself has zero lint issues, confirming this plan introduced no new lint debt.

## Task Commits

Each task was committed atomically:

1. **Task 1: DASH-01 — Skeleton loading states for KPI cards + Atividade Recente** - `c927156` (feat)
2. **Task 2: DASH-02 — Empty zero-data states for Prazos Urgentes, Processos Recentes, and defensive Atividade Recente** - `70c3b50` (feat)

_No TDD tasks this plan (type="auto", tdd not set) — single feat commit per task, no test→feat→refactor cycle._

## Files Created/Modified
- `web/src/app/(dashboard)/dashboard/page.tsx` - `DashboardKpis` gains an `isLoading` branch + `KpiCardSkeleton`; inline "Atividade Recente" JSX extracted to `AtividadeRecenteCard` (Skeleton + defensive Empty); `PrazosUrgentesCard` empty branch swapped to `EmptyState`; `RecentProcessosCard` (+ both wrappers) threads `isLoading` and swaps its blank-table case to `EmptyState`; new local `EmptyState` helper wraps the `Empty` family with the mandatory `EmptyTitle` typography override

## Decisions Made
- Split the single-file edit strictly along the plan's Task 1 (Skeleton) / Task 2 (Empty) boundary so each task's commit is independently buildable — the `AtividadeRecenteCard` defensive `Inbox`/`EmptyState` branch was written but held out of the Task 1 diff (since `Empty` wasn't imported until Task 2), then added in Task 2 exactly where the plan specifies.
- Used a type-only `import type { ReactNode } from "react"` for the new `AtividadeRecenteEntry.icon` field rather than a `React.ReactNode` namespace reference, matching the project's `jsx: "react-jsx"` tsconfig (no global `React` namespace import required).

## Deviations from Plan

None — plan executed exactly as written. The one self-correction (moving the `AtividadeRecenteCard` defensive Empty branch out of the Task 1 edit and into Task 2) was a same-session in-progress fix before the Task 1 commit was made, not a deviation from the shipped plan — the final Task 1/Task 2 commit contents match the plan's task boundaries exactly.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. The `AtividadeRecenteCard`'s defensive Empty branch is documented dead code (see above), not a stub blocking the plan's own goal — DASH-01/DASH-02 are both fully satisfied for all four sections in scope (KPI cards, Atividade Recente, Prazos Urgentes, Processos Recentes).

## Threat Flags

None. This plan is purely presentational (Skeleton/Empty primitives on existing read-only data), touches no auth flow, no data-mutation path, no new endpoint/query, and no tenant-scoping logic — matching the plan's own threat-model disposition (all `accept`/`mitigate` items were about not disturbing existing RBAC gates and TanStack cache behavior, both confirmed unchanged by `pnpm build`'s clean typecheck).

## Next Phase Readiness

- DASH-01 and DASH-02 both complete; `Skeleton`/`Empty` primitives now have a first real consumer, validating the Phase 101/102 token layer (`bg-muted`, `--radius: 0rem`) on a low-risk surface as intended.
- "Status dos Processos" static chart placeholder remains untouched, confirmed out of scope (deferred `DASH-V2-01`).
- No blockers for Phase 104 (Padrão DataTable Partilhado) or any other module phase adopting these same primitives.

---
*Phase: 103-m-dulo-dashboard*
*Completed: 2026-07-16*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/dashboard/page.tsx`
- FOUND: `.planning/phases/LEXCV-103-m-dulo-dashboard/103-01-SUMMARY.md`
- FOUND: commit `c927156` (Task 1)
- FOUND: commit `70c3b50` (Task 2)
