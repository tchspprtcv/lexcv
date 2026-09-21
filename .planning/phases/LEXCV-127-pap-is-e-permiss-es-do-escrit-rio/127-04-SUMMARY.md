---
phase: 127-pap-is-e-permiss-es-do-escrit-rio
plan: 04
subsystem: auth
tags: [spring-security, rbac, multi-tenant, java, crud]

# Dependency graph
requires:
  - phase: 127-pap-is-e-permiss-es-do-escrit-rio (plan 02)
    provides: PapelCreateRequest/PapelRenameRequest DTOs, UserRepository.countByTenantRolesId
  - phase: 127-pap-is-e-permiss-es-do-escrit-rio (plan 03)
    provides: tenant-scoped GET/PUT /admin/rbac, TenantRole provenance-based protegido/podeApagar projection
provides:
  - "New OfficeRolesController at /api/v1/admin/rbac/roles -- class-level hasAuthority('rbac:manage') gate, structurally distinct from AdminController's users:manage class gate"
  - "POST creates a tenant-scoped role (moldeId null, sistema false), preserving submitted case, refusing reserved names/duplicate names/unknown permission keys"
  - "PUT renames any of the caller's own roles including the protected administrator role, touching only nome"
  - "DELETE refuses by cross-tenant 404, by ADMIN-molde provenance (never by name), and by a live assignment count, before deleting"
  - "OfficeRolesControllerTest -- 15 cases proving the authority gate on all three verbs by real proxy, per-tenant isolation, case preservation, and both delete refusals including the renamed-protected-role case"
