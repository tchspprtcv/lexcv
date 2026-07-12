---
phase: 90-spotbugs-sast-commit-e-verifica-o
plan: 01
subsystem: infra
tags: [spotbugs, findsecbugs, sast, maven, jdk23, security]

# Dependency graph
requires: []
provides:
  - "mvn spotbugs:check green against JDK 23 bytecode (spotbugs-maven-plugin 4.10.2.0 + findsecbugs-plugin 1.14.0)"
  - "backend/spotbugs-exclude.xml as a reviewed, git-tracked suppression file"
  - "setId(null) mass-assignment/IDOR fix on ResourceController's 9 create endpoints"
  - "EI_EXPOSE_REP fix on UserPrincipal.getAuthorities()"
  - "Defensive List.copyOf() in ConflictCheckResponse and WorkflowResponse compact constructors"
affects: [91-testcontainers-integration-test-infra, 97-cross-cutting-milestone-audit]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "FindBugsFilter <Match> suppressions require a per-finding justifying comment (class/method/bug pattern/why-safe), never a blanket suppression"
    - "setId(null) before first save() in create endpoints, to force Hibernate persist() over merge() and close a client-suppliable-id mass-assignment/IDOR gap"

key-files:
  created:
    - backend/spotbugs-exclude.xml
  modified:
    - backend/pom.xml
    - backend/src/main/java/com/lexcv/config/UserPrincipal.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java
    - backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java

key-decisions:
  - "Fast-forwarded the executor worktree branch to master before starting — the worktree was checked out at a stale commit (eed883e) that predated the phase 90 planning commits (cacb031, f250f88), which is a clean ancestor fast-forward, not a merge, so no local worktree work was at risk."
  - "The plan's uncommitted SAST deliverable (pom.xml bumps, spotbugs-exclude.xml, 4 defensive source fixes) physically existed only in the main repo's working directory, not in this linked worktree's working directory (git worktrees do not share uncommitted changes). Reconstructed the exact same diff inside the worktree by reading the main repo's `git diff`/untracked file content and applying identical edits, byte-for-byte verified against the main repo before running spotbugs:check."
  - "No source fixes were needed in Task 1 — `mvn -DskipTests compile spotbugs:check` reported BUILD SUCCESS with zero findings on the first run, confirming the pre-existing fix set (already applied to source, just uncommitted) remains valid against the current tree."

patterns-established:
  - "SAST suppression file (spotbugs-exclude.xml) requires individual per-finding review and a justifying header comment naming class/method/bug-pattern before any <Match> entry is added"

requirements-completed: [SAST-01]

# Metrics
duration: ~15min
completed: 2026-07-12
---

# Phase 90 Plan 01: SpotBugs/SAST Commit e Verificação Summary

**Reconfirmed `mvn spotbugs:check` passes clean against JDK 23 bytecode (spotbugs-maven-plugin 4.10.2.0 + findsecbugs-plugin 1.14.0) and landed the full SAST fix — version bumps, reviewed exclusion file, and 4 defensive source fixes (EI_EXPOSE_REP, 2x record defensive-copy, 9x setId(null) mass-assignment/IDOR close) — as a single commit.**

## Performance

- **Duration:** ~15 min
- **Tasks:** 2 completed
- **Files modified:** 6 (5 modified, 1 created)

## Accomplishments
- `mvn -DskipTests compile spotbugs:check` runs BUILD SUCCESS with zero unsuppressed findings against JDK 23 bytecode (Success Criterion 1) — no source changes were required beyond what was already reviewed and staged in a prior session.
- All 6 SAST deliverable files (backend/pom.xml, backend/spotbugs-exclude.xml, and the 4 defensive-fix source files) landed in a single git commit, closing SAST-01.
- Working tree left clean of these 6 files post-commit; CI wiring (`.github/workflows/deploy.yml`) was correctly left untouched, deferred to Phase 91 (TEST-03).

## Task Commits

Each task was committed atomically:

1. **Task 1: Reconfirm mvn spotbugs:check passes clean** - no commit (verification-only; BUILD SUCCESS on first run, no source changes needed)
2. **Task 2: Commit the SpotBugs/SAST deliverable as one coherent commit** - `158b7c3` (chore)

## Files Created/Modified
- `backend/pom.xml` - spotbugs-maven-plugin 4.8.3.1 -> 4.10.2.0, findsecbugs-plugin 1.13.0 -> 1.14.0, `excludeFilterFile` wired to spotbugs-exclude.xml
- `backend/spotbugs-exclude.xml` (new) - Reviewed FindBugsFilter suppressions for ENTITY_MASS_ASSIGNMENT (safe endpoints) and NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE (ParecerVersao.getId())
- `backend/src/main/java/com/lexcv/config/UserPrincipal.java` - `getAuthorities()` now returns `Collections.unmodifiableCollection(authorities)` (EI_EXPOSE_REP fix)
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - `setId(null)` before first `save()` in 9 create endpoints (createCliente, createProcesso, createProcessoIntake, createParte, createMovimentacao, createFacto, createEvento, createHonorario, createPagamento); default-branch warning log added to the FORMALIZAR switch; two previously-swallowed `catch (Exception ignored)` blocks in pagamento create/delete now log via `log.warn`
- `backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java` - compact constructor defensive-copies `matches` via `List.copyOf`
- `backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java` - compact constructor defensive-copies `transicoesDisponiveis` via `List.copyOf`

