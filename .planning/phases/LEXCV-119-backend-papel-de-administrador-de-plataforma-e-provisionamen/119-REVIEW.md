---
phase: LEXCV-119-backend-papel-de-administrador-de-plataforma-e-provisionamen
reviewed: 2026-07-29T18:00:00Z
depth: deep
files_reviewed: 7
files_reviewed_list:
  - backend/src/main/java/com/lexcv/repositories/TenantRepository.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - backend/src/main/java/com/lexcv/services/SetupService.java
  - backend/src/main/java/com/lexcv/dtos/TenantProvisionResponse.java
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
  - backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java
findings:
  critical: 2
  warning: 2
  info: 2
  total: 6
status: issues_found
---

# Phase LEXCV-119: Code Review Report

**Reviewed:** 2026-07-29T18:00:00Z
**Depth:** deep
**Files Reviewed:** 7
**Status:** issues_found

## Summary

This phase adds a `PLATAFORMA_ADMIN` role, a reserved "LexCV" tenant, and a new
`POST /api/v1/platform/tenants` endpoint capable of provisioning arbitrary tenants. The five
specific risk vectors called out for this review were traced end-to-end, across files outside the
seven under review where necessary (`UserPrincipal`, `JwtAuthenticationFilter`, `AuthController`,
`SecurityConfig`, `PublicController`, `Tenant`, `User`, plus the actual git diffs for every touched
commit) rather than relying on the code's own comments:

1. **`PLATAFORMA_ADMIN` reachability by a regular tenant ADMIN — NOT actually closed.** The four
   "containment guards" added to `AdminController` (createUser, updateUser, getRbac, updateRbac)
   correctly block the `roles` array from ever containing `"PLATAFORMA_ADMIN"`. However, they check
   only `roles`, never the sibling free-form `permissions` array on the same two endpoints. Because
   `UserPrincipal.create` folds every string in `User.permissions` into `GrantedAuthority` objects
   verbatim (no prefix stripping, no catalog check), any tenant `ADMIN` can plant the literal string
   `"ROLE_PLATAFORMA_ADMIN"` into their own (or any user's) `permissions` set via the *existing*
   `PUT/POST .../admin/users` payload shape and pass `PlatformAdminController`'s
   `hasRole('PLATAFORMA_ADMIN')` gate on the very next request. See **CR-01**. This is exactly the
   escalation path the phase's own guard comments describe wanting to prevent, just via a field the
   guards never inspect.
2. **`GlobalExceptionHandler`'s new global `AccessDeniedException → 403` handler — verified safe.**
   Traced how Spring Security's method-security AOP interceptor (the only authorization mechanism in
   this app — `SecurityConfig` has no URL-level role rules, only `.anyRequest().authenticated()`)
   surfaces exceptions through `DispatcherServlet`'s exception-resolver chain rather than
   `ExceptionTranslationFilter`, confirmed no other manual `AccessDeniedException` throw sites exist
   in `backend/src/main`, and confirmed the frontend (`web/src/lib/api.ts:43`) already special-cases
   401/403 without a toast. No unintended side effect found; this is a net-positive, correctly scoped
   fix (closes a `500`-with-internal-message leak on every `@PreAuthorize`-protected endpoint in the
   app, not just the new one).
3. **`PlatformAdminController` reachability without the role — verified safe** via the class-level
   `@PreAuthorize`, confirmed by diffing `SecurityConfig.java` against the pre-phase-119 baseline
   (zero changes — no new `permitAll()` entry), and by the real AOP-proxy tests in
   `PlatformAdminControllerTest`. The only way to reach it without holding a genuine
   `PLATAFORMA_ADMIN`-assigned role is the CR-01 bypass above (a `User.permissions` route, not a
   `SecurityConfig`/controller-gate route).
4. **`DatabaseSeeder` startup ordering — the "read counts before inserting" fix is itself correct**
   (confirmed against the actual pre/post diff: the old live count check is replaced by a boolean
   captured before `seedTenantPlataforma()` runs, and `seedRbac()` — which runs first — never
   touches the three counted tables). But making the reserved tenant's seeding **unconditional and
   first** has two knock-on defects the plan never traced: it permanently breaks
   `TenantRepository.findFirstByOrderByCreatedAtAsc()`'s "oldest tenant" assumption for every future
   install (**CR-02**), and its find-or-create-by-name has no protection against a concurrent-boot
   race across replicas (**WR-01**).
