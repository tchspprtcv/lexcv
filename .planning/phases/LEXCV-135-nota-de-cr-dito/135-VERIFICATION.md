---
phase: LEXCV-135-nota-de-cr-dito
verified: 2026-10-05T18:10:00Z
status: human_needed
score: 3/3 roadmap success criteria verified (13/13 plan truth groups verified in code; post-review UI path needs a live re-run)
overrides_applied: 0
human_verification:
  - test: "Live re-run of the NC dialog after the review fixes (CR-02, WR-01, WR-02, WR-03 changed nota-credito-dialog.tsx / nota-credito-dialogo.ts / erros-emissao.ts after the 12/12 live UAT). On a live stack, emit one Parcial and one Total NC through the UI."
    expected: "Both NCs emit, and the emission request body carries totalEsperado and valorCreditavelEsperado. The toast reads 'Nota de crédito SIM-NC-<ano>/N emitida.'. Saldo, total pago and KPI move by the credited amount, as in UAT steps 4-7."
    why_human: "The automated live UAT (135-HUMAN-UAT.md) ran before these UI changes. Vitest covers the helpers, but no browser run has exercised the changed dialog end to end."
  - test: "WR-01 stale preview: open the NC dialog on an FR and pre-visualize. In a second session, emit another NC on the same FR. Then click 'Emitir nota de crédito' in the first session."
    expected: "409 NC_VALORES_ALTERADOS with the copy 'Os valores desta fatura-recibo mudaram desde a pré-visualização…'. The user is sent back to the form, no NC row is written, and a new preview shows the reduced remainder."
    why_human: "Multi-session UI race. The fixer flagged it 'needs human check of the logic'. The backend unit path exists, but the UI round trip has not been observed."
  - test: "CR-02 retry: force a retryable definitive 4xx during emission (for example PROCESSO_ALTERADO_TENTE_NOVAMENTE or DATA_EMISSAO_ALTERADA), then click 'Emitir nota de crédito' again."
    expected: "The second click sends a new request with a fresh idempotency key. The button is never an enabled no-op."
    why_human: "Hard to trigger by grep or unit test against a real backend. Vitest covers tentativaDepoisDeFalhaNc, but not a live click."
  - test: "CR-01 / WR-04 logic sign-off: review that (a) the NC debits the FR's cliente (origem.getClienteId()), (b) PUT /processos/{id} refuses a cliente change with 409 PROCESSO_COM_DOCUMENTOS_FISCAIS, and (c) partial-NC withholding is round(clampedBase × taxa / 100) capped at the remaining withholding."
    expected: "The developer accepts the business logic. The IT processoReatribuidoDepoisDaFrDebitaOClienteDaFr and the WR-04 clamp vector pass; both were re-run here and are green."
    why_human: "The code-review fixer explicitly marked CR-01, WR-01 and WR-04 'fixed (needs human check of the logic)'. This is a business-rule sign-off, not a mechanical check."
---

# Phase 135: Nota de Crédito Verification Report

