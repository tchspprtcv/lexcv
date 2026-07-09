---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
reviewed: 2026-07-09T23:10:00Z
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
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase LEXCV-88: Code Review Report (re-review, iteration 3)

**Reviewed:** 2026-07-09T23:10:00Z
**Depth:** standard
**Files Reviewed:** 7
**Status:** issues_found

## Summary

This is the third review pass of `AlertasDiariosJob` and its supporting files, following `88-REVIEW-FIX.iter2.md`, which fixed four of the five WARNING findings from the iteration-2 review (`WR-01`–`WR-04`) and explicitly skipped the fifth (`WR-05`, migration startup self-check) as out-of-scope cross-cutting work; `IN-01` (Mockito `LENIENT`) was excluded from that fix pass's scope entirely and was left untouched. I did not take either disposition on faith — I independently re-verified all five iteration-2 items against the current source, then re-read the full file set plus its immediate call graph (`RiscoPrazoService`, `NotificacaoService`, the `Evento`/`Prazo`/`Honorario` entities, `PrazoRequest`/`ResourceController`'s prazo endpoints, `88-CONTEXT.md`'s locked design decisions, migrations 82/86/88, `application.yml`/`application-prod.yml`) from scratch looking for anything new, rather than assuming a shrinking finding count means the code is now clean.

**Re-verified as correctly fixed (iteration 2 → current source):**
- **WR-01** (admin fan-out had no per-recipient isolation): all three admin loops (`AlertasDiariosJob.java:183-193`, `:228-238`, `:283-294`) now wrap each `notificar(...)` call in its own `try/catch (Exception e)`.
- **WR-02** (zone-naive `LocalDate.now()` in the no-arg `executar()`): line 84 now calls `executar(LocalDate.now(FUSO_CABO_VERDE))`, with the constant declared at line 67.
- **WR-03** (category/per-entidade catches only handled `Exception`): all three category-level catches in `processarTenant` (lines 120, 130, 137) and all three per-entidade catches (lines 194, 239, 295) now read `catch (Throwable e)`.
- **WR-04** (no test proved the outer `Throwable` catches worked): `executar_umTenantLancaError_naoEscapaDoJobEOutroTenantAindaEhProcessado` (test file, lines 241-281) now injects a genuine `StackOverflowError` and proves it is absorbed only by the per-tenant `catch (Throwable e)`, not by `safeProcessoPorId`'s inner `catch (Exception e)`.

**Confirmed still open (not fixed, dispositioned as skip/out-of-scope last iteration):**
- **WR-05** (migration has no automated startup verification) — re-flagged below as `WR-02`, with one additional angle found.
- **IN-01** (`@MockitoSettings(strictness = Strictness.LENIENT)`) — re-flagged below as `IN-03`.

I also specifically chased the one locked design decision most likely to hide a real recipient-resolution bug: `88-CONTEXT.md` states plainly that all three alert categories notify `Processo.responsavelId`, never a per-entity responsável. `Prazo.java` does have its own, independently-settable `responsavelId` column (wired through `PrazoRequest`/`ResourceController.createPrazo`), which the job never reads. I confirmed this is the documented, intended behavior (not an oversight) and is not a finding.

