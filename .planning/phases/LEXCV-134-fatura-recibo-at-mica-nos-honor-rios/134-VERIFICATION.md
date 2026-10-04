---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
verified: 2026-10-04T19:20:00Z
status: human_needed
score: 6/6 roadmap success criteria verified in code and automated tests (12/12 EMIS requirements satisfied)
overrides_applied: 0
human_verification:
  - test: "Ambiguous-failure retry with the post-review key lifecycle (CR-02). With billing active, fill the form, open the preview, abort POST /pagamentos at the network level (or force a 5xx), then click 'Voltar e editar' and click 'Registar pagamento' -> 'Emitir' again WITHOUT changing any value."
    expected: "The second POST carries the SAME chaveIdempotencia as the aborted one. If the first one had committed, the response is 200 with the same pagamento id and documentoFiscal.id. Only one Fatura-Recibo exists. Changing any value before resubmitting generates a NEW key."
    why_human: "The UI changed after the live E2E. HUMAN-UAT step 7c recorded the OLD behaviour ('after Voltar e editar and reopening, a new key was used'), which is the bug CR-02 fixed. Vitest covers the pure helpers (tentativaParaPedido/marcarPorResolver) but not the component wiring in a browser."
  - test: "Payment card modes after WR-03/WR-04. On /financeiro/{id}: (a) make GET /faturacao/estado-emissao fail; (b) slow it down; (c) log in with a role that has financeiro:manage + financeiro:view but NOT financeiro:edit; (d) as a financeiro:edit user, get a 403 on preview."
    expected: "(a) red message 'Não foi possível confirmar se a faturação está ativa' plus 'Tentar novamente', which refetches; (b) legacy form with submit disabled; (c) no payment form is offered; (d) banner 'Não tem permissão para registar pagamentos.'. The legacy submit is enabled only when billing is known to be off."
    why_human: "These render paths were added after the E2E run. Only the pure mode function is unit-tested."
  - test: "Inline error presentation after IN-02/IN-01. Force a 503 FATURACAO_OCUPADA on emission and a 500 on preview; then look at the 'Método' column of a billed payment."
    expected: "The 503 shows the backend message inline with no duplicate toast. The 5xx is reported once. The Método column shows 'Transferência bancária', not 'TRANSFERENCIA'. Legacy free-text methods still show verbatim."
    why_human: "This UI behaviour changed after the E2E run and needs visual confirmation."
  - test: "Replay after deactivation (WR-05), through the real UI/proxy. Emit a FR with key K, deactivate billing, then POST /pagamentos again with the same body and key K."
    expected: "200 with the original pagamento and documentoFiscal. No second payment and no conta corrente credit. The same key with different values gets 409 CHAVE_REUTILIZADA."
    why_human: "The review fix report flags this 'requires human verification'. IT repeticaoDepoisDeDesligarAFaturacaoDevolveOMesmoResultado passes, but the end-to-end path through the Next rewrite has not been exercised since the fix."
---

# Phase 134: Fatura-Recibo Atómica nos Honorários Verification Report

**Phase Goal:** Num escritório com faturação ativa, registar um pagamento de honorários emite na mesma operação uma Fatura-Recibo fiscalmente coerente e imutável, e o utilizador vê, confere e consulta esses documentos.
**Verified:** 2026-10-04T19:20:00Z
**Status:** human_needed
**Re-verification:** No. This is the initial verification; no earlier VERIFICATION.md existed.

