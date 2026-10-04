---
phase: 134
slug: fatura-recibo-at-mica-nos-honor-rios
status: approved
nyquist_compliant: true
wave_0_complete: true
approved: 2026-10-04
created: 2026-10-04
---

# Phase 134 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution. Derived from 134-RESEARCH.md "## Validation Architecture".

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Mockito (surefire), Testcontainers 1.20.4 `@DataJpaTest` (failsafe `*IT`), vitest 4 (web), Node `verify:*.mjs` source gates |
| **Config file** | `backend/src/test/resources/application.properties`, `web/vitest.config.ts` |
| **Quick run command** | `cd backend && mvn -q -Dmaven.compiler.release=21 test -Dtest='<touched test classes>'` ; `cd web && pnpm test` |
| **Full suite command** | `cd backend && mvn -Dmaven.compiler.release=21 verify && mvn -Dmaven.compiler.release=21 spotbugs:check` ; `cd web && pnpm test && npx tsc --noEmit && pnpm lint && pnpm verify:faturacao && pnpm verify:documentos-fiscais` |
| **Estimated runtime** | ~300 seconds (backend verify with ITs) |

Docker must be running for ITs (`docker info`; start `dockerd` if needed). `~/.docker-java.properties` sets `api.version=1.44`.

---

## Sampling Rate

- **After every task commit:** quick command for the touched classes (+ `pnpm test` for web tasks)
- **After every plan wave:** full suite command
- **Before `/gsd:verify-work`:** full suite green with ITs actually executed by Testcontainers (no scratch substitutes)
- **Max feedback latency:** 120 seconds for quick runs

---

## Per-Requirement Verification Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| EMIS-01 | All-or-nothing: an injected failure after the pagamento save / after numbering / at the document insert (e.g. a duplicated `pagamento_id`) leaves 0 pagamento, 0 doc, CC unchanged, series counter unchanged | IT | `-Dit.test=PagamentoFaturadoServiceIT` | ✅ PagamentoFaturadoServiceIT (10 IT) |
| EMIS-01 | Lock order: config → cliente → processo → CC → série; lock before any other finder (Mockito `InOrder`) | unit | `-Dtest=PagamentoFaturadoServiceTest` | ✅ PagamentoFaturadoServiceTest |
| EMIS-01 | Concurrency: N parallel emissions, same tenant/different clients → numbers 1..N, CC sums exact; two tenants independent (two emitters, C12) | IT | `-Dit.test=PagamentoFaturadoConcorrenciaIT` | ✅ PagamentoFaturadoConcorrenciaIT (4 IT) |
| EMIS-02 | Preview = emission figures for the same input; preview writes nothing (row counts and series counter unchanged); 422s identical | unit + IT | `-Dtest=PreVisualizacaoFaturaServiceTest`, IT case | ✅ PreVisualizacaoFaturaServiceTest + PagamentoFaturadoServiceIT (paridade) |
| EMIS-03/04 | Vectors table + invariants property test; isento uses `mencao`; CC += total | unit | `-Dtest=CalculoFiscalTest` | ✅ CalculoFiscalTest |
| EMIS-05 | null → hoje; yesterday/tomorrow → 422 `DATA_PAGAMENTO_RETROATIVA`; `Clock.fixed` 23:59:59 / 00:00:01 CV; year rollover uses the new series | unit | `-Dtest=PagamentoFaturadoServiceTest#data*` | ✅ PagamentoFaturadoServiceTest + ValidacaoEmissaoTest |
| EMIS-06 | NIF null / `012345678` / 8 digits; nome blank/2/151 chars; morada blank/101 → 422 with `campo`; nothing written | unit | `-Dtest=ValidacaoEmissaoTest` | ✅ ValidacaoEmissaoTest |
| EMIS-07 | Same key sequential → 200, same ids; same key + different value → 409 `CHAVE_REUTILIZADA`; **two threads, same key, released together** → exactly 1 pagamento, 1 doc, CC credited once, the loser returns the same doc | IT | `-Dit.test=PagamentoFaturadoConcorrenciaIT#mesmaChave*` | ✅ PagamentoFaturadoConcorrenciaIT + PagamentoFaturadoServiceIT |
| EMIS-08 | Narrow repositories (reflection, copy of `AuditLogImutabilidadeTest`); `@Immutable` present; no PUT/PATCH/DELETE route on `documentos-fiscais`; an attempted dirty change + flush leaves the DB row unchanged; editing Cliente/ConfiguracaoFiscal after emission doesn't change the snapshot | unit + IT | `-Dtest=DocumentoFiscalImutabilidadeTest`, `-Dit.test=DocumentoFiscalRepositoryIT` | ✅ DocumentoFiscalImutabilidadeTest + DocumentoFiscalRepositoryIT (13 IT) |
| EMIS-09 | delete pagamento/cliente/processo/honorário with doc → 409 (Mockito); without doc → legacy behaviour; `repontarCliente` native UPDATE works on the `@Immutable` entity, touches only that tenant + client, leaves snapshot columns untouched; merge calls it before `delete(secondary)`; deleteCliente vs in-flight emission is serialized (holding-lock IT, `ConfiguracaoFiscalConcorrenciaIT` style) | unit + IT | `-Dtest=ResourceControllerDocumentoFiscalGuardasTest`, `-Dit.test=DocumentoFiscalRepositoryIT,GuardasDocumentoFiscalConcorrenciaIT` | ✅ ResourceControllerDocumentoFiscalGuardasTest + GuardasDocumentoFiscalConcorrenciaIT (6 IT) |
| EMIS-10 | Every `MetodoPagamento` has a code; unknown/blank metodo on the active path → 422; the code is snapshotted | unit | `-Dtest=MetodoPagamentoTest` | ✅ MetodoPagamentoTest |
| EMIS-11 | Listing: tenant A never sees B (same client NIF/ids), each filter null/non-null, pagination totals, detail of B's id from A → 404; `financeiro:view` gate via real `preAuthorize()` interceptor (copy `FaturacaoControllerAutorizacaoTest`) | IT + unit | `-Dit.test=DocumentoFiscalRepositoryIT`, `-Dtest=DocumentoFiscalControllerAutorizacaoTest` | ✅ DocumentoFiscalRepositoryIT + DocumentoFiscalControllerAutorizacaoTest + DocumentoFiscalControllerTest |
| EMIS-12 | Payments created before activation show `documentoFiscal: null`; no endpoint emits for an existing pagamento (reflection: no handler takes `pagamentoId` to emit) | unit | `-Dtest=ResourceControllerListaPagamentosTest` | ✅ ResourceControllerListaPagamentosTest |
| CFG-03 | Disabled branch: same repository calls, swallowed `DataAccessException`, 201 entity, service `registar` never called; source hash of the extracted method; `Pagamento.java` token ban | unit | `-Dtest=FaturacaoDesligadaPagamentoInalteradoTest` (evolved) | ✅ FaturacaoDesligadaPagamentoInalteradoTest (evolved, 134-08) |
| Schema | Script 134 = Hibernate schema (incl. defaults), idempotent, no CHECK, second `update` boot emits no fiscal DDL | IT | `-Dit.test=MigracaoFiscal134IT` | ✅ MigracaoFiscal134IT (6 IT) |
| Frontend | Schema: metodo required when active, taxa 0<t≤100, ≤2 decimals; key helper falls back without `randomUUID`; preview/response mappers; no rate literals; gating tokens | vitest + gate | `cd web && pnpm test && pnpm verify:documentos-fiscais` | ✅ financeiro.test.ts, idempotencia.test.ts, erros-emissao.test.ts, verify-documentos-fiscais.mjs |
| UI end-to-end | Dialog flow, 422 inline, list/detail, "Simulação" mark, column in pagamentos | manual / HUMAN-UAT | live run (backend+web) | ✅ 134-HUMAN-UAT.md (Playwright live run, 134-14) |


