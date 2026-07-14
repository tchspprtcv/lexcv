---
phase: 94-notf-27-corrigir-colis-o-de-dedup-admin
plan: 01
subsystem: api
tags: [spring-boot, jpa, notifications, dedup, transactional-safety]

# Dependency graph
requires:
  - phase: 88-alertas-di-rios-prazos-e-honor-rios
    provides: "uk_notificacao_dedup unique constraint (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria) that this bug collided against"
  - phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
    provides: "current NotificacaoService.criar() shape (mute-guard, Optional<Notificacao> return) that this plan builds directly on top of"
provides:
  - "criarComFanOutAdmin private helper in NotificacaoService merging primary recipient(s) + ADMIN fan-out into one deduplicated LinkedHashSet<UUID> before the creation loop"
  - "DataIntegrityViolationException backstop catch per-recipient, mirroring AlertasDiariosJob.notificar()'s existing defense-in-depth pattern"
  - "Collision-safe notificarFaseEntrada/notificarProcessoAtribuido/notificarDocumentoNovo/notificarParecerAtribuido with unchanged public signatures"
affects: [95-notf-25-notificar-equipa-alargada]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Merge-before-write dedup: build the full deduplicated recipient set (primary + ADMIN, minus excluded actor) BEFORE any persistence call, instead of two independent write passes that can target the same tuple"
    - "Per-recipient try/catch isolation extended to a second exception type (DataIntegrityViolationException) alongside the existing IllegalArgumentException isolation, so one failure never blocks the rest of the fan-out or propagates to the caller's transaction"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/services/NotificacaoService.java
    - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java

key-decisions:
  - "Single shared private helper (criarComFanOutAdmin) rather than duplicating the merge logic across the 4 notificar* methods -- keeps the dedup invariant in exactly one place for Phase 95 to build on"
  - "excluirUserId (actor) filtered out of both the primary set and the ADMIN set before the union, preserving the pre-existing actor-exclusion behavior for DOCUMENTO_NOVO/PARECER_ATRIBUIDO"
  - "Removed both notificarAdmins(...) overloads entirely (no longer called anywhere) rather than leaving them as dead code, per plan instruction"

patterns-established:
  - "Recipient-set merge before write: any future notificar* addition should merge all recipient sources into one LinkedHashSet<UUID> and iterate once, never call criar() twice for the same event across separate recipient-resolution passes"

requirements-completed: [NOTF-27]

# Metrics
duration: ~20min
completed: 2026-07-14
---

# Phase 94 Plan 01: Corrigir Colisão de Dedup ADMIN Summary

**Merged primary-recipient + ADMIN fan-out into a single deduplicated LinkedHashSet<UUID> in NotificacaoService, eliminating the uk_notificacao_dedup collision that threw an uncaught DataIntegrityViolationException whenever a primary recipient was also ADMIN of the same tenant.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-14T11:33:30-01:00 (after fast-forwarding the worktree branch to master)
- **Completed:** 2026-07-14T11:50:57-01:00
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- Root-caused, reproduced (RED), and fixed the NOTF-27 dedup collision bug across all 4 `notificar*` trigger methods (`notificarFaseEntrada`, `notificarProcessoAtribuido`, `notificarDocumentoNovo`, `notificarParecerAtribuido`)
- New private helper `criarComFanOutAdmin` is now the single choke point where primary recipient(s) and ADMIN fan-out are merged into one `LinkedHashSet<UUID>` before any `criar()` call, so a person who is both the primary recipient and ADMIN receives exactly one notification row instead of two colliding writes
- Added a `catch (DataIntegrityViolationException)` backstop per-recipient (defense-in-depth against a residual concurrent race), mirroring the existing pattern in `AlertasDiariosJob.notificar()`
- All 4 public `notificar*` signatures are unchanged — the 7 production call sites in `ResourceController`/`ParecerController` compile without any modification
- Removed the now-dead `notificarAdmins(...)` overloads (7-arg and 8-arg) and their two direct-call tests; their prior coverage (fan-out-per-admin, actor-exclusion) is preserved by the existing trigger-level tests

## Task Commits

Each task was committed atomically:

