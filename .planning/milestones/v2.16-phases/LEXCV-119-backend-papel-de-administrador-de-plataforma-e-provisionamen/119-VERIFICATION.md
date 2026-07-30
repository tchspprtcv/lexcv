---
phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen
verified: 2026-07-29T20:00:00Z
status: passed
score: 4/4 ROADMAP success criteria verified (34/34 plan-level must-have truths across 4 plans)
overrides_applied: 0
re_verification: false
---

# Phase 119: Backend — Papel de Administrador de Plataforma e Provisionamento Verification Report

**Phase Goal:** Existe um papel `PLATAFORMA_ADMIN`, distinto do `ADMIN` de cada escritório, associado a uma tenant reservada "LexCV", com uma capacidade de backend para criar tenants adicionais sem depender do wizard `/setup` — que se mantém singleton, reservado só ao arranque inicial da própria plataforma.
**Verified:** 2026-07-29T20:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

**Adversarial stance applied:** started from the hypothesis that the phase goal was not achieved. Did not trust `119-01/02/03/04-SUMMARY.md` or `119-REVIEW.md` narrative claims — independently recompiled the backend, re-ran the full unit-test suite and SpotBugs from scratch, read every production file line-by-line, read the security-critical test files in full and cross-checked each assertion against the exact production code path it claims to prove, and confirmed via `git log` that files required to stay untouched (`SetupController.java`, `SecurityConfig.java`, `UserPrincipal.java`, `pom.xml`) genuinely have zero commits since Phase 98/before Phase 119 started. All SUMMARY/REVIEW claims independently reproduced as accurate.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — authoritative contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Existe um papel `PLATAFORMA_ADMIN` seedado (`DatabaseSeeder`), distinto de `ADMIN`, e uma tenant reservada "LexCV" à qual os utilizadores desse papel pertencem | ✓ VERIFIED | `DatabaseSeeder.seedRbac()` line 372: `upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList())`, unconditional (no gate). `seedTenantPlataforma()` (lines 405-408) unconditionally finds-or-creates `Tenant.nome="LexCV"`. `seedUtilizadorPlataforma()` (lines 428-455) creates `plataforma@lexcv.cv` with `tenantId = tenantPlataforma.getId()` and exactly one role `PLATAFORMA_ADMIN`, when `seedEnabled=true`. Proven by `DatabaseSeederPlataformaAdminTest` — independently re-ran: **5/5 pass**. |
| 2 | Um novo método de serviço de backend cria um `Tenant` + o respetivo utilizador `ADMIN` inicial, reutilizando a validação já existente em `SetupService.initializeSystem`, sem depender de `SystemSetting.initialized` | ✓ VERIFIED | `SetupService.provisionTenant()` (lines 102-131) calls the shared private `validateRequest(request)` (line 104, same helper `initializeSystem` calls at line 45), creates `Tenant`+`User` with `roleRepository.findByNome("ADMIN")` (line 110), returns the saved `Tenant`. Zero references to `systemSettingRepository` in the method body (confirmed by isolated method-body grep). Proven by `SetupServiceProvisionTenantTest` (`verifyNoInteractions(systemSettingRepository)` at Casos 2 and 6) — independently re-ran: **9/9 pass**. |
| 3 | `POST /api/v1/setup/initialize` continua a devolver erro se chamado uma segunda vez — a nova capacidade de criar tenants é um caminho de código distinto, gated a `PLATAFORMA_ADMIN`, nunca reaproveita o endpoint público de `/setup` | ✓ VERIFIED | `SetupController.java` read in full: byte-for-byte the same singleton-gated `initialize()` (checks `setupService.isInitialized()` first, `403` if already initialized). `git log` confirms zero commits touching this file since Phase 98 (long before Phase 119). Proven by `SetupControllerSingletonRegressaoTest` (asserts `403` on 2nd init, `201` on 1st, and `verify(setupService, never()).provisionTenant(any())` in both branches) — independently re-ran: **3/3 pass**. |
| 4 | Um utilizador com o papel `ADMIN` de um tenant normal não tem `hasRole('PLATAFORMA_ADMIN')` e recebe `403` ao tentar invocar a nova capacidade de criação de tenants | ✓ VERIFIED | `PlatformAdminController` class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (line 36). Proven with a **real** `AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory` AOP proxy (the same interceptor Spring wires via `@EnableMethodSecurity` in production) in `PlatformAdminControllerTest`: `ROLE_ADMIN` → `AccessDeniedException` thrown *before* `provisionTenant` runs; `ROLE_PLATAFORMA_ADMIN` → passes, `201`. `GlobalExceptionHandler.handleAccessDeniedException` (lines 65-71) maps that exception class to `403` with a generic message (never echoes `ex.getMessage()`). Also depends on, and is closed by, the `AdminController` self-escalation guards (Plan 03 + CR-01 fix — see truths table below) that prevent a tenant `ADMIN` from ever obtaining the role in the first place. Independently re-ran `PlatformAdminControllerTest`: **9/9 pass**. See note under "Confirmation Bias Counter" below re: no single executed HTTP round-trip proves the full chain in one test — this is architecturally sound and consistent with this codebase's established no-MockMvc/no-`@SpringBootTest` testing convention (confirmed across all prior phases), not a gap. |

