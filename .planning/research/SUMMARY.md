# Project Research Summary

**Project:** LexCV — v2.11 "Auditoria Técnica e Notificações Avançadas"
**Domain:** Tech-debt closure + notification-system extension on an existing multi-tenant Spring Boot 3.4.1 / Java 23 + Next.js 16 legal-practice management platform
**Researched:** 2026-07-12
**Confidence:** HIGH

## Executive Summary

This milestone is not greenfield feature work — it is a retrofit milestone on a mature, already-shipped codebase (v2.10's `Notificacao`/`NotificacaoService`/`AlertasDiariosJob` subsystem, Phase 85's `RiscoPrazoService`, and a backend that has never had integration tests or a working SAST pipeline). All four research tracks converge on the same conclusion: **almost nothing needs to be invented — everything needs to be extended, consolidated, or committed.** No new runtime dependency is required for the three notification features (NOTF-24/25/26); the frontend already has every UI primitive needed (`Switch`, `RadioGroup`, `Popover`), and the backend pattern (a single `NotificacaoService.criar()` write choke point) already exists and just needs new logic threaded through it. The one genuinely new dependency is test-scoped: Testcontainers' PostgreSQL module (version 1.20.4, inherited from the parent BOM — explicitly do not pin the newly-released 2.0.x line, which renamed artifacts and breaks Spring Boot 3.4.x's `@ServiceConnection`). Separately, the SpotBugs/FindSecBugs-on-JDK-23 fix is already ~90% done, uncommitted, in the working tree — this milestone's SAST job is to verify and commit it, not re-engineer it.

The recommended approach is architecture-led: because `NotificacaoService.criar()` is the sole write path for every notification (event-triggered and daily-job-generated alike), NOTF-24 (mute), NOTF-25 (team fan-out), and NOTF-26 (snooze) should be built **sequentially, not in parallel** — all three mechanically collide on the same file (`NotificacaoService.java`) and its 20-call-site test file. The recommended internal order is NOTF-24 → NOTF-25 → NOTF-26, so each new team-fan-out recipient automatically inherits the mute gate for free, and snooze — the most additive, lowest-risk feature — lands last. Testing infrastructure (Testcontainers, not H2, for the two Postgres-dialect-sensitive risk areas: a native query and a concurrency lock) and the SpotBugs commit are independent tracks with no file overlap and can run in parallel with the notification work or before it.

The key risks are integration risks, not technology risks, and PITFALLS.md surfaces one that is unusually consequential: a **pre-existing, currently-live bug** where `uk_notificacao_dedup` (added Phase 88) will throw an uncaught `DataIntegrityViolationException` whenever a fan-out recipient is also an ADMIN (a Phase 87 comment explicitly assumed double-delivery was safe, before the Phase 88 constraint existed). This bug gets strictly worse as NOTF-25 widens fan-out from one recipient to a full team, and must be fixed as a standalone discovered-gap item before/alongside NOTF-25, not deferred. Other high-value risks: a mute check placed anywhere except inside `criar()` will be silently bypassable by the daily job; snooze naively implemented as "hide the row" will be resurrected by the job's idempotency check the very next morning; and any new query/table introduced by these features must carry an explicit `tenant_id` filter, not rely on `user_id`/`cliente_id` alone (this project has a documented history of exactly this class of leakage bug).

## Key Findings

### Recommended Stack

No new frontend dependency is needed — `@radix-ui/react-switch`, `-radio-group`, and `-popover` are already installed and wrapped in `web/src/components/ui/*`, covering every UI need for mute toggles, team-fan-out (no UI needed), and fixed-duration snooze presets. On the backend, the only new dependency set is test-scoped: Testcontainers' PostgreSQL module + JUnit-Jupiter integration + the `spring-boot-testcontainers` starter, all with **no explicit `<version>` tag** so they resolve to the parent-managed 1.20.4 (do not manually import the newly-released Testcontainers 2.0.x BOM — it renamed artifacts/relocated classes and is not what Spring Boot 3.4.x's `@ServiceConnection` auto-configuration expects; see `spring-projects/spring-boot#47639`). SpotBugs (4.10.2.0) and FindSecBugs (1.14.0) are already the current-latest versions and already bumped, uncommitted, in `backend/pom.xml`.

