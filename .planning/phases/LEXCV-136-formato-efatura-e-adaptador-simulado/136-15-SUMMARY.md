---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 15
subsystem: backend-fiscal-outbox
tags: [fiscal, efatura, outbox, scheduler, testcontainers, notification, tdd]
requires: ["136-13", "136-14"]
provides:
  - "FiscalOutboxJob (@Component, jobs package): @Scheduled(fixedDelayString ${app.efatura.outbox.intervalo:PT30S}, initialDelayString ${app.efatura.outbox.atraso-inicial:PT20S}) executar(); package-private int executarUmaVez()"
  - "FixturaEmissaoFiscal Phase 136 helpers: estadoComunicacao, linhaComunicacao, linhaXml, contarNotificacoes, contarNotificacoesDe, suspenderTenant, forcarTentativas, adiarComunicacao, criarUtilizador"
  - "FiscalOutboxJobIT + FiscalOutboxJobFalhasForcadasIT: end-to-end real-DB proof"
affects: [136-16]
tech-stack:
  added: []
  patterns:
    - "Job called directly in ITs (executarUmaVez) on a JPA slice without @EnableScheduling; movable Clock for FR -> NC created_at order"
    - "Forced-failure scenario in a second IT class (own context via @TestPropertySource)"
key-files:
  created:
    - backend/src/main/java/com/lexcv/jobs/FiscalOutboxJob.java
    - backend/src/test/java/com/lexcv/jobs/FiscalOutboxJobTest.java
    - backend/src/test/java/com/lexcv/jobs/FiscalOutboxJobIT.java
    - backend/src/test/java/com/lexcv/jobs/FiscalOutboxJobFalhasForcadasIT.java
  modified:
    - backend/src/test/java/com/lexcv/services/fiscal/FixturaEmissaoFiscal.java
decisions:
  - "The forced-failure scenario is a second IT class (FiscalOutboxJobFalhasForcadasIT) rather than a @Nested context, so the @ServiceConnection container wiring stays the proven per-class pattern"
  - "ResolucaoPapeisService is a @MockitoBean returning effective permissions per user id; users, NotificacaoService.criar's tenant check and the ON CONFLICT dedup are real"
  - "DFE-03, DFE-04, DFE-05 and DFE-07 marked complete; DFE-01/02 stay pending for the 136-16 G1–G15 primary-source gate (format choices may change), DFE-06 stays pending because PDF and email marking belong to later phases"
metrics:
  duration: "~30 min"
  completed: 2026-10-06
  tasks: 2
  files: 5
---

# Phase 136 Plan 15: Scheduled outbox job, proven end to end Summary

`FiscalOutboxJob` runs the fiscal outbox in the background. About every 30 s (after an initial 20 s), it claims up to 20 due PENDENTE rows from every office, suspended ones included, with a 2-minute lease. It then hands each row to `ProcessadorComunicacaoFiscal`.

- **Decoupled from emission:** the emission transaction is untouched. It only creates the PENDENTE row, which is the outbox entry.
- **Isolation:** there is a `catch (Throwable)` at the top and another per item. One failing item, even a JVM `Error`, never stops the batch, and the job never throws.
- **No user, no transaction:** the job has no SecurityContext, does not iterate tenants (each row carries its tenant) and has no transaction annotation.
- **Proof:** the whole background flow is now proven on real PostgreSQL with real FR and NC emission.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | FiscalOutboxJob with isolation unit test | bc9018b (RED), d0ddcad (GREEN) |
| 2 | FiscalOutboxJobIT end to end on PostgreSQL (+ fixture helpers) | c0c62b2 |

## Verification

- **FiscalOutboxJobTest:** 6 tests, green.
  - It claims with `(20, PT2M)` from `EfaturaProperties` and processes the items in order, returning the count. An empty batch processes nothing.
  - A `StackOverflowError` on item 1 and an `AssertionError` on item 2 still let item 3 run.
  - `reclamar` throwing a `RuntimeException` or an `OutOfMemoryError` does not escape `executar()`.
  - Reflection pins the `@Scheduled` strings, the absence of cron and fixedRate, and the absence of `@Transactional`.
