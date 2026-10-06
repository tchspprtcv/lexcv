# Phase 137: PDF, Armazenamento, Email e Relatório — Context

**Gathered:** 2026-10-06
**Status:** Ready for planning
**Requirements:** ENTR-01..07, RELF-01, DFE-06 (PDF/email part)

<domain>
## Phase Boundary

Each Fatura-Recibo and Nota de Crédito gets a server-side PDF faithful to the stored document, downloadable with its XML. When the office turns automatic sending on, the client gets an email with the PDF and XML. Fiscal documents show up on the client's page for viewing and download only. The accountant gets a monthly CSV export.
Out of scope: platform-as-issuer / subscriptions (138), real eFatura connection, real SMTP in automated runs.
</domain>

<decisions>
## Implementation Decisions

### PDF (ENTR-01, DFE-06)
- Library: OpenHTMLtoPDF (HTML/CSS → PDF). Template rendered from the stored document snapshot only, never from live cliente/honorário data.
- No external resources: a custom resource/URI resolver refuses every non-classpath URI; fonts are embedded from the classpath (a font with PT accents, e.g. DejaVu Sans / Liberation, licence noted).
- Generated **once**, after the document is issued (outside the payment transaction, from the background flow), stored in MinIO via `StorageService` (key under the tenant prefix), and downloaded through a presigned URL. If generation failed or has not run yet, the download endpoint generates it on demand from the same snapshot and stores it (idempotent).
- Legal elements: emitente (firma, NIF, morada), adquirente (nome, NIF or "Consumidor final"), document type, série and número, IUD, issue date, lines, base, IVA or exemption reason, retenção, total; NC shows the referenced FR and the reason.
- Simulation mark: diagonal watermark "SIMULAÇÃO — SEM VALIDADE FISCAL" on every page plus a header band with the same text, while the mode is SIMULADO (always, in this milestone).

### Downloads (ENTR-02)
- PDF and XML: `financeiro:view`, tenant-scoped (404 on another tenant's document), every download audited (document id, kind, user).
- XML download serves the stored, XSD-validated XML from `t_documento_fiscal_xml` (or its MinIO copy if planning chooses one); the attachment name follows série/número.

### Email (ENTR-03..06)
- Reuses the Phase 136 outbox pattern: delivery-state columns on a dedicated table (or columns) with a scheduled job, SKIP LOCKED, lease, backoff, **max 5 attempts**.
- Enqueued only when the document is issued AND communicated (state "aceite em simulação"), only if `envio_email_automatico` is on, and never inside the payment registration transaction.
- Spring Mail (`spring-boot-starter-mail`); SMTP host/port/user/password/from/TLS only from environment variables, documented in `.env.example`, the three compose files and `deploy.yml`. Every SMTP value is optional. Without SMTP the app starts and issues normally, and the delivery state reads "Não configurado".
- Recipient: the client's email from the document snapshot or, failing that, the client record. A client with no email gets the state "Sem email do cliente" (no attempts).
- Email body in pt-PT. It carries the simulation mark in the subject and body, with the PDF and XML attached.
- States shown in the UI: Não configurado / Desligado / Sem email / Pendente / Enviado / Falhou (tentativas, último erro). Manual resend requires exact `financeiro:edit` (frontend gate matches), is audited, and resets the attempt count for a new episode.
- Persistent failure (attempts exhausted) triggers an in-app notification, **non-silenceable**, one per failure episode, to the office's financeiro managers (same recipient rule as COMUNICACAO_FISCAL_FALHOU).
- Tests use Mailpit (Testcontainers or a local container) / GreenMail. **Human checkpoint before any real SMTP** (`safety.always_confirm_external_services`).

### Ficha do cliente (ENTR-07)
- New tab "Documentos fiscais" on the client page: list (type, série/número, date, total, communication state, email state), with PDF/XML download only. No delete or edit actions; fiscal documents never enter the common `documentos` table and the generic delete endpoints cannot reach them.

### CSV mensal (RELF-01)
- In Financeiro, "Exportar mês" (month picker) → CSV; `financeiro:view`, tenant-scoped, audited.
- Format for Excel PT: `;` separator, UTF-8 with BOM, decimal comma, dates dd/mm/aaaa.
- Columns: data, tipo (FR/NC), série, número, IUD, cliente, NIF cliente, base, IVA, motivo de isenção, retenção, total, documento de origem (NC), estado da comunicação. NC values are negative. A final totals line follows.
- CSV/formula-injection protection only on free-text fields (lesson from v2.13 Phase 104); structured fields (NIF, IUD, numbers) are left alone.

### Claude's Discretion
- Exact table and column names, endpoint paths, split into plans, template structure, job cadence (around 30–60 s), font choice within licence constraints.
</decisions>

<code_context>
## Existing Code Insights
- No PDF or email infrastructure exists yet (pom has no mail/pdf deps).
- `ConfiguracaoFiscal.envioEmailAutomatico` (+ `envio_email_aceite_por/_em`) already exists from Phase 133; the toggle has had no effect until now.
- Outbox reference: `FilaComunicacaoFiscal`, `FiscalOutboxJob`, `ComunicacaoFiscalTransacoes`, `NotificacaoComunicacaoFiscal`, `ProcessadorComunicacaoFiscal` (Phase 136).
- Storage: `services/StorageService.java` (MinIO, presigned URLs).
- Document read side: `DocumentoFiscalController` / `DocumentoFiscalService`; web list/detail under documentos-fiscais; communication card/badge/banner components.
- Never touch `registarPagamentoLegado` (SHA guard test, CFG-03).
- Migrations: idempotent script in `backend/migrations/` + README line in the same commit.
</code_context>

<deferred>
## Deferred Ideas
- Real SMTP provider choice/credentials: after the human checkpoint.
- IN-02 (lost notification retry) and IN-03 (REJEITADO notification) from the 136 review: product decision, not in this phase unless trivially shared with the email notification.
</deferred>
