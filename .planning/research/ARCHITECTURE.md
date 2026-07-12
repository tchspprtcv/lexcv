# Architecture Research — v2.11 Auditoria Técnica e Notificações Avançadas

**Domain:** Integration architecture for NOTF-24/25/26 into the existing `Notificacao`/`NotificacaoService`/`AlertasDiariosJob` subsystem (v2.10), plus Agenda/`RiscoPrazoService` unification and a minimal Testcontainers/H2 test-infrastructure slice for this specific Spring Boot 3.4.1 / Java 23 backend.
**Researched:** 2026-07-12
**Confidence:** HIGH for all file/line-level facts (read directly from the repo); MEDIUM for product-shape recommendations that depend on an unstated business decision (flagged explicitly below); HIGH for the Testcontainers pattern (corroborated by docs.spring.io + independent guides).

This is not a greenfield domain survey — v2.10 already built the entire notification subsystem (superseding the prior v2.10-era research file that used to live at this path). This document answers exactly how three new requirements (NOTF-24/25/26) and one test-infra gap graft onto that existing code, file by file.

---

## 1. Existing subsystem — the four choke points that matter

| Component | File | Role |
|---|---|---|
| `Notificacao` | `backend/src/main/java/com/lexcv/models/Notificacao.java` | Immutable-after-creation row except `lida` (only field with a `@Setter`). Composite unique constraint `uk_notificacao_dedup` on `(tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)`. |
| `NotificacaoService.criar(...)` | `backend/src/main/java/com/lexcv/services/NotificacaoService.java:33` | **"ÚNICO ponto de escrita de CRIAÇÃO de Notificacao em todo o código"** (comment at line 27) — every trigger, every fan-out, and the daily job all funnel through this one method. |
| `NotificacaoRepository` | `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` | `buscarPorFiltros` (native query + `Pageable`, first of its kind in this backend), `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria` (the dedup check used by the daily job). |
| `AlertasDiariosJob.notificar(...)` | `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:307` | The job's own choke point — checks `existsBy...` then delegates to `NotificacaoService.criar`. Never calls `notificacaoRepository.save` directly. |

Every one of NOTF-24/25/26 is best understood as "what do we insert at or near `criar(...)`," not as three independent features.

`NotificacaoServiceTest.java` currently has **20 direct call sites** of `new NotificacaoService(notificacaoRepository, userRepository)` (verified via grep). Any new `final` constructor dependency added to `NotificacaoService` for any of the three requirements breaks compilation at all 20 sites simultaneously — a mechanical, non-optional edit that must land in the same change as the constructor edit. This is the single biggest shared-file risk in this milestone.

---

## 2. NOTF-24 — per-user notification category preferences

### Where the preferences table lives

New entity `NotificacaoPreferencia` (new file, mirrors the `ClienteAdvogado`/`ClienteAdministrativo` shape — `UUID` id, no soft-delete, `@PrePersist createdAt`):

```
t_notificacao_preferencia
  id            UUID PK
  tenant_id     UUID NOT NULL
  user_id       UUID NOT NULL
  categoria     VARCHAR NOT NULL
  created_at    TIMESTAMP
  UNIQUE (tenant_id, user_id, categoria)
```

**Design choice: presence of a row = muted, absence = delivered (default-on).** This avoids seeding 9 rows per user at account creation and matches the v2.10 decision that "todas as categorias são sempre entregues" was the *default*, not a hardcoded guarantee — NOTF-24 only needs to represent the *exceptions*. A `NotificacaoPreferenciaRepository.existsByTenantIdAndUserIdAndCategoria(tenantId, userId, categoria)` boolean is the entire read contract `criar()` needs.

### How `NotificacaoService`'s recipient-resolution step consults it

Insert the mute-check **inside `criar()`**, immediately after the existing `destinatarioId`-belongs-to-tenant validation (line ~41) and before persistence — not in each of the four `notificarX` trigger methods, and not in `AlertasDiariosJob`. Because `criar()` is the sole write path, this one change automatically gates:
- All 4 event-triggered alerts (`notificarFaseEntrada`, `notificarProcessoAtribuido`, `notificarDocumentoNovo`, `notificarParecerAtribuido`)
- The `notificarAdmins` fan-out (an ADMIN who mutes `DOCUMENTO_NOVO` stops receiving it via fan-out too — this is intentional: muting is a personal delivery preference, orthogonal to the NOTF-14 targeting/no-mass-broadcast rule, which governs *who is eligible*, not *whether an eligible recipient wants it*)
- `AlertasDiariosJob`, for free, with zero changes to the job itself