**Phase Goal:** Utilizador autorizado corrige uma Fatura-Recibo emitida, total ou parcialmente, através de uma Nota de Crédito que reverte o valor de forma coerente em todo o sistema.
**Verified:** 2026-10-05T18:10:00Z
**Status:** human_needed
**Re-verification:** No (initial verification)

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A user with `financeiro:manage` issues a total or partial NC on an FR. The motivo is mandatory, the NC references the original invoice, and it is numbered in the NC's own series. | VERIFIED | **Gate:** `DocumentoFiscalController.java:196-215` has `@PreAuthorize("hasAuthority('financeiro:manage')")` on the preview and emission routes; 201 for a new NC, 200 for a replay.<br>**Validation:** `ValidacaoNotaCredito` refuses a missing or invalid tipo, motivoCodigo (closed enum `MotivoNotaCredito`) and motivoTexto (1..200 chars) with 422.<br>**Composition and numbering:** `ComposicaoNotaCredito.compor` handles TOTAL and PARCIAL. `NotaCreditoService.emitir:241-243` calls `numeracaoService.proximoNumero(tenantId, TipoDocumentoFiscal.NC, origem.getAmbiente())` and writes `documentoOrigemId(origem.getId())`, `motivoCodigo` and `motivoTexto`.<br>**Web:** the `podeEmitirNotaCredito` exact gate is used at `page.tsx:83,210`.<br>**Live UAT:** SIM-NC-2026/1 and SIM-NC-2026/2 were issued in their own series (pre-fix run). |
| 2 | The sum of an FR's NCs never exceeds the original value. The system refuses to credit an NC or another emitter's document. | VERIFIED | **Cap:** `ComposicaoNotaCredito.compor:85-90` refuses with 409 `NC_EXCEDE_ORIGINAL` when `remTotal <= 0` or `valor > remTotal`. Per-column remainders are clamped, so no column can exceed the FR. The cap is read under the configuração lock (`emitir` step 3, before `notasDe` in step 9).<br>**NC on an NC:** `exigirFaturaRecibo` refuses with 422 `NC_SOBRE_NC`.<br>**Other emitter:** CONTEXT defines this as 404 cross-tenant, via `findByIdAndTenantId` → `DOCUMENTO_FISCAL_NAO_ENCONTRADO`. The emitente snapshot is copied from the FR.<br>**Tests:** `NotaCreditoConcorrenciaIT` (3 tests: cap under concurrency, same-key race, lock order) passed here. |
| 3 | After an NC, the conta corrente saldo, the honorário's total pago, the dashboard's monthly KPI and the overdue-honorário alert all reflect the reversal coherently, and the honorário can count as unpaid again. | VERIFIED | **Saldo:** `emitir:220-225` debits the conta corrente of the FR's cliente (CR-01 fix).<br>**Estorno:** `emitir:228-232` writes a negative `Pagamento` on the same honorário. This is the single ledger.<br>**Total pago:** `Honorario.totalPago` is `@Formula SUM(valor_pago)`.<br>**KPI:** `ResourceController.calculateMensalReceived:3416` → `RecebidoNoMes.somar` (year and month, CV zone, injected Clock). The estorno subtracts in the month it is dated.<br>**Alert:** `AlertasDiariosJob:275-277` skips a honorário only when `totalPago >= valorTotal`, so it becomes eligible again after an NC.<br>**Estorno delete guard:** `ResourceController:3311` → 409 `PAGAMENTO_ESTORNO`.<br>**Tests:** `NotaCreditoServiceIT` (11 tests, including four-reader coherence and `processoReatribuidoDepoisDaFrDebitaOClienteDaFr`) passed here. |

**Score:** 3/3 roadmap truths verified.

**Plan must_haves (01-13).** I spot-checked each group against the code:
- the immutable NC row with `pagamento_id` = estorno;
- the idempotent migration 135 (`MigracaoFiscal135IT` 4/4);
- the single pure composition;
- the read side (`DocumentoFiscalDetalheResponse` uses `ComposicaoNotaCredito.valorCreditavelRestante`);
- the shared FR/NC key space (409 `CHAVE_REUTILIZADA`);
- atomic, lock-ordered emission;
- the KPI fix;
- the estorno guard;
- the 201/200/404 routes;
- the web types, exact gate, error mapping and schema;
- the two-step dialog;
- the estorno row, the "Corrige" line and the FR/NC filter.

