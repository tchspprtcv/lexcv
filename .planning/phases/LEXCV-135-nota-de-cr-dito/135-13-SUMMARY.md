---
phase: 135-nota-de-cr-dito
plan: 13
subsystem: verification
tags: [fiscal, nota-de-credito, e2e, playwright, testcontainers, gate]
requires:
  - "135-02: migration 135 + MigracaoFiscal135IT"
  - "135-07: NC ITs on real PostgreSQL"
  - "135-12: estorno row and FR/NC documents list"
provides:
  - "135-HUMAN-UAT.md: gate counts + live E2E record (12/12 PASS)"
affects: [phase-135-verification, 136]
tech-stack:
  added: []
  patterns: []
key-files:
  created:
    - .planning/phases/LEXCV-135-nota-de-cr-dito/135-HUMAN-UAT.md
  modified:
    - .planning/STATE.md
    - .planning/ROADMAP.md
    - .planning/REQUIREMENTS.md
decisions:
  - "The checkpoint:human-verify was auto-verified on a live stack (orchestrator instruction). All 12 steps PASS with evidence, so the plan closes without a human checkpoint"
  - "Step 8 is a PASS on eligibility: the plan makes the job run conditional ('if the daily job can be triggered'). AlertasDiariosJob is cron-only with no trigger endpoint; the DB condition (SUM=0 < 120000, 40 days) was verified live and the job reader is covered by the executed NotaCreditoServiceIT"
  - "Tenant B's cross-tenant check was re-run after activating tenant B's own billing. With billing off, 409 FATURACAO_DESLIGADA is returned for every id, so it is no existence oracle; with billing on, tenant A's FRs return 404 like a random UUID"
  - "NCRD-01..03 marked complete in REQUIREMENTS.md: each was observed end to end live and is covered by executed unit tests and ITs"
metrics:
  duration: "~35 min"
  completed: 2026-10-05
  tasks: 2
  files: 4
---

# Phase 135 Plan 13: Phase gate and live end-to-end NC verification Summary

Phase 135 is closed by evidence:
- Every backend and web gate is green, with all nine fiscal Testcontainers ITs actually executed.
- The whole Nota de Crédito flow was driven on a real PostgreSQL + Spring Boot + Next dev stack with Playwright, curl and psql. The partial and total NCs produced the exact backend figures in their own SIM-NC series.
- Saldo, total pago and the monthly KPI each moved by exactly the credited amount.
- Every refusal path returned its specified status and code.
- No product code changed.

## Task 1: Full phase gate

| Gate | Result |
|------|--------|
| `mvn -Dmaven.compiler.release=21 verify` | BUILD SUCCESS. Surefire **1007**, failsafe **122**, 0 failures / 0 errors / 0 skipped |
| Fiscal ITs (executed, 0 skipped) | MigracaoFiscal133IT 8, MigracaoFiscal134IT 6, MigracaoFiscal135IT 4, DocumentoFiscalRepositoryIT 18, PagamentoFaturadoServiceIT 11, PagamentoFaturadoConcorrenciaIT 4, GuardasDocumentoFiscalConcorrenciaIT 11, NotaCreditoServiceIT 10, NotaCreditoConcorrenciaIT 3 |
| `mvn -q -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check` | exit 0 |
| web `vitest run` / `tsc --noEmit` / `lint` | 244/244 / clean / 0 errors (20 pre-existing warnings) |
| `verify:faturacao` / `verify:documentos-fiscais` | OK / OK |
| `pnpm build` | success; `/financeiro/documentos-fiscais` and `/[id]` listed |
| `web/package-lock.json` | unchanged |

## Task 2: Live E2E (checkpoint auto-verified)

The full record is in `135-HUMAN-UAT.md`. Highlights:
- **Steps 2-4 (NCRD-01):**
  - FR SIM-FR-2026/1 for 120 000 with 20% retention gives the baseline: saldo S0 165 000, total pago 120 000, KPI K0 120 000.
  - The FR detail shows the empty NC card, "Valor ainda creditável 120 000$00" and the outline trigger.
  - The partial preview shows Base 17 391$30, IVA 2608$70, Retenção 3478$26, Total a creditar 20 000$00 and restante 100 000$00. The preview wrote 0 NC rows.
  - Emission toasts "Nota de crédito SIM-NC-2026/1 emitida.".
