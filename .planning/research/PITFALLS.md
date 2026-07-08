# Domain Pitfalls

**Domain:** Adding persisted in-app notifications (new entity, RBAC/relationship-scoped targeting, daily `@Scheduled` re-scan job, brand-new entity-reassignment endpoint) to an existing multi-tenant Spring Boot 3.4.1 / Next.js 16 legal practice management app
**Researched:** 2026-07-08
**Milestone:** v2.10 Notificações e Alertas
**Confidence:** HIGH for all codebase-grounded findings (verified by reading the actual current source, cited by file/line below); HIGH/MEDIUM for external Spring/TanStack claims (verified against official docs and the Spring Framework issue tracker, cited in Sources)

## Confidence note

Most findings below are derived directly from reading this repository's actual code (`ResourceController.java`, `ParecerController.java`, `Processo.java`, `Evento.java`, `Prazo.java`, `Honorario.java`, `AuditLog.java`, `User.java`, `UserPrincipal.java`, `UserRepository.java`, `HonorarioRepository.java`, `providers.tsx`, `api.ts`, `permissions.ts`, `docker-compose.prod.yml`, and `.planning/PROJECT.md`'s Key Decisions log), not generic notification-system advice. Line numbers are current as of 2026-07-08 and will drift — treat them as pointers, not permanent anchors. Where a claim rests on Spring Framework or TanStack Query behavior rather than this codebase, it is verified against official docs/issue tracker and cited explicitly.

## Critical Pitfalls

### Pitfall 1: The daily job cannot reuse this codebase's `getTenantId()` pattern — guaranteed `NullPointerException`

**What goes wrong:**
Every single existing endpoint in `ResourceController.java` derives the current tenant via a private helper:

```java
private UUID getTenantId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
    return principal.getTenantId();
}
```
(`ResourceController.java:115-119`)

This is called 100+ times across the controller and is the *only* tenant-resolution idiom that exists anywhere in this codebase. A `@Scheduled` method runs on a background thread with **no HTTP request, no JWT, no `Authentication` in the `SecurityContextHolder`** — `SecurityContextHolder.getContext().getAuthentication()` returns `null` on that thread, so `auth.getPrincipal()` throws an immediate NPE. Since this is the *only* pattern developers have ever written in this codebase to get "the current tenant," the natural (and wrong) move when writing the new job is to copy-paste or delegate into existing private helpers/methods that assume this context — anything the job calls transitively that touches `getTenantId()` will crash on first invocation, on every scheduled run, silently (Spring swallows the exception — see Pitfall 4).

**Why it happens:**
This is the first background/non-request-driven code path ever introduced into LexCV (confirmed: zero `@Scheduled`, `@Async`, or `TaskScheduler` usage anywhere in `backend/src/main/java`). Every other piece of business logic in the app was written under the unstated assumption "there is always exactly one logged-in tenant for the duration of this call," which is true for every HTTP request but false for a batch job that must iterate **all** tenants.

