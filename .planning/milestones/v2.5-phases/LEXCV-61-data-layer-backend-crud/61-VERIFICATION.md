---
phase: 61-data-layer-backend-crud
verified: 2026-06-30T20:00:00Z
status: passed
score: 11/11 must-haves verified
overrides_applied: 0
---

# Phase 61: Data Layer & Backend CRUD — Verification Report

**Phase Goal:** Existe uma base de dados e API funcionais para criar, atribuir e listar solicitações de parecer, com RBAC dedicado
**Verified:** 2026-06-30T20:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | ParecerSolicitacao entity persists to t_parecer_solicitacao with all required fields, FKs, and auto createdAt | VERIFIED | `backend/.../models/ParecerSolicitacao.java` declares `@Table(name="t_parecer_solicitacao")`, all 10 fields exactly as specified (id, tenantId not-null, clienteId not-null, descricao TEXT not-null, processoId/advogadoId nullable, prioridade default MEDIA, status default PENDENTE, prazo, createdAt via `@PrePersist`) |
| 2 | Repository can fetch all rows for a given tenantId | VERIFIED | `ParecerSolicitacaoRepository.findByTenantId(UUID)` declared, extends `JpaRepository<ParecerSolicitacao, UUID>` |
| 3 | pareceres:view/create/edit/manage seeded with correct per-role assignment | VERIFIED | `DatabaseSeeder.java:302` declares all 4 keys; ADMIN gets all via blanket `permissionMap.values()`; ADVOGADO (lines 333-347) gets view+create+edit; TECNICO (320-330) and ASSISTENTE (313-320) get view only; `pareceres:manage` appears nowhere in a role-specific list |
| 4 | pareceres scope mirrored in web/src/lib/permissions.ts | VERIFIED | `KNOWN_SCOPES` array includes `"pareceres"` alongside the 5 existing scopes; `PermissionScope` type derived; signatures of `resolveScopedPermissions`/`hasScopedPermission` unchanged (still `scope: string`) |
| 5 | User with pareceres:create can POST a solicitacao; clienteId/descricao mandatory (400 if missing) | VERIFIED | `ParecerController.createSolicitacao` checks both fields, returns 400 with explicit messages; `@PreAuthorize("hasAuthority('pareceres:create')")` present |
| 6 | createdAt auto-populated, satisfies PARC-01 "data da solicitação" | VERIFIED | Entity `@PrePersist onCreate()` sets `createdAt = LocalDateTime.now()`; controller never sets it manually |
| 7 | Solicitacao can carry optional processoId | VERIFIED | `processoId` nullable column; controller validates tenant ownership only when non-null (`processoBelongsToTenant`) |
| 8 | User can assign/reassign advogado via PUT /{id}/atribuir; rejected if not ADVOGADO role, wrong tenant, or status CONCLUIDO | VERIFIED | `atribuirAdvogado` validates via shared `validateAdvogado` (checks tenant match + `Role.getNome().equals("ADVOGADO")`), rejects when `status.equals("CONCLUIDO")` before validation |
| 9 | Assigning advogado sets status to EM_ELABORACAO; unassigned stays PENDENTE | VERIFIED | Both `createSolicitacao` (when advogadoId present) and `atribuirAdvogado` set `status = "EM_ELABORACAO"` on success; create defaults to `"PENDENTE"` otherwise |
| 10 | User with pareceres:view can GET tenant-scoped list and single solicitacao by id | VERIFIED | `listSolicitacoes` and `getSolicitacao` both annotated `@PreAuthorize("hasAuthority('pareceres:view')")`; `getSolicitacao` returns 404 on tenant mismatch |
| 11 | GET list supports optional clienteId/advogadoId/status filters (AND-combined), no params = full list | VERIFIED | Stream filter chain in `listSolicitacoes`: each predicate short-circuits to `true` when its param is null; status comparison case-sensitive (fixed via WR-02) |

