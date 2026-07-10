# Phase 85: Consolidação da Lógica de "Prazo Crítico" - Pattern Map

**Mapped:** 2026-07-08
**Files analyzed:** 2 (1 new file, 1 modified file containing 8 distinct internal modification points)
**Analogs found:** 2 / 2 structural analogs (see note below — this phase is dominated by a verbatim **extraction**, not a build-new-following-analog operation)

**Note on this phase's shape:** Phase 85 is a mechanical refactor, not new feature work. The new file's actual logic does not come from "the closest similar file" — it comes verbatim from `ResourceController.computeRisco()`, which is being cut and relocated. The "analog" search below therefore answers two different questions separately: (1) what should the *shape* of the new `@Service` class look like (answered by `StorageService.java` / `SetupService.java`), and (2) what is the *exact logic* to move and where does it currently get called from (answered by `ResourceController.java` itself, before/after).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java` | service | transform (pure computation, no I/O, no persistence) | `backend/src/main/java/com/lexcv/services/StorageService.java` + `SetupService.java` (structure only) | role-match (structural) — logic itself is a **verbatim move**, not an imitation |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | controller | request-response (8 call sites being repointed: 5 `Prazo`-based, 3 `Evento`-based) | itself (before → after) | exact — this is a repoint-and-delete edit of existing code, not new code written against a pattern |

## Pattern Assignments

### `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java` (NEW — service, transform)

**Analog for structure:** `backend/src/main/java/com/lexcv/services/StorageService.java`, `backend/src/main/java/com/lexcv/services/SetupService.java` — these are the **only two** `@Service` classes that exist anywhere in `com.lexcv` today (verified: `grep "@Service"` across `backend/src/main/java` returns exactly these two files). Both follow the identical shape and must be matched for consistency.

**Class declaration + DI convention** (`StorageService.java` lines 26-33):
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class StorageService implements ApplicationRunner {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final MinioProperties props;
```

Same shape, no `@Slf4j`/no `implements` (`SetupService.java` lines 21-23 + 31-35):
```java
@Service
@RequiredArgsConstructor
public class SetupService {
    ...
    private final SystemSettingRepository systemSettingRepository;
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
```

**Convention to copy:** `@Service` + Lombok `@RequiredArgsConstructor` over `private final` fields, constructor-injected — no interfaces, no static methods, no factory pattern anywhere in this codebase's service layer.

**Important deviation to flag for the planner:** unlike both existing services, `RiscoPrazoService` needs **zero** injected dependencies — no repository, no config bean, no other service. The logic is a pure function of its arguments (a date and a priority string in, a risk-level string out). `@RequiredArgsConstructor` on a class with no `final` fields simply generates an implicit no-arg constructor — that's correct and sufficient here. It still produces an injectable, mockable Spring bean (satisfying CONTEXT.md's explicit requirement: "sem precedente de utilitário estático neste código-base, e métodos estáticos não são mockáveis"). Do not invent an artificial dependency just for symmetry with `StorageService`/`SetupService`.

**Logic to move verbatim** (`backend/src/main/java/com/lexcv/controllers/ResourceController.java` lines 1397-1404 — this is the actual payload of the extraction, not a pattern to imitate but code to relocate):
```java
private String computeRisco(LocalDate dataLimite, String prioridade) {
    if (dataLimite == null) return "ok";
    LocalDate hoje = LocalDate.now();
    if (dataLimite.isBefore(hoje)) return "vencido";
    long diasRestantes = ChronoUnit.DAYS.between(hoje, dataLimite);
    int limiarProximo = "ALTA".equalsIgnoreCase(prioridade) ? 7 : 3;
    return diasRestantes <= limiarProximo ? "proximo" : "ok";
}
```
- Change `private` → `public` (must be callable from `ResourceController` as an injected bean).
- Per `.planning/research/ARCHITECTURE.md` Pattern 3, add a 3-arg overload that takes `hoje` (the reference date) explicitly, with the signature above becoming a 2-arg convenience wrapper defaulting `hoje = LocalDate.now()`. This preserves the exact current behavior at all 5 existing `Prazo` call sites with zero change, while giving `AlertasDiariosJob` (Phase 88) a way to inject a deterministic date in future tests.
- Imports needed in the new file: `java.time.LocalDate`, `java.time.temporal.ChronoUnit` — both already imported in `ResourceController.java` (lines 34 and 40 of the current import block) and can be copied directly.

