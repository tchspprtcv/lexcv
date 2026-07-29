# Phase 120: Frontend — Consola de Administração de Tenants - Pattern Map

**Mapped:** 2026-07-29
**Files analyzed:** 12 (6 backend, 6 frontend)
**Analogs found:** 11 / 12 (1 partial-only — see "No Analog Found")

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/models/Tenant.java` | model | CRUD | `backend/src/main/java/com/lexcv/models/User.java` (`ativo` field) | role-match |
| `backend/migrations/120-add-tenant-ativo.sql` | migration | batch | `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` | exact |
| `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` | middleware | request-response | itself (existing `user.getAtivo()` re-check, line 43) | exact |
| `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` (extend) | controller | CRUD | `backend/src/main/java/com/lexcv/controllers/AdminController.java` | role-match |
| `backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java` (new) | model (DTO) | transform | `UserSummaryResponse.java` + `TenantProvisionResponse.java` | role-match |
| `backend/src/main/java/com/lexcv/dtos/TenantUpdateRequest.java` (new, discretionary) | model (DTO) | transform | `SetupInitializeRequest.java` | partial |
| `web/src/types/auth.ts` (extend `Role` union) | config/types | transform | itself | exact |
| `web/src/components/shared/dashboard-shell.tsx` (extend) | component (shell) | request-response | itself (`NAV`/`SidebarNav` wiring) + `settings/page.tsx`'s `isAdmin` line | role-match |
| `web/src/app/(dashboard)/plataforma/page.tsx` (new) | component (route) | CRUD | `clientes/page.tsx` + `settings/page.tsx`'s `UserManagementTab` | role-match |
| `web/src/app/(dashboard)/plataforma/columns.tsx` (new) | component (column defs) | transform | `clientes/columns.tsx` | exact |
| `web/src/hooks/use-platform-admin.ts` (new) | hook | CRUD | `web/src/hooks/use-clientes.ts` | exact |
| `web/src/types/platform-admin.ts` (new) | config/types | transform | `web/src/types/setup.ts` | role-match |

**Not modified (explicitly, per UI-SPEC):** `web/src/components/shared/sidebar-nav.tsx` — do NOT touch its filter logic. `DashboardShell` builds a derived nav array (`NAV` vs `[...NAV, platformNavItem]`) and passes that to `<SidebarNav nav={...} />`; `SidebarNav` itself never learns about `PLATAFORMA_ADMIN`.

**Zod schema for Criar Tenant:** no new file recommended — the form fields (`clientName`/`adminEmail`/`adminPassword`/`confirmPassword`) are identical to `web/src/schemas/setup.ts`'s `setupSchema`/`SetupFormValues`. Import and reuse directly (`import { setupSchema, type SetupFormValues, strongPasswordPattern } from "@/schemas/setup";`). Only create `web/src/schemas/platform-admin.ts` if the planner wants a semantically distinct name — the rules must stay byte-identical either way (UI-SPEC: "do not re-derive the regex").

---

## Pattern Assignments

### `backend/src/main/java/com/lexcv/models/Tenant.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/lexcv/models/User.java`

Current `Tenant.java` (full file, 55 lines) has no `ativo` field yet. `User.java` already has the exact field/semantics to replicate:

**Field pattern to copy** (`User.java` lines 36-38):
```java
@Column(nullable = false)
@Builder.Default
private Boolean ativo = true;
```

Apply the same shape to `Tenant.java` (insert near `plano`/`limiteUtilizadores`, `Tenant.java` lines 35-45):
```java
@Enumerated(EnumType.STRING)
@Column(name = "plano")
private TenantPlano plano;

