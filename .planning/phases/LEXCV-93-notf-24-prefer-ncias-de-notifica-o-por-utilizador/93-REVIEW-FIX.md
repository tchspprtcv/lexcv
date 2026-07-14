---
phase: LEXCV-93-notf-24-preferencias-de-notificacao-por-utilizador
fixed_at: 2026-07-14T12:16:17Z
review_path: .planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/93-REVIEW.md
iteration: 3
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-93: Code Review Fix Report

**Fixed at:** 2026-07-14T12:16:17Z
**Source review:** .planning/phases/LEXCV-93-notf-24-prefer-ncias-de-notifica-o-por-utilizador/93-REVIEW.md
**Iteration:** 3

**Summary:**
- Findings in scope (this iteration): 2 — CR-01 (fix_scope: critical_warning) plus IN-03, explicitly
  added to this pass per the spawning task's instructions ("include this as part of the same fix
  pass since it's a one-line, low-risk correction directly related to the same entity"), despite
  Info-tier findings normally being excluded under `critical_warning` scope. IN-01/IN-02 remain
  excluded (Info-tier, not called out for inclusion this pass).
- Fixed: 2
- Skipped: 0

This is a third-pass re-review fix. The 2026-07-14T16:30:00Z re-review escalated the iteration-2
"fix" for the `silenciarCategoria()` concurrency race to Critical: the iteration-2 fix
(`saveAndFlush()` + inline `catch(DataIntegrityViolationException)`) correctly forced the `INSERT`
to happen synchronously, but never addressed that PostgreSQL aborts the **entire** transaction the
instant any statement violates a constraint. Because `silenciarCategoria()` is itself the outermost
`@Transactional` boundary, catching the translated exception locally does not "un-abort" the
underlying Postgres transaction — the interceptor's subsequent implicit `COMMIT` would either throw
(relocating the original WR-01 symptom from flush-time to commit-time) or be silently treated as a
rollback by the driver, hiding the failure from the caller. This iteration replaces the pattern
entirely with an atomic native upsert, which sidesteps the problem by never raising an exception on
the expected "already exists" code path.

## Fixed Issues (Iteration 3)

### CR-01: `silenciarCategoria()`'s `saveAndFlush` + inline catch does not actually neutralize the concurrency race against real PostgreSQL

**Files modified:**
- `backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java`
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
- `backend/src/test/java/com/lexcv/repositories/NotificacaoPreferenciaRepositoryIT.java`

**Commit:** c3cab8a

**Applied fix:** Added `NotificacaoPreferenciaRepository.upsertSilenciar(tenantId, userId, categoria)`,
a `@Modifying @Query(nativeQuery = true)` method executing
`INSERT INTO t_notificacao_preferencia (id, tenant_id, user_id, categoria, created_at) VALUES
(gen_random_uuid(), :tenantId, :userId, :categoria, now()) ON CONFLICT (tenant_id, user_id, categoria)
DO NOTHING` — mirroring the existing native-query precedent (`NotificacaoRepository.buscarPorFiltros`).
This is the first `@Modifying` query in the codebase; the code comment on the new method flags this
explicitly and explains why a derived/JPQL upsert cannot express `ON CONFLICT DO NOTHING` (a native
query is required). `gen_random_uuid()` is safe here — the project's Postgres image is `postgres:16-alpine`
everywhere (dev/prod/Testcontainers), and `gen_random_uuid()` has been a core built-in since PostgreSQL 13
(no `pgcrypto` extension needed).

Rewrote `NotificacaoService.silenciarCategoria()` to a single call to `upsertSilenciar(...)`, removing
the `existsByTenantIdAndUserIdAndCategoria(...)` pre-check, the `saveAndFlush(...)` call, and the
`catch (DataIntegrityViolationException ...)` block entirely — none of the three are needed anymore,
since the atomic `ON CONFLICT DO NOTHING` upsert *is* the idempotency check and never throws on the
"already silenced" path. Removed the now-unused `org.springframework.dao.DataIntegrityViolationException`
import from the service (confirmed via grep that it had no other use in the file).

