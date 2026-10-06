---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 14
subsystem: backend-fiscal-reprocess
tags: [fiscal, efatura, reprocess, rbac, audit, multi-tenant, tdd]
requires: ["136-06", "136-12"]
provides:
  - "FilaComunicacaoFiscal.reporPendente(tenantId, documentoId, agora) (MANDATORY): conditional reset limited to ERRO/REJEITADO of the tenant"
  - "ReprocessamentoComunicacaoService.reprocessar(tenantId, autor, documentoId) @Transactional -> ReprocessarComunicacaoResponse(estado, tentativas)"
  - "AuditoriaFiscalService.registarReprocessamentoComunicacao (MANDATORY), ACAO_REPROCESSAR_COMUNICACAO = documento_fiscal_reprocessar_comunicacao"
  - "POST /api/v1/documentos-fiscais/{id}/comunicacao/reprocessar with hasAuthority('financeiro:edit') (7th handler)"
  - "EstadoEmissaoResponse.modoComunicacao (gateway ambiente, also when faturação is off) + comModo(String); 3-arg constructor kept"
affects: [136-15, 136-16]
tech-stack:
  added: []
  patterns:
    - "Conditional native UPDATE as the state guard (0 rows -> 409), audit event in the same transaction"
key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/ReprocessarComunicacaoResponse.java
    - backend/src/main/java/com/lexcv/services/fiscal/ReprocessamentoComunicacaoService.java
    - backend/src/test/java/com/lexcv/services/fiscal/ReprocessarComunicacaoIT.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/FilaComunicacaoFiscal.java
    - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
    - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
    - backend/src/main/java/com/lexcv/dtos/EstadoEmissaoResponse.java
    - backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerTest.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerAutorizacaoTest.java
decisions:
  - "Error code COMUNICACAO_ESTADO_INVALIDO (UI-SPEC Surface 2, the approved contract), message 'Só é possível reprocessar comunicações em erro ou rejeitadas.'"
  - "A document without a comunicação row is a 409 (nothing to reprocess), not a 404"
  - "The audit's estadoAnterior is read from ComunicacaoFiscalRepository before the conditional UPDATE, in the same transaction"
  - "modoComunicacao is set in the controller from EfaturaGateway.ambiente().name(); PreVisualizacaoFaturaService is untouched"
metrics:
  duration: "~35 min"
  completed: 2026-10-06
  tasks: 2
  files: 10
---

# Phase 136 Plan 14: Manual reprocess route and communication mode Summary

A user with exactly `financeiro:edit` can now reprocess a document whose communication is in ERRO or REJEITADO.

- **The reset:** one tenant-scoped, conditional `UPDATE`. The row goes back to PENDENTE, due now, with 0 attempts, the error, lease and completion cleared, `reprocessamentos + 1` (a new failure episode) and `versao + 1`.
- **Refusals:** any other state (or no communication row) is a 409 `COMUNICACAO_ESTADO_INVALIDO` and changes nothing. A malformed, missing or other-office id gets the same 404 body as the detail route.
- **Audit:** an event `documento_fiscal_reprocessar_comunicacao` is written in the same transaction, holding only the author's name, the document number and the previous state.
- **Never touched:** the document and its XML row. The next attempt reuses the stored XML (same IUD).
- **Mode for the banner:** `GET /faturacao/estado-emissao` now carries `modoComunicacao` from the running gateway (`SIMULADO`), also when faturação is off.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | reporPendente + ReprocessamentoComunicacaoService + audit event, proven on PostgreSQL | 6b05e57 (RED), 3b09ace (GREEN), ba2df81 (SpotBugs fix) |
| 2 | Route with exact financeiro:edit (7 handlers) and modoComunicacao on estado-emissao | ed4f7df (RED), e54ab03 (GREEN) |

## Verification

- **ReprocessarComunicacaoIT** (Testcontainers `postgres:16-alpine`, run by failsafe): 8 tests, 0 failures, 0 errors, 0 skipped.
  - ERRO (attempt 8, error, `concluido_em` set) returns `{PENDENTE, 0}`. The row is checked column by column: PENDENTE, 0 attempts, next attempt = now, the error, code, lease and `concluido_em` all NULL, `reprocessamentos` 1, `versao` 4 → 5.
  - One audit row with entity `documento_fiscal`, the document id, the author id, and exactly the keys `autorNome`, `numeroFormatado` and `estadoAnterior` (= ERRO).
  - REJEITADO is reset the same way.
  - Calling again right away (now PENDENTE) gives 409, the row is unchanged and there is still one event. ACEITE_SIMULADO and a missing row also give 409 with no event.
  - Another tenant's document gives 404 `DOCUMENTO_FISCAL_NAO_ENCONTRADO`; that tenant's row is unchanged and neither tenant gets an event. An unknown id also gives 404.
  - The document and XML rows (number, total, IUD, xml, sha, counts) are identical before and after.
