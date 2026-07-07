---
phase: 75-componente-nico-view-edit
plan: 01
subsystem: ui
tags: [nextjs, react-hook-form, zod, tanstack-query, clientes]

# Dependency graph
requires: []
provides:
  - "Unified `/clientes/[id]` page toggling read/edit via local `isEditing` boolean, no route navigation"
  - "Deletion of the standalone `/clientes/[id]/editar` route (no redirect shim)"
affects: [75-02-sub-component-edit-gating, 75-03-list-page-link-updates]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Merge-two-pages-into-one-with-boolean-toggle pattern for view/edit unification (no URL state)"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"
    - "web/src/app/(dashboard)/clientes/[id]/editar/page.tsx (deleted)"

key-decisions:
  - "Sub-components (ClienteContactosCard, ClienteNotasCard, ResponsaveisCard, ProcuracaoCard) intentionally left untouched (still gated only by canEditClientes, not isEditing) — their editable-prop gating is explicitly deferred to plan 75-02 per the plan's own instructions"
  - "Extracted buildDefaultValues(data: Cliente) as a useCallback shared by the load effect and onCancel, avoiding duplicated defaultValues-construction logic between initial load and cancel-revert"
  - "Used the exported Cliente type instead of `typeof cliente.data` in buildDefaultValues' parameter annotation to avoid a spurious react-hooks/exhaustive-deps warning caused by the type-only reference to `cliente`"

patterns-established:
  - "isEditing local boolean (not URL-reflected) drives conditional dl/dd-vs-form rendering per field group, matching CONTEXT.md's discretion clause"

requirements-completed: [CLI-12, CLI-13, CLI-14]

# Metrics
duration: ~35min
completed: 2026-07-04
---

# Phase 75 Plan 01: Componente Único View/Edit — Merge Summary

**Merged the standalone `/clientes/[id]/editar` form into `[id]/page.tsx` behind a local `isEditing` toggle (react-hook-form + Zod, `useUpdateCliente` save, in-place cancel) and deleted the now-orphaned `/editar` route file outright.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 2 completed
- **Files modified:** 2 (1 rewritten, 1 deleted)

