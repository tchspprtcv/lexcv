# Feature Research: Notification Preferences, Team Targeting & Snooze (NOTF-24/25/26)

**Domain:** In-app notification system extensions for multi-tenant legal practice management (LexCV v2.11) — per-user category muting, case-team-wide alert fan-out, deadline-reminder snooze
**Researched:** 2026-07-12
**Confidence:** MEDIUM-HIGH (codebase-grounded dependency claims are HIGH confidence — verified directly against the shipped v2.10 `Notificacao`/`NotificacaoService`/`AlertasDiariosJob` code; cross-product UX pattern claims are MEDIUM confidence, cross-verified across 2+ independent sources each; no Context7 coverage exists for this domain, so all ecosystem claims come from WebSearch and are flagged accordingly)

> Supersedes the v2.10-dated `FEATURES.md` previously at this path (that research answered "should a notification system exist at all" for v2.10; this one answers three specific extension questions for v2.11 against the system v2.10 actually shipped). Per the milestone brief, this file does **not** re-research the existing notification plumbing — it treats `Notificacao`, `NotificacaoService`, `NotificacaoController`, and `AlertasDiariosJob` as fixed and researches only how NOTF-24/25/26 attach to them.

## Context Recap — What v2.10 Actually Shipped (verified in code, not inferred)

- `Notificacao` (`backend/src/main/java/com/lexcv/models/Notificacao.java`) is a flat, per-recipient row: `tenantId`, `destinatarioId`, `categoria` (free-form string, 9 known values in use), `entidadeTipo`/`entidadeId`, `titulo`/`mensagem`/`linkUrl`, `lida` (boolean), `createdAt`. No existing column expresses "muted," "team," or "snoozed" — all three are net-new concerns.
- The 9 live `categoria` values: `FASE_ENTRADA`, `DOCUMENTO_NOVO`, `PROCESSO_ATRIBUIDO`, `PARECER_ATRIBUIDO` (event-triggered, Phase 87) and `PRAZO_PROXIMO`, `PRAZO_VENCIDO`, `EVENTO_PROXIMO`, `EVENTO_VENCIDO`, `HONORARIO_ATRASADO` (daily-job-generated, Phase 88).
- `NotificacaoService.criar(...)` is the **single write choke point** for row creation across the whole subsystem (event triggers in `ResourceController`/`ParecerController` and the daily job in `AlertasDiariosJob` both funnel through it). Any new gating logic (mute check, team resolution) is cheapest to add here or immediately around it, not duplicated at each of the ~9 call sites.
- `AlertasDiariosJob` is already idempotent **per (tenantId, destinatarioId, entidadeTipo, entidadeId, categoria) tuple**, enforced by both an application-level `existsBy...` check and a DB unique constraint (`uk_notificacao_dedup`). This is the single most important fact for NOTF-26: **the job already never recreates a row for a categoria that has already fired for that entity+recipient.** A `PRAZO_PROXIMO` notification for a given prazo/recipient is created at most once, ever, regardless of how many days the job runs while it stays at that risk tier. "Snooze causing the item to reappear on the very next job run" is therefore not a job-side problem in this codebase — it is entirely a **read-side** problem (the row is still unread, so it still shows in the bell/list). This materially narrows the design space for NOTF-26 (see below).
- Targeting today is asymmetric in a way directly relevant to NOTF-25: a **cliente**-linked `DOCUMENTO_NOVO` already fans out to the *whole* client team (`ClienteAdvogado` + `ClienteAdministrativo`, deduplicated via `LinkedHashSet`) plus ADMIN. A **processo**-linked `DOCUMENTO_NOVO` — and `FASE_ENTRADA`, and `PROCESSO_ATRIBUIDO` — notify only `Processo.responsavelId` (a single `UUID` field) plus ADMIN. `Processo` has no `equipa`/team relation of its own; it only carries `clienteId`. `ParecerSolicitacao.processoId` is **nullable** (a parecer can be linked to a cliente only) — this matters directly for scoping NOTF-25 below.
- No per-user preference concept exists anywhere in the backend today (no `NotificacaoPreferencia`-equivalent table, no settings endpoint). `PROJECT.md`'s v2.10 Out-of-Scope entry ("todas as categorias são sempre entregues") is the thing NOTF-24 explicitly reverses.
- Delivery is polling-only (TanStack Query, 30s) — no push/email/SMS channel exists, which simplifies NOTF-24 considerably versus generic multi-channel preference-center literature (see Anti-Features).

