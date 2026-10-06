---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 08
subsystem: web-components
tags: [fiscal, efatura, web, ui, rbac, a11y]
requires: ["136-04"]
provides:
  - "ComunicacaoEstadoBadge({ estado, descricao? }) — shared neutral state badge"
  - "ModoSimuladoBanner() — page-level banner driven by estado-emissao modoComunicacao"
  - "ComunicacaoFiscalCard({ documento, modoComunicacao }) — read-only detail card"
  - "ReprocessarComunicacao({ documento, tituloCardRef }) — trigger + confirm dialog + mutation"
affects: [136-11]
tech-stack:
  added: []
  patterns:
    - "Icon keys from the React-free lib mapped to lucide components in the badge"
    - "Mutation isolated in its own component file so the detail page stays mutation-free (verify:documentos-fiscais)"
key-files:
  created:
    - web/src/components/shared/comunicacao-estado-badge.tsx
    - web/src/components/shared/modo-simulado-banner.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/comunicacao-fiscal-card.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/reprocessar-comunicacao.tsx
  modified: []
decisions:
  - "The banner enables the estado-emissao query for exact financeiro:view OR exact financeiro:edit (the endpoint's two authorities); it renders nothing for any mode other than SIMULADO"
  - "Timestamps are formatted dd/MM/yyyy HH:mm in Atlantic/Cape_Verde via Intl formatToParts (locale-independent separators)"
  - "After a definitive inline error (COMUNICACAO_ESTADO_INVALIDO / MODO_NAO_SUPORTADO) the confirm button stays disabled and the dismiss reads 'Fechar'; retryable errors (503 / network) keep confirm enabled"
  - "The sr-only close label is written as a literal (closeLabel prop) so the acceptance grep finds it; the other dialog copy comes from lib/comunicacao-fiscal constants"
metrics:
  duration: "~25 min"
  completed: 2026-10-06
  tasks: 2
  files: 4
---

# Phase 136 Plan 08: Communication UI building blocks Summary

The four Phase 136 UI pieces are in place. Each one only composes the 136-04 contracts and calculates nothing.

- **Badge:** one neutral badge (outline/secondary) for every communication state. It shows a visible label, an `aria-hidden` icon and a long-description title. Unknown states show "Estado desconhecido".
- **Banner:** a "Modo simulado" banner that appears only when the backend reports `modoComunicacao === "SIMULADO"`. It uses `role="status"` and neutral notice classes and cannot be dismissed. It uses no `localStorage` and no `NEXT_PUBLIC_*`.
- **Card:** a read-only "Comunicação fiscal" card with these rows:
  - Estado: badge plus description.
  - Ambiente: "Teste (simulado)".
  - IUD: selectable mono text with "Ambiente de teste — sem validade fiscal", or "A gerar..." while it is missing.
  - Tentativas.
  - Última tentativa.
  - Próxima tentativa: only while `PENDENTE`.
  - A plain-text "Última falha" block, only for `REJEITADO`/`ERRO`.
- **Reprocess component:** owns the mutation, with a trigger and a single-step confirm dialog.
  - Focus moves to the title on open.
  - Escape, overlay and X are blocked while pending, and both buttons are disabled.
  - Errors show inline as fixed copy.
  - On success it shows a toast and returns focus to the card heading.
  - The card shows the trigger only when `mostrarReprocessar(podeReprocessarComunicacao(perms), comunicacao, modo)` is true.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | ComunicacaoEstadoBadge + ModoSimuladoBanner | 843a2c9 |
| 2 | ComunicacaoFiscalCard + ReprocessarComunicacao | cec3b04 |

## Verification

- `pnpm exec tsc --noEmit` is clean.
- `pnpm lint` reports 0 errors. The same 20 warnings were already there.
- `pnpm exec vitest run`: 14 files, 318 tests, green.
- `verify:documentos-fiscais` and `verify:faturacao` pass.
- **Acceptance greps:**
  - The forbidden accent/colour classes and `variant="default"` appear 0 times in the badge and the banner.
  - `NEXT_PUBLIC` and `localStorage` appear 0 times in the banner.
  - `role="status"` appears once in the banner.
  - `modoComunicacao === "SIMULADO"` appears once.
  - `useMutation|useReprocessarComunicacao` appears 0 times in the card.
  - `useReprocessarComunicacao` appears 2 times in the reprocess file.
  - `hasScopedPermission` appears 0 times in both files.
  - `select-all` appears once in the card, and `COPY_IUD_TESTE` is referenced.
  - `dangerouslySetInnerHTML` appears 0 times in both files.
  - "Fechar reprocessamento da comunicação" appears once.

## Deviations from Plan

**1. [Rule 3 - Acceptance] Banner condition written as `const simulado = ... === "SIMULADO"`**
- The first version used `!== "SIMULADO"`, which has the same meaning, but the plan's verify grep requires the literal `modoComunicacao === "SIMULADO"`.
- **Commit:** 843a2c9.

Otherwise the plan was executed as written. The UI-SPEC's "text-slate-600 on bg-slate-50" clarification is applied to the IUD test mark.

## Notes for downstream plans

- **136-11:** place `<ModoSimuladoBanner />` once at the top of the list, detail and honorário pages.
- **136-11:** render `<ComunicacaoFiscalCard documento={d} modoComunicacao={estadoEmissao.data?.modoComunicacao} />` after "Valores" (after "Notas de crédito" on an FR) and before "Ligações".
- **136-11:** the list column uses `<ComunicacaoEstadoBadge estado={row.estadoComunicacao} />`.

## Threat Flags

None. T-136-28 through T-136-31 are mitigated as planned:
- T-136-28: neutral variants, test mark and backend-driven banner.
- T-136-29: React text only and `line-clamp-3`.
- T-136-30: exact gate via `mostrarReprocessar`.
- T-136-31: synchronous ref guard plus disabled buttons while pending.

## Self-Check: PASSED

- FOUND: all 4 component files listed in key-files
- FOUND: commits 843a2c9, cec3b04
