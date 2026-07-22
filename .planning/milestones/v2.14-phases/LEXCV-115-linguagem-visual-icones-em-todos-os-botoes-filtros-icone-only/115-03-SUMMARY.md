---
phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only
plan: "03"
subsystem: ui
tags: [lucide-react, icons, accessibility, react, next.js, cliente-ficha]

# Dependency graph
requires:
  - phase: 101-fundacao-shadcn-ui
    provides: "lucide-react already the exclusive icon library across the app; no new package needed"
provides:
  - "All 31 ICON-01 gap buttons in web/src/app/(dashboard)/clientes/[id]/page.tsx closed with vocabulary-correct lucide-react icons, all visible text preserved"
  - "Single consolidated lucide-react import (10 names: ArrowLeft, Check, Download, Pencil, Plus, Printer, Save, Trash2, Upload, X)"
affects: [115-01, 115-02, 115-04, 115-05, 115-06, 115-07, 115-08, 115-09, 115-10, 115-11]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "asChild+Link icon placement: icon element goes INSIDE <Link> as first child, not inside <Button>, for Button asChild wrapping a Link (Voltar top-toolbar button)"
    - "Icon className stays bare h-4 w-4 (no mr-2) — Button's own gap-2 (button.tsx buttonVariants base) handles spacing; matches the newer/majority codebase convention over the older Printer mr-2 style"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/clientes/[id]/page.tsx

key-decisions:
  - "Extended the lucide-react import with Download and Trash2 beyond the plan's literal 7-name list (ArrowLeft/Pencil/Save/X/Plus/Check/Upload) — 5 of the citations bundled under the plan's 'inline Contactos edit' label are actually a Procuracao 'Ver / Download' button and 4 'Remover' buttons (Procuracao, ResponsaveisCard x2, Contactos, Notas), which the UI-SPEC's full Icon Vocabulary Contract maps to Download/Trash2, not the task's abbreviated 5-verb subset. Using the wrong icon would have violated ICON-01's own correctness goal."
  - "Applied the plan's 'Deterministic vocabulary' rule literally: every button whose visible text is literally 'Adicionar' maps to Plus, whether it is a dialog trigger or the dialog's own submit button (ResponsaveisCard L1727, ClienteContactosCard L1849, ClienteNotasCard 'Adicionar nota') — resolves an apparent tension with the broader UI-SPEC table's looser 'Confirmar / Adicionar (dialog's own submit) -> Check' phrasing by following the plan's own more specific, explicitly-labeled-deterministic instruction for this file."
  - "tipo-change dialog's affirmative button (visible text 'Continuar', not 'Confirmar') mapped to Check per the plan's explicit line-level instruction (L1055/L1056 Cancelar->X, Confirmar->Check) — visible text left unchanged, only the icon follows the semantic 'dialog affirmative submit' mapping."
  - "Skipped the optional Loader2 pending-state icon swap (mentioned as optional in the UI-SPEC vocabulary contract) everywhere, including the top-toolbar Guardar button where isSaving was available — kept every icon static to keep a 31-button mechanical pass low-risk and fully covered by the plan's literal acceptance criteria."

requirements-completed: [ICON-01]

# Metrics
duration: ~35min
completed: 2026-07-22
---

# Phase 115 Plan 03: Cliente Ficha Icon Gaps (ICON-01) Summary

**Closed all 31 ICON-01 icon gaps in the Cliente ficha (`clientes/[id]/page.tsx`, the heaviest single file in Phase 115) — top toolbar, Doc-a-Tratar/Deslocacao/tipo-change/Documento-upload dialog groups, Procuracao, Advogados/Administrativos, and inline Contactos/Notas edit rows all now carry vocabulary-correct lucide-react icons with 100% of visible text preserved.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 2/2 completed
- **Files modified:** 1

## Accomplishments

