
# Feature Research

**Domain:** Cross-entity global search for an institutional legal practice management platform (LexCV v2.14 — "Pesquisa global funcional cross-entity")
**Researched:** 2026-07-18
**Confidence:** HIGH for codebase-grounded findings (verified by reading actual current source) and for general B2B SaaS/command-palette UX patterns (multiple independent sources agree) / MEDIUM for domain-specific reasoning about legal/institutional search behavior (inferred from the data model + general enterprise-search literature, not from verified named-competitor documentation — see Sources) / LOW confidence items flagged inline

> Scope note: this file covers **only** milestone target #1 ("Pesquisa global funcional cross-entity"). Targets #2–#5 (estado filter on Processos, icon-only buttons, `--radius` token, icon-only filter actions) are pure UI polish inside the already-migrated shadcn/ui design system and are deliberately **not** researched here per the orchestrator's scoping instructions.

## Critical Cross-Cutting Finding (read this before the per-area tables)

**The frontend primitive needed for the idiomatic version of this feature already exists and is unused for this purpose.** `web/src/components/ui/command.tsx` (added in v2.13 Phase 101 as the `shadcn` `Command` primitive, backed by `cmdk@1.1.1`) already exports `CommandDialog` — a fully-styled, accessible, keyboard-navigable modal search palette (built on the existing `Dialog` primitive), complete with `CommandInput`, `CommandList`, `CommandGroup` (auto-hides empty groups), `CommandItem`, and `CommandEmpty`. Today it is only consumed indirectly via `Combobox` (a single-select dropdown), never as a top-level ⌘K-style palette. This means the industry-standard command-palette pattern (see below) is close to a **wiring exercise on the frontend**, not a new-component build — the real net-new work is the backend endpoint and the data it returns. This substantially lowers the complexity ratings below versus what they'd be in a codebase without shadcn's Command already installed.

