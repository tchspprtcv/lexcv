# Phase 61: Data Layer + Backend CRUD - Pattern Map

**Mapped:** 2026-06-30
**Files analyzed:** 4 (new) + 2 (modified)
**Analogs found:** 6 / 6

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java` | model | CRUD | `backend/src/main/java/com/lexcv/models/Prazo.java` | exact (UUID id, tenantId, processoId optional FK, prioridade default, status-like field) |
| `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` | model (repository) | CRUD | `backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java` | exact (JpaRepository<Entity, UUID>, findByTenantId) |
| `backend/src/main/java/com/lexcv/controllers/ParecerController.java` | controller | request-response | `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (Processo CRUD section, lines 796-980) | role-match (new dedicated controller, but copies same `@RestController`/`@PreAuthorize`/tenant-scoping idioms) |
| `backend/src/main/java/com/lexcv/models/Permission.java` (no code change expected — just new rows via seeder) | config | batch | N/A | n/a — seeded at runtime |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (modify `seedRbac()`) | config | batch | itself, `seedRbac()` lines 293-343 | exact (extend existing method) |
| `web/src/lib/permissions.ts` (mirror scopes — explicitly out of scope for this backend phase per CONTEXT.md, listed for completeness) | config | n/a | n/a | deferred to Phase 62 |

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/models/Prazo.java` (full file, 57 lines) — chosen over `Evento.java` because `Prazo` already has the `prioridade` default-value convention and a UUID `@GeneratedValue(strategy = GenerationType.UUID)` id (matches CONTEXT.md decision for `ParecerSolicitacao`, vs. `Evento`'s `IDENTITY`/`Integer` id).

**Imports pattern** (`Prazo.java` lines 1-7):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
```

**Entity/annotation pattern** (`Prazo.java` lines 9-19):
```java
@Entity
@Table(name = "t_prazo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Prazo {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
```

**Default-value field pattern (`prioridade`)** (`Prazo.java` lines 33-36):
```java
    // ALTA | MEDIA | BAIXA
    @Column(nullable = false)
    @Builder.Default
    private String prioridade = "MEDIA";
```
Apply identically for `ParecerSolicitacao.prioridade` (default `"MEDIA"`) and, by the same `@Builder.Default` idiom, for `status` (default `"PENDENTE"`, free-form `String`, not a Java enum — see CONTEXT.md "Status/prioridade como String livre").

**Optional FK column pattern** (`Evento.java` line 24-25, since `processoId` is nullable there, matching `ParecerSolicitacao.processoId` being optional per CONTEXT.md):
```java
    @Column(name = "processo_id")
    private UUID processoId;
```
Contrast with required FK pattern (`Processo.java` line 24-25, for `clienteId` which is mandatory per CONTEXT.md):
```java
    @Column(name = "cliente_id", nullable = false)
    private UUID clienteId;
```

**`@PrePersist` timestamp pattern** (`Prazo.java` lines 50-56):
```java
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
```

**Fields to compose for `ParecerSolicitacao` (per CONTEXT.md decisions):**
- `id` (UUID, `GenerationType.UUID`) — like `Prazo`
- `tenantId` (UUID, not null) — like all entities
- `clienteId` (UUID, not null) — like `Processo.clienteId`
- `descricao` (String, not null, `@Column(columnDefinition = "TEXT")`) — required free-text body of the request per PARC-01; no default, no analog default-value idiom (plain not-null TEXT column)
- `processoId` (UUID, nullable) — like `Evento.processoId`
- `advogadoId` (UUID, nullable — FK to `User`, optional at creation, required at assignment) — like `Processo.responsavelId` (nullable column, validated against `User` in the controller, not at the JPA level)
- `prioridade` (String, default `"MEDIA"`) — like `Prazo.prioridade`
- `status` (String, default `"PENDENTE"`) — same free-string idiom as `prioridade`/`Processo.estado`
- `prazo` (LocalDate, nullable) — like `Prazo.dataLimite` but nullable (no `nullable = false`)
- `createdAt` (LocalDateTime, `@PrePersist`) — like all entities

---

### `backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java` (repository, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java` (full file, 12 lines)

```java
package com.lexcv.repositories;

import com.lexcv.models.Processo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProcessoRepository extends JpaRepository<Processo, UUID> {
    List<Processo> findByTenantId(UUID tenantId);
    List<Processo> findByClienteId(UUID clienteId);
}
```

