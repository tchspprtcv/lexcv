# Feature Research

**Domain:** In-app notifications/alerts for multi-tenant B2B/SaaS practice-management (legal case management; adjacent verticals: accounting/consulting firm practice management, ITSM/helpdesk for SLA-alert patterns, AR/collections for overdue-payment patterns)
**Researched:** 2026-07-08
**Confidence:** MEDIUM-HIGH (general in-app notification-center UX patterns are HIGH confidence, multi-source corroborated; legal-specific reminder-cadence numbers and accounting-practice-management specifics are MEDIUM confidence — fewer, less authoritative sources; codebase-grounded dependency claims are HIGH confidence, verified directly against LexCV's current backend models)

## Context Recap

This research answers one scoped question for LexCV v2.10: how in-app-only (no email/push, no per-user preferences) notification systems typically work in B2B practice-management products, and what's specific to "deadline/SLA-approaching" alerts vs. simple event-triggered alerts. It is **not** a green-field domain survey — v2.10's 7 target features are already committed in `.planning/PROJECT.md`. This file validates that scope against ecosystem evidence, flags concrete complexity/dependency details grounded in the actual codebase, and surfaces the few genuinely open questions (chiefly: snooze/dismiss for recurring deadline reminders) that `.planning/PROJECT.md` has not yet settled either way.

LexCV's current state relevant to this research (verified directly in code, not inferred):
- `NotificationBell` (`web/src/components/shared/notification-bell.tsx`) is 100% client-computed from `useUpcomingEventos()` → `GET /eventos/upcoming`. No backend notification entity exists anywhere in `backend/src/main/java/com/lexcv/models/`.
- `Prazo` already has a working, if narrow, criticality convention: `computeRisco(dataLimite, prioridade)` in `ResourceController.java` returns `vencido` (overdue) / `proximo` (due within 7 days if `prioridade=ALTA`, else 3 days) / `ok`. This is one of the 4 inconsistent implementations PROJECT.md wants consolidated — but it's also the closest thing LexCV has today to a proven, tenant-tested threshold model, and a natural anchor for the new consolidated logic rather than a reason to invent something unrelated.
- `Processo.responsavelId` is settable only at creation today — no reassignment endpoint exists. This is a hard, already-acknowledged prerequisite for the "processo atribuído" alert (see Dependencies).
- `Honorario` has `dataAcordo` and a computed `totalPago` (`@Formula` summing `t_pagamento`) but no due-date field, and per explicit Key Decision will not get one in v2.10 — the alert must be age-since-agreement, not date-vs-deadline.
- `ParecerSolicitacao.advogadoId` exists and is already settable.
- `Documento.processoId` / `Documento.clienteId` are both nullable FKs — a document can belong to either.
- No `@Scheduled` job exists anywhere in the project yet. The daily deadline-scan job is genuinely new infrastructure, not an extension of an existing pattern.

## Feature Landscape

### Table Stakes (Users Expect These)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Bell icon + unread count badge | Universal convention across every B2B SaaS product surveyed (GitHub, Slack, Linear, Notion-style patterns); users scan the badge as their primary "do I need to look" signal | LOW | `NotificationBell` component already exists and already renders a badge — this is a data-source swap (backend-driven unread count instead of `useUpcomingEventos().length`), not new UI |
| Persisted read/unread state per recipient | Users expect a notification they've seen to stop demanding attention, and expect that state to survive a page reload/re-login — the current v2.1 bell fails this today (nothing is ever "read," it just recomputes) | LOW-MEDIUM | Needs a `lida`/`lida_em` (or `read_at`) column on the new Notification row, scoped per recipient — see Dependencies for schema shape |
| Mark individual as read + "mark all as read" | Baseline inbox-management affordance in every notification center reviewed (SuprSend, Courier, GitHub, Jira patterns) | LOW | Two small endpoints (`PATCH /notificacoes/{id}/ler`, `POST /notificacoes/ler-todas`) mirroring existing mutation patterns already used elsewhere in `ResourceController` |
| Dedicated history/inbox page with pagination | Already explicitly scoped (`/notificacoes`) — confirmed as standard: every product reviewed treats "see everything, not just the last 10" as a first-class page, not a bigger popover | MEDIUM | New route + new TanStack Query hook (`use-notificacoes.ts`), reusing the list+pagination pattern already used for Processos/Clientes lists |
| Filters on history page (category/type, read/unread, date range) | Standard in every notification-center product analyzed; without filters, a shared "responsável + ADMIN" inbox (see below) becomes unusable once volume grows past a handful of items | MEDIUM | Requires the notification row to store a queryable `categoria` (fase/documento/atribuicao/parecer/prazo/honorario) — must be a stored column, not inferred at render time, or filtering breaks |
| Deep-link from notification to source entity | Universal pattern ("click a notification, land on the thing it's about") — already partially present in the v2.1 bell (`processoId` → `/processos/{id}` link) | MEDIUM | 6 categories need 6 correctly-mapped destinations: fase→`/processos/{processoId}` (fases tab), documento→`/processos/{id}` or `/clientes/{id}` depending on which FK is set, atribuição→`/processos/{id}`, parecer→`/pareceres/{id}`, prazo/calendário→`/processos/{id}` or `/agenda`, honorário→`/processos/{id}/termo-honorarios` or `/financeiro`. Getting the document one wrong (always assuming `processoId`) is the most likely mistake since it's the only category with two possible parents |
| Role/relationship-based targeting only, never permission-broadcast | Confirmed as correct existing decision, not a new finding: broadcasting to "everyone with `X:view`" is a documented anti-pattern (see Anti-Features) — LexCV already decided targeting = responsável/advogado/cliente-team + ADMIN | MEDIUM-HIGH | Cross-cutting correctness risk: this rule must be re-derived correctly at each of the 6 trigger sites (different entity, different owning relationship each time) — a single shared "resolve recipients for entity X" helper is worth the investment to avoid 6 subtly-different implementations |
| Actor exclusion (never notify the user who performed the triggering action) | Extremely well-established convention (explicit standard trigger condition in Zendesk, GitHub, Jira: "don't notify the assignee if they assigned it to themselves") — without it, the ADVOGADO who just moved their own processo to a new fase gets a self-notification on every action they take, which reads as broken, not helpful | LOW | One shared guard in the notification-creation helper: skip insert when `actorUserId == recipientUserId`. Exception: the daily deadline-scan job has no "actor" (system-generated), so this guard is a no-op there, not a blocker |
| Category icon/visual differentiation | Standard across every notification center reviewed — users triage by glancing at an icon/color before reading text | LOW | Pure frontend, one icon+color per category, no backend dependency |
| Empty state ("Sem notificações") | Baseline UX completeness — already the pattern used in the current bell for zero-events | LOW | Trivial, matches existing component |
| Multi-tenant isolation of notification rows | Not a new finding for this project — every entity in LexCV already carries `tenant_id` and every controller already scopes by it | LOW | Inherited convention, not new risk, provided the new `Notificacao` entity follows the same `tenant_id` + controller-level scoping already used everywhere else |
| Near-real-time freshness via polling | Users expect a bell that updates without a manual refresh; 30-60s polling is the already-decided, already-precedented mechanism (TanStack Query is used for all data fetching in this app) | LOW | Already decided (Key Decision: polling over WebSocket/SSE) — `refetchInterval` on the existing query client pattern, zero new infrastructure |

### Deadline/SLA-Approaching Alert Patterns (Cross-Cutting — Answers the Specific Sub-Question)

Deadline-approaching alerts are a **different animal** from the other 4 event-triggered categories (fase change, document, assignment, parecer assignment), which are naturally single-fire: something happened once, one notification is created, done. Deadline alerts must re-evaluate a *state* (is this still overdue/approaching?) on a recurring cadence, which raises questions event-triggered alerts don't have. Research across legal docketing systems, ITSM/helpdesk SLA tooling, and AR/collections software converges on two distinct archetypes — and LexCV's 3 named deadline-scan sources split cleanly across them:

**Archetype A — countdown-to-a-future-date** (applies to `Prazo.dataLimite` and `Evento`/calendário crítico):
- Standard pattern is a **decreasing-interval, multi-tier reminder schedule** anchored to the deadline, not a single alert. One legal-deadline-management source (MEDIUM confidence, single source but consistent with general docketing-software framing) specifies a 30/14/7/3/1-days-before cadence, with buffer sized to deadline type (statute of limitations: 30+ days; responsive pleadings: 7-10 days; discovery: 5-7 days; motions: 3-5 days; appeals: 14+ days). ITSM/SLA tooling frames the equivalent idea as **percentage-of-window thresholds** (50% / 75-80% / 90% of the time-to-deadline elapsed, escalating who gets notified at each tier) — same underlying idea, different unit.
- Legal docketing/tickler systems (MatterAlert, LawToolBox, Aderant — MEDIUM confidence, vendor marketing sources but consistent with each other) universally emphasize **redundant verification**: a critical date entered by one person should be checkable/visible to a second, because missed deadlines are the single leading cause of legal malpractice claims. LexCV's decision to always co-target ADMIN alongside the responsável on every category (already decided in Key Decisions) *is* a form of this redundancy — worth stating explicitly as a benefit of that existing decision, not just a side effect.
- **Recommendation for LexCV:** don't invent a new tier scheme from scratch. `Prazo.computeRisco()` already implements a 2-tier model (vencido / próximo-within-N-days-by-prioridade / ok) that's live and tenant-tested. The v2.10 consolidation work should promote this into the single shared source (as already decided) and use its tier *transitions* as the notification-creation trigger points, rather than designing an unrelated 30/14/7/3/1 scheme the rest of the app doesn't otherwise use. `Evento` has no equivalent function today (only `Prazo` does) — extending the same threshold logic to `Evento.dataFim`/`dataInicio` is the concrete piece of consolidation work, not a redesign.

**Archetype B — aging-since-a-past-date, no fixed end** (applies to `Honorario` unpaid since `dataAcordo`):
- This is structurally an **accounts-receivable/dunning problem**, not a court-deadline problem — there's no future date to count down to, only an open-ended clock counting up from `dataAcordo` until `totalPago` reaches `valorTotal`. AR/collections literature (HIGH confidence, many independent, converging sources) consistently uses **escalating aging buckets** — commonly 7/14/30/60/90 days overdue, or the coarser 30/60/90(+120), with tone/severity increasing per bucket, phone/escalation entering by day ~14, and formal escalation by day ~30-60.
- **Recommendation for LexCV:** `Honorario` has no equivalent bucket function today (unlike `Prazo`) — this is genuinely new logic, not a consolidation of something existing. Recommend a small number of buckets (e.g., 7 / 30 / 60+ days unpaid since `dataAcordo`, `valorTotal` not null, `totalPago < valorTotal`) rather than a single "N days" cutoff, so severity in the UI/history filters can scale with age the way the legal-deadline categories already do via `prioridade`.

**Re-notification cadence — the concrete mechanical answer:** for both archetypes, the correct model is **one notification-row creation per entity per calendar day that it remains in a notify-worthy tier** (a new `proximo`→`vencido` transition, or a newly-crossed age bucket), driven by the daily `@Scheduled` job — **never** one creation per bell poll. This distinction matters mechanically: the 30-60s poll interval is a *delivery/freshness* cadence (how fast the UI reflects existing rows), completely decoupled from the *creation* cadence (how often new rows get inserted, which is once/day at most from the scheduled job). Conflating the two — e.g., accidentally re-running scan-and-insert logic on every poll instead of only in the daily job — is a concrete, easy-to-introduce bug worth flagging explicitly for implementation (see Anti-Features). Idempotency (don't insert a second row for the same entity+day if the job runs twice) should be enforced by a uniqueness check (entity id + category + date), not by trusting the scheduler to fire exactly once.

**Snooze/dismiss expectation — genuinely open, not yet decided in PROJECT.md:** general UX literature (MEDIUM confidence) consistently finds that recurring/deadline-style reminders specifically (as opposed to one-shot event notices) generate an expectation of a lightweight "remind me later" affordance, distinct from marking-as-read (which for a still-overdue item is misleading — see Anti-Features) and distinct from a full mute/preference system (which v2.10 has explicitly excluded). The literature's own recommended pattern is "snooze-with-consequence": the item resurfaces later rather than disappearing quietly, so a snooze cannot become a silent permanent mute of something still legally/financially critical. **This does not conflict with the "no per-user notification preferences" exclusion** if scoped narrowly: a snooze implemented as an ephemeral `snoozed_until` timestamp on the individual notification row (or suppressing that day's re-creation for that entity) is a per-instance UI action, not a persisted per-user/per-category configuration row — a meaningfully different thing from "silenciar categorias" which PROJECT.md correctly excludes. Recommend REQUIREMENTS.md make an explicit call on this rather than let it fall through by omission: it is not in the Active list, and the current Out-of-Scope wording ("Preferências de notificação por utilizador (silenciar categorias)") does not obviously cover it either way.

### Differentiators (Competitive Advantage)

| Feature | Value Proposition | Complexity | Notes |
|---------|--------------------|------------|-------|
| Lightweight per-notification snooze/dismiss for the two recurring deadline categories | Directly addresses the "won't a daily job just re-notify me forever" concern without reopening the excluded preferences system — see dedicated section above | MEDIUM | Only makes sense for `prazo`/`calendário` and `honorário` categories (recurring); meaningless for the 4 one-shot categories. Scope as ephemeral row-level state, not user-level config, to stay clear of the excluded scope |
| Grouping/digest for high-volume categories | Research (MEDIUM confidence, cites Braze data via a secondary source) associates digesting with materially higher engagement and lower "tune-out" than one-notification-per-event; most directly relevant to `documento` (a processo can receive several uploads in a burst) and to the daily deadline scan if a single responsável has many prazos crossing tiers the same day | MEDIUM-HIGH | Given ADMIN is co-targeted on **every** category (already decided), ADMIN's inbox aggregates across the entire tenant's activity — this makes grouping/digest more valuable for ADMIN's experience specifically than for an individual responsável's, since ADMIN's unread count grows fastest. Worth validating with real post-launch volume before building rather than speculatively |
| Inline actionable notifications | Letting a user act (e.g., "marcar concluído" on a prazo, approve a parecer) directly from the bell/history without navigating away reduces friction; standard "actionable notification" pattern in modern notification centers | MEDIUM-HIGH | Only viable where the underlying action endpoint already exists (e.g., `Prazo`/`Evento` conclude endpoints) — don't build new business actions just to expose them here |
| Severity-tiered visual treatment + urgency-based sort in the history page | Reusing/extending `computeRisco()`'s existing vencido/próximo/ok vocabulary as color coding (already a familiar convention to users of the Agenda page) rather than inventing new severity language | LOW-MEDIUM | Cheap to add given the underlying computation is being consolidated anyway; mostly a rendering decision once the shared logic exists |
| Real-time delivery (WebSocket/SSE) | Would remove the 30-60s lag entirely | HIGH | No existing real-time infrastructure anywhere in the project (confirmed: zero WebSocket/SSE usage). Disproportionate for a practice-management tool at this tenant/user scale — flag as a future consideration only, not a differentiator worth pursuing now |
| @mentions / free-text collaborative notifications | Common in adjacent tools with comment/chat threads (e.g., Notion, Slack-integrated case tools) | HIGH | No anchor feature exists in LexCV today — there is no comment/discussion thread on any entity to "mention" someone within. Would require building that feature first; not realistically actionable for v2.10 or its near successors |

### Anti-Features (Commonly Requested, Often Problematic)

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|------------------|-------------|
| Per-user notification preferences (mute/configure by category) | Feels like standard SaaS polish — "let me turn off what I don't care about" | Already explicitly excluded in PROJECT.md, and evidence supports the exclusion at this scale: with only 6 fixed categories and small per-tenant teams, a full preferences UI is disproportionate config-surface for the value delivered; it also risks the exact malpractice-adjacent failure mode research flags — a user silencing a category that included a real deadline | If noise becomes a real problem post-launch, the narrower per-instance snooze (see Differentiators) is the escape valve — it solves the "I'm getting reminded too often" complaint without a persisted mute |
| Email/push delivery | Obvious "why not also email me" ask once in-app exists | Already explicitly excluded in PROJECT.md; correctly so — it drags in SMTP/push infrastructure, deliverability, unsubscribe-compliance concerns, and a second targeting/preferences surface, none of which exist in this project today | In-app only, as decided; revisit only as a dedicated future milestone if users request it post-launch |
| Real-time WebSocket/SSE push | Feels more "modern" than polling | No real-time infrastructure exists anywhere in this codebase; introducing it solely for notifications is a large infrastructure investment for a freshness gain (30-60s vs. instant) unlikely to matter for a practice-management tool's usage pattern | Polling via TanStack Query, as already decided |
| Notify the full processo team on reassignment (all advogados/administrativos, not just responsável) | Feels more "complete" — surely everyone on the matter should know | Already explicitly excluded in PROJECT.md; each additional recipient type multiplies the "who gets what" surface this milestone is trying to keep simple, and the evidence base (SLA/ticketing escalation patterns) supports narrow, role-specific targeting over broadcast | Single `responsavelId` target + ADMIN, as decided; equipe-wide notification is a defensible future milestone, not a v2.10 gap |
| Mass/permission-based broadcast (e.g., notify every user with `documentos:view`) | Simplest to implement — "just check who can see it" | Already explicitly rejected in Key Decisions ("nunca notificação em massa por permissão de visualização") for good reason: it would notify every TECNICO in the tenant on every single document upload company-wide, producing exactly the alert-fatigue failure mode the research repeatedly warns about (users desensitize and start ignoring the bell entirely) | Relationship-derived targeting only: responsável/advogado/cliente-team (`ClienteAdvogado`/`ClienteAdministrativo`) + ADMIN |
| Re-running deadline-scan/business logic on every bell poll | Seems simpler than maintaining a separate scheduled job — "just recompute risco() on every fetch," as the current v2.1 bell already does for eventos | Conflates delivery cadence (30-60s freshness) with creation cadence (should be ≤1x/day); re-running full scan logic on every poll either duplicates notification rows or requires expensive de-dup checks on every request instead of once a day. This is precisely the gap the v2.1 bell has today, which v2.10 exists to fix — repeating the same mistake in the new system would be a regression, not a fix | New `@Scheduled` daily job does the scan-and-insert exactly once (idempotent per entity+day); the poll only ever reads already-created rows |
| Silently letting an unresolved critical/`vencido` item stop re-notifying after its first alert | Simplifies logic — treat every category the same as the 4 one-shot event types | Directly contradicts the legal-malpractice risk-management principle found repeatedly in docketing-system research: a still-overdue prazo or still-unpaid honorário must keep resurfacing (e.g., daily) until resolved — going silent after one notification is the single most dangerous anti-pattern for exactly the deadline categories this milestone is trying to make safer | Daily re-evaluation with idempotent, at-most-once-per-day re-creation (see dedicated section above), continuing until the underlying condition (`concluido`, `totalPago >= valorTotal`) resolves |
| Treating "marked as read" as equivalent to "the underlying item is resolved" | Convenient to conflate in the UI — one click, done | A read prazo notification is not a completed prazo, and a read honorário-overdue notification is not a paid invoice; conflating notification-read-state with business-state would let a user "clear" a critical alert without actually acting on it, defeating the purpose of the alert entirely | Keep `lida`/`lida_em` strictly scoped to the notification row; business resolution stays driven by the existing entity fields (`Prazo.concluido`, `Evento.concluido`, `Honorario.totalPago`/`valorTotal`) which the notification only points at |

## Feature Dependencies

```
Persisted Notificacao entity + list/mark-read/mark-all-read API
    └──prerequisite-for──> Bell (backend-driven unread count)
    └──prerequisite-for──> /notificacoes history page + filters
    └──prerequisite-for──> all 6 alert categories below
    (no new external dependency — reuses existing tenant_id/JWT/User patterns already used by every other entity)

Bell w/ unread count (replaces v2.1 client-computed useUpcomingEventos bell)
    └──requires──> Notificacao entity + API

/notificacoes history page + filters
    └──requires──> Notificacao entity + API
    └──requires──> `categoria` stored as a column on Notificacao (not derived at render time) — filters break otherwise

Alerta de entrada de nova fase
    └──requires──> ProcessoFase (exists) transition point — wherever a fase is activated (`ativa` flip) in ResourceController
    └──targets──> Processo.responsavelId + ADMIN

Alerta de novo documento em processo/cliente
    └──requires──> Documento (exists) creation hook — the upload endpoint
    └──targets──> depends on which FK is set: Processo.responsavelId (if processoId) or cliente team via ClienteAdvogado/ClienteAdministrativo (if clienteId) + ADMIN
    └──risk──> a document can have neither/both FKs populated depending on upload path — recipient-resolution logic must handle both cases explicitly, not assume processoId

Alerta de processo atribuído + reatribuição de responsável
    └──hard-requires──> NEW reassignment endpoint (does not exist yet — Processo.responsavelId is creation-only today)
    └──already-correctly-scoped──> PROJECT.md's Active list already pairs these as one roadmap item, which is the right call — the alert is meaningless without the endpoint existing first
    └──targets──> new Processo.responsavelId (post-reassignment) + ADMIN; consider whether the *previous* responsável should also be notified (common pattern in ticketing systems is to notify the outgoing assignee too) — an open scoping question for REQUIREMENTS.md, not yet addressed in PROJECT.md

Alerta de parecer atribuído
    └──requires──> ParecerSolicitacao.advogadoId (exists, already settable at creation)
    └──open-question──> confirm during requirements/roadmap whether advogado reassignment after creation is already possible today; if not, same category of dependency as processo reassignment applies (smaller scope, since parecer has no separate "reassign" flow mentioned in PROJECT.md)
    └──targets──> ParecerSolicitacao.advogadoId + ADMIN

Alerta de prazos de processos e calendário crítico
    └──hard-requires──> Consolidated "prazo crítico" shared logic (already flagged as prerequisite architecture work in Key Decisions — replacing 4 inconsistent implementations: dashboard KPI, sino v2.1, /eventos/upcoming, agenda page)
    └──hard-requires──> NEW @Scheduled daily job infrastructure (first one in the project)
    └──reads──> Prazo (dataLimite, prioridade, responsavelId, concluido) and Evento (dataInicio/dataFim, prioridade, concluido) — both exist, no schema change needed
    └──targets──> Prazo.responsavelId, or the associated Processo's responsável for Evento + ADMIN
    └──ui-note──> even though the underlying risk computation is consolidated into one function, the notification `categoria` shown/filterable to users can still distinguish "Prazo de Processo" from "Evento de Agenda" as separate filter values if desired — consolidation is about the logic, not necessarily about collapsing the user-facing label into one bucket

Alerta de honorário não pago (dias desde dataAcordo)
    └──requires──> Honorario.dataAcordo + totalPago (both exist; totalPago is a computed @Formula, consistent with the explicit decision not to add a due-date column)
    └──requires──> same NEW @Scheduled daily job infrastructure as the deadline category above — recommend one scheduled trigger with multiple scan functions internally, not two independently-scheduled jobs that can drift apart
    └──targets──> Processo.responsavelId (via Honorario.processoId → Processo) + ADMIN

Grouping/digest (differentiator)
    └──enhances──> all 6 categories, highest value for documento and the daily deadline scan specifically
    └──additive──> does not block MVP; requires only an aggregation query over the base Notificacao table

Snooze/dismiss for deadline reminders (differentiator, open question)
    └──enhances──> prazo/calendário + honorário categories specifically (the two recurring ones)
    └──compatible-with──> current Out-of-Scope wording, IF implemented as ephemeral per-notification state
    └──would-violate-scope-if──> implemented as a persisted per-user/per-category mute (duplicates the explicitly excluded "Preferências de notificação por utilizador")
```

## MVP Definition

### Launch With (v2.10 — already committed in PROJECT.md Active)

- [ ] Persisted `Notificacao` entity + list/mark-read/mark-all-read API — foundation, everything else depends on it
- [ ] Bell with backend-driven unread count, 30-60s poll via TanStack Query — replaces the v2.1 computed bell
- [ ] `/notificacoes` history page with filters (categoria, lida/não-lida, intervalo de datas)
- [ ] Deep-link per category to its correct source entity (6 distinct destination mappings, including the two-FK case for documentos)
- [ ] Actor-exclusion as a shared cross-cutting rule in the notification-creation helper
- [ ] All 6 alert categories exactly as scoped: fase, documento, atribuição/reatribuição de responsável (+ new reassignment endpoint), parecer atribuído, prazo+calendário crítico (consolidated, daily job), honorário não pago (daily job)
- [ ] Targeting strictly limited to responsável/advogado/equipa-do-cliente + ADMIN — no broadcast-by-permission

### Add After Validation (v2.11+, only if real usage data shows need — do not build speculatively)

- [ ] Grouping/digest for `documento` and the daily deadline-scan output, if post-launch unread-count growth shows real flooding (validate with data, especially for ADMIN's aggregated inbox, before building)
- [ ] Lightweight per-notification snooze ("lembrar amanhã") scoped to the two recurring categories only, if the daily job's re-surfacing proves annoying in practice — build as ephemeral row state, not user preference
- [ ] Inline actionable notifications (e.g., "marcar concluído" directly from the bell) for prazo/evento categories, reusing existing conclude endpoints
- [ ] Whether the previous responsável (not just the new one) should be notified on reassignment — open question, decide with real usage feedback

### Future Consideration (v2+, explicitly deferred or newly identified as premature)

- [ ] Full per-user notification preferences/mute-by-category — explicitly out of scope in PROJECT.md
- [ ] Email/push delivery — explicitly out of scope in PROJECT.md
- [ ] Real-time WebSocket/SSE delivery replacing polling — no infrastructure exists, no evidenced need at this scale
- [ ] @mentions / free-text collaboration alerts — no anchor comment/discussion feature exists yet to hang mentions off
- [ ] Full processo-team notification (beyond the single responsável) — explicitly out of scope in PROJECT.md

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|----------------------|----------|
| Persisted Notificacao entity + API | HIGH | MEDIUM | P1 |
| Bell w/ unread count (backend-driven) | HIGH | LOW | P1 |
| `/notificacoes` history page + filters | HIGH | MEDIUM | P1 |
| Deep-link to source entity (6 categories) | HIGH | MEDIUM | P1 |
| Actor exclusion | MEDIUM | LOW | P1 |
| Alerta de nova fase | MEDIUM | LOW | P1 |
| Alerta de novo documento | MEDIUM | LOW-MEDIUM | P1 |
| Alerta de atribuição + reatribuição endpoint | HIGH | MEDIUM-HIGH | P1 |
| Alerta de parecer atribuído | MEDIUM | LOW | P1 |
| Consolidated prazo crítico logic + daily job | HIGH | HIGH | P1 |
| Alerta de prazos/calendário (uses consolidated logic) | HIGH | MEDIUM (given job exists) | P1 |
| Alerta de honorário não pago | MEDIUM-HIGH | MEDIUM (shares job infra) | P1 |
| Severity-tiered visual treatment | LOW-MEDIUM | LOW | P2 |
| Grouping/digest | MEDIUM | MEDIUM-HIGH | P2 |
| Snooze/dismiss (deadline categories) | MEDIUM | MEDIUM | P2 |
| Inline actionable notifications | MEDIUM | MEDIUM-HIGH | P2 |
| Real-time WebSocket/SSE | LOW (at this scale) | HIGH | P3 |
| @mentions | LOW (no anchor feature) | HIGH | P3 |
| Per-user preferences/mute | LOW (per explicit decision) | MEDIUM-HIGH | P3 |
| Email/push delivery | LOW (per explicit decision) | HIGH | P3 |

**Priority key:**
- P1: Must have for v2.10 launch (matches PROJECT.md's already-committed Active scope)
- P2: Should have, add once real usage data justifies it
- P3: Explicitly deferred/out of scope per PROJECT.md, or no current driver

## Competitor Feature Analysis

| Feature | Legal PM (Clio/MyCase/PracticePanther) | Accounting PM (Karbon/TaxDome/Canopy) | LexCV v2.10 Approach |
|---------|------------------------------------------|----------------------------------------|------------------------|
| Rules-based/tiered deadline reminders | Court-rules engines (often via LawToolBox integration) auto-calculate and tier deadlines by jurisdiction | Recurring-job schedulers with reminders, task dependencies, Kanban due-date tracking | No court-rules engine (out of scope) — reuses/extends existing `computeRisco()` priority-based tiering instead of jurisdiction rules |
| Redundant/dual verification of critical dates | Docketing systems (MatterAlert, Aderant) emphasize a second-person check or duplicate docket as risk mitigation | Less emphasized — accounting deadlines are typically lower-stakes than statutes of limitation | Achieved indirectly: ADMIN is always co-targeted alongside the responsável on every category (already decided), giving a form of redundant visibility without a formal second-docket system |
| Assignment/reassignment notifications | Native task/matter assignment notifications are standard | Native task assignment notifications standard (Karbon's task system in particular) | New in v2.10 — requires building the reassignment endpoint first (does not exist today) |
| Document/upload notifications | Standard (new document on matter triggers activity feed entry) | Standard (client portal document upload triggers alerts) | In-app equivalent, scoped to responsável/cliente-team, not broadcast |
| Client-facing reminders (client portal) | MyCase/PracticePanther send reminders directly to clients (email/SMS/portal) | TaxDome/Canopy portals notify clients of pending items | Not applicable — LexCV has no client portal; out of scope entirely for v2.10 |
| In-app notification center depth (bell + history + filters) | Present but typically secondary to email-first alerting in these products | Present, generally tied to task/Kanban views rather than a dedicated inbox | This is LexCV's primary and only channel by explicit decision — makes the in-app experience proportionally more important to get right than in products where email is the primary channel |
| Real-time vs. polling delivery | Mixed; largely non-real-time (page-load/refresh-based) except where paired with chat-style client portals | Similar — mostly refresh/poll-based, not WebSocket-driven | Polling (30-60s), consistent with the norm in this product category, not a competitive gap |
| Preferences/mute granularity | Typically offers per-category email/SMS toggle | Typically offers per-category notification toggle | Deliberately excluded for v2.10; acceptable given small per-tenant team sizes and only 6 fixed categories |

## Sources

**In-app notification center UX (HIGH confidence — multiple corroborating sources):**
- [In-App Notification Center for SaaS: Design Patterns and Implementation Guide](https://www.suprsend.com/post/in-app-notification-center)
- [What is App Inbox Notification Center and How to Use it in Your SAAS Product?](https://medium.com/@nikita_79236/what-is-app-inbox-notification-center-and-how-to-use-it-in-your-saas-product-e5877086da1a)
- [Notification UX: 8 Best Practices + Real Examples](https://www.eleken.co/blog-posts/notification-ux)
- [Notification System Design: Architecture & Best Practices](https://www.magicbell.com/blog/notification-system-design)
- [Notifications UI design: Why most apps annoy users instead](https://www.setproduct.com/blog/notifications-ui-design)
- [Notification Design: UX, Performance, Security — Courier](https://www.courier.com/guides/how-to-build-a-notification-center/chapter-3-best-practices-for-notification-centers)

**Legal practice management / docketing (MEDIUM confidence — vendor/marketing sources, internally consistent):**
- [Best Legal Practice Management Software for Your Firm — MyCase](https://www.mycase.com/blog/legal-case-management/best-legal-practice-management-software/)
- [Docketing System: Mastering Law Firm Deadlines and Compliance — RunSensible](https://www.runsensible.com/blog/docketing-system-law-firm-deadlines/)
- [MatterAlert - Legal Matter Docketing and Calendaring System](https://paayatech.com/matteralert/)
- [The Trick for Missing Fewer Court Deadlines — CARET Legal](https://caretlegal.com/blog/the-trick-for-missing-fewer-court-deadlines/)
- [Attorney Deadline Management: How to Prevent Malpractice — AttorneyReview](https://attorneyreview.com/blog/attorney-deadline-management-systems) (source of the 30/14/7/3/1-day cadence and per-deadline-type buffer table)
- [Missed Deadlines: A Litigator's Constant Fear — CARET Legal](https://caretlegal.com/blog/malpractice-for-missed-deadlines-a-litigators-constant-fear-how-to-curb-it/)

**Accounting practice management (MEDIUM confidence):**
- [TaxDome vs. Canopy — Karbon Magazine](https://karbonhq.com/resources/taxdome-vs-canopy/)
- [Karbon vs Canopy vs TaxDome: How CPA Firms Choose — US Tech Automations](https://ustechautomations.com/resources/blog/karbon-vs-canopy-vs-taxdome-2026)
- [10 Best Workflow Management Software for Accountants — TaxDome](https://taxdome.com/blog/workflow-management-software-for-accountants)

**SLA/escalation-tier patterns (MEDIUM-HIGH confidence, ITSM/helpdesk domain, transferable pattern):**
- [Build SLA Breach Alerts Without Coding Easily](https://www.lowcode.agency/blog/sla-breach-alert-automation-no-code)
- [How To Design SLA-Aware Escalation Workflows That Actually Work — Unito](https://unito.io/blog/sla-aware-ticket-escalation-workflows/)
- [SLA Severity Levels Explained: Critical vs. High vs. Medium](https://www.atlassystems.com/blog/sla-severity-levels)

**AR/collections dunning cadence (HIGH confidence — multiple converging independent sources):**
- [Dunning Letter: Definition, Examples & Free Templates — Centime](https://www.centime.com/learning-center/dunning-letter)
- [The Dunning Process: How It Works and When to Escalate](https://www.creditpulse.com/blog/dunning-process-guide)
- [What is Dunning in Accounts Receivable — FinanceOps](https://financeops.ai/blogs/what-is-dunning-in-accounts-receivable-examples-and-best-practices)

**Notification fatigue / grouping / digest (MEDIUM confidence):**
- [How to Reduce Notification Fatigue: 7 Proven Product Strategies — Courier](https://www.courier.com/blog/how-to-reduce-notification-fatigue-7-proven-product-strategies-for-saas)
- [Best Practices – How to Not Over Notify Your Users — Novu](https://novu.co/blog/digest-notifications-best-practices-example/)
- [Best practices for Batching & Digest — SuprSend docs](https://docs.suprsend.com/docs/best-practices-for-batching-digest)

**Snooze/dismiss and recurring-reminder patterns (MEDIUM confidence):**
- [A Comprehensive Guide to Notification Design — Toptal](https://www.toptal.com/designers/ux/notification-design)
- [How to Create Contextual Reminders in a Mobile App Without Overload](https://koder.ai/blog/create-mobile-app-contextual-reminders-without-overload)

**Actor-exclusion / reassignment notification convention (HIGH confidence, standard documented platform behavior):**
- [About the standard ticket triggers — Zendesk help](https://support.zendesk.com/hc/en-us/articles/4408828984346-About-the-standard-ticket-triggers)
- [How to set up Zendesk trigger notify assignee on assignment](https://www.eesel.ai/blog/zendesk-trigger-notify-assignee-on-assignment)

**Alert fatigue / "boy who cried wolf" (HIGH confidence, well-established cross-industry phenomenon):**
- [What Is Alert Fatigue & How to Combat It? — NinjaOne](https://www.ninjaone.com/blog/what-is-alert-fatigue/)
- [Understanding and fighting alert fatigue — Atlassian](https://www.atlassian.com/incident-management/on-call/alert-fatigue)

**Notification data model / multi-tenant architecture (HIGH confidence, general system-design consensus):**
- [Building Scalable Notifications: A Journey to the Perfect Database Design — Medium](https://medium.com/@aboud-khalaf/building-scalable-notifications-a-journey-to-the-perfect-database-design-part-1-a7818edad0ba)
- [Designing a Notification System — DesignGurus](https://www.designgurus.io/course-play/grokking-system-design-interview-ii/doc/designing-a-notification-system)

**Codebase (primary source, HIGH confidence — read directly):**
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (`computeRisco`, `/processos/{id}/prazos`, `responsavelId` handling)
- `backend/src/main/java/com/lexcv/models/{Processo,Prazo,Evento,Honorario,ParecerSolicitacao,Documento,ProcessoFase}.java`
- `web/src/components/shared/notification-bell.tsx`
- `.planning/PROJECT.md`

---
*Feature research for: in-app notifications/alerts, LexCV v2.10*
*Researched: 2026-07-08*
