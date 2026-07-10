---
phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico
plan: "01"
subsystem: api
tags: [spring-boot, refactor, service-layer, junit5, prazo, evento, dashboard, agenda]

# Dependency graph
requires: []
provides:
  - "RiscoPrazoService (@Service, zero dependências injetadas): computeRisco(LocalDate,String,LocalDate) + overload de 2 args (hoje=now); computeRiscoEvento(LocalDateTime,String,LocalDate) + overload de 2 args — fonte única partilhada da tabela de limiares 7d-ALTA/3d-outros"
  - "RiscoPrazoServiceTest.java — primeiro teste do backend (backend/src/test não existia antes desta plan), JUnit 5 puro, fixa vencido/proximo/ok no dia-limiar exato (0/3/7/8), null, equalsIgnoreCase, prioridade null e semântica LocalDateTime->LocalDate"
  - "ResourceController: 8 call sites de Prazo repontados a riscoPrazoService.computeRisco (zero regressão); método privado computeRisco apagado; agendaUrgentesCount e prazosCriticosCount (Evento) migrados para riscoPrazoService.computeRiscoEvento(dataFim,...) — mudança de comportamento intencional; getUpcomingEventos enriquecido com campo risco via computeRiscoEvento(dataInicio,...), filtro days intacto"
affects: [LEXCV-88-job-di-rio-alertas-prazos-eventos-honor-rios]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Injectable @Service para lógica de negócio pura (sem dependências, sem tenant/repositório) em vez de utilitário estático — segue o precedente StorageService/SetupService, mockável para a Phase 88"
    - "Overload de 3 args com `hoje: LocalDate` injetável por trás de um overload de 2 args que faz default a LocalDate.now() — preserva comportamento byte-idêntico dos call sites existentes e habilita determinismo em testes futuros (Phase 88)"
    - "Teste de caracterização JUnit 5 puro (sem @SpringBootTest/@Autowired/@ExtendWith) para lógica sem dependências — primeiro precedente de teste no backend"

key-files:
  created:
    - backend/src/main/java/com/lexcv/services/RiscoPrazoService.java
    - backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "agendaUrgentesCount (site 6) e prazosCriticosCount (site 7) usam ambos Evento.dataFim (não dataInicio) — agendaUrgentesCount hoje não referenciava nenhuma data, logo foi preciso escolher; dataFim alinha as duas KPIs de dashboard à mesma base"
  - "getUpcomingEventos (site 8) usa opção (b): ENRIQUECER sem mudar o filtro — o parâmetro days e a janela [now, now+days] sobre dataInicio ficam intactos; apenas um 6º campo 'risco' foi adicionado ao Map.of de cada item, via computeRiscoEvento(dataInicio,...), preservando zero regressão observável ao contrato existente do sino"
  - "Variáveis locais today/sevenDays removidas do bloco prazosCriticosCount depois de confirmadas sem outro uso no método (grep de todo o ficheiro) — eram dead code após a migração para riscoPrazoService.computeRiscoEvento"

patterns-established:
  - "RiscoPrazoService é o primeiro serviço 'de negócio' puro (sem repositório/tenant) do projeto — services/ existentes (StorageService, SetupService) eram infraestrutura; este é o precedente para a próxima extração de lógica duplicada"

requirements-completed: [NOTF-22]

# Metrics
duration: ~17min
completed: 2026-07-08
---

# Phase LEXCV-85 Plan 01: Consolidação da Lógica de "Prazo Crítico" Summary

**Extraiu as 5 implementações duplicadas do veredito "ok/proximo/vencido" (1 método privado + 3 blocos ad-hoc em `ResourceController`) para um único `RiscoPrazoService` injetável, com teste de caracterização (primeiro do backend) e migração intencional dos 3 sites baseados em `Evento` para a mesma tabela de limiares 7d-ALTA/3d-outros.**

## Performance

- **Duration:** ~17 min (estimated — first commit at 2026-07-08T14:34:42Z, second at 2026-07-08T14:38:42Z, plus upfront context-loading and post-verification time)
- **Started:** ~2026-07-08T14:24:00Z (estimated)
- **Completed:** 2026-07-08T14:41:30Z
- **Tasks:** 2/2
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments
- New `RiscoPrazoService` (`@Service`, zero injected dependencies) exposing 4 public methods (`computeRisco` x2, `computeRiscoEvento` x2), moving the `Prazo` threshold logic verbatim and adding an `Evento`-based twin that reuses the exact same 7d-ALTA/3d-outros table
- First backend test ever (`backend/src/test` did not exist before this plan): a pure JUnit 5 characterization test (no Spring context) fixing null, vencido, exact-threshold days (0/3/7/8), `equalsIgnoreCase`, null-prioridade, and `LocalDateTime`->`LocalDate` semantics — 15 test methods, all passing
- All 8 `Prazo`-based call sites in `ResourceController` (listProcessos risco_mais_critico, listPrazos, listAllPrazos, createPrazo x2, togglePrazoConcluido x3) repointed to `riscoPrazoService.computeRisco(...)`; the private `computeRisco` method deleted entirely — no duplicate copy left behind
- Intentional behavior change (ROADMAP criterio 3): `agendaUrgentesCount` and `getProcessosDashboard`'s `prazosCriticosCount` now use `riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade())` against the shared threshold table, replacing their previous inconsistent windows (no window at all, and a fixed 7-day window, respectively)
- `getUpcomingEventos` enriched with a `risco` field via `computeRiscoEvento(dataInicio, prioridade)` — the `days` window filter, sort, and slice contract are completely unchanged (zero regression to the existing bell-dropdown consumer)

