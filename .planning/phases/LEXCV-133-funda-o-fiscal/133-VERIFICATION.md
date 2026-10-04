---
phase: 133-funda-o-fiscal
verified: 2026-10-04T14:20:00Z
status: human_needed
score: 6/6 roadmap success criteria verified (29/29 merged plan truths verified)
overrides_applied: 0
human_verification:
  - test: "Re-run the Faturação tab flow by hand as admin@lexcv.cv: fill and save the fiscal data, edit a field and wait for a background refetch (window refocus), activate, turn the email switch on through the acknowledgement dialog, turn it off, deactivate"
    expected: "Unsaved edits survive the refetch (WR-03); success toasts and inline 409/422 copy appear as in the UI-SPEC; the badge, activation card and email card states follow the server; 'Aceite por {nome} em {data}.' is shown after enabling"
    why_human: "The 133-08 checkpoint:human-verify was auto-verified by an agent with Playwright, not by a person. The review fixes WR-03, WR-04, IN-02 and IN-05 changed the form and hooks after that run, and no end-to-end run covers them"
  - test: "Visual check of the Faturação tab in light and dark mode, at desktop width and at 375px"
    expected: "Readable text, red destructive deactivate confirm, simulation notice always visible, series table scrolls horizontally, no horizontal page overflow"
    why_human: "Visual appearance cannot be verified with grep or unit tests. The agent reviewed its own screenshots; a person has not signed off"
  - test: "Log in as a user without financeiro:manage (e.g. assistente@lexcv.cv) and open Definições"
    expected: "No Faturação tab. Every /api/v1/faturacao/* call returns 403"
    why_human: "Confirms that the UI gating and the backend gating agree in a real session. Unit tests cover each layer separately"
---

# Phase 133: Fundação Fiscal Verification Report

