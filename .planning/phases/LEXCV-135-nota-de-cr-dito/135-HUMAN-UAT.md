---
status: auto-verified
phase: 135-nota-de-cr-dito
source: [135-13-PLAN.md Task 1, Task 2]
started: 2026-10-05T09:36:00Z
updated: 2026-10-05T10:05:00Z
---

# Phase 135: Gate and live end-to-end verification record

## Gate (Task 1)

All commands were run from a clean tree on 2026-10-05. Docker was available (`~/.docker-java.properties` api.version=1.44), so every Testcontainers IT ran against a real PostgreSQL 16.

| Gate | Result |
|------|--------|
| `mvn -Dmaven.compiler.release=21 verify` | BUILD SUCCESS. Surefire **1007** tests, 0 failures, 0 errors, 0 skipped. Failsafe **122** tests, 0 / 0 / 0. |
| `mvn -q -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check` | exit 0 |
| web `pnpm exec vitest run` | 12 files, **244** tests, all passed |
| web `pnpm exec tsc --noEmit` | clean |
| web `pnpm lint` | 0 errors (20 pre-existing warnings in unrelated files) |
| `pnpm verify:faturacao` / `pnpm verify:documentos-fiscais` | OK / OK |
| `pnpm build` (BACKEND_API_ORIGIN, NEXT_PUBLIC_API_BASE_PATH inline) | success; `/financeiro/documentos-fiscais` (static) and `/financeiro/documentos-fiscais/[id]` (dynamic) listed |
| `git diff --quiet HEAD -- web/package-lock.json` | unchanged |

**Fiscal ITs (failsafe, executed, 0 skipped):**

| IT | Tests |
|----|-------|
| MigracaoFiscal133IT | 8 |
| MigracaoFiscal134IT | 6 |
| MigracaoFiscal135IT | 4 |
| DocumentoFiscalRepositoryIT | 18 |
| PagamentoFaturadoServiceIT | 11 |
| PagamentoFaturadoConcorrenciaIT | 4 |
| GuardasDocumentoFiscalConcorrenciaIT | 11 |
| NotaCreditoServiceIT | 10 |
| NotaCreditoConcorrenciaIT | 3 |

## Live end-to-end run (Task 2, checkpoint auto-verified)

### Stack and method

- **Database:** PostgreSQL 16 in Docker (`lexcv_pg`).
- **Backend:** `mvn -Dmaven.compiler.release=21 spring-boot:run`, `SEED_ENABLED=true`, random throwaway JWT secret.
- **Web:** `pnpm dev` on :3000. Every browser and curl request went through `:3000/api/v1` (the Next rewrite).
- **Drivers:** Playwright with the preinstalled Chromium (`/tmp/claude-0/e2e135/*.cjs`), curl with login cookie jars, psql through `docker exec`.
- **Env files:** `backend/.env` and `web/.env.local` are git-ignored (`git check-ignore`: `backend/.gitignore:4:*.env`, `web/.gitignore:34:.env*`). Both were deleted at the end.

