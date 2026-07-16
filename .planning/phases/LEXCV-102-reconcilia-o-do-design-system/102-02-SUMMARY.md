---
phase: 102-reconcilia-o-do-design-system
plan: 02
subsystem: ui
tags: [shadcn, tailwind, design-tokens, dark-mode, card, dialog, alert-dialog, popover, table, sheet]

# Dependency graph
requires:
  - phase: 101-funda-o-cli-init-e-design-tokens
    provides: "components.json (radix-vega preset), --card/--popover/--muted semantic tokens in globals.css, --radius:0rem"
provides:
  - "card/dialog/alert-dialog/popover reconciled onto --card/--popover tokens (dark-mode elevation fix)"
  - "rounded-none -> rounded-lg tokenized radius on card/dialog/alert-dialog"
  - "table.tsx footer/row-hover/selected converged to --muted token"
  - "sheet.tsx bg-white/dark:bg-neutral-950 converged to bg-popover (hand-edit only, structure frozen)"
affects: [102-04-visual-checkpoint, future-module-phases-consuming-card-dialog-table-sheet]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Diff-first reconciliation: pnpm dlx shadcn@latest add <component> --diff (preview only) followed by targeted hand-edits, never blind --overwrite"
    - "Rule A (identical-pixel token swap), Rule B (intentional dark-mode elevation change), Rule C (preserve component identity) classification applied per 102-UI-SPEC.md"

key-files:
  created: []
  modified:
    - web/src/components/ui/card.tsx
    - web/src/components/ui/popover.tsx
    - web/src/components/ui/dialog.tsx
    - web/src/components/ui/alert-dialog.tsx
    - web/src/components/ui/table.tsx
    - web/src/components/ui/sheet.tsx

key-decisions:
  - "table.tsx: applied the optional Rule-B convergence (bg-neutral-50/dark:bg-neutral-900/30 on TableFooter, hover/selected on TableRow -> bg-muted/50 / bg-muted) — visually low-risk per 102-UI-SPEC.md, kept TableCaption/TableHead/TableCell text colors and all data-slot attributes untouched"
  - "sheet.tsx: converged dark:bg-neutral-950 (a third distinct literal) to dark:bg-popover, matching dialog/card's newly-adopted elevated surface, per the plan's recommended discretion — bg-white->bg-popover applied as an exact-match Rule A swap; file was hand-edited (single line changed), never re-scaffolded via --overwrite"
  - "dialog.tsx: did not adopt the registry's new showCloseButton prop — grep confirmed zero call sites manually render a close affordance inside DialogContent; kept today's always-visible close button"

requirements-completed: [DSR-01, DSR-02]

# Metrics
duration: ~18min
completed: 2026-07-16
---

# Phase 102 Plan 02: Reconcile Surface Primitives (card/popover/dialog/alert-dialog/table/sheet) Summary

**Tokenized the 6 "surface" shadcn primitives onto `--card`/`--popover`/`--muted` semantic tokens, replacing the flat `dark:bg-[#020617]`/`dark:bg-slate-950` magic hex that gave zero dark-mode elevation contrast, while preserving `rounded-lg` visual identity and every accessibility label (Fechar) and Rule-C button-color identity (AlertDialogAction/Cancel) verbatim.**

## Performance

- **Duration:** ~18 min (including `pnpm install` in the isolated worktree, ~5 min)
- **Tasks:** 3 completed
- **Files modified:** 6 (+ 1 gitignored `.env.local` created for the local build gate, not committed)

## Accomplishments

- `card.tsx`/`dialog.tsx`/`alert-dialog.tsx`: `rounded-none` → `rounded-lg` (Rule A, identical pixel via `--radius:0rem`), `bg-white` → `bg-card`/`bg-popover` (Rule A exact match), `dark:bg-[#020617]` → `dark:bg-card`/`dark:bg-popover` (Rule B — the intentional dark-mode elevation fix, flagged for the 102-04 human visual checkpoint, not auto-signed-off here)
- `popover.tsx`: `dark:bg-slate-950` → `dark:bg-popover` (Rule B, same elevation reasoning — `slate-950` hex is literally `#020617`)
- `alert-dialog.tsx`: `AlertDialogAction`'s `bg-neutral-900` and `AlertDialogCancel`'s outline styling preserved byte-for-byte (Rule C) — no retargeting to `--primary`/`--destructive`
- `table.tsx`: optional Rule-B convergence applied — `TableFooter`/`TableRow` neutral-scale literals → `bg-muted/50`/`bg-muted`; `TableCaption`/`TableHead`/`TableCell` text colors (Rule C) untouched
- `sheet.tsx`: `bg-white` → `bg-popover` (Rule A exact match); `dark:bg-neutral-950` → `dark:bg-popover` (discretionary convergence, recommended for consistency with dialog/card); file hand-edited only (one line changed), never re-scaffolded; `sr-only` "Fechar" label and `sideVariants` preserved verbatim
- `pnpm build` verified green — all 24 routes compile/typecheck against the reconciled components

