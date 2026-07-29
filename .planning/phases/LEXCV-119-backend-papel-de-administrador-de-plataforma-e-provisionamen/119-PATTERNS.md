# Phase 119: Backend — Papel de Administrador de Plataforma e Provisionamento - Pattern Map

**Mapped:** 2026-07-29
**Files analyzed:** 5 (3 explicit from CONTEXT.md + 2 implied by its own decisions)
**Analogs found:** 5 / 5

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/services/SetupService.java` (add `provisionTenant`) | service | CRUD | `SetupService.initializeSystem` (same file, lines 43-86) | exact (same class) |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (add `PLATAFORMA_ADMIN` role + reserved tenant + bootstrap user) | config (CommandLineRunner seed component) | batch (idempotent upsert at startup) | `DatabaseSeeder.seedRbac()`/`upsertRolePermissions` (lines 293-360) + demo Tenant/User block (lines 63-93), same file | exact (same class, combined patterns) |
| `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` (new) | controller | request-response | `SetupController.java` (structure) + `AdminController.java` (auth header) | exact / role-match |
| `backend/src/main/java/com/lexcv/repositories/TenantRepository.java` (add `findByNome`) — **implied**, required by the CONTEXT.md idempotency decision but not named explicitly in its integration-points list | model (repository interface) | CRUD (read) | `RoleRepository.java` (lines 1-9) | exact |
| New response DTO, e.g. `backend/src/main/java/com/lexcv/dtos/TenantProvisionResponse.java` — **implied** by "Claude's Discretion: exact response DTO shape" | model (DTO) | transform | `TenantPublicInfoResponse.java` + `SetupInitializeResponse.java` | exact |

**Optional (Claude's Discretion, not required this phase):** `GET /api/v1/platform/tenants` list endpoint, if added to `PlatformAdminController.java` — analog `AdminController.listUsers` (lines 38-67).

## Pattern Assignments

### `backend/src/main/java/com/lexcv/services/SetupService.java` (service, CRUD) — add `provisionTenant`

**Analog:** itself — `initializeSystem`, same class. This is the strongest possible analog: same file, same collaborators already injected, same domain operation ("create Tenant + create initial User with a role").

**Imports pattern** (lines 1-19 — no new imports needed; all types `provisionTenant` needs — `Tenant`, `User`, `Role`, the two repositories, `PasswordEncoder` — are already imported):
```java
package com.lexcv.services;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.models.Role;
import com.lexcv.models.SystemSetting;
import com.lexcv.models.Tenant;
import com.lexcv.models.User;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.SystemSettingRepository;
import com.lexcv.repositories.TenantRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
```

**Core CRUD pattern to copy, minus the `SystemSetting` singleton block** (lines 43-86 — `provisionTenant` should look nearly identical, dropping the highlighted parts):
```java
@Transactional
public void initializeSystem(SetupInitializeRequest request) {
    validateRequest(request);

    SystemSetting settings = systemSettingRepository.findByIdForUpdate(SystemSetting.SINGLETON_ID)  // DROP for provisionTenant
            .orElseGet(() -> systemSettingRepository.saveAndFlush(                                    // DROP
                    SystemSetting.builder()                                                            // DROP
                            .id(SystemSetting.SINGLETON_ID)                                             // DROP
                            .initialized(false)                                                         // DROP
                            .build()                                                                     // DROP
            ));                                                                                          // DROP

    if (Boolean.TRUE.equals(settings.getInitialized())) {                                               // DROP
        throw new IllegalStateException("O sistema já foi inicializado.");                               // DROP
    }                                                                                                     // DROP

    if (userRepository.findByEmail(request.getAdminEmail().trim().toLowerCase()).isPresent()) {
        throw new IllegalArgumentException("Já existe um utilizador com este email.");
    }

    Role adminRole = roleRepository.findByNome("ADMIN")
            .orElseThrow(() -> new IllegalStateException("O papel ADMIN não está configurado."));

    Tenant tenant = Tenant.builder()
            .nome(request.getClientName().trim())
            .email(request.getAdminEmail().trim().toLowerCase())
            .logoDataUrl(normalizeLogo(request.getLogo()))
            .build();
    tenant = tenantRepository.save(tenant);

    User adminUser = User.builder()
            .tenantId(tenant.getId())
            .nome("Administrador")
            .email(request.getAdminEmail().trim().toLowerCase())
            .passwordHash(passwordEncoder.encode(request.getAdminPassword()))
            .ativo(true)
            .roles(Set.of(adminRole))
            .build();
    userRepository.save(adminUser);

    settings.setInitialized(true);        // DROP for provisionTenant
    settings.setInitializedAt(LocalDateTime.now());  // DROP
    systemSettingRepository.save(settings);           // DROP
}
```
Per CONTEXT.md, `provisionTenant` looks up role `"ADMIN"` (not `"PLATAFORMA_ADMIN"`) — a newly provisioned tenant's first user is that tenant's own regular admin, exactly like this method does today.

**Validation pattern — reuse as-is, do not duplicate** (lines 88-113, already `private` and in-class, zero visibility change needed):
```java
private void validateRequest(SetupInitializeRequest request) {
    if (request == null) {
        throw new IllegalArgumentException("Payload de inicialização em falta.");
    }
    if (isBlank(request.getClientName())) {
        throw new IllegalArgumentException("O nome da empresa/cliente é obrigatório.");
    }
    if (isBlank(request.getAdminEmail()) || !EMAIL_PATTERN.matcher(request.getAdminEmail().trim()).matches()) {
        throw new IllegalArgumentException("O email do administrador é inválido.");
    }
    if (isBlank(request.getAdminPassword()) ||
            !STRONG_PASSWORD_PATTERN.matcher(request.getAdminPassword()).matches()) {
        throw new IllegalArgumentException(
                "A password deve ter no mínimo 8 caracteres, com maiúscula, minúscula, número e caractere especial."
        );
    }
    if (!isBlank(request.getLogo())) {
        String normalizedLogo = request.getLogo().trim();
        if (normalizedLogo.length() > MAX_LOGO_LENGTH) {
            throw new IllegalArgumentException("O logo excede o tamanho máximo permitido.");
        }
        if (!DATA_URL_IMAGE_PATTERN.matcher(normalizedLogo).matches()) {
            throw new IllegalArgumentException("O logo deve ser enviado como imagem base64 válida.");
        }
    }
}
```

**Error handling pattern:** plain thrown `IllegalArgumentException` (validation/business-rule failures) and `IllegalStateException` (missing `ADMIN` role) — no try/catch inside the service; the controller layer maps these to HTTP status (see `SetupController` below).

**IMPORTANT deviation to flag for the planner:** `initializeSystem` returns `void`. CONTEXT.md's endpoint requirement is `POST /api/v1/platform/tenants` returning "the created tenant (id + nome at minimum)". `provisionTenant` therefore **cannot** mirror `void` — it must return the saved `Tenant` (or equivalent id/nome data) so `PlatformAdminController` has something to build its response DTO from. This is the one place a literal copy of `initializeSystem`'s signature would break the required behavior.

---

### `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (config/seed, batch) — add `PLATAFORMA_ADMIN` + reserved tenant + bootstrap user

