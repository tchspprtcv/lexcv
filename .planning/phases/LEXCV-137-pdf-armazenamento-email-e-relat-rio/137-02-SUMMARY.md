---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 02
subsystem: backend/fiscal persistence
tags: [jpa, immutability, multi-tenancy, email-delivery, pdf]
requires: []
provides:
  - EstadoEntregaEmail + EstadoEntregaEmailConverter
  - EntregaEmailFiscal (t_entrega_email_fiscal, MAX_TENTATIVAS = 5)
  - DocumentoFiscalPdf (t_documento_fiscal_pdf, @Immutable)
  - EntregaEmailFiscalRepository {findByTenantIdAndDocumentoFiscalId, findByTenantIdAndDocumentoFiscalIdIn}
  - DocumentoFiscalPdfRepository {inserirSeAusente, findByTenantIdAndDocumentoFiscalId}
  - DocumentoFiscalRepository.findByTenantIdAndDataEmissaoBetweenOrderByDataEmissaoAscAnoAscNumeroAsc
  - DocumentoFiscalXmlRepository.findByTenantIdAndDocumentoFiscalIdIn
affects: [137-04 (SQL migration), 137-05, 137-06, 137-07 (FilaEntregaEmail), 137-14, 137-17, CSV plan]
tech-stack:
  added: []
  patterns: [mutable satellite with @Version (ComunicacaoFiscal analog), insert-only @Immutable satellite with native ON CONFLICT DO NOTHING (DocumentoFiscalXml analog)]
key-files:
  created:
    - backend/src/main/java/com/lexcv/models/EstadoEntregaEmail.java
    - backend/src/main/java/com/lexcv/models/EstadoEntregaEmailConverter.java
    - backend/src/main/java/com/lexcv/models/EntregaEmailFiscal.java
    - backend/src/main/java/com/lexcv/models/DocumentoFiscalPdf.java
    - backend/src/main/java/com/lexcv/repositories/EntregaEmailFiscalRepository.java
    - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalPdfRepository.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalRepository.java
    - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalXmlRepository.java
    - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalImutabilidadeTest.java
decisions:
  - "NAO_CONFIGURADO is never persisted; EstadoEntregaEmail has exactly DESLIGADO, SEM_EMAIL, PENDENTE, ENVIADO, FALHOU"
  - "Unknown estado in the DB -> IllegalStateException with a fixed message (value not echoed)"
  - "terminal() == (this != PENDENTE); reenviavelPorEstado() for FALHOU, ENVIADO, SEM_EMAIL only"
metrics:
  duration: ~15min
  completed: 2026-10-07
  tasks: 2
  files: 9
---

# Phase 137 Plan 02: Email-delivery and PDF persistence model Summary

This plan adds two satellites to the immutable `DocumentoFiscal`, both keyed per document and scoped by tenant:
- **`t_entrega_email_fiscal`**: the mutable email-delivery row, with a version, attempts, a lease and a resend episode counter.
- **`t_documento_fiscal_pdf`**: the stored-PDF row, insert-only and `@Immutable`.

It also adds two narrow repositories and the two CSV finders, all pinned in `DocumentoFiscalImutabilidadeTest`.

## Tasks

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Enum, converter, two entities | 9fd3736 | models/EstadoEntregaEmail*, EntregaEmailFiscal, DocumentoFiscalPdf |
| 2 (RED) | Guard test additions | 542ff9c | DocumentoFiscalImutabilidadeTest |
| 2 (GREEN) | Repositories + finders | c2952b5 | 4 repositories |

## Details

- **`EntregaEmailFiscal`:** unique key `uk_entrega_email_fiscal_documento` and indexes `idx_entrega_email_fiscal_estado_proxima` and `idx_entrega_email_fiscal_tenant_estado`, all declared in `@Table`. `reenvios` has `@ColumnDefault("0")`. `tenant_id`, `documento_fiscal_id` and `created_at` are `updatable=false`. The Javadoc records that the row is created only after `ACEITE_SIMULADO` (137-17), that writes go only through `FilaEntregaEmail` native SQL (137-07), and that `destinatario` is personal data and is never logged or audited.
- **`DocumentoFiscalPdf`:** same shape as `DocumentoFiscalXml`: protected no-args constructor, getters only, every column `updatable=false`. It has two unique keys: per document and per `object_key`.
- **Guard test changes:**
  - Both new repositories are added to `REPOSITORIOS_FISCAIS` with a Phase 137 comment.
  - The pinned sets gain the month finder and the batched XML finder.
  - New Test 5d covers PDF insert-only: SQL prefix `INSERT INTO t_documento_fiscal_pdf`, ends in `ON CONFLICT DO NOTHING`, no `do update`, no `delete`.
  - New Test 5e covers the email repository: exactly two finders, no `@Modifying`.
  - `DocumentoFiscalPdf` is added to Test 7, and both repositories to Test 8.
  - The XML insert-only checks moved into a shared `assertInsertOnly` helper with the same assertions. Nothing was relaxed.

## Verification

- `mvn -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check`: clean (after both tasks).
- Task 1 check: the email table name does not start with `t_documento_fiscal`.
- `DocumentoFiscalImutabilidadeTest`: RED was a compile failure (missing repositories). GREEN is **14/14**.
- Extra regression check (not required by the plan): `MigracaoFiscal134IT` **6/6** and `MigracaoFiscal136IT` **8/8** pass with the new entities mapped. The second-boot no-DDL check is unaffected.

## Deviations from Plan

None in substance. One minor refactor: the XML insert-only assertions were moved into a helper so the new PDF test reuses them. The assertions are identical.

## TDD Gate Compliance

Task 2: `test(137-02)` commit 542ff9c (RED, compile failure), then `feat(137-02)` commit c2952b5 (GREEN).

## Known Stubs

None. The SQL migration for both tables is planned in 137-04. Until then `ddl-auto: update` creates the tables and indexes from the annotations.

## Self-Check: PASSED

All 9 files are present; commits 9fd3736, 542ff9c and c2952b5 are in `git log`.
