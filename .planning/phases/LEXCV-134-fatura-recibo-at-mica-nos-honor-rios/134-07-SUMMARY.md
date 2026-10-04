---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 07
subsystem: backend-fiscal
tags: [fiscal, efatura, testcontainers, concurrency, atomicity, idempotency]
requires:
  - "134-06: PagamentoFaturadoService.registar / faturacaoAtiva, AuditoriaFiscalService.registarEmissao"
  - "134-04: PreVisualizacaoFaturaService.preVisualizar"
  - "134-03: DocumentoFiscalRepository.buscar / findByIdAndTenantId"
provides:
  - "FixturaEmissaoFiscal (test helper, JdbcTemplate): criarTenantComFaturacao / criarTenant(regime, ativa), garantirParametros, criarCliente/Processo/Honorario, criarContaCorrente, counters (pagamentos, documentos, linhas, comunicacoes, eventos de emissão), saldo, ultimoNumero, numerosEmitidos -- reusable by plan 10"
  - "PagamentoFaturadoServiceIT (10 tests) and PagamentoFaturadoConcorrenciaIT (4 tests) on postgres:16-alpine"
affects: [134-10, 134-14]
tech-stack:
  added: []
  patterns:
    - "@MockitoSpyBean on a @Transactional(MANDATORY) bean: stub on AopTestUtils.getUltimateTargetObject(spy), not on the proxy"
    - "Holding-lock injection: a second thread holds SELECT ... FOR UPDATE on the series row while the emission runs"
key-files:
  created:
    - backend/src/test/java/com/lexcv/services/fiscal/FixturaEmissaoFiscal.java
    - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceIT.java
    - backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoConcorrenciaIT.java
  modified: []
decisions:
  - "Both ITs use a fixed Clock (2026-06-15T13:00Z = midday in Cabo Verde) instead of Clock.systemUTC(), so 'hoje' and the series year (SIM-FR-2026) are deterministic and no run can straddle Cabo Verde midnight"
  - "rollbackQuandoInsertDoDocumentoFalha asserts the cause chain names uk_documento_fiscal_numero instead of one exception class, because the violation may surface at an IDENTITY insert (translated DataIntegrityViolationException) or at commit"
metrics:
  duration: "~25 min"
  completed: 2026-10-04
  tasks: 2
  files: 3
---

# Phase 134 Plan 07: Emission proofs on real PostgreSQL Summary

This plan proves on PostgreSQL (Testcontainers) what Mockito cannot:
- all-or-nothing emission under three injected failures
- gapless numbering and exact conta-corrente balances with 8 concurrent emissions
- the same-key double-submit race
- preview parity with no side effects
- snapshot immutability after source-data edits
- two independent emitters

## What was built

- **`FixturaEmissaoFiscal`**: a plain JDBC helper with no Spring annotations. It inserts only the NOT NULL columns of `t_configuracao_fiscal` (complete, NIF 512345679, NORMAL or ISENTO with motivo "1"), `t_parametro_fiscal` (IVA 15 / retenção 20 from 2000-01-01, `ON CONFLICT DO NOTHING`), `t_cliente`, `t_processo` and `t_honorario` (`RETURNING id`). It also provides the counters every assertion uses. Plan 10 reuses it.
- **`PagamentoFaturadoServiceIT`** (10 tests):
  - **a.** CONTEXT figures: 104347.83 / 15652.17 / 20869.57 / 120000.00 / 99130.43, `SIM-FR-2026/1`, controlled line text without the honorário's `SEGREDO-PROFISSIONAL`, PENDENTE, CC +120000.00, `ultimo_numero` 1, one `documento_fiscal_emitir` event pointing at the document.
  - **b.** ISENTO: base = total, IVA 0, motivo "1" and its mencao.
  - **c.** `rollbackQuandoAuditoriaFalha`: the spy throws after the pagamento, document, line and comunicação inserts. Afterwards there are 0 rows, the pre-seeded CC is still 500.00 and the series is unchanged.
  - **d.** `rollbackQuandoNumeracaoFalha`: another thread holds `FOR UPDATE` on the series row, so the emission gets 503 (`SERIE_INDISPONIVEL`) after the 5 s `lock_timeout`. All counters equal the pre-call snapshot, so the pagamento saved before numbering was rolled back.
  - **e.** `rollbackQuandoInsertDoDocumentoFalha`: a JDBC-inserted document occupies (tenant, série, 2), so the emission fails on `uk_documento_fiscal_numero`. Counters are unchanged apart from the pre-inserted row.
  - **f.** The preview writes nothing (all counters equal and no series created). The emission then stores the same base/IVA/retenção/total/líquido, NIF and line text.
  - **g.** Editing the cliente (nome, morada, NIF) and the configuração (firma, morada) via JDBC leaves the document's `adquirente_*`/`emitente_*` unchanged, both in the entity read and the raw row.
  - **h.** Same key twice gives `novo=false` with the same pagamento and document, and the CC is credited once. The same key with 120000.01 gives 409 `CHAVE_REUTILIZADA` and no new rows.
  - **i.** Two emitters each get `SIM-FR-2026/1` on different series, and `buscar` returns only the tenant's own document.
  - **j.** Billing off gives 409 `FATURACAO_DESLIGADA` and zero fiscal rows; `faturacaoAtiva` is false for that tenant and true for an active one.
