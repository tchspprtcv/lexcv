---
phase: 117-backend-limite-de-utilizadores-por-tenant
plan: 02
subsystem: api
tags: [spring-boot, spring-security, mockito, multi-tenant, rbac, java]

# Dependency graph
requires:
  - phase: 117-01
    provides: "Tenant.plano/limiteUtilizadores persisted fields, UserRepository.countByTenantIdAndAtivoTrue"
provides:
  - "POST /api/v1/admin/users returns 409 CONFLICT with {\"message\": \"Limite de utilizadores atingido para o vosso plano.\"} when the caller's tenant active-user count >= limiteUtilizadores"
  - "AdminController.createUser gains a TenantRepository dependency (5th @RequiredArgsConstructor param)"
  - "AdminControllerLimiteUtilizadoresTest — 4-case Mockito proof of the limit's full behavior contract"
affects: [118-frontend-limite-de-utilizadores, 120-tenant-console, 122-relatorio-utilizacao]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pre-save 409 CONFLICT business-rule check (Map.of(\"message\", ...)) inserted immediately before .save(), same response shape as every other 409 site in ResourceController"
    - "TenantRepository injected into a second controller (AdminController) via @RequiredArgsConstructor, mirroring the existing AuthController wiring"
    - "RED/GREEN split into two atomic commits (test(...) then feat(...)) for a task-level tdd=\"true\" task, even though the plan's own type is execute (not tdd) — preserves an accurate compile-failing-then-passing history"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/AdminController.java

key-decisions:
  - "RED (failing test) and GREEN (implementation) committed as two separate atomic commits rather than one, so git history shows the test genuinely failing to compile against the pre-existing 4-arg AdminController constructor before the fix lands"
  - "Verification block placed immediately after the roles.isEmpty() check, before the unrelated permsList/permissions assembly block — satisfies CTX-05's 'after all format validations, before save()' contract at the earliest valid point rather than the latest"
  - "Tenant lookup miss (tenantRepository.findById returns empty) does not block creation — matches CONTEXT.md's explicit 'se o tenant não for encontrado, não bloquear' instruction, avoiding a new failure mode for an edge case outside this phase's scope"

requirements-completed: [PLAN-02, PLAN-04]

# Metrics
duration: ~12min
completed: 2026-07-29
---

# Phase 117 Plan 02: Enforcement do Limite de Utilizadores em AdminController Summary

**`POST /api/v1/admin/users` now returns `409 CONFLICT` once a tenant's live `ativo=true` user count reaches `Tenant.limiteUtilizadores`, proven first by 4 failing Mockito tests (RED) then made to pass by a 5-line pre-save check reusing Plan 01's `countByTenantIdAndAtivoTrue` (GREEN) — zero regressions across the 88-test backend suite.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-07-29T01:15:19Z (approx, immediately after 117-01's metadata commit)
- **Completed:** 2026-07-29T01:27:00Z (approx)
- **Tasks:** 2
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- `AdminControllerLimiteUtilizadoresTest` (new, 4 `@Test` methods) proves all 4 required behaviors: 409 at the limit with the exact literal message and zero `save()` calls; 201 below the limit with exactly one `save()` call; `limiteUtilizadores == null` returns 201 while never even calling `countByTenantIdAndAtivoTrue`; and a chained count (`3L, 2L`) across two consecutive calls on the same controller instance proves the count is read live per-request, never cached — CONFLICT then CREATED, exactly matching PLAN-04's "deactivating frees a slot immediately" requirement
- `AdminController.createUser` gained a `TenantRepository` dependency (inserted after `passwordEncoder`, preserving the constructor-argument order the test depends on) and a 5-line business-rule block: looks up the caller's own tenant via `principal.getTenantId()` (never a body field), and when `limiteUtilizadores` is non-null, compares it against a live `countByTenantIdAndAtivoTrue` call, returning `409` with `Map.of("message", "Limite de utilizadores atingido para o vosso plano.")` when the count already meets the limit
- Full backend regression suite reconfirmed green: 88/88 tests (84 pre-existing + 4 new), `mvn spotbugs:check` clean (0 findings), `mvn -DskipTests package` exit 0
- All 4 STRIDE mitigations from the plan's threat model (T-117-04/05/06/08) independently re-verified by origin-based grep against the final code, not just by re-reading the plan — see "Threat Mitigation Verdicts" below

## Task Commits

Each task was committed atomically. Task 1 (`tdd="true"`) is split into its RED and GREEN halves as two separate commits, per the TDD execution flow:

1. **Task 1 (RED): failing test for user-limit enforcement** - `b300f4f` (test) — 4-case test file added; confirmed failing to *compile* (`mvn test -Dtest=AdminControllerLimiteUtilizadoresTest` → constructor arity mismatch) before any implementation existed
2. **Task 1 (GREEN): enforce active-user limit per tenant in createUser** - `c2525a8` (feat) — `TenantRepository` dependency + limit check added; same test command now green (4/4, 0 failures, 0 errors)
3. **Task 2: regression + security gate** - no commit (verification-only; all gates passed cleanly on the first run, nothing required fixing — see Deviations)

**Plan metadata:** commit to follow (this SUMMARY + STATE.md/ROADMAP.md/REQUIREMENTS.md)

## Files Created/Modified
- `backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java` - New, 4 Mockito test cases (409-at-limit, 201-below-limit, null-limit-bypass, live-recount-after-deactivation), following the `ResourceControllerUploadDocumentoTest` direct-instantiation-with-mocks convention (no MockMvc/`@SpringBootTest` in this codebase)
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - Added `TenantRepository tenantRepository` field (5th `@RequiredArgsConstructor` param) and the pre-save limit-check block in `createUser`, between the `roles.isEmpty()` validation and the `permsList` assembly

## Decisions Made
- Split Task 1 into two atomic commits (RED test-only, then GREEN implementation-only) instead of one combined commit — makes the compile-failure-then-pass gate sequence checkable directly from `git log`/`git show`, consistent with how `type: tdd` plans are validated elsewhere in this workflow, even though this plan's own frontmatter type is `execute`
- Inserted the limit-check block directly after `roles.isEmpty()` (the last check with an early `return`) rather than after the unrelated `permsList`/`permissions` assembly that sits between it and `User.builder()` — both positions satisfy CTX-05's "after all format validations, before save()" instruction, but the earlier point keeps the business-rule check adjacent to the other validation early-returns it conceptually belongs with
- Tenant Optional resolved with `.orElse(null)` and an explicit `tenant != null` guard (matching the plan's own illustrative composition in 117-PATTERNS.md) rather than `Optional#ifPresent`, since the method needs to fall through to the rest of `createUser` on both "no tenant" and "no limit" cases

## Deviations from Plan

None — plan executed exactly as written. Both tasks' acceptance criteria passed on the first attempt: no auto-fixes, no missing functionality discovered, no blockers, no architectural changes, and Task 2 required zero code changes (full suite, SpotBugs, and package build were all clean on the first run, so the task's own "condicional" file list — `AdminController.java`/the test file, only if a fix was needed — was correctly left untouched).

## Threat Mitigation Verdicts (Task 2)

Per the plan's explicit instruction to record a verdict per mitigation (not a generic checklist), each of the 4 `mitigate`-disposition threats from the plan's `<threat_model>` was re-verified against the committed code via origin-based grep, independent of the plan's own text:

- **T-117-04 (Elevation of Privilege — tenant id origin): CONFIRMADO.** `grep -c 'body.get("tenant_id")\|body.get("tenantId")\|containsKey("tenant_id")'` against `AdminController.java` returns `0`. The only tenant id used anywhere in the limit check is `principal.getTenantId()`, obtained from `SecurityContextHolder` before the check runs; the pre-existing `User.builder().tenantId(principal.getTenantId())` call is unchanged.
- **T-117-05 (Information Disclosure — 409 body): CONFIRMADO.** The literal message `Limite de utilizadores atingido para o vosso plano.` appears exactly once; a grep for concatenation/`String.format` patterns around that message returns `0`. No limit value, no current count, and no other tenant's data is ever interpolated into the response.
- **T-117-06 (Tampering — cross-tenant count): CONFIRMADO.** `countByTenantIdAndAtivoTrue(principal.getTenantId())` appears exactly once, and the zero-argument form `countByTenantIdAndAtivoTrue()` appears zero times — the tenant predicate is structurally required at every call site. The chained-count test (Case 4) additionally proves this at runtime: both invocations in that test use the same `TENANT_ID`.
- **T-117-08 (Spoofing — endpoint authorization): CONFIRMADO.** `hasRole('ADMIN')` still appears exactly once in the file (class-level `@PreAuthorize`), and `git diff` on `AdminController.java` shows zero changes to that annotation line or to `listUsers`/`updateUser`/`deleteUser`/`getRbac`/`updateRbac`. No new `@PreAuthorize` annotation was added to any method.

(T-117-07 and T-117-09 are `accept`-disposition per the plan's threat register — no code mitigation was required or attempted for either; T-117-SC is `n/a` and independently reconfirmed here: `git diff --stat -- backend/pom.xml` is empty, no dependency was added.)

## Issues Encountered
- A first attempt to capture `mvn test`'s full output via `mvn test 2>&1 | tee <logfile> | tail -5` produced a truncated log (113 lines, missing the final `BUILD SUCCESS`/`Tests run` summary) — not a test failure, just an incomplete capture from that particular pipe combination in this shell. Re-ran as a plain `mvn test > <logfile> 2>&1; echo EXIT_CODE=$?` (no pipe), which captured the complete 88-test run cleanly (`EXIT_CODE=0`). Consistent with the environment note about the global `rtk` hook affecting piped Bash output — used the dedicated `Grep` tool for all pattern/count verification throughout this plan, as instructed, and plain-redirect-then-read for the full-suite/SpotBugs/package runs instead of piping through `tail`.

## User Setup Required

None — no external service configuration required, no new environment variables, no new migration script (this plan only changes application code and adds a test; the schema was already migrated by 117-01).

## Next Phase Readiness

- The backend contract Plan 118 (frontend "X/Y utilizadores" indicator) needs is now live: `POST /api/v1/admin/users` returns `409` with a stable, parseable message when at capacity, and the existing `GET /api/v1/admin/users` (unchanged) plus `Tenant.limiteUtilizadores` (from 117-01) give the frontend everything needed to compute and display "X/Y" without any further backend work.
- Backend compiles clean, full test suite green (88/88), SpotBugs/FindSecBugs clean (0 findings), package artifact builds successfully.
- No blockers. Phase 117 (backend) is functionally complete — both plans (117-01 data layer, 117-02 enforcement) delivered with zero deviations.

---
*Phase: 117-backend-limite-de-utilizadores-por-tenant*
*Completed: 2026-07-29*

## Self-Check: PASSED

All created/modified files confirmed present on disk:
- FOUND: backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java
- FOUND: backend/src/main/java/com/lexcv/controllers/AdminController.java
- FOUND: .planning/phases/LEXCV-117-backend-limite-de-utilizadores-por-tenant/117-02-SUMMARY.md

All task commits confirmed in git log:
- FOUND: b300f4f (Task 1, RED)
- FOUND: c2525a8 (Task 1, GREEN)

Plan-level verification re-confirmed: `mvn test` 88/88 passing (BUILD SUCCESS), `mvn spotbugs:check` 0 findings (BUILD SUCCESS), `mvn -DskipTests package` exit 0. All 4 origin-based mitigation grep gates (T-117-04/05/06/08) independently re-verified in this session.
