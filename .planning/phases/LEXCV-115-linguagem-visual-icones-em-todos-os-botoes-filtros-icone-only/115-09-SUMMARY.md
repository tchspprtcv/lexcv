---
phase: LEXCV-115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
plan: 09
subsystem: ui
tags: [lucide-react, icons, pareceres, notificacoes, accessibility]

# Dependency graph
requires: []
provides:
  - "Pareceres list (pareceres/page.tsx) — Plus/Check/X icons on Nova Solicitacao and both filter panels' Aplicar/Limpar-equivalent buttons, text preserved"
  - "Notificacoes (notificacoes/page.tsx) — X icon on 'Limpar filtros', text preserved"
  - "Parecer detail (pareceres/[id]/page.tsx) — Send icon on Submeter Versao and Entregar Parecer"
  - "Parecer create (pareceres/nova/page.tsx) — X on both Cancelar, Plus/Loader2 on create submit"
affects: [115-11-do-not-regress-spot-check]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ICON-01 icon+text buttons in modules excluded from FICO-01 (Pareceres/Notificacoes): icon added as a JSX sibling before the text child, relying on Button's built-in `inline-flex items-center gap-2` — no wrapper span, no Tooltip, no size=\"icon\""
    - "Loader2 pending-state icon swap (ternary against isSubmitting/isPending, ternary text) reused verbatim from settings/page.tsx:773-777 precedent"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/pareceres/page.tsx
    - web/src/app/(dashboard)/notificacoes/page.tsx
    - web/src/app/(dashboard)/pareceres/[id]/page.tsx
    - web/src/app/(dashboard)/pareceres/nova/page.tsx

key-decisions:
  - "Left pareceres/[id]/page.tsx's AnexoLink 'Descarregar anexo' button (L121) untouched — it already carries a Paperclip icon + text (added 2026-07-01, predates this phase's 2026-07-21 audit), so it does not match the plan's 'gap' citation. 115-UI-SPEC.md Scope Boundaries S2 explicitly forbids re-touching/swapping already-correct icons, which takes precedence over the stale per-line citation."
  - "Applied the UI-SPEC's optional Loader2 pending-swap to nova/page.tsx's create-submit button only (the one button among this plan's scope with an isSubmitting/isPending boolean already wired to disabled), matching the plan's explicit invitation for that specific button."

patterns-established: []

requirements-completed: [ICON-01]

# Metrics
duration: ~20min
completed: 2026-07-22
---

# Phase 115 Plan 09: Pareceres + Notificacoes ICON-01 (icon+text, FICO-01 excluded) Summary