**Core technologies:**
- Testcontainers `postgresql` module (1.20.4, inherited) — spins up a real disposable `postgres:16-alpine` container (matching prod/dev exactly) for the two Postgres-dialect-sensitive risk areas
- `spring-boot-testcontainers` starter + `@ServiceConnection` — auto-wires the container into Spring's `DataSource`, no manual `@DynamicPropertySource`
- `@DataJpaTest` (not `@SpringBootTest`) — sidesteps the `MINIO_ENDPOINT`-required-env-var wall entirely by construction, since it never instantiates `MinioConfig`/`SecurityConfig`
- Existing `@radix-ui/react-switch`/`-radio-group`/`-popover` — zero new frontend libraries for NOTF-24/25/26

### Expected Features

**Must have (table stakes) — NOTF-24/25/26 MVP:**
- Per-user, per-category mute toggle defaulting to "on" (opt-out model) — universal pattern once a system has >3 categories (LexCV has 9)
- Notify more than the single assignee on a shared processo (assignee + team, not assignee-only) — the Clio "Responsible Attorney + Responsible Staff" and Jira/GitHub "assignee + watchers" patterns both treat single-assignee-only as an incomplete model
- Snooze = hide until a concrete, bounded future date/time, then reappear automatically as unread — every reminder-app precedent treats an unbounded "snooze forever" as an anti-pattern, not a feature

**Should have (competitive/safety differentiators):**
- At least one non-mutable "cannot silence" category (`PRAZO_VENCIDO` at minimum) — general SaaS guidance says legally-significant alerts should not be fully opt-outable, and this domain's direct analogue is an already-breached deadline
- Escalation (a worse categoria, e.g. `PRAZO_VENCIDO` after a snoozed `PRAZO_PROXIMO`) must always break through snooze — achieved for free here because the job creates a new row per categoria transition
- A "Snoozed" filter/tab on `/notificacoes` so deferred items aren't invisible forever

**Defer (v2+):**
- Category×channel preference matrix — premature with a single delivery channel (in-app polling only); revisit only if email/push is ever added
- A brand-new `ProcessoEquipa` join table independent of the client's team — no evidence a processo's team ever needs to diverge from its client's team; reuse `ClienteAdvogado`/`ClienteAdministrativo` transitively via `Processo.clienteId` instead
- Extending NOTF-25's team fan-out to the Phase-88 daily-job categories (`PRAZO_*`, `EVENTO_*`, `HONORARIO_ATRASADO`) — flagged as an explicit roadmap decision, not silently included or silently dropped

### Architecture Approach

Every one of NOTF-24/25/26 is best understood as "what gets inserted at or near `NotificacaoService.criar()`," the single, deliberately-documented write choke point for the entire notification subsystem — not as three independent features. This centralization is what makes NOTF-24's mute check automatically apply to all 4 event triggers and the daily job with one guard clause, and it is exactly what NOTF-25's team-resolution helper (`resolverEquipaProcesso`) must also route through rather than being duplicated at 4-5 call sites.

**Major components:**
1. `NotificacaoService.criar(...)` — sole write path; hosts the new mute-check guard (NOTF-24) and is the natural home for `resolverEquipaProcesso` (NOTF-25) and `snooze()` (NOTF-26)
2. `NotificacaoPreferencia` (new entity) — `(tenant_id, user_id, categoria)` unique join table; presence of a row = muted, absence = default-on delivery, mirroring the `ClienteAdvogado`/`ClienteAdministrativo` join-table convention
3. Team resolution via `Processo.clienteId` → `ClienteAdvogadoRepository`/`ClienteAdministrativoRepository` — zero new tables; reuses the exact lookup already proven correct for cliente-linked document uploads
4. `Notificacao.snoozedUntil` (new nullable column) — a visibility toggle on the existing row, deliberately orthogonal to `lida` and to `AlertasDiariosJob`'s per-categoria dedup, so escalation and un-snoozing both work by construction with no job changes
5. Testcontainers `@DataJpaTest` slices — narrow, MinIO/security-bean-free repository/lock verification for the native-query and concurrency-lock risk areas, not full-context `@SpringBootTest`

