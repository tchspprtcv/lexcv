---
phase: 122-relat-rio-de-utiliza-o-por-tenant
plan: 04
subsystem: testing
tags: [live-uat, partial, tooling-blocker, tenant-plano-migration]

requires:
  - phase: 122-03
    provides: "Ver Relatório entry link + verify:relatorio-utilizacao gate (15/15), UTIL-01 closed"
provides:
  - "Live HTTP confirmation of authorization (200/403) and suspended-tenant-visibility (A1-A3), including discovery and fix of a real, previously-uncharacterized functional regression"
  - "Honest record of what remains unverified (H1-H6, browser-visual scenarios) and why"
affects: [123-isol-04-auditoria-de-isolamento]

tech-stack:
  added: []
  patterns: []

key-files:
  created:
    - .planning/phases/LEXCV-122-relat-rio-de-utiliza-o-por-tenant/122-HUMAN-UAT.md
  modified: []

key-decisions:
  - "Did not fabricate or infer PASS verdicts for H1-H6 despite strong indirect evidence (all code/gates green, A1-A3 all passing) that they would likely also pass — the plan explicitly requires live visual/interactive observation for exactly the claims no source-level gate can prove (real navigation, real rendering, absence of a content flash), and no amount of indirect confidence substitutes for that observation once it's asked for"
  - "Ran the pending 120b-backfill-tenant-plano.sql migration against the dev database mid-task, after discovering it causes a real 500 (not the previously-assumed cosmetic badge issue) on any tenant write — a genuine, unplanned finding investigated and fixed rather than worked around or ignored"

requirements-completed: []

duration: ~90min (majority spent diagnosing a Browser-pane tooling blocker)
completed: 2026-07-30
---

# Phase 122 Plan 04: Live UAT (partial) Summary

**Task 1 (automated HTTP battery, A1-A3) fully complete, including discovery and fix of a real functional regression (pending migration causing 500s on tenant writes, not merely a cosmetic badge issue as previously documented). Task 2 (6 human browser scenarios, H1-H6) could not be completed — a persistent Browser-MCP tooling blocker ("Browser pane is currently hidden/not displayed"), not a product defect, prevented interactive browser verification after extensive troubleshooting.**

## Performance

- **Duration:** ~90 min (majority spent diagnosing the Browser-pane blocker, not phase work itself)
- **Tasks:** 1/2 completed (Task 1 automated; Task 2 attempted extensively, blocked)
- **Files modified:** 0 code files (verification-only, as designed); 1 data-only fix (migration run against dev DB, not a code change)

## Accomplishments

