-- Phase 117 (PLAN-01): add plano/limite_utilizadores columns to t_tenant
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that adds the `plano`/`limiteUtilizadores` fields to the `Tenant`
-- entity (backend/src/main/java/com/lexcv/models/Tenant.java).
--
-- Why: `application-prod.yml` sets `ddl-auto: validate` in production (dev/CI use
-- `ddl-auto: update`, which auto-adds these columns locally from the entity mapping).
-- `ddl-auto=validate` never creates or alters schema — it only checks the existing
-- schema is compatible at startup. Without this script, the application will fail to
-- start in production (schema validation error: missing columns `plano`/
-- `limite_utilizadores` on table `t_tenant`).
--
-- There is no automated migration runner in this repository (no Flyway, no Liquibase --
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is
-- therefore manual: run it once against each environment's database (staging/prod)
-- before that environment picks up the deploy that introduces these fields.
--
-- Semantics: `limite_utilizadores` = NULL means "sem limite" (no cap on active users) --
-- this is the ONLY representation of "no limit" (AdminController.createUser, Phase 117
-- PLAN-02, never treats a sentinel like -1/MAX_VALUE as meaningful). The existing single
-- production tenant is backfilled to `plano = 'ENTERPRISE'` below so it is never blocked
-- as a side effect of this migration; `limite_utilizadores` is DELIBERATELY left NULL for
-- that tenant (no UPDATE statement touches it) -- this is the intended "unlimited" value
-- for an Enterprise-plan tenant "por acordo", not an omission to be "fixed" later.

ALTER TABLE t_tenant ADD COLUMN plano VARCHAR(255);
ALTER TABLE t_tenant ADD COLUMN limite_utilizadores INTEGER;

UPDATE t_tenant SET plano = 'ENTERPRISE' WHERE plano IS NULL;
