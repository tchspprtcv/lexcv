---
phase: 120-frontend-consola-de-administra-o-de-tenants
plan: 02
subsystem: api
tags: [spring-boot, rest-api, multi-tenant, rbac, jpa, mockito, dto]

# Dependency graph
requires:
  - phase: 120-01
    provides: "Tenant.ativo field (Boolean, NOT NULL DEFAULT TRUE) and the 3-path suspension enforcement (JwtAuthenticationFilter/login/refresh) that PATCH /tenants/{id}/ativo now toggles"
  - phase: 119-04
    provides: "PlatformAdminController with its class-level @PreAuthorize(hasRole('PLATAFORMA_ADMIN')) gate, the existing createTenant endpoint, and the AccessDeniedException -> 403 GlobalExceptionHandler mapping this plan's new gate tests rely on"
provides:
  - "GET /api/v1/platform/tenants -- all tenants with nome/plano/limiteUtilizadores/ativo/utilizadoresAtivos, sorted by nome (case-insensitive)"
  - "PUT /api/v1/platform/tenants/{id} -- adjusts plano/limiteUtilizadores (null = sem limite), never touches ativo"
  - "PATCH /api/v1/platform/tenants/{id}/ativo -- toggles suspended/active, rejects suspending the reserved LexCV tenant with 400"
  - "TenantAdminSummaryResponse / TenantUpdateRequest DTOs, reusable as-is by Phase 122's usage report"
  - "GlobalExceptionHandler now maps HttpMessageNotReadableException -> 400 globally (backend-wide, not just this plan's endpoints)"
affects: [120-03, 120-04, 120-05, 120-06, 122]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared private toSummary(Tenant) helper reused by all 3 handlers to build TenantAdminSummaryResponse identically (including the active-user count lookup)"
    - "HttpMessageNotReadableException -> 400 handler extends the existing GlobalExceptionHandler @RestControllerAdvice convention (same shape as the Phase 119 AccessDeniedException -> 403 handler)"

key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java
    - backend/src/main/java/com/lexcv/dtos/TenantUpdateRequest.java
  modified:
    - backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java
    - backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
    - backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java

key-decisions:
  - "toSummary(Tenant) extracted as a shared private helper so listTenants/updateTenant/setTenantAtivo all build TenantAdminSummaryResponse the same way, with utilizadoresAtivos always sourced from UserRepository.countByTenantIdAndAtivoTrue"
  - "Reserved-tenant guard blocks ativo=false only -- reactivating LexCV is always allowed, since suspending it is the only unsafe direction (would lock out the sole PLATAFORMA_ADMIN)"
  - "Reworded 2 pre-existing Javadoc passages (PlatformAdminController's class doc, PlatformAdminControllerTest's class doc) that collided with this same plan's own literal-text verify gates, without changing their documented meaning -- same precedent as 119-04/120-01"

requirements-completed: [PROV-03, PROV-04, PROV-05]

# Metrics
duration: 22min
completed: 2026-07-29
---

# Phase 120 Plan 02: PlatformAdminController List/Adjust/Suspend Endpoints Summary

**3 new PLATAFORMA_ADMIN-gated endpoints (list with utilization, adjust plano/limite, suspend/reactivate) added to the existing PlatformAdminController, plus a global 400 handler for malformed JSON bodies and a reserved-tenant suspend guard proven by dedicated tests**

## Performance

- **Duration:** 22 min
- **Started:** 2026-07-29T12:14:37Z
- **Completed:** 2026-07-29T12:36:11Z
- **Tasks:** 3 completed
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments

- 3 new endpoints on `PlatformAdminController`: `GET /tenants` (list with active-user utilization), `PUT /tenants/{id}` (adjust `plano`/`limiteUtilizadores`), `PATCH /tenants/{id}/ativo` (suspend/reactivate) -- all covered by the pre-existing class-level `hasRole('PLATAFORMA_ADMIN')` gate, zero new per-method `@PreAuthorize`
- New global `HttpMessageNotReadableException` -> `400` handler in `GlobalExceptionHandler`, fixing a pre-existing `500`-with-exception-class-name response for ANY malformed JSON body across the whole backend, not just this plan's new endpoints -- same `{"message": ...}` shape as every other error response
- The reserved `LexCV` tenant cannot be suspended (`400` with the exact UI-SPEC message) but CAN always be reactivated -- both directions proven by dedicated tests
- 17 new Mockito test cases (13 behavior + 3 real AOP-proxy authorization-gate cases + 1 structural reflection case) bring `PlatformAdminControllerTest` from 9 to 26 tests; full backend suite 165/165 green; `mvn spotbugs:check` clean

## Task Commits

Each task was committed atomically:

1. **Task 1: DTOs de contrato e handler global para corpo JSON inválido** - `3e33d16` (feat)
2. **Task 2: 3 endpoints novos em PlatformAdminController com guarda da tenant reservada** - `0c276de` (feat)
3. **Task 3: Cobertura de teste dos 3 endpoints, incluindo o gate de autorização de cada um** - `3af7211` (test)

**Plan metadata:** (recorded in the next commit, after this SUMMARY)

## Files Created/Modified

