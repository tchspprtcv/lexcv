---
phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen
plan: 01
subsystem: auth
tags: [rbac, multi-tenant, seed, spring-boot, mockito, tdd]

# Dependency graph
requires: []
provides:
  - PLATAFORMA_ADMIN role seeded unconditionally (every startup, no seedEnabled gate) with an empty permission collection
  - Reserved Tenant "LexCV" seeded unconditionally, idempotent find-or-create by nome
  - Bootstrap user plataforma@lexcv.cv seeded only when app.seed.enabled=true, single PLATAFORMA_ADMIN role
  - TenantRepository.findByNome(String) derived query
  - Demo-data gate rewritten so its three protective counts are read before the reserved-tenant insert (bdVaziaAntesDoSeedPlataforma)
affects: [120-frontend-provisionamento, 121-isolamento-tenant]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Idempotent find-or-create seeding extended to Tenant (repository.findByNome(...).orElseGet(() -> repository.save(...)))"
    - "Split gating within a single CommandLineRunner.run(): some seed rows unconditional (role/tenant), one gated (bootstrap user), without duplicating the pre-existing demo-data protection logic"
    - "TDD RED/GREEN committed as two separate atomic commits for a tdd=true task (RED intentionally leaves one of five cases already green, acknowledged by the plan text, because that case locks in a prior task's contract)"

key-files:
  created:
    - backend/src/test/java/com/lexcv/seed/DatabaseSeederPlataformaAdminTest.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/TenantRepository.java
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java

key-decisions:
  - "PLATAFORMA_ADMIN role + reserved Tenant 'LexCV' seeded unconditionally (every startup, no seedEnabled gate) -- infrastructure Phase 120 needs regardless of demo data"
  - "Bootstrap user plataforma@lexcv.cv gated by app.seed.enabled only (not SystemSetting.initialized, not the demo-data counts) -- follows this plan's DECISAO REVISTA banner, which supersedes the original 119-CONTEXT.md/119-PATTERNS.md text (both said the user should also be unconditional) because Pa$$w0rd is a publicly documented password (CLAUDE.md)"
  - "Demo-block guard counts hoisted into bdVaziaAntesDoSeedPlataforma, read immediately after seedRbac() and before seedTenantPlataforma() inserts any row -- prevents the now-unconditional reserved-tenant insert from permanently poisoning the 'genuinely empty database' gate that protects the demo-data block"

patterns-established:
  - "Reserved/platform-level seed rows (role, tenant) live before all seedEnabled/initialized/count gates in run(); credential-bearing seed rows (bootstrap user) live immediately after the seedEnabled gate only, never after the demo-data gate"

requirements-completed: [PROV-01]

# Metrics
duration: ~20min
completed: 2026-07-29
---

# Phase 119 Plan 01: Papel de Plataforma e Seed de Infraestrutura Reservada Summary

**PLATAFORMA_ADMIN role and reserved "LexCV" tenant now seed unconditionally on every backend startup, while the bootstrap plataforma@lexcv.cv credential seeds only when app.seed.enabled=true, proven by 5 new Mockito TDD cases plus a hoisted demo-data gate.**

## Performance

- **Duration:** ~20 min (commit span 07:21:07Z -> 07:31:02Z ~10 min; total includes upfront reading of the revised CONTEXT.md/PATTERNS.md superseded banners and post-commit verification)
- **Started:** 2026-07-29T07:12:00Z (estimated)
- **Completed:** 2026-07-29T07:32:00Z
- **Tasks:** 2 (Task 2 executed as TDD: RED + GREEN commits)
- **Files modified:** 3 (1 created, 2 modified)

## Accomplishments
- `TenantRepository` gains `findByNome(String)`, the idempotent lookup the reserved tenant needs; the pre-existing WR-01 Javadoc warning on `findFirstByOrderByCreatedAtAsc` was left completely intact
- `seedRbac()` now upserts `PLATAFORMA_ADMIN` with `Collections.emptyList()` permissions, unconditionally, alongside the four existing tenant-scoped roles
- New `seedTenantPlataforma()`: unconditional find-or-create of `Tenant` "LexCV" by `nome`, called before any of the three `run()` gates -- infrastructure, not demo data
- New `seedUtilizadorPlataforma()`: bootstrap user `plataforma@lexcv.cv` / `Pa$$w0rd`, called immediately after the `seedEnabled` gate only (never after `initialized` or the demo-data counts), find-or-create by email, exactly one `PLATAFORMA_ADMIN` role, startup warning printed only on the branch that actually creates the account (T-119-06 mitigation)
- `run()`'s demo-data protection rewritten without changing its semantics: the three counts (`tenantRepository`/`userRepository`/`clienteRepository`) are now captured into `bdVaziaAntesDoSeedPlataforma` right after `seedRbac()`, before `seedTenantPlataforma()` can insert a row that would otherwise permanently poison the "genuinely empty database" check
- New test class `DatabaseSeederPlataformaAdminTest` (5 cases, Mockito, no Spring context) proves: production posture creates zero platform credentials, dev posture creates a correctly-linked single-role bootstrap user, a second startup recreates nothing, the demo-gate counts are read in the correct order relative to the tenant insert (`InOrder`), and the role itself is seeded even in production posture

## Task Commits

Each task was committed atomically:

1. **Task 1: TenantRepository.findByNome + papel PLATAFORMA_ADMIN sem permissoes** - `681c53f` (feat)
2. **Task 2 (TDD, RED): failing tests for platform tenant/user seeding** - `f4f7077` (test)
3. **Task 2 (TDD, GREEN): seed reserved LexCV tenant unconditional + bootstrap user gated** - `22d07ed` (feat)

