---
phase: 101-funda-o-cli-init-e-design-tokens
plan: 03
subsystem: ui
tags: [shadcn, radix-ui, cmdk, react-day-picker, tailwind-v4]

# Dependency graph
requires:
  - phase: 101-02
    provides: "web/components.json (Radix base, radix-vega style) and the full semantic token set in web/src/app/globals.css that new primitives style against"
provides:
  - "16 net-new shadcn UI primitives in web/src/components/ui/ (select, native-select, tabs, dropdown-menu, command, tooltip, checkbox, avatar, separator, skeleton, progress, calendar, breadcrumb, accordion, navigation-menu, empty), all on the Radix/data-slot/CVA conventions"
  - "web/src/components/ui/input-group.tsx (transitive registryDependency of command, not one of the 16 named primitives, but required for command.tsx to compile)"
  - "react-day-picker pinned to exact 9.14.0 (no caret) in web/package.json, avoiding the broken v10 line (upstream issue #10914)"
affects: [102, 104, 105, 106, 107, 108, 109]

# Tech tracking
tech-stack:
  added: ["cmdk (Command palette primitive)", "react-day-picker@9.14.0 (exact pin)", "date-fns (Calendar date formatting)"]
  patterns:
    - "move-aside/restore technique: when a CLI `add` target has a registryDependency on an existing hand-rolled file, temporarily rename the hand-rolled file(s) so the CLI writes the new files cleanly (with correct icon/import transforms applied), then restore the originals and discard the CLI-fresh versions of the hand-rolled files - avoids the interactive overwrite-prompt hanging in a non-TTY shell and guarantees zero accidental overwrites"
    - "when a new primitive needs a hand-rolled component's internal (non-exported) CVA or a ref that the hand-rolled component doesn't forward, keep the fix entirely inside the new primitive file (local CVA mirror + typed cast wrapper) rather than modifying the hand-rolled file"

key-files:
  created:
    - "web/src/components/ui/select.tsx"
    - "web/src/components/ui/native-select.tsx"
    - "web/src/components/ui/tabs.tsx"
    - "web/src/components/ui/dropdown-menu.tsx"
    - "web/src/components/ui/command.tsx"
    - "web/src/components/ui/tooltip.tsx"
    - "web/src/components/ui/checkbox.tsx"
    - "web/src/components/ui/avatar.tsx"
    - "web/src/components/ui/separator.tsx"
    - "web/src/components/ui/skeleton.tsx"
    - "web/src/components/ui/progress.tsx"
    - "web/src/components/ui/calendar.tsx"
    - "web/src/components/ui/breadcrumb.tsx"
    - "web/src/components/ui/accordion.tsx"
    - "web/src/components/ui/navigation-menu.tsx"
    - "web/src/components/ui/empty.tsx"
    - "web/src/components/ui/input-group.tsx"
  modified:
    - "web/package.json"
    - "web/pnpm-lock.yaml"

key-decisions:
  - "input-group.tsx added even though it's not one of the 16 named primitives - command.tsx's CommandInput hard-imports InputGroup/InputGroupAddon from it and cannot compile without it; input-group.tsx itself only depends on the already-existing button/input/textarea (via their exported component, not overwriting them)"
  - "Removed the showCloseButton prop pass-through in CommandDialog (CLI-generated) since the hand-rolled DialogContent has no such prop and always renders its close button unconditionally - command.tsx now compiles against the existing dialog.tsx with zero modification to dialog.tsx; net effect is the command palette always shows a close button (upstream default hides it), an acceptable, documented behavior difference since Command isn't consumed by any page yet"
  - "calendar.tsx mirrors button.tsx's buttonVariants CVA locally (button.tsx doesn't export it) and casts Button through a locally-typed ButtonWithRef (button.tsx isn't forwardRef-wrapped) rather than modifying button.tsx - React 19 forwards `ref` through the props spread at the host <button> element regardless of forwardRef, so this preserves the roving-tabindex keyboard-focus behavior at runtime while keeping button.tsx byte-for-byte unchanged"
  - "3 pre-existing `vitest` module-resolution errors in unrelated Phase 97-02 test files logged to deferred-items.md instead of fixed - out of this plan's scope per the Scope Boundary rule; `pnpm build` (the stronger, bundler-exercising gate) passes clean with exit 0 including all 16 new primitives"
  - "Created web/.env.local (gitignored, not committed) with the documented BACKEND_API_ORIGIN/NEXT_PUBLIC_API_BASE_PATH dev defaults from .env.example - `pnpm build` throws at startup without it (pre-existing project requirement, unrelated to this plan's own changes) and Task 3's build gate cannot run without it"

