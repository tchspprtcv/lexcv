---
phase: LEXCV-97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente
plan: 01
subsystem: api
tags: [tenant-isolation, security-audit, jpa, spring-data, notificacoes]

# Dependency graph
requires:
  - phase: 93-notf-24-preferencias
    provides: NotificacaoPreferencia entity + repository (mute preferences)
  - phase: 95-notf-25-equipa
    provides: resolverEquipaCliente team fan-out
  - phase: 96-notf-26-snooze
    provides: snoozedUntil visibility + mutation queries
provides:
  - Confirmed tenant-isolation verdict (COVERED, zero fixes needed) for the three newest notification data surfaces of milestone v2.11
affects: [97-04-milestone-closeout]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified: []

key-decisions:
  - "No code changes made — every query traced in this audit already carries the required tenant_id (+ user_id / destinatario_id) predicate; verdict is COVERED, not FIXED, for all in-scope surfaces."
  - "Live cross-tenant probe (creating a second seeded tenant/user) was skipped per 97-CONTEXT.md's explicit discretion clause — the source-level audit read every query end-to-end (repository method signature, JPQL/native SQL text, and every call site's tenant-id provenance) and gives sufficient confidence without the added risk/cost of provisioning throwaway cross-tenant test data this late in the milestone."

patterns-established: []

requirements-completed: [AUD-01]

# Metrics
duration: ~15min
completed: 2026-07-14
---

# Phase 97 Plan 01: Tenant-Isolation Audit of Notification Surfaces Summary

**Cross-cutting tenant-isolation audit of Phase 93/95/96 notification surfaces (preferences, team fan-out, snooze) — zero gaps found, zero fixes required, verdict COVERED across all four in-scope query families.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-07-14T19:44:00Z
- **Completed:** 2026-07-14T19:59:39Z
- **Tasks:** 2/2 completed (both audit-only, no fixes needed)
- **Files modified:** 0 (audit found no tenant-scoping gaps)

## Accomplishments

