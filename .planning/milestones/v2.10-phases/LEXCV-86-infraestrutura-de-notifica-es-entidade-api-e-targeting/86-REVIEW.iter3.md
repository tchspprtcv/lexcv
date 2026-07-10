---
phase: LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting
reviewed: 2026-07-08T22:30:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/Notificacao.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
  - backend/migrations/86-create-notificacao-table.sql
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java
  - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/main/java/com/lexcv/config/UserPrincipal.java
  - web/src/lib/permissions.ts
findings:
  critical: 0
  warning: 2
  info: 7
  total: 9
status: issues_found
---

# Phase 86: Code Review Report

**Reviewed:** 2026-07-08T22:30:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Narrative Findings (AI reviewer)

### Summary

This is iteration 2 of the review for Phase 86 (`Notificacao` entity, `NotificacaoRepository`, `NotificacaoService` write chokepoint + ADMIN fan-out, `NotificacaoController`, migration, RBAC plumbing). Iteration 1 (`86-REVIEW.md`, now superseded by this file) found 0 Critical and 5 Warning findings; `86-REVIEW-FIX.md` reports all 5 fixed across commits `1b095af`, `2f48092`, `35afb15`, `46b6c9d`, `aa8dd78`.

**Fix verification (not just re-reading the diff — independently re-derived):**
- Ran `mvn -o test -Dtest=NotificacaoServiceTest` myself: **5/5 passing**. Ran the full suite (`mvn -o test`): **20/20 passing** (5 `NotificacaoServiceTest` + 15 `RiscoPrazoServiceTest`), 0 failures/errors — matches `86-REVIEW-FIX.md`'s claim, confirmed independently rather than taken on faith. `mvn -o test-compile` is clean.
- Grepped the full `backend/src` tree for `notificacaoRepository.save(` / `.saveAll(`: the only production call sites are `NotificacaoService.java:60,103,113` (test-file mocks aside) — the "sole write chokepoint" invariant still holds after the fixes.
- Grepped for any `.set(TenantId|DestinatarioId|Categoria|Titulo|Mensagem|EntidadeTipo|EntidadeId|LinkUrl|CreatedAt|Id)(` call anywhere in `backend/src`: zero hits against a `Notificacao` instance — confirms WR-03's removal of the class-level `@Setter` is complete and no caller depended on a setter that no longer exists.
- Confirmed WR-04's fix line-by-line against `DatabaseSeeder.seedRbac()`'s `permKeys` list: the two 19-entry lists are now identical, including `"notificacoes:view"`.
- Confirmed WR-05's fix: `NotificacaoController.listar` now rejects `page < 0 || size < 1` with 400 before `PageRequest.of(...)` can throw.
- Confirmed WR-01's fix: `criar()` now null-checks `tenantId`/`destinatarioId`, verifies `destinatarioId` belongs to `tenantId` via `userRepository.findById(...).filter(...).orElseThrow(...)`, and blank/length-validates every `VARCHAR(255)` field. All 5 prior warnings are correctly and completely resolved.

**This pass's own adversarial findings (new, not raised in iteration 1):** the WR-01 fix introduced real validation logic but shipped with **no test that exercises any of its failure paths** (WR-01 below), and the phase's single riskiest, most novel piece of SQL — the first-ever native query + `Pageable` + `countQuery` combination in this codebase — has **never been run against a real PostgreSQL instance**, by the executing agent's own account (WR-02 below). Neither is a proven functional defect (I found no concrete flaw in the SQL or the validation logic itself), but both are verification gaps on code whose entire purpose is to guard security-relevant or previously-broken behavior. Two new Info-level observations were also found (IN-06, IN-07); the five Info items from iteration 1 (IN-01 through IN-05) were explicitly left unfixed by design (`fix_scope: critical_warning`) and remain valid/unaddressed in the current code — re-verified against the current files rather than copy-pasted.

## Warnings