`criar()` currently returns `Notificacao` (non-`Optional`); none of the 8 call sites use the return value for anything except the test file's `ArgumentCaptor` assertions. Recommend changing the return type to `Optional<Notificacao>` (empty when muted) rather than silently returning `null`, and updating the ~20 test call sites' assertions accordingly.

**Mockito-safety note:** a new `NotificacaoPreferenciaRepository` mock, when unstubbed, returns `false` from `existsBy...` by default (Mockito's default for a primitive `boolean`) — meaning all 20 existing `NotificacaoServiceTest` cases keep passing unmodified for their actual assertions once the constructor call is mechanically updated, because "not stubbed" reads as "not muted," which is the existing default-on behavior. This is a real, low-risk migration path, not just a hope.

### Category enum gap this surfaces

The backend has **no canonical enum** for `categoria` — every trigger hardcodes its own string literal (`"FASE_ENTRADA"`, `"DOCUMENTO_NOVO"`, etc.), 9 values total. The frontend, by contrast, already has the canonical list as a union type: `web/src/types/notificacoes.ts:1-10` (`NotificacaoCategoria`). Recommend a small `CategoriaNotificacao` Java enum (same 9 values, `.name()` used for storage) used **only** to validate incoming preference-toggle requests — do not retrofit the 9 existing hardcoded string call sites to use it. This matches the project's established "surgical over global" precedent (e.g. the `@JsonProperty`-per-field decision in Key Decisions) and keeps NOTF-24's blast radius to one new file plus the preference endpoints.

### New endpoints (add to `NotificacaoController`, not `ResourceController`)

`NotificacaoController.java` is already the dual tenant+user-scoped controller (its own doc comment explains why it was extracted). Preferences are inherently "my own settings," so they belong here:
- `GET /api/v1/notificacoes/preferencias` — list muted categories for the caller (`getTenantId()`/`getUserId()`, same pattern as every other method in this file)
- `PUT /api/v1/notificacoes/preferencias/{categoria}` / `DELETE .../{categoria}` — toggle mute for one category, validated against `CategoriaNotificacao`

RBAC: reuse `notificacoes:view` (self-service, not administrative — never accept a `userId` from the request body, always derive from the JWT, matching `getUserId()` everywhere else in this file).

### Frontend

New `web/src/hooks/use-notificacao-preferencias.ts` (same TanStack Query shape as `use-notificacoes.ts`). UI: a new section on the existing `/settings` page (`web/src/app/(dashboard)/settings/page.tsx`) — the 9 categories are already enumerable client-side via `NotificacaoCategoria`.

---

## 3. NOTF-25 — notify the full process team, not just `responsavelId`

### What "team" resolves to today, and why that matters

There is **no processo-level team table**. Team membership exists only at the **cliente** level via `ClienteAdvogado`/`ClienteAdministrativo` (`backend/src/main/java/com/lexcv/models/ClienteAdvogado.java`, `ClienteAdministrativo.java` — both `(tenant_id, cliente_id, user_id)`-unique join tables).

Critically, **this pattern is already half-implemented** for one of the four triggers. `ResourceController.java:2600-2624` (the upload handler behind `notificarDocumentoNovo`) branches on where the uploaded `Documento` attaches:
- `saved.getProcessoId() != null` → `dests = List.of(processo.getResponsavelId())` (single recipient, or empty) — **this is the exact gap NOTF-25 closes**
- `saved.getClienteId() != null` → `dests` built from `clienteAdvogadoRepository.findByClienteIdAndTenantId(...)` + `clienteAdministrativoRepository.findByClienteIdAndTenantId(...)` — **this is already "full team," just not for processos**

### Recommendation: reuse `ClienteAdvogado`/`ClienteAdministrativo` transitively via `Processo.clienteId`, not a new join table

`Processo` already carries `clienteId` (`backend/src/main/java/com/lexcv/models/Processo.java:24`). Define "processo team" = `{responsavelId}` ∪ the processo's cliente's `ClienteAdvogado` + `ClienteAdministrativo` members. This requires **zero new tables/migrations** and mirrors the exact code already sitting 20 lines away in `ResourceController`. This is the recommended MVP path for this milestone: lowest risk, no new UI for team management, consistent with the project's repeated "reuse existing pattern over introducing a new one" decisions (see PROJECT.md Key Decisions rows on `ClienteAdvogado`/`ClienteAdministrativo` and on pareceres reusing existing UI patterns).

**Open question requirements must resolve, not research:** if the actual product intent is that a processo's working team can genuinely diverge from the client's overall team (e.g. a specialist added to one matter only), the correct design is a new `t_processo_equipe (tenant_id, processo_id, user_id)` join table plus UI to manage it on the ficha do processo — a materially larger scope (new entity, repository, endpoints, "Equipa" UI section). Given "equipa do processo" is the literal wording of NOTF-25, flag this explicitly for the roadmap/requirements step rather than assuming the cheaper reading. The rest of this section assumes the cheaper (cliente-transitive) reading, since it is very likely what fits in one milestone alongside NOTF-24/26 and the tech-debt backlog.

### Centralize resolution in `NotificacaoService`, not per-controller

Add one new helper to `NotificacaoService` (new dependencies: `ClienteAdvogadoRepository`, `ClienteAdministrativoRepository` — both already exist, currently only wired into `ResourceController`):

```java
Set<UUID> resolverEquipaProcesso(UUID tenantId, UUID clienteId, UUID responsavelId) {
    Set<UUID> equipa = new LinkedHashSet<>();
    if (responsavelId != null) equipa.add(responsavelId);
    clienteAdvogadoRepository.findByClienteIdAndTenantId(clienteId, tenantId).forEach(a -> equipa.add(a.getUserId()));
    clienteAdministrativoRepository.findByClienteIdAndTenantId(clienteId, tenantId).forEach(a -> equipa.add(a.getUserId()));
    return equipa;
}
```

Doing this **inside** `NotificacaoService` rather than re-deriving the union at each of the 4 call sites is a direct, deliberate application of the lesson this very milestone is trying to close for `RiscoPrazoService` (Phase 85: "consolidate one shared source instead of a Nth divergent copy"). Don't let NOTF-25 create a second, uncoordinated team-resolution implementation.

Callers already have the `Processo` object loaded at every relevant site (`ResourceController.java:988` `createProcesso`, `~1055` `atribuirResponsavel`, `1703-1724` `createProcessoFase`, `2601` documento upload) — pass `processo.getClienteId()` through rather than adding a `ProcessoRepository` dependency to `NotificacaoService` to re-fetch it. This keeps `NotificacaoService`'s collaborator count minimal, at the cost of two extra parameters on 3 method signatures — a deliberate, worthwhile trade.

### Per-trigger impact

| Trigger | Change |
|---|---|
| `notificarFaseEntrada` | Single `responsavelId` param → resolve team, notify each (same per-recipient try/catch isolation pattern already used by `notificarAdmins`/`notificarDocumentoNovo`) |
| `notificarProcessoAtribuido` | **Message-shape nuance:** keep the 2ª-pessoa message ("Foi-lhe atribuído...") for `responsavelId` only; team members other than the newly-assigned responsável need a 3ª-pessoa variant analogous to the ADMIN message — this is not a mechanical find-and-replace, it changes the method's branching |
| `notificarDocumentoNovo` (processo branch) | Change `ResourceController.java:2601-2606` from `dests = List.of(resp)` to the same team-resolution call already used 10 lines below for the cliente branch — this collapses two near-duplicate branches into one shared helper call |
| `notificarParecerAtribuido` | **Recommend leaving as single-`advogadoId` recipient.** A `ParecerSolicitacao` (`backend/src/main/java/com/lexcv/models/ParecerSolicitacao.java`) always has a `clienteId` but "atribuído a um advogado" is semantically a single-person assignment — broadcasting it to the whole cliente team would contradict the meaning of "atribuição." Flag for requirements confirmation; do not silently fold this trigger into NOTF-25's scope. |

`AlertasDiariosJob`'s three `processar*` methods also currently notify only `processo.getResponsavelId()` + admins for prazos/eventos/honorários — the question scopes NOTF-25 to "the 4 existing triggers" (Phase 87), so treat the daily job as **out of scope** for this requirement unless the roadmap says otherwise. It is a cheap follow-on later since the helper will already live in `NotificacaoService`, which the job already depends on.

---

## 4. NOTF-26 — snooze a deadline reminder

### The dedup risk, resolved without touching `uk_notificacao_dedup`

The question's concern is real: `uk_notificacao_dedup` is `(tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)` with no time dimension. If snooze were modeled as "hide the row, then let `AlertasDiariosJob` re-create it later," the job's own `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria` check (`AlertasDiariosJob.java:312`) would find the original row still exists and **permanently refuse** to create a new one — the reminder would never resurface. Widening the dedup key (e.g. adding a date/cycle column) would also defeat Phase 88's entire edge-triggered design, turning a "once per risk-level-crossing" alert into a recurring daily one for every unresolved prazo — a much bigger, unwanted behavior change.

**Recommendation: dedup needs no new dimension.** Model snooze as a visibility toggle on the *same, already-existing* row, not as a request to re-create it:

- Add `snoozedUntil` (nullable `LocalDateTime`, `@Setter`-only — same mutability pattern already used for `lida`) directly to `Notificacao.java`. New manual migration script following the existing `NN-*.sql` convention (e.g. `backend/migrations/<phase>-add-notificacao-snoozed-until.sql`), same as the `uk_notificacao_dedup` and `Honorario`/`Facto` unique-constraint precedents (`ddl-auto=validate` in prod never creates this from the annotation alone).
- New `NotificacaoService.snooze(tenantId, destinatarioId, id, until)` — same find-then-mutate-then-save shape as `marcarLida` (`NotificacaoService.java:244`), same 404-via-empty-`Optional` contract, same choke-point discipline ("nenhuma outra classe deve chamar `notificacaoRepository.save(...)`").
- New endpoint `PATCH /api/v1/notificacoes/{id}/snooze` in `NotificacaoController`, mirroring `marcarLida`'s dual tenant+destinatario scoping exactly.
- **Bell/unread queries change; the `/notificacoes` history page does not.** `NotificacaoRepository.countByTenantIdAndDestinatarioIdAndLidaFalse` and `findByTenantIdAndDestinatarioIdAndLidaFalse` (feeding the badge count and "mark all read") need an added `AND (snoozed_until IS NULL OR snoozed_until <= :now)` predicate so a currently-snoozed item is invisible to the noisy "unread" surface. The full-history `buscarPorFiltros` query (native query behind `/notificacoes`) should **not** change — it is explicitly a browsable audit view; a snoozed item should still be findable there (optionally with a "Adiado até DD/MM" badge), it just shouldn't drive the bell badge or appear in the dropdown while deferred.
- Once `snoozedUntil` elapses, **the same row reappears** in the unread queries automatically — no job involvement, no new row, no dedup interaction at all. This is the key simplification: snooze and the daily job's idempotency are made orthogonal by construction, rather than reconciled.

### Escalation interacts correctly, by construction

If a snoozed `PRAZO_PROXIMO` prazo crosses into `PRAZO_VENCIDO` while still snoozed, that is a **different `categoria` value**, hence a different dedup tuple — `AlertasDiariosJob` will correctly create a brand-new, fully active (non-snoozed) `PRAZO_VENCIDO` row regardless of the earlier snooze. Escalation to a worse risk band is never silently suppressed by an earlier snooze on the milder band. Verify this exact interaction with a test once Testcontainers infra exists (Section 6) — it is a natural, non-obvious case worth asserting explicitly, not just reasoning about.

### Domain-specific product-safety recommendation

This is a *legal deadline* reminder in a Cape Verde legal-practice product — snoozing a "prazo fatal" into oblivion has real consequences. Recommend: (a) a fixed, server-validated set of snooze durations (e.g. 1/3/7 dias) rather than a free-form date picker, and (b) capping `snoozedUntil` so it can never be pushed past the underlying `Prazo.dataLimite` itself, and/or disallowing snooze entirely on `PRAZO_VENCIDO` (already-overdue) — these are product decisions to confirm during requirements, not something research should silently assume, but they should be explicitly asked rather than left implicit.

### Scope of what's snoozable

NOTF-26's own name ("lembrete de **prazo**") argues for restricting the snooze *action* server-side to `categoria ∈ {PRAZO_PROXIMO, PRAZO_VENCIDO}` at first (reject others with 400), while keeping the `snoozedUntil` column generic on the entity so extending to `EVENTO_*`/`HONORARIO_ATRASADO` later is a one-line validation change, not a schema change.

---

## 5. Shared-file collision map across NOTF-24/25/26

All three requirements converge on the same small set of files. This is the most important build-order constraint in this milestone:

| File | NOTF-24 | NOTF-25 | NOTF-26 |
|---|---|---|---|
| `NotificacaoService.java` | new mute-check in `criar()`, new constructor dep | new `resolverEquipaProcesso`, signature changes on 3 trigger methods, new constructor deps | new `snooze()` method |
| `NotificacaoServiceTest.java` | all ~20 constructor calls | all ~20 constructor calls (again) | none required, but likely gains new cases |
| `NotificacaoRepository.java` | none | none | new/modified unread-count and unread-list queries |
| `NotificacaoController.java` | new preferences endpoints | none | new snooze endpoint |
| `ResourceController.java` | none | edits at lines ~990, ~1055, ~1724, ~2601-2624 | none |

**Do not execute NOTF-24/25/26 as parallel phases.** They are logically independent features but mechanically collide on `NotificacaoService.java` (and its test file) every time. Sequence them.

**Recommended internal order: NOTF-24 → NOTF-25 → NOTF-26.**
- NOTF-24 first because its change to `criar()` is the smallest, most self-contained insertion (one guard clause) — good foundation, lowest risk to land first.
- NOTF-25 second, specifically *after* NOTF-24, so every new team-member recipient NOTF-25 fans out to automatically inherits the mute-preference gate for free (both funnel through the same `criar()`). Reversing the order still works, but doing 24 first means NOTF-25's own tests don't need to separately re-verify preference interaction — it was already proven correct one layer down.
- NOTF-26 last: almost entirely additive (new column, new endpoint, new query predicates), least likely to conflict with the other two's core logic changes, and is naturally "the last-mile UX layer" on top of an already-correct notification stream.

---

## 6. Agenda ↔ `RiscoPrazoService` unification

### The endpoint already exists — no new backend endpoint needed for prazos

`useAllPrazos()` (`web/src/hooks/use-processos.ts:728`) already calls `GET /prazos`, and that endpoint (`ResourceController.java:1528` `listAllPrazos`) **already returns a backend-computed `risco` field** via `riscoPrazoService.computeRisco(...)` (line 1533) — the exact same `RiscoPrazoService` (Phase 85) consumed by the dashboard and `AlertasDiariosJob`. This is the "prazo-listing endpoint already used elsewhere" the question asks about, and it is sufficient as-is for prazos.

### Where the divergence actually lives in `agenda/page.tsx`

Reading the file directly (`web/src/app/(dashboard)/agenda/page.tsx`), the "5th divergent implementation" is not a recomputation of the risk formula itself — it's that the page **silently drops the `risco` field it already receives**, then substitutes a cruder proxy:

1. `allUnifiedEvents` (line 95-106) maps each `Prazo` into the unified calendar-event shape but only copies `id, tenantId, processoId, titulo, descricao, dataInicio, dataFim, prioridade, concluido, isPrazo` — `p.risco` (present on the `Prazo` type, `web/src/types/processos.ts:267`) is never carried over.
2. `weekStats.urgentes` (line 166) computes "Urgentes" as `active.filter(e => e.prioridade === "ALTA").length` — a static priority check with **no date-proximity component at all**, diverging from `RiscoPrazoService.computeRisco`'s actual threshold logic (≤7 dias for ALTA, ≤3 dias otherwise, `VENCIDO` if already past). An ALTA-priority prazo 60 days out currently counts as "urgente" here; a MEDIA-priority prazo due tomorrow does not.
3. `getCategoria(e)` (line 568) categorizes by `tipo`/title-text matching only (PRAZO/AUDIENCIA/DILIGENCIA/REUNIAO) — this is a different, legitimate concept (event *type*, not risk) and does not need to change.

### The one real gap: `GET /eventos` has no `risco` field at all

Prazos carry `risco` end-to-end already; **eventos never have**. `ResourceController.listEventos` (`ResourceController.java:2231`) returns raw `Evento` rows with no risk field — unlike `GET /eventos/upcoming` (line 2358) and the dashboard KPI code, which already call `riscoPrazoService.computeRiscoEvento(...)` inline. `web/src/types/eventos.ts`'s `Evento` interface has no `risco` field to match.

### Minimal refactor path

1. **Backend, 1-line-per-call addition, no new endpoint:** extend `listEventos` to add `"risco", riscoPrazoService.computeRiscoEvento(e.getDataInicio(), e.getPrioridade())` to each returned `Evento`, mirroring the identical one-liner already present in `getUpcomingEventos` and the dashboard KPI path. This is strictly additive (new JSON field), so it cannot break the (nonexistent) other consumers of `GET /eventos`'s current shape.
2. **Frontend types:** add `risco?: PrazoRisco` to `Evento` (`web/src/types/eventos.ts`) and confirm it's already present on `Prazo` (`web/src/types/processos.ts:267` — it is).
3. **`agenda/page.tsx`:** in the `allUnifiedEvents` mapping, stop dropping `p.risco`/`e.risco` — carry it through into the unified shape.
4. Replace `weekStats.urgentes`'s `prioridade === "ALTA"` check with `risco === "proximo" || risco === "vencido"` (or `risco === "vencido"` only, depending on the desired "Urgentes" definition — a product call, not an architecture one).
5. Reuse the existing `prazosRiscoToVariant`/`prazosRiscoToLabel` helpers (`web/src/lib/prazos.ts`, already used on the processo-detail and processos-list pages) to render a risk-colored badge on calendar day pills — do not invent a second color-mapping.

### Sequencing note vs. NOTF-25

Step 1 above edits `ResourceController.listEventos` — a **different method** in the same file NOTF-25 also edits (`createProcesso`, `atribuirResponsavel`, `createProcessoFase`, the documento-upload handler). Different methods, same ~2900-line file: low conflict probability, but do not run these two phases as literally-concurrent edits; sequence them (either order is fine — there's no logical dependency, only a shared-file caution).

---

## 7. Minimal Testcontainers/H2 setup for this backend

### Current state (verified from `backend/pom.xml`)

Zero test-database dependencies exist. `spring-boot-starter-parent:3.4.1` is the parent POM, which transitively manages Testcontainers BOM versions — no explicit Testcontainers version needs pinning. The three existing backend tests (`RiscoPrazoServiceTest`, `NotificacaoServiceTest`, `AlertasDiariosJobTest`) are pure Mockito/JUnit5 unit tests with zero Spring context and zero database — this pattern continues to be correct and should **not** be replaced wholesale; Testcontainers is additive, for the two specifically-flagged risk areas only.

### Dependencies to add (test scope, no version needed — managed by the parent BOM)

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
```

Use image `postgres:16-alpine` — the exact image already pinned in `docker-compose.yml` for dev/prod, so behavior parity with the real deployment target is guaranteed, not assumed.

### Why `@DataJpaTest` sidesteps the `MINIO_ENDPOINT` blocker entirely — no workaround needed

The recurring `MINIO_ENDPOINT` failure (documented in `.planning/milestones/v2.10-MILESTONE-AUDIT.md` and PROJECT.md Context) happens because `MinioConfig.s3Client()`/`s3Presigner()` (`backend/src/main/java/com/lexcv/config/MinioConfig.java`) are `@Configuration`-scoped beans that eagerly resolve `${MINIO_ENDPOINT}` via `MinioProperties`, and `application.yml` declares every property as a required env var with no default. This only breaks a **full** `@SpringBootTest` context (the kind that would exercise `SetupController`/full HTTP round-trips).

`@DataJpaTest` does not load arbitrary `@Configuration` classes — it slices the context to JPA infrastructure (entities, repositories, a `JpaTransactionManager`) and explicitly excludes regular `@Configuration` beans like `MinioConfig`, `SecurityConfig`, `JwtProvider`, etc. Since `MinioProperties`/`JwtProperties`/`CorsProperties` beans are simply never instantiated in this slice, their unresolved placeholders never get evaluated, and the `MINIO_ENDPOINT` blocker cannot occur. **No exclusion annotation, no mock bean, no profile trick is needed** — using the narrowest correct test slice is the workaround, by construction.

This also means: use `@DataJpaTest`, not `@SpringBootTest`, for both flagged risk areas. Neither `NotificacaoRepository.buscarPorFiltros` nor the `ParecerSolicitacao`/`ParecerVersao` locking behavior requires HTTP, `@PreAuthorize`, or MinIO — they are pure repository/transaction-manager concerns.

### Pattern A — `NotificacaoRepository.buscarPorFiltros` (Phase 86 risk)

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class NotificacaoRepositoryIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired NotificacaoRepository notificacaoRepository;

    @Test
    void buscarPorFiltros_combinaTenantDestinatarioCategoriaLida_comPageable() {
        // persist a few Notificacao rows via notificacaoRepository.save(...),
        // then assert buscarPorFiltros(tenantId, destinatarioId, categoria, lida, PageRequest.of(0, 10))
        // returns the right page/content/totalElements against a REAL Postgres -- this is the
        // exact nativeQuery+Pageable combination that has never executed against Postgres before.
    }
}
```

`@AutoConfigureTestDatabase(replace = Replace.NONE)` is required alongside `@ServiceConnection` — `@DataJpaTest` defaults to swapping in an embedded database, which would silently override the Testcontainers-provided Postgres connection if not disabled.

### Pattern B — `numeroVersao` PESSIMISTIC_WRITE lock (Phase 87 risk)

This is a genuine concurrency test, not just a repository query test — it needs two **independently-committing** transactions racing on `ParecerSolicitacaoRepository.findByIdForUpdate` (`backend/src/main/java/com/lexcv/repositories/ParecerSolicitacaoRepository.java:22`). A non-obvious but important gotcha: `@DataJpaTest` wraps each test method in a single transaction that rolls back at the end by default — a genuine 2-thread lock test needs that implicit wrapping turned **off** for the specific test method (`@Transactional(propagation = Propagation.NOT_SUPPORTED)` on the test method), then two real, separate transactions driven manually via an injected `PlatformTransactionManager`/`TransactionTemplate` on two threads (`ExecutorService` + `CountDownLatch` to sequence "thread A holds the lock" → "thread B blocks" → "thread B proceeds only after A commits"), asserting the two resulting `numeroVersao` values are sequential (e.g. 1 and 2, never a duplicate 1 and 1). This is the DB-level guarantee `WR-04` in `ParecerController.java:472` relies on and that a JVM-only `synchronized` block could never prove.

### Net effect on the pending UAT/verification gaps

Writing these two tests is not separate from the "10 phases with pending UAT" tech-debt item — it **is** how Phase 86's and Phase 87's specific `human_needed` verification gaps (STATE.md Deferred Items table) get closed. The other 8 pending UAT items (75, 76, 79, 81, 82, 84, 85, 89) are live-browser/HTTP walkthroughs, unrelated to Testcontainers, and mostly blocked by the separate `MINIO_ENDPOINT` env-resolution issue in full-context/live runs — a different problem from the one this section solves, requiring an actual working `backend/.env` (or a documented local MinIO/docker-compose workaround), not a test-slice choice.

---

## 8. Recommended build order for the whole milestone

```
Track 1 (infra/tooling, no feature-file overlap with Track 2/3):
  1. Resolve/document the MINIO_ENDPOINT blocker (unblocks live UAT for 87/89 later)
  2. SpotBugs/SAST vs JDK 23 bytecode fix (backend/spotbugs-exclude.xml already touched, untracked)
  3. Testcontainers/H2 infra + Pattern A (Notificacao native query) + Pattern B (numeroVersao lock)
     -> closes Phase 86 + Phase 87 verification gaps as a side effect

Track 2 (frontend-only, fully independent of Track 3):
  4. Agenda <-> RiscoPrazoService unification (agenda/page.tsx + Evento.risco enrichment)

Track 3 (notification features, MUST be sequential -- shared NotificacaoService.java):
  5. NOTF-24 (preferences)
  6. NOTF-25 (process team)      -- after 24, so new recipients inherit the mute gate for free
  7. NOTF-26 (snooze)            -- after 25, smallest remaining blast radius

Track 4 (housekeeping, best done last so the audit sees final state):
  8. Minor debt closure (enum label translations, NIF validation tests) + fresh gap audit
  9. Remaining live/manual UAT closure (75, 76, 79, 81, 82, 84, 85, 89) once MINIO_ENDPOINT (step 1)
     and all code changes above have landed
```

**Do not attempt these in the same phase / concurrently:**
- NOTF-24, NOTF-25, NOTF-26 with each other (Section 5 — all three edit `NotificacaoService.java` and its test file).
- NOTF-25 with the Agenda unification's `GET /eventos` change, if both touch `ResourceController.java` in the same execution window (different methods, low but non-zero collision risk — sequence rather than parallelize).
- Any live/manual UAT closure work before step 3 (Testcontainers) exists, for the two phases (86, 87) whose gaps are specifically test-infrastructure-shaped, not browser-shaped — attempting those live/manually first duplicates effort that automated tests will supersede.
- The final housekeeping audit (step 8) before Track 3 lands — an audit run mid-milestone would not see NOTF-24/25/26's final integration shape and would need to be redone, repeating the exact "audit runs at milestone close" pattern already established in this project's v2.7/v2.9/v2.10 retrospectives.

Track 1 and Track 2 have no file overlap with each other or with Track 3, and can run in either order or in parallel with each other; Track 3's internal order is a hard constraint, not a suggestion.

---

## Sources

- Direct repository reads (all file:line references above verified against the actual working tree, 2026-07-12): `Notificacao.java`, `NotificacaoService.java`, `NotificacaoRepository.java`, `NotificacaoController.java`, `AlertasDiariosJob.java`, `RiscoPrazoService.java`, `Processo.java`, `Prazo.java`, `ClienteAdvogado.java`, `ClienteAdministrativo.java`, `ParecerVersao.java`, `ParecerSolicitacao.java`, `ParecerSolicitacaoRepository.java`, `ParecerVersaoRepository.java`, `ParecerController.java`, `ResourceController.java`, `backend/pom.xml`, `backend/src/main/resources/application.yml`, `docker-compose.yml`, `web/src/app/(dashboard)/agenda/page.tsx`, `web/src/hooks/use-processos.ts`, `web/src/hooks/use-eventos.ts`, `web/src/hooks/use-notificacoes.ts`, `web/src/types/{eventos,processos,notificacoes}.ts`, `web/src/lib/prazos.ts`, `.planning/PROJECT.md`, `.planning/STATE.md`.
- [Testcontainers :: Spring Boot (docs.spring.io)](https://docs.spring.io/spring-boot/reference/testing/testcontainers.html) — confirms `spring-boot-testcontainers`/`junit-jupiter`/`postgresql` dependency set and that versions are managed by `spring-boot-starter-parent`. HIGH confidence.
- [Integration tests with Testcontainers and Spring Boot 3.1+ (Medium, Aleksander Kołata)](https://medium.com/@aleksanderkolata/integration-tests-with-testcontainers-and-spring-boot-3-1-39103ff95bd7) and [Everything about testcontainers on Spring Boot 3.1 (Rabobank tech blog)](https://rabobank.jobs/en/techblog/everything-about-testcontainers-on-spring-boot-3-1/) — corroborate the `@ServiceConnection` + `@AutoConfigureTestDatabase(Replace.NONE)` combination for `@DataJpaTest`. MEDIUM-HIGH confidence (community sources, consistent with official docs).

---
*Architecture research for: LexCV v2.11 (Auditoria Técnica e Notificações Avançadas)*
*Researched: 2026-07-12*
