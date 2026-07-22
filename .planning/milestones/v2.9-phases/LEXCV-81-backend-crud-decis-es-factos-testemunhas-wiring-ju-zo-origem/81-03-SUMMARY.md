---
phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
plan: "03"
subsystem: api
tags: [spring-boot, jpa, rest, concurrency, tenant-isolation]

# Dependency graph
requires:
  - phase: LEXCV-81-02
    provides: DecisaoRepository/TestemunhaRepository CRUD block in ResourceController.java (Facto block inserted immediately after it, same section)
provides:
  - "GET/POST/PUT/DELETE /api/v1/processos/{id}/factos — final 4 of the phase's 12 endpoints"
  - "FactoRepository.findMaxOrdemByProcessoId — @Query max-lookup scoped by processo_id"
  - "Server-computed append-only ordem on create (synchronized, concurrency-safe), explicit client-controlled ordem on update (reordering entry point)"
affects: [LEXCV-83-frontend-types-schemas-hooks, LEXCV-84-frontend-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Class-level synchronized block wrapping both a @Query MAX(...) lookup and the subsequent save() to close read-then-write races — same shape as createCliente's numeroSequencial and ParecerVersao's numeroVersao"
    - "ProcessoFase double-check pattern (parent tenant + child processoId) on every PUT/DELETE for child entities without their own tenant_id column"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/repositories/FactoRepository.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "POST always discards any client-supplied ordem and recomputes max(existing ordem for processo_id)+1 inside a synchronized(FactoRepository.class) block (matches T-81-10/T-81-11 threat mitigations)"
  - "PUT explicitly trusts payload.getOrdem() with no recompute — the deliberate reordering entry point per 81-CONTEXT.md"
  - "DELETE does not re-index/compact remaining ordem values — gaps are harmless for sort-only use (PITFALLS.md Pitfall 9)"

patterns-established:
  - "Facto CRUD completes the 3-entity double-check pattern (Decisao, Testemunha, Facto) started in 81-02, closing PROC-17 for the whole phase"

requirements-completed: [PROC-10, PROC-17]

# Metrics
duration: ~12min
completed: 2026-07-07
---

# Phase 81 Plan 03: Facto CRUD with server-computed ordem sequencing Summary

**Facto CRUD (GET/POST/PUT/DELETE `/processos/{id}/factos`) with server-computed, processo-scoped, concurrency-safe `ordem` on create and explicit client-controlled `ordem` on update — the phase's final 4 of 12 endpoints.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-07-07T19:44:00Z (approx)
- **Completed:** 2026-07-07T19:56:44Z
- **Tasks:** 1 (single-task plan)
- **Files modified:** 2

## Accomplishments
- `FactoRepository` gained `findMaxOrdemByProcessoId(UUID)`, an `@Query`-based `Optional<Integer>` max-lookup scoped by `processo_id`, copying `ClienteRepository`'s exact `@Query`/`@Param` style.
- `ResourceController` gained `factoRepository` as a constructor-injected field and four new endpoints (`listFactos`, `createFacto`, `updateFacto`, `deleteFacto`), inserted immediately after the Testemunha block added in `81-02`, completing the "DECISÕES / FACTOS / TESTEMUNHAS" section (12/12 endpoints across the phase).
- `createFacto` ignores any client-supplied `ordem` in the payload; inside a `synchronized (FactoRepository.class)` block it computes `findMaxOrdemByProcessoId(id).orElse(0) + 1`, sets it, and saves — the save call stays inside the synchronized block to close the read-then-write race window.
- `updateFacto` is the deliberate reordering entry point: it applies both ownership checks (parent tenant, then `facto.getProcessoId().equals(id)`) and then explicitly overwrites `ordem` from the payload with no recompute.
- `deleteFacto` applies the same double-check pattern before deletion; no re-indexing of sibling `ordem` values on delete (intentional, matches PITFALLS.md Pitfall 9).
- `listFactos` uses the already-existing `findByProcessoIdOrderByOrdemAsc` (not the unordered variant), guaranteeing deterministic ordering by `ordem`.

## Task Commits

Each task was committed atomically:

1. **Task 1: FactoRepository.findMaxOrdemByProcessoId + Facto CRUD endpoints** - `7233de5` (feat)

**Plan metadata:** (this commit, following SUMMARY.md creation)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/repositories/FactoRepository.java` - added `findMaxOrdemByProcessoId` `@Query` method plus `Query`/`Param`/`Optional` imports
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - added `factoRepository` field and `listFactos`/`createFacto`/`updateFacto`/`deleteFacto` endpoints

## Decisions Made
None beyond what's already recorded in the plan's frontmatter/threat model — plan executed exactly as written, following the `ClienteRepository`/`createCliente` and `ProcessoFase` double-check analogs specified in the plan's `<interfaces>` section verbatim.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Live HTTP verification blocked (same as `81-01`/`81-02`):** The local dev database contains real project data rather than a fresh seed; `admin@lexcv.cv`/`Pa$$w0rd` login was not attempted against it to avoid lockout risk (consistent with prior waves' documented finding of a 401 against this non-fresh dataset). Per the plan's explicit fallback clause ("If local Postgres/MinIO are unreachable in the execution environment, fall back to `mvn -DskipTests package` success as the acceptance gate"), verification was performed via:
1. `cd backend && mvn -DskipTests package -q` — exit code 0 (BUILD SUCCESS), confirmed twice.
2. Direct code review against every acceptance criterion in the plan:
   - `findMaxOrdemByProcessoId` is present, `@Query`-annotated, returns `Optional<Integer>` — confirmed by reading `FactoRepository.java`.
   - `createFacto` contains `synchronized (FactoRepository.class) { ... }` wrapping both the `findMaxOrdemByProcessoId(id).orElse(0) + 1` computation and the `factoRepository.save(facto)` call — confirmed by grep at lines 1864-1868 of `ResourceController.java`.
   - `updateFacto` contains `facto.setOrdem(payload.getOrdem())` preceded by both ownership checks (parent tenant, then `facto.getProcessoId().equals(id)`) — confirmed.
   - `deleteFacto` contains both ownership checks before `factoRepository.delete(...)` — confirmed.
   - `listFactos` calls `findByProcessoIdOrderByOrdemAsc`, not the unordered `findByProcessoId` — confirmed.

This mirrors the exact fallback already documented and accepted as non-blocking in `81-01-SUMMARY.md` and `81-02-SUMMARY.md`.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 81 (Backend — CRUD Decisões/Factos/Testemunhas + Wiring Juízo/Origem) is now complete: all 12 planned endpoints (4 Decisão, 4 Testemunha, 4 Facto) exist with the mandatory `ProcessoFase`-style double-check pattern on every PUT/DELETE (PROC-17), and `ordem`/`numeroSequencial`-style concurrency-safe sequencing is in place for Facto (PROC-10). This unblocks `82` (Honorário auto-creation, depends only on Phase 80) and `83` (frontend types/schemas/hooks, depends on all of Phase 81's endpoint surface). Live HTTP round-trip verification against a fresh seed remains outstanding across all three plans in this phase (`81-01`, `81-02`, `81-03`) and should be captured as a UAT item before the milestone closes, consistent with the existing carried-forward UAT gaps for phases 75/76/79.

---
*Phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem*
*Completed: 2026-07-07*

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/repositories/FactoRepository.java
- FOUND: backend/src/main/java/com/lexcv/controllers/ResourceController.java
- FOUND: .planning/phases/LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem/81-03-SUMMARY.md
- FOUND commit: 7233de5