1. **Task 1: Testes de regressão de colisão primário==ADMIN (RED)** - `544a43d` (test)
2. **Task 2: Fundir primário+ADMIN num LinkedHashSet único + backstop DIV (GREEN)** - `f30d941` (fix)

_TDD flow: RED confirmed (33 tests run, 5 new failures, 0 preexisting regressions) before GREEN (31 tests run — 2 obsolete direct-call tests removed — 0 failures)._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java` - Added `criarComFanOutAdmin` helper (merge + single-pass creation loop with `IllegalArgumentException`/`DataIntegrityViolationException` isolation); rewrote the 4 `notificar*` methods to delegate to it; removed both `notificarAdmins(...)` overloads and the obsolete "sem dedup"/"recebe 2 linhas" comments
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` - Added 5 regression tests (4 primary==ADMIN collision tests, 1 `DataIntegrityViolationException` backstop test); removed 2 tests that called the now-removed `notificarAdmins(...)` directly

## Decisions Made
- Single shared private helper instead of duplicating merge logic per trigger method — keeps the dedup invariant centralized for Phase 95 (NOTF-25, team-wide fan-out) to build directly on top of, per 94-CONTEXT.md's stated purpose for this phase.
- `excluirUserId` (the acting user) is filtered out of both the primary and ADMIN sets before the union is built, so `notificarDocumentoNovo`/`notificarParecerAtribuido` continue to never notify the actor of their own action, even when the actor is also ADMIN.
- No new tests were added to cover "actor also ADMIN AND actor excluded" as a combined scenario beyond what the 5 new regression tests + existing `..._adminIgualAoAtor_...`/`..._advogadoIgualAoAtor_...` tests already prove independently — this was implicit in the plan's task scope, not a gap.

## Deviations from Plan

None — plan executed exactly as written. Both tasks' acceptance criteria were verified exactly as specified:
- Task 1: `mvn -q test-compile` exit 0; `grep -c "TambemAdmin"` = 4; `grep -c "DataIntegrityViolationException"` = 3; RED confirmed (33 tests run, 5 failures, all new, 0 preexisting regressions).
- Task 2: `mvn test -Dtest=NotificacaoServiceTest` exit 0 (31/31 green); `mvn -q compile` exit 0; `criarComFanOutAdmin(` count = 5; `void notificarAdmins(` count = 0; `catch (DataIntegrityViolationException` count = 1; `recebe 2 linhas` count = 0; `sem dedup entre` count = 0; `git diff --stat` touches exactly `NotificacaoService.java` and `NotificacaoServiceTest.java` — `AlertasDiariosJob.java` untouched.

## Issues Encountered

The worktree branch (`worktree-agent-a0b93168285cca173`) was behind `master` at spawn time — `.planning/phases/LEXCV-94-.../94-01-PLAN.md` did not yet exist on this branch, and `git merge-base --is-ancestor HEAD master` confirmed the worktree HEAD was a strict ancestor of `master` (no divergent local commits). Fast-forwarded the worktree branch to `master` (`git merge --ff-only master`) before reading the plan, per the parallel-execution note in this task's instructions. No conflicts; working tree was clean beforehand.

## Next Phase Readiness

- NOTF-27 is closed: the dedup collision is eliminated by construction (merged set before write), with a backstop against residual concurrent races. This unblocks Phase 95 (NOTF-25, team-wide fan-out) — its expanded recipient pool can be layered on top of `criarComFanOutAdmin`'s already-deduplicated set instead of the previous uncoordinated two-pass pattern.
- Full backend suite (`mvn test`) is green: 55/55 (`AlertasDiariosJobTest` 9/9, `NotificacaoServiceTest` 31/31, `RiscoPrazoServiceTest` 15/15). Note: the `*IT.java` Testcontainers-backed integration tests (`NotificacaoRepositoryIT`, `NotificacaoPreferenciaRepositoryIT`, `ParecerVersaoConcorrenciaIT`) are Failsafe-scoped and do not run under `mvn test` — this is the project's existing Surefire/Failsafe split, not a gap introduced by this plan.
- No blockers.

---
*Phase: 94-notf-27-corrigir-colis-o-de-dedup-admin*
*Completed: 2026-07-14*

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/services/NotificacaoService.java
- FOUND: backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java
- FOUND: commit 544a43d (test: RED)
- FOUND: commit f30d941 (fix: GREEN)
