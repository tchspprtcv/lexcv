---
phase: 100-infraestrutura-routing-e-deployment
reviewed: 2026-07-15T17:00:00Z
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
  critical: 0
  warning: 1
  info: 6
  total: 7
status: issues_found
---

# Phase 100: Code Review Report

**Reviewed:** 2026-07-15T17:00:00Z
**Depth:** standard
**Files Reviewed:** 11
**Status:** issues_found

## Summary

This is a re-review of the same 11 files after a fix pass (`100-REVIEW-FIX.md`, iteration 1) that claimed to resolve CR-01, WR-02, and WR-03 from the prior `100-REVIEW.md`, and to explicitly defer WR-01. Per instructions, every claim was re-verified independently against the current file contents and against `git show` on the three cited commits — not taken on trust.

**CR-01 (caddy service missing `DOMAIN_NAME`/`CADDY_MINIO_*` env passthrough) — confirmed fixed.** `docker-compose.prod.yml:41-45` now has the `environment:` block under `caddy:` (`DOMAIN_NAME`, `CADDY_MINIO_USER`, `CADDY_MINIO_PASSWORD_HASH`), added in commit `8ee9e2c` (verified via `git show 8ee9e2c` — pure 4-line addition, correctly placed as a sibling of `restart:`/`volumes:`). Variable names match `Caddyfile.prod:1,7`'s `{$DOMAIN_NAME}`/`{$CADDY_MINIO_USER}`/`{$CADDY_MINIO_PASSWORD_HASH}` exactly, and all three keys exist in root `.env.example:20,24-25`, so the fix is viable end-to-end, not just syntactically present. Docker Compose merge semantics confirmed correct: base `docker-compose.yml`'s `caddy` service has no `environment:` key to conflict with, so the override's block is the entire resulting environment.

**WR-02 (stale `ghcr.io/lexcv` registry default) — confirmed fixed.** All three image lines in `docker-compose.prod.yml` (6, 15, 24) now read `${REGISTRY:-ghcr.io/tchspprtcv/lexcv}`, verified via `git show c5fb2e9` (clean 3-line find/replace, nothing else touched). Cross-checked consistency: `.github/workflows/deploy.yml:10` (`REGISTRY: ghcr.io/tchspprtcv/lexcv`), `docker-compose.hostinger.yml:44,80,97` (hardcoded same path), and root `.env.example:28` (`REGISTRY=ghcr.io/tchspprtcv/lexcv`) all now agree — no remaining path where a wrong default could resurface. Repo-wide grep for `ghcr.io/lexcv` (the old, wrong value) returns zero hits outside `.planning/` historical docs.

**WR-03 (dead CI cache steps) — confirmed fixed.** Both `actions/cache@v4` steps ("Cache Maven repository", "Cache pnpm store") are gone from `.github/workflows/deploy.yml`'s `build-and-push` job, verified via `git show 16b8111` (pure 17-line deletion, `Log in to GHCR` step immediately follows with no gap). No dangling reference to the removed `id: pnpm-cache` remains anywhere in `.github/`. The legitimate, unrelated `cache: maven` built into `actions/setup-java@v4` (line 28, `test` job — this one runs `mvn` directly on the runner and genuinely benefits) was correctly left untouched.

**WR-01 (hostinger Caddy heredoc missing MinIO console route) — confirmed still open, exactly as the fix report states.** `docker-compose.hostinger.yml` was not touched by any of the three fix commits. Its embedded heredoc (lines 124-137) still has no `handle_path /minio-console*` block, and the `minio` service (lines 20-41) still exposes no host ports — the console remains completely unreachable in this deployment target while `Caddyfile.prod`'s equivalent route (auth-protected) exists for the `docker-compose.prod.yml` target. Retained below as WR-01.

