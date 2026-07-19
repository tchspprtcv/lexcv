---
phase: LEXCV-111-backend-pesquisa-global-cross-entity-api
reviewed: 2026-07-18T23:59:49Z
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
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase LEXCV-111: Code Review Report

**Reviewed:** 2026-07-18T23:59:49Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Reviewed the new cross-entity global search surface (`GET /api/v1/pesquisa`): four new `pesquisarGlobal` native-query repository methods (Cliente/Processo/Documento/ParecerSolicitacao), the aggregating `PesquisaController`, the `ResultadoPesquisaDto`, the enable-extensions migration, and both test files (Mockito unit test + Testcontainers integration test).

Given the explicit brief to focus on tenant isolation and RBAC branch-gating, those two guarantees were the primary focus and were verified line-by-line, including cross-referencing `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `UserPrincipal.java`, `DatabaseSeeder.java`'s seeded scopes, the four entity models, and `GlobalExceptionHandler.java` for supporting context (none of these are part of this phase's file list and were not otherwise reviewed):

- **Tenant isolation (SRCH-07):** All four `pesquisarGlobal` queries place `tenant_id = :tenantId` as the first predicate, and in every query that also has an `OR`-chain of match conditions, that chain is correctly parenthesized (`AND (a OR b OR c)`), so the tenant predicate cannot be defeated by SQL operator precedence (the classic `AND x OR y` bug, which would silently turn into `(tenant_id = X AND a) OR b OR c` and leak cross-tenant rows). `termo` is always bound via `@Param`, never Java-string-concatenated into the query — confirmed no SQL injection surface. `tenantId` is always derived server-side from the authenticated `UserPrincipal`, never accepted as a request parameter, so there is no IDOR/tenant-spoofing path. This matches what `PesquisaRepositoryIT`'s `pesquisarGlobal_isolaPorTenant_zeroVazamentoEmTodosOsQuatroTipos` test exercises against real PostgreSQL.
- **RBAC branch-gating (SRCH-06):** Each of the 4 branches in `PesquisaController.pesquisar()` is independently gated by `hasAuthority(auth, "<scope>:view")` before the corresponding repository is even called. There is no fifth branch, no `HonorarioRepository`/financeiro dependency injected into the controller, and no financeiro-derived field anywhere in `ResultadoPesquisaDto` or the entities being mapped — financeiro/honorario data is structurally unreachable through this endpoint, not just RBAC-excluded. `/api/v1/pesquisa` is not in `SecurityConfig`'s `permitAll()` list, so `.anyRequest().authenticated()` (which explicitly excludes `AnonymousAuthenticationToken`) guarantees `getTenantId()`'s cast to `UserPrincipal` cannot be reached by an unauthenticated caller.

No Critical/Blocker findings resulted from this focused analysis — the core security guarantees the phase was built around hold up. The findings below are Warning/Info-level correctness and robustness gaps found during the broader standard-depth pass (Unicode-unsafe truncation, unescaped `ILIKE` wildcard metacharacters, and a few smaller quality items).

## Warnings

### WR-01: `substring()`-based truncation can split a UTF-16 surrogate pair

**File:** `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:76` and `:180`

**Issue:** Both truncation sites use raw `String.substring(0, N)`, which cuts at a UTF-16 *code unit* boundary, not a codepoint boundary:

```java
// line 74-77
String termo = q == null ? "" : q.trim();
if (termo.length() > TERMO_MAX_LENGTH) {
    termo = termo.substring(0, TERMO_MAX_LENGTH);   // line 76
}
```
```java
// line 178-181
String trimmed = descricao.trim();
return trimmed.length() > DESCRICAO_PREVIEW_LENGTH
        ? trimmed.substring(0, DESCRICAO_PREVIEW_LENGTH) + "..."   // line 180
        : trimmed;
```

If a supplementary-plane character (most emoji, some CJK extension characters — anything outside the BMP) straddles the cut index, `substring` splits the surrogate pair and leaves an unpaired (isolated) surrogate in the resulting `String`.

- On the **input** side (line 76), the mangled `termo` is passed straight into `pesquisarGlobal(...)` as a JDBC bind parameter; Java's default UTF-8 encoder silently replaces the orphaned surrogate on the wire, so the query executes but silently matches on corrupted text instead of what the user typed — a silent correctness bug, not a crash.
- On the **output** side (line 180), `truncarDescricao()` truncates *persisted* `ParecerSolicitacao.descricao` content (arbitrary user-entered text, not validated for BMP-only characters anywhere in the write path) and the result is serialized to JSON via Jackson. Jackson's UTF-8 string writer rejects isolated surrogates during serialization, so a `descricao` containing an astral character positioned exactly at the 80-character boundary can turn a normal search request into a failed response for every subsequent search that surfaces that record — a reproducible, data-triggered failure, not just a theoretical one.

Neither truncation site nor either test file exercises this boundary condition.

**Fix:** Use a codepoint-safe (or better, grapheme-safe) truncation helper instead of raw `substring`, e.g.:
```java
private static String truncateSafely(String s, int maxLength) {
    if (s.length() <= maxLength) return s;
    int end = maxLength;
    if (Character.isHighSurrogate(s.charAt(end - 1)) && Character.isLowSurrogate(s.charAt(end))) {
        end--; // don't split the pair
    }
    return s.substring(0, end);
}
```
Apply at both line 76 and line 180.

### WR-02: `ILIKE` wildcard metacharacters in `termo` are not escaped

**File:** `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java:35-38`, `ProcessoRepository.java:29-33`, `DocumentoRepository.java:26-27`, `ParecerSolicitacaoRepository.java:73` (all four `pesquisarGlobal` queries)

**Issue:** `termo` is safely bound as a JDBC parameter (no injection risk — confirmed), but it is wrapped as a literal `LIKE`/`ILIKE` pattern (`'%' || CAST(:termo AS text) || '%'`) without escaping the two `ILIKE` metacharacters `%` and `_` that may appear inside the user's own input. A search term containing a literal `%` or `_` (e.g. a document type code like `CONTR_ATO`, or a two-character query of `%%`) is interpreted as a wildcard rather than a literal character, producing unintended matches — e.g., a query of exactly `%%` matches every row with any non-null value in the scanned columns, effectively letting a user browse the 5 most-recent rows of each entity type without a real search term. This stays fully tenant-scoped and RBAC-scoped (not a privilege escalation — the caller already has list access to these entity types through other endpoints), but it is a genuine logic bug in the "search" contract: the endpoint doesn't do what a user typing `%` or `_` would expect.

**Fix:** Escape `%`, `_`, and the escape character itself in `termo` before wrapping it in wildcards, e.g. in the controller before calling the repositories:
```java
private static String escapeLike(String termo) {
    return termo.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
}
```
and use `ILIKE ... ESCAPE '\'` in each query, or perform the escaping in a shared helper reused by all four repository methods.

