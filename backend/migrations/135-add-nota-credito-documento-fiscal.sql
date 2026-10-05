-- Phase 135 (NCRD-01..03, Nota de Crédito): add documento_origem_id, motivo_codigo and
-- motivo_texto to t_documento_fiscal, plus the index idx_documento_fiscal_tenant_origem
--
-- IMPORTANT: This is a REQUIRED manual production migration script for any install running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`. It MUST be run manually (e.g. via psql or DBeaver)
-- against the database BEFORE or DURING deploying the code change that adds the Nota de Crédito
-- columns to the `DocumentoFiscal` entity (backend/src/main/java/com/lexcv/models/). It requires
-- `134-create-documento-fiscal-tables.sql` to have run first (t_documento_fiscal must exist).
--
-- Why: `application.yml` runs `ddl-auto: update` and adds these columns by itself on a dev
-- database from the entity mapping. A database running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to start without them -- `validate` never
-- alters schema, it only checks that the existing schema matches the entities at startup
-- (missing column documento_origem_id / motivo_codigo / motivo_texto). There is no automated
-- migration runner in this repository (no Flyway, no Liquibase). After 134 + this script the
-- schema is column-for-column what Hibernate generates (proven by MigracaoFiscal134IT and
-- MigracaoFiscal135IT).
--
-- Design: a Nota de Crédito (tipo = 'NC') references the Fatura-Recibo it corrects through
-- documento_origem_id. Its pagamento_id is the id of the NC's OWN negative estorno payment, so
-- pagamento_id stays NOT NULL + UNIQUE (uk_documento_fiscal_pagamento) and an estorno can never
-- receive a second document. No existing constraint is changed or relaxed.
--
-- Deliberately NO constraint restricting the values of motivo_codigo: it is a plain
-- VARCHAR(32), so a future motivo needs no DROP CONSTRAINT (PITFALLS P-15). Deliberately no
-- foreign key (134 has none either: bare UUID columns are the codebase convention).
--
-- Deliberately NO backfill/UPDATE: existing Fatura-Recibo rows keep NULL in the three new
-- columns, which is exactly what the application writes for an FR.
--
-- Idempotent: every statement uses IF NOT EXISTS, so this script is safe to run twice against
-- the same database.

ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS documento_origem_id UUID;
ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS motivo_codigo VARCHAR(32);
ALTER TABLE t_documento_fiscal ADD COLUMN IF NOT EXISTS motivo_texto VARCHAR(200);

CREATE INDEX IF NOT EXISTS idx_documento_fiscal_tenant_origem
    ON t_documento_fiscal (tenant_id, documento_origem_id);
