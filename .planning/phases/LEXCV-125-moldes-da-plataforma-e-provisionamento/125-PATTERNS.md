# Phase 125: Moldes da Plataforma e Provisionamento - Pattern Map

**Mapped:** 2026-09-20
**Files analyzed:** 14 (backend: 7 create/modify + tests; frontend: 6 create/modify + 1 verify script)
**Analogs found:** 14 / 14

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/.../models/Role.java` | model | CRUD | itself (add column) | n/a — modify in place |
| `backend/.../models/TenantRole.java` (NEW) | model | CRUD | `backend/.../models/Cliente.java` (tenant-unique constraint) + `backend/.../models/Role.java` (ManyToMany) | exact (composite) |
| `backend/.../repositories/TenantRoleRepository.java` (NEW) | model/repository | CRUD | `backend/.../repositories/ClienteRepository.java` + `backend/.../repositories/RoleRepository.java` | exact |
| `backend/.../services/SetupService.java` (`provisionTenant`) | service | CRUD, transactional | itself (existing method, extend) | exact — modify in place |
| `backend/.../controllers/PlatformAdminController.java` (new endpoints) | controller | request-response | itself, `createTenant`/`listTenants`/`updateTenant` (existing handlers) | exact — extend in place |
| `backend/.../dtos/*` (Molde DTOs, NEW) | dto | request-response | `backend/.../dtos/TenantAdminSummaryResponse.java`, `TenantUpdateRequest.java` | exact |
| `backend/migrations/126-*.sql` (NEW) | migration | batch | `backend/migrations/124-add-permission-catalogo-columns.sql` | exact |
| `backend/src/test/.../TenantRoleServiceTest.java` / provisioning test (NEW) | test | request-response | `backend/src/test/.../services/SetupServiceProvisionTenantTest.java` | exact |
| `backend/src/test/.../PlatformAdminControllerMoldesTest.java` (NEW) | test | request-response | `backend/src/test/.../controllers/PlatformAdminControllerTest.java` | exact |
| `web/src/app/(dashboard)/plataforma/moldes/page.tsx` (NEW) | route/component | request-response | `web/src/app/(dashboard)/plataforma/relatorio/page.tsx` (+ `columns.tsx`) | exact |
| `web/src/app/(dashboard)/plataforma/page.tsx` (entry button) | component | request-response | itself, "Ver Relatório" button block | exact — modify in place |
| `web/src/hooks/use-platform-moldes.ts` (NEW) | hook | request-response | `web/src/hooks/use-platform-admin.ts` | exact |
| `web/src/types/platform-moldes.ts` (NEW) | type | n/a | `web/src/types/platform-admin.ts` | exact |
| `web/scripts/verify-consola-moldes.mjs` (NEW) + `package.json` entry | test (structural gate) | batch | `web/scripts/verify-relatorio-utilizacao.mjs`, `web/scripts/verify-consola-tenants.mjs` | exact |

Not separately tabulated but reused directly: the checkbox-matrix editing UI for `/plataforma/moldes` should copy `RbacTab` (`web/src/app/(dashboard)/settings/page.tsx:755-960`ish), and the inline creation panel should copy `criar-tenant-panel.tsx` — see Pattern Assignments below.

---

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/Role.java` (model, CRUD) — gains instantiability column

**Analog:** `Permission.java` (same file family, same "flag column with safe default" idiom already used for `reservadaPlataforma`)

**Current full file** (`backend/src/main/java/com/lexcv/models/Role.java:1-32`):
```java
package com.lexcv.models;

import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "t_role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "nome")
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String nome;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "t_role_permission",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
}
```

**Flag-column-with-safe-default idiom to copy** (`Permission.java:61-63`, apply the same shape to the new `instanciavel`/molde column on `Role`):
```java
@Column(name = "reservada_plataforma", nullable = false, columnDefinition = "boolean not null default true")
@Builder.Default
private Boolean reservadaPlataforma = true;
```
Note the semantics differ (CONTEXT.md: the 4 existing roles are moldes by default, `PLATAFORMA_ADMIN` is not) — the new column's SQL default and the `DatabaseSeeder` upsert must agree on which side is the safe default, exactly as `Permission.reservadaPlataforma`'s doc-comment (lines 49-63) explains for its own column. `columnDefinition` guarantees `ddl-auto: update` creates the column with the right default on environments that don't yet have it; `@Builder.Default` is mandatory or any `Role.builder()...build()` call that doesn't set the field explicitly gets `null` instead.

---

### `backend/src/main/java/com/lexcv/models/TenantRole.java` (NEW model, CRUD)

**Analog A — tenant-scoped unique constraint:** `backend/src/main/java/com/lexcv/models/Cliente.java:12-19` (exactly the `(tenant_id, documento_numero)` pattern CONTEXT.md points at):
```java
@Entity
@Table(
    name = "t_cliente",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "documento_numero"}),
        @UniqueConstraint(columnNames = {"tenant_id", "numero_sequencial"})
    }
)
```
Apply the same shape to `t_tenant_role`:
```java
@Entity
@Table(
    name = "t_tenant_role",
    uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "nome"})
)
```

**Analog B — the `@ManyToMany` + `@JoinTable` idiom:** `Role.permissions` (`Role.java:24-31`) and `User.roles` (`User.java:54-61`) are the two live examples; `TenantRole.permissions` must follow the exact same shape, pointed at a new join table:
```java
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(
    name = "t_tenant_role_permission",
    joinColumns = @JoinColumn(name = "tenant_role_id"),
    inverseJoinColumns = @JoinColumn(name = "permission_id")
)
@Builder.Default
private Set<Permission> permissions = new HashSet<>();
```

**tenant_id column convention** (from `User.java:23-24`, same as `Cliente.java:30-31`):
```java
@Column(name = "tenant_id", nullable = false)
private UUID tenantId;
```

**`molde_id` nullable provenance + `sistema` boolean:** no direct analog exists (this is the first nullable FK-as-provenance-not-live-reference in the codebase), but the *nullable optional column* convention is `Permission.rotulo`/`descricao` (`Permission.java:31-35`, plain `@Column` with no `nullable = false`). Keep `molde_id` a plain `@Column(name = "molde_id")` Integer/reference (not a JPA `@ManyToOne` with cascade, per CONTEXT.md: "Não cria dependência viva — é snapshot, e o `molde_id` é histórico, não referência de leitura" — a bare FK column, not a navigable association, is the correct translation of that decision). `sistema` should follow the same "boolean flag, explicit default" idiom as `Permission.reservadaPlataforma` shown above.

**Full field template to build from** (composing Cliente's `@Table` + Role's `@ManyToMany` + User's `tenant_id`/id conventions — id strategy should follow `Cliente.id`/`User.id` (`GenerationType.UUID`), not `Role.id`/`Permission.id` (`GenerationType.IDENTITY`), since `TenantRole` rows are created at runtime per tenant, same as `Cliente`/`User`):
```java
@Entity
@Table(name = "t_tenant_role", uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "nome"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TenantRole {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String nome;

    @Column(name = "molde_id")
    private Integer moldeId; // nullable, snapshot provenance -- never navigated as an association

    @Column(nullable = false)
    @Builder.Default
    private Boolean sistema = false;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "t_tenant_role_permission",
        joinColumns = @JoinColumn(name = "tenant_role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    @Builder.Default
    private Set<Permission> permissions = new HashSet<>();
}
```

---

### `backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java` (NEW repository)

**Analog:** `backend/src/main/java/com/lexcv/repositories/ClienteRepository.java:12-16` (tenant-scoped finder methods) + `backend/src/main/java/com/lexcv/repositories/RoleRepository.java` (simple `findByNome`):
```java
public interface ClienteRepository extends JpaRepository<Cliente, UUID> {
    List<Cliente> findByTenantId(UUID tenantId);
    ...
    Optional<Cliente> findByTenantIdAndDocumentoNumero(UUID tenantId, String documentoNumero);
}
```
```java
public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByNome(String nome);
}
```
Combine into:
```java
public interface TenantRoleRepository extends JpaRepository<TenantRole, UUID> {
    List<TenantRole> findByTenantId(UUID tenantId);
    Optional<TenantRole> findByTenantIdAndNome(UUID tenantId, String nome);
}
```

---

### `backend/src/main/java/com/lexcv/services/SetupService.java` — `provisionTenant` (modify in place, insertion point)

**Current full method** (`SetupService.java:102-131`), the exact transaction the instantiation loop goes inside:
```java
@Transactional
public Tenant provisionTenant(SetupInitializeRequest request) {
    validateRequest(request);

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

    return tenant;
}
```
Per CONTEXT.md decisions: the molde-instantiation loop is inserted after `tenant = tenantRepository.save(tenant);` (tenant.getId() must exist first, since `TenantRole.tenantId` needs it) and can run either before or after the `adminUser` save — it does NOT touch `adminUser.roles` (that stays `Set.of(adminRole)`, the **global** Role, unchanged this phase). The method still returns `Tenant`, unchanged signature. Constructor injection order (`RequiredArgsConstructor`, 5 collaborators today) is documented at `SetupServiceProvisionTenantTest.java:47-51` — a new `roleRepository.findAll()`-style read for "every current molde" plus a new `TenantRoleRepository` save loop are the only additions; **no new collaborator is needed for `roleRepository`** (already injected), only `TenantRoleRepository` needs to be added to the constructor list, which changes the fixed 5-mock instantiation order in every test that constructs `SetupService` directly (see Test pattern below).

**Copy-a-collection-onto-a-new-owner idiom** to reuse for cloning each molde's permission set snapshot into the new `TenantRole` (`backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:482-490`):
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
The instantiation loop is the mirror of this: for each instantiable `Role` (find all where the new instantiability flag is true, excluding `PLATAFORMA_ADMIN` by construction since its flag is false), build a new `TenantRole` with `permissions = new HashSet<>(molde.getPermissions())` (a copy, not a shared reference — this is what makes it a snapshot per CONTEXT.md) and `moldeId = molde.getId()`, `sistema = true`, save via `tenantRoleRepository`.

---

### `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` — new `/platform/moldes` endpoints

**Class-level gate already in place** (`PlatformAdminController.java:51-55`), inherited automatically by any new handler, no per-method annotation needed:
```java
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")
@RequiredArgsConstructor
public class PlatformAdminController {
```

**Form to copy for `GET /platform/moldes`** — `listTenants` (`PlatformAdminController.java:106-113`), cross-tenant-by-design listing with DTO projection:
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

**Form to copy for `PUT /platform/moldes` (edit a molde's permission set)** — `updateTenant` (`PlatformAdminController.java:126-147`), not-found guard + field-level validation + save + re-project response:
```java
@PutMapping("/tenants/{id}")
public ResponseEntity<?> updateTenant(@PathVariable UUID id, @RequestBody TenantUpdateRequest request) {
    Tenant tenant = tenantRepository.findById(id).orElse(null);
    if (tenant == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Tenant não encontrado."));
    }
    if (request.getPlano() == null) {
        return ResponseEntity.badRequest().body(Map.of("message", "O plano é obrigatório."));
    }
    ...
    tenant.setPlano(request.getPlano());
    tenant.setLimiteUtilizadores(request.getLimiteUtilizadores());
    Tenant tenantGravado = tenantRepository.save(tenant);
    return ResponseEntity.ok(toSummary(tenantGravado));
}
```
For `PUT /platform/moldes` this becomes: load `Role` by id/nome, 404 if missing, **reject if the role is not instantiable / is `PLATAFORMA_ADMIN`** (mirrors the `TENANT_RESERVADO` guard pattern in `setTenantAtivo`, see below), replace `permissions` from the request DTO, save, return the projected DTO. Per CONTEXT.md this edit is snapshot-only — it never touches already-instantiated `TenantRole` rows.

**Guard-a-reserved-entity idiom to copy** for "PLATAFORMA_ADMIN never editable/creatable as molde" (`PlatformAdminController.java:165-184`, `setTenantAtivo`):
```java
if (TENANT_RESERVADO.equals(tenant.getNome()) && !novoAtivo) {
    return ResponseEntity.badRequest().body(Map.of("message", "Não é possível suspender o tenant da plataforma (ALCv)."));
}
```
Apply the same literal-name guard shape against `"PLATAFORMA_ADMIN"` in both the `PUT` and `POST /platform/moldes` handlers.

**Form to copy for `POST /platform/moldes` (create a new molde)** — `createTenant` (`PlatformAdminController.java:67-94`), delegates validation to the service layer and translates exceptions to HTTP status:
```java
@PostMapping("/tenants")
public ResponseEntity<?> createTenant(@RequestBody SetupInitializeRequest request) {
    try {
        Tenant tenant = setupService.provisionTenant(request);
        TenantProvisionResponse response = TenantProvisionResponse.builder()
                .id(tenant.getId())
                .nome(tenant.getNome())
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    } catch (IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
    } catch (IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", ex.getMessage()));
    } catch (DataIntegrityViolationException ex) {
        return ResponseEntity.badRequest().body(Map.of("message", "Já existe um utilizador com este email."));
    }
}
```
For `POST /platform/moldes`: validate `nome` non-blank and not already used (unique constraint on `Role.nome` already enforces this at the DB level — the same `DataIntegrityViolationException` catch shape applies to a concurrent duplicate name), build and save the new `Role` with the instantiability flag set true, return 201 with a minimal DTO (id + nome), same shape as `TenantProvisionResponse`.

**DTO-projection helper to copy** (`PlatformAdminController.java:191-200`, `toSummary`):
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
The molde equivalent should also compute "how many tenants have already instantiated this molde" the same live way (`tenantRoleRepository.countByMoldeId(role.getId())` or similar) — this is exactly the count the UI-SPEC requires for the non-propagation warning (CONTEXT.md `<specifics>`: "Deve dizer quantos escritórios já instanciaram aquele molde").

---

### New DTOs under `backend/src/main/java/com/lexcv/dtos/`

**Response DTO analog:** `TenantAdminSummaryResponse.java` (full file, 24-35) — `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, flat projection, doc-comment explaining what's deliberately omitted from the raw entity:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAdminSummaryResponse {
    private UUID id;
    private String nome;
    private TenantPlano plano;
    private Integer limiteUtilizadores;
    private Boolean ativo;
    private long utilizadoresAtivos;
}
```
Molde-list response DTO should follow this shape: `id`, `nome`, `permissoes` (list of permission ids or a nested minimal projection — never the raw `Set<Permission>` from the entity), and `tenantsInstanciados` (long, the live count above).

**Request DTO analog:** `TenantUpdateRequest.java` (full file, 21-27) — `@Getter @Setter`, no Lombok `@Data`/`@Builder` (mutable request body, deserialized by Jackson):
```java
@Getter
@Setter
public class TenantUpdateRequest {
    private TenantPlano plano;
    private Integer limiteUtilizadores;
}
```
The `PUT /platform/moldes/{id}` request body DTO should follow this exact shape (`permissoes: List<Integer>` or `List<String>` of permission identifiers).

---

### `backend/migrations/126-add-tenant-role-tables.sql` (NEW — next free number is 126, not 125)

**Important gotcha for the planner:** migration filenames are a flat, monotonically increasing sequence across the *entire history of the repo*, not tied 1:1 to the roadmap phase number. `backend/migrations/125-convert-tenant-logo-data-url-to-text.sql` already exists (an unrelated, much older change) — so this phase's migration, despite being "Phase 125" on the roadmap, must be filed as **`126-...`**, the next unused integer. Confirmed via `ls backend/migrations | sed 's/^\([0-9]*\)-.*/\1/' | sort -n`: the existing sequence is `74, 81, 82, 86, 88, 91, 93, 96, 111, 117, 120, 124, 125` — 126 is free.