@Column(name = "limite_utilizadores")
private Integer limiteUtilizadores;
```
— i.e. add `@Column(nullable = false)` + `@Builder.Default private Boolean ativo = true;` directly after these. Note `DatabaseSeeder.seedTenantPlataforma()` (`Tenant.builder().nome("LexCV").build()`) relies on `@Builder.Default` to get `ativo=true` for free on the reserved tenant — do not make callers set it explicitly.

---

### `backend/migrations/120-add-tenant-ativo.sql` (migration, batch)

**Analog:** `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` (full file, 31 lines) — same table (`t_tenant`), same "add column(s) + backfill" shape, most recent migration touching this exact entity.

**Pattern to copy verbatim** (header comment convention, lines 1-19 of 117's migration):
```sql
-- Phase 117 (PLAN-01): add plano/limite_utilizadores columns to t_tenant
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that adds the `plano`/`limiteUtilizadores` fields to the `Tenant`
-- entity (backend/src/main/java/com/lexcv/models/Tenant.java).
--
-- Why: `application-prod.yml` sets `ddl-auto: validate` in production (dev/CI use
-- `ddl-auto: update`, which auto-adds these columns locally from the entity mapping).
-- ...
-- There is no automated migration runner in this repository (no Flyway, no Liquibase --
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is
-- therefore manual: run it once against each environment's database (staging/prod)
-- before that environment picks up the deploy that introduces these fields.
```

**Column-add + backfill pattern** (lines 28-31):
```sql
ALTER TABLE t_tenant ADD COLUMN plano VARCHAR(255);
ALTER TABLE t_tenant ADD COLUMN limite_utilizadores INTEGER;

UPDATE t_tenant SET plano = 'ENTERPRISE' WHERE plano IS NULL;
```
For `120-add-tenant-ativo.sql`, the equivalent is a `BOOLEAN NOT NULL DEFAULT TRUE` column — Postgres backfills existing rows automatically via `DEFAULT`, so an explicit `UPDATE` is optional but harmless for clarity:
```sql
ALTER TABLE t_tenant ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
```
Match `User.ativo`'s exact naming (`ativo`, not `suspenso`) per CONTEXT.md's own decision.

---

### `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` (middleware, request-response)

**Analog:** itself — extend in place, do not restructure.

**Current per-request re-validation** (full file is 99 lines; the exact hook, lines 32-83):
```java
private final JwtTokenProvider tokenProvider;
private final UserRepository userRepository;

@Override
protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
    try {
        String jwt = getJwtFromRequest(request);

        if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
            Claims claims = tokenProvider.getClaimsFromToken(jwt);
            UUID userId = UUID.fromString(claims.getSubject());

            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getAtivo()) {
                Set<String> roles = user.getRoles().stream()
                        .map(Role::getNome)
                        .collect(Collectors.toSet());
                // ... build principal, set SecurityContextHolder ...
            } else if (user == null) {
                logger.warn("JWT valid but user " + userId + " not found");
            } else {
                logger.warn("JWT valid but user " + userId + " is deactivated");
            }
        }
    } catch (Exception ex) {
        logger.error("Could not set user authentication in security context", ex);
    }

    filterChain.doFilter(request, response);
}
```

**Extension required:** inject `TenantRepository` (constructor injection, `@RequiredArgsConstructor` already generates it — just add the field), then change line 43's condition from `user != null && user.getAtivo()` to also require the tenant to be active:
```java
private final TenantRepository tenantRepository; // new field

// ... inside doFilterInternal, replace the condition:
User user = userRepository.findById(userId).orElse(null);
Tenant tenant = user != null ? tenantRepository.findById(user.getTenantId()).orElse(null) : null;
if (user != null && user.getAtivo() && tenant != null && tenant.getAtivo()) {
    // unchanged principal-building code
} else if (user == null) {
    logger.warn("JWT valid but user " + userId + " not found");
} else if (!user.getAtivo()) {
    logger.warn("JWT valid but user " + userId + " is deactivated");
} else {
    logger.warn("JWT valid but tenant " + user.getTenantId() + " is suspended");
}
```
This is the exact mechanism CONTEXT.md calls out as already proven for user-level deactivation — no new filter, no new bean, just widen the existing boolean check and its `else` branches. `TenantRepository` already exists (see below) and is trivially injectable since `JwtAuthenticationFilter` is a `@Component` with `@RequiredArgsConstructor`.

---

### `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` (controller, CRUD — extend)

**Current file** (full, 71 lines) — class-level gate, `@RequiredArgsConstructor`, one existing endpoint:
```java
@RestController
@RequestMapping("/api/v1/platform")
@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final SetupService setupService;

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
}
```
New endpoints add `TenantRepository` (+ `UserRepository`) to the constructor-injected fields, same as `AdminController` already does.

**Analog for the list endpoint — `AdminController.listUsers`** (`AdminController.java` lines 68-97): fetch all rows, map each to a purpose-built response DTO, return `ResponseEntity.ok(responses)`:
```java
@GetMapping("/users")
public ResponseEntity<?> listUsers() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

    List<User> users = userRepository.findByTenantId(principal.getTenantId());
    List<UserResponse> responses = users.stream().map(u -> {
        // ... build DTO fields off u ...
        return UserResponse.builder()/*...*/.build();
    }).collect(Collectors.toList());

    return ResponseEntity.ok(responses);
}
```
For `GET /api/v1/platform/tenants`, replace `userRepository.findByTenantId(...)` with `tenantRepository.findAll()` (no tenant scoping here — `PlatformAdminController` is deliberately NOT tenant-scoped, per its own class doc-comment), and for each `Tenant`, call `userRepository.countByTenantIdAndAtivoTrue(tenant.getId())` to fill the active-user count field on `TenantAdminSummaryResponse`.

**Analog for the reserved-tenant immutability guard (suspend rejection) — `AdminController.deleteUser`'s self-guard** (lines 328-336):
```java
@DeleteMapping("/users/{id}")
public ResponseEntity<?> deleteUser(@PathVariable UUID id) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();

    if (principal.getUserId().equals(id)) {
        return ResponseEntity.badRequest().body(Map.of("message", "Não é permitido apagar a sua própria conta de utilizador administrador."));
    }
    // ...
}
```
This is the closest "reject an action against a specific reserved record with a 400 + message" shape (an actual rejected response, not a silent skip). Apply the same shape to the suspend endpoint:
```java
Tenant tenant = tenantRepository.findById(id).orElse(null);
if (tenant == null) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Tenant não encontrado."));
}
if ("LexCV".equals(tenant.getNome())) {
    return ResponseEntity.badRequest().body(Map.of("message", "Não é possível suspender o tenant da plataforma (LexCV)."));
}
```
CONTEXT.md also cites `AdminController.updateRbac`'s `"ADMIN".equals(roleName)` line (lines 399-408) as the conceptual precedent for reserved-identity immutability — that one is a silent `continue` (skip), useful as the "reserved identifier gets special-cased by literal string comparison" idea, but `deleteUser`'s guard above is the better structural match since suspend must actively reject with an error response.

**Analog for the boolean-toggle body-validation (suspend/reactivate payload) — `AdminController.updateUser`'s `ativo` handling** (lines 250-269):
```java
if (body.containsKey("ativo")) {
    if (!(body.get("ativo") instanceof Boolean novoAtivo)) {
        return ResponseEntity.badRequest().body(Map.of("message", "O campo ativo deve ser um valor booleano."));
    }
    // ... apply transition-specific side effects ...
    user.setAtivo(novoAtivo);
}
```
Reuse this exact `instanceof Boolean` guard shape if the suspend endpoint accepts a raw `Map<String, Object> body` rather than a typed DTO.

**Analog for the limit-check helper style — `AdminController.limiteUtilizadoresExcedido`** (lines 119-129) — not reused directly by this phase, but demonstrates the established "small private helper returning `Optional<ResponseEntity<?>>`, called from multiple endpoints" idiom already in this controller if the adjust/suspend endpoints need shared validation logic.

**Convention observed for HTTP verb choice** (not prescriptive — CONTEXT.md leaves this to discretion, but useful precedent): this codebase uses `@PatchMapping` specifically for narrow, single-purpose state-toggle sub-resources (`NotificacaoController`: `/{id}/lida`, `/{id}/snooze`; `ResourceController`: `/processos/{id}/prazos/{prazoId}/concluido`), and `@PutMapping` for whole-resource field updates (`AdminController`: `/users/{id}`, `/rbac`). A `PATCH /tenants/{id}/ativo` (toggle) + `PUT /tenants/{id}` (plano/limite) split would match this existing convention exactly; a single combined `PATCH /tenants/{id}` accepting all three fields is also internally consistent (CONTEXT.md explicitly allows either).

---

### `backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java` (new DTO, transform)

**Analogs:** `UserSummaryResponse.java` (full file, 26 lines — minimal projection DTO with doc-comment explaining what's deliberately excluded) and `TenantProvisionResponse.java` (full file, 27 lines — doc-comment explicitly stating "never serialize the entity directly").

```java
package com.lexcv.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.UUID;

