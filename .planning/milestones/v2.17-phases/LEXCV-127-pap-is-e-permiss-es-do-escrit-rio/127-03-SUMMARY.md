---
phase: 127-pap-is-e-permiss-es-do-escrit-rio
plan: 03
subsystem: auth
tags: [spring-security, rbac, multi-tenant, java, isolation]

# Dependency graph
requires:
  - phase: 127-pap-is-e-permiss-es-do-escrit-rio (plan 02)
    provides: OfficeRbacResponse/OfficeRbacUpdateRequest DTOs, UserRepository.countByTenantRolesId, AdminController class/getRbac gates already authority-based
provides:
  - "GET /api/v1/admin/rbac reads only the caller's own t_tenant_role rows, projecting protegido/podeApagar/utilizadoresAtribuidos server-side from TenantRole.moldeId provenance"
  - "PUT /api/v1/admin/rbac gated by hasAuthority('rbac:manage'), id-keyed, tenant-scoped write with validate-then-write ordering and the administrator floor-lock (PAPEL-08)"
  - "ISOL-03 (Phase 121) retired in writing -- Role/Permission are no longer touched by this handler, replacement comment explains the retirement and instructs restoring the old gate if anyone widens the handler back to global tables"
  - "AdminControllerRbacEscritorioTest -- 11 cases proving multi-tenant isolation (PAPEL-07), provenance-based protection (PAPEL-08), PLATAFORMA_ADMIN exclusion (PAPEL-09), and the live-session effect (PAPEL-03)"
