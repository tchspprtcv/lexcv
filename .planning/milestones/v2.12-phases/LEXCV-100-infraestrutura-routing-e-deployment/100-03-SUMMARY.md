---
phase: 100-infraestrutura-routing-e-deployment
plan: 03
subsystem: infra
tags: [docker-compose, caddy, reverse-proxy, hostinger, deployment, caddyfile]

# Dependency graph
requires:
  - phase: 100-infraestrutura-routing-e-deployment (plan 01)
    provides: "webpage/Dockerfile + GHCR CI publish step — the image this plan's compose service references (ghcr.io/tchspprtcv/lexcv/webpage:latest)"
provides:
  - "docker-compose.hostinger.yml webpage service (internal-only, GHCR image, mirrors frontend block)"
  - "docker-compose.hostinger.yml caddy entrypoint heredoc @webpage routing block (/ and /landing-static/* -> webpage:3000), zero new $ characters"
  - "docker-compose.hostinger.yml caddy depends_on now includes webpage"
affects: [100-04-full-stack-verification]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "webpage compose service is a byte-for-byte structural mirror of the existing frontend block (GHCR image, no ports:, depends_on backend, BACKEND_API_ORIGIN/NEXT_PUBLIC_API_BASE_PATH env, lexcv_net, 0.5cpu/256M limits, restart unless-stopped)"
    - "Caddy @webpage matcher inserted between handle /api/* and the catch-all handle {} inside the hostinger heredoc, using the same narrow two-path matcher (path / /landing-static/*) as the plain Caddyfile/Caddyfile.prod sources — never a /* glob"
    - "Zero-$ heredoc discipline: every character added inside docker-compose.hostinger.yml's caddy entrypoint heredoc is a hardcoded literal, continuing the pattern established by commits 67e2120/534fa92 (this is the 3rd time the same heredoc has been touched, first time deliberately guarded by an automated scoped gate)"

key-files:
  created: []
  modified:
    - docker-compose.hostinger.yml

key-decisions:
  - "Transcribed ARCHITECTURE.md's exact TARGET reference blocks verbatim for both the webpage service and the heredoc @webpage insertion — no re-derivation, to minimize risk in the file most responsible for this session's earlier production incidents"
  - "Task 2's real caddy validate check (not just docker compose config's structural render) was run against the extracted heredoc body in an ephemeral caddy:2-alpine container via stdin (--config -), matching the plan's mandated same-rigor-as-100-02 acceptance gate"

requirements-completed: [LP-14, LP-15]

# Metrics
duration: ~7min
completed: 2026-07-15
---

# Phase 100 Plan 03: Infraestrutura — Routing e Deployment (Hostinger compose + Caddy heredoc) Summary

**Wired the `webpage` container into `docker-compose.hostinger.yml` — the real Hostinger production path — adding an internal-only compose service and an `@webpage` Caddy routing block inside the inline entrypoint heredoc using zero new `$` characters, verified by a scoped no-`$` gate, a full `docker compose config` render, and a real `caddy validate` pass inside a container.**

## Performance

- **Duration:** ~7 min
- **Started:** 2026-07-15T14:17:09Z (approx., base commit timestamp)
- **Completed:** 2026-07-15T14:23:55Z
- **Tasks:** 2 completed (both `type="auto"`)
- **Files modified:** 1 (`docker-compose.hostinger.yml`)

## Accomplishments
- Added the internal-only `webpage` service to `docker-compose.hostinger.yml`, mirroring `frontend`'s exact shape (GHCR image `ghcr.io/tchspprtcv/lexcv/webpage:latest`, `container_name: lexcv_webpage`, `depends_on: backend`, `BACKEND_API_ORIGIN`/`NEXT_PUBLIC_API_BASE_PATH` env, `lexcv_net`, `0.5` cpu / `256M` memory limits, `restart: unless-stopped`, **no `ports:` key** — internal-only, since Hostinger publishes only caddy's 80/443).
- Inserted the `@webpage { path / /landing-static/* }` matcher and `handle @webpage { reverse_proxy webpage:3000 }` block inside the caddy service's inline `entrypoint` heredoc, positioned between the existing `handle /api/*` block and the catch-all `handle {}`, using exclusively hardcoded literals.
- Added `webpage` to caddy's `depends_on` (now `[frontend, backend, webpage]`).
- All verification was local only — no SSH/connection to the live Hostinger VPS was made at any point.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add the internal-only webpage service to docker-compose.hostinger.yml** - `0ebf003` (feat)
2. **Task 2: Insert @webpage into the caddy entrypoint heredoc (ZERO new $) + caddy depends_on webpage** - `4275025` (feat)

**Plan metadata:** SUMMARY commit follows this file (see below).

## Files Created/Modified
- `docker-compose.hostinger.yml` - added `webpage` service block; inserted `@webpage` routing into the caddy entrypoint heredoc; added `webpage` to caddy's `depends_on`

