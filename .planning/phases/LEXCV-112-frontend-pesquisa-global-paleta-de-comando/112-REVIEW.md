---
phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando
reviewed: 2026-07-21T19:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - web/src/types/search.ts
  - web/src/lib/use-debounced-value.ts
  - web/src/hooks/use-global-search.ts
  - web/src/lib/search-recents.ts
  - web/src/lib/highlight-match.tsx
  - web/src/components/shared/global-search-dialog.tsx
  - web/src/components/shared/dashboard-shell.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/processos/page.tsx
  - web/src/app/(dashboard)/documentos/page.tsx
  - web/src/app/(dashboard)/pareceres/page.tsx
findings:
  critical: 2
  warning: 5
  info: 5
  total: 12
status: issues_found
---

# Phase LEXCV-112: Code Review Report

**Reviewed:** 2026-07-21T19:00:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Reviewed all 11 files that make up Phase 112's cross-entity search command palette (Ctrl+K), built across 5 plans by 3 executor agents. The six explicitly-flagged risk areas were traced individually:

1. **`shouldFilter={false}`** — present on the `Command` root (`global-search-dialog.tsx:172`). Correct.
2. **XSS in the highlight renderer** — `highlight-match.tsx` never uses `dangerouslySetInnerHTML`; matched/unmatched text is returned as plain JSX children (auto-escaped by React). Confirmed safe.
3. **Open-redirect on `rota`** — `isInternalLinkUrl` (`web/src/lib/notificacao-categoria.ts`, called from `global-search-dialog.tsx:144`) is invoked before every `router.push`. I empirically verified it against 15+ bypass payloads (`//evil.com`, `/\evil.com`, embedded TAB/CR/LF, etc. — all via Node's real WHATWG `URL` parser, matching what the function itself uses) and it rejected every protocol-relative/authority-introducing variant. The mechanism is sound, but see WR-03 and WR-05 below for gaps in its surrounding error handling and test coverage.
4. **sessionStorage-only recents** — confirmed `search-recents.ts` never touches `localStorage` and never calls `apiFetch`/`fetch`. Correct. See WR-02 for a robustness gap in how this data is consumed.
5. **RBAC gating via `permissions.isFetched`** — grepped all 11 files; every permission gate (the palette's skeleton-loading gate and all 4 list pages' access-denied gates) uses `permissions.isFetched`, never `!permissions.isLoading`. Correct, no regressions of the recurring bug class.
6. **The 4 list-page `?q=` seeding edits** — this is where the review found real, previously-undiscovered problems. I traced each seeded value all the way through to the backend query it ultimately triggers (not just the frontend wiring) and compared it against the fields the *global* search (`PesquisaController`/`*Repository#pesquisarGlobal`) actually matched on to produce the clicked-through result. **For 3 of the 4 entity types, the "Ver Todos X" link can silently show fewer results — including zero — than the global search that produced it**, because each list page's pre-existing filter/search endpoint was built independently of the new global search and matches a different field set and/or accent-folding behavior. Processos is the one page that happens to be safe. Full detail in CR-01/CR-02/WR-01 below.

I also ran the project's actual toolchain rather than trusting the plan/summary docs: `pnpm exec tsc --noEmit` (clean for all 11 files; 3 unrelated pre-existing `vitest`-module errors elsewhere) and `pnpm exec eslint` scoped to these 11 files (0 errors/warnings in 10 of them; 1 pre-existing `react-hooks/set-state-in-effect` error + 2 pre-existing `@next/next/no-img-element` warnings in `dashboard-shell.tsx`, both predating this phase — see IN-05).

## Critical Issues

### CR-01: "Ver Todos os Pareceres" can show zero results for a solicitação the global search just found — it searches a different field on a different table

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:62-72, 87`
**Issue:**

The palette's parecer branch (`PesquisaController.java:138-141` → `ParecerSolicitacaoRepository.pesquisarGlobal`) matches the query against **`s.descricao`** (the solicitação's own description), accent-folded:

```java
// ParecerSolicitacaoRepository.java:75-81
"WHERE s.tenant_id = :tenantId " +
"AND unaccent(s.descricao) ILIKE unaccent('%' || CAST(:termoEscapado AS text) || '%') ESCAPE '\\' " +
```

But `pareceres/page.tsx`'s seed block sends that same term to `pesquisaFilters.texto`, which `usePesquisarPareceres` (`web/src/hooks/use-pareceres.ts:60-81`) sends to `GET /api/v1/pareceres/pesquisa?texto=`, which is backed by a **completely different query** — `ParecerSolicitacaoRepository.pesquisar()` — that matches **`v.conteudo`** (the latest uploaded *version's* content, via a `LEFT JOIN`), with **no `unaccent`**:

```java
// ParecerSolicitacaoRepository.java:41-50
"LEFT JOIN t_parecer_versao v ON v.solicitacao_id = s.id AND v.numero_versao = (SELECT MAX(...)) " +
"WHERE s.tenant_id = :tenantId " +
"AND (CAST(:texto AS text) IS NULL OR v.conteudo ILIKE '%' || CAST(:texto AS text) || '%')",
```

Consequences:
- Any `ParecerSolicitacao` that has **no submitted version yet** (i.e. any `PENDENTE`/newly-created request — a large, common fraction of real data) has `v.conteudo IS NULL`, and `NULL ILIKE '...'` is `NULL`, not true — it can **never** match once `texto` is non-null, regardless of how well its `descricao` matched in the global search.
- Even for solicitações with a version, an accented query that matched via `unaccent(descricao)` in the global search may not match the plain (non-`unaccent`) `v.conteudo` ILIKE.

So a very ordinary flow — search "revisão contrato X" in the palette, see a matching `PENDENTE` parecer, click "Ver todos os Pareceres" — lands on `/pareceres?q=revisão contrato X` and shows "Nenhum resultado encontrado," even though the exact result that motivated the click is sitting right there in the palette. This directly breaks the "Ver Todos" contract that Phase 112 is supposed to deliver (SRCH-09), for a common, non-exotic case. Not called out in `112-05-PLAN.md`/`112-05-SUMMARY.md`/`deferred-items.md` as a known limitation — appears to be a genuine, previously-undiscovered gap.

**Fix:** This needs to be fixed where the two queries actually diverge, i.e. `ParecerSolicitacaoRepository.pesquisar()` (backend, not in this review's file list, but the frontend's choice at `pareceres/page.tsx:62-72` to route `?q=` into this specific endpoint is what surfaces the bug — cited here as the concrete root cause). Minimal-diff option: make the deep-search endpoint's `texto` predicate a superset of the global search's, so anything `pesquisarGlobal` finds is still findable here:

```java
// ParecerSolicitacaoRepository.java — pesquisar(), replace line 50
"AND (CAST(:texto AS text) IS NULL OR " +
"     unaccent(v.conteudo) ILIKE unaccent('%' || CAST(:texto AS text) || '%') ESCAPE '\\' OR " +
"     unaccent(s.descricao) ILIKE unaccent('%' || CAST(:texto AS text) || '%') ESCAPE '\\')",
```
(remember to also pre-escape `texto`'s ILIKE metacharacters the way `PesquisaController#escapeLike` already does for the other three repositories, and pair with `ESCAPE '\\'`). Until a backend fix ships, flag this as a known limitation of "Ver Todos os Pareceres" rather than shipping it silently.

### CR-02: "Ver Todos os Clientes" omits the two fields the global search ranks highest — a structured-ID match shows zero results

**File:** `web/src/app/(dashboard)/clientes/page.tsx:66-76`
**Issue:**

The global search's cliente branch ranks matches on `numero_cliente`/`nif`/`documento_numero` as tier 0/1 (exact/prefix — the *highest*-priority match type, above even a `nome` match):

```java
// ClienteRepository.java:39-44 (pesquisarGlobal)
"AND (unaccent(c.nome) ILIKE unaccent('%' || CAST(:termoEscapado AS text) || '%') ESCAPE '\\' " +
"OR c.numero_cliente ILIKE '%' || CAST(:termoEscapado AS text) || '%' ESCAPE '\\' " +
"OR c.nif ILIKE '%' || CAST(:termoEscapado AS text) || '%' ESCAPE '\\' " +
"OR c.documento_numero ILIKE '%' || CAST(:termoEscapado AS text) || '%' ESCAPE '\\') " +
```

But the seeded `draftQuery` flows into `filters.q` → `GET /clientes?q=`, whose server-side `q` handler (`ResourceController.listClientes`, lines 192-200) checks a **different** field set — `nome`/`nif`/`email`/`telefone` — and never checks `numero_cliente` or `documento_numero` at all:

```java
// ResourceController.java:194-200
.filter(c -> {
    if (qNorm == null || qNorm.isEmpty()) return true;
    return contains(c.getNome(), qNorm)
            || contains(c.getNif(), qNorm)
            || contains(c.getEmail(), qNorm)
            || contains(c.getTelefone(), qNorm);
})
```

So a user who searches by a client's own internal number ("CLI-0042", shown as the primary line of the search result's subtitle — `montarSubtituloCliente`) or by their BI/passport `documento_numero` gets a real hit in the palette, but clicking "Ver todos os Clientes" lands on a list filtered by a `q` handler that structurally cannot match either field — again, "Nenhum resultado encontrado" for a result the user just saw. (`nome` is also affected by a secondary, lesser gap: the global search's `nome` match is `unaccent()`-folded, the list's `contains()` is plain substring — an accented name match can also silently disappear.)

