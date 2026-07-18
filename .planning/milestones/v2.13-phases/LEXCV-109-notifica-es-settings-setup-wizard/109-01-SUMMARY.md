---
phase: 109-notifica-es-settings-setup-wizard
plan: 01
subsystem: ui
tags: [react, nextjs, radix-ui, dropdown-menu, lucide-react]

# Dependency graph
requires:
  - phase: 101
    provides: "shadcn DropdownMenu primitive installed (web/src/components/ui/dropdown-menu.tsx), zero prior consumers"
provides:
  - "Shared UserMenu component (web/src/components/shared/user-menu.tsx) — first real DropdownMenu consumer in web/src"
  - "3 call sites in dashboard-shell.tsx (topbar, desktop sidebar footer, mobile Sheet footer) consolidated onto UserMenu"
affects: [109-03]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DropdownMenuTrigger asChild wrapping a plain non-navigating <button> (not a shadcn Button) for a multi-element trigger visual"
    - "variant-prop component (topbar | sidebar) sharing one DropdownMenuContent item list, differing only in trigger markup"

key-files:
  created: [web/src/components/shared/user-menu.tsx]
  modified: [web/src/components/shared/dashboard-shell.tsx]

key-decisions:
  - "UserMenu takes `me: MeResponse | undefined` (from web/src/types/auth.ts) rather than a broader ReturnType<typeof useMe> — narrower, sufficient for the fields consumed (nome, roles, avatar_url)"
  - "Terminar sessão uses DropdownMenuItem variant=\"default\" (not \"destructive\") per UI-SPEC's reservation of --destructive for irreversible actions only"
  - "No DropdownMenuLabel header added — kept to the locked 4-item list (Perfil, Configurações, separator, Terminar sessão)"

patterns-established:
  - "Pattern: shared shell subcomponent taking a `variant` discriminator for visually-different-but-behaviorally-identical trigger markup, reusable for future multi-instance dropdown/menu consolidations in dashboard-shell.tsx"

requirements-completed: [NTF-28]

# Metrics
duration: 25min
completed: 2026-07-17
---

# Phase 109 Plan 01: Shared UserMenu DropdownMenu Summary

**Consolidated 3 near-duplicate avatar/name blocks in dashboard-shell.tsx (topbar, desktop sidebar footer, mobile Sheet footer) into one shared `UserMenu` component wrapping the official Radix `DropdownMenu` primitive — its first real consumer in `web/src` — with locked items Perfil / Configurações / separator / Terminar sessão, folding the two standalone logout buttons into the menu.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-07-17T13:35:01Z
- **Tasks:** 2/2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- New `web/src/components/shared/user-menu.tsx`: a `"use client"` component with `variant: "topbar" | "sidebar"` prop, rendering one `DropdownMenu` per instance with a non-navigating `<button type="button">` trigger wrapping the exact pre-existing avatar+name/role markup (pixel-for-pixel preserved: circular gradient avatar for topbar, square `bg-slate-800` avatar for sidebar; initials-fallback and `avatar_url` `<img>` logic both preserved verbatim).
- Menu content identical across all 3 instances: `Perfil` (→ `/profile`, no icon) → `Configurações` (→ `/settings`, `Settings` icon) → `DropdownMenuSeparator` → `Terminar sessão` (`LogOut` icon, `variant="default"`, `onSelect={onLogout}`).
- `dashboard-shell.tsx` now renders `<UserMenu variant="topbar" ...>` once (header) and `<UserMenu variant="sidebar" ...>` twice (desktop `<aside>` footer + mobile `<SheetContent>` footer), each no longer navigating directly to `/settings` on click.
- Removed the 2 standalone `Tooltip`+`Button` "Terminar sessão" icon buttons (desktop sidebar + Sheet footers) — logout now lives exclusively inside the shared menu.
- Removed now-unused imports from `dashboard-shell.tsx`: `Button`, `Tooltip`/`TooltipContent`/`TooltipTrigger`, `LogOut`. Kept `Settings` (still used by the "Configurações" nav-section links), `Link`, `cn`, `useMe`, `hasPermission`, and all nav icons untouched.
- `onLogout` (dashboard-shell.tsx lines 69-77) and the `NAV.filter(...hasPermission...)` nav-filtering logic are byte-for-byte unchanged — passed through as a prop, not re-implemented.

## Task Commits

Each task was committed atomically:

1. **Task 1: Create shared UserMenu DropdownMenu component** - `2665265` (feat)
2. **Task 2: Wire UserMenu into dashboard-shell.tsx at all 3 call sites + remove dead code** - `e90d241` (feat)

**Plan metadata:** committed alongside this SUMMARY (see final commit below)

