---
phase: 128-auditoria-de-atribui-es-de-pap-is
plan: 05
subsystem: controllers
tags: [rbac, audit-log, spring-transactions, mockito, tdd]

# Dependency graph
requires: ["128-02", "128-04"]
provides:
  - "AdminController.updateRbac/createUser/updateUser/deleteUser: wired to AuditoriaRbacService, recording papel_permissoes_alterar (AUDT-01) and papel_atribuir/papel_retirar (AUDT-02) only on success paths"
  - "AdminController.DiffPermissoesPapel: per-role permission-key diff computed against the current saved state before any setPermissions"
  - "AdminControllerAuditoriaTest: behavior proof that every audit call carries the correct antes/depois/diff arguments and that every refusal (400/403/404/409) writes nothing"
affects: [128-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-role permission diff computed BEFORE the write loop mutates tenantRole.setPermissions, stored in a parallel LinkedHashMap<TenantRole, DiffPermissoesPapel> keyed by the same TenantRole instance the write loop iterates"
    - "updateUser snapshots Set<TenantRole> papeisAntes immediately after the last-administrator DetentorPapelProtegidoOriginal snapshot (before ANY mutation), then only calls registarAtribuicoes when the request body actually contained tenantRoleIds"
    - "deleteUser snapshots papeisAntes before userRepository.deleteById, then calls registarAtribuicoes AFTER the delete -- proven with InOrder, not just call-count"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AdminControllerAuditoriaTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/AdminController.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerAtribuicaoPapeisEscritorioTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacEscritorioTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerUltimoAdministradorTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerTransacaoTest.java

key-decisions:
  - "updateRbac calls registarPermissoesAlteradas unconditionally for every resolved role in the write loop (never gated on the diff being non-empty in the controller itself) -- the plan's own behavior bullet confirms this: 'for R2 it is called with both empty (the service no-ops)'. Emptiness filtering is AuditoriaRbacService's responsibility (Plan 02), not the controller's, keeping the diff-then-write loop simple and matching the existing 'validate-then-write, one entry per resolved role' idiom already used for tenantRoleRepository.save"
  - "DiffPermissoesPapel is a private record (adicionadas, removidas) local to AdminController, computed against tenantRole.getPermissions() BEFORE the entry is added to the existing `resolvido` map and BEFORE any setPermissions call -- mirrors the plan's instruction to diff against the CURRENT state, never the submitted one"
  - "updateUser's papeisAntes snapshot is taken immediately after the existing detentorOriginal (last-administrator) snapshot, both for the same reason: user.getTenantRoles() is mutated later in the same method (tenantRoleIds branch), so the antes copy has to be a HashSet copy taken before ANY mutation, never read from the already-mutated instance"

requirements-completed: [AUDT-01, AUDT-02]

# Metrics
duration: 11min
completed: 2026-09-22
---

# Phase 128 Plan 05: Audited AdminController write handlers Summary

**`AdminController`'s four transactional write handlers (`updateRbac`, `createUser`, `updateUser`, `deleteUser`) now call `AuditoriaRbacService` inside their existing `@Transactional` boundary — a per-role permission diff computed against the pre-write state for `updateRbac`, and role-assignment diffs (snapshotted before mutation, or before the hard delete) for the other three — with a dedicated 11-test Mockito suite proving both the argument shape of every successful call and the silence of every refused one.**

## Performance

- **Duration:** ~11 min
- **Started:** 2026-09-22T15:41Z (continuation immediately after 128-07)
- **Completed:** 2026-09-22T15:52Z
- **Tasks:** 2
- **Files modified:** 10 (1 created, 9 modified)

## Accomplishments

- `AdminController` gained `private final AuditoriaRbacService auditoriaRbacService` as the trailing constructor field (after `tenantRoleRepository`, per the plan's "only a trailing argument, never reordered" constraint) — all eight pre-existing test files that construct `AdminController` positionally gained a matching `@Mock` and the trailing constructor argument, with zero other changes to those files.
- `updateRbac` (AUDT-01): a new `DiffPermissoesPapel` record captures `adicionadas`/`removidas` permission-key sets per role, computed against `tenantRole.getPermissions()` (the CURRENT saved state) before any `setPermissions` call, and kept in a parallel `LinkedHashMap<TenantRole, DiffPermissoesPapel>` keyed by the same `TenantRole` instance the write loop already iterates. `auditoriaRbacService.registarPermissoesAlteradas` is called once per role, immediately after that role's `tenantRoleRepository.save` — a full-matrix `PUT` that only changes one role therefore produces exactly one *meaningful* event (the service itself no-ops on an empty diff; the controller does not filter).
- `createUser` (AUDT-02): `registarAtribuicoes(tenant, principal, savedUser, Set.of(), user.getTenantRoles(), null)` called right after `userRepository.save`.
- `updateUser` (AUDT-02): `Set<TenantRole> papeisAntes` snapshotted immediately after the existing `detentorOriginal` (last-administrator) snapshot — before ANY mutation in the method — and `registarAtribuicoes` is called after `userRepository.save`, but ONLY when the request body contained `tenantRoleIds`. Deactivation, name, email, password and free-form `permissions` edits never call the audit service (Decision 3 scope), documented inline.
- `deleteUser` (AUDT-02, T-128-25): `papeisAntes` snapshotted before `userRepository.deleteById`, then `registarAtribuicoes(tenant, principal, user, papeisAntes, Set.of(), AuditoriaRbacService.MOTIVO_UTILIZADOR_ELIMINADO)` called AFTER the delete — proven with `InOrder`, not merely call-count — relying on the in-memory `user` still carrying its id/nome after the hard delete (JPA does not null them out).
- `AdminControllerAuditoriaTest` (new, 11 tests): captures the real arguments passed to `AuditoriaRbacService` for every success path (`updateRbac` two-roles-one-changed, `createUser` with `tenantRoleIds`, `updateUser` role-swap with pre-mutation `antes`, `deleteUser` two-role removal with `InOrder`), and proves `verifyNoInteractions(auditoriaRbacService)` on every refusal path exercised (`updateRbac` 409 floor-lock, `updateUser` name-only edit / PLATAFORMA_ADMIN 403 / foreign-tenant 404 / last-administrator 409, `deleteUser` foreign-tenant 404). The final test reuses the `ProxyFactory` + real `TransactionInterceptor` + mocked `PlatformTransactionManager` technique from `AdminControllerTransacaoTest` to prove that an exception thrown inside `registarAtribuicoes` rolls the whole `createUser` transaction back (`txManager.rollback` called, `commit` never).

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire AuditoriaRbacService into AdminController and record events in the four handlers** - `f3c6abd1` (feat)
2. **Task 2: AUDT-01/AUDT-02 behavior proof for AdminController** - `4f6c1f13` (test)

_Task 2 is TDD-flagged in the plan. As with prior plans in this phase, the test suite was written directly against the plan's `<behavior>` bullets (the exhaustive spec, one dedicated test per bullet) rather than committed as a separate failing-test-first step, because it exercises an already-complete Task 1 implementation with no partial state to drive incrementally. All 11 tests were run and passed on first attempt before the commit._

## Files Created/Modified

- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - `AuditoriaRbacService` field, `DiffPermissoesPapel` record, four handlers instrumented
- `backend/src/test/java/com/lexcv/controllers/AdminControllerAuditoriaTest.java` - new, 11 tests proving AUDT-01/AUDT-02 argument shape and refusal silence
- `backend/src/test/java/com/lexcv/controllers/AdminControllerAtribuicaoPapeisEscritorioTest.java` - `@Mock AuditoriaRbacService` + trailing constructor arg
- `backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java` - same
- `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java` - same
- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java` - same
- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java` - same
- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacEscritorioTest.java` - same
- `backend/src/test/java/com/lexcv/controllers/AdminControllerUltimoAdministradorTest.java` - same
- `backend/src/test/java/com/lexcv/controllers/AdminControllerTransacaoTest.java` - same

## Decisions Made

See key-decisions above (unconditional per-role `registarPermissoesAlteradas` call, `DiffPermissoesPapel` record shape, `papeisAntes` snapshot placement).

## Deviations from Plan

None — plan executed as written. The 8 test files' constructor edits were mechanical (import + `@Mock` field + trailing argument), verified individually per file before running the suite.

## Issues Encountered

None. `AdminControllerAuditoriaTest` passed all 11 tests on first run; the full `mvn test` and `spotbugs:check` passed on first run after both task commits.

## Verification Evidence

- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=AdminController*Test` (Task 1 plan-level verification) — **Tests run: 80, Failures: 0, Errors: 0, BUILD SUCCESS** (14+9+14+14+5+11+7+6, unchanged from 128-04's baseline — the eight pre-existing files gained only a mock/constructor argument, no new test methods).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=AdminControllerAuditoriaTest` (Task 2 verification) — **Tests run: 11, Failures: 0, Errors: 0, BUILD SUCCESS** (plan required ≥11).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test` (full suite) — **Tests run: 424, Failures: 0, Errors: 0, BUILD SUCCESS**. Phase state before this plan was 413 (128-CONTEXT.md); 424 = 413 + 11 new tests, confirming no other test was affected.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml spotbugs:check` — **BugInstance size is 0, Error size is 0, BUILD SUCCESS**.
- All grep-based acceptance criteria run individually and matched:
  - Task 1: `grep -F "private final AuditoriaRbacService auditoriaRbacService"` → matches; `grep -c -F "auditoriaRbacService.registarAtribuicoes"` → `3`; `grep -c -F "auditoriaRbacService.registarPermissoesAlteradas"` → `1`; `grep -F "MOTIVO_UTILIZADOR_ELIMINADO"` → matches; all eight test files: `grep -c -F "AuditoriaRbacService"` → `2` each (import + mock field).
  - Task 2: `grep -F "utilizador_eliminado"` in the new test file → matches (in a doc-comment naming the literal motivo string); `grep -F "verifyNoInteractions(auditoriaRbacService)"` → matches (6 call sites + 1 javadoc mention).
- Stub scan of all modified/created files (`TODO`, `FIXME`, "coming soon", "not available", "placeholder") — only case-insensitive false positives (`método`/`metodo` containing "todo"), same pattern already noted in 128-03/128-04 summaries; no real stub markers.
- Privacy invariant (revised Decision 2): `grep -F "getEmail"` against `AuditoriaRbacService.java` and `AdminController.java` finds only the two PRE-EXISTING, unrelated `UserResponse`/`User` field-mapping call sites in `listUsers`/`createUser` (not touched by this plan) — no `getEmail` call was introduced in or near the audit call sites added by this plan.

## User Setup Required

None.

## Threat Flags

None — this plan only adds calls into `AuditoriaRbacService` (Plan 02, already reviewed) from the internals of four already-existing, already-gated endpoints in `AdminController`. No new endpoint, auth path, file-access pattern, or schema change was introduced. The five STRIDE entries in this plan's own threat model (T-128-24 through T-128-28) are all dispositioned `mitigate` and are satisfied by the implementation and test evidence above (rollback-on-failure test, `InOrder` delete-then-audit test, principal-only tenant/actor sourcing carried over unchanged from Plan 04, `verifyNoInteractions` on every refusal path, and the controller passing entities so `AuditoriaRbacService` alone extracts `User.nome` per Plan 02's own privacy test).

## Next Phase Readiness

- AUDT-01 (permission changes) and AUDT-02 (assign/remove/delete-driven removal) are now recorded for `AdminController`'s write surface, alongside `OfficeRolesController` (Plan 03) — Decision 3's write-path coverage is complete except for provisioning (Plan 06, already executed per this session's git history) and the read endpoint (Plan 07, already executed).
- No blockers for any downstream plan in this phase — 128-06 and 128-07 summaries already exist in this worktree's history.

---
*Phase: 128-auditoria-de-atribui-es-de-pap-is*
*Completed: 2026-09-22*