All 6 success criteria are backed by code I read and by a fresh, green test run. The status is `human_needed`, not `passed`, for one reason: several UI paths were changed by the code-review fixes after the 13/13 live E2E. One E2E record, step 7c, now describes behaviour that has since been deliberately changed.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Registering a payment with billing active writes the payment, the FR and the conta-corrente update in one transaction. A failure in any part rolls back all of them. A double submit produces one payment and one document. | VERIFIED | **One transaction:** `PagamentoFaturadoService.registar` is `@Transactional` and, in order: locks configuração → cliente → processo → re-checks the honorário (CR-01) → creates/locks and credits the conta corrente → saves the payment → takes the series number (last lock) → saves the immutable document, the line, the PENDENTE communication and the audit event.<br>**Delegation:** `ResourceController.createPagamento` delegates when `faturacaoAtiva` is true and is not itself transactional.<br>**Idempotency:** the key is mandatory (`exigirChave`) and looked up under the configuração lock. The same request returns the stored result (200); a different request gets 409 `CHAVE_REUTILIZADA`. Unique constraints on (chave) and (pagamento) are the backstop.<br>**ITs (fresh run, green):** `rollbackQuandoAuditoriaFalha`, `rollbackQuandoNumeracaoFalha`, `rollbackQuandoInsertDoDocumentoFalha`, `mesmaChaveSequencial`, `mesmaChaveDoisPedidosEmSimultaneo`.<br>**Frontend:** a synchronous `emitindoRef` guard plus the payload-bound key. |
| 2 | The preview (adquirente, base, IVA, retenção, total) appears before confirmation, and emission happens only after confirming. The VAT-inclusive amount is split per the office regime; an exempt office shows the exemption reason. | VERIFIED | **Endpoint:** `POST /faturacao/pre-visualizacao` (`financeiro:edit`) uses the same pure `ComposicaoFaturaRecibo.compor` as the emission. The IT `preVisualizacaoIgualAEmissaoESemEfeitos` proves it has no side effects and matches the emission.<br>**Form flow:** preview, then `PagamentoFaturadoDialog`, then `emitir` POSTs only from the dialog's confirm button.<br>**Calculation:** `CalculoFiscal` computes base = round(total·100/(100+taxa), HALF_UP) and IVA as the residual. ISENTO gives base = total and IVA 0.<br>**Dialog:** shows "Isento" and "Motivo de isenção".<br>**Tests:** IT `escritorioIsento`; live supplementary isento run. |
| 3 | Optional withholding (suggested rate, editable, on the VAT-free base). The document shows the amount withheld and the net received, and the conta corrente is credited with the total. The payment method maps to the eFatura payment means. | VERIFIED | **Withholding:** retenção = round(base·taxa/100) and líquido = total − retenção. The conta corrente is credited `calculo.total()`.<br>**Suggested rate:** comes from `estado-emissao.taxaRetencaoSugerida` and pre-fills an editable input.<br>**Payment means:** `MetodoPagamento` maps DINHEIRO→10, TRANSFERENCIA→30, CHEQUE→20, CARTAO→48, OUTRO→ZZZ, snapshotted into `meio_pagamento_codigo`. Codes are [ASSUMED] UNCL4461; Phase 136 confirms them against the official XSD.<br>**Tests:** reference vector 120 000 → 104 347,83 / 15 652,17 / 20 869,57 / 99 130,43 (CalculoFiscalTest, live step 4). |
| 4 | Emission is refused with a clear message when the date is not today (Cape Verde time; no date means today), or when the cliente lacks a valid CV NIF, a name, or a morada of at most 100 characters. The message says what to fix. | VERIFIED | **Date:** `ValidacaoEmissao.validarData` returns hoje when null and otherwise gives 422 `DATA_PAGAMENTO_RETROATIVA`, campo `dataPagamento`. Hoje comes from `Clock` in `Atlantic/Cape_Verde`.<br>**Adquirente:** `validarAdquirente` refuses an invalid NIF (`^[1-9]\d{8}$`), nome outside 3..150, a missing morada or one over 100, and a localidade over 100 (WR-02). Each gives 422 `ADQUIRENTE_INCOMPLETO` with the field and copy saying what to fix.<br>**Frontend:** `interpretarErroEmissao` routes field errors under the field and adquirente errors to a banner with "Abrir cliente".<br>**Tests:** ValidacaoEmissaoTest; live step 7a/7b. |
| 5 | The FR stores emitente and adquirente as they were at emission, and no screen or endpoint edits or deletes it. A billed payment cannot be deleted. Deleting its cliente, processo or honorário is refused, and a client merge preserves the document links. | VERIFIED | **Entity:** `DocumentoFiscal` is `@Immutable` with every column `updatable=false`.<br>**Repository:** `DocumentoFiscalRepository` extends only `Repository` (save + tenant-scoped finders); the method set is pinned by DocumentoFiscalImutabilidadeTest. The only write is the native `repontarCliente` UPDATE of `cliente_id`.<br>**No mutation route:** none exists in `DocumentoFiscalController`, and the detail page has no mutation (verify:documentos-fiscais gate).<br>**Guards:** `deletePagamento` gives 409 `PAGAMENTO_FATURADO`. `deleteCliente` and `deleteProcesso` take a row lock and then return 409 `*_COM_DOCUMENTOS_FISCAIS`. `deleteHonorario` takes the processo lock and then returns 409.<br>**Merge:** calls `repontarCliente` before deleting the secondary.<br>**Tests:** IT `snapshotNaoMudaDepoisDeEditarClienteEConfiguracao`, the guard race ITs (11, incl. the honorário race), `fusaoDepoisDeEmissaoEmCursoRepontaDocumentos`. |
| 6 | A user with `financeiro:view` lists their office's fiscal documents (filters: cliente, period, type, status) and opens the detail, never seeing another office's. Payments from before activation show "sem documento fiscal" and are not invoiced retroactively. | VERIFIED | **Endpoints:** `GET /documentos-fiscais` and `/{id}` are `hasAuthority('financeiro:view')`, with the tenant taken only from the principal.<br>**Query:** the native query's first predicate is `d.tenant_id = :tenantId`. The detail uses `findByIdAndTenantId` and returns 404 for another office's id.<br>**UI:** filters for cliente/de/ate/tipo/estado, server pagination and URL state. Access is gated by `podeLerDocumentosFiscais`, otherwise `AccessDeniedState`.<br>**Pre-activation payments:** `pagamentos-card` shows "Sem documento fiscal" when `documentoFiscal` is null. There is no backfill in the migration and no "emit for existing payment" route.<br>**Tests:** DocumentoFiscalControllerAutorizacaoTest, DocumentoFiscalRepositoryIT (tenant isolation), live step 12 (403 for assistente, tenant B gets 404). |

