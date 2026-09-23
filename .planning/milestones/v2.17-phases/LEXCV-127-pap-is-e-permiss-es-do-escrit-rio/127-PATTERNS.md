# Phase 127: Papéis e Permissões do Escritório - Pattern Map

**Mapped:** 2026-09-21
**Files analyzed:** 12 (backend: 6 modify-in-place + new DTOs/queries + tests; frontend: 4 modify/rewrite + new types/schema)
**Analogs found:** 12 / 12 (all analogs are within this same codebase; several are the files themselves, pre-cutover, or the Phase 125 moldes console reused nearly verbatim)

No RESEARCH.md for this phase (skipped per config). This map is CODE analogs only — a UI-SPEC is being produced in parallel; do not duplicate its design decisions here.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/.../config/UserPrincipal.java` | model (security principal) | request-response | itself (`create`, lines 27-62) + `ResolucaoPapeisService.resolverNomesPapeis` (proveniência shape) | exact — modify in place |
| `backend/.../config/JwtAuthenticationFilter.java` | middleware | request-response | itself (lines 71-81, the one real per-request `UserPrincipal.create` call site) | exact — modify in place |
| `backend/.../services/VerificacaoDerivaPapeisService.java` | service | batch/comparison | itself (lines 89-95, 139-145 — two more `UserPrincipal.create` call sites, batch not per-request) | exact — modify in place (ripple, not explicitly named in the prompt) |
| `backend/.../controllers/ParecerController.java` (lines 423, 494) | controller | request-response | `ParecerController.validateAdvogado` (lines 76-83, already converted) — but NOT directly reusable, see analysis below | partial — needs the new `UserPrincipal` provenance field, not `resolucaoPapeisService.temPapelDeMolde` directly |
| `backend/.../controllers/AdminController.java` (`getRbac`/`updateRbac`) | controller | CRUD | itself (`listUsers`/`createUser`/`updateUser`, tenant-scoping pattern already in the same file) + `PlatformAdminController`'s molde CRUD (Phase 125) | exact |
| `backend/.../controllers/AdminController.java` (new role CRUD: create/rename/delete) | controller | CRUD | `PlatformAdminController` `POST/PUT /platform/moldes` (Phase 125) + `ResourceController.deleteHonorario` (delete-guard-by-count idiom) | exact |
| `backend/.../repositories/UserRepository.java` (new count query) | repository | CRUD (aggregate) | `UserRepository.countByTenantIdAndAtivoTrue` (line 38) + `TenantRoleRepository.countByMoldeId` (line 23) | exact |
| `backend/.../repositories/TenantRoleRepository.java` (possible new finder) | repository | CRUD | itself (`findByTenantId`, `findByTenantIdAndNome`, already scaffolded) | exact |
| New DTOs under `backend/.../dtos/` (role CRUD request/response) | dto | request-response | `RbacResponse`/`RbacResponse.PermissionDefDto` + `MoldesConsolaResponse`-family DTOs (Phase 125) | exact |
| Mockito tests (backend) | test | request-response | `AdminControllerAtribuicaoPapeisEscritorioTest` (multi-tenant isolation, Caso 4) + `AdminControllerPlataformaAdminContencaoTest`/`PlatformAdminControllerTest` (real `@PreAuthorize` proxy) | exact |
| `web/src/app/(dashboard)/settings/page.tsx` (`RbacTab` rewrite) | component | request-response | `web/src/app/(dashboard)/plataforma/moldes/page.tsx` + `merge-local-permissoes.ts` + `criar-molde-panel.tsx` (Phase 125, editable matrix with create panel) | exact |
| `web/src/hooks/use-admin.ts` (`useAdminRbac` + new mutations) | hook | request-response | `web/src/hooks/use-platform-moldes.ts` (full file, Phase 125) | exact |
| `web/scripts/verify-bloqueio-rbac.mjs` (rewrite) | test (structural gate) | batch | itself (12 assertions, full file) + `web/scripts/verify-consola-moldes.mjs` (16-assertion shape for an editable matrix gate) | exact |
| New types/schemas (frontend) | type/schema | n/a | `web/src/types/platform-moldes.ts` + `web/src/schemas/moldes.ts` (Phase 125) | exact |

---

## Pattern Assignments

### 1. `UserPrincipal.create`'s signature and every call site — the exact ripple

**Current signature and full method** (`backend/src/main/java/com/lexcv/config/UserPrincipal.java:27-62`):
```java
public static UserPrincipal create(UUID userId, UUID tenantId, String nome, String email, Set<String> roles, Set<String> dbPermissions) {
    Set<String> permissions = new java.util.HashSet<>(dbPermissions);

    Set<SimpleGrantedAuthority> authorities = roles.stream()
            .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
            .collect(Collectors.toSet());

    if (roles.contains("ADMIN")) {
        // Keep in sync with DatabaseSeeder.CATALOGO_PERMISSOES (Phase 124).
        permissions.addAll(java.util.Arrays.asList(
                "clientes:view", "clientes:edit", /* ...20 entries... */
        ));
    }

    permissions.stream().map(SimpleGrantedAuthority::new).forEach(authorities::add);

    return UserPrincipal.builder()
            .userId(userId).tenantId(tenantId).nome(nome).email(email)
            .roles(roles).permissions(permissions).authorities(authorities)
            .build();
}
```
The class itself (`UserPrincipal.java:15-25`) is a flat `@Getter @AllArgsConstructor @Builder` over `userId, tenantId, nome, email, roles (Set<String>), permissions (Set<String>), authorities`. There is no field today that carries `moldeId`/provenance — Decision 2a requires adding one (e.g. a `Set<Integer> moldeIds` or a narrower `Integer moldeIdAdmin`-style field, resolved once, same shape as `roles`).

**Every call site, verified by grep (not by assumption) — there are exactly TWO production call sites, not three:**

1. **`JwtAuthenticationFilter.java:71-81`** — the real per-request path. Already resolves roles/permissions via `ResolucaoPapeisService` (`resolverNomesPapeis`, `resolverPermissoesEfectivas`) before calling `create`:
```java
Set<String> roles = resolucaoPapeisService.resolverNomesPapeis(user);
Set<String> permissions = resolucaoPapeisService.resolverPermissoesEfectivas(user);

