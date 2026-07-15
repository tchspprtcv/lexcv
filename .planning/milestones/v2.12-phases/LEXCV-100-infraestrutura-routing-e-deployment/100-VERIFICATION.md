---
phase: 100-infraestrutura-routing-e-deployment
verified: 2026-07-15T16:20:00Z
status: passed
score: 5/5 roadmap success criteria verified (15/15 plan-level truths independently confirmed)
overrides_applied: 0
---

# Phase 100: Infraestrutura — Routing e Deployment Verification Report

**Phase Goal:** O container `webpage` está corretamente encaminhado em todos os ambientes (dev, prod, Hostinger), servido em `/`, enquanto todas as rotas existentes continuam a chegar a `web/`/`backend` inalteradas — verificado com um `docker compose up` completo, não apenas checks isolados de `pnpm dev`/`mvn test`.
**Verified:** 2026-07-15T16:20:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Verification Method (important context)

This phase's own defining mandate is live verification, not static inspection. The dev stack (`postgres`, `minio`, `backend`, `frontend`, `webpage`, `caddy`) happened to be running live at verification time. Rather than trust 100-04-SUMMARY.md's narrative of a prior live test, **every routing-matrix, asset-non-collision, and no-hairpin claim was independently re-executed against the live containers during this verification pass** (fresh `curl`/`docker exec` calls, not a re-read of the SUMMARY's tables). Results matched the SUMMARY's claims exactly. Static checks (`caddy validate`, `docker compose config`, `grep`/`awk` gates, `docker image inspect`) were also independently re-run rather than trusted from the SUMMARYs/REVIEW.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — authoritative contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `docker compose up` (dev) starts `webpage` alongside `postgres`/`backend`/`frontend` from its own multi-stage Dockerfile (`output: standalone`); `/` shows the landing via Caddy while `/login`, `/dashboard`, `/setup`, `/api/*` resolve to `web`/`backend` unchanged | ✓ VERIFIED | Live `docker compose ps` shows all 6 services running. Independently curled: `/` → 200, `id="funcionalidades"`=1, `/landing-static/`=1 (webpage landing, not frontend). `/login` → 200, `/landing-static/`=0. `/dashboard` → 200, `/landing-static/`=0. `/setup` → 200, `/landing-static/`=0. `/api/v1/setup/status` → 200 `{"initialized":true}`. All results reproduced fresh, not read from SUMMARY. |
| 2 | The 3 Caddy config sources (`Caddyfile`, `Caddyfile.prod`, `docker-compose.hostinger.yml` heredoc) are updated consistently with equivalent `handle` blocks for `/` + the webpage `assetPrefix`, ordered before the catch-all | ✓ VERIFIED | Direct file reads confirm identical `@webpage { path / /landing-static/* } handle @webpage { reverse_proxy webpage:3000 }` block in all 3 sources, positioned after `/api/*` (and after `/minio-console*` in `Caddyfile.prod`) and before the catch-all. Independently re-ran `caddy validate` against all 3 (real container, not `docker compose config`'s syntactic check alone) — all three print `Valid configuration`. Hostinger heredoc's zero-`$` gate re-run: `0`. Narrow-matcher gate (`path /\*` or bare `path \*`) re-run: `0` in all 3 files. |
| 3 | `webpage`'s `_next/static/*` chunks (under `assetPrefix`) and `web`'s never collide when requested through the same Caddy origin — verified live | ✓ VERIFIED | Extracted a real chunk URL from the live `/` HTML (`/landing-static/_next/static/chunks/0wez8zg~spqpi.js`) → curled through Caddy → HTTP 200. Extracted a real chunk URL from the live `/login` HTML (`/_next/static/chunks/03q757hpt3301.js`) → curled through Caddy → HTTP 200. Namespaces are structurally disjoint (`/landing-static/_next/` vs `/_next/`). Reproduced fresh against the live stack, not read from SUMMARY. |
| 4 | `.github/workflows/deploy.yml` builds and publishes a 3rd artifact (`webpage`) alongside the existing `backend`/`web` `docker/build-push-action@v6` steps, same tagging/registry convention | ✓ VERIFIED | Direct file read confirms a 3rd step "Build and push webpage image", `context: ./webpage`, tags `${{ env.REGISTRY }}/webpage:latest` + `:${{ env.IMAGE_TAG }}`, `cache scope=webpage`. `grep -c 'docker/build-push-action@v6'` = 3 (backend+frontend+webpage), no other action version present. Repo-wide grep confirms zero stale `ghcr.io/lexcv` registry references remain (WR-02 fix verified complete). Note: this is verified statically per the plan's own acceptance bar (yq/grep) — an actual push-triggered CI run to observe the real GHCR publish was not executed (would require pushing to `master`, a side-effecting action outside verification scope); the pattern is identical to the already-working backend/frontend steps, and the underlying image is confirmed to build successfully (see Artifact table). |
| 5 | A full `docker compose up` smoke test confirms `webpage`'s server-side setup-status fetch resolves against the internal Docker network, not a hairpin through the public domain — documented pass/fail | ✓ VERIFIED | Independently re-ran all 3 converging proofs against the live container: (a) `docker exec lexcv_webpage env \| grep BACKEND_API_ORIGIN` → `http://backend:8080`. (b) `docker exec lexcv_webpage sh -c "wget -qO- http://backend:8080/..."` → real JSON. (c) `curl http://localhost/api/v1/public/branding` → 200 with a real, non-fallback `logoDataUrl` (base64 PNG data), proving the internal fetch actually succeeds end-to-end right now — this also directly re-confirms the `PublicController.getBranding()` `@Transactional` bug fix (commit `942152e`) is genuinely effective in the live system, not just claimed. `docker logs lexcv_webpage \| grep -iE 'ECONNREFUSED\|ENOTFOUND\|fetch failed'` → 0 matches. |

**Score:** 5/5 roadmap success criteria verified. All 15 plan-level `must_haves.truths` across the 4 plans (100-01 through 100-04) map onto and are subsumed by these 5, and were independently confirmed via the evidence above plus the Artifacts/Key Links tables below.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `webpage/Dockerfile` | 3-stage (deps→builder→runner) standalone build mirroring `web/Dockerfile`, 2 deliberate omissions | ✓ VERIFIED | Read directly: matches reference exactly. No `public` COPY line (`grep -c public` = 0), no `BACKEND_API_ORIGIN` build-arg. Not just "exists" — the actual running `lexcv-webpage:latest` image built from this file was inspected: `ExposedPorts=3000/tcp`, `User=appuser`, `Entrypoint=["node","server.js"]` — proves the Dockerfile genuinely produces the specified artifact, not merely that the text looks right. |
| `webpage/.dockerignore` | byte-for-byte copy of `web/.dockerignore` | ✓ VERIFIED | Read directly, content matches (`node_modules/`, `.next/`, `.env`, `.env.*`, `.env.local`, `*.md`, `.git/`, `.gitignore`). |
| `.github/workflows/deploy.yml` | 3rd `docker/build-push-action@v6` step for `webpage` | ✓ VERIFIED | Read directly; step present, correctly configured, git commits `efabdf3`/`1facae6`/`6702a42` confirmed in `git log`. |
| `Caddyfile` (dev) | `@webpage` handle block, `/` + `/landing-static/*` → `webpage:3000` | ✓ VERIFIED | Read directly + `caddy validate` (real container) passed + live-routed (Truth #1/#3). |
| `Caddyfile.prod` | same `@webpage` block, real mounted file | ✓ VERIFIED | Read directly + `caddy validate` (dummy env vars) passed. |
| `docker-compose.yml` | `webpage` service (build ./webpage, port 3004:3000) + caddy `depends_on` | ✓ VERIFIED | Read directly; live container `lexcv_webpage` running from this exact config right now. |
| `docker-compose.prod.yml` | `webpage` override (GHCR image + 0.5cpu/256M), caddy env passthrough | ✓ VERIFIED (webpage-routing portion); ⚠️ see Anti-Patterns for a residual, out-of-scope MinIO-console finding | Rendered via `docker compose -f docker-compose.yml -f docker-compose.prod.yml config`: `webpage` present, image `ghcr.io/tchspprtcv/lexcv/webpage:...` (registry fix WR-02 confirmed), caddy `depends_on` correctly lists `backend`/`frontend`/`webpage`. `DOMAIN_NAME=alcv.tech` passes through env intact (CR-01 fix works for this variable). |
| `docker-compose.hostinger.yml` | internal-only `webpage` service (no ports) + heredoc `@webpage` (zero `$`) + `depends_on` | ✓ VERIFIED | Rendered via `docker compose config`: `webpage` present, GHCR image, no `ports:` key. Heredoc zero-`$` gate = 0, narrow-matcher gate = 0, real `caddy validate` on extracted heredoc body = `Valid configuration`. `reverse_proxy webpage:3000` count = 1. |
| `backend/.../PublicController.java` | `@Transactional(readOnly = true)` fix for `@Lob` read (found live during 100-04) | ✓ VERIFIED | Read directly; annotation present with explanatory comment. Live-re-confirmed: `GET /api/v1/public/branding` returns 200 with real `logoDataUrl` data right now (would 500 without the fix, per the documented root cause). Commit `942152e` confirmed in git log. |
| `100-04-SUMMARY.md` | documented pass/fail for routing matrix, asset non-collision, no-hairpin | ✓ VERIFIED | Exists, all 3 tables present; every claim independently reproduced live during this verification pass (see Truths table). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `.github/workflows/deploy.yml` (webpage step) | `ghcr.io/tchspprtcv/lexcv/webpage` | `docker/build-push-action@v6` + REGISTRY/webpage tags | ✓ WIRED | `grep -c 'REGISTRY }}/webpage'` = 2 (latest + sha tags). |
| `webpage/Dockerfile` builder stage | `webpage/.next/standalone` (`server.js`) | `next build` with `output: standalone` | ✓ WIRED | Confirmed structurally AND behaviorally — running image's entrypoint (`node server.js`) actually serves live traffic right now. |
| `Caddyfile` `@webpage` matcher | `webpage:3000` | `handle @webpage { reverse_proxy webpage:3000 }` | ✓ WIRED | Live-routed; confirmed via curl. |
| `docker-compose.yml` webpage service | `webpage/Dockerfile` | `build.context ./webpage` | ✓ WIRED | Live container running from this build context. |
| `docker-compose.hostinger.yml` caddy heredoc `@webpage` | `webpage:3000` | `handle @webpage { reverse_proxy webpage:3000 }` (literal, zero `$`) | ✓ WIRED | `reverse_proxy webpage:3000` present in rendered config; heredoc zero-`$` gate = 0. |
| `docker-compose.hostinger.yml` webpage service | `ghcr.io/tchspprtcv/lexcv/webpage:latest` | image reference | ✓ WIRED | Confirmed in rendered config. |
| browser → Caddy (path `/`) | `webpage:3000` | `@webpage` handle block, live curl | ✓ WIRED | Independently curled — landing page + `/landing-static/` assets served. |
| `webpage` container → `backend:8080` | internal `lexcv_net` | server-side fetch (`BACKEND_API_ORIGIN=http://backend:8080`) | ✓ WIRED | `docker exec` env check + in-container `wget` + real branding data flowing through — all independently reproduced. |

### Data-Flow Trace (Level 4)

The one dynamic-data path this phase's live-verification mandate covers is `webpage`'s server-side branding fetch (`webpage/src/lib/branding.ts` → `backend:8080` → `PublicController.getBranding()` → Postgres `Tenant.logoDataUrl`).

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `webpage` `/` page render | rendered `logoDataUrl` in HTML | `fetchBranding()` → `GET /api/v1/public/branding` → `TenantRepository.findFirstByOrderByCreatedAtAsc()` | Yes — independently confirmed: direct `curl` of `/api/v1/public/branding` returned a real `data:image/png;base64,...` payload (not the hardcoded `{nome:"LexCV", logoDataUrl:null}` fail-open fallback) | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| `/` routes to webpage | `curl http://localhost/` + grep discriminators | 200, `id="funcionalidades"`=1, `/landing-static/`=1 | ✓ PASS |
| `/login` routes to web | `curl http://localhost/login` + grep | 200, `/landing-static/`=0 | ✓ PASS |
| `/dashboard`, `/setup` route to web | `curl -L` + grep | both 200, `/landing-static/`=0 | ✓ PASS |
| `/api/v1/setup/status` routes to backend | `curl http://localhost/api/v1/setup/status` | 200 `{"initialized":true}` | ✓ PASS |
| webpage chunk resolves (no collision) | `curl` extracted `/landing-static/_next/...` URL | 200 | ✓ PASS |
| web chunk resolves (no collision) | `curl` extracted `/_next/...` URL | 200 | ✓ PASS |
| No-hairpin: internal env | `docker exec lexcv_webpage env \| grep BACKEND_API_ORIGIN` | `http://backend:8080` | ✓ PASS |
| No-hairpin: in-container connectivity | `docker exec lexcv_webpage wget -qO- http://backend:8080/...` | real JSON | ✓ PASS |
| No-hairpin: real branding data flowed | `curl http://localhost/api/v1/public/branding` | 200, real base64 logo (not fallback) | ✓ PASS |
| webpage image artifact shape | `docker image inspect lexcv-webpage:latest` | `3000/tcp`, `appuser`, `["node","server.js"]` | ✓ PASS |
| Caddy config validity ×3 sources | `caddy validate` (real container, ×3) | `Valid configuration` ×3 | ✓ PASS |
| Hostinger heredoc zero-`$` gate | `awk`+`grep -c '\$'` | `0` | ✓ PASS |
| CI registry consistency | `grep -c 'docker/build-push-action@v6'` / stale-registry grep | `3` / zero stale hits | ✓ PASS |

### Probe Execution

No conventional (`scripts/*/tests/probe-*.sh`) or PLAN/SUMMARY-declared probes exist for this phase. Step 7c: SKIPPED (no probes declared or discovered).

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| LP-13 | 100-04 | `webpage` uses Next.js Multi-Zones (`assetPrefix`) so its `_next/static/*` never collides with `web/`'s | ✓ SATISFIED | `webpage/next.config.ts` sets `assetPrefix: "/landing-static"`; live non-collision test passed (both chunk namespaces 200, structurally disjoint). |
| LP-14 | 100-02, 100-03 | Caddy routes `/` + webpage's `assetPrefix` to `webpage`; all other routes unchanged; consistent across all 3 Caddy sources | ✓ SATISFIED | All 3 sources (`Caddyfile`, `Caddyfile.prod`, hostinger heredoc) confirmed identical narrow matcher, validated, live-routed for the dev source. |
| LP-15 | 100-01, 100-02, 100-03 | `webpage` service exists in all 3 compose files with its own multi-stage `output: standalone` Dockerfile | ✓ SATISFIED | All 3 compose files confirmed with correct `webpage` service shape; Dockerfile confirmed building and running live. |
| LP-16 | 100-01 | CI builds/publishes `webpage` as 3rd GHCR artifact, same convention as `backend`/`web` | ✓ SATISFIED (static verification; live CI run not executed — see Truth #4 note) | 3rd `docker/build-push-action@v6` step confirmed structurally identical to the two working steps; local `docker build` of the identical Dockerfile confirmed producing a working image. |

No orphaned requirements: REQUIREMENTS.md's traceability table maps exactly LP-13/14/15/16 to Phase 100, matching the union of all 4 plans' frontmatter `requirements:` fields.

**Documentation-currency note (INFO, non-blocking):** `.planning/REQUIREMENTS.md`'s checklist/traceability table still marks LP-14, LP-15, LP-16 as `[ ]`/"Pending" (only LP-13 is checked/"Complete"), and `.planning/STATE.md`'s "Current Position" still reads "Phase: 100 ... EXECUTING, Plan: 1 of 4" with the hairpin-risk concern not yet marked resolved. Both are functionally complete per the evidence above — this is a bookkeeping lag in the planning docs, not a gap in the actual implementation. Recommend updating both files as part of this phase's closure.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none of the 10 phase-100-modified files) | — | `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER` | — | Zero matches across `webpage/Dockerfile`, `.dockerignore`, `pnpm-workspace.yaml`, `deploy.yml`, `Caddyfile`, `Caddyfile.prod`, `docker-compose{,.prod,.hostinger}.yml`, `PublicController.java` — debt-marker gate is clean. |
| `docker-compose.prod.yml` | 43-45 (caddy `environment:`) | **New finding, beyond 100-REVIEW.md's CR-01 verification:** `CADDY_MINIO_PASSWORD_HASH` silently truncated by Docker Compose's own `.env`-file interpolation | ⚠️ WARNING (out of Phase 100's LP-13–16 scope) | See detail below. Does not affect `webpage` routing (this phase's actual mandate) — `DOMAIN_NAME` (the variable that matters for the prod-overlay's TLS/domain matching) propagates correctly (confirmed intact, no `$`, in the rendered config). Only the MinIO-console bcrypt hash is affected. |
| `docker-compose.hostinger.yml` | 20-41, 113-147 | WR-01 (carried forward from 100-REVIEW.md, already investigated and deliberately deferred with hard evidence in 100-REVIEW-FIX.md) — hostinger heredoc has no MinIO console route | ⚠️ WARNING (already documented, deliberate deferral) | Not re-litigated here; the existing deferral rationale (bcrypt hash format structurally incompatible with the heredoc's zero-`$` constraint) is sound and already recorded. |
| `docker-compose.yml`, `docker-compose.hostinger.yml` | webpage service env | IN-01 (carried forward): `NEXT_PUBLIC_API_BASE_PATH` set for `webpage` is dead config (never read; no build-arg wires it) | ℹ️ INFO | Already documented in 100-REVIEW.md; not a functional defect (routing/goal unaffected). |
| `webpage/Dockerfile` | 20-21 | IN-02 (carried forward): `COPY` before `USER appuser` switch lacks `--chown` | ℹ️ INFO | Already documented; works today because Next's standalone output is world-readable. |
| `webpage/.dockerignore` | — | IN-03 (carried forward): doesn't exclude `tsconfig.tsbuildinfo`; redundant `.env.local` line | ℹ️ INFO | Already documented; CI unaffected (fresh checkout). |
| `docker-compose.hostinger.yml` vs `docker-compose.yml` | ports | IN-04 (carried forward): HTTP/3 UDP 443 asymmetry between hostinger and prod-overlay targets | ℹ️ INFO | Already documented; likely harmless. |
| `.github/workflows/deploy.yml` | `test` job | IN-05 (carried forward): no frontend lint/typecheck gate in CI | ℹ️ INFO | Already documented. |
| `webpage/Dockerfile` | 4, 11 | IN-06 (carried forward): `pnpm@latest` unpinned via corepack | ℹ️ INFO | Already documented; mirrors pre-existing `web/Dockerfile` pattern. |

#### New finding detail: CR-01's fix is only partially effective (discovered during this verification pass)

100-REVIEW.md states CR-01 ("caddy service missing `DOMAIN_NAME`/`CADDY_MINIO_*` env passthrough" in `docker-compose.prod.yml`) is "confirmed fixed," verified via `git show` (correct diff) and variable-name matching against `Caddyfile.prod`/`.env.example`. That verification method did not test whether the *actual value* of a bcrypt-hash-shaped secret survives Docker Compose's own `.env`-file interpolation.

Independent reproduction (isolated scratchpad test, using the well-known **public, non-sensitive** example bcrypt hash `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy` — no real secret was read or exposed) confirms: Docker Compose's `.env`-file loader performs its own `$VAR` interpolation pass over values read from `.env`, independent of the compose-YAML-level `${VAR}` substitution. Because bcrypt hashes are internally delimited by `$` (`$2a$10$<53-char-salt+hash>`), Compose misinterprets the portion after the second `$` as an undefined-variable reference and silently truncates it — reproduced with a **real running container**: `REAL_VALUE=[$2a$10]` instead of the full hash, accompanied by the exact `"...variable is not set. Defaulting to a blank string."` warning also observed when rendering the real `docker-compose.prod.yml` + `.env` (with the real, untouched secret — I did not read or print that value, per the credential-materialization guard that correctly blocked a direct inspection attempt).

**Consequence:** if `docker-compose.prod.yml` + `Caddyfile.prod` is ever actually used as a deployment target with a real bcrypt `CADDY_MINIO_PASSWORD_HASH` in `.env`, Caddy's `basicauth` directive for `/minio-console*` would receive a truncated/malformed hash — the exact same `$`-interpolation footgun class already documented for the hostinger heredoc (WR-01, T-100-02), just surfacing via `.env`-loading instead of heredoc text, and in a different file.

**Why this is not a Phase 100 blocker:** LP-13 through LP-16 (this phase's entire mandate) concern only the `webpage` container's routing/deployment. `DOMAIN_NAME` — the variable that actually matters for the prod-overlay's own TLS/domain matching — has no `$` and passes through CR-01's fix correctly (confirmed intact in the rendered config). The MinIO-console credential is inherited from Phase 52, orthogonal to `webpage`, and (per `.planning/STATE.md`/`100-CONTEXT.md`) `docker-compose.hostinger.yml` — not `docker-compose.prod.yml` — is the actual live Hostinger production path; `docker-compose.prod.yml`'s exact deployment role appears to be a secondary/generic prod-overlay pattern from the original v1.8 milestone design, not the current real target.

**Recommendation:** track this alongside WR-01 as a single follow-up decision (both are "$-in-bcrypt-hash vs. Compose interpolation" issues affecting MinIO-console auth across the two production-ish compose files) rather than opening two separate tickets — the same structural fix (e.g., generating/reading the basicauth credential at container boot instead of threading it through Compose interpolation) would likely resolve both.

```yaml
# Suggested override, if a human reviewer agrees this is out of Phase 100's scope and wants
# it formally acknowledged rather than left as an open verifier note:
overrides:
  - must_have: "docker-compose.prod.yml CR-01 caddy env passthrough (CADDY_MINIO_PASSWORD_HASH)"
    reason: "Pre-existing Phase 52 MinIO-console-auth issue, orthogonal to Phase 100's LP-13-16 webpage-routing mandate; docker-compose.hostinger.yml (not .prod.yml) is the real Hostinger path; same root cause as already-deferred WR-01"
    accepted_by: "{pending human decision}"
    accepted_at: "{pending}"
```

### Human Verification Required

None required to reach a `passed` verdict — every truth in this phase was independently confirmed either through live reproduction against the running stack or through static checks that matched the plan's own specified acceptance bar.

One optional, non-blocking follow-up worth a human's attention before any real Hostinger/prod-overlay deploy:

#### 1. CR-01 residual `$`-truncation for `CADDY_MINIO_PASSWORD_HASH` (docker-compose.prod.yml)

**Test:** Deploy (or dry-run) `docker compose -f docker-compose.yml -f docker-compose.prod.yml up` against a real `.env` containing the actual bcrypt hash, and check whether Caddy's MinIO-console `basicauth` accepts the real credential.
**Expected:** Either confirm the hash arrives intact (if this environment's Compose version handles it differently than reproduced here), or apply the structural fix recommended above (read the credential outside Compose's interpolation path).
**Why human:** Requires a real secret value and a real deployment dry-run against the specific target environment (Hostinger's Compose version) to confirm definitively — this verification pass confirmed the bug class with a public dummy hash by design, to avoid touching the real secret.

## Gaps Summary

No gaps. All 5 ROADMAP success criteria and all 4 requirements (LP-13, LP-14, LP-15, LP-16) are independently, directly verified — the majority via live reproduction against the actually-running dev `docker compose` stack (not merely re-reading 100-04-SUMMARY.md's tables), plus static/structural checks (`caddy validate`, `docker compose config`, zero-`$` gates, `docker image inspect`) re-run fresh rather than trusted from the SUMMARYs. The phase's core mandate — proving the `webpage` container is correctly routed across dev/prod/Hostinger config sources and that this holds under a real `docker compose up`, not just isolated `pnpm dev`/`mvn test` — is met.

One finding beyond the existing code review (100-REVIEW.md/100-REVIEW-FIX.md) was discovered during this pass: CR-01's fix to `docker-compose.prod.yml` is only partially effective (the `DOMAIN_NAME` portion works; the `CADDY_MINIO_PASSWORD_HASH` portion would still be corrupted by Compose's own `.env` interpolation with a real bcrypt hash). This is scoped to MinIO-console auth in a compose file that is not the actual live Hostinger production path, and does not touch LP-13 through LP-16 — it is surfaced as a WARNING/human-follow-up item, not a Phase 100 blocker.

---

*Verified: 2026-07-15T16:20:00Z*
*Verifier: Claude (gsd-verifier)*
