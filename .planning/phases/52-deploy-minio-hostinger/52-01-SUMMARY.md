---
phase: "52-deploy-minio-hostinger"
plan: 01
subsystem: infrastructure
tags: [minio, docker-compose, caddy, github-actions, deploy]
dependency_graph:
  requires: [50-01]
  provides: [minio-service-in-production, ci-cd-auto-deploy]
  affects: [docker-compose.yml, docker-compose.prod.yml, Caddyfile.prod, .github/workflows/deploy.yml]
tech_stack:
  added: [minio/minio:latest, appleboy/ssh-action@v1]
  patterns: [docker-compose-overlay, caddy-basicauth, github-actions-ssh-deploy]
key_files:
  modified:
    - docker-compose.yml
    - docker-compose.prod.yml
    - Caddyfile.prod
    - .github/workflows/deploy.yml
decisions:
  - "MinIO console exposed via /minio-console* on Caddy with basicauth (not direct port exposure)"
  - "minio overlay uses 512M memory limit matching backend; no image override (public image, not GHCR)"
  - "deploy job guards on github.ref == refs/heads/master to skip PR runs"
metrics:
  duration: ~8m
  completed: "2026-06-19"
---

# Phase 52 Plan 01: Deploy MinIO to Hostinger VPS Summary

**One-liner:** Docker Compose MinIO service with persistent volume, healthcheck, and Caddy basicauth console; GitHub Actions SSH deploy job added.

## Tasks Completed

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Add MinIO service + backend env vars to docker-compose.yml | 8503546 |
| 2 | Prod overlay + Caddy /minio-console route + GitHub Actions deploy job | 073defb |

## What Was Built

**Task 1 — docker-compose.yml:**
- Added `minio` service (minio/minio:latest) between postgres and backend, with `lexcv_minio_data` named volume, healthcheck via `curl -f http://localhost:9000/minio/health/live`, and no exposed ports (internal network only).
- Extended backend `depends_on` with `minio: condition: service_healthy`.
- Added five MINIO_* env vars to backend service (`MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME`, `MINIO_PUBLIC_ENDPOINT`) — all `${VAR}` references, no hardcoded values.
- Added `lexcv_minio_data:` to top-level volumes.

**Task 2 — three files:**
- `docker-compose.prod.yml`: minio overlay with `restart: unless-stopped` and resource limits (`cpus: 0.5`, `memory: 512M`). No image field — public image used directly.
- `Caddyfile.prod`: `handle /minio-console*` block inserted before catch-all, with `basicauth` using `{$CADDY_MINIO_USER}` / `{$CADDY_MINIO_PASSWORD_HASH}` and `reverse_proxy minio:9001`.
- `.github/workflows/deploy.yml`: `deploy` job with `needs: build-and-push`, `if: github.ref == 'refs/heads/master'`, using `appleboy/ssh-action@v1` to SSH into VPS and run `docker compose -f docker-compose.yml -f docker-compose.prod.yml pull && up -d`.

## Verification

`docker compose -f docker-compose.yml -f docker-compose.prod.yml config` produces valid output with minio service present, depends_on configured, and all env vars as ${VAR} references. Warnings are only for unset env vars (expected in dev without .env).

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — all mitigations from threat model were applied:
- T-52-01: basicauth on /minio-console* in Caddyfile.prod using bcrypt hash env var.
- T-52-02: no credential values in any versioned file; all ${VAR} placeholders.
- T-52-04: appleboy/ssh-action@v1 tag (consistent with project pattern).

## Self-Check: PASSED

- docker-compose.yml: modified and committed (8503546)
- docker-compose.prod.yml: modified and committed (073defb)
- Caddyfile.prod: modified and committed (073defb)
- .github/workflows/deploy.yml: modified and committed (073defb)
- docker compose config: validated without YAML errors