### WR-01: The WR-01 fix's own validation logic has zero test coverage for any of its failure paths

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:28-48` (also `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`)
**Issue:** Iteration 1's WR-01 fix added, to the sole `Notificacao` write chokepoint: a null-check on `tenantId`/`destinatarioId`, a tenant-ownership check (`userRepository.findById(destinatarioId).filter(u -> tenantId.equals(u.getTenantId())).orElseThrow(...)`), five `requireNonBlank` calls, and five `requireMaxLength` calls. This is exactly the security-relevant guard rail the class comment says `criar()` must be, since Phase 87 will add several new public callers that funnel through it. However, `NotificacaoServiceTest` (the only test file for this class) still contains just the original 5 tests, and every one of them stubs `userRepository.findById(...)` to return a *matching-tenant* user — none of the new failure branches is ever exercised:
- No test asserts `criar()` throws `IllegalArgumentException` when `destinatarioId` belongs to a *different* tenant (the exact IDOR-adjacent scenario this check exists to catch).
- No test asserts it throws for a null/blank `categoria`/`titulo`/`mensagem`/`entidadeTipo`/`entidadeId`.
- No test asserts it throws for a field exceeding 255 characters.

`assertThrows` does not appear anywhere in the test file. A future edit that silently weakens or deletes any of these checks (e.g., someone "simplifies" `requireMaxLength` away as apparently-dead code, or flips the `.filter(...)` predicate) would not be caught by this suite — the regression the validation itself was written to prevent.
**Fix:**
```java
@Test
void criar_destinatarioDeOutroTenant_lancaIllegalArgumentException() {
    when(userRepository.findById(DESTINATARIO_A))
            .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(UUID.randomUUID()).build()));
    NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);

    assertThrows(IllegalArgumentException.class, () ->
            service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "/link"));
    verify(notificacaoRepository, never()).save(any());
}

@Test
void criar_tituloExcede255Caracteres_lancaIllegalArgumentException() {
    when(userRepository.findById(DESTINATARIO_A))
            .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
    NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);

    assertThrows(IllegalArgumentException.class, () ->
            service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "x".repeat(256), "m", "processo", "id-1", "/link"));
}
```

### WR-02: First native query + `Pageable` + `countQuery` combination in the codebase has never been run against a real database

**File:** `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:16-39`
**Issue:** The repository's own comment (line 16) states this is the "First use of Spring Data Pageable/Page in this backend." The CAST-null-guard idiom is a genuine reuse of `ParecerSolicitacaoRepository.pesquisar` (verified by reading that file), but `pesquisar` returns a plain `List`, has no `countQuery`, and is not paginated — so the specific combination used here (`nativeQuery = true` + a separate hand-written `countQuery` + a `Pageable` argument) has no working precedent anywhere in this codebase to have copied. There is no `@DataJpaTest`/H2/Testcontainers in this project (confirmed: `NotificacaoServiceTest`'s own docstring states this, and no other integration-test infrastructure exists), so `buscarPorFiltros` has never executed against Postgres in an automated test — it is only exercised through mocks that don't validate real SQL. `86-03-SUMMARY.md` additionally states outright: *"Live HTTP round-trip / RBAC Settings-screen UI verification not performed in this session (static/compile-level verification only)."* This means the query backing `GET /notificacoes` — the first endpoint named in Success Criterion 1 — has had **no automated or manual execution against a real database** at any point in this phase. I did not find a concrete defect in the SQL itself (the query is simpler than its precedent — no JOIN — which reduces risk), but the combination is novel enough, and central enough to the phase's primary read path, that shipping it with zero real-database verification is itself the gap.
**Fix:** Before this ships, do at least one manual round trip against a dev database with seeded rows covering all four filter combinations (no filters; `categoria` only; `lida` only; both), e.g. via curl/Postman, and record the result in the phase's verification artifacts. If a longer-term fix is preferred, this is also a reasonable first candidate for introducing `@DataJpaTest` if the project ever adopts an embedded-database test dependency.

## Info

### IN-01: `createdAt` nullability still inconsistent between entity and migration

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:51` vs `backend/migrations/86-create-notificacao-table.sql:30`
**Issue:** Carried forward from iteration 1, left unfixed by design (`fix_scope: critical_warning` excluded Info items) — still true in the current code. The migration declares `created_at TIMESTAMP NOT NULL`, but `@Column(name = "created_at", updatable = false)` still omits `nullable = false`, unlike every other required column on this entity. Harmless today (`@PrePersist onCreate()` always populates it), but inconsistent self-documentation.
**Fix:**
```java
@Column(name = "created_at", updatable = false, nullable = false)
private LocalDateTime createdAt;
```

