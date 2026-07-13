---
phase: LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)
fixed_at: 2026-07-13T17:00:00Z
review_path: .planning/phases/LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)/91-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase LEXCV-91: Code Review Fix Report

**Fixed at:** 2026-07-13T17:00:00Z
**Source review:** .planning/phases/LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)/91-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (WR-01 through WR-05; fix_scope=critical_warning excludes IN-01/IN-02)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### WR-01: Sleep-based ordering assertion is a flaky-test pattern

**Files modified:** `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java`
**Commit:** 5f4bc15
**Applied fix:** Removed the two `Thread.sleep(5)` calls between inserts. Since `Notificacao.createdAt`
has no setter (only `@PrePersist` sets it, and the column is `updatable = false`), the review's
literal "add a setter" suggestion did not apply cleanly to the actual entity. Adapted the fix to the
"persist then immediately `UPDATE ... SET created_at = ?`" alternative the finding also offered: after
persisting the three rows, an injected `EntityManager` flushes the pending inserts, then issues a raw
native `UPDATE t_notificacao SET created_at = :createdAt WHERE id = :id` per row with strictly
increasing, hand-picked timestamps, then calls `entityManager.clear()` so the subsequent
`buscarPorFiltros` native query rehydrates fresh entities from the DB instead of returning the
already-managed (first-level-cached) instances with their original near-simultaneous timestamps. The
ordering assertion no longer depends on wall-clock timing at all.

### WR-02: Test-only `application.properties` can silently mask a missing `@ServiceConnection`

**Files modified:** `backend/src/test/resources/application.properties`
**Commit:** 7b4a840
**Applied fix:** Applied the fix suggestion as given: replaced the real-looking
`jdbc:postgresql://localhost:5432/lexcv_test` with a bogus, non-resolvable host
(`this-property-must-be-overridden-by-ServiceConnection`) plus an explanatory comment, so any future
`@DataJpaTest` that forgets to pair `@ServiceConnection` with `Replace.NONE` fails fast and loudly
instead of silently connecting to whatever Postgres happens to be listening on `localhost:5432` on a
developer's machine.

### WR-03: `ExecutorService.shutdown()` doesn't reclaim leaked threads on the failure/timeout path

**Files modified:** `backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java`
**Commit:** 3e793ac
**Applied fix:** Applied the fix suggestion as given: replaced `executor.shutdown()` with
`executor.shutdownNow()` followed by `executor.awaitTermination(5, TimeUnit.SECONDS)` in the `finally`
block, so a timeout/failure on the lock-under-test no longer leaves a stuck worker thread (and its
open DB connection/lock) running past the test method in the shared forked JVM.

### WR-04: Unverified/likely-missing production migration undercuts the constraint-backstop test's guarantee

**Files modified:** `backend/migrations/91-add-parecer-versao-unique-constraint.sql` (new file)
**Commit:** ec37b4e
**Applied fix:** Per explicit task guidance (this repo has no automated migration runner — only
Hibernate `ddl-auto`, with `validate` in prod), added a new numbered manual migration script
following the established precedent of `81-add-facto-ordem-unique-constraint.sql`,
`82-add-honorario-processo-unique-constraint.sql`, and
`88-add-notificacao-dedup-unique-constraint.sql`: `ALTER TABLE t_parecer_versao ADD CONSTRAINT
uk_parecer_versao_solicitacao_numero UNIQUE (solicitacao_id, numero_versao);`. The script's header
documents that it is a required manual step to run against each environment before/alongside the
deploy that relies on this constraint, and notes to first check whether the constraint already
exists in a given environment (e.g. if that DB was ever bootstrapped with `ddl-auto: update`) to
avoid a duplicate-constraint error. This does not by itself prove the constraint is live in any
already-running production database — a human still needs to execute this script (or confirm the
constraint already exists there) before treating `ParecerVersaoConcorrenciaIT`'s second test as
evidence the DB-level backstop is active in production.

### WR-05: CI "gating" test job only ran post-merge, not on pull requests

**Files modified:** `.github/workflows/deploy.yml`
**Commit:** (orchestrator, post user approval)
**Applied fix:** The code-fixer's Edit attempt was blocked by the Claude Code auto-mode permission
classifier as an unauthorized modification to shared CI/CD infrastructure. The orchestrator surfaced
this to the user explicitly (CI/CD changes affect shared systems and warrant confirmation), the user
approved, and the orchestrator applied it directly: added `pull_request: branches: [master]` alongside
the existing `push` trigger, and added `if: github.event_name == 'push'` to the `build-and-push` job
so it never runs on pull-request events (only the `test` job runs on PRs; image builds/pushes remain
push-to-master-only, unchanged from before).

**Original issue:** The workflow trigger was `on: push: branches: [master]` only — there was no
`pull_request` trigger — so `mvn -B verify` and `spotbugs:check` only ever ran after code had
already landed on `master`. A broken commit could merge to `master` freely; the "gating" job only
prevented the already-merged, already-broken commit from being built into a container image, which
was materially weaker than "gating" implies.

---

_Fixed: 2026-07-13T17:00:00Z_
_Fixer: Claude (gsd-code-fixer, WR-05 applied by orchestrator post-approval)_
_Iteration: 1_