- A1 (positive authorization): `plataforma@lexcv.cv` gets `200` on `GET /platform/tenants`, all 6 fields present per tenant.
- A2 (negative authorization, ASVS L1 proof): `admin@lexcv.cv`, separate cookie jar, gets exactly `403` — not `200`, not `500`.
- A3 (suspended tenant stays visible): after fixing a real blocker (below), confirmed a suspended tenant remains in the list with `ativo:false`, same `id`, unchanged tenant count.
- **Real finding, investigated and fixed**: the first suspend attempt 500'd with `DataIntegrityViolationException` on `Tenant.plano`. Root-caused to Phase 120's CR-01 fix (`Tenant.plano` now `nullable=false` at the JPA level) combined with this dev database never having run the already-written, already-pending `120b-backfill-tenant-plano.sql` migration. `@Builder.Default` only supplies a default for entities built via Lombok's builder — it does nothing for entities Hibernate loads from an already-populated, still-nullable column. Ran the migration directly (`psql -f migrations/120b-backfill-tenant-plano.sql`); confirmed both existing tenants now have `plano=STARTER`; confirmed the same suspend operation that previously 500'd now returns `200` cleanly. Updated `STATE.md` to correct this migration's documented severity from "cosmetic" to "functional blocker" (commit `5da4de2a`), since this is a real risk for any future production deploy that skips this migration.
- Environment left clean: the test-suspended tenant was reactivated via direct API call (since the browser console was unavailable for the plan's own preferred cleanup path), confirmed `ativo:true` again, zero code files changed (`git status --porcelain -- backend web` empty throughout).

## Task Commits

1. **Task 1: Automated HTTP battery** — no commit for the battery itself (environment/HTTP operations only); 1 commit for the STATE.md severity correction this task's finding required (`5da4de2a`)
2. **Task 2: Human verification checkpoint** — `.planning/phases/LEXCV-122-relat-rio-de-utiliza-o-por-tenant/122-HUMAN-UAT.md` created, documenting both what was confirmed (A1-A3) and what remains unverified (H1-H6) with the specific reason

**Plan metadata:** committed alongside this SUMMARY.

## Files Created/Modified

- `.planning/phases/LEXCV-122-relat-rio-de-utiliza-o-por-tenant/122-HUMAN-UAT.md` — full A1-A3 confirmation record plus an explicit, reasoned "NÃO VERIFICADO" section for H1-H6, rather than a silent gap or a fabricated pass.

## Decisions Made

See key-decisions in frontmatter. In short: chose honest non-verification over inferred/fabricated verification for H1-H6, and chose to investigate-and-fix rather than route around the real plano=NULL regression found in A3.

## Deviations from Plan

### Auto-fixed Issues (Rule 3 — non-blocking, in-scope)

**1. `120b-backfill-tenant-plano.sql` migration was pending in this dev database, causing a real 500 on tenant writes**
- **Found during:** Task 1, scenario A3 (attempting to suspend a tenant for the live UAT)
- **Issue:** `PATCH /platform/tenants/{id}/ativo` 500'd with `DataIntegrityViolationException` on `Tenant.plano` — a functional regression, not the "cosmetic wrong badge" this exact pending migration was previously documented as causing.
- **Fix:** Ran the already-written, already-reviewed migration directly against the dev database via `psql`. Confirmed both existing tenants now have `plano=STARTER`; confirmed the suspend operation that previously 500'd now succeeds.
- **Files modified:** None (data-only fix in the disposable dev database) — `STATE.md` was updated to correct the documented severity of this already-tracked pending item.
- **Verification:** Direct `SELECT`/`PATCH`/`GET` round-trip before and after, all confirmed.

### Process deviations (not code fixes)

**1. Task 2 (H1-H6) could not be completed — Browser MCP tooling blocker**
- **Found during:** Task 2 setup, immediately after Task 1 completed
- **Issue:** Extensive troubleshooting (cache-clear + restart of the frontend dev server, fresh tabs, explicit tab-fronting, direct navigation to `/login` instead of `/` after diagnosing an unrelated known Suspense-hydration hang on the root route, sequential vs. parallel tool calls, multiple waits) still left interactive browser actions (`form_input`, `left_click` follow-through, `screenshot`, `get_page_text` in most attempts) failing with "the Browser pane is currently hidden" / "not displayed" — while simple metadata calls (`tabs_context`) kept succeeding throughout, and the backend/frontend themselves were repeatedly confirmed healthy via direct `curl` during the same window.
- **Resolution:** None found within this session. This is recorded as a genuine, unresolved tooling blocker, not a product defect — no code change was attempted to "fix" it, since there is nothing in this codebase to fix.
- **Files modified:** None.
- **Verification:** N/A — this is the thing that could not be verified.

## Issues Encountered

Both issues above are the substantive content of this plan's execution. No other issues.

## User Setup Required

**Yes — this is the actionable item from this plan.** H1-H6 (6 live browser scenarios: one-click reachability, 4-field rendering + cross-screen number consistency, suspended-tenant visibility under search, mobile card layout, access-denied-without-flash for a tenant ADMIN, and single sidebar nav entry) still need to be run in a session where the Browser MCP pane is functioning normally. Recommend re-running this plan's Task 2 specifically (`122-HUMAN-UAT.md`'s H1-H6 section) once that tooling is confirmed healthy, rather than re-running the whole phase.

## Next Phase Readiness

Not yet ready to declare Phase 122 fully closed. `UTIL-01` is closed (Plan 03, on code + gate evidence), and 3 of the phase's 4 live-verification scenarios that could run did run and passed, including catching a real cross-phase regression. But 6 of 9 total UAT scenarios remain genuinely unverified due to tooling, not product risk. Recommend: retry H1-H6 in a working browser session before considering this phase's live verification complete, or explicitly accept the current evidence (all automated gates green, 3/9 live scenarios independently confirmed, zero known product defects) as sufficient and proceed — this is a judgment call for whoever reviews this record, not one to make unilaterally by silently marking it done.

---
*Phase: 122-relat-rio-de-utiliza-o-por-tenant*
*Completed: 2026-07-30 (partial — see Task 2 status above)*
