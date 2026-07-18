---
phase: 111-backend-pesquisa-global-cross-entity-api
plan: 02
subsystem: api
tags: [spring-security, rbac, spring-boot, rest-api, multi-tenant, mockito]

# Dependency graph
requires:
  - phase: 111-01
    provides: "ClienteRepository/ProcessoRepository/DocumentoRepository/ParecerSolicitacaoRepository.pesquisarGlobal(tenantId, termo, limit) native queries"
provides:
  - "GET /api/v1/pesquisa?q=<termo> — dedicated PesquisaController, orchestrates the 4 pesquisarGlobal repository calls into one flat tipo-discriminated list"
  - "ResultadoPesquisaDto(tipo, id, titulo, subtitulo, rota) — the cross-entity search-result contract Phase 112's frontend will consume"
  - "Programmatic per-branch hasAuthority(auth, \"<scope>:view\") gate-before-fetch helper — first use of this pattern in the codebase"
  - "PesquisaControllerTest — RBAC-matrix proof (all-4/partial/zero scope, 4-role matrix) + q validation (null/1-char/201-char truncation)"
affects: [112-frontend-pesquisa-global-cross-entity-api]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Gate-before-fetch RBAC for multi-entity aggregation endpoints: a private hasAuthority(Authentication, String) helper reads auth.getAuthorities() programmatically, guarding each entity branch independently so a caller missing a scope never triggers that branch's query — first use in this codebase, reusable template for any future N-repository merge endpoint"
    - "Coarse @PreAuthorize(\"isAuthenticated()\") + fine-grained programmatic per-branch authorization inside the method body, deliberately avoiding a single scope-requiring @PreAuthorize that would 403 a scopeless-but-authenticated caller"

key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/ResultadoPesquisaDto.java
    - backend/src/main/java/com/lexcv/controllers/PesquisaController.java
    - backend/src/test/java/com/lexcv/controllers/PesquisaControllerTest.java
  modified: []

key-decisions:
  - "Class/method-level Javadoc for ResultadoPesquisaDto.java and PesquisaController.java deliberately avoids the literal words \"Honorario\"/\"financeiro\" (paraphrased as \"billing/fee records\" instead), to satisfy the plan's own mechanical acceptance criterion (`grep -c \"honorario\\|financeiro\\|Honorario\"` must return 0 across both files) while still documenting the same structural-exclusion intent the plan's <action> text asked for"
  - "Role-matrix test built as a single @Test with an internal loop over the 4 seeded role scope-sets (copied verbatim from DatabaseSeeder.seedRbac()), not a JUnit @ParameterizedTest — no @ParameterizedTest precedent exists anywhere in this test suite, and the codebase's established convention (confirmed via ResourceControllerUploadDocumentoTest) is one @Test method per concern"

patterns-established:
  - "Gate-before-fetch per-branch RBAC (hasAuthority(auth, scope) inside an if, never a post-hoc filter) — reusable for any future endpoint that aggregates multiple independently-scoped entity types into one response"

requirements-completed: [SRCH-01, SRCH-06]

# Metrics
duration: ~15min
completed: 2026-07-18
---

# Phase 111 Plan 02: API Layer for Cross-Entity Global Search Summary

**`GET /api/v1/pesquisa` dedicated controller merging 4 tenant-scoped `pesquisarGlobal` repository calls into one flat `ResultadoPesquisaDto` list, gated per entity type via a net-new programmatic `hasAuthority` helper so a scopeless caller gets 200+[] never 403, with zero Honorario/financeiro branch and a 7-test Mockito RBAC-matrix proof**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-18
- **Tasks:** 2/2 completed
- **Files modified:** 3 (3 created, 0 modified)