- Traced every read/write to `t_notificacao_preferencia` (Phase 93 mute preferences) and confirmed all four repository methods are dual-scoped by `tenant_id` AND `user_id`
- Traced `resolverEquipaCliente` (Phase 95 team resolution) and confirmed it resolves exclusively via the tenant-scoped `findByClienteIdAndTenantId` on both junction repositories, with no `findByClienteId`-only fallback existing anywhere in the codebase
- Traced all four `ResourceController`/`ParecerController` call sites that trigger notifications and confirmed every one sources `tenantId` from `getTenantId()` (security context / JWT) or from an already-tenant-validated entity, never from client-supplied request data
- Traced the Phase 96 snooze surface (`NotificacaoRepository`'s two `snoozedUntil`-aware queries and `NotificacaoService.snooze`) and confirmed both queries filter `tenantId` AND `destinatarioId`, and that `snooze()` fetches via `findByIdAndTenantIdAndDestinatarioId` before mutating
- Recorded a documented, reasoned decision to skip the optional live cross-tenant probe

## Task Commits

No source-code commits were made for this plan — the audit found no tenant-scoping gaps to fix, so both tasks are verification-only. The only commit produced by this plan is the SUMMARY documentation commit (see below), which the executor script commits directly since there is no earlier per-task commit slot for a pure confirmation pass.

_Note: Per `task_commit_protocol`, a task commit is only made when files are modified. Both Task 1 and Task 2 concluded with a COVERED verdict (no code change), so there is nothing to stage for either task individually._

## Per-Surface Tenant-Scoping Verdict (AUD-01)

### Task 1 — Phase 93 preference surface (`NotificacaoPreferencia`)

| Query / Guard | Scope confirmed | Verdict |
|---|---|---|
| `existsByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria)` | Derived query name carries both `TenantId` and `UserId` | COVERED |
| `findByTenantIdAndUserId(tenantId, userId)` | Derived query name carries both `TenantId` and `UserId` | COVERED |
| `deleteByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria)` | Derived query name carries both `TenantId` and `UserId` | COVERED |
| `upsertSilenciar(...)` native `INSERT ... ON CONFLICT (tenant_id, user_id, categoria) DO NOTHING` | ON CONFLICT target is the composite `(tenant_id, user_id, categoria)` unique key, matching the entity's `uk_notificacao_preferencia` constraint | COVERED |
| `NotificacaoService.criar()` mute guard (`CategoriaNotificacao.isSilenciavelCategoria(categoria) && existsByTenantIdAndUserIdAndCategoria(tenantId, destinatarioId, categoria)`) | Keys off the destinatário's own `tenantId`/`destinatarioId` pair passed into `criar()`; `PRAZO_VENCIDO` short-circuits before the preference check even runs | COVERED |
| `NotificacaoController` preferences endpoints (`GET/PUT/DELETE /notificacoes/preferencias*`) | All four endpoints call `getTenantId()`/`getUserId()`, both sourced from `UserPrincipal` via `SecurityContextHolder` — never from `@RequestParam`/`@RequestBody` | COVERED |

Zero methods scope by `user_id` alone. No fix required.

### Task 2 — Phase 95 team resolution + Phase 96 snooze

| Surface | Scope confirmed | Verdict |
|---|---|---|
| `NotificacaoService.resolverEquipaCliente(tenantId, clienteId)` | Calls only `clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, tenantId)` and `clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, tenantId)` — confirmed by reading both repository interfaces: neither exposes a `findByClienteId`-only variant, so no unscoped fallback exists anywhere to accidentally call | COVERED |
| 4 notification call sites (`ResourceController.createProcesso`, `.atribuirResponsavel`, `.createProcessoFase`, `.uploadDocumento` x2; `ParecerController.createSolicitacao`/`.atribuirAdvogado`) | All source `tenantId` from `getTenantId()` (security context) or from an entity (`processo.getTenantId()`) whose tenant was already verified equal to `getTenantId()` earlier in the same method before use | COVERED |
| `NotificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse` (unread-count badge) | JPQL: `WHERE n.tenantId = :tenantId AND n.destinatarioId = :destinatarioId AND n.lida = false AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :agora)` | COVERED |
| `NotificacaoRepository.findByTenantIdAndDestinatarioIdAndLidaFalse` (mark-all-read load) | Same tenant+destinatario+snooze predicate as above | COVERED |
| `NotificacaoService.snooze(tenantId, destinatarioId, id, dias)` | Fetches exclusively via `findByIdAndTenantIdAndDestinatarioId(id, tenantId, destinatarioId)` before any mutation; returns `Optional.empty()` (never touches the row) when it belongs to a different tenant/recipient | COVERED |

Zero gaps found. No fix required.

**Live cross-tenant probe:** Skipped. Per `97-CONTEXT.md`'s explicit discretion clause for AUD-01 ("avaliar se compensa criar um durante a fase, ou se a auditoria de código já dá confiança suficiente"), this audit read every one of the above queries at the source level — repository method signature or literal JPQL/native SQL text, plus every call site that supplies `tenantId` — rather than sampling behavior through a live HTTP round trip. Every single query in scope was confirmed to carry the required tenant predicate; none required interpretation of ambiguous runtime behavior that only a live probe could resolve. Provisioning a second seeded tenant/user this late in the milestone (after the NOTF-25 test data already created in this same session per `97-CONTEXT.md`) was judged unnecessary added risk/cost for a verification method (live curl) that would only re-confirm what static reading of the exact same code paths already proves deterministically.

## Files Created/Modified

None. This plan was audit-only; the code it inspected was already correctly tenant-scoped from Phases 93/95/96's own code reviews.

## Decisions Made

- No fixes applied — every one of the four Task 1 preference queries and every one of the Task 2 team-resolution/snooze queries was already dual-scoped correctly; nothing needed correction.
- Live cross-tenant HTTP probe skipped in favor of exhaustive source-level tracing (see verdict table above for rationale).

## Deviations from Plan

None — plan executed exactly as written. No Rule 1/2/3 auto-fixes were triggered because no bug, missing critical functionality, or blocking issue was found; the audit's own explicit "if found, fix it" branches were never entered.

## Issues Encountered

None. One verify-command false negative was observed (documented for transparency, not an actual gap): Task 2's second automated verify command (`grep -c "n.tenantId = :tenantId AND n.destinatarioId = :destinatarioId"`) returned 0 because `NotificacaoRepository.java`'s JPQL string literal is split across two Java string-concatenation lines (`"...n.tenantId = :tenantId " + "AND n.destinatarioId = :destinatarioId..."`), so the exact substring never appears on one physical line. Manual reading of both queries (lines 48-53 and 60-65) confirms the compiled JPQL predicate is present and correct in both.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

AUD-01 is fully satisfied and closed: all three new notification data surfaces from this milestone (Phase 93 preferences, Phase 95 team resolution, Phase 96 snooze) are confirmed tenant-scoped with no exceptions. This verdict is ready for Plan 97-04 (milestone closeout) to read and cite directly — no follow-up work required from this plan.

---
*Phase: LEXCV-97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente*
*Completed: 2026-07-14*
