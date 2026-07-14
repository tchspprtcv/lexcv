---
phase: 97-auditoria-de-milestone
plan: 02
subsystem: ui, testing
tags: [nextjs, react, typescript, jakarta-validation, hibernate-validator, cliente]

# Dependency graph
requires:
  - phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
    provides: "cliente-documento-tipo.ts (getDocumentoTipoOptions/toDocumentoTipo) and the vitest-syntax-but-no-runner test convention"
  - phase: 73.1-gap-closure
    provides: "Cliente.nif @NotBlank + @Pattern Bean Validation constraints (previously untested)"
provides:
  - "getDocumentoTipoLabel(value) — single-sourced DocumentoTipo -> Portuguese label translator"
  - "Both DocumentoTipo raw-render sites (client detail page, printable ficha) now show translated labels"
  - "ClienteNifValidationTest — automated Bean-Validation regression coverage for the 4 Phase 73.1 NIF scenarios"
affects: [clientes, ficha, nif-validation]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single-sourced label map (DOCUMENTO_TIPO_LABELS) reused by both the options list and the label lookup, avoiding label drift"
    - "Standalone jakarta.validation.Validator (Validation.buildDefaultValidatorFactory()) for Bean Validation unit tests, no @SpringBootTest/MockMvc"

key-files:
  created:
    - backend/src/test/java/com/lexcv/models/ClienteNifValidationTest.java
  modified:
    - web/src/lib/cliente-documento-tipo.ts
    - web/src/lib/cliente-documento-tipo.test.ts
    - web/src/app/(dashboard)/clientes/[id]/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx

key-decisions:
  - "getDocumentoTipoLabel returns the raw value verbatim for unknown/legacy values (never hides real stored data)"
  - "No vitest install — new test cases appended to the existing vitest-syntax spec file, verified via tsc --noEmit + a standalone Node assertion script, matching the repo's pre-existing no-test-runner convention (74-02-SUMMARY.md)"

patterns-established:
  - "Standalone jakarta Validator test pattern for Bean Validation constraints, reusable for future entity-constraint regression tests"

requirements-completed: [AUD-03]

# Metrics
duration: 33min
completed: 2026-07-14
---

# Phase 97 Plan 02: DocumentoTipo Labels + NIF Validation Tests Summary

**Shared `getDocumentoTipoLabel` helper translates `DocumentoTipo` enum values to Portuguese on both cliente render sites, plus a new standalone-Validator JUnit test locking in the 4 Phase 73.1 NIF Bean-Validation scenarios.**

## Performance

- **Duration:** 33 min
- **Started:** 2026-07-14T19:40:00Z
- **Completed:** 2026-07-14T20:13:18Z
- **Tasks:** 2/2 completed
- **Files modified:** 5 (4 frontend, 1 new backend test)

## Accomplishments

- `getDocumentoTipoLabel` added to `cliente-documento-tipo.ts`, backed by a single `DOCUMENTO_TIPO_LABELS` map reused by `OPTIONS_BY_TIPO` — labels can never drift between the dropdown and the read-only render
- Client detail page (`clientes/[id]/page.tsx`) and printable ficha (`clientes/[id]/ficha/page.tsx`) both call the new helper instead of rendering `documento_tipo`/`documentoTipo` raw — a company client now shows "Registo Comercial" instead of "REG_COMERCIAL"
- `ClienteNifValidationTest` (new) proves all 4 NIF scenarios from Phase 73.1 with a standalone `jakarta.validation.Validator`: valid 9-digit NIF (zero violations), null/blank/whitespace (`@NotBlank` "NIF é obrigatório"), wrong length and non-numeric (`@Pattern` "NIF deve conter exatamente 9 dígitos numéricos")

## Task Commits

Each task was committed atomically:

1. **Task 1: Add getDocumentoTipoLabel and apply it at both render sites** - `80cb859` (feat)
2. **Task 2: Add backend Bean-Validation tests for the 4 NIF scenarios** - `e11a4f2` (test)

_Note: Task 2 is test-only (no new implementation code) — the Bean Validation constraints under test already existed from Phase 73.1, so there is no separate feat/GREEN commit; this is a characterization/regression test, matching the existing `RiscoPrazoServiceTest` convention in this codebase._

## Files Created/Modified

- `web/src/lib/cliente-documento-tipo.ts` - Added `DOCUMENTO_TIPO_LABELS` map + exported `getDocumentoTipoLabel(value)`; `OPTIONS_BY_TIPO` now derives its labels from the same map
- `web/src/lib/cliente-documento-tipo.test.ts` - Added 6 new `getDocumentoTipoLabel` behavior-case tests (vitest syntax)
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Detail page's "Tipo de Documento" row now calls `getDocumentoTipoLabel(...) ?? "—"`; added the import
- `web/src/app/(dashboard)/clientes/[id]/ficha/page.tsx` - Ficha's "Tipo Doc." field now calls `fmt(getDocumentoTipoLabel(...))`; added the import
- `backend/src/test/java/com/lexcv/models/ClienteNifValidationTest.java` - New: standalone-Validator JUnit 5 test, 4 test methods covering the valid/blank/wrong-length/non-numeric NIF scenarios

## Decisions Made