affects: [127-04, 127-05, 127-06, 127-07, 127-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "updateRbac's gate and body changed in the same commit as each other (never in sequence) -- opening the gate before the body stopped writing global Role rows would have reopened the cross-tenant write ISOL-03 closed"
    - "Floor-lock enforced by two independent checks on the same protected role: a superset rule (nothing currently held may be dropped) plus an explicit both-gate-authorities-present check, so the invariant holds even against hand-edited stored state"
    - "Validate-then-write over the whole PUT payload (id resolution -> PLATAFORMA_ADMIN refusal -> unknown-key refusal -> floor-lock), mirroring PlatformAdminController.updateMoldes -- no tenantRoleRepository.save call until every entry in the request has passed every guard"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacEscritorioTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/AdminController.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerAtribuicaoPapeisEscritorioTest.java
  deleted:
    - backend/src/main/java/com/lexcv/dtos/RbacResponse.java

key-decisions:
  - "PLATAFORMA_ADMIN's write access to updateRbac is RETRACTED, not silently skipped: Phase 119's original body used 'continue' to ignore a PLATAFORMA_ADMIN entry while still returning 200; this plan's gate change means PLATAFORMA_ADMIN never reaches the body at all (hasAuthority('rbac:manage') denies it, since the seeded role holds zero permissions), and the defense-in-depth per-entry check inside the body now returns 403 for the same case rather than silently skipping it. Read as a strengthening of PAPEL-09's containment, not a regression -- ported AdminControllerPlataformaAdminContencaoTest Casos 7/8 and AdminControllerRbacAutorizacaoTest's updateRbac scenarios to assert the new (stronger) behavior"
  - "Task 1 (getRbac) and Task 2 (updateRbac) each ported only the test cases their own handler change actually broke -- AdminControllerPlataformaAdminContencaoTest's Caso 6 (getRbac) was ported in the Task 1 commit, Casos 7/8 (updateRbac) were deliberately left on the old Map-based signature until the Task 2 commit that also changed updateRbac's production signature/gate/body. Attempting to port Casos 7/8 alongside Task 1 (as an earlier draft of this plan's Task 1 action text literally requested) breaks compilation, since updateRbac still accepted Map<String,Object> at that point -- verified by actually running the build, not assumed"
  - "Floor-lock's two checks (superset rule, then independent gate-authority check) are both required even though the second usually follows from the first: the second exists specifically to survive stored state that was mutated outside the application (e.g. direct SQL), which the superset rule alone cannot detect if the stored set already lacks rbac:manage/users:manage before the request arrives -- pinned by AdminControllerRbacEscritorioTest Caso 9"

requirements-completed: [CATL-04, PAPEL-01, PAPEL-03, PAPEL-07, PAPEL-08, PAPEL-09]

# Metrics
duration: ~2h10min (three task commits; majority of time spent reading context/interfaces/source files before the first edit, and iterating test fixtures against Mockito's strict-stubbing argument-mismatch behavior)
completed: 2026-09-21
---

# Phase 127 Plan 03: Office-Scoped RBAC Read/Write + Isolation Proof Summary

**`GET/PUT /api/v1/admin/rbac` now operate exclusively over the caller's own `t_tenant_role` rows -- `PUT` gated by `hasAuthority('rbac:manage')` with an id-keyed, validate-then-write body enforcing the administrator floor-lock, and the historic `ISOL-03` gate is retired in writing rather than silently removed, with an 11-case behavioral test proving isolation, provenance-based protection, and the live-session effect.**

## Performance

- **Duration:** ~2h10min active work across three task commits
- **Completed:** 2026-09-21
- **Tasks:** 3/3
- **Files modified:** 8 (1 new test file, 6 modified, 1 deleted)

## Accomplishments
- `getRbac` rewritten to read `tenantRoleRepository.findByTenantId(principal.getTenantId())` instead of `roleRepository.findAll()` -- tenant-scoped exactly like `listUsers`, never a global `Role` read. `protegido`/`podeApagar`/`utilizadoresAtribuidos` are computed server-side from `TenantRole.moldeId` provenance against the global `"ADMIN"` `Role` id, resolved once per request before the projection loop, never from `TenantRole.nome`.
- `RbacResponse` (name-keyed, global DTO) deleted; the response retargeted to `OfficeRbacResponse` (id-keyed, tenant-scoped, landed by plan 02). `TenantRoleRepository` injected into `AdminController`'s constructor, rippling through all 5 test files that construct the controller positionally.
- `updateRbac`'s gate changed from `hasRole('PLATAFORMA_ADMIN')` to `hasAuthority('rbac:manage')` and its body rewritten in the same commit to accept `OfficeRbacUpdateRequest` (id-keyed) and write only `TenantRole` rows of the caller's tenant. Validate-then-write ordering: unknown submitted id -> 404, PLATAFORMA_ADMIN role (by raw name, `ROLE_`-prefixed name, or `moldeId` provenance) -> 403, unknown permission key -> 400 -- all before any `tenantRoleRepository.save` call.
- Floor-lock (PAPEL-08): for the role whose `moldeId` matches the ADMIN molde, the submitted permission set must be a superset of what it currently holds (409 naming any removed key), and must independently still contain `rbac:manage`/`users:manage` regardless of the superset check (409 naming whichever is missing) -- the invariant that keeps plan 02's authority gates reachable even against hand-edited stored state.
- The `ISOL-03` (Phase 121) comment replaced -- not deleted -- with a comment explaining Phase 121's original reasoning (global `Role`/`Permission`, no `tenant_id`), why Phases 125/126 closed that gap, and an explicit instruction that anyone widening this handler back to global tables must restore the old gate in the same edit.
- New `AdminControllerRbacEscritorioTest` (11 test methods): office-scoped read (PAPEL-01), provenance-based `protegido`/`podeApagar` against a role literally named `"ADMIN"` with no provenance (PAPEL-08), live assignment count, `PLATAFORMA_ADMIN` exclusion by both name and provenance (PAPEL-09), write isolation with a real second tenant proven unread and unchanged (PAPEL-07, Decisão 5), floor-lock in both directions plus the independent gate-authority check, unknown-key refusal, and the live-session effect (PAPEL-03) proven by feeding the exact `TenantRole` instance captured from `save` into a real `ResolucaoPapeisService.resolverPermissoesEfectivas` call.
- Full backend suite green at 331 tests (318 baseline + 13 net new). `mvn spotbugs:check` clean. No schema change (`backend/migrations/` untouched).

## Task Commits

Each task was committed atomically:

1. **Task 1: GET /admin/rbac returns the office's own roles** - `4b148cff` (feat)
2. **Task 2: PUT /admin/rbac writes the office's own roles, under rbac:manage, with the floor-lock rule** - `9da8cb92` (feat)
3. **Task 3: Prove isolation, the floor-lock, and live-session effect** - `d3cc7889` (test)

**Plan metadata:** (this commit, following the summary)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - `getRbac` and `updateRbac` rewritten tenant-scoped; `TenantRoleRepository` injected; `ISOL-03` comment replaced
- `backend/src/main/java/com/lexcv/dtos/RbacResponse.java` - Deleted (superseded by `OfficeRbacResponse`, landed plan 02)
- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java` - Retargeted to `OfficeRbacResponse`; permission-catalogue assertions ported unchanged in behavior; new PLATAFORMA_ADMIN-provenance exclusion case added
- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java` - `getRbac` scenarios switched to real-principal authentication + `tenantRoleRepository` stubbing; `updateRbac` scenarios rewritten for the new gate/body (raw `rbac:manage` succeeds, `ROLE_ADMIN`/`ROLE_PLATAFORMA_ADMIN`/`ROLE_rbac:manage` all denied); annotation-reflection test updated to the new method signature and gate value
- `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java` - Caso 6 ported to `OfficeRbacResponse` (Task 1); Casos 7/8 ported to `OfficeRbacUpdateRequest`, Caso 7 now asserting 403 refusal instead of silent skip (Task 2); all 14 PAPEL-09 containment cases preserved, none weakened
- `backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java` - Constructor ripple only (`TenantRoleRepository` added)
- `backend/src/test/java/com/lexcv/controllers/AdminControllerAtribuicaoPapeisEscritorioTest.java` - Constructor ripple only (`tenantRoleRepository` now also passed directly into `AdminController`, not just into the real `ResolucaoPapeisService`)
- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacEscritorioTest.java` - New, 11 cases (isolation, provenance, floor-lock x3, live-session effect)

## Decisions Made
- Followed the plan's interface contract exactly for `getRbac`'s projection fields and `updateRbac`'s validate-then-write ordering.
- Split the porting of `AdminControllerPlataformaAdminContencaoTest` across Task 1 (Caso 6, getRbac) and Task 2 (Casos 7/8, updateRbac) rather than all at once in Task 1 as an earlier draft of the Task 1 action text suggested -- verified by running the build that porting Casos 7/8 in Task 1 breaks compilation, since `updateRbac` still accepted `Map<String,Object>` at that point. This is the correct reading of the plan's own critical invariant ("gate and body change in the same task, never in sequence") applied consistently to the test file as well as the production handler.
- `updateRbac`'s refusal of a `PLATAFORMA_ADMIN` entry is now an explicit 403 rather than the old silent `continue`-and-200 -- a strengthening of PAPEL-09's containment (fail loud instead of fail silent), not a regression. Documented explicitly in the ported test comments so a future reader does not mistake the status-code change for a defect.
- Both floor-lock checks (superset rule + independent gate-authority check) kept as separate, independently-triggerable guards per the plan's explicit instruction, with a dedicated test (Caso 9) proving the second check fires even when the first would not catch the violation.

## Deviations from Plan

None requiring a rule. Two plan-defects observed and worked around without loosening any criterion (see below); both are the same class of issue already documented in 127-01-SUMMARY.md ("a grep-based acceptance criterion overcounts against a comment, not a real code reference").

### Plan-defect observations (not deviations, no rule needed)

1. **Task 1's dangling-`RbacResponse`-reference criterion overcounts on prose comments.** The criterion `grep -R -F -c 'RbacResponse' backend/src/main backend/src/test | grep -v 'OfficeRbacResponse' | grep -v ':0$'` is meant to return nothing, but `grep -c` outputs `path:count` lines -- `grep -v 'OfficeRbacResponse'` filters those *count lines*, not the underlying matches, so it never actually removes lines where the real matches were all `OfficeRbacResponse` occurrences. Read literally, the pipeline still surfaces `AdminController.java:9`, two test files at `:4`/`:12`. Verified directly (via the Grep tool, not the naive count) that every one of those lines is either an `import`/usage of `OfficeRbacResponse` (which contains "RbacResponse" as a substring) or a doc-comment sentence explaining what the type *used to be* ("...retargetado de `RbacResponse` (nome-keyed, global) para `OfficeRbacResponse`..."). Zero actual code references to the deleted `RbacResponse` type remain -- confirmed by the fact that `mvn -DskipTests package` compiles clean after the file was deleted.
2. **The `roleRepository.findAll()` criterion (Task 1) and the `hasAuthority('rbac:manage')` count criterion (Task 2) both hit a local tool issue, not a plan defect.** The project's `rtk` grep-hook wrapper returned false negatives/zeros for both `grep -F -c` invocations run via the Bash tool (already documented in this project's memory as `project_rtk_grep_false_negative_complex_pattern`) -- re-running the identical patterns against `/usr/bin/grep` directly, and independently via the Grep tool, gave the correct counts (`roleRepository.findAll()`: 1 occurrence, inside a comment explaining the change, zero real call sites; `hasAuthority('rbac:manage')`: 4 occurrences, 2 of them the actual `@PreAuthorize` annotations, satisfying "at least 2"). Documented here per the instruction to verify the real invariant rather than trust a contradicting/broken gate.

## Issues Encountered

- **Mockito strict-stubbing `PotentialStubbingProblem` on `roleRepository.findByNome`.** Both `getRbac` and `updateRbac` unconditionally call `roleRepository.findByNome("ADMIN")` and `roleRepository.findByNome("PLATAFORMA_ADMIN")` once per request (to resolve the two molde ids used for provenance checks). Several new/ported tests initially stubbed only one of the two names; Mockito's strict-stubbing mode raised `PotentialStubbingProblem` on the other, unstubbed name because *a* stub existed for that method with different arguments. Fixed by either stubbing both names explicitly (when either provenance value matters to the test) or stubbing neither (letting Mockito's built-in `Optional.empty()` default apply to both) -- never stubbing exactly one. Caught by actually running the tests, not assumed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `GET/PUT /api/v1/admin/rbac` are now fully office-scoped, id-keyed, and floor-locked -- plans 04+ (role create/rename/delete CRUD, frontend `RbacTab` rewrite) can build directly on this contract without further backend changes to these two handlers.
- `ISOL-03`'s retirement is documented in the code itself, not just in planning artifacts -- a future reader (or reviewer) has the full history without needing to dig through `121-REVIEW.md`.
- The floor-lock and isolation invariants are now pinned by tests that fail if either regresses; `AdminControllerRbacEscritorioTest` is the reference file for any future plan touching these two handlers.
- Full backend suite green at 331 tests. `mvn spotbugs:check` clean. No schema change, no new dependency. Testcontainers ITs were not run locally (known Docker npipe blocker, unrelated to this plan -- no IT tests were added or touched by this plan).
- No blockers for subsequent plans in this phase.

---
*Phase: 127-pap-is-e-permiss-es-do-escrit-rio*
*Completed: 2026-09-21*

## Self-Check: PASSED

All key files verified present on disk (AdminController.java, AdminControllerRbacCatalogoTest.java,
AdminControllerRbacAutorizacaoTest.java, AdminControllerPlataformaAdminContencaoTest.java,
AdminControllerLimiteUtilizadoresTest.java, AdminControllerAtribuicaoPapeisEscritorioTest.java,
AdminControllerRbacEscritorioTest.java, this SUMMARY.md); RbacResponse.java confirmed absent.
All three task commits (`4b148cff`, `9da8cb92`, `d3cc7889`) verified present in
`git log --oneline --all`.
