---
phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac
plan: 04
subsystem: testing
tags: [live-uat, rbac, security, tenant-isolation, http-proof]

# Dependency graph
requires:
  - phase: 121-01
    provides: "Method-level @PreAuthorize(\"hasRole('PLATAFORMA_ADMIN')\") on AdminController.updateRbac — this plan proves it against the real Spring context, not just the hand-built AOP proxy"
  - phase: 121-02
    provides: "RbacTab Save-button-to-Badge+Tooltip swap and pnpm verify:bloqueio-rbac gate — this plan confirms it live in the browser"
provides:
  - "Live, non-static confirmation that ADMIN tenant callers get 403 on PUT /api/v1/admin/rbac and PLATAFORMA_ADMIN gets 200, against the real Spring context (not the Plan 01 hand-built AOP proxy)"
  - "Live confirmation the RbacTab Save action is genuinely gone for a tenant ADMIN, with both Tooltip interaction paths (mouse, keyboard) confirmed separately"
affects: [123-isol-04-auditoria-de-isolamento]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Structural DOM assertions (querySelectorAll + data-slot/data-state attributes) as a substitute for a human's visual judgment, used to make the negative claims ('no Guardar Regras button anywhere') and the keyboard-only claim (no mouse hover, focus() never called — real Tab keypresses only) independently checkable rather than just visually eyeballed"

key-files:
  created:
    - .planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-HUMAN-UAT.md
  modified: []

key-decisions:
  - "Used real keyboard Tab key presses (computer tool 'key' action) to reach the Tooltip trigger for point 4, rather than calling .focus() via JavaScript — the plan's own point 4 requires 'sem usar o rato, navegar com Tab', and a scripted .focus() call would not distinguish a focus-only-reachable-by-mouse regression from a real one, since both fire the same DOM focus event but only real Tab traversal proves the element is actually in the natural tab order at the position a keyboard user would reach it"
  - "Verified the negative claim in point 2 ('no Guardar Regras button exists') structurally via document.querySelectorAll('button') filtered by text content, not just by screenshot — a visual absence can be a false negative if the element scrolled off-screen or rendered with zero opacity; the structural check has no such blind spot"

requirements-completed: [ISOL-03]

# Metrics
duration: ~40min
completed: 2026-07-29
---

# Phase 121 Plan 04: Live UAT — 403/200 HTTP proof + RBAC tab checkpoint Summary

**All 8 checkpoint points plus the 4-code HTTP battery from Task 1 are CONFIRMADO — the milestone's single highest-risk claim (a real tenant ADMIN session gets 403 on `PUT /admin/rbac` against the live Spring context, not just the Plan 01 hand-built proxy) is proven, with the `PLATAFORMA_ADMIN` counter-test (200) ruling out a universally-closed gate, zero drift in the persisted RBAC matrix, and both Tooltip interaction paths (mouse, keyboard) confirmed as separate, non-merged verdicts.**

## Performance

- **Duration:** ~40 min (includes booting both dev servers from cold)
- **Tasks:** 2/2 completed (Task 1 automated HTTP battery + Task 2 human-verify checkpoint, performed directly since this is an autonomous run under standing user authorization)
- **Files modified:** 0 code files (verification-only, as designed)

## Accomplishments

