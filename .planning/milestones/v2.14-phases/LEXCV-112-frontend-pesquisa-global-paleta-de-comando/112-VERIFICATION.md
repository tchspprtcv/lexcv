---
phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando
verified: 2026-07-21T22:30:00Z
status: passed
score: 23/23 must-haves verified (5 roadmap Success Criteria + 18 plan-level truths)
overrides_applied: 2
overrides:
  - must_have: "\"Ver Todos os Clientes\" / \"Ver Todos os Documentos\" find the exact same accented-term matches the global search palette found"
    reason: "Accent-folding gap in listClientes's/documentos's client-side q/nome filter (WR-01/WR-02 in 112-REVIEW.md's re-review) is real but narrow: SRCH-09's core wiring (link exists, opens the list, pre-fills and filters by the searched term) works correctly for all 4 entity types, including the higher-priority structured-ID case (CR-02 closed numero_cliente/documento_numero). Only the lower-priority free-text accented-name edge case remains open. Explicitly scoped out of the fix cycle and accepted as documented, non-blocking debt by 112-REVIEW.md's own Closure Note."
    accepted_by: "orchestrator (112-REVIEW.md Closure Note)"
    accepted_at: "2026-07-21T21:00:00Z"
  - must_have: "Automated regression tests exist for highlight-match.tsx and for the open-redirect guard reuse in global-search-dialog.tsx"
    reason: "vitest is not installed anywhere in web/package.json (pre-existing infrastructure gap tracked since Phase 74/83, independent of Phase 112). 112-REVIEW-FIX.md's WR-05 explicitly defers adding tests until vitest is wired up in a future phase, since a 4th test file under the same broken import would not compile or run under any configured test runner today."
    accepted_by: "orchestrator (112-REVIEW-FIX.md Skipped Issues, WR-05)"
    accepted_at: "2026-07-21T18:50:57Z"
---

# Phase 112: Frontend — Pesquisa Global (Paleta de Comando) Verification Report

**Phase Goal:** O utilizador encontra e navega para qualquer Cliente/Processo/Documento/Parecer do seu tenant a partir de qualquer página, através de uma paleta de pesquisa acessível pelo topbar ou por atalho de teclado.
**Verified:** 2026-07-21T22:30:00Z
**Status:** passed
**Re-verification:** No — initial verification (no prior `112-VERIFICATION.md` existed)

**Method note:** This is a frontend-only phase verified against a live backend contract already shipped and independently verified in Phase 111 (`111-VERIFICATION.md`, status: passed, 4/4). No dev server/browser was available in this environment for live interactive UAT. Verification below is evidence-based: direct reading of every file the phase claims to have created/modified (not SUMMARY.md narrative), independent re-derivation of grep/line evidence, live re-execution of `pnpm exec tsc --noEmit`, `pnpm lint` (raw JSON parsed, not the condensed summary), and `pnpm build`, plus direct inspection of the 6 backend/frontend commits the code-review-fix cycle produced. This mirrors the same evidence standard Phase 111's own verification used when a live execution environment (there: Docker/Testcontainers) was unavailable.

## Goal Achievement

