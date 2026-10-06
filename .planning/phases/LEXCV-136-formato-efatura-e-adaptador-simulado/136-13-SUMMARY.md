---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 13
subsystem: backend-fiscal-outbox
tags: [fiscal, efatura, outbox, pipeline, notification, tdd]
requires: ["136-03", "136-06", "136-09", "136-10", "136-12"]
provides:
  - "ProcessadorComunicacaoFiscal (@Service, no transaction annotations): processar(ComunicacaoReclamada), never throws"
  - "Fixed codes/messages: ORIGEM_SEM_IUD, DOCUMENTO_INEXISTENTE, IUD_COLISAO, FALHA_INTERNA (+ validator code with 'O documento não cumpre o formato eFatura (linha n).', RecusaFormatoEfatura codes)"
affects: [136-15, 136-16]
tech-stack:
  added: []
  patterns:
    - "Three catch (Throwable) layers: computation -> FALHA_INTERNA transient, result recording, notification"
    - "Notification only when registarResultado returned 1 row and the state is ERRO (after the result commit)"
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscal.java
    - backend/src/test/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscalTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/ProcessadorComunicacaoFiscalPipelineTest.java
  modified: []
decisions:
  - "An invalid XML is REJEITADO with the validator's own code (XSD_INVALIDO, or XML_PROIBIDO/XML_ILEGIVEL), with the same fixed message as SimuladoEfaturaGateway"
  - "A RecusaFormatoEfatura with ORIGEM_SEM_IUD (defensive; the processor checks iudOrigem first) is transient, every other refusal code is REJEITADO"
  - "The IUD and the XML row use the document's ambiente; the request to the gateway uses the claimed row's ambiente (both SIMULADO)"
  - "If the document number is unknown when ERRO is reached (failure before the snapshot loaded), the notification step re-reads the snapshot and falls back to the document id"
  - "Backoff uses max(1, tentativas) so a malformed claim never makes atraso throw"
metrics:
  duration: "~25 min"
  completed: 2026-10-06
  tasks: 2
  files: 3
---

# Phase 136 Plan 13: Per-item outbox pipeline Summary

`ProcessadorComunicacaoFiscal.processar` turns one claimed communication row into an IUD, a validated DFE XML, a gateway result and a recorded state.

- **Steps:** load the tenant-scoped snapshot. Reuse the stored XML row if there is one. Otherwise generate the IUD, build the `Dfe`, marshal it and validate it against the XSD. Only valid XML is written (insert-only). The stored row's IUD and XML are then sent through the single `EfaturaGateway`, and `EstadoComunicacaoMapper.estadoPara` decides the state. A PENDENTE state gets `now + BackoffComunicacao.atraso(tentativas)`.
- **NC before its FR:** an NC whose FR has no IUD yet is checked before `DocumentoComunicavel.de` and becomes the transient `ORIGEM_SEM_IUD`. It is retried with backoff and is never an invalid document.
- **Failures:** format refusals (firma > 150, XSD) become REJEITADO with fixed codes, and no XML row is written. Unexpected exceptions, including JVM `Error`s, become the transient `FALHA_INTERNA`. Exception text goes only to the server log, never to the database.
- **Notification:** when a row becomes ERRO and `registarResultado` updated exactly 1 row, the office's `financeiro:manage` holders are notified with the episode (`reprocessamentos`). A lost lease notifies nobody, and a notification failure is logged and never reverts or blocks anything.
- **No transaction here:** the processor has no transaction annotation. Every transaction lives in `ComunicacaoFiscalTransacoes`.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | ProcessadorComunicacaoFiscal with mock-based behaviour tests | 3d89fe8 (RED), 6f087d8 (GREEN) |
| 2 | Pipeline test with the real format components and simulated gateway | 8a151d1 |

## Verification

