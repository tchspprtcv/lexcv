-- Phase 134 (EMIS-01, EMIS-08, Fatura-Recibo atómica nos honorários): create t_documento_fiscal,
-- t_documento_fiscal_linha and t_comunicacao_fiscal, plus a guarded unique index on
-- t_conta_corrente.cliente_id
--
-- IMPORTANT: This is a REQUIRED manual production migration script for any install running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. It MUST be run manually (e.g. via psql or DBeaver)
-- against the database BEFORE or DURING deploying the code change that introduces the
-- `DocumentoFiscal`, `DocumentoFiscalLinha` and `ComunicacaoFiscal` entities
-- (backend/src/main/java/com/lexcv/models/).
--
-- Why: `application.yml` runs `ddl-auto: update` and creates these three tables by itself on a
-- fresh/dev database from the entity mapping. A database running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to start without them -- `validate` never
-- creates or alters schema, it only checks the existing schema is compatible at startup. There
-- is no automated migration runner in this repository (no Flyway, no Liquibase). The schema
-- below is column-for-column what Hibernate generates (proven by MigracaoFiscal134IT).
--
-- t_documento_fiscal is a SNAPSHOT: emitter and buyer data are flat columns copied at issue
-- time, never references to the live client / fiscal-settings rows. Documents and lines are
-- never updated nor deleted (the entities are @Immutable). t_comunicacao_fiscal is the mutable
-- communication-status satellite (one row per document, born PENDENTE, consumed by Phase 136).
--
-- Deliberately no foreign key to t_tenant (or to the other referenced tables): codebase
-- convention is bare UUID / integer id columns (no navigable @ManyToOne).
--
-- Deliberately NO constraint restricting the values of the enum-typed columns (tipo, ambiente,
-- emitente_regime_iva, estado): they are plain VARCHAR(32), so a future enum value -- e.g. the
-- communication states of Phase 136 -- needs no DROP CONSTRAINT. No column defaults either.
--
-- Deliberately NO backfill/INSERT: payments recorded before the office activated invoicing are
-- NOT invoiced retroactively (EMIS-12).
--
-- t_conta_corrente.cliente_id: the emission path upserts the client's current account with
-- INSERT ... ON CONFLICT (cliente_id) DO NOTHING, which requires a unique index whose only key
-- column is cliente_id. Hibernate already declares one (ContaCorrente.clienteId unique = true),
-- but an old install may lack it. The DO block at the end creates uk_conta_corrente_cliente only
-- when t_conta_corrente exists and has no such index, and ABORTS with a clear message (creating
-- nothing) when duplicate cliente_id values exist -- resolve them first, then re-run.
--
-- Idempotent: every statement below guards its own creation (IF NOT EXISTS / catalogue check),
-- so this script is safe to run twice against the same database.

CREATE TABLE IF NOT EXISTS t_documento_fiscal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    tipo VARCHAR(32) NOT NULL,
    ambiente VARCHAR(32) NOT NULL,
    serie_id UUID NOT NULL,
    serie_codigo VARCHAR(20) NOT NULL,
    ano INTEGER NOT NULL,
    numero BIGINT NOT NULL,
    numero_formatado VARCHAR(40) NOT NULL,
    data_emissao DATE NOT NULL,
    emitido_em TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    emitente_nif VARCHAR(9) NOT NULL,
    emitente_firma VARCHAR(200) NOT NULL,
    emitente_morada VARCHAR(100) NOT NULL,
    emitente_localidade VARCHAR(100),
    emitente_regime_iva VARCHAR(32) NOT NULL,
    emitente_motivo_isencao_codigo VARCHAR(2),
    emitente_motivo_isencao_descricao VARCHAR(200),
    emitente_motivo_isencao_mencao VARCHAR(100),
    adquirente_nif VARCHAR(9) NOT NULL,
    adquirente_nome VARCHAR(150) NOT NULL,
    adquirente_morada VARCHAR(100) NOT NULL,
    adquirente_localidade VARCHAR(100),
    cliente_id UUID NOT NULL,
    processo_id UUID NOT NULL,
    honorario_id INTEGER NOT NULL,
    pagamento_id INTEGER NOT NULL,
    metodo_pagamento VARCHAR(32) NOT NULL,
    meio_pagamento_codigo VARCHAR(3) NOT NULL,
    moeda VARCHAR(3) NOT NULL,
    taxa_iva NUMERIC(7,4) NOT NULL,
    total_base NUMERIC(19,2) NOT NULL,
    total_iva NUMERIC(19,2) NOT NULL,
    total_retencao NUMERIC(19,2) NOT NULL,
    total_documento NUMERIC(19,2) NOT NULL,
    valor_liquido NUMERIC(19,2) NOT NULL,
    taxa_retencao NUMERIC(7,4),
    chave_idempotencia UUID NOT NULL,
    emitido_por_id UUID,
    emitido_por_nome VARCHAR(255),
    CONSTRAINT uk_documento_fiscal_numero UNIQUE (tenant_id, serie_id, numero),
    CONSTRAINT uk_documento_fiscal_pagamento UNIQUE (pagamento_id),
    CONSTRAINT uk_documento_fiscal_chave UNIQUE (tenant_id, chave_idempotencia)
);