Processos, by contrast, is safe: its own `q` handler (`ResourceController.java:967-975`, matching `numeroProcesso`/`tipoProcesso`/`descricao`/`tribunal`/`areaJuridica`/`estado`) is a superset of what `ProcessoRepository.pesquisarGlobal` matches, so nothing the palette finds can disappear on click-through.

**Fix:** align `listClientes`'s `q` predicate with `pesquisarGlobal`'s field set:

```java
// ResourceController.java:194-200
.filter(c -> {
    if (qNorm == null || qNorm.isEmpty()) return true;
    return contains(c.getNome(), qNorm)
            || contains(c.getNif(), qNorm)
            || contains(c.getEmail(), qNorm)
            || contains(c.getTelefone(), qNorm)
            || contains(c.getNumeroCliente(), qNorm)
            || contains(c.getDocumentoNumero(), qNorm);
})
```
(this still won't accent-fold `nome` the way `pesquisarGlobal` does; consider normalizing accents on both sides, or moving this whole filter server-side to a query that reuses the same `unaccent()` predicate.)

## Warnings

### WR-01: "Ver Todos os Documentos" client-side filter omits `tipo` and doesn't accent-fold — narrower version of CR-01/CR-02's mismatch

**File:** `web/src/app/(dashboard)/documentos/page.tsx:109-113`
**Issue:** The global search's documento branch matches `d.nome` **or** `d.tipo`, accent-folded (`DocumentoRepository.java:30-31`, `unaccent(d.nome) ILIKE ... OR unaccent(d.tipo) ILIKE ...`). The new client-side filter this phase adds only checks `nome`:
```ts
return termo ? base.filter((d) => (d.nome ?? "").toLowerCase().includes(termo)) : base;
```
A palette hit that matched on `tipo` (e.g. searching "procuração" and matching a document whose `tipo` is `PROCURACAO`, not its `nome`), or on an accented substring of `nome`, will not reappear after clicking "Ver todos os Documentos." Narrower blast radius than CR-01/CR-02 (plain `nome` substring matches — the common case — still work), so kept at Warning rather than Critical.
**Fix:**
```ts
const documentosVisiveis = React.useMemo(() => {
  const termo = nomeFiltro.trim().toLowerCase();
  const base = list.data ?? [];
  return termo
    ? base.filter(
        (d) =>
          (d.nome ?? "").toLowerCase().includes(termo) ||
          (d.tipo ?? "").toLowerCase().includes(termo),
      )
    : base;
}, [list.data, nomeFiltro]);
```
Accent-folding still won't match the backend's `unaccent()` exactly without a shared normalize helper (e.g. stripping combining diacritics via `.normalize("NFD").replace(/[̀-ͯ]/g, "")` on both `termo` and `d.nome`/`d.tipo`) — worth doing here too since this is the one page where the mismatch is otherwise cheap to close entirely client-side.

### WR-02: `readRecents()` performs no shape validation — an unrecognized `tipo` crashes the whole app, and there is no error boundary anywhere to contain it

**File:** `web/src/lib/search-recents.ts:12-27`, consumed at `web/src/components/shared/global-search-dialog.tsx:188-192`
**Issue:** `readRecents()` only checks that the parsed JSON is an `Array`, then casts wholesale: `return parsed as ResultadoPesquisa[];`. Nothing validates that each item's `tipo` is actually one of `"cliente" | "processo" | "documento" | "parecer"`. The render path indexes straight into `TIPO_META` with it:
```tsx
<ResultRow icon={TIPO_META[recente.tipo].icon} ... />
```
If `recente.tipo` is anything else, `TIPO_META[recente.tipo]` is `undefined` and `.icon` throws `TypeError` synchronously during render. I confirmed via `Glob` that this Next.js app has **no `error.tsx`/`global-error.tsx` anywhere** in `web/src/app/`, so this isn't contained to the dialog — it takes down the entire routed page.

This isn't reachable by the *current* code today (the only writer, `pushRecent`, only ever writes values the type system already constrains), which is why this is Warning rather than Critical — but `sessionStorage` persists across soft navigations within an open tab, and nothing versions the `"lexcv:search-recents"` key. The very next time `PesquisaResultadoTipo` gains/renames/removes a value (a routine kind of change), any user with a tab already open across that deploy has a stale, now-invalid `tipo` sitting in `sessionStorage`, and opening the palette crashes their session. The write-side guard (`RECENT_ELIGIBLE_TIPOS.includes(item.tipo)`) currently provides zero protection either, since that array is literally the full `PesquisaResultadoTipo` union today.
**Fix:** validate on read, not just on write:
```ts
const VALID_TIPOS = new Set<PesquisaResultadoTipo>(["cliente", "processo", "documento", "parecer"]);

export function readRecents(): ResultadoPesquisa[] {
  if (typeof window === "undefined") return [];
  try {
    const raw = sessionStorage.getItem(RECENTS_KEY) ?? "[]";
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (item): item is ResultadoPesquisa =>
        !!item && typeof item === "object" &&
        VALID_TIPOS.has((item as ResultadoPesquisa).tipo) &&
        typeof (item as ResultadoPesquisa).id === "string" &&
        typeof (item as ResultadoPesquisa).titulo === "string" &&
        typeof (item as ResultadoPesquisa).rota === "string",
    );
  } catch {
    return [];
  }
}
```

### WR-03: Blocked navigation fails silently, and the blocked item is still recorded as a "recent"

**File:** `web/src/components/shared/global-search-dialog.tsx:142-152`
**Issue:**
```ts
function navigate(rota: string) {
  if (!isInternalLinkUrl(rota)) return;   // no feedback of any kind
  router.push(rota);
  setOpen(false);
}

function onSelectResult(resultado: ResultadoPesquisa) {
  pushRecent(resultado);   // recorded unconditionally, even if navigate() below is a no-op
  navigate(resultado.rota);
}
```
`isInternalLinkUrl` itself is sound (verified separately against 15+ bypass payloads), and today's backend always produces internal-looking `rota` values, so this isn't currently exploitable — but the failure mode of the mitigation is bad defense-in-depth: if `rota` ever fails validation (compromised/buggy backend, or a future change to `ResultadoPesquisaDto`), the dialog just sits there — doesn't close, doesn't navigate, shows no error — and the rejected item has already been pushed into "Visitados recentemente," where selecting it again repeats the same silent no-op.
**Fix:**
```ts
function navigate(rota: string) {
  if (!isInternalLinkUrl(rota)) {
    toast.error("Não foi possível abrir este resultado.");
    return;
  }
  router.push(rota);
  setOpen(false);
}

function onSelectResult(resultado: ResultadoPesquisa) {
  if (!isInternalLinkUrl(resultado.rota)) {
    navigate(resultado.rota); // still show the error, but don't pollute recents
    return;
  }
  pushRecent(resultado);
  navigate(resultado.rota);
}
```

### WR-04: Re-clicking "Ver Todos" with the same search term won't re-apply the filter if the user manually cleared it in between

**Files:** `web/src/app/(dashboard)/clientes/page.tsx:72-76`, `web/src/app/(dashboard)/processos/page.tsx:63-67`, `web/src/app/(dashboard)/documentos/page.tsx:74-77`, `web/src/app/(dashboard)/pareceres/page.tsx:64-72`
**Issue:** all four pages guard their render-time seeding with `if (seededQ && seededQ !== <field>SeedKey)`. This correctly re-seeds on a *new* `?q=` value, but the seed-key is never reset by the pages' own "Limpar" actions or by the user manually editing the field. Sequence: user clicks "Ver Todos Clientes" for "silva" (seed-key becomes `"silva"`), manually clears the search box to browse everything, reopens the palette, searches "silva" again, clicks "Ver Todos Clientes" again — same URL, `seededQ === "silva" === <field>SeedKey` already, so the condition is false and the box stays empty, even though the user just explicitly asked to see "silva" results again. Narrower/less frequent trigger than CR-01/CR-02 (requires an intervening manual clear), so Warning rather than Critical.
**Fix:** make repeated "Ver Todos" clicks distinguishable regardless of text equality, e.g. have `onSelectVerTodos` append a one-shot nonce the pages key off instead of (or in addition to) the text value:
```ts
// global-search-dialog.tsx
function onSelectVerTodos(segment: string) {
  navigate(`/${segment}?q=${encodeURIComponent(query.trim())}&_seed=${Date.now()}`);
}
```
```ts
// each list page
const seededQ = searchParams.get("q");
const seedNonce = searchParams.get("_seed");
if (seededQ && seedNonce !== lastSeedNonce) {
  setLastSeedNonce(seedNonce);
  setDraftQuery(seededQ);
}
```

### WR-05: No automated tests for either of this phase's two security-relevant primitives

**Files:** `web/src/lib/highlight-match.tsx`, `web/src/components/shared/global-search-dialog.tsx` (open-redirect guard usage)
**Issue:** this review had to write a throwaway Node script to empirically verify `isInternalLinkUrl`'s bypass resistance and manually trace `highlight-match.tsx` to confirm no `dangerouslySetInnerHTML` path exists — there is no committed regression test doing either. `web/src/lib/notificacao-categoria.ts`'s own header comment documents that this exact guard has already been bypassed and reopened *twice before* ("`//evil.com`" then "`/\evil.com`"), which makes the absence of a permanent regression test for it particularly notable. Compare to Phase 111 (backend), which added real Postgres-backed regression tests for its own security-relevant fixes (ILIKE wildcard escaping). Related/contributing factor: `vitest` is referenced by 3 pre-existing test files but was never installed (`deferred-items.md` #1) — this phase inherited that gap rather than causing it, but shipped more security-relevant frontend logic on top of it without addressing it.
**Fix:** once `vitest` is wired up (tracked separately), add a unit suite asserting `isInternalLinkUrl` rejects `//evil.com`, `/\evil.com`, control-character variants, etc., and a `highlight-match` test asserting the output is plain text/React elements (e.g. `renderToStaticMarkup` never contains an unescaped `<` from the input) for inputs like `"<img src=x onerror=alert(1)>"`.

## Info

### IN-01: `highlightMatch` highlights against the live keystroke, not the debounced term that produced the visible results

**File:** `web/src/components/shared/global-search-dialog.tsx:258-263`
**Issue:** `resultados` comes from `useGlobalSearch(debouncedQuery)`, but `highlightMatch(resultado.titulo, query)` (and `subtitulo`) is called with the raw, non-debounced `query`. While the debounce timer hasn't fired yet, the bolded substring is computed against text the results weren't actually fetched for — typically just means the highlight briefly disappears (no match found) until the debounce catches up. Self-correcting within ~300ms; cosmetic only.
**Fix:** pass `debouncedQuery` (or `termo`) to both `highlightMatch` calls instead of `query`.

### IN-02: Skeleton-loading gate uses `isFetching`, discarding already-visible results during a silent background refetch

**File:** `web/src/components/shared/global-search-dialog.tsx:210`
**Issue:** `search.isFetching` is true both for the initial fetch of a brand-new query key *and* for a background refetch of an already-successful one (e.g. on window refocus once `staleTime` has elapsed). In the latter case `resultados` already has data, but the branch order shows the skeleton anyway, momentarily replacing real, valid results with placeholders.
**Fix:** gate on `search.isPending` (true only when there is no data yet) instead of `search.isFetching`, or add `&& resultados.length === 0` to the existing condition.

### IN-03: `ResultadoPesquisa.subtitulo` is typed as `string | undefined`, but the backend can send explicit `null`

**File:** `web/src/types/search.ts:7`
**Issue:** `ResultadoPesquisaDto.subtitulo` is a plain (nullable) Java `String`; `montarSubtituloCliente`/`mapearProcessos` can return `null` (e.g. a cliente with neither NIF nor `numero_cliente`). Every current consumer guards with a truthy check (`resultado.subtitulo ? ... : undefined`), so `null` is handled correctly today by accident of how it's used, not because the type says so.
**Fix:** `subtitulo?: string | null;` to make the real contract explicit and catch a future non-truthy-guarded usage at compile time.

### IN-04: Dead code — `onLogout`'s cache-invalidation comment doesn't match what the code does

**File:** `web/src/components/shared/dashboard-shell.tsx:95-103` (pre-existing, not part of this phase's diff, but present in a file this phase modified)
**Issue:**
```ts
const onLogout = async () => {
  await clearTokens();
  // Invalidamos a cache do React Query para forçar a verificação de estado e limpar dados
  await import("@tanstack/react-query");
  // Mas não podemos chamar hook dentro de função callback...
  window.location.href = "/login";
};
```
`await import("@tanstack/react-query")` only loads the module and discards the returned namespace object — it does not call `.clear()`/`invalidateQueries` on anything, so it invalidates nothing. The comment claims the opposite of what the line does. Harmless in practice (the subsequent full-page navigation resets all in-memory state anyway), but misleading to a future reader.
**Fix:** delete the dead `import(...)` call and the inaccurate comment (the hard navigation already achieves the intended effect on its own).

### IN-05: `dashboard-shell.tsx` violates the project's own `react-hooks/set-state-in-effect` rule, in the same file that treats this rule as a hard constraint

**File:** `web/src/components/shared/dashboard-shell.tsx:91-93`
**Issue:**
```ts
React.useEffect(() => {
  setDrawerOpen(false);
}, [pathname]);
```
This is exactly the pattern this phase's own comments (in the same file, a few lines above: "this repo's `react-hooks/set-state-in-effect` lint rule is configured as an error") were written to avoid. Confirmed by running `pnpm exec eslint` on this file directly: 1 error, this exact line. Already known/tracked — both `112-05-SUMMARY.md`'s "Issues Encountered" and the phase's own `deferred-items.md` explicitly log this as pre-existing, out-of-scope debt spanning several files (not introduced by Phase 112) — logged here at Info rather than Warning purely to avoid re-flagging already-tracked debt at a higher severity than the project itself has assigned it.
**Fix:** out of this phase's scope per its own tracking; when the dedicated lint-debt cleanup happens, replace with the same render-time "adjust state" idiom this phase already established elsewhere (or drop the effect — `SidebarNav`'s `onNavigate={() => setDrawerOpen(false)}` already closes the drawer on every nav-link click; confirm whether this effect is actually load-bearing for any navigation path that doesn't go through `onNavigate` before deleting it).

---

_Reviewed: 2026-07-21T19:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
