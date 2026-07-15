---
phase: 100-infraestrutura-routing-e-deployment
fixed_at: 2026-07-15T17:00:27Z
review_path: .planning/milestones/v2.12-phases/LEXCV-100-infraestrutura-routing-e-deployment/100-REVIEW.md
iteration: 2
findings_in_scope: 1
fixed: 0
skipped: 1
status: none_fixed
---

# Phase 100: Code Review Fix Report

**Fixed at:** 2026-07-15T17:00:27Z
**Source review:** .planning/milestones/v2.12-phases/LEXCV-100-infraestrutura-routing-e-deployment/100-REVIEW.md
**Iteration:** 2 (final iteration of the 3-iteration auto-fix loop)

**Summary:**
- Findings in scope (critical + warning): 1
- Fixed: 0
- Skipped: 1

## Fixed Issues

None — all findings were skipped.

## Skipped Issues

### WR-01: Caddy routing drift — hostinger deployment still silently drops MinIO console access that `Caddyfile.prod` provides

**File:** `docker-compose.hostinger.yml:20-41` (no `ports:` for `minio`), `docker-compose.hostinger.yml:113-147` (embedded Caddy heredoc), cf. `Caddyfile.prod:5-10`

**Reason:** Deliberate, evidence-based deferral reached after active investigation and empirical testing — not a scope-based punt and not a repeat of iteration 1's deferral (iteration 1 deferred this as "out of scope for this pass"; this iteration investigated it directly and concludes it is technically unfixable within the required constraints). Chain of evidence:

1. **The reviewer's suggested "structural" fix (mount `Caddyfile.prod` directly, delete the heredoc) is explicitly excluded** — this repo already tried exactly this, this session, in commit `1482f47` (`fix: configure Caddy for alcv.tech domain with HTTPS support`, 2026-07-14T22:51:33-01:00, which replaced the heredoc `entrypoint:` with `volumes: - ./Caddyfile.prod:/etc/caddy/Caddyfile:ro`), and it was reverted 17 minutes later in `ba67f4e` (`fix: use Caddy native env var expansion {$}DOMAIN_NAME for Hostinger VPS`, 2026-07-14T23:09:10-01:00) because file-mounting was not reliable in Hostinger's actual deploy path. This is recorded as "Anti-Pattern 3" in `.planning/research/ARCHITECTURE.md` and as a mandatory (non-discretionary) constraint in this phase's own `100-CONTEXT.md`. Reapplying it would very likely reintroduce a known-bad, already-rejected pattern.

2. **The reviewer's "minimal" fix (add `handle_path /minio-console* { basicauth { {$CADDY_MINIO_USER} {$CADDY_MINIO_PASSWORD_HASH} } reverse_proxy minio:9001 }` directly to the heredoc) was *also* already tried and reverted** — found independently while investigating; it is not mentioned in the WR-01 finding text itself. `ba67f4e` (above) reintroduced exactly this block using Caddy-native `{$VAR}` syntax alongside `{$DOMAIN_NAME}` templating. Three minutes later, `534fa92` (`fix: remove MinIO basicauth from Caddy - fix crash due to bcrypt hash dollar signs`, 2026-07-14T23:12:18-01:00) deleted the block and its supporting `environment:` entries. Two minutes after that, `67e2120` (`fix: hardcode alcv.tech in Caddy entrypoint - avoid Docker Compose brace expansion bug`, 2026-07-14T23:14:56-01:00) removed `{$DOMAIN_NAME}` entirely, hardcoding `alcv.tech` literally — arriving at the current, fully-hardcoded, zero-`$` heredoc. Root cause per this phase's own `100-CONTEXT.md` ("Lições de Git History — MANDATÓRIAS, não discricionárias"): Docker Compose interpolates `$VAR`/`${VAR}` across the **entire YAML file** before Caddy ever sees the content, including inside the `entrypoint: |` heredoc string — a Compose-file-parsing-layer bug, not a shell-quoting issue.

