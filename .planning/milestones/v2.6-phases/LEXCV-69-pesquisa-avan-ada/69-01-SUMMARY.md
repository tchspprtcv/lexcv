---
phase: 69-pesquisa-avan-ada
plan: 01
subsystem: frontend (pareceres search UI)
tags: [pareceres, search, tanstack-query, dual-view]
requires:
  - "web/src/hooks/use-pareceres.ts (Phase 65 Plan 01 — usePareceres/ParecerSolicitacoesListFilters)"
  - "web/src/app/(dashboard)/pareceres/page.tsx (Phase 65 Plan 02 — list page, dual-view, filters)"
provides:
  - "usePesquisarPareceres hook + ParecerPesquisaFilters type + buildParecerPesquisaSearch builder"
  - "Pesquisa Avançada toggle panel on /pareceres with submit-driven search results"
affects: []
tech-stack:
  added: []
  patterns:
    - "Separate TanStack Query cache namespace (['pareceres','pesquisa',...]) for a sibling search endpoint, avoiding collision with the list namespace"
    - "Draft-state + committed-filters submit convention reused for the search panel (Phase 65/66 precedent)"
    - "Single conditional swap of data source (rows/resultsLoading/resultsError) to reuse the exact same dual-view JSX for two different queries"
key-files:
  created: []
  modified:
    - web/src/hooks/use-pareceres.ts
    - "web/src/app/(dashboard)/pareceres/page.tsx"
decisions:
  - "Date params are always sent as full ISO date-times (T00:00:00/T23:59:59) since the backend binds dataInicio/dataFim as LocalDateTime — a bare YYYY-MM-DD would 400"
  - "usePesquisarPareceres is read-only (no useMutation/invalidateQueries), matching usePareceres's shape"
  - "The existing 'Filtros' toggle/state and usePareceres list remain completely untouched — Pesquisa Avançada is a second, independent toggle/state/query"
metrics:
  duration: "~20 minutes"
  completed: 2026-07-01
---

# Phase 69 Plan 01: Pesquisa Avançada Summary

Added a second, distinct "Pesquisa Avançada" panel to the existing `/pareceres` list page, wired to a new `usePesquisarPareceres` hook that calls the backend's `GET /pareceres/pesquisa` endpoint, reusing the exact same dual-view (table/cards) rendering as the Phase 65 simple list.

## What Was Built

### Task 1: `usePesquisarPareceres` hook
- `web/src/hooks/use-pareceres.ts`: added `ParecerPesquisaFilters` type (`texto`, `clienteId`, `advogadoId`, `status`, `dataInicio`, `dataFim`), `buildParecerPesquisaSearch` builder, and `usePesquisarPareceres` hook.
- Query key `["pareceres", "pesquisa", texto, clienteId, advogadoId, status, dataInicio, dataFim]` — distinct namespace from `["pareceres", "list", ...]`, no cache collision.
- Calls top-level `/pareceres/pesquisa` (sibling to `/pareceres/solicitacoes`, not nested).
- `dataInicio`/`dataFim` are appended with `T00:00:00`/`T23:59:59` respectively before being sent, satisfying the backend's `LocalDateTime` `@RequestParam` binding.
- `ParecerSolicitacoesListFilters`, `buildParecerSearch`, and `usePareceres` left untouched.

### Task 2: Pesquisa Avançada panel on `/pareceres`
- Second toggle button ("Pesquisa Avançada" / "Ocultar Filtros", `Search` icon, `variant="outline"`, never blue-600) added alongside the existing "Filtros" toggle — both toggles and their underlying state (`pesquisaOpen` vs `advancedOpen`) are fully independent.
- Panel (revealed by `pesquisaOpen`) contains draft fields: texto (`<input type="text">`), cliente/advogado/estado `<select>` (reusing the exact select markup/classes and option lists from the Phase 65 filter bar), and a "Período" field group with two native `<input type="date">` (Data Início / Data Fim) using this file's slate/`rounded-none`/`focus-visible:ring-blue-500` visual classes.
- "Pesquisar" submit button (the only blue-600 accent element in the panel) commits the six trimmed draft fields into `pesquisaFilters` and sets `pesquisaSubmitted = true`; shows "A pesquisar..." while `pesquisa.isFetching`. "Limpar Filtros" (ghost variant) resets all draft fields, clears `pesquisaFilters`, and sets `pesquisaSubmitted = false`, returning to the simple list.
- Results block: a single `searchActive = pesquisaSubmitted` conditional derives `rows`/`resultsLoading`/`resultsError` from either `pesquisa` or `pareceres` — the mobile-card and desktop-table JSX itself is unchanged/shared, just fed a different array.
- Distinct empty-state ("Nenhum resultado encontrado" / "Não foram encontrados pareceres para os critérios indicados. Tente ajustar o texto ou os filtros de pesquisa.") shown only when `searchActive` and zero rows; the Phase 65 list empty-state copy is preserved for the non-search case.
- Distinct error copy ("Não foi possível concluir a pesquisa. Verifique a ligação e tente novamente.") shown only when `searchActive` and `pesquisa.isError`; list error copy unchanged otherwise.

## Verification

- `pnpm exec tsc --noEmit` — zero errors (project-wide).
- `pnpm lint` — zero errors/warnings attributable to `use-pareceres.ts` or `pareceres/page.tsx` (5 pre-existing errors remain in unrelated files: `dashboard-shell.tsx`, `use-toast.ts` — out of scope, not touched by this plan).
- All plan acceptance-criteria greps pass: hook export, cache namespace, endpoint path, date-time suffixes, old type intact, toggle copy, both toggle states present, hook wired into the page, distinct empty-state copy, date inputs present, dual-view `clienteNomeById.get` reused twice (mobile + desktop).

## Deviations from Plan

None - plan executed exactly as written.

## Commits

- `6137a46` — feat(69-01): add usePesquisarPareceres hook with pesquisa cache namespace
- `16a6c50` — feat(69-01): add Pesquisa Avançada panel to /pareceres list page

## Known Stubs

None — the panel is fully wired to `usePesquisarPareceres`; no hardcoded/mock data.

## Threat Flags

None — no new network surface beyond what the plan's own threat model (T-69-01 through T-69-03, T-69-SC) already accounts for. The endpoint is already gated `pareceres:view`-only, tenant-scoped server-side, and the frontend introduces no new permission surface, tenant override, or injection vector (texto is sent via `URLSearchParams.set`, never concatenated into markup/SQL).

## Self-Check: PASSED

- FOUND: web/src/hooks/use-pareceres.ts (usePesquisarPareceres present)
- FOUND: web/src/app/(dashboard)/pareceres/page.tsx (Pesquisa Avançada panel present)
- FOUND: 6137a46 in git log
- FOUND: 16a6c50 in git log
