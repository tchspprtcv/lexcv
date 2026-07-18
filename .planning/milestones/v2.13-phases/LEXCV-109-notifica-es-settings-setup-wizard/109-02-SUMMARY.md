---
phase: 109-notifica-es-settings-setup-wizard
plan: 02
subsystem: ui
tags: [react, nextjs, shadcn, radix-ui, tailwind, badge, progress]

# Dependency graph
requires:
  - phase: 101-funda-o-cli-init-e-design-tokens
    provides: Badge and Progress primitives already installed/vetted (shadcn `ui/badge.tsx`, `ui/progress.tsx`)
provides:
  - notification-bell.tsx unread counter migrated from manual <span> to official Badge (className override, identical visuals)
  - /setup wizard Progress bar (33/66/100%) derived from existing form/submit state, replacing static Checklist text block
affects: [109-03 (holistic pnpm build/lint gate for the whole phase)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Badge className override via cn() to reskin a shadcn primitive to an exact legacy pixel spec (position/size/color) without adding a new variant"
    - "Locally-derived UI phase enum (idle/submitting/success) computed inline from existing form state, no new useState, feeding a Progress value"

key-files:
  created: []
  modified:
    - web/src/components/shared/notification-bell.tsx
    - web/src/app/setup/page.tsx

key-decisions:
  - "Used cn(...) (not a raw template string) for the Badge className override, since base badgeVariants bg-neutral-100 and the override bg-red-500/bg-slate-400 are same-property-group Tailwind classes needing twMerge conflict resolution"
  - "Renamed the Checklist card label to 'Progresso' (recommended default in the plan) rather than keeping 'Checklist'"
  - "No distinct 'error' wizardPhase branch — a failed submit leaves isSubmitting=false/successMessage=null, correctly falling back to idle/33%, matching serverError's separate unchanged inline box"

patterns-established:
  - "Same-file Badge precedent (category chip) reused for consistency when adding a second Badge usage in the same component"

requirements-completed: [NTF-29, NTF-30]

# Metrics
duration: 15min
completed: 2026-07-17
---

# Phase 109 Plan 02: Notification Bell Badge + Setup Wizard Progress Summary

**Migrated the notification-bell unread counter from a manual `<span>` to the official `Badge` primitive, and replaced the static `/setup` wizard Checklist text with a linear `Progress` bar (33/66/100%) derived from existing form/submit state.**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-17T13:33:44Z
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- `notification-bell.tsx` unread counter now renders as `<Badge>` with a `className` override reproducing the exact original position (`absolute -top-0.5 -right-0.5 h-4 w-4`), color logic (`bg-red-500` normal / `bg-slate-400` on fetch error), and cap (`9+` / `!`) — Popover composition, category-chip Badge, and mark-read handlers all untouched.
- `/setup` wizard now shows an always-visible `Progress` bar with a `Progresso`/percentage header row, value derived from a locally computed `wizardPhase` (`idle`→33%, `submitting`→66%, `success`→100%) sourced from existing `successMessage`/`form.formState.isSubmitting` — no new business state. The 3 original checklist lines are preserved verbatim as a legend below the bar.

## Task Commits

Each task was committed atomically:

1. **Task 1: Migrate notification-bell unread counter from `<span>` to Badge (NTF-29)** - `4b538bb` (feat)
2. **Task 2: Replace /setup Checklist block with a Progress indicator (NTF-30)** - `be07a57` (feat)

**Plan metadata:** committed in this same close-out step (SUMMARY + STATE/ROADMAP, per parallel-mode handling below)

_Note: Both tasks are single-file, single-edit swaps; no TDD RED/GREEN/REFACTOR sequence applies (plan `type: execute`)._

## Files Created/Modified
- `web/src/components/shared/notification-bell.tsx` - Unread-count `<span>` replaced with `<Badge className={cn(...)}>`; added `import { cn } from "@/lib/utils"`.
- `web/src/app/setup/page.tsx` - Added `import { Progress } from "@/components/ui/progress"`; added `wizardPhase`/`wizardProgress` derived values; Checklist card inner content now a `Progresso` + `%` header row, `<Progress value={wizardProgress} />`, and the 3 checklist `<p>` lines as a legend.

## Decisions Made
- Kept the `Badge` default `secondary` variant as the base and overrode 100% of its visuals via `className` + `cn()`, rather than introducing a new `badgeVariants` entry — per plan, the existing soft `"red"` variant was explicitly rejected as too muted for this always-visible alert-style counter.
- Labeled the setup wizard's indicator card "Progresso" (plan's recommended default) instead of retaining "Checklist" as the header text, since the content is no longer a static checklist but a live percentage + bar.

## Deviations from Plan

None - plan executed exactly as written. Both tasks matched the plan's prescribed code verbatim (import additions, className strings, derivation logic, and preserved copy), and both automated `<verify>` grep commands passed on the first attempt.

## Issues Encountered

This worktree's branch (`worktree-agent-a5f8c9ffac174cb20`) had been created 50 commits behind current `master` (missing the entire LEXCV-109 phase-planning history, a known Claude Code worktree-creation bug — #2015) and had zero unique commits of its own. Before starting, HEAD was confirmed to be on the correct per-agent branch (not a protected ref), then the branch was hard-reset to master's current tip (`f54e0c5`) — safe because there were no divergent commits to lose. This is a workspace-setup issue, not a plan-execution deviation, and is recorded here for traceability.

`pnpm build`/`pnpm lint` could not be run in this worktree — `web/node_modules` is not installed here. Per this plan's own `<verification>` section, the authoritative build/lint gate for the whole phase runs in Plan 109-03; both this plan's automated grep-based `<verify>` checks passed for both tasks.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Both NTF-29 and NTF-30 requirements are satisfied; no shared files with Plan 109-01 (`user-menu.tsx`, `dashboard-shell.tsx`), so no merge conflicts expected between the two Wave 1 plans.
- Ready for Plan 109-03's holistic `pnpm build`/`pnpm lint` verification gate across the full phase (both plans' changes combined).

---
*Phase: 109-notifica-es-settings-setup-wizard*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: web/src/components/shared/notification-bell.tsx
- FOUND: web/src/app/setup/page.tsx
- FOUND: .planning/phases/LEXCV-109-notifica-es-settings-setup-wizard/109-02-SUMMARY.md
- FOUND: commit 4b538bb (Task 1)
- FOUND: commit be07a57 (Task 2)
