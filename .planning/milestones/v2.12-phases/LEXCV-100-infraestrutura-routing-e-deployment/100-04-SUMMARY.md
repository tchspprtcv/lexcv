---
phase: 100-infraestrutura-routing-e-deployment
plan: 04
subsystem: infra
tags: [docker-compose, caddy, live-verification, next-multi-zones, hibernate, postgresql-lob, spring-transactional]

# Dependency graph
requires:
  - phase: 100-01
    provides: "webpage/Dockerfile — buildable image (confirmed building successfully per 100-01's Orchestrator Resolution)"
  - phase: 100-02
    provides: "Caddyfile (dev) @webpage routing block + docker-compose.yml webpage service — the exact two files this plan brings up live"
provides:
  - "Live pass/fail evidence (not static inspection) for the routing matrix, LP-13 asset non-collision, and the no-hairpin smoke test (ROADMAP criteria #1, #3, #5) — the phase's defining live-verification mandate"
  - "Fixed a genuine runtime-only bug in Phase 98's PublicController.getBranding() (missing @Transactional around a Hibernate @Lob read), found only because this plan exercised it against a real tenant with a persisted logo"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Live docker compose verification is the only reliable proof for Caddy multi-zone routing and Docker-internal-network fetch behavior — neither the @Lob/auto-commit bug nor the routing matrix could have been caught by static config inspection or isolated pnpm dev/mvn test runs"
    - "When container_name is hardcoded in a compose file and a same-named container from the project's own prior session already exists, the compose project name (-p) must match the pre-existing project rather than defaulting to the worktree directory's basename, or container creation fails on a Docker Engine-level name conflict"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/PublicController.java

key-decisions:
  - "Ran all docker compose commands with an explicit -p lexcv (not the worktree directory's default-derived project name), because docker-compose.yml's container_name values (lexcv_postgres, lexcv_minio, etc.) are hardcoded and a container named lexcv_minio was already Up (healthy) under the user's existing lexcv-named project from a prior session — a mismatched project name would have hit a Docker Engine container-name conflict on every service, not just minio."
  - "Created a worktree-local root .env (gitignored, never committed) mirroring the main repo's actual root .env values verbatim, required for docker-compose.yml's ${POSTGRES_*}/${JWT_*}/${MINIO_*} substitution. Values had to match exactly (not the .env.example placeholders) because -p lexcv reuses the pre-existing lexcv_lexcv_pgdata volume, whose Postgres role password was only ever set at that volume's original initdb — a different POSTGRES_PASSWORD now would not rotate the stored role, it would just make backend's DB_PASSWORD wrong and fail auth."
  - "System was already initialized ({\"initialized\":true}) from the reused Postgres volume's prior session(s) — skipped the plan's literal POST /api/v1/setup/initialize (would correctly 403) and proceeded against the existing singleton tenant, exactly as the plan's own action text allows for this branch."
  - "Adapted Task 3 criterion (c)'s literal proof target. The plan's reference assumed a fresh initialize creating tenant name \"Escritorio Teste LexCV\"; the real persisted tenant is named \"LexCV\" — identical to branding.ts's hardcoded FALLBACK string, so a bare name-substring match cannot distinguish a real fetch from the fail-open fallback. Used the real tenant's persisted logoDataUrl (a data:image/png;base64,... value, present ONLY via a successful real fetch — the fallback is always logoDataUrl: null, and brand-mark.tsx never renders an <img> for a null logo) as a strictly stronger, non-coincidental discriminator instead. This preserves the check's exact intent (prove real data flowed through, not the hardcoded default) using real environment state rather than destroying/reinitializing the shared dev database to force a synthetic match."
  - "Scoped teardown to `stop` + `rm -f` on exactly the 5 services this run (re)started (postgres, backend, frontend, webpage, caddy), deliberately excluding minio — which was already Up 2 hours (healthy) before this run touched anything. A blanket `docker compose down` would have stopped/removed that pre-existing, not-owned container, and would also have attempted to remove lexcv_net while minio was still attached to it."
  - "[Rule 1 - Bug] Added @Transactional(readOnly = true) to PublicController.getBranding() (Phase 98 code, outside this plan's declared files_modified: []) because it directly blocked this plan's own Task 3 acceptance criterion (c) — GET /api/v1/public/branding 500'd with a Hibernate 'Unable to access lob stream' / PostgreSQL 'Large Objects may not be used in auto-commit mode' error whenever the tenant has a persisted logo. Rule 1 applies (broken behavior discovered while executing the task); the scope boundary exception is that this bug directly blocks completing the current task's own acceptance criteria, not an unrelated pre-existing issue."

