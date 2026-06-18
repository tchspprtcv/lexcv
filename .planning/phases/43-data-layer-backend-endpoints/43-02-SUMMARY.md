---
phase: 43-data-layer-backend-endpoints
plan: "02"
subsystem: backend
tags: [financeiro, honorarios, pagamentos, crud, spring-boot]
dependency_graph:
  requires: []
  provides: [GET /honorarios/{id}, PUT /honorarios/{id}, DELETE /honorarios/{id}, DELETE /pagamentos/{id}]
  affects: [ResourceController.java]
tech_stack:
  added: []
  patterns: [tenant-scoping via processo chain, @PreAuthorize authority checks, ContaCorrente saldo reversal]
key_files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
decisions:
  - deletePagamento only subtracts from ContaCorrente if cc != null (no create during deletion — differs from createPagamento)
  - processoId is never read from request body in updateHonorario (immutability enforced server-side)
  - All four endpoints return 404 (not 403) on tenant mismatch to avoid leaking resource existence
metrics:
  duration: "~10 minutes"
  completed: "2026-06-18"
---

# Phase 43 Plan 02: Honorarios and Pagamentos CRUD Endpoints Summary

**One-liner:** Four missing CRUD endpoints for honorarios/pagamentos added to ResourceController with tenant scoping, RBAC, and ContaCorrente saldo reversal on payment deletion.

## What Was Built

Added four new handler methods to `ResourceController.java` immediately after the existing `createPagamento` method (before the DASHBOARD block):

| Endpoint | Authority | Behavior |
|----------|-----------|----------|
| GET /api/v1/honorarios/{id} | financeiro:view | Returns honorario JSON; 404 if not found or wrong tenant |
| PUT /api/v1/honorarios/{id} | financeiro:edit | Updates valorTotal/descricao/dataAcordo; processoId immutable |
| DELETE /api/v1/honorarios/{id} | financeiro:manage | 409 Conflict if pagamentos exist; 204 if none |
| DELETE /api/v1/pagamentos/{id} | financeiro:manage | Subtracts valorPago from ContaCorrente saldo; 204 on success |

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add GET/PUT /honorarios/{id} | e8b0591 | ResourceController.java |
| 2 | Add DELETE /honorarios/{id} and DELETE /pagamentos/{id} | e8b0591 | ResourceController.java |

## Deviations from Plan

None — plan executed exactly as written.

## Threat Mitigations Applied

- T-43-02-01: GET /honorarios/{id} returns 404 (not 403) on tenant mismatch — existence not leaked
- T-43-02-02: PUT /honorarios/{id} never reads processoId from request body — enforced server-side
- T-43-02-03: DELETE /honorarios/{id} checks pagamentoRepository.findByHonorarioId before delete — 409 guard
- T-43-02-04: DELETE /pagamentos/{id} traverses pag → hon → processo → getTenantId() before delete

## Known Stubs

None.

## Self-Check: PASSED

- `e8b0591` exists in git log
- All four methods (`getHonorario`, `updateHonorario`, `deleteHonorario`, `deletePagamento`) present in ResourceController.java
- `mvn -DskipTests package` exited 0
- CONFLICT guard and `subtract` confirmed in file
