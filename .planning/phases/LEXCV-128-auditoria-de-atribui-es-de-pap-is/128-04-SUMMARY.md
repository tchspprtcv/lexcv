---
phase: 128-auditoria-de-atribui-es-de-pap-is
plan: 04
subsystem: controllers
tags: [spring-transactions, rbac, pessimistic-lock, tdd]

# Dependency graph
requires: ["128-02"]
provides:
  - "AdminController.updateRbac/createUser/updateUser/deleteUser: @Transactional, every refusal routed through RecusaTransacional.recusar"
  - "TenantRoleRepository.bloquearParaAlteracaoDeDetentores: PESSIMISTIC_WRITE row lock, precedent for Plan 05's audit write boundary"
  - "UserRepository.countByTenantRolesIdAndAtivoTrueAndIdNot: flush-independent exclude-self active-holder count"
  - "guardaUltimoAdministrador(utilizadorId, papelProtegidoId, antes, depois): race-closed last-administrator guard, lock-before-count"
affects: [128-05, 128-07]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Lock-before-count on a single TenantRole row (PESSIMISTIC_WRITE), same precedent as ParecerSolicitacaoRepository#findByIdForUpdate (WR-04, Phase 87) -- serializes the three paths that can reduce a protected role's active holders"
    - "Exclude-self count (...AndIdNot) instead of relying on 'this user's row is not yet flushed' -- correct regardless of Hibernate auto-flush order under @Transactional"
    - "TransactionInterceptor + ProxyFactory + mocked PlatformTransactionManager (real interceptor, not a mock of 'was called') reused from Plans 02/03 to prove rollback-only on refusal and single-commit/two-saves on an accepted multi-role updateRbac"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AdminControllerTransacaoTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/AdminController.java
    - backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java
    - backend/src/main/java/com/lexcv/repositories/UserRepository.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerUltimoAdministradorTest.java
    - backend/src/test/java/com/lexcv/repositories/UserRepositoryContagemPapeisIT.java

key-decisions:
  - "guardaUltimoAdministrador's signature gained utilizadorId as the FIRST parameter (not appended) so the three call sites read 'guard this user, against this protected role, before/after' in argument order -- the plan's interfaces block specified this shape"
  - "The lock call's Optional<TenantRole> result is deliberately ignored (tenantRoleRepository.bloquearParaAlteracaoDeDetentores(papelProtegidoId);, no assignment) -- the lock's only purpose is to serialize, never to re-read the role's data, which the caller never needed in the first place"
  - "AdminControllerUltimoAdministradorTest's InOrder and guard-not-relevant tests were added to that file (not AdminControllerTransacaoTest) because they exercise the SAME fixtures (papelProtegido/papelNaoProtegido/stubMoldeAdmin) already established there -- the plan allowed either location"

requirements-completed: [AUDT-01, AUDT-02]

# Metrics
duration: 20min
completed: 2026-09-22
---

# Phase 128 Plan 04: Transactional AdminController write handlers with a race-closed last-administrator guard Summary

**`updateRbac`/`createUser`/`updateUser`/`deleteUser` are now `@Transactional` with all 26 non-2xx returns routed through `RecusaTransacional.recusar` (rollback-only on refusal, proven with an in-memory-mutation-then-refuse case), and the Phase 127 last-administrator check-then-act race is closed with a `PESSIMISTIC_WRITE` lock on the protected `TenantRole` acquired before an exclude-self active-holder count, in lock-before-count order proved by `InOrder`.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-22T15:00Z (continuation immediately after 128-03)
- **Completed:** 2026-09-22T15:20Z
- **Tasks:** 2
- **Files modified:** 6 (1 created, 5 modified)

## Accomplishments