**New sibling method — `computeRiscoEvento(...)`:** must reuse the identical 7-day/ALTA vs. 3-day/other threshold table — not a second, independently-tuned window. Type mismatch to resolve: `Prazo.dataLimite` is `LocalDate` (`backend/src/main/java/com/lexcv/models/Prazo.java:31`), but `Evento.dataInicio`/`Evento.dataFim` are both `LocalDateTime` (`backend/src/main/java/com/lexcv/models/Evento.java:32,35`) — the new method needs a `LocalDateTime`-accepting signature (or the call site converts via `.toLocalDate()` before calling the existing `LocalDate` overload). Either is defensible; pick one and apply it consistently across all 3 `Evento` call sites (see below).

---

### `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (MODIFIED — controller, request-response)

8 distinct modification points: add 1 constructor field, repoint 5 `Prazo`-based call sites + 3 `Evento`-based ad-hoc blocks, then delete the private method.

**Constructor field to add** — follow the exact existing injection shape for a service collaborator (`storageService`, already present at line 66 of the field block spanning lines 49-71):
```java
private final StorageService storageService;   // existing precedent, line 66
```
→ add `private final RiscoPrazoService riscoPrazoService;` to the same block; add `import com.lexcv.services.RiscoPrazoService;` alongside the existing `import com.lexcv.services.StorageService;` (line 6).

**Call site 1 — `listProcessos` enrichment, `risco_mais_critico`/`tem_prazo_escalonado`** (lines 941-949):
```java
String riscoMaisCritico = "ok";
boolean temEscalonado = false;
for (Prazo pr : ativos) {
    String r = computeRisco(pr.getDataLimite(), pr.getPrioridade());
    if ("vencido".equals(r)) {
        riscoMaisCritico = "vencido";
    } else if ("proximo".equals(r) && !"vencido".equals(riscoMaisCritico)) {
        riscoMaisCritico = "proximo";
    }
    ...
```
→ `String r = riscoPrazoService.computeRisco(pr.getDataLimite(), pr.getPrioridade());` — one-line repoint, rest of the loop body unchanged.

**Call sites 2 & 3 — `listPrazos`** (line 1417) **and `listAllPrazos`** (line 1439) — identical one-line shape in both:
```java
String risco = computeRisco(p.getDataLimite(), p.getPrioridade());
```
→ `riscoPrazoService.computeRisco(p.getDataLimite(), p.getPrioridade())`.

**Call site 4 — `createPrazo`** (lines 1483, 1503):
```java
String risco = computeRisco(payload.dataLimite(), prioridade);
...
response.put("risco", computeRisco(saved.getDataLimite(), saved.getPrioridade()));
```
→ both repointed to `riscoPrazoService.computeRisco(...)`.

**Call site 5 — `togglePrazoConcluido`** (lines 1530-1531, 1543):
```java
boolean nowEscalonado = !nowConcluido &&
        ("proximo".equals(computeRisco(prazo.getDataLimite(), prazo.getPrioridade()))
                || "vencido".equals(computeRisco(prazo.getDataLimite(), prazo.getPrioridade())));
...
response.put("risco", computeRisco(saved.getDataLimite(), saved.getPrioridade()));
```
→ all three repointed. Note the existing code already calls `computeRisco` twice redundantly here (line 1530 and 1531 with identical arguments) — preserve that redundancy as-is (out of scope to also optimize this) unless the planner explicitly decides otherwise; the phase's stated goal is zero behavior change, not micro-optimization.

**Delete once all 5 sites above are repointed** (lines 1397-1404): remove the private `computeRisco` method from `ResourceController` entirely. CONTEXT.md is explicit: "não deixar as duas versões coexistirem" (do not leave both versions coexisting) — a leftover pass-through wrapper would just be a 6th copy waiting to drift.

