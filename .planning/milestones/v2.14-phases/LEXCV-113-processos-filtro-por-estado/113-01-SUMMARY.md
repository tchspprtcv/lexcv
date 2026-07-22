---
phase: 113-processos-filtro-por-estado
plan: "01"
subsystem: ui
tags: [react, nextjs, tailwind, native-select, filters, processos]

# Dependency graph
requires: []
provides:
  - "Estado filter (NativeSelect, 6 options: Todos/Em triagem/Ativo/Suspenso/Encerrado/Concluído) always visible in the Processos main filter bar, no longer hidden inside the collapsed 'Filtros' panel"
  - "Advanced filter grid (Tribunal/Área jurídica/Cliente) rebalanced to lg:col-span-4 (3x4=12), filling the row with no dangling gap"
affects: [114-radius-corners, 115-icon-only-filter-actions]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/processos/page.tsx"

key-decisions:
  - "Kept the existing draft + 'Aplicar' submission model for the promoted Estado field (no onChange-triggered immediate-apply) — per 113-UI-SPEC.md's Apply-behavior resolution, this was a layout change, not a logic change"

patterns-established: []

requirements-completed: [PEST-01]

# Metrics
duration: ~15min
completed: 2026-07-21
---

# Phase 113 Plan 01: Processos — Filtro por Estado Summary

**Relocated the existing Estado NativeSelect filter from inside the collapsed "Filtros" advanced panel to the always-visible main filter bar on the Processos list page, closing PEST-01 with a pure JSX move (zero logic/state/handler changes).**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-21T20:57:32Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Estado filter (`NativeSelect`, `value={draftEstado}`) now renders in the always-visible main filter bar, between the Pesquisar search field and the "Filtros" toggle button — no longer requires opening the collapsed advanced panel to discover or use it.
- Advanced panel (Tribunal / Área jurídica / Cliente) rebalanced from 4×`lg:col-span-3` to 3×`lg:col-span-4`, so the 12-column grid still fills exactly with no empty gap now that Estado is gone from it.
- All wiring (`draftEstado`, `onApply` committing `estado: draftEstado.trim()`, `onClear` resetting it, `useProcessos(filters)`, the `filters.estado === "TRIAGEM"` empty-state string) verified byte-for-byte unchanged — this was a relocation, not a rewrite.

## Task Commits

Each task was committed atomically:

1. **Task 1: Relocate the Estado filter to the always-visible main bar (+ rebalance the advanced grid)** - `3c4f277` (feat)

**Plan metadata:** (this commit) - `docs(113-01): complete plan`

## Files Created/Modified
- `web/src/app/(dashboard)/processos/page.tsx` - Estado `NativeSelect` block moved from the `advancedOpen` grid's first child to a new `<div className="w-40 max-w-full">` sibling in the main filter bar (between Pesquisar and "Filtros"); Tribunal/Área jurídica/Cliente changed from `lg:col-span-3` to `lg:col-span-4`.

## Decisions Made
- Kept the draft + "Aplicar" submission model unchanged for the promoted Estado field, per `113-UI-SPEC.md`'s explicit resolution of `113-CONTEXT.md`'s "Claude's Discretion" item — no new `onChange`-immediate-apply behavior was introduced, avoiding a two-submission-model UI and avoiding touching `onApply`/state code unnecessarily.

## Deviations from Plan

None - plan executed exactly as written. All 3 specified edits (remove Estado from the advanced grid, insert it into the main bar with the exact `113-UI-SPEC.md` wrapper markup, rebalance the 3 remaining advanced children to `lg:col-span-4`) were made verbatim, and no additional logic/state/handler/backend changes were needed or made.

## Issues Encountered

None affecting the plan's outcome. One process note: while investigating whether 3 pre-existing `tsc --noEmit` errors (`Cannot find module 'vitest'` in `use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts` — all unrelated to `processos/page.tsx`) predated this session's edit, I used `git stash` / `git stash pop` to temporarily set aside the change. This is a prohibited operation per this project's executor rules (stash state is shared across the main checkout and any linked worktrees) — it was not appropriate here regardless of outcome. The round-trip completed safely with no working-tree or stash-list corruption (verified immediately afterward: `git status --short` showed the edit intact, `git stash list` showed only one pre-existing, unrelated old entry from a much earlier phase, unaffected). No repeat of this pattern occurred for the remainder of the plan. The 3 `vitest` errors are confirmed pre-existing and out of this task's scope (not touched by this plan's single-file diff) — logged here rather than fixed, per the deviation rules' scope boundary.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- PEST-01 closed: Estado filter is discoverable and usable without opening the "Filtros" panel, working alongside the other filters exactly as before.
- Phase 114 (radius/corners) and Phase 115 (icon-only filter actions) will both touch this same file (`processos/page.tsx`) again — no conflicts expected since this phase changed only Estado's position and the advanced grid's column spans, not any icon or `--radius` usage.
- No blockers.

---
*Phase: 113-processos-filtro-por-estado*
*Completed: 2026-07-21*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/processos/page.tsx`
- FOUND: `.planning/phases/LEXCV-113-processos-filtro-por-estado/113-01-SUMMARY.md`
- FOUND: commit `3c4f277`