**Analog:** itself — two different existing blocks in the same file cover the two things this change needs.

**Part A — role seeding, analog `seedRbac()` / `upsertRolePermissions`** (lines 293-360, call site + helper):
```java
private void seedRbac() {
    List<String> permKeys = Arrays.asList(
                    "clientes:view", "clientes:edit",
                    ...
                    "notificacoes:view");

    Map<String, Permission> permissionMap = new HashMap<>();
    for (String key : permKeys) {
            Permission perm = permissionRepository.findByNome(key)
                            .orElseGet(() -> permissionRepository
                                            .save(Permission.builder().nome(key).build()));
            permissionMap.put(key, perm);
    }

    upsertRolePermissions("ADMIN", permissionMap.values());

    upsertRolePermissions("ASSISTENTE", Arrays.asList( ... ));
    upsertRolePermissions("TECNICO", Arrays.asList( ... ));
    upsertRolePermissions("ADVOGADO", Arrays.asList( ... ));
}

private void upsertRolePermissions(String roleName, Collection<Permission> permissions) {
    Role role = roleRepository.findByNome(roleName)
                    .orElseGet(() -> roleRepository.save(Role.builder().nome(roleName).build()));

    boolean changed = role.getPermissions().addAll(permissions);
    if (changed) {
            roleRepository.save(role);
    }
}
```
Add one line inside `seedRbac()`, after the existing four calls: `upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList());` — the file already has `import java.util.*;` at line 14, so `Collections` is already in scope, no new import required.

