# Manual migrations — operational checklist

**This file is the authoritative migration checklist for ALCv.** It lives next to the
`.sql` files, is versioned with the code, and is the only place where the migration
inventory is maintained. Any other document (commercial, planning, deployment) must link
here rather than restate the list.

There is **no automated migration runner** in this repository — no Flyway, no Liquibase.
Schema evolution relies on Hibernate `ddl-auto` (`update` in the default profile,
`validate` in `application-prod.yml`). Everything in this directory is executed **by hand**,
by a human, against a specific database.

---

## STOP — read this before you paste anything

> ### `125-convert-tenant-logo-data-url-to-text.sql` MUST NEVER RUN ON A FRESH DATABASE
>
> **Since 2026-08-22 this rule is enforced technically, not only in writing.** The script
> opens with a `BEGIN` and a `DO $guarda$` block that reads the real column type from
> `pg_catalog` and raises an exception if it is not `oid` — before any `lo_get`, `DROP` or
> `RENAME`. Run it on the wrong database and it aborts, loudly, having changed nothing.
> **The warning below is now reinforcement, not the only line of defence** — but it stays,
> because it is what tells you *why* the guard exists, and because no guard replaces
> verifying which database you are connected to.
>
> What follows describes what the script **would** do without that guard. It is still worth
> reading: it is the reason the guard is there.
>
> Mechanism, on a database whose schema was created from the current entity:
>
> 1. `Tenant.java:37-38` maps the column as `@Column(name = "logo_data_url", columnDefinition = "text")`
>    with **no `@Lob`** — so on a fresh database `logo_data_url` is born as `text`, never as `oid`.
> 2. Step 2 (line 115) calls `lo_get(r.logo_data_url)` on that `text` value. There is no
>    `lo_get(text)` overload and no implicit `text` → `oid` cast, so the call errors with
>    `function lo_get(text) does not exist`.
> 3. That error is **swallowed** by the `EXCEPTION WHEN OTHERS` handler on line 118. The
>    destination column `logo_data_url_text` is left `NULL` and the script keeps going,
>    reporting success.
> 4. Step 4 then runs `DROP COLUMN logo_data_url` (line 142) — deleting the real logo data —
>    followed by `RENAME COLUMN logo_data_url_text TO logo_data_url` (line 143), putting the
>    all-`NULL` column in its place.
>
> Net result, **without the guard**: every tenant logo is gone, the script exits `0`, and
> nothing in the application reports an error. The same thing would happen on a second run
> against a database where `125` already succeeded — see [Re-run safety](#re-run-safety).
> **With the guard, both of those paths now abort at statement one.**
>
> #### Evidence base — this was executed, not only reasoned about
>
> Rehearsed end to end on **2026-08-22**, against a throwaway database whose
> `t_tenant.logo_data_url` was a populated `text` column (a 34,432-character `data:` URL),
> running **this exact file verbatim** with `-v ON_ERROR_STOP=1`. Observed:
>
> - two swallowed errors, surfacing only as `RAISE NOTICE` —
>   `function lo_get(text) does not exist`, then `function lo_unlink(text) does not exist`;
> - both step-4 statements reporting `ALTER TABLE` successfully;
> - **exit code `0`**;
> - the surviving `logo_data_url` column left as `text` and `NULL` — the 34,432 characters gone.
>
> Two honest caveats, neither of which softens the rule. The rehearsal ran on **PostgreSQL
> 18.4** (a local server), while the deployment images are `postgres:16-alpine`; overload
> resolution for `lo_get`/`lo_unlink` is not version-dependent in a way that would change
> this outcome, but the run itself was not on 16. And the fixture reproduced the fresh-database
> *column shape*, not a full Hibernate-generated schema. The mapping claim in step 1 above
> (`Tenant.java`, `columnDefinition = "text"`, no `@Lob`) remains established by reading the
> entity, not by booting the app against an empty database.
>
> **The prohibition does not depend on any of that.** It is the conservative side of the
> bet: `125` is only ever *needed* where the column is `oid`, so refusing to run it on the
> root/fresh path costs nothing even if some detail of the mechanism were wrong. Verify the
> column type and act on the answer — never on the reasoning.
>
> #### The guard — also executed, not only reasoned about
>
> Verified on **2026-08-22** against three throwaway databases on the same local
> PostgreSQL 18.4, running the file **verbatim**, with a 34,433-character `data:` URL
> (`md5 ad2e8489b5cf1cb15ac9fc8973e641c3`) as the fixture logo:
>
> | Scenario | Observed |
> |---|---|
> | `logo_data_url` is `oid`, logo present | Guard emits its "a prosseguir" notice, migration completes, exit `0`, column now `text`, logo `md5` **unchanged** — 34,433 chars, byte for byte. `pg_largeobject_metadata` back to 0 rows. |
> | `logo_data_url` already `text`, logo present | `ERROR: GUARDA 125: ... ja e do tipo "text", nao "oid"`, exit `3`, column unchanged, logo `md5` **unchanged**. |
> | Second run immediately after a successful first run | Same error, exit `3`, logo `md5` **unchanged**, no leftover `logo_data_url_text` column. A third run behaves identically. |
> | Wrong database (no `t_tenant.logo_data_url` at all) | `ERROR: GUARDA 125: a coluna ... nao existe no schema "public"`, exit `3`. |
>
> The guard was also run against an already-`text` database **without** `-v ON_ERROR_STOP=1`,
> the case where a bare `RAISE EXCEPTION` would not be enough: the surrounding transaction
> left every later statement rejected with `current transaction is aborted`, the closing
> `COMMIT` reported `ROLLBACK`, and the logo survived intact. `psql` still exits `0` there —
> **exit code is not the signal in that mode, the `ROLLBACK` line is** — which is why rule 3
> under [How to run](#how-to-run) still stands.

---

## Pick your path

| Your situation | Follow |
|---|---|
| Brand-new, empty database (first install, new client, new environment) | [Path A — fresh install](#path-a--fresh-install) |
| Database already in use, with real data, receiving a new deploy | [Path B — existing database](#path-b--existing-database) |

---

## Path A — fresh install

On a fresh database the schema is created from the JPA entity mappings on first boot
(`ddl-auto: update` — note that none of the committed `docker-compose*.yml` files set
`SPRING_PROFILES_ACTIVE`, so the default profile, not `prod`, is the one that actually runs).
Every column, table, constraint and index that the numbered scripts add is therefore created
automatically. There is nothing to backfill, because there are no rows.

**Exactly one script is mandatory:**

| # | File | Why it is still required |
|---|---|---|
| 1 | `111-enable-search-extensions.sql` | `CREATE EXTENSION` is **not** part of Hibernate's schema model. No `ddl-auto` setting ever issues it. Without it, every global-search query calling `unaccent(...)` fails at runtime with `ERROR: function unaccent(text) does not exist`. The file itself states it must run in **every** environment, local dev included. |

**Run it before first boot** (or at least before anyone uses Pesquisa Global).

Everything else in this directory is **skip** on a fresh install:

- `74`, `81`, `82`, `86`, `88`, `91`, `93`, `96`, `117`, `120` — redundant; `ddl-auto` already
  produced the same schema. Running them raises a duplicate-object error (harmless, but noise).
- `120b` — nothing to backfill; the entity's `@Builder.Default` + `columnDefinition` already
  give every new row a non-null `plano`.
- `125` — **FORBIDDEN.** See the stop banner above. The script now refuses to run here on its
  own (guard), but do not treat that as licence to try it: a fresh database never needs it.
- `126-add-tenant-role-tables.sql` — redundant, but unlike the group above it will not error:
  `ddl-auto` already created `t_tenant_role`, `t_tenant_role_permission` and
  `t_role.instanciavel`, and every statement in the script guards its own creation, so running
  it against a fresh database simply changes nothing.
- `127-add-user-tenant-role-table.sql` — redundant, but unlike the `FORBIDDEN` group it will
  not error: `ddl-auto` already created `t_user_tenant_role`, and the script's own
  `CREATE TABLE IF NOT EXISTS` guards its creation, so running it against a fresh database
  simply changes nothing.

---

## Path B — existing database

Run **in the order listed below**, skipping any script already applied to *that specific
database*. Verify per database, not per environment — the environments have drifted (see
[Known execution status](#known-execution-status)).

| Order | File | What it does | Re-runnable? |
|---|---|---|---|
| 1 | `74-cleanup-nif-documento-tipo.sql` | Nulls `documento_tipo`/`documento_numero` on legacy `'NIF'` rows before the enum constant is removed. Without it, reading any such `Cliente` crashes. | **Yes** — the `UPDATE` converges (0 rows on a second pass). |
| 2 | `81-add-facto-ordem-unique-constraint.sql` | `uk_facto_processo_ordem` on `t_facto(processo_id, ordem)`. | No |
| 3 | `82-add-honorario-processo-unique-constraint.sql` | `uk_honorario_processo` on `t_honorario(processo_id)`. | No |
| 4 | `86-create-notificacao-table.sql` | Creates `t_notificacao` + read index. | No |
| 5 | `88-add-notificacao-dedup-unique-constraint.sql` | `uk_notificacao_dedup` unique index. | No |
| 6 | `91-add-parecer-versao-unique-constraint.sql` | `uk_parecer_versao_solicitacao_numero`. **Check by column set first** — the file explains why checking the constraint *name* is not enough on a DB bootstrapped by `ddl-auto: update`. | No |
| 7 | `93-create-notificacao-preferencia-table.sql` | Creates `t_notificacao_preferencia` + unique index. | No |
| 8 | `96-add-notificacao-snoozed-until.sql` | Adds `t_notificacao.snoozed_until`. | No |
| 9 | `111-enable-search-extensions.sql` | Enables `unaccent` + `pg_trgm`. Required in **every** environment. | **Yes** — `CREATE EXTENSION IF NOT EXISTS`. |
| 10 | `117-add-tenant-plano-limite-utilizadores.sql` | Adds `t_tenant.plano` + `limite_utilizadores`, backfills existing rows to `'ENTERPRISE'`. `limite_utilizadores` is deliberately left `NULL` = "sem limite". | No |
| 11 | `120-add-tenant-ativo.sql` | Adds `t_tenant.ativo BOOLEAN NOT NULL DEFAULT TRUE`. | **No — read the warning below.** |
| 12 | `120b-backfill-tenant-plano.sql` | Backfills `plano = 'STARTER'` on rows still `NULL`, then tightens the column to `NOT NULL DEFAULT 'STARTER'`. **Existing-database only.** Skipping it on a DB where `117` already ran causes a hard `500 DataIntegrityViolationException` on the first tenant save. | **Yes** — declares and delivers idempotency. |
| 13 | `124-add-permission-catalogo-columns.sql` | Adds `t_permission.rotulo`/`descricao`/`modulo`/`ordem`/`reservada_plataforma` (Phase 124, catalogue of permissions). No backfill UPDATE — `DatabaseSeeder.seedRbac()` populates existing rows on the very next boot. | **Yes** — every statement uses `ADD COLUMN IF NOT EXISTS`. |
| 14 | `125-convert-tenant-logo-data-url-to-text.sql` | Converts `t_tenant.logo_data_url` from a Large Object (`oid`) column to plain `text`. **Existing-database only — see the stop banner.** Required only where the column is currently `oid`; without it the app fails schema validation after the `@Lob` removal deploy. | **No** — but it now refuses safely (guard), instead of destroying. |
| 15 | `126-add-tenant-role-tables.sql` | Creates `t_tenant_role` + `t_tenant_role_permission` and adds `t_role.instanciavel` (Phase 125, moldes e papeis de escritorio); no backfill — the seeder converges on the very next boot. | **Yes** — every statement uses `IF NOT EXISTS`. |
| 16 | `127-add-user-tenant-role-table.sql` | Creates `t_user_tenant_role` (Phase 126, migração de papéis existentes); no backfill — the conversion of existing users to office roles runs in Java on the next boot. `t_user_role` is not touched. | **Yes** — `CREATE TABLE IF NOT EXISTS`. |

### Verify before running `125`

`125` applies **only** if the column is still an `oid`. The script checks this itself and
aborts if the answer is wrong, but check first anyway — knowing the answer *before* you run
is what tells you whether the migration is outstanding or already done:

```sql
SELECT data_type
FROM information_schema.columns
WHERE table_name = 't_tenant' AND column_name = 'logo_data_url';
```

- `oid` → the migration applies. Run it once.
- `text` → **already migrated, or a fresh database. Do not run it.**

---

## Re-run safety

Only **6 of 16** scripts tolerate being run twice:

| Safe to re-run | Why |
|---|---|
| `74` | The `UPDATE` converges — no rows left matching. |
| `111` | `CREATE EXTENSION IF NOT EXISTS`. |
| `120b` | Explicitly designed for it: the `UPDATE` is `WHERE plano IS NULL`, and `SET DEFAULT` / `SET NOT NULL` are idempotent. |
| `124-add-permission-catalogo-columns` | Every `ALTER TABLE` uses `ADD COLUMN IF NOT EXISTS`. |
| `126-add-tenant-role-tables` | every statement uses `IF NOT EXISTS` |
| `127-add-user-tenant-role-table.sql` | the single statement uses `CREATE TABLE IF NOT EXISTS` |

The other **10 must not be re-run**. Nine of them fail loudly (duplicate table / column /
constraint / index) — annoying but safe. **`125` used to be the dangerous exception: it did
not fail, it destroyed.** Its guard (see the stop banner) now puts it in the "fails loudly"
group: a second run aborts with `GUARDA 125: ... ja e do tipo "text", nao "oid"` and changes
nothing. Verified by execution, not by reading the code.

> ### Watch out for `120`
> Its header comment talks about backfill safety and reads as reassuring. It is not a
> statement about re-running. Line 33 is:
>
> ```sql
> ALTER TABLE t_tenant ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;
> ```
>
> There is **no `IF NOT EXISTS`**. A second pass fails with
> `column "ativo" of relation "t_tenant" already exists`.
> **Safe against a populated table ≠ safe to repeat.**

---

## Known execution status

As of the last verification, on a client (existing) database, **8 scripts are outstanding**:

| File | Status |
|---|---|
| `74-cleanup-nif-documento-tipo.sql` | Pending — recorded in `.planning/STATE.md:143` |
| `117-add-tenant-plano-limite-utilizadores.sql` | Pending — recorded in `.planning/STATE.md:144` |
| `120-add-tenant-ativo.sql` | Pending — recorded in `.planning/STATE.md:145` |
| `120b-backfill-tenant-plano.sql` | Run on the **development** database only (2026-07-30). Pending everywhere else. |
| `124-add-permission-catalogo-columns.sql` | Pending — new in this phase. |
| `125-convert-tenant-logo-data-url-to-text.sql` | **No execution record anywhere.** Determine its status per database using the `information_schema` query above. |
| `126-add-tenant-role-tables.sql` | Pending — new in this phase. |
| `127-add-user-tenant-role-table.sql` | Pending — new in this phase. |

`91`, `93`, `96`, `111` and `125` have **no trace at all** in `.planning/STATE.md`. That gap
is precisely why this checklist exists: **do not treat planning documents as the record of
what has run.** Verify against the database itself.

---

## How to run

Against a Docker Compose deployment (service `postgres`, container `lexcv_postgres`):

```bash
docker compose exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v ON_ERROR_STOP=1 \
  -f - < backend/migrations/111-enable-search-extensions.sql
```

Against a directly reachable database:

```bash
psql "postgresql://USER@HOST:5432/DBNAME" \
  -v ON_ERROR_STOP=1 \
  -f backend/migrations/111-enable-search-extensions.sql
```

Rules of engagement:

1. **Back up first** on any database holding real data.
2. **One script at a time**, in the order above. Read the output of each before starting the next.
3. Use `-v ON_ERROR_STOP=1` so a failure stops instead of cascading.
4. `RAISE NOTICE` output is **not** decoration. Read every notice line.
5. Re-read the stop banner before touching `125`. Its guard will stop you if you are on the
   wrong database, but the guard is a backstop — it is not a substitute for knowing which
   database you are connected to.

---

## Adding a new migration

- Name it `<phase-number>-<kebab-description>.sql` (suffix `b`, `c`, … for follow-ups within
  the same phase, as `120b` does).
- Keep the file-header comment convention: what, why, what breaks without it, and whether it
  is idempotent.
- **Add a row to the tables in this file in the same commit.** The inventory lives here and
  nowhere else.
