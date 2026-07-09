---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
plan: 02
subsystem: backend
tags: [spring-scheduling, tdd, mockito, notificacoes, cron, cross-tenant]

# Dependency graph
requires:
  - phase: 88-01
    provides: "existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria (idempotency), HonorarioRepository.findByProcessoIdIn (batch fetch), SchedulingConfig (@EnableScheduling)"
  - phase: 85-consolidacao-logica-prazo-critico
    provides: "RiscoPrazoService.computeRisco/computeRiscoEvento (3-arg, hoje-injectable) — single source of prazo/evento risk"
  - phase: 86-notificacoes-infraestrutura
    provides: "NotificacaoService.criar(...) — sole write choke point for Notificacao rows"
provides:
  - "AlertasDiariosJob — first @Scheduled background job in the codebase; daily (06:00 Atlantic/Cape_Verde) cross-tenant scan for prazo/evento risk-level crossings and honorarios >=30 days unpaid, edge-triggered notifications to Processo responsavel + tenant ADMINs (NOTF-20, NOTF-21, NOTF-23)"
  - "AlertasDiariosJobTest — 7-scenario Mockito behavioral proof (idempotency, threshold-crossing, per-tenant failure isolation, honorario guards, evento-without-processo admin-only fan-out)"
affects: [89-sino-pagina-notificacoes, v2.10-milestone-close]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Package-private 2-arg/3-arg date-injectable split on a @Scheduled job (public no-arg executar() delegates to package-private executar(LocalDate hoje)) for deterministic, Spring-context-free unit testing"
    - "3-layer try/catch failure isolation for background/cross-tenant jobs: top-level (whole executar body), per-tenant (inside tenantRepository.findAll() loop), per-entity (inside each processar* loop)"
    - "Batch-preload once per tenant (processoPorId map + ADMIN list) computed in a single processarTenant entry point and threaded as parameters — never re-queried per entity"
    - "Edge-triggered idempotency: existsBy<5-tuple>(tenantId, destinatarioId, entidadeTipo, entidadeId, categoria) existence-check immediately before every write, called responsavel-first-then-admins so a responsavel-who-is-also-admin is deduped naturally"

key-files:
  created:
    - backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java
    - backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java
  modified: []

key-decisions:
  - "ADMIN fan-out resolved inline by the job itself (fetch admins once per tenant in processarTenant, call the already-public NotificacaoService.criar per admin per entity) rather than via NotificacaoService.notificarAdmins(...), which is package-private and unreachable from com.lexcv.jobs — this was flagged as the required resolution in 88-PATTERNS.md, not a deviation"
  - "@MockitoSettings(strictness = Strictness.LENIENT) on AlertasDiariosJobTest — the Task 1 RED skeleton's empty executar(LocalDate) body exercises none of the configured stubs, so MockitoExtension's default STRICT_STUBS would fail every test with UnnecessaryStubbingException instead of the intended behavioral assertion failures; LENIENT keeps verify(...) as the real gate in both RED and GREEN"
  - "Single shared titulo/mensagem per categoria (no separate 2nd/3rd-person phrasing) for both the Processo responsavel and every ADMIN recipient, matching 88-CONTEXT.md/88-PATTERNS.md's one-mensagem-per-categoria description rather than copying notificarProcessoAtribuido's dest-vs-admin phrasing split (not specified for PRAZO/EVENTO/HONORARIO)"

patterns-established:
  - "Pattern: 2-arg/3-arg (or no-arg/1-arg) date-injectable split as the standard shape for any future @Scheduled job needing deterministic tests"
  - "Pattern: 3-layer try/catch (top-level / per-tenant / per-entity) as the standard failure-isolation shape for cross-tenant background jobs"

requirements-completed: [NOTF-20, NOTF-21, NOTF-23]

# Metrics
duration: 21min
completed: 2026-07-09
---

# Phase 88 Plan 02: Daily Alertas Diários Job Summary

**AlertasDiariosJob — the codebase's first `@Scheduled` cross-tenant job, delivering edge-triggered PRAZO/EVENTO/HONORARIO alerts via `RiscoPrazoService` + `NotificacaoService.criar`, built test-first (7 Mockito scenarios, RED confirmed failing before GREEN implementation).**

## Performance

- **Duration:** ~21 min
- **Started:** 2026-07-09T20:36:21Z
- **Completed:** 2026-07-09T20:57:20Z
- **Tasks:** 2 completed
- **Files modified:** 2 (both newly created; `AlertasDiariosJob.java` written as a skeleton in Task 1 then fully implemented in Task 2)

## Accomplishments

- `AlertasDiariosJob`: first `@Scheduled(cron = "0 0 6 * * *", zone = "Atlantic/Cape_Verde")` job in the codebase. Iterates `tenantRepository.findAll()` explicitly (never `SecurityContextHolder`/`getTenantId()`), with a 3-layer try/catch (top-level, per-tenant, per-entity) so one tenant's or one entity's failure never blocks the rest of the run or cancels future scheduled executions.
- Risk for prazo/evento comes exclusively from `RiscoPrazoService.computeRisco`/`computeRiscoEvento` (zero re-implemented threshold logic); honorario uses its own `ChronoUnit.DAYS.between(dataAcordo, hoje) >= 30` rule, silently skipping honorarios with `valorTotal == null`, `dataAcordo == null`, or already fully paid.
- Every notification funnels through a single `notificar(...)` helper: null-safe on `destinatarioId`, existence-checked via `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria` before every `notificacaoService.criar(...)` call — called for the Processo responsavel first, then each tenant ADMIN (fetched once per tenant, never per entity).
- `AlertasDiariosJobTest`: 7 scenarios proving idempotency (re-run creates nothing new), threshold crossing (proximo->vencido yields exactly one new notification), per-tenant failure isolation (tenant A throws, tenant B still fully processed, `executar` itself never throws), honorario guard rules (null/fully-paid/too-recent skipped, exactly-30-days-unpaid notifies), and an evento with no `processoId` (admin-only fan-out, no responsavel). Zero `SecurityContextHolder` setup anywhere in the test — its absence is itself proof the job never depends on it.
- TDD gate sequence confirmed in git log: `test(88-02)` RED commit (5 of 7 scenarios failing for behavioral reasons, confirmed via `mvn -q -o test -Dtest=AlertasDiariosJobTest` exiting non-zero) -> `feat(88-02)` GREEN commit (7/7 passing). Full backend suite (`mvn -q -o test`) also passes with no regressions.

