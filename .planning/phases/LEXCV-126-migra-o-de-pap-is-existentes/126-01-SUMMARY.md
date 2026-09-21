---
phase: 126-migracao-de-papeis-existentes
plan: 01
subsystem: database

tags: [jpa, hibernate, postgresql, rbac, migration, spring-boot]

# Dependency graph
requires:
  - phase: 125-moldes-da-plataforma-e-provisionamento
    provides: "TenantRole entity + t_tenant_role/t_tenant_role_permission tables, SetupService.instanciarMoldes"
provides:
  - "User.tenantRoles association (t_user_tenant_role), EAGER, coexisting with User.roles (t_user_role)"
  - "backend/migrations/127-add-user-tenant-role-table.sql, idempotent, no data backfill"
  - "backend/migrations/README.md catalog updated in all 4 required locations"
  - "DEPLOYMENT.md Phase 126 operator runbook sub-section"
affects: [126-02, 126-03, 126-04, 126-05]

# Tech tracking
tech-stack:
  added: []
  patterns: ["Structural reflection test over declared @JoinTable metadata (no Spring context, no Mockito) to guard schema-coexistence invariants"]

key-files:
  created:
    - backend/src/test/java/com/lexcv/models/UserAssociacaoTenantRoleTest.java
    - backend/migrations/127-add-user-tenant-role-table.sql
  modified:
    - backend/src/main/java/com/lexcv/models/User.java
    - backend/migrations/README.md
    - DEPLOYMENT.md

key-decisions:
  - "New join table t_user_tenant_role, not a repointed t_user_role — preserves D-04 reversibility (126-CONTEXT.md): reverting is a code rollback, not a backup restore."
  - "tenantRoles is FetchType.EAGER, matching the existing roles association — required by JwtAuthenticationFilter's documented one-query-per-request-no-memoization invariant."
  - "127 is idempotent (CREATE TABLE IF NOT EXISTS) and joins the TOLERANT re-run group; the non-tolerant group's size (10) and its 'Nine of them fail loudly' description are unchanged by adding a safe script."
  - "Data conversion is explicitly deferred to Java (plan 03's MigracaoPapeisEscritorioService) rather than SQL backfill, reusing SetupService.instanciarMoldes instead of a second implementation of the same rule."

patterns-established:
  - "Migration script header discipline: IMPORTANT/Why/what-is-deliberately-NOT-done/idempotency-statement, matching 126-add-tenant-role-tables.sql"

requirements-completed: [MIGR-01, MIGR-03]

duration: 25min
completed: 2026-09-21
---

# Phase 126 Plan 01: Schema for office-role coexistence Summary

**Added `User.tenantRoles` (EAGER `@ManyToMany` over new `t_user_tenant_role`) alongside the untouched `User.roles`/`t_user_role`, plus idempotent migration `127` and the README/DEPLOYMENT.md documentation — zero behavior change, zero authority-resolution files touched.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-09-21T10:15:00Z (approx, first file read)
- **Completed:** 2026-09-21T10:40:00Z
- **Tasks:** 3/3 completed
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments
- `User` now carries two coexisting role associations — `roles` (global, `t_user_role`, untouched) and `tenantRoles` (office-scoped, new `t_user_tenant_role`, EAGER, dormant) — proven by a 4-case pure-reflection structural test that fails if either association regresses.
- Migration `127-add-user-tenant-role-table.sql` created: single idempotent `CREATE TABLE IF NOT EXISTS`, two legitimate `REFERENCES` (to `t_user` and `t_tenant_role`), zero destructive statements, zero SQL references to `t_user_role`.
- `backend/migrations/README.md` updated in all 4 required locations (Path A, Path B row 16, Re-run safety, Known execution status) with the prose counts that `127` actually changes advanced (`5 of the 15`→`6 of 16`, `7 scripts are outstanding`→`8 scripts are outstanding`) while the counts it does NOT change were left byte-for-byte intact (`10 must not be re-run`, `Nine of them fail loudly`).
- `DEPLOYMENT.md` gained a `### Phase 126` sub-section inside `## Database Schema — Two-Stage Boot`, answering the four operator questions: when to run `127`, where the data conversion happens (Java, next boot, also in production with `SEED_ENABLED=false`), what an abort from the zero-drift check means, and how to roll back (code rollback, no backup restore, no `DROP COLUMN` to undo).

