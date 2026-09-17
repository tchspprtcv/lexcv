-- ############################################################################
-- ##  STOP. EXISTING-DATABASE ONLY. NEVER RUN THIS ON A FRESH DATABASE.     ##
-- ############################################################################
--
-- >> There is now a TECHNICAL GUARD at the bottom of this header (search for
-- >> "GUARDA TECNICA"). It reads the real column type and aborts the whole
-- >> script before touching anything if `logo_data_url` is not `oid`. Everything
-- >> you read below is still true about what WOULD happen without it — the
-- >> written warning is now reinforcement, no longer the only line of defence.
--
-- This script is valid ONLY against a database where t_tenant.logo_data_url is
-- still a Postgres Large Object (`oid`) column. On a database created from the
-- current entity mapping it is DESTRUCTIVE, and it does NOT fail — it silently
-- deletes every logo already uploaded and exits successfully.
--
-- VERIFY FIRST. Run this and read the answer before going any further:
--
--   SELECT data_type FROM information_schema.columns
--   WHERE table_name = 't_tenant' AND column_name = 'logo_data_url';
--
--     oid  -> this migration applies. Run it ONCE.
--     text -> ALREADY MIGRATED, or a fresh database. DO NOT RUN THIS SCRIPT.
--
-- Why it destroys data on a fresh database:
--   1. Tenant.java:37-38 maps the column as
--      @Column(name = "logo_data_url", columnDefinition = "text") with NO @Lob,
--      so on a fresh database the column is born as `text`, never as `oid`.
--   2. Step 2 (line 115 below) calls lo_get() on that `text` value. There is no
--      lo_get(text) overload and no implicit text -> oid cast, so the call fails
--      with: function lo_get(text) does not exist.
--   3. That error is SWALLOWED by the `EXCEPTION WHEN OTHERS` handler (line 118).
--      logo_data_url_text stays NULL and the script continues, reporting success.
--   4. Step 4 then runs DROP COLUMN logo_data_url (line 142), deleting the real
--      logo data, and RENAME COLUMN logo_data_url_text (line 143) puts the
--      all-NULL column in its place.
--
--   The only trace left behind is a RAISE NOTICE line. Nothing throws, nothing
--   in the application reports an error, and the logos are gone.
--
-- NOT RE-RUNNABLE, for the same reason: after a successful run the column is
-- already `text`, so a second execution would follow the exact destructive path
-- above and wipe the logos this script just migrated. The guard turns that into
-- a clean, loud failure: a second run now aborts with the same exception as any
-- other already-`text` database, and destroys nothing.
--
-- EVIDENCE BASE for the four steps above: executed, not only reasoned about.
-- Rehearsed on 2026-08-22 against a throwaway database whose logo_data_url was a
-- populated `text` column (34,432-character data URL), running this file verbatim
-- with -v ON_ERROR_STOP=1. Observed: two swallowed errors surfacing only as
-- RAISE NOTICE (`function lo_get(text) does not exist`, then
-- `function lo_unlink(text) does not exist`), both step-4 statements reporting
-- ALTER TABLE, exit code 0, and logo_data_url left `text` and NULL — the 34,432
-- characters gone. Caveats, neither of which softens the rule: the run was on
-- PostgreSQL 18.4, while the deployment images are postgres:16-alpine; and the
-- fixture reproduced the fresh-database column shape, not a full Hibernate-generated
-- schema — the Tenant.java mapping claim in point 1 is established by reading the
-- entity. The prohibition is the conservative side of the bet either way: this script
-- is only ever NEEDED where the column is `oid`, so refusing to run it on a fresh
-- database costs nothing. VERIFY THE COLUMN TYPE AND ACT ON THAT, not on reasoning.
--
-- Operational checklist for all migrations: backend/migrations/README.md
--
-- ############################################################################

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

