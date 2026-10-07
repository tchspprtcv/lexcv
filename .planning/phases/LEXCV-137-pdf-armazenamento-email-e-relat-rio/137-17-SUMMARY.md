---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 17
subsystem: backend/fiscal delivery hook
tags: [outbox, atomic-enqueue, pdf, background, payment-isolation]
requires: [137-05, 137-07, 137-10]
provides:
  - EnfileiramentoEntregaEmail.enfileirarAposAceite(tenantId, documentoId, agora) (Propagation.MANDATORY)
  - ComunicacaoFiscalTransacoes.registarResultado enqueues on ACEITE_SIMULADO (same tx, linhas == 1)
  - ProcessadorComunicacaoFiscal calls PdfDocumentoFiscalService.garantirPdfSilencioso after the accepted commit
  - EntregaForaDaTransacaoDoPagamentoTest (source gate)
affects: [137-19 (EmailFiscalOutboxJob claims the PENDENTE rows created here), 137-14 processor consumes them]
tech-stack:
  added: []
  patterns: [atomic outbox enqueue inside the result transaction, best-effort post-commit side effect]
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/EnfileiramentoEntregaEmail.java
    - backend/src/test/java/com/lexcv/services/fiscal/EntregaForaDaTransacaoDoPagamentoTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/ComunicacaoFiscalTransacoesTest.java
  modified:
    - backend/src/main/java/com/lexcv/services/fiscal/ComunicacaoFiscalTransacoes.java
    - backend/src/main/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscal.java
    - backend/src/test/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscalTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscalPipelineTest.java
    - backend/src/test/java/com/lexcv/jobs/FiscalOutboxJobIT.java
    - backend/src/test/java/com/lexcv/jobs/FiscalOutboxJobFalhasForcadasIT.java
    - backend/src/test/java/com/lexcv/services/fiscal/FilaComunicacaoFiscalIT.java
decisions:
  - "Recipient source (deliberate deviation from CONTEXT's 'snapshot first' order): the recipient comes only from the client record (Cliente.email, read via findById(...).filter(tenantId) and validated with RegrasEntregaEmail.emailValido), because DocumentoFiscal has no buyer-email snapshot column"
  - "No configuration row, or envioEmailAutomatico not TRUE -> DESLIGADO (never claimed); a document missing in the tenant -> WARN with ids only and no row"
  - "PdfDocumentoFiscalService is appended as the LAST constructor parameter of ProcessadorComunicacaoFiscal (both ctors), and EnfileiramentoEntregaEmail as the last of ComunicacaoFiscalTransacoes"
  - "The PDF hook runs after the ERRO-notification branch, only for ACEITE_SIMULADO with linhas == 1, wrapped in try/catch(RuntimeException) that logs the class name only (garantirPdfSilencioso already never throws; this is defensive)"
metrics:
  duration: ~35min
  completed: 2026-10-07
  tasks: 3
  files: 10
---

# Phase 137 Plan 17: Enqueue email and generate PDF after ACEITE_SIMULADO Summary

Delivery hooks into the Phase 136 flow at exactly one point.

**Enqueue.** When `ComunicacaoFiscalTransacoes.registarResultado` writes `ACEITE_SIMULADO` and the versao-guarded update returns 1, it calls `EnfileiramentoEntregaEmail.enfileirarAposAceite` in the same transaction. That is an atomic outbox: the result and the delivery row commit together or not at all. The row is created insert-if-absent, so there is one per document:
- `DESLIGADO` when the office toggle is off or no configuration exists;
- `SEM_EMAIL` when the client has no valid email;
- `PENDENTE` with the client's email otherwise.

**PDF.** After that commit, `ProcessadorComunicacaoFiscal` calls `garantirPdfSilencioso` outside any transaction, best effort. A PDF failure never changes the communication outcome.

**Untouched paths.** `PagamentoFaturadoService`, `NotaCreditoService` and `ResourceController` are unchanged. A source gate proves they reference no delivery or PDF class.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | EnfileiramentoEntregaEmail + payment-path source gate | RED 470413d, GREEN c6d3460 |
| 2 | Hooks in ComunicacaoFiscalTransacoes + ProcessadorComunicacaoFiscal | RED 81bacf4, GREEN a906707 |
| 3 | Adapt the Phase 136 ITs and assert enqueue-once | de7a746 |

## Verification

- `EntregaForaDaTransacaoDoPagamentoTest`: **2/2**. No forbidden token appears in the three payment-path sources, and `enfileirarAposAceite` is MANDATORY. `FaturacaoDesligadaPagamentoInalteradoTest` (CFG-03): **5/5**.
- `ComunicacaoFiscalTransacoesTest` (new): **3/3**. ACEITE with 1 row enqueues once with the claimed tenant, the document and `agora`. 0 rows does not enqueue, and neither do PENDENTE, REJEITADO or ERRO.
- `ProcessadorComunicacaoFiscalTest`: **28/28** (25 existing + 3 new). The new cases:
  - the PDF is called once after the ACEITE register;
  - it is not called after transient, ERRO, REJEITADO or a lost lease;
  - a throwing PDF changes nothing and does not propagate.
- `ProcessadorComunicacaoFiscalPipelineTest` **4/4**, `FiscalOutboxJobTest` **9/9**, `EstadoComunicacaoMapperTest` **24/24**. `compile spotbugs:check` is clean.
- ITs (Testcontainers PostgreSQL 16): **54 tests, 0 failures, 0 errors** across `FiscalOutboxJobIT` 7, `FiscalOutboxJobFalhasForcadasIT` 1, `FilaComunicacaoFiscalIT` 16, `ReprocessarComunicacaoIT` 8, `PagamentoFaturadoServiceIT` 11 and `NotaCreditoServiceIT` 11. New assertions:
  - FR happy path: exactly one `DESLIGADO` delivery row (the fixture toggle is off) and one `garantirPdfSilencioso(tenant, fr)` call.
  - Reprocess path: the NC accepted after the reprocess has exactly one row. A forced second `ACEITE_SIMULADO` of the FR (communication reset to PENDENTE through JDBC, job run again) still leaves exactly one row.
- Full backend `mvn verify` after this plan, covering everything from 137-13 to 137-17: surefire **1523 tests**, failsafe **196 ITs**, 0 failures, 0 errors, BUILD SUCCESS.

## Deviations from Plan

1. **Recipient source.** The recipient comes only from the client record, not "snapshot first" (see decisions). This was required by the plan's interface note and is recorded here as instructed.
2. **Extra test file `ComunicacaoFiscalTransacoesTest`.** The plan's Task 2 behaviour for `registarResultado` had no unit-test home among the listed files.
3. **`ReprocessarComunicacaoIT` needed no change.** Its `@Import` has neither `ComunicacaoFiscalTransacoes` nor `ProcessadorComunicacaoFiscal`. It ran green unchanged (8/8).
4. **Second-ACEITE scenario.** It is forced with a direct JDBC reset of the FR communication. `reporPendente` only accepts ERRO/REJEITADO, so a real reprocess can never re-accept an already accepted document.

## Threat Flags

None. T-137-70..74 are mitigated as planned.

## Self-Check: PASSED