No BLOCKER/Critical-severity issues found in this pass either — no crash, injection, hardcoded secret, or auth-bypass defect, and `NotificacaoService.criar` remains the single validated write choke point. The findings below are a schema-naming consistency gap that undermines the exact remediation the prior review itself proposed, a fresh test-coverage gap in the newest admin-isolation fix (the same shape of gap the prior iteration's own `WR-04` caught elsewhere), and the two carried-forward items above, none of which have changed since being deferred.

## Warnings

### WR-01: `Notificacao`'s dedup unique constraint has no explicit name, so it will exist under two different names in dev/CI vs. production

**File:** `backend/src/main/java/com/lexcv/models/Notificacao.java:14-15`, `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql:23-24`
**Issue:**
```java
@Table(name = "t_notificacao", uniqueConstraints = @UniqueConstraint(columnNames = {
        "tenant_id", "destinatario_id", "entidade_tipo", "entidade_id", "categoria"}))
```
The manual production migration creates this index under the explicit, human-chosen name `uk_notificacao_dedup`. But the `@UniqueConstraint` annotation on the entity has no `name` attribute set. When `ddl-auto=update` auto-creates this same constraint in dev/CI (confirmed: `application.yml:19` vs. `application-prod.yml:10`, no custom Hibernate naming strategy configured anywhere in `application*.yml`), Hibernate's default implicit naming strategy generates its own schema-dependent constraint name (typically a `UK`-prefixed hash), **not** `uk_notificacao_dedup`. The column set matches everywhere (already verified column-for-column in the prior iteration), so the constraint's *behavior* is identical in every environment — this is not a functional bug in `notificar(...)`'s `catch (DataIntegrityViolationException ...)`, which doesn't care about the constraint's name. But it does mean the constraint cannot be reliably referenced, queried, or dropped by name across environments, which directly undermines the exact remediation the prior review (`WR-05`, iteration 2) proposed for the sibling gap: a startup self-check querying `to_regclass('uk_notificacao_dedup')` would correctly find the constraint in production (where the manual script used that literal name) but would find nothing in dev/CI (where the constraint exists under a different, Hibernate-generated name) — a false negative for the exact tool meant to close that gap. This is also not a new pattern: the Phase-82 precedent this migration explicitly cites, `Honorario.java:12`'s `@UniqueConstraint(columnNames = "processo_id")`, has the identical gap (its own migration names it `uk_honorario_processo`, but the annotation has no matching `name`) — so this is the second occurrence of the same naming inconsistency, worth closing now that it's been noticed twice.
**Fix:** Pin the name explicitly so it matches across every environment:
```java
@Table(name = "t_notificacao", uniqueConstraints = @UniqueConstraint(
        name = "uk_notificacao_dedup",
        columnNames = {"tenant_id", "destinatario_id", "entidade_tipo", "entidade_id", "categoria"}))
```
(Consider the same for `Honorario.java`'s `uk_honorario_processo` as a follow-up, though that file is outside this phase's change set.)

### WR-02: Manual dedup-index migration still has no automated startup verification, and has no guard against pre-existing duplicate rows

**File:** `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql:1-25`, `backend/src/main/resources/application-prod.yml:10`
**Issue:** Confirmed unchanged since the iteration-2 review's `WR-05`, which was explicitly skipped in `88-REVIEW-FIX.iter2.md` as *"out of scope for a pure code change in this phase... cross-cutting follow-up."* Re-verified `application-prod.yml:10` is still `ddl-auto: validate` and `application.yml:19` is still `ddl-auto: update`; nothing was added anywhere to detect a missing constraint at application startup. A skipped manual migration in production still fails silently at the "everything looks fine" level — the app starts, and `AlertasDiariosJob`'s idempotency guarantee quietly reverts to the pre-`WR-01` check-then-act race. "Skipped, out of scope" is a legitimate scoping decision, but it is not "fixed" — re-flagging so it doesn't quietly disappear from view.

  One additional angle not previously raised: the script itself is a bare `CREATE UNIQUE INDEX` with no pre-flight check for rows that would already violate it:
  ```sql
  CREATE UNIQUE INDEX uk_notificacao_dedup
      ON t_notificacao (tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria);
  ```
  If any duplicate `(tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria)` rows already exist in the target database at the moment a DBA runs this script (e.g., leftover data from manually exercising the job before this constraint existed, or from the exact concurrent-race window this migration is meant to close), `CREATE UNIQUE INDEX` fails outright with a Postgres error and blocks the deploy step this script exists to unblock — with the script's own comments giving no guidance on what to do if that happens. (The identical gap already exists in the Phase-82 precedent, `82-add-honorario-processo-unique-constraint.sql:21` — so this is a pre-existing, repo-wide pattern rather than something new to Phase 88, consistent with how `WR-05` characterized the startup-verification gap. Naming it explicitly here since it's the second manual migration script that could fail this way.)
**Fix:** Still out of scope for a single-file patch in this phase (same reasoning as the iteration-2 disposition), but if picked up as a follow-up, the script could at least add a pre-flight duplicate check with an explicit comment on what to do if it returns rows:
```sql
-- Run first — if this returns any rows, resolve/merge the duplicates before proceeding:
SELECT tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria, COUNT(*)
FROM t_notificacao
GROUP BY tenant_id, destinatario_id, entidade_tipo, entidade_id, categoria
HAVING COUNT(*) > 1;
```

