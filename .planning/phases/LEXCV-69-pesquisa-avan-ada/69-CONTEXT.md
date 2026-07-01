# Phase 69: Pesquisa Avançada - Context

**Gathered:** 2026-07-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Final phase of the milestone: expose the backend's already-built `pesquisar()` endpoint (free-text + combined filters) as a dedicated UI, purely additive over the existing `/pareceres` list (Phase 65). Depends on Phases 65-68 (all prior UI + entrega/RBAC must exist first, per roadmap's dependency-driven "safest last" ordering). No new backend work.

</domain>

<decisions>
### Pesquisa UI Location & Layout
- Not a new full route — a dedicated search/filter section on the existing `/pareceres` list page (Phase 65's `pareceres/page.tsx`), or a distinct results view triggered by entering search criteria (either "toggle to advanced mode on the same page" or "separate results panel below/replacing the simple list when search is active" — resolve exact interaction during planning, but do NOT create a new top-level route; this is additive to the existing list, not a parallel page).
- Fields mirror the backend `pesquisar()` endpoint exactly: `texto` (free-text, searches conteúdo), `clienteId`, `advogadoId`, `status`, `dataInicio`, `dataFim` (date range, native `<input type="date">` per the established codebase convention — no date-picker library exists or should be introduced).
- Reuses the same cliente/advogado/status select population already built in Phase 65's list filters (`useClientes`, `useAdminUsers` filtered to ADVOGADO) — no new data source.

### Data Layer
- New `usePesquisarPareceres(filters)` hook calling `GET /api/v1/pareceres/pesquisa` (confirmed distinct top-level path from the `/pareceres/solicitacoes` base used everywhere else — this is a sibling route, not nested under `solicitacoes`, per `ParecerController.pesquisarSolicitacoes`).
- Separate TanStack Query key namespace `["pareceres", "pesquisa", ...filters]`, distinct from `["pareceres", "list", ...]` (Phase 65's simple list) — confirmed as a CONTEXT-level decision from the milestone's research phase, avoids cache collision between the two different backend endpoints.
- Results rendering reuses the exact same row/card rendering (status badges, dual-view, cliente name resolution) already built in Phase 65's list page — do not duplicate that rendering logic; extract/share it if the plan finds that cleaner, or simply reuse the same JSX structure.

### Claude's Discretion
- Exact toggle/tab mechanism between "lista simples" and "pesquisa avançada" (e.g. a button that reveals extra filter fields inline vs. a separate collapsible panel) — implementation detail.
- Whether `texto` search triggers on every keystroke (debounced) or only on explicit submit — the backend has no pagination, so debounced live-search is technically fine, but an explicit "Pesquisar" submit button matching the codebase's existing draft/committed filter-bar convention (used in Phase 65/66) is more consistent — lean toward submit-based unless the plan finds strong reason otherwise.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 65's `/pareceres` list page — dual-view table/cards, status badge variant map, cliente/advogado select population, empty/loading/error states — all directly reusable for search results rendering.
- `web/src/app/(dashboard)/agenda/page.tsx` (lines ~240, 250) — native `<input type="date">` pattern for date-range filters, the only date-input convention in this codebase (no date-picker library).
- Phase 65's `use-pareceres.ts` — existing query-key conventions (`["pareceres","list",...]`, `["pareceres","detail",id]`, `["pareceres","versoes",id]`) to extend with a new `["pareceres","pesquisa",...]` namespace.

### Established Patterns
- Filter bars use a draft-state + committed-filters split with an explicit submit action (Phase 65/66 precedent), not live-as-you-type filtering.

### Integration Points
- Backend: `GET /api/v1/pareceres/pesquisa` (top-level path, sibling to `/pareceres/solicitacoes`, NOT nested under it) — `@PreAuthorize("hasAuthority('pareceres:view')")`, query params `texto`, `clienteId`, `advogadoId`, `status`, `dataInicio`, `dataFim` (all optional, `LocalDateTime` for date params per `ParecerController.pesquisarSolicitacoes`), confirmed directly from source during Phase 65 research. Backend has no pagination on this endpoint (flagged in milestone research as a performance trap for later scale, not a blocker now).
- Response: array of `ParecerSolicitacao`, same shape as the plain list endpoint.

</code_context>

<specifics>
## Specific Ideas

No additional specifics — this phase is explicitly "purely additive, safest last" per the roadmap's own design rationale.

</specifics>

<deferred>
## Deferred Ideas

None beyond what's already deferred at the milestone level (aprovação, diff, rich text, notificações — see REQUIREMENTS.md v2 section).

</deferred>