### WR-03: No defensive handling for the required-but-manual `unaccent` extension dependency

**File:** `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:86-97`, `backend/migrations/111-enable-search-extensions.sql`

**Issue:** All four `pesquisarGlobal` queries call `unaccent(...)`. The migration comment is admirably explicit that this extension must be installed manually in *every* environment (including local dev) before this code can run, and that there is no automated migration runner in this repository to enforce it. If that manual step is skipped, all four branches fail identically (every query in every repository calls `unaccent`), so the entire endpoint goes down at once with no fault isolation between entity types, and no code path distinguishes "extension missing" from any other unexpected SQL error — the caller just gets a generic 500 (`GlobalExceptionHandler`'s catch-all) exposing the raw exception class/message rather than an actionable diagnostic. This is a plausible, self-documented operational failure mode with zero runtime safeguard in the code under review (no startup check, no per-branch try/catch to degrade gracefully, no targeted error message).

**Fix:** Consider one (or both) of:
- A lightweight startup check (e.g. an `ApplicationRunner`/`CommandLineRunner` bean querying `pg_extension` for `unaccent`) that logs a loud, specific error at boot if missing, rather than waiting for the first user-facing search request to fail.
- Wrapping each of the four repository calls in `pesquisar()` in its own try/catch so one failing branch (e.g. a future entity type added without accounting for the extension) doesn't take down the other three.

## Info

### IN-01: Redundant double-fetch of `Authentication` from `SecurityContextHolder`

**File:** `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:60-64, 82-83`

**Issue:** `pesquisar()` calls `getTenantId()` (which internally re-reads `SecurityContextHolder.getContext().getAuthentication()` and casts it) and then, on the very next line, independently re-reads the same `SecurityContextHolder.getContext().getAuthentication()` again into a local `auth` variable:
```java
UUID tenantId = getTenantId();
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
```
Not a bug (both reads return the same value within the same request thread), just an avoidable duplicate lookup and a slightly awkward reading of the two related pieces of principal state.

**Fix:**
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UUID tenantId = ((UserPrincipal) auth.getPrincipal()).getTenantId();
```

### IN-02: `ResponseEntity<?>` return type loses type information

**File:** `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:73`

**Issue:** `pesquisar()` returns `ResponseEntity<?>` even though every code path returns a `List<ResultadoPesquisaDto>`. The wildcard weakens compile-time type checking for callers/tests (the test file has to `@SuppressWarnings("unchecked")` cast the body back) and, if this codebase ever adds OpenAPI/springdoc generation, would produce a weaker generated schema than a concrete return type.

**Fix:** `public ResponseEntity<List<ResultadoPesquisaDto>> pesquisar(...)`.

### IN-03: Nullable source fields can surface a blank `titulo`/`subtitulo` in results

**File:** `backend/src/main/java/com/lexcv/controllers/PesquisaController.java:115-127` (`montarSubtituloCliente`) and `:148-159` (`mapearDocumentos`)

**Issue:** `Cliente.numeroCliente` (`ClienteRepository`/`Cliente.java:64-66`, no `nullable=false`) and `Documento.nome` (`Documento.java:29`, no `nullable=false`) are not guaranteed non-null at the entity level. `montarSubtituloCliente` falls through to `return numeroCliente;` when neither NIF nor `numeroCliente` is present, which can be `null`; `mapearDocumentos` passes `documento.getNome()` straight through as `titulo`, which can be `null` if a Documento matched only on `tipo`. Neither causes an exception — the DTO field just serializes as JSON `null` — but it produces a search result card with a blank title in the UI for these edge cases.

**Fix:** Fall back to a placeholder (e.g. `"Cliente"` / `"Documento"`) the same way `mapearProcessos` already does for `numeroProcesso`, for consistency across all four mapping methods.

---

_Reviewed: 2026-07-18T23:59:49Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
