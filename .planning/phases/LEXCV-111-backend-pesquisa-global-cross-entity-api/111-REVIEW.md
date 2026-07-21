---
phase: LEXCV-111-backend-pesquisa-global-cross-entity-api
reviewed: 2026-07-19T00:49:48Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - backend/migrations/111-enable-search-extensions.sql
  - backend/src/test/java/com/lexcv/repositories/PesquisaRepositoryIT.java
  - backend/src/main/java/com/lexcv/repositories/ClienteRepository.java
  - backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java
  - backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java
  - backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java
  - backend/src/main/java/com/lexcv/dtos/ResultadoPesquisaDto.java
  - backend/src/main/java/com/lexcv/controllers/PesquisaController.java
  - backend/src/test/java/com/lexcv/controllers/PesquisaControllerTest.java
findings:
  critical: 0
  warning: 0
  info: 1
  total: 1
status: clean
closed_at: 2026-07-20T00:00:00Z
closed_by: orchestrator (independent verification after fixer agent crashed post-commit)
---

## Closure Note (2026-07-20)

WR-01 and WR-02 (below) were fixed by commits `077f78b` and `f69a62d` (see `111-REVIEW-FIX.md`). The fixer agent crashed (401 OAuth token expiry) after committing but before its own final report/cleanup, so its "all_fixed" claim was independently re-verified rather than trusted at face value:

- Read `PesquisaController.java:251-258` directly — `truncarDescricao` now derives the truncation decision from `truncado.length() < trimmed.length()`, matching the suggested fix exactly (WR-01 closed).
- Confirmed both new test methods exist: `PesquisaControllerTest`'s codepoint/emoji regression test, and `PesquisaRepositoryIT`'s `pesquisarGlobal_termoComUnderscoreLiteral_...` + `pesquisarGlobal_cliente_correspondenciaExataComPercentLiteral_...` (WR-02 closed).
- Independently re-ran (not reused from any agent's self-report): `mvn -Dtest=PesquisaControllerTest test` → 11/11 pass; full `mvn test` → 84/84 pass, `BUILD SUCCESS`; `mvn -DskipTests test-compile` → clean (proves `PesquisaRepositoryIT`'s new tests compile against real repository signatures).
- `PesquisaRepositoryIT` (Testcontainers) still cannot execute locally (no Docker daemon in this environment) — unchanged, pre-existing, CI-gated limitation (see Phase 91 history). Its two new tests are structurally verified only; first real execution will be in CI.

IN-01 (duplicate exception logging) remains open, unfixed by design (cosmetic, out of scope for this iteration).

# Phase LEXCV-111: Code Review Report

**Reviewed:** 2026-07-19T00:49:48Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

This is a **re-review** verifying the three fix commits applied against the prior `111-REVIEW.md` (`d41b1c1` WR-01 codepoint-safe truncation, `657d45e` WR-02 ILIKE wildcard escaping, `536582c` WR-03 per-branch fault isolation). The brief specifically asked to confirm (a) the dual `termo`/`termoEscapado` parameter split in `ClienteRepository`/`ProcessoRepository` didn't break exact-match ranking, and (b) the per-branch `DataAccessException` catch in `PesquisaController` doesn't swallow tenant-isolation or RBAC-relevant errors. Both were independently re-derived from first principles, not assumed from the fix commits' own doc comments.

**Methodology:** full re-read of all 9 files; a line-by-line trace of every `:termo` vs `:termoEscapado` usage across all four native `pesquisarGlobal` queries; `git show` on each of the 3 fix commits in isolation to distinguish newly-introduced code from pre-existing code; two standalone Java reproductions (compiled and executed outside the project) to empirically verify `PesquisaController#escapeLike`'s and `#truncateSafely`'s exact character-level output rather than relying on manual reasoning alone; and `mvn -o test -Dtest=PesquisaControllerTest` (9/9 passed, confirms the changed method signatures are mutually consistent across controller/test/repository-interface). The Testcontainers `PesquisaRepositoryIT` suite could **not** be executed in this sandbox (Docker daemon not running) — its correctness is assessed by static SQL-text inspection only; this factors into WR-02 below.

