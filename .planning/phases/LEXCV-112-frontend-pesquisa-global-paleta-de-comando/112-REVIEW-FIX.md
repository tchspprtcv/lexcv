---
phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando
fixed_at: 2026-07-21T18:50:57Z
review_path: .planning/phases/LEXCV-112-frontend-pesquisa-global-paleta-de-comando/112-REVIEW.md
iteration: 1
findings_in_scope: 7
fixed: 6
skipped: 1
status: partial
---

# Phase LEXCV-112: Code Review Fix Report

**Fixed at:** 2026-07-21T18:50:57Z
**Source review:** .planning/phases/LEXCV-112-frontend-pesquisa-global-paleta-de-comando/112-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 7 (CR-01, CR-02 — both Critical findings; WR-01 through WR-05 — all 5 Warning findings; Info findings IN-01 through IN-05 explicitly out of scope for this run)
- Fixed: 6
- Skipped: 1

## Fixed Issues

### CR-01: "Ver Todos os Pareceres" can show zero results for a solicitação the global search just found — it searches a different field on a different table

**Files modified:** `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java`, `backend/src/main/java/com/lexcv/controllers/ParecerPesquisaController.java`
**Commit:** `c35f8d6`
**Applied fix:** Rewrote `ParecerSolicitacaoRepository.pesquisar()`'s final `AND` predicate to OR an `unaccent(s.descricao) ILIKE unaccent(...) ESCAPE '\'` branch alongside the existing (now also `unaccent`-folded, `ESCAPE`-paired) `v.conteudo` branch — matching REVIEW.md's suggested diff exactly, applied at the current line (the code matched the reviewed context verbatim). Since `escapeLike` is `private static` on `PesquisaController` (a different class) and this repository method has exactly one call site (`ParecerPesquisaController.pesquisarSolicitacoes`, confirmed via project-wide grep — no other production caller exists), added an equivalent `private static escapeLike` helper directly to `ParecerPesquisaController` (mirroring `PesquisaController#escapeLike`'s implementation and Javadoc verbatim, same convention as every other `*Repository#pesquisarGlobal` caller in this codebase) and used it to pre-escape `texto` (null-safe) before the call, instead of widening `PesquisaController#escapeLike`'s visibility or introducing a new shared utility class — the narrower, minimal-diff option. Added Javadoc/inline comments cross-referencing "CR-01 (Phase 112 code review)" to disambiguate from a pre-existing, differently-scoped "CR-01" comment already in this file (from an earlier Phase 90 review, about the `LEFT JOIN` choice).

**Verification:** Confirmed no other call site of `ParecerSolicitacaoRepository.pesquisar()` or `ParecerPesquisaController` exists in `backend/src/test` (grep, zero matches) — the escaping/query change cannot break an existing test. `mvn -q -o compile` → `BUILD SUCCESS` (offline, no errors).

**Note (logic-verification flag):** This is a SQL predicate/query-logic change. Tier 1 (re-read) and the compile check only confirm syntactic/structural correctness, not that the Postgres `ILIKE ... ESCAPE '\'` + `unaccent()` semantics behave exactly as intended against real data (no Postgres instance was available to execute this query in this environment). Flagged **`fixed: requires human verification`**.

### CR-02: "Ver Todos os Clientes" omits the two fields the global search ranks highest — a structured-ID match shows zero results

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `e699575`
**Applied fix:** Added `contains(c.getNumeroCliente(), qNorm) || contains(c.getDocumentoNumero(), qNorm)` to `listClientes`'s `q` filter predicate (lines 194-200, matched REVIEW.md's cited context verbatim), matching the exact diff REVIEW.md suggested. Did not additionally implement the parenthetical accent-folding suggestion for `nome` ("consider normalizing accents on both sides") — that is explicitly framed in REVIEW.md as a further consideration, not part of the concrete fix diff, and would require a broader unaccent-equivalent Java helper beyond this finding's stated scope.

**Verification:** `mvn -q -o compile` → `BUILD SUCCESS` (offline, no errors).

**Note (logic-verification flag):** This is a filter-predicate logic change (added OR branches to an existing boolean condition). Flagged **`fixed: requires human verification`** per the same reasoning as CR-01 — syntax/compile checks cannot confirm the runtime field-matching behavior against real data.

### WR-01: "Ver Todos os Documentos" client-side filter omits `tipo` and doesn't accent-fold

**Files modified:** `web/src/app/(dashboard)/documentos/page.tsx`
**Commit:** `f91f013`
**Applied fix:** Applied REVIEW.md's exact suggested diff — `documentosVisiveis`'s filter predicate now also checks `(d.tipo ?? "").toLowerCase().includes(termo)` alongside the existing `nome` check. Did not implement the parenthetical accent-folding suggestion, same reasoning as CR-02 (explicitly framed as a further "worth doing" consideration, not the concrete diff).

