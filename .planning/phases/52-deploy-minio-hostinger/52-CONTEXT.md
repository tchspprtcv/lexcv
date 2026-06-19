# Phase 52: Deploy MinIO no Hostinger — Context

**Gathered:** 2026-06-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Add MinIO as a Docker Compose service to the production Hostinger VPS deployment. Three files change:

1. **`docker-compose.yml`** (base) — add `minio` service with named volume and healthcheck
2. **`docker-compose.prod.yml`** (prod overlay) — add `minio` with resource limits; add MinIO env vars to `backend` service override
3. **`Caddyfile.prod`** — expose MinIO console (`/minio-console` or subdomain `minio.{$DOMAIN_NAME}`) via reverse proxy on port 9001
4. **`.github/workflows/deploy.yml`** — currently has only `build-and-push` job; add a `deploy` job that SSHs into Hostinger VPS, runs `docker compose pull && docker compose up -d` to restart all services including MinIO
5. **`backend/docker-compose.yml`** — add MinIO env vars to backend service so the running container picks them up from the host `.env`

No MinIO image is built — use `minio/minio:latest` directly. No Dockerfile changes.
</domain>

<decisions>
## Implementation Decisions

### MinIO service definition
- Image: `minio/minio:latest`
- Command: `server /data --console-address ":9001"`
- Data volume: `lexcv_minio_data:/data` (named volume for persistence across restarts)
- Ports: NOT exposed directly — Caddy proxies traffic. Port 9000 (S3 API) is backend-internal only. Port 9001 (console) proxied by Caddy.
- Environment: `MINIO_ROOT_USER: ${MINIO_ROOT_USER}`, `MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}` — no hardcoded values
- Healthcheck: `mc ready local` equivalent → use `curl -f http://localhost:9000/minio/health/live || exit 1`
- Network: `lexcv_net` (same as all other services)
- `restart: unless-stopped`

### Credentials (MIN-10)
- `MINIO_ROOT_USER` and `MINIO_ROOT_PASSWORD` — injected via VPS `.env` file (same pattern as DB creds)
- Backend env vars added to docker-compose.yml: `MINIO_ENDPOINT`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `MINIO_BUCKET_NAME`, `MINIO_PUBLIC_ENDPOINT` — all using `${VAR}` from host env
- No credential values in any committed file

### Caddy MinIO console (MIN-12)
- Add route in `Caddyfile.prod`: under `{$DOMAIN_NAME}` block, add `handle /minio-console*` that reverse_proxies to `minio:9001`
- Caddy handles TLS automatically (same as current `{$DOMAIN_NAME}` block)
- Basic auth on the console route via Caddy `basicauth` directive using `{$CADDY_MINIO_USER}` and `{$CADDY_MINIO_PASSWORD_HASH}` (bcrypt hash) — keeps MinIO console inaccessible without credentials even if MINIO_ROOT credentials are known

### GitHub Actions deploy job (MIN-11)
- Add `deploy` job that depends on `build-and-push` (only runs on `push` to `master`, not on PRs)
- Uses `appleboy/ssh-action` to SSH into the VPS
- Commands on VPS:
  1. `cd /opt/lexcv`
  2. `docker compose -f docker-compose.yml -f docker-compose.prod.yml pull`
  3. `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`
- Requires secrets: `VPS_HOST`, `VPS_USER`, `VPS_SSH_KEY` (already added to GitHub secrets per v1.8 deploy pattern)

### Backend env vars in docker-compose.yml
- Add to backend service environment block:
  ```
  MINIO_ENDPOINT: ${MINIO_ENDPOINT}
  MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
  MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
  MINIO_BUCKET_NAME: ${MINIO_BUCKET_NAME}
  MINIO_PUBLIC_ENDPOINT: ${MINIO_PUBLIC_ENDPOINT:-${MINIO_ENDPOINT}}
  ```
- Also add `depends_on: minio: condition: service_healthy` to backend service

### Volumes
- `lexcv_minio_data` added to top-level `volumes:` in `docker-compose.yml`
- No change to `docker-compose.prod.yml` volumes (inherits from base)
</decisions>

<code_context>
## Existing Code

### Files to modify
- `docker-compose.yml` — add `minio` service; add `lexcv_minio_data` volume; add MinIO env vars to backend; add backend depends_on minio
- `docker-compose.prod.yml` — add `minio` resource limits overlay; pass backend MinIO env vars override
- `Caddyfile.prod` — add `/minio-console` route with basicauth + reverse_proxy to minio:9001
- `.github/workflows/deploy.yml` — add `deploy` job after `build-and-push`

### Existing pattern (from docker-compose.yml)
- Named volumes: `lexcv_pgdata`, `lexcv_uploads`, `caddy_data`, `caddy_config`
- All env vars via `${VAR}` from host .env — NO defaults for secrets
- Services on `lexcv_net` bridge network
- Resource limits in `docker-compose.prod.yml` overlay

### deploy.yml current state
- Only has `build-and-push` job — no deploy step exists yet
- Secrets available: VPS_HOST, VPS_USER, VPS_SSH_KEY (assumed from v1.8 state)
- Uses `appleboy/ssh-action@v1` for SSH (standard for this project)

### MinIO bucket name
- Application expects bucket to be created on startup (`StorageService` ApplicationRunner)
- Bucket name comes from `MINIO_BUCKET_NAME` env var — no hardcoded name
</code_context>

<deferred>
## Deferred

- MinIO bucket lifecycle policies (auto-expiry for old objects)
- Multi-site MinIO replication
- Monitoring/alerting for MinIO disk usage
- Migration of existing filesystem uploads to MinIO (separate one-shot script)
</deferred>
