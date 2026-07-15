---
phase: 100-infraestrutura-routing-e-deployment
reviewed: 2026-07-15T00:00:00Z
depth: standard
files_reviewed: 11
files_reviewed_list:
  - webpage/Dockerfile
  - webpage/.dockerignore
  - webpage/.gitignore
  - webpage/pnpm-workspace.yaml
  - .github/workflows/deploy.yml
  - Caddyfile
  - Caddyfile.prod
  - docker-compose.yml
  - docker-compose.prod.yml
  - docker-compose.hostinger.yml
  - backend/src/main/java/com/lexcv/controllers/PublicController.java
findings:
  critical: 1
  warning: 3
  info: 5
  total: 9
status: issues_found
---

# Phase 100: Code Review Report

**Reviewed:** 2026-07-15T00:00:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

Reviewed the webpage Docker image, the new CI publish step, the three independent Caddy routing sources (`Caddyfile`, `Caddyfile.prod`, and the heredoc embedded in `docker-compose.hostinger.yml`), the three docker-compose targets (dev/prod/hostinger), and the `PublicController.getBranding()` transactional fix.

What checks out:
- The historical "`$` in the hostinger heredoc breaks Docker Compose interpolation" bug class was **not** reintroduced — the embedded Caddyfile in `docker-compose.hostinger.yml:124-137` contains zero `$` characters (domain is hardcoded, no `{$VAR}` refs), confirmed by full-text inspection.
- The `@webpage` Caddy matcher (`path / /landing-static/*`) is correctly paired with `assetPrefix: "/landing-static"` in `webpage/next.config.ts:12` — the landing page's own `_next` static assets are scoped under `/landing-static/*` rather than the default `/_next/*`, so they don't fall through to the catch-all `frontend` route. This was verified live in `100-04-SUMMARY.md` as well (namespace non-collision, both directions 200).
- `PublicController.getBranding()`'s new `@Transactional(readOnly = true)` is the correct, idiomatic fix for the documented PostgreSQL "Large Objects may not be used in auto-commit mode" error on the `@Lob String logoDataUrl` field — confirmed against `Tenant.java` (`@Lob` field), `TenantRepository`, and the DTO shape, and traced end-to-end against the real consumer (`webpage/src/lib/branding.ts` + `types/branding.ts`), whose `BrandingResponse` type and fail-open handling match the backend response exactly. No regressions introduced by this change.

What doesn't: a genuine, previously-latent bug in the "prod" Caddy wiring (the `caddy` service never receives the environment variables its own mounted Caddyfile depends on), a second instance of the exact "3 sources of truth drift" risk this phase was asked to guard against (the hostinger heredoc silently dropped the MinIO console route that `Caddyfile.prod` has), a stale image-registry default, and some dead/no-op CI and compose configuration. Details below.

## Critical Issues

### CR-01: `caddy` service never receives `DOMAIN_NAME` / `CADDY_MINIO_USER` / `CADDY_MINIO_PASSWORD_HASH` — the documented "prod" deploy path cannot actually bind to its domain

**File:** `docker-compose.yml:106-121`, `docker-compose.prod.yml:40-45` (consumed by `Caddyfile.prod:1,7`)

**Issue:** `Caddyfile.prod` reads three variables from Caddy's own process environment via `{$VAR}` syntax:
```
{$DOMAIN_NAME}, www.{$DOMAIN_NAME} {
    ...
    basicauth {
        {$CADDY_MINIO_USER} {$CADDY_MINIO_PASSWORD_HASH}
    }
```
`.env.example:19-25` defines all three (`DOMAIN_NAME`, `CADDY_MINIO_USER`, `CADDY_MINIO_PASSWORD_HASH`), and `DEPLOYMENT.md` documents exactly this flow: edit `DOMAIN_NAME` in `.env`, then run `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d`.

But neither `docker-compose.yml`'s base `caddy` service nor `docker-compose.prod.yml`'s override adds an `environment:` (or `env_file:`) key for the `caddy` service — every other service that needs runtime config (`postgres`, `backend`, `frontend`, `webpage`) has an explicit `environment:` block; `caddy` has none in either file. Docker Compose's `.env` file is used **only** to interpolate `${VAR}` tokens written literally in the compose YAML text — it is not automatically forwarded into every container's process environment. Since `Caddyfile.prod` is mounted as a file (Compose never touches its contents), Caddy itself is the only thing that ever reads `{$DOMAIN_NAME}` etc., and it reads them from its own container's environment — which will be empty.

