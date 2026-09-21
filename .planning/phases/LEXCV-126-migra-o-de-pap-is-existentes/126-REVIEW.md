---
phase: LEXCV-126-migra-o-de-pap-is-existentes
reviewed: 2026-09-21T13:48:26Z
depth: deep
files_reviewed: 32
files_reviewed_list:
  - backend/migrations/127-add-user-tenant-role-table.sql
  - backend/migrations/README.md
  - backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/main/java/com/lexcv/controllers/AuthController.java
  - backend/src/main/java/com/lexcv/controllers/ParecerController.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - backend/src/main/java/com/lexcv/models/User.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - backend/src/main/java/com/lexcv/seed/MigracaoPapeisRunner.java
  - backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java
  - backend/src/main/java/com/lexcv/services/ResolucaoPapeisService.java
  - backend/src/main/java/com/lexcv/services/SetupService.java
  - backend/src/main/java/com/lexcv/services/VerificacaoDerivaPapeisService.java
  - backend/src/test/java/com/lexcv/config/JwtAuthenticationFilterPapeisEscritorioTest.java
  - backend/src/test/java/com/lexcv/config/JwtAuthenticationFilterTenantSuspensoTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerAtribuicaoPapeisEscritorioTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java
  - backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeTenantPlanoTest.java
  - backend/src/test/java/com/lexcv/controllers/AuthControllerGetMeUtilizadoresAtivosTest.java
  - backend/src/test/java/com/lexcv/controllers/AuthControllerLoginLockoutTest.java
  - backend/src/test/java/com/lexcv/controllers/AuthControllerTenantSuspensoTest.java
  - backend/src/test/java/com/lexcv/controllers/ParecerControllerProveniencaPapelTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerProveniencaPapelTest.java
  - backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java
  - backend/src/test/java/com/lexcv/models/UserAssociacaoTenantRoleTest.java
  - backend/src/test/java/com/lexcv/services/MigracaoPapeisEscritorioServiceTest.java
  - backend/src/test/java/com/lexcv/services/ResolucaoPapeisServiceTest.java
  - backend/src/test/java/com/lexcv/services/VerificacaoDerivaPapeisServiceTest.java
findings:
  critical: 1
  warning: 4
  info: 1
  total: 6
status: issues_found
---

# Phase LEXCV-126: Code Review Report

**Reviewed:** 2026-09-21T13:48:26Z
**Depth:** deep
**Files Reviewed:** 32
**Status:** issues_found

## Summary