/**
 * [Same "never raw entity" doc-comment convention as TenantProvisionResponse —
 * explain what this DTO is for and why it excludes some Tenant fields.]
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantAdminSummaryResponse {
    private UUID id;
    private String nome;
    private String plano;             // or TenantPlano — UserResponse-style Set<String> vs enum precedent both exist
    private Integer limiteUtilizadores; // null = sem limite, same semantics as Tenant.limiteUtilizadores
    private Boolean ativo;
    private Long utilizadoresAtivos;   // from userRepository.countByTenantIdAndAtivoTrue(tenant.getId())
}
```
Every controller response in this codebase is a purpose-built DTO, never a raw entity (`ResourceController`, `AdminController`, `PlatformAdminController` all follow this without exception) — this is a hard, universal convention, not just a suggestion.

---

### `backend/src/main/java/com/lexcv/dtos/TenantUpdateRequest.java` (new DTO, transform — discretionary but recommended)

**Analog:** `SetupInitializeRequest.java` (full file, 14 lines) — the ONLY existing typed request-body DTO in this codebase; every other controller (`AdminController.createUser/updateUser`, `AdminController.updateRbac`) takes `@RequestBody Map<String, Object> body` and manually pulls fields out.

```java
package com.lexcv.dtos;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SetupInitializeRequest {
    private String clientName;
    private String logo;
    private String adminEmail;
    private String adminPassword;
}
```

CONTEXT.md's own reasoning ("Jackson will already reject invalid enum values with a 400 by default — no need to hand-roll that check") only holds if `plano` is deserialized into a typed `TenantPlano` field — which requires a typed request DTO, not a raw `Map`. Recommended shape:
```java
@Getter
@Setter
public class TenantUpdateRequest {
    private TenantPlano plano;
    private Integer limiteUtilizadores; // null = sem limite
}
```
If the planner instead folds `ativo` into the same combined endpoint (CONTEXT.md's discretion point), add `private Boolean ativo;` here too — but see the `updateUser`-style `instanceof Boolean` guard above if `ativo` stays on a raw-`Map` endpoint instead.

---

### `web/src/types/auth.ts` (config/types — extend)

**Current file** (full, 31 lines):
```typescript
export type Role = "ADMIN" | "TECNICO" | "ADVOGADO" | "ASSISTENTE";
```
Extend to:
```typescript
export type Role = "ADMIN" | "TECNICO" | "ADVOGADO" | "ASSISTENTE" | "PLATAFORMA_ADMIN";
```
No other change needed in this file — `MeResponse.roles: Role[]` already types the array that both `dashboard-shell.tsx` and `plataforma/page.tsx` will check against.

---

### `web/src/components/shared/dashboard-shell.tsx` (component/shell — extend)

**Current NAV array + wiring** (lines 60-68, 120-145):
```typescript
const NAV: NavItem[] = [
  { href: "/dashboard", label: "Dashboard", icon: Home },
  { href: "/clientes", label: "Clientes", icon: Users, requiredPermission: "clientes:view" },
  { href: "/processos", label: "Processos", icon: Scale, requiredPermission: "processos:view" },
  { href: "/agenda", label: "Agenda", icon: Calendar, requiredPermission: "agenda:view" },
  { href: "/documentos", label: "Documentos", icon: FileText, requiredPermission: "documentos:view" },
  { href: "/financeiro", label: "Financeiro", icon: Wallet, requiredPermission: "financeiro:view" },
  { href: "/pareceres", label: "Pareceres", icon: ScrollText, requiredPermission: "pareceres:view" },
];

export function DashboardShell({ children }: { children: React.ReactNode }) {
  // ...
  const me = useMe();
  // ...
  <SidebarNav
    nav={NAV}
    pathname={pathname}
    permissions={me.data?.permissions}
    onNavigate={() => setDrawerOpen(false)}
  />
  // (repeated verbatim at the mobile <Sheet> call site)
```
`Building2` is already imported (line 10) — no new icon import needed.

**Role-check analog — `settings/page.tsx`'s `isAdmin`** (line 56):
```typescript
const isAdmin = me?.roles?.includes("ADMIN");
```
Apply the same one-liner shape inside `DashboardShell`, then derive the nav array passed to BOTH `<SidebarNav>` call sites (desktop `<aside>` and mobile `<Sheet>` — the file currently duplicates the `<SidebarNav nav={NAV} .../>` JSX at both call sites, so the derived variable must be computed once and reused at both, not recomputed differently):
```typescript
const isPlatformAdmin = me.data?.roles?.includes("PLATAFORMA_ADMIN");
const platformNavItem: NavItem = { href: "/plataforma", label: "Plataforma", icon: Building2 };
const navItems = isPlatformAdmin ? [...NAV, platformNavItem] : NAV;
// then: <SidebarNav nav={navItems} .../> at both call sites, replacing nav={NAV}
```
`NavItem` type (no change needed, `requiredPermission` stays optional) is exported from `sidebar-nav.tsx`.

---

### `web/src/app/(dashboard)/plataforma/page.tsx` (new route, CRUD)

**Analog 1 — page-level RBAC guard, `clientes/page.tsx`** (lines 25-46):
```typescript
export default function ClientesPage() {
  const permissions = usePermissions();
  const canViewClientes = permissions.can.view("clientes");
  // ...
  if (permissions.isFetched && !canViewClientes) {
    return (
      <AccessDeniedState
        description="Não tem permissão para consultar o módulo de clientes."
        backHref="/dashboard"
      />
    );
  }

  return <ClientesPageContent .../>;
}
```
UI-SPEC's own worded contract for this page uses `useMe()` directly instead of `usePermissions()` (role check, not scope check — `PLATAFORMA_ADMIN` carries zero scoped permissions):
```typescript
const me = useMe();
if (me.isFetched && !me.data?.roles?.includes("PLATAFORMA_ADMIN")) {
  return (
    <AccessDeniedState
      description="Não tem permissão para aceder à consola de administração de tenants."
      backHref="/dashboard"
    />
  );
}
```
`AccessDeniedState` (full file, `web/src/components/shared/access-denied-state.tsx`, 42 lines) takes `{ title?, description?, backHref?, backLabel? }` — same props shape, no change needed to the component itself.

**Analog 2 — Card shape with no KPI row, create-panel toggle, search Input, table — `settings/page.tsx`'s `UserManagementTab`** (lines 180-755, see full excerpts below). This is the CONTEXT.md-named closest analog for "Criar Tenant" specifically because it has no KPI hero row, matching this screen's simpler shape (unlike `clientes/page.tsx`, which DOES have a 4-card KPI row at lines 304-332 — do NOT copy that KPI section).

State shape to replicate (`UserManagementTab`, lines 180-190):
```typescript
const [editingUser, setEditingUser] = React.useState<Partial<MockUser> | null>(null);
const [isFormOpen, setIsFormOpen] = React.useState(false);
const [searchTerm, setSearchTerm] = React.useState("");
```
For `plataforma/page.tsx`: `isFormOpen` (create-panel toggle) + `searchTerm` map directly; `editingTenant`/`isEditDialogOpen` are separate state for the Editar Dialog (see Analog 4 below — UI-SPEC is explicit the edit dialog must NOT reuse the inline-panel mechanic).

List-Card CTA + tooltip-when-disabled pattern (`UserManagementTab`, lines 381-427) — directly reusable shape for "Criar Tenant" (this screen has no limit-based disabling, so the `atUserLimit` ternary collapses to always showing the plain enabled Button, no Tooltip needed here):
```tsx
{!isFormOpen || !editingUser ? (
  <Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
    <CardHeader className="flex flex-row items-center justify-between space-y-0">
      <div>
        <CardTitle className="text-xl font-semibold">Utilizadores Registados</CardTitle>
        <CardDescription>Lista de profissionais com credenciais de acesso ao sistema LexCV.</CardDescription>
      </div>
      <Button onClick={handleCreateClick} className="bg-blue-600 hover:bg-blue-700 text-white flex items-center gap-1.5 shadow-sm text-xs py-1.5 px-3 h-auto">
        <Plus className="h-4 w-4" />
        Novo Utilizador
      </Button>
    </CardHeader>
    <CardContent className="space-y-4">
      <Input placeholder="Pesquisar utilizador por nome ou email..." value={searchTerm} onChange={(e) => setSearchTerm(e.target.value)} className="bg-slate-50 dark:bg-slate-950 pr-8" />
      {/* table */}
    </CardContent>
  </Card>
) : (
  /* Edit/Create Form Panel — see below */
)}
```

Create-panel close/submit chrome (`UserManagementTab`, lines 570-591, 732-750 — the "X" close button + footer):
```tsx
<Card className="border-slate-200 dark:border-slate-800 bg-white/50 dark:bg-slate-900/50 backdrop-blur-sm rounded-xl">
  <form onSubmit={handleFormSubmit}>
    <CardHeader className="flex flex-row items-center justify-between space-y-0">
      <div>
        <CardTitle className="text-xl font-semibold">{editingUser.id ? "Editar Utilizador" : "Registar Novo Utilizador"}</CardTitle>
        <CardDescription>...</CardDescription>
      </div>
      <Button type="button" variant="ghost" onClick={() => setIsFormOpen(false)} className="h-8 w-8 p-0 rounded-full border border-slate-200 dark:border-slate-800" aria-label="Fechar">
        <X className="h-4 w-4" />
      </Button>
    </CardHeader>
    <CardContent className="space-y-6">{/* fields */}</CardContent>
    <CardFooter className="flex justify-end gap-3 pt-6 border-t border-slate-200 dark:border-slate-800">
      <Button type="button" variant="outline" onClick={() => setIsFormOpen(false)} className="border-slate-200 dark:border-slate-700">
        <X className="h-4 w-4" />
        Cancelar
      </Button>
      <Button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white shadow-sm">
        <Save className="h-4 w-4" />
        Guardar Utilizador
      </Button>
    </CardFooter>
  </form>
