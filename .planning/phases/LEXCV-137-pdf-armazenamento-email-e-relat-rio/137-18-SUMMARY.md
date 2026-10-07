---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 18
subsystem: backend/fiscal monthly report
tags: [csv, export, audit, rbac, tenant-isolation, formula-injection]
requires: [137-02, 137-05, 137-10, 137-15]
provides:
  - RelatorioMensalFiscalService.exportar(tenantId, autor, YearMonth) -> CsvMensal(conteudo, nomeFicheiro, numeroDocumentos)
  - GET /api/v1/documentos-fiscais/exportacao-mensal?mes=AAAA-MM (financeiro:view)
  - AuditoriaFiscalService.registarExportacaoMensal (MANDATORY), ACAO_EXPORTAR_MES, ENTIDADE_TIPO_RELATORIO
affects: [137-20 (web "Exportar mês" dialog calls this route)]
tech-stack:
  added: []
  patterns: [batch satellite reads by tenant (DocumentoFiscalService.listar), attachment ResponseEntity<byte[]> (137-15 XML download), defensive-copy byte[] record]
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/RelatorioMensalFiscalService.java
    - backend/src/test/java/com/lexcv/services/fiscal/RelatorioMensalFiscalServiceIT.java
  modified:
    - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
    - backend/src/main/java/com/lexcv/models/AuditLog.java
    - backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerAutorizacaoTest.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
decisions:
  - "Route is the plan's GET /api/v1/documentos-fiscais/exportacao-mensal?mes=AAAA-MM; the UI-SPEC path was only an example ('e.g.')"
  - "Month validation lives in the controller: a strict \\d{4}-\\d{2} regex, then YearMonth.parse, then 'not after YearMonth.now(clock in Atlantic/Cape_Verde)'. Missing, blank, malformed (2026-13, 2026-9, 2026-09-01, abc) or future answer 422 {message, code: MES_INVALIDO} without calling the service"
  - "Content-Disposition uses ContentDisposition.attachment().filename(name) with no charset, the same as the 137-15 XML download. The name is ASCII, so the header is exactly attachment; filename=\"documentos-fiscais-simulacao-AAAA-MM.csv\""
  - "Motivo de isenção = '{código} — {descrição}' from the emitente exemption snapshot only when emitenteRegimeIva == ISENTO. It is just the code when there is no description, and empty otherwise"
  - "Row order is the finder's (data, ano, número), then a stable in-memory tie-break on emitidoEm, tipo (FR before NC) and número. FR and NC have separate series, so their numbers tie (see deviation 2)"
  - "The Totais line is built from plain cells (no textoLivre) and has 14 cells. An empty month gives exactly 'Totais;;;;;;;0,00;0,00;;0,00;0,00;;'"
  - "Audit detail is {autorNome, mes, numeroDocumentos}; entidadeId = AAAA-MM. Nothing about clients is stored"
metrics:
  duration: ~45min
  completed: 2026-10-07
  tasks: 2
  files: 8
---

# Phase 137 Plan 18: Monthly fiscal CSV export Summary

**What it does.** The accountant's monthly CSV comes from `GET /api/v1/documentos-fiscais/exportacao-mensal?mes=AAAA-MM`, gated on exact `financeiro:view`. `RelatorioMensalFiscalService.exportar` (`@Transactional`, read-write because of the audit) builds it in memory:
1. Reads the tenant's documents for the month with the tenant-first finder.
2. Batch-reads IUDs, communication states and origin FR numbers, all by tenant.
3. Writes the 14 UI-SPEC columns with `CsvFiscal`: UTF-8 BOM, `;`, CRLF including the last line, decimal comma, dd/mm/aaaa.
   - Credit notes are negated in Base, IVA, Retenção and Total.
   - A "Totais" line carries the signed sums.
   - The formula guard (`textoLivre`) applies only to Cliente and Motivo de isenção.
   - A blank NIF shows "Consumidor final".
   - The communication state uses the UI labels.
