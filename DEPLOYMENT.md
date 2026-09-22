# ALCv VPS Deployment Guide

Step-by-step runbook for deploying ALCv on a fresh Ubuntu/Debian VPS with automatic HTTPS via Caddy and Let's Encrypt.

## Prerequisites

- VPS running Ubuntu 22.04+ or Debian 12+
- Docker Engine 24+ and Docker Compose v2:
  ```
  apt update && apt install -y docker.io docker-compose-plugin
  ```
- A domain name (e.g. `alcv.example.com`) with an **A record** pointing to the VPS public IP
- DNS must fully propagate before starting the stack — Caddy's ACME HTTP-01 challenge requires the domain to resolve to the VPS

## Firewall

Open required ports before starting the stack. Caddy's ACME HTTP-01 challenge requires port 80 to be reachable from the internet; port 443 serves HTTPS traffic.

```bash
ufw allow 22/tcp   # SSH
ufw allow 80/tcp   # ACME challenge + HTTP redirect
ufw allow 443/tcp  # HTTPS
ufw enable
```

## Deploy Steps

### 1. Clone the repository

```bash
git clone https://github.com/tchspprtcv/lexcv.git
cd lexcv
```

### 2. Configure environment

```bash
cp .env.example .env
```

Edit `.env` — required changes:

| Variable | Value |
|----------|-------|
| `DOMAIN_NAME` | `your-actual-domain.com` |
| `POSTGRES_PASSWORD` | Strong random password |
| `JWT_SECRET` | Base64-encoded random secret, minimum 32 bytes |
| `CORS_ALLOWED_ORIGINS` | `https://your-actual-domain.com` |
| `SEED_ENABLED` | `true` on first run only to seed admin user; set `false` after |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` on first run only; `validate` from then on — see [Two-Stage Boot](#database-schema--two-stage-boot) |

Generate a secure JWT secret:
```bash
openssl rand -base64 48
```

### 3. Start the stack

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

### 4. Check Caddy logs

```bash
docker compose logs -f caddy
```

Caddy will log certificate provisioning. First startup can take 30–60 seconds while Let's Encrypt issues the certificate.

## Database Schema — Two-Stage Boot

This repository has **no automated migration runner** (no Flyway, no Liquibase). Schema evolution is driven by Hibernate `ddl-auto`, plus the hand-written scripts in `backend/migrations/` for everything `ddl-auto` cannot do. `backend/migrations/README.md` is the authoritative migration checklist — read it before every deploy.

Because of that, a new installation is brought up in **two stages**. Stage 2 is part of the installation, not an optional follow-up.

### Where these two settings actually live

Two variables drive the whole procedure — `SPRING_JPA_HIBERNATE_DDL_AUTO` and `SEED_ENABLED`. Both compose paths now read them **from `.env`**, but the two paths are otherwise different files with different commands, so first work out which one you are on.

| Deploy path | How to tell you are on it | How to change the two settings |
|---|---|---|
| `docker-compose.yml` (+ `docker-compose.prod.yml`) — the path this runbook uses | You pass **two** `-f` flags; Caddy reads `Caddyfile.prod` and the domain comes from `DOMAIN_NAME` in `.env` | **In `.env`.** Both keys are interpolated by the `backend` service (`SEED_ENABLED: "${SEED_ENABLED:-true}"`, `SPRING_JPA_HIBERNATE_DDL_AUTO: "${SPRING_JPA_HIBERNATE_DDL_AUTO:-update}"`). |
| `docker-compose.hostinger.yml` — the standalone Hostinger file | You pass **one** `-f docker-compose.hostinger.yml`; images are pulled from `ghcr.io/tchspprtcv/lexcv`, and the Caddy config is generated inline by the service `entrypoint` with the domain **hardcoded** to `alcv.tech` | **In `.env`.** Same two keys, same defaults — see [Hostinger path](#hostinger-path-docker-composehostingeryml) below for the exact commands. |

**On both paths the defaults are the stage-1 values: if you set neither key, you get `SEED_ENABLED=true` and `ddl-auto=update`.** That is deliberate — an install only leaves stage 1 when an operator changes `.env` on purpose, never as a side effect of a deploy. It also means an install that nobody has touched is *still in stage 1* and re-seeds on every restart.

> Compose reads `.env` from the project directory for `${VAR}` interpolation on **both** files. That is a separate mechanism from `env_file:` (which neither file uses) — the absence of `env_file:` does not stop `.env` from working. It is the same mechanism that already supplies `POSTGRES_PASSWORD` and `JWT_SECRET` to the Hostinger file.

No compose file, `Dockerfile`, or workflow sets `SPRING_PROFILES_ACTIVE`, so the backend always runs on the **default** profile. Two consequences: `backend/src/main/resources/application-prod.yml` — which pins `ddl-auto: validate` — is **never loaded** and is effectively a dead file today, and the effective default therefore comes from `application.yml`, which hardcodes `ddl-auto: update`. In other words, **if nothing sets the env var, the install is on `update`.** Setting `SPRING_JPA_HIBERNATE_DDL_AUTO` in `.env` is currently the *only* thing that puts an install on `validate`.

### Stage 1 — First boot: create the schema, seed the data

| Variable | Value |
|----------|-------|
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `update` |
| `SEED_ENABLED` | `true` |

Set them in `.env` (or leave them unset — these are the defaults on both paths). Start the stack. Hibernate creates the schema from the JPA entity model; `DatabaseSeeder` populates roles, permissions, and the initial admin user.

Then apply any script in `backend/migrations/` that stage 1 cannot produce on its own. Hibernate `ddl-auto` never installs PostgreSQL extensions, never creates an index that is not declared on an entity, and never changes the type of a column that already exists. `backend/migrations/README.md` lists which scripts a fresh install still needs.

### Stage 2 — Steady state: validate, never mutate

Immediately after stage 1 succeeds, flip both values in `.env` and restart the backend:

| Variable | Value |
|----------|-------|
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | `validate` |
| `SEED_ENABLED` | `false` |

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d backend
docker compose -f docker-compose.yml -f docker-compose.prod.yml logs -f backend
```