**Score:** 11/11 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/.../models/ParecerSolicitacao.java` | JPA entity for parecer requests | VERIFIED | Exists, all fields/annotations match plan exactly |
| `backend/.../repositories/ParecerSolicitacaoRepository.java` | tenant-scoped repository | VERIFIED | `findByTenantId` present |
| `backend/.../seed/DatabaseSeeder.java` | pareceres RBAC scopes | VERIFIED | All 4 scope keys + correct role assignment confirmed by direct read |
| `web/src/lib/permissions.ts` | frontend mirror of pareceres scope | VERIFIED | `KNOWN_SCOPES`/`PermissionScope` present |
| `backend/.../controllers/ParecerController.java` | CRUD + filtered list + assignment endpoints | VERIFIED | 5 endpoints (POST, GET list, GET by-id, PUT, PUT /atribuir), 251 lines, well above 90-line minimum |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|----|--------|---------|
| `DatabaseSeeder.seedRbac` | ADVOGADO role | `upsertRolePermissions` | WIRED | `pareceres:create` present in ADVOGADO's permission list (line 345-347) |
| `web/src/lib/permissions.ts` | pareceres scope | `KNOWN_SCOPES` declaration | WIRED | literal `"pareceres"` present in array |
| `ParecerController.atribuir` | UserRepository + Role check | advogado role validation | WIRED | `validateAdvogado` calls `userRepository.findById`, checks `user.getRoles().stream().anyMatch(r -> "ADVOGADO".equals(r.getNome()))` |
| `ParecerController` | ParecerSolicitacaoRepository | `findByTenantId`/`save`/`findById` | WIRED | All three methods used across create/list/get/update/atribuir handlers |
| `ParecerController.list` | query-param filtering | stream filter on findByTenantId result | WIRED | `@RequestParam(required=false)` x3, stream filter chain confirmed |
| `@PreAuthorize` scopes | seeded permission keys | string literal match | WIRED | `pareceres:create`, `pareceres:view` (x2), `pareceres:edit` (x2) in controller all match seeded keys exactly |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles | `mvn -DskipTests -q compile` (backend/) | Exit 0, no errors | PASS |
| Frontend typechecks | `pnpm exec tsc --noEmit -p tsconfig.json` (web/) | Exit 0, no errors | PASS |
| Commits exist in git history | `git log --oneline \| grep <hashes>` | All 6 referenced commits (10f6ce6, 107d0a3, 8c4ac79, 4eb5d67, 0c5cf02, 5cab55a) found | PASS |

Note: The 61-01-SUMMARY.md documented a deviation — `pnpm exec tsc --noEmit` could not run at execution time because `web/node_modules` was missing in that worktree. Re-running it now (post-merge, dependencies installed) confirms the typecheck passes cleanly, closing that gap.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PARC-01 | 61-02 | Criar solicitação (cliente, descrição, data, prazo, urgência) | SATISFIED | POST handler validates clienteId/descricao, accepts prazo/prioridade, createdAt auto-set |
| PARC-02 | 61-02 | Associação opcional a Processo | SATISFIED | processoId nullable, validated when present |
| PARC-03 | 61-02 | Atribuir/reatribuir advogado (role ADVOGADO) | SATISFIED | `/atribuir` endpoint with full role/tenant/status validation |
| PARC-04 | 61-01, 61-02 | Status (PENDENTE, EM_ELABORACAO, EM_REVISAO, CONCLUIDO) | SATISFIED | Entity status field documented with all 4 values; state transitions implemented for PENDENTE→EM_ELABORACAO; EM_REVISAO/CONCLUIDO reserved for later phases (correctly out of scope here) |
| PARC-05 | 61-02 | Listar e filtrar (cliente, advogado, status) | SATISFIED | GET list with 3 optional AND-combined filters |
| PARC-06 | 61-02 | Ver detalhe de solicitação | SATISFIED | GET /{id} with tenant-scoped 404 |
| PARC-10 | 61-01 | Scope pareceres:* seeded + @PreAuthorize + frontend mirror | SATISFIED | All three layers verified above |

All 7 requirement IDs declared in phase plans (PARC-01, 02, 03, 04, 05, 06, 10) are accounted for and match REQUIREMENTS.md traceability table exactly. No orphaned requirements found.

### Anti-Patterns Found

None. Grep for TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER across all 5 modified files returned zero matches. Code review (61-REVIEW.md) confirms 0 Critical, 0 Warning findings — only 3 Info-level notes (active-flag check, "unassigned only" filter expressiveness, prioridade validation), all explicitly deferred/accepted as out of scope for this phase, none of which block the phase goal.

### Human Verification Required

None. All must-haves are verifiable via code inspection, compilation, and grep — no UI, visual, or runtime-dependent behavior in this backend-only phase.

### Gaps Summary

No gaps. All observable truths verified directly against the codebase (not SUMMARY claims). Backend compiles cleanly, frontend typechecks cleanly, all 6 commits exist in git history, RBAC scope assignment matches the CONTEXT.md table exactly, tenant isolation enforced at every endpoint (cross-tenant IDOR issues found in code review — CR-01/CR-02 — were fixed in commit 0c5cf02 and confirmed resolved by direct code reading), and all 7 requirement IDs are satisfied with concrete evidence.

---

_Verified: 2026-06-30T20:00:00Z_
_Verifier: Claude (gsd-verifier)_