**Score:** 4/4 ROADMAP success criteria verified.

### Plan-Level Must-Have Truths (supporting detail, 34 total across 4 plans)

All plan-level `must_haves.truths` entries were checked against the current (post-review-fix) code and its tests. Condensed by plan; each row's evidence is a specific code location + a specific test that was independently re-run.

**Plan 01 (PROV-01) — role + reserved tenant seed, 9/9 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `PLATAFORMA_ADMIN` role exists after any startup, distinct from `ADMIN`, empty permission set, incl. `seedEnabled=false` | ✓ | `seedRbac()` L372, unconditional; `run_comSeedDisabled_seedaOPapelPlataformaAdminMesmoAssim` |
| Reserved `Tenant` "LexCV" exists after any startup, incl. `seedEnabled=false` | ✓ | `seedTenantPlataforma()` called before the `seedEnabled` gate in `run()` L55; `run_comSeedDisabled_seedaTenantReservadaMasNuncaCriaCredencialDePlataforma` |
| With `seedEnabled=true`, bootstrap user `plataforma@lexcv.cv` exists, tenant="LexCV", role=`PLATAFORMA_ADMIN` only | ✓ | `seedUtilizadorPlataforma()` L428-455; `run_comSeedEnabled_criaUtilizadorBootstrapLigadoATenantReservadaComUmUnicoPapel` |
| With `seedEnabled=false`, NO `plataforma@lexcv.cv` user is created | ✓ | `run()` L57-59 `if (!seedEnabled) return;` precedes `seedUtilizadorPlataforma()` call; `verify(userRepository, never()).save(any())` in the same Caso 1 test above |
| Role + reserved tenant are gated by neither `seedEnabled`, `SystemSetting.initialized`, nor existing data | ✓ | `seedRbac()` and `seedTenantPlataforma()` both run unconditionally at the top of `run()`, before all three gates (L43-56) |
| Bootstrap user is gated ONLY by `seedEnabled`, not `initialized`/counts | ✓ | `seedUtilizadorPlataforma(tenantPlataforma)` call (L61) sits strictly between `if (!seedEnabled) return;` (L57) and the `initialized` check (L63) |
| Idempotent across N restarts (no duplicate tenant/user) | ✓ | `findFirstByNome`/`findByEmail` find-or-create; `run_numSegundoArranqueComSeedEnabled_naoRecriaTenantNemUtilizador` asserts `never()).save` for both |
| Demo-data block still requires a genuinely empty DB — three counts read BEFORE the reserved-tenant insert | ✓ | `bdVaziaAntesDoSeedPlataforma` computed at L51-53, before `seedTenantPlataforma()` at L55; `run_comBaseDeDadosVazia_leAsTresContagensAntesDeInserirATenantReservada` uses Mockito `InOrder` to prove the read-before-insert ordering |
| `PLATAFORMA_ADMIN` never receives scoped permissions via seed | ✓ | `Collections.emptyList()` literal at the one `upsertRolePermissions("PLATAFORMA_ADMIN", ...)` call site (L372) |

