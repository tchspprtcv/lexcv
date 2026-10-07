# Phase 137: PDF, Armazenamento, Email e Relatório Mensal — Human UAT & Gate Record

## 1. Automated Gate Record

### Backend Test Suite (Surefire & Failsafe)
- **Total Tests Run**: 209
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Outcome**: BUILD SUCCESS

#### Fiscal Integration Tests (Failsafe) Executed:
- `MigracaoFiscal133IT`: 7 passed
- `MigracaoFiscal134IT`: 8 passed
- `MigracaoFiscal135IT`: 8 passed
- `MigracaoFiscal136IT`: 6 passed
- `MigracaoFiscal137IT`: 6 passed
- `DocumentoFiscalRepositoryIT`: 11 passed
- `ConfiguracaoFiscalConcorrenciaIT`: 4 passed
- `FilaComunicacaoFiscalIT`: 6 passed
- `FilaEntregaEmailIT`: 6 passed
- `NotaCreditoConcorrenciaIT`: 4 passed
- `NotaCreditoServiceIT`: 10 passed
- `NumeracaoServiceConcorrenciaIT`: 4 passed
- `PagamentoFaturadoConcorrenciaIT`: 4 passed
- `PagamentoFaturadoServiceIT`: 14 passed
- `PdfDocumentoFiscalServiceIT`: 6 passed
- `ReenviarEmailIT`: 8 passed
- `RelatorioMensalFiscalServiceIT`: 6 passed
- `ReprocessarComunicacaoIT`: 8 passed
- `FiscalOutboxJobIT`: 4 passed
- `FiscalOutboxJobFalhasForcadasIT`: 3 passed

### Backend Static Analysis (SpotBugs)
- **Goal**: `mvn compile spotbugs:check`
- **Result**: 0 bugs found, BUILD SUCCESS.

### Frontend Test Suite (Vitest)
- **Total Test Files**: 15 passed (15)
- **Total Tests**: 353 passed (353)
- **Duration**: ~9.5s

### Frontend Static Verification Gates
- `pnpm verify:faturacao`: **PASS** (gating, copy, read-only series, no tax constants)
- `pnpm verify:documentos-fiscais`: **PASS** (gating financeiro:view, immutable detail, communication badge & cards)
- `pnpm verify:entrega-fiscal`: **PASS** (gating financeiro:edit/view, copy vinculativa, neutral badges, read-only client tab, backend CSV)

### Frontend Production Build
- `pnpm build`: **PASS** (28 static/dynamic routes compiled cleanly).

---

## 2. End-to-End Live Verification Record

| Step | Requirement | Description | Status | Evidence |
|------|-------------|-------------|--------|----------|
| 1 | ENTR-06 | Startup without SMTP, migration 137 idempotence, initial delivery state `NAO_CONFIGURADO`, settings SMTP notice | **PASS** | Migration applied cleanly; when `SMTP_HOST` is unset, `t_entrega_email_fiscal` records `NAO_CONFIGURADO` with 0 attempts; settings card displays neutral notice under switch. |
| 2 | ENTR-01, DFE-06 | Server-side PDF generation, presigned URL download, legal elements, watermark "SIMULAÇÃO — SEM VALIDADE FISCAL", embedded DejaVu fonts only | **PASS** | `GET /api/v1/documentos-fiscais/{id}/pdf` returns presigned MinIO URL; PDF includes header band and diagonal watermark "SIMULAÇÃO — SEM VALIDADE FISCAL", DejaVu fonts embedded, no external assets; idempotent storage row in `t_documento_fiscal_pdf`. |
| 3 | ENTR-02 | XML attachment download, Content-Disposition header, SHA-256 match, audited download events, RBAC isolation | **PASS** | `GET /api/v1/documentos-fiscais/{id}/xml` downloads byte-identical XML matching `xml_sha256`; audit log registers `documento_fiscal_descarregar` with format PDF/XML; 403 on users without `financeiro:view`, 404 on other tenants. |
| 4 | ENTR-03, DFE-06 | Email delivery via outbox with PDF and XML attachments, simulation marks, Reply-To, state `ENVIADO` | **PASS** | Outbox job processes delivery to client with subject `[SIMULAÇÃO — SEM VALIDADE FISCAL] ...`; plain and HTML bodies contain simulation disclaimer; attachments `.pdf` and `.xml` match stored snapshots; detail reflects `ENVIADO`. Payment POST latency unaffected. |
| 5 | ENTR-01, ENTR-03 | Credit note PDF and email delivery | **PASS** | NC PDF displays "Nota de Crédito", "Corrige a Fatura-Recibo {número}", "Total a crédito"; email body explicitly notes correction of original FR; attachments include NC PDF and XML. |
| 6 | ENTR-05 | Delivery failure handling, backoff exhaustion, `EMAIL_FISCAL_FALHOU` notification | **PASS** | Persistent failure marks status `FALHOU` with sanitized Portuguese error message; creates non-silenceable notification for `financeiro:manage` and `financeiro:edit` users; view-only users receive no alert. |
| 7 | ENTR-04, ENTR-05 | Manual resend with exact `financeiro:edit` gate and dialog guards | **PASS** | `POST /documentos-fiscais/{id}/email/reenviar` requires exact `financeiro:edit` (403 for manage-only or view-only); resets attempts to 0 and re-enters `PENDENTE`; 409 on concurrent send; 422 if automatic toggle is OFF. |
| 8 | ENTR-03, ENTR-04 | Automatic email toggle OFF -> `DESLIGADO`; Missing email -> `SEM_EMAIL`; Update email & "Enviar email" -> `ENVIADO` | **PASS** | FR issued with toggle OFF sets `DESLIGADO` without enqueuing; client without email sets `SEM_EMAIL`; once email is added to client, "Enviar email" dispatches to the new address. |
| 9 | ENTR-07 | Client page tab "Documentos fiscais", read/download only, isolated from generic documents | **PASS** | Client page tab renders `ModoSimuladoBanner`, link to filtered list, and paginated table with PDF/XML download buttons; no edit/delete/upload actions; fiscal records omitted from generic documents; `DELETE /api/v1/documentos/{fiscalId}` returns 404. |
| 10 | RELF-01 | Monthly accountant CSV export | **PASS** | `GET /api/v1/documentos-fiscais/exportacao-mensal?mes=AAAA-MM` exports CSV starting with UTF-8 BOM (`\uFEFF`), semicolon separator, decimal comma, negative NC values, and summary `Totais` row; formula injection sanitized; future month returns 422 `MES_INVALIDO`. |
| 11 | DFE-06 | Deceptive wording scan | **PASS** | No forbidden claims ("Autorizado", "Aprovado", "Validado pela DNRE", "Entregue", "Recebido", "Lido") in fiscal documents, emails, UI surfaces, or PDFs. |
| 12 | — | Clean teardown | **PASS** | No lingering environment files, scratch files, or test containers. |

---

## 3. Decisão SMTP real (safety.always_confirm_external_services)

- **Opção Escolhida**: `close-no-real-smtp` (Fecho da fase com envio por email validado localmente via catcher/testes, mantendo a instalação pronta para configuração de SMTP real por variáveis de ambiente em produção).
- **Estado de DFE-06**: **Concluído** (Faturação simulada, comunicação simulada, PDF/XML com marcas de simulação e entrega de email concluídos com segurança).
