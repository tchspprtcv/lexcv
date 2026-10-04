---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 08
subsystem: backend-api
tags: [fiscal, efatura, controller, cfg-03, guard, sha256, payments]
requires:
  - "134-06: PagamentoFaturadoService.faturacaoAtiva / registar, ResultadoPagamentoFaturado"
  - "134-05: DocumentoFiscalService.referenciasPorPagamento, PagamentoComDocumentoResponse, DocumentoFiscalRef"
  - "134-04: PagamentoRequest.paraPagamentoLegado"
provides:
  - "POST /api/v1/pagamentos takes PagamentoRequest; billing on -> PagamentoFaturadoService (201 new / 200 repeated key); billing off -> private registarPagamentoLegado(Pagamento) (verbatim legacy body)"
  - "GET /api/v1/honorarios/{id}/pagamentos returns List<PagamentoComDocumentoResponse> (documentoFiscal null for legacy payments)"
  - "Evolved CFG-03 guard: HASH_CORPO_LEGADO = SHA-256 of the normalized legacy body at 09aa999"
affects: [134-10, 134-11, 134-12]
tech-stack:
  added: []
  patterns:
    - "Source-hash pin of a moved method body (brace matching + whitespace normalization + SHA-256) to prove a branch is textually unchanged"
key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerPagamentoTest.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerListaPagamentosTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - backend/src/test/java/com/lexcv/controllers/FaturacaoDesligadaPagamentoInalteradoTest.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerProveniencaPapelTest.java
    - backend/src/test/java/com/lexcv/controllers/ResourceControllerUploadDocumentoTest.java
decisions:
  - "HASH_CORPO_LEGADO = 3f84b99944c79d0056c0a801d2da18659c3bd6cd09ff8935901499d343041908, computed from git show 09aa999 (createPagamento body unchanged since Phase 133); the new registarPagamentoLegado body hashes to the same value"
  - "ResourceController javadoc avoids naming the guard test class (it contains 'Faturacao', which the guard counts) -- it says 'teste-guarda CFG-03'"
  - "ResourceControllerPagamentoTest.novoController is a static, package-visible builder reused by ResourceControllerListaPagamentosTest"
  - "Requirements EMIS-01/07/12 are not marked complete yet: the payment form, dialog and 'Documento fiscal' column arrive in plans 11-12"
metrics:
  duration: "~25 min"
  completed: 2026-10-04
  tasks: 2
  files: 6
---

# Phase 134 Plan 08: Payment endpoint delegation and payments list Summary

`POST /api/v1/pagamentos` now has a single billing check. With billing on, it delegates to `PagamentoFaturadoService`: 201 for a new emission, 200 for a repeated idempotency key. With billing off, it runs the old body, moved verbatim into `registarPagamentoLegado` and pinned by SHA-256. The CFG-03 guard was evolved deliberately, not relaxed. The honorário payments list now carries each payment's Fatura-Recibo reference, or null.

## What was built

- **Evolved `FaturacaoDesligadaPagamentoInalteradoTest`** (5 tests; not `@Disabled`). Its javadoc explains the Phase 134 change. It checks five things:
  1. `Pagamento.java` stays free of `ConfiguracaoFiscal`, `NumeracaoService`, `SerieFiscal`, `faturacao`, `Faturacao`, `Fiscal`, `chave` and `retencao`.
  2. `ResourceController.java` never mentions `ConfiguracaoFiscal`, `NumeracaoService`, `SerieFiscal(Repository)`, `DocumentoFiscalRepository` or `ComunicacaoFiscal`.
  3. There is exactly 1 `faturacao`, 0 `Faturacao` and 1 `pagamentoFaturadoService.faturacaoAtiva(`, inside the body of `createPagamento`, and that body calls `registarPagamentoLegado(`.
  4. `corpoDoMetodo` (brace matching) + `\s+` → `" "` + `MessageDigest.getInstance("SHA-256")` on `registarPagamentoLegado` gives `HASH_CORPO_LEGADO`, the hash of the HEAD body.
  5. By reflection, neither method is `@Transactional`, and `createPagamento` keeps `hasAuthority('financeiro:edit')`.