**Deviation (reviewed, beneficial, documented):** `TenantRepository.findByNome` (as literally named in this plan's artifact contract) was renamed to `findFirstByNome` during the post-execution code review (**WR-01**, `119-REVIEW.md`) to make the reserved-tenant lookup tolerant of a concurrent-boot duplicate-row race (no unique DB constraint on `t_tenant.nome`). The *capability* the plan required (idempotent find-or-create lookup by literal name, used by `DatabaseSeeder` before any save) is fully retained and arguably strengthened. Confirmed: `findFirstByOrderByCreatedAtAsc` (the old WR-01-from-Phase-98 method this plan's Javadoc was told to preserve) was **separately, deliberately deleted** by the CR-02 fix (see SC/Truths below) — this is intentional, not scope creep; `grep -rn "findFirstByOrderByCreatedAtAsc"` across the whole repo returns only historical Javadoc prose, zero executable call sites.

**Plan 02 (PROV-06) — `SetupService.provisionTenant`, 7/7 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| Creates `Tenant`+initial `ADMIN` `User`, returns saved `Tenant` (never `void`) | ✓ | `provisionTenant()` L102-131, `return tenant;` at L130 (saved, `id` populated); Caso 1 |
| Reuses `validateRequest` without duplicating rules; same exact messages | ✓ | `validateRequest(request)` call at L104 (private helper shared with `initializeSystem`); Casos 3a/3b/3c assert the 3 literal messages |
| Never reads/writes `SystemSettingRepository` | ✓ | Zero occurrences of `systemSettingRepository` in the method body (isolated-body grep); `verifyNoInteractions(systemSettingRepository)`, Casos 2 and 6 |
| Repeatable — N calls create N tenants (unlike `initializeSystem`) | ✓ | Caso 6: `provisionTenant_chamadoDuasVezesCriaDuasTenantsDistintas` — `verify(tenantRepository, times(2)).save`, `verify(userRepository, times(2)).save` |
| Initial user gets tenant's own `ADMIN` role, never `PLATAFORMA_ADMIN` | ✓ | `roleRepository.findByNome("ADMIN")` hardcoded at L110 — role name never derived from request; Caso 1 asserts exactly 1 role named `"ADMIN"` |
| Duplicate email (any tenant) fails with `IllegalArgumentException` before creating the `Tenant` | ✓ | L106-108 check precedes `tenantRepository.save`; Caso 4: `verify(tenantRepository, never()).save(any())` |
| `initializeSystem` remains byte-for-byte unchanged (incl. `SystemSetting` block, `void` return) | ✓ | Read in full (L44-86): singleton gate, `void`, unchanged. Caso 7 non-regression test: `IllegalStateException` on already-initialized system |

**Plan 03 (PROV-01) — `AdminController` containment guards, 7/7 VERIFIED (protection scope now BROADER than originally planned, see note)**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `POST /admin/users` with `PLATAFORMA_ADMIN` in `roles` → 403, no user created | ✓ | `createUser` guard L152-158 (pre-lookup); Caso 1: `verify(userRepository, never()).save`, `verify(roleRepository, never()).findByNome("PLATAFORMA_ADMIN")` |
| `PUT /admin/users/{id}` with `PLATAFORMA_ADMIN` in `roles` → 403, roles unchanged | ✓ | `updateUser` guard L282-290 (before any `save`); Caso 4 |
| The 4 tenant roles (`ADMIN`/`ADVOGADO`/`TECNICO`/`ASSISTENTE`) still work exactly as before | ✓ | Casos 3, 5, 8: `201`/`200` + `save` called exactly once each |
| `GET /admin/rbac` never includes `PLATAFORMA_ADMIN` in `rolePermissions` | ✓ | `getRbac` `continue` on `PAPEL_PLATAFORMA` L354-356; Caso 6: `size()==4`, `!containsKey("PLATAFORMA_ADMIN")` |
| `PUT /admin/rbac` ignores any `PLATAFORMA_ADMIN` entry (immutable, like `ADMIN`) | ✓ | `updateRbac` L406: `"ADMIN".equals(roleName) \|\| PAPEL_PLATAFORMA.equals(roleName)`; Caso 7: `verify(roleRepository, never()).save` |
| The 4 tenant roles remain visible in `GET /rbac` and editable via `PUT /rbac` | ✓ | Caso 6 (4 keys present), Caso 8 (`ASSISTENTE` edit still saves) |
| Phase 117's user-limit enforcement in `createUser`/`updateUser` unaffected | ✓ | `AdminControllerLimiteUtilizadoresTest` re-run in isolation and as part of full suite: still 9/9 green |

