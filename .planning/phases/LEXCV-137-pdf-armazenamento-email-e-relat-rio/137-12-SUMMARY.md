---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 12
subsystem: backend/fiscal email resend
tags: [rbac, audit, email-delivery, multi-tenancy, concurrency]
requires: [137-05, 137-07, 137-08]
provides:
  - ReenvioEmailFiscalService.reenviar(tenantId, autor, documentoId) -> ReenviarEmailResponse("PENDENTE", 0)
  - ReenviarEmailResponse(estado, tentativas)
  - AuditoriaFiscalService.ACAO_REENVIAR_EMAIL + registarReenvioEmail (MANDATORY)
  - POST /api/v1/documentos-fiscais/{id}/email/reenviar (hasAuthority('financeiro:edit'))
  - DocumentoFiscalController constructor + ReenvioEmailFiscalService (last parameter)
affects: [137-16/137-23 (web resend button and dialog error table), 137-15/137-18 (further controller params and audit actions)]
tech-stack:
  added: []
  patterns: [ReprocessamentoComunicacaoService analog: tx owner, refusals before any write, conditional UPDATE, audit in the same tx]
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/ReenvioEmailFiscalService.java
    - backend/src/main/java/com/lexcv/dtos/ReenviarEmailResponse.java
    - backend/src/test/java/com/lexcv/services/fiscal/ReenviarEmailIT.java
  modified:
    - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
    - backend/src/main/java/com/lexcv/models/AuditLog.java
    - backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerAutorizacaoTest.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
decisions:
  - "Refusal order exactly as planned: 404 document -> 409 no row -> 422 SMTP -> 422 toggle -> 422 communication -> 409 state -> 422 client email -> 409 lost race. Every refusal happens before any write, so none leaves an audit row."
  - "Status 422 is HttpStatus.UNPROCESSABLE_ENTITY. The messages are fixed Portuguese strings with the UI-SPEC 1d meaning; the 409 message is generic because one code covers several causes."
  - "The client's current email comes from ClienteRepository.findById filtered by tenant, then RegrasEntregaEmail.emailValido (trimmed). A client of another tenant is never read (SEM_EMAIL_CLIENTE)."
  - "The new controller dependency is the last constructor parameter, so existing positional test constructions only append one argument"
metrics:
  duration: ~30min
  completed: 2026-10-07
  tasks: 2
  files: 9
---

# Phase 137 Plan 12: Manual email resend Summary

`POST /api/v1/documentos-fiscais/{id}/email/reenviar` requires the exact `financeiro:edit` authority. `ReenvioEmailFiscalService` runs in one transaction:
- Before any write, it refuses with the fixed UI-SPEC codes: 404 `DOCUMENTO_FISCAL_NAO_ENCONTRADO`; 409 `ENTREGA_ESTADO_INVALIDO`; 422 `SMTP_NAO_CONFIGURADO`, `ENVIO_EMAIL_DESLIGADO`, `COMUNICACAO_NAO_ACEITE`, `SEM_EMAIL_CLIENTE`.
- It then resets the delivery through `FilaEntregaEmail.reporPendente`, using the client's current email: PENDENTE, 0 attempts, `reenvios + 1` (a new failure episode).
- It audits `documento_fiscal_reenviar_email` in the same transaction, with the author name, number and previous state only; the address is never recorded.

No SMTP call happens here; the job sends the email. `ResourceController` and `registarPagamentoLegado` are untouched.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | Service + response + audit method + route + RBAC/controller/audit tests | RED a8c8558, GREEN 4285bca |
| 2 | ReenviarEmailIT | 3588de5 |

## Verification

- `DocumentoFiscalControllerAutorizacaoTest`: **37/37**. Six new cases cover the real method-security proxy:
  - exact `financeiro:edit` calls the service with the principal's tenant and author;
  - refused with AccessDeniedException, and the service is never called, for `financeiro:manage` alone, `view`, `view + manage`, `ROLE_financeiro:edit` and no authorities.
- `DocumentoFiscalControllerTest`: **33/33**. Covers delegation and the same 404 for a non-UUID id; the handler pin moved from 7 to 8 and asserts the new route.
- `AuditoriaFiscalServiceTest`: **21/21**. The count of `registar*` methods is now 9, all MANDATORY. The new event has exactly {autorNome, numeroFormatado, estadoAnterior} and no "@". The source gate (no email getter) still passes.
- `FaturacaoDesligadaPagamentoInalteradoTest` 5/5 (CFG-03 guard) and `DocumentoFiscalImutabilidadeTest` 14/14.
- `git diff --quiet HEAD -- ResourceController.java`: unchanged.
- `ReenviarEmailIT` (PostgreSQL 16 Testcontainers): **12/12, 0 skipped**.
  - **Successes:** FALHOU with 5 attempts, ENVIADO, and SEM_EMAIL once the client has an email. Each leaves PENDENTE, 0 attempts, 1 resend, the current email, and next attempt / lease / errors / last attempt all null, with versao + 1, `updated_at` = clock, and one audit row without "@".
  - **Refusals with no write and no audit:** PENDENTE and DESLIGADO (409), no row (409), SMTP not configured (422), toggle off (422), communication ERRO (422), client without email or with two addresses (422).
  - **Tenant isolation:** another tenant's document gives 404 and nothing changes. Another tenant's client gives 422 (its email is never used).
  - **Concurrency:** two concurrent resends give exactly 1 success and 1 409, with reenvios = 1 and 1 audit row.
- `compile spotbugs:check`: clean.

## Deviations from Plan

1. **[Rule 3 - Blocking] `DocumentoFiscalControllerTest`** was not in the plan's file list. It builds the controller positionally and pins the handler count at 7. I appended the new constructor argument, moved the pin to 8 with a route assertion, and added two delegation/404 tests.
2. Extra IT cases beyond the behaviour list: an invalid (two-address) client email, and a client from another tenant.
3. The "SMTP not configured" IT case uses a second service instance built with a non-configured `EmailProperties` inside a `TransactionTemplate`. The plan allowed either this or two nested configurations; this keeps the IT to a single Spring context.

## Threat Flags

None. The new route is the planned surface (T-137-46..51).

## Self-Check: PASSED
