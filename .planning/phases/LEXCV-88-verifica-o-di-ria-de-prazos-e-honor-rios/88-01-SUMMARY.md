---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
plan: 01
subsystem: backend
tags: [spring-data-jpa, spring-scheduling, notificacoes, honorarios]

# Dependency graph
requires:
  - phase: 85-consolidacao-logica-prazo-critico
    provides: RiscoPrazoService (consolidated "prazo crítico" logic Plan 88-02's job will consume)
  - phase: 86-notificacoes-infraestrutura
    provides: Notificacao entity + NotificacaoRepository base, criar(...) service method
provides:
  - "NotificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria — per-(tenant,recipient,entity,categoria) idempotency existence-check"
  - "HonorarioRepository.findByProcessoIdIn(Collection<UUID>) — batch fetch avoiding N+1"
  - "SchedulingConfig — dedicated @Configuration @EnableScheduling class activating Spring's @Scheduled infrastructure"
affects: [88-02]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dedicated single-concern @Configuration class for cross-cutting activation (mirrors MinioConfig), used here for @EnableScheduling"
    - "existsBy<N-tuple> derived-query as the idempotency backbone for scheduled/background jobs"
    - "findBy<Field>In(Collection<...>) as the batch-fetch idiom to avoid per-row N+1 loops"

key-files:
  created:
    - backend/src/main/java/com/lexcv/config/SchedulingConfig.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
    - backend/src/main/java/com/lexcv/repositories/HonorarioRepository.java

key-decisions:
  - "@EnableScheduling isolated in its own dedicated SchedulingConfig class rather than annotating BackendApplication directly, per phase CONTEXT.md convention and to keep the one-concern-per-@Configuration pattern already set by MinioConfig/SecurityConfig"
  - "No @Query annotation added to either new repository method — both are pure Spring Data method-name-derived queries, matching the existing simple finders in each interface"
  - "No DB unique constraint/migration added for the idempotency tuple in this plan — deliberately deferred to Plan 88-02's threat model (T-88-04), relying on the application-level existence-check on this single-instance deployment"

patterns-established:
  - "Pattern: existsBy<5-tuple> derived-query as the idempotency backbone for future scheduled/background jobs"
  - "Pattern: findBy<Field>In(Collection<...>) as the batch-fetch idiom to avoid N+1 loops"

requirements-completed: [NOTF-20, NOTF-21, NOTF-23]

# Metrics
duration: 13min
completed: 2026-07-09
---

# Phase 88 Plan 01: Repository & Scheduling Enablers Summary

**Two Spring Data derived-query methods (idempotency existence-check on Notificacao, batch honorario fetch by processo-id set) plus an isolated `@EnableScheduling` config class — the three enabling artifacts Plan 88-02's daily alertas job depends on to compile and actually fire.**

## Performance

- **Duration:** 13 min
- **Started:** 2026-07-09T20:16:23Z
- **Completed:** 2026-07-09T20:29:11Z
- **Tasks:** 2 completed
- **Files modified:** 3 (2 modified, 1 created)

## Accomplishments
- `NotificacaoRepository` gained `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria` — the exact-5-tuple existence-check that will back the daily job's per-recipient, per-level idempotency (called before every `criar(...)` to skip a notification that already exists for this tenant/recipient/entity/categoria combination).
- `HonorarioRepository` gained `findByProcessoIdIn(Collection<UUID>)` — a single batch query to fetch all of a tenant's honorarios via its processo-id set, avoiding the N+1 anti-pattern (looping `findByProcessoId` once per processo) flagged as Pitfall 7 in the phase's research.
- New `SchedulingConfig` class (`@Configuration` + `@EnableScheduling`, empty body) activates Spring's scheduling infrastructure in isolation, as its own dedicated class rather than on `BackendApplication` — so Plan 88-02's `@Scheduled` trigger on `AlertasDiariosJob` will actually register and fire instead of silently no-op'ing.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add the two Spring Data derived-query methods the daily job depends on** - `aa88da4` (feat)
2. **Task 2: Create SchedulingConfig to activate @Scheduled support** - `45a6938` (feat)

**Plan metadata:** (pending — this SUMMARY.md commit)

_Note: No TDD tasks in this plan; both tasks were single-commit feat additions._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` - Added `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria` boolean existence-check (5-param derived query); pre-existing methods untouched.
- `backend/src/main/java/com/lexcv/repositories/HonorarioRepository.java` - Added `findByProcessoIdIn(Collection<UUID>)` batch finder plus `java.util.Collection` import; pre-existing `findByProcessoId(UUID)` untouched.
- `backend/src/main/java/com/lexcv/config/SchedulingConfig.java` - New file: `@Configuration @EnableScheduling` class with empty body, sole purpose is activating `@Scheduled` support for Plan 88-02.

## Decisions Made
- Followed the plan's `<interfaces>` block verbatim (field names/types were pre-verified by the planner against `Notificacao.java`/`Honorario.java`), so no additional codebase exploration was needed beyond the plan's `read_first` list.
- Kept `SchedulingConfig` with zero `@Bean` methods and zero fields as specified — its only job is the class-level annotation.

## Deviations from Plan

None - plan executed exactly as written. Both tasks matched their acceptance criteria on the first attempt; no auto-fixes, no blocking issues, no architectural questions arose.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. No new Maven dependency was added (`@EnableScheduling`/`@Scheduled` ship in `spring-context`, already resolved via `spring-boot-starter-web`).

## Next Phase Readiness

- Plan 88-02 (the daily `AlertasDiariosJob`) can now be written and will compile against `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria` and `findByProcessoIdIn`, and its `@Scheduled` trigger will be registered against a live scheduler thread thanks to `SchedulingConfig`.
- No blockers or concerns. `mvn -q -DskipTests compile` exits 0 with all three artifacts present; `@EnableScheduling` confirmed to appear exactly once in `backend/src/main/java`; both new method signatures confirmed present via grep.

---
*Phase: 88-verificacao-diaria-de-prazos-e-honorarios*
*Completed: 2026-07-09*
