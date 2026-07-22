---
phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
plan: "04"
subsystem: ui
tags: [lucide-react, tooltip, accessibility, react, next.js, processos]

# Dependency graph
requires:
  - phase: 108-109
    provides: "Tooltip primitive (TooltipProvider mounted once at web/src/app/providers.tsx, 700ms delayDuration) already installed and proven at 15+ icon-only call sites"
  - phase: 115-UI-SPEC
    provides: "Locked FICO-01 icon set (Aplicar->Check, Limpar->X, Exportar->Download), locked tooltip/aria-label copy, and the ICON-01 vocabulary table this plan's icon choices were sourced from"
provides:
  - "Processos list filter bar: Aplicar/Limpar converted to icon-only (Check/X) with size=icon + aria-label + Tooltip; Exportar (pre-existing icon-only disabled placeholder) completed with aria-label + Tooltip while staying disabled — FICO-01 for the Processos module done"
  - "Processos list Dashboard link (LayoutDashboard) and 'Ver Agenda Completa' (ArrowRight) gain icons while keeping text"
  - "All 9 buttons in the Processo intake wizard (novo/page.tsx) carry vocabulary-correct icons: Voltar->ArrowLeft, Continuar/Seguinte->ArrowRight, Executar Conflict Check->Search, Registar Decisão (submit + disabled placeholder)->Check, Formalizar Processo->CheckCircle2"
  - "Processo editar page: Cancelar->X (x2), Guardar->Save"
  - "processos/[id]/documentos-columns.tsx Apagar row-action converted to icon-only Trash2 + Tooltip + aria-label, matching its sibling Download button's exact footprint"
