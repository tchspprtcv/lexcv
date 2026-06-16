---
phase: 39
name: Pipeline de CI/CD
status: ready
gathered: 2026-06-16
---

# Phase 39: Pipeline de CI/CD - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Configurar GitHub Actions para compilar imagens Docker e implantar continuamente na VPS Hostinger via SSH quando há push para main.

Depends on:
- Phase 37: Dockerfiles (backend/Dockerfile, web/Dockerfile) + docker-compose.yml
- Phase 38: Caddyfile.prod + docker-compose.prod.yml + DEPLOYMENT.md

</domain>

<decisions>
## Implementation Decisions

### CI Strategy
Build Docker images and push to GHCR (GitHub Container Registry) on push to `main`.
- Registry: `ghcr.io/{owner}/{repo}/backend` and `ghcr.io/{owner}/{repo}/frontend`
- GHCR is free, tightly integrated with GitHub Actions, no external service needed.
- Images tagged with both `latest` and the git SHA for rollback capability.

### Deploy Mechanism
SSH into VPS using `appleboy/ssh-action`. Deploy script:
1. `docker pull ghcr.io/.../backend:latest && docker pull ghcr.io/.../frontend:latest`
2. `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build`
No `--force-recreate` to minimize downtime.

### GitHub Secrets Required
- `VPS_HOST` — VPS IP or hostname
- `VPS_USER` — SSH user (e.g. `root` or `deploy`)
- `VPS_SSH_KEY` — private SSH key (no passphrase)
- `VPS_PORT` — SSH port (default 22)
- `GHCR_PAT` — GitHub Personal Access Token with `read:packages` scope (for VPS docker login)
  These are set in repo Settings → Secrets → Actions. GHCR push uses `GITHUB_TOKEN` (automatic).

### Build Caching
- Maven: `actions/cache` caching `~/.m2/repository`
- pnpm: `actions/cache` caching pnpm store (`~/.local/share/pnpm` or `$PNPM_HOME`)
  Cuts build time from ~5min to ~1min for incremental changes.

### Workflow Trigger
- `push` to `main` branch → full CI (build + push + deploy)
- `pull_request` to `main` → CI only (build, no push/deploy)
  Prevents accidental deploys from feature branches.

### Docker Build Platform
`linux/amd64` — standard Hostinger VPS architecture (x86_64). No multi-arch builds needed.

### Workflow File Location
`.github/workflows/deploy.yml` — single workflow file covering CI and CD jobs.

</decisions>

<code_context>
## Existing Code Insights

- `backend/Dockerfile` — two-stage Maven build, JRE Alpine runtime
- `web/Dockerfile` — three-stage pnpm build, standalone runner
- `docker-compose.yml` — base compose (builds locally via `build:` directive)
- `docker-compose.prod.yml` — prod override; backend/frontend use `image:` keys with `${REGISTRY:-ghcr.io/lexcv}/backend:${IMAGE_TAG:-latest}` pattern (already set in Phase 37)
- `DEPLOYMENT.md` — references docker compose -f ... -f ... up -d pattern
- Repo owner: `tchspprtcv` (from git config)

The `docker-compose.prod.yml` backend/frontend `image:` references use `${REGISTRY:-ghcr.io/lexcv}/backend:${IMAGE_TAG:-latest}`. The CI workflow must set `REGISTRY=ghcr.io/tchspprtcv/lexcv` and `IMAGE_TAG=${{ github.sha }}` (or `latest`) when running on VPS.

</code_context>

<specifics>
## Specific Ideas

Workflow structure (two jobs):
```
build-and-push:
  - checkout
  - set up QEMU + buildx (for linux/amd64)
  - login to GHCR with GITHUB_TOKEN
  - build + push backend image (tagged :latest and :$SHA)
  - build + push frontend image (tagged :latest and :$SHA)
  - cache Maven ~/.m2 and pnpm store

deploy:
  needs: [build-and-push]
  if: github.ref == 'refs/heads/main'
  - SSH into VPS (appleboy/ssh-action)
  - docker login ghcr.io with GHCR_PAT
  - docker compose pull (or docker pull each image)
  - docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build
```

Also add `.github/workflows/ci.yml` (or just one workflow with conditional deploy step).

</specifics>

<deferred>
## Deferred Ideas

- Zero-downtime blue/green deploy (overkill for single VPS)
- Health check before marking deploy successful
- Slack/Discord notification on deploy
- Rollback automation

</deferred>