- Measured, not assumed: `PUT /api/v1/admin/rbac` returns `403` for `admin@lexcv.cv` (tenant ADMIN) and `200` for `plataforma@lexcv.cv` (PLATAFORMA_ADMIN) against the actually-running backend — the real `@EnableMethodSecurity` + JWT filter + DB-derived `UserPrincipal` path, not the Plan 01 unit test's manually-assembled `AuthorizationManagerBeforeMethodInterceptor` proxy.
- The counter-test (step 6) closes the exact gap a hand-wavy "it returned 403, done" check would leave open: without it, a misspelled role literal in the annotation would also produce a universal 403, indistinguishable from success.
- Zero-drift proof: `GET /admin/rbac` (ADMIN cookies) captured before the battery and re-fetched after — `diff` reports no difference, confirming the no-op `PUT` payloads (the `GET`'s own `rolePermissions` object, sent back verbatim) never mutated the shared `t_role_permission` rows real tenants depend on.
- Structural (not just visual) confirmation that the "Guardar Regras" button is entirely gone from the DOM for a tenant ADMIN, and that the Tooltip opens via 2 real `Tab` keypresses alone — reusing and extending the Tooltip+`<span tabIndex={0}>` composition for the third time in this codebase (Phase 118, Phase 120, now Phase 121).
- Confirmed, as an explicitly-intentional observation (not a defect), that `PLATAFORMA_ADMIN` still cannot see the RBAC tab at all — `hasRbacManage` was deliberately left unwidened per `121-CONTEXT.md`.

## Task Commits

No code commits — this plan's only artifact is documentation, per its own `files_modified` declaration and the project's established convention for pure-verification plans (`120-06-SUMMARY.md`, `121-03-SUMMARY.md`).

1. **Task 1: Live HTTP proof battery** — no commit (environment operations and curl calls only, explicitly prohibited from touching code)
2. **Task 2: Human verification checkpoint** — `.planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-HUMAN-UAT.md` created

**Plan metadata:** committed alongside this SUMMARY.

## Files Created/Modified

- `.planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-HUMAN-UAT.md` — HTTP code table (7 rows) + 8-point verdict record, points 3/4 (mouse/keyboard Tooltip) recorded as separate verdicts per the plan's explicit requirement

## Decisions Made

- Performed both Task 1 (HTTP battery) and Task 2 (browser checkpoint) directly, standing in for the "operator," consistent with how `120-06` (Phase 120's equivalent live-UAT plan) was handled in this same autonomous run under the standing `/gsd:autonomous` authorization ("o claude decide as opções e avança"). No part of the checkpoint was auto-approved without actually performing it: every one of the 8 points was independently exercised (real HTTP calls, real clicks, real keyboard traversal, real DOM inspection) rather than inferred from source code.
- See key-decisions in frontmatter for the reasoning on using real `Tab` keypresses (not scripted `.focus()`) and structural DOM checks (not just screenshots) for the two claims most vulnerable to a shallow pass — the mouse-vs-keyboard distinction and the "button doesn't exist" negative claim.

## Deviations from Plan

### Auto-fixed Issues

None — no code was touched by this plan (by design).

### Process deviations (not code fixes)

**1. Both dev servers were cold (not running) at the start of this plan**
- **Found during:** Task 1 setup, before the login calls
- **Issue:** `curl` to both `localhost:8080` and `localhost:3000` returned connection-refused (exit 7) — neither server survived from the prior session's work.
- **Fix:** Started both via `preview_start` using the existing `.claude/launch.json` configurations (`backend`, `web`) already present from Phase 120's UAT; polled the backend's public branding endpoint in a bounded loop until it returned `200` before proceeding.
- **Files modified:** None
- **Verification:** Both servers confirmed responsive (`200`) before any Task 1 HTTP call was made.

## Issues Encountered

None — all 8 checkpoint points and all 4 HTTP-code pairs passed on the first attempt. No product code defects were found during this plan (contrast with Phase 118's UAT, which did find a real code bug; this result matches Phase 120's UAT, which also found zero defects).

Same recurring environment note as prior phases in this milestone: `psql` is not on `PATH` despite being installed (bundled binary at `C:\Program Files\PostgreSQL\18\bin\psql.exe` used directly for the secondary role/permission-count confirmation).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Phase 121 is fully verified and ready to close — all 3 ISOL requirements (ISOL-01, ISOL-02 via Plan 03; ISOL-03 via Plans 01/02/04) confirmed both statically and live. This was the last of Phase 121's 4 plans. Phase 122 (UTIL-01, usage report) can proceed — it has no direct dependency on this plan's live-verification artifacts, only on Phase 117's `countByTenantIdAndAtivoTrue` and Phase 120's `GET /platform/tenants`, both already shipped.

---
*Phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `.planning/phases/LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac/121-HUMAN-UAT.md` (grep count of `CONFIRMADO|FALHOU|NAO VERIFICADO` = 15, well over the required 8 — 8 checkpoint points + 7 HTTP battery rows)
- FOUND: `git status --porcelain -- backend web` empty at time of writing
- Re-confirmed all 4 critical HTTP codes are recorded in `121-HUMAN-UAT.md`'s table: PUT ADMIN=403, PUT plataforma=200, GET ADMIN=200, GET plataforma=403
- Re-confirmed points 3 and 4 have distinct, separately-worded verdicts (not merged into one "tooltip works" line)
