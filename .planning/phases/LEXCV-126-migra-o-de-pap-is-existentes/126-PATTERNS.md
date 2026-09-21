# Phase 126: Migração de Papéis Existentes - Pattern Map

**Mapped:** 2026-09-21
**Files analyzed:** 10 (backend only: 6 modify-in-place + 1 new model field + 1 new service + 1 migration/README + tests)
**Analogs found:** 10 / 10 (all analogs are within this same codebase; several are the files themselves, pre-cutover)

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/.../models/User.java` | model | CRUD | itself (`roles` `@ManyToMany`, lines 54-61) + `TenantRole.permissions` (`TenantRole.java:46-53`) | exact — modify in place |
| `backend/.../config/JwtAuthenticationFilter.java` | middleware | request-response (per-request authority resolution) | itself (`doFilterInternal`, lines 59-69) | exact — modify in place |
| `backend/.../controllers/AuthController.java` | controller | request-response | itself (`login`/`refresh`/`getMe`, lines 126,189,258-259) | exact — modify in place |
| `backend/.../controllers/AdminController.java` | controller | request-response (CRUD-adjacent, listing) | itself (`listUsers`, lines 78-79,222 area) | exact — modify in place |
| `backend/.../controllers/ParecerController.java` | controller | request-response | itself (`validateAdvogado`, line 69) | exact — modify in place |
| `backend/.../controllers/ResourceController.java` | controller | request-response | itself (`addClienteAdvogado`/`addClienteAdministrativo`, lines 503,565) | exact — modify in place |
| `backend/.../services/*MigracaoPapeisService.java` (NEW, name TBD) | service | batch (one-time data conversion, per-tenant) | `SetupService.provisionTenant` + `SetupService.instanciarMoldes` (`SetupService.java:116-189`) | exact |
| `backend/migrations/127-*.sql` (NEW) | migration | batch | `backend/migrations/126-add-tenant-role-tables.sql` (full file) | exact |
| Zero-drift verification artifact | test (structural + data-integrity gate) | batch/comparison | `SetupServiceInstanciacaoMoldesTest` (Mockito ArgumentCaptor snapshot proof) **and** `NotificacaoRepositoryIT`/`ParecerVersaoConcorrenciaIT` (Testcontainers `@DataJpaTest`, real Postgres) | exact (two valid shapes, pick one or compose) |
| Mockito tests (filter/controllers/service) | test | request-response | `SetupServiceProvisionTenantTest` + `SetupServiceInstanciacaoMoldesTest` (constructor-injection Mockito convention) | exact |

---

## Pattern Assignments

### 1. The effective-permission union — the exact three parcels (read before writing anything)

This union is computed **identically in three places** today (`JwtAuthenticationFilter:60-69`, `AuthController.updateMe:258-263`, `AdminController.listUsers:78-83`) and **partially** in a fourth (`AuthController.login`/`refresh`, which only need `roles`, not the merged permission set, since the JWT carries only role names — see below). The zero-drift verification must reproduce parcel (1)+(2)+(3) exactly, per user, or it will bless a migration that silently changed access.

**Parcel 1 — permissions of the user's roles** (`JwtAuthenticationFilter.java:64-67`):
```java
Set<String> permissions = user.getRoles().stream()
        .flatMap(r -> r.getPermissions().stream())
        .map(Permission::getNome)
        .collect(Collectors.toSet());
```

**Parcel 2 — the user's direct permissions** (`t_user_permission`, `JwtAuthenticationFilter.java:69`):
```java
user.getPermissions().forEach(permissions::add);
```
`User.permissions` is an `@ElementCollection` (`User.java:63-67`), not a relation — plain strings, free-form, no catalogue validation. This is the field `AdminController.createUser`/`updateUser` write to directly from request JSON (`AdminController.java:198-201`, `318-322`).

**Parcel 3 — the code-added ADMIN block** (`UserPrincipal.create`, `UserPrincipal.java:34-47`):
```java
if (roles.contains("ADMIN")) {
    // Keep in sync with DatabaseSeeder.CATALOGO_PERMISSOES (Phase 124).
    permissions.addAll(java.util.Arrays.asList(
            "clientes:view", "clientes:edit",
            "processos:view", "processos:edit",
            "processos:create", "processos:manage",
            "agenda:view", "agenda:edit",
            "documentos:view", "documentos:edit",
            "financeiro:view", "financeiro:edit", "financeiro:manage",
            "rbac:manage", "users:manage",
            "pareceres:view", "pareceres:create", "pareceres:edit", "pareceres:manage",
            "notificacoes:view"
    ));
}
```
This block fires on the **string** `"ADMIN"` being present in the `roles` set passed in — it is downstream of, and blind to, whether that string came from a global `Role` or a tenant `TenantRole`. **This is the load-bearing fact for the cutover**: as long as the post-migration `roles` set passed into `UserPrincipal.create` still contains the literal string `"ADMIN"` for a tenant's admin user, this block keeps firing unchanged. If Phase 127 ever lets a tenant rename its ADMIN-equivalent role, this literal-string block (not just the three name-comparison sites CONTEXT.md calls out) becomes a second silent-drift trap — out of scope for 126, but worth naming in the plan's risk section since it's adjacent to Decision 2's exact class of bug.

**Where the union is assembled and consumed** — three near-identical blocks to change in lockstep (same `roles.stream().map(Role::getNome))` / `flatMap(r -> r.getPermissions().stream())` shape each time):
- `JwtAuthenticationFilter.java:60-69` (feeds `UserPrincipal.create`, i.e. authorization for the current request)
- `AuthController.java:258-263` (`updateMe`, feeds the `UserResponse` returned to the frontend after a profile edit)
- `AdminController.java:78-83` (`listUsers`, feeds the `UserResponse` list for the admin console)

**Where only `roles` (not the merged permission set) is needed** — the JWT itself carries role names only, permissions are recomputed per-request by the filter, never embedded in the token:
- `AuthController.java:126` (`login`): `List<String> roles = user.getRoles().stream().map(Role::getNome).collect(Collectors.toList());` → `tokenProvider.generateAccessToken(user.getId(), user.getTenantId(), roles)`
- `AuthController.java:189` (`refresh`): identical shape, same two token-generation calls
- `AuthController.java:258` is actually the `updateMe` roles line, immediately followed by the full permission union above — grouped with it, not with login/refresh in practice, but it is a 4th read of `user.getRoles()` in this file as CONTEXT.md counts it (roles line 258, permissions loop 259-263).
- `AuthController.java:214-215` (`getMe`): note this one is **different in shape** — it does NOT recompute from `User.getRoles()`. It reads `principal.getRoles()`/`principal.getPermissions()` straight off the already-resolved `UserPrincipal` (set by the filter). This means `/auth/me` is automatically correct once `JwtAuthenticationFilter` is fixed — no separate read point despite living in the same file. Only lines 126, 189, 258-263 in `AuthController` are real read points; line 214-215 is a pass-through.

---

### 2. The dual resolution path — `plataforma@lexcv.cv` has no tenant role

**Provenance** (`DatabaseSeeder.java`, `seedTenantPlataforma`/`seedUtilizadorPlataforma`, ~lines 528-563):
```java
private Tenant seedTenantPlataforma() {
    return tenantRepository.findFirstByNome("ALCv")
            .orElseGet(() -> tenantRepository.save(Tenant.builder().nome("ALCv").build()));
}
...
private void seedUtilizadorPlataforma(Tenant tenantPlataforma) {
    Role plataformaAdminRole = roleRepository.findByNome("PLATAFORMA_ADMIN")
            .orElseThrow(...);
    if (userRepository.findByEmail("plataforma@lexcv.cv").isEmpty()) {
        User utilizadorPlataforma = User.builder()
                .tenantId(tenantPlataforma.getId())
                .nome("Administrador de Plataforma")
                .email("plataforma@lexcv.cv")
                .passwordHash(passwordEncoder.encode("Pa$$w0rd"))
                .ativo(true)
                .roles(Set.of(plataformaAdminRole))
                .build();
        userRepository.save(utilizadorPlataforma);
    }
}
```
`PLATAFORMA_ADMIN` is seeded with `instanciavel = false` (`DatabaseSeeder.upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList(), false)`), and `SetupService.instanciarMoldes` explicitly skips it even as defense-in-depth (`NOME_PAPEL_PLATAFORMA.equals(molde.getNome())` guard, `SetupService.java:177-179`, proven by `SetupServiceInstanciacaoMoldesTest.instanciarMoldes_plataformaAdminNuncaEInstanciado`). The reserved "ALCv" tenant is never passed through `provisionTenant`/`instanciarMoldes` at all — it is born directly via `tenantRepository.save(...)` in the seeder, which (per `SetupServiceInstanciacaoMoldesTest`'s own scope note, lines 57-61) "não conhece `TenantRoleRepository`". **Net effect for Phase 126:** `plataforma@lexcv.cv` will have zero rows in whatever new `t_user_tenant_role` (or equivalent) join table gets introduced, forever, by construction — not a migration edge case to patch around, a permanent state.

**Existing null/empty-handling idiom to copy at every one of the read points above**, already used twice in this exact area for an analogous "might not exist" resolution:
```java
// JwtAuthenticationFilter.java:45-48
User user = userRepository.findById(userId).orElse(null);
Tenant tenant = user != null ? tenantRepository.findById(user.getTenantId()).orElse(null) : null;
```
and the `Optional<...>.orElse(null)` + null-check-before-use shape repeated throughout `AuthController`/`AdminController` (e.g. `AuthController.java:171-174`, `AdminController.java:235-238`). Apply the **same shape** to "does this user have a `TenantRole` for this tenant": `tenantRoleRepository.findByTenantIdAndUserId(...)` (or via the new `User` association) returning empty/null must fall back to **global** `user.getRoles()` cleanly, not throw — this is exactly what keeps `plataforma@lexcv.cv` able to log in. Concretely: at each of the three read points in section 1, the roles/permissions computation should become "tenant roles if any exist for this user, else global roles" — a simple `isEmpty()` branch using the same defensive style as the tenant-suspension check already in the filter (`JwtAuthenticationFilter.java:59`: `user != null && user.getAtivo() && tenant != null && ...`).

---

### 3. `SetupService.instanciarMoldes` — exact current shape to reuse, not reimplement

**Full method** (`SetupService.java:174-189`):
```java
private void instanciarMoldes(UUID tenantId) {
    List<Role> moldes = roleRepository.findAllByInstanciavelTrue();
    for (Role molde : moldes) {
        if (NOME_PAPEL_PLATAFORMA.equals(molde.getNome())) {
            continue;
        }
        TenantRole tenantRole = TenantRole.builder()
                .tenantId(tenantId)
                .nome(molde.getNome())
                .moldeId(molde.getId())
                .sistema(true)
                .permissions(new HashSet<>(molde.getPermissions()))
                .build();
        tenantRoleRepository.save(tenantRole);
    }
}
```
Called once, inline, from `provisionTenant` (`SetupService.java:144`), inside the **same transaction**, only after `tenant = tenantRepository.save(tenant)` (needs `tenant.getId()`). `NOME_PAPEL_PLATAFORMA` is `private static final String NOME_PAPEL_PLATAFORMA = "PLATAFORMA_ADMIN";` (`SetupService.java:42`).

**Reuse contract for the migration service**: this method is already idempotent-safe for "instantiate every current molde into one tenant" — the migration's job is to call the **same instantiation logic** (either by extracting `instanciarMoldes` to package-private/public and calling it directly, or by extracting a shared helper both `SetupService` and the new migration service delegate to) for every **existing** tenant that doesn't already have `TenantRole` rows, instead of reimplementing the loop. `TenantRoleRepository.findByTenantIdAndNome` (already scaffolded, `TenantRoleRepository.java:18`, with an `IN-01` comment literally saying "a Phase 126 (migração) precisa de repontar t_user_role... " — read it) is the documented seam for the second half: once a tenant has `TenantRole` rows (instantiated the same way `instanciarMoldes` does it), each user's global `Role` names get resolved via `findByTenantIdAndNome(tenantId, role.getNome())` to find the matching `TenantRole` to link.

**Constructor/field order** (`SetupService.java:44-49`, `@RequiredArgsConstructor`, 6 fields in this exact order — any test constructing `SetupService`/a new service directly must match):
```java
private final SystemSettingRepository systemSettingRepository;
private final TenantRepository tenantRepository;
private final UserRepository userRepository;
private final RoleRepository roleRepository;
private final PasswordEncoder passwordEncoder;
private final TenantRoleRepository tenantRoleRepository;
```

---

### 4. The three name-comparison sites — verbatim, plus the provenance-based analog

**`ParecerController.java:69-73`** (`validateAdvogado`):
```java
boolean isAdvogado = user.getRoles().stream()
        .anyMatch(r -> "ADVOGADO".equals(r.getNome()));
if (!isAdvogado) {
    return null;
}
```

**`ResourceController.java:503-505`** (`addClienteAdvogado`):
```java
if (user.getRoles().stream().noneMatch(r -> "ADVOGADO".equals(r.getNome()))) {
    return ResponseEntity.badRequest().body(Map.of("message", "Utilizador não tem o papel ADVOGADO"));
}
```

**`ResourceController.java:565-567`** (`addClienteAdministrativo`):
```java
if (user.getRoles().stream().noneMatch(r -> "ASSISTENTE".equals(r.getNome()) || "TECNICO".equals(r.getNome()))) {
    return ResponseEntity.badRequest().body(Map.of("message", "Utilizador não tem o papel ASSISTENTE ou TECNICO"));
}
```

**Provenance-based analog already in the codebase** — no site today resolves "does this user hold a role that came from molde X" (this is new), but `TenantRole.moldeId` (the field these three sites need to pivot to) already carries exactly the provenance needed, and `TenantRoleRepository` already exposes a `moldeId`-keyed query to copy the shape from:
```java
// TenantRoleRepository.java:23
long countByMoldeId(Integer moldeId);
```
The replacement predicate at all three sites becomes, in spirit: "does this user have a `TenantRole` whose `moldeId` equals the id of the global `Role` named ADVOGADO/ASSISTENTE/TECNICO" — i.e. resolve the molde's `Role.id` once (`roleRepository.findByNome("ADVOGADO")`, same call already used in `SetupService.provisionTenant:124-125`), then filter the user's tenant roles by `moldeId`, mirroring the `.stream().anyMatch(r -> X.equals(r.getNome()))` shape but swapping `getNome()`/`.equals(String)` for `getMoldeId()`/`.equals(Integer)`. This preserves the documented limit from CONTEXT.md Decision 2 verbatim: a tenant-created-from-scratch role (`moldeId == null`) never satisfies `Integer.equals(null)`, so it never matches — same behavior as today's exact-name match failing for a role a tenant invented itself.

---

### 5. The migration script — analog and README update shape

**Full header/idempotency shape to copy** (`backend/migrations/126-add-tenant-role-tables.sql`, already read in full above under "Pattern Assignments #3" migration context) — structure: (a) `IMPORTANT: REQUIRED manual production migration script` boilerplate naming the exact entity/field being introduced, (b) a "why" paragraph tied to `ddl-auto: update` vs `validate`, (c) a paragraph on what is deliberately NOT done here (here: no backfill, `DatabaseSeeder`/the new migration service converges data on next boot — except Phase 126 is different: **this phase's migration is the one that DOES need a data backfill**, unlike 125's schema-only script, because converting `t_user_role` → tenant-role linkage is exactly the "migração de dados" CONTEXT.md puts in-scope), (d) an explicit idempotency statement.

**Filename: next free number is 127** (`126-add-tenant-role-tables.sql` was Phase 125's file; confirmed no `127-*.sql` exists yet in `backend/migrations/`).

**README.md update required in the same commit** (`backend/migrations/README.md`):
- Add row 16 to the Path B table (`README.md:145-158`), immediately after row 15 (`126-add-tenant-role-tables.sql`), in strict ascending numeric order.
- Update the **Re-run safety** section (`README.md:177-189`) — its prose currently reads `Only **5 of the 15** scripts tolerate being run twice` / `The other **10 must not be re-run**`. Both counts go stale the moment row 16 is added: they must become `5 of 16` / `11 must not be re-run` (if `127` is *not* re-runnable — likely, since this script does a one-time data backfill of `t_user_role`/whatever new join table, which if run twice on already-migrated data must either be a no-op or must be written to converge, same idiom as `74-cleanup-nif-documento-tipo.sql`'s "the UPDATE converges (0 rows on a second pass)" — decide which and count accordingly) or `6 of 16` / `10 must not be re-run` if it is written idempotently.
- Update the **Path A — fresh install** section (`README.md:113-131`)'s "skip on fresh install" bullet list — `127` almost certainly belongs in the "redundant but harmless" bucket alongside `126` (same reasoning: on a fresh database there is no legacy `t_user_role` data to convert, `ddl-auto` + the seeder already converge everything from zero), not the "FORBIDDEN" bucket like `125`.
- This is the exact stale-prose-count problem CONTEXT.md flags: "a README keeps its Path B table in strict ascending numeric order and carries prose counts that go stale (currently reading counts that Phase 125 advanced)" — the planner must budget a task for updating these two numeric callouts, not just appending a table row.

---

### 6. Existing data-integrity verification precedents (for MIGR-02, the zero-drift check)

Two valid, already-proven shapes exist in this codebase; CONTEXT.md leaves the choice to the executor, but both are real precedents, not invented:

**Shape A — Mockito behavioral/snapshot proof** (`SetupServiceInstanciacaoMoldesTest.java`, full file read above). Convention: `@ExtendWith(MockitoExtension.class)`, `@Mock` per collaborator, service built via `@RequiredArgsConstructor`'s exact field order in `@BeforeEach`, `ArgumentCaptor<TenantRole>` to inspect exactly what would be persisted, `assertNotSame`/mutate-after-capture to prove independence (their Caso 2), and explicit negative-space assertions (`verify(roleRepository, never()).findAll()`, their Caso 4) proving no unexpected fallback path is used. This shape suits a MIGR-02 check phrased as "for a given set of mocked before-state users, the computed effective-permission set after migration equals the computed effective-permission set before" — i.e. a **unit-level** proof that the algorithm preserves the union, not a live-database proof.

**Shape B — Testcontainers integration test against real Postgres** (`NotificacaoRepositoryIT.java` and `ParecerVersaoConcorrenciaIT.java`, both read in full above). Convention:
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class SomethingIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    ...
}
```
Both files' doc-comments explain *why* `@DataJpaTest` (not `@SpringBootTest`) was chosen: it "contorna por construção o bloqueio MINIO_ENDPOINT" — this slice never instantiates `MinioConfig`/`SecurityConfig`, so their required env-var placeholders are never evaluated, which is exactly the kind of test-harness gotcha a new `*IT.java` for this phase would otherwise hit. `@AutoConfigureTestDatabase(replace = Replace.NONE)` is called out as "obrigatório a par de `@ServiceConnection`" in **both** files — omitting it silently swaps in an embedded database and the container is ignored. This shape suits a MIGR-02 check phrased as "seed real `User`/`Role`/`Permission` rows into a real Postgres, run the actual migration SQL/service, then assert the real post-migration join-table rows resolve to the same permission set" — a genuine before/after comparison against the real schema, closer to what "zero-drift verification" literally promises.

**Recommendation embedded in the analog, not asserted independently:** Shape A alone proves the *algorithm* is a no-op on access; Shape B alone proves the *actual SQL/constraints* don't corrupt or drop rows. CONTEXT.md's framing ("a verificação que compara, utilizador a utilizador, as permissões efectivas antes e depois, e falha alto perante qualquer divergência") reads as closer to Shape A's precision (per-user, named divergence) but Shape B's realism (real DB, real migration path) — the planner should decide whether one artifact does both or two artifacts split the concerns, but should not invent a third harness shape when these two are already proven in this exact codebase.

---

## Shared Patterns

### Role→permission-set flattening (backend, all three read points in section 1)
**Source:** `JwtAuthenticationFilter.java:60-67`, mirrored in `AuthController.java:258-263` and `AdminController.java:78-83`
```java
Set<String> roles = user.getRoles().stream().map(Role::getNome).collect(Collectors.toSet());
Set<String> permissions = user.getRoles().stream()
        .flatMap(r -> r.getPermissions().stream())
        .map(Permission::getNome)
        .collect(Collectors.toSet());
user.getPermissions().forEach(permissions::add);
```
**Apply to:** every one of these three sites, replacing `user.getRoles()` with whatever new tenant-role-aware accessor is introduced on `User`, while falling back to the existing global `user.getRoles()` for users with no tenant roles (the `plataforma@lexcv.cv` case, section 2).

### Optional/null-safe resolution idiom (backend, all dual-path sites)
**Source:** `JwtAuthenticationFilter.java:45-48`
```java
User user = userRepository.findById(userId).orElse(null);
Tenant tenant = user != null ? tenantRepository.findById(user.getTenantId()).orElse(null) : null;
```
**Apply to:** the new "does this user have any `TenantRole`" check at every read point — same `.orElse(null)`/ternary-guard style, never an exception-based control flow, matching the codebase's existing convention for "this might legitimately not exist."

### `@ManyToMany` + `@JoinTable` (backend, new `User` association)
**Source:** `User.java:54-61` (`roles`) and `TenantRole.java:46-53` (`permissions`)
```java
@ManyToMany(fetch = FetchType.EAGER)
@JoinTable(
    name = "t_user_role",
    joinColumns = @JoinColumn(name = "user_id"),
    inverseJoinColumns = @JoinColumn(name = "role_id")
)
@Builder.Default
private Set<Role> roles = new HashSet<>();
```
**Apply to:** the new `User` field pointing at `TenantRole` (e.g. `tenantRoles`), same `FetchType.EAGER` + `@Builder.Default` + `HashSet` idiom, new join table name (must NOT reuse `t_user_role`, per CONTEXT Decision 4 — that table stays exactly as-is, populated, for reversibility).

### Snapshot-copy-not-live-reference idiom (backend, molde/tenant-role instantiation)
**Source:** `SetupService.java:174-189` (`instanciarMoldes`), proven by `SetupServiceInstanciacaoMoldesTest.instanciarMoldes_permissoesSaoSnapshot_naoReferenciaViva`
```java
.permissions(new HashSet<>(molde.getPermissions()))
```
**Apply to:** the migration service, if it instantiates any new `TenantRole` rows for tenants that don't yet have them — must copy, never share, the permission `Set` reference, exactly as `instanciarMoldes` already does for new tenants.

### Migration script header/idempotency + README inventory discipline (backend)
**Source:** `backend/migrations/126-add-tenant-role-tables.sql` (full file) + `backend/migrations/README.md` Path B table (lines 145-158), Re-run safety section (177-189), Path A skip-list (113-131)
**Apply to:** `backend/migrations/127-*.sql` — same header shape, same `IF NOT EXISTS`/idempotency discipline where the script allows it, and a same-commit README update that touches the table row **and** the two stale prose counts (see section 5).

### Mockito constructor-injection test convention (backend, all new service/filter tests)
**Source:** `SetupServiceInstanciacaoMoldesTest.java:63-80`, `SetupServiceProvisionTenantTest.java`
```java
@ExtendWith(MockitoExtension.class)
class SomeServiceTest {
    @Mock private SomeRepo someRepo;
    ...
    private SomeService service;
    @BeforeEach
    void setUp() {
        service = new SomeService(someRepo, ...); // exact @RequiredArgsConstructor field order
    }
}
```
**Apply to:** any new Mockito test for the migration service and for the modified read points, matching each class's actual `@RequiredArgsConstructor` field order exactly (a mismatch here is a compile error, not a runtime one, so it surfaces immediately — but still must be kept in sync by hand whenever a new collaborator is added).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| The new `t_user_tenant_role`-style backfill logic itself (converting every existing user's global role membership into tenant-role membership, tenant by tenant) | service | batch (one-time, cross-tenant) | No prior migration in this codebase converts *relationship* data (a join table) at this scale — `120b-backfill-tenant-plano.sql` backfills a scalar column, not a relation. Closest partial precedent is `instanciarMoldes` (creates new `TenantRole` rows) composed with `TenantRoleRepository.findByTenantIdAndNome` (links a user's existing role name to the right tenant role) — composed guidance given inline in section 3, not a literal single copy target. |
| Provenance-based (`moldeId`) role-membership check replacing a name-based one | controller logic | request-response | No existing predicate in the codebase filters by `TenantRole.moldeId` yet (`countByMoldeId` is an aggregate count, not a per-user membership filter) — composed guidance given inline in section 4. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{models,repositories,services,controllers,config,seed}`, `backend/migrations/`, `backend/src/test/java/com/lexcv/{services,repositories}`
**Files scanned:** 17 read directly (full or targeted): `User.java`, `TenantRole.java`, `TenantRoleRepository.java`, `RoleRepository.java`, `UserRepository.java`, `JwtAuthenticationFilter.java`, `UserPrincipal.java`, `AuthController.java`, `AdminController.java`, `ParecerController.java` (partial), `ResourceController.java` (targeted range), `SetupService.java` (targeted range), `DatabaseSeeder.java` (targeted greps + range), `126-add-tenant-role-tables.sql`, `README.md` (targeted sections), `SetupServiceInstanciacaoMoldesTest.java`, `NotificacaoRepositoryIT.java` + `ParecerVersaoConcorrenciaIT.java` (partial)
**Pattern extraction date:** 2026-09-21
