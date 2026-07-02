---
phase: LEXCV-71-frontend-types-schema-api-integration
plan: 02
subsystem: ui
tags: [typescript, react-hook-form, zod, clientes, tsc-build-restoration]

# Dependency graph
requires:
  - phase: LEXCV-71-frontend-types-schema-api-integration/71-01
    provides: Flattened Cliente/ClienteCreateRequest/ClienteUpdateRequest types, DocumentoTipo union, mandatory NIF Zod validation
provides:
  - Three client consumer pages (novo, editar, ficha) compiling against the flattened 71-01 types
  - Whole-app `tsc --noEmit` green after the dados_tipo removal
affects: [LEXCV-72 (create/edit client forms rebuild), LEXCV-73 (ficha/detail page rebuild)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "documento_tipo allow-list guard: narrow a schema-typed `string | undefined` to the `DocumentoTipo` union via an array `.includes()` check instead of a cast, before assigning into a typed request payload field"
    - "truthiness-guarded optional-to-required sync: `if (cond && value) { payload.requiredField = value; }` instead of a non-null assertion when syncing a legacy optional field into a newly-required field"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/clientes/novo/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
    - web/src/app/(dashboard)/clientes/page.tsx

key-decisions:
  - "Removed all dados_tipo-bound JSX input blocks (Particular/Empresa sub-fields) from novo/editar pages rather than rewiring them to flat fields — plan explicitly deferred the flat-field form rebuild to Phase 72"
  - "Ficha page renders blank placeholders (BLANK constant via fmt(undefined)) for the four removed Empresa sub-fields (nome_comercial/sede/representante_legal/cargo) instead of inventing new Cliente fields — real Empresa ficha rendering deferred to Phase 73"
  - "Guarded novo/page.tsx NIF sync with `values.documento_tipo === \"NIF\" && values.documento_numero` (no assertion/cast) so the required payload.nif never receives string|undefined"
  - "Added a documento_tipo allow-list guard (toDocumentoTipo helper) in novo/editar pages to narrow the schema's string|undefined into the DocumentoTipo union before payload assignment — this was a compile-blocker surfaced only by the whole-app tsc gate, not by the plan's per-file remnant grep checks"
  - "CSV bulk-import in clientes/page.tsx now skips rows missing a NIF (same as rows missing a nome) instead of sending undefined into the now-required ClienteCreateRequest.nif"

patterns-established:
  - "When a plan's per-task verify only checks for removed-symbol remnants (grep), always still run the plan-level whole-app build gate before finalizing — type-shape changes (e.g. string -> union type) can break call sites the per-task checks don't cover"

requirements-completed: [CLI-06]

# Metrics
duration: 22min
completed: 2026-07-02
---

# Phase LEXCV-71 Plan 02: Restore Frontend Build After Cliente Type Flattening Summary

**Stripped dados_tipo/DadosTipoParticular/DadosTipoEmpresa from three client consumer pages, guarded the novo-page NIF sync, and fixed two additional DocumentoTipo/required-nif compile errors surfaced only by the whole-app `tsc --noEmit` gate, bringing the entire web app back to a green build.**

## Performance

- **Duration:** 22 min
- **Started:** 2026-07-02T00:14:00Z (approx.)
- **Completed:** 2026-07-02T00:36:50Z
- **Tasks:** 3
- **Files modified:** 4 (3 planned + 1 deviation fix)

## Accomplishments
- `web/src/app/(dashboard)/clientes/novo/page.tsx` builds a `dados_tipo`-free `ClienteCreateRequest`, with the required `nif` sync guarded against `string | undefined`
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` builds a `dados_tipo`-free `ClienteUpdateRequest`
- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` reads flat `Cliente.idade/sexo/nacionalidade`, imports only `Cliente`, and renders blanks for removed Empresa sub-fields
- `pnpm exec tsc --noEmit` exits 0 across the whole `web/` app (verified twice, explicit exit-code check)

## Task Commits

Each task was committed atomically:

1. **Task 1: Strip dados_tipo from the create-client page (novo)** - `3227364` (fix)
2. **Task 2: Strip dados_tipo from the edit-client page (editar)** - `babcd06` (fix)
3. **Task 3: Fix the ficha page reads/imports and confirm full frontend build** - `13e3678` (fix, includes deviation fixes needed to reach the green build gate)

**Plan metadata:** (this commit, following SUMMARY write)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/novo/page.tsx` - Removed dados_tipo defaultValues/setValue writes/derivation/JSX blocks; guarded NIF sync; added documento_tipo allow-list narrowing helper
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` - Removed dados_tipo defaultValues/setValue writes/reset-mapping/derivation/JSX blocks; added documento_tipo allow-list narrowing helper
- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` - Dropped DadosTipoParticular/DadosTipoEmpresa import and type guard; reads flat Cliente fields; blanks removed Empresa sub-fields
- `web/src/app/(dashboard)/clientes/page.tsx` - CSV bulk-import skips rows without a NIF instead of assigning undefined into the required field

## Decisions Made
- Removing the dados_tipo-bound Particular/Empresa input blocks (rather than rewiring to flat fields) is correct per plan scope — Phase 72 owns the flat-field form rebuild.
- The ficha page's Empresa sub-field blanking is a deliberate no-op placeholder, not data loss — Phase 73 owns real Empresa ficha rendering.
- Chose an allow-list narrowing helper (`toDocumentoTipo`) over a type assertion/cast for the documento_tipo -> DocumentoTipo union narrowing, consistent with the plan's "no `!`/cast" guidance for the analogous NIF guard.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] documento_tipo/documentoTipo type mismatch in novo/page.tsx and editar/page.tsx**
- **Found during:** Task 3 (whole-app `tsc --noEmit` gate)
- **Issue:** Plan 71-01 retyped `documento_tipo`/`documentoTipo` on `ClienteCreateRequest`/`ClienteUpdateRequest` from `string` to the `DocumentoTipo` union. The `...values` spread and the explicit `documentoTipo: values.documento_tipo || undefined` assignment in both create and edit onSubmit handlers still typed `documento_tipo` as `string | undefined` (from the Zod schema's `optionalTrimmedString`), causing `TS2322: Type 'string | undefined' is not assignable to type 'DocumentoTipo | undefined'` in both files. Not caught by the plan's per-file dados_tipo remnant grep checks since it's an unrelated symbol.
- **Fix:** Added a `DOCUMENTO_TIPOS` allow-list and `toDocumentoTipo(value)` helper in both pages that narrows the free-form select value to `DocumentoTipo | undefined` via `.includes()`, and assigns the narrowed value to both `documento_tipo` and `documentoTipo` payload fields (overriding the untyped `...values` spread).
- **Files modified:** web/src/app/(dashboard)/clientes/novo/page.tsx, web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
- **Verification:** `pnpm exec tsc --noEmit` exits 0 (errors on these two files gone)
- **Committed in:** 13e3678 (Task 3 commit)

**2. [Rule 2 - Missing Critical] CSV bulk-import assigned undefined into required ClienteCreateRequest.nif**
- **Found during:** Task 3 (whole-app `tsc --noEmit` gate)
- **Issue:** `web/src/app/(dashboard)/clientes/page.tsx`'s CSV import handler built `nif: idxNif >= 0 ? (r[idxNif] ?? "").trim() || undefined : undefined`, which is `string | undefined` — no longer assignable to the now-required `ClienteCreateRequest.nif: string` (`TS2322`). This surfaced only via the whole-app gate, not the three plan-scoped files.
- **Fix:** Extracted the trimmed NIF value up front and added it to the existing row-skip guard (alongside the missing-`nome` check), so rows without a NIF are now skipped and counted as `failed`, consistent with how missing-`nome` rows are already handled. The `mutateAsync` call now passes the guaranteed-non-empty `nif` string directly.
- **Files modified:** web/src/app/(dashboard)/clientes/page.tsx
- **Verification:** `pnpm exec tsc --noEmit` exits 0 (error on this file gone); behavior change is additive validation (rows without NIF now correctly rejected instead of being sent with an invalid/missing NIF to the backend)
- **Committed in:** 13e3678 (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking type error across 2 files, 1 missing validation)
**Impact on plan:** Both fixes were necessary to satisfy the plan's own Task 3 gate ("whole app tsc --noEmit exits 0"), which the plan explicitly calls out as depending on Task 1's guard but does not otherwise scope beyond the three named files. No form/detail UX redesign was performed; both fixes are minimal, type/validation-only changes. No scope creep.

## Issues Encountered
None beyond the deviations documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Frontend compiles cleanly end-to-end; Phase 72 (create/edit client forms) and Phase 73 (ficha/detail page) can proceed against the flattened types without inheriting a broken build.
- The removed dados_tipo Particular/Empresa input blocks in novo/editar and the blanked Empresa ficha fields are intentional placeholders pending Phase 72/73 rework — flagged in key-decisions above for those phases' planners.

---
*Phase: LEXCV-71-frontend-types-schema-api-integration*
*Completed: 2026-07-02*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/clientes/novo/page.tsx
- FOUND: web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
- FOUND: web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx
- FOUND: commit 3227364
- FOUND: commit babcd06
- FOUND: commit 13e3678
- `pnpm exec tsc --noEmit` exit code: 0
