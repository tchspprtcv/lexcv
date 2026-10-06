---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 16
subsystem: verification
tags: [fiscal, efatura, e2e, playwright, xmllint, testcontainers, gate, primary-source]
requires:
  - "136-01..136-15: XSD vendoring, JAXB builder/validator, IUD, gateway, outbox job, reprocess, notification, UI"
provides:
  - "136-HUMAN-UAT.md: gate counts, live E2E record from the packaged jar (12/12 PASS), G1–G15 primary-source gate table"
  - "deferred-items.md #1: pre-existing role-drift boot abort (Phases 126/127)"
affects: [phase-136-verification, 137, real-connection milestone EFAT-01..06]
tech-stack:
  added: []
  patterns: []
key-files:
  created:
    - .planning/phases/LEXCV-136-formato-efatura-e-adaptador-simulado/136-HUMAN-UAT.md
    - .planning/phases/LEXCV-136-formato-efatura-e-adaptador-simulado/deferred-items.md
  modified: []
decisions:
  - "I auto-verified the checkpoint:human-verify on a live stack started from the packaged jar (orchestrator instruction). All 12 steps PASS with evidence"
  - "I did not decide the checkpoint:decision (G1–G15 gate). The table is in 136-HUMAN-UAT.md, and the plan stops at Task 3 for the user"
  - "DFE-01/DFE-02 stay Pending until the gate decision: the 136-scope behaviour is proven live, but close-pending vs adjust-items decides whether the format choices change. DFE-06 stays Pending because the REQUIREMENTS wording includes PDF and email (Phase 137)"
metrics:
  duration: "~30 min"
  completed: 2026-10-06
  tasks: "2/3 (Task 3 awaiting the user's decision)"
  files: 2
---

# Phase 136 Plan 16: Phase gate, live eFatura verification and primary-source gate Summary

Every automated gate is green, with all 13 fiscal Testcontainers ITs executed. The whole simulated eFatura flow was verified live from the **packaged jar**:
- an FR and an NC each reached "Aceite (simulação)" in the background, each with a 45-char `CV3` IUD;
- xmllint accepts the stored XML against the vendored XSD, and its SHA-256 matches;
- `EFATURA_MODE=REAL` and an empty value both abort startup;
- a forced persistent failure reaches ERRO and notifies the manage holders;
- reprocess keeps the same IUD, and every refusal holds;
- the DB refuses AUTORIZADO on a SIMULADO row.

No product code changed. The G1–G15 gate decision is pending with the user.

## Task 1: Full phase gate (commit 5754573)

| Gate | Result |
|------|--------|
| `mvn -Dmaven.compiler.release=21 verify` | BUILD SUCCESS. Surefire **1207**, failsafe **159**, 0 failures / 0 errors / 0 skipped |
| Fiscal ITs (executed, 0 skipped) | MigracaoFiscal133IT 8, 134IT 6, 135IT 4, 136IT 8, DocumentoFiscalRepositoryIT 18, PagamentoFaturadoServiceIT 11, PagamentoFaturadoConcorrenciaIT 4, GuardasDocumentoFiscalConcorrenciaIT 11, NotaCreditoServiceIT 11, NotaCreditoConcorrenciaIT 3, FilaComunicacaoFiscalIT 13, ReprocessarComunicacaoIT 8, FiscalOutboxJobIT 6 (+ FiscalOutboxJobFalhasForcadasIT 1) |
| SpotBugs | exit 0 |
| web vitest / tsc / lint | 318/318 / clean / 0 errors (20 pre-existing warnings) |
| verify:faturacao / verify:documentos-fiscais | OK / OK |
| `pnpm build` | success |
| `web/package-lock.json` | unchanged |

## Task 2: Live E2E (checkpoint auto-verified, commit acd4dd0)

