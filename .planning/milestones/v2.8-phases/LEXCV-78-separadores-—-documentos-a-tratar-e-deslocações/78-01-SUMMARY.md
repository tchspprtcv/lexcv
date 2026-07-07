---
phase: 78-separadores-documentos-a-tratar-e-deslocacoes
plan: 01
subsystem: ui
tags: [nextjs, react, tabs, jsx-relocation, cliente-ficha]

# Dependency graph
requires:
  - phase: 76-separadores-shell-dados-contactos-notas
    provides: TabKey union with "documentosATratar"/"deslocacoes" keys, PlaceholderEmBreve component, dialog-reset useEffect (CR-01)
  - phase: 77-separadores-processos-pareceres
    provides: sibling tab-branch pattern (ClienteProcessosTab/ClienteParecerTab) used as wrapper analog
provides:
  - "Documentos a Tratar" tab renders the real list (isEditing-gated), replacing PlaceholderEmBreve
  - "Deslocações" tab renders the real list (isEditing-gated), replacing PlaceholderEmBreve
  - Dialog-reset useEffect widened to close/clear each intake dialog against its own tab key
affects: [79-separadores-documentos-entregues]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Tab-branch JSX relocation within a single component file (cut existing block, paste into new ternary branch, no new component extraction)"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"

key-decisions:
  - "Preserved the existing isEditing ? (...) : null gate exactly on both relocated blocks (per 78-CONTEXT.md's corrected discrepancy resolution) — read mode still renders nothing for these two tabs, no new always-visible read-only view was introduced"
  - "Wrapped each relocated block in Card/CardContent with className=\"space-y-2 pt-6\" to match sibling tab-branch wrapper shape (ClienteProcessosTab/ClienteParecerTab) while preserving the original div's internal space-y-2 spacing"
  - "Split the single dialog-reset useEffect guard (tab !== \"dados\") into three independent if-blocks on the same effect/dependency array — addDocEntreModal keeps tab !== \"dados\", addDocATratarModal now uses tab !== \"documentosATratar\", addDeslocacaoModal now uses tab !== \"deslocacoes\""

patterns-established: []

requirements-completed: [CLI-30, CLI-31]

# Metrics
duration: 25min
completed: 2026-07-06
---

# Phase 78 Plan 01: Relocate Documentos a Tratar / Deslocações into own tabs Summary

**Moved the existing "Documentos a Tratar" and "Deslocações" list sections out of the "Dados" tab's Intake do Caso card into their own dedicated tab branches, replacing the Phase 76 PlaceholderEmBreve placeholders — byte-for-byte same JSX/state/handlers, only the parent conditional branch changed.**

## Performance

- **Duration:** 25 min
- **Started:** 2026-07-06T02:05:00Z
- **Completed:** 2026-07-06T02:30:00Z
- **Tasks:** 1
- **Files modified:** 1

## Accomplishments
- "Documentos a Tratar" tab now renders the real list (heading, Adicionar dialog, ul/empty-state) instead of `<PlaceholderEmBreve />`
- "Deslocações" tab now renders the real list (descrição/local/data) instead of `<PlaceholderEmBreve />`
- Dialog-reset `useEffect` extended with per-dialog tab conditions so each "Adicionar" dialog closes/clears when its own tab is no longer active, not just when leaving "Dados"
- `documentosEntregues` tab, its state, handlers, and the "Guardar"/"Cancelar"/load-effect persistence logic were left completely untouched

## Task Commits

1. **Task 1: Relocate "Documentos a Tratar" and "Deslocações" blocks into their own tab branches + extend dialog-reset useEffect** - `7a40ff3` (refactor)

**Plan metadata:** (this commit, docs)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Removed the two JSX blocks from the "Intake do Caso" card; added them (wrapped in `isEditing ? (<Card><CardContent>...) : null`) as the `tab === "documentosATratar"` and `tab === "deslocacoes"` branches; widened the dialog-reset `useEffect` to three per-dialog `if` blocks keyed to each dialog's own tab

## Decisions Made
- Preserved the exact current `isEditing`-gated visibility (read mode: nothing rendered for these two tabs) per 78-CONTEXT.md's corrected reading of 78-PATTERNS.md Section F — this is not a regression, it matches today's behavior exactly.
- Used `<Card><CardContent className="space-y-2 pt-6">` as the wrapper for both relocated blocks, matching the sibling `ClienteProcessosTab`/`ClienteParecerTab` wrapper shape while keeping the original block's own `space-y-2` internal spacing intact.
- Kept state, handlers (`confirmAddDocATratar`, `confirmAddDeslocacao`), `onSubmit`/`onCancel`/load-effect references untouched at component scope — only the JSX consumption site moved.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
- `web/node_modules` was not installed in this worktree; ran `pnpm install` before `tsc`/`lint`/`build` could execute. Not a code issue, environment-only.
- `pnpm build` initially failed with `BACKEND_API_ORIGIN is required` because `web/.env.local` didn't exist in this worktree; created a local-only `.env.local` (gitignored, not committed) with the values from `.env.example` to run the build verification. No project files were changed for this.
- Pre-existing TypeScript errors in `src/lib/cliente-documento-tipo.test.ts` and `src/schemas/clientes.legacy-documento-tipo.test.ts` (missing `vitest` module — not a project dependency) are unrelated to this change; confirmed zero errors in the target file.
- Pre-existing lint errors/warnings in unrelated files (`documentos/novo/page.tsx`, `pareceres/nova/page.tsx`, `processos/[id]/page.tsx`, `processos/novo/page.tsx`, `settings/page.tsx`, `components/shared/dashboard-shell.tsx`) are pre-existing and untouched by this change; confirmed zero lint issues in the target file.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- CLI-30 and CLI-31 satisfied. Phase 78 complete (single-plan phase).
- Ready for Phase 79 (Documentos Entregues real upload) — the `documentosEntregues` tab/state/dialog remain in the "Dados" tab exactly as before, untouched by this phase, as expected for Phase 79 to pick up.

## Verification

- `pnpm exec tsc --noEmit` — no errors in target file (2 pre-existing, unrelated `vitest` module-resolution errors in test files, not caused by this change)
- `pnpm lint` — no errors/warnings in target file (pre-existing issues in unrelated files only)
- `pnpm build` — succeeded, all 23 routes compiled and prerendered
- `grep -n 'tab === "documentosATratar"'` — shows the branch now renders the relocated block, not `<PlaceholderEmBreve />`
- `grep -c 'PlaceholderEmBreve'` — dropped from 4 to 2 (function def + `documentosEntregues` call-site only)
- `grep -n 'tab !== "documentosATratar"'` / `'tab !== "deslocacoes"'` — both present inside the dialog-reset `useEffect`
- `grep -n 'newDocATratar: {'` — no `data` field added, remains `{ descricao: string }`

## Self-Check: PASSED

---
*Phase: 78-separadores-documentos-a-tratar-e-deslocacoes*
*Completed: 2026-07-06*
