---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 19
subsystem: backend/fiscal email outbox job
tags: [scheduler, outbox, email, greenmail, end-to-end, concurrency, notification]
requires: [137-09, 137-12, 137-14, 137-17]
provides:
  - EmailFiscalOutboxJob (@Scheduled app.email.outbox.intervalo / atraso-inicial), executarUmaVez()
  - EmailFiscalOutboxJobIT (end-to-end proof on PostgreSQL + GreenMail)
affects: [137-20 (web shows the delivery states this job produces)]
tech-stack:
  added: []
  patterns: [FiscalOutboxJob analog (sweep exhausted -> claim -> per-item try/catch, no transaction, Error propagates), switchable gateway test double, in-memory StorageService via @MockitoBean answers, movable clock over backoff]
key-files:
  created:
    - backend/src/main/java/com/lexcv/jobs/EmailFiscalOutboxJob.java
    - backend/src/test/java/com/lexcv/jobs/EmailFiscalOutboxJobTest.java
    - backend/src/test/java/com/lexcv/jobs/EmailFiscalOutboxJobIT.java
  modified: []
decisions:
  - "The not-configured short-circuit sits before the exhausted-row sweep as well as the claim. Without SMTP the job touches no row at all (no versao/updated_at change, no FALHOU, no notification), so nothing is burned or closed while SMTP is missing"
  - "Logs in the job carry ids and the exception simple class name only, the same rule as ProcessadorEntregaEmail, so no exception text reaches the log. The not-configured path logs at DEBUG only"
  - "The IT builds EmailProperties and the gateway as test beans instead of importing EmailConfig. The gateway is a switchable delegate over the real SmtpEntregaEmailGateway (GreenMail on 127.0.0.1:3225, or a closed port) and NaoConfiguradoEntregaEmailGateway"
metrics:
  duration: ~40min
  completed: 2026-10-07
  tasks: 2
  files: 3
---

# Phase 137 Plan 19: Email outbox job and end-to-end delivery proof Summary

`EmailFiscalOutboxJob` is the third periodic job, scheduled on `${app.email.outbox.intervalo:PT30S}` with an initial delay of `${app.email.outbox.atraso-inicial:PT40S}`. It has no transaction.

Each `executarUmaVez()`:
1. Returns 0 straight away when `EntregaEmailGateway.configurado()` is false, before any database access.
2. Otherwise closes exhausted rows and notifies each through `notificarEsgotada`.
3. Claims `lote` rows with `lease`, and passes each to `ProcessadorEntregaEmail.processar`.

Failures are contained per item and per notification. `executar()` swallows any `Exception`; an `Error` propagates to the scheduler.

`EmailFiscalOutboxJobIT` proves the whole chain on Testcontainers PostgreSQL with GreenMail in process. No real SMTP is used.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | EmailFiscalOutboxJob + unit test | RED cb77257, GREEN 7d5f073 |
| 2 | EmailFiscalOutboxJobIT end to end | 55c8965 |

## Verification

- `EmailFiscalOutboxJobTest`: **12/12**. Covers:
  - Not configured: 0 returned, no interaction with the transactions or the processor.
  - Lote/lease from properties and in-order processing.
  - Exhausted rows notified in order before the claim; empty batch.
  - Per-item, per-notification and sweep failures contained.
  - `executar` swallows a claim failure and a gateway failure; an Error from an item or from the claim propagates.
  - Schedule strings exact; no `@Transactional` anywhere.
- `grep @Transactional EmailFiscalOutboxJob.java`: none. `spotbugs:check`: clean.
- `EmailFiscalOutboxJobIT`: **7/7, 0 skipped**.
  1. **FR happy path.**
     - After the payment: zero delivery rows, zero messages, and the email job claims 0.
     - The communication job gives ACEITE_SIMULADO plus one PENDENTE row whose recipient is the client email.
     - The email job delivers exactly one message to that address. The subject starts with `[SIMULAÇÃO — SEM VALIDADE FISCAL] Fatura-Recibo` and contains the number. There are exactly two attachments: `{nome}.pdf` starting with `%PDF`, and `{nome}.xml` equal to `t_documento_fiscal_xml.xml`.
     - The row ends ENVIADO with tentativas 1 and `enviado_em` set; `t_documento_fiscal_pdf` has one row.
  2. **NC on that FR.** Same path: no row at emission, PENDENTE after acceptance, ENVIADO after the job. The subject reads "Nota de Crédito {n}", it has the two attachments, and the body contains "corrige a Fatura-Recibo {FR}".
  3. **No SMTP.** The FR still issues and is accepted; three runs claim 0 and the row stays PENDENTE with tentativas 0. After switching to SMTP, the next run sends.
  4. **Opt-outs.** Toggle off gives DESLIGADO; a client without email gives SEM_EMAIL. Both have tentativas 0 and nothing is sent.
  5. **Failure episodes, with SMTP on a closed port.**
     - Attempts 1–4 stay PENDENTE with no notification. Attempt 5 gives FALHOU with `SMTP_INDISPONIVEL`.
     - Exactly one `EMAIL_FISCAL_FALHOU` goes to the manage user and one to the edit user, with entidade_id `{doc}:0`; the view-only user gets none. Further runs create nothing.
     - `reenviar` sets PENDENTE with reenvios 1. Five more failures notify again with `{doc}:1`, so the tenant has 4 notifications in total.
     - With GreenMail reachable, a second resend (reenvios 2) is ENVIADO.
  6. **Concurrency.** Two threads run `executarUmaVez` at the same time over 3 PENDENTE rows. 3 rows are claimed in total, GreenMail receives exactly 3 messages to the 3 distinct clients, and all 3 rows are ENVIADO with tentativas 1.
  7. **No SMTP, exhausted row.** A row already at tentativas 5 keeps its estado, versao and updated_at over three runs, and no notification is created.
- `FiscalOutboxJobIT`: **7/7** in the same run (14/14 total, BUILD SUCCESS).

## Deviations from Plan

1. **[Rule 2] The not-configured short-circuit also skips the exhausted sweep.** The plan's behaviour already listed "neither encerrarEsgotadas nor reclamar". The extra IT case (7) shows this is what keeps rows untouched while SMTP is missing. No production fix was needed: every IT assertion passed against the plan-shaped code.
2. **Test count.** The FR+NC flow was split into two tests (1 and 2) to meet the plan's ">= 7". Test 7 (no-SMTP exhausted row) goes beyond the plan's list.
3. **Note (not a deviation).** When failsafe runs two or more IT classes in one fork, it prints "Surefire is going to kill self fork JVM. The exit has elapsed 30 seconds after System.exit(0)" and still ends in BUILD SUCCESS. This also happens with the existing pair `FilaComunicacaoFiscalIT,FiscalOutboxJobIT`, so this plan did not cause it. Each class run alone exits cleanly.

## Threat Flags

None. T-137-81 (concurrency IT), T-137-82 (tentativas 0 without SMTP), T-137-83 (FALHOU + one notification per episode), T-137-84 (local GreenMail only) and T-137-85 (no delivery work at payment time) are mitigated and proven.

## Self-Check: PASSED