**Workaround 1, demo tenant (environment only, no product change):** the first-run wizard still 500s on a fresh database (pre-existing `Set.of` bug, 133 deferred-items #1). As in 133-08 / 134-14, I booted once, then:
- set `system_settings(id=1).is_initialized = true`;
- truncated `t_tenant` and `t_user` (cascade);
- restarted, which seeded admin@lexcv.cv / Pa$$w0rd and assistente@lexcv.cv.

**Workaround 2, second tenant (environment only):** `POST /platform/tenants` has the same bug, so tenant B ("Escritório B (E2E)") and admin.b@lexcv.cv (ADMIN global role, admin's password hash) were inserted with SQL.

**Edit-only user:** created through the product's own admin API:
- `PUT /admin/rbac` added `financeiro:edit` to the TECNICO office role → 200. The DB then shows TECNICO = `financeiro:view,financeiro:edit` (no manage).
- `POST /admin/users` created tecnico.fin@lexcv.cv with TECNICO → 201.

**Money format:** the app renders amounts with the existing `Intl` pt-CV CVE formatter, for example `17 391$30`. pt-CV uses `$` as the CVE decimal separator. CLDR pt also sets minimumGroupingDigits=2, so 4-digit amounts have no group separator: the plan's `2 608,70` renders as `2608$70` and `3 478,26` as `3478$26`. The values are identical. This is the same formatter Phase 134 uses everywhere (`new Intl.NumberFormat("pt-CV",{style:"currency",currency:"CVE"}).format(2608.70)` → `2608$70`), so it is not a defect.

### Results

| Step | Req | Result | Evidence |
|------|-----|--------|----------|
| 1 | — | PASS | **Stack up** with both workarounds above.<br>**Faturação:** `PUT /faturacao/configuracao` (regime NORMAL) → `completa:true`; `POST /faturacao/ativar` → `ativa:true`; `estado-emissao` → `{"ativa":true,"ambiente":"SIMULADO","taxaRetencaoSugerida":20.0000}`.<br>**Data:** cliente João Andrade NIF set to 512345679 (PUT 200). New processo PROC-NC-135. Honorário id 3 with valorTotal 120 000 and dataAcordo 2026-08-26 (40 days ago). |
| 2 | — | PASS | **UI** (`/financeiro/3`): 120 000, Transferência, retenção 20% → FR **SIM-FR-2026/1** (`bcf459dd-…`). DB: base 104347.83, IVA 15652.17, retenção 20869.57, total 120000.00.<br>**Baseline:** conta-corrente saldo **S0 = 165 000** (45 000 + 120 000); honorário `totalPago` **120 000**; dashboard `valores_recebidos_mes` **K0 = 120 000**. |
| 3 | NCRD-01 | PASS | **FR detail:**<br>• "Notas de crédito" card shows "Ainda não foram emitidas notas de crédito para esta fatura-recibo.";<br>• "Valor ainda creditável" shows `120 000$00`;<br>• exactly 1 "Emitir Nota de Crédito" button, outline variant. |
| 4 | NCRD-01 | PASS | **Input:** Parcial, 20 000, motivo "Correção de valor", descrição "Valor acordado revisto" → "Pré-visualizar nota de crédito".<br>**Step 2 shows:**<br>• Base tributável `17 391$30`, IVA (15%) `2608$70`, Retenção na fonte (20%) `3478$26` (see "Money format");<br>• Total a creditar `20 000$00`, Valor creditável restante `100 000$00`;<br>• "Corrige a fatura-recibo SIM-FR-2026/1" and "Correção de valor — Valor acordado revisto".<br>**After the preview:** psql still shows **0** NC rows.<br>**Emit:** toast "**Nota de crédito SIM-NC-2026/1 emitida.**". DB: `SIM-NC-2026/1\|SIM-NC-2026\|17391.30\|2608.70\|3478.26\|20000.00\|CORRECAO_VALOR\|<FR id>`, in its own series SIM-NC-2026. |
| 5 | NCRD-03 | PASS | **Coherence** (all moved by −20 000):<br>• saldo 165 000 → **145 000** (S0 − 20 000);<br>• honorário `totalPago` 120 000 → **100 000**;<br>• KPI 120 000 → **100 000** (K0 − 20 000).<br>**Payments list row:** "05/10/2026 \| `-20 000$00` \| — \| #4 \| Estorno (NC n.º SIM-NC-2026/1) \| Estorno: não pode ser apagado.". The amount cell is `text-red-600` and the row has 0 Apagar buttons. DB estorno: `t_pagamento` id 4 = −20000.00.<br>**"Por pagar":** the Financeiro list derives the status badge from the same `totalPago` (`calcHonorarioStatus`). Observed later at 40 000 / 120 000: "Parcialmente Pago". At the step 5 state (100 000 < 120 000) the same function yields the same badge; at 0 it yields the unpaid badge. |
| 6 | NCRD-02 | PASS | The FR shows "Valor ainda creditável `100 000$00`".<br>**Dialog:** Parcial 100000.01 → pre-visualizar → inline banner "O valor indicado excede o que ainda pode ser creditado nesta fatura-recibo. Reduza o valor ou escolha crédito total.".<br>**DB:** NC rows 1 → **1**. |
| 7 | NCRD-02, NCRD-03 | PASS | **Input:** same dialog, Total, motivo "Anulação total" ("Serviço não prestado").<br>**Step 2 shows:** Base `86 956$53`, IVA (15%) `13 043$47`, Retenção (20%) `17 391$31`, Total a creditar `100 000$00`, Valor creditável restante `0$00`.<br>**Emit:** toast "Nota de crédito **SIM-NC-2026/2** emitida.". DB: `SIM-NC-2026/2:86956.53/13043.47/17391.31/100000.00:ANULACAO_TOTAL`.<br>**FR detail:** shows "Esta fatura-recibo já foi totalmente creditada." and 0 trigger buttons.<br>**Coherence** (all moved by −120 000 in total):<br>• saldo 165 000 → **45 000** (S0 − 120 000);<br>• `totalPago` → **0**;<br>• KPI 120 000 → **0** (K0 − 120 000). |
| 8 | NCRD-03 | PASS (eligibility); job run not observed live | **After step 7:** `t_pagamento` for honorário 3 = 120000, −20000, −100000, so **SUM = 0 < valor_total 120000**. dataAcordo is 2026-08-26, **40 days ago**, which is ≥ 30, the `DIAS_HONORARIO_ATRASADO` threshold. `AlertasDiariosJob.processarHonorarios` skips only when `totalPago >= valorTotal`, and `totalPago` is the same `@Formula SUM(valor_pago)` the API returned as 0, so the honorário is eligible for HONORARIO_ATRASADO.<br>**At the end of the run:** SUM = 40 000 (after step 9's extra FR/NC), still eligible. No prior alert existed for honorário 3 (0 `t_notificacao` rows).<br>**Not run live:** the job is cron-only (`0 0 6 * * *` Atlantic/Cape_Verde) with no trigger endpoint, so I could not run it in this stack. The job-reader condition after an NC is covered by `NotaCreditoServiceIT` (four-reader coherence, executed in the gate). |
| 9 | NCRD-01, NCRD-02 | PASS | **NC on an NC** (POST on SIM-NC-2026/1) → **422** `NC_SOBRE_NC`.<br>**New FR SIM-FR-2026/2** (50 000, DINHEIRO, 20%) emitted via API for the idempotency checks:<br>• Parcial 10 000 with key `3a8dab9c-…` → **201** SIM-NC-2026/3 (`e370f184-…`);<br>• the same key and body again → **200** with the same id `e370f184-…`, and NC rows 2 → 3 (only one created);<br>• the same key with valor 11 000 → **409** `CHAVE_REUTILIZADA`.<br>**assistente@lexcv.cv:** POST and pre-visualizacao → **403** "Acesso negado.".<br>**Edit-only user (tecnico.fin):**<br>• POST and pre-visualizacao → **403**, while GET detail → 200;<br>• in the UI the FR2 detail renders ("Valor ainda creditável `40 000$00`") with **0** "Emitir Nota de Crédito" buttons;<br>• an in-browser fetch → 403.<br>**Tenant B admin, billing off:** POST → 409 `FATURACAO_DESLIGADA`. This is the same answer for any id, so it is no existence oracle.<br>**Tenant B admin, after configuring and activating its own billing:** POST and pre-visualizacao on tenant A's FR1 and FR2 → **404** `DOCUMENTO_FISCAL_NAO_ENCONTRADO`, identical to a random UUID. GET tenant A's FR1 → 404.<br>NC rows unchanged by every refusal (3).<br>**Deletes:**<br>• `DELETE /pagamentos/4` (estorno) → **409** `PAGAMENTO_ESTORNO` "Este estorno pertence a uma nota de crédito emitida e não pode ser apagado.";<br>• `DELETE /pagamentos/3` (FR payment) → **409** `PAGAMENTO_FATURADO`;<br>• all payments are still present. |
| 10 | NCRD-01 | PASS | **NC detail SIM-NC-2026/1:**<br>• h1 "SIM-NC-2026/1"; badges Nota de Crédito / Pendente / Simulação — sem validade fiscal;<br>• "Documento original" card with "Corrige a fatura-recibo SIM-FR-2026/1" and "Ver fatura-recibo original" → `/financeiro/documentos-fiscais/bcf459dd-…` (FR1);<br>• Motivo "Correção de valor" and Descrição "Valor acordado revisto";<br>• "Total creditado `20 000$00`" and "Conta corrente debitada do total creditado.";<br>• Ligações: Ver honorário → `/financeiro/3`; Ver estorno → `/financeiro/3#pagamento-4`, and clicking it lands on the row "-20 000$00 \| — \| #4 \| Estorno (NC n.º SIM-NC-2026/1) \| Estorno: não pode ser apagado.";<br>• 0 Editar/Apagar/Anular/Enviar/Eliminar/Emitir buttons; ends with "Documento imutável: não pode ser alterado nem apagado.".<br>**Snapshot:** the cliente morada was changed to "Rua Nova 135, Assomada" (PUT 200). The NC page still shows "Achada Santo António, Praia", and DB `adquirente_morada` is unchanged before and after. |
| 11 | NCRD-01 | PASS | **Tipo "Nota de Crédito":** URL `?tipo=NC`; only NC rows. There are 3, because step 9 added SIM-NC-2026/3. Each shows "Corrige SIM-FR-…": SIM-NC-2026/3 → SIM-FR-2026/2, SIM-NC-2026/2 → SIM-FR-2026/1, SIM-NC-2026/1 → SIM-FR-2026/1.<br>**Reload** keeps the filter and the row count.<br>**Tipo "Todos":** 5 rows with both FR and NC; the `tipo` param is removed from the URL. |
| 12 | — | PASS | **Stopped:** the Next dev server and the backend (`pgrep` shows 0 matching processes). The `lexcv_pg` container was removed (`docker ps -a` is empty).<br>**Deleted:** `backend/.env`, `web/.env.local` and the cookie jars.<br>`git status --porcelain` is clean. |

### Defects found

None. No product code was changed during the run.

### Notes and observations (not defects of this phase)

- **Step 8:** the daily job could not be triggered live (cron-only, no endpoint). The eligibility condition was verified on the real database. The job's reader is covered by the executed `NotaCreditoServiceIT`.
- **Step 9, tenant B:** the first attempt hit `FATURACAO_DESLIGADA` (409), because the billing-active check precedes the document lookup. It returns the same answer for every id, so it leaks nothing. With tenant B's billing active, cross-tenant access returns 404 as specified.
- **Estorno row in the DB:** `t_pagamento.metodo` for the estorno holds the FR's method (TRANSFERENCIA), but the UI shows "—" as the UI-SPEC requires.
- **Pre-existing, unchanged:** the setup wizard / tenant provisioning `Set.of` 500 (133 deferred-items #1).

## Summary

The gate is green with every IT executed. The live run gave 12/12 steps PASS. Step 8 is a PASS on eligibility evidence, because the cron job cannot be triggered on demand. NCRD-01, NCRD-02 and NCRD-03 were each observed end to end on a live stack.

## Post-review human items (from 135-VERIFICATION.md, 2026-10-05) — pending

1. [pending] Re-run the NC dialog live (one Parcial, one Total); request body carries `totalEsperado` and `valorCreditavelEsperado`.
2. [pending] WR-01 stale preview: preview in session A, emit another NC on the same FR in session B, confirm in A → 409 `NC_VALORES_ALTERADOS`, back to form, no NC written.
3. [pending] CR-02 retry after a retryable 409 → second click sends a new request with a fresh key (never a silent no-op).
4. [pending] Business-logic sign-off: CR-01 (FR's cliente debited; processo cliente change blocked with documents), WR-01, WR-04 (withholding on clamped base).
