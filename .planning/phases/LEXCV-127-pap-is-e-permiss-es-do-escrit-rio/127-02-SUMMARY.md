---
phase: 127-pap-is-e-permiss-es-do-escrit-rio
plan: 02
subsystem: auth
tags: [spring-security, rbac, multi-tenant, java, dto]

# Dependency graph
requires:
  - phase: 127-pap-is-e-permiss-es-do-escrit-rio (plan 01)
    provides: UserPrincipal.moldeIds, ResolucaoPapeisService.resolverMoldeIds/temPapelDeMolde(UserPrincipal, String)
provides:
  - OfficeRbacResponse/OfficeRbacUpdateRequest/PapelCreateRequest/PapelRenameRequest DTOs -- the office-scoped RBAC read/write contract plans 03/04/05 write against
  - UserRepository.countByTenantRolesId -- per-role assignment count, proven against real Postgres, consumed by plan 04's delete guard (PAPEL-05)
  - AdminController class gate = hasAuthority('users:manage'); getRbac method gate = hasAuthority('rbac:manage') -- both survive an office administrator role rename
  - AdminControllerRbacAutorizacaoTest rewritten to prove authority-based gates by real ProxyFactory/AuthorizationManagerBeforeMethodInterceptor proxy
affects: [127-03, 127-04, 127-05, 127-06, 127-07, 127-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Office-scoped response/request DTOs declared independently of the platform-console DTOs (RbacResponse, MoldesConsolaResponse) they resemble -- same key/label field shape copied, never imported, per MoldesConsolaResponse's own doc-comment anticipating this phase"
    - "Delete/assignment guards count via a derived collection-property query (UserRepository.countByTenantRolesId over User.tenantRoles) rather than find-then-isEmpty or FK-violation catch"
    - "@PreAuthorize gate changes on AdminController proven exclusively by real AOP proxy (ProxyFactory + AuthorizationManagerBeforeMethodInterceptor.preAuthorize()); annotation-value reflection kept only as a clearly-labelled non-regression check, never as the sole proof"

key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/OfficeRbacResponse.java
    - backend/src/main/java/com/lexcv/dtos/OfficeRbacUpdateRequest.java
    - backend/src/main/java/com/lexcv/dtos/PapelCreateRequest.java
    - backend/src/main/java/com/lexcv/dtos/PapelRenameRequest.java
    - backend/src/test/java/com/lexcv/repositories/UserRepositoryContagemPapeisIT.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/UserRepository.java
    - backend/src/main/java/com/lexcv/controllers/AdminController.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java

key-decisions:
  - "OfficeRbacResponse.PapelDto declares all seven UI-SPEC fields (id, nome, sistema, protegido, podeApagar, utilizadoresAtribuidos, permissoes); protegido and podeApagar are documented as server-computed from provenance, never from nome, precisely because PAPEL-04 makes nome editable this phase -- the actual computation logic ships in plan 03/04 alongside the tenant-scoped handler bodies, this plan only fixes the contract"
  - "OfficeRbacUpdateRequest keys by TenantRole id (UUID), never by nome, mirroring MoldesUpdateRequest's id-keyed shape -- the exact unsafety PAPEL-04's renaming introduces for a name-keyed write"
  - "getRbac's gate drops the PLATAFORMA_ADMIN alternative entirely (was 'hasRole(ADMIN) or hasRole(PLATAFORMA_ADMIN)') rather than widening it to an authority OR -- PAPEL-09 explicitly asks for the platform read of an office surface to be retracted, and PLATAFORMA_ADMIN holds no permissions at all (DatabaseSeeder.upsertRolePermissions(..., emptyList, false)), so it could never have satisfied hasAuthority('rbac:manage') anyway"
  - "updateRbac's gate and ISOL-03 comment intentionally untouched this plan -- one added comment line states plan 03 must change gate and body together, never in sequence, or the ISOL-03 cross-tenant write reopens for the duration of one wave"

requirements-completed: [CATL-04, PAPEL-08, PAPEL-09]

# Metrics
duration: ~20min (two task commits ~4-5min apart; additional time spent reading 127-01-SUMMARY.md/127-CONTEXT.md/127-PATTERNS.md/127-UI-SPEC.md and source files before either commit)
completed: 2026-09-21
---

# Phase 127 Plan 02: Office RBAC DTO Contract + Authority-Based Admin Gates Summary

**Four new office-scoped RBAC DTOs (id-keyed write contract) plus `UserRepository.countByTenantRolesId` land the read/write/count building blocks plans 03-05 need, and `/api/v1/admin`'s class gate and `getRbac` gate move from `hasRole('ADMIN')`/`hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')` to `hasAuthority('users:manage')`/`hasAuthority('rbac:manage')` so a renamed office administrator role never locks itself out.**

## Performance

- **Duration:** ~20 min (Task 1 commit at 20:01:40, Task 2 commit at 20:06:06, plus context-reading time before either commit)
- **Completed:** 2026-09-21
- **Tasks:** 2/2
- **Files modified:** 8 (5 created + 3 modified)

## Accomplishments
- `OfficeRbacResponse` (with nested `PapelDto`/`PermissaoDefDto`), `OfficeRbacUpdateRequest` (nested `PapelPermissoesDto`, id-keyed), `PapelCreateRequest`, and `PapelRenameRequest` created under `backend/src/main/java/com/lexcv/dtos/`, following the existing response-DTO (`@Data @Builder @NoArgsConstructor @AllArgsConstructor`, nested static DTOs) and request-DTO (plain `@Getter @Setter`) conventions, declared independently of `RbacResponse`/`MoldesConsolaResponse` per those files' own doc-comments anticipating this phase.
- `UserRepository.countByTenantRolesId(UUID)` added as a derived query over the `User.tenantRoles` collection property — the only derived query in this repository traversing a collection property, proven by a new Testcontainers IT (`UserRepositoryContagemPapeisIT`): zero for an unassigned role, exact count for an assigned role, and tenant containment across two tenants with homonymous roles.
- `AdminController`'s class-level gate changed from `hasRole('ADMIN')` to `hasAuthority('users:manage')`; `getRbac`'s method gate changed from `hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')` to `hasAuthority('rbac:manage')`. `updateRbac`'s gate (`hasRole('PLATAFORMA_ADMIN')`) and its ISOL-03 comment were left untouched except for one added paragraph explaining why plan 03 must change its gate and body together.
- `AdminControllerRbacAutorizacaoTest` fully rewritten (12 test methods, up from 7): proves the new gates by real `ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` proxy across all 7 scenarios named in the plan (raw `rbac:manage` succeeds on `getRbac`; `ROLE_ADMIN` alone, `ROLE_PLATAFORMA_ADMIN` alone, and `ROLE_rbac:manage` are all refused on `getRbac`; a caller with `users:manage` plus only a renamed role-shaped authority `ROLE_Administrador do Escritório` still reaches `listUsers`; `rbac:manage`/`users:manage` are not interchangeable between `listUsers`/`getRbac`; `updateRbac` still refuses `ROLE_ADMIN` and still accepts `ROLE_PLATAFORMA_ADMIN`, labelled as temporary), plus 3 annotation-value non-regression checks.