## Task Commits

Each task was committed atomically:

1. **Task 1 (RED): Write AlertasDiariosJobTest + compilable job skeleton; tests fail** - `1e999ed` (test)
2. **Task 2 (GREEN): Implement the cross-tenant scan so all tests pass** - `a4f31f2` (feat)

**Plan metadata:** (pending — this SUMMARY.md commit)

## Files Created/Modified

- `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` - New file, first in package `com.lexcv.jobs`. `@Component @RequiredArgsConstructor @Slf4j`, 9 constructor-injected collaborators. Task 1: compilable skeleton (`@Scheduled` no-arg `executar()` delegating to an empty package-private `executar(LocalDate hoje)`). Task 2: full implementation — `processarTenant` (batch-preloads `processoPorId` map + tenant ADMIN list once) dispatching to `processarPrazos`/`processarEventos`/`processarHonorarios` (each with its own per-entity try/catch), plus the shared `notificar(...)` idempotency/write helper.
- `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java` - New file. `@ExtendWith(MockitoExtension.class)` + `@MockitoSettings(strictness = Strictness.LENIENT)`. Mocks all 7 repositories + `NotificacaoService`; uses a real `new RiscoPrazoService()`. 7 test methods covering the full behavior block from the plan.

## Decisions Made

- ADMIN fan-out resolved inline by the job itself (pattern (a) from 88-PATTERNS.md): `NotificacaoService.notificarAdmins(...)` is package-private and unreachable from `com.lexcv.jobs` — the job instead fetches `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` once per tenant inside `processarTenant` and calls the already-public `criar(...)` directly for each pre-fetched admin per entity. This was the flagged, required resolution (not a discretionary deviation) and also satisfies ARCHITECTURE.md's "admins fetched once per tenant, not once per entity" requirement.
- Added `@MockitoSettings(strictness = Strictness.LENIENT)` to the test class: with Task 1's empty `executar(LocalDate)` skeleton, none of the stubs configured for the RED-phase tests are actually invoked, so MockitoExtension's default `STRICT_STUBS` would fail every test with `UnnecessaryStubbingException` rather than the intended "notification never created" behavioral failures. LENIENT keeps the `verify(...)` calls as the real assertions in both RED and GREEN — a standard, low-risk adjustment for a test-first job that starts as a no-op.
- Used a single shared titulo/mensagem per categoria (no separate 2nd-/3rd-person phrasing) for both the Processo responsavel and every ADMIN recipient, matching 88-CONTEXT.md/88-PATTERNS.md's "one mensagem per categoria" notification-text guidance rather than copying `notificarProcessoAtribuido`'s dest-vs-admin phrasing split (which was not specified for PRAZO/EVENTO/HONORARIO categorias).
- Followed 88-PATTERNS.md's batch-preload idiom verbatim: `processoPorId` map and `admins` list are computed exactly once in `processarTenant` and threaded as parameters into all three `processar*` methods — never re-queried per prazo/evento/honorario.

## Deviations from Plan

None - plan executed exactly as written. Both tasks matched their acceptance criteria on the documented attempt: the RED run genuinely failed (5 of 7 scenarios, for behavioral "notification never created" reasons, not compile errors) before any implementation existed, and the GREEN run passed all 7 scenarios plus the full backend test suite. No auto-fixes, no blocking issues, no architectural questions arose.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. No new Maven dependency was added (`@Scheduled` support was already activated by Plan 88-01's `SchedulingConfig`, so this plan only needed to add the job class itself).

## Next Phase Readiness

- All three of this phase's requirements (NOTF-20 prazo alerts, NOTF-21 evento alerts, NOTF-23 honorario alerts) are now wired end-to-end: `AlertasDiariosJob` -> `notificar(...)` -> `NotificacaoService.criar(...)` -> persisted `Notificacao` rows.
- The daily cron trigger is fully live: `SchedulingConfig`'s `@EnableScheduling` (Plan 88-01) activates Spring's scheduler, and `AlertasDiariosJob.executar()` is annotated `@Scheduled(cron = "0 0 6 * * *", zone = "Atlantic/Cape_Verde")` — no further wiring required for it to fire in a running application.
- Only Phase 89 (bell + `/notificacoes` page, gated only on Phase 86) remains to close out milestone v2.10; it purely consumes whatever rows this job (and Phase 87's event-triggered alerts) persist — no new dependency on Phase 88 beyond the `Notificacao` table itself.
- No blockers or concerns. `cd backend && mvn -q -o test -Dtest=AlertasDiariosJobTest` exits 0 (7/7 passing); `cd backend && mvn -q -o test` (full backend suite) also exits 0 — no regressions introduced by the new job or the Plan 88-01 repository additions it depends on.

---
*Phase: 88-verificacao-diaria-de-prazos-e-honorarios*
*Completed: 2026-07-09*