#### Confirm the setting took effect — do this before trusting the boot

A successful boot only proves the schema is intact **if the install is really on `validate`**. On `update` the boot always succeeds, because Hibernate silently adds whatever is missing — so a green boot with the wrong setting proves nothing. Confirm the setting first:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml exec backend printenv SPRING_JPA_HIBERNATE_DDL_AUTO
```

| Output | What it means |
|---|---|
| `validate` | Confirmed. Proceed — the boot below is meaningful. |
| `update` | Still stage 1. Either `.env` was not changed, or the backend was not recreated after changing it. |
| nothing (empty / non-zero exit) | The variable is **not set in the container** at all, so the install falls back to `application.yml`'s `ddl-auto: update`. Both compose files now always set it (defaulting to `update`), so this means the running container predates that change, or the stack is being driven by some other compose file — re-read [Where these two settings actually live](#where-these-two-settings-actually-live). |

Same check for the seeder: `... exec backend printenv SEED_ENABLED` must print `false`.

Compose can also show you the merged value without starting anything:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml config | grep -E 'SEED_ENABLED|DDL_AUTO'
```

**Once `printenv` says `validate`, the installation is not complete until the backend has also started successfully.** Those two facts together — and only together — are the proof that the live schema matches the entity model.

Leaving an installation permanently on `update` hides drift instead of reporting it: every boot silently adds whatever is missing, so nothing is ever flagged — and `update` still will not repair a column whose *type* changed, which then fails much later and much more expensively.

If the backend fails to start under `validate`, the log names the exact object:

```
Schema-validation: missing table [t_notificacao]
Schema-validation: missing column [ativo] in table [t_tenant]
Schema-validation: wrong column type encountered in column [logo_data_url] in table [t_tenant]
```

Fix it with the corresponding script in `backend/migrations/`. Do **not** switch back to `update` to make the error go away.

