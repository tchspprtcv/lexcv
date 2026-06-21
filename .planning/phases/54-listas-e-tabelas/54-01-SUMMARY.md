---
phase: "54"
plan: "01"
subsystem: frontend
tags: [tables, responsive, ux]
dependency_graph:
  requires: []
  provides: [partes-table-scroll, fases-table-scroll]
  affects: [processos/[id]/page.tsx]
tech_stack:
  added: []
  patterns: [overflow-x-auto with negative margin bleed, min-w constraint for scroll trigger]
key_files:
  modified:
    - web/src/app/(dashboard)/processos/[id]/page.tsx
decisions:
  - min-w-[400px] for partes table (3 cols: Tipo, Nome, NIF)
  - min-w-[480px] for fases table (3 cols + select + button = wider)
  - negative margin bleed (-mx-4 px-4) allows scroll container to extend to card edge on mobile
metrics:
  duration: "3 minutes"
  completed: "2026-06-21"
  tasks_completed: 1
  files_modified: 1
---

# Phase 54 Plan 01: Horizontal Scroll on Partes and Fases Tables Summary

Adds horizontal scroll to the Partes and Fases tables in the processo detail page so they remain usable on narrow viewports without content truncation.

## What Was Done

### Task 1 — Add overflow-x-auto + min-w to Partes and Fases tables

Modified 4 class strings in `web/src/app/(dashboard)/processos/[id]/page.tsx`:

1. Partes wrapper div: `overflow-x-auto` -> `overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0`
2. Partes table: `w-full text-sm` -> `w-full min-w-[400px] text-sm`
3. Fases wrapper div: `overflow-x-auto` -> `overflow-x-auto -mx-4 px-4 sm:mx-0 sm:px-0`
4. Fases table: `w-full text-sm` -> `w-full min-w-[480px] text-sm`

Commit: `616f61d`

## Verification

`grep -n "min-w-\[400px\]\|min-w-\[480px\]"` returns exactly 2 lines (lines 1254 and 1332).

Lint: pre-existing errors in `use-toast.ts` and an unrelated `<img>` element — none introduced by this change.

## Deviations from Plan

None - plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — purely presentational CSS class changes, no new surface.

## Self-Check: PASSED

- File modified: `web/src/app/(dashboard)/processos/[id]/page.tsx` — confirmed
- Commit `616f61d` exists — confirmed
- min-w classes present at exactly 2 locations — confirmed
