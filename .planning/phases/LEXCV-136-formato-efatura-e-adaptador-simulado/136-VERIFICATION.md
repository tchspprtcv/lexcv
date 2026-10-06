---
phase: 136-formato-efatura-e-adaptador-simulado
verified: 2026-10-06T22:10:00Z
status: human_needed
score: 5/5 roadmap success criteria verified (DFE-01..DFE-07 satisfied within Phase 136 scope)
overrides_applied: 0
re_verification: false
deferred:
  - truth: "DFE-06: PDF and email mark a simulated document as 'simulação, sem validade fiscal'"
    addressed_in: "Phase 137"
    evidence: "ROADMAP SC4 of Phase 136: '(PDF e email herdam esta marca na Phase 137)'; Phase 137 depends on 136 and 'o PDF inclui a Nota de Crédito e a marca de simulação'"
  - truth: "Primary-source gate G1-G15 (Manual Técnico / efatura.cv XSD) confirmed"
    addressed_in: "Real-connection milestone (EFAT-01..06)"
    evidence: "User decision 2026-10-06 close-pending, recorded in 136-16-SUMMARY.md and 136-HUMAN-UAT.md"
human_verification:
  - test: "Post-review live smoke from the packaged jar: one FR and one NC reach 'Aceite (simulação)' with a 45-char CV3 IUD, and the stored XML passes xmllint against the vendored XSD"
    expected: "Same result as 136-HUMAN-UAT steps 3-5. The 12/12 live UAT ran before the 13 code-review fix commits (b50548a..59e4255), which changed the processor, claim/lease SQL, the emission composition and the notification recipients"
    why_human: "The live run needs a full stack (PostgreSQL, jar, browser). Unit tests and the Testcontainers ITs cover the new paths; nobody has run the app end to end since the fixes"
  - test: "WR-04: try to issue an FR, and an NC, for an emitente whose firma is over 150 characters. Also open a REJEITADO document"
    expected: "Emission is refused with 422 FIRMA_EXCEDE_150 before any write, and the NC message says the firma comes from the FR. The REJEITADO card no longer promises that reprocessing fixes the problem"
    why_human: "The code-review fixer marked this item 'requires human verification'. It is a copy and UX decision (UI-SPEC copy was changed on purpose)"
  - test: "WR-03/WR-05: an NC whose FR ended REJEITADO, and a document with a deterministic snapshot defect"
    expected: "The NC goes straight to REJEITADO ORIGEM_REJEITADA with the 'Reprocesse primeiro a fatura-recibo' message. The defective snapshot goes to REJEITADO DADOS_INVALIDOS with no 8-attempt retry"
    why_human: "The fixer marked this 'requires human verification': is the message clear to an office user?"
  - test: "WR-07/IN-06: the banner during loading or on error, and focus after a successful reprocess"
    expected: "The 'Modo simulado' banner is visible while the state loads or fails. After you confirm a reprocess, focus moves to the 'Comunicação fiscal' card title, not to <body>"
    why_human: "Visual and focus behaviour cannot be proven by grep or unit tests"
---

# Phase 136: Formato eFatura e Adaptador Simulado Verification Report

**Phase Goal:** Cada documento emitido produz um XML no formato eFatura exato, com IUD, e é comunicado em segundo plano a um adaptador de interface única com implementação simulada, sempre identificado como sem validade fiscal.
**Verified:** 2026-10-06T22:10:00Z
**Status:** human_needed
**Re-verification:** No. This is the initial verification.

## Goal Achievement

