---
phase: 79-documentos-entregues-upload-real
plan: 01
subsystem: api
tags: [spring-boot, rest, tenant-scoping, documentos, clientes]

# Dependency graph
requires:
  - phase: null
    provides: existing Documento entity/repository, existing listProcessoDocumentos pattern
provides:
  - "GET /clientes/{id}/documentos tenant-scoped listing endpoint"
affects: [79-02 (frontend Documentos Entregues tab)]

# Tech tracking
tech-stack:
  added: []
  patterns: ["cliente sub-resource GET endpoint mirroring listProcessoDocumentos/listClienteContactos idiom"]

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "Gated by documentos:view (not clientes:view) per CONTEXT.md RBAC decision to keep documentos:* scopes consistent with listDocumentos/listProcessoDocumentos/downloadDocumento"

patterns-established: []

requirements-completed: [CLI-27]

# Metrics
duration: 8min
completed: 2026-07-06
---

# Phase 79 Plan 01: Cliente Documentos Listing Endpoint Summary

**New `GET /clientes/{id}/documentos` REST endpoint returns tenant-scoped `Documento` records for a cliente, mirroring the existing `listProcessoDocumentos` pattern exactly.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-07-06T14:45:00Z
- **Completed:** 2026-07-06T14:53:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- Added `listClienteDocumentos` controller method to `ResourceController.java`, gated by `@PreAuthorize("hasAuthority('documentos:view')")` and mapped to `@GetMapping("/clientes/{id}/documentos")`.
- Tenant-scoping enforced twice: cliente lookup returns 404 (`Cliente não encontrado`) on missing/cross-tenant cliente before any document is read, and the document query itself is filtered by `findByTenantIdAndClienteId(getTenantId(), id)`.
- No repository, DTO, or dependency-wiring changes — reused the pre-existing `DocumentoRepository.findByTenantIdAndClienteId` method and already-injected `clienteRepository`/`documentoRepository` fields.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add listClienteDocumentos endpoint** - `ba3eaeb` (feat)

**Plan metadata:** (this commit) (docs: complete plan)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - Added `listClienteDocumentos` GET endpoint (10 lines) immediately after `listProcessoDocumentos`, before `downloadDocumento`.

## Decisions Made
None - followed plan as specified. The one explicit RBAC decision (use `documentos:view`, not `clientes:view`) was already made in CONTEXT.md and applied verbatim.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `GET /clientes/{id}/documentos` is live, compiles cleanly, and `mvn -DskipTests package` succeeds end-to-end (BUILD SUCCESS).
- This was the only backend work in Phase 79. Ready for Plan 02 (frontend "Documentos Entregues" tab) to consume this endpoint for real upload/list/download against a cliente.
- No blockers.

---
*Phase: 79-documentos-entregues-upload-real*
*Completed: 2026-07-06*
