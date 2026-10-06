---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 07
subsystem: backend-fiscal-read
tags: [fiscal, efatura, dto, multi-tenant, tdd]
requires: ["136-02"]
provides:
  - "ComunicacaoFiscalResumo record (estado, ambiente, iud, tentativas, ultimaTentativaEm, ultimoErro, proximaTentativaEm) with static de(ComunicacaoFiscal, iudOuNulo)"
  - "DocumentoFiscalDetalheResponse.comunicacao (last component) + factory overload de(d, linhas, estado, origem, ncs, resumo)"
  - "DocumentoFiscalService(…, DocumentoFiscalXmlRepository) — detalhe fills comunicacao with the tenant-scoped IUD"
affects: [136-04, 136-11, 136-14]
tech-stack:
  added: []
  patterns:
    - "Visibility rules computed in the DTO factory (ultimoErro only reprocessavel, proxima only PENDENTE) so the UI renders backend values only"
key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/ComunicacaoFiscalResumo.java
    - backend/src/test/java/com/lexcv/dtos/ComunicacaoFiscalResumoTest.java
  modified:
    - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalDetalheResponse.java
    - backend/src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java
    - backend/src/test/java/com/lexcv/services/fiscal/DocumentoFiscalServiceTest.java
decisions:
  - "The XML satellite is read only when a comunicação row exists (no row -> comunicacao null, no XML lookup)"
  - "estadoComunicacao stays on the detail for the list and older callers; the two Phase 134/135 factory overloads delegate with a null summary"
metrics:
  duration: "~20 min"
  completed: 2026-10-06
  tasks: 2
  files: 5
---

# Phase 136 Plan 07: Communication summary and IUD on the document detail Summary

`GET /documentos-fiscais/{id}` now returns a `comunicacao` object with these fields, all computed by the backend:

- `estado`
- `ambiente`
- `iud`
- `tentativas`
- `ultimaTentativaEm`
- `ultimoErro`
- `proximaTentativaEm`

The IUD comes from the XML satellite of the caller's tenant and is null until it is generated. `ultimoErro` appears only for `REJEITADO`/`ERRO` and `proximaTentativaEm` only for `PENDENTE`. When a document has no communication row, `comunicacao` is null. The list, its `estadoComunicacao` and the `estado` filter are unchanged. The controller's `EstadoComunicacaoFiscal.valueOf` already accepts the four states.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | ComunicacaoFiscalResumo DTO + detail component | 9a90717 (RED), fca4ebf (GREEN) |
| 2 | DocumentoFiscalService.detalhe reads the row and the tenant-scoped IUD | 64b020d (RED), 309450e (GREEN) |

## Verification

- **ComunicacaoFiscalResumoTest:** 6 tests, green.
  - `PENDENTE` shows the next attempt and hides the error.
  - `ERRO` and `REJEITADO` show the error, and `ERRO` hides the next attempt.
  - `ACEITE_SIMULADO` hides an error left over from an earlier attempt.
  - With no row the summary is null.
  - The new overload carries the summary, and both old overloads give null.
- **DocumentoFiscalNotaCreditoDtosTest:** still green.
- **DocumentoFiscalServiceTest:** 3 new detail cases.
  - The IUD comes from the XML row, looked up with `(tenant, documentId)`.
  - Without an XML row the IUD is null.
  - Without a communication row the summary and `estadoComunicacao` are null and the XML repository is never called.
  - The existing foreign-tenant 404 test now also checks that the XML repository is never called.
- **Unit suite and IT:** `mvn -Dmaven.compiler.release=21 verify -Dit.test=GuardasDocumentoFiscalConcorrenciaIT` passed.
  - Surefire: 1079 tests, 0 failures, 0 errors, 0 skipped. This includes DocumentoFiscalControllerTest and DocumentoFiscalControllerAutorizacaoTest.
  - Failsafe: 11 tests in GuardasDocumentoFiscalConcorrenciaIT, green. That IT imports DocumentoFiscalService and confirms the new dependency wires in.
- **SpotBugs:** `mvn -DskipTests compile spotbugs:check` is clean.
- **Acceptance greps:**
  - `ComunicacaoFiscalResumo comunicacao` appears once.
  - `reprocessavel()` appears once in the DTO.
  - `DocumentoFiscalXmlRepository` appears 3 times in the service.
  - `findByTenantIdAndDocumentoFiscalId(tenantId` appears twice.

## Deviations from Plan

None. The plan was executed as written. The new overload's parameter is named `resumoOuNulo` so that the acceptance grep `ComunicacaoFiscalResumo comunicacao` matches only the record component.

## TDD Gate Compliance

- **Task 1:** RED 9a90717 failed to compile because the DTO was missing. GREEN fca4ebf.
- **Task 2:** RED 64b020d failed to compile on the 5-argument constructor. GREEN 309450e.

## Threat Flags

None.
- **T-136-25:** mitigated. The XML lookup runs only after the tenant-scoped document fetch and uses the same tenant. A foreign id returns the same 404 and never touches the XML repository, and a test checks this.
- **T-136-26:** mitigated. The stored `ultimoErro` is shown only for `REJEITADO`/`ERRO`.
- **T-136-27:** accepted.

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/dtos/ComunicacaoFiscalResumo.java, backend/src/test/java/com/lexcv/dtos/ComunicacaoFiscalResumoTest.java
- FOUND: commits 9a90717, fca4ebf, 64b020d, 309450e
