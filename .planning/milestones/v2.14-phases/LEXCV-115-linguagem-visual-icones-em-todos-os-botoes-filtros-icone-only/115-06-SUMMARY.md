---
phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
plan: 06
subsystem: ui
tags: [lucide-react, tooltip, accessibility, icons, agenda, fico-01, icon-01]

# Dependency graph
requires:
  - phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
    provides: 115-UI-SPEC.md icon vocabulary contract (FICO-01 locked icon set, ICON-01 vocabulary table, icon-only+Tooltip code pattern), 115-CONTEXT.md scope decisions
provides:
  - Agenda list "Limpar Filtros" converted to icon-only (X) with Tooltip + aria-label="Limpar filtros" (FICO-01 complete for the Agenda module)
  - Agenda list "Ver Lista Completa" gains ArrowRight (text preserved)
  - Agenda list month-nav Chevron buttons gain aria-label ("Mês anterior"/"Mês seguinte") — optional a11y completion, included
  - Agenda event detail page (Voltar x2, Editar, Concluir/Reabrir toggle, Apagar trigger, "Apagar esta instância") fully iconed
  - Agenda event edit page (Voltar, Cancelar x2, Guardar) fully iconed
  - Agenda event create page (Voltar, Criar, Cancelar) fully iconed
affects: [115-11 (interactive/visual FICO-01 verification across all modules), any future Agenda UI work]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "FICO-01 icon-only+Tooltip conversion (Tooltip/TooltipTrigger asChild/TooltipContent, size=\"icon\", aria-label == TooltipContent copy) — reused from 115-UI-SPEC.md, no new pattern introduced"
    - "Concluir/Reabrir state-conditional icon (CheckCircle2/RotateCcw) mirroring the existing Prazo-conclusion toggle in processos/[id]/page.tsx"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/agenda/page.tsx
    - web/src/app/(dashboard)/agenda/[id]/page.tsx
    - web/src/app/(dashboard)/agenda/[id]/editar/page.tsx
    - web/src/app/(dashboard)/agenda/novo/page.tsx

key-decisions:
  - "ArrowRight placed after text on 'Ver Lista Completa' (not before), matching the only existing ArrowRight precedent in the codebase (setup/page.tsx's 'Concluir configuração')"
  - "Limpar Filtros icon-only className kept 'self-end' (flex-alignment, still needed) but dropped h-9/px-3/font-bold/text-xs (superseded by size=\"icon\")"
  - "Task 1's tdd=\"true\" flag was treated via the task's own grep-based <verify> block as the RED/GREEN check (confirmed 0 matches pre-edit, 1 match post-edit) — no vitest install was performed; see TDD Gate Compliance note below"

patterns-established: []

requirements-completed: [ICON-01, FICO-01]

# Metrics
duration: ~25min
completed: 2026-07-22
---

# Phase 115 Plan 06: Agenda Module Icons Summary

**FICO-01 icon-only "Limpar Filtros" (X + Tooltip + aria-label) and full ICON-01 icon vocabulary applied across all 4 Agenda pages (list, detail, edit, create) — no Aplicar/Exportar button invented, no filter behavior changed.**

## Performance

- **Duration:** ~25 min
- **Started:** ~2026-07-22T01:35:00Z (approximate)
- **Completed:** 2026-07-22T01:57:02Z
- **Tasks:** 3/3 completed
- **Files modified:** 4

## Accomplishments

- Agenda list's only filter-action button ("Limpar Filtros") converted to icon-only `X` wrapped in `Tooltip`, with `aria-label="Limpar filtros"` matching `TooltipContent` copy — satisfies FICO-01 for the Agenda module (the module has no Aplicar/Exportar button; confirmed none was invented via negative grep)
- "Ver Lista Completa" gains `ArrowRight` (text preserved), month-nav Chevrons gain `aria-label` (optional a11y completion, included since it was "nearly free")
- Event detail page: `ArrowLeft` (Voltar, both occurrences), `Pencil` (Editar), state-conditional `CheckCircle2`/`RotateCcw` (Concluir/Reabrir toggle, mirroring the existing Prazo-conclusion pattern), `Trash2` (Apagar trigger + "Apagar esta instância") — AlertDialog confirmation flow left byte-for-byte unchanged
- Event edit + create pages: `ArrowLeft` (Voltar), `Save` (Guardar), `Plus` (Criar), `X` (Cancelar, all occurrences)
- `L233` "Novo Evento" `Plus` button (do-not-touch) confirmed byte-identical via diff — zero accidental edits

## Task Commits

Each task was committed atomically:

1. **Task 1: Agenda list — FICO-01 Limpar + Ver Lista Completa + optional Chevron a11y** - `77e12b3` (feat)
2. **Task 2: Agenda event detail page** - `476ba66` (feat)
3. **Task 3: Agenda event create + edit pages** - `9c7a83a` (feat)