- Extended the file's single `lucide-react` import from `{ Printer }` to 10 names (`ArrowLeft, Check, Download, Pencil, Plus, Printer, Save, Trash2, Upload, X`).
- Top toolbar (Voltar/Cancelar/Guardar/Editar) now carries icons; the already-compliant "Imprimir Ficha" (`Printer`, `mr-2` styling) confirmed byte-identical throughout both tasks.
- Doc-a-Tratar, Deslocacao, tipo-change-confirm, and Documento-upload dialog groups (trigger + Cancelar + Confirmar/Continuar/Upload-submit) all gained icons.
- Procuracao card ("Ver / Download" -> `Download`, "Remover" -> `Trash2`) and the shared `ResponsaveisCard` (used for both Advogados and Administrativos: Adicionar-trigger/Adicionar-confirm -> `Plus`, Remover -> `Trash2`, Cancelar -> `X`) closed.
- Inline Contactos and Notas edit rows (Adicionar/Guardar/Cancelar/Editar/Remover, both cards share an identical structure) closed with the same 5-icon mapping.
- Final count: 31 new `className="h-4 w-4"` icon usages, exactly one `lucide-react` import line, zero visible text removed anywhere (verified via full `git diff` review against the pre-plan baseline).

## Task Commits

Each task was committed atomically:

1. **Task 1: Top toolbar + Doc-a-Tratar/Deslocacao/tipo-change/Documento-upload groups** - `c517e13` (feat)
2. **Task 2: Inline Contactos edit + inline Notas edit groups** - `af2e174` (feat)

