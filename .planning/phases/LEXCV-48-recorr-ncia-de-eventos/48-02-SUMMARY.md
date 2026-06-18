---
phase: 48-recorrencia-de-eventos
plan: "02"
subsystem: frontend
tags: [agenda, recorrência, eventos, tipos, hooks, schema, forms, delete]
dependency_graph:
  requires: [48-01]
  provides: [recurrence-frontend]
  affects: [agenda/novo, agenda/page, agenda/[id]/page]
tech_stack:
  added: []
  patterns: [AlertDialog for confirm flow, form.watch for conditional fields, superRefine for cross-field validation]
key_files:
  created: []
  modified:
    - web/src/types/eventos.ts
    - web/src/hooks/use-eventos.ts
    - web/src/schemas/eventos.ts
    - web/src/app/(dashboard)/agenda/novo/page.tsx
    - web/src/app/(dashboard)/agenda/page.tsx
    - web/src/app/(dashboard)/agenda/[id]/page.tsx
decisions:
  - Used AlertDialog primitive (already in components/ui) for delete confirmation flow
  - Used form.watch for reactive recurrenceRule-driven conditional rendering
  - Cast unified event list entries to Evento for isRecurrenceInstance check instead of any
  - superRefine chained after existing refine to keep dataFim>=dataInicio validation intact
metrics:
  duration: 15m
  completed: "2026-06-18T20:10:46Z"
  tasks_completed: 3
  tasks_total: 3
---

# Phase 48 Plan 02: Recorrência de Eventos — Frontend Summary

**One-liner:** Wired recurrence types, Zod superRefine validation, delete mutations, new-event form Recorrência section with conditional end-date, calendar ↻ instance indicator, and AlertDialog delete flow with per-instance vs. whole-series branching.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Types, hooks, and Zod schema for recurrence | a5b9a4b | eventos.ts, use-eventos.ts, schemas/eventos.ts |
| 2 | Recorrência section in new-event form + calendar indicator | 82d0a4b | agenda/novo/page.tsx, agenda/page.tsx |
| 3 | Delete dialog with instance vs series options | bd1335f | agenda/[id]/page.tsx |

## What Was Built

### Types (web/src/types/eventos.ts)
Added to `Evento`: `recurrenceRule`, `recurrenceEndDate`, `recurrenceExceptions`, `isRecurrenceInstance`, `recurrenceInstanceDate`. Added `recurrenceRule` and `recurrenceEndDate` to `EventoCreateRequest` and `EventoUpdateRequest`.

### Schema (web/src/schemas/eventos.ts)
Added `recurrenceRule: z.enum(['NONE','DAILY','WEEKLY','MONTHLY']).default('NONE')` and `recurrenceEndDate: optionalTrimmedString` to `eventoFormSchema`. Added `.superRefine()` after the existing `.refine()` that enforces recurrenceEndDate when recurrenceRule is not NONE (path: `['recurrenceEndDate']`, message: "A data de fim da recorrência é obrigatória").

### Hooks (web/src/hooks/use-eventos.ts)
Added `useDeleteEvento(id)` mutation: `DELETE /eventos/{id}`, invalidates `["eventos","list"]` and `["eventos","upcoming"]`. Added `useDeleteEventoInstance(id)` mutation taking `{ date: string }`: `DELETE /eventos/{id}/instances?date={date}`, same invalidations.

### New-Event Form (agenda/novo/page.tsx)
Added `recurrenceRule: 'NONE'` and `recurrenceEndDate: undefined` to defaultValues. Added Recorrência select with four options. Used `form.watch('recurrenceRule')` to conditionally render the `recurrenceEndDate` date input. Payload construction only includes recurrence fields when `recurrenceRule !== 'NONE'`.

### Calendar (agenda/page.tsx)
In the day event pill render, added ↻ character (HTML entity `&#x21BB;`) before the category label when the event is not a prazo and `isRecurrenceInstance` is true. Cast to `Evento` type (instead of `any`) to access the new field cleanly.

### Detail/Delete (agenda/[id]/page.tsx)
Imported `AlertDialog` suite, `useRouter`, `useDeleteEvento`, `useDeleteEventoInstance`. Added delete state and handlers. Added "Apagar" destructive button (gated on `canEditAgenda && evento.data`). AlertDialog branches: recurring event shows two action buttons (Apagar esta instância / Apagar toda a série); non-recurring shows single (Apagar evento). Both navigate to `/agenda` on success. Errors shown inline within the dialog.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced `(e as any).isRecurrenceInstance` with typed cast**
- **Found during:** Task 2 lint check
- **Issue:** Adding `(e as any).isRecurrenceInstance` introduced a new `@typescript-eslint/no-explicit-any` lint error on line 319 of agenda/page.tsx
- **Fix:** Cast to `Evento` type (which now has `isRecurrenceInstance`) instead of `any`
- **Files modified:** web/src/app/(dashboard)/agenda/page.tsx
- **Commit:** 82d0a4b

**Note on pre-existing lint errors:** `agenda/page.tsx` has 4 pre-existing `no-explicit-any` errors (lines 69, 88, 130, 131) and one `no-unused-vars` warning that pre-date this plan. These are out of scope per the deviation rules' scope boundary. They are recorded in `deferred-items.md` for a future cleanup pass.

## Known Stubs

None — all recurrence data flows from the backend API response. The ↻ indicator only renders when `isRecurrenceInstance` is set by the backend. The delete mutations call real endpoints.

## Threat Flags

None — no new network endpoints or auth paths introduced. UI gated on `canEditAgenda` (T-48F-02 mitigation applied). Zod superRefine enforces required end date client-side (T-48F-01 mitigation applied).

## Self-Check: PASSED

- a5b9a4b exists in git log
- 82d0a4b exists in git log
- bd1335f exists in git log
- web/src/types/eventos.ts — modified with recurrence fields
- web/src/hooks/use-eventos.ts — modified with delete mutations
- web/src/schemas/eventos.ts — modified with superRefine
- web/src/app/(dashboard)/agenda/novo/page.tsx — modified with Recorrência section
- web/src/app/(dashboard)/agenda/page.tsx — modified with ↻ indicator
- web/src/app/(dashboard)/agenda/[id]/page.tsx — modified with delete dialog
