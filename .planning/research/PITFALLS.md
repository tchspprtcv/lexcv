# Pitfalls Research

**Domain:** Adding a new cross-entity global search feature (clientes, processos, documentos, pareceres) to an existing production multi-tenant, RBAC'd legal-practice platform (LexCV, v2.14 milestone)
**Researched:** 2026-07-18
**Confidence:** HIGH for all codebase-specific findings (verified by direct inspection of models/repositories/controllers/security config/seed data, not assumed); HIGH for the cmdk `shouldFilter` claim (verified against the project's own existing workaround in `combobox.tsx` AND cross-checked against upstream cmdk documentation); MEDIUM for general multi-tenant-search industry patterns not specific to this codebase.

## Project Baseline (verified by direct inspection, not assumed)

These facts materially change the risk profile versus generic "add search to a multi-tenant app" advice, and are referenced throughout:

- **The sidebar search input is 100% decorative today.** `web/src/components/shared/dashboard-shell.tsx:121-127` renders a plain `<Input placeholder="Pesquisar processos, entidades...">` with no `onChange`, no state, no query. This is a from-scratch feature, not a wire-up of existing plumbing — there is no existing cross-entity query, DTO, or hook to extend.
- **`Honorario` (the `financeiro` entity) has no `tenant_id` column at all.** Confirmed in `backend/src/main/java/com/lexcv/models/Honorario.java`: its only ownership link is `processo_id` (a bare `UUID` FK, not a mapped JPA association). Every existing read/write path in `ResourceController` (`getHonorario`, `updateHonorario`, `deleteHonorario`, `listHonorarioPagamentos`, `listHonorarios`) manually re-fetches the parent `Processo` and checks `processo.getTenantId().equals(getTenantId())` before trusting the row. There is no shortcut — tenant isolation for this entity is **transitive and must be re-derived every time**, and nothing in the schema enforces it automatically.
- **The exact RBAC matrix (verified directly in `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:293-353`), for the four entity types this milestone explicitly targets plus the adjacent financeiro scope:**

  | Scope | ADMIN | ADVOGADO | TECNICO | ASSISTENTE |
  |---|---|---|---|---|
  | `clientes:view` | Y | Y | Y | Y |
  | `processos:view` | Y | Y | Y | Y |
  | `documentos:view` | Y | Y | Y | Y |
  | `pareceres:view` | Y | Y | Y | Y |
  | `financeiro:view` | Y | Y | Y | **N** |

  This is a more precise and more useful fact than it first looks: **all four roles already hold `view` on all four of the milestone's literal target entities** (clientes/processos/documentos/pareceres) — so a coarse, single-scope authorization gate would not visibly misbehave for any of the *named* entities today. The one real, current role-differential gap in this system is `financeiro:view` (ASSISTENTE lacks it, everyone else has it) — which is exactly why Pitfall 2 below is about how `financeiro`/`Honorario` data gets pulled into a search response **indirectly**, not about the four named entities directly.
- **`pareceres:view` is scope-only, no instance-level restriction.** Verified in `ParecerController.java`: `listSolicitacoes`/`getSolicitacao` are gated only by `@PreAuthorize("hasAuthority('pareceres:view')")` — any caller with the scope sees *every* tenant `ParecerSolicitacao`, not just ones they're assigned to. (Instance-level checks — "apenas o advogado responsável ou ADMIN" — exist only for the *edit*-type actions `entregar`/`createVersao`, never for view/list.) This means, for view purposes, RBAC in this system is purely scope-based per entity type — good news for a search feature's design, since it means "does the caller hold scope X" is a sufficient and complete check for whether entity-type X's rows may appear in search results, with no per-row exception logic to replicate.
- **Two of the four target entities already have a real search/filter precedent to learn from — and to NOT blindly copy:**
  - `ClienteRepository`/`ResourceController.listClientes` (line 170 onward): loads the *entire* tenant's `Cliente` table into memory (`clienteRepository.findByTenantId(tenantId)`), then filters with Java `Stream`s and a `contains()` case-insensitive substring helper. Not SQL-pushed, not ranked, not paginated.
  - `ParecerSolicitacaoRepository.pesquisar()` (native `@Query`, `nativeQuery = true`): the codebase's only existing free-text search that hits SQL directly — `tenant_id = :tenantId` first, `ILIKE '%...%'` on parsed content, every nullable parameter explicitly `CAST(... AS type)` (required because PostgreSQL cannot infer the type of a bare null bind). This is the right template to copy for tenant-scoping *mechanics*, but it still returns an **unranked** list.
  - Neither pattern produces a relevance score. Concatenating outputs of both styles across 4 entity types has no natural combined ordering (see Pitfall 4).
- **No full-text or trigram indexes exist anywhere in the schema.** Confirmed by scanning every file under `backend/migrations/*.sql` for `CREATE INDEX`, `gin_trgm`, `to_tsvector` — zero hits. `ddl-auto: update` (dev) / `validate` (prod) means Hibernate never adds one on its own either. `numero_cliente` and `documento_numero` (NIF-equivalent) each have a real unique index (via `@UniqueConstraint` on `Cliente`), but **`Processo.numeroProcesso` has no uniqueness constraint and no index at all** — it's free-text, user-entered at intake (`CAMPOS_MINIMOS_POR_TIPO` in `ResourceController.java`), not server-generated like `numero_cliente` (`CLI-0001`).
- **There is no existing debounce utility anywhere in `web/src`.** A live/keystroke-driven search UI is new client-side infrastructure, not a reuse of an existing hook.
- **`Command` (cmdk) is already in the design system, and the project already had to work around its default client-side filtering once.** `web/src/components/shared/combobox.tsx:107` explicitly passes `<Command shouldFilter={false}>` to stop cmdk's built-in `command-score` fuzzy filter from re-filtering/re-sorting server-provided options. Given the milestone wants a keyboard shortcut (cmdk-style launcher pattern) and `Command` is the obvious, idiomatic, already-available primitive, this exact gotcha is highly likely to resurface.
- **Zero `@ManyToOne`/`@OneToMany` JPA associations exist across `Cliente`/`Processo`/`Documento`/`Honorario`/`ParecerSolicitacao`.** Every relation is a raw `UUID` FK column, resolved by a second manual `repository.findById()` call in application code. This avoids classic Hibernate lazy-load N+1 surprises, but it means **nothing** stops a naive per-row hydration loop — the codebase already has one live example of exactly that (see Pitfall 3).
- **`Honorario` has a `@UniqueConstraint(columnNames = "processo_id")`** — a strict 1:1 with `Processo` — and the project has already shipped one feature that deliberately joins Cliente+Processo+Honorario for display (`Termo de Honorários imprimível`, v2.9). This is relevant precedent/"muscle memory" for Pitfall 2: a developer building a rich search-result preview for a `Processo` hit has an established, recent example in this exact codebase of "it's natural to pull in the linked Honorario for display."
- **This project has already shipped and fixed the exact bug class Pitfall 1 describes, twice.** Per `.planning/PROJECT.md` Key Decisions (v2.9): `GET /honorarios?processo_id=X` and `GET /documentos?processo_id=X` both accepted a filter parameter that was silently *not* applied in the query, returning tenant-wide data instead of the scoped subset. Root cause noted in the log: "each side looked correct in isolation... only visible when checking the full contract" — the same failure shape (a filter that's defined but not actually wired into the query) is the most likely way a rushed 5-entity search implementation regresses tenant isolation.
- **ASVS Level 1 security enforcement is active for this project** (`.planning/config.json`: `security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: "high"`) — a tenant-isolation or RBAC-bypass finding in this feature is a high-severity blocking issue under this project's own gate, not an informal nice-to-fix.

## Critical Pitfalls

### Pitfall 1: Tenant isolation leak via an entity that has no `tenant_id` column of its own

**What goes wrong:**
A search query hits `t_honorario` (or, more generally, any table whose only tenant linkage is transitive through a parent) directly — e.g. `SELECT * FROM t_honorario WHERE descricao ILIKE '%...%'` — without joining back through `t_processo` to check `tenant_id`. The result: financial records from every tenant in the database, not just the caller's, leak into a search response. This is the single highest-stakes failure mode this platform explicitly exists to prevent (per `CLAUDE.md`: "tenant_id... primary data-isolation boundary").

The same risk exists in a subtler form even for entities that *do* carry their own `tenant_id`: `ProcessoRepository.findByClienteId(UUID clienteId)` (in `backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java`) is an existing, already-merged repository method that takes **no tenant parameter at all**. It is safe today only because every current call site happens to additionally filter by a tenant-checked `clienteId`. A search implementation that reaches for this method because "it's already there, it must be safe" inherits an IDOR-adjacent hole the moment it's called with an ID that wasn't independently tenant-verified first.

**Why it happens:**
Cross-entity search is fundamentally "one new query per entity type, written quickly, five times in a row." The existing single-entity endpoints in `ResourceController` get tenant scoping right because each one has had years of individual review and, in `Honorario`'s case, a repeated, explicit "re-fetch Processo, check `tenantId.equals(...)`" idiom copy-pasted at every call site (`getHonorario`, `updateHonorario`, `deleteHonorario`, `listHonorarioPagamentos`, `createPagamento`...). A search feature is exactly the kind of change most likely to add a "sixth" ad hoc path to `t_honorario` that forgets to repeat that idiom, especially under the time pressure of "just add one more branch to the merge." The project's own history confirms this exact shape of bug already happened twice with simpler single-entity filters (`GET /honorarios?processo_id=X`, `GET /documentos?processo_id=X` silently ignoring their filter parameter) — a 5-way merge is strictly more surface area for the same mistake.

**How to avoid:**
- For every entity type folded into search, the query's `WHERE` clause must either (a) start from a column that is `tenant_id` on that exact table (`Cliente`, `Processo`, `Documento`, `ParecerSolicitacao`/`ParecerVersao` all qualify directly), or (b) for `Honorario` specifically, go through a real SQL `JOIN t_processo p ON h.processo_id = p.id AND p.tenant_id = :tenantId`, or reuse the already-existing batch method `HonorarioRepository.findByProcessoIdIn(Collection<UUID>)` against a pre-computed, pre-tenant-checked set of the caller's own `Processo` IDs. Never scan `t_honorario` keyed by text/ID match alone.
- Audit every repository method the new search code calls for a `tenantId` parameter in its signature. If a method like `findByClienteId` lacks one, either add a tenant-scoped overload or wrap the call so the returned rows are still checked (`.filter(x -> x.getTenantId().equals(tenantId))`), matching the project's own established "double verification" convention already used for `Decisao`/`Facto`/`Testemunha` (tenant of the parent `Processo`, checked in the controller, per `PROJECT.md` Key Decisions).
- Write one query builder/repository method per entity type, each independently unit-testable, rather than one giant hand-assembled `UNION`/string-concatenated query — makes the tenant predicate reviewable per entity type instead of buried in one large query.

**Warning signs:**
- Any new repository method or native `@Query` whose `WHERE` clause doesn't have a `tenant_id = :tenantId` predicate as its first condition (or, for `Honorario`, a `JOIN` that supplies it).
- A code review comment or PR that touches `t_honorario`/`HonorarioRepository` without also touching `ProcessoRepository`/a tenant check in the same diff.
- A search result for one tenant that includes a `Processo`/`Cliente` display name the current tenant's users don't recognize (manual smoke-test signal).

**How to detect (verification, not just review):**
Mirror the project's own existing pattern — `NotificacaoRepositoryIT` (Testcontainers + real PostgreSQL, added in Phase 91) is the established precedent for exactly this kind of guarantee. Add an analogous integration test that seeds **two tenants**, each with a `Cliente` + `Processo` + `Documento` + `ParecerSolicitacao` (+ `Honorario` if in scope) all containing the *same* distinctive search token, calls the new search endpoint/repository method as Tenant A, and asserts zero Tenant B rows come back — repeated for every entity type, `Honorario` included even if it's only reachable via hydration rather than direct search.

**Phase to address:**
Data/query layer — this must be the first slice of work built and independently tested per entity type, before any RBAC branching (Pitfall 2) or hydration (Pitfall 3) is layered on top. Nothing downstream can be trusted if this layer is wrong.

---

### Pitfall 2: A single coarse authorization gate lets `financeiro` data leak into search results that a role without `financeiro:view` should never see — even though none of the four named target entities are individually role-gated differently today

**What goes wrong:**
Because this is *one* new endpoint (something like `GET /api/v1/pesquisa?q=...`) that internally queries several repositories, the natural move — pattern-matched from every other endpoint in `ResourceController`, which is always one method statement, one `@PreAuthorize` — is to put a single scope check on the whole handler (e.g. `@PreAuthorize("hasAuthority('clientes:view')")` or just `isAuthenticated()`). `@PreAuthorize` is designed to gate a whole method, not to conditionally include/exclude branches inside it. As verified above, all four roles already hold `view` on `clientes`/`processos`/`documentos`/`pareceres`, so a coarse gate would not visibly misbehave for those four alone — the real exposure is `financeiro` (`Honorario`), which **only ASSISTENTE lacks**, arriving in the response through a path other than "financeiro was one of the four literal target entities":
1. **Hydration/breadcrumb enrichment.** `Honorario` has a strict 1:1 FK to `Processo` (`@UniqueConstraint(columnNames = "processo_id")`), and this project has already shipped a feature that deliberately joins Cliente+Processo+Honorario for one screen (`Termo de Honorários imprimível`, v2.9). It is a very natural next step, while building a rich search-result card for a `Processo` hit ("show a helpful preview"), to also pull in `Honorario.valorTotal`/status — silently exposing financial data to ASSISTENTE inside what looks like an ordinary `processos:view`-gated result.
2. **Scope creep during implementation.** "Users will also want to search by fee amount/description" is a plausible, reasonable-sounding extension that could add `Honorario` as a fifth searchable entity type after the four named ones are done — at which point it inherits both this pitfall and Pitfall 1 simultaneously (no own `tenant_id` *and* a role that must never see it).
3. **Global search treated as a "lighter" surface than its dedicated page.** A generic risk independent of this codebase: teams sometimes reason "it's just a search preview, not the real financeiro page" and skip re-applying the same scope check the dedicated `/financeiro` page enforces — the check must be identical, not relaxed, because the data is identical.

**Why it happens:**
Every other endpoint in this codebase is single-entity with a single `@PreAuthorize` — there is zero existing precedent in `ResourceController` for "one endpoint, N independently-gated data sources," so a developer has no in-codebase example to pattern-match against for the *correct* shape and will reach for the familiar single-gate idiom instead.

**How to avoid:**
- Keep the class/method-level `@PreAuthorize` on the search endpoint minimal — authenticate the caller and resolve their tenant, nothing more. Do **not** require any specific business scope just to call the endpoint at all (a caller with only `clientes:view`, hypothetically, should still get a 200 with clientes-only results, not a 403).
- Inside the handler, branch per entity type on the caller's actual authorities, mirroring the frontend's `hasScopedPermission` fallback chain (`web/src/lib/permissions.ts`) but server-side — e.g. only call the `Cliente` search branch if the caller holds `clientes:view`, only call the `Honorario` branch (if ever added) if the caller holds `financeiro:view`. Keep this entity-type → required-scope table in exactly one place so frontend and backend can't drift, honoring the project's own stated rule that "both layers must agree" (`CLAUDE.md`).
- Treat the search-result hydration/preview payload for a `Processo` hit as strictly `processos:view`-scoped data only — never opportunistically embed `Honorario`/`Pagamento` fields "since the join is already there for the breadcrumb." If a rich preview genuinely needs financial context, gate that specific field's presence in the response on the caller separately holding `financeiro:view`, not on the presence of the parent `Processo` result.
- If `Honorario` is ever added as a directly searchable entity, its query must satisfy Pitfall 1's join requirement *and* be branch-gated on `financeiro:view` — both, not either.

**Warning signs:**
- A single `@PreAuthorize` annotation above a handler that internally calls more than one repository for more than one entity type.
- A `Processo` search-result DTO/response shape that contains any field sourced from `t_honorario`/`t_pagamento`.
- Manual testing only ever done as ADMIN (who holds every scope, including `financeiro:view`) — the gap is invisible unless tested as ASSISTENTE specifically.

**How to detect (verification, not just review):**
A per-role test matrix (ADMIN / ADVOGADO / TECNICO / ASSISTENTE) against a search term chosen to match a `Cliente`, `Processo`, `Documento`, and `ParecerSolicitacao` simultaneously, with that `Processo` having a linked `Honorario` containing the same term in its `descricao`. Assert ASSISTENTE's response contains the clientes/processos/documentos/pareceres hits but has zero fields or entries sourced from `Honorario`, while ADVOGADO/TECNICO/ADMIN's responses may.

**Phase to address:**
API layer (search orchestration/controller) — depends on Pitfall 1's tenant-scoped queries per entity type being ready, and must be finalized before the UI layer starts consuming and rendering the response shape (the UI can't correctly hide a "Financeiro" section it was never told to expect).

---

### Pitfall 3: N+1 query explosion when hydrating cross-entity breadcrumbs/labels for merged results

**What goes wrong:**
Every merged search result needs enough context to be useful — a `Processo` hit needs its `Cliente.nome`, a `Documento` hit needs its `Cliente.nome` and/or `Processo.numeroProcesso`, a `ParecerSolicitacao` hit needs its `Cliente.nome`. A naive implementation resolves this per row, inside a loop: for each of N results, call `clienteRepository.findById(x.getClienteId())` (or `processoRepository.findById(...)`) individually. With five entity types each contributing several rows, this turns "search across 4-5 tables" into the base queries *plus* up to `resultsPerType × entityTypes` additional point lookups on every single keystroke-triggered request.

This isn't hypothetical for this codebase — it's a bug the project already shipped and only partially fixed. `ResourceController.listHonorarios()`'s no-filter branch (line ~2911-2919) still does, today:
```java
for (Processo p : tenantProcs) {
    response.addAll(honorarioRepository.findByProcessoId(p.getId()));
}
```
one query for the tenant's processos, plus one query *per process* — even though `HonorarioRepository.findByProcessoIdIn(Collection<UUID>)` already exists specifically to fix this exact pattern (its own code comment cites it was added to avoid "looping `findByProcessoId` once per processo (N+1 anti-pattern...)" for the daily alerts job). The batch-fetch fix landed in one call site and was never back-ported to this one — proof that the correct pattern being known elsewhere in the codebase does not automatically prevent the naive version from being written again in new code.

**Why it happens:**
None of `Cliente`/`Processo`/`Documento`/`Honorario`/`ParecerSolicitacao` have any `@ManyToOne`/`@OneToMany` JPA association — every relation is a bare `UUID` FK column. This is good for avoiding classic Hibernate lazy-proxy N+1 surprises, but it also means **nothing** structural stops a developer writing a per-row `findById()` loop; 100% of the batching responsibility sits on whoever writes the aggregation code, with no framework guardrail.

**How to avoid:**
Batch the entire hydration pass in one shot, after all five raw match lists are collected:
1. Walk all five result lists once, collecting distinct `clienteId`s and `processoId`s referenced by *any* of them into two `Set<UUID>`.
2. Issue exactly one `clienteRepository.findAllById(clienteIds)` and one `processoRepository.findAllById(processoIds)` (both inherited for free from `JpaRepository`, no new method needed).
3. Build two `Map<UUID, Cliente>` / `Map<UUID, Processo>` lookups from those two result sets.
4. Hydrate every result row from the maps — zero additional queries per row.

This is the same shape of fix the project's own Phase 104 UI audit already applied when it found Documentos showing raw UUIDs instead of resolved names ("lookup Map" pattern) — reuse that precedent explicitly rather than rediscovering it.

**Warning signs:**
- Any `for` loop or `.stream().map()`/`.forEach()` over a result list that calls a `*Repository.findById(...)` (or any repository method) inside the loop body.
- `grep -rn "findByProcessoId(p.getId())"` or similar patterns inside a `for` — this exact signature already exists once in `ResourceController.listHonorarios`; treat it as a known-bad idiom to actively search for and avoid reproducing, not just something to notice by accident.
- Backend request logs / SQL logs showing query counts that scale with result count rather than staying constant.

**How to detect (verification, not just review):**
A SQL-log or Hibernate-statistics assertion in a test (query count for a multi-row, multi-entity-type result set should be a small constant, not proportional to row count), or a deliberate manual check during code review of the SQL log output for one representative search request.

**Phase to address:**
API/data-layer boundary — a dedicated result-aggregation step, built immediately after Pitfall 1's per-entity tenant-scoped queries land and before the response reaches the UI layer.

---

### Pitfall 4: Naive substring search buries exact structured-ID matches, and cmdk's default client-side filter can silently undo whatever ranking the backend computes

**What goes wrong, backend half:**
Lawyers and administrative staff in this domain search by structured identifiers as often as free-text names — `numero_cliente` (`CLI-0001`), `numero_processo` (free text, e.g. a court reference), NIF/`documento_numero` (9 digits). As established in the baseline: `numero_cliente` and `documento_numero` have real unique indexes; `numero_processo` has neither a uniqueness constraint nor an index and is free-text at intake; nothing in the schema has a GIN/trigram index for fuzzy text search. If cross-entity search simply concatenates each entity type's filtered list in table/branch order (Clientes, then Processos, then Documentos, then Pareceres) with no explicit ranking, a user who types a complete, unambiguous NIF or `numero_cliente` to jump straight to one record will see that exact hit buried underneath every incidental substring match from the other entity types (e.g., a `Processo.descricao` that happens to contain the same digit sequence). Neither existing search precedent in this codebase (`listClientes`'s in-memory `contains()` filter, `ParecerSolicitacaoRepository.pesquisar()`'s `ILIKE`) produces a relevance score today — both return unordered filtered lists, and naively unioning several unordered lists produces no meaningful combined order at all.

**What goes wrong, frontend half:**
`cmdk`'s `Command` primitive filters and sorts its children client-side **by default** (`shouldFilter` prop, default `true`), using the `command-score` fuzzy-matching algorithm; a score of `0` **hides the item entirely**, not just deprioritizes it (confirmed against upstream cmdk documentation). This project's `web/src/components/shared/combobox.tsx` already had to explicitly pass `shouldFilter={false}` to stop this from re-filtering server-provided options. Since `Command` is the obvious, already-available primitive for a keyboard-shortcut-triggered search launcher (which this milestone explicitly wants), and the flag is easy to forget on a fresh component that wasn't built by copying `combobox.tsx` line-for-line, the likely failure mode is: the backend computes a correct exact-match-first ranking, ships it to the client, and cmdk's own fuzzy scorer re-sorts (or worse, hides, if the exact ID string scores 0 against its own algorithm) results independently of what the API actually returned — making the bug invisible in the network tab (the API response looks right) and only visible in the rendered UI.

**Why it happens:**
`ILIKE '%term%'` substring search is the path of least resistance (it's literally what `ParecerSolicitacaoRepository.pesquisar()` already does, an easy copy-paste template), and "concatenate the five lists" is the obvious naive merge strategy. The cmdk gotcha is subtle because it only manifests when `Command` is reused for a *new* component rather than by extending the one file that already has the workaround — nobody re-derives a library default they haven't personally hit before.

**How to avoid:**
- Define an explicit tiered ranking, computed server-side or in one well-defined merge step, never left to entity/table iteration order:
  1. Exact match on a structured-ID field (`numero_cliente`, `documento_numero`/NIF, `numero_processo`) — ranks first, regardless of which entity type it came from.
  2. Prefix match on the same fields — next.
  3. `ILIKE`/substring match on free-text fields (`nome`, `descricao`, `conteudo`) — last, with a stable secondary sort (e.g. `createdAt desc`) so results don't reorder unpredictably between requests.
- If `Command` (cmdk) is used for the results UI, pass `shouldFilter={false}` and feed it a stable `value`/key per item — copy `combobox.tsx`'s pattern deliberately rather than reimplementing from scratch.
- Treat `numero_processo`'s lack of an index as a known performance ceiling to flag, not silently accept: an "exact match" branch against it is still a full scan today; if search volume/table size justifies it, a follow-up migration adding a plain B-tree index on `numero_processo` (and, if substring/fuzzy performance becomes a real problem on `nome`/`descricao` fields, a `pg_trgm` GIN index) is a reasonable, separately-scoped follow-up — don't block the first ship on it, but don't assume the exact-match tier is free either.
- Avoid concatenating multiple searchable columns into one "blob" and running a single `ILIKE` against it — search each structured field precisely and independently per entity type, otherwise a numeric NIF fragment can accidentally substring-match an unrelated numeric value in a different field of a different entity.

**Warning signs:**
- Typing a complete, unambiguous NIF/`numero_cliente`/`numero_processo` into the search box and *not* seeing that record as the first result.
- Search response ordering that visibly matches the entity-type iteration order in the controller code (i.e., always all Clientes, then all Processos, etc.) rather than relevance.
- QA observing the rendered results reorder or drop entries as the user types, in a way the Network tab's raw API response doesn't explain.

**Phase to address:**
API layer (merge/ranking logic) for the tiering strategy — must be designed before the UI layer consumes the response shape. UI layer for the `shouldFilter={false}` flag — must be caught in the frontend implementation and its code review specifically, since it's a one-line omission with no compiler/type-level signal.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|-----------------|------------------|
| Ship search as 4-5 client-side calls to the *existing* per-entity list endpoints (`GET /clientes`, `GET /processos`, `GET /documentos`, `GET /pareceres/pesquisa`), merged in the browser, instead of one new backend endpoint | Zero new backend code; reuses endpoints whose tenant/RBAC scoping is already proven | No cross-entity relevance ranking possible without shipping full unpaged result sets to the browser first (defeats server-side `LIMIT`); 4-5x round-trips per keystroke; `listClientes`'s existing "load whole tenant table, filter in memory" cost is paid in full on every keystroke; structured-ID ranking (Pitfall 4) becomes materially harder to get right client-side | Only as a disposable UX prototype/spike, never shipped — `PROJECT.md` itself already states this milestone "vai precisar de trabalho novo no backend, não só frontend" |
| Copy `ParecerSolicitacaoRepository.pesquisar()`'s native-SQL-with-`CAST` template verbatim, 3-4 more times (one per entity), instead of one shared query-builder abstraction | Fast to write; proven-safe template (tenant-first `WHERE`, `CAST`-wrapped nullable params) | 4-5 near-duplicate native queries to keep in sync if the ranking/tiering strategy changes later; a fix to one has to be manually re-verified against all the others | Acceptable for a first ship, provided each copy is independently tenant-scoped and independently integration-tested — leave an explicit code comment flagging the duplication for future consolidation |
| Reuse the `ParecerPesquisaController`-style "separate top-level controller class" pattern without re-verifying the new route doesn't collide with `ResourceController`'s existing `/api/v1` mappings | Reuses a pattern this project already has a known-good fix for | This project already shipped `pesquisar()` as unreachable at its documented path for an entire milestone (v2.5→v2.6) because Spring concatenates class-level and method-level `@RequestMapping` regardless of a leading `/` — the same routing mistake is trivial to reintroduce in a new controller class | Never skip the actual HTTP smoke test — a route that "looks right" in code review is exactly what failed silently here once already |

## Cross-Entity Integration Gotchas

This feature has no external service dependency, but "integrating" five internal repositories into one response has the same category of gotchas as a multi-service integration:

| Integration point | Common mistake | Correct approach |
|--------------------|-----------------|-------------------|
| `Honorario` ↔ `Processo` (no own `tenant_id`) | Querying `t_honorario` directly by text/ID match | Join through `t_processo` for `tenant_id`, or use the existing `findByProcessoIdIn` batch method against a pre-tenant-checked ID set |
| `Cliente`/`Processo`/`Documento`/`ParecerSolicitacao` labels used for cross-entity breadcrumbs | Per-row `findById()` calls while building result previews | Collect all referenced IDs once, batch-fetch with `findAllById`, hydrate from an in-memory `Map` |
| Frontend `Command` (cmdk) primitive rendering server-ranked results | Omitting `shouldFilter={false}`, letting cmdk's own fuzzy scorer re-filter/re-sort | Always set `shouldFilter={false}` when displaying server-computed results, exactly as `combobox.tsx` already does |
| RBAC scope table (frontend `permissions.ts` `KNOWN_SCOPES`/`hasScopedPermission` vs. backend `@PreAuthorize`) | Defining the entity-type → required-scope mapping twice, independently, in frontend and backend code, and letting them drift | Treat the mapping as one contract; when adding `pesquisa` as a new scope-checked surface, update both `KNOWN_SCOPES`-adjacent logic and the backend branch-gating in the same change, per `CLAUDE.md`'s "both layers must agree" rule |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| Firing a full 4-5-entity search request on every keystroke with no debounce | Backend logs show a full fan-out per character typed; UI feels laggy/flickery | Debounce input (~250-350ms) before triggering the query; there is no existing debounce hook in this codebase to reuse, so it must be built or a small dependency added | Noticeable from the very first user, since there's no existing throttling anywhere in the app to inherit |
| Reusing `listClientes`'s "load entire tenant table into memory, then Java-stream-filter" style for every entity type in search | Search latency scales with total row count per tenant, not match count | Push filtering into the SQL `WHERE` clause (tenant-first, indexed/`ILIKE`), matching `ParecerSolicitacaoRepository.pesquisar()`'s approach, not `findByTenantId()` + `.stream().filter()` | Breaks down once a tenant's `clientes`/`processos`/`documentos` tables reach a few thousand rows — search is precisely the feature most likely to be run against the largest tables in the system, so this trap bites earlier than most |
| No cap/`LIMIT` per entity type | A short 2-3 character query (common mid-typing) returns hundreds of rows per entity type before the user finishes typing | Cap each entity-type branch (e.g. top 5-10) for the live/dropdown UX; offer a separate "ver todos os resultados" full view only on explicit submit, not on every keystroke | Immediate — even at today's modest data volume, an unbounded 5-way fan-out per 2-character keystroke is wasteful |
| Coupling search state to the persistent `dashboard-shell.tsx` (mounted on every dashboard route) | Careless implementation causes results/query state to leak across route navigations, or triggers refetches on unrelated shell re-renders | Keep search state local to a dedicated `GlobalSearch`/command-palette component that the shell only renders; fetch on explicit trigger (palette open + debounced typing), never on shell re-render | Visible as soon as the input is wired to real state — should be caught in first code review |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| One coarse `@PreAuthorize` on the whole `/pesquisa` endpoint instead of per-entity-type branching | `Honorario` data (or any future higher-scope entity) reaches a caller lacking `financeiro:view` via hydration/enrichment or later scope-creep, even though the four literally-named entities alone wouldn't currently expose the gap | Baseline `@PreAuthorize` only asserts authentication; branch each entity-type query on the caller's actual authorities inside the handler |
| Querying `t_honorario` (or any table lacking its own `tenant_id`) directly instead of joining through its tenant-owning parent | Cross-tenant financial data leak — the platform's single highest-severity guarantee, broken | Always join `Honorario` → `Processo` → `tenant_id`, or reuse `findByProcessoIdIn` against a pre-scoped ID set |
| Trusting an existing repository method name (e.g. `findByClienteId`) as "already safe" purely because it already exists in the codebase | IDOR-adjacent leak: a caller who can reference another tenant's ID gets that tenant's rows, since the method itself performs no tenant filtering | Audit every repository method the new search code calls for a `tenantId` parameter; if absent, wrap with an explicit tenant check, per the project's own `Decisao`/`Facto`/`Testemunha` double-verification convention |
| Letting a `Processo` search-result preview opportunistically embed `Honorario`/`Pagamento` fields "since the join is already there" | Field-level leak even when entity-level RBAC is technically correctly gated elsewhere | Keep response payloads per entity type minimal (id, label, breadcrumb) and gate any financial field's *presence* on the caller separately holding `financeiro:view`, never on the presence of the parent `Processo` result alone |
| Treating global search as a "lighter" surface than each entity's dedicated page, and applying a looser check than that page uses | A generic anti-pattern independent of this codebase: search bypasses the same granular RBAC its "real" page enforces, because it doesn't feel like a full access surface | Whatever scope gates entity X's own dedicated endpoint today (`clientes:view`, `processos:view`, `documentos:view`, `pareceres:view`) must gate that entity type's presence in search identically — no separate, laxer rule for search |
| Assuming `Documento.confidencialidade` provides row-level access control for search | It is a display-only label today (defaults `"PUBLICO"`, never read as a filter anywhere in `ResourceController`) — if search implicitly assumes confidential documents are hidden, that assumption isn't backed by any existing enforcement anywhere else in the app either | If confidentiality-based filtering is genuinely wanted, it must be built as new logic and applied consistently everywhere `Documento` is exposed, not assumed to already exist just for search |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-------------------|
| Flat, unranked list-of-5-lists results | A user typing an exact `numero_cliente`/NIF/`numero_processo` scrolls past irrelevant fuzzy matches from other entity types to find a record they already know the ID of | Group by entity type with clear headings, and promote exact/prefix structured-ID matches to a distinct "top result" slot above the grouped sections |
| Rendering an empty entity-type section for a scope the caller doesn't hold (e.g. an empty "Financeiro" heading with "0 resultados" for ASSISTENTE) | Implies the app is broken/missing data, or worse, confirms to the user that a "Financeiro" section conceptually exists for a search they ran, inviting probing | Omit entity-type sections entirely for scopes the caller lacks — mirrors how the rest of the app already hides whole nav items/tabs rather than showing them disabled/empty |
| Transient over-broad render before permissions resolve | This exact race (`!permissions.isLoading` vs. `permissions.isFetched`) has already bitten this codebase repeatedly — Phase 103 Dashboard, Phase 105's ten Clientes+Processos guards, the standalone `notificacoes/page.tsx` fix found in the v2.13 milestone audit. In a search context it would mean a Financeiro-shaped result group flashes before permissions finish loading, then disappears | Gate entity-type-section rendering on `permissions.isFetched`, never `!permissions.isLoading` — grep the new component against this known-bad idiom before shipping, it has recurred in every module built so far |
| New global keyboard shortcut colliding with browser defaults or an existing in-app handler | A shortcut that fires unexpectedly while focus is inside an unrelated form field, or conflicts with a browser-reserved combination | Check `dashboard-shell.tsx`/layout for any existing global key handler before binding a new one; verify the shortcut doesn't fire while focus is inside a form input elsewhere in the app |

## "Looks Done But Isn't" Checklist

- [ ] **Tenant isolation:** Often "looks correct" because the first-checked entity (e.g. Cliente, which has its own `tenant_id`) is properly scoped — verify *every* entity branch independently, especially `Honorario` (no own `tenant_id`) and any reused repository method lacking a `tenantId` parameter (e.g. `findByClienteId`).
- [ ] **RBAC per entity type:** Often verified only against ADMIN (who holds every scope, including `financeiro:view`) — explicitly verify as ASSISTENTE and confirm zero `Honorario`-sourced data anywhere in the response, not just that a UI section is visually hidden.
- [ ] **Result hydration labels:** Often shows raw UUIDs instead of resolved names under time pressure — this exact bug already shipped once in Documentos (Phase 104 UI audit) — verify every result row shows a human-readable label/breadcrumb.
- [ ] **Structured-ID exact match:** Often tested only against fuzzy name search, never against a complete, exact NIF/`numero_cliente`/`numero_processo` — explicitly test pasting a full ID and confirm it is the top result, not buried under substring matches from other entity types.
- [ ] **cmdk filter flag:** Easy to forget if `Command` is reused for the results UI without copying `combobox.tsx` directly — grep the new component for `shouldFilter={false}`.
- [ ] **Empty/partial/error states per entity type:** Often only the "happy path with hits in all entity types" gets tested — verify a query matching only 1-2 types, and a query matching zero (a real empty state, not a stuck spinner).
- [ ] **Debounce and stale-response handling:** Often missing entirely on first pass — verify rapid typing doesn't fire a full fan-out per keystroke, and that a slower, older in-flight response can't overwrite a newer one that already returned (out-of-order response race, e.g. via TanStack Query's built-in request de-duplication by query key rather than manual `AbortController` bookkeeping).

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|----------------|------------------|
| Cross-tenant leak shipped to production | HIGH | Disable the search endpoint immediately (feature flag or route removal); audit access/`AuditLog` records for evidence of prior exposure; add the missing tenant predicate; add the Testcontainers IT regression test for that exact entity type before re-enabling; document the fix and root cause in `PROJECT.md` Key Decisions, matching how the `GET /honorarios?processo_id=X`/`GET /documentos?processo_id=X` tenant-filter bugs were handled |
| RBAC bypass (`financeiro` data visible to ASSISTENTE) shipped | HIGH | Same disable-first response; because the leaked data is financial, treat it as a confidentiality incident rather than an ordinary bug — audit who actually issued matching search queries before silently patching the code |
| N+1 performance regression discovered post-ship | MEDIUM | Reuse the fix pattern already proven in this codebase: add/extend a `findAllById`/`findByXIn` batch method (mirrors `HonorarioRepository.findByProcessoIdIn`, added for exactly this reason) and replace the loop with one batched call plus in-memory `Map` hydration |
| Ranking feels wrong post-ship (exact ID matches buried) | LOW | Purely additive fix — introduce the tiered ranking (exact > prefix > substring) in the merge layer without touching the underlying per-entity queries; no data-safety implications, safe to iterate quickly |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Layer | Verification |
|---------|-------------------|----------------|
| Tenant isolation leak (`Honorario` / any transitively-scoped entity, or an un-tenant-scoped repository method reused for search) | **Data/query layer** — one tenant-scoped repository query per entity type, built and reviewed first | Testcontainers IT test (mirrors `NotificacaoRepositoryIT`) seeding 2 tenants with matching-text rows in every entity type including `Honorario`; assert zero cross-tenant rows returned per entity type |
| RBAC bypass via merged/hydrated response (`financeiro` reaching ASSISTENTE) | **API layer** — search orchestration/controller, branch-gated per entity type on caller authorities | Per-role test matrix (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) against a query matching all entity types plus a linked `Honorario`; assert ASSISTENTE's response has zero `Honorario`-sourced fields or entries |
| N+1 hydration of cross-entity breadcrumbs | **API/data-layer boundary** — a dedicated batch-hydration step, immediately after per-entity queries land | Query-count assertion (constant, not proportional to result count) or manual SQL-log inspection during code review of a representative multi-type result set |
| Structured-ID ranking buried / cmdk re-filtering | **API layer** for the tiering strategy (exact > prefix > substring); **UI layer** for `shouldFilter={false}` | Manual test: paste an exact NIF/`numero_cliente`/`numero_processo`, confirm it's the top result; code review checklist item + grep for `shouldFilter={false}` in the new results component |

## Sources

**Direct codebase inspection (HIGH confidence):**
- `backend/src/main/java/com/lexcv/models/{Cliente,Processo,Documento,Honorario,ParecerSolicitacao,ParecerVersao}.java`
- `backend/src/main/java/com/lexcv/repositories/{ClienteRepository,ProcessoRepository,DocumentoRepository,HonorarioRepository,ParecerSolicitacaoRepository,NotificacaoRepository}.java`
- `backend/src/main/java/com/lexcv/controllers/{ResourceController,ParecerPesquisaController,ParecerController}.java`
- `backend/src/main/java/com/lexcv/config/{SecurityConfig,UserPrincipal,JwtAuthenticationFilter}.java`
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (RBAC matrix, lines 293-353)
- `backend/migrations/*.sql` (confirmed absence of any full-text/trigram index across the whole schema)
- `backend/src/main/resources/{application.yml,application-prod.yml}` (`ddl-auto` settings)
- `web/src/components/shared/{combobox.tsx,dashboard-shell.tsx}`, `web/src/components/ui/command.tsx`
- `web/src/lib/{api.ts,permissions.ts}`
- `web/src/hooks/{use-pareceres.ts,use-me.ts}`
- `.planning/PROJECT.md` (Key Decisions log: v2.9 `GET /honorarios?processo_id=X`/`GET /documentos?processo_id=X` silently-ignored-filter bug; v2.9 `Decisao`/`Facto`/`Testemunha` double-verification pattern; v2.9 `Termo de Honorários` Cliente+Processo+Honorario join precedent; Phase 103/105/v2.13-audit `permissions.isFetched` race recurrences; Phase 104 raw-UUID hydration bug and its "lookup Map" fix; v2.5→v2.6 `ParecerPesquisaController` routing bug)
- `.planning/config.json` (ASVS Level 1 `security_enforcement`, `security_block_on: "high"`)

**External verification (HIGH confidence, cross-checked against the project's own independently-discovered workaround):**
- [cmdk npm package](https://www.npmjs.com/package/cmdk) and [cmdk GitHub source](https://github.com/pacocoursey/cmdk/blob/main/cmdk/src/index.tsx) — confirms `shouldFilter` defaults to `true`, the built-in filter uses the `command-score` algorithm, and a score of `0` hides an item entirely (not just deprioritizes it).

---
*Pitfalls research for: cross-entity global search in a multi-tenant, RBAC'd legal practice platform*
*Researched: 2026-07-18*
