---
phase: 37-containerizacao-docker-compose
verified: 2026-06-16T00:00:00Z
status: human_needed
score: 9/11 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Run docker compose up --build -d and verify all four services start cleanly"
    expected: "docker compose ps shows lexcv_postgres (healthy), lexcv_backend (running), lexcv_frontend (running), lexcv_caddy (running)"
    why_human: "Cannot run Docker daemon on this Windows dev machine during verification; Task 3 of Plan 37-02 was a blocking human-verify checkpoint that was explicitly deferred"
  - test: "curl http://localhost/ and curl http://localhost/api/v1/setup/status via Caddy"
    expected: "First returns HTTP 200 or 307; second returns HTTP 200 from backend"
    why_human: "Requires running containers — programmatic grep cannot verify live routing"
  - test: "Test PostgreSQL data persistence across container restarts"
    expected: "After docker compose down && docker compose up -d, postgres tables still exist"
    why_human: "Requires running Docker stack to validate named volume lexcv_pgdata persistence"
---

# Phase 37: Containerizacao Docker Compose Verification Report

**Phase Goal:** Configurar os Dockerfiles multi-stage para Next.js e Spring Boot e orquestrar o ambiente local e de producao via Docker Compose com volume para PostgreSQL.
**Verified:** 2026-06-16
**Status:** HUMAN NEEDED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Backend Dockerfile uses eclipse-temurin:23-jre-alpine as runtime base | VERIFIED | Line 10 of backend/Dockerfile: `FROM eclipse-temurin:23-jre-alpine AS runtime` |
| 2 | Backend Dockerfile multi-stage: maven builder + JRE runtime | VERIFIED | Two FROM statements; Stage 1: `maven:3.9-eclipse-temurin-23`, Stage 2: JRE Alpine |
| 3 | Backend Dockerfile has non-root user (appuser) before ENTRYPOINT | VERIFIED | Lines 12, 15: `adduser -S appuser` + `USER appuser` |
| 4 | No secrets baked into backend image ENV layers | VERIFIED | No ENV instructions in backend/Dockerfile; all env vars injected via compose |
| 5 | Frontend Dockerfile uses three-stage build (deps/builder/runner) | VERIFIED | Three FROM statements: node:22-alpine AS deps, builder, runner |
| 6 | Frontend image copies only standalone, static, public — no node_modules | VERIFIED | Runner stage COPY lines reference only `.next/standalone`, `.next/static`, `public` |
| 7 | next.config.ts has output: 'standalone' | VERIFIED | Line 9: `output: 'standalone',`; rewrites() and headers() preserved |
| 8 | docker-compose.yml has all 4 services with lexcv_pgdata named volume | VERIFIED | All four services (postgres, backend, frontend, caddy) + volumes section with lexcv_pgdata, lexcv_uploads, caddy_data, caddy_config |
| 9 | Backend depends_on postgres with condition: service_healthy | VERIFIED | Lines 25-26 of docker-compose.yml: `condition: service_healthy` under depends_on.postgres |
| 10 | docker compose up brings up all services and Caddy routes traffic correctly | UNCERTAIN | Cannot verify without running Docker; human checkpoint deferred |
| 11 | PostgreSQL data persists across container restarts (lexcv_pgdata volume) | UNCERTAIN | Named volume declared; runtime persistence requires human verification |