## Decisions Made
- Treated this strictly as "verify + commit already-reviewed work," per the plan's explicit framing — no re-engineering, no new SAST tooling, no scope creep into CI wiring (Phase 91's TEST-03).
- Because the linked worktree's working directory did not contain the uncommitted deliverable (it lived only in the main repo's working tree — worktrees don't share uncommitted state), reconstructed the identical diff inside the worktree from the main repo's `git diff` output and untracked file content, and confirmed byte-for-byte parity (`diff` against the deliverable diff file, ignoring blob-hash `index` lines) before running any verification. This is not a re-engineering deviation — it reproduces exactly the same reviewed change described in the plan's `<facts>` block.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree branch was stale, missing the phase 90 planning commits**
- **Found during:** Setup, before Task 1
- **Issue:** The worktree branch (`worktree-agent-a65035d144e440bbd`) was at commit `eed883e`, which predates the phase 90 planning commits on `master` (`cacb031` smart-discuss context, `f250f88` plan creation). `.planning/phases/LEXCV-90-spotbugs-sast-commit-e-verifica-o/90-01-PLAN.md` did not exist in the worktree.
- **Fix:** Verified `eed883e` is a clean ancestor of `master` (`git merge-base --is-ancestor`), then fast-forwarded (`git merge --ff-only master`) — a safe, lossless update since no local worktree commits existed beyond that ancestor point.
- **Files modified:** None (branch pointer update only, no working-tree edits from the merge beyond the incoming planning docs).
- **Verification:** `git log --oneline -3` showed `f250f88` at HEAD; `90-01-PLAN.md` and `90-CONTEXT.md` present after the fast-forward.

**2. [Rule 3 - Blocking] Plan's described uncommitted deliverable existed only in the main repo's working directory, not in the worktree**
- **Found during:** Setup, before Task 1
- **Issue:** `git status --short` in the worktree was empty even after the fast-forward, contradicting the plan's `<facts>` block (which described `M backend/pom.xml`, `?? backend/spotbugs-exclude.xml`, etc.). The actual uncommitted changes were confirmed present in the main repo's working directory (same HEAD commit, `f250f88`) via a separate `git status --short` there.
- **Fix:** Extracted the exact diff (`git diff`) for the 5 modified files and the full content of the untracked `spotbugs-exclude.xml` from the main repo, then applied identical edits inside the worktree using Edit/Write. Verified parity with `diff` against the exported diff (ignoring blob-hash `index:` lines) before proceeding — confirmed identical.
- **Files modified:** backend/pom.xml, backend/spotbugs-exclude.xml, backend/src/main/java/com/lexcv/config/UserPrincipal.java, backend/src/main/java/com/lexcv/controllers/ResourceController.java, backend/src/main/java/com/lexcv/dtos/ConflictCheckResponse.java, backend/src/main/java/com/lexcv/dtos/WorkflowResponse.java
- **Verification:** `diff` of exported main-repo diff vs. worktree diff (ignoring `index` lines) reported no differences; `diff` of `spotbugs-exclude.xml` content between main repo and worktree reported "Files are identical."
- **Committed in:** `158b7c3` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking, both setup/infrastructure issues required to make the plan's tasks executable in this isolated worktree; no change to the reviewed fix content itself, no scope creep).
**Impact on plan:** Zero impact on the deliverable's content — the same exact reviewed diff described in the plan was landed. Both deviations were necessary preconditions for running Task 1/Task 2 inside a git worktree whose working directory is isolated from the main repo's uncommitted state.

## Issues Encountered

The `git show --stat --name-only HEAD | grep -E "..."  | wc -l` command specified in Task 2's `<verify>` block returned 8 instead of the expected 6, because the grep pattern also matches file-path-shaped substrings inside the commit message body itself (e.g. the literal text "(backend/pom.xml)" and "ResourceController" appear in the commit message prose). Verified independently via `git show --name-only HEAD` (plain, no `--stat`) which lists exactly the 6 unique deliverable paths with no duplication or extra matches — the underlying acceptance criterion (all 6 deliverable paths present, no scope creep) is fully satisfied; the discrepancy is a false-positive artifact of the verify script's grep pattern against free-text commit body content, not a defect in the commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- SAST-01 tech debt closed: `mvn spotbugs:check` runs clean on JDK 23, all 6 deliverable files committed (`158b7c3`), working tree clean of them.
- CI wiring of `mvn test`/`mvn spotbugs:check` into `.github/workflows/deploy.yml` remains explicitly deferred to Phase 91 (TEST-03) — untouched here, confirmed via `git status --porcelain .github/workflows/deploy.yml` returning empty both before and after this plan's commit.
- No blockers for Phase 91 (Testcontainers) or Phase 92 (Agenda/RiscoPrazoService) — both were already noted as mutually parallelizable with this phase (zero file overlap).

---
*Phase: 90-spotbugs-sast-commit-e-verifica-o*
*Completed: 2026-07-12*

## Self-Check: PASSED

- FOUND: backend/spotbugs-exclude.xml
- FOUND: .planning/phases/LEXCV-90-spotbugs-sast-commit-e-verifica-o/90-01-SUMMARY.md
- FOUND: 158b7c3 (git log --oneline --all)
