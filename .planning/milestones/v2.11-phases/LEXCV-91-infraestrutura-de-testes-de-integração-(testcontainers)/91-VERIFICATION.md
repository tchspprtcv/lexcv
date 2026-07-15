---
phase: 91-infraestrutura-de-testes-de-integracao
verified: 2026-07-13T15:10:00Z
status: human_needed
score: 6/8 must-haves verified (2 code-verified but execution-blocked by sandbox environment)
overrides_applied: 0
human_verification:
  - test: "Run `mvn -B verify` (or `mvn -B test -Dtest=NotificacaoRepositoryIT`) against a real, reachable Docker daemon — e.g. the GitHub Actions `test` job in .github/workflows/deploy.yml on its first push to master, or any machine where Testcontainers can talk to Docker — and confirm NotificacaoRepositoryIT's 4 test methods pass."
    expected: "BUILD SUCCESS; postgres:16-alpine container starts; all 4 methods (tenant/destinatario scoping, categoria null-guard, lida null-guard, pagination/ordering) pass with 0 failures/errors."
    why_human: "This sandbox's Docker Desktop 4.80 npipe endpoint returns a malformed/HTTP-400 response to Testcontainers 1.20.4's docker-java client (`Could not find a valid Docker environment`), even though the native `docker` CLI works fine here. This was independently reproduced twice in this verification session (once via `mvn -B test -Dtest=NotificacaoRepositoryIT`, once via `mvn -B verify`), confirming it is a transport-layer sandbox limitation, not a code defect — analogous to this project's established MINIO_ENDPOINT blocker pattern (see STATE.md verification_gap entries for Phases 85/86/87/89). It cannot be resolved or worked around from within this sandbox."
  - test: "Run the same `mvn -B verify` against real Docker and confirm ParecerVersaoConcorrenciaIT's 2 test methods pass."
    expected: "BUILD SUCCESS; the lock-race method asserts the resulting numeroVersao set is exactly {1, 2} (never a duplicate); the backstop method asserts DataIntegrityViolationException is thrown on the saveAndFlush-forced colliding insert."
    why_human: "Same Docker/npipe sandbox blocker as above — confirmed identically for this test class in the same `mvn -B verify` run."
---

# Phase 91: Infraestrutura de Testes de Integração (Testcontainers) Verification Report

