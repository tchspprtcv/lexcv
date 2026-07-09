# Phase 88: Verificação Diária de Prazos e Honorários - Pattern Map

**Mapped:** 2026-07-09
**Files analyzed:** 5 to create/modify (+ 2 flagged design-decision points on existing files)
**Analogs found:** 5 / 5 (all composite/partial — this phase introduces the first `@Scheduled` job, so no single exact behavioral analog exists anywhere in the codebase; every assignment below is a synthesis of 2-3 concrete existing patterns)

**Note on Role/Data-Flow taxonomy:** this phase is 100% backend (no frontend files). The standard Role enum (controller/component/service/model/middleware/utility/config/test) doesn't have a slot for "scheduled job" or "repository" — I use `job` and `repository` as pragmatic, accurate labels below rather than force-fitting `service`/`model`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` (NEW) | job (batch orchestrator) | batch, edge-triggered | Composite: `services/NotificacaoService.java` (write shape) + `seed/DatabaseSeeder.java` (class shape/package precedent) + `controllers/ResourceController.java:908-922` (batch-preload) | partial — first of its kind, no single exact analog |
| `backend/src/main/java/com/lexcv/config/SchedulingConfig.java` (NEW) | config | n/a | `config/MinioConfig.java` | role-match (structural — small, single-responsibility `@Configuration`) |
| `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` (MODIFIED — add 1 method) | repository | CRUD (existence-check) | itself — existing derived-query methods in the same file | exact (same file, same conventions) |
| `backend/src/main/java/com/lexcv/repositories/HonorarioRepository.java` (MODIFIED — add 1 method) | repository | batch read | `controllers/ResourceController.java:908-922` (Java-side batch-map idiom) — no existing `...In(Collection<...>)` repository method anywhere to copy verbatim | role-match, but this specific derived-query shape is new to the codebase |
| `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java` (NEW) | test | n/a | `services/NotificacaoServiceTest.java` (Mockito shape) + `services/RiscoPrazoServiceTest.java` (fixed-date determinism convention) | exact — these are literally this job's own two dependencies' test files |

---

## Cross-Tenant Data Access — what's already solved vs. what's genuinely new

This is the orchestrator's central question, so stating it plainly before the per-file breakdown: **most of "how does the job read across all tenants" is already solved by existing repository methods that happen to take `tenantId` as an explicit parameter** (this codebase's repositories have never depended on `SecurityContextHolder` — only the *controller* convenience helper `getTenantId()` does). Only one genuinely new repository method is required.

| Entity | Method to use | Status |
|---|---|---|
| `Tenant` | `tenantRepository.findAll()` (bare `JpaRepository<Tenant, UUID>`, `TenantRepository.java:7` — no custom methods, no `ativo`/status column on `Tenant.java` to filter by) | **Already exists** — no change |
| `Prazo` | `prazoRepository.findByTenantId(tenantId)` (`PrazoRepository.java:10`) | **Already exists** — no change |
| `Evento` | `eventoRepository.findByTenantId(tenantId)` (`EventoRepository.java:10`) | **Already exists** — no change |
| `Processo` | `processoRepository.findByTenantId(tenantId)` (`ProcessoRepository.java:9`) | **Already exists** — no change |
| `User` (ADMIN fan-out) | `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` (`UserRepository.java:16-17`) | **Already exists** — no change |
| `User` (responsavel batch lookup) | `userRepository.findAllById(Collection<UUID>)` (inherited from `JpaRepository`) | **Already exists** — no change (see batch-preload pattern below) |
| `Honorario` | **No tenant-scoped method exists.** `HonorarioRepository` only has `findByProcessoId(UUID)` (singular). `Honorario.java` has **no `tenant_id` column at all** — tenant isolation is transitive via `Honorario.processoId → Processo.tenantId` (confirmed by reading `Honorario.java`: fields are `id, processoId, valorTotal, descricao, dataAcordo`, no `tenantId`). | **New method required**: `findByProcessoIdIn(Collection<UUID> processoIds)` |
| `Notificacao` (idempotency check) | **No existence-check method exists.** Current methods are `buscarPorFiltros`, `countByTenantIdAndDestinatarioIdAndLidaFalse`, `findByTenantIdAndDestinatarioIdAndLidaFalse`, `findByIdAndTenantIdAndDestinatarioId` — none test "does a row for this exact (tenant, recipient, entity, categoria) tuple already exist." | **New method required**: `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(...)` |

