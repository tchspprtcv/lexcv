# Project Research Summary

**Project:** LexCV — v2.10 Notificações e Alertas
**Domain:** Persisted, RBAC-targeted, polling-refreshed in-app notification system for a multi-tenant legal practice-management SaaS (backend entity + daily deadline-scan job + REST API; frontend bell + dedicated history page)
**Researched:** 2026-07-08
**Confidence:** HIGH

## Executive Summary

LexCV v2.10 replaces a purely client-computed notification bell (today: 100% derived from `GET /eventos/upcoming`, no read/unread state, no backend entity) with a real persisted in-app notification system. Research across in-app notification-center UX literature, legal docketing/tickler systems, ITSM/SLA escalation tooling, and AR/collections dunning cadences converges on exactly the shape this milestone has already committed to in `PROJECT.md`: a bell + unread badge, a persisted read/unread history page with filters, relationship-derived targeting that is never a permission-broadcast, actor exclusion, and — the one genuinely domain-specific piece — daily re-evaluation of "is this still critical" rather than a one-shot alert, because a still-overdue court deadline or unpaid invoice must keep resurfacing until resolved (this is literally the leading cause of legal-malpractice claims in the docketing-system literature reviewed). Critically, **zero new dependencies are required**: everything is achievable with Spring's already-transitively-available `@Scheduled`/`@EnableScheduling`, a new JPA entity modeled directly on the existing `AuditLog` polymorphic-reference pattern, and TanStack Query's already-installed `refetchInterval` — consistent with this project's own track record of shipping feature milestones with "nenhuma nova dependência."

Architecturally, the work has exactly one hard sequencing constraint: consolidating the 5 different existing "is this critical" computations (dashboard KPI, sino v2.1, agenda page, `/eventos/upcoming`, `computeRisco()`) into a single `RiscoPrazoService` must happen before the new daily `@Scheduled` job can be written, since the job's entire purpose is to alert on that one shared definition — writing the job first would force a 6th ad-hoc copy of the same logic. Everything else — the persisted `Notificacao` entity/API, the four event-triggered alert hooks, the new responsável-reassignment endpoint, and the frontend bell/history page — can be built and verified largely independently once that consolidation and the notification entity exist. The entity itself must fan out one row per `(event, recipient)` at creation time — never a shared row with query-time ADMIN visibility — because read state is inherently per-user, and this is the **first** per-recipient-private entity this codebase has ever needed (every other entity today is tenant-shared, visible to anyone in the tenant with the right permission scope).