**Part B — reserved tenant + bootstrap user shape, analog the demo-data Tenant/User block** (lines 58-93 — note the surrounding gating at lines 39-56 is explicitly what must NOT be copied, see Shared Patterns below):
```java
System.out.println("🌱 Seeding LexCV database...");

Role adminRole = roleRepository.findByNome("ADMIN").orElseThrow();
Role assistenteRole = roleRepository.findByNome("ASSISTENTE").orElseThrow();

// 3. Tenants
Tenant tenant = Tenant.builder()
                .nome("Gabinete Jurídico Demonstração")
                .nif("000000000")
                .tipoEntidade("PRIVADO")
                .email("contacto@lexcv.cv")
                .telefone("+238 200 0000")
                .build();
tenant = tenantRepository.save(tenant);
UUID tenantId = tenant.getId();

// 4. Users
User adminUser = User.builder()
                .tenantId(tenantId)
                .nome("Administrador (PostgreSQL Real)")
                .email("admin@lexcv.cv")
                .passwordHash(passwordEncoder.encode("Pa$$w0rd"))
                .ativo(true)
                .roles(Set.of(adminRole))
                .build();
userRepository.save(adminUser);
```
For the new block: `Tenant.builder().nome("LexCV").build()` (no other fields required by CONTEXT.md), find-or-create by `nome` first via the new `TenantRepository.findByNome` (see below) before calling `.save(...)` — mirrors the `orElseGet(() -> repo.save(...))` idiom used by `upsertRolePermissions` above, not the unconditional `.save()` the demo block uses (the demo block never needs idempotency because it's already guarded by the `tenantRepository.count() > 0` early-return at line 54). Then `User.builder()...email("plataforma@lexcv.cv")...passwordHash(passwordEncoder.encode("Pa$$w0rd"))...roles(Set.of(plataformaAdminRole))...build()`, saved via `userRepository.save(...)`. Guard the user-creation the same find-or-create way (e.g. `userRepository.findByEmail(...)`, already used elsewhere in this codebase — see `SetupService` line 59) so a restart never tries to insert a duplicate email and hit the DB unique constraint.

**Placement pattern — where the new unconditional block goes** (lines 39-56, `run()` method):
```java
@Override
public void run(String... args) throws Exception {
        seedRbac();

        if (!seedEnabled) {
                return;
        }

        boolean initialized = systemSettingRepository.findById(SystemSetting.SINGLETON_ID)
                        .map(SystemSetting::getInitialized)
                        .orElse(false);
        if (!initialized) {
                return;
        }

        if (tenantRepository.count() > 0 || userRepository.count() > 0 || clienteRepository.count() > 0) {
                return;
        }
        // demo-data block starts here — gated by seedEnabled + initialized + zero-existing-data
```
The new reserved-tenant/bootstrap-user block must sit **between** `seedRbac();` (line 41) and the `if (!seedEnabled)` gate (line 43) — i.e. it runs unconditionally on every startup, exactly like `seedRbac()` itself, and specifically **before** any of the three gates that protect the demo-data block. Do not place it inside or after those gates.

> **⚠ PARCIALMENTE SUPERSEDED (revisao de planeamento, 2026-07-29) — ver `119-01-PLAN.md`.**
> Este "Placement pattern" continua correto para a **tenant reservada** `"LexCV"`, que fica mesmo
> antes do gate `if (!seedEnabled)`. Deixou de valer para o **utilizador bootstrap**
> `plataforma@lexcv.cv`, que passa a ser criado **logo a seguir** a esse gate (e antes do calculo de
> `initialized`), ou seja gated por `app.seed.enabled` e so por ele. Estrutura alvo:
>
> ```java
> seedRbac();
> boolean bdVaziaAntesDoSeedPlataforma = ...;      // as tres contagens, ANTES de qualquer insert
> Tenant tenantPlataforma = seedTenantPlataforma(); // INCONDICIONAL
> if (!seedEnabled) { return; }                     // producao: sem credencial de plataforma
> seedUtilizadorPlataforma(tenantPlataforma);       // gated SO por seedEnabled
> boolean initialized = ...;
> if (!initialized) { return; }
> if (!bdVaziaAntesDoSeedPlataforma) { return; }
> // ... bloco de dados demo, inalterado
> ```

---

### `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` (new controller, request-response)

**Primary structural analog:** `SetupController.java` (full file, 53 lines) — same shape needed: thin controller, one collaborator (`SetupService`), delegates validation/business logic entirely to the service, catches the service's declared exceptions and maps them to HTTP status.

**Auth header analog:** `AdminController.java` lines 26-30 — class-level `@PreAuthorize`, swap the role name.

**Imports + class header pattern** (`SetupController.java` lines 1-22, adapted):
```java
package com.lexcv.controllers;

import com.lexcv.dtos.SetupInitializeRequest;
import com.lexcv.services.SetupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")   // AdminController.java line 28, role swapped
@RequiredArgsConstructor                        // AdminController.java line 29 / SetupController.java line 20
public class PlatformAdminController {
    private final SetupService setupService;
```
(`AdminController.java` lines 26-30 for direct comparison — the exact class-level pattern being replicated):
```java
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
```

**Core request-response + error-mapping pattern** (`SetupController.initialize`, lines 33-52 — the exact template for `POST /api/v1/platform/tenants`):
```java
@PostMapping("/initialize")
public ResponseEntity<?> initialize(@RequestBody SetupInitializeRequest request) {
    if (setupService.isInitialized()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "O sistema já foi inicializado."));
    }

    try {
        setupService.initializeSystem(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(SetupInitializeResponse.builder()
                        .initialized(true)
                        .message("Ambiente configurado com sucesso.")
                        .build());
    } catch (IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    } catch (IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    }
}
```
For `PlatformAdminController`, drop the `isInitialized()` pre-check (that's the setup singleton gate this phase must NOT touch), call `setupService.provisionTenant(request)`, and build the 201 response from its return value instead of a fixed `SetupInitializeResponse`. Keep the same `catch (IllegalArgumentException ...) → 400` / `catch (IllegalStateException ...) → 403` shape since `provisionTenant` throws the same two exception types as `initializeSystem` (via the shared `validateRequest` and the `ADMIN` role lookup).

**201-response / no-raw-entity pattern — analog `AdminController.createUser`** (lines 151-176, explicitly the shape CONTEXT.md says to mirror):
```java
User user = User.builder()
        .tenantId(principal.getTenantId())
        .nome((String) body.get("nome"))
        .email(email)
        .passwordHash(passwordEncoder.encode((String) body.get("password")))
        .roles(roles)
        .permissions(permissions)
        .ativo(ativoInicial)
        .telefone(body.containsKey("telefone") ? (String) body.get("telefone") : "")
        .avatarUrl(body.containsKey("avatar_url") ? (String) body.get("avatar_url") : "")
        .build();

user = userRepository.save(user);

UserResponse response = UserResponse.builder()
        .id(user.getId())
        .tenant_id(user.getTenantId())
        .nome(user.getNome())
        .email(user.getEmail())
        .roles(user.getRoles().stream().map(Role::getNome).collect(Collectors.toSet()))
        .permissions(permissions)
        .ativo(user.getAtivo())
        .build();

return ResponseEntity.status(HttpStatus.CREATED).body(response);
```
`PlatformAdminController`'s handler should build a purpose-built response object (see the new DTO pattern assignment below) from the `Tenant` returned by `provisionTenant`, then `return ResponseEntity.status(HttpStatus.CREATED).body(response);` — never `return ResponseEntity.status(HttpStatus.CREATED).body(tenant);`.

---

### `backend/src/main/java/com/lexcv/repositories/TenantRepository.java` (model/repository, CRUD-read) — add `findByNome`

**Analog:** `RoleRepository.java` (full file, exact match — a one-method Spring Data interface doing exactly this lookup shape):
```java
package com.lexcv.repositories;

import com.lexcv.models.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByNome(String nome);
}
```
Existing `TenantRepository.java` (full file, 21 lines) for context — add `Optional<Tenant> findByNome(String nome);` alongside the existing derived-query method:
```java
public interface TenantRepository extends JpaRepository<Tenant, UUID> {
    Optional<Tenant> findFirstByOrderByCreatedAtAsc();
    // add: Optional<Tenant> findByNome(String nome);
}
```

---

### New response DTO, e.g. `backend/src/main/java/com/lexcv/dtos/TenantProvisionResponse.java` (model/DTO, transform)

**Analog A** — `TenantPublicInfoResponse.java` (full file, data-carrying DTO built with explicit getter→setter copy, exact field-shape precedent for "expose Tenant fields, never the entity"):
```java
package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantPublicInfoResponse {
    private String nome;
    private String logoDataUrl;
}
```

**Analog B** — `SetupInitializeResponse.java` (full file, immutable message-style DTO precedent, if a `message` field alongside id/nome is wanted):
```java
package com.lexcv.dtos;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SetupInitializeResponse {
    private final boolean initialized;
    private final String message;
}
```
CONTEXT.md: "returns the created tenant (id + nome at minimum) — mirror whatever shape `AdminController.createUser` uses for its 201 response, i.e. a purpose-built response object, never the raw `Tenant`/`User` entities serialized directly." A `@Data @Builder @NoArgsConstructor @AllArgsConstructor` class with `id` (`UUID`) + `nome` (`String`) fields, built via `TenantProvisionResponse.builder().id(tenant.getId()).nome(tenant.getNome()).build()` in the controller, matches both this instruction and the codebase's dominant DTO style (`UserResponse`, `TenantPublicInfoResponse` both use this exact four-annotation combo).

---

## Shared Patterns

### Authentication / Authorization — class-level role gate, zero SecurityConfig changes needed
**Source:** `backend/src/main/java/com/lexcv/controllers/AdminController.java` lines 26-30; role→authority mapping in `backend/src/main/java/com/lexcv/config/UserPrincipal.java` lines 27-47; request-matcher allowlist in `backend/src/main/java/com/lexcv/config/SecurityConfig.java` lines 51-70.
**Apply to:** `PlatformAdminController.java`.
```java
// AdminController.java:26-30
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {
```
```java
// UserPrincipal.java:27-32 — role → GrantedAuthority mapping is fully generic (driven off DB Role.nome),
// no hardcoded role allowlist to extend for PLATAFORMA_ADMIN:
Set<SimpleGrantedAuthority> authorities = roles.stream()
        .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
        .collect(Collectors.toSet());

if (roles.contains("ADMIN")) {
    // Keep in sync with DatabaseSeeder.seedRbac()'s permKeys list.
    permissions.addAll(...);   // PLATAFORMA_ADMIN intentionally has no equivalent branch — it gets zero permissions
}
```
`hasRole('PLATAFORMA_ADMIN')` will work the moment the role exists in the DB and a user has it — Spring Security's `hasRole('X')` checks for authority `ROLE_X`, and `UserPrincipal.create` derives that generically from whatever role names are attached to the user, with no enum/allowlist to touch. `SecurityConfig.java`'s `authorizeHttpRequests` allowlist (lines 52-68) only lists **public** paths (`/auth/login`, `/setup/status`, `/setup/initialize`, `/public/branding`); `/api/v1/platform/**` is not public, so it already falls under `.anyRequest().authenticated()` (line 69) and needs no new entry there — the `@PreAuthorize` on the controller is the only new authorization gate required.

### Error handling — thrown exceptions from service, explicit mapping in controller
**Source:** `backend/src/main/java/com/lexcv/controllers/SetupController.java` lines 40-51; fallback `backend/src/main/java/com/lexcv/config/GlobalExceptionHandler.java` (full file).
**Apply to:** `SetupService.provisionTenant` (throws) and `PlatformAdminController` (catches).
```java
try {
    setupService.initializeSystem(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(...);
} catch (IllegalArgumentException ex) {
    return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
} catch (IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
}
```
Any exception not explicitly caught falls through to `GlobalExceptionHandler.handleAllExceptions` (`@ExceptionHandler(Exception.class)`), which logs and returns a generic 500 body `{"error": ..., "message": ...}` — controllers in this codebase never need a catch-all themselves.

### DTO / no-raw-entity discipline
**Source:** `backend/src/main/java/com/lexcv/dtos/UserResponse.java`, `TenantPublicInfoResponse.java`, `SetupInitializeResponse.java`; enforced explicitly in `backend/src/main/java/com/lexcv/controllers/PublicController.java` (docstring, lines 18-22).
**Apply to:** the new tenant-provisioning response DTO and `PlatformAdminController`'s handler.
```java
// PublicController.java:18-22
/**
 * ... Devolve exclusivamente nome+logoDataUrl da tenant singleton, via
 * cópia explícita getter-para-setter (TenantPublicInfoResponse) — nunca a entidade Tenant.
 */
```
Every response-returning controller in this codebase copies entity fields into a `@Builder` DTO before returning; none serialize `Tenant`/`User`/`Role` directly (the one partial exception, `AdminController.updateUser` returning the raw `User` at line 252, is pre-existing and not a pattern to copy).

### Idempotent find-or-create seeding
**Source:** `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` lines 352-360 (`upsertRolePermissions`) and lines 306-311 (`permissionMap` loop) — both use the same `repository.findByX(...).orElseGet(() -> repository.save(...))` idiom.
**Apply to:** the reserved `"LexCV"` tenant lookup/creation and the `PLATAFORMA_ADMIN` role.
```java
Role role = roleRepository.findByNome(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().nome(roleName).build()));
```
Use the identical shape for the tenant: `Tenant tenant = tenantRepository.findByNome("LexCV").orElseGet(() -> tenantRepository.save(Tenant.builder().nome("LexCV").build()));`, and for the bootstrap user, guard with `userRepository.findByEmail("plataforma@lexcv.cv")` before saving.

### Testing convention (bonus — no test file is required by CONTEXT.md's file list, but this codebase adds a test per phase touching `AdminController`-style role-gated logic)
**Source:** `backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java` lines 35-90 (docstring + setup).
**Apply to:** any test added for `PlatformAdminController` or `SetupService.provisionTenant`.
```java
/**
 * ... Segue a mesma convenção de todos os testes de controller deste codebase (ver
 * {@code ResourceControllerUploadDocumentoTest}): não existe harness MockMvc/{@code @SpringBootTest}
 * neste projeto — o controller é instanciado diretamente com colaboradores mockados via Mockito,
 * o método sob teste é invocado como uma chamada Java simples, e o {@code SecurityContextHolder}
 * é povoado manualmente com um {@link UserPrincipal} do tenant do caso e limpo em
 * {@code @AfterEach}.
 */
@ExtendWith(MockitoExtension.class)
class AdminControllerLimiteUtilizadoresTest {
    @Mock private UserRepository userRepository;
    ...
    @AfterEach
    void limparSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
```
No `MockMvc`/`@SpringBootTest` in this codebase's controller tests — instantiate the controller directly with `@Mock` collaborators (Mockito), call the handler method as a plain Java call, and manually populate/clear `SecurityContextHolder` if the handler reads it (note: per CONTEXT.md, `PlatformAdminController`'s handler likely does **not** need to read `SecurityContextHolder`/`UserPrincipal` at all — unlike tenant-scoped `AdminController` endpoints, tenant scoping is exactly what this endpoint does NOT do; it provisions a *new* tenant rather than acting within the caller's own).

## No Analog Found

None — every file in scope has a strong same-class or same-role analog already in the codebase. This phase is explicitly additive/precedented (CONTEXT.md frames both the service method and the controller as near-duplicates of existing operations), so there is no genuinely novel code shape here. The one nuance worth flagging (not a missing analog, a missing *combination*): no existing code seeds data that is unconditional-but-not-`seedEnabled`-gated **and** not part of `seedRbac()`'s pure-RBAC rows — the reserved tenant + bootstrap user is a new category ("core infrastructure data" vs. "RBAC rows" vs. "gated demo fixtures"). The pattern is still directly assembled from two existing blocks (see `DatabaseSeeder.java` pattern assignment above); it just doesn't pre-exist as a single copy-pasteable block.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{services,seed,controllers,repositories,models,dtos,config}/`, `backend/src/test/java/com/lexcv/controllers/`
**Files scanned:** `SetupService.java`, `AdminController.java`, `SetupController.java`, `DatabaseSeeder.java`, `PublicController.java`, `SetupInitializeRequest.java`, `SetupInitializeResponse.java`, `TenantPublicInfoResponse.java`, `UserResponse.java`, `Tenant.java`, `Role.java`, `User.java`, `TenantRepository.java`, `RoleRepository.java`, `TenantPlano.java`, `UserPrincipal.java`, `SecurityConfig.java`, `GlobalExceptionHandler.java`, `AdminControllerLimiteUtilizadoresTest.java` (partial)
**Pattern extraction date:** 2026-07-29
