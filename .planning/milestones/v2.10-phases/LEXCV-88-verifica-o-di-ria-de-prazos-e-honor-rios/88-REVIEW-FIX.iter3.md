---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
fixed_at: 2026-07-09T22:42:00Z
review_path: .planning/phases/LEXCV-88-verifica-o-di-ria-de-prazos-e-honor-rios/88-REVIEW.md
iteration: 2
findings_in_scope: 5
fixed: 4
skipped: 1
status: partial
---

# Phase LEXCV-88: Code Review Fix Report

**Fixed at:** 2026-07-09T22:42:00Z
**Source review:** .planning/phases/LEXCV-88-verifica-o-di-ria-de-prazos-e-honor-rios/88-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 5 (fix_scope: critical_warning — WR-01 through WR-05; IN-01 excluded, out of scope)
- Fixed: 4
- Skipped: 1

All fixes verified via: (1) re-read of the modified file section, (2) `mvn -o compile` (clean after every single fix, no errors), and (3) a full run of the existing `mvn -o test -Dtest=AlertasDiariosJobTest` targeted suite (8/8 tests green after every fix — 7 pre-existing plus the new WR-04 test — including after WR-02 and WR-03, which touch exception-handling control flow).

## Fixed Issues

### WR-01: Admin fan-out loops have no per-recipient failure isolation, unlike the established sibling pattern

**Files modified:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`
**Commit:** 3f84607
**Applied fix:** Wrapped each of the three admin fan-out loops (in `processarPrazos`, `processarEventos`, `processarHonorarios`) in its own try/catch around the per-admin `notificar(...)` call, mirroring `NotificacaoService.notificarAdmins`'s established per-recipient isolation pattern exactly as the review suggested. A transient failure (e.g. a `DataAccessException`) while notifying one admin can no longer abort the remaining admins in that fan-out for the same entidade — it is now logged (`log.warn`) and the loop continues.

### WR-02: No-arg `executar()` computes "hoje" with zone-naive `LocalDate.now()`, contradicting the class's own explicit-zone design principle

**Files modified:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`
**Commit:** 0600d3b
**Applied fix:** Added a `private static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde")` constant (plus the `java.time.ZoneId` import) and changed the no-arg `executar()` to call `executar(LocalDate.now(FUSO_CABO_VERDE))` instead of the zone-naive `LocalDate.now()`, exactly as the review suggested — applying the same "never trust the container's default zone" rigor already used for the `@Scheduled` annotation's own `zone=` attribute to this line as well.

### WR-03: Category-level and per-entidade catch blocks still only handle `Exception` — a narrower version of the exact gap the prior review's WR-04 closed at the two outer layers

**Files modified:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`
**Commit:** fecf2c9
**Applied fix:** Upgraded all six remaining `catch (Exception e)` blocks the review identified — the three category-level catches in `processarTenant` (wrapping the `processarPrazos`/`processarEventos`/`processarHonorarios` calls) and the three per-entidade catches inside those same three methods — to `catch (Throwable e)`, for consistency with the `Throwable` upgrade already applied to the two outer layers in the prior iteration (WR-04, iteration 1). `safeProcessoPorId`/`safeAdmins`'s own catches, and the three new WR-01 per-admin catches, intentionally remain `catch (Exception e)` — both explicitly outside this finding's stated scope (confirmed by re-reading the Issue text, which names only the category-level and per-entidade layers).

### WR-04: No test proves the outer `catch (Throwable e)` layers actually work — the test that used to inject a tenant-level failure is now absorbed by an inner catch before ever reaching them

**Files modified:** `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java`
**Commit:** e79415c
**Applied fix:** Added `executar_umTenantLancaError_naoEscapaDoJobEOutroTenantAindaEhProcessado`, mirroring the existing `executar_umTenantLancaExcecao_outroTenantAindaEhProcessadoENenhumaExcecaoEscapa` test's tenant A / tenant B setup, but stubbing `processoRepository.findByTenantId(TENANT_ID)` to throw `new StackOverflowError()` (a genuine non-`Exception` `Throwable`) instead of a `RuntimeException`. Running the full targeted suite (8/8 green, including this new test) confirms it exercises the intended path end-to-end: the `StackOverflowError` is NOT absorbed by `safeProcessoPorId`'s `catch (Exception e)` (an `Error` is not a subtype of `Exception`), propagates out of `processarTenant` uncaught, and is only finally intercepted by the per-tenant `catch (Throwable e)` layer in `executar(LocalDate)` — while tenant B is still processed normally and `assertDoesNotThrow` holds for the job as a whole. This gives the prior review's `Throwable` upgrade (WR-04, iteration 1) its first real test coverage.

## Skipped Issues

### WR-05: The new dedup unique constraint's manual production migration has no automated verification at startup

**File:** `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql:1-25`, `backend/src/main/resources/application-prod.yml:10`
**Reason:** The review's own **Fix:** section frames this explicitly as *"Out of scope for a pure code change in this phase, but worth tracking as a follow-up"* and offers two non-mandatory alternatives rather than a ready-to-apply patch: (a) a new prod-profile-gated startup self-check (e.g. a `CommandLineRunner` querying `to_regclass(...)` for both `uk_notificacao_dedup` and the pre-existing `uk_honorario_processo`), or (b) adding both constraints to a shared deployment checklist/runbook. Both were evaluated before skipping: (a) has no natural, low-risk home among this phase's reviewed files — `SchedulingConfig.java` (the only reviewed `@Configuration` class) states its own "sole responsibility" is enabling `@Scheduled` infrastructure in its header comment, and introducing a new prod-only schema-validation runner is a cross-cutting architectural decision (fail-fast vs. warn-only semantics; whether it should cover just these two constraints or become a reusable pattern for future migrations) exceeding the scope of a single warning-tier fix. (b) `DEPLOYMENT.md` exists at the repo root but currently documents zero of the five manual SQL files under `backend/migrations/` (74/81/82/86/88) — adding only `88-add-notificacao-dedup-unique-constraint.sql` (and retroactively `82-add-honorario-processo-unique-constraint.sql`) to it in isolation would produce a misleadingly partial checklist, itself a scoping decision the review did not make. The review's own text confirms this is *"not a new architectural problem introduced by this phase"* — the identical gap already exists, and was already accepted, for migration 82 — i.e. a pre-existing, repo-wide gap rather than something phase 88 introduced. Recommend tracking as its own cross-cutting follow-up covering all manual migrations together, rather than fixing piecemeal within this phase.
**Original issue:** Hibernate's `ddl-auto=validate` in prod does not reliably validate the presence of unique constraints/indexes, so a skipped manual migration during a production deploy would very likely start up successfully with no error, warning, or log line indicating the constraint is missing — silently reverting the WR-01 (iteration 1) notification-idempotency guarantee back to the original check-then-act race, with the deploy pipeline never surfacing it.

---

_Fixed: 2026-07-09T22:42:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