**Added Lucide icons (Plus/Check/X/Send, text preserved) to 11 of 12 ICON-01 gap buttons across Pareceres and Notificacoes — the two modules deliberately excluded from FICO-01's icon-only+Tooltip conversion.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-22T01:35:00Z (approx.)
- **Completed:** 2026-07-22T01:55:23Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- Pareceres list (`pareceres/page.tsx`): `Plus` on "Nova Solicitação"; `Check`/`X` on the dual-view panel's Aplicar/Limpar; `Check`/`X` on the advanced-search panel's Pesquisar/Limpar Filtros — all four keep visible text, none converted to icon-only, no `Tooltip` added (honors the FICO-01 module exclusion locked in `115-CONTEXT.md`)
- Notificações (`notificacoes/page.tsx`): `X` on "Limpar filtros", text preserved
- Parecer detail (`pareceres/[id]/page.tsx`): `Send` on "Submeter Versão" and "Entregar Parecer" (both are "submit to next workflow stage" actions); the irreversible-delivery `AlertDialog` confirmation (Cancelar/Confirmar Entrega) left byte-identical
- Parecer create (`pareceres/nova/page.tsx`): `X` on both Cancelar buttons; `Plus` (with `Loader2` pending-state swap) on the create-submit button
- All already-compliant buttons (Filter/Search in pareceres, CheckCheck/Check in notificacoes) left byte-identical
- `cd web && pnpm lint` on all 4 files: 0 errors; 1 pre-existing warning (`react-hooks/incompatible-library` on `form.watch`, unrelated to this task, present before this plan's edits)

## Task Commits

Each task was committed atomically:

1. **Task 1: Pareceres list + Notificações — icons WITH TEXT (no icon-only, no Tooltip)** - `27ee010` (feat)
2. **Task 2: Parecer detail + create pages** - `ff3fe5f` (feat)

**Plan metadata:** (this commit) — `docs(115-09): complete plan`

## Files Created/Modified
- `web/src/app/(dashboard)/pareceres/page.tsx` - Plus on Nova Solicitação; Check/X on both filter panels' apply/clear buttons
- `web/src/app/(dashboard)/notificacoes/page.tsx` - X on "Limpar filtros"
- `web/src/app/(dashboard)/pareceres/[id]/page.tsx` - Send on Submeter Versão + Entregar Parecer
- `web/src/app/(dashboard)/pareceres/nova/page.tsx` - X on both Cancelar; Plus/Loader2 on create submit

## Decisions Made
- Skipped adding an icon to `pareceres/[id]/page.tsx`'s "Descarregar anexo" button (the plan cited this as "L121 Download → gap"). On inspection it already has a `Paperclip` icon + text, added in commit `bebeed24` (2026-07-01), three weeks before this phase's 2026-07-21 UI-SPEC audit. Re-touching or swapping it would violate `115-UI-SPEC.md` Scope Boundaries §2 ("do not re-touch/swap icons that are already correct"), a general rule the plan's own `<context>` cites as authoritative. Treated the general do-not-touch rule as taking precedence over the one stale per-line citation.
- Applied the UI-SPEC's optional `Loader2` pending-icon-swap pattern (reused from `settings/page.tsx:773-777`) to the create-submit button in `nova/page.tsx`, since the plan explicitly invited it there and the `isSubmitting`/`isPending` boolean was already wired to `disabled` — free completion, no new state introduced.
- Mapped the advanced-search panel's "Pesquisar"/"Limpar Filtros" buttons to the `Check`/`X` "Aplicar"/"Limpar" family per the plan's task text (which cites them as L370/L377 Aplicar/Limpar) — semantically these are the affirmative-submit and clear-filters actions for that panel, consistent with `115-UI-SPEC.md`'s vocabulary ("Confirmar ... same glyph as Aplicar").

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — plan premise did not match ground truth] `pareceres/[id]/page.tsx` L121 "Descarregar anexo" was already ICON-01 compliant, not a gap**
- **Found during:** Task 2 (`pareceres/[id]/page.tsx`)
- **Issue:** The plan (and `115-UI-SPEC.md`'s audit table) cited this button as "0 compliant, gap: L121 Download → `Download`". On reading the file, the button already renders `<Paperclip className="h-3 w-3" />` before its "Descarregar anexo" text — added by commit `bebeed24` on 2026-07-01, predating the phase's own 2026-07-21 audit. The audit table appears to have mis-tallied this one line.
- **Fix:** Left the button untouched. Did not import an unused `Download` (would fail lint's unused-vars check and would violate Scope Boundaries §2's explicit "do not swap already-correct icons" instruction).
- **Files modified:** none (intentional non-change)
- **Verification:** `git blame` confirmed the `Paperclip` icon predates this phase by ~3 weeks; `pnpm lint` on the file is clean.
- **Committed in:** `ff3fe5f` (Task 2 commit message documents this explicitly)

---

**Total deviations:** 1 (plan-premise correction, no code risk)
**Impact on plan:** Zero functional/security impact — the underlying ICON-01 goal (every button communicates its action via a consistent icon) was already satisfied at that one call site before this plan started. No scope creep; if anything, this avoided introducing a redundant/conflicting second icon on an already-correct button.

## Issues Encountered
- **Worktree base drift (pre-existing infra issue, not a plan defect):** This worktree's branch (`worktree-agent-ae7666def1108e3bf`) was created before the phase 115 planning commits landed on `master` — `.planning/phases/LEXCV-115-.../` did not exist in the worktree at task start (88 commits behind `master`, zero unique commits of its own). Per the `<worktree_branch_check>` protocol (HEAD confirmed on a proper `worktree-agent-*` branch, not a protected ref), resolved with `git reset --hard master` — a safe fast-forward since the branch had no commits to lose. This matches a previously-logged project decision (see `PROJECT.md` Key Decisions: "Um agente executor spawnado com isolamento de worktree apontou para um checkout desatualizado sem os commits de planeamento recentes").
- **Fresh worktree had no `node_modules`:** `web/node_modules` does not exist in a newly created worktree (gitignored, not shared). Ran `pnpm install` (bare, against the committed `pnpm-lock.yaml` — not a new package reference, so outside the Rule 3 package-legitimacy exclusion) to materialize it before `pnpm lint` could run.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 12 ICON-01 gaps in Pareceres + Notificações addressed (11 newly iconed, 1 already compliant pre-phase).
- FICO-01 module exclusion honored throughout: zero `size="icon"` additions, zero new `TooltipTrigger` usages in either file.
- Plan 11's "do-not-regress spot-check" (per `115-09-PLAN.md` `<verification>`) can include these 4 files; nothing here should need revisiting.
- No blockers for sibling wave-1 plans — this plan's 4 files had zero overlap with other 115-0X plans per the plan-checker.

---
*Phase: LEXCV-115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only*
*Completed: 2026-07-22*

## Self-Check: PASSED

All 4 modified source files confirmed present on disk; this SUMMARY.md confirmed present; both task commits (`27ee010`, `ff3fe5f`) confirmed in `git log`.
