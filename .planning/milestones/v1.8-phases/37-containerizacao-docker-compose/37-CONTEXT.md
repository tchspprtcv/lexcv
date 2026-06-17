---
phase: 37
name: Containerização e Docker Compose
status: ready
gathered: 2026-06-16
---

# Phase 37: Containerização e Docker Compose - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Configurar os Dockerfiles multi-stage para Next.js e Spring Boot e orquestrar o ambiente local e de produção via Docker Compose com volume para PostgreSQL.

Stack:
- Backend: Spring Boot 3.4.1 / Java 23
- Frontend: Next.js 16.2.6 (App Router)
- Database: PostgreSQL
- Reverse proxy: Caddy (included from day 1)

Existing env vars (from .env.example):
- Backend: SERVER_PORT, DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD, JWT_SECRET, JWT_ACCESS_EXPIRATION_MS, JWT_REFRESH_EXPIRATION_MS, CORS_ALLOWED_ORIGINS, SEED_ENABLED
- Frontend: BACKEND_API_ORIGIN, NEXT_PUBLIC_API_BASE_PATH

</domain>

<decisions>
## Implementation Decisions

### Compose structure
`docker-compose.yml` (base, used for local dev) + `docker-compose.prod.yml` (override, merged for prod). Dev runs `docker compose up`; prod runs `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`.

### Reverse proxy
Include Caddy in both compose files from the start. Local dev uses Caddy on port 80/443 so topology matches prod and avoids CORS/port issues.

### Java base image
Multi-stage Dockerfile: build stage uses `maven:3.9-eclipse-temurin-23` (or local Maven via COPY), runtime stage uses `eclipse-temurin:23-jre-alpine`. Cuts image size significantly vs JDK.

### Next.js build output
Set `output: 'standalone'` in `next.config.ts`. Produces a self-contained server bundle — no `node_modules` in final image, ~80% smaller. Multi-stage: builder → runner with only `.next/standalone` + `.next/static` + `public`.

### Volumes
- `lexcv_uploads` named volume → `backend:/app/uploads` (persists file uploads)
- `lexcv_pgdata` named volume → `postgres:/var/lib/postgresql/data` (persists DB data)
- No bind mounts for data (avoids host permission issues on Linux VPS)

</decisions>

<code_context>
## Existing Code Insights

- Backend entry point: `backend/src/main/java/.../BackendApplication.java`
- Backend `.env` is imported in `application.yml` — all env vars are required (no defaults)
- Frontend rewrites `/api/v1/*` → `${BACKEND_API_ORIGIN}/api/v1/*` via `next.config.ts`
- File uploads land in `uploads/` relative to backend working directory
- No existing Dockerfile or docker-compose.yml in the repo

</code_context>

<specifics>
## Specific Ideas

- `.dockerignore` for both apps (exclude node_modules, target/, .env files)
- Backend Dockerfile: Maven build → JRE runtime, copy JAR only
- Frontend Dockerfile: pnpm install → next build → standalone copy
- `docker-compose.yml`: services = backend, frontend, postgres, caddy
- Caddy routes: `/api/*` → backend:8080, `/*` → frontend:3000
- Health checks on postgres so backend waits for DB ready
- `depends_on` with condition `service_healthy` for DB dependency

</specifics>

<deferred>
## Deferred Ideas

- Production-specific Caddy config with real domain/HTTPS → Phase 38
- CI/CD image build and push → Phase 39
- Secrets management (Docker secrets vs env files) → deferred, use .env files for now

</deferred>