Pitfalls research is unusually dense and specific here because this milestone introduces two genuinely new architectural capabilities: a background thread with no `SecurityContext` (a guaranteed `NullPointerException` if the job copies this codebase's only existing tenant-resolution idiom, `getTenantId()`), and a per-recipient-private entity (a silent cross-user data leak if implementers/reviewers pattern-match the familiar "tenant check present = done" heuristic that has been correct for every other entity so far). The daily job additionally concentrates several independent failure modes — the wrong `@Scheduled` trigger type re-firing on every deploy, an uncaught exception silently killing all future runs with nothing visible in the logs, "notify on every run" instead of "notify on threshold crossing" producing indistinguishable-from-spam duplicate alerts, and an N+1 query pattern this exact codebase already has a live example of both to avoid (`listHonorarios`'s no-filter branch) and to copy instead (`listProcessos`'s batch-preload). Finally, this project has a proven, three-times-repeated bug class — cross-phase contract drift between what the frontend sends/reads and what the backend actually implements — and this milestone's several new API surfaces (notification list filters, the new reassignment endpoint) are equally exposed; the mitigation is the same explicit grep-diff audit that caught it the previous three times, not an assumption that "build passes" is sufficient verification.

## Key Findings

### Recommended Stack

No new dependency is required for this milestone — confirmed independently by STACK, ARCHITECTURE, and PITFALLS research, and verified against current official Spring Boot and TanStack Query v5 documentation (not training-data assumption). Every capability needed — the daily scheduled scan, de-duplication of "already notified for this threshold," and the polling bell — is achievable with what already exists in `backend/pom.xml` and `web/package.json`. The only genuinely new things are application code: one JPA entity, one service pair, one `@Scheduled` method, one controller, one TanStack Query hook file, and one route.

**Core technologies:**
- Spring `@EnableScheduling` + `@Scheduled` (Spring Framework 6.2.x, bundled transitively with `spring-boot-starter-parent` 3.4.1) — daily tenant-wide deadline-scan job; `spring-boot-starter-web` alone is sufficient, no new starter, no Quartz/Spring Batch needed for one fixed daily cron
- New JPA entity `Notificacao` (`t_notificacao`) — mirrors `AuditLog`'s proven `entidade_tipo`/`entidade_id` `String`-pair pattern, the only existing precedent in this codebase for referencing entities with mixed `UUID`/`Integer` primary-key types
- `@tanstack/react-query` `refetchInterval` (`^5.87.4`, already installed) — bell + `/notificacoes` polling (30-60s); this codebase's first use of the option, but a built-in, version-confirmed, stable API requiring no upgrade
- PostgreSQL composite unique constraint — de-duplicates "already notified for this threshold crossing," following the exact precedent of `backend/migrations/81-*.sql`/`82-*.sql` (added after an application-level check-then-act race was found in review for Facto/Honorario)
- Native `Intl.RelativeTimeFormat` + `java.time.ChronoUnit` (zero install) — relative timestamps and day-count-since-`dataAcordo` logic, matching this project's existing native-API-over-library convention

**Explicitly rejected:** Quartz/Spring Batch (over-engineered for one fixed daily job), ShedLock today (no horizontal scaling yet — single-container deployment confirmed in `docker-compose.prod.yml`; revisit only if that changes), WebSocket/SSE/STOMP (explicitly rejected by the user for this milestone, zero real-time infra exists anywhere in the repo), a new date library, new shadcn primitives (existing `popover`/`badge`/`table`/`switch`/`input` cover the need), and `ApplicationEventPublisher`/`@EventListener` (no precedent anywhere in this codebase, unjustified indirection for 4-6 call sites).

### Expected Features

The 7 features already committed in `PROJECT.md`'s Active list map cleanly onto ecosystem-standard "in-app notification center" table stakes, plus one domain-specific twist: LexCV's deadline alerts split into two distinct archetypes long recognized in legal-docketing and AR/collections literature — **countdown-to-a-future-date** (`Prazo`/`Evento`, which should reuse the existing `computeRisco()` tiers rather than invent a new scheme) and **aging-since-a-past-date** (`Honorario`, genuinely new bucket logic since `dataAcordo` has no fixed end date). The re-notification model for both is "one row per entity per calendar day it remains in a notify-worthy tier," driven by the daily job — never re-running scan logic on every 30-60s poll, which would conflate delivery cadence with creation cadence.

**Must have (table stakes, already scoped in PROJECT.md):** Bell icon + unread count badge (backend-driven); persisted read/unread state per recipient; mark individual/all as read; dedicated `/notificacoes` history page with filters (categoria, lida/não-lida, date range — `categoria` must be a stored column, not derived at render time); deep-link from notification to source entity across all 6 categories (careful: `documento` has two possible parent FKs — `processoId` or `clienteId`); role/relationship-based targeting only, never permission-broadcast; actor exclusion; near-real-time freshness via 30-60s polling.

**Should have (not required for v2.10 launch):** Severity-tiered visual treatment reusing the consolidated risk vocabulary; grouping/digest for high-volume categories; lightweight per-notification snooze for the two recurring categories (open question, flagged below); inline actionable notifications where the underlying action endpoint already exists.

**Defer (v2+):** Full per-user notification preferences/mute-by-category; email/push delivery; real-time WebSocket/SSE; @mentions; full processo-team broadcast beyond the single responsável.

### Architecture Approach

The integration plan adds one new package (`jobs/`), two new services (`NotificacaoService` for recipient-resolution + a single write choke point, `RiscoPrazoService` extracted verbatim from the existing `computeRisco()` private method), one new dedicated controller (`NotificacaoController`, following the precedent already set by extracting `ParecerPesquisaController`), and one new entity fanned out one row per `(event, recipient)` with `tenant_id` kept as its own first-class column and display text denormalized at write time — so the polling read path stays a single flat query with zero joins.

**Major components:**
1. `NotificacaoService` — resolves "who gets notified" per event type; single `criar(...)` choke point that is the only code path allowed to save a `Notificacao` row
2. `RiscoPrazoService` — single source of truth for "is this critical," extracted verbatim from `computeRisco()` plus a new `computeRiscoEvento(...)` method; hard prerequisite for the daily job
3. `AlertasDiariosJob` (new `jobs/` package, first of its kind) — daily `@Scheduled` cross-tenant scan; takes `tenantId` as an explicit parameter through every call (no `SecurityContext`); per-tenant try/catch isolation
4. `NotificacaoController` — new dedicated REST surface (list/unread-count/mark-read), scoped by tenant **and** recipient
5. `Notificacao` entity — flat table, `String entidadeTipo`/`entidadeId` pair (mirrors `AuditLog`), composite unique constraint backing idempotency, manual migration script required since `application-prod.yml` runs `ddl-auto=validate`

### Critical Pitfalls

1. **`getTenantId()` cannot be reused in the scheduled job** — the first background thread this codebase has ever had has no `SecurityContext`; calling the existing tenant-resolution helper throws an immediate NPE. Loop `tenantRepository.findAll()` and thread `tenantId` as an explicit parameter everywhere.
2. **`Notificacao` is the first per-recipient-private entity in an app where every other entity is tenant-shared** — pattern-matching the familiar "tenant check present" review heuristic silently misses that a second dimension (`destinatario_id`) is required. Every read/write must filter by both `tenant_id` **and** `destinatario_id`.
3. **Level-triggered instead of edge-triggered re-notification** — without a persisted "last notified state" per (entity, recipient), the daily job will re-create a notification every day for every still-critical item instead of only on a threshold crossing. Track last-notified risco level explicitly and only insert on change.
4. **An uncaught exception can silently stop all future job runs, not just the current one** — verified against Spring's own issue tracker. Wrap the whole job **and** each per-tenant iteration in try/catch.
5. **Cross-phase contract drift** — this exact project has hit "frontend sends a param/reads a field the backend doesn't actually implement" three times before. This milestone's new query params and new reassignment endpoint are equally exposed; close with an explicit grep-diff audit at the milestone-level integration review.

## Implications for Roadmap

Six phases cover the milestone's 7 target features with dependencies respected. The one unconditional hard gate is that the daily-job phase needs **both** the consolidation phase and the notification-infrastructure phase to exist first. (Note: the roadmapper ultimately produced a 5-phase roadmap, Phases 85-89 — see `.planning/ROADMAP.md` for the approved structure, which folds this research's Phase 3/Phase 4 event-triggered work into a single Phase 87.)