**Phase Goal:** O backend passa a ter, pela primeira vez, testes de integração reais contra PostgreSQL, cobrindo os dois riscos de maior severidade identificados (query nativa de `Notificacao`, lock de concorrência de `numeroVersao`), com uma decisão explícita sobre a sua execução em CI.
**Verified:** 2026-07-13T15:10:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Testcontainers integration-test infrastructure exists for the first time in this backend (deps + failsafe binding + shared test properties) | ✓ VERIFIED | `backend/pom.xml` has 3 test-scoped, version-less deps (`spring-boot-testcontainers`, `org.testcontainers:junit-jupiter`, `org.testcontainers:postgresql`) + explicit `maven-failsafe-plugin` declaration; `backend/src/test/resources/application.properties` exists with placeholder-free datasource + `ddl-auto=create-drop`. Confirmed `mvn -q -DskipTests test-compile` succeeds. |
| 2 | `NotificacaoRepositoryIT` correctly exercises `buscarPorFiltros` (tenant+destinatario scoping, both CAST-null-guarded `categoria`/`lida` filters, Pageable ordering/totals) against a real `postgres:16-alpine` container | ✓ VERIFIED (code) / ? UNCERTAIN (live pass) | File exists (152 lines, well above the 60-line minimum), uses `@DataJpaTest`+`@AutoConfigureTestDatabase(Replace.NONE)`+`@Testcontainers`+`@ServiceConnection` exactly per ARCHITECTURE.md Pattern A, `@Autowired NotificacaoRepository`, and 4 test methods matching every acceptance criterion in 91-01-PLAN.md verbatim (scoping, `categoria=null`/concrete, `lida=null`/concrete, `PageRequest.of(0,2)` + DESC ordering assertion). Reproduced independently in this session: `mvn -B test -Dtest=NotificacaoRepositoryIT` and `mvn -B verify` both fail identically with `Could not find a valid Docker environment` — a sandbox Docker/npipe transport issue, not a test-logic error (see human_verification). |
| 3 | `ParecerVersaoConcorrenciaIT` proves the PESSIMISTIC_WRITE lock serializes concurrent `numeroVersao` increments (sequential distinct {1,2}) and the DB unique constraint rejects duplicates | ✓ VERIFIED (code) / ? UNCERTAIN (live pass) | File exists (181 lines, above the 90-line minimum). Method A: `@Transactional(propagation = NOT_SUPPORTED)`, `TransactionTemplate`/`PlatformTransactionManager`, `ExecutorService`+`CountDownLatch`, bounded `Future.get(20, TimeUnit.SECONDS)`, asserts `Set.of(1, 2)`. Method B: `saveAndFlush(...)` used for both inserts, colliding call placed inside `assertThrows(DataIntegrityViolationException.class, ...)`. Matches `ParecerSolicitacaoRepository.findByIdForUpdate` / `ParecerVersaoRepository.findMaxNumeroVersaoBySolicitacaoId` / `findBySolicitacaoId` signatures exactly; `ParecerVersao`'s `@UniqueConstraint(columnNames={"solicitacao_id","numero_versao"})` confirmed present. Same Docker-connectivity failure reproduced independently as truth #2 (see human_verification). |
| 4 | Both new `*IT` tests run on the `@DataJpaTest` slice, never requiring `MINIO_ENDPOINT` or any other production env var | ✓ VERIFIED | Both classes use `@DataJpaTest` (never `@SpringBootTest`), which by Spring Boot slice-test convention never instantiates `MinioConfig`/`SecurityConfig`. Independently confirmed: the actual failures observed in this session are `IllegalStateException: Could not find a valid Docker environment` at Testcontainers' container-resolution step — occurring before/independent of Spring context startup — with zero `MINIO_ENDPOINT`/`could not resolve placeholder` errors in the output. |
| 5 | The 3 pre-existing unit test classes (`RiscoPrazoServiceTest`, `NotificacaoServiceTest`, `AlertasDiariosJobTest`) still pass unchanged (no regression from the pom.xml/config changes) | ✓ VERIFIED | Ran `mvn -B test` independently in this session: `Tests run: 44, Failures: 0, Errors: 0` across exactly those 3 classes (`AlertasDiariosJobTest` 9, `NotificacaoServiceTest` 20, `RiscoPrazoServiceTest` 15). `BUILD SUCCESS`. |
| 6 | Fast-unit/slow-container separation works: `mvn test` (Surefire) excludes `*IT` classes; `mvn verify` (Failsafe) includes them | ✓ VERIFIED | `mvn -B test` (no `-Dtest` override) ran only the 3 unit classes (44 tests), never touching either `*IT` class. `mvn -B verify` (independently run this session) explicitly logged `--- failsafe:3.5.2:integration-test (default) @ backend ---` followed by `Running com.lexcv.repositories.NotificacaoRepositoryIT` and `Running com.lexcv.repositories.ParecerVersaoConcorrenciaIT` — proving Failsafe's binding correctly picks up both new classes (both then fail at the identical Docker-connectivity point, not a wiring defect). |
| 7 | CI workflow (`deploy.yml`) gains a `test` job running `mvn -B verify` + `mvn -B spotbugs:check` on `ubuntu-latest`, gating `build-and-push` via `needs: test` | ✓ VERIFIED | `.github/workflows/deploy.yml` has job `test:` (`runs-on: ubuntu-latest`, `permissions: contents: read` only, no `packages: write`) with steps `actions/checkout@v4` → `actions/setup-java@v4` (temurin, JDK 23, `cache: maven`) → `mvn -B verify` (working-directory `backend`) → `mvn -B spotbugs:check` (working-directory `backend`). `build-and-push:` declares `needs: test`; its existing steps/permissions/caching untouched. Independently confirmed `mvn -B spotbugs:check` passes locally (`BugInstance size is 0`, `BUILD SUCCESS`), so the second CI step is expected to pass on the runner too. |
| 8 | TEST-03 decision (CI runs tests + SAST as a gate) explicitly recorded with rationale in PROJECT.md Key Decisions and STATE.md | ✓ VERIFIED | `.planning/PROJECT.md` line 183 contains a `TEST-03`-tagged Key Decision row stating both the affirmative decision and the `dependency-check` deferral. `.planning/STATE.md` line 85 has a matching `(v2.11 TEST-03)` Decisions entry; all three previously-stale H2/Testcontainers references (Pending Todos bullet, `tooling` Deferred Items row, Phase 86 `verification_gap` row) are updated to reflect closure by Phase 91 (lines 93, 108, 113). |

