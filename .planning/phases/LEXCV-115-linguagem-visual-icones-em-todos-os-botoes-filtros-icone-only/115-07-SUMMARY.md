---
phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
plan: "07"
subsystem: ui
tags: [lucide-react, tooltip, accessibility, shadcn, documentos]

# Dependency graph
requires:
  - phase: 101-fundacao-shadcn-ui
    provides: "shadcn CLI foundation, Tooltip primitive available"
  - phase: 108-modulo-pareceres
    provides: "Tooltip primitive first put into real use (app-wide TooltipProvider mounted at root, delayDuration=700)"
provides:
  - "Documentos filter bar: Filtrar (Aplicar semantic, Check) and Limpar (X) converted to icon-only Button+Tooltip+aria-label with unified 'Aplicar filtros'/'Limpar filtros' copy — FICO-01 for this module"
  - "Documentos module ICON-01 gaps closed: Upload trigger, 3× Apagar (mobile card, column row-action, detail page), Voltar ×2, Enviar, Cancelar, Download (icon+text add)"
  - "columns.tsx row-action Apagar converted from text-only to icon-only Trash2+Tooltip, matching the already-compliant sibling Download action"
affects: [115-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "FICO-01 icon-only+Tooltip conversion: Tooltip > TooltipTrigger asChild > Button size=\"icon\" aria-label=\"...\" > Icon, followed by sibling TooltipContent with identical copy — copied verbatim from 115-UI-SPEC.md's locked code pattern"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/documentos/page.tsx
    - web/src/app/(dashboard)/documentos/columns.tsx
    - web/src/app/(dashboard)/documentos/novo/page.tsx
    - web/src/app/(dashboard)/documentos/[id]/page.tsx

key-decisions:
  - "columns.tsx Apagar row-action kept its existing variant=\"outline\" (not switched to Download's variant=\"ghost\") when converting to icon-only — 'match the sibling Download' was read as matching the icon-only+Tooltip+aria-label structural pattern, not byte-copying every style prop, consistent with the UI-SPEC's own FICO-01 rule of preserving each button's pre-existing variant"
  - "Used size=\"icon\" (not the sibling Download's size=\"sm\") for the newly-converted Apagar action, per 115-UI-SPEC.md's explicit instruction: use size=\"icon\" for every new icon-only conversion, do not retrofit older hand-rolled sizing onto new work"

requirements-completed: [ICON-01, FICO-01]

# Metrics
duration: ~15min
completed: 2026-07-22
---

# Phase 115 Plan 07: Documentos Module Icons Summary

**Documentos filter bar (Filtrar/Limpar) converted to icon-only Check/X + Tooltip + aria-label, and all 9 remaining Documentos-module ICON-01 gap buttons (Upload, 3× Apagar, 2× Voltar, Enviar, Cancelar, Download) gained vocabulary-mapped Lucide icons with text preserved.**

## Performance

- **Duration:** ~15 min (includes stale-worktree recovery, see Issues Encountered)
- **Tasks:** 2/2 completed
- **Files modified:** 4

## Accomplishments

- Documentos filter bar's "Filtrar" (Aplicar semantic) and "Limpar" buttons converted to icon-only (`Check`/`X`), each wrapped in `Tooltip` with `aria-label` and `TooltipContent` carrying the unified copy ("Aplicar filtros" / "Limpar filtros") — satisfies FICO-01's AC4 (keyboard users get the same accessible name as mouse users) with no separate a11y pass.
- Confirmed no "Exportar" button was invented for Documentos (module has none per UI-SPEC) — negative assertion grep returned 0 matches.
- Upload trigger, mobile-card Apagar, and the `columns.tsx` row-action Apagar all gained icons (`Upload`, `Trash2`); the row-action Apagar was additionally converted to icon-only + Tooltip to match its already-compliant Download sibling, while all existing delete handlers (including the `window.confirm()` guards) were left untouched.
- `documentos/novo/page.tsx` (Voltar/Enviar/Cancelar) and `documentos/[id]/page.tsx` (Voltar/Download/Apagar) gained vocabulary-mapped icons (`ArrowLeft`, `Upload`, `X`, `Download`, `Trash2`) with all visible text and handlers preserved — these are ICON-01 icon+text additions, not row-action conversions.
- `pnpm lint`: `documentos/page.tsx`, `documentos/columns.tsx`, and `documentos/[id]/page.tsx` are all 0 errors / 0 warnings. `documentos/novo/page.tsx` carries 1 pre-existing error + 1 pre-existing warning, both on code this plan did not touch (see Issues Encountered — independently confirmed pre-existing by Phase 114's own SUMMARY.md).

## Task Commits

Each task was committed atomically:

1. **Task 1: Documentos list (FICO-01 + Upload + Apagar-mobile) + column Apagar** - `cb1345b` (feat)
2. **Task 2: Documento create + detail pages** - `8f28382` (feat)

**Plan metadata:** _(see final commit below)_

## Files Created/Modified

- `web/src/app/(dashboard)/documentos/page.tsx` - Filtrar/Limpar → icon-only Check/X + Tooltip + aria-label; Upload trigger and mobile-card Apagar gained icons
- `web/src/app/(dashboard)/documentos/columns.tsx` - Row-action Apagar converted to icon-only Trash2 + Tooltip, matching the sibling Download pattern
- `web/src/app/(dashboard)/documentos/novo/page.tsx` - Voltar (ArrowLeft), Enviar (Upload), Cancelar (X) — icon+text
- `web/src/app/(dashboard)/documentos/[id]/page.tsx` - Voltar (ArrowLeft), Download (Download icon+text add), Apagar (Trash2) — icon+text

## Decisions Made

- Kept `columns.tsx`'s Apagar action on its existing `variant="outline"` rather than switching to Download's `variant="ghost"` — interpreted "match the sibling Download" as matching the icon-only+Tooltip+aria-label *structure*, consistent with the FICO-01 rule elsewhere in the same UI-SPEC of preserving each button's pre-existing variant when converting to icon-only.
- Used `size="icon"` (not Download's `size="sm"`) for the new Apagar conversion, per the UI-SPEC's explicit instruction that new icon-only conversions should use `size="icon"` and not retrofit the older hand-rolled sizing convention.

## Deviations from Plan

None - plan executed exactly as written. All file:line references in the plan matched the actual codebase state exactly; no auto-fixes (Rules 1-3) or architectural questions (Rule 4) were triggered.

## Issues Encountered

- **Stale worktree base (pre-existing infra issue, not a plan defect):** this worktree's branch (`worktree-agent-adee8119ca5a99ac4`) was created from a commit that pre-dated all of Phase 115's planning commits (and Phases 113/114's execution) — the `.planning/phases/` directory didn't exist in the checkout at all, so the plan/context/UI-SPEC files referenced in this task's prompt could not be read. Diagnosed via `git log HEAD..master` (25+ missing commits) and `git log master..HEAD` (zero unique commits on this branch, clean working tree) — confirmed a pure ancestor/fast-forward relationship, exactly the "stale checkout without recent planning commits" failure mode already documented in `PROJECT.md`'s Key Decisions log. Per the executor's `<worktree_branch_check>` (which had already passed — HEAD was correctly on the `worktree-agent-*` branch, not a protected ref), recovered via `git reset --hard master`, which fast-forwarded with zero risk of losing work (nothing unique to lose). This was an environment/setup issue, not a plan or code defect.
- `node_modules` did not exist in this fresh worktree (gitignored, not shared across worktrees). Ran `pnpm install --frozen-lockfile` in `web/` to materialize it from the already-committed, unchanged `pnpm-lock.yaml` — required to run `pnpm lint` per the plan's verification step. No dependency was added or changed; `lucide-react` and `Tooltip` were already installed, exactly as `115-UI-SPEC.md` states ("this phase imports additional icon names from the already-installed lucide-react package, never a new package").
- `pnpm lint` reports 1 pre-existing error (`react-hooks/refs` on the `<form onSubmit={form.handleSubmit(onSubmit)}>` line) and 1 pre-existing warning (`@next/next/no-img-element` on the file-preview `<img>` element) in `documentos/novo/page.tsx`. Confirmed via `git diff` that neither line was touched by this plan's edits (diff shows only the import line and the 3 button/link JSX blocks changed) — the finding merely shifted line numbers due to unrelated additions earlier in the file. Independently corroborated: Phase 114's own `114-01-SUMMARY.md` already logged this exact `react-hooks/refs` finding in this exact file as pre-existing and out of scope, before Phase 115 existed. Out of scope per the deviation rules' scope boundary (fixing either would mean changing form-submission or image-loading behavior, well beyond this phase's "additive-visual only" icon boundary); not fixed, logged here for visibility only.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Documentos module is fully closed for both ICON-01 (all 11 gap buttons, including the 2 FICO-01 conversions) and FICO-01 (Filtrar/Limpar icon-only, no Exportar invented).
- Interactive/visual verification (Tooltip hover copy, keyboard-only Tab pass, light/dark contrast) is explicitly deferred to Plan 11 per this plan's own `<verification>` block — not performed here.
- No blockers for sibling module plans (Clientes/Processos/Agenda/Financeiro) or Plan 11 — this plan touched only the 4 Documentos files declared in its `files_modified` list, confirmed via `git status`/`git diff --stat` before each commit.

---
*Phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only*
*Completed: 2026-07-22*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/documentos/page.tsx`
- FOUND: `web/src/app/(dashboard)/documentos/columns.tsx`
- FOUND: `web/src/app/(dashboard)/documentos/novo/page.tsx`
- FOUND: `web/src/app/(dashboard)/documentos/[id]/page.tsx`
- FOUND: `.planning/phases/LEXCV-115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only/115-07-SUMMARY.md`
- FOUND commit: `cb1345b` (Task 1)
- FOUND commit: `8f28382` (Task 2)
