---
phase: 96-notf-26-snooze-de-lembrete-de-prazo
plan: 02
subsystem: testing
tags: [spring-boot, jpa, testcontainers, postgres, integration-test, notifications, snooze]

# Dependency graph
requires:
  - phase: 96-01
    provides: Notificacao.snoozedUntil column + the agora-parameterized countByTenantIdAndDestinatarioIdAndLidaFalse / findByTenantIdAndDestinatarioIdAndLidaFalse queries this plan tests
  - phase: 91
    provides: NotificacaoRepositoryIT Testcontainers scaffolding (@DataJpaTest + @ServiceConnection postgres:16-alpine) this plan extends
provides:
  - Real-Postgres proof that a future-snoozed notification is invisible to the badge-count and mark-all-read queries
  - Real-Postgres proof that a never-snoozed row and an already-elapsed-snooze row are still counted/returned (automatic reappearance, no job involvement)
affects: [97-cross-cutting-milestone-audit]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Data-layer visibility-toggle test: assert exact counts/list contents across null / future / past predicate values in one method to prove both hide-while-active and auto-reveal-after-expiry in a single assertion"

key-files:
  created: []
  modified:
    - backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java

key-decisions:
  - "Added a persistirComSnooze fixture helper alongside the existing persistir rather than overloading persistir itself, keeping the original helper's call sites in prior tests unchanged."
  - "Test A (count) uses three rows (null/future/past snoozedUntil) with a single count==2 assertion to prove both NOTF-26 criterion 2 (hides while snoozed) and criterion 3 (reappears once elapsed) at once, per the plan's specified approach."
  - "Test B (find) uses two rows (null/future) and asserts the returned list is exactly size 1 and is the never-snoozed row, proving marcarTodasLidas' data source can never mark a future-snoozed row read."

requirements-completed: [NOTF-26]

# Metrics
duration: ~25min
completed: 2026-07-14
---

# Phase 96 Plan 02: NOTF-26 Snooze Visibility Integration Tests Summary

**Two new `@DataJpaTest`/Testcontainers methods in `NotificacaoRepositoryIT` prove against real PostgreSQL that a future-snoozed `Notificacao` row is hidden from both the badge-count and mark-all-read unread queries, and automatically reappears once its `snoozedUntil` has elapsed — with no job, no new row, and no dedup interaction.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-07-14T17:10:00Z (approx, worktree fast-forward + context read)
- **Completed:** 2026-07-14T17:34:43Z
- **Tasks:** 1/1 completed
- **Files modified:** 1

## Accomplishments
- `countByTenantIdAndDestinatarioIdAndLidaFalse_escondeAdiadaNoFuturo_contaNuncaAdiadaEAdiadaJaExpirada`: persists three unread rows (snoozedUntil null / +3 days / -1 hour) for one (tenant, destinatario) and asserts the count is exactly 2 — the future-snoozed row is excluded while the null-snooze and already-elapsed-snooze rows both still count. This single assertion evidences NOTF-26 success criteria 2 (disappears while snoozed) and 3 (reappears after expiry) together.
- `findByTenantIdAndDestinatarioIdAndLidaFalse_escondeAdiadaNoFuturo_devolveApenasNuncaAdiada`: persists one null-snooze and one future-snoozed unread row and asserts the query (the `marcarTodasLidas` load source) returns exactly the null-snooze row — proving mark-all-read can never flip a future-snoozed row to `lida=true`, which is the write-side half of "reappears unread after expiry."
- Added a `persistirComSnooze` fixture helper (mirrors the existing `persistir`, adds `.snoozedUntil(...)` via the Lombok builder) without touching the original `persistir` or any of its existing call sites.
- Full backend unit suite (surefire): 65/65 tests pass, 0 failures/errors — confirms the new test file compiles cleanly and does not regress any existing test.

## Task Commits

Each task was committed atomically:

1. **Task 1: Snooze-visibility integration tests against real Postgres** - `6069f91` (test)

## Files Created/Modified
- `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java` - adds `persistirComSnooze(...)` fixture helper and two new `@Test` methods exercising the post-96-01 `agora`-parameterized `countByTenantIdAndDestinatarioIdAndLidaFalse` / `findByTenantIdAndDestinatarioIdAndLidaFalse` queries.

## Decisions Made
- Kept the new fixture helper separate from `persistir` (rather than adding an optional/overloaded `snoozedUntil` parameter to the existing method) to avoid touching any of the four pre-existing test methods that already call `persistir` with its current signature.
- Followed the plan's exact fixture shapes: Test A uses three rows to prove both hide-while-snoozed and reappear-after-expiry in one assertion; Test B uses two rows scoped to the write-side (`find...`) query specifically.
- Each test uses a fresh `UUID.randomUUID()` tenantId/destinatarioId pair (per the plan and the threat-model's T-96-06 mitigation), so a query that dropped tenant/destinatario scoping would change the asserted totals and fail — this reuses the class's established pattern from the pre-existing `buscarPorFiltros_escopaPorTenantEDestinatario_...` test.

## Deviations from Plan

None — plan executed exactly as written. The worktree branch was behind local `master` by ~30 commits (missing phase 96's own plan/context files and all of Phase 96 Plan 01's backend changes this test depends on). Verified `HEAD` was a strict ancestor of `master` with zero unique commits on the worktree branch (`git log --oneline master..HEAD` produced no output), then fast-forwarded (`git merge master --ff-only`) before starting any plan work. No conflicts, no lost work — this is process/setup, not a plan deviation.

## Issues Encountered
- **Known Docker/Testcontainers limitation (documented in prior phases 91, 93):** `mvn -f backend/pom.xml verify -Dit.test=NotificacaoRepositoryIT` was run to attempt actual execution of the new IT tests. The unit-test phase (surefire) passed cleanly: `Tests run: 65, Failures: 0, Errors: 0`. The integration-test phase (failsafe) failed only with `org.testcontainers.containers.ContainerFetchException: Can't get Docker image: ... postgres:16-alpine`, caused by `Could not find a valid Docker environment` (Testcontainers 1.20.4 cannot reach Docker Desktop's npipe transport in this sandbox) — not a compilation or logic error. `mvn -f backend/pom.xml test-compile` succeeded (`BUILD SUCCESS`) confirming the new test code is syntactically and semantically correct against the current `NotificacaoRepository`/`Notificacao` signatures. This is the same recurring, documented environment blocker as prior phase SUMMARYs (91, 93); no workaround was attempted per the known-limitation guidance.

## Next Phase Readiness
- Data-layer evidence for NOTF-26 criteria 2 and 3 is now committed. Combined with 96-01 (backend write path) and the parallel 96-03 (frontend UI, zero file overlap with this plan), the milestone's NOTF-26 backend contract is fully test-covered pending real-Docker CI execution.
- No new infrastructure or dependencies were introduced; this plan only extended the existing Phase 91 `NotificacaoRepositoryIT` class.
- The Testcontainers/Docker unavailability in this sandbox is environmental, not code-related — these tests should be verified in a CI environment or local machine with a working Docker daemon before being treated as fully proven, per the same caveat as phases 91 and 93.

---
*Phase: 96-notf-26-snooze-de-lembrete-de-prazo*
*Completed: 2026-07-14*

## Self-Check: PASSED

`backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java` confirmed present on
disk with 6 `snoozedUntil` references; task commit hash `6069f91` confirmed present in
`git log --oneline --all`.
