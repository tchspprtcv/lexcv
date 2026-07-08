---
phase: 86-infraestrutura-de-notificacoes-entidade-api-e-targeting
plan: 2
subsystem: api
tags: [spring-boot, mockito, junit5, notifications, rbac]

# Dependency graph
requires:
  - phase: 86-01
    provides: "Notificacao entity (@Builder, lida defaults false) + NotificacaoRepository (findByIdAndTenantIdAndDestinatarioId, findByTenantIdAndDestinatarioIdAndLidaFalse, buscarPorFiltros, countByTenantIdAndDestinatarioIdAndLidaFalse) + manual migration script"
provides:
  - "NotificacaoService — sole write choke point for Notificacao: criar(...) for creation, marcarLida(...)/marcarTodasLidas(...) for status mutation"
  - "notificarAdmins(...) package-private ADMIN fan-out helper (one independent row per current ADMIN of the tenant)"
  - "NotificacaoServiceTest — first Mockito test in the backend, 5 passing tests proving per-recipient row independence, ADMIN fan-out, and scoped mark-read mutation"
affects: [86-03, 87, 88]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "First Mockito usage in the backend (@ExtendWith(MockitoExtension.class), @Mock repositories) — no H2/Testcontainers exists, so service logic is unit-tested against mocked repositories instead of a real database"
    - "Service-layer @Transactional for compound find-then-mutate-then-save methods (marcarLida/marcarTodasLidas), mirroring SetupService.initializeSystem's existing precedent instead of putting @Transactional on the controller"
    - "Load-mutate-saveAll instead of a bulk @Modifying update (marcarTodasLidas) — keeps the codebase's zero-@Modifying convention intact"

key-files:
  created:
    - backend/src/main/java/com/lexcv/services/NotificacaoService.java
    - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java
  modified: []

key-decisions:
  - "notificarAdmins(...) kept package-private (no public notificar* real-trigger methods added) — those are explicitly deferred to Phase 87 per the plan; this phase only proves the fan-out mechanism works"
  - "marcarLida(...) returns Optional.empty() (never calls save) when the finder returns nothing, rather than throwing — lets Plan 86-03's controller answer 404 without any exception-based control flow"

patterns-established:
  - "NotificacaoService.criar/marcarLida/marcarTodasLidas are the ONLY notificacaoRepository.save(...)/saveAll(...) call sites in the codebase (verified via grep) — Plan 86-03's controller must call these service methods, never the repository directly"

requirements-completed: [NOTF-14]

# Metrics
duration: 6min
completed: 2026-07-08
---

# Phase 86 Plan 2: NotificacaoService Write Choke Point + ADMIN Fan-Out Summary

**NotificacaoService centralizing all Notificacao writes (criar/marcarLida/marcarTodasLidas) with ADMIN fan-out, proven by 5 Mockito tests — the backend's first use of Mockito.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-07-08T19:49:52-01:00 (first commit after wave-1 tracking update)
- **Completed:** 2026-07-08T19:55:25-01:00
- **Tasks:** 1 completed (TDD: RED + GREEN)
- **Files modified:** 2 (both newly created)

## Accomplishments
- `NotificacaoService.criar(...)` is now the sole entry point that persists a new `Notificacao` row — builds the entity from 8 args and calls `notificacaoRepository.save(...)` exactly once.
- `notificarAdmins(...)` fans out to every current ADMIN of the tenant via the existing `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")`, calling `criar(...)` once per admin — one independent row per ADMIN, never a shared row with a visibility flag (NOTF-14).
- `marcarLida(...)`/`marcarTodasLidas(...)` centralize status mutation too (not just creation) — both `@Transactional`, both scoped by tenant+destinatario via Plan 86-01's finders, so `NotificacaoService` is the sole write path for the entire notification lifecycle.
- `NotificacaoServiceTest` — the first Mockito test in this backend — proves automatically: (1) two distinct recipients get independent rows via `ArgumentCaptor`, (2) fan-out produces exactly `times(2)` saves for 2 stubbed admins each with `lida == false`, (3) `marcarLida` mutates and saves exactly once when the row belongs to the caller, (4) `marcarLida` returns empty and never calls save for a foreign/non-existent row, (5) `marcarTodasLidas` calls `saveAll` exactly once with every element's `lida == true`.

## Task Commits

Each task was committed atomically following TDD RED/GREEN:

1. **Task 1 (RED): failing test for NotificacaoService** - `84f96c6` (test) — compilation fails as expected (`NotificacaoService` did not exist yet)
2. **Task 1 (GREEN): implement NotificacaoService** - `625e935` (feat) — all 5 tests pass

_No REFACTOR commit was needed — the GREEN implementation matched the plan's specified shape with no cleanup required._

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` - 5 Mockito tests covering `criar` dual-recipient independence, ADMIN fan-out, `marcarLida` (found + not-found), `marcarTodasLidas`
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java` - `@Service`/`@RequiredArgsConstructor`; public `criar`/`marcarLida`/`marcarTodasLidas`, package-private `notificarAdmins`

## Decisions Made
None beyond what the plan specified — the plan's `<action>` block fully determined method signatures, access modifiers, and test structure. Followed as written.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. `mvn -q test -Dtest=NotificacaoServiceTest` reported `Tests run: 5, Failures: 0, Errors: 0` on the first GREEN attempt. A full `mvn -q test` run (both `NotificacaoServiceTest` and `RiscoPrazoServiceTest`, the only 2 test classes in the backend) confirmed zero regressions (5/5 and 15/15 respectively).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 86-03 (`NotificacaoController`) can now call `NotificacaoService.marcarLida(...)`/`marcarTodasLidas(...)` instead of touching `NotificacaoRepository.save`/`saveAll` directly, per this plan's stated contract.
- Phase 87/88 have a tested `notificarAdmins(...)` fan-out helper ready to be reused once real business triggers (fase entrada, documento novo, atribuição, parecer, prazos/honorários) are added — no public `notificar*` trigger methods exist yet, by design.
- No blockers.

---
*Phase: 86-infraestrutura-de-notificacoes-entidade-api-e-targeting*
*Completed: 2026-07-08*
