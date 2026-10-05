---
phase: 135-nota-de-cr-dito
plan: 11
subsystem: web-financeiro
tags: [frontend, nota-de-credito, dialog, idempotency, rbac, source-gate, vitest]
requires:
  - "135-09: POST /documentos-fiscais/{id}/notas-credito[/pre-visualizacao] (financeiro:manage)"
  - "135-10: NC types, podeEmitirNotaCredito, NC hooks, interpretarErroNotaCredito, notaCreditoFormSchema"
provides:
  - "NotaCreditoDialog: outline trigger + two-step dialog (form -> backend preview -> emission)"
  - "lib/nota-credito-dialogo.ts: reagirAErroNotaCredito(erro, passo) pure step/banner/field/close mapping"
  - "Fiscal detail page: Notas de crédito card (FR), Documento original card (NC), NC Ligações"
  - "verify:documentos-fiscais: Phase 135 assertions"
affects: [135-12, 135-13]
tech-stack:
  added: []
  patterns:
    - "Dialog error routing extracted to a pure, vitest-covered function (plan-checker recommendation)"
    - "Trigger ref through Button asChild + native button (ButtonProps has no ref)"
key-files:
  created:
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/nota-credito-dialog.tsx
    - web/src/lib/nota-credito-dialogo.ts
    - web/src/lib/nota-credito-dialogo.test.ts
  modified:
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx
    - web/scripts/verify-documentos-fiscais.mjs
decisions:
  - "A definitive banner (NC_SOBRE_NC, FATURACAO_DESLIGADA) returns to step 1, where the dismiss button reads 'Fechar'. Non-definitive banners and network errors stay on the current step, so a retry reuses the same key"
  - "Form values persist when the dialog is closed (only success resets them). Reopening with the same values regenerates the same canonical payload, so an unresolved key is reused"
  - "The trigger is rendered only for FR + exact manage + valorCreditavelRestante > 0 + estado-emissao not known to be off. When the FR is fully credited, focus goes to the page h1 (tabIndex -1) through onCloseAutoFocus"
  - "The NC list loading and error states are those of the detail query: the list is part of the same response, so there is no separate card skeleton or retry"
  - "The NC Valores card keeps the 'Líquido recebido' row, because the backend returns valorLiquido for an NC too (UI-SPEC: omitted only when the backend does not return it)"
metrics:
  duration: "~35 min"
  completed: 2026-10-05
  tasks: 2
  files: 5
---

# Phase 135 Plan 11: Emitir Nota de Crédito dialog and FR/NC detail surfaces Summary

This plan delivers UI-SPEC Surfaces 1 and 2. There is still no money math in the client: every amount is a backend value passed through the pt-CV CVE formatter.

**Emission entry point.** A `financeiro:manage` user sees an outline "Emitir Nota de Crédito" button on a Fatura-Recibo that still has value to credit. It opens a two-step dialog:
- **Step 1:** Total/Parcial, valor, motivo, and a description with a {n}/200 counter.
- **Step 2:** the backend-computed preview, followed by the irreversible emission.

**Idempotency key (Phase 134 CR-02 lifecycle):**
- The key belongs to `{ documentoOrigemId: documento.id, ...pedido }`.
- It survives ambiguous failures, including closing the dialog.
- It is dropped on success, on a definitive 4xx, or when the payload changes.
- A synchronous `emitindoRef` guard blocks double clicks.

**Detail page:**
- **FR:** shows its NCs, the total credited and the value still creditable. When the FR is fully credited it shows "Esta fatura-recibo já foi totalmente creditada." instead of the button.
- **NC:** shows the original FR link, the motivo, and links to the honorário and the estorno.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | NotaCreditoDialog (trigger, two steps, key lifecycle, inline errors) + pure helper | 349a3b0 (RED helper tests), 1d27582 |
| 2 | Detail page FR/NC additions + source-gate extension | 09bdfff |

## Verification

