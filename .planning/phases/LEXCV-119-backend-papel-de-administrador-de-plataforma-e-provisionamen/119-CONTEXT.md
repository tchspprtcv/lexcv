# Phase 119: Backend — Papel de Administrador de Plataforma e Provisionamento - Context

**Gathered:** 2026-07-29
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — user pre-authorized Claude to decide grey areas ("o claude decide as opções e avança")

<domain>
## Phase Boundary

Backend-only. Introduces the `PLATAFORMA_ADMIN` role (distinct from each office's `ADMIN`), a reserved "LexCV" platform tenant that role's users belong to, and a new backend capability to provision additional tenants without touching the public `/setup` wizard or its singleton gate. No frontend/UI in this phase — that is Phase 120, which will build the actual console consuming the endpoint this phase creates.

</domain>

<decisions>
## Role and Reserved Tenant Seeding
- `PLATAFORMA_ADMIN` is seeded via `DatabaseSeeder.seedRbac()` — the same unconditional (every-startup, no `seedEnabled` gate) method that already upserts `ADMIN`/`ASSISTENTE`/`TECNICO`/`ADVOGADO`. Call `upsertRolePermissions("PLATAFORMA_ADMIN", Collections.emptyList())` — this role gets **zero** scoped `clientes:view`-style permissions; it operates exclusively through its own dedicated, separately-`@PreAuthorize`-gated endpoint(s), never through the regular tenant RBAC system. Do not add it to `RbacResponse.systemPermissions` or any tenant-facing RBAC screen.
- A reserved `Tenant` named literally `"LexCV"` plus one bootstrap `PLATAFORMA_ADMIN` user are also seeded unconditionally in `DatabaseSeeder` (NOT gated by `seedEnabled` — that flag controls optional demo data, e.g. `Cliente`/`Processo` fixtures; the reserved platform tenant is core infrastructure, needed for the capability to be usable at all, same category as the RBAC rows themselves).
  - > **⚠ PARCIALMENTE SUPERSEDED (revisao de planeamento, 2026-07-29) — ver `119-01-PLAN.md`.**
    > O **papel** `PLATAFORMA_ADMIN` e a **tenant reservada** `"LexCV"` continuam a ser seedados
    > incondicionalmente, exatamente como escrito acima. O **utilizador bootstrap**
    > `plataforma@lexcv.cv` passou a ser gated por `app.seed.enabled` — o mesmo flag que ja
    > protege o utilizador demo `admin@lexcv.cv`. Motivo: `Pa$w0rd` e uma password
    > publicamente documentada (esta no `CLAUDE.md`); `admin@lexcv.cv` nunca chega a um
    > arranque de producao porque vive atras de tres gates, mas um seed incondicional de
    > `plataforma@lexcv.cv` criaria uma conta de privilegio maximo com password conhecida em
    > TODAS as producoes. Com `SEED_ENABLED=false` nao existe credencial de plataforma por
    > omissao. Tudo o resto deste bloco (credenciais exatas quando o seed corre, idempotencia
    > por `nome = "LexCV"`, `Collections.emptyList()` no papel) mantem-se vinculativo.
- Idempotency: look up the reserved tenant by `nome = "LexCV"` before creating (mirrors the find-or-create idempotency already used by `upsertRolePermissions`) — repeated app restarts must never create duplicate reserved tenants.
- Seeded bootstrap credentials follow the exact existing convention (`admin@lexcv.cv` / `Pa$$w0rd` documented in CLAUDE.md): use `plataforma@lexcv.cv` / `Pa$$w0rd` for the seeded `PLATAFORMA_ADMIN` user, same password (dev-only seed, already a public/known dev credential per CLAUDE.md — not a new secret).

## New Service Method — Reuse, Don't Duplicate
- Add a new method directly to `SetupService` (natural home — it is the exact same "create Tenant + initial ADMIN User" operation `initializeSystem` already does, minus the `SystemSetting` singleton gate). Name it `provisionTenant(SetupInitializeRequest request)`.
- Reuse `validateRequest(request)` as-is (already private within `SetupService`, zero visibility changes needed since the new method lives in the same class) — do not duplicate the email/password/logo regex rules.
- `provisionTenant` does NOT read or write `SystemSettingRepository` at all — no singleton check, no `initialized` flag touched. It looks up the `ADMIN` role (not `PLATAFORMA_ADMIN`) for the new tenant's initial user, exactly like `initializeSystem` does today — a newly provisioned tenant's first user is that tenant's own regular `ADMIN`, never a platform role.
- Reuse the existing `SetupInitializeRequest` DTO (clientName/adminEmail/adminPassword/logo) as the input shape — no new DTO needed, the shape is identical to `/setup/initialize`'s.

## New Endpoint — Fully Distinct Code Path
- New controller `PlatformAdminController`, mapped under `/api/v1/platform`, `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` at class level — matches `AdminController`'s established class-level `@PreAuthorize("hasRole('ADMIN')")` pattern exactly.
- `POST /api/v1/platform/tenants` calls `setupService.provisionTenant(request)`, returns the created tenant (id + nome at minimum — mirror whatever shape `AdminController.createUser` uses for its 201 response, i.e. a purpose-built response object, never the raw `Tenant`/`User` entities serialized directly, consistent with `AuthController`/`PublicController` never leaking raw entities).
- `POST /api/v1/setup/initialize` is completely untouched by this phase — same public access, same singleton gate, same behavior. The new capability never reuses, wraps, or calls through the public setup endpoint; it is a fully separate controller + service method + security gate.

## Claude's Discretion
- Exact response DTO shape for the new endpoint (as long as it doesn't leak raw entities) — implementation detail.
- Whether `PlatformAdminController` also gets a `GET /api/v1/platform/tenants` (list) in this phase or waits for Phase 120 — Phase 120's own ROADMAP goal explicitly describes "lista todos os tenants existentes" as PROV-03, a Phase 120 requirement, not Phase 119's PROV-01/PROV-06. Default to backend-minimal here: only what PROV-01 (role/reserved tenant) and PROV-06 (setup stays singleton, provisioning is a distinct path) actually require, i.e. just the creation endpoint. If a list/read endpoint is trivially cheap to add now and clearly reusable by Phase 120, it MAY be included, but it is not required by this phase's own success criteria.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SetupService.initializeSystem` (`backend/src/main/java/com/lexcv/services/SetupService.java:44-86`) — the exact operation to reuse: validate → check email uniqueness → find `ADMIN` role → create `Tenant` → create `User` with that role. `provisionTenant` should look nearly identical, minus the `SystemSetting` block (lines 47-57, 83-85).
- `DatabaseSeeder.seedRbac()` (`backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:293-360`) — `upsertRolePermissions(String roleName, Collection<Permission> permissions)` (line 352) already does exactly the idempotent role-creation this phase needs, just called with an empty permission collection for `PLATAFORMA_ADMIN`.
- `AdminController` (`backend/src/main/java/com/lexcv/controllers/AdminController.java:24-28`) — class-level `@PreAuthorize("hasRole('ADMIN')")` + `@RequiredArgsConstructor` is the exact pattern to replicate for `PlatformAdminController` with `PLATAFORMA_ADMIN`.

### Established Patterns
- Every controller response uses a purpose-built DTO, never a raw entity (`UserResponse`, `TenantPublicInfoResponse`) — `PlatformAdminController`'s tenant-creation response must follow this.
- `DatabaseSeeder`'s demo-data block (lines 43-56) is gated by `seedEnabled` AND `SystemSetting.initialized` AND zero-existing-data checks — explicitly NOT the pattern to copy for the reserved tenant, which must exist regardless of those three conditions.

### Integration Points
- `backend/src/main/java/com/lexcv/services/SetupService.java` — add `provisionTenant` method
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` — add `PLATAFORMA_ADMIN` to `seedRbac()`, add reserved tenant + bootstrap user seeding (unconditional block, separate from the demo-data block)
- New file: `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java`
- No frontend files touched this phase

</code_context>

<specifics>
## Specific Ideas

None beyond what's captured above and in ROADMAP.md's own Success Criteria for Phase 119.

</specifics>

<deferred>
## Deferred Ideas

- Listing tenants, adjusting plano/limite, suspending tenants, the actual admin console UI — all Phase 120 (PROV-02 through PROV-05)
- Locking down `PUT /api/v1/admin/rbac` to platform-only — Phase 121 (ISOL-03)

</deferred>