The existing search `<Input>` at `web/src/components/shared/dashboard-shell.tsx:121-127` is positioned exactly where a command-palette **trigger** conventionally lives in this pattern (a header search box with a keyboard-shortcut hint, e.g. GitHub/Linear/Vercel's `⌘K` badge inside the input). Recommendation: convert this element into the trigger for `CommandDialog` (click or focus opens the palette; add a `kbd` hint showing `Ctrl K`/`⌘K`) rather than building a second, separate search affordance.

**No debounce hook or global keyboard-shortcut listener exists anywhere in `web/src/`** (confirmed by search) — both are small, new, and have no existing pattern to reconcile with, unlike almost everything else in this milestone.

**Existing per-entity search is inconsistent and mostly unsuitable to reuse as-is for a fan-out query:**

| Entity | Existing search today | Mechanism | Searchable fields |
|--------|----------------------|-----------|--------------------|
| Cliente | `GET /clientes?q=` | In-memory Java stream filter over `findByTenantId()` (loads the whole tenant table) | `nome`, `nif`, `email`, `telefone` |
| Processo | `GET /processos?q=` | Same in-memory stream pattern | `numeroProcesso`, `tipoProcesso`, `descricao`, `tribunal`, `areaJuridica`, `estado` |
| Documento | `GET /documentos` | **No filter parameters at all today** — returns the entire tenant's documents, a pre-existing gap already flagged in `.planning/PROJECT.md`'s decision log (`GET /documentos` also ignores `cliente_id`/`processo_id`) | none |
| ParecerSolicitacao | `GET /pareceres/pesquisa?texto=` (separate `ParecerPesquisaController`) | **Native SQL query** (`@Query(nativeQuery = true)`) with `ILIKE` on the latest `ParecerVersao.conteudo`, plus exact filters for `clienteId`/`advogadoId`/`status`/date range | `conteudo` of most recent version only (via correlated subquery), not `descricao` |

Only the Pareceres implementation uses a real SQL-level query; Clientes/Processos load full tables into JVM memory. For a **fan-out search that hits 4 tables on every keystroke**, the in-memory approach doesn't scale as a pattern to copy — the new endpoint should use targeted, `LIMIT`-capped SQL queries per entity (closer to the Pareceres precedent than the Clientes/Processos one). This is an implementation detail more properly owned by architecture/backend research, but it directly affects the complexity rating of "cross-entity search" below, so it's flagged here.

**Structured identifiers exist for 2 of the 4 entities, not all 4** — this matters a great deal for the domain-specific part of the question:

| Entity | Structured identifier(s) | Format | Notes |
|--------|---------------------------|--------|-------|
| Cliente | `numero_cliente` | `CLI-0001` (sequential per tenant, DB-unique) | Also `nif` (9 digits, DB-unique per tenant) and `documento_numero` (BI/passaporte/registo comercial) |
| Processo | `numero_processo` | e.g. `PROC-2026-0001` (free-text `String` column, **not** DB-unique/required — no constraint on it, unlike `numero_cliente`) | |
| Documento | none | — | Only `nome` (filename-ish string) and `tipo` (free-text category) |
| ParecerSolicitacao | **none** | — | No reference-number field exists anywhere in the model; only findable via `cliente_id`, `descricao` text, `advogado_id`, `status` |

This confirms the question's premise directly: institutional users (advogados, técnicos, assistentes) genuinely can and will type `CLI-0001`, a NIF, or a `PROC-...` string as often as a name — but only for Clientes and Processos. Documentos and Pareceres have no equivalent, so their entries in the result set will always be reached via free-text/relational matching, never an exact-code lookup. The ranking strategy needs to account for this asymmetry (see Table Stakes below).

All 4 entities already have a canonical detail route to deep-link a search result to: `/clientes/[id]`, `/processos/[id]`, `/documentos/[id]`, `/pareceres/[id]` — navigation-on-select is trivial, no new routing needed.

**RBAC is per-entity-scope, not a single "can search" permission**, and this is non-negotiable given the codebase's own established doctrine (CLAUDE.md: "both layers must agree"; and the identical pattern already implemented in v2.8 Phase 77, where the Ficha de Cliente's Processos/Pareceres tabs are independently gated by `processos:view`/`pareceres:view`). The 4 seeded roles (ADMIN, ADVOGADO, TECNICO, ASSISTENTE) all currently hold all of `clientes:view`, `processos:view`, `documentos:view`, `pareceres:view` — but RBAC in this system is dynamically configurable (`AdminController`), so a custom role with only 2 of the 4 scopes is a real, testable case the search feature must handle correctly, not a hypothetical. The seed data will not surface this bug by accident.

---

## Feature Landscape

### Table Stakes (Users Expect These)

