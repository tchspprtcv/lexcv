# Domain Pitfalls — Milestone v2.11 (Auditoria Técnica e Notificações Avançadas)

**Domain:** Retrofitting notification fan-out/mute/snooze onto an existing persisted `Notificacao` subsystem; retrofitting Testcontainers/H2 and fixing SpotBugs on a mature Spring Boot 3.4.1/Java 23 multi-tenant backend; consolidating a duplicated frontend risk calculation; broad undocumented-tech-debt audit.
**Researched:** 2026-07-12
**Confidence:** HIGH (all findings below are grounded in direct reads of this repository's current code — `NotificacaoService.java`, `Notificacao.java`, `AlertasDiariosJob.java`, `RiscoPrazoService.java`, `ResourceController.java`, `NotificacaoController.java`, `agenda/page.tsx`, `web/src/lib/prazos.ts`, `pom.xml`, `backend/Dockerfile`, `.github/workflows/deploy.yml`, plus the uncommitted working-tree diff for the SpotBugs fix already in progress). Not third-party ecosystem research — this file is a code-grounded audit, so confidence is stated per-finding based on what was directly verified vs. inferred.

**Supersedes:** the previous `PITFALLS.md` in this directory was written for milestone v2.10 (Notificações e Alertas, 2026-07-08) and is no longer current — its findings (job tenant-resolution, per-recipient scoping, dedup idempotency design) are now implemented in the codebase and referenced here as established precedent rather than open risks.

## Critical Pitfalls

### Pitfall 1: Mute check added anywhere except `NotificacaoService.criar()` will be bypassable

**What goes wrong:**
NOTF-24 (per-category mute) gets implemented as a guard inside one or two of the *trigger* methods (`notificarFaseEntrada`, `notificarProcessoAtribuido`, `notificarDocumentoNovo`, `notificarParecerAtribuido`) instead of in the shared `criar()` method underneath all of them — plus `AlertasDiariosJob.notificar()`, which calls `notificacaoService.criar(...)` directly, bypassing all four trigger methods entirely. Any call site that isn't updated keeps notifying muted users.

**Why it happens:**
There are currently **5 independent call paths** into notification creation: `notificarFaseEntrada`, `notificarProcessoAtribuido`, `notificarDocumentoNovo`, `notificarParecerAtribuido` (all in `NotificacaoService`), plus `AlertasDiariosJob.notificar()` (which calls `criar()` directly, not through any of the four). A developer implementing NOTF-24 who greps for "where notifications get created" and patches the four public trigger methods will still miss the daily job's 9 categories (`PRAZO_PROXIMO`, `PRAZO_VENCIDO`, `EVENTO_PROXIMO`, `EVENTO_VENCIDO`, `HONORARIO_ATRASADO`), because that job talks straight to `criar()`.

**How to avoid:**
Put the mute check inside `criar(...)` itself, at the very top, right after the existing tenant/destinatario validation and before `requireNonBlank(...)`. The method's own comment already declares it "ÚNICO ponto de escrita de CRIAÇÃO de Notificacao em todo o código" — this is the one place a check is structurally guaranteed to run for every current and future trigger, including the daily job. Do **not** duplicate the check in `notificarAdmins`, `notificarFaseEntrada`, etc. — that reintroduces the N-call-site risk this pitfall describes.

This is a breaking contract change: `criar()` currently always returns a persisted `Notificacao` (or throws `IllegalArgumentException`). Muting introduces a third outcome — "validation passed, but nothing was persisted." Every existing caller that assumes a non-null return (there are ~9 call sites across the 5 methods above) needs to be re-reviewed for what it does with that return value today, and updated to treat "muted, no-op" as neither a success-with-object nor the existing `IllegalArgumentException`-driven "orphaned destinatario" warning path — conflating the two log paths would make real orphaned-user bugs indistinguishable from ordinary muting in the logs.

**Warning signs:**
- Any implementation PR that touches only `Notificacao*Service` trigger methods and never touches `AlertasDiariosJob.java`.
- `criar()`'s signature/return type unchanged while mute logic lives elsewhere.
- Existing per-recipient `try/catch (IllegalArgumentException)` blocks (Phase 87 CR-01/CR-02 pattern) not re-examined for the new no-op case.

**Phase to address:** NOTF-24 phase (mute preferences), as the very first implementation task before any UI is built — this is a backend-core decision, not a UI decision.

---

### Pitfall 2: Team fan-out will trip the `uk_notificacao_dedup` constraint via an uncaught `DataIntegrityViolationException` — and this bug already exists today, independent of NOTF-25

**What goes wrong:**
`uk_notificacao_dedup` is a unique index on `(tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)` (`Notificacao.java`, added by the Phase 88 migration `88-add-notificacao-dedup-unique-constraint.sql`, *after* Phase 87 wrote `notificarDocumentoNovo`/`notificarParecerAtribuido`). Both of those Phase 87 methods have a documented, deliberate behavior: "se um membro da equipa também for ADMIN recebe 2 linhas" (if a team member is also ADMIN, they get 2 rows) — i.e., the primary fan-out loop and the `notificarAdmins` fan-out loop are allowed to both call `criar()` for the *same person* with the *same* `(tenant, entidadeTipo, entidadeId, categoria)` tuple. Since the constraint didn't exist when that comment was written, this was previously harmless. **It is not harmless anymore.** The second `save()` for that overlapping person will throw `DataIntegrityViolationException` — and neither method's per-recipient `try/catch` (`catch (IllegalArgumentException ex)`) catches it. The exception propagates out of an already-committed-business-action `@Transactional` controller method (document upload, parecer attribution) and rolls back the whole request with a 500, exactly the class of bug Phase 87's three code-review rounds (CR-01/CR-02) fixed for *orphaned users* but never revisited for *constraint violations*.

Expanding NOTF-25 to full teams makes this worse, not better: more recipients per event means a higher chance that at least one team member also holds the ADMIN role.

**Why it happens:**
The dedup constraint (Phase 88) and the "no dedup between primary and ADMIN fan-out" comment (Phase 87) were written in different phases, by different reasoning, and nobody revisited the Phase 87 assumption after the Phase 88 constraint landed. This is a cross-phase integration gap of exactly the kind past milestone audits in this project have found repeatedly (see `PROJECT.md` Key Decisions: the `pesquisar()` routing bug, the `/honorarios`/`/documentos` `processo_id` filter bugs — each "looked correct in isolation").

**How to avoid:**
1. Before implementing NOTF-25, write a test that reproduces this today (a team member who is also ADMIN, triggering `notificarDocumentoNovo` or `notificarParecerAtribuido`) and confirm it currently 500s or violates the constraint — this is a **pre-existing latent bug, not a NOTF-25 side effect**, and should be logged/fixed as its own discovered gap in the audit, independent of whether NOTF-25 ships.
2. Fix pattern: merge the primary-recipient set and the ADMIN set into one `LinkedHashSet<UUID>` *before* looping (mirroring how `notificarDocumentoNovo` already dedupes its own `destinatarios` collection), so each person is only ever passed to `criar()` once per event. This also finally makes the "2 linhas" comment obsolete — update/remove it once fixed.
3. As defense-in-depth (matching the precedent already set for `AlertasDiariosJob.notificar()`, which *does* catch `DataIntegrityViolationException` as a backstop), add the same catch to the trigger methods in `NotificacaoService` — but treat this as a backstop, not the primary fix; pre-deduping is the primary fix.
4. Whatever "team" resolution NOTF-25 introduces (see Pitfall 3) must feed into the same merged/deduped set, not a second independent loop.

**Warning signs:**
- `notificarDocumentoNovo`/`notificarParecerAtribuido` still contain the "sem dedup entre destinatário primário e fan-out ADMIN" comment after NOTF-25 ships.
- Team-resolution code adds a *third* loop (team, then admins, then existing primary) instead of merging into one set.
- No test exercises "team member who is also ADMIN."

**Phase to address:** Flag as a standalone discovered-gap fix (audit-discovery phase, fixable same-session per this project's established precedent of fixing cross-phase gaps immediately rather than deferring) — then NOTF-25 phase must build on top of the fixed/deduped pattern, not the current one.

---

### Pitfall 3: "Notify the full process team" has no existing data model to point at — and the one call site that already half-solved this is inconsistent with its sibling

**What goes wrong:**
`Processo` has exactly one recipient-shaped field: `responsavelId` (single `UUID`). There is no `ProcessoAdvogado`/`ProcessoEquipa` join table. The only multi-person "team" concept that exists anywhere in this codebase is `Cliente`'s `ClienteAdvogado`/`ClienteAdministrativo` join tables. `Processo` does have a `clienteId` FK, so the natural (and already-precedented) design is: a process's "team" = the assigned advogados + administrativos of its owning `Cliente`, resolved the same way document-upload notifications already do it for cliente-linked documents.

Except: **that resolution is already inconsistent today.** In `ResourceController`, when a document is uploaded against a **Processo**, the notification recipients are computed as `resp != null ? List.of(resp) : List.of()` — only the single `responsavelId`. A few lines below, when a document is uploaded against a **Cliente** directly, the code pulls the full team via `clienteAdvogadoRepository.findByClienteIdAndTenantId(...)` + `clienteAdministrativoRepository.findByClienteIdAndTenantId(...)`. Same `notificarDocumentoNovo(tenantId, id, Collection<UUID> destinatarios, ...)` method, same fan-out mechanism — one call site uses it correctly for "team," the other doesn't.

**Why it happens:**
Nobody had a reason to close this gap before now — Phase 87's brief was "notify the responsible party," not "notify the team," so `List.of(resp)` was a correct, minimal implementation for its own scope at the time. NOTF-25 is exactly the requirement that turns this from "fine for its scope" into "inconsistent with the sibling code path three lines away."

**How to avoid:**
- Decide NOTF-25's team definition explicitly during phase planning, not implementation: "team of a Processo" = `responsavelId` (kept, for the primary/2nd-person-message slot) **plus** the owning `Cliente`'s `ClienteAdvogado` + `ClienteAdministrativo` members (for the broadened fan-out), reusing the exact repository calls already proven correct in the Cliente-linked document path.
- Fix the Processo-linked `notificarDocumentoNovo` call site to resolve the same way as its Cliente-linked sibling, as part of this phase (not a separate future cleanup) — it is the most direct, ready-made template for every other NOTF-25 trigger (fase entrada, processo atribuído, parecer atribuído don't currently have a "team" concept at all and need the same resolution added).
- Extend the *same* team-resolution helper to `notificarFaseEntrada`/`notificarProcessoAtribuido`/`AlertasDiariosJob`'s prazo/evento/honorário processing — write it once (e.g., a `resolverEquipaProcesso(UUID processoId, UUID tenantId)` helper in `NotificacaoService` or a small dedicated resolver), not 5 times with independently-drifting logic — mirroring the exact "criar() is the único ponto de escrita" discipline this codebase already follows for writes.

**Warning signs:**
- A grep for `ClienteAdvogado\|ClienteAdministrativo` after NOTF-25 ships still shows only the pre-existing Cliente-document call site, not the new Processo-triggered ones.
- Team resolution logic duplicated inline at each of the 4-5 trigger call sites instead of one shared helper.
- No decision recorded (Key Decisions / CONTEXT.md) about whether `responsavelId` keeps a distinguished "2nd person" message slot once teams exist, or becomes just another team member.

**Phase to address:** NOTF-25 phase. The Processo-linked `notificarDocumentoNovo` inconsistency should be called out explicitly as an in-scope fix for this phase (it's the same requirement, discovered mid-way rather than up front).

---

### Pitfall 4: Snooze has nowhere to "remember" a dismissed occurrence — the daily job's idempotency key will resurrect it the next morning

**What goes wrong:**
`AlertasDiariosJob.notificar()` only ever checks "does a row already exist for `(tenant, destinatario, entidadeTipo, entidadeId, categoria)`?" before creating one — that's the entire idempotency model (edge-triggered by `categoria`, per the class-level Javadoc and `uk_notificacao_dedup`). It has zero concept of "the user saw this and asked to be reminded later instead." If NOTF-26's snooze is implemented as simply deleting or hiding the notification row, the next day's job run sees "no existing row for this tuple" (because the row is gone/hidden) and creates a brand new one — the exact bug this feature is supposed to prevent, on day one of use.

**Why it happens:**
The existing idempotency design deliberately has no state richer than "exists / doesn't exist" — that was the right, minimal design for "never duplicate," but it is the *wrong* foundation for "remember a user's per-occurrence dismissal," which is a fundamentally different kind of memory (per-user intent, not per-entity fact).

**How to avoid:**
Do not try to make "snoozed" a variant of "lida" (read) — they answer different questions ("did the user see it" vs. "should this specific occurrence stop nagging until date X") and conflating them will corrupt the unread-count badge and the `/notificacoes` filters that already exist. Instead:
- Add explicit state to `Notificacao` (or a new lightweight join) capturing *what* is snoozed and *until when* — at minimum `snoozedUntil: LocalDateTime` (nullable) on the notification row, or a separate `(tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria, snoozed_until)` table keyed identically to the existing dedup tuple, so the job's existence-check can be extended to "exists AND not currently snoozed" without restructuring the tuple.
- The job's `notificar()` existence-check must become "skip if a non-snoozed row already exists **or** an active snooze covers this tuple" — otherwise snoozing does nothing, and un-snoozing does nothing either.
- Decide explicitly what happens when the snooze expires while the underlying condition still holds (e.g., a prazo still overdue): does the job need to actively re-surface it that morning, or does it wait for the next natural re-run? Given the job already runs daily at 06:00, "wait for the next run" is simplest and consistent — but write this down as a decision, don't let it be discovered as a surprise during UAT.
- This state needs the same 4-layer failure isolation (`Throwable`-catching, per-tenant/per-category/per-entity try/catch) already established in `AlertasDiariosJob` — a snooze-check that throws must not abort the whole day's run for other tenants/categories.

**Warning signs:**
- Snooze implemented purely as a frontend "hide this row" action with no backend column/table.
- Snooze implemented by setting `lida = true` (this breaks "unread count" semantics and is indistinguishable from actually reading it).
- No test simulates "job runs day 1 (creates), user snoozes, job runs day 2 (must NOT recreate), snooze expires, job runs day 3 (must decide explicitly whether to recreate)."

**Phase to address:** NOTF-26 phase. This is the single highest-complexity item of the three NOTF features because it requires new persisted state, not just new fan-out logic — plan it with its own explicit CONTEXT.md decision on the snoozed-until semantics before writing code.

---

### Pitfall 5: `@SpringBootTest`-based integration tests will hit the exact same required-env-var wall that already blocks live UAT today

**What goes wrong:**
`application.yml` has **zero defaults** — every property (DB, JWT secret, MinIO/S3 config, etc.) is a required env var, imported from an optional `.env` file (`spring.config.import: optional:file:.env[.properties]`). This is already documented as the cause of a real, current blocker: `MinioConfig.s3Client()` fails at context startup before any controller becomes reachable, which is why 4 of 5 Phase 87/89 UAT scenarios in this exact project remain "human_needed" today (`PROJECT.md` Context section). A naive integration-test approach that spins up the **full** Spring context (`@SpringBootTest`) to test, say, `NotificacaoController.listar`'s native query or `AlertasDiariosJob`'s prazo processing, will fail to start the context for the same reason — not because Testcontainers/H2 doesn't work, but because unrelated beans (MinIO client, JWT provider needing a real secret, etc.) fail to construct first.

**Why it happens:**
`@SpringBootTest` boots the *entire* application context by default. This project's context has hard dependencies unrelated to the two things this milestone actually needs to verify (STATE.md names them precisely: the native `nativeQuery=true` + `Pageable` combination in `NotificacaoRepository.buscarPorFiltros`, and the concurrency lock on `Processo`/`Parecer`'s `numeroVersao`). Reaching for the broadest test annotation is the natural first instinct and the wrong one here.

**How to avoid:**
- Prefer narrow slice tests: `@DataJpaTest` (auto-configures an embedded/Testcontainers JPA layer only, excludes web/security/MinIO/JWT beans) for the native query and any repository-level concurrency verification, wired to a real PostgreSQL Testcontainer via `@ServiceConnection` (Spring Boot 3.1+) rather than H2 — see Pitfall 6 for why H2 specifically is risky for the native query in question.
- If a genuine full-stack HTTP round-trip is ever needed (e.g., a future phase wants to verify RBAC + tenant scoping end-to-end), build a **dedicated minimal test property source** (`application-test.yml` under `src/test/resources`, or `@TestPropertySource`) that stubs every required-but-irrelevant property (dummy JWT secret, dummy MinIO endpoint/bucket) rather than trying to satisfy them from a real `.env`. Do not assume CI or local dev machines will have a working MinIO/Postgres available for tests — that assumption is precisely what has already blocked live UAT.
- Scope this milestone's testing-infra phase to the two named risk areas only (native query, concurrency lock) rather than attempting broad controller-level integration coverage — matches the milestone's own stated priority and avoids scope creep into "test everything," which risks never finishing.

**Warning signs:**
- Any new test class annotated `@SpringBootTest` without `webEnvironment = NONE` or explicit `@MockBean`/exclusion of `MinioConfig`/security auto-config.
- Test suite requires a real `.env` file or real MinIO instance to pass.
- CI run fails with "required property X not set" for a property (e.g., `MINIO_ENDPOINT`, JWT secret) that has nothing to do with what the test is actually verifying.

**Phase to address:** Testing-infrastructure phase, as the very first architectural decision (slice tests vs. full context) — before any Testcontainers dependency is even added to `pom.xml`.

---

### Pitfall 6: H2 will give false confidence on the one query that most needs real-Postgres verification

**What goes wrong:**
The specific risk area STATE.md names is `NotificacaoRepository.buscarPorFiltros` — "the first-ever `nativeQuery=true`+`Pageable` combination in this codebase." Native queries are, by definition, written in the target database's SQL dialect (PostgreSQL). H2's PostgreSQL-compatibility mode covers common syntax but has known gaps around Postgres-specific functions, `::` casts, `ILIKE`, JSONB operators, and native pagination semantics combined with native queries (Spring Data's native-query + `Pageable` support has historically had rough edges that behave differently across H2 vs. real Postgres, e.g., around how the count query is derived). Testing this specific query against H2 can pass while the identical query fails or behaves subtly differently (wrong pagination totals, wrong ordering, silent type coercion) against real PostgreSQL in production — the exact "looks done but isn't" failure mode this milestone exists to close.

**Why it happens:**
H2 is faster to set up and requires no Docker, which is attractive when retrofitting test infra into a project that has never had any — but "fast and easy" is precisely why it produces false confidence for dialect-sensitive code.

**How to avoid:**
For this specific query (and any other native query added going forward), use a real PostgreSQL Testcontainer, not H2 — Testcontainers' overhead (container startup) is the right tradeoff specifically because the milestone's own stated reason for wanting tests is to verify a native-SQL/Postgres-specific code path. If H2 is used elsewhere in this retrofit for speed (e.g., simple `@DataJpaTest`s over plain JPQL/derived-query repositories with no native SQL), that's a reasonable, lower-risk choice — but draw the line explicitly at "any repository method with `nativeQuery=true`" and route those exclusively through the Postgres Testcontainer.

**Warning signs:**
- `buscarPorFiltros` test passes against H2 and is treated as "verified" without ever running against Postgres.
- Test suite uses H2 exclusively "for speed" with no Testcontainers dependency at all.

**Phase to address:** Testing-infrastructure phase — this is a tooling *choice*, not incidental detail; record it as an explicit decision (H2 acceptable for plain JPQL, Testcontainers-Postgres mandatory for native queries) so future phases don't quietly regress to H2-everywhere for convenience.

---

### Pitfall 7: New Testcontainers-based tests (and the already-existing 3 unit tests) provide zero regression protection unless CI is also changed — this project's pipeline currently never runs `mvn test`

**What goes wrong:**
`backend/Dockerfile` builds with `mvn -DskipTests package` explicitly. `.github/workflows/deploy.yml` (the only CI workflow in this repo) never invokes `mvn test`, `mvn verify`, or `mvn spotbugs:check` at any point — it goes straight to `docker/build-push-action`, which itself uses the Dockerfile that skips tests. This means the 3 unit tests that already exist in this codebase (`RiscoPrazoServiceTest`, `NotificacaoServiceTest`, `AlertasDiariosJobTest` — all pure JUnit 5/Mockito, no Spring context, no DB) **have never once run in CI**, and neither has SpotBugs. Adding Testcontainers-based integration tests without also adding a CI step that runs them produces the same outcome: tests that can be run manually, pass, and then silently rot the next time someone changes the code they cover — exactly the fate that already befell SpotBugs (STATE.md: "discovered during Phase 87, unrelated background task" that it was broken against JDK 23, implying nobody had run it in a long time).

**Why it happens:**
The CI workflow was designed purely as a build-and-deploy pipeline (push to `master` → build images → push to GHCR → deploy), not a quality gate. Retrofitting test/SAST infra without also touching CI treats "the tests exist" as equivalent to "the tests protect anything," which is false in this repository specifically.

**How to avoid:**
Explicitly decide, as part of this milestone, whether to add a `test`/`verify` job to `deploy.yml` (running before the Docker build stages, with Docker daemon access available natively on `ubuntu-latest` runners for Testcontainers) or to consciously leave tests as a local-only/manual gate and document that decision (e.g., in Key Decisions) rather than let it happen by omission again. If a CI step is added, it should run both `mvn test` (or `mvn verify` once Testcontainers ITs exist) **and** `mvn spotbugs:check`, so the SpotBugs fix from this same milestone doesn't immediately start rotting again the moment it's merged.

**Warning signs:**
- Milestone closes with new tests/SpotBugs config in the repo but `deploy.yml` unchanged.
- No record of an explicit choice ("tests are local-only by design" vs. "tests gate CI") in Key Decisions.

**Phase to address:** Both the testing-infrastructure phase and the SpotBugs-fix phase should each explicitly resolve this for their own tooling — ideally in the same CI-wiring change, since both problems have the identical root cause and the identical fix location.

---

### Pitfall 8: The SpotBugs/JDK-23 fix is already ~90% done, uncommitted, in the current working tree — treating this as "broken, start from scratch" risks losing or duplicating real work

**What goes wrong:**
`git status` at the start of this milestone already shows `backend/pom.xml` modified (spotbugs-maven-plugin bumped `4.8.3.1` → `4.10.2.0`, findsecbugs-plugin `1.13.0` → `1.14.0`, `excludeFilterFile` wired in), a new untracked `backend/spotbugs-exclude.xml` with real, individually-reviewed suppressions (dated "2026-07, after upgrading... for Java 23 support" in its own header comment), and real source fixes already applied and uncommitted: `UserPrincipal.getAuthorities()` now returns `Collections.unmodifiableCollection(...)` instead of the raw mutable set (EI_EXPOSE_REP), `ConflictCheckResponse`/`WorkflowResponse` records now defensively copy their list fields in compact constructors (same bug class), and 9 `ResourceController` create-endpoints (`createCliente`, `createProcesso`, `createProcessoIntake`, `createParte`, `createMovimentacao`, `createFacto`, `createEvento`, `createHonorario`, `createPagamento`) now call `setId(null)` before their first `save()` to close a genuine mass-assignment/IDOR-adjacent gap (a crafted client-supplied id would previously route through Hibernate's `merge()` instead of `persist()`, silently overwriting an existing, possibly cross-tenant, row).

If a phase in this milestone starts "fixing SpotBugs" assuming a clean slate, it risks (a) redoing analysis that's already been done, (b) accidentally discarding this uncommitted work via a careless `git checkout`/`git stash drop`/branch switch, or (c) not noticing this work exists at all and reporting a stale "still broken" status in the audit.

**Why it happens:**
This looks like leftover work from a prior session that was never committed. Uncommitted state is invisible to anyone who only reads `PROJECT.md`/`STATE.md`, both of which still describe SpotBugs as "broken"/"out of scope" from the v2.10 close.

**How to avoid:**
Before doing any SpotBugs work in this milestone: run `git status`/`git diff` on `backend/pom.xml` and `backend/spotbugs-exclude.xml` first. If this work is still present, verify it (`mvn spotbugs:check` should now succeed cleanly), read through the suppression rationale already written in `spotbugs-exclude.xml` to confirm it's still accurate, and commit it as the foundation — then look for any *remaining* findings or endpoints not yet covered (e.g., double-check `updateParte`, `updateHonorario`, `updateMovimentacao`, and any other update-style endpoints for the same client-suppliable-id class of bug, since the existing fix pass only lists 9 create endpoints and a handful of update endpoints already deemed safe — confirm that list is actually exhaustive rather than assuming it).

**Warning signs:**
- Milestone plan for the SpotBugs phase describes starting a version bump / dependency investigation that's already sitting in the working tree.
- `git log` for `backend/pom.xml`/`backend/spotbugs-exclude.xml` shows no commit despite the audit claiming to have "fixed" SpotBugs.

**Phase to address:** SpotBugs-fix phase — first task should be "inventory and commit existing uncommitted work," not "investigate SpotBugs JDK 23 compatibility" (that investigation already happened).

---

### Pitfall 9: Consolidating `agenda/page.tsx` isn't "write a 5th formula" — it's "thread an already-correct field through," except for Eventos, where that field doesn't exist on the endpoint agenda actually calls

**What goes wrong:**
The backend already computes and returns a `risco` field (`"ok" | "proximo" | "vencido"`, via `RiscoPrazoService`) on `Prazo` objects returned by the endpoint(s) `useAllPrazos()` consumes — this is confirmed by the frontend's own `PrazoRisco` type (`web/src/types/processos.ts`, non-optional `risco` field) and the existing shared mapping utility `web/src/lib/prazos.ts` (`prazosRiscoToVariant`/`prazosRiscoToLabel`), already correctly used by the processos list/detail pages. `agenda/page.tsx`'s `allUnifiedEvents` transform builds its own `pzs` array from `prazos.data` but never copies `p.risco` onto the unified objects, and its "urgentes" week-stat instead recomputes a *different*, date-blind signal: `active.filter((e) => e.prioridade === "ALTA").length` — priority alone, ignoring how close the deadline actually is, and never distinguishing "próximo" from "vencido" at all. Meanwhile `getCategoria()`'s red "PRAZO FATAL" styling is driven purely by matching `titulo`/`tipo` strings, not by actual risk — a not-yet-due low-priority prazo gets the same red treatment as an overdue one.

For **Eventos**, the situation is different and harder: the general `GET /eventos` list endpoint that `useEventos({})` actually calls does **not** return a `risco` field at all today — only the separate, legacy `GET /eventos/upcoming` endpoint does (built for the old v2.1 bell, already superseded per Phase 89's decision to replace it entirely). So full consolidation for events specifically requires a real decision, not just a frontend threading fix: either (a) add a `risco` field to the general `GET /eventos` response (broader blast radius — this endpoint also feeds the drag/drop calendar and mobile event cards, so any shape change needs those consumers re-verified), or (b) compute event risk client-side using a *shared, tested* constant/threshold table that is kept in lockstep with `RiscoPrazoService`'s Java thresholds (7 days for `ALTA`, 3 days otherwise) — which is still "a second implementation," just a deliberately mirrored and tested one, not an independent invention.

**Why it happens:**
Phase 85 explicitly scoped `agenda/page.tsx` out (`85-CONTEXT.md`, referenced in `PROJECT.md`) because the consolidation work belonged to a different milestone. Nobody has since checked *how much* of the divergence is "missing plumbing" (Prazos: trivial, data's already there) vs. "missing backend support" (Eventos: real gap).

**How to avoid:**
Split this phase's work explicitly into the two different problems: (1) for Prazos, thread the existing `p.risco` field through `allUnifiedEvents` and replace the `prioridade === "ALTA"` stat and the string-matched styling with `prazosRiscoToVariant(risco)`/direct `risco` comparisons — this is a pure frontend change, no backend involved, low risk; (2) for Eventos, make an explicit choice (extend `GET /eventos`'s response shape vs. mirror the threshold table client-side with a parity test against `RiscoPrazoServiceTest`'s cases) and record it as a Key Decision before writing code, since the two options have very different blast radii.

**Warning signs:**
- The fix touches only `agenda/page.tsx` and claims full consolidation without checking whether `GET /eventos` was also changed.
- A new TypeScript risk-threshold function appears in the frontend with no test tying its output to `RiscoPrazoServiceTest`'s known cases (7d/3d thresholds), reintroducing exactly the "5th divergent implementation" problem this phase exists to remove.

**Phase to address:** Agenda/RiscoPrazoService-consolidation phase.

---

### Pitfall 10: Multi-tenant leakage risk is concentrated in three specific new surfaces this milestone introduces — mute preferences, team resolution, and the background job's snooze/mute interaction

**What goes wrong:**
This codebase's established pattern for tenant isolation is: every entity carries `tenant_id` directly, OR (for lean child entities like `Decisao`/`Facto`/`Testemunha`/`Parte`) isolation is enforced *transitively* by loading and checking the parent (`Processo`) — and every write endpoint for those child entities explicitly re-verifies **both** tenant **and** parent-entity ownership (the "double-check" pattern `PROJECT.md` documents repeatedly, including a case where the *simpler* single-check `Parte` pattern was deliberately rejected in favor of the double-check because the entity IDs are guessable sequential integers). Three new v2.11 surfaces are exactly the kind of thing that historically gets under-scoped by pattern-matching the wrong precedent:

1. **Mute preferences** — a new table almost certainly keyed by `(user_id, categoria)` at minimum. If it's built by pattern-matching "just key by user_id" (since a user only ever seems to belong to one tenant in this system), it should still carry `tenant_id` explicitly and every read/write should filter by both — `STATE.md` already flagged this exact risk for `Notificacao` itself ("this project's first per-recipient-private entity, easy to under-scope by pattern-matching the tenant-only checks used everywhere else"), and a preferences table is the same shape of risk one level further.
2. **Team resolution for NOTF-25** — resolving a Processo's team via its `clienteId` must re-verify that the `Cliente` being joined through actually belongs to the same tenant as the `Processo`/the acting request, not just trust the FK blindly — the same reasoning already applied to `POST /documentos/upload`'s `clienteId`/`processoId` ownership check (Phase 79 fix, `PROJECT.md`).
3. **`AlertasDiariosJob`'s mute/snooze checks** — this job runs with **no security context at all** (`SecurityContextHolder`/`getTenantId()` are never called; `tenantId` is always an explicit parameter threaded through every call, by deliberate design, per the job's own Javadoc and its code-review comments). Any new mute/snooze lookup added inside this job's loop must take `tenantId` as an explicit parameter through the same call chain — copy-pasting a mute-check helper written for the (session-based) trigger-method call sites into this job will null-pointer or, worse, silently query across all tenants if it falls back to some ambient/default tenant resolution.

**Why it happens:**
Copy-paste from an adjacent, superficially-similar call site is the normal way new features get built quickly — but the adjacent call site in this codebase very often has a *subtly different* isolation requirement (session-scoped vs. job-scoped; direct-column vs. transitive-via-parent), and this project's own history (Key Decisions table) is largely a record of exactly this mistake being caught, repeatedly, across many prior phases.

**How to avoid:**
For each of the three surfaces above, write the isolation check as the *first* thing implemented, verified with two independent test users/tenants (the pattern `STATE.md` already prescribes for `Notificacao` itself), before any UI or trigger wiring. For the job specifically, grep the new code path for any `getTenantId()`/`SecurityContextHolder` call before merging — its absence in the rest of the file is not incidental, it's an ADR-worthy constraint.

**Warning signs:**
- A new preferences/team-resolution query has no `tenant_id` in its `WHERE` clause, relying on `user_id`/`cliente_id` alone.
- Any new helper method used by `AlertasDiariosJob` calls `getTenantId()` or references `SecurityContextHolder`.
- A team-resolution join from `Processo` → `Cliente` → `ClienteAdvogado` has no explicit tenant check at the `Cliente` step.

**Phase to address:** Cross-cutting — enforce during code review on NOTF-24, NOTF-25, and NOTF-26 phases specifically (not a separate phase of its own); the audit-discovery phase should specifically grep for these three patterns as a checklist item.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|-----------------|------------------|
| Mute check duplicated at each trigger call site instead of centralized in `criar()` | Feels "more explicit" per-category | Silent bypass the moment a 6th trigger (or a future job) is added | Never |
| Snooze implemented by reusing `lida` (read) flag | Zero schema change | Corrupts unread-count semantics and read/unread filters already relied on by `/notificacoes` UI | Never |
| H2 used for the native-query test (`buscarPorFiltros`) "for speed" | Faster local test runs, no Docker needed | False confidence — dialect gaps between H2's Postgres-compat mode and real Postgres are exactly where native queries break | Only for plain-JPQL/derived-query repositories with zero native SQL |
| `@SpringBootTest` used for a narrow repository/job verification | Feels "more realistic" | Hits the same required-env-var/MinIO wall already blocking live UAT; slow, fragile | Only if a full HTTP-level RBAC/tenant round-trip is the explicit goal, and only with a dedicated stub property source |
| Team resolution for NOTF-25 written inline at each trigger instead of one shared helper | Faster to ship the first trigger | Drift across 4-5 independently-maintained copies, exactly the failure class `RiscoPrazoService`/`criar()` were extracted to prevent | Never — this project has already paid to remove 4 divergent copies of "prazo crítico" once (Phase 85); don't recreate the same problem shape for "team" |
| Skipping the CI-wiring decision for new tests/SpotBugs ("we'll do it later") | Ships this milestone faster | Repeats the exact SpotBugs-rot history this milestone is fixing | Only if explicitly recorded as a deliberate decision, not silence |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|-------------------|
| `NotificacaoService.criar()` ↔ its 5 callers | Assuming `criar()`'s return contract stays "always returns a persisted object" after adding mute logic | Audit every caller's handling of the new no-op/muted outcome explicitly, don't just add the check and hope nothing downstream cared about the return value |
| `AlertasDiariosJob` ↔ `uk_notificacao_dedup` | Assuming the existence-check and the DB constraint are redundant/interchangeable | They're a check-then-act pair by design — any new state (snooze) must be consistent with *both*, not just the application-level check |
| `notificarDocumentoNovo`/`notificarParecerAtribuido` ↔ `uk_notificacao_dedup` | Assuming "no dedup between primary and ADMIN fan-out" (a Phase 87 comment) is still safe after the Phase 88 constraint | It isn't — see Pitfall 2. Verify every "intentional non-dedup" comment written before Phase 88 against the constraint added in Phase 88 |
| Testcontainers ↔ this repo's Dockerfile/CI | Assuming adding the dependency to `pom.xml` is sufficient | `mvn -DskipTests package` in `Dockerfile` and no test step in `deploy.yml` mean the tests never run unless CI is also changed (Pitfall 7) |
| `agenda/page.tsx` ↔ `GET /prazos`/`GET /eventos` | Assuming both endpoints return the same shape/fields | Only Prazos already return `risco`; Eventos' general list endpoint does not (Pitfall 9) |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| Per-team-member `existsBy...` existence-check query in `AlertasDiariosJob`'s daily loop, multiplied by team size instead of single responsavel | Job duration grows roughly linearly with average team size × entity count × tenant count | Preload today's existing notification tuples per tenant/category once (mirroring the existing `safeProcessoPorId`/`safeAdmins` preload pattern) instead of one query per recipient per entity | Noticeable once team sizes and/or entity counts per tenant grow past what a handful of admins already costs today — worth profiling once NOTF-25 ships, not before |
| Mute-preference lookup called once per notification-creation attempt with no caching, inside a hot loop (job or fan-out) | Extra DB round-trip per recipient per entity per day | Preload the acting tenant's mute preferences once per job run (or per request for event-triggered paths), same preload-once philosophy already used for `processoPorId`/`admins` in `AlertasDiariosJob` | Same threshold as above |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Preferences/team-resolution query keyed by `user_id` alone, no `tenant_id` filter | Cross-tenant preference/notification leakage if a user id is ever reused/guessed, or if the join chain has any gap | Always filter by `tenant_id` explicitly, even where a `user_id`-only filter would "happen to" be correct today (STATE.md already flags this exact risk class for `Notificacao`) |
| `AlertasDiariosJob` mute/snooze helper calling `getTenantId()`/`SecurityContextHolder` | `NullPointerException` at best (no security context on a scheduler thread), or silent wrong-tenant behavior at worst if a fallback/default sneaks in | Thread `tenantId` explicitly through every call, matching the job's existing, deliberate pattern |
| Team resolution trusting `Processo.clienteId` without re-verifying the `Cliente`'s tenant matches the request's tenant | Cross-tenant data exposure via a manipulated/mismatched FK, same class of gap already found and fixed for `POST /documentos/upload`'s `clienteId`/`processoId` (Phase 79) | Re-verify tenant ownership at the `Cliente` join step, not just at the `Processo` step |
| `DataIntegrityViolationException` from `uk_notificacao_dedup` propagating uncaught out of an already-committed business transaction (Pitfall 2) | 500 + rollback of an unrelated, already-succeeded business action (document upload, parecer attribution) — a reliability bug more than a confidentiality one, but real user-facing breakage | Pre-dedupe recipient sets before looping; add `DataIntegrityViolationException` backstop matching `AlertasDiariosJob`'s existing pattern |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-------------------|
| Mute granularity mismatched to what users actually think in (e.g., forcing 9 individual per-`categoria` toggles when users think in terms of "prazos" vs. "documentos" vs. "atribuições") | Settings screen with 9 confusing raw-enum toggles | Reuse the existing `CATEGORIA_LABEL_MAP`/`NOTIFICACAO_CATEGORIA_OPTIONS` (`web/src/lib/notificacao-categoria.ts`) as the UI's source of truth for labels/grouping — it's already the exhaustive, translated list this feature needs, don't reinvent it |
| Snooze with no visible "snoozed until X" indicator anywhere in the bell/`/notificacoes` list | User forgets they snoozed something and is confused when it reappears (or doesn't) | Surface snooze state explicitly in the list (e.g., a "Silenciado até DD/MM" badge), consistent with how `lida`/categoria are already surfaced |
| Agenda's "urgentes" stat silently changing behavior once consolidated with `RiscoPrazoService` (fewer/more items counted than before) | Users who relied on the old, wrong count for daily triage see a sudden shift with no explanation | Call out the behavior change explicitly in this phase's UAT notes — this is a deliberate correctness fix, but it will look like a regression to anyone comparing before/after counts without context |

## "Looks Done But Isn't" Checklist

- [ ] **NOTF-24 mute:** Verify the mute check is inside `criar()` itself — grep for the string `"criar("` and confirm every call site (including `AlertasDiariosJob.notificar()`) is covered by inspection, not just the 4 public trigger methods.
- [ ] **NOTF-25 team:** Verify the Processo-linked `notificarDocumentoNovo` call site was actually changed (currently `List.of(resp)`) — don't assume "team support was added" means every existing single-recipient call site was upgraded.
- [ ] **NOTF-25 + dedup constraint:** Verify a test exists for "team member who is also ADMIN" specifically — this is the collision case that trips `uk_notificacao_dedup`.
- [ ] **NOTF-26 snooze:** Verify a test simulates at least 3 consecutive daily job runs (create → snooze → don't-recreate) — a single-run test will not catch the reappearing-snooze bug.
- [ ] **Testing infra:** Verify the native-query test (`buscarPorFiltros`) runs against a real Postgres Testcontainer, not H2 — check the actual `@Testcontainers`/`@ServiceConnection` config, not just that "a test exists."
- [ ] **Testing infra + CI:** Verify `deploy.yml` (or a new workflow) actually invokes the new tests — a green `mvn test` on a developer's machine proves nothing about CI unless CI was changed too.
- [ ] **SpotBugs fix:** Verify the uncommitted working-tree changes (`pom.xml`, `spotbugs-exclude.xml`, `UserPrincipal`/`ConflictCheckResponse`/`WorkflowResponse`/`ResourceController` fixes) were actually reviewed and committed, not silently lost or redone from scratch.
- [ ] **Agenda consolidation:** Verify `GET /eventos` (the endpoint agenda's calendar actually calls) was checked for whether it needed a `risco` field added — don't assume "Prazos now show correct risk" means "Eventos do too."
- [ ] **Multi-tenant:** Verify every new table/query introduced by NOTF-24/25/26 has an explicit `tenant_id` filter, even where a narrower key (`user_id`, `cliente_id`) would "happen to" produce the right answer today.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|-----------------|
| Mute check bypassed by a missed call site | LOW | Since `criar()` is the single choke point, moving the check there retroactively fixes every past and future call site at once — no data migration needed, just relocate the guard |
| Team fan-out trips `uk_notificacao_dedup` in production | MEDIUM | Deploy the pre-dedup fix (merge recipient sets before looping) + the `DataIntegrityViolationException` backstop; no data cleanup needed since the failure mode is "500, transaction rolled back," not "bad data persisted" |
| Snooze reappears immediately (Pitfall 4 realized) | MEDIUM | Add the snoozed-until column/table and extend the job's existence-check; existing rows are unaffected, this is additive |
| Integration tests added but never wired into CI, discovered late | LOW | Add the CI step; no code changes needed, purely pipeline configuration |
| SpotBugs uncommitted work lost via a careless git operation | HIGH (if truly lost — re-deriving the ENTITY_MASS_ASSIGNMENT/EI_EXPOSE_REP fixes and their review rationale takes real re-analysis time) | Check `git reflog`/any stash immediately if this happens; otherwise, redo the `mvn spotbugs:check` run and re-triage from scratch, using this document's Pitfall 8 as a checklist of what was already found once |
| Agenda consolidation ships without the Eventos `risco` field, leaving a partial fix | LOW | Frontend-only or backend-only follow-up depending on which half was skipped; no data migration involved either way |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|-------------------|----------------|
| 1 — mute check placement | NOTF-24 phase | Grep confirms mute check lives only in `criar()`; test exercises the daily job path specifically, not just the 4 trigger methods |
| 2 — team fan-out vs. dedup constraint | Audit-discovery phase (fix, pre-existing) + NOTF-25 phase (don't reintroduce) | Test: team member who is also ADMIN, same category/entity — no `DataIntegrityViolationException`, exactly one row per person |
| 3 — team concept doesn't exist / inconsistent call site | NOTF-25 phase | Processo-linked `notificarDocumentoNovo` call site pulls full team (not just `responsavelId`); shared resolver used by all 4-5 trigger points |
| 4 — snooze vs. job idempotency | NOTF-26 phase | 3-consecutive-run simulation test (create/snooze/no-recreate/expire-decision) |
| 5 — `@SpringBootTest` env-var wall | Testing-infrastructure phase | New tests use `@DataJpaTest`/slice scope; no test requires a real `.env`/MinIO |
| 6 — H2 false confidence on native query | Testing-infrastructure phase | `buscarPorFiltros` test specifically runs against a Postgres Testcontainer |
| 7 — CI never runs tests/SpotBugs | Testing-infrastructure phase + SpotBugs-fix phase (shared) | `deploy.yml` diff shows a new test/verify step, or an explicit recorded decision not to add one |
| 8 — uncommitted SpotBugs work | SpotBugs-fix phase | `git log` shows the existing working-tree diff committed (not redone) as the phase's first commit |
| 9 — agenda consolidation split (Prazos vs. Eventos) | Agenda/RiscoPrazoService-consolidation phase | Both `GET /prazos`-derived and `GET /eventos`-derived risk displays verified separately, with an explicit recorded decision for the Eventos gap |
| 10 — multi-tenant leakage in 3 new surfaces | Cross-cutting, enforced in NOTF-24/25/26 phases + audit-discovery phase checklist | Two-tenant test for each new query; grep for `getTenantId()`/`SecurityContextHolder` inside `AlertasDiariosJob`-reachable code returns zero matches |

## Sources

- Direct code reads (this repository, 2026-07-12): `backend/src/main/java/com/lexcv/services/NotificacaoService.java`, `backend/src/main/java/com/lexcv/models/Notificacao.java`, `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`, `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java`, `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (notification trigger call sites, `ClienteAdvogado`/`ClienteAdministrativo` endpoints, `Processo.clienteId`), `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql`, `backend/pom.xml` (+ uncommitted diff), `backend/spotbugs-exclude.xml` (untracked), `backend/Dockerfile`, `.github/workflows/deploy.yml`, `web/src/app/(dashboard)/agenda/page.tsx`, `web/src/lib/prazos.ts`, `web/src/lib/notificacao-categoria.ts`, `web/src/types/processos.ts`, `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`, `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java`.
- Project history/precedent: `.planning/PROJECT.md` (Key Decisions table — cross-phase integration bugs already found and fixed in this exact codebase: `pesquisar()` routing bug, `/honorarios`/`/documentos` filter bugs, `POST /documentos/upload` ownership gap, Phase 87 CR-01/CR-02 per-recipient isolation fixes, Phase 88 WR-01/02/03/04 job failure-isolation and idempotency design).
- `.planning/STATE.md` (Pending Todos, Deferred Items — explicit prior flags for `Notificacao` as "first per-recipient-private entity," `AlertasDiariosJob` as "first background-thread code path," the H2/Testcontainers gap, and the SpotBugs/JDK-23 gap).
- Git history/working-tree inspection (`git log`/`git diff`/`git status` on the files above) used to establish phase ordering (Phase 86 vs. 87 vs. 88) and to discover the uncommitted SpotBugs fix already present in the working tree.

---
*Pitfalls research for: LexCV milestone v2.11 (Auditoria Técnica e Notificações Avançadas)*
*Researched: 2026-07-12*
