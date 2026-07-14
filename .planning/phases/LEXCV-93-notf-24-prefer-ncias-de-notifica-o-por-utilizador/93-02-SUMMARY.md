---
phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
plan: 02
subsystem: api
tags: [spring-boot, notifications, mute-guard, tenant-isolation, mockito]

# Dependency graph
requires:
  - phase: 93-01
    provides: "NotificacaoPreferencia entity, CategoriaNotificacao enum, NotificacaoPreferenciaRepository (3 dual-scoped derived methods)"
provides:
  - "NotificacaoService.criar(...) now returns Optional<Notificacao> and mutes at the single write choke point"
  - "NotificacaoService.silenciarCategoria/reativarCategoria/listarCategoriasSilenciadas service methods"
affects: [93-03, 93-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single choke-point guard clause: mute check lives inside criar(), never duplicated across the 5 notificar* trigger methods or the daily job"
    - "Optional<T> as a third result distinguishing 'muted, nothing persisted' from both the exception path (invalid recipient) and a null"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/services/NotificacaoService.java
    - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java

key-decisions:
  - "Mute guard placed inside criar() only, after existing validations and before the Notificacao builder — AlertasDiariosJob.java was not touched, since it calls criar() directly and inherits the guard automatically"
  - "criar() return type changed from Notificacao to Optional<Notificacao>; verified via grep that no caller (5 notificar* methods, AlertasDiariosJob, all test call sites) reads the return value, making the signature change safe and contained to this file"
  - "isSilenciavelCategoria(categoria) is checked before the preference existence query, so PRAZO_VENCIDO never even queries the preference table and is always delivered (defense-in-depth per Success Criterion 2)"

patterns-established:
  - "Any future per-category behavior gate belongs inside criar(), not in the trigger methods, to guarantee AlertasDiariosJob inherits it by construction"

requirements-completed: [NOTF-24]

# Metrics
duration: ~15min
completed: 2026-07-14
---

# Phase 93 Plan 02: NOTF-24 Mute Guard + Preference Service Methods Summary

**Mute guard inserted inside `NotificacaoService.criar(...)` (the sole write choke point) plus 3 new preference service methods (`silenciarCategoria`/`reativarCategoria`/`listarCategoriasSilenciadas`), with 29 passing Mockito tests proving the daily job inherits the mute by construction**

## Performance

- **Duration:** ~15 min
- **Completed:** 2026-07-14T10:38:16Z
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- `criar(...)` now short-circuits to `Optional.empty()` (no `save()` call) when the category is `isSilenciavelCategoria(...)` AND a `NotificacaoPreferencia` row exists for `(tenantId, destinatarioId, categoria)` — verified this is the exact code path `AlertasDiariosJob.notificar()` exercises (direct call to `criar()`), proving Success Criterion 3 (daily job respects mutes) without modifying `AlertasDiariosJob.java` at all
- `PRAZO_VENCIDO` always persists: `isSilenciavelCategoria` is checked first and short-circuits before any preference lookup, so even a stray preference row for `PRAZO_VENCIDO` cannot suppress it (Success Criterion 2)
- Added `silenciarCategoria` (validates via `CategoriaNotificacao.fromString`, rejects unknown categories and `PRAZO_VENCIDO`, idempotent insert), `reativarCategoria` (`@Transactional`, idempotent derived delete), and `listarCategoriasSilenciadas` (maps preference rows to category strings) — ready for Plan 93-03's endpoints to delegate to
- Migrated all 20 existing `new NotificacaoService(notificacaoRepository, userRepository)` test call sites to the 3-arg constructor and added 9 new tests; full suite (29 tests) passes with zero regressions

## Task Commits

Each task was committed atomically:

1. **Task 1: Mute guard em criar() (Optional) + 3 métodos de preferência** - `68415d2` (feat)
2. **Task 2: Migrar os 20 construtores do teste + testes de mute/preferência** - `38d8aa7` (test)

_Note: no plan-metadata commit is included here — the orchestrator commits STATE.md/ROADMAP.md separately after this SUMMARY lands._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java` - `criar()` returns `Optional<Notificacao>` with the mute guard; added `silenciarCategoria`/`reativarCategoria`/`listarCategoriasSilenciadas`
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` - 20 constructors migrated to 3 args (added `@Mock NotificacaoPreferenciaRepository`); 9 new tests covering mute guard, PRAZO_VENCIDO non-silenciability, and the 3 preference methods

## Decisions Made
None beyond what was already locked in 93-CONTEXT.md and the plan itself — executed as specified:
- Guard lives exclusively inside `criar()`, never duplicated in the 5 `notificar*` trigger methods
- `AlertasDiariosJob.java` was not modified — confirmed via `git diff --stat` across both task commits that the file has zero changes
- Existing `try/catch (IllegalArgumentException)` blocks in the trigger methods were left untouched — an `Optional.empty()` return is not an exception, callers simply proceed as statements (they never read the return value)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Worktree behind master:** This worktree's branch (`worktree-agent-a1b56b1ec26c35bb6`) was 90 commits behind `master` at the start of execution and did not yet contain Plan 93-01's `NotificacaoPreferencia`/`CategoriaNotificacao`/`NotificacaoPreferenciaRepository` artifacts (the phase directory itself was missing from the worktree). Verified with `git merge-base --is-ancestor HEAD master` that a fast-forward was safe (worktree HEAD was a strict ancestor of master, zero commits ahead), then ran `git merge --ff-only master` before reading any plan files. This is not a plan deviation — it is the documented pre-condition from the execution prompt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 93-03 (preferences endpoints) can now delegate directly to `NotificacaoService.silenciarCategoria`/`reativarCategoria`/`listarCategoriasSilenciadas` — all three are compiled, tested, and follow the existing `@Transactional` conventions of the service
- Plan 93-04 (frontend UI) can rely on the backend contract being stable: category strings in, category strings out, `IllegalArgumentException` for invalid/non-silenciable categories
- No blockers. `cd backend && mvn -q -DskipTests compile` succeeds; `cd backend && mvn -q test -Dtest=NotificacaoServiceTest` passes 29/29.

---
*Phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador*
*Completed: 2026-07-14*

## Self-Check: PASSED

All modified files confirmed on disk (`NotificacaoService.java`, `NotificacaoServiceTest.java`); both task commits (`68415d2`, `38d8aa7`) confirmed in git log; `93-02-SUMMARY.md` confirmed on disk.