affects: [127-05, 127-06, 127-07, 127-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "A CRUD surface gets its own class with a class-level @PreAuthorize rather than living inside a controller whose class gate differs from what the new handlers need -- a method-level annotation SUBSTITUTES the class one rather than combining with it, so relying on class-level coverage removes the 'someone forgot the method annotation' failure mode entirely"
    - "Undeletable/protected-role discrimination is always by TenantRole.moldeId against the global molde's id, never by TenantRole.nome -- this phase's own PAPEL-04 makes the name editable, so a name-based guard would silently stop protecting the first time an office renames the role"
    - "Delete guards run cheapest-to-most-expensive and validate-before-write: cross-tenant 404 (a single comparison) before provenance (two repository reads) before the assignment count (one query) before any mutation"

key-files:
  created:
    - backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java
    - backend/src/test/java/com/lexcv/controllers/OfficeRolesControllerTest.java
  modified: []

key-decisions:
  - "Followed the plan's explicit routing choice (/api/v1/admin/rbac/roles as a NEW controller) even though PapelCreateRequest/PapelRenameRequest's own javadoc (written in plan 02) documents a different path (/api/v1/admin/rbac/papeis) -- see Plan-defect observations below; the plan's own <objective> and Task 1 action text are unambiguous about both the path and the reason (structural safety against AdminController's users:manage class gate), so the plan's instruction took precedence over a stale doc-comment in a file this plan does not touch"
  - "renameRole is allowed on the protected administrator role without any special-case branch -- the only guard is the standard per-tenant duplicate-name check applied to every rename; PAPEL-08 forbids deleting/stripping permissions from this role, never renaming it, and the handler's doc-comment states this explicitly so a future reader doesn't 'fix' it into a new restriction"
  - "deleteRole checks provenance for BOTH the office's own ADMIN molde and the PLATAFORMA_ADMIN molde (plus the two reserved-name forms) as defense in depth, even though a PLATAFORMA_ADMIN-provenance TenantRole can never reach this endpoint in practice (it's already excluded from the tenant-scoped read that populates the UI) -- mirrors the same triple-guard discipline AdminController.getRbac/updateRbac already apply"

requirements-completed: [PAPEL-02, PAPEL-04, PAPEL-05, PAPEL-07, PAPEL-08, PAPEL-09]

# Metrics
duration: ~50min (three task commits; majority of time spent reading plan/context/patterns/interfaces and existing AdminController/PlatformAdminController/test precedents before the first edit)
completed: 2026-09-21
---

# Phase 127 Plan 04: Office Role CRUD (Create/Rename/Delete) Summary

**New `OfficeRolesController` at `/api/v1/admin/rbac/roles`, gated at the class level by `hasAuthority('rbac:manage')`, gives an office administrator create/rename/delete for their own roles -- delete refuses by a live assignment count and, independently, by ADMIN-molde provenance (never by name, since this same phase makes the name editable).**

## Performance

- **Duration:** ~50 min active work across three task commits
- **Completed:** 2026-09-21
- **Tasks:** 3/3
- **Files modified:** 2 (both new)

## Accomplishments
- `OfficeRolesController` created as a **separate class** from `AdminController` -- the doc-comment states explicitly why: since plan 02, `AdminController`'s class gate is `hasAuthority('users:manage')`, and a method-level `@PreAuthorize` SUBSTITUTES (never adds to) a class-level one, so a role-CRUD handler added there would silently inherit the wrong authority if anyone ever forgot the method annotation. A dedicated class-level `hasAuthority('rbac:manage')` gate cannot be lost this way -- every handler in the class is automatically covered.
- `POST ""` (PAPEL-02): validates/normalizes the submitted name (trim, no case transform -- office roles are named freely, unlike platform moldes), rejects both forms of the reserved `PLATAFORMA_ADMIN` name case-insensitively, rejects a per-tenant duplicate (pre-check + `DataIntegrityViolationException` backstop), resolves every permission key against `findAllByReservadaPlataformaFalse()` (400 "Permissão desconhecida" on the first miss), and builds the `TenantRole` with `moldeId(null)`/`sistema(false)` -- a role created from scratch can never be provenance-protected.
- `PUT "/{id}"` (PAPEL-04): loads by id then compares `tenantId` to the principal's (404 if absent/foreign), validates/normalizes the name the same way, rejects a duplicate held by a different id, and touches only `nome` -- explicitly allowed on the protected administrator role, since PAPEL-08 only forbids deleting/stripping it, not renaming it.
- `DELETE "/{id}"` (PAPEL-05/PAPEL-08/PAPEL-09): ordered cheapest-to-most-expensive, nothing deleted before every check passes -- (1) cross-tenant id → 404, never 403, closing an id-probe channel; (2) ADMIN-molde provenance → 409, discriminated by `moldeId` against the global "ADMIN" role's id, **never** by `nome.equals("ADMIN")`, with a comment explaining this is the exact reason provenance replaced name comparisons across this phase; PLATAFORMA_ADMIN provenance/name also refused as defense in depth; (3) `UserRepository.countByTenantRolesId(id) > 0` → 409 naming the exact count, never a `findBy...isEmpty()` list load or a caught FK violation; (4) `deleteById` + 204.
- New `OfficeRolesControllerTest` (15 test methods): the class-level authority gate proven by real `ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor` on all three verbs (including the `hasAuthority`-vs-`hasRole` trap -- `ROLE_rbac:manage` still denied), create writes landing in the caller's own tenant with `moldeId` null/`sistema` false, case preservation (`"Recepção"` saved verbatim), reserved-name refusal in all three forms/cases, unknown-permission-key refusal, per-tenant-only duplicate refusal, rename of the protected role leaving `permissions`/`moldeId`/`sistema` untouched, cross-tenant 404 on rename and delete (with the delete case additionally proving the assignment count is never even queried), the assigned-role delete refusal naming the count, and the renamed-protected-role delete refusal (`"Administradores"` with the ADMIN molde id) -- the case that fails if the provenance guard is ever rewritten as a name comparison.
- Full backend suite green at 346 tests (331 baseline + 15 net new). `mvn spotbugs:check` clean. No schema change (`backend/migrations/` untouched).

## Task Commits

Each task was committed atomically:

1. **Task 1: OfficeRolesController with create and rename** - `2a0cc828` (feat)
2. **Task 2: DELETE with the count guard and the provenance guard** - `bdab7e7b` (feat)
3. **Task 3: Prove the gate, the isolation and both delete refusals** - `b9958a7a` (test)

**Plan metadata:** (this commit, following the summary)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java` - New: `POST`/`PUT`/`DELETE` for the office's own roles under a class-level `hasAuthority('rbac:manage')` gate
- `backend/src/test/java/com/lexcv/controllers/OfficeRolesControllerTest.java` - New: 15 cases, real-proxy gate proof plus both delete refusals and cross-tenant refusals

## Decisions Made
- Followed the plan's explicit interface contract for the controller's path, gate, guard ordering, and error messages exactly as written.
- Split Task 1/Task 2 across two commits by writing the full controller once, then reverting the file to a create+rename-only version for the Task 1 commit and re-adding the delete handler for the Task 2 commit -- the plan's per-task atomic-commit requirement outranked the convenience of writing the whole class in one pass.
- Test fixtures for `tenantRoleRepository.save(any())` needed to synthesize a generated `id` when the captured `TenantRole` didn't already have one (Hibernate would assign one on a real insert) -- without it, `Map.of("id", ..., "nome", ...)` in the handler's response body throws `NullPointerException` on a `null` id, since `Map.of` rejects null values. Fixed with a small `simularGravacaoComIdGerado` test helper, not a change to the handler (the handler's behavior is correct; only the test's mock needed to model Hibernate's real behavior).

## Deviations from Plan

None requiring a rule. One plan-defect observation, same class already documented in 127-02/127-03-SUMMARY.md ("a stale cross-reference in a file this plan doesn't modify, not a functional inconsistency").

### Plan-defect observations (not deviations, no rule needed)

1. **`PapelCreateRequest`/`PapelRenameRequest`'s own javadoc (written by plan 02) documents a different path than this plan specifies.** `PapelCreateRequest.java:9` says `POST /api/v1/admin/rbac/papeis` and `PapelRenameRequest.java:7` says `PUT /api/v1/admin/rbac/papeis/{id}/nome`; `UserRepository.java:41`'s comment (also plan 02) says `DELETE /admin/rbac/papeis/{id}`. This plan's own `<objective>` and Task 1 `<action>` are explicit and unambiguous about the actual routing: a **new** controller `OfficeRolesController` mapped at `/api/v1/admin/rbac/roles`, with `POST ""`/`PUT "/{id}"`/`DELETE "/{id}"` (no `/nome` suffix on rename, no `/papeis` segment) -- and gives the structural reason (avoiding `AdminController`'s `users:manage` class gate). Followed the plan's explicit instruction, since it is authoritative for this plan's own deliverable and the three DTO/repository doc-comments are describing a stale draft from an earlier plan, not a contract this plan is bound by. `OfficeRolesController.java`'s own class-level javadoc documents the actual final path and DOES cross-reference `AdminController`'s `/api/v1/admin/rbac` mapping so the two are legible together. Did not edit the three stale doc-comments (out of this plan's `files_modified` scope), consistent with the precedent set in 127-03-SUMMARY.md for out-of-scope doc drift.

## Issues Encountered

- **Test `Map.of` NullPointerException on a `null` generated id.** Three of the new create-path tests initially stubbed `tenantRoleRepository.save(any())` to return the argument unchanged, but the handler's response body (`Map.of("id", papelGravado.getId(), "nome", ...)`) NPEs when `getId()` is `null` (`Map.of` does not accept `null` values) -- a real Hibernate save would have populated the id via `GenerationType.UUID`, but the mocked repository doesn't. Fixed by adding a shared `simularGravacaoComIdGerado` helper that assigns a random id if one isn't already present before returning from the stub, mirroring what the real repository does. Caught by actually running the tests, not assumed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The office role CRUD surface (create/rename/delete) is complete and independently gated from `AdminController`'s user-management and matrix-editing handlers -- plans 05+ (frontend `RbacTab` rewrite, `verify-bloqueio-rbac.mjs` gate rewrite) can wire directly against `POST/PUT/DELETE /api/v1/admin/rbac/roles` without further backend changes to this controller.
- `OfficeRolesControllerTest` is the reference file for any future plan touching create/rename/delete of office roles -- it pins the provenance-not-name discipline for the protected role, the count-not-catch discipline for the assignment guard, and the case-preservation contract the rename/create dialogs will rely on.
- Full backend suite green at 346 tests. `mvn spotbugs:check` clean. No schema change, no new dependency. Testcontainers ITs were not run locally (known Docker npipe blocker, unrelated to this plan -- no IT tests were added or touched by this plan).
- No blockers for subsequent plans in this phase.

---
*Phase: 127-pap-is-e-permiss-es-do-escrit-rio*
*Completed: 2026-09-21*

## Self-Check: PASSED

All key files verified present on disk (OfficeRolesController.java, OfficeRolesControllerTest.java,
this SUMMARY.md). All three task commits (`2a0cc828`, `bdab7e7b`, `b9958a7a`) verified present in
`git log --oneline --all`.
