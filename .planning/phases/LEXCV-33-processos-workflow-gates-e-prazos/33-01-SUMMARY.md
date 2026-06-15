---
phase: 33-processos-workflow-gates-e-prazos
plan: "01"
subsystem: backend
tags: [workflow, state-machine, prazos, rbac, multi-tenant, spring-boot]
dependency_graph:
  requires:
    - 32-01 (ConflictCheckDecisao/formalizar/Movimentacao patterns)
  provides:
    - Prazo entity + PrazoRepository (tenant-scoped)
    - GET /processos/{id}/workflow (WorkflowResponse with TransicaoInfo)
    - POST /processos/{id}/transicao/{acao} (state-machine with 3 gates)
    - GET/POST /processos/{id}/prazos (with computed risco)
    - PATCH /processos/{id}/prazos/{prazoId}/concluido (returns risco)
    - Enriched GET /processos (responsavel_nome, risco_mais_critico, tem_prazo_escalonado)
  affects:
    - ResourceController.listProcessos (return shape changed to LinkedHashMap)
    - Processo.java (responsavel_id added)
tech_stack:
  added:
    - WorkflowResponse (nested TransicaoInfo) DTO record
    - TransicaoRequest DTO record
    - PrazoRequest DTO record
    - Prazo JPA entity (t_prazo)
    - PrazoRepository (Spring Data JPA)
  patterns:
    - TRANSICOES_PERMITIDAS static Map as single source of truth for state machine
    - computeRisco() private helper: risco derived at response time, never stored
    - Inline manage-permission gate via SecurityContextHolder.getContext().getAuthentication()
    - N+1 prevention: prazoRepository.findByTenantId() + in-memory groupBy processoId
key_files:
  created:
    - backend/src/main/java/com/lexcv/models/Prazo.java
    - backend/src/main/java/com/lexcv/repositories/PrazoRepository.java
    - backend/src/main/java/com/lexcv/dtos/TransicaoRequest.java
    - backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java
    - backend/src/main/java/com/lexcv/dtos/PrazoRequest.java
  modified:
    - backend/src/main/java/com/lexcv/models/Processo.java (responsavel_id added)
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java (workflow, transicao, prazos endpoints + listing enrichment)
decisions:
  - TRANSICOES_PERMITIDAS map contains ATIVO/SUSPENSO/ENCERRADO keys only; TRIAGEM->ATIVO remains in /formalizar (Phase 32)
  - risco (ok/proximo/vencido) is computed at response time by computeRisco(), never stored as a column
  - PATCH /concluido returns a response map with recomputed risco field (verifier W1) instead of raw entity
  - listProcessos now returns List<LinkedHashMap> to carry enriched fields; field names preserved for existing frontend normalizeProcesso
  - Inline manage-gate checked via SecurityContextHolder authorities (not redundant @PreAuthorize) to allow floor of processos:edit on the POST endpoint
  - N+1 avoided by fetching all tenant prazos once and grouping in-memory per processoId in listProcessos
metrics:
  duration: "~30 minutes"
  completed: "2026-06-15T13:44:07Z"
  tasks_completed: 3
  files_created: 5
  files_modified: 2
---

# Phase 33 Plan 01: Workflow Gates e Prazos Backend Summary

**One-liner:** Backend state-machine enforcement for processos workflow (ATIVO/SUSPENSO/ENCERRADO) with 3-gate transition validation, new Prazo entity with server-derived risco, and enriched listProcessos including responsavel_nome and risco_mais_critico.

## What Was Built

### Task 1 — Entidade Prazo, repositorio, campo responsavel_id e DTOs (commit b54cdc1)

- **Prazo.java** — JPA entity mapped to `t_prazo`; UUID PK generated via `GenerationType.UUID`; tenant-scoped; fields: tenantId, processoId, descricao, dataLimite (LocalDate), prioridade (Builder.Default "MEDIA"), responsavelId, concluido (Builder.Default false), escalonado (Builder.Default false), createdAt (@PrePersist). No `risco` column — risco is computed at response time.
- **PrazoRepository.java** — `findByTenantIdAndProcessoIdOrderByDataLimiteAsc` and `findByTenantId` derived finders.
- **Processo.java** — Added `responsavel_id` UUID column (nullable=true; ddl-auto=update handles schema migration).
- **TransicaoRequest.java** — record with nullable `justificativa`.
- **WorkflowResponse.java** — record with nested `TransicaoInfo` (acao, label, permissaoNecessaria, requerJustificativa).
- **PrazoRequest.java** — record with descricao, dataLimite, prioridade, responsavelId.