UserPrincipal principal = UserPrincipal.create(
        user.getId(), user.getTenantId(), user.getNome(), user.getEmail(),
        roles, permissions
);
```
This is the seam Decision 2a should extend through: `ResolucaoPapeisService` already computes `usaPapeisDeEscritorio(user)` once per call and already has the `moldeId`-provenance predicate (`temPapelDeMolde`, see below) — a new `ResolucaoPapeisService` method (e.g. `resolverMoldeIds(User user)`, mirroring `resolverNomesPapeis`'s exact shape: map `user.getTenantRoles()` to `TenantRole::getMoldeId` when `usaPapeisDeEscritorio`, else derive from `user.getRoles()` — global roles' own ids ARE their own "moldeId" for a plataforma admin, so the fallback branch is simply `user.getRoles().stream().map(Role::getId)`) is the natural single new query-free source, then passed into `UserPrincipal.create`'s new parameter, exactly as `roles`/`permissions` already are.

2. **`VerificacaoDerivaPapeisService.java:89-95` and `:139-145`** — NOT per-request, a batch drift-verification tool (MIGR-02) that constructs `UserPrincipal` twice per user (before/after) purely to reuse the ADMIN-block-and-authority-derivation logic without duplicating it. Both call sites must gain the new parameter too, or this file stops compiling the moment the signature changes:
```java
// capturarAntes (line 89) — builds from GLOBAL roles/permissions only, by design (doc-comment
// at lines 63-75 explains why it must never go through ResolucaoPapeisService)
UserPrincipal principal = UserPrincipal.create(
        user.getId(), user.getTenantId(), user.getNome(), user.getEmail(),
        nomesGlobais, permissoesGlobais);

// verificarSemDeriva (line 139) — builds from the NEW (tenant-role) side via the resolver
UserPrincipal principalDepois = UserPrincipal.create(
        user.getId(), user.getTenantId(), user.getNome(), user.getEmail(),
        nomesDepois, permissoesDepois);
```
**Important scoping note for the planner:** this service is explicitly out of the `<code_context>` Integration Points list in 127-CONTEXT.md, but it WILL break at compile time if the signature changes without updating it. It is not migration-relevant to Phase 127's behavior (it was Phase 126's one-time drift tool), but it must compile — the safest fix is giving the new parameter a sensible default at each of these two sites (e.g. `Set.of()` for `capturarAntes`, since "before" never needs molde provenance; and the same `resolverMoldeIds`-style call as the filter for `verificarSemDeriva`, since it already goes through `ResolucaoPapeisService`).

**`AuthController.java` does NOT call `UserPrincipal.create`** — confirmed by grep across the whole file. `login`/`refresh` (lines ~129-131, ~189) only call `resolucaoPapeisService.resolverNomesPapeis(user)` to build the JWT's role-name list, never construct a principal. `getMe` (lines 209-232) reads an already-resolved `UserPrincipal` from `SecurityContextHolder`, never rebuilds one. `updateMe` (lines ~244+) calls `resolucaoPapeisService.resolverNomesPapeis`/`resolverPermissoesEfectivas` directly for the response DTO, again never touching `UserPrincipal.create`. **The prompt's hint that `AuthController` may need updating "wherever `UserPrincipal.create` is called" does not apply — correct this assumption before planning**, but `AuthController` DOES already call `ResolucaoPapeisService`, so if the new molde-provenance accessor is added there too for some other reason, the wiring point already exists.

**Test call sites that will need updating for a compile-safe signature change** (found by grep, not exhaustively read — flag for the planner, do not treat as scoped-out):
- `backend/src/test/java/com/lexcv/config/UserPrincipalCatalogoSyncTest.java:35` — direct `UserPrincipal.create(...)` call.
- `backend/src/test/java/com/lexcv/config/JwtAuthenticationFilterPapeisEscritorioTest.java` — references the ADMIN block by name (lines ~190-212), may not call `create` directly but should be checked.
- `backend/src/test/java/com/lexcv/services/VerificacaoDerivaPapeisServiceTest.java` — exercises `VerificacaoDerivaPapeisService`, which itself calls `create` twice (see above); this test's fixtures will need the new field's expected values.
- `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java` — uses `UserPrincipal.create`-derived authorities as its subject; check whether it constructs a principal directly or via `autenticarComoRoles`-style raw `SimpleGrantedAuthority` (the latter, per its own comment at line 490, does NOT go through `UserPrincipal.create`, so may be unaffected).

---

### 2. `ParecerController:423` and `:494` — verbatim, and why they cannot just call `temPapelDeMolde`

**Both sites, byte-for-byte identical shape** (`ParecerController.java:421-429` and `:492-500`):
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
boolean isAdmin = principal.getRoles().contains("ADMIN");
boolean isResponsavel = solicitacao.getAdvogadoId() != null
        && solicitacao.getAdvogadoId().equals(principal.getUserId());
if (!isAdmin && !isResponsavel) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(Map.of("message", "Apenas o advogado responsável ou ADMIN pode entregar o parecer")); // or "...criar uma versão"
}
```

