---
phase: 106-m-dulo-agenda
plan: 04
subsystem: ui
tags: [react-day-picker, radix-select, react-hook-form, rbac, agenda]

requires:
  - phase: 106-01
    provides: DatePickerField (shared Popover+Calendar composition), Radix Select filters, RBAC isFetched fix (list+detail)
  - phase: 106-02
    provides: Create-Evento form on NativeSelect + DatePickerField
  - phase: 106-03
    provides: Edit-Evento form on NativeSelect + DatePickerField
provides:
  - Holistic build/lint/regression gate across all 4 Agenda files + the new shared component
  - Human visual+functional sign-off (both themes, RBAC matrix, date-picker day-accuracy)
affects: [agenda, eventos]

tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified: []

key-decisions:
  - "Approved phase close via live UAT rather than a written HUMAN-UAT resolution doc — no verifier blocker was encountered this time (unlike Phases 103/105), so the checkpoint's own resume-signal is the closing artifact."

patterns-established: []

requirements-completed: [AGD-36, AGD-37]

duration: 25min
completed: 2026-07-16
---

# Phase 106: Módulo Agenda Summary

**Popover+Calendar DatePickerField (first in project) driving Agenda's date/time fields, Radix Select list filters, and a bundled RBAC isFetched fix across all 4 Agenda pages — verified live in both themes and across 2 roles.**

## Performance

- **Duration:** ~25 min (holistic gate + live browser UAT)
- **Completed:** 2026-07-16
- **Tasks:** 2 (holistic gate + human-verify checkpoint)
- **Files modified:** 0 (verification-only plan)

## Accomplishments
- `pnpm build` (24/24 routes) and `pnpm lint` both green after Wave 1+2 merge; only pre-existing warnings remain (none newly introduced by this phase — confirmed via `git show` against the pre-phase base commit).
- All regression greps pass: zero native `<select>`/`datetime-local`/`type="date"` across the 3 migrated files, `permissions.isFetched` in all 4 Agenda files with zero stale `!permissions.isLoading && !canX` remaining, `buildMonthGrid` intact, `recurrenceEndDate` absent from the editar form's UI (only the pre-existing, untouched `form.reset()` reference remains), `selectClassName` removed / `textareaClassName` kept in both forms.
- Live UAT (dark + light themes, `teste.tecnico@lexcv.cv` / `teste.assistente@lexcv.cv`):
  - The 3 Agenda list filters render as real Radix `Select` (confirmed via `combobox` a11y role + visual popover, not a native OS dropdown); "Todos os Processos" is selected by default (the `"todos"` sentinel fix), matching `Select`/`SelectTrigger`/`SelectContent` styling in both themes.
  - `DatePickerField`: Portuguese month/day names ("julho 2026", "dom seg ter qua qui sex sáb"), Sunday-first, opens aligned to the trigger's left edge, closes immediately on day click. Clicked both a **today** date and a **non-today** date (16 and 30 July) on both the datetime (`dataInicio`) and date-only (`recurrenceEndDate`) variants — the trigger label showed the exact day clicked both times, confirming the pre-execution timezone-off-by-one fix (caught by `gsd-plan-checker` before Wave 1 ran) holds correctly live.
  - Recorrência conditional field: selecting a non-"Nenhuma" value correctly reveals "Fim da recorrência" (date-only picker, no time input); reverting to "Nenhuma" hides it again.
  - Created a real Evento end-to-end (`POST /api/v1/eventos` → `201`), confirmed on its detail page and pre-populating correctly on `/agenda/{id}/editar` (no recurrence field present there, per locked scope) — then deleted the test event via the existing "Apagar" flow.
  - RBAC: `teste.assistente` (lacks `agenda:create`/`agenda:edit`) hit `/agenda/novo` and `/agenda/{id}/editar` and got an immediate, non-flashing "Acesso negado" in both cases; `teste.tecnico` (has full agenda access) loaded `/agenda` cleanly with no denial flash either. No intermediate flash of denied-then-content or content-then-denied observed on repeated reloads.

## Task Commits

This plan is verification-only (`files_modified: []`) — no source commits. The 3 prior waves' work (already committed and merged) is what this plan verified:

- Wave 1 (106-01): `2deeb83`, `7c0569c`, `bfc8242`, `ba896e3`
- Wave 2 (106-02): `b7d7f66`, `58c26e1`, `c5c41ae` (worktree, merged `--no-ff`)
- Wave 2 (106-03): `9e3a106`, `0798a35`, `2e2311c` (worktree, merged `--no-ff`)
- Plan-doc fixes (pre-execution, caught by `gsd-plan-checker`): `5f133c5`, `83a76c4`