### Observable Truths (ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | (SRCH-05) Utilizador abre a paleta de pesquisa a partir do campo já existente no topbar ou via atalho Ctrl+K/⌘K, a partir de qualquer página autenticada | ✓ VERIFIED | `dashboard-shell.tsx:158-173` replaces the old decorative `<Input>` with a `<button onClick={openSearch}>` carrying the same visual classes (`rounded-full`, `h-9`, `pl-9`, etc.) plus a platform-aware `<kbd>` hint (`useSyncExternalStore`, defaults "Ctrl K", upgrades to "⌘K" only post-mount on Mac/iOS — no SSR/hydration mismatch). `dashboard-shell.tsx:194-201` adds an `md:hidden` mobile `Search` icon button (`aria-label="Pesquisar"`). Both call the shared `openSearch()` helper (`window.dispatchEvent(new Event(SEARCH_OPEN_EVENT))`, line 31-33). `global-search-dialog.tsx:113-122` registers a global `keydown` listener (`(event.metaKey \|\| event.ctrlKey) && (event.key === "k" \|\| "K")`) with `preventDefault()`, and a separate listener for `SEARCH_OPEN_EVENT` (lines 125-131) that calls `setOpen(true)` — the same window-event bridge the topbar triggers use, avoiding prop-drilling. `<GlobalSearchDialog />` is mounted **exactly once** (`dashboard-shell.tsx:204`, confirmed via grep — 1 occurrence), and `web/src/app/(dashboard)/layout.tsx:14` wraps **every** `(dashboard)` route (`agenda`, `clientes`, `dashboard`, `documentos`, `financeiro`, `notificacoes`, `pareceres`, `processos`, `profile`, `settings`) in `<DashboardShell>`, so the palette is present on every authenticated page by construction, not by per-page opt-in. |
| 2 | (SRCH-03) Ao escrever 2+ caracteres, a pesquisa dispara automaticamente ao fim de ~300ms de pausa (debounce), sem um pedido novo a cada tecla premida | ✓ VERIFIED | `use-debounced-value.ts:5-14`: generic `useDebouncedValue<T>(value, delayMs = 300)` — `window.setTimeout(() => setDebounced(value), delayMs)` with `clearTimeout` cleanup on every keystroke (deps `[value, delayMs]`), so only the *last* keystroke's timer survives to fire. `global-search-dialog.tsx:108-110`: `const debouncedQuery = useDebouncedValue(query, 300); const search = useGlobalSearch(debouncedQuery);`. `use-global-search.ts:8-17`: `enabled = trimmed.length >= 2` gates the TanStack Query `useQuery` — a query under 2 trimmed chars never fires (`enabled: false`, no network call). This mirrors the backend's own `TERMO_MIN_LENGTH = 2` (`PesquisaController.java:58,113`) exactly. Because the *branch selection* in the dialog (`hasQuery = debouncedQuery.trim().length >= 2`, line 177-178) is keyed off the **debounced** value (not the raw `query` the input displays), no loading indicator can appear during the ~300ms pause itself — `search.isFetching` only becomes true once the debounced value both changes and clears the 2-char gate. |
| 3 | (SRCH-04, SRCH-11) Resultados agrupados por tipo, subtítulo desambiguador, texto pesquisado destacado, clique navega e fecha | ✓ VERIFIED | `global-search-dialog.tsx:48` fixed `TIPO_ORDER = ["cliente","processo","documento","parecer"]` (matches the sidebar NAV order). Lines 263-295: for each tipo, results are grouped via `resultados.filter(r => r.tipo === tipo)` — **no client re-sort**, preserving the backend's own ranking (also protected by `shouldFilter={false}` at line 191, verified: exactly 1 occurrence). Each `CommandItem` renders `ResultRow` (lines 81-101) with `highlightMatch(resultado.titulo, query)` **and**, when present, `highlightMatch(resultado.subtitulo, query)` (lines 277-283) — the subtitle is the backend-computed disambiguator (`montarSubtituloCliente` → `numero_cliente · NIF nif`, `PesquisaController.java:182-194`; processo → estado/tipo; documento → tipo; parecer → status). `highlight-match.tsx:3-29`: case-insensitive `indexOf`, wraps the matched, **original-cased** slice in `<strong>`, falls back to plain, unmodified text when no literal substring match exists (e.g. an accent-folded backend match) — never `dangerouslySetInnerHTML` (grep: 0 occurrences). On select, `onSelectResult` (lines 157-167) calls `navigate(resultado.rota)`, which gates on `isInternalLinkUrl(rota)` (`notificacao-categoria.ts:106-113`, the hardened WHATWG-URL-parser guard already used by `NotificationBell`) before `router.push(rota); setOpen(false)` (lines 143-155) — navigation AND closing the palette are both confirmed in the same function. |
| 4 | (SRCH-08, SRCH-10) 3 estados distintos: antes de escrever (recentes, client/sessão-only), a carregar, sem resultados | ✓ VERIFIED | `global-search-dialog.tsx:198-296` — exactly one branch renders at a time, driven by `!hasQuery` → recents-or-empty (lines 198-228), `hasQuery && search.isFetching` → permission-gated skeleton rows (lines 229-242), `hasQuery && search.isError` → inline error text (lines 243-246, additive 4th state), `resultados.length === 0` → `Empty`/`EmptyTitle`/`EmptyDescription` "Sem resultados" (lines 247-258), else → grouped results (line 259+). Pre-query recents come from `readRecents()` (`search-recents.ts:20-43`), which reads/writes **only** `sessionStorage` (grep: 2 `sessionStorage` occurrences read+write, 0 `localStorage` occurrences), capped at `RECENTS_CAP = 5`, deduped by `(tipo,id)` (lines 56-58), most-recent-first (`unshift`, line 59), all 4 entity types eligible (`RECENT_ELIGIBLE_TIPOS`, lines 5-10) — never sent to the server (no `apiFetch`/`fetch` call anywhere in the file). The loading skeletons are gated on `permissions.isFetched && permissions.can.view(scope)` (line 233) — **never** `!permissions.isLoading` (grep: 0 occurrences of that anti-pattern), matching the project's own documented recurring-bug guard (PITFALLS.md). |
| 5 | (SRCH-09) Cada grupo de resultados mostra um link "Ver todos" que abre a lista completa desse tipo, já filtrada pelo termo pesquisado | ✓ VERIFIED | `global-search-dialog.tsx:169-175,286-292`: each group's last `CommandItem` is `meta.verTodosLabel` ("Ver todos os Clientes/Processos/Documentos/Pareceres"), `onSelect={() => onSelectVerTodos(meta.segment)}`, which calls `navigate(`/${segment}?q=${encodeURIComponent(query.trim())}&_seed=${Date.now()}`)` — a one-shot nonce added by the WR-04 code-review fix so repeated "Ver Todos" clicks with the same text still re-seed even if the destination page's box was manually cleared in between. All 4 destination pages independently confirmed to consume `?q=`/`_seed` and genuinely filter (not just cosmetically pre-fill): **Clientes** (`clientes/page.tsx:66-83`) and **Processos** (`processos/page.tsx:57-74`) seed the pre-existing `draftQuery`, which the pre-existing 300ms debounce effect flows into `filters.q` → real server-side `GET /clientes\|/processos?q=` predicates (`use-clientes.ts:16`, `use-processos.ts:147`, `ResourceController.java:195-205,973-980`). **Documentos** (`documentos/page.tsx:64-129`) — which had *no* free-text filter at all before this phase — gained a genuinely new client-side `nomeFiltro` state feeding a `documentosVisiveis` `useMemo` that the `DataTable`/mobile map now consume instead of `list.data` directly (confirmed at lines 248, 251); `useDocumentos`/`DocumentosListFilters` are untouched (`types/documentos.ts` still has only `processo_id`/`cliente_id`). **Pareceres** (`pareceres/page.tsx:62-79`) seeds the *correct* dual-mode advanced-search path (`pesquisaTexto`, `pesquisaFilters`, `pesquisaSubmitted=true`, `pesquisaOpen=true`), never the unrelated simple `filters` state, and `usePesquisarPareceres` only activates when `pesquisaSubmitted` is true (`use-pareceres.ts`). All 4 pages re-seed correctly on repeated same-page navigation via a `seedNonce ?? seededQ` fallback key (traced explicitly, matches both the nonce-bearing and bare-`?q=` cases). |

