---
phase: 126-migracao-de-papeis-existentes
plan: 03
subsystem: auth

tags: [rbac, authorization, spring-boot, mockito, migration, command-line-runner]

# Dependency graph
requires:
  - phase: 126-01
    provides: "User.tenantRoles association (t_user_tenant_role), EAGER, coexisting with User.roles"
  - phase: 126-02
    provides: "ResolucaoPapeisService (resolverPapeisDeEscritorio seam) + VerificacaoDerivaPapeisService (capturarAntes/verificarSemDeriva)"
provides:
  - "MigracaoPapeisEscritorioService.migrar(): converging per-tenant conversion, reusing SetupService.instanciarMoldes, skipping the reserved ALCv tenant, verifying zero drift in the same transaction"
  - "MigracaoPapeisRunner: boot-time CommandLineRunner, @Order(LOWEST_PRECEDENCE), runs unconditionally including in production, uncaught exception aborts boot"
  - "DatabaseSeeder @Order(LOWEST_PRECEDENCE - 100): explicit boot ordering so seedRbac() converges Role.instanciavel before the conversion runs"
  - "SetupService.instanciarMoldes promoted to package-private for reuse, zero duplication of the instantiation loop"
  - "9 Mockito tests proving happy-path conversion, reversibility, convergence (both directions), idempotency, reserved-tenant skip, no-equivalent-role safe fallback, drift abort, and capture-before-write ordering"
affects: [126-04, 126-05]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Converging boot-time data migration: guarded by an emptiness check per resource (t_tenant_role) and an equality check per row (alvo.equals(current)), so a second run is a true no-op", "Two CommandLineRunners ordered explicitly via @Order rather than relying on bean-definition order for an authorization-sensitive dependency"]

key-files:
  created:
    - backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java
    - backend/src/main/java/com/lexcv/seed/MigracaoPapeisRunner.java
    - backend/src/test/java/com/lexcv/services/MigracaoPapeisEscritorioServiceTest.java
  modified:
    - backend/src/main/java/com/lexcv/services/SetupService.java
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java

key-decisions:
  - "instanciarMoldes changed from private to package-private only -- body, signature, and constructor untouched; a 5th invariant paragraph was added to its doc-comment recording why (reused by MigracaoPapeisEscritorioService, no second implementation exists anywhere else)."
  - "MigracaoPapeisEscritorioService.migrar() carries all six collaborators from ResolucaoPapeisService and VerificacaoDerivaPapeisService (real) plus SetupService (mocked in tests, already proven) -- never a duplicate instantiation loop, never a duplicate drift check."
  - "Convergence is enforced by two independent guards: tenantRoleRepository.findByTenantId(tenantId).isEmpty() gates re-instantiation of moldes (would otherwise violate the (tenant_id, nome) unique constraint), and alvo.equals(user.getTenantRoles()) gates re-saving a user already at the correct target set."
  - "The same User object references flow from capturarAntes (via userRepository.findByTenantId) through to the final verificarSemDeriva call (via todosOsUtilizadores) -- in-place mutation via user.setTenantRoles(...) means the 'depois' read in the drift check sees the just-applied conversion, exactly mirroring how a real JPA persistence-context entity behaves within one transaction."
  - "DatabaseSeeder's only change is the @Order annotation (plus its two imports and a one-line comment) -- 4 lines added total, verified by git diff --stat gate, so the Phase 125 structural reflection test (no declared field mentions TenantRole) continues to hold by construction, not by luck."

patterns-established:
  - "Converging (not one-shot) boot-time migration: idempotent by construction via per-resource emptiness guards and per-row equality guards, not via a 'has this run before' flag."
  - "Explicit @Order between multiple CommandLineRunners whenever one has an undocumented dependency on another's side effect in an authorization-sensitive path."

requirements-completed: [MIGR-01, MIGR-02]

# Metrics
duration: 55min
completed: 2026-09-21
---

