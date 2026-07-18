---
phase: 110-refinamento-da-landing-webpage
plan: 02
subsystem: ui
tags: [react, nextjs, shadcn, radix-ui, tailwind, badge, card, webpage]

# Dependency graph
requires:
  - phase: 101-funda-o-cli-init-e-design-tokens
    provides: shadcn preset (radix-vega) and Card/Button primitives already established in webpage/
provides:
  - webpage/src/components/ui/badge.tsx — new Badge primitive (byte-identical copy of web/'s badge.tsx), first introduction of Badge to webpage/
  - hero-section.tsx and contact-section.tsx recomposed onto TrustSection's Card pattern, eyebrow spans migrated to Badge
affects: [110-01 (Sheet mobile nav, disjoint files, same phase Wave 1), any future webpage/ landing section touching Hero/Contacto]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Card as pure layout/spacing wrapper around a raw page heading (h1/h2) — explicitly NOT using CardTitle to avoid heading-level demotion (h3)"
    - "Badge className override (text-sm font-semibold uppercase tracking-[0.2em]) to reskin the 12px/500 default to a pre-existing 14px/600 eyebrow look, replacing a manual bordered <span>"

key-files:
  created:
    - webpage/src/components/ui/badge.tsx
  modified:
    - webpage/src/components/hero-section.tsx
    - webpage/src/components/contact-section.tsx

key-decisions:
  - "CardHeader wraps only the Badge; CardContent wraps h1/h2 + p + CTA button(s) — chosen over CardFooter for CTAs, mirroring how content naturally flows top-to-bottom in a single Card without an extra footer boundary"
  - "BrandMark and the blue accent rule stay outside/above the Hero's Card exactly as before — only Badge+h1+p+CTAs relocated inside, per the plan's explicit scope boundary"

patterns-established:
  - "Two-section Card/Badge consistency lock: Hero and Contacto share the identical Card/CardHeader/CardContent shape and identical Badge variant+className, so future edits to one should mirror the other"

requirements-completed: [LDG-18]

# Metrics
duration: ~20min
completed: 2026-07-18
---

# Phase 110 Plan 02: Hero/Contacto Card + Badge Recomposition Summary

**Recomposed the landing's Hero and Contacto sections onto TrustSection's idiomatic Card pattern, introducing a new Badge primitive (copied verbatim from `web/`) for both eyebrows while keeping the raw `<h1>`/`<h2>` tags untouched.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-18T00:26:57Z
- **Tasks:** 3 completed
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments
- `webpage/src/components/ui/badge.tsx` created as a byte-identical copy of `web/src/components/ui/badge.tsx` (verified via `diff`) — all 9 variants preserved, `defaultVariants.variant: "secondary"` intact, zero token-mismatch risk since Badge uses hardcoded Tailwind color literals.
- `HeroSection` eyebrow span replaced with `<Badge variant="secondary" className="text-sm font-semibold uppercase tracking-[0.2em]">`; Badge + `<h1>` + `<p>` + 2 CTA `Button`s now live inside a single `Card` (`CardHeader` for the Badge, `CardContent` for the rest). `BrandMark` and the blue accent rule remain outside/above the Card, unchanged.
- `ContactSection` eyebrow span replaced with the identical Badge variant/className as the Hero; Badge + `<h2>` + `<p>` + the mailto CTA `Button` now live inside a `Card` with the same `CardHeader`/`CardContent` split, making the two Cards visually identical apart from inner content.
- Neither heading tag was swapped for `CardTitle` — both `<h1>` and `<h2>` remain raw tags with their original classes, avoiding the heading-demotion regression (`CardTitle` renders `<h3>`) called out in the plan and UI-SPEC.
- `pnpm build` (Turbopack) completed successfully with zero type errors across all 3 touched files plus the 2 Card/Badge primitives.

## Task Commits

Each task was committed atomically:

1. **Task 1: Copy the Badge primitive into webpage/ verbatim** - `23f814d` (feat)
2. **Task 2: Recompose HeroSection with Card + Badge** - `a2318b5` (feat)
3. **Task 3: Recompose ContactSection with Card + Badge** - `d0b464a` (feat)

**Plan metadata:** committed in this same close-out step (SUMMARY + REQUIREMENTS, per parallel worktree mode — STATE.md/ROADMAP.md are handled centrally by the orchestrator after merge)

_Note: all 3 tasks are `type="execute"` (non-TDD); each is a single atomic feat commit._

## Files Created/Modified
- `webpage/src/components/ui/badge.tsx` - New file, byte-identical copy of `web/src/components/ui/badge.tsx` (cva-based Badge, 9 variants, default `secondary`).
- `webpage/src/components/hero-section.tsx` - Added `Badge`/`Card`/`CardContent`/`CardHeader` imports; eyebrow span → `Badge`; Badge/h1/p/CTAs wrapped in `Card`; `BrandMark`/accent rule left outside.
- `webpage/src/components/contact-section.tsx` - Added `Badge`/`Card`/`CardContent`/`CardHeader` imports; eyebrow span → `Badge` (identical variant/className to Hero); Badge/h2/p/CTA wrapped in `Card` matching Hero's shape.

## Decisions Made
- Split each Card into `CardHeader` (Badge only) + `CardContent` (heading + paragraph + CTA(s)), rather than using `CardFooter` for the CTA buttons — simpler two-region layout, and matches how the content already reads top-to-bottom without an extra footer seam.
- Followed the plan's explicit scope boundary literally: `BrandMark` and the Hero's standalone blue accent rule (`h-px w-12 bg-blue-600`) stay outside the `Card`, exactly where they were before — only the 4 named elements (Badge/heading/paragraph/CTAs) moved inside.

## Deviations from Plan

None - plan executed exactly as written. All 3 tasks matched the plan's prescribed code (imports, Badge variant/className, Card composition, preserved raw headings) and all automated `<verify>` grep/build commands passed.

## Issues Encountered

This worktree's branch (`worktree-agent-a0ac073ba52c4eb96`) had been created from a point ~28 commits behind current `master`, missing the entire LEXCV-110 phase-planning history (the plan file itself did not exist yet in this worktree) — the same known Claude Code worktree-creation bug (#2015) documented in prior phase summaries. HEAD was first confirmed to be on the correct per-agent branch (not a protected ref), and since this branch had zero unique commits of its own (its HEAD was a strict ancestor of `master`), it was safely fast-forwarded to `master`'s tip (`git merge --ff-only master`) to pick up the phase 110 planning docs before execution began. This is a workspace-setup issue, not a plan-execution deviation.

`webpage/node_modules` was also missing in this worktree (worktrees don't share the gitignored `node_modules` tree); `pnpm install` was run successfully in `webpage/` before the build-based verification in Task 3, so `pnpm build` was actually executed (not skipped/stubbed).

A transient `grep -q` flakiness was observed in the Bash tool when checking for the `tracking-[0.2em]` literal (escaped-bracket pattern intermittently returned exit 1 on an otherwise-matching line, most likely a git-bash/grep interaction on this Windows host). Verification was cross-checked with the dedicated Grep tool (ripgrep-backed), which consistently confirmed the match; this is a tooling artifact, not a content defect — the underlying file content was correct throughout.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- LDG-18 fully satisfied: Hero and Contacto both use the Card/Badge composition, visually consistent with each other and with TrustSection's existing Card pattern; no heading-semantics regression.
- No file overlap with Plan 110-01 (`webpage/src/components/ui/sheet.tsx`, `webpage/src/components/site-header.tsx`), so no merge conflicts expected between the two Wave 1 plans.
- Ready for Wave 2 (Plan 110-03) or phase-level merge/verification.

---
*Phase: 110-refinamento-da-landing-webpage*
*Completed: 2026-07-18*

## Self-Check: PASSED

- FOUND: webpage/src/components/ui/badge.tsx
- FOUND: webpage/src/components/hero-section.tsx
- FOUND: webpage/src/components/contact-section.tsx
- FOUND: commit 23f814d (Task 1)
- FOUND: commit a2318b5 (Task 2)
- FOUND: commit d0b464a (Task 3)