**Score:** 6/6 truths verified.

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `backend/migrations/134-create-documento-fiscal-tables.sql` + README row 20 | VERIFIED | Idempotent (`IF NOT EXISTS`, catalogue-checked index); MigracaoFiscal134IT 6/6 |
| `models/DocumentoFiscal`, `DocumentoFiscalLinha`, `ComunicacaoFiscal` | VERIFIED | `@Immutable`, `updatable=false` |
| `repositories/DocumentoFiscalRepository` (+ LigacaoCliente) | VERIFIED | Narrow, tenant-scoped |
| `services/fiscal/CalculoFiscal`, `ValidacaoEmissao`, `ComposicaoFaturaRecibo` | VERIFIED | Pure; shared by preview and emission |
| `services/fiscal/PagamentoFaturadoService` | VERIFIED | Atomic emission, lock order, idempotency, CR-01 re-check |
| `services/fiscal/PreVisualizacaoFaturaService`, `DocumentoFiscalService` | VERIFIED | Read-only and tenant-scoped |
| `controllers/DocumentoFiscalController`; `ResourceController` changes | VERIFIED | Delegation, guards, merge re-pointing, payment list with `documentoFiscal` |
| `web/.../financeiro/[id]/pagamento-faturado-form.tsx`, `-dialog.tsx`, `pagamentos-card.tsx`, `page.tsx` | VERIFIED (UI behaviour needs human re-check) | Wired to `usePreVisualizacaoFaturacao` and `useCreatePagamento` |
| `web/.../financeiro/documentos-fiscais/page.tsx`, `[id]/page.tsx`, `columns.tsx` | VERIFIED | Wired to `useDocumentosFiscais` and `useDocumentoFiscal`; read-only |
| `web/src/lib/idempotencia.ts`, `erros-emissao.ts`, `hooks/use-faturacao.ts`, `scripts/verify-documentos-fiscais.mjs` | VERIFIED | Unit-tested; the gate passes |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| `createPagamento` | `PagamentoFaturadoService.registar` | `faturacaoAtiva(tenantId)` branch | WIRED |
| `createPagamento` (billing off) | `resultadoGuardado` → legacy branch | WR-05 pre-check | WIRED |
| Preview service and emission | `ComposicaoFaturaRecibo.compor` | Same pure function | WIRED |
| `listHonorarioPagamentos` | `documentoFiscalService.referenciasPorPagamento` | Batch lookup → `PagamentoComDocumentoResponse` | WIRED |
| `mergeClientes` | `documentoFiscalService.repontarCliente` | Native UPDATE of `cliente_id` | WIRED |
| delete cliente/processo/honorário/pagamento | `existePara*` | Lock, then exists check, then 409 | WIRED |
| Form | `/faturacao/pre-visualizacao` → dialog → `POST /pagamentos` with `chaveIdempotencia` | Hooks via `apiFetch` | WIRED |
| Payments card | `/financeiro/documentos-fiscais/{id}` | Link from `p.documentoFiscal.id` | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Status |
|----------|------|--------|--------|
| Preview dialog | `preVisualizacao` | Backend preview response (DB config, cliente and parameter) | FLOWING |
| Documentos fiscais list | `documentos.data.content` | `DocumentoFiscalRepository.buscar` native query | FLOWING |
| Document detail | `useDocumentoFiscal` | `findByIdAndTenantId` + lines + communication | FLOWING |
| Payments card "Documento fiscal" column | `p.documentoFiscal` | `referenciasPorPagamento` | FLOWING |