## Task Commits

Each task was committed atomically:

1. **Task 1: Associação User -> TenantRole em tabela nova** - `aee658ad` (feat)
2. **Task 2: Script manual 127 e inventário do README nas quatro localizações** - `5d1d008c` (feat)
3. **Task 3: MIGR-03 — lugar do 127 no arranque em duas fases do DEPLOYMENT.md** - `c24e7413` (docs)

_No plan-metadata commit created by this agent — orchestrator owns STATE.md/ROADMAP.md updates per this plan's execution scope._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/User.java` - Added `tenantRoles` field (`@ManyToMany(EAGER)` + `@JoinTable(name = "t_user_tenant_role", ...)`), `roles`/`permissions` untouched
- `backend/src/test/java/com/lexcv/models/UserAssociacaoTenantRoleTest.java` - 4-case pure-reflection structural test (join-table names/columns, non-shared join tables, EAGER fetch)
- `backend/migrations/127-add-user-tenant-role-table.sql` - New idempotent migration creating `t_user_tenant_role`
- `backend/migrations/README.md` - Path A skip-list, Path B row 16, Re-run safety table + prose, Known execution status table + prose
- `DEPLOYMENT.md` - New `### Phase 126` sub-section + one-sentence pointer from the pre-deploy checklist

## Decisions Made
- New join table over repointing `t_user_role` — the only shape compatible with D-04 (reversibility) from `126-CONTEXT.md`.
- EAGER fetch on `tenantRoles`, matching `roles` — required by `JwtAuthenticationFilter`'s documented "one query per request, no memoization" invariant (`JwtAuthenticationFilter.java:50-58`), which the structural test's 4th case enforces going forward.
- `127`'s Re-run safety table row uses the `.sql` suffix (unlike the existing `126` row, which omits it) — needed to satisfy the plan's `grep -c '...-table.sql' README.md >= 4` gate across all 4 required locations; noted here since it is a deliberate departure from the immediate `126` row's formatting, not an oversight.

## Deviations from Plan

None — plan executed exactly as written. No Rule 1-4 auto-fixes were needed; all three tasks' verification commands and acceptance criteria passed on the first attempt (after one self-correction on the Re-run safety table row's `.sql` suffix, made before any commit, to satisfy the plan's own acceptance gate — not a deviation from the plan's intent).

## Issues Encountered

One documentation-baseline discrepancy, not a defect: the plan's acceptance criteria for Task 1 (and the phase's `<verification>` block) expect the full backend suite to grow from a baseline of 209 to 213 tests (209 + 4 new). The suite actually reported **238 tests, 0 failures** both before adding the 4 new tests would be subtracted and after — i.e. the codebase's test count was already higher than the 209 baseline recorded in `125-01-SUMMARY.md` by the time this plan ran (unrelated test growth from intervening phases/commits not reflected in that baseline note). The suite is fully green either way; this is a stale-number observation, not a blocker, and no code change was made in response.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Schema is in place and dormant. `t_user_tenant_role` exists (script `127`), `User.tenantRoles` is mapped and EAGER, and nothing reads it yet. Plan 02 (zero-drift verification harness) and plan 03 (`MigracaoPapeisEscritorioService`, the Java-side data conversion referenced by this plan's migration header) can proceed. No blockers.

---
*Phase: 126-migracao-de-papeis-existentes*
*Completed: 2026-09-21*

## Self-Check: PASSED

All 6 claimed files found on disk; all 4 commit hashes (`aee658ad`, `5d1d008c`, `c24e7413`, `206f9c66`) found in `git log`.