**Why these two are structurally different from the three sites Phase 126 already converted** (`ParecerController.validateAdvogado:76-83`, `ResourceController.addClienteAdvogado:514-520`, `ResourceController.addClienteAdministrativo:580-588`): all three converted sites operate on a **freshly loaded `User` entity** (`User user = userRepository.findById(...)`), which is what `ResolucaoPapeisService.temPapelDeMolde(User user, String nomeMolde)` requires as its first parameter — it reads `user.getTenantRoles()` directly off the entity. `validateAdvogado`'s own converted call:
```java
// ParecerController.java:76,83 (already converted, Phase 126 Plan 05)
User user = userRepository.findById(advogadoId).orElse(null);
...
if (!resolucaoPapeisService.temPapelDeMolde(user, NOME_MOLDE_ADVOGADO)) { ... }
```
Lines 423/494, by contrast, operate on `principal` — the `UserPrincipal` from `SecurityContext`, already resolved once per request by the filter — not on a `User` entity. `UserRepository` IS already injected in this controller (`ParecerController.java:44`), so a naive fix (`userRepository.findById(principal.getUserId())` then `temPapelDeMolde`) is technically possible, but this is exactly the extra per-request query `JwtAuthenticationFilter`'s own comment (lines 50-58) is written to avoid ("uma query por pedido, sem memorização" is the accepted cost; a second ad-hoc lookup at these two business-logic sites would silently double it). Decision 2a locks in the alternative: resolve molde provenance ONCE in the filter (same place `roles`/`permissions` are already resolved) and carry it on the principal, so these two sites become a simple in-memory set check — `principal.getMoldeIds().contains(adminMoldeId)` or an equivalent helper — no new query, same cost profile as today's `principal.getRoles().contains("ADMIN")`.

**Concrete replacement shape to aim for** (composing `resolverNomesPapeis`'s exact structure from `ResolucaoPapeisService.java:98-107` with `temPapelDeMolde`'s provenance check from lines 152-165): a new `ResolucaoPapeisService.resolverMoldeIds(User user)` returning `Set<Integer>`, called once by the filter alongside the two existing calls, then a `UserPrincipal` accessor (or the constructor carrying the raw set) that `ParecerController` checks against the id of the global `Role` named `"ADMIN"` (resolved once, e.g. injected `RoleRepository` + `findByNome("ADMIN")`, same idiom `SetupService.provisionTenant` already uses at `SetupService.java:124-125` and `temPapelDeMolde` itself uses at `ResolucaoPapeisService.java:154-156`).

---

### 3. `AdminController.getRbac`/`updateRbac` — current global-Role shape, and the tenant-scoping analog already in the same file

**Current gates and full bodies** (`AdminController.java:441-557`):
```java
@PreAuthorize("hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')")
@GetMapping("/rbac")
public ResponseEntity<?> getRbac() {
    List<Role> roles = roleRepository.findAll();               // GLOBAL, not tenant-scoped
    ...
    for (Role role : roles) {
        if (PAPEL_PLATAFORMA.equals(role.getNome())) { continue; }
        List<String> perms = role.getPermissions().stream().map(Permission::getNome).collect(Collectors.toList());
        rolePermissions.put(role.getNome(), perms);
    }
    List<Permission> permissoesCatalogo = permissionRepository.findAllByReservadaPlataformaFalse();
    ...
}

@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")               // <- Decision 1/3: becomes hasAuthority('rbac:manage'), tenant-scoped
@PutMapping("/rbac")
public ResponseEntity<?> updateRbac(@RequestBody Map<String, Object> body) {
    ...
    for (Map.Entry<?, ?> entry : newRolePermissions.entrySet()) {
        String roleName = (String) entry.getKey();
        if ("ADMIN".equals(roleName) || PAPEL_PLATAFORMA.equals(roleName)) { continue; }
        Role role = roleRepository.findByNome(roleName).orElse(null);   // GLOBAL, not tenant-scoped
        ...
        role.setPermissions(permissions);
        roleRepository.save(role);
    }
    return ResponseEntity.ok(Map.of("message", "..."));
}
```
Both handlers read/write the **global** `Role`/`Permission` tables — this is the exact ISOL-03 problem Decision 1 explains and asks the plan to explicitly retire. `PAPEL_PLATAFORMA`/`PAPEL_PLATAFORMA_AUTORIDADE` (constants at lines 57-65, with the full historical comment on why both the raw and `ROLE_`-prefixed forms are blocked) are the analog to preserve for "never expose/accept `PLATAFORMA_ADMIN`" — same double-form guard belongs in the rewritten tenant-scoped handlers.

**Tenant-scoping analog already established in the SAME controller** — `listUsers` (`AdminController.java:75-101`) and `createUser`/`updateUser`'s `resolverTenantRolesOuErro` (`AdminController.java:139-178, 232-236, 371-384`) are the two closest in-file precedents for "read/write `TenantRole` scoped to `principal.getTenantId()`, never a value from the request body":
```java
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
List<User> users = userRepository.findByTenantId(principal.getTenantId());
```
The rewritten `getRbac` should follow this exact shape but call `tenantRoleRepository.findByTenantId(principal.getTenantId())` (already scaffolded, `TenantRoleRepository.java:17`, its own comment naming this exact phase as the intended caller) instead of `roleRepository.findAll()`.

**Cross-tenant CRUD analog (create/rename/delete) — `PlatformAdminController`'s molde endpoints (Phase 125)**, already summarized fully in `125-PATTERNS.md` "Pattern Assignments" section for `backend/.../controllers/PlatformAdminController.java` (create shape at `createTenant`-style validate→save→201, guard-a-reserved-entity idiom at `setTenantAtivo`-style literal-name check, DTO-projection helper at `toSummary`) — copy that same request→validate→persist→project shape, but scope every read/write by `principal.getTenantId()` instead of operating cross-tenant, and operate on `TenantRoleRepository`/`TenantRole` instead of `RoleRepository`/`Role`.