Apply directly: `ParecerSolicitacaoRepository extends JpaRepository<ParecerSolicitacao, UUID>` with `findByTenantId(UUID tenantId)` (mandatory, used by the list endpoint) plus optionally `findByTenantIdAndClienteId` / `findByTenantIdAndAdvogadoId` if the planner wants filtered list endpoints (not required by CONTEXT.md, which states "lista simples filtrada por tenant" — filtering by other params can happen in-stream like `ResourceController.listProcessos`).

---

### `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, Processo CRUD block (lines 796-980), plus class header (lines 1-66) for the dedicated-controller skeleton.

**Imports pattern** (`ResourceController.java` lines 1-29, trimmed to what a dedicated controller needs):
```java
package com.lexcv.controllers;

import com.lexcv.config.UserPrincipal;
import com.lexcv.models.*;
import com.lexcv.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
```

**Class header + tenant resolution pattern** (`ResourceController.java` lines 43-46, 111-115 — copy verbatim into the new dedicated controller since it has no shared base class):
```java
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ResourceController {

    private final ClienteRepository clienteRepository;
    // ...

    private UUID getTenantId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        return principal.getTenantId();
    }
```
For `ParecerController`, use `@RequestMapping("/api/v1/pareceres/solicitacoes")` per CONTEXT.md, inject `ParecerSolicitacaoRepository` and `UserRepository` (for advogado-role validation), and duplicate the same `getTenantId()` private helper (no shared base controller exists in this codebase — each controller repeats this idiom; see `AdminController`/`AuthController` for confirmation of the same per-class duplication).

**Core CRUD pattern — create** (`ResourceController.java` lines 924-938, `createProcesso`):
```java
    @PreAuthorize("hasAuthority('processos:manage')")
    @PostMapping("/processos")
    public ResponseEntity<?> createProcesso(@RequestBody Processo processo) {
        UUID tenantId = getTenantId();
        processo.setTenantId(tenantId);
        if (processo.getResponsavelId() != null) {
            User responsavel = userRepository.findById(processo.getResponsavelId()).orElse(null);
            if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("message", "responsavelId não pertence a este tenant"));
            }
        }
        Processo saved = processoRepository.save(processo);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
```
Apply to `POST /api/v1/pareceres/solicitacoes` with `@PreAuthorize("hasAuthority('pareceres:create')")`. Note CONTEXT.md requires `clienteId` mandatory (validate not-null, 400 if missing) and `advogadoId` optional at creation — if present, validate the user exists, belongs to tenant, AND has role `ADVOGADO` (this codebase has no existing example of role-checking a FK user; compose it from `userRepository.findById(...)` + `tenantId` check above plus a new check against `user.getRole()`/`role.getNome().equals("ADVOGADO")` — see `User.java`/`Role.java` for the relationship shape before implementing). Default `status` to `"PENDENTE"` when `advogadoId` is null; set `status = "EM_ELABORACAO"` when `advogadoId` is provided at creation (CONTEXT.md: status changes automatically on assignment).

**Core CRUD pattern — get by id** (`ResourceController.java` lines 940-948, `getProcesso`):
```java
    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos/{id}")
    public ResponseEntity<?> getProcesso(@PathVariable UUID id) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }
        return ResponseEntity.ok(processo);
    }
```
Apply verbatim with `pareceres:view` and `GET /{id}` returning the entity directly (no DTO, per CONTEXT.md).

**Core CRUD pattern — list (simple, tenant-scoped, no pagination)** (derived from `ResourceController.listProcessos`, lines 796-815 — use the simple filter shape, skip the enrichment/sorting complexity which is Processo-specific):
```java
    @PreAuthorize("hasAuthority('processos:view')")
    @GetMapping("/processos")
    public ResponseEntity<?> listProcessos(...) {
        UUID tenantId = getTenantId();
        List<Processo> processos = processoRepository.findByTenantId(tenantId);
        // ... filtering/sorting/enrichment omitted for ParecerSolicitacao — not needed per CONTEXT.md ("lista simples")
        return ResponseEntity.ok(processos);
    }
```
For `ParecerController`, the minimal equivalent is:
```java
    @PreAuthorize("hasAuthority('pareceres:view')")
    @GetMapping
    public ResponseEntity<?> listSolicitacoes() {
        UUID tenantId = getTenantId();
        return ResponseEntity.ok(parecerSolicitacaoRepository.findByTenantId(tenantId));
    }
```

