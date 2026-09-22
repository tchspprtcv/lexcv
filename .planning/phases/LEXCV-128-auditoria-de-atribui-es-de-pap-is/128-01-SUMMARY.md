---
phase: 128-auditoria-de-atribui-es-de-pap-is
plan: 01
subsystem: database
tags: [jpa, hibernate, spring-data, postgresql, audit-log, rbac, immutability]

# Dependency graph
requires: []
provides:
  - "AuditLog entity with nullable detalhe TEXT column and @Immutable"
  - "AuditLogRepository narrowed to Repository<AuditLog, Long> with save/findByTenantIdAndProcessoIdOrderByTimestampDesc/buscarEventosRbac"
  - "Manual migration 128-add-audit-log-detalhe.sql, inventoried in README and DEPLOYMENT.md"
  - "AuditLogImutabilidadeTest structural gate proving AUDT-04 (no mutating surface over t_audit_log)"
affects: [128-02, 128-03, 128-04, 128-07, 128-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Repository<T, ID> narrowing (Spring Data Commons marker interface) instead of JpaRepository, to remove delete*/saveAll/saveAndFlush from the compiled API rather than merely leaving them uncalled"
    - "@Immutable (org.hibernate.annotations.Immutable) on an append-only entity to suppress Hibernate dirty-checking UPDATEs"
    - "ClassPathScanningCandidateComponentProvider + AnnotationTypeFilter(RestController.class) reflection scan, asserting a minimum result count so an empty/misdirected scan cannot pass vacuously"

key-files:
  created:
    - backend/migrations/128-add-audit-log-detalhe.sql
    - backend/src/test/java/com/lexcv/repositories/AuditLogImutabilidadeTest.java
  modified:
    - backend/src/main/java/com/lexcv/models/AuditLog.java
    - backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java
    - backend/migrations/README.md
    - DEPLOYMENT.md

key-decisions:
  - "detalhe stores display names (User.nome at event time), never resolved at read time, because AdminController.deleteUser hard-deletes users and id-only resolution would silently lose the actor/target of the most common investigation case (revised Decision 2, already locked in CONTEXT.md before this plan ran)"
  - "buscarEventosRbac matches papelId against both papel_escritorio.entidade_id directly and atribuicao_papel's detalhe->>'papelId' JSON key, so a role query also surfaces its own assignment events and survives the role being renamed later"
  - "Extended the acao/entidadeTipo vocabulary comments with every value found by grep, not only the three named in the plan text (also added parecer_atribuir, parecer_aprovar, parecer_entregar, parecer_versao_criar, parecer_versao) -- the comment's job is to be a complete inventory, and the plan's own instruction was to confirm by grep, which surfaced a larger set than the three examples it named"

requirements-completed: [AUDT-01, AUDT-02, AUDT-04]

# Metrics
duration: 55min
completed: 2026-09-22
---

# Phase 128 Plan 01: AuditLog storage foundation Summary

**Nullable `detalhe` column plus `@Immutable` on `AuditLog`, `AuditLogRepository` narrowed from `JpaRepository` to a three-method `Repository<AuditLog, Long>` with a paginated tenant-scoped RBAC finder, migration 128, and a 6-test structural gate proving the repository has no mutating surface — verified by making it fail on a real revert to `JpaRepository`.**

## Performance

- **Duration:** 55 min
- **Started:** 2026-09-22T14:30:00Z (approx, continuation after a mid-session network interruption with no partial work on disk)
- **Completed:** 2026-09-22T15:34:39Z
- **Tasks:** 2
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments
- `AuditLog` entity carries a nullable `detalhe TEXT` column (Decision 2, additive — all ten existing write sites compile unchanged) and `@Immutable`, with `acao`/`entidadeTipo` vocabulary comments extended to cover both the new RBAC values and every previously-undocumented existing value found by grepping `.acao("`/`.entidadeTipo("` across `backend/src/main/java`.
- `AuditLogRepository` narrowed from `JpaRepository<AuditLog, Long>` to `Repository<AuditLog, Long>`, declaring exactly `save`, `findByTenantIdAndProcessoIdOrderByTimestampDesc`, and the new paginated `buscarEventosRbac` (native `@Query` + `countQuery`, the second use of `Pageable`/`Page` in this codebase after `NotificacaoRepository.buscarPorFiltros`).
- Migration `128-add-audit-log-detalhe.sql` (idempotent `ADD COLUMN IF NOT EXISTS`), inventoried in all four `backend/migrations/README.md` locations (Path A, Path B row 17, re-run safety, execution status) and pointed to from a new DEPLOYMENT.md subsection.
- `AuditLogImutabilidadeTest`: 6 JUnit 5 tests (no Spring context) asserting the repository's base interface, exact method set, absence of mutating method-name prefixes, `@Immutable` on the entity, absence of any `@RestController` mutating handler whose path contains "audit", and absence of any `DELETE`/`UPDATE` SQL statement against `t_audit_log`/`auditlog` in `src/main/java`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Extend AuditLog, narrow AuditLogRepository, add migration 128 and its inventory rows** - `1015c4e9` (feat)
2. **Task 2: Structural immutability gate (AUDT-04)** - `619071f2` (test)

_Note: Task 2 is TDD-flagged in the plan, but the "no analog" nature of the test (a pure structural gate, not a behavior driven by a pre-existing implementation) meant there was nothing to make it fail in a meaningful RED sense before writing the assertions — the test was written directly against the already-correct Task 1 implementation. The mutation check (see below) supplies the equivalent proof that the gate is not vacuously green, per the plan's own acceptance criterion, and is documented in the commit message rather than as a separate RED/GREEN commit pair._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/AuditLog.java` - nullable `detalhe` column, `@Immutable`, extended vocabulary comments
- `backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java` - narrowed base interface, three declared methods including `buscarEventosRbac`
- `backend/migrations/128-add-audit-log-detalhe.sql` - idempotent `ADD COLUMN IF NOT EXISTS detalhe`
- `backend/migrations/README.md` - Path A/B, re-run safety, execution status rows for script 128
- `DEPLOYMENT.md` - new "Phase 128 — auditoria de papéis" subsection
- `backend/src/test/java/com/lexcv/repositories/AuditLogImutabilidadeTest.java` - AUDT-04 structural gate (273 lines, 6 tests)

## Decisions Made
- Extended the vocabulary comment with the full set of values found by grep (see key-decisions above), rather than only the three named literally in the plan's action text — the plan's own instruction was "confirm by grepping", and the grep surfaced a larger accurate set.
- `buscarEventosRbac`'s role filter matches both `papel_escritorio` (direct `entidade_id`) and `atribuicao_papel` (via `detalhe->>'papelId'`) so that filtering by role also returns that role's own assignment events, and continues to work after a role rename (id-stable, not name-stable) — this directly follows the plan's stated WHERE clause and rationale, restated here for the summary's own record.
- Task 2's TDD flag was honored via the mutation-check acceptance criterion (revert to `JpaRepository`, observe failure, restore, observe pass) rather than a literal RED-commit/GREEN-commit pair, because the test is a structural gate over the already-correct Task 1 code, not a behavior being driven into existence — see Task Commits note above.

## Deviations from Plan

None - plan executed as written. The vocabulary-comment extension (see Decisions Made) is not a deviation: the plan's action text explicitly instructed "if the executor confirms them by grepping", and grepping confirmed additional values beyond the three the plan named as examples — filling in the full, accurate list is exactly what that instruction called for, not new scope.

## Issues Encountered

**Mid-session network interruption.** The initial file-reading phase of this plan was interrupted by an upstream network error (API unreachable) partway through reading `DEPLOYMENT.md` and grepping for `.acao(`/`.entidadeTipo(` usages. No files had been written yet at that point (working tree was clean, `HEAD` at `7da0e0c9`). On resumption, the same reads were re-run to completion and execution proceeded from Task 1 with no rework needed.

## Verification Evidence

- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test-compile` — exit 0, immediately after Task 1 (proves the ten existing write sites, `getAuditLog`, and the four test files' `@Mock`/`save(any())` stubs compile against the narrowed repository).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=AuditLogImutabilidadeTest` — **Tests run: 6, Failures: 0, Errors: 0** (green, as committed).
- **Mutation check (required by the plan's Task 2 acceptance criteria), performed and reverted, not committed:** temporarily changed `AuditLogRepository` to `extends JpaRepository<AuditLog, Long>` (restoring the `JpaRepository` import). Re-ran the same test:
  - `Tests run: 6, Failures: 3, Errors: 0` — **BUILD FAILURE**, with:
    - `repositorioEAssignavelASoRepositoryENaoAsBasesMaisPermissivas` — failed: "AuditLogRepository NAO deve ser assignavel a CrudRepository" (`expected: <false> but was: <true>`)
    - `conjuntoDeMetodosEExactamenteOPinadoSemModifying` — failed: expected set `[save, findByTenantIdAndProcessoIdOrderByTimestampDesc, buscarEventosRbac]`, actual set included `getReferenceById, saveAll, deleteAllInBatch, findOne, ... deleteById, ...` (24 methods)
    - `nenhumMetodoTemNomeDeApagarOuActualizar` — failed: "Metodo 'saveAndFlush' comeca por prefixo proibido 'saveAndFlush'"
  - Tests 4, 5, 6 (entity `@Immutable`, controller scan, source-file scan) were unaffected by this specific mutation and continued to pass, as expected — they gate different surfaces.
  - File restored via the exact pre-mutation content (captured before mutating); `git diff backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java` after restoration produced **no output** (byte-identical to the committed state). Re-ran the test suite: **Tests run: 6, Failures: 0** — green again.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test` (full suite) — **Tests run: 363, Failures: 0, Errors: 0, BUILD SUCCESS**. Baseline was 357 (per plan's phase_context); 363 = 357 + 6 new tests in `AuditLogImutabilidadeTest`, confirming no other test was affected.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml spotbugs:check` — **BugInstance size is 0, Error size is 0, BUILD SUCCESS**.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=ParecerControllerEntregaProveniencaTest` — Tests run: 6, Failures: 0.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=ResourceControllerUploadDocumentoTest` — Tests run: 2, Failures: 0.
- All grep-based acceptance criteria from the plan (Task 1: `@Immutable`, `name = "detalhe"` with no `nullable = false` on the same line, `papel_permissoes_alterar`/`atribuicao_papel` present, `extends Repository<AuditLog, Long>` present and `JpaRepository` import absent, `buscarEventosRbac` present with `a.tenant_id = :tenantId` appearing twice, `ADD COLUMN IF NOT EXISTS detalhe` present, `7 of 17` present and `6 of 16` absent, `9 scripts are outstanding` present, `128-add-audit-log-detalhe` appearing at least 4 times in the README, and present in DEPLOYMENT.md; Task 2: `ClassPathScanningCandidateComponentProvider` and `buscarEventosRbac` present in the test file, file ≥ 60 lines) were run individually and all matched.

## User Setup Required

None - no external service configuration required. Migration `128-add-audit-log-detalhe.sql` still needs a human operator to run it against any production/staging database running `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, per `backend/migrations/README.md` — this is documented, standard operational process for this repository (no automated migration runner exists), not a new setup requirement introduced by this plan.

## Next Phase Readiness
- The storage contract (`AuditLog.detalhe`, the narrowed `AuditLogRepository` with `buscarEventosRbac`, and the RBAC vocabulary comments) is in place for Plan 02 (`AuditoriaRbacService`, the only intended writer of `detalhe`) and Plan 07 (the paginated RBAC-audit query endpoint) to build on directly.
- `AuditLogImutabilidadeTest` is a standing regression gate: any later plan that tries to widen `AuditLogRepository` or add a mutating audit endpoint will fail this test immediately, by design.
- No blockers for Plan 02.

---
*Phase: 128-auditoria-de-atribui-es-de-pap-is*
*Completed: 2026-09-22*

## Self-Check: PASSED

All 6 created/modified files verified present on disk. Both task commit hashes (`1015c4e9`, `619071f2`) verified present in `git log --oneline --all`. No missing items.