So: **2 new repository methods, both simple Spring Data derived queries (no `@Query` annotation needed), added to 2 already-existing repository files.** No new `Notificacao`/`Prazo`/`Evento`/`Tenant` repository changes at all.

---

## Pattern Assignments

### `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` (repository, CRUD — MODIFIED)

**Analog:** itself (`NotificacaoRepository.java`, full file already read — 52 lines)

**Existing derived-query conventions to match exactly** (`NotificacaoRepository.java:42,46,51`):
```java
long countByTenantIdAndDestinatarioIdAndLidaFalse(UUID tenantId, UUID destinatarioId);

List<Notificacao> findByTenantIdAndDestinatarioIdAndLidaFalse(UUID tenantId, UUID destinatarioId);

Optional<Notificacao> findByIdAndTenantIdAndDestinatarioId(UUID id, UUID tenantId, UUID destinatarioId);
```

**New method to add** (matches the exact `(tenantId, destinatarioId, entidadeTipo, entidadeId, categoria)` tuple specified in CONTEXT.md's idempotency decision, line 34):
```java
boolean existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
        UUID tenantId, UUID destinatarioId, String entidadeTipo, String entidadeId, String categoria);
```
Plain Spring Data method-name derivation — no `@Query` needed, consistent with every other simple predicate method already in this file/codebase. Field order in the method name should match the entity's declared field order (`tenantId, destinatarioId, ..., entidadeTipo, entidadeId` per `Notificacao.java:20-35`) purely for readability; Spring Data doesn't care about order.

---

### `backend/src/main/java/com/lexcv/repositories/HonorarioRepository.java` (repository, batch read — MODIFIED)

**Analog:** No existing `...In(Collection<...>)` method anywhere in `backend/src/main/java/com/lexcv/repositories/` (confirmed via grep — this will be the **first** batch-`IN` query method in the codebase). This exact gap and exact method name are called out explicitly in `.planning/research/PITFALLS.md` Pitfall 7 as the fix for the `listHonorarios` no-filter N+1 anti-pattern (`ResourceController.java:2568-2575`, which loops `findByProcessoId` once per processo — do **not** model the job on that).

**Current file (full contents, 10 lines):**
```java
public interface HonorarioRepository extends JpaRepository<Honorario, Integer> {
    List<Honorario> findByProcessoId(UUID processoId);
}
```

**New method to add:**
```java
List<Honorario> findByProcessoIdIn(Collection<UUID> processoIds);
```

**Usage shape** (job side — batch-fetch once per tenant, then map in Java, mirroring the `responsaveisMap`/`prazosPorProcesso` pattern below):
```java
List<Processo> processos = processoRepository.findByTenantId(tenantId);
Map<UUID, Processo> processoPorId = processos.stream()
        .collect(Collectors.toMap(Processo::getId, p -> p));
List<Honorario> honorarios = honorarioRepository.findByProcessoIdIn(processoPorId.keySet());
```
This is a single query per tenant for all of that tenant's honorários, instead of one query per processo — the exact discipline Pitfall 7 and the Performance Traps table in PITFALLS.md require.

---

### `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` (job, batch/edge-triggered — NEW)

No single existing file has this shape (scheduled trigger + cross-tenant loop + per-tenant/per-entity isolation). The correct pattern is a synthesis of three things already in the codebase — each extracted below with exact line numbers.

**1. Class shape / package precedent — `seed/DatabaseSeeder.java:1-18`** (the only other "runs broadly across the system, not per-request" class today, per ARCHITECTURE.md's own explicit analogy):
```java
@Component
@RequiredArgsConstructor
public class DatabaseSeeder implements CommandLineRunner {
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    // ... 12 more repository fields, all constructor-injected
```
Adopt: `@Component` (not `@Service` — this class is an orchestrator/entry-point, not an injectable business-logic dependency of anything else, matching why `DatabaseSeeder` is `@Component` while `NotificacaoService`/`RiscoPrazoService` are `@Service`), `@RequiredArgsConstructor` with `private final` fields for every collaborator (`TenantRepository`, `ProcessoRepository`, `PrazoRepository`, `EventoRepository`, `HonorarioRepository`, `NotificacaoRepository`, `UserRepository`, `RiscoPrazoService`, `NotificacaoService`). **Do not copy** `DatabaseSeeder`'s error handling (it has none) or its `System.out.println` logging (not a pattern to propagate) — for those, use pattern 3 below instead.

**2. Deterministic-date testable split — `services/RiscoPrazoService.java:24-36`** (built specifically for this phase, per its own code comment: *"usado pela Phase 88 para determinismo em testes"*):
```java
// 3-arg: `hoje` injetável — usado pela Phase 88 para determinismo em testes.
public String computeRisco(LocalDate dataLimite, String prioridade, LocalDate hoje) { ... }

// 2-arg: wrapper de conveniência — comportamento byte-idêntico ao atual (default hoje = now).
public String computeRisco(LocalDate dataLimite, String prioridade) {
    return computeRisco(dataLimite, prioridade, LocalDate.now());
}
```
Apply the **same 2-arg/3-arg split shape to the job itself**: the public `@Scheduled` entry point takes no parameters (Spring requires this) and delegates immediately to a package-private overload that takes `LocalDate hoje` explicitly — this is what `AlertasDiariosJobTest` calls directly with a fixed date, with zero Spring context:
```java
@Scheduled(cron = "0 0 6 * * *", zone = "Atlantic/Cape_Verde")
public void executar() {
    executar(LocalDate.now());
}

void executar(LocalDate hoje) {
    for (Tenant tenant : tenantRepository.findAll()) {
        try {
            processarTenant(tenant.getId(), hoje);
        } catch (Exception e) {
            log.error("Falha ao processar alertas diários para tenant {}", tenant.getId(), e);
        }
    }
}
```
Every downstream call (`riscoPrazoService.computeRisco(dataLimite, prioridade, hoje)`, `riscoPrazoService.computeRiscoEvento(dataEvento, prioridade, hoje)`, the honorário `ChronoUnit.DAYS.between(dataAcordo, hoje)` check) must thread the **same** `hoje` value through — never call `LocalDate.now()` a second time inside the loop.

**3. Batch-preload + per-entity isolation — `controllers/ResourceController.java:907-922`** (the exact existing "avoid N+1" idiom, already used for a structurally identical problem — enriching processos with prazo-derived risk and responsável names in one pass):
```java
// Enrich with responsavel_nome, risco_mais_critico, tem_prazo_escalonado
// Fetch all prazos for tenant once to avoid N+1
List<Prazo> allPrazos = prazoRepository.findByTenantId(tenantId);
Map<UUID, List<Prazo>> prazosPorProcesso = new HashMap<>();
for (Prazo prazo : allPrazos) {
    prazosPorProcesso.computeIfAbsent(prazo.getProcessoId(), k -> new ArrayList<>()).add(prazo);
}

// Batch-load responsáveis to avoid N+1 queries
Set<UUID> responsavelIds = sorted.stream()
        .map(Processo::getResponsavelId)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());
Map<UUID, User> responsaveisMap = userRepository.findAllById(responsavelIds).stream()
        .filter(u -> tenantId.equals(u.getTenantId()))
        .collect(Collectors.toMap(User::getId, u -> u));
```
`processarTenant(tenantId, hoje)` should open with the equivalent for this job: fetch `processoRepository.findByTenantId(tenantId)` once → build `Map<UUID, Processo> processoPorId`; fetch `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` once → keep as `List<User> admins` for the whole tenant iteration (per ARCHITECTURE.md Pattern 4's explicit note: *"it must fetch that tenant's ADMIN user list once per tenant iteration (not once per prazo) and reuse it"*). Then three per-entity-type methods (`processarPrazos`, `processarEventos`, `processarHonorarios` — exact names are Claude's Discretion per CONTEXT.md), each with its **own inner per-entity try/catch** so one malformed `Prazo`/`Evento`/`Honorario` cannot block the rest of that same tenant's scan (CONTEXT.md: *"uma entidade com dados inconsistentes não deve impedir a verificação das restantes entidades do mesmo tenant"*).

**4. The write choke point — `services/NotificacaoService.java:33-66`** (the only place any code may call `notificacaoRepository.save(...)`; `criar(...)` is `public`):
```java
public Notificacao criar(UUID tenantId, UUID destinatarioId, String categoria, String titulo,
                          String mensagem, String entidadeTipo, String entidadeId, String linkUrl) {
    if (tenantId == null || destinatarioId == null) {
        throw new IllegalArgumentException("tenantId e destinatarioId são obrigatórios");
    }
    userRepository.findById(destinatarioId)
            .filter(u -> tenantId.equals(u.getTenantId()))
            .orElseThrow(() -> new IllegalArgumentException(
                    "destinatarioId não pertence ao tenant informado"));
    // ... blank/length validation, then notificacaoRepository.save(...)
}
```
The job calls this directly for **every** recipient (both the primary `responsavelId` and each pre-fetched admin), guarded by the new existence-check and wrapped in the exact per-recipient `try/catch (IllegalArgumentException)` shape already used 6+ times in this same file (e.g. `NotificacaoService.java:127-134`, `165-172`):
```java
if (responsavelId != null &&
    !notificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
            tenantId, responsavelId, "prazo", prazo.getId().toString(), categoria)) {
    try {
        notificacaoService.criar(tenantId, responsavelId, categoria, titulo, mensagem,
                "prazo", prazo.getId().toString(), linkUrl);
    } catch (IllegalArgumentException ex) {
        log.warn("{}: responsavelId {} inválido/órfão, notificação primária ignorada",
                categoria, responsavelId, ex);
    }
}
for (User admin : admins) {
    if (!notificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
            tenantId, admin.getId(), "prazo", prazo.getId().toString(), categoria)) {
        try {
            notificacaoService.criar(tenantId, admin.getId(), categoria, tituloAdmin, mensagemAdmin,
                    "prazo", prazo.getId().toString(), linkUrl);
        } catch (IllegalArgumentException ex) {
            log.warn("{}: admin {} inválido/órfão, notificação ADMIN ignorada", categoria, admin.getId(), ex);
        }
    }
}
```

**IMPORTANT — flagged design decision, not a copy-paste-able analog:** CONTEXT.md line 46 says ADMIN fan-out should go "via `NotificacaoService.notificarAdmins` já existente." Read literally, this is **not directly possible**: `notificarAdmins(...)` is declared **package-private** (`void notificarAdmins(...)`, no `public` modifier — `NotificacaoService.java:87,97`), with an explicit comment stating *"Package-private porque só é chamado a partir deste serviço (e do teste, no mesmo pacote)"*. `AlertasDiariosJob` lives in `com.lexcv.jobs`, a different package, so it **cannot call `notificarAdmins` at all** — this will not compile. Two ways to reconcile CONTEXT.md's intent with this constraint, for the planner to pick between:
- **(a) Recommended:** the job does its own admin fan-out inline exactly as shown above, calling the already-`public` `criar(...)` directly for each pre-fetched admin. This is not a workaround — it's actually **required anyway** by ARCHITECTURE.md Pattern 4's own explicit instruction to fetch the admin list **once per tenant**, not once per entity: calling `notificarAdmins(...)` per-entity (even if it were public) would re-run `findByTenantIdAndRoleName` on every single prazo/evento/honorário, reproducing the exact "resolving ADMIN inside the innermost loop" performance trap PITFALLS.md warns against. No modification to `NotificacaoService.java` needed under this option.
- **(b) Alternative:** add 3 new public methods to `NotificacaoService` (`notificarPrazoCritico`, `notificarEventoCritico`, `notificarHonorarioAtrasado`), mirroring `notificarFaseEntrada`/`notificarProcessoAtribuido`'s exact shape, each internally calling the existing package-private `notificarAdmins`. This keeps "all recipient-resolution logic lives in `NotificacaoService`" as an absolute invariant, at the cost of `NotificacaoService.java` becoming a **modified** file this phase and re-querying admins per-entity unless `notificarAdmins` itself is also refactored to accept a pre-fetched admin list.

Given ARCHITECTURE.md's own explicit once-per-tenant admin-batching requirement, **(a) is the better fit** — flagging both so the planner can make this call explicitly rather than hit a silent compile error mid-implementation.

**5. Logging convention — `@Slf4j` (Lombok), not `System.out.println`:** `NotificacaoService.java:8,21` (`import lombok.extern.slf4j.Slf4j; ... @Slf4j`) is the pattern to copy; `DatabaseSeeder`'s `System.out.println("🌱 Seeding...")` is not (that class predates the logging convention this milestone establishes). Use `log.error(...)` for the outer per-tenant catch and `log.warn(...)` for per-recipient/per-entity catches, matching severity levels already used in `NotificacaoService`.

**A pre-existing field to explicitly NOT reuse for idempotency:** `Prazo.escalonado` (`Prazo.java:45-48`, `Boolean`, default `false`) already exists and is already written by `ResourceController.createPrazo`/`togglePrazoConcluido` (`ResourceController.java:1581,1589,1626-1627`) — but it is a pure UI-display snapshot ("is this prazo currently escalated, as of its last create/update"), recomputed only on create/toggle, never re-evaluated by a background process, and **not** part of CONTEXT.md's idempotency design (which is explicitly the `Notificacao`-table existence-check). Do not read or write `escalonado` from this job — it is an unrelated, pre-existing concern that happens to sound similar.

**A null-safety edge case found by direct inspection, not covered explicitly in CONTEXT.md:** `Honorario.dataAcordo` (`Honorario.java:31-32`) has no `nullable = false` — a null value would throw `NullPointerException` from `ChronoUnit.DAYS.between(null, hoje)`. CONTEXT.md's per-entity try/catch isolation principle (line 27) covers this if applied per-honorário inside the loop; alternatively guard explicitly with `if (h.getDataAcordo() == null) continue;` alongside the already-specified `valorTotal == null` skip (CONTEXT.md line 42).

---

### `backend/src/main/java/com/lexcv/config/SchedulingConfig.java` (config — NEW)

**Analog:** `backend/src/main/java/com/lexcv/config/MinioConfig.java` (full file, 48 lines)

```java
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

    @Bean
    public S3Client s3Client(MinioProperties props) { ... }

    @Bean
    public S3Presigner s3Presigner(MinioProperties props) { ... }
}
```

**Pattern to copy:** the `@Configuration`, single-responsibility, no-business-logic shape. `SchedulingConfig` will actually be **simpler** than this analog — no `@Bean` methods at all, just the enabling annotation:
```java
package com.lexcv.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class SchedulingConfig {
}
```
Confirmed via `BackendApplication.java` (full file read) that `@EnableScheduling` is not present anywhere yet — no risk of duplicate-annotation conflict. CONTEXT.md line 19 is explicit that this must be its own dedicated class, not attached to `BackendApplication` directly (matching the one-`@Configuration`-per-concern convention `MinioConfig`/`SecurityConfig` already establish).

---

### `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java` (test — NEW)

**Analog A — Mockito shape:** `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` (full file, 457 lines — described in its own header comment as *"Primeiro teste Mockito do backend"*)
```java
@ExtendWith(MockitoExtension.class)
class NotificacaoServiceTest {
    @Mock
    private NotificacaoRepository notificacaoRepository;
    @Mock
    private UserRepository userRepository;

    private static final UUID TENANT_ID = UUID.randomUUID();
    ...

    @Test
    void criar_doisDestinatariosDistintos_geramLinhasIndependentesComEstadoLidaProprio() {
        NotificacaoService service = new NotificacaoService(notificacaoRepository, userRepository);
        when(userRepository.findById(DESTINATARIO_A)).thenReturn(Optional.of(...));
        when(notificacaoRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ...
        ArgumentCaptor<Notificacao> captor = ArgumentCaptor.forClass(Notificacao.class);
        verify(notificacaoRepository, times(2)).save(captor.capture());
    }
}
```
`AlertasDiariosJobTest` needs the same shape but with more mocks (`@Mock TenantRepository`, `PrazoRepository`, `EventoRepository`, `HonorarioRepository`, `ProcessoRepository`, `NotificacaoRepository`, `UserRepository`, plus a **real** (not mocked) `RiscoPrazoService` instance — it has no collaborators, so `new RiscoPrazoService()` is simpler and more faithful than mocking it, exactly how `RiscoPrazoServiceTest` itself is constructed) and a real-or-mocked `NotificacaoService` (mocking it directly and verifying `criar(...)` calls via `ArgumentCaptor` is likely simplest, avoiding a 3-level mock chain).

**Analog B — fixed-date determinism convention:** `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java:19-22`
```java
private final RiscoPrazoService service = new RiscoPrazoService();
private static final LocalDate HOJE = LocalDate.of(2026, 1, 15);
```
`AlertasDiariosJobTest` should call the package-private `job.executar(hoje)` overload directly with a fixed `LocalDate`, never the public no-arg `executar()` — this is precisely why that 2-arg/3-arg split (see Pattern Assignment above) exists, and is explicitly required by PITFALLS.md's own verification guidance: *"Run the job twice on consecutive simulated days against unchanged data; second run produces zero new notifications for unchanged items"* and *"manually invoke the job's core logic outside an authenticated request and confirm no NPE"* (Pitfall 1/4 verification steps). Concretely: no `SecurityContextHolder` setup anywhere in this test class proves Pitfall 1 is avoided (if the job's code path ever touched `SecurityContext`, the test would NPE without any mock for it — the *absence* of that mock is itself the proof).

**Test-worthy scenarios directly implied by CONTEXT.md's decisions (for the planner to assign, not exhaustive):**
- Running `executar(hoje)` twice with unchanged data produces zero new `criar(...)` calls the second time (idempotency — CONTEXT.md line 35a).
- A prazo crossing `proximo` → `vencido` between two runs produces exactly one new `criar(...)` call for `PRAZO_VENCIDO`, even though `PRAZO_PROXIMO` already has a row (CONTEXT.md line 35b).
- One tenant throwing inside `processarTenant` does not prevent other tenants in the same `tenantRepository.findAll()` loop from being processed (mirrors `RiscoPrazoServiceTest`'s isolation-of-concern style, but at the tenant-loop level — inject a mock that throws for one tenant ID only).
- `Honorario.valorTotal == null` is skipped silently, never throws (CONTEXT.md line 42).
- `Honorario` with `totalPago >= valorTotal` (fully paid) is skipped.
- Fewer than 30 days since `dataAcordo` does not notify; exactly 30 or more does (`ChronoUnit.DAYS.between(...) >= 30`, CONTEXT.md line 41).

---

## Shared Patterns

### Constructor injection via `@RequiredArgsConstructor` + `private final` fields
**Source:** universal in this codebase — `NotificacaoService.java:19-25`, `RiscoPrazoService.java:11-13`, `DatabaseSeeder.java:16-34`
**Apply to:** `AlertasDiariosJob` (9 collaborator fields: 5 repositories it queries directly + `RiscoPrazoService` + `NotificacaoService` + `NotificacaoRepository` for the existence-check + `UserRepository` for admin fan-out)

### Explicit `tenantId` parameter threading, never `SecurityContextHolder`
**Source:** `.planning/research/ARCHITECTURE.md` Pattern 2, `.planning/research/PITFALLS.md` Pitfall 1; anti-pattern confirmed at `ResourceController.java:122-126` / `ParecerController.java:51-55` / `NotificacaoController.java:52-56` (all three existing controllers' `getTenantId()` helpers — structurally identical, all read `SecurityContextHolder.getContext().getAuthentication()`)
**Apply to:** every method `AlertasDiariosJob` defines — none may call `getTenantId()` or anything that transitively touches it. `tenantId` is a parameter, never inferred.

### Per-tenant and per-entity `try/catch` isolation
**Source:** `.planning/research/ARCHITECTURE.md` Pattern 2's code example; `.planning/research/PITFALLS.md` Pitfall 6; existing per-recipient isolation precedent already in `NotificacaoService.java:106-111,128-134,169-172` (comments tagged `CR-01`/`CR-02` from Phase 87 code review — same isolation principle, one level up)
**Apply to:** outer loop over `tenantRepository.findAll()`, and inner loops over each tenant's prazos/eventos/honorários

### Batch-preload once per tenant, map in Java, never query-per-entity
**Source:** `ResourceController.java:907-922` (`prazosPorProcesso`, `responsaveisMap`); `.planning/research/PITFALLS.md` Pitfall 7 and Performance Traps table
**Apply to:** `AlertasDiariosJob.processarTenant(...)` — fetch processos/admins once per tenant, not once per prazo/evento/honorário

### `NotificacaoService.criar(...)` as the sole write choke point
**Source:** `NotificacaoService.java:33-66`
**Apply to:** every notification the job creates — never call `notificacaoRepository.save(...)` directly from the job

### Cron with explicit `zone`, never `fixedRate`/`fixedDelay`
**Source:** `.planning/research/PITFALLS.md` Pitfall 5; CONTEXT.md line 20-21 (locked decision, not discretion)
**Apply to:** the single `@Scheduled(cron = "0 0 6 * * *", zone = "Atlantic/Cape_Verde")` annotation on `AlertasDiariosJob.executar()`

### `@Slf4j` logging, not `System.out.println`
**Source:** `NotificacaoService.java:8,21` (`@Slf4j`, `log.warn(...)`)
**Apply to:** `AlertasDiariosJob` — do not follow `DatabaseSeeder`'s older `System.out.println` convention

---

## No Analog Found

| File/Method | Role | Data Flow | Reason |
|---|---|---|---|
| `AlertasDiariosJob` as a whole class | job | batch | First `@Scheduled` background job in this codebase — no HTTP-triggered controller pattern applies directly; synthesized from 3 existing files instead (see Pattern Assignment above) |
| `HonorarioRepository.findByProcessoIdIn(Collection<UUID>)` | repository method | batch | First `...In(Collection<...>)` derived-query method anywhere in `repositories/` — no existing method to copy verbatim, though the naming convention (Spring Data `In` suffix) and the Java-side batch-map usage pattern are both already well-established elsewhere |
| `SchedulingConfig` | config | n/a | First `@EnableScheduling` usage — `MinioConfig` is a structural analog (small dedicated `@Configuration`) but has `@Bean` methods `SchedulingConfig` won't need; genuinely simpler than any existing config class |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/{jobs,config,services,repositories,models,controllers,seed}/`, `backend/src/test/java/com/lexcv/services/`, `backend/src/main/resources/application.yml`, `backend/migrations/`, `backend/src/main/java/com/lexcv/BackendApplication.java`
**Files scanned/read directly:** 24 (8 required-reading files, 8 additional repositories/config/tests located via Glob/Grep, 8 supporting checks — `DatabaseSeeder.java` partial read, `ResourceController.java` targeted excerpt via grep+offset, `BackendApplication.java`, `application.yml`, migration `86-create-notificacao-table.sql`)
**Pattern extraction date:** 2026-07-09
**Upstream inputs used:** `88-CONTEXT.md` (full, primary source — highly prescriptive), `.planning/research/ARCHITECTURE.md` (milestone-level, Patterns 1-4 + Anti-Patterns 1-5 + Suggested Build Order), `.planning/research/PITFALLS.md` (milestone-level, Pitfalls 1/4/5/6/7 map directly to this phase per its own Pitfall-to-Phase Mapping table). No phase-level RESEARCH.md exists (deliberately skipped per orchestrator instructions).
