---
phase: LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)
reviewed: 2026-07-13T19:00:00Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - backend/pom.xml
  - backend/src/test/resources/application.properties
  - backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java
  - backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java
  - backend/migrations/91-add-parecer-versao-unique-constraint.sql
  - .github/workflows/deploy.yml
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
status: issues_found
---

# Phase LEXCV-91: Code Review Report

**Reviewed:** 2026-07-13T19:00:00Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

This is a re-review of the Testcontainers integration-test infrastructure after the prior
review's five WARNING findings (WR-01 through WR-05) were addressed by the fix workflow
(commits `5f4bc15`, `7b4a840`, `3e793ac`, `ec37b4e`, `c8a2a9a`). Each fix was independently
re-verified against the current file contents rather than taken on faith:

- **WR-01 (flaky sleep-based ordering assertion)** — confirmed fixed.
  `NotificacaoRepositoryIT.buscarPorFiltros_paginacao_...` no longer calls `Thread.sleep`.
  It now flushes the three pending inserts, forces strictly increasing `created_at` values via
  a native `UPDATE ... WHERE id = :id` per row, then calls `entityManager.clear()` before the
  native `buscarPorFiltros` query. The ordering is now fully deterministic; traced the
  Hibernate auto-flush/no-setter/`updatable=false` constraints and the fix correctly places
  `entityManager.flush()` *before* the native `UPDATE`s (required — the native `UPDATE`s
  target rows by `id`, and without the prior flush the still-pending `INSERT`s wouldn't exist
  in Postgres yet, so the `UPDATE`s would silently affect zero rows).
- **WR-02 (test datasource can silently mask a missing `@ServiceConnection`)** — confirmed
  fixed. `application.properties` now points at the non-resolvable host
  `this-property-must-be-overridden-by-ServiceConnection`, with a comment explaining the
  fail-fast intent.
- **WR-03 (`ExecutorService.shutdown()` doesn't reclaim leaked threads on timeout)** —
  confirmed fixed. `ParecerVersaoConcorrenciaIT`'s `finally` block now calls
  `executor.shutdownNow()` followed by `executor.awaitTermination(5, TimeUnit.SECONDS)`.
- **WR-04 (missing production migration for the `ParecerVersao` unique constraint)** —
  confirmed fixed: `backend/migrations/91-add-parecer-versao-unique-constraint.sql` was added,
  following the established `81`/`82`/`88` precedent (`ALTER TABLE ... ADD CONSTRAINT ...
  UNIQUE (...)`). However, re-verifying this fix against `ParecerVersao.java` surfaced a new,
  narrower issue with the script's own safety guidance — see WR-01 below (renumbered for this
  review).
- **WR-05 (CI "gating" job only ran post-merge, not on PRs)** — confirmed fixed.
  `deploy.yml` now triggers on both `push` and `pull_request` to `master`; `build-and-push`
  gained `if: github.event_name == 'push'` so image builds/pushes (and the `packages: write`
  permission they need) are never exercised on pull-request events, i.e. fork PRs cannot
  trigger a package push. No permissions regression was introduced by this change.

The concurrency mechanics were re-traced end-to-end against production code: `findByIdForUpdate`
really does issue `SELECT ... FOR UPDATE` and blocks the second transaction under Postgres's
default READ COMMITTED isolation until the first commits, at which point
`findMaxNumeroVersaoBySolicitacaoId` correctly observes the first transaction's insert — the
`{1, 2}` (never `{1, 1}`) assertion is sound.

One new WARNING was found while re-verifying WR-04: the migration script's own "check before
running to avoid a duplicate-constraint error" guidance is unreliable because the entity's
`@UniqueConstraint` has no explicit `name`. The two INFO items from the previous review
(`IN-01`, `IN-02`) were intentionally out of the prior fix's scope (`fix_scope=critical_warning`)
and remain unresolved in the current code — carried forward here.

## Warnings

### WR-01: Migration script's duplicate-constraint safety check is unreliable because `ParecerVersao`'s `@UniqueConstraint` has no explicit name

