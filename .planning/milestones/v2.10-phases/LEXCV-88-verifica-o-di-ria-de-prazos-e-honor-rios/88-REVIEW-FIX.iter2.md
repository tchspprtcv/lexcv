---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
fixed_at: 2026-07-09T21:41:45Z
review_path: .planning/phases/LEXCV-88-verifica-o-di-ria-de-prazos-e-honor-rios/88-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase LEXCV-88: Code Review Fix Report

**Fixed at:** 2026-07-09T21:41:45Z
**Source review:** .planning/phases/LEXCV-88-verifica-o-di-ria-de-prazos-e-honor-rios/88-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (fix_scope: critical_warning — WR-01 through WR-05; IN-01 excluded, out of scope)
- Fixed: 5
- Skipped: 0

All fixes verified via: (1) re-read of the modified file section, (2) `mvn -o compile` (clean after every single fix, no errors), and (3) a full run of the existing `mvn -o test -Dtest=AlertasDiariosJobTest` targeted suite (all 7 tests green after every fix, including after WR-02 and WR-04 which touch exception-handling control flow).

## Fixed Issues

### WR-01: Notification idempotency has no database-level backstop

**Files modified:** `backend/src/main/java/com/lexcv/models/Notificacao.java`, `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql` (new file), `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`
**Commit:** 788d053
**Applied fix:** Added a DB-level unique-index backstop mirroring migration 82's own precedent for `t_honorario`: `uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "destinatario_id", "entidade_tipo", "entidade_id", "categoria"})` on the `Notificacao` JPA entity (so dev/CI `ddl-auto=update` databases pick it up automatically), plus a new manual production migration `88-add-notificacao-dedup-unique-constraint.sql` (`CREATE UNIQUE INDEX uk_notificacao_dedup ON t_notificacao (...)`, required because `ddl-auto=validate` in prod never creates schema). `notificar(...)` now also catches `org.springframework.dao.DataIntegrityViolationException` alongside the existing `IllegalArgumentException`, so a future concurrent duplicate-insert attempt fails closed with a warning log instead of throwing out of the per-entity try/catch.

### WR-02: One failed preload query silently skips ALL alert categories for a tenant, not just the failing one

**Files modified:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`
**Commit:** 15aa0ce
**Applied fix:** Restructured `processarTenant` per the review's suggested pattern: extracted `safeProcessoPorId(tenantId)` and `safeAdmins(tenantId)` helpers that each catch their own preload failure, log it, and fall back to an empty `Map`/`List` respectively; each of `processarPrazos`/`processarEventos`/`processarHonorarios` now runs inside its own independent try/catch. A failure loading processos, loading admins, or inside any single category can no longer suppress the other two categories for that tenant. Confirmed against the existing `executar_umTenantLancaExcecao_outroTenantAindaEhProcessadoENenhumaExcecaoEscapa` test (still green) which exercises exactly this failure path.

### WR-03: Honorario batch query is the one tenant-scoped read that doesn't take `tenantId` as an explicit parameter

**Files modified:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`
**Commit:** 3d12452
**Applied fix:** Applied the review's "at minimum" option. The review's "better" alternative (add a `tenant_id` column to `t_honorario`, change the repository method to `findByTenantIdAndProcessoIdIn`) is a schema change with much wider blast radius (new migration, entity change, updates to every other `Honorario` call site) and was judged out of scope for an atomic warning-tier fix in this pass. Instead, added an explicit comment at the `honorarioRepository.findByProcessoIdIn(processoPorId.keySet())` call site pinning the invariant this data path depends on: that `processoPorId.keySet()` must never be widened or replaced by a source that could include another tenant's processo ids.

### WR-04: Top-level/per-tenant catches only handle `Exception`, not `Throwable`

**Files modified:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`
**Commit:** 828fd78
**Applied fix:** Changed both the top-level and per-tenant `catch (Exception e)` blocks in `executar(LocalDate)` to `catch (Throwable e)`, exactly as suggested, so a JVM-fatal `Error` (e.g. `StackOverflowError`) can no longer bypass both isolation layers and silently cancel all future scheduled runs — the precise failure mode the 3-layer design exists to prevent.

### WR-05: `Evento.titulo` can be null and is concatenated unguarded into user-facing notification text

**Files modified:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`
**Commit:** 3e197f6
**Applied fix:** Mirrored the existing `numeroProcesso(...)` null-guard pattern exactly as suggested: introduced `String tituloTexto = evento.getTitulo() != null ? evento.getTitulo() : "(sem título)";` and used it when building `mensagem`, so a null `Evento.titulo` can no longer produce a literal `"null"` string permanently persisted in user-facing notification text.

---

_Fixed: 2026-07-09T21:41:45Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
