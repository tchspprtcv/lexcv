# Stack Research: Cross-Entity ("Pesquisa Global") Search

**Domain:** Unified/cross-entity search (clientes, processos, documentos, pareceres) for a multi-tenant Spring Boot 3.4.1 + PostgreSQL legal-practice SaaS, exposed to a Next.js 16 + TanStack Query frontend
**Researched:** 2026-07-18
**Confidence:** HIGH — every claim below is grounded either in this repository's own code (file:line citations) or in the official PostgreSQL 16 docs / official `docker-library/postgres` build source, not training-data recall alone.

## Bottom line

**No new backend dependency is warranted.** LexCV already has everything needed: `spring-boot-starter-data-jpa` + the `org.postgresql:postgresql` driver already do native SQL passthrough, and PostgreSQL 16 (the exact `postgres:16-alpine` image already in `docker-compose.yml`) ships the two relevant contrib extensions (`pg_trgm`, `unaccent`) compiled into the image already — enabling them is a one-line `CREATE EXTENSION` migration, not a new library or a new container. The frontend is in the same position: `cmdk@1.1.1` and shadcn's `Command`/`CommandDialog`/`CommandGroup` primitives are already installed (`web/src/components/ui/command.tsx`, shipped in v2.13 Phase 107) and are a near-perfect fit for "grouped by entity type, behind a keyboard shortcut."