### Behavioral Spot-Checks (re-run by the verifier, after all review fixes)

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full backend suite incl. Testcontainers ITs | `mvn -Dmaven.compiler.release=21 verify` | EXIT 0. Surefire 865 tests, 0 failures, 0 errors, 0 skipped. Failsafe 99 / 0 / 0 / 0. Phase ITs: GuardasDocumentoFiscalConcorrenciaIT 11, PagamentoFaturadoServiceIT 10, PagamentoFaturadoConcorrenciaIT 4, DocumentoFiscalRepositoryIT 13, MigracaoFiscal134IT 6 | PASS |
| Web typecheck | `npx tsc --noEmit` | clean | PASS |
| Web unit tests | `pnpm test` | 189/189 | PASS |
| Source gates | `pnpm verify:faturacao`, `pnpm verify:documentos-fiscais` | OK / OK | PASS |
| Lint | `pnpm lint` | 0 errors, 20 warnings (pre-existing) | PASS |

### Probe Execution

Step 7c: no `scripts/*/tests/probe-*.sh` is declared or present for this phase, so there was nothing to run.

### Requirements Coverage

Every EMIS ID appears in at least one PLAN `requirements:` field (134-14 lists all 12). No orphaned IDs: REQUIREMENTS.md maps exactly EMIS-01..12 to Phase 134.