affects: [115-05, 115-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "FICO-01 icon-only conversion: <Tooltip><TooltipTrigger asChild><Button size=\"icon\" aria-label=\"...\">...</Button></TooltipTrigger><TooltipContent>...</TooltipContent></Tooltip>, aria-label and TooltipContent carrying identical locked copy, preserving each button's existing variant/type/onClick"
    - "Icon-only row-action matching an adjacent sibling's exact footprint (variant=\"ghost\" size=\"sm\") instead of the generic size=\"icon\" convention, when two row actions sit side-by-side and must render at identical height"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/processos/page.tsx
    - web/src/app/(dashboard)/processos/novo/page.tsx
    - web/src/app/(dashboard)/processos/[id]/editar/page.tsx
    - web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx

key-decisions:
  - "Processos' Exportar button variant is \"ghost\" (not \"outline\" as the UI-SPEC's general FICO-01 pattern note suggested) — preserved the button's own actual pre-existing variant per the task's explicit \"preserve variant/type/onClick\" instruction, rather than the cross-module generic example"
  - "'Ver Agenda Completa' gets ArrowRight AFTER its text (not before) — matched the only in-repo ArrowRight/forward-navigation precedent (setup/page.tsx's \"Concluir configuração\"), which places the icon after text; 'Dashboard' link gets LayoutDashboard BEFORE its text, matching its sibling \"Novo Processo\" (Plus-before-text) button in the same toolbar row"
  - "novo/page.tsx's L455 submit (\"Continuar para Conflict Check\") mapped to ArrowRight, not Plus — the visible text literally says \"Continuar\", which is the vocabulary table's explicit ArrowRight precedent, even though the action technically creates the intake record"
  - "novo/page.tsx's L624 submit + L634 disabled placeholder (both labelled \"Registar Decisão\") mapped to Check (affirmative-submit family) rather than Plus, consistent with both being confirm/register actions, not create-new actions"
  - "documentos-columns.tsx's Apagar icon-only footprint matches its sibling Download button's exact props (variant=\"ghost\" size=\"sm\") instead of size=\"icon\", so the two adjacent row-action buttons render at identical height in the Ações column"
  - "Optional Loader2 pending-icon swap (UI-SPEC, explicitly non-mandatory) was not implemented for novo/page.tsx's dynamic-text buttons — kept scope to the required static vocabulary icon, avoiding added conditional-render complexity not requested by the task"

requirements-completed: [ICON-01, FICO-01]

# Metrics
duration: ~20min
completed: 2026-07-22
---

# Phase 115 Plan 04: Processos List + Wizard + Editar Icons Summary

**FICO-01 icon-only Aplicar/Limpar/Exportar completion on the Processos list filter bar, plus ICON-01 vocabulary icons across the list nav buttons, the 9-button intake wizard, the editar page, and the documentos-column Apagar row action — 4 files, 17 button sites.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 3/3 completed
- **Files modified:** 4

## Accomplishments

- Processos list filter bar is now FICO-01-compliant: Aplicar (`Check`) and Limpar (`X`) are icon-only with `size="icon"`, locked `aria-label`, and `Tooltip`; the pre-existing icon-only-but-incomplete Exportar (`Download`, `disabled`) now has its `aria-label="Exportar CSV"` + `Tooltip` a11y completion while remaining a non-functional, disabled placeholder (out of scope to wire up).
- Dashboard link and "Ver Agenda Completa" keep their visible text and gain `LayoutDashboard`/`ArrowRight` icons respectively.
- All 9 buttons in the Processo intake wizard (`novo/page.tsx`) — spanning all 3 wizard steps (Intake, Conflict Check, Abertura) — carry vocabulary-correct icons with text, handlers, and disabled logic fully preserved.
- Processo editar page's Cancelar (x2) and Guardar buttons carry `X`/`Save` icons with text preserved.
- `processos/[id]/documentos-columns.tsx`'s Apagar row-action is now icon-only (`Trash2`) with `Tooltip`+`aria-label`, matching its already-compliant Download sibling's exact visual footprint; the delete handler and `window.confirm()` guard are untouched.
- The 3 already-compliant buttons in `processos/page.tsx` (`Plus`/"Novo Processo" x2, `Filter`/"Filtros") are confirmed byte-identical in the diff — not re-touched.

## Task Commits

Each task was committed atomically:

1. **Task 1: Processos list — FICO-01 (Aplicar/Limpar icon-only, Exportar a11y) + nav icons** - `a94ecf7` (feat)
2. **Task 2: Processo intake wizard (novo)** - `4f0a8cd` (feat)
3. **Task 3: Processo editar page + documentos-column Apagar** - `8ded5fa` (feat)

**Plan metadata:** _pending — SUMMARY commit follows this document_

_Note: tdd="true" was set on Task 1's frontmatter, but the task itself defines no `<test>`/`<implementation>` split (only `<behavior>`/`<action>`/`<acceptance_criteria>`/`<verify>` with grep-based static source assertions), and this repo's `web/` has no test runner (`package.json` has no `test` script, no vitest/jest devDependency — confirmed live). Verified via the task's own grep-based acceptance checks (all passed, see Deviations) plus `eslint`/`tsc --noEmit`, consistent with prior-phase precedent (STATE.md Phase 112 note: "WR-05 deferred, needs vitest")._

## Files Created/Modified
- `web/src/app/(dashboard)/processos/page.tsx` - Aplicar/Limpar icon-only conversion, Exportar a11y completion, Dashboard/Ver Agenda Completa icons
- `web/src/app/(dashboard)/processos/novo/page.tsx` - 9 intake-wizard buttons gain vocabulary icons
- `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` - Cancelar (x2)/Guardar icons
- `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx` - Apagar row-action converted to icon-only matching sibling Download

## Decisions Made

See `key-decisions` in frontmatter for the full rationale on icon-placement (before/after text) and variant-preservation choices. Summarized:
- Preserved each button's actual existing `variant` (e.g., Processos' Exportar is `ghost`, not the UI-SPEC's cross-module generic `outline` example).
- Icon-before-text vs. icon-after-text resolved by precedent: semantic/destination icons (Dashboard) go before text next to their sibling `Plus`-before-text convention; directional/forward-navigation icons (Ver Agenda Completa, and all of novo/page.tsx per its own explicit "icon before text" instruction) follow the codebase's only established `ArrowRight` precedent.
- Apagar in documentos-columns.tsx matches its Download sibling's exact footprint (`variant="ghost" size="sm"`) rather than the generic `size="icon"` convention, for pixel-consistent adjacent row actions.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree branch was 5 commits behind master, missing all Phase 115 planning docs**
- **Found during:** Initial file-read step, before Task 1
- **Issue:** `worktree-agent-a919a6c6cccfd3fc5` was created at commit `70ff067`, before Phase 115's planning commits (`ef2f09a`..`776defc`) landed on `master`. `.planning/phases/LEXCV-115-*` did not exist in the worktree at all, so the plan/context/UI-spec files this execution depends on were unreadable.
- **Fix:** Verified the branch-safety assertion (HEAD on `worktree-agent-a919a6c6cccfd3fc5`, not a protected branch) per this agent's mandatory first action, confirmed a pure fast-forward was possible (`git merge-base --is-ancestor HEAD master` = true, zero commits unique to HEAD, clean working tree), then ran `git merge --ff-only master` to catch up.
- **Files modified:** none (metadata-only branch catch-up; brought in the pre-existing Phase 111-115 planning docs and Phase 111/112 source changes already on `master`)
- **Verification:** `git log HEAD..master` empty after the fast-forward; `.planning/phases/LEXCV-115-*` files present and readable.
- **Committed in:** n/a (fast-forward merge, not a new commit — `git merge --ff-only` simply moved the branch pointer)

**2. [Rule 3 - Blocking] `novo/page.tsx` already had a `lucide-react` import (plan assumed none existed)**
- **Found during:** Task 2
- **Issue:** The plan's `<action>` text said "Add a `lucide-react` import (file has none today)", but the file already imports `Check, ChevronRight` from `lucide-react` for its (out-of-scope, non-`<Button>`) step-indicator UI.
- **Fix:** Merged the 4 new icon names (`ArrowLeft, ArrowRight, CheckCircle2, Search`) plus reused the already-imported `Check` into the existing import line, instead of adding a second `import ... from "lucide-react"` line (which would also have failed lint's duplicate-import handling).
- **Files modified:** `web/src/app/(dashboard)/processos/novo/page.tsx`
- **Verification:** `grep -c 'from "lucide-react"'` returns 1 (matches the task's own literal acceptance check); `eslint`/`tsc --noEmit` clean for this file.
- **Committed in:** `4f0a8cd` (Task 2 commit)

**3. [Rule 3 - Blocking] `web/node_modules` did not exist in this worktree — installed before lint/typecheck verification**
- **Found during:** Pre-commit verification (after all 3 tasks' edits were made)
- **Issue:** `pnpm exec eslint` failed with "Command not found" — this worktree had never had `pnpm install` run, so `web/node_modules` was entirely absent, blocking the plan's own required `pnpm lint` verification step.
- **Fix:** Ran `pnpm install --frozen-lockfile` in `web/` (populating `node_modules` from the existing, already-committed `pnpm-lock.yaml` — not installing any new/unpinned package, so the Rule 3 package-legitimacy exclusion does not apply).
- **Files modified:** none (node_modules is gitignored, not a tracked change)
- **Verification:** `pnpm exec eslint` and `tsc --noEmit` both ran successfully afterward.
- **Committed in:** n/a (no source change; local dependency install only)

---

**Total deviations:** 3 auto-fixed (3 blocking — 1 worktree/branch setup, 1 stale plan premise, 1 missing local tooling). None required an architectural decision or user input.
**Impact on plan:** All 3 were prerequisites for being able to execute/verify the plan at all in this worktree; zero scope creep into the plan's actual UI deliverables.

## Issues Encountered

- `eslint` on `novo/page.tsx` surfaced 1 pre-existing warning (`@typescript-eslint/no-unused-vars` on `_estado` at line 121) and `tsc --noEmit` surfaced 3 pre-existing `Cannot find module 'vitest'` errors in unrelated `.test.ts` files. Both confirmed via `git diff` to be outside every hunk this plan touched — logged to `.planning/phases/LEXCV-115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only/deferred-items.md` per the scope-boundary rule (pre-existing issues in files/lines this task didn't touch are not auto-fixed).
- Interactive/visual verification (hover tooltip reveal, keyboard-only Tab pass) was intentionally not performed — the plan's own `<verification>` section defers this explicitly to Plan 11, and this agent is one of several parallel worktree executors where starting a dev server risked port collisions across sibling worktrees.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 4 files in this plan's exclusive ownership are done, lint-clean (net new), and type-clean (net new); ready for Plan 11's consolidated interactive/keyboard verification pass across the whole phase.
- `processos/[id]/page.tsx` (the heavy ficha, 31 gaps) remains untouched here by design — owned by the sibling Plan 05.
- `deferred-items.md` created at the phase level (first plan in this phase to need one) with 2 pre-existing, out-of-scope findings for a future cleanup pass.

---
*Phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only*
*Completed: 2026-07-22*

## Self-Check: PASSED

All 4 modified source files, `deferred-items.md`, and this `115-04-SUMMARY.md` confirmed present on disk. All 3 task commits (`a94ecf7`, `4f0a8cd`, `8ded5fa`) confirmed present in git log.