5. **`/api/v1/setup/initialize` — verified genuinely untouched.** `git diff` of every phase-119
   commit against `SetupService.java`/`SetupController.java` shows `initializeSystem`,
   `validateRequest`, and `SetupController` are a pure addition (`provisionTenant` appended after),
   confirmed further by `SetupControllerSingletonRegressaoTest`.

## Narrative Findings (AI reviewer)

### Critical Issues

#### CR-01: `User.permissions` free-form field bypasses every `PLATAFORMA_ADMIN` containment guard — any tenant ADMIN can self-escalate to platform-wide tenant creation

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:168-172` (createUser) and `:275-282` (updateUser)
**Related:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java:27-62` (specifically 49-51), `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java:42-62` (specifically 48-53), `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java:35`

**Issue:** The phase's containment design (documented at length in `AdminController.java:32-44`) assumes the only way a user's authorities can ever contain `"ROLE_PLATAFORMA_ADMIN"` is by attaching the `PLATAFORMA_ADMIN` `Role` — so all four guards check the `roles` list. But `UserPrincipal.create` builds `authorities` from **two** independent sources:

```java
// UserPrincipal.java:30-51
Set<SimpleGrantedAuthority> authorities = roles.stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
        .collect(Collectors.toSet());
...
permissions.stream()
        .map(SimpleGrantedAuthority::new)   // <-- raw string, no "ROLE_" prefixing, no catalog check
        .forEach(authorities::add);
```

`permissions` here is `user.getPermissions()` (`JwtAuthenticationFilter.java:48-53`), a completely free-form `Set<String>` (`User.java:63-67`, `@ElementCollection`) that `AdminController.createUser`/`updateUser` accept **verbatim from the request body**, with zero validation against `PermissionRepository` — unlike `roles`, which are resolved via `roleRepository.findByNome(...)` and silently dropped if unknown:

```java
// AdminController.java:168-172 (createUser) — identical shape at 275-282 for updateUser
List<?> permsList = body.containsKey("permissions") ? (List<?>) body.get("permissions") : Collections.emptyList();
Set<String> permissions = new HashSet<>();
for (Object pObj : permsList) {
    permissions.add((String) pObj);   // any string accepted, including "ROLE_PLATAFORMA_ADMIN"
}
```

Because Spring Security's `hasRole('PLATAFORMA_ADMIN')` SpEL check is a pure string match against `getAuthorities()` for `"ROLE_PLATAFORMA_ADMIN"` — it does not care whether that `GrantedAuthority` came from a seeded `Role` or a raw permission string — this is a complete, mechanical bypass of all four guards.

**Reproduction (no timing/race required, 100% reliable):**
1. Authenticate as any tenant's existing `ADMIN` (any real customer's office admin, or the seeded `admin@lexcv.cv`).
2. `PUT /api/v1/admin/users/{ownUserId}` with body `{"permissions": ["ROLE_PLATAFORMA_ADMIN"]}` (own id obtainable from `GET /api/v1/auth/me`). This is accepted with `200 OK` — `updateUser`'s roles-guard (`AdminController.java:255-263`) never runs because the request has no `"roles"` key.
3. No re-login needed: `JwtAuthenticationFilter` re-reads `User` from the DB on every request (`JwtAuthenticationFilter.java:42-62`), so the very next request already carries the new authority.
4. `POST /api/v1/platform/tenants` with a valid `SetupInitializeRequest` body now passes `PlatformAdminController`'s class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` and creates an arbitrary new tenant + its own `ADMIN` user, `201 Created`.

