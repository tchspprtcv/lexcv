---
status: auto-verified
phase: 134-fatura-recibo-at-mica-nos-honor-rios
source: [134-14-PLAN.md Task 2]
started: 2026-10-04T16:39:00Z
updated: 2026-10-04T17:05:00Z
---

# Phase 134: Live end-to-end verification record

## Stack and method

- **Database:** PostgreSQL 16 in Docker (`lexcv_pg`).
- **Backend:** `mvn -Dmaven.compiler.release=21 spring-boot:run` with `SEED_ENABLED=true` and a throwaway random JWT secret.
- **Web:** `pnpm dev` on :3000.
- **Routing:** every browser and curl request went through `:3000/api/v1` (the Next rewrite).
- **Drivers:** Playwright with the preinstalled Chromium (`/tmp/claude-0/e2e134/*.cjs`), curl with a login cookie jar, and psql through `docker exec`.
- **Env files:** `backend/.env` and `web/.env.local` are git-ignored, confirmed with `git check-ignore`. Both were deleted after the run, and every process and the container were stopped.

**Workaround 1, demo tenant (environment only, no product change):** the first-run wizard 500s on a fresh database (`Set.of` in SetupService, logged as pre-existing in `133-funda-o-fiscal/deferred-items.md` #1). As in 133-08, I booted once, then:
- inserted `system_settings(id=1, is_initialized=true)`;
- truncated `t_tenant` and `t_user`;
- restarted, which seeded the demo tenant with admin@lexcv.cv / Pa$$w0rd and assistente@lexcv.cv / assist123.

**Workaround 2, second tenant (environment only):** `POST /api/v1/platform/tenants` fails with the same `UnsupportedOperationException`, because `provisionTenant` has the same bug. For step 12 I inserted tenant B and its user with SQL:
- a `t_tenant` row "Escritório B (E2E)";
- a `t_user` row admin.b@lexcv.cv, reusing the admin password hash;
- a `t_user_role` row with ADMIN (global-role fallback in ResolucaoPapeisService).

**Money format:** amounts appear in the app's existing pt-CV CVE format, for example `104 347$83`. That is the same figure as the plan's `104 347,83`; the cifrão is pt-CV's decimal separator for CVE.

## Results

| Step | Result | Evidence |
|------|--------|----------|
| 1 | PASS | Stack up as described above; logged in as admin@lexcv.cv (curl 200, and through the UI in every Playwright script). |
| 2 | PASS | `estado-emissao` returned `{"ativa":false}`. On `/financeiro/1`: the button reads "Adicionar"; "Registar pagamento" is absent; 1000 with método "Transferência" opened no dialog (count 0) and showed the toast "Pagamento registado com sucesso.". Both payment rows show "Sem documento fiscal" and keep their "Apagar" buttons. In the database, t_pagamento went 2 → 3 and `t_documento_fiscal` stayed at 0. (My script's first threshold expected 3 rows, but the honorário has 2; the evidence is as listed.) |
| 3 | PASS | `PUT /clientes/{João}` with NIF 512345679 → 200 (morada "Achada Santo António, Praia"). `PUT /faturacao/configuracao` (regime NORMAL) → 200 `completa:true`. `POST /faturacao/ativar` → 200 `ativa:true`. `estado-emissao` → `{"ativa":true,"ambiente":"SIMULADO","taxaRetencaoSugerida":20.0000}`. |
| 4 | PASS | **Form:** shows "Registar pagamento", "Método de pagamento *" and "Aplicar retenção na fonte"; "Adicionar" is gone. Submitting with no método shows "Escolha o método de pagamento.". Ticking retenção pre-fills the rate with "20". **Preview dialog** for 120 000 / Transferência bancária / retenção:<br>• "Confirmar fatura-recibo", with focus on the title (not on the confirm button);<br>• the simulation notice and the "Simulação — sem validade fiscal" badge;<br>• adquirente João Andrade (PostgreSQL Real) / 512345679 / Achada Santo António, Praia;<br>• linha "Honorários por serviços jurídicos — Processo n.º PROC-2026-0001";<br>• **Base tributável 104 347$83, IVA (15%) 15 652$17, Retenção na fonte (20%) - 20 869$57, Total 120 000$00, Líquido recebido 99 130$43**;<br>• the helper line and "Transferência bancária".<br>After the preview, the database still had 0 `t_documento_fiscal` rows, `t_pagamento` unchanged and the series counter at 0 → 0. |
| 5 | PASS | "Emitir fatura-recibo" → toast "Pagamento registado e fatura-recibo SIM-FR-2026/1 emitida.". The dialog closed. The row shows the link SIM-FR-2026/1 → `/financeiro/documentos-fiscais/2cf34b67-…` and "Pagamento faturado: não pode ser apagado.", with 0 Apagar buttons in that row. Conta corrente 46000.00 → 166000.00 (+120 000). `t_documento_fiscal` = 1. |
| 6 | PASS | Same body (honorário 1, 5000, DINHEIRO) and the same key `c00927d3-…`:<br>• first POST → **201** `{"id":5,…,"documentoFiscal":{"id":"f59563f3-…","numeroFormatado":"SIM-FR-2026/2"}}`;<br>• second POST → **200** with the same pagamento id 5 and the same `documentoFiscal.id f59563f3-…`;<br>• same key with valorPago 6000 → **409** `{"code":"CHAVE_REUTILIZADA","message":"Este pedido já foi usado com valores diferentes. …"}`.<br>Documents 1 → 2, so only one extra was created. |
| 7 | PASS | **7a:** date 2026-10-03 (yesterday) → under-field error "Com a faturação ativa, a data do pagamento tem de ser a de hoje. Escolha a data de hoje ou deixe o campo vazio.", focus on `#fat-dataPagamento`, no dialog, no toast.<br>**7b:** cliente NIF set to 012345678 → banner "O cliente não tem um NIF válido. Corrija o NIF do cliente (9 dígitos, começa por 1 a 9) e tente de novo." with an "Abrir cliente" link → `/clientes/686b5ce2-…`, and no dialog. The NIF was restored to 512345679.<br>**7c (extra, D-10):** with the POST aborted at the network level, the dialog stays open with "Não foi possível emitir a fatura-recibo. Verifique a ligação e tente novamente.". The retry sent the **same** key and emitted SIM-FR-2026/3 (documents +1). After "Voltar e editar" and reopening, a new key was used. |
| 8 | PASS | **API:** `DELETE /pagamentos/5` (faturado) → **409** `{"code":"PAGAMENTO_FATURADO","message":"Este pagamento tem uma fatura-recibo emitida e não pode ser apagado."}`; the payment remains.<br>**Honorário delete (UI):** the dialog stays open with the inline `role=alert` "Não é possível apagar este honorário porque tem documentos fiscais emitidos.", the cancel label becomes "Fechar", there are 0 toasts, and the honorário remains.<br>**Cliente delete** from the Clientes list (window.confirm accepted) → toast "Erro 409: Não é possível apagar este cliente porque tem documentos fiscais emitidos."; the cliente remains. |
| 9 | PASS | **Navigation:** the Financeiro header's outline "Documentos fiscais" button opens the list with the spec description, 10 rows, "Página 1 de 2", newest first; "Limpar filtros" is hidden. "Seguinte" sets `page=2` in the URL ("Página 2 de 2").<br>**Filters:**<br>• Estado = Pendente → URL `?estado=PENDENTE`, page param removed, back to page 1;<br>• Tipo = Fatura-Recibo → 10 rows;<br>• Cliente = João (Combobox) → only João's documents, with `clienteId` in the URL;<br>• De = tomorrow → "Nenhum documento encontrado" / "Nenhum documento corresponde aos filtros. Altere ou limpe os filtros.";<br>• Até < De → "A data final não pode ser anterior à inicial.";<br>• De = Até = today → João's documents.<br>**URL state:** a reload keeps the filters; "Limpar filtros" → bare URL and 10 rows.<br>**Re-run after fixes:** my hard-coded counts predated step 11, so the re-run reported FAIL on counts only. Behaviour was correct: 13 documents, and the João filter now also includes the merged document, shown under its snapshot name "João Andrade (duplicado)". |
| 10 | PASS | **Header:** `h1` "SIM-FR-2026/1" in `font-mono`, badges Fatura-Recibo / Pendente (plus helper) / Simulação — sem validade fiscal, and the notice.<br>**Cards:** Emitente (Gabinete Jurídico Demonstração, Lda / 500000001 / Plateau… / Praia / Normal); Adquirente snapshot plus helper; Linha; Valores with the same figures as step 4 and "Conta corrente creditada do total.", "Transferência bancária", Data de emissão.<br>**Ligações:** Ver pagamento → `/financeiro/1#pagamento-4` (lands on that row), Ver honorário → `/financeiro/1`, Ver cliente → `/clientes/686b5ce2-…`. There are no Editar/Apagar/Anular/Enviar/Eliminar buttons, and the page ends with "Documento imutável: não pode ser alterado nem apagado.".<br>**Snapshot:** changing the cliente's morada (PUT 200; the database shows "Nova Morada Alterada 99, Assomada") and reloading → the detail still shows "Achada Santo António, Praia".<br>**Not found:** an unknown UUID and a non-UUID id both show "Documento fiscal não encontrado", with no toast. Re-run after fixes: PASS. |
| 11 | PASS | I created a duplicate cliente (NIF 298765432) with a processo, an honorário and a faturado payment (SIM-FR-2026/13). Merging it into João on `/clientes/merge` gave the toast "Merge concluído. Processos: 1, …, saldo transferido: 11500.00.". In psql the document changed from `f0d433fb-…\|João Andrade (duplicado)\|298765432` to `686b5ce2-…(primary)\|João Andrade (duplicado)\|298765432`, so the buyer snapshot is unchanged, and the duplicate cliente row is gone. The detail page's "Ver cliente" → `/clientes/686b5ce2-…` (primary). |
| 12 | PASS | **assistente@lexcv.cv:**<br>• `/financeiro/documentos-fiscais` shows the access-denied state "Não tem permissão para consultar os documentos fiscais.";<br>• the detail page shows "Não tem permissão para consultar este documento fiscal.";<br>• an in-browser `fetch('/api/v1/documentos-fiscais')` → **403**;<br>• curl GET list, detail and estado-emissao → **403** `{"message":"Acesso negado."}`.<br>**Tenant B admin:**<br>• the list → 200, `totalElements 0`; the UI shows "Ainda não há documentos fiscais";<br>• tenant A's document ids `2cf34b67-…` and `f4045e73-…` → **404** `DOCUMENTO_FISCAL_NAO_ENCONTRADO`; the UI shows "Documento fiscal não encontrado", and no number leaks. |
| 13 | PASS | Next dev, the backend and the `lexcv_pg` container were stopped and removed (`ps` shows 0 matching processes; `docker ps -a` is empty). `backend/.env` and `web/.env.local` were deleted, and `git status --porcelain` is clean. |

**Extra checks:**
- At 375px in dark mode, the list, detail and honorário pages have 0px horizontal overflow, and the "Ambiente" column is hidden below md.
- In the dialog, the vertical gaps between blocks measure 16px after the fix. Without retenção, the retenção row is absent.

## Supplementary live run (EMIS-03 isento, EMIS-10 meio codes)

The main run used regime Normal and checked only the method labels. A second, fresh stack was brought up with the same recipe and workaround, then torn down the same way (0 processes, 0 containers, env files deleted, `git status` clean).

| Check | Result | Evidence |
|-------|--------|----------|
| EMIS-03 isento | PASS | Configuration saved with regime ISENTO and motivo 5, then activated. Preview for 50 000 / Transferência bancária / retenção 20:<br>• Base tributável 50 000$00;<br>• "IVA" → "Isento" and "Motivo de isenção" → "5 — Outras isenções", with no "IVA (x%)" row;<br>• Retenção na fonte (20%) - 10 000$00, Total 50 000$00, Líquido recebido 40 000$00.<br>Emitted SIM-FR-2026/1. The detail page shows Regime de IVA Isento, the motivo row, the line cell "Isento (5)" and the same values. In the database: `50000.00\|0.00\|0.0000\|ISENTO\|5\|10000.00\|50000.00\|40000.00`. |
| EMIS-10 meios | PASS | One emission per method: DINHEIRO → `10`, TRANSFERENCIA → `30`, CHEQUE → `20`, CARTAO → `48`, OUTRO → `ZZZ`, each snapshotted in `t_documento_fiscal.meio_pagamento_codigo`. An unknown method ("BITCOIN") and a missing method both return 422 `METODO_PAGAMENTO_INVALIDO`, `campo: metodo`. The codes remain [ASSUMED] UNCL4461 until Phase 136 confirms them (STATE decision). |

## Defects found and fixed during the run

1. **Doubled vertical spacing in the preview dialog** (~32px; the UI-SPEC says 16px). `DialogContent` is already `grid gap-4`, so the extra `space-y-4` doubled it. Fixed in e6d5e2d and re-measured at 16px.
2. **Breadcrumb squeezed into a narrow column at 375px** on the fiscal document pages. The header did not wrap; fixed with `flex-wrap` in 63d99eb and re-checked by screenshot.

## Observations (not defects of this phase)

- The legacy "Método" column of the honorário payments table shows the stored value, so billing-on payments show the enum name (for example `TRANSFERENCIA`). The new "Documento fiscal" column and the document detail page show the label "Transferência bancária". Showing the label in that column too would be a small follow-up.
- The setup wizard and platform tenant provisioning both 500 on `Set.of` (pre-existing, deferred-items #1). This phase did not change it.

## Summary

13/13 steps PASS with evidence, auto-verified on a live stack, plus the supplementary isento and meio-code checks. Two cosmetic defects were found and fixed during the run.
