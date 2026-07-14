---
phase: LEXCV-93-notf-24-preferencias-de-notificacao-por-utilizador
reviewed: 2026-07-14T15:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/NotificacaoPreferencia.java
  - backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java
  - backend/migrations/93-create-notificacao-preferencia-table.sql
  - web/src/types/notificacoes.ts
  - web/src/lib/notificacao-categoria.ts
  - web/src/hooks/use-notificacao-preferencias.ts
  - web/src/app/(dashboard)/settings/page.tsx
findings:
  critical: 0
  warning: 1
  info: 3
  total: 4
status: issues_found
---

# Phase LEXCV-93: Code Review Report (re-review)

**Reviewed:** 2026-07-14T15:00:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

This is a re-review of the NOTF-24 "per-user notification category preferences" feature after the
93-REVIEW-FIX.md iteration that claimed to resolve WR-01/WR-02/WR-03 from the prior review. I
independently re-verified the code (not the fix report's claims) against the actual runtime
semantics of the stack in use (Spring Data JPA / Hibernate 6 / PostgreSQL, Spring Boot 3.4.1).

WR-02 (frontend/backend silenciável-list duplication) and WR-03 (missing error state in the
settings preferences tab) are genuinely fixed — confirmed by direct inspection of
`web/src/lib/notificacao-categoria.ts` and the `NotificationPreferencesTab` component in
`web/src/app/(dashboard)/settings/page.tsx`.

WR-01 is **not actually fixed**, despite 93-REVIEW-FIX.md marking it "Fixed" and citing a passing
unit test. The applied `try { ... } catch (DataIntegrityViolationException e)` wraps a plain
`repository.save(...)` call, but Spring Data JPA does not flush a newly persisted entity to the
database synchronously inside `save()` — for an ID strategy that doesn't require a DB round-trip to
obtain the identifier (`GenerationType.UUID`, as used here), the actual `INSERT` is deferred until
the surrounding `@Transactional` method returns and Spring's transaction interceptor commits the
transaction. That means the real unique-constraint violation from a genuine concurrent race is
thrown *after* `silenciarCategoria()`'s try/catch has already exited normally, so it still escapes to
`GlobalExceptionHandler`'s generic `Exception` handler and still produces a raw HTTP 500 leaking the
exception class name/message to the client — the exact defect the original WR-01 described. The
regression is invisible in the current test suite because the only test exercising this path
(`NotificacaoServiceTest`) mocks the repository and has `save(any())` throw synchronously, which
does not reflect how a real `EntityManager`/Postgres round-trip behaves; there is no
`@DataJpaTest` + Testcontainers integration test for `NotificacaoPreferenciaRepository` (the
codebase already has this exact infrastructure, demonstrated by `NotificacaoRepositoryIT`, but it
was not applied here).

Tenant/user dual-scoping remains correct throughout, the mute guard is still the single choke point
in `criar()`, and no SQL injection, hardcoded secrets, or authorization bypass was found. Two
previously-reported Info items (asymmetric `reativar` validation, `:view`-scoped mutating endpoints)
remain open — the fix report explicitly deferred them as Info-tier, so they are re-listed here for
completeness rather than as new findings.

## Warnings

### WR-01: The "fix" for the `silenciarCategoria()` concurrency race does not work — the exception it tries to catch is thrown after the method has already returned

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:303-327`
**Issue:**
```java
@Transactional
public void silenciarCategoria(UUID tenantId, UUID userId, String categoria) {
    ...
    try {
        if (!notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria)) {
            notificacaoPreferenciaRepository.save(NotificacaoPreferencia.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .categoria(categoria)
                    .build());
        }
    } catch (DataIntegrityViolationException ex) {
        log.debug(...);
    }
}
```
`NotificacaoPreferencia.id` uses `@GeneratedValue(strategy = GenerationType.UUID)`, an in-memory ID
generator that does **not** require an immediate DB round-trip (unlike `IDENTITY`). Spring Data
JPA's `SimpleJpaRepository.save()` for a new entity just calls `entityManager.persist(...)` — it does
not call `flush()`. With Hibernate's default `FlushModeType.AUTO` and no subsequent query in this
method to trigger an auto-flush, the actual `INSERT` statement (and therefore any
`uk_notificacao_preferencia` unique-constraint violation from a genuine concurrent request) is
deferred until the enclosing `@Transactional` boundary commits — which happens in Spring's
`TransactionInterceptor`, **after** `silenciarCategoria()` (including its try/catch) has already
returned control to the caller. So on an actual two-tabs/double-click race:
1. Both requests pass the `existsBy...` check (READ_COMMITTED, neither sees the other's uncommitted row).
2. Both call `.save(...)`, which just registers the pending insert in-memory — no exception yet, so the try/catch sees nothing to catch, and both methods return normally.
3. At commit time, one of the two physical `INSERT`s violates the unique index. That failure surfaces from `JpaTransactionManager`'s commit path, entirely outside the method body that contains the try/catch.
4. The resulting `DataIntegrityViolationException` (or a `TransactionSystemException` wrapping it, depending on translation path) propagates uncaught to `NotificacaoController.silenciar()`, which only catches `IllegalArgumentException`, and falls through to `GlobalExceptionHandler.handleAllExceptions` (`backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java:42-49`) — an HTTP 500 with `error: ex.getClass().getSimpleName()` and the raw exception message, surfaced verbatim to the user via `apiFetch`'s automatic error toast.

This is precisely the outcome the original WR-01 finding described, and 93-REVIEW-FIX.md's "Fixed"
verification (`mvn -o compile` + a Mockito unit test whose stub makes `save(any())` throw
synchronously) cannot detect it, because a mocked repository does not reproduce Hibernate's
deferred-flush timing. No repository-level or `@DataJpaTest` integration test exists for
`NotificacaoPreferenciaRepository` to exercise this with a real Postgres transaction (contrast with
`backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java`, which already
establishes the Testcontainers pattern this table needs but never got).
**Fix:** Force the flush to happen inside the try block, so the constraint violation is thrown while
still inside the guarded region:
```java
try {
    if (!notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria)) {
        notificacaoPreferenciaRepository.saveAndFlush(NotificacaoPreferencia.builder()
                .tenantId(tenantId)
                .userId(userId)
                .categoria(categoria)
                .build());
    }
} catch (DataIntegrityViolationException ex) {
    log.debug("silenciarCategoria: concurrent insert for {}/{}/{}, treating as success",
            tenantId, userId, categoria);
}
```
`saveAndFlush` is already available on `JpaRepository` (no repository interface change needed) and
guarantees the `INSERT` — and thus any unique-constraint violation — executes synchronously where the
catch block can actually observe it. Alternatively, replace the check-then-act pair with a single
native `INSERT ... ON CONFLICT (tenant_id, user_id, categoria) DO NOTHING` query, which is atomic at
the database level and removes the race entirely. Either way, add a `@DataJpaTest` +
Testcontainers-backed integration test (mirroring `NotificacaoRepositoryIT`) that actually issues two
concurrent inserts for the same `(tenant, user, categoria)` and asserts no exception escapes — the
current mocked unit tests cannot validate this fix.

## Info

### IN-01: `reativar` endpoint still does not validate `categoria`, unlike `silenciar` (carried over, unresolved)

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:138-143`
**Issue:** Unchanged since the prior review. `reativar()` calls `reativarCategoria()`, which goes
straight to a derived delete with no validation via `CategoriaNotificacao.fromString(...)`, unlike
`silenciar()`. `DELETE /notificacoes/preferencias/TYPO_CATEGORY` still returns `200` with
`"silenciada": false` for any nonexistent string. Still harmless (no row can exist to delete), but
the asymmetric validation contract between two sibling endpoints remains and silently swallows
client-side typos.
**Fix:** Validate via `CategoriaNotificacao.fromString(categoria)` in `reativarCategoria()` (or the
controller) and return 400 for unknown values, mirroring `silenciar`'s contract.