</Card>
```
**Do NOT copy** `handleDeleteClick`/`handleFormSubmit`'s `setTimeout(() => window.location.reload(), 1000)` (lines 249-251, 306-309) — UI-SPEC explicitly calls this out as a legacy mock-database-era artifact. Use `queryClient.invalidateQueries` instead (see `use-platform-admin.ts` below).

**At-limit / stacked-caption visual language** (reused for the `utilizadores` column's "limite atingido" state — `UserManagementTab` lines 196-211):
```typescript
const activeUserCount = users?.filter((u) => u.ativo === true).length ?? 0;
const tenantUserLimit = me?.tenant_limite_utilizadores ?? null;
const atUserLimit = tenantUserLimit !== null && activeUserCount >= tenantUserLimit;
const userCountLabel =
  tenantUserLimit === null
    ? `${activeUserCount} utilizadores`
    : atUserLimit
      ? `${activeUserCount}/${tenantUserLimit} utilizadores · limite atingido`
      : `${activeUserCount}/${tenantUserLimit} utilizadores`;
```

**Analog 3 — DataTable + mobile card block, `clientes/page.tsx`** (lines 15, 476-544):
```tsx
import { DataTable } from "@/components/shared/data-table/data-table";
import { columns } from "./columns";
// ...
{/* Mobile: cards empilhados */}
<div className="md:hidden divide-y divide-slate-200 dark:divide-slate-800">
  {clientes.data.map((c) => {
    const initials = c.nome.split(" ").filter(Boolean).slice(0, 2).map((w) => w[0]).join("").toUpperCase();
    return (
      <div key={c.id} className="p-4">
        <div className="flex items-center gap-3">
          <div className="h-10 w-10 rounded-md flex-shrink-0 bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold">
            {initials}
          </div>
          <div className="min-w-0 flex-1">
            <Link href={...} className="font-bold text-slate-900 dark:text-white ...">{c.nome}</Link>
          </div>
          {/* status/plano Badges inline */}
        </div>
        <div className="mt-3 pl-[52px] flex items-center gap-1">
          {/* Tooltip-wrapped h-12 w-12 p-0 action buttons */}
        </div>
      </div>
    );
  })}
