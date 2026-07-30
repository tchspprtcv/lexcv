---
phase: 124-eliminar-duplica-o-da-contagem-de-utilizadores-ativos-no-ind
plan: 01
subsystem: api
tags: [spring-boot, jwt, mockito, jpa, tenant-isolation, tech-debt]

# Dependency graph
requires:
  - phase: 117
    provides: "UserRepository.countByTenantIdAndAtivoTrue — a única função de contagem de utilizadores ativos por tenant"
  - phase: 118
    provides: "AuthController.getMe()'s tenantRepository.findById(...).ifPresent(...) block already extended once for tenant_plano/tenant_limite_utilizadores"
provides:
  - "GET /api/v1/auth/me expõe tenant_utilizadores_ativos (Long), o 3º e último consumidor de UserRepository.countByTenantIdAndAtivoTrue"
  - "4 testes Mockito novos provando valor, zero-não-nulo, tenant-ausente e contagem exacta de 1 consulta de cada tipo por pedido"
affects: [124-02]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "3rd consumer of a single-source repository count method, added as the last statement inside an already-existing ifPresent lambda (same pattern Phase 118 established for tenant_plano/tenant_limite_utilizadores)"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeUtilizadoresAtivosTest.java
  modified:
    - backend/src/main/java/com/lexcv/dtos/UserResponse.java
    - backend/src/main/java/com/lexcv/controllers/AuthController.java

key-decisions:
  - "tenant_utilizadores_ativos typed as Long (boxed) — direct autobox of countByTenantIdAndAtivoTrue's primitive long, preserving the null-when-ifPresent-skips contract shared by tenant_plano/tenant_limite_utilizadores"
  - "Count call uses t.getId() (not a second principal.getTenantId() read) — matches the call-site style of the other two consumers (AdminController:122, PlatformAdminController:198) and keeps tenantRepository.findById at exactly 1 call inside getMe()"
  - "Production comment describes the mechanism in prose without naming the method/field literally, to avoid inflating the acceptance gate's grep -cF count for those exact tokens (same recurring MSYS2/grep-collision precedent as Phases 119/120/121)"
  - "New sibling test file (not an extension of AuthControllerGetMeTenantPlanoTest) — matches this codebase's one-test-class-per-AuthController-concern convention"

requirements-completed: []

# Metrics
duration: ~20min
completed: 2026-07-30
---

# Phase 124 Plan 01: Eliminar Duplicação da Contagem de Utilizadores Ativos — Backend Summary

**`GET /api/v1/auth/me` now returns `tenant_utilizadores_ativos` (Long) sourced exclusively from `UserRepository.countByTenantIdAndAtivoTrue` (Phase 117), becoming its 3rd and final consumer and closing the v2.16 milestone audit's integration finding #2 (the frontend "X/Y utilizadores" indicator recomputing this count client-side).**

## Performance

- **Duration:** ~20 min
- **Completed:** 2026-07-30
- **Tasks:** 2 (Task 1 produced 2 commits: RED then GREEN)
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments

- `AuthController.getMe()` gained a single new line inside its existing tenant `ifPresent` lambda, setting `tenant_utilizadores_ativos` from `userRepository.countByTenantIdAndAtivoTrue(t.getId())` — zero new endpoint, zero new DTO, zero new authorization surface.
- `UserResponse` gained the `tenant_utilizadores_ativos` field typed `Long` (boxed), matching the sibling `tenant_plano`/`tenant_limite_utilizadores` null-safety contract.
- 4 new Mockito tests (`AuthControllerGetMeUtilizadoresAtivosTest`) prove: the value passes through correctly, zero is returned as `0L` (never collapsed to `null`), an absent tenant leaves the field `null` and never invokes the count (`never()`), and exactly one call each to `tenantRepository.findById`/`countByTenantIdAndAtivoTrue` per request (no N+1).
- Full backend regression suite (187 tests across 20 classes), SpotBugs SAST, and the packaging build all confirmed green with zero new findings.
- All 5 threat-model items given a named, evidence-backed verdict (see below), including an honest disposition change from Phase 118's T-118-04 ("zero new query") now that this phase adds exactly one `COUNT` per authenticated request.

## Task Commits

Each task was committed atomically:

