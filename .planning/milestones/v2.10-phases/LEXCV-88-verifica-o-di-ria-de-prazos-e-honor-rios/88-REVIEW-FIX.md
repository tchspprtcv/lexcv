---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
fixed_at: 2026-07-09T23:26:49Z
review_path: .planning/phases/LEXCV-88-verifica-o-di-ria-de-prazos-e-honor-rios/88-REVIEW.md
iteration: 3
findings_in_scope: 3
fixed: 2
skipped: 1
status: partial
---

# Phase LEXCV-88: Code Review Fix Report

**Fixed at:** 2026-07-09T23:26:49Z
**Source review:** .planning/phases/LEXCV-88-verifica-o-di-ria-de-prazos-e-honor-rios/88-REVIEW.md
**Iteration:** 3 (final allowed iteration)

**Summary:**
- Findings in scope: 3 (fix_scope: critical_warning — WR-01 through WR-03; IN-01/IN-02/IN-03 excluded, out of scope)
- Fixed: 2
- Skipped: 1

All fixes verified via: (1) re-read of the modified file section, (2) `mvn -o compile` (clean, no errors, after the WR-01 fix), and (3) a full run of the existing `mvn -o test -Dtest=AlertasDiariosJobTest` targeted suite (9/9 tests green after the WR-03 fix — 8 pre-existing plus the new test). Each fix was applied and committed inside an isolated git worktree (`gsd-reviewfix/88-*`), then fast-forwarded onto `master` during cleanup; no uncommitted or partial changes remain.

## Fixed Issues

### WR-01: `Notificacao`'s dedup unique constraint has no explicit name, so it will exist under two different names in dev/CI vs. production

**Files modified:** `backend/src/main/java/com/lexcv/models/Notificacao.java`
**Commit:** e5960fe
**Applied fix:** Added `name = "uk_notificacao_dedup"` to the `@UniqueConstraint` on `Notificacao`'s `@Table` annotation, matching the literal name the manual production migration (`88-add-notificacao-dedup-unique-constraint.sql`) already uses. This makes the constraint consistently referenceable by name in every environment — dev/CI (`ddl-auto=update`, which previously generated an arbitrary Hibernate-default name) and production (`ddl-auto=validate` + manual migration) now agree — closing the false-negative gap the review identified for any future `to_regclass('uk_notificacao_dedup')`-style startup check. The review's suggestion to apply the same fix to `Honorario.java`'s `uk_honorario_processo` (Phase 82 precedent) was intentionally **not** applied here — the finding itself flags that file as "outside this phase's change set."

### WR-03: No test proves the per-admin `catch (Exception e)` fan-out isolation (iteration-2 fix) actually stops one admin's failure from blocking the rest

**Files modified:** `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java`
**Commit:** 8dc30c6
**Applied fix:** Added `executar_umAdminFalhaAoNotificar_restantesAdminsAindaSaoNotificados`, adapted from the review's suggested test to the file's actual current helpers/constants (`semEventos`, `semHonorarios`, `nuncaAntesNotificado`, `buildJob()`, `TENANT_ID`/`PROCESSO_ID`/`RESPONSAVEL_ID`/`HOJE` — all confirmed identical to the review's context). Configures two admins where the first's `notificacaoService.criar(...)` throws a `RuntimeException`, then asserts the job doesn't throw and the second admin still receives its `PRAZO_PROXIMO` notification. Required adding the `org.mockito.Mockito.doThrow` static import. Full targeted suite run: 9/9 green (8 pre-existing + this new test), confirming the isolation added by WR-01 (iteration 2) is now under regression coverage.

## Skipped Issues

### WR-02: Manual dedup-index migration still has no automated startup verification, and has no guard against pre-existing duplicate rows

**File:** `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql:1-25`, `backend/src/main/resources/application-prod.yml:10`
**Reason:** The finding's own **Fix:** text states this is "Still out of scope for a single-file patch in this phase (same reasoning as the iteration-2 disposition)." This is the third consecutive review pass raising the same underlying migration-verification gap (originally `WR-05` in iteration 2, explicitly skipped there for cross-cutting/architectural reasons documented in `88-REVIEW-FIX.iter2.md` — a prod-profile-gated `CommandLineRunner` or a deployment-runbook checklist, neither of which fits as a narrow single-phase change). Nothing about that scoping analysis has changed. The "one additional angle" this iteration adds — the script has no pre-flight duplicate-row check before `CREATE UNIQUE INDEX` — is itself presented as "if picked up as a follow-up," not as a fix to apply now, consistent with the same out-of-scope framing. Maintaining the established disposition rather than partially patching only the newly-raised angle in isolation from the broader (still out-of-scope) issue it's attached to.
**Original issue:** `ddl-auto=validate` never creates schema in prod, so a skipped manual migration fails silently at startup, silently reverting the WR-01 (iteration 1) notification-idempotency guarantee back to a check-then-act race with no error/warning surfaced anywhere; additionally, the migration script's bare `CREATE UNIQUE INDEX` has no guard against pre-existing duplicate rows, so it would fail outright if any exist at the moment a DBA runs it against a target database.

## Remaining Open Items (iteration limit reached)

This was the final allowed auto-fix iteration (3 of 3). The auto-fix loop stops here regardless of remaining findings. Items still open after this pass:

- **WR-02** (skipped above) — cross-cutting migration-verification gap, carried forward unchanged since iteration 2 (as `WR-05`). Recommend tracking as its own follow-up covering all manual migration scripts under `backend/migrations/` together (this one plus the pre-existing Phase-82 equivalent), rather than fixing piecemeal inside a phase.
- **IN-01** (`AlertasDiariosJob.java:42-46` Javadoc claims "3 camadas" of failure isolation; code has had 4 since iteration 1) — out of scope for `fix_scope: critical_warning`, never attempted.
- **IN-02** (no test for the "responsável-also-admin" natural-dedup behavior documented in `notificar()`'s own comment) — out of scope for `fix_scope: critical_warning`, never attempted.
- **IN-03** (`@MockitoSettings(strictness = Strictness.LENIENT)` still suppresses Mockito's unused-stub safety net; RED-phase justification no longer applies) — out of scope for `fix_scope: critical_warning`, never attempted; flagged as `IN-01` in both prior reviews and excluded from both prior fix passes too.

All three Info items are low-risk, narrowly-scoped, and mechanical-looking (a doc comment fix, a new test, and a one-line annotation removal). If another pass is desired, re-running with `fix_scope: all` would bring them into scope; otherwise they should be picked up as manual follow-ups.

---

_Fixed: 2026-07-09T23:26:49Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
