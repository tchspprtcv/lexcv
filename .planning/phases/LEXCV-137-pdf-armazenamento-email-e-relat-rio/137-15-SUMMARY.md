---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 15
subsystem: backend/fiscal downloads
tags: [download, presigned-url, audit, rbac, multi-tenancy, entr-07-guard]
requires: [137-10, 137-12]
provides:
  - GET /api/v1/documentos-fiscais/{id}/pdf -> {url, nomeFicheiro, expiresIn} (financeiro:view)
  - GET /api/v1/documentos-fiscais/{id}/xml -> attachment application/xml;charset=UTF-8 (financeiro:view)
  - DescargaDocumentoFiscalTransacoes {autorizarERegistarPdf, autorizarERegistarXml -> XmlDescarregavel}
  - DescargaDocumentoFiscalService {descarregarPdf -> DescargaPdf, descarregarXml}
  - AuditoriaFiscalService.registarDescarga / ACAO_DESCARREGAR "documento_fiscal_descarregar"
  - DocumentosFiscaisForaDosDocumentosComunsTest (ENTR-07 guard)
affects: [137-16 (web download buttons call these routes), 137-18 (AuditLog vocabulary next entry)]
tech-stack:
  added: []
  patterns: [tx-owner bean for authorise+audit, orchestrator without tx for PDF/MinIO]
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/DescargaDocumentoFiscalTransacoes.java
    - backend/src/main/java/com/lexcv/services/fiscal/DescargaDocumentoFiscalService.java
    - backend/src/test/java/com/lexcv/services/fiscal/DescargaDocumentoFiscalServiceTest.java
    - backend/src/test/java/com/lexcv/repositories/DocumentosFiscaisForaDosDocumentosComunsTest.java
  modified:
    - backend/src/main/java/com/lexcv/services/fiscal/AuditoriaFiscalService.java
    - backend/src/main/java/com/lexcv/models/AuditLog.java
    - backend/src/main/java/com/lexcv/controllers/DocumentoFiscalController.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerAutorizacaoTest.java
    - backend/src/test/java/com/lexcv/controllers/DocumentoFiscalControllerTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/AuditoriaFiscalServiceTest.java
decisions:
  - "Audit asymmetry (intended): the PDF audit row commits BEFORE garantirPdf/presign, so a PDF request that ends 503 (FICHEIRO_INDISPONIVEL / STORAGE_INDISPONIVEL / FALHA_PDF) still leaves one row recording the attempt. An XML request with no stored XML row answers 503 FICHEIRO_INDISPONIVEL inside the tx before the audit insert, so it leaves no row. Tests assert both."
  - "404 reuses ReenvioEmailFiscalService.CODIGO_NAO_ENCONTRADO / MSG_NAO_ENCONTRADO (DOCUMENTO_FISCAL_NAO_ENCONTRADO, 'Documento fiscal não encontrado.'), identical to the detail route; another tenant's id leaves no audit row"
  - "503 messages: FICHEIRO_INDISPONIVEL uses FicheiroFiscalIndisponivelException.MENSAGEM; STORAGE_INDISPONIVEL and FALHA_PDF use 'Não foi possível preparar o ficheiro neste momento. Aguarde um momento e tente novamente.'"
  - "Content-Disposition built with ContentDisposition.attachment().filename(nome) WITHOUT a charset: with UTF-8 Spring emits RFC 2047 '=?UTF-8?Q?...?=' + filename*, while NomesFicheiroFiscal names are ASCII [A-Za-z0-9._-] only, so the plain form is exact and safe (T-137-65)"
  - "expiresIn = MinioProperties.presignedUrlExpiry (seconds), the same value StorageService signs with"
metrics:
  duration: ~35min
  completed: 2026-10-07
  tasks: 2
  files: 10
---

# Phase 137 Plan 15: Audited PDF/XML downloads and ENTR-07 guard Summary