**Verification:** `pnpm exec tsc --noEmit` → 3 errors in 3 files, all in pre-existing unrelated `*.test.ts` files referencing the uninstalled `vitest` module (confirmed as the exact pre-existing baseline via a `tsc` run before any edits in this session, matching REVIEW.md's own noted "3 unrelated pre-existing vitest-module errors elsewhere"). No new errors in the modified file.

### WR-02: `readRecents()` performs no shape validation — an unrecognized `tipo` crashes the whole app

**Files modified:** `web/src/lib/search-recents.ts`
**Commit:** `2076a2e`
**Applied fix:** Applied REVIEW.md's exact suggested diff — added a module-level `VALID_TIPOS` set and rewrote `readRecents()` to `.filter()` parsed items through a type-guard checking `tipo` membership in that set plus `id`/`titulo`/`rota` are all strings, instead of the previous blind `parsed as ResultadoPesquisa[]` cast. Kept the existing multi-line `if (typeof window === "undefined")` brace style already used in this file (REVIEW.md's snippet condensed it to one line; preserved the surrounding file's existing formatting convention instead).

**Verification:** `pnpm exec tsc --noEmit` → same pre-existing 3-error baseline, no new errors in the modified file.

**Note (logic-verification flag):** This introduces a new validation predicate (a type guard deciding what shape counts as valid). A subtly wrong check (e.g. a missing field, wrong operator) would still compile cleanly. Flagged **`fixed: requires human verification`**.

### WR-03: Blocked navigation fails silently, and the blocked item is still recorded as a "recent"

**Files modified:** `web/src/components/shared/global-search-dialog.tsx`
**Commit:** `4e64daf`
**Applied fix:** Applied REVIEW.md's exact suggested diff — `navigate()` now calls `toast.error("Não foi possível abrir este resultado.")` before returning when `isInternalLinkUrl` rejects the route (previously a silent no-op), and `onSelectResult` now checks `isInternalLinkUrl` itself before calling `pushRecent`, skipping straight to `navigate()` (which still shows the toast) for a rejected result instead of polluting "Visitados recentemente." Added the `toast` import from `@/hooks/use-toast` (verified its `.error()`/`.success()` API shape against existing call sites elsewhere in the codebase, e.g. `clientes/page.tsx`), placed alphabetically among the existing `@/hooks/*` imports.

**Verification:** `pnpm exec tsc --noEmit` → same pre-existing 3-error baseline, no new errors in the modified file.

**Note (logic-verification flag):** This changes control flow (an early-return branch deciding when `pushRecent` runs) — REVIEW.md itself frames the underlying issue as "bad state handling," one of the explicit logic-error categories. Flagged **`fixed: requires human verification`**.

### WR-04: Re-clicking "Ver Todos" with the same search term won't re-apply the filter if the user manually cleared it in between

**Files modified:** `web/src/components/shared/global-search-dialog.tsx`, `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/processos/page.tsx`, `web/src/app/(dashboard)/documentos/page.tsx`, `web/src/app/(dashboard)/pareceres/page.tsx`
**Commit:** `49550aa`
**Applied fix:** `global-search-dialog.tsx`'s `onSelectVerTodos` now appends a one-shot `&_seed=${Date.now()}` nonce to the destination URL, applied exactly as REVIEW.md suggested. **Adapted** (not verbatim) on the four list-page side: REVIEW.md's own snippet replaces the seeding guard's comparison with `seedNonce !== lastSeedNonce` alone, dropping the prior `q`-value comparison entirely — tracing this through, that literal snippet regresses a plain, nonce-less `?q=` link (e.g. a hand-typed or bookmarked URL): on first render `seedNonce` is `null` and `lastSeedNonce` initializes to `null`, so `null !== null` is `false` and the seed would never apply. Adapted the fix to `const seedKey = seedNonce ?? seededQ` (falling back to the raw `q` value only when no nonce is present) and keyed each page's existing seed-tracking state off `seedKey` instead of the raw seeded value — this fixes the reported repeated-click bug (a fresh nonce is appended on every "Ver Todos" click, so the guard always re-seeds) while preserving old behavior for any link that doesn't carry a `_seed` param. Repurposed each page's existing seed-tracking state variable (`lastSeededQ`/`nomeFiltroSeedKey`/`pesquisaSeedKey`) to store the key instead of introducing new state, keeping the diff minimal.

**Verification:** `pnpm exec tsc --noEmit` → same pre-existing 3-error baseline, no new errors across all 5 modified files.

**Note (logic-verification flag):** REVIEW.md frames this finding's root cause as a state-key comparison bug ("bad state handling"), and the fix as applied here deliberately diverges from the review's literal snippet to avoid a regression identified during implementation. Flagged **`fixed: requires human verification`**.

## Skipped Issues

### WR-05: No automated tests for either of this phase's two security-relevant primitives

**File:** `web/src/lib/highlight-match.tsx`, `web/src/components/shared/global-search-dialog.tsx` (open-redirect guard usage)
**Reason:** REVIEW.md's own Fix section explicitly defers this: *"once `vitest` is wired up (tracked separately), add a unit suite..."* — confirmed `vitest` is not present anywhere in `web/package.json` (grep, zero matches), and `.planning/phases/LEXCV-112-frontend-pesquisa-global-paleta-de-comando/deferred-items.md` #1 independently documents this exact gap as pre-existing infrastructure debt (3 test files already reference `vitest` and fail `tsc --noEmit` with `Cannot find module 'vitest'` — the same 3-error baseline this session's other verifications ran against). Adding a 4th test file under this same broken pattern would not be a working fix: it could not compile cleanly, and there is no configured test runner that would ever execute it. This is infrastructure work (installing/configuring `vitest`) rather than a source-code fix, and is explicitly out of scope per the review's own framing. Recommend tracking as a follow-up once `vitest` is installed (a natural pairing with the pre-existing `deferred-items.md` #1 item, which affects the same 3 legacy test files).
**Original issue:** This review had to write a throwaway Node script to empirically verify `isInternalLinkUrl`'s bypass resistance and manually trace `highlight-match.tsx` to confirm no `dangerouslySetInnerHTML` path exists — there is no committed regression test doing either, despite `notificacao-categoria.ts`'s own header comment documenting this exact guard has already been bypassed and reopened twice before.

---

_Fixed: 2026-07-21T18:50:57Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
