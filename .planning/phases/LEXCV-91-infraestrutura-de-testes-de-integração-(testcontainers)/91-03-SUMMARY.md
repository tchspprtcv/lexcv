---
phase: 91-infraestrutura-de-testes-de-integracao
plan: 03
subsystem: infra
tags: [github-actions, ci-cd, maven-failsafe, spotbugs, testcontainers]

# Dependency graph
requires:
  - phase: 91-01
    provides: "Testcontainers PostgreSQL integration-test infra (deps, failsafe binding, shared test properties) that mvn verify now exercises in CI"
  - phase: 91-02
    provides: "ParecerVersaoConcorrenciaIT, the second *IT class now protected by the CI test gate"
provides:
  - "A `test` job in .github/workflows/deploy.yml (mvn -B verify + mvn -B spotbugs:check on ubuntu-latest) gating build-and-push via needs: test"
  - "TEST-03 decision recorded and closed in PROJECT.md Key Decisions and STATE.md (Decisions, Pending Todos, Deferred Items)"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: ["CI quality gate: needs: <job> to block image build/push on a prior job's success"]

key-files:
  created: []
  modified:
    - .github/workflows/deploy.yml
    - .planning/PROJECT.md
    - .planning/STATE.md

key-decisions:
  - "CI test job scoped to contents: read only (no packages: write) — only build-and-push needs registry write, minimizing blast radius of a compromised test step"
  - "OWASP dependency-check:check deliberately NOT added to CI this milestone (NVD dataset download cost/API-key requirement, out of TEST-03's literal scope) — documented via inline YAML comment and in both PROJECT.md/STATE.md"

patterns-established:
  - "New CI job id `test` runs before `build-and-push`, which declares `needs: test` — first quality-gate pattern in this repo's CI"

requirements-completed: [TEST-03]

# Metrics
duration: 3min
completed: 2026-07-13
---

# Phase 91 Plan 03: CI Test/SAST Gate Summary

**`.github/workflows/deploy.yml` gains a `test` job (`mvn -B verify` + `mvn -B spotbugs:check` on `ubuntu-latest`) that gates `build-and-push` via `needs: test`, closing TEST-03 by both implementing the CI gate and recording the decision in PROJECT.md/STATE.md — including resolving all three stale H2/Testcontainers-gap references left over from before Phase 91.**

## Performance