-- ############################################################################
-- ##  GUARDA TECNICA — auto-executavel. NAO REMOVER.                        ##
-- ############################################################################
--
-- Duas construcoes, e so estas duas, tornam a protecao auto-executavel:
--
--   1. `BEGIN` ... `COMMIT` (no fim do ficheiro) envolvem a migracao inteira
--      numa unica transacao. Se a guarda disparar, TUDO o que vem a seguir
--      aborta ou e revertido — inclusive quando o operador se esquece de
--      `-v ON_ERROR_STOP=1`. Nao ha trabalho parcial nem estado intermedio.
--   2. O bloco `DO $guarda$` a seguir le o tipo REAL da coluna em pg_catalog
--      (a fonte autoritativa, nao a reformulacao do information_schema) e faz
--      RAISE EXCEPTION se nao for `oid`.
--
-- PORQUE E QUE ISTO NAO E ENGOLIDO PELOS `EXCEPTION WHEN OTHERS`:
--   Os dois handlers `WHEN OTHERS` deste ficheiro (passos 2 e 3) vivem DENTRO
--   dos respetivos blocos `DO`, e um handler so alcanca o que acontece no seu
--   proprio bloco. Esta guarda e uma instrucao de topo separada, IRMA e nao
--   filha desses blocos, e corre ANTES de ambos — nenhum deles esta sequer a
--   executar quando ela dispara. Foi exatamente o padrao `WHEN OTHERS` que
--   engoliu o `function lo_get(text) does not exist` original; a guarda esta
--   deliberadamente colocada fora do seu alcance lexico e temporal.
--
-- IDEMPOTENTE POR CONSTRUCAO: apos uma execucao com exito a coluna ja e `text`,
-- por isso a segunda execucao cai no mesmo ramo e falha com a mesma mensagem.

BEGIN;

DO $guarda$
DECLARE
    tipo_atual text;
BEGIN
    SELECT format_type(a.atttypid, a.atttypmod)
      INTO tipo_atual
      FROM pg_attribute  a
      JOIN pg_class      c ON c.oid = a.attrelid
      JOIN pg_namespace  n ON n.oid = c.relnamespace
     WHERE n.nspname = current_schema()
       AND c.relname = 't_tenant'
       AND a.attname = 'logo_data_url'
       AND a.attnum  > 0
       AND NOT a.attisdropped;

    IF tipo_atual IS NULL THEN
        RAISE EXCEPTION
            'GUARDA 125: a coluna t_tenant.logo_data_url nao existe no schema "%". Esta migracao NAO se aplica a esta base de dados e foi ABORTADA sem tocar em nada.',
            current_schema()
            USING HINT = 'Confirme que esta ligado a base de dados e ao schema corretos antes de correr qualquer migracao.';
    END IF;

    IF tipo_atual <> 'oid' THEN
        RAISE EXCEPTION
            'GUARDA 125: t_tenant.logo_data_url ja e do tipo "%", nao "oid". Esta migracao NAO se aplica a esta base de dados e foi ABORTADA sem tocar em nada.',
            tipo_atual
            USING DETAIL = 'A base ja foi migrada, ou nasceu em text (instalacao nova). Prosseguir APAGARIA todos os logotipos: o lo_get() do passo 2 falharia em silencio (engolido por EXCEPTION WHEN OTHERS), o DROP COLUMN do passo 4 eliminaria os dados reais e o RENAME poria uma coluna toda NULL no lugar.',
                  HINT   = 'Nao ha nada a fazer nesta base de dados. Registe 125 como concluida para ela e siga em frente.';
    END IF;

    RAISE NOTICE 'GUARDA 125: t_tenant.logo_data_url e "oid" — a migracao aplica-se; a prosseguir.';
END
$guarda$;

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

-- Fecha a transacao aberta pela GUARDA TECNICA. Se a guarda tiver disparado,
-- este COMMIT e tratado como ROLLBACK e nada acima foi consumado.
COMMIT;
