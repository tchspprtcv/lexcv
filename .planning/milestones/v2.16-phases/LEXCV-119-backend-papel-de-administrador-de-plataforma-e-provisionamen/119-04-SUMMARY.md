---
phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen
plan: 04
subsystem: auth
tags: [rbac, authorization, spring-boot, mockito, spring-security-test, aop-proxy, tdd, multi-tenant]

# Dependency graph
requires:
  - phase: 119-02
    provides: "SetupService.provisionTenant(SetupInitializeRequest) + TenantProvisionResponse DTO (id + nome)"
  - phase: 119-03
    provides: "PLATAFORMA_ADMIN containment guards in AdminController -- the precondition this plan's own threat model (T-119-04) says its @PreAuthorize gate depends on"
provides:
  - "POST /api/v1/platform/tenants -- PLATAFORMA_ADMIN-gated tenant provisioning endpoint, class-level @PreAuthorize, returning {id, nome} via TenantProvisionResponse -- the exact contract Phase 120's admin console will consume"
  - "GlobalExceptionHandler now maps AccessDeniedException (parent of the AuthorizationDeniedException subclass Spring Security 6.4 actually throws) to 403 globally -- fixes the previously-documented 500-instead-of-403 symptom for every @PreAuthorize-gated endpoint in the backend, not just this plan's new one"
  - "SetupControllerSingletonRegressaoTest -- permanent regression proof that /setup/initialize's singleton gate remains intact and fully independent from the new provisioning path"
  - "PlatformAdminControllerTest -- first use in this codebase of a real AuthorizationManagerBeforeMethodInterceptor + ProxyFactory proxy to prove a @PreAuthorize gate behaviorally (not just by reflection), reusable as precedent for any future role-gated controller test"
affects: [120, 123]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AOP proxy-based method-security behavioral test (ProxyFactory.setProxyTargetClass(true) + AuthorizationManagerBeforeMethodInterceptor.preAuthorize()) to prove a @PreAuthorize gate is actually evaluated, without introducing MockMvc/@SpringBootTest into this codebase"
    - "Global exception-to-HTTP-status translation extended in GlobalExceptionHandler (AccessDeniedException -> 403) rather than per-controller catch blocks -- matches the existing @RestControllerAdvice convention and applies retroactively to every already-gated endpoint"

key-files:
  created:
    - backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
    - backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java
    - backend/src/test/java/com/lexcv/controllers/SetupControllerSingletonRegressaoTest.java
  modified:
    - backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java

key-decisions:
  - "GlobalExceptionHandler's new Javadoc deliberately avoids spelling out the literal word 'AuthorizationDeniedException' -- the acceptance criteria's grep -c 'AuthorizationDeniedException' devolve 0 check would otherwise false-positive against an explanatory comment naming that class; rewrote to describe it generically ('a subclasse concreta que o Spring Security 6.4 lança...'), mirroring the exact false-positive pattern 119-02 already hit and resolved the same way"
  - "createTenant (English verb) chosen as the handler method name, matching this codebase's existing controller-method convention (createUser, updateUser, getBranding, initialize) even though the domain/DTO/route vocabulary stays Portuguese"
  - "PlatformAdminControllerTest Group B (Casos 5/6/8) authenticates via UsernamePasswordAuthenticationToken with explicit SimpleGrantedAuthority roles (not derived from a stored Role), and Casos 6/8 additionally populate a UserPrincipal with a distinct random tenantId to prove the controller never substitutes it into the request -- same shape as AdminControllerLimiteUtilizadoresTest's authentication helper"
  - "SetupControllerSingletonRegressaoTest's 3rd case reuses one shared mock with consecutive stubbing (isInitialized().thenReturn(true, false)) across two sequential initialize() calls rather than resetting or duplicating mocks -- same idiom already established in AdminControllerLimiteUtilizadoresTest (contagemAoVivo test), proving both branches independently in one verification"

patterns-established:
  - "AOP proxy-based method-security behavioral test: ProxyFactory(controller) + setProxyTargetClass(true) + addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize()), with SecurityContextHolder populated manually and cleared in @AfterEach -- available as precedent for any future role-gated controller needing more than a reflection-only annotation check"

requirements-completed: [PROV-06, PROV-01]

# Metrics
duration: ~17min
completed: 2026-07-29
---

# Phase 119 Plan 04: PlatformAdminController + Global 403 Mapping Summary

