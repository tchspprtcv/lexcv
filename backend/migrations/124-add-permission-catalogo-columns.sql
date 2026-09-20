-- Phase 124 (CATL-01/CATL-02/CATL-03): add rotulo/descricao/modulo/ordem/reservada_plataforma
-- columns to t_permission
--
-- IMPORTANT: This is a REQUIRED manual production migration script. It MUST be run
-- manually (e.g. via psql or DBeaver) against the database BEFORE or DURING deploying the
-- code change that adds the `rotulo`/`descricao`/`modulo`/`ordem`/`reservadaPlataforma`
-- fields to the `Permission` entity (backend/src/main/java/com/lexcv/models/Permission.java).
--
-- Why: `application.yml` runs `ddl-auto: update` and creates these columns by itself on a
-- fresh/dev database from the entity mapping. A database running with
-- `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` refuses to start without them -- `validate` never
-- creates or alters schema, it only checks the existing schema is compatible at startup.
-- There is no automated migration runner in this repository (no Flyway, no Liquibase --
-- only Hibernate `ddl-auto` for schema evolution). Execution of this script is therefore
-- manual: run it once against each environment's database (staging/prod) before that
-- environment picks up the deploy that introduces these fields.
--
-- Deliberately NO backfill UPDATE for rotulo/descricao/modulo/ordem here: this script only
-- creates the columns. DatabaseSeeder.seedRbac() runs unconditionally on the very next boot
-- (it is the first statement of CommandLineRunner.run(), before the app serves its first
-- request) and populates all 20 existing t_permission rows with their descriptive fields,
-- and lowers reservada_plataforma from its DEFAULT TRUE to FALSE on those same 20 catalogue
-- entries in that same boot. There is no exposure window: the seeder is a CommandLineRunner,
-- it always completes before the HTTP listener starts accepting requests.
--
-- Idempotent: every ADD COLUMN statement below uses IF NOT EXISTS, so this script is safe to
-- run twice against the same database.

ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS rotulo VARCHAR(255);
ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS descricao VARCHAR(500);
ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS modulo VARCHAR(255);
ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS ordem INTEGER;
ALTER TABLE t_permission ADD COLUMN IF NOT EXISTS reservada_plataforma BOOLEAN NOT NULL DEFAULT TRUE;

DO $$
DECLARE
    linhas_sem_rotulo INTEGER;
BEGIN
    SELECT COUNT(*) INTO linhas_sem_rotulo FROM t_permission WHERE rotulo IS NULL;
    RAISE NOTICE 'Migracao 124: % linha(s) de t_permission ainda com rotulo IS NULL -- '
        'esperado ate ao proximo arranque do backend (DatabaseSeeder.seedRbac() povoa-as).',
        linhas_sem_rotulo;
END $$;
