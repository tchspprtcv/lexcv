# Phase 120: Frontend — Consola de Administração de Tenants - Context

**Gathered:** 2026-07-29
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — user pre-authorized Claude to decide grey areas ("o claude decide as opções e avança")

<domain>
## Phase Boundary

This phase delivers the actual internal console a `PLATAFORMA_ADMIN` uses day-to-day: create tenants (UI on top of Phase 119's `POST /api/v1/platform/tenants`), list all tenants with active-user counts, adjust `plano`/`limiteUtilizadores`, and suspend/reactivate a tenant with immediate effect. Unlike Phases 117-119, this phase requires REAL new backend work in addition to frontend — Phase 119 deliberately scoped itself to "just the creation endpoint" (per its own CONTEXT.md discretion note) and left list/adjust/suspend for here. This phase is planned and executed with the project's established backend-before-frontend sequencing, but within the SAME phase number (120), same as how Phases 117/118/119 already split backend/frontend/UAT across multiple plans within one phase.

</domain>

<decisions>
## New Backend Work Required (not yet built by Phase 119)

### Suspend mechanism
- Add `Tenant.ativo` (`Boolean`, default `true`, matching `User.ativo`'s exact naming/semantics — not `suspenso`, to keep the "true = usable" polarity consistent with the rest of the codebase). Requires a manual SQL migration `backend/migrations/120-add-tenant-ativo.sql` (this project has no Flyway/Liquibase; `ddl-auto=update` in dev auto-adds the column but prod needs the manual script, same convention as every prior migration in `backend/migrations/`). Backfill existing tenants to `ativo=true`.
- **Enforcement — immediate effect on already-active sessions, not just login-time.** `JwtAuthenticationFilter.doFilterInternal` (`backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java:43`) already re-checks `user.getAtivo()` on **every single request** (not cached, not only at login) — this is the exact established mechanism that makes deactivating a `User` take immediate effect mid-session. Extend the SAME check to also verify the user's `Tenant.ativo` (inject `TenantRepository`, look up `user.getTenantId()`, require it to be active too — `user != null && user.getAtivo() && tenant present && tenant.getAtivo()`). This directly satisfies ROADMAP Success Criterion 4's explicit wording: a suspended tenant's users must stop being able to authenticate **or continue using an already-active session** — reusing the filter's per-request re-validation gives both for free, matching the exact pattern already proven for user-level deactivation.
- Reserved "LexCV" platform tenant itself must never be suspendable through this UI (suspending it would lock out the only `PLATAFORMA_ADMIN` account) — the console should omit or disable the suspend action for the tenant named "LexCV" specifically (mirrors `AdminController.updateRbac`'s existing `"ADMIN".equals(roleName)` immutability-guard pattern, applied here to the reserved tenant instead of a role).

### List tenants with utilization (PROV-03)
- New endpoint `GET /api/v1/platform/tenants` on `PlatformAdminController` (already `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` at class level from Phase 119 — reuse the same class, don't create a second controller). Returns, per tenant: id, nome, plano, limiteUtilizadores, ativo, and active-user count via the SAME reusable `UserRepository.countByTenantIdAndAtivoTrue` Phase 117 built specifically for this future reuse (see its own doc comment).
- Response is a list of a new purpose-built DTO (e.g. `TenantAdminSummaryResponse`) — never raw `Tenant` entities, matching every other controller's established convention.

### Adjust plano/limite (PROV-04)
- New endpoint, e.g. `PATCH /api/v1/platform/tenants/{id}` (or `PUT`, Claude's discretion) accepting `plano`/`limiteUtilizadores`, on the same `PlatformAdminController`. Reuse `TenantPlano` enum validation (Jackson will already reject invalid enum values with a 400 by default — no need to hand-roll that check). `limiteUtilizadores` stays nullable (`null` = sem limite), exact same semantics as Phase 117.

### Suspend/reactivate (PROV-05)
- New endpoint, e.g. `PATCH /api/v1/platform/tenants/{id}/ativo` or folded into the same adjust endpoint above (Claude's discretion on whether to combine into one PATCH accepting all three fields, or split) — toggles `Tenant.ativo`. Must reject (400 or 409) attempts to suspend the reserved "LexCV" tenant.

## Frontend Console

### Route and navigation
- New route under the existing `(dashboard)` route group (reuse `DashboardShell` chrome — same sidebar/topbar/theme as the rest of the app, not a standalone screen) — e.g. `web/src/app/(dashboard)/plataforma/page.tsx`.
- New sidebar nav item (in `sidebar-nav.tsx`'s `NAV` array, `dashboard-shell.tsx`) visible only when `me?.roles?.includes("PLATAFORMA_ADMIN")` — this is a ROLE check, not a `requiredPermission` scope check like every other nav item (`PLATAFORMA_ADMIN` deliberately carries zero scoped permissions per Phase 119, so the existing `hasPermission(permissions, item.requiredPermission)` filter mechanism doesn't apply here; this needs its own conditional, analogous to how `settings/page.tsx` already computes `isAdmin = me?.roles?.includes("ADMIN")` for its own role-based tab gating).
- **Emergent, no-extra-work consequence worth confirming during planning, not re-deciding:** a `PLATAFORMA_ADMIN` user has an empty `permissions` array (Phase 119), so every existing `requiredPermission`-gated nav item (Clientes/Processos/Agenda/etc.) already renders as hidden for that user automatically, via the existing `hasPermission` filter — no new hiding logic needed for the regular nav, only the new item needs adding.

### Screen contents
- Table/list of all tenants: nome, plano, limiteUtilizadores (or "sem limite"), active-user count, ativo/suspenso status.
- "Criar Tenant" action opens a form (nome/adminEmail/adminPassword/logo — same shape as the existing `/setup` wizard's fields, reuse copy/validation patterns from `SetupInitializeRequest`'s frontend equivalent if one already exists, otherwise build fresh following this file's own established form patterns from `UserManagementTab`'s "Novo Utilizador" dialog).
- Per-tenant "Editar" action to adjust plano/limiteUtilizadores (reuse `NativeSelect`/`Input` patterns already established project-wide per PROJECT.md's v2.13 decisions).
- Per-tenant suspend/reactivate toggle, disabled for the row where `nome === "LexCV"` (with a tooltip explaining why, matching the established disabled+Tooltip pattern Phase 118 just fixed).
- Reuse the shared `DataTable` component (`web/src/components/shared/data-table/`) for the tenant list — established pattern for every list screen in this app (Clientes, Processos, Pareceres, Financeiro, Documentos per v2.13 Phase 104) — do not hand-roll a new table.

## Claude's Discretion
- Exact new-tenant creation dialog layout (modal Dialog vs. inline card) — follow whichever existing pattern (`UserManagementTab`'s create dialog is the closest analog) fits best.
- Whether adjust-plano/limite and suspend/reactivate are combined into one edit dialog or separate actions — either is fine as long as both capabilities exist and are clearly labeled.
- Exact HTTP verb/path for the new endpoints beyond what's specified above.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `UserRepository.countByTenantIdAndAtivoTrue` (Phase 117) — explicitly built with Phase 120/122 reuse in mind, per its own doc comment
- `JwtAuthenticationFilter.doFilterInternal:43` — the exact per-request re-validation hook to extend for tenant-level suspension
- `PlatformAdminController` (Phase 119) — extend with 3 new endpoints (list, adjust, suspend), same class, same `@PreAuthorize` gate
- `TenantPlano` enum (Phase 117) — reuse for the adjust-plano endpoint, no new enum
- Shared `DataTable` (`web/src/components/shared/data-table/`) — reuse for the tenant list
- `settings/page.tsx`'s `isAdmin = me?.roles?.includes("ADMIN")` pattern — exact analog for the new `isPlatformAdmin` nav-gating check

### Established Patterns
- Manual SQL migrations in `backend/migrations/NNN-description.sql`, no Flyway/Liquibase
- Every controller response is a purpose-built DTO, never a raw entity
- Two-layer enforcement (backend authoritative, frontend UX mirror) — the suspend-takes-immediate-effect backend mechanism is the security boundary; the frontend disabled-row-for-LexCV is UX only
- `Tooltip` + `<span tabIndex={0}>` wrapper for any disabled interactive element with an explanation (Phase 118 precedent, first fix of this composition)

### Integration Points
- `backend/src/main/java/com/lexcv/models/Tenant.java` — add `ativo` field
- `backend/migrations/120-add-tenant-ativo.sql` — new migration
- `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` — inject `TenantRepository`, extend the active-check
- `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java` — add list/adjust/suspend endpoints
- New DTO(s) for the list response
- `web/src/components/shared/sidebar-nav.tsx` / `dashboard-shell.tsx` — new role-gated nav item
- New file `web/src/app/(dashboard)/plataforma/page.tsx` (or similar route name)

</code_context>

<specifics>
## Specific Ideas

None beyond what's captured above and in ROADMAP.md's own Success Criteria for Phase 120.

</specifics>

<deferred>
## Deferred Ideas

- Usage report / billing report (nome/plano/limite/utilizadores ativos por tenant, aggregated view) — Phase 122 (UTIL-01), this phase's own list screen is the operational console, not the reporting view (some overlap in the underlying data is fine and expected, they can share the list endpoint)
- Isolation audit of this new surface — Phase 123 (ISOL-04), explicitly scoped as a dedicated later audit, not this phase's own job to self-certify beyond normal code review

</deferred>
