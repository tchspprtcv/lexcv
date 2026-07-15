---
phase: 92-agenda-riscoprazoservice-consolida-o
plan: 01
subsystem: api
tags: [spring-boot, jpa, agenda, risco-prazo, refactor]

# Dependency graph
requires:
  - phase: 85-risco-prazo-service
    provides: RiscoPrazoService.computeRiscoEvento(LocalDateTime, String) shared risk calculation, already consumed by /eventos/upcoming and dashboard KPIs
provides:
  - GET /eventos now returns a top-level "risco" (ok/proximo/vencido) string per event, including per-occurrence risk for expanded recurring instances
  - Removal of the orphaned GET /eventos/upcoming endpoint (zero frontend consumers confirmed by grep)
affects: [92-02, agenda-page-frontend]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "risco exposed as a direct top-level JSON field (not nested), mirroring the existing Prazos convention in the same controller"
    - "@Transient JPA field + Lombok @Getter/@Setter for a Jackson-serialized, non-persisted derived field (same pattern as isRecurrenceInstance/recurrenceInstanceDate)"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/models/Evento.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "risco computed via e.getDataInicio() (not getDataFim()) in listEventos, matching the exact method call already used in the now-removed getUpcomingEventos — per locked decision in 92-CONTEXT.md"
  - "The pre-existing dataInicio (Agenda) vs dataFim (dashboard isEventoCritico KPI) argument divergence in computeRiscoEvento calls is intentionally left untouched — out of scope per 92-CONTEXT.md, deferred to Phase 97 audit"

requirements-completed: [AGD-35]

# Metrics
duration: ~15min
completed: 2026-07-13
---

# Phase 92 Plan 01: Agenda risco field + orphaned endpoint removal Summary

**GET /eventos now returns a per-event `risco` (ok/proximo/vencido) computed via the shared `RiscoPrazoService`, and the unused `GET /eventos/upcoming` endpoint was deleted.**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-13T22:30:24Z
- **Tasks:** 2/2 completed
- **Files modified:** 2

## Accomplishments
- `Evento.java` gained a `@Transient private String risco` field (Jackson-serialized, JPA-ignored — no schema change, no migration needed)
- `ResourceController.listEventos` now sets `risco` on every expanded event (including each recurring instance, computed from that instance's own `dataInicio`) via the already-injected `riscoPrazoService.computeRiscoEvento(...)`, before returning the response
- `GET /eventos/upcoming` (`getUpcomingEventos`) was removed entirely — confirmed via grep that it had zero frontend call sites before deletion
- `computeRiscoEvento` remains referenced by both `listEventos` (new) and `isEventoCritico` (dashboard KPI helper, pre-existing) — no cascading removal

## Task Commits

Each task was committed atomically:

1. **Task 1: Adicionar campo risco ao payload de GET /eventos** - `19ce082` (feat)
2. **Task 2: Remover o endpoint órfão GET /eventos/upcoming** - `7ca5b2c` (fix)

**Plan metadata:** pending (this SUMMARY commit, owned by orchestrator per execution contract)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/Evento.java` - added `@Transient private String risco;` alongside the existing `isRecurrenceInstance`/`recurrenceInstanceDate` transient fields
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - `listEventos` now loops over `expanded` and calls `e.setRisco(riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade()))` before returning; `getUpcomingEventos` handler (and its `@GetMapping("/eventos/upcoming")` mapping) deleted in full

## Decisions Made
- Followed the plan's locked decision (92-CONTEXT.md) to reuse the exact `computeRiscoEvento(dataInicio, prioridade)` 2-arg overload already used by the now-removed `/eventos/upcoming`, rather than introducing any new argument convention.
- `risco` exposed as a direct top-level string field on each returned event object, matching the existing Prazos pattern in the same controller (`listPrazos`/`listAllPrazos`), not a nested object — this is additive to the `GET /eventos` response shape and does not break existing consumers.
- Did not touch the pre-existing `dataInicio` (Agenda/listEventos) vs `dataFim` (dashboard `isEventoCritico` KPI) argument divergence for `computeRiscoEvento` — this is a documented pre-existing inconsistency explicitly out of scope for this phase (deferred to Phase 97 per 92-CONTEXT.md).

## Deviations from Plan

None — plan executed exactly as written. Both tasks were implemented per the exact code patterns and locked decisions specified in `92-01-PLAN.md` and `92-CONTEXT.md`. No Rule 1-4 auto-fixes were needed; the codebase compiled cleanly on the first attempt for both tasks.

## Evidence: zero frontend consumers of the removed endpoint

Per plan's additional guidance, ran an explicit grep against the frontend before removing the backend endpoint:

```
$ grep -rn "eventos/upcoming\|useUpcomingEventos" web/src
web/src/hooks/use-eventos.ts:165:export function useUpcomingEventos(days = 7) {
web/src/hooks/use-eventos.ts:168:    queryFn: () => apiFetch<UpcomingEvento[]>(`/eventos/upcoming?days=${days}`),
```

Both matches are the hook's own definition site (`use-eventos.ts` lines 165/168) — no import or call site of `useUpcomingEventos` exists anywhere else in `web/src`. This confirms the research doc's claim of zero consumers with fresh evidence at execution time, matching the dead-hook cleanup planned for 92-02. The backend endpoint was safe to remove.

## Issues Encountered

None. `cd backend && mvn -q -DskipTests compile` passed cleanly after each task, and both plan-provided automated verification scripts (source-pattern assertions via `node -e`) passed on the first run.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `GET /eventos` response now carries `risco` for every event and every expanded recurring instance, unblocking 92-02 (frontend `agenda/page.tsx` can now consume backend-computed `risco` for both Prazos and Eventos, removing its own frontend risk calculation).
- `GET /eventos/upcoming` no longer exists; 92-02's removal of the now-fully-dead `useUpcomingEventos` hook (already confirmed to have zero call sites, see evidence above) can proceed without any backend coordination.
- No blockers or concerns for 92-02.

---
*Phase: 92-agenda-riscoprazoservice-consolida-o*
*Completed: 2026-07-13*

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/models/Evento.java
- FOUND: backend/src/main/java/com/lexcv/controllers/ResourceController.java
- FOUND: .planning/phases/LEXCV-92-agenda-riscoprazoservice-consolida-o/92-01-SUMMARY.md
- FOUND: commit 19ce082 (Task 1)
- FOUND: commit 7ca5b2c (Task 2)
