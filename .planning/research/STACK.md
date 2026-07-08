# Stack Research

**Domain:** Persisted, RBAC-targeted, polling-refreshed in-app notification system (backend entity + daily deadline-scan job + REST API; frontend bell + dedicated history page)
**Project:** LexCV — v2.10 Notificações e Alertas
**Researched:** 2026-07-08
**Confidence:** HIGH (core recommendations verified against current official Spring Boot docs and TanStack Query v5 docs via Context7 + direct source fetch; two clearly-flagged MEDIUM-confidence footnotes are non-critical/deferred items)

## Headline Finding

**Zero new dependencies are required to ship this milestone.** Everything asked for — the daily scheduled scan, the de-duplication of "already notified for this threshold," and the polling bell — is achievable with what's already in `backend/pom.xml` and `web/package.json`. The only genuinely new things are application code: one JPA entity (`Notificacao`), one `@Service`, one `@Scheduled` method, one controller, one TanStack Query hook file, and one route. This is consistent with this project's own track record (v2.6 Pareceres UI, v2.9 Processos: "nenhuma nova dependência" was the norm, not the exception).

| Question asked | Short answer |
|---|---|
| Idiomatic way to add a single daily scheduled job? | `@EnableScheduling` + `@Scheduled(cron = ..., zone = "Atlantic/Cape_Verde")` on a new `@Service`. Already fully supported by `spring-boot-starter-web`, which is already in `pom.xml` — no new starter, no Quartz, no Spring Batch. |
| Is anything heavier ever warranted at this scale? | No, not for "one tenant-wide daily scan." Quartz/Spring Batch/db-scheduler solve problems (dynamic per-tenant schedules, chunked million-row ETL, persistent misfire recovery) that don't exist here. |
| Lightweight library for de-dup of "already notified for this threshold"? | No library — a Spring Data derived query (`findTop...OrderByCreatedAtDesc`) plus a composite unique constraint (same pattern this codebase already uses in `backend/migrations/81-*.sql` / `82-*.sql`) is sufficient and is the idiomatic tool here. |
| Is plain TanStack Query `refetchInterval` sufficient for the bell? | Yes — it's a first-class, already-installed option (`^5.87.4`). Two real, verified pitfalls apply and need explicit handling (see below): default background-tab pausing interacting with this project's global `refetchOnWindowFocus: false`, and unauthenticated polling after logout. |

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Spring `@EnableScheduling` + `@Scheduled` | Spring Framework 6.2.x, bundled transitively with `spring-boot-starter-parent` 3.4.1 (already pinned in `pom.xml`) | Daily tenant-wide deadline-scan job (prazos de processos, calendário crítico, prazos de honorários) | Confirmed via Spring Boot's own official docs (fetched directly, see Sources) that `spring-boot-starter-web` alone is sufficient — "doesn't require a separate starter." Auto-configures a `ThreadPoolTaskScheduler` with **1 thread by default**, which is more than adequate for one job firing once a day. |
| New JPA entity `Notificacao` (`t_notificacao`) | Hibernate/JPA already provided by `spring-boot-starter-data-jpa` (already in `pom.xml`) | Persisted notification record with `lida`/`lida_em` read-state | Mirrors the shape of the existing `AuditLog` entity almost exactly (`tenant_id UUID`, `entidade_tipo String`, `entidade_id String` — chosen there specifically "to accommodate both UUID and Integer IDs across entities," which this feature needs too: `Processo`/`Documento`/`Parecer` use UUID ids, `Honorario`/`Facto` use Integer ids). No new persistence technology, just a new table via the project's existing `ddl-auto=update` + hand-written `backend/migrations/*.sql` convention. |
| `@tanstack/react-query` `refetchInterval` | `^5.87.4` (already in `web/package.json`) | Bell badge + `/notificacoes` history page polling refresh (30-60s) | Already the project's sole data-fetching layer (per `CLAUDE.md`: "TanStack Query para toda interação com API"). `refetchInterval` is a built-in query option, not an add-on package — confirmed present and stable in the installed major version via Context7 (`/tanstack/query`, v5 branch). |
| PostgreSQL composite unique constraint | Whatever Postgres version the project already runs (unchanged) | De-duplicate "already notified for this threshold crossing" | Exactly the tool this codebase already reaches for: `backend/migrations/81-add-facto-ordem-unique-constraint.sql` and `82-add-honorario-processo-unique-constraint.sql` both exist for the same reason (a "check-then-act" without a DB-level backstop was flagged in code review as a genuine race-condition risk). Extending that same convention here is the path of least surprise. |

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `Intl.RelativeTimeFormat` (native — **zero install**) | Baseline web platform API, already available in the Node/browser runtime this project targets | Relative timestamps in the bell/history list ("há 3 horas", "há 2 dias") | Use instead of installing `date-fns`/`dayjs`. Supports `pt` locale natively (`new Intl.RelativeTimeFormat("pt", { numeric: "auto" })`); the project's only existing date-formatting need (`NotificationItem`'s `toLocaleDateString("pt-PT", ...)`) already relies on native `Intl`/`Date` APIs, not a library — this is the same pattern, applied to relative time. |
| `java.time.temporal.ChronoUnit` (JDK stdlib — **zero install**) | Day-count-since-`dataAcordo` for the Honorário deadline category | Already the exact mechanism `ResourceController.computeRisco()` uses today (`ChronoUnit.DAYS.between(hoje, dataLimite)`) for the Prazo risk calculation. The new Honorário category is the same idiom applied to `Honorario.dataAcordo` instead of `Prazo.dataLimite`. |
| Existing `components/ui/*` (shadcn-style primitives already in the repo) | Whatever's already installed — `popover.tsx`, `badge.tsx`, `table.tsx`, `switch.tsx`, `input.tsx` | Bell popover (already used by the current `NotificationBell`), unread badge, history table/list, read/unread + category filters on `/notificacoes` | No new shadcn primitive (`Tabs`, `Select`, `DropdownMenu`) is needed. The project has a track record of reaching for native HTML controls over adding new shadcn CLI-generated components — Phase 79 explicitly chose a native `<datalist>` combobox for exactly this reason. A native `<select>`/checkbox-driven filter bar on `/notificacoes` follows the same precedent. |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `backend/migrations/*.sql` (manual, hand-run) | Create `t_notificacao` + the composite unique index | Same convention already established by `74-cleanup-nif-documento-tipo.sql`, `81-add-facto-ordem-unique-constraint.sql`, `82-add-honorario-processo-unique-constraint.sql`. No Flyway/Liquibase is being introduced by this milestone — stay consistent with the existing manual-script-before-deploy process documented in `CLAUDE.md`. |
| Spring Boot Actuator `scheduledtasks` endpoint (optional, **not currently in `pom.xml`**) | Runtime visibility into the new `@Scheduled` job (last/next execution) | Not required to ship this milestone — the app has no Actuator dependency today and adding one is a bigger decision than this feature warrants. Mentioned only as a documented option if operational visibility becomes a real ask later (`GET /actuator/scheduledtasks`, confirmed current in Spring Boot's own docs). |

## Installation

No new dependencies are required for the recommended path. Nothing to add to `pom.xml` or `package.json`.

```bash
# Backend: nothing to install.
# @EnableScheduling / @Scheduled ship inside spring-context,
# already pulled in transitively by spring-boot-starter-web (already in pom.xml).

# Frontend: nothing to install.
# refetchInterval is part of @tanstack/react-query, already ^5.87.4 in package.json.
```

Only if a future need actually materializes (see "Stack Patterns by Variant" below) would this change:

```xml
<!-- OPTIONAL / DEFERRED — only add if the backend is ever scaled to >1 replica.
     Not needed today: docker-compose.prod.yml runs a single backend container. -->
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>7.7.0</version>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>7.7.0</version>
</dependency>
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|--------------------------|
| `@Scheduled(cron = ...)` | Quartz Scheduler (`spring-boot-starter-quartz`) | Only if you need per-tenant/user-configurable schedules, misfire recovery across restarts, or dynamically add/remove jobs at runtime. None of that applies to one fixed, tenant-wide, once-a-day scan. |
| `@Scheduled(cron = ...)` | Spring Batch | Only if the scan needed chunked reads/writes over millions of rows with restart-from-checkpoint semantics. LexCV's per-tenant `Prazo`/`Honorario` row counts are nowhere near that scale. |
| `@Scheduled(cron = ...)` | db-scheduler (kagkarlsson) | Only if you need many independently-scheduled, dynamically created one-off/recurring jobs persisted in the DB (e.g., a per-client reminder at an arbitrary future timestamp). This milestone has exactly one fixed schedule, not a per-entity dynamic one. |
| Plain `@Scheduled`, no distributed lock | ShedLock 7.7.0 (`net.javacrumbs.shedlock`) | Only once the backend runs as more than one replica simultaneously. `docker-compose.prod.yml` currently defines a single `backend` service with resource limits but no replica count — i.e., exactly one instance runs the job. Adding ShedLock today would be solving a problem that doesn't exist yet. |
| Exists-check (`findTop...OrderByCreatedAtDesc`) + composite unique constraint | A dedicated idempotency/dedup library | No standard, widely-adopted library exists in the Spring ecosystem for "did I already record this state transition" — this is squarely a database-constraint problem, and the codebase already has an established pattern for it (see Core Technologies). |
| Synchronous same-transaction `notificacaoService.criar(...)` call inline in the 4 existing write endpoints (nova fase, novo documento, atribuição, parecer atribuído) | `ApplicationEventPublisher` + `@TransactionalEventListener`, or `@Async` | Only worth the indirection if notification-creation becomes a measurable bottleneck on the write path, or if the number of trigger points grows well beyond today's four. This codebase has **zero** existing `@EventListener`/`@Async` usage anywhere (confirmed by search) — introducing eventing for 4 call sites would be a new architectural pattern with no existing precedent to build on, for no measurable benefit yet. |
| TanStack Query `refetchInterval` (30-60s) | WebSocket / SSE (`SseEmitter`, STOMP) | Already explicitly decided against for this milestone by the user. Would only be justified if sub-second delivery latency became a real product requirement — this app has zero WebSocket/SSE/STOMP infrastructure today (confirmed absent by search across the whole repo). |
| Native `Intl.RelativeTimeFormat` | `date-fns` / `dayjs` / `luxon` | Only if the app starts needing broader date arithmetic (timezone conversion, business-day math, recurrence rules) beyond simple relative-time display — which the backend already handles today via plain `java.time`, with no library. |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|--------------|
| Quartz Scheduler / `spring-boot-starter-quartz` | Brings its own persistent job-store tables, migration, and clustering configuration — pure overhead for one fixed cron job that never changes at runtime. | `@EnableScheduling` + `@Scheduled(cron = "...", zone = "Atlantic/Cape_Verde")`. |
| Spring Batch | Designed for large chunked ETL-style processing with step/restart/skip semantics. Nothing in this milestone approaches that data volume or failure-recovery complexity. | A plain `@Scheduled` method with a couple of Spring Data repository queries. |
| ShedLock (today) | Solves a distributed-lock problem that doesn't exist yet — confirmed single-container backend deployment in `docker-compose.prod.yml`. Adding it now is speculative complexity. | Add it later — one dependency, one annotation (`@SchedulerLock`) — only if/when the backend is ever scaled horizontally. |
| WebSocket / SSE / STOMP / any push transport | Explicitly rejected by the user for this milestone; introduces connection lifecycle, reconnect/backoff, and proxy/Caddy configuration this project has never needed (confirmed zero WebSocket/SSE code anywhere in the repo). | TanStack Query `refetchInterval` (30-60s), same as every other data surface in this app. |
| A new date library (`date-fns`/`dayjs`/`luxon`) introduced solely for this feature | The day-count-since-`dataAcordo` calculation is one line of `java.time` on the backend (same idiom as the existing `computeRisco()`); the "relative time" display need is one native `Intl.RelativeTimeFormat` call. Neither justifies a new frontend dependency. | `ChronoUnit.DAYS.between(...)` (backend) + `Intl.RelativeTimeFormat` (frontend). |
| New shadcn/ui primitives (`Tabs`, `Select`, `DropdownMenu`) for the `/notificacoes` filters | `popover.tsx`, `badge.tsx`, `table.tsx`, `switch.tsx`, `input.tsx` already exist and cover the same ground; the project has explicitly preferred native HTML controls over new shadcn components before (Phase 79's native `<datalist>` combobox). | Reuse existing `components/ui/*` + native `<select>` / checkbox inputs for category and read/unread filters. |
| `ApplicationEventPublisher` / `@EventListener` / `@Async` for the four instant-trigger notifications | Zero existing precedent anywhere in this codebase; adds indirection, thread-pool considerations, and eventual-consistency edge cases for what is currently four call sites inside an already-large, procedurally-styled `ResourceController`. | A direct, synchronous call to a new `NotificacaoService` inside the same transaction as the triggering write — mirrors how every other side effect in `ResourceController` already works (e.g., `Honorario` auto-creation on formalização is already a direct synchronous call, not an event). |
| A hard unique constraint on `(tenant_id, entidade_tipo, entidade_id, categoria)` without the threshold/recipient columns | Would silently prevent the legitimate 3-row lifecycle of one entity (`ok`→`proximo` is one row, `proximo`→`vencido` is a second row) and — because targeting fans out to multiple recipients (the directly-linked user **and every ADMIN**, per the milestone's own targeting decision) — would silently drop every recipient's row after the first if `destinatario_user_id` isn't part of the key. | A composite constraint on `(tenant_id, destinatario_user_id, entidade_tipo, entidade_id, categoria, nivel)` — narrow enough to block a true duplicate re-insert of the *same* recipient's *same* crossing, wide enough to allow the natural progression of alerts and the fan-out to multiple recipients. |

## Stack Patterns by Variant

**If the backend is ever scaled to more than one replica (not the case today):**
- Add `net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template` (7.7.0) and wrap the scan method with `@SchedulerLock(name = "notificacoes-scan-diario", lockAtMostFor = "PT30M")`, backed by `@EnableSchedulerLock`.
- Because without it, N replicas would each execute `@Scheduled` independently at the same wall-clock moment and attempt to fan out duplicate notification rows on the same day (the composite unique constraint would catch and swallow the duplicates, but it's cleaner to prevent the redundant work outright once concurrency is real).

**If the daily scan ever needs a different cadence per tenant (not requested in this milestone — today it's one shared schedule for all tenants):**
- Reconsider Quartz or db-scheduler for DB-driven, per-row schedule configuration.
- Because `@Scheduled` cron expressions are fixed at compile/startup time (or at best externalized to a single property), not configurable per database row at runtime.

**If notification volume per tenant grows into the thousands per day:**
- Add `Pageable`/pagination to the `/notificacoes` list endpoint and a supporting index on `(tenant_id, destinatario_user_id, created_at DESC)`.
- Because this is a straightforward indexing concern as history accumulates, not a reason to reach for a new dependency.

**If observability into "did the job run, when, did it fail" becomes an actual operational need:**
- Add `spring-boot-starter-actuator` and expose `/actuator/scheduledtasks` (and optionally Micrometer metrics), rather than hand-rolling a status table.
- Because Spring Boot already ships this integration; it's a config addition, not new code, if/when Actuator is adopted for other reasons too.

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|------------------|-------|
| `spring-boot-starter-parent` 3.4.1 (already pinned) | `@Scheduled` / `@EnableScheduling`, Java 23 | Confirmed directly against Spring Boot's official reference docs: `spring-boot-starter-web` alone is sufficient; no `spring-boot-starter-quartz`/batch needed. Default auto-configured `ThreadPoolTaskScheduler` uses 1 thread unless `spring.task.scheduling.pool.size` is raised — no tuning needed for one job/day. |
| `@tanstack/react-query` `^5.87.4` (already installed) | `refetchInterval: (query) => number \| false` | v5 changed the callback signature from v4's `(data, query) => ...` to `(query) => ...` only — confirmed current against the v5 branch of the official docs (also verified against the newer `v5_90.3` snapshot in Context7, no further change within v5.x). The installed `^5.87.4` already has this API; no upgrade needed. |
| Spring Framework 6.2.x (bundled with Boot 3.4.1) | `cron` attribute's `zone` parameter | `@Scheduled(cron = "...", zone = "Atlantic/Cape_Verde")` is required for a predictable local-time trigger: the backend's Docker image (`eclipse-temurin:23-jre-alpine`, confirmed in `backend/Dockerfile`) sets no `TZ`, so its JVM default is almost certainly UTC, one hour off Cabo Verde's UTC-1 (no DST) — without an explicit `zone`, "runs daily at 07:00" would actually fire at 06:00 local time. |
| `net.javacrumbs.shedlock:shedlock-spring` 7.7.0 (deferred, not adopted now) | Spring Boot 3.x, JDBC lock provider against the project's existing PostgreSQL instance | Only relevant if/when horizontal scaling happens (see "Stack Patterns by Variant"). Version confirmed current via Maven Central at time of research (MEDIUM confidence — WebSearch cross-referenced against Maven Central and the project's own GitHub repo, not an official Spring doc). |

## Integration Notes Specific to LexCV's Existing Architecture

- **RBAC integration — no new `@PreAuthorize` scope needed.** The milestone's own targeting decision ("Alvo de cada notificação = só a entidade diretamente ligada... + ADMIN — nunca notificação em massa por permissão de visualização") means the list/mark-read endpoints are authorized by **row ownership** (`WHERE destinatario_user_id = <principal's user id> AND tenant_id = <principal's tenant id>`), not by a `scope:action` permission string. This is a different authorization shape than every other resource in `ResourceController` (which all use `hasAuthority('<scope>:<action>')`), but it's the correct one here: every authenticated user, regardless of role, should be able to see (only) their own notifications. No new row needs to be added to `DatabaseSeeder`'s permission set for this — `isAuthenticated()` plus the `WHERE` clause is the whole authorization story. (`permissions.ts`'s `KNOWN_SCOPES` registry does not need a `notificacoes` entry either, for the same reason.)
- **The scheduled job runs outside any HTTP request/JWT context.** Every other piece of backend logic in this codebase derives `tenantId` from `SecurityContextHolder` via `ResourceController.getTenantId()` (which reads `UserPrincipal.getTenantId()`). A `@Scheduled` method has no `Authentication` in its thread's `SecurityContext` — it must instead query across **all** tenants directly (e.g., iterate `tenantRepository.findAll()`, or a single cross-tenant query grouped in memory), which is a genuinely new access pattern for this codebase (everything else is 100% tenant-scoped-by-request). Worth flagging explicitly during implementation review so nobody accidentally tries to reuse `getTenantId()` inside the job and gets a `NullPointerException`/`ClassCastException` on an absent principal.
- **Threshold logic to consolidate.** `ResourceController.computeRisco(LocalDate dataLimite, String prioridade)` (currently a private method, ~line 1397) is the exact "ok/proximo/vencido" logic the milestone wants unified across dashboard, bell, agenda, and now the new job — pulling it into a shared, reusable method (rather than a fifth private-method copy) is a low-risk refactor already implied by the milestone's own stated goal ("lógica de 'crítico' consolidada numa única fonte partilhada").
- **The frontend polling query must be gated on auth state.** `web/src/lib/api.ts`'s `apiFetch` deliberately swallows the toast for 401/403 responses ("Ignorar toast automático para rotas de auth check... para evitar spam se a sessão expirar") but still throws. A `refetchInterval`-driven query has no natural stopping condition on logout the way a one-shot query does — without gating `enabled` on a successful `useMe()` (mirroring how other hooks gate on `typeof window !== "undefined"`), the bell would keep silently retrying every 30-60s against an expired/absent session.
- **`refetchOnWindowFocus: false` is already set globally** in `web/src/app/providers.tsx`. Combined with TanStack Query's own default of pausing `refetchInterval` while the tab is hidden, a user who alt-tabs away and back could see a bell that's stale by up to a full interval (paused while away, then waits out whatever remains of the timer before firing — no immediate catch-up). Recommend an explicit **per-query override** of `refetchOnWindowFocus: true` on just the notifications query (TanStack Query merges per-query options over the `QueryClient` defaults, so this is a one-line, fully-scoped override) so returning to the tab triggers an immediate refresh on top of the interval. Do **not** reach for `refetchIntervalInBackground: true` instead — that keeps the network request firing every 30-60s even while the tab sits unfocused in the background all day, which is unnecessary load for an internal practice-management tool.
- **Mutation-invalidation, not a second polling loop, for "mark as read."** The existing `use-eventos.ts` pattern (`onSuccess: () => queryClient.invalidateQueries({ queryKey: [...] })`) is exactly what "marcar como lida" should do — invalidate the bell's query key so the badge count updates immediately, rather than waiting for the next interval tick. No new pattern needed here, just the one this project already uses everywhere.

## Sources

- Context7 `/spring-projects/spring-boot` (resolved to version `v3.4.1`, exact match to `pom.xml`) — topic: "scheduled tasks @Scheduled @EnableScheduling cron"
- https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html — official, current Spring Boot reference docs, fetched directly; confirms `spring-boot-starter-web` sufficiency, default 1-thread `ThreadPoolTaskScheduler`, `spring.task.scheduling.*` properties, and virtual-thread behavior — HIGH confidence
- Context7 `/tanstack/query` (React branch, v5) — topic: "refetchInterval polling refetchIntervalInBackground"
- https://github.com/TanStack/query/blob/main/docs/framework/react/guides/polling.md — confirms default background-tab pause behavior, `refetchIntervalInBackground`, and dedup of concurrent in-flight fetches for the same query — HIGH confidence
- https://github.com/TanStack/query/blob/main/docs/framework/react/guides/important-defaults.md — confirms window-focus/reconnect refetch defaults — HIGH confidence
- https://github.com/TanStack/query/blob/main/docs/framework/react/guides/migrating-to-v5.md — confirms the v4→v5 `refetchInterval` callback signature change — HIGH confidence
- https://github.com/lukas-krecan/ShedLock and https://central.sonatype.com/artifact/net.javacrumbs.shedlock/shedlock-spring/7.7.0 — current version confirmation for the deferred/optional recommendation — MEDIUM confidence (WebSearch, cross-referenced against Maven Central and the library's own GitHub repo, not an official Spring source)
- https://github.com/spring-projects/spring-boot/issues/49949 (and related Boot issue-tracker discussion) — virtual-threads + `fixedDelay` serialization gotcha, informing the choice of `cron` over `fixedDelay` — MEDIUM confidence (GitHub issue tracker, not primary docs; non-blocking footnote since `cron` is being recommended regardless)
- Direct codebase inspection (HIGH confidence, read first-hand for this research): `backend/pom.xml`, `web/package.json`, `backend/src/main/java/com/lexcv/models/AuditLog.java`, `backend/src/main/java/com/lexcv/models/Honorario.java`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (`computeRisco`, `getTenantId`), `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (permission scopes), `backend/migrations/74-*.sql`, `81-*.sql`, `82-*.sql`, `backend/src/main/resources/application.yml`, `backend/Dockerfile`, `docker-compose.prod.yml`, `web/src/app/providers.tsx`, `web/src/lib/api.ts`, `web/src/lib/permissions.ts`, `web/src/hooks/use-eventos.ts`, `web/src/lib/prazos.ts`, `web/src/components/shared/notification-bell.tsx`, `web/src/components/ui/*` — confirmed absence of `@Scheduled`/`@EnableScheduling`/Quartz/Spring Batch, WebSocket/SSE/STOMP, and any existing `refetchInterval` usage anywhere in the repository via targeted search across the whole tree

---
*Stack research for: persisted in-app notifications (backend scheduled job + REST API; frontend polling bell + history page)*
*Researched: 2026-07-08*