## Wave 0 Requirements

- [x] `CalculoFiscalTest` (vectors + property), `ValidacaoEmissaoTest`, `MetodoPagamentoTest`
- [x] `MigracaoFiscal134IT` (copy 133 harness incl. `CapturaMetadataHibernate`)
- [x] `DocumentoFiscalImutabilidadeTest`, `DocumentoFiscalRepositoryIT`
- [x] `PagamentoFaturadoServiceTest`, `PagamentoFaturadoServiceIT`, `PagamentoFaturadoConcorrenciaIT`, `GuardasDocumentoFiscalConcorrenciaIT`
- [x] Evolved `FaturacaoDesligadaPagamentoInalteradoTest` + new disabled-branch Mockito test
- [x] `DocumentoFiscalControllerTest` / `…AutorizacaoTest`
- [x] Update `ResourceControllerProveniencaPapelTest`/`ResourceControllerUploadDocumentoTest` constructors
- [x] `web/src/schemas/financeiro.test.ts` (extended), `web/src/lib/idempotencia.test.ts`, `web/scripts/verify-documentos-fiscais.mjs` + `package.json` script


---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Dialog flow, inline 422, list/detail, "Simulação" mark, "Documento fiscal" column | EMIS-02, EMIS-11, EMIS-12 | no RTL/jsdom in web | Live run backend+web (Playwright if available), record in HUMAN-UAT |

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved (automated) — 2026-10-04

Full gate run (134-14): backend `mvn -Dmaven.compiler.release=21 verify` → surefire 800 / 0 failures / 0 skipped, failsafe 94 / 0 / 0 (MigracaoFiscal134IT 6, DocumentoFiscalRepositoryIT 13, PagamentoFaturadoServiceIT 10, PagamentoFaturadoConcorrenciaIT 4, GuardasDocumentoFiscalConcorrenciaIT 6, all executed by Testcontainers); `spotbugs:check` green; web vitest 157/157, tsc clean, lint 0 errors, verify:faturacao OK, verify:documentos-fiscais OK, production build OK.