### WR-03: No test proves the per-admin `catch (Exception e)` fan-out isolation (iteration-2 fix) actually stops one admin's failure from blocking the rest

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:183-193` (also `:228-238`, `:283-294`), cross-referenced with `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java` (all 8 `@Test` methods, lines 111-374)
**Issue:** The iteration-2 fix wrapped each admin's `notificar(...)` call in its own try/catch, e.g. in `processarPrazos`:
```java
for (User admin : admins) {
    try {
        notificar(tenantId, admin.getId(), categoria, titulo, mensagem, "prazo", entidadeId, linkUrl);
    } catch (Exception e) {
        log.warn("Falha ao notificar admin {} para prazo {} do tenant {}", admin.getId(), prazo.getId(), tenantId, e);
    }
}
```
This is exactly the shape of gap the prior iteration's own `WR-04` identified and fixed for the two outer `Throwable` layers — a defensive-coding change with no test proving it does what it claims. Checking every one of the 8 tests in `AlertasDiariosJobTest.java` confirms none of them exercise this specific path: tests with admins either use exactly one admin whose notification succeeds normally (`executar_prazoProximoComResponsavelDefinido_notificaResponsavelEAdmin`, `executar_eventoSemProcessoIdCritico_notificaApenasAdmins`), or use an empty admin list entirely (all the others). No test configures two-or-more admins where one throws (e.g., an `IllegalArgumentException` or transient `RuntimeException` from `notificacaoService.criar(...)`) and then verifies the remaining admin(s) still get notified. If a future change accidentally broke this isolation (e.g., moved the try/catch outside the loop, or narrowed the catch type incorrectly), nothing in this suite would catch the regression.
**Fix:** Add a dedicated test mirroring the existing coverage pattern:
```java
@Test
void executar_umAdminFalhaAoNotificar_restantesAdminsAindaSaoNotificados() {
    Tenant tenant = Tenant.builder().id(TENANT_ID).nome("Tenant A").build();
    when(tenantRepository.findAll()).thenReturn(List.of(tenant));

    Processo processo = Processo.builder().id(PROCESSO_ID).tenantId(TENANT_ID)
            .responsavelId(RESPONSAVEL_ID).numeroProcesso("PROC-0001").build();
    when(processoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(processo));

    UUID prazoId = UUID.randomUUID();
    Prazo prazo = Prazo.builder().id(prazoId).tenantId(TENANT_ID).processoId(PROCESSO_ID)
            .descricao("Contestação").dataLimite(HOJE.plusDays(2)).prioridade("MEDIA")
            .concluido(false).build();
    when(prazoRepository.findByTenantId(TENANT_ID)).thenReturn(List.of(prazo));

    semEventos(TENANT_ID);
    semHonorarios();
    nuncaAntesNotificado();

    UUID adminFalhaId = UUID.randomUUID();
    UUID adminOkId = UUID.randomUUID();
    User adminFalha = User.builder().id(adminFalhaId).tenantId(TENANT_ID).build();
    User adminOk = User.builder().id(adminOkId).tenantId(TENANT_ID).build();
    when(userRepository.findByTenantIdAndRoleName(TENANT_ID, "ADMIN"))
            .thenReturn(List.of(adminFalha, adminOk));

    // First admin's notify call throws an unexpected RuntimeException (e.g. transient DataAccessException).
    doThrow(new RuntimeException("boom"))
            .when(notificacaoService).criar(eq(TENANT_ID), eq(adminFalhaId), any(), any(), any(), any(), any(), any());

    assertDoesNotThrow(() -> buildJob().executar(HOJE));

    // The second admin must still be notified despite the first admin's failure.
    verify(notificacaoService, times(1)).criar(eq(TENANT_ID), eq(adminOkId), eq("PRAZO_PROXIMO"),
            anyString(), anyString(), eq("prazo"), eq(prazoId.toString()), anyString());
}
```
(requires adding `import static org.mockito.Mockito.doThrow;`)

## Info

### IN-01: Class-level Javadoc still claims "3 camadas" of failure isolation; the code has had 4 layers since the iteration-1/2 fixes

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:42-46`, contradicted by the accurate description at `:110-113`
**Issue:** The class Javadoc reads:
```java
 * <p>Isolamento de falha em 3 camadas (T-88-03): try/catch de topo em {@link #executar(LocalDate)},
 * try/catch por tenant dentro do loop de {@link TenantRepository#findAll()}, e try/catch por
 * entidade dentro de cada {@code processar*} — uma exceção não tratada num {@code @Scheduled}
 * ...
```
This lists exactly three layers: top-level, per-tenant, per-entidade. It omits the per-category layer (the three independent try/catch blocks around `processarPrazos`/`processarEventos`/`processarHonorarios` inside `processarTenant`) that the iteration-1 `WR-02` fix introduced specifically so one category's failure can't suppress its siblings. That fourth layer is correctly documented at the method level a few lines below (`processarTenant`'s own comment, lines 110-113: *"cada preload e cada categoria tem isolamento de falha própria"*), so the class-level summary is now internally inconsistent with the method-level comment directly beneath it — a maintainer skimming only the class Javadoc would undercount the actual isolation layers and could reasonably miss the category layer entirely when reasoning about a future change.
**Fix:**
```java
 * <p>Isolamento de falha em 4 camadas (T-88-03, camada de categoria acrescentada pela fix de
 * WR-02/iteração 1): try/catch de topo em {@link #executar(LocalDate)}, try/catch por tenant
 * dentro do loop de {@link TenantRepository#findAll()}, try/catch por categoria (prazo/evento/
 * honorário) dentro de {@code processarTenant}, e try/catch por entidade dentro de cada
 * {@code processar*} — uma exceção não tratada num {@code @Scheduled} pode cancelar
 * silenciosamente todas as execuções futuras desse método (Spring Framework), daí a defesa em
 * profundidade.
```

### IN-02: The "responsável-also-admin" natural-dedup behavior documented in `notificar()`'s own comment has no test

**File:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java:303-306`, cross-referenced with `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java` (lines 111-374)
**Issue:**
```java
// Choke point único: nunca chama notificacaoRepository.save(...) diretamente -- toda a
// escrita passa por notificacaoService.criar(...). Idempotência via existence-check antes de
// cada chamada; ordem responsavel-primeiro-depois-admins faz um responsavel-que-é-também-admin
// ser deduplicado naturalmente (a segunda existence-check já encontra a linha da primeira).
```
This is a real, load-bearing behavioral claim — the primary-recipient call and the admin-loop call for the same person are only deduplicated because they run sequentially and the second `existsBy...` check observes the first `criar(...)`'s effect. But no test in the suite ever configures a scenario where `admin.getId()` equals `RESPONSAVEL_ID` (every test with a non-empty admin list uses a distinct `ADMIN_ID`). If a future refactor changed the call order (admins-first) or made the two calls concurrent/batched, this claim could silently stop holding with no test failure to signal it.
**Fix:** Add a test with an admin whose id equals the process's `responsavelId`, and stub the existence check to return `false` then `true` across the two sequential calls:
```java
when(notificacaoRepository.existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(
        TENANT_ID, RESPONSAVEL_ID, "prazo", prazoId.toString(), "PRAZO_PROXIMO"))
        .thenReturn(false, true);
// ... admins list contains a User with id == RESPONSAVEL_ID ...
buildJob().executar(HOJE);
verify(notificacaoService, times(1)).criar(eq(TENANT_ID), eq(RESPONSAVEL_ID), eq("PRAZO_PROXIMO"),
        anyString(), anyString(), eq("prazo"), eq(prazoId.toString()), anyString());
```

### IN-03: `@MockitoSettings(strictness = Strictness.LENIENT)` remains in place, still suppressing Mockito's unused-stub safety net

**File:** `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java:58`
**Issue:** Flagged as `IN-01` in both prior reviews and explicitly excluded from both fix passes' scope (`fix_scope: critical_warning` in both `88-REVIEW-FIX.md` and `88-REVIEW-FIX.iter2.md`). Confirmed unchanged — still present, still class-level, still disabling `MockitoExtension`'s default `STRICT_STUBS`. The original RED-phase justification no longer applies; the GREEN implementation has been complete and stable across two subsequent fix iterations, and every stub in the class does appear to be genuinely exercised by the current tests.
**Fix:** Remove the annotation and re-run the suite; if every stub is still exercised (likely, given the current test count and structure), this restores automatic detection of orphaned/unused stubs for future edits at no cost.

---

_Reviewed: 2026-07-09T23:10:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
