---
phase: 86-infraestrutura-de-notificacoes-entidade-api-e-targeting
plan: 1
subsystem: database
tags: [jpa, spring-data, postgresql, hibernate, pagination]

# Dependency graph
requires: []
provides:
  - "Notificacao JPA entity (t_notificacao) — one row per (event, recipient), dual NOT NULL scoping columns (tenant_id, destinatario_id), polymorphic entidade_tipo/entidade_id reference"
  - "NotificacaoRepository — dual-scoped (tenant + destinatario) query surface: buscarPorFiltros (native paginated history query), countByTenantIdAndDestinatarioIdAndLidaFalse (unread badge), findByTenantIdAndDestinatarioIdAndLidaFalse (mark-all-read), findByIdAndTenantIdAndDestinatarioId (single mark-read)"
  - "backend/migrations/86-create-notificacao-table.sql — required manual production migration (CREATE TABLE + composite index)"
affects: [86-02, 86-03, phase-87, phase-88, phase-89]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dual-scoped repository finders (tenant_id + destinatario_id) — first per-recipient-private resource in this codebase; no tenant-only finder permitted"
    - "Native paginated query with mandatory countQuery — first Spring Data Pageable/Page use in this backend"
    - "CAST(:param AS type) guard for optional native-query filters — reused idiom from ParecerSolicitacaoRepository.pesquisar"

key-files:
  created:
    - backend/src/main/java/com/lexcv/models/Notificacao.java
    - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
    - backend/migrations/86-create-notificacao-table.sql
  modified: []

key-decisions:
  - "None - followed 86-PATTERNS.md pattern map verbatim, no deviations"

patterns-established:
  - "Notificacao entity composes AuditLog's polymorphic entidade_tipo/entidade_id reference with Prazo's id/tenant_id/boolean-flag/createdAt shape — first entity in the codebase requiring both"
  - "Every NotificacaoRepository finder carries both tenant and destinatario params — grep for a tenant-only finder returns nothing, by design"

requirements-completed: [NOTF-14]

# Metrics
duration: 12min
completed: 2026-07-08
---

# Phase 86 Plan 1: Notificacao Entity, Repository & Migration Summary

**Notificacao JPA entity (one row per event-recipient pair) + dual-scoped (tenant+destinatario) NotificacaoRepository with a native paginated history query, plus the required manual production migration for `t_notificacao`.**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-07-08T20:46:04Z
- **Tasks:** 2/2 completed
- **Files modified:** 3 (all new)

## Accomplishments
- `Notificacao` entity persists one row per (event, recipient) with its own per-row `lida` flag — no shared row with computed visibility, matching CONTEXT.md's locked decision.
- `NotificacaoRepository` exposes exactly four dual-scoped finders and zero tenant-only finders, establishing the codebase's first per-recipient-private data-access contract.
- `backend/migrations/86-create-notificacao-table.sql` provides the required manual production DDL (the codebase's first manual migration that creates a table from scratch rather than altering an existing one), including the composite `(tenant_id, destinatario_id, lida, created_at)` index ARCHITECTURE.md mandates "from day one."

## Task Commits

Each task was committed atomically:

1. **Task 1: Notificacao entity + manual migration script** - `6e77068` (feat)
2. **Task 2: NotificacaoRepository — dual-scoped query surface** - `009c441` (feat)

_Note: No TDD tasks in this plan — both tasks are `type="auto"` with no `tdd="true"` attribute._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/Notificacao.java` - JPA entity for `t_notificacao`; `UUID` id (`GenerationType.UUID`), `tenantId`/`destinatarioId` both `NOT NULL`, polymorphic `entidadeTipo`/`entidadeId` (String, copied from `AuditLog`), `mensagem` as `TEXT`, `linkUrl` the only nullable column, `lida` defaults `false` via `@Builder.Default`, `createdAt` set in `@PrePersist`.
- `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` - `JpaRepository<Notificacao, UUID>` exposing `buscarPorFiltros` (native, paginated, `countQuery` supplied, `tenant_id`/`destinatario_id` non-optional, `categoria`/`lida` `CAST`-guarded optional filters), `countByTenantIdAndDestinatarioIdAndLidaFalse`, `findByTenantIdAndDestinatarioIdAndLidaFalse`, `findByIdAndTenantIdAndDestinatarioId`.
- `backend/migrations/86-create-notificacao-table.sql` - Manual production migration: `CREATE TABLE t_notificacao` (all 11 columns matching the entity) + `CREATE INDEX idx_notificacao_tenant_destinatario_lida_created`.

## Decisions Made
None - plan executed exactly as written, directly from `86-PATTERNS.md`'s composed entity/repository proposals and `.planning/research/ARCHITECTURE.md` Pattern 4.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both `mvn -q -DskipTests compile` runs succeeded on the first attempt; all grep-based verification checks (migration DDL/index presence, repository method signatures, absence of a tenant-only finder) passed without rework.

## User Setup Required

None - no external service configuration required. Note for deploy: `backend/migrations/86-create-notificacao-table.sql` must be run manually against staging/prod databases before/during the deploy that ships this entity (no Flyway/Liquibase in this repo; `ddl-auto: validate` in prod never creates schema). This mirrors the existing carried-forward manual-migration items already tracked in STATE.md for `74-`/`81-`/`82-`.

## Next Phase Readiness

`Notificacao` and `NotificacaoRepository` are ready for `86-02` (service layer — `NotificacaoService.criar(...)` single write choke point + ADMIN fan-out) and `86-03` (dedicated `NotificacaoController` + RBAC seeding), both of which read `86-PATTERNS.md`'s already-composed proposals for those layers. No blockers. The manual migration script is written but not yet executed against any real database — dev/CI continue to rely on `ddl-auto=update` auto-creating `t_notificacao` until this script is run against staging/prod.

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/models/Notificacao.java
- FOUND: backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
- FOUND: backend/migrations/86-create-notificacao-table.sql
- FOUND commit: 6e77068
- FOUND commit: 009c441

---
*Phase: 86-infraestrutura-de-notificacoes-entidade-api-e-targeting*
*Plan: 01*
*Completed: 2026-07-08*
