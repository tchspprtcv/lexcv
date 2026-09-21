-- Phase 126 (MIGR-01/MIGR-03): create t_user_tenant_role -- the join table between a user
-- and the office roles (TenantRole) of their own tenant.
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run manually
-- (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying the code change
-- that adds the `tenantRoles` field to the `User` entity
-- (backend/src/main/java/com/lexcv/models/User.java).
--
-- Why: `application.yml` runs `ddl-auto: update` and creates this table by itself on a
-- fresh/dev database from the entity mapping. A database running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to start without it -- `validate` never
-- creates or alters schema, it only checks the existing schema is compatible at startup.
-- There is no automated migration runner in this repository (no Flyway, no Liquibase -- only
-- Hibernate `ddl-auto` for schema evolution). Execution of this script is therefore manual:
-- run it once against each environment's database (staging/prod) before that environment
-- picks up the deploy that introduces this field.
--
-- Deliberately NO data backfill here. Converting existing users -- instantiating office roles
-- (TenantRole) for every existing tenant and pointing each user at the office-role equivalent
-- of their current global role -- is not done in SQL. It is done in Java, on the next boot, by
-- a convergent, idempotent service (MigracaoPapeisEscritorioService, Phase 126 plan 03), the
-- same idiom the `124` script already uses for DatabaseSeeder.seedRbac() to populate
-- rotulo/descricao/modulo/ordem on t_permission. This is reuse, not preference:
-- SetupService.instanciarMoldes is already the proven instantiation mechanism (Phase 125), and
-- reimplementing it in SQL would create a second definition of the same rule, capable of
-- diverging.
--
-- `t_user_role` is NOT touched by this script, nor by the conversion described above. The new
-- association coexists with the old one -- reverting this migration means pointing the reads
-- back, not restoring a backup. There is no `DROP COLUMN`, `DELETE` or `TRUNCATE` anywhere in
-- this script or this phase.
--
-- Idempotent: `CREATE TABLE IF NOT EXISTS` -- safe to run twice against the same database.

CREATE TABLE IF NOT EXISTS t_user_tenant_role (
    user_id UUID NOT NULL REFERENCES t_user(id),
    tenant_role_id UUID NOT NULL REFERENCES t_tenant_role(id),
    PRIMARY KEY (user_id, tenant_role_id)
);