### Observable Truths (ROADMAP success criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Each issued document has an eFatura (DFE) XML, validated against the official schema before it counts as ready, and a 45-char IUD with the official structure, marked as test environment | VERIFIED | `ProcessadorComunicacaoFiscal.comunicar` runs the pipeline: snapshot → `IudGerador.gerar(repositorioPara(SIMULADO)=3, …, ledPara=99999, …)` → `DfeXmlBuilder.construir` → `DfeMarshaller` → `DfeValidador.validar`. The XML row is written only when `validacao.valido()` is true; otherwise the result is `Rejeitado`. The IUD is `CV` + a 42-digit payload (1+6+9+5+2+9+10) + a Luhn DV, so 45 chars, and `IudGeradorTest` matches the official vector `CV1200520123456789000112345678901112345678904`. The validator is hardened against XXE (FSP, empty ACCESS_EXTERNAL_*, disallow-doctype, classpath resolver). There are 22 vendored XSDs, checked by SHA-256 in `XsdEfaturaIntegridadeTest`. `DfeXmlBuilderTest` asserts that FR and NC validate against the XSD. |
| 2 | The adapter mode comes from deployment config; in v3.0 only "simulado" exists, and an unknown mode stops startup | VERIFIED | `EfaturaConfig.efaturaGateway` accepts only the exact value `SIMULADO`. Anything else throws `IllegalStateException` ("só SIMULADO existe"). `application.yml` sets `modo: ${EFATURA_MODE:SIMULADO}`, and `EFATURA_MODE` is in `.env.example`, all three compose files and `deploy.yml`. `EfaturaConfigTest` passes 17/17. The live UAT confirmed that REAL and an empty value both exit 1. There is a single port, `EfaturaGateway`, and `SimuladoEfaturaGateway` is its only implementation. |
| 3 | Each document shows its communication state (pending, accepted in simulation, rejected, error). The state updates in the background without slowing payment registration. Transient failures are retried automatically. A user with `financeiro:edit` reprocesses a document in error | VERIFIED | **Background:** `FiscalOutboxJob` runs `@Scheduled` every 30 s with `FOR UPDATE SKIP LOCKED`, a lease, a version guard and a per-item lease renewal (WR-02). Nothing on the emission/payment path references the gateway, builder or IUD (grep: only the job, processor, transactions and config do). The scheduler pool is 3. **Retry:** `EstadoComunicacaoMapper` keeps `ErroTransitorio` PENDENTE below 8 attempts and sets ERRO at 8. `BackoffComunicacao` sets the delay, and WR-01 caps reclaims. **Reprocess:** `POST /documentos-fiscais/{id}/comunicacao/reprocessar` has `@PreAuthorize("hasAuthority('financeiro:edit')")`. It is tenant-scoped (`findByIdAndTenantId`, `reporPendente(tenantId, …)`), returns 409 for a non-reprocessable state and is audited. The frontend `podeReprocessarComunicacao` uses the exact `financeiro:edit` check. **UI:** the badge, the "Comunicação" column, the filter and the "Comunicação fiscal" card are wired, and the page polls only while PENDENTE. |
| 4 | No simulated document ever appears as DNRE-authorised: state, series, IUD and screens mark it clearly as "simulação, sem validade fiscal" | VERIFIED (Phase 136 scope; PDF/email deferred to 137) | `AmbienteFiscal` has only `SIMULADO`, and `EstadoComunicacaoFiscal` has no AUTORIZADO constant. The mapper's exhaustive switch can only produce `ACEITE_SIMULADO`. A DB CHECK `ck_comunicacao_fiscal_autorizado_producao` (migration 136, idempotent, README row 22) is proven by `MigracaoFiscal136IT` (SQLSTATE 23514). Series use the `SIM-` prefix, and the IUD uses repository 3 ("Teste"). The card shows `COPY_IUD_TESTE` "Ambiente de teste — sem validade fiscal". The fail-closed `ModoSimuladoBanner` is on the list, detail and honorário pages. "Simulação — sem validade fiscal" also appears in the payment and NC dialogs and on the detail page. `verify:documentos-fiscais` passes, including its "sem linguagem de autorização" check. |
| 5 | A persistent communication failure creates an in-app notification for the office's responsible users | VERIFIED | The processor calls `notificar` after the ERRO commit, and the WR-01 exhausted sweep also notifies through `notificarEsgotada`. `NotificacaoComunicacaoFiscal` sends to holders of effective `financeiro:manage` or `financeiro:edit` (WR-06), with per-episode dedup (reprocessamentos). The category `COMUNICACAO_FISCAL_FALHOU(false)` cannot be muted. `NotificacaoComunicacaoFiscalTest` passes 10/10, and `FiscalOutboxJobFalhasForcadasIT` and `FiscalOutboxJobIT` pass. |

**Score:** 5/5 truths verified.

### Deferred Items

| # | Item | Addressed In | Evidence |
|---|------|-------------|----------|
| 1 | DFE-06: the PDF and email marking | Phase 137 | ROADMAP SC4: "PDF e email herdam esta marca na Phase 137" |
| 2 | Primary-source format gate G1–G15 | Real-connection milestone | User decision on 2026-10-06: close-pending (not a gap) |

