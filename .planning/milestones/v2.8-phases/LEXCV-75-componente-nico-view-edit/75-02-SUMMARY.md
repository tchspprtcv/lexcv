---
phase: 75-componente-nico-view-edit
plan: 02
subsystem: ui
tags: [nextjs, react, rbac, clientes]

# Dependency graph
requires:
  - phase: 75-01
    provides: "Unified /clientes/[id] page with local isEditing boolean toggle, merged read/edit rendering"
provides:
  - "editable: boolean prop on ClienteContactosCard, ClienteNotasCard, ResponsaveisCard, ProcuracaoCard"
  - "All inline CRUD affordances on those four sub-components AND-gated on canEditClientes && editable"
  - "5 call sites in ClienteDetailContent passing editable={isEditing}"
affects: [75-03-list-page-link-updates]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "AND-gate pattern: existing RBAC boolean (canEditClientes) ANDed with a new page-level UX toggle (editable) at every affordance guard, never replacing the RBAC check"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"

key-decisions:
  - "Applied editable guard inside internal event handlers (onCreate/onSaveEdit/onDelete/onAdd/onRemove/onFileChange) in addition to the render-time JSX guards, matching the existing pre-plan pattern where canEditClientes was already checked in both places (defense in depth against a stray call, not a security boundary — server-side @PreAuthorize remains the real gate per the plan's threat model)"
  - "Left Ver/Download button on ProcuracaoCard completely ungated by editable, per UI-SPEC.md section 3 and the plan's explicit instruction — it remains a read action visible in both view and edit mode"
  - "Did not touch web/src/components/shared/file-drop-zone.tsx — confirmed no diff after both tasks"

patterns-established:
  - "editable prop naming (not readOnly) used consistently across all four sub-components, matching the codebase's existing positive-framing convention (canEditClientes, canViewClientes)"

requirements-completed: [CLI-13]

# Metrics
duration: ~20min
completed: 2026-07-04
---

# Phase 75 Plan 02: Sub-Component Edit Gating Summary

**AND-gated all inline CRUD affordances on ClienteContactosCard, ClienteNotasCard, ResponsaveisCard (x2), and ProcuracaoCard behind a new `editable` prop wired to the parent's `isEditing` state, completing CLI-13's sub-component coverage left deliberately untouched by plan 75-01.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- `ClienteContactosCard` and `ClienteNotasCard` now hide their "Adicionar"/"Adicionar nota" input rows and per-item `Editar`/`Remover`/`Guardar`/`Cancelar` buttons unless `canEditClientes && editable`.
- `ResponsaveisCard` (used for both Advogados and Administrativos) hides its "Adicionar" button (modal trigger) and per-item `Remover` button under the same AND-gate.
- `ProcuracaoCard` hides the `Remover` button, the "Substituir ficheiro" `FileDropZone`, and the initial-upload `FileDropZone` branch under the AND-gate, while the `Ver / Download` button remains visible in both modes (read action, per UI-SPEC.md).
- All 5 call sites in `ClienteDetailContent` (Contactos, Notas, Advogados, Administrativos, Procuração) now pass `editable={isEditing}` alongside the existing `canEditClientes={canEditClientes}`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add editable prop and AND-gate Contactos + Notas cards** - `0580c41` (feat)
2. **Task 2: Add editable prop and AND-gate Responsaveis + Procuracao cards** - `2284b1f` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Added `editable: boolean` to the prop types of `ClienteContactosCard`, `ClienteNotasCard`, `ResponsaveisCard`, and `ProcuracaoCard`; changed every `canEditClientes ? (...) : null` guard around an Adicionar/Editar/Remover/upload affordance to `canEditClientes && editable ? (...) : null` (and the equivalent early-return guards inside `onCreate`/`onSaveEdit`/`onDelete`/`onAdd`/`onRemove`/`onFileChange`); left the `Ver / Download` button and the Dialog's own internal Cancelar/Adicionar buttons ungated; updated the 5 sub-component call sites in `ClienteDetailContent` to pass `editable={isEditing}`.

## Decisions Made
- Mirrored the existing dual-guard pattern (render-time JSX guard + handler-body early return) that `canEditClientes` already used throughout the file, applying `editable` the same way at every site rather than only gating the JSX. This keeps the two flags structurally symmetric and avoids a scenario where a handler could still fire if a guard were bypassed via a stale closure or ref.
- Kept `editable` naming (not `readOnly`) per the plan's recommendation, matching the codebase's existing positive-framing convention.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- The worktree had no `node_modules` (fresh worktree, consistent with 75-01-SUMMARY's note). Ran `pnpm install --frozen-lockfile` in `web/` before verification — a normal dependency-install step matching the already-committed lockfile, not a scope-affecting action.
- `pnpm tsc --noEmit` (as literally specified in the plan's `<verify>` blocks) has no `tsc` script in `package.json`; used `pnpm exec tsc --noEmit` instead, which resolves to the same TypeScript compiler.
- `pnpm exec tsc --noEmit` reports 2 pre-existing errors (`Cannot find module 'vitest'` in `src/lib/cliente-documento-tipo.test.ts` and `src/schemas/clientes.legacy-documento-tipo.test.ts`) unrelated to this plan's file — confirmed via grep that zero errors reference `clientes/[id]/page.tsx`. `pnpm lint` similarly reports 2 pre-existing errors in unrelated files (`user-profile-form.tsx`, `dashboard-shell.tsx`); confirmed zero lint findings reference the target file.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 75-03 (list-page link updates) is unaffected by this plan's changes and can proceed independently — it was already executed per the presence of `75-03-SUMMARY.md` in this phase directory.
- CLI-13 is now fully covered: main form fields (75-01) and all four sub-component CRUD affordances (this plan) are hidden in view mode and shown in edit mode, ANDed with existing RBAC.
- Manual/visual verification (toggling to edit mode with a `clientes:edit` permission user, confirming buttons appear/disappear, confirming Ver/Download stays visible in both modes) was not performed live in this environment — no `backend/.env` or `web/.env.local` configured in this worktree — and remains deferred to `human_verify` per the plan's own verification section.

---
*Phase: 75-componente-nico-view-edit*
*Completed: 2026-07-04*