</div>

{/* Desktop: DataTable */}
<div className="hidden md:block">
  <DataTable columns={clienteColumns} data={clientes.data} getRowId={(c) => c.id} />
</div>
```
Note: `plataforma/page.tsx` — per UI-SPEC — uses **plain text, not a `<Link>`**, for the tenant name (no detail page this phase); everything else in this block transfers directly, action icons swap from `Eye`/`Pencil` to `Pencil`/`Lock`/`Unlock`.

**Analog 4 — Editar Tenant Dialog (modal form) and Suspender/Reativar AlertDialog — `financeiro/[id]/page.tsx`** (Dialog+form, lines 284-348) and `agenda/[id]/page.tsx` (AlertDialog+destructive action, lines 187-238):
```tsx
<Dialog open={editOpen} onOpenChange={setEditOpen}>
  <DialogTrigger asChild>
    <Button variant="outline"><Pencil className="h-4 w-4" />Editar</Button>
  </DialogTrigger>
  <DialogContent>
    <DialogHeader><DialogTitle>Editar honorário</DialogTitle></DialogHeader>
    <form className="space-y-4" onSubmit={editForm.handleSubmit(onSubmitEdit)}>
      {/* Input/NativeSelect fields */}
      <DialogFooter>
        <DialogClose asChild>
          <Button type="button" variant="outline"><X className="h-4 w-4" />Cancelar</Button>
        </DialogClose>
        <Button type="submit" disabled={editForm.formState.isSubmitting || updateHonorario.isPending}>
          <Save className="h-4 w-4" />
          {editForm.formState.isSubmitting || updateHonorario.isPending ? "A guardar..." : "Guardar"}
        </Button>
      </DialogFooter>
    </form>
  </DialogContent>