requirements-completed: [FND-04, FND-06]

# Metrics
duration: ~13min (task execution, 3 commits)
completed: 2026-07-15
---

# Phase 101 Plan 03: 15 Missing Primitives + Calendar (react-day-picker pin) Summary

**16 net-new shadcn/Radix UI primitives (Select through Empty, including Command/cmdk and Calendar/react-day-picker) scaffolded in web/src/components/ui/ via the official CLI, with react-day-picker exact-pinned to 9.14.0 and zero modification to any of the 14 pre-existing hand-rolled components.**

## Performance

- **Duration:** ~13 min across 3 commits
- **Started:** 2026-07-15T21:00:48-01:00 (first commit)
- **Completed:** 2026-07-15T21:13:55-01:00 (last commit)
- **Tasks:** 3 (all `type="auto"`)
- **Files modified:** 19 (17 new component files, `package.json`, `pnpm-lock.yaml`)

## Accomplishments

- 14 of the 15 non-Calendar primitives (`select`, `native-select`, `tabs`, `dropdown-menu`, `tooltip`, `checkbox`, `avatar`, `separator`, `skeleton`, `progress`, `breadcrumb`, `accordion`, `navigation-menu`, `empty`) added in a single non-interactive `shadcn add` batch with zero overwrite conflicts
- `command` (the 15th) added via a move-aside/restore technique so its registry dependencies (`dialog`, and `input-group`'s own `button`/`input`/`textarea` deps) never touched the 4 corresponding hand-rolled files, while still landing `command.tsx` and the transitively-required `input-group.tsx` with correctly CLI-transformed local icon/import paths
- `calendar.tsx` added the same way (declining the `button` registry-dependency overwrite), then `react-day-picker` immediately re-pinned from the registry's broken `^10.0.1` resolution to an exact `9.14.0` (`--save-exact`, no caret) - confirmed via `package.json` and `pnpm ls`
- All 16 new primitives carry `data-slot`, import Radix from the unified `radix-ui` package (matching the Phase 101-02 base), and drive corners from semantic `rounded-*` classes (no hardcoded `rounded-none` outside legitimate variant-specific exceptions in `tabs.tsx`/`input-group.tsx`/`calendar.tsx`)
- `pnpm build` passes clean (exit 0) with all 16 primitives included in the production bundle across all 30 routes
- Zero modification to any of the 14 pre-existing hand-rolled `web/src/components/ui/*` files, verified via `git diff --stat` against the pre-plan commit

## Task Commits

Each task was committed atomically:

1. **Task 1: Add the 15 non-Calendar primitives via the shadcn CLI** - `7c8e843` (feat)
2. **Task 2: Add Calendar and immediately pin react-day-picker to 9.14.0** - `800c105` (feat)
3. **Task 3: Typecheck + production build gate fix (calendar.tsx)** - `8c3452a` (fix)

**Plan metadata:** commit pending (this SUMMARY + orchestrator-owned STATE.md/ROADMAP.md/REQUIREMENTS.md update)

## Files Created/Modified

- `web/src/components/ui/select.tsx` - Radix Select primitive
- `web/src/components/ui/native-select.tsx` - styled native `<select>` primitive
- `web/src/components/ui/tabs.tsx` - Radix Tabs primitive
- `web/src/components/ui/dropdown-menu.tsx` - Radix DropdownMenu primitive
- `web/src/components/ui/command.tsx` - cmdk-based Command palette, adapted to compile against the existing `dialog.tsx` (no `showCloseButton` prop)
- `web/src/components/ui/tooltip.tsx` - Radix Tooltip primitive
- `web/src/components/ui/checkbox.tsx` - Radix Checkbox primitive
- `web/src/components/ui/avatar.tsx` - Radix Avatar primitive
- `web/src/components/ui/separator.tsx` - Radix Separator primitive
- `web/src/components/ui/skeleton.tsx` - presentational loading-skeleton primitive
- `web/src/components/ui/progress.tsx` - Radix Progress primitive
- `web/src/components/ui/calendar.tsx` - react-day-picker Calendar, with a locally-mirrored `buttonVariants` and a `ButtonWithRef` cast so it compiles/functions without modifying `button.tsx`
- `web/src/components/ui/breadcrumb.tsx` - breadcrumb nav primitive
- `web/src/components/ui/accordion.tsx` - Radix Accordion primitive
- `web/src/components/ui/navigation-menu.tsx` - Radix NavigationMenu primitive
- `web/src/components/ui/empty.tsx` - empty-state primitive
- `web/src/components/ui/input-group.tsx` - transitive dependency of `command.tsx` (InputGroup/InputGroupAddon), not one of the 16 named primitives
- `web/package.json` - added `cmdk`; `react-day-picker` exact-pinned to `9.14.0`; `date-fns` added
- `web/pnpm-lock.yaml` - lockfile updated to match

## Decisions Made

- **`input-group.tsx` added as an unlisted 17th file:** `command.tsx`'s `CommandInput` hard-imports `InputGroup`/`InputGroupAddon`; without this file `command.tsx` cannot compile. It only depends on the already-existing `button`/`input`/`textarea` exports (not overwriting them), so adding it doesn't violate the "don't touch the 14 hand-rolled files" rule - it's a pure new file.
- **Move-aside/restore technique for registry-dependency conflicts:** interactive overwrite prompts (`The file X already exists. Would you like to overwrite? (y/N)`) could not be reliably scripted through this non-TTY shell (repeated attempts left the CLI process hung mid-batch). Instead, the 4 conflicting hand-rolled files (`button.tsx`, `input.tsx`, `textarea.tsx`, `dialog.tsx`) were temporarily renamed before running `add command`/`add calendar`, letting the CLI write fresh versions (plus the genuinely new `command.tsx`/`input-group.tsx`/`calendar.tsx`) with correctly-transformed local icon imports, then the CLI's fresh `button.tsx`/`input.tsx`/`textarea.tsx`/`dialog.tsx` were discarded and the originals restored byte-for-byte. Verified via `git diff --stat` showing zero changes to all 4 files.
- **`CommandDialog`'s `showCloseButton` prop removed:** the CLI-generated `command.tsx` passes `showCloseButton={showCloseButton}` to `DialogContent`, but the project's hand-rolled `DialogContent` has no such prop (it always renders its close button). Since `dialog.tsx` cannot be modified, the prop pass-through was removed from `command.tsx` instead - the command palette will always show a close button (a minor, documented behavior difference from upstream's hide-by-default), acceptable since no page consumes `Command` yet in this infrastructure-only phase.
- **`calendar.tsx`'s local `buttonVariants`/`ButtonWithRef`:** the hand-rolled `button.tsx` exports neither its internal `buttonVariants` CVA nor a forwardRef-wrapped `Button`, both of which the CLI-generated `calendar.tsx` needs (nav-button classNames computed from `buttonVariants({variant})`, and a ref for roving-tabindex keyboard focus in `CalendarDayButton`). Rather than exporting either from `button.tsx` (forbidden - `button.tsx` must stay byte-for-byte unchanged per Task 1/2/3's own acceptance criteria), `calendar.tsx` mirrors `buttonVariants` locally (documented as a bounded, explicitly-tracked duplication) and casts `Button` through a locally-typed `ButtonWithRef`. React 19 forwards `ref` through a component's props spread to the host `<button>` element regardless of `forwardRef` wrapping, so the roving-tabindex focus behavior works identically at runtime; only the TypeScript surface needed the cast.
- **Pre-existing `vitest` errors logged, not fixed:** `tsc --noEmit` reports 3 `Cannot find module 'vitest'` errors in test files created in Phase 97-02 (a different, already-shipped v2.11 milestone) - confirmed via `git log` that these predate this plan entirely and that `vitest` was never a `web/package.json` dependency. Per the Scope Boundary rule, these are out of scope; logged to `.planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/deferred-items.md` instead of fixed. `pnpm build` (the task's own stronger, bundler-exercising gate) passes clean with exit 0.
- **Created `web/.env.local`:** `pnpm build` throws `Error: BACKEND_API_ORIGIN is required` at config-load time without it (per `CLAUDE.md`, both env vars are validated at startup with no defaults). Populated with the documented dev defaults from `.env.example` (gitignored, not committed) so Task 3's build gate could actually run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `input-group.tsx` required but not in the plan's 16-file list**
- **Found during:** Task 1
- **Issue:** `shadcn add command` resolves `command`'s registryDependencies to `dialog` and `input-group`; `command.tsx`'s `CommandInput` function directly imports `InputGroup`/`InputGroupAddon` from `input-group.tsx` and will not compile without it.
- **Fix:** Added `input-group.tsx` as a genuinely new file (not an overwrite of anything) via the move-aside/restore technique described above.
- **Files modified:** `web/src/components/ui/input-group.tsx` (new)
- **Committed in:** `7c8e843`

**2. [Rule 3 - Blocking] Interactive overwrite prompts for `command`'s dependency chain could not be scripted non-interactively**
- **Found during:** Task 1
- **Issue:** `shadcn add command` triggers 4 sequential interactive "already exists, overwrite? (y/N)" prompts for `button.tsx`/`input.tsx`/`textarea.tsx`/`dialog.tsx`. Piping `n`/newline answers via `printf`/`timeout` left the CLI process hung partway through the prompt sequence in this non-TTY shell.
- **Fix:** Temporarily renamed the 4 conflicting hand-rolled files, ran `add command -y` (now a pure-add operation with zero conflicts), then discarded the CLI's fresh versions of those 4 files and restored the originals. Repeated for `add calendar` (only `button.tsx` conflicts there).
- **Files modified:** none of the 14 hand-rolled files (verified unchanged via `git diff --stat`)
- **Committed in:** `7c8e843` (command), `800c105` (calendar)

**3. [Rule 1 - Bug] `command.tsx` passes an unsupported `showCloseButton` prop to the existing `DialogContent`**
- **Found during:** Task 1 (spot-check read of generated `command.tsx`, confirmed by Task 3's typecheck)
- **Issue:** CLI-generated `CommandDialog` passes `showCloseButton={showCloseButton}` to `DialogContent`, but the hand-rolled `DialogContent`'s prop type (`React.ComponentProps<typeof DialogPrimitive.Content>`) has no such field.
- **Fix:** Removed the `showCloseButton` destructure/prop-forward from `CommandDialog` in `command.tsx`; the existing `DialogContent` always renders its close button unconditionally regardless.
- **Files modified:** `web/src/components/ui/command.tsx`
- **Committed in:** `7c8e843`

**4. [Rule 1 - Bug] `calendar.tsx` fails to typecheck: missing `buttonVariants` export and missing `ref` support on `Button`**
- **Found during:** Task 3 (`pnpm exec tsc --noEmit`)
- **Issue:** `calendar.tsx` imports `buttonVariants` from `@/components/ui/button` (not exported by the hand-rolled file) and passes `ref={ref}` to `<Button>` (a plain function component, not `forwardRef`-wrapped) for roving-tabindex keyboard focus in `CalendarDayButton` - both produced real `tsc` errors (`TS2459`, `TS2322`).
- **Fix:** Added a local `buttonVariants` CVA in `calendar.tsx` mirroring `button.tsx`'s exact variant/size classes (documented as a bounded duplication), and a locally-typed `ButtonWithRef` cast (`Button as unknown as React.ForwardRefExoticComponent<ButtonProps & React.RefAttributes<HTMLButtonElement>>`) used only inside `CalendarDayButton`. `button.tsx` itself was not modified.
- **Files modified:** `web/src/components/ui/calendar.tsx`
- **Verification:** `pnpm exec tsc --noEmit` no longer reports any error in `calendar.tsx`; `pnpm build` passes.
- **Committed in:** `8c3452a`

**5. [Rule 3 - Blocking] `pnpm build` fails without `BACKEND_API_ORIGIN`/`NEXT_PUBLIC_API_BASE_PATH`**
- **Found during:** Task 3 (`pnpm build`)
- **Issue:** `next.config.ts` throws `Error: BACKEND_API_ORIGIN is required` at config-load time; no `web/.env.local` existed in this worktree.
- **Fix:** Created `web/.env.local` (gitignored) with the documented dev defaults from `web/.env.example`.
- **Files modified:** `web/.env.local` (gitignored, not committed)
- **Committed in:** n/a (gitignored, never staged)

---

**Total deviations:** 5 auto-fixed (3 blocking/CLI-surface issues, 2 bugs — one CLI-generated type mismatch, one CLI-generated unsupported-prop mismatch)
**Impact on plan:** All five were necessary to complete Task 1/2/3 given the actual CLI/registry surface and this environment's non-TTY shell constraints. Every fix stayed scoped to the newly-added primitive files (`command.tsx`, `calendar.tsx`, `input-group.tsx`) or to environment setup (`.env.local`, gitignored) - zero modification to any of the 14 pre-existing hand-rolled components, verified via `git diff --stat` against the pre-plan commit. No scope creep.

## Issues Encountered

- `pnpm exec tsc --noEmit` reports 3 pre-existing `Cannot find module 'vitest'` errors in Phase 97-02 test files (a different, already-shipped v2.11 milestone), unrelated to this plan. This means the plan's literal `tsc --noEmit && pnpm build` verify chain fails at the first command - however, `pnpm build` run standalone (the task's own stronger, explicitly-justified gate, since "`tsc --noEmit` alone does NOT exercise the bundler") passes clean with exit 0, and zero of the 3 pre-existing errors are attributable to any of this plan's 16 new primitives (confirmed: after fixing `calendar.tsx`'s 2 real errors, only the 3 pre-existing `vitest` errors remain). Logged to `deferred-items.md` per the Scope Boundary rule rather than fixed, since fixing would require adding `vitest` as a dependency - an unrelated, larger test-infrastructure decision outside this plan's scope.
- Interactive `shadcn add` overwrite-decline prompts could not be reliably scripted in this non-TTY Git Bash environment (see Deviation #2) - worked around via the move-aside/restore technique rather than fighting the CLI's stdin handling further.

## User Setup Required

None - `web/.env.local` was created automatically with documented dev defaults (gitignored, not committed) to unblock the build gate; no external service configuration required.

## Next Phase Readiness

- FND-04 and FND-06 are satisfied by this plan (16 primitives present and building; `react-day-picker` exact-pinned to `9.14.0`) - REQUIREMENTS.md/STATE.md/ROADMAP.md updates are intentionally deferred to the orchestrator per this execution's parallel-wave instructions (this agent does not touch those shared files).
- All 16 new primitives are ready for Phase 102's reconciliation pass and for the module phases (104-109) that will actually consume them; none are wired into any page yet (no visible page changes, matching this phase's own scope).
- `command.tsx`'s always-visible close button (vs. upstream's hide-by-default) and `calendar.tsx`'s locally-mirrored `buttonVariants` are both documented, bounded deviations - a future phase that reconciles `button.tsx`/`dialog.tsx` onto the CLI's current registry versions (Phase 102) should re-check whether these local workarounds can be simplified once those hand-rolled files themselves adopt `showCloseButton`/`forwardRef` support.
- 3 pre-existing `vitest` module-resolution errors (Phase 97-02, unrelated) remain open in `deferred-items.md` for a future test-infrastructure decision.

