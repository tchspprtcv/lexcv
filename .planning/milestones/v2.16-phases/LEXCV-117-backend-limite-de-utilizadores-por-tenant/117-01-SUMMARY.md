---
phase: 117-backend-limite-de-utilizadores-por-tenant
plan: 01
subsystem: database
tags: [jpa, hibernate, postgresql, spring-boot, multi-tenant, spring-data-jpa]

# Dependency graph
requires: []
provides:
  - "TenantPlano enum (STARTER, STANDARD, ENTERPRISE), persisted @Enumerated(EnumType.STRING)"
  - "Tenant.plano and Tenant.limiteUtilizadores persisted fields (limiteUtilizadores nullable Integer, null = sem limite)"
  - "UserRepository.countByTenantIdAndAtivoTrue(UUID) — single reusable active-user-count method"
  - "backend/migrations/117-add-tenant-plano-limite-utilizadores.sql — manual migration backfilling the existing tenant to plano=ENTERPRISE"
affects: [117-02, 120-tenant-console, 122-relatorio-utilizacao]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Persisted simple enum via @Enumerated(EnumType.STRING), same shape as DocumentoTipo (no methods/fields on the enum itself)"
    - "Nullable Integer as the sole representation of an 'unlimited' business rule — no sentinel values (-1/MAX_VALUE)"
    - "Manual production migration script convention (backend/migrations/NNN-descricao.sql, REQUIRED header, no Flyway/Liquibase)"

key-files:
  created:
    - backend/src/main/java/com/lexcv/models/TenantPlano.java
    - backend/migrations/117-add-tenant-plano-limite-utilizadores.sql
  modified:
    - backend/src/main/java/com/lexcv/models/Tenant.java
    - backend/src/main/java/com/lexcv/repositories/UserRepository.java
    - .planning/STATE.md

key-decisions:
  - "countByTenantIdAndAtivoTrue implemented as a pure derived query (no @Query), matching the simpler existing UserRepository precedent (findByTenantId) since tenantId+ativo is a plain two-predicate equality"
  - "limiteUtilizadores is Integer (wrapper), never int, with no nullable=false and no default — absence of the attribute is this codebase's existing idiom for 'nullable'"
  - "Migration backfills plano='ENTERPRISE' for the existing tenant but deliberately never writes limite_utilizadores, leaving it NULL ('sem limite') — documented in the script's header so a future review does not 'fix' it"

requirements-completed: [PLAN-01]

# Metrics
duration: ~9min
completed: 2026-07-29
---

# Phase 117 Plan 01: Tenant Plano/Limite de Utilizadores — Camada de Dados Summary

**New `TenantPlano` enum plus `Tenant.plano`/`limiteUtilizadores` persisted fields, a single reusable `UserRepository.countByTenantIdAndAtivoTrue` active-user count, and a manual production migration that backfills the existing tenant to `ENTERPRISE`/unlimited — zero endpoint behavior changes this wave.**

## Performance

- **Duration:** ~9 min
- **Started:** 2026-07-29T01:00:25Z (approx, per STATE.md "Phase 117 execution started")
- **Completed:** 2026-07-29T01:09:02Z
- **Tasks:** 2
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments
- `TenantPlano` enum (`STARTER`, `STANDARD`, `ENTERPRISE`) created as an exact structural analog of `DocumentoTipo` — no methods, no fields, no annotations
- `Tenant` entity gained `plano` (`@Enumerated(EnumType.STRING)`, column `plano`) and `limiteUtilizadores` (nullable `Integer`, column `limite_utilizadores`) between `logoDataUrl` and `createdAt`, with `null` as the sole "sem limite" contract
- `UserRepository.countByTenantIdAndAtivoTrue(UUID)` added as the single reusable source of truth for "active users per tenant" — the contract Plan 02's `createUser` check consumes, and that Phases 120/122 are expected to reuse later
- Manual production migration script `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` created: 2 `ALTER TABLE ADD COLUMN` statements with column types/names matching the JPA mapping exactly, plus an idempotent backfill (`WHERE plano IS NULL`) setting the existing tenant's `plano='ENTERPRISE'`, deliberately never touching `limite_utilizadores`
- `.planning/STATE.md` Pending Todos updated to register the new script as a required manual pre-deploy step

## Task Commits

Each task was committed atomically:

1. **Task 1: Enum TenantPlano, campos plano/limiteUtilizadores em Tenant, contagem reutilizavel em UserRepository** - `e0dc400` (feat)
2. **Task 2: Script de migracao manual 117 (2 ADD COLUMN + backfill ENTERPRISE do tenant existente)** - `24ee81a` (docs)

