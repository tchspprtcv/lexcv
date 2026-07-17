---
phase: LEXCV-107-m-dulos-documentos-financeiro
plan: 01
subsystem: ui
tags: [react, shadcn, cmdk, popover, combobox, typescript]

# Dependency graph
requires:
  - phase: LEXCV-106-m-dulo-agenda
    provides: "DatePickerField structural precedent (open-state + Popover/Button skeleton composed from primitives, plain value/onChange props for Controller compatibility)"
provides:
  - "web/src/components/shared/combobox.tsx exporting Combobox + ComboboxOption, supporting closed-searchable and creatable modes"
  - "LOCKED prop signature (value, onChange, options, placeholder, searchPlaceholder, emptyMessage, creatable, createLabel, triggerClassName, id) for Wave-2 plans 04/05 to consume"
affects: [107-04, 107-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "First Command (cmdk) consumer in the project — manual filtering (shouldFilter={false}) plus a synthetic 'create' CommandItem, relying on cmdk's own item-count-based CommandEmpty auto-visibility (filtered.count===0 when shouldFilter is false equals the number of currently-mounted CommandItem elements) rather than manually branching between CommandGroup/CommandEmpty rendering"

key-files:
  created:
    - web/src/components/shared/combobox.tsx
  modified: []

key-decisions:
  - "CommandEmpty is always rendered (not conditionally) alongside CommandGroup; cmdk's own internal count-based visibility (verified against cmdk@1.1.1's dist/index.mjs source: filtered.count = u.current.size — the total number of mounted CommandItem instances — whenever shouldFilter is false, regardless of search text) automatically hides it once any option or the synthetic create item is mounted, matching the plan's specified empty-state behavior without extra branching logic"
  - "Ran a scoped `pnpm install --offline` inside the worktree before verification, since worktrees do not inherit node_modules from the main checkout (confirmed prior finding, PROJECT.md Phase 101) — required to run `pnpm exec tsc --noEmit` for this plan's acceptance criteria"

patterns-established:
  - "Combobox (Popover + Command) composition: trailing ChevronsUpDown (vs. DatePickerField's leading CalendarIcon), role=\"combobox\"/aria-expanded, fixed w-80 PopoverContent, font-normal trigger override — the binding contract for all future closed-searchable/creatable select fields in the project"

requirements-completed: [DOF-02]

duration: ~15min
completed: 2026-07-17
---

# Phase 107 Plan 01: Shared Combobox Composition Summary

**Built `web/src/components/shared/combobox.tsx` — the project's first `Command`-based composition, a `Popover`+`Command` `Combobox` supporting closed-searchable and creatable ("Usar \"{query}\"") modes behind one locked prop contract.**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-17T01:34:40Z
- **Tasks:** 1 completed
- **Files modified:** 1 created

## Accomplishments
- `Combobox` component exporting `Combobox` + `ComboboxOption` (`{ value: string; label: string }`), matching the LOCKED prop signature in `107-01-PLAN.md`'s `<interfaces>` block exactly (`value`, `onChange`, `options`, `placeholder`, `searchPlaceholder`, `emptyMessage`, `creatable`, `createLabel`, `triggerClassName`, `id`)
- Closed-searchable mode: trigger shows the matched option's label or a muted placeholder; typing filters the list manually (`shouldFilter={false}`); selecting an item closes the popover and clears the query
- Creatable mode: a typed value with no case-insensitive exact match renders a `Usar "{query}"` item above the filtered group; selecting it commits the raw typed value; a freshly-typed value with no match still displays in the trigger (not silently replaced by the placeholder)
- Reused the exact `useState(open)` + `Popover open/onOpenChange` + `PopoverTrigger asChild` + outline `Button` skeleton from `date-picker-field.tsx`, swapping the leading `CalendarIcon` for a trailing `ChevronsUpDown` and the `Calendar` content for the `Command` composition
- Zero manual `<Check>` icon inside options (relies on `command.tsx`'s own `CommandItem` auto-rendered checkmark gated on `data-checked`)

## Task Commits

Each task was committed atomically:

1. **Task 1: Build the shared Combobox component (closed + creatable modes)** - `abba7d7` (feat)

**Plan metadata:** (this SUMMARY.md + STATE.md/ROADMAP.md updates are applied by the orchestrator after all Wave 1 worktree agents complete, per this plan's parallel-execution instructions)

## Files Created/Modified
- `web/src/components/shared/combobox.tsx` - New shared `Combobox` composition (Popover + Command), 120 lines, exporting `Combobox` + `ComboboxOption`

## Decisions Made
- `CommandEmpty` is always rendered unconditionally (not gated by an explicit `if (filtered.length === 0 && !showCreateItem)` branch); verified against `cmdk@1.1.1`'s actual bundled source (`node_modules/.pnpm/cmdk@1.1.1.../dist/index.mjs`) that `CommandEmpty`'s own internal visibility (`filtered.count === 0`) is computed as `u.current.size` (the count of currently-mounted `CommandItem` instances) whenever `shouldFilter` is `false` — since this component only ever mounts `CommandItem`s for entries already present in the locally-filtered array (plus the synthetic create item when shown), cmdk's own automatic empty-state toggling already matches the plan's specified behavior with no extra branching needed.
- Ran `pnpm install --offline` inside the worktree before verification — confirmed via PROJECT.md (Phase 101 decision) that Claude Code worktrees do not inherit `node_modules` from the main checkout; the offline install resolved entirely from the existing local pnpm store with zero network access and produced no lockfile/dependency changes (`git status` showed only the new `combobox.tsx` file after install).

## Deviations from Plan

None - plan executed exactly as written. The component's behavior, prop contract, trigger anatomy, and creatable/closed-list logic all match `107-01-PLAN.md`'s `<behavior>`/`<action>` blocks and `107-UI-SPEC.md`'s Component Inventory binding contract verbatim.

## Issues Encountered

**Pre-existing, out-of-scope `tsc` failures unrelated to this task's file:** `cd web && pnpm exec tsc --noEmit` reports 3 errors, all `TS2307: Cannot find module 'vitest'`, in `src/hooks/use-processos.round-trip.test.ts`, `src/lib/cliente-documento-tipo.test.ts`, and `src/schemas/clientes.legacy-documento-tipo.test.ts`. Confirmed via `git log` that all three files predate this phase (most recently touched by commit `80cb859`, Phase 97/v2.11) and that `vitest` has never been added as a project dependency — the commit message for `80cb859` explicitly documents this as a deliberate, known repo convention ("vitest syntax, matching the repo's existing no-test-runner-installed convention from 74-02-SUMMARY.md"). `pnpm-lock.yaml` confirms `vitest` is absent from the lockfile entirely. None of the 3 errors reference `combobox.tsx` or any file this task touched. Per the executor's Scope Boundary rule ("Only auto-fix issues DIRECTLY caused by the current task's changes... pre-existing failures in unrelated files are out of scope"), this was left unfixed and is not a deviation introduced by this plan — it is a pre-existing, previously-undocumented-at-the-tsc-level environmental gap. `combobox.tsx` itself introduces zero new `tsc` errors.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `Combobox` + `ComboboxOption` are ready for the four Wave-2 call sites: Documentos list Processo/Cliente filters (`107-04`) and the `Documento.tipo` field in the Processo and Cliente document tabs (`107-05`), consuming the exact LOCKED prop signature this plan established.
- The pre-existing `vitest`-missing `tsc` baseline gap (3 files, unrelated to this component) remains open; not blocking, and out of this plan's scope to fix.

---
*Phase: LEXCV-107-m-dulos-documentos-financeiro*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: web/src/components/shared/combobox.tsx
- FOUND: .planning/phases/LEXCV-107-m-dulos-documentos-financeiro/107-01-SUMMARY.md
- FOUND: abba7d7 (Task 1 commit, verified in `git log --oneline --all`)
