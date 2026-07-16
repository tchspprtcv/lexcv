---
phase: 106-m-dulo-agenda
plan: 03
subsystem: ui
tags: [react, nextjs, react-hook-form, native-select, date-picker-field, rbac]

# Dependency graph
requires:
  - phase: 106-01
    provides: Shared DatePickerField (Popover+Calendar composition) at web/src/components/shared/date-picker-field.tsx
  - phase: 105-clientes-processos
    provides: NativeSelect-for-RHF-forms convention, permissions.isFetched RBAC-fix precedent
provides:
  - Edit-Evento form (agenda/[id]/editar) migrated off native <select> onto NativeSelect
  - Edit-Evento form's dataInicio/dataFim wired to the shared DatePickerField via Controller
  - RBAC isFetched fix landed on agenda/[id]/editar/page.tsx (4th and last Agenda file to receive it)
affects: [phase-107, phase-108, phase-109]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Consumed 106-01's DatePickerField unchanged (no prop/shape changes needed) -- confirms the shared component's contract works for a second, independently-planned call site"

key-files:
  modified:
    - web/src/app/(dashboard)/agenda/[id]/editar/page.tsx

key-decisions:
  - "Left the pre-existing form.reset() line `recurrenceEndDate: evento.data.recurrenceEndDate ?? undefined` untouched -- it predates this plan (confirmed via git show against the wave's base commit), the plan explicitly forbids touching form.reset, and this form has no recurrenceEndDate UI field to remove it from"

patterns-established: []

requirements-completed: [AGD-36]

# Metrics
duration: ~15min
completed: 2026-07-16
---

# Phase 106 Plan 03: Edit-Evento Form — NativeSelect + Shared DatePickerField Summary

**Migrated `agenda/[id]/editar/page.tsx`'s 3 RHF selects to `NativeSelect` and its 2 date+time fields to the Phase-106-01 shared `DatePickerField` via `Controller`, closing AGD-36's edit-form half; RBAC race fixed as bundled cleanup.**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-16T21:28:22-01:00
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- Fixed the RBAC access-gate race (`!permissions.isLoading` → `permissions.isFetched`) at line 65, matching the precedent from Phases 103/105 and the 3 other Agenda files fixed in 106-01
- Migrated the 3 RHF-bound `<select>` elements (`processoId`, `tipo`, `prioridade`) to `NativeSelect` with `size="default" className="w-full"`, preserving every `<option>` verbatim including the dynamic processoId placeholder/disabled logic
- Removed the now-unused `const selectClassName` string; kept `const textareaClassName` (still used by the `descricao` textarea)
- Replaced both `<Input type="datetime-local">` fields (`dataInicio`, `dataFim`) with the shared `DatePickerField` (from 106-01) wired via `Controller`, `withTime` on both
- Deliberately did **not** add any recurrence UI — this form has none today and 106-CONTEXT.md/UI-SPEC Scope note #1 defers it
- `pnpm build` green; `pnpm lint` reports zero issues on the modified file (all pre-existing lint findings elsewhere in the codebase are out of scope)

## Task Commits

Each task was committed atomically:

1. **Task 1: RBAC fix + 3 NativeSelect + drop selectClassName** - `9e3a106` (fix)
2. **Task 2: dataInicio/dataFim → DatePickerField via Controller (no recurrence field)** - `0798a35` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx` - RBAC gate fixed, 3 selects → NativeSelect, dataInicio/dataFim → shared DatePickerField via Controller, selectClassName removed

## Decisions Made
- Kept the pre-existing `recurrenceEndDate: evento.data.recurrenceEndDate ?? undefined` line inside `form.reset()` completely untouched. This line predates this plan (verified via `git show` against the wave's base commit `ba896e3`) — it silently populates an RHF field that has no corresponding UI in this form (a pre-existing quirk, not introduced here). The plan's own `<action>` text explicitly says "Do NOT change ... form.reset ... or onSubmit," so removing it would have violated an explicit plan instruction to touch code outside this task's scope. See "Deviations from Plan" below for how this affects the plan's automated verify gate.

## Deviations from Plan

### Noted script/reality mismatch (not a deviation, no fix applied)

**Task 2's automated verify command includes `grep -c "recurrenceEndDate" ... | grep -qx 0`, but the file already contained one `recurrenceEndDate` reference (in `form.reset()`, line 111) before this plan touched anything.**
- **Found during:** Task 2 verification
- **Root cause:** `git show ba896e3:"web/src/app/(dashboard)/agenda/[id]/editar/page.tsx"` (the wave's base commit, prior to any 106-03 work) shows this exact line already present. The plan author's acceptance criteria ("No recurrenceEndDate reference is added ... grep count stays 0") assumed a baseline of 0 that was never actually true.
- **Action taken:** None — per the plan's own explicit instruction not to touch `form.reset`, and because the intent of the acceptance criteria (no new recurrenceEndDate *UI field*) is fully satisfied: no `<Controller name="recurrenceEndDate">`/date-only DatePickerField/select was added anywhere in this form.
- **Verification performed manually:** `pnpm build` passes; `datetime-local` count is 0; `DatePickerField`/`Controller` present; no recurrence UI (select, label, or DatePickerField call) exists for `recurrenceEndDate` anywhere in the file — only the pre-existing dead `form.reset()` assignment remains, unchanged.
- **Files modified:** None (no fix needed/applied).

**Total deviations:** 0 auto-fixed; 1 documented script-vs-baseline discrepancy (no code change).
**Impact on plan:** None on functionality — the substantive acceptance criterion (no recurrence UI added to this form) holds true. Recommend the plan author update the automated grep gate in a future revision to scope it to newly-added UI rather than a blind file-wide count, since the field's dead `form.reset()` reference already existed independently of this phase.

## Issues Encountered
- `pnpm build` initially failed with `node_modules` missing in the worktree checkout (a known pattern documented in STATE.md from Phase 101: worktree installs don't propagate to the main checkout, and vice versa — each worktree needs its own `pnpm install`). Ran `pnpm install` in `web/` to resolve; all declared dependencies (already correct in `package.json`/lockfile) installed cleanly with no version changes.
- `pnpm build` then failed on `BACKEND_API_ORIGIN is required` because `web/.env.local` doesn't exist in a fresh worktree checkout (gitignored, per `web/.gitignore` `.env*` / `!.env.example`). Copied `web/.env.example` to `web/.env.local` (same values used across the whole project's dev setup, not a secret) to unblock the build-only verification step. This file remains gitignored and was never staged/committed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- AGD-36 is now fully closed across both Agenda forms: `agenda/novo/page.tsx` (106-02, parallel Wave 2 plan) and `agenda/[id]/editar/page.tsx` (this plan) both consume the shared `DatePickerField` built in 106-01, confirming its contract generalizes across two independently-planned call sites with zero changes to the shared component itself.
- All 4 Agenda-module files (`page.tsx`, `novo/page.tsx`, `[id]/page.tsx`, `[id]/editar/page.tsx`) now use the `permissions.isFetched` RBAC-fix pattern — no remaining `!permissions.isLoading && !canX` access-gate race in the Agenda module.
- No blockers for Wave 3 / phase close-out.

---
*Phase: 106-m-dulo-agenda*
*Completed: 2026-07-16*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/agenda/[id]/editar/page.tsx
- FOUND commit: 9e3a106
- FOUND commit: 0798a35
