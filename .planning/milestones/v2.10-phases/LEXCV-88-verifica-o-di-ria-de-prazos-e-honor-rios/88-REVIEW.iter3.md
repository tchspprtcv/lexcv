---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
reviewed: 2026-07-09T22:30:00Z
depth: standard
files_reviewed: 7
files_reviewed_list:
  - backend/src/main/java/com/lexcv/config/SchedulingConfig.java
  - backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java
  - backend/src/main/java/com/lexcv/repositories/HonorarioRepository.java
  - backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java
  - backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java
  - backend/src/main/java/com/lexcv/models/Notificacao.java
  - backend/migrations/88-add-notificacao-dedup-unique-constraint.sql
findings:
  critical: 0
  warning: 5
  info: 1
  total: 6
status: issues_found
---

# Phase LEXCV-88: Code Review Report (re-review)

**Reviewed:** 2026-07-09T22:30:00Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

This is a re-review of `AlertasDiariosJob` and its supporting files, after `88-REVIEW-FIX.md` (iteration 1) applied fixes for the five WARNING findings from the prior pass (`WR-01`–`WR-05`) plus deliberately deferred one INFO finding (`IN-01`). I did not take the fix report's word for it — I independently re-verified each of the five fixes against the current source, then re-traced the full call graph (`NotificacaoService`, `RiscoPrazoService`, `Prazo`/`Evento`/`Processo`/`Honorario`/`User`/`Tenant`/`Notificacao` entities, all six repositories, `application.yml`/`application-prod.yml`, `86-create-notificacao-table.sql`) from scratch for anything new, rather than assuming a clean prior finding list means the code is now clean.

**Prior findings, independently re-verified as correctly fixed:**
- **WR-01** (no DB-level dedup backstop): `Notificacao.java`'s `@UniqueConstraint(columnNames = {"tenant_id", "destinatario_id", "entidade_tipo", "entidade_id", "categoria"})` matches the new migration's `CREATE UNIQUE INDEX uk_notificacao_dedup ON t_notificacao (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)` column-for-column, and `notificar(...)` now catches `DataIntegrityViolationException`. Confirmed `t_notificacao` has no other unique/FK constraints that could be silently mis-attributed by that catch (checked `86-create-notificacao-table.sql`) — the catch is correctly scoped.
- **WR-02** (one failed preload skips all 3 categories): `safeProcessoPorId`/`safeAdmins` plus three independent per-category try/catch blocks in `processarTenant` are in place and functionally correct.
- **WR-03** (Honorario tenant-scoping implicit): fixed at the documented "at minimum" tier — an invariant-pinning comment is present at the `findByProcessoIdIn` call site. No schema change (correctly noted in the fix report as deferred, wider-blast-radius work).
- **WR-04** (`Exception` vs `Throwable` at the two outer layers): both the top-level and per-tenant catches in `executar(LocalDate)` now read `catch (Throwable e)`.
- **WR-05** (`Evento.titulo` null): the `tituloTexto` guard mirrors `numeroProcesso(...)` exactly.

**New findings from this pass:** five warnings, one info. Two of the five (`WR-03`, `WR-04` below) are direct consequences of how the prior fixes were scoped: `WR-02`'s fix added a new code layer that wasn't brought up to `WR-04`'s `Throwable` standard, and `WR-04`'s own fix silently invalidated what the existing "one tenant throws" test actually proves. No BLOCKER/Critical-severity issues found in this pass either — no crash, injection, hardcoded secret, or auth-bypass defect. All findings below are robustness/defense-in-depth or coverage gaps, none currently causing incorrect behavior under today's deployment topology, but each is a concrete, provable gap relative to a design guarantee this same code/phase explicitly states it provides.

## Warnings

