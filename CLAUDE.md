# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

LexCV is a multi-tenant legal practice management platform for Cape Verde (clientes, processos, agenda/prazos, documentos, financeiro). The domain language is **Portuguese** — entities, routes, and DTOs use Portuguese names (`cliente`, `processo`, `evento`, `honorario`, `fase`, `parte`, `movimentacao`). Keep new code consistent with that.

The repo is a two-app monorepo:
- `backend/` — Spring Boot 3.4.1 / Java 23 REST API + PostgreSQL
- `web/` — Next.js 16 (App Router) / React 19 frontend

## Commands

### Backend (`backend/`, run with system `mvn` — no wrapper committed)
- Run dev server: `mvn spring-boot:run`
- Build: `mvn -DskipTests package`
- All tests: `mvn test`
- Single test: `mvn test -Dtest=ClassName#methodName`
- SAST (SpotBugs + FindSecBugs): `mvn spotbugs:check`

Requires a `backend/.env` file (see `backend/.env.example`) — `application.yml` imports it and **every** value is a required env var (no defaults). PostgreSQL must be reachable at the configured `DB_*`. Set `SEED_ENABLED=true` to run `DatabaseSeeder` on startup.

### Frontend (`web/`, pnpm — `pnpm-lock.yaml` is authoritative)
- Install: `pnpm install`
- Dev: `pnpm dev` (port 3000)
- Build: `pnpm build`
- Lint: `pnpm lint`

A stale `package-lock.json` is still present in `web/`; ignore it (or delete it) — use pnpm for all dependency operations.

Requires `web/.env.local` (see `web/.env.example`): `BACKEND_API_ORIGIN` (e.g. `http://localhost:8080`) and `NEXT_PUBLIC_API_BASE_PATH` (`/api/v1`). Both are validated at startup and throw if missing.

## Architecture

### How the two apps connect
The frontend never calls the backend host directly. `next.config.ts` rewrites `/api/v1/:path*` → `${BACKEND_API_ORIGIN}/api/v1/:path*`, so the browser sees a same-origin API. `web/src/lib/api.ts#apiFetch` is the single fetch wrapper — it always sends `credentials: "include"` (auth is cookie-based) and surfaces non-401/403 errors as toasts. All data fetching goes through TanStack Query hooks in `web/src/hooks/use-*.ts`, which call `apiFetch`.

### Authentication & RBAC (the core cross-cutting concern)
- Auth is **JWT in httpOnly cookies**. `POST /api/v1/auth/login` sets `access_token` + `refresh_token` cookies; `JwtAuthenticationFilter` reads them per request. There are no bearer tokens in JS.
- Backend authorization is method-level: `@EnableMethodSecurity` + `@PreAuthorize("hasAuthority('<scope>:<action>')")` on each endpoint (e.g. `clientes:view`, `processos:edit`). `AdminController` is gated `@PreAuthorize("hasRole('ADMIN')")` at the class level.
- Permissions follow a `scope:action` convention where `action ∈ {view, create, edit, manage}`. The frontend mirrors this in `web/src/lib/permissions.ts` with a fallback chain (`manage` implies `edit` implies `create`; all imply nothing weaker for `view`). Use `hasScopedPermission(perms, scope, action)` in the UI, and gate the backend with a matching `@PreAuthorize` — **both layers must agree**.
- Roles/permissions/default users are seeded in `backend/.../seed/DatabaseSeeder.java` (roles: ADMIN, ADVOGADO, TECNICO, ASSISTENTE). Default admin: `admin@lexcv.cv` / `Pa$$w0rd`.

### Multi-tenancy (must not be bypassed)
Every domain entity carries a `tenant_id`. Controllers derive the current tenant via `getTenantId()` (reads `UserPrincipal.getTenantId()` from the security context) and **must** scope all reads/writes by it. Unique constraints are per-tenant (e.g. `(tenant_id, documento_numero)`). When adding endpoints or queries, always filter by tenant id — this is the primary data-isolation boundary.

### Backend layout
- `controllers/` — `ResourceController` is a deliberately large (~1000-line) controller holding the bulk of CRUD under `/api/v1` (clientes, processos+partes+fases+movimentacoes, eventos, documentos upload/download, honorarios+pagamentos, dashboard KPIs, cliente merge). `AuthController`, `AdminController` (`/api/v1/admin`), `SetupController` (`/api/v1/setup`, public).
- `models/` — JPA entities (Lombok `@Builder`/`@Data`). `config/` — security (`SecurityConfig`, JWT provider/filter, `UserPrincipal`). `repositories/`, `dtos/`, `services/`, `seed/`.
- JPA: `ddl-auto=update` in dev, `validate` in prod (`application-prod.yml`). File uploads land in `uploads/` on the backend host.

### Frontend layout
- `src/app/(auth)/` and `src/app/(dashboard)/` are route groups; pages map to the domain (`clientes`, `processos`, `agenda`, `documentos`, `financeiro`, plus `clientes/merge`, `settings`, `profile`). `src/app/setup` is the first-run wizard.
- `proxy.ts` is Next 16's middleware replacement: it checks setup status and redirects un-initialized installs to `/setup`, and authenticated users away from `/setup`.
- `components/ui/` are shadcn-style primitives; `components/shared/` holds app shells (`dashboard-shell`, `access-denied-state`). `schemas/` are Zod schemas for react-hook-form; `types/` are API response types.

### Legacy / ignore unless migrating
`web/src/app/_api-backup/` (old Next.js mock API routes) and `web/src/server/` (`mock-db.ts`, `mock-jwt.ts`) are the pre-backend mock implementation, now superseded by the Spring Boot API. Don't build new features against them.

## Next.js 16 caveat
`web/AGENTS.md` warns this Next.js version has breaking changes vs. older training data. Before writing frontend framework code (routing, middleware/proxy, data APIs), check `web/node_modules/next/dist/docs/` rather than assuming older conventions.

## Planning workflow
`.planning/` holds an active milestone/phase planning system (`PROJECT.md`, `ROADMAP.md`, `STATE.md`, `config.json`, `phases/`, `milestones/`). `config.json` drives a gated workflow (plan check, verifier, ASVS-level-1 security enforcement, docs committed). When working inside that flow, follow the phase docs; phase artifacts are committed to git.
