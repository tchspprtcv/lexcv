---
phase: LEXCV-124-cat-logo-de-permiss-es-em-base-de-dados
reviewed: 2026-09-20T21:42:59Z
depth: deep
files_reviewed: 8
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/Permission.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/main/java/com/lexcv/repositories/PermissionRepository.java
  - backend/src/main/java/com/lexcv/config/UserPrincipal.java
  - backend/migrations/124-add-permission-catalogo-columns.sql
  - backend/src/test/java/com/lexcv/seed/DatabaseSeederCatalogoPermissoesTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase LEXCV-124: Code Review Report

**Reviewed:** 2026-09-20T21:42:59Z
**Depth:** deep
**Files Reviewed:** 8
**Status:** issues_found

## Summary

Phase 124 moves the RBAC permission catalogue from a hardcoded 17-entry Java list into `t_permission` (new `rotulo`/`descricao`/`modulo`/`ordem`/`reservada_plataforma` columns), with `DatabaseSeeder.seedRbac()` as a non-destructive upsert source of truth and `AdminController.getRbac()` reading from `PermissionRepository.findAllByReservadaPlataformaFalse()`.

Ran the full new test suite (`DatabaseSeederCatalogoPermissoesTest`, `AdminControllerRbacCatalogoTest`) under JDK 23 — 10/10 pass — and `mvn spotbugs:check` — clean, no findings. The tests genuinely exercise the production code paths (no mocking-away of the logic under test) and would fail against a broken `id`/`nome`-mutating upsert, a swapped `key`/`nome` mapping, or a mis-ordered/incorrectly-filtered catalogue, so they back the claims in the phase's commit messages.

No Critical/Blocker findings: `id`/`nome` are provably never touched on the upsert path, `reservadaPlataforma` defaults are correctly forced to `false` explicitly on every catalogue write (both create and update branches), and the DB-level `NOT NULL DEFAULT TRUE` closes the fail-open gap for any row that arrives outside the seeder. Three Warnings below cover an inaccurate correctness claim in the migration script's own comment, an undocumented boot-time race that this phase's `seedRbac()` introduces (comparable to, but not identical to, the already-accepted `seedTenantPlataforma` race), and a read-side asymmetry between `rolePermissions` and `systemPermissions` that becomes exploitable as soon as a future phase actually marks a catalogue permission reserved.

## Warnings

### WR-01: Migration script's "no exposure window" claim does not hold — Tomcat accepts connections before `CommandLineRunner` runs

**File:** `backend/migrations/124-add-permission-catalogo-columns.sql:20-24`
**Issue:** The comment asserts: "There is no exposure window: the seeder is a CommandLineRunner, it always completes before the HTTP listener starts accepting requests." That is not how Spring Boot's embedded servlet container starts up: `ServletWebServerApplicationContext` starts the web server (`webServer.start()`) as part of `AbstractApplicationContext.refresh()` (`finishRefresh()`), which completes *before* `SpringApplication.callRunners()` invokes `CommandLineRunner` beans. In other words, Tomcat is already accepting connections while `DatabaseSeeder.run()` — and therefore `seedRbac()`, wrapped in a single `@Transactional` method — is still executing (or, on a fresh deploy immediately after this migration runs, before it has even started). Since `run()` is one transaction, none of its writes are visible under READ COMMITTED until it commits.

Net effect during that window: `GET /api/v1/admin/rbac` legitimately returns `systemPermissions: []` (every row is still `reservada_plataforma = TRUE` right after this migration adds the column with `DEFAULT TRUE`, and `findAllByReservadaPlataformaFalse()` filters all of them out). This fails closed (no over-exposure) and self-heals once the seeder transaction commits, so it is not a security hole, but the code comment's factual claim is wrong and could mislead a future engineer into skipping a real (if narrow) race when reasoning about deploy-time behavior.
**Fix:** Correct the comment to describe the actual guarantee ("the app may briefly serve an empty catalogue until the seeder's transaction commits, which happens very early in the boot sequence and self-heals") rather than asserting no window exists. If the empty-catalogue window is unacceptable, consider running `seedRbac()` outside of `ApplicationRunner`/`CommandLineRunner` context (e.g. an `ApplicationListener<ApplicationPreparedEvent>` running before `refresh()` completes) — out of scope for a comment fix alone, but worth flagging.

### WR-02: `seedRbac()`'s find-then-insert loop races on the `t_permission.nome` unique constraint across concurrent instance startups — undocumented, unlike the two sibling races in the same file