- `pnpm verify:documentos-fiscais`: OK. New assertions:
  - all 8 binding dialog strings, plus the step-2 description and the success toast
  - `tentativaParaPedido`, `documentoOrigemId: documento.id`, `chaveIdempotencia: tentativa.chave`, `marcarPorResolver`, `desfechoDefinitivo`, `emitindoRef`, and both NC hooks
  - the detail page copy and links, `podeEmitirNotaCredito(...)` and `<NotaCreditoDialog`
  - no `dangerouslySetInnerHTML` in the dialog or the page, and no `/api/v1` in the dialog
  - the dialog is included in the money-math regex loop
  - the exact-gate constant in `use-faturacao.ts`, and `notas-credito`
  - every existing immutability assertion on the detail page is unchanged
- **Negative self-checks** (each run restored the file afterwards, and the gate went back to exit 0):
  - Appending `// useMutation` to the detail page: the gate exited 1 with "contem token proibido \"useMutation\"".
  - Appending `// const x = 1 * 0.15; dangerouslySetInnerHTML` to the dialog: the gate exited 1 with 3 findings (rate constant, fraction multiplication, dangerouslySetInnerHTML).
- `pnpm verify:faturacao`: OK.
- `npx tsc --noEmit`: clean.
- `pnpm lint`: 0 errors. The 20 warnings are unchanged and all in unrelated files.
- `pnpm exec vitest run`: 12 files, 244 tests, all green. 11 of them are new, in `nota-credito-dialogo.test.ts`:
  - the key binding: the same FR reuses the key and another FR gets a new one; switching to Total gets a new key
  - the full error-to-step matrix: campo, excede, chave reutilizada, definitive banner, non-definitive banner, network on each step, not found, and null
- **Acceptance greps:**
  - The 8 copy strings appear 10 times in the dialog (the 8-occurrence minimum is met).
  - `tentativaParaPedido`, `documentoOrigemId: documento.id`, `marcarPorResolver` and `desfechoDefinitivo` all match.
  - The money-math regex matches 0 times.
  - The dialog has 496 lines, against a minimum of 200.
  - The page has 0 `useMutation` or `method: "POST"` and still contains the immutability line.
  - `notaCreditoDialog` is in the gate script.

## TDD Gate Compliance

- **Helper:** the plan-checker recommendation was applied. RED `test(135-11)` 349a3b0 failed on the missing module. GREEN is part of `feat(135-11)` 1d27582.
- **Plan tasks:** they are `type="auto"` with no `tdd` flag. Their gates are tsc, lint and the source gate.

## Deviations from Plan

**1. [Plan-checker recommendation] Pure helper `lib/nota-credito-dialogo.ts` + vitest**
- **What:** `reagirAErroNotaCredito` routes every interpreted NC error to an action (field, banner, close or ignore), a step, and the definitive flag. The component only applies the result.
- **Not extracted:** the key-lifecycle expression and the dismiss label stay inline in the dialog (`desfechoDefinitivo(e) ? null : marcarPorResolver(atual)`, `{ documentoOrigemId: documento.id, ...novoPedido }`, `"Fechar sem emitir"`). The plan's acceptance greps and the source gate require those literals in the dialog itself. Earlier helper variants of them were removed before the commit, so no logic is duplicated.
- **Files added:** two, outside the plan's `files_modified`.
- **Commits:** 349a3b0, 1d27582

**2. [Rule 3 - Blocking] Trigger ref through `Button asChild`**
- **Issue:** `ButtonProps` does not accept a `ref`, so a ref on `Button` fails tsc.
- **Fix:** the trigger is `<Button asChild variant="outline"><button ref={triggerRef} …></Button>`. It looks the same, and focus can return to it.
- **Commit:** 1d27582

**3. [Scope note] No separate loading or error state for the "Notas de crédito" card**
- **Reason:** the NC list comes in the same `GET /documentos-fiscais/{id}` response. The page-level skeleton and the error/retry state already cover it.

## Threat Flags

None. The surfaces match the plan's threat model:
- T-135-39: `emitindoRef` plus the payload-bound key.
- T-135-40: the exact `podeEmitirNotaCredito` gate, and the backend still returns 403.
- T-135-41: `motivoTexto` is rendered only as React text, and the gate asserts there is no `dangerouslySetInnerHTML`.
- T-135-42: no client amounts.

## Known Stubs

None.

## Self-Check: PASSED

- All 5 files are present.
- Commits 349a3b0, 1d27582 and 09bdfff are present in `git log`.
