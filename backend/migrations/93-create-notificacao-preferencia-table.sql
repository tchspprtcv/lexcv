-- Phase 93 (NOTF-24): create t_notificacao_preferencia table + unique index
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that adds the `NotificacaoPreferencia` entity (backend/src/main/java/
-- com/lexcv/models/NotificacaoPreferencia.java) and its `NotificacaoPreferenciaRepository`.
--
-- Why: `application-prod.yml` sets `ddl-auto: validate` in production (dev/CI use
-- `ddl-auto: update`, which auto-creates this table locally from the entity mapping).
-- `ddl-auto=validate` never creates or alters schema — it only checks the existing
-- schema is compatible at startup. Without this script, the application will fail to
-- start in production (schema validation error: missing table `t_notificacao_preferencia`).
--
-- There is no automated migration runner in this repository (no Flyway, no Liquibase --
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is
-- therefore manual: run it once against each environment's database (staging/prod)
-- before that environment picks up the deploy that introduces the
-- `NotificacaoPreferencia` entity.
--
-- Semantics: presence of a row = the (tenant, user, categoria) combination is muted;
-- absence = default-on delivery. No boolean "ativo" column -- row existence IS the
-- preference.

CREATE TABLE t_notificacao_preferencia (
    id          UUID PRIMARY KEY,
    tenant_id   UUID NOT NULL,
    user_id     UUID NOT NULL,
    categoria   VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL
);

CREATE UNIQUE INDEX uk_notificacao_preferencia
    ON t_notificacao_preferencia (tenant_id, user_id, categoria);
