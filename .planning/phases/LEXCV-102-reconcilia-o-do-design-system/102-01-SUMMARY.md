---
phase: 102-reconcilia-o-do-design-system
plan: 01
subsystem: ui
tags: [shadcn, radix-ui, cva, design-system, button, badge, calendar, breadcrumb]

# Dependency graph
requires:
  - phase: 101-funda-o-cli-init-e-design-tokens
    provides: shadcn CLI init (components.json, radix-vega preset), semantic design tokens in globals.css, unified radix-ui package migration, 101-REVIEW.md findings IN-01/IN-04/IN-05/IN-06
provides:
  - "button.tsx exports buttonVariants (named export) for reuse by calendar.tsx and any future consumer"
  - "Diff-first reconciliation of button/badge/input/label/radio-group/switch/textarea against the official shadcn registry, all Rule-C identity preserved byte-for-byte"
  - "calendar.tsx buttonVariants duplication removed (imports from button.tsx instead)"
  - "breadcrumb.tsx Slot aliasing unified with button.tsx (SlotPrimitive/.Slot convention)"
  - "shadcn package correctly classified as devDependency"
affects: [102-02, 102-03, 103, 104, 105, 106, 107, 108, 109]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Diff-first reconciliation: shadcn add <component> --diff (preview only, never --overwrite), classify every delta as Rule A (identical-value, apply) / Rule B (convergent-but-different, apply+flag) / Rule C (component identity, never substitute)"
    - "buttonVariants exported from button.tsx as the single source of truth; other files (calendar.tsx) import it instead of duplicating the CVA config"

key-files:
  created: []
  modified:
    - web/src/components/ui/button.tsx
    - web/src/components/ui/badge.tsx (audited, unchanged)
    - web/src/components/ui/input.tsx (audited, unchanged)
    - web/src/components/ui/label.tsx (audited, unchanged)
    - web/src/components/ui/radio-group.tsx (audited, unchanged)
    - web/src/components/ui/switch.tsx (audited, unchanged)
    - web/src/components/ui/textarea.tsx (audited, unchanged)
    - web/src/components/ui/calendar.tsx
    - web/src/components/ui/breadcrumb.tsx
    - web/package.json
    - web/pnpm-lock.yaml

key-decisions:
  - "button.tsx/badge.tsx: upstream registry's bg-primary-based default variant, new destructive variant, and xs/icon-xs/icon-sm/icon-lg sizes explicitly NOT adopted — all 5 button variants / 4 sizes and all 9 badge variants (incl. gray) preserved byte-for-byte per Rule C"
  - "input/label/radio-group/switch/textarea: diffs confirmed zero color/radius delta to accept (as the pattern map predicted); upstream's aria-invalid/peer-disabled/group-disabled state-styling additions and label.tsx's added 'use client' directive were judged non-critical UX enhancements (not a correctness/security/a11y regression against what ships today) and were NOT adopted, keeping the five files unchanged this plan"
  - "calendar.tsx import reordered to `import { Button, type ButtonProps, buttonVariants } from \"@/components/ui/button\"` (buttonVariants last) so the plan's literal automated verify string matches; functionally identical to any import order"

patterns-established:
  - "buttonVariants as an exported, reusable CVA config — future consumers of button styling should import from button.tsx, not duplicate the config"

requirements-completed: [DSR-01, DSR-02]

# Metrics
duration: ~35min
completed: 2026-07-16
---

# Phase 102 Plan 01: Reconcile Component-Identity Primitives (button/badge/form) Summary

**Diff-first reconciliation of 7 Rule-C shadcn primitives against the official registry with zero visual/variant drift, plus closure of 3 inherited Phase-101 code-review findings (buttonVariants dedup, Slot alias uniformity, shadcn devDependency placement).**

## Performance

- **Duration:** ~35 min
- **Completed:** 2026-07-16T01:13:10Z
- **Tasks:** 3/3 completed
- **Files modified:** 4 (button.tsx, calendar.tsx, breadcrumb.tsx, package.json + pnpm-lock.yaml); 6 files audited with zero changes (badge.tsx, input.tsx, label.tsx, radio-group.tsx, switch.tsx, textarea.tsx)

## Accomplishments

