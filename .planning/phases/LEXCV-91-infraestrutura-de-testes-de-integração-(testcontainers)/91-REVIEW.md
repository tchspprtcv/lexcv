---
phase: LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)
reviewed: 2026-07-13T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - backend/pom.xml
  - backend/src/test/resources/application.properties
  - backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java
  - backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java
  - .github/workflows/deploy.yml
findings:
  critical: 0
  warning: 5
  info: 2
  total: 7
status: issues_found
---

# Phase LEXCV-91: Code Review Report

**Reviewed:** 2026-07-13T00:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Reviewed the Testcontainers integration-test infrastructure added in this phase: the Maven
dependency/plugin wiring (`pom.xml`), the shared test `application.properties`, the two new
`*IT` classes (`NotificacaoRepositoryIT`, `ParecerVersaoConcorrenciaIT`), and the new CI
`test` job in `deploy.yml`.

The mechanics are sound and were independently verified rather than taken on faith:
`maven-failsafe-plugin`'s `integration-test`/`verify` executions really are pre-bound in
`spring-boot-starter-parent:3.4.1`'s `pluginManagement` (confirmed against the cached parent
POM), so declaring the plugin bare in this project's `<build>` correctly activates `*IT`
execution under `mvn verify`. The `@DataJpaTest` + `@AutoConfigureTestDatabase(Replace.NONE)`
+ `@ServiceConnection` combination is used correctly and the concurrency test's use of
`@Transactional(propagation = NOT_SUPPORTED)` to get two genuinely independent, committing
transactions is the correct, documented Spring idiom for this scenario. The line-number
references the test javadoc makes into `ParecerController.createVersao` (472/513/523) were
checked against the current file and are accurate.

That said, several issues reduce the robustness/trustworthiness of what this infrastructure
delivers: a flaky sleep-based ordering assertion, a test-only datasource config that can
silently mask a missing `@ServiceConnection` in future tests, a thread-leak risk on the
concurrency test's failure path, a CI "gating" job that only runs post-merge (not on PRs), and
— most substantively — evidence that the DB-level unique-constraint backstop the second
concurrency test asserts against may not actually exist in production, which undercuts the
guarantee the new test is meant to provide.

## Warnings

### WR-01: Sleep-based ordering assertion is a flaky-test pattern

**File:** `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java:129-150`
**Issue:** `buscarPorFiltros_paginacao_...` relies on `Thread.sleep(5)` between inserts to force
distinct `created_at` values so the `ORDER BY n.created_at DESC` assertion is deterministic
(lines 135, 137). This is a classic flaky-test pattern: under CI load, coarser clock
resolution, or GC pauses, two inserts can still land in the same millisecond (or the scheduler
can delay the test thread far longer than 5ms without actually shrinking the gap it's supposed
to guarantee), non-deterministically breaking the ordering assertion on `content.get(0)` /
`content.get(1)`.
**Fix:** Set `createdAt` explicitly and deterministically instead of relying on wall-clock
sleeps, e.g. add a test-only setter/builder path or persist then immediately
`update ... set created_at = ?` per row with strictly increasing, hand-picked timestamps:
```java
Notificacao n1 = persistir(tenantId, destinatarioId, "FASE_ENTRADA", "id-30", false);
n1.setCreatedAt(LocalDateTime.now().minusSeconds(2));
notificacaoRepository.saveAndFlush(n1);
// ...repeat with minusSeconds(1), then now()
```
or add a secondary deterministic tiebreaker (e.g. `ORDER BY n.created_at DESC, n.id DESC`) plus
an explicit `id` comparison in the assertion so real-world clock ties don't flip the test.

### WR-02: Test-only `application.properties` can silently mask a missing `@ServiceConnection`

**File:** `backend/src/test/resources/application.properties:1-4`
**Issue:** This file hardcodes a real-looking connection (`jdbc:postgresql://localhost:5432/lexcv_test`,
`test`/`test`). Today it's harmless because both existing `@DataJpaTest` classes pair
`@AutoConfigureTestDatabase(Replace.NONE)` with `@ServiceConnection`, so the container's dynamic
`JdbcConnectionDetails` bean always wins over these properties. But that safety depends entirely
on every future `@DataJpaTest` remembering both annotations. If a future test adds
`@DataJpaTest` without `@ServiceConnection` (or without `Replace.NONE`), it will silently connect
to whatever real Postgres instance happens to be listening on `localhost:5432` on the developer's
machine (if any) instead of failing loudly — this can produce hidden state pollution or a test
that passes locally for the wrong reason and only fails in CI (where no such host exists), which
is a hard failure mode to debug.
**Fix:** Point at a value that fails fast and unambiguously if the Testcontainers wiring is ever
missing, e.g.:
```properties
spring.datasource.url=jdbc:postgresql://this-property-must-be-overridden-by-ServiceConnection:5432/lexcv_test
```
or add a one-line comment plus a repo convention (e.g. an abstract base `*IT` class, see IN-01)
that always pairs the two annotations so there's a single place to get it right instead of two
independent copy-pasted class headers.

### WR-03: `ExecutorService.shutdown()` doesn't reclaim leaked threads on the failure/timeout path

