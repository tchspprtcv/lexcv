---
phase: 135-nota-de-cr-dito
plan: 07
subsystem: backend-fiscal
tags: [fiscal, nota-de-credito, testcontainers, concurrency, integration-tests]
requires:
  - "135-06: NotaCreditoService.preVisualizar / emitir"
  - "135-08: RecebidoNoMes.somar (monthly KPI with estorno)"
provides:
  - "FixturaEmissaoFiscal: contarNotasCredito, contarEstornos, totalPagoHonorario, numerosNotasCredito, numerosFaturasRecibo, ultimoNumeroNotaCredito"
  - "NotaCreditoServiceIT (10 tests): real-DB atomicity, four-reader coherence, numbering, snapshot, preview parity, replay, isolation"
  - "NotaCreditoConcorrenciaIT (3 tests): cap race, same-key race, NC/FR interleaving without 40P01"
affects: [135-13]
tech-stack:
  added: []
  patterns:
    - "Outcome-classifying parallel runner (Desfecho): accepted RecusaFiscalException codes are outcomes; anything else fails the test"
key-files:
  created:
    - backend/src/test/java/com/lexcv/services/fiscal/NotaCreditoServiceIT.java
    - backend/src/test/java/com/lexcv/services/fiscal/NotaCreditoConcorrenciaIT.java
  modified:
    - backend/src/test/java/com/lexcv/services/fiscal/FixturaEmissaoFiscal.java
    - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceIT.java
decisions:
  - "The overdue-alert condition is evaluated as in AlertasDiariosJob (totalPago == null || totalPago < valorTotal) on a Honorario re-read in a fresh transaction"
  - "In the cap race, 503 FATURACAO_OCUPADA is tolerated and counted. With zero 503s the test demands exactly 4 successes and 4 NC_EXCEDE_ORIGINAL; otherwise successes <= 4. All runs so far had zero 503s"
  - "The NC/FR interleaving test uses two extra processos of the same cliente (t_honorario has UNIQUE processo_id), alternating between them"
metrics:
  duration: "~20 min"
  completed: 2026-10-05
  tasks: 2
  files: 4
---

# Phase 135 Plan 07: NC integration and concurrency ITs on PostgreSQL Summary

These ITs run on real PostgreSQL (Testcontainers). They prove what the Mockito suite of plan 06 cannot:
- **Atomicity:** NC emission is all or nothing, including the NC series increment.
- **Coherence:** the four "pago" readers agree after a partial and then a total NC.
- **Concurrency:** the cumulative cap and idempotency hold under real concurrency, and the NC and FR paths never deadlock.

No production defect was found.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | Fixture extension + NotaCreditoServiceIT + FR-reuses-NC-key case | 00fd337 |
| 2 | NotaCreditoConcorrenciaIT | dbcde30 |

## What is proven

**NotaCreditoServiceIT (10 tests)**
- `coerenciaSaldoTotalPagoKpiEAlertaAposNcParcialETotal`. Setup: a honorário of 120 000 and an FR of 120 000 with 20 % retention. Each reader takes its own real path:
  - the conta-corrente saldo
  - `Honorario.totalPago` (@Formula), re-read in a new transaction
  - `RecebidoNoMes.somar(findByHonorarioId, 2026-06)`
  - the alert condition

  Results:

  | State | All four readers | Alert eligible |
  |-------|------------------|----------------|
  | Before any NC | 120 000 | no |
  | After the PARCIAL NC of 20 000 | 100 000 | yes |
  | After the TOTAL NC | 0 | yes |

  A JDBC SUM cross-checks the formula. A further NC of 0.01 is refused with 409 NC_EXCEDE_ORIGINAL and has no effects.
