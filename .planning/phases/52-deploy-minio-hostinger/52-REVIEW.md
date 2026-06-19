---
phase: 52-deploy-minio-hostinger
reviewed: 2026-06-19T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - docker-compose.yml
  - docker-compose.prod.yml
  - Caddyfile.prod
  - .github/workflows/deploy.yml
  - .planning/phases/52-deploy-minio-hostinger/52-CONTEXT.md
findings:
  critical: 2
  warning: 3
  info: 1
  total: 6
status: issues_found
---

# Phase 52: Code Review Report

**Reviewed:** 2026-06-19
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Five files were reviewed for the MinIO-on-Hostinger deployment phase. No hardcoded credentials were found — all secrets are injected via `${VAR}` environment variable references. The `deploy` job is correctly gated to `refs/heads/master` (line 84 of `deploy.yml`) and SSH uses `secrets.*` exclusively. Named volumes are used correctly. Two critical issues were found: a path-stripping bug in `Caddyfile.prod` that will cause the MinIO console to return 404, and the `build-and-push` job running on PRs targeting master (which constitutes an unnecessary attack surface for privilege escalation). Three warnings cover image pinning, missing `restart` on `backend` in the base compose file, and the `minio/minio:latest` tag in production.

## Structural Findings (fallow)

No structural pre-pass was provided for this phase.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: MinIO console prefix not stripped before proxying — all requests will 404

**File:** `Caddyfile.prod:5-9`
**Issue:** The `handle /minio-console*` block reverse-proxies the raw path (including `/minio-console`) to `minio:9001`. The MinIO web console does not understand the `/minio-console` prefix: it serves assets and API calls from `/` (e.g. `/login`, `/api/v1/...`). Every request will land on an unknown path inside the container and return a 404 or redirect loop. Caddy's `handle` directive does not strip the matched prefix automatically — `uri strip_prefix` or `handle_path` must be used.

**Fix:** Replace `handle` with `handle_path`, which strips the matched prefix before proxying:

```caddy
{$DOMAIN_NAME} {
    handle /api/* {
        reverse_proxy backend:8080
    }
    handle_path /minio-console* {
        basicauth {
            {$CADDY_MINIO_USER} {$CADDY_MINIO_PASSWORD_HASH}
        }
        reverse_proxy minio:9001
    }
    handle {
        reverse_proxy frontend:3000
    }
}
```

`handle_path /minio-console*` strips `/minio-console` from the URI before the request reaches the upstream, so `GET /minio-console/login` becomes `GET /login` on `minio:9001`. Alternatively keep `handle` and add `uri strip_prefix /minio-console` inside the block, but `handle_path` is the idiomatic Caddy v2 form.

---

### CR-02: `build-and-push` job runs on every PR targeting master — image push is correctly conditional, but the job exposes `GITHUB_TOKEN` write-permissions to PR code

**File:** `.github/workflows/deploy.yml:3-6`
**Issue:** The workflow triggers on both `push` to `master` and `pull_request` targeting `master`:

```yaml
on:
  push:
    branches: [master]
  pull_request:
    branches: [master]
```

The `build-and-push` job is granted `packages: write` permission. On a `pull_request` event the `push:` condition on lines 59/71 (`github.ref == 'refs/heads/master'`) is false, so images are not pushed — but the job still runs and still holds the `packages: write` token for the duration of the build. A contributor who can open a PR (including fork PRs in a public repo) gets code execution inside a runner that carries a write-capable `GITHUB_TOKEN`. This is a privilege-escalation vector: malicious build steps injected via the Dockerfile or build context can exfiltrate or abuse that token.

Additionally, the `deploy` job is correctly conditional (`if: github.ref == 'refs/heads/master'`, line 84), so deployment is safe — but the CI build cost and the token exposure remain.

**Fix:** Restrict the `build-and-push` job to `push` events only, or drop the `pull_request` trigger entirely if PR builds are not needed:

