---
phase: 32-processos-intake-e-conflict-check
plan: "01"
subsystem: backend
tags: [intake, conflict-check, rbac, jpa, spring-boot]
dependency_graph:
  requires: []
  provides:
    - ConflictCheckDecisao JPA entity (t_conflict_check_decisao, tenant-scoped)
    - ConflictCheckDecisaoRepository with findByTenantIdAndProcessoId
    - POST /processos/intake — creates process in TRIAGEM
    - POST /processos/{id}/conflict-check — tenant-scoped match search
    - GET /processos/{id}/conflict-check/decisao — fetch saved decision
    - POST /processos/{id}/conflict-check/decisao — upsert conflict decision
    - POST /processos/{id}/formalizar — TRIAGEM->ATIVO with dual enforcement
    - processos:create and processos:manage seeded for ADMIN and ADVOGADO
  affects:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
tech_stack:
  added: []
  patterns:
    - JPA entity with tenant_id column, @GeneratedValue(UUID), @PrePersist createdAt
    - Spring Data JPA named finders (findByTenantIdAndProcessoId)
    - Java records for DTOs (request/response)
    - @PreAuthorize method-level RBAC with scope:action convention
    - CAMPOS_MINIMOS_POR_TIPO static Map as source of truth for mandatory fields per tipo_processo
    - Dual-block enforcement in single formalizar endpoint (campos minimos THEN conflict decision)
key_files:
  created:
    - backend/src/main/java/com/lexcv/models/ConflictCheckDecisao.java
    - backend/src/main/java/com/lexcv/repositories/ConflictCheckDecisaoRepository.java
    - backend/src/main/java/com/lexcv/dtos/ConflictCheckRequest.java
    - backend/src/main/java/com/lexcv/dtos/ConflictCheckDecisaoRequest.java
    - backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
decisions:
  - "ConflictCheckDecisaoRequest created as separate file (not nested in ConflictCheckRequest.java) — Java does not allow two public top-level types in one file"
  - "CAMPOS_MINIMOS_POR_TIPO defined as static Map<String, List<String>> with uppercase keys matching tipoProcesso.toUpperCase() and a 'default' fallback"
  - "Dual-block order in formalizar: campos minimos checked FIRST (422), conflict decision checked SECOND (409) — a single enforcement point per CONTEXT.md specifics"
  - "runConflictCheck returns nivelSugerido 'potencial' for any match, 'sem_conflito' when no matches — final decision is always human"
metrics:
  duration: "~35 minutes"
  completed_date: "2026-06-13"
  tasks_completed: 3
  files_created: 5
  files_modified: 2
---

# Phase 32 Plan 01: Intake e Conflict Check — Backend Summary

**One-liner:** Backend enforcement layer for structured processo intake (TRIAGEM state) and bloqueante conflict check with JPA decision entity, tenant-scoped match search, and server-side TRIAGEM->ATIVO transition gating.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Entidade, repositorio e DTOs da decisao de conflito | `00b17b7` | ConflictCheckDecisao.java, ConflictCheckDecisaoRepository.java, ConflictCheckRequest.java, ConflictCheckResponse.java |
| 2 | Seed das permissoes processos:create e processos:manage | `1345308` | DatabaseSeeder.java |
| 3 | Endpoints intake, conflict-check, decisao, formalizar com bloqueio server-side | `8d1e635` | ResourceController.java, ConflictCheckDecisaoRequest.java |

## What Was Built

### Entity & Repository (Task 1)
- `ConflictCheckDecisao` JPA entity mapped to `t_conflict_check_decisao` — tenant-scoped via `tenant_id` column, UUID primary key, stores `nivel`, `justificativa`, `decisorId`, `dataDecisao`, `referenciaEvidencia`, and `createdAt` (@PrePersist)
- `ConflictCheckDecisaoRepository` with `findByTenantId` and `findByTenantIdAndProcessoId` Spring Data finders
- `ConflictCheckResponse` record with nested `ConflictMatchDto` (entidadeId, entidadeTipo, nome, nif, nivelConflito, motivo)
- `ConflictCheckRequest` (processoId) and `ConflictCheckDecisaoRequest` (nivel, justificativa, referenciaEvidencia) DTOs

### RBAC Seed (Task 2)
- `processos:create` and `processos:manage` added to `permKeys` list in `seedRbac()`
- ADMIN receives both via `permissionMap.values()` (unchanged behavior)
- ADVOGADO receives both (can run conflict check, register decision, formalize)
- ASSISTENTE and TECNICO remain at `processos:view` only — no processos:manage

### Endpoints (Task 3)
- `POST /api/v1/processos/intake` (`processos:create`): forces `setEstado("TRIAGEM")` regardless of payload; saves and returns 201
- `POST /api/v1/processos/{id}/conflict-check` (`processos:create`): tenant-scoped NIF exact match + nome approximate match against clients and partes; returns `ConflictCheckResponse(matches, nivelSugerido)`
- `GET /api/v1/processos/{id}/conflict-check/decisao` (`processos:view`): returns saved decision or `null` body (200) when not yet registered
- `POST /api/v1/processos/{id}/conflict-check/decisao` (`processos:manage`): validates nivel enum, requires justificativa for potencial/sanavel overrides, upserts decision with decisorId from SecurityContext (never from payload)
- `POST /api/v1/processos/{id}/formalizar` (`processos:manage`): single enforcement point — checks campos minimos (422 + camposEmFalta list) BEFORE checking conflict decision (409 if absent or impeditivo), then sets estado=ATIVO

### CAMPOS_MINIMOS_POR_TIPO
Static `Map<String, List<String>>` constant in ResourceController with entries for CIVIL, PENAL, LABORAL, ADMINISTRATIVO, FAMILIA, COMERCIAL, and a `"default"` fallback — ensures every tipo_processo has a defined mandatory field set.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] ConflictCheckDecisaoRequest extracted to separate file**
- **Found during:** Task 3
- **Issue:** The plan specified both `ConflictCheckRequest` and `ConflictCheckDecisaoRequest` could live in `ConflictCheckRequest.java`, but Java requires each public top-level type in its own file
- **Fix:** Created `ConflictCheckDecisaoRequest.java` as a separate file in `com.lexcv.dtos`
- **Files modified:** backend/src/main/java/com/lexcv/dtos/ConflictCheckDecisaoRequest.java (new)
- **Commit:** 8d1e635

## Known Stubs

None — all endpoints return real data from the database via tenant-scoped queries.

## Threat Flags

No new threat surfaces beyond those already registered in the plan's threat model. All endpoints use tenant guard + @PreAuthorize as specified in T-32-01 through T-32-05.

## Self-Check: PASSED

Files verified:
- FOUND: backend/src/main/java/com/lexcv/models/ConflictCheckDecisao.java
- FOUND: backend/src/main/java/com/lexcv/repositories/ConflictCheckDecisaoRepository.java
- FOUND: backend/src/main/java/com/lexcv/dtos/ConflictCheckRequest.java
- FOUND: backend/src/main/java/com/lexcv/dtos/ConflictCheckDecisaoRequest.java
- FOUND: backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java
- FOUND: backend/src/main/java/com/lexcv/controllers/ResourceController.java (modified)
- FOUND: backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java (modified)

Commits verified:
- 00b17b7: feat(32-01): add ConflictCheckDecisao entity, repository, and DTOs
- 1345308: feat(32-01): seed processos:create and processos:manage permissions
- 8d1e635: feat(32-01): add intake, conflict-check, decisao, and formalizar endpoints

Build: `mvn -DskipTests compile` -> BUILD SUCCESS