**`@PreAuthorize("hasAuthority(...)")` vs `hasRole(...)` — concrete examples of both already in this codebase, confirming Decision 3's note:**
- `hasRole('ADMIN')` (class-level, `AdminController.java:32`), `hasRole('PLATAFORMA_ADMIN')` (`PlatformAdminController.java:53`, `updateRbac` today) — used for the app's fixed **role** names, which `UserPrincipal.create` DOES prefix with `"ROLE_"` (`UserPrincipal.java:30-32`), so `hasRole('X')` (Spring's own `"ROLE_" + X` convention) lines up.
- `hasAuthority('clientes:edit')`, `hasAuthority('pareceres:view')`, `hasAuthority('financeiro:manage')` — pervasive throughout `ResourceController.java` and `ParecerController.java` (every method-level `@PreAuthorize`) — used for **permission** names, which `UserPrincipal.create` does NOT prefix (`permissions.stream().map(SimpleGrantedAuthority::new)`, `UserPrincipal.java:49-51`, no `"ROLE_"` added). `rbac:manage` is a permission, not a role, so `updateRbac`'s new gate must be `@PreAuthorize("hasAuthority('rbac:manage')")` — `hasRole('rbac:manage')` would silently check for authority `"ROLE_rbac:manage"`, which no `SimpleGrantedAuthority` in this codebase ever carries, and would 403 every caller unconditionally. This exact trap is already documented in `AdminController.java:59-64`'s own comment about `PAPEL_PLATAFORMA_AUTORIDADE`.

---

### 4. Delete guard by count — `PAPEL-05` (assigned role cannot be deleted)