```yaml
on:
  push:
    branches: [master]
```

If pre-merge build validation is desired for PRs, create a separate `ci` job without `packages: write` that builds but does not push.

---

## Warnings

### WR-01: `minio/minio:latest` tag used in production — uncontrolled upgrades

**File:** `docker-compose.yml:20`
**Issue:** `image: minio/minio:latest` will silently pull a new upstream release each time `docker compose pull` runs during deployment. MinIO has had breaking changes between major releases (e.g. the AGPL relicensing and erasure-coding API changes between RELEASE.2023-* and RELEASE.2024-*). A routine deploy could pull a breaking version and take the storage layer offline.

**Fix:** Pin to a specific MinIO release tag, e.g.:

```yaml
image: minio/minio:RELEASE.2025-05-24T17-08-30Z
```

Update the pinned tag deliberately as part of a planned upgrade, not on every deploy.

---

### WR-02: `backend` service missing `restart: unless-stopped` in base `docker-compose.yml`

**File:** `docker-compose.yml:37`
**Issue:** `postgres` (line 2), `minio` (line 35), `caddy` (implicitly via prod overlay), and `frontend` (prod overlay) all carry `restart: unless-stopped`, but `backend` does not set a restart policy in `docker-compose.yml`. `docker-compose.prod.yml` adds it for `backend` (line 8), so production is fine — but a developer running the base compose file alone (or `docker-compose.yml` without the prod overlay) will not get automatic restarts for the backend, which is inconsistent and can obscure crash loops during local debugging sessions. Also, if the prod overlay merge ever changes, the gap could silently persist into production.

**Fix:** Add `restart: unless-stopped` to the `backend` service in `docker-compose.yml` for consistency:

```yaml
  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: lexcv_backend
    restart: unless-stopped
    depends_on:
      ...
```

---

### WR-03: `basicauth` password hash sourced from env var — Caddy silently accepts plaintext passwords if hash format is wrong

**File:** `Caddyfile.prod:6-8`
**Issue:** `{$CADDY_MINIO_PASSWORD_HASH}` is expected to be a bcrypt hash (Caddy's `basicauth` accepts bcrypt `$2a$` hashes or `$2b$` hashes). If the operator sets `CADDY_MINIO_PASSWORD_HASH` to a plaintext string by mistake, Caddy will reject startup — but there is no enforcement or documentation in the Caddyfile itself. More critically, if Caddy ever changes its fallback behavior, or if the operator accidentally provides a hash of the wrong format, the console could be exposed without authentication.

This is a configuration-time risk rather than a runtime bug, but since the `basicauth` on the MinIO console is identified as MIN-12 (a security control), the absence of a visible reminder is a quality gap.

**Fix:** Add an inline comment in `Caddyfile.prod` clarifying the expected format, and document in the deployment runbook that `CADDY_MINIO_PASSWORD_HASH` must be generated with `caddy hash-password --plaintext '<password>'`:

```caddy
handle_path /minio-console* {
    # CADDY_MINIO_PASSWORD_HASH must be a bcrypt hash: caddy hash-password --plaintext 'yourpass'
    basicauth {
        {$CADDY_MINIO_USER} {$CADDY_MINIO_PASSWORD_HASH}
    }
    reverse_proxy minio:9001
}
```

---

## Info

### IN-01: Deploy script does not prune old images — disk will fill over time

**File:** `.github/workflows/deploy.yml:93-96`
**Issue:** The deploy script runs `docker compose pull` and `docker compose up -d`, but does not prune the old images that are replaced. Each deploy to the VPS leaves the previous tagged image on disk. Given that each backend and frontend image can be 200-500 MB, 20-30 deploys will consume 4-10 GB of disk space without intervention.

**Fix:** Add a prune step after the compose up:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
docker image prune -f
```

`docker image prune -f` removes dangling (untagged, unreferenced) images only — it will not remove the currently running image layers.

---

_Reviewed: 2026-06-19_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
