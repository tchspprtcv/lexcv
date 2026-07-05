---
phase: 77-separadores-processos-e-pareceres
plan: 01
subsystem: ui
tags: [nextjs, react, tanstack-query, clientes, processos, pareceres]

# Dependency graph
requires:
  - phase: 76-separadores-dados-contactos-e-notas
    provides: 7-tab shell in clientes/[id]/page.tsx with PlaceholderEmBreve for unfinished tabs
provides:
  - ClienteProcessosTab component (Processos tab content, lazy-fetch via sub-component mount)
  - ClienteParecerTab component (Pareceres tab content, lazy-fetch via sub-component mount)
affects: [78-separadores-documentos-a-tratar-e-deslocacoes, 79-documentos-entregues-upload]

# Tech tracking
tech-stack:
  added: []
  patterns: ["sub-component mount for lazy TanStack Query fetch (no hook changes)"]

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/clientes/[id]/page.tsx

key-decisions:
  - "Lazy fetch achieved via sub-component mount (ClienteProcessosTab/ClienteParecerTab only rendered inside their tab branch) instead of adding an external `enabled` override to useProcessos/usePareceres — zero hook changes, per 77-PATTERNS.md locked design decision."
  - "Área Jurídica rendered as plain text (not Badge) in the compact Processos table to keep exactly one accent-colored element (Estado badge) per row, per UI-SPEC color contract."
  - "ParecerSolicitacao has no numero/titulo field — descricao used as the display value for the Número/Título column (display-mapping only, no data-model change)."

patterns-established:
  - "Compact per-entity tab tables inside a cliente ficha: reuse exact header styling, badge-variant mapping, and loading/error/empty copy from the entity's own list page, dropping only the surrounding chrome (search bar, KPI cards, pagination, Ações column)."

requirements-completed: [CLI-16, CLI-17]

# Metrics
duration: ~25min
completed: 2026-07-05
---

# Phase 77 Plan 01: Separadores Processos e Pareceres Summary

**Ficha de cliente ganha listagens reais nos separadores Processos e Pareceres, com fetch lazy via sub-componentes montados condicionalmente e navegação direta para /processos/[id] e /pareceres/[id].**

## Performance

- **Duration:** ~25 min
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- `ClienteProcessosTab` lists the current client's processos (Número, Estado badge, Área Jurídica, Data de Início) via `useProcessos({ cliente_id: id })`, replacing the "Em breve" placeholder on the Processos tab.
- `ClienteParecerTab` lists the current client's pareceres (Número/Título via `descricao`, Estado badge, Advogado Responsável resolved via `useAdminUsers`, Data de Criação) via `usePareceres({ clienteId: id })`, replacing the placeholder on the Pareceres tab.
- Both tabs are true lazy-fetch: the query only fires once the tab is mounted (sub-component mount pattern), with zero changes to `use-processos.ts` / `use-pareceres.ts`.
- Loading/error/empty states match the copy and styling mandated by 77-UI-SPEC.md exactly.

## Task Commits

Each task was committed atomically:

1. **Task 1: Separador Processos — componente ClienteProcessosTab (CLI-16)** - `bce42f2` (feat)
2. **Task 2: Separador Pareceres — componente ClienteParecerTab (CLI-17)** - `d78dc77` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Added `Table`/`useProcessos`/`usePareceres`/`ParecerStatus` imports, swapped the `tab === "processos"`/`tab === "pareceres"` branches from `PlaceholderEmBreve` to `ClienteProcessosTab`/`ClienteParecerTab`, and added both new components (plus local `parecerStatusVariant`/`formatParecerDate` helpers copied verbatim from `pareceres/page.tsx`).

## Decisions Made
- Followed the plan's locked design decision (sub-component mount) exactly — no changes to hook files, confirmed via `git diff --name-only` showing zero modifications to `use-processos.ts`/`use-pareceres.ts`.
- No new dependencies, no new UI primitives, no new color/spacing tokens — 100% reuse of existing `Table`/`Badge`/`Card` idiom from `/processos` and `/pareceres` list pages, per 77-UI-SPEC.md.

## Deviations from Plan

None - plan executed exactly as written. (Note: `.planning/phases/LEXCV-77-.../77-PATTERNS.md` referenced by the plan's `read_first` blocks did not exist as a file in this worktree; the equivalent guidance — exact source markup/line references — was already fully specified inline in `77-01-PLAN.md` and `77-UI-SPEC.md`, so execution proceeded directly from those two files without any gap.)

## Issues Encountered
- `web/node_modules` and `web/.env.local` were not present in the worktree (fresh checkout). Ran `pnpm install` and copied `web/.env.example` to `web/.env.local` (gitignored, not committed) to unblock `pnpm build`/`pnpm lint` — standard local dev setup, no code impact.
- Both tasks' edits landed in the same file with interleaved changes (shared import block, shared `tab === ...` ternary chain). To keep task commits atomic and independently buildable, Task 2's additions were applied, then temporarily reverted, Task 1 committed alone (verified `pnpm build` green), then Task 2's additions reapplied and committed separately (verified `pnpm build` green again).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Processos and Pareceres tabs fully functional and ready for Phase 78 (Documentos a Tratar + Deslocações tabs), which follows the same tab-shell pattern established in Phase 76 and reused here.
- No blockers or concerns.

---
*Phase: 77-separadores-processos-e-pareceres*
*Completed: 2026-07-05*
