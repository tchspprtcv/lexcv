-- Phase 136 (DFE-02, DFE-04, DFE-06, Formato eFatura e Adaptador Simulado): add the outbox
-- columns, the CHECK ck_comunicacao_fiscal_autorizado_producao and the index
-- idx_comunicacao_fiscal_estado_proxima to t_comunicacao_fiscal, and create the insert-only XML
-- satellite t_documento_fiscal_xml
--
-- IMPORTANT: This is a REQUIRED manual production migration script for any install running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. It MUST be run manually (e.g. via psql or DBeaver)
-- against the database BEFORE booting a build of Phase 136 on a database that already holds
-- fiscal documents. It requires `134-create-documento-fiscal-tables.sql` and
-- `135-add-nota-credito-documento-fiscal.sql` to have run first (t_comunicacao_fiscal must exist).
--
-- Why: `application.yml` runs `ddl-auto: update` and adds the new columns, the index and the new
-- table by itself from the entity mapping. A database running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to start without them (missing column
-- lease_ate / ... / missing table t_documento_fiscal_xml) -- `validate` never alters schema.
-- ALSO on `update`: Hibernate only creates a CHECK constraint together with its table, never on
-- an existing table, so on any database where t_comunicacao_fiscal already existed (Phase 134 or
-- 135 deploys) ck_comunicacao_fiscal_autorizado_producao exists ONLY if this script runs.
-- Without it the database-level guarantee "a simulated document can never be AUTORIZADO"
-- (DFE-06) is missing. There is no automated migration runner in this repository (no Flyway,
-- no Liquibase). After 134 + 135 + this script the schema is column-for-column what Hibernate
-- generates (proven by MigracaoFiscal134IT, MigracaoFiscal135IT and MigracaoFiscal136IT).
--
-- Design:
-- * The next attempt keeps using the existing proxima_tentativa_em column.
-- * ultimo_erro holds a short, user-safe Portuguese message (never a stack trace);
--   ultimo_erro_codigo the machine code.
-- * reprocessamentos is NOT NULL DEFAULT 0. The default is a deliberate exception to the
--   "no column defaults" convention of 134: it lets this script (and `ddl-auto: update`) add a
--   NOT NULL column to a populated table, backfilling every existing row with 0. The entity
--   declares the same default (@ColumnDefault("0")), so the schemas stay identical.
-- * The CHECK allows estado = 'AUTORIZADO' only when ambiente = 'PRODUCAO'. Neither value exists
--   in this build: it is a pure prohibition, enforced by PostgreSQL (SQLState 23514).
--   Deliberately NO other constraint on the enum-typed columns (PITFALLS P-15).
-- * t_documento_fiscal_xml is insert-only: at most one row per document
--   (uk_documento_fiscal_xml_documento) and a unique 45-character IUD
--   (uk_documento_fiscal_xml_iud). The application never updates nor deletes it.
-- * Deliberately no foreign key (134 has none either: bare UUID columns are the codebase
--   convention).
--
-- Backfill: none beyond the reprocessamentos default. Existing communication rows (all PENDENTE)
-- keep their state and are picked up by the Phase 136 job; the new nullable columns stay NULL.
--
-- Idempotent: every statement guards its own creation (IF NOT EXISTS / catalogue check), so this
-- script is safe to run twice against the same database.

ALTER TABLE t_comunicacao_fiscal ADD COLUMN IF NOT EXISTS lease_ate TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE t_comunicacao_fiscal ADD COLUMN IF NOT EXISTS ultima_tentativa_em TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE t_comunicacao_fiscal ADD COLUMN IF NOT EXISTS ultimo_erro VARCHAR(500);
ALTER TABLE t_comunicacao_fiscal ADD COLUMN IF NOT EXISTS ultimo_erro_codigo VARCHAR(64);
ALTER TABLE t_comunicacao_fiscal ADD COLUMN IF NOT EXISTS concluido_em TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE t_comunicacao_fiscal ADD COLUMN IF NOT EXISTS reprocessamentos INTEGER DEFAULT 0 NOT NULL;

DO $$
DECLARE
    comunicacao regclass := to_regclass('t_comunicacao_fiscal');
BEGIN
    IF NOT EXISTS (
        SELECT 1
          FROM pg_constraint
         WHERE conrelid = comunicacao
           AND conname = 'ck_comunicacao_fiscal_autorizado_producao'
    ) THEN
        ALTER TABLE t_comunicacao_fiscal
            ADD CONSTRAINT ck_comunicacao_fiscal_autorizado_producao
            CHECK (estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO');
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_comunicacao_fiscal_estado_proxima
    ON t_comunicacao_fiscal (estado, proxima_tentativa_em);

CREATE TABLE IF NOT EXISTS t_documento_fiscal_xml (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    documento_fiscal_id UUID NOT NULL,
    iud VARCHAR(45) NOT NULL,
    ambiente VARCHAR(32) NOT NULL,
    repositorio_codigo INTEGER NOT NULL,
    led_codigo INTEGER NOT NULL,
    versao_formato VARCHAR(20) NOT NULL,
    xml TEXT NOT NULL,
    xml_sha256 VARCHAR(64) NOT NULL,
    gerado_em TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_documento_fiscal_xml_documento UNIQUE (documento_fiscal_id),
    CONSTRAINT uk_documento_fiscal_xml_iud UNIQUE (iud)
);
