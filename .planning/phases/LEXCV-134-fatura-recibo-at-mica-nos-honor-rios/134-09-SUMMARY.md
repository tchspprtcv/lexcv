---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 09
subsystem: backend-api
tags: [fiscal, efatura, controller, rbac, preauthorize, pagination, read-side]
requires:
  - "134-04: PreVisualizacaoFaturaService.preVisualizar / estadoEmissao, PagamentoRequest"
  - "134-05: DocumentoFiscalService.listar / detalhe (404 DOCUMENTO_FISCAL_NAO_ENCONTRADO)"
provides:
  - "GET /api/v1/faturacao/estado-emissao (financeiro:view) -> EstadoEmissaoResponse"
  - "POST /api/v1/faturacao/pre-visualizacao (financeiro:edit) -> PreVisualizacaoFaturaResponse, no writes"
  - "GET /api/v1/documentos-fiscais?clienteId&de&ate&tipo&estado&page&size (financeiro:view) -> {content, totalElements, totalPages, page, size}"
  - "GET /api/v1/documentos-fiscais/{id} (financeiro:view) -> DocumentoFiscalDetalheResponse; non-UUID id -> 404 {message, code}"
affects: [134-11, 134-12, 134-13, 134-14]
tech-stack:
  added: []
  patterns:
    - "Per-method @PreAuthorize in a dedicated controller (no class gate) when one surface mixes view and edit"
    - "Query params accepted as String and parsed manually so malformed input is a 400, never the 500 catch-all"
key-files:
  created:
    - backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerTest.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerAutorizacaoTest.java
  modified: []
decisions:
  - "400 messages: 'page deve ser >= 0 e size deve estar entre 1 e 100' (same as AuditoriaRbacController), 'O cliente indicado não é válido.', 'As datas devem estar no formato AAAA-MM-DD.', 'A data final não pode ser anterior à inicial.', 'O tipo de documento indicado não é válido.', 'O estado de comunicação indicado não é válido.'"
  - "tipo/estado filters accept exact enum names only (FR, NC, PENDENTE); blank values are absent"
  - "Response 'page'/'size' echo the parsed request values (the service builds an unsorted PageRequest from them)"
  - "Backend checks exact authorities; the web fallback (view satisfied by edit/manage) can only diverge for custom roles holding edit without view -- seeded roles always pair them (ADMIN view+edit+manage, ADVOGADO/TECNICO view, ASSISTENTE none)"
metrics:
  duration: "~20 min"
  completed: 2026-10-04
  tasks: 2
  files: 3
---

# Phase 134 Plan 09: DocumentoFiscalController Summary

A new `DocumentoFiscalController` exposes the read and preview surface of Phase 134 with method-level gates:
- billing state for the payment form
- the side-effect-free Fatura-Recibo preview
- the paginated per-tenant listing with filters
- the read-only detail

Each gate is proven against Spring Security's real `preAuthorize()` interceptor.

## What was built

- **`DocumentoFiscalController`** (`@RequestMapping("/api/v1")`, `@RequiredArgsConstructor`, no class-level gate, not `@Transactional`). It is a new class because `FaturacaoController` is class-gated `financeiro:manage` and its tests pin it to 7 handlers. Its four handlers:
  - `estadoEmissao`: `GET /faturacao/estado-emissao`, `financeiro:view`.
  - `preVisualizar`: `POST /faturacao/pre-visualizacao`, `financeiro:edit`, the same gate as registering a payment. It writes nothing.
  - `listar`: `GET /documentos-fiscais`, `financeiro:view`. Every parameter is a `String` parsed by hand:
    - page/size must be integers with page ≥ 0 and size 1..100 (defaults 0 and 10)
    - `clienteId` must be a UUID
    - `de`/`ate` must be ISO dates with `de ≤ ate`
    - `tipo`/`estado` must be `TipoDocumentoFiscal`/`EstadoComunicacaoFiscal` names
    - blank means absent; any failure returns 400 `{message}`
    
    It answers `{content, totalElements, totalPages, page, size}`.
  - `detalhe`: `GET /documentos-fiscais/{id}`, `financeiro:view`. A non-UUID id gets 404 `{message: "Documento fiscal não encontrado.", code: "DOCUMENTO_FISCAL_NAO_ENCONTRADO"}`, identical to the service's answer for a missing or foreign document, so there is no oracle.
  - The tenant always comes from the principal. There are no PUT/PATCH/DELETE routes and no parameter carries a `pagamentoId` (EMIS-12).

## Verification

- `DocumentoFiscalControllerTest` 22/22:
  - **Delegation:** estado and preview use the principal's tenant; `RecusaFiscalException` propagates.
  - **Listing:** defaults, all filters parsed, blanks treated as absent, same-day range accepted.
  - **400 cases:** negative page; size 0 and 101; page "x" and size "1e3"; a non-UUID cliente; non-ISO and impossible dates; `de > ate` with its message; unknown tipo; unknown estado.
  - **Detail:** an invalid id gives 404 with no service call; delegation; the service's 404 propagates.
  - **Reflection:** exactly 4 handlers with their exact paths; no PUT/PATCH/DELETE/RequestMapping on methods; view/view/view/edit gates and no class gate; no parameter named like tenant or pagamentoId (compiled with `-parameters`); the only body is `PagamentoRequest`, which has no tenant component; nothing is `@Transactional`.
- `DocumentoFiscalControllerAutorizacaoTest` 10/10, through `ProxyFactory` + `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()`:
  - **Refused, services untouched:** no financeiro authority; the `ROLE_financeiro:*` hasRole trap; `ROLE_PLATAFORMA_ADMIN`; `ROLE_ADMIN` alone; the seeded ASSISTENTE.
  - **Partial access:** view-only and the seeded ADVOGADO can read but not preview; edit-only can preview but not read (documented divergence from the web fallback for custom roles).
  - **Full access:** view+edit passes everywhere, with the principal's tenant captured in every service call; the seeded ADMIN (`UserPrincipal.create` with role ADMIN) passes everywhere.
- `FaturacaoControllerTest`, `AuditLogImutabilidadeTest` and `DocumentoFiscalImutabilidadeTest` are green. `FaturacaoController.java` is untouched.
- Full surefire suite: **783 tests, 0 failures, 0 errors**.
- `compile spotbugs:check` passes with `spotbugs-exclude.xml` unchanged.
- **Acceptance greps:** `@PreAuthorize` appears 4 times in the controller; `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` is used.

## Commits

| Task | Phase | Commit | Description |
|------|-------|--------|-------------|
| 1 | RED | 3a75baa | Failing DocumentoFiscalControllerTest |
| 1 | GREEN | bd1c4ab | DocumentoFiscalController |
| 2 | - | a48f293 | DocumentoFiscalControllerAutorizacaoTest (real interceptor) |

## TDD Gate Compliance

Task 1 (`tdd="true"`) has a `test(134-09)` commit first, while the test failed to compile against the missing controller. A `feat(134-09)` commit followed with 22/22 green. Task 2 is not TDD: it proves the gates written in Task 1.

## Deviations from Plan

None. The plan was executed as written; the 400 messages and the exact-enum-name rule are recorded as decisions.

## Known Stubs

None. Plans 11-13 consume these endpoints from the web.

## Self-Check: PASSED

- FOUND: all 3 key files
- FOUND: 3a75baa, bd1c4ab, a48f293
