-- Phase 137 (ENTR-01, ENTR-03, ENTR-04, PDF, Armazenamento, Email e Relatório): create the
-- mutable email-delivery satellite t_entrega_email_fiscal and the insert-only PDF record
-- t_documento_fiscal_pdf, with their unique keys and indexes
--
-- IMPORTANT: This is a REQUIRED manual production migration script for any install running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. It MUST be run manually (e.g. via psql or DBeaver)
-- against the database BEFORE booting a build of Phase 137. It requires
-- `133-create-fiscal-foundation-tables.sql`, `134-create-documento-fiscal-tables.sql`,
-- `135-add-nota-credito-documento-fiscal.sql` and `136-efatura-comunicacao.sql` to have run
-- first.
--
-- Why: `application.yml` runs `ddl-auto: update` and creates both tables by itself from the
-- entity mapping. A database running with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to
-- start without them (missing table t_entrega_email_fiscal / t_documento_fiscal_pdf) --
-- `validate` never alters schema. There is no automated migration runner in this repository
-- (no Flyway, no Liquibase). After 133 + 134 + 135 + 136 + this script the schema is
-- column-for-column what Hibernate generates (proven by MigracaoFiscal137IT).
--
-- Design:
-- * t_entrega_email_fiscal holds exactly one row per fiscal document
--   (uk_entrega_email_fiscal_documento). The row is created only after the document's
--   communication reached ACEITE_SIMULADO; the NAO_CONFIGURADO state (no SMTP configured) is
--   derived at read time and never stored.
-- * reenvios counts manual resends and identifies the failure episode for the
--   EMAIL_FISCAL_FALHOU notification. It is NOT NULL DEFAULT 0 -- the one deliberate exception
--   to the "no column defaults" convention, for the same reason as
--   t_comunicacao_fiscal.reprocessamentos (136). The entity declares the same default
--   (@ColumnDefault("0")), so the schemas stay identical.
-- * The cap of 5 automatic attempts per episode is enforced by the claim predicate in
--   FilaEntregaEmail, not by a CHECK. Deliberately NO constraint on the enum-typed columns
--   (PITFALLS P-15).
-- * destinatario is a delivery address (personal data); ultimo_erro is a short, user-safe
--   Portuguese message (never the SMTP server text nor a stack trace).
-- * t_documento_fiscal_pdf is insert-only: at most one row per document
--   (uk_documento_fiscal_pdf_documento) and a unique object key
--   (uk_documento_fiscal_pdf_object_key). The PDF bytes live in MinIO under the tenant prefix;
--   this table only records where they are and their SHA-256. The application never updates
--   nor deletes it.
-- * Deliberately no foreign key (bare UUID columns are the codebase convention, as in 134-136).
--
-- Backfill: none. Documents issued before Phase 137 simply have no delivery row and no PDF row;
-- their PDF is generated on demand at first download.
--
-- Idempotent: every statement uses IF NOT EXISTS, so this script is safe to run twice against
-- the same database.

CREATE TABLE IF NOT EXISTS t_entrega_email_fiscal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    documento_fiscal_id UUID NOT NULL,
    estado VARCHAR(32) NOT NULL,
    destinatario VARCHAR(254),
    tentativas INTEGER NOT NULL,
    proxima_tentativa_em TIMESTAMP(6) WITH TIME ZONE,
    lease_ate TIMESTAMP(6) WITH TIME ZONE,
    ultima_tentativa_em TIMESTAMP(6) WITH TIME ZONE,
    enviado_em TIMESTAMP(6) WITH TIME ZONE,
    ultimo_erro VARCHAR(500),
    ultimo_erro_codigo VARCHAR(64),
    reenvios INTEGER DEFAULT 0 NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    versao BIGINT NOT NULL,
    CONSTRAINT uk_entrega_email_fiscal_documento UNIQUE (documento_fiscal_id)
);

CREATE INDEX IF NOT EXISTS idx_entrega_email_fiscal_estado_proxima
    ON t_entrega_email_fiscal (estado, proxima_tentativa_em);

CREATE INDEX IF NOT EXISTS idx_entrega_email_fiscal_tenant_estado
    ON t_entrega_email_fiscal (tenant_id, estado);

CREATE TABLE IF NOT EXISTS t_documento_fiscal_pdf (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    documento_fiscal_id UUID NOT NULL,
    object_key VARCHAR(512) NOT NULL,
    sha256 VARCHAR(64) NOT NULL,
    tamanho_bytes BIGINT NOT NULL,
    versao_modelo VARCHAR(20) NOT NULL,
    gerado_em TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_documento_fiscal_pdf_documento UNIQUE (documento_fiscal_id),
    CONSTRAINT uk_documento_fiscal_pdf_object_key UNIQUE (object_key)
);