</Dialog>
```
(This example uses `react-hook-form` + `zodResolver` — reasonable for 2 simple fields to skip and use plain `useState` instead, closer to `UserManagementTab`'s style; either is consistent with existing precedent.)

```tsx
<AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
  <AlertDialogTrigger asChild>
    <Button type="button" variant="secondary" className="border border-red-300 text-red-600 hover:bg-red-50 ...">
      <Trash2 className="h-4 w-4" />Apagar
    </Button>
  </AlertDialogTrigger>
  <AlertDialogContent>
    <AlertDialogHeader>
      <AlertDialogTitle>Apagar evento</AlertDialogTitle>
      <AlertDialogDescription>...</AlertDialogDescription>
    </AlertDialogHeader>
    <AlertDialogFooter>
      <AlertDialogCancel disabled={isDeleting}>Cancelar</AlertDialogCancel>
      <AlertDialogAction
        disabled={isDeleting}
        onClick={(e) => { e.preventDefault(); void handleDeleteSeries(); }}
        className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
      >
        {del.isPending ? "A apagar..." : "Apagar evento"}
      </AlertDialogAction>
    </AlertDialogFooter>
  </AlertDialogContent>
</AlertDialog>
```
For Suspender, swap `className` to the UI-SPEC-mandated literal `bg-red-600 hover:bg-red-700 text-white` (first solid-destructive button in this codebase, per UI-SPEC Color section — a deliberate deviation from this analog's `bg-destructive` token class); for Reativar, `bg-emerald-600 hover:bg-emerald-700 text-white`. The Tooltip+disabled-`<span tabIndex={0}>` composition for the "LexCV" row's disabled Suspender icon is at `settings/page.tsx` lines 401-417 (already excerpted above) and `pareceres/[id]/page.tsx` (2nd known site, per grep) — same composition, wrap the disabled `Button` in `<span tabIndex={0}>` inside `<TooltipTrigger asChild>`.

**Column defs / row-actions cell** — see `plataforma/columns.tsx` below (same file family, direct dependency of this page).

---

### `web/src/app/(dashboard)/plataforma/columns.tsx` (new, transform)

**Analog:** `web/src/app/(dashboard)/clientes/columns.tsx` (full file, 245 lines).

**Nome cell with initials avatar** (lines 120-168 — copy the avatar classes verbatim per UI-SPEC):
```tsx
{
  id: "nome",
  accessorKey: "nome",
  enableHiding: false,
  header: ({ column }) => <DataTableColumnHeader column={column} title="Nome / Razão Social" />,
  cell: ({ row }) => {
    const cliente = row.original;
    const initials = cliente.nome.split(" ").filter(Boolean).slice(0, 2).map((p) => p[0]).join("").toUpperCase();
    return (
      <div className="flex items-center gap-3">
        <div className="h-10 w-10 rounded-md bg-blue-50 dark:bg-blue-500/10 text-blue-700 dark:text-blue-400 border border-blue-100 dark:border-blue-500/20 flex items-center justify-center text-xs font-bold shadow-sm">
          {initials}
        </div>
        <div className="min-w-0">
          <Link href={...} className="font-bold text-slate-900 dark:text-white hover:text-blue-600 ...">{cliente.nome}</Link>
        </div>
      </div>
    );
  },
},
```
For `plataforma/columns.tsx`: same avatar block, but the name renders as plain bold text (`<span>`, not `<Link>`) plus a conditional `<Badge variant="outline">Plataforma</Badge>` under the name when `row.original.nome === "LexCV"`.

**Badge column, raw enum casing** (lines 169-183 — the `tipo` column is the exact precedent for `plano`):
```tsx
{
  id: "tipo",
  accessorFn: (cliente) => (cliente.tipo ?? "").toUpperCase(),
  header: ({ column }) => <DataTableColumnHeader column={column} title="Tipo" />,
  cell: ({ row }) => {
    const tipo = (row.original.tipo ?? "").toUpperCase();
    const badgeVariant = tipo === "PARTICULAR" ? "blue" : tipo === "EMPRESA" ? "purple" : "gray";
    return <Badge variant={badgeVariant} className="font-bold tracking-wide">{tipo || "—"}</Badge>;
  },
},
```
For `plano`: `STARTER` → `"gray"`, `STANDARD` → `"purple"`, `ENTERPRISE` → `"amber"` (per UI-SPEC), same `font-bold tracking-wide` class.

**Ações cell, per-row hook via inline component** (lines 20-113 — the exact reason a separate component is needed instead of an inline `cell` function: TanStack column defs are plain objects, cannot call hooks):
```tsx
function ClienteAcoesCell({ cliente, canEditClientes }: { cliente: Cliente; canEditClientes: boolean }) {
  const del = useDeleteCliente(cliente.id);
  // ...
  return (
    <div className="inline-flex items-center gap-1">
      <Tooltip>
        <TooltipTrigger asChild>
          <Button size="sm" variant="ghost" aria-label="Ver detalhes" className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
            <Eye className="h-4 w-4" />
          </Button>
        </TooltipTrigger>
        <TooltipContent>Ver detalhes</TooltipContent>
      </Tooltip>
      {/* ...more Tooltip-wrapped action buttons... */}
    </div>
  );
}
```
For `TenantAcoesCell`: two icon buttons (Editar always enabled; Suspender/Reativar toggle disabled+tooltip-guarded for "LexCV"), same `h-9 w-9 p-0` sizing, same Tooltip-wrap-per-button shape. Editar opens the Dialog state lifted from the parent page (needs a callback prop, e.g. `onEdit: (tenant) => void`, since Dialog open-state for Editar most naturally lives in the page, not per-row — unlike the AlertDialog confirm, which CAN be self-contained per-row like `ClienteAcoesCell`'s own delete-confirm).

**Badge column, humanized boolean status** — mirrors the `numero_cliente`/`avencado` badge cluster convention (lines 150-163) and the `ativo` mobile-card convention (`clientes/page.tsx` line 494: `<Badge variant={c.ativo ? "green" : "gray"}>{c.ativo ? "Ativo" : "Inativo"}</Badge>`) — for `estado`: `Ativo` → `variant="green"`, `Suspenso` → `variant="red"` (UI-SPEC's own spec, red not gray, since suspended is a stronger negative signal than merely inactive).

**Badge variants available** (`web/src/components/ui/badge.tsx`, full file, all needed variants already exist — zero new variants needed): `default | secondary | outline | blue | green | amber | red | purple | gray`.

---

### `web/src/hooks/use-platform-admin.ts` (new, CRUD)

**Analog:** `web/src/hooks/use-clientes.ts` (full file, 267 lines) — the idiomatic, current TanStack Query pattern this codebase wants (explicitly preferred by UI-SPEC over `use-admin.ts`'s consumer's `window.location.reload()`).

**List query pattern** (lines 28-48):
```typescript
export function useClientes(filters: ClientesListFilters) {
  const enabled = typeof window !== "undefined";
  // ...destructure/trim filters...
  return useQuery({
    queryKey: ["clientes", "list", q, nome, nif, tipo, ativo, localidade, createdFrom, createdTo],
    queryFn: () => apiFetch<Cliente[]>(`/clientes${buildClientesSearch({...})}`),
    enabled,
    staleTime: 30_000,
  });
}
```
For `useTenantsAdmin()`: `apiFetch<TenantAdminSummary[]>("/platform/tenants")`, `queryKey: ["platform", "tenants", "list"]`, no filters needed server-side (search is client-side per UI-SPEC's single `nome`-only search — mirrors how `DataTable` is documented to do sort/paginate over an already-fetched array, filtering owned by the page's own `useState`).

**Create mutation pattern** (lines 73-86):
```typescript
export function useCreateCliente() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ClienteCreateRequest) =>
      apiFetch<Cliente>("/clientes", {
        method: "POST",
        body: JSON.stringify(payload satisfies ClienteCreateRequest),
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["clientes", "list"] });
    },
  });
}
```
For `useCreateTenant()`: `POST /platform/tenants`, invalidate `["platform", "tenants", "list"]` on success — this satisfies UI-SPEC's explicit instruction ("Do not copy `UserManagementTab`'s `window.location.reload()`... Follow [the `use-clientes.ts`] pattern").

**Update mutation pattern** (lines 88-104):
```typescript
export function useUpdateCliente(id: string) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ClienteUpdateRequest) =>
      apiFetch<Cliente>(`/clientes/${encodeURIComponent(id)}`, {
        method: "PUT",
        body: JSON.stringify(payload satisfies ClienteUpdateRequest),
      }),
    onSuccess: async (updated) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["clientes", "list"] }),
        queryClient.setQueryData(["clientes", "detail", id], updated),
      ]);
    },
  });
}
```
For `useUpdateTenant(id)` (adjust plano/limite) and `useSuspendTenant(id)`/`useReactivateTenant(id)` (or one combined `useUpdateTenantStatus(id)`): same shape, `PATCH`/`PUT` per whatever verb the planner picks in the controller, invalidate `["platform", "tenants", "list"]` (no per-tenant `"detail"` cache exists in this UI, so `setQueryData` isn't needed — the whole list re-fetches and re-renders).

**Note on `use-admin.ts`:** technically a working analog for the mutation/invalidate shape (`web/src/hooks/use-admin.ts`, lines 18-37), but its type imports (`MockUser`, `MockRolePermissions`, `MockPermissionDef` from `@/server/mock-db`) are a legacy artifact per `CLAUDE.md`'s "Legacy / ignore" section — do not import from `@/server/mock-db` in the new hook file; use fresh types from `web/src/types/platform-admin.ts` instead (see below).

---

### `web/src/types/platform-admin.ts` (new, transform)

**Analog:** `web/src/types/setup.ts` (full file, 16 lines) for the plain-type-alias style, and the domain-per-file convention (`clientes.ts`, `processos.ts`, `dashboard.ts`, etc. — one file per domain area under `web/src/types/`):
```typescript
export type SetupStatusResponse = {
  initialized: boolean;
};