CREATE INDEX IF NOT EXISTS idx_documento_fiscal_tenant_data ON t_documento_fiscal (tenant_id, data_emissao);
CREATE INDEX IF NOT EXISTS idx_documento_fiscal_tenant_cliente ON t_documento_fiscal (tenant_id, cliente_id);
CREATE INDEX IF NOT EXISTS idx_documento_fiscal_tenant_processo ON t_documento_fiscal (tenant_id, processo_id);
CREATE INDEX IF NOT EXISTS idx_documento_fiscal_tenant_honorario ON t_documento_fiscal (tenant_id, honorario_id);

CREATE TABLE IF NOT EXISTS t_documento_fiscal_linha (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    documento_fiscal_id UUID NOT NULL,
    numero_linha INTEGER NOT NULL,
    descricao VARCHAR(200) NOT NULL,
    quantidade NUMERIC(19,4) NOT NULL,
    preco_unitario NUMERIC(19,2) NOT NULL,
    valor_base NUMERIC(19,2) NOT NULL,
    taxa_iva NUMERIC(7,4) NOT NULL,
    valor_iva NUMERIC(19,2) NOT NULL,
    motivo_isencao_codigo VARCHAR(2),
    taxa_retencao NUMERIC(7,4),
    valor_retencao NUMERIC(19,2) NOT NULL,
    total_linha NUMERIC(19,2) NOT NULL,
    CONSTRAINT uk_documento_fiscal_linha_numero UNIQUE (documento_fiscal_id, numero_linha)
);

CREATE TABLE IF NOT EXISTS t_comunicacao_fiscal (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    documento_fiscal_id UUID NOT NULL,
    ambiente VARCHAR(32) NOT NULL,
    estado VARCHAR(32) NOT NULL,
    tentativas INTEGER NOT NULL,
    proxima_tentativa_em TIMESTAMP(6) WITH TIME ZONE,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE,
    versao BIGINT NOT NULL,
    CONSTRAINT uk_comunicacao_fiscal_documento UNIQUE (documento_fiscal_id)
);

CREATE INDEX IF NOT EXISTS idx_comunicacao_fiscal_tenant_estado ON t_comunicacao_fiscal (tenant_id, estado);

DO $$
DECLARE
    conta_corrente regclass := to_regclass('t_conta_corrente');
BEGIN
    IF conta_corrente IS NULL THEN
        RETURN;
    END IF;

    -- Already covered by a unique index whose only key column is cliente_id (e.g. the one
    -- Hibernate creates for ContaCorrente.clienteId unique = true): nothing to do.
    IF EXISTS (
        SELECT 1
          FROM pg_index i
          JOIN pg_attribute a ON a.attrelid = i.indrelid AND a.attnum = i.indkey[0]
         WHERE i.indrelid = conta_corrente
           AND i.indisunique
           AND i.indnkeyatts = 1
           AND i.indpred IS NULL
           AND a.attname = 'cliente_id'
    ) THEN
        RETURN;
    END IF;

    IF EXISTS (SELECT cliente_id FROM t_conta_corrente GROUP BY cliente_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Phase 134: t_conta_corrente tem cliente_id duplicado (mais do que uma conta corrente por cliente); resolva antes de continuar';
    END IF;

    CREATE UNIQUE INDEX IF NOT EXISTS uk_conta_corrente_cliente ON t_conta_corrente (cliente_id);
END $$;
