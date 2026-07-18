---
phase: 110-refinamento-da-landing-webpage
plan: 01
subsystem: ui
tags: [nextjs, react, radix-ui, sheet, mobile-nav, webpage]

# Dependency graph
requires:
  - phase: 101-foundation
    provides: "webpage/'s unified radix-ui package dependency + tw-animate-css (101-04), consumed unchanged by this plan's Sheet copy"
provides:
  - "webpage/src/components/ui/sheet.tsx — Sheet primitive (Dialog-based), byte-identical copy of web/'s post-Phase-102 version"
  - "Functional mobile navigation (hamburger -> Sheet drawer) on the public landing SiteHeader, closing LDG-17"
affects: [110-02, 110-03]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared NAV_LINKS array mapped into both the desktop <nav> and the mobile Sheet drawer (single source of truth for anchor links)"
    - "Direct onClick={() => setOpen(false)} close-on-navigate for same-page anchor links (NOT usePathname/useEffect, per Phase 109's WR-01 lesson)"

key-files:
  created:
    - webpage/src/components/ui/sheet.tsx
  modified:
    - webpage/src/components/site-header.tsx

key-decisions:
  - "Sheet primitive copied verbatim via shell `cp` from web/src/components/ui/sheet.tsx — zero edits, guaranteeing byte-identical imports/exports"
  - "Header-level Entrar Button gated `hidden md:inline-flex` to prevent it duplicating the drawer's own Entrar CTA on mobile"
  - "Hamburger trigger kept at h-9 w-9 (36px) to match the adjacent ThemeToggle, not a 44px touch target"

patterns-established:
  - "Mobile nav drawers in this app close via direct onClick per link/CTA, never a pathname-watching effect"

requirements-completed: [LDG-17]

# Metrics
duration: ~20min
completed: 2026-07-17
---

# Phase 110 Plan 01: Mobile Navigation Sheet for SiteHeader Summary

**Hamburger-triggered Radix Sheet drawer added to `webpage/`'s public-landing SiteHeader, closing a total mobile-navigation gap (LDG-17), while the desktop header stays pixel-identical to before.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-17T23:10:00-01:00 (approx.)
- **Completed:** 2026-07-17T23:30:17-01:00
- **Tasks:** 2 completed
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- `webpage/src/components/ui/sheet.tsx` created as a byte-identical copy of `web/`'s vetted Sheet primitive (Radix Dialog-based) — zero new dependency, `radix-ui`/`lucide-react`/`@/lib/utils` already resolve unchanged.
- `SiteHeader` converted to a Client Component exposing a right-side Sheet drawer (`w-72 sm:max-w-sm`) containing the same 3 nav anchors (Funcionalidades/Confiança/Contacto) plus an Entrar CTA, all closing on click via direct `setOpen(false)`.
- Desktop header behavior/appearance unchanged: 3 nav anchors (now sourced from a shared `NAV_LINKS` array instead of 3 hardcoded `<a>` tags) + ThemeToggle + Entrar, hamburger hidden at `md:` breakpoint.
- Header-level Entrar button gated `hidden md:inline-flex` so it never renders twice on mobile once the drawer also carries an Entrar CTA.

## Task Commits

Each task was committed atomically:

1. **Task 1: Copy the Sheet primitive into webpage/ verbatim** - `1643350` (feat)
2. **Task 2: Rewrite SiteHeader as a Client Component with a Sheet mobile drawer** - `5ae176e` (feat)

**Plan metadata:** (this commit, following)

## Files Created/Modified
- `webpage/src/components/ui/sheet.tsx` - New; byte-identical copy of `web/src/components/ui/sheet.tsx` (Sheet/SheetTrigger/SheetContent/SheetTitle/SheetClose/SheetPortal/SheetOverlay/SheetHeader/SheetFooter/SheetDescription, all 10 exports).
- `webpage/src/components/site-header.tsx` - Rewritten as a Client Component (`"use client"`); adds `NAV_LINKS` shared array, a `React.useState` Sheet open/close state, a `md:hidden` hamburger trigger (`aria-label="Abrir menu"`, `h-9 w-9`), a `side="right"` `SheetContent` with an `sr-only` `SheetTitle`, and gates the header-level Entrar button `hidden md:inline-flex`.