### Phase 126 — conversão de papéis de escritório e verificação de deriva zero

Four questions an operator asks at 3am after this deploy.

**1. What to run, and when.** `backend/migrations/127-add-user-tenant-role-table.sql` runs **before** the install picks up the deploy that introduces the `User.tenantRoles` field, for the same reason as every other script in that directory: an install running `validate` refuses to start without the table. On an install still in stage 1 (`ddl-auto=update`) the script is redundant but harmless.

**2. Where the data conversion happens.** Not in the script. On the next boot, in Java, by a convergent, idempotent service — the same category of work as `DatabaseSeeder.seedRbac()`, which this guide already documents elsewhere as running unconditionally **even in production with `SEED_ENABLED=false`** (it is the first statement of `CommandLineRunner.run()`, ahead of the `seedEnabled` gate). Practical consequence, stated explicitly: **an operator does not "run the role migration"; it runs itself on the first boot after the deploy.** Booting the same release twice does not duplicate anything.

**3. What a boot failure after this deploy means.** This is the part unique to this phase: the zero-drift verification (MIGR-02) compares, user by user, the set of effective permissions before and after the conversion, and **aborts the boot** if they diverge, naming the user's email and the diverging permissions in the log. A backend that fails to start after this deploy, with that message in the log, is not an infrastructure failure — it is the safety net doing its job. The correct response is to **not force the boot**: the conversion runs inside a transaction, so a failure never leaves partially-converted data.

**4. How to roll back.** `t_user_role` stays populated — nothing in this phase deletes or alters it. Rolling back is a code-deploy rollback, which goes back to reading global roles; the rows in `t_user_tenant_role` become orphaned and harmless. Restoring a backup is **not** required, and there is **no `DROP COLUMN` to undo.**

