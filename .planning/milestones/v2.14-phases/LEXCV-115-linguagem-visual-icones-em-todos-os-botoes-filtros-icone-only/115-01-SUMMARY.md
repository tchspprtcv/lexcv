---
phase: LEXCV-115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
plan: 01
subsystem: ui
tags: [lucide-react, icons, accessibility, react, nextjs, shared-components]

# Dependency graph
requires:
  - phase: LEXCV-108/109 (v2.13)
    provides: Tooltip primitive already mounted app-wide (not used directly by this plan, but confirms readiness for sibling FICO-01 plans)
provides:
  - ChevronLeft/ChevronRight icons on the shared DataTable pagination footer (propagates to every list page: Clientes, Processos, Documentos, Financeiro, Pareceres)
  - ArrowLeft icon on the shared AccessDeniedState "Voltar" button (propagates to every RBAC-gated access-denied screen app-wide)
  - Check icon on the notification snooze "Adiar" confirm button
  - X/Save icons on both profile settings forms (Informações Pessoais + Alterar Palavra-passe)
affects: [LEXCV-115-02, LEXCV-115-03, LEXCV-115-04, LEXCV-115-05, LEXCV-115-06, LEXCV-115-07, LEXCV-115-08, LEXCV-115-09, LEXCV-115-10, LEXCV-115-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Icon-before-text with h-4 w-4 sizing inside Button (matches the 44 pre-existing compliant instances)"
    - "asChild Button + Link: icon element goes inside the Link, as a sibling of the label text (not a direct Button child)"
    - "Pending-state icon swap: Loader2 (animate-spin) while isPending, actionable icon (Save) while idle, with the visible text kept as a static sibling outside the ternary"

key-files:
  created: []
  modified:
    - web/src/components/shared/data-table/data-table-pagination.tsx
    - web/src/components/shared/access-denied-state.tsx
    - web/src/components/shared/notificacao-snooze-control.tsx
    - web/src/components/profile/user-profile-form.tsx
    - web/src/components/profile/user-password-form.tsx

key-decisions:
  - "Worktree branch was behind master by 45 commits (missing all phase 115 planning docs) — corrected via git reset --hard master per the worktree_branch_check step, since HEAD was already confirmed on the correct worktree-agent-* branch with zero unique commits to lose."
  - "user-profile-form.tsx's Guardar button was already fully compliant (Save + Loader2 pending-swap) despite the plan/UI-SPEC audit listing it as a gap — left untouched, only added X to the Cancelar button, per the do-not-touch-already-compliant-code rule."
  - "Applied UI-SPEC's optional pending-state icon-swap pattern (Loader2 while pending) to user-password-form.tsx's submit button while adding its Save icon, since mutation.isPending was already threaded into disabled — mirrors the exact pattern user-profile-form.tsx's Guardar button already uses."

patterns-established:
  - "Icon-only-had-no-icon buttons that already have a pending boolean at hand get the Loader2 pending-swap opportunistically when touched for an unrelated icon addition, rather than leaving the pending state text-only."

requirements-completed: [ICON-01]

# Metrics
duration: 4min
completed: 2026-07-22
---

# Phase 115 Plan 01: Shared/Leaf Component Icons (Highest-Leverage) Summary

**Added Lucide icons (ChevronLeft/ChevronRight, ArrowLeft, Check, X, Save) to 5 shared/leaf components — the DataTable pagination and access-denied fixes alone propagate to every list page and every RBAC-gated screen app-wide.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-07-22T01:37:02Z
- **Completed:** 2026-07-22T01:41:25Z
- **Tasks:** 2 completed
- **Files modified:** 5

## Accomplishments
- `data-table-pagination.tsx`: Anterior/Seguinte buttons now show `ChevronLeft`/`ChevronRight` — reflected on every `DataTable` list page (Clientes, Processos, Documentos, Financeiro, Pareceres) from this single fix
- `access-denied-state.tsx`: Voltar button now shows `ArrowLeft` inside its `asChild` Link — reflected on every RBAC-gated access-denied screen app-wide
- `notificacao-snooze-control.tsx`: Adiar confirm button now shows `Check` (Clock trigger left untouched)
- `user-profile-form.tsx` / `user-password-form.tsx`: Cancelar/Limpar buttons gained `X`; password-form submit gained `Save` (with the same Loader2-while-pending swap already used by the profile form's Guardar button)

## Task Commits

Each task was committed atomically:

1. **Task 1: Chevron pagination + access-denied Voltar (highest leverage)** - `648005b` (feat)
2. **Task 2: Snooze confirm + profile forms** - `36f73f0` (feat)

**Plan metadata:** SUMMARY commit follows this document.

## Files Created/Modified
- `web/src/components/shared/data-table/data-table-pagination.tsx` - Added `ChevronLeft`/`ChevronRight` before/after Anterior/Seguinte text
- `web/src/components/shared/access-denied-state.tsx` - Added `ArrowLeft` inside the Voltar Link, before `{backLabel}`
- `web/src/components/shared/notificacao-snooze-control.tsx` - Added `Check` before the Adiar/"A adiar..." text
- `web/src/components/profile/user-profile-form.tsx` - Added `X` to the Cancelar button (Guardar was already compliant)
- `web/src/components/profile/user-password-form.tsx` - Added `X` to Limpar; added `Save` (+ existing Loader2 pending-swap) to the submit button

## Decisions Made
- Kept the snooze-confirm button's existing pending-text ("A adiar..." / "Adiar") untouched, prepending `Check` unconditionally before it rather than inventing a Loader2 swap not explicitly requested for that file — stayed tightly scoped to the plan's literal action text.
- For `user-password-form.tsx`'s submit button, restructured the pending ternary so the icon (Loader2/Save) swaps while the "Atualizar Palavra-passe" text stays a static, always-visible sibling — this both adds the required `Save` icon and fixes a minor pre-existing UX quirk (text used to vanish entirely during pending) by aligning it with the identical pattern already used in `user-profile-form.tsx`'s Guardar button and `settings/page.tsx` (both cited as the reference pattern in 115-UI-SPEC.md).

## Deviations from Plan

### Environment correction (not a code deviation)

**1. Worktree branch was 45 commits behind master, missing all Phase 115 planning docs**
- **Found during:** Initial file reads (Task setup, before Task 1)
- **Issue:** `115-01-PLAN.md`, `115-CONTEXT.md`, `115-UI-SPEC.md` did not exist in this worktree — the worktree-agent branch was created from an older `master` commit (`70ff067`) that predates the phase 115 planning commits (`ef2f09a`..`776defc`).
- **Fix:** Verified HEAD was on the correct `worktree-agent-a97587c25fb02d427` branch (not a protected branch) and that the branch had zero unique commits that would be lost, then ran `git reset --hard master` to pick up the missing planning docs, per this executor's `worktree_branch_check` step.
- **Files modified:** None (git ref only)
- **Verification:** `.planning/phases/LEXCV-115-.../115-01-PLAN.md` and siblings became readable immediately after; `git log --oneline -3` confirmed HEAD at `776defc`.

### Plan-evidence discrepancy (informational, no Rule-tagged fix needed)

**2. `user-profile-form.tsx`'s Guardar button was already icon-compliant, contrary to the plan/UI-SPEC audit**
- **Found during:** Task 2 read_first (reading `user-profile-form.tsx` before editing)
- **Issue:** 115-UI-SPEC.md's audit table listed this file as "0 compliant / 2 gaps" (Cancelar, Guardar), and the plan's task action said to add a new `Save`/`X` import "no lucide import exists today." In the live file, `Camera, Loader2, Save` were already imported and the Guardar submit button already had the full Loader2-while-pending / Save-while-idle swap wired to `mutation.isPending`.
- **Resolution:** Did not re-touch the already-compliant Guardar button (would violate the "do not re-touch already-compliant code" rule from 115-UI-SPEC.md Scope Boundaries §2, applied here as the same-spirit guard even though this specific file wasn't in the do-not-touch list). Only added the genuinely missing `X` icon to the Cancelar button, adding `X` to the existing import line rather than introducing a redundant one.
- **Files modified:** `web/src/components/profile/user-profile-form.tsx` (same file already in `files_modified`, no scope expansion)
- **Verification:** `grep` confirms `X` imported once and used once on Cancelar; Guardar/Save/Loader2 block byte-identical to before (`git diff` shows only the Cancelar hunk touched).

---

**Total deviations:** 1 environment correction (pre-execution, not a code change) + 1 informational plan-evidence discrepancy (reduced scope, no extra risk).
**Impact on plan:** No scope creep — the environment fix was a prerequisite to seeing the plan at all, and the evidence discrepancy resulted in *less* code touched than the plan assumed, not more.

## Issues Encountered
- `pnpm lint` could not run — `node_modules` does not exist in this freshly created worktree (pre-existing environment condition, not caused by this task's changes; out of scope to fix per the deviation rules' scope boundary — installing the full dependency tree was judged out of scope for a 5-file icon-only change). Substituted with the plan's own grep-based acceptance-criteria checks (all passed) plus a manual `git diff` review of every changed hunk for syntax correctness.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `data-table-pagination.tsx` and `access-denied-state.tsx` fixes are shared components — sibling module plans (115-02 through 115-11) do not need to touch pagination or access-denied icons again; they inherit this fix automatically.
- No blockers for sibling plans in this wave (zero file overlap by design, confirmed via `git diff --name-only` showing only this plan's 5 `files_modified` touched).
- `pnpm lint`/`pnpm build` verification against these 5 files is still recommended once a `node_modules` install is available in this worktree or on the merged branch.

## Self-Check: PASSED

All 5 modified source files and the SUMMARY.md itself confirmed present on disk; both task commits (`648005b`, `36f73f0`) confirmed present in `git log`.

---
*Phase: LEXCV-115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only*
*Completed: 2026-07-22*
