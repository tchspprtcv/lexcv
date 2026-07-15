---
phase: LEXCV-101-funda-o-cli-init-e-design-tokens
fixed_at: 2026-07-15T23:13:33Z
review_path: .planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 2
skipped: 1
status: partial
---

# Phase LEXCV-101: Code Review Fix Report

**Fixed at:** 2026-07-15T23:13:33Z
**Source review:** .planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/101-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (fix_scope: critical_warning — 0 Critical/Blocker + 3 Warnings; 5 Info-level findings excluded as out of scope)
- Fixed: 2
- Skipped: 1 (deliberate — see rationale below)

## Fixed Issues

### WR-01: `EmptyDescription` is typed as a paragraph but renders a `<div>`

**Files modified:** `web/src/components/ui/empty.tsx`
**Commit:** `70a68d0`
**Applied fix:** Changed the rendered element in `EmptyDescription` from `<div>` to `<p>`, matching its declared `React.ComponentProps<"p">` type. This aligns it with the sibling component pattern in the same file and restores correct paragraph semantics/style inheritance for consumers. Self-contained, single-file, no behavioral risk — verified by re-reading the modified section (Tier 1) and attempting a TypeScript project check (Tier 2 unavailable in this isolated worktree: `node_modules`/`tsc` are not installed since they're gitignored and not part of the git-tracked worktree checkout; fell back to Tier 3, accepting Tier 1).

### WR-03: Unsafe double type-cast to attach a ref to a non-forwardRef `Button`

**Files modified:** `web/src/components/ui/calendar.tsx`
**Commit:** `97e1fbd`
**Applied fix:** Did not modify `button.tsx` (it is one of the 7 files explicitly scheduled for full token/primitive reconciliation in Phase 102, per `ROADMAP.md` line 310 "Phase 102: Reconciliação do Design System" — adding `React.forwardRef` there now would front-run that phase's planned work on a file whose review-cited risk surface (WR-02) already spans it). Instead, applied the reviewer's second suggested option: added a dev-mode-only runtime safety net in `CalendarDayButton` (a second `useEffect` on mount) that warns via `console.warn` if `ref.current` is still `null` after commit — which would indicate the `ButtonWithRef` cast's assumption (that `Button` forwards `ref` through its props spread) silently broke. This converts a previously undetectable silent regression (roving-tabindex keyboard focus breaking with no compile-time or runtime signal) into a loud dev-console warning, without touching the out-of-scope file or duplicating Phase 102's `forwardRef` migration. Verified: re-read the modified section (Tier 1, effect logic and surrounding code intact); Tier 2 syntax check unavailable for the same `node_modules`-not-installed reason as WR-01, and `node -c` does not support `.tsx` (per verification_strategy, fell back to Tier 1/Tier 3).

## Skipped Issues

### WR-02: Half-migrated design tokens leave two disconnected color systems

**File:** `web/src/components/ui/alert-dialog.tsx:39`, `web/src/components/ui/dialog.tsx:41`, `web/src/components/ui/sheet.tsx:55`, `web/src/components/ui/popover.tsx:22`, `web/src/components/ui/switch.tsx:16`, `web/src/components/ui/radio-group.tsx:29`, `web/src/components/ui/button.tsx:12-17`
**Reason:** Deliberate skip, not a rollback-after-failure. Confirmed via `ROADMAP.md` (`.planning/ROADMAP.md:310`, "Phase 102: Reconciliação do Design System") that this exact set of hand-rolled components hardcoding the legacy neutral/slate palette is the explicit subject matter of the next phase, which — per the roadmap's own sequencing rationale (`.planning/ROADMAP.md:288`) — must complete before any module phase begins specifically to avoid "the app visibly half-migrated" pitfall. Re-skinning these 7 files onto the semantic tokens now (as the review's suggested fix describes: `bg-white ... dark:bg-[#020617]` → `bg-popover text-popover-foreground`, `border-neutral-300 ... dark:border-neutral-700` → `border-input`, etc.) would mean doing Phase 102's planned work early, inside Phase 101's fix pass, risking duplicate/conflicting edits when Phase 102 executes its own planned token migration across the same files.
**Original issue:** These 7 components were touched in Phase 101 only to swap the Radix import path (`@radix-ui/react-*` → unified `radix-ui` package); their Tailwind classes were left on the old hardcoded neutral/slate palette (including a magic hex literal `dark:bg-[#020617]` in both `dialog.tsx` and `alert-dialog.tsx` that duplicates, without referencing, the `--background` dark-mode token). This creates two parallel, disconnected color systems that will silently diverge if the semantic tokens are ever retuned.
**Recommendation:** No new tracking action taken here beyond this report, since the review's suggested action ("track as an explicit follow-up... it may already be in `deferred-items.md`") is already superseded by a stronger commitment: this work is a named, roadmapped phase (Phase 102) rather than an ad-hoc deferred item. No entry was added to `deferred-items.md` to avoid a redundant/conflicting tracking record — Phase 102 is the tracking record.

---

_Fixed: 2026-07-15T23:13:33Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
