---
phase: 39-pipeline-ci-cd
plan: "01"
subsystem: devops
tags: [ci-cd, github-actions, docker, ghcr, deployment]
dependency_graph:
  requires: [Phase 37 (Dockerfiles), Phase 38 (docker-compose.prod.yml, DEPLOYMENT.md)]
  provides: [automated-ci-cd-pipeline, ghcr-image-push, vps-ssh-deploy]
  affects: [DEPLOYMENT.md, .github/workflows/]
tech_stack:
  added: [GitHub Actions, appleboy/ssh-action@v1.2.0, docker/build-push-action@v6, GHCR]
  patterns: [build-and-push + deploy two-job workflow, matrix caching (maven + pnpm)]
key_files:
  created:
    - .github/workflows/deploy.yml
  modified:
    - DEPLOYMENT.md
decisions:
  - "IMAGE_TAG=latest on VPS (not SHA) because docker-compose.prod.yml default tag is :latest and :latest is always updated by push step"
  - "No --force-recreate to minimize downtime (per CONTEXT.md)"
  - "linux/amd64 only — Hostinger VPS is x86_64, no multi-arch needed"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-16"
  tasks_completed: 2
  tasks_total: 3
  files_created: 1
  files_modified: 1
---

# Phase 39 Plan 01: CI/CD Pipeline Summary

**One-liner:** GitHub Actions workflow that builds backend and frontend Docker images, pushes them to GHCR tagged `:latest` and `:$SHA`, and deploys to Hostinger VPS via SSH using `appleboy/ssh-action@v1.2.0`.

## What Was Built

### Task 1 — `.github/workflows/deploy.yml` (commit ca53756)

Two-job workflow:

**Job `build-and-push`** (ubuntu-latest, permissions: packages: write):
- `actions/checkout@v4`
- Docker QEMU + Buildx setup for linux/amd64 builds
- Maven `~/.m2/repository` cache keyed on `backend/pom.xml`
- pnpm store `~/.local/share/pnpm/store` cache keyed on `web/pnpm-lock.yaml`
- GHCR login via `docker/login-action@v3` using `GITHUB_TOKEN`
- Backend image build+push: `ghcr.io/tchspprtcv/lexcv/backend:{latest,SHA}` — push only on main
- Frontend image build+push: `ghcr.io/tchspprtcv/lexcv/frontend:{latest,SHA}` with build-args `NEXT_PUBLIC_API_BASE_PATH` and `BACKEND_API_ORIGIN` — push only on main
- GHA layer cache (`type=gha`) scoped per image for maximum reuse

**Job `deploy`** (needs: build-and-push, if: refs/heads/main only):
- SSH into VPS via `appleboy/ssh-action@v1.2.0` using `secrets.VPS_SSH_KEY`
- `docker login ghcr.io` with `GHCR_PAT` via `--password-stdin`
- `docker compose -f docker-compose.yml -f docker-compose.prod.yml pull`
- `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d --no-build`
- `docker image prune -f` to remove dangling images

### Task 2 — `DEPLOYMENT.md` secrets section (commit d6bd7d8)

Appended `## GitHub Actions — Required Secrets` section with:
- Table of all five secrets (`VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY`, `VPS_PORT`, `GHCR_PAT`) with descriptions and examples
- Note that `GITHUB_TOKEN` is automatic
- Workflow behavior table (push to main vs PR to main)
- First Deploy Checklist (5 pre-conditions before first pipeline run)

### Task 3 — Checkpoint (awaiting human verification)

Pipeline must be triggered by a push to main and verified end-to-end on the VPS.

## Deviations from Plan

None — plan executed exactly as written.

## Security Notes (Threat Model Compliance)

| Threat | Mitigation Applied |
|---|---|
| T-39-01: Tampered images | `permissions: packages: write` scoped to workflow only; images built from pinned Dockerfiles |
| T-39-02: VPS_SSH_KEY in logs | Used as `key:` input to appleboy/ssh-action — never echoed; appleboy masks it |
| T-39-03: GHCR_PAT in logs | Piped via `--password-stdin`; GitHub Actions masks all `secrets.*` references |
| T-39-04: Arbitrary code exec on VPS | `if: github.ref == 'refs/heads/main'` restricts deploy; branch protection on main enforces PR review |
| T-39-05: Disk exhaustion from images | `docker image prune -f` runs after every deploy |
| T-39-SC: Floating action versions | All actions pinned: checkout@v4, build-push-action@v6, ssh-action@v1.2.0, login-action@v3, cache@v4 |

## Commits

| Task | Commit | Description |
|---|---|---|
| Task 1 | ca53756 | feat(39-01): create GitHub Actions CI/CD workflow |
| Task 2 | d6bd7d8 | docs(39-01): append GitHub Actions secrets section to DEPLOYMENT.md |

## Self-Check: PASSED

- `.github/workflows/deploy.yml` exists: PASS
- `appleboy/ssh-action@v1.2.0` present (count=1): PASS
- `docker/build-push-action@v6` present (count=2): PASS
- `actions/cache@v4` present (count=2): PASS
- `needs: [build-and-push]` present (count=1): PASS
- `ghcr.io/tchspprtcv/lexcv` present (count=2): PASS
- `docker compose.*docker-compose.prod.yml` present (count=2): PASS
- `docker image prune -f` present (count=1): PASS
- `--force-recreate` absent (count=0): PASS
- `VPS_SSH_KEY` in DEPLOYMENT.md (count=1): PASS
- `GHCR_PAT` in DEPLOYMENT.md (count=1): PASS
- `First Deploy Checklist` in DEPLOYMENT.md (count=1): PASS
- Commits ca53756 and d6bd7d8 exist: PASS
