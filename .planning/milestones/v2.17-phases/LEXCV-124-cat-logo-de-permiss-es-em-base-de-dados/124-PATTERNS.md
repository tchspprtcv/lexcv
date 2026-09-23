# Phase 124: Catálogo de Permissões em Base de Dados - Pattern Map

**Mapped:** 2026-09-20
**Files analyzed:** 5 (1 model, 1 seed/infra, 1 controller, 1 repository, 1 dto) + 2 likely test files
**Analogs found:** 5 / 5

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|---------------|
| `backend/src/main/java/com/lexcv/models/Permission.java` | model | CRUD | `backend/src/main/java/com/lexcv/models/Tenant.java` | role-match (same entity style; `Tenant` is the only entity in the codebase that already added descriptive/backfilled columns to a previously-thin entity) |
| `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (`seedRbac`) | service (seed) | batch / idempotent-upsert | itself — `seedRbac()` (existing find-or-create loop) + `upsertRolePermissions()` (existing additive-merge loop), same file | exact (extend existing method, don't replace pattern) |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` (`getRbac`) | controller | request-response | `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` (`listTenants` + `toSummary`) | role-match (closest "map entity list → DTO list via repository read" pattern in the codebase) |
| `backend/src/main/java/com/lexcv/repositories/PermissionRepository.java` | repository | CRUD | `backend/src/main/java/com/lexcv/repositories/RoleRepository.java` / itself | exact (trivial Spring Data interface, same idiom) |
| `backend/src/main/java/com/lexcv/dtos/RbacResponse.java` (`PermissionDefDto`) | model (DTO) | request-response | itself (already shaped correctly — see Invariant below) | exact — **do not change field names/shape** |
| Backend test — `AdminController#getRbac` DB-backed behavior | test | request-response | `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java` | exact (same handler, same Mockito/proxy idiom) |
| Backend test — `DatabaseSeeder#seedRbac` idempotency/upsert | test | batch | `backend/src/test/java/com/lexcv/seed/DatabaseSeederPlataformaAdminTest.java` | exact (same class, same `@InjectMocks` + lenient-stub idiom) |
| `backend/migrations/<126>-add-permission-catalogo-columns.sql` (new, production-only) | migration | batch | `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` | exact (same "add nullable column(s) + backfill existing rows" shape) |

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/Permission.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/models/Tenant.java`

**Current state of the file being modified** (all 21 lines — this is the full file today):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "t_permission")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "nome")
public class Permission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String nome;
}
```