**Core CRUD pattern — update (excluding status-only fields, mirrors `estado` exclusion)** (`ResourceController.java` lines 950-965, `updateProcesso`):
```java
    @PreAuthorize("hasAuthority('processos:edit')")
    @PutMapping("/processos/{id}")
    public ResponseEntity<?> updateProcesso(@PathVariable UUID id, @RequestBody Processo payload) {
        Processo processo = processoRepository.findById(id).orElse(null);
        if (processo == null || !processo.getTenantId().equals(getTenantId())) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
        }

        processo.setClienteId(payload.getClienteId());
        processo.setNumeroProcesso(payload.getNumeroProcesso());
        // ...
        // estado is intentionally excluded: changes must go through /transicao or /formalizar
```
For `ParecerSolicitacao`, mirror this "exclude state-machine field from generic update" idiom: a generic `PUT /{id}` (scope `pareceres:edit`) updates `prazo`, `prioridade`, `clienteId`, `processoId` but NOT `status` directly. Add a dedicated assignment endpoint, e.g. `PUT /{id}/atribuir` (also `pareceres:edit`), that sets `advogadoId` (validating the target user has role `ADVOGADO` and tenant match), blocks reassignment when `status.equals("CONCLUIDO")` (CONTEXT.md: "Reatribuição permitida em qualquer status exceto CONCLUIDO"), and forces `status = "EM_ELABORACAO"` on successful assignment — this is the controller's own state-transition variant of the `processos:manage` / `/transicao` pattern seen at `ResourceController.java` lines 86-98, simplified to a single auto-transition rather than a generic transition map (not needed here per CONTEXT.md scope).

**Error/not-found pattern** (used throughout, e.g. lines 944-946):
```java
if (processo == null || !processo.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Processo não encontrado"));
}
```
Reuse verbatim (tenant-scoped 404) for every lookup-by-id in `ParecerController`.

---

### `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` — `seedRbac()` modification (config, batch)

**Analog:** itself, current `seedRbac()` method (lines 293-343).

**Current permission-key list pattern** (lines 294-302):
```java
    private void seedRbac() {
        List<String> permKeys = Arrays.asList(
                "clientes:view", "clientes:edit",
                "processos:view", "processos:edit",
                "processos:create", "processos:manage",
                "agenda:view", "agenda:edit",
                "documentos:view", "documentos:edit",
                "financeiro:view", "financeiro:edit",
                "rbac:manage", "users:manage"
        );
```
Add `"pareceres:view", "pareceres:create", "pareceres:edit", "pareceres:manage"` to this list.

**Permission map build + ADMIN gets all** (lines 304-311):
```java
        Map<String, Permission> permissionMap = new HashMap<>();
        for (String key : permKeys) {
            Permission perm = permissionRepository.findByNome(key)
                    .orElseGet(() -> permissionRepository.save(Permission.builder().nome(key).build()));
            permissionMap.put(key, perm);
        }

        upsertRolePermissions("ADMIN", permissionMap.values());
```
No change needed here — ADMIN automatically receives the new `pareceres:*` scopes since it iterates `permissionMap.values()`.

**Per-role permission lists** (lines 313-342) — extend each role's `Arrays.asList(...)` per CONTEXT.md ("ADVOGADO recebe view+create+edit; TECNICO/ASSISTENTE recebem apenas view"):
```java
        upsertRolePermissions("ASSISTENTE", Arrays.asList(
                permissionMap.get("clientes:view"),
                permissionMap.get("clientes:edit"),
                permissionMap.get("processos:view"),
                permissionMap.get("agenda:view"),
                permissionMap.get("documentos:view")
                // ADD: permissionMap.get("pareceres:view")
        ));

        upsertRolePermissions("TECNICO", Arrays.asList(
                permissionMap.get("clientes:view"),
                permissionMap.get("processos:view"),
                permissionMap.get("agenda:view"),
                permissionMap.get("agenda:edit"),
                permissionMap.get("documentos:view"),
                permissionMap.get("financeiro:view")
                // ADD: permissionMap.get("pareceres:view")
        ));

        upsertRolePermissions("ADVOGADO", Arrays.asList(
                permissionMap.get("clientes:view"),
                permissionMap.get("clientes:edit"),
                permissionMap.get("processos:view"),
                permissionMap.get("processos:edit"),
                permissionMap.get("processos:create"),
                permissionMap.get("processos:manage"),
                permissionMap.get("agenda:view"),
                permissionMap.get("agenda:edit"),
                permissionMap.get("documentos:view"),
                permissionMap.get("documentos:edit"),
                permissionMap.get("financeiro:view")
                // ADD: permissionMap.get("pareceres:view"), permissionMap.get("pareceres:create"), permissionMap.get("pareceres:edit")
        ));
```
Note: `pareceres:manage` is reserved for Phase 63 (approval/delivery) per CONTEXT.md — do not assign it to any role yet except via ADMIN's blanket `permissionMap.values()`.