Updated `NotificacaoServiceTest`:
- `silenciarCategoria_prazoVencido_lancaIllegalArgumentException` and
  `silenciarCategoria_categoriaDesconhecida_lancaIllegalArgumentException` now verify
  `never().upsertSilenciar(any(), any(), any())` instead of `never().save(any())`.
- The two tests that stubbed `existsByTenantIdAndUserIdAndCategoria(...)` and verified
  `saveAndFlush(any())`/`never().saveAndFlush(any())` (`silenciarCategoria_categoriaValidaAindaNaoSilenciada_persisteUmaLinha`
  and `silenciarCategoria_jaSilenciada_naoPersisteSegundaLinha`) were collapsed into one test,
  `silenciarCategoria_categoriaValida_delegaIdempotenciaAoUpsertAtomicoSemPreCheckExistsBy`, which
  verifies `upsertSilenciar(...)` is called exactly once with the correct arguments and that
  `existsByTenantIdAndUserIdAndCategoria(...)` is never called. The old "já silenciada → não persiste
  segunda linha" assertion is no longer expressible against a mocked repository — the idempotency
  guarantee now lives entirely inside the native SQL statement, not in Java branching — so that
  guarantee is proven instead by the real-database concurrency test below. All 28 tests in
  `NotificacaoServiceTest` pass (`mvn test -Dtest=NotificacaoServiceTest`: 28 run / 0 failures / 0 errors).

Updated `NotificacaoPreferenciaRepositoryIT`'s concurrency test
(`silenciarCategoria_duasTransacoesConcorrentes_perdedoraApanhaExcecaoSemEscaparDoCatch` →
renamed `upsertSilenciar_duasTransacoesConcorrentes_nenhumaLancaExcecaoEUmaUnicaLinhaPersiste`): both
concurrent transactions now call `notificacaoPreferenciaRepository.upsertSilenciar(...)` directly
(the exact call `silenciarCategoria()` now makes), instead of replicating the old
`existsBy...→saveAndFlush→catch` sequence inline. The assertion changed from "neither `Future.get(...)`
throws `ExecutionException`" (proving the old catch worked) to the same shape but exercising the new
call — plus the pre-existing assertion that exactly one row survives. The other three tests in this
IT class (`uniqueConstraint_insercaoDuplicada_rejeitadaComDataIntegrityViolationException`,
`existsByTenantIdAndUserIdAndCategoria_...`, `findByTenantIdAndUserId_...`,
`deleteByTenantIdAndUserIdAndCategoria_...`) were left unchanged — they exercise repository methods
that are unaffected by this fix (`existsByTenantIdAndUserIdAndCategoria` is still used by `criar()`'s
mute guard; the raw unique-constraint test still validates the DB constraint directly via `saveAndFlush`).

**Verification performed:** `mvn -q compile test-compile` succeeded (exit 0) for the whole backend
module with all four files changed, confirming the new `@Modifying @Query` compiles and the removed
import doesn't break anything. `mvn test -Dtest=NotificacaoServiceTest` ran and passed (28/0/0/0). The
new/renamed `NotificacaoPreferenciaRepositoryIT` concurrency test requires a running Docker daemon
(Testcontainers) and could **not** be executed in this sandbox for the same reason both the
iteration-2 fixer and the iteration-3 reviewer documented (Docker Desktop's backend process is not
reachable here). **Recommend running the full `NotificacaoPreferenciaRepositoryIT` suite against real
PostgreSQL (Docker/CI) before merging**, specifically the renamed concurrency test, to get an actual
green/red signal for the atomic-upsert claim — this is a logic/runtime-semantics fix, and compile-only
verification does not prove the `ON CONFLICT DO NOTHING` behaves as expected under real concurrent
load. Flagging this finding as requiring human/CI verification of the integration test run, per the
logic-bug limitation in the fixer's verification strategy — the *code* fix itself (replacing the
provably-broken try/catch pattern with a database-native atomic operation that cannot throw on the
conflict path) is a correct, well-established pattern independent of test execution, but the specific
IT proof has not been run against a real database in this pass either.

