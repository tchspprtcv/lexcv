-- Phase 138 (SUBS-01..05, Plataforma como Emitente): create t_pagamento_subscricao,
-- drop NOT NULL from office-specific columns on t_documento_fiscal, add adquirente_tenant_id
-- and pagamento_subscricao_id, and create the adquirente index
--
-- IMPORTANT: This is a REQUIRED manual production migration script for any install running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. It MUST be run manually (e.g. via psql or DBeaver)
-- against the database BEFORE booting a build of Phase 138. It requires
-- `133-create-fiscal-foundation-tables.sql`, `134-create-documento-fiscal-tables.sql`,
-- `135-add-nota-credito-documento-fiscal.sql`, `136-efatura-comunicacao.sql` and
-- `137-entrega-documento-fiscal.sql` to have run first.
--
-- Why: `application.yml` runs `ddl-auto: update` and creates the table/columns by itself from the
-- entity mapping. A database running with `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to
-- start without them. There is no automated migration runner in this repository. After this script,
-- the schema is column-for-column what Hibernate generates (proven by MigracaoFiscal138IT).
--
-- Design:
-- * t_pagamento_subscricao records subscription payments made by office tenants to LexCV.
-- * t_documento_fiscal now accommodates invoices emitted by the platform (LexCV) to office tenants:
--   cliente_id, processo_id, honorario_id, pagamento_id become nullable; adquirente_tenant_id
--   and pagamento_subscricao_id record the subscriber tenant and payment references.
-- * Deliberately no foreign keys (bare UUID columns are the codebase convention).
--
-- Idempotent: every statement uses IF NOT EXISTS or safe alter commands, safe to run multiple times.

CREATE TABLE IF NOT EXISTS t_pagamento_subscricao (
    id UUID PRIMARY KEY,
    adquirente_tenant_id UUID NOT NULL,
    valor_pago NUMERIC(19,2) NOT NULL,
    data_pagamento DATE NOT NULL,
    metodo VARCHAR(32) NOT NULL,
    periodo_inicio DATE,
    periodo_fim DATE,
    plano VARCHAR(32),
    criado_por_id UUID,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_pagamento_subscricao_adquirente ON t_pagamento_subscricao (adquirente_tenant_id);
CREATE INDEX IF NOT EXISTS idx_pagamento_subscricao_data ON t_pagamento_subscricao (data_pagamento);

-- Alterações na tabela t_documento_fiscal para suportar faturação de plataforma
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 't_documento_fiscal' AND column_name = 'cliente_id' AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE t_documento_fiscal ALTER COLUMN cliente_id DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 't_documento_fiscal' AND column_name = 'processo_id' AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE t_documento_fiscal ALTER COLUMN processo_id DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 't_documento_fiscal' AND column_name = 'honorario_id' AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE t_documento_fiscal ALTER COLUMN honorario_id DROP NOT NULL;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 't_documento_fiscal' AND column_name = 'pagamento_id' AND is_nullable = 'NO'
    ) THEN
        ALTER TABLE t_documento_fiscal ALTER COLUMN pagamento_id DROP NOT NULL;
    END IF;
END $$;

ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS adquirente_tenant_id UUID;
ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS pagamento_subscricao_id UUID;

CREATE INDEX IF NOT EXISTS idx_documento_fiscal_adquirente_tenant ON t_documento_fiscal (adquirente_tenant_id, data_emissao);