### Critical Pitfalls

1. **Mute check placed anywhere except inside `criar()`** — the daily job calls `criar()` directly, bypassing all 4 trigger methods; a check added only to the trigger methods silently misses 5 of 9 categories. Fix: guard clause inside `criar()` itself, and audit all ~9 existing callers for how they handle the new "muted, no-op" return outcome.
2. **Team fan-out will trip `uk_notificacao_dedup` — a bug that already exists today, independent of NOTF-25** — Phase 87's "no dedup between primary recipient and ADMIN fan-out" comment predates Phase 88's unique constraint; any team member who is also ADMIN will now cause an uncaught `DataIntegrityViolationException` that 500s and rolls back an already-succeeded business action (document upload, parecer attribution). Must be fixed (merge recipient sets into one `LinkedHashSet` before looping) as a standalone discovered-gap item, and gets strictly worse as NOTF-25 widens the recipient pool.
3. **Snooze naively implemented as "hide/delete the row"** — `AlertasDiariosJob`'s idempotency check only knows "exists / doesn't exist" per `(tenant, destinatario, entidadeTipo, entidadeId, categoria)`; hiding the row makes the job recreate it the very next run. Fix: add `snoozedUntil` state to the same row and extend only the *unread-count/badge* queries with a time predicate — leave the dedup tuple and the job's creation logic untouched.
4. **`@SpringBootTest`/H2 will produce false confidence or hit the same `MINIO_ENDPOINT` wall already blocking live UAT** — use `@DataJpaTest` (excludes MinIO/security beans entirely) with a real `postgres:16-alpine` Testcontainer (not H2) specifically for the native query and the concurrency-lock test; H2's Postgres-compatibility mode has known gaps for native SQL and cannot reproduce true MVCC locking semantics.
5. **Multi-tenant leakage concentrated in 3 new surfaces** — the mute-preferences table, `Processo→Cliente` team resolution, and any new lookup inside `AlertasDiariosJob` (which runs with no security context and threads `tenantId` explicitly as a parameter by deliberate design). Every new query must filter by `tenant_id` explicitly, never by `user_id`/`cliente_id` alone, and any job-side helper must never call `getTenantId()`/`SecurityContextHolder`.

## Implications for Roadmap

Based on combined research, suggested phase structure groups by shared-file collision risk and dependency order, not just by feature name. Two tracks (infra/tooling and frontend-only agenda consolidation) have zero file overlap with the notification track and can run in parallel with it; the notification track itself is a hard sequential chain.

### Phase 1: SpotBugs/SAST Commit + Verification
**Rationale:** Already ~90% done, uncommitted, in the working tree (version bumps + exclusion file + real source fixes for EI_EXPOSE_REP/mass-assignment already applied). Zero dependency on any other phase; highest risk of being accidentally lost or redone from scratch if not landed first.
**Delivers:** Committed `backend/pom.xml` version bumps, `backend/spotbugs-exclude.xml`, and the already-applied `UserPrincipal`/`ConflictCheckResponse`/`WorkflowResponse`/`ResourceController` defensive-copy fixes; a clean `mvn spotbugs:check` run.
**Addresses:** SAST/JDK-23 tech-debt item from PROJECT.md scope.
**Avoids:** Pitfall 8 (uncommitted work treated as "still broken" and redone or lost).