_Note: Task 1 was flagged `tdd="true"` but produced a single `feat` commit — see TDD Gate Compliance below._

## Files Created/Modified

- `web/src/app/(dashboard)/agenda/page.tsx` - FICO-01 icon-only Limpar Filtros + Tooltip; ArrowRight on Ver Lista Completa; Chevron aria-labels
- `web/src/app/(dashboard)/agenda/[id]/page.tsx` - ArrowLeft/Pencil/CheckCircle2/RotateCcw/Trash2 across 6 button sites
- `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx` - ArrowLeft/X/Save across 4 button sites
- `web/src/app/(dashboard)/agenda/novo/page.tsx` - ArrowLeft/Plus/X across 3 button sites

## Decisions Made

- `ArrowRight` placed after text (not before) on "Ver Lista Completa", matching the sole existing precedent in the codebase (`setup/page.tsx`, "Concluir configuração").
- Kept `self-end` on the icon-only Limpar Filtros button (still required for flex alignment against the Select triggers); dropped `h-9 px-3 font-bold text-xs` since `size="icon"` supersedes the height/width and no text remains to style.
- Did not add a `Loader2` pending-state icon swap anywhere in this plan — the plan's `<action>` blocks only requested one for Task 3 optionally, contingent on an already-wired `isSubmitting`/`isPending` boolean, and existing text-based pending indicators (`"A guardar..."`, `"A apagar..."`) were left as the sole pending signal to keep the diff strictly additive-visual, per each task's explicit scope.

## Deviations from Plan

None — plan executed exactly as written. The two items below are environment/infrastructure notes, not deviations in code or scope.

## TDD Gate Compliance

Task 1 carried `tdd="true"` at the task level (the plan's own frontmatter `type` is `execute`, not `tdd`, so plan-level TDD gate enforcement does not apply). No `<implementation>` tag was present, only `<action>`, and the task's `<verify>` block is a plain grep/source-assertion — identical in kind to sibling Tasks 2/3, which are `tdd="false"`. Investigation found **no runtime test framework installed anywhere in `web/`**: three `.test.ts` files exist (`use-processos.round-trip.test.ts`, `cliente-documento-tipo.test.ts`, `clientes.legacy-documento-tipo.test.ts`) that import `vitest`, but `vitest` is absent from `package.json`, `node_modules`, and `pnpm-lock.yaml` — pre-existing dead scaffolding, unrelated to this plan, confirmed by `tsc --noEmit` still failing on those 3 files with `Cannot find module 'vitest'` after a full dependency install.

Given this, installing a new test framework solely to satisfy one task's `tdd="true"` flag would have been a disproportionate architecture change outside this task's actual file/action scope (Rule 4 territory). Instead, the plan's own grep-based `<verify>` command was used as the RED/GREEN gate: confirmed `grep -nE 'aria-label="Limpar filtros"'` returned no match (RED) before the edit, then returned exactly one match (GREEN) after. A single `feat` commit was made (no separate `test`/`refactor` commits), consistent with how the task was actually specified and verified.

## Issues Encountered

- **Worktree branch was 5 commits behind local `master`.** This worktree's branch (`worktree-agent-a7d839109ef26b63f`) was created before the Phase 115 planning commits (`ef2f09a`..`776defc`) landed on `master`, so `.planning/phases/LEXCV-115-.../115-06-PLAN.md` and sibling planning docs did not exist in the worktree checkout. Confirmed via `git merge-base`/`git log HEAD..master` that HEAD was a strict ancestor of `master` with zero unique commits and a clean working tree — a safe base-drift correction per the mandatory startup check. Resolved with `git reset --hard master` before any file reads/edits. Also confirmed `origin/master` (70ff067) does not yet include these Phase 115 planning commits — they exist only on the local `master` ref.
- **`web/node_modules` did not exist in this worktree.** Ran `pnpm install --frozen-lockfile` (restores the exact locked tree from the committed `pnpm-lock.yaml`, not a new/unverified package) to enable `pnpm lint` and `tsc --noEmit` verification.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Agenda module (list, detail, edit, create) fully complete for both ICON-01 and FICO-01 per `115-UI-SPEC.md`'s audit table — no remaining gaps in the 4 files owned by this plan.
- FICO-01 interactive/visual verification for this module is deferred to Plan 11 (per `115-UI-SPEC.md`'s own verification plan) — not a blocker, matches the phase's intended verification sequencing.
- No blockers for sibling parallel plans — `files_modified` scope (the 4 Agenda files) had zero overlap with other Phase 115 plans per the plan-checker.

---
*Phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only*
*Completed: 2026-07-22*

## Self-Check: PASSED

All 4 modified files confirmed present on disk; all 3 task commits (`77e12b3`, `476ba66`, `9c7a83a`) confirmed present in git log.
