---
phase: LEXCV-72-form-refactoring-create-edit
plan: 01
subsystem: ui
tags: [react-hook-form, nextjs, clientes, forms]

# Dependency graph
requires:
  - phase: LEXCV-71
    provides: Zod schema and flattened cliente data model (NIF as primary identification field, documento_tipo select with REG_COMERCIAL)
provides:
  - Dynamic Nome/Nome Comercial and Morada/Sede labels driven live by tipo (PARTICULAR/EMPRESA) in both cliente forms
  - NIF field label consolidated from "NIF (Legado)" to "NIF" in both cliente forms
  - Confirmation that REG_COMERCIAL is selectable in documento_tipo select in both forms
affects: [clientes-novo, clientes-editar]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "form.watch(\"tipo\") read directly in component body (no useState mirror) to drive derived label constants nomeLabel/moradaLabel, since tipo lives only in react-hook-form state via Controller + form.setValue"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/clientes/novo/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx

key-decisions:
  - "Used form.watch(\"tipo\") exactly as prescribed in 72-PATTERNS.md, placed immediately before the JSX return in both components, to avoid re-deriving tipo state"
  - "REG_COMERCIAL option was already present in both selects (delivered in Phase 71 review fix) — verified, not re-added"

requirements-completed: [CLI-05, CLI-07, CLI-08, CLI-09, CLI-10]

# Metrics
duration: 12min
completed: 2026-07-02
---

# Phase 72 Plan 01: Dynamic Nome/Morada Labels + NIF Rename Summary

**Live tipo-driven Nome/Morada labels and consolidated "NIF" label (removing legacy "(Legado)" suffix) added to both cliente create and edit forms via `form.watch("tipo")`.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-02T00:00:00Z (approx, see commit timestamps)
- **Completed:** 2026-07-02
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- `clientes/novo/page.tsx`: added `tipoValue`/`nomeLabel`/`moradaLabel` derived constants; Nome label switches to "Nome Comercial" and Morada label switches to "Sede" live when tipo=EMPRESA; NIF label renamed from "NIF (Legado)" to "NIF"
- `clientes/[id]/editar/page.tsx`: identical dynamic label behavior added to the edit form, reacting correctly after the async `form.reset()` populates `tipo` from loaded cliente data
- Confirmed REG_COMERCIAL option already present in both `documento_tipo` selects (delivered in Phase 71 review fix) — no changes needed

## Task Commits

Each task was committed atomically:

1. **Task 1: Rótulos dinâmicos + rename do NIF em novo/page.tsx** - `0dffd35` (feat)
2. **Task 2: Rótulos dinâmicos + rename do NIF em editar/page.tsx** - `fd36a9a` (feat)

**Plan metadata:** (this commit)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/novo/page.tsx` - Added `tipoValue`/`nomeLabel`/`moradaLabel`, applied to Nome/Morada `<Label>` elements, renamed NIF label
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` - Same pattern applied to the edit form

## Decisions Made
- Followed the exact pattern from `72-PATTERNS.md` verbatim (no deviation): three derived constants placed immediately before `return (` in each component body, reading `form.watch("tipo")` directly rather than mirroring `tipo` into local `useState`.
- No field reordering, no changes to `onTipoChange`/`confirmTipoChange` handlers, no schema changes — pure label text changes as scoped.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Both files matched the plan's `<interfaces>` block verbatim (line numbers were close approximations but content matched exactly), so no exploration or adaptation was required.

## Verification Results

- `grep -rn "NIF (Legado)" web/src/app/(dashboard)/clientes` → no matches (exit code 1, empty result) — PASS
- Task 1 automated gate (`node -e ...` checking nomeLabel/moradaLabel/form.watch/NIF/REG_COMERCIAL presence in novo/page.tsx) → `ok` — PASS
- Task 2 automated gate (same check against editar/page.tsx) → `ok` — PASS
- `pnpm build` → compiled successfully, TypeScript passed with no errors, all 23 routes generated — PASS
- `pnpm lint` → 5 pre-existing errors and 18 pre-existing warnings remain in unrelated files (`dashboard-shell.tsx`, `use-toast.ts`, `processos/*`, `settings/page.tsx`, `profile/user-profile-form.tsx`); the two files modified in this plan show only the pre-existing, codebase-wide "React Compiler: incompatible library" warning on `form.watch()` calls (same warning already present on other `form.watch()` usages in the codebase, e.g. `documentos` page and `user-profile-form.tsx`) — no new errors introduced, PASS (no regression)

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- CLI-05, CLI-07, CLI-08, CLI-09, CLI-10 closed for both cliente create and edit forms.
- No blockers. Ready for the next plan in phase 72 (if any) or phase closure.

---
*Phase: LEXCV-72-form-refactoring-create-edit*
*Completed: 2026-07-02*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/clientes/novo/page.tsx
- FOUND: web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
- FOUND: commit 0dffd35
- FOUND: commit fd36a9a