- **ProcessadorComunicacaoFiscalTest:** 15 tests, green. One per behaviour bullet:
  - FR happy path: the IUD comes from `(3, dataEmissao, NIF, 99999, 2, numero)`; `gravarXml` gets the marshalled string and its 64-char lower-case SHA-256; the gateway gets the *stored* row's IUD and XML; ACEITE_SIMULADO is recorded with null code, message and next attempt; no notification.
  - Existing XML row is reused: no IUD, builder or marshaller call.
  - NC without the FR IUD: PENDENTE `ORIGEM_SEM_IUD`, next attempt now + 30 s.
  - ErroTransitorio at attempt 8: ERRO, next attempt null, one notification with episode 2.
  - Lease lost (0 rows): no notification. A throwing notification does not propagate.
  - `FIRMA_EXCEDE_150`: REJEITADO, no XML write and no gateway call. Invalid XSD: REJEITADO `XSD_INVALIDO` "(linha 12)", same.
  - IUD collision: PENDENTE `IUD_COLISAO`, now + 2 min at attempt 2.
  - A gateway `RuntimeException("Ana Lopes 512345670")`: `FALHA_INTERNA` with the fixed message, which contains neither "Ana" nor "512345670". At attempt 8 it is ERRO and notifies.
  - Missing snapshot: REJEITADO `DOCUMENTO_INEXISTENTE`.
  - `registarResultado` throwing: `processar` returns normally and notifies nobody. A `StackOverflowError` is also contained.
  - Reflection: no transaction annotation on the class or its methods.
- **ProcessadorComunicacaoFiscalPipelineTest:** 4 tests, green. Only the transactions and the notification are mocked. The test uses `new SimuladoEfaturaGateway(...)` and a real `DfeValidador`.
  - FR (NORMAL + IR 20%) reaches ACEITE_SIMULADO. The captured XML validates, its IUD starts with "CV3" and is 45 characters long, `Dfe/@Id` is that IUD and `RepositoryCode` is 3.
  - The NC, given the FR IUD from the first run, reaches ACEITE_SIMULADO, and `References/Reference/FiscalDocument` equals that IUD.
  - `SEMPRE_TRANSITORIA` at attempt 8: ERRO, notification with episode 1.
  - A 151-character firma: REJEITADO `FIRMA_EXCEDE_150`, no `gravarXml`.
- **Also run:** DfeXmlBuilderTest (13) and SimuladoEfaturaGatewayTest (9) are green. `mvn -DskipTests compile spotbugs:check` is clean with no new exclusion.
- **Acceptance greps:** `@Transactional` 0, `getMessage()` 0, `catch (Throwable` 4.

## Deviations from Plan

**1. [Rule 3 - Acceptance] Javadoc wording**
- The Javadoc first said "SEM `@Transactional`", which made the `@Transactional` grep count 1. It now says "Sem anotações de transação". This was fixed before the GREEN commit (6f087d8).

**2. [Rule 2 - Robustness] Extra safety nets**
- A `StackOverflowError` test was added, because the plan's bullets only covered `RuntimeException`.
- The notification falls back to re-reading the document number when the failure happened before the snapshot loaded, so an ERRO reached through `FALHA_INTERNA` still notifies with a real number.
- The validator's own code is stored rather than always `XSD_INVALIDO` (consistent with 136-10).

The pipeline test (Task 2) passed on its first run: Task 1 had already implemented the behaviour, so no production fix was needed. The plan used a seeded `IudGerador`; its seeding constructor is package-private in `com.lexcv.fiscal.efatura`, so the test uses the public `SecureRandom` constructor. The assertions do not depend on the random part.

## TDD Gate Compliance

- **Task 1:** RED 3d89fe8 failed to compile because `ProcessadorComunicacaoFiscal` was missing. GREEN 6f087d8.
- **Task 2:** test-only, written against the existing production code. It passed at once (see above).

## Notes for downstream plans

- **136-15:** the job only needs `transacoes.reclamar(lote, lease)` and then `processador.processar(item)` per item. `processar` never throws, but keep the per-item `catch (Throwable)` in the job.
- **136-15 IT:** an NC whose FR has no XML row and attempt 7 forced: the claim makes it 8, which gives ERRO `ORIGEM_SEM_IUD` and notifies with episode = `reprocessamentos`.

## Threat Flags

None.
- T-136-48: fixed codes only, no `getMessage()`, with a PII-injection test.
- T-136-49: single mapper, simulated gateway.
- T-136-50: `gravarXml` only after `validar().valido()`, with tests.
- T-136-51: three `catch (Throwable)` layers.
- T-136-52: every transaction call takes `item.tenantId()`.

## Self-Check: PASSED

- FOUND: all 3 files listed in key-files
- FOUND: commits 3d89fe8, 6f087d8, 8a151d1