### Phase 2: Backend Integration-Test Infrastructure (Testcontainers)
**Rationale:** Independent of the notification track (different files); unblocks closure of Phase 86/87's pending UAT gaps as a side effect. Should land before or alongside the notification track so NOTF-25's dedup-collision fix (Phase 4 below) and NOTF-26's job-interaction behavior can be verified with real Postgres semantics rather than asserted by inspection alone.
**Delivers:** `@DataJpaTest` + `@Testcontainers` + `@ServiceConnection` slice tests for `NotificacaoRepository.buscarPorFiltros` (native query) and the `ParecerVersao.numeroVersao` concurrency lock; a decision on whether CI (`deploy.yml`) is updated to actually run `mvn test`/`spotbugs:check`.
**Uses:** Testcontainers `postgresql` (1.20.4, inherited), `spring-boot-testcontainers`, `postgres:16-alpine` image (matches prod).
**Avoids:** Pitfalls 5, 6, 7 (env-var wall, H2 false confidence, CI never running tests).

### Phase 3: Agenda ↔ RiscoPrazoService Consolidation
**Rationale:** Frontend-mostly, fully independent of the notification track (Phase 85 explicitly scoped this out of the prior milestone). Lower complexity for Prazos (pure plumbing — `risco` already computed backend-side, just dropped in `agenda/page.tsx`'s mapping); real backend decision needed for Eventos (`GET /eventos` has no `risco` field at all, unlike `/eventos/upcoming`).
**Delivers:** `allUnifiedEvents` threads `risco` through instead of recomputing a date-blind `prioridade === "ALTA"` proxy; an explicit decision + implementation for whether `GET /eventos` gains a `risco` field or the frontend mirrors the threshold table with a parity test against `RiscoPrazoServiceTest`.
**Addresses:** "5th divergent implementation of prazo crítico" tech-debt item from PROJECT.md scope.
**Avoids:** Pitfall 9 (declaring victory on Prazos while silently leaving Eventos unaddressed).

### Phase 4: NOTF-25-adjacent Discovered-Gap Fix — Dedup Constraint vs. ADMIN Double-Delivery
**Rationale:** This is a **pre-existing, currently-live bug**, not new NOTF-25 scope — but NOTF-25 makes it strictly worse (more recipients per event → higher chance of an ADMIN collision) and NOTF-25 cannot be safely built on top of the current fan-out code without it. Sequencing this immediately before NOTF-25 (same phase or the phase directly preceding it) matches this project's established precedent of fixing cross-phase integration gaps immediately rather than deferring.
**Delivers:** Merged/deduped recipient sets in `notificarDocumentoNovo`/`notificarParecerAtribuido` before looping; a `DataIntegrityViolationException` backstop matching the pattern already used in `AlertasDiariosJob`; a test proving "team member who is also ADMIN" no longer 500s.
**Avoids:** Pitfall 2 (the single highest-severity finding in PITFALLS.md).

### Phase 5: NOTF-24 — Per-User Notification Category Preferences
**Rationale:** Must come first among the three notification features — its change to `criar()` is the smallest, most self-contained insertion (one guard clause), and doing it first means NOTF-25's team fan-out automatically inherits the mute gate for free rather than needing separate re-verification.
**Delivers:** New `NotificacaoPreferencia` entity/table, mute-check inside `criar()`, `GET/PUT/DELETE /notificacoes/preferencias/{categoria}` endpoints on `NotificacaoController`, a settings-page toggle list reusing the existing `NOTIFICACAO_CATEGORIA_OPTIONS` labels, at least one non-mutable critical category.
**Implements:** The `NotificacaoPreferencia` component from Architecture Approach above.
**Avoids:** Pitfall 1 (mute check bypassable if placed anywhere but `criar()`).

### Phase 6: NOTF-25 — Notify the Full Process Team
**Rationale:** Second in the notification chain, after NOTF-24 and after Phase 4's dedup fix. Reuses `ClienteAdvogado`/`ClienteAdministrativo` transitively via `Processo.clienteId` — zero new tables, mirrors the exact pattern already sitting 20 lines away in `ResourceController` for cliente-linked documents.
**Delivers:** `resolverEquipaProcesso` helper in `NotificacaoService`; team fan-out extended to `notificarFaseEntrada`, `notificarDocumentoNovo` (processo branch), `notificarProcessoAtribuido` (with 2nd-person copy preserved for the responsável, 3rd-person for the rest of the team); `notificarParecerAtribuido` explicitly left as individual-assignment (flagged for requirements confirmation, not silently folded in); an explicit roadmap decision on whether Phase-88 daily-job categories get the same expansion.
**Addresses:** NOTF-25 from PROJECT.md target features.
**Avoids:** Pitfall 3 (inconsistent team-resolution call sites) and Pitfall 10 (tenant-leakage via unverified `Cliente` ownership at the join step).

### Phase 7: NOTF-26 — Snooze a Deadline Reminder
**Rationale:** Last in the notification chain — almost entirely additive (new column, new endpoint, new query predicates), least likely to conflict with the other two features' core logic changes.
**Delivers:** `Notificacao.snoozedUntil` (nullable, manual migration script), `NotificacaoService.snooze(...)` mirroring `marcarLida`'s shape, `PATCH /notificacoes/{id}/snooze` endpoint, updated unread-count/list/mark-all-read predicates (leaving the full-history `/notificacoes` view unfiltered so snoozed items remain browsable), fixed-duration presets (1/3/7 dias) in the UI via existing `Popover`+`RadioGroup` primitives, a product decision on whether snooze is disallowed on already-overdue `PRAZO_VENCIDO` items.
**Addresses:** NOTF-26 from PROJECT.md target features.
**Avoids:** Pitfall 4 (snooze resurrected by the job's idempotency check the next morning).

### Phase 8: Cross-Cutting Audit + Minor Debt Closure + Remaining UAT
**Rationale:** Best done last so the audit sees the milestone's final integration shape (matches this project's established v2.7/v2.9/v2.10 retrospective pattern of auditing at milestone close, not mid-stream).
**Delivers:** Fresh gap audit across all NOTF-24/25/26 surfaces for tenant-isolation checklist items; minor debt closure (enum label translations, NIF validation tests); resolution/workaround for the `MINIO_ENDPOINT` blocker; closure of the remaining live/manual UAT items (75, 76, 79, 81, 82, 84, 85, 89) now that Testcontainers has already closed the test-infrastructure-shaped gaps for 86/87.

### Phase Ordering Rationale

- Track 1 (SpotBugs, Testcontainers) and Track 2 (Agenda consolidation) have zero file overlap with the notification track or each other — safe to run in parallel with, or before, Track 3.
- Track 3 (NOTF-24/25/26) is a **hard sequential constraint**, not a suggestion: all three mechanically collide on `NotificacaoService.java` and its ~20-call-site test file, regardless of their logical independence.
- The dedup-collision fix (Phase 4) must land before NOTF-25 specifically, because NOTF-25 is what turns a latent, low-probability bug into a near-certain one as team sizes grow.
- The final audit (Phase 8) must come after everything else, per this project's own established retrospective pattern — an audit run mid-milestone would need to be redone once Track 3 lands.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 4/6 (dedup fix + NOTF-25):** the "open question" of whether Phase-88 daily-job categories also get team expansion is explicitly unresolved by research and needs a requirements-level decision, not further research — flag for `/gsd:plan-phase` to surface as a decision point.
- **Phase 6 (NOTF-25):** whether "team" means the literal wording "equipa do processo" (potentially a new `t_processo_equipe` table) or the cheaper "client's team, transitively" reading — research recommends the cheaper reading but flags it as a product decision, not a closed question.
- **Phase 7 (NOTF-26):** snooze-duration caps relative to `Prazo.dataLimite` and whether `PRAZO_VENCIDO` is snoozable at all are explicit product-safety decisions research could not resolve unilaterally.

Phases with standard patterns (skip research-phase):
- **Phase 1 (SpotBugs):** verification + commit of already-completed work, not new research.
- **Phase 2 (Testcontainers):** well-documented official Spring Boot pattern (`@ServiceConnection`, `@DataJpaTest`), directly corroborated against docs.spring.io.
- **Phase 3 (Agenda consolidation, Prazos half):** pure plumbing, data already computed backend-side.
- **Phase 5 (NOTF-24):** established join-table + choke-point pattern already used repeatedly in this codebase.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Versions verified directly against the fetched `spring-boot-dependencies-3.4.1.pom` from Maven Central and this repo's own uncommitted `pom.xml`/`spotbugs-exclude.xml` state — not training-data guesses |
| Features | MEDIUM-HIGH | Codebase-grounded dependency claims are HIGH confidence (read directly from shipped v2.10 code); cross-product UX pattern claims (Clio, Jira, Todoist, IntelligentContract) are MEDIUM confidence, each cross-verified across 2+ independent sources but with no Context7 coverage for this niche domain |
| Architecture | HIGH for file/line-level facts (read directly from the repo); MEDIUM for product-shape recommendations that depend on an unstated business decision (explicitly flagged, e.g. NOTF-25's team-scope reading) |
| Pitfalls | HIGH | All findings grounded in direct reads of current repository code plus the uncommitted working-tree diff; not third-party ecosystem research |

**Overall confidence:** HIGH

### Gaps to Address

- Whether NOTF-25's "equipa do processo" means the client's transitive team (research's recommended, cheaper reading) or a genuinely new per-processo team concept — must be confirmed during requirements/phase planning, not assumed.
- Whether Phase-88 daily-job categories (`PRAZO_*`, `EVENTO_*`, `HONORARIO_ATRASADO`) receive the same team-fan-out expansion as the 4 event-triggered categories in this milestone, or are deliberately deferred — currently undecided by omission.
- Whether CI (`deploy.yml`) gets a `mvn test`/`spotbugs:check` step added in this milestone, or whether tests remain a deliberate local-only/manual gate — needs an explicit recorded decision either way (Pitfall 7).
- Snooze safety bounds (max duration, interaction with already-overdue `PRAZO_VENCIDO`) — a product/UX decision research flagged but did not resolve.
- Whether a full HTTP-level RBAC/tenant round-trip test is ever needed for these features (would require a dedicated stub property source to avoid the `MINIO_ENDPOINT` wall) — not needed for this milestone's two named risk areas, but worth deciding explicitly if a future phase wants broader coverage.

## Sources

### Primary (HIGH confidence)
- `spring-boot-dependencies-3.4.1.pom` (fetched directly from Maven Central) — verified `testcontainers.version=1.20.4`, `junit-jupiter.version=5.11.4`, `mockito.version=5.14.2`, `assertj.version=3.26.3`
- `https://docs.spring.io/spring-boot/3.4/reference/testing/testcontainers.html` — official `@ServiceConnection`/`spring-boot-testcontainers` pattern
- Direct repository inspection — `NotificacaoService.java`, `Notificacao.java`, `AlertasDiariosJob.java`, `RiscoPrazoService.java`, `ResourceController.java`, `NotificacaoController.java`, `Processo.java`, `ClienteAdvogado.java`/`ClienteAdministrativo.java`, `ParecerSolicitacao.java`/`ParecerVersao.java`, `backend/pom.xml` (+ uncommitted diff), `backend/spotbugs-exclude.xml`, `web/package.json`, `web/src/components/ui/*`, `agenda/page.tsx`, `docker-compose.yml`, `.github/workflows/deploy.yml`, `.planning/PROJECT.md`, `.planning/STATE.md`

### Secondary (MEDIUM confidence)
- `https://github.com/spring-projects/spring-boot/issues/47639` — Testcontainers 2.0.x rename breaks `@ServiceConnection`
- Context7 `/testcontainers/testcontainers-java` — corroborates the 2.0.x artifact rename
- Clio Help Center, Atlassian (Jira watchers), Asana Help Center, SuprSend, Smashing Magazine, IntelligentContract, Todoist, Any.do — cross-product notification-preference/team-targeting/snooze UX patterns, each cross-verified across 2+ sources

### Tertiary (LOW confidence)
- None identified — all claims in this milestone's research were either codebase-verified (HIGH) or cross-checked across multiple independent secondary sources (MEDIUM)

---
*Research completed: 2026-07-12*
*Ready for roadmap: yes*
