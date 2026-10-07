---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 03
subsystem: backend/notifications
tags: [notifications, fiscal, email-delivery, non-silenceable]
requires: []
provides:
  - CategoriaNotificacao.EMAIL_FISCAL_FALHOU(false)
  - NotificacaoComunicacaoFiscal.destinatariosFalhaFiscal(UUID tenantId) -> List<UUID>
  - NotificacaoEntregaEmailFiscal.notificarFalhaPersistente(tenantId, documentoId, numeroFormatado, episodio, tentativas)
  - NotificacaoEntregaEmailFiscal.textoTentativas(int)
affects: [137-14 (email processor calls the notifier after the FALHOU commit), 137-16/137-23 (web category registration)]
tech-stack:
  added: []
  patterns: [single shared recipient rule for fiscal-failure categories, per-episode dedup via entidadeId documentoId:episodio]
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/NotificacaoEntregaEmailFiscal.java
    - backend/src/test/java/com/lexcv/services/fiscal/NotificacaoEntregaEmailFiscalTest.java
  modified:
    - backend/src/main/java/com/lexcv/models/CategoriaNotificacao.java
    - backend/src/test/java/com/lexcv/models/CategoriaNotificacaoTest.java
    - backend/src/main/java/com/lexcv/services/fiscal/NotificacaoComunicacaoFiscal.java
decisions:
  - "Null/blank número fallback: 'do documento' with no number (titulo 'Falha no envio por email do documento')"
  - "If recipient resolution itself throws, the email notifier returns 0 without propagating (in addition to the never-throws contract of destinatariosFalhaFiscal)"
metrics:
  duration: ~15min
  completed: 2026-10-07
  tasks: 2
  files: 5
---

# Phase 137 Plan 03: EMAIL_FISCAL_FALHOU notification Summary

This plan adds the non-silenceable `EMAIL_FISCAL_FALHOU` category. It also adds `NotificacaoEntregaEmailFiscal`, which notifies exactly the `COMUNICACAO_FISCAL_FALHOU` recipients (through the new shared `destinatariosFalhaFiscal`) once per failure episode. The UI-SPEC copy is fixed and count-aware ("1 tentativa" / "{n} tentativas"), and the notification never contains an email address or SMTP text.

## Tasks

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 (RED) | Category test expects 11 / EMAIL_FISCAL_FALHOU | 2188fb3 | CategoriaNotificacaoTest |
| 1 (GREEN) | Category + shared recipient extraction | c153d1a | CategoriaNotificacao, NotificacaoComunicacaoFiscal |
| 2 (RED) | Notifier tests | a32f655 | NotificacaoEntregaEmailFiscalTest |
| 2 (GREEN) | NotificacaoEntregaEmailFiscal | 415688c | NotificacaoEntregaEmailFiscal |

## Details

- **`CategoriaNotificacao`:** `EMAIL_FISCAL_FALHOU(false)` is added after `COMUNICACAO_FISCAL_FALHOU`. The comment blocks now list three non-silenceable categories. `NotificacaoService` needed no change: it already reads `isSilenciavelCategoria`.
- **`NotificacaoComunicacaoFiscal`:**
  - The user query, active filter and effective-permission check moved into `public List<UUID> destinatariosFalhaFiscal(UUID tenantId)`.
  - It never throws. A failed user read returns an empty list; if one user's permissions can't be resolved, that user is skipped.
  - `notificarFalhaPersistente` now iterates that list with the same per-recipient try/catch. The constructor, constants and copy are unchanged.
  - `NotificacaoComunicacaoFiscalTest` passes **unchanged (10/10)**.
- **`NotificacaoEntregaEmailFiscal`:**
  - The constructor takes only `(NotificacaoComunicacaoFiscal, NotificacaoService)`.
  - Constant `CATEGORIA = "EMAIL_FISCAL_FALHOU"`.
  - The rest matches the analog: `entidadeTipo` comes from `NotificacaoComunicacaoFiscal.ENTIDADE_TIPO`, `entidadeId` is `documentoId:episodio`, and `linkUrl` is `/financeiro/documentos-fiscais/{id}`.
  - Errors are logged with ids and the exception class only. It is non-transactional.

## Verification

- `CategoriaNotificacaoTest` 5/5, `NotificacaoComunicacaoFiscalTest` 10/10 (unchanged).
- `NotificacaoEntregaEmailFiscalTest` **10/10**. It covers:
  - the UI-SPEC arguments, with tentativas=5;
  - the singular "1 tentativa", with no "5 tentativas" and no "(s)";
  - `textoTentativas(1/2/5)`;
  - an already-notified episode not being counted, and different episodes getting different dedup keys;
  - one recipient throwing while the other is still notified;
  - recipient resolution throwing, which returns 0;
  - a null número giving neutral copy with no "null" and no "@";
  - no "@" in any text;
  - the constructor shape.
- `NotificacaoServiceTest` 39/39.
- SpotBugs clean.
- **Full backend unit suite (`mvn test`): 1244 tests, 0 failures, 0 errors.**

## Deviations from Plan

**1. [Rule 2 - Correctness] The notifier also guards its own call to `destinatariosFalhaFiscal`.** That call is wrapped in try/catch and returns 0, so the "never propagates" truth holds even if the collaborator breaks its contract (for example, a mock or a future change). This is covered by `falhaAResolverDestinatariosDevolveZeroSemLancar`.

## Known Stubs

None. The web registration of the category (label, badge, non-silenceable list) is planned in 137-16/137-23. The caller (email processor, after the FALHOU commit) is planned in 137-14.

## TDD Gate Compliance

Both tasks have a `test(137-03)` RED commit (2188fb3, a32f655) followed by a `feat(137-03)` GREEN commit (c153d1a, 415688c).

## Self-Check: PASSED

All 5 files are present; commits 2188fb3, c153d1a, a32f655 and 415688c are in `git log`.