- **Step 5 (NCRD-03):**
  - Saldo 145 000, total pago 100 000 and KPI 100 000: each moved by −20 000.
  - The estorno row shows "-20 000$00" in red, método "—", "Estorno (NC n.º SIM-NC-2026/1)" and "Estorno: não pode ser apagado.".
- **Step 6 (NCRD-02):** 100 000,01 shows the "excede" banner, and no NC is written.
- **Step 7:**
  - The total NC shows 86 956$53 / 13 043$47 / 17 391$31 / 100 000$00 and is emitted as SIM-NC-2026/2.
  - The FR shows "totalmente creditada" and no trigger.
  - Saldo 45 000 (S0 − 120 000), total pago 0, KPI 0 (K0 − 120 000).
- **Step 8:** the honorário's SUM(valor_pago) is 0 < 120 000 and dataAcordo is 40 days ago, so it is eligible for HONORARIO_ATRASADO. The job itself is cron-only and was not triggered.
- **Step 9:**
  - An NC on an NC → 422 `NC_SOBRE_NC`.
  - The same key twice → 201, then 200 with the same id. The same key with another valor → 409 `CHAVE_REUTILIZADA`.
  - Assistente and an edit-only user (created via `/admin/rbac` + `/admin/users`) → 403. The edit-only user sees no trigger.
  - Tenant B → 404.
  - Deleting the estorno → 409 `PAGAMENTO_ESTORNO`; deleting the FR's payment → 409 `PAGAMENTO_FATURADO`.
- **Step 10:**
  - The NC detail shows its badges, the original-FR link, motivo and descrição, and "Ver estorno", which lands on the estorno row. It has no action buttons.
  - Editing the cliente's morada leaves the NC buyer snapshot unchanged.
- **Step 11:** Tipo NC shows only NC rows, each with "Corrige SIM-FR-…", and the URL keeps the filter. "Todos" shows both FR and NC.
- **Step 12:** processes and the container were stopped and the env files deleted. `git status` is clean.

**Environment workarounds (no product change):**
- The pre-existing `Set.of` setup bug (133 deferred-items #1) breaks the setup wizard, so I seeded the demo tenant the 133-08 / 134-14 way.
- Tenant B was created with SQL.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | 86eab84 | Gate counts recorded in 135-HUMAN-UAT.md |
| 2 | 58fb00f | Live end-to-end NC record (12/12 PASS) |

## Deviations from Plan

**1. [Environment] Third NC created in step 9.** The idempotency checks used a fresh FR (SIM-FR-2026/2, 50 000) and emitted SIM-NC-2026/3. As a result, step 11's NC filter shows 3 rows instead of the plan's 2, and every row is an NC with its "Corrige" line.

**2. [Environment] Tenant B billing activated for the 404 check.** With tenant B's billing off, the NC routes return 409 `FATURACAO_DESLIGADA`, because that check comes before the lookup. The answer is identical for any id, so it leaks nothing. Activating tenant B's billing exercised the real cross-tenant path, which returned 404.

**3. [Observation] Money format.** The plan writes `2 608,70`, but the app's existing Intl pt-CV formatter renders `2608$70`: CLDR pt minimumGroupingDigits=2 and `$` as the CVE decimal mark. The values match exactly, and this is the same formatter as Phase 134, so it is not a defect.

**4. [Partial evidence] "Por pagar" badge.** The "Parcialmente Pago" badge was observed at a total pago of 40 000 / 120 000, not at the step 5 state. The badge is derived by `calcHonorarioStatus` from the same `totalPago`, which the API returned as 100 000 at step 5.

## Observations (not in scope)

- The estorno's `t_pagamento.metodo` stores the FR's method (TRANSFERENCIA); the UI shows "—" as specified.
- The setup wizard / tenant provisioning `Set.of` 500 is unchanged (pre-existing).

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: .planning/phases/LEXCV-135-nota-de-cr-dito/135-HUMAN-UAT.md (status auto-verified, 12 step rows PASS)
- FOUND: 86eab84, 58fb00f