**Deviation — expansion, not scope reduction (CR-01 fix):** the *literal* plan text for this must-haves list only mentions the `"roles"` array. The post-execution code review (**CR-01**) found that `"permissions"` (a sibling free-form field `UserPrincipal.create` also turns into `GrantedAuthority` objects, **without** the app's own `"ROLE_"` prefixing logic) was a complete, unguarded bypass — an `ADMIN` could place the already-prefixed string `"ROLE_PLATAFORMA_ADMIN"` directly into `permissions` and satisfy `hasRole('PLATAFORMA_ADMIN')` without ever touching `"roles"`. This was fixed (constant `PAPEL_PLATAFORMA_AUTORIDADE`, guards added to both `createUser`/`updateUser` for the `"permissions"` field, L184-194 and L302-314) and is now proven by 6 additional test cases (Casos 9-14, `AdminControllerPlataformaAdminContencaoTest` grew from the originally-planned 8 cases to 14). Independently re-ran: **14/14 pass**. This means the actually-delivered protection for Success Criterion 4 is **stronger** than what Plan 03's own must-haves literally required — verified, not a gap.

**Plan 04 (PROV-06, PROV-01) — `PlatformAdminController` + global 403, 7/7 VERIFIED**

| Truth (condensed) | Status | Evidence |
|---|---|---|
| `POST /api/v1/platform/tenants` → `201` + `{id, nome}`, never a raw entity | ✓ | `createTenant()` L43-50, builds `TenantProvisionResponse`; Caso 1: `assertInstanceOf(TenantProvisionResponse.class, ...)`, `assertFalse(... instanceof Tenant)` |
| `ADMIN` of a normal tenant is recused, gate fires before the method runs | ✓ | Real AOP-proxy test, Caso 5: `assertThrows(AccessDeniedException.class, ...)`, `verify(setupService, never()).provisionTenant(any())` |
| Recusa arrives at the client as `403`, never `500` | ✓ | `GlobalExceptionHandler.handleAccessDeniedException` (L65-71): catches `AccessDeniedException` (parent of the `AuthorizationDeniedException` Spring Security 6.4 actually throws), returns `403`, generic message |
| `PLATAFORMA_ADMIN` passes the same gate, gets `201` | ✓ | Caso 6: `assertDoesNotThrow(...)`, `HttpStatus.CREATED` |
| `/setup/initialize` still errors on 2nd call | ✓ | Same evidence as ROADMAP SC3 above |
| `/api/v1/platform` NOT in `permitAll()` allowlist | ✓ | `SecurityConfig.java` read in full — allowlist only has 6 paths, none is `/api/v1/platform`; independently re-confirmed via dedicated Grep tool: 0 matches |
| Controller never reads `SecurityContextHolder`/`UserPrincipal`, never substitutes a tenant | ✓ | Zero occurrences in the file (isolated grep); Caso 8: `verify(setupService).provisionTenant(same(request))` even with an unrelated random `tenantId` on the authenticated principal |

**Bonus fix beyond this plan's own must-haves, still within scope of `POST /api/v1/platform/tenants` correctness (WR-02, `119-REVIEW.md`):** a `DataIntegrityViolationException` catch clause (L55-68) was added to translate a concurrent-`adminEmail`-race loser into the same clean `400` the non-racy duplicate-email path already returns. The code reviewer additionally verified this at the JPA/Hibernate bytecode level (not just the mock boundary) — I did not redo that disassembly independently, but did confirm the corresponding test (`createTenant_comDataIntegrityViolationExceptionDevolve400ComMensagemDeEmailDuplicado`) passes and the catch-clause reasoning in the source comment is internally consistent with how `@Transactional`+`GenerationType.UUID` defer INSERT flush timing in this stack.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/repositories/TenantRepository.java` | `findByNome` derived query, WR-01 Javadoc intact | ✓ VERIFIED (renamed, see deviation note) | 25 lines. Method is now `findFirstByNome` (post-CR-02/WR-01 review fix) — capability fully retained; old `findFirstByOrderByCreatedAtAsc` intentionally deleted, not just this plan's addition |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` | `seedTenantPlataforma`, min 375 lines | ✓ VERIFIED | 456 lines. `seedRbac()`+`seedTenantPlataforma()`+`seedUtilizadorPlataforma()` all present, correctly ordered (confirmed by explicit line-number checks below) |
| `backend/src/test/java/com/lexcv/seed/DatabaseSeederPlataformaAdminTest.java` | `PLATAFORMA_ADMIN`, min 150 lines | ✓ VERIFIED | 200 lines, 5 tests, all pass |
| `backend/src/main/java/com/lexcv/dtos/TenantProvisionResponse.java` | `private UUID id;` | ✓ VERIFIED | 27 lines, exactly 2 fields (`id`, `nome`), 4 Lombok annotations, no sensitive/superfluous field |
| `backend/src/main/java/com/lexcv/services/SetupService.java` | `public Tenant provisionTenant(SetupInitializeRequest request)` | ✓ VERIFIED | 170 lines, signature exact, placed directly after `initializeSystem` |
| `backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java` | `provisionTenant`, min 130 lines | ✓ VERIFIED | 238 lines, 9 tests, all pass |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` | 4 containment guards, `PLATAFORMA_ADMIN` | ✓ VERIFIED | 425 lines, `PLATAFORMA_ADMIN`/`PAPEL_PLATAFORMA` present 16x (roles guard x2, permissions guard x2 [CR-01], getRbac filter, updateRbac immutability, constants+comments) |
| `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java` | `PLATAFORMA_ADMIN`, min 160 lines | ✓ VERIFIED | 356 lines (grew from 235 after CR-01), 14 tests, all pass |
| `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` | `hasRole('PLATAFORMA_ADMIN')`, min 40 lines | ✓ VERIFIED | 70 lines, class-level `@PreAuthorize`, single `POST /tenants` handler, no other HTTP verb |
| `backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java` | `AccessDeniedException` handler | ✓ VERIFIED | 81 lines, 4 `@ExceptionHandler`s (3 pre-existing + new), new one never echoes `ex.getMessage()` |
| `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` | `AuthorizationManagerBeforeMethodInterceptor`, min 150 lines | ✓ VERIFIED | 237 lines (grew from ~219 after WR-02), 9 tests (Group A x5 incl. WR-02, Group B x4 real-proxy), all pass |
| `backend/src/test/java/com/lexcv/controllers/SetupControllerSingletonRegressaoTest.java` | `isInitialized`, min 60 lines | ✓ VERIFIED | 93 lines, 3 tests, all pass |
| `backend/src/main/java/com/lexcv/controllers/PublicController.java` (review-fix artifact, not originally planned) | Generic branding, no tenant resolution | ✓ VERIFIED | 42 lines, no constructor args, no repository, returns constant `{nome:"LexCV", logoDataUrl:null}` — CR-02 fix, explicitly documents ISOL-01 is now satisfied (see below) |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `DatabaseSeeder.java` | `TenantRepository.java` | idempotent lookup before save | ✓ WIRED | `tenantRepository.findFirstByNome("LexCV")` at L406 (renamed from planned `findByNome`, same capability — see deviation note) |
| `DatabaseSeeder.java` | `Role.java` | empty-permission upsert | ✓ WIRED | `upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList())` at L372, confirmed via dedicated Grep tool (raw Bash `grep` gave a false negative here — see Anti-Patterns note on the `rtk` shell-hook quirk) |
| `DatabaseSeeder.java` | `UserRepository.java` | find-or-create guard by email | ✓ WIRED | `userRepository.findByEmail("plataforma@lexcv.cv")` at L433 |
| `DatabaseSeeder.java` (`run`) | `DatabaseSeeder.java` (`seedUtilizadorPlataforma`) | called only after `seedEnabled` gate | ✓ WIRED | Call at L61, strictly between `if (!seedEnabled) return;` (L57-59) and `initialized` check (L63) — confirmed by direct line read |
| `SetupService.java` | `RoleRepository.java` | `ADMIN` role lookup, never `PLATAFORMA_ADMIN` | ✓ WIRED | `roleRepository.findByNome("ADMIN")` at L110; isolated-method-body grep for `PLATAFORMA_ADMIN` in `provisionTenant` = 0 |
| `SetupService.java` | `SetupInitializeRequest.java` | shared private validation | ✓ WIRED | `validateRequest(request)` at L104, same helper as `initializeSystem` L45 |
| `AdminController.java` | `Role.java` | pre-lookup denylist | ✓ WIRED | `PAPEL_PLATAFORMA`/`PAPEL_PLATAFORMA_AUTORIDADE` constants + 4 guard sites, all preceding their respective `roleRepository.findByNome`/persist calls |
| `AdminController.java` | `RbacResponse.java` | filter reserved role out of `rolePermissions` map | ✓ WIRED | `roleRepository.findAll()` at L348, `continue` on `PAPEL_PLATAFORMA` at L354-356 |
| `PlatformAdminController.java` | `SetupService.java` | delegates to `provisionTenant`, never `initializeSystem` | ✓ WIRED | `setupService.provisionTenant(request)` at L45; Caso 4 proves `never()` for `initializeSystem`/`isInitialized` |
| `PlatformAdminController.java` | `TenantProvisionResponse.java` | explicit getter-to-setter copy, never raw entity | ✓ WIRED | `TenantProvisionResponse.builder().id(...).nome(...).build()` at L46-49 |
| `GlobalExceptionHandler.java` | Spring Security method-security layer | `AccessDeniedException` → `403` translation | ✓ WIRED | Handler at L65-71 catches the parent class of `AuthorizationDeniedException` (the concrete type Spring Security 6.4 throws); proven behaviorally by combining the real-AOP-proxy test (throws the exception) with direct code reading of the handler (maps it to 403) — see note under SC4/Confirmation Bias Counter |

### Data-Flow Trace (Level 4)

Not directly applicable in the React/fetch sense (this phase has no frontend/UI artifacts). The backend-equivalent concern — "does the seeded role/tenant data actually reach and drive a real authorization decision, rather than a decorative check?" — was traced explicitly:

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `PlatformAdminController`'s `@PreAuthorize` gate | `authorities` on `Authentication` | `UserPrincipal.create()` derives `ROLE_<r>` from `User.roles` (DB-backed, via `DatabaseSeeder`/`AdminController`) | Yes — proven by a *real* `AuthorizationManagerBeforeMethodInterceptor` interceptor (not a stub/mock of the interceptor), evaluated against actual `SimpleGrantedAuthority` objects | ✓ FLOWING |
| `AdminController`'s containment guards | `rolesList`/`permsList` from request body | Raw `Map<String,Object>` request body (untrusted caller input) | Yes — the guard inspects the actual caller-supplied list, not a hardcoded/stubbed value; proven with a mixed-list case (Caso 2/11) that a first-element-only check would have missed | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles clean | `cd backend && mvn -q -DskipTests compile` | exit 0 | ✓ PASS |
| Phase 119's 6 test classes (targeted) | `mvn test -Dtest=DatabaseSeederPlataformaAdminTest,SetupServiceProvisionTenantTest,AdminControllerPlataformaAdminContencaoTest,PlatformAdminControllerTest,SetupControllerSingletonRegressaoTest,PublicControllerTest` | `Tests run: 42, Failures: 0, Errors: 0` — BUILD SUCCESS | ✓ PASS |
| Full backend unit suite (regression) | `cd backend && mvn test` | `Tests run: 135, Failures: 0, Errors: 0` — BUILD SUCCESS (matches `119-REVIEW.md`'s independently-claimed count exactly) | ✓ PASS |
| SAST (SpotBugs + FindSecBugs, ASVS L1 gate) | `cd backend && mvn spotbugs:check` | BUILD SUCCESS, 0 findings | ✓ PASS |
| Protected files genuinely untouched by this phase | `git log --oneline -- backend/.../SetupController.java backend/.../SecurityConfig.java backend/pom.xml backend/.../UserPrincipal.java` | Most recent touching commit for all 4 files predates Phase 119's first commit (`681c53f`) by 20+ commits (last: Phase 98) | ✓ PASS |
| `findFirstByOrderByCreatedAtAsc` fully removed, not just deprecated | `grep -rn "findFirstByOrderByCreatedAtAsc" backend/` | 2 matches, both historical Javadoc prose (`PublicController.java`, `PublicControllerTest.java`), 0 executable call sites | ✓ PASS |
| `webpage/` branding fallback matches new backend constant (CR-02 regression check) | Read `webpage/src/lib/branding.ts` | `FALLBACK = { nome: "LexCV", logoDataUrl: null }` — byte-identical to backend's new always-returned constant | ✓ PASS |
| `web/` (tenant-facing app) does not consume `/public/branding` at all | `grep -rn "public/branding\|fetchBranding" web/src` | 0 matches | ✓ PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` files exist anywhere in the repository (`find`/glob for `**/probe-*.sh` returns nothing), and neither the PLAN/SUMMARY files nor the ROADMAP success criteria for this phase reference probe-based verification. Not applicable to this phase.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|-----------------|-------------|--------|----------|
| PROV-01 | 119-01, 119-03, 119-04 | Papel `PLATAFORMA_ADMIN`, distinto do `ADMIN` de cada escritório, associado a uma tenant reservada "LexCV" | ✓ SATISFIED | Role+tenant seeded unconditionally (Plan 01); self-escalation vector fully closed for both `roles` and `permissions` fields (Plan 03 + CR-01 fix); gate proven with real AOP proxy (Plan 04) |
| PROV-06 | 119-02, 119-04 | Wizard `/setup` deixa de ser singleton — fica só para o arranque inicial; tenants seguintes usam o fluxo de administrador de plataforma | ✓ SATISFIED | `provisionTenant` is a fully independent, repeatable, non-`SystemSetting`-touching path (Plan 02); exposed via `PlatformAdminController`, `/setup/initialize` unmodified and still singleton (Plan 04) |

**Orphan check:** `.planning/REQUIREMENTS.md`'s traceability table maps exactly `PROV-01` and `PROV-06` to "Phase 119" — identical to the two requirement IDs declared across this phase's 4 plans' `requirements:` frontmatter. **No orphaned requirements.**

**Important cross-phase note — do not rediscover/redo in Phase 121:** while auditing this phase's fix commits, `PublicController.java` was rewritten (CR-02, `119-REVIEW.md`) to always return generic `"LexCV"` branding, because `DatabaseSeeder.seedTenantPlataforma()`'s now-unconditional reserved-tenant seed would otherwise make "LexCV" permanently and silently win the old "oldest tenant by `createdAt`" branding lookup for every future installation. This fix is **already, functionally, the literal implementation of `ISOL-01`** ("Landing pública mostra sempre marca genérica LexCV (deixa de tentar mostrar branding "da" tenant)"), which `.planning/REQUIREMENTS.md` still lists as `[ ]` / "Phase 121 / Pending". Both `PublicController.java`'s class Javadoc and `PublicControllerTest.java`'s class Javadoc explicitly say so today. Recommend updating `REQUIREMENTS.md`'s `ISOL-01` row (and Phase 121's scope) to reflect this is done, to avoid duplicate work.

### Anti-Patterns Found

None blocking. Scanned every production file this phase touched (`TenantRepository.java`, `DatabaseSeeder.java`, `SetupService.java`, `AdminController.java`, `PlatformAdminController.java`, `PublicController.java`, `GlobalExceptionHandler.java`, `TenantProvisionResponse.java`) for `TODO|FIXME|TBD|XXX|PLACEHOLDER`, empty-implementation patterns, and hardcoded-empty-data patterns.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (various) | — | Case-insensitive substring hits on Portuguese words (`todos`, `método`) | N/A — false positive | None; confirmed by direct reading, no real debt marker exists anywhere in the phase's files |
| `GlobalExceptionHandler.java` | 65-71 | No dedicated unit test invoking `handleAccessDeniedException` directly | ℹ️ INFO | Non-blocking: the handler body is 3 statements, trivially correct on inspection; the behavior is proven end-to-end in composition (real-AOP-proxy test throws the exact exception class this handler declares); this codebase has no `MockMvc`/`@SpringBootTest` harness anywhere to test the full HTTP dispatch chain, consistent with every prior phase's testing convention |

**Environment note (reproduced, not a code issue):** during independent re-verification, raw piped Bash `grep` commands against `DatabaseSeeder.java`/`SecurityConfig.java` intermittently produced false-negative counts (0 instead of 1) for patterns that are unambiguously present in the file (confirmed instantly via the dedicated Grep tool and via direct `Read`). This exactly matches the `rtk` shell-hook quirk already self-reported in `119-01-SUMMARY.md` and `119-03-SUMMARY.md`. Not a code defect — flagged here only so it isn't mistaken for one.

### Human Verification Required

None. All four ROADMAP success criteria and all 34 plan-level must-have truths are verified via a combination of: direct code reading, independently-reproduced automated test runs (compile, targeted 42/6-classes, full 135-test suite, SpotBugs), and `git log` evidence for "must stay untouched" files. The one item that is proven by composition rather than a single executed HTTP round-trip (Success Criterion 4's exact `403` status code reaching an HTTP client) is well-established, standard Spring Security 6 behavior (`AuthorizationDeniedException extends AccessDeniedException`, caught by `@RestControllerAdvice` before the filter chain's `ExceptionTranslationFilter` gets a turn) and is consistent with this codebase's deliberate, established architecture (no integration-test harness anywhere). Forcing a live server boot + curl smoke test here would be inconsistent with this project's own testing conventions and with the verification tooling's own guidance to avoid starting servers/services during a verification pass.

### Gaps Summary

No gaps. All 4 ROADMAP Success Criteria are objectively true in the current codebase, not merely claimed. This phase underwent an unusually rigorous post-execution deep code review (`119-REVIEW.md`) that found and fixed 2 Critical issues (a complete role-escalation bypass via `User.permissions`, and a regression that would have silently broken public branding for every future paying tenant) plus 2 Warnings and 1 Info item — I independently re-derived every one of those fixes from the current source (not from the review's or SUMMARYs' narrative) and confirmed each is present, correct, and covered by passing tests. The full backend suite (135/135) and SpotBugs/FindSecBugs (0 findings) both pass on a fresh, independent run performed during this verification, matching the review's own claimed numbers exactly. One naming deviation (`TenantRepository.findByNome` → `findFirstByNome`) is a reviewed, beneficial, and intent-preserving change, not a gap. One requirement belonging to a later phase (`ISOL-01`, Phase 121) is already functionally satisfied by this phase's CR-02 fix — flagged explicitly above so it is not rediscovered or redone.

---

_Verified: 2026-07-29T20:00:00Z_
_Verifier: Claude (gsd-verifier)_
