---
phase: LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting
reviewed: 2026-07-08T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/Notificacao.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
  - backend/migrations/86-create-notificacao-table.sql
  - backend/src/main/java/com/lexcv/services/NotificacaoService.java
  - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java
  - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - web/src/lib/permissions.ts
findings:
  critical: 0
  warning: 5
  info: 5
  total: 10
status: issues_found
---

# Phase 86: Code Review Report

**Reviewed:** 2026-07-08T00:00:00Z
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Narrative Findings (AI reviewer)

### Summary

This phase introduces `Notificacao` — the codebase's first per-recipient-private resource — plus its repository, service (the designated sole write chokepoint, including ADMIN fan-out), controller, unit tests, migration, and the `notificacoes:view` RBAC plumbing threaded through `DatabaseSeeder`, `AdminController`, and the frontend `permissions.ts` registry.

**The specific risk this review was asked to prioritize was checked and not confirmed:** every repository finder (`buscarPorFiltros`, `countByTenantIdAndDestinatarioIdAndLidaFalse`, `findByTenantIdAndDestinatarioIdAndLidaFalse`, `findByIdAndTenantIdAndDestinatarioId`) carries both `tenant_id` and `destinatario_id` as non-optional predicates; every `NotificacaoController` endpoint sources both ids exclusively from `SecurityContextHolder` (never from client-suppliable input); and a full-codebase grep confirms `NotificacaoRepository.save`/`saveAll` is called only from `NotificacaoService` (plus the test) — no bypass writer exists. The `marcarLida` 404-not-403 design (returning empty rather than leaking existence for another destinatario's row) is implemented correctly and matches its documented intent.

That said, several real gaps remain, concentrated at the write chokepoint's trust boundary, in an out-of-sync duplicate permission list, in the new entity's mutability posture, in the new controller's input validation, and in test coverage that doesn't fully prove the property it documents. Two follow-on files outside the explicit review list (`UserPrincipal.java`, `UserRepository.java`) were read only to verify claims that trace directly from the reviewed files (e.g., whether the new `notificacoes:view` permission actually reaches an authenticated principal, and whether admin fan-out is tenant-scoped) — both are cited below because the defect is only visible by following that chain.

## Warnings

### WR-01: `NotificacaoService.criar()` — the sole write chokepoint — performs no validation of its own inputs

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:26-39`
**Issue:** The class comment (lines 22-25) explicitly designates `criar()` as the single place a `Notificacao` row can be born, precisely so that invariants like "never a cross-tenant write" can be enforced in one place. But the method itself does none of that enforcement — it blindly builds and saves whatever `(tenantId, destinatarioId, categoria, ...)` it's handed:
- No check that `destinatarioId` actually belongs to `tenantId` (nothing stops a future caller from passing a recipient from a different tenant — the exact IDOR-adjacent failure mode this phase is meant to guard against, just shifted from "read" to "write").
- No null-check on `destinatarioId`/`categoria`/`titulo`/`mensagem`/`entidadeTipo`/`entidadeId` — a null `destinatarioId` would violate the `NOT NULL` DB constraint and surface as an uncaught exception (mapped by `GlobalExceptionHandler`'s catch-all to a 500, not a controlled error).
- No length check against the `VARCHAR(255)` columns (`titulo`, `linkUrl`, `entidadeTipo`, `entidadeId`) — an oversized value fails the same way.

Per the code comment, Phase 87 will add several new public callers (`notificarFaseEntrada`, `notificarDocumentoNovo`, etc.) that funnel through this exact method. Today it is effectively a rubber stamp, not a guard rail.
**Fix:**
```java
public Notificacao criar(UUID tenantId, UUID destinatarioId, String categoria, String titulo,
                          String mensagem, String entidadeTipo, String entidadeId, String linkUrl) {
    if (tenantId == null || destinatarioId == null) {
        throw new IllegalArgumentException("tenantId e destinatarioId são obrigatórios");
    }
    userRepository.findById(destinatarioId)
            .filter(u -> tenantId.equals(u.getTenantId()))
            .orElseThrow(() -> new IllegalArgumentException(
                    "destinatarioId não pertence ao tenant informado"));

    Notificacao n = Notificacao.builder()
            .tenantId(tenantId)
            .destinatarioId(destinatarioId)
            .categoria(categoria)
            .titulo(titulo)
            .mensagem(mensagem)
            .entidadeTipo(entidadeTipo)
            .entidadeId(entidadeId)
            .linkUrl(linkUrl)
            .build();
    return notificacaoRepository.save(n);
}
```

### WR-02: Fan-out test doesn't assert the property it claims to prove

**File:** `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java:63-81`
**Issue:** `notificarComFanOutAdmin_umaLinhaPorAdminAtualDoTenant` (and its docstring at lines 26-29) claims to prove "Critério de Sucesso 3" — one independent row per current ADMIN. The assertions actually made are only: `save()` was called twice, and each saved row has `lida == false` (lines 75-78). Nothing asserts that the two captured rows' `destinatarioId` values equal `admin1.getId()` and `admin2.getId()` respectively. A regression that fanned out to the wrong user (e.g., a copy-paste bug passing the same admin twice, or an off-by-one against a different list) would still pass this test as long as `save()` is invoked twice with `lida=false`. For a test whose entire purpose is to lock down per-recipient targeting, this is the one assertion that matters and it's missing.
**Fix:**
```java
List<UUID> destinatarios = captor.getAllValues().stream()
        .map(Notificacao::getDestinatarioId)
        .toList();
assertEquals(List.of(admin1.getId(), admin2.getId()), destinatarios);
```

### WR-03: `Notificacao.tenantId` / `destinatarioId` are mutable via class-level `@Setter` with no guard

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:11, 21-25`
**Issue:** `@Setter` is applied at the class level (line 11), generating `setTenantId(...)` and `setDestinatarioId(...)` alongside every other field's setter. `createdAt` is protected from accidental mutation via `updatable = false` (line 51), but `tenantId`/`destinatarioId` have no equivalent guard. Today the only two mutators in the codebase (`NotificacaoService.marcarLida`/`marcarTodasLidas`) only ever call `setLida(true)`, so there is no live exploit path — but this is the codebase's first entity where accidentally calling the wrong setter re-parents a row to a different *user* (not just a different tenant), which is a materially different and easier-to-trigger-by-accident mistake than the tenant-remapping risk that already exists identically on every other entity in this codebase. There is currently no compiler- or runtime-level guard stopping a future change (including a Phase 87 caller) from doing `notificacao.setDestinatarioId(outroUserId); repo.save(notificacao);` on a fetched row.
**Fix:** Drop the class-level `@Setter` and apply it field-by-field instead, omitting it for `id`, `tenantId`, `destinatarioId`, and `createdAt`:
```java
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notificacao {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "destinatario_id", nullable = false)
    private UUID destinatarioId;

    // ... other fields ...

    @Setter
    @Column(nullable = false)
    @Builder.Default
    private Boolean lida = false;
    // ...
}
```

### WR-04: Hardcoded ADMIN permission fallback in `UserPrincipal` was not updated for `notificacoes:view`

**File:** `backend/src/main/java/com/lexcv/config/UserPrincipal.java:33-44` (traced from `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:303`, in the reviewed set)
**Issue:** `DatabaseSeeder.seedRbac()` adds `"notificacoes:view"` to `permKeys` and attaches it to every role including ADMIN (line 313, `upsertRolePermissions("ADMIN", permissionMap.values())`). But `UserPrincipal.create()` has a **second, hardcoded** copy of "what ADMIN should have" (lines 34-43) that gets unioned into the principal's authorities whenever `roles.contains("ADMIN")` — and that list was not updated to include `"notificacoes:view"` (it does correctly include the `pareceres:*` scopes added in an earlier phase, showing this list has already needed — and gotten — at least one prior update, then missed this one). This is a duplicate source of truth for authorization data that has now measurably drifted.
Concretely this is currently masked, not exploited: `JwtAuthenticationFilter` re-derives `permissions` fresh from `user.getRoles()...getPermissions()` on every request (not just at login), `seedRbac()` runs unconditionally on every application startup (it executes before the `seedEnabled` gate, `DatabaseSeeder.java:41-45`), and `AdminController.updateRbac()` explicitly refuses to let anyone edit the ADMIN role (`"ADMIN".equals(roleName)) continue;`) — so the DB-backed permission set for ADMIN self-heals to the correct value on every restart. The residual risk is a narrow window on the very first boot after this deploy (Spring's embedded server can begin accepting connections before `CommandLineRunner`s finish) and, more importantly, the next scope addition repeating this exact miss with less benign timing.
**Fix:** Either delete the hardcoded list entirely (redundant given `dbPermissions` already carries the role-derived set) or add `"notificacoes:view"` to it and add a code comment pointing back at `DatabaseSeeder.seedRbac()` so the two stay in lockstep:
```java
if (roles.contains("ADMIN")) {
    // Keep in sync with DatabaseSeeder.seedRbac()'s permKeys list.
    permissions.addAll(java.util.Arrays.asList(
            "clientes:view", "clientes:edit",
            "processos:view", "processos:edit",
            "processos:create", "processos:manage",
            "agenda:view", "agenda:edit",
            "documentos:view", "documentos:edit",
            "financeiro:view", "financeiro:edit",
            "rbac:manage", "users:manage",
            "pareceres:view", "pareceres:create", "pareceres:edit", "pareceres:manage",
            "notificacoes:view"
    ));
}
```

### WR-05: `NotificacaoController.listar` has no validation on `page`/`size`, turning bad input into a 500

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:69-71`
**Issue:** `page`/`size` are bound directly from request params with no bounds checking before `PageRequest.of(page, size)` is called. `PageRequest.of` throws `IllegalArgumentException` for `page < 0` or `size < 1` (e.g. `GET /api/v1/notificacoes?size=0`). This isn't caught anywhere in this controller, so it falls through to `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)`, which returns **HTTP 500** with the raw exception class name and message in the JSON body — for what is really trivial, guessable client-input validation that should be a 400. This is the first use of `Pageable`/`PageRequest` in the backend (per the repository's own comment), so there's no existing safe pattern to have copied.
**Fix:**
```java
@GetMapping
public ResponseEntity<?> listar(
        @RequestParam(required = false) String categoria,
        @RequestParam(required = false) Boolean lida,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    if (page < 0 || size < 1) {
        return ResponseEntity.badRequest().body(Map.of("message", "page deve ser >= 0 e size deve ser >= 1"));
    }
    Pageable pageable = PageRequest.of(page, size);
    ...
}
```

## Info

### IN-01: `createdAt` nullability inconsistent between entity and migration

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:51-52` vs `backend/migrations/86-create-notificacao-table.sql:30`
**Issue:** Every other required column on this entity pairs a `NOT NULL` in the migration with an explicit `nullable = false` on the `@Column` annotation. `createdAt` is the one exception: the migration declares `created_at TIMESTAMP NOT NULL`, but `@Column(name = "created_at", updatable = false)` omits `nullable = false`. Harmless today because `@PrePersist onCreate()` always populates it before insert, and Hibernate's `ddl-auto=validate` does not enforce nullability parity — but it's an inconsistent declaration that could confuse a future reader into thinking it's optional.
**Fix:** Add `nullable = false` for self-documentation/consistency:
```java
@Column(name = "created_at", updatable = false, nullable = false)
private LocalDateTime createdAt;
```

### IN-02: ADMIN fan-out doesn't filter by active status

**File:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java:48` (via `UserRepository.findByTenantIdAndRoleName`, `backend/src/main/java/com/lexcv/repositories/UserRepository.java:16-17`)
**Issue:** `notificarAdmins` fans out to every user returned by `findByTenantIdAndRoleName(tenantId, "ADMIN")`, which does not filter on `ativo` (unlike `JwtAuthenticationFilter`, which explicitly gates authentication on `user.getAtivo()`). A deactivated admin account will still accumulate notification rows. This may be intentional (the code comment says "cada ADMIN atual", not "ativo"), but it's worth an explicit decision given the rest of the app treats `ativo=false` as "this account shouldn't be treated as a live actor."
**Fix:** If unintentional, add an `ativo` predicate (either a new repository method or a filter in the service); if intentional, a one-line comment would save the next reader from re-litigating it.

### IN-03: New frontend scope registry has no consumers yet

**File:** `web/src/lib/permissions.ts:6-15`
**Issue:** `KNOWN_SCOPES`/`PermissionScope` are exported but not referenced anywhere else in `web/src` (confirmed via full-tree search), and no other file references `"notificacoes"` at all — there is no notification bell/list/hook consuming this yet. Consistent with this being an infrastructure-only phase (mirrors `notificarAdmins` being unwired on the backend until Phase 87), so likely intentional groundwork rather than a defect — flagging only so it isn't mistaken for a completed feature.

### IN-04: `@Builder.Default` on `lida` only applies via the builder

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:47-49`
**Issue:** Lombok's `@Builder.Default` moves the `= false` initializer out of the field and into the generated builder's `build()` method. It does not apply to the `@NoArgsConstructor`-generated constructor. Every current construction path goes through `.builder()...build()` (in `NotificacaoService.criar` and the test), so this doesn't manifest today, but a future `new Notificacao()` + setters path (or a Jackson deserializer relying on the no-args constructor, should this entity ever become a `@RequestBody`) would leave `lida` as `null` rather than `false`, and `null` would fail the `NOT NULL` constraint at insert time rather than being silently corrected.

### IN-05: `getTenantId()`/`getUserId()` boilerplate duplicated across a fourth controller

**File:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java:52-62`
**Issue:** The `Authentication auth = SecurityContextHolder...; UserPrincipal principal = (UserPrincipal) auth.getPrincipal(); return principal.getXxxId();` pattern is now hand-copied in four controllers (`ResourceController.java:117`, `ParecerController.java:48`, `ParecerPesquisaController.java:37`, and this one at lines 52-62, which additionally introduces `getUserId()` as a new copy of the same shape). Not a new problem introduced by this phase, but this phase does grow it, and it's now duplicated enough times that a shared `@Component` (e.g. `CurrentUserContext.getTenantId()`/`getUserId()`) would pay for itself.

---

_Reviewed: 2026-07-08T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