**Score:** 6/8 truths fully VERIFIED; 2/8 VERIFIED at the code level but their live-execution claim is UNCERTAIN in this sandbox (routed to human/CI verification, not counted as failed — see Gaps Summary).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/pom.xml` | Testcontainers test-scoped deps (BOM-managed, no version) + failsafe binding | ✓ VERIFIED | 3 deps present, no `<version>` tags, no `com.h2database` anywhere; `maven-failsafe-plugin` declared in `<build><plugins>`. |
| `backend/src/test/resources/application.properties` | Placeholder-free datasource + `ddl-auto=create-drop` | ✓ VERIFIED | Exactly 4 keys present as specified; `grep -c create-drop` = 1. |
| `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java` | `@DataJpaTest`+`@Testcontainers`+`@ServiceConnection` IT for `buscarPorFiltros` | ✓ VERIFIED (exists, substantive, wired) — live pass ? UNCERTAIN | 152 lines (min 60); imports/uses `NotificacaoRepository.buscarPorFiltros` directly; container annotated correctly. |
| `backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java` | Two-thread lock test + unique-constraint backstop test | ✓ VERIFIED (exists, substantive, wired) — live pass ? UNCERTAIN | 181 lines (min 90); uses `findByIdForUpdate`, `TransactionTemplate`/`PlatformTransactionManager`, `saveAndFlush` correctly. |
| `.github/workflows/deploy.yml` | Test/SAST gate job before build-and-push | ✓ VERIFIED | Contains `spotbugs:check`, `mvn -B verify`, `needs: test`; `test:` job key present with `contents: read` only. |
| `.planning/PROJECT.md` | Recorded TEST-03 decision | ✓ VERIFIED | Contains `TEST-03` in a Key Decisions row describing the CI gate + dependency-check deferral. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `NotificacaoRepositoryIT` | `PostgreSQLContainer("postgres:16-alpine")` | `@Container` + `@ServiceConnection` | ✓ WIRED | Both annotations present on the static container field (line 42-44). |
| `NotificacaoRepositoryIT` | `NotificacaoRepository.buscarPorFiltros` | `@Autowired` repository call under assertion | ✓ WIRED | Called in all 4 test methods with real assertions on returned `Page<Notificacao>`. |
| `ParecerVersaoConcorrenciaIT` | `ParecerSolicitacaoRepository.findByIdForUpdate` | Row lock acquired inside worker thread transaction | ✓ WIRED | Called at line 111, inside the `TransactionTemplate.executeWithoutResult` block run by both `ExecutorService` threads. |
| `ParecerVersaoConcorrenciaIT` | `PlatformTransactionManager`/`TransactionTemplate` | Two independently-committing transactions on two threads | ✓ WIRED | `TransactionTemplate` built from injected `PlatformTransactionManager`; `ExecutorService`(2) + `CountDownLatch` + bounded `Future.get`. |
| `deploy.yml build-and-push` | `deploy.yml test` job | `needs: test` | ✓ WIRED | Line 42: `needs: test` present; downstream job otherwise untouched. |
| `deploy.yml test` job | `mvn verify` + `mvn spotbugs:check` | Run steps on `ubuntu-latest` | ✓ WIRED | Both commands present as separate steps with `working-directory: backend`. |

### Data-Flow Trace (Level 4)

Not applicable — this phase delivers test infrastructure and a CI gate, not a UI/data-rendering artifact. No dynamic-data component to trace.

### Behavioral Spot-Checks / Probe Execution

Executed directly in this verification session (not trusted from SUMMARY.md claims):

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Test-source compiles (both new `*IT` classes, no syntax/type errors) | `mvn -q -DskipTests test-compile` | No output (clean success) | ✓ PASS |
| Pre-existing unit suite has zero regression | `mvn -B test` | `Tests run: 44, Failures: 0, Errors: 0` — `BUILD SUCCESS` | ✓ PASS |
| SpotBugs/FindSecBugs SAST gate (2nd CI step) passes locally | `mvn -B spotbugs:check` | `BugInstance size is 0`, `Error size is 0` — `BUILD SUCCESS` | ✓ PASS |
| Failsafe binding actually picks up both new `*IT` classes under `mvn verify` | `mvn -B verify` | Failsafe log shows `Running com.lexcv.repositories.NotificacaoRepositoryIT` then `Running com.lexcv.repositories.ParecerVersaoConcorrenciaIT`; both fail identically with `Could not find a valid Docker environment` (`ContainerFetchException`) — a sandbox transport failure, not a test-logic failure | ⚠️ ENV_BLOCKED (wiring confirmed; live pass unresolved in this sandbox) |
| `NotificacaoRepositoryIT` passes against real Postgres | `mvn -B test -Dtest=NotificacaoRepositoryIT` | Identical `Could not find a valid Docker environment` failure, reproduced independently in this session | ⚠️ ENV_BLOCKED — routed to human_verification |

No conventional `scripts/*/tests/probe-*.sh` probes exist for this phase; the above `mvn` invocations serve as the phase's de facto probes and were run directly by this verifier (not narrated from SUMMARY.md).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|--------------|------------|-------------|--------|----------|
| TEST-01 | 91-01-PLAN.md | Teste de integração (Testcontainers+PostgreSQL) cobre `buscarPorFiltros` de `Notificacao` | ✓ SATISFIED (code) / CI-run pending | `NotificacaoRepositoryIT` exists, is substantive, correctly wired to the real repository method; live-pass confirmation pending a working Docker environment (see human_verification). |
| TEST-02 | 91-02-PLAN.md | Teste de integração cobre o lock de concorrência de `numeroVersao` em `ParecerVersao` | ✓ SATISFIED (code) / CI-run pending | `ParecerVersaoConcorrenciaIT` exists, is substantive, correctly replicates the `createVersao` lock+increment+insert path; live-pass confirmation pending. |
| TEST-03 | 91-03-PLAN.md | Decisão registada e aplicada sobre CI a correr `mvn test`/`spotbugs:check` | ✓ SATISFIED | Both the decision (PROJECT.md/STATE.md) and the implementation (`deploy.yml` `test` job + `needs: test`) are present and verified. |

**Orphaned requirements check:** REQUIREMENTS.md Traceability table maps exactly TEST-01, TEST-02, TEST-03 to Phase 91 — no additional requirement IDs are mapped to this phase that aren't claimed by a plan. No orphans.

**Note on REQUIREMENTS.md checkbox state:** TEST-01/02/03 still show `[ ]` (unchecked) and "Pending" in the Traceability table. This matches this project's established pattern (confirmed via `git log`/`git show d014e52`): the checkbox/traceability update is applied by the orchestrator's "complete phase execution" commit alongside VERIFICATION.md, which runs after this verification step — not a gap.

### Anti-Patterns Found

None. Scanned all phase-modified files (`backend/pom.xml`, `backend/src/test/resources/application.properties`, both new `*IT` classes, `.github/workflows/deploy.yml`) for `TODO|FIXME|XXX|TBD|HACK|PLACEHOLDER` and stub-return patterns — the only match was the Portuguese word "placeholders" inside a doc comment describing environment-variable placeholders (not a debt marker). No empty implementations, no hardcoded-empty stubs, no unreferenced debt markers.

### Human Verification Required

### 1. NotificacaoRepositoryIT live pass against real Postgres

**Test:** Run `mvn -B verify` (or `mvn -B test -Dtest=NotificacaoRepositoryIT`) on a machine/CI runner with a working Docker daemon (e.g., the `test` job in `.github/workflows/deploy.yml` on its first push to master, or `ubuntu-latest`/WSL2/native Linux).
**Expected:** `BUILD SUCCESS`; a `postgres:16-alpine` container starts; all 4 test methods pass (tenant+destinatario scoping, `categoria` null-guard, `lida` null-guard, pagination + DESC ordering).
**Why human:** This sandbox's Docker Desktop 4.80 npipe transport returns a malformed/HTTP-400 response to Testcontainers 1.20.4's docker-java client, confirmed independently in this session via two separate reproductions (`mvn -B test -Dtest=NotificacaoRepositoryIT` and `mvn -B verify`), both failing with `IllegalStateException: Could not find a valid Docker environment` before any test logic executes. This is a sandbox transport-layer limitation, not a code defect — the same class of blocker as this project's documented `MINIO_ENDPOINT` pattern (see STATE.md verification_gap entries for Phases 85-89).

### 2. ParecerVersaoConcorrenciaIT live pass against real Postgres

**Test:** Same command/environment as above, targeting `ParecerVersaoConcorrenciaIT`.
**Expected:** `BUILD SUCCESS`; Method A asserts the resulting `numeroVersao` set is exactly `{1, 2}`; Method B asserts `DataIntegrityViolationException` on the `saveAndFlush`-forced colliding insert.
**Why human:** Identical Docker/npipe sandbox blocker, reproduced in the same `mvn -B verify` run as truth #1.

### Gaps Summary

No code-level gaps were found. All artifacts exist, are substantive (well above minimum line counts), are correctly wired to the real repository/model types they target, and match every acceptance criterion in their respective PLAN.md files. Independent verification in this session (not trusted from SUMMARY.md) confirmed:
- Clean test-source compilation.
- Zero regression in the 44 pre-existing unit tests.
- Correct Surefire/Failsafe separation (Failsafe's binding genuinely picks up both new `*IT` classes under `mvn verify`; Surefire genuinely excludes them under plain `mvn test`).
- SpotBugs SAST (the CI's second gate step) passes cleanly today.
- The CI `test` job and its `needs: test` gate on `build-and-push` are correctly declared in `deploy.yml`.
- The TEST-03 decision is recorded with rationale in both PROJECT.md and STATE.md, and all three previously-stale STATE.md H2/Testcontainers references were updated to reflect closure.

The only unresolved item is that neither new `*IT` test class could be executed to a real pass/fail verdict against a live `postgres:16-alpine` container in this sandbox, because Testcontainers 1.20.4's docker-java client cannot negotiate with this sandbox's Docker Desktop 4.80 npipe endpoint (confirmed independently, twice, in this verification session — identical root cause and failure signature to what both 91-01-SUMMARY.md and 91-02-SUMMARY.md documented). This is an environment constraint, not a code defect, and is expected to resolve on the next push to master once the CI `test` job (91-03) runs on an `ubuntu-latest` runner with a native Docker daemon. Routed to human/CI verification rather than marked as a failing gap, consistent with this project's established `MINIO_ENDPOINT` precedent.

---

*Verified: 2026-07-13T15:10:00Z*
*Verifier: Claude (gsd-verifier)*