- **FiscalOutboxJobIT** (Testcontainers `postgres:16-alpine`, run by failsafe): 6 tests, 0 failures, 0 errors, 0 skipped.
  - **Emission, then one run:** right after `PagamentoFaturadoService.registar`, the row is PENDENTE and there is no XML row. One run gives ACEITE_SIMULADO, 1 attempt, `concluido_em` = now, and no error or lease. The XML row has an IUD of 45 characters starting with "CV3", repositorio 3, LED 99999, `versao_formato` 2024-05-27, the right tenant, `xml_sha256` = SHA-256 of `xml`, and `DfeValidador` accepts it. A second run claims nothing.
  - **FR then NC (created 1 s apart), one run:** 2 items, both ACEITE_SIMULADO. The NC XML validates and contains `References … FiscalDocument` = the FR IUD, and its own IUD is different.
  - **Suspended office:** `t_tenant.ativo = false` after emission, and the row is still accepted.
  - **ERRO and notification:** NC whose FR has no XML row (the FR is deferred), attempts forced to 7.
    - The run gives ERRO `ORIGEM_SEM_IUD` with its fixed message, 8 attempts and no NC XML.
    - In the same batch, another office's FR is accepted, so the failure did not stop the batch.
    - Exactly one `COMUNICACAO_FISCAL_FALHOU` notification goes to each of the two active manage users, with `entidade_id = <nc>:0`. None goes to the inactive manage user, the view-only user, or the other office's manage user. The counts are 2 for the office and 0 for the other.
    - A second run claims nothing and creates nothing.
  - **Episode 1:** the reprocess is emulated with `reporPendente` in a `TransactionTemplate`, giving PENDENTE and `reprocessamentos` 1. Attempts forced to 7 again: ERRO, and a second notification for the manage user (`:0`, `:1`). Still none for the viewer.
  - **Recovery (extra):** after the FR is accepted, the reprocessed NC is accepted and references the FR IUD.
- **FiscalOutboxJobFalhasForcadasIT** (second context, `app.efatura.simulado.falhas-forcadas=true`): 1 test, green, 0 skipped. A valid FR gives PENDENTE, 1 attempt, `FALHA_SIMULADA`, next attempt now + 30 s, no completion or lease, the XML row is stored (reused next time) and no notification is sent. A second run at the same instant claims nothing.
- **Also run:** the full unit suite ran 1207 tests with 0 failures. `mvn -DskipTests compile spotbugs:check` is clean with no new exclusion.
- **Acceptance checks:**
  - In `FiscalOutboxJob`: `@Transactional` appears 0 times, `app.efatura.outbox.intervalo` once and `catch (Throwable` 3 times (2 in code, 1 in Javadoc).
  - `git diff --quiet HEAD -- PagamentoFaturadoService.java NotaCreditoService.java` passes. Both files were last changed in Phase 135, so they are untouched in Phase 136 (CFG-03).
  - The IT asserts `startsWith("CV3")` and length 45, and validates the stored XML with `DfeValidador`.
  - Fixture BASE 6ed05bf: `git diff 6ed05bf -- FixturaEmissaoFiscal.java | grep -c '^-[^-]'` gives 0.

## Deviations from Plan

None that changed production code. Every IT behaviour passed on the first run, so no `fix(136-15)` commit was needed. Additions:

- **Fixture helpers:** `contarNotificacoesDe` and `adiarComunicacao` were added on top of the planned ones, additively. `adiarComunicacao` keeps the FR out of a run so the NC deterministically hits `ORIGEM_SEM_IUD`. The planned `linhaXml(docId)` returns `Optional<Map>`, so "no XML row" is assertable.
- **Movable clock:** the IT uses a movable clock and emits the NC 1 s after the FR. Both rows get `created_at` from the injected clock, and equal timestamps would make the FR/NC claim order depend on random UUIDs.
- **Extra recovery test:** the reprocessed NC is accepted once the FR has an IUD.

## TDD Gate Compliance

- **Task 1:** RED bc9018b failed to compile because `FiscalOutboxJob` was missing. GREEN d0ddcad.
- **Task 2:** the end-to-end IT was written against the finished production code and passed at once. No production defect was found.

## Requirements

- **Marked complete:**
  - DFE-03: the single port, chosen at deployment with fail-fast, and now actually carrying communication.
  - DFE-04: background update after emission, with automatic retries and the state shown in the UI (136-11).
  - DFE-05: the reprocess route (136-14) and the UI (136-08/11).
  - DFE-07: the per-episode notification to `financeiro:manage` holders.
- **Left pending:**
  - DFE-01 and DFE-02: XML and IUD are delivered, but the G1–G15 primary-source gate in 136-16 may still change format choices.
  - DFE-06: the PDF and email marking belong to later phases.

## Notes for downstream plans

- **136-16 (live UAT from the jar):** this is the first real boot with `FiscalOutboxJob` scheduled. Expect the first run about 20 s after startup and then about every 30 s. Booting with `EFATURA_MODE=REAL` must abort.

## Threat Flags

None.
- T-136-58: batch of 20, fixedDelay, two `catch (Throwable)` layers, pool size 3.
- T-136-59: the IT proves there is no cross-tenant notification and none for non-manage users.
- T-136-60: emission files unchanged, and PENDENTE with no XML right after emission is asserted.

## Self-Check: PASSED

- FOUND: all 4 created and 1 modified files listed in key-files
- FOUND: commits bc9018b, d0ddcad, c0c62b2
