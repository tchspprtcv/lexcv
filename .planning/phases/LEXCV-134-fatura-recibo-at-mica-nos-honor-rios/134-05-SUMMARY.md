---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 05
subsystem: backend-fiscal
tags: [fiscal, efatura, read-side, dto, tdd, merge, guards]
requires:
  - "134-03: DocumentoFiscalRepository.buscar/findBy*/exists*, DocumentoFiscalLinhaRepository, ComunicacaoFiscalRepository, DocumentoFiscalLigacaoClienteRepository"
  - "134-01: DocumentoFiscal/DocumentoFiscalLinha getters, EstadoComunicacaoFiscal"
  - "134-02: MetodoPagamento.porNome/rotulo"
provides:
  - "DocumentoFiscalService.listar(tenantId, clienteId, TipoDocumentoFiscal, EstadoComunicacaoFiscal, de, ate, page, size) -> Page<DocumentoFiscalResumoResponse>"
  - "DocumentoFiscalService.detalhe(tenantId, id) -> DocumentoFiscalDetalheResponse (404 DOCUMENTO_FISCAL_NAO_ENCONTRADO)"
  - "DocumentoFiscalService.referenciasPorPagamento(tenantId, Collection<Integer>) -> Map<Integer, DocumentoFiscalRef>"
  - "DocumentoFiscalService.existeParaPagamento/Cliente/Processo/Honorario(tenantId, id)"
  - "DocumentoFiscalService.repontarCliente(tenantId, antigo, novo) with Propagation.MANDATORY"
  - "DTOs: DocumentoFiscalRef, PagamentoComDocumentoResponse (superset of Pagamento JSON), DocumentoFiscalResumoResponse, DocumentoFiscalDetalheResponse (+ nested Linha)"
affects: [134-08, 134-09, 134-10, 134-11, 134-12, 134-13]
tech-stack:
  added: []
  patterns:
    - "Single service door from ResourceController to fiscal data (keeps the CFG-03 source guard simple)"
    - "One batch satellite lookup per page (findByTenantIdAndDocumentoFiscalIdIn) instead of N+1"
key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalRef.java
    - backend/src/main/java/com/lexcv/dtos/PagamentoComDocumentoResponse.java
    - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalResumoResponse.java
    - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalDetalheResponse.java
    - backend/src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java
    - backend/src/test/java/com/lexcv/services/fiscal/DocumentoFiscalServiceTest.java
  modified:
    - backend/src/test/java/com/lexcv/services/fiscal/ConfiguracaoFiscalServiceTest.java
decisions:
  - "Every public DocumentoFiscalService method is @Transactional (reads readOnly, repontarCliente MANDATORY) and takes tenantId first; both are pinned by reflection in DocumentoFiscalServiceTest"
  - "DocumentoFiscalDetalheResponse.emitenteRegimeIva is the enum name as a String; metodoPagamentoRotulo falls back to the raw stored value when the name is unknown"
  - "The detail record copies linhas with List.copyOf in a compact constructor (WorkflowResponse idiom) to satisfy SpotBugs EI_EXPOSE_REP"
metrics:
  duration: "~20 min"
  completed: 2026-10-04
  tasks: 2
  files: 7
---

# Phase 134 Plan 05: Fiscal document read side Summary

This plan adds one tenant-scoped `DocumentoFiscalService`. It lists documents, using a single batch lookup of communication states per page, and details them with a 404 for foreign ids. It maps payments to their Fatura-Recibo in one query, answers the delete-guard "has documents?" questions, and re-points documents on client merge, only inside an existing transaction. The read DTOs never expose the idempotency key.

## What was built

- **DTOs** (`com.lexcv.dtos`):
  - `DocumentoFiscalRef(id, numeroFormatado)`
  - `PagamentoComDocumentoResponse`: components in the order `id, honorarioId, valorPago, dataPagamento, metodo, documentoFiscal`. This is a superset of the `Pagamento` entity's JSON, so the frontend type keeps working; `documentoFiscal` is null for legacy payments (EMIS-12).
  - `DocumentoFiscalResumoResponse`: the listing row, with `tipo`, `ambiente` and `estadoComunicacao` as enum names.
  - `DocumentoFiscalDetalheResponse` with a nested `Linha`: the emitente/adquirente snapshot, values, lines, estado, and links to cliente/processo/honorário/pagamento. It deliberately excludes `chaveIdempotencia` and `emitidoPorId`; only the snapshotted name is returned.
