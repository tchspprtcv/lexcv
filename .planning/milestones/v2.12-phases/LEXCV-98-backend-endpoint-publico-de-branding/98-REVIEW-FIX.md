---
phase: 98-backend-endpoint-publico-de-branding
fixed_at: 2026-07-15T09:10:00Z
review_path: .planning/milestones/v2.12-phases/LEXCV-98-backend-endpoint-publico-de-branding/98-REVIEW.md
iteration: 3
findings_in_scope: 1
fixed: 0
skipped: 1
status: none_fixed
---

# Phase 98: Code Review Fix Report

**Fixed at:** 2026-07-15T09:10:00Z
**Source review:** .planning/milestones/v2.12-phases/LEXCV-98-backend-endpoint-publico-de-branding/98-REVIEW.md
**Iteration:** 3 (final iteration of this auto-fix loop)

**Summary:**
- Findings in scope: 1 (fix_scope = critical_warning; WR-01 only — 0 Critical findings; IN-01 through IN-04 excluded by scope)
- Fixed: 0
- Skipped: 1 (deliberate, re-confirmed architectural deferral — not a fixer failure)

This is the third and final fix iteration for this phase. WR-01 (CORS reachability) has now been carried forward and independently re-verified across all 3 iterations of this auto-fix loop. Rather than trusting the prior two fix reports' conclusions at face value, this iteration re-checked the underlying facts directly:

- Re-read `backend/src/main/java/com/lexcv/config/SecurityConfig.java` in full: `permitAll()` on `/api/v1/public/branding` (line 67) and `corsConfigurationSource()` registered against `"/**"` with `allowCredentials(true)` (lines 82-99) are byte-for-byte unchanged since commit `1b468a5` (iteration 1's documentation-only fix).
- Confirmed via `git log` that no commit since `1b468a5` has touched `SecurityConfig.java`; the only fix commit made against this phase since then (`a01fb98`) touched only `PublicControllerTest.java` (iteration 2's WR-02 test-coverage fix).
- Re-checked `backend/.env.example`: `CORS_ALLOWED_ORIGINS=http://localhost:3000` is still the only configured origin (the `web/` dashboard) — no `webpage/` origin entry exists.
- Confirmed no `webpage/` directory exists anywhere in the repository (repo root has only `backend/` and `web/`).
- Confirmed no Phase 99 planning artifacts exist: `.planning/milestones/v2.12-phases/` contains only the `LEXCV-98-...` directory; `.planning/ROADMAP.md` explicitly lists Phase 99 ("webpage/ — Nova App Next.js de Landing") as **"Not started"** with **0/TBD** plans, and its own "Next" line still points at the unexecuted `/gsd:plan-phase 99` as the next step.

All of this confirms the premise iterations 1 and 2 relied on is still true today: Phase 99's `webpage/` app does not exist and has not even been planned, so its fetch strategy (server-side vs. client-side browser fetch) — the single fact that determines whether any CORS remediation is needed at all, and if so, which one — remains genuinely undecided. Applying either of WR-01's two candidate fixes now (adding an unconfirmed origin to `CORS_ALLOWED_ORIGINS`, or registering a second cookie-free `CorsConfigurationSource`) would mean guessing at an architecture that hasn't been built, which iterations 1 and 2 both correctly declined to do. Nothing observed this pass changes that conclusion.

## Fixed Issues

None — the only in-scope finding (WR-01) was skipped as a deliberate, re-confirmed architectural deferral, not attempted and failed. See Skipped Issues below.

## Skipped Issues

### WR-01: CORS reachability for the endpoint's stated consumer remains an open, intentionally deferred architectural decision (terminal state for this auto-fix run)

**File:** `backend/src/main/java/com/lexcv/config/SecurityConfig.java:58-67` (permitAll entry + comment), `:77-98` (`corsConfigurationSource()` + comment)
**Reason:** Deliberately deferred, not attempted, for the third and final consecutive iteration — this is not a fixer failure or an oversight. The finding's root cause is an architectural decision that depends entirely on a component that does not exist yet: Phase 99's `webpage/` app. Independently re-verified this iteration (not merely re-asserted from prior reports): `SecurityConfig.java` is unchanged since commit `1b468a5`; `backend/.env.example`'s `CORS_ALLOWED_ORIGINS` still lists only the `web/` origin; no `webpage/` directory exists in the repo; and `.planning/ROADMAP.md` confirms Phase 99 is still "Not started," with no plan file yet created. Because whether this endpoint will ever be called cross-origin from a browser (vs. server-side, where CORS never applies) is exactly the open question Phase 99's plan is expected to resolve, picking a remediation now would mean guessing at unbuilt architecture — the same reasoning iterations 1 and 2 both applied, and which still holds without any new information this pass.

Fabricating a CORS origin or prematurely registering a second `CorsConfigurationSource` scoped to `/api/v1/public/**` would itself introduce risk: an unverifiable, potentially-wrong config now, and possible unnecessary attack-surface widening later if the guessed origin/strategy turns out wrong once `webpage/` actually lands.

**Terminal state for this auto-fix run:** This is iteration 3 of 3 — no further automatic re-review/fix iterations are scheduled for this finding within this loop. It should now be tracked as an explicit acceptance-criterion / pre-condition item in the Phase 99 (or 100) plan, exactly as WR-01's own Fix section recommends, rather than re-entering the auto-fix loop again. This finding is not expected to resolve itself through further automated passes — it requires the Phase 99 planning decision (server-side fetch vs. client-side browser fetch) as a human/planning input. Once that decision is made, the fix is mechanical: either no code change (server-side), or a one-line `CORS_ALLOWED_ORIGINS` addition / narrower `CorsConfigurationSource` (client-side) — both already fully specified in `98-REVIEW.md`'s WR-01 Fix section.

**Original issue:** `permitAll()` on `/api/v1/public/branding` exempts authentication only, not Spring's CORS filter; `corsConfigurationSource()` still applies the `app.cors.allowed-origins` allowlist (with `allowCredentials(true)`) to `"/**"`, which includes this path. `CORS_ALLOWED_ORIGINS` in `backend/.env.example` contains only the `web/` dashboard origin — if `webpage/` (once built) performs a client-side browser `fetch()` from a different origin, the server will return 200 but the browser will block the JS from reading the response body, with no error surfaced anywhere in the codebase today (since `webpage/` doesn't exist to exercise that path).

---

_Fixed: 2026-07-15T09:10:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3 (final)_