- **FilaComunicacaoFiscalIT:** 13 tests, still green.
- **AuditoriaFiscalServiceTest:** 19 tests, green. It now expects 8 `registar*` methods, all MANDATORY, and pins the new event's keys and its MANDATORY propagation.
- **DocumentoFiscalControllerTest:** 31 tests, green. `exatamenteSeteHandlersSemRotasQueAlterem` (7, still no PUT/PATCH/DELETE), the route and gate pins, delegation with the principal's tenant and principal, the shared 404 for a non-UUID id with no service call, refusal propagation, and `modoComunicacao` = SIMULADO both when active and when off.
- **DocumentoFiscalControllerAutorizacaoTest:** 31 tests, green, through the real `preAuthorize()` interceptor. `{financeiro:edit}` is allowed. `{financeiro:manage}`, `{financeiro:view}`, `{financeiro:view, financeiro:manage}`, `{ROLE_financeiro:edit}` and none are refused, and the service is never called.
- **Also run:** PreVisualizacaoFaturaServiceTest (11), FaturacaoControllerTest (13) and FaturacaoControllerAutorizacaoTest (8) are green. The full unit suite ran 1201 tests with 0 failures. `mvn -DskipTests compile spotbugs:check` is clean with no new exclusion.
- **Acceptance greps:**
  - `estado IN ('ERRO', 'REJEITADO')` appears once in FilaComunicacaoFiscal.
  - `COMUNICACAO_ESTADO_INVALIDO` appears once in the service.
  - `documento_fiscal_reprocessar_comunicacao` appears once in AuditoriaFiscalService.
  - `hasAuthority('financeiro:edit')")` appears twice and `comunicacao/reprocessar` once in the controller.
  - `assertEquals(7` appears once and `assertEquals(6, hs.size()` 0 times in the controller test.
  - `modoComunicacao` is in EstadoEmissaoResponse.

## Deviations from Plan

**1. [Rule 1 - Bug] SpotBugs EI_EXPOSE_REP2 on the hand-written constructor**
- **Found during:** the post-task SpotBugs run.
- **Issue:** the explicit constructor of `ReprocessamentoComunicacaoService` storing `AuditoriaFiscalService` was flagged.
- **Fix:** `@RequiredArgsConstructor`, the same pattern as `NotaCreditoService`. There is no new exclusion.
- **Commit:** ba2df81.

**2. [Rule 3 - Acceptance] Javadoc wording kept the literal greps exact**
- The controller Javadoc points to `{@link #reprocessarComunicacao}`, so `comunicacao/reprocessar` appears only in the mapping.
- The audit Javadoc points to `{@link #ACAO_REPROCESSAR_COMUNICACAO}` rather than repeating the action literal.

**3. [Rule 2 - Correctness] Missing communication row**
- A document with no communication row is a 409, as the plan says, and the IT covers it.
- The authorization test stubs `estadoEmissao` and the gateway, because the controller now calls `comModo` on the service result.

**Note:** `Propagation.MANDATORY` now appears 3 times in `FilaComunicacaoFiscal`. The 136-06 grep expected 2, which was true at the time; the third is the new MANDATORY method.

## TDD Gate Compliance

- **Task 1:** RED 6b05e57 failed to compile because the service, DTO and audit method were missing. GREEN 3b09ace.
- **Task 2:** RED ed4f7df failed to compile because of the 5-argument constructor and the missing handler. GREEN e54ab03.

## Notes for downstream plans

- **136-15:** to emulate a reprocess in the IT, call `FilaComunicacaoFiscal.reporPendente(tenantId, docId, now)` inside a `TransactionTemplate` (it is MANDATORY).
- **136-16:** in the live UAT, the reprocess button calls `POST /api/v1/documentos-fiscais/{id}/comunicacao/reprocessar`, and the banner reads `modoComunicacao` from estado-emissao.

## Threat Flags

None.
- T-136-53: exact authority, with the auth matrix tested.
- T-136-54: tenant from the principal, `findByIdAndTenantId`, `UPDATE ... WHERE tenant_id`, and an IT proving another tenant's row is untouched.
- T-136-55: MANDATORY audit in the same transaction.
- T-136-56: the conditional UPDATE; the document and XML row are unchanged.
- T-136-57: the shared 404 for a non-UUID id.

## Self-Check: PASSED

- FOUND: all 3 created and 7 modified files listed in key-files
- FOUND: commits 6b05e57, 3b09ace, ba2df81, ed4f7df, e54ab03
