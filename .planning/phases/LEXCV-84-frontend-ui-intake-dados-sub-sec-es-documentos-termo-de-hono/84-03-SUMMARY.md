---
phase: 84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
plan: 03
subsystem: ui
tags: [nextjs, react-hook-form, radix-dialog, tanstack-query, processos]

# Dependency graph
requires:
  - phase: 84-01
    provides: Juízo Input on processos/[id]/editar/page.tsx, origem-processo lib
  - phase: 84-02
    provides: Termo de Honorários print route conventions
provides:
  - "TabKey union extended to 8 values (timeline/partes/fases/decisoes/factos/testemunhas/documentos/auditoria) with null placeholder branches for the 4 new tabs"
  - "Dados card shows read-only Juízo and Origem rows plus a conditional Gerar Termo de Honorários button (estado === ATIVO)"
  - "Partes and Fases tabs refactored from side-by-side grid form to full-width list Card + Dialog Adicionar pattern"
affects: [84-04, 84-05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dialog Adicionar X pattern (DialogTrigger asChild + DialogContent/DialogHeader/DialogFooter) applied to Partes/Fases, matching the pattern the 4 new tabs (84-04/84-05) will also use"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/processos/[id]/page.tsx"

key-decisions:
  - "4 new TabKey values render as explicit null branches (not omitted) in the tab-content ternary chain, keeping the chain type-correct until 84-04/84-05 fill in real bodies"
  - "rounded-none applied explicitly to every new/moved form control (Input, Button, DialogTrigger) in the Partes/Fases Dialog refactor, per UI-SPEC Anti-Safe Harbor requirement, even where the pre-existing analog omitted it"

patterns-established:
  - "Partes/Fases CardHeader now uses a flex items-center justify-between row containing CardTitle + Dialog trigger, mirroring the shell 84-04/84-05 will reuse for Decisões/Factos/Testemunhas/Documentos"

requirements-completed: [PROC-01, PROC-02, PROC-05, PROC-15]

# Metrics
duration: 10min
completed: 2026-07-08
---

# Phase 84 Plan 03: Extend TabKey, Dados card Juízo/Origem/Termo button, Partes/Fases Dialog refactor Summary

**Extended processos/[id]/page.tsx's TabKey to 8 values, added read-only Juízo/Origem rows and a conditional Gerar Termo de Honorários button to the Dados card, and refactored the Partes/Fases tabs from a side-by-side grid form to the Dialog "Adicionar" pattern.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-07-08T00:54:26Z
- **Completed:** 2026-07-08T00:59:32Z
- **Tasks:** 3
- **Files modified:** 1

## Accomplishments
- `TabKey` union extended with `decisoes`/`factos`/`testemunhas`/`documentos`, plus 4 matching tab-toggle buttons and explicit `null` placeholder branches in the render ternary — lays the foundation Plans 84-04/84-05 build their tab bodies on
- Dados card now shows read-only `Juízo` and `Origem` (via `origemProcessoToLabel`) rows, plus a `Gerar Termo de Honorários` button that opens `/processos/{id}/termo-honorarios` in a new tab, conditioned on `estado === "ATIVO"`
- Partes and Fases tabs both refactored from the old side-by-side `grid lg:grid-cols-2` form+list layout to a single full-width list `Card` with an "Adicionar Parte"/"Adicionar Fase" `Dialog` trigger next to the `CardTitle`, gated by `canEditProcessos` exactly as before
- Fases tab's inline per-row status `<select>` + "Guardar" button (`faseDraftStatus`/`onUpdateFaseStatus`) left completely untouched inside the unchanged list table

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend TabKey, add DialogTrigger import, add Juízo/Origem/Gerar-Termo to Dados card** - `b160ddb` (feat)
2. **Task 2: Refactor Partes tab to the Dialog "Adicionar Parte" pattern** - `2e3d6cd` (feat)
3. **Task 3: Refactor Fases tab to the Dialog "Adicionar Fase" pattern** - `31b28d5` (feat)

_No TDD tasks in this plan — all tasks type="auto" tdd="false"._

## Files Created/Modified
- `web/src/app/(dashboard)/processos/[id]/page.tsx` - `TabKey` extended to 8 values; `DialogTrigger` and `origemProcessoToLabel` imports added; Dados card `dl` gained Juízo/Origem rows plus a conditional Gerar Termo de Honorários button; tab-toggle button row gained 4 new buttons; Partes and Fases tab bodies refactored from grid+form to Dialog "Adicionar" pattern with `addParteModal`/`addFaseModal` state; both `onSubmitParte`/`onSubmitFase` success paths now close their respective dialog

## Decisions Made
- Kept the 4 new tab values as explicit `null` branches in the ternary chain (per plan instruction) rather than omitting them, to keep the chain exhaustive/type-correct ahead of 84-04/84-05
- Applied `rounded-none` to every Input/Button introduced or moved by the Partes/Fases Dialog refactor, consistent with UI-SPEC's Anti-Safe Harbor requirement, even though this file's other pre-existing Partes/Fases inputs did not previously carry it (those untouched instances were replaced as part of the move, so all now carry it)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None. `pnpm exec tsc --noEmit` reported zero errors in `processos/[id]/page.tsx` after each task (the only 3 reported errors are pre-existing, unrelated `vitest` module-resolution errors in `*.test.ts` files outside this plan's scope — this repo has no test runner installed, a known precedent since Phase 74/82).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `processos/[id]/page.tsx` is in a clean, fully-committed state with `TabKey`, imports (`DialogTrigger`, `origemProcessoToLabel`), and the Dados card ready for Plans 84-04/84-05 to fill in the 4 new tab bodies (currently `null` placeholders) without touching Partes/Fases again
- No blockers or concerns for Waves 2-3 (84-04, 84-05)

## Self-Check: PASSED

- FOUND: `.planning/phases/LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono/84-03-SUMMARY.md`
- FOUND: `b160ddb` (Task 1 commit)
- FOUND: `2e3d6cd` (Task 2 commit)
- FOUND: `31b28d5` (Task 3 commit)