- **Duration:** ~3 min
- **Started:** 2026-07-13T15:40:46Z (after fast-forwarding the worktree branch to master, which brought in 91-01/91-02's completed work)
- **Completed:** 2026-07-13T15:43:50Z
- **Tasks:** 2/2 completed
- **Files modified:** 3

## Accomplishments
- Added a new `test` job to `.github/workflows/deploy.yml`: `runs-on: ubuntu-latest`, `permissions: contents: read` only, steps `actions/checkout@v4` → `actions/setup-java@v4` (temurin, JDK 23, `cache: maven`) → `mvn -B verify` (working-directory `backend`, runs surefire unit tests + failsafe `*IT` Testcontainers integration tests using the runner's built-in Docker daemon) → `mvn -B spotbugs:check` (working-directory `backend`)
- `build-and-push` now declares `needs: test` — a failing test or SpotBugs finding blocks image build/push; its existing `permissions`, caching, GHCR login, and both `docker/build-push-action` steps were left untouched
- Left an inline YAML comment documenting the deliberate deferral of OWASP `dependency-check:check` (NVD dataset download cost, out of TEST-03 scope)
- Recorded the TEST-03 decision in `.planning/PROJECT.md` Key Decisions (new row, tagged `v2.11, Phase 91, TEST-03`) stating both the affirmative decision and the dependency-check deferral
- Updated `.planning/STATE.md` surgically in three places: added a `(v2.11 TEST-03)` tagged one-line Decisions entry; closed the Pending Todos H2/Testcontainers bullet (struck through, marked CLOSED with the concrete test-class evidence); updated both the `tooling` Deferred Items row and the Phase 86 `verification_gap` row to reflect that Testcontainers infra now exists and `NotificacaoRepositoryIT` proved `buscarPorFiltros` against real Postgres (Phase 86's row now correctly narrows the remaining gap to just the RBAC visual confirmation)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add a gating test/SAST job to the CI workflow** - `93203a1` (feat)
2. **Task 2: Record the TEST-03 CI decision in PROJECT.md and STATE.md** - `5142386` (docs)

## Files Created/Modified
- `.github/workflows/deploy.yml` - new `test` job (mvn verify + spotbugs:check), `build-and-push` gated via `needs: test`
- `.planning/PROJECT.md` - new Key Decision row recording TEST-03
- `.planning/STATE.md` - Decisions entry, Pending Todos closure, two Deferred Items rows updated

## Decisions Made
- Followed the plan's exact job/step structure (setup-java's built-in `cache: maven` instead of a manual `actions/cache@v4` step, matching the plan's instruction to use setup-java's built-in cache for the test job specifically — distinct from build-and-push's existing manual Maven cache step, which was left unchanged).
- Chose to strike through (`~~...~~`) the closed Pending Todos bullet rather than delete it outright, preserving a visible trail that the item was tracked and resolved, consistent with how the plan's acceptance criteria asked for "surgical" edits rather than removal.

## Deviations from Plan

None — plan executed exactly as written. Both tasks' acceptance criteria were verified directly (grep confirmed the `test:` job key, `needs: test`, `spotbugs:check`, `mvn -B verify` tokens all present; `TEST-03` greppable in both PROJECT.md and STATE.md; `git diff --stat` confirmed both STATE.md/PROJECT.md edits were additive/surgical with no unrelated content rewritten).

## Issues Encountered

None for the plan's own tasks. Note (workflow-level, not a plan defect): this worktree's branch was found stale at session start — several commits behind local `master`, missing the phase 91 plan files and 91-01/91-02's completed work entirely. Resolved via a verified-safe `git merge --ff-only master` (confirmed `git merge-base --is-ancestor HEAD master` first) before any task work began, per the plan-execution instructions' explicit guidance for this scenario.

## Known Stubs

None.

## Threat Flags

None — this plan only adds a CI job (no runtime code, no new endpoints/auth paths/schema) and documentation edits. Matches the plan's own threat model: T-91-04 (tampering via new CI job) mitigated by pinned action versions (`checkout@v4`, `setup-java@v4`) and no new secrets; T-91-05 (quality-gate bypass) mitigated by `needs: test`; T-91-06 (information disclosure via CI permissions) mitigated by scoping the `test` job to `contents: read` only, no `packages: write`.

## User Setup Required

None. The new `test` job requires no new secrets or manual configuration — it reuses the same `ubuntu-latest` runner and Docker daemon already available to `build-and-push`, and Testcontainers pulls `postgres:16-alpine` automatically at run time (same as any other public image pull already happening in this workflow, e.g. `docker/build-push-action`).

## Next Phase Readiness

- TEST-01, TEST-02, and TEST-03 (the entirety of this phase's requirement scope) are now closed: infra + two integration tests (91-01, 91-02) + the CI gate that runs and protects them (91-03).
- The next push to `master` will be the first real-world confirmation that `mvn -B verify` (including both new `*IT` classes) and `mvn -B spotbugs:check` pass on a standard Linux Docker daemon — resolving the live-verification gap that both 91-01-SUMMARY.md and 91-02-SUMMARY.md deferred to this plan, since this sandbox's Docker Desktop npipe transport could not run Testcontainers locally.
- No blockers for subsequent v2.11 phases (92 onward) — this plan touched only `.github/workflows/deploy.yml`, `.planning/PROJECT.md`, and `.planning/STATE.md`, with zero file overlap with Phases 92-97.

---
*Phase: 91-infraestrutura-de-testes-de-integracao*
*Completed: 2026-07-13*

## Self-Check: PASSED

- FOUND: .github/workflows/deploy.yml
- FOUND: .planning/phases/LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)/91-03-SUMMARY.md
- FOUND commit: 93203a1 (Task 1)
- FOUND commit: 5142386 (Task 2)
