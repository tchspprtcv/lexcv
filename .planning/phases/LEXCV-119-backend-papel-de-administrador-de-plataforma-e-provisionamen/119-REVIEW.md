---
phase: LEXCV-119-backend-papel-de-administrador-de-plataforma-e-provisionamen
reviewed: 2026-07-29T19:30:00Z
depth: deep
files_reviewed: 7
files_reviewed_list:
  - backend/src/main/java/com/lexcv/repositories/TenantRepository.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - backend/src/main/java/com/lexcv/services/SetupService.java
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
  - backend/src/main/java/com/lexcv/controllers/PublicController.java
  - backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java
findings:
  critical: 0
  warning: 1
  info: 2
  total: 3
status: issues_found
---

# Phase LEXCV-119: Code Review Report

**Reviewed:** 2026-07-29T19:30:00Z
**Depth:** deep
**Files Reviewed:** 7 (`backend/src/main/java/com/lexcv/dtos/TenantProvisionResponse.java` was additionally
read in full for cross-file context, as `PlatformAdminController` builds it, but is not counted in the
formal scope below since it wasn't part of the `files` list for this pass)
**Status:** issues_found (0 blocking — see Final Verdict)

## Summary

This is the fix-verification re-review of Phase 119, covering the same files after a single fix pass that
addressed all 5 findings from the previous `119-REVIEW.md` (2 Critical, 2 Warning, 1 of 2 Info items). Rather
than trusting the fix commits' own descriptions, every fix was independently re-derived: I re-read all 7
files in full plus `TenantProvisionResponse.java`/`UserPrincipal.java`/`SecurityConfig.java`/`AuthController.java`/
`SetupController.java`/`Tenant.java`/`User.java` for cross-file context, diffed each of the five fix commits
individually (`git show <hash> --stat`, and the full diff for the two most structurally significant ones),
re-ran the compiler and the full automated test suite, ran SpotBugs/FindSecBugs, re-audited the whole backend
for related authorization/repository call sites the fixes depend on, checked both frontend apps (`web/`,
`webpage/`) for consumers that could be broken by the behavioral change in `PublicController`, and
cross-referenced this codebase's two most relevant prior reviews (`98-REVIEW.md`, `117-REVIEW.md`) so
severity judgments stay consistent with precedent rather than re-litigating already-triaged patterns from
scratch.

**CR-01 — verified FIXED.** Commit `6485fd93` (47 insertions / 8 deletions, `AdminController.java` only — no
other file touched). `createUser` (`AdminController.java:189-194`) and `updateUser`
(`AdminController.java:309-314`) now reject a `"permissions"` array containing either the raw string
`"PLATAFORMA_ADMIN"` (`PAPEL_PLATAFORMA`, `:52`) or the actually-dangerous prefixed form
`"ROLE_PLATAFORMA_ADMIN"` (`PAPEL_PLATAFORMA_AUTORIDADE`, `:60` = `"ROLE_" + PAPEL_PLATAFORMA`), with a `403`
returned *before* the pre-existing, still-present `permissions.add((String) pObj)` loop (`:196-199`,
`:316-319`) ever runs — i.e. before the free-form string can reach `User.permissions` at all. This closes the
exact reproduction from the original finding (`PUT /api/v1/admin/users/{ownUserId}` with
`{"permissions": ["ROLE_PLATAFORMA_ADMIN"]}`). Verified via the 14-case `AdminControllerPlataformaAdminContencaoTest`
(Casos 9–14 are new, added by this same commit, and reproduce the exact bypass plus mixed-array and
non-regression variants for both `createUser`/`updateUser`); all 14 pass. I also independently re-audited
`grep -rn "hasRole(" backend/src/main` end-to-end: `hasRole('ADMIN')` (class-level on `AdminController`,
already held by the only actor who can reach this code) and `hasRole('PLATAFORMA_ADMIN')` (`PlatformAdminController`)
are the *only two* role-based checks anywhere in this backend — every other `@PreAuthorize` is
`hasAuthority('scope:action')`, which the free-form `permissions` field is legitimately allowed to grant by
design. So today, this two-string denylist is a functionally complete closure of the reachable escalation
surface. See **WR-03** below for a residual, non-blocking observation about how this fix was implemented
relative to what the original finding recommended.

**CR-02 — verified FIXED**, including the specific double-check requested for this round. Commit `55167365`
(net −55 lines across `PublicController.java`/`TenantRepository.java`, plus a rewritten `PublicControllerTest.java`).
`TenantRepository.findFirstByOrderByCreatedAtAsc()` was deleted outright (not deprecated, not left dead) —
confirmed by reading the current `TenantRepository.java` (25 lines, single method `findFirstByNome`) and by
`grep -rn "findFirstByOrderByCreatedAtAsc"` across the entire repo: the only remaining hits are historical
Javadoc prose in `PublicController.java`/`PublicControllerTest.java` explaining *why* the old approach was
abandoned, and planning/markdown history — zero executable call sites anywhere. `PublicController.getBranding()`
(`:33-41`) now takes no constructor args, injects no repository, and unconditionally returns
`TenantPublicInfoResponse{nome="LexCV", logoDataUrl=null}`. Specifically verifying the "no other code path
depended on this" claim:
- `webpage/src/lib/branding.ts` is the *only* real consumer of `GET /api/v1/public/branding` in the repo
  (confirmed via repo-wide grep for `public/branding`/`fetchBranding`). Its own hardcoded
  `FALLBACK = { nome: "LexCV", logoDataUrl: null }` is now byte-identical to what the backend always returns —
  so the dynamic fetch became a no-op with respect to output, not a behavior change for this consumer, and its
  `if (!response.ok) return FALLBACK` branch is simply never taken anymore (the endpoint can no longer 404).
  `BrandMark`/`HeroSection`/`SiteFooter`/`SiteHeader` all just render whatever `nome`/`logoDataUrl` they're
  given, with their own `"LexCV"` fallback baked in too — no special-casing anywhere that could misbehave now
  that the value is always generic.
- `web/` (the actual tenant-facing app) does **not** call `/public/branding` at all
  (`grep -rn "public/branding" web/src` → no matches). `web/src/lib/setup.ts`'s setup-redirect gate calls only
  `/api/v1/setup/status` (`SetupController`, untouched by this phase). Tenant-specific branding inside the
  authenticated app comes from `GET /auth/me` (`AuthController.java:169-174`):
  `tenantRepository.findById(principal.getTenantId())` resolves the *caller's actual tenant* from the
  JWT-derived principal, populating `tenant_nome`/`tenant_logo_data_url` — a genuinely different,
  tenant-correct code path, completely unaffected by removing the "oldest tenant" heuristic. This directly
  corroborates the Javadoc's own claim (`PublicController.java:26-27`).
- One intentional behavior change, not a regression: pre-fix, `getBranding()` returned `404` when no tenant
  existed yet (system not initialized); post-fix it always returns `200`. Confirmed no consumer depended on
  that specific `404` — `webpage/`'s fetch treats any non-`200` identically (falls back to the same constant),
  and nothing in `web/` reads this endpoint at all.
- `PublicControllerTest.java` was rewritten in the same commit to match (`new PublicController()`, no mocks,
  two tests asserting the constant response); both pass. `SecurityConfig`'s `permitAll()` allowlist entry for
  this path is correctly untouched (the endpoint still exists, unauthenticated, so the entry is still needed).
  The now-unnecessary `@Transactional(readOnly = true)` (added in Phase 100 specifically to stream the `@Lob`
  `logoDataUrl` column) was also correctly removed along with the `Tenant`/`TenantRepository` imports and the
  `@Slf4j`/`@RequiredArgsConstructor` annotations — a clean, complete removal, not a partial one.

**WR-01 — verified FIXED.** Commit `27ae731c`. `TenantRepository.findByNome` → `findFirstByNome`
(`TenantRepository.java:24`), consumed by `DatabaseSeeder.seedTenantPlataforma()` (`:405-408`). Confirmed
`Tenant.nome` genuinely has no `unique = true` (`Tenant.java:20-21`, only `nullable = false`), so the
described concurrent-boot duplicate-row race is real, and `findFirst...` (translating to `LIMIT 1`, never
throwing `IncorrectResultSizeDataAccessException`) is an appropriate, minimal mitigation that converts a
permanent crash-loop into "ignore the extra row." `DatabaseSeederPlataformaAdminTest`'s
`run_numSegundoArranqueComSeedEnabled_naoRecriaTenantNemUtilizador` stubs `findFirstByNome` directly and
passes, along with all 5 cases in that file (including the `InOrder`-verified "read counts before inserting"
regression guard this same commit also touches, `:51-55`).

**WR-02 — verified FIXED**, and verified more rigorously than the unit test alone proves. Commit `56c2fa8e`
(14 insertions, 0 deletions — purely additive to `PlatformAdminController.java`). A new
`catch (DataIntegrityViolationException ex)` (`:55-68`) translates the loser of a concurrent-`adminEmail`
race into the same `400 {"message": "Já existe um utilizador com este email."}` as the non-racy case. The
existing Mockito-based test (`PlatformAdminControllerTest.createTenant_comDataIntegrityViolationExceptionDevolve400ComMensagemDeEmailDuplicado`)
only proves the *controller's* handling once that exception type is thrown — it doesn't prove Spring/Hibernate
actually throws *that* type (as opposed to, say, `TransactionSystemException`) for a constraint violation that
only surfaces at commit-time flush, which is the scenario the fix's own comment describes (`User`/`Tenant`
both use `GenerationType.UUID`, so `INSERT` is deferred past `save()` to the `@Transactional` proxy's
commit boundary). Because getting this wrong would mean the fix silently doesn't work in the one scenario it
targets, I went further than reading code: I extracted and disassembled the actual `spring-orm` classes this
Spring Boot 3.4.1 stack resolves (`JpaTransactionManager`, `HibernateJpaDialect`, via `javap` against the
locally-cached jar) and confirmed the real bytecode path — `EntityTransaction.commit()` failing at flush time
throws `jakarta.persistence.RollbackException`; `JpaTransactionManager.doCommit()` unwraps its cause and
calls `JpaDialect.translateExceptionIfPossible(cause)` before ever falling back to `TransactionSystemException`;
`HibernateJpaDialect.convertHibernateAccessException(...)` has an unconditional, translator-independent
`instanceof org.hibernate.exception.ConstraintViolationException` branch that constructs
`org.springframework.dao.DataIntegrityViolationException` directly. So `DataIntegrityViolationException` is
indeed what reaches `PlatformAdminController.createTenant`'s new catch block in the real race, not just in
the mock. All 9 `PlatformAdminControllerTest` cases pass (Group A direct-call cases including this one, and
Group B's real `@PreAuthorize` AOP-proxy cases).

**IN-02 — verified FIXED.** Commit `2740e41f` (10 insertions, 1 deletion, `DatabaseSeeder.java` only).
`@Slf4j` added to the class; the platform-admin bootstrap-credential warning
(`DatabaseSeeder.java:451-453`) now goes through `log.warn(...)` instead of `System.out.println`. Confirmed
this is the only seed message changed — the file's other `System.out.println`s (`:74`, `:306`) are
unchanged, consistent with the finding's narrow, deliberately-scoped recommendation.

**IN-01 — unchanged, still open by design.** Not part of this fix pass (the task brief for this round listed
CR-01/CR-02/WR-01/WR-02/IN-02 only). Re-confirmed `SetupService.provisionTenant` (`:102-131`) still checks
only `adminEmail` uniqueness (`:106-108`), never `Tenant.nome`, and `Tenant.nome` still has no DB constraint.
Carried forward unchanged below.

**Regression check — clean, verified with real tool runs, not just reading:**
- `cd backend && mvn -o -DskipTests compile` and `mvn -o test-compile`: both clean (exit 0), confirming no
  dangling references anywhere to the deleted `findFirstByOrderByCreatedAtAsc`/renamed `findByNome`, and that
  `PublicController`'s new no-arg shape and `PlatformAdminController`'s new catch clause compile against
  every caller/test in the tree.
- `cd backend && mvn -o test` (full unit suite; Surefire's default `**/*Test.java` inclusion correctly skips
  the three DB-dependent `*IT.java` integration tests, matching this repo's `maven-failsafe-plugin` convention):
  **135 tests across 14 classes, 0 failures, 0 errors** — including all 5 fix-targeted test classes
  (`PublicControllerTest` 2, `DatabaseSeederPlataformaAdminTest` 5, `AdminControllerPlataformaAdminContencaoTest`
  14, `SetupServiceProvisionTenantTest` 9, `PlatformAdminControllerTest` 9) and the adjacent, same-file
  regression-risk classes (`AdminControllerLimiteUtilizadoresTest` 9, `SetupControllerSingletonRegressaoTest` 3,
  `AuthControllerGetMeTenantPlanoTest` 4). `PesquisaControllerTest`'s and `AlertasDiariosJobTest`'s printed
  stack traces are their own intentional failure-isolation assertions (matches this user's own prior note
  about the local `unaccent`-extension gap), not failures — both report fully green.
- `cd backend && mvn -o spotbugs:check` (SpotBugs + FindSecBugs): **0 bug instances, 0 errors** — clean SAST
  across the whole backend, not just the touched files.
- `git show --stat` on all five fix commits confirms each is minimal and correctly scoped: CR-01 touches only
  `AdminController.java` (+its test); CR-02 touches only `PublicController.java`+`TenantRepository.java`
  (+its test); WR-01 touches only `TenantRepository.java`+`DatabaseSeeder.java` (+its test); WR-02 touches
  only `PlatformAdminController.java` (+its test); IN-02 touches only `DatabaseSeeder.java`. No fix commit
  incidentally changed an unrelated file.
- `GlobalExceptionHandler.java` and `TenantProvisionResponse.java` are untouched by this fix pass; both
  re-confirmed still correct on their own terms — the `AccessDeniedException → 403` handler
  (`:65-71`) still requires the parent-class catch (matches Spring Security 6.4's actual thrown subclass) and
  never echoes `ex.getMessage()`; `TenantProvisionResponse` is still the only thing
  `PlatformAdminController.createTenant` ever serializes, re-confirmed by
  `PlatformAdminControllerTest.createTenant_devolve201ComIdENomeSemEntidadeCrua`'s explicit
  `assertFalse(response.getBody() instanceof Tenant)`.

## Final Verdict

**Phase 119 (backend papel de administrador de plataforma e provisionamento) is APPROVED — no blockers.**

- 0 open Critical findings. Both CR-01 and CR-02 are verified fixed, correct, and complete against their
  exact original reproductions, re-derived independently (not taken on faith) via source reading, a targeted
  repo-wide authorization audit, and (for CR-02) explicit verification that no other code path — in either
  frontend app or elsewhere in the backend — assumed the old "oldest tenant" semantics.
- 0 open blocking Warnings. WR-01 and WR-02 are verified fixed and complete; WR-02 was additionally verified
  at the bytecode level against the actual Spring/Hibernate exception-translation chain, since the existing
  test only proves the fix at the mock boundary. The one Warning still open (**WR-03**, new this round) is a
  forward-looking hardening suggestion about *how* CR-01 was fixed, not evidence that CR-01 is still
  exploitable — confirmed today's codebase has no second reachable escalation path this pattern misses.
- 0 open Info items requiring a code change this phase. IN-02 is verified fixed. IN-01 is explicitly deferred
  by design (unchanged, tracked for Phase 120). **IN-03** (new this round) is a cross-reference to an
  already-known, already-accepted pattern (`117-REVIEW.md` IN-05) — not a regression, not new risk, filed only
  for completeness since this round specifically re-examined every line CR-01 touches.
- Automated gates re-run fresh, not taken on faith: compile clean, 135/135 unit tests green (including 39
  tests across the five fix-specific classes plus 16 adjacent regression-risk tests), SpotBugs/FindSecBugs
  clean, all five fix commits confirmed minimally-scoped via `git show --stat`.

No further fix iteration is required for this phase. WR-03 and IN-03 below are recorded for traceability and
for whichever phase next touches `AdminController.java`'s permission-handling or role catalog — not as
pre-conditions for shipping Phase 119.

## Narrative Findings (AI reviewer)

### Warnings

#### WR-03: CR-01's fix closes the reported bypass with a two-string denylist rather than the systemic validation the original finding recommended

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:189-194` (createUser),
`:309-314` (updateUser)
**Related:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java:49-51`, `AdminController.java:52,60`
(`PAPEL_PLATAFORMA`/`PAPEL_PLATAFORMA_AUTORIDADE`)

**Issue:** The original CR-01 finding's primary recommended fix was to validate `permissions` against the
known `Permission` catalog, exactly like `roles` already are (`permissionRepository.findByNome(...)`), with
stripping any `^ROLE_.*`-shaped string in `UserPrincipal.create` offered as secondary defense-in-depth. The
fix actually shipped does neither: it adds a literal-string denylist that rejects exactly
`"PLATAFORMA_ADMIN"` and `"ROLE_PLATAFORMA_ADMIN"` in the `permissions` array, then still accepts *any other*
arbitrary string into `User.permissions` unchanged (`:196-199`, `:316-319` — pre-existing, untouched by this
fix). This closes the literal reported exploit — re-confirmed by an exhaustive `hasRole(` audit: today,
`hasRole('ADMIN')` and `hasRole('PLATAFORMA_ADMIN')` are the *only* role checks in the entire backend, and the
former is already held by the only actor who can reach this code, so there is currently no third
`"ROLE_<X>"` an attacker could mint via this field that would grant anything they don't already have. But the
underlying root cause — `UserPrincipal.create` turning arbitrary `permissions` strings into raw
`GrantedAuthority` objects with no `"ROLE_"`-prefix hygiene (`UserPrincipal.java:49-51`) — is unchanged. The
comment block directly above this fix (`AdminController.java:50-51`) already flags that "Phase 121 (ISOL-03)
irá depois fechar o endpoint PUT /rbac... a papéis de plataforma," i.e. more platform-scoped gating is coming;
each new `hasRole('<X>')` a future phase adds will need this exact denylist manually extended, or it silently
reopens the same class of bypass for the new role.

**Fix:** Not required to ship Phase 119 (verified functionally complete today), but before Phase 121 adds
another `hasRole(...)`-gated surface, prefer one of the two originally-recommended, self-maintaining
approaches instead of (or in addition to) the denylist:
```java
// Option A: validate against the real catalog, exactly like roles already are.
List<?> permsList = body.containsKey("permissions") ? (List<?>) body.get("permissions") : Collections.emptyList();
Set<String> permissions = new HashSet<>();
for (Object pObj : permsList) {
    String permName = (String) pObj;
    permissionRepository.findByNome(permName).ifPresent(p -> permissions.add(p.getNome()));
}
```
```java
// Option B: defense-in-depth at the authority-minting boundary itself — never let a free-form
// permission string mint a synthetic role, regardless of which controller writes User.permissions.
permissions.stream()
        .filter(p -> !p.startsWith("ROLE_"))
        .map(SimpleGrantedAuthority::new)
        .forEach(authorities::add);
```

### Info

#### IN-01: No duplicate-name protection when provisioning tenants (carried forward, unchanged)

**File:** `backend/src/main/java/com/lexcv/services/SetupService.java:102-131`

**Issue:** Unchanged since the original review; not part of this fix pass. `provisionTenant` validates
`adminEmail` uniqueness (`:106-108`) but never checks `Tenant.nome` for collisions, and `Tenant.nome` still
has no DB unique constraint (`Tenant.java:20-21`). A `PLATAFORMA_ADMIN` can create any number of tenants with
identical display names, indistinguishable in a future tenant-listing UI except by `id`. Re-confirmed this
round that tenant-listing/management is still explicitly deferred to Phase 120 per `119-CONTEXT.md`, so this
remains acceptable for this phase's minimal scope.

**Fix:** Either enforce a case-insensitive uniqueness check in `provisionTenant` (mirroring the email check)
or explicitly document that tenant names are non-unique by design before Phase 120 builds a name-based lookup
UI on top of it.

#### IN-03: `AdminController`'s unvalidated `Map`-cast pattern (already tracked as `117-REVIEW.md` IN-05) remains reachable via `"password"` and via the two `permissions`-list casts CR-01's new guard now sits in front of

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:137-138` (createUser password),
`:271` (updateUser password), `:196-199`/`:316-319` (permissions list, pre-existing, unchanged by CR-01)

**Issue:** Not a regression and not newly introduced by Phase 119 — filed here only because this round's
line-by-line re-verification of CR-01 walked through this exact code and it's directly relevant to that
fix's completeness. `117-REVIEW.md`'s **IN-05** already identified and explicitly, deliberately deferred
("optional, low-priority... best done together with a broader input-validation pass") this general pattern —
`Map<String, Object>` values cast straight to their expected type with no `instanceof` check — for
`nome`/`email`/`telefone`/`roles`/`permissions`. Two things worth adding to that existing, accepted
disposition rather than re-opening it as new: (1) `"password"` belongs on that list too — `createUser`
(`:137-138`) calls `.matches(...)` directly on the cast value with no null guard, and `updateUser` (`:271`)
calls `.trim()` the same way, so an explicit JSON `"password": null` NPEs (caught only by
`GlobalExceptionHandler`'s catch-all, returning `500` with the JDK's "helpful NPE" message instead of a clean
`400`) rather than just risking a `ClassCastException` like the other fields; (2) CR-01's new denylist checks
(`PAPEL_PLATAFORMA.equals(pObj)`, `:189`/`:309`) are themselves safe against this — `String.equals(Object)`
never throws regardless of `pObj`'s runtime type or nullness — but they run immediately before the
pre-existing unsafe `permissions.add((String) pObj)` (`:198`/`:318`), so the pattern IN-05 already flagged is
still there, unchanged, right next to the new fix.

**Fix:** No action needed to ship Phase 119, consistent with IN-05's existing disposition. If/when that
broader input-validation pass happens, extend it to `"password"` too:
```java
if (!(body.get("password") instanceof String password) || password.isBlank()) {
    return ResponseEntity.badRequest().body(Map.of("message", "Nome, email, password e roles são obrigatórios."));
}
```

---

_Reviewed: 2026-07-29T19:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
