---
phase: 107-m-dulos-documentos-financeiro
plan: 02
subsystem: ui
tags: [react, shadcn, radix-ui, react-hook-form, rbac, next.js]

# Dependency graph
requires:
  - phase: 101-fundacao-shadcn
    provides: Progress and NativeSelect primitives (added to web/src/components/ui/)
  - phase: 103-modulo-dashboard
    provides: permissions.isFetched RBAC-race fix pattern (established fix shape)
provides:
  - documentos/novo upload form migrated to the official Progress bar (bg-primary, theme-aware)
  - documentos/novo confidencialidade field migrated to NativeSelect (4 fixed enum options)
  - documentos/novo and documentos/[id] RBAC gates closed against the isLoading render race
affects: [107-01, 107-03, 107-04, 107-05, 107-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "RBAC gate: permissions.isFetched && !canX (not !permissions.isLoading), submit-guard isLoading usages left untouched"
    - "Progress primitive replacing hand-rolled bg-blue-600 upload bars"
    - "NativeSelect (className=\"w-full\" mandatory) replacing raw <select> for fixed-enum fields"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/documentos/novo/page.tsx
    - web/src/app/(dashboard)/documentos/[id]/page.tsx

key-decisions:
  - "documentos/novo's tipo field stays a plain Input (locked exclusion, no processo/cliente context to source Combobox suggestions from) — confirmed untouched"

patterns-established:
  - "NativeSelect single-line JSX attribute layout for fixed-enum fields (matches plan's verify-script expectation of same-line NativeSelect id=... match)"

requirements-completed: [DOF-01, DOF-02]

# Metrics
duration: ~10min
completed: 2026-07-17
---

# Phase 107 Plan 02: Documentos standalone upload + detail — Progress/NativeSelect/RBAC Summary

**Migrated the standalone document-upload form to the official Progress bar and NativeSelect for confidencialidade, and closed the isFetched RBAC-race on both documentos/novo and documentos/[id] detail.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-07-17T00:29:00-01:00 (approx)
- **Completed:** 2026-07-17T00:30:48-01:00
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- `documentos/novo/page.tsx` upload progress bar now renders via `<Progress value={progresso ?? 0} />` (radix-ui-backed, `bg-primary`/`bg-muted`, theme-aware) instead of a hand-rolled `bg-blue-600` div
- `documentos/novo/page.tsx` `confidencialidade` field now renders as `<NativeSelect id="confidencialidade" size="default" className="w-full">` with the 4 fixed enum options (PUBLICO/INTERNO/CONFIDENCIAL/RESTRITO) unchanged
- RBAC render-race fix applied to both `documentos/novo` (line ~122) and `documentos/[id]` (line ~25): `permissions.isFetched && !canX` replacing `!permissions.isLoading && !canX`, matching the fix established in Phases 103/105/106
- Legitimate submit-guard use of `permissions.isLoading` (line ~261 of `documentos/novo/page.tsx`) left untouched, as required

## Task Commits

Each task was committed atomically:

1. **Task 1: documentos/novo — Progress bar + NativeSelect confidencialidade + isFetched gate** - `7b08f37` (feat)
2. **Task 2: documentos/[id] — isFetched RBAC gate (detail)** - `82080d9` (fix)

_No TDD tasks in this plan; all changes are UI-migration/RBAC-fix only, no new behavior tests required._

## Files Created/Modified
- `web/src/app/(dashboard)/documentos/novo/page.tsx` - Progress bar + NativeSelect confidencialidade + isFetched RBAC gate
- `web/src/app/(dashboard)/documentos/[id]/page.tsx` - isFetched RBAC gate (detail page, no other change)

## Decisions Made
- Wrote the migrated `NativeSelect` for `confidencialidade` as a single-line JSX opening tag (`<NativeSelect id="confidencialidade" size="default" className="w-full" {...form.register("confidencialidade")}>`) rather than the multi-line attribute layout used by the `processos/novo` precedent — this matches the plan's own verify script (`grep -q 'NativeSelect id="confidencialidade"'`, which requires both tokens on the same line) and the plan's literal single-line action-text example. Functionally identical either way; purely a formatting choice made to satisfy the plan's automated verification.

## Deviations from Plan

None - plan executed exactly as written. The only adjustment was a formatting choice (single-line vs. multi-line JSX attributes for the `NativeSelect` tag) needed to pass the plan's own grep-based verify script — not a functional deviation, no Rule 1/2/3/4 applicable (documented above under Decisions Made for transparency).

## Issues Encountered
- Initial multi-line `NativeSelect` JSX layout (mirroring the `processos/novo/page.tsx` precedent read during `read_first`) failed the plan's `grep -q 'NativeSelect id="confidencialidade"'` verify check, since the tag and its `id` attribute were split across lines. Reformatted to single-line per the plan's literal action-text example; re-ran verification, passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- DOF-01 (Progress) and DOF-02 (NativeSelect for `confidencialidade`) fully satisfied for this plan's scope (the standalone upload form).
- 2 of the 6 bundled RBAC `isFetched` gates now fixed (`documentos/novo:122`, `documentos/[id]:25`); the remaining 4 (`documentos/page.tsx`, `financeiro/page.tsx`, `financeiro/novo/page.tsx`, `financeiro/[id]/page.tsx`) are owned by sibling plans in this same wave/phase per `107-PATTERNS.md`.
- No blockers. `node_modules` is not installed in this worktree (consistent with the Phase 101 finding that worktree installs don't propagate to sibling checkouts) — full `pnpm build`/`pnpm lint` verification is deferred to the Wave-3 holistic gate (Plan 06), as the plan itself specifies.

---
*Phase: 107-m-dulos-documentos-financeiro*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/documentos/novo/page.tsx`
- FOUND: `web/src/app/(dashboard)/documentos/[id]/page.tsx`
- FOUND: `.planning/phases/LEXCV-107-m-dulos-documentos-financeiro/107-02-SUMMARY.md`
- FOUND commit: `7b08f37`
- FOUND commit: `82080d9`
