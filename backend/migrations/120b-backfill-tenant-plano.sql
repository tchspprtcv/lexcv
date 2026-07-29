-- Phase 120 code review (CR-01): backfill NULL t_tenant.plano rows and tighten the column to
-- NOT NULL DEFAULT 'STARTER'
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run manually
-- (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying the code change that
-- adds `@Builder.Default private TenantPlano plano = TenantPlano.STARTER;` to the `Tenant` entity
-- (backend/src/main/java/com/lexcv/models/Tenant.java).
--
-- Why: migration 117-add-tenant-plano-limite-utilizadores.sql added `plano` as a nullable
-- VARCHAR(255) with a one-time backfill of the single tenant that existed in production at that
-- time (`plano = 'ENTERPRISE'`). Every tenant created since then was built without ever calling
-- `.plano(...)` on the entity's builder -- `SetupService.initializeSystem`, `SetupService.
-- provisionTenant` (the endpoint this phase's "Criar Tenant" panel calls), and `DatabaseSeeder.
-- seedTenantPlataforma()` (the reserved "LexCV" tenant, seeded unconditionally on every boot) --
-- because `Tenant.plano` had no `@Builder.Default`, unlike `Tenant.ativo` (this same phase's own
-- field). The result was `plano = NULL` for every tenant this application has ever created since
-- migration 117 ran, confirmed against the real dev database by this phase's own
-- 120-HUMAN-UAT.md (point 10: both existing tenants have `plano=NULL`).
--
-- `ddl-auto=validate` in production never alters schema, and `ddl-auto=update` (dev/CI) does not
-- retroactively tighten constraints on a column that already exists (Hibernate's SchemaUpdate only
-- adds missing tables/columns; it does not add NOT NULL/DEFAULT to an already-existing nullable
-- column). This script is therefore the only way an environment where migration 117 has already
-- run converges on the same "NOT NULL DEFAULT 'STARTER'" invariant the entity now declares.
--
-- Idempotent: the UPDATE only touches rows still NULL, and both ALTER statements are safe to run
-- more than once.

UPDATE t_tenant SET plano = 'STARTER' WHERE plano IS NULL;

ALTER TABLE t_tenant ALTER COLUMN plano SET DEFAULT 'STARTER';
ALTER TABLE t_tenant ALTER COLUMN plano SET NOT NULL;