Consequence: `{$DOMAIN_NAME}, www.{$DOMAIN_NAME} {` resolves to `, www. {` — an empty primary hostname. This either fails Caddy's Caddyfile parse (container crash-loops under `restart: unless-stopped`, i.e. the reverse proxy — and therefore the whole public site — never comes up) or, if tolerated, Caddy never has the real domain to request/serve a matching Let's Encrypt certificate for. The `/minio-console` basic-auth credentials resolve empty the same way. This affects only the `docker-compose.prod.yml` overlay target (not `docker-compose.hostinger.yml`, which hardcodes its domain and has no basicauth block at all — see WR-01).

Circumstantial support: `docker-compose.prod.yml` also has a stale registry default (WR-02) inconsistent with what's actually published — consistent with this "prod" overlay path being documented but not exercised against a live deploy recently, which is exactly how this class of bug survives.

**Fix:** add the missing `environment:` block where `Caddyfile.prod` is introduced:
```yaml
# docker-compose.prod.yml
  caddy:
    restart: unless-stopped
    environment:
      DOMAIN_NAME: ${DOMAIN_NAME}
      CADDY_MINIO_USER: ${CADDY_MINIO_USER}
      CADDY_MINIO_PASSWORD_HASH: ${CADDY_MINIO_PASSWORD_HASH}
    volumes:
      - ./Caddyfile.prod:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
```
Then verify with `docker compose -f docker-compose.yml -f docker-compose.prod.yml config` (renders the merged config) and a real `up -d` against a throwaway domain before trusting `DEPLOYMENT.md`'s runbook again.

## Warnings

### WR-01: Caddy routing drift — hostinger deployment silently drops MinIO console access that `Caddyfile.prod` provides

**File:** `docker-compose.hostinger.yml:20-41` (no `ports:` for `minio`), `docker-compose.hostinger.yml:113-137` (embedded Caddy heredoc), cf. `Caddyfile.prod:5-10`

**Issue:** This is the exact "3 independent Caddy sources drift" risk this phase was asked to check for — not the historical `$`-character bug (confirmed absent, see Summary), but a different instance of the same class. `Caddyfile.prod` has a `handle_path /minio-console* { basicauth {...} reverse_proxy minio:9001 }` block; the hostinger heredoc's Caddy config has no equivalent block at all (it otherwise matches the plain dev `Caddyfile`, not `Caddyfile.prod`). Combined with the `minio` service in `docker-compose.hostinger.yml` exposing no host ports (unlike dev's `9000:9000`/`9001:9001`), the MinIO admin console is completely unreachable in the hostinger deployment — there is no route to it at all, whereas the parallel "prod" target has one (auth-protected). This looks like the heredoc was copied from the dev `Caddyfile` rather than `Caddyfile.prod` and the console route was simply never ported over.