export type SetupInitializeRequest = {
  clientName: string;
  logo?: string | null;
  adminEmail: string;
  adminPassword: string;
};

export type SetupInitializeResponse = {
  initialized: boolean;
  message: string;
};
```
Shape to create for the new domain file:
```typescript
export type TenantAdminSummary = {
  id: string;
  nome: string;
  plano: string; // "STARTER" | "STANDARD" | "ENTERPRISE" raw enum value
  limiteUtilizadores: number | null;
  ativo: boolean;
  utilizadoresAtivos: number;
};

export type TenantUpdateRequest = {
  plano: string;
  limiteUtilizadores: number | null;
};
```
`SetupInitializeRequest`/`SetupInitializeResponse` (already in `web/src/types/setup.ts`) are reused as-is for the "Criar Tenant" mutation payload/response — no duplicate type needed for those two.

---

## Shared Patterns

### Two-layer RBAC enforcement (backend authoritative + frontend UX mirror)
**Source:** `PlatformAdminController`'s class-level `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (backend, authoritative) + `clientes/page.tsx`'s `if (permissions.isFetched && !canViewClientes) return <AccessDeniedState .../>` (frontend, UX-only mirror).
**Apply to:** `plataforma/page.tsx` (page guard) and `dashboard-shell.tsx` (nav-item visibility) — both are UX-only; the real boundary is already the existing `@PreAuthorize` on `PlatformAdminController` (Phase 119) plus the new `Tenant.ativo` check in `JwtAuthenticationFilter`.