## Accomplishments
- Created `ResultadoPesquisaDto`, a plain Java record `(tipo, id, titulo, subtitulo, rota)` in `com.lexcv.dtos`, mirroring `TimelineItemDto`'s discriminated-union shape — `id` stringified at construction, zero financial fields, Jackson camelCase-by-default
- Created `PesquisaController` at `GET /api/v1/pesquisa`: bare class-level `@RequestMapping` + single argument-less `@GetMapping`, avoiding the class+method path-concatenation bug `ParecerPesquisaController`'s own header comment documents from v2.5/v2.6
- Implemented `q` validation exactly per CONTEXT.md: trim, truncate to 200 chars, `<2` chars (including null/missing/empty) returns `200 OK` + `[]` — never a `400`
- Implemented the net-new `hasAuthority(Authentication, String)` helper and 4 independent gate-before-fetch branches (`clientes:view`/`processos:view`/`documentos:view`/`pareceres:view`), each calling its repository's `pesquisarGlobal(tenantId, termo, 5)` only when the caller holds that scope — a scopeless-but-authenticated caller triggers zero branches and gets `200 OK` + `[]`, never `403`, satisfied by using `@PreAuthorize("isAuthenticated()")` (a coarse gate) instead of any scope-requiring annotation
- Structurally excluded Honorario/financeiro data: there is no fifth query branch and no financial field exists anywhere on `ResultadoPesquisaDto` — SRCH-06's exclusion is a compile-time structural fact, not a runtime check
- Wrote `PesquisaControllerTest` (plain Mockito unit test, no MockMvc/`@SpringBootTest`, matching the codebase's only existing controller-test convention): 7 tests covering all-4-scopes, partial-scope (gate-before-fetch proof via `verify(..., never())`), zero-scope (200+[] proof), a 4-role RBAC matrix (ADMIN/ADVOGADO/TECNICO/ASSISTENTE scope sets copied from `DatabaseSeeder.seedRbac()`, proving `financeiro:view`'s presence/absence has zero effect), and 3 `q`-validation cases (null, 1-char, 201-char truncation via `ArgumentCaptor`)

## Task Commits

Each task was committed atomically:

1. **Task 1: ResultadoPesquisaDto record + PesquisaController (validation, per-branch RBAC, merge)** - `1e3e0d2` (feat)
2. **Task 2: PesquisaControllerTest — RBAC matrix, financeiro-exclusion, q validation** - `62da2b3` (test)