**Phase Goal:** Cada escritório pode registar os seus dados fiscais e ativar a faturação de forma segura, e o sistema dispõe de parâmetros fiscais com vigência e de uma numeração sequencial sem lacunas, sem que nada mude para os escritórios que não ativarem.
**Verified:** 2026-10-04T14:20:00Z
**Status:** human_needed
**Re-verification:** No. This is the first verification.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An administrator with `financeiro:manage` registers and edits the NIF (9 digits, first 1–9), firma, morada and regime de IVA (NORMAL, or ISENTO with a motivo) in Definições → Faturação | VERIFIED | `ConfiguracaoFiscalRequest` applies `@Pattern(NIF_FISCAL_REGEX = "^[1-9]\\d{8}$")`, `@Size(max=100)` on morada and `@NotNull` on regimeIva. `ConfiguracaoFiscalService.guardar` refuses ISENTO without an official motivo (`MOTIVO_ISENCAO_INVALIDO`). `MotivoIsencaoIva` has 21 codes. `FaturacaoController` has a class-level `@PreAuthorize("hasAuthority('financeiro:manage')")`. `settings/page.tsx` adds the `faturacao` TabId, gated by `can.manage("financeiro")`. The Zod schema mirrors the NIF regex and the length limits. |
| 2 | Billing can only be activated with complete fiscal data. After the first document it cannot be turned off and the NIF is locked | VERIFIED | `ativar` returns 422 `CONFIGURACAO_FISCAL_INCOMPLETA` unless `config.completa()`. `desativar` returns 409 `FATURACAO_JA_EMITIU` and `guardar` returns 409 `NIF_BLOQUEADO` when `existsByTenantIdAndUltimoNumeroGreaterThan(tenantId, 0L)` is true. Every mutator locks the row first with `bloquearPorTenant` (CR-01). In the UI, the form disables the NIF when `nifBloqueado` is set, and the activation card replaces deactivate with an explanation. |
| 3 | With billing off, registering a honorário payment behaves exactly as before | VERIFIED | `ResourceController` and the non-fiscal services are untouched; the git diff since the phase base shows 0 changes. `FaturacaoDesligadaPagamentoInalteradoTest` is a source guard that forbids fiscal tokens in the payment path, and it passes. `obter` never creates a row and returns `ativa=false`. The 133-08 E2E step 9 got 201 on `POST /pagamentos` with billing both on and off. |
| 4 | IVA 15% and suggested withholding 20% exist as dated parameters in the database, not as code constants | VERIFIED | `DatabaseSeeder.seedParametrosFiscais()` runs on every boot, before the `seedEnabled` gate, as a non-destructive upsert (`IVA_TAXA_NORMAL=15`, `RETENCAO_SUGERIDA=20`, `vigente_desde 2000-01-01`). `ParametroFiscalService.valorVigente` uses `findFirstByCodigoAndVigenteDesdeLessThanEqualOrderByVigenteDesdeDesc`. The `ParametrosFiscaisSemConstantesTest` grep gate and `verify:faturacao` both pass. |
| 5 | Concurrent requests for the same series get consecutive numbers with no gaps or duplicates, and numbering restarts at 1 each civil year | VERIFIED | `NumeracaoService.proximoNumero` is `MANDATORY` and runs `set_config lock_timeout 5s` → `INSERT ... ON CONFLICT DO NOTHING` → `PESSIMISTIC_WRITE` → `refresh` → increment, with the year taken in Atlantic/Cape_Verde from the injected Clock. I re-ran `NumeracaoServiceConcorrenciaIT` against real PostgreSQL (Testcontainers): 10/10 pass. It covers 8 concurrent transactions getting 1..8, a concurrent first use, rollback leaving no gap, tenant isolation, the annual restart, the 31 Dec / 1 Jan boundary in CV time, refusal outside a transaction, the lock timeout giving `SERIE_INDISPONIVEL`, and the WR-02 stale-context case. |
| 6 | The administrator turns automatic invoice email on or off (off by default), and can only turn it on after explicitly accepting the simulation notice | VERIFIED | `definirEmailAutomatico` refuses to turn it on unless billing is active (409 `FATURACAO_DESLIGADA`) and the declaration was accepted (422 `DECLARACAO_NAO_ACEITE`). It stores `envioEmailAceitePor`/`Em` and writes an audit event. Deactivating billing forces email off. The builder default is `envioEmailAutomatico(false)`. In the email card, the switch is disabled while billing is off, and the dialog's confirm stays disabled until the "Compreendo que os documentos simulados não têm validade fiscal" checkbox is ticked. It then sends `{ligado: true, aceiteDeclaracao: true}`. |

**Score:** 6/6 roadmap truths verified. All 29 plan-frontmatter truths (plans 01–08) are consistent with the code I read, and none contradicts the roadmap.