**How to avoid:**
The job must NOT call `getTenantId()` or any method that transitively depends on `SecurityContextHolder`. Instead:
- Loop explicitly over `tenantRepository.findAll()` (bare `JpaRepository<Tenant, UUID>`, no `ativo`/status column exists on `Tenant` — every row is eligible, no filter needed).
- Pass `tenantId` as an explicit method parameter through every layer of the job (repository query methods already take `tenantId` as a param throughout this codebase — e.g. `findByTenantId(tenantId)` — so this is consistent with existing repository-layer conventions; it's only the controller-layer `getTenantId()` shortcut that must not be reused).
- If any shared logic (e.g. the new consolidated risk-computation service) is extracted from the controller for reuse by both the job and the existing endpoints, make sure that extraction takes `tenantId` as a parameter rather than reading it internally — do not let the shared service silently depend on `SecurityContextHolder`.

**Warning signs:**
- Any new `@Service`/`@Component` class for the job that imports `SecurityContextHolder` or `UserPrincipal`.
- A job class that only has a no-arg `run()`/`scan()` method with no `tenantId` parameter anywhere in its call chain.
- Local manual testing of the job only ever done via an authenticated HTTP endpoint that triggers it synchronously (masking the missing-SecurityContext failure mode, since that path *does* have an Authentication).

**Phase to address:**
The phase that introduces the daily scheduled job (prazos/honorários alert scan). Should be flagged for extra plan-time review since it is the first background-thread code path in the app.

---

### Pitfall 2: `Notificacao` is the first per-recipient-private entity in an app where every other entity is tenant-shared — recipient scoping is easy to omit entirely

**What goes wrong:**
Every existing entity in LexCV (Cliente, Processo, Documento, Honorario, Evento, ParecerSolicitacao...) is **tenant-shared**: any user in the tenant with the right permission scope (`clientes:view`, `processos:view`, etc.) sees *all* rows of that type in the tenant. The authorization pattern baked into 20+ endpoints is exactly one check: `if (x == null || !x.getTenantId().equals(getTenantId())) return 404`. There is no precedent anywhere in this codebase for "this row belongs to a specific *user*, not just a specific *tenant*."

`Notificacao` breaks this assumption: two ADVOGADOs in the same tenant must see *different* notification inboxes even though they share every other permission scope. If `GET /notificacoes` is implemented by pattern-matching the familiar shape ("apply `@PreAuthorize("hasAuthority('notificacoes:view')")`, then `notificacaoRepository.findByTenantId(tenantId)`"), it will pass every existing code-review heuristic in this codebase (permission check present, tenant filter present) while leaking every user's notifications to every other user in the tenant — a real, silent, cross-user data leak that looks identical to "done correctly" by this project's own established review pattern.

**Why it happens:**
Reviewers and implementers pattern-match against the 20+ existing tenant-only checks in `ResourceController.java` and consider "tenant-scoped" to be the complete authorization contract, because it always has been for every other entity in this app.

**How to avoid:**
Every read/write on `Notificacao` needs a **second** filter dimension beyond tenant: `WHERE tenant_id = ? AND destinatario_user_id = ?` (or the ADMIN fan-out equivalent — see Pitfall 3). Write this as an explicit repository method, e.g. `findByTenantIdAndDestinatarioIdOrderByCreatedAtDesc(tenantId, userId)`, and make the "mark as read" endpoint verify **both** tenant *and* recipient ownership before mutating (`!n.getTenantId().equals(tenantId) || !n.getDestinatarioId().equals(currentUserId)` → 404/403), not just tenant. Add this as an explicit item in the phase's plan/success-criteria, since it won't be caught by copy-pasting the existing review checklist.

**Warning signs:**
- A `NotificacaoRepository` method named only `findByTenantId(...)` with no user-id parameter.
- A code review that says "tenant check present" without a second sentence about recipient check.
- Manual test only performed while logged in as a single user (never verifying user A cannot see/mark-read user B's notifications within the same tenant).

**Phase to address:**
Notification infrastructure phase (entity + API de listagem/marcar-lida). This is the foundation every later phase builds on — get the two-dimensional scoping right here or every consumer inherits the leak.

---

### Pitfall 3: ADMIN broad visibility + per-recipient read state is an architectural tension that needs an explicit design decision, not an afterthought

**What goes wrong:**
The targeting rule (per `PROJECT.md` Key Decisions) is "responsável do processo / advogado do parecer / equipa do cliente + **ADMIN**." ADMIN's extra permissions are added *dynamically* at login/JWT-issuance time in `UserPrincipal.create()` (`config/UserPrincipal.java:33-44`), not stored per-row anywhere. This creates a genuine fork in how `Notificacao` can implement "ADMIN also sees this":

- **Query-time** ("`WHERE destinatario_id = ? OR (tenant has ADMIN role check)`"): cheap to write, but then *read state* ("lida"/unread) cannot live on the notification row itself — if 3 ADMINs exist in a tenant and the row is shared, one ADMIN marking it "lida" would either (a) incorrectly mark it read for the other two ADMINs too (shared mutable state on a conceptually-personal action), or (b) require a *second* join table just for read-state per (notification, admin-user) pair — which is really the fan-out model in disguise, just implemented lazily/inconsistently.
- **Fan-out-at-creation** (create one row per concrete recipient, including one row per current ADMIN, at the moment the triggering event happens): each row has its own independent read state, consistent with every other row in the table. This requires enumerating admins at creation time — which this codebase already has a one-line repository method for: `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` (`UserRepository.java:16-17`, already exists, unused until now).

Picking the query-time approach because it seems simpler will work fine in initial testing (single-admin tenants, e.g. the seeded default tenant) and only visibly break once a tenant has 2+ ADMIN users independently reading/dismissing the same alert — an easy gap to miss in a milestone whose stated scope explicitly excludes broader team-based targeting and notification preferences (i.e., nobody is asking for this edge case to be raised, so it's easy to silently under-design it).

**Why it happens:**
The targeting rule bundles two different recipient models ("this one specific FK" vs "this dynamic role-derived set") into a single sentence in the requirements, but they need different implementations to give both audiences a coherent independent unread/read experience.

**How to avoid:**
Decide explicitly and document as a Key Decision: fan out one `Notificacao` row per concrete recipient at creation time (responsável/advogado/equipa-do-cliente FK resolves to 1 row; `findByTenantIdAndRoleName(tenantId, "ADMIN")` resolves to N rows, one per current admin). Accept that an admin promoted *after* the notification was created won't retroactively see it (consistent with this milestone's explicit no-retroactive-preferences philosophy) — this is the same category of simplicity trade-off the project already made elsewhere (e.g., `Honorario.valorTotal` never auto-populated from `Cliente.honorariosPropostos`).

**Warning signs:**
- Any SQL/JPQL for `Notificacao` containing `OR` logic against a live role check instead of a stored `destinatario_id`.
- A "mark as read" implementation that updates a `lida` column directly on `t_notificacao` for a notification that could be shared by multiple recipients.

**Phase to address:**
Notification infrastructure phase (entity design), before any alert-generation phase depends on the shape.

---

### Pitfall 4: "Notify on every job run" instead of "notify on threshold crossing" — no persisted last-known-state means the job re-fires daily for every still-critical item

**What goes wrong:**
The milestone's decision is explicit: re-notify on threshold crossing (ok → próximo → vencido), via a daily job, not notify-once. If the job's "should I create a notification?" check is only `computeRisco(...) != "ok"` (i.e., "is this currently critical"), it will create a **new** notification every single day for every prazo/honorário that remains in `proximo` or `vencido` state — a processo sitting at "vencido" for two weeks would generate 14 duplicate-content notifications, one per day, none of which represents an actual state change. This is functionally indistinguishable from spam even though no single run is "buggy" in isolation — the bug is the *absence* of a persisted "what did I last notify this recipient about this entity at" fact.

**Why it happens:**
`computeRisco()` (`ResourceController.java:1397-1404`) and every place that consumes it today is **stateless and read-time** — it recomputes fresh on every HTTP request and nothing persists "the last computed value," because until now nothing needed to compare "then" vs "now." Porting this same stateless mental model unmodified into a job whose entire purpose is "detect a change since last time" is the natural but wrong move.

**How to avoid:**
Track last-notified state explicitly, e.g. a column/table keyed by `(tenant_id, entidade_tipo, entidade_id, destinatario_id) → last_risco_notificado`. Each day, the job recomputes current risco (via the *new consolidated* function this milestone is supposed to create) and only creates a `Notificacao` row when `current_risco != last_risco_notificado`, then updates the tracking record. This is an edge-triggered design (react to the transition), not level-triggered (react to the current level) — the same distinction monitoring/alerting systems make between "alert on state change" vs. "re-alert every evaluation."

**Warning signs:**
- The job's insert condition reads only the current computed risco with no comparison to a previously stored value.
- No new column/table anywhere that stores "last known risco level per (entity, recipient)".
- QA only tests "one day," never simulates "run the job two days in a row against unchanged data" (which is the exact scenario that exposes this bug).

**Phase to address:**
Daily scheduled job phase (prazos de processos e calendário crítico). This is the single most important behavioral contract for that phase's success criteria.

---

### Pitfall 5: Wrong `@Scheduled` trigger type (`fixedRate`/`fixedDelay` vs `cron`) causes the "daily" job to re-fire on every deploy

**What goes wrong:**
This project deploys via GitHub Actions → GHCR → SSH → `docker compose up` on a single-instance VPS (confirmed: `docker-compose.prod.yml` sets no `replicas:`, only resource limits, and `restart: unless-stopped`), and deploys happen frequently during active development (this repo's own commit history shows multiple phases shipped per day). `@Scheduled(fixedRate = ...)` / `@Scheduled(fixedDelay = ...)` compute their next run relative to **application startup**, not wall-clock time — so `fixedRate = 86400000` ("every 24h") actually means "24h after this specific process started," and **every container restart resets that clock and fires the job again almost immediately** (default `initialDelay` is 0). Given `restart: unless-stopped` plus routine CI/CD redeploys, a `fixedRate`-based "daily" job would in practice fire multiple times per day — reproducing exactly the "duplicate notifications on job re-run" symptom, but the root cause here is a scheduling-configuration mistake, not concurrency.

**Why it happens:**
`fixedRate`/`fixedDelay` are the two attributes most Spring tutorials show first (simpler syntax than a cron expression), and "run once a day" is easy to mis-translate as "every 86400000ms" without considering what happens across restarts.

**How to avoid:**
Use `@Scheduled(cron = "0 0 3 * * *")` (wall-clock-anchored) for the daily scan, not `fixedRate`/`fixedDelay`. Cron-based scheduling only fires at the specified time of day regardless of when the process last started, so a redeploy at 14:00 does not cause an extra 03:00 run. This does not remove the need for idempotent inserts (Pitfall 4's state-tracking already covers "did I already notify for this exact state"), but it removes the single most likely real-world trigger of duplicate runs in *this specific deployment topology*.

**Warning signs:**
- `@Scheduled(fixedRate = ...)` or `@Scheduled(fixedDelay = ...)` used for anything described as "daily."
- No test/observation of "does the job fire again if I restart the container mid-day."

**Phase to address:**
Daily scheduled job phase. Cheap to get right up front, expensive to diagnose after the fact (looks like random duplicate spam correlated with deploys, not obviously a scheduling bug).

---

### Pitfall 6: An uncaught exception inside the job silently stops it from ever running again — with no error surfaced anywhere

**What goes wrong:**
Community consensus and Spring's own issue tracker confirm: if a `@Scheduled` method throws an uncaught exception, the task scheduler does not simply log-and-continue by default in every configuration — it can stop rescheduling that task entirely, with no exception visible outside the background thread (nothing propagates to a controller, nothing produces an HTTP 500, nothing shows up as "the app crashed"). Given this job's job is to iterate every tenant, every processo/prazo/honorário, one bad row (a null `dataAcordo`, an orphaned `processo_id` with no matching Processo, a Prazo with a null `responsavelId` and no ADMIN fallback resolved) anywhere in any tenant can silently kill all future notification generation for **every** tenant, and nobody will notice until someone asks "why did notifications stop appearing three weeks ago."

**Why it happens:**
This is the first `@Scheduled` job in the app, so there is no existing convention here for "wrap scheduled work defensively." Every existing piece of code in this app runs inside an HTTP request/response cycle, where an uncaught exception is caught by Spring's default exception handling and turned into a visible 500 response — developers have never had to think about "what happens to an exception with no HTTP response to attach to."

**How to avoid:**
Wrap the entire job body in a top-level `try/catch (Exception e)` that logs with full context (tenant id, entity id) and continues to the next tenant/entity rather than letting one bad row abort the whole run. Prefer per-tenant (or even per-entity) try/catch boundaries inside the loop so one tenant's bad data doesn't prevent notifications for every other tenant. Log at `ERROR` level with enough context to be actionable, and treat "the job produced zero output for N consecutive days across all tenants" as a symptom worth a smoke-test/health-check, since there is no `@Scheduled`-specific alerting anywhere in this app today.

**Warning signs:**
- Job code with no top-level try/catch.
- A single loop over "all tenants x all processos" with no per-iteration exception isolation — one throw anywhere aborts everything after it in that run, and (per the above) possibly all future runs too.

**Phase to address:**
Daily scheduled job phase — this is a plan-time review item ("does the job survive one bad row"), not something that shows up in isolated unit tests of the happy path.

---

### Pitfall 7: N+1 query pattern — this exact codebase already contains both the anti-pattern to avoid and the correct pattern to copy, right next to each other

**What goes wrong:**
`GET /honorarios` (no `processo_id` filter) currently does exactly this (`ResourceController.java:2568-2575`):

```java
List<Processo> tenantProcs = processoRepository.findByTenantId(tenantId);
List<Honorario> response = new ArrayList<>();
for (Processo p : tenantProcs) {
    response.addAll(honorarioRepository.findByProcessoId(p.getId()));
}
```

This is a live, committed, textbook N+1 (one query for processos, then one additional query *per processo* for its honorário). If the new daily job's "scan honorários for aging alerts" logic is modeled on this existing endpoint — the only place in the codebase today that already does "get all honorários for a tenant" — it inherits the N+1 for every tenant, every run. At a handful of tenants with dozens of processos this is invisible in dev; it will show up as the job's wall-clock time growing linearly with total processo count across all tenants once real data accumulates.

**Why it happens:**
Nobody has needed "all honorários for a tenant" outside a single processo before, so the only prior art for "loop over processos to reach a related table" happens to be the wrong pattern. A developer skimming the codebase for "how do we usually list financeiro data for a tenant" will find this exact loop first.

**How to avoid:**
Copy the *correct* pattern instead — it already exists 500 lines earlier in the same file, for the same problem shape (`ResourceController.java:896-911`, comment: `// Batch-load responsáveis to avoid N+1 queries`): fetch the whole collection once with a single `findByTenantId`/`findByProcessoIdIn(Collection<UUID>)`-style batch query, then group into a `Map<UUID, List<X>>` in Java, and look up per-processo from the map instead of re-querying. `HonorarioRepository` currently only exposes `findByProcessoId(UUID)` (singular) — add a `findByProcessoIdIn(Collection<UUID> processoIds)` batch method for the job (and consider fixing the existing `listHonorarios` fallback loop at the same time, since it's the same underlying gap, though that's arguably out of this milestone's scope unless it becomes a blocker).

**Warning signs:**
- Job code containing a `for` loop over processos with a repository call inside the loop body for prazos, honorários, or users.
- Any use of `honorarioRepository.findByProcessoId(...)` inside a loop rather than a single `findByProcessoIdIn(...)` call outside it.

**Phase to address:**
Daily scheduled job phase. Also worth a one-line callout in that phase's plan: "do not model this on `listHonorarios`'s no-filter branch."

---

### Pitfall 8: Mixed entity ID types and non-existent `tenant_id` columns break a naive polymorphic `Notificacao.entidade_id` design

**What goes wrong:**
The six alert types reference entities with **inconsistent primary key types**: `Processo`, `Prazo`, `Documento`, `ParecerSolicitacao`, `Cliente` use `UUID` (`GenerationType.UUID`); `Evento`, `Honorario`, `Facto` use `Integer` (`GenerationType.IDENTITY`). A `Notificacao` entity that tries to store a single typed FK column (e.g. `UUID entidadeId`) cannot represent an alert about an `Evento` or `Honorario` without a lossy cast/parallel column. Separately, several of these related tables have **no `tenant_id` column of their own** — `Honorario`, `Facto`, `Decisao`, `Testemunha`, `Parte` all rely on *transitive* tenant isolation via their parent `Processo.tenant_id` (an explicit, documented Key Decision from prior phases, chosen to avoid duplicating the column). A `Notificacao` repository query that tries to filter directly by an assumed `tenant_id` column on whatever `entidade_tipo` it's joining against will not compile/work uniformly across alert types, or — worse — someone "fixes" the compile error by joining on the wrong table and silently drops the tenant filter for that alert type specifically.

**Why it happens:**
This is the first entity in the app that needs to reference *any* of six different entity types generically. Every existing FK relationship in this codebase points at exactly one fixed target type with a known ID type — there is no existing "polymorphic reference" pattern to reach for except one: `AuditLog` (`models/AuditLog.java`), which already solved exactly this problem with `@Column(name = "entidade_id") private String entidadeId;` (a string, accommodating both UUID and Integer PKs) plus a separate `entidadeTipo` discriminator string, and a nullable `processoId` for cases where the audited entity isn't itself a processo.

**How to avoid:**
Model `Notificacao` the same way `AuditLog` already does: `String entidadeId` (not a typed UUID/Integer column) + `String entidadeTipo` discriminator (`"processo" | "prazo" | "documento" | "honorario" | "parecer" | "fase"`), plus `tenantId` and `destinatarioId` columns owned directly by `Notificacao` itself (do not try to derive tenant scoping transitively through the referenced entity at query time — store it directly on the notification row, since the notification's own tenant is always known at creation time regardless of what it points to).

**Warning signs:**
- A `Notificacao` entity with a single `UUID entidadeId` (or `Integer entidadeId`) column instead of a `String`.
- Any repository/query on `Notificacao` that tries to join to "the referenced entity" generically to re-derive `tenant_id`, rather than reading `Notificacao.tenantId` directly.

**Phase to address:**
Notification infrastructure phase (entity design) — this is a schema decision that's expensive to change once alert-generation phases depend on the shape.

---

### Pitfall 9: No DB-level uniqueness backs the idempotency check — and this project has no Flyway/Liquibase, so a forgotten migration script means the constraint silently doesn't exist in prod

**What goes wrong:**
This codebase's only existing idempotency tool is an **in-process** `synchronized (XRepository.class)` check-then-act block, used three times today (`ClienteRepository.class` for `numero_cliente`, `FactoRepository.class` for `ordem`, `ParecerVersaoRepository.class` for `numero_versao`). This protects against races *within a single JVM*, but is not a substitute for a real DB constraint, and this project's own commit history shows it explicitly learned this lesson already: the `Honorario` and `Facto` unique constraints were added specifically because "the application-level check-then-act idempotency check... does not protect against a genuine race condition between concurrent requests" (`PROJECT.md` Key Decisions, referencing `backend/migrations/81-add-facto-ordem-unique-constraint.sql` and `82-add-honorario-processo-unique-constraint.sql`). Backend uses `ddl-auto=update` in dev (new constraints appear automatically) but `ddl-auto=validate` in prod (constraints must already exist in the real database, or the app fails to start / silently doesn't enforce them if validation is loose) — and there is **no Flyway/Liquibase**, so any new unique constraint needed on `Notificacao` (e.g. `(tenant_id, destinatario_id, entidade_tipo, entidade_id, risco_notificado)`) requires a hand-written, manually-run SQL script following the existing `backend/migrations/NN-description.sql` numbering convention. If this step is skipped or forgotten during deployment, dev/local testing (where `ddl-auto=update` creates it automatically) will look completely fine, while prod silently has no constraint at all — the exact gap the project already hit and fixed twice before.

**Why it happens:**
`ddl-auto=update` in dev masks the absence of a migration script; the bug is invisible until someone deploys to prod, and even then it doesn't fail loudly (prod just accepts duplicate-notification inserts it shouldn't, rather than throwing at startup) — unless the constraint is required for `validate` mode to pass, which depends on how strictly that's configured. This is a "looks done" trap: the feature works perfectly in every dev/local demo.

**How to avoid:**
Write a manual migration script (`backend/migrations/NN-add-notificacao-unique-constraint.sql`) in the same phase that introduces the `Notificacao` entity, mirroring `81-add-facto-ordem-unique-constraint.sql`/`82-add-honorario-processo-unique-constraint.sql`, and add it to the deployment checklist/runbook exactly like those two were. Pair the DB constraint with a `try { save(...) } catch (DataIntegrityViolationException e) { /* already exists, ignore */ }` at the application layer (Spring's DB-agnostic translated exception for constraint violations) as defense in depth alongside — not instead of — the constraint.

**Warning signs:**
- A new unique constraint added only via `@Table(uniqueConstraints = ...)` on the entity with no corresponding file in `backend/migrations/`.
- Deployment runbook/checklist for this milestone with no explicit "run migration NN before/during deploy" step.

**Phase to address:**
Notification infrastructure phase (entity + constraint), verified again at the milestone's final integration-audit step (this project already runs a milestone-level audit before shipping — this is exactly the kind of gap that audit exists to catch).

---

### Pitfall 10: Reassignment endpoint and its notification trigger live in two different controllers with no shared template — easy to build one and skip the other's guard rails

**What goes wrong:**
The closest existing precedent for "reassign an owner FK with validation and a status guard" is `PUT /pareceres/{id}/atribuir` in `ParecerController.java` (lines 235-274): it validates the new `advogadoId` belongs to the tenant *and* holds the `ADVOGADO` role (`validateAdvogado(...)`), and explicitly blocks reassignment when `status == "CONCLUIDO"`. The brand-new `processo` responsável-reassignment endpoint being built this milestone lives in a **different file** (`ResourceController.java`), addresses a structurally identical problem, but has no shared base class/utility forcing parity. Two distinct risks follow: (a) the new endpoint might accept any UUID as the new `responsavelId` without validating tenant membership/role (the existing `createProcesso` path already validates this at creation time — `ResourceController.java:1474-1480` — so skipping it on the *update* path would be an inconsistency introduced by this milestone, not a pre-existing gap); (b) whoever wires the "processo atribuído" notification into the new endpoint may correctly do so there, then forget that "parecer atribuído" needs the identical hook added to the pre-existing, unrelated-looking `atribuirAdvogado` method in the other file (that endpoint currently has zero notification side effects — it was explicitly out of scope in v2.6, per `PROJECT.md`: *"NOTF-05/06/07 ... removidas do âmbito v1 da v2.6"* — meaning this milestone must actively go back and add the hook there, not just build it fresh in the new endpoint).

**Why it happens:**
The two reassignment flows are conceptually parallel but physically distant (different controller files, different feature history — one is brand new, one is 4 milestones old and easy to forget exists), so nothing forces an implementer touching one to notice the other.

**How to avoid:**
Explicitly mirror `atribuirAdvogado`'s shape for the new endpoint: validate new `responsavelId` belongs to tenant (reuse the same `userRepository.findById(...).getTenantId().equals(tenantId)` check already used at `ResourceController.java:1475-1480`), consider whether an analogous state guard is needed (e.g., can responsável be reassigned on an `ENCERRADO` processo?), and — in the same phase or an explicitly linked phase — add the matching notification-creation call to **both** `ResourceController`'s new endpoint **and** `ParecerController.atribuirAdvogado`. Treat "parecer atribuído" and "processo atribuído" as one shared checklist item, not two independent ones, precisely because they're easy to treat as unrelated given the file separation.

**Warning signs:**
- A PR/phase that only touches `ResourceController.java` for "processo atribuído" notifications with no corresponding change to `ParecerController.java` for "parecer atribuído."
- The new reassignment endpoint accepting a `responsavelId` with no tenant/role validation (compare directly against `createProcesso`'s existing check).

**Phase to address:**
"Reatribuição de responsável de processo" phase should explicitly cross-reference the "alerta de parecer atribuído" phase (or be sequenced so both notification hooks are verified together in the milestone's integration-audit step).

---

### Pitfall 11: The project's own proven repeat bug — cross-phase contract drift — applies directly to the new query params, DTO field names, and reassignment endpoint contract

**What goes wrong:**
This exact project has hit "each side looked correct in isolation, the full contract was broken" **three times** in its documented history: (1) v2.4 — backend emitted camelCase, frontend read snake_case, for newly-added fields (fixed with surgical `@JsonProperty`, `PROJECT.md` Key Decisions); (2) v2.9 milestone audit — `GET /honorarios?processo_id=X` and `GET /documentos?processo_id=X` both silently ignored the `processo_id` filter server-side while the frontend hook happily sent it, returning whole-tenant data instead of per-processo data, only caught by a milestone-level audit; (3) v2.5→v2.6 — `pesquisar()` lived in the wrong controller mapping (`ParecerController` vs. dedicated `ParecerPesquisaController`), making the documented route unreachable, undetected by unit tests or isolated review. This milestone introduces multiple new contract surfaces at once: new query params on `/notificacoes` (likely `lida`, `categoria`, `desde`, pagination), new field names in the `Notificacao` response DTO consumed by the bell + history page, and the brand-new reassignment endpoint's exact route/method/body shape. Any one of these can repeat the same failure mode — frontend sends a filter the backend accepts syntactically but ignores functionally, or reads a field name the backend never emits.

**Why it happens:**
Spring silently accepts unknown/unused `@RequestParam`s and unmatched JSON fields deserialize to `null` without error; TypeScript types are hand-written to *match an assumption* about the backend response rather than generated from it — nothing fails loudly at compile time or on first manual test if a name is off by a naming-convention or the backend never wired a param into its filtering logic. Each side passes its own isolated tests/review.

**How to avoid:**
Apply the same technique this project already uses to catch this class of bug: at the milestone-level audit (which this project already performs as a standard step, per its `.planning/milestones/vX-MILESTONE-AUDIT.md` history), explicitly grep every `@RequestParam`/query-string param the frontend hook constructs (`use-notificacoes.ts`'s `URLSearchParams`/query-string building) against the backend controller method's actual parameter list, and every field name the frontend's TypeScript type reads against what the backend DTO/`Map.of(...)`/`LinkedHashMap` response actually populates — do not accept "the frontend compiles and the backend returns 200" as sufficient verification. Given three prior instances, this warrants an explicit named check in this milestone's plan/audit rather than being left implicit.

**Warning signs:**
- A frontend hook building a query string parameter the corresponding `@GetMapping` method signature doesn't declare.
- A frontend TypeScript type for the notification/reassignment response containing a field name not visibly constructed in the backend response map/DTO.
- Any phase marked "done" based only on "build passes" / "manual click-through of the happy path," without an explicit request/response byte-level check.

**Phase to address:**
Cross-cutting — call out explicitly in the plan for (a) the notifications API phase, (b) the reassignment endpoint phase, and (c) the milestone-level integration audit (this project's existing practice, which caught this exact bug class twice before).

---

## Technical Debt Patterns

Shortcuts that seem reasonable but create long-term problems.

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|--------------------|-----------------|-------------------|
| No pagination on `GET /notificacoes` (this app has zero existing `Pageable`/`Page<>` usage anywhere — confirmed via codebase search) | Matches every other list endpoint's simplicity; ships faster | Unread history grows unbounded per user; `/notificacoes` page and its query get slower as months of history accumulate | Acceptable for v1 at current law-firm scale (tens of users, hundreds of processos); revisit if history isn't ever archived/pruned |
| In-process `synchronized(NotificacaoRepository.class)` as the *only* duplicate-insert guard (no DB unique constraint) | Fast to write, matches 3 existing precedents in this codebase (Cliente, Facto, ParecerVersao) | Does not protect against a future horizontally-scaled deployment (currently single-instance, confirmed via `docker-compose.prod.yml`); the project already learned this exact lesson for Facto/Honorario and added DB constraints afterward | Acceptable only paired with the DB unique constraint (Pitfall 9) — never acceptable alone, per the project's own established precedent |
| Denormalized/snapshotted notification text (title/body baked in at creation, not a live join to current entity state) | Immune to the "processo reassigned, notification silently relabels itself" problem (see Pitfall below); simple to implement | Renaming/correcting the source entity later won't retroactively fix historical notification text | Always acceptable here — this is the *recommended* choice, not just a shortcut, given the reassignment-mid-lifecycle risk |
| Query-time `OR user has ROLE_ADMIN` instead of fan-out-at-creation for ADMIN visibility | Less code, no need to enumerate admins per event | Breaks independent per-admin read state the moment a tenant has 2+ ADMIN users (Pitfall 3) | Never acceptable once more than one ADMIN per tenant is possible — seed data already creates exactly this scenario |

## Integration Gotchas

Common mistakes when connecting the new pieces to the existing app.

| Integration | Common Mistake | Correct Approach |
|-------------|-----------------|--------------------|
| New reassignment endpoint ↔ existing `createProcesso` responsavelId validation | Update path accepts any UUID without the tenant/role check `createProcesso` already enforces at creation | Mirror the exact check at `ResourceController.java:1474-1480`; do not treat create-time validation as sufficient for the new update path |
| New reassignment endpoint ↔ `ParecerController.atribuirAdvogado` | Treated as unrelated because they live in different files; only one gets a notification hook, a status guard, or tenant/role validation | Explicitly diff the two implementations against each other before considering either "done" (Pitfall 10) |
| Frontend `KNOWN_SCOPES` (`web/src/lib/permissions.ts`) ↔ backend `seedRbac()` permKeys (`DatabaseSeeder.java`) | New `notificacoes:*` scope added to only one side (e.g., backend seeds the permission and grants it, but frontend never adds `"notificacoes"` to `KNOWN_SCOPES`, so `hasScopedPermission` always returns false and the UI never shows what the backend would allow — or vice versa) | Add the scope string to both registries in the same commit; this project has a documented history of exactly this two-registry drift for other concerns (query filters, route mappings) — treat RBAC scope as the same risk class |
| Daily job ↔ consolidated risco/threshold logic (this milestone's own stated goal) | Job re-implements its own "is this critical" check instead of calling the new shared function, recreating a 5th inconsistent computation instead of eliminating the other 4 | Job must be the *first and only* caller of the new consolidated function from a non-request context — verify by grepping for any risco-like inline computation left inside the job |
| Notification bell polling ↔ mutations the user just performed (mark-as-read, reassign, etc.) | Bell's unread count only updates on the next 30-60s poll tick, so a user who just read/reassigned something sees a stale badge until the interval fires | `invalidateQueries` (or `setQueryData`) for the notifications query key from every mutation that could affect it, exactly as `useToggleEventoConcluido`/`useSetEventoConcluido` already invalidate `["eventos","upcoming"]` in `use-eventos.ts` — reuse that established pattern |

## Performance Traps

Patterns that work at small scale but fail as usage grows.

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| Per-processo repository calls inside the daily job's loop (the `listHonorarios` fallback anti-pattern, `ResourceController.java:2568-2575`) | Job wall-clock time grows linearly with total processo count across all tenants; invisible with seed data, visible after months of real usage | Batch-load per tenant once (`findByTenantId`/new `findByProcessoIdIn`), group in a `Map`, exactly like the existing `listProcessos` prazo-enrichment (`ResourceController.java:896-911`) | Noticeable once total processos across all tenants reaches the low hundreds; the existing `/honorarios` no-filter endpoint already exhibits this today at current data volume, just not yet painfully |
| Resolving "which users are ADMIN in this tenant" inside the innermost per-entity loop instead of once per tenant | Redundant repeated `findByTenantIdAndRoleName` calls, one per processo/prazo/honorário instead of one per tenant | Hoist the ADMIN lookup to once per tenant iteration, cache in a local variable/list for that tenant's processing | Low absolute cost at this scale (few tenants, few admins) but easy and free to avoid — do it right from the start |
| `User.roles`/`User.permissions` are `FetchType.EAGER` (`User.java:54-67`) | Every `findAllById(...)` batch-load of users (e.g., resolving recipients across many processos) eagerly pulls roles+permissions collections per user | Not a true N+1 given Hibernate's batching, but worth confirming the job doesn't re-fetch the same `User` object repeatedly inside a loop instead of reusing a pre-built map (same principle as the `responsaveisMap` pattern already in `listProcessos`) | Only matters once user/tenant counts grow well beyond current seed scale |

## Security Mistakes

Domain-specific issues beyond general web security.

| Mistake | Risk | Prevention |
|---------|------|------------|
| `Notificacao` read/mark-read endpoints scoped by tenant only (copying the pattern used by every other entity in this app) | User A can view or mark-as-read User B's notifications within the same tenant — a real, silent cross-user leak (Pitfall 2) | Every `Notificacao` query/mutation must check `destinatarioId == currentUserId` **in addition to** `tenantId == currentTenantId` |
| Notification detail/click-through re-validates "is this still MY processo" (`responsavelId == currentUser`) instead of tenant-only, after a reassignment has happened | The user who *used to be* responsável gets a 403 opening their own historical notification, because the underlying processo's `responsavelId` has since changed — inconsistent with how this codebase authorizes viewing every other already-created record (tenant-only, never retroactive-ownership) | Click-through/detail fetch for a referenced entity from a notification should use the same tenant-only check every other detail endpoint in this codebase already uses (e.g. `!processo.getTenantId().equals(getTenantId())`), not an ownership/assignment check |
| New `notificacoes:*` permission scope granted asymmetrically (e.g., backend allows TECNICO but frontend's `KNOWN_SCOPES`/gating never surfaces it, or vice versa) | Either a silently-broken feature for a role that should have access, or a role sees UI it can't actually use (confusing, not exactly a leak, but a contract violation of this project's stated "both layers must agree" rule) | Add the scope to `DatabaseSeeder.seedRbac()` (permKeys list + explicit per-role grant arrays) and `web/src/lib/permissions.ts` (`KNOWN_SCOPES`) in the same change, and verify against the actual role that should receive it (likely all four seeded roles, since notifications are inherently personal — not gated the way `financeiro`/`rbac:manage` are) |
| Job running with no `Authentication` accidentally "fixed" by hardcoding a synthetic system/admin `UserPrincipal` and pushing it into `SecurityContextHolder` for the job's thread | Introduces a standing "god-mode" principal pattern that could be misused or left behind for other background code later, and duplicates/diverges from the real JWT-derived principal shape | Don't authenticate the background thread at all — the job should call tenant-scoped repository methods directly with an explicit `tenantId` parameter (see Pitfall 1), never manufacture a fake `Authentication` to satisfy `@PreAuthorize`-guarded service methods; keep `@PreAuthorize`-guarded methods for HTTP-triggered paths only, and give the job its own unguarded internal service methods |

## UX Pitfalls

Common user experience mistakes for this specific feature.

| Pitfall | User Impact | Better Approach |
|---------|--------------|-------------------|
| Global `refetchOnWindowFocus: false` (`web/src/app/providers.tsx:14`, set app-wide, presumably deliberately for other reasons) silently applies to the new notifications query too | User backgrounds the tab, returns after a few minutes; the bell shows a stale unread count for up to a full poll interval (30-60s) because nothing triggers a catch-up fetch on refocus — TanStack Query v5's `refetchInterval` also pauses while the tab is hidden by default, so no background accumulation happens either, it's specifically the *return-to-tab* window that's stale | Explicitly override `refetchOnWindowFocus: true` (or `"always"`) as a **per-query** option on the new `useNotificacoes`/`useUnreadCount` hook, rather than changing the global default (which was presumably disabled for a reason relevant to other pages) |
| No existing polling precedent anywhere in this codebase (confirmed: zero `refetchInterval` usage today) | Whoever implements the bell may reach for a hand-rolled `useEffect(() => { const t = setInterval(...); return () => clearInterval(t) }, [])` instead of the built-in `refetchInterval` option, risking leaked/duplicate timers on remount (React Strict Mode double-invoke in dev, or route remounts) and reimplementing visibility handling TanStack Query already provides for free | Use TanStack Query's native `refetchInterval` (with an explicit `refetchOnWindowFocus` override as above) — do not hand-roll `setInterval` |
| `apiFetch` (`web/src/lib/api.ts:43-45`) triggers a `toast.error(...)` for every non-401/403 error response | A transient backend hiccup, brief container restart during a deploy, or any 5xx during a poll cycle will toast-spam the user every 30-60s until it recovers — an intrusive, repeated error banner for a background feature the user didn't explicitly action | Suppress or rate-limit toast errors specifically for the background polling query (e.g., pass a flag/option so polling failures log quietly or show a subtle inline indicator instead of the shared toast pipeline used for user-initiated actions) |
| Reassigning a processo's responsável leaves the previous responsável's already-created notifications about that processo untouched (no retroactive revoke) | Mildly confusing if the old responsável keeps seeing "prazo crítico" alerts for a processo they're no longer responsible for, generated *before* the reassignment | Acceptable given this milestone's explicit no-extra-complexity philosophy (Out of Scope: no team broadcast, no preferences) — but the *daily job's next run* should stop generating *new* alerts to the old responsável once it re-reads the current `responsavelId`, since the job always reads fresh state per Pitfall highlighted for race conditions; only pre-existing notifications remain as historical record, which is fine as a conscious choice, not an oversight |

## "Looks Done But Isn't" Checklist

Verify each of these explicitly before considering the relevant phase complete — none of them fail an isolated demo, only a closer look.

- [ ] **Scheduled job:** `@EnableScheduling` is actually present on a config/application class. The `@Scheduled` annotation is a silent no-op without it — no error, no log, the job simply never runs.
- [ ] **Scheduled job:** uses `cron`, not `fixedRate`/`fixedDelay` — verify it does *not* re-fire immediately after a container restart/redeploy (Pitfall 5).
- [ ] **Scheduled job:** does not call `getTenantId()` or anything that transitively touches `SecurityContextHolder` (Pitfall 1) — verify by actually invoking the job path in a test/manual trigger with no `Authentication` present, not just via an authenticated HTTP wrapper endpoint.
- [ ] **Scheduled job:** wrapped in a top-level try/catch that isolates one bad tenant/entity from aborting the whole run (Pitfall 6).
- [ ] **Notificacao entity:** the idempotency/dedup check is backed by an actual DB unique constraint with a corresponding file in `backend/migrations/`, not just in-process `synchronized` logic (Pitfall 9).
- [ ] **Notificacao queries:** every read/write filters by `destinatario_id` (or the ADMIN fan-out equivalent) *in addition to* `tenant_id` — grep for any `NotificacaoRepository` method that only takes a `tenantId` parameter (Pitfall 2).
- [ ] **Threshold tracking:** a persisted "last notified risco level" exists per (entity, recipient) and is actually read/compared before inserting — not just "is currently critical" (Pitfall 4).
- [ ] **RBAC:** new `notificacoes:*` scope(s) exist in `DatabaseSeeder.seedRbac()` (permKeys + explicit per-role grant) **and** `web/src/lib/permissions.ts` `KNOWN_SCOPES`, granted to the roles actually intended to use notifications (likely all seeded roles, not just ADMIN).
- [ ] **Reassignment endpoint:** validates new `responsavelId` belongs to the tenant (mirrors `createProcesso`'s existing check, `ResourceController.java:1474-1480`) — not just "accepts any UUID."
- [ ] **Reassignment endpoint + parecer atribuição:** both `ResourceController`'s new endpoint and `ParecerController.atribuirAdvogado` emit their respective notification — verify explicitly, since they live in different files and are easy to address only one of (Pitfall 10).
- [ ] **Frontend polling hook:** explicit `refetchOnWindowFocus` override present (not silently inheriting the app-wide `false`), and uses `refetchInterval` rather than a hand-rolled `setInterval`.
- [ ] **Cross-phase contract:** every query param the frontend sends to `/notificacoes` (and the reassignment endpoint's request body) is grepped against what the backend controller method actually declares/reads — the project's own documented repeat bug (Pitfall 11).
- [ ] **Mutation → polling interaction:** mark-as-read and reassignment mutations invalidate the relevant notifications query key, matching the existing `useToggleEventoConcluido`/`useSetEventoConcluido` invalidation pattern in `use-eventos.ts`.

## Recovery Strategies

When pitfalls occur despite prevention, how to recover.

| Pitfall | Recovery Cost | Recovery Steps |
|---------|-----------------|-------------------|
| Duplicate notifications already inserted (missing threshold-tracking or wrong `@Scheduled` trigger type) | LOW-MEDIUM | Write a one-time cleanup migration keeping the earliest row per `(tenant_id, destinatario_id, entidade_tipo, entidade_id, risco)`, then retroactively backfill the "last notified state" tracking table from the surviving rows' max risco per entity+recipient, then add the missing DB unique constraint and fix the trigger type |
| Cross-user notification leak discovered (missing recipient filter, Pitfall 2) | LOW | Add the missing `destinatario_id` filter to the offending repository method/endpoint immediately — same low-cost pattern this project already used twice to fix the analogous tenant-filter gaps in `/honorarios` and `/documentos` "in the same session" per the v2.9 milestone audit |
| RBAC scope drift discovered late (backend/frontend registries out of sync) | LOW | Grep-audit both registries side by side (`seedRbac()` permKeys + role grants vs. `KNOWN_SCOPES`), add the missing entries — same low-cost fix pattern already used for the `pareceres` route-mapping bug, "corrected in the same session" |
| Scheduled job silently stopped running after an uncaught exception (Pitfall 6) | LOW-MEDIUM | Add the top-level try/catch retroactively, redeploy, manually trigger one catch-up run (e.g., a temporary admin-only HTTP endpoint that invokes the same internal job method) to close the gap in missed notifications, or accept the gap and let the next scheduled run resume normally |
| `Notificacao` schema needs to change from a typed FK to `String entidadeId` after alert-generation phases already depend on the wrong shape (Pitfall 8) | MEDIUM | Requires a data migration (cast/backfill existing rows) and updating every alert-generation call site — cheapest if caught during the infrastructure phase's own review, expensive if caught after multiple alert types are already wired against the wrong column type |

## Pitfall-to-Phase Mapping

How roadmap phases should address these pitfalls. Phase names below mirror the feature language already used in `PROJECT.md`'s target-features list, for direct mapping by the roadmapper.

| Pitfall | Prevention Phase | Verification |
|---------|--------------------|-----------------|
| Per-recipient scoping omitted (#2) | Infraestrutura de notificações persistida (entidade + API) | Two independent test users in the same tenant see different notification lists; mark-as-read by one never affects the other |
| ADMIN fan-out vs shared read state (#3) | Infraestrutura de notificações persistida (entidade + API) | Seed a tenant with 2 ADMIN users; verify each has an independent read state for an ADMIN-targeted alert |
| Mixed PK types / transitive tenant isolation (#8) | Infraestrutura de notificações persistida (entidade design) | `Notificacao` rows exist and resolve correctly for at least one UUID-keyed entity (Processo) and one Integer-keyed entity (Evento/Honorario) |
| Missing DB-level idempotency constraint + missing migration script (#9) | Infraestrutura de notificações persistida (entidade + migration) | A file exists in `backend/migrations/` for the new constraint; prod deployment runbook references it |
| `getTenantId()`/SecurityContext reuse in background thread (#1) | Alerta de prazos de processos e calendário crítico (job diário) | Job's internal methods take `tenantId` as an explicit parameter end-to-end; manually invoke the job's core logic outside an authenticated request and confirm no NPE |
| Notify-every-run instead of on-crossing (#4) | Alerta de prazos de processos e calendário crítico (job diário) | Run the job twice on consecutive simulated days against unchanged data; second run produces zero new notifications for unchanged items |
| Wrong `@Scheduled` trigger type (#5) | Alerta de prazos de processos e calendário crítico (job diário) | Restart the backend container mid-day in a test/staging environment; confirm the job does not fire again until its actual scheduled time |
| Uncaught exception kills all future runs (#6) | Alerta de prazos de processos e calendário crítico (job diário) | Inject one deliberately malformed row (e.g., orphaned processo_id) in a test tenant; confirm other tenants still get notifications that same run, and the job still runs the next day |
| N+1 query pattern in the scan (#7) | Alerta de prazos de processos e calendário crítico (job diário) / Alerta de prazos de honorários | Query count for a single job run scales with tenant count, not with total processo/honorário count across all tenants |
| Reassignment endpoint ↔ parecer atribuição parity (#10) | Alerta de processo atribuído + fluxo de reatribuição de responsável | Both `ResourceController`'s new endpoint and `ParecerController.atribuirAdvogado` verified to emit their respective notification in the same review pass |
| Cross-phase contract drift (#11) | Cross-cutting — every phase touching `/notificacoes` or the reassignment endpoint, closed out at the milestone-level integration audit | Explicit grep-diff of frontend-sent params/fields vs. backend-declared params/fields, performed as a named audit step (not implied by "build passes") |
| Stale unread badge after backgrounding (UX) | Sino + página `/notificacoes` (frontend) | Manually background the tab past one poll interval, refocus, confirm the badge updates promptly rather than waiting a further full interval |
| Toast spam on polling errors (UX) | Sino + página `/notificacoes` (frontend) | Simulate a backend 500/restart while polling is active; confirm the user is not shown a repeated toast every poll cycle |

## Sources

**Codebase (HIGH confidence — read directly, 2026-07-08):**
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (getTenantId at 115-119; computeRisco at 1397-1404; processo enrichment/batch-load pattern at 896-958; responsavelId create-time validation at 1474-1480; updateProcesso with no responsavelId handling at 990-1012; N+1 anti-pattern in listHonorarios at 2558-2577; dashboard prazos críticos at 2855-2867; eventos/upcoming at 2246-2270; cliente merge reassignment pattern at 759-827)
- `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (atribuirAdvogado reassignment precedent, 235-274)
- `backend/src/main/java/com/lexcv/models/Processo.java`, `Evento.java`, `Prazo.java`, `Honorario.java`, `Documento.java`, `AuditLog.java`, `ParecerSolicitacao.java`, `User.java`, `Tenant.java`
- `backend/src/main/java/com/lexcv/repositories/UserRepository.java` (`findByTenantIdAndRoleName`), `HonorarioRepository.java`, `TenantRepository.java`
- `backend/src/main/java/com/lexcv/config/UserPrincipal.java` (ADMIN synthetic permissions at login time)
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (`seedRbac()`, role/permission grants)
- `backend/migrations/74-cleanup-nif-documento-tipo.sql`, `81-add-facto-ordem-unique-constraint.sql`, `82-add-honorario-processo-unique-constraint.sql` (manual migration convention)
- `web/src/app/providers.tsx` (global `refetchOnWindowFocus: false`), `web/src/lib/api.ts` (`apiFetch` toast-on-error behavior), `web/src/lib/permissions.ts` (`KNOWN_SCOPES`), `web/src/hooks/use-eventos.ts` (existing invalidation pattern), `web/src/hooks/use-processos.ts` (mutation/invalidation pattern), `web/src/components/shared/notification-bell.tsx` (current computed-only bell)
- `docker-compose.prod.yml` (single-instance deployment, `restart: unless-stopped`)
- `.planning/PROJECT.md` Key Decisions log (documented history of the camelCase/snake_case bug, the twice-repeated silently-ignored-filter bug, the `pesquisar()` route-mapping bug, the Facto/Honorario constraint-race lesson, the NOTF-05/06/07 deferral)

**External (verified 2026-07-08):**
- [Task Execution and Scheduling — Spring Boot official docs](https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html) — default scheduler thread pool size is 1
- [SimpleAsyncTaskScheduler: task with fixed delay stops working after unhandled exception · Issue #31749 · spring-projects/spring-framework](https://github.com/spring-projects/spring-framework/issues/31749) — official issue tracker confirmation of the silent-stop-after-exception behavior
- [The @Scheduled Annotation in Spring — Baeldung](https://www.baeldung.com/spring-scheduled-tasks)
- [Why Your @Scheduled Tasks Might Be Failing Silently in Spring Boot](https://medium.com/@himanshu675/why-your-scheduled-tasks-might-be-failing-silently-in-spring-boot-and-how-to-stop-it-%EF%B8%8F-86335cde37bc)
- [Window Focus Refetching — TanStack Query React Docs (v5)](https://tanstack.com/query/v5/docs/react/guides/window-focus-refetching)
- [Polling — TanStack Query React Docs](https://tanstack.com/query/latest/docs/framework/react/guides/polling) — `refetchInterval` pauses by default when the tab loses focus; `refetchIntervalInBackground` overrides this

---
*Pitfalls research for: LexCV v2.10 Notificações e Alertas*
*Researched: 2026-07-08*
