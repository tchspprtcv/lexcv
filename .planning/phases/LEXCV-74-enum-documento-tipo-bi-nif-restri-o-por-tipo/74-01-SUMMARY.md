---
phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
plan: 01
subsystem: api
tags: [spring-boot, jpa, enum, validation, cliente]

# Dependency graph
requires: []
provides:
  - "DocumentoTipo enum with BI added and NIF removed (BI, CNI, PASSAPORTE, REG_COMERCIAL)"
  - "Server-side tipo x documentoTipo cross-field validation in createCliente and updateCliente (HTTP 400 + Portuguese message)"
  - "Standalone manual-execution SQL script to null legacy NIF documento_tipo rows before deploy"
affects: [74-02, 74-03, "phase 76 (Dados card identification UI)"]

# Tech tracking
tech-stack:
  added: []
  patterns: [ad-hoc cross-field BAD_REQUEST validation in ResourceController, private helper method for reusable validation logic]

key-files:
  created:
    - backend/migrations/74-cleanup-nif-documento-tipo.sql
  modified:
    - backend/src/main/java/com/lexcv/models/DocumentoTipo.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java

key-decisions:
  - "Fixed DatabaseSeeder.java's seed cliente1 (DocumentoTipo.NIF -> DocumentoTipo.BI) to keep the module compiling -- direct consequence of the enum change, not scope creep"
  - "Validation extracted to a private helper isDocumentoTipoValidoParaTipo(tipo, documentoTipo) shared by createCliente and updateCliente rather than duplicating the Set.of(...) checks inline"

patterns-established:
  - "Cross-field validation for cliente tipo/documentoTipo lives in a small private controller helper, called at the top of create (before persistence prep) and after the NOT_FOUND tenant guard in update"

requirements-completed: [CLI-20, CLI-21, CLI-22, CLI-23, CLI-24]

# Metrics
duration: ~15min
completed: 2026-07-03
---

# Phase 74 Plan 01: DocumentoTipo Enum + Backend Validation Summary

**DocumentoTipo enum now BI/CNI/PASSAPORTE/REG_COMERCIAL (NIF removed), with server-side tipo/documentoTipo combination validation on cliente create+update and a manual pre-deploy SQL cleanup script for legacy NIF rows.**

## Performance

- **Duration:** ~15 min
- **Tasks:** 3 completed
- **Files modified:** 4 (1 created, 3 modified)

## Accomplishments
- Added `BI`, removed `NIF` from `DocumentoTipo` enum — final value set is `BI, CNI, PASSAPORTE, REG_COMERCIAL`
- `createCliente` and `updateCliente` now reject invalid tipo/documentoTipo combinations with HTTP 400 and `{"message": "Tipo de documento inválido para o tipo de cliente selecionado"}`
- Authored `backend/migrations/74-cleanup-nif-documento-tipo.sql`, a standalone, manually-run defensive cleanup script (not wired into any Java code path)

## Task Commits

Each task was committed atomically:

1. **Task 1: Author defensive NIF cleanup SQL script** - `d651c61` (docs)
2. **Task 2: Update DocumentoTipo enum (add BI, remove NIF)** - `8a9ed8e` (feat)
3. **Task 3: Add tipo x documento_tipo validation to createCliente and updateCliente** - `c93f3e0` (feat)

## Files Created/Modified
- `backend/migrations/74-cleanup-nif-documento-tipo.sql` - Manual pre-deploy UPDATE nulling `documento_tipo`/`documento_numero` for legacy `NIF` rows, with a comment header explaining why and when to run it
- `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` - Enum constants changed from `NIF, CNI, PASSAPORTE, REG_COMERCIAL` to `BI, CNI, PASSAPORTE, REG_COMERCIAL`
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - Added `isDocumentoTipoValidoParaTipo` private helper; called from `createCliente` (before tenant/id assignment) and `updateCliente` (after the NOT_FOUND tenant guard, before field copies)
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` - Seed cliente1's `documentoTipo(DocumentoTipo.NIF)` changed to `documentoTipo(DocumentoTipo.BI)` (compile fix required by the enum change)

## Decisions Made
- The plan's Task 2 acceptance criteria only covered the enum file itself, but `DatabaseSeeder.java` referenced the now-removed `DocumentoTipo.NIF` constant, which would break compilation. Fixed it as part of Task 2 (same commit) rather than deferring, since an uncompilable build fails every subsequent task's `mvn compile` verification step. Chose `DocumentoTipo.BI` as the replacement because the seed cliente (`tipo("SINGULAR")`, an individual) maps most closely to BI among the PARTICULAR-allowed set (CNI/BI/PASSAPORTE).
- Validation logic implemented as a single shared private helper method rather than duplicating the `Set.of(...)` membership checks inline in both `createCliente` and `updateCliente`, per the plan's key_link pattern (`DocumentoTipo\.(CNI|BI|PASSAPORTE|REG_COMERCIAL)`) while keeping the code DRY.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed DatabaseSeeder.java compile break caused by enum change**
- **Found during:** Task 2 (Update DocumentoTipo enum)
- **Issue:** `DatabaseSeeder.java` line 104 referenced `DocumentoTipo.NIF`, which Task 2 removes from the enum, breaking `mvn compile`
- **Fix:** Changed `.documentoTipo(DocumentoTipo.NIF)` to `.documentoTipo(DocumentoTipo.BI)` for the seed's individual cliente
- **Files modified:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java`
- **Verification:** `mvn -q -DskipTests compile` and `mvn -q -DskipTests package` both succeed
- **Committed in:** `8a9ed8e` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary for correctness — the plan's own verification step (`mvn compile`) would have failed without it. No scope creep beyond the single enum-reference fix.

## Issues Encountered
None beyond the DatabaseSeeder fix documented above.

## User Setup Required
None — no external service configuration required. Note: `backend/migrations/74-cleanup-nif-documento-tipo.sql` must be run manually against each environment's database before that environment's deploy of this change (see script header for details); this is an operational step for the deployer, not a code-level setup requirement.

## Next Phase Readiness
- Backend `DocumentoTipo` value set is now final (`BI, CNI, PASSAPORTE, REG_COMERCIAL`) — Plan 03 (frontend dropdown) and Phase 76 (Dados card identification UI) can build against it without risk of further changes.
- Backend rejects invalid tipo/documentoTipo combinations at create and update, satisfying the backend half of CLI-24; frontend-side filtering/validation (Plan 03) is a UX layer on top, not the security boundary.
- No blockers for Plan 02 (frontend Zod schema) or Plan 03 (frontend dropdown filtering).

---
*Phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo*
*Completed: 2026-07-03*