`DocumentoFiscalController` gains two routes, both gated on exact `financeiro:view`:
- `GET /documentos-fiscais/{id}/pdf` returns `{url, nomeFicheiro, expiresIn}`. The URL is presigned with `attachment; filename="FR-...pdf"`, and the PDF is generated on demand if missing.
- `GET /documentos-fiscais/{id}/xml` returns the stored XML bytes unchanged as `application/xml;charset=UTF-8` with `Content-Disposition: attachment; filename="FR-...xml"`.

**Audit and transactions.** `DescargaDocumentoFiscalTransacoes` checks the tenant and writes `documento_fiscal_descarregar` (detail `{autorNome, numeroFormatado, formato}`) in one short transaction. `DescargaDocumentoFiscalService` then runs `garantirPdf` and the presign outside any transaction.

**Errors.** Failures become 503 with fixed codes (FICHEIRO_INDISPONIVEL, STORAGE_INDISPONIVEL, FALHA_PDF), never exception text. Another tenant's id or a malformed id gets the detail route's 404.

**ENTR-07 guard.** A new guard test proves fiscal code never uses `Documento`, `DocumentoRepository` or `t_documento`. It also checks that fiscal keys live under `/documentos-fiscais/`, and that no controller exposes DELETE on a fiscal-document path.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | Descarga transactions + orchestrator + audit method | RED fbfdb40, GREEN 55146ee |
| 2 | Routes, RBAC cases, ENTR-07 guard | RED 3fd94a4, GREEN 748d9ed |

## Verification

- `DescargaDocumentoFiscalServiceTest`: **12/12**.
  - PDF: the audit comes before `garantirPdf`, which comes before the presign with the attachment name; `expiresIn` is 900 (configured).
  - Another tenant gets 404 with no audit, PDF or storage call.
  - FicheiroFiscalIndisponivel, Storage (in garantirPdf), Storage (in presign) and FalhaGeracaoPdf each give 503 with their code, and each still has exactly one PDF audit call.
  - PDF and presign run with no active transaction.
  - XML returns the UTF-8 bytes and the name. A missing XML row gives 503 and no audit; another tenant gives 404 and no audit.
  - The transaction methods are `@Transactional` (REQUIRED) and the orchestrator has none; the `XmlDescarregavel` bytes are copied.
- `AuditoriaFiscalServiceTest`: **23/23**. The `registar*` count is now 10, all MANDATORY. The `registarDescarga` detail keys are exactly autorNome/numeroFormatado/formato, with no "@".
- `DocumentoFiscalControllerAutorizacaoTest`: **45/45**.
  - Exact view calls the service with the principal's tenant and author.
  - edit, manage, ROLE_-prefixed and no authority are each refused on both routes.
  - A malformed id gives 404 without calling the service.
  - Response shapes: PDF map; XML content type, disposition and body.
- `DocumentosFiscaisForaDosDocumentosComunsTest` **4/4**, `DocumentoFiscalImutabilidadeTest` **14/14**, `FaturacaoDesligadaPagamentoInalteradoTest` **5/5** (CFG-03), `DocumentoFiscalControllerTest` **33/33**.
- ResourceController is unchanged (`git diff --quiet`), `@Transactional` is absent from DescargaDocumentoFiscalService, and `compile spotbugs:check` is clean.

## Deviations from Plan

1. **[Rule 1 - Bug] Content-Disposition without a charset.** The interface note suggested `filename(nome, UTF_8)`. That produces `filename="=?UTF-8?Q?FR-...xml?="` and does not meet the behaviour requirement (`attachment; filename="FR-...xml"`). Names are ASCII-only, so the plain `filename(nome)` is used.
2. **[Rule 3 - Blocking] `DocumentoFiscalControllerTest` pinned 8 handlers.** Raised to 10 (renamed `exatamenteDezHandlersSemRotasQueAlterem`), with both new routes asserted. Its constructor call also gets the new service mock.
3. **Test-only fix in GREEN 55146ee.** The combined storage-failure test re-stubbed a throwing answer with `when(...)`. It was split into two tests, one using `doThrow`.
4. **Extra RBAC case.** `ROLE_financeiro:view` is refused too, beyond the plan's three cases.

## Threat Flags

None. T-137-60..65 are mitigated as planned. The presigned URL is a bearer link valid for the configured expiry, the same as common documents.

## Self-Check: PASSED