**`POST /api/v1/platform/tenants` gated to `PLATAFORMA_ADMIN` at the class level, proved by a real `AuthorizationManagerBeforeMethodInterceptor` proxy (not reflection), backed by a new `GlobalExceptionHandler` mapping that turns every `@PreAuthorize` refusal backend-wide from `500` into `403` -- closing Success Criteria 3 and 4 of Phase 119.**

## Performance

- **Duration:** ~17 min (commit span 08:19:04Z -> 08:29:41Z ~10.5 min; total includes upfront reading of PLAN/119-02-SUMMARY/119-03-SUMMARY/PROJECT/STATE/config/CONTEXT/PATTERNS/CLAUDE.md plus 10 source-file reads for pattern confirmation, and the Task 3 verification gate: full suite, 2 targeted regression suites, SpotBugs, package)
- **Started:** 2026-07-29T08:14:02Z (STATE.md's recorded completion of 119-03, immediately preceding this plan)
- **Completed:** 2026-07-29T08:31:19Z
- **Tasks:** 3 (Task 2 executed as TDD: RED + GREEN commits; Task 3 added a new regression test file, no production code changes needed)
- **Files modified:** 4 (3 created, 1 modified)

## Accomplishments

- `GlobalExceptionHandler` gained a 4th handler, `@ExceptionHandler(AccessDeniedException.class)` (lines 65-71), placed before the pre-existing `Exception.class` catch-all: returns `403` with a generic Portuguese message (`"Acesso negado."`) that never echoes `ex.getMessage()`, and logs via `logger.warn` (not `error`) since an authorization refusal is an expected event. Deliberately catches the **parent** `AccessDeniedException` rather than the concrete `AuthorizationDeniedException` Spring Security 6.4 actually throws for method-security refusals, so the mapping stays stable across a future Spring Security version bump. This is a **global** side effect: every endpoint already gated by `@PreAuthorize` anywhere in the backend now returns `403` instead of `500` on refusal -- including the exact symptom already logged in STATE.md for `GET /api/v1/admin/users` ("For any non-ADMIN role this returns 500 (should be 403)"). `web/src/lib/api.ts` line 43 (`if (res.status !== 401 && res.status !== 403) { toast.error(...) }`) already special-cases both `401` and `403` to skip the toast while still `throw`-ing an `Error` for calling code to handle -- so the only client-visible change is the disappearance of a spurious "Erro 500: Access Denied" toast, with zero change to navigation/redirect behavior.
- `PlatformAdminController` (new, 56 lines): `@RestController`, `@RequestMapping("/api/v1/platform")`, class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (line 35, above the class declaration at line 37 -- covers any handler a future Phase 120 might add here automatically), single collaborator `SetupService`. Its one handler, `POST /tenants` (`createTenant`), delegates to `setupService.provisionTenant(request)` and builds `TenantProvisionResponse.builder().id(tenant.getId()).nome(tenant.getNome()).build()` -- never `body(tenant)`. Catches `IllegalArgumentException` -> `400` and `IllegalStateException` -> `403`, the same mapping `SetupController.initialize` already uses. Never reads `SecurityContextHolder`/`UserPrincipal` (it provisions a *new* tenant, not one scoped to the caller), never calls `initializeSystem`/`isInitialized`, and has no `@GetMapping`/`@PutMapping`/`@DeleteMapping`/`@PatchMapping` -- only the one `POST` this phase requires (PROV-03's list endpoint is explicitly Phase 120's job).
- `PlatformAdminControllerTest` (new, 219 lines, 8 cases): Group A (direct instantiation) proves `201` with `id`+`nome` and no raw `Tenant` in the body (`assertInstanceOf`/`assertFalse(... instanceof Tenant)`), `400` on `IllegalArgumentException`, `403` on `IllegalStateException`, and exclusive delegation to `provisionTenant` (`verify(..., never()).initializeSystem(any())` / `.isInitialized()`). Group B wraps the controller in a real `ProxyFactory` (`setProxyTargetClass(true)`, CGLIB) advised with `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` -- the same interceptor Spring Security installs in production via `@EnableMethodSecurity` -- and proves: a `ROLE_ADMIN` caller gets `AccessDeniedException` thrown **before** `provisionTenant` runs (`verify(..., never())`); a `ROLE_PLATAFORMA_ADMIN` caller passes and gets `201`; the annotation's exact SpEL string is `hasRole('PLATAFORMA_ADMIN')` (reflection, catches role-name typos or accidental removal); and the controller passes the **same** request object reference to the service (`verify(setupService).provisionTenant(same(request))`) even when the authenticated `UserPrincipal` carries an unrelated random `tenantId` -- proving no tenant substitution ever happens.
- `SetupControllerSingletonRegressaoTest` (new, 93 lines, 3 cases): proves Success Criterion 3 end-to-end -- an already-initialized system returns `403` and never calls `initializeSystem`; a first initialization returns `201` and calls `initializeSystem` exactly once; and neither branch of the public wizard ever calls `provisionTenant` (proved via consecutive stubbing `isInitialized().thenReturn(true, false)` across two sequential calls in one test, so both branches are checked against the same assertion). `SetupController.java` itself was never touched by this phase (`git diff --name-only` empty across all 4 plans of Phase 119).
- Full backend unit suite: **130/130 green** (119 pre-existing after 119-03 + 11 new: 8 from `PlatformAdminControllerTest` + 3 from `SetupControllerSingletonRegressaoTest`). `AdminControllerLimiteUtilizadoresTest` (9 tests) and `AuthControllerGetMeTenantPlanoTest` (4 tests) re-run in isolation, unchanged. `mvn spotbugs:check`: 0 findings, no new `spotbugs-exclude.xml` entries needed. `mvn -DskipTests package`: `BUILD SUCCESS`.

## Task Commits

Each task was committed atomically:

1. **Task 1: GlobalExceptionHandler maps AccessDeniedException to 403** - `a3bd05d` (fix)
2. **Task 2 (TDD, RED): failing tests for PlatformAdminController** - `00452b1` (test)
3. **Task 2 (TDD, GREEN): PlatformAdminController** - `c73680d` (feat)
4. **Task 3: SetupController singleton regression suite + full-suite/SpotBugs/package verification** - `5352b17` (test)

_Note: Task 2 has two commits per this codebase's established TDD convention (RED then GREEN, e.g. Phases 117/118/119-01/119-02/119-03) -- RED was confirmed as a genuine compile failure (`cannot find symbol: class PlatformAdminController`, 2 errors) before GREEN was written._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java` - adds `@ExceptionHandler(AccessDeniedException.class)` -> `403`, generic message, never echoes `ex.getMessage()`; the 3 pre-existing handlers untouched
- `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` (new) - `POST /api/v1/platform/tenants`, `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` at class level, delegates to `SetupService.provisionTenant`
- `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` (new) - 8 Mockito cases; Group A direct instantiation, Group B real AOP method-security proxy
- `backend/src/test/java/com/lexcv/controllers/SetupControllerSingletonRegressaoTest.java` (new) - 3 Mockito cases proving `/setup/initialize`'s singleton gate and its independence from the new provisioning path

## Decisions Made
- Followed `119-PATTERNS.md`'s exact structural templates: `PlatformAdminController` mirrors `SetupController.initialize`'s try/catch shape plus `AdminController`'s class-level `@PreAuthorize` header, with the `isInitialized()` pre-check deliberately dropped (that gate belongs only to the public wizard).
- Rewrote `GlobalExceptionHandler`'s new-handler Javadoc mid-task after recognizing it would otherwise name `AuthorizationDeniedException` literally inside a comment, which the acceptance criteria's `grep -c 'AuthorizationDeniedException'` (expected `0`) would have flagged even though those lines are excluded by the actual automated `<verify>` gate's comment filter (`grep -v '^[[:space:]]*[/*]'`). Removed the ambiguity by describing the subclass generically instead of naming it, rather than relying on the two checks disagreeing in my favor -- the same resolution 119-02 already applied to `TenantProvisionResponse`'s Javadoc.
- Named the handler method `createTenant` (English verb), matching this codebase's existing controller-method-naming convention (`createUser`, `updateUser`, `getBranding`, `initialize`) even though CLAUDE.md's Portuguese-domain-language guidance governs entity/route/DTO vocabulary, not verb choice for controller methods.
- Used a real `ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` for Group B instead of only a reflection check on the annotation string, per the plan's explicit instruction -- this is the only way to prove the gate is *evaluated*, not merely present, and required no `ApplicationContext` since `hasRole(...)` needs no bean resolution.

## Deviations from Plan

None - plan executed exactly as written. (The Javadoc wording adjustment above was a same-task refinement made before the first commit, not a deviation from the plan's actual instructions -- the plan required explaining *why* the parent class is caught, not specific wording, and the final wording still conveys that reasoning in full.)

## Issues Encountered

None functionally. Per this plan's `<known_environment_note>`, all `mvn` invocations were redirected directly to a log file (`>`/`2>&1`, no pipe) and read back with the Read tool, and all source-content verification used the dedicated Grep tool rather than piped Bash `grep` -- avoiding the `rtk` shell-hook truncation risk flagged for this environment. No actual truncation or under-counting was observed this session, but the precaution was applied throughout as instructed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 120 (frontend admin console, PROV-02 through PROV-05) can now build directly against `POST /api/v1/platform/tenants`, consuming `{id, nome}` from `TenantProvisionResponse` and handling `201`/`400`/`403` exactly as proved by `PlatformAdminControllerTest`.
- All 5 Phase 119 success criteria for this plan hold structurally: `201` with `{id, nome}` for `PLATAFORMA_ADMIN`; `403` (never `500`, never `201`) for a tenant `ADMIN`; `/setup/initialize` still returns an error on a second call with `SetupController.java` byte-for-byte unchanged; no JPA entity serialized in the new endpoint's response; `SecurityConfig.java` untouched and `/api/v1/platform` not public.
- Phase 121 (ISOL-03, locking `PUT /api/v1/admin/rbac`) and Phase 123 (ISOL-04, the dedicated isolation audit) should include this new endpoint and its `PLATAFORMA_ADMIN` role in their scope -- the roadmap's own sequencing note (STATE.md) already anticipates this.
- Full backend suite: 130/130 tests green (119 pre-existing + 11 new from this plan), 0 regressions. `mvn spotbugs:check`: 0 findings. `mvn -DskipTests package`: exit 0, artifact produced.
- `backend/src/main/java/com/lexcv/config/SecurityConfig.java`, `backend/src/main/java/com/lexcv/controllers/SetupController.java`, `backend/src/main/java/com/lexcv/services/SetupService.java`, and `backend/pom.xml` all confirmed untouched (`git diff --name-only` empty for all four, across the whole of Phase 119) -- Success Criterion 3 ("nunca reaproveita o endpoint público de /setup") and the zero-new-dependency claim both hold structurally, not just behaviorally.
- **Phase 119 itself is now functionally complete across all 4 plans:** role/reserved tenant seeded unconditionally (119-01), repeatable provisioning service method (119-02), self-escalation containment guards closing the precondition this plan's gate depends on (119-03), and the gated endpoint + global 403 translation + singleton regression proof (119-04, this plan).

## STRIDE Mitigation Verdicts (Task 3)

| Threat ID | Verdict | Evidence |
|-----------|---------|----------|
| **T-119-04** (Elevation of Privilege -- endpoint alcançável por papel errado) | **CONFIRMADO** | `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` at `PlatformAdminController.java:35`, above the class declaration at line 37. Caso 5 (`PlatformAdminControllerTest`): a real `AuthorizationManagerBeforeMethodInterceptor` proxy throws `AccessDeniedException` for `ROLE_ADMIN` **before** `provisionTenant` runs (`verify(setupService, never()).provisionTenant(any())`). Caso 6 proves `ROLE_PLATAFORMA_ADMIN` passes the identical gate and receives `201` (rules out a gate that rejects everyone). Caso 7 fixes the exact SpEL string by reflection, closing the role-name-typo/removed-annotation gap. Depended on 119-03's containment guards (T-119-01), which are already `CONFIRMADO` per `119-03-SUMMARY.md`. |
| **T-119-05** (Elevation of Privilege -- endpoint alcançável sem autenticação) | **CONFIRMADO** | `grep -c '/api/v1/platform'` against `SecurityConfig.java` returns `0` -- the path is absent from the `permitAll()` allowlist (which only lists `/auth/login`, `/auth/refresh`, `/auth/logout`, `/setup/status`, `/setup/initialize`, `/public/branding`) and therefore falls under `.anyRequest().authenticated()` (line 69). `git diff --name-only -- SecurityConfig.java` is empty across this entire phase. |
| **T-119-08** (Tampering -- bypass ou afrouxamento do gate singleton de `/setup/initialize`) | **CONFIRMADO** | `SetupControllerSingletonRegressaoTest`'s 3 cases: already-initialized -> `403` + `initializeSystem` never called; first call -> `201` + `initializeSystem` called exactly once; neither branch ever calls `provisionTenant` (both scenarios asserted via consecutive stubbing in one test). `git diff --name-only -- SetupController.java SetupService.java` is empty for this plan; `SetupService.initializeSystem` was last touched by 119-02 only to add the sibling `provisionTenant` method beside it (confirmed in `119-02-SUMMARY.md`), never modifying `initializeSystem` itself. |
| **T-119-17** (Information Disclosure -- serialização da entidade `Tenant`/`User`/`Role` crua no corpo do 201) | **CONFIRMADO** | `PlatformAdminController.java`, filtering comments: `grep -c 'TenantProvisionResponse.builder()'` = 1 (line 45); the combined pattern `isInitialized\|initializeSystem\|SecurityContextHolder\|UserPrincipal\|body(tenant)` = 0 outside comments, confirming `body(tenant)` never appears. Caso 1 (`PlatformAdminControllerTest`) asserts `assertInstanceOf(TenantProvisionResponse.class, ...)` and `assertFalse(response.getBody() instanceof Tenant)`. The initial `ADMIN` user's email/password hash are never referenced anywhere in the response construction. |
| **T-119-21** (Information Disclosure -- mensagem interna de autorização no corpo do 403) | **CONFIRMADO** | The new handler's body (`GlobalExceptionHandler.java` lines 66-71) contains `logger.warn` (not `logger.error`) and returns a fixed generic string `"Acesso negado."` -- extracting just that method body shows `ex.getMessage()` appears 0 times; the only two textual occurrences of `ex.getMessage()` in the whole file are (a) inside this handler's own Javadoc explaining *why* it must not be echoed, and (b) inside the pre-existing, unrelated `Exception.class` catch-all. |
| **T-119-22** (Elevation of Privilege -- ampliação silenciosa da superfície de plataforma) | **CONFIRMADO** | `PlatformAdminController.java`, filtering comments: `grep -c '@GetMapping\|@PutMapping\|@DeleteMapping\|@PatchMapping'` = 0. Only one `@PostMapping("/tenants")` exists; the tenant-listing endpoint (PROV-03) is explicitly deferred to Phase 120 per `119-CONTEXT.md`'s own "Claude's Discretion" note. |
| **T-119-SC** (Tampering -- cadeia de fornecimento) | **CONFIRMADO (n/a)** | `git diff --name-only -- backend/pom.xml` is empty across all 4 plans of this phase. `AccessDeniedException`, `AuthorizationManagerBeforeMethodInterceptor`, and `ProxyFactory` were all already on the classpath via `spring-boot-starter-security` (main) and `spring-security-test`/transitive `spring-aop` (test) -- confirmed by a successful compile and test run with zero new dependencies. The Package Legitimacy Gate does not apply. |

### Cross-cutting impact note (T-119-23, disposition: accept, per this plan's own threat model)

Task 1's handler is global: it changes the HTTP status of **every** `@PreAuthorize` refusal in the backend from `500` to `403`, not only this plan's new endpoint. This is a correction of already-documented incorrect behavior (STATE.md's `GET /api/v1/admin/users` entry: "For any non-ADMIN role this returns 500 (should be 403)"), not a new risk. Client-side impact analysis: `web/src/lib/api.ts` line 43 (`if (res.status !== 401 && res.status !== 403) { toast.error(...) }`) already special-cases both `401` and `403` to suppress the toast while still throwing an `Error` for the calling code to handle, with no automatic redirect anywhere in that function. The only observable client-side effect of this change is the disappearance of a spurious `"Erro 500: Access Denied"` toast on any page that calls a role-gated endpoint without the required role -- no navigation regression. The full backend suite (130/130, Task 3) confirms no existing test asserted `500` on an authorization refusal, so no test needed weakening or rewriting to accommodate this change.

---
*Phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java`
- FOUND: `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java`
- FOUND: `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java`
- FOUND: `backend/src/test/java/com/lexcv/controllers/SetupControllerSingletonRegressaoTest.java`
- FOUND: `.planning/phases/LEXCV-119-backend-papel-de-administrador-de-plataforma-e-provisionamen/119-04-SUMMARY.md`
- FOUND commit: `a3bd05d`
- FOUND commit: `00452b1`
- FOUND commit: `c73680d`
- FOUND commit: `5352b17`