**Verification verdict on the two specifically-requested checks:**

- **Dual `termo`/`termoEscapado` split does not break exact-match ranking — CONFIRMED.** In both `ClienteRepository.pesquisarGlobal` and `ProcessoRepository.pesquisarGlobal`, the bare `:termo` parameter is referenced **only** inside the tier-0 `CASE WHEN lower(...) = lower(CAST(:termo AS text))` exact-equality branches (3 occurrences in `ClienteRepository`, 1 in `ProcessoRepository`) — never inside a wildcard-wrapped `ILIKE`. Every other occurrence, in the `WHERE` clause and in the tier-1/tier-2 prefix `CASE WHEN` branches, correctly uses `:termoEscapado`. The controller call sites (`PesquisaController.java:128, 132`) pass `(tenantId, termoFinal, termoEscapado, LIMITE_POR_TIPO)` in the order the method signatures declare `(tenantId, termo, termoEscapado, limit)` — argument order/positions are not swapped. Since an exact match (`numero_cliente == termo`) trivially satisfies the `WHERE ... ILIKE '%'||escaped(termo)||'%'` substring filter regardless of escaping, exact-tier rows are never excluded by the WHERE clause either. Ranking tiers (0=exact, 1=structured-ID prefix, 2/3=free-text) are unchanged in shape from before the fix.
- **Per-branch `DataAccessException` catch does not swallow tenant-isolation or RBAC-relevant errors — CONFIRMED.** `hasAuthority(auth, "<scope>:view")` is a plain boolean check evaluated in the `if` **before** `pesquisarComIsolamentoDeFalhas(...)` is ever invoked (`PesquisaController.java:126-141`) — a caller lacking a scope never enters the try/catch for that branch at all, exactly as before this fix. `@PreAuthorize("isAuthenticated()")` is enforced by a method-interceptor proxy *around* the whole `pesquisar()` call, so an `AccessDeniedException` from that check is thrown before the method body (and thus the try/catch) is ever reached; `org.springframework.security.access.AccessDeniedException` is not a subtype of `org.springframework.dao.DataAccessException` regardless. Tenant isolation is enforced structurally by the `WHERE tenant_id = :tenantId` predicate in each query — there is no runtime condition under which "tenant isolation was bypassed" would itself *throw* a `DataAccessException`; a broken tenant predicate would silently return wrong rows, a class of bug this catch block has no interaction with either way. `getTenantId()` (`PesquisaController.java:121`) runs before any branch and is not wrapped by the new try/catch, so a bad/missing principal still fails the whole request rather than degrading silently.

**WR-01 (truncation) verdict:** the specific failure mode described in the original finding — `substring()` splitting a UTF-16 surrogate pair and producing an unpaired surrogate that corrupts the JDBC bind value or makes Jackson reject JSON serialization — is genuinely fixed. `truncateSafely` (`PesquisaController.java:85-91`) uses `codePointCount`/`offsetByCodePoints`, which can never return an index inside a surrogate pair, and is applied at both of the originally-flagged call sites. However, tracing the *caller* of `truncateSafely` in `truncarDescricao` surfaced a new, narrower defect this fix leaves behind — see WR-01 below (deliberately kept under the same ID as the original since it is the direct continuation of the same call site, not a new topic).

**WR-02 (ILIKE escaping) verdict:** the escaping logic itself is correct — verified by an exhaustive scan of all 8+ `ILIKE` usages of `:termoEscapado` across `ClienteRepository`/`ProcessoRepository`/`DocumentoRepository`/`ParecerSolicitacaoRepository`, every one of which is paired with a matching `ESCAPE '\\'` clause, and by compiling and running `escapeLike` standalone against `"100%_off"`, `"a\\b"`, `"%%"`, `"_"`, `"CONTR_ATO"`, `"50\\%"` — the backslash-first ordering prevents any escape-sequence bypass. The gap is that none of this is proven against a real PostgreSQL instance — see WR-02 below.