- **`ResourceController`**:
  - New fields `pagamentoFaturadoService` and `documentoFiscalService` after `resolucaoPapeisService`, plus `principalAtual()`.
  - `createPagamento(@RequestBody PagamentoRequest req)` either delegates or returns `registarPagamentoLegado(req.paraPagamentoLegado())`.
  - The old body (including its comments, `setId(null)`, the swallowed `DataAccessException` and the 201 entity) is unchanged inside `private registarPagamentoLegado(Pagamento pag)`.
  - Neither method is `@Transactional` (P-02).
  - `listHonorarioPagamentos` keeps its 404 checks and gate. It then makes one `referenciasPorPagamento(getTenantId(), ids)` call and maps each payment with `PagamentoComDocumentoResponse.de(p, refs.get(p.getId()))`.
- **Constructor tests**: `ResourceControllerProveniencaPapelTest` and both `ResourceControllerUploadDocumentoTest` call sites pass two extra mocks.

## Verification

- `FaturacaoDesligadaPagamentoInalteradoTest` is 5/5. Before the controller change, 3 of its 5 tests failed (RED commit).
- `ResourceControllerPagamentoTest` 13/13:
  - **Billing off:** 201 with the `Pagamento` passed to `save`; `metodo` "Transferência" and date 2020-01-01 are unchanged (no date rule); CC 50 → 150; a missing CC is created; `DataAccessResourceFailureException` on the CC save is swallowed (still 201); 400 "honorarioId é obrigatório"; 400 "valorPago é obrigatório e deve ser positivo"; 404 "Honorário não encontrado"; 404 "Processo associado não encontrado" for a foreign tenant; `registar` is never called and `documentoFiscalService` is untouched.
  - **Billing on:** 201 / 200 with the service's response; `registar` gets the principal's tenant and the same principal instance; no repository is touched; `RecusaFiscalException` propagates unchanged.
  - **JSON binding (A2):** `Jackson2ObjectMapperBuilder.json().build()` reads `{"id":99,...,"extra":"x"}` into `PagamentoRequest`, and `paraPagamentoLegado().getId()` is null.
- `ResourceControllerListaPagamentosTest` 7/7:
  - Only payment #2 has `{id, numeroFormatado}`; #1 and #3 are null (EMIS-12).
  - `referenciasPorPagamento` is called once, with the principal's tenant and ids {1,2,3}.
  - A foreign tenant or a missing honorário gives 404 and `documentoFiscalService` is never called.
  - An empty list is handled.
  - The `financeiro:view` gate is unchanged.
  - Reflection: no POST/PUT handler path contains `pagamentos/{` together with fatura/documento-fiscal/documentos-fiscais/emitir.
- Full surefire suite: `mvn -Dmaven.compiler.release=21 test` gives **751 tests, 0 failures, 0 errors**.
- `compile spotbugs:check` passes. `spotbugs-exclude.xml` is untouched; its `createPagamento` ENTITY_MASS_ASSIGNMENT entry is now unused, which is harmless.
- **Acceptance greps:** `faturacao` 1, `Faturacao` 0; no `@Transactional` near `createPagamento|registarPagamentoLegado`; `referenciasPorPagamento` appears once.

## Commits

| Task | Phase | Commit | Description |
|------|-------|--------|-------------|
| 1 | RED | f79ffbe | Evolved CFG-03 guard (hash pin) |
| 1 | GREEN | 809fdba | Delegation + verbatim registarPagamentoLegado + constructor tests |
| 2 | RED | f9def44 | Both-branch behavioural tests + failing payments-list test |
| 2 | GREEN | c482992 | listHonorarioPagamentos with document references |

## TDD Gate Compliance

For both tasks, a `test(134-08)` commit came first:
- Task 1: the guard failed 3/5 before the change.
- Task 2: the list test failed 2/7 with a `ClassCastException`. In the same commit, the 13 delegation tests already passed against Task 1's code, as expected.

A `feat(134-08)` commit followed for each. No refactor commit was needed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Guard self-trip] Javadoc reworded so the token count stays exact**
- **Found during:** Task 1
- **Issue:** The new `createPagamento` javadoc named `FaturacaoDesligadaPagamentoInalteradoTest`, which added one `Faturacao` occurrence to `ResourceController.java`. The guard and the acceptance grep require 0.
- **Fix:** The javadoc now says "teste-guarda CFG-03".
- **Commit:** 809fdba

## Known Stubs

None. The frontend still sends the legacy payload and reads the superset JSON; plans 11-12 add the billing-on form and the "Documento fiscal" column.

## Self-Check: PASSED

- FOUND: all 6 key files
- FOUND: f79ffbe, 809fdba, f9def44, c482992
