# Architecture Research: Cross-Entity Global Search

**Domain:** Feature integration into an existing multi-tenant Spring Boot 3.4.1 / Java 23 + Next.js 16 legal-practice platform (LexCV, v2.14 milestone)
**Researched:** 2026-07-18
**Confidence:** HIGH — every claim below is grounded in direct reads of the current `backend/` and `web/` source tree (files enumerated in Sources), cross-checked against 2 external validations (Microsoft Graph Search API authorization model, shadcn `CommandDialog` Cmd+K convention).

## Scope Note

This document answers exactly the three integration questions posed for target feature 1 ("Pesquisa global funcional cross-entity"): (a) one backend endpoint vs. N client-side calls, (b) RBAC-safe per-entity-type filtering within a single search response, (c) where the endpoint lives in the existing controller structure. Items 2–5 of the v2.14 milestone (estado filter, icon buttons, `--radius`, icon-only filter buttons) are pure shadcn/ui UI work with no new architecture and are intentionally out of scope here, per the orchestrator's framing.

## Standard Architecture

### System Overview

```
┌───────────────────────────────────────────────────────────────────────────────┐
│  web/ (Next.js 16, dashboard app)                                              │
│                                                                                  │
│  dashboard-shell.tsx (topbar)                                                  │
│    └─ [MODIFIED] decorative <Input> (line 121-127) replaced by:                │
│       GlobalSearchDialog  ── self-contained, mounted once ──┐                  │
│         ├─ owns open-state + Cmd/Ctrl+K keydown listener    │ (same shape as   │
│         ├─ CommandDialog / CommandInput / CommandGroup      │  NotificationBell│
│         │  (shadcn primitives — already installed)          │  precedent)      │
│         └─ useGlobalSearch(debouncedQ) ──► apiFetch ─────────┘                 │
│                        │ GET /api/v1/search?q=...                              │
├────────────────────────┼────────────────────────────────────────────────────────┤
│  backend/ (Spring Boot) │  JwtAuthenticationFilter → SecurityContext            │
│                        ▼   (UserPrincipal: tenantId + authorities)              │
│  ┌──────────────────────────────────────────────────────────────────────────┐  │
│  │ SearchController  [NEW]  @RequestMapping("/api/v1/search")               │  │
│  │ @PreAuthorize("hasAnyAuthority('clientes:view','processos:view',        │  │
│  │                                 'documentos:view','pareceres:view')")    │  │
│  │                                                                            │  │
│  │  UUID tenantId = getTenantId();       (existing per-controller pattern)  │  │
│  │  for each of 4 categories:                                               │  │
│  │    if (hasAuthority(auth, "<scope>:view"))  ──┐                          │  │
│  │        results.addAll(searchX(tenantId, q))   │  branch SKIPPED (never   │  │
│  │                                                 │  queried) when scope    │  │
│  │                                                 │  is absent              │  │
│  └────────┬───────────────┬───────────────┬───────────────┬─────────────────┘  │
│           ▼               ▼               ▼               ▼                    │
│  ClienteRepository ProcessoRepository DocumentoRepository ParecerSolicitacao-   │
│  .findByTenantId   .findByTenantId    .findByTenantId     Repository           │
│  (existing)        (existing)         (existing)          .findByTenantId      │
│                                                             (existing)          │
│  → merged into a single List<SearchResultDto> (tipo-discriminated, capped      │
│    per category), same shape family as TimelineItemDto (getTimeline, L2273)    │
└───────────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Typical Implementation |
|-----------|-----------------|-------------------------|
| `SearchController` (new) | Owns `GET /api/v1/search`; resolves tenant + authorities once; branches per entity type; merges results | New `@RestController`, `@RequiredArgsConstructor`, injects the 4 existing repositories — no new repository classes needed |
| `SearchResultDto` (new) | Unified shape for a search hit across all 4 entity types | `record` in `com.lexcv.dtos`, mirrors `TimelineItemDto`'s discriminated-union shape |
| `ClienteRepository` / `ProcessoRepository` / `DocumentoRepository` / `ParecerSolicitacaoRepository` (existing, unmodified) | Tenant-scoped data access | Already expose `findByTenantId(UUID)`, reused as-is |
| `UserPrincipal` / `Authentication` (existing, unmodified) | Carries `tenantId` + `authorities` (the `scope:action` `SimpleGrantedAuthority` set) into the request | Populated by `JwtAuthenticationFilter`/`JwtTokenProvider` at login; read via `SecurityContextHolder` exactly as every other controller does |
| `GlobalSearchDialog` (new) | Self-contained UI: Cmd/Ctrl+K listener, trigger button, `CommandDialog` overlay, grouped result rendering, click-to-navigate | `web/src/components/shared/global-search-dialog.tsx`, mounted once from `dashboard-shell.tsx`, same "own its state" shape as `NotificationBell` |
| `useGlobalSearch` (new hook) | TanStack Query wrapper for `GET /api/v1/search`, debounced | `web/src/hooks/use-global-search.ts`, same shape as `usePesquisarPareceres` |
| `dashboard-shell.tsx` (modified) | Hosts the search trigger in the topbar in place of the decorative `<Input>` | Lines 121-127 replaced with `<GlobalSearchDialog />` |

## Recommended Project Structure

```
backend/src/main/java/com/lexcv/
├── controllers/
│   └── SearchController.java          # NEW — dedicated, ~80-120 lines
├── dtos/
│   └── SearchResultDto.java           # NEW — record, mirrors TimelineItemDto
└── (no repository or model changes — all 4 repositories already exist)