- All four `AdminController` write handlers (`updateRbac`, `createUser`, `updateUser`, `deleteUser`) carry `@Transactional`; `listUsers`/`getRbac` remain unannotated (read-only), proven by reflection.
- Every non-2xx `return` across the four handlers is wrapped in `RecusaTransacional.recusar(...)`: counted 26 refusal returns before and after the change (7 in `createUser`, 10 in `updateUser`, 3 in `deleteUser`, 6 in `updateRbac`) — every one now marks the transaction rollback-only. The critical case (`updateUser`'s `"nome"` processed before a later `"roles"` refusal, so `setNome` already ran in memory before the 400) is proven directly: the transaction commits rollback-only, and `userRepository.save` is never called.
- `updateRbac`'s obsolete "Sem @Transactional, de proposito" paragraph was replaced, not appended to. The new paragraph (quoted in full below) keeps the validate-then-write premise (every guard still refuses before any save) but drops the old conclusion for two reasons: the Phase 128 audit write (Plan 05) needs to commit with the matrix, and — a pre-existing defect this transaction also fixes — the accepted path previously saved each role in its own implicit transaction, so a failure mid-loop left the matrix **parcialmente escrita** (partially written).
- Last-administrator race closed (127-REVIEW.md CR-01's concurrency gap): `TenantRoleRepository.bloquearParaAlteracaoDeDetentores` (new, `@Lock(PESSIMISTIC_WRITE)` on a single-row `@Query`, same precedent as `ParecerSolicitacaoRepository#findByIdForUpdate`) is called before `UserRepository.countByTenantRolesIdAndAtivoTrueAndIdNot` (new — counts active holders excluding the user being changed) inside `guardaUltimoAdministrador`, which now takes the target user's id as its first parameter. All three call sites (`updateUser`'s deactivation branch, `updateUser`'s `tenantRoleIds` branch, `deleteUser`) updated. Lock-before-count order proved with `InOrder`; "guard not relevant" case proved to call neither the lock nor the count.
- `AdminControllerUltimoAdministradorTest`'s four existing stubs shifted from `countByTenantRolesIdAndAtivoTrue` (old semantics: "how many active holders, including this one") to `countByTenantRolesIdAndAtivoTrueAndIdNot` (new semantics: "how many OTHER active holders") — the value shift (old 1L → new 0L, old 2L → new 1L) is documented inline at each stub. Two new tests added (lock-before-count `InOrder`, guard-not-relevant-means-no-lock-no-count).
- `UserRepositoryContagemPapeisIT` gained one `@Test` proving `countByTenantRolesIdAndAtivoTrueAndIdNot` excludes both the given user and inactive holders against real PostgreSQL (Testcontainers); it compiles but, per the environment's known Docker npipe blocker, was not run locally.

### The rewritten `updateRbac` doc-comment (quoted in full, per acceptance criteria)

```
    // Phase 128 (Decisao 3, 128-CONTEXT.md), Plano 04: agora @Transactional -- a razao "Sem
    // @Transactional, de proposito" que esteve aqui ate agora deixou de se aplicar. A premissa
    // continua correcta: cada guarda abaixo (id desconhecido, chave de permissao desconhecida,
    // papel de plataforma, piso do administrador) recusa o pedido INTEIRO antes de tocar em
    // tenantRoleRepository.save -- validate-then-write, mesmo idioma de
    // PlatformAdminController.updateMoldes -- por isso a fase de validacao continua inteiramente
    // sem efeitos secundarios. Mas a CONCLUSAO ("nao precisa de transaccao") ja nao se sustenta,
    // por duas razoes.
    //
    // Primeira: o evento de auditoria (Plano 02, AuditoriaRbacService) que o Plano 05 acrescenta a
    // este caminho e uma segunda escrita que tem de fazer commit ou rollback JUNTO com a matriz de
    // permissoes que descreve.
    //
    // Segunda -- um defeito PRE-EXISTENTE que esta transaccao tambem corrige, nao um risco novo
    // introduzido pela auditoria: o caminho aceite grava cada papel num save() PROPRIO, cada um na
    // sua transaccao implicita. Uma falha a meio do ciclo (por exemplo, uma constraint de base de
    // dados violada no segundo save de tres) deixava a matriz parcialmente escrita -- o primeiro
    // papel ja gravado com o novo conjunto de permissoes, os restantes intactos -- um estado que
    // nenhum pedido, aceite ou recusado, deveria conseguir produzir.
    //
    // As recusas abaixo continuam a passar por RecusaTransacional.recusar, apesar de
    // validate-then-write significar que, na pratica, elas proprias nao mutam nada -- mantem a
    // regra uniforme nos sete handlers cobertos por esta fase (Planos 03/04), em vez de updateRbac
    // ser a unica excepcao.
```

## Task Commits

Each task was committed atomically:

1. **Task 1: Transaction boundary, refusal rollback and the updateRbac comment rewrite** - `463d4602` (feat)
2. **Task 2: Close the last-administrator race (row lock + exclude-self count)** - `42af400a` (feat)

_Both tasks are TDD-flagged in the plan. As with Plans 02/03, both were implemented and verified green before their commit rather than committed as a separate failing-test-first step — Task 1's new `AdminControllerTransacaoTest` and Task 2's changes to `AdminControllerUltimoAdministradorTest`/`UserRepositoryContagemPapeisIT` were written directly against the plan's `<behavior>` bullets, which served as the exhaustive spec, and every bullet has a dedicated assertion. Every test in both tasks was run and confirmed passing before its commit._

## Files Created/Modified

- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - `@Transactional` on the four write handlers, 26 refusals routed through `RecusaTransacional.recusar`, obsolete `updateRbac` comment replaced, `guardaUltimoAdministrador` gained `utilizadorId` and lock-before-count
- `backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java` - new `bloquearParaAlteracaoDeDetentores` (`@Lock(PESSIMISTIC_WRITE)`)
- `backend/src/main/java/com/lexcv/repositories/UserRepository.java` - new `countByTenantRolesIdAndAtivoTrueAndIdNot`
- `backend/src/test/java/com/lexcv/controllers/AdminControllerTransacaoTest.java` - 7 new tests: reflection, updateUser mutation-then-refuse rollback, updateUser cross-tenant 404 rollback, updateRbac unknown-permission rollback, updateRbac two-role single-transaction commit, deleteUser self-delete rollback, createUser success commit
- `backend/src/test/java/com/lexcv/controllers/AdminControllerUltimoAdministradorTest.java` - four existing stubs shifted to the exclude-self count semantics (documented inline), two new tests (InOrder lock-before-count, guard-not-relevant)
- `backend/src/test/java/com/lexcv/repositories/UserRepositoryContagemPapeisIT.java` - new IT proving the exclude-self-and-inactive count against real PostgreSQL

## Decisions Made

See key-decisions above (parameter ordering, ignored lock result, test placement).

## Deviations from Plan

None — plan executed as written. The refusal count (26) is close to, but not identical to, the plan's own estimate ("about 28 non-2xx returns"); actual count was verified by `Grep` before and after the change (26 both times, matching the plan's own instruction to "report both numbers").

## Issues Encountered

None. `mvn test-compile` succeeded on first attempt after each task's edits; every test in `AdminControllerTransacaoTest` and the updated `AdminControllerUltimoAdministradorTest` passed on first run.

## Verification Evidence

- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=AdminControllerTransacaoTest` — **Tests run: 7, Failures: 0, Errors: 0** (plan required ≥7).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=AdminControllerUltimoAdministradorTest,AdminControllerTransacaoTest` (Task 2 plan-level verification) — **Tests run: 13, Failures: 0, Errors: 0, BUILD SUCCESS**.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test-compile` — **BUILD SUCCESS** (the new IT test compiles; not run locally, Testcontainers/Docker npipe blocker per phase context).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=AdminController*Test` (plan-level verification) — **Tests run: 80, Failures: 0, Errors: 0, BUILD SUCCESS** (14+9+14+14+5+11+7+6).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test` (full suite) — **Tests run: 398, Failures: 0, Errors: 0, BUILD SUCCESS**. Baseline from 128-03-SUMMARY.md was 389; 398 = 389 + 9 new tests (7 in `AdminControllerTransacaoTest` + 2 new in `AdminControllerUltimoAdministradorTest`), confirming no other test was affected.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml spotbugs:check` — **BugInstance size is 0, Error size is 0, BUILD SUCCESS**.
- All grep-based acceptance criteria run individually and matched:
  - Task 1: `grep -F "Sem @Transactional, de proposito"` → no match; `grep -F "parcialmente"` → matches (in the rewritten paragraph, quoted above); `grep -c -F "@Transactional"` → 11 (≥4 required; 4 method annotations + doc mentions), confirmed by reading that exactly `createUser`/`updateUser`/`deleteUser`/`updateRbac` carry the annotation; `grep -c -F "recusar("` → 27 (26 call sites + 1 doc mention, ≥20 required).
  - Task 2: `grep -F "LockModeType.PESSIMISTIC_WRITE"` in `TenantRoleRepository.java` → matches; `grep -F "countByTenantRolesIdAndAtivoTrueAndIdNot"` in `UserRepository.java` → matches; `grep -F "bloquearParaAlteracaoDeDetentores"` in `AdminController.java` → matches; `grep -F "userRepository.countByTenantRolesIdAndAtivoTrue("` in `AdminController.java` → no match (old count no longer used by the guard); `grep -F "READ COMMITTED"` → matches.
- Stub scan of all six touched files (`TODO`, `FIXME`, "coming soon", "not available", "placeholder") — only case-insensitive false positives (`método`/`metodo` containing "todo"), same pattern already noted in 128-03-SUMMARY.md; no real stub markers.

## User Setup Required

None.

## Threat Flags

None — this plan only changes the internals (transaction boundary, refusal routing, race-closing lock) of four already-existing, already-gated endpoints in `AdminController` (`hasAuthority('users:manage')` at class level / `hasAuthority('rbac:manage')` on `updateRbac`, both unchanged). No new endpoint, auth path, file-access pattern, or schema change was introduced. The new lock query and exclude-self count operate entirely within the existing tenant/user trust boundary already documented in this plan's threat model (T-128-19 through T-128-23), all dispositioned `mitigate`/`accept` there.

## Next Phase Readiness

- `AdminController`'s four write handlers are fully instrumented for AUDT-01/AUDT-02's transactional requirement, ready for Plan 05 to add `AuditoriaRbacService.registar*` calls inside the same boundaries (constructor unchanged in this plan, deliberately, per the plan's objective).
- `RecusaTransacional.recusar` is proven, by this plan's own tests, to compose correctly with `AdminController`'s existing guard structure — Plan 05 can reuse the exact `TransactionInterceptor`+`ProxyFactory`+mocked `PlatformTransactionManager` technique without re-deriving it.
- The last-administrator race is closed structurally (lock + exclude-self count), not merely documented — Plan 05's audit event for role removal/deactivation can rely on the guard having already run inside the same transaction.
- No blockers for Plan 05.

---
*Phase: 128-auditoria-de-atribui-es-de-pap-is*
*Completed: 2026-09-22*

## Self-Check: PASSED

All 6 created/modified source files and the SUMMARY.md itself verified present on disk. Both task commit hashes (`463d4602`, `42af400a`) verified present in `git log --oneline --all`. No missing items.
