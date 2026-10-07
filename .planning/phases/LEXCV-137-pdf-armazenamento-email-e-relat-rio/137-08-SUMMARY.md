---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 08
subsystem: backend/fiscal email adapter
tags: [smtp, jakarta-mail, greenmail, header-injection, credentials]
requires: [137-01, 137-05]
provides:
  - EmailProperties (app.email.smtp.*, app.email.outbox.*; configurado(); masked toString)
  - EntregaEmailGateway {configurado(), enviar(MensagemEmailFiscal)}
  - ResultadoEnvioEmail sealed {Enviado, ErroTransitorio(codigo, mensagem), ErroPermanente(codigo, mensagem)}
  - MensagemEmailFiscal (+ Anexo) — exactly two attachments
  - NaoConfiguradoEntregaEmailGateway (SMTP_NAO_CONFIGURADO)
  - SmtpEntregaEmailGateway (codes DESTINATARIO_INVALIDO, DESTINATARIO_RECUSADO, SMTP_INDISPONIVEL, SMTP_AUTENTICACAO, SMTP_REMETENTE_INVALIDO, FALHA_ENVIO + fixed MSG_* constants)
affects: [137-09 (bean wiring + application.yml), 137-14 (composer/processor), 137-19 (job short-circuit)]
tech-stack:
  added: []
  patterns: [sealed result port (EfaturaGateway analog), private JavaMailSenderImpl, scripted SMTP server in tests]
key-files:
  created:
    - backend/src/main/java/com/lexcv/fiscal/email/EmailProperties.java
    - backend/src/main/java/com/lexcv/fiscal/email/EntregaEmailGateway.java
    - backend/src/main/java/com/lexcv/fiscal/email/ResultadoEnvioEmail.java
    - backend/src/main/java/com/lexcv/fiscal/email/MensagemEmailFiscal.java
    - backend/src/main/java/com/lexcv/fiscal/email/NaoConfiguradoEntregaEmailGateway.java
    - backend/src/main/java/com/lexcv/fiscal/email/SmtpEntregaEmailGateway.java
    - backend/src/test/java/com/lexcv/fiscal/email/SmtpEntregaEmailGatewayTest.java
  modified: []
decisions:
  - "The recipient must pass RegrasEntregaEmail.emailValido (137-05) and strict InternetAddress parsing, and must be a single address with no display name. Otherwise the result is DESTINATARIO_INVALIDO and no connection is opened."
  - "An invalid Reply-To is dropped (logged without the address) and the email is still sent. An invalid configured From gives transient SMTP_REMETENTE_INVALIDO."
  - "A 5xx on any step other than RCPT (for example DATA or MAIL FROM) is transient FALHA_ENVIO, not permanent. Only a recipient refusal is permanent, as the plan specifies."
  - "SmtpEntregaEmailGateway is not a @Component. Its constructor rejects an unconfigured Smtp, and the bean choice is left to 137-09."
  - "Test harness: GreenMail 2.1.14 in-process (spike D) for delivery and authentication. A minimal scripted SMTP server in the test covers the 550/450 RCPT replies, which GreenMail cannot produce."
metrics:
  duration: ~25min
  completed: 2026-10-07
  tasks: 2
  files: 7
---

# Phase 137 Plan 08: Email delivery port and SMTP adapter Summary

This plan adds one email port with a sealed result, the optional-SMTP properties (`app.email.*`, never `spring.mail.*`), a "not configured" fallback that never sends, and the real SMTP adapter.
- **Message:** multipart (plain text plus HTML) with the PDF and XML as the two attachments, From set to the configured sender, and an optional Reply-To.
- **Addressing:** recipients are strictly parsed before any connection is opened.
- **Failures:** classified with fixed codes and messages.
- **Logs:** only exception class names are logged. Credentials are masked everywhere.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | Properties, port, sealed result, message, NaoConfigurado adapter | 5aa1c1b |
| 2 | SmtpEntregaEmailGateway + test | RED 4be8b1f, GREEN b59d63e |

## Verification

- Task 1: `compile spotbugs:check` is clean, and `password=***` is present in `EmailProperties`.
- `SmtpEntregaEmailGatewayTest`: **10/10**, with no real SMTP.
  - Delivery to GreenMail: one email, exact UTF-8 subject including "SIMULAÇÃO — SEM VALIDADE FISCAL", correct From and Reply-To, a text/plain and a text/html part, and two attachments whose names, content types and bytes match the input.
  - No Reply-To header when none is given.
  - RCPT 550 gives permanent `DESTINATARIO_RECUSADO`; 0 messages are accepted.
  - RCPT 450 gives transient `SMTP_INDISPONIVEL`.
  - A port with nothing listening gives transient `SMTP_INDISPONIVEL` well within the timeout.
  - Wrong credentials on an auth-required GreenMail give transient `SMTP_AUTENTICACAO`; correct credentials deliver.
  - Invalid recipients (CR/LF Bcc injection, several `@`, a display name, a list, no `@`) give `DESTINATARIO_INVALIDO` with **0 connections opened**.
  - An injected Reply-To is dropped, and no Bcc header appears.
  - `toString` of the properties, gateway and message never shows the password, the username or the recipient.
  - A captured logback appender shows no recipient, password, server reply text or stack trace in any adapter log line.
- `mvn -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check`: clean.
- The whole backend unit suite (`mvn -Dmaven.compiler.release=21 test`) passes: **1376 tests, 0 failures, 0 errors, 0 skipped**. This checks that plans 04–08 did not break anything, including the global PDFBox FontMapper change from 137-06.

## Deviations from Plan

1. **[Rule 2 - Security] Two layers of recipient validation.** The adapter reuses `RegrasEntregaEmail.emailValido` on top of `InternetAddress` strict parsing, and also rejects display names and address lists.
2. **Extra fixed code `SMTP_REMETENTE_INVALIDO`.** It covers an invalid configured `SMTP_FROM` and is transient, because it is a configuration problem.
3. **Scripted SMTP server in the test.** GreenMail cannot reject RCPT with 550/450, so a small server in the test file handles those cases. GreenMail is still the main harness, as the spike decided.

## Self-Check: PASSED