### IN-02: Mutating preference endpoints still gated by `notificacoes:view`, not an edit/manage scope (carried over, unresolved)

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:115-143`
**Issue:** Unchanged since the prior review. `listarPreferencias` (GET), `silenciar` (PUT) and
`reativar` (DELETE) are all still gated on `hasAuthority('notificacoes:view')`, not an edit/manage
scope, per the `scope:action` convention documented in `CLAUDE.md`. As before, this mirrors the
pre-existing `marcarLida`/`marcarTodasLidas` precedent and every seeded role currently has
`notificacoes:view`, so there is no active privilege-escalation path — flagging for awareness only,
in case a future read-only/reporting role is introduced with `notificacoes:view` but not intended to
mutate its own preferences.
**Fix:** Consider a dedicated `notificacoes:edit` scope for the mutating endpoints in a follow-up, or
explicitly document that notification self-service actions are intentionally `:view`-scoped because
they only ever touch the caller's own data.

### IN-03: No dedicated test coverage exists for `NotificacaoPreferenciaRepository` against a real database

**File:** `backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java`
**Issue:** All current coverage of this repository is indirect, through `NotificacaoServiceTest`'s
Mockito stubs. There is no `@DataJpaTest` (Testcontainers-backed, per the established
`NotificacaoRepositoryIT` pattern) verifying that `uk_notificacao_preferencia` actually rejects a
duplicate `(tenant_id, user_id, categoria)` insert at the database level, that
`deleteByTenantIdAndUserIdAndCategoria` is scoped correctly across tenants/users, or that
`existsByTenantIdAndUserIdAndCategoria` behaves as expected against real Postgres. This gap is
directly related to WR-01 above — a real integration test would have caught (and would have
prevented re-introducing) that defect.
**Fix:** Add a `NotificacaoPreferenciaRepositoryIT` mirroring `NotificacaoRepositoryIT`'s
`@DataJpaTest` + `@ServiceConnection` Testcontainers setup, covering at minimum: unique-constraint
enforcement on duplicate insert, tenant/user-scoped `existsBy...`/`findBy...`/`deleteBy...`
correctness, and (once WR-01 is properly fixed) the concurrent-insert-race behavior.

---

_Reviewed: 2026-07-14T15:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
