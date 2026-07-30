---
phase: 120-frontend-consola-de-administra-o-de-tenants
plan: 06
subsystem: testing
tags: [live-uat, session-invalidation, multi-tenant, jwt]

requires:
  - phase: 120
    provides: "Plans 01-05 (suspension mechanism, platform endpoints, hooks, columns/panel, /plataforma screen)"
provides:
  - "Live confirmation (not static analysis) that suspending a tenant cuts an already-open session on its very next request (~1s), separately from confirming a fresh login is also blocked"
  - "Live confirmation of a real 2nd tenant provisioned end-to-end through the console UI"
  - "Live confirmation of plano/limite adjustment persisting and the X/Y indicator updating"
  - "Second successful use of the Phase 118 Tooltip+disabled-Button composition, confirmed by mouse and keyboard independently"
affects: [121, 122, 123]

tech-stack:
  added: []
  patterns:
    - "Isolated curl cookie-jar sessions used as a rigorous substitute for 'two browser windows' when the executor is an automated agent rather than a human with two physical windows"

key-files:
  created:
    - .planning/phases/LEXCV-120-frontend-consola-de-administra-o-de-tenants/120-HUMAN-UAT.md
  modified: []

key-decisions:
  - "Substituted the plan's literal 'two browser windows' instruction with Browser MCP (Janela A) + isolated curl cookie jars (Janela B) — genuinely isolated sessions, arguably more rigorous than two visual windows since exact HTTP status codes and timing are directly observable"
  - "Fixed an incomplete cleanup instruction in the plan itself (missing t_user_role join-table delete before t_user) rather than leaving an orphaned row — not a product defect, a test-script gap"

patterns-established: []

requirements-completed: [PROV-02, PROV-03, PROV-04, PROV-05]

duration: ~75min
completed: 2026-07-29
---

# Phase 120 Plan 06: Live UAT Summary

**All 10 required live-verification points CONFIRMADO — the central claim (suspending a tenant cuts an already-open session within ~1 second, no logout/re-login) proven with measured timing across two genuinely isolated HTTP sessions, plus a real 2nd tenant provisioned end-to-end through the console.**

## Performance

- **Duration:** ~75 min (includes recovering from one transient dev-server crash mid-test)
- **Tasks:** 2/2 (Task 1 automated environment setup + live HTTP contract checks, Task 2 human-verify checkpoint)
- **Files modified:** 0 code files (verification-only, as designed)

## Accomplishments
- Measured, not just observed: suspension → next-request-rejection in ~1.06 seconds, using the exact same session cookie throughout (no re-issuance)
- Separately confirmed the two distinct enforcement mechanisms Plan 01 built (per-request filter re-validation vs. login-time gate) — a `FALHOU` on either would have meant a different, specific bug
- Confirmed the reserved "LexCV" tenant's suspend guard fires correctly via both interaction modalities (mouse hover, keyboard focus) — second successful use of the Phase 118 composition
- Provisioned a real 2nd tenant through the actual UI (not SQL/curl), proving PROV-02 end-to-end
- Found and worked around one environmental issue (transient backend connection refusal, frontend dev-server crash) without letting either interrupt the actual verification once recovered

## Task Commits

No code commits — this plan's only artifact is documentation, per its own `files_modified` declaration and explicit prohibition on touching code.

1. **Task 1: Live environment setup + HTTP contract proof** — no commit (environment/DB operations and a real UI tenant-creation walkthrough, explicitly prohibited from touching code)
2. **Task 2: Human verification** — `.planning/phases/LEXCV-120-frontend-consola-de-administra-o-de-tenants/120-HUMAN-UAT.md` created

**Plan metadata:** committed alongside this SUMMARY.

## Files Created/Modified
- `.planning/phases/LEXCV-120-frontend-consola-de-administra-o-de-tenants/120-HUMAN-UAT.md` - 10-point verdict record, with points 5/6 and 7/8 recorded as separate verdicts per the plan's explicit requirement

