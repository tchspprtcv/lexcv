---
phase: 37-containerizacao-docker-compose
plan: "01"
subsystem: infrastructure
tags: [docker, containerization, backend, frontend, next.js, spring-boot]
dependency_graph:
  requires: []
  provides: [backend/Dockerfile, web/Dockerfile, backend/.dockerignore, web/.dockerignore]
  affects: [web/next.config.ts]
tech_stack:
  added: [eclipse-temurin:23-jre-alpine, maven:3.9-eclipse-temurin-23, node:22-alpine, pnpm standalone build]
  patterns: [multi-stage Docker build, non-root container user, Next.js standalone output]
key_files:
  created:
    - backend/Dockerfile
    - backend/.dockerignore
    - web/Dockerfile
    - web/.dockerignore
  modified:
    - web/next.config.ts
decisions:
  - "Backend runtime stage uses eclipse-temurin:23-jre-alpine (JRE only, not JDK) to minimize image size"
  - "Frontend uses output: standalone in next.config.ts — final image contains no node_modules"
  - "All secrets injected at runtime via Docker Compose; no ENV secrets baked into image layers"
  - "Both images run as non-root appuser (adduser appuser) to reduce blast radius"
  - "Backend dependency layer cached separately (mvn dependency:go-offline before COPY src)"
metrics:
  duration: "15 minutes"
  completed: "2026-06-16"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 1
---

# Phase 37 Plan 01: Multi-stage Dockerfiles for Backend and Frontend Summary

**One-liner:** Multi-stage Dockerfiles for Spring Boot (JRE Alpine runtime) and Next.js (standalone output, no node_modules) with non-root users and .dockerignore files excluding secrets.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Backend Dockerfile (Maven → JRE) | a3c0ea9 (backend submodule) | backend/Dockerfile, backend/.dockerignore |
| 2 | Frontend Dockerfile (pnpm → standalone) + next.config.ts | 4a2e3b3 (web submodule) | web/Dockerfile, web/.dockerignore, web/next.config.ts |

## Deviations from Plan

None — plan executed exactly as written.

Note: `backend/` and `web/` are git submodules. Files were committed inside each submodule repo directly (not via the parent repo). The parent repo submodule pointers will be updated in the metadata commit.

## Threat Model Coverage

| Threat ID | Mitigation Applied |
|-----------|--------------------|
| T-37-01 | No ENV instructions with secret values in either Dockerfile; all env vars injected at runtime |
| T-37-02 | Non-root appuser created via adduser in both Dockerfiles; USER instruction before ENTRYPOINT |
| T-37-03 | .dockerignore in both repos excludes .env, .env.*, .env.local |

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, or schema changes introduced.

## Self-Check: PASSED

- backend/Dockerfile: EXISTS
- backend/.dockerignore: EXISTS
- web/Dockerfile: EXISTS
- web/.dockerignore: EXISTS
- web/next.config.ts contains output: 'standalone': VERIFIED
- backend submodule commit a3c0ea9: EXISTS
- web submodule commit 4a2e3b3: EXISTS