**Header/idempotency shape to copy** — `backend/migrations/124-add-permission-catalogo-columns.sql` (full file):
```sql
-- Phase 124 (CATL-01/CATL-02/CATL-03): add rotulo/descricao/modulo/ordem/reservada_plataforma
-- columns to t_permission
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying the
-- code change ...
-- (full "why", "what breaks without it", idempotency explanation)
--
-- Idempotent: every ADD COLUMN statement below uses IF NOT EXISTS, so this script is safe to
-- run twice against the same database.

ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS rotulo VARCHAR(255);
...
```
For this phase's script: `CREATE TABLE IF NOT EXISTS t_tenant_role (...)`, `CREATE TABLE IF NOT EXISTS t_tenant_role_permission (...)`, `ALTER TABLE t_role ADD COLUMN IF NOT EXISTS instanciavel BOOLEAN NOT NULL DEFAULT ...` (name TBD by planner) — `CREATE TABLE IF NOT EXISTS` + `ADD COLUMN IF NOT EXISTS` both re-run safely, so this script should join the small "safe to re-run" group (`74`, `111`, `120b`, `124`) documented in the README's re-run-safety table, not the "fails loudly" group.

**README update required in the same commit** (`backend/migrations/README.md:253-260`, "Adding a new migration" section):
```markdown
- Name it `<phase-number>-<kebab-description>.sql` ...
- Keep the file-header comment convention: what, why, what breaks without it, and whether it
  is idempotent.
- **Add a row to the tables in this file in the same commit.** The inventory lives here and
  nowhere else.
```
Add a new row to the **Path B** table (`README.md:138-153`) as entry 15, strictly after row 14 (`125-convert-tenant-logo-data-url-to-text.sql`) — ascending numeric order per file name, not per roadmap phase — and (since it's re-runnable) also add it to the "safe to re-run" table (`README.md:174-181`).

---

### Backend tests (Mockito, service + controller)

**Service test analog — full pattern:** `backend/src/test/java/com/lexcv/services/SetupServiceProvisionTenantTest.java` (1-90 shown). Key conventions:
- `@ExtendWith(MockitoExtension.class)`, `@Mock` per collaborator, service instantiated directly via constructor in `@BeforeEach` — no `@SpringBootTest`.
- Constructor argument order must match `SetupService`'s `@RequiredArgsConstructor` field order exactly; adding `TenantRoleRepository` as a new field shifts this list — every test file that does `new SetupService(...)` needs updating (this test is one; `SetupServiceProvisionTenantTest.java:66-67` is the exact line).
- `stubTenantSaveComIdGerado()` helper pattern (lines 79-85) — stub `save()` to assign a generated id via `thenAnswer`, needed because `TenantRole.tenantId` depends on `tenant.getId()` being set mid-transaction.
- `ArgumentCaptor` used elsewhere in the same file to assert exactly what was persisted — use the same technique to assert each instantiated `TenantRole`'s `permissions` is a **copy** (not the same `Set` instance) of the molde's permissions, proving the snapshot decision from CONTEXT.md.

**Controller test analog — full `@PreAuthorize` proxy pattern:** `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java:78-134` (Grupo A direct-call tests + Grupo B AOP-proxy tests):
```java
private PlatformAdminController novoProxyComMethodSecurity() {
    ProxyFactory factory = new ProxyFactory(novoController());
    factory.setProxyTargetClass(true);
    factory.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
    return (PlatformAdminController) factory.getProxy();
}

private void autenticarComoRoles(String... roles) {
    List<SimpleGrantedAuthority> autoridades = Arrays.stream(roles)
            .map(SimpleGrantedAuthority::new)
            .toList();
    SecurityContextHolder.getContext()
            .setAuthentication(new UsernamePasswordAuthenticationToken(null, null, autoridades));
}
```
Use this exact proxy technique (never annotation-reflection alone) to prove a tenant-scoped `ADMIN` (non-`PLATAFORMA_ADMIN`) gets denied at the new `/platform/moldes` handlers — this is the pattern the CONTEXT.md `<code_context>` section explicitly calls out as the Phase 119-established convention.

---

## Frontend Pattern Assignments

### `web/src/app/(dashboard)/plataforma/moldes/page.tsx` (NEW route)

**Structural analog:** `web/src/app/(dashboard)/plataforma/relatorio/page.tsx` (full file, 192 lines) — page-guard pattern to copy verbatim:
```tsx
export default function RelatorioUtilizacaoPage() {
  const me = useMe();

  if (!me.isFetched) {
    return null;
  }

  if (!me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
    return (
      <AccessDeniedState
        description="Não tem permissão para aceder ao relatório de utilização de tenants."
        backHref="/dashboard"
      />
    );
  }

  return <RelatorioUtilizacaoContent />;
}
```
Note the ordering comment at lines 29-37: `!me.isFetched` must resolve **before** the role check, never combined into one condition (WR-03 from the Phase 120 code review) — copy this ordering exactly for `/plataforma/moldes`.

Back-link + header block to copy (lines 67-87):
```tsx
<div className="flex items-center gap-4">
  <Button asChild variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-slate-900 dark:hover:text-white">
    <Link href="/plataforma" aria-label="Voltar">
      <ArrowLeft className="h-4 w-4" />
    </Link>
  </Button>
  <div>
    <h1 className="text-3xl font-semibold ...">Relatório de Utilização</h1>
    <p className="text-sm text-slate-500 ...">...</p>
  </div>
</div>
```
Card + search input + `isLoading`/`isError` branches + mobile-cards/desktop-`DataTable` split (lines 89-190) is the shape to copy for the moldes list, swapping `useTenantsAdmin()` for the new `useMoldes()` hook and `relatorioColumns` for new `moldesColumns`.

**Column-defs analog:** `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx` (full file, 119 lines) — plain exported array (`export const relatorioColumns: ColumnDef<TenantAdminSummary>[] = [...]`), no row-action factory function (this is the one column file in the app with zero write actions — same shape needed for a read-only moldes list, or if moldes needs a row action like "editar", fall back to `web/src/app/(dashboard)/plataforma/columns.tsx`'s factory-function shape instead, which does take callbacks per row).

**Checkbox-matrix editor analog:** `RbacTab` in `web/src/app/(dashboard)/settings/page.tsx:755-960+`. Core pieces to copy:
- Module grouping from flat permission list (line 850): `const modules = Array.from(new Set(systemPermissions.map((p) => p.modulo)));`
- Per-cell toggle handler (lines 808-826), guarding on role/permission immutability the same way `ADMIN` is guarded here — apply the equivalent guard for `PLATAFORMA_ADMIN` never being editable as a molde:
```tsx
const handleCheckboxChange = (role: MockRole, permKey: MockPermission) => {
  if (role === "ADMIN") return; // Admin permissions are immutable (always enabled)
  if (!isPlatformAdmin) return;
  const base = effectiveRolePermissions;
  const currentPerms = base[role] || [];
  let nextPerms = currentPerms.includes(permKey)
    ? currentPerms.filter((p) => p !== permKey)
    : [...currentPerms, permKey];
  setLocalRolePermissions({ ...base, [role]: nextPerms });
};
```
- Save handler (lines 828-847): local optimistic state (`localRolePermissions`), `apiFetch` PUT, `toast.success`/`toast.error`, `refetch()`.
- Module-grouped `<table>` markup (lines 919-954): sticky module separator row (`colSpan`), one column per role/molde.
- **Add the non-propagation warning banner** (CONTEXT.md requirement) in the same visual slot as the existing "Nota Importante" info box (lines 903-917) — that box is the direct visual analog; the new one must additionally state the live count of tenants that already instantiated the molde being edited (from the new DTO's `tenantsInstanciados` field).

**Inline creation panel analog:** `web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx` (full file, 241 lines) — `react-hook-form` + `zodResolver`, controlled by parent via `onCancel`/`onSubmit`/`isSubmitting` props, `Card`+`CardHeader`+`CardContent`+`CardFooter` layout, no internal mutation/toast logic (parent owns that). Copy this prop-driven shape for a `CriarMoldePanel` (name field + initial permission checkboxes, no logo/file-upload fields needed).

---

### `web/src/app/(dashboard)/plataforma/page.tsx` — new "Gerir Moldes" entry point (modify in place)

**Existing analog to copy exactly** — the `CardHeader` button block (`page.tsx:163-183`), "Ver Relatório" `Link`+`Button`:
```tsx
<CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 space-y-0">
  <div>
    <CardTitle className="text-xl font-semibold">Tenants Registados</CardTitle>
    <CardDescription>Lista de organizações com acesso à plataforma ALCv.</CardDescription>
  </div>
  <div className="flex items-center gap-2">
    <Button asChild variant="outline" className="text-xs py-1.5 px-3 h-auto flex items-center gap-1.5">
      <Link href="/plataforma/relatorio">
        <FileChartColumn className="h-4 w-4" />
        Ver Relatório
      </Link>
    </Button>
    <Button onClick={() => setIsFormOpen(true)} className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto">
      <Plus className="h-4 w-4" />
      Criar Tenant
    </Button>
  </div>
</CardHeader>
```
Add a third `Button asChild` (or reuse the existing `variant="outline"` slot) pointing `href="/plataforma/moldes"` with a "Gerir Moldes" label, same visual treatment as "Ver Relatório".

---

### `web/src/hooks/use-platform-moldes.ts` (NEW hooks)

**Full analog:** `web/src/hooks/use-platform-admin.ts` (full file, 76 lines) — the TanStack Query + `apiFetch` pattern from Phase 120:
```ts
const TENANTS_LIST_KEY = ["platform", "tenants", "list"] as const;

export function useTenantsAdmin() {
  const enabled = typeof window !== "undefined";
  return useQuery({
    queryKey: TENANTS_LIST_KEY,
    queryFn: () => apiFetch<TenantAdminSummary[]>("/platform/tenants"),
    enabled,
    staleTime: 30_000,
  });
}

export function useCreateTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (payload: SetupInitializeRequest) =>
      apiFetch<{ id: string; nome: string }>("/platform/tenants", {
        method: "POST",
        body: JSON.stringify(payload),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: TENANTS_LIST_KEY });
    },
  });
}

export function useUpdateTenant() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ id, payload }: { id: string; payload: TenantUpdateRequest }) =>
      apiFetch<TenantAdminSummary>(`/platform/tenants/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: JSON.stringify(payload satisfies TenantUpdateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: TENANTS_LIST_KEY });
    },
  });
}
```
Copy this exactly for `useMoldes()` (query, key `["platform", "moldes", "list"]`), `useCreateMolde()` (POST), `useUpdateMolde()` (PUT) — same `invalidateQueries` (never `setQueryData`), same `enabled: typeof window !== "undefined"` SSR guard, same `staleTime: 30_000`.

---

### `web/src/types/platform-moldes.ts` (NEW types)

**Full analog:** `web/src/types/platform-admin.ts` (full file, 28 lines) — plain exported `type` aliases, no interfaces, no runtime validation (Zod schemas live separately in `schemas/` if needed for forms):
```ts
export type TenantAdminSummary = {
  id: string;
  nome: string;
  plano: TenantPlano;
  limiteUtilizadores: number | null;
  ativo: boolean;
  utilizadoresAtivos: number;
};