### Research Flags

All four research documents are grounded in direct codebase inspection with file/line citations, cross-checked against current official Spring Boot / TanStack Query documentation. No phase needs a fresh `--research-phase` pass. Two areas warrant the most careful plan-time review: the daily job (first-of-its-kind background/cross-tenant infrastructure) and the notification entity/API (first per-recipient-private + first genuinely polymorphic entity reference in this codebase).

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Verified against current official Spring Boot and TanStack Query v5 docs via direct source fetch, cross-referenced with direct codebase inspection |
| Features | MEDIUM-HIGH | General in-app notification-center UX patterns HIGH confidence, multi-source corroborated; legal-docketing reminder-cadence specifics MEDIUM confidence (vendor sources) |
| Architecture | HIGH | All codebase claims verified by direct source inspection (file/line citations), Spring scheduling mechanics verified against official reference docs |
| Pitfalls | HIGH | Derived directly from reading this repository's actual code; external Spring/TanStack claims verified against official docs and the Spring Framework issue tracker |

**Overall confidence:** HIGH

### Gaps to Address

- **Snooze/dismiss for the two recurring deadline categories** — genuinely open; resolved during requirements definition as NOTF-26 (deferred to v2, see REQUIREMENTS.md).
- **Whether the previous responsável should be notified on reassignment** — resolved during requirements definition: no, only the new responsável (see REQUIREMENTS.md Out of Scope).
- **Whether parecer advogado reassignment after creation is already possible today** — confirmed during Architecture research: yes, via `ParecerController.atribuirAdvogado`, which currently has zero notification side effects (deferred out of v2.6 scope) — Phase 87 wires this hook.
- **Categoria label granularity for the history-page filter** — left to Phase 89 plan-time decision.
- **Grouping/digest and inline actionable notifications** — explicitly deferred past v2.10 (not in REQUIREMENTS.md v1 or v2 lists).

## Sources

### Primary (HIGH confidence)
- https://docs.spring.io/spring-boot/reference/features/task-execution-and-scheduling.html — official Spring Boot reference docs
- Context7 `/spring-projects/spring-boot` (v3.4.1) — `@Scheduled`/`@EnableScheduling`/cron
- Context7 `/tanstack/query` (React, v5) — `refetchInterval`/polling/`refetchIntervalInBackground`
- https://github.com/TanStack/query/blob/main/docs/framework/react/guides/polling.md, `important-defaults.md`, `migrating-to-v5.md`
- https://github.com/spring-projects/spring-framework/issues/31749 — uncaught exception in `@Scheduled` silently stops future executions
- Direct codebase inspection across all four research files: `backend/pom.xml`, `web/package.json`, `backend/src/main/java/com/lexcv/{controllers,models,repositories,services,config,seed}/**`, `backend/migrations/{74,81,82}-*.sql`, `backend/src/main/resources/{application.yml,application-prod.yml}`, `backend/Dockerfile`, `docker-compose{,.prod}.yml`, `web/src/{app,components,hooks,lib}/**`, `.planning/PROJECT.md`

### Secondary (MEDIUM confidence)
- Legal practice management / docketing: MyCase, RunSensible, MatterAlert, CARET Legal, AttorneyReview
- Accounting practice management: Karbon, TaxDome, Canopy
- AR/collections dunning cadence: Centime, CreditPulse, FinanceOps
- SLA/escalation-tier patterns: Unito, Atlas Systems, lowcode.agency
- Notification fatigue / grouping / digest: Courier, Novu, SuprSend docs
- Snooze/dismiss UX: Toptal, koder.ai
- https://central.sonatype.com/artifact/net.javacrumbs.shedlock/shedlock-spring/7.7.0 — ShedLock version (deferred/optional, not adopted now)
- https://www.baeldung.com/spring-scheduled-tasks — corroborating source on silent-scheduler-stop behavior

### Tertiary (LOW confidence)
None flagged.

---
*Research completed: 2026-07-08*
*Ready for roadmap: yes (roadmap created same day — see .planning/ROADMAP.md, Phases 85-89)*
