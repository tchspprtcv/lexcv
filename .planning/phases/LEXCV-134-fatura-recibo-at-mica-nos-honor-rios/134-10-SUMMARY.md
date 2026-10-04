---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 10
subsystem: backend-api
tags: [fiscal, efatura, controller, guards, locks, merge, concurrency, testcontainers, cfg-03]
requires:
  - "134-03: ClienteRepository/ProcessoRepository.bloquearPorIdETenant (PESSIMISTIC_WRITE, tenant-scoped)"
  - "134-05: DocumentoFiscalService.existeParaPagamento/Cliente/Processo/Honorario, repontarCliente (MANDATORY)"
  - "134-06/07: PagamentoFaturadoService.registar (lock order configuração → cliente → processo → CC → série), FixturaEmissaoFiscal"
  - "134-08: createPagamento delegation, ResourceController constructor with the two fiscal services"
provides:
  - "DELETE /pagamentos/{id}: 409 {message, code: PAGAMENTO_FATURADO} when the payment has a Fatura-Recibo (still non-transactional)"
  - "DELETE /honorarios/{id}: @Transactional, processo lock, 409 HONORARIO_COM_DOCUMENTOS_FISCAIS before the old pagamentos 409"
  - "DELETE /clientes/{id} and /processos/{id}: @Transactional, row lock is the first read, 409 CLIENTE_/PROCESSO_COM_DOCUMENTOS_FISCAIS"
  - "POST /clientes/merge: both clientes locked in ascending UUID order, repontarCliente before delete(secondary), response key moved_documentos_fiscais"
  - "FixturaEmissaoFiscal is now public (reusable outside com.lexcv.services.fiscal)"
affects: [134-11, 134-12, 134-14]
tech-stack:
  added: []
  patterns:
    - "Hand-built (unproxied) controller in a @DataJpaTest IT: each handler call wrapped in TransactionTemplate to reproduce its @Transactional boundary"
    - "Holding emission: registar inside an outer TransactionTemplate that only commits when a latch opens; blocking proven via pg_stat_activity wait_event_type = 'Lock'"
key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerDocumentoFiscalGuardasTest.java
    - backend/src/test/java/com/lexcv/controllers/GuardasDocumentoFiscalConcorrenciaIT.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/test/java/com/lexcv/services/fiscal/FixturaEmissaoFiscal.java
decisions:
  - "deletePagamento checks existeParaPagamento only after the existing 404/tenant checks, so a foreign id never reaches the fiscal lookup (no oracle)"
  - "deleteHonorario: if the processo lock comes back empty (deleted concurrently) it answers the same 404 'Honorário não encontrado'; the old pagamentos 409 is left as a plain return (nothing mutated before it)"
  - "mergeClientes takes both locks before checking either 404, then returns 'Cliente principal não encontrado' / 'Cliente duplicado não encontrado' exactly as before; the tenant check is now inside the lock query"
  - "IT (b) accepts 409 PROCESSO_ALTERADO_TENTE_NOVAMENTE or 404 from the emission, as the plan allows, and asserts the code whenever the status is 409"
  - "IT (e) adds a same-pair reversed merge (merge(S,P) racing merge(P,S)) on top of the plan's other-pair reversed merge -- the case that actually needs the ascending-UUID order"
  - "EMIS-09 not marked complete in REQUIREMENTS.md yet: backend enforcement is done, the inline 409 surface lands in plan 12 and phase verification in 14 (same convention as plans 06-09)"
metrics:
  duration: "~30 min"
  completed: 2026-10-04
  tasks: 2
  files: 4
---

# Phase 134 Plan 10: Fiscal-document delete guards and merge re-pointing Summary

Deleting a faturado payment, or a cliente, processo or honorário that has fiscal documents, now returns a 409 with the UI-SPEC Surface 5 copy. Each of these deletes takes the cliente/processo row lock before it checks, so it waits for an emission that is still running instead of racing it. A merge now locks both clientes in ascending UUID order and moves the documents' `cliente_id` to the surviving cliente. A real-PostgreSQL IT proves all of this against emissions that are mid-transaction.

## What was built

- **`ResourceController`** (no change to `createPagamento` or `registarPagamentoLegado`; the CFG-03 guard is unchanged and green):
  - **`deletePagamento`** still has no `@Transactional` (P-02; a comment explains why). After the existing 404 checks, a document gives 409 `PAGAMENTO_FATURADO`, "Este pagamento tem uma fatura-recibo emitida e não pode ser apagado." The CC and the payment are not touched.
  - **`deleteHonorario`** is now `@Transactional`. The existing 404 checks run first. Then `processoRepository.bloquearPorIdETenant` runs, only to serialize with emissions (comment explains the READ COMMITTED reasoning). Then 409 `HONORARIO_COM_DOCUMENTOS_FISCAIS` via `RecusaTransacional.recusar`, before the old "pagamentos registados" 409.
  - **`deleteCliente` / `deleteProcesso`** are now `@Transactional`. `bloquearPorIdETenant` replaces `findById` as the first read of the row (OSIV rule); an empty result keeps the old 404 message. Then a recused 409 `CLIENTE_/PROCESSO_COM_DOCUMENTOS_FISCAIS`. Otherwise the old delete runs (CC then cliente; processo) and returns 200.
  - **`mergeClientes`**: the two `findById` calls are replaced by two lock calls in ascending `UUID.compareTo` order. A comment explains why this rules out merge-vs-merge and merge-vs-emission deadlocks. `documentoFiscalService.repontarCliente(tenantId, secondary, primary)` runs right before `clienteRepository.delete(secondary)`, and the response gains `moved_documentos_fiscais`. Every other step and response key is unchanged.
  - Fiscal checks go only through `DocumentoFiscalService`; no fiscal repository appears in the controller.
