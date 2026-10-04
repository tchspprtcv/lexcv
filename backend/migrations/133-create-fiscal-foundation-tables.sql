-- Phase 133 (CFG-01..06, Fundação Fiscal): create t_configuracao_fiscal, t_parametro_fiscal and
-- t_serie_fiscal
--
-- IMPORTANT: This is a REQUIRED manual production migration script for any install running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. It MUST be run manually (e.g. via psql or DBeaver)
-- against the database BEFORE or DURING deploying the code change that introduces the
-- `ConfiguracaoFiscal`, `ParametroFiscal` and `SerieFiscal` entities
-- (backend/src/main/java/com/lexcv/models/).
--
-- Why: `application.yml` runs `ddl-auto: update` and creates these three tables by itself on a
-- fresh/dev database from the entity mapping. A database running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to start without them -- `validate` never
-- creates or alters schema, it only checks the existing schema is compatible at startup. There
-- is no automated migration runner in this repository (no Flyway, no Liquibase). The schema
-- below is column-for-column what Hibernate generates (proven by MigracaoFiscal133IT).
--
-- t_tenant is NOT altered: fiscal data lives in its own 1:1 table (tenant_id UNIQUE).
--
-- Deliberately no foreign key to t_tenant: codebase convention is bare UUID tenant_id columns
-- (no navigable @ManyToOne).
--
-- Deliberately NO constraint restricting the values of the enum-typed columns (regime_iva,
-- tipo_documento, ambiente): they are plain VARCHAR(32), so a future enum value -- e.g. a real
-- eFatura ambiente in Phase 136 -- needs no DROP CONSTRAINT.
--
-- Deliberately NO backfill/INSERT: DatabaseSeeder upserts t_parametro_fiscal (IVA_TAXA_NORMAL,
-- RETENCAO_SUGERIDA) unconditionally on the very next boot; series rows are created on demand by
-- the application (INSERT ... ON CONFLICT DO NOTHING).
--
-- Idempotent: every statement below guards its own creation (IF NOT EXISTS), so this script is safe to run
-- twice against the same database.

CREATE TABLE IF NOT EXISTS t_configuracao_fiscal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    nif VARCHAR(9),
    firma VARCHAR(200),
    morada VARCHAR(100),
    localidade VARCHAR(100),
    pais_codigo VARCHAR(2),
    email_contacto VARCHAR(254),
    telefone_contacto VARCHAR(32),
    regime_iva VARCHAR(32),
    motivo_isencao_codigo VARCHAR(2),
    ativa BOOLEAN NOT NULL DEFAULT FALSE,
    envio_email_automatico BOOLEAN NOT NULL DEFAULT FALSE,
    envio_email_aceite_por UUID,
    envio_email_aceite_em TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    updated_by UUID,
    CONSTRAINT uk_configuracao_fiscal_tenant UNIQUE (tenant_id)
);

CREATE TABLE IF NOT EXISTS t_parametro_fiscal (
    id UUID PRIMARY KEY,
    codigo VARCHAR(64) NOT NULL,
    valor NUMERIC(9,4) NOT NULL,
    vigente_desde DATE NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_parametro_fiscal_codigo_vigencia UNIQUE (codigo, vigente_desde)
);

CREATE TABLE IF NOT EXISTS t_serie_fiscal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    tipo_documento VARCHAR(32) NOT NULL,
    ano INTEGER NOT NULL,
    ambiente VARCHAR(32) NOT NULL,
    codigo VARCHAR(20) NOT NULL,
    led_codigo INTEGER,
    ultimo_numero BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_serie_fiscal UNIQUE (tenant_id, tipo_documento, ano, ambiente)
);
