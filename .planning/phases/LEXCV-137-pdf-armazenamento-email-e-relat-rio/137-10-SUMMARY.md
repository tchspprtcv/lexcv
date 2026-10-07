---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 10
subsystem: backend/fiscal pdf storage
tags: [pdf, minio, presigned-url, idempotency, outbox-free, multi-tenancy]
requires: [137-02, 137-06]
provides:
  - StorageService.uploadBytes(key, bytes, contentType) (fiscal key-shape guard)
  - StorageService.presignedDownloadUrl(key, nomeFicheiroAnexo) (attachment Content-Disposition + content type)
  - StorageService.lerBytes(key)
  - NomesFicheiroFiscal {base, pdf, xml, csv}
  - PdfDocumentoFiscalTransacoes {pdfExistente, carregarDados -> DadosParaPdf, registar}
  - PdfDocumentoFiscalService {garantirPdf -> Optional<PdfArmazenado>, garantirPdfSilencioso, lerPdf; VERSAO_MODELO "137.1"}
  - FicheiroFiscalIndisponivelException (CODIGO FICHEIRO_INDISPONIVEL, fixed message)
affects: [137-14 (lerPdf for email), 137-15 (download endpoint, 503 mapping, presign), 137-17 (garantirPdfSilencioso hook), CSV plan (NomesFicheiroFiscal.csv)]
tech-stack:
  added: []
  patterns: [render/upload outside tx + short tx insert-if-absent, loser cleans its own object (row id in key)]
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/NomesFicheiroFiscal.java
    - backend/src/main/java/com/lexcv/services/fiscal/PdfDocumentoFiscalTransacoes.java
    - backend/src/main/java/com/lexcv/services/fiscal/PdfDocumentoFiscalService.java
    - backend/src/main/java/com/lexcv/services/fiscal/FicheiroFiscalIndisponivelException.java
    - backend/src/test/java/com/lexcv/services/StorageServiceFiscalTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/PdfDocumentoFiscalServiceTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/PdfDocumentoFiscalServiceIT.java
  modified:
    - backend/src/main/java/com/lexcv/services/StorageService.java
decisions:
  - "Object key: <tenantId>/documentos-fiscais/<documentoId>/pdf-<pdfRowId>.pdf; uploadBytes refuses any other shape (UUID segments, single [A-Za-z0-9._-]{1,200} file name)"
  - "presignedDownloadUrl(key, nome) refuses a name outside [A-Za-z0-9._-]{1,200} (no header injection); sets application/pdf for .pdf and application/xml for .xml"
  - "carregarDados returns the document, lines, own IUD, NC origin (same tenant) and the existing PDF row in one read-only tx, so garantirPdf does one read when the PDF already exists"
  - "Concurrency: the service compares the registered row id with its own; on mismatch it returns the winner and deletes its own object best-effort. If registar returns no row at all (should not happen), it cleans up and throws FicheiroFiscalIndisponivelException"
  - "lerPdf on a document not in the tenant throws IllegalArgumentException with a fixed message"
  - "NomesFicheiroFiscal replaces each unsafe character with '-' one-for-one (no collapsing); a blank number falls back to 'documento-fiscal'"
metrics:
  duration: ~35min
  completed: 2026-10-07
  tasks: 3
  files: 8
---

# Phase 137 Plan 10: Fiscal PDF storage and generate-once service Summary

Each FR/NC gets at most one stored PDF. `PdfDocumentoFiscalService.garantirPdf` works like this:
- If a row already exists, it returns it without rendering or uploading.
- Before the document has its XML/IUD, it throws `FicheiroFiscalIndisponivelException`.
- Otherwise it renders from the stored snapshot and the IUD, outside any transaction, uploads to the tenant-prefixed key, and registers the row in a short transaction with sha256, size and `versao_modelo` "137.1".

Under concurrency, the loser gets the winner's row and deletes only its own object. `StorageService` gained three additive methods (guarded byte upload, read, presigned URL with an attachment name). `NomesFicheiroFiscal` derives the download names from série/número.

## Tasks

| # | Task | Commits |
|---|------|---------|
| 1 | StorageService additive methods + NomesFicheiroFiscal | RED 49783dd, GREEN 7a04795 |
| 2 | PdfDocumentoFiscalTransacoes + PdfDocumentoFiscalService | RED 2911d31, GREEN 99ee4cb |
| 3 | PdfDocumentoFiscalServiceIT | 488b37b |

## Verification

- `StorageServiceFiscalTest`: **26/26**.
  - PutObject key, type, length and body checked; SdkException becomes StorageUnavailableException.
  - 10 malformed keys are refused, including null; the attempts include `..`, an extra folder, a non-UUID segment, a space and a leading slash.
  - The presigned URL carries `attachment; filename="FR-2026A-000123.pdf"`, application/pdf and the configured expiry; .xml is not forced to pdf.
  - 5 unsafe attachment names are refused, including quote, CR/LF and `;`.
  - `lerBytes` returns the bytes, and an SdkException maps to StorageUnavailableException.
  - Generic `upload`, `presignedDownloadUrl(String)` and `delete` behave as before.
  - The `NomesFicheiroFiscal` cases from the plan pass.
- `ResourceControllerUploadDocumentoTest` 2/2 (unchanged).
- `PdfDocumentoFiscalServiceTest`: **14/14**. Covers every behaviour in the plan:
  - reuse, generate-once, renderer input (IUD and snapshot);
  - concurrent winner with own-object delete, and a swallowed delete failure;
  - missing XML, other tenant, upload failure without registration;
  - the silent variant across 4 failure kinds;
  - `lerPdf` fresh, stored and unknown;
  - no active transaction inside the renderer/storage answers, and no `@Transactional` on the service.
- `PdfDocumentoFiscalServiceIT` (PostgreSQL 16 Testcontainers, real renderer, mocked StorageService): **3/3, 0 skipped**.
  - A latch forces both threads into the upload before either registers. They end with 1 row and the same objectKey, and at most 1 delete, of the loser's own key.
  - No transaction is active during upload.
  - The stored sha256 and size match the uploaded bytes, which start with `%PDF-`.
  - A second call does not upload.
  - Another tenant gets empty and nothing is created.
- `compile spotbugs:check`: clean (0 bugs, no new exclusion).

## Deviations from Plan

1. **Extra file `FicheiroFiscalIndisponivelException.java`.** The plan names the exception but does not list its file.
2. **[Rule 3 - Blocking] SpotBugs EI_EXPOSE_REP2 on the hand-written constructor** that stores `StorageService`. The fix was Lombok `@RequiredArgsConstructor`, as `StorageService` and `ResourceController` already use. The constructor signature is unchanged, so the unit test still calls it directly.
3. `DadosParaPdf` also carries the existing PDF row, and `garantirPdf` uses `carregarDados` as its single first read. `pdfExistente` is still exposed as planned. The behaviour is the same; this just saves a query.
4. The IT inserts the FR, line and XML rows with JDBC directly, like the `FilaComunicacaoFiscalIT` scaffold. `FixturaEmissaoFiscal` has no document/XML insert helper.
5. `PdfArmazenado` overrides equals/hashCode/toString: equals compares the byte array by content, and toString leaves out the bytes.

## Self-Check: PASSED
