---
phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
plan: 01
subsystem: database
tags: [jpa, hibernate, postgres, notifications, tenant-isolation]

# Dependency graph
requires: []
provides:
  - "NotificacaoPreferencia join-table entity (tenant_id + user_id + categoria, row presence = muted)"
  - "CategoriaNotificacao enum: 9 canonical categories, PRAZO_VENCIDO marked non-silenciable, non-throwing fromString/isSilenciavelCategoria contract"
  - "NotificacaoPreferenciaRepository: 3 dual-scoped derived methods (exists/find/delete)"
  - "Manual production migration script for t_notificacao_preferencia"
affects: [93-02, 93-03]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Join-table tenant+user dual-scoping (ClienteAdvogado/ClienteAdministrativo convention) applied to notification preferences"
    - "Presence-of-row-as-boolean-flag pattern (no explicit 'ativo' column)"

key-files:
  created:
    - backend/src/main/java/com/lexcv/models/NotificacaoPreferencia.java
    - backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java
    - backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java
    - backend/migrations/93-create-notificacao-preferencia-table.sql
  modified: []

key-decisions:
  - "NotificacaoPreferencia stores no boolean flag — row existence IS the mute preference (locked in 93-CONTEXT.md)"
  - "CategoriaNotificacao enum is validation-only; the 9 existing hardcoded String call sites in Notificacao/NotificacaoService are NOT retrofitted"
  - "fromString/isSilenciavelCategoria never throw — return Optional.empty()/false for unknown values, per downstream (93-02/93-03) contract requirement"

patterns-established:
  - "Dual-scoped (tenant_id + user_id) derived repository methods as the mandatory shape for any per-user preference resource, to prevent cross-tenant/cross-user leaks (Pitfall 10)"

requirements-completed: [NOTF-24]

# Metrics
duration: ~15min
completed: 2026-07-14
---

# Phase 93 Plan 01: NOTF-24 Data Foundation Summary

**JPA join-table `NotificacaoPreferencia` (tenant_id+user_id+categoria) with dual-scoped repository and a canonical `CategoriaNotificacao` enum marking `PRAZO_VENCIDO` as the sole non-silenciable category**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-14T10:29:28Z
- **Tasks:** 2 completed
- **Files modified:** 4 (all new)

## Accomplishments
- Created the `NotificacaoPreferencia` entity mirroring the `ClienteAdvogado` tenant-scoped join-table convention, with a unique constraint on `(tenant_id, user_id, categoria)` and the "row presence = muted" semantic locked by 93-CONTEXT.md
- Created `CategoriaNotificacao`, the single source of truth in the backend for the 9 notification categories and their silenciability, with a non-throwing `fromString`/`isSilenciavelCategoria` contract that Plan 93-02 (mute guard) and Plan 93-03 (endpoints) can depend on without further exploration
- Created `NotificacaoPreferenciaRepository` with exactly the 3 dual-scoped derived methods needed downstream (`existsByTenantIdAndUserIdAndCategoria`, `findByTenantIdAndUserId`, `deleteByTenantIdAndUserIdAndCategoria`), with no `@Modifying`, matching the codebase's zero-`@Modifying` convention
- Created the required manual production migration `93-create-notificacao-preferencia-table.sql` (table + unique index), following the exact header convention of `86-create-notificacao-table.sql`

## Task Commits

Each task was committed atomically:

1. **Task 1: Entidade NotificacaoPreferencia + enum CategoriaNotificacao** - `594b89a` (feat)
2. **Task 2: NotificacaoPreferenciaRepository + migração manual** - `656e77f` (feat)

_Note: no plan-metadata commit is included here — the orchestrator commits STATE.md/ROADMAP.md separately after this SUMMARY lands._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/NotificacaoPreferencia.java` - Tenant+user-scoped join-table entity; row presence = category muted
- `backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java` - 9-category enum with silenciability flag and non-throwing fromString/isSilenciavelCategoria helpers
- `backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java` - Dual-scoped (tenant_id+user_id) derived repository: exists/find/delete
- `backend/migrations/93-create-notificacao-preferencia-table.sql` - Required manual production migration (table + unique index); pending manual execution against prod DB (joins already-pending 74/86/88 scripts noted in STATE.md)

## Decisions Made
None beyond what was already locked in 93-CONTEXT.md and the plan itself — executed as specified:
- No boolean "ativo" column on `NotificacaoPreferencia`; row existence is the preference
- `CategoriaNotificacao` is validation-only, not a retrofit of existing `String categoria` fields elsewhere
- Confirmed by inspection (not modified) that `notificacoes:view` scope already exists in `DatabaseSeeder.java`, `UserPrincipal.java`, `NotificacaoController.java`, and `AdminController.java` — no new scope/permission was created, per plan instruction

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. Note: `backend/migrations/93-create-notificacao-preferencia-table.sql` must be run manually against staging/production databases before/during the deploy that ships this entity (ddl-auto=validate in prod never auto-creates schema) — this joins the already-pending 74/86/88 migration scripts tracked in STATE.md.

## Next Phase Readiness
- Plan 93-02 (mute guard in `NotificacaoService.criar`) can now depend on `NotificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria` and `CategoriaNotificacao.isSilenciavelCategoria` as stable, compiled contracts
- Plan 93-03 (preferences endpoints) can depend on `findByTenantIdAndUserId`, `deleteByTenantIdAndUserIdAndCategoria`, and the same `isSilenciavelCategoria` validation helper
- No blockers. `cd backend && mvn -q -DskipTests compile` succeeds with all new files.

---
*Phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador*
*Completed: 2026-07-14*

## Self-Check: PASSED

All created files confirmed on disk; both task commits (`594b89a`, `656e77f`) confirmed in git log.