**Plan metadata:** commit to follow (this SUMMARY + STATE.md/ROADMAP.md/REQUIREMENTS.md)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/TenantPlano.java` - New enum, 3 constants (STARTER/STANDARD/ENTERPRISE), analog of `DocumentoTipo`
- `backend/src/main/java/com/lexcv/models/Tenant.java` - Added `plano`/`limiteUtilizadores` fields + a business-rule comment above `limiteUtilizadores` citing Phase 117 and the "null = sem limite" contract
- `backend/src/main/java/com/lexcv/repositories/UserRepository.java` - Added `countByTenantIdAndAtivoTrue(UUID tenantId)` as the interface's last method, with a comment declaring it the single source of truth reused by Phases 120/122
- `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` - New manual migration script (2 `ADD COLUMN` + 1 backfill `UPDATE`), following the established `96-`/`74-` template
- `.planning/STATE.md` - One new line under `### Pending Todos` registering the new manual migration script

## Decisions Made
- Chose the pure derived-query form (`long countByTenantIdAndAtivoTrue(UUID tenantId);`) over an explicit `@Query`, since `tenantId = X AND ativo = true` has no extra predicate logic — matches `findByTenantId`/`findByEmail` in the same file rather than the `@Query`-based `NotificacaoRepository` precedent (both were valid per 117-PATTERNS.md; picked the simpler one for the simpler need)
- Field placement in `Tenant.java`: `plano` immediately followed by `limiteUtilizadores`, inserted between `logoDataUrl` and `createdAt` exactly as instructed, with the business-rule comment placed only above `limiteUtilizadores` (not above `plano`) per the plan's literal wording

## Deviations from Plan

None - plan executed exactly as written. Both tasks' acceptance criteria all passed on the first attempt; no auto-fixes, no missing functionality, no blockers, no architectural changes.

## Issues Encountered

- The environment's global `rtk` (Rust Token Killer) hook, installed via the user's `~/.claude/CLAUDE.md`, transparently rewrites Bash `grep`/`git` invocations and summarizes/truncates output for token savings. This is transparent for single-stage commands, but it broke the plan's literal `<verify><automated>` shell pipelines (`grep -v ... | grep -c ...`), because the first `grep`'s output going into the pipe was `rtk`'s summarized text rather than the raw filtered file content. **Not a code defect** — worked around by using the dedicated `Grep` tool (unaffected by the Bash-layer hook) for all pattern/count verifications, and `rtk proxy <cmd>` (the hook's own documented raw-passthrough escape hatch) for `git diff --numstat`/`git status` checks that needed unsummarized output. All acceptance criteria were independently re-verified this way; one criterion (`grep -ci 'NOT NULL\|DEFAULT\|CHECK\|...'` on the migration script) initially flagged a false positive from the word "checks" inside an explanatory SQL comment (`-- ddl-auto=validate ... only checks the existing schema`) — confirmed harmless by direct file inspection (the actual SQL statements contain none of those tokens) and consistent with the plan's own comment-filtering intent.
- `gsd-sdk` (used for STATE.md/ROADMAP.md/REQUIREMENTS.md mutations) was not on `PATH` in this session and not under a local `node_modules`. Located the installed CLI via `npm root -g` at `AppData\Roaming\npm\gsd-sdk.cmd`, which resolves to a cached npx install; invoked directly via `node <resolved-path>/gsd-sdk.js query ...` per the SDK-first instruction (not `npx`).

## User Setup Required

None - no external service configuration required. The one manual step required before/alongside the next production deploy is running `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` against the database (already registered in `.planning/STATE.md` Pending Todos, same convention as every other manual migration script in this repo).

## Next Phase Readiness

- `Tenant.plano`/`Tenant.limiteUtilizadores` and `UserRepository.countByTenantIdAndAtivoTrue` are now available as stable contracts for Plan 02 (`AdminController.createUser` 409 enforcement) — no further schema work needed there.
- Backend compiles clean, full test suite green (84/84), SpotBugs/FindSecBugs clean (0 bug instances).
- No blockers. Ready for `117-02-PLAN.md`.

---
*Phase: 117-backend-limite-de-utilizadores-por-tenant*
*Completed: 2026-07-29*

## Self-Check: PASSED

All created/modified files confirmed present on disk:
- FOUND: backend/src/main/java/com/lexcv/models/TenantPlano.java
- FOUND: backend/migrations/117-add-tenant-plano-limite-utilizadores.sql
- FOUND: backend/src/main/java/com/lexcv/models/Tenant.java
- FOUND: backend/src/main/java/com/lexcv/repositories/UserRepository.java
- FOUND: .planning/STATE.md
- FOUND: .planning/phases/LEXCV-117-backend-limite-de-utilizadores-por-tenant/117-01-SUMMARY.md

All task commits confirmed in git log:
- FOUND: e0dc400 (Task 1)
- FOUND: 24ee81a (Task 2)

Plan-level verification re-confirmed: `mvn -q -DskipTests compile` exit 0, `mvn test` 84/84 passing (BUILD SUCCESS), `mvn spotbugs:check` 0 bug instances / 0 errors.