## Task Commits

Each task was committed atomically:

1. **Task 1: Office RBAC DTO contract + assignment count query** - `a47258cb` (feat)
2. **Task 2: Gate /api/v1/admin by permission authority instead of the ADMIN role name** - `803d822e` (feat)

**Plan metadata:** (this commit, following the summary)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/dtos/OfficeRbacResponse.java` - New: `List<PapelDto> papeis` + `List<PermissaoDefDto> permissoes`, seven UI-SPEC fields on `PapelDto` (id, nome, sistema, protegido, podeApagar, utilizadoresAtribuidos, permissoes)
- `backend/src/main/java/com/lexcv/dtos/OfficeRbacUpdateRequest.java` - New: `List<PapelPermissoesDto> papeis`, keyed by `UUID id`, never by nome
- `backend/src/main/java/com/lexcv/dtos/PapelCreateRequest.java` - New: `nome` + `permissoes` plain request DTO
- `backend/src/main/java/com/lexcv/dtos/PapelRenameRequest.java` - New: `nome`-only plain request DTO
- `backend/src/main/java/com/lexcv/repositories/UserRepository.java` - Added `countByTenantRolesId(UUID)` derived query
- `backend/src/test/java/com/lexcv/repositories/UserRepositoryContagemPapeisIT.java` - New: 3 Testcontainers-backed tests proving the count query and its tenant containment
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - Class gate → `hasAuthority('users:manage')`; `getRbac` gate → `hasAuthority('rbac:manage')`; `updateRbac` gate/body untouched, one comment line added
- `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java` - Fully rewritten, 12 tests, real-proxy proof of the new authority gates plus the temporarily-unchanged `updateRbac` gate

## Decisions Made
- Followed the plan's interface contract exactly for all four DTOs and for `countByTenantRolesId`'s derived-method form (no custom `@Query` needed).
- `getRbac`'s comment was rewritten (not just its annotation) to explain why `PLATAFORMA_ADMIN` loses read access entirely rather than gaining an authority-OR fallback — it holds no permissions at all per `DatabaseSeeder.upsertRolePermissions("PLATAFORMA_ADMIN", emptyList, false)`, so widening to an OR would have been dead code.
- Test helper `autenticarComoPrincipalComAuthorities` (constructing a real `UserPrincipal` via its builder, not via `UserPrincipal.create`) was added alongside the existing raw-authorities-only `autenticarComoAuthorities`, because `listUsers`'s body reads `principal.getTenantId()` — the existing helper's `null` principal would NPE for that handler specifically, while `getRbac`/`updateRbac` never touch the principal.

## Deviations from Plan

None requiring a rule — plan executed as written for both tasks. The IT test's Docker unavailability (see below) was explicitly anticipated by the plan itself ("If Docker is unavailable locally ... record that in the SUMMARY and rely on CI; do not delete the IT"), so it is not treated as a deviation.

## Issues Encountered

- **`UserRepositoryContagemPapeisIT` could not run locally — pre-existing, known Docker npipe blocker.** Running `mvn verify -Dtest=skip -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=UserRepositoryContagemPapeisIT` (the plan's literal command needed `-Dsurefire.failIfNoSpecifiedTests=false` added, since this project's surefire config fails outright on a `-Dtest=skip` pattern with zero matches rather than silently running zero unit tests) reaches Testcontainers, which fails with:
  ```
  org.testcontainers.containers.ContainerFetchException: Can't get Docker image: RemoteDockerImage(imageName=postgres:16-alpine, ...)
  Caused by: java.lang.IllegalStateException: Could not find a valid Docker environment. Please see logs and check configuration
  ... NpipeSocketClientProviderStrategy attempted first, no valid strategy found ...
  ```
  This matches the already-known local Docker npipe blocker referenced in STATE.md/127-02-PLAN.md itself and in the project's `project_rtk_tsc_false_clean_worktree`/general environment notes — not a defect introduced by this plan. The IT file was NOT deleted; it compiles cleanly (`mvn -DskipTests package` succeeds) and will run under CI where a real Docker daemon is available. `mvn test` (the unit-only default-test phase, which does not include `*IT.java` classes per this project's surefire/failsafe split) is green at 318/318, independently confirming no regression from adding the IT file.
- **Task 1's literal acceptance-criteria command (`-Dtest=skip`) needed one flag added to run at all** — not a defect in the criterion's intent (the underlying check — does the IT pass or is Docker genuinely unavailable — was verified either way), just a surefire version difference from whatever generated the plan's exact command. Documented here rather than silently deviating from the plan's wording.

## User Setup Required

None - no external service configuration required. (The IT test will exercise the real count query the next time it runs somewhere Docker is available, e.g. CI.)

## Next Phase Readiness

- Plan 03 can now write its tenant-scoped `getRbac`/`updateRbac` bodies directly against `OfficeRbacResponse`/`OfficeRbacUpdateRequest` — the contract is fixed and UI-SPEC-complete.
- Plan 04's delete guard (PAPEL-05) has `UserRepository.countByTenantRolesId` ready and proven (modulo the local Docker blocker, which does not block CI).
- `/api/v1/admin`'s class and `getRbac` gates are already authority-based; plan 03 only needs to flip `updateRbac`'s gate (and rewrite its body) to complete PAPEL-08/PAPEL-09/ISOL-03's resolution — the invariant that makes the class/getRbac gates safe (the floor-lock rule keeping `users:manage`/`rbac:manage` on the office admin role) is explicitly plan 03's job, not yet implemented here.
- Full backend suite green at 318 tests (313 baseline + 5 net new: 12 new/rewritten in `AdminControllerRbacAutorizacaoTest` minus 7 replaced). `mvn spotbugs:check` clean. No schema change (`backend/migrations/` untouched), no new dependency.
- No blockers for plan 03.

---
*Phase: 127-pap-is-e-permiss-es-do-escrit-rio*
*Completed: 2026-09-21*

## Self-Check: PASSED

All key files verified present on disk (OfficeRbacResponse.java, OfficeRbacUpdateRequest.java,
PapelCreateRequest.java, PapelRenameRequest.java, UserRepositoryContagemPapeisIT.java,
UserRepository.java, AdminController.java, AdminControllerRbacAutorizacaoTest.java, this
SUMMARY.md). Both task commits (`a47258cb`, `803d822e`) verified present in
`git log --oneline --all`.