requirements-completed: [LP-13]

# Metrics
duration: ~11 min (approx.)
completed: 2026-07-15
---

# Phase 100 Plan 04: Infraestrutura — Routing e Deployment (Live Docker Compose Verification) Summary

**Brought up the full dev stack with a real `docker compose up -d --build` and proved live — not via static config reading — the routing matrix, LP-13's asset non-collision, and criterion #5's no-hairpin internal fetch; the live run also caught and fixed a genuine runtime-only Hibernate/PostgreSQL bug in Phase 98's public branding endpoint that only manifests against a tenant with a real persisted logo.**

## Performance

- **Duration:** ~11 min (approx. — reconstructed from observed container/log timestamps; exact start not captured via `date` before the first action)
- **Started:** ~2026-07-15T15:36:00Z (approx.)
- **Completed:** 2026-07-15T15:46:53Z
- **Tasks:** 3 completed (all `type="auto"`)
- **Files modified:** 1 (backend fix, found live during Task 1/3)

## Accomplishments
- Full dev stack (`postgres`, `minio`, `backend`, `frontend`, `webpage`, `caddy`) brought up via a real `docker compose -f docker-compose.yml -p lexcv up -d --build` — all 6 services confirmed running/healthy.
- Live routing matrix proven: `/` → webpage landing (200, `/landing-static/` assets, `id="funcionalidades"` present), `/login` + `/dashboard` + `/setup` → web (200, zero `/landing-static/` references), `/api/v1/setup/status` → backend JSON. Confirms T-100-01 (the narrow `@webpage` matcher does not capture authenticated/internal paths) live, not just by config reading.
- LP-13 asset non-collision proven live: a real `/landing-static/_next/static/chunks/*.js` URL (webpage) and a real `/_next/static/chunks/*.js` URL (web) both resolve 200 through the same Caddy origin, in structurally distinct namespaces.
- Criterion #5 (no-hairpin) proven via 3 converging live proofs: `BACKEND_API_ORIGIN=http://backend:8080` inside the webpage container; an in-container `wget` to `backend:8080` gets a real JSON response; the `/` HTML rendered the tenant's real persisted logo (`data:image/png;base64,...`), which is only possible if the internal fetch succeeded (the fail-open fallback never has a logo). Zero ECONNREFUSED/ENOTFOUND/fetch-failed lines in webpage's logs.
- Found and fixed a genuine, previously-undetected bug: `PublicController.getBranding()` threw a 500 (`Hibernate: Unable to access lob stream` / Postgres: `Large Objects may not be used in auto-commit mode`) whenever the tenant has a real logo, because the read of the `@Lob` `logoDataUrl` field had no surrounding transaction. This only ever manifests with a *real* persisted logo — Phase 98's own testing apparently never exercised that data shape. Fixed with `@Transactional(readOnly = true)`, matching this codebase's existing controller-level `@Transactional` pattern.
- Non-destructive teardown: only the 5 containers this run (re)started were stopped/removed; the pre-existing, already-running `lexcv_minio` was left untouched; all 5 named volumes (`lexcv_lexcv_pgdata`, `lexcv_lexcv_uploads`, `lexcv_caddy_data`, `lexcv_caddy_config`, `lexcv_lexcv_minio_data`) survive intact. No connection to the live Hostinger VPS occurred at any point.

## Task Commits

Each task was committed atomically (this plan is verification-only; the one code commit below is a Rule 1 bug fix discovered live during Task 1/3, not a planned deliverable):

