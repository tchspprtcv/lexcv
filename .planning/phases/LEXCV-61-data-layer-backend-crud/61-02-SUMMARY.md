---
phase: 61-data-layer-backend-crud
plan: 02
status: complete
---

# Summary: ParecerController — CRUD, Filterable List, and Advogado Assignment

## What was built

`ParecerController` (`backend/src/main/java/com/lexcv/controllers/ParecerController.java`), a new dedicated controller under `/api/v1/pareceres/solicitacoes`, exposing five endpoints:

1. **POST `""`** (`pareceres:create`) — creates a `ParecerSolicitacao`. Always overwrites `tenantId` from the security context (never trusts the body). Returns 400 if `clienteId` is null or `descricao` is null/blank. If `advogadoId` is supplied, validates it via `validateAdvogado` and auto-sets status to `EM_ELABORACAO`; otherwise status stays at the entity default `PENDENTE`. `createdAt` (PARC-01 "data da solicitação") is populated automatically by the entity's `@PrePersist`.
2. **GET `""`** (`pareceres:view`) — tenant-scoped filterable list (PARC-05). Accepts optional `clienteId`, `advogadoId`, `status` query params; starts from `findByTenantId(getTenantId())` and applies stream predicates that only constrain when the param is present (status comparison is case-insensitive). No params returns the full tenant list.
3. **GET `"/{id}"`** (`pareceres:view`) — tenant-scoped 404 idiom (404 if not found or tenant mismatch).
4. **PUT `"/{id}"`** (`pareceres:edit`) — updates `prazo`, `prioridade`, `clienteId`, `processoId` only. Excludes `status` and `advogadoId` (status is a state machine; advogadoId is set only via `/atribuir`), mirroring the `Processo.estado` exclusion pattern in `ResourceController`.
5. **PUT `"/{id}/atribuir"`** (`pareceres:edit`) — assigns/reassigns an advogado. Parses `advogadoId` from a `Map<String,String>` body (400 if missing/blank/unparseable). Rejects with 400 if the target solicitacao's status is `CONCLUIDO`. Validates the advogado via the shared `validateAdvogado` helper (null user, cross-tenant user, or no `ADVOGADO` role all reject with 400). On success sets `advogadoId` and transitions status to `EM_ELABORACAO`.

A private `validateAdvogado(UUID advogadoId, UUID tenantId)` helper is shared by both POST create and PUT atribuir, so the role/tenant validation rule (load `User`, check `tenantId` match, check `user.getRoles().stream().anyMatch(r -> "ADVOGADO".equals(r.getNome()))`) lives in exactly one place, per the plan's Task 2 refactor instruction.

Both plan tasks (1: CRUD+list, 2: assignment+shared helper) were implemented together in a single file creation, since Task 2 explicitly required refactoring Task 1's inline advogado validation into the shared helper — splitting into two separate commits would have meant committing then immediately rewriting the same lines. Committed as one atomic, coherent commit.

## Tasks completed

- Task 1 + Task 2 (ParecerController: CRUD, filterable list, assignment, shared validateAdvogado helper) — commit `4eb5d67`

## Verification

- `mvn -DskipTests -q compile` — passed (no errors/output).
- `grep -c '@RequestParam' ParecerController.java` → `3` (clienteId, advogadoId, status on the list endpoint).
- `grep -c 'ADVOGADO' ParecerController.java` → `4` (role-check usages across validateAdvogado, javadoc, etc.).

## Requirements delivered

- PARC-01 — solicitação creation with clienteId/descricao/prazo/prioridade; createdAt auto-populated.
- PARC-02 — optional processoId on solicitação.
- PARC-03 — advogado assignment/reassignment endpoint with role + tenant + non-CONCLUIDO validation.
- PARC-04 — persistence contract exercised end-to-end via the new controller (entity/repo from Plan 01).
- PARC-05 — filterable list (clienteId/advogadoId/status query params, tenant-scoped, AND-combined).
- PARC-06 — GET list and GET-by-id under `pareceres:view`.

## Notes for downstream plans

- Phase 62 (elaboração/versionamento) can build directly on this controller's tenant-scoped `ParecerSolicitacao` CRUD; no DTO layer exists yet for this module (entities returned directly, per `61-CONTEXT.md`).
- No pagination on the list endpoint (per CONTEXT.md) — revisit if solicitacao volume grows.
- `pareceres:manage` scope (seeded in Plan 01) is not yet used by any endpoint — reserved for Phase 63 approval/delivery flows.
