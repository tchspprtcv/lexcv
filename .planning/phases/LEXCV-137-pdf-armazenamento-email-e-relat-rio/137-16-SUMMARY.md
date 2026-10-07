---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 16
subsystem: web/fiscal email delivery contracts
tags: [web, types, presentation, badge, notifications, frontend-burro]
requires: [137-03, 137-11]
provides:
  - types: EstadoEntregaEmail, EntregaEmail, ReenviarEmailResposta, DescargaPdfResposta; DocumentoFiscalResumo.estadoEntregaEmail, DocumentoFiscalDetalhe.entregaEmail, ConfiguracaoFiscal.smtpConfigurado; Phase 137 error codes in CodigoErroFaturacao
  - lib/entrega-email.ts {ESTADOS_ENTREGA_EMAIL, ESTADO_ENTREGA_DESCONHECIDO, apresentacaoEntregaEmail, descricaoEntregaEmail, textoTentativas, mostrarReenviar, rotuloReenviar, deveSondarEntrega, INTERVALO_ATUALIZACAO_ENTREGA_MS, interpretarErroReenvio, interpretarErroDescarga, interpretarErroExportacao, STATUS_SEM_TOAST_DESCARGA, COPY_* constants}
  - components/shared/entrega-email-badge.tsx (EntregaEmailBadge {estado, titulo?})
  - EMAIL_FISCAL_FALHOU in NotificacaoCategoria + label/variant maps + non-silenceable list
affects: [137-20/21/23 (detail card, resend dialog, list column, client tab, export dialog use these), podeReenviarEmail is added by a later plan]
tech-stack:
  added: []
  patterns: [lib/comunicacao-fiscal.ts mirror (exhaustive Record, unknown fallback, fixed-copy error interpreter)]
key-files:
  created:
    - web/src/lib/entrega-email.ts
    - web/src/lib/entrega-email.test.ts
    - web/src/components/shared/entrega-email-badge.tsx
  modified:
    - web/src/types/faturacao.ts
    - web/src/types/notificacoes.ts
    - web/src/lib/notificacao-categoria.ts
    - web/src/lib/notificacao-categoria.test.ts
decisions:
  - "Resend error result reuses the 136 shape {mensagem, definitivo, fecharDialogo, refrescar} rather than a single 'fechar|manter|refetch' flag, because the UI-SPEC rows combine 'Fechar' with a refetch (409, SMTP_NAO_CONFIGURADO, ENVIO_EMAIL_DESLIGADO). A 409 with an unknown code falls back to the ENTREGA_ESTADO_INVALIDO copy; other unknown 4xx/5xx use the generic retry copy"
  - "descricaoEntregaEmail(estado, {podeReenviar, reenviavel, tentativas?}): FALHOU without a numeric tentativas (list row) gives the short 'O envio do email falhou.'; the static ESTADOS_ENTREGA_EMAIL.FALHOU.descricao is that short variant"
  - "rotuloReenviar uses a Record over all six states (Enviar only for SEM_EMAIL); the trigger is still shown only when mostrarReenviar is true"
  - "INTERVALO_ATUALIZACAO_ENTREGA_MS re-exports the 136 INTERVALO_ATUALIZACAO_MS (15000), so there is one polling constant"
  - "STATUS_SEM_TOAST_DESCARGA [404, 500, 502, 503, 504] is exported so download callers pass it as semToastParaStatus and the generic apiFetch toast does not duplicate the specific one"
metrics:
  duration: ~25min
  completed: 2026-10-07
  tasks: 2
  files: 7
---

# Phase 137 Plan 16: Web contracts for email delivery Summary

The web side now mirrors the Phase 137 backend contract.

**Types.** `EstadoEntregaEmail` (six states), `EntregaEmail`, `ReenviarEmailResposta` and `DescargaPdfResposta`. The list row gets a mandatory `estadoEntregaEmail`, the detail gets `entregaEmail`, and the configuration gets `smtpConfigurado`. The new error codes are added to `CodigoErroFaturacao`.

**`lib/entrega-email.ts`** is the pure presentation module, mirroring `comunicacao-fiscal.ts`:
- An exhaustive Record gives each state its UI-SPEC label, neutral variant (only FALHOU is `secondary`) and icon, with an "Estado desconhecido" fallback.
- The long descriptions are verbatim from the UI-SPEC. FALHOU counts the attempts ("1 tentativa" / "{n} tentativas") and adds "Pode reenviar o email." only with the gate and `reenviavel`.
- `mostrarReenviar` requires the exact-permission boolean AND the backend's `reenviavel`. `rotuloReenviar` returns "Enviar email" only for SEM_EMAIL.
- `deveSondarEntrega` is true while either the communication or the delivery is PENDENTE.
- Error interpreters map the resend (1d), download (1a) and monthly export (Surface 4) errors to fixed copy, never backend text.

**Badge and notifications.** `EntregaEmailBadge` is the single place that maps a state to a badge. `EMAIL_FISCAL_FALHOU` is labelled "Falha de envio de email fiscal", badged red and non-silenceable, in sync with `CategoriaNotificacao.java`.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | Types + lib/entrega-email.ts + tests | RED fa0f686, GREEN 121deb3 |
| 2 | EntregaEmailBadge + EMAIL_FISCAL_FALHOU registrations | RED ffa19eb, GREEN 7dc4870 |

## Verification

- `vitest run src/lib/entrega-email.test.ts`: **19/19**.
  - Six states plus fallback (including `"toString"` and null).
  - Only FALHOU is secondary.
  - The five verbatim descriptions; FALHOU at 5/1/2/1 attempts with and without the resend promise; the short variant; no "(s)" and no fixed "5 tentativas"; `textoTentativas`.
  - `mostrarReenviar`, `rotuloReenviar`, `deveSondarEntrega`.
  - Every 1d code, 404, 503, network/5xx and 401/403.
  - Export: 422 MES_INVALIDO, 503 and network. Download: 404, 503 (×3 codes) and others.
  - A scan of every exported string for Entregue/Recebido/Lido/Autorizado/Aprovado/Validado finds none.
- `vitest run src/lib/notificacao-categoria.test.ts`: **5/5**. Full web suite: **15 files, 347 tests**, all passing.
- `pnpm exec tsc --noEmit`: clean. No fixture fallout, because no test builds the extended interfaces.
- `pnpm lint`: 0 errors. There are 20 warnings, all in files this plan did not touch, and eslint on the touched files is clean.

## Deviations from Plan

1. **[Rule 3 - Blocking] `notificacao-categoria.test.ts` pinned the option count at 10.** Raised to 11 by the new category.
2. **Resend error result shape:** see decisions (the 136 four-field shape instead of a single flag).
3. **Extra exports:** `STATUS_SEM_TOAST_DESCARGA` and the Surface 1a/1b/1d/4 copy constants (`COPY_*`), so later screen plans do not inline text.
4. **The resend error codes were also added to `CodigoErroFaturacao`.**

## Known Stubs

None. These are contracts only; the screens that use them come in later plans.

## Self-Check: PASSED