- **`ResourceControllerDocumentoFiscalGuardasTest`** (17 Mockito tests): each guard with and without documents; 404 paths that never reach the lock or the fiscal lookup; `InOrder` checks for lock → exists → delete; the merge `InOrder` for both argument orders (smaller id locked first, then repontar, then delete); the merge 404s with no repontar; reflection on `@Transactional` and the unchanged `@PreAuthorize` values.
- **`GuardasDocumentoFiscalConcorrenciaIT`** (6 tests, postgres:16-alpine, 498 lines):
  - **a.** A held emission blocks `deleteCliente`, proven with `pg_stat_activity` and the future not done. After release: 409 `CLIENTE_COM_DOCUMENTOS_FISCAIS`, and the cliente and document survive with the right `cliente_id`.
  - **b.** A held `deleteCliente` blocks the emission. After release, the emission fails with `RecusaFiscalException` (409 `PROCESSO_ALTERADO_TENTE_NOVAMENTE` or 404). There are 0 pagamentos, documentos and linhas, the series is unchanged and the orphan query returns 0.
  - **c.** Same as (a) for `deleteProcesso`, giving 409 `PROCESSO_COM_DOCUMENTOS_FISCAIS`.
  - **d.** A merge waits for an emission on the secondary cliente. Afterwards `moved_documentos_fiscais = 1`, `cliente_id` = primary, and `adquirente_nome`/`adquirente_nif` are still the secondary's values from emission time.
  - **e.** 10 rounds, each with fresh data, run 5 operations concurrently: an emission on the secondary, merge(P,S), merge(S,P), merge(S2,P2) and a `deleteProcesso` on an unrelated processo. Every round finishes within 30 s, no failure carries `40P01`/deadlock, and the `NOT EXISTS` orphan query returns 0 after every round.
  - **f.** CFG-03: for a tenant with no configuração and one with `ativa=false`, `createPagamento` returns 201 with a `Pagamento`. There are 0 rows in `t_documento_fiscal`, `_linha`, `t_comunicacao_fiscal` and `t_serie_fiscal`, and the CC is credited 100.00.

## Verification

Docker was running and failsafe actually executed the IT.

| Run | Command | Result |
|-----|---------|--------|
| Task 1 RED | `-Dtest=ResourceControllerDocumentoFiscalGuardasTest` | 17 run, 13 failures (before the controller change) |
| Task 1 GREEN | `-Dtest='ResourceControllerDocumentoFiscalGuardasTest,FaturacaoDesligadaPagamentoInalteradoTest,ResourceControllerPagamentoTest,ResourceControllerListaPagamentosTest,ResourceControllerProveniencaPapelTest,ResourceControllerUploadDocumentoTest'` | 17 + 5 + 13 + 7 + 8 + 2, 0 failures |
| IT run 1 | `-Dit.test=GuardasDocumentoFiscalConcorrenciaIT verify` | 6 run, 0 failures, 0 errors, 0 skipped (10.2 s) |
| IT run 2 | full `mvn -Dmaven.compiler.release=21 verify` | surefire 800/800; failsafe 94/94, GuardasDocumentoFiscalConcorrenciaIT 6/6 (9.9 s); BUILD SUCCESS |
| SAST | `-DskipTests compile spotbugs:check` | passes |

**Acceptance greps:**
- `_COM_DOCUMENTOS_FISCAIS|PAGAMENTO_FATURADO` appears 4 times in the controller, and the exact cliente copy matches.
- `FaturacaoDesligadaPagamentoInalteradoTest.java` has no diff.
- The IT contains both `NOT EXISTS` and `40P01`.

**Note:** at the end of the full failsafe run, Surefire logged "going to kill self fork JVM ... 30 seconds after System.exit(0)" (the shared IT JVM had lingering non-daemon threads). The isolated run of this IT did not log it, and the build is SUCCESS. I did not investigate which IT leaves the threads.

## Commits

| Task | Phase | Commit | Description |
|------|-------|--------|-------------|
| 1 | RED | 6524fe9 | Failing guard/merge tests |
| 1 | GREEN | c098dab | Guards, locks and merge re-pointing in ResourceController |
| 2 | - | d5eae4d | GuardasDocumentoFiscalConcorrenciaIT + public fixture |

## TDD Gate Compliance

Task 1 has a `test(134-10)` commit (13/17 failing), then a `feat(134-10)` commit (17/17 green). No refactor commit was needed. Task 2 is not TDD: it proves Task 1's code under concurrency.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] FixturaEmissaoFiscal was package-private**
- **Found during:** Task 2
- **Issue:** The plan reuses the plan-07 fixture from an IT in `com.lexcv.controllers`, but the class, its constructor and its helpers were package-private in `com.lexcv.services.fiscal`.
- **Fix:** Made the class, its constructor, its constants and its helper methods `public`, and updated the javadoc. There is no behaviour change, and the plan-07 ITs are still green in the full failsafe run.
- **Files modified:** backend/src/test/java/com/lexcv/services/fiscal/FixturaEmissaoFiscal.java
- **Commit:** d5eae4d

## Known Stubs

None. The 409 codes are consumed by the web in plans 11 and 12.

## Self-Check: PASSED

- FOUND: ResourceController.java, ResourceControllerDocumentoFiscalGuardasTest.java, GuardasDocumentoFiscalConcorrenciaIT.java, FixturaEmissaoFiscal.java
- FOUND: 6524fe9, c098dab, d5eae4d