**File:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:386-408`
**Issue:** For every one of the 20 catalogue entries, `seedRbac()` does `permissionRepository.findByNome(...).map(update).orElseGet(insert)`. If two backend instances boot concurrently against the same empty/partial database, both can observe `findByNome(...) == empty` for the same key inside their own (uncommitted) transaction and both attempt `INSERT`, tripping the existing `unique = true` constraint on `Permission.nome`. Unlike `seedTenantPlataforma()` and `seedUtilizadorPlataforma()` — which have explicit doc comments reasoning through exactly this class of race and concluding it's an accepted, self-healing residual risk — this method has no such comment, and the failure mode here is arguably worse: because `run()` is one `@Transactional` method and `seedRbac()` is its first statement, a `DataIntegrityViolationException` here rolls back the *entire* boot transaction (tenant seeding, demo data, everything), and because `CommandLineRunner.run()` threw, `SpringApplication.callRunners()`'s failure handling aborts application startup for the losing instance entirely (not just a duplicated row like the tenant case — a hard boot failure). A subsequent, non-concurrent restart of that instance would succeed (the idempotent upsert tolerates it), so it isn't a permanent crash-loop, but it's a startup race this phase introduces without the same risk-acceptance reasoning already established as the pattern in this file.
**Fix:** At minimum, add a doc comment analogous to the ones on `seedTenantPlataforma()`/`seedUtilizadorPlataforma()` explicitly accepting this risk (or explaining why it doesn't apply, e.g. if deployments are guaranteed single-instance). If the risk is not acceptable, catch `DataIntegrityViolationException` per-entry (or wrap the loop body to fall back to a retried `findByNome`), consistent with the "find-first" mitigation already used for `seedTenantPlataforma`.

### WR-03: `rolePermissions` (unfiltered) and `systemPermissions` (filtered) can disagree — a role's permission list can reference keys absent from the catalogue

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:361-393`
**Issue:** `getRbac()` builds `rolePermissions` straight from `role.getPermissions()` (every `Permission.nome` the role actually holds, via `t_role_permission`), with no filtering. `systemPermissions` is separately built from `permissionRepository.findAllByReservadaPlataformaFalse()`, further filtered to drop blank/null `rotulo`. Actual authorization (`JwtAuthenticationFilter`, `UserPrincipal.create`) also reads `Role.getPermissions()` directly and is entirely unaffected by `reservadaPlataforma`/`rotulo` — so a permission that is reserved-to-platform or lacks a label, if ever assigned to a non-platform role, remains a fully live, enforceable authority while simultaneously vanishing from the catalogue offered to the RBAC UI.

Today this is latent, not live: all 20 seeded catalogue entries are simultaneously `reservadaPlataforma = false` and have a non-blank `rotulo`, so no role can currently hold a "phantom" permission. But the whole point of `reservadaPlataforma` (per the `Permission.java` comment, CATL-03) is to support future phases (125/127, per the code comments) actually marking some catalogue permissions reserved — at which point, if any role is ever granted one of those keys (by direct SQL, a future admin-only endpoint, or a bug in `updateRbac`), `getRbac()`'s response would contain a `rolePermissions` entry naming a key with no corresponding `systemPermissions` column, a shape the frontend RBAC matrix (which derives its columns from `systemPermissions`) has no defined behavior for.
**Fix:** Either filter `rolePermissions` values through the same reserved/labeled criteria used for `systemPermissions` (so the API never asserts a role holds a permission key it can't also describe), or explicitly document/test the intended contract for this case now, before phase 125/127 makes `reservadaPlataforma = true` a live state for a catalogue entry.

## Info

### IN-01: Update-path test never asserts `reservadaPlataforma` is reset to `false`

**File:** `backend/src/test/java/com/lexcv/seed/DatabaseSeederCatalogoPermissoesTest.java:152-175`
**Issue:** `seedRbac_numSegundoArranque_actualizaAMesmaInstanciaSemTocarEmIdNemNome` seeds an existing `clientesViewExistente` row and asserts `id`, `nome`, and `rotulo` are handled correctly on update, but never asserts `getReservadaPlataforma()` on that same object — only the *create* path (`seedRbac_noPrimeiroArranque_criaAs20PermissoesComCamposDescritivosPreenchidos`) checks `assertEquals(Boolean.FALSE, clientesView.getReservadaPlataforma())`. The behavior of `existente.setReservadaPlataforma(false)` on line 395 of `DatabaseSeeder.java` — which is explicitly called out as intentional design in the `CATALOGO_PERMISSOES` comment block (a manually-reserved catalogue permission gets un-reserved on the next boot) — is therefore only verified on the insert branch, not the update branch where it actually matters most.
**Fix:** Add `assertEquals(Boolean.FALSE, clientesViewExistente.getReservadaPlataforma());` to the existing update-path test.

### IN-02: `UserPrincipal`'s hardcoded ADMIN permission list and `DatabaseSeeder.CATALOGO_PERMISSOES` have no test enforcing their claimed sync

**File:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java:34-47`
**Issue:** This phase updated the comment above `UserPrincipal`'s hardcoded 20-entry ADMIN permission `Arrays.asList(...)` to read "Keep in sync with `DatabaseSeeder.CATALOGO_PERMISSOES` (Phase 124)" (comment-only change, confirmed by `git log -p`; the array itself is untouched and, checked by hand, its 20 keys do currently match `CATALOGO_PERMISSOES` exactly). No test asserts this equivalence, so the sync is enforced purely by convention/comment. If a future phase adds or renames a `CatalogoEntry` in `DatabaseSeeder` without updating this array, JWT-derived ADMIN authorities would silently drift from the catalogue (an ADMIN either missing a new scope or, on removal, incorrectly retaining a scope no longer in the catalogue) with no compiler or test failure to catch it.
**Fix:** Add a unit test (e.g. reflectively reading `DatabaseSeeder.CATALOGO_PERMISSOES` keys, or duplicating the literal set as an expected constant in a test) that fails if the two lists diverge. Out of scope to fix within this phase given the file only received a comment update, but worth tracking given the comment now makes an explicit, testable claim.

---

_Reviewed: 2026-09-20T21:42:59Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
