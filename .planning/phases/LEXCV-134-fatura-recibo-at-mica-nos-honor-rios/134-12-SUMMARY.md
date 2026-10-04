---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 12
subsystem: web-financeiro
tags: [fiscal, efatura, frontend, dialog, idempotency, delete-guard]
requires:
  - "134-11: useEstadoEmissao, usePreVisualizacaoFaturacao, useCreatePagamento [409,422], pagamentoFaturadoFormSchema, paraPedidoPagamentoFaturado, gerarChaveIdempotencia, interpretarErroEmissao, mensagemGuardaFiscal, podeLerDocumentosFiscais"
provides:
  - "PagamentoFaturadoForm (billing-on payment form, preview -> confirm with idempotency key)"
  - "PagamentoFaturadoDialog (UI-SPEC Surface 1 preview dialog)"
  - "PagamentosCard (Documento fiscal column, row anchors #pagamento-{id}, inline 409 delete guard)"
  - "Honorário delete dialog with inline 409"
  - "DialogContent optional closeLabel prop"
affects: [134-13, 134-14]
tech-stack:
  added: []
  patterns:
    - "Controlled AlertDialog + preventDefault + mutateAsync so 409 guard messages stay inside the dialog"
    - "Synchronous ref guard + disabled state against double confirmation"
key-files:
  created:
    - web/src/app/(dashboard)/financeiro/[id]/pagamento-faturado-form.tsx
    - web/src/app/(dashboard)/financeiro/[id]/pagamento-faturado-dialog.tsx
    - web/src/app/(dashboard)/financeiro/[id]/pagamentos-card.tsx
  modified:
    - web/src/app/(dashboard)/financeiro/[id]/page.tsx
    - web/src/components/ui/dialog.tsx
decisions:
  - "useEstadoEmissao is enabled only with canEditFinanceiro && podeLerDocumentosFiscais (exact financeiro:view), matching the backend gate; disabled query => off form (isLoading false)"
  - "The idempotency key is generated after a successful preview, when the dialog opens; closing the dialog clears it; a network/5xx failure keeps the dialog open and retries with the same key"
  - "Banner focus moves through a useEffect on the banner state (React Compiler rejects ref access inside handlers passed to handleSubmit)"
  - "Success toast uses documentoFiscal.numeroFormatado from the POST response; falls back to the legacy copy if the reference is absent"
  - "Honorário legacy 409 (with payments, no code) is shown inline using the ApiError message"
metrics:
  duration: "~25 min"
  completed: 2026-10-04
  tasks: 2
  files: 5
---

# Phase 134 Plan 12: Billing-on payment form, preview dialog and payments column Summary

This plan wires the honorário page for billing. When billing is active, the payment form asks the backend for a preview and shows it in a dialog. The Fatura-Recibo is emitted only when the user clicks "Emitir fatura-recibo", and the request carries an idempotency key. Each payment row now shows its fiscal document. Delete-guard 409s appear inside the delete dialogs instead of as toasts.

## What was built

- **`pagamento-faturado-form.tsx`** (form fields):
  - "Valor pago", "Data do pagamento" (defaults to today in Atlantic/Cape_Verde) and "Método de pagamento *", each with the UI-SPEC helper copy.
  - The "Aplicar retenção na fonte" checkbox. The first time it is checked, an empty "Taxa de retenção (%)" is filled with `taxaRetencaoSugerida`.
  - Every field is linked to its error with `aria-invalid` / `aria-describedby`.
- **`pagamento-faturado-form.tsx`** (submit and emit):
  - Submitting shows "A calcular..." and calls the preview. If the preview succeeds, the form generates the key and opens the dialog.
  - `interpretarErroEmissao` decides where errors go:
    - field errors get `setError` and `setFocus`;
    - client-data errors show a banner with an "Abrir cliente" link;
    - key-reuse, generic and network errors show a banner.
  - Emitting sends `{...pedido, chaveIdempotencia}`. A network error keeps the dialog open with the network banner and the same key. Any other error closes the dialog, clears the key and is mapped as for the preview.
  - On success the dialog closes, the form resets and the toast reads `Pagamento registado e fatura-recibo {numero} emitida.`
