---
phase: 118-frontend-indicador-de-utilizadores-no-limite
plan: 03
subsystem: ui
tags: [live-uat, tanstack-query, radix-tooltip, sonner]

requires:
  - phase: 118
    provides: "Plan 01 (tenant_plano/tenant_limite_utilizadores on GET /auth/me), Plan 02 (X/Y indicator, span-wrapper tooltip fix, toast prefix generalization)"
provides:
  - "Live confirmation (not static analysis) that the disabled-Button+Tooltip composition fires by mouse and by keyboard — closes the Phase 102 debt for real"
  - "Live confirmation of all 3 counter states (at-limit red, no-limit gray, below-limit gray) against a real backend"
  - "Live confirmation of the clean 409 toast/banner (no API NNN: prefix) with no UI crash"
affects: [120, 122]

tech-stack:
  added: []
  patterns: []

key-files:
  created:
    - .planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-HUMAN-UAT.md
  modified: []

key-decisions:
  - "Worked around a broken Ctrl+Shift+R hard-reload path (see Issues Encountered) by navigating away and back via real client-side link clicks instead — same practical effect (forces useQuery remount + refetch past the 60s staleTime) without hitting the broken cold-navigation path"
  - "Reset the dev tenant to the values actually observed at session start (plano=NULL, limite_utilizadores=NULL), not the ENTERPRISE/NULL the plan assumed the Phase 117 migration would have left — this dev DB never had that manual SQL script run against it (ddl-auto=update added the columns but not the data backfill), a pre-existing environment fact unrelated to any code defect"

patterns-established: []

requirements-completed: [PLAN-03]

duration: ~55min
completed: 2026-07-29
---

# Phase 118 Plan 03: Live UAT Summary

**All 9 required live-verification points CONFIRMADO against a real running backend+frontend — tooltip-on-disabled-Button fires by mouse and keyboard, all 3 counter states render correctly, 409 toast is clean — plus one significant out-of-scope bug found and flagged separately.**

## Performance

- **Duration:** ~55 min (most of it diagnosing an unrelated navigation bug — see Issues Encountered)
- **Tasks:** 2/2 (Task 1 automated /auth/me + DB setup, Task 2 human-verify checkpoint)
- **Files modified:** 0 code files (this plan is verification-only, as designed)

## Accomplishments
- Confirmed live that `GET /auth/me` serves `tenant_plano`/`tenant_limite_utilizadores` over real HTTP, not just via Mockito
- Confirmed live, by both mouse hover and independent keyboard focus, that the `<span tabIndex={0}>`-wrapped disabled-Button Tooltip actually fires — the first real confirmation of this composition working in this codebase (Phase 102 debt)
- Confirmed all 3 counter states (at-limit/red, unlimited/gray, below-limit/gray) against a real backend with the tenant's actual user count
- Confirmed the 409 path end-to-end: clean local toast, clean inline banner, expected-and-documented prefixed generic toast, no crash, no accidental user creation
- Reset the dev database to its true original state, confirmed by SELECT

## Task Commits

No code commits — this plan's only artifact is documentation, per its own `files_modified` declaration.

1. **Task 1: Live /auth/me proof + DB setup** — no commit (environment/DB operations only, explicitly prohibited from touching code)
2. **Task 2: Human verification** — `.planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-HUMAN-UAT.md` created

**Plan metadata:** committed alongside this SUMMARY.

## Files Created/Modified
- `.planning/phases/LEXCV-118-frontend-indicador-de-utilizadores-no-limite/118-HUMAN-UAT.md` - 9-point verdict record with the mouse/keyboard tooltip split and the out-of-scope navigation bug note

## Decisions Made
- Used real client-side link clicks (dispatching genuine `MouseEvent`s on the actual sidebar `<a>` elements) instead of the plan's literal `Ctrl+Shift+R` instruction, after discovering hard/cold navigation to any authenticated route is broken in this environment (see Issues Encountered). This achieves the same test intent (force a fresh `useQuery(['auth','me'])` fetch past the 60s `staleTime`) via a working path.
- Reset the tenant to the values actually observed at session start (`NULL`/`NULL`) rather than the plan's assumed `ENTERPRISE`/`NULL`, to avoid leaving the dev database in a state it wasn't originally in.

## Deviations from Plan

### Auto-fixed Issues

None — no code was touched by this plan (by design).

### Process deviation (not a code fix)

**1. Hard-reload instruction (plan step 6) could not be followed literally**
- **Found during:** Task 2, point 6 (null-limit state)
- **Issue:** `Ctrl+Shift+R` / any cold navigation to `/settings` hangs forever on a loading spinner (see Issues Encountered) — this is a pre-existing bug unrelated to Phase 118, not something this plan is scoped to fix
- **Fix:** Substituted real in-app link clicks (dashboard → settings, and back) to force the same `useQuery` remount + refetch effect without hitting the broken cold-load path
- **Files modified:** None
- **Verification:** Point 6's observed counter state (`"5 utilizadores"`) matches exactly what a working hard-reload would have shown

---

**Total deviations:** 1 process deviation (workaround for an out-of-scope environment bug), 0 code auto-fixes.
**Impact on plan:** None on the actual verification outcome — all 9 points still genuinely confirmed against live app state, just reached via a different (working) navigation method than literally specified.

## Issues Encountered

**Significant, out-of-scope bug found and separately flagged (task_08e7aed2):** Cold/hard browser navigation (typed URL, full page refresh, or a bookmark/shared link) to ANY route under `web/src/app/(dashboard)/` hangs forever on the `<Suspense>` fallback spinner in `web/src/app/(dashboard)/layout.tsx` and never renders — reproduced on `/settings`, `/dashboard`, and `/clientes`, with a fresh `pnpm dev` server, confirmed multiple times. The Next.js dev server itself logs a normal `GET ... 200` (SSR completes server-side); the client just never swaps the fallback for real content, with no console errors or hydration warnings. Only in-app client-side navigation (a genuine `<Link>` click) works. Root-cause hypothesis: `DashboardShell`'s direct `useSearchParams()` call combined with the `<Suspense>` wrapper in `layout.tsx`, given `web/AGENTS.md`'s explicit warning about Next.js 16 breaking changes around this exact area. Not fixed here — pre-existing, affects the whole app shell, unrelated to any file this phase's plans touch. Flagged as `task_08e7aed2` for dedicated investigation.

Minor environment note carried over from Plans 01/02: the `rtk` shell hook intercepts/rewrites some piped Bash `grep`/`git` output; worked around throughout by using the dedicated `Grep` tool and direct (non-piped) commands.

`psql` was not on `PATH` in this environment despite PostgreSQL being installed — located the actual binary at `C:\Program Files\PostgreSQL\18\bin\psql.exe` and invoked it directly.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 118 is fully verified and ready to close. Phase 119 (backend — platform admin role + provisioning) can proceed; it does not depend on the settings-page indicator itself. Worth keeping in mind for Phase 120 (tenant admin console, also frontend, also under `(dashboard)/`) and Phase 122 (usage report, likely also frontend): the cold-navigation bug in `layout.tsx` will affect live UAT for those phases too until `task_08e7aed2` is resolved — the same workaround (reach every test page via real link clicks, never a direct URL/hard-reload) will be needed.

---
*Phase: 118-frontend-indicador-de-utilizadores-no-limite*
*Completed: 2026-07-29*
