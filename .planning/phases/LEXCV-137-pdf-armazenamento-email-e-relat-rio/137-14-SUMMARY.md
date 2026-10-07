---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 14
subsystem: backend/fiscal email delivery
tags: [email, composition, outbox, lease, notification, html-escaping]
requires: [137-03, 137-05, 137-06, 137-07, 137-08, 137-10]
provides:
  - ComposicaoEmailFiscal.compor(snapshot, destinatario, pdf) -> MensagemEmailFiscal
  - ProcessadorEntregaEmail.processar(EntregaEmailReclamada), notificarEsgotada(EntregaEmailReclamada)
affects: [137-19 (scheduled job calls processar / notificarEsgotada), 137-17 (enqueue)]
tech-stack:
  added: []
  patterns: [ProcessadorComunicacaoFiscal analog (never throws, LeasePerdido sentinel, sealed-result switch, notify after terminal commit)]
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/ComposicaoEmailFiscal.java
    - backend/src/test/java/com/lexcv/services/fiscal/ComposicaoEmailFiscalTest.java
    - backend/src/main/java/com/lexcv/services/fiscal/ProcessadorEntregaEmail.java
    - backend/src/test/java/com/lexcv/services/fiscal/ProcessadorEntregaEmailTest.java
  modified: []
decisions:
  - "The email total is FormatacaoFiscal.dinheiro(totalDocumento, moeda), as the plan says. With retention the PDF also shows 'Valor recebido' (137-06 dev. 9); the email states only the document total."
  - "NC origin line is included only when the snapshot has numeroOrigem; otherwise the line is omitted rather than inventing copy"
  - "Closing line uses emitenteMorada only ('NIF {nif} · {morada}'), no localidade, matching UI-SPEC 6b literally"
  - "Order inside processar: snapshot -> recipient revalidation (RegrasEntregaEmail.emailValido) -> XML presence -> lerPdf -> compose -> renovarLease -> enviar. Snapshot first, so the notification always has the document number"
  - "Fixed codes: DOCUMENTO_INEXISTENTE / DESTINATARIO_INVALIDO (permanent), FICHEIRO_INDISPONIVEL / STORAGE_INDISPONIVEL / FALHA_PDF / FALHA_INTERNA (transient; FALHOU on attempt 5)"
  - "Logs carry document id, delivery id, attempt and exception simple class name only (no stack trace, so exception text never reaches the log)"
metrics:
  duration: ~30min
  completed: 2026-10-07
  tasks: 2
  files: 4
---

# Phase 137 Plan 14: Email composition and per-delivery processor Summary

`ComposicaoEmailFiscal` builds the client email from the stored snapshot, following UI-SPEC 6b.
- **Subject:** `[SIMULAÇÃO — SEM VALIDADE FISCAL] {tipo} {número} — {firma}`.
- **Bodies:** plain text and HTML come from one ordered list of paragraphs. In the HTML, the simulation block sits in a grey bordered box (#e5e7eb / #6b7280). One escape helper handles all text, and there are inline styles only: no links, images or remote content.
- **Formatting:** money and dates use the shared `FormatacaoFiscal`, the same as the PDF.
- **Attachments:** `{nome}.pdf` (application/pdf) and `{nome}.xml` (application/xml, the stored XML as UTF-8 bytes).
- **Reply-To:** set only when the office `emailContacto` is valid, which also picks the closing-line variant.

`ProcessadorEntregaEmail` runs one claimed delivery end to end:
1. Loads the snapshot and revalidates the recipient.
2. Reads or ensures the PDF and composes the message.
3. Renews the lease immediately before the single send.
4. Records ENVIADO, PENDENTE with backoff, or FALHOU.
5. Only after the FALHOU write commits (`linhas == 1`), notifies once per episode (`reenvios`) with the real attempt count.

A lost lease means no send and no record. The processor never throws an `Exception`; an `Error` propagates. It has no `@Transactional`.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | ComposicaoEmailFiscal | RED 1c12ba7, GREEN 08660d7 |
| 2 | ProcessadorEntregaEmail | RED 2f99291, GREEN cc5a926 |

## Verification

- `ComposicaoEmailFiscalTest`: **11/11**.
  - FR subject and body; NC origin line (absent on FR).
  - Reply-To valid and invalid (empty, blank, not an email, list).
  - Two attachments with exact names, types and bytes; missing XML gives a fixed IllegalStateException.
  - HTML escapes `<b>X</b> & Y`, contains no `<a `, `<img` or `http`, and has the grey box before the greeting.
  - Every plain line also appears in the HTML; no authorisation words.
  - Total and date equal `FormatacaoFiscal.dinheiro` / `data`.
- `ProcessadorEntregaEmailTest`: **23/23**. Every behaviour in the plan is covered:
  - ENVIADO; PENDENTE with `atraso(2)`; FALHOU on attempt 5 with notification (2, 5).
  - Permanent failure on attempt 1 notifies with tentativas 1, after the register.
  - DOCUMENTO_INEXISTENTE; missing XML, FicheiroFiscalIndisponivel, Storage and FalhaGeracaoPdf are transient; transient on attempt 5 becomes FALHOU.
  - Invalid recipients (empty, no @, CRLF injection, list) never call the gateway.
  - Lost lease: no send, no record. Renewal comes right before send with the configured lease. `linhas == 0`: no notification.
  - Unexpected exceptions: FALHA_INTERNA, PENDENTE or FALHOU on attempt 5. Failures in the snapshot, register or notification never propagate; an Error propagates.
  - `notificarEsgotada` uses episode/tentativas from the item and falls back to the id.
  - PDF, compose and gateway run with no active transaction; no `@Transactional`.
- `grep @Transactional ProcessadorEntregaEmail.java`: none. `compile spotbugs:check`: clean (exit 0).

## Deviations from Plan

1. **Test-only fix in the GREEN commit (cc5a926).** The RED test re-stubbed `registarResultado` with `when(...)` after a throwing stub, which runs the throwing stub. It was changed to `doReturn`. Production code was not affected.
2. Two tests beyond the plan's behaviour list: the lease renewal order, and the `notificarEsgotada` fallback when the snapshot is missing.

## Threat Flags

None. T-137-55..59 are mitigated as planned.

## Self-Check: PASSED