Note: the cross-tenant NIF uniqueness rule (`NIF_JA_REGISTADO`) is absent on purpose. WR-05 removed it by user decision, and `guardar` has a comment recording that. This is correct, not a gap.

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `backend/.../models/ConfiguracaoFiscal.java`, `SerieFiscal.java`, `ParametroFiscal.java` | VERIFIED | Unique constraints present. `completa()` is substantive. |
| `backend/.../repositories/SerieFiscalRepository.java` (+ Custom/Impl) | VERIFIED | `bloquear`, `criarSeNaoExiste` (ON CONFLICT), `definirLockTimeoutLocal`, `refrescar`, `existsByTenantIdAndUltimoNumeroGreaterThan`. |
| `backend/.../exceptions/RecusaFiscalException.java` + `GlobalExceptionHandler` | VERIFIED | Handler returns status/code/campo. The catch-all no longer echoes internals (WR-06). |
| `backend/.../config/ClockConfig.java` | VERIFIED | Injected into the Numeracao, Parametro and Configuracao services. |
| `backend/migrations/133-create-fiscal-foundation-tables.sql` + README row 19 | VERIFIED | `CREATE TABLE IF NOT EXISTS` throughout, and `t_tenant` is not altered. `MigracaoFiscal133IT` re-run: 8/8 pass, covering parity and no per-boot DDL. |
| `backend/.../services/fiscal/ParametroFiscalService.java` | VERIFIED | It has no production consumer yet, by design: Phase 134 computes documents. |
| `backend/.../services/fiscal/NumeracaoService.java` (+ `NumeroFiscalAtribuido`) | VERIFIED | It has no production consumer yet, by design (CONTEXT: "sem consumidor de produção ainda"). It is proven by the IT. |
| `backend/.../services/fiscal/ConfiguracaoFiscalService.java`, `AuditoriaFiscalService.java` | VERIFIED | Mutations audit inside the same transaction, and refusals are thrown before any mutation. |
| `backend/.../controllers/FaturacaoController.java` | VERIFIED | Seven endpoints. The tenant comes only from the principal, and bodies use `@Valid`. |
| `web/src/lib/api.ts` (ApiError, semToastParaStatus) | VERIFIED | Additive change. The `API <status>: <msg>` message format is preserved. |
| `web/src/types/faturacao.ts`, `schemas/faturacao.ts`, `hooks/use-faturacao.ts` | VERIFIED | Hooks call `/faturacao/*` and invalidate config and series. |
| `web/src/app/(dashboard)/settings/faturacao-{tab,dados-form,series-card,ativacao-card,email-card}.tsx` | VERIFIED | Wired into `page.tsx` and composed in the tab shell. |
| `web/scripts/verify-faturacao.mjs` | VERIFIED | `pnpm verify:faturacao` passes. |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| GlobalExceptionHandler | RecusaFiscalException | `@ExceptionHandler(RecusaFiscalException.class)` | WIRED |
| migrations/README.md | 133 script | row 19 + re-run safety table | WIRED |
| DatabaseSeeder.run | seedParametrosFiscais() | unconditional call after seedRbac() | WIRED |
| ParametroFiscalService | repository derived query | `findFirstBy...OrderByVigenteDesdeDesc` | WIRED |
| NumeracaoService | criarSeNaoExiste / bloquear / definirLockTimeoutLocal / refrescar | same transaction | WIRED |
| ConfiguracaoFiscalService | existsByTenantIdAndUltimoNumeroGreaterThan | `documentosEmitidos()` | WIRED |
| ConfiguracaoFiscalService | AuditoriaFiscalService.registar* | same `@Transactional` | WIRED |
| FaturacaoController | ConfiguracaoFiscalService | delegation with getTenantId()/getPrincipal() | WIRED |
| use-faturacao.ts | /faturacao/* | apiFetch | WIRED |
| page.tsx | FaturacaoTab | `activeTab === "faturacao" && hasFinanceiroManage` | WIRED |
| dados-form | useGuardarConfiguracaoFiscal | zodResolver(configuracaoFiscalSchema) | WIRED |
| ativacao-card / email-card | useAtivar/useDesativar / useEmailAutomatico | AlertDialog → mutateAsync | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| FaturacaoTab / cards | `configuracao.data` | `GET /faturacao/configuracao` → `ConfiguracaoFiscalRepository.findByTenantId` | Yes | FLOWING |
| FaturacaoSeriesCard | series list | `GET /faturacao/series` → `findByTenantIdOrderByAnoDescTipoDocumentoAsc` | Yes | FLOWING |
| Motivo select | 21 motivos | `GET /faturacao/motivos-isencao` → `MotivoIsencaoIva` enum | Yes, static by nature (an official code list) | FLOWING |

### Behavioral Spot-Checks (run by this verifier)

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Frontend typecheck | `npx tsc --noEmit` | exit 0 | PASS |
| Frontend unit tests | `pnpm test` | 90/90 | PASS |
| Source gate | `pnpm verify:faturacao` | OK | PASS |
| Backend fiscal unit tests | `mvn -Dmaven.compiler.release=21 test -Dtest='Faturacao*Test,ConfiguracaoFiscalServiceTest,...'` | 0 failures (34 service, 13 controller, 8 authorization, 1 CFG-03 guard, 2 no-constants, 9 numbering, 10 audit, 4 parametro tests, among others) | PASS |
| Numbering concurrency on real PostgreSQL | failsafe `NumeracaoServiceConcorrenciaIT` | 10/10 (18.3s) | PASS |
| Migration parity / no per-boot DDL | failsafe `MigracaoFiscal133IT` | 8/8 | PASS |

### Probe Execution

No probes are declared and no `scripts/*/tests/probe-*.sh` scripts exist for this phase. Not applicable.

### Requirements Coverage

| Requirement | Source Plans | Status | Evidence |
|-------------|--------------|--------|----------|
| CFG-01 | 01, 04, 05, 06, 07 | SATISFIED | Truth 1 |
| CFG-02 | 01, 04, 05, 06, 07, 08 | SATISFIED | Truth 2 |
| CFG-03 | 04, 05, 08 | SATISFIED | Truth 3 |
| CFG-04 | 01, 02 | SATISFIED | Truth 4 |
| CFG-05 | 01, 03, 07 | SATISFIED | Truth 5 |
| CFG-06 | 04, 05, 06, 08 | SATISFIED | Truth 6 |

REQUIREMENTS.md maps no other IDs to Phase 133, so nothing is orphaned.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (all 70 phase files) | | TBD/FIXME/XXX/TODO/HACK | none | The only matches are the Portuguese word "TODOS" in comments, which are false positives. |
| GlobalExceptionHandler.java | catch-all | 500 body changed from `{error, message}` to `{message, referencia}` app-wide | Info | A security hardening step (WR-06). Frontend consumers read `json.message \|\| json.error`, so nothing breaks. |
| 133-REVIEW IN-01 | NumeracaoService | `lock_timeout 5s` persists for the rest of the caller's transaction | Info | Matters only once Phase 134 adds a consumer. Recorded in the review. |
| 133-REVIEW IN-03 / IN-04 | dados-form / converters | gating convention, and converters throwing on unknown values | Info | Deferred by the review and not goal-affecting. |

### Human Verification Required

1. **Manual flow re-run after the review fixes.** Log in as admin@lexcv.cv and go to Definições → Faturação. Save the fiscal data, refocus the window with an unsaved edit, activate, turn email on with the acknowledgement, turn it off, then deactivate. Expected: unsaved edits survive the refetch, the inline and toast copy follows the UI-SPEC, the states follow the server, and the acceptance line is shown. Why human: the plan 08 human-verify checkpoint was completed by an agent with Playwright, and the frontend review fixes (WR-03, WR-04, IN-02, IN-05) landed after that run.
2. **Visual check, light/dark, desktop/375px.** Expected: readable text, a red deactivate confirm, the notice always visible, and the series table scrolling inside its wrapper. Why human: visual appearance.
3. **Non-manager session.** Log in as assistente@lexcv.cv. Expected: no Faturação tab and 403 on `/api/v1/faturacao/*`. Why human: confirms that the frontend and backend gates agree in a real session.

### Gaps Summary

No blocking gaps. The phase goal holds in the codebase:
- **Fiscal data:** stored per tenant and validated in both layers, with the NIF locked after the first document.
- **Activation:** guarded by data completeness and by the documents-emitted check, under a row lock.
- **Parameters:** stored as dated database rows, seeded on every boot and never as code constants.
- **Numbering:** gapless and duplicate-free under real concurrency, restarting yearly in Cape Verde time.
- **Email switch:** off by default and gated on an explicit acceptance.
- **Offices that do not activate:** see no change. The payment path is untouched and reading creates no row.

The status is `human_needed` only because the planned human end-to-end checkpoint was agent-executed and predates the post-review UI fixes. The known setup-wizard 500 on a fresh DB is pre-existing and out of scope (deferred-items.md #1).

---

_Verified: 2026-10-04T14:20:00Z_
_Verifier: Claude (gsd-verifier)_