### Required Artifacts

| Artifact | Status | Details |
|----------|--------|---------|
| `backend/src/main/resources/xsd/efatura/` (22 XSD + README) | VERIFIED | SHA-256 integrity test passes |
| `fiscal/efatura/IudGerador.java` | VERIFIED | Correct layout and Luhn; used by the processor |
| `fiscal/efatura/DfeXmlBuilder.java`, `DfeMarshaller.java`, `DfeValidador.java`, `ClasspathXsdResolver.java` | VERIFIED | Wired into the processor and the simulated gateway |
| `fiscal/efatura/EfaturaGateway.java`, `SimuladoEfaturaGateway.java`, `ResultadoComunicacao.java` (sealed), `EfaturaConfig.java` | VERIFIED | Single bean, fail-fast |
| `services/fiscal/ProcessadorComunicacaoFiscal.java`, `EstadoComunicacaoMapper.java`, `BackoffComunicacao.java`, `ComunicacaoFiscalTransacoes.java`, `repositories/FilaComunicacaoFiscal.java` | VERIFIED | Substantive and tenant-scoped SQL |
| `jobs/FiscalOutboxJob.java` | VERIFIED | `@Scheduled`; end-to-end IT on PostgreSQL |
| `services/fiscal/ReprocessamentoComunicacaoService.java` + controller route | VERIFIED | Exact `financeiro:edit` check; 404/409; audited |
| `services/fiscal/NotificacaoComunicacaoFiscal.java`, `CategoriaNotificacao.COMUNICACAO_FISCAL_FALHOU` | VERIFIED | Cannot be muted; dedup per episode |
| `backend/migrations/136-efatura-comunicacao.sql` + README | VERIFIED | Idempotent; CHECK, outbox columns and insert-only XML table |
| `web/src/lib/comunicacao-fiscal.ts`, `components/shared/comunicacao-estado-badge.tsx`, `modo-simulado-banner.tsx`, `documentos-fiscais/[id]/comunicacao-fiscal-card.tsx`, `reprocessar-comunicacao.tsx`, `columns.tsx` | VERIFIED | Rendered on the list, detail and honorário pages |

### Key Link Verification

| From | To | Via | Status |
|------|----|-----|--------|
| Emission (134/135) | Outbox | A `t_comunicacao_fiscal` PENDENTE row created in the emission tx; no I/O in that tx | WIRED |
| FiscalOutboxJob | ProcessadorComunicacaoFiscal | `transacoes.reclamar` → `processador.processar(item)` | WIRED |
| Processor | IudGerador / Builder / Validator / Gateway | Direct calls in `comunicar` | WIRED |
| Processor | t_documento_fiscal_xml | `transacoes.gravarXml`, written only when the XML is valid | WIRED |
| Processor (ERRO) | NotificacaoComunicacaoFiscal | `notificar` after the commit | WIRED |
| EfaturaConfig | EfaturaGateway bean | `@Bean efaturaGateway` (the only implementation) | WIRED |
| Detail page | Reprocess route | `use-faturacao` mutation → `POST …/comunicacao/reprocessar` | WIRED |
| estado-emissao `modoComunicacao` | ModoSimuladoBanner | `mostrarBannerModoSimulado` (fail-closed) | WIRED |

### Data-Flow Trace (Level 4)

| Artifact | Data | Source | Real Data | Status |
|----------|------|--------|-----------|--------|
| comunicacao-fiscal-card | `c.estado`, `c.iud`, `tentativas`, `ultimoErro` | `ComunicacaoFiscalResumo` on the document detail (from `t_comunicacao_fiscal` + `t_documento_fiscal_xml`) | Yes (ResumoTest 7/7; live UAT showed the real IUD) | FLOWING |
| columns.tsx | `estadoComunicacao` | List DTO | Yes | FLOWING |