## Task Commits

Each task was committed atomically:

1. **Task 1: Criar RiscoPrazoService (@Service) + teste de caracterização da tabela de limiares** - `53fc3ff` (feat)
2. **Task 2: Repontar os 8 call sites de Prazo + 3 blocos de Evento em ResourceController e apagar o método privado** - `5e3efcf` (refactor)

**Plan metadata:** (this commit, docs: complete plan — added by orchestrator/this agent after summary)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java` - New injectable `@Service`, 4 public methods, zero dependencies, zero tenant/repository access — pure function (data + prioridade -> risco string)
- `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java` - New — first backend test; 15 `@Test` methods covering the full threshold table plus `LocalDateTime`->`LocalDate` conversion semantics
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - `+RiscoPrazoService` import/field; 8 call sites repointed; private `computeRisco` deleted; `agendaUrgentesCount`/`prazosCriticosCount` rewritten to use `computeRiscoEvento`; `getUpcomingEventos` gained a `risco` field; unused `today`/`sevenDays` locals removed

## Decisions Made
- `dataFim` chosen as the shared field for both dashboard KPIs (sites 6 and 7) — `agendaUrgentesCount` had no date reference at all before, so a field had to be chosen; using the same field as site 7 keeps the two dashboard KPIs on a consistent basis
- Site 8 (`getUpcomingEventos`) kept its `days`-window filter and ordering/slice completely untouched (option b from the plan), only adding the enrichment field — this was a deliberate zero-regression choice for the existing `NotificationBell` consumer, not a shortcut
- Removed the now-dead `today`/`sevenDays` locals after confirming (via a whole-file grep) they had no other use once the fixed-window check was replaced

## Deviations from Plan

None — plan executed exactly as written. The `<interfaces>` block's verbatim code (SHAPE-ALVO for the service, and the exact call-site inventory table) matched the live file precisely on inspection, so both tasks were mechanical, precise edits with no drift. One self-correction during Task 1: the test file's Javadoc originally spelled out `@SpringBootTest`/`@Autowired`/`@ExtendWith` literally to explain why they weren't needed, which caused the acceptance-criteria grep (checking for *usage* of those annotations) to false-positive on the comment text; reworded the comment to describe the same fact without the literal annotation strings, and the grep now correctly returns 0. This was caught and fixed before committing Task 1, so no separate commit was needed.

## Issues Encountered

`85-PATTERNS.md`, referenced repeatedly in this plan's `<read_first>`/`<context>` blocks (sections "RiscoPrazoService.java (NEW)", "No Analog Found", "ResourceController.java (MODIFIED)"), does not exist in `.planning/phases/LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico/` — only `85-01-PLAN.md` and `85-CONTEXT.md` are present. This did not block execution: the plan's own `<interfaces>` block already inlined the exact verbatim service shape, the full call-site inventory table (with verified line numbers), and the injection convention — i.e., everything the missing sections were meant to supply was duplicated directly in the plan text and cross-checked by this agent against the live source before editing. Flagging this as a gap from the phase-planning step (85-PATTERNS.md was apparently never generated or committed), not something this execution needed to work around beyond what the plan already provided.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `RiscoPrazoService` is the hard prerequisite Phase 88 (daily `@Scheduled` job) needs — it exists, is stable, injectable, and its 3-arg overloads (`hoje: LocalDate`) are exactly the determinism hook Phase 88's tests will need to fix a reference date
- `ResourceController` now has exactly one source of truth for "prazo crítico"/"evento crítico" — no dangling duplicate logic for a future phase to accidentally diverge from
- No blockers. The `85-PATTERNS.md` gap noted above is informational only (did not affect this plan's completeness) but may be worth backfilling if a future phase's planning step expects to read it via `@`-reference

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/services/RiscoPrazoService.java
- FOUND: backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java
- FOUND: backend/src/main/java/com/lexcv/controllers/ResourceController.java (modified, verified via grep counts: 8x riscoPrazoService.computeRisco(, 3x riscoPrazoService.computeRiscoEvento(, 0x private String computeRisco()
- FOUND commit: 53fc3ff
- FOUND commit: 5e3efcf

---
*Phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico*
*Completed: 2026-07-08*
