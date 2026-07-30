---
phase: 122-relat-rio-de-utiliza-o-por-tenant
plan: 02
subsystem: testing
tags: [junit5, mockito, spring-boot, regression-test, multi-tenant, platform-admin]

# Dependency graph
requires:
  - phase: 120-consola-de-administra-o-de-tenants
    provides: "PlatformAdminController.listTenants()/toSummary() (the code under test) and PlatformAdminControllerTest's Group A direct-instantiation Mockito harness (novoController(), Tenant/TenantPlano fixtures) this plan extends"
provides:
  - "listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta — a 4th listTenants_* test proving, for the first time, that a suspended tenant (.ativo(false)) still appears in GET /api/v1/platform/tenants with its state faithfully transported (ativo=false), instead of being silently filtered"
affects: [122-03 (frontend wiring for the report screen), 123 (ISOL-04 dedicated isolation audit, whose own scope text names \"the usage report\" as one of its three audit targets)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Group A direct-controller-instantiation regression test (no Spring context, no MockMvc) — same convention as the 3 pre-existing listTenants_* tests"
    - "Single any()-matcher Mockito stub (countByTenantIdAndAtivoTrue(any())) for a 2-fixture test with no per-id assertions, avoiding UnnecessaryStubbingException under this project's strict MockitoExtension"

key-files:
  created: []
  modified:
    - backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java

key-decisions:
  - "Zero production code changes — the new test is a pure regression guard for behavior already correct today (no .filter(...) exists anywhere in listTenants()'s stream pipeline, confirmed by direct reading before and after)"
  - "requirements mark-complete NOT run for UTIL-01, despite it being listed in this plan's own frontmatter — REQUIREMENTS.md traceability requires the report screen to be actually reachable (Plan 03/04), mirroring the established Phase 120 Plan 02/03/04 precedent for PROV-02/PROV-05"

patterns-established:
  - "None new — this plan strictly reuses the Group A test-authoring convention already established by Phase 120 Plan 02"

requirements-completed: []

# Metrics
duration: ~10min
completed: 2026-07-30
---

# Phase 122 Plan 02: Regression Guard for Suspended-Tenant Visibility Summary

**Added `listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta` to `PlatformAdminControllerTest`, proving server-side that `GET /api/v1/platform/tenants` already includes suspended tenants (`ativo=false`) — a behavior that was correct but, until now, entirely unproven by any test.**

## Performance

- **Duration:** ~10 min
- **Started:** ~2026-07-30T03:00:00Z
- **Completed:** 2026-07-30T03:10:59Z
- **Tasks:** 1 completed
- **Files modified:** 1

## Accomplishments

- Closed the one genuine backend gap this phase's research identified: `listTenants()` demonstrably included suspended tenants already (no `.filter()` anywhere in its pipeline), but all 3 pre-existing `listTenants_*` tests built their fixtures with `.ativo(true)` only, leaving that property with zero regression coverage.
- New test passed on the very first run, against the completely unmodified `PlatformAdminController` — exactly as the plan predicted ("verde imediato").
- Full backend suite (183 tests) and `mvn spotbugs:check` both stayed green after the change, confirming zero regressions and zero new SAST findings from a test-only diff.

## Task Commits

Each task was committed atomically:

1. **Task 1: Acrescentar a guarda de regressao de tenant suspenso ao Grupo A de PlatformAdminControllerTest** - `3144d6d` (test)

**Plan metadata:** committed together with this SUMMARY, STATE.md, and ROADMAP.md (see final commit in this session).

_Note: This task carried `tdd="true"` in the plan, but per the plan's own explicit instructions this was not a RED→GREEN cycle — the test was expected (and required) to pass immediately against unmodified production code, since it guards already-correct behavior rather than fixing a bug. A single `test(122-02):` commit is therefore the correct and complete commit history for this task, not an incomplete TDD sequence._

## Files Created/Modified

- `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` — added one new `@Test` method (with a 4-sentence Javadoc explaining its purpose) inside the existing "Grupo A: comportamento de listTenants/updateTenant/setTenantAtivo" section, positioned exactly between `listTenants_devolveOrdenadoPorNomeCaseInsensitiveMesmoQuandoFindAllDevolveForaDeOrdem` and `updateTenant_comPlanoELimiteValidosDevolve200EGravaComAtivoInalterado`. Zero existing methods were reordered, reformatted, or otherwise touched. Zero new imports were needed (all types/static-imports used by the new test were already present in the file).

## Decisions Made

- No production code changes: `PlatformAdminController.listTenants()` (lines 106-113) and `toSummary(Tenant)` (lines 191-200) were read both before writing the test (to confirm no filter exists) and are unmodified in the final diff (`git diff --name-only` lists exactly one file, `PlatformAdminControllerTest.java`).
- `requirements mark-complete` was deliberately NOT run for `UTIL-01`, even though it appears in this plan's frontmatter `requirements` field. The plan's own success-criteria note is explicit: `REQUIREMENTS.md` requires an actually-reachable internal report showing 4 fields per tenant, which this plan (a backend test-only change) does not deliver. This mirrors the established precedent from Phase 120 Plans 02/03/04, which each deliberately skipped `mark-complete` for `PROV-02`/`PROV-05` despite listing them, until the plan that made the screen genuinely reachable (Phase 120 Plan 05) closed them.

## Deviations from Plan

None - plan executed exactly as written. The single task's behavior, arrange/act/assert shape, Javadoc content constraints (no literal reproduction of the filter expression being guarded against, no self-referential repetition of the test's own name), insertion point, and verification commands were all followed exactly as specified in the plan's `<action>` and `<interfaces>` blocks.

**One documentation-accuracy note (not a deviation, no fix required):** the plan's acceptance criteria stated that, before this task, `grep -c '\.ativo(false)'` over code lines in the test file returned 0. In fact it already returned 1 file-wide, from an unrelated pre-existing test (`setTenantAtivo_comTrueSobreLexCVDevolve200EGrava`, which constructs a tenant starting as `.ativo(false)` before reactivating it) — a different handler's test, not one of the `listTenants_*` fixtures the plan was describing. This does not affect correctness: the automated verify gate only asserts the final count is `>= 1` (now 2), not a before/after delta, so it passes regardless. Noted here only for transparency, not logged as a deviation since no plan text or production code needed to change.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Phase 122 Plan 01 (frontend `relatorio` columns/route components) is running concurrently in a separate agent this session, with zero file overlap confirmed by the plan-checker (backend/src/test vs. web/src) and zero conflicts observed in practice (commit history interleaved cleanly: `f18bd049` from Plan 01 landed immediately before this plan's `3144d6d`).
- Backend is now fully ready for Plan 03 (`122-03-PLAN.md`, wave 2, depends on 122-01): it adds the "Ver Relatório" link to `/plataforma`'s `CardHeader` plus a source-level verify script. This backend plan required nothing from Plan 03 and blocks nothing for it — `GET /api/v1/platform/tenants` needed zero changes and now has one additional regression test protecting the exact property (suspended-tenant visibility) that ROADMAP Success Criterion 3 depends on.
- `UTIL-01` remains open, as designed: it only closes once the report screen is genuinely reachable and live-verified (Plan 03's link-wiring plus Plan 04's live UAT), not from this backend-only proof step.
- No blockers or concerns raised by this plan.

---
*Phase: 122-relat-rio-de-utiliza-o-por-tenant*
*Completed: 2026-07-30*

## Self-Check: PASSED

- FOUND: `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java` exists on disk
- FOUND: `listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta` present in that file
- FOUND: this SUMMARY file (`122-02-SUMMARY.md`) exists on disk
- FOUND: commit `3144d6d` (task commit) in `git log --oneline --all`
- FOUND: commit `301fe2d0` (metadata commit: SUMMARY.md + STATE.md + ROADMAP.md) in `git log --oneline --all`
- Re-verified acceptance criteria: `mvn test -Dtest=PlatformAdminControllerTest` (27 tests, 0 failures/errors), full `mvn test` (183 tests, 0 failures/errors), `mvn spotbugs:check` (0 bugs, 0 errors) — all green as reported above
- `git diff --name-only 3144d6d~1 3144d6d` lists exactly one file: `backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java`