- Reused the single `DOCUMENTO_TIPO_LABELS` record as the source of truth for both `OPTIONS_BY_TIPO` (dropdowns) and `getDocumentoTipoLabel` (read-only render), per the plan's explicit instruction, to prevent future label drift between the two use sites.
- `getDocumentoTipoLabel` returns the raw value verbatim for an unrecognized/legacy value rather than `undefined` — matches the plan's explicit behavior requirement and the project's established precedent of never silently hiding real stored data (see Phase 74's `toDocumentoTipo` legacy-value handling).
- Did not install vitest as a new devDependency to make the test file literally executable via `pnpm test`. This repo has a pre-existing, previously-documented gap (no test runner in `package.json`/`pnpm-lock.yaml` anywhere in `web/`, confirmed again during this task) that Phase 74 (`74-02-SUMMARY.md`) explicitly chose not to fix under its own no-new-installs threat model — this plan's own threat model (T-97-02-SC) makes the same choice explicit ("No npm/pip/cargo installs"). Followed the exact same precedent: wrote the 6 new cases in vitest syntax as the durable spec, and independently verified correctness via `tsc --noEmit` (scoped to exclude the 3 pre-existing non-runnable `*.test.ts` files, which is a pre-existing condition unrelated to this plan) plus a standalone Node assertion script re-implementing the exact `getDocumentoTipoLabel` logic (8/8 assertions passing, matching the 6 new + related cases).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking, worked around without new installs] `pnpm test -- cliente-documento-tipo` from the plan's `<verify>` block is not executable — no test runner installed anywhere in `web/`**
- **Found during:** Task 1 verification
- **Issue:** `web/package.json` has no `"test"` script and no `vitest`/`jest` anywhere in `dependencies`/`devDependencies`/`pnpm-lock.yaml`. This is a pre-existing, previously-documented repo-wide gap (see `74-02-SUMMARY.md`, "No test runner exists in this repo"), not introduced by this plan. This plan's own threat model (T-97-02-SC) explicitly forbids new package installs.
- **Fix:** Added the 6 new test cases to `cliente-documento-tipo.test.ts` in standard vitest syntax anyway (durable behavior spec, same precedent as Phase 74). Independently verified: (1) `pnpm exec tsc --noEmit` against a temporary tsconfig excluding `**/*.test.ts` (the 3 pre-existing files that fail to resolve the `vitest` module) — zero errors on all source files, including the two modified page.tsx files and the lib module; (2) a standalone Node assertion script (`scratchpad/verify-get-documento-tipo-label.mjs`, not committed — verification-only) re-implementing `getDocumentoTipoLabel`'s exact logic, 8/8 assertions passing.
- **Files modified:** `web/src/lib/cliente-documento-tipo.test.ts` (test cases added, not executable by a runner in this repo today)
- **Verification:** `tsc --noEmit` clean on all non-test source files touched; standalone Node script 8/8 PASS
- **Committed in:** `80cb859` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (pre-existing missing test-runner infra, worked around without violating the plan's no-new-installs constraint — same precedent as Phase 74)
**Impact on plan:** No scope creep. `getDocumentoTipoLabel` is implemented, applied at both render sites, and independently verified correct by two methods; the test file documents the exact contract for when frontend test infra is eventually added.

## Issues Encountered

- `node_modules` was not present in this worktree at start (fresh worktree checkout); ran `pnpm install` to restore it (no `package.json`/`pnpm-lock.yaml` changes — install only, versions match the existing lockfile exactly).
- Full-project `pnpm exec tsc --noEmit` surfaces 3 pre-existing "Cannot find module 'vitest'" errors in `use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, and `clientes.legacy-documento-tipo.test.ts` — all caused by the pre-existing missing test-runner gap (see Deviations above), not by this plan's changes. Verified via a scoped tsconfig excluding `*.test.ts` instead.
- Full-project `pnpm lint` surfaces pre-existing errors/warnings unrelated to this plan's changes (`react-hooks/set-state-in-effect` in `clientes/[id]/page.tsx` at lines 1610/1755/1957, `react/no-danger` unused-directive warning in `ficha/page.tsx` at line 79, and others across unrelated files like `dashboard-shell.tsx`). Confirmed via targeted `eslint` runs on the two touched files that none of these are on the lines this plan modified (imports + the two label-render lines). Out of scope per the scope-boundary rule — not fixed, not further investigated.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- AUD-03 (both known debt items — DocumentoTipo raw-string rendering and missing NIF validation test coverage) is closed.
- Visual/live confirmation ("Registo Comercial" rendering on a real company client, per the plan's optional manual-verification step) was not performed against the shared live environment: the running `localhost:3000`/`:8080` instances reflect the main checkout, not this worktree's uncommitted-to-main changes, so a visual check there would not exercise this plan's code. Recommend a quick visual spot-check after this worktree merges to main, or as part of the phase-level 97-VERIFICATION/AUD-02 pass.
- No blockers for other Phase 97 plans — this plan touched only `web/src/lib/cliente-documento-tipo.ts`, its test file, the two cliente page components, and one new backend test file, none of which are shared with the sibling 97-01 (backend `Notificacao*`) or 97-03 (`97-UAT.md`) plans.

## Self-Check: PASSED

All created/modified files verified present; both task commits (`80cb859`, `e11a4f2`) verified present in git log.

---
*Phase: 97-auditoria-de-milestone*
*Completed: 2026-07-14*
