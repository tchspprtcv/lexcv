---
phase: 128-auditoria-de-atribui-es-de-pap-is
plan: 06
subsystem: services
tags: [spring-transactions, rbac, audit-log, setup, tdd]

# Dependency graph
requires:
  - phase: 128-02
    provides: "AuditoriaRbacService.registarAtribuicoes and MOTIVO_PROVISIONAMENTO"
provides:
  - "SetupService.provisionTenant records the founding administrator's role assignment as the office's first audit event, in the same @Transactional boundary"
  - "Explicit code-level record of why initializeSystem and MigracaoPapeisEscritorioService stay unaudited"
affects: [128-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Discretionary Decision 4 item resolved YES: an existing @Transactional write path gets an audit call added directly in its body (no new transaction boundary needed) because Propagation.MANDATORY on the audit-service side already enforces same-transaction semantics"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/services/SetupService.java
    - backend/src/test/java/com/lexcv/services/SetupServiceAtribuicaoAdminFundadorTest.java
    - backend/src/test/java/com/lexcv/services/SetupServiceInstanciacaoMoldesTest.java
    - backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java

key-decisions:
  - "registarAtribuicoes is called unconditionally at the end of provisionTenant (never guarded by an 'was a TenantRole actually assigned' check) -- when no ADMIN molde is instantiated, adminUser.getTenantRoles() is the same empty HashSet it always defaults to, so the call passes antes=Set.of() and depois=Set.of(); AuditoriaRbacService's own empty-diff no-op (proven in 128-02) is what prevents any row from being written, not a conditional in SetupService. This keeps SetupService from re-implementing a diff-emptiness check that already lives in exactly one place."
  - "The provisionTenant Javadoc addition describing the new call deliberately avoids writing the literal call-chain text 'auditoriaRbacService.registarAtribuicoes(...)' -- an early draft did, and it self-tripped the plan's own acceptance-criteria grep (`grep -c -F \"auditoriaRbacService.registarAtribuicoes\"` expects exactly 1, the real call, not 2 including a doc mention). Caught by running the plan's own acceptance criteria before committing, not assumed correct -- same category of self-referential trap as 128-02's getEmail() case."

requirements-completed: [AUDT-02]

# Metrics
duration: ~20min
completed: 2026-09-22
---

# Phase 128 Plan 06: Audit Office Provisioning in SetupService Summary

**`SetupService.provisionTenant` now calls `AuditoriaRbacService.registarAtribuicoes` in its existing `@Transactional` boundary right after the founding admin's `TenantRole` is set, recording the office's first audit event with `autor null` (never the invoking `PLATAFORMA_ADMIN`) and `motivo "provisionamento"`; `initializeSystem` and the Phase 126 boot conversion stay silent, with the reasoning now written into `SetupService`'s own Javadoc/comments.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-22T15:23:00Z (approx, first file read)
- **Completed:** 2026-09-22T15:29:56-01:00 (task commit)
- **Tasks:** 1
- **Files modified:** 4

## Accomplishments

- Added `private final AuditoriaRbacService auditoriaRbacService;` as the last field of `SetupService` (`@RequiredArgsConstructor` extends the generated constructor to 7 args).
- `provisionTenant` calls `auditoriaRbacService.registarAtribuicoes(tenant.getId(), null, adminUser, Set.of(), adminUser.getTenantRoles(), AuditoriaRbacService.MOTIVO_PROVISIONAMENTO)` immediately after `instanciarMoldesEAtribuirAdminFundador` — inside the method's own `@Transactional` boundary, so `Propagation.MANDATORY` on the audit-service side makes the event and the tenant/user rows commit or roll back together (proven structurally: the call sits before `return tenant`, with no intervening commit point, and `instanciarMoldes`'s existing failure-propagation test (`SetupServiceInstanciacaoMoldesTest` Caso 6) already proves a mid-method exception unwinds the whole transaction).
- `initializeSystem` and `instanciarMoldesEAtribuirAdminFundador` are untouched by the audit call — verified both by `grep` (no `auditoriaRbacService` token inside `initializeSystem`'s body) and by a new Mockito `verifyNoInteractions(auditoriaRbacService)` assertion on the existing `initializeSystem` test case in `SetupServiceAtribuicaoAdminFundadorTest`.
- `MigracaoPapeisEscritorioService.java` has a zero-line `git diff --stat` — confirmed untouched, per Decision 4.
- Added Portuguese doc comments explaining all four required points: Decision 4's discretionary item decided YES (transactional cost was low); actor recorded as `null` rather than the platform admin, to avoid a cross-tenant identity leak (T-128-29); `initializeSystem` unaudited because it has no authenticated actor, same category as the Phase 126 conversion; `MigracaoPapeisEscritorioService` untouched on purpose.
- Two new behavioral test cases in `SetupServiceProvisionTenantTest` (Caso 10/11): ADMIN-molde-present asserts `registarAtribuicoes` called once with the new tenant id, `autor=null`, the founding admin as target, `antes=Set.of()`, and `depois` containing exactly the instantiated ADMIN `TenantRole`; no-ADMIN-molde asserts the same call happens but with `depois=Set.of()` too (the actual no-write behavior for an empty diff is AuditoriaRbacService's own responsibility, already proven in 128-02).
- One new assertion in `SetupServiceAtribuicaoAdminFundadorTest`'s existing `initializeSystem` case proving zero interaction with `auditoriaRbacService`.
- All three `SetupService` test files' `new SetupService(...)` constructor calls updated for the new 7th collaborator (`@Mock AuditoriaRbacService`).

## Task Commits

Each task was committed atomically:

1. **Task 1: Audit the founding-administrator assignment in provisionTenant** - `7acd545d` (feat)

_Note: This plan's single task is `tdd="true"`, but — like 128-02's tasks — it was implemented directly against a complete spec (the plan's `<behavior>` bullets) with tests written and run to green before the commit, rather than as a separate failing-test-first commit. No untested code was ever committed._

## Files Created/Modified

- `backend/src/main/java/com/lexcv/services/SetupService.java` - new `auditoriaRbacService` field; `provisionTenant` records the founding-admin assignment in-transaction; doc comments on both `provisionTenant` and `initializeSystem` explain what's audited and why
- `backend/src/test/java/com/lexcv/services/SetupServiceAtribuicaoAdminFundadorTest.java` - constructor updated for the new collaborator; `verifyNoInteractions(auditoriaRbacService)` added to the existing `initializeSystem` case
- `backend/src/test/java/com/lexcv/services/SetupServiceInstanciacaoMoldesTest.java` - constructor updated for the new collaborator (no new assertions needed — this file's scope is molde instantiation, not auditing)
- `backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java` - constructor updated for the new collaborator; two new test cases (Caso 10/11) proving the audited-provisioning behavior in both the with-ADMIN-molde and without-ADMIN-molde paths

## Decisions Made

- `registarAtribuicoes` is called unconditionally at the end of `provisionTenant`, relying on `AuditoriaRbacService`'s own empty-diff no-op rather than adding a second "was anything actually assigned" check in `SetupService` — see key-decisions above.
- Reworded the `provisionTenant` Javadoc to avoid literally spelling `auditoriaRbacService.registarAtribuicoes(...)`, after that phrasing self-tripped the plan's own `grep -c -F` acceptance gate (which expects the count to be exactly 1, the real call site) — see key-decisions above.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Javadoc self-tripped its own grep acceptance gate**
- **Found during:** Task 1, acceptance-criteria verification
- **Issue:** The first draft of the new Javadoc paragraph on `provisionTenant` wrote `{@code auditoriaRbacService.registarAtribuicoes(...)}` to describe the new call. The plan's own acceptance criterion (`grep -c -F "auditoriaRbacService.registarAtribuicoes"` must equal 1) is a literal string count over the whole file, so the doc comment made the count 2 instead of 1.
- **Fix:** Reworded the sentence to describe the call without spelling the literal `object.method(` text (`"chamando o método de registo de atribuições de {@link AuditoriaRbacService}"`), matching the actual call site's identifier count back down to 1.
- **Files modified:** `backend/src/main/java/com/lexcv/services/SetupService.java`
- **Commit:** `7acd545d` (fixed before this commit was made; no separate commit needed)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Cosmetic wording fix only; no behavior or test changed. Caught by running the plan's own verification commands before committing, not assumed correct.

## Issues Encountered

None beyond the one auto-fixed issue above.

## Verification Evidence

- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=SetupService*Test` — **Tests run: 23, Failures: 0, Errors: 0** (4 + 6 + 13 across the three files; 2 more than before this plan, matching the 2 new Caso 10/11 cases).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=SetupService*Test,AuditoriaRbacServiceTest` (plan-level combined verification) — **BUILD SUCCESS**.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test` (full suite) — **Tests run: 400, Failures: 0, Errors: 0, BUILD SUCCESS**. Baseline stated in the plan was 398; 400 = 398 + 2 new tests, confirming no other test was affected.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml spotbugs:check` — clean (exit 0, no findings).
- All acceptance-criteria greps run individually and matched:
  - `grep -F "MOTIVO_PROVISIONAMENTO" SetupService.java` → matches (the real call argument).
  - `grep -c -F "auditoriaRbacService.registarAtribuicoes" SetupService.java` → `1` (exactly the real call site, after the Javadoc fix above).
  - `grep -F "initializeSystem" SetupService.java` → matches (method definition + cross-references); method body read and confirmed no audit call inside it.
  - `git diff --stat -- backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java` → empty (untouched).
- No other file in the codebase constructs `SetupService` directly outside the three updated test files (`grep "new SetupService("` across `backend/` returns exactly those three).
- Stub scan of the modified files (`TODO`, `FIXME`, "coming soon", "not available", "placeholder") — no matches.

## User Setup Required

None.

## Next Phase Readiness

- Decision 4's discretionary item is now fully resolved and documented in code: provisioning IS audited, the wizard and boot conversion are NOT, both with an explicit written rationale a future editor can find at the call sites.
- `SetupService` now depends on `AuditoriaRbacService` — any future constructor change to either class needs to keep the three `SetupService*Test` files' `new SetupService(...)` calls in sync (documented in each file's class Javadoc).
- No blockers for plan 07 (the paginated RBAC-audit query endpoint) — this plan's new event uses the same `entidade_tipo`/`acao`/`detalhe` shape already produced by `AuditoriaRbacService.registarAtribuicoes` for the office-surface write paths, so plan 07's query/read path needs no special-casing for provisioning events.

---
*Phase: 128-auditoria-de-atribui-es-de-pap-is*
*Completed: 2026-09-22*
