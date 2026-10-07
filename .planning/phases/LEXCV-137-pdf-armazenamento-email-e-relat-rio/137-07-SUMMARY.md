---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 07
subsystem: backend/fiscal email outbox
tags: [outbox, skip-locked, lease, multi-tenancy, postgresql]
requires: [137-02, 137-05]
provides:
  - FilaEntregaEmail {criarSeAusente, reclamar, encerrarEsgotadas, renovarLease, registarResultado, reporPendente} (all MANDATORY)
  - EntregaEmailReclamada (record; toString omits the recipient)
  - SnapshotEntregaEmail (documento, xml, numeroOrigem, replyTo)
  - EntregaEmailTransacoes {TENTATIVAS_ESGOTADAS, reclamar, encerrarEsgotadas, carregarSnapshot, renovarLease, registarResultado}
affects: [137-12 (resend uses reporPendente), 137-14 (processor), 137-17 (enqueue uses criarSeAusente)]
tech-stack:
  added: []
  patterns: [Phase 136 outbox mirrored: SKIP LOCKED claim + lease + versao guard + separate short-tx bean]
key-files:
  created:
    - backend/src/main/java/com/lexcv/repositories/FilaEntregaEmail.java
    - backend/src/main/java/com/lexcv/services/fiscal/EntregaEmailReclamada.java
    - backend/src/main/java/com/lexcv/services/fiscal/SnapshotEntregaEmail.java
    - backend/src/main/java/com/lexcv/services/fiscal/EntregaEmailTransacoes.java
    - backend/src/test/java/com/lexcv/services/fiscal/FilaEntregaEmailIT.java
  modified: []
decisions:
  - "encerrarEsgotadas is a plain UPDATE ... RETURNING with no CTE and no SKIP LOCKED. The plan's gate allows exactly one SKIP LOCKED. Exhausted rows are never claimable, and a concurrent closer waits on the row lock, re-checks estado = 'PENDENTE' and skips the row, so each row is still returned only once."
  - "destinatario is never truncated. A value over 254 characters throws IllegalArgumentException, because a cut address is a different address. Code and message are still truncated to 64 and 500."
  - "registarResultado clears ultimo_erro and ultimo_erro_codigo in the SQL when estado = ENVIADO. enviado_em uses COALESCE(:enviadoEm, enviado_em)."
  - "EntregaEmailReclamada overrides toString to leave out the recipient (personal data), so the address cannot reach logs through the record's toString"
  - "reporPendente sets proxima_tentativa_em NULL, so the row is due immediately"
metrics:
  duration: ~25min
  completed: 2026-10-07
  tasks: 2
  files: 5
---

# Phase 137 Plan 07: Email delivery outbox (database side) Summary

This plan adds the database side of the email delivery outbox on `t_entrega_email_fiscal`, mirroring the Phase 136 communication outbox:
- **Create:** the delivery row is created at most once per document (insert-if-absent).
- **Claim:** `FOR UPDATE SKIP LOCKED` with a lease. The attempt is counted at claim time, and the cap of 5 is in the claim predicate.
- **Close exhausted rows:** they become `FALHOU` with "O envio do email falhou após {n} tentativa(s).".
- **Guards:** results are written only if `versao` and `tenant_id` still match, and the lease is renewed before sending.
- **Manual resend:** starts a new episode (`reenvios + 1`).

Each step runs in its own short transaction in `EntregaEmailTransacoes`, so no transaction wraps PDF, MinIO or SMTP work.

## Tasks

| # | Task | Commit |
|---|------|--------|
| 1 | FilaEntregaEmail + records + EntregaEmailTransacoes | 1965458 |
| 2 | FilaEntregaEmailIT | d888916 |

## Verification

- Task 1 gates:
  - Exactly 1 `FOR UPDATE SKIP LOCKED`, 6 `Propagation.MANDATORY`, `versao = :versao`, and `IN ('FALHOU', 'ENVIADO', 'SEM_EMAIL')` are present.
  - `compile spotbugs:check` is clean.
  - `DocumentoFiscalImutabilidadeTest` passes **14/14**.
- `FilaEntregaEmailIT` (Testcontainers PostgreSQL 16): **12 tests, 0 failures, 0 errors, 0 skipped**. It proves:
  - Create-once; only PENDENTE rows are claimed (not DESLIGADO, SEM_EMAIL, ENVIADO or FALHOU).
  - Claim order is oldest first; the attempt is counted at claim, `versao` goes up by 1, the lease is set, and future rows are skipped.
  - A row with 5 attempts is not claimed. It is closed once with the exact plural message, and a second call returns nothing.
  - An exhausted row with an active lease is closed only after the lease expires.
  - The singular message "…após 1 tentativa." is used when there was one attempt.
  - Concurrent claims return disjoint sets.
  - An expired lease can be reclaimed; the old version and a wrong tenant both write 0 rows.
  - ENVIADO sets `enviado_em` and clears the error. PENDENTE keeps `enviado_em` null and sets `proxima_tentativa_em`.
  - Code and message are truncated to the column widths.
  - Renewing the lease does not change `versao`, and fails once the row has left PENDENTE.
  - `reporPendente` works from FALHOU, ENVIADO and SEM_EMAIL (new recipient, `reenvios + 1`, everything else cleared). It returns 0 from PENDENTE or DESLIGADO, or for another tenant.
  - An NC snapshot carries the FR number. Snapshots never cross tenants, and `replyTo` is empty when there is no config.

## Deviations from Plan

1. **[Rule 1 - Gate conflict] `encerrarEsgotadas` has no `SKIP LOCKED`.** The 136 analog has two `SKIP LOCKED` statements, but this plan's gate requires exactly one. Concurrency stays correct (see decisions).
2. **[Rule 2 - Correctness] The recipient is rejected, not truncated, when it is too long.**
3. **[Rule 2 - Privacy] `EntregaEmailReclamada.toString()` leaves out the recipient.**
4. **TDD note:** the IT tests code written in Task 1, which the plan does not mark TDD. It passed on its first run, so there was no red run.

## Self-Check: PASSED