### WR-01: Admin fan-out loops have no per-recipient failure isolation, unlike the established sibling pattern

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:169-171` (also `:204-206`, `:249-252`)
**Issue:** In all three `processar*` methods the admin fan-out is a bare loop with no try/catch per iteration, e.g. in `processarPrazos`:
```java
notificar(tenantId, responsavelId, categoria, titulo, mensagem, "prazo", entidadeId, linkUrl);
for (User admin : admins) {
    notificar(tenantId, admin.getId(), categoria, titulo, mensagem, "prazo", entidadeId, linkUrl);
}
```
`notificar(...)` (lines 263-287) only internally catches two specific exception types: `IllegalArgumentException` (orphaned/invalid destinatario) and `DataIntegrityViolationException` (the WR-01 dedup-race backstop from the prior review). Any *other* exception raised while notifying one admin — a transient `DataAccessException`, an unexpected `RuntimeException` surfacing from deeper in `NotificacaoService` — propagates out of the `for` loop uncaught, aborting the remaining admins in that list for that one entidade. This is exactly the scenario `NotificacaoService.notificarAdmins` already guards against, per that method's own comment: *"CR-01 (Phase 87 code review, iteration 2): isolate each admin so one stale/orphaned admin reference can never prevent the rest of the ADMIN fan-out from being notified"* — each admin there is wrapped in its own try/catch around the `criar(...)` call. `AlertasDiariosJob` reimplements this same fan-out inline (a documented, required decision, since `notificarAdmins` is package-private and unreachable from `com.lexcv.jobs`, per `88-02-SUMMARY.md`), but did not carry over the per-recipient isolation that made the original pattern safe. The outer per-entidade `catch (Exception e)` still contains the failure and lets the next prazo/evento/honorario be processed, so this is not job-ending — but one admin further down a multi-admin tenant's list can silently receive zero notification for an entity that genuinely crossed a risk threshold that day.
**Fix:** Wrap each admin notification independently, mirroring `notificarAdmins`:
```java
notificar(tenantId, responsavelId, categoria, titulo, mensagem, "prazo", entidadeId, linkUrl);
for (User admin : admins) {
    try {
        notificar(tenantId, admin.getId(), categoria, titulo, mensagem, "prazo", entidadeId, linkUrl);
    } catch (Exception e) {
        log.warn("Falha ao notificar admin {} para prazo {} do tenant {}", admin.getId(), prazo.getId(), tenantId, e);
    }
}
```
(repeat for the evento/honorario admin loops at lines 204-206 and 249-252).

### WR-02: No-arg `executar()` computes "hoje" with zone-naive `LocalDate.now()`, contradicting the class's own explicit-zone design principle

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:75-78`
**Issue:**
```java
@Scheduled(cron = "0 0 6 * * *", zone = "Atlantic/Cape_Verde")
public void executar() {
    executar(LocalDate.now());
}
```
This class's entire design — its own Javadoc and `88-CONTEXT.md` — is explicit that the job must never rely on the container's default timezone: *"zona explícita Atlantic/Cape_Verde: o container corre em UTC, Cabo Verde é UTC-1... não usar fixedRate/fixedDelay"*, and CONTEXT.md calls the `zone=` attribute "**obrigatório**, não confiar no fuso horário do container." That rigor was applied to the `@Scheduled` annotation, but not to the very next line: `LocalDate.now()` resolves via `Clock.systemDefaultZone()` — the JVM's default zone, not `Atlantic/Cape_Verde`. Today this is not exploitable: no `TZ` env var is set anywhere in `backend/`, any `docker-compose*.yml`, or the `Dockerfile` (confirmed via search), so the container's JVM default is presumably UTC, and UTC is only 1 hour ahead of Cape Verde — at the instant the cron fires (06:00 Cape Verde = 07:00 UTC), both zones still agree on the calendar date. But the moment the container's default zone is ever changed to something meaningfully *behind* UTC (a `TZ` env var added for an unrelated reason, a different base image or hosting provider default), `LocalDate.now()` at trigger time would silently resolve to *yesterday's* date relative to Cape Verde, shifting every `dias`/`diasRestantes` computation in that run by a full day — under-escalating prazos/eventos/honorarios that are genuinely at or past a threshold. `AlertasDiariosJobTest`'s own docstring confirms this exact line has zero test coverage by design: *"drives the package-private `executar(LocalDate hoje)` overload directly... never the no-arg `executar()`."*
**Fix:**
```java
private static final ZoneId FUSO_CABO_VERDE = ZoneId.of("Atlantic/Cape_Verde");

@Scheduled(cron = "0 0 6 * * *", zone = "Atlantic/Cape_Verde")
public void executar() {
    executar(LocalDate.now(FUSO_CABO_VERDE));
}
```