# Phase 126 Plan 03: Convert existing offices to office-scoped roles Summary

**Built `MigracaoPapeisEscritorioService.migrar()` (converging per-tenant conversion reusing `SetupService.instanciarMoldes`, skipping the reserved `ALCv` tenant, verifying zero drift inside the same transaction) plus `MigracaoPapeisRunner` (boot-time `CommandLineRunner`, explicitly ordered after `DatabaseSeeder`, uncaught exceptions abort boot by design) — 9 Mockito tests, two genuine RED demonstrations, zero call sites of role/permission resolution touched.**

## Performance

- **Duration:** 55 min
- **Started:** 2026-09-21T10:50:00Z (approx, first file read)
- **Completed:** 2026-09-21T11:25:53Z
- **Tasks:** 3/3 completed
- **Files modified:** 5 (3 created, 2 modified)

## Accomplishments
- `SetupService.instanciarMoldes` promoted from `private` to package-private with zero change to its body, signature, or the six-argument `SetupService` constructor — both existing `SetupService` test classes (17 cases: 11 + 6) stay green, proving the reuse contract holds.
- `MigracaoPapeisEscritorioService.migrar()` — `@Transactional`, iterates every tenant, skips the reserved `ALCv` tenant by name (never reads its users, never instantiates moldes for it), captures the pre-conversion effective state via `VerificacaoDerivaPapeisService.capturarAntes` before any write, instantiates missing moldes via the reused `SetupService.instanciarMoldes` only when `t_tenant_role` is empty for that tenant, associates each user to the office `TenantRole` homonymous with their global role via `ResolucaoPapeisService.resolverPapeisDeEscritorio`, skips users with no office equivalent (safe fallback to global resolution, logged as `warn`), skips users already at the correct target (idempotency), and runs `verificarSemDeriva` inside the same transaction before returning — an abort here rewinds tenants, instantiated moldes, and associations together.
- `MigracaoPapeisRunner` — `@Order(Ordered.LOWEST_PRECEDENCE)`, no `try`/`catch`, no `seedEnabled`/`@Value` gate, runs on every boot including production (`SEED_ENABLED=false`); `DatabaseSeeder` gained `@Order(Ordered.LOWEST_PRECEDENCE - 100)` as its only change (2 imports + 1 comment + 1 annotation, 4 lines total per `git diff --stat`), so the Phase 125 structural reflection test asserting no declared field mentions `TenantRole` continues to hold.
- 9 Mockito tests (happy-path conversion with `ArgumentCaptor` proof, `t_user_role` reversibility, instantiate-when-missing, never-instantiate-when-present, full idempotency on a second pass, reserved-tenant skip, no-equivalent-role safe fallback, drift-triggered `IllegalStateException` naming the affected email, and `InOrder`-proven capture-before-write ordering via a spy) — `ResolucaoPapeisService` and `VerificacaoDerivaPapeisService` injected real, never mocked, so the drift case cannot be a tautology.
- Full backend suite green at 263 tests (254 baseline from plans 01+02 + 9 new), `mvn spotbugs:check` clean, `git diff --name-only` against the pre-plan commit contains exactly the 5 files in `files_modified` — zero role-read call sites (`JwtAuthenticationFilter`, `AuthController`, `AdminController`, `ParecerController`, `ResourceController`, `UserPrincipal`) touched, `git diff --stat backend/pom.xml web/package.json` empty.

## Task Commits

Each task was committed atomically:

1. **Task 1: Reutilizar instanciarMoldes e escrever a conversão convergente por tenant** - `48a5d171` (feat)
2. **Task 2: Runner de arranque com ordem explícita relativamente ao seeder** - `a4b71c9a` (feat)
3. **Task 3: Prova Mockito da conversão, da idempotência e do administrador de plataforma** - `408a7585` (test)