These are the non-negotiable pieces of "pesquisa global funcional cross-entity" as scoped by the milestone. Missing any of these makes the feature feel broken or, worse, insecure.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Search across all 4 named entities (Cliente, Processo, Documento, ParecerSolicitacao) from one input | This is the literal, explicit ask — replaces the currently-decorative `<Input>` that has no `onChange` at all | MEDIUM | Backend: **one new unified endpoint** (matches PROJECT.md's phrasing "endpoint novo", singular) that internally fans out to 4 tenant-scoped, `LIMIT`-capped queries and returns a single discriminated/grouped payload — not 4 separate frontend calls. Frontend: one new TanStack Query hook. |
| Search-as-you-type with debounce | Baseline expectation for any modern search box; without it, an endpoint that fans out to 4 tables gets hit on every keystroke, which is 4x the query cost of a normal single-list filter | LOW | ~300ms is the established sweet spot (250–500ms range) for desktop; no library needed, a small custom hook or `useDeferredValue` suffices. No debounce utility exists in the codebase today — new, small (~10–20 lines). |
| Minimum query length before firing (e.g. 2 characters) | Prevents a 1-character keystroke from firing 4 broad `ILIKE '%a%'` queries simultaneously; doubly important here because of the fan-out | LOW | Simple guard in the hook/controller. Combine with debounce, not instead of it. |
| Results grouped visually by entity type (Clientes / Processos / Documentos / Pareceres) | This is the standard, expected shape for any multi-source search (see Cross-Cutting Finding + Sources) | LOW | `CommandGroup` already auto-hides a group with zero matches — no extra empty-state logic needed per group. |
| Each result shows enough context to disambiguate, not just a bare name | Clientes/Processos lists elsewhere in the app already show secondary fields (número, NIF, estado); a search result with only a name would be a visible regression from the rest of the app's conventions | LOW | Suggested subtitle per type: Cliente → `CLI-0001 · NIF 123456789`; Processo → `PROC-2026-0001 · ATIVO`; Documento → `tipo · nome do cliente/processo associado`; Parecer → `cliente · status`. |
| Structured-identifier-aware matching: `numero_cliente`, `numero_processo`, `nif`, `documento_numero` must be matched, not just free-text name/description fields | This is the domain-specific crux of the question — institutional users type exact codes as often as names, and 2 of the 4 entities have no other reliable lookup path | LOW–MEDIUM | Straightforward to add columns to the existing `ILIKE`-style pattern (see Pareceres precedent). Becomes MEDIUM if paired with exact-match-first ranking (next row) rather than shipped as "just another ILIKE column." |
| Exact/prefix matches on structured identifiers rank at or above fuzzy substring matches on names | Standard enterprise-search practice (see Sources): a user who types `CLI-0001` or a full NIF wants that one record, not a Clientes group buried under 8 fuzzy name hits | LOW–MEDIUM | Doesn't require a scoring engine — a simple `ORDER BY` that puts exact/`starts-with` identifier matches first, then substring matches, is sufficient at this data scale (single-tenant, hundreds–low-thousands of rows per table, not millions). |
| Click a result → navigate to its existing detail route, palette closes | Zero new routing work — `/clientes/[id]`, `/processos/[id]`, `/documentos/[id]`, `/pareceres/[id]` already exist | LOW | |
| Visible trigger (button/input) **and** keyboard shortcut (`Ctrl+K` / `⌘K`) to open | PROJECT.md explicitly asks for both ("botão 'Pesquisar' + atalho de teclado"); this also matches the near-universal convention of pairing a labeled search affordance with a shortcut hint, since shortcut-only discovery is poor UX for less power-user staff (técnicos/assistentes) | LOW | Reuse/convert the existing header `<Input>` as the trigger; add a small new global `keydown` listener (no existing pattern to reuse, but trivial: one `useEffect` checking `(e.metaKey \|\| e.ctrlKey) && e.key === "k"`). |
| Per-entity-type RBAC gating inside the search itself, not a single blanket check | Directly follows CLAUDE.md's RBAC doctrine and the already-established Phase 77 precedent (Cliente ficha tabs gated per scope); a user without `pareceres:view` must never see Pareceres results or trigger a Pareceres query at all, independent of whatever other scopes they hold | MEDIUM | Mechanically simple per branch (`@PreAuthorize`-equivalent check or an early skip before that sub-query runs) but must be **explicitly tested with a partial-scope role**, since all 4 seeded roles currently have all 4 scopes and won't catch a regression here by accident. Also decide the "zero matching scopes" case (e.g. a hypothetical future role with none of the 4): the trigger/palette should degrade gracefully (empty, not a 403 or console error), not crash. |
| Tenant isolation on every sub-query | The project's stated primary data-isolation boundary (CLAUDE.md) — every existing query in this codebase filters by `tenant_id`; a global search endpoint touching 4 tables at once is exactly the kind of surface where a copy-paste omission on one branch would be easy to miss and severe if missed | LOW effort / HIGH consequence | Mirror the pattern already used everywhere else (`tenantId = getTenantId()` passed into every repository call). Call out explicitly in code review for this feature — cross-tenant leakage in a legal document/case-file product is a serious confidentiality failure, not a cosmetic bug. |
| Empty / loading / no-results states | Standard UX baseline | LOW | `CommandEmpty` already provides most of this "for free" per the installed primitive; just needs copy (e.g. "Comece a escrever para pesquisar" vs. "Nenhum resultado encontrado"). |

### Differentiators (Competitive Advantage)

Genuinely valuable, but none of these are implied by the milestone's literal wording ("endpoint novo + botão + atalho") — flagged so the roadmap can make a deliberate include/defer call rather than silently absorbing them into v1.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Recently-viewed records shown in the empty/pre-query state | Well-documented pattern in Notion/Linear-style palettes (recent pages/items surfaced before any typing) — genuinely useful for lawyers who reopen the same handful of active processos/clientes repeatedly through a workday | LOW–MEDIUM | Cheapest version: client-side only (e.g. `localStorage`, last N detail-page visits, session/browser-scoped). Explicitly **not** the same feature as persisted "recent searches" (see Anti-Features) — recording which *records* were opened is safer and more useful than recording what *query strings* were typed. |
| "Ver todos os resultados de [Tipo]" overflow link per group, deep-linking into that entity's own list page with the query pre-filled | Bridges the new quick-jump palette with the app's existing rich per-entity filter pages instead of the search feeling like a disconnected bolt-on; also gives users a path past whatever per-group result cap the palette enforces (e.g. top 5) | LOW for Clientes/Processos (their list pages already accept `?q=`), MEDIUM for Documentos/Pareceres (Documentos has no list-level `q` today; Pareceres' existing search lives at a different route/param shape — `/pareceres/pesquisa?texto=` — than its own list page uses) | Worth including specifically because it's nearly free for 2 of the 4 entities today; flags a small pre-existing inconsistency (Pareceres search endpoint's `texto` param vs. a hypothetical `/pareceres?q=`) worth resolving as part of this work rather than adding a third param convention. |
| Matched-substring highlighting in result labels | Classic search-UX polish, helps users trust *why* a result matched | LOW–MEDIUM | Pure frontend; no backend change needed beyond returning the raw match. |
| Smart "jump straight to record" on Enter when the query is an unambiguous exact identifier match (e.g. a full `CLI-0001` or 9-digit NIF with exactly one hit) | Power-user shortcut — skips the results list entirely for the highest-confidence case | MEDIUM | Requires detecting "exactly one exact match" server- or client-side and special-casing Enter; real but bounded complexity. Reasonable to defer to a later pass once the base palette ships and usage patterns are observed. |
| Cross-entity relevance ranking (e.g., weighting Cliente/Processo above Documento when match quality is similar) | Could reduce noise if result volume grows | MEDIUM–HIGH for uncertain payoff | Given this is a single-institution, per-tenant deployment (not a multi-million-row SaaS), the realistic result volumes are small; a real ranking model is likely solving a problem that doesn't exist yet at this scale. Reasonable to skip entirely rather than defer — revisit only if a specific tenant's data volume makes plain grouping+identifier-priority insufficient. |
| Searching Processo by assigned lawyer's name (`responsavelId` → `User.nome`) | Natural query institutional staff might try ("processos da Dra. Maria") | MEDIUM | Requires a join the existing `/processos?q=` doesn't do today; real value but not implied by the milestone's literal scope — good v1.x candidate, not v1. |

### Anti-Features (Commonly Requested, Often Problematic)

Explicitly called out per the milestone's own framing risk: this is a "pesquisa global funcional" UI-polish item, not a search-platform initiative. Every item below is a plausible-sounding scope-creep vector for exactly this kind of feature.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|------------------|-------------|
| Search analytics / dashboards (tracking query volume, click-through, "top searches") | Feels like an obvious companion to "add search" in a mature product | No stakeholder has asked for it; nothing in this milestone or product consumes it; pure speculative infrastructure for a first release of the feature itself | If ever needed later, ordinary DB/application logs are sufficient forensics — don't build a reporting surface pre-emptively |
| Saved searches / stored queries | Looks like a natural "power user" feature once any search exists | Duplicates what the entity-specific advanced filter pages already do (Pareceres already ships its own dedicated advanced search with filters — `clienteId`, `advogadoId`, `estado`, `tipo`, date range, full-text; Processos is gaining a state filter in this same milestone). A *global quick-jump* palette and a *saved, reusable structured query* are different feature classes; conflating them roughly doubles scope (new persistence entity, CRUD, UI) for a handful of internal institutional users who haven't asked for it | Rely on the existing entity-specific filter/list pages (which already support building and, via URL, effectively "bookmarking" a filtered view) |
| Persisted "recent searches" (storing/displaying past **query strings**, not opened records) | Seems like the same feature as "recently viewed items" (a genuine differentiator above) | For a legal practice tool, a visible list of recently-typed search strings on a shared or unlocked workstation is a mild confidentiality leak (e.g., a receptionist's screen showing a colleague's search for a sensitive client/case name) — a real concern given the domain, not a hypothetical one | Recently-**viewed records** (see Differentiators) is safer and more useful; even that should stay session/browser-local, not synced or backend-persisted, given the same confidentiality reasoning |
| Full-text / OCR search inside uploaded document **contents** (PDF/DOCX body text) | The most literal reading of "search documentos" a user might expect | `Documento` today stores only metadata (`nome`, `tipo`) — no extracted text exists anywhere in the system. Building content extraction + indexing (e.g. Tika, a dedicated search engine, embeddings) is a genuine infrastructure project on its own, orders of magnitude larger than a UI-polish milestone item, and was not what was scoped ("pesquisa global... com endpoint novo... botão... atalho") | Ship metadata-only search for Documentos in this milestone (`nome`, `tipo`, and its linked cliente/processo); treat content search as a legitimate **future, separately-scoped** milestone if users ask for it |
| Dedicated search infrastructure (Elasticsearch, Meilisearch, Algolia, a separate search microservice) | "Real" search products use dedicated search engines | Overkill relative to this product's actual scale (single-institution tenants, hundreds–low-thousands of rows, one Postgres instance already serving as the source of truth for everything including notifications and audit) and inconsistent with the project's own consistent zero-new-infra bias (no message queue, no separate search stack anywhere in the stack today) | Plain SQL (`ILIKE`, optionally `pg_trgm` for fuzzy matching later) against existing Postgres tables — same tool already used for the one real search implementation in the codebase (`ParecerSolicitacaoRepository.pesquisar`) |
| Natural-language / AI-powered semantic search (e.g. "processos parecidos com despejo do ano passado") | Trendy, and the model already has a `Notificacao`/AI-adjacent-sounding surface nearby | Enormous scope and cost relative to the ask; also raises new data-handling questions for confidential legal content that the rest of this product has deliberately avoided (no AI/LLM integration exists anywhere in LexCV today) | Not needed; literal/structured search already fits how institutional users actually query (by exact identifiers, names, and known filters) |
| Cross-tenant / platform-wide search | Sounds like a natural extension of "search everything" | Would directly violate the single inviolable architectural boundary of this product — every domain entity is `tenant_id`-scoped, and CLAUDE.md is explicit that this is the primary data-isolation mechanism, never to be bypassed | Every sub-query must filter by the caller's own `tenant_id`, exactly like every other endpoint in the codebase — flagged here because a generically-written "search everything" implementation is precisely the kind of code that could accidentally drop a `WHERE tenant_id = ?` clause if not deliberately checked |
| Command-palette-as-action-launcher (typing "criar novo cliente" to trigger actions, not just find records) | `CommandDialog`/cmdk is exactly the component Raycast/Linear use for *both* search and actions, so it's an easy scope-adjacent feature to reach for once the palette exists | Roughly doubles the feature's scope (needs a permission-gated action registry, not just a query fan-out) for something the milestone never asked for; conflates two different feature classes the way "saved searches" does above | Keep v1 to record search + navigate only. The palette's structure would technically support an action-launcher later without rework — worth noting as a clean **future** extension point, not part of this milestone |
| Sophisticated fuzzy/typo-tolerant matching (edit-distance, phonetic matching) beyond simple substring `ILIKE` | Feels like "better search" | For professional/institutional users who type exact structured identifiers (`CLI-0001`, 9-digit NIF, `numero_processo`) most of the time, plain substring/prefix matching already covers the dominant real query pattern in this domain; fuzzy-matching infrastructure (e.g. `pg_trgm` similarity scoring) is disproportionate investment for a first release with no evidence of a typo-driven miss problem | Ship `ILIKE` substring/prefix matching first (consistent with the one existing precedent in the codebase); revisit only if real usage shows a measurable typo-related miss rate |

## Feature Dependencies

```
Cross-entity global search (core, v1)
    ├──requires──> Cliente entity + `clientes:view` scope (existing)
    ├──requires──> Processo entity + `processos:view` scope (existing)
    ├──requires──> Documento entity + `documentos:view` scope (existing entity/scope,
    │                but NEW backend filter capability — GET /documentos has zero
    │                query params today)
    ├──requires──> ParecerSolicitacao (+ latest ParecerVersao.conteudo)
    │                + `pareceres:view` scope (existing; can extend the proven
    │                native-SQL-query pattern already in ParecerSolicitacaoRepository)
    ├──requires──> ONE new unified backend endpoint that fans out to 4 tenant-scoped,
    │                RBAC-gated, LIMIT-capped sub-queries and returns a single
    │                grouped/discriminated response (matches PROJECT.md's singular
    │                "endpoint novo")
    ├──requires──> New frontend debounce mechanism (does not exist yet)
    ├──requires──> New frontend global Ctrl/Cmd+K keydown listener (does not exist yet)
    └──reuses────> Existing `CommandDialog`/`Command*` primitives (web/src/components/ui/command.tsx,
                     installed v2.13 Phase 101, currently only used inside Combobox)

Per-entity-type RBAC gating ──is prerequisite for──> Cross-entity global search
    (must ship together — a version of "search everything" that doesn't check each
    entity's own scope independently is a security regression, not a smaller v1)

Tenant isolation on every sub-query ──is prerequisite for──> Cross-entity global search
    (same reasoning — not a separable/deferrable piece)

Structured-identifier matching (numero_cliente, numero_processo, nif, documento_numero)
    ──enhances──> Cross-entity global search
    (Documento and ParecerSolicitacao have no equivalent identifier field — those two
    entity groups in the result set are always free-text/relational-only, which the
    ranking logic must account for rather than assume every group behaves the same)

"Ver todos os resultados de X" overflow link ──enhances──> Cross-entity global search
    └──requires──> Clientes/Processos list pages' existing `?q=` param (already present)
    └──requires (new, small)──> equivalent `q`-style param added to Documentos list
                                   (currently has none) and reconciled with Pareceres'
                                   existing but differently-shaped `/pareceres/pesquisa?texto=`

Recently-viewed records (empty-state) ──enhances──> Cross-entity global search
    (independent, can ship before/after/without it — no hard dependency either direction)

Saved searches ──conflicts with / is superseded by──> Pareceres' existing dedicated
    advanced search page (and Processos' new estado filter, same milestone) — building
    both a global "save this search" feature and per-entity advanced filter pages is
    redundant scope for the same underlying need

Full-text document content search ──blocked by──> Documento having no extracted-text
    storage anywhere today (a genuine prerequisite gap, not a v1-scope decision)
```

## MVP Definition

### Launch With (v1)

Matches the milestone's explicit, literal scope ("endpoint novo no backend + botão 'Pesquisar' + atalho de teclado no frontend").

- [ ] One new backend endpoint that searches Cliente, Processo, Documento, and ParecerSolicitacao for the caller's tenant, gating each entity branch independently by its own RBAC scope (`clientes:view`, `processos:view`, `documentos:view`, `pareceres:view`) — essential: this *is* the feature, and the security gating is inseparable from it
- [ ] Structured-identifier matching (`numero_cliente`, `numero_processo`, `nif`, `documento_numero`) alongside free-text name/description matching, with exact/prefix identifier matches ranked at or above fuzzy substring matches — essential: this is the specific domain requirement the question raises, and skipping it would ship a search that fails on the query pattern institutional users will actually use most
- [ ] Frontend: convert the existing decorative header `<Input>` into a trigger (click) for a `CommandDialog`-based palette, reusing the already-installed `Command`/`CommandDialog`/`CommandGroup`/`CommandItem`/`CommandEmpty` primitives — essential: this is the "botão 'Pesquisar'" half of the ask, and reuses existing components rather than building new UI
- [ ] Global `Ctrl+K` / `⌘K` keyboard shortcut to open the same palette — essential: this is the explicit "atalho de teclado" half of the ask
- [ ] Search-as-you-type with ~300ms debounce and a minimum query length (e.g. 2 characters) before firing — essential given the fan-out cost of hitting 4 tables per query
- [ ] Results grouped by entity type with a short disambiguating subtitle per result, capped per group (e.g. top 5) — essential baseline UX, and cheap given `CommandGroup` already handles empty-group hiding
- [ ] Click a result → navigate to its existing detail route (`/clientes/[id]`, `/processos/[id]`, `/documentos/[id]`, `/pareceres/[id]`), closing the palette — essential, and free (routes already exist)
- [ ] Empty state and no-results state — essential baseline, mostly provided by `CommandEmpty` already
- [ ] Tenant isolation verified on all 4 sub-queries — essential, non-negotiable given the product's core multi-tenancy boundary

### Add After Validation (v1.x)

- [ ] "Ver todos os resultados de [Tipo]" overflow link per group, deep-linked into that entity's list page with the query pre-filled — add once the base palette is live and a per-group cap (e.g. top 5) is actually being hit in practice; also a natural moment to reconcile Documentos'/Pareceres' list-level query params with Clientes'/Processos' existing `?q=` convention
- [ ] Recently-viewed records (client-side/session-local) shown in the empty/pre-query state — add if usage shows people reopening the same records repeatedly; keep it record-based (not query-string-based) and local, not backend-persisted, for the confidentiality reasons noted above
- [ ] Matched-substring highlighting in result labels — cheap polish, not essential to function

### Future Consideration (v2+)

- [ ] Full-text/OCR search inside uploaded document contents — genuinely large scope (content extraction + indexing), defer until explicitly requested and separately scoped
- [ ] Fuzzy/typo-tolerant matching (`pg_trgm` similarity or equivalent) — defer until real usage shows a measurable typo-driven miss rate; unlikely to matter given how identifier-heavy this domain's queries are
- [ ] "Smart jump" on Enter for an unambiguous single exact-identifier match — nice power-user touch, but a distinct, boundable follow-up rather than part of the initial ship
- [ ] Command-palette-as-action-launcher (create/navigate actions beyond record search) — the installed primitives would structurally support this later without rework, but it's a different feature class from "pesquisa global" and would roughly double this milestone's scope if pulled forward
- [ ] Any form of saved search, persisted search history, or search analytics — explicitly not warranted for a focused v1 in an internal institutional tool with a handful of users per tenant (see Anti-Features)

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Unified backend endpoint (4-entity fan-out, RBAC + tenant scoped) | HIGH | MEDIUM | P1 |
| Structured-identifier matching + exact-match-first ranking | HIGH | LOW-MEDIUM | P1 |
| `CommandDialog` palette wired to header trigger | HIGH | LOW | P1 |
| Ctrl/Cmd+K shortcut | MEDIUM-HIGH | LOW | P1 |
| Debounce + minimum query length | HIGH (infra necessity) | LOW | P1 |
| Grouped results with disambiguating subtitles | HIGH | LOW | P1 |
| Click-through navigation to detail routes | HIGH | LOW | P1 |
| Per-entity RBAC gating + tenant isolation | HIGH (non-negotiable) | LOW-MEDIUM | P1 |
| "Ver todos os resultados" overflow links | MEDIUM | LOW (Clientes/Processos) – MEDIUM (Documentos/Pareceres) | P2 |
| Recently-viewed records in empty state | MEDIUM | LOW-MEDIUM | P2 |
| Matched-substring highlighting | LOW-MEDIUM | LOW-MEDIUM | P2 |
| Search by Processo's assigned lawyer name | MEDIUM | MEDIUM | P2/P3 |
| Smart exact-match jump on Enter | LOW-MEDIUM | MEDIUM | P3 |
| Document content (OCR) search | MEDIUM (if ever requested) | HIGH | P3 / out of this milestone |
| Fuzzy/typo-tolerant matching | LOW (at current scale) | MEDIUM | P3 |
| Saved searches / search history / search analytics | LOW (unrequested) | MEDIUM-HIGH | Do not build |

**Priority key:**
- P1: Must have for this milestone's launch (matches PROJECT.md's literal target)
- P2: Should have, natural next iteration once v1 usage is observed
- P3: Nice to have, future consideration only if explicitly requested later

## Competitor Feature Analysis

General web research on named legal-practice-management competitors (Clio, MyCase, PracticePanther) did not surface concrete, verifiable documentation of their internal global-search UX — their public marketing/comparison content doesn't document this level of interaction detail, and going deeper (their own help centers, which typically sit behind a login) was out of reach for this research pass. This is flagged honestly rather than presented as verified: any specific claim about how a named legal-software competitor implements search should be treated as **LOW confidence / unverified** if it appears elsewhere.

What **is** well-documented (HIGH confidence, multiple independent sources) is the general B2B SaaS command-palette pattern this feature should follow, which happens to be exactly what LexCV's already-installed primitives (`cmdk`/shadcn `Command`) are built for:

| Pattern element | How Linear/Notion/GitHub/Vercel/Slack do it | Our approach |
|---|---|---|
| Trigger | Visible search input/button in the header + `Ctrl+K`/`⌘K` shortcut, often shown as a `kbd` hint inside the trigger | Convert the existing (currently decorative) header search `<Input>` into this exact trigger; matches PROJECT.md's explicit ask for both a button and a shortcut |
| Modal | A centered/upper-screen modal overlay, not an inline dropdown | `CommandDialog` (already installed, unused) is precisely this |
| Grouping | Results grouped under labeled sections by type/source | `CommandGroup` (already installed) auto-hides empty groups |
| Empty state | Recent/frequently-used items shown before any typing | Recommended as a v1.x differentiator here (client-side, record-based, not query-string-based — see Anti-Features for why the distinction matters in a legal-confidentiality context) |
| Debounce | ~300ms is the reported industry sweet spot for desktop search-as-you-type | Adopted directly (Table Stakes) |

## Sources

- Direct code inspection (this session): `web/src/components/ui/command.tsx`, `web/src/components/shared/dashboard-shell.tsx`, `web/src/components/shared/combobox.tsx`, `web/src/lib/permissions.ts`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (Clientes/Processos/Documentos list endpoints), `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java`, `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`, `backend/src/main/java/com/lexcv/models/{Cliente,Processo,Documento,ParecerSolicitacao,ParecerVersao}.java`, `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (role→scope assignments), `web/package.json` (`cmdk@1.1.1` confirmed present), `.planning/PROJECT.md` (milestone target wording, decision log re: `GET /documentos` gap)
- [Command Palette Pattern — UX Patterns for Developers](https://uxpatterns.dev/patterns/advanced/command-palette) — grouping, empty-state, recent-items conventions
- [Designing Command Palettes — Sam Solomon](https://solomon.io/designing-command-palettes/) — Linear/Notion/Vercel convention analysis
- [Destiner's notes — Designing a Command Palette](https://destiner.io/blog/post/designing-a-command-palette/)
- [Mobbin — Command Palette UI Design](https://mobbin.com/glossary/command-palette)
- [SaaSUI — Command Palette glossary entry](https://www.saasui.design/glossary/command-palette)
- [Algolia — Debounce sources](https://www.algolia.com/doc/ui-libraries/autocomplete/guides/debouncing-sources) — debounce timing guidance
- [Atomic Object — Improve Your Search Autocomplete Timing with Debouncing](https://spin.atomicobject.com/2018/06/04/automplete-timing-debouncing/)
- [Algolia Engineering — Fuzzy search 101](https://www.algolia.com/blog/engineering/fuzzy-search-101) — exact-match vs. fuzzy-match ranking principles
- [Redis — What is fuzzy matching?](https://redis.io/blog/what-is-fuzzy-matching/) — exact-match-ranks-first principle in hybrid search
- Legal-practice-management competitor search (Clio, MyCase, PracticePanther) — WebSearch surfaced only marketing/comparison pages, no verifiable UX documentation; treated as a gap, not a claim (LOW confidence where referenced above)

---
*Feature research for: Cross-entity global search, LexCV v2.14*
*Researched: 2026-07-18*
