---
phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando
reviewed: 2026-07-21T20:30:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java
  - backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
  - web/src/app/(dashboard)/documentos/page.tsx
  - web/src/lib/search-recents.ts
  - web/src/components/shared/global-search-dialog.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/processos/page.tsx
  - web/src/app/(dashboard)/pareceres/page.tsx
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: clean
closed_at: 2026-07-21T21:00:00Z
closed_by: orchestrator (accepted as documented, non-blocking debt — see Closure Note)
---

## Closure Note (2026-07-21)

All 6 original Critical/Warning findings (CR-01, CR-02, WR-01 through WR-04) are confirmed fixed by this re-review — 0 Critical remains. The re-review surfaced 2 *new* Warnings (accent-folding gap on Clientes/Documentos "Ver Todos" destination filters — a secondary completeness gap explicitly acknowledged as out-of-scope by CR-02's own original text, not a functional break) plus 3 Info items (code duplication, redundant guard). None reach `security_block_on: high`. Given diminishing returns on a further fix→re-review cycle for cosmetic/completeness gaps, these are accepted as documented technical debt rather than triggering fix iteration 2 — tracked here for a future phase, not silently dropped. WR-05 (missing tests) remains deferred pending vitest infrastructure, per `deferred-items.md`.

# Phase LEXCV-112: Code Review Report (RE-REVIEW)

**Reviewed:** 2026-07-21T20:30:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

This is a re-review of the fixes `gsd-code-fixer` applied against the original `112-REVIEW.md` (2 Critical, 5 Warning findings). All 6 in-scope fixes (CR-01, CR-02, WR-01, WR-02, WR-03, WR-04) were traced end-to-end — not just re-read in isolation — against the actual commits (`c35f8d6`, `e699575`, `f91f013`, `2076a2e`, `4e64daf`, `49550aa`) and cross-checked against the exact backend queries/frontend call chains they claim to fix. WR-05 (missing tests) is confirmed still deferred per `deferred-items.md`/`112-REVIEW-FIX.md` and is **not** re-flagged here, per instruction.

**Note on finding IDs:** this document fully replaces the prior `112-REVIEW.md`. The `WR-01`/`WR-02`/`IN-01`–`IN-03` IDs below are **freshly assigned to new issues found during this pass** and do not correspond to the previous document's `CR-01`/`CR-02`/`WR-01`–`WR-05` IDs — all of the latter are addressed in the verification narrative immediately below, not re-listed as open findings.

**Verification results, one per original finding:**

1. **CR-01 (pareceres deep-search field mismatch) — CONFIRMED FIXED.** Traced the full chain: `GlobalSearchDialog.onSelectVerTodos` → `pareceres/page.tsx`'s seed block → `usePesquisarPareceres` (`web/src/hooks/use-pareceres.ts:60-81`) → `GET /pareceres/pesquisa?texto=` → `ParecerPesquisaController.pesquisarSolicitacoes` (now escapes `texto` via a new local `escapeLike`, mirroring `PesquisaController#escapeLike` byte-for-byte) → `ParecerSolicitacaoRepository.pesquisar()`, whose final predicate now reads `unaccent(v.conteudo) ILIKE unaccent(...) ESCAPE '\' OR unaccent(s.descricao) ILIKE unaccent(...) ESCAPE '\'` (`ParecerSolicitacaoRepository.java:62-64`). Verified: (a) parens balance and the clause is syntactically consistent with every other `CAST(...) IS NULL OR ...` predicate in the same query; (b) the `LEFT JOIN`'s correlated `MAX(numero_versao)` subquery still yields at most one row per `solicitacao` (no fan-out/duplicate-row risk from adding the OR branch); (c) `escapeLike`'s backslash-first replacement order is preserved, so escaping is not corrupted for terms containing literal `%`/`_`/`\`; (d) grepped `backend/src/test` — zero references to `ParecerSolicitacaoRepository.pesquisar()` or `ParecerPesquisaController` exist, so no existing test could have been broken by this change, confirming the fix report's own claim. This also closes a pre-existing wildcard-injection gap in `pesquisar()` that predates Phase 112 (the old code passed `texto` unescaped) — a genuine, if incidental, security improvement.
2. **CR-02 (clientes q filter omitted numero_cliente/documento_numero) — CONFIRMED FIXED, for the specific defect scoped.** `ClienteRepository#pesquisarGlobal`'s WHERE clause matches `{nome, numero_cliente, nif, documento_numero}` (`ClienteRepository.java:41-44`); `ResourceController.listClientes`'s `q` predicate now matches `{nome, nif, email, telefone, numero_cliente, documento_numero}` (`ResourceController.java:200-205`) — confirmed a strict superset. Confirmed `Cliente.numeroCliente`/`Cliente.documentoNumero` are both `String` (`Cliente.java:53,66`), so no type mismatch. Confirmed no tenant-isolation regression: the new fields are additional OR-branches inside a predicate applied only after `clienteRepository.findByTenantId(tenantId)` plus a redundant `.filter(c -> c.getTenantId().equals(tenantId))` — both pre-existing tenant gates are untouched. Confirmed no SQL-injection surface: this is pure in-memory Java `String.contains()` filtering, not a SQL predicate. Grepped `backend/src/test` for any test of `listClientes`/`/api/v1/clientes` — none exists, so nothing could have regressed. **However**, this fix does not close every gap the original CR-02 text itself named — see new WR-01 below (the "secondary, lesser" accent-folding gap the original finding explicitly flagged but did not include in its concrete diff).
3. **WR-01/old (documentos filter omitted tipo) — CONFIRMED FIXED, for the specific defect scoped.** `DocumentoRepository#pesquisarGlobal` matches `unaccent(d.nome) ... OR unaccent(d.tipo) ...` (`DocumentoRepository.java:30-31`); `documentos/page.tsx`'s `documentosVisiveis` filter now checks `(d.nome ?? "").toLowerCase().includes(termo) || (d.tipo ?? "").toLowerCase().includes(termo)` (`documentos/page.tsx:116-129`) — confirmed the `tipo` omission is closed. **However**, same residual pattern as CR-02 — see new WR-02 below.
4. **WR-02/old (`readRecents()` shape validation) — CONFIRMED FIXED.** `search-recents.ts`'s `readRecents()` now filters parsed JSON through a type guard requiring `tipo ∈ VALID_TIPOS` plus `id`/`titulo`/`rota` all being strings (`search-recents.ts:20-43`). Confirmed `VALID_TIPOS`'s 4 hardcoded values exactly match `PesquisaResultadoTipo`'s current union (`web/src/types/search.ts:1`) — no under- or over-inclusion today. Confirmed the guard correctly rejects `null`, non-object, and array entries (arrays are `typeof "object"` in JS, but lack a `.tipo` property, so `VALID_TIPOS.has(undefined)` correctly evaluates `false`). Minor duplication noted — see new IN-02.
5. **WR-03/old (silent blocked navigation + recents pollution) — CONFIRMED FIXED.** `navigate()` now shows `toast.error(...)` before returning on a blocked `rota` (`global-search-dialog.tsx:143-155`); `onSelectResult` now checks `isInternalLinkUrl` itself and skips `pushRecent` on a blocked result (`global-search-dialog.tsx:157-167`). Independently re-verified `isInternalLinkUrl`'s own implementation (`web/src/lib/notificacao-categoria.ts:106-113`): it resolves the candidate against a fixed sentinel base via the real WHATWG `URL` parser and compares `.origin` — sound, and unaffected by this fix. Minor redundant double-invocation noted — see new IN-03.
6. **WR-04/old (Ver-Todos re-seed used q-equality) — CONFIRMED FIXED, correctly handles both required scenarios.** `onSelectVerTodos` now appends `&_seed=${Date.now()}` (`global-search-dialog.tsx:169-175`), and all 4 list pages key their seeding guard off `const seedKey = seedNonce ?? seededQ` instead of `seededQ` alone (`clientes/page.tsx:77-83`, `processos/page.tsx:68-74`, `documentos/page.tsx:71-84`, `pareceres/page.tsx:69-79`). Traced both scenarios named in the task: (a) **nonce-bearing "Ver Todos" clicks** — `seedNonce` is a fresh `Date.now()` string on every click, so `seedKey` differs from the page's stored seed-key on every repeated click regardless of whether the search text is identical, correctly re-seeding every time; (b) **bare bookmarked/hand-typed `?q=` URL with no nonce** — `seedNonce` is `null`, so `seedKey` falls back to `seededQ`; on first load the page's stored seed-key state is `null` (its initializer), so `seedKey !== null` is true and the field seeds correctly exactly once, then stays put on subsequent renders where `searchParams` hasn't changed (matching the pre-existing "don't fight the user's own edits" behavior) — neither case regresses the other. This also confirms the fix report's own stated deviation from the original review's literal code snippet (which would have broken case (b) by comparing `seedNonce` alone against a nonce-only tracking variable) was the correct call. `onSelectVerTodos`'s generated URL (`/${segment}?q=...&_seed=...`) was also confirmed to always pass through `navigate()`'s (WR-03's) `isInternalLinkUrl` gate uneventfully, since `segment` is always one of 4 fixed literal strings and `encodeURIComponent` cannot introduce a `/`, `\`, or other authority-changing character into the query string.

No new Critical-severity issues were found in any of the 3 backend files (tenant isolation, parameterization, and `@PreAuthorize` gates on `listClientes`/`pesquisarSolicitacoes` are all intact) or the 6 frontend files (no `dangerouslySetInnerHTML`, `eval`, or hardcoded secrets introduced by any of the 6 fixes).

## Warnings

### WR-01: "Ver Todos os Clientes" still can't find an accented `nome` match the global search found — CR-02 fixed the missing fields, not the accent-folding gap its own text also flagged

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:143-146, 200`
**Issue:** `ClienteRepository#pesquisarGlobal`'s `nome` branch is `unaccent()`-folded (`ClienteRepository.java:41`: `unaccent(c.nome) ILIKE unaccent(...)`), and the project's own `PesquisaRepositoryIT` explicitly proves this works (`pesquisarGlobal_cliente_ignoraDiacriticos_ConceicaoEncontraNomeComCedilha`: searching `"Conceicao"` finds a client named `"Maria da Conceição"`). But `listClientes`'s `contains()` helper (`ResourceController.java:143-146`) does plain `value.toLowerCase().contains(queryLower)` — no diacritic folding. So the exact scenario the test proves works in the palette (typing an unaccented term to find an accented name) still silently disappears on "Ver Todos os Clientes" click-through. The original CR-02 finding explicitly named this ("nome is also affected by a secondary, lesser gap..."), and the applied fix's own report confirms it was consciously left out ("Did not additionally implement the parenthetical accent-folding suggestion"). Given Cape Verde/Portuguese names routinely carry diacritics (Conceição, José, António), this is a real, if narrower, residual slice of the original defect class. (Note: `listProcessos`'s own `q` filter, `ResourceController.java:973-981`, has the same non-folding characteristic for its `descricao`/`tribunal`/`area_juridica`/`tipo_processo` fields versus `ProcessoRepository#pesquisarGlobal`'s `unaccent()` branches — pre-existing from Phase 111, not part of this phase's diff, and not flagged in the original review, but a future accent-folding pass should probably cover all three list pages together.)
**Fix:** Normalize diacritics on both sides before comparing, e.g.:
```java
import java.text.Normalizer;

private static String foldAccents(String value) {
    if (value == null) return null;
    return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
}

private static boolean contains(String value, String queryLower) {
    if (value == null) return false;
    return foldAccents(value.toLowerCase()).contains(foldAccents(queryLower));
}
```
(apply `foldAccents` to `qNorm` once up front too), or move this predicate server-side into a query that reuses `ClienteRepository`'s own `unaccent()` expression so the two paths can't drift again.

### WR-02: "Ver Todos os Documentos" still can't find an accented nome/tipo match — same residual gap as WR-01, scoped to Documentos

**File:** `web/src/app/(dashboard)/documentos/page.tsx:116-129`
**Issue:** `DocumentoRepository#pesquisarGlobal` unaccent-folds both its `nome` and `tipo` branches (`DocumentoRepository.java:30-31`). The client-side filter this phase's WR-01 fix added still does plain, non-folded substring matching:
```ts
return termo
  ? base.filter(
      (d) =>
        (d.nome ?? "").toLowerCase().includes(termo) ||
        (d.tipo ?? "").toLowerCase().includes(termo),
    )
  : base;
```
Same consequence as WR-01 above, for a documento's `nome`/`tipo` instead of a cliente's `nome`. The original WR-01 finding's own text already named this ("Accent-folding still won't match the backend's `unaccent()` exactly... worth doing here too since this is the one page where the mismatch is otherwise cheap to close entirely client-side"), and the fix report confirms it was consciously deferred, same reasoning as CR-02.
**Fix:** Since this filter is already 100% client-side (no round-trip needed to close it), this is the cheapest of the pages to fully fix:
```ts
const fold = (s: string) => s.normalize("NFD").replace(/\p{Diacritic}/gu, "").toLowerCase();

const documentosVisiveis = React.useMemo(() => {
  const termo = fold(nomeFiltro.trim());
  const base = list.data ?? [];
  return termo
    ? base.filter(
        (d) => fold(d.nome ?? "").includes(termo) || fold(d.tipo ?? "").includes(termo),
      )
    : base;
}, [list.data, nomeFiltro]);
```

## Info

### IN-01: `escapeLike` is now duplicated verbatim across two controllers instead of being shared

**File:** `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java:50-52`, `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:104-106`
**Issue:** CR-01's fix added a second, byte-for-byte-identical `private static String escapeLike(String termo)` to `ParecerPesquisaController` rather than reusing `PesquisaController`'s existing one. Both classes live in the same package (`com.lexcv.controllers`), so simply dropping the `private` modifier on `PesquisaController#escapeLike` (making it package-private) would have let `ParecerPesquisaController` call it directly — a one-keyword diff, smaller than the 12+ lines of duplicated method + Javadoc actually added, and without the "widen to public" or "new shared utility class" trade-offs the fix report weighed against. As written, a future correction to the escaping logic (e.g. a third metacharacter needing escaping) requires remembering to update two copies in sync.
**Fix:** Remove the `private` modifier from `PesquisaController#escapeLike` and delete `ParecerPesquisaController`'s copy (or extract both into a small shared `IlikeUtils` class if a cleaner boundary is preferred).

### IN-02: `search-recents.ts` keeps two independently-hardcoded copies of the same 4-value tipo list

**File:** `web/src/lib/search-recents.ts:5-10, 18`
**Issue:** `RECENT_ELIGIBLE_TIPOS` (pre-existing) and the new `VALID_TIPOS` (WR-02/old's fix) both hardcode the identical literal list `["cliente", "processo", "documento", "parecer"]` in the same file. Confirmed they agree with each other and with `PesquisaResultadoTipo` (`web/src/types/search.ts:1`) today, but nothing enforces that they stay in sync if the type ever gains/loses a member — exactly the kind of drift `VALID_TIPOS`'s own comment warns about for the type itself.
**Fix:**
```ts
const VALID_TIPOS = new Set<PesquisaResultadoTipo>(RECENT_ELIGIBLE_TIPOS);
```

### IN-03: `onSelectResult` and `navigate` both call `isInternalLinkUrl` on the same `rota`

**File:** `web/src/components/shared/global-search-dialog.tsx:143-167`
**Issue:** WR-03/old's fix added an `isInternalLinkUrl(resultado.rota)` check inside `onSelectResult` (to decide whether to skip `pushRecent`), but `navigate()` — called unconditionally from both of `onSelectResult`'s branches — already performs the identical check on the same value. The check runs twice on every valid selection. `isInternalLinkUrl` is pure/side-effect-free, so this isn't a correctness bug, but it duplicates validation logic across two call sites that could silently drift apart if one is updated without the other.
**Fix:**
```ts
function onSelectResult(resultado: ResultadoPesquisa) {
  if (isInternalLinkUrl(resultado.rota)) {
    pushRecent(resultado);
  }
  navigate(resultado.rota); // still validates + shows the toast itself
}
```

---

_Reviewed: 2026-07-21T20:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
