---
phase: 100-infraestrutura-routing-e-deployment
plan: 02
subsystem: infra
tags: [caddy, docker-compose, reverse-proxy, routing, multi-zone, next-js]

# Dependency graph
requires:
  - phase: 100-01
    provides: "webpage/Dockerfile — buildable 3-stage standalone image, confirmed building successfully per Orchestrator Resolution in 100-01-SUMMARY.md"
provides:
  - "Caddyfile (dev) + Caddyfile.prod — narrow @webpage handle block (path / /landing-static/*) routing to webpage:3000, inserted before the frontend catch-all, both validated with `caddy validate`"
  - "docker-compose.yml — webpage service (build ./webpage, container lexcv_webpage, port 3004:3000, lexcv_net) + caddy depends_on now includes webpage"
  - "docker-compose.prod.yml — webpage override (GHCR image ghcr.io/lexcv/webpage, 0.5cpu/256M limits), mirroring the frontend override exactly"
affects: [100-03-hostinger-compose-and-caddy-heredoc, 100-04-full-stack-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Next.js Multi-Zones via Caddy named matcher: @webpage { path / /landing-static/* } + handle @webpage (never handle_path, so the assetPrefix is NOT stripped) — identical block transcribed into both Caddy sources this plan owns"
    - "webpage service definition mirrors frontend's shape exactly in both dev (build context + container_name + depends_on backend + env + networks + ports) and prod override (image + restart + deploy.resources.limits) — same pattern repeated per compose file, not templated"

key-files:
  created: []
  modified:
    - Caddyfile
    - Caddyfile.prod
    - docker-compose.yml
    - docker-compose.prod.yml

key-decisions:
  - "Transcribed ARCHITECTURE.md's exact before/after reference blocks verbatim for all 4 files rather than re-deriving — plan explicitly required this to avoid drift from the milestone's own architecture research"
  - "Task 2's acceptance-criteria grep depth (-A6 from 'caddy:') undercounts on this environment's Docker Compose CLI version, which renders each depends_on entry as a 3-line condition/required block instead of a 1-line list item — verified the underlying substance (webpage present in caddy's depends_on) via both the raw source YAML and a deeper-context grep; not a code defect, no fix needed"

requirements-completed: [LP-14, LP-15]

# Metrics
duration: ~6min
completed: 2026-07-15
---

# Phase 100 Plan 02: Infraestrutura — Routing e Deployment (Caddy + Compose wiring, non-hostinger) Summary

**Added the narrow `@webpage` Caddy routing block (path `/` + `/landing-static/*`, never a glob) to both `Caddyfile` and `Caddyfile.prod`, and wired the `webpage` service into `docker-compose.yml` (built from `./webpage`, port 3004) plus its GHCR-image override in `docker-compose.prod.yml` — both Caddy sources validate with `caddy validate` and both compose renders (dev + prod overlay) succeed with `webpage` present as a peer of `frontend`/`backend`.**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-07-15T14:17:09-01:00 (approx., base commit timestamp)
- **Completed:** 2026-07-15T14:23:24-01:00
- **Tasks:** 2 completed (both `type="auto"`)
- **Files modified:** 4

## Accomplishments
- `Caddyfile` (dev): inserted `@webpage { path / /landing-static/* }` + `handle @webpage { reverse_proxy webpage:3000 }` between the existing `handle /api/*` block and the catch-all `handle { reverse_proxy frontend:3000 }`. Validated with `caddy validate` — `Valid configuration`.
- `Caddyfile.prod`: inserted the identical `@webpage` block between the `handle_path /minio-console*` block and the catch-all, leaving `{$DOMAIN_NAME}`/`{$CADDY_MINIO_*}` native Caddy templating untouched (safe in a real mounted file, per plan's explicit guidance). Validated with dummy `DOMAIN_NAME`/`CADDY_MINIO_USER`/`CADDY_MINIO_PASSWORD_HASH` env vars — `Valid configuration`.
- `docker-compose.yml`: added `webpage` service (build context `./webpage`, `container_name: lexcv_webpage`, `depends_on: [backend]`, `BACKEND_API_ORIGIN`/`NEXT_PUBLIC_API_BASE_PATH` env, `lexcv_net` network, port `3004:3000`) after `frontend` and before `caddy`; `caddy`'s `depends_on` extended from `[frontend, backend]` to `[frontend, backend, webpage]`.
- `docker-compose.prod.yml`: added `webpage` override (`image: ${REGISTRY:-ghcr.io/lexcv}/webpage:${IMAGE_TAG:-latest}`, `restart: unless-stopped`, `deploy.resources.limits` 0.5 cpu / 256M) alongside the existing `frontend` override, byte-for-byte mirroring its shape.
- All acceptance criteria for both tasks verified passing (see Verification below).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add @webpage routing block to Caddyfile (dev) and Caddyfile.prod** - `99f08a4` (feat)
2. **Task 2: Add webpage service to docker-compose.yml + docker-compose.prod.yml, caddy depends_on webpage** - `92893d7` (feat)

**Plan metadata:** SUMMARY commit follows this file (see below).

## Files Created/Modified
- `Caddyfile` - narrow `@webpage` handle block added (dev)
- `Caddyfile.prod` - narrow `@webpage` handle block added (prod, real mounted file)
- `docker-compose.yml` - new `webpage` service + `caddy` depends_on gained `webpage`
- `docker-compose.prod.yml` - new `webpage` override (GHCR image + resource limits)

## Decisions Made
- Followed the plan's reference blocks transcription verbatim for all 4 files, per the plan's own explicit instruction to transcribe rather than re-derive from `ARCHITECTURE.md`.
- No architectural changes needed — this plan is pure "wire the pre-built pieces together" work; `webpage/Dockerfile` (from plan 100-01) and the routing/compose shapes were both fully specified in `ARCHITECTURE.md`.

## Verification

- **Caddy validate (dev):** `MSYS_NO_PATHCONV=1 docker run --rm -v "$(pwd)/Caddyfile:/etc/caddy/Caddyfile:ro" caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile --adapter caddyfile` → `Valid configuration`.
- **Caddy validate (prod, dummy env):** same command against `Caddyfile.prod` with `DOMAIN_NAME=example.test CADDY_MINIO_USER=u CADDY_MINIO_PASSWORD_HASH=eA==` → `Valid configuration`.
- **Matcher narrowness (T-100-01 guard):** `grep -n 'path / /landing-static/\*' Caddyfile Caddyfile.prod` → present in both (line 7 and line 13 respectively); `grep -Ec 'path\s+/\*|path\s+\*' Caddyfile Caddyfile.prod` → `0` for each (no broad glob).
- **handle vs handle_path (T-100-03 guard):** `grep -n 'handle @webpage'` → present in both files; `grep -c 'handle_path @webpage'` → `0` in both files.
- **Existing routes untouched:** `handle /api/*` and `reverse_proxy frontend:3000` confirmed present, unchanged, in both files.
- **`reverse_proxy webpage:3000` count:** exactly `1` in each of `Caddyfile`/`Caddyfile.prod`.
- **Compose renders:** `docker compose -f docker-compose.yml config` and the overlaid `-f docker-compose.yml -f docker-compose.prod.yml config` both exit 0 (`COMPOSE_OK`); `webpage` service present in both renders (build context resolves to `./webpage`; prod overlay shows `image: ghcr.io/lexcv/webpage:latest` + `cpus: 0.5` / `memory: "268435456"` (256M), matching the pre-existing `frontend` override's merge pattern exactly).
- **caddy depends_on includes webpage (dev):** confirmed both via direct source read (`docker-compose.yml` lines 116-119: `depends_on: [frontend, backend, webpage]`) and via `docker compose config` render (caddy's rendered `depends_on` block lists `backend`/`frontend`/`webpage`, each with `condition: service_started`).
- **No other service definitions altered:** `git diff docker-compose.yml` / `git diff docker-compose.prod.yml` confirm only the new `webpage` blocks were added plus the single `- webpage` line in caddy's `depends_on` — `postgres`, `minio`, `backend` untouched in both files.
- **Scope boundary respected:** `docker-compose.hostinger.yml` (plan 100-03's exclusive scope) confirmed untouched via `git status --short`/`git diff --stat` (empty output).

## Deviations from Plan

None - plan executed exactly as written. One verification-tooling nuance was investigated and resolved without any code change (documented below, not a deviation from the plan's actual deliverable).

### Notes (not deviations — verification-only findings)

**1. Docker Compose CLI depends_on rendering depth**
- **Found during:** Task 2's acceptance-criteria verification (`docker compose -f docker-compose.yml config | grep -A6 'caddy:' | grep -c webpage` initially returned `0`).
- **Investigation:** This environment's Docker Compose CLI renders each `depends_on` entry as a 3-line block (`service:` / `condition: service_started` / `required: true`) rather than the older 1-line-per-entry list format the plan's literal `-A6` grep depth assumed. `webpage` was the 3rd of 3 dependencies, landing outside the 6-line window.
- **Resolution:** Re-ran with `-A12` (sufficient depth for 3 dependencies × 3 lines each + the `depends_on:` header) — confirmed `webpage` present, count `1`. Also confirmed directly via source read (`docker-compose.yml` lines 116-119) and via the full rendered `caddy:` block, which explicitly lists `webpage: condition: service_started, required: true`.
- **No files modified, no fix needed** — the underlying configuration was correct from the first edit; only the grep depth in the ad-hoc verification command needed adjusting for this Compose CLI version's output format.

**Total deviations:** 0 auto-fixed. 0 blocking issues. 1 verification-tooling nuance investigated and resolved (no code change).
**Impact on plan:** None — both tasks match the plan's reference blocks exactly, all acceptance criteria confirmed passing.

## Issues Encountered

None. Both tasks executed without any bugs, missing functionality, or blocking issues requiring auto-fix.

## User Setup Required

None - no external service configuration required. This plan only edits static config files (Caddy + Compose) already present in the repository.

## Next Phase Readiness

- Two of the three Caddy config sources (LP-14) and two of the three compose service definitions (LP-15) are now complete and verified. The remaining source — `docker-compose.hostinger.yml`'s inline heredoc entrypoint, the `$`-interpolation footgun deliberately isolated per `100-CONTEXT.md` — is owned by the concurrently-running sibling plan 100-03 in a separate worktree; this plan touched none of its files.
- The DEV stack (`docker-compose.yml` + `Caddyfile`) is now structurally complete: `webpage` builds from `./webpage` (Dockerfile confirmed buildable per 100-01's Orchestrator Resolution), Caddy routes `/` and `/landing-static/*` to it, and `caddy` won't start before `webpage` is up. This is ready for plan 100-04's live `docker compose up` full-stack verification.
- No blockers for 100-03 or 100-04. LP-14/LP-15 remain multi-plan requirements (100-03 completes the third Caddy source and third compose file); this plan's portion is fully done and verified.

## Self-Check: PASSED

All modified files verified present on disk and containing the expected content:
- FOUND: Caddyfile (contains `@webpage`/`handle @webpage`/`reverse_proxy webpage:3000`)
- FOUND: Caddyfile.prod (contains `@webpage`/`handle @webpage`/`reverse_proxy webpage:3000`)
- FOUND: docker-compose.yml (contains `webpage:` service + `caddy` depends_on webpage)
- FOUND: docker-compose.prod.yml (contains `webpage:` override with GHCR image + limits)

All commits verified present in git log:
- FOUND: 99f08a4 (feat: @webpage routing block, Caddyfile + Caddyfile.prod)
- FOUND: 92893d7 (feat: webpage service, docker-compose.yml + docker-compose.prod.yml)

No file deletions detected in either commit (`git diff --diff-filter=D` empty for both).
No untracked files left behind after either commit.
`docker-compose.hostinger.yml` confirmed untouched (sibling plan 100-03's exclusive scope).

---
*Phase: 100-infraestrutura-routing-e-deployment*
*Completed: 2026-07-15*