_No plan-metadata commit created by this agent — orchestrator owns STATE.md/ROADMAP.md updates per this plan's execution scope._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/services/SetupService.java` - `instanciarMoldes` promoted `private` → package-private; 5th invariant paragraph added to its doc-comment; body/signature/constructor untouched
- `backend/src/main/java/com/lexcv/services/MigracaoPapeisEscritorioService.java` - `migrar()`: skip `ALCv`, capture-before-write, instantiate-if-missing (reused, not reimplemented), associate-or-skip per user, verify zero drift inside the transaction, summary log line
- `backend/src/main/java/com/lexcv/seed/MigracaoPapeisRunner.java` - `CommandLineRunner`, `@Order(LOWEST_PRECEDENCE)`, unconditional, uncaught, invokes `migrar()`
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` - `@Order(LOWEST_PRECEDENCE - 100)` + 2 imports + 1 comment (4 lines total); no other change
- `backend/src/test/java/com/lexcv/services/MigracaoPapeisEscritorioServiceTest.java` - 9 cases: happy conversion, `t_user_role` preserved, instantiate-when-missing, never-instantiate-when-present, idempotent second pass, reserved tenant skipped, no-equivalent user safe fallback, drift aborts with email named, capture-before-save ordering (`InOrder` + spy)

## Decisions Made
- `instanciarMoldes` visibility change only — no reimplementation of the instantiation loop anywhere; `MigracaoPapeisEscritorioService` contains zero `TenantRole.builder()` calls (grep-gate verified at `0`), calling the reused method exactly once.
- Convergence via two independent guards rather than a migration-run flag: `tenantRoleRepository.findByTenantId(tenantId).isEmpty()` for moldes (avoids violating the `(tenant_id, nome)` unique constraint on a second pass), and `alvo.equals(user.getTenantRoles())` for user associations (avoids a redundant `save` when nothing changed).
- `todosOsUtilizadores` accumulates the exact same `User` object references returned by `userRepository.findByTenantId` across all non-reserved tenants — since `user.setTenantRoles(...)` mutates those same instances in place, the final `verificarSemDeriva(antes, todosOsUtilizadores)` call observes the post-conversion state through the identical object graph a real JPA persistence context would produce within one transaction, without any extra re-fetch.
- Two doc-comment rewordings were needed to keep the plan's own grep gates honest (see Deviations below) — this mirrors the same class of self-correction documented in `126-02-SUMMARY.md` for the `UserPrincipal.create` gate and the `isEmpty()` gate.

## Deviations from Plan

None architecturally — plan executed exactly as written. Two self-corrections made before any commit, not deviations from intent:

1. The first draft of `MigracaoPapeisEscritorioService`'s class-level Javadoc referenced `{@link SetupService#instanciarMoldes(UUID)}` and later `{@code setRoles}, {@code getRoles().clear()/.add()/.remove()}, {@code deleteAll}, {@code .delete(}` — both phrasings pushed the plan's own grep gates (`grep -c 'instanciarMoldes' == 1` and the mutate-ops gate `== 0`) above their required counts, because the gates match literal substrings anywhere in the file, comments included. Reworded both paragraphs to describe the same invariants in prose without the literal method-name/operation tokens (e.g. "reutiliza o mecanismo de `SetupService`... para instanciar moldes" instead of linking the method by name; "a colecção de papéis globais... nunca é reatribuída, esvaziada nem alterada" instead of naming the Java operations). No behavior change — same self-correction class as the two documented in `126-02-SUMMARY.md`.
2. The first draft of `MigracaoPapeisRunner`'s Javadoc quoted `{@code @Order(LOWEST_PRECEDENCE - 100)}` when describing `DatabaseSeeder`'s annotation, which pushed `grep -c '@Order' MigracaoPapeisRunner.java` to `2` against the plan's required `1`. Reworded to "ordenado 100 posições antes desta" (no literal `@Order` substring). No behavior change.

## Issues Encountered

None. All three tasks' verification commands and acceptance criteria passed after the two grep-gate self-corrections above (made before any commit).

## User Setup Required

None - no external service configuration required. `MigracaoPapeisRunner` runs automatically on every boot (including production with `SEED_ENABLED=false`), as documented in `DEPLOYMENT.md`'s `### Phase 126` sub-section (plan 01, Task 3).

## Red Demonstrations (literal output)

### Demonstration 1 — tenant reservada `ALCv` guard (case 6)

Temporarily changed `migrar()`'s guard from `if (TENANT_RESERVADO.equals(tenant.getNome()))` to `if (false && TENANT_RESERVADO.equals(tenant.getNome()))`, disabling the skip, and re-ran only case 6:

```
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 4.660 s <<< FAILURE! -- in com.lexcv.services.MigracaoPapeisEscritorioServiceTest
[ERROR] com.lexcv.services.MigracaoPapeisEscritorioServiceTest.migrar_tenantReservadaALCv_ehSaltadaSemLerUtilizadoresNemInstanciarMoldes -- Time elapsed: 4.507 s <<< FAILURE!
org.mockito.exceptions.verification.NeverWantedButInvoked:

userRepository.findByTenantId(
    82a2e036-761c-47f1-89a1-8e3d0281deff
);
Never wanted here:
-> at com.lexcv.services.MigracaoPapeisEscritorioServiceTest.migrar_tenantReservadaALCv_ehSaltadaSemLerUtilizadoresNemInstanciarMoldes(MigracaoPapeisEscritorioServiceTest.java:221)
But invoked here:
-> at com.lexcv.services.MigracaoPapeisEscritorioService.migrar(MigracaoPapeisEscritorioService.java:90) with arguments: [82a2e036-761c-47f1-89a1-8e3d0281deff]
```

This is exactly the platform-administrator-lockout case: with the guard disabled, the reserved tenant's users would be read and its moldes instantiated like any other office. Reverted the one-line change and confirmed green: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`.

### Demonstration 2 — `verificarSemDeriva` call (case 8)

Temporarily wrapped the final call in `if (false) { verificacaoDerivaPapeisService.verificarSemDeriva(antes, todosOsUtilizadores); }`, disabling the drift check, and re-ran only case 8:

```
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 5.923 s <<< FAILURE! -- in com.lexcv.services.MigracaoPapeisEscritorioServiceTest
[ERROR] com.lexcv.services.MigracaoPapeisEscritorioServiceTest.migrar_derivaDetectada_lancaIllegalStateExceptionComEmailDoUtilizador -- Time elapsed: 5.791 s <<< FAILURE!
org.opentest4j.AssertionFailedError: Expected java.lang.IllegalStateException to be thrown, but nothing was thrown.
	at org.junit.jupiter.api.AssertThrows.assertThrows(AssertThrows.java:73)
	at com.lexcv.services.MigracaoPapeisEscritorioServiceTest.migrar_derivaDetectada_lancaIllegalStateExceptionComEmailDoUtilizador(MigracaoPapeisEscritorioServiceTest.java:267)
```

This proves the safety net is genuinely wired into the conversion path, not merely present in another file. Reverted the change and confirmed the full class green: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`.

## Next Phase Readiness

The conversion exists, is convergent, reuses the Phase 125 instantiation mechanism, and runs on every boot ordered after the seeder — but **no read site has been cut over yet**. Authority resolution still reads global `User.roles` exclusively; `git diff --name-only` across all three task commits touches none of the nine read call sites (`JwtAuthenticationFilter`, `AuthController` ×2, `AdminController` ×2, `ParecerController`, `ResourceController` ×2). If this plan ran and plan 04 did not, nothing would change for any user — exactly the deliberate boundary stated in the plan's objective. Plan 04 (read-path cutover) and Plan 05 can proceed. No blockers.

---
*Phase: 126-migracao-de-papeis-existentes*
*Completed: 2026-09-21*

## Self-Check: PASSED

All 5 claimed files found on disk; all 3 commit hashes (`48a5d171`, `a4b71c9a`, `408a7585`) found in `git log`.