web/src/
├── components/shared/
│   ├── dashboard-shell.tsx            # MODIFIED — swap decorative Input for trigger
│   └── global-search-dialog.tsx       # NEW — CommandDialog + Cmd/Ctrl+K listener
├── hooks/
│   └── use-global-search.ts           # NEW — TanStack Query, debounced
├── lib/
│   └── use-debounced-value.ts         # NEW — small hook, ~10 lines, zero new deps
└── types/
    └── search.ts                      # NEW — SearchResult, SearchTipo types
```

### Structure Rationale

- **`SearchController.java` as its own file**, not a method added to `ResourceController` (3,296 lines as of this milestone — see Anti-Patterns) or to any single-entity controller. Matches the precedent already set twice: `ParecerController`/`ParecerPesquisaController` split (v2.5/v2.6) and `NotificacaoController` (v2.10) — both introduced as dedicated controllers rather than appended to `ResourceController`, specifically because the feature spans/aggregates concerns that don't belong to one entity's CRUD surface.
- **No new repository or service layer.** All 4 repositories already expose `findByTenantId(UUID)` (verified directly in `ClienteRepository`, `ProcessoRepository`, `DocumentoRepository`, `ParecerSolicitacaoRepository`, and already used exactly this way in `ResourceController`). A `GlobalSearchService` extraction is explicitly *not* recommended for v1 — see Patterns below.
- **`SearchResultDto` in `dtos/`**, not a `Map<String,Object>`. The codebase has both conventions (`listProcessos` hand-builds a `Map<String,Object>`; `getTimeline`/`getDashboard` use typed DTOs). For a brand-new, purpose-built, non-legacy contract, the typed-record convention is the correct one to follow — it also sidesteps the camelCase/snake_case drift documented in PROJECT.md's Key Decisions (see Anti-Patterns).
- **Frontend hook/component split mirrors `use-pareceres.ts` + `notification-bell.tsx`**: a thin TanStack Query hook, and a self-contained shell component that owns its own open/closed state and side effects (keyboard listener), consistent with how `NotificationBell` is already mounted once in `dashboard-shell.tsx` and manages its own popover state and polling.

## Architectural Patterns

### Pattern 1: Server-side merge into a single discriminated-union DTO (not client-side merge)

**What:** One backend endpoint queries the 4 repositories itself and returns one flat, already-merged `List<SearchResultDto>`, each item tagged with a `tipo` field (`"cliente" | "processo" | "documento" | "parecer"`).

**When to use:** Whenever the UI needs "one view across several entity types" — this is not a new pattern being invented for search, it is the **exact pattern this codebase already uses** for `GET /processos/{id}/timeline`, which merges `Movimentacao` + `Evento` + `Documento` + `ConflictCheckDecisao` into a single sorted `List<TimelineItemDto>` server-side (`ResourceController.java:2273-2322`).

**Trade-offs:**
- (+) One HTTP round-trip per keystroke instead of 4; one TanStack Query key to debounce/cancel instead of 4.
- (+) `GET /documentos` currently has **no free-text query parameter at all** (only `processo_id`/`cliente_id` filters — verified in `ResourceController.java:2803-2805` and `use-documentos.ts`). A client-merge approach would need this endpoint changed anyway, so "no backend work" is not actually on the table for option (b) — the true comparison is "one new endpoint" vs. "modify `GET /documentos` + write 4-way client merge/loading-state logic," and the former is strictly less total work.
- (+) Every one of the 4 existing list/search endpoints already independently enforces its own `@PreAuthorize` scope. A user without `documentos:view` calling `GET /documentos` gets a 403. `apiFetch` (`web/src/lib/api.ts:43`) silently swallows 401/403 (no toast) but still `throw`s, so a client-merge implementation would need **4 separate try/catch-and-ignore blocks**, one per category, just to avoid a broken/errored UI state for the categories a given role can't see. A single backend endpoint makes this a non-issue: the category is simply absent from the response, same as `getTimeline` never including a `ConflictCheckDecisao` entry when none exists.
- (−) None significant for this codebase's actual data volumes (see Scaling Considerations).

**Example** (backend — mirrors `getTimeline`'s structure directly):
```java
@PreAuthorize("hasAnyAuthority('clientes:view','processos:view','documentos:view','pareceres:view')")
@GetMapping("/search")
public ResponseEntity<?> search(@RequestParam String q) {
    String term = q == null ? "" : q.trim();
    if (term.length() < 2) return ResponseEntity.ok(List.of());

    UUID tenantId = getTenantId();
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String termLower = term.toLowerCase();

    List<SearchResultDto> results = new ArrayList<>();
    if (hasAuthority(auth, "clientes:view"))   results.addAll(searchClientes(tenantId, termLower));
    if (hasAuthority(auth, "processos:view"))  results.addAll(searchProcessos(tenantId, termLower));
    if (hasAuthority(auth, "documentos:view")) results.addAll(searchDocumentos(tenantId, termLower));
    if (hasAuthority(auth, "pareceres:view"))  results.addAll(searchPareceres(tenantId, termLower));
    return ResponseEntity.ok(results);
}
```

### Pattern 2: Gate at the query branch, never filter after fetch (the RBAC-per-result-type answer)

**What:** For each of the 4 categories, check `authentication.getAuthorities()` for that category's `:view` scope **before** calling its repository method. If the scope is absent, that category's repository is never queried and never contributes rows to `results` — not "fetched then hidden," but "never fetched."

**When to use:** Any endpoint that aggregates multiple permission-gated resource types into one response. This is also the industry-standard shape: Microsoft Graph Search API's authorization model states search results "are scoped to enforce any access control applied to the items... users cannot access more items in a search than they can otherwise obtain from a corresponding GET operation with the same permissions" — i.e., the search endpoint must re-derive the same per-type gate its single-entity `GET` endpoints already enforce, not a separate/weaker one.

**Why this is the correct (and only safe) answer to "how does partial-permission filtering work in the SAME response":**
- The 4 target entities (clientes, processos, documentos, pareceres) each already have their own `@PreAuthorize("hasAuthority('<scope>:view')")` gate on their respective list endpoint. `SearchController` must reproduce that **same** per-scope gate, once per category, inside one method — because a single class/method-level `@PreAuthorize` can only express "all of," "any of," or "none," never "this subset, computed per branch."
- **Concrete, verified finding on current impact:** every one of the 4 seeded roles (`ASSISTENTE`, `TECNICO`, `ADVOGADO`, `ADMIN` — read directly from `DatabaseSeeder.seedRbac()`) already holds **all four** of `clientes:view`, `processos:view`, `documentos:view`, `pareceres:view`. `ASSISTENTE` is missing `financeiro:*` entirely (matching the milestone's illustrative example), but **financeiro/honorarios is not one of the 4 entities this search feature targets** per PROJECT.md ("Pesquisa global funcional cross-entity (clientes, processos, documentos, pareceres)"). So with today's seed data, no role will ever actually see a category silently omitted.
- **Why the mechanism is still mandatory, not optional:** roles/permissions in this system are DB-managed and administrable (`rbac:manage` scope, `AdminController` `GET/POST /admin/rbac`), not hardcoded to the 4 seeded roles. A tenant admin can create a custom role holding, say, `clientes:view` + `processos:view` but not `documentos:view`. The per-category gate must be correct independent of what today's fixtures happen to contain — this is exactly the class of latent bug this codebase's own audit history repeatedly catches (e.g., Phase 87's `GET /admin/users` vs. `processos:manage` mismatch, Phase 79's missing ownership check on `POST /documentos/upload`). Building the gate correctly now, even though it is inert against current seed data, is the responsible default — and it is nearly free (4 boolean checks against an already-populated `Authentication.getAuthorities()`).
- The check is a programmatic read of the same authority set Spring Security's `hasAuthority()` SpEL evaluates declaratively — no new security primitive, no risk of drifting from the `@PreAuthorize` convention used on every other endpoint in this codebase.

**Trade-offs:**
- (+) Data the requester cannot view is never materialized in the JVM for this request — strictly stronger than "fetch everything, filter the DTO list before serializing," which momentarily holds forbidden data in memory and is one careless refactor away from a leak (e.g., a future contributor reordering the filter to run after `ResponseEntity.ok(results)` is built, or a debug log statement dumping `results` before filtering).
- (+) Composes cleanly: adding a 5th searchable entity later (e.g., `honorarios`) is one more `if (hasAuthority(...)) results.addAll(searchHonorarios(...))` line, not a redesign.
- (−) A few lines of boilerplate (`hasAuthority` helper + 4 `if` branches) vs. a single declarative annotation — an acceptable, explicit cost for correctness that can't otherwise be expressed declaratively at this granularity.

**Example:**
```java
private boolean hasAuthority(Authentication auth, String authority) {
    return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals(authority));
}
```

### Pattern 3: Dedicated top-level controller, not an addition to `ResourceController`

**What:** `SearchController` gets its own class with `@RequestMapping("/api/v1/search")` at the class level and a single bare `@GetMapping` (no sub-path) on the one method.

**When to use:** Any new endpoint whose natural REST "owner" doesn't map to one of `ResourceController`'s existing entity families, mirroring how `ParecerPesquisaController` and `NotificacaoController` were split out rather than folded in.

**Why this specific shape (class-level top-level path + bare method mapping) matters here — a documented codebase pitfall:** `ParecerPesquisaController`'s own header comment explains it was extracted from `ParecerController` specifically because Spring **concatenates class-level and method-level `@RequestMapping`/`@GetMapping` paths regardless of a leading `/`** — `ParecerController` is mapped at `/api/v1/pareceres/solicitacoes`, so a method there written as `@GetMapping("/api/v1/pareceres/pesquisa")` (looks absolute, but isn't) actually resolved to `/api/v1/pareceres/solicitacoes/api/v1/pareceres/pesquisa`, making the route unreachable from v2.5 (Phase 64) until caught in the v2.6 milestone audit (Phase 69). Giving `SearchController` its own flat class-level mapping with a single argument-less method mapping sidesteps this entire bug class by construction — there is nothing to concatenate incorrectly.

**Trade-offs:**
- (+) Zero risk of the Phase 69 class of routing bug.
- (+) Keeps `ResourceController` from growing past its already-considerable size (3,296 lines).
- (+) `SearchController` has a single, clear reason to change (the search contract), independent of clientes/processos/documentos/pareceres CRUD churn.
- (−) One more controller class + one more `getTenantId()` private-method duplication (already duplicated 3× in this codebase — `ResourceController`, `ParecerPesquisaController`, `NotificacaoController` — so this is accepted, pre-existing style, not a new cost introduced here).

## Data Flow

### Request Flow

```
User types in GlobalSearchDialog's CommandInput
    ↓
