---
phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
plan: 10
subsystem: ui
tags: [lucide-react, icons, accessibility, nextjs, react-hook-form]

# Dependency graph
requires: []
provides:
  - "Settings 'Guardar Utilizador' -> Save icon, 'Cancelar' -> X icon, 'Tentar novamente' -> RotateCcw icon"
  - "Settings close-X (user form dialog) gains aria-label=\"Fechar\" for accessible name"
  - "Dashboard 'Ver Agenda Completa' -> trailing ArrowRight icon"
  - "Login 'Entrar' -> LogIn icon, with Loader2 pending-swap while form is submitting"
affects: [115-phase-audit, ICON-01-requirement-closure]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Icon+text Button: icon element placed as first child before text, relying on Button's built-in gap-2 for spacing (no extra className needed)"
    - "Directional-forward icons (ArrowRight) placed trailing after text, matching setup/page.tsx precedent"
    - "Pending-state icon swap: {isSubmitting ? <Loader2 className=\"h-4 w-4 animate-spin\" /> : <ActionIcon className=\"h-4 w-4\" />} reused from settings/page.tsx's existing 'Guardar Regras' pattern"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/settings/page.tsx"
    - "web/src/app/(dashboard)/dashboard/page.tsx"
    - "web/src/app/(auth)/login/page.tsx"

key-decisions:
  - "ArrowRight placed trailing (after text) on Dashboard's 'Ver Agenda Completa', inside the asChild Link, matching the only other ArrowRight precedent in the codebase (setup/page.tsx's 'Concluir configuração')"
  - "Applied Login's optional Loader2 pending-swap (plan-sanctioned, not mandatory) since form.formState.isSubmitting was already wired into the button's disabled prop — no new state introduced"
  - "Applied Settings' optional close-X aria-label=\"Fechar\" fix (plan-sanctioned, not mandatory) since it was nearly free and closes a real WCAG 4.1.2 gap (T-115-A11Y in the plan's threat model)"

patterns-established: []

requirements-completed: [ICON-01]

# Metrics
duration: ~20min
completed: 2026-07-22
---

# Phase 115 Plan 10: Settings/Dashboard/Login Icon Gaps Summary

**Closed the last 3 top-level-misc ICON-01 gaps (Settings Guardar/Cancelar/Tentar novamente, Dashboard Ver Agenda Completa, Login Entrar) by reusing already-imported or newly-added lucide-react icons, plus two plan-sanctioned optional a11y/UX completions (close-X aria-label, Login pending-spinner).**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-22T01:34:00Z (approx.)
- **Completed:** 2026-07-22T01:54:16Z
- **Tasks:** 2 completed
- **Files modified:** 3