**Existing "refuse delete because dependent rows exist" precedent** (`ResourceController.java:3137-3154`, `deleteHonorario`):
```java
@PreAuthorize("hasAuthority('financeiro:manage')")
@DeleteMapping("/honorarios/{id}")
public ResponseEntity<?> deleteHonorario(@PathVariable Integer id) {
    Honorario hon = honorarioRepository.findById(id).orElse(null);
    if (hon == null) { return ResponseEntity.status(HttpStatus.NOT_FOUND)...; }
    Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) { return ...NOT_FOUND...; }
    List<Pagamento> pagamentos = pagamentoRepository.findByHonorarioId(id);
    if (!pagamentos.isEmpty()) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Não é possível eliminar um honorário com pagamentos registados"));
    }
    honorarioRepository.deleteById(id);
    return ResponseEntity.noContent().build();
}
```
This is the right *shape* (404-then-409-then-delete, 409 body message naming why) but its dependency check is a `findByX(id).isEmpty()` list load, not a count — CONTEXT.md Decision 4 explicitly requires "a verificação é por contagem, não por tentativa-e-erro". **Use the counting idiom instead**, following the two existing `countBy...` derived queries verbatim:
```java
// UserRepository.java:38 (existing)
long countByTenantIdAndAtivoTrue(UUID tenantId);

// TenantRoleRepository.java:23 (existing)
long countByMoldeId(Integer moldeId);
```
The new query needed is on `UserRepository`, traversing the `@ManyToMany` collection property `User.tenantRoles` (`User.java:77-84`, join table `t_user_tenant_role`, join column `tenant_role_id`) — Spring Data JPA supports property-path derivation through a collection the same way `findByTenantIdAndRoleName` already traverses `User.roles` (`UserRepository.java:16-17`, `JOIN u.roles r WHERE r.nome = ...`). The derived-method form (no custom `@Query` needed, mirroring `countByMoldeId`'s style exactly):
```java
long countByTenantRolesId(UUID tenantRoleId);
```
Guard shape to copy at the new `DELETE /admin/rbac/roles/{id}`-style handler:
```java
long atribuicoes = userRepository.countByTenantRolesId(tenantRole.getId());
if (atribuicoes > 0) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
            "Este papel está atribuído a " + atribuicoes + " utilizador(es) e não pode ser apagado."));
}
```

**Second guard needed by Decision 4b** (the office's own admin role can never be deleted nor stripped of the permissions that make it admin) — discriminate by **provenance** (`TenantRole.moldeId`), not name, exactly as `ResolucaoPapeisService.temPapelDeMolde` already discriminates by `moldeId` rather than string comparison (`ResolucaoPapeisService.java:152-165`). The global `Role` named `"ADMIN"`'s id (resolved once via `roleRepository.findByNome("ADMIN")`, same call `SetupService.provisionTenant` and `temPapelDeMolde` already make) is the value to compare `tenantRole.getMoldeId()` against — never `tenantRole.getNome().equals("ADMIN")`, since Decision 4b's whole point is that the office's admin role's name is no longer a safe discriminator once renaming ships.

---

### 5. New DTOs — analog is the Phase 124/125 pair already in this codebase

**`RbacResponse.java` (full file, `backend/src/main/java/com/lexcv/dtos/RbacResponse.java`)** — the existing response shape `getRbac` already returns, nested static DTO for the permission catalog entries:
```java
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class RbacResponse {
    private Map<String, List<String>> rolePermissions;
    private List<PermissionDefDto> systemPermissions;

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class PermissionDefDto {
        private String key;
        private String nome;
        private String descricao;
        private String modulo;
    }
}
```
Tenant-scoped `getRbac` can keep this exact response shape (map keyed by `TenantRole.nome` instead of `Role.nome`) if the plan chooses not to expose `TenantRole.id`/`moldeId`/`sistema` in the read model — but CONTEXT.md's `<specifics>` requires the UI to distinguish molde-instantiated vs. own roles and show per-role user counts, so the response DTO almost certainly needs new fields per role (id, `sistema`, `atribuicoes`) — follow the **Molde-list response DTO shape** already recommended in `125-PATTERNS.md` (`id`, `nome`, `permissoes`, a live count field) rather than reusing `RbacResponse` unchanged; the closest full analog for a per-item-with-live-count response is `PlatformAdminController.toSummary` (`utilizadoresAtivos` computed live via a repository count at projection time, `PlatformAdminController.java:191-200`, cited in full in `125-PATTERNS.md`).

**Request DTO shape** — follow `TenantUpdateRequest`/`MoldesUpdateRequest`'s plain `@Getter @Setter` (or a `record`, per the codebase's newer `record ResolucaoTenantRolesOuErro` convention at `AdminController.java:139-147`) mutable-body shape for create/rename/edit-permissions payloads — never `@Data`/`@Builder` for a Jackson-deserialized request body, matching the existing convention split between response DTOs (`@Builder`) and request DTOs (plain setters or records).

---

### 6. Backend tests — three distinct precedents to compose, not invent

**Mockito constructor-injection convention** (`AdminControllerAtribuicaoPapeisEscritorioTest.java`, full pattern already summarized in `126-PATTERNS.md` "Shared Patterns" — same `@ExtendWith(MockitoExtension.class)`, `@Mock` per collaborator, `novoController()` helper matching `AdminController`'s exact `@RequiredArgsConstructor` field order):
```java
private AdminController novoController() {
    ResolucaoPapeisService resolucaoPapeisService =
            new ResolucaoPapeisService(tenantRoleRepository, roleRepository);
    return new AdminController(userRepository, roleRepository, permissionRepository, passwordEncoder,
            tenantRepository, resolucaoPapeisService);
}
```
Note `ResolucaoPapeisService` is constructed for real (not mocked) inside this helper — the tests exercise its real logic against mocked repositories, never stub `resolverPapeisDeEscritorio`/`temPapelDeMolde` directly. Follow this exactly for any new test touching tenant-scoped RBAC CRUD.

**Multi-tenant isolation proof (Decision 5) — exact precedent already in this codebase**, `AdminControllerAtribuicaoPapeisEscritorioTest.createUser_comTenantRoleHomonimoNoutroTenant_naoAssociaOPapelDeOutroEscritorio` (lines 185-214): stubs the OTHER tenant's lookup with `lenient()` (proving by absence-of-call that it's never reached), asserts `verify(tenantRoleRepository, never()).findByTenantIdAndNome(eq(OUTRO_TENANT_ID), any())`. This is the "monta dois tenants, grava no primeiro e assere que o segundo não mudou" precedent Decision 5 cites, at the Mockito-mock level (not necessarily a full two-real-tenant Testcontainers integration test — both shapes are valid per `126-PATTERNS.md` section 6, but this is the more directly reusable one for controller-level isolation).

**Real `@PreAuthorize` proxy technique (never annotation-reflection)** — already fully documented in `125-PATTERNS.md` (`novoProxyComMethodSecurity`/`autenticarComoRoles` via `ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()`, `PlatformAdminControllerTest.java:78-134`), and again in `AdminControllerPlataformaAdminContencaoTest.java` for `AdminController` specifically. Reuse verbatim to prove: (a) a tenant `ADMIN` without `rbac:manage` gets 403 on the new `PUT`; (b) `PLATAFORMA_ADMIN` is unreachable from any new office-scoped handler if that's the intended contract; (c) `hasAuthority('rbac:manage')` — not `hasRole` — actually gates the endpoint (a test using `autenticarComoRoles` alone, which per its own comment at `PlatformAdminControllerMoldesTest.java:490` does NOT add the `ROLE_` prefix, is the right tool to prove the authority-vs-role distinction from section 3 above).

---

## Frontend Pattern Assignments

### 7. `RbacTab` (`web/src/app/(dashboard)/settings/page.tsx:755-994`) — the whole tab, to be rewritten

**Full current structure, already read in full:**
- Guard: `me.isFetched && me.data?.roles?.includes("PLATAFORMA_ADMIN")` (line 763) — the read-only/writable split to invert.
- Hardcoded 4-role column list (line 806): `const roles: MockRole[] = ["ADMIN", "TECNICO", "ADVOGADO", "ASSISTENTE"];` — **this must become dynamic**, derived from the tenant's own `TenantRole` list (variable count, renameable), exactly the way `moldes/page.tsx`'s `moldesFiltrados`/columns are already dynamic (`moldesFiltrados.map((molde) => <th key={molde.id}>...)`, `moldes/page.tsx:339-357`).
- `handleCheckboxChange` (lines 808-826): local-state-only toggle, gated by `!isPlatformAdmin return` and `role === "ADMIN" return` (the immutable-admin-row guard) — both guards need re-targeting: the immutability guard must become provenance-based (the tenant's own admin `TenantRole`, identified by `moldeId`, not by the string `"ADMIN"` — same Decision 4b discriminator as the backend), and the write-permission guard flips from `isPlatformAdmin` to `can.manage("rbac")`-or-equivalent (the tab is already gated open by `hasRbacManage` at the page level, `settings/page.tsx:58`, so the *tab itself becomes writable to whoever can already open it* — no separate hidden platform check remains).
- `handleSave` (lines 828-847): direct `apiFetch("/admin/rbac", { method: "PUT", ... })`, bypassing `useAdminSaveRbac` (which already exists in `use-admin.ts:69-85` unused) — **this is dead code precedent worth fixing while rewriting**, not a pattern to copy: switch to the mutation hook, matching `moldes/page.tsx`'s `atualizarMoldes.mutateAsync(...)` convention (`moldes/page.tsx:188-193`).
- Read-only Badge+Tooltip block (lines 863-887) and permanent info-box mirror (lines 903-917) — the whole "Gerido pela Plataforma" apparatus is removed per Decision 1, not adapted; the `verify-bloqueio-rbac.mjs` gate's own assertion `A02` (Hooks-rule ordering) is the one piece that must survive intact (see section 9 below).

**Direct reuse analog — `moldes/page.tsx` (Phase 125, full file already read) is the closest working "editable permission matrix with variable columns, create panel, and unsaved-edit-merge" in the codebase.** Concretely reusable pieces:
- Dynamic column matrix with `scope="col"` header + `<th scope="row">`/`text-left` body + `aria-label` per checkbox (`moldes/page.tsx:332-357`, `378-399`) — copy verbatim, swapping `molde`/`moldeId` for the tenant's own role/roleId.
- The unsaved-edit reconciliation pattern — `mesclarEstadoLocal` (`merge-local-permissoes.ts`, full file already read) — reusable **as-is or by direct adaptation** (swap `MoldesConsola`/`moldeId: number` for the tenant-role equivalent, `id: string` UUID) — this exact function already solves "don't lose an operator's unsaved checkbox edits when an unrelated mutation invalidates the list query", which applies identically to a rename/create/delete CRUD screen invalidating the same list.
- `CriarMoldePanel` (full file already read) — inline (not modal) creation form via `react-hook-form` + `zodResolver`, presentational only (mutation/toast owned by the parent page) — direct analog for a "Criar Papel" panel; needs a corresponding rename affordance (no existing rename-UI analog in this codebase — flagged under "No Analog Found" below).
- The `AlertDialog` confirm-before-save-with-per-item-warning pattern (`moldes/page.tsx:420-475`) is the *closest* analog for a delete-confirmation dialog (PAPEL-05's "cannot delete if assigned" and PAPEL-08's "cannot delete the office admin role" need user-facing messaging, not just a 409 toast) — but its actual warning content (non-propagation) doesn't apply to Phase 127; only the AlertDialog wiring shape (`AlertDialogAction` is the sole place the mutation fires, `Cancel` never triggers it) should be reused.

**Zod schema analog** — `web/src/schemas/moldes.ts` (full file already read), the `NOME_RESERVADO_PLATAFORMA` refine-and-transform-to-uppercase shape:
```ts
export const criarMoldeSchema = z.object({
  nome: z.string().trim().min(1, "O nome do molde é obrigatório.")
    .refine((valor) => valor.toUpperCase() !== NOME_RESERVADO_PLATAFORMA, { message: "..." })
    .transform((valor) => valor.toUpperCase()),
  permissoes: z.array(z.string()),
});
```
Copy this exact shape for a "criar/renomear papel de escritório" schema — same reserved-name refine (still block `PLATAFORMA_ADMIN` as a name an office could pick), same mirror-of-backend-guard comment convention.

---

### 8. `web/src/hooks/use-admin.ts` — rewrite `useAdminRbac`, add CRUD mutations

**Current state** (full file already read, 85 lines): `useAdminRbac` already exists and works (`queryKey: ["admin", "rbac"]`, `apiFetch<RbacResponse>("/admin/rbac")`, `staleTime: 60_000`); `useAdminSaveRbac` already exists but is unused by `RbacTab` (a bug/gap to close in the same rewrite, see section 7). Both currently type against **legacy mock types** (`import type { MockUser, MockRolePermissions, MockPermissionDef } from "@/server/mock-db"`, line 5) — per `CLAUDE.md`'s "Legacy / ignore" section, `web/src/server/` is pre-backend mock code; this file importing from it is exactly the kind of stale wiring Phase 127 should clean up while touching this file, not carry forward.

**Full analog for the CRUD mutations to add** — `web/src/hooks/use-platform-moldes.ts` (full file already read, Phase 125) — copy its exact shape for `useCreateOfficeRole`/`useRenameOfficeRole`/`useDeleteOfficeRole` (query key array, `invalidateQueries` never `setQueryData`, `enabled: typeof window !== "undefined"` SSR guard, `satisfies` type assertion on request bodies):
```ts
const MOLDES_LIST_KEY = ["platform", "moldes", "list"] as const;

export function useMoldes() {
  const enabled = typeof window !== "undefined";
  return useQuery({ queryKey: MOLDES_LIST_KEY, queryFn: () => apiFetch<MoldesConsola>("/platform/moldes"), enabled, staleTime: 30_000 });
}

export function useCreateMolde() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: MoldeCreateRequest) =>
      apiFetch<{ id: number; nome: string }>("/platform/moldes", { method: "POST", body: JSON.stringify(payload satisfies MoldeCreateRequest) }),
    onSuccess: async () => { await queryClient.invalidateQueries({ queryKey: MOLDES_LIST_KEY }); },
  });
}
```
Note the id type difference: molde ids are `number` (global `Role.id`, `Integer`); office `TenantRole.id` is `UUID` (`string` in TypeScript) — every new hook/type must use `string`, not `number`, for role ids.

---

### 9. `web/scripts/verify-bloqueio-rbac.mjs` — all 12 assertions, and which one must survive

**Full file already read (247 lines).** The 12 assertions, verbatim ids and predicates:
1. `A01-usemme-dentro-do-rbactab` — `rbacBlock.includes("useMe()")`
2. `A02-hook-antes-do-primeiro-early-return` — `useMe()` index `<` `if (isLoading)` index. **Must survive the rewrite exactly as-is per Decision 2b** — the Hooks-rule ordering is orthogonal to who can save.
3. `A03-isplatformadmin-com-isfetched-e-papel` — a `const isPlatformAdmin` line containing both `me.isFetched` and `roles?.includes("PLATAFORMA_ADMIN")`. **Must be REPLACED**, not kept — the gate inverts; the new assertion should check for whatever gate variable replaces `isPlatformAdmin` (e.g. a `const podeGravar`/`hasRbacManage`-derived check), preserving the same "fetched-before-role" ordering discipline.
4. `A04-isfetched-antes-do-papel` — same line, `me.isFetched` index `<` role-check index (fail-closed during loading). Same replace-not-keep treatment as A03.
5. `A05-guardar-regras-sob-condicao` — `isPlatformAdmin ?` ternary index `<` `"Guardar Regras"` text index (button inside the true branch). Replace: the new gate should probably make the Save button **unconditional** (visible to anyone who can already open the tab) rather than ternary — if so, this assertion should be replaced with one asserting the button always renders, or asserting it's gated by the *new* permission variable instead of a role.
6. `A06-badge-outline-com-rotulo` — the "Gerido pela Plataforma" Badge, in the false branch of the ternary. **Must be REMOVED per Decision 1** — the badge apparatus goes away entirely; drop this assertion, do not adapt it.
7. `A07-span-tabindex-dentro-do-tooltiptrigger` — same Badge/Tooltip accessibility wiring. Removed alongside A06.
8. `A08-texto-exato-do-tooltip` — exact tooltip phrase. Removed alongside A06/A07.
9. `A09-hasrbacmanage-inalterado` — `settingsPage.includes('const hasRbacManage = can.manage("rbac") || isAdmin;')`, a non-regression guard on the **tab-visibility** gate (separate from the write gate) — this line is untouched by Phase 127 (Decision 3 makes `rbac:manage` finally govern something, it doesn't change who sees the tab) — **keep this assertion as-is**.
10. `A10-handlesave-e-leitura-intactos` — `apiFetch("/admin/rbac"` + `method: "PUT"` + `useAdminRbac()` all present. Needs updating if `handleSave` moves to `useAdminSaveRbac()`/a new mutation hook instead of raw `apiFetch` (see section 7's note on this pre-existing gap) — assert the new mutation hook's usage instead, or assert the raw `apiFetch` call if the executor decides not to fix that inconsistency in this phase.
11. `A11-matriz-admin-imutavel-inalterada` — `if (role === "ADMIN") return;` literal-string guard. **Must be REPLACED** with a provenance-based equivalent (Decision 4b: discriminator is `moldeId`, not the string `"ADMIN"`, precisely because the name becomes editable) — this is the single most important assertion to get right in the rewrite, since a literal string match here would silently stop working the moment an office renames its admin role.
12. `A12-matriz-so-leitura-para-nao-plataforma` — `if (!isPlatformAdmin) return;` in the toggle handler + `const isDisabled = isAdminRow || !isPlatformAdmin;`. **Must be REPLACED** — the matrix becomes writable for the same audience that can open the tab; if there's no longer a "read-only" mode at all (every viewer with `rbac:manage` can edit), this assertion may have no successor, or its successor asserts the OPPOSITE (matrix is never read-only-with-visible-checkboxes for someone who can't save).

**Shape for the rewritten gate** — `web/scripts/verify-consola-moldes.mjs` (16 assertions, ids read: `entrada-gerir-moldes`, `guarda-de-pagina-falha-fechado`, `aviso-camada1-banner`, `matriz-scope-col-row`, `matriz-aria-label-checkbox`, `matriz-colspan-dinamico`, `sem-regressoes-rbactab`, `painel-criacao-zod-e-fechar`, `painel-criacao-sem-mutacao-propria`, `wiring-pagina-usa-criarmoldepanel`, `matriz-sem-branch-readonly`, ...) is the closer structural template for a REWRITTEN gate proving an EDITABLE dynamic-column matrix, versus `verify-bloqueio-rbac.mjs`'s own current shape which proves a READ-ONLY one. In particular `matriz-sem-branch-readonly` (asserting the matrix has NO read-only conditional branch at all) is the exact inverse-assertion analog for what a rewritten `verify-bloqueio-rbac.mjs` (or its replacement script name) needs for the new guarantee — worth checking that assertion's predicate directly when writing the new gate, since it's proving precisely the opposite of old A12.

Same `stripComments`/marker-slice/assertion-loop/`process.exit` infrastructure (both scripts share it byte-for-byte, already shown in `125-PATTERNS.md`) — copy verbatim, do not reinvent.

---

## Shared Patterns

### `@PreAuthorize("hasAuthority('X')")` for permissions vs `hasRole('X')` for roles (backend)
**Source:** `ResourceController.java`/`ParecerController.java` (pervasive `hasAuthority`), `AdminController.java:32`/`PlatformAdminController.java:53` (`hasRole`), `UserPrincipal.java:30-32` vs `:49-51` (the prefixing asymmetry)
**Apply to:** `PUT /admin/rbac`'s new gate — must be `hasAuthority('rbac:manage')`, never `hasRole('rbac:manage')`.

### Tenant-scoping via `principal.getTenantId()`, never a request-body value (backend)
**Source:** `AdminController.listUsers` (line 79), `createUser`/`updateUser`'s `resolverTenantRolesOuErro` calls (lines 232, 372)
**Apply to:** rewritten `getRbac`/`updateRbac` and all new role-CRUD handlers.

### Provenance (`moldeId`) discriminator, never name comparison (backend)
**Source:** `ResolucaoPapeisService.temPapelDeMolde` (lines 152-165)
**Apply to:** the office-admin-role-is-undeletable guard (Decision 4b) and, if feasible within this phase's scope, `ParecerController:423/494` via the new `UserPrincipal` field (section 2 above).

### Count-based delete guard, never find-then-isEmpty (backend)
**Source:** `UserRepository.countByTenantIdAndAtivoTrue` / `TenantRoleRepository.countByMoldeId` (derived-query idiom) composed with `ResourceController.deleteHonorario`'s 404→409→delete shape
**Apply to:** the new role-delete handler (Decision 4a, PAPEL-05).

### Mockito constructor-injection + real-collaborator test convention (backend)
**Source:** `AdminControllerAtribuicaoPapeisEscritorioTest.java` (`novoController()` helper, `ResolucaoPapeisService` constructed for real over mocked repositories)
**Apply to:** all new backend tests touching `AdminController`'s RBAC CRUD.

### Real `@PreAuthorize` proxy (`ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor`), never annotation-reflection (backend)
**Source:** `PlatformAdminControllerTest.java`, `AdminControllerPlataformaAdminContencaoTest.java` (both cited in full in `125-PATTERNS.md`)
**Apply to:** every new gate-proving test (hasAuthority vs hasRole, tenant-ADMIN-denied cases).

### TanStack Query hook shape + `apiFetch`, never `setQueryData` (frontend)
**Source:** `web/src/hooks/use-platform-moldes.ts` (full file)
**Apply to:** new mutations in `web/src/hooks/use-admin.ts`.

### Unsaved-edit merge against a fresh query payload (frontend)
**Source:** `web/src/app/(dashboard)/plataforma/moldes/merge-local-permissoes.ts` (full file, `mesclarEstadoLocal`)
**Apply to:** the rewritten `RbacTab`'s local-edit state, if the new screen keeps a "matrix + separate Save" model rather than per-cell auto-save.

### Zod reserved-name refine + uppercase-transform (frontend)
**Source:** `web/src/schemas/moldes.ts` (full file)
**Apply to:** the new create/rename role schema — same `PLATAFORMA_ADMIN` reserved-name block.

## No Analog Found

| File/Concern | Role | Data Flow | Reason |
|---|---|---|---|
| Rename-role UI affordance | component | request-response | No existing screen in this codebase lets a user rename an already-persisted named entity inline (moldes/page.tsx only creates and edits permissions, never renames; tenant admin console only toggles `ativo`/`plano`, never renames a tenant). The closest partial precedent is the inline-edit pattern already used for profile fields (`UserProfileForm`) — a controlled `Input` bound to local state, submitted via a dedicated mutation — but that is a full-page form, not an in-matrix rename. Composed guidance: build on `CriarMoldePanel`'s form structure but bind it to an existing role's id for edit-mode, rather than inventing a new interaction pattern. |
| `UserPrincipal` gaining a provenance field | model | n/a | No existing field on `UserPrincipal` carries anything beyond flat name/permission strings; `ResolucaoPapeisService.temPapelDeMolde`'s `Integer moldeId`-based check is the closest provenance concept in the codebase but operates on `User`+`TenantRole`, never on the principal itself. Composed guidance given in section 1/2 above, not a literal copy target. |
| `createUser`/`updateUser`'s "roles" field resolving against a POSSIBLY-RENAMED `TenantRole` | controller | request-response | **Flagged as a likely-needed but not explicitly scoped concern**: `resolverTenantRolesOuErro` (`AdminController.java:163-178`) resolves user-role assignment by first looking up a **global** `Role` by name (`roleRepository.findByNome(roleName)`), then finding the tenant's homonymous `TenantRole`. Once Phase 127 allows renaming a `TenantRole`, this global-Role-by-name lookup breaks for any renamed role — `roleRepository.findByNome("Advogado Sénior")` (a name that only exists as a tenant's own renamed `TenantRole`, never as a global molde) returns empty, and `createUser`/`updateUser`'s "atribuir papéis a utilizadores" (in-scope per the phase boundary) would fail for exactly the roles this phase makes user-facing. No existing code resolves user-role assignment directly against `TenantRoleRepository.findByTenantIdAndNome` without the global-Role detour. This is not a pattern gap so much as an architecture question for the plan to resolve explicitly — worth a dedicated task, not a silent carry-forward of `resolverTenantRolesOuErro` unchanged. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{config,controllers,services,repositories,dtos,models}`, `backend/src/test/java/com/lexcv/{config,controllers,services}`, `web/src/app/(dashboard)/{settings,plataforma/moldes}`, `web/src/hooks`, `web/src/types`, `web/src/schemas`, `web/scripts`
**Files scanned:** 22 read directly (full or targeted): `UserPrincipal.java`, `JwtAuthenticationFilter.java`, `ResolucaoPapeisService.java`, `VerificacaoDerivaPapeisService.java`, `TenantRoleRepository.java`, `UserRepository.java`, `User.java` (targeted), `ParecerController.java` (targeted, 2 ranges), `ResourceController.java` (targeted, 2 ranges), `AdminController.java` (full via 3 targeted reads), `AuthController.java` (targeted), `RbacResponse.java`, `AdminControllerAtribuicaoPapeisEscritorioTest.java` (targeted), `settings/page.tsx` (targeted, RbacTab block + imports), `use-admin.ts`, `plataforma/moldes/page.tsx`, `merge-local-permissoes.ts`, `criar-molde-panel.tsx`, `verify-bloqueio-rbac.mjs`, `verify-consola-moldes.mjs` (grepped), `types/platform-moldes.ts`, `hooks/use-platform-moldes.ts`, `schemas/moldes.ts`. Plus full reuse of `125-PATTERNS.md` and `126-PATTERNS.md` for already-documented shared conventions (ProxyFactory proxy test technique, migration script/README discipline, `@ManyToMany`/`@JoinTable` idiom) not re-derived here.
**Pattern extraction date:** 2026-09-21
