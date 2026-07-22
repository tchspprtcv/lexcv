---
phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
plan: 02
subsystem: ui
tags: [lucide-react, tooltip, accessibility, clientes, icon-only-buttons]

# Dependency graph
requires:
  - phase: 108-109
    provides: Tooltip primitive (TooltipProvider mounted app-wide, providers.tsx:30) and the icon-only+Tooltip+aria-label pattern this plan reuses
provides:
  - "FICO-01 icon-only conversion (Check/X/Download + Tooltip + aria-label) for Clientes list filter-action buttons (Aplicar/Limpar/Exportar CSV)"
  - "ICON-01 icon-add (text preserved) for Clientes list Merge/Importar CSV, and for all 7 gap buttons in clientes/novo + clientes/merge"
affects: [115-11 (phase human-verify checkpoint), any future Clientes-module UI plan]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "FICO-01 icon-only+Tooltip conversion: TooltipTrigger asChild wraps a Button with size=\"icon\" + aria-label (copy identical to TooltipContent), no custom className beyond variant — reused verbatim from 115-UI-SPEC.md's locked code pattern"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/page.tsx"
    - "web/src/app/(dashboard)/clientes/novo/page.tsx"
    - "web/src/app/(dashboard)/clientes/merge/page.tsx"

key-decisions:
  - "FICO-01 conversions drop ALL custom className (not just w-full) beyond variant, matching the UI-SPEC's literal locked code-pattern example and the newer size=\"icon\" majority convention — Limpar's pre-existing custom hover-color classes (text-slate-500 hover:text-slate-900 dark:hover:text-white) were dropped rather than retrofitted, since the locked pattern shows no such classes at any of its 15+ precedent call sites"
  - "Did not implement the optional Loader2 pending-swap on novo/page.tsx's submit button — explicitly optional per plan text (\"only if already present... optional\") and not required by Task 2's acceptance criteria; kept static Plus icon to minimize scope"
  - "Worktree branch was reset --hard to master's tip (776defc) before any file edits — see Deviations"

patterns-established: []

requirements-completed: [ICON-01, FICO-01]

# Metrics
duration: ~35min (includes worktree base-drift diagnosis/recovery)
completed: 2026-07-22
---

# Phase 115 Plan 02: Clientes Module Icon Work Summary

**FICO-01 icon-only conversion (Check/X/Download + Tooltip + aria-label) for Clientes' Aplicar/Limpar/Exportar CSV filter actions, plus ICON-01 icon-adds (text preserved) on Merge/Importar CSV and all 7 gap buttons across the Cliente create + merge pages.**

## Performance

- **Duration:** ~35 min (includes an unplanned worktree base-drift diagnosis and recovery — see Deviations)
- **Completed:** 2026-07-22T01:53:12Z
- **Tasks:** 2/2 completed
- **Files modified:** 3

## Accomplishments
- Clientes list filter bar's Aplicar/Limpar/Exportar CSV buttons are now icon-only (`Check`/`X`/`Download`), each wrapped in `Tooltip` and carrying an `aria-label` with copy identical to the tooltip text — satisfies FICO-01 AC4 (keyboard/screen-reader parity) automatically.
- Merge and Importar CSV buttons in the same toolbar keep their visible text and gain `Merge`/`Upload` icons.
- All 7 gap buttons across `clientes/novo/page.tsx` (Voltar, submit/Criar, Cancelar, dialog Cancelar, dialog Continuar) and `clientes/merge/page.tsx` (Voltar, Fazer merge) now carry an icon from the locked ICON-01 vocabulary, text preserved throughout.
- Zero regressions: `L285 Plus` (Adicionar Novo Cliente) and `L353-361 Filter` (Avançados) in `clientes/page.tsx` are byte-identical to before — confirmed via diff and grep.

## Task Commits

Each task was committed atomically:

1. **Task 1: Clientes list — FICO-01 conversions + Merge/Importar icons** - `c7f55ba` (feat)
2. **Task 2: Cliente create + merge pages — ICON-01** - `38b17e8` (feat)

**Plan metadata:** (this SUMMARY commit, see below)