The real decision isn't *which library* — it's **which PostgreSQL search technique** (plain `ILIKE`, `pg_trgm` similarity, or `tsvector`/`tsquery` full-text search) fits LexCV's actual scale. Given "a handful of institutional tenants, thousands not millions of rows per tenant," and every query mandatorily led by an indexed `tenant_id` equality predicate, **plain multi-column `ILIKE` (wrapped in `unaccent()`) is the correct MVP technique** — it is the same technique this codebase already uses twice, at a data volume where it is not a performance risk. `pg_trgm` and `tsvector` are documented below as the correct *next* steps, not as things to build now.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| PostgreSQL `unaccent` (contrib extension) | Bundled with PostgreSQL 16 — same version as the already-deployed `postgres:16-alpine` image (`docker-compose.yml:3`); no independent version number | Strip diacritics (´ ç ã õ etc.) before comparison, on both the stored column and the search term | LexCV's data is Portuguese: client names ("Conceição"), localities ("São Vicente"), etc. A user who types "Conceicao" (no cedilla/tilde) gets zero matches under plain `ILIKE` today — this is a real, concrete UX gap, not hypothetical. `unaccent()` closes it in one function call, no schema change required for the MVP query shape (see caveat under Version Compatibility about functional-index use later) |
| `ILIKE` inside a native `@Query` (`spring-boot-starter-data-jpa`, already in `backend/pom.xml`) | Already present — Spring Boot 3.4.1 parent BOM, no version bump | Case-insensitive substring match across `t_cliente`, `t_processo`, `t_documento`, `t_parecer_solicitacao` | This is not a new pattern — it is the **exact same idiom already proven twice** in this codebase: `ParecerSolicitacaoRepository.pesquisar()` (`backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java:41-58`) and `NotificacaoRepository.buscarPorFiltros()` (`backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:26-42`), both native `@Query`, both `CAST(:param AS type) IS NULL OR col ILIKE '%' \|\| CAST(:param AS text) \|\| '%'`. A new global-search query should reuse this literal idiom, not invent a second search paradigm |
| `pg_trgm` (contrib extension) | Bundled with PostgreSQL 16, same image | Trigram similarity (`similarity()`, `%` operator) and, later, `GIN (col gin_trgm_ops)` indexes that transparently accelerate `ILIKE '%term%'` | Recommend enabling it **now** (cost: zero — it's already compiled into the image, `CREATE EXTENSION` is instant) but **not using it for anything in v1**. Enabling it up front means the future "add a trigram index" or "add typo-tolerance" step is a pure index/query addition later, never a re-provisioning exercise |

### Supporting Libraries (frontend — already installed, zero new packages)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `cmdk` | `^1.1.1` (`web/package.json:18`, already installed since v2.13 Phase 107) | Headless command-palette primitive powering shadcn's `Command` | Already the base of `web/src/components/ui/command.tsx` |
| shadcn `CommandDialog` / `CommandInput` / `CommandList` / `CommandGroup` / `CommandItem` | Already generated in `web/src/components/ui/command.tsx:36-192` | Dialog-wrapped, keyboard-navigable palette with **built-in per-group headings** | `CommandGroup heading="Clientes"` (etc.) maps directly onto "results grouped … by entity type" — this is not an approximation, it's the literal feature the primitive exists for. Trigger with a `keydown` listener for Cmd/Ctrl+K (no library needed, ~10 lines) |
| `@tanstack/react-query` | `^5.87.4` (`web/package.json:14`, already installed) | Data-fetching hook for the new search endpoint | A new `useGlobalSearch(query)` hook, same shape as every other `use-*.ts` hook in `web/src/hooks/` |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `backend/migrations/*.sql` manual-migration convention | Ship the two `CREATE EXTENSION` statements | This repo has no Flyway/Liquibase (`ddl-auto=update` in dev / `validate` in prod, per `CLAUDE.md`); every schema-adjacent change ships as a hand-numbered script in `backend/migrations/` (see `86-create-notificacao-table.sql`, `96-add-notificacao-snoozed-until.sql`). A `CREATE EXTENSION IF NOT EXISTS unaccent;` / `pg_trgm;` script follows the exact same convention and must be run manually at deploy time like the others — it is *not* something Hibernate's `ddl-auto` will do for you |

## Installation

There is nothing to add to `pom.xml` and nothing to `mvn`/`pnpm install`. The only "installation" step is a SQL migration, run once per environment (matching the existing manual-migration deploy step already documented for every prior `backend/migrations/*.sql` file):

```sql
-- backend/migrations/NN-enable-search-extensions.sql
-- Both are PostgreSQL contrib modules, already compiled into the postgres:16-alpine
-- image (docker-library/postgres builds `make -C contrib install` for every Alpine
-- variant) — this is a metadata-only operation, not a package install.
CREATE EXTENSION IF NOT EXISTS unaccent;
CREATE EXTENSION IF NOT EXISTS pg_trgm;
```

Illustrative shape of the actual search query (per entity, reusing the exact `CAST(:param AS type) IS NULL OR …` + `ILIKE` idiom already in `ParecerSolicitacaoRepository`/`NotificacaoRepository`):

```sql
SELECT c.* FROM t_cliente c
WHERE c.tenant_id = :tenantId
  AND unaccent(c.nome) ILIKE unaccent('%' || CAST(:texto AS text) || '%')
ORDER BY
  CASE WHEN lower(c.nome) = lower(CAST(:texto AS text)) THEN 0
       WHEN lower(c.nome) LIKE lower(CAST(:texto AS text)) || '%' THEN 1
       ELSE 2 END,
  c.created_at DESC
LIMIT :limit
```

`tenant_id = :tenantId` must be the leading predicate in every one of the four per-entity queries — see Integration Notes below.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|--------------------------|
| Plain `ILIKE` + `unaccent()`, no index | `pg_trgm` `GIN (col gin_trgm_ops)` index, same `ILIKE` query | Once a single tenant's searched table(s) grow well past the "thousands of rows" range described for this milestone (rough order of magnitude: tens of thousands+ rows in one table) and search latency becomes user-visible. The index is purely additive — it accelerates the *same* `ILIKE '%term%'` query verbatim, no query rewrite. Enable `pg_trgm` now (free) so this is a future index-creation migration, not a future extension-provisioning exercise |
| Plain `ILIKE` ordering (`CASE WHEN exact/prefix THEN 0/1 ELSE 2`, then recency) | `tsvector`/`tsquery` + `ts_rank` full-text search | If/when a genuine cross-row relevance-ranking requirement appears (e.g. ranking hits *within* a long free-text field like `Processo.descricao` or `ParecerVersao.conteudo` by term frequency/proximity, or supporting boolean query syntax) rather than the "grouped by entity type, exact-match-first" behavior this milestone actually asks for. Requires a `GENERATED ALWAYS AS (to_tsvector('portuguese', …)) STORED` column + `GIN` index per searched table (PostgreSQL ships a built-in `'portuguese'` text-search configuration, no extra dictionary install) — a real schema change across 4 tables, not warranted pre-emptively |
| 4 independent tenant-scoped native queries (one per existing repository), merged in a small service/controller | A single `UNION ALL` native query spanning all 4 tables | Only if a single DB round-trip genuinely matters (it won't, at this row count) or a single global `LIMIT`/pagination cutoff across all types is required. A `UNION ALL` across 4 differently-shaped tables needs every branch individually `CAST`-aligned to a common column shape and, critically, needs `tenant_id = :tenantId` correctly repeated in *every* branch — this repo's own milestone audit history has twice caught a filter silently missing from one query while "looking correct" in isolation (`GET /honorarios?processo_id=X`, `GET /documentos?processo_id=X`, both fixed in the v2.9 milestone audit). Four separate, independently-reviewable repository calls — one per existing `ClienteRepository`/`ProcessoRepository`/`DocumentoRepository`/`ParecerSolicitacaoRepository` — are lower-risk for the one invariant that must never break here (tenant isolation) |
| Reuse `TimelineItemDto`-style discriminated `record` for the response shape | A generic/polymorphic JSON shape per entity type | This codebase already solved "merge heterogeneous entities into one normalized list" once, for `GET /processos/{id}/timeline` (`backend/src/main/java/com/lexcv/dtos/TimelineItemDto.java`: `record TimelineItemDto(String tipo, String id, LocalDateTime timestamp, String titulo, String descricao, String autorNome)`, merging `Movimentacao`/`Evento`/`Documento`/`ConflictCheckDecisao`). A `PesquisaGlobalResultDto(String tipo, String id, String titulo, String subtitulo, String url)` is the same pattern applied tenant-wide instead of processo-scoped — no new architectural concept |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|--------------|
| Elasticsearch / OpenSearch / Meilisearch / Algolia / Typesense | Wrong tool at this scale. LexCV is a single-VPS `docker-compose` deployment (Postgres + MinIO + backend + `web` + `webpage` + Caddy already share one host, per `docker-compose.yml`) serving "a handful of institutional tenants" with thousands of rows each — nowhere near where a dedicated search engine pays for itself. It would add a whole second service to operate (indexing pipeline, dual-write consistency between Postgres and the index, extra container/RAM on a VPS that isn't provisioned for it) and, worse, creates a **second place `tenant_id` scoping has to be correctly re-implemented outside SQL** — exactly the kind of cross-cutting isolation risk this project's `CLAUDE.md` calls out as the primary constraint to never bypass | PostgreSQL `ILIKE`/`unaccent` now; `pg_trgm`/`tsvector` later if row counts genuinely grow (see Stack Patterns by Variant) |
| Hibernate Search (Lucene or Elasticsearch-backed) | Same objection as above, plus a heavier in-code commitment: `@Indexed`/`@FullTextField` annotations across 4 entities, an index lifecycle to manage (rebuild-on-deploy strategy for an embedded Lucene index, or an external Elasticsearch dependency for a remote one) — solving a problem plain SQL already solves at this row count | Native `@Query` on existing Spring Data JPA repositories (already proven in this codebase) |
| Apache Tika / OCR / any document-content-extraction library | This solves a *different, larger* feature — full-text search **inside uploaded file bytes** (PDF/DOCX/scanned-image content) — than what's requested. `Documento` (`backend/src/main/java/com/lexcv/models/Documento.java`) stores `nome`/`tipo`/`caminhoArquivo` (a MinIO object key), not extracted text; "documentos" in the milestone brief reads as searching that metadata, exactly parallel to how Pareceres search already works against a stored text column (`ParecerVersao.conteudo`), never against binary content. Do not conflate the two — content-extraction search is a legitimately larger feature that would need its own research and explicit product sign-off | `ILIKE`/`unaccent` against `Documento.nome`/`Documento.tipo` |
| QueryDSL / jOOQ / Blaze-Persistence or any other SQL-building abstraction | Four independent tenant-scoped native/JPQL `@Query` methods sit entirely inside the vocabulary Spring Data JPA already provides, and this codebase already exercises (two working examples cited above). Introducing a query-builder library for a 4-table search adds a new abstraction with no problem left for it to solve | Plain `@Query(nativeQuery = true)` methods on existing repositories |
| A new frontend debounce package (e.g. `use-debounce`, `lodash.debounce`) | Trivial to implement locally (`useState` + `useEffect` + `setTimeout`, ~10 lines) and the codebase has zero existing debounce dependency to justify pulling one in for this alone | A small local `useDebouncedValue` hook colocated with the new `useGlobalSearch` hook |
| A dedicated full paginated "resultados" page/`Pageable` machinery for v1 | The milestone brief describes a "botão 'Pesquisar' + atalho de teclado" — a command-palette/typeahead pattern (top-N-per-type, dismissable), not a browsable results page. Building `Page`/count-query ceremony (the `NotificacaoRepository.buscarPorFiltros` pattern) for a UI that never needs to page through results is premature | A plain `LIMIT :limit` bind parameter per entity query (e.g. top 5-8), native-query `Pageable`/`Page` reserved for if/when a "ver todos os resultados" page is explicitly requested later — reusing the exact `NotificacaoRepository` precedent at that point |

## Stack Patterns by Variant

**If row counts per tenant stay in the thousands (current, stated expectation):**
- Use plain `ILIKE` wrapped in `unaccent()`, no index beyond whatever B-tree already backs `tenant_id` lookups.
- Because a `tenant_id = ?` equality filter followed by a substring scan over a few thousand rows is, in practice, a low-single-digit-millisecond operation on typical hardware — there is no measured or expected performance problem to solve yet, so building index/ranking infrastructure now would be solving a problem LexCV doesn't have.

**If a searched table for one tenant grows past roughly tens of thousands of rows and search latency becomes noticeable:**
- Add `CREATE INDEX … USING GIN (unaccent(col) gin_trgm_ops)` (trigram GIN index) on the specific `ILIKE`-searched columns.
- Because `pg_trgm`'s GIN/GiST operator classes accelerate `LIKE`/`ILIKE`/regex queries directly (confirmed in PostgreSQL 16 docs: "these indexes support similarity operators as well as trigram-based searches for LIKE, ILIKE, regular expressions, and equality queries") — **no query rewrite is required**, since `pg_trgm` was already enabled in the MVP migration. This is the reason to enable the extension on day one even though nothing uses its indexing capability yet.

**If genuine relevance-ranking or typo-tolerance becomes an explicit user request (not assumed pre-emptively):**
- For ranking/stemming on long free-text fields (`Processo.descricao`, `ParecerVersao.conteudo`): add a `GENERATED ALWAYS AS (to_tsvector('portuguese', …)) STORED` column + `GIN` index on that column, query with `@@ to_tsquery('portuguese', …)`, order with `ts_rank`. PostgreSQL ships a built-in `'portuguese'` search configuration (stemming/stopwords), no dictionary installation needed.
- For typo-tolerance (e.g. a misspelled client surname): use `pg_trgm`'s `similarity()`/`%` operator against the already-enabled extension — again additive, no re-provisioning.
- Do **not** build either pre-emptively: the milestone's explicit requirement is "grouped … by entity type," not a single flat cross-entity relevance-ranked list — grouping sidesteps the hardest part of "ranking" (making relevance scores comparable across four differently-shaped entities), so within-group ordering only needs the simple heuristic already shown in Installation (exact/prefix match first, then recency), which plain SQL handles today.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|------------------|-------|
| `postgres:16-alpine` (already `docker-compose.yml:3`) | `pg_trgm`, `unaccent` contrib modules | Verified directly against the official `docker-library/postgres` build source (`16/alpine3.23/Dockerfile`, GitHub): the Alpine build runs `make -C contrib install` after the core build, compiling **all** contrib modules — including `pg_trgm` and `unaccent` — into the image. `CREATE EXTENSION` therefore works immediately against the existing image; no Dockerfile change, no image swap, no `apk add` step |
| `unaccent(text)` | Functional indexes / generated columns | **Caveat for later, not for the v1 MVP query:** the built-in `unaccent(text)` function is `STABLE`, not `IMMUTABLE` (its behavior depends on `search_path` and external dictionary files) — PostgreSQL will refuse to use it directly inside an index expression or a `GENERATED … STORED` column (`ERROR: functions in index expression must be marked IMMUTABLE`). It works fine as-is inside a plain `WHERE` clause (the v1 recommendation above), where no immutability requirement applies. If a functional index on `unaccent(col)` is added later (per Stack Patterns by Variant), wrap it first: `CREATE FUNCTION immutable_unaccent(text) RETURNS text AS $$ SELECT unaccent('unaccent', $1) $$ LANGUAGE SQL IMMUTABLE PARALLEL SAFE STRICT;` and index/generate on `immutable_unaccent(col)` instead |
| `spring-boot-starter-data-jpa` (Spring Boot 3.4.1 parent BOM) | `nativeQuery = true` + `Pageable`/`LIMIT` bind params | Already proven end-to-end in this exact codebase and covered by CI: `NotificacaoRepository.buscarPorFiltros` (native `@Query` + `Pageable` + separate `countQuery`) is exercised by a real Testcontainers-PostgreSQL integration test (`NotificacaoRepositoryIT`) gated in `deploy.yml`'s `test` job. No version-compatibility risk here — this is not new ground for the project |
| `cmdk@1.1.1` / shadcn `Command*` primitives | React 19 / Next.js 16 (already the frontend stack) | Already installed and in production use since v2.13 Phase 107 (the `Combobox` component) — no compatibility question to resolve |

## Integration Notes (tenant isolation — the quality gate for this feature)

- Every one of the four per-entity queries must lead with `tenant_id = :tenantId` (or the entity's transitive-tenant pattern already used for `Decisao`/`Facto`/`Testemunha`, where applicable) — bound the same way `getTenantId()` is already read from `UserPrincipal`/`SecurityContextHolder` in every existing controller (`ParecerPesquisaController.java:37-41` is the smallest reference example). No exceptions, no entity type skipped.
- Follow the existing dedicated-controller precedent, not the mega-controller one: `ParecerPesquisaController` exists specifically *because* a cross-cutting search route couldn't safely live inside the entity's main ~1000-line controller (`ResourceController`) — the same reasoning applies more strongly to a search that spans four entities at once. A new `GlobalSearchController`/`PesquisaGlobalController` at its own top-level `@RequestMapping` avoids `ResourceController` growing further and avoids the exact routing bug `ParecerPesquisaController`'s own header comment documents (Spring concatenates class-level + method-level `@RequestMapping` regardless of a leading `/`, so this must be its own class, never a method appended to an existing `/api/v1/...`-mapped controller in a way that double-prefixes).
- RBAC must be applied **per entity slice**, not all-or-nothing on the whole endpoint: a caller without `pareceres:view` should see zero Pareceres results (silently omit that slice) rather than get a 403 for the entire search, mirroring how the app already treats partial-permission surfaces elsewhere (e.g. Financeiro visible only to ADMIN/TECNICO). Concretely: check each scope (`clientes:view`, `processos:view`, `documentos:view`, `pareceres:view`) before running that entity's query, inside the one endpoint.
- Reuse existing repositories rather than building parallel ones: add one new `@Query(nativeQuery = true)` "quick search" method each to `ClienteRepository`/`ProcessoRepository`/`DocumentoRepository` (mirroring the `ParecerSolicitacaoRepository.pesquisar` CAST-guard + `ILIKE` idiom), and for Pareceres, prefer calling the **existing** `ParecerSolicitacaoRepository.pesquisar(...)` (already does exactly this search, LEFT JOIN to latest `ParecerVersao` included) rather than writing a second parallel query against the same table.
- Never string-concatenate the search term into SQL in Java before binding it — the safe idiom already in use is to bind `:texto` as a parameter and do the `'%' || CAST(:texto AS text) || '%'` wildcard-wrapping *inside* the SQL string, exactly as both existing native queries do. A new query must follow this literally, given this project's ASVS-level-1 security gate on new backend code.

## Sources

- Context7 `/websites/postgresql_16` — `textsearch-tables.html`, `textsearch-indexes.html`, `functions-textsearch.html`, `textsearch-controls.html` (tsvector generated column, GIN index, `ts_rank`); `pgtrgm.html` (trigram GIN/GiST index support, including for `ILIKE`); `unaccent.html` (function signature, text-search-configuration integration) — HIGH confidence, official PostgreSQL 16 docs, version-matched to the deployed image
- WebSearch, corroborated by PostgreSQL mailing list thread (`postgresql.org/message-id/CABRT9RAxL5nL-34WeigFiGHWi+P-kpgbGO=iK70o6us1Jr4rfw@mail.gmail.com`) — `unaccent()` is `STABLE` not `IMMUTABLE`, and the standard `IMMUTABLE STRICT` SQL-wrapper workaround for functional-index use — MEDIUM-HIGH confidence (community-verified, consistent explanation across multiple independent threads, aligns with documented PostgreSQL immutability rules for index expressions)
- WebFetch, `raw.githubusercontent.com/docker-library/postgres/master/16/alpine3.23/Dockerfile` — confirms `make -C contrib install` compiles `pg_trgm`/`unaccent`/etc. into the official `postgres:16-alpine` image — HIGH confidence, primary source (the actual build script for the exact image LexCV already runs)
- In-repo evidence (HIGH confidence, read directly): `backend/pom.xml`, `docker-compose.yml`, `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`, `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java`, `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (lines ~155-230, ~930-970 — the current in-memory-stream filtering pattern for Clientes/Processos lists), `backend/src/main/java/com/lexcv/models/{Cliente,Processo,Documento,ParecerSolicitacao}.java`, `backend/src/main/java/com/lexcv/dtos/TimelineItemDto.java`, `web/package.json`, `web/src/components/ui/command.tsx`, `web/src/components/shared/dashboard-shell.tsx` (the current decorative search input, lines 121-127)

---
*Stack research for: Cross-entity global search backend (LexCV v2.14 milestone)*
*Researched: 2026-07-18*