_Note: Task 2 has two commits per this codebase's established TDD convention (RED then GREEN, e.g. Phases 117/118) -- Case 5 of the RED commit's test suite passed immediately because it proves Task 1's already-delivered contract (unconditional role seed), which the plan's own `<behavior>` text explicitly anticipates ("prova que o upsert do papel (Task 1) corre dentro de seedRbac()"). This was verified as intentional, not a test-authoring mistake, before proceeding to GREEN._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/repositories/TenantRepository.java` - adds `Optional<Tenant> findByNome(String nome)` derived query with a short Javadoc explaining its purpose (idempotent reserved-tenant lookup for `DatabaseSeeder`)
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` - `PLATAFORMA_ADMIN` upsert added to `seedRbac()`; `run()` restructured to hoist the demo-gate counts and split platform-seed calls across the `seedEnabled` gate; two new private methods, `seedTenantPlataforma()` and `seedUtilizadorPlataforma(Tenant)`
- `backend/src/test/java/com/lexcv/seed/DatabaseSeederPlataformaAdminTest.java` (new) - 5 Mockito test cases (`@ExtendWith(MockitoExtension.class)`, no `@SpringBootTest`) covering production posture, dev posture, idempotency, demo-gate ordering (`InOrder`), and unconditional role seed

## Decisions Made
- Followed this plan's `DECISAO REVISTA` banner exactly: role + reserved tenant stay unconditional, but the bootstrap user moved behind `app.seed.enabled` -- this intentionally supersedes the original text in `119-CONTEXT.md` and `119-PATTERNS.md`, both of which said the user should also be unconditional. Rationale carried into the code comments and Javadoc: `Pa$$w0rd` is a publicly documented password (`CLAUDE.md`), and an unconditional seed would create a maximum-privilege account with a known password on every production install.
- In the test's shared stub setup, used `thenAnswer` keyed off the invocation's own argument for `roleRepository.findByNome(anyString())` (returning a `Role` named after whatever was queried) instead of a single fixed-name stub -- this one generic stub transparently satisfies all five `seedRbac()` role lookups plus the explicit `PLATAFORMA_ADMIN` lookup inside `seedUtilizadorPlataforma()`, with no need for a second overriding stub.

## Deviations from Plan

None - plan executed exactly as written.

One minor pre-existing discrepancy noted (not a deviation caused by this execution): Task 1's acceptance criteria expected `grep -c '^import'` on `TenantRepository.java` to return 3 ("sem imports novos"). The file already had 4 import lines before this plan touched it (`Tenant`, `JpaRepository`, `Optional`, `UUID`). This plan added zero new imports (the substantive requirement -- "no new imports" -- holds true, unchanged at 4 before and after); the plan's hardcoded expected count of 3 was simply stale relative to the file's actual pre-existing state. No action needed, flagging for awareness only.

## Issues Encountered

- A piped Bash `grep ... | grep -v ...` verification chain produced a false-negative count (0 instead of 1) for `tenantRepository.findByNome("LexCV")`, consistent with the environment note that the user's global `rtk` shell hook can intercept/summarize piped grep output. Re-verified immediately with the dedicated Grep tool, which correctly found the single occurrence. Switched all subsequent verification greps to the dedicated Grep tool rather than piped Bash commands, per the environment guidance.

## User Setup Required

None - no external service configuration required. `SEED_ENABLED` is an existing environment variable (see `backend/.env.example`); no new variables were introduced.

## Next Phase Readiness

- Phase 120 (frontend provisioning console) can now rely on: `PLATAFORMA_ADMIN` existing with zero permissions on every environment, and the reserved `Tenant` "LexCV" existing on every environment (resolvable via `TenantRepository.findByNome("LexCV")`) -- both independent of `app.seed.enabled`.
- **Carried forward per this plan's `<output>` instructions (T-119-14):** a production installation with `SEED_ENABLED=false` has the role and reserved tenant but **zero** `PLATAFORMA_ADMIN` users -- the Phase 120 console is unreachable until a first platform account is provisioned. Documented bootstrap path until Phase 120 ships a first-class provisioning mechanism: one controlled restart with `SEED_ENABLED=true`, immediate password change on the created `plataforma@lexcv.cv` account, then set `SEED_ENABLED=false` again.
- **Carried forward per this plan's `<output>` instructions (T-119-06):** recommend Phase 120 forces a password change on first login of the `PLATAFORMA_ADMIN` role, since the seeded password is the same publicly documented dev credential pattern as `admin@lexcv.cv`.
- Plans 02/03/04 of Phase 119 (out of scope for this plan) still need to build: `SetupService.provisionTenant`, the new `PlatformAdminController` endpoint, and RBAC-screen containment of the new role (keeping it out of `RbacResponse.systemPermissions`).
- Full backend test suite (102 tests across all classes, including this plan's 5 new cases) is green with zero regressions.

---
*Phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/repositories/TenantRepository.java`
- FOUND: `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java`
- FOUND: `backend/src/test/java/com/lexcv/seed/DatabaseSeederPlataformaAdminTest.java`
- FOUND: `.planning/phases/LEXCV-119-backend-papel-de-administrador-de-plataforma-e-provisionamen/119-01-SUMMARY.md`
- FOUND commit: `681c53f`
- FOUND commit: `f4f7077`
- FOUND commit: `22d07ed`