**WR-03 (fault isolation) verdict:** correctly and narrowly scoped to the four repository calls; does not affect RBAC or tenant-isolation logic (see confirmation above). No Critical/Blocker findings resulted from this re-review.

IN-01/IN-02/IN-03 from the prior review (duplicate `Authentication` fetch, `ResponseEntity<?>` return type, nullable-field blank titulo/subtitulo) were left unfixed by design per this iteration's scope and are not re-flagged here.

## Warnings

### WR-01: `truncarDescricao`'s guard still compares UTF-16 length while the (now-fixed) truncation itself is codepoint-based — appends "..." to text that was never actually truncated

**File:** `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:246-247` (guard/call site), depends on `truncateSafely` at `:85-91`

**Issue:** This is a regression the WR-01 fix itself introduces, not a pre-existing condition. Before the fix, both the truncation *decision* (`trimmed.length() > DESCRICAO_PREVIEW_LENGTH`) and the truncation *action* (`trimmed.substring(0, DESCRICAO_PREVIEW_LENGTH)`) used the same unit (UTF-16 code units), so they were always consistent: if the guard said "too long," the substring call always genuinely removed characters. The fix swapped only the *action* to codepoint-aware `truncateSafely`, leaving the *guard* on `String.length()` (UTF-16 units):

```java
String trimmed = descricao.trim();
return trimmed.length() > DESCRICAO_PREVIEW_LENGTH                          // guard: UTF-16 units
        ? truncateSafely(trimmed, DESCRICAO_PREVIEW_LENGTH) + "..."         // action: codepoint units
        : trimmed;
```

`truncateSafely` internally decides "no truncation needed" using `codePointCount(...) <= maxLength`. Whenever a `descricao` (trimmed) has a **codepoint count `<= 80` but a UTF-16 length `> 80`** — which requires at least one supplementary-plane character (e.g. an emoji) among the first ~80 codepoints — the outer guard fires (`length() > 80` is true) but `truncateSafely` returns the string **unchanged** (no codepoints removed), and `"..."` is appended to the full, non-truncated text. The result is longer than the intended 80-codepoint preview bound and carries a misleading trailing ellipsis implying content was cut when none was.

Empirically reproduced by extracting the exact logic into a standalone class and running it against a string of 79 `'a'` characters plus one `U+1F600` emoji (80 codepoints, 81 UTF-16 units):

```
codePointCount = 80
UTF-16 length  = 81
result ends with '...' ? true
result equals input+'...' ? true      <- full text, unmodified, plus a spurious "..."
result codePointCount = 83            <- 3 codepoints over the intended 80-codepoint bound
```

Neither test file exercises a supplementary-plane character anywhere (confirmed via search — no `codePoint`/`surrogate`/emoji-literal reference in either `PesquisaControllerTest.java` or `PesquisaRepositoryIT.java`), so this is untested.

**Fix:** derive the "was it truncated" decision from the same call, instead of a separately-computed, unit-mismatched pre-check:

```java
private String truncarDescricao(String descricao) {
    if (descricao == null) {
        return "";
    }
    String trimmed = descricao.trim();
    String truncado = truncateSafely(trimmed, DESCRICAO_PREVIEW_LENGTH);
    return truncado.length() < trimmed.length() ? truncado + "..." : truncado;
}
```
This compares `truncado` against `trimmed` directly (both `String.length()`, but of the *same* string before/after the *same* truncation call), which is unit-consistent by construction and can never fire spuriously.

### WR-02: The WR-02 escaping fix has no integration-level (real PostgreSQL) regression test — both the wildcard-neutralization claim and the exact-match-tier's use of the raw `termo` are unverified against actual `ILIKE ... ESCAPE` semantics

**File:** `backend/src/test/java/com/lexcv/repositories/PesquisaRepositoryIT.java` (no new test added by commit `657d45e` — diff is a mechanical 4-arg signature update to 4 pre-existing tests only); queries under test: `ClienteRepository.java:39-58`, `ProcessoRepository.java:33-48`, `DocumentoRepository.java:28-38`, `ParecerSolicitacaoRepository.java:75-81`

