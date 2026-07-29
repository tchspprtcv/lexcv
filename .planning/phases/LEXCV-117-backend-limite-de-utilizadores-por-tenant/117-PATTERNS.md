# Phase 117: Backend — Limite de Utilizadores por Tenant - Pattern Map

**Mapped:** 2026-07-28
**Files analyzed:** 5 (2 new, 3 modified)
**Analogs found:** 5 / 5

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/src/main/java/com/lexcv/models/Tenant.java` (modify) | model | CRUD | `backend/src/main/java/com/lexcv/models/Cliente.java` (enum field + nullable Integer field) | role-match |
| `backend/src/main/java/com/lexcv/models/TenantPlano.java` (new) | model (enum) | n/a — value/constant type | `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` | exact |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` (modify `createUser`) | controller | CRUD (create) | self (`AdminController.java`) for structure; `backend/src/main/java/com/lexcv/controllers/ResourceController.java` for the pre-save 409 check pattern; `backend/src/main/java/com/lexcv/controllers/AuthController.java` for `TenantRepository` injection + lookup | exact (local conventions) / role-match (borrowed 409 pattern) |
| `backend/src/main/java/com/lexcv/repositories/UserRepository.java` (modify — add count method) | repository | CRUD | self (`UserRepository.java`, existing `findByTenantIdAndRoleNameAndAtivoTrue`) | exact |
| `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` (new) | migration | batch (one-off schema + data backfill) | `backend/migrations/96-add-notificacao-snoozed-until.sql` (ALTER TABLE ADD COLUMN + header) and `backend/migrations/74-cleanup-nif-documento-tipo.sql` (UPDATE backfill) | exact |

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/Tenant.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/models/Cliente.java`

Tenant.java's own existing fields already establish the column-naming/builder conventions to keep following (snake_case `@Column(name=...)`, camelCase Java field). What Tenant.java does **not** yet have is a persisted enum field or a nullable `Integer` field — both exist in `Cliente.java` and are the exact shape needed for `plano` / `limiteUtilizadores`.

**Current full file** (`backend/src/main/java/com/lexcv/models/Tenant.java:1-42`) — note line 3 already does `import jakarta.persistence.*;`, so `@Enumerated`/`EnumType` need **no new import**:
\`\`\`java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "t_tenant")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nome;

    private String nif;

    @Column(name = "tipo_entidade")
    private String tipoEntidade;

    private String email;
    private String telefone;

    @Lob
    @Column(name = "logo_data_url")
    private String logoDataUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
\`\`\`

**Persisted-enum field pattern to copy** (`backend/src/main/java/com/lexcv/models/Cliente.java:46-51`):
\`\`\`java
    private Boolean ativo;

    @Enumerated(EnumType.STRING)
    @Column(name = "documento_tipo")
    private DocumentoTipo documentoTipo;
\`\`\`
→ For Tenant: `@Enumerated(EnumType.STRING)` + `@Column(name = "plano")` + `private TenantPlano plano;`

**Nullable Integer field pattern to copy** (`backend/src/main/java/com/lexcv/models/Cliente.java:61-62`):
\`\`\`java
    @Column(name = "numero_sequencial")
    private Integer numeroSequencial;
\`\`\`
→ For Tenant: `@Column(name = "limite_utilizadores")` + `private Integer limiteUtilizadores;` (no `nullable = false` — absence of the annotation attribute is exactly how this codebase expresses "nullable", consistent with `tipoEntidade`/`nif`/`email`/`telefone` already in this same file).

Both new fields insert cleanly between `logoDataUrl` and `createdAt`, or after `telefone` — either placement follows the file's existing flat field-list style (no grouping/section comments currently exist in this file to disrupt).

---

### `backend/src/main/java/com/lexcv/models/TenantPlano.java` (model/enum, n/a)

**Analog:** `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` (exact match — simple enum, no methods, no annotations)

**Full analog file** (`backend/src/main/java/com/lexcv/models/DocumentoTipo.java:1-8`):
\`\`\`java
package com.lexcv.models;

public enum DocumentoTipo {
    BI,
    CNI,
    PASSAPORTE,
    REG_COMERCIAL
}
\`\`\`

→ New file, same shape:
\`\`\`java
package com.lexcv.models;

public enum TenantPlano {
    STARTER,
    STANDARD,
    ENTERPRISE
}
\`\`\`

---

### `backend/src/main/java/com/lexcv/controllers/AdminController.java` (controller, CRUD — `createUser`)

**Analog (local structure):** self — the method already being modified. **Analog (borrowed sub-pattern):** `ResourceController.java` for the pre-save 409 check; `AuthController.java` for the `TenantRepository` dependency + lookup-by-id idiom.

**Imports** (`backend/src/main/java/com/lexcv/controllers/AdminController.java:1-22`) — `HttpStatus` and `Map` are already imported, so the new 409 response needs **no new imports**. Only a new field/constructor dependency (`TenantRepository`) is needed:
\`\`\`java
package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.dtos.RbacResponse;
import com.lexcv.dtos.UserResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.models.User;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
import com.lexcv.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
\`\`\`
→ add `import com.lexcv.repositories.TenantRepository;` and `import com.lexcv.models.Tenant;` (only if the tenant object is bound to a local variable typed explicitly — optional with `var`).

**Class fields / DI** (`backend/src/main/java/com/lexcv/controllers/AdminController.java:24-33`) — class-level `@PreAuthorize("hasRole('ADMIN')")` already covers this endpoint; **no authorization change needed**, only a new repository dependency, following exactly how `AuthController` added `TenantRepository`:
\`\`\`java
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
\`\`\`
Compare to how `AuthController` wires the same repository (`backend/src/main/java/com/lexcv/controllers/AuthController.java:33-36`):
\`\`\`java
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
\`\`\`
`@RequiredArgsConstructor` auto-wires the new `final` field — no explicit constructor edit needed in either controller.

**Tenant lookup-by-id idiom to copy** (`backend/src/main/java/com/lexcv/controllers/AuthController.java:169-171`):
\`\`\`java
        tenantRepository.findById(principal.getTenantId()).ifPresent(t -> {
            response.setTenant_nome(t.getNome());
            response.setTenant_logo_data_url(t.getLogoDataUrl());
\`\`\`
(`TenantRepository` needs no changes — `findById` is inherited from `JpaRepository<Tenant, UUID>`, confirmed in `backend/src/main/java/com/lexcv/repositories/TenantRepository.java:1-21`, matching CONTEXT.md's "Nenhuma mudança em TenantRepository".)

**Current `createUser` — full existing validation chain and error-response format to preserve** (`backend/src/main/java/com/lexcv/controllers/AdminController.java:66-127`):
\`\`\`java
    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        if (!body.containsKey("nome") || !body.containsKey("email") || !body.containsKey("password") || !body.containsKey("roles")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nome, email, password e roles são obrigatórios."));
        }

        String password = (String) body.get("password");
        if (!password.matches("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,}$")) {
            return ResponseEntity.badRequest().body(Map.of("message", "A password deve ter no mínimo 8 caracteres, uma maiúscula, uma minúscula, um número e um caractere especial."));
        }

        String email = (String) body.get("email");
        if (userRepository.findByEmail(email).isPresent()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Já existe um utilizador registado com este endereço de email."));
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

        List<?> rolesList = (List<?>) body.get("roles");
        Set<Role> roles = new HashSet<>();
        for (Object rObj : rolesList) {
            String roleName = (String) rObj;
            roleRepository.findByNome(roleName).ifPresent(roles::add);
        }

        if (roles.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Pelo menos uma role válida é obrigatória."));
        }

        List<?> permsList = body.containsKey("permissions") ? (List<?>) body.get("permissions") : Collections.emptyList();
        Set<String> permissions = new HashSet<>();
        for (Object pObj : permsList) {
            permissions.add((String) pObj);
        }

        User user = User.builder()
                .tenantId(principal.getTenantId())
                .nome((String) body.get("nome"))
                .email(email)
                .passwordHash(passwordEncoder.encode((String) body.get("password")))
                .roles(roles)
                .permissions(permissions)
                .ativo(body.get("ativo") == null || (Boolean) body.get("ativo"))
                .telefone(body.containsKey("telefone") ? (String) body.get("telefone") : "")
                .avatarUrl(body.containsKey("avatar_url") ? (String) body.get("avatar_url") : "")
                .build();

        user = userRepository.save(user);
        ...
\`\`\`
Per CONTEXT.md, the new limit check goes **after** the `roles.isEmpty()` check (last format validation, line 92-94) and **before** `User.builder()...save()` (line 102) — i.e., right after `principal` is already resolved (line 82-83, so no duplicate `Authentication`/`SecurityContextHolder` lookup is needed).

**Pre-save 409 CONFLICT pattern to copy** (`backend/src/main/java/com/lexcv/controllers/ResourceController.java:240-255`) — this is the closest existing precedent in the codebase for "business-rule check immediately before `.save()`, returning 409 with the same `Map.of("message", ...)` shape used everywhere else":
\`\`\`java
    @PostMapping("/clientes")
    public ResponseEntity<?> createCliente(@Valid @RequestBody Cliente cliente) {
        if (!isDocumentoTipoValidoParaTipo(cliente.getTipo(), cliente.getDocumentoTipo(), cliente.getDocumentoNumero())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("message", "Tipo de documento inválido para o tipo de cliente selecionado"));
        }
        // WR-03 (Phase 90 code review, iteration 2): validate the documento_numero unique
        // constraint explicitly, so the DataIntegrityViolationException catch below is reserved
        // for the numero_sequencial race it's actually meant to handle, instead of also catching
        // (and mislabeling as a "client number conflict") this permanent validation failure.
        if (cliente.getDocumentoNumero() != null
                && clienteRepository.findByTenantIdAndDocumentoNumero(getTenantId(), cliente.getDocumentoNumero()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", "Já existe um cliente com este número de documento"));
        }
\`\`\`
Other confirmed `HttpStatus.CONFLICT` + `Map.of("message", ...)` sites for the same shape: `ResourceController.java:508`, `570`, `1283`, `1433`, `1467`, `1472`, `1553`, `3068` — this is a codebase-wide idiom, not a one-off.

**Illustrative composition** (not prescriptive — shows how the two borrowed patterns above combine at the insertion point; exact variable names/method name are Claude's Discretion per CONTEXT.md):
\`\`\`java
        // after roles.isEmpty() check, before User.builder()...save()
        Tenant tenant = tenantRepository.findById(principal.getTenantId()).orElse(null);
        if (tenant != null && tenant.getLimiteUtilizadores() != null) {
            long ativos = userRepository.countByTenantIdAndAtivoTrue(principal.getTenantId());
            if (ativos >= tenant.getLimiteUtilizadores()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("message", "Limite de utilizadores atingido para o vosso plano."));
            }
        }
\`\`\`

---

### `backend/src/main/java/com/lexcv/repositories/UserRepository.java` (repository, CRUD)

**Analog:** self — existing derived-name + `@Query` methods in the same file already cover both styles needed.

**Full current file** (`backend/src/main/java/com/lexcv/repositories/UserRepository.java:1-32`):
\`\`\`java
package com.lexcv.repositories;

import com.lexcv.models.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    List<User> findByTenantId(UUID tenantId);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName")
    List<User> findByTenantIdAndRoleName(@Param("tenantId") UUID tenantId, @Param("roleName") String roleName);

    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome IN :roleNames")
    List<User> findByTenantIdAndRoleNameIn(@Param("tenantId") UUID tenantId, @Param("roleNames") List<String> roleNames);

    // WR-02 (Phase 94 code review): variant of findByTenantIdAndRoleName that excludes
    // deactivated accounts, used by NotificacaoService's ADMIN fan-out so a deactivated ADMIN
    // does not keep accumulating notification rows indefinitely (mirrors the `ativo` check
    // ResourceController.atribuirResponsavel already applies before assigning a responsible
    // party). Added as a separate method (not a change to findByTenantIdAndRoleName in place)
    // to avoid altering AlertasDiariosJob's existing ADMIN fan-out behavior, which was not part
    // of this review's scope.
    @Query("SELECT u FROM User u JOIN u.roles r WHERE u.tenantId = :tenantId AND r.nome = :roleName AND u.ativo = true")
    List<User> findByTenantIdAndRoleNameAndAtivoTrue(@Param("tenantId") UUID tenantId, @Param("roleName") String roleName);
}
\`\`\`

Two viable patterns for the new count method, both already established elsewhere in the codebase:

1. **Pure derived query (no `@Query`)** — matches `findByEmail`/`findByTenantId` above (lines 13-14), the simplest precedent, appropriate here since `tenantId = X AND ativo = true` is a plain two-predicate equality with no joins/extra logic:
\`\`\`java
    long countByTenantIdAndAtivoTrue(UUID tenantId);
\`\`\`
The `...AtivoTrue` suffix on a `Boolean` field is already proven in this exact file (`findByTenantIdAndRoleNameAndAtivoTrue`, line 30).

2. **Explicit JPQL `@Query` count** — matches `NotificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse` (`backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:48-53`), used there because extra predicate logic (snooze visibility) didn't fit a derived name:
\`\`\`java
    @Query("SELECT COUNT(n) FROM Notificacao n WHERE n.tenantId = :tenantId " +
            "AND n.destinatarioId = :destinatarioId AND n.lida = false " +
            "AND (n.snoozedUntil IS NULL OR n.snoozedUntil <= :agora)")
    long countByTenantIdAndDestinatarioIdAndLidaFalse(@Param("tenantId") UUID tenantId, ...);
\`\`\`
Given the simplicity of "active users per tenant" (no extra predicate), option 1 is the closer-fitting analog; option 2 is documented in case the eventual implementation needs to add logic beyond `ativo = true`. Method name and file placement (`UserRepository` vs. a dedicated service) are explicitly Claude's Discretion per CONTEXT.md — either is directly supported by precedent in this codebase. CONTEXT.md's Success Criteria 4 requires this be a single reusable method (for Phases 120/122 reuse), which either style satisfies since both are public repository methods.

---

### `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` (migration, batch)

**Analog 1 (ALTER TABLE ADD COLUMN + required-manual-migration header):** `backend/migrations/96-add-notificacao-snoozed-until.sql:1-26`
\`\`\`sql
-- Phase 96 (NOTF-26): add snoozed_until column to t_notificacao
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that adds the `snoozedUntil` field to the `Notificacao` entity
-- (backend/src/main/java/com/lexcv/models/Notificacao.java).
--
-- Why: `application-prod.yml` sets `ddl-auto: validate` in production (dev/CI use
-- `ddl-auto: update`, which auto-adds this column locally from the entity mapping).
-- `ddl-auto=validate` never creates or alters schema — it only checks the existing
-- schema is compatible at startup. Without this script, the application will fail to
-- start in production (schema validation error: missing column `snoozed_until` on
-- table `t_notificacao`).
--
-- There is no automated migration runner in this repository (no Flyway, no Liquibase --
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is
-- therefore manual: run it once against each environment's database (staging/prod)
-- before that environment picks up the deploy that introduces `snoozedUntil`.
--
-- Semantics: NULL = never snoozed (default, no explicit default clause needed).
-- [... trailing semantics note specific to that phase ...]

ALTER TABLE t_notificacao ADD COLUMN snoozed_until TIMESTAMP;
\`\`\`
Same header boilerplate (paragraphs 2-4 verbatim in style) is repeated identically in `backend/migrations/82-add-honorario-processo-unique-constraint.sql:1-19` and `backend/migrations/93-create-notificacao-preferencia-table.sql:1-18` — this is a fixed, copy-and-adapt template across every manual migration in this repo, not a one-off.

**Analog 2 (backfill UPDATE for existing rows):** `backend/migrations/74-cleanup-nif-documento-tipo.sql:1-27`
\`\`\`sql
-- Phase 74: Defensive cleanup for legacy NIF documento_tipo rows
--
-- IMPORTANT: This is a ONE-OFF DEFENSIVE cleanup script. It MUST be run manually
-- (e.g. via psql or DBeaver) against the database BEFORE deploying the code change
-- that removes the `NIF` constant from `DocumentoTipo.java`.
-- [...]

UPDATE t_cliente SET documento_tipo = NULL, documento_numero = NULL WHERE documento_tipo = 'NIF';
\`\`\`

**Composition for this migration** — CONTEXT.md requires both an `ADD COLUMN` (x2, for `plano` and `limite_utilizadores`) **and** a backfill `UPDATE` (existing tenant gets `plano='ENTERPRISE'`, `limite_utilizadores` stays `NULL` which is already the default absence-of-value for a freshly added nullable column — no explicit UPDATE needed for that second column). Illustrative shape combining Analog 1's header/ALTER style with Analog 2's UPDATE style:
\`\`\`sql
-- Phase 117: add plano/limite_utilizadores columns to t_tenant
--
-- IMPORTANT: This is a REQUIRED manual production migration script. [... same rationale
-- paragraph as migration 96, adapted for t_tenant / Tenant.java / plano+limiteUtilizadores ...]
--
-- Backfill: the existing single production tenant gets plano=ENTERPRISE (unlimited via
-- limite_utilizadores staying NULL) so this migration never locks out the current tenant.

ALTER TABLE t_tenant ADD COLUMN plano VARCHAR(255);
ALTER TABLE t_tenant ADD COLUMN limite_utilizadores INTEGER;

UPDATE t_tenant SET plano = 'ENTERPRISE' WHERE plano IS NULL;
\`\`\`
(Column type `VARCHAR(255)` for the `@Enumerated(EnumType.STRING)` field matches Hibernate's default DDL for string-mapped enums — same implicit type already in place for `t_cliente.documento_tipo`, which received no explicit type override in any migration.)

---

## Shared Patterns

### Error response format
**Source:** `backend/src/main/java/com/lexcv/controllers/AdminController.java` (throughout `createUser`/`updateUser`/`deleteUser`) and `ResourceController.java` (409 sites listed above)
**Apply to:** the new 409 in `AdminController.createUser`
\`\`\`java
return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "..."));
\`\`\`
Never a new error DTO/shape — `Map.of("message", String)` is the only error body shape used anywhere in this controller layer.

### Persisted enum convention
**Source:** `backend/src/main/java/com/lexcv/models/Cliente.java:48-50` (usage) + `backend/src/main/java/com/lexcv/models/DocumentoTipo.java` (enum definition)
**Apply to:** `Tenant.plano` / `TenantPlano`
\`\`\`java
@Enumerated(EnumType.STRING)
@Column(name = "plano")
private TenantPlano plano;
\`\`\`

### Repository dependency injection
**Source:** `backend/src/main/java/com/lexcv/controllers/AuthController.java:33-36`
**Apply to:** `AdminController` gaining `TenantRepository`
\`\`\`java
private final TenantRepository tenantRepository;
\`\`\`
(`@RequiredArgsConstructor` at class level already handles wiring — confirmed present in `AdminController.java:27`.)

### Business-rule inline comment convention
**Source:** `backend/src/main/java/com/lexcv/repositories/UserRepository.java:22-28` (`WR-02 (Phase 94 code review)`), `backend/src/main/java/com/lexcv/controllers/ResourceController.java:247-250` (`WR-03 (Phase 90 code review, iteration 2)`)
**Apply to:** the new limit-check block in `createUser` and the new count method — a short comment citing the phase/rationale ("Phase 117 — limite de utilizadores por tenant, ver proposta secção 5.2") is the established convention whenever a non-obvious business rule is added, not just a bare `if`.

### Manual migration header template
**Source:** `backend/migrations/96-add-notificacao-snoozed-until.sql`, `82-add-honorario-processo-unique-constraint.sql`, `93-create-notificacao-preferencia-table.sql` (near-identical boilerplate across all three)
**Apply to:** `117-add-tenant-plano-limite-utilizadores.sql`
Structure: title line → "IMPORTANT: REQUIRED manual production migration script" paragraph → "Why: `ddl-auto: validate` in prod..." paragraph → "no automated migration runner (no Flyway/Liquibase)..." paragraph → optional semantics/backfill note → the SQL statement(s).

## No Analog Found

None — all 5 files have at least a role-match analog; the two files with no *exact* single-file analog (`Tenant.java` modification, `AdminController.createUser`'s 409 logic) are each covered by combining two strong existing analogs from sibling files in the same layer.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/models/`, `backend/src/main/java/com/lexcv/controllers/`, `backend/src/main/java/com/lexcv/repositories/`, `backend/migrations/`
**Files read in full or targeted-range:** `Tenant.java`, `DocumentoTipo.java`, `Cliente.java` (targeted), `User.java`, `AdminController.java`, `ResourceController.java` (targeted, file is 3000+ lines), `AuthController.java` (targeted), `UserRepository.java`, `NotificacaoRepository.java` (targeted), `TenantRepository.java`, migrations `74`, `82`, `86` (targeted), `93`, `96`
**Candidate files globbed (not all read):** 45 files in `backend/src/main/java/com/lexcv/models/`, 9 files in `backend/migrations/`
**Pattern extraction date:** 2026-07-28