A stealthier variant works via `createUser` instead of self-escalation: create a brand-new user with an innocuous `"roles": ["ADVOGADO"]` (satisfies the non-empty-roles check and the reserved-word guard) plus `"permissions": ["ROLE_PLATAFORMA_ADMIN"]` — a throwaway account with full platform privilege that looks like an ordinary lawyer account in the roles column of `GET /api/v1/admin/users` (the raw string is visible in that endpoint's `permissions` array if anyone audits it, but nothing flags or blocks it).

The extensive new test suite (`AdminControllerPlataformaAdminContencaoTest`) only ever sends `"roles"` payloads — none of its 8 cases exercise a `"permissions"` array, so this gap has no regression coverage either.

**Fix:** Validate `permissions` against the known catalog exactly the way `roles` are already validated, instead of trusting client-supplied strings:

```java
// AdminController.java:168-172 (createUser) and :275-282 (updateUser) — apply to both
List<?> permsList = body.containsKey("permissions") ? (List<?>) body.get("permissions") : Collections.emptyList();
Set<String> permissions = new HashSet<>();
for (Object pObj : permsList) {
    String permName = (String) pObj;
    permissionRepository.findByNome(permName).ifPresent(p -> permissions.add(p.getNome()));
}
```

As defense-in-depth (in case another write path to `User.permissions` is added later), consider also hardening `UserPrincipal.create` to reject/strip any permission string matching `^ROLE_.*` before turning it into a `GrantedAuthority` — permissions should never be able to mint a synthetic role. Add a regression test mirroring the existing "Caso 1/2" tests in `AdminControllerPlataformaAdminContencaoTest` but asserting on a `"permissions": ["ROLE_PLATAFORMA_ADMIN"]` payload.

---

#### CR-02: Unconditional reserved-tenant seeding permanently breaks the "oldest tenant" assumption behind the public branding endpoint, for every future install

**File:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:389-392` (`seedTenantPlataforma`)
**Related:** `backend/src/main/java/com/lexcv/repositories/TenantRepository.java:10-20` (`findFirstByOrderByCreatedAtAsc`, with its own pre-existing WR-01 warning), `backend/src/main/java/com/lexcv/controllers/PublicController.java:46-51`, `backend/src/main/java/com/lexcv/models/Tenant.java:47-53` (`createdAt` set by `@PrePersist`)

**Issue:** `seedTenantPlataforma()` runs unconditionally, first, on **every** application boot (before the `seedEnabled` check, `DatabaseSeeder.java:53-57`) — in dev *and* in production. On any brand-new database, this inserts the "LexCV" tenant with `createdAt = <first-ever boot time>`, unconditionally, before a real customer ever exists. `TenantRepository.java` already carries a Phase-98 warning about exactly this class of risk:

> "esta query assume um deployment single-tenant... Se uma futura feature de onboarding multi-tenant permitir uma 2ª Tenant, este call site tem de ser revisitado." (`TenantRepository.java:13-18`)

Phase 119 is precisely that future feature, and the call site was never revisited. `PublicController.getBranding()` — the public, unauthenticated endpoint that serves the login screen's name/logo — calls `tenantRepository.findFirstByOrderByCreatedAtAsc()` (`PublicController.java:51`) and returns whatever tenant is oldest. From this phase onward, for **every new deployment**, that will always be the reserved "LexCV" tenant (`nome="LexCV"`, `logoDataUrl=null`), because it is now guaranteed to be created before any tenant a real customer creates via `/setup/initialize`. The customer's actual branding (their name + logo, configured through the setup wizard) will never be shown — the public login page will silently serve generic/blank "LexCV" branding forever.

The endpoint's own pre-existing defensive log (`PublicController.java:46-49`, `if (tenantCount > 1) log.warn(...)`) *will* fire in this scenario, but it only writes a server-side log line — it does not change which tenant is served, and no operator is likely to be watching for it. Verified this is not a pre-existing/already-mitigated concern: it is absent from every Phase 119 planning document (`119-CONTEXT.md`, `119-01-PLAN.md`, `119-PATTERNS.md`) — the plans explicitly preserve the WR-01 Javadoc "intact" without anyone connecting it to the new unconditional seeding they were simultaneously introducing.

This is also forward-looking: the project's own multi-tenancy proposal (`proposta_multitenancy_distribuicao_faturacao.md`) and Phase 120's "list all tenants" success criterion both point toward genuinely multi-tenant hosting (many customer tenants on one backend). `PublicController.getBranding()` has no per-request tenant resolution at all (no host/subdomain lookup) — Phase 119 is the first change that guarantees a 2nd tenant will always exist, turning a previously theoretical gap into a guaranteed-to-trigger one for every install from here on.

**Verified NOT affected:** existing production deployments that already have a real tenant created before upgrading to this version (their real tenant's `createdAt` predates the newly-inserted "LexCV" row, so `findFirstByOrderByCreatedAtAsc()` still returns the real tenant, and the `tenantCount > 1` warning will correctly fire in their logs).

**Fix:** Exclude the reserved tenant from this lookup rather than relying on "whichever tenant happens to be oldest." Minimal patch (name-based, zero migration):

```java
// TenantRepository.java
Optional<Tenant> findFirstByNomeNotOrderByCreatedAtAsc(String nomeReservado);
```
```java
// PublicController.java:51
Optional<Tenant> tenant = tenantRepository.findFirstByNomeNotOrderByCreatedAtAsc("LexCV");
```

A more robust fix (recommended before Phase 120 broadens this surface) adds a dedicated boolean/flag column (e.g. `reservado_plataforma`) so the exclusion doesn't depend on a customer never naming their firm literally "LexCV". Either way, add a seeder-level regression test that runs `DatabaseSeeder.run()` and then asserts the branding lookup still resolves to the *real* tenant, not the reserved one — the current test suite never connects these two pieces of the phase.

### Warnings

#### WR-01: Concurrent-boot race in reserved-tenant/bootstrap-user seeding can duplicate the tenant or crash-loop the app on every subsequent start

**File:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:389-392` (`seedTenantPlataforma`), `:403-423` (`seedUtilizadorPlataforma`)
**Related:** `backend/src/main/java/com/lexcv/models/Tenant.java:20-21` (`nome` has no `unique = true`), `backend/src/main/java/com/lexcv/models/User.java:29-30` (`email` has `unique = true`)

**Issue:** `seedTenantPlataforma()` is a classic check-then-act find-or-create (`findByNome("LexCV")` → `save(...)` if absent) with no DB-level unique constraint on `Tenant.nome` and no transactional/pessimistic lock around the pair. If two application instances start concurrently against the same empty database (a rolling deploy or any >1-replica startup is a realistic trigger for a "platform" service), both can read `Optional.empty()` before either commits, and both then insert a row named "LexCV" — nothing in the schema prevents it. Once two rows share that name, `TenantRepository.findByNome` (declared to return a single `Optional<Tenant>`) will throw `IncorrectResultSizeDataAccessException` the next time *any* instance boots, since `seedTenantPlataforma()` calls it unconditionally on every startup — i.e., a transient race at first-ever boot can turn into a **permanent startup failure** for the whole application until someone manually deletes the duplicate row.

`seedUtilizadorPlataforma()` has a narrower version of the same race on `userRepository.findByEmail("plataforma@lexcv.cv")`, but there `User.email` *does* carry a real unique constraint, so the losing instance would instead fail its `CommandLineRunner.run()` with an uncaught `DataIntegrityViolationException` on that specific boot (also fatal to that instance's startup, though not a permanent crash-loop like the tenant case since a later, non-concurrent restart would find the winner's row and proceed normally).

Note the pre-existing `upsertRolePermissions` (`DatabaseSeeder.java:373-381`) has the identical structural pattern for `Role`/`Permission`, so this is not a new anti-pattern in this codebase — but applying it to a tenant that's supposed to be a stable, singleton "reserved" identity raises the stakes of getting it wrong, and — unlike roles/permissions, which are pure reference data — a duplicated `Tenant` row can hard-fail every future boot via the `Optional`-returning derived query.

**Fix:** Add a real DB-level unique constraint on `t_tenant.nome` (or, better, a dedicated flag column as suggested in CR-02) and make the seeding methods tolerant of losing the race instead of assuming they never will:

```java
private Tenant seedTenantPlataforma() {
    try {
        return tenantRepository.findByNome("LexCV")
                .orElseGet(() -> tenantRepository.save(Tenant.builder().nome("LexCV").build()));
    } catch (DataIntegrityViolationException raceLost) {
        return tenantRepository.findByNome("LexCV").orElseThrow();
    }
}
```

#### WR-02: `provisionTenant` has a TOCTOU on admin-email uniqueness that surfaces as a raw 500 instead of a clean 400

**File:** `backend/src/main/java/com/lexcv/services/SetupService.java:102-131`, `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java:41-55`

**Issue:** `provisionTenant` checks `userRepository.findByEmail(...).isPresent()` and only *afterward* saves the `Tenant` and the `User` (lines 106-108 vs. 118/128). Unlike `initializeSystem`, which is naturally serialized by `systemSettingRepository.findByIdForUpdate(...)` (a pessimistic lock that means only one caller can ever get past the singleton gate at a time), `provisionTenant` has no such lock — and its whole design point (proven by `SetupServiceProvisionTenantTest`'s "Caso 6", which explicitly asserts repeated/back-to-back calls both succeed) is to be called repeatedly and without serialization. Two concurrent `POST /api/v1/platform/tenants` calls with the same `adminEmail` (a plausible double-submit from a slow admin console UI in Phase 120, or two operators racing to onboard the same client) can both pass the `isPresent()` check, and the loser's `userRepository.save(adminUser)` then throws `DataIntegrityViolationException` against the real unique constraint on `User.email`. Because `provisionTenant` is `@Transactional`, the loser's `Tenant` insert rolls back too (no orphaned data), but `PlatformAdminController.createTenant`'s catch blocks only handle `IllegalArgumentException`/`IllegalStateException` (`PlatformAdminController.java:50-54`) — the `DataIntegrityViolationException` falls through to `GlobalExceptionHandler`'s catch-all, returning `500` with the raw exception class name and message instead of the intended `400 "Já existe um utilizador com este email."`.

**Fix:** Catch `DataIntegrityViolationException` around the `userRepository.save(...)` call in `provisionTenant` (or in `PlatformAdminController.createTenant`) and translate it to the same `IllegalArgumentException`/400 response used for the non-racy case, so the client-visible behavior doesn't depend on timing.

### Info

#### IN-01: No duplicate-name protection when provisioning tenants

**File:** `backend/src/main/java/com/lexcv/services/SetupService.java:102-118`

**Issue:** `provisionTenant` validates admin-email uniqueness but never checks `Tenant.nome` for collisions (and `Tenant.nome` has no DB unique constraint — `Tenant.java:20-21`). A `PLATAFORMA_ADMIN` can create any number of tenants with identical display names, which will be indistinguishable in any future tenant-listing UI (Phase 120) except by `id`. Likely acceptable for this phase's minimal scope (tenant listing/management is explicitly deferred to Phase 120 per `119-CONTEXT.md`), but worth tracking so Phase 120 addresses it rather than assuming names are unique.

**Fix:** Either enforce a case-insensitive uniqueness check in `provisionTenant` (mirroring the email check) or explicitly document that tenant names are non-unique by design before Phase 120 builds a name-based lookup UI on top of it.

#### IN-02: Bootstrap platform-admin credential warning uses `System.out.println` instead of the logger

**File:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:419-421`

**Issue:** The warning that a default-password, maximum-privilege account was just created (`"⚠️ Utilizador de administrador de plataforma criado..."`) is emitted via `System.out.println`, which won't carry a log level, timestamp, or be reliably picked up by log-level-based alerting/aggregation in ops the way an SLF4J `logger.warn(...)` call would. This matches this file's existing style for its other seed messages (lines 72, 304), so it isn't a new deviation introduced by this phase, but given this specific message is arguably the single most security-sensitive line this class ever prints, it's worth promoting to a real logger call as a low-effort improvement.

**Fix:** Add an SLF4J `Logger` to `DatabaseSeeder` (or reuse an existing one if this class already has access to one) and emit this specific message via `logger.warn(...)` instead of `System.out.println`.

---

_Reviewed: 2026-07-29T18:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