## Files Created/Modified
- `web/src/components/shared/user-menu.tsx` - New shared `UserMenu` DropdownMenu component (topbar + sidebar trigger variants, locked Perfil/Configurações/separator/Terminar sessão item list)
- `web/src/components/shared/dashboard-shell.tsx` - 3 call sites rewired to `<UserMenu>`; 2 standalone logout buttons and 4 now-unused imports (`Button`, `Tooltip`, `TooltipContent`, `TooltipTrigger`, `LogOut`) removed

## Decisions Made
- Typed the `me` prop as `MeResponse | undefined` (imported from `@/types/auth`) rather than deriving it from `ReturnType<typeof useMe>["data"]` — equivalent at the call site (`me.data` from `useMe()` is exactly `MeResponse | undefined`) but avoids a generic-inference dependency on the hook's internal typing, per the plan's "executor discretion" note on typing.
- Kept the trigger element as a plain `<button type="button">` (not `<div role="button" tabIndex={0}>`) for both variants — simpler, natively focusable/keyboard-operable, consistent with the plan's first listed option.
- No `DropdownMenuLabel` (name/email header) added inside the menu — the plan explicitly scoped this as optional discretion and the locked item list is exactly 4 entries; kept minimal per UI-SPEC guidance.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree branch was stale (created from an outdated base, not current master HEAD)**
- **Found during:** Initial setup, before Task 1 — reading `read_first` files via the worktree's own relative paths failed: `.planning/phases/LEXCV-109-notifica-es-settings-setup-wizard/` did not exist, and `web/src/components/ui/dropdown-menu.tsx` (the DropdownMenu primitive itself, installed in Phase 101) was also missing from the worktree checkout.
- **Issue:** The worktree branch `worktree-agent-a6c1a9e1683ff4cde` was created from a much older point in history (pre-Phase 101, missing ~50 commits including all of Phase 101's shadcn primitive installs and Phase 109's planning docs) — this matches the known upstream issue where `EnterWorktree` branches from a stale base instead of the current feature-branch/main HEAD.
- **Fix:** Verified the worktree branch HEAD was a strict ancestor of `master` with **zero** unique commits not already in `master` (`git log --oneline master..HEAD` returned empty), confirming a hard-reset would lose no work. Ran `git reset --hard <master-tip-sha>` to bring the worktree branch up to `master`'s current tip (`f54e0c5`), then re-verified the worktree was clean and all expected files (`.planning/phases/LEXCV-109-.../`, `web/src/components/ui/dropdown-menu.tsx`, etc.) were present.
- **Files modified:** None (git ref update only, no source file changes)
- **Verification:** `git status --short` clean pre- and post-reset; re-read `dashboard-shell.tsx` and `109-01-PLAN.md` from the worktree paths post-reset and confirmed byte-for-byte match against what had been read from the main repo paths during initial context-gathering.
- **Committed in:** N/A (branch-ref operation, not a file commit — occurred before Task 1's commit `2665265`)

---

**Total deviations:** 1 auto-fixed (1 blocking — stale worktree base)
**Impact on plan:** No impact on the plan's actual code changes; this was purely an environment-recovery step required before any task could begin (the plan's own read_first files and even the DropdownMenu primitive it depends on were unreachable otherwise). Verified safe (zero unique commits lost) before acting.

## Issues Encountered
- The Bash tool's `grep` invocations intermittently returned exit code 1 (no match) for patterns that were verifiably present in the file (confirmed via direct file read and via the dedicated Grep tool). Worked around by using the Grep tool for all acceptance-criteria pattern checks instead of shell `grep` — no impact on verification confidence, all 5 Task 1 acceptance patterns and both Task 2 structural checks (`<UserMenu` count = 3, no `aria-label="Terminar sessão"` remaining) were independently confirmed via the Grep tool and full-file reads.
- `pnpm lint` / `pnpm build` could not be run locally — this worktree has no `node_modules` installed (`web/node_modules` absent). Per this plan's own `<verification>` note, the authoritative lint/typecheck/build gate runs in Plan 109-03's holistic check across all of Phase 109's changes. Acceptance criteria that don't require a running lint (import-usage grep checks, structural `<UserMenu>` count, string-literal checks) were all verified manually instead.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `UserMenu` and its 3 call sites are complete and self-contained; Plan 109-02 (notification-bell.tsx Badge migration + setup/page.tsx Progress wizard) touches entirely different files with no overlap.
- Plan 109-03's holistic gate should run `pnpm lint`/`pnpm build`/`pnpm typecheck` in an environment with `node_modules` installed to confirm no unused-import or type errors were introduced by this plan (not verifiable in this worktree).
- No blockers identified.

---
*Phase: 109-notifica-es-settings-setup-wizard*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: web/src/components/shared/user-menu.tsx
- FOUND: web/src/components/shared/dashboard-shell.tsx
- FOUND: .planning/phases/LEXCV-109-notifica-es-settings-setup-wizard/109-01-SUMMARY.md
- FOUND commit: 2665265 (Task 1)
- FOUND commit: e90d241 (Task 2)
