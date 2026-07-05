---
phase: 76-separadores-dados-contactos-e-notas
plan: 01
subsystem: ui
tags: [nextjs, react, react-hook-form, tailwind, tabs]

# Dependency graph
requires:
  - phase: 75-componente-unico-view-edit
    provides: unified ClienteDetailContent component (single view/edit toggle, isEditing state, form + sub-component gating)
provides:
  - 7-tab shell (button-toggle row) on the ficha de cliente page, replicating the processos/[id] tab mechanism
  - Real "Dados" tab content (existing fields + new "Identificação" sub-section: NIF, Tipo de Documento, Número do Documento)
  - Real "Contactos e Notas" tab content (ClienteContactosCard + ClienteNotasCard, isolated from other tabs)
  - "Em breve" placeholder pattern (PlaceholderEmBreve component) reused by the 5 not-yet-built tabs
affects: [77-processos-pareceres-tabs, 78-documentos-a-tratar-deslocacoes-tabs, 79-documentos-entregues-upload]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Button-toggle tab row (TabKey union + useState + conditional-chain rendering) — reused verbatim from processos/[id]/page.tsx, with an overflow-x-auto + w-max wrapper divergence for 7 buttons on mobile"
    - "PlaceholderEmBreve shared component for not-yet-implemented tabs (no spinner, no API call)"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"

key-decisions:
  - "Used a JSX Fragment (<>...</>) to wrap the multi-element 'dados' tab arm (grid + conditional Intake card + Procuração/Responsáveis grid), since the ternary conditional-chain requires a single child per arm — matches the Phase 54-discovered fragment-required-for-siblings pattern already documented in PROJECT.md"
  - "View-mode Identificação sub-section implemented as a second dl block (bordered, pt-4 border-t) appended after the main Dados dl, rather than interleaving dt/dd pairs mid-grid — keeps the 3-column dl grid math simple and matches the edit-mode sub-section's visual separation"

patterns-established:
  - "PlaceholderEmBreve: <Card><CardContent className='py-12 text-center'>...</CardContent></Card>, no CardHeader/CardTitle, no loading/error state, reusable verbatim by Phases 77-79 until each tab gets real content"

requirements-completed: [CLI-15, CLI-18, CLI-19]

# Metrics
duration: ~35min
completed: 2026-07-05
---

# Phase 76 Plan 01: Separadores — Dados, Contactos e Notas Summary

**7-tab button-toggle shell added to the ficha de cliente page (replica of the processos/[id] tab mechanism), with real content for "Dados" (including a new "Identificação" sub-section) and "Contactos e Notas", and a shared "Em breve" placeholder for the other 5 tabs.**

## Performance

- **Duration:** ~35 min
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- 7-button tab row (`Dados`, `Contactos e Notas`, `Processos`, `Pareceres`, `Documentos Entregues`, `Documentos a Tratar`, `Deslocações`) added to `web/src/app/(dashboard)/clientes/[id]/page.tsx`, defaulting to `"dados"`, none disabled, `overflow-x-auto` + `w-max` wrapper for mobile scroll.
- "Dados" tab now contains a new "Identificação" sub-section (NIF, Tipo de Documento, Número do Documento) inside the Dados card, in both view and edit mode, without touching the Phase 74 legacy-`documento_tipo` validation logic.
- "Informações Adicionais" card reduced to Ramo de Atividade + Detalhes Adicionais only (single column), since the identification fields moved out.
- "Contactos e Notas" tab now renders `ClienteContactosCard` + `ClienteNotasCard` exclusively — they no longer render on the "Dados" tab or anywhere else.
- Clicking "Editar" from any tab now also switches the active tab to "Dados" (`setIsEditing(true); setTab("dados")`), matching CONTEXT.md's mandated interaction; `Cancelar`/`Guardar` do not force a tab switch.
- 5 not-yet-built tabs (`Processos`, `Pareceres`, `Documentos Entregues`, `Documentos a Tratar`, `Deslocações`) each render a shared `PlaceholderEmBreve` component: "Em breve" / "Esta funcionalidade estará disponível brevemente.", no spinner, no API call.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add 7-tab state machine + shell (button row, conditional chain, placeholder)** - `ff70d85` (feat)
2. **Task 2: Split Contactos e Notas into own tab + fold Identificação into Dados card** - `b77aedb` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Added `TabKey` union type, `tab`/`setTab` state, 7-button tab row, conditional-chain rendering per tab, `PlaceholderEmBreve` component, relocated NIF/Tipo de Documento/Número do Documento into a new "Identificação" sub-section inside the Dados card, relocated Contactos/Notas cards into their own "Contactos e Notas" tab arm, reduced "Informações Adicionais" to Ramo de Atividade + Detalhes Adicionais, and wired `Editar` to force-switch to the "Dados" tab.

## Decisions Made
- Wrapped the "dados" tab arm's multiple top-level JSX children in a `<>...</>` Fragment (required by the ternary conditional-chain pattern — a plain `(` grouping only accepts one child); this is the same defect class flagged in PROJECT.md's Key Decisions from Phase 54 ("React fragments obrigatórios em siblings dentro de ternário JSX").
- Implemented the view-mode "Identificação" sub-section as a second bordered `dl` block appended after the main Dados `dl`, rather than interleaving fields into the same 3-column grid — keeps the grid's implicit row math simple and mirrors the edit-mode sub-section's `pt-4 border-t` visual separation exactly.

## Deviations from Plan

None — plan executed exactly as written. Both tasks' acceptance criteria (grep-verified field/relocation counts, tab arm presence, `pnpm build` success) pass as specified. `web/.env.local` was created from `.env.example` and `web/node_modules` installed via `pnpm install` since this worktree had neither — required to run `pnpm build`/`pnpm lint` at all, not a plan deviation.

## Issues Encountered
- Initial Task 1 edit produced a Turbopack parse error ("Expected '</', got '{'") because the "dados" tab arm had multiple sibling top-level elements without a wrapping Fragment — a known defect class in this codebase (see PROJECT.md Phase 54 decision). Fixed by wrapping the arm's content in `<>...</>`; `pnpm build` passed cleanly afterward.
- `pnpm lint` reports pre-existing errors (`react-hooks/set-state-in-effect`, `react-hooks/refs`) in `dashboard-shell.tsx`, `documentos/novo/page.tsx`, and in this file's untouched sub-components (`ClienteContactosCard`/`ClienteNotasCard`/`ResponsaveisCard`, carried over from Phase 75). Verified via `git stash`/lint-before comparison that these are pre-existing and unrelated to this plan's changes (same error count with and without this plan's diff) — not fixed here, out of this plan's scope. `pnpm build`, the plan's actual gate, passes cleanly.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Phase 77 (Processos + Pareceres tabs) and Phase 78 (Documentos a Tratar + Deslocações tabs) can now replace the corresponding `PlaceholderEmBreve` arms in the same conditional chain with real content, following the same `tab === "x" ? (...) : ...` pattern.
- Phase 79 (Documentos Entregues upload) replaces the `documentosEntregues` arm similarly.
- No blockers. Live browser/backend UAT for this tab restructuring has not been performed in this environment (static build/grep verification only) — consistent with this milestone's established pattern of deferring live UAT to a human verification pass (see STATE.md's recurring `human_verification pending` entries for Phases 65-69).

---
*Phase: 76-separadores-dados-contactos-e-notas*
*Completed: 2026-07-05*