useDebouncedValue(q, ~300ms)
    ↓ (only fires once query stabilizes AND length >= 2)
useGlobalSearch(debouncedQ) — TanStack Query, queryKey: ["search", debouncedQ]
    ↓
apiFetch("/search?q=...")  — credentials: "include", cookie-based JWT
    ↓
JwtAuthenticationFilter → SecurityContext populated with UserPrincipal
    ↓
SearchController.search()
    ├─ @PreAuthorize gate: reject outright if caller has NONE of the 4 view scopes
    ├─ getTenantId() — same private-method pattern as every other controller
    ├─ per-category: hasAuthority(auth, "<scope>:view") ? query : skip
    │     each branch: repository.findByTenantId(tenantId) → stream filter (contains) → limit(N) → map to SearchResultDto
    └─ merge all 4 branches into one List<SearchResultDto>, return as-is (unsorted-by-relevance is fine; grouped by tipo client-side)
    ↓
GlobalSearchDialog groups results by `tipo` into CommandGroup sections ("Clientes", "Processos", "Documentos", "Pareceres")
    ↓
User selects a result → router.push(`/${ROUTE_SEGMENT[tipo]}/${id}`) — every one of the 4 entities already has a `[id]` detail route (`/clientes/[id]`, `/processos/[id]`, `/documentos/[id]`, `/pareceres/[id]`, all verified to exist)
```

### Key Data Flows

1. **Search-as-you-type:** keystroke → debounce → single GET → single merged response → client-side grouping for display only (never client-side permission filtering — permission filtering already happened server-side per Pattern 2).
2. **Tenant isolation (must hold in every branch):** each of the 4 per-category search methods calls the same `repository.findByTenantId(tenantId)` (or an equivalent tenant-scoped finder) already used by that entity's own list endpoint — never a cross-tenant query, never a `findAll()`. This is identical to the isolation boundary every other endpoint in `ResourceController` already relies on.
3. **RBAC gate (2 layers, deliberately redundant):** (1) method-level `@PreAuthorize("hasAnyAuthority(...)")` rejects a caller with zero relevant scopes before the method body runs; (2) per-category `hasAuthority()` checks inside the method decide which of the 4 repositories are actually queried. Both layers read the same `Authentication.getAuthorities()` populated once per request by `JwtAuthenticationFilter` — no caching, no staleness risk, consistent with `SessionCreationPolicy.STATELESS`.

## Scaling Considerations

This product is a single-institution, multi-tenant practice-management tool (per PROJECT.md's Out of Scope: "Onboarding self-service multi-institituição... este deployment continua a servir uma única instituição"). The relevant scaling axis is **records per tenant accumulated over years**, not concurrent user count — reframing the template's "users" axis accordingly:

| Scale (records/tenant) | Architecture Adjustment |
|---|---|
| Current reality (dozens–low hundreds per entity type, per seed data and product stage) | The recommended in-memory `findByTenantId(tenantId)` + Java `Stream.filter(contains(...))` + `.limit(N)` approach — i.e., **exactly the same pattern already used by `listClientes`/`listProcessos`/`listDocumentos` today** — is correct and requires zero new infrastructure. |
| Thousands per entity type | Same risk profile as the *existing* `listClientes`/`listProcessos` endpoints already have today (they load the full tenant table before filtering) — not a risk newly introduced by search. No action needed specifically for search; if/when this is addressed, it should be addressed for the underlying list endpoints too, not search in isolation. |
| Tens of thousands+ per entity type | Convert the 4 in-memory filters into native `ILIKE`-with-`LIMIT` `@Query` methods, following the **already-proven pattern in this exact codebase**: `ParecerSolicitacaoRepository.pesquisar()` (nullable-param `CAST`, `ILIKE '%' \|\| :term \|\| '%'`, `nativeQuery = true`). Pair with a PostgreSQL `pg_trgm` extension + `GIN` index per searched column, added via a new numbered file in `backend/migrations/` (the project's established manual-migration convention — no Flyway/Liquibase exists; see `74-cleanup-nif-documento-tipo.sql`, `86-create-notificacao-table.sql`, etc. for the exact style). |

### Scaling Priorities

1. **First real bottleneck (if it ever arrives):** unindexed `ILIKE`/substring scans across full tenant tables — same bottleneck the rest of the app already has, search just adds 2 more entity types (documentos, a lightweight parecer pass) to a list that already includes clientes and processos today.
2. **Not a concern for v1:** result ranking/relevance scoring, full-text search engines (Postgres `tsvector`, Elasticsearch, etc.) — substantial over-engineering for an institutional tool of this size; simple substring `contains()` matching (already the app's own established UX for every existing quick-filter) is the right level of sophistication.

## Anti-Patterns

### Anti-Pattern 1: N parallel client-side calls merged in the browser

**What people do:** Fire `GET /clientes?q=`, `GET /processos?q=`, `GET /documentos?...`, `GET /pareceres/pesquisa?texto=` in parallel from a `Promise.all` in a new hook, merge/sort the 4 arrays client-side.
**Why it's wrong here:** (1) `GET /documentos` has no free-text parameter today — this doesn't even work out of the box, so "no backend change" isn't actually true. (2) Each endpoint 403s independently for a role missing that scope; `apiFetch` throws on non-2xx, so the merge hook needs 4 separate try/catch-and-treat-as-empty blocks just to avoid a broken UI section — logic a single backend endpoint eliminates entirely by only including categories the caller can see. (3) 4× the request volume per keystroke. (4) Breaks with the codebase's own established precedent (`getTimeline`) for "multiple entity types, one view."
**Do this instead:** One `SearchController` endpoint, per Pattern 1.

### Anti-Pattern 2: Fetch everything, then strip forbidden categories before responding

**What people do:** Query all 4 repositories unconditionally inside `SearchController`, then filter the merged `List<SearchResultDto>` by `tipo` right before `return ResponseEntity.ok(...)`, based on the caller's scopes.
**Why it's wrong:** Data the requester has no authority to view is briefly materialized in the JVM for that request — weaker than never querying it. One careless future refactor (a reordered filter call, a debug log of the unfiltered list, an early return added before the filter) silently reintroduces a leak. This is exactly the shape of bug this codebase's own phase-review history has repeatedly caught in other endpoints (unauthorized data reachable through a code path nobody re-checked).
**Do this instead:** Gate at the query branch — never call `searchDocumentos(...)` at all when `documentos:view` is absent (Pattern 2).

### Anti-Pattern 3: A single blanket `@PreAuthorize` scope (or AND of all 4) on the endpoint

**What people do:** `@PreAuthorize("hasAuthority('processos:view')")` on the whole method (too loose — silently lets a `processos:view`-only caller's search implicitly touch clientes/documentos/pareceres data with no declared authorization for those types), or `@PreAuthorize("hasAuthority('clientes:view') and hasAuthority('processos:view') and hasAuthority('documentos:view') and hasAuthority('pareceres:view')")` (too strict — a role holding 3 of 4 scopes gets a hard 403 and can't search at all, even for the 3 categories it *can* see).
**Why it's wrong:** Declarative `@PreAuthorize` can only express one gate for the whole method; this feature structurally needs per-category gates that only a programmatic check inside the method body can express (Pattern 2). The class-level annotation should only guard the coarse "can this caller use search at all" question (`hasAnyAuthority` of the 4 scopes).
**Do this instead:** `hasAnyAuthority(...)` at the method level as a fast-fail, then 4 independent `hasAuthority()` checks inside the body.

### Anti-Pattern 4: Adding a `search:view` permission scope

**What people do:** Introduce a new standalone permission (`search:view`) and gate the endpoint on that instead of reusing the 4 existing entity scopes.
**Why it's wrong:** Search has no independent authorization meaning of its own — it is a lens over data that is already independently permission-gated per entity type. A `search:view` scope would either be redundant (granted alongside all 4 real scopes, adding nothing) or actively confusing (granted without any of the 4 real scopes, in which case what would it even return?). It would also require updating `DatabaseSeeder.seedRbac()` and every role's permission set for a capability that should just fall out of scopes that already exist.
**Do this instead:** Derive search access entirely from the 4 existing `<entity>:view` scopes, exactly as `getTimeline` derives its cross-entity view from `processos:view` alone without inventing a `timeline:view`.

### Anti-Pattern 5: Cramming the endpoint into `ResourceController`

**What people do:** Add one more `@GetMapping("/search")` method to the already-3,296-line `ResourceController`, reusing its already-injected repositories.
**Why it's wrong:** `ResourceController` already spans clientes/processos/partes/fases/movimentações/eventos/documentos/honorários/pagamentos/dashboard — search is not a natural extension of any single one of those families, it's an aggregate over several. The two most recent instances of "a feature spans concerns" in this exact codebase (Pareceres, Notificações) were both given their own controllers rather than appended here.
**Do this instead:** New `SearchController` (Pattern 3).

### Anti-Pattern 6: Re-implementing Pareceres' deep-content search inside global search

**What people do:** Join `ParecerVersao.conteudo` (like `ParecerSolicitacaoRepository.pesquisar()` already does for the dedicated advanced-search page) inside the new global search's parecer branch, to make global search "as powerful" as the existing advanced search.
**Why it's wrong:** Duplicates business logic that already exists and is already reachable at `/pareceres/pesquisa` (`ParecerPesquisaController`, `usePesquisarPareceres` on the frontend). Two independent implementations of "search inside parecer content" will drift over time (this codebase already has direct experience with exactly this class of divergence — see PROJECT.md's "5ª implementação divergente de prazo crítico" saga that took 4 milestones to fully consolidate).
**Do this instead:** Global search's `parecer` category does a shallow match on `ParecerSolicitacao.descricao` only (fast, consistent with a "quick filter" UX). A "ver mais resultados em Pareceres" affordance in the results UI can deep-link to `/pareceres/pesquisa?texto=...`, the already-existing, already-correct advanced-search surface — reuse, not duplication.

### Anti-Pattern 7 (naming, not logic): letting the new DTO drift into snake_case/camelCase mismatch

**What people do:** Build `SearchResultDto` as a `Map<String,Object>` with hand-picked snake_case keys (like `listProcessos` does) "for consistency" with the older list endpoints.
**Why it's wrong:** PROJECT.md's own Key Decisions log documents this exact bug class from the v2.4 milestone (backend emitting camelCase, frontend reading snake_case on new fields) as something that required a surgical, field-by-field `@JsonProperty` fix and was explicitly flagged as recurring technical debt. This is a brand-new contract with no legacy consumers — there is no reason to inherit the older, inconsistent convention.
**Do this instead:** `SearchResultDto` as a plain Java `record` (Jackson serializes camelCase fields as camelCase JSON by default, zero `@JsonProperty` needed) — exactly matching `TimelineItemDto` and `ParecerSolicitacao`'s existing, already-consistent camelCase contracts. The frontend `SearchResult` TS type mirrors those same field names verbatim.

## Integration Points

### External Services

None. This feature touches no external service — it is a purely internal aggregation over 4 repositories already backed by the same PostgreSQL database every other endpoint uses.

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| `SearchController` ↔ `ClienteRepository` / `ProcessoRepository` / `DocumentoRepository` / `ParecerSolicitacaoRepository` | Direct Spring `@RequiredArgsConstructor` injection, same as `ResourceController` | No new repository methods strictly required — all 4 already expose `findByTenantId(UUID)`. If capping via SQL `LIMIT` is later desired (post-v1, per Scaling), add native `@Query` methods mirroring `ParecerSolicitacaoRepository.pesquisar()`. |
| `SearchController` ↔ `SecurityContextHolder` / `UserPrincipal` | Read-only, per-request | Same `getTenantId()` idiom duplicated in `ResourceController`/`ParecerPesquisaController`/`NotificacaoController`; add a second small `hasAuthority(Authentication, String)` private helper (net new, ~3 lines) for the per-category gate. |
| `GlobalSearchDialog` ↔ `useGlobalSearch` ↔ `apiFetch` | TanStack Query → fetch wrapper, cookie-based JWT (`credentials: "include"`) | Identical to every other `use-*.ts` hook; no new auth mechanism, no bearer tokens introduced. |
| `GlobalSearchDialog` ↔ Next.js router | `router.push()` on result click, using a small client-side `tipo → route segment` map (`cliente→/clientes`, `processo→/processos`, `documento→/documentos`, `parecer→/pareceres`) | All 4 `[id]` detail routes already exist and were verified directly in `web/src/app/(dashboard)/{clientes,processos,documentos,pareceres}/[id]/page.tsx`. |
| `dashboard-shell.tsx` ↔ `GlobalSearchDialog` | Mount point only — shell passes nothing in, dialog is fully self-contained | Same relationship `dashboard-shell.tsx` already has with `NotificationBell` and `UserMenu`. |

## Recommendation Summary (for roadmap/phase planning)

**(a) One backend endpoint** — `GET /api/v1/search?q=`, returning a flat `List<SearchResultDto>` (tipo-discriminated, capped per category, e.g. 5-8 results/category). Rejects N-parallel-client-calls for concrete, codebase-specific reasons (Anti-Pattern 1), not just general preference.

**(b) RBAC-per-result-type** — method-level `@PreAuthorize("hasAnyAuthority('clientes:view','processos:view','documentos:view','pareceres:view')")` as a coarse gate, plus 4 independent `hasAuthority(auth, "<scope>:view")` checks inside the method body, each guarding whether that category's repository is queried **at all** (never "query then hide"). Verified finding: no currently-seeded role is actually restricted across these 4 specific entities today (all of ASSISTENTE/TECNICO/ADVOGADO/ADMIN hold all 4 `:view` scopes) — but the mechanism must be built correctly regardless, because roles are DB-managed via `rbac:manage` and future/custom roles are not guaranteed to hold all 4.

**(c) New dedicated `SearchController`**, not a method on `ResourceController`. Own top-level `@RequestMapping("/api/v1/search")` with one bare `@GetMapping`, deliberately avoiding the class-level/method-level path-concatenation bug this codebase already hit once (`ParecerPesquisaController`'s header comment, Phase 69). Matches the established precedent of giving cross-cutting/aggregate features (Pareceres, Notificações) their own controller rather than growing `ResourceController` further.

**Build order: backend before frontend.** This mirrors how every comparable feature in this codebase's own history was sequenced — Parecer Jurídico shipped backend-only in v2.5 (Phase 64ish) then frontend in v2.6; the Notificações system built its persistence/API/alert-triggers in v2.10 Phases 85-88 before the bell/page UI landed in Phase 89. There is no mock-API layer left in this project to build the frontend against speculatively (`web/src/app/_api-backup/` and `web/src/server/` are explicitly legacy/superseded per `CLAUDE.md`). Concretely: implement and verify `SearchController` + `SearchResultDto` (with at least one test per RBAC branch — a caller missing one of the 4 scopes must get that category omitted, not a 403 for the whole request) before wiring `GlobalSearchDialog`/`useGlobalSearch`/the `dashboard-shell.tsx` trigger. Whether that split becomes one phase or two is a roadmap-granularity decision, not an architecture one — but the backend contract should exist and be stable before frontend work starts.

## Sources

**Backend (read directly, this session):**
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (3,296 lines — `getTenantId()` L127, `listClientes` L169-232, `listProcessos` L930-1059, `listDocumentos`/`listProcessoDocumentos`/`listClienteDocumentos` L2802-2826, `getDashboard` L3104-3127, `getTimeline` L2272-2322)
- `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java` (full — dedicated-controller + routing-bug precedent)
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` (RBAC management confirmation — `@PreAuthorize("hasRole('ADMIN')")`, `GET/POST /admin/rbac`)
- `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` (L55-89 — the one existing `Pageable`/`Page<T>` precedent in this codebase, and its response envelope shape)
- `backend/src/main/java/com/lexcv/config/{UserPrincipal,SecurityConfig}.java` (authorities population, `@EnableMethodSecurity`, stateless session policy)
- `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (native ILIKE query precedent for future scaling)
- `backend/src/main/java/com/lexcv/models/{Cliente,Processo,Documento,ParecerSolicitacao}.java` (field inventory for per-category search-field mapping)
- `backend/src/main/java/com/lexcv/dtos/TimelineItemDto.java` (the discriminated-union DTO precedent this design mirrors)
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (L293-353 — exact per-role permission grants, verifying the ASSISTENTE/TECNICO/ADVOGADO/ADMIN scope matrix)
- `backend/migrations/*.sql` (manual-migration convention, no Flyway/Liquibase)

**Frontend (read directly, this session):**
- `web/src/components/shared/dashboard-shell.tsx` (decorative search input to be replaced, L121-127; `NotificationBell` mount precedent)
- `web/src/components/shared/notification-bell.tsx` (self-contained shell-component precedent)
- `web/src/components/ui/command.tsx` (confirms `Command`/`CommandDialog`/`CommandGroup`/`CommandInput` already installed via `cmdk`, added in v2.13 Phase 107 for the `Combobox` component — zero new dependency needed)
- `web/src/lib/api.ts` (`apiFetch` — 401/403 toast-suppression behavior, relevant to Anti-Pattern 1)
- `web/src/lib/permissions.ts`, `web/src/hooks/use-permissions.ts`, `web/src/hooks/use-me.ts` (frontend RBAC mirror)
- `web/src/hooks/use-pareceres.ts`, `web/src/hooks/use-clientes.ts`, `web/src/hooks/use-documentos.ts` (existing hook conventions, and confirmation `GET /documentos` has no `q` param today)
- `web/src/app/(dashboard)/{clientes,processos,documentos,pareceres}/[id]/page.tsx` (confirmed all 4 entities have a working detail route for result-click navigation)
- `.planning/PROJECT.md` (milestone scope, Key Decisions log — camelCase/snake_case drift precedent, "5ª implementação divergente" precedent, single-institution deployment scope)

**External validation (WebSearch, 2026-07-18):**
- Microsoft Graph Search API authorization model — confirms "gate before fetch, not after" as the industry-standard shape for multi-entity search authorization: [Use the Microsoft Search API to query data](https://learn.microsoft.com/en-us/graph/api/resources/search-api-overview?view=graph-rest-1.0)
- shadcn `CommandDialog` + global Cmd/Ctrl+K listener pattern — confirms the recommended frontend shape is the standard, documented convention for this exact primitive: [Command - shadcn/ui](https://ui.shadcn.com/docs/components/radix/command), [Shadcn KBD UI: Build a Powerful ⌘K Command Menu](https://shadcnstudio.com/blog/shadcn-kbd-ui-component/)

---
*Architecture research for: LexCV v2.14 — Pesquisa global funcional cross-entity*
*Researched: 2026-07-18*
