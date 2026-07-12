# Stack Research

**Domain:** Backend/frontend tech-debt closure + notification-feature extension for an existing multi-tenant Spring Boot / Next.js legal-practice app (LexCV v2.11 — Auditoria Técnica e Notificações Avançadas)
**Researched:** 2026-07-12
**Confidence:** HIGH (versions verified directly against the actual `spring-boot-dependencies:3.4.1` POM fetched from Maven Central, official Spring Boot testing docs, and this repo's own uncommitted `backend/pom.xml`/`backend/spotbugs-exclude.xml` state — not training-data guesses)

## Headline Finding

**No new runtime dependency is needed for NOTF-24/25/26 (part a).** They are pure extensions of the JPA/`NotificacaoService`/TanStack-Query/shadcn stack v2.10 already built. **One new dependency set is needed for integration-test infra (part b): Testcontainers' PostgreSQL module + the `spring-boot-testcontainers` starter, test-scoped.** **Part (c), SpotBugs/FindSecBugs on JDK 23, is already fixed in the working tree but uncommitted** — this milestone's job is to commit/formalize it, not re-solve it.

| Question asked | Short answer |
|---|---|
| (a) What's needed for NOTF-24/25/26? | Nothing new — same JPA entity + `NotificacaoService` choke-point pattern already used for every v2.10 alert, same `@radix-ui/react-switch`/`-radio-group`/`-popover` primitives already installed on the frontend. |
| (b) What test infra for the native query + concurrency lock? | Testcontainers PostgreSQL (managed version **1.20.4**, inherited from `spring-boot-starter-parent:3.4.1` — do not pin explicitly), not H2. See rationale below — these two specific risk areas need real Postgres semantics. |
| (c) Is the SpotBugs/FindSecBugs fix still open? | No — `git diff backend/pom.xml` shows it already bumped (`spotbugs-maven-plugin` 4.8.3.1→4.10.2.0, `findsecbugs-plugin` 1.13.0→1.14.0) and `backend/spotbugs-exclude.xml` already exists with individually-reviewed suppressions, dated this same week. Both versions are confirmed current-latest on Maven Central. |

---

## (a) NOTF-24 / NOTF-25 / NOTF-26 — no new dependencies

### Why no new libraries

All three features extend infrastructure v2.10 already shipped:

- **NOTF-24 (mute categories per user)** — `Notificacao.categoria` is already a free-text `String` (`FASE_ENTRADA`, `DOCUMENTO_NOVO`, `PROCESSO_ATRIBUIDO`, `PARECER_ATRIBUIDO`, `PRAZO_PROXIMO`, `PRAZO_VENCIDO`, `HONORARIO_ATRASADO` — confirmed by reading `NotificacaoService.java`/`AlertasDiariosJob.java`). This needs one new small entity (`NotificacaoPreferencia`: `tenant_id`, `user_id`, `categoria`, `silenciada`) plus a check inside `NotificacaoService.criar(...)` (the single existing choke point every alert already flows through — confirmed by reading the file and its test, `NotificacaoServiceTest`) before persisting a row for that recipient+category. Plain JPA — the same tenant-scoped junction-table pattern already used for `ClienteAdvogado`/`ClienteAdministrativo`. No new backend library. Frontend needs a small settings form (category list + `Switch` toggles) — `@radix-ui/react-switch` **is already a dependency** (`web/package.json`), already wrapped as `web/src/components/ui/switch.tsx`, already used elsewhere. Reuse it; do not add a second toggle/checkbox library.
- **NOTF-25 (notify full team, not just `responsavelId`)** — confirmed by reading `Processo.java`: there is currently no `ProcessoAdvogado`/`ProcessoAdministrativo` join table, only the single `responsavel_id` column. Closing this gap needs either (a) a new join table mirroring `ClienteAdvogado`/`ClienteAdministrativo`, or (b) resolving the team via the process's `cliente_id` → the existing `ClienteAdvogado`/`ClienteAdministrativo` tables. **This is a data-modeling decision for phase design, not a stack decision** — either path is plain JPA, zero new dependencies. Whichever is chosen, the fan-out should go through `NotificacaoService`'s existing per-recipient try/catch isolation — a bad/orphaned recipient ID must never roll back the triggering transaction nor suppress the guaranteed ADMIN fan-out. That exact guarantee was hardened over 3 code-review rounds in Phase 87 (see `notificarProcessoAtribuido_responsavelInvalido_...` and `notificarParecerAtribuido_advogadoInvalido_...` in `NotificacaoServiceTest.java`) and must not regress when the recipient set grows from "one responsável" to "a team."
- **NOTF-26 (snooze a deadline reminder)** — needs a "snoozed until" concept that `AlertasDiariosJob`'s idempotency check (`existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria`) can consult before re-firing a `PRAZO_PROXIMO`/`PRAZO_VENCIDO` alert for that entity+recipient. Implementable as a `snoozedUntil` timestamp — either a new small table keyed by `tenant_id + destinatario_id + entidade_tipo + entidade_id + categoria`, or a column checked by the job before calling `notificar(...)`. Pure `java.time.LocalDate`/`LocalDateTime` arithmetic — no scheduling or date-math library needed; the existing `@Scheduled` cron in `AlertasDiariosJob` does not need to change. Frontend needs a small "snooze for X" control on the bell dropdown / `/notificacoes` row. **`@radix-ui/react-radio-group` and `@radix-ui/react-popover` are already dependencies** (`web/src/components/ui/radio-group.tsx`, `popover.tsx` already exist and are used elsewhere) — a `Popover` containing a `RadioGroup` of fixed durations ("1 dia", "3 dias", "1 semana") is the zero-new-dependency path. **Do not introduce a calendar/date-picker library** — `web/package.json` has no `react-day-picker`, `date-fns`, or similar today, and a full calendar picker would be scope creep for a "snooze for a period" requirement that reads as fixed presets, not an arbitrary custom date.

### What NOT to add for (a)

| Avoid | Why | Use instead |
|-------|-----|--------------|
| A new frontend toggle/select/date-picker library | `@radix-ui/react-switch`, `-radio-group`, `-popover` already cover every UI need for these three features (verified against `web/package.json` and `web/src/components/ui/*`) | Existing `components/ui/*` primitives |
| A Java enum for `categoria` as part of this milestone | `categoria` is a plain `String` column used consistently across `Notificacao`, `NotificacaoService`, `AlertasDiariosJob`, and the frontend category filter on `/notificacoes` — converting it to an enum mid-milestone would touch every alert call site for a benefit unrelated to NOTF-24/25/26 | Keep `String`; validate against a known set only where preferences are read/written |
| A message-queue/event-bus library (Spring `ApplicationEventPublisher`, RabbitMQ, etc.) for the team fan-out | Out of proportion for current scale (single-tenant fan-out, at most a few dozen recipients); this codebase has zero existing `@EventListener`/`@Async` usage to build on | Extend `NotificacaoService` directly with the same synchronous, per-recipient try/catch pattern already used since Phase 87 |

---

## (b) Backend integration-test infrastructure

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Testcontainers `postgresql` module | **1.20.4** (inherited — do not pin explicitly) | Spins up a real, disposable PostgreSQL container per test run | `spring-boot-starter-parent:3.4.1` already imports `org.testcontainers:testcontainers-bom:1.20.4` inside its own `dependencyManagement` (verified directly against `spring-boot-dependencies-3.4.1.pom` fetched from `repo.maven.apache.org`, line ~2420). Declaring `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` **with no `<version>`** resolves correctly to 1.20.4 automatically — the same "let the parent BOM manage it" pattern this `pom.xml` already uses for `spring-boot-starter-test`/the `postgresql` runtime JDBC driver |
| `spring-boot-testcontainers` starter | managed by parent (3.4.1) | Provides `@ServiceConnection`, which auto-wires the container's JDBC URL/user/password into Spring's `DataSource` — no manual `@DynamicPropertySource` needed | Official, idiomatic Spring Boot ≥3.1 pattern, confirmed against `docs.spring.io/spring-boot/3.4/reference/testing/testcontainers.html`; removes an entire class of "forgot to override `spring.datasource.url`" bugs |
| `org.testcontainers:junit-jupiter` | managed (1.20.4) | `@Testcontainers`/`@Container` JUnit 5 extension | Pairs with the JUnit 5 already in use — `spring-boot-starter-test` already manages `junit-jupiter 5.11.4` via the same parent, and all 3 existing test classes (`RiscoPrazoServiceTest`, `NotificacaoServiceTest`, `AlertasDiariosJobTest`) are already JUnit 5 |

**Do NOT add `org.testcontainers:testcontainers-bom` as a manual `<dependencyManagement>` import at a newer version.** It is already imported transitively by `spring-boot-starter-parent`. Testcontainers has just released a **2.0.x** major line that **renamed every module artifact** (`org.testcontainers:postgresql` → `org.testcontainers:testcontainers-postgresql`) and **relocated container classes** (`org.testcontainers.containers.PostgreSQLContainer` → `org.testcontainers.postgresql.PostgreSQLContainer`). Spring Boot 3.4.x's own `@ServiceConnection` auto-configuration was built and tested against the pre-2.0 artifact names/packages (confirmed: `spring-projects/spring-boot#47639`, "Upgrade to Testcontainers 2.0.0," is still open at time of writing, and specifically notes the rename breaks `@ServiceConnection`/R2DBC discovery). Manually forcing 2.0.x here would fight the parent's tested pairing for zero benefit — use the inherited 1.20.4 with the old (`postgresql`, `junit-jupiter`) artifact coordinates shown below.

### Supporting Libraries

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| JUnit 5 (`junit-jupiter`) | 5.11.4 (already present via `spring-boot-starter-test`) | Test runner | Already used by all 3 existing test classes — no change |
| Mockito | 5.14.2 (already present via `spring-boot-starter-test`) | Unit-test mocking for classes with collaborators | Keep using for pure-unit tests with mocked repositories (as `NotificacaoServiceTest` already does); do not mock the repository layer in the two new integration tests below — that would defeat their purpose |
| AssertJ | 3.26.3 (already present via `spring-boot-starter-test`, currently unused in this repo's tests) | Fluent assertions | Optional — existing tests use plain JUnit `Assertions.*`; already on the test classpath if a phase author prefers it, no action needed either way |
| `java.util.concurrent` (`ExecutorService`, `CountDownLatch`, `Future`) | JDK 23 stdlib | Drive two genuinely concurrent transactions at the `ParecerVersao` unique-constraint race | No new dependency — stdlib is sufficient to fire two threads at the same `createVersao` code path and assert exactly one wins, one throws `DataIntegrityViolationException` |
| Awaitility | *(not recommended)* | Polling-style async assertions | Skip — the concurrency test needs deterministic "both threads submitted, then assert" semantics via `CountDownLatch`/`Future.get()`, not polling; adding it here is an unjustified dependency |

### Why Testcontainers-PostgreSQL, not H2, for these two specific risk areas

The question names two concrete targets, and both have failure modes a generic embedded-DB test wouldn't reliably surface:

1. **`NotificacaoRepository.buscarPorFiltros`** is a **native** query (`nativeQuery = true`) using Postgres-specific `CAST(:param AS text)`/`CAST(:param AS boolean)` idioms — the in-code comment explicitly says these casts exist "because PostgreSQL cannot infer the type of a bare null bind inside `(:param IS NULL OR ...)`." H2, even in `MODE=PostgreSQL` compatibility mode, is a different SQL engine with its own parser/type-inference — a native query built around a Postgres-specific quirk is exactly the kind of thing that can pass against H2 for the wrong reason, or fail for a reason unrelated to what actually runs in production. Only a real PostgreSQL engine gives a trustworthy result here.
2. **`ParecerVersao.numeroVersao`'s DB-level unique constraint** is a deliberate defense-in-depth backstop against a genuine multi-transaction race (`PROJECT.md` Key Decisions: "a verificação de idempotência 'check-then-act' a nível de aplicação... não protege contra uma race condition genuína entre pedidos concorrentes"). Proving this backstop actually fires under concurrent load requires real transactional/locking semantics — Postgres's MVCC and unique-index conflict behavior under concurrent inserts is not something H2 reproduces with equivalent fidelity. A test that "passes on H2" would not actually validate the thing this milestone needs validated.

This project deploys on real PostgreSQL everywhere (`docker-compose.yml` and `docker-compose.hostinger.yml` both use `postgres:16-alpine`), and Docker is already available in this repo's only CI pipeline (`ubuntu-latest` runner in `.github/workflows/deploy.yml`, which already runs Docker Buildx). There is no infrastructure reason to prefer H2. Use `new PostgreSQLContainer<>("postgres:16-alpine")` — same image tag already running in prod/dev, avoiding a class of "works differently on a different Postgres minor version" surprises.

**Do not introduce both H2 and Testcontainers.** There is no scenario in this specific milestone where H2's faster startup outweighs the fidelity loss for either named risk area. If a future phase needs a high volume of trivial, dialect-agnostic repository slice tests, that's a decision for that phase — not a reason to add a second DB technology now.

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `@DataJpaTest` + `@Testcontainers` + `@ServiceConnection` | Repository-slice test for `buscarPorFiltros` | Add `@AutoConfigureTestDatabase(replace = Replace.NONE)` — without it, Spring Boot's test auto-configuration replaces the `DataSource` with an embedded DB, defeating the point of wiring in the container |
| `@SpringBootTest` + `@Testcontainers` + `@ServiceConnection` | Full-context test for the `ParecerVersao` concurrency race (needs the real `createVersao` controller/service code path, not just the repository) | Two threads each performing a full save; assert one succeeds and the other surfaces the unique-constraint violation the same way `ParecerController` currently handles it |
| `src/test/resources/application-test.yml` (or `@TestPropertySource`) forcing `spring.jpa.hibernate.ddl-auto=create-drop` | Builds the schema fresh from JPA entity annotations against the Testcontainers Postgres instance for each test run | This project has no Flyway/Liquibase — migrations are manual SQL scripts in `backend/migrations/`, applied by hand where prod uses `ddl-auto=validate`. For tests, `create-drop` against annotation-declared constraints is correct and sufficient: both constraints under test (`Notificacao`'s `uk_notificacao_dedup` and `ParecerVersao`'s `(solicitacao_id, numero_versao)`) are declared directly on the `@Entity` via `@Table(uniqueConstraints=...)`, so Hibernate creates them from the annotation alone, independent of the manual migration scripts being replayed |
| CI wiring for `mvn test` | Currently, `.github/workflows/deploy.yml` never invokes `mvn test` — it only runs `docker/build-push-action`, which builds the backend image via `mvn -DskipTests package` inside the Dockerfile (per `CLAUDE.md`) | Out of scope for stack research, but flagged for phase planning: these tests only produce ongoing value once a CI step actually runs them. `ubuntu-latest` GitHub-hosted runners have a Docker daemon available by default, so Testcontainers needs no additional CI configuration once a test step exists |

## Installation

```xml
<!-- backend/pom.xml — add inside <dependencies>, no <version> tags (parent-managed) -->
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

No `npm`/`pnpm` installation needed for (a) or (b) — every frontend primitive required already exists in `web/package.json`.

For (c), no installation is needed either — `backend/pom.xml` already has the corrected versions uncommitted; see below.

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|-------------------------|
| Testcontainers + real PostgreSQL container | H2 in-memory (`MODE=PostgreSQL`) | If a future phase needs many fast, dialect-agnostic repository tests where Postgres-specific SQL/locking behavior is irrelevant — not the case for either target named in this milestone |
| `@ServiceConnection` auto-wiring | Manual `@DynamicPropertySource` registering `spring.datasource.url` etc. | Only if ever on Spring Boot <3.1 (not the case here — this project is on 3.4.1) |
| Fixed-duration `RadioGroup` for NOTF-26 snooze | A calendar/date-picker (`react-day-picker` + `date-fns`) | Only if the milestone later decides snooze must support an arbitrary custom date rather than fixed presets — not indicated by "snooze de lembrete de prazo … por um período" as currently stated |
| New `NotificacaoPreferencia`/team join table via plain JPA | A generic "settings JSON blob" column (the `dados_tipo` pattern used, then reverted, for `Cliente` in v2.4/v2.7) | Never for this case — `PROJECT.md`'s own Key Decisions log records that pattern was deliberately reverted in v2.7 because normalized columns were easier to validate/maintain; do not reintroduce it here |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|--------------|
| H2 (for the two named risk areas in this milestone) | Different SQL engine/locking semantics than production Postgres; specifically wrong for validating a Postgres-specific native-query CAST idiom and a real concurrent-transaction unique-constraint race | Testcontainers `PostgreSQLContainer("postgres:16-alpine")` |
| Manually importing `org.testcontainers:testcontainers-bom` at the newly-released 2.0.x line | Renamed artifacts (`testcontainers-postgresql`) and relocated `PostgreSQLContainer` package are not what Spring Boot 3.4.x's `@ServiceConnection` auto-configuration expects (confirmed open issue `spring-boot#47639`); fights the parent's tested 1.20.4 pairing | Let `spring-boot-starter-parent:3.4.1` manage the version — declare `org.testcontainers:postgresql`/`:junit-jupiter` with no `<version>` |
| A second SAST tool (Semgrep, SonarQube plugin, Checkstyle security rules, etc.) "to make sure SpotBugs really works" | `backend/spotbugs-exclude.xml`'s own header comment documents a first-ever successful, individually-reviewed `mvn spotbugs:check` run against the current pinned versions — the tool already works; remaining work is committing/formalizing it | Commit the existing uncommitted `pom.xml`/`spotbugs-exclude.xml` state; re-run `mvn spotbugs:check` to reconfirm before commit |
| Downgrading `spotbugs-maven-plugin`/`findsecbugs-plugin` "to be safe" | Both are already the current latest Maven Central releases (verified 2026-07-12: `spotbugs-maven-plugin` 4.10.2.0, published 2026-06-09; `findsecbugs-plugin` 1.14.0), and SpotBugs's bundled ASM (9.8) already supports bytecode well beyond Java 23 (up to Java 25) | Keep the versions already in the working tree |

## Stack Patterns by Variant

**If a future phase needs a plain repository-slice test with no Postgres-specific SQL:**
- `@DataJpaTest` alone (Spring Boot's default embedded-H2 auto-configuration) is acceptable there.
- Because there is no fidelity requirement to lose — but this milestone's two named targets are not that case.

**If a future phase adopts Flyway/Liquibase:**
- Testcontainers tests should run real migrations against the container rather than `ddl-auto=create-drop`.
- Because that becomes the more faithful representation of what actually runs in prod — not applicable today since this project has no migration tool (manual SQL scripts only).

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|-----------------|-------|
| `spring-boot-starter-parent:3.4.1` | `testcontainers-bom:1.20.4` (transitively imported) | Verified directly against `spring-boot-dependencies-3.4.1.pom` fetched from Maven Central — do not override to Testcontainers 2.0.x |
| `spotbugs-maven-plugin:4.10.2.0` | `findsecbugs-plugin:1.14.0`, JDK 23 bytecode (class file major version 67) | Already validated in this repo's own uncommitted state — `spotbugs-exclude.xml` header: "first-ever successful `mvn spotbugs:check` run … after upgrading spotbugs-maven-plugin to 4.10.2.0 for Java 23 support" |
| `PostgreSQLContainer("postgres:16-alpine")` | `docker-compose.yml`/`docker-compose.hostinger.yml` (`postgres:16-alpine`) | Matches prod/dev image tag exactly — deliberate choice to avoid minor-version drift between test and prod Postgres |

## (c) SpotBugs + FindSecBugs on JDK 23 — status and rationale

**This is not an open problem to solve from scratch — it is already solved in the working tree, uncommitted.** `git diff backend/pom.xml` (run as part of this research) shows:

```diff
-                <version>4.8.3.1</version>
+                <version>4.10.2.0</version>
                  <configuration>
+                    <excludeFilterFile>spotbugs-exclude.xml</excludeFilterFile>
                      <plugins>
                          <plugin>
                              <groupId>com.h3xstream.findsecbugs</groupId>
                              <artifactId>findsecbugs-plugin</artifactId>
-                            <version>1.13.0</version>
+                            <version>1.14.0</version>
```

`backend/spotbugs-exclude.xml` (currently untracked/`??` in `git status`) already exists with a documented header: "suppressions for findings reviewed during the first-ever successful `mvn spotbugs:check` run (2026-07, after upgrading spotbugs-maven-plugin to 4.10.2.0 for Java 23 support)" and 3 groups of individually-reviewed, justified `<Match>` entries (mass-assignment false positives with documented allowlist-copy reasoning, and one null-check false positive on a Hibernate-assigned UUID `@GeneratedValue`).

Both versions are confirmed current-latest on Maven Central as of this research (2026-07-12): `spotbugs-maven-plugin` 4.10.2.0 (published 2026-06-09) and `findsecbugs-plugin` 1.14.0. SpotBugs's own bundled ASM version (9.8) supports bytecode well past Java 23 (up to Java 25), so this pairing is not just "the newest available" but genuinely sufficient for JDK 23 class files (major version 67).

**Recommendation for this milestone:** treat this as verification + commit, not re-engineering:
1. Run `mvn spotbugs:check` (and ideally `mvn verify`) fresh, from a clean checkout of the current working tree, to reconfirm the build still passes with these versions before committing.
2. Commit `backend/pom.xml`'s version bumps and `backend/spotbugs-exclude.xml` together, with a message that credits this as closing the "SAST broken on JDK 23" tech debt.
3. Do not add, swap, or "double up" the SAST tool — the fix is version + exclusion-file, not a tool change.

## Sources

- `spring-boot-dependencies-3.4.1.pom` (fetched directly from Maven Central, `repo.maven.apache.org`) — verified `testcontainers.version=1.20.4`, `junit-jupiter.version=5.11.4`, `mockito.version=5.14.2`, `assertj.version=3.26.3`, `postgresql.version=42.7.4` (JDBC driver) — HIGH confidence, primary source
- `https://docs.spring.io/spring-boot/3.4/reference/testing/testcontainers.html` — official `@ServiceConnection`/`spring-boot-testcontainers` dependency list and example — HIGH confidence
- `https://github.com/spring-projects/spring-boot/issues/47639` ("Upgrade to Testcontainers 2.0.0") — confirms Spring Boot has not yet moved off the pre-2.0 Testcontainers artifact naming, and that the 2.0.x rename breaks `@ServiceConnection`/R2DBC discovery — MEDIUM confidence (single GitHub issue), consistent with the directly-verified 1.20.4 pin above
- Context7 `/testcontainers/testcontainers-java` — confirmed Testcontainers 2.0.x renamed module artifacts (`postgresql` → `testcontainers-postgresql`) and relocated container classes — MEDIUM confidence (mixed old/new naming in the same result set), cross-checked against `mvnrepository.com/artifact/org.testcontainers/testcontainers-postgresql/2.0.2` and OpenRewrite's `testcontainers2migration` recipe docs, which independently confirm the rename
- WebSearch cross-checked against `mvnrepository.com`/`central.sonatype.com` — `spotbugs-maven-plugin` 4.10.2.0 (published 2026-06-09) and `findsecbugs-plugin` 1.14.0 confirmed current-latest — MEDIUM-HIGH confidence (multiple independent listings agree)
- WebSearch — SpotBugs bundles ASM 9.8, supporting bytecode up to Java 25 — MEDIUM confidence (no single canonical changelog entry found, but consistent across results and consistent with this repo's own already-successful `mvn spotbugs:check` run against JDK 23 bytecode)
- Direct repo inspection (HIGH confidence, primary source for all "already exists / already fixed / no new dependency needed" claims): `git diff backend/pom.xml`, `git status`, `git log --oneline -- backend/pom.xml backend/spotbugs-exclude.xml`, `backend/spotbugs-exclude.xml`, `backend/src/main/java/com/lexcv/models/{Notificacao,ParecerVersao,Processo}.java`, `backend/src/main/java/com/lexcv/repositories/{NotificacaoRepository,ParecerVersaoRepository}.java`, `backend/src/main/java/com/lexcv/services/NotificacaoService.java`, `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`, `backend/src/test/java/com/lexcv/**` (existing 3 test classes), `backend/src/main/resources/application.yml`, `web/package.json`, `web/src/components/ui/*`, `docker-compose.yml`, `docker-compose.hostinger.yml`, `.github/workflows/deploy.yml`, `.planning/PROJECT.md`

---
*Stack research for: LexCV v2.11 (Auditoria Técnica e Notificações Avançadas) — NOTF-24/25/26, backend integration-test infrastructure, SpotBugs/FindSecBugs on JDK 23*
*Researched: 2026-07-12*
