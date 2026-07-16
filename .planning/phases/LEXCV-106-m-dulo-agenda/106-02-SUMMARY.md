---
phase: 106-m-dulo-agenda
plan: 02
subsystem: ui
tags: [react, nextjs, react-hook-form, native-select, react-day-picker, date-fns, rbac]

# Dependency graph
requires:
  - phase: 106-01
    provides: Shared DatePickerField (Popover+Calendar composition) at web/src/components/shared/date-picker-field.tsx
provides:
  - Create-Evento form (agenda/novo/page.tsx) migrated to NativeSelect (4 selects) and shared DatePickerField (3 date fields), closing AGD-36's create-form share
  - RBAC isFetched fix applied to agenda/novo/page.tsx
affects: [106-03, phase-107, phase-108, phase-109]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DatePickerField consumed via Controller for RHF fields that render a Button+Calendar, not a native input (mirrors clientes/novo/page.tsx's existing Controller idiom)"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/agenda/novo/page.tsx

key-decisions:
  - "None beyond the plan's own bundled RBAC fix — executed exactly as specified"

patterns-established: []

requirements-completed: [AGD-36]

# Metrics
duration: ~15min
completed: 2026-07-16
---

# Phase 106 Plan 02: Create-Evento Form — NativeSelect + DatePickerField Summary

**Migrated agenda/novo/page.tsx's 4 RHF selects to NativeSelect and its 3 date fields (dataInicio/dataFim with time, recurrenceEndDate date-only) to the shared Popover+Calendar DatePickerField built in 106-01, closing the create-form half of AGD-36.**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-16T22:25:55Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Fixed the RBAC access-gate race on `agenda/novo/page.tsx` (`!permissions.isLoading` → `permissions.isFetched`), matching the precedent from Phases 103/105/106-01; left the submit-button's `permissions.isLoading` disable-guard untouched per plan instruction
- Migrated all 4 RHF-bound `<select>` elements (`processoId`, `tipo`, `prioridade`, `recurrenceRule`) to `NativeSelect` (`size="default"`, `className="w-full"`), preserving every `<option>` child verbatim including the dynamic processoId placeholder and its `disabled` prop
- Deleted `const selectClassName`; kept `const textareaClassName` (still used by the `descricao` `<textarea>`)
- Replaced the 3 native date `<Input>` elements (`dataInicio`/`dataFim` `datetime-local`, `recurrenceEndDate` `date`) with the shared `DatePickerField` (from 106-01), wired via `Controller` — `dataInicio`/`dataFim` use `withTime`, `recurrenceEndDate` stays date-only and remains gated inside the `recurrenceRule !== "NONE"` conditional
- `pnpm build` green (24/24 routes compiled, including `/agenda/novo`)

## Task Commits

Each task was committed atomically:

1. **Task 1: RBAC fix + 4 NativeSelect + drop selectClassName** - `b7d7f66` (feat)
2. **Task 2: 3 date fields → DatePickerField via Controller** - `58c26e1` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/agenda/novo/page.tsx` - RBAC `isFetched` fix; 4 native `<select>` → `NativeSelect`; 3 date `<Input>` → `DatePickerField` via `Controller`; `selectClassName` removed, `textareaClassName` retained

## Decisions Made
None beyond the plan's own bundled RBAC fix — executed exactly as specified. No architectural changes, no new dependencies.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Hydrated worktree `node_modules` and `.env.local` to run the required `pnpm build` gate**
- **Found during:** Task 2 verification
- **Issue:** This worktree checkout had no `node_modules` (worktrees don't inherit it from the main checkout — the same class of issue recorded in PROJECT.md's Phase 101 decision log) and no `web/.env.local`, so `pnpm build` failed before ever reaching the code under test (`'next' is not recognized`, then `Error: BACKEND_API_ORIGIN is required`).
- **Fix:** Ran `pnpm install` (hydrates `node_modules` strictly from the existing `pnpm-lock.yaml` — zero new/changed dependencies, not a package-install decision) and copied the existing, already-gitignored `web/.env.local` from the main checkout into the worktree (same non-secret local dev values already used by every other session in this repo).
- **Files modified:** none tracked by git (`node_modules` is gitignored; `.env.local` is gitignored — confirmed via `git check-ignore -v .env.local` before proceeding, neither is staged or committed)
- **Verification:** `pnpm build` subsequently completed successfully, compiling all 24 routes including `/agenda/novo`
- **Committed in:** N/A (no tracked files changed by this fix)

---

**Total deviations:** 1 auto-fixed (1 blocking, environment-only)
**Impact on plan:** No scope creep — purely local environment setup required to execute the plan's own mandated `pnpm build` verification gate. No code, dependency manifest, or lockfile changes.

## Issues Encountered
None beyond the environment-setup deviation documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- AGD-36's create-form half is now closed; `agenda/[id]/editar/page.tsx` (106-03, parallel wave) covers the edit-form half (`dataInicio`/`dataFim` only, no recurrence field there).
- No blockers for downstream Agenda work.

---
*Phase: 106-m-dulo-agenda*
*Completed: 2026-07-16*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/agenda/novo/page.tsx
- FOUND commit: b7d7f66
- FOUND commit: 58c26e1