**Fix:** Either add the missing block to the heredoc, or — better, and this also structurally forecloses the whole "`$`-breaks-the-heredoc" bug class for good — stop maintaining a third, hand-embedded copy of the routing logic and mount the existing `Caddyfile.prod` the same way `docker-compose.prod.yml` does:
```yaml
  caddy:
    image: caddy:2-alpine
    container_name: lexcv_caddy
    ports:
      - "80:80"
      - "443:443"
      - "443:443/udp"
    environment:
      DOMAIN_NAME: alcv.tech
      CADDY_MINIO_USER: ${CADDY_MINIO_USER}
      CADDY_MINIO_PASSWORD_HASH: ${CADDY_MINIO_PASSWORD_HASH}
    volumes:
      - ./Caddyfile.prod:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
      - caddy_config:/config
    depends_on:
      - frontend
      - backend
      - webpage
    networks:
      - lexcv_net
    restart: unless-stopped
```
(`Caddyfile.prod`'s `{$DOMAIN_NAME}, www.{$DOMAIN_NAME}` already produces `alcv.tech, www.alcv.tech` when `DOMAIN_NAME=alcv.tech`.) This removes the entrypoint script, the duplicated routing logic, and this exact drift risk in one move — and note it depends on CR-01's fix (or an equivalent inline one) actually wiring the env vars through.

### WR-02: `docker-compose.prod.yml` default image registry doesn't match what CI actually publishes

**File:** `docker-compose.prod.yml:6,15,24`

**Issue:** All three image references default to `${REGISTRY:-ghcr.io/lexcv}` (e.g. `${REGISTRY:-ghcr.io/lexcv}/webpage:${IMAGE_TAG:-latest}`). `.github/workflows/deploy.yml:10` publishes to `ghcr.io/tchspprtcv/lexcv/*`, and `docker-compose.hostinger.yml:44,80,97` hardcodes the same `ghcr.io/tchspprtcv/lexcv/*` path. Anyone running `docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d` (per `DEPLOYMENT.md`) without explicitly setting `REGISTRY` in `.env` will get `ghcr.io/lexcv/backend:latest` / `.../frontend:latest` / `.../webpage:latest` — none of which exist — and every pull will fail.

**Fix:**
```yaml
  backend:
    image: ${REGISTRY:-ghcr.io/tchspprtcv/lexcv}/backend:${IMAGE_TAG:-latest}
  frontend:
    image: ${REGISTRY:-ghcr.io/tchspprtcv/lexcv}/frontend:${IMAGE_TAG:-latest}
  webpage:
    image: ${REGISTRY:-ghcr.io/tchspprtcv/lexcv}/webpage:${IMAGE_TAG:-latest}
```

### WR-03: CI's "Cache Maven repository" / "Cache pnpm store" steps are dead configuration, and the pnpm cache key ignores the new `webpage` lockfile

**File:** `.github/workflows/deploy.yml:61-76`

**Issue:** Both `actions/cache@v4` steps target paths on the GitHub Actions runner's own filesystem (`~/.m2/repository`, `~/.local/share/pnpm/store`). Every build in this job (`backend`, `frontend`, `webpage`) goes through `docker/build-push-action@v6` (lines 85-124), which runs inside an isolated Buildx/BuildKit context set up by `docker/setup-buildx-action` — it does not share the runner's home directory. None of the three Dockerfiles (`backend/Dockerfile`, `web/Dockerfile`, `webpage/Dockerfile`) declare a `--mount=type=cache` targeting those paths, so nothing inside the Docker builds ever reads from or writes to `~/.m2/repository` or `~/.local/share/pnpm/store`. These two cache steps restore (near-certain miss) and save (near-certain no-op) on every single run for zero effect — the caching that actually works for these builds is the separately configured `cache-from`/`cache-to: type=gha` on each `build-push-action` step. Separately, and regardless of the dead-cache issue: the pnpm cache key (line 74, `hashFiles('web/pnpm-lock.yaml')`) was never updated to also hash the new `webpage/pnpm-lock.yaml` added by this phase, so even a fixed version of this cache would not invalidate correctly when only `webpage`'s dependencies change. The unused `id: pnpm-cache` (line 71) confirms nothing downstream ever consults this step's output either.

**Fix:** Remove both `actions/cache@v4` steps (lines 61-76) — they add CI time for no benefit given the `type=gha` Docker layer cache already in place. If store-level caching inside the Docker build is actually wanted, wire it explicitly per Dockerfile instead, e.g.:
```dockerfile
# webpage/Dockerfile, deps stage
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile --ignore-scripts
```
(requires enabling BuildKit cache export for that mount too). If kept as-is for some other reason, at minimum fix the key: `hashFiles('web/pnpm-lock.yaml', 'webpage/pnpm-lock.yaml')`.

## Info

### IN-01: `NEXT_PUBLIC_API_BASE_PATH` set for the `webpage` service is dead configuration

**File:** `docker-compose.yml:100`, `docker-compose.hostinger.yml:103`

**Issue:** Both files set `NEXT_PUBLIC_API_BASE_PATH: /api/v1` as a runtime container env var for `webpage`. Confirmed via search that `webpage/src` never references `NEXT_PUBLIC_API_BASE_PATH` anywhere — all backend calls go through `BACKEND_API_ORIGIN` server-side (`webpage/src/lib/backend-origin.ts`). Next.js `NEXT_PUBLIC_*` variables are inlined into the client bundle at **build** time; the CI build step for webpage (`.github/workflows/deploy.yml:112-124`) deliberately passes no `build-args` (per its own comment). So this variable can never take effect for `webpage` as currently built, regardless of what value is set at container-run time — it looks like it was copied from the `frontend` service block, which genuinely needs both variables.

**Fix:** Remove the line from both compose files' `webpage` blocks, or, if browser-side use is actually planned, add it as a `build-args` entry in the CI workflow's webpage build step and in `webpage/Dockerfile`'s builder stage (mirroring `web/Dockerfile:14-15`) — a runtime-only env var alone will not do anything for a `NEXT_PUBLIC_*` value.

### IN-02: `webpage/Dockerfile` copies build output as root, then switches to a non-root user

**File:** `webpage/Dockerfile:20-22`

**Issue:**
```dockerfile
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
USER appuser
```
Neither `COPY` uses `--chown=appuser:appgroup`, so the copied files land owned by `root:root` before the container ever switches to `appuser`. This works today only because Next.js's build output happens to be world-readable by default; Next.js's own documented standalone-Docker example sets ownership explicitly for exactly this reason rather than relying on that default. `web/Dockerfile:24-26` has the identical gap (pre-existing, not introduced here), so worth fixing in both while touching this pattern.

**Fix:**
```dockerfile
COPY --from=builder --chown=appuser:appgroup /app/.next/standalone ./
COPY --from=builder --chown=appuser:appgroup /app/.next/static ./.next/static
```

### IN-03: `webpage/.dockerignore` doesn't exclude the local TS build cache, and has a redundant line

**File:** `webpage/.dockerignore:1-8`

**Issue:** `tsconfig.tsbuildinfo` (~139KB, already in `webpage/.gitignore:42`) is not excluded, so a local `docker build`/`docker compose build` picks up whatever stale incremental-build cache happens to sit on the developer's machine as part of the build context (CI is unaffected — a fresh checkout never has this file). Separately, line 5 (`.env.local`) is redundant: line 4's `.env.*` glob already matches it.

**Fix:**
```
node_modules/
.next/
.env
.env.*
*.md
.git/
.gitignore
tsconfig.tsbuildinfo
```

### IN-04: HTTP/3 (UDP 443) enabled for hostinger's Caddy but not for the prod overlay target

**File:** `docker-compose.hostinger.yml:119` vs. `docker-compose.yml:109-111` (inherited by `docker-compose.prod.yml`, which adds no `ports:`)

**Issue:** Hostinger's `caddy` maps `"443:443/udp"` in addition to the TCP ports; the base `docker-compose.yml` (used as-is by the `docker-compose.prod.yml` overlay) only maps TCP `80`/`443`. The two production-ish targets now differ in an HTTP feature unrelated to their environment-specific concerns. Likely harmless (Caddy serves HTTP/1.1 and HTTP/2 fine without it) but worth a deliberate decision rather than accidental drift, especially since WR-01's suggested fix would make hostinger reuse the base file's `caddy` service more directly.

**Fix:** Add `- "443:443/udp"` to `docker-compose.yml`'s `caddy.ports` if HTTP/3 is wanted everywhere, or leave a comment noting it's intentionally hostinger-only.

### IN-05: No frontend lint/build gate in the CI `test` job for either Next.js app

**File:** `.github/workflows/deploy.yml:14-41`

**Issue:** The `test` job (runs on both `push` and `pull_request`) only executes backend checks (`mvn -B verify`, `mvn -B spotbugs:check`). Neither `web/` nor the new `webpage/` app has a lint/typecheck/test step anywhere in CI; the only implicit gate for either is whatever `next build` enforces inside the Docker build in `build-and-push` — which only runs on `push` to `master`, never on a pull request, so PRs get zero automated frontend feedback. Pre-existing gap for `web/`, now inherited rather than closed by `webpage/`'s addition.

**Fix:** Add a lint step to the `test` job, e.g. `pnpm --dir web lint` and `pnpm --dir webpage lint`, so PRs surface frontend issues before merge rather than only at image-build time on `push`.

---

_Reviewed: 2026-07-15T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
