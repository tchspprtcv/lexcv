---
phase: 108-m-dulo-pareceres
plan: 02
subsystem: ui
tags: [react-hook-form, native-select, rbac, tanstack-query, shadcn]

# Dependency graph
requires:
  - phase: 101-foundation
    provides: NativeSelect primitive (@/components/ui/native-select), register()-compatible drop-in for native <select>
  - phase: 102-design-system-reconciliation
    provides: reconciled shadcn primitives (Button/Card/Label/Input) already consumed unchanged by this file
provides:
  - Pareceres create form (pareceres/nova/page.tsx) fully migrated to NativeSelect for all 4 select fields
  - Both RBAC sites in this file (view gate + submit-disable) switched from permissions.isLoading to permissions.isFetched
affects: [108-04 (holistic Wave-2 gate), 108-03 (pareceres list/detail, same module different files)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "NativeSelect className=\"w-full rounded-none\" for register()-bound select fields (matches Processos/Documentos Phase 105/107 precedent)"
    - "permissions.isFetched (not isLoading) gates both client-side view-access checks and submit-button disable, closing the pre-resolve RBAC render race"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/pareceres/nova/page.tsx

key-decisions:
  - "clienteId and processoId NativeSelect JSX kept single-line (matching prioridade/advogadoId's existing style in this file) rather than the multi-line attribute layout used in processos/novo/page.tsx — preserves this file's internal formatting consistency while remaining functionally/structurally identical (same loading-aware placeholder, same disabled logic, same error blocks)."

patterns-established: []

requirements-completed: [PARC-18]

# Metrics
duration: ~15min
completed: 2026-07-17
---

# Phase 108 Plan 02: Pareceres Create Form NativeSelect + RBAC isFetched Summary

**Migrated all 4 native `<select>` fields in the Pareceres create form to `NativeSelect`, deleted the dead `selectClassName` constant, and fixed both RBAC sites (view gate + submit-disable) to use `permissions.isFetched` instead of `permissions.isLoading`.**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-17T10:16:53Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- `clienteId`, `processoId`, `prioridade`, `advogadoId` all render as `NativeSelect className="w-full rounded-none"`, each preserving its exact prior dynamic option list, loading/disabled behavior, and validation/error `<p>` blocks.
- Zero native `<select>` elements remain in the file.
- `selectClassName` constant deleted (zero remaining consumers after the 4 migrations); `textareaClassName` preserved (still used by the `descricao` textarea).
- Both RBAC sites (`ParecerCreatePage`'s view gate at line 33, and the submit-button `disabled` expression) now use `permissions.isFetched`, closing the pre-resolve render race where the form/gate would briefly show the wrong state before the permissions query settled.

## Task Commits

Each task was committed atomically:

1. **Task 1: 4 create-form selects to NativeSelect + delete selectClassName + isFetched RBAC (both sites)** - `b19f946` (feat)

**Plan metadata:** (this commit — SUMMARY.md only, no STATE.md/ROADMAP.md changes per orchestrator instruction)

## Files Created/Modified
- `web/src/app/(dashboard)/pareceres/nova/page.tsx` - 4 `<select>` → `NativeSelect` (clienteId/processoId/prioridade/advogadoId), `selectClassName` deleted, both RBAC sites switched to `isFetched`, new `NativeSelect` import added.

## Decisions Made
- Formatted `clienteId`/`processoId`'s `NativeSelect` opening tag as a single line (all props inline) rather than the multi-line attribute layout `processos/novo/page.tsx` uses for its structurally-identical `clienteId` field. This matches the file's own existing single-line style for `prioridade`/`advogadoId` and satisfies the plan's own literal single-line grep-based verification (`grep -q 'NativeSelect id="clienteId"'`), while remaining a 1:1 structural match (same loading-aware placeholder text, same `disabled={clientes.isPending || clientes.isError}`, same `.map` over `clientes.data`, same two error `<p>` blocks below) to the Processos precedent named in the plan's `read_first`.

## Deviations from Plan

None - plan executed exactly as written. (One clarifying formatting choice is documented above under Decisions Made — it does not change any behavior, prop, or option list named in the plan; it only affects whether the JSX attributes are on one line or several.)

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- PARC-18 is satisfied for the Pareceres create form: all 4 fields now use `NativeSelect`, matching the established Processos/Documentos precedent.
- 2 of the 7 total bundled RBAC `isFetched` sites for Phase 108 are now fixed (`pareceres/nova/page.tsx`'s view gate and submit-disable); the remaining 5 sites belong to other Phase 108 plans (list/detail/edit pages) and are unaffected by this plan's exclusive ownership of `pareceres/nova/page.tsx`.
- No blockers. This plan has zero dependency on and zero file overlap with the other Wave 1 plans (108-01, 108-03) running in parallel.
- Plan-level `pnpm build`/`pnpm lint` verification is deferred to the Wave-2 holistic gate (Plan 04), per this plan's own `<verification>` section.

---
*Phase: 108-m-dulo-pareceres*
*Completed: 2026-07-17*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/pareceres/nova/page.tsx
- FOUND: .planning/phases/LEXCV-108-m-dulo-pareceres/108-02-SUMMARY.md
- FOUND commit: b19f946