- **`PagamentoFaturadoConcorrenciaIT`** (4 tests). An 8-thread pool and a start latch release all calls together, with a 60 s timeout per future:
  - **a.** 8 different clients in one tenant get numbers exactly {1..8}; each CC equals its own payment.
  - **b.** 8 payments on the same honorário leave CC = the exact sum (no lost update), numbered 1..8.
  - **c.** The same-key race, repeated 5 times with fresh keys: 1 `t_pagamento` row and 1 `t_documento_fiscal` row per key, both callers see the same document and pagamento id, exactly one result is `novo`, and the CC is credited once.
  - **d.** 4 + 4 threads on two tenants number 1..4 each, independently.
  - **e.** An `@AfterEach` check: no recorded failure carries SQLState 40P01 or the word "deadlock" (every future also has to succeed).

## Verification

Docker was running and failsafe actually executed the ITs; there were no scratch substitutes.

| Run | Command | Result |
|-----|---------|--------|
| 1 | `-Dit.test=PagamentoFaturadoServiceIT verify` | 10 run, 0 failures, 0 errors, 0 skipped (13.9 s) |
| 1 | `-Dit.test=PagamentoFaturadoConcorrenciaIT verify` | 4 run, 0 failures, 0 skipped (9.6 s) |
| 2 | `-Dit.test=PagamentoFaturadoConcorrenciaIT verify` (flakiness check, back-to-back) | 4 run, 0 failures, 0 skipped (9.9 s) |

**Acceptance greps:**
- `104347.83` / `99130.43` / `SEGREDO-PROFISSIONAL` are present
- `void rollback` has 3 matches
- the `mesmaChave*` test counts the `t_pagamento` and per-key `t_documento_fiscal` rows equal to 1

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | e222d32 | FixturaEmissaoFiscal + PagamentoFaturadoServiceIT |
| 2 | 6c8019e | PagamentoFaturadoConcorrenciaIT |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Stubbing the spy through the transactional proxy hit MANDATORY**
- **Found during:** Task 1 (case c)
- **Issue:** `doThrow(...).when(auditoria).registarEmissao(...)` called the method through the Spring `@Transactional(MANDATORY)` proxy, which failed with `IllegalTransactionStateException` outside a transaction.
- **Fix:** Stub (and reset) the unwrapped spy obtained with `AopTestUtils.getUltimateTargetObject(auditoria)`. The service still calls through the proxy, so MANDATORY still runs inside the emission transaction.
- **Files modified:** backend/src/test/java/com/lexcv/services/fiscal/PagamentoFaturadoServiceIT.java
- **Commit:** e222d32

**2. [Rule 1 - Determinism] Fixed Clock instead of Clock.systemUTC()**
- **Found during:** Task 1
- **Issue:** With the system clock, the expected `SIM-FR-{ano}` and "hoje" depend on when the test runs. A run that straddles Cabo Verde midnight would also refuse with `DATA_EMISSAO_ALTERADA`.
- **Fix:** `Clock.fixed(2026-06-15T13:00Z)` in both ITs. The midnight behaviour itself is covered by the plan-06 Mockito tests.
- **Commit:** e222d32, 6c8019e

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: all 3 key files
- FOUND: e222d32, 6c8019e
