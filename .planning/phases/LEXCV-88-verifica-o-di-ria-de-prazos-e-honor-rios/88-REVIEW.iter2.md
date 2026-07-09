---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
reviewed: 2026-07-09T21:18:23Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - backend/src/main/java/com/lexcv/config/SchedulingConfig.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
  - backend/src/main/java/com/lexcv/repositories/HonorarioRepository.java
  - backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java
  - backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java
findings:
  critical: 0
  warning: 5
  info: 1
  total: 6
status: issues_found
---

# Phase LEXCV-88: Code Review Report

**Reviewed:** 2026-07-09T21:18:23Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Reviewed the codebase's first `@Scheduled`/cross-tenant background job (`AlertasDiariosJob`) plus its two enabling repository additions and its dedicated `@Configuration` class, against the six specific adversarial risk vectors called out for this phase. Read the full call graph, not just the five listed files: `NotificacaoService`, `RiscoPrazoService`, the `Prazo`/`Evento`/`Honorario`/`Processo`/`User`/`Tenant`/`Notificacao` entities, `ProcessoRepository`/`PrazoRepository`/`EventoRepository`/`UserRepository`/`TenantRepository`, all three `docker-compose*.yml` files, the `t_notificacao`/`t_honorario` manual migration SQL, and the phase's own `88-CONTEXT.md`/`88-01-SUMMARY.md`/`88-02-SUMMARY.md`. Verified `Atlantic/Cape_Verde` resolves correctly against the actual installed JDK (`ZoneId.of("Atlantic/Cape_Verde")` succeeds under Java 23.0.2).

