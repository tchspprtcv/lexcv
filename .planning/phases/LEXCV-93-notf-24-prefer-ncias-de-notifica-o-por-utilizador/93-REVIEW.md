---
phase: LEXCV-93-notf-24-preferencias-de-notificacao-por-utilizador
reviewed: 2026-07-14T16:30:00Z
depth: standard
files_reviewed: 8
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/NotificacaoPreferencia.java
  - backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoPreferenciaRepository.java
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java
  - backend/src/test/java/com/lexcv/repositories/NotificacaoPreferenciaRepositoryIT.java
  - web/src/lib/notificacao-categoria.ts
  - web/src/app/(dashboard)/settings/page.tsx
findings:
  critical: 1
  warning: 0
  info: 3
  total: 4
status: issues_found
---

# Phase LEXCV-93: Code Review Report (re-review, iteration 3)

**Reviewed:** 2026-07-14T16:30:00Z
**Depth:** standard
**Files Reviewed:** 8
**Status:** issues_found

## Summary

This is a third-pass re-review of NOTF-24, performed after 93-REVIEW-FIX.md's iteration-2 fix for
WR-01 (`silenciarCategoria()`'s concurrency race). I independently re-verified the applied fix
against actual Hibernate/Spring/PostgreSQL transaction semantics rather than accepting the fix
report's own "manually traced" verification, because the fix report explicitly admits its new
`NotificacaoPreferenciaRepositoryIT` concurrency test was **never executed against a real database**
(Docker was unreachable in that sandbox). I attempted to start Docker locally to get an empirical
answer before writing this report; `docker version` succeeds but `docker ps`/Testcontainers cannot
reach the daemon in this environment either (`Docker Desktop.exe`'s backend process exits
immediately), so I could not run the new IT test to get a definitive pass/fail signal, and neither
could the fix's author. Given that, my finding below is a reasoned technical objection, not an
empirically-confirmed test failure — the fix report's own text already flags that it needs to be
run with Docker before merging, and I could not discharge that recommendation either. I am
escalating it to Critical because the change ships a supposedly-fixed concurrency guard whose core
correctness claim remains completely unverified by any test that actually exercises a real
transaction commit, and because the specific pattern used (catch a flush-time constraint violation
inline, then let the same `@Transactional` method's implicit commit proceed) is a well-documented
Hibernate/Spring anti-pattern independent of this specific defect's history.

Tenant/user dual-scoping is still correct everywhere (`criar()`'s mute guard,
`NotificacaoController`'s self-service `getTenantId()`/`getUserId()` derivation, the repository's
dual-scoped methods, and the new `NotificacaoPreferenciaRepositoryIT` assertions covering
cross-tenant/cross-user isolation). No SQL injection, hardcoded secrets, or authorization bypass was
found. `CategoriaNotificacao` stays perfectly in sync with the frontend's `NotificacaoCategoria`
union and `NOTIFICACAO_CATEGORIAS_NAO_SILENCIAVEIS`. The two previously-reported Info items
(asymmetric `reativar` validation, `:view`-scoped mutating endpoints) remain open and are re-listed
below for completeness, plus one new Info item (an entity/migration nullability mismatch on
`created_at`).

## Critical Issues

### CR-01: `silenciarCategoria()`'s `saveAndFlush` + inline catch does not actually neutralize the concurrency race — it only moves the risk, and the claim that it does was never verified against a real database

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:303-333`
**Issue:**
```java
@Transactional
public void silenciarCategoria(UUID tenantId, UUID userId, String categoria) {
    ...
    try {
        if (!notificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria)) {
            notificacaoPreferenciaRepository.saveAndFlush(NotificacaoPreferencia.builder()
                    .tenantId(tenantId)
                    .userId(userId)
                    .categoria(categoria)
                    .build());
        }
    } catch (DataIntegrityViolationException ex) {
        log.debug("silenciarCategoria: insert concorrente para {}/{}/{}, a tratar como sucesso", ...);
    }
}
```
The iteration-2 fix correctly diagnosed that `save()` defers the physical `INSERT` to commit time
(because `NotificacaoPreferencia.id` uses `GenerationType.UUID`), and switched to `saveAndFlush()` so
the `INSERT` — and therefore the unique-constraint violation on a genuine concurrent race — happens
synchronously, inside the try block. That half of the diagnosis is correct.

What the fix does **not** account for: once that `INSERT` fails inside a PostgreSQL transaction,
PostgreSQL marks the *entire* transaction as aborted (`current transaction is aborted, commands
ignored until end of transaction block`). Catching the translated `DataIntegrityViolationException`
in Java only stops the exception from propagating out of this method — it does nothing to un-poison
the underlying database transaction. `silenciarCategoria()` is itself the `@Transactional` boundary
(there is no ambient transaction from the controller), so when the method returns normally, Spring's
`TransactionInterceptor` immediately attempts to commit that same poisoned transaction. Depending on
exactly how the pgjdbc driver and Spring's exception translation react to a `COMMIT` issued against
an already-aborted PostgreSQL transaction, this either:
1. throws (a `TransactionSystemException` or similar) from the interceptor's commit call —
   propagating uncaught to `NotificacaoController.silenciar()` (which only catches
   `IllegalArgumentException`) and falling through to `GlobalExceptionHandler.handleAllExceptions`,
   i.e. exactly the original WR-01 symptom (HTTP 500 leaking the exception class/message), just
   relocated from "flush time" to "commit time"; or
2. succeeds silently, because PostgreSQL treats `COMMIT` on an aborted transaction as an implicit
   `ROLLBACK` without raising a server-side error — in which case the losing request's own database
   work (nothing else here, but a latent risk if this method ever grows a second write) is discarded
   without the caller ever being told, while the HTTP response still reports `200 {"silenciada":
   true}`.

This is not a novel theory — it is the exact, widely-documented Hibernate/Spring pitfall (e.g. Vlad
Mihalcea's "How to catch and handle a `ConstraintViolationException`") that recovering from a
constraint violation requires either isolating the risky write in its own
`@Transactional(propagation = REQUIRES_NEW)` transaction (so only that inner transaction is poisoned
and rolled back, leaving the caller's transaction healthy to commit) or avoiding the exception
entirely with an atomic upsert. Catching the exception inline, in the same transactional method that
must still commit afterward, does not achieve either.

Crucially, **this was never actually tested**: 93-REVIEW-FIX.md states the new
`NotificacaoPreferenciaRepositoryIT.silenciarCategoria_duasTransacoesConcorrentes_...` test "was not
run in this sandbox because the local Docker Desktop daemon was not reachable" and recommends running
it before merging. I attempted to do so for this re-review and hit the same wall (`docker ps` /
Testcontainers cannot reach the daemon here either — `Docker Desktop.exe`'s backend process exits
immediately after launch in this environment). So the central claim of the fix — "no exception
escapes the try/catch under real concurrent load" — is shipped entirely unverified, on both the
producing and reviewing sides, for a fix whose entire purpose was to close a previously-shipped,
review-caught concurrency defect.
**Fix:** Replace the check-then-insert with a single atomic upsert, which sidesteps the whole
transaction-poisoning question because no exception is ever thrown on the expected "already exists"
path:
```java
// NotificacaoPreferenciaRepository
@Modifying
@Query(value = """
        INSERT INTO t_notificacao_preferencia (id, tenant_id, user_id, categoria, created_at)
        VALUES (gen_random_uuid(), :tenantId, :userId, :categoria, now())
        ON CONFLICT (tenant_id, user_id, categoria) DO NOTHING
        """, nativeQuery = true)
void upsertSilenciar(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId,
                      @Param("categoria") String categoria);

// NotificacaoService.silenciarCategoria
@Transactional
public void silenciarCategoria(UUID tenantId, UUID userId, String categoria) {
    CategoriaNotificacao resolvida = CategoriaNotificacao.fromString(categoria)
            .orElseThrow(() -> new IllegalArgumentException("categoria desconhecida: " + categoria));
    if (!resolvida.isSilenciavel()) {
        throw new IllegalArgumentException("categoria não silenciável: " + categoria);
    }
    notificacaoPreferenciaRepository.upsertSilenciar(tenantId, userId, categoria);
}
```
If a native upsert is undesirable, the alternative is to isolate the write in its own transaction —
but this requires the isolated method to live on a *different* Spring bean (or be invoked through a
self-injected proxy), because a plain `this.`-style call to a `@Transactional(REQUIRES_NEW)` method
from within the same class bypasses the proxy and silently keeps running under the outer
`REQUIRED` propagation, which would reintroduce this exact bug:
```java
// In a separate bean/collaborator, NOT the same class as silenciarCategoria's direct self-call
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void inserirPreferenciaIsolada(UUID tenantId, UUID userId, String categoria) {
    try {
        notificacaoPreferenciaRepository.saveAndFlush(NotificacaoPreferencia.builder()
                .tenantId(tenantId).userId(userId).categoria(categoria).build());
    } catch (DataIntegrityViolationException ex) {
        log.debug("concurrent insert, treating as success");
    }
}
```
Either way, before merging: get Docker/Testcontainers working in a CI or local environment and
actually run `NotificacaoPreferenciaRepositoryIT` (all four tests, especially the concurrency one)
against real PostgreSQL. A compile-only check and manual trace of expected Postgres locking behavior
(as 93-REVIEW-FIX.md did) is not sufficient evidence that this fix works, given the fix is
specifically about a runtime timing/transaction-semantics defect that unit/mock tests and pure
reasoning have already been shown (twice) not to catch reliably in this codebase.

## Info

### IN-01: `reativar` endpoint still does not validate `categoria`, unlike `silenciar` (carried over, unresolved)

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:138-143`
**Issue:** Unchanged since the prior two reviews. `reativar()` calls `reativarCategoria()`, which goes
straight to a derived delete with no validation via `CategoriaNotificacao.fromString(...)`, unlike
`silenciar()`. `DELETE /notificacoes/preferencias/TYPO_CATEGORY` still returns `200` with
`"silenciada": false` for any nonexistent string. Still harmless (no row can exist to delete for a
bogus category), but the asymmetric validation contract between two sibling endpoints remains and
silently swallows client-side typos.
**Fix:** Validate via `CategoriaNotificacao.fromString(categoria)` in `reativarCategoria()` (or the
controller) and return 400 for unknown values, mirroring `silenciar`'s contract.

### IN-02: Mutating preference endpoints still gated by `notificacoes:view`, not an edit/manage scope (carried over, unresolved)

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:115-143`
**Issue:** Unchanged since the prior two reviews. `listarPreferencias` (GET), `silenciar` (PUT) and
`reativar` (DELETE) are all still gated on `hasAuthority('notificacoes:view')`, not an edit/manage
scope, per the `scope:action` convention documented in `CLAUDE.md`. This mirrors the pre-existing
`marcarLida`/`marcarTodasLidas` precedent and every seeded role currently has `notificacoes:view`, so
there is no active privilege-escalation path today — flagged for awareness only, in case a future
read-only/reporting role is introduced with `notificacoes:view` but not intended to mutate its own
preferences.
**Fix:** Consider a dedicated `notificacoes:edit` scope for the mutating endpoints in a follow-up, or
explicitly document that notification self-service actions are intentionally `:view`-scoped because
they only ever touch the caller's own data.

### IN-03: `NotificacaoPreferencia.createdAt` is not marked `nullable = false`, unlike the manual production migration's `NOT NULL` column

**File:** `backend/src/main/java/com/lexcv/models/NotificacaoPreferencia.java:40-41`
**Issue:** `tenantId` and `userId` both declare `@Column(nullable = false)`, matching
`backend/migrations/93-create-notificacao-preferencia-table.sql`'s `NOT NULL` columns exactly. `createdAt`
does not:
```java
@Column(name = "created_at", updatable = false)
private LocalDateTime createdAt;
```
while the manual migration declares `created_at TIMESTAMP NOT NULL`. In practice `@PrePersist`
always sets this field, so no row is ever inserted with a null value at runtime. But in a fresh dev
environment (`ddl-auto=update`, no manual migration run), Hibernate would auto-create this column as
nullable, diverging from the hand-written prod migration script for the same table — a latent
schema-drift source between dev and prod for this specific table.
**Fix:** Add `nullable = false` for consistency and to keep Hibernate's own DDL generation (dev) in
lockstep with the manually-maintained production migration:
```java
@Column(name = "created_at", nullable = false, updatable = false)
private LocalDateTime createdAt;
```

---

_Reviewed: 2026-07-14T16:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