3. **I independently reproduced this failure empirically** (real `docker compose config` / `docker compose run` via the Docker Desktop instance available in this environment, not just inference from git history), using throwaway test files in the scratchpad directory (never touching the repo), mirroring this file's exact heredoc structure with a synthetic bcrypt-shaped string:
   - Hardcoding the literal hash `$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy` in the heredoc produced: `WARNING: The "N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy" variable is not set. Defaulting to a blank string.` — Compose silently truncated the resolved value to `$2a$10` before the container ever started, byte-for-byte reproducing the class of crash `534fa92` describes.
   - Sourcing the identical hash via `${CADDY_MINIO_PASSWORD_HASH}` from a `.env` file (the secret-hygienic approach, matching how every other credential in this exact file — `POSTGRES_PASSWORD`, `MINIO_ROOT_PASSWORD`, `JWT_SECRET`, etc. — is handled) produced the **identical** truncation and warning. Routing the hash through an env var does not avoid the bug, because Compose's interpolation also scans the substituted value.
   - The only mitigation that worked was manually escaping every literal `$` as `$$` in a hardcoded string (confirmed via a real container run that the resulting in-container file has the intact, correct hash). But this requires (a) hardcoding the real bcrypt hash directly into a git-tracked file instead of referencing it from the untracked `.env` — a credential-hygiene regression versus every other secret in this file and versus how `Caddyfile.prod` handles this same credential today — and (b) introducing new literal `$` characters into the file, which directly violates the zero-`$` constraint this file has operated under since `67e2120` and which `100-CONTEXT.md` documents as mandatory, specifically *because of* these two already-reverted incidents.

4. **A bcrypt hash cannot be expressed without literal `$` delimiters** (`$2a$10$...` is the format itself), so "add a working basicauth block" and "zero new `$` characters" are mutually exclusive for this specific heredoc — not merely difficult to reconcile. No variant of this fix (hardcoded, env-var-sourced, or otherwise) adds real, working MinIO authentication to this file without either reintroducing the exact bug class already reverted twice in this session (`534fa92`, `67e2120`) or shipping a silently broken/truncated credential.

5. **This would be the third distinct attempt at this exact mechanism in this session** (`1482f47`→`ba67f4e` for the file-mount attempt; `ba67f4e`→`534fa92` for the basicauth-in-heredoc attempt), and this is the final iteration of a 3-iteration auto-fix loop — not a context in which a fourth attempt at a pattern with a two-strike failure history should be made speculatively, especially with no way to validate against a real Hostinger deploy from this environment.

6. **The residual risk is an availability gap, not an open security hole**: `100-REVIEW.md` itself notes that `minio` exposing no host port in this file is correct, intentional hardening (mirrors how `Caddyfile.prod` only reaches MinIO through an authenticated proxy route, never a raw port). The current state means the hostinger console is unreachable (an operability inconvenience for admins, presumably worked around via SSH port-forwarding), not that it is exposed insecurely. Whether hostinger's operators deliberately chose to omit a public admin console (a defensible security posture) versus this being an accidental omission during the original Phase 52 work ("52-01 add MinIO prod overlay, Caddy console route") cannot be resolved from any available decision record for that phase.

7. **Orthogonal to this phase's own requirements**: this finding originates from Phase 52, not from Phase 100's LP-13 through LP-16 requirements, none of which mention MinIO.

**Conclusion:** deferred, with a concrete recommendation for how to actually close it: a dedicated follow-up task should decide, with a written decision record, between (a) accepting hostinger's MinIO console as intentionally admin-only-via-SSH-tunnel and closing this as "won't fix," or (b) if console access is genuinely required, redesigning the credential-delivery mechanism for hostinger specifically so the hash never has to survive a pass through Compose's YAML interpolator as file text — e.g., generating/reading the basicauth credential inside the container's own entrypoint at boot (`caddy hash-password`, or reading a value from a file mounted via a Docker secret rather than embedded in `entrypoint:` text) — rather than a same-session third attempt at text-substitution into this specific heredoc.

**Original issue:** `Caddyfile.prod` has a `handle_path /minio-console* { basicauth {...} reverse_proxy minio:9001 }` block; the hostinger heredoc's Caddy config (lines 124-137) has no equivalent — it otherwise matches the plain dev `Caddyfile`, not `Caddyfile.prod`. Combined with `minio` exposing no host ports in this file (unlike dev's `9000:9000`/`9001:9001`), there is currently no path at all — neither reverse-proxied nor direct — to the MinIO admin console on the hostinger target.

---

_Fixed: 2026-07-15T17:00:27Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2 (final)_
