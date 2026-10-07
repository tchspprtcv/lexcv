---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 11
subsystem: backend/fiscal read API
tags: [email-delivery, dto, multi-tenancy, n+1, configuration]
requires: [137-05, 137-08]
provides:
  - EntregaEmailResumo {estado, destinatario, emailDestinatario, tentativas, ultimaTentativaEm, proximaTentativaEm, enviadoEm, ultimoErro, reenviavel} + static de(linha, smtp, envioAutomatico, comunicacao, emailClienteAtual)
  - DocumentoFiscalDetalheResponse.entregaEmail (trailing; null without a delivery row)
  - DocumentoFiscalResumoResponse.estadoEntregaEmail (trailing; null without a delivery row)
  - ConfiguracaoFiscalResponse.smtpConfigurado (trailing)
  - DocumentoFiscalService constructor + (EntregaEmailFiscalRepository, ConfiguracaoFiscalRepository, ClienteRepository, EmailProperties)
  - ConfiguracaoFiscalService constructor + EmailProperties (last)
affects: [137-12 (resend uses the same rules), 137-15/137-16/137-23 (web types and cards)]
tech-stack:
  added: []
  patterns: [single rule source (RegrasEntregaEmail) for derived UI state, one batched query per page]
key-files:
  created:
    - backend/src/main/java/com/lexcv/dtos/EntregaEmailResumo.java
  modified:
    - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalDetalheResponse.java
    - backend/src/main/java/com/lexcv/dtos/DocumentoFiscalResumoResponse.java
    - backend/src/main/java/com/lexcv/services/fiscal/DocumentoFiscalService.java
    - backend/src/main/java/com/lexcv/dtos/ConfiguracaoFiscalResponse.java
    - backend/src/main/java/com/lexcv/services/fiscal/ConfiguracaoFiscalService.java
    - backend/src/test/java/com/lexcv/services/fiscal/DocumentoFiscalServiceTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/ConfiguracaoFiscalServiceTest.java
    - backend/src/test/java/com/lexcv/controllers/GuardasDocumentoFiscalConcorrenciaIT.java
    - backend/src/test/java/com/lexcv/services/fiscal/ConfiguracaoFiscalConcorrenciaIT.java
    - backend/src/test/java/com/lexcv/controllers/FaturacaoControllerTest.java
decisions:
  - "With the presented state NAO_CONFIGURADO, ultimoErro and proximaTentativaEm are null even if the stored row is PENDENTE with an error; proximaTentativaEm otherwise only for PENDENTE (mirrors ComunicacaoFiscalResumo)"
  - "destinatario is null when the stored state is SEM_EMAIL or the presented state is NAO_CONFIGURADO"
  - "emailDestinatario passes through RegrasEntregaEmail.emailValido (trimmed); an invalid client email is null and makes reenviavel false"
  - "The detail reads the tenant configuration and the client only when a delivery row exists"
  - "DocumentoFiscalService reads EmailProperties.configurado() once in the constructor"
metrics:
  duration: ~30min
  completed: 2026-10-07
  tasks: 2
  files: 11
---

# Phase 137 Plan 11: Email delivery state on the read API and smtpConfigurado Summary

The fiscal document detail now carries `entregaEmail` (`EntregaEmailResumo`), and each list row carries `estadoEntregaEmail`, loaded in one batched query per page. The billing configuration response reports `smtpConfigurado`. All derived values come from `RegrasEntregaEmail`:
- the presented state, including `NAO_CONFIGURADO` without SMTP;
- whether the last error is visible;
- `reenviavel`.

The client's current email is read by id, filtered by tenant, and validated.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | EntregaEmailResumo + detail/list fields + DocumentoFiscalService | RED 4d0237d, GREEN c62e8f7 |
| 2 | smtpConfigurado on ConfiguracaoFiscalResponse | RED 5a2a28b, GREEN 5d6d012 |

## Verification

- `DocumentoFiscalServiceTest`: **36/36**. That is 27 existing tests, unchanged apart from the constructor, plus 9 new:
  - FALHOU, SMTP on, toggle on, ACEITE_SIMULADO, client with email: reenviavel, error visible, current email returned.
  - No SMTP, PENDENTE: NAO_CONFIGURADO; no error, recipient, next attempt or resend.
  - SEM_EMAIL without a client email: not resendable; once the client has an email (trimmed): resendable.
  - Invalid client email (CR/LF): null and not resendable.
  - DESLIGADO: never resendable.
  - No delivery row: null, and neither the client nor the configuration is read.
  - Client with the same id in another tenant: ignored.
  - The list makes exactly one `findByTenantIdAndDocumentoFiscalIdIn` call per page and maps every row.
  - The list without SMTP shows NAO_CONFIGURADO but keeps ENVIADO.
- `DocumentoFiscalControllerTest` 31/31, `DocumentoFiscalControllerAutorizacaoTest` 31/31.
- `ConfiguracaoFiscalServiceTest`: **36/36**, including 2 new tests:
  - `smtpConfigurado` is true/false with and without the tenant row;
  - every other field is identical between the two SMTP states.
- `FaturacaoControllerTest` 13/13, `FaturacaoControllerAutorizacaoTest` 8/8.
- ITs, Testcontainers: `GuardasDocumentoFiscalConcorrenciaIT` 11/11 and `ConfiguracaoFiscalConcorrenciaIT` 2/2. Their assertions are unchanged; each context only gained an `EmailProperties` bean with SMTP not configured.
- Full unit suite `mvn -Dmaven.compiler.release=21 test`: 1445 tests, 0 failures, 0 errors, 0 skipped.
- `compile spotbugs:check`: clean.

## Deviations from Plan

1. **[Rule 3 - Blocking] `FaturacaoControllerTest`** builds `ConfiguracaoFiscalResponse` positionally, so it needed the new trailing argument (`false`). The file is not in the plan's list.
2. **Extra detail behaviour:** an invalid client email makes `emailDestinatario` null and `reenviavel` false, and there is a test for it. This follows from `emailValido` being part of the contract.
3. The configuration and client reads in `detalhe` are skipped when there is no delivery row (tested). Fewer queries; the result is the same.

## Self-Check: PASSED