## Decisions Made
- Followed the plan's ARCHITECTURE.md reference blocks verbatim for both the service definition and the heredoc text — no re-derivation, matching the plan's explicit "transcribe, don't re-derive" instruction for this specific footgun-prone file.
- Ran the mandatory real `caddy validate` check (not merely `docker compose config`'s structural render) against the extracted heredoc body, per the plan's requirement that this file receive the same validation rigor as 100-02's `Caddyfile`/`Caddyfile.prod`.

## Deviations from Plan

None - plan executed exactly as written. Both tasks matched the ARCHITECTURE.md TARGET reference blocks byte-for-byte; no auto-fixes, no blocking issues, no architectural questions arose.

## Issues Encountered

None.

## Verification Evidence

**Task 1:**
- `docker compose -f docker-compose.hostinger.yml config` — exit 0 (warnings only, for undefined env vars like `POSTGRES_DB`, expected/pre-existing and unrelated to this plan).
- `docker compose -f docker-compose.hostinger.yml config | grep -c 'tchspprtcv/lexcv/webpage'` → `1`.
- Rendered `webpage` service confirmed to have no `ports:` key (internal-only).
- Rendered frontend/backend/postgres/minio/caddy blocks confirmed unchanged by this task (caddy's `depends_on` at this point still `[frontend, backend]`, correctly deferred to Task 2).

**Task 2 (the load-bearing footgun gate, T-100-02):**
- Scoped no-`$` gate: `awk "/echo 'alcv.tech/,/> \/etc\/caddy\/Caddyfile/" docker-compose.hostinger.yml | grep -v '^[[:space:]]*#' | grep -c '\$'` → `0`.
- Interpolation-intact proof: `docker compose -f docker-compose.hostinger.yml config | grep -c 'reverse_proxy webpage:3000'` → `1`; the same render's caddy `entrypoint` shows the `@webpage`/`handle @webpage`/catch-all block textually intact, proving Compose did not mangle anything inside the heredoc.
- Real `caddy validate`: extracted the echoed Caddyfile body (via the same `awk` range + `sed` strip of the `echo '`/`' > /etc/caddy/Caddyfile ...` shell wrapper) and piped it through `MSYS_NO_PATHCONV=1 docker run --rm -i caddy:2-alpine caddy validate --config - --adapter caddyfile` → printed `Valid configuration`, exit code `0`.
- Narrow-matcher guard (T-100-01): `awk ... | grep -Ec 'path\s+/\*|path\s+\*'` → `0` (matcher is `path / /landing-static/*`, never a bare glob).
- caddy `depends_on` in the rendered config now lists `backend`, `frontend`, and `webpage` (all `condition: service_started`).
- No live deployment: every check above ran against the local working tree and an ephemeral `caddy:2-alpine` container invoked only for `caddy validate`; no SSH or `docker compose pull/up` was issued against the Hostinger VPS.

## User Setup Required

None - no external service configuration required. This plan's scope was explicitly local-verification-only; the actual Hostinger VPS deploy remains out of scope and requires separate user authorization (per 100-CONTEXT.md).

## Next Phase Readiness

- `docker-compose.hostinger.yml` now has all 3 required pieces for `webpage`: the internal-only service, the Caddy routing (verified with a real `caddy validate` pass), and the `depends_on` wiring — matching the shape already delivered for the dev/prod compose+Caddy sources by the parallel 100-02 plan.
- LP-14 (3rd Caddy config source) and LP-15 (3rd compose file service) are both satisfied by this plan's portion. LP-15 was also partially claimed by 100-01 (Dockerfile) and 100-02 (dev/prod compose services) — this plan completes the Hostinger compose file's share of it.
- No blockers for 100-04 (full-stack `docker compose up` verification) from this plan's side — `docker-compose.hostinger.yml` is internally consistent and parses cleanly. Note: 100-04's own success depends on the `webpage` GHCR image actually existing/being buildable, which 100-01's SUMMARY flagged as resolved by the orchestrator (commit `6702a42`, `docker build --no-cache -t lexcv-webpage:verify ./webpage` confirmed successful) — not a concern introduced by this plan.
- This plan did not touch `Caddyfile`, `Caddyfile.prod`, `docker-compose.yml`, or `docker-compose.prod.yml` (100-02's exclusive scope) — confirmed zero file overlap with the sibling parallel plan.

## Self-Check: PASSED

All modified files verified present on disk:
- FOUND: docker-compose.hostinger.yml (contains `webpage` service, `@webpage` heredoc block, `reverse_proxy webpage:3000`, and caddy `depends_on: [frontend, backend, webpage]`)

All commits verified present in git log:
- FOUND: 0ebf003 (feat: add internal-only webpage service to docker-compose.hostinger.yml)
- FOUND: 4275025 (feat: route @webpage inside caddy hostinger heredoc, zero new $ chars)

No file deletions detected in either commit (`git diff --diff-filter=D`).

No new `$` characters were introduced anywhere in the caddy entrypoint heredoc (scoped gate = 0), and the working tree is clean (no untracked files) after both commits.

---
*Phase: 100-infraestrutura-routing-e-deployment*
*Completed: 2026-07-15*