### WR-03: Category-level and per-entidade catch blocks still only handle `Exception` — a narrower version of the exact gap the prior review's WR-04 closed at the two outer layers

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:111-125` (category layer, added by the WR-02 fix), and `:172-174`, `:207-209`, `:253-255` (per-entidade layer)
**Issue:** The prior review's `WR-04` established that `catch (Exception e)` is insufficient for this job's stated goal, because a JVM `Error` is not an `Exception` and would otherwise "silently cancel all future scheduled executions" — and that finding was correctly fixed, but only at the two outermost layers (`executar(LocalDate)`'s own try/catch and the per-tenant try/catch around `processarTenant(...)`, both now `catch (Throwable e)` at lines 87 and 95). The `WR-02` fix, applied in the same fix pass, introduced a *third* layer — three independent try/catch blocks inside `processarTenant`, one per `processar*` call — and that layer still reads `catch (Exception e)`:
```java
try {
    processarPrazos(tenantId, hoje, processoPorId, admins);
} catch (Exception e) {
    log.error("Falha ao processar prazos do tenant {}", tenantId, e);
}
```
The pre-existing per-entidade layer inside each of the three `processar*` methods (e.g. lines 172-174) is likewise still `catch (Exception e)`. The job-level guarantee ("nothing escapes `executar()` and kills future runs") still holds, because an `Error` here still propagates up to the now-`Throwable`-catching per-tenant layer two frames higher. But the *category-level* guarantee `WR-02` was specifically about — "uma falha... dentro de um processar* nunca deve impedir as restantes categorias... de serem verificadas" — is not actually true for a JVM `Error`: an `Error` thrown from inside `processarPrazos` bypasses this `catch (Exception e)` and the per-entidade `catch (Exception e)` entirely, and is only caught two layers higher — which aborts `processarEventos`/`processarHonorarios` for that tenant for that day. The exact "one category's failure blocks its siblings" outcome `WR-02` fixed for ordinary exceptions remains fully open for `Error`s.
**Fix:** Upgrade these two remaining layers to `catch (Throwable e)` for consistency with the category-isolation goal (or explicitly document, as a deliberate choice, why `Error`s are excepted from that specific guarantee):
```java
try {
    processarPrazos(tenantId, hoje, processoPorId, admins);
} catch (Throwable e) {
    log.error("Falha ao processar prazos do tenant {}", tenantId, e);
}
```

### WR-04: No test proves the outer `catch (Throwable e)` layers actually work — the test that used to inject a tenant-level failure is now absorbed by an inner catch before ever reaching them

**File:** `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java:206-239` (`executar_umTenantLancaExcecao_outroTenantAindaEhProcessadoENenhumaExcecaoEscapa`), cross-referenced with `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:128-136` (`safeProcessoPorId`)
**Issue:** This test injects its failure via `when(processoRepository.findByTenantId(TENANT_ID)).thenThrow(new RuntimeException("boom"))` (line 214). Before the `WR-02` fix, `processoRepository.findByTenantId(tenantId)` was called directly inside `processarTenant`'s own body with no local guard, so this exception genuinely reached (and was caught by) the per-tenant catch in `executar(LocalDate)` — exercising exactly the layer the test's name describes. After the `WR-02` fix, that same call now happens inside `safeProcessoPorId(tenantId)`, which has its own `catch (Exception e)` and falls back to `Map.of()`:
```java
private Map<UUID, Processo> safeProcessoPorId(UUID tenantId) {
    try {
        return processoRepository.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Processo::getId, p -> p));
    } catch (Exception e) {
        log.error("Falha ao carregar processos do tenant {}, a prosseguir sem eles", tenantId, e);
        return Map.of();
    }
}
```
The `RuntimeException` this test throws is now fully absorbed *inside* `safeProcessoPorId` and never reaches the per-tenant/top-level `catch (Throwable e)` blocks that the class's own Javadoc calls the outermost defense layer. The test still passes and still proves something real (tenant B is unaffected by tenant A's preload failure) — but no longer proves what its name and the "3-layer isolation" documentation claim for the *outer* layers specifically. Checking every other test and every other inner catch in the file confirms this is total, not partial: `safeProcessoPorId`/`safeAdmins`, all three category try/catches, and all three per-entidade try/catches catch plain `Exception`, and every test in this suite only ever throws `RuntimeException` (itself an `Exception`). Concretely: **the `Throwable` upgrade from the prior review's `WR-04` fix has zero test coverage** — nothing in this suite throws an `Error` (or any non-`Exception` `Throwable`) to prove those two outer layers actually intercept it, which is the entire point of that fix.
**Fix:** Add a dedicated test that throws something none of the inner layers can absorb:
```java
@Test
void executar_umTenantLancaError_naoEscapaDoJobEOutroTenantAindaEhProcessado() {
    // ... same tenant A / tenant B setup as the existing exception test ...
    when(processoRepository.findByTenantId(TENANT_ID)).thenThrow(new StackOverflowError());
    // ... tenant B stubs as in the existing test ...

    assertDoesNotThrow(() -> buildJob().executar(HOJE));

    verify(processoRepository, times(1)).findByTenantId(tenantB);
    verify(notificacaoService, times(1)).criar(eq(tenantB), eq(responsavelB), eq("PRAZO_PROXIMO"),
            anyString(), anyString(), eq("prazo"), eq(prazoBId.toString()), anyString());
}
```

### WR-05: The new dedup unique constraint's manual production migration has no automated verification at startup

**File:** `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql:1-25`, `backend/src/main/resources/application-prod.yml:10`
**Issue:** The migration file's own comments are candid that this is a required-but-manual step, and that `ddl-auto: validate` — confirmed set in `application-prod.yml:10` (vs. `ddl-auto: update` in `application.yml:19` for dev/CI) — "never creates or alters schema — it only checks the existing schema is compatible at startup." What the comment doesn't spell out, and what's worth surfacing on its own: Hibernate's `hbm2ddl.auto=validate` does not reliably validate the presence of unique constraints/indexes at all — its schema validation centers on table and column existence/type compatibility, not exhaustively checking every constraint declared on an entity. This means that if this migration is skipped during a production deploy — a manual, easy-to-forget step, with no CI gate and no automated migration runner anywhere in this repository (per `CLAUDE.md`: "no Flyway, no Liquibase — only Hibernate `ddl-auto`") — the application will very likely **start up successfully with no error, warning, or log line indicating the constraint is missing**. `AlertasDiariosJob`'s notification idempotency guarantee would then silently revert to exactly the check-then-act race the `WR-01` fix was meant to close, with the deploy pipeline never surfacing it. This is the same "t_honorario" precedent situation already accepted for `uk_honorario_processo` (Phase 82) — not a new architectural problem introduced by this phase — but it is now the *second* instance of this exact silent-failure-mode pattern, worth tracking rather than re-accepting by default each time it recurs.
**Fix:** Out of scope for a pure code change in this phase, but worth tracking as a follow-up: either (a) add a lightweight startup self-check (e.g. a `CommandLineRunner` querying `to_regclass('uk_notificacao_dedup')`/`uk_honorario_processo` and logging a loud warning — or failing fast — if either is null in a `prod` profile), or (b) at minimum, add both constraints to a shared deployment checklist/runbook so they aren't only discoverable by reading migration file comments.

## Info

### IN-01: `@MockitoSettings(strictness = Strictness.LENIENT)` remains in place after the RED phase, still suppressing Mockito's unused-stub safety net

**File:** `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java:58`
**Issue:** Flagged in the prior review as `IN-01` and explicitly excluded from that fix pass's scope (`88-REVIEW-FIX.md`: *"IN-01 excluded, out of scope"*). It is still present, unchanged. The original RED-phase justification (an empty `executar(LocalDate)` skeleton meaning most configured stubs went unused) no longer applies now that the GREEN implementation is complete and every stub in the class does appear to be exercised. This class-level override continues to disable `MockitoExtension`'s default `STRICT_STUBS`, so a future edit leaving a stub configured-but-unused (a copy-pasted scenario, a stub orphaned by a later production-code change) will not be automatically flagged.
**Fix:** Remove `@MockitoSettings(strictness = Strictness.LENIENT)` and re-run the suite; if every stub is still genuinely exercised (likely), this restores the regression-detection benefit at no cost.

---

_Reviewed: 2026-07-09T22:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