_Note: Task 1 carried a `tdd="true"` attribute but produced only a single `feat` commit — see "TDD Gate Compliance" below for why._

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/page.tsx` - Aplicar/Limpar/Exportar CSV converted to icon-only+Tooltip+aria-label; Merge/Importar CSV gain icons, text kept
- `web/src/app/(dashboard)/clientes/novo/page.tsx` - Voltar/submit/Cancelar/dialog-Cancelar/dialog-Continuar gain icons, text kept; first `lucide-react` import in this file
- `web/src/app/(dashboard)/clientes/merge/page.tsx` - Voltar/Fazer-merge gain icons, text kept; first `lucide-react` import in this file

## Decisions Made
- FICO-01 buttons follow the UI-SPEC's locked code pattern literally: `size="icon"` + `aria-label`, existing `variant` preserved, no other custom `className` retained (drops `w-full` and, for Limpar, the pre-existing custom hover-color utility classes too) — matches all 15+ existing icon-only+Tooltip precedents in this codebase, none of which carry extra color classes on the `Button` itself (color inherits via `currentColor` from `variant`).
- Left the optional `Loader2` pending-icon swap (mentioned as an opportunistic, non-mandatory enhancement in 115-UI-SPEC.md) out of `novo/page.tsx`'s submit button — not required by this task's acceptance criteria, keeps the change minimal.
- `Merge` (not the `GitMerge` fallback) is available in the installed `lucide-react@0.543.0` — verified directly against `node_modules` before use, so no fallback substitution was needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree branch was 88 commits stale, missing the entire phase-115 plan/context/UI-spec files**
- **Found during:** Task startup (`files_to_read` step) — `115-02-PLAN.md`, `115-CONTEXT.md`, `115-UI-SPEC.md` all reported "File does not exist" despite the prompt directing me to them.
- **Issue:** `git worktree list` and `git rev-list --count HEAD..master` confirmed this worktree's branch (`worktree-agent-ae51299f8e01a2c71`) was checked out at commit `70ff067`, a strict ancestor of `master` (`776defc`), 88 commits behind — missing all of phases 111-115 including the plan files this execution depends on. This is the exact known failure mode already documented in `PROJECT.md`'s Key Decisions ("Um agente executor spawnado com isolamento de worktree apontou para um checkout desatualizado sem os commits de planeamento recentes, bloqueando a execução").
- **Fix:** Verified the situation was a clean, lossless fast-forward before acting: `git rev-list --count master..HEAD` = 0 (no unique commits on my branch), `git status --short` = clean working tree, HEAD confirmed on the correct `worktree-agent-*` branch (not detached, not a protected branch). Ran `git reset --hard 776defc52252f1d07a312e8805627b1dac4e93ac` (master's exact tip commit hash, not the floating branch name, to avoid any TOCTOU ambiguity). This is the explicitly sanctioned recovery path per this execution's own `<worktree_branch_check>` instruction ("Only after this assertion passes is `git reset --hard` to correct a base drift safe"). Re-verified post-reset that HEAD was still on `worktree-agent-ae51299f8e01a2c71` (not moved to `master`, not detached) before proceeding.
- **Files modified:** None (this was a branch-pointer operation, not a file edit; it only affected `refs/heads/worktree-agent-ae51299f8e01a2c71`, isolated from `master` and every sibling worktree's own branch ref).
- **Verification:** Post-reset, `.planning/phases/LEXCV-115-.../115-02-PLAN.md` and its siblings were readable; all cited line numbers in `115-UI-SPEC.md` (e.g. `clientes/page.tsx:265`, `:269`, `:364`) matched the actual file content exactly, confirming the reset landed on the correct, current base.
- **Committed in:** N/A — no commit needed (branch-ref-only change, not a working-tree change).

## TDD Gate Compliance

Task 1 carries a `tdd="true"` attribute, but this task's own `<verify>` block specifies grep-based source assertions (not an executable test suite), and `web/package.json` confirms no test framework (Jest/Vitest/React Testing Library) exists anywhere in this frontend — consistent with all prior UI phases (101-114), none of which introduced one. Installing a net-new test framework to satisfy a literal RED/GREEN commit pair for a single icon-only visual change would be a disproportionate, out-of-scope architectural addition not requested by this plan (and the `MVP_MODE`/`TDD_MODE` flags that would trigger the stricter MVP+TDD runtime gate were not passed to this execution). Task 1 was instead verified via the plan's own grep-based acceptance checks (all passed, see Task Commits/Accomplishments) and a full `pnpm lint` pass, producing a single `feat` commit rather than a `test`→`feat` pair. No RED-gate `test(...)` commit exists for this task; flagging here for visibility rather than silently diverging from the documented TDD flow.

## Issues Encountered
- `node_modules` was absent in this fresh worktree checkout (expected — gitignored, not shared across worktrees). Ran `pnpm install --prefer-offline` in `web/`, which resolved instantly against the already-populated local pnpm store (no network fetch needed) and left `pnpm-lock.yaml` unchanged (confirmed via `git diff --stat`). This was necessary to run `pnpm lint` per the plan's `<verification>` section.
- `pnpm lint` is a full-project lint (not file-scoped); it reported 6 pre-existing errors / 17 warnings across 14 files unrelated to this plan's 3 touched files (e.g. `react-hooks/incompatible-library` in `clientes/[id]/page.tsx`, `dashboard-shell.tsx`, etc.), including one pre-existing `react-hooks/incompatible-library` warning already present in `clientes/novo/page.tsx` before this plan touched it. Confirmed identical issue count/rule in two separate lint runs (before and after Task 2's edits) to prove nothing in this plan's scope introduced or worsened it. Per Scope Boundary, left untouched — not caused by this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Clientes module's FICO-01 and ICON-01 work is complete; ready for the phase-level human-verify checkpoint (Plan 11) alongside the other 9 module plans in this wave.
- No blockers introduced for sibling plans — `files_modified` for this plan (`clientes/page.tsx`, `clientes/novo/page.tsx`, `clientes/merge/page.tsx`) has zero overlap with any other 115-0X plan per the plan-checker's confirmed exclusive ownership.
- Worth flagging to the orchestrator: at least 5 other sibling worktrees observed via `git worktree list` were also pinned at the same stale `70ff067` base at the time this plan started (before this plan's own reset) — other parallel agents in this wave may hit the identical missing-plan-file blocker and should apply the same sanctioned `git reset --hard` recovery documented above.

---
*Phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only*
*Completed: 2026-07-22*

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/clientes/page.tsx
- FOUND: web/src/app/(dashboard)/clientes/novo/page.tsx
- FOUND: web/src/app/(dashboard)/clientes/merge/page.tsx
- FOUND: .planning/phases/LEXCV-115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only/115-02-SUMMARY.md
- FOUND commit: c7f55ba (Task 1)
- FOUND commit: 38b17e8 (Task 2)
