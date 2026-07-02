---
phase: LEXCV-70-backend-refactoring-seeder-alignment
plan: 01
subsystem: backend
tags: [jpa, jackson, spring-boot, seed-data, cliente]

# Dependency graph
requires: []
provides:
  - "DocumentoTipo enum with REG_COMERCIAL constant"
  - "Cliente entity with dados_tipo JSON blob fully removed"
  - "DatabaseSeeder Empresa client aligned to REG_COMERCIAL"
affects: [cliente-identification, seed-data, documento-tipo]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Flat-column identification model for Cliente (documentoTipo/documentoNumero) replaces JSON @Convert blob pattern"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/models/DocumentoTipo.java
    - backend/src/main/java/com/lexcv/models/Cliente.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java

key-decisions:
  - "Deleted DadosTipo.java and DadosTipoConverter.java as dead code rather than deprecating, since no remaining references existed"
  - "Left orphaned dados_tipo DB column unmapped/undropped (ddl-auto=update never drops columns) rather than adding a migration, per plan's explicit data-migration note"
  - "Did not add a REG_COMERCIAL branch to the NIF-derivation logic in ResourceController.updateCliente, per plan scope (CLI-09 only requires the enum value and documentoNumero storage)"

patterns-established:
  - "Cliente identification data flows entirely through flat columns (documentoTipo, documentoNumero, nif) - no JSON blob for identification fields"

requirements-completed: [CLI-06, CLI-09]

# Metrics
duration: 12min
completed: 2026-07-01
---

# Phase LEXCV-70 Plan 01: Backend Refactoring - Seeder Alignment Summary

**Removed the `dados_tipo` JSON `@Convert` blob from Cliente, added `REG_COMERCIAL` to `DocumentoTipo`, and aligned the seeded Empresa client to use it.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-01T00:00:00Z (approx)
- **Completed:** 2026-07-01T00:12:00Z (approx)
- **Tasks:** 2
- **Files modified:** 4 (2 deleted, 4 modified)

## Accomplishments
- `DocumentoTipo` enum now includes `REG_COMERCIAL` alongside `NIF`, `CNI`, `PASSAPORTE`
- `Cliente` entity converged fully onto the flat-column identification model — `dados_tipo` JSON `@Convert` field removed
- `DadosTipo` and `DadosTipoConverter` deleted as dead code
- `ResourceController.updateCliente` no longer references `dadosTipo`; NIF-derivation logic untouched
- `DatabaseSeeder`'s Empresa client (`cliente2`, "Empresa Atlântico, SA") now uses `DocumentoTipo.REG_COMERCIAL` with its registration number in `documentoNumero`; Singular client (`cliente1`) unchanged (`DocumentoTipo.NIF`)
- Backend compiles and packages cleanly (`mvn -DskipTests package` → BUILD SUCCESS)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add REG_COMERCIAL and remove the dados_tipo JSON model from Cliente** - `a718a9d` (refactor)
2. **Task 2: Align DatabaseSeeder Empresa client to REG_COMERCIAL and verify compile** - `124f9a5` (refactor)

**Plan metadata:** (this commit, following SUMMARY creation)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` - Added `REG_COMERCIAL` enum constant
- `backend/src/main/java/com/lexcv/models/Cliente.java` - Removed `dados_tipo` column/@Convert/@JsonProperty field block
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - Removed `cliente.setDadosTipo(payload.getDadosTipo())` call in `updateCliente`
- `backend/src/main/java/com/lexcv/models/DadosTipo.java` - Deleted (dead code)
- `backend/src/main/java/com/lexcv/models/DadosTipoConverter.java` - Deleted (dead code)
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` - Empresa seed client (`cliente2`) now uses `DocumentoTipo.REG_COMERCIAL`

## Decisions Made
- Deleted `DadosTipo`/`DadosTipoConverter` outright rather than deprecating — confirmed zero remaining references before removal.
- No Flyway/DDL migration added for the orphaned `dados_tipo` column; `ddl-auto=update` in dev never drops columns, so the column stays dormant and unmapped, per plan's explicit guidance to avoid destructive DDL on shared dev DBs.
- Did not extend NIF-derivation logic with a REG_COMERCIAL branch — out of scope per plan; CLI-09 only requires the enum value and number storage in `documentoNumero`.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `Cliente` identification model is now fully flat-column based; no JSON blob remains for identification data.
- `REG_COMERCIAL` is available for future CLI-09 downstream work (e.g., UI-side company-registration identification flows), if any is planned in later phases.
- Sibling JSON `@Convert` fields (`documentosEntregues`, `documentosATratar`, `deslocacoes`, `honorariosPropostos`) and the unrelated `tipo` String/`TipoCliente` enum inconsistency were explicitly left untouched, as scoped.
- No blockers.

---
*Phase: LEXCV-70-backend-refactoring-seeder-alignment*
*Completed: 2026-07-01*

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/models/DocumentoTipo.java
- FOUND: backend/src/main/java/com/lexcv/models/Cliente.java
- FOUND: backend/src/main/java/com/lexcv/controllers/ResourceController.java
- FOUND: backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
- CONFIRMED DELETED: backend/src/main/java/com/lexcv/models/DadosTipo.java
- CONFIRMED DELETED: backend/src/main/java/com/lexcv/models/DadosTipoConverter.java
- FOUND commit: a718a9d
- FOUND commit: 124f9a5
- `mvn -DskipTests package` → BUILD SUCCESS (confirmed during Task 2 execution)
