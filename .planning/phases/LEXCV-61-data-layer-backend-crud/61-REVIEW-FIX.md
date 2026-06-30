---
phase: 61-data-layer-backend-crud
fixed_at: 2026-06-30T18:26:00Z
review_path: .planning/phases/LEXCV-61-data-layer-backend-crud/61-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 61: Code Review Fix Report

**Fixed at:** 2026-06-30T18:26:00Z
**Source review:** .planning/phases/LEXCV-61-data-layer-backend-crud/61-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (CR-01, CR-02, WR-01, WR-02, WR-03)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### CR-01: clienteId is never validated against the caller's tenant (cross-tenant data linkage / IDOR)

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** 0c5cf02
**Applied fix:** Injected `ClienteRepository`, added `clienteBelongsToTenant(UUID, UUID)` helper, and call it in both `createSolicitacao` (before persisting) and `updateSolicitacao` (before applying `payload.getClienteId()`). Cross-tenant or non-existent `clienteId` now returns 400 with message "clienteId inválido ou não pertence a este tenant".

### CR-02: processoId is never validated against the caller's tenant (cross-tenant data linkage)

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** 0c5cf02
**Applied fix:** Injected `ProcessoRepository`, added `processoBelongsToTenant(UUID, UUID)` helper, and call it (when `processoId` is non-null) in both `createSolicitacao` and `updateSolicitacao`. Cross-tenant `processoId` now returns 400 with message "processoId inválido ou não pertence a este tenant".

### WR-01: createSolicitacao accepts attacker-controlled id, status, createdAt fields via @RequestBody binding

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** 0c5cf02
**Applied fix:** `createSolicitacao` no longer saves the raw `@RequestBody` entity. It now constructs a fresh `ParecerSolicitacao` from an explicit allowlist (`tenantId`, `clienteId`, `processoId`, `descricao`, `prazo`, `prioridade`, `advogadoId`). `status` is always forced to `"PENDENTE"` and only promoted to `"EM_ELABORACAO"` when a valid `advogadoId` is supplied — it is never taken directly from client input. `id`/`createdAt` are left server-generated since the new entity instance never has them set from the body.

### WR-02: GET list status filter inconsistent case-sensitivity vs atribuir CONCLUIDO guard

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** 0c5cf02
**Applied fix:** Changed `listSolicitacoes`'s status filter from `status.equalsIgnoreCase(p.getStatus())` to `status.equals(p.getStatus())`, matching the case-sensitive `.equals("CONCLUIDO")` check already used in `atribuirAdvogado`. Chose case-sensitive as recommended in the review since status values are uppercase constants throughout the codebase (and WR-01's fix now guarantees status is always set server-side to one of the canonical uppercase values).

### WR-03: Malformed-UUID and missing-UUID error paths in atribuirAdvogado return the same misleading message

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** 0c5cf02
**Applied fix:** The `catch (IllegalArgumentException e)` block (triggered when `UUID.fromString` fails on a malformed-but-present `advogadoId`) now returns `"advogadoId inválido"`, distinct from the missing-field message `"advogadoId é obrigatório"` used when the field is absent/blank.

## Skipped Issues

None — all in-scope findings (CR-01, CR-02, WR-01, WR-02, WR-03) were fixed. IN-01 and IN-02 were excluded per the requested fix scope (Critical + Warning only).

**Verification:** `mvn -DskipTests compile` ran clean (BUILD SUCCESS) after all fixes were applied.

---

_Fixed: 2026-06-30T18:26:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