**File:** `backend/migrations/91-add-parecer-versao-unique-constraint.sql:23-28,36` (root cause: `backend/src/main/java/com/lexcv/models/ParecerVersao.java:10-11`)
**Issue:** The migration script instructs the operator to verify "whether the constraint already
exists there (e.g. `\d t_parecer_versao` or a query against `information_schema.table_constraints`)
... to avoid a duplicate-constraint error" before running
`ALTER TABLE t_parecer_versao ADD CONSTRAINT uk_parecer_versao_solicitacao_numero UNIQUE
(solicitacao_id, numero_versao);`. But `ParecerVersao.java`'s `@Table` annotation declares
`uniqueConstraints = @UniqueConstraint(columnNames = {"solicitacao_id", "numero_versao"})`
**without a `name` attribute** — unlike `Notificacao.java`'s equivalent
`@UniqueConstraint(name = "uk_notificacao_dedup", columnNames = {...})`, whose migration
(`88-add-notificacao-dedup-unique-constraint.sql`) can be safely name-matched against. Any
environment bootstrapped locally/in CI via `ddl-auto=update` gets this constraint under a
Hibernate/Postgres **auto-generated** name (not `uk_parecer_versao_solicitacao_numero`).
Consequently: (1) Postgres allows multiple unique constraints on the same column set with
different names, so running this script against such an environment will **not** raise a
duplicate-constraint error as the script's guidance implies — it will silently add a second,
functionally-redundant unique constraint instead; and (2) an operator who literally checks for
the name `uk_parecer_versao_solicitacao_numero` (rather than querying by column set) in a
target environment could be misled into believing the constraint isn't present at all when an
equivalent one (under a different name) already is.
**Fix:** Add the missing explicit constraint name to the entity so it matches the migration
script, closing the gap for any future `ddl-auto=update` environment:
```java
@Table(name = "t_parecer_versao",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_parecer_versao_solicitacao_numero",
                columnNames = {"solicitacao_id", "numero_versao"}))
```
and update the migration script's verification guidance to check by column set rather than by
literal name, e.g.:
```sql
SELECT conname FROM pg_constraint
WHERE conrelid = 't_parecer_versao'::regclass AND contype = 'u';
```

## Info

### IN-01: Testcontainers container/annotation boilerplate still duplicated across both `*IT` classes

**File:** `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java:39-46`,
`backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java:55-62`
**Issue:** Carried forward from the previous review (explicitly out of the prior fix's
`critical_warning` scope, so unresolved by design). Both classes still repeat the identical
`@DataJpaTest` / `@AutoConfigureTestDatabase(Replace.NONE)` / `@Testcontainers` class
annotations and the identical `@Container @ServiceConnection static PostgreSQLContainer<?>
postgres = new PostgreSQLContainer<>("postgres:16-alpine");` field. Tolerable at two classes,
but the required-but-easy-to-forget annotation combination (the exact failure mode WR-02 guards
against) will keep being copy-pasted as more `*IT` classes are added.
**Fix:** Extract a shared abstract base class (e.g. `AbstractPostgresIT`) carrying the
annotations and the static container field; have new `*IT` classes extend it.

### IN-02: CI jobs still have no `timeout-minutes`

**File:** `.github/workflows/deploy.yml:14-15` (`test` job), `:43-50` (`build-and-push` job)
**Issue:** Carried forward from the previous review (explicitly out of the prior fix's
`critical_warning` scope, so unresolved by design). Neither job declares `timeout-minutes`, so
a hang (Testcontainers/Docker-daemon issue, or any deadlock that somehow bypasses the
concurrency test's own 20s `Future.get` timeouts) would run until GitHub's default 360-minute
job timeout, burning CI minutes. This is now slightly more relevant since the `test` job runs
on every pull request in addition to every push, doubling its exposure.
**Fix:** Add a conservative bound, e.g. `timeout-minutes: 20` on `test` and
`timeout-minutes: 30` on `build-and-push`.

---

_Reviewed: 2026-07-13T19:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