### DTO discipline — never a raw entity in a response
**Source:** `TenantProvisionResponse.java`'s own doc-comment ("nunca serializar a entidade Tenant diretamente"); mirrored by every other controller (`UserResponse`, `UserSummaryResponse`, `ResultadoPesquisaDto`, etc.).
**Apply to:** `TenantAdminSummaryResponse.java` — universal, no exceptions in this codebase.

### `apiFetch` error handling (already centralized — no new code needed)
**Source:** `web/src/lib/api.ts` (full file, 55 lines) — `credentials: "include"`, auto-toasts non-401/403 errors, throws `Error("API {status}: {message}")` on failure, parses JSON `{message|error}` bodies.
**Apply to:** every new hook in `use-platform-admin.ts` — call `apiFetch` exactly like `use-clientes.ts` does; do not hand-roll fetch/error handling.

### Toast success/error
**Source:** `web/src/hooks/use-toast.ts` (full file, 47 lines) — `toast.success(message)` / `toast.error(message)`, sonner-backed.
**Apply to:** every mutation's `onSuccess`/`catch` in `plataforma/page.tsx` — e.g. `toast.success("Tenant criado com sucesso.")` per the exact copy UI-SPEC locks in.

### Tooltip + disabled `<span tabIndex={0}>` composition
**Source:** `settings/page.tsx` lines 401-417 (first fix of this composition, per CONTEXT.md), also `pareceres/[id]/page.tsx`.
```tsx
<Tooltip>
  <TooltipTrigger asChild>
    <span tabIndex={0}>
      <Button disabled className="...">...</Button>
    </span>
  </TooltipTrigger>
  <TooltipContent>Limite de utilizadores atingido. ...</TooltipContent>
</Tooltip>
```
**Apply to:** the disabled Suspender icon on the "LexCV" row (both desktop `columns.tsx` cell and mobile card block in `plataforma/page.tsx`).

### TanStack Query invalidate-on-mutate (not `window.location.reload()`)
**Source:** `use-clientes.ts`'s `useCreateCliente`/`useUpdateCliente` (`queryClient.invalidateQueries({ queryKey: [...] })`).
**Apply to:** all 4 new mutations in `use-platform-admin.ts`. Explicitly forbidden: `UserManagementTab`'s `setTimeout(() => window.location.reload(), 1000)` — UI-SPEC calls this out by name as a legacy pattern not to copy.

### Manual SQL migration convention
**Source:** every file in `backend/migrations/` (e.g. `117-add-tenant-plano-limite-utilizadores.sql`) — numbered `NNN-description.sql`, header comment always explains the `ddl-auto: validate` production gap, no Flyway/Liquibase.
**Apply to:** `120-add-tenant-ativo.sql`.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `backend/src/main/java/com/lexcv/dtos/TenantUpdateRequest.java` | model (DTO) | transform | No existing typed **request** DTO precedent for a PATCH/PUT-style partial update — every comparable endpoint (`AdminController.updateUser`, `.updateRbac`) takes `Map<String, Object> body` instead. `SetupInitializeRequest` is the only typed-request precedent in the whole codebase, and it's for a POST-create, not an update. Structurally close enough to use as a style template (plain `@Getter @Setter`, no `@Builder`), but genuinely a first-of-its-kind for "typed PATCH body" in this codebase — flagged here rather than claimed as an exact match. RESEARCH.md-equivalent guidance: Jackson's default enum-deserialization 400-on-invalid-value behavior is a Spring Boot/Jackson framework default, not something this codebase has had to configure before, so there's no existing custom exception handler to reuse for that specific failure mode either — verify manually that the default 400 response shape is acceptable (it will be a raw Spring `HttpMessageNotReadableException` 400, not the `Map.of("message", ...)` shape every other endpoint returns) or add a `@ExceptionHandler` if a consistent error body is required. |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{controllers,models,dtos,repositories,config,seed}`, `backend/migrations`, `web/src/{app/(dashboard),components/shared,components/ui,hooks,types,schemas,lib}`
**Files scanned:** ~40 read directly (full or targeted ranges); ~120 enumerated via directory listing/grep
**Pattern extraction date:** 2026-07-29