**Score:** 9/11 truths verified (2 require human testing)

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/Dockerfile` | Multi-stage Maven → JRE runtime | VERIFIED | Exists; 2-stage; JRE Alpine runtime; non-root user; no secrets |
| `web/Dockerfile` | Multi-stage pnpm → standalone runner | VERIFIED | Exists; 3-stage; copies only standalone/static/public; non-root user |
| `backend/.dockerignore` | Excludes target/, .env | VERIFIED | Contains `target/`, `.env`, `.env.*`, `.git/`, `.mvn/` |
| `web/.dockerignore` | Excludes node_modules, .next, .env.local | VERIFIED | Contains `node_modules/`, `.next/`, `.env`, `.env.local` |
| `docker-compose.yml` | 4 services + lexcv_pgdata volume | VERIFIED | All 4 services; postgres healthcheck; backend/frontend not host-exposed; caddy on 80/443; all volumes declared |
| `docker-compose.prod.yml` | Production overrides with restart policies and resource limits | VERIFIED | All 4 services have `restart: unless-stopped`; backend/frontend use `image:` not `build:`; deploy.resources.limits set |
| `Caddyfile` | Routes /api/* to backend:8080, /* to frontend:3000 | VERIFIED | `reverse_proxy backend:8080` and `reverse_proxy frontend:3000` present |
| `.env.example` | Root-level template with POSTGRES_PASSWORD, JWT_SECRET | VERIFIED | Contains POSTGRES_DB, POSTGRES_USER, POSTGRES_PASSWORD, JWT_SECRET, JWT_ACCESS_EXPIRATION_MS, JWT_REFRESH_EXPIRATION_MS, CORS_ALLOWED_ORIGINS, SEED_ENABLED |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| docker-compose.yml | Caddyfile | Caddy service mounts `./Caddyfile:/etc/caddy/Caddyfile:ro` | VERIFIED | Volume mount present in caddy service |
| docker-compose.yml postgres | backend service | `depends_on: condition: service_healthy` | VERIFIED | Confirmed in compose lines 24-26 |
| docker-compose.yml backend | lexcv_uploads volume | `volumes: - lexcv_uploads:/app/uploads` | VERIFIED | Declared in backend service volumes |
| web/Dockerfile | next.config.ts | `output: 'standalone'` must be set before build | VERIFIED | `output: 'standalone'` present in next.config.ts |
| backend/Dockerfile | target/*.jar | COPY --from=builder copies JAR to runtime stage | VERIFIED | Line 13: `COPY --from=builder /build/target/*.jar app.jar` |

---

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|-------------|-------------|--------|----------|
| DEP-01 | Dockerfiles multi-stage para Next.js e Spring Boot | SATISFIED | backend/Dockerfile (2-stage Maven→JRE) and web/Dockerfile (3-stage pnpm→standalone) verified |
| DEP-02 | Docker Compose multi-container com PostgreSQL, Caddy, volumes | PARTIAL | docker-compose.yml structure verified statically; runtime behavior requires human test (Task 3 deferred) |
| DEP-05 | Credenciais isoladas via variaveis de ambiente no ficheiro .env | SATISFIED | No secrets in Dockerfiles; all credentials passed via compose env vars from .env; .env.example committed (not .env) |

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| None | — | — | — |

No TBD, FIXME, XXX, placeholder patterns, or secret values found in any phase artifact.

---

### Human Verification Required

#### 1. Full Docker Stack Startup

**Test:** Copy `.env.example` to `.env`, set real values for `POSTGRES_PASSWORD`, `JWT_SECRET`, and `SEED_ENABLED=true`. Run `docker compose up --build -d`. Wait ~60s.
**Expected:** `docker compose ps` shows all four containers running — lexcv_postgres (healthy), lexcv_backend (running), lexcv_frontend (running), lexcv_caddy (running). No exit codes in any service.
**Why human:** Requires Docker daemon; this Windows dev machine could not run Docker during phase execution.

#### 2. Caddy Routing Verification

**Test:** After stack is running, execute `curl -s -o /dev/null -w "%{http_code}" http://localhost/` and `curl -s -o /dev/null -w "%{http_code}" http://localhost/api/v1/setup/status`.
**Expected:** First returns 200 or 307. Second returns 200 (backend responds through Caddy).
**Why human:** Live routing can only be confirmed with running containers.

#### 3. PostgreSQL Data Persistence

**Test:** Run `docker compose down` (without `-v`), then `docker compose up -d`. Execute `docker compose exec postgres psql -U postgres -d lexcvservice_db -c "\dt"`.
**Expected:** Tables listed (not "no relations") — proves lexcv_pgdata volume survived restart.
**Why human:** Named volume persistence requires container lifecycle testing.

---

### Gaps Summary

No hard gaps were found. All artifacts exist and are substantive. All key links are wired at the code level.

The two unverified truths (#10 and #11) are blocked solely because the Docker runtime was unavailable on this Windows machine during verification — this was anticipated and documented as a deferred human checkpoint (Task 3 of Plan 37-02, `type="checkpoint:human-verify" gate="blocking"`).

DEP-02 is marked PARTIAL pending the human runtime test. DEP-01 and DEP-05 are fully SATISFIED by static analysis.

---

_Verified: 2026-06-16_
_Verifier: Claude (gsd-verifier)_
