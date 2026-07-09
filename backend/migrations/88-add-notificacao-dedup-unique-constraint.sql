-- Phase 88 code review (WR-01): DB-level backstop for notificação dedup uniqueness
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that adds `uniqueConstraints = @UniqueConstraint(columnNames = {...})`
-- to `Notificacao.java`.
--
-- Why: `application-prod.yml` sets `ddl-auto: validate` in production (dev/CI use
-- `ddl-auto: update`, which auto-creates this constraint locally from the entity mapping).
-- `ddl-auto=validate` never creates or alters schema — it only checks the existing schema
-- is compatible at startup. Without this script, the unique index declared on the
-- `Notificacao` entity will exist in every developer's local DB but never in production,
-- leaving `AlertasDiariosJob`'s notification idempotency guarantee ("running the job
-- twice never duplicates a notification") resting solely on the application-level
-- check-then-act pair in `notificar(...)` there while appearing closed everywhere else
-- (same precedent/reasoning as migration 82 for `t_honorario`'s uk_honorario_processo).
--
-- There is no automated migration runner in this repository (no Flyway, no Liquibase —
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is
-- therefore manual: run it once against each environment's database (staging/prod)
-- before that environment picks up the deploy that adds the constraint to the entity.

CREATE UNIQUE INDEX uk_notificacao_dedup
    ON t_notificacao (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria);