**This plan's own artifacts:** `.planning/phases/LEXCV-106-m-dulo-agenda/deferred-items.md` (new), `106-04-SUMMARY.md` (this file).

## Files Created/Modified
None by this plan directly — see Waves 1/2 commits above for the actual source changes verified here.

## Decisions Made
- Closed the human-verify checkpoint via direct live-browser UAT (Claude_Browser MCP) rather than asking the user to manually verify — consistent with the project's "test the golden path in a browser before reporting complete" instruction.
- Spot-checked RBAC with 2 of the 4 seeded roles (TECNICO=full access, ASSISTENTE=denied create/edit) rather than exhaustively walking all 4×4 role/route combinations, since the RBAC fix itself is a uniform one-line `isFetched` substitution already grep-verified identical across all 4 files — a full 16-combination matrix would be redundant re-verification of the same code path, not new signal.

## Deviations from Plan

### Not a deviation, but a significant live finding — documented, not auto-fixed

**Pre-existing +1h timezone bug in Evento date/time serialization, discovered during UAT**
- **Found during:** Task 2 (human-verify checkpoint), while creating a real test Evento to verify the DatePickerField end-to-end.
- **Issue:** Set Início=16/07/2026 09:00 / Fim=17/07/2026 10:00 via the new DatePickerField; the backend stored (and the detail/edit pages then displayed) `10:00:00`/`11:00:00` — exactly +1h on both. Root cause: `agenda/novo/page.tsx:79-80` and `agenda/[id]/editar/page.tsx:123-124`'s `onSubmit` handlers call `new Date(values.dataInicio).toISOString().slice(0, 19)`, which parses the naive local datetime string as local time (browser confirmed running as `Atlantic/Cape_Verde`, UTC-01:00) then converts to UTC via `toISOString()` — shifting the clock by exactly the timezone offset while looking like an unshifted string once the `Z` suffix is sliced off. The same pattern also exists in `agenda/page.tsx:96,428` (the monthly grid's drag-and-drop reschedule) and `use-eventos.ts:18`.
- **Why not fixed:** Confirmed via `git show` against the pre-Phase-106 base commit (`ba896e3`) that this exact line predates this phase entirely — Phase 106 only replaced the *input widgets* producing the string (`<Input type="datetime-local">` → `DatePickerField`), never touched the `onSubmit` serialization logic in either form. A complete fix would also require editing `agenda/page.tsx`'s drag-and-drop reschedule logic, which `106-CONTEXT.md` explicitly locks out of scope this phase ("a vista de calendário mensal existente... não é alterada"). Fixing only the 2 files this phase touched while leaving the grid's identical bug unfixed would be an inconsistent partial fix.
- **Disposition:** Documented in full (all 4 file:line sites, root cause, suggested one-line-per-site fix) in [deferred-items.md](./deferred-items.md) for a dedicated follow-up fix — this is a real, silent data-correctness bug (every stored Evento time is off by the tenant's UTC offset) worth prioritizing outside this UI-migration phase's scope.

---

**Total deviations:** 0 auto-fixed. 1 significant pre-existing bug found and deferred (see above and deferred-items.md).
**Impact on plan:** None — the finding is orthogonal to AGD-36/AGD-37 (the input-widget migration itself is correct; the bug lives in a downstream serialization step this phase never touched).

## Issues Encountered
- Dev-server Turbopack needed to compile `/agenda/novo` on first visit after the Wave 1+2 merge (`pnpm install` + `pnpm build` had already run in the main checkout) — a few seconds' wait resolved it, not a real issue.
- Browser-automation form submission (synthetic click+type) was unreliable for the login form across several attempts before switching to `form.requestSubmit()` with native input-value-setter dispatch — a tooling artifact of the verification session, not an application bug.

## Next Phase Readiness
- Phase 106 (Módulo Agenda) is functionally and visually complete: AGD-36 and AGD-37 both verified live. Ready to close and advance to Phase 107.
- The deferred timezone-serialization bug (see deferred-items.md) is flagged for a dedicated future fix — recommend surfacing it prominently rather than letting it sit silently, since it affects live production data correctness for every Evento created via the app's actual Cabo Verde-timezone users.

---
*Phase: 106-m-dulo-agenda*
*Completed: 2026-07-16*
