---
phase: 49-drag-drop-no-calendario
plan: "01"
subsystem: frontend/agenda
tags: [drag-drop, optimistic-update, tanstack-query, html5-dnd]
dependency_graph:
  requires: []
  provides: [AGE-07, AGE-08]
  affects: [web/src/app/(dashboard)/agenda/page.tsx]
tech_stack:
  added: []
  patterns: [HTML5 drag & drop API, optimistic UI update with Map override, TanStack Query mutation + invalidation]
key_files:
  created: []
  modified:
    - web/src/app/(dashboard)/agenda/page.tsx
decisions:
  - "Optimistic override stored in a Map<number, string> keyed by event id; cleared on both onSuccess and onError"
  - "Duration preserved on drop by computing durMs = dataFim - dataInicio offset and applying to new date"
  - "Same-cell drop guard uses dayKey comparison to avoid no-op API calls"
  - "dragDropMutation onError relies on apiFetch toast — no duplicate error handling added"
metrics:
  duration: "~15 minutes"
  completed_date: "2026-06-18"
  tasks_completed: 2
  files_modified: 1
---

# Phase 49 Plan 01: Drag & Drop no Calendário Summary

HTML5 drag & drop on the agenda month calendar — movable event pills with optimistic repositioning, PUT persistence, and duration-preserving date moves.

## What Was Built

Single-file change to `web/src/app/(dashboard)/agenda/page.tsx`:

- **`moveDatePreservingTime(originalISO, newDateKey)`** — module-scope helper that swaps the YYYY-MM-DD prefix while keeping the `T...` time portion intact.
- **Drag state** (`dragState`, `dragOverKey`, `optimisticOverrides`) added inside `AgendaPageContent`.
- **`dragDropMutation`** — `useMutation` that calls `apiFetch<Evento>(/eventos/{id}, { method: "PUT" })`. On success: clears overrides and invalidates `["eventos","list"]`. On error: clears overrides (reverts optimistic move; `apiFetch` already shows an error toast).
- **`allUnifiedEvents` useMemo** extended to apply `optimisticOverrides` — if an event id has an override, `dataInicio` is replaced and `dataFim` is shifted by the original duration. `optimisticOverrides` added to dependency array.
- **Event pills** — `canDrag = !e.isPrazo && !(e as Evento).isRecurrenceInstance`. Draggable pills get `draggable={true}`, `onDragStart` (sets `dataTransfer` + `dragState`), `onDragEnd` (clears), and `cursor-grab active:cursor-grabbing` class.
- **Calendar cells** — `onDragOver` (preventDefault + `setDragOverKey`), `onDragLeave` (clear only if current key), `onDrop` (same-cell guard, compute new dates, set override, fire mutation). Highlight class `ring-2 ring-inset ring-blue-400 bg-blue-50 dark:bg-blue-900/20` applied when `!day.isOutsideMonth && dragOverKey === key`.

## Verification

- `pnpm exec tsc --noEmit` — no errors in agenda/page.tsx
- `pnpm lint` — clean for agenda/page.tsx

## Task 3 — Manual Browser Verification Required

Task 3 is a `checkpoint:human-verify` and was not automated. Manual testing steps:

1. Start backend (`mvn spring-boot:run`) and frontend (`pnpm dev`), log in, open http://localhost:3000/agenda.
2. Ensure a normal (non-prazo, non-recurring) event exists in the current month.
3. Drag a movable pill over another day — destination cell should show blue ring + light-blue background.
4. Drop it — event moves immediately; Network tab should show `PUT /eventos/{id}` with new `dataInicio`/`dataFim`; time-of-day preserved.
5. Refresh — event stays on new day (persisted).
6. Confirm prazo pills and recurring pills (↻) are not draggable.
7. Drop an event on its own cell — no network request should fire.
8. (Error path) Stop backend, drag+drop — event snaps back and error toast appears.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — frontend-only change using pre-existing `PUT /eventos/{id}` endpoint gated by existing `@PreAuthorize` + tenant scoping. No new security surface introduced.

## Self-Check: PASSED

- `web/src/app/(dashboard)/agenda/page.tsx` — modified and committed (cfca7df)
- `moveDatePreservingTime` — present at module scope
- `dragState`, `dragOverKey`, `optimisticOverrides` — declared
- `dragDropMutation` — wired with PUT + onSuccess/onError
- `optimisticOverrides` applied in `allUnifiedEvents`
- Pills: `draggable`, `onDragStart`, `onDragEnd`, `cursor-grab`
- Cells: `onDragOver`, `onDragLeave`, `onDrop`, highlight class
- TypeScript and lint clean