export type TenantUpdateRequest = {
  plano: TenantPlano;
  limiteUtilizadores: number | null;
};
```
Molde types should mirror this: `MoldeSummary` (`id`, `nome`, `permissoes: string[]`, `tenantsInstanciados: number`), `MoldeUpdateRequest` (`permissoes: string[]`), `MoldeCreateRequest` (`nome`, `permissoes: string[]`).

---

### `web/scripts/verify-consola-moldes.mjs` (NEW structural gate) + `package.json` entry

**Full structural analog:** `web/scripts/verify-relatorio-utilizacao.mjs` (331 lines) and `web/scripts/verify-consola-tenants.mjs` (281 lines) — both zero-dependency Node scripts that read target files as raw text (no import, no type-stripping), strip comments, then run an array of `{ id, descricao, predicate }` assertions.

**Comment-stripping helper to copy** (`verify-relatorio-utilizacao.mjs:53-68`):
```js
function stripComments(source) {
  let out = source;
  out = out.replace(/\{\s*\/\*[\s\S]*?\*\/\s*\}/g, "");
  out = out.replace(/\/\*[\s\S]*?\*\//g, "");
  out = out
    .split("\n")
    .filter((line) => {
      const trimmed = line.trim();
      return !trimmed.startsWith("//") && !trimmed.startsWith("*");
    })
    .join("\n");
  return out;
}
```

**Marker-slice helper to copy** (`verify-relatorio-utilizacao.mjs:75-81`):
```js
function sliceBetweenMarkers(source, startMarker, endMarker) {
  const startIdx = source.indexOf(startMarker);
  if (startIdx === -1) return null;
  const endIdx = source.indexOf(endMarker, startIdx);
  if (endIdx === -1) return null;
  return source.slice(startIdx, endIdx);
}
```

**Assertion + reporting loop to copy** (`verify-relatorio-utilizacao.mjs:97-108` for one assertion shape, `310-328` for the runner):
```js
const assertions = [
  {
    id: "colunas-array-estatico",
    descricao: '...',
    predicate: () => { /* boolean */ },
  },
  // ...
];

let failures = 0;
for (const assertion of assertions) {
  let pass = false;
  let error = null;
  try {
    pass = assertion.predicate();
  } catch (err) {
    error = err;
  }
  if (pass) {
    console.log(`PASS ${assertion.id}`);
  } else {
    failures += 1;
    const motivo = error ? error.message : assertion.descricao;
    console.log(`FAIL ${assertion.id} — ${motivo}`);
  }
}
process.exit(failures === 0 ? 0 : 1);
```

**File-path setup to copy** (`verify-relatorio-utilizacao.mjs:26-45`), pointed instead at `.../plataforma/moldes/page.tsx` and its columns/panel files:
```js
import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const PLATAFORMA_DIR = path.join(__dirname, "..", "src", "app", "(dashboard)", "plataforma");
const PLATAFORMA_PAGE_PATH = path.join(PLATAFORMA_DIR, "page.tsx");
const MOLDES_DIR = path.join(PLATAFORMA_DIR, "moldes");
const MOLDES_PAGE_PATH = path.join(MOLDES_DIR, "page.tsx");
```

**Deliberate scope limits to document** (analog: `verify-relatorio-utilizacao.mjs:16-24`) — state clearly in the new script's header what it CANNOT prove (route actually resolves in-browser, checkbox clicks persist, the non-propagation count is numerically correct, a non-`PLATAFORMA_ADMIN` really gets 403 from the live backend). This "what this gate cannot prove" header block is itself a convention to copy verbatim in structure.

**`package.json` entry to add** (`web/package.json:11-15`, append a new line in the same `verify:*` block):
```json
"verify:consola-moldes": "node scripts/verify-consola-moldes.mjs"
```

---

## Shared Patterns

### `@PreAuthorize` class-level gate (backend, all new controller handlers)
**Source:** `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java:51-55`
**Apply to:** All new `/platform/moldes` handlers — no per-method annotation, the class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` covers them automatically, exactly as it already covers the 3 existing tenant handlers (documented convention from Phase 119/120).

### Tenant-scoped unique constraint (backend, `TenantRole`)
**Source:** `backend/src/main/java/com/lexcv/models/Cliente.java:12-19`
```java
@Table(name = "t_cliente", uniqueConstraints = { @UniqueConstraint(columnNames = {"tenant_id", "documento_numero"}), ... })
```
**Apply to:** `TenantRole` — `@UniqueConstraint(columnNames = {"tenant_id", "nome"})`, per CONTEXT.md decision.

### `@ManyToMany` + `@JoinTable` (backend, `TenantRole.permissions`)
**Source:** `backend/src/main/java/com/lexcv/models/Role.java:24-31` and `backend/src/main/java/com/lexcv/models/User.java:54-61`
**Apply to:** `TenantRole.permissions`, pointed at new join table `t_tenant_role_permission`.

### Manual migration + README inventory (backend)
**Source:** `backend/migrations/124-add-permission-catalogo-columns.sql` + `backend/migrations/README.md` Path B table (lines 138-153) and "Adding a new migration" section (lines 253-260)
**Apply to:** `backend/migrations/126-*.sql` — must add a README row in the same commit, in strict ascending numeric order (as row 15, after `125`).

### TanStack Query hook shape + `apiFetch` (frontend, all new hooks)
**Source:** `web/src/hooks/use-platform-admin.ts` (full file)
**Apply to:** `web/src/hooks/use-platform-moldes.ts` — same query-key-array convention, same `invalidateQueries` (never manual cache writes), same SSR `enabled` guard.

### Page-guard ordering: `!isFetched` before role check (frontend, all new gated pages)
**Source:** `web/src/app/(dashboard)/plataforma/relatorio/page.tsx:38-51` (citing WR-03, Phase 120 code review)
**Apply to:** `/plataforma/moldes/page.tsx` — same two-step guard, same order, non-negotiable per the cited review finding.

### Structural verification gate shape (frontend, Node-only)
**Source:** `web/scripts/verify-relatorio-utilizacao.mjs`, `web/scripts/verify-consola-tenants.mjs`
**Apply to:** `web/scripts/verify-consola-moldes.mjs` + a new `verify:consola-moldes` line in `web/package.json`'s `scripts` block.

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `TenantRole.moldeId` (nullable FK-as-provenance, not a live JPA association) | model field | n/a | No existing entity in the codebase has a nullable FK column that is deliberately *not* mapped as a navigable `@ManyToOne`/`@OneToOne` association. Closest partial precedent is `Permission`'s plain nullable `@Column` fields (no relationship at all) — composed guidance given inline above, not a literal copy target. |
| Live "N tenants have instantiated this molde" counter | controller/DTO computation | CRUD (aggregate) | No existing count query is grouped by a provenance/molde id; `userRepository.countByTenantIdAndAtivoTrue` (used in `toSummary`) is the closest shape (a live `COUNT` derived query) but counts by `tenantId`, not by a foreign "molde" id — the new `TenantRoleRepository` needs a new `countByMoldeId(Integer moldeId)` derived query following that same naming convention. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{models,repositories,services,controllers,dtos,seed}`, `backend/migrations/`, `backend/src/test/java/com/lexcv/{services,controllers}`, `web/src/app/(dashboard)/plataforma/`, `web/src/app/(dashboard)/settings/page.tsx`, `web/src/hooks/`, `web/src/types/`, `web/scripts/`
**Files scanned:** ~30 read directly (full or targeted), directory listings across `models/`, `repositories/`, `dtos/`, `plataforma/`
**Pattern extraction date:** 2026-09-20