- Ran `pnpm dlx shadcn@latest add <component> --diff` (preview-only, never `--overwrite`) against all 7 in-scope components, confirming the exact upstream deltas predicted by 102-UI-SPEC.md/102-PATTERNS.md: `bg-primary`-based defaults, a new `destructive` variant, new size steps, and (for badge) the complete removal of the 6 custom color variants — none of which were adopted, per Rule C.
- `button.tsx` now exports `buttonVariants` alongside `Button` (`export { Button, buttonVariants };`), closing 101-REVIEW.md IN-05.
- `calendar.tsx`'s duplicated `buttonVariants` CVA config (and its explanatory comment block) removed; now imports `buttonVariants` from `button.tsx`. The `ButtonWithRef` cast (unrelated ref-forwarding workaround, WR-03) preserved verbatim.
- Bonus cleanup: removed the now-unnecessary `eslint-disable-next-line react-hooks/exhaustive-deps` on `calendar.tsx`'s dev-mode ref-warning effect (closes 101-REVIEW.md IN-01).
- `breadcrumb.tsx`'s `Slot` aliasing unified to match `button.tsx`'s `SlotPrimitive`/`.Slot` convention (closes 101-REVIEW.md IN-04).
- `shadcn` moved from `dependencies` to `devDependencies` in `web/package.json`, alphabetically placed between `eslint-config-next` and `tailwindcss`; `pnpm install` re-classified `pnpm-lock.yaml` with zero net-new packages (closes 101-REVIEW.md IN-06 / 101-UI-REVIEW.md Priority Fix #3).
- `pnpm build` passes green — Next.js typecheck across all 93 known import occurrences / 38 consumer files confirmed no regression.

## Task Commits

Each task was committed atomically:

1. **Task 1: Reconcile button.tsx and badge.tsx (Rule C) + export buttonVariants + Slot alias** - `2466287` (feat)
2. **Task 2: Reconcile the five form primitives (Rule C — audit, expect no color change)** - no commit (audit-only task; all 5 files already compliant, zero changes required — see Deviations)
3. **Task 3: Dedup buttonVariants in calendar.tsx, uniform Slot alias in breadcrumb.tsx, move shadcn to devDependencies, verify build** - `bedf28c` (refactor)

_Note: Task 2 produced no file changes because the diff-first audit confirmed the pre-existing files already satisfied every Rule-C requirement (no `bg-primary`, no scoped `@radix-ui/react-*` imports, all `data-slot` attributes present) — there is nothing to commit for a verified no-op._

## Files Created/Modified

- `web/src/components/ui/button.tsx` - Added `buttonVariants` to the export statement (`export { Button, buttonVariants };`); all 5 variants/4 sizes/Slot alias unchanged
- `web/src/components/ui/badge.tsx` - Audited via registry diff, zero changes (all 9 variants already compliant)
- `web/src/components/ui/input.tsx` - Audited, zero changes
- `web/src/components/ui/label.tsx` - Audited, zero changes
- `web/src/components/ui/radio-group.tsx` - Audited, zero changes
- `web/src/components/ui/switch.tsx` - Audited, zero changes
- `web/src/components/ui/textarea.tsx` - Audited, zero changes
- `web/src/components/ui/calendar.tsx` - Removed duplicated `buttonVariants` CVA + explanatory comment; now imports `buttonVariants` from `button.tsx`; removed unnecessary eslint-disable directive
- `web/src/components/ui/breadcrumb.tsx` - `Slot` import/usage renamed to `SlotPrimitive`/`.Slot` to match `button.tsx`
- `web/package.json` - `shadcn` moved from `dependencies` to `devDependencies`
- `web/pnpm-lock.yaml` - Re-classified via `pnpm install` (dependency-block move only, zero new packages)

## Decisions Made

- Kept `button.tsx`'s existing `SlotPrimitive`/`.Slot` convention (rather than adopting `breadcrumb.tsx`'s prior `Slot`/`.Root` convention or the upstream registry's own `Slot`/`.Root`) as the unified alias across both files, per the plan's explicit instruction.
- For the 5 form primitives, judged the upstream diffs' `aria-invalid`/`peer-disabled`/`group-data-[disabled]` state-styling additions and `label.tsx`'s new `"use client"` directive as non-critical enhancements rather than "genuinely missing" accessibility attributes required for correctness — left all 5 files unchanged, matching the pattern map's explicit expectation of "no color/radius change to accept."
- Reordered calendar.tsx's import so `buttonVariants` is the last named import, making the exact substring the plan's automated `<verify>` block greps for (`buttonVariants } from "@/components/ui/button"`) present verbatim.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing `web/.env.local` in the isolated worktree**
- **Found during:** Task 3 (build verification gate)
- **Issue:** `pnpm build` failed immediately with `Error: BACKEND_API_ORIGIN is required` — the worktree checkout, like every prior worktree in this milestone (per PROJECT.md Phase 101 Key Decisions), does not carry gitignored files. `web/.env.local` never existed in this worktree.
- **Fix:** Copied `web/.env.local` from the main checkout (`C:\Users\francisco.horta\Documents\projects\personal\lexcv\web\.env.local`) into the worktree's `web/.env.local`. This is a local dev-environment file, gitignored, never committed — no secrets were introduced into git history.
- **Files modified:** `web/.env.local` (not tracked by git, not committed)
- **Verification:** `pnpm build` subsequently completed successfully (24/24 static pages generated, TypeScript passed).

---

**Total deviations:** 1 auto-fixed (1 blocking, environment-only)
**Impact on plan:** No code-level scope creep. The fix only unblocked the build verification gate; it introduced no source changes.

## Issues Encountered

- Initial `pnpm dlx shadcn@latest add button --diff` / `add badge --diff` confirmed the exact upstream deltas anticipated by 102-UI-SPEC.md (bg-primary default, destructive variant, xs size for button; complete loss of all 6 custom badge color variants) — none adopted, as designed. No surprises beyond what research already flagged.
- Worktree `node_modules`/lockfile classification required an explicit `pnpm install` (per the Phase-101 lesson already recorded in PROJECT.md Key Decisions: "Instalações via isolation=\"worktree\" não propagam node_modules ao checkout principal") — this was already anticipated by the plan's own Task 3 instructions and executed as specified.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `buttonVariants` is now a stable, exported API from `button.tsx` — any future component (this phase's 102-02/102-03, or later module phases) needing button-derived styling should import it rather than re-duplicating the CVA config.
- The diff-first reconciliation protocol (run `--diff`, classify Rule A/B/C, never blind-overwrite) is proven out end-to-end for 7 of the 13 in-scope components; the same protocol applies unchanged to 102-02's remaining 6 (card/popover/dialog/alert-dialog/table/sheet).
- No blockers for 102-02/102-03 (zero file overlap, confirmed by plan-checker) or for any later phase depending on this reconciliation.

## Self-Check: PASSED

All 11 claimed files found on disk (button.tsx, badge.tsx, input.tsx, label.tsx, radio-group.tsx, switch.tsx, textarea.tsx, calendar.tsx, breadcrumb.tsx, package.json, this SUMMARY.md). Both task commits (`2466287`, `bedf28c`) confirmed present in `git log --oneline --all`.

---
*Phase: 102-reconcilia-o-do-design-system*
*Completed: 2026-07-16*