### IN-03: `NotificacaoPreferencia.createdAt` was not marked `nullable = false`, unlike the manual production migration's `NOT NULL` column

**Files modified:** `backend/src/main/java/com/lexcv/models/NotificacaoPreferencia.java`
**Commit:** ab50d90
**Applied fix:** Added `nullable = false` to the `@Column(name = "created_at", ...)` annotation on
`createdAt`, matching `tenantId`/`userId`'s existing `nullable = false` and
`backend/migrations/93-create-notificacao-preferencia-table.sql`'s `created_at TIMESTAMP NOT NULL`.
`@PrePersist` already unconditionally sets this field before every insert, so this is a documentation/
schema-generation correction (keeps Hibernate's dev `ddl-auto=update` DDL in lockstep with the
hand-written prod migration) rather than a behavioral change. Included in this pass per explicit
instruction from the spawning task, despite `fix_scope: critical_warning` normally excluding Info-tier
findings.

**Verification performed:** `mvn -q compile test-compile` succeeded (exit 0) — a one-line annotation
attribute addition, re-read to confirm text present and surrounding code intact.

## Skipped Issues (Iteration 3)

None — both in-scope findings were fixed.

## Not Addressed This Pass (Info, out of scope)

### IN-01: `reativar` endpoint still does not validate `categoria`, unlike `silenciar` (carried over, unresolved)

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:138-143`
**Status:** Not in scope this pass (`fix_scope: critical_warning`, and not called out for explicit
inclusion). Unchanged since the prior two reviews — see 93-REVIEW.md for full detail.

### IN-02: Mutating preference endpoints still gated by `notificacoes:view`, not an edit/manage scope (carried over, unresolved)

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:115-143`
**Status:** Not in scope this pass (`fix_scope: critical_warning`, and not called out for explicit
inclusion). No active privilege-escalation path today (every seeded role has `notificacoes:view`) —
flagged for awareness only, per 93-REVIEW.md.

## Carried Forward From Iteration 2 (for history — not re-verified this pass)

### WR-01: The iteration-1 "fix" for the `silenciarCategoria()` concurrency race did not work; iteration-2's own fix was then found (this pass) to be insufficient in turn

**Commit (iteration 2):** ae35bbb — superseded this pass by commit c3cab8a (see CR-01 above), which
replaces `saveAndFlush()` + catch with the atomic native upsert.

## Carried Forward From Iteration 1 (for history — not re-verified this pass)

### WR-02: "Which categories can be silenced" was duplicated between backend and frontend with no shared source of truth

**Files modified:** `web/src/lib/notificacao-categoria.ts`, `web/src/app/(dashboard)/settings/page.tsx`
**Commit:** 0f5df1d
**Applied fix (iteration 1):** Centralized the silenciável-category exclusion list into
`NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS` / `NOTIFICACAO_CATEGORIA_SILENCIAVEIS_OPTIONS` in
`notificacao-categoria.ts`, consumed by `NotificationPreferencesTab` instead of an inline filter
literal. Re-verified fixed by the iteration-2 reviewer; unaffected by this pass.

### WR-03: `NotificationPreferencesTab` had no error state for the preferences fetch

**Files modified:** `web/src/app/(dashboard)/settings/page.tsx`
**Commit:** bae3de3
**Applied fix (iteration 1):** Added an `isError` branch (with retry via `refetch()`) to
`NotificationPreferencesTab`, replacing the previous silent fallback to an all-categories-delivered
default on fetch failure. Re-verified fixed by the iteration-2 reviewer; unaffected by this pass.

---

_Fixed: 2026-07-14T12:16:17Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