The full record is in `136-HUMAN-UAT.md`. Highlights:
- **Step 1:** migration 136 ran twice on a Hibernate-created DB, with no error.
- **Step 2:** REAL → exit 1, empty → exit 1, each logging "só SIMULADO existe".
- **Step 3:**
  - Payment POST took 0.104 s. The detail showed PENDENTE/iud null immediately.
  - ACEITE_SIMULADO came after ~25 s with IUD `CV3261006212345678999990200000000132498643528`.
- **Steps 4–5:**
  - The FR XML and the NC XML both pass xmllint ("validates"), and the SHA-256 of the exported bytes equals `xml_sha256`.
  - Each row has repo 3, LED 99999 and version 2024-05-27.
  - The NC has IssueReasonCode 2, Reference = the FR's IUD, and a controlled Note.
- **Step 6 (Playwright):**
  - The "Comunicação" column shows outline "Aceite (simulação)" badges, and the Erro filter gives the empty state.
  - The detail badges and the "Comunicação fiscal" card show "Teste (simulado)", the mono IUD and "Ambiente de teste — sem validade fiscal".
  - The "Modo simulado." banner appears on the list, the detail and the honorário page.
  - No authorisation wording appears on any screen.
- **Step 7:**
  - With the fault lever on and tentativas forced to 7, the document reaches ERRO with `FALHA_SIMULADA`.
  - Admin and the manage holder each got 1 `COMUNICACAO_FISCAL_FALHOU` linking to the document. The view-only user got 0.
  - Muting is refused (400).
- **Step 8:** the UI reprocess shows the exact dialog copy and the toast. The DB then reads PENDENTE/0. After a restart without the lever: ACEITE_SIMULADO with the **same IUD**, and still 1 XML row.
- **Step 9:**
  - 409 `COMUNICACAO_ESTADO_INVALIDO`.
  - 404 for a malformed id or a random UUID.
  - 403 for the view-only and the manage-only users, and neither sees the button.
  - 404 for tenant B.
- **Step 10:** SQLSTATE 23514 `ck_comunicacao_fiscal_autorizado_producao`.
- **Step 11:** efatura.cv is still blocked (proxy CONNECT 403).
- **Step 12:** all processes and containers were stopped, the env files deleted, and git is clean.

## Task 3: Primary-source gate G1–G15 — CHECKPOINT (awaiting user decision)

The 15-row table is in `136-HUMAN-UAT.md` under "Portão das fontes primárias (G1–G15)". Each row gives:
- the topic, what the package fixes and what remains assumed;
- the exact file/constant (e.g. `MapeamentoEfatura.issueReasonCode` for G6, `MapeamentoEfatura.LED_SIMULADO` for G4, `application.yml app.efatura.transmissao` for G9, `xsd/efatura/README.md` for G1/G14);
- the consequence if wrong, the live evidence and a recommendation.

The options are close-pending (recommended), keep-open, and adjust-items (G6/G11/G15 are the natural candidates). **The user's choice is not recorded yet.**

## Deviations from Plan

**1. [Environment] Manage-only user via a direct permission.** I first gave the ADVOGADO office role `financeiro:manage` through `PUT /admin/rbac`. The next restart then aborted with the pre-existing role-drift check. I moved the permission to a direct `t_user_permission` row: an environment-only change, logged as `deferred-items.md` #1.

**2. [Environment] WebFetch retry not run.** That tool is not available to this executor. The curl retry was run and recorded (CONNECT 403).

**3. [Environment] Tenant B billing left off.** The reprocess route performs no billing-active check before the lookup, so tenant B got 404 directly. This differs from 135, where the 409 `FATURACAO_DESLIGADA` path came first.

## Deferred Issues

- `deferred-items.md` #1: after an office role that has users is customised, the next backend start aborts in `MigracaoPapeisEscritorioService` → `VerificacaoDerivaPapeisService`. This is pre-existing (Phases 126/127) and out of scope.
- Unchanged, pre-existing: the setup wizard / tenant provisioning `Set.of` 500 (133 deferred-items #1).

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: 136-HUMAN-UAT.md (12 step rows PASS, 15 G rows), deferred-items.md
- FOUND: commits 5754573, acd4dd0