## Accomplishments
- `/clientes/[id]` is now a single page: read mode (`dl`/`dd` grid, unchanged visually) by default, edit mode (react-hook-form + `buildClienteFormSchema` + `zodResolver`) behind an `Editar` button, with no route navigation for either save or cancel.
- Header button cluster implements the state machine from UI-SPEC.md section 1: `Voltar`/`Imprimir Ficha` always visible; `Editar` (default variant) in view mode; `Cancelar`(outline)/`Guardar`(default, "A guardar..." while pending) in edit mode.
- Save calls `useUpdateCliente(id).mutateAsync(payload)`, shows a success toast, and returns to read mode on the same page (TanStack Query cache already updated via the hook's existing `onSuccess`). Cancel calls `form.reset(...)` plus reverts the 3 staged intake lists (`documentosEntregues`/`documentosATratar`/`deslocacoes`) from `cliente.data`, with no `window.confirm` per CONTEXT.md.
- Deleted `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` outright — no redirect shim, no compatibility route.

## Task Commits

Each task was committed atomically:

1. **Task 1: Merge edit-mode logic + state machine into [id]/page.tsx** - `438a2a5` (feat)
2. **Task 2: Delete the orphaned /editar route file** - `fcf5dc8` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Rewritten to host both read-mode (`dl`/`dd`) and edit-mode (react-hook-form) rendering behind `isEditing`; adds `useUpdateCliente`, `onSubmit`/`onCancel` handlers, tipo-switch confirmation dialog, and the "Intake do Caso" edit-only section (descrição, honorários propostos, 3 staged lists with Dialog "Adicionar" modals). Sub-component call sites (`ClienteContactosCard`, `ClienteNotasCard`, `ResponsaveisCard`x2, `ProcuracaoCard`) and their inline function bodies were left exactly as they were before this plan — still gated only by `canEditClientes`.
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` - Deleted (740 lines removed); empty `editar/` directory no longer exists.

## Decisions Made
- Kept the four sub-components (`ClienteContactosCard`, `ClienteNotasCard`, `ResponsaveisCard`, `ProcuracaoCard`) completely untouched in this plan, as explicitly instructed ("Do NOT touch... Leave them exactly as-is for now"). Their `editable`/`isEditing` gating prop is plan 75-02's job. During drafting the merge, an `editable` prop was briefly added to all four (and their call sites) by over-eager pattern-matching against the UI-SPEC's Interaction Contract section 3 — this was caught and fully reverted before committing, restoring the original `canEditClientes`-only gating so plan 75-02 starts from a clean, unmodified baseline for those components.
- Kept page heading as "Cliente" in both modes (per UI-SPEC.md's stated recommendation, to avoid layout shift), rather than switching to "Editar cliente" in edit mode.
- Extracted a shared `buildDefaultValues(data: Cliente)` helper (via `useCallback`) used by both the cliente-load effect and `onCancel`, so the two must construct `defaultValues` identically — this wasn't explicitly prescribed by the plan but was the natural way to satisfy "onCancel: form.reset(defaultValues built from cliente.data)" without duplicating the ~20-line object literal.

## Deviations from Plan

### Auto-fixed Issues

**1. [Self-caught] Removed premature `editable` prop from four sub-components**
- **Found during:** Task 1 drafting (before commit)
- **Issue:** The UI-SPEC.md's Interaction Contract section 3 describes the `editable` prop contract for `ClienteContactosCard`/`ClienteNotasCard`/`ResponsaveisCard`/`ProcuracaoCard`, and it was applied inline while writing Task 1, contradicting the plan's explicit "Do NOT touch... in this task" instruction (that work belongs to plan 75-02).
- **Fix:** Reverted all `editable` prop additions, call-site changes, and `canEdit = canEditClientes && editable` derivations back to the original `canEditClientes`-only gating in all four components, before running verification or committing.
- **Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` (same file, pre-commit)
- **Verification:** `grep -n "editable" page.tsx` returns no matches; `tsc --noEmit` and `eslint` both pass with 0 errors after the revert.
- **Committed in:** `438a2a5` (part of Task 1 commit — the revert happened before the commit, so no separate commit was needed)

---

**Total deviations:** 1 self-caught-and-reverted (no net scope change; plan 75-02's starting point is unaffected)
**Impact on plan:** None on final code — the sub-components are byte-identical to their pre-plan state. Flagging for transparency since the draft momentarily went out of scope.

## Issues Encountered
- The worktree had no `node_modules` installed (fresh worktree, not shared with the main checkout). Ran `pnpm install --frozen-lockfile` in `web/` to enable `tsc --noEmit` and `eslint` verification — this is a normal dependency-install step, not a destructive or scope-affecting action, and matches the lockfile already committed to the repo.
- `pnpm tsc --noEmit` (as literally specified in the plan's `<verify>` block) fails because there's no `tsc` script in `package.json`; used `pnpm exec tsc --noEmit` instead, which resolves to the same TypeScript compiler installed via devDependencies.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 75-02 can proceed to add the `editable`/`isEditing`-gating prop to `ClienteContactosCard`, `ClienteNotasCard`, `ResponsaveisCard`, and the procuração `FileDropZone` block inside `ProcuracaoCard` — all four are confirmed untouched by this plan and still take only `canEditClientes`.
- Plan 75-03 can proceed to update the `clientes/page.tsx` (list page) `Link` hrefs that still point at `/clientes/[id]/editar` (2 occurrences found, lines ~450 and ~597) — confirmed out of scope for this plan.
- Live/manual UAT (toggle behavior, save/cancel round-trip against a real backend) was not performed in this environment — no `backend/.env` or `web/.env.local` configured in this worktree, consistent with the plan's own deferral of "Manual/visual" verification to `human_verify`.

---
*Phase: 75-componente-nico-view-edit*
*Completed: 2026-07-04*