**Plan metadata:** _(this SUMMARY's own commit)_

## Files Created/Modified

- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - 31 ICON-01 gap buttons closed (top toolbar, 4 dialog groups, Procuracao, Responsaveis, Contactos, Notas); import extended to 10 lucide-react names; zero visible text removed.

## Decisions Made

See frontmatter `key-decisions` for full detail. Summary:
- Added `Download`/`Trash2` to the import beyond the plan's literal 7-name list, because several citations the plan grouped under "inline Contactos edit" are actually Procuracao/ResponsaveisCard "Ver/Download" and "Remover" buttons requiring those icons per the UI-SPEC's full Icon Vocabulary Contract.
- Applied "Adicionar text -> Plus, always" deterministically (trigger and dialog-submit alike), per the plan's own explicitly-labeled "Deterministic vocabulary" instruction.
- Mapped tipo-change's "Continuar" button to `Check` per the plan's explicit line-level instruction, despite the literal text not being "Confirmar".
- Skipped the optional Loader2 pending-swap everywhere to keep the 31-button pass mechanical and low-risk.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Base-drift recovery: worktree branch was 88 commits behind local master, missing all Phase 115 planning files**
- **Found during:** Startup (`worktree_branch_check` + `files_to_read` step) — every `.planning/phases/LEXCV-115-*` file failed to read with "File does not exist"
- **Issue:** The worktree's branch (`worktree-agent-a8279e9d511f1ac42`) was created from a stale `origin/master` that predates the Phase 115 planning commits present on local `master`. `git merge-base --is-ancestor` confirmed the worktree branch was a strict, conflict-free ancestor of local `master` (0 unique commits on the worktree branch, 88 commits behind) — a pure fast-forward base-drift, not a divergent-history conflict.
- **Fix:** `git reset --hard master` (working tree was clean, HEAD confirmed on the `worktree-agent-*` branch and not on any protected branch beforehand, per the mandatory `worktree_branch_check`). This only moved the local `worktree-agent-a8279e9d511f1ac42` ref forward to the shared commit already on `master`; `refs/heads/master` itself (checked out in another worktree) was never touched.
- **Files modified:** none (ref-only operation, no working-tree file changes beyond picking up the already-committed planning docs)
- **Verification:** `.planning/phases/LEXCV-115-*` files all readable afterward; `git log --oneline -5` showed the expected Phase 115 planning commits as HEAD's ancestry.
- **Committed in:** n/a (ref move, no new commit)

**2. [Rule 2 - Missing Critical] Two icon names (`Download`, `Trash2`) required beyond the plan's literal import list**
- **Found during:** Task 2, reading the actual JSX at each cited line as the plan's own context section instructs ("read the actual JSX at each cited line before editing and map its VISIBLE TEXT/context to the vocabulary")
- **Issue:** The plan's "Import discipline" note said Task 1's 7-name import (`ArrowLeft, Pencil, Save, X, Plus, Check, Upload`) would cover both tasks. Byte-reading the cited lines showed 5 of the 16 Task-2 citations are actually a Procuracao "Ver / Download" button and 4 "Remover" buttons (Procuracao, ResponsaveisCard x2 total across Advogados+Administrativos render sites, Contactos, Notas) — none of which fit the plan's closed Editar/Adicionar/Cancelar/Guardar/Confirmar subset. The UI-SPEC's full Icon Vocabulary Contract explicitly names `Download` for this exact citation (`clientes/[id]/page.tsx:1546`) and `Trash2` for Remover/Apagar/Eliminar generally.
- **Fix:** Added `Download` and `Trash2` to the single lucide-react import (done once, in Task 1's import edit, so Task 2 still never touched the import line — satisfying that acceptance criterion literally).
- **Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` (import line only)
- **Verification:** `pnpm lint` (after a fresh `pnpm install`, see Issues Encountered) reports zero unused-import or undefined-name issues for this file.
- **Committed in:** `c517e13` (Task 1 commit, since the import was extended there)

---

**Total deviations:** 2 auto-fixed (1 bug/blocking base-drift recovery, 1 missing-critical vocabulary correction)
**Impact on plan:** Both were necessary for the plan to be executable at all (base drift) and for ICON-01's actual correctness goal (right icon per real button semantics, not just any icon). No scope creep — file list, task boundaries, and button count (31) are unchanged from the plan.

## Issues Encountered

- **No `node_modules` in this worktree.** `pnpm lint` initially failed with `eslint not found` because dependencies were never installed in this fresh worktree checkout. Ran `pnpm install` (existing `pnpm-lock.yaml`, no `package.json` changes — a dependency hydration, not a new-package install, so the Rule 3 package-legitimacy exclusion does not apply) in the background while continuing Task 2's edits, then ran `pnpm lint` successfully once it completed.
- **4 pre-existing lint findings in the target file, confirmed unrelated to this plan's edits and left untouched (out of scope per the Scope Boundary rule):**
  - `react-hooks/incompatible-library` warning at `form.watch("tipo")` (originally line 372, pre-dates this plan)
  - `react-hooks/set-state-in-effect` errors (3x) at `setModalOpen(false)`/`setEditingId(null)` inside three `React.useEffect` cleanup-on-exit-edit-mode blocks (`ResponsaveisCard`, `ClienteContactosCard`, `ClienteNotasCard`)
  - Verified via `git show 776defc:"web/src/app/(dashboard)/clientes/[id]/page.tsx"` (the pre-plan baseline commit) that all four patterns existed byte-for-byte before any of this plan's edits, and via `git diff --unified=0` that none of my 18 edit hunks touch these lines (every hunk is a pure single-line insertion of an icon element, `@@ -N,0 +M @@`).
  - These exact same four findings (same rules, same file) were also independently confirmed pre-existing and out-of-scope in `114-01-SUMMARY.md`'s "Issues Encountered" section — consistent cross-plan evidence this is genuine pre-existing debt, not something introduced by any Phase 115 sibling plan.
  - Also present project-wide (not in this plan's file): `@next/next/no-img-element` (7x), `@typescript-eslint/no-unused-vars` (2x, in `processos/[id]/page.tsx`), `react-hooks/refs` (1x) — all in files outside this plan's `files_modified` scope, not investigated further.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All 31 ICON-01 gaps in `clientes/[id]/page.tsx` are closed; this file needed zero FICO-01 work (confirmed zero filter buttons in this file, per the plan's own objective statement).
- No blockers for sibling Phase 115 plans (115-01/02/04-11) — this plan touched exactly one file (`web/src/app/(dashboard)/clientes/[id]/page.tsx`), confirmed zero overlap with any other plan's `files_modified` by the plan-checker before parallel dispatch.
- Visual/keyboard verification (icon legibility in both themes, `Tab`-only pass) is called out in `115-UI-SPEC.md`'s Verification/QA Plan as spot-check items for the phase as a whole ("clientes/[id]/page.tsx (31 gaps): spot-check both the top toolbar (Voltar/Editar) and at least one of the 6 repeated dialog groups") — not performed here, left for the phase-level verifier/human-UAT pass.

---
*Phase: 115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only*
*Completed: 2026-07-22*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/clientes/[id]/page.tsx`
- FOUND: `.planning/phases/LEXCV-115-linguagem-visual-icones-em-todos-os-botoes-filtros-icone-only/115-03-SUMMARY.md`
- FOUND commit: `c517e13` (Task 1)
- FOUND commit: `af2e174` (Task 2)
