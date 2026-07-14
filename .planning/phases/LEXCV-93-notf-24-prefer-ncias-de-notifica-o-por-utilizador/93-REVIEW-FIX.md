---
phase: LEXCV-93-notf-24-preferencias-de-notificacao-por-utilizador
fixed_at: 2026-07-14T11:51:34Z
review_path: .planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/93-REVIEW.md
iteration: 2
findings_in_scope: 1
fixed: 1
skipped: 0
status: all_fixed
---

# Phase LEXCV-93: Code Review Fix Report

**Fixed at:** 2026-07-14T11:51:34Z
**Source review:** .planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/93-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope (this iteration): 1 (fix_scope: critical_warning — WR-01 re-opened; IN-01/IN-02/IN-03 excluded as Info-tier, though the WR-01 fix's added test also closes IN-03's coverage gap)
- Fixed: 1
- Skipped: 0

This is a re-review fix pass. The 2026-07-14 re-review found that the iteration-1 fix for WR-01
did not actually work: it caught `DataIntegrityViolationException` around a plain `repository.save(...)`
call, but `NotificacaoPreferencia.id` uses `GenerationType.UUID` (an in-memory ID generator), so
Spring Data's `save()` only calls `entityManager.persist()` — the real `INSERT` (and any
unique-constraint violation) is deferred until the surrounding `@Transactional` method's transaction
commits, which happens *after* the try/catch has already exited. The exception therefore still
escaped to `GlobalExceptionHandler`'s generic handler as a raw HTTP 500 on a genuine concurrent race.
WR-02 and WR-03 (also from the iteration-1 report, and independently reverified by iteration-2's
reviewer as "genuinely fixed") are unaffected by this pass and are carried forward below for history.

## Fixed Issues (Iteration 2)

### WR-01: The "fix" for the `silenciarCategoria()` concurrency race did not work — the exception it tried to catch was thrown after the method had already returned

**Files modified:**
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
- `backend/src/test/java/com/lexcv/repositories/NotificacaoPreferenciaRepositoryIT.java` (new)

**Commit:** ae35bbb

**Applied fix:** Changed `notificacaoPreferenciaRepository.save(...)` to
`notificacaoPreferenciaRepository.saveAndFlush(...)` inside `silenciarCategoria()`'s guarded
try/catch block. `saveAndFlush` forces Hibernate to issue the actual `INSERT` (and therefore any
`uk_notificacao_preferencia` unique-constraint violation) synchronously, inside the try/catch's
dynamic scope, rather than deferring it to the enclosing transaction's commit — which is what made
the iteration-1 fix ineffective. Added an explanatory comment at the call site documenting exactly
why `saveAndFlush` (not `save`) is required here, referencing the `GenerationType.UUID` root cause.

Updated `NotificacaoServiceTest`'s two mock-based assertions
(`silenciarCategoria_categoriaValidaAindaNaoSilenciada_persisteUmaLinha` and
`silenciarCategoria_jaSilenciada_naoPersisteSegundaLinha`) to verify `saveAndFlush(any())` instead of
`save(any())`, since the mocked repository call changed. All 29 tests in `NotificacaoServiceTest`
pass (`mvn -o -Dtest=NotificacaoServiceTest test`, surefire report: 29 run / 0 failures / 0 errors).

Added `NotificacaoPreferenciaRepositoryIT`, a new `@DataJpaTest` + Testcontainers (`postgres:16-alpine`)
integration test mirroring the established `NotificacaoRepositoryIT` / `ParecerVersaoConcorrenciaIT`
pattern (`@AutoConfigureTestDatabase(replace = Replace.NONE)` + `@ServiceConnection`). It covers:
- Unique-constraint enforcement: a duplicate `(tenant_id, user_id, categoria)` `saveAndFlush` throws
  `DataIntegrityViolationException` against a real Postgres instance.
- Tenant/user-scoped correctness of `existsByTenantIdAndUserIdAndCategoria`,
  `findByTenantIdAndUserId`, and `deleteByTenantIdAndUserIdAndCategoria` (no cross-tenant/cross-user
  leakage).
- The concurrent-race scenario itself: two independently-committed transactions (via
  `TransactionTemplate`, `@Transactional(propagation = NOT_SUPPORTED)` on the test method to disable
  `@DataJpaTest`'s default rollback wrapping, released simultaneously via a `CountDownLatch`) both
  run the exact `existsBy... → saveAndFlush → catch(DataIntegrityViolationException)` sequence that
  `silenciarCategoria()` executes. Asserts that neither `Future.get(...)` throws
  `ExecutionException` (i.e. the losing transaction's constraint violation is caught inside its own
  try/catch, never escaping) and that exactly one row ends up persisted. This test would have failed
  before the fix (with a plain `save()`, the violation surfaces at `TransactionTemplate`'s commit,
  outside the callback's try/catch, exactly mirroring how it would surface at
  `TransactionInterceptor`'s commit in production) and passes after it — closing the IN-03 coverage
  gap (no dedicated real-database test existed for this repository) at the same time.

This new integration test requires a running Docker daemon (Testcontainers) to execute; it was not
run in this sandbox because the local Docker Desktop daemon was not reachable
(`failed to connect to the docker API at npipe:////./pipe/dockerDesktopLinuxEngine`). It was,
however, verified to compile cleanly together with the rest of the module
(`mvn -o -DskipTests test-compile`, exit code 0), and its logic was manually traced against
Postgres's `READ COMMITTED` unique-index-conflict blocking behavior (the second concurrent `INSERT`
blocks on the first transaction's uncommitted index entry, then raises the violation once the first
commits) to confirm it exercises the intended race. **Recommend running this test with Docker
available before merging**, to get an actual green/red signal rather than a compile-only check.

## Skipped Issues (Iteration 2)

None — the single in-scope finding was fixed.

## Carried Forward From Iteration 1 (for history — not re-verified this pass)

The iteration-2 re-review independently reconfirmed WR-02 and WR-03 below as genuinely fixed by
direct code inspection; they required no further action this pass.

### WR-02: "Which categories can be silenced" was duplicated between backend and frontend with no shared source of truth

**Files modified:** `web/src/lib/notificacao-categoria.ts`, `web/src/app/(dashboard)/settings/page.tsx`
**Commit:** 0f5df1d
**Applied fix (iteration 1):** Centralized the silenciável-category exclusion list into
`NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS` / `NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS` in
`notificacao-categoria.ts`, consumed by `NotificationPreferencesTab` instead of an inline filter
literal. Re-verified fixed by the iteration-2 reviewer.

### WR-03: `NotificationPreferencesTab` had no error state for the preferences fetch

**Files modified:** `web/src/app/(dashboard)/settings/page.tsx`
**Commit:** bae3de3
**Applied fix (iteration 1):** Added an `isError` branch (with retry via `refetch()`) to
`NotificationPreferencesTab`, replacing the previous silent fallback to an all-categories-delivered
default on fetch failure. Re-verified fixed by the iteration-2 reviewer.

---

_Fixed: 2026-07-14T11:51:34Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