Beyond re-verifying the four escalated items, this pass also re-confirmed all five Info items from the prior review are still present and unaddressed (none were in the fix pass's scope), traced several cross-file claims embedded in the reviewed files' own comments to make sure they still hold (`SecurityConfig.java` still `permitAll()`s `/api/v1/public/branding`; `TenantRepository.findFirstByOrderByCreatedAtAsc()` compiles against `Tenant.createdAt`; `webpage/next.config.ts` still sets `output: "standalone"` + `assetPrefix: "/landing-static"`, matching the Dockerfile and Caddy matcher; `webpage/src/app/page.tsx` still declares `export const dynamic = "force-dynamic"`, confirming the CI comment that no env var is needed at webpage build time), and found one new Info-level finding (IN-06, unpinned `pnpm@latest` in `webpage/Dockerfile`, mirroring a pre-existing pattern in `web/Dockerfile`) that neither review pass had flagged before.

## Critical Issues

None. CR-01 is verified fixed (see Summary).

## Warnings

### WR-01: Caddy routing drift — hostinger deployment still silently drops MinIO console access that `Caddyfile.prod` provides

**File:** `docker-compose.hostinger.yml:20-41` (no `ports:` for `minio`), `docker-compose.hostinger.yml:113-147` (embedded Caddy heredoc), cf. `Caddyfile.prod:5-10`

**Issue:** Unchanged from the prior review and confirmed still present. `Caddyfile.prod` has a `handle_path /minio-console* { basicauth {...} reverse_proxy minio:9001 }` block; the hostinger heredoc's Caddy config (lines 124-137) has no equivalent — it otherwise matches the plain dev `Caddyfile`, not `Caddyfile.prod`. Combined with `minio` exposing no host ports in this file (unlike dev's `9000:9000`/`9001:9001`), there is currently no path at all — neither reverse-proxied nor direct — to the MinIO admin console on the hostinger target.

Note the `minio` service's lack of host ports is itself correct/intentional hardening (mirrors how `Caddyfile.prod` reaches MinIO only via an authenticated reverse-proxy route rather than a raw exposed port) — the fix must add the missing Caddy route, not add a host port mapping, or it would reintroduce an unauthenticated exposure.

**Fix (minimal, matches this file's existing pattern):** add the missing block to the heredoc:
```
    handle_path /minio-console* {
        basicauth {
            {$CADDY_MINIO_USER} {$CADDY_MINIO_PASSWORD_HASH}
        }
        reverse_proxy minio:9001
    }
```
inserted after the `/api/*` handle block, before `@webpage`. This requires adding `CADDY_MINIO_USER`/`CADDY_MINIO_PASSWORD_HASH` to the `caddy` service's `environment:` in this file too (it currently has none — this file has no `environment:` key for `caddy` at all, only the base/prod overlay pair does since CR-01's fix).

**Fix (structural, forecloses the 3-sources-of-truth drift risk permanently):** now that CR-01 has wired the env-var passthrough pattern for `docker-compose.prod.yml`, the same pattern can be replicated here since this file is self-contained (not an overlay):
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
This removes the `entrypoint:` heredoc script entirely, eliminating this exact drift class for good (one Caddy routing source instead of three).

## Info

### IN-01: `NEXT_PUBLIC_API_BASE_PATH` set for the `webpage` service is dead configuration

**File:** `docker-compose.yml:100`, `docker-compose.hostinger.yml:103`

**Issue:** Still present, unaddressed. Both files set `NEXT_PUBLIC_API_BASE_PATH: /api/v1` as a runtime container env var for `webpage`. `webpage/src` never references this variable — all backend calls resolve `BACKEND_API_ORIGIN` server-side via `webpage/src/lib/backend-origin.ts`, confirmed again this pass. `NEXT_PUBLIC_*` variables are inlined at Next.js **build** time, and `webpage/Dockerfile` (unlike `web/Dockerfile:14-15`) declares no corresponding `ARG`/`ENV` in its builder stage, and the CI build step for `webpage` (`.github/workflows/deploy.yml`, "Build and push webpage image") passes no `build-args` — so this runtime value can never take effect.

**Fix:** Remove the line from both compose files' `webpage` blocks, or wire it as a build-arg in both the CI step and `webpage/Dockerfile`'s builder stage if browser-side use is actually planned.

### IN-02: `webpage/Dockerfile` copies build output as root, then switches to a non-root user

**File:** `webpage/Dockerfile:20-22`

**Issue:** Still present, unaddressed.
```dockerfile
COPY --from=builder /app/.next/standalone ./
COPY --from=builder /app/.next/static ./.next/static
USER appuser
```
Neither `COPY` uses `--chown=appuser:appgroup`, so files land owned by `root:root` before the switch to `appuser`. Works today only because Next.js's standalone output happens to be world-readable by default. `web/Dockerfile:24-26` has the identical gap (pre-existing, not introduced by this phase).