### Task 2 — Endpoints workflow + transicao (commit 826d2ce)

- **TRANSICOES_PERMITIDAS** static Map: ATIVO->[suspender(manage,true), encerrar(manage,true)], SUSPENSO->[ativar(edit,false), encerrar(manage,true)], ENCERRADO->[reabrir(manage,true)]. TRIAGEM absent by design.
- **derivarProximoPasso()** private helper returning human-readable next step per estado.
- **GET /processos/{id}/workflow** — returns WorkflowResponse; resolves responsavel from same-tenant userRepository lookup; @PreAuthorize processos:view + tenant guard.
- **POST /processos/{id}/transicao/{acao}** — 3-gate validation: (0) estado gate returns 409 CONFLICT when acao not in TRANSICOES_PERMITIDAS for current estado; (1) manage permission gate returns 403 FORBIDDEN via inline SecurityContextHolder check; (2) justificativa gate returns 400 BAD_REQUEST when blank. On success: sets novo estado, creates TRANSICAO_ESTADO Movimentacao with author from UserPrincipal, saves processo. @Transactional + @PreAuthorize processos:edit.

### Task 3 — Endpoints Prazos + enriquecimento listProcessos (commit b70cb72)

- **computeRisco()** — null dataLimite -> "ok"; past dataLimite -> "vencido"; days remaining <= threshold (7 for ALTA, 3 otherwise) -> "proximo"; else "ok".
- **GET /processos/{id}/prazos** — list ordered by dataLimite asc; each item includes computed `risco`; @PreAuthorize processos:view + tenant guard.
- **POST /processos/{id}/prazos** — builds Prazo via builder; auto-computes `escalonado=true` when risco is proximo/vencido; returns response map with computed risco; @Transactional + @PreAuthorize processos:edit.
- **PATCH /processos/{id}/prazos/{prazoId}/concluido** — verifies prazo.tenantId and prazo.processoId match; sets concluido; returns response map with **recomputed risco** field (W1 requirement); @Transactional + @PreAuthorize processos:edit.
- **listProcessos enrichment** — fetches all prazos by tenantId once (avoids N+1), groups in-memory by processoId, then for each processo: resolves responsavel_nome via userRepository (same-tenant guard), computes risco_mais_critico (vencido > proximo > ok) and tem_prazo_escalonado from non-concluido prazos. Returns List<LinkedHashMap> preserving all existing field names.

## Deviations from Plan

None — plan executed exactly as written, with one addition:

**[Rule 2 - Missing critical functionality] PATCH /concluido returns response map with risco field**
- **Found during:** Task 3
- **Issue:** The build_note (verifier warning W1) required the PATCH endpoint to return a response map including the recomputed `risco` field, not the raw entity, so the frontend cache keeps the risco badge current after a toggle.
- **Fix:** PATCH endpoint builds and returns a LinkedHashMap matching the GET /prazos item shape, with `risco` computed via `computeRisco()`.
- **Files modified:** ResourceController.java (togglePrazoConcluido method)
- **Commit:** b70cb72

## Known Stubs

None — all backend logic is fully implemented. Frontend surface for Phase 33 (Workflow card, Prazos card, listing signals) is covered in Plan 33-02.

## Threat Surface Scan

All new endpoints/fields are covered by the plan's threat model:
- T-33-01: Inline manage permission gate present in executarTransicao
- T-33-02: Estado gate rejects unknown acao with 409
- T-33-03: Author resolved from UserPrincipal (SecurityContext), never from request payload
- T-33-04: Tenant guard `!getTenantId().equals(...)` present in all new handlers; prazo toggle also verifies prazo.processoId and prazo.tenantId
- T-33-05: responsavelId stored but resolved via userRepository with tenant check before display
- T-33-06: Justificativa mandatory for suspender/encerrar/reabrir; recorded in Movimentacao

## Self-Check

Files exist:
- [x] backend/src/main/java/com/lexcv/models/Prazo.java
- [x] backend/src/main/java/com/lexcv/repositories/PrazoRepository.java
- [x] backend/src/main/java/com/lexcv/dtos/TransicaoRequest.java
- [x] backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java
- [x] backend/src/main/java/com/lexcv/dtos/PrazoRequest.java
- [x] Processo.java modified with responsavel_id
- [x] ResourceController.java modified with all endpoints

Commits:
- [x] b54cdc1 — Task 1
- [x] 826d2ce — Task 2
- [x] b70cb72 — Task 3

Compile: BUILD SUCCESS (all 3 tasks verified)

## Self-Check: PASSED