### IN-02: ADMIN fan-out still doesn't filter by `ativo`

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:81-86` (via `UserRepository.findByTenantIdAndRoleName`, `backend/src/main/java/com/lexcv/repositories/UserRepository.java:16-17`)
**Issue:** Carried forward from iteration 1, still true — re-verified against the current `UserRepository.findByTenantIdAndRoleName` query (`SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName`), which has no `ativo` predicate, unlike `JwtAuthenticationFilter`'s login gate. A deactivated admin still accumulates notification rows via `notificarAdmins`. May be intentional; worth an explicit decision.
**Fix:** If unintentional, add an `ativo` predicate to the query or filter in the service; if intentional, a one-line comment saves the next reader from re-litigating it.

### IN-03: Frontend scope registry still has no consumers

**File:** `web/src/lib/permissions.ts:6-15`
**Issue:** Carried forward from iteration 1, re-verified: a full-tree grep of `web/src` for `notificacoes` still returns only `permissions.ts` itself. Consistent with this being infrastructure-only (the bell/list UI is Phase 89) — flagged only so it isn't mistaken for a wired-up feature.

### IN-04: `@Builder.Default` on `lida` still only applies via the builder

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:46-49`
**Issue:** Carried forward from iteration 1, still true. Every current construction path goes through `.builder()...build()`, so this doesn't manifest today, but a future `new Notificacao()` + setters path would leave `lida` `null` rather than `false`.

### IN-05: `getTenantId()`/`getUserId()` boilerplate still duplicated across four controllers

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:52-62`
**Issue:** Carried forward from iteration 1, still true — the same `SecurityContextHolder` → cast → getter pattern is hand-copied in `ResourceController.java`, `ParecerController.java`, `ParecerPesquisaController.java`, and here. A shared `CurrentUserContext` component would pay for itself, but this is pre-existing and not introduced by this phase.

### IN-06: No upper bound on the `size` query parameter

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:69-74`
**Issue:** The iteration-1 WR-05 fix correctly rejects `page < 0 || size < 1`, but places no ceiling on `size`. `GET /api/v1/notificacoes?size=1000000` is accepted as-is and passed straight into `PageRequest.of(page, size)`. The result set is still scoped to the caller's own notifications (no cross-tenant/cross-user leak), so this isn't a new instance of the dual-scoping risk this phase is about, but it does let a single authenticated caller force one request to materialize an arbitrarily large page in-memory with no server-side cap.
**Fix:**
```java
if (page < 0 || size < 1 || size > 100) {
    return ResponseEntity.badRequest().body(Map.of("message", "page deve ser >= 0 e size deve estar entre 1 e 100"));
}
```

### IN-07: `notificarAdmins` fan-out is not transactional — a mid-loop validation failure leaves a partial notification set

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:81-86`
**Issue:** `marcarLida`/`marcarTodasLidas` are both explicitly `@Transactional` (the class comments call out that they are compound find-then-mutate-then-save operations), but `notificarAdmins` — which loops over every current ADMIN and calls `criar(...)` once per admin, each a separate implicit transaction — has no `@Transactional` of its own. Since the WR-01 fix, `criar(...)` can now throw `IllegalArgumentException` for a variety of reasons before its `save()`. If it throws on the Nth admin in the loop, the notifications already committed for admins `1..N-1` are **not** rolled back — the fan-out that Success Criterion 3 describes as producing "uma linha própria por cada ADMIN" can now complete partially rather than atomically. Low risk today (the only caller is the test, using fixed literal values that the validation added in WR-01 will never reject), but Phase 87 is explicitly documented as the next real caller of this exact method, and it will pass caller-supplied `categoria`/`titulo`/`mensagem` values that are far likelier to trip validation than today's test fixtures.
**Fix:** Add `@Transactional` to `notificarAdmins` so a failure partway through one fan-out call rolls back that call's own already-inserted rows, rather than leaving a partial set:
```java
@Transactional
void notificarAdmins(UUID tenantId, String categoria, String titulo, String mensagem,
                      String entidadeTipo, String entidadeId, String linkUrl) {
    for (User admin : userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")) {
        criar(tenantId, admin.getId(), categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
    }
}
```

---

_Reviewed: 2026-07-08T22:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
