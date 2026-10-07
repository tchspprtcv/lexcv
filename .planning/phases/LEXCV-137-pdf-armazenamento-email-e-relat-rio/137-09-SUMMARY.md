---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 09
subsystem: backend/fiscal email configuration
tags: [smtp, configuration, optional-dependency, scheduler]
requires: [137-08]
provides:
  - EmailConfig (@EnableConfigurationProperties(EmailProperties); @Bean EntregaEmailGateway)
  - application.yml app.email.smtp.* (SMTP_HOST/PORT/USERNAME/PASSWORD/FROM/STARTTLS, empty defaults) + app.email.outbox.*
  - spring.task.scheduling.pool.size 4
affects: [137-13 (env var names), 137-14 (processor gets EntregaEmailGateway bean), 137-19 (job uses outbox props)]
tech-stack:
  added: []
  patterns: [optional adapter chosen at startup (EfaturaConfig analog, but absence never fails)]
key-files:
  created:
    - backend/src/main/java/com/lexcv/fiscal/email/EmailConfig.java
    - backend/src/test/java/com/lexcv/fiscal/email/EmailConfigTest.java
  modified:
    - backend/src/main/resources/application.yml
    - backend/src/main/java/com/lexcv/config/SchedulingConfig.java
    - backend/src/main/java/com/lexcv/fiscal/email/EmailProperties.java
    - backend/src/test/java/com/lexcv/fiscal/efatura/EfaturaConfigTest.java
decisions:
  - "SMTP_HOST blank -> NaoConfiguradoEntregaEmailGateway (never fails startup). Host set with blank/invalid SMTP_FROM (strict InternetAddress) or SMTP_PORT outside 1..65535 -> IllegalStateException naming the variable, never any value."
  - "EmailProperties.Smtp.port/starttls are now Integer/Boolean with a compact constructor defaulting null to 587/true, so SMTP_PORT= and SMTP_STARTTLS= (empty, as compose passes them) do not abort binding"
  - "No spring.autoconfigure.exclude needed: no spring.mail.* key exists, and the test proves MailSenderAutoConfiguration creates no JavaMailSender"
metrics:
  duration: ~20min
  completed: 2026-10-07
  tasks: 2
  files: 6
---

# Phase 137 Plan 09: Optional SMTP wiring and scheduler pool 4 Summary

`EmailConfig` picks the email adapter from the optional `app.email.smtp.*` block. With no SMTP host (absent or empty), the application starts, issues documents normally and the gateway reports "not configured". With host and a valid sender it wires `SmtpEntregaEmailGateway`. A half configuration fails startup with an explicit message naming `SMTP_FROM` or `SMTP_PORT`. Startup logs one INFO line: either "não configurado" or `SMTP host:port (STARTTLS on/off)`, never credentials. The scheduler pool is 4 (AlertasDiariosJob, FiscalOutboxJob, EmailFiscalOutboxJob, headroom).

## Tasks

| # | Task | Commit |
|---|------|--------|
| 1 | EmailConfig + application.yml block + pool 4 | 5aa55a1 |
| 2 | EmailConfigTest (+ empty-port binding fix, EfaturaConfigTest pin) | 44edb67 |

## Verification

- Task 1 gates: `size: 4`, `${SMTP_HOST:}` present, no `mail:` key in application.yml; `compile spotbugs:check` clean.
- `EmailConfigTest`: **15/15**. Runner loads `MailSenderAutoConfiguration` + `EmailConfig`:
  - no properties, and all six values empty: NaoConfigurado, `configurado()` false;
  - host + from: Smtp adapter, `configurado()` true;
  - host with empty port/starttls: 587 / STARTTLS on, Smtp adapter;
  - host with from `""`, `"   "`, `not-an-address`, `a@b@c`, two addresses: fails with "SMTP_FROM", message contains neither the password sentinel nor the from value;
  - port 0 / -1 / 65536: fails with "SMTP_PORT";
  - outbox defaults PT30S / PT40S / 10 / PT2M;
  - application.yml: exact `${SMTP_*}` placeholders, pool 4, no `spring.mail.*` key; its unresolved defaults start NaoConfigurado;
  - zero `JavaMailSender` beans in every successful case.
- `EfaturaConfigTest` 17/17 (pool pin updated), `SmtpEntregaEmailGatewayTest` 10/10.

## Deviations from Plan

1. **[Rule 1 - Bug] Empty `SMTP_PORT` / `SMTP_STARTTLS` aborted startup.** The plan's own "all six values empty" behaviour failed: Spring converts an empty string to null, which cannot bind to the primitive `int`/`boolean` of `EmailProperties.Smtp` (137-08). Changed both components to `Integer`/`Boolean` with a compact constructor defaulting to 587/true. Callers unbox safely (never null). Commit 44edb67.
2. **[Rule 3 - Blocking] `EfaturaConfigTest` pinned the pool size at 3.** Updated to 4 (direct consequence of this plan's change).
3. **TDD order:** the plan puts the implementation (Task 1) before the test (Task 2, `tdd="true"`), so there is no failing-first commit for EmailConfig; the RED run that did happen was the empty-port case above.
4. Extra tests beyond the behaviour list: empty port/starttls with host, port range, application.yml placeholders.

## Self-Check: PASSED
