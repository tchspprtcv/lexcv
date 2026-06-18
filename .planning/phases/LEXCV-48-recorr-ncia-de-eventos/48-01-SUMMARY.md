---
phase: 48-recorrencia-de-eventos
plan: 01
subsystem: backend
tags: [recurrence, evento, java]
key-files:
  modified:
    - backend/src/main/java/com/lexcv/models/Evento.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
decisions:
  - "Master recurring Evento record is excluded from GET /eventos response; only expanded virtual instances are returned"
  - "Virtual instances carry the master id so UI detail/toggle calls still resolve the master row"
  - "Unknown recurrenceRule values (not DAILY/WEEKLY/MONTHLY) are treated as non-recurring to prevent infinite loops"
metrics:
  completed: "2026-06-18"
---

# Phase 48 Plan 01: Recorrência de Eventos — Backend Summary

Added recurrence support to the Evento entity and ResourceController: three persisted columns (recurrenceRule, recurrenceEndDate, recurrenceExceptions), two transient fields (isRecurrenceInstance, recurrenceInstanceDate), in-memory instance expansion in GET /eventos, and a soft-delete-instance endpoint.

## Commits

| Task | Commit  | Message |
|------|---------|---------|
| 1    | 53c4c5e | feat(48-01): add recurrence fields to Evento entity |
| 2    | 0ac4afd | feat(48-01): expand recurring evento instances in GET /eventos |
| 3    | 0bcbed3 | feat(48-01): add DELETE /eventos/{id}/instances endpoint |

## Task Details

**Task 1 — Evento entity fields**
- Added `import java.time.LocalDate`
- Added `@Column(name = "recurrence_rule") private String recurrenceRule` (nullable; DAILY|WEEKLY|MONTHLY)
- Added `@Column(name = "recurrence_end_date") private LocalDate recurrenceEndDate`
- Added `@Column(name = "recurrence_exceptions") private String recurrenceExceptions` (comma-separated YYYY-MM-DD)
- Added `@Transient private Boolean isRecurrenceInstance`
- Added `@Transient private String recurrenceInstanceDate`

**Task 2 — GET /eventos instance expansion**
- Defaults expansion window to `now().minusYears(1)..now().plusYears(1)` when no date params supplied
- For each master with recurrenceRule != null: walks cursor from dataInicio through recurrenceEndDate
- DAILY/WEEKLY/MONTHLY stepping; unknown rules break immediately to prevent infinite loop
- Parses recurrenceExceptions comma list into a Set; skips matching dates
- Virtual copy built via Evento.builder() preserving all fields; dataFim computed via Duration.between
- Master record excluded from response; only virtual instances returned
- Added imports: `java.time.Duration`, `java.time.format.DateTimeParseException`

**Task 3 — DELETE /eventos/{id}/instances**
- `@PreAuthorize("hasAuthority('agenda:edit')")` on `@DeleteMapping("/eventos/{id}/instances")`
- 404 on unknown id or tenant mismatch
- 400 on DateTimeParseException for malformed date param
- Reads existing exceptions into LinkedHashSet, adds new date, joins back with comma, saves master
- Does NOT delete the master row

## Deviations

None — plan executed exactly as written.

## Self-Check

PASSED

- `backend/src/main/java/com/lexcv/models/Evento.java` — exists with recurrenceRule field
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — exists with instances endpoint
- Commits 53c4c5e, 0ac4afd, 0bcbed3 present in git log
- `mvn -DskipTests compile -q` succeeded after each task
