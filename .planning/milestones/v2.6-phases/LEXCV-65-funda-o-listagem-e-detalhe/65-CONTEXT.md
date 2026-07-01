# Phase 65: Fundação — Listagem e Detalhe - Context

**Gathered:** 2026-07-01
**Status:** Ready for planning

<domain>
## Phase Boundary

Read-only foundation for the Parecer Jurídico UI: `/pareceres` list (table/cards, status badges, filters) and `/pareceres/[id]` detail (metadata + immutable version timeline). No mutations (create/version/entrega) in this phase — those are Phases 66-68. Types/schemas/hooks built here are the foundation every later phase depends on.

</domain>

<decisions>
### Route & File Layout
- New route group `web/src/app/(dashboard)/pareceres/` with `page.tsx` (list) and `[id]/page.tsx` (detail), mirroring `processos/` exactly (same directory shape, same `layout.tsx` inheritance from `(dashboard)`).
- New `web/src/types/pareceres.ts` — types map 1:1 camelCase to `ParecerSolicitacao`/`ParecerVersao` Java entities (verified: no `@JsonProperty` overrides exist on either entity, so Jackson serializes exact camelCase field names — `clienteId`, `processoId`, `advogadoId`, `versaoFinalId`, `prazo`, `prioridade`, `status`, `createdAt` / `numeroVersao`, `conteudo`, `caminhoAnexo`, `criadoPorId`, `aprovado`, `aprovadoPorId`, `aprovadoEm`, `createdAt`). No normalization layer needed — direct pass-through.
- New `web/src/schemas/pareceres.ts` — Zod schemas for the two entities (read-only shape needed now; create/mutation schemas added in Phases 66-67).
- New `web/src/hooks/use-pareceres.ts` — TanStack Query hooks. This phase adds `usePareceres(filters)` (list, `GET /pareceres/solicitacoes`) and `useParecer(id)` (detail) and `useParecerVersoes(id)` (`GET /pareceres/solicitacoes/{id}/versoes`). Query key namespace: `["pareceres", "list", ...]` / `["pareceres", "detail", id]` / `["pareceres", "versoes", id]` — separate from the future `["pareceres", "pesquisa", ...]` namespace (Phase 69).

### List Page (`/pareceres`)
- Dual-view pattern identical to Clientes/Processos/Documentos: `hidden md:block` table + `md:hidden` mobile cards. No new component.
- Status badges reuse the existing badge component pattern from Processos (fase/status badges) — one badge per `status` value (`PENDENTE`, `EM_ELABORACAO`, `EM_REVISAO`, `CONCLUIDO`), each with a distinct color following existing status-badge color conventions in the codebase.
- Filters: status (select), advogado (user-picker/select scoped to ADVOGADO role), cliente/processo (text or select, matching existing filter-bar pattern in Processos/Documentos list pages). Filters map directly to the backend's existing query params (`clienteId`, `advogadoId`, `status`) on `GET /pareceres/solicitacoes`.
- Empty/loading/error states use the same skeleton + toast-on-error convention as every other list page — no new pattern.
- Nav: add "Pareceres" item to the sidebar (`dashboard-shell.tsx` or wherever nav items are defined), gated by `hasScopedPermission(perms, "pareceres", "view")`, following the exact pattern used for Financeiro's ADMIN/TECNICO-only visibility.

### Detail Page (`/pareceres/[id]`)
- Manual `useState<TabKey>` + toggle buttons — same as `processos/[id]/page.tsx` (`TabKey = "timeline" | "partes" | ...`). No Radix Tabs primitive exists in the codebase and this phase does not introduce one. Tab set for this phase: just a single "Versões" view (no tabs needed yet since Phase 65 has only one sub-view) — if the phase's own planning finds it cleaner to pre-declare tab scaffolding for Phase 68's future "Entregue" tab, that's an in-phase implementation call, not a decision to gate here.
- Version timeline renders each `ParecerVersao` as a chronological, actor-attributed entry (autor via `criadoPorId` resolved to a user display name, `createdAt`, `numeroVersao`, `conteudo`, and an anexo indicator/download link if `caminhoAnexo` is present) — directly porting the visual language of Processos' existing timeline/auditoria tab (v1.7). If no versions exist yet, show an empty state ("Nenhuma versão ainda — aguarda elaboração pelo advogado atribuído" or similar), since Phase 65 ships before Phase 67 (version creation) exists.
- Anexo download in this phase is read-only (uses the existing `GET /pareceres/solicitacoes/{id}/versoes/{versaoId}/anexo` presigned-URL endpoint, same shape as Documentos' `useDownloadDocumento`) — no upload UI yet (that's Phase 67).
- RBAC-gated action buttons (create version, entregar, etc.) are NOT part of this phase — Phase 65 is read-only. Any action affordances visible in the detail page should be limited to what's true today (view-only); do not stub buttons for future phases' actions.

### Claude's Discretion
- Exact badge color mapping per status value.
- Whether to pre-scaffold a tab shell for future phases or keep the detail page single-view for now — implementation detail, not a phase-boundary decision.
- Exact copy for empty/loading states (follow existing Portuguese tone used elsewhere in the app).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/(dashboard)/processos/[id]/page.tsx` — `TabKey` + `useState` tab pattern (lines ~93, ~142), timeline/auditoria tab rendering.
- `web/src/hooks/use-documentos.ts` — `useDownloadDocumento` pattern (presigned URL fetch) to reuse for anexo download.
- `web/src/lib/permissions.ts` — `pareceres` already present in `KNOWN_SCOPES` (line 12); use `hasScopedPermission(perms, "pareceres", "view")` directly, no new permission plumbing needed.
- Clientes/Processos/Documentos list pages — dual-view table/card pattern, filter-bar pattern, status badge pattern — all to be ported near-verbatim.

### Established Patterns
- TanStack Query hooks per module in `web/src/hooks/use-<module>.ts`, calling `apiFetch` from `web/src/lib/api.ts` against same-origin `/api/v1/*`.
- Route groups `web/src/app/(dashboard)/<module>/page.tsx` + `[id]/page.tsx`.
- Zod schemas in `web/src/schemas/<module>.ts`, types in `web/src/types/<module>.ts`.

### Integration Points
- Backend routes confirmed directly from `backend/src/main/java/com/lexcv/controllers/ParecerController.java`: base path `/api/v1/pareceres/solicitacoes` (note: list/detail/versoes are under `.../solicitacoes/...`, while `pesquisar` is a sibling top-level route `/api/v1/pareceres/pesquisa` — different base path, relevant for Phase 69, not this phase).
- Entity fields confirmed directly from `ParecerSolicitacao.java` / `ParecerVersao.java` — no Lombok/Jackson customization, pure camelCase.
- Sidebar nav registration point: wherever other module nav items are defined (likely inside `dashboard-shell.tsx` or a nav-config file) — locate during planning and add "Pareceres" following the existing item shape.

</code_context>

<specifics>
## Specific Ideas

No additional specifics beyond what's captured above — this phase closely mirrors existing modules by design (per PROJECT.md's "frontend burro" principle and the Stack/Architecture research findings that no new patterns are needed).

</specifics>

<deferred>
## Deferred Ideas

- Create solicitação / create versão / aprovar / entregar actions — Phases 66-68.
- Pesquisa avançada UI — Phase 69.
- Notificações in-app — Phases 66-68 (tied to the mutation that triggers each notification).
- "Parecer Entregue" dedicated view — Phase 68 (depends on entrega existing).

</deferred>
