# Architecture Research

**Domain:** Backend integration plan for milestone v2.10 (Notificações e Alertas) — LexCV legal practice management, Spring Boot 3.4.1/Java 23 + Next.js 16/React 19
**Researched:** 2026-07-08
**Confidence:** HIGH (all codebase claims verified by direct source inspection, not training-data assumption; Spring scheduling mechanics verified against Spring Boot 3.4 official docs via Context7 CLI fallback and cross-checked with multiple current web sources — see Sources)

## Decisions at a Glance

| # | Question | Answer |
|---|----------|--------|
| 1 | Where do event-triggered notifications hook in? | New `NotificacaoService` (single class, `services/` package) called from existing controller methods — **not** raw inline `repository.save()` calls (too much duplicated recipient-resolution logic across 2 controllers), and **not** a full `ApplicationEventPublisher`/`@EventListener` bus (no existing precedent, unjustified indirection for ~4-6 call sites today) |
| 2 | How does the daily `@Scheduled` job get wired up? | New `SchedulingConfig` (`@EnableScheduling`, `config/` package) + new `AlertasDiariosJob` (`jobs/` package, new). Job takes `tenantId` as an **explicit parameter** through every call — it cannot use the `getTenantId()` controller helper because there is no `SecurityContext` on a scheduler thread. Single backend container today (confirmed in `docker-compose*.yml`) means **no distributed lock (ShedLock etc.) is required yet** — flag it as a future trigger condition, not a build-now requirement |
| 3 | How to consolidate the 4-5 duplicated "prazo crítico" computations? | Extract to a new `RiscoPrazoService` (`services/` package, injectable `@Service`, **not a static utility**) exposing the *exact* existing `computeRisco(...)` logic unchanged (zero regression risk) plus one new method for the `Evento`-based call sites. This extraction is a **hard prerequisite** for the daily job — the job's entire purpose is to reuse this single source of truth |
| 4 | How does `Notificacao` carry `tenant_id` + target `user_id`, and what avoids N+1 on polling? | Flat table, **one row per (event, recipient)** — not a recipient-list column — with `tenant_id` kept as its own denormalized column (matches every other entity's isolation pattern) even though it's technically derivable through the recipient's `User.tenantId`. N+1 is avoided by **denormalizing `titulo`/`mensagem`/`linkUrl` onto the row at write time**, so polling reads are a single flat `WHERE tenant_id = ? AND destinatario_id = ?` query with zero joins and zero per-row entity re-resolution |

---

## Standard Architecture

### System Overview (as it exists today, with v2.10 additions marked `[NEW]`)

```
┌────────────────────────────────────────────────────────────────────────────┐
│ FRONTEND (web/src)                                                         │
│  components/shared/notification-bell.tsx                                   │
│    today: useUpcomingEventos() → GET /eventos/upcoming (no polling)         │
│    [NEW]: useNotificacoesUnreadCount() + useNotificacoes()                  │
│           refetchInterval: 30-60s (first refetchInterval usage in the app)  │
│  app/(dashboard)/notificacoes/page.tsx  [NEW — dedicated history+filters]   │
│  hooks/use-notificacoes.ts  [NEW — mirrors use-eventos.ts shape exactly]    │
│  lib/permissions.ts — KNOWN_SCOPES gains "notificacoes"  [MODIFIED]         │
├────────────────────────────────────────────────────────────────────────────┤
│ next.config.ts rewrite: /api/v1/:path* → BACKEND_API_ORIGIN (unchanged)     │
├────────────────────────────────────────────────────────────────────────────┤
│ HTTP CONTROLLERS (backend/.../controllers)                                 │
│  ┌────────────────────────┐ ┌───────────────────┐ ┌──────────────────────┐│
│  │ ResourceController.java│ │ ParecerController │ │ NotificacaoController││
│  │ (2898 lines, MODIFIED) │ │ (518 ln, MODIFIED)│ │ [NEW]                ││
│  │ createProcessoFase →   │ │ createSolicitacao →│ │ GET /notificacoes    ││
│  │   notificarFaseEntrada │ │ atribuirAdvogado → │ │ GET .../unread-count ││
│  │ uploadDocumento →      │ │   notificarParecer-│ │ PATCH .../{id}/lida  ││
│  │   notificarDocumentoNv │ │   Atribuido         │ │                      ││
│  │ NEW responsavel-reassign endpoint →                                     ││
│  │   notificarProcessoAtribuido                                            ││
│  │ 5 computeRisco() call sites → riscoPrazoService.computeRisco(...)       ││
│  │ 3 Evento-based inline blocks → riscoPrazoService.computeRiscoEvento(...)││
│  └───────────┬────────────┘ └─────────┬─────────┘ └──────────┬───────────┘│
├──────────────┴───────────────────────┴────────────────────────┴───────────┤
│ SERVICE LAYER (backend/.../services) — today: StorageService, SetupService │
│  ┌───────────────────────┐  ┌─────────────────────────┐                    │
│  │ NotificacaoService     │  │ RiscoPrazoService        │  [BOTH NEW]      │
│  │ [NEW]                  │  │ [NEW]                    │                  │
│  │ - resolves recipients  │  │ - computeRisco(Prazo)     │                  │
│  │   per event type       │  │   (moved verbatim from    │                  │
│  │ - criar(...) → the ONE │  │   ResourceController)     │                  │
│  │   repository.save()    │  │ - computeRiscoEvento(...) │                  │
│  │   choke point           │  │   (new, same thresholds)  │                  │
│  └───────────┬─────────────┘  └────────────┬─────────────┘                  │
├──────────────┴──────────────────────────────┴───────────────────────────────┤
│ SCHEDULED JOB (no HTTP entry point — runs on Spring's TaskScheduler thread)  │
│  config/SchedulingConfig.java  [NEW]  — @EnableScheduling, isolated         │
│  jobs/AlertasDiariosJob.java  [NEW]                                          │
│    @Scheduled(cron = "...")  — daily                                        │
│    for (Tenant t : tenantRepository.findAll())  ← FIRST cross-tenant loop   │
│       try { scan prazos+eventos via riscoPrazoService;                      │
│             scan honorarios (dataAcordo + totalPago vs valorTotal);          │
│             notificacaoService.criar(...) }                                 │
│       catch (Exception e) { log.error(...); continue; }  ← per-tenant        │
│                                                             isolation        │
├───────────────────────────────────────────────────────────────────────────┤
│ REPOSITORIES → PostgreSQL                                                   │
│  NotificacaoRepository  [NEW]  →  t_notificacao  [NEW TABLE]                │
│    findTop50ByTenantIdAndDestinatarioIdOrderByCreatedAtDesc(...)             │
│    countByTenantIdAndDestinatarioIdAndLidaFalse(...)                        │
│  existing: ProcessoRepository, EventoRepository, PrazoRepository,            │
│    HonorarioRepository, UserRepository, ClienteAdvogadoRepository,           │
│    ClienteAdministrativoRepository, TenantRepository (unmodified)            │
├───────────────────────────────────────────────────────────────────────────┤
│ backend/migrations/NN-create-notificacao-table.sql  [NEW — manual, required │
│   for prod because application-prod.yml sets ddl-auto=validate]             │
└───────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | New or Modified |
|-----------|-----------------|------------------|
| `NotificacaoService` | Resolves "who gets notified for event X" per notification type; single low-level `criar(...)` method that is the only code path allowed to `notificacaoRepository.save(...)` | **New** |
| `RiscoPrazoService` | Single source of truth for "is this deadline critical" — one pure method per entity shape (`Prazo`, `Evento`) | **New** (extracted from `ResourceController.computeRisco`) |
| `AlertasDiariosJob` | Daily cross-tenant scan: prazos, eventos, honorários pendentes → calls into the two services above | **New** |
| `SchedulingConfig` | Hosts `@EnableScheduling` in isolation, matching the one-responsibility-per-`@Configuration` convention already used by `SecurityConfig`/`MinioConfig` | **New** |
| `NotificacaoController` | REST surface for listing/unread-count/mark-read, scoped to caller's own `tenantId` + `userId` exactly like every other controller | **New** |
| `Notificacao` (entity) + `NotificacaoRepository` | Persisted notification row, one per (event, recipient) | **New** |
| `ResourceController` | Gains 1 new endpoint (responsável reassignment) + ~8 one-line call-outs to the new services at existing save points | **Modified** (net-additive diffs, no structural rewrite) |
| `ParecerController` | Gains 2 one-line call-outs to `NotificacaoService` | **Modified** |
| `DatabaseSeeder`, `AdminController.getRbac()`, `UserPrincipal` (hardcoded ADMIN list), `web/src/lib/permissions.ts` | RBAC plumbing for the new `notificacoes:view` scope | **Modified** (mechanical, small) |
| `NotificationBell`, new `use-notificacoes.ts`, new `/notificacoes` page | Frontend consumption, first use of TanStack Query `refetchInterval` in this codebase | **New/Modified** |

## Recommended Project Structure

```
backend/src/main/java/com/lexcv/
├── controllers/
│   ├── ResourceController.java        # MODIFIED: +1 endpoint, +~8 call sites, computeRisco() deleted
│   ├── ParecerController.java         # MODIFIED: +2 call sites
│   └── NotificacaoController.java     # NEW: GET list, GET unread-count, PATCH lida
├── services/
│   ├── StorageService.java            # unchanged — existing precedent for "service" layer
│   ├── SetupService.java              # unchanged
│   ├── NotificacaoService.java        # NEW — recipient resolution + creation choke point
│   └── RiscoPrazoService.java         # NEW — extracted "risco crítico" logic
├── jobs/                               # NEW PACKAGE — first of its kind in this codebase
│   └── AlertasDiariosJob.java         # NEW — the @Scheduled entry point
├── config/
│   ├── SecurityConfig.java            # unchanged
│   ├── MinioConfig.java               # unchanged
│   └── SchedulingConfig.java          # NEW — @EnableScheduling, nothing else
├── models/
│   └── Notificacao.java               # NEW entity
├── repositories/
│   └── NotificacaoRepository.java     # NEW
├── dtos/
│   └── NotificacaoResponse.java       # NEW (optional — see Pattern 4 notes)
└── seed/
    └── DatabaseSeeder.java             # MODIFIED — +"notificacoes:view" permission, granted to all 4 roles

backend/migrations/
└── NN-create-notificacao-table.sql    # NEW — required manual prod script (ddl-auto=validate in prod)

web/src/
├── hooks/
│   └── use-notificacoes.ts            # NEW — mirrors use-eventos.ts shape
├── components/shared/
│   └── notification-bell.tsx          # MODIFIED — swap useUpcomingEventos() for the new hooks
├── app/(dashboard)/notificacoes/
│   └── page.tsx                       # NEW — dedicated history + filters page
└── lib/permissions.ts                 # MODIFIED — KNOWN_SCOPES += "notificacoes"
```

### Structure Rationale

- **`jobs/` is a new top-level package**, not folded into `services/` or `controllers/`. `AlertasDiariosJob` has a different caller (Spring's scheduler thread, not an HTTP request), a different testing shape (needs deterministic-date injection, not `MockMvc`), and a different transactional shape (per-tenant isolation, not one request/one transaction) than everything else in the app. Mirrors how `seed/DatabaseSeeder` — the only other "runs broadly across the system, not per-request" class today — already gets its own package rather than living inside `services/` or a controller.
- **`NotificacaoController` is a new dedicated controller**, not more lines in `ResourceController`. This directly follows a precedent the team has *already* established in this same codebase: `ParecerPesquisaController` was extracted out of `ParecerController` specifically to give a cohesive new capability its own routing surface (see PROJECT.md Key Decisions, v2.5/v2.6). `ResourceController` is already ~2900 lines; notifications are a new, self-contained resource with no shared CRUD-workflow lineage to `Processo`/`Cliente`, making this the cleanest possible case for a new controller rather than growing the existing one further.
- **`services/` gains its first *business-logic* services.** `StorageService`/`SetupService` are infrastructure-facing (S3/MinIO client wrapping, first-run bootstrap). `NotificacaoService`/`RiscoPrazoService` are the first services that encode domain decisions ("who should be notified," "what counts as critical"). This is a deliberate, minimal expansion of the existing pattern rather than a new one.

## Architectural Patterns

### Pattern 1: Thin recipient-resolving service, not inline saves and not an event bus

**What:** A single `NotificacaoService` with one method per notification type (`notificarFaseEntrada`, `notificarDocumentoNovo`, `notificarProcessoAtribuido`, `notificarParecerAtribuido`), each resolving recipients and then calling one shared private `criar(...)` method that performs the actual `notificacaoRepository.save(...)`. Controllers call these methods at the exact point they currently call `auditLogRepository.save(AuditLog.builder()...)`.

**When to use:** Whenever a controller action should also produce a notification. This is the *only* place `notificacaoRepository.save(...)` should ever be called from.

**Why not inline (matching the `AuditLog` precedent literally):** `AuditLog` writes are trivial — same 5 fields, no recipient logic, no branching. Notifications are not: "who gets notified" is a genuinely different computation per event type —
- fase entrada → `Processo.responsavelId` + tenant ADMIN(s)
- documento novo → `Processo.responsavelId` **if** attached to a `processoId`, else every `ClienteAdvogado`/`ClienteAdministrativo` row for that `clienteId` (via `findByClienteIdAndTenantId`, already present on both repositories) + ADMIN(s)
- processo atribuído → the *new* `responsavelId` from the reassignment endpoint + ADMIN(s)
- parecer atribuído → `ParecerSolicitacao.advogadoId` + ADMIN(s)

Inlining this branching logic at every controller call site would duplicate exactly the kind of "same concept computed differently in N places" problem this same milestone is already paying down for "prazo crítico" (see Pattern 3). One service = one place to change the "+ADMIN" rule, or add a 5th notification type, later.

**Why not a full event bus (`ApplicationEventPublisher` + `@EventListener`/`@TransactionalEventListener`):** This is the textbook-correct decoupling pattern, and it is explicitly the right call *if a third consumer of "this domain event happened" shows up* (e.g. email digests, a webhook, analytics). Today it would be pure indirection for ~4-6 call sites, with real added subtlety this codebase has never had to deal with (event ordering, `@TransactionalEventListener(phase = AFTER_COMMIT)` to avoid notifying about a fase/documento whose transaction later rolls back). `ResourceController` is already a known complexity hot spot; adding a new cross-cutting abstraction on top of it is disproportionate to a feature that, at its core, is "insert a few extra rows next to writes that already happen." Revisit if/when a second real consumer of the same triggers appears.

**Trade-off accepted:** A notification-insert failure shares the same transactional fate as the parent operation, exactly like `AuditLog` does today (no extra resilience machinery, e.g. no "notification failed but the fase was still created" isolation). This matches the existing risk profile in this codebase rather than inventing a new one — introducing `@Transactional(propagation = REQUIRES_NEW)` or async dispatch here would itself be new complexity unjustified by any observed problem.

**Example:**
```java
// ResourceController.createProcessoFase — after processoFaseRepository.save(pf):
ProcessoFase saved = processoFaseRepository.save(pf);
notificacaoService.notificarFaseEntrada(processo, saved, catalog);
return ResponseEntity.status(HttpStatus.CREATED).body(saved);
```

### Pattern 2: Explicit tenant parameter through a dedicated scheduled job — no ThreadLocal tenant context

**What:** `AlertasDiariosJob` calls `tenantRepository.findAll()`, then loops, passing `tenant.getId()` as an explicit method argument into every repository/service call it makes for that iteration. No attempt is made to populate `SecurityContextHolder` with a synthetic `UserPrincipal`, and no `ThreadLocal`-based "current tenant" context is introduced.

**When to use:** Any future background job that must operate across tenants without an HTTP request.

**Why this is correct for *this* codebase specifically:** `getTenantId()` (defined identically in `ResourceController` and `ParecerController`) reads `SecurityContextHolder.getContext().getAuthentication()`. A `@Scheduled` method runs on a scheduler thread that never went through `JwtAuthenticationFilter` — there is no `Authentication` to read. Fabricating a fake one just to satisfy that helper method would be a workaround for a problem that doesn't need solving: the controller helper exists purely as a *convenience* over the security context for request-scoped code. A background job doesn't have that convenience available and doesn't need it — passing `tenantId` explicitly is not a compromise, it's simpler and more explicit than what controllers do, and it is the *first* cross-tenant iteration in this codebase (confirmed: even `AdminController`, which is `@PreAuthorize("hasRole('ADMIN')")`-gated, still scopes every query to `principal.getTenantId()` — the caller's own tenant, never all tenants).

**Why no distributed lock (ShedLock, Quartz clustering, `SELECT ... FOR UPDATE` lock row) is needed *yet*:** `docker-compose.yml`/`docker-compose.prod.yml` define exactly one `backend` service/container, no replica count, no load balancer fanning out across multiple app instances. A single-instance deployment cannot double-run the same `@Scheduled` invocation concurrently. **This is a documented future trigger condition, not a current requirement:** if LexCV ever scales the backend horizontally, `AlertasDiariosJob` must gain a lock (ShedLock is the standard minimal-dependency choice for this exact Spring Boot use case) *before* that point, or every tenant will get duplicate notifications once per running instance.

**Why per-tenant (and outer) try/catch is not optional:** Verified against current sources (see Sources) — an uncaught exception thrown out of a `@Scheduled` method does not just fail *that* run; with Spring's default single-thread scheduler it can silently cancel *all future invocations* of that scheduled task, with no crash, no alert, nothing in the logs pointing at "notifications stopped." Given this project has no monitoring/alerting infrastructure today, this is exactly the kind of failure that would go unnoticed for weeks. The job must therefore:
1. Wrap the **entire** method body in a top-level try/catch (defense in depth).
2. Wrap **each tenant's** processing in its own try/catch inside the loop, logging and continuing — so tenant B's bad/unexpected data (e.g., a `Prazo` with a malformed value) cannot block notifications for tenants C, D, E that would otherwise process cleanly the same day.

**Why no class-level `@Transactional`:** Wrapping the whole multi-tenant loop in one transaction risks an all-or-nothing rollback across unrelated tenants (tenant #3 throwing would undo notifications already correctly created for tenants #1-2) and holds a long-lived transaction open for the job's full duration. Each `notificacaoRepository.save(...)` is already atomic on its own; no wrapping transaction is needed at the job level.

**Example:**
```java
@Scheduled(cron = "${app.jobs.alertas-diarios-cron:0 0 6 * * *}")
public void executar() {
    LocalDate hoje = LocalDate.now();
    for (Tenant tenant : tenantRepository.findAll()) {
        try {
            processarTenant(tenant.getId(), hoje);
        } catch (Exception e) {
            log.error("Falha ao processar alertas diários para tenant {}", tenant.getId(), e);
            // deliberately continue — one tenant's failure must not block the rest
        }
    }
}
```

### Pattern 3: Extract-in-place into an injectable service, not a static utility — and do it *before* the job

**What:** Move `ResourceController.computeRisco(LocalDate dataLimite, String prioridade)` verbatim into a new `RiscoPrazoService`, add a 2-arg convenience overload that defaults `hoje = LocalDate.now()` (preserving today's exact behavior for all 5 existing internal call sites), then add a second method, `computeRiscoEvento(...)`, reusing the *same* 7-day/ALTA vs 3-day/other threshold table for the `Evento`-based call sites.

**When to use:** Any time the same non-trivial business rule is computed independently in more than one place. Not before — see the honorários note in Anti-Patterns below for the "don't pre-extract" counter-case.

**Why a `@Service`, not a static utility method:** There are no static utility classes carrying business logic anywhere in `com.lexcv` today — the established convention for shared, stateless, cross-controller logic is an injectable `@Service` (`StorageService`, `SetupService`), wired via the same `@RequiredArgsConstructor` constructor-injection style already used everywhere. A static method also cannot be mocked/stubbed without PowerMock-style static mocking, which is not part of this project's test stack (`spring-boot-starter-test` + `spring-security-test` only) — an injectable bean is trivially fake-able in the future `AlertasDiariosJob` tests that need to control "what counts as critical as of date X" deterministically.

**Why the extraction is a hard prerequisite for `AlertasDiariosJob`, not parallelizable work:** The daily job's *entire stated purpose* (per PROJECT.md: "lógica de crítico consolidada, job diário") is to notify based on the *same* definition of "critical" the UI already shows. Writing the job first would force either (a) a sixth ad-hoc copy of the threshold logic just to unblock it — defeating the milestone's own goal — or (b) blocking on the extraction anyway. There is no ordering in which the job can be meaningfully written first.

**The actual duplication being consolidated (verified by direct inspection, more than the "4 ways" shorthand suggests):**

| # | Location | Field/window | Notes |
|---|----------|--------------|-------|
| 1 | `ResourceController.computeRisco()` (private method, `ResourceController.java:1397`) | `Prazo.dataLimite`; vencido if past, "próximo" if ≤7 days (ALTA) / ≤3 days (other) | Used at 5 call sites: `listProcessos` enrichment (`risco_mais_critico`), `listPrazos`, `listAllPrazos`, `createPrazo`, `togglePrazoConcluido` |
| 2 | `/dashboard` KPI, `agendaUrgentesCount()` | `Evento` where `concluido=false AND prioridade=ALTA` | **No date window at all** — counts any pending ALTA event regardless of how far away, a third distinct notion of "critical" |
| 3 | `/processos/dashboard`, inline `prazosCriticosCount` block | `Evento.dataFim` within `[hoje, hoje+7]` | Fixed 7-day window, ignores priority entirely |
| 4 | `/eventos/upcoming` (feeds today's `NotificationBell`) | `Evento.dataInicio` within `[now, now+days]` (`days` param, default 7, capped 30) | Configurable window, different field again |
| 5 | Frontend Agenda page, `getCategoria()` (`web/src/app/(dashboard)/agenda/page.tsx`) | String-sniffs `titulo`/`tipo` client-side | Not actually a risk-level computation (no vencido/próximo/ok) — a *visual category* (PRAZO/AUDIENCIA/DILIGENCIA/REUNIAO), independent concern from 1-4 |

**Migration is mechanical, not a rewrite:** each of the 5 backend call sites becomes a one-line delegation to the injected service; `computeRisco` itself is deleted from `ResourceController` once repointed (keeping a dead pass-through wrapper would just be a 6th copy waiting to drift). Item 5 (frontend category labeling) is **not** required to change for this consolidation to be complete — it never computed a risk level, so leave it alone unless a later phase wants the frontend to stop guessing risk from title strings.

### Pattern 4: Fan-out at write time, denormalize display text at write time — avoid N+1 on the read/poll path

**What:** `Notificacao` is a flat table with one row per `(event, recipient)` pair. Human-readable `titulo`/`mensagem`/`linkUrl` are computed and stored **when the notification is created** (`NotificacaoService.criar(...)`, at which point the triggering `Processo`/`Documento`/`ParecerSolicitacao` object is already in hand — no extra query needed), not re-derived at read time from a bare entity/id reference.

**When to use:** Any per-user, persisted, polling-read notification feed.

**Why fan-out (N rows), not a recipient-list column:** "Read" state (`lida`) is inherently per-user. A single row shared by 3 recipients would need a separate join table just to track who has read it — which is reinventing the exact 1:N shape a flat per-recipient row already gives for free, and would make "give me MY unread count" a join instead of a single indexed `WHERE`. Every other entity in this schema (`Documento`, `Evento`, `Prazo`, `AuditLog`) is a flat row with no "who can see this" join table; the only join tables that exist (`ClienteAdvogado`, `ClienteAdministrativo`) model a genuine many-to-many *team* relationship, which is a different shape than "this specific notification belongs to this specific user." Write amplification (typically 2-4 rows per event, given the "+ADMIN" rule) is trivial at this project's realistic scale (a handful of tenants, a handful of users per tenant).

**Why `tenant_id` stays a first-class column even though `destinatario_id` already implies a tenant via `User.tenantId`:** This mirrors the isolation pattern used by *every* other entity in the schema without exception (CLAUDE.md: tenant_id is "the primary data-isolation boundary"), keeps the hot read path a single-table lookup instead of a join through `t_user` for zero benefit, and follows the same "don't trust one FK alone" defense-in-depth already used elsewhere (e.g. `uploadDocumento` independently re-validates that `clienteId`/`processoId` belong to the caller's tenant even though those FKs "should" already be consistent).

**Why denormalizing display text avoids the actual N+1 risk (which is not the classic child-collection N+1):** If the read endpoint instead stored only `entidadeId` and re-resolved a display label per row (e.g. `processoRepository.findById(n.getEntidadeId())` inside a `.map()` over the notification list), it would reproduce the exact bug class this codebase has already had to fix before — see the `responsaveisMap`/`prazosPorProcesso` batch-preload rewrite in `listProcessos` ("Batch-load responsáveis to avoid N+1 queries"). Baking the text in at creation time means the poll-path query is a single flat `SELECT`, full stop — no batch-preload maps needed on the read side at all, because there is nothing left to resolve.

**`entidade_tipo`/`entidade_id` as a `String` pair directly reuses a specific existing, well-justified precedent — not just a similar-looking shape:** `AuditLog.entidadeId` is already `String` "to accommodate both UUID and Integer IDs across entities" (its own code comment). This is not optional here either: `Notificacao` must be able to reference a `Processo`/`Documento`/`ParecerSolicitacao`/`Prazo` (all `UUID` ids) **or** an `Evento`/`Honorario` (both `Integer` ids) — so a single typed FK column is not possible, and `AuditLog`'s exact trick is the correct reused answer, not a new invention.

**Two different read shapes need two different repository methods, not one over-generalized paginated method:**
```java
public interface NotificacaoRepository extends JpaRepository<Notificacao, UUID> {
    // Bell dropdown / unread badge — bounded, no pagination needed
    List<Notificacao> findTop50ByTenantIdAndDestinatarioIdOrderByCreatedAtDesc(UUID tenantId, UUID destinatarioId);
    long countByTenantIdAndDestinatarioIdAndLidaFalse(UUID tenantId, UUID destinatarioId);

    // Dedicated /notificacoes history+filters page — genuinely unbounded over time
    Page<Notificacao> findByTenantIdAndDestinatarioId(UUID tenantId, UUID destinatarioId, Pageable pageable);
}
```
Nowhere else in this backend uses `Pageable`/`Page<T>` (every existing endpoint returns a full `List<T>` — `Processo`, `Cliente`, `Documento`, etc. are all naturally bounded per tenant). Introducing `Pageable` for the one dedicated history page is a deliberate, narrow first use of a new Spring Data idiom — flagged explicitly so the roadmapper budgets for it — while the polling-optimized bell endpoint deliberately stays on the simpler `findTop50.../count...` shape rather than forcing both read patterns through one generalized paginated method.

**Batch-fetch discipline applies to the *write* side too:** when `AlertasDiariosJob` finds N critical prazos in one tenant, it must fetch that tenant's ADMIN user list **once** per tenant iteration (not once per prazo) and reuse it — the exact same batch-preload discipline `listProcessos` already applies on the read side, just applied here on the job's write side.

## Data Flow

### Request Flow — event-triggered notification (e.g. fase entrada)

```
PUT/POST /processos/{id}/fases  (existing endpoint, ResourceController)
    ↓
processoFaseRepository.save(pf)   [unchanged]
    ↓
notificacaoService.notificarFaseEntrada(processo, pf, catalog)   [NEW call]
    ↓ resolves recipients: processo.responsavelId (if set) + tenant ADMIN(s)
    ↓ for each recipient →
notificacaoService.criar(tenantId, destinatarioId, "FASE_ENTRADA", titulo, ..., linkUrl)
    ↓
notificacaoRepository.save(new Notificacao row)   × N recipients
    ↓ (separately, on its own timer)
Frontend: useNotificacoesUnreadCount()  —  refetchInterval 30-60s
    ↓
GET /api/v1/notificacoes/unread-count  (NotificacaoController, scoped to caller's own tenantId+userId)
    ↓
countByTenantIdAndDestinatarioIdAndLidaFalse(tenantId, userId)   — single indexed COUNT
```

### Request Flow — daily scheduled scan

```
Spring TaskScheduler thread (no HTTP request, no SecurityContext)
    ↓
AlertasDiariosJob.executar()  @Scheduled(cron = daily)
    ↓
tenantRepository.findAll()  — FIRST cross-tenant iteration in this codebase
    ↓ for each Tenant (try/catch isolates failures per tenant):
      prazoRepository.findByTenantId(tenantId) → riscoPrazoService.computeRisco(...) per Prazo
      eventoRepository.findByTenantId(tenantId) → riscoPrazoService.computeRiscoEvento(...) per Evento
      honorarioRepository.findAll-for-tenant-processos(...) → dataAcordo/totalPago/valorTotal check
        (skip rows where valorTotal is null — cannot determine "sem pagamento total" without a total;
         Honorario auto-created at formalização always starts with valorTotal = null per the v2.9 decision)
    ↓ for each newly-critical item →
      notificacaoService.criar(...)  (existence-check guard: skip if an equivalent
      notification for this entidadeId was already created today, so the job stays
      idempotent per day instead of re-alerting on every run for a still-critical item)
```

### Key Data Flows

1. **Event-triggered (synchronous, in-request):** controller write → `NotificacaoService` resolves recipients → N rows inserted in the same transaction as the triggering write. No queueing, no async dispatch — matches the `AuditLog` precedent's risk profile exactly.
2. **Scheduled (asynchronous, out-of-request):** `AlertasDiariosJob` → per-tenant loop with explicit `tenantId` parameter threading (no `SecurityContext`, no `getTenantId()`) → same `NotificacaoService.criar(...)` choke point as the synchronous path, so both flows produce identically-shaped rows.
3. **Polling read (frontend, new pattern):** TanStack Query `refetchInterval` (first use in this codebase) → single flat indexed query, no enrichment step needed because display text was denormalized at write time.

## Scaling Considerations

Realistic scale for this product: a small number of tenants (individual law firms/institutions), each with roughly 5-50 users, single VPS, single backend container. The meaningful scaling axis for this specific feature is not concurrent users — it's **notification row count growing unboundedly over time** (every day, forever, regardless of user count), which is a fundamentally different growth curve than every other entity in this schema (`Processo`/`Cliente`/`Documento` growth is bounded by real-world case volume; `Notificacao` growth is bounded by nothing unless rows are pruned or archived).

| Scale | Architecture Adjustments |
|-------|--------------------------|
| Current (single VPS, single backend container, few tenants) | Plain `@Scheduled` with Spring Boot's default single-thread `ThreadPoolTaskScheduler` is sufficient. No distributed lock needed (see Pattern 2). `findTop50...`-style bounded queries are sufficient for the bell; add the composite index `(tenant_id, destinatario_id, lida, created_at)` from day one regardless, since it costs nothing now and is exactly what both read paths need |
| If notification volume grows unbounded per tenant over months/years | The dedicated `/notificacoes` history page is the one place this actually bites — that's exactly why it gets real `Pageable` pagination now rather than "add it later." Consider a retention/archival policy (e.g. auto-delete or archive `lida=true` rows older than N months) as a follow-up, not blocking for this milestone |
| If the backend is ever horizontally scaled (multiple containers/replicas) | `AlertasDiariosJob` **must** gain a distributed lock (ShedLock is the standard low-dependency choice for Spring Boot) before that point, or every tenant receives duplicate daily notifications once per running instance. This is not a concern today — flagged only so it isn't silently forgotten later |

### Scaling Priorities

1. **First real bottleneck:** unbounded growth of `t_notificacao` rows over calendar time on the dedicated history page — addressed now via `Pageable` on that one endpoint (see Pattern 4).
2. **Second, much later bottleneck:** horizontal backend scaling breaking the single-instance assumption the scheduled job currently relies on for safety — addressed by adding a distributed lock only if/when that scaling actually happens.

## Anti-Patterns

### Anti-Pattern 1: Building a full event-publisher/listener system for 4-6 call sites

**What people do:** Reach for `ApplicationEventPublisher` + `@TransactionalEventListener` the moment "notify on domain event" comes up, because it's the textbook-correct decoupling pattern.
**Why it's wrong here:** No precedent anywhere in this codebase, real added subtlety (event ordering, after-commit timing) for zero current benefit, and disproportionate weight added on top of an already-2898-line controller file for a feature that is fundamentally "insert a few extra rows next to writes that already happen."
**Instead:** A thin `NotificacaoService` (Pattern 1). Revisit only when a second/third real consumer of the same triggers appears (email digest, webhook, etc.).

### Anti-Pattern 2: Retrofitting a ThreadLocal "current tenant" context for the scheduled job

**What people do:** Since `getTenantId()` relies on `SecurityContextHolder`, it's tempting to build a `TenantContext.setCurrentTenant(id)` ThreadLocal so "existing" tenant-scoped code can be reused unchanged inside the job.
**Why it's wrong here:** This codebase has no such context today, and introducing one just for a single background job adds a whole new cross-cutting mechanism (and a new footgun — ThreadLocal leakage across pooled scheduler threads) to save writing one explicit parameter.
**Instead:** Pass `tenantId` as an explicit method argument everywhere in the job (Pattern 2) — simpler, more explicit, and zero new infrastructure.

### Anti-Pattern 3: Letting one bad tenant (or one bad day) silently stop the job forever

**What people do:** Write the `@Scheduled` method as a plain loop with no error handling, on the assumption that "it'll just log and retry tomorrow" if something throws.
**Why it's wrong here:** Verified against current sources — an uncaught exception in a `@Scheduled` method can silently cancel *all future* runs of that task, not just the current one, with nothing loud in the logs. Combined with this project having no monitoring/alerting, this is a "notifications quietly stop forever" bug that could go unnoticed for weeks.
**Instead:** Outer try/catch around the whole method **and** an inner try/catch per tenant inside the loop (Pattern 2).

### Anti-Pattern 4: Storing only an entity reference and re-resolving display text on every poll

**What people do:** Store `entidadeTipo`/`entidadeId` only, and have the list endpoint `.map()` over notifications re-fetching each linked `Processo`/`Documento` to build a human-readable label — "keep it normalized."
**Why it's wrong here:** This is the exact N+1-by-re-fetch bug class this codebase has already paid down once (`listProcessos`'s `responsaveisMap` batch-preload rewrite exists precisely because per-row re-fetching was a real, fixed problem). A notification feed polled every 30-60s is the worst possible place to reintroduce it.
**Instead:** Denormalize `titulo`/`mensagem`/`linkUrl` onto the row at creation time (Pattern 4) — a small, deliberate staleness trade-off in exchange for a read path with zero joins.

### Anti-Pattern 5: Pre-extracting the honorários "dias sem pagamento" check into its own service before it has a second caller

**What people do:** Since the milestone is already extracting `RiscoPrazoService` for prazos/eventos, reflexively give the honorários check ("dias sem pagamento total desde `dataAcordo`") its own service class too, for symmetry.
**Why it's wrong here:** Unlike prazo/evento criticality, this check has exactly **one** consumer today (the new daily job) — there is no existing duplication to consolidate. Premature extraction for a rule used in exactly one place adds a class for no present benefit, running against the very lesson this milestone is teaching (extract *because* something is duplicated, not in anticipation of it).
**Instead:** Keep it as a private method inside `AlertasDiariosJob` for now. Extract it the moment a second consumer appears (e.g. a future Financeiro-dashboard "honorários em atraso" KPI) — mirroring exactly how `computeRisco()` should have been shared from the start.

**A closely related, easy-to-miss gotcha for this specific check:** `Honorario.valorTotal` is `null` by design immediately after auto-creation at formalização (an explicit, deliberate v2.9 decision — see PROJECT.md Key Decisions — specifically to avoid ever pre-filling a real financial value without user confirmation). Any "days since `dataAcordo` without full payment" check **must** explicitly skip rows where `valorTotal` is null rather than let a null-comparison silently misfire (always-false) or throw — there is currently no other code in the app that reads `valorTotal` in a way that would have already surfaced this edge case for you.

## Integration Points

### New Files

| File | Purpose |
|------|---------|
| `backend/src/main/java/com/lexcv/models/Notificacao.java` | New entity — see Pattern 4 for field design |
| `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` | New repository — bounded queries for polling + `Pageable` query for history page |
| `backend/src/main/java/com/lexcv/services/NotificacaoService.java` | New — recipient resolution + single `criar(...)` write choke point |
| `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java` | New — extracted from `ResourceController.computeRisco`, plus `computeRiscoEvento(...)` |
| `backend/src/main/java/com/lexcv/config/SchedulingConfig.java` | New — `@EnableScheduling` only |
| `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` | New — the `@Scheduled` daily entry point (first file in a new `jobs/` package) |
| `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` | New — list / unread-count / mark-read endpoints |
| `backend/src/main/java/com/lexcv/dtos/NotificacaoResponse.java` | New (optional) — response shaping, or return the entity directly (both patterns exist in this codebase already) |
| `backend/migrations/NN-create-notificacao-table.sql` | New — **required** manual prod script (`CREATE TABLE t_notificacao` + composite index), following the exact header-comment convention of `81-`/`82-` scripts, since `application-prod.yml` sets `ddl-auto: validate` |
| `web/src/hooks/use-notificacoes.ts` | New — mirrors `use-eventos.ts` shape; introduces this codebase's first `refetchInterval` usage |
| `web/src/app/(dashboard)/notificacoes/page.tsx` | New — dedicated history + filters page (explicitly in PROJECT.md scope) |

### Modified Files

| File | Change |
|------|--------|
| `backend/.../controllers/ResourceController.java` | + `NotificacaoService`/`RiscoPrazoService` constructor fields; `createProcessoFase` and `uploadDocumento` gain 1-line calls to `notificacaoService.notificar*`; new responsável-reassignment endpoint (already required by the milestone independent of notifications) calls `notificarProcessoAtribuido`; 5 `computeRisco(...)` call sites repointed to `riscoPrazoService.computeRisco(...)`, private method deleted; 3 `Evento`-based inline blocks (`agendaUrgentesCount`, `getProcessosDashboard`'s `prazosCriticosCount`, `/eventos/upcoming`) repointed to `riscoPrazoService.computeRiscoEvento(...)` |
| `backend/.../controllers/ParecerController.java` | + `NotificacaoService` constructor field; `createSolicitacao` (when `advogadoId` present at creation) and `atribuirAdvogado` gain 1-line calls to `notificarParecerAtribuido` |
| `backend/.../seed/DatabaseSeeder.java` | + `"notificacoes:view"` to `permKeys`, granted to ADMIN/ADVOGADO/TECNICO/ASSISTENTE — a single view-only scope is sufficient since every user only ever reads/marks-read their *own* notifications (no create/edit/manage distinction needed, unlike privilege-gated resources such as `financeiro`) |
| `backend/.../controllers/AdminController.java` (`getRbac()`) | + `notificacoes:view` to the `systemPermissions` list so the admin RBAC-management screen can display/toggle it — an easy-to-forget cross-cutting touch point, exactly the class of gap prior milestone audits in this project have caught after the fact |
| `backend/.../config/UserPrincipal.java` | + `notificacoes:view` to the hardcoded ADMIN bonus-permission list, for consistency with how `pareceres:*` was added previously (likely redundant once seeded for all roles, but matches existing precedent) |
| `web/src/lib/permissions.ts` | `KNOWN_SCOPES` += `"notificacoes"` |
| `web/src/components/shared/notification-bell.tsx` | Swap `useUpcomingEventos()` for the new unread-count/list hooks; same `Popover` shell reused |
| `web/src/components/shared/dashboard-shell.tsx` | Optionally add a "Notificações" nav entry pointing at `/notificacoes` |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Controllers ↔ `NotificacaoService` | Direct method call, same transaction | Matches `AuditLog`'s existing risk profile; no async/event indirection (Pattern 1) |
| `AlertasDiariosJob` ↔ `RiscoPrazoService` / `NotificacaoService` | Direct method call, explicit `tenantId` parameter, no shared request-scoped state | Job has no `SecurityContext`; this is the first code in the app to call tenant-scoped services from outside an HTTP request (Pattern 2) |
| `NotificacaoController` ↔ Frontend | REST + polling (`refetchInterval`) | First polling usage in this codebase; existing `apiFetch` wrapper and cookie-based auth need no changes — the endpoint is scoped by `getTenantId()`/`principal.getUserId()` exactly like every other authenticated endpoint |
| Dev vs. prod schema | Hibernate `ddl-auto=update` (dev) vs. `ddl-auto=validate` (prod) | The new `t_notificacao` table exists automatically in dev/CI but **requires** the manual migration script before prod deploy — the same discipline already documented for the `81-`/`82-` scripts |

## Suggested Build Order

Ordering respects the one hard dependency called out in the question (`RiscoPrazoService` must exist before `AlertasDiariosJob` can use it), plus the natural "skeleton before consumers" shape of the rest:

**Phase A — Foundational (no user-visible behavior change from A1; A2 is a testable skeleton before anything triggers it end-to-end):**
1. **A1:** Extract `RiscoPrazoService` (Pattern 3), repoint all 5 `Prazo`-based + 3 `Evento`-based existing call sites. Zero new tables, pure refactor with a built-in safety property (the 2-arg overload defaults to today's exact `LocalDate.now()` behavior) — verify this in isolation first, independent of everything else below.
2. **A2 (can start in parallel with A1):** `Notificacao` entity + `NotificacaoRepository` + migration script + `NotificacaoService` skeleton (the `criar(...)` choke point, plus recipient-resolution methods) + `NotificacaoController` + RBAC plumbing (`DatabaseSeeder`/`AdminController`/`UserPrincipal`/`permissions.ts`). Demoable via direct API calls before any real event or the frontend touches it.

**Phase B — Event-triggered notifications** *(depends on A2 only)*:
3. Wire the 4 event types into their controller call sites (fase entrada, documento novo, processo atribuído + its new reassignment endpoint, parecer atribuído) — 4 small, independent, parallelizable increments once `NotificacaoService` exists.

**Phase C — Scheduled/batch notifications** *(hard-depends on both A1 and A2 — this is the dependency the question explicitly flags)*:
4. `SchedulingConfig` + `AlertasDiariosJob`, covering prazo/evento criticality (via `RiscoPrazoService`) and the honorários "dias sem pagamento" check (kept local to the job per Anti-Pattern 5). Cannot start meaningfully before both A1 and A2 exist.

**Phase D — Frontend** *(depends on A2; benefits from B and C existing for a realistic end-to-end demo, but the read/mark-read endpoints are independently testable against manually-seeded rows as soon as A2 ships)*:
5. `use-notificacoes.ts` + `NotificationBell` swap-over + dedicated `/notificacoes` page.

This gives: **A1 → (A2 in parallel) → B and C in parallel (C also needs A1) → D**, with C strictly gated on A1+A2 as required, and B/D each gated only on A2.

## Sources

- Direct source inspection (HIGH confidence — primary evidence for every codebase-specific claim above):
  - `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (2898 lines — `computeRisco` at line 1397; `getTenantId()` at line 115; `createProcessoFase`/`updateProcessoFase` at lines 1601-1652; `/eventos/upcoming` at line 2247; `/dashboard` and `/processos/dashboard` KPI blocks at lines 2723-2900; `uploadDocumento` at line 2370)
  - `backend/src/main/java/com/lexcv/controllers/ParecerController.java` (518 lines — `createSolicitacao`, `atribuirAdvogado`)
  - `backend/src/main/java/com/lexcv/controllers/AdminController.java`, `SecurityConfig.java`, `UserPrincipal.java`
  - `backend/src/main/java/com/lexcv/models/{Processo,Evento,Prazo,ProcessoFase,Documento,Honorario,User,AuditLog,ClienteAdvogado,ClienteAdministrativo}.java`
  - `backend/src/main/java/com/lexcv/repositories/{ProcessoRepository,EventoRepository,AuditLogRepository,ClienteAdvogadoRepository,ClienteAdministrativoRepository,TenantRepository}.java`
  - `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` (`seedRbac()` at line 293)
  - `backend/src/main/resources/{application.yml,application-prod.yml}` — confirms `ddl-auto: update` (dev) vs. `validate` (prod)
  - `backend/migrations/{74-cleanup-nif-documento-tipo.sql,81-add-facto-ordem-unique-constraint.sql,82-add-honorario-processo-unique-constraint.sql}` — manual migration precedent and header-comment convention
  - `backend/pom.xml` — confirms no Quartz/ShedLock/messaging dependency present; `@Scheduled` needs no extra dependency beyond `spring-boot-starter-web` (brings in `spring-context`)
  - `docker-compose.yml`, `docker-compose.prod.yml` — confirms single backend container, no replicas (grounds the "no distributed lock needed yet" conclusion)
  - `web/src/components/shared/notification-bell.tsx`, `web/src/app/(dashboard)/agenda/page.tsx`, `web/src/hooks/use-eventos.ts`, `web/src/hooks/use-processos.ts` (`useAllPrazos`), `web/src/lib/api.ts`, `web/src/lib/permissions.ts`
  - `.planning/PROJECT.md` — v2.10 scope, Key Decisions (notably the `valorTotal` null-by-design decision from v2.9, and the "+ADMIN, never mass-notify by view-permission" v2.10 decision)
- Spring Boot 3.4 official documentation (HIGH confidence, fetched via Context7 CLI fallback since MCP tools were unavailable in this session — `npx ctx7@latest docs "/websites/spring_io_spring-boot_3_4" "..."`, source: `https://docs.spring.io/spring-boot/3.4/reference/features/task-execution-and-scheduling.html`): confirms Spring Boot auto-configures a `ThreadPoolTaskScheduler` (single thread by default) once `@EnableScheduling` is present — no manual `TaskScheduler` bean or extra dependency required.
- WebSearch, cross-checked across multiple sources (MEDIUM-HIGH confidence — consistent across independent sources): an uncaught exception in a `@Scheduled` method can silently cancel all future executions of that task under Spring's default scheduler, not just fail the current run — [Baeldung: The @Scheduled Annotation in Spring](https://www.baeldung.com/spring-scheduled-tasks), [A Complete Guide to Spring Boot Scheduler](https://medium.com/@ushandilusha/a-complete-guide-to-spring-boot-scheduler-320eeb88667d), [Exception in @Scheduled Tasks shutdowns Application](https://medium.com/@trivajay259/exception-scheduled-tasks-shutdowns-application-exceptionhandler-or-cleaner-solution-possible-6db4a4a3100d), [spring-projects/spring-framework#31749](https://github.com/spring-projects/spring-framework/issues/31749).
- WebSearch (used only to confirm this codebase does *not* need the enterprise multi-tenant scheduling patterns it surfaced — MEDIUM confidence, contextual only): schema-per-tenant/DB-per-tenant Quartz-clustering patterns exist for genuinely isolated-database multi-tenancy, which does **not** describe LexCV (shared schema, `tenant_id` column, single datasource) — [Scaling Background Tasks in SaaS](https://yogeshbali.medium.com/scaling-background-tasks-in-saas-from-scheduled-to-multi-tenant-quartz-with-dedicated-databases-c1bdb82473dc), [Baeldung: Multitenancy With Spring Data JPA](https://www.baeldung.com/multitenancy-with-spring-data-jpa).

---
*Architecture research for: LexCV v2.10 (Notificações e Alertas) — backend integration of a persisted notification entity, event-triggered writes, and a daily cross-tenant scheduled scan*
*Researched: 2026-07-08*