### Behavioral Spot-Checks (run by the verifier, after the review fixes, HEAD 8c75caf)

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Phase 136 backend unit tests (16 classes) | `mvn -o -Dmaven.compiler.release=21 verify -Dtest=… -Dit.test=…` | 184 tests, 0 failures/errors/skipped | PASS |
| Phase 136 Testcontainers ITs | same run | MigracaoFiscal136IT 8, FilaComunicacaoFiscalIT 16, ReprocessarComunicacaoIT 8, FiscalOutboxJobIT 7, FiscalOutboxJobFalhasForcadasIT 1; 40 tests, 0 failures | PASS |
| Web comunicacao-fiscal lib | `pnpm exec vitest run src/lib/comunicacao-fiscal.test.ts` | 45/45 | PASS |
| Web static gates | `pnpm verify:documentos-fiscais`, `pnpm verify:faturacao`, `tsc --noEmit` | OK / OK / exit 0 | PASS |

The full suite (1227 unit / 163 IT, SpotBugs 0, web 326) is the fixer's report. The verifier did not re-run it; the targeted re-runs above agree with it.

### Probe Execution

Step 7c: SKIPPED. The phase declares no `scripts/*/tests/probe-*.sh`.

### Requirements Coverage

| Requirement | Plans | Status | Evidence |
|-------------|-------|--------|----------|
| DFE-01 | 6 plans | SATISFIED | Truth 1 |
| DFE-02 | 6 plans | SATISFIED | Truth 1 (repo 3, LED 99999, CV3 IUD) |
| DFE-03 | 3 plans | SATISFIED | Truth 2 |
| DFE-04 | 10 plans | SATISFIED | Truth 3 |
| DFE-05 | 5 plans | SATISFIED | Truth 3 (reprocess) |
| DFE-06 | 12 plans | SATISFIED for Phase 136 scope; PDF/email deferred to 137 | Truth 4. REQUIREMENTS.md correctly keeps DFE-06 Pending until 137 |
| DFE-07 | 5 plans | SATISFIED | Truth 5 |

No requirement is orphaned. Every DFE-01..07 ID mapped to Phase 136 appears in at least one plan.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (102 phase-modified backend/web files) | - | TBD/FIXME/XXX | none found | - |
| `ProcessadorComunicacaoFiscal.java` | notificar | A lost notification is never retried (review IN-02, out of scope) | Info | The ERRO state is still visible on the document; only the push notification can be lost on a DB blip |
| `ProcessadorComunicacaoFiscal.java` | REJEITADO path | REJEITADO does not notify (review IN-03, needs a product decision) | Info | Follows CONTEXT ("ERRO definitivo"); SC5 says "falha persistente", which ERRO satisfies |

### Human Verification Required

#### 1. Post-review live smoke
**Test:** From the packaged jar, issue an FR and an NC. Wait for "Aceite (simulação)". Run xmllint on the stored XML.
**Expected:** The 136-HUMAN-UAT steps 3–5 results hold after the fix commits.
**Why human:** The 12/12 live UAT came before the 13 behaviour-changing review fixes.

#### 2. WR-04: firma over 150
**Test:** Issue an FR and an NC for an emitente whose firma is over 150 characters. Open a REJEITADO document.
**Expected:** 422 `FIRMA_EXCEDE_150` before any write. The REJEITADO copy does not promise that reprocessing fixes it.
**Why human:** The fixer flagged this "requires human verification"; it is a copy decision.

#### 3. WR-03 / WR-05: immediate rejections
**Test:** An NC on a REJEITADO FR, and a document with a deterministic snapshot defect.
**Expected:** REJEITADO `ORIGEM_REJEITADA` / `DADOS_INVALIDOS` at once, with clear messages.
**Why human:** The fixer flagged this; message clarity for office users.

#### 4. WR-07 / IN-06: banner and focus
**Test:** Throttle or fail the estado-emissao request. Then reprocess a document in ERRO.
**Expected:** The banner stays visible. Focus lands on the card title after the dialog closes.
**Why human:** Visual and focus behaviour.

### Gaps Summary

There are no blocking gaps. All 5 roadmap success criteria and DFE-01..07 (DFE-06 within Phase 136 scope) are implemented, wired and covered by tests that pass at HEAD. The status is `human_needed` because the only live end-to-end run (136-16) came before the code-review fixes. The fixer itself flagged WR-03/04/05 for human verification. The G1–G15 primary-source gate is closed as pending by user decision. Accountant validation, the setup 500 and the role-drift startup abort are out of scope.

---

_Verified: 2026-10-06T22:10:00Z_
_Verifier: Claude (gsd-verifier)_