---

## NOTF-24 — Per-User Notification Preferences (mute categories)

### How this typically works elsewhere

Every mature multi-category notification product surveyed (GitHub, Jira, Asana, Slack, and the generic SaaS notification-preference literature) converges on the same shape: a **per-user, per-category on/off setting**, defaulting to "on," presented as a flat settings list rather than buried in the notification stream itself. The one recurring, cross-source caveat is that **safety-critical or legally-significant categories are marked non-negotiable** — shown in the preference UI (for transparency) but not toggleable — precisely so a user cannot silently opt out of something whose absence causes real harm (security alerts in generic SaaS; by direct domain analogy here, an overdue-deadline alert in a legal practice tool). Multi-channel products (email/push/SMS/in-app) use a category×channel matrix; LexCV has exactly one channel today, so the matrix pattern is not applicable — a flat per-category toggle list is the correct scope.

### Category breakdown

| Aspect | Table Stakes | Differentiator | Anti-Feature |
|---|---|---|---|
| Per-category on/off toggle, defaulting to "on" (opt-out model) | Yes — universal in every product surveyed once a system has >3 categories; users expect it once the app has enough notification volume to be annoying (LOW-MEDIUM complexity: one new join-style table + one settings endpoint + one settings-page section) | | |
| Non-mutable "cannot silence" categories for the highest-severity tier (`PRAZO_VENCIDO`, `HONORARIO_ATRASADO`) | | Differentiator specific to a legal-deadline domain — general SaaS guidance says "security/legal notices should not be opt-outable"; LexCV's direct analogue is an *already-breached* deadline. Missed-deadline is literature's most-cited cause of legal malpractice claims, which raises the stakes of a silent full mute above typical SaaS annoyance-reduction | |
| Category×channel preference matrix | | | Anti-feature **right now**: this is the standard generic pattern, but LexCV has one delivery channel (in-app polling). Building a matrix UI for a single column is premature complexity with no present payoff — revisit only if/when email/push is ever added (currently explicitly Out of Scope in `PROJECT.md`) |
| Preference change taking effect immediately for future notifications, not retroactive to already-created rows | Yes — matches how every reviewed product treats mute (stops future noise, doesn't rewrite history) | | |
| Admin-level override of another user's personal preference | | | Anti-feature: requirement is explicit ("não global, não por tenant") — an ADMIN "unmute for everyone" or "force-mute a user" control would violate the stated per-user scope and reintroduce exactly the broadcast-creep pattern v2.10 deliberately avoided |

### Dependencies on the existing architecture

- **New entity, not a column on `User`:** a `NotificacaoPreferencia`-style row per `(tenantId, userId, categoria)` mirrors the existing join-table convention already used for `ClienteAdvogado`/`ClienteAdministrativo` (unique constraint on the triple; absence of a row = "not muted," matching the opt-out default). This is lower-risk than a single JSON/CSV column on `User` given the project's established preference for typed join tables over JSON blobs for anything queried (see `PROJECT.md` Key Decisions: the `dados_tipo` JSON-column approach was explicitly reverted in v2.7 for exactly this reason).
- **Enforcement point — recommend creation-time, not read-time only:** check the preference inside (or immediately around) `NotificacaoService.criar(...)` before inserting a row, mirroring the per-recipient try/catch isolation pattern already established there (CR-01/CR-02 review comments: one bad recipient must never block the rest of a fan-out). This avoids ever persisting a row that will never be shown, keeps `unread-count` accurate without an extra join, and — because a fan-out loop (team, ADMIN) already calls `criar()` once per recipient — a mute check here naturally applies per-recipient with zero extra plumbing.
- **Interaction with `AlertasDiariosJob`'s idempotency tuple:** if a category is muted at the moment the job would have created a row, no row is created, so nothing is "skipped" that could later resurface stale — the tuple simply doesn't exist yet. If the user un-mutes later, the next daily run creates it fresh (max 1-day latency), correctly. For the four **event-triggered** categories (one-shot: `FASE_ENTRADA`, `DOCUMENTO_NOVO`, `PROCESSO_ATRIBUIDO`, `PARECER_ATRIBUIDO`), muting at trigger time means that specific occurrence is permanently missed if unmuted later — this is expected mute semantics, not a bug, but worth stating explicitly since it differs from the job-driven categories' behavior.
- **Interacts with NOTF-25:** once processo-linked triggers fan out to a team (not just `responsavelId`), the mute check must run **per destination user inside the fan-out loop**, not once for the whole notification — otherwise one muted team member would suppress the notification for the rest of the team. The existing per-recipient isolation pattern already used for orphaned-user handling is the same shape needed here.
- **RBAC:** this is inherently self-scoped (a user can only read/write their own preferences), so it does not need a new `scope:action` permission pair — it can piggyback on the existing `notificacoes:view` authority already required for all `/notificacoes/*` endpoints, the same way profile self-service typically needs no additional grant beyond "authenticated."

---

## NOTF-25 — Notify the Full Process Team, Not Just `responsavelId`

### How this typically works elsewhere

Legal-specific practice-management products (Clio is the most directly comparable, MEDIUM confidence from vendor help-center docs) model exactly this distinction: a single **Responsible Attorney** (the equivalent of `responsavelId`) plus a separate, multi-valued **Responsible Staff** field for "any individual also responsible for matter-related tasks other than the responsible attorney" — i.e., matters commonly have one accountable owner and a broader working team, and case-management tooling treats these as two different concepts, not one. General task/PM tooling (Jira "watchers," GitHub "subscribers") reaches the same shape from a different angle: an assignee (primary responsibility, gets the strongest notification language) plus a broader set of people who get informed of activity without being the accountable owner. The common thread across both domains: **team-wide notification is additive to, not a replacement for, the primary-assignee notification** — the primary recipient still gets distinguishable ("you were assigned") messaging, while the rest of the team gets FYI-level ("the matter was...") messaging, exactly mirroring the 2nd-person/3rd-person split LexCV's `NotificacaoService` already uses between the primary recipient and the ADMIN fan-out.

### Category breakdown

| Aspect | Table Stakes | Differentiator | Anti-Feature |
|---|---|---|---|
| Notifying more than the single assignee on a shared matter/case | Yes, once a firm has more than one person working a case — Clio's Responsible Attorney + Responsible Staff split and every general-PM assignee+watchers pattern surveyed treat single-assignee-only as an incomplete/legacy model | | |
| Reusing an *existing* team concept instead of inventing a parallel one | | Differentiator for LexCV specifically: `ClienteAdvogado`/`ClienteAdministrativo` already exist, are already the "equipa" notified for cliente-linked documents, and every `Processo` already has exactly one `clienteId`. Treating "the process team" = "the client's assigned team" reuses proven infrastructure at near-zero schema cost instead of building a parallel `ProcessoEquipa` join table for a concept the data model already expresses one level up | |
| A brand-new `ProcessoEquipa` join table, independent of the client's team | | | Anti-feature (over-engineering for this milestone): duplicates `ClienteAdvogado`/`ClienteAdministrativo` semantics one entity down, doubles the maintenance surface (two places to add/remove a team member), and the milestone's own framing question ("or a new explicit team concept") is answerable today by observing the data model already has the relationship needed — Processo→Cliente→team — without a new table. Revisit only if a firm's real workflow genuinely needs a different team per processo than the client's overall team (not evidenced by anything in `PROJECT.md`) |
| Applying the expansion uniformly to all 4 event triggers without checking each one's semantics | | | Anti-feature: `PARECER_ATRIBUIDO` is a poor fit — `ParecerSolicitacao.processoId` is nullable (a parecer can exist with only a `clienteId`, no processo at all), and a parecer's advogado assignment is domain-modeled as an individual professional-responsibility act, not a case-team broadcast (this mirrors why Clio's Responsible Attorney and general "assignee" notifications stay individual even in team-based tools — accountability language needs one clear owner). Recommend leaving `PARECER_ATRIBUIDO` as-is (individual advogado + ADMIN) and scoping the team expansion to `FASE_ENTRADA`, `DOCUMENTO_NOVO` (processo branch), and `PROCESSO_ATRIBUIDO` |
| Preserving the existing 2nd-person/3rd-person message-copy split for the primary responsável when adding team fan-out | Yes — matches the Clio/Jira "assignee is more strongly addressed than watchers" convention and matches LexCV's own existing precedent (destinatário vs. ADMIN message copy already differ in `notificarProcessoAtribuido`) | | |

### Dependencies on the existing architecture

- **No new entity required** if the "reuse client team" recommendation is adopted: resolve `Processo.clienteId` → `ClienteAdvogadoRepository.findByClienteIdAndTenantId` + `ClienteAdministrativoRepository.findByClienteIdAndTenantId`, exactly the lookup pattern already written and battle-tested in `ResourceController`'s cliente-branch of `DOCUMENTO_NOVO` (lines ~2611-2623). This is a copy-and-adapt of existing code into the three chosen event triggers, not new design.
- **Retroactivity is automatic, not a migration concern:** because team membership is resolved live at notification-creation time (via `clienteId` lookup) rather than snapshotted onto the `Processo` row, every existing processo automatically gets the expanded targeting the moment the code ships — no backfill needed. Processos whose client currently has zero linked advogados/administrativos degrade gracefully to today's exact behavior (responsável + ADMIN only), so there is no regression risk for the (likely common, early-tenant) case of an empty team.
- **Dedup is a solved pattern:** reuse the `LinkedHashSet<UUID>` dedup already in `notificarDocumentoNovo` so a responsável who is *also* a `ClienteAdvogado` for the same client, or a user who is both advogado and administrativo, gets exactly one row per notification event, not two or three.
- **Extends `NotificacaoService.notificarFaseEntrada`, `notificarDocumentoNovo` (processo branch, currently in `ResourceController`), and `notificarProcessoAtribuido`** — each needs its single-`responsavelId` path widened to "responsável (2nd-person copy) + rest of team (3rd-person copy) + ADMIN (3rd-person copy, existing)," with the existing per-recipient try/catch isolation preserved for every new recipient added.
- **Open question worth flagging for roadmap, not resolved by the literal wording of NOTF-25:** the milestone question scopes this to "the 4 existing event triggers," which are the Phase 87 set. The daily-job categories (`PRAZO_PROXIMO`/`VENCIDO`, `EVENTO_PROXIMO`/`VENCIDO`, `HONORARIO_ATRASADO` — Phase 88) currently have the *same* single-`responsavelId` limitation and would become inconsistent with the newly-expanded event triggers if left alone. Recommend the roadmap explicitly decide whether Phase-88 categories get the same team expansion in this milestone or are deliberately deferred — leaving it undecided by omission risks the same kind of "5th inconsistent implementation" pattern this project has already had to consolidate once (`RiscoPrazoService`, Phase 85).

---

## NOTF-26 — Snoozing a Deadline Reminder

### How this typically works elsewhere

Two distinct, well-precedented models exist across the products surveyed, and they answer "what does snooze mean" differently:

1. **Consumer reminder/task apps (Todoist, Any.do, Due — MEDIUM confidence, direct vendor docs):** snooze = pick a concrete resurfacing point (preset intervals like 15 min/1 hour/tomorrow, or an explicit date/time) and the reminder simply stops showing until then, reappearing automatically and unconditionally at that point. Several of these apps explicitly warn against a "snooze forever" affordance — every preset has a concrete resurfacing time, never an open-ended dismiss.
2. **Compliance/legal-deadline tooling (IntelligentContract — MEDIUM confidence, single vendor source but the closest direct domain analogue found, a contract-compliance-deadline alerting product): per-user configurable "snooze length" (default 3 days) that **overrides the standard reminder frequency once**, after which the alert reverts to the normal recurring cadence if still unresolved. Critically, the source is explicit that snoozing is a **temporary suppression of re-*notification*, never a way to make the underlying deadline stop being tracked** — the alert always eventually comes back if the deadline itself is still open.

Both models converge on the same underlying principle directly relevant to LexCV: **snooze must have a concrete, bounded resurfacing point, and must never silently and permanently suppress an item that is still substantively open** (still-unpaid honorário, still-unmet prazo). Neither model treats snooze as equivalent to "mark as read" — a still-open, snoozed item that quietly counted as "read" would misrepresent the user's actual attention state to anyone reviewing the account later (a second reviewer, an ADMIN, an audit trail), which is a specifically bad property for a domain where missed deadlines are a malpractice exposure.

### Category breakdown

| Aspect | Table Stakes | Differentiator | Anti-Feature |
|---|---|---|---|
| Snooze = hide until a concrete future date/time, then reappear automatically as unread | Yes across every reminder-app precedent found — snooze without a bounded resurfacing point isn't snooze, it's dismiss-in-disguise | | |
| Snooze that cannot suppress a *worse* subsequent alert for the same underlying deadline (escalation breaks through) | | Differentiator, and specifically the safest design for this domain: because `AlertasDiariosJob` already creates a **new row per categoria transition** (a `PRAZO_PROXIMO` row and a later `PRAZO_VENCIDO` row for the same prazo are two different rows, two different categoria values), scoping snooze to the individual notification row rather than to "this prazo, permanently" means a snooze of today's "approaching" notice cannot, even accidentally, hide tomorrow's "overdue" notice — they are different rows with independent snooze state | |
| Snooze presets (+1 dia / +3 dias / +7 dias / data específica) rather than snooze-until-forever | Yes — matches Todoist/Any.do/Due preset patterns and IntelligentContract's per-user configurable length; "no expiry" option is explicitly the thing to avoid, not the thing to add | | |
| Snooze silently setting `lida = true` as a side effect | | | Anti-feature: conflates two independent signals ("I looked at this" vs. "hide this for now") — a snoozed-but-never-actually-read item that shows as read would understate real outstanding risk to anyone else reviewing the account (ADMIN fan-out recipients, a future audit). Keep `lida` and `snoozedUntil` orthogonal columns/state |
| A visible "snoozed" filter/tab on `/notificacoes` so deferred items aren't invisible forever | | Differentiator (small effort, meaningfully closes the loop) — mirrors Gmail's "Snoozed" folder and GitHub's "snoozed" issue list; without it, a user has no way to see what they've deferred except waiting for it to resurface |

### Dependencies on the existing architecture, and the concrete answer to "how do other systems avoid it reappearing on the very next job run"

- **This is not a job-side problem in LexCV, and does not need to be.** `AlertasDiariosJob` is already edge-triggered/idempotent per `(tenant, destinatario, entidadeTipo, entidadeId, categoria)` (Phase 88, DB-backed by `uk_notificacao_dedup`). It will never re-create a second `PRAZO_PROXIMO` row for the same prazo+recipient while the risk stays at that tier — that dedup already exists and needs no change for NOTF-26. The entire "avoid reappearing" concern is a **read-side filtering problem**: an unread row that's still unread will keep showing in the bell/list regardless of the job, simply because nothing has changed its visibility state. Snooze's job is exclusively to add a visibility filter, not to touch the job's creation logic.
- **Recommended shape: add `snoozedUntil` (nullable `LocalDateTime`) directly to `Notificacao`,** not a separate table — it is per-instance ephemeral state on a single row, structurally unlike NOTF-24's per-user-per-category preference (a standing rule) and unlike `lida` (permanent, one-way). A nullable column with a straightforward `WHERE snoozedUntil IS NULL OR snoozedUntil <= NOW()` predicate is the lowest-complexity correct implementation.
- **Three existing read paths need this predicate added, and each needs independent thought about "should mark-all-read reach into snoozed items?":**
  - `NotificacaoRepository.buscarPorFiltros` (feeds `/notificacoes` page + bell dropdown, per `useNotificacoes(filters, { poll })`) — needs the visibility predicate, plus (per the differentiator above) an explicit way to *opt into* seeing snoozed items via a filter value, rather than only ever hiding them.
  - `countByTenantIdAndDestinatarioIdAndLidaFalse` (bell badge) — must exclude snoozed-but-unread rows, or the badge count would contradict what the dropdown actually shows, reintroducing exactly the kind of surface-inconsistency Phase 89's shared `useNotificacoes` hook was built to prevent.
  - `findByTenantIdAndDestinatarioIdAndLidaFalse` (feeds "mark all as read") — needs an explicit decision: should "mark all as read" reach into currently-snoozed rows and silently mark them read too? Recommend **no** — marking a snoozed item read defeats the purpose of having snoozed it in the first place (see the `lida`/`snoozedUntil` orthogonality anti-feature above); scope "mark all read" to only the currently-visible (non-snoozed) unread set.
- **New endpoint(s):** `PATCH /notificacoes/{id}/snooze` (body: target datetime or a preset key) and an unsnooze path (either the same endpoint with `null`, or a `DELETE`) — same dual tenant+destinatario scoping already established in `NotificacaoController` (the codebase's first per-recipient-private resource; every new mutation on it must keep both scoping dimensions to avoid the exact IDOR-adjacent risk `NotificacaoController`'s own header comment already calls out).
- **No interaction with NOTF-24's mute:** a muted category never creates a row to begin with, so there is nothing to snooze; a snoozed row belongs to a category the user has *not* muted (they still want to see it — just not right now). The two features are orthogonal and can be built independently.

---

## Feature Dependencies

```
NOTF-24 (per-user category mute)
    reads/gates ──> NotificacaoService.criar(...)  [existing single write choke point]
    interacts with ──> NOTF-25's fan-out loop (mute must be checked per-recipient inside
                        the team fan-out, not once per event)
    orthogonal to ──> NOTF-26 (mute prevents row creation; snooze hides an already-created row —
                        no row exists to snooze once muted)

NOTF-25 (team-wide targeting)
    requires ──> decision: reuse ClienteAdvogado/ClienteAdministrativo via Processo.clienteId
                 (recommended, zero new entities) vs. new ProcessoEquipa table (higher cost, not
                 evidenced as necessary)
    extends ──> notificarFaseEntrada, notificarDocumentoNovo (processo branch),
                notificarProcessoAtribuido
    excludes ──> notificarParecerAtribuido (ParecerSolicitacao.processoId nullable; individual-
                 assignment semantics, not case-team semantics)
    open question ──> should Phase-88 daily-job categories (PRAZO_*/EVENTO_*/HONORARIO_ATRASADO)
                       get the same team expansion for consistency? Not required by NOTF-25's
                       literal scope but flagged as a gap the roadmap should decide explicitly

NOTF-26 (snooze)
    requires ──> new nullable Notificacao.snoozedUntil column
    requires ──> visibility predicate added to 3 existing read paths (list, unread-count,
                 mark-all-read exclusion)
    relies on ──> AlertasDiariosJob's EXISTING per-categoria idempotency (Phase 88) — no change
                  needed there; this is what makes "escalation breaks through snooze" free
    conflicts with ──> treating snooze as lida=true (breaks the honest-unread-signal property)
```

## MVP Definition

### Launch With (v2.11)

- [ ] NOTF-24: new `NotificacaoPreferencia`-style table (tenant, user, categoria, muted), checked inside `NotificacaoService.criar(...)`, exposed via a flat settings-page toggle list (no channel matrix — only one channel exists)
- [ ] NOTF-24: at least one category kept non-mutable (recommend `PRAZO_VENCIDO` at minimum, arguably `HONORARIO_ATRASADO` too) — decide explicitly rather than making every category equally mutable by default
- [ ] NOTF-25: extend `FASE_ENTRADA`, `DOCUMENTO_NOVO` (processo branch), `PROCESSO_ATRIBUIDO` to fan out to the processo's client team (`ClienteAdvogado`+`ClienteAdministrativo` via `clienteId`), preserving the existing 2nd-person/3rd-person message-copy split for the responsável vs. the rest of the team
- [ ] NOTF-26: `snoozedUntil` column + snooze/unsnooze endpoint + visibility filtering in the 3 affected read paths + preset snooze durations in the UI, with mark-all-read explicitly excluding snoozed rows

### Add After Validation (v2.11.x)

- [ ] A "Snoozed" filter/tab on `/notificacoes` to review deferred items (not strictly required for MVP correctness, but closes the UX loop reviewed products all provide)
- [ ] Roadmap decision + implementation on whether Phase-88 daily-job categories also get NOTF-25's team expansion

### Future Consideration (post-v2.11)

- [ ] Category×channel preference matrix — defer until a second delivery channel (email/push) is actually built; premature today
- [ ] Per-processo team distinct from the client's team (only worth it if a real workflow surfaces where these genuinely diverge — not evidenced today)

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---|---|---|---|
| NOTF-24 core (mute toggle, opt-out default) | HIGH | LOW-MEDIUM | P1 |
| NOTF-24 non-mutable critical categories | MEDIUM (risk mitigation, not asked-for by users) | LOW | P1 |
| NOTF-25 team fan-out (3 of 4 triggers) | HIGH | LOW-MEDIUM (mostly copy-adapt of existing cliente-branch code) | P1 |
| NOTF-25 extension to Phase-88 job categories | MEDIUM | MEDIUM | P2 |
| NOTF-26 core snooze (bounded resurfacing) | HIGH | MEDIUM (schema + 3 read-path changes) | P1 |
| NOTF-26 "Snoozed" filter/tab | MEDIUM | LOW | P2 |
| Category×channel preference matrix | LOW today (single channel) | HIGH | P3 |
| New `ProcessoEquipa` entity | LOW (no evidenced need beyond reusing client team) | HIGH | P3 (avoid unless proven necessary) |

## Competitor / Ecosystem Feature Analysis

| Feature | Clio (legal, direct comparable) | Generic PM tools (Jira/Asana/GitHub) | Reminder apps (Todoist/Any.do/Due) | LexCV Approach |
|---|---|---|---|---|
| Team vs. single owner | Responsible Attorney (single) + Responsible Staff (multi) | Assignee (single) + Watchers/Subscribers (multi) | N/A (mostly single-user tools) | Responsável (single, existing) + client team via `ClienteAdvogado`/`ClienteAdministrativo` (multi, reused not rebuilt) |
| Notification muting | Not clearly documented per-category in help center | Per-project/per-type mute (Jira notification schemes, Asana Do Not Disturb) | Category-level reminder toggles | Per-category, per-user, opt-out default, with select categories locked "always on" |
| Deferring a reminder | N/A (no direct evidence found) | GitHub "snooze" issue notifications; Jira has no first-class snooze | Preset-interval snooze (15 min/1h/tomorrow), or vendor-configurable snooze length (IntelligentContract, compliance-deadline analog) | Row-level `snoozedUntil` with presets, escalation (worse categoria) always breaks through |

## Sources

- [Clio Help Center — Create Matters](https://help.clio.com/hc/en-us/articles/9285959663131-Create-Matters) — Responsible Attorney/Responsible Staff team model (MEDIUM confidence, vendor docs)
- [Atlassian — Using watchers and @mentions effectively](https://www.atlassian.com/blog/jira-software/using-watchers-and-mentions-effectively) — assignee vs. watcher notification distinction (MEDIUM confidence)
- [Atlassian — Manage your Jira personal settings](https://support.atlassian.com/jira-software-cloud/docs/manage-your-jira-personal-settings/) — per-user notification configuration (MEDIUM confidence)
- [Asana Help Center — Notification settings](https://help.asana.com/s/article/notification-settings) — Do Not Disturb / mute pattern (MEDIUM confidence)
- [SuprSend — Notification Preference Center: UX Patterns, GDPR, and Code](https://www.suprsend.com/post/notification-preference-center) — category structure, opt-in/opt-out defaults, non-mutable critical categories (MEDIUM confidence, cross-verified against Smashing Magazine below)
- [SuprSend — The Ultimate Guide to Perfecting Notification Preferences](https://www.suprsend.com/post/the-ultimate-guide-to-perfecting-notification-preferences-putting-your-users-in-control) — granular preference rationale (MEDIUM confidence)
- [Smashing Magazine — Design Guidelines For Better Notifications UX (2025)](https://www.smashingmagazine.com/2025/07/design-guidelines-better-notifications-ux/) — snooze/mute best practices, non-opt-outable critical alerts (MEDIUM confidence)
- [IntelligentContract — Alert Behaviour](https://support.intelligentcontract.com/support/solutions/articles/22000267553-alert-behaviour) — closest direct domain analog (compliance/contract-deadline alerting): per-user configurable snooze length overriding recurring reminder frequency (MEDIUM confidence, single vendor source)
- [Todoist — Introduction to reminders](https://www.todoist.com/help/articles/introduction-to-reminders-9PezfU) — snooze interval presets (MEDIUM confidence)
- [Any.do Help Center — Notifications & Reminders Overview](https://support.any.do/en/articles/12812921-notifications-reminders-overview) / [Board Due Dates, Reminders, and Recurring Tasks](https://support.any.do/en/articles/8635318-board-due-dates-reminders-and-recurring-tasks) — snooze-from-notification pattern (MEDIUM confidence)
- Direct codebase inspection (HIGH confidence): `backend/src/main/java/com/lexcv/models/Notificacao.java`, `NotificacaoService.java`, `NotificacaoController.java`, `NotificacaoRepository.java`, `jobs/AlertasDiariosJob.java`, `models/Processo.java`, `models/ClienteAdvogado.java`, `controllers/ResourceController.java` (documento-upload notification branch), `models/ParecerSolicitacao.java`, `.planning/PROJECT.md`

---
*Feature research for: LexCV v2.11 — NOTF-24/25/26 notification system extensions*
*Researched: 2026-07-12*