| Requirement | Status | Evidence |
|-------------|--------|----------|
| EMIS-01 atomic emission / rollback | SATISFIED | SC1; rollback ITs |
| EMIS-02 preview before confirming | SATISFIED | SC2; preview/emission parity IT |
| EMIS-03 VAT-inclusive split / isento reason | SATISFIED | SC2; CalculoFiscal, isento IT |
| EMIS-04 withholding on base, credit of total | SATISFIED | SC3 |
| EMIS-05 date must be today (CV), default today | SATISFIED | SC4 |
| EMIS-06 NIF/name/morada refusal with what to fix | SATISFIED | SC4 |
| EMIS-07 double submit gives single payment + FR | SATISFIED | SC1; same-key concurrency IT (frontend retry wiring: human item 1) |
| EMIS-08 snapshot + immutability | SATISFIED | SC5 |
| EMIS-09 delete guards + merge preserves links | SATISFIED | SC5 |
| EMIS-10 method → eFatura means | SATISFIED | SC3 (codes provisional until Phase 136 XSD) |
| EMIS-11 tenant-scoped list/detail with filters | SATISFIED | SC6 |
| EMIS-12 no retroactive invoicing, "sem documento fiscal" | SATISFIED | SC6 |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (phase files scanned) | — | TBD/FIXME/XXX | — | None found |
| `ResourceController.registarPagamentoLegado` | ~3163 | Unlocked read-modify-write of the conta corrente | Info | Pre-existing body, frozen by the CFG-03 hash guard. It runs only with billing off, so it cannot race the FR credit within this phase's scope. |
| `pagamento-faturado-form.tsx` | state | The idempotency key lives in component state | Info | Navigating away after an ambiguous failure loses the key; the refreshed payments list is the remaining signal. Documented as residual in the REVIEW fix report. |
| Review IN-03 | — | The emitted FR is not compared with the confirmed preview | Info | Skipped as optional. If the cliente or config is edited between preview and confirm, the FR reflects the live data. This does not break any SC. |
| `MetodoPagamento` codes | 17-21 | [ASSUMED] UNCL4461 codes | Info | Phase 136 validates against the official eFatura schema. |

### Human Verification Required

#### 1. Ambiguous-failure retry reuses the key (CR-02, changed after E2E)
- **Test:** With billing active, abort the emission POST (network or 5xx), click "Voltar e editar", then resubmit the identical values and confirm.
- **Expected:** The same `chaveIdempotencia` is sent. The result is a single payment and a single FR (200 replay if the first committed). Changing a value produces a new key.
- **Why human:** HUMAN-UAT step 7c recorded the pre-fix behaviour, a new key after reopening, which is exactly what CR-02 removed. Only the helpers are unit-tested.

#### 2. Payment card modes (WR-03, WR-04)
- **Test:** Try four cases: estado-emissao failing, estado-emissao slow, a manage+view role without edit, and a 403 on preview.
- **Expected:** An error state with "Tentar novamente"; the legacy form disabled while loading; no form without exact `financeiro:edit`; the 403 banner "Não tem permissão para registar pagamentos.".
- **Why human:** These render paths are new since the E2E run.

#### 3. Inline 5xx/503 presentation and the Método label (IN-02, IN-01)
- **Test:** Force a 503 `FATURACAO_OCUPADA` and a 500; then view a billed payment row.
- **Expected:** The backend message appears inline with no double toast, and the Método column shows the label.
- **Why human:** This is visual presentation that changed after the E2E run.

#### 4. Replay after billing deactivation through the full stack (WR-05)
- **Test:** Emit with key K, deactivate billing, then repeat the POST with K.
- **Expected:** 200 with the original payment and FR, no new payment, no extra credit.
- **Why human:** The review flags it "requires human verification". The IT passes, but the live path has not been re-run since the fix.

### Gaps Summary

No blocking gaps. Every roadmap success criterion and every EMIS requirement is implemented, wired and covered by tests that pass on a fresh run after all review fixes. The four human items exist only because the code-review fix commits (dd79836, c884f4c, 2f8f917, cc2c447, fc583cb) changed user-facing behaviour after the automated live E2E recorded in 134-HUMAN-UAT.md, and one E2E record (step 7c) is now stale.

Two items are deliberately not treated as gaps:
- The accountant validation of the fiscal rules is deferred by the user (STATE.md Pending Todos).
- The setup-wizard / tenant-provisioning 500 is pre-existing and out of scope.

---

_Verified: 2026-10-04T19:20:00Z_
_Verifier: Claude (gsd-verifier)_