- **`pagamento-faturado-dialog.tsx`:**
  - It is a `Dialog`, laid out per Surface 1:
    - the simulation notice and an outline badge;
    - buyer block, then the line description;
    - a `dl` of amounts: base, IVA or Isento with the exemption reason, retention with a "- " prefix, Total and Líquido recebido, plus the helper line;
    - payment method and date.
  - Amounts are formatted with `pt-CV` / `CVE`, `tabular-nums`, right-aligned. Nothing is computed.
  - The title receives focus when the dialog opens, so the confirm button is never auto-focused.
  - The dialog cannot be closed while emitting: Esc, outside clicks and `onOpenChange(false)` are all blocked.
  - The X button is labelled "Fechar pré-visualização" through a new optional `closeLabel` prop on `DialogContent`. The default stays "Fechar", so other dialogs are unchanged.
- **`pagamentos-card.tsx`:**
  - The new "Documento fiscal" column shows a `font-mono` link to `/financeiro/documentos-fiscais/{id}`, or "Sem documento fiscal".
  - Each row has the anchor `id="pagamento-{id}"`.
  - A faturado payment has no delete button and shows "Pagamento faturado: não pode ser apagado." instead.
  - A legacy payment keeps the "Apagar pagamento?" dialog, now controlled. A guard 409 shows inline (`role="alert"`) and the cancel label becomes "Fechar". Other errors show the existing toast.
- **`page.tsx`:**
  - It renders `PagamentoFaturadoForm` when `estadoEmissao.data?.ativa`.
  - The honorário delete `AlertDialog` is now controlled. A 409 (fiscal guard, or the legacy "pagamentos registados" rejection) shows inline with the cancel label "Fechar" and no toast. Other errors close the dialog and keep the existing `deleteHonorarioError` and toast.

**Billing-off path (reviewer check):** `git show 5e335c4 -- page.tsx` shows the off-path form JSX, labels ("Adicionar"), toasts and `onSubmitPagamento` byte-identical. The only additions are the conditional branch in front of the form and `|| estadoEmissao.isLoading` in the submit `disabled`.

**Clientes and processos deletes:** unchanged. The cliente list uses `window.confirm`, which has no slot for an inline message, so its 409 reaches the user through apiFetch's existing toast with the Surface 5 copy from the backend. There is no processo delete UI.

## Verification

| Check | Result |
|-------|--------|
| `pnpm exec tsc --noEmit` | clean |
| `pnpm lint` | 0 errors; 21 warnings, none in files of this plan |
| `pnpm exec vitest run` | 157/157 |
| `pnpm build` (env set) | success |
| `pnpm verify:faturacao` | OK |
| Money-math grep on form + dialog | no matches |

All acceptance greps for both tasks match: copy strings, `gerarChaveIdempotencia`, `role="alert"` in both files, and the `pagamento-${` anchor.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 5e335c4 | Billing-on form + preview dialog + page wiring (+ dialog closeLabel) |
| 2 | cf5a696 | PagamentosCard + inline 409 guards (pagamento, honorário) |

## Deviations from Plan

**1. [Rule 3 - Blocking] `DialogContent` gained an optional `closeLabel` prop**
- **Found during:** Task 1
- **Issue:** The primitive hard-codes the X button's sr-only label "Fechar", but UI-SPEC requires "Fechar pré-visualização".
- **Fix:** Added an optional prop that defaults to "Fechar", so existing usages are unaffected. The plan allowed adapting how `dialog.tsx` is used.
- **Files:** web/src/components/ui/dialog.tsx
- **Commit:** 5e335c4

**2. [Rule 1 - Lint/compiler] `useWatch` instead of `form.watch`, banner focus in an effect**
- **Found during:** Task 1
- **Issue:** The React Compiler lint failed on these two patterns.
- **Fix:** Rewrote both. Behaviour is the same.
- **Commit:** 5e335c4

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: pagamento-faturado-form.tsx, pagamento-faturado-dialog.tsx, pagamentos-card.tsx
- FOUND: 5e335c4, cf5a696