**Column-addition-with-safe-default pattern** (`Tenant.java:50-53`, the `plano` field — directly analogous to adding a required `categoria`/`rotulo` column to an entity whose table already has rows in production):
```java
@Enumerated(EnumType.STRING)
@Column(name = "plano", nullable = false, columnDefinition = "varchar(255) not null default 'STARTER'")
@Builder.Default
private TenantPlano plano = TenantPlano.STARTER;
```
Key points to copy:
- `columnDefinition` embeds the SQL default so that on a **fresh** database (`ddl-auto: update` against an empty table) the `ALTER TABLE ... ADD COLUMN ... NOT NULL DEFAULT '...'` succeeds immediately without a separate backfill.
- `@Builder.Default` is mandatory whenever a field has a Java-side default — every `.builder().build()` call site that does not explicitly set the new field (e.g. `DatabaseSeeder.seedRbac`'s `Permission.builder().nome(key).build()`) otherwise silently gets `null`, not the default. The comment block above this field in `Tenant.java:40-49` documents this exact trap and is worth mirroring in `Permission.java`'s new fields.
- A plain (non-required/no-default) descriptive column, e.g. `rotulo`/`descricao`/`categoria` if some are allowed to start blank, should still declare `@Column(name = "...")` explicitly (snake_case name) even without `columnDefinition`, matching `Tenant.java:23-26` (`nif`, `tipoEntidade`) style for simple nullable strings.
- Because `t_permission` already has real rows (seeded permissions with real role/user assignments per CONTEXT.md invariant), any **NOT NULL** new column needs the same `columnDefinition ... default '...'` treatment as `plano`, or a companion backfill migration (see Shared Patterns → Migration below) — the `ativo` field (`Tenant.java:75-77`) is the second worked example of this same "populate existing rows automatically via DDL default" concern, with its own explanatory comment.

**Boolean/flag column pattern** (`Tenant.java:75-77`, useful if a "reservada"/"oferecível" boolean is added, e.g. to mark the `PLATAFORMA_ADMIN`-only permissions):
```java
@Column(name = "ativo", nullable = false, columnDefinition = "boolean not null default true")
@Builder.Default
private Boolean ativo = true;
```

---

### `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` — extend `seedRbac()` (service/seed, batch/idempotent-upsert)

**Analog:** itself, `seedRbac()` at lines 311-330 and `upsertRolePermissions()` at lines 377-385 — this is the file being modified, and CONTEXT.md explicitly names this as "o ponto de partida correcto". Do not invent a new seeding idiom; extend the existing one.

**Current find-or-create idempotent loop** (lines 311-330):
```java
private void seedRbac() {
        List<String> permKeys = Arrays.asList(
                        "clientes:view", "clientes:edit",
                        "processos:view", "processos:edit",
                        "processos:create", "processos:manage",
                        "agenda:view", "agenda:edit",
                        "documentos:view", "documentos:edit",
                        "financeiro:view", "financeiro:edit", "financeiro:manage",
                        "rbac:manage", "users:manage",
                        "pareceres:view", "pareceres:create", "pareceres:edit", "pareceres:manage",
                        "notificacoes:view");

        Map<String, Permission> permissionMap = new HashMap<>();
        for (String key : permKeys) {
                Permission perm = permissionRepository.findByNome(key)
                                .orElseGet(() -> permissionRepository
                                                .save(Permission.builder().nome(key).build()));
                permissionMap.put(key, perm);
        }
        ...
```
To enrich each permission with `rotulo`/`descricao`/`categoria` while staying upsert-safe, the `.orElseGet(...)` branch (create path) needs a sibling **update path**: when `findByNome` returns a present `Permission`, its descriptive fields still need to be kept in sync with the catalogue source-of-truth on every boot (per ROADMAP success criterion 2: "actualiza rótulo/descrição/categoria das existentes"). The natural shape (mirroring the existing `if (changed) roleRepository.save(role)` idiom just below) is: look up the permission, set/overwrite the three descriptive fields on whichever instance is returned (new or existing), and always `save()` — this is safe because `nome` (the identity/unique column) is never touched, so it can never disturb `t_role_permission`/`t_user_permission` foreign keys.

**Never-remove additive-merge pattern** (`upsertRolePermissions`, lines 377-385) — copy verbatim as the model for "how this codebase writes seed data that must never delete production rows":
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
The `boolean changed = collection.addAll(...); if (changed) save();` idiom (avoid unnecessary writes, never call anything that removes) is the codebase's canonical "safe reconciliation" shape and should be echoed by whatever the new "keep descriptive columns fresh" logic looks like — except here the target is field values on `Permission`, not membership in a `Set`, so a direct `permissionRepository.save(perm)` per key (already inside the existing loop) is idiomatic, not `addAll`.

**Reservation pattern already established for `PLATAFORMA_ADMIN`** (line 374, directly relevant to invariant #2 in CONTEXT.md — "reserva é por papel, não por permissão"):
```java
upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList());
```
Comment above it (lines 369-373) is the precedent for how this codebase documents "why this collection stays empty on purpose" — worth mirroring in whatever seeds the new descriptive catalogue, if any permission is marked reserved/excluded.

---

### `backend/src/main/java/com/lexcv/controllers/AdminController.java` — replace hardcoded list in `getRbac()` (controller, request-response)

**Analog:** `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` — `listTenants()` (lines 106-113) + `toSummary()` (lines 191-200), the only existing "repository `findAll()` → stream → map to DTO → collect" pattern in `controllers/`.

**Imports already present in `AdminController.java`** (lines 1-24 — no new imports needed beyond what's already there for the `getRbac` change; `Permission` and `PermissionRepository` are already injected):
```java
import com.lexcv.dtos.RbacResponse;
import com.lexcv.models.Permission;
import com.lexcv.models.Role;
import com.lexcv.repositories.PermissionRepository;
import com.lexcv.repositories.RoleRepository;
...
import java.util.*;
import java.util.stream.Collectors;
```

**findAll → map → DTO-list pattern to copy** (`PlatformAdminController.java:106-113`):
```java
@GetMapping("/tenants")
public ResponseEntity<?> listTenants() {
    List<TenantAdminSummaryResponse> tenants = tenantRepository.findAll().stream()
            .map(this::toSummary)
            .sorted(Comparator.comparing(TenantAdminSummaryResponse::getNome, String.CASE_INSENSITIVE_ORDER))
            .collect(Collectors.toList());
    return ResponseEntity.ok(tenants);
}
```
and the extracted private mapper (`PlatformAdminController.java:191-200`):
```java
private TenantAdminSummaryResponse toSummary(Tenant tenant) {
    return TenantAdminSummaryResponse.builder()
            .id(tenant.getId())
            .nome(tenant.getNome())
            .plano(tenant.getPlano())
            .limiteUtilizadores(tenant.getLimiteUtilizadores())
            .ativo(tenant.getAtivo())
            .utilizadoresAtivos(userRepository.countByTenantIdAndAtivoTrue(tenant.getId()))
            .build();
}
```
Applied to `getRbac()`, the block being replaced (`AdminController.java:374-392`, the 17-entry `Arrays.asList(new RbacResponse.PermissionDefDto(...))` literal) becomes a `permissionRepository.findAll()` read, a private `toPermissionDef(Permission p)` mapper building `RbacResponse.PermissionDefDto` from the entity's new columns, `.stream().map(this::toPermissionDef).collect(Collectors.toList())`. Two things the analog does **not** need but this handler does, both already established elsewhere in this same file:
1. **Exclude platform-reserved permissions** — mirror the existing filter idiom already used for roles two lines above the block being replaced (`AdminController.java:365-367`):
   ```java
   if (PAPEL_PLATAFORMA.equals(role.getNome())) {
       continue;
   }
   ```
   whatever field/flag marks a `Permission` as platform-reserved needs the equivalent `.filter(p -> !isReservada(p))` before `.collect(...)`, consistent with CONTEXT.md's invariant that reservation is "por papel", but the resulting catalogue exposed to `systemPermissions` must still never leak permissions that exist only to define `PLATAFORMA_ADMIN`.
2. **Stable ordering** — `PlatformAdminController` sorts by `nome` for UI stability (`.sorted(Comparator.comparing(...))`); apply the same when building `systemPermissions` so the RBAC matrix doesn't reorder between requests (the hardcoded list it replaces had a fixed, deliberate order).

**Do not touch** — `getRbac()`'s existing role-exclusion loop (lines 358-372) and the response-building tail (lines 394-399) stay as-is; only the `systemPermissions` construction (374-392) changes source.

---

### `backend/src/main/java/com/lexcv/dtos/RbacResponse.java` (dto, request-response) — contract, not a pattern to imitate

**Current shape** (already correct, 4 fields, do not rename):
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class PermissionDefDto {
    private String key;
    private String nome;
    private String descricao;
    private String modulo;
}
```
CONTEXT.md's Portuguese phrasing "nome, rótulo, descrição, categoria" maps onto this existing DTO's fields as: `key` = technical name (`Permission.nome`), `nome` = rótulo/label, `descricao` = descrição, `modulo` = categoria. **No field rename** — CONTEXT.md invariant #3 states the response contract (`RbacResponse`/`PermissionDefDto`) must not change, since `web/src/hooks/use-admin.ts` (`useAdminRbac`) and `RbacTab` already consume this exact shape.

---

### `backend/src/main/java/com/lexcv/repositories/PermissionRepository.java` (repository, CRUD)

**Analog:** itself / `RoleRepository.java` (same one-liner Spring Data idiom throughout `repositories/`).

**Current file** (full, 9 lines):
```java
package com.lexcv.repositories;

import com.lexcv.models.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface PermissionRepository extends JpaRepository<Permission, Integer> {
    Optional<Permission> findByNome(String nome);
}
```
`findAll()` (inherited from `JpaRepository`) is already sufficient for `getRbac()`'s new read — no new query method is strictly required. If filtering platform-reserved permissions at the SQL level is preferred over filtering in Java (e.g. `findAllByCategoriaNot(...)` or an `ativo`/`reservada` boolean predicate method), follow the existing single-line derived-query idiom shown above; no repository in this codebase uses `@Query` for anything this simple.

---

## Shared Patterns

### Idempotent upsert (never destructive), for any RBAC-catalogue seeding
**Source:** `DatabaseSeeder.java:311-330` (`seedRbac`, find-or-create) and `:377-385` (`upsertRolePermissions`, additive `addAll`)
**Apply to:** the extended `seedRbac()` method — the loop must keep using `findByNome(...).orElseGet(() -> save(...))` for creation, and any field sync on existing rows must be a plain field-`set` + `save`, never a delete-then-reinsert. This is CONTEXT.md's non-negotiable invariant #1.

### Column-add-with-safe-default on an entity with existing production rows
**Source:** `Tenant.java:50-53` (`plano`) and `:75-77` (`ativo`), with the accompanying migration `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql`
**Apply to:** every new column on `Permission`. Pattern: `@Column(..., columnDefinition = "<type> not null default '<value>'")` + `@Builder.Default` on the entity (covers fresh `ddl-auto: update` databases), **plus** a new numbered file in `backend/migrations/` for existing/production databases (`ddl-auto: validate`), following the exact shape of `117-add-tenant-plano-limite-utilizadores.sql`:
```sql
ALTER TABLE t_permission ADD COLUMN rotulo VARCHAR(255);
ALTER TABLE t_permission ADD COLUMN descricao VARCHAR(1000);
ALTER TABLE t_permission ADD COLUMN categoria VARCHAR(255);

UPDATE t_permission SET rotulo = ... WHERE rotulo IS NULL;
-- (repeat per column, or defer to the seeder's own upsert to backfill values at next boot)
```
Any new migration file **must** also get a new row added to `backend/migrations/README.md`'s tables (see "Adding a new migration" section, lines 250-257 of that file) in the same commit — this is an explicit project convention, not optional housekeeping.

### DTO-list built from repository read (replacing a hardcoded literal)
**Source:** `PlatformAdminController.java:106-113` + `:191-200` (`listTenants`/`toSummary`)
**Apply to:** `AdminController.getRbac()`'s `systemPermissions` construction — `findAll().stream().map(entity -> dto).sorted(...).collect(Collectors.toList())`, with the reserved-permission filter modeled on the existing role-exclusion `continue` already in the same method (lines 365-367).

### Mockito test scaffolding for AdminController handlers
**Source:** `backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java` — `@ExtendWith(MockitoExtension.class)`, `@Mock` per repository, `novoController()` constructing via the `@RequiredArgsConstructor`-generated constructor, `novoProxyComMethodSecurity()` for tests that need `@PreAuthorize` to actually evaluate.
**Apply to:** any new test asserting `getRbac()`'s returned `systemPermissions` reflects DB rows (mock `permissionRepository.findAll()` to return a list of `Permission.builder()...build()` and assert the resulting `RbacResponse.systemPermissions` DTOs); no proxy needed for this (pure return-value assertion, not an authorization-gate assertion) — direct call to `novoController().getRbac()` is enough, mirroring `getRbac_comRolePlataformaAdminObtemSucesso` (lines 179-188) minus the proxy wrapper if only body behavior (not the `@PreAuthorize` annotation itself) is under test.

### Mockito test scaffolding for `DatabaseSeeder`
**Source:** `backend/src/test/java/com/lexcv/seed/DatabaseSeederPlataformaAdminTest.java` — `@InjectMocks private DatabaseSeeder seeder`, `@Mock` per repository/collaborator, `lenient()` stubs in `@BeforeEach` for branches exercised in every test case, `ReflectionTestUtils.setField(seeder, "seedEnabled", ...)` to toggle the seed gate, `ArgumentCaptor<Permission>`/`<Role>` to assert what gets saved.
**Apply to:** a new test proving `seedRbac()` (a) creates a `Permission` with populated descriptive fields on first boot, (b) on a second boot with an existing `Permission` row (stub `findByNome` to return a present, differently-labeled `Permission`), updates its descriptive fields via `save()` without ever calling anything that deletes `t_role_permission`/`t_user_permission` rows — i.e. `verify(permissionRepository, never()).delete(any())` / no `deleteAll` call, matching the spirit of `run_numSegundoArranqueComSeedEnabled_naoRecriaTenantNemUtilizador` (lines 158-178) which proves "second boot doesn't re-create, doesn't destroy".

## No Analog Found

None — every file in scope has a direct or role-matched analog in the current codebase (this phase is explicitly "infraestrutura... convenções já estabelecidas no codebase" per CONTEXT.md, so no novel pattern needed).

## Open Decision Flagged by CONTEXT.md (not a pattern, but planner-relevant)

CONTEXT.md's `<specifics>` section requires an explicit decision — during planning, not deferred — for the 3 permission keys seeded in `DatabaseSeeder.seedRbac()` but absent from the current hardcoded `getRbac()` list: `processos:create`, `processos:manage`, `financeiro:manage`. Once `getRbac()` reads from the database instead of the hardcoded 17-entry list, these 3 will automatically appear in `systemPermissions` unless deliberately excluded/labeled. The seeder extension (see `seedRbac()` pattern above) is the natural place to assign them `rotulo`/`descricao`/`categoria` alongside the other 17, or to mark them non-offered if that's the chosen resolution — this is a planning/content decision, not a pattern gap.

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{models,repositories,controllers,seed,dtos}/`, `backend/src/test/java/com/lexcv/{controllers,seed}/`, `backend/migrations/`
**Files scanned:** 41 models, 29 repositories, 2 seed-adjacent test files, `AdminController.java`, `PlatformAdminController.java`, `RbacResponse.java`, `PermissionRepository.java`, `backend/migrations/README.md` + 1 referenced migration file
**Pattern extraction date:** 2026-09-20
