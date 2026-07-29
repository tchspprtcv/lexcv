---
phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen
plan: 02
subsystem: auth
tags: [multi-tenant, provisioning, spring-boot, mockito, tdd, dto]

# Dependency graph
requires: []
provides:
  - "SetupService.provisionTenant(SetupInitializeRequest) -- creates a new Tenant + its initial ADMIN User and returns the saved Tenant (with id), reusing validateRequest/normalizeLogo as-is, with zero SystemSettingRepository interaction (repeatable, unlike initializeSystem)"
  - "TenantProvisionResponse DTO (id + nome only) -- the 201 response shape Plan 04's PlatformAdminController will build from provisionTenant's return value"
  - "9 Mockito test cases (SetupServiceProvisionTenantTest) proving creation, zero-SystemSetting interaction, shared validation messages, duplicate-email rejection, missing-ADMIN-role IllegalStateException, repeatability, and initializeSystem's continued singleton gate"
affects: [119-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "New service method added directly beside its closest same-class analog (initializeSystem), reusing private helpers (validateRequest/normalizeLogo) instead of duplicating validation logic"
    - "TDD RED/GREEN committed as two separate atomic commits (test then feat), matching the convention already established in Phases 117/118/119-01"

key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/TenantProvisionResponse.java
    - backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java
  modified:
    - backend/src/main/java/com/lexcv/services/SetupService.java

key-decisions:
  - "provisionTenant placed immediately after initializeSystem and before validateRequest in SetupService.java, exactly as PATTERNS.md prescribed -- keeps the two sibling creation paths visually adjacent while validateRequest stays the shared private helper both call"
  - "TenantProvisionResponse's class Javadoc deliberately avoids spelling out the excluded field names (email/logoDataUrl/plano/limiteUtilizadores/createdAt) as literal words, describing the exclusion generically instead -- the plan's own informal acceptance-criteria grep pattern (grep -cE 'email|logoDataUrl|...' with no 'private .*;' wrapper) would have false-positived on a comment listing those words, even though the stricter automated <verify> gate (which requires the 'private .* (word);' shape) would not have"

patterns-established:
  - "Sibling service methods sharing validation: a new method reuses an existing private validateRequest/helper without widening its visibility, proven by an explicit test asserting the exact shared exception messages"

requirements-completed: [PROV-06]

# Metrics
duration: ~11min
completed: 2026-07-29
---

# Phase 119 Plan 02: SetupService.provisionTenant Summary

**New `SetupService.provisionTenant` method creates a Tenant + initial ADMIN user and returns the saved Tenant, reusing `initializeSystem`'s shared validation but with zero `SystemSetting` singleton-gate interaction, proven by 9 Mockito TDD cases plus a purpose-built `TenantProvisionResponse` DTO.**

## Performance

- **Duration:** ~11 min (commit span 07:42:49Z -> 07:47:35Z ~5 min; total includes upfront reading of PLAN/CONTEXT/PATTERNS/PROJECT/STATE and post-commit gate verification)
- **Started:** 2026-07-29T07:37:31Z (estimated, immediately after 119-01 completion)
- **Completed:** 2026-07-29T07:48:32Z
- **Tasks:** 2 (Task 2 executed as TDD: RED + GREEN commits)
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments
- `TenantProvisionResponse` DTO created with exactly 2 fields (`id`, `nome`), following the `TenantPublicInfoResponse`/`UserResponse` four-annotation Lombok convention (`@Data @Builder @NoArgsConstructor @AllArgsConstructor`) -- zero sensitive or superfluous fields leaked into the contract
- `SetupService.provisionTenant(SetupInitializeRequest)` added directly after `initializeSystem`: validates via the existing private `validateRequest`, rejects a globally-duplicate email, looks up role `"ADMIN"` (never `"PLATAFORMA_ADMIN"`), creates+saves `Tenant` then `User`, and returns the saved `Tenant` (with `id` populated) -- the one deliberate signature divergence from `initializeSystem` (`void`), required because Plan 04's controller needs the id/nome for its 201 body
- Zero references to `systemSettingRepository` inside `provisionTenant`'s body (confirmed by extracting the method and grepping in isolation) -- the method never reads or writes the `/setup` singleton gate, so it is repeatable (proven by Caso 6: two calls create two distinct tenants)
- `initializeSystem`, `isInitialized`, `validateRequest`, `normalizeLogo`, `isBlank` left byte-for-byte unchanged (confirmed via `git diff`: pure +45/-0 insertion) -- `initializeSystem`'s own singleton gate re-proven by a dedicated non-regression test (Caso 7)
- New test class `SetupServiceProvisionTenantTest` (9 cases: Caso 3 split into 3 sub-tests for nome/email/password) proves every `<behavior>` requirement from the plan, including the security-critical `verifyNoInteractions(systemSettingRepository)` assertion (Caso 2) and the duplicate-email-before-persist ordering (Caso 4, `verify(tenantRepository, never()).save(any())`)

## Task Commits

Each task was committed atomically:

1. **Task 1: DTO TenantProvisionResponse** - `86b0370` (feat)
2. **Task 2 (TDD, RED): failing tests for SetupService.provisionTenant** - `efb378f` (test)
3. **Task 2 (TDD, GREEN): implement SetupService.provisionTenant** - `f3d233e` (feat)

_Note: Task 2 has two commits per this codebase's established TDD convention (RED then GREEN, e.g. Phases 117/118/119-01) -- RED was confirmed as a genuine compile failure (`cannot find symbol: method provisionTenant`) across all 9 test methods before GREEN was written._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/dtos/TenantProvisionResponse.java` (new) - response DTO for `POST /api/v1/platform/tenants` (Plan 04); `id` + `nome` only
- `backend/src/main/java/com/lexcv/services/SetupService.java` - adds `provisionTenant`, a sibling of `initializeSystem` minus the `SystemSetting` gate, returning the saved `Tenant`
- `backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java` (new) - 9 Mockito test cases (`@ExtendWith(MockitoExtension.class)`, service instantiated directly via its `@RequiredArgsConstructor`-generated constructor, no Spring context)

## Decisions Made
- Followed `119-PATTERNS.md`'s exact placement instruction: `provisionTenant` sits between `initializeSystem` and the private `validateRequest`/`normalizeLogo`/`isBlank` helpers, so both public creation paths read as siblings sharing the same private helpers below them.
- Rewrote `TenantProvisionResponse`'s Javadoc mid-task after discovering its first draft (which enumerated the excluded field names in prose: "Nunca inclui email, logoDataUrl, plano, limiteUtilizadores, createdAt...") would false-positive against the plan's informal acceptance-criteria grep pattern (`grep -cE 'email|logoDataUrl|passwordHash|plano|limiteUtilizadores|createdAt'`, with no `private .*;` wrapper) even though the actual automated `<verify>` gate -- which requires the stricter `private .* (word);` field-declaration shape -- would not have flagged it. Rewrote to describe the exclusion generically instead of naming the fields, removing the ambiguity entirely rather than relying on the two checks disagreeing in my favor.
- Instantiated `SetupService` directly via `new SetupService(systemSettingRepository, tenantRepository, userRepository, roleRepository, passwordEncoder)` in `@BeforeEach` (matching the exact constructor-argument order documented in the plan's `<interfaces>` block) rather than `@InjectMocks`, for an explicit, self-documenting constructor call that doubles as a check that the collaborator order hasn't silently changed.

## Deviations from Plan

None - plan executed exactly as written. (The Javadoc wording adjustment above was a same-task refinement before the first commit, not a deviation from the plan's instructions -- the plan never specified exact Javadoc wording, only that it explain the DTO's purpose and the never-leak-raw-entity discipline, which the final wording still does.)

## Issues Encountered

None. All verification commands (compile, targeted test run, isolated method-body extraction via `sed` + `Grep`, full suite) passed on the first attempt after implementation.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 04 (Wave 2, `depends_on: [119-02]`) can now build `PlatformAdminController.POST /api/v1/platform/tenants` directly against `setupService.provisionTenant(request)` and `TenantProvisionResponse.builder().id(...).nome(...).build()` -- both already proven to satisfy the "never touch `SystemSetting`" and "never leak raw entity" contracts this plan's threat model assigned to them (T-119-08, T-119-14, T-119-15, T-119-16, T-119-17, T-119-18).
- Plan 03 (Wave 1, `AdminController` containment guards) has no file overlap with this plan and was unaffected.
- `SetupController.java` and `backend/pom.xml` confirmed untouched (`git diff --name-only` empty for both) -- Success Criterion 3 ("nunca reaproveita o endpoint público de /setup") holds structurally, not just behaviorally.
- Full backend test suite: 111/111 tests green (including this plan's 9 new cases), 0 regressions, `BUILD SUCCESS`.

---
*Phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/dtos/TenantProvisionResponse.java`
- FOUND: `backend/src/main/java/com/lexcv/services/SetupService.java`
- FOUND: `backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java`
- FOUND: `.planning/phases/LEXCV-119-backend-papel-de-administrador-de-plataforma-e-provisionamen/119-02-SUMMARY.md`
- FOUND commit: `86b0370`
- FOUND commit: `efb378f`
- FOUND commit: `f3d233e`