- `backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java` - 6-field projection DTO (`id`, `nome`, `plano`, `limiteUtilizadores`, `ativo`, `utilizadoresAtivos`) for the list/adjust/suspend responses; deliberately excludes `logoDataUrl`/`nif`/`email`/`telefone`/`createdAt`
- `backend/src/main/java/com/lexcv/dtos/TenantUpdateRequest.java` - typed `PUT` body (`TenantPlano plano`, nullable `Integer limiteUtilizadores`), no `ativo` field
- `backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java` - new `@ExceptionHandler(HttpMessageNotReadableException.class)` -> `400`, the 4 pre-existing handlers untouched
- `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` - `listTenants`/`updateTenant`/`setTenantAtivo` handlers, `TENANT_RESERVADO` constant, shared `toSummary` helper, 2 new injected repositories
- `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` - 17 new tests (13 Grupo A behavior, 3 Grupo B authorization gate, 1 structural reflection), `novoController()` updated for the 3-arg constructor

## Decisions Made

- `toSummary(Tenant)` extracted as a single shared private helper so the 3 handlers never diverge on how `utilizadoresAtivos` is computed (always `userRepository.countByTenantIdAndAtivoTrue(tenant.getId())`, never a second implementation).
- The reserved-tenant guard in `setTenantAtivo` only blocks the `ativo=false` transition; reactivating a reserved tenant is always allowed, matching the plan's explicit rationale (suspending `LexCV` would lock out the only `PLATAFORMA_ADMIN`, with no application-level recovery path).
- `plano` is required (not-null) in `TenantUpdateRequest`, and `limiteUtilizadores` accepts `null` as "sem limite" but rejects any non-null value `< 1` -- both validated explicitly in `updateTenant`, distinct from the Jackson-level enum rejection an invalid `plano` string already triggers.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Pre-existing class Javadoc text collided with Task 2's own automated verify gate**
- **Found during:** Task 2 (`PlatformAdminController` implementation)
- **Issue:** The pre-existing class Javadoc (written in Phase 119) spelled out the literal text "SecurityContextHolder/UserPrincipal" to explain that the controller never reads the security context. Task 2's own automated `<verify>` requires `grep -c 'SecurityContextHolder'` against this exact file to equal `0` -- left unchanged, the pre-existing sentence would have failed this plan's own gate.
- **Fix:** Reworded the sentence to convey the identical property ("nenhum handler lê o contexto de segurança nem o principal autenticado do chamador") without using the literal class names. The documented guarantee (no handler reads the security context) is unchanged; only the wording avoiding the two literal identifiers changed.
- **Files modified:** `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java`
- **Verification:** `grep -c 'SecurityContextHolder'` and `grep -c 'UserPrincipal'` both return `0` on the file.
- **Committed in:** `0c276de` (Task 2 commit)

**2. [Rule 1 - Bug] Pre-existing test-class Javadoc text collided with Task 3's own automated verify gate**
- **Found during:** Task 3 (`PlatformAdminControllerTest` extension)
- **Issue:** Two pre-existing sentences in the test class's Javadoc (Phase 119) stated no MockMvc/SpringBootTest-style harness exists in this project, spelling out those two literal class names. Task 3's acceptance criteria requires `grep -c '@SpringBootTest\|MockMvc\|lenient()'` against this file to equal `0`.
- **Fix:** Reworded both sentences to convey the same fact ("não existe nenhum harness de contexto Spring nem de simulação de pedidos HTTP") without the literal class names.
- **Files modified:** `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java`
- **Verification:** grep count is `0`; all 26 tests still pass after the reword.
- **Committed in:** `3af7211` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1, both pre-existing comment-wording collisions with this plan's own literal-text verification gates -- the same class of issue documented in `119-04-SUMMARY.md` and `120-01-SUMMARY.md`).
**Impact on plan:** Cosmetic only -- no functional/behavioral code was changed by either fix, both are Javadoc rewording that preserves the exact documented property. No scope creep.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plans 120-03 through 120-06 (frontend console: route, nav item, list/create/edit/suspend UI) can now consume `GET`/`PUT`/`PATCH` `/api/v1/platform/tenants{, /{id}, /{id}/ativo}` directly, exactly as scoped in `120-CONTEXT.md` and `120-PATTERNS.md`.
- Phase 122 (usage report, UTIL-01) can reuse `TenantAdminSummaryResponse` and the `listTenants` endpoint as-is per this plan's own doc-comments -- no additional backend work anticipated for its read side.
- Full backend suite: 165/165 tests green, 0 regressions. `mvn spotbugs:check`: 0 findings.
- This is the last backend plan of Phase 120 -- from 120-03 onward, only the frontend console is built on top of this contract.

---
*Phase: 120-frontend-consola-de-administra-o-de-tenants*
*Completed: 2026-07-29*

## Self-Check: PASSED

All 5 claimed created/modified source files confirmed present on disk (plus this SUMMARY.md itself). All 3 claimed commit hashes (`3e33d16`, `0c276de`, `3af7211`) confirmed present in `git log --oneline --all`. `PlatformAdminControllerTest` re-confirmed at 26/26 passing; full backend suite 165/165 passing; `mvn spotbugs:check` exit code 0.