4. Records one `documento_fiscal_exportar_mes` audit row per export.

**Response.** `text/csv;charset=UTF-8` with `attachment; filename="documentos-fiscais-simulacao-AAAA-MM.csv"`.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | Service + audit method + route + RBAC | RED d0907c0, GREEN 0a05c84 |
| 2 | RelatorioMensalFiscalServiceIT on PostgreSQL | 721c18d (IT), ca890d6 (service fix: deterministic order) |

## Verification

- `DocumentoFiscalControllerAutorizacaoTest`: **61/61**. New cases:
  - Exact view calls the service with the principal's tenant and author.
  - Edit-only, manage-only, `ROLE_` prefix and no authority are refused, and the service is never touched.
  - 9 parameterised invalid months, including null, give 422 `MES_INVALIDO`. "2026-11" counts as future at 2026-11-01T00:30Z, which is still October in Cape Verde.
  - The current month " 2026-10 " (trimmed) is accepted.
  - Attachment content type, disposition and bytes are checked.
- `DocumentoFiscalControllerTest`: **33/33**. The handler gate is now 11 and includes the new route and its `financeiro:view` gate.
- `AuditoriaFiscalServiceTest`: **26/26**. Three new tests: detail keys, no "@", entidade relatorio_fiscal / 2026-09; zero documents; MANDATORY. The `registar*` count gate is now 11.
- `DocumentoFiscalImutabilidadeTest` 14/14 and `FaturacaoDesligadaPagamentoInalteradoTest` 5/5.
- `RelatorioMensalFiscalServiceIT`: **6/6, 0 skipped**, on Testcontainers PostgreSQL 16. It covers:
  - A real FR (with retention) and a real partial NC: 4 lines, BOM bytes, exact header.
  - FR cells equal to the stored totals, with the IUD from the XML row.
  - NC cells negative and not apostrophe-prefixed, with the origin number; the Totais row holds signed sums with the other cells empty.
  - Communication labels "Aceite (simulação)" and "Pendente".
  - The `=HYPERLINK("x")` client is quoted and apostrophe-prefixed, and the NIF is untouched.
  - The ISENTO motivo comes from the snapshot.
  - October, August and other-tenant documents are absent.
  - An empty month gives the exact zero-totals line.
  - One audit row per export, with numeroDocumentos 2.
- `mvn -DskipTests compile spotbugs:check`: clean.

## Deviations from Plan

1. **[Rule 1 - Bug, SpotBugs] Defensive copy in `CsvMensal`.** The `byte[]` record component triggered EI_EXPOSE_REP/EI_EXPOSE_REP2. The constructor and accessor now clone, and equals/hashCode/toString are content-based. This is the same pattern as `DescargaDocumentoFiscalTransacoes.XmlDescarregavel`. Included in 0a05c84.
2. **[Rule 1 - Bug] Row order was not deterministic.** FR and NC have separate series, so "FR 1" and "NC 1" from the same day tie on the finder's `(data_emissao, ano, numero)`, and PostgreSQL can return them in either order. The service now applies a stable sort on `dataEmissao`, `emitidoEm`, `tipo` (FR before NC), `ano`, `numero`. The repository finder is unchanged. Commit ca890d6.
3. **Expected structural gate updates.** These are not behaviour changes:
   - `DocumentoFiscalControllerTest.exatamenteDezHandlers…` was renamed `exatamenteOnzeHandlers…` and now expects 11.
   - `AuditoriaFiscalServiceTest.todosOsMetodosRegistarSaoMandatory` now expects 11.
   - `DocumentoFiscalControllerTest` passes the two new constructor arguments: the `RelatorioMensalFiscalService` mock and `Clock`.
4. The IT does not cover "Consumidor final", because `adquirente_nif` is NOT NULL in the schema. The branch is a one-line blank check in `nifCliente`.

## Threat Flags

None. T-137-75, -76, -77, -78 and -80 are mitigated as planned. T-137-79 is accepted.

## Self-Check: PASSED
