---
phase: 111-backend-pesquisa-global-cross-entity-api
plan: 01
subsystem: database
tags: [postgresql, spring-data-jpa, native-query, unaccent, pg_trgm, testcontainers, multi-tenant]

# Dependency graph
requires: []
provides:
  - "ClienteRepository.pesquisarGlobal(tenantId, termo, limit) — tenant-first, accent-folded, structured-ID-ranked native query"
  - "ProcessoRepository.pesquisarGlobal(tenantId, termo, limit) — tenant-first, numero_processo-ranked native query"
  - "DocumentoRepository.pesquisarGlobal(tenantId, termo, limit) — tenant-first, accent-folded native query (nome/tipo metadata only)"
  - "ParecerSolicitacaoRepository.pesquisarGlobal(tenantId, termo, limit) — NEW shallow descricao-only native query, separate from existing pesquisar()"
  - "backend/migrations/111-enable-search-extensions.sql — enables unaccent + pg_trgm PostgreSQL contrib extensions"
  - "PesquisaRepositoryIT — Testcontainers proof of zero cross-tenant leakage + exact-match-first ranking + accent-folding + LIMIT cap"
affects: [111-02-backend-pesquisa-global-cross-entity-api]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Accent-folded ranked native search: unaccent(col) ILIKE unaccent('%' || CAST(:param AS text) || '%') combined with a tiered ORDER BY CASE WHEN (exact > prefix > substring, then created_at DESC) — first use in this codebase"
    - "termo bound via @Param, wildcard-wrapped INSIDE the SQL string as '%' || CAST(:termo AS text) || '%', never Java string-concatenated — extends the existing CAST-null-guard idiom from ParecerSolicitacaoRepository.pesquisar()/NotificacaoRepository.buscarPorFiltros to a required (non-optional) bind parameter"

key-files:
  created:
    - backend/migrations/111-enable-search-extensions.sql
    - backend/src/test/java/com/lexcv/repositories/PesquisaRepositoryIT.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/ClienteRepository.java
    - backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java
    - backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java
    - backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java

key-decisions:
  - "ParecerSolicitacaoRepository.pesquisarGlobal is a NEW, separate, shallow descricao-only method — does not reuse existing pesquisar() (whose texto param matches only the latest ParecerVersao.conteudo, not descricao) and does not LEFT JOIN t_parecer_versao. Resolves the STACK.md-vs-PATTERNS.md tension flagged during planning in favor of shallow match for the quick global-search preview; deep content search stays exclusive to /pareceres/pesquisa"
  - "pg_trgm extension enabled now but intentionally unused by any query this phase — future GIN-index headroom per STACK.md, zero cost to enable up front"
  - "No functional index on unaccent(col) added this phase — unaccent() is STABLE not IMMUTABLE, PostgreSQL rejects it in index expressions; used only in WHERE/ORDER BY here, where that restriction does not apply"
  - "Processo.numero_processo exact-match tier accepted as a full table scan (threat T-111-06, disposition: accept) — no index exists on that column; acceptable at current per-tenant row counts, flagged as a future ceiling, not a v1 blocker"

patterns-established:
  - "Tiered CASE WHEN ranking (structured-ID exact match tier 0, structured-ID prefix tier 1, free-text prefix/substring tiers 2-3, tie-broken by created_at DESC) — reusable template for any future ILIKE-based search in this codebase"

requirements-completed: [SRCH-02, SRCH-07]

# Metrics
duration: ~15min
completed: 2026-07-18
---

# Phase 111 Plan 01: Data Layer for Cross-Entity Global Search Summary