## Decisions Made
- Used shell `cp` for Task 1 to guarantee byte-for-byte identity with `web/`'s Sheet, per the plan's explicit instruction (avoids any accidental import-path or whitespace drift from a manual retype).
- Kept the Sheet's internal Entrar CTA as a `Button asChild` wrapping a plain `<a href="/login">` (not `next/link`), consistent with the existing header-level Entrar button and the project-wide convention that `/login` is a cross-zone link into `web/`, not part of `webpage/`'s own route table.
- Placed the `Sheet`/`SheetTrigger` markup as a sibling of `ThemeToggle`/Entrar inside the same `flex items-center gap-3` group, relying on `md:hidden`/`hidden md:inline-flex` breakpoint gating (rather than a separate mobile-only wrapper div) to keep the render tree minimal.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree branch was ~200 commits behind master; fast-forwarded before starting work**
- **Found during:** Task 1 read_first gate (attempting to read `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/` and confirm `webpage/package.json`'s `radix-ui` dependency)
- **Issue:** This worktree's branch (`worktree-agent-a5c241eff7d7fb122`) was checked out at `23346b5` (end of the v2.12 milestone), predating the entire v2.13 milestone (Phases 101–110). The Phase 110 plan/context/pattern docs did not exist in the worktree at all, and `webpage/package.json` still carried the pre-Phase-101-04 `@radix-ui/react-slot`/`tailwindcss-animate` dependencies instead of the unified `radix-ui`/`tw-animate-css` this plan's Sheet copy requires. This is distinct from the main-repo checkout, which was already at the current master tip (`28ffcfa`) — the discrepancy was only caught because a direct worktree-path `cat`/`git diff` was run after an initial (misleading) read via the main-repo absolute path.
- **Fix:** Verified `git merge-base --is-ancestor HEAD master` succeeded (this worktree branch had zero unique commits — a pure ancestor of master, so no work could be lost) and the working tree was clean, then ran `git merge --ff-only master` to fast-forward the worktree branch to `28ffcfa`. Re-ran `pnpm install` in `webpage/` afterward to replace the stale `@radix-ui/react-slot`/`tailwindcss-animate` install with `radix-ui`/`tw-animate-css`. Did not touch the pre-existing unrelated stash entry (`stash@{0}`, belongs to a different, older session) per the destructive-git-operations prohibition.
- **Files modified:** None directly (branch ref update + `webpage/node_modules` reinstall only; no tracked source files touched by the sync itself).
- **Verification:** Post-fast-forward, `git log --oneline -1` showed `28ffcfa` (matching master); `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-01-PLAN.md` and siblings now present; `webpage/package.json` confirmed to contain `radix-ui`/`tw-animate-css`; `webpage/node_modules/radix-ui` confirmed present after reinstall.
- **Commit:** N/A (no source commit — this was a worktree/environment sync, not a code change)

**2. [Rule 3 - Blocking] `webpage/node_modules` was missing in this worktree; installed before build verification**
- **Found during:** Pre-Task-2 preparation (worktrees don't share the gitignored `node_modules` tree with the main checkout)
- **Issue:** `pnpm build` (Task 2's verify step) requires `webpage/node_modules`, which did not exist in this worktree.
- **Fix:** Ran `pnpm install` inside `webpage/` (twice — once before discovering the stale-branch issue above with the wrong package.json, and once after the fast-forward with the correct package.json).
- **Files modified:** `webpage/node_modules/` (gitignored, not committed).
- **Verification:** `pnpm build` completed successfully (see Task 2 verification below).
- **Commit:** N/A (gitignored artifact, not committed)

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking environment/worktree issues, discovered and resolved before any source-code task work began). **Impact:** No code-behavior impact — both were prerequisite environment corrections. All plan-specified source changes were implemented exactly as written with no further deviations.

## Issues Encountered
None beyond the 2 deviations documented above (both environment-setup, not implementation problems).

## Verification Results

- `cd webpage && pnpm build` — **PASS** (Next.js 16.2.6 Turbopack build + TypeScript check completed with zero errors).
- `diff web/src/components/ui/sheet.tsx webpage/src/components/ui/sheet.tsx` — **PASS** (byte-identical, exit 0).
- `grep -q 'from "radix-ui"' webpage/src/components/ui/sheet.tsx` — **PASS**.
- All 4 named Sheet exports (Sheet, SheetTrigger, SheetContent, SheetTitle) present — **PASS**.
- `globals.css` not modified by Task 1 — **PASS** (confirmed via `git status`).
- Task 2's full combined verify command (`"use client"`, `aria-label="Abrir menu"`, `setOpen(false)`, `side="right"`, `hidden md:inline-flex`, `sr-only` all present; `usePathname`/`useEffect` both absent) — **PASS**.
- `grep -c '#funcionalidades\|#confianca\|#contacto'` on `site-header.tsx` — **3** (each anchor href appears exactly once, inside the shared `NAV_LINKS` array — not duplicated as hardcoded literals).
- Plan-level `<verification>`: build passes, Sheet byte-identical, SiteHeader is a Client Component with the described Sheet drawer, header Entrar gated `hidden md:inline-flex`, no `usePathname`/`useEffect` anywhere in the file — **ALL PASS**.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- LDG-17 is satisfied: `webpage/`'s `SiteHeader` now has functional mobile navigation via a reused `Sheet`, desktop header is visually unchanged, and the Entrar CTA never renders twice on any breakpoint.
- Wave 1 of Phase 110 (this plan) is complete. Plan 110-02 (Hero/Contacto `Card`/`Badge` recomposition via `badge.tsx`/`hero-section.tsx`/`contact-section.tsx`) runs independently in a separate worktree with zero file overlap — no coordination needed before merge.
- No blockers for 110-02 or 110-03.

---
*Phase: 110-refinamento-da-landing-webpage*
*Completed: 2026-07-17*

## Self-Check: PASSED

- `[ -f webpage/src/components/ui/sheet.tsx ]` — FOUND
- `[ -f webpage/src/components/site-header.tsx ]` — FOUND (modified, "use client" present)
- `git log --oneline --all --grep="110-01"` — FOUND 2 commits (`1643350`, `5ae176e`)
- All acceptance criteria for both tasks re-verified PASS (see Verification Results above)
- All plan-level `<verification>` commands re-run PASS