**File:** `backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java:120-133`
**Issue:** If the pessimistic lock under test were ever broken (the exact regression this test
exists to catch), one of `futureA.get(20, SECONDS)` / `futureB.get(20, SECONDS)` could throw
`TimeoutException` while the other worker thread is still blocked inside the DB call. The
`finally` block only calls `executor.shutdown()`, which stops accepting new tasks but does not
interrupt in-flight ones — the stuck worker thread (and its open DB connection/lock) can outlive
the test method. Since Surefire/Failsafe reuse the same forked JVM across test classes by
default, a leaked thread from this test could interfere with, or hold connection-pool resources
away from, subsequently-run test classes in the same fork.
**Fix:** Use `shutdownNow()` (and optionally `awaitTermination`) in the `finally` block so a
timeout/failure here doesn't leave background work running past the test:
```java
} finally {
    executor.shutdownNow();
    executor.awaitTermination(5, TimeUnit.SECONDS);
}
```

### WR-04: Unverified/likely-missing production migration undercuts the constraint-backstop test's guarantee

**File:** `backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java:145-179`
**Issue:** `createVersao_numeroVersaoDuplicado_constraintUnicaRejeitaComDataIntegrityViolationException`
proves that the `(solicitacao_id, numero_versao)` unique constraint declared via
`@Table(uniqueConstraints=...)` on `ParecerVersao` (`backend/src/main/java/com/lexcv/models/ParecerVersao.java:10-11`)
rejects a duplicate — but it only proves this against the Testcontainers schema, which is built
with `ddl-auto=create-drop` (i.e. generated straight from JPA annotations). Per this repo's own
documented pattern (see `Notificacao.java:8-12`: "ddl-auto=validate in prod never creates this
[constraint] from the annotation alone", paired with a manual migration
`backend/migrations/88-add-notificacao-dedup-unique-constraint.sql`), production runs
`ddl-auto=validate` and does **not** create constraints from annotations — they require a
hand-written migration. I checked `backend/migrations/` and there is no migration for the
`ParecerVersao` constraint (only `74`, `81`, `82`, `86`, `88` exist, covering other tables). The
commit that introduced this constraint (`02b46d3`, "fix(62): WR-01 add unique constraint on
(solicitacao_id, numero_versao)") only touched `ParecerVersao.java` — no accompanying SQL
migration, unlike every later constraint (`81`, `82`, `86`, `88`) which paired the annotation
change with a manual script once that pattern was established. This strongly suggests the
constraint this test validates as a "backstop" may not actually exist in the production
database, meaning the `PESSIMISTIC_WRITE` lock (test 1) is production's *only* real protection
against duplicate `numeroVersao` rows — if that lock ever regresses, there is currently no DB-level
safety net to catch it in production, only in this test's ephemeral schema.
**Fix:** Verify against the actual production schema (`\d t_parecer_versao` / `information_schema.table_constraints`)
whether `uk_...` (or an unnamed unique index) on `(solicitacao_id, numero_versao)` exists. If it
doesn't, add a manual migration mirroring `88-add-notificacao-dedup-unique-constraint.sql`, e.g.
`backend/migrations/91-add-parecer-versao-unique-constraint.sql`, and land it before relying on
this test as evidence the backstop is live in production.

### WR-05: CI "gating" test job only runs post-merge, not on pull requests

**File:** `.github/workflows/deploy.yml:3-5, 41-46`
**Issue:** The commit that added this job is titled "add gating test/SAST job to CI workflow"
and `build-and-push` correctly declares `needs: test` (line 42) so a failing test/SpotBugs run
blocks the Docker push. However, the workflow trigger is still `on: push: branches: [master]`
only (lines 3-5) — there is no `pull_request` trigger. That means `mvn -B verify` and
`spotbugs:check` only ever run *after* code has already landed on `master`; a broken commit can
merge to `master` freely, and the "gating" only stops the resulting (already-merged, already
broken) commit from being built into a container image. This is a materially weaker guarantee
than "gating" implies, and it means `master` itself is not protected from regressions this phase
was meant to catch.
**Fix:** Add a `pull_request` trigger (at minimum targeting `master`) that runs the same `test`
job, so regressions are caught before merge rather than after:
```yaml
on:
  push:
    branches: [master]
  pull_request:
    branches: [master]
```
(`build-and-push` already guards `push: ${{ github.ref == 'refs/heads/master' }}`, so adding a
PR trigger will not cause images to be pushed from PR runs.)

## Info

### IN-01: Testcontainers container/annotation boilerplate duplicated across both `*IT` classes

**File:** `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java:37-44`,
`backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java:55-62`
**Issue:** Both classes repeat the identical `@DataJpaTest` / `@AutoConfigureTestDatabase(Replace.NONE)`
/ `@Testcontainers` class annotations and the identical `@Container @ServiceConnection static
PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");` field. With
only two classes this is tolerable, but as more `*IT` classes are added the required-but-easy-to-
forget combination (see WR-02) will be copy-pasted repeatedly.
**Fix:** Extract a shared abstract base class (e.g. `AbstractPostgresIT`) carrying the
annotations and the static container field, and have new `*IT` classes extend it — this also
gives future contributors one place to fix if the annotation combination ever needs to change.

### IN-02: CI jobs have no `timeout-minutes`

**File:** `.github/workflows/deploy.yml:12-13, 41-43`
**Issue:** Neither the `test` nor `build-and-push` job declares `timeout-minutes`, so a hang
(e.g. a Testcontainers/Docker-daemon issue, or a genuine deadlock in the new concurrency test
that somehow bypasses its own 20s `Future.get` timeouts) would run until GitHub's default 360-minute
job timeout before failing, burning CI minutes.
**Fix:** Add a conservative bound, e.g. `timeout-minutes: 20` on `test` and `timeout-minutes: 30`
on `build-and-push`.

---

_Reviewed: 2026-07-13T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