**Four tenant-scoped, accent-folded, exact-match-first native `pesquisarGlobal` queries (Cliente/Processo/Documento/ParecerSolicitacao) plus the `unaccent`/`pg_trgm` extension migration and a Testcontainers IT proving zero cross-tenant leakage against real PostgreSQL**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-18
- **Tasks:** 2/2 completed
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments
- Added one `pesquisarGlobal(tenantId, termo, limit)` native `@Query` method to each of `ClienteRepository`, `ProcessoRepository`, `DocumentoRepository`, `ParecerSolicitacaoRepository` — every query leads its `WHERE` with `tenant_id = :tenantId`, binds `termo` exclusively via `@Param`/`CAST(:termo AS text)` (zero Java string-concatenation, zero SQLi surface), and caps results with `LIMIT :limit`
- Implemented structured-ID exact/prefix-first ranking (`Cliente.numero_cliente`/`nif`/`documento_numero`; `Processo.numero_processo`) ahead of free-text substring matches, tie-broken by `created_at DESC`, satisfying SRCH-02
- Implemented accent-folded matching via `unaccent()` wrapped around both the column and the search term on every free-text field, closing the "Conceição" vs "Conceicao" gap identified in research
- Created `backend/migrations/111-enable-search-extensions.sql` enabling the `unaccent` and `pg_trgm` PostgreSQL contrib extensions (already compiled into the deployed `postgres:16-alpine` image — zero new dependency, zero image change)
- Created `PesquisaRepositoryIT` (Testcontainers, mirrors `NotificacaoRepositoryIT`) with 5 test methods proving: zero cross-tenant leakage across all 4 entity types from a single shared-token fixture (SRCH-07, the platform's highest-severity guarantee), structured-ID-exact-beats-substring ranking for both Cliente and Processo (SRCH-02), accent-insensitive matching, and the per-type `LIMIT 5` cap

## Task Commits

Each task was committed atomically:

1. **Task 1: Migration + 4 tenant-scoped ranked native search queries** - `e626750` (feat)
2. **Task 2: Testcontainers IT — zero cross-tenant leakage + ranking + accent-folding** - `4043e59` (test)

**Plan metadata:** (this commit, following SUMMARY.md/STATE.md/ROADMAP.md updates)

## Files Created/Modified
- `backend/migrations/111-enable-search-extensions.sql` - Manual migration enabling `unaccent` + `pg_trgm`; must be run against every environment's DB (including local dev) since `CREATE EXTENSION` has no `ddl-auto` equivalent
- `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java` - Adds `pesquisarGlobal`: matches `nome`/`numero_cliente`/`nif`/`documento_numero`, 4-tier ranking (exact structured-ID → prefix structured-ID → prefix nome → substring)
- `backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java` - Adds `pesquisarGlobal`: matches `numero_processo`/`descricao`/`tribunal`/`area_juridica`/`tipo_processo`, 3-tier ranking (exact `numero_processo` → prefix `numero_processo` → substring)
- `backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java` - Adds `pesquisarGlobal`: matches `nome`/`tipo` metadata only (never `caminho_arquivo`, a MinIO key), 2-tier ranking (prefix `nome` → substring)
- `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` - Adds `pesquisarGlobal`: shallow `descricao`-only match, `created_at DESC` only; pre-existing `pesquisar()` (deep `ParecerVersao.conteudo` search) is unchanged
- `backend/src/test/java/com/lexcv/repositories/PesquisaRepositoryIT.java` - Testcontainers IT, 5 tests: cross-tenant isolation (all 4 types), Cliente ranking, Processo ranking, accent-folding, LIMIT cap

## Decisions Made
- `ParecerSolicitacaoRepository.pesquisarGlobal` implemented as a genuinely new, separate method rather than reusing `pesquisar()` — the plan had already flagged and resolved this tension (STACK.md suggested reuse; PATTERNS.md/ARCHITECTURE.md Anti-Pattern 6 required a shallow, `descricao`-only match to avoid duplicating `/pareceres/pesquisa`'s deep-content-search job). Executed exactly as specified in `<action>`.
- No functional index added on `unaccent(col)` this phase, per explicit plan instruction — `unaccent()` is `STABLE` not `IMMUTABLE`, so PostgreSQL would reject it in an index expression; deferred to a future phase if row counts justify it.

## Deviations from Plan

None - plan executed exactly as written. All 4 repository queries, the migration, and the IT match the plan's `<action>` specifications (WHERE predicates, ranking tiers, and file-by-file guidance) precisely.

## Issues Encountered

`cd backend && mvn -Dit.test=PesquisaRepositoryIT verify` could not complete locally: `IllegalStateException: Could not find a valid Docker environment` (`failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`). This is the same Docker Desktop/Testcontainers npipe incompatibility already documented in STATE.md as a recurring, independently-confirmed environmental limitation since Phase 91 — not a code defect, and the plan's own acceptance criteria explicitly anticipated this exact scenario ("if the Docker daemon is unreachable, `mvn -DskipTests test-compile` MUST still pass and the IT is gated in CI via deploy.yml's `test` job. Do not weaken the IT to make it pass without Docker"). Verified instead:
- `cd backend && mvn -DskipTests test-compile` — passes (main + test sources compile clean, confirmed twice).
- `.github/workflows/deploy.yml`'s `test` job runs `mvn -B verify` on `ubuntu-latest` with Docker available — Failsafe auto-discovers `*IT` classes by naming convention, so `PesquisaRepositoryIT` will run there without any workflow change.
- Source-level review confirms every acceptance criterion the IT would otherwise verify at runtime: `tenant_id = :tenantId` is the first `WHERE` predicate in all 4 queries (grep-verified), `termo` never appears outside `CAST(:termo AS text)` (grep-verified — zero raw Java string concatenation), and the pre-existing `ParecerSolicitacaoRepository.pesquisar()` is byte-for-byte unchanged.

## User Setup Required

None for this plan directly, but flagging for deploy awareness: `backend/migrations/111-enable-search-extensions.sql` is a **required manual migration** (no Flyway/Liquibase in this repo) that must be run against local dev, staging, and prod databases before the `pesquisarGlobal()` methods are exercised — unlike prior column/table migrations, this one has no `ddl-auto` fallback in any environment (not even dev), so skipping it produces a runtime `function unaccent(text) does not exist` error the first time any of these 4 queries executes.

## Next Phase Readiness
Data layer is complete and ready for Plan 111-02 (`PesquisaController`): all 4 `pesquisarGlobal` methods return plain entity lists (`List<Cliente>`, `List<Processo>`, `List<Documento>`, `List<ParecerSolicitacao>`) for the controller to map into `ResultadoPesquisaDto`. No blockers. One follow-up item for whoever runs Plan 111-02 or deploys this change: remember to run the `111-enable-search-extensions.sql` migration manually against the target database first (see "User Setup Required" above) — without it, `PesquisaController`'s first real request will 500.

---
*Phase: 111-backend-pesquisa-global-cross-entity-api*
*Completed: 2026-07-18*

## Self-Check: PASSED

Files verified present:
- FOUND: backend/migrations/111-enable-search-extensions.sql
- FOUND: backend/src/test/java/com/lexcv/repositories/PesquisaRepositoryIT.java
- FOUND: backend/src/main/java/com/lexcv/repositories/ClienteRepository.java
- FOUND: backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java
- FOUND: backend/src/main/java/com/lexcv/repositories/DocumentoRepository.java
- FOUND: backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java

Commits verified present:
- FOUND: e626750 (Task 1)
- FOUND: 4043e59 (Task 2)