I found no contradicting evidence.

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `backend/.../services/fiscal/NotaCreditoService.java` | VERIFIED | 467 lines. Preview and atomic emission; WR-01 `exigirValoresConfirmados`; CR-01 FR-cliente debit; IN-02 ambiente taken from the FR. |
| `backend/.../services/fiscal/ComposicaoNotaCredito.java` | VERIFIED | Pure composition. WR-04 withholding is computed on the clamped base. IN-03 has a single `totalCreditado` / `valorCreditavelRestante`. |
| `backend/.../services/fiscal/ValidacaoNotaCredito.java` | VERIFIED | tipo, motivo, texto and valor checks; `NC_SOBRE_NC`. |
| `backend/.../controllers/DocumentoFiscalController.java` | VERIFIED | Two NC routes with the exact `financeiro:manage` gate and 404 for a non-UUID id. |
| `backend/.../services/RecebidoNoMes.java` | VERIFIED | Wired from `ResourceController:3416`. |
| `backend/.../controllers/ResourceController.java` | VERIFIED | `updateProcesso` guard `PROCESSO_COM_DOCUMENTOS_FISCAIS` (1292-1296), estorno delete guard (3311), KPI. |
| `backend/migrations/135-add-nota-credito-documento-fiscal.sql` | VERIFIED | Exercised by `MigracaoFiscal135IT` (passed). |
| `web/.../documentos-fiscais/[id]/nota-credito-dialog.tsx` | VERIFIED (live re-run pending) | Two steps. The emit button is disabled when `!tentativa`. It sends `totalEsperado` / `valorCreditavelEsperado` and keeps unresolved keys through `lembrarTentativaNc`. |
| `web/.../documentos-fiscais/[id]/page.tsx` | VERIFIED | NC list, "Documento original", "Ver estorno", "totalmente creditada" message. |
| `web/.../financeiro/[id]/pagamentos-card.tsx` | VERIFIED | Estorno row with an NC link and no delete button. |
| `web/.../documentos-fiscais/page.tsx`, `columns.tsx` | VERIFIED | `?tipo=FR|NC` whitelist and the "Corrige {n}" line. |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| Dialog | `POST /documentos-fiscais/{id}/notas-credito[/pre-visualizacao]` | `useEmitirNotaCredito` / `usePreVisualizacaoNotaCredito` (apiFetch) | WIRED |
| Controller | `NotaCreditoService.preVisualizar` / `emitir` | direct call with `getTenantId()` / `getPrincipal()` | WIRED |
| `emitir` | `NumeracaoService` (NC series) | `proximoNumero(tenant, NC, origem.getAmbiente())` | WIRED |
| `emitir` | conta corrente / `t_pagamento` | lock and debit; negative estorno save | WIRED |
| `t_pagamento` | totalPago / KPI / alert | `@Formula SUM`, `RecebidoNoMes.somar`, `AlertasDiariosJob` totalPago check | WIRED |
| `deletePagamento` | `DocumentoFiscalService.eEstornoDeNotaCredito` | 409 `PAGAMENTO_ESTORNO` | WIRED |
| `updateProcesso` | `existeParaProcesso` | 409 `PROCESSO_COM_DOCUMENTOS_FISCAIS` | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real data | Status |
|----------|------|--------|-----------|--------|
| NC dialog step 2 | `preVisualizacao` | backend preview → `ComposicaoNotaCredito` on DB rows | yes | FLOWING |
| FR detail "Valor ainda creditável" | `d.valorCreditavelRestante` | `DocumentoFiscalDetalheResponse.de` → shared helper | yes | FLOWING |
| Payments list estorno row | `p.estorno` | `estornosPorPagamento` (tenant-scoped query) | yes | FLOWING |
| Dashboard KPI | `valores_recebidos_mes` | `pagamentoRepository.findByHonorarioId` → `RecebidoNoMes` | yes | FLOWING |

### Behavioral Spot-Checks (re-run by the verifier, post-fix HEAD `ca6a9c2`)

| Check | Command | Result | Status |
|-------|---------|--------|--------|
| Backend full suite with Testcontainers | `mvn -q -Dmaven.compiler.release=21 verify` | EXIT 0. Surefire 1018/0/0/0, failsafe 123/0/0/0. NotaCreditoServiceIT 11, NotaCreditoConcorrenciaIT 3, MigracaoFiscal135IT 4, DocumentoFiscalRepositoryIT 18, all 0 skipped. | PASS |
| SAST | `mvn -q -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check` | exit 0 | PASS |
| Web unit tests | `pnpm exec vitest run` | 12 files, 277 passed | PASS |
| Web types | `pnpm exec tsc --noEmit` | exit 0 | PASS |
| Web lint | `pnpm lint` | 0 errors, 20 pre-existing warnings | PASS |
| Source gates | `pnpm verify:faturacao`, `pnpm verify:documentos-fiscais` | OK / OK | PASS |
| Production build | `pnpm build` (env inline) | Compiled; `/financeiro/documentos-fiscais` (static) and `/[id]` (dynamic) listed | PASS |