This phase converges authority resolution onto office-scoped `TenantRole`s behind a single seam (`ResolucaoPapeisService`), backed by a startup-time convergent migration (`MigracaoPapeisEscritorioService`) and an unusually rigorous before/after drift check (`VerificacaoDerivaPapeisService`) that aborts boot on any divergence. The design is careful and the tests for the three new services are genuinely adversarial (negative-space assertions, "which side did the permission come from" checks, multi-user divergence, order-of-operations checks). Ordering between `DatabaseSeeder` and `MigracaoPapeisRunner` is correct (`LOWEST_PRECEDENCE - 100` then `LOWEST_PRECEDENCE`), the `t_user_role` reversibility invariant is respected (never written by this phase's migration code), and the `noneMatch`→`temPapelDeMolde` OR/AND conversions in `ParecerController`/`ResourceController` preserve original semantics correctly.

However, the one safety net this phase built — `VerificacaoDerivaPapeisService` — only runs once, at boot, over users who already exist. It does **not** cover the live write path (`AdminController.createUser`/`updateUser`) that this same phase changed to start writing `tenantRoles`. That write path has an unguarded partial-mapping gap (CR-01) that silently drops permissions an admin explicitly granted, with a 200/201 response implying success. Three further structural issues (WR-01 through WR-03) weaken the "empty `tenantRoles` == platform admin" invariant the whole design leans on, and one is a maintainability landmine (`TenantRole` has no `equals`/`hashCode`, so a `Set.equals()`-based idempotency check silently depends on JPA identity-map behavior that isn't documented as a requirement).

## Critical Issues

### CR-01: AdminController.createUser/updateUser silently drop permissions when only some of the assigned global roles have a matching TenantRole

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:180`, `:219`, `:317-318`

**Issue:** `resolucaoPapeisService.resolverPapeisDeEscritorio(tenantId, roles)` (`ResolucaoPapeisService.java:134-140`) maps each global `Role` to its tenant-homonymous `TenantRole` and silently **drops** any role that has no match — it returns whatever subset matched, never an error, never a partial-match flag. `AdminController` writes that result straight into `user.tenantRoles` (`createUser` line 219, `updateUser` lines 317-318) alongside the full `roles` set that was written unconditionally.

Because `ResolucaoPapeisService.usaPapeisDeEscritorio` (`ResolucaoPapeisService.java:50-52`) branches on "is `tenantRoles` non-empty", any user who ends up with a **non-empty but incomplete** `tenantRoles` set will have their effective permissions computed **exclusively** from that partial set (`resolverPermissoesEfectivas`, `ResolucaoPapeisService.java:82-97`) — the permissions belonging to the unmapped role(s) are gone from the effective authority, even though `user.getRoles()` (the field actually persisted and shown back to the caller) still lists all of them.

Concretely: an admin calls `PUT /api/v1/admin/users/{id}` with `roles: ["ADVOGADO", "NOVO_PAPEL"]` where `NOVO_PAPEL` is a role that became `instanciavel=true` in `t_role` *after* this tenant's `TenantRole`s were already instantiated (recall `MigracaoPapeisEscritorioService.migrar()` only calls `instanciarMoldes` when `t_tenant_role` is **completely empty** for the tenant — `MigracaoPapeisEscritorioService.java:96-98` — so a molde added later is never retro-instantiated for an already-converted tenant). The response is `200 OK`, `user.roles` shows both roles, but the user's effective permission set silently contains only `ADVOGADO`'s permissions. Nothing is logged (contrast with `MigracaoPapeisEscritorioService.java:104-107`, which does `log.warn` for the *all-empty* case — the *partial* case gets no such treatment anywhere in the codebase).

This is exactly the class of bug `VerificacaoDerivaPapeisService` was built to catch — but that verifier only runs once, at boot, over the set of users being converted by `migrar()`. It is never invoked from the live admin write path this same phase modified. A multi-role user created or edited through `AdminController` after boot has zero drift protection.

**Fix:** Either (a) make partial mapping a hard failure the caller sees (reject the request with 400/409 naming the unmapped role, forcing the admin to fix the molde catalog first), or (b) make `resolverPapeisDeEscritorio` all-or-nothing (return empty, not a partial set, unless every role maps) so the existing `usaPapeisDeEscritorio` empty-set fallback safely degrades to global-role resolution instead of silently truncating permissions:

```java
// ResolucaoPapeisService.resolverPapeisDeEscritorio
public Set<TenantRole> resolverPapeisDeEscritorio(UUID tenantId, Collection<Role> papeisGlobais) {
    Set<TenantRole> encontrados = papeisGlobais.stream()
            .map(role -> tenantRoleRepository.findByTenantIdAndNome(tenantId, role.getNome()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toSet());
    if (encontrados.size() != papeisGlobais.size()) {
        // partial coverage is unsafe to apply -- fail closed to the global-role fallback
        // rather than silently truncating the effective permission set.
        return Set.of();
    }
    return encontrados;
}
```
At minimum, log a warning identical in spirit to `MigracaoPapeisEscritorioService.java:104-107` whenever `AdminController` observes `tenantRoles.size() < roles.size()`, so this is at least observable in production.

## Warnings

### WR-01: "Empty `tenantRoles` means platform admin" is not actually true — it is also the state of every freshly provisioned tenant's founding admin until the next reboot

**File:** `backend/src/main/java/com/lexcv/services/ResolucaoPapeisService.java:44-52`, `backend/src/main/java/com/lexcv/services/SetupService.java:87-100`, `:116-147`, `:180-195`

**Issue:** The single most load-bearing comment in the phase (`ResolucaoPapeisService.java:44-48`) asserts: "Um utilizador sem nenhum papel de escritorio (o administrador de plataforma, permanentemente...) cai silenciosamente para os papeis globais." In practice this is false for two other, very reachable cases:

1. `SetupService.initializeSystem()` (the public `/setup/initialize` first-run wizard, lines 57-100) builds the tenant and its admin user with only `.roles(Set.of(adminRole))` and **never calls `instanciarMoldes`** at all — unlike its sibling `provisionTenant()` (lines 116-147), which does call it at line 144. The tenant created via the public wizard has **zero** `TenantRole`s until the next application restart runs `MigracaoPapeisEscritorioService.migrar()` and notices `t_tenant_role` is empty for it.
2. Even `provisionTenant()`, which *does* instantiate the tenant's `TenantRole` catalog, never assigns any of them to the founding admin user it just created (`instanciarMoldes` only creates the tenant's `TenantRole` rows — see the class doc at `SetupService.java:168-172`, point 4, which states this is deliberate). That admin's `tenantRoles` stays empty until a subsequent boot's `migrar()` run converts them (or someone calls `PUT /admin/users/{id}` on that specific user).

Both cases mean a legitimate, brand-new office administrator — not the platform admin, not in the reserved `ALCv` tenant — takes the exact same "empty `tenantRoles` ⇒ resolve via global roles" branch that the comment claims is exclusive to `plataforma@lexcv.cv`. Nothing in `usaPapeisDeEscritorio` narrows this by tenant identity (e.g. checking the reserved tenant name), as the design intent implies it should. Today this is functionally harmless only because nothing in the codebase lets a tenant customize a `TenantRole`'s permissions independently of the global `Role` it was snapshotted from (no such endpoint exists yet), so the global and tenant-role permission sets can't diverge in practice — but the invariant the comments assert is not what the code enforces, and the very next phase that adds any TenantRole-specific customization (explicitly anticipated by `TenantRole.java:31-38`) will make this a real, silent authority mismatch for founding admins and any tenant onboarded through the public wizard, for however long elapses until the next deploy/restart.

**Fix:** Narrow the fallback explicitly by tenant identity rather than by "collection happens to be empty", e.g.:
```java
private boolean usaPapeisDeEscritorio(User user) {
    return user.getTenantRoles() != null && !user.getTenantRoles().isEmpty();
}
```
should be paired with either (a) having `SetupService.initializeSystem`/`provisionTenant` populate the founding admin's `tenantRoles` immediately (mirroring what `MigracaoPapeisEscritorioService` does), so "empty" genuinely only ever describes the platform admin, or (b) explicitly asserting/logging when a non-`ALCv`-tenant user resolves via the fallback branch, so the gap is observable rather than silent.

### WR-02: `TenantRole` has no `equals`/`hashCode` override, so the migration's idempotency check depends on undocumented JPA identity-map behavior

**File:** `backend/src/main/java/com/lexcv/models/TenantRole.java:20-54`, `backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java:110`

**Issue:** `Role` explicitly declares `@EqualsAndHashCode(of = "nome")` (`Role.java:15`). `TenantRole` declares no such annotation, so it falls back to `Object`'s identity-based `equals`/`hashCode`. `MigracaoPapeisEscritorioService.migrar()` relies on `Set<TenantRole>` equality to decide whether a user is "already converted" and should be skipped (`alvo.equals(user.getTenantRoles())`, line 110) — the class-level doc even markets this as the property that makes the whole migration "convergent, not one-shot" (`MigracaoPapeisEscritorioService.java:35-38`).

This only works because, within a single `@Transactional` method, Hibernate's persistence-context identity map guarantees that two independent queries returning a row with the same PK return the *same Java object instance* — so default `Object` reference-equality happens to coincide with entity-identity equality here. That is a real JPA guarantee, but it is an implicit one: nothing documents that `migrar()`'s idempotency depends on it, and it is exactly the kind of invariant a future refactor (splitting the loop across two `@Transactional` boundaries, introducing `EntityManager.clear()`/`.detach()`, batching via a stateless session, or moving any of this to a native/JDBC query) would silently break — the check would then always evaluate to "different", making every restart write every user again. That's not a security bug by itself (rewriting the same target set is harmless), but it silently defeats a guarantee explicitly claimed in the code's own documentation and tests (`MigracaoPapeisEscritorioServiceTest.java` Caso 5), and there's no test that would catch the regression since the test suite also relies on the same coincidental object-identity sharing (same `TenantRole` instance stubbed into both the mock return value and the user's `tenantRoles`, `MigracaoPapeisEscritorioServiceTest.java:190-205`).

**Fix:** Add `@EqualsAndHashCode(of = {"tenantId", "nome"})` to `TenantRole` (matching the class's own documented unique constraint at `TenantRole.java:14`), consistent with the pattern already used for `Role`. This makes the idempotency check correct by construction instead of by persistence-context accident.

### WR-03: `MigracaoPapeisRunner`/`DatabaseSeeder` run after the embedded servlet container has already started accepting requests

**File:** `backend/src/main/java/com/lexcv/seed/MigracaoPapeisRunner.java:26-38`, `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:24-48`

**Issue:** Spring Boot starts the embedded web server during `ApplicationContext` refresh, before `CommandLineRunner` beans execute (`SpringApplication.run()` calls `context.refresh()` — which starts Tomcat — and only afterwards invokes registered runners). That means there is a real window, on every boot, during which the HTTP API is reachable while `DatabaseSeeder`/`MigracaoPapeisRunner` are still running or haven't started. A request landing in that window against a tenant that hasn't yet been converged (e.g. `AdminController.createUser`/`updateUser` for a newly-onboarded tenant, or any tenant whose `TenantRole` catalog isn't yet instantiated) will observe `resolverPapeisDeEscritorio` returning an empty/partial set purely due to timing, not due to any real data problem — compounding WR-01/CR-01 with a timing dimension that's easy to miss in local testing (where the window is typically sub-second) but real in slower production startups.

This is not flagged as a correctness bug in the read paths (`JwtAuthenticationFilter`, `ResolucaoPapeisService`) because they fail safe to the global-role fallback. It is flagged here because it widens the reachable window for CR-01 and because `MigracaoPapeisEscritorioService.migrar()` runs one long transaction across every tenant with no external readiness gate — a slow migration on a large install extends the window during which admin writes can race the conversion.

**Fix:** Out of scope to fully solve here, but worth a readiness probe (`/actuator/health` `ApplicationAvailability`-based) that the deployment's load balancer/orchestrator waits on before routing traffic, so the window is closed operationally rather than left as an accepted race.

### WR-04: `resolverPapeisDeEscritorio`'s "no correspondence" case and the "partial correspondence" case are indistinguishable to every caller

**File:** `backend/src/main/java/com/lexcv/services/ResolucaoPapeisService.java:128-140`

**Issue:** This is the root cause shared by CR-01 and part of WR-01/WR-02: `resolverPapeisDeEscritorio` returns `Set.of()` both when *no* role mapped and when the caller passed zero roles, and returns a *silently partial* set when *some but not all* roles mapped. Every one of its three current callers (`AdminController.createUser`, `AdminController.updateUser`, `MigracaoPapeisEscritorioService.migrar`) treats "empty" as a meaningful, checkable signal (`alvo.isEmpty()` / falls through to the `usaPapeisDeEscritorio` fallback) but has no way to detect "partial" at all — and `migrar()` is the only caller effectively protected against it (accidentally, by `VerificacaoDerivaPapeisService` catching the resulting drift for any multi-role user, since drift-checking is unconditional there). This is a design smell independent of any single call site: the method's contract doesn't distinguish three genuinely different outcomes ("nothing to do", "fully converted", "partially converted — unsafe") with three different correct caller behaviors.

**Fix:** See CR-01's suggested fix — making the method all-or-nothing (or returning a richer result type distinguishing "complete"/"partial"/"empty") removes the ambiguity for all current and future callers at once, rather than patching each call site individually.

## Info

### IN-01: `SetupService.initializeSystem` and `SetupService.provisionTenant` diverge on whether they call `instanciarMoldes`, undocumented

**File:** `backend/src/main/java/com/lexcv/services/SetupService.java:57-100`, `:116-147`

**Issue:** `provisionTenant` (the `PLATAFORMA_ADMIN`-gated path) explicitly instantiates the tenant's `TenantRole` catalog at creation time (line 144); `initializeSystem` (the public first-run wizard) does not, leaving the new tenant fully dependent on the next `MigracaoPapeisRunner` boot to converge. Nothing in either method's Javadoc calls out or justifies this asymmetry — a reader has to infer it's "fine" because the migration is convergent (per `MigracaoPapeisEscritorioService`'s own doc), but that inference isn't written down next to the divergent code, and it directly feeds WR-01.

**Fix:** Add a short comment at `initializeSystem` cross-referencing `MigracaoPapeisEscritorioService` and explaining why it's safe to defer `instanciarMoldes` to the next boot here but not in `provisionTenant` — or, for consistency and to close the WR-01 window sooner, call `instanciarMoldes(tenant.getId())` here too.

---

_Reviewed: 2026-09-21T13:48:26Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
