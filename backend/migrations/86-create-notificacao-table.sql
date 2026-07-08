-- Phase 86: create t_notificacao table + composite read index
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that adds the `Notificacao` entity (backend/src/main/java/com/lexcv/
-- models/Notificacao.java) and its `NotificacaoRepository`.
--
-- Why: `application-prod.yml` sets `ddl-auto: validate` in production (dev/CI use
-- `ddl-auto: update`, which auto-creates this table locally from the entity mapping).
-- `ddl-auto=validate` never creates or alters schema — it only checks the existing
-- schema is compatible at startup. Without this script, the application will fail to
-- start in production (schema validation error: missing table `t_notificacao`).
--
-- There is no automated migration runner in this repository (no Flyway, no Liquibase —
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is
-- therefore manual: run it once against each environment's database (staging/prod)
-- before that environment picks up the deploy that introduces the `Notificacao` entity.

CREATE TABLE t_notificacao (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL,
    destinatario_id UUID NOT NULL,
    categoria       VARCHAR(255) NOT NULL,
    entidade_tipo   VARCHAR(255) NOT NULL,
    entidade_id     VARCHAR(255) NOT NULL,
    titulo          VARCHAR(255) NOT NULL,
    mensagem        TEXT NOT NULL,
    link_url        VARCHAR(255),
    lida            BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP NOT NULL
);

CREATE INDEX idx_notificacao_tenant_destinatario_lida_created
    ON t_notificacao (tenant_id, destinatario_id, lida, created_at);