1. **Task 1: Bring up the dev stack and verify the routing matrix live** — no code changes; verification only (see Verification Evidence below). During this task's branding check, discovered the `PublicController` LOB bug (fixed under Task 3, see commit below).
2. **Task 2: Verify webpage and web asset namespaces never collide (LP-13, live)** — no code changes; verification only.
3. **Task 3: Verify no-hairpin internal resolution (criterion #5), then tear down** — `942152e` (fix) — `fix(100-04): make PublicController.getBranding() transactional for LOB read`, required to produce criterion (c)'s real-data proof; then non-destructive scoped teardown.

**Plan metadata:** SUMMARY commit follows this file (see below).

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/PublicController.java` - added `@Transactional(readOnly = true)` (+ import) to `getBranding()`, fixing a live-only Hibernate `@Lob`/auto-commit-mode failure.
- `.env` (worktree root, **not committed** — gitignored) - created to supply `docker-compose.yml`'s `${POSTGRES_*}/${JWT_*}/${MINIO_*}` substitutions, mirroring the main repo's real root `.env` values exactly (required for credential consistency with the reused `lexcv_lexcv_pgdata`/minio state).

## Decisions Made
See frontmatter `key-decisions` for full detail. Summary: adopted the pre-existing `lexcv` compose project (`-p lexcv`) instead of a fresh one (hardcoded `container_name` values forced this); reused the already-initialized shared Postgres volume rather than forcing a fresh DB; adapted Task 3(c)'s literal proof string to the real environment's data (persisted logo, not a coincidental tenant-name match); fixed the live-discovered `PublicController` transactional bug because it directly blocked this plan's own acceptance criteria; scoped teardown to avoid touching the not-owned, pre-existing `lexcv_minio`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `PublicController.getBranding()` threw 500 on any tenant with a real logo (Hibernate `@Lob` read outside a transaction)**
- **Found during:** Task 1 (branding check while validating the `/` route render) and blocking Task 3's criterion (c).
- **Issue:** `Tenant.logoDataUrl` is `@Lob`; PostgreSQL's JDBC Large Object API requires the LOB stream read to happen inside an explicit transaction. `PublicController.getBranding()` (Phase 98) had no `@Transactional` boundary, so any call against a tenant with a non-null `logoDataUrl` failed with `org.hibernate.HibernateException: Unable to access lob stream` → `org.postgresql.util.PSQLException: Large Objects may not be used in auto-commit mode`. `webpage`'s `fetchBranding()` catches this as `!response.ok` and fails open to the hardcoded `{nome:"LexCV", logoDataUrl:null}`, so the bug was silent from the frontend's perspective — the landing page still rendered, just without the real name/logo.
- **Fix:** Added `import org.springframework.transaction.annotation.Transactional;` and `@Transactional(readOnly = true)` on `getBranding()`, matching the existing controller-level `@Transactional` pattern already used in `ResourceController`/`ParecerController` in this codebase (no service-layer indirection exists for this controller).
- **Files modified:** `backend/src/main/java/com/lexcv/controllers/PublicController.java`
- **Verification:** Rebuilt the `backend` image, recreated the container, re-queried `GET /api/v1/public/branding` → `200 {"nome":"LexCV","logoDataUrl":"data:image/png;base64,iVBORw0KGgo..."}` (previously `500`). Re-fetched `/` and confirmed the real logo's base64 payload appears in the rendered HTML.
- **Committed in:** `942152e`

---

**Total deviations:** 1 auto-fixed (1 Rule 1 bug). 0 blocking issues left unresolved. 0 architectural questions.
**Impact on plan:** The fix was necessary to produce this plan's own criterion (c) evidence (a real, non-fallback branding value rendering through the internal network fetch) — without it, criterion #5 could still be argued as PASS via proofs (a)+(b) alone (both are independent of this bug), but proof (c) would have been permanently unobtainable against this shared dev database's real data. No scope creep: the fix is a single annotation + import, touches no schema, no other endpoint, and required no follow-up decisions.

### Notes (not deviations — environment-driven verification adaptations)

**1. Compose project name (`-p lexcv`) and volume reuse**
- This worktree's directory name (`agent-a6504e803b9367a69`) is not `lexcv`, so Docker Compose's default project-name derivation would not have matched the user's pre-existing `lexcv`-named project (confirmed via existing volume names `lexcv_lexcv_pgdata`, `lexcv_lexcv_net`, etc., and a `lexcv_minio` container already `Up 2 hours (healthy)` before this plan touched anything). Since `container_name` is hardcoded in `docker-compose.yml` (not modifiable — outside this plan's `files_modified: []`), any compose invocation of this file targets the exact same global container names regardless of project; a mismatched project name would have failed on Docker Engine container-name conflicts for every service, not just `minio`. Using `-p lexcv` was the only way to bring the stack up without either conflicting with or destructively removing the user's existing containers.
- **Consequence:** Postgres reused the existing `lexcv_lexcv_pgdata` volume (already initialized from prior sessions across this project's history), so `/api/v1/setup/status` reported `{"initialized":true}` from the very first poll — never `false`. This is explicitly one of the two branches the plan's own action text anticipates ("if already initialized:true, skip init... and proceed using the existing tenant"), so no plan violation occurred; the `POST /initialize` step was correctly skipped rather than attempted-and-403'd.

**2. Task 3(c) proof target adapted from tenant name to logo data**
- The plan's literal reference assumes a fresh `initialize` call created a tenant named "Escritorio Teste LexCV", which would be unambiguous proof of a real fetch (vs. the hardcoded `"LexCV"` fallback). Because the reused database's real tenant is named exactly `"LexCV"` — identical to the fallback string — a bare substring match on the tenant name cannot distinguish "real fetch succeeded" from "fetch failed, fell back to the hardcoded default" in this specific environment.
- Resolved by using the tenant's real, persisted `logoDataUrl` (`data:image/png;base64,iVBORw0KGgo...`) as the discriminator instead: `webpage/src/lib/branding.ts`'s `FALLBACK` constant is always `{nome:"LexCV", logoDataUrl:null}`, and `webpage/src/components/brand-mark.tsx` only renders an `<img>` when `logoDataUrl` matches a real `data:image/...;base64,` pattern — so the literal base64 payload appearing in the `/` HTML is *strictly* stronger, non-coincidental proof of a successful real internal fetch, fully preserving the check's original intent.
- No files were modified to make this adaptation; it is a change to which live HTML substring is asserted during verification, not a code or plan change.

**3. Benign `$`-in-.env warning observed, out of scope, not fixed**
- `docker compose ps`/`exec` emitted `The "TLWkBVhzy9fOogh60tOQ8OISwzdACelAEvIcJd2ja1nac0PwfY2ni" variable is not set. Defaulting to a blank string.` — caused by `CADDY_MINIO_PASSWORD_HASH`'s bcrypt hash (`$2a$14$TLWkB...`) in the root `.env`, which Docker Compose's own `.env`-file variable-expansion misparses as embedded `$VAR` references (the same class of footgun as Anti-Pattern 3 in `ARCHITECTURE.md`, but at the `.env`-loading layer rather than the hostinger heredoc). Confirmed harmless for this plan's scope: neither `docker-compose.yml` nor `Caddyfile` (dev) reference `CADDY_MINIO_USER`/`CADDY_MINIO_PASSWORD_HASH` anywhere, so the corrupted substitution never reaches any service this plan verifies. No fix applied — logged here for visibility only, matching the "log out-of-scope discoveries, do not fix them" scope-boundary rule. (`docker-compose.hostinger.yml`'s heredoc, where this class of bug is actually load-bearing, was already hardened against it in plan 100-03.)

## Issues Encountered

None beyond the auto-fixed bug documented above (which was resolved within this plan's own execution, not left open).

## Verification Evidence

### Task 1 — Routing Matrix (live, ROADMAP criterion #1)

| Route | Expected target | HTTP | Discriminator check | Result |
|-------|-----------------|------|----------------------|--------|
| `/` | webpage (landing) | 200 | `id="funcionalidades"` count = 1 (≥1); `/landing-static/` count = 1 (≥1) | **PASS** |
| `/login` | web | 200 | `/landing-static/` count = 0 | **PASS** |
| `/dashboard` | web | 200 | `/landing-static/` count = 0 | **PASS** |
| `/setup` | web | 200 | `/landing-static/` count = 0 | **PASS** |
| `/api/v1/setup/status` | backend | 200 | JSON body `{"initialized":true}` (has `initialized` key) | **PASS** |

All 6 services (`postgres`, `minio`, `backend`, `frontend`, `webpage`, `caddy`) confirmed running via `docker compose ps` before this matrix was run.

### Task 2 — LP-13 Asset Non-Collision (live, ROADMAP criterion #3)

| Asset | Namespace | Sample URL (real, extracted from live HTML) | HTTP | Result |
|-------|-----------|-----------------------------------------------|------|--------|
| webpage chunk | `/landing-static/_next/*` | `/landing-static/_next/static/chunks/0wez8zg~spqpi.js` | 200 | **PASS** |
| web chunk | `/_next/*` (unprefixed) | `/_next/static/chunks/03q757hpt3301.js` | 200 | **PASS** |

Namespaces are structurally non-overlapping (`/landing-static/_next/` vs. `/_next/`, no shared prefix) — LP-13's "chunks never collide under the same domain" holds live, not just by `assetPrefix` config reading.

### Task 3 — No-Hairpin Smoke Test (live, ROADMAP criterion #5)

| Proof | Check | Result | Verdict |
|-------|-------|--------|---------|
| (a) Internal service name | `docker compose exec webpage env \| grep BACKEND_API_ORIGIN` | `BACKEND_API_ORIGIN=http://backend:8080` | **PASS** |
| (b) In-container connectivity | `docker compose exec webpage sh -c "wget -qO- http://backend:8080/api/v1/setup/status"` | `{"initialized":true}` | **PASS** |
| (c) Real data rendered (adapted target, see Notes above) | `/` HTML contains the tenant's real `data:image/png;base64,...` logo (fallback is always `logoDataUrl:null`, never rendered as an `<img>`) | 1 match, byte-identical prefix to the direct `/api/v1/public/branding` response | **PASS** |
| Log cleanliness | `docker compose logs webpage \| grep -iE 'ECONNREFUSED\|ENOTFOUND\|fetch failed'` | 0 matches | **PASS** |

**Criterion #5: PASS** — all three converging proofs plus the log-cleanliness check pass. The research-flagged "hairpin fetch risk" (STATE.md Blockers/Concerns) is resolved: `webpage` reaches `backend` via the internal Docker service name over `lexcv_net`, never by hairpinning through the public domain.

### Teardown

- `docker compose -f docker-compose.yml -p lexcv stop postgres backend frontend webpage caddy` then `rm -f` on the same 5 services — scoped deliberately to exclude `minio` (not started by this run).
- Confirmed after teardown: only `lexcv_minio` remains (`Up 2 hours (healthy)`, unchanged); all 5 named volumes (`lexcv_lexcv_pgdata`, `lexcv_lexcv_uploads`, `lexcv_caddy_data`, `lexcv_caddy_config`, `lexcv_lexcv_minio_data`) still present.
- No `-v` flag used anywhere. No SSH/connection/deploy to the live Hostinger VPS occurred at any point in this session.

## Known Stubs

None. The one file touched (`PublicController.java`) is a bug fix, not a stub.

## User Setup Required

None for the code delivered. For anyone reproducing this exact live verification in a fresh environment with no pre-existing `lexcv` compose project: a root `.env` (see `.env.example`) is required for `docker-compose.yml`'s variable substitution — this plan created one locally (gitignored, not committed) mirroring the main repo's real values for credential consistency with the reused Postgres/MinIO state.

## Next Phase Readiness

- Phase 100 (Infraestrutura — Routing e Deployment) is now fully, live-verified: LP-13's runtime clause is satisfied with recorded pass/fail evidence, ROADMAP criteria #1/#3/#5 are all PASS against a real running stack, and the research-flagged hairpin risk is closed.
- This was the last plan (wave 3) of Phase 100, which was itself the last phase of milestone v2.12 (Landing Page) per `.planning/STATE.md`'s roadmap. No further plans are pending in this phase.
- One real, previously-latent bug was found and fixed live (`PublicController.getBranding()` transactional fix) — this should be considered a milestone-level fix, not phase-100-specific, since the endpoint itself was delivered in Phase 98. Recommend the milestone close-out step note this fix against Phase 98's own requirements (LP-01/LP-02, whichever covers the branding endpoint) for traceability, even though the commit lives on this phase's branch.
- No blockers. No live Hostinger VPS deploy occurred or is required by this plan — that remains explicitly out of scope per `100-CONTEXT.md`.

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/controllers/PublicController.java` contains `@Transactional(readOnly = true)` on `getBranding()` (confirmed via Edit tool's post-edit file state).
- FOUND: commit `942152e` present in `git log` (`fix(100-04): make PublicController.getBranding() transactional for LOB read`).
- No file deletions detected in the commit (`git diff --diff-filter=D HEAD~1 HEAD` empty).
- No untracked files left in the working tree (`git status --short` shows nothing after the commit; the worktree-local `.env` is intentionally gitignored and does not appear).
- Docker state confirmed clean post-teardown: only the pre-existing `lexcv_minio` container remains; all 5 named volumes intact; no live Hostinger VPS connection made.

---
*Phase: 100-infraestrutura-routing-e-deployment*
*Completed: 2026-07-15*