**5. There IS a narrow exposure window on every boot, and it fails closed, not open.** Spring Boot starts the embedded servlet container during `ApplicationContext` refresh (`finishRefresh()`, inside `AbstractApplicationContext.refresh()`) — which completes **before** `SpringApplication.callRunners()` invokes any `CommandLineRunner`, including `DatabaseSeeder` and, after it, `MigracaoPapeisRunner` (Plan 03's explicit `@Order` chain, `LOWEST_PRECEDENCE - 100` then `LOWEST_PRECEDENCE` — see the ordering comment on `DatabaseSeeder`). That means Tomcat is already accepting connections while `MigracaoPapeisRunner.run()` — and therefore `MigracaoPapeisEscritorioService.migrar()`, one `@Transactional` method — is still executing, or on a fresh deploy hasn't started yet. Until that transaction commits, `t_tenant_role` for any tenant still awaiting its first conversion is exactly as populated as it was before this deploy: empty for tenants provisioned before Phase 126, complete immediately for any tenant provisioned by `SetupService.provisionTenant`/`initializeSystem` after Phase 126 (both now instantiate the founding admin's `TenantRole` inline, at provisioning time, not on a later boot — see 126-REVIEW.md WR-01/IN-01).

Concretely, during that window: a request against a not-yet-converged tenant that reaches `ResolucaoPapeisService` sees `tenantRoles` empty, exactly the same "no correspondence yet" case that a genuinely un-converted tenant already produces outside the window — read paths (`JwtAuthenticationFilter`, `listUsers`, `/auth/me`) fail safe to global-role resolution, the same behavior they'd have with this phase absent entirely. `AdminController.createUser`/`updateUser` calling `resolverPapeisDeEscritorio` in that same window for such a tenant get the same "none mapped" empty `Set` — never a partial one (126-REVIEW.md CR-01 made partial mapping a hard `409`, not a silent write, precisely so this window can never turn into a truncated-permissions bug; it can only ever under-populate `tenantRoles`, which the read side already treats as "stay on global roles"). What this window is **not**: a privilege-escalation or lockout risk — nothing observes more or less authority than the pre-Phase-126 baseline for that tenant during the window, and the platform admin's own resolution path never depends on any `MigracaoPapeisEscritorioService` output. What it **is**: the same class of risk already accepted and documented for the seeder itself in Phase 124's WR-01 (`backend/migrations/124-add-permission-catalogo-columns.sql`) — typically sub-second locally, real on a slower production boot, and closed operationally (not in code) by a readiness probe the load balancer waits on before routing traffic, which remains out of scope here exactly as it was there.

### Phase 128 — auditoria de papéis

Run `backend/migrations/128-add-audit-log-detalhe.sql` before the deploy on any install running `SPRING_JPA_HIBERNATE_DDL_AUTO=validate` — it adds the nullable `t_audit_log.detalhe` column the `AuditLog` entity now maps, and `validate` refuses to start without it. On an install still running `update`, the script is redundant but harmless (`ADD COLUMN IF NOT EXISTS`). There is no data conversion: existing rows keep `detalhe = NULL` forever, only new RBAC audit events populate it. See `backend/migrations/README.md` for the authoritative migration inventory — do not restate the list here.

### Hostinger path (`docker-compose.hostinger.yml`)

The Hostinger install is a **single** compose file — no `docker-compose.prod.yml` override — so every command above changes. The procedure is otherwise identical: the two keys come from `.env` in the same directory as the compose file.

This install has historically run on the stage-1 literals, which means it re-seeds on every restart and has never been validated against the entity model. Moving it to stage 2 is a deliberate, one-time operator action.

1. Confirm where it stands today:

   ```bash
   docker compose -f docker-compose.hostinger.yml exec backend printenv SPRING_JPA_HIBERNATE_DDL_AUTO SEED_ENABLED
   ```

2. Apply every outstanding script in `backend/migrations/` **first** — read `backend/migrations/README.md` before pasting anything; at least one script is destructive on a database it was not meant for. Under `update` the schema may already have drifted, so this step is what makes `validate` survivable.

3. Set stage 2 in `.env` next to the compose file:

   ```
   SPRING_JPA_HIBERNATE_DDL_AUTO=validate
   SEED_ENABLED=false
   ```

4. Check the merged config before restarting anything:

   ```bash
   docker compose -f docker-compose.hostinger.yml config | grep -E 'SEED_ENABLED|DDL_AUTO'
   ```

   Expected: `SEED_ENABLED: "false"` and `SPRING_JPA_HIBERNATE_DDL_AUTO: validate`. If it still prints `true`/`update`, the `.env` is not in the directory Compose treats as the project directory — fix that before continuing.

5. Restart only the backend and watch it:

   ```bash
   docker compose -f docker-compose.hostinger.yml up -d backend
   docker compose -f docker-compose.hostinger.yml logs -f backend
   ```

6. Confirm inside the container, then confirm the boot succeeded — both, in that order:

   ```bash
   docker compose -f docker-compose.hostinger.yml exec backend printenv SPRING_JPA_HIBERNATE_DDL_AUTO
   ```

To roll back, remove the two keys from `.env` (or set them to `update`/`true`) and repeat step 5 — the compose defaults return the install to stage-1 behaviour unchanged.

### Every subsequent deploy

Stays on `validate` with `SEED_ENABLED=false`. If a release introduces new entities or new columns, run its migration script from `backend/migrations/` **before** starting the new image.

Re-run the `printenv` check after any change to `.env` or to a compose file — it is the only cheap way to notice that an install has drifted back to `update`.

## Verify

Once the stack is up and the certificate is provisioned:

```bash
curl -I https://your-actual-domain.com/api/v1/setup/status
```

Expected: `HTTP/2 200` with a JSON body. If you see a redirect loop or TLS error, check that:
1. DNS A record points to the correct IP
2. Ports 80 and 443 are open in the firewall
3. `DOMAIN_NAME` in `.env` matches the DNS record exactly

## Image Registry (CI/CD)

`docker-compose.prod.yml` **always** uses pre-built images rather than building on the VPS — it sets `image:` on `backend`, `frontend`, and `webpage`. `REGISTRY` and `IMAGE_TAG` only choose *which* image; both have defaults, so setting neither gives you `ghcr.io/tchspprtcv/lexcv/<service>:latest`.

```
REGISTRY=ghcr.io/your-org
IMAGE_TAG=v1.0.0
```

`docker-compose.hostinger.yml` ignores both variables — it hardcodes `ghcr.io/tchspprtcv/lexcv/<service>:latest`.

Pull and restart:

```bash
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Updates

```bash
git pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml pull
docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d
```

## Certificate Renewal

Caddy renews Let's Encrypt certificates automatically — no cron job or manual intervention needed. Certificates are stored in the `caddy_data` Docker named volume. **Do not delete this volume** or certificates will be re-issued from scratch (subject to Let's Encrypt rate limits).

## GitHub Actions — what the pipeline actually does

`.github/workflows/deploy.yml` is the only workflow in the repository. Despite its name (`CI/CD — Build and Deploy`), **it does not deploy anything.** It has exactly two jobs and no step that touches a server:

| Job | Runs on | What it does |
|---|---|---|
| `test` | every push and every PR to `master` | `mvn -B verify` in `backend/` (Surefire unit tests + Failsafe Testcontainers integration tests), then `mvn -B spotbugs:check` (SpotBugs + FindSecBugs). OWASP `dependency-check` is deliberately not run. |
| `build-and-push` | `needs: test`, and only `if: github.event_name == 'push'` | Builds the `backend`, `frontend`, and `webpage` images for `linux/amd64` with Buildx and pushes them to `ghcr.io/tchspprtcv/lexcv`. |

The trigger branch is **`master`**, not `main` — both the `push` and `pull_request` triggers, and the `push: ${{ github.ref == 'refs/heads/master' }}` condition on each build step.

**Putting a new image into service is a manual step.** Nothing in CI connects to the VPS: there is no SSH action, no `VPS_HOST`/`VPS_USER`/`VPS_SSH_KEY`/`VPS_PORT`/`GHCR_PAT` secret in use, and no remote `docker compose` invocation. After CI has pushed the images, an operator must log into the host and pull them — see [Updates](#updates), or the Hostinger equivalent:

```bash
docker compose -f docker-compose.hostinger.yml pull
docker compose -f docker-compose.hostinger.yml up -d
```

### Required secrets

Only `GITHUB_TOKEN` — automatically provided by GitHub Actions, granted `packages: write` by the workflow's own `permissions:` block. **No repository secrets need to be configured for this workflow to run.**

### Image tags

| Event | Run tests | Build images | Push to GHCR | Deploy |
|---|---|---|---|---|
| Push to `master` | yes | yes | yes | **no — manual** |
| Pull request to `master` | yes | no | no | no |

Images are tagged with both `:latest` and the git SHA (`:${{ github.sha }}`). `docker-compose.hostinger.yml` pins `:latest`; `docker-compose.prod.yml` uses `${IMAGE_TAG:-latest}`.

### Before deploying to a host for the first time

1. Docker Engine and the Compose plugin are installed on the host.
2. The host can pull from `ghcr.io/tchspprtcv/lexcv` (public images need no login; private ones need `docker login ghcr.io` with a token carrying `read:packages`).
3. The deploy directory contains the compose file you intend to use plus a valid `.env` (see `.env.example`) — for the two-file path, also `docker-compose.prod.yml` and `Caddyfile.prod`.
4. You know which stage the target install is in — see [Database Schema — Two-Stage Boot](#database-schema--two-stage-boot). A first-ever deploy runs stage 1 and then stage 2; every later deploy assumes the install is already in stage 2 (`SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, `SEED_ENABLED=false`) and that any pending script in `backend/migrations/` has been applied first. Deploying Phase 126 (office-role migration)? See [Phase 126 — conversão de papéis de escritório e verificação de deriva zero](#phase-126--conversão-de-papéis-de-escritório-e-verificação-de-deriva-zero).
