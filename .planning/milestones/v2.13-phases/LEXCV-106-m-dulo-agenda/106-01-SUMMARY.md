---
phase: 106-m-dulo-agenda
plan: 01
subsystem: ui
tags: [react, nextjs, radix-select, react-day-picker, date-fns, rbac]

# Dependency graph
requires:
  - phase: 101-foundation
    provides: Calendar/Popover/Select primitives installed, react-day-picker@9.14.0 pinned, date-fns@^4.4.0 present
  - phase: 102-design-system-reconciliation
    provides: tokenized Popover/Select surfaces (--popover, --card) this plan's components render on
  - phase: 103-modulo-dashboard
    provides: permissions.isFetched RBAC-fix precedent (fixes !permissions.isLoading race)
  - phase: 105-clientes-processos
    provides: Select-for-filters / NativeSelect-for-RHF-forms convention this plan follows
provides:
  - Shared DatePickerField (Popover+Calendar composition) at web/src/components/shared/date-picker-field.tsx for Wave 2 (106-02+) form plans to consume
  - Agenda list filters (Processo/Categoria/Estado) migrated to Radix Select (AGD-37)
  - RBAC isFetched fix landed on agenda/page.tsx and agenda/[id]/page.tsx
affects: [106-02, 106-03, phase-107, phase-108, phase-109]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DatePickerField: single shared Popover+Calendar+Button(+optional Input type=time) component, parameterized by withTime, first such composition in the project"
    - "parseDateOnly: parse YYYY-MM-DD components directly (new Date(y, m-1, d)) instead of new Date(string) to avoid UTC/local date-only timezone off-by-one"

key-files:
  created:
    - web/src/components/shared/date-picker-field.tsx
  modified:
    - web/src/app/(dashboard)/agenda/page.tsx
    - web/src/app/(dashboard)/agenda/[id]/page.tsx

key-decisions:
  - "Used autoFocus (not initialFocus) on Calendar -- react-day-picker@9.14.0 deprecated initialFocus in favor of autoFocus, confirmed by reading node_modules/react-day-picker/dist/esm/types/props.d.ts"

patterns-established:
  - "DatePickerField: shared component consumed by Wave 2's form plans (agenda/novo, agenda/[id]/editar) for dataInicio/dataFim (withTime) and recurrenceEndDate (date-only)"

requirements-completed: [AGD-36, AGD-37]

# Metrics
duration: ~20min
completed: 2026-07-16
---

# Phase 106 Plan 01: Shared DatePickerField + Agenda List Filters + RBAC Fix Summary

**First Popover+Calendar date-picker composition in the project (Portuguese locale, Sunday-first), plus Radix Select migration of the 3 Agenda list filters and the isFetched RBAC race fix on the list/detail pages.**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-16T22:16:04Z
- **Tasks:** 3
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments
- Built `DatePickerField`, a reusable `Popover`+`Calendar`(+optional `Input type="time"`) component with Portuguese locale (`date-fns/locale` `pt`), `weekStartsOn={0}` to match the existing Sunday-first monthly grid, and a deselect guard so a stray click never wipes a required RHF field
- Migrated the 3 Agenda list filters (Processo/Categoria/Estado) from native `<select>` to Radix `Select`/`SelectTrigger`/`SelectContent`/`SelectItem`, closing AGD-37
- Changed the Processo filter's sentinel from `""` to `"todos"` (Radix `Select.Item` throws on `value=""`), updating the `useState` initializer, the filter predicate, and the "Limpar Filtros" handler together
- Fixed the RBAC access-gate race (`!permissions.isLoading` → `permissions.isFetched`) on `agenda/page.tsx` and `agenda/[id]/page.tsx`, matching the precedent established in Phases 103/105
- Left the monthly calendar grid (`buildMonthGrid`/`dayKey`/`cursorMonth`, drag-and-drop) byte-for-byte unchanged
- `pnpm build` green across all 3 files

## Task Commits

Each task was committed atomically:

1. **Task 1: Create shared DatePickerField (Popover + Calendar composition)** - `2deeb83` (feat)
2. **Task 2: agenda/page.tsx — 3 filters to Radix Select + 'todos' sentinel + RBAC fix** - `7c0569c` (feat)
3. **Task 3: agenda/[id]/page.tsx — RBAC isFetched fix only** - `bfc8242` (fix)

## Files Created/Modified
- `web/src/components/shared/date-picker-field.tsx` - New shared `DatePickerField` component (Popover+Calendar, optional withTime variant with adjacent time Input)
- `web/src/app/(dashboard)/agenda/page.tsx` - 3 list filters migrated to Radix Select, Processo sentinel changed to "todos", RBAC gate fixed
- `web/src/app/(dashboard)/agenda/[id]/page.tsx` - RBAC gate fixed only (read-only detail page, no other change)

## Decisions Made
- Used `autoFocus` instead of the reference pattern's `initialFocus` on `Calendar` — verified directly against `react-day-picker@9.14.0`'s type declarations (`node_modules/react-day-picker/dist/esm/types/props.d.ts`), which mark `initialFocus` `@deprecated` in favor of `autoFocus`. Same visual/behavioral outcome, avoids using a deprecated prop.

## Deviations from Plan

None - plan executed exactly as written (one clarifying implementation detail resolved via source inspection, see Decisions Made above; this was called out by the plan itself as a point to verify, not a deviation from it).

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `DatePickerField` (`web/src/components/shared/date-picker-field.tsx`) is ready for Wave 2 plans (`agenda/novo/page.tsx`, `agenda/[id]/editar/page.tsx`) to consume for `dataInicio`/`dataFim` (`withTime`) and `recurrenceEndDate` (date-only) via `Controller` wiring, per `106-PATTERNS.md` Shared Pattern 4(c)/(d).
- AGD-37 fully closed for the list-filter surface; AGD-36's foundation (the shared component) is in place, but the requirement itself completes once Wave 2's form plans wire it into `agenda/novo`/`agenda/[id]/editar`.
- No blockers.

---
*Phase: 106-m-dulo-agenda*
*Completed: 2026-07-16*

## Self-Check: PASSED

- FOUND: web/src/components/shared/date-picker-field.tsx
- FOUND: web/src/app/(dashboard)/agenda/page.tsx
- FOUND: web/src/app/(dashboard)/agenda/[id]/page.tsx
- FOUND commit: 2deeb83
- FOUND commit: 7c0569c
- FOUND commit: bfc8242