**Fix:**
```dockerfile
COPY --from=builder --chown=appuser:appgroup /app/.next/standalone ./
COPY --from=builder --chown=appuser:appgroup /app/.next/static ./.next/static
```

### IN-03: `webpage/.dockerignore` doesn't exclude the local TS build cache, and has a redundant line

**File:** `webpage/.dockerignore:1-8`

**Issue:** Still present, unaddressed. `tsconfig.tsbuildinfo` (already in `webpage/.gitignore:42`) is not excluded, so a local `docker build` picks up a stale incremental-build cache from the developer's machine (CI is unaffected — fresh checkout). Line 5 (`.env.local`) is redundant given line 4's `.env.*` glob already matches it.

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

**File:** `docker-compose.hostinger.yml:119` vs. `docker-compose.yml:109-111` (inherited unchanged by `docker-compose.prod.yml`, which adds no `ports:` override)

**Issue:** Still present, unaddressed. Hostinger's `caddy` maps `"443:443/udp"` in addition to TCP; the base file (used by the prod overlay) only maps TCP `80`/`443`. Likely harmless (Caddy serves HTTP/1.1 and HTTP/2 fine without it) but the two production-ish targets now differ in an HTTP feature unrelated to their environment-specific concerns.

**Fix:** Add `- "443:443/udp"` to `docker-compose.yml`'s `caddy.ports` if HTTP/3 is wanted everywhere, or leave a comment noting it's intentionally hostinger-only.

### IN-05: No frontend lint/build gate in the CI `test` job for either Next.js app

**File:** `.github/workflows/deploy.yml:14-41`

**Issue:** Still present, unaddressed (WR-03's fix only removed dead cache steps from the separate `build-and-push` job; the `test` job's contents are unchanged). The `test` job (runs on both `push` and `pull_request`) only executes backend checks (`mvn -B verify`, `mvn -B spotbugs:check`). Neither `web/` nor `webpage/` has a lint/typecheck step in CI; the only implicit gate is `next build` inside the Docker image build, which only runs on `push` to `master` (gated by `if: github.event_name == 'push'`), never on a pull request — so PRs get zero automated frontend feedback.

**Fix:** Add `pnpm --dir web lint` and `pnpm --dir webpage lint` to the `test` job.

### IN-06: `webpage/Dockerfile` pins nothing for the pnpm version it installs via corepack

**File:** `webpage/Dockerfile:4,11`

**Issue:** New finding, not present in the prior review. Both the `deps` and `builder` stages run:
```dockerfile
RUN corepack enable && corepack prepare pnpm@latest --activate
```
`pnpm@latest` is resolved fresh from the npm registry at image-build time — there is no `packageManager` field in `webpage/package.json` (confirmed absent) that `corepack prepare` would otherwise honor, and `@latest` explicitly overrides any such pin even if one existed. This means two builds of the identical commit, weeks apart, can install different pnpm major versions, with no guarantee that `--frozen-lockfile`'s behavior against the committed `pnpm-lock.yaml` stays consistent across pnpm versions, and with no reproducibility guarantee for the build tool itself. Notably, this sits in the same file as `webpage/pnpm-workspace.yaml`'s `minimumReleaseAgeExclude` mechanism (a deliberate supply-chain guard against just-published npm packages) — pinning application dependencies' freshness while leaving the package manager itself fully unpinned is an inconsistent security posture. This exact pattern is copied verbatim from `web/Dockerfile:4,11` (pre-existing, not newly introduced by this phase, but now duplicated into a second file).

**Fix:** Pin an explicit version, e.g.:
```dockerfile
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate
```
(matching whatever version generated the committed `pnpm-lock.yaml`), or add a `"packageManager": "pnpm@9.15.0"` field to `package.json` and change both `corepack prepare` invocations to drop `@latest` (`corepack prepare --activate`), which makes corepack read the pin from `package.json` instead. Apply to both `webpage/Dockerfile` and `web/Dockerfile` for consistency.

---

_Reviewed: 2026-07-15T17:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
