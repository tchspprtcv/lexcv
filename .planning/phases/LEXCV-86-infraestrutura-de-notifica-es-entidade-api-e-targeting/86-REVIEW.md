---
phase: LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting
reviewed: 2026-07-08T23:15:00Z
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
  warning: 4
  info: 9
  total: 13
status: issues_found
---

# Phase 86: Code Review Report

**Reviewed:** 2026-07-08T23:15:00Z
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Narrative Findings (AI reviewer)

### Summary

This is a fresh, independent re-review of Phase 86 (`Notificacao` entity, `NotificacaoRepository`, the `NotificacaoService` write chokepoint + ADMIN fan-out, `NotificacaoController`, migration, and `notificacoes:view` RBAC plumbing across `DatabaseSeeder`/`AdminController`/`UserPrincipal`/`permissions.ts`). Two prior review cycles are on record: cycle 1 found 5 Warnings (all fixed, commits `1b095af`, `2f48092`, `35afb15`, `46b6c9d`, `aa8dd78`); cycle 2 found 2 new Warnings, of which one (missing test coverage for `criar()`'s validation branches) was fixed (commit `66ad927`) and one (the native-query/`Pageable`/`countQuery` combination never executed against real PostgreSQL) was explicitly skipped as outside an automated fixer's scope.

**Independently re-verified, not taken on faith:**
- Re-traced every query and mutation path (`buscarPorFiltros`, `countByTenantIdAndDestinatarioIdAndLidaFalse`, `findByTenantIdAndDestinatarioIdAndLidaFalse`, `findByIdAndTenantIdAndDestinatarioId`, `criar`, `marcarLida`, `marcarTodasLidas`, `notificarAdmins`) end-to-end for a tenant/destinatario scoping bypass. Found none — every controller endpoint sources `tenantId`/`destinatarioId` exclusively from `SecurityContextHolder`, never from client-suppliable input, and every repository finder carries both as non-optional predicates. **0 Critical confirmed independently.**
- Re-read `NotificacaoServiceTest.java` line-by-line against the cycle-2 fix commit (`66ad927`, +47 lines): the three new tests (`criar_destinatarioDeOutroTenant_...`, `criar_tituloExcede255Caracteres_...`, `criar_camposObrigatoriosEmBranco_...`) are present, correctly wired, and each of the five `requireNonBlank` call sites is now individually exercised. This fix is complete and correct as claimed.
- Confirmed via `backend/pom.xml` (grep for `h2`/`testcontainers`, case-insensitive) that no embedded-database test dependency has been added since cycle 2 — the native-query verification gap (this report's WR-01) is still genuinely unresolved, not just unrevisited.
- Grepped the full `backend/src` tree again for `notificacaoRepository.save(`/`.saveAll(` (production call sites only in `NotificacaoService.java:60,103,113`) and for any caller of `NotificacaoService.criar`/`notificarAdmins` outside the service/test (none) — the "sole write chokepoint" invariant still holds, and this remains genuinely infrastructure-only (no Phase 87 trigger code has landed yet).
- Grepped `web/src` for `notificacoes`/`Notificacao` — still only `permissions.ts` itself references it.

**New, this pass:** the requirement-length counterpart to cycle 2's now-fixed blank-field test gap was never closed — `requireMaxLength` is invoked once per field (5 call sites) from the exact same kind of shared private helper as `requireNonBlank`, using the exact same "a silently-deleted call site won't be caught" reasoning the prior fix explicitly used to justify testing all five `requireNonBlank` sites individually — yet only 1 of 5 `requireMaxLength` sites (`titulo`) has any test coverage (WR-03). Also newly found: `notificarAdmins` still has no `@Transactional` (re-raised at Warning, see WR-02) and the `size` query parameter on `GET /notificacoes` is still unbounded (raised as Info twice already and left unfixed both times; re-assessed here at Warning severity per this review's own judgment, not merely carried forward — see WR-04). Four small Info-level observations are new this pass (IN-06 through IN-09); IN-01 through IN-05 are carried forward from prior cycles, re-verified against the current files rather than copy-pasted, and remain valid.

## Warnings

### WR-01: First native query + `Pageable` + `countQuery` combination in the codebase is still unverified against a real PostgreSQL instance

**File:** `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:16-39`
**Issue:** This is the third consecutive review cycle raising this gap. `buscarPorFiltros` is the query backing `GET /api/v1/notificacoes` — the first endpoint in Success Criterion 1 — and combines `nativeQuery = true`, a hand-written `countQuery`, and a `Pageable` argument. No working precedent for this exact combination exists elsewhere in the codebase (`ParecerSolicitacaoRepository.pesquisar` shares the `CAST(:param AS type) IS NULL OR ...` null-guard idiom but returns a plain unpaginated `List`, with no `countQuery`). There is still no `@DataJpaTest`/H2/Testcontainers dependency in `backend/pom.xml` (re-confirmed this pass), so this query has never executed against Postgres in an automated test — only through Mockito mocks that don't validate real SQL. Static analysis found no concrete defect in the SQL itself (the CAST-null-guard pattern is sound, and Hibernate can pick up sort/limit/offset for a simple `SELECT ... WHERE ... ORDER BY` native query once an explicit `countQuery` is supplied, exactly as done here) — this is a verification gap on the phase's riskiest, most central read path, not a proven functional bug, but it has now shipped unverified through two prior review cycles.
**Fix:** Before this ships to an environment where it matters, do at least one manual round trip against a dev database with seeded rows covering all four filter combinations (no filters; `categoria` only; `lida` only; both) via curl/Postman against `GET /api/v1/notificacoes`, and record the result in the phase's verification artifacts. Longer-term, this is a reasonable first candidate for adopting `@DataJpaTest` if the project ever takes on an embedded-database test dependency — but that is an explicit human/architecture decision, not something to bolt on unilaterally.

### WR-02: `notificarAdmins` fan-out is still not `@Transactional` — a mid-loop validation failure now leaves a partial ADMIN notification set

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:81-86`
**Issue:** `marcarLida`/`marcarTodasLidas` are both explicitly `@Transactional`, but `notificarAdmins` — which loops over every current ADMIN and calls `criar(...)` once per admin — is not. Since the cycle-1 `criar()` fix added real validation (tenant-ownership check, blank checks, length checks), `criar(...)` can now throw `IllegalArgumentException` partway through the loop. If it throws on the Nth admin, rows already saved for admins `1..N-1` are **not** rolled back: the fan-out that Success Criterion 3 requires to produce "uma linha própria por cada ADMIN" can complete partially rather than atomically. Today's only caller is the test, using fixed literal values the validation will never reject, so there is no live exploit path yet — but Phase 87 is explicitly documented (class-level comment, lines 76-80) as the next real caller, and it will pass caller-supplied `categoria`/`titulo`/`mensagem` values that are materially more likely to trip validation than today's test fixtures.
**Fix:**
```java
@Transactional
void notificarAdmins(UUID tenantId, String categoria, String titulo, String mensagem,
                      String entidadeTipo, String entidadeId, String linkUrl) {
    for (User admin : userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")) {
        criar(tenantId, admin.getId(), categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
    }
}
```

### WR-03: `requireMaxLength`'s 5 call sites have test coverage for only 1 field — the exact blind spot the prior fix explicitly closed for `requireNonBlank`, but left open here

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:44-48, 69-74` (test gap in `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java:80-90`)
**Issue:** `criar()` calls `requireMaxLength(...)` once each for `categoria`, `titulo`, `entidadeTipo`, `entidadeId`, and `linkUrl` — five independent call sites funneling through one shared private helper, structurally identical to the five `requireNonBlank` call sites. The cycle-2 fix (commit `66ad927`) explicitly reasoned that `requireNonBlank` needed all five fields tested individually, "since `requireNonBlank` is invoked once per field from a shared private helper — a regression that silently deletes just one of the five call sites would not be caught by a test that only exercises a different field" (per `86-REVIEW-FIX.iter2.md`). That exact reasoning applies unchanged to `requireMaxLength` — yet only `criar_tituloExcede255Caracteres_lancaIllegalArgumentException` exists; `categoria`, `entidadeTipo`, `entidadeId`, and `linkUrl` have zero length-limit test coverage. A future edit that silently drops the `requireMaxLength("linkUrl", ...)` call (or any of the other three), letting an oversized value reach the `VARCHAR(255)` column and fail as an uncaught, unmapped exception at the JDBC layer instead of a clean `IllegalArgumentException`, would not be caught by this suite.
**Fix:**
```java
@Test
void criar_camposComTamanhoExcedido_lancaIllegalArgumentException() {
    when(userRepository.findById(DESTINATARIO_A))
            .thenReturn(Optional.of(User.builder().id(DESTINATARIO_A).tenantId(TENANT_ID).build()));
    NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);

    assertThrows(IllegalArgumentException.class, () ->
            service.criar(TENANT_ID, DESTINATARIO_A, "x".repeat(256), "t", "m", "processo", "id-1", "/link"));
    assertThrows(IllegalArgumentException.class, () ->
            service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "x".repeat(256), "id-1", "/link"));
    assertThrows(IllegalArgumentException.class, () ->
            service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "x".repeat(256), "/link"));
    assertThrows(IllegalArgumentException.class, () ->
            service.criar(TENANT_ID, DESTINATARIO_A, "FASE_ENTRADA", "t", "m", "processo", "id-1", "x".repeat(256)));
    verify(notificacaoRepository, never()).save(any());
}
```

### WR-04: `GET /api/v1/notificacoes` still accepts an unbounded `size` — flagged as Info twice and left unfixed both times; re-assessed here at Warning

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:69-73`
**Issue:** The cycle-1 fix correctly rejects `page < 0 || size < 1` with a 400, but places no ceiling on `size`. `GET /api/v1/notificacoes?size=1000000` (or `size=2000000000`) is accepted as-is and passed straight into `PageRequest.of(page, size)` — no exception, just an attempt to materialize and JSON-serialize an unbounded result set scoped to the caller's own data. This was raised as an Info-level observation in the prior review cycle and left unaddressed (explicitly out of `fix_scope: critical_warning`) — it is re-classified here as a Warning on independent review: it is a live, un-remediated "missing input validation" gap (a named Security review category) on a newly introduced, publicly-reachable endpoint (every one of the four seeded roles holds `notificacoes:view`), and the fix is a one-line addition with zero risk of regressing existing behavior. It is not tenant/destinatario-boundary-breaking (results are still scoped to the caller's own rows), which is why it remains a Warning rather than Critical.
**Fix:**
```java
if (page < 0 || size < 1 || size > 100) {
    return ResponseEntity.badRequest().body(Map.of("message", "page deve ser >= 0 e size deve estar entre 1 e 100"));
}
```

## Info

### IN-01: `createdAt` nullability still inconsistent between entity and migration

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:51-52` vs `backend/migrations/86-create-notificacao-table.sql:30`
**Issue:** Carried forward from prior cycles, re-verified — still true. The migration declares `created_at TIMESTAMP NOT NULL`, but `@Column(name = "created_at", updatable = false)` still omits `nullable = false`, unlike every other required column on this entity. Harmless today (`@PrePersist onCreate()` always populates it), but an inconsistent self-declaration.
**Fix:**
```java
@Column(name = "created_at", updatable = false, nullable = false)
private LocalDateTime createdAt;
```

### IN-02: ADMIN fan-out still doesn't filter by `ativo`

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:81-86` (via `UserRepository.findByTenantIdAndRoleName`)
**Issue:** Carried forward, re-verified against the current query (`SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName`, `UserRepository.java:16-17`) — no `ativo` predicate, unlike `JwtAuthenticationFilter`'s login gate (`user.getAtivo()`). A deactivated admin still accumulates notification rows via `notificarAdmins`. May be intentional; worth an explicit decision either way.
**Fix:** If unintentional, add an `ativo` predicate to the query or filter in the service; if intentional, a one-line comment saves the next reader from re-litigating it.

### IN-03: Frontend `KNOWN_SCOPES` registry still has no consumers

**File:** `web/src/lib/permissions.ts:6-15`
**Issue:** Carried forward, re-verified — a fresh full-tree grep of `web/src` for `notificacoes` still returns only `permissions.ts` itself. Consistent with this being infrastructure-only (the bell/list UI is Phase 89) — flagged only so it isn't mistaken for a wired-up feature.

### IN-04: `@Builder.Default` on `lida` still only applies via the builder

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:46-49`
**Issue:** Carried forward, still true. Every current construction path (`NotificacaoService.criar`, the test file) goes through `.builder()...build()`, so this doesn't manifest today. But `@Builder.Default`'s initializer only fires inside the generated builder's `build()` — the co-existing `@NoArgsConstructor`/`@AllArgsConstructor` do not apply it, so a future `new Notificacao(...)` path (e.g. a Jackson deserializer, should this entity ever become a `@RequestBody`) would leave `lida` as `null` rather than `false`, tripping the `NOT NULL` constraint at insert time instead of being silently defaulted.

### IN-05: `getTenantId()`/`getUserId()` boilerplate still duplicated across four controllers

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:52-62`
**Issue:** Carried forward, still true — the same `SecurityContextHolder` → cast → getter pattern is hand-copied in `ResourceController.java`, `ParecerController.java`, `ParecerPesquisaController.java`, and here. A shared `CurrentUserContext` component would pay for itself, but this predates this phase.

### IN-06: `notificarAdmins` has no null-check on its own `tenantId` — silently no-ops instead of failing loudly

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:81-86`
**Issue:** `criar()` fails fast on a null `tenantId`/`destinatarioId` (`IllegalArgumentException`). `notificarAdmins(tenantId, ...)` has no equivalent guard: if called with a null `tenantId`, `userRepository.findByTenantIdAndRoleName(null, "ADMIN")` translates to a JPQL `u.tenantId = :tenantId` comparison against `NULL`, which matches zero rows — the `for` loop simply never executes, `criar()` is never called, and the method returns having silently done nothing. For a method whose sibling (`criar`) was specifically hardened in cycle 1 to fail loudly on exactly this kind of bad input, a caller bug that produces a null `tenantId` here (plausible once Phase 87 derives it from a business entity, e.g. a `Processo`) would manifest as "no notifications sent" with no exception and no log line, rather than a clear error at the source.
**Fix:** Mirror `criar()`'s guard:
```java
void notificarAdmins(UUID tenantId, String categoria, String titulo, String mensagem,
                      String entidadeTipo, String entidadeId, String linkUrl) {
    if (tenantId == null) {
        throw new IllegalArgumentException("tenantId é obrigatório");
    }
    for (User admin : userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")) {
        criar(tenantId, admin.getId(), categoria, titulo, mensagem, entidadeTipo, entidadeId, linkUrl);
    }
}
```

### IN-07: `criar()`'s validation chokepoint does not constrain `linkUrl`'s scheme/format

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:28-61`
**Issue:** The class comment designates `criar()` as the single enforcement point for every invariant a `Notificacao` row must satisfy, and cycle 1 added real validation (tenant ownership, blank checks, length checks) precisely so future callers (Phase 87's `notificarFaseEntrada`/`notificarDocumentoNovo`/etc.) can't accidentally violate them. `linkUrl` is validated only for length — nothing constrains its scheme or shape, so a caller could pass `javascript:alert(1)` or a `data:` URI and it would be persisted and later returned verbatim via `GET /notificacoes` and `PATCH /{id}/lida`. There is no reachable path today that feeds untrusted input into `linkUrl` (no HTTP endpoint exposes `criar()`'s parameters to a client), so this is not exploitable yet — but Phase 89's bell/list UI is the documented consumer of this exact field, and hardening the one designated chokepoint now (rather than relying on every future caller and the eventual frontend to individually get this right) is consistent with why this class exists.
**Fix:** Constrain to the two shapes this domain actually uses (root-relative app paths):
```java
private static void requireSafeLinkUrl(String linkUrl) {
    if (linkUrl != null && !linkUrl.startsWith("/")) {
        throw new IllegalArgumentException("linkUrl deve ser um caminho relativo iniciado por '/'");
    }
}
```

### IN-08: `Notificacao` JPA entity is serialized directly as the API response, bypassing the DTO pattern used elsewhere

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:75-84, 100`
**Issue:** `listar` returns `pageResult.getContent()` (a `List<Notificacao>`) and `marcarLida` returns the `Notificacao` entity directly inside `ResponseEntity.ok(...)`, rather than mapping to a response DTO. `AdminController` (in the same reviewed set) uses `UserResponse`/`RbacResponse` for exactly this reason. Harmless today — `Notificacao` has no `@JsonIgnore`-worthy or lazily-loaded relationship fields, so nothing sensitive leaks — but it couples the wire format to the persistence model: any future column added to this entity is automatically exposed via the API with no deliberate opt-in.
**Fix:** Introduce a small `NotificacaoResponse` DTO (mirroring `UserResponse`'s pattern) before Phase 89 builds a frontend contract against the current entity shape.

### IN-09: `notificacoes:view` gates both read and mark-read/mark-all-read endpoints — a documented deviation from the project's `scope:action` convention

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:64,86,93,103`; self-documented at `backend/src/main/java/com/lexcv/controllers/AdminController.java:232`
**Issue:** CLAUDE.md documents the project-wide convention as `scope:action` with `action ∈ {view, create, edit, manage}`, where mutations normally require `edit`/`manage`. Here, the two state-mutating endpoints (`PATCH /{id}/lida`, `POST /ler-todas`) are gated by the same `notificacoes:view` authority as the two read endpoints — there is no `notificacoes:edit`. This is a deliberate, reasoned, and self-documented decision (86-CONTEXT.md, and the RBAC screen's own permission description: "Ver e marcar como lidas as notificações próprias"), not an oversight — marking your own notification read is a personal read-state toggle, not an edit of shared tenant data. Flagged only so a future maintainer applying the `scope:action` convention by pattern-matching doesn't "fix" this into an inconsistency with the documented design.

---

_Reviewed: 2026-07-08T23:15:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
