---
phase: 88-verificacao-diaria-de-prazos-e-honorarios
verified: 2026-07-09T23:50:00Z
status: passed
score: 7/7 must-haves verified
overrides_applied: 0
---

# Phase 88: Verificação Diária de Prazos e Honorários Verification Report

**Phase Goal:** Um job agendado diário deteta transições de risco em prazos de processos, eventos de calendário crítico e honorários sem pagamento total, notificando o responsável apenas quando o estado efetivamente muda — nunca repetidamente a cada execução para um item já notificado nesse estado.
**Verified:** 2026-07-09T23:50:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Verification Method (Important Context)

This phase went through 2 plans (2 waves) plus a **3-iteration code-review/auto-fix loop**
(`88-REVIEW.md` + `.iter2.md` + `.iter3.md`, paired with `88-REVIEW-FIX.md` + `.iter2.md` +
`.iter3.md`) *after* both SUMMARY.md files were written. The SUMMARYs are stale with respect to
that review activity. Per the adversarial mandate, this verification:

1. Did **not** read the SUMMARYs as evidence of current state — only as a historical record of
   original intent, then cross-checked every claim against the files on disk.
2. Re-derived the true chronological order of the three review/fix rounds from `reviewed:`/
   `fixed_at:` timestamps in frontmatter (the `.iter2.md`/`.iter3.md` filename suffixes do **not**
   match fix-iteration numbers — `88-REVIEW.iter2.md` [21:18] is actually the review that produced
   iteration-1 fixes at 21:41, `88-REVIEW.iter3.md` [22:30] produced iteration-2 fixes at 22:42,
   and the unsuffixed `88-REVIEW.md` [23:10] produced the final iteration-3 fixes at 23:26). All
   fix content was cross-referenced against this corrected order and found internally consistent.
3. Confirmed all 15 review-fix commits (`788d053` … `8dc30c6`) are real, committed, and are
   ancestors of the current `master` HEAD (`git merge-base --is-ancestor 8dc30c6 HEAD` → true).
4. Read the current `AlertasDiariosJob.java`, `AlertasDiariosJobTest.java`, `Notificacao.java`,
   `NotificacaoRepository.java`, `HonorarioRepository.java`, `SchedulingConfig.java`, and the
   migration SQL directly off disk — not the SUMMARY prose.
5. **Ran the test suite fresh** (`cd backend && mvn -q -o test -Dtest=AlertasDiariosJobTest`) rather
   than trusting the "9/9 green" claim in `88-REVIEW-FIX.iter3.md` — confirmed via
   `target/surefire-reports/com.lexcv.jobs.AlertasDiariosJobTest.txt`:
   `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`. The one `log.error` stack trace in the raw
   output is the intentional "tenant A throws, tenant B still processes" test scenario logging its
   own caught exception — not a test failure.

## Goal Achievement

### Observable Truths

