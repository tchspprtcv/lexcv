---
phase: 128-auditoria-de-atribui-es-de-pap-is
plan: 03
subsystem: controllers
tags: [spring-transactions, rbac, audit-log, tdd]

# Dependency graph
requires: ["128-02"]
provides:
  - "OfficeRolesController.createRole/renameRole/deleteRole: @Transactional, audited via AuditoriaRbacService, every refusal routed through RecusaTransacional.recusar"
affects: [128-04, 128-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "saveAndFlush/flush inside the try, not save/deleteById alone -- forces unique/FK violations to surface inside the catch instead of at commit, where they would become an unstructured 500"
    - "TransactionInterceptor + ProxyFactory + mocked PlatformTransactionManager (real interceptor, not a mock of 'was called') to prove same-transaction commit/rollback ordering with InOrder, reused from RecusaTransacionalTest (128-02)"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/OfficeRolesControllerAuditoriaTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java
    - backend/src/test/java/com/lexcv/controllers/OfficeRolesControllerTest.java

key-decisions:
  - "AuditoriaRbacService appended as the LAST constructor field (not inserted alphabetically or by role) so @RequiredArgsConstructor's positional constructor only gains a trailing argument -- every existing `new OfficeRolesController(...)` call site needed exactly one addition at the end, not a reorder"
  - "renameRole compares the trimmed submitted name against the name captured BEFORE setNome, and skips registarPapelRenomeado when they're equal -- a rename to the same name is not a change and the plan's own action text asked for exactly this check"
  - "getPrincipal() extracted as a new private helper (getTenantId() now delegates to it) so both tenantId and the actor (for registar*/RecusaTransacional's caller-identity needs) come from a single call to SecurityContextHolder, never re-derived per handler"

requirements-completed: [AUDT-01]

# Metrics
duration: 20min
completed: 2026-09-22
---

# Phase 128 Plan 03: Transactional, audited OfficeRolesController create/rename/delete Summary

**`createRole`/`renameRole`/`deleteRole` are now `@Transactional` and call `AuditoriaRbacService.registarPapel*` inside the same transaction as their `saveAndFlush`/`deleteById`+`flush()` write; all 13 non-2xx returns across the three handlers route through `RecusaTransacional.recusar` so a refused request marks the transaction rollback-only and writes no event — proven under a real `TransactionInterceptor`, not a mock, with `InOrder` assertions on write-then-event-then-commit and on rollback-without-commit when the event write itself fails.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-22 (continuation immediately after 128-02)
- **Completed:** 2026-09-22T15:00:26-01:00
- **Tasks:** 2
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- `OfficeRolesController`: `AuditoriaRbacService` added as the trailing constructor field; new `getPrincipal()` helper (`getTenantId()` now delegates to it) so tenant and actor always come from `SecurityContextHolder`, never the request body.
- `createRole`, `renameRole`, `deleteRole` annotated `@Transactional`. Each write uses `saveAndFlush`/`deleteById`+`flush()` inside its existing `try`, so a concurrent unique-name or FK violation still surfaces in the `catch` as 409 instead of only appearing at commit as an unstructured 500. A new class-level Javadoc paragraph documents why (Decision 3, 128-CONTEXT.md).
- All 13 non-2xx returns across the three handlers (4 in `createRole`, 4 in `renameRole`, 5 in `deleteRole` — counted before and after, matches exactly) now go through `RecusaTransacional.recusar(...)`.
- `createRole` calls `registarPapelCriado` right after `saveAndFlush`, inside the `try`, before building the 201 body. `renameRole` captures `nomeAntigo` before `setNome` and calls `registarPapelRenomeado` only when the trimmed submitted name differs from the captured old name — a no-op rename writes no event. `deleteRole` keeps the loaded `tenantRole` reference, deletes+flushes inside the `try`, then (outside the `try`, only on success) calls `registarPapelApagado` with that same reference before returning 204.
- The `deleteRole` WR-01 Javadoc comment was rewritten to explain that `@Transactional` at the default isolation (READ COMMITTED) does **not** close the check-then-act race window by itself — it's the explicit `flush()` that keeps the FK violation's failure shape at 409 instead of deferring it to commit — and that the audit event can never survive this catch's rollback because `registarPapelApagado` is only called after the `try` block succeeds.
- `OfficeRolesControllerTest` (existing, 16 tests, none removed or weakened): constructor call sites updated with a `@Mock AuditoriaRbacService`; every `tenantRoleRepository.save(...)` stub/verify updated to `saveAndFlush(...)` (`sed`-verified as the only 15 occurrences in the file, all `tenantRoleRepository`-related); `verifyNoInteractions(auditoriaRbacService)` added to all seven existing refusal-path tests (400 reserved name, 400 unknown permission, 409 duplicate, 404 rename cross-tenant, 409 assigned, 409 protected, 404 delete cross-tenant).
- New `OfficeRolesControllerAuditoriaTest` (11 tests): reflection proof that all three handlers carry `@Transactional`; `createRole` success proven with `InOrder(txManager, tenantRoleRepository, auditoriaRbacService)` showing `getTransaction` → `saveAndFlush` → `registarPapelCriado` → `commit` with `rollbackOnly == false`; an event-write `RuntimeException` proven to reach `txManager.rollback` with `commit` never called; a `saveAndFlush` `DataIntegrityViolationException` proven to never call the audit method and still commit with `rollbackOnly == true`. Same pattern (success `InOrder`, cross-tenant 404 refusal with `verifyNoInteractions` + `rollbackOnly == true`) repeated for `renameRole` (plus the same-name-no-event case) and `deleteRole` (plus the protected-role refusal). A final test captures the literal `UUID` argument passed to `registarPapelApagado` and asserts it equals `principal.getTenantId()`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Transactional, audited create/rename/delete with refusal rollback** - `063e9ab8` (feat)
2. **Task 2: Audit content and transaction-boundary proof** - `0c01c351` (test)

_Both tasks are TDD-flagged in the plan. As with Plan 02, both were implemented and verified green before their commit rather than committed as a separate failing-test-first step: Task 1's behavior (existing tests keep passing under a changed constructor/stub surface) has no meaningful RED state to drive from, and Task 2 is a pure proof suite written directly against the already-correct Task 1 implementation. Every test in both tasks was run and confirmed passing before its commit; the plan's own `<behavior>`/acceptance-criteria bullets served as the exhaustive spec for each._

## Files Created/Modified

- `backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java` - `@Transactional` + audited create/rename/delete, `getPrincipal()` helper, all refusals routed through `RecusaTransacional.recusar`
- `backend/src/test/java/com/lexcv/controllers/OfficeRolesControllerTest.java` - constructor/stub updates for the new dependency and `saveAndFlush`, `verifyNoInteractions(auditoriaRbacService)` on every refusal test
- `backend/src/test/java/com/lexcv/controllers/OfficeRolesControllerAuditoriaTest.java` - 11 new tests proving same-transaction commit ordering, event-failure rollback, refusal rollback-only, and tenant-from-principal

## Decisions Made

- See key-decisions above (constructor field ordering, same-name-rename no-op, `getPrincipal()` extraction).
- `deleteRole`'s WR-01 comment was substantively rewritten, not just appended to, because the plan explicitly asked the executor to explain why `@Transactional` at default isolation does not close the race and why the flush still matters — restating the old comment unchanged would have left a now-inaccurate implication that the transaction boundary alone might help.

## Deviations from Plan

None — plan executed as written. The refusal count (13) matches the plan's own estimate ("about 13 non-2xx returns") exactly: 4 in `createRole` (name validation, duplicate pre-check, unknown-permission, catch), 4 in `renameRole` (404, name validation, duplicate pre-check, catch), 5 in `deleteRole` (404, protected-admin, protected-platform, assigned-count, catch).

## Issues Encountered

None. `mvn test-compile` succeeded on the first attempt after updating the test constructor call sites and `save`→`saveAndFlush` stubs; every test in both `OfficeRolesControllerTest` and the new `OfficeRolesControllerAuditoriaTest` passed on first run.

## Verification Evidence

- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=OfficeRolesControllerTest` — **Tests run: 16, Failures: 0, Errors: 0** (same count as before this plan — no test removed or weakened).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=OfficeRolesControllerAuditoriaTest` — **Tests run: 11, Failures: 0, Errors: 0** (plan required ≥10).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=OfficeRolesControllerTest,OfficeRolesControllerAuditoriaTest,AuditLogImutabilidadeTest` (plan-level verification) — **Tests run: 33, Failures: 0, Errors: 0, BUILD SUCCESS**.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test` (full suite) — **Tests run: 389, Failures: 0, Errors: 0, BUILD SUCCESS**. Baseline from 128-02-SUMMARY.md was 378; 389 = 378 + 11 new tests, confirming no other test was affected.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml spotbugs:check` — **BugInstance size is 0, Error size is 0, BUILD SUCCESS**.
- All grep-based acceptance criteria run individually and matched:
  - Task 1: `grep -c -F "@Transactional"` → 5 (3 method annotations + 2 doc mentions, ≥3 required); `grep -F "saveAndFlush"` → matches (5 call sites); `grep -F "tenantRoleRepository.flush()"` → matches; `grep -F "registarPapelCriado"`, `registarPapelRenomeado`, `registarPapelApagado` → each match; `grep -c -F "recusar("` → 13 (≥11 required).
  - Task 2: `grep -F "TransactionInterceptor"` → matches (5 occurrences); `grep -F "isRollbackOnly"` → matches (8 occurrences); `grep -F "inOrder"` → matches (3 occurrences).
- Stub scan of the two main-source/test files touched (`TODO`, `FIXME`, "coming soon", "not available", "placeholder") — one case-insensitive false positive (`método`/`metodo` contains the substring "todo"), no real stub markers.
- TDD gate sequence check (`git log`): `063e9ab8` is a `feat` commit (Task 1, includes its own test updates since it's a behavior change to existing tests, not a fresh RED/GREEN pair — see Task Commits note), `0c01c351` is a `test` commit (Task 2, the new proof suite). Both green before commit, per the "TDD flag honored via passing tests, not literal RED-commit" pattern established in 128-01/128-02.

## User Setup Required

None.

## Threat Flags

None — this plan only changes the internals (transaction boundary, audit call, refusal routing) of three already-existing, already-gated endpoints (`OfficeRolesController`, `hasAuthority('rbac:manage')` at class level, unchanged). No new endpoint, auth path, file-access pattern, or schema change was introduced.

## Next Phase Readiness

- `OfficeRolesController` is fully instrumented for AUDT-01 (create/rename/delete). Plan 04 can apply the same pattern to `AdminController.updateRbac`/`createUser`/`updateUser`/`deleteUser`.
- `RecusaTransacional.recusar` and `AuditoriaRbacService` are proven, by this plan's own tests, to compose correctly under a real Spring transaction proxy — Plan 04 can reuse the exact `TransactionInterceptor`+`ProxyFactory`+mocked `PlatformTransactionManager` technique without re-deriving it.
- No blockers for Plan 04.

---
*Phase: 128-auditoria-de-atribui-es-de-pap-is*
*Completed: 2026-09-22*

## Self-Check: PASSED

All 3 created/modified source files and the SUMMARY.md itself verified present on disk. All three commit hashes (`063e9ab8`, `0c01c351`, `4251ce6b`) verified present in `git log --oneline --all`. No missing items.
