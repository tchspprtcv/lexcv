---
phase: LEXCV-111-backend-pesquisa-global-cross-entity-api
verified: 2026-07-21T15:30:00Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
---

# Phase 111: Backend — Pesquisa Global Cross-Entity (API) Verification Report

**Phase Goal:** Existe um endpoint backend que devolve, de forma segura e corretamente ordenada, resultados de Clientes, Processos, Documentos e Pareceres do tenant do utilizador autenticado — a fundação sobre a qual a experiência de pesquisa do utilizador (Phase 112) é construída.
**Verified:** 2026-07-21T15:30:00Z
**Status:** passed
**Re-verification:** No — initial verification (no prior `111-VERIFICATION.md` existed)

## Goal Achievement

### Observable Truths (mapped to ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | (SRCH-01) Autenticada chamada a `GET /api/v1/pesquisa?q=<termo>` devolve resultados de Cliente, Processo, Documento e ParecerSolicitacao do tenant do utilizador, cada um identificável pelo seu `tipo` | VERIFIED | `PesquisaController.java:49-144` — dedicated controller, bare `@RequestMapping("/api/v1/pesquisa")` + argument-less `@GetMapping` (no path-concat bug, confirmed no `@GetMapping("...")` sub-path). Merges 4 `pesquisarGlobal` calls into `List<ResultadoPesquisaDto>` with `tipo ∈ {cliente,processo,documento,parecer}`. `SecurityConfig.java:69` `.anyRequest().authenticated()` gates this path at the filter chain (path not in the `permitAll()` list) — an unauthenticated caller never reaches the controller. Personally ran `mvn test -Dtest=PesquisaControllerTest` → **11/11 passing**, including `pesquisar_comTodosOsQuatroScopes_consultaTodosOsQuatroRamos` which asserts all 4 `tipo` values are present in one response. |
| 2 | (SRCH-07) Dois tenants com registos/termos coincidentes nunca veem resultados um do outro, em nenhum dos 4 tipos — cada sub-query parte do próprio `tenant_id` | VERIFIED (static + CI-gated runtime proof) | Read all 4 native `@Query` strings directly: `ClienteRepository.java:39-58`, `ProcessoRepository.java:33-48`, `DocumentoRepository.java:28-38`, `ParecerSolicitacaoRepository.java:75-81` — every one leads its `WHERE` with the unconditional `tenant_id = :tenantId` (never `CAST`-guarded/optional, unlike other nullable params in the same file's `pesquisar()` method, which correctly use the `(:param IS NULL OR ...)` pattern only for genuinely optional filters). Confirmed `PesquisaController` never calls an un-tenant-scoped finder (e.g. `findByClienteId`). `PesquisaRepositoryIT.pesquisarGlobal_isolaPorTenant_zeroVazamentoEmTodosOsQuatroTipos` (lines 115-156) seeds 2 tenants with a shared token across all 4 entity types and asserts zero cross-tenant leakage per type — compiles clean (`mvn -DskipTests test-compile` passed), but **cannot execute in this sandbox**: `docker info` confirms `failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine` — the same pre-existing, documented-since-Phase-91 constraint. This is not a gap under the plan's own acceptance criteria, which explicitly define "done" for this exact scenario as "`test-compile` passes AND IT is CI-gated" — both confirmed (`.github/workflows/deploy.yml`'s `test` job runs `mvn -B verify` on `ubuntu-latest`, which has Docker, and Failsafe auto-discovers `*IT` classes by naming convention). |
| 3 | (SRCH-02) Correspondências exatas/prefixo em identificadores estruturados (`numero_cliente`, `numero_processo`, `nif`, `documento_numero`) ordenam antes de correspondências por substring, no mesmo conjunto de resultados | VERIFIED (static + CI-gated runtime proof) | Read the `ORDER BY CASE WHEN` clauses directly: `ClienteRepository.java:45-54` — tier 0 exact `numero_cliente`/`nif`/`documento_numero`, tier 1 prefix on those 3, tier 2 prefix `unaccent(nome)`, tier 3 else, tie-broken `created_at DESC`; `ProcessoRepository.java:40-43` — tier 0 exact `numero_processo`, tier 1 prefix, tier 2 else. (Documento/ParecerSolicitacao correctly have no structured-ID tier — neither entity has a field in the SC's named list, consistent with CONTEXT.md's explicit design.) `PesquisaRepositoryIT` has 2 dedicated ranking tests (`..._correspondenciaExataNumeroClienteRankeiaAntesDeSubstringEmNome`, `..._correspondenciaExataNumeroProcessoRankeiaAntesDeSubstringEmDescricao`) plus 2 WR-02-added tests specifically proving the raw-vs-escaped-term split doesn't corrupt tier-0 exact matching — all compile clean; execution is CI-gated (same Docker constraint as truth #2). `111-REVIEW.md`'s independently-derived, line-by-line trace ("Dual `termo`/`termoEscapado` split does not break exact-match ranking — CONFIRMED") corroborates the static read. |
| 4 | (SRCH-06) Um tipo só é consultado quando o utilizador detém o scope `:view` correspondente — nunca obtido e escondido depois; dados de `Honorario`/financeiro nunca surgem, independentemente do perfil | VERIFIED | `PesquisaController.java:126-141` — 4 independent `if (hasAuthority(auth, "<scope>:view"))` gates, one per branch, **before** invoking the corresponding repository (gate-before-fetch, not post-hoc filtering); confirmed exactly 4 occurrences of `hasAuthority(auth,`. No 5th branch exists for Honorario/financeiro; `grep -c "honorario\|financeiro\|Honorario"` against `ResultadoPesquisaDto.java` + `PesquisaController.java` returns **0** (personally re-ran). `ResultadoPesquisaDto` has exactly 5 fields (`tipo,id,titulo,subtitulo,rota`), no financial field. `@PreAuthorize("isAuthenticated()")` only (no scope-requiring annotation) confirmed at line 108, so a scopeless-but-authenticated caller triggers zero branches → `200 + []`, never `403`. Cross-checked `PesquisaControllerTest`'s role-matrix scope sets directly against `DatabaseSeeder.seedRbac()` (lines 293-353) — they match exactly (ADMIN/ADVOGADO/TECNICO all hold `financeiro:view`, ASSISTENTE does not; all 4 hold the 4 `:view` scopes this endpoint gates on). Ran the full test class: **11/11 passing**, including `pesquisar_comScopeParcial_...` (partial-scope `verify(..., never())` proof), `pesquisar_semNenhumScope_...` (zero-scope → `[]`, no exception, all repos `never()` called), and `pesquisar_matrizDePerfis_nuncaExpoeTipoForaDosQuatroPermitidos` (4-role matrix, `financeiro:view` toggling has zero effect). |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/controllers/PesquisaController.java` | `GET /api/v1/pesquisa` orchestration + RBAC-by-branch + validation | VERIFIED | 259 lines; bare route mapping, `getTenantId()`, `hasAuthority()`, 4 gated branches, `truncateSafely`/`escapeLike`/`pesquisarComIsolamentoDeFalhas` (WR-01/02/03 hardening), 4 mapping helpers. Compiles clean, exercised by 11 passing unit tests. |
| `backend/src/main/java/com/lexcv/dtos/ResultadoPesquisaDto.java` | Discriminated record `(tipo,id,titulo,subtitulo,rota)` | VERIFIED | Exact 5-field record, Javadoc documents structural Honorario/financeiro exclusion without using the literal grep-checked words. |
| `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java` (`pesquisarGlobal`) | Tenant-first, accent-folded, ranked, LIMIT-capped native query | VERIFIED | Lines 39-58; `tenant_id = :tenantId` first predicate, 4-tier `CASE WHEN`, `unaccent()`, `ESCAPE '\'`-paired `ILIKE`, `LIMIT :limit`. |
| `backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java` (`pesquisarGlobal`) | Same idiom, `numero_processo`-ranked | VERIFIED | Lines 33-48; 3-tier `CASE WHEN`, same tenant/escaping/limit pattern. |
| `backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java` (`pesquisarGlobal`) | Same idiom, `nome`/`tipo` metadata only | VERIFIED | Lines 28-38; searches metadata only, never `caminho_arquivo`. |
| `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (`pesquisarGlobal`) | New, separate, shallow `descricao`-only query | VERIFIED | Lines 75-81; pre-existing `pesquisar()` (deep content search) left untouched — confirmed by re-reading both methods side by side. |
| `backend/migrations/111-enable-search-extensions.sql` | `CREATE EXTENSION` for `unaccent` + `pg_trgm` | VERIFIED | 2 `CREATE EXTENSION IF NOT EXISTS` statements; header follows the repo's 3-part manual-migration convention (compared directly against `96-add-notificacao-snoozed-until.sql`), correctly adapted to note this one must run in every environment including dev (not just staging/prod), since `CREATE EXTENSION` has no `ddl-auto` equivalent at all. |
| `backend/src/test/java/com/lexcv/controllers/PesquisaControllerTest.java` | RBAC matrix + financeiro-exclusion + q validation | VERIFIED & WIRED | 11 `@Test` methods; personally executed — **11/11 pass**. |
| `backend/src/test/java/com/lexcv/repositories/PesquisaRepositoryIT.java` | Testcontainers proof of isolation/ranking/accent/LIMIT | VERIFIED (compiles; execution CI-gated) | 7 `@Test` methods incl. 2 WR-02 additions; `@BeforeEach` issues `CREATE EXTENSION IF NOT EXISTS unaccent`/`pg_trgm`. Cannot execute here — Docker daemon unreachable (confirmed via `docker info`), matching the documented Phase-91 constraint; CI-gated via `deploy.yml`. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `PesquisaController.pesquisar()` | `tenant_id` | `getTenantId()` result passed as first arg to every `pesquisarGlobal` call | WIRED | Confirmed at call sites lines 128, 132, 136, 140. |
| `PesquisaController.pesquisar()` | each `pesquisarGlobal` | `if (hasAuthority(auth, "<scope>:view"))` before the call | WIRED | 4/4 branches gated; `grep -c "hasAuthority(auth,"` returns 4. |
| Each `pesquisarGlobal` query | `unaccent` + `ILIKE` | accent-folded matching on column and term | WIRED | Present in Cliente/Processo/Documento/ParecerSolicitacao (Documento/ParecerSolicitacao lack structured-ID fields by design, not by omission bug). |
| `PesquisaController` | route `GET /api/v1/pesquisa` | bare class `@RequestMapping` + single argument-less `@GetMapping` | WIRED | No `@GetMapping("...")` with a sub-path found — avoids the documented class+method path-concatenation bug (`ParecerPesquisaController`'s own header comment). |
| `PesquisaRepositoryIT` | `CREATE EXTENSION unaccent/pg_trgm` | `@BeforeEach` native query before any test body runs | WIRED | Lines 63-67. |

### Data-Flow Trace (Level 4 — backend equivalent)

| Artifact | Data Source | Produces Real Data | Status |
|----------|-------------|---------------------|--------|
| `PesquisaController.pesquisar()` result list | 4 `@Query(nativeQuery = true)` `SELECT ... FROM t_cliente/t_processo/t_documento/t_parecer_solicitacao` | Yes — real `SELECT` against real tables (not a hardcoded/static return; only the **unit test** mocks the repository layer, which is correct and expected for a Mockito unit test, not a stub in production code) | FLOWING (empirical DB-level confirmation pending first CI run, per the accepted Docker constraint above) |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Main + test sources compile with all 4 new repository methods + controller + DTO | `cd backend && mvn -DskipTests test-compile` | `BUILD SUCCESS`, 0 errors | PASS |
| `PesquisaControllerTest` (RBAC matrix, validation, WR-01/02/03 regressions) | `cd backend && mvn test -Dtest=PesquisaControllerTest` | `Tests run: 11, Failures: 0, Errors: 0, Skipped: 0` | PASS |
| Full backend unit-test suite has zero regressions from this phase's changes | `cd backend && mvn test` | `Tests run: 84, Failures: 0, Errors: 0, Skipped: 0` — `BUILD SUCCESS` (matches `111-REVIEW.md`'s independently-claimed 84/84, re-confirmed by me directly, not reused) | PASS |
| No Honorario/financeiro reference anywhere in the DTO/controller | `grep -c "honorario\|financeiro\|Honorario" ResultadoPesquisaDto.java PesquisaController.java` | `0` in both files | PASS |
| Docker/Testcontainers availability in this sandbox | `docker info` | `Server: failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine` | CONFIRMED UNAVAILABLE (pre-existing, accepted, matches Phase 91 history) |
| CI gate exists for the Testcontainers IT | Read `.github/workflows/deploy.yml` | `test` job runs `mvn -B verify` on `ubuntu-latest` (has Docker); Failsafe auto-binds `*IT` classes | PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` convention exists in this repository and none is declared in either PLAN or SUMMARY for this phase (backend API phase, not a migration/tooling phase using the probe pattern). `find scripts -path '*/tests/probe-*.sh'` and a grep of both PLANs/SUMMARYs for probe references returned no matches.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| SRCH-01 | 111-02 | Utilizador pesquisa por texto/identificador e obtém resultados de Clientes, Processos, Documentos e Pareceres do seu tenant, agrupados por tipo | SATISFIED | `PesquisaController` + `ResultadoPesquisaDto`; unit-tested. |
| SRCH-02 | 111-01 | Resultados priorizam correspondências exatas/prefixo em identificadores estruturados acima de correspondências por substring | SATISFIED | Tiered `ORDER BY CASE WHEN` in `ClienteRepository`/`ProcessoRepository`; IT written+compiled, CI-gated. |
| SRCH-06 | 111-02 | Resultados só incluem tipos de entidade para os quais o utilizador tem permissão de visualização, verificado por ramo, nunca por filtro posterior | SATISFIED | 4x gate-before-fetch `hasAuthority`; zero Honorario/financeiro; role-matrix test passing. |
| SRCH-07 | 111-01 | Toda a pesquisa é isolada por tenant, incluindo em cada sub-query por tipo de entidade | SATISFIED | `tenant_id = :tenantId` unconditional first predicate in all 4 queries; IT written+compiled, CI-gated. |

**Orphaned requirements:** None. `REQUIREMENTS.md`'s traceability table maps exactly SRCH-01/02/06/07 to Phase 111 (all marked "Complete"), and both PLANs' frontmatter `requirements:` fields (`[SRCH-02, SRCH-07]` + `[SRCH-01, SRCH-06]`) together account for exactly those 4 — no unclaimed IDs, no unmapped plans.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `PesquisaController.java` | 161-164 | `logger.error(format, tipo, ex.toString(), ex)` logs the exception description twice (once inline, once via the trailing `Throwable`'s stack trace) | Info (IN-01 from `111-REVIEW.md`, explicitly accepted/deferred, cosmetic) | None — does not affect behavior, security, or correctness; independently reviewed and left open by design. |
| (false positive) `PesquisaControllerTest.java` / `PesquisaRepositoryIT.java` | various | Grep for debt markers (`TODO`/`FIXME`/etc., case-insensitive) matches the Portuguese word "**Todos**" (= "all") inside camelCase test method names (e.g. `...consultaTodosOsQuatroRamos`) | None (false positive) | No actual TODO/FIXME/XXX/HACK/PLACEHOLDER marker exists in any of the 9 files this phase touched. |

No Critical or Blocker findings. `111-REVIEW.md` closed `status: clean` (0 critical, 0 warning, 1 info) after WR-01/WR-02/WR-03 fixes were independently re-verified by the orchestrator (not merely trusted from the fixer's self-report) — confirmed by reading the closure note and re-deriving the same evidence myself (`truncarDescricao` fix present at `PesquisaController.java:251-258`; both regression tests present and passing).

### Human Verification Required

None. This is a backend-only API phase (per `111-CONTEXT.md`: "Backend apenas; nenhuma UI nesta fase") with fully mechanically-verifiable success criteria — no visual appearance, no real-time behavior, no external service integration requiring subjective human judgment. The one item that cannot be executed in this session (the Testcontainers `PesquisaRepositoryIT`) is not a human-judgment item — it is an automated test whose execution is deferred to CI (a machine), not to a human tester, and the plan's own acceptance criteria explicitly define this exact scenario as an acceptable "done" state.

### Gaps Summary

No gaps. All 4 ROADMAP success criteria are verified: two (SRCH-01, SRCH-06) with full local automated proof — I personally compiled the project, ran the 11-test `PesquisaControllerTest` class (11/11 passing) and the full 84-test backend unit suite (84/84 passing, zero regressions) — and two (SRCH-02, SRCH-07) with direct static inspection of the actual SQL text in all 4 repositories (not inferred from SUMMARY prose), corroborated by `111-REVIEW.md`'s independent line-by-line re-derivation (including standalone empirical Java reproductions of `escapeLike`/`truncateSafely`), with genuine Postgres-level execution deferred to CI due to a pre-existing, documented-since-Phase-91 Docker/Testcontainers sandbox limitation that I independently reconfirmed (`docker info` → daemon unreachable) and that the plan's own acceptance criteria explicitly anticipate and accept.

Security posture (`security_asvs_level: 1`, `security_block_on: "high"` per `.planning/config.json`): no unmitigated HIGH-severity threat remains — SQLi surface is closed by exclusive `@Param` binding (verified: `termo`/`termoEscapado` never Java-string-concatenated into any query), cross-tenant leakage is structurally prevented by the unconditional `tenant_id` predicate, and cross-scope/financeiro leakage is structurally prevented by gate-before-fetch plus the DTO's fixed 5-field shape (no financial field exists to leak).

One out-of-scope observation (not a phase-111 gap): `git status` shows staged-but-uncommitted changes to `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` and `JwtTokenProvider.java` (added logging for null/deactivated JWT users and validation failures). These files are not part of Phase 111's file set in either PLAN or SUMMARY, are unrelated to the search endpoint or RBAC-scope logic this phase introduces, and do not affect any of the 4 success criteria verified above.

---

_Verified: 2026-07-21T15:30:00Z_
_Verifier: Claude (gsd-verifier)_