Merged from ROADMAP.md Phase 88 Success Criteria (SC1–SC4) and both plans' `must_haves.truths`
(deduplicated where a plan truth restates a roadmap SC with more implementation detail).

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A single `@Scheduled` job runs once daily (cron `0 0 6 * * *`, zone `Atlantic/Cape_Verde`), iterates every tenant explicitly via `tenantRepository.findAll()` — never `SecurityContextHolder`/`getTenantId()` — and an uncaught exception/error in one tenant or entity does not block the rest of that run nor future scheduled runs. (Maps to ROADMAP SC1, Plan 88-02 truths 1+4) | ✓ VERIFIED | `AlertasDiariosJob.java:82-85` (`@Scheduled(cron = "0 0 6 * * *", zone = "Atlantic/Cape_Verde")` delegating to `executar(LocalDate.now(FUSO_CABO_VERDE))`); `:91-104` (`tenantRepository.findAll()` loop, per-tenant `catch (Throwable e)`); `:114-140` (per-category `catch (Throwable e)` in `processarTenant`, added in iteration-1/2 fixes); `:161-301` (per-entidade `catch (Throwable e)` in each `processar*`). Grep confirms **zero** occurrences of `SecurityContextHolder`/`getTenantId()`/`UserPrincipal` in the job file. Freshly re-run tests `executar_umTenantLancaExcecao_outroTenantAindaEhProcessadoENenhumaExcecaoEscapa` (RuntimeException) and `executar_umTenantLancaError_naoEscapaDoJobEOutroTenantAindaEhProcessado` (genuine `StackOverflowError`, added in iteration-2 fix e79415c) both pass — proving isolation holds for both `Exception` and non-`Exception` `Throwable`. |
| 2 | When a non-concluded prazo's Processo, or a non-concluded evento, crosses into "proximo"/"vencido", the Processo's responsavel (+ each tenant ADMIN) receives exactly one new notification for that level; running the job again on unchanged data creates zero additional notifications. (Maps to ROADMAP SC2, Plan 88-02 truth 2) | ✓ VERIFIED | `notificar(...)` (`:307-331`) calls `existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria` before every `criar(...)`, responsavel-first-then-admins. Fresh test run green: `executar_prazoProximoComResponsavelDefinido_notificaResponsavelEAdmin`, `executar_notificacaoJaExistenteParaOMesmoNivel_naoDuplicaNotificacao` (existence-check stubbed true → zero `criar` calls), `executar_prazoCruzaDeProximoParaVencido_criaApenasNotificacaoDoNovoNivel` (PROXIMO already exists, VENCIDO doesn't → exactly one new call), `executar_eventoSemProcessoIdCritico_notificaApenasAdmins`. Defense-in-depth added by code review beyond the original plan: DB-level `uk_notificacao_dedup` unique constraint (explicit name, `Notificacao.java:14-16`) mirrored by `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql`, with `notificar(...)` catching `DataIntegrityViolationException` as a fail-closed backstop (`:322-330`). |
| 3 | A honorario whose Processo is ≥30 days past `dataAcordo` with `valorTotal` set and not fully paid triggers exactly one `HONORARIO_ATRASADO` notification; a honorario with `valorTotal` null (or `dataAcordo` null) is skipped silently, never throwing. (Maps to ROADMAP SC3, Plan 88-02 truth 3) | ✓ VERIFIED | `processarHonorarios()` (`:247-301`), guard at `:259-271` (`valorTotal == null \|\| dataAcordo == null` → skip; `totalPago >= valorTotal` → skip; `ChronoUnit.DAYS.between(dataAcordo, hoje) < 30` → skip). Fresh test run green: `executar_honorarioValorTotalNulo_ignoradoSemExcecao` (`assertDoesNotThrow`, `verify(never())`), `executar_honorarioAtrasado30DiasNaoPago_notificaApenasEsseHonorario` (3 honorarios: fully-paid-despite-age skipped, exactly-30-days-unpaid notifies, 10-days-unpaid skipped — exactly 1 `criar` call total). |
| 4 | The job derives "proximo"/"vencido" exclusively from `RiscoPrazoService` — no threshold/limiar logic is re-implemented inside the job. (Maps to ROADMAP SC4, Plan 88-02 truth 5) | ✓ VERIFIED | `grep -n "<=\s*7\|<=\s*3\|isBefore(hoje)\|isAfter(hoje)"` on `AlertasDiariosJob.java` → zero matches. Only `riscoPrazoService.computeRisco(...)` (`:168`) and `riscoPrazoService.computeRiscoEvento(...)` (`:206`) determine prazo/evento categoria. The one `ChronoUnit.DAYS.between` match (`:268`) is the honorario's own 30-day rule, which is explicitly **not** a `RiscoPrazoService` concern (confirmed against `RiscoPrazoService.java`, which only exposes `computeRisco`/`computeRiscoEvento` for prazo/evento). |
| 5 | The notification repository can answer "does a notification for this exact (tenant, recipient, entity-type, entity-id, categoria) tuple already exist?" — the idempotency backbone. (Plan 88-01 truth 1) | ✓ VERIFIED | `NotificacaoRepository.java:56-57`: `boolean existsByTenantIdAndDestinatarioIdAndEntidadeTipoAndEntidadeIdAndCategoria(UUID, UUID, String, String, String)` — exact signature match to plan spec, pure derived query, no `@Query`. Wired: called at `AlertasDiariosJob.java:312-314`. |
| 6 | The honorario repository can batch-fetch all honorarios for a set of processo ids in a single query (no per-processo N+1). (Plan 88-01 truth 2) | ✓ VERIFIED | `HonorarioRepository.java:14`: `List<Honorario> findByProcessoIdIn(Collection<UUID> processoIds)`, pre-existing `findByProcessoId(UUID)` untouched. Wired: called exactly once per tenant at `AlertasDiariosJob.java:257` via `processoPorId.keySet()` — not inside any per-entity loop. |
| 7 | `@EnableScheduling` is active in the Spring context so the `@Scheduled` trigger actually fires. (Plan 88-01 truth 3) | ✓ VERIFIED | `SchedulingConfig.java`: dedicated `@Configuration @EnableScheduling` class, empty body. `grep -rn "EnableScheduling" backend/src/main` → exactly 1 occurrence (the import + the annotation, both in this file). `BackendApplication.java` confirmed bare `@SpringBootApplication` with no scheduling annotation — no duplicate-activation risk. |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java` | existence-check derived query | ✓ VERIFIED | Method present exactly as specified; wired into `AlertasDiariosJob.notificar(...)`. |
| `backend/src/main/java/com/lexcv/repositories/HonorarioRepository.java` | batch fetch by processo id collection | ✓ VERIFIED | `findByProcessoIdIn` present; wired, called once per tenant. |
| `backend/src/main/java/com/lexcv/config/SchedulingConfig.java` | `@EnableScheduling` activation, isolated `@Configuration` | ✓ VERIFIED | Exists, empty body, exactly 1 `@EnableScheduling` codebase-wide. |
| `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java` | daily cross-tenant alert scan, min 90 lines | ✓ VERIFIED | 339 lines. Full implementation (not skeleton) — `@Scheduled` entry point, 3 `processar*` methods, shared `notificar(...)` choke point, `safeProcessoPorId`/`safeAdmins` preload helpers. No stub markers. |
| `backend/src/test/java/com/lexcv/jobs/AlertasDiariosJobTest.java` | Mockito proof of idempotency/threshold/honorario/isolation | ✓ VERIFIED | 9 test methods (7 from the original plan + 2 added by the review-fix loop: an `Error`-isolation test and a per-admin-fan-out-isolation test). Freshly executed: **9/9 passing**. |
| `backend/src/main/java/com/lexcv/models/Notificacao.java` (review-added, beyond original plan scope) | DB-level unique constraint backstop | ✓ VERIFIED | `@UniqueConstraint(name = "uk_notificacao_dedup", columnNames = {...})` — explicit name pinned in iteration-3 fix (`e5960fe`) to match the manual migration exactly. |
| `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql` (review-added) | manual prod migration for the constraint | ✓ VERIFIED (exists, column list matches entity) | Not auto-verified at startup — see Gaps Summary (accepted, not a phase truth failure). |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `AlertasDiariosJob.java` | `RiscoPrazoService` | `computeRisco(dataLimite, prioridade, hoje)` / `computeRiscoEvento(dataInicio, prioridade, hoje)` | ✓ WIRED | Lines 168, 206 — 3-arg overloads used, `hoje` threaded from the parameter, never `LocalDate.now()` inside the loop. |
| `AlertasDiariosJob.java` | `NotificacaoRepository` existence-check | called before every `criar` | ✓ WIRED | Lines 312-314 in `notificar(...)`. |
| `AlertasDiariosJob.java` | `NotificacaoService.criar` | single write choke point | ✓ WIRED | Line 317; job never calls `notificacaoRepository.save(...)` directly (grep confirms zero such calls in the job). |
| `AlertasDiariosJob.java` | `TenantRepository` | explicit cross-tenant loop, no SecurityContext | ✓ WIRED | Line 91, `tenantRepository.findAll()`. |
| `SchedulingConfig.java` | Spring scheduling infrastructure | `@EnableScheduling` on a `@Configuration` class | ✓ WIRED | Confirmed sole occurrence codebase-wide; `AlertasDiariosJob`'s `@Scheduled` method depends on it to register. |
| `NotificacaoService.criar` | tenant-membership re-validation | `userRepository.findById(destinatarioId).filter(tenantId::equals)` | ✓ WIRED | `NotificacaoService.java:38-41` — independent defense-in-depth layer confirmed present, re-validates every recipient the job passes in, even though the job also filters by tenant upstream. |

### Data-Flow Trace (Level 4)

This phase has no UI; the "renders dynamic data" analogue is: does the job's computed data reach a
real persistence call, or a stub?

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `AlertasDiariosJob.notificar(...)` | `categoria/titulo/mensagem/entidadeTipo/entidadeId/linkUrl` (per-entity, dynamically built from `prazo`/`evento`/`honorario` fields) | `NotificacaoService.criar(...)` → `notificacaoRepository.save(n)` | Yes — `NotificacaoService.java:55-65` builds a real `Notificacao` from the passed arguments and persists via JPA `save`, not a static/empty return. | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Full `AlertasDiariosJobTest` suite passes against current `master` HEAD (not trusting the REVIEW-FIX narrative) | `cd backend && mvn -q -o test -Dtest=AlertasDiariosJobTest` | `target/surefire-reports/com.lexcv.jobs.AlertasDiariosJobTest.txt`: `Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 7.721 s` | ✓ PASS |
| No re-implemented prazo/evento threshold literals snuck into the job | `grep -n "<=\s*7\|<=\s*3\|isBefore(hoje)\|isAfter(hoje)" AlertasDiariosJob.java` | zero matches | ✓ PASS |
| Zero SecurityContext dependence | `grep -n "SecurityContextHolder\|getTenantId()\|UserPrincipal" AlertasDiariosJob.java` | zero matches | ✓ PASS |
| `@EnableScheduling` registered exactly once, `BackendApplication` untouched | `grep -rn "EnableScheduling" backend/src/main` + read of `BackendApplication.java` | 1 occurrence (SchedulingConfig only); `BackendApplication.java` is a bare `@SpringBootApplication` | ✓ PASS |
| No debt markers left in any phase-88 file | `grep -n "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` across all 6 phase-88 source files | zero matches in every file | ✓ PASS |
| All review-fix commits are real and merged to `master` | `git merge-base --is-ancestor 8dc30c6 HEAD` | true; `git branch --contains 8dc30c6` → `* master` | ✓ PASS |

### Probe Execution

No conventional probe scripts (`scripts/*/tests/probe-*.sh`) exist in this repository, and none are
referenced by this phase's PLAN/SUMMARY/REVIEW files (`find`/`grep` both empty). This is a
Java/Maven backend phase — its runnable-check equivalent is the Behavioral Spot-Checks section
above (fresh `mvn test` execution), which serves the same evidentiary purpose a probe script would.

**Step 7c: SKIPPED (no probe scripts apply to this phase; equivalent behavioral verification performed via `mvn test`)**

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| NOTF-20 | 88-01, 88-02 | Responsável do processo é alertado quando um prazo do processo muda de estado de risco (próximo/vencido), via verificação diária | ✓ SATISFIED | `processarPrazos()` + `PRAZO_PROXIMO`/`PRAZO_VENCIDO` categorias; `executar_prazoProximoComResponsavelDefinido_notificaResponsavelEAdmin` and `executar_prazoCruzaDeProximoParaVencido_criaApenasNotificacaoDoNovoNivel` pass. |
| NOTF-21 | 88-01, 88-02 | Responsável do processo é alertado quando um evento de calendário crítico muda de estado de risco (próximo/vencido), via verificação diária | ✓ SATISFIED | `processarEventos()` + `EVENTO_PROXIMO`/`EVENTO_VENCIDO` categorias; `executar_eventoSemProcessoIdCritico_notificaApenasAdmins` pass (plus the shared crossing/idempotency tests exercise the same code path as prazo). |
| NOTF-23 | 88-01, 88-02 | Responsável do processo é alertado quando o honorário do processo atinge N dias sem pagamento total desde a data do acordo, via verificação diária | ✓ SATISFIED | `processarHonorarios()` + `HONORARIO_ATRASADO` categoria (N=30 days, `DIAS_HONORARIO_ATRASADO` constant); `executar_honorarioValorTotalNulo_ignoradoSemExcecao` and `executar_honorarioAtrasado30DiasNaoPago_notificaApenasEsseHonorario` pass. |

No orphaned requirements: `REQUIREMENTS.md`'s traceability table maps exactly NOTF-20/NOTF-21/NOTF-23
to Phase 88, matching the `requirements: [NOTF-20, NOTF-21, NOTF-23]` frontmatter declared
identically in both `88-01-PLAN.md` and `88-02-PLAN.md`. No additional Phase-88 requirement IDs
appear in `REQUIREMENTS.md` that are absent from the plans.

**Documentation-sync discrepancy (informational, not a functional gap):** `REQUIREMENTS.md` still
shows NOTF-20/21/23 as unchecked (`- [ ]`) with traceability status "Pending" (lines 26-27, 29, 70-73),
even though the implementation evidence above satisfies all three. This is the tracking document
being stale, not the code being incomplete — every other completed phase in this milestone (85, 86,
87) shows `- [x]`/"Complete" for its requirements, so this looks like a simple oversight in updating
`REQUIREMENTS.md` after Phase 88 finished its review/fix loop. Recommend updating the three checkboxes
and traceability rows to `[x]`/"Complete" as bookkeeping; does not block this phase's status.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AlertasDiariosJob.java` | 42 | Javadoc says "Isolamento de falha em **3 camadas**" but the code has had **4** layers (top-level, per-tenant, per-category, per-entidade) since the iteration-2 fix (`15aa0ce`, WR-02) added the per-category layer | ℹ️ INFO | Stale doc comment only — no functional impact. Raised as `IN-01` in the final review iteration and explicitly deferred (`fix_scope: critical_warning` excluded INFO items). |
| `AlertasDiariosJobTest.java` | 59 | `@MockitoSettings(strictness = Strictness.LENIENT)` retained after the RED phase's original justification (empty skeleton body) no longer applies — GREEN implementation has been complete since `a4f31f2` | ℹ️ INFO | Low risk: the `verify(...)` calls remain the real behavioral gate regardless of strictness; LENIENT only suppresses `UnnecessaryStubbingException`, not assertion failures. Raised as `IN-03` across iterations 2 and 3, explicitly deferred. |
| `AlertasDiariosJobTest.java` | n/a | No dedicated test isolates the "responsável-also-admin" natural-dedup behavior documented in `notificar()`'s own comment (it is exercised only implicitly by the general idempotency tests, never in isolation) | ℹ️ INFO | Raised as `IN-02` in the final review iteration, explicitly deferred. |
| `backend/migrations/88-add-notificacao-dedup-unique-constraint.sql` | n/a | Manual production migration has no automated startup verification that it was actually applied; `ddl-auto=validate` in prod does not reliably catch a missing unique constraint | ⚠️ WARNING (accepted, see below) | See Gaps Summary — same class of accepted risk as the pre-existing Phase-82 `uk_honorario_processo` migration; consistently raised and consciously deferred across all 3 review iterations (`WR-05`→`WR-02`) as cross-cutting/out-of-scope for a single-phase fix. Does not affect this phase's must-haves: the primary idempotency mechanism (the application-level existence-check) does not depend on this constraint. |

No `TBD`/`FIXME`/`XXX` debt markers (the hard gate per the review process) exist in any file
modified by this phase — confirmed by direct grep across all 6 source files plus the migration SQL.

### Human Verification Required

None. This phase is a pure backend scheduled job with no UI/visual component, no external service
integration, and no user-facing flow — every observable truth is verifiable via code inspection and
automated test execution. Both PLAN.md files were checked for `<verify><human-check>` blocks
(Step 8's harvest step) — neither contains any; both tasks in each plan use only
`<verify><automated>...</automated></verify>`.

### Gaps Summary

No gaps. All 7 merged must-have truths (ROADMAP Success Criteria 1-4 plus Plan 88-01's 3
enabling-artifact truths) are VERIFIED against the current `master` HEAD, using freshly executed
tests (`9/9 passing`, re-run in this verification session, not read from a prior report) and direct
grep/read evidence — not SUMMARY.md narrative, which predates all 3 rounds of code review.

**One pre-existing, consciously accepted residual risk carried forward (not a gap against this
phase's must-haves):** the manual production migration
`backend/migrations/88-add-notificacao-dedup-unique-constraint.sql` has no automated startup check
proving it was applied before the app relies on the DB-level `uk_notificacao_dedup` constraint as a
defense-in-depth backstop. This was raised in all 3 review iterations (as `WR-05`, then `WR-02`)
and consistently, deliberately deferred as genuinely cross-cutting (it would need to cover this
migration and the pre-existing Phase-82 `uk_honorario_processo` migration together, and choosing
fail-fast-vs-warn-only semantics is an architectural decision exceeding a single-phase fix). It is
not re-litigated as a defect here: the DB constraint is an ADDED hardening layer that code review
introduced beyond Plan 88-02's original scope — the plan's own threat model (T-88-04) always
treated the **application-level** existence-check as the accepted, sufficient idempotency
mechanism for this single-instance deployment, and that mechanism does not depend on the DB
constraint to function. Recommend tracking as its own cross-cutting follow-up (covering both
manual migrations) rather than reopening this phase.

---

_Verified: 2026-07-09T23:50:00Z_
_Verifier: Claude (gsd-verifier)_