**Score:** 5/5 Roadmap Success Criteria verified (plus 18/18 finer-grained plan-level truths independently verified — see Required Artifacts / Key Link tables below).

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/types/search.ts` | `ResultadoPesquisa` mirroring backend `ResultadoPesquisaDto` (subtitulo optional) | ✓ VERIFIED | 9 lines; `tipo: "cliente"\|"processo"\|"documento"\|"parecer"`, `id`, `titulo`, `subtitulo?`, `rota` — exact 1:1 field match against `ResultadoPesquisaDto` (5 fields) and `PesquisaController.java`'s 4 `mapear*` methods. |
| `web/src/lib/use-debounced-value.ts` | Generic 300ms debounce hook | ✓ VERIFIED | 14 lines; `"use client"`, `useState`+`useEffect` with `setTimeout`/`clearTimeout`, default `delayMs = 300`. |
| `web/src/hooks/use-global-search.ts` | TanStack Query wrapper, enabled-gated at ≥2 chars | ✓ VERIFIED | 18 lines; `useQuery({queryKey:["pesquisa",trimmed], queryFn: () => apiFetch<ResultadoPesquisa[]>(\`/pesquisa?q=${encodeURIComponent(trimmed)}\`), enabled, staleTime:30_000})`. `API_BASE` = `NEXT_PUBLIC_API_BASE_PATH` (`/api/v1`), so the real request path is `/api/v1/pesquisa?q=` — matches `PesquisaController`'s `@RequestMapping("/api/v1/pesquisa")` exactly. |
| `web/src/lib/search-recents.ts` | sessionStorage recents, cap 5, dedupe, all 4 tipos | ✓ VERIFIED | 65 lines; `readRecents()`/`pushRecent()`, `VALID_TIPOS` shape-guard added by the WR-02 code-review fix (rejects a stale/renamed `tipo` instead of crashing the render path with no error boundary). |
| `web/src/lib/highlight-match.tsx` | Bold-substring highlighter, plain fallback | ✓ VERIFIED | 29 lines; case-insensitive `indexOf`, `<strong>`-only, 0 `dangerouslySetInnerHTML`. |
| `web/src/components/shared/global-search-dialog.tsx` | Self-contained Ctrl+K palette | ✓ VERIFIED | 301 lines (well above the plan's `min_lines: 120`); exports `GlobalSearchDialog` + `SEARCH_OPEN_EVENT`; composes all 5 Plan-01 units. |
| `web/src/components/shared/dashboard-shell.tsx` (modified) | Topbar triggers + single mount | ✓ VERIFIED | Desktop trigger (158-173), mobile trigger (194-201), single `<GlobalSearchDialog />` (204), old `<Input>` and its import fully removed (grep: 0 `<input` in the topbar region, 0 unused `Input` import — confirmed by a clean `pnpm lint`). |
| `web/src/app/(dashboard)/clientes/page.tsx` (modified) | `?q=` seeds `draftQuery` | ✓ VERIFIED | Lines 66-83; seeds via a render-phase conditional (not `useEffect`, avoiding `react-hooks/set-state-in-effect`), keyed on `seedNonce ?? seededQ`; other draft* filters untouched. |
| `web/src/app/(dashboard)/processos/page.tsx` (modified) | `?q=` seeds `draftQuery` | ✓ VERIFIED | Lines 57-74; identical shape to Clientes. |
| `web/src/app/(dashboard)/documentos/page.tsx` (modified) | New client-side `nomeFiltro`, seeded from `?q=` | ✓ VERIFIED | Lines 64-129; genuinely new filter (nome **and** tipo, after the WR-01 fix), `useDocumentos`/`DocumentosListFilters` untouched. |
| `web/src/app/(dashboard)/pareceres/page.tsx` (modified) | `?q=` seeds the advanced-search path | ✓ VERIFIED | Lines 62-79; sets all 4 `pesquisa*` setters, never touches the unrelated `filters`/`setFilters`. |

**11/11 artifacts VERIFIED** (exists, substantive, wired — no stubs, no orphans).

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `GlobalSearchDialog` | `useGlobalSearch(debouncedQuery)` | `useDebouncedValue(query, 300)` then the enabled-gated hook | ✓ WIRED | Lines 108-110. |
| `GlobalSearchDialog` result `onSelect` | `router.push(rota)` | `isInternalLinkUrl` guard + `pushRecent` + `setOpen(false)` | ✓ WIRED | Lines 143-167; `isInternalLinkUrl` independently re-read (`notificacao-categoria.ts:106-113`) — resolves against a fixed sentinel origin via the real `URL` parser, rejecting `//host`, backslash, and control-char open-redirect variants. |
| `Command` root | cmdk ranking | `shouldFilter={false}` | ✓ WIRED | Line 191; exactly 1 occurrence (grep). |
| `GlobalSearchDialog` | topbar triggers (dashboard-shell) | exported `SEARCH_OPEN_EVENT` window event + Ctrl/Cmd+K keydown listener | ✓ WIRED | Lines 37, 113-131; `dashboard-shell.tsx:3,32` imports and dispatches the same constant (not a re-declared string literal — verified both files reference the same exported symbol). |
| topbar desktop + mobile triggers | `GlobalSearchDialog` open-state | `window.dispatchEvent(new Event(SEARCH_OPEN_EVENT))` | ✓ WIRED | `dashboard-shell.tsx:31-33,162,196`. |
| `dashboard-shell` | `GlobalSearchDialog` | mounted once, zero props | ✓ WIRED | Line 204; grep confirms exactly 1 JSX occurrence. |
| clientes/processos page | existing `draftQuery` → `filters.q` → `GET /clientes\|/processos?q=` | `useSearchParams().get("q")` seeds `draftQuery`; existing 300ms debounce flows it into `filters.q` | ✓ WIRED | Traced through `use-clientes.ts:16`/`use-processos.ts:147` into `ResourceController.java`'s real `Stream` predicates (not a static/hardcoded return). |
| documentos page | client-side `.filter()` over `list.data` by nome/tipo | new `nomeFiltro` state seeded from `?q=`; filtered array feeds `DataTable` + mobile cards | ✓ WIRED | Lines 116-129, 248, 251 — `documentosVisiveis`, not `list.data`, is what's rendered. |
| pareceres page | `usePesquisarPareceres` advanced-search path | seed sets `pesquisaTexto`+`pesquisaFilters({texto})`+`pesquisaSubmitted(true)`+`pesquisaOpen(true)` | ✓ WIRED | Lines 73-79; `searchActive = pesquisaSubmitted` (line 96) correctly switches `rows`/`resultsLoading`/`resultsError` to the search-path data. |

**9/9 key links WIRED.**

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `GlobalSearchDialog` results | `search.data` | `useGlobalSearch` → `apiFetch` → `GET /api/v1/pesquisa?q=` → `PesquisaController` → 4 real native `@Query` repositories, tenant + RBAC scoped | Yes — real DB-backed query, independently confirmed in Phase 111's own verification (`111-VERIFICATION.md`, Data-Flow Trace: FLOWING) | ✓ FLOWING |
| `GlobalSearchDialog` recents | `readRecents()` | `sessionStorage` (by design — SRCH-10 requires client/session-only, never server) | Yes — real client-persisted selections written by `pushRecent` on every successful navigation | ✓ FLOWING (client-only by design, not a stub) |
| Clientes/Processos list results | `clientes.data`/`processos.data` | `filters.q` (seeded) → `useClientes`/`useProcessos` → real tenant-scoped `Stream` filter in `ResourceController.java` | Yes | ✓ FLOWING |
| Documentos list results | `documentosVisiveis` | `nomeFiltro` (seeded) → client-side `.filter()` over `list.data` (already-fetched, already-tenant-scoped) | Yes | ✓ FLOWING |
| Pareceres list results | `rows` (`pesquisa.data` when `searchActive`) | `pesquisaFilters`/`pesquisaSubmitted` (seeded) → `usePesquisarPareceres` → `GET /pareceres/pesquisa?texto=` → real native query | Yes | ✓ FLOWING |

No hollow props, no hardcoded empty arrays feeding a rendered list, no disconnected data sources found.

### Behavioral Spot-Checks / Build Verification

No dev server or browser was available in this environment (stated constraint for this verification run), so interactive spot-checks (actually pressing Ctrl+K, typing, clicking a result) were not run live. In their place, the following were independently re-executed (not just read from SUMMARY.md) to confirm the current state of the repository:

| Check | Command | Result | Status |
|-------|---------|--------|--------|
| Type safety | `cd web && pnpm exec tsc --noEmit` | 3 errors, all `TS2307: Cannot find module 'vitest'` in 3 pre-existing, unrelated `*.test.ts` files (Phase 74/83) — 0 errors touching any Phase 112 file | ✓ PASS (matches documented pre-existing baseline exactly) |
| Lint | `cd web && pnpm lint` | 6 errors / 17 warnings total, all in 14 files pre-existing and unrelated to Phase 112 — independently parsed the raw ESLint JSON report and confirmed **0 errors, 0 warnings** in all 10 Phase 112 files (`global-search-dialog.tsx`, `use-global-search.ts`, `search-recents.ts`, `highlight-match.tsx`, `use-debounced-value.ts`, `types/search.ts`, `clientes/page.tsx`, `processos/page.tsx`, `documentos/page.tsx`, `pareceres/page.tsx`) | ✓ PASS |
| Production build | `cd web && pnpm build` | `✓ Compiled successfully`, `Finished TypeScript`, all 24 routes generated including `/documentos/[id]`, `/pareceres/[id]`, `/processos/[id]`, `/clientes/[id]` (confirming all 4 recents/navigation detail routes genuinely exist) | ✓ PASS |
| Backend compile of review-fix commits | Direct read of `ResourceController.java`, `ParecerSolicitacaoRepository.java`, `ParecerPesquisaController.java` at current HEAD | CR-01/CR-02 fixes independently re-confirmed present in the actual current source (not just claimed in `112-REVIEW-FIX.md`) | ✓ PASS |
| No new dependency | `git log -- web/package.json` since Phase 104 | Last touched at Phase 104 (`9f8327b`), untouched by any Phase 112 commit | ✓ PASS |

Step 7c (Probe Execution): N/A — no `scripts/*/tests/probe-*.sh` convention exists in this project and none is referenced by any Phase 112 PLAN/SUMMARY.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|--------------|--------|----------|
| SRCH-03 | 112-01, 112-02 | Debounce ~300ms, ≥2 chars | ✓ SATISFIED | `use-debounced-value.ts`, `use-global-search.ts` |
| SRCH-04 | 112-02 | Subtítulo desambiguador + navega ao clicar | ✓ SATISFIED | `global-search-dialog.tsx` `ResultRow`, `navigate()` |
| SRCH-05 | 112-02, 112-03 | Abre via topbar ou Ctrl+K/⌘K, qualquer página | ✓ SATISFIED | `dashboard-shell.tsx`, `global-search-dialog.tsx`, `layout.tsx` |
| SRCH-08 | 112-02 | Estados vazio/loading/sem-resultados | ✓ SATISFIED | `global-search-dialog.tsx` 4-branch `CommandList` |
| SRCH-09 | 112-02, 112-04, 112-05 | "Ver todos" filtra a lista completa | ✓ SATISFIED (with documented accent-folding edge-case debt — see overrides) | `onSelectVerTodos` + all 4 list pages |
| SRCH-10 | 112-01, 112-02 | Recentes em sessionStorage, nunca servidor | ✓ SATISFIED | `search-recents.ts` |
| SRCH-11 | 112-01, 112-02 | Destaque visual do texto correspondente | ✓ SATISFIED | `highlight-match.tsx` |

**7/7 requirements mapped to Phase 112 in REQUIREMENTS.md are SATISFIED. 0 orphaned requirements** (cross-checked `REQUIREMENTS.md`'s Traceability table against every plan's frontmatter `requirements:` field — exact match, no Phase-112-mapped requirement is missing from a plan's declared scope).

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | none found | — | Scanned all 11 created/modified frontend files and the 3 review-fix-touched backend files for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`, "coming soon"/"not yet implemented" wording, empty-return stubs, and hardcoded-empty-data patterns — **zero matches** (the one `return null` in `global-search-dialog.tsx:265` is a legitimate empty-group skip inside a `.map()`, not a stub; the one `placeholder=` match is a normal HTML input attribute). |

No debt markers, no blockers, no warnings from this scan.

### Accepted, Documented Debt (non-blocking — see `overrides` in frontmatter)

Two residual findings from `112-REVIEW.md`'s re-review cycle remain, both explicitly scoped out of the fix cycle and accepted as non-blocking by the review's own Closure Note (not silently dropped):

1. **Accent-folding gap on "Ver Todos os Clientes"/"Ver Todos os Documentos"** — `ResourceController.java`'s `listClientes` `q` filter and `documentos/page.tsx`'s client-side `nomeFiltro` filter both do plain, non-accent-folded substring matching, while the global search palette's own backend queries (`ClienteRepository#pesquisarGlobal`, `DocumentoRepository#pesquisarGlobal`) are `unaccent()`-folded. A palette hit found via an accented term (e.g. searching "Conceicao" to find "Maria da Conceição") can disappear on "Ver Todos" click-through. Independently re-confirmed still present by direct code read (`ResourceController.java:143-146` uses plain `toLowerCase().contains()`, no `Normalizer`/`unaccent`). Does not affect the higher-priority structured-ID case (NIF, numero_cliente, numero_processo, documento_numero all match correctly after CR-02).
2. **No automated tests for `highlight-match.tsx` / the `isInternalLinkUrl` reuse** — blocked on `vitest` not being installed anywhere in this repo (pre-existing gap since Phase 74/83, orthogonal to Phase 112). Adding a test file under the same broken import would not compile or run under any configured runner today.

Neither finding blocks any of the 5 ROADMAP.md Success Criteria — both are narrow completeness/coverage gaps on top of otherwise fully-wired, working functionality.

### Human Verification Required

None required to reach a PASS determination — all 5 Success Criteria are supported by direct, traceable code evidence (existence, substance, wiring, and data-flow), plus a passing build. The following are optional, non-blocking confirmations a human could do at convenience when a browser becomes available (not gating this verification):

1. **Visual near-identity of the new topbar trigger vs. the old decorative input** — code confirms identical Tailwind classes were copied, but a side-by-side screenshot compare would confirm no visual regression.
2. **Cross-browser Ctrl+K interception** — `event.preventDefault()` is called on the app's own keydown handler; some browsers may still reserve the combo in certain focus contexts (e.g., address bar). This is standard industry practice, not specific to a Phase 112 defect, and does not change the PASS determination.
3. **Toast visibility for the WR-03 blocked-navigation path** — not reachable with today's backend (which always emits internal-looking `rota` values), so this is defense-in-depth for a hypothetical future case, not a currently-exercisable path.

### Gaps Summary

None. All 5 ROADMAP.md Success Criteria are verified with direct code evidence (existence, substance, and wiring, plus real data-flow to either the Phase 111 backend endpoint or each list page's own existing/newly-added filter mechanism). All 11 artifacts and 9 key links pass. All 7 requirements mapped to this phase in `REQUIREMENTS.md` are satisfied, with 0 orphans. `pnpm build`, `pnpm exec tsc --noEmit`, and `pnpm lint` were independently re-run and show zero new issues attributable to any of the 10 Phase 112 frontend files (the residual 3 `tsc` errors and 6 lint errors/17 warnings are all pre-existing, unrelated, and independently confirmed to not touch this phase's files). The code review cycle (`112-REVIEW.md` → `112-REVIEW-FIX.md` → re-review) closed with 0 Critical findings remaining; 3 of the 6 fixed issues (CR-01, CR-02, and the documentos WR-01/old tipo-match) were independently re-verified against the actual current backend/frontend source in this session, not merely trusted from the review document. Two narrow, explicitly-accepted debt items remain (accent-folding edge case on 2 "Ver Todos" destinations; deferred test coverage pending `vitest` infrastructure) and are recorded as overrides above — neither blocks phase goal achievement.

---

*Verified: 2026-07-21T22:30:00Z*
*Verifier: Claude (gsd-verifier)*