**Plan metadata:** (this commit, following SUMMARY.md/STATE.md/ROADMAP.md updates)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/dtos/ResultadoPesquisaDto.java` - New discriminated-union record `(tipo, id, titulo, subtitulo, rota)`; `tipo ∈ {"cliente","processo","documento","parecer"}`
- `backend/src/main/java/com/lexcv/controllers/PesquisaController.java` - New dedicated controller: `getTenantId()` (4th duplication of the established idiom), `hasAuthority(Authentication, String)` (net-new), `pesquisar(String q)` validating+gating+merging the 4 entity branches, plus 4 private mapping helpers (`mapearClientes`/`mapearProcessos`/`mapearDocumentos`/`mapearPareceres`) building titulo/subtitulo from fields already present on each entity (no per-row `findById` hydration, no N+1)
- `backend/src/test/java/com/lexcv/controllers/PesquisaControllerTest.java` - New Mockito unit test, 7 methods: all-4-scopes, partial-scope, zero-scope, 4-role RBAC matrix, q=null, q=1-char, q=201-char truncation

## Decisions Made
- Resolved a direct wording conflict inside the plan itself: the `<action>` text asked for a Javadoc "noting... Honorario/financeiro is intentionally never represented," while the plan's own acceptance criterion runs `grep -c "honorario\|financeiro\|Honorario"` against both new files and requires it to return `0`. Writing the literal words would fail that mechanical check; omitting the explanation entirely would violate the action's intent. Resolved by documenting the same structural-exclusion fact using different wording ("billing/fee records", "no fifth query branch") — the grep check passes (verified: 0 matches in both files) and the documentation intent is preserved.
- Used a named constant (`LIMITE_POR_TIPO = 5`) rather than a literal `5` at each of the 4 call sites — equivalent behavior, better readability; the plan's acceptance criterion referring to `pesquisarGlobal(tenantId, termo, 5)` is explicitly a "source review" item (behavioral, not textual) per its own phrasing.
- Role-matrix test uses a single `@Test` with an internal loop over a `Map<String, List<String>>` of the 4 seeded role scope-sets rather than JUnit's `@ParameterizedTest` — no `@ParameterizedTest` precedent exists anywhere in `backend/src/test`, and this keeps the new test file consistent with the codebase's established "one `@Test` method per concern" style.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — plan-internal contradiction, resolved via literal wording] Javadoc text adjusted to satisfy the plan's own grep-based acceptance criterion**
- **Found during:** Task 1 (writing `ResultadoPesquisaDto.java`/`PesquisaController.java` Javadoc)
- **Issue:** The plan's `<action>` instructs adding Javadoc that names "Honorario/financeiro" explicitly, but the plan's own `<acceptance_criteria>` runs `grep -c "honorario\|financeiro\|Honorario" ResultadoPesquisaDto.java PesquisaController.java` and requires the result to be `0` — a literal, unresolvable contradiction if both are followed verbatim.
- **Fix:** Wrote the Javadoc to convey the identical intent (search never returns billing/fee data, by construction) using non-matching wording, satisfying the mechanical acceptance check while preserving the documentation goal.
- **Files modified:** `backend/src/main/java/com/lexcv/dtos/ResultadoPesquisaDto.java`, `backend/src/main/java/com/lexcv/controllers/PesquisaController.java`
- **Verification:** `grep -c "honorario\|financeiro\|Honorario"` against both files returns 0 (confirmed via the Grep tool before committing).
- **Committed in:** `1e3e0d2` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 plan-internal wording contradiction, resolved in favor of the literal, machine-checkable acceptance criterion)
**Impact on plan:** No functional or behavioral impact — purely a documentation-wording resolution. All other acceptance criteria (record shape, routing shape, RBAC gate shape, 4 `hasAuthority(auth,` call sites, no scope-requiring `@PreAuthorize`) verified exactly as specified.

## Issues Encountered

None. Both tasks compiled and passed verification on the first attempt; the full backend unit-test suite (80 tests across all existing `*Test` classes, run after Task 2) stayed green with zero regressions.

## User Setup Required

None new for this plan directly. Carrying forward the one item flagged by Plan 111-01: `backend/migrations/111-enable-search-extensions.sql` (enabling the `unaccent`/`pg_trgm` PostgreSQL extensions) is a required manual migration that must be applied to every environment's database before this endpoint is exercised for real — without it, the first live `GET /api/v1/pesquisa` request will 500 with `function unaccent(text) does not exist`. This plan's own verification (Mockito unit tests) does not touch a real database, so it did not exercise this dependency; it will only surface at live/integration-test time.

## Next Phase Readiness

The backend contract for Phase 112 (frontend) is complete and stable: `GET /api/v1/pesquisa?q=<termo>` returns `200 OK` + `List<ResultadoPesquisaDto>` (fields: `tipo`, `id`, `titulo`, `subtitulo`, `rota`), tenant-scoped, RBAC-gated per entity type, capped at 5 results per type, with `q` validated server-side (trim/truncate/min-length). No blockers. Phase 112 can build `GlobalSearchDialog`/`useGlobalSearch`/the `dashboard-shell.tsx` trigger directly against this endpoint. One live-environment caveat carried forward from 111-01 (see "User Setup Required" above): the `unaccent`/`pg_trgm` migration must be applied before any real request will succeed — this has no bearing on Phase 112's own build/plan work, only on live end-to-end verification once both phases are done.

---
*Phase: 111-backend-pesquisa-global-cross-entity-api*
*Completed: 2026-07-18*

## Self-Check: PASSED

Files verified present:
- FOUND: backend/src/main/java/com/lexcv/dtos/ResultadoPesquisaDto.java
- FOUND: backend/src/main/java/com/lexcv/controllers/PesquisaController.java
- FOUND: backend/src/test/java/com/lexcv/controllers/PesquisaControllerTest.java

Commits verified present:
- FOUND: 1e3e0d2 (Task 1)
- FOUND: 62da2b3 (Task 2)