---
*Phase: 101-funda-o-cli-init-e-design-tokens*
*Completed: 2026-07-15*

## Self-Check: PASSED

- FOUND: web/src/components/ui/select.tsx
- FOUND: web/src/components/ui/native-select.tsx
- FOUND: web/src/components/ui/tabs.tsx
- FOUND: web/src/components/ui/dropdown-menu.tsx
- FOUND: web/src/components/ui/command.tsx
- FOUND: web/src/components/ui/tooltip.tsx
- FOUND: web/src/components/ui/checkbox.tsx
- FOUND: web/src/components/ui/avatar.tsx
- FOUND: web/src/components/ui/separator.tsx
- FOUND: web/src/components/ui/skeleton.tsx
- FOUND: web/src/components/ui/progress.tsx
- FOUND: web/src/components/ui/calendar.tsx
- FOUND: web/src/components/ui/breadcrumb.tsx
- FOUND: web/src/components/ui/accordion.tsx
- FOUND: web/src/components/ui/navigation-menu.tsx
- FOUND: web/src/components/ui/empty.tsx
- FOUND: web/src/components/ui/input-group.tsx
- FOUND: .planning/phases/LEXCV-101-funda-o-cli-init-e-design-tokens/deferred-items.md
- FOUND: commit 7c8e843
- FOUND: commit 800c105
- FOUND: commit 8c3452a
