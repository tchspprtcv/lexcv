---
phase: LEXCV-111-backend-pesquisa-global-cross-entity-api
fixed_at: 2026-07-19T01:10:10Z
review_path: .planning/phases/LEXCV-111-backend-pesquisa-global-cross-entity-api/111-REVIEW.md
iteration: 1
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-111: Code Review Fix Report

**Fixed at:** 2026-07-19T01:10:10Z
**Source review:** .planning/phases/LEXCV-111-backend-pesquisa-global-cross-entity-api/111-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 2 (WR-01, WR-02 — the 2 Warning findings; IN-01 explicitly left untouched per fix scope)
- Fixed: 2
- Skipped: 0

## Fixed Issues

### WR-01: `truncarDescricao`'s guard still compares UTF-16 length while the (now-fixed) truncation itself is codepoint-based

**Files modified:** `backend/src/main/java/com/lexcv/controllers/PesquisaController.java`, `backend/src/test/java/com/lexcv/controllers/PesquisaControllerTest.java`
**Commit:** `077f78b`
**Applied fix:** Rewrote `truncarDescricao` (PesquisaController.java) to derive the "was it truncated" decision from comparing `truncateSafely`'s own result (`truncado`) against the pre-truncation string (`trimmed`), instead of a separately-computed `trimmed.length() > DESCRICAO_PREVIEW_LENGTH` (UTF-16 code-unit) pre-check — matching the fix suggested in REVIEW.md exactly, since the current code matched the review's cited context verbatim. Added a Javadoc comment above the method explaining the unit-mismatch this closes, consistent with the file's existing per-finding documentation convention (see the WR-01/WR-02/WR-03 Javadocs already on `truncateSafely`, `escapeLike`, and `pesquisarComIsolamentoDeFalhas`).

Added two regression tests to `PesquisaControllerTest.java`:
- `pesquisar_comDescricaoDeOitentaCodepointsIncluindoEmoji_naoTruncaENaoAdicionaReticencias` — reproduces the reviewer's exact repro case (79 `'a'` chars + 1 supplementary-plane emoji = 80 codepoints / 81 UTF-16 units) and asserts the result is the original text unchanged, with no spurious `"..."` appended. The emoji is constructed via `new String(Character.toChars(0x1F600))` (from the raw codepoint) rather than a source-literal glyph or `\u` escape, to stay independent of the project's (unset) `project.build.sourceEncoding`.
- `pesquisar_comDescricaoAcimaDoLimiteDeOitentaCodepoints_truncaEAdicionaReticencias` — companion test proving genuine truncation (85 plain-ASCII codepoints) still removes characters and appends `"..."` after the guard rewrite, i.e. the fix doesn't overcorrect into never truncating.

Neither test previously existed — REVIEW.md's own investigation confirmed no `codePoint`/`surrogate`/emoji-literal reference existed anywhere in the test suite before this fix.

**Verification:** `mvn test -Dtest=PesquisaControllerTest` → 11/11 passing (9 pre-existing + 2 new), 0 failures/errors.

### WR-02: The ILIKE-escaping fix has no integration-level (real PostgreSQL) regression test

**Files modified:** `backend/src/test/java/com/lexcv/repositories/PesquisaRepositoryIT.java`
**Commit:** `f69a62d`
**Applied fix:** Added two new IT tests (Testcontainers, real `postgres:16-alpine`) to `PesquisaRepositoryIT.java`:

1. `pesquisarGlobal_termoComUnderscoreLiteral_naoAgeComoCoringaDeUmCaractereEmNenhumDosQuatroTipos` — a shared cross-entity test (the "one shared case exercising all four" alternative REVIEW.md's Fix section explicitly sanctions, mirroring the existing `pesquisarGlobal_isolaPorTenant_...` cross-entity pattern) covering all four repositories named in the finding (`ClienteRepository`, `ProcessoRepository`, `DocumentoRepository`, `ParecerSolicitacaoRepository`). For each entity type it seeds a "decoy" row (e.g. `numero_cliente = "ESCX9k"`) that would match a search for `"ESC_9k"` **only** if the ILIKE engine treated the literal `_` in the search term as its built-in "any single character" wildcard, alongside a target row genuinely containing the literal underscore (`"ESC_9k"`). Asserts exactly 1 result (the target) per entity type, and explicitly asserts the decoy's id is never present. If the `ESCAPE '\'` clause were ever dropped, or `PesquisaController#escapeLike` stopped being applied before the repository call, the decoy would also match and the test would fail with 2 results instead of 1 — this is the discriminating behavior the existing 4 IT tests lacked (they use search terms like `"0042"`/`"PROC-7777"` with no wildcard metacharacters, so escaped and unescaped values are byte-identical and cannot distinguish correct from broken escaping).
2. `pesquisarGlobal_cliente_correspondenciaExataComPercentLiteral_usaTermoBrutoNaoEscapadoNoCaseWhen` — targets REVIEW.md's "Additionally" clause: proves the tier-0 exact-match `CASE WHEN` genuinely needs the raw, unescaped `:termo` (not `:termoEscapado`). Seeds `numero_cliente = "100%"` (literal `%` as data) plus a `"100%EXTRA"` prefix decoy. If tier-0 ever used `:termoEscapado` for its equality check, `"100%"` would never equal `"100\%"`, silently demoting the exact row to a tier-1/tier-1 tie with the decoy, which the `created_at DESC` tie-break would then sort first — flipping `resultados.get(0)` away from the exact match. Under the current (correct) implementation, the exact row is unambiguously tier 0 and always sorts first regardless of creation order or the `Thread.sleep(5)` inserted between the two `save()` calls (added solely to make the tie-break timestamp deterministic in the hypothetical-regression branch, not needed for the current-correct-code pass path).

**Verification:** Per task instructions, this IT suite cannot be executed in this environment (Docker Desktop unavailable — Testcontainers requires a Docker daemon). Verified via `mvn -DskipTests test-compile` → `BUILD SUCCESS`, confirming both new tests compile cleanly against the existing `ClienteRepository`/`ProcessoRepository`/`DocumentoRepository`/`ParecerSolicitacaoRepository`/`Cliente`/`Processo`/`Documento`/`ParecerSolicitacao` signatures. Field constraints were checked by reading each JPA entity (`Cliente.nif` has `@Pattern(regexp = "^\\d{9}$")`, so all new/existing NIFs used are valid 9-digit strings; `numero_cliente`/`numero_processo`/`nome`/`descricao` have no pattern constraints, so literal `%`/`_` characters are safe there) before writing the seed data, and query semantics (which columns each `ILIKE` branch touches, `unaccent()` wrapping, `ESCAPE '\'` placement) were traced against the actual current `@Query` text in all four repository files, not assumed from REVIEW.md's summary. This test is written to run unattended in CI (`ubuntu-latest`, which has Docker) per the task's instructions.

**Note (logic-verification flag):** Both fixes are logic changes (a ranking/truncation guard rewrite and new integration-test assertions), and the WR-02 tests specifically could not be executed against a real database in this environment. Per the fixer's verification-strategy rules, this is flagged as `"fixed: requires human verification"` for WR-02's IT tests specifically — the compile check confirms structural correctness, but the actual PostgreSQL `ILIKE ... ESCAPE '\'` behavior these tests assert on has not been empirically re-confirmed by running them. CI (`ubuntu-latest`) should be the first real execution of `PesquisaRepositoryIT` including these two new tests. WR-01 was fully executed and passed (11/11 including both new tests), so it does not carry this caveat.

## Skipped Issues

None — both in-scope findings (WR-01, WR-02) were fixed. IN-01 was intentionally left untouched per the fix scope given for this run (minor/cosmetic, not requested).

---

_Fixed: 2026-07-19T01:10:10Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