## Accomplishments
- Settings: `Guardar Utilizador` -> `Save`, `Cancelar` -> `X`, `Tentar novamente` -> `RotateCcw` (only `RotateCcw` was a new import; `Save`/`X` were reused from the already-imported set); close-X gained `aria-label="Fechar"`.
- Dashboard: `Ver Agenda Completa` -> trailing `ArrowRight`, added inside the `asChild` `Link` so the icon renders alongside the anchor text.
- Login: `Entrar` -> `LogIn`, with a `Loader2` spin-swap while `form.formState.isSubmitting` is true (reusing the exact pending-state pattern already established in `settings/page.tsx`'s "Guardar Regras" button).
- Zero changes to any of the pre-existing compliant icons in these files (Settings' `Plus`/`Edit`/`Trash2`/`Save`+`Loader2`; Dashboard's `Plus`).

## Task Commits

Each task was committed atomically:

1. **Task 1: Settings — Guardar/Cancelar/Tentar novamente + close-X aria-label** - `a2631e2` (feat)
2. **Task 2: Dashboard + Login** - `98a9ac5` (feat)

**Plan metadata:** committed alongside this SUMMARY.md (see below)

## Files Created/Modified
- `web/src/app/(dashboard)/settings/page.tsx` - Added `RotateCcw` import; iconed Guardar Utilizador/Cancelar/Tentar novamente; added `aria-label="Fechar"` to the close-X
- `web/src/app/(dashboard)/dashboard/page.tsx` - Added `ArrowRight` import; iconed "Ver Agenda Completa" (trailing icon inside the Link)
- `web/src/app/(auth)/login/page.tsx` - Added `LogIn`/`Loader2` imports (first lucide-react import in this file); iconed "Entrar" with a pending-state Loader2 swap

## Decisions Made
- ArrowRight icon placement: trailing (after text), matching the only existing ArrowRight precedent (`setup/page.tsx` "Concluir configuração") rather than the leading-icon convention used by most other action buttons — directional/forward-navigation icons read better trailing.
- Applied both plan-flagged "optional, nearly free" completions (Settings close-X `aria-label`, Login `Loader2` pending-swap) since both preconditions were already met in the code (an unlabeled icon-only button; an `isSubmitting` boolean already wired to `disabled`) and neither required new state or structural changes.

## Deviations from Plan

None — plan executed exactly as written, including its two explicitly-optional recommended additions (Settings close-X `aria-label`, Login `Loader2` pending-swap), both of which were pre-conditioned on state already present in the code and required no new state.

## Issues Encountered

1. **Worktree branch was stale at spawn time (pre-execution, resolved before Task 1).** This worktree's branch (`worktree-agent-ad72930cf663d7314`) was created pointing at commit `70ff067`, which predates every Phase 115 planning commit — `.planning/phases/LEXCV-115-.../115-10-PLAN.md` (and the CONTEXT/UI-SPEC docs) did not exist in the checkout. Verified `git merge-base HEAD master` equaled `HEAD` exactly (a pure ancestor relationship, zero divergent local commits, clean working tree), confirming a lossless fast-forward was safe. Reset to local `master`'s tip (`776defc`, the commit most sibling worktrees were already using as their wave base) per this session's explicit sanction to correct base drift via `git reset --hard` after the worktree-branch-type assertion passes. Re-verified HEAD remained on the correct `worktree-agent-*` branch (not detached, not a protected ref) after the reset.
2. **`web/node_modules` was absent in this worktree (pre-execution, resolved before verification).** Worktrees don't carry `node_modules` (gitignored, not part of git). Ran `pnpm install --frozen-lockfile` in `web/`, which completed quickly via the shared pnpm content-addressable store (no fresh downloads needed), enabling `pnpm lint` to run for verification.
3. **UI-SPEC audit inaccuracy found, not fixed (out of this plan's scope).** `115-UI-SPEC.md` classifies `dashboard/page.tsx`'s row-action "Abrir" button (table row for recent processos, originally cited as "L588 Eye-pattern row action") as already-compliant/icon-bearing. Direct inspection shows this button (`<Link href={...}>Abrir</Link>` inside an `asChild` `Button`, currently at L591-598) has **no icon at all** — text-only. `git log` confirms no Phase 115 commit has touched this file before this plan, so this is a genuine pre-existing audit discrepancy, not a stale-line-number artifact from other parallel work. Per the plan's explicit instruction ("Do not touch L91 Plus / L588 Eye-pattern (compliant)") and this plan's `must_haves` (which only cover "Ver Agenda Completa"), this button was left untouched — fixing it was out of this plan's declared scope and risks overlapping with another phase-115 plan's ownership of that file/button. Flagging for the phase-level audit/review step.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All 3 files in this plan's scope (`settings/page.tsx`, `dashboard/page.tsx`, `login/page.tsx`) are fully ICON-01-compliant for their listed gaps; ready for the phase-level do-not-regress spot-check referenced in the plan's `<verification>` section.
- `pnpm lint` confirmed zero new errors/warnings introduced by this plan across all 3 files (Settings retains one unrelated pre-existing warning at L406, an `<img>` element unrelated to icons/buttons, untouched by this plan).
- Flag for phase-level audit: the `dashboard/page.tsx` "Abrir" row-action button (see Issues Encountered #3) appears to be a genuine ICON-01 gap mis-classified as compliant in `115-UI-SPEC.md` — needs a scope decision (which plan/pass owns it) before phase close.

---
*Phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only*
*Plan: 10*
*Completed: 2026-07-22*

## Self-Check: PASSED

All 3 modified files confirmed present on disk; all 3 commits (`a2631e2`, `98a9ac5`, `498574d`) confirmed present in `git log --oneline --all`. No missing items.