## Task Commits

Each task was committed atomically:

1. **Task 1: Reconcile card.tsx and popover.tsx** - `05c9f83` (feat)
2. **Task 2: Reconcile dialog.tsx and alert-dialog.tsx** - `9de87db` (feat)
3. **Task 3: Reconcile table.tsx and sheet.tsx, verify build** - `3852ed7` (feat)

**Plan metadata:** (this commit, following SUMMARY.md creation)

## Files Created/Modified

- `web/src/components/ui/card.tsx` - Card root: tokenized radius + surface (Rule A + B)
- `web/src/components/ui/popover.tsx` - PopoverContent: tokenized dark surface (Rule B)
- `web/src/components/ui/dialog.tsx` - DialogContent: tokenized radius + surface (Rule A + B); Fechar label preserved
- `web/src/components/ui/alert-dialog.tsx` - AlertDialogContent: tokenized radius + surface (Rule A + B); Action/Cancel colors preserved (Rule C)
- `web/src/components/ui/table.tsx` - TableFooter/TableRow: converged to `--muted` token (optional Rule B)
- `web/src/components/ui/sheet.tsx` - SheetContent: `bg-popover` (Rule A) + discretionary dark convergence (Rule B); hand-edited only

## Decisions Made

- Applied the table.tsx optional Rule-B muted convergence (see frontmatter `key-decisions`) — recommend the 102-04 checkpoint explicitly confirm this reads correctly against real row data in both themes.
- Applied sheet.tsx's discretionary `dark:bg-popover` convergence rather than leaving the third distinct `dark:bg-neutral-950` literal — recommend the 102-04 checkpoint confirm this visually alongside dialog/card.
- Did not add `showCloseButton` to `dialog.tsx` — confirmed via grep that no call site (`agenda/[id]`, `clientes/novo`, `clientes/[id]`, `financeiro/[id]`, `pareceres/[id]`, `processos/[id]`) manually renders its own `DialogContent` close affordance.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created worktree-local `web/.env.local` to unblock the `pnpm build` gate**
- **Found during:** Task 3 (build verification)
- **Issue:** `next.config.ts` throws `BACKEND_API_ORIGIN is required` at build time; the isolated worktree checkout has no `.env.local` (gitignored, not synced by worktree creation — same class of environment gap Phase 101 documented for `node_modules`).
- **Fix:** Created `web/.env.local` with the same dev-default values as `web/.env.example` (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`). No secret values involved — these are the documented local-dev defaults.
- **Files modified:** `web/.env.local` (gitignored, not committed — confirmed via `git check-ignore`)
- **Verification:** `pnpm build` completed successfully, all 24 routes compiled.

**2. [Rule 3 - Blocking] Ran `pnpm install` in the worktree before diffing/building**
- **Found during:** Start of Task 1
- **Issue:** `web/node_modules` was absent in the isolated worktree checkout (confirmed known gap from Phase 101's Key Decisions — worktree creation does not sync `node_modules`).
- **Fix:** Ran `pnpm install` in `web/` per the plan's own Task 3 note anticipating this exact scenario.
- **Files modified:** none (lockfile already correct; install just materialized `node_modules`)
- **Verification:** `pnpm dlx shadcn@latest add <component> --diff` and `pnpm build` both ran successfully afterward.

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking, both anticipated by the plan's own notes referencing Phase 101's worktree lesson)
**Impact on plan:** No scope creep — both fixes are environment-only prerequisites for running the verification the plan itself mandates (`pnpm build`), not code changes to reconciled behavior.

## Issues Encountered

None beyond the two auto-fixed environment gaps above.

## Known Stubs

None — this plan is a pure styling/token reconciliation with no new data-fetching or UI wiring surfaces.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- 6 surface components (card, popover, dialog, alert-dialog, table, sheet) reconciled and building green; ready for 102-04's mandatory human visual checkpoint (light+dark) to sign off on the Rule-B elevation changes and the two discretionary convergences (table muted, sheet dark surface) flagged above.
- No blockers. Runs in parallel with 102-01 (button/badge/input/label/radio-group/switch/textarea/calendar/breadcrumb/package.json) and 102-03 (providers.tsx/clientes/settings/dashboard-shell) — zero file overlap confirmed, this plan touched exactly the 6 files declared in its `files_modified` frontmatter.

---
*Phase: 102-reconcilia-o-do-design-system*
*Completed: 2026-07-16*
