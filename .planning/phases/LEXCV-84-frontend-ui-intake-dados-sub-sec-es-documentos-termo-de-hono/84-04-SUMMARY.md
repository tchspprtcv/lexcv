---
phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
plan: 04
subsystem: ui
tags: [nextjs, react-hook-form, zod, tanstack-query, radix-dialog]

# Dependency graph
requires:
  - phase: LEXCV-84 (plan 84-03)
    provides: 8-value TabKey union with null placeholder branches for decisoes/factos/testemunhas/documentos, and the Dialog "Adicionar" refactor pattern (Partes/Fases) to replicate
provides:
  - Decisões tab body (list + Dialog Adicionar/Editar, multipart file upload on create only, delete with confirm)
  - Testemunhas tab body (list + Dialog Adicionar/Editar, delete with confirm)
affects: [LEXCV-84 plan 84-05 (Factos + Documentos tabs, same file)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dialog Adicionar/Editar per-entity pattern extended with edit-mode: single Dialog+form reused for both create and edit, gated by an `editingXId: number | null` state variable that also drives the DialogTitle text and (for Decisão) which fields render"
    - "Native <select> options for an entity's Tipo enum are rendered by mapping over the Zod enum's `.options` array, not hardcoded per-value JSX"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/processos/[id]/page.tsx"
    - "web/src/schemas/processos.ts"

key-decisions:
  - "[Phase 84-04] testemunhaFormSchema's optional `tipo` field wrapped in z.preprocess to coerce an empty-string <select> value to undefined — z.enum(...).optional() only treats `undefined` as absent and rejected the blank placeholder selection, contradicting the plan's own stated UI-SPEC intent (bug fix, Rule 1)"
  - "[Phase 84-04] testemunhaForm's zodResolver cast `as any` (eslint-disabled on that line) because the z.preprocess input type diverges from TestemunhaFormValues (z.infer's output type) — same class of RHF+Zod-effects mismatch already present in this file's prazoForm, same established workaround reused"

patterns-established: []

requirements-completed: [PROC-08, PROC-12]

# Metrics
duration: 25min
completed: 2026-07-08
---

# Phase 84 Plan 04: Decisões and Testemunhas tabs Summary

**Decisões and Testemunhas tab bodies in `processos/[id]/page.tsx`, using the Dialog Adicionar/Editar pattern with the 8 already-built Phase 83 hooks — Decisão's create form additionally carries a native file input for a single-step multipart anexo upload.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- Decisões tab: list (Data/Tipo/Resumo/Ações table), Dialog "Adicionar Decisão" with data/tipo/resumo/anexo(opcional, native `<input type="file">`) fields, per-row "Editar" (Dialog pre-populated, no file field on edit per `DecisaoUpdateRequest`'s shape), per-row "✕ Apagar" with `window.confirm`
- Testemunhas tab: list (Nome/Tipo/Contacto/Ações table), Dialog "Adicionar Testemunha" with nome/tipo/contacto/notas fields, per-row "Editar" (Dialog pre-populated, symmetric create/update shape), per-row "✕ Apagar" with `window.confirm`
- Both tabs: Dialog triggers and row Ações gated by `canEditProcessos`, all new form controls carry `rounded-none` per UI-SPEC's Anti-Safe Harbor requirement
- Fixed a real bug in `testemunhaFormSchema` (optional `tipo` enum rejected the blank `<select>` placeholder value)

## Task Commits

Each task was committed atomically:

1. **Task 1: Decisões tab — list, Adicionar (multipart w/ file), Editar, Apagar** - `bdca457` (feat)
2. **Task 2: Testemunhas tab — list, Adicionar, Editar, Apagar** - `69e45da` (feat, includes the `testemunhaFormSchema` bug fix)

**Plan metadata:** (this commit)

## Files Created/Modified
- `web/src/app/(dashboard)/processos/[id]/page.tsx` - Decisões and Testemunhas tab bodies (hooks, state, handlers, Dialog forms, tables), replacing the Plan 84-03 `null` placeholders
- `web/src/schemas/processos.ts` - `testemunhaFormSchema.tipo` wrapped in `z.preprocess` to treat an empty-string select value as `undefined` for this optional enum field

## Decisions Made
- Both tabs reuse a single Dialog/form per entity for both Adicionar and Editar, switching on an `editingXId: number | null` state var (mirrors the plan's explicit instruction, no new UI pattern invented)
- Decisão's file input is conditionally rendered only when `editingDecisaoId === null`, since `DecisaoUpdateRequest` has no file/documentoId slot (anexo is create-only by Phase 81 design)
- Tipo `<select>` options for both entities are generated from each Zod enum's `.options` array (`tipoDecisaoSchema.options`, `tipoTestemunhaSchema.options`) rather than hardcoded, so the list stays in sync with the schema

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed `testemunhaFormSchema`'s optional `tipo` field rejecting the blank `<select>` placeholder**
- **Found during:** Task 2 (Testemunhas tab implementation, production build type-check)
- **Issue:** `tipo: tipoTestemunhaSchema.optional()` only treats `undefined` as a valid "absent" value; a native `<select>` with an unselected `<option value="">Selecionar tipo</option>` submits the string `""`, which Zod's `.optional()` rejects with "Invalid enum value" — directly contradicting the plan/UI-SPEC's stated intent that "the placeholder is a legitimate empty selection, not a validation error state." Verified with a standalone Node/Zod repro before fixing.
- **Fix:** Wrapped the field in `z.preprocess((v) => (v === "" ? undefined : v), tipoTestemunhaSchema.optional())` in `web/src/schemas/processos.ts`. This changed the resolver's inferred input type vs. `TestemunhaFormValues` (the z.infer output type), so `testemunhaForm`'s `zodResolver(testemunhaFormSchema)` call needed an `as any` cast — same divergence class and same workaround already established in this file for `prazoForm` (which uses `.default(...)`).
- **Files modified:** `web/src/schemas/processos.ts`, `web/src/app/(dashboard)/processos/[id]/page.tsx`
- **Verification:** `pnpm run build` passes (0 TypeScript errors), `pnpm exec eslint` on the modified page file reports 0 errors (only 3 pre-existing unused-var warnings unrelated to this change)
- **Committed in:** `69e45da` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 Rule 1 bug fix)
**Impact on plan:** Necessary for the Testemunhas Dialog form to actually submit when Tipo is left unselected, as the plan itself specifies. No scope creep — schema-only, single-field, backward compatible (only consumer is this same plan's new code).

## Issues Encountered
None beyond the deviation above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- `processos/[id]/page.tsx` now has working Decisões and Testemunhas tabs; the `null` placeholders for `factos` and `documentos` remain for Plan 84-05
- Plan 84-05 (Factos + Documentos tabs) can proceed — no blockers, no changes needed to the 8 Phase 83 hooks or schemas beyond the `testemunhaFormSchema` fix documented above (which does not affect `factoFormSchema` or the Documentos tab's `FileDropZone`-based flow)

---
*Phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono*
*Completed: 2026-07-08*

## Self-Check: PASSED
- FOUND: web/src/app/(dashboard)/processos/[id]/page.tsx
- FOUND: web/src/schemas/processos.ts
- FOUND: .planning/phases/LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono/84-04-SUMMARY.md
- FOUND commit: bdca457
- FOUND commit: 69e45da
