-- Hotfix (post-v2.16): convert t_tenant.logo_data_url from a Postgres Large Object
-- (oid) column to a plain text column.
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying
-- the code change that removes `@Lob` from `Tenant.logoDataUrl`
-- (backend/src/main/java/com/lexcv/models/Tenant.java).
--
-- Why: `Tenant.logoDataUrl` was originally mapped `@Lob private String logoDataUrl;`.
-- On PostgreSQL, Hibernate maps `@Lob String` to a native Large Object column (`oid`),
-- storing the actual bytes out-of-line in the server-wide `pg_largeobject` table and
-- keeping only a numeric reference in `t_tenant.logo_data_url`. This is fragile: large
-- objects do not survive naive `pg_dump`/`pg_restore` (missing the `-b`/--blobs flag),
-- replication, or certain backup tooling — the `oid` reference can silently become
-- orphaned (pointing at a large object that no longer exists), and reading it back then
-- throws `org.hibernate.HibernateException: Unable to access lob stream`.
--
-- This is exactly what happened in production: `DatabaseSeeder.seedTenantPlataforma()`
-- (Phase 119, `PROV-01`) is the first-ever code path in this codebase to hydrate a full
-- `Tenant` row from inside a `CommandLineRunner` at application-boot time (every prior
-- `Tenant` read happened inside a normal HTTP-request-scoped transaction). The very
-- first attempt to do so crashed the entire application on startup with the error
-- above, because at least one `t_tenant` row's `logo_data_url` oid reference could not
-- be resolved in that context. The Java-side fix removes `@Lob` entirely (PostgreSQL's
-- plain `text` type has no practical length limit, so `@Lob` was never actually
-- necessary here) — this script performs the corresponding one-time data migration.
--
-- There is no automated migration runner in this repository (no Flyway, no Liquibase —
-- only Hibernate `ddl-auto` for schema evolution, and `ddl-auto: validate` in
-- production never creates or alters schema). Without this script, the application
-- will fail to start in production after the code deploy (schema validation error:
-- `logo_data_url` expected as `text`/`varchar`, found as `oid`).
--
-- Data handling: for every row with a non-null `logo_data_url`, the large object's
-- bytes are read back via `lo_get()` and decoded as UTF-8 text (matching how Hibernate
-- originally wrote it) into a new `logo_data_url_text` column. Any row whose oid
-- reference is already orphaned (the large object no longer exists) is caught
-- individually — via a per-row exception handler, NOT a single all-or-nothing
-- statement — logged via RAISE NOTICE, and left NULL in the new column, rather than
-- aborting the whole migration. This is an acceptable, bounded data loss for a
-- non-critical branding/logo field (the tenant remains fully functional either way;
-- the admin can simply re-upload the logo), and is strictly better than the current
-- state, where the reference was already unreadable. Rehearsed against a real
-- populated `oid` value (34,410-character base64 data URL) and against a deliberately
-- invalid oid on 2026-07-30 — both paths confirmed to behave as described above.

-- Step 1: add the new plain-text column.
ALTER TABLE t_tenant ADD COLUMN logo_data_url_text text;

-- Step 2: copy every readable large object's content into the new column, tolerating
-- individually-orphaned references without aborting the migration.
DO $$
DECLARE
    r RECORD;
    conteudo text;
BEGIN
    FOR r IN SELECT id, nome, logo_data_url FROM t_tenant WHERE logo_data_url IS NOT NULL LOOP
        BEGIN
            SELECT convert_from(lo_get(r.logo_data_url), 'UTF8') INTO conteudo;
            UPDATE t_tenant SET logo_data_url_text = conteudo WHERE id = r.id;
            RAISE NOTICE 'logo migrado para o tenant % (oid %, % caracteres)', r.nome, r.logo_data_url, length(conteudo);
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'logo NAO migrado para o tenant % (oid % em falta/corrompido: %) -- logo_data_url_text fica NULL, sem interromper a migracao', r.nome, r.logo_data_url, SQLERRM;
        END;
    END LOOP;
END $$;

-- Step 3: release the large objects still referenced by the old column (cleanup —
-- avoids leaking rows in the server-wide pg_largeobject table). Tolerates the same
-- already-orphaned case as step 2.
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT logo_data_url FROM t_tenant WHERE logo_data_url IS NOT NULL LOOP
        BEGIN
            PERFORM lo_unlink(r.logo_data_url);
        EXCEPTION WHEN OTHERS THEN
            RAISE NOTICE 'lo_unlink(%) ignorado (ja em falta): %', r.logo_data_url, SQLERRM;
        END;
    END LOOP;
END $$;

-- Step 4: drop the old oid column and rename the new one into its place, matching the
-- column name the entity mapping expects (`@Column(name = "logo_data_url")`).
ALTER TABLE t_tenant DROP COLUMN logo_data_url;
ALTER TABLE t_tenant RENAME COLUMN logo_data_url_text TO logo_data_url;