**Issue:** The only regression test added for WR-02 is `PesquisaControllerTest#pesquisar_comCoringasIlikeNoTermo_escapaTermoParaRepositoriosMasPreservaOriginalParaRanking`, which is a pure-Mockito test — it asserts what Java string `escapeLike("100%_off")` produces (`"100\%\_off"`), but the repository call is mocked, so **no SQL is ever executed** and the `ESCAPE '\\'` clauses in the four `@Query` strings are never parsed or run against a real engine by any test in this codebase.

This matters because `PesquisaRepositoryIT` — the file that exists specifically to validate Postgres-specific SQL semantics against a real `postgres:16-alpine` Testcontainers instance (per its own class Javadoc) — was touched by the WR-02 commit but only to update 4 pre-existing tests' call arity from 3 to 4 args; every one of those tests passes the **same literal value** for both `termo` and `termoEscapado` (e.g. `clienteRepository.pesquisarGlobal(tenantId, termo, termo, 5)` with `termo = "0042"` or `"PROC-7777"` — no `%`/`_` present in either). Because raw and escaped values are identical whenever the input contains no wildcard metacharacters, **none of the existing IT tests can distinguish a correct implementation from a subtly-wrong one** that, say, swapped `:termo`/`:termoEscapado` inside the ranking `CASE WHEN` tiers, or has an off-by-one in the `ESCAPE` clause placement — they would still pass. Static text inspection (performed in this review) found the escaping logically sound and consistently applied, but that is manual reasoning, not proof against actual Postgres.

**Fix:** add at least one IT case per repository (or one shared case exercising all four, matching the existing `pesquisarGlobal_isolaPorTenant_...` cross-entity pattern) with a `termo` containing a literal `%` or `_`, asserting the wildcard is matched literally and does not over-match unrelated rows, e.g.:

```java
@Test
void pesquisarGlobal_cliente_termoComPercentLiteralNaoAgeComoCoringa() {
    UUID tenantId = UUID.randomUUID();
    persistirCliente(tenantId, "Cliente Comum", "CLI-9001", "700800900", "DOC-ESC-1");
    Cliente alvo = persistirCliente(tenantId, "Promoção 100% Legal", "CLI-9002", "700800901", "DOC-ESC-2");

    String termo = "100%";
    String termoEscapado = "100\\%"; // mirrors PesquisaController#escapeLike

    List<Cliente> resultados = clienteRepository.pesquisarGlobal(tenantId, termo, termoEscapado, 5);

    assertEquals(1, resultados.size());
    assertEquals(alvo.getId(), resultados.get(0).getId());
}
```
Additionally, extend one exact-match-ranking test (e.g. the `numero_cliente` one) so the stored exact value itself contains a `%`/`_`, proving the tier-0 `CASE WHEN` genuinely needs the raw `:termo` and not `:termoEscapado`.

## Info

### IN-01: Exception logged twice in the same log record

**File:** `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:161-164`

**Issue:** `logger.error(format, tipo, ex.toString(), ex)` passes `ex.toString()` as a `{}` placeholder value *and* `ex` itself as a trailing argument. SLF4J recognizes the trailing `Throwable` (since there are more arguments than `{}` placeholders) and appends its full stack trace — which itself starts by printing `ex.toString()`. The exception's description therefore appears twice in the emitted log record (once inline in the message, once at the head of the stack trace).

**Fix:** drop the inline `ex.toString()` placeholder and let the trailing `Throwable` argument do it once:
```java
logger.error("Search failed for entity type '{}' in GET /api/v1/pesquisa (branch isolated " +
        "from the other three). Most likely cause: the PostgreSQL 'unaccent' extension is not " +
        "installed (see backend/migrations/111-enable-search-extensions.sql).",
        tipo, ex);
```

---

_Reviewed: 2026-07-19T00:49:48Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