1. **Task 1 (RED): add failing test for `tenant_utilizadores_ativos`** - `fc2898a3` (test) — new test file, compilation fails on `body.getTenant_utilizadores_ativos()` (getter does not exist yet), proving the test is genuine before any production code changes.
2. **Task 1 (GREEN): expose `tenant_utilizadores_ativos` from single count source** - `537aea42` (feat) — the 2 production-code lines (DTO field + controller call); all 4 tests pass.
3. **Task 2: prove threat-model mitigations + full-suite non-regression** - no production-code commit (verification-only task, per its own `<action>` — "Esta task não altera código de produção"). Its deliverable is this SUMMARY.md, committed via the plan's final metadata commit.

_Task 1 is `tdd="true"`; its RED/GREEN split mirrors the precedent recorded in STATE.md for Phase 117 ("RED/GREEN committed as two separate atomic commits ... so git history shows the test genuinely failing to compile before the fix lands")._

## Files Created/Modified

- `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeUtilizadoresAtivosTest.java` - New test class, 4 `@Test` methods proving value/zero/absent-tenant/query-count behavior for the new field.
- `backend/src/main/java/com/lexcv/dtos/UserResponse.java` - Added `private Long tenant_utilizadores_ativos;` after `tenant_limite_utilizadores`, no other change.
- `backend/src/main/java/com/lexcv/controllers/AuthController.java` - Added one line + a 3-line prose comment inside `getMe()`'s existing tenant `ifPresent` lambda; no other change.

## Decisions Made