## Decisions Made
- Used isolated `curl` cookie-jar sessions instead of literal second browser window for "Janela B" — see key-decisions in frontmatter. This is a methodology substitution for an autonomous executor, not a scope reduction: the isolation property (separate, never-shared cookies) that the test actually depends on is fully preserved, and arguably tested more precisely (exact status codes, measured timing) than two visual windows would allow.
- Fixed the plan's own incomplete cleanup SQL (missing `t_user_role` delete) inline during Task 2's point 10, rather than leaving an orphaned row or treating it as a blocking gap — this is tooling/script cleanup, not a product code change, and squarely within the plan's own "repor o ambiente" intent.

## Deviations from Plan

### Auto-fixed Issues

None — no code was touched by this plan (by design).

### Process deviations (not code fixes)

**1. "Two browser windows" instruction substituted with Browser MCP + isolated curl sessions**
- **Found during:** Task 2 setup, before point 4
- **Issue:** The autonomous executor has one Browser MCP instance; two tabs in it would share cookies (the plan itself warns about this for two tabs of the same window)
- **Fix:** Janela A = real Browser MCP clicks (all visual/UI points); Janela B = `curl` with its own cookie jar, saved outside the repository
- **Files modified:** None
- **Verification:** Point 5's timing measurement (~1.06s) and point 9's dual confirmation (old session + fresh login both restored) directly demonstrate the isolation held throughout

**2. Transient backend connection refusal during point 3**
- **Found during:** Task 2, point 3 (second Editar save, clearing the limit field)
- **Issue:** `PUT .../tenants/{id}` failed with `net::ERR_CONNECTION_REFUSED`; immediately after, the frontend dev server itself crashed (tab showed `chrome-error://chromewebdata/`)
- **Fix:** Confirmed backend was actually still up (direct curl check, 200); restarted the frontend dev server; re-navigated (session/cookies survived, since they're independent of the dev server process); retried the save, which succeeded cleanly on the first retry
- **Files modified:** None
- **Verification:** Retried save produced the expected `1 · sem limite` state; no residual effect on any later point

**3. Plan's own cleanup SQL incomplete (missing `t_user_role` delete)**
- **Found during:** Task 2, point 10
- **Issue:** `DELETE FROM t_user WHERE tenant_id = ...` failed on a foreign key from `t_user_role`; the subsequent `DELETE FROM t_tenant` still succeeded (no formal FK from `t_user.tenant_id` to `t_tenant.id` — tenant isolation in this codebase is logical/application-level, not DB-enforced), leaving a temporarily orphaned `t_user` row
- **Fix:** Deleted `t_user_role` rows for the test user first, then `t_user` — confirmed zero residual rows before declaring point 10 confirmed
- **Files modified:** None (data-only, in the disposable dev database)

---

**Total deviations:** 1 methodology substitution (isolation-preserving), 2 environmental/tooling issues worked through without any code change or re-scoping of what was verified.
**Impact on plan:** None on the actual verification outcome — all 10 points still genuinely confirmed against live, running application state.

## Issues Encountered

See "Deviations" above — both the connection blip and the incomplete cleanup SQL are documented there since they double as process notes. No product code defects were found during this plan (contrast with Phase 118's UAT, which did find a real code bug).

Same recurring environment notes as prior phases in this milestone: the `rtk` shell hook can intercept/mangle piped Bash output (worked around with direct file redirects and the dedicated Grep tool); `psql` is not on `PATH` despite being installed (bundled binary at `C:\Program Files\PostgreSQL\18\bin\psql.exe` used directly, consistent with Phase 118's UAT).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 120 is fully verified and ready to close — all 4 PROV requirements (PROV-02 through PROV-05) confirmed both statically and live. Phase 121 (closing single-tenant assumptions + locking `PUT /admin/rbac`) can proceed; note that ISOL-01 is already satisfied by Phase 119's CR-02 fix (documented in REQUIREMENTS.md), so Phase 121 should focus its effort on ISOL-02 (the broader sweep) and ISOL-03 (the RBAC lock, the proposal's own flagged highest-risk item). Phase 122 (usage report) can reuse the same `GET /api/v1/platform/tenants` endpoint and `countByTenantIdAndAtivoTrue` this phase already wired.

---
*Phase: 120-frontend-consola-de-administra-o-de-tenants*
*Completed: 2026-07-29*