- **`DocumentoFiscalService`**:
  - `listar` passes filters to `buscar` as text or null, with an unsorted `PageRequest` (the native query owns the order). It fetches states with one `findByTenantIdAndDocumentoFiscalIdIn`, skipped for an empty page, and keeps page order and totals. As a defensive check it throws `IllegalArgumentException` for page < 0 or size outside 1..100; the controller returns 400 earlier (plan 09).
  - `detalhe` returns 404 `DOCUMENTO_FISCAL_NAO_ENCONTRADO` ("Documento fiscal não encontrado.") for foreign or missing ids; otherwise it loads lines and estado with the same tenant.
  - `referenciasPorPagamento` makes no call for null or empty ids; otherwise one query, returning a pagamentoId → ref map.
  - `existeParaPagamento`, `existeParaCliente`, `existeParaProcesso` and `existeParaHonorario` delegate to the tenant-scoped `exists*`.
  - `repontarCliente` delegates to the pinned native UPDATE, under `@Transactional(propagation = MANDATORY)`.
  - It never reads `SecurityContextHolder`.

## Verification

- `DocumentoFiscalServiceTest` 14/14:
  - filter mapping and unsorted page
  - null filters
  - `verify(times(1))` on the batch estado lookup, plus order and totals
  - an empty page makes no estado call
  - pagination bounds
  - 404 code and message
  - detail loads lines and estado with the same tenant
  - DTO components exclude the key and `emitidoPorId`
  - `verifyNoInteractions` when there are no pagamento ids
  - batch reference mapping
  - `existePara*` delegation
  - `repontarCliente` delegates under `MANDATORY`
  - every public method takes tenantId first and is transactional (reads readOnly)
  - no `SecurityContextHolder` in the source
- Full unit suite: `mvn -Dmaven.compiler.release=21 test` gives 677 tests, 0 failures (after deviation 2).
- `compile spotbugs:check` passes.
- **Acceptance greps:** `chaveIdempotencia|emitidoPorId` appears only in javadoc of the two DTOs; 0 `SecurityContextHolder`; 1 `Propagation.MANDATORY`.

## Commits

| Task | Phase | Commit | Description |
|------|-------|--------|-------------|
| 1 | - | d92606d | Read DTOs |
| 2 | RED | 9ed3439 | Failing DocumentoFiscalServiceTest |
| 2 | GREEN | 5b399aa | DocumentoFiscalService (+ immutable linhas copy) |
| fix | - | d4053de | Phase 133 repository pin accepts ativaPorTenant |

## TDD Gate Compliance

Task 2 (`tdd="true"`) has a `test(134-05)` commit first, while the test failed to compile against the missing service. A `feat(134-05)` commit followed with 14/14 green. Task 1 is not TDD.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] SpotBugs EI_EXPOSE_REP on DocumentoFiscalDetalheResponse.linhas**
- **Found during:** Task 2 (`spotbugs:check`)
- **Issue:** The record stored and returned the caller's mutable `List<Linha>`.
- **Fix:** Added a compact constructor `linhas = linhas == null ? List.of() : List.copyOf(linhas)`, the same idiom as `WorkflowResponse`/`ConflictCheckResponse`.
- **Files modified:** backend/src/main/java/com/lexcv/dtos/DocumentoFiscalDetalheResponse.java
- **Commit:** 5b399aa

**2. [Rule 1 - Regression from 134-03] Phase 133 pin on ConfiguracaoFiscalRepository methods**
- **Found during:** full unit-suite regression run after Task 2
- **Issue:** `ConfiguracaoFiscalServiceTest.guardarNaoConsultaONifDeOutrosEscritorios` asserts the repository declares only `findByTenantId` and `bloquearPorTenant`. Plan 134-03 added the tenant-scoped scalar `ativaPorTenant`, so the pin failed.
- **Fix:** Added `ativaPorTenant` to the allowed set deliberately, with a comment. The pin's intent (no cross-tenant finder) is unchanged.
- **Files modified:** backend/src/test/java/com/lexcv/services/fiscal/ConfiguracaoFiscalServiceTest.java
- **Commit:** d4053de

## Known Stubs

None. No controller exposes these methods yet; plans 08–10 wire them, as planned.

## Self-Check: PASSED

- FOUND: all 7 key files
- FOUND: d92606d, 9ed3439, 5b399aa, d4053de