- **Field type `Long`, not `Integer`/primitive `long`:** `countByTenantIdAndAtivoTrue` returns primitive `long`; `Long` autoboxes with zero cast and preserves the "boxed, defaults to `null` when `ifPresent` never runs" contract its siblings already establish. `Integer` would not compile without an explicit cast; primitive `long` would break the null contract test case 3 asserts.
- **`t.getId()` over a second `principal.getTenantId()` read:** both are guaranteed equal here since `t` came from `tenantRepository.findById(principal.getTenantId())`; `t.getId()` matches the exact call-site idiom already used by `PlatformAdminController.java:198` and keeps the T-124-02 gate (`tenantRepository.findById(principal.getTenantId())` count stays at exactly 1) trivially true by construction.
- **Comment avoids literal method/field names:** the acceptance gate counts `grep -cF 'countByTenantIdAndAtivoTrue'` over the whole file (comments included, per this environment's documented grep quirks — see Baseline vs Final Counts below) and requires exactly `1`. A comment reproducing the method name literally would push that count to `2` and fail the gate. The production comment instead describes the mechanism in prose ("o metodo unico de contagem de utilizadores ativos do repositorio de utilizadores").
- **New sibling test file, not an extension of `AuthControllerGetMeTenantPlanoTest`:** matches the established one-test-class-per-`AuthController`-concern convention (`AuthControllerLoginLockoutTest`, `AuthControllerTenantSuspensoTest`, `AuthControllerGetMeTenantPlanoTest`), and the existing class's name/Javadoc are explicitly scoped to "TenantPlano".

## Deviations from Plan

None - plan executed exactly as written. All baseline grep counts, test names, and acceptance gates matched the plan's `<baseline_measurements>` table and Task 1/Task 2 acceptance criteria on first execution; no auto-fixes, no blocking issues, no architectural questions arose.

## Issues Encountered

None. No grep false positives or environment quirks were hit this session (the plan's documented Git Bash/MSYS2 grep quirks — `-cF` over the whole file, `[(]`/`[)]` instead of `\(`/`\)`, `-E` required for `|` alternation, `MSYS_NO_PATHCONV=1` for leading-slash patterns — were all pre-applied per the plan's own guidance and none tripped unexpectedly).

## Threat Model Verdicts

All 5 `<threat_model>` entries from `124-01-PLAN.md`, each verified by direct command execution or direct code reading (not by inspection alone):

**T-124-01 (Information Disclosure — new field served to all authenticated roles): CONFIRMADO.**
The new field is an occupancy integer scoped to the caller's own tenant, read exclusively from `t` (obtained via `tenantRepository.findById(principal.getTenantId())` — confirmed by direct reading of `AuthController.java:225-234` post-change). It is the same data class and audience as the already-universal `tenant_nome`/`tenant_logo_data_url`/`tenant_plano`/`tenant_limite_utilizadores` fields served by this same method since Phase 118. No cross-tenant read path exists: the only cross-tenant count in the codebase remains `PlatformAdminController.toSummary` (gated `PLATAFORMA_ADMIN`), confirmed untouched by `git status --porcelain` returning empty for that file.

**T-124-02 (Tampering — origin of the tenant id used in the count): CONFIRMADO.**
`grep -cF 'tenantRepository.findById(principal.getTenantId())'` = **1**; `grep -cF 'tenantRepository.findById'` = **3** (the other 2 are `login`'s `:119` and `refresh`'s `:182`, both keyed on `user.getTenantId()`, Phase 120's suspended-tenant guards — unrelated and unmodified). The count's argument is `t.getId()`, derived from that single `getMe()`-scoped lookup; no `tenant_id` is read from any request body/param/header in `getMe()`. This corrects `124-PATTERNS.md`'s "the only `tenantRepository.findById` call" framing — true only inside `getMe()`, not file-wide — exactly as `124-01-PLAN.md`'s own `<baseline_measurements>` section had already flagged.

**T-124-03 (Denial of Service — added query cost on a very-high-frequency endpoint): CONFIRMADO, disposição alterada face a T-118-04.**
Phase 118's T-118-04 accepted "zero new query"; that is no longer true. This phase adds exactly 1 aggregate `COUNT` per authenticated request to `/auth/me`. Proof: test case 4 (`getMe_consultaTenantEContagemExatamenteUmaVezCada`) asserts `verify(userRepository, times(1)).countByTenantIdAndAtivoTrue(TENANT_ID)` — passing. Test case 3 (`getMe_semTenantEncontrado_...`) asserts `verify(userRepository, never()).countByTenantIdAndAtivoTrue(any())` when the tenant is absent — passing. The unauthenticated path returns `401` on the method's first statement (`AuthController.java:204-207`), before any repository access — confirmed by direct reading, zero added query cost there. `grep -cF '@Index' backend/src/main/java/com/lexcv/models/User.java` = **0** — confirmed no composite index on `(tenant_id, ativo)` exists; `User.ativo` is `Boolean` (wrapper) with `@Builder.Default ... = true` (confirmed by direct reading of `User.java:36-38`; note the field also carries `@Column(nullable = false)`, a Hibernate DDL-generation hint only — it does not retroactively constrain a pre-existing column nor perform Bean Validation, so it does not weaken the "no index presumed" framing this verdict relies on). Acceptance rests on real cardinality (tens of users per tenant) and the fact the same query already runs on the `POST /admin/users` (Phase 117) and `GET /platform/tenants` (Phases 120/122) paths — not on a presumed index. No index added this phase; named as the future mitigation if `t_user` scale ever makes it matter.

**T-124-04 (Elevation of Privilege — trusting the client-exposed number as the gate): CONFIRMADO.**
`git status --porcelain` returns empty for `backend/src/main/java/com/lexcv/controllers/AdminController.java` — confirmed untouched. `AdminControllerLimiteUtilizadoresTest` (the authoritative `409` gate's test suite) passed unmodified: **9 tests, 0 failures, 0 errors** (`target/surefire-reports/com.lexcv.controllers.AdminControllerLimiteUtilizadoresTest.txt`). `AdminController.limiteUtilizadoresExcedido` (lines 119-122, confirmed by direct reading) remains the sole authoritative check; this phase does not touch it.

**T-124-SC (Tampering — supply chain): N/A.**
`git status --porcelain` returns empty for `backend/pom.xml` — confirmed untouched, zero dependencies added. The Package Legitimacy Gate does not apply (zero installs this phase).

## Command Output (real, not paraphrased)

**1. `cd backend && mvn test` (full unit suite, 20 classes):**
```
com.lexcv.controllers.AuthControllerGetMeTenantPlanoTest: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.config.JwtAuthenticationFilterTenantSuspensoTest: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.PublicControllerTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.PesquisaControllerTest: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.PlatformAdminControllerTest: Tests run: 27, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.models.ClienteNifValidationTest: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.services.NotificacaoServiceTest: Tests run: 39, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.AuthControllerTenantSuspensoTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.jobs.AlertasDiariosJobTest: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.seed.DatabaseSeederPlataformaAdminTest: Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.AuthControllerLoginLockoutTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.SetupControllerSingletonRegressaoTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.services.RiscoPrazoServiceTest: Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.AuthControllerGetMeUtilizadoresAtivosTest: Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.ResourceControllerUploadDocumentoTest: Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.services.SetupServiceProvisionTenantTest: Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.AdminControllerRbacAutorizacaoTest: Tests run: 7, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.AdminControllerPlataformaAdminContencaoTest: Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.config.ForwardedHeaderFilterRemoteAddrTest: Tests run: 3, Failures: 0, Errors: 0, Skipped: 0
com.lexcv.controllers.AdminControllerLimiteUtilizadoresTest: Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```
Total: 187 tests, 0 failures, 0 errors across 20 classes. (Integration tests with the `IT` suffix run under a separate `verify`/failsafe phase, not `test`/surefire — consistent with this project's Testcontainers infrastructure from Phase 91 and not part of this task's gate.)

**2. `cd backend && mvn spotbugs:check`:**
```
[INFO] Fork Value is true
[INFO] Done SpotBugs Analysis....
[INFO] BugInstance size is 0
[INFO] Error size is 0
[INFO] No errors/warnings found
[INFO] BUILD SUCCESS
```

**3. `cd backend && mvn -q -DskipTests package`:**
No console output under `-q` (silent on success). Verified via build artifact: `target/backend-0.0.1-SNAPSHOT.jar` (69.3M) freshly produced, confirming exit 0.

## Baseline vs Final Grep Counts

All match `124-01-PLAN.md`'s `<baseline_measurements>` table exactly:

| File | Pattern (`grep -cF`) | Before | After (expected) | After (actual) |
|---|---|---|---|---|
| `AuthController.java` | `countByTenantIdAndAtivoTrue` | 0 | 1 | **1** |
| `AuthController.java` | `tenantRepository.findById(principal.getTenantId())` | 1 | 1 | **1** |
| `AuthController.java` | `tenantRepository.findById` | 3 | 3 | **3** |
| `AuthController.java` | `@PreAuthorize` | 0 | 0 | **0** |
| `AuthController.java` | `grep -cE` stream/filter/query pattern | 5 | 5 | **5** |
| `UserResponse.java` | `@JsonProperty` | 0 | 0 | **0** |
| `UserResponse.java` | `private Long tenant_utilizadores_ativos;` | 0 | 1 | **1** |
| `UserResponse.java` | `private (Integer\|int\|long) tenant_utilizadores_ativos` | — | 0 | **0** |
| Test file | `@Test` count | — | 4 | **4** |
| `User.java` | `@Index` | — | 0 | **0** |

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The backend contract is complete and stable for Plan 02 (frontend): `tenant_utilizadores_ativos` is a `Long`, JSON key identical to the Java field name (no `@JsonProperty`), `null` when the tenant is not found, `0` (not `null`) when the count is genuinely zero.
- `web/src/types/auth.ts`'s `MeResponse` interface will need the matching `tenant_utilizadores_ativos?: number | null;` field, and `settings/page.tsx`'s `UserManagementTab` will need its `activeUserCount` derivation switched from the client-side `.filter()` to this new field — both already scoped and pattern-mapped in `124-PATTERNS.md` sections 5 and 7 for Plan 02.
- `web/scripts/verify-limite-utilizadores-indicator.mjs`'s `contagem-estrita` assertion will need updating (not bypassing) once Plan 02 changes the client-side derivation — flagged in `124-PATTERNS.md` section 8, out of this plan's scope.
- No blockers identified for Plan 02.

## Self-Check: PASSED

- FOUND: `backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeUtilizadoresAtivosTest.java`
- FOUND: `.planning/phases/LEXCV-124-eliminar-duplica-o-da-contagem-de-utilizadores-ativos-no-ind/124-01-SUMMARY.md`
- FOUND: commit `fc2898a3` (RED — test)
- FOUND: commit `537aea42` (GREEN — feat)
- FOUND: commit `0f74ecdf` (docs — this SUMMARY)

---
*Phase: 124-eliminar-duplica-o-da-contagem-de-utilizadores-ativos-no-ind*
*Completed: 2026-07-30*
