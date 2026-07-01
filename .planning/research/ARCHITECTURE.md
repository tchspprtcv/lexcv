# Architecture Patterns

**Domain:** Frontend feature integration — `/pareceres` UI for LexCV's existing Módulo de Parecer Jurídico backend (v2.5)
**Researched:** 2026-07-01

## Recommended Architecture

The `/pareceres` module is a straight application of LexCV's existing "route group + hooks + schemas + types" pattern (as used by `processos`, `clientes`, `documentos`). No new architectural primitives are needed — this is integration, not invention. The one real design decision is how to model the **solicitação → versão → aprovação → entrega** lifecycle as TanStack Query state, since it's more stateful than a flat CRUD resource (it's closer to `processos` + `fases`/`movimentacoes` than to `clientes`).

```
web/src/app/(dashboard)/pareceres/
  page.tsx                 — lista de solicitações (filtros: cliente, advogado, status)
  novo/page.tsx             — form de criação de solicitação
  [id]/page.tsx              — detalhe: dados da solicitação, timeline de versões, ações de estado
  [id]/editar/page.tsx        — editar descricao/prazo/prioridade/cliente/processo (PUT /solicitacoes/{id})
  pesquisa/page.tsx           — pesquisa avançada dedicada (texto livre + filtros combinados via /pareceres/pesquisa)

web/src/hooks/use-pareceres.ts       — solicitação CRUD, atribuir, aprovar, entregar, pesquisar
web/src/hooks/use-parecer-versoes.ts — versão create/list/get/anexo download

web/src/schemas/pareceres.ts         — Zod: solicitação create/update, versão create (conteudo/anexo), atribuir, entregar
web/src/types/pareceres.ts           — ParecerSolicitacao, ParecerVersao, status/prioridade unions

web/src/components/pareceres/         — module-local components (list table, status badge, version timeline, forms)
```

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|---------------|-------------------|
| `pareceres/page.tsx` | List + filter solicitações | `useParecerSolicitacoes`, `usePesquisarPareceres` (if search state active) |
| `pareceres/novo/page.tsx` | Create solicitação form | `useCreateParecerSolicitacao`, `useClientes` (cliente picker), `useProcessos` (processo picker, optional) |
| `pareceres/[id]/page.tsx` | Detail shell: solicitação summary, status badge, action buttons (atribuir/aprovar/entregar), version list | `useParecerSolicitacao(id)`, `useParecerVersoes(id)`, `useAtribuirAdvogado`, `useAprovarVersao`, `useEntregarSolicitacao` |
| `pareceres/[id]/editar/page.tsx` | Edit descricao/prazo/prioridade/cliente/processo | `useUpdateParecerSolicitacao` |
| `pareceres/pesquisa/page.tsx` | Advanced search UI mirroring backend `pesquisar()` | `usePesquisarPareceres` |
| `components/pareceres/versao-form.tsx` | Multipart form: conteudo (textarea) + anexo (file) | `useCreateParecerVersao` |
| `components/pareceres/versao-timeline.tsx` | Renders ordered versões with aprovado/aprovadoPorId/aprovadoEm badges, download link | `useParecerVersoes`, `useDownloadAnexo` |
| `components/pareceres/entrega-view.tsx` | Dedicated "parecer entregue" view — resolves the PARC-09 audit gap by surfacing `versaoFinalId` prominently, not just as raw JSON | Consumes `ParecerSolicitacao.versaoFinalId` + matching entry from `useParecerVersoes` |
| `components/shared/dashboard-shell.tsx` (modified) | Add nav item `{ href: "/pareceres", label: "Pareceres", icon: ScrollText, requiredPermission: "pareceres:view" }` | N/A |

### Data Flow

The lifecycle is a state machine on `ParecerSolicitacao.status` (`PENDENTE → EM_ELABORACAO → EM_REVISAO → CONCLUIDO`), with `ParecerVersao` as a growing, immutable child collection. Query key design should mirror the `processos` module's nested-resource pattern exactly:

```
["pareceres", "list", ...filters]                     — GET /pareceres/solicitacoes
["pareceres", "pesquisa", ...filters]                  — GET /pareceres/pesquisa (separate key: different endpoint, different filter shape incl. texto/dataInicio/dataFim)
["pareceres", "detail", id]                            — GET /pareceres/solicitacoes/{id}
["pareceres", "versoes", solicitacaoId]                — GET /pareceres/solicitacoes/{id}/versoes
["pareceres", "versao-detail", solicitacaoId, versaoId] — GET .../versoes/{versaoId} (rarely needed standalone; list usually suffices)
```

**Invalidation rules per mutation** (mirrors `useExecutarTransicao`/`useTogglePrazoConcluido` patterns in `use-processos.ts`):

| Mutation | Endpoint | Invalidate / Update |
|----------|----------|---------------------|
| `useCreateParecerSolicitacao` | `POST /solicitacoes` | invalidate `["pareceres","list"]` |
| `useUpdateParecerSolicitacao` | `PUT /solicitacoes/{id}` | invalidate `["pareceres","list"]`, `setQueryData(["pareceres","detail",id], updated)` |
| `useAtribuirAdvogado` | `PUT /solicitacoes/{id}/atribuir` | invalidate `["pareceres","list"]` (status badge changes in list), `setQueryData(["pareceres","detail",id], updated)` |
| `useCreateParecerVersao` | `POST /solicitacoes/{id}/versoes` (multipart) | invalidate `["pareceres","versoes",id]` — **and** `["pareceres","detail",id]` defensively, since `numeroVersao` count affects UI even though status doesn't change on version-create |
| `useAprovarVersao` | `PUT /solicitacoes/{id}/versoes/{versaoId}/aprovar` | invalidate `["pareceres","versoes",id]` (aprovado flag) **and** `["pareceres","detail",id]` (status may flip PENDENTE/EM_ELABORACAO → EM_REVISAO) |
| `useEntregarSolicitacao` | `PUT /solicitacoes/{id}/entregar?versaoFinalId=` | invalidate `["pareceres","list"]`, `setQueryData(["pareceres","detail",id], updated)` — this is the terminal transition; UI should render an "entrega view" once `status === "CONCLUIDO"` |
| `useDownloadAnexo` | `GET .../versoes/{versaoId}/anexo` | no cache mutation — treat as a one-shot query/mutation returning a presigned URL, open in new tab; do not persist the URL in the query cache beyond its 3600s TTL (matches `documentos` download pattern) |

**Why status invalidation matters across boundaries:** `aprovar` and `entregar` both mutate `ParecerSolicitacao.status`, which the **list** view's status filter and status badges depend on — every write mutation that can change `status` must invalidate `["pareceres","list"]`, not just the detail/versões keys. This is the one place a naive "invalidate only my own resource" approach would leave stale badges in the list.

**Multipart submission (`useCreateParecerVersao`):** Unlike every other mutation in this module (and most of the app, which use JSON `apiFetch`), version creation is `multipart/form-data` (conteudo as text field, `file` as optional attachment) — this mirrors the existing `documentos` upload hook, **not** `use-processos.ts`. Check `web/src/hooks/use-documentos.ts` (or equivalent) for the exact `FormData` + `apiFetch` invocation pattern (likely omits the `Content-Type: application/json` header override) before writing `use-parecer-versoes.ts`.

**Backend contract note (no mismatch to worry about):** Unlike other v2.4-era fields, `ParecerSolicitacao`/`ParecerVersao` entities have zero `@JsonProperty` annotations and no snake_case columns leaking through JSON serialization (verified directly in `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java` and `ParecerVersao.java`) — Jackson emits plain camelCase (`clienteId`, `advogadoId`, `versaoFinalId`, `numeroVersao`, `criadoPorId`, `aprovadoPorId`, `aprovadoEm`). **Do not build a `normalizeParecer()` snake_case/camelCase bridge like `use-processos.ts` does** — that complexity exists in `processos` only because of a historical mismatch (per `PROJECT.md`'s Key Decisions) and does not apply here. Types in `types/pareceres.ts` should be camelCase-only, 1:1 with the Java model, with no dual-field API-shape types.

## Patterns to Follow

### Pattern 1: RBAC gating mirrors the existing `pareceres` scope already wired into `permissions.ts`
**What:** `KNOWN_SCOPES` in `web/src/lib/permissions.ts` already includes `"pareceres"` (added ahead of this milestone). Use `hasScopedPermission(perms, "pareceres", action)` exactly as `clientes`/`processos` pages do — no new permission plumbing needed.
**When:** Every page/action: list+detail need `pareceres:view`; create solicitação/versão need `pareceres:create`/`pareceres:edit` respectively (per backend `@PreAuthorize`); aprovar needs `pareceres:manage`.
**Example:**
```typescript
const canApprove = hasScopedPermission(session.permissoes, "pareceres", "manage");
const canCreateVersao = hasScopedPermission(session.permissoes, "pareceres", "edit");
```
Note the asymmetry: backend gates `createVersao` and `entregarSolicitacao` at `pareceres:edit` (not `create`), and `aprovarVersao` at `pareceres:manage` (the strictest tier) — the frontend's action-button visibility must match these exact scope:action pairs per endpoint, not a blanket "edit implies everything" assumption, or buttons will render for users who get a 403 on click. Additionally, `createVersao` and `entregarSolicitacao` also enforce a **role/ownership check server-side** (ADMIN or the assigned `advogadoId`) beyond the scope check — the frontend should hide those actions for non-owning ADVOGADO users even if they hold `pareceres:edit`, to avoid dead buttons.

### Pattern 2: Nested-resource hooks scoped by parent id, following `use-processos.ts`'s `usePrazos(processoId)` / `useProcessoFases(id)` style
**What:** `useParecerVersoes(solicitacaoId)` takes the parent id as an argument and produces a query key array `["pareceres","versoes",solicitacaoId]`, exactly like `useProcessoFases`/`useProcessoMovimentacoes`.
**When:** Any 1-to-many child resource under a solicitação.

### Pattern 3: Terminal/status-machine actions as dedicated mutation hooks (not generic PUT wrappers)
**What:** `useAtribuirAdvogado`, `useAprovarVersao`, `useEntregarSolicitacao` should be named, single-purpose hooks (like `useFormalizarProcesso`/`useExecutarTransicao` in `use-processos.ts`), each hard-coding its endpoint and invalidation set — not a single generic `usePatchParecer()`.
**Why:** Each transition has different RBAC (`edit` vs `manage`), different payload shape, and different invalidation footprint (see table above); collapsing them loses that specificity and risks under-invalidating.

## Anti-Patterns to Avoid

### Anti-Pattern 1: Building a snake_case/camelCase normalization layer for pareceres
**What:** Copying the `normalizeProcesso()`/`ProcessoApi` dual-field pattern from `use-processos.ts` into `use-pareceres.ts`.
**Why bad:** That pattern exists in `processos` only because of a historical mismatch (documented as tech debt in `PROJECT.md`'s Key Decisions). The `Parecer*` entities were built clean in v2.5 with pure camelCase and no `@JsonProperty` — adding a normalization shim here is unnecessary complexity that also risks masking real bugs if a future backend change actually does introduce a mismatch.
**Instead:** Type `ParecerSolicitacao`/`ParecerVersao` in `types/pareceres.ts` directly against the Java model fields, no dual-key API-shape types.

### Anti-Pattern 2: Treating `pesquisar()` as just "list with more query params" sharing the list's query key
**What:** Reusing `["pareceres","list", ...filters]` for both `GET /solicitacoes` (simple filter: clienteId/advogadoId/status) and `GET /pareceres/pesquisa` (texto livre + dataInicio/dataFim + same filters).
**Why bad:** They're different endpoints with different filter shapes and different backend query logic (`pesquisar()` does a broader query across `t_parecer_versao`, per the v2.5 audit's confirmation that `pesquisar()` correctly joins version data). Conflating them in one query key causes cache collisions when a user toggles between "list" and "pesquisa avançada" views with overlapping filter values.
**Instead:** Separate query key namespace `["pareceres","pesquisa", ...]`, separate hook `usePesquisarPareceres`.

### Anti-Pattern 3: Building `/pareceres/[id]/entregue` as a distinct route
**What:** A separate top-level route for "delivered" pareceres.
**Why bad:** `versaoFinalId` and `status === "CONCLUIDO"` are just a state of the same solicitação — a separate route fragments the detail experience and complicates linking/breadcrumbs, and the audit gap (PARC-09) is about the *field not being surfaced*, not about routing.
**Instead:** Conditionally render an "entrega view" section/component inside the existing `[id]/page.tsx` detail page when `status === "CONCLUIDO"`, resolving the version by `versaoFinalId` from the already-fetched `useParecerVersoes` list.

## Scalability Considerations

| Concern | Current scale (single-tenant office use) | Notes |
|---------|-------------------------------|-------|
| List pagination | Backend `listSolicitacoes`/`pesquisar` return unbounded lists (no pagination params observed in `ParecerController`) | Frontend should not assume pagination exists; if solicitação volume grows this becomes a backend concern out of scope for this milestone — flag as a phase-specific risk only if volume is expected to be high |
| Version count per solicitação | `numeroVersao` is a simple incrementing counter per solicitação (synchronized block, race-condition-fixed per v2.5 audit) | UI version timeline is a flat list; no special handling needed — pareceres typically accumulate only a handful of versions |
| File downloads | Presigned URL pattern (matches `documentos` MinIO integration from v2.2) | Reuse existing download UX conventions, do not build new |

## Suggested Build Order (phase/plan breakdown)

Ordering follows dependency: you cannot version/approve/deliver a solicitação that doesn't exist yet in the UI, and you cannot usefully test mutations without a working list+detail to observe results in.

1. **Foundation: types, schemas, read-only list + detail**
   - `types/pareceres.ts`, `schemas/pareceres.ts` (Zod for create/update, reused across later phases)
   - `use-pareceres.ts`: `useParecerSolicitacoes(filters)`, `useParecerSolicitacao(id)`
   - `use-parecer-versoes.ts`: `useParecerVersoes(solicitacaoId)`
   - `pareceres/page.tsx` (list, basic filters: cliente/advogado/status dropdowns — no free-text search yet)
   - `pareceres/[id]/page.tsx` (detail: solicitação fields + version timeline, read-only, status badge)
   - Nav item in `dashboard-shell.tsx` gated by `pareceres:view`
   - Rationale: nothing else can be verified/tested without a place to see solicitações and their state.

2. **Creation + editing mutations**
   - `pareceres/novo/page.tsx` + `useCreateParecerSolicitacao` (cliente/processo picker reusing existing `useClientes`/`useProcessos` hooks)
   - `pareceres/[id]/editar/page.tsx` + `useUpdateParecerSolicitacao`
   - `useAtribuirAdvogado` (advogado picker constrained to ADVOGADO-role users — check `use-clientes.ts` intake forms for an existing reusable advogado-picker hook before building a new one, since v2.4 intake already links advogados to Users)
   - Rationale: solicitação lifecycle starts here; depends on Phase 1's list/detail existing to show results.

3. **Versionamento (elaboração)**
   - `components/pareceres/versao-form.tsx` (multipart: conteudo textarea + anexo file input, following documentos upload UX)
   - `useCreateParecerVersao` (multipart mutation)
   - `useDownloadAnexo` for version attachments
   - Wire into `[id]/page.tsx` version timeline (from Phase 1) with an "add version" action, gated `pareceres:edit` + advogado-responsável-or-ADMIN check (mirrors backend authorization)
   - Rationale: requires a solicitação to exist (Phase 2) and a place to display versions (Phase 1).

4. **Aprovação e Entrega (terminal actions)**
   - `useAprovarVersao` (gated `pareceres:manage`) — action button per version row in timeline
   - `useEntregarSolicitacao` — action requiring `versaoFinalId` selection from existing versions
   - `components/pareceres/entrega-view.tsx` — dedicated "parecer entregue" section rendered when `status === "CONCLUIDO"`, resolving the PARC-09 audit gap by surfacing `versaoFinalId` prominently (version number, content/anexo, aprovado metadata) instead of leaving it as a raw JSON field
   - Rationale: depends on versions existing (Phase 3); this is the highest-risk phase for RBAC/state-machine bugs (CONCLUIDO is irreversible per backend), so should be built and tested last among the write flows, with the most scrutiny on button visibility matching backend `@PreAuthorize`/role checks exactly.

5. **Pesquisa avançada**
   - `pareceres/pesquisa/page.tsx` + `usePesquisarPareceres` (texto livre + clienteId/advogadoId/status/dataInicio/dataFim, separate query key namespace per Anti-Pattern 2 above)
   - Rationale: purely additive read capability; safest to build last since it has no dependents and doesn't block any other phase, and benefits from the status/lifecycle vocabulary already being stable from Phases 1-4.

**Do not interleave phases 3 and 4** — approval and delivery both operate on the version list built in phase 3, and both are high-stakes irreversible-or-near-irreversible state transitions; building them together in one phase risks conflating two distinct RBAC boundaries (`manage` vs `edit`+role-check) in testing.

## Sources

- `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (HIGH confidence — read directly, full 12-endpoint contract)
- `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java`, `ParecerVersao.java` (HIGH confidence — confirms no snake_case/camelCase mismatch)
- `web/src/hooks/use-processos.ts` (HIGH confidence — existing pattern reused for query key / invalidation design)
- `web/src/lib/permissions.ts` (HIGH confidence — confirms `pareceres` scope pre-registered)
- `web/src/components/shared/dashboard-shell.tsx` (HIGH confidence — nav item pattern)
- `.planning/v2.5-MILESTONE-AUDIT.md` (HIGH confidence — source of PARC-09 gap and backend-only scope decision)
- `.planning/PROJECT.md` (HIGH confidence — milestone goal, key decisions, out-of-scope boundaries)