**Idempotent upsert helper** (lines 345-353, no changes needed, reuse as-is):
```java
    private void upsertRolePermissions(String roleName, Collection<Permission> permissions) {
        Role role = roleRepository.findByNome(roleName)
                .orElseGet(() -> roleRepository.save(Role.builder().nome(roleName).build()));

        boolean changed = role.getPermissions().addAll(permissions);
        if (changed) {
            roleRepository.save(role);
        }
    }
```

---

## Shared Patterns

### Tenant Scoping (mandatory, security-critical)
**Source:** `ResourceController.java` lines 111-115 (`getTenantId()`) + every lookup-by-id check (e.g. lines 944-946)
**Apply to:** All `ParecerController` endpoints — every read/write must filter or verify `tenantId` matches `getTenantId()`. Never trust `tenantId` from the request body; always `entity.setTenantId(getTenantId())` on create and compare on read/update/delete.
```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}
```

### RBAC via `@PreAuthorize`
**Source:** Used inline per-endpoint throughout `ResourceController.java` (e.g. lines 924, 940, 950)
**Apply to:** Every `ParecerController` endpoint, matching the scope to the CONTEXT.md table: `pareceres:view` (GET list/by-id), `pareceres:create` (POST), `pareceres:edit` (PUT update + PUT assign), `pareceres:manage` reserved (not used by any endpoint in this phase).

### FK Cross-Tenant Validation (for `advogadoId`)
**Source:** `ResourceController.java` lines 929-935 (`createProcesso` validating `responsavelId`)
```java
if (processo.getResponsavelId() != null) {
    User responsavel = userRepository.findById(processo.getResponsavelId()).orElse(null);
    if (responsavel == null || !tenantId.equals(responsavel.getTenantId())) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("message", "responsavelId não pertence a este tenant"));
    }
}
```
**Apply to:** `ParecerController` create and assignment endpoints, extended with an additional role check (`Role.nome == "ADVOGADO"`) not present in any existing analog — read `User.java`/`Role.java` relationship before implementing to confirm whether role is a direct field or a collection.

### Free-string status/priority fields (no Java enum)
**Source:** `Prazo.prioridade` (`Prazo.java` line 33-36), `Processo.estado` (`Processo.java` line 40)
**Apply to:** `ParecerSolicitacao.status` and `ParecerSolicitacao.prioridade` — both stay `String` with `@Builder.Default`, validated only at the application layer if at all (existing codebase does not enforce enum-like values via `@Column` constraints).

### Direct entity response (no DTO)
**Source:** All Processo/Cliente GET and POST handlers return the JPA entity directly via `ResponseEntity.ok(entity)` / `ResponseEntity.status(HttpStatus.CREATED).body(saved)`
**Apply to:** All `ParecerController` responses, per CONTEXT.md explicit decision ("Resposta: retornar a entidade diretamente (sem DTO layer)").

## No Analog Found

None — every new file (entity, repository, controller, seeder modification) has a strong, directly-applicable analog in the existing codebase. The only piece without a precedent is the **role-validation-on-FK-assignment** logic (checking that a `User` has role `ADVOGADO` before accepting `advogadoId`); no existing endpoint validates a FK's role, only its tenant membership. The planner should compose this from `User.java`/`Role.java` (not yet read in full — recommend a quick read before writing the plan) plus the tenant-validation idiom shown above.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/models/`, `backend/src/main/java/com/lexcv/repositories/`, `backend/src/main/java/com/lexcv/controllers/`, `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java`
**Files scanned:** `Evento.java`, `Prazo.java`, `Processo.java`, `EventoRepository.java`, `ProcessoRepository.java`, `ResourceController.java` (header + Processo CRUD block), `DatabaseSeeder.java` (`seedRbac()`)
**Pattern extraction date:** 2026-06-30