The counts match the Fix Report's post-fix gate claims (1018 / 123 / 277).

### Probe Execution

No `scripts/*/tests/probe-*.sh` is declared or present for this phase. Step 7c is not applicable.

### Requirements Coverage

| Requirement | Source Plans | Status | Evidence |
|-------------|--------------|--------|----------|
| NCRD-01 | 01, 02, 03, 04, 05, 06, 07, 09, 10, 11, 13 | SATISFIED | Truth 1 |
| NCRD-02 | 01, 03, 04, 06, 07, 09, 10, 11, 12, 13 | SATISFIED | Truth 2 |
| NCRD-03 | 04, 06, 07, 08, 10, 12, 13 | SATISFIED | Truth 3 |

REQUIREMENTS.md maps only NCRD-01..03 to Phase 135, so there are no orphaned requirements.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (40 phase-modified source files) | — | TBD / FIXME / XXX / TODO / HACK | none | The only hits are the Portuguese word "TODOS" and the identifier "METODO". There are no debt markers. |
| `NotaCreditoService.java` | 124-139 | IN-06: the preview skips the processo/honorário link checks that emission performs | Info | Out of scope per the Fix Report. In the worst case a preview succeeds and emission then refuses; no wrong document is written. |
| `NotaCreditoService.java` | replay | IN-05: a replay returns the current remainder, not the one at emission time | Info | Out of scope per the Fix Report. Harmless. |

### Accepted by user decision (not gaps)

- The `HONORARIO_ATRASADO` lifetime dedup is kept: the alert fires only if it has never fired for that honorário. Eligibility after an NC is restored in code (`totalPago < valorTotal`).
- Validation by an accountant is deferred.
- The pre-existing setup wizard `Set.of` 500 is out of scope.

### Human Verification Required

#### 1. Live re-run of the NC dialog after the review fixes
**Test:** On a live stack, emit one Parcial and one Total NC through the UI.
**Expected:** Both are emitted, and the request body includes `totalEsperado` / `valorCreditavelEsperado`. Saldo, total pago and KPI move as in UAT steps 4-7.
**Why human:** The 12/12 live UAT predates commits `12f6721`, `6c112a7`, `da5ada7` and `aef69bb`, which changed the dialog and the error logic.

#### 2. WR-01 stale preview
**Test:** Pre-visualize in session 1, emit another NC on the same FR in session 2, then confirm in session 1.
**Expected:** 409 `NC_VALORES_ALTERADOS`, the user returns to the form, and no NC is written.
**Why human:** This is a multi-session UI race, and the fixer flagged it for a human logic check.

#### 3. CR-02 retry after a definitive 4xx
**Test:** Force a retryable 409 during emission, then click "Emitir nota de crédito" again.
**Expected:** A new request with a fresh key. The button is never a silent no-op.
**Why human:** It is only covered by vitest on the helper, not by a live click.

#### 4. CR-01 / WR-04 business-logic sign-off
**Test:** Review three rules:
- the NC debits the FR's cliente;
- a processo with fiscal documents cannot change cliente;
- the partial-NC withholding is computed on the clamped base.

**Expected:** The developer accepts the rules. The supporting tests passed in this verification.
**Why human:** The fixer marked these items "needs human check of the logic".

### Gaps Summary

There are no blocking gaps. All three roadmap success criteria are implemented, wired and tested:
- the whole backend suite passed on the post-fix HEAD, with every Testcontainers IT executed and none skipped;
- the whole web gate passed (vitest, tsc, lint, source gates, build).

The status is `human_needed`, not `passed`, for two reasons:
- The only live end-to-end evidence (135-HUMAN-UAT.md) was recorded before the code-review fixes. Four of those fixes changed the NC dialog's emission and error paths.
- The fixer explicitly asked for a human sign-off on CR-01, WR-01 and WR-04.

---

_Verified: 2026-10-05T18:10:00Z_
_Verifier: Claude (gsd-verifier)_