Verification results for the six named risk vectors:
1. **`SecurityContextHolder`/`getTenantId()` on the background thread** — confirmed absent everywhere in the call chain (`AlertasDiariosJob`, `NotificacaoService`, `RiscoPrazoService`). No NPE risk from this vector. Not a finding.
2. **Explicit `tenantId` threading at every query** — confirmed for 6 of 7 query call sites. One gap found: `HonorarioRepository.findByProcessoIdIn` (see WR-03).
3. **3-layer exception isolation** — the documented top-level/per-tenant/per-entity structure exists and is exercised by tests, but two gaps were found in its actual granularity/coverage (see WR-02, WR-04).
4. **Idempotency race-freedom under the single-instance assumption** — the single-instance assumption is currently true (verified: no `replicas:`/`deploy.replicas` in `docker-compose.yml`, `docker-compose.prod.yml`, or `docker-compose.hostinger.yml`; no custom `TaskScheduler` bean overriding Spring Boot's default pool-size-1 scheduler; no manual "run job now" endpoint anywhere in the codebase). However, the mechanism has no independent backstop if that assumption ever changes (see WR-01).
5. **Honorario `valorTotal == null` arithmetic safety** — confirmed safe. The null-guard at `AlertasDiariosJob.java:174` executes strictly before the `ChronoUnit.DAYS.between(...)` call at line 183, and `AlertasDiariosJobTest.executar_honorarioValorTotalNulo_ignoradoSemExcecao` exercises this exact path. Not a finding.
6. **Cron zone correctness** — `zone = "Atlantic/Cape_Verde"` is present and valid (verified against a live JDK), not left to container-default UTC. Not a finding. Note, however, that no `@SpringBootTest`-style context-loading test exists anywhere in this backend, so a *future* typo in the cron string or zone ID would only be caught at real application startup, not in CI — flagged only as context for WR-04/WR-02, not as its own separate finding since the current value is correct.

No BLOCKER/critical-severity issues were found — no crash, injection, auth-bypass, or hardcoded-secret defects. The six findings below are all robustness/defense-in-depth gaps that are not currently exploitable given today's deployment topology and data, but would silently degrade (duplicate notifications, missed alerts for a tenant, information disclosure across tenants, or literal "null" in user-facing text) if specific — plausible — future changes are made without revisiting this job.

## Warnings

### WR-01: Notification idempotency has no database-level backstop

**File:** `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:56-57`, `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:212-228`, `backend/migrations/86-create-notificacao-table.sql:19-34`
**Issue:** The entire idempotency guarantee ("running the job twice never duplicates a notification") rests solely on the check-then-act pair in `notificar(...)` — `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(...)` followed later by `notificacaoService.criar(...)`. The `t_notificacao` table (per `86-create-notificacao-table.sql`) has only a non-unique read index (`idx_notificacao_tenant_destinatario_lida_created`) — no unique constraint exists on `(tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)` at the DB layer, in dev, staging, or prod. `88-01-SUMMARY.md` confirms this was a conscious choice: *"No DB unique constraint/migration added for the idempotency tuple in this plan — deliberately deferred... relying on the application-level existence-check on this single-instance deployment."*

  I independently verified the single-instance premise still holds today (no `replicas:` anywhere across all three `docker-compose*.yml`, no custom `TaskScheduler`), so this is not exploitable *right now*. But the codebase's own precedent for an analogous situation — `backend/migrations/82-add-honorario-processo-unique-constraint.sql` — explicitly added a DB-level unique constraint specifically so that *"the multi-instance concurrent-`formalizarProcesso` race this constraint exists to close" doesn't stay "fully open... while appearing closed everywhere else"* the moment the deployment topology changes. Phase 88 doesn't follow that same precedent here. Since scaling the backend to more than one replica (e.g. for zero-downtime deploys) is a routine, low-visibility infra change that lives in a completely different file than this code, nothing today would fail a build, a test, or a deploy if that assumption is silently invalidated — duplicate notifications would just start appearing in production with no error anywhere.
**Fix:** Add a unique index mirroring migration 82's own reasoning, e.g.:
```sql
-- New migration, e.g. 88-add-notificacao-dedup-unique-constraint.sql
CREATE UNIQUE INDEX uk_notificacao_dedup
    ON t_notificacao (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria);
```
and have `notificar(...)` additionally tolerate the resulting constraint-violation exception (e.g. catch `org.springframework.dao.DataIntegrityViolationException` alongside `IllegalArgumentException`) so a future concurrent duplicate attempt fails closed instead of throwing out of the per-entity try/catch.

### WR-02: One failed preload query silently skips ALL alert categories for a tenant, not just the failing one

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:97-106`
**Issue:** `processarTenant` is the *only* thing wrapped by the per-tenant try/catch in `executar(LocalDate)` (lines 84-88). Its own body is not internally protected:
```java
private void processarTenant(UUID tenantId, LocalDate hoje) {
    List<Processo> processos = processoRepository.findByTenantId(tenantId);
    Map<UUID, Processo> processoPorId = processos.stream()
            .collect(Collectors.toMap(Processo::getId, p -> p));
    List<User> admins = userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN");

    processarPrazos(tenantId, hoje, processoPorId, admins);
    processarEventos(tenantId, hoje, processoPorId, admins);
    processarHonorarios(tenantId, hoje, processoPorId, admins);
}
```
If `processoRepository.findByTenantId(tenantId)` or `userRepository.findByTenantIdAndRoleName(tenantId, "ADMIN")` throws (e.g. a transient DB blip), or if `processarPrazos` throws from *outside* its own per-prazo try (only possible via `prazoRepository.findByTenantId(tenantId)` at the head of its for-each, which is evaluated before the per-prazo try block is entered), then `processarEventos` and `processarHonorarios` never run at all for that tenant that day — even though eventos/honorarios verification has no actual dependency on the prazo query having succeeded. This is a stronger violation of 88-CONTEXT.md's stated intent than "one bad entity" isolation: *"Dentro de cada tenant, cada entidade... individual também tem isolamento de falha — uma entidade com dados inconsistentes não deve impedir a verificação das restantes entidades do mesmo tenant"* — here it's not "an entity with bad data," it's an unrelated query hiccup silently suppressing two entire, otherwise-healthy alert categories, with only a single generic `"Falha ao processar alertas diários para tenant {}"` log line to go on.
**Fix:** Give each category (and its preload) independent fault isolation inside `processarTenant`, e.g.:
```java
private void processarTenant(UUID tenantId, LocalDate hoje) {
    Map<UUID, Processo> processoPorId = safeProcessoPorId(tenantId);
    List<User> admins = safeAdmins(tenantId);

    try {
        processarPrazos(tenantId, hoje, processoPorId, admins);
    } catch (Exception e) {
        log.error("Falha ao processar prazos do tenant {}", tenantId, e);
    }
    try {
        processarEventos(tenantId, hoje, processoPorId, admins);
    } catch (Exception e) {
        log.error("Falha ao processar eventos do tenant {}", tenantId, e);
    }
    try {
        processarHonorarios(tenantId, hoje, processoPorId, admins);
    } catch (Exception e) {
        log.error("Falha ao processar honorarios do tenant {}", tenantId, e);
    }
}
```
(with `safeProcessoPorId`/`safeAdmins` falling back to empty collections on failure, logging their own error, so a failure resolving admins doesn't block prazo/evento/honorario processing for the responsável either).

### WR-03: Honorario batch query is the one tenant-scoped read that doesn't take `tenantId` as an explicit parameter

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:172`, `backend/src/main/java/com/lexcv/repositories/HonorarioRepository.java:14`
**Issue:** Every other query in this job is tenant-scoped by an explicit repository parameter: `findByTenantId(tenantId)`, `findByTenantIdAndConcluido(tenantId, false)`, `findByTenantIdAndRoleName(tenantId, "ADMIN")`, `existsByTenantIdAnd...(tenantId, ...)`. The honorario fetch is the exception:
```java
for (Honorario honorario : honorarioRepository.findByProcessoIdIn(processoPorId.keySet())) {
```
`Honorario` has no `tenant_id` column at all (confirmed in `Honorario.java` and in `86`/`82` migrations), so tenant scoping here is entirely implicit: it works *only* because `processoPorId.keySet()` happens to have been built from `processoRepository.findByTenantId(tenantId)` a few lines earlier, combined with `Processo.id` being a globally-unique UUID (not a per-tenant compound key). Today that's safe. But it means this one data path's tenant isolation lives entirely in caller discipline rather than in the query itself — and because the resulting `Processo`/`Honorario` data (numero do processo, valor, etc.) is embedded directly into the notification `mensagem` sent to the *current* tenant's own responsável/admins (a correctly-tenant-scoped recipient), a future refactor that widens or mis-scopes the `Collection<UUID>` passed to `findByProcessoIdIn` would silently leak another tenant's honorario/processo details into this tenant's notification feed — with no query-level guard anywhere to catch it, unlike every other case in this job.
**Fix:** At minimum, add an explicit comment at the call site pinning the invariant this depends on (that `processoPorId.keySet()` must never be widened beyond `processoRepository.findByTenantId(tenantId)`'s output). Better: if a schema change is ever on the table for this area, add `tenant_id` to `t_honorario` and change the method to `findByTenantIdAndProcessoIdIn(UUID tenantId, Collection<UUID> processoIds)` so this query is self-defending like all the others.

### WR-04: Top-level/per-tenant catches only handle `Exception`, not `Throwable` — the exact class of bug the 3-layer design exists to prevent can still occur

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:81-93`
**Issue:**
```java
void executar(LocalDate hoje) {
    try {
        for (Tenant tenant : tenantRepository.findAll()) {
            try {
                processarTenant(tenant.getId(), hoje);
            } catch (Exception e) {
                log.error("Falha ao processar alertas diários para tenant {}", tenant.getId(), e);
            }
        }
    } catch (Exception e) {
        log.error("Falha inesperada na execução do job de alertas diários", e);
    }
}
```
Both catches are `catch (Exception e)`. The class Javadoc and `88-CONTEXT.md` justify this entire 3-layer structure by citing a specific, named Spring Framework risk: *"uma exceção não tratada num `@Scheduled` pode cancelar silenciosamente todas as execuções futuras desse método."* `Error` (e.g. `StackOverflowError`, `OutOfMemoryError`) is not a subtype of `Exception`, so an `Error` raised anywhere in tenant/entity processing bypasses both catch layers and escapes `executar()` uncaught — reproducing precisely the "silently cancels all future scheduled runs" failure mode this design exists to prevent. This is a narrow edge case (JVM-fatal conditions), but it is a real, provable gap relative to the stated design goal, not a hypothetical one.
**Fix:** If the intent is genuinely "nothing this job does can ever silently kill future runs," catch `Throwable` at both layers instead of `Exception` (or at least document explicitly why `Error` is deliberately excluded, e.g. because recovering from OOM is unsafe anyway):
```java
} catch (Throwable e) {
    log.error("Falha ao processar alertas diários para tenant {}", tenant.getId(), e);
}
```

### WR-05: `Evento.titulo` can be null and is concatenated unguarded into user-facing notification text

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:152-153`
**Issue:**
```java
String mensagem = "O evento \"" + evento.getTitulo() + "\" "
        + (RiscoPrazoService.PROXIMO.equals(risco) ? "está a aproximar-se." : "está em atraso.");
```
`Evento.titulo` (`backend/src/main/java/com/lexcv/models/Evento.java:28`) is `private String titulo;` with no `nullable = false` and no bean validation — and `ResourceController.createEvento` (`@PostMapping("/eventos")`, line 2395) binds `@RequestBody Evento evento` directly with no `@Valid` and no manual blank/null check on `titulo` before `eventoRepository.save(evento)`. So a real (if malformed) API call can persist an `Evento` with `titulo == null`. When that evento later crosses a risk threshold, this job produces and permanently persists a notification whose `mensagem` literally reads `O evento "null" está a aproximar-se.` — a genuine, reachable, user-facing data-quality defect (unlike `numeroProcesso(processo)`, which is explicitly null-guarded a few lines above for the exact same class of concern). Not a crash; a real "null" leaking into production notification text delivered to advogados/admins, with no way to correct it after the fact (notifications aren't editable).
**Fix:** Mirror the existing `numeroProcesso(...)` guard pattern:
```java
String tituloTexto = evento.getTitulo() != null ? evento.getTitulo() : "(sem título)";
String mensagem = "O evento \"" + tituloTexto + "\" "
        + (RiscoPrazoService.PROXIMO.equals(risco) ? "está a aproximar-se." : "está em atraso.");
```

## Info

### IN-01: `@MockitoSettings(strictness = Strictness.LENIENT)` is no longer justified now that the GREEN implementation is complete

**File:** `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java:58`
**Issue:** The class-level `@MockitoSettings(strictness = Strictness.LENIENT)` was a documented, legitimate necessity during the TDD RED phase, per `88-02-SUMMARY.md`: *"during the RED phase the job's `executar(LocalDate)` body is empty, so most stubs configured here are never actually invoked... LENIENT keeps the behavioral `verify(...)` assertions below as the real gate in both RED and GREEN."* Now that Task 2 (GREEN) is complete and every stub in the class does appear to be exercised by the real implementation, this class-level override permanently disables Mockito's `UnnecessaryStubbingException` safety net (the default `STRICT_STUBS` from `MockitoExtension`) for all future edits to this test class — a future change that leaves a stub configured-but-unused (e.g. a copy-pasted test scenario, or a stub left behind after production code changes) will no longer be flagged automatically.
**Fix:** Now that the skeleton/RED phase is behind this class, consider removing `@MockitoSettings(strictness = Strictness.LENIENT)` (reverting to Mockito's default `STRICT_STUBS`) and re-running the suite to confirm all configured stubs are still genuinely exercised, restoring the regression-detection benefit for future changes to this test.

---

_Reviewed: 2026-07-09T21:18:23Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