- **Atomicity:** a failure injected in `registarEmissaoNotaCredito` (spy target behind the MANDATORY proxy) rolls back everything: estorno, debit, document, line, comunicação, event and NC series. The next successful NC is `SIM-NC-2026/1`.
- **Numbering:** with FRs at `SIM-FR-2026/1..3`, the NCs are `SIM-NC-2026/1` and `/2` with a separate `serie_id`, and the FR series stays at 3.
- **Snapshot:** after the cliente name and morada and the emitente firma are edited, the NC adquirente and emitente still equal the FR's.
- **Preview parity:** the preview matches the emitted document exactly (base, IVA, retenção, total, líquido, restante, adquirente, data, linha), and the preview writes nothing.
- **Replay:** the same key with the same request returns `novo=false` with the same NC and estorno ids, and there is still only one NC. The same key with another valor gives 409 CHAVE_REUTILIZADA with no effects.
- **Refusals:**
  - Another tenant gets 404 DOCUMENTO_FISCAL_NAO_ENCONTRADO on both emit and preview.
  - An NC on an NC is refused with 422 NC_SOBRE_NC.
  - After billing is deactivated, a replay still returns the stored NC, while a new NC gets 409 FATURACAO_DESLIGADA.

**PagamentoFaturadoServiceIT (+1, 11 total):** an FR request that reuses an NC's key gets 409 CHAVE_REUTILIZADA, and no payment or document is created.

**NotaCreditoConcorrenciaIT (3 tests)**
- **Cap race:** 8 parallel PARCIAL NCs of 30 000 on a 120 000 FR:
  - 4 return 201 and 4 return 409 NC_EXCEDE_ORIGINAL
  - the sum of the NCs is at most 120 000, and the NC numbers are 1..4
  - saldo and totalPago equal FR - sum(NC), and there are 4 estornos
- **Same-key race:** 4 parallel requests with the same key produce one NC and one estorno. Every caller gets the same id, and exactly one gets `novo`.
- **Interleaving:** an NC and an FR for the same cliente, run in parallel 10 times. Everything succeeds, the NC series is 1..10 and the FR series is 1..11, the saldo is exact, and no failure carries SQLState 40P01.

## Verification

- `mvn -Dmaven.compiler.release=21 -Dtest=NenhumTeste -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=NotaCreditoServiceIT,PagamentoFaturadoServiceIT verify`:
  - NotaCreditoServiceIT: 10 tests run, 0 failures, 0 errors, 0 skipped
  - PagamentoFaturadoServiceIT: 11 tests run, 0 failures, 0 errors, 0 skipped
- `... -Dit.test=NotaCreditoConcorrenciaIT,PagamentoFaturadoConcorrenciaIT verify` was run twice in a row with Docker. Both runs passed: NotaCreditoConcorrenciaIT 3/3 and PagamentoFaturadoConcorrenciaIT 4/4, with 0 skipped.
- Acceptance greps: `coerenciaSaldoTotalPagoKpiEAlertaAposNcParcialETotal`, `RecebidoNoMes.somar` and `40P01` all match. The two IT files have 459 and 311 lines, against minimums of 200 and 120.

## TDD Gate Compliance

These tasks are test-only and run against production code that already exists (plans 06 and 08). Every assertion passed on the first run against real PostgreSQL, so there was no RED failure and no `feat`/`fix` commit was needed. The only iteration was a test-fixture error: a second honorário on the same processo violated `UNIQUE processo_id`. It was fixed in the test before the commit.

## Deviations from Plan

**1. [Rule 3 - Blocking] Extra fixture helpers**
- **Change:** besides the four planned helpers, the fixture gained `numerosFaturasRecibo` and `ultimoNumeroNotaCredito`. The existing `ultimoNumero` and `numerosEmitidos` are FR-only or tipo-agnostic, and the gapless and rollback assertions needed per-series views.
- **Commit:** 00fd337

**2. [Rule 1 - Test] Separate processos in the interleaving test**
- **Change:** the plan says "another honorário of the same cliente". Because `t_honorario.processo_id` is UNIQUE, the test creates two more processos for the same cliente.
- **Commit:** dbcde30

## Known Stubs

None.

## Self-Check: PASSED

- Both IT files and both modified files are present.
- Commits 00fd337 and dbcde30 are present in `git log`.