**Call site 6 — `agendaUrgentesCount` helper, feeds `/dashboard` KPI's `DashboardKpiResponse.prazos_vencer` field** (lines 2748-2753) — today has **no date window at all**, just a pending-ALTA count:
```java
private long agendaUrgentesCount(UUID tenantId) {
    return eventoRepository.findByTenantIdAndConcluido(tenantId, false)
            .stream()
            .filter(e -> "ALTA".equalsIgnoreCase(e.getPrioridade()))
            .count();
}
```
→ repoint to count events where `riscoPrazoService.computeRiscoEvento(...)` returns `"proximo"` or `"vencido"` — this **changes** behavior (adds a date window that didn't exist before), which is the explicit, intended purpose of CONTEXT.md's decision, not a regression: "reutilize a mesma tabela de limiares... em vez das 3 janelas fixas/inconsistentes."

**Call site 7 — `getProcessosDashboard`'s local `prazosCriticosCount`, feeds `ProcessosDashboardData.OperacionalData.prazos_criticos_count`** (lines 2856-2867) — today a fixed 7-day window on `Evento.dataFim`, ignoring `prioridade`:
```java
LocalDateTime today = LocalDateTime.now();
LocalDateTime sevenDays = today.plusDays(7);
long prazosCriticosCount = 0;

List<Evento> eventos = eventoRepository.findByTenantIdAndConcluido(tenantId, false);
for (Evento e : eventos) {
    if (e.getDataFim() != null) {
        if (!e.getDataFim().isBefore(today) && !e.getDataFim().isAfter(sevenDays)) {
            prazosCriticosCount++;
        }
    }
}
```
→ replace the fixed-window `if` with a call to `riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade())`, counting `"proximo"`/`"vencido"` results.

**Call site 8 — `getUpcomingEventos` / `GET /eventos/upcoming`** (lines 2246-2270) — today a configurable window (`days` param, default 7, capped 30) on `Evento.dataInicio`, ignoring `prioridade`:
```java
int effectiveDays = Math.min(days, 30);
UUID tenantId = getTenantId();
List<Evento> eventos = eventoRepository.findByTenantId(tenantId);
LocalDateTime now = LocalDateTime.now();
LocalDateTime limit = now.plusDays(effectiveDays);
eventos.removeIf(e -> Boolean.TRUE.equals(e.getConcluido()));
eventos.removeIf(e -> e.getDataInicio() == null || e.getDataInicio().isBefore(now) || e.getDataInicio().isAfter(limit));
```
**Flag for planning (not a blocker, just a judgment call CONTEXT.md leaves to Claude's discretion):** this endpoint's `days` query param is a genuinely separate, user/frontend-controlled feature (how far ahead the bell dropdown looks), distinct from a risk-level verdict — it has no `if/else "vencido"/"proximo"/"ok"` branch today at all, just an inclusion filter. CONTEXT.md and ARCHITECTURE.md both still name it as one of the "3 ad-hoc `Evento` implementations" to consolidate. Decide during planning whether `computeRiscoEvento(...)` (a) replaces the inclusion filter outright (collapsing `days` semantics into the fixed 7/3-day threshold table — a behavior change beyond "same data, same result"), or (b) is additionally exposed as a `risco` field on each item in the response, layered on top of the existing `days`-windowed filter (no behavior change to the filter itself, only enrichment). Option (b) is more conservative and safer against the phase's "zero regressão observável" success criterion (ROADMAP.md Phase 85, criterion 2) if `days` is meant to stay independently configurable.

## Shared Patterns

### Dependency injection convention (applies to the one constructor-field addition)
**Source:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:66` — `private final StorageService storageService;`, inside the existing constructor field block (lines 49-71), all populated via Lombok `@RequiredArgsConstructor` at the class level (line 46).
**Apply to:** adding `private final RiscoPrazoService riscoPrazoService;` to that same block. No manual constructor to write — Lombok generates it.

### No auth/tenant-scoping/error-handling pattern applies inside the new service itself
`computeRisco`/`computeRiscoEvento` take only a date and a priority string — no repository access, no `tenantId`, no `@PreAuthorize`, no `try/catch`. This is the one file in this batch where those usual pattern categories (Auth/Guard, Error Handling, Validation) genuinely don't apply. Do **not** add `getTenantId()` or tenant checks into `RiscoPrazoService` — tenant scoping remains entirely the caller's (`ResourceController`'s) responsibility both before and after the extraction, exactly as it works today (the private method never took a `tenantId` either).

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| A test file for `RiscoPrazoService` (name/existence TBD by planner) | test | n/a | `backend/src/test/` does not exist at all (confirmed: `Glob` for `backend/src/test/**/*.java` returns zero files) despite `spring-boot-starter-test` and `spring-security-test` both being present in `backend/pom.xml`. There is no existing test-file-structure convention anywhere in this codebase to copy. If the planner scopes a test for this phase (reasonable, given ARCHITECTURE.md's explicit rationale that this extraction exists partly *to be* mockable/testable), it starts from a blank slate: plain JUnit 5 is available via `spring-boot-starter-test`, and since `RiscoPrazoService` is dependency-free, a test needs no `@SpringBootTest`/`@WebMvcTest`/`MockMvc` — just `new RiscoPrazoService()` and direct assertions against the 7-day/ALTA vs. 3-day/other threshold table (vencido / próximo / ok), including the `hoje` boundary day itself and the `null` date case. |

## Metadata

**Analog search scope:** `backend/src/main/java/com/lexcv/services/` (both existing services), `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (full extraction source + all call sites), `backend/src/main/java/com/lexcv/models/{Prazo,Evento}.java` (field types), `backend/src/test/` (confirmed empty), `backend/pom.xml` (test-stack confirmation)
**Files scanned:** 9 (`StorageService.java`, `SetupService.java`, `ResourceController.java`, `Prazo.java`, `Evento.java`, `PrazoRepository.java`, `EventoRepository.java`, `MinioConfig.java`, `pom.xml`)
**Pattern extraction date:** 2026-07-08
