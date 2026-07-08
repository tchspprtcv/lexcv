---
phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
plan: 01
subsystem: ui
tags: [nextjs, react-hook-form, zod, processos, intake]

# Dependency graph
requires:
  - phase: 83
    provides: "processoIntakeFormSchema / origemProcessoSchema (Zod), OrigemProcesso type, origemProcessoToLabel(), ProcessoCreateRequest.origem, ProcessoUpdateRequest.juizo, toProcessoApiPayload/mapJuizoOrigemToPayload mapping layer"
provides:
  - "Required Origem field wired into processos/novo/page.tsx step-1 intake form, enforced client-side via processoIntakeFormSchema"
  - "Editable Juízo Input on processos/[id]/editar/page.tsx, pre-filled and persisted through the existing juizo mapping layer"
affects: [84-02, 84-03, 84-04, 84-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Intake step-1 form now bound to the stricter processoIntakeFormSchema (Zod .extend) instead of the base processoFormSchema, activating origem requiredness for the first time since Phase 83 built it"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/processos/novo/page.tsx"
    - "web/src/app/(dashboard)/processos/[id]/editar/page.tsx"

key-decisions:
  - "New Juízo Input on processos/[id]/editar/page.tsx carries an explicit className=\"rounded-none\" per 84-UI-SPEC.md's hard sharp-edges requirement, even though sibling Input fields on that page rely on the component's rounded-md default (a pre-existing inconsistency on that page, left untouched)"

patterns-established: []

requirements-completed: [PROC-03, PROC-04, PROC-02]

# Metrics
duration: 8min
completed: 2026-07-08
---

# Phase 84 Plan 01: Origem Intake Field + Juízo Edit Field Summary

**Closed the two small-field gaps blocking the rest of Phase 84: Origem is now a required, validated field on the intake wizard's step 1 (switched from `processoFormSchema` to `processoIntakeFormSchema`), and Juízo is now editable on `processos/[id]/editar/page.tsx` next to Tribunal/Área Jurídica.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-07-08T00:36:44Z
- **Completed:** 2026-07-08T00:41:21Z
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- `processos/novo/page.tsx` step 1 form is now bound to `processoIntakeFormSchema`/`ProcessoIntakeFormValues`; Zod `.enum()` requiredness on `origem` is enforced client-side for the first time (previously built in Phase 83 but never wired to a form).
- Added a native `Origem` select (placeholder "Selecionar origem", options "Petição Inicial" / "Notificações Avulsas" via `origemProcessoToLabel`), positioned as a full-width row directly after Cliente/Tipo de Processo, with inline Zod error rendering matching sibling fields.
- `processos/[id]/editar/page.tsx` now has an editable Juízo `Input` (positioned after Tribunal), wired into `defaultValues`, the `processo.data` reset effect, and submitted via the existing `values satisfies ProcessoUpdateRequest` path — no schema or mapping-layer changes needed (Phase 83 already built `juizo` into `processoFormSchema`/`ProcessoUpdateRequest`).
- Confirmed zero `origem` references remain in `editar/page.tsx` (origem is immutable after intake by design; `ProcessoUpdateRequest` has no `origem` field, so TypeScript itself would reject an accidental addition).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add required Origem field to intake step 1, switch form to processoIntakeFormSchema** - `6787f8b` (feat)
2. **Task 2: Add editable Juízo field to processos/[id]/editar/page.tsx** - `12c6cdb` (feat)

**Plan metadata:** (pending — this commit)

## Files Created/Modified
- `web/src/app/(dashboard)/processos/novo/page.tsx` - Intake step-1 form switched to `processoIntakeFormSchema`; added required Origem select with inline validation error.
- `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` - Added editable Juízo Input (defaultValues, reset effect, form field); no origem field added (immutable after intake).

## Decisions Made
- Applied `rounded-none` explicitly to the new Juízo Input per the design-note override in the executor's task brief (UI-SPEC.md's "Anti-Safe Harbor" sharp-edges requirement applies to all new form controls in this phase), even though the file's existing Input fields (numero, tribunal, etc.) rely on the component's `rounded-md` default and were left unchanged — this is a pre-existing inconsistency on that page, out of scope to fix retroactively.

## Deviations from Plan

None - plan executed exactly as written. The `rounded-none` addition on the new Juízo Input was already anticipated by the plan-checker's non-blocking design_note (not a deviation from plan intent, just an execution-time clarification of exact styling not spelled out in the PLAN.md action text).

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `origem` is now reachable end-to-end from intake submission through to persistence (Phase 81 backend, Phase 83 mapping layer, this plan's UI) — downstream plans (84-02 through 84-05) can safely read/write `origem` without re-verifying this wiring.
- `juizo` is now editable in the only editable Processo-fields surface in this codebase, satisfying ROADMAP success criterion 2.
- No blockers for Wave 2 (84-04, 84-05), which depend on 84-03 (not on this plan).

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/processos/novo/page.tsx
- FOUND: web/src/app/(dashboard)/processos/[id]/editar/page.tsx
- FOUND: .planning/phases/LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono/84-01-SUMMARY.md
- FOUND commit: 6787f8b
- FOUND commit: 12c6cdb

---
*Phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono*
*Completed: 2026-07-08*
