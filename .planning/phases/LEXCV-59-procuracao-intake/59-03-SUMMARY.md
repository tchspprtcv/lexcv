---
phase: "59"
plan: "03"
subsystem: backend-api
tags: [spring-boot, minio, rbac, multi-tenant, procuracao, intake]
dependency-graph:
  requires: [59-01, 59-02]
  provides: [procuracao-endpoints, advogados-endpoints, administrativos-endpoints, cliente-intake-put-extension]
  affects: [59-04, 59-05, 59-06]
tech-stack:
  added: []
  patterns:
    - "Dedicated 1:1 sub-resource endpoint for procuração (not generic /documentos/upload) — Cliente row stores a single MinIO object key"
    - "Upload-before-delete ordering on replace, mirroring existing /documentos/upload pattern"
    - "Server-side role validation (user.getRoles().stream().anyMatch) before persisting Cliente<->User junction rows — never trust client-supplied role claims"
key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
decisions:
  - "Procuração upload uses a dedicated endpoint (POST /clientes/{id}/procuracao) rather than reusing /documentos/upload, per 59-RESEARCH.md D-05 resolution — Cliente.procuracaoKey is a 1:1 field, not a t_documento row"
  - "PUT /clientes/{id} extended to accept descricaoCaso, documentosEntregues, documentosATratar, deslocacoes, honorariosPropostos but explicitly excludes procuracaoKey — that field is managed only by its own endpoint (T-59-06 mitigation)"
metrics:
  duration: "~25 min"
  completed: 2026-06-29
---

# Phase 59 Plan 03: Procuração + Advogados/Administrativos Backend Endpoints Summary

Added 9 new REST endpoints to `ResourceController` (3 for procuração upload/download/delete, 6 for advogados/administrativos sub-resource CRUD) and extended `PUT /clientes/{id}` to persist the new JSON intake fields, completing the backend API surface for Phase 59.

## What Was Built

### Task 1: Procuração endpoints + PUT extension
- `POST /clientes/{id}/procuracao` (multipart) — uploads file to MinIO via `StorageService.upload`, stores the returned object key on `Cliente.procuracaoKey`. On replace, uploads the new object before deleting the old one (safe ordering, matches existing `/documentos/upload` replace path).
- `GET /clientes/{id}/procuracao/download` — returns `{ url, expiresIn: 3600 }` via `StorageService.presignedDownloadUrl`; 404 if `procuracaoKey` is null.
- `DELETE /clientes/{id}/procuracao` — deletes the MinIO object and clears `procuracaoKey`; 404 if already null.
- `PUT /clientes/{id}` (existing `updateCliente`) extended to additionally persist `descricaoCaso`, `documentosEntregues`, `documentosATratar`, `deslocacoes`, `honorariosPropostos` when present in the payload. `procuracaoKey` is deliberately never set here.
- Added `@Autowired` (constructor-injected via Lombok `@RequiredArgsConstructor`) fields `clienteAdvogadoRepository` and `clienteAdministrativoRepository`.

### Task 2: Advogados/Administrativos sub-resource endpoints
- `GET /clientes/{id}/advogados` — lists `User` objects linked via `ClienteAdvogado` junction rows.
- `POST /clientes/{id}/advogados/{userId}` — validates tenant match on cliente and user, validates the user has role `ADVOGADO` (400 if not), checks for duplicate link (409 if exists), persists junction row, returns 201.
- `DELETE /clientes/{id}/advogados/{userId}` (`@Transactional`) — removes junction row.
- Same three endpoints mirrored for administrativos, using `clienteAdministrativoRepository` and role check `ASSISTENTE` OR `TECNICO`.

## Deviations from Plan

None — plan executed exactly as written. All repositories, entities, and `Cliente` fields referenced by this plan (from 59-01 and 59-02) were already present after merging in the latest `master` work (this worktree's branch had been created before 59-01/59-02 landed; merged `origin-local/master` into the worktree branch before starting implementation to pick up those dependencies).

## Verification

- `mvn -DskipTests package` → BUILD SUCCESS
- `grep -nE "@(Get|Post|Delete|Put)Mapping.*(procuracao|advogados|administrativos)"` on ResourceController.java → 9 matches, matching the 9 new endpoint method signatures specified in the plan tasks.
- `updateCliente` handler contains `setDescricaoCaso` and `setHonorariosPropostos`; does NOT contain `setProcuracaoKey` inside its body (confirmed by code review).

## Commits

- `b2a1454` feat(59-03): add procuracao upload/download/delete endpoints and extend PUT cliente
- `043caeb` feat(59-03): add advogados and administrativos sub-resource endpoints

## Self-Check: PASSED
