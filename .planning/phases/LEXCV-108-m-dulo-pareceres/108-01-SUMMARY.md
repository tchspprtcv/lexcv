---
phase: 108-m-dulo-pareceres
plan: 01
subsystem: ui
tags: [react, radix-select, shadcn, rbac, pareceres]

# Dependency graph
requires:
  - phase: 102-reconciliacao-design-system
    provides: Select primitive already installed and consumed by Financeiro/Agenda (Phase 106/107 precedent)
provides:
  - "6 Radix Select filters (3 quick + 3 advanced-search) in web/src/app/(dashboard)/pareceres/page.tsx"
  - "\"todos\" sentinel + onApply/onClear/onPesquisar/onLimparPesquisa translation for all 6 filters"
  - "permissions.isFetched RBAC gate fix for the Pareceres list view"
affects: [108-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "\"todos\" Select sentinel translated to empty-string/omitted filter at the handler boundary (matches Financeiro/Agenda precedent)"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/pareceres/page.tsx

key-decisions:
  - "No new dependencies — reused the Select primitive and \"todos\" sentinel pattern already established in Financeiro (Phase 106) and Agenda (Phase 107)"

patterns-established:
  - "Sixth-filter migration confirms the \"todos\" sentinel pattern generalizes cleanly across quick-filter and advanced-search forms in the same file"

requirements-completed: [PARC-18]

# Metrics
duration: ~10min
completed: 2026-07-17
---

# Phase 108 Plan 01: Pareceres List Filters → Radix Select Summary

**Migrated all 6 native `<select>` filter fields (3 quick filters + 3 advanced-search filters) in the Pareceres list page to Radix `Select` with a `"todos"` sentinel, and fixed the RBAC gate's pre-resolve render race (`!permissions.isLoading` → `permissions.isFetched`).**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-07-17T09:11Z (approx, base commit `88343be`)
- **Completed:** 2026-07-17T10:18Z
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- 3 quick filters (Estado/Advogado/Cliente, under the "Filtros" toggle) migrated to `Select`/`SelectTrigger`/`SelectContent`/`SelectItem`/`SelectValue`, each defaulting to a `"todos"` sentinel
- 3 advanced-search filters (Cliente/Advogado/Estado, under "Pesquisa Avançada") migrated the same way
- `onApply`/`onClear` (quick filters) and `onPesquisar`/`onLimparPesquisa` (advanced search) all updated so `"todos"` means "no filter" — zero behavioral regression versus the prior empty-string-based logic
- RBAC gate switched from `!permissions.isLoading && !canView` to `permissions.isFetched && !canView`, closing the same pre-resolve render race fixed in Phases 103/105/106/107
- Zero native `<select>` elements remain anywhere in `pareceres/page.tsx`

## Task Commits

Each task was committed atomically:

1. **Task 1: Quick filters (Estado/Advogado/Cliente) to Radix Select + todos sentinel + onApply/onClear translation + isFetched gate** - `56e0c47` (feat)
2. **Task 2: Advanced-search filters (Cliente/Advogado/Estado) to Radix Select + todos sentinel + onPesquisar/onLimparPesquisa translation** - `82aa673` (feat)

**Plan metadata:** (pending — final docs commit follows this summary)

## Files Created/Modified
- `web/src/app/(dashboard)/pareceres/page.tsx` - All 6 list filters migrated to Radix `Select`; RBAC gate now uses `permissions.isFetched`; `"todos"` sentinel translated to "no filter" in all 4 filter handlers

## Decisions Made
None - plan executed exactly as written. Reused the exact `Select` composition and `"todos"` sentinel pattern already verified in `financeiro/page.tsx` and `agenda/page.tsx` from prior phases.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. `pnpm exec tsc --noEmit` passed with zero errors after both tasks. `pnpm lint` could not run in this environment (`ESLint output ... ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL — Command "eslint" not found`) — this is a pre-existing environment/tooling gap, not caused by this plan's changes, and per the plan's own `<verification>` section, the holistic `pnpm build`/`pnpm lint` gate is explicitly owned by the Wave-2 plan (108-04), not this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `pareceres/page.tsx` is fully self-contained for this plan's scope (owns the file exclusively within Wave 1) — no dependency on 108-02/108-03.
- Ready for the Wave-2 holistic gate (108-04) to run `pnpm build`/`pnpm lint` across all Phase 108 changes together.

---
*Phase: 108-m-dulo-pareceres*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/pareceres/page.tsx
- FOUND: .planning/phases/LEXCV-108-m-dulo-pareceres/108-01-SUMMARY.md
- FOUND commit: 56e0c47
- FOUND commit: 82aa673
- FOUND commit: 55819f7
