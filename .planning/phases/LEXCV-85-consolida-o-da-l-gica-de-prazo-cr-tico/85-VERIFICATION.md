---
phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico
verified: 2026-07-08T16:43:01Z
status: human_needed
score: 5/5 must-haves verified (0 overrides)
overrides_applied: 0
human_verification:
  - test: "Confirm intended dashboard-KPI behavior for an ALTA-priority Evento with a null dataFim"
    expected: "Product owner states whether such an event should count as urgent/critical in agendaUrgentesCount (/dashboard prazos_vencer) and prazosCriticosCount (/processos/dashboard), or whether the new behavior (excluded, since RiscoPrazoService.computeRiscoEvento(null, ...) returns \"ok\") is accepted as-is"
    why_human: "This is a genuine product/business ambiguity, not a code defect. The code review process (85-REVIEW.md iteration 1, WR-02) explicitly declined to resolve it unilaterally and flagged its fix as 'requires human verification' because it is a real behavior reversal versus pre-phase logic (which counted ALL non-concluded ALTA events regardless of any date) for a plausible real data shape (an open-ended high-priority reminder with no end date). Three review/fix iterations passed without a human ever confirming which behavior is correct — the comment documenting the corner case remains the only mitigation."
---

# Phase LEXCV-85: Consolidação da Lógica de "Prazo Crítico" Verification Report

**Phase Goal:** Dashboard, agenda e (mais tarde) notificações partilham uma única fonte de verdade para decidir se um prazo ou evento está "próximo" ou "vencido", eliminando as 4 implementações inconsistentes hoje espalhadas pelo backend.
**Verified:** 2026-07-08T16:43:01Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

This phase went through 3 code-review/auto-fix iterations after initial execution (`85-REVIEW.md`, `85-REVIEW.iter2.md`, `85-REVIEW.iter3.md` + matching `85-REVIEW-FIX*.md`), which materially changed the code beyond what `85-01-SUMMARY.md` describes (the SUMMARY was written before the review loop ran). This verification checks the **current committed state of the code**, not the SUMMARY's narrative, and independently re-ran the test suite and compiler rather than trusting any prior report's claims.

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Um único `@Service` injetável (`RiscoPrazoService`) calcula o risco (ok/proximo/vencido) para `Prazo` (LocalDate) e `Evento` (LocalDateTime) usando a MESMA tabela de limiares 7d-ALTA/3d-outros | ✓ VERIFIED | `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java` (46 lines): `@Service` at line 11; `computeRiscoEvento` (line 39) converts `LocalDateTime`→`LocalDate` and delegates straight into `computeRisco` (line 24) — genuinely one shared threshold table, not a parallel copy |
| 2 | O método privado `computeRisco` já NÃO existe em `ResourceController` — nenhuma cópia duplicada da lógica de limiares permanece no controller | ✓ VERIFIED | `grep -n "private String computeRisco("` on `ResourceController.java` → zero matches. Independent grep for stray `"vencido"`/`"proximo"`/`"ok"` string-literal comparisons → only 1 hit, inside a comment (line 2775), not logic |
| 3 | Os 5 call sites baseados em `Prazo` (listProcessos, listPrazos, listAllPrazos, createPrazo, togglePrazoConcluido) devolvem veredictos byte-idênticos aos de antes — zero regressão via overload de 2 args (default `hoje=LocalDate.now()`) | ✓ VERIFIED | All 8 call expressions confirmed present and using the 2-arg overload: listProcessos:946, listPrazos:1410, listAllPrazos:1432, createPrazo:1476+1496, togglePrazoConcluido:1523+1524+1536 (`grep -c "riscoPrazoService.computeRisco("` = 8) |
| 4 | Os 3 call sites baseados em `Evento` (agendaUrgentesCount, prazosCriticosCount, getUpcomingEventos) passam a usar `computeRiscoEvento` — mudança intencional (ROADMAP critério 3) | ✓ VERIFIED (implementation evolved post-plan, functionally equivalent/better) | `getUpcomingEventos` calls `riscoPrazoService.computeRiscoEvento(...)` directly (line 2261). `agendaUrgentesCount` (line 2795) and `prazosCriticosCount` (line 2904) both now call a shared private `isEventoCritico(Evento e)` helper (line 2787) added by review-fix commit `830f427`, which itself calls `riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade())` (line 2788) — all 3 consumers still route through the shared threshold table, just with one extra layer of de-duplication than the plan literally specified |
| 5 | Teste de caracterização JUnit 5 puro (sem Spring context) fixa a tabela de limiares: dia-limiar exato (0/3/7/8 dias), null, insensibilidade a maiúsculas, conversão `LocalDateTime`→`LocalDate` | ✓ VERIFIED — actually executed | `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java` (119 lines, 15 `@Test` methods). Ran `cd backend && mvn -o test -Dtest=RiscoPrazoServiceTest` myself: **BUILD SUCCESS, Tests run: 15, Failures: 0, Errors: 0**. `grep -c "@SpringBootTest\|@Autowired\|@ExtendWith"` = 0 (pure unit test, confirmed) |

**Score:** 5/5 truths verified

### ROADMAP Success Criteria (authoritative contract, Step 2a)

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| 1 | Novo serviço `RiscoPrazoService` substitui por completo o método privado + as 3 implementações ad-hoc de `Evento` (dashboard, /processos/dashboard, /eventos/upcoming) | ✓ VERIFIED | Same evidence as Truth 1 + 4 above |
| 2 | Todos os pontos de consumo de `Prazo` (dashboard KPI via listProcessos, listagem/criação/conclusão de prazos) devolvem exatamente os mesmos resultados de antes — zero regressão | ✓ VERIFIED | 2-arg overload preserves `hoje=LocalDate.now()` default at every Prazo call site; characterization test (15/15) locks in the exact threshold table with no drift from the verbatim-moved logic |
| 3 | `Evento` passa a ser avaliado pela mesma tabela de limiares (7d ALTA / 3d outros) em vez das 3 janelas fixas/inconsistentes | ✓ VERIFIED | All 3 Evento call sites confirmed routed through `computeRiscoEvento`, which shares `computeRisco`'s exact threshold logic |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java` | `@Service`, 4 public methods (computeRisco x2, computeRiscoEvento x2) | ✓ VERIFIED | Exists, 46 lines, substantive (real threshold logic + null-guard + named constants, no stubs), wired (imported + injected in ResourceController, called from 10 call expressions across 8 methods) |
| `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java` | Characterization test, `new RiscoPrazoService()` | ✓ VERIFIED | Exists, 119 lines, 15 `@Test` methods, actually executed and passing (see above) |
| `backend/src/main/java/com/lexcv/controllers/ResourceController.java` | 8 Prazo call sites repointed; private method removed; 3 Evento blocks delegate to computeRiscoEvento | ✓ VERIFIED | Import (line 7) + field (line 68) present; all repointing and deletion confirmed above |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `ResourceController.java` | `RiscoPrazoService.computeRisco` | injected field `riscoPrazoService` | ✓ WIRED | `grep -c "riscoPrazoService.computeRisco("` = 8, matches expected count exactly |
| `ResourceController.java` | `RiscoPrazoService.computeRiscoEvento` | 3 Evento call sites | ✓ WIRED (evolved shape) | Direct pattern match `grep -c "riscoPrazoService.computeRiscoEvento("` = 2 (not the plan's literal 3), because 2 of the original 3 direct calls were consolidated into a single shared `isEventoCritico` helper during code review (commit `830f427`, fixing a real duplicate-comment bug — WR-01 iteration 2). Traced by hand: all 3 original consumers (agendaUrgentesCount, prazosCriticosCount, getUpcomingEventos) still terminate in `computeRiscoEvento`. This is a legitimate, git-documented post-plan improvement, not a missing link |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Characterization test suite passes | `cd backend && mvn -o test -Dtest=RiscoPrazoServiceTest` | `Tests run: 15, Failures: 0, Errors: 0` / BUILD SUCCESS | ✓ PASS |
| Full backend module compiles (proves no orphaned `computeRisco(...)` call survived deletion) | `cd backend && mvn -o compile` | BUILD SUCCESS | ✓ PASS |
| No debt markers introduced in phase files | `grep -n "TBD\|FIXME\|XXX\|TODO\|HACK\|PLACEHOLDER"` across the 3 touched files | 0 matches | ✓ PASS |
| All review-fix commits are real, not just narrated | `git cat-file -e` on `02c4c71`, `c72f1bd`, `1a96c4c`, `830f427`, `e6c2cfa`, `6adc5e3` | All 6 exist with matching messages | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| NOTF-22 | 85-01-PLAN.md | "Dashboard, agenda e notificações mostram sempre o mesmo veredito de 'prazo crítico' — lógica consolidada numa única fonte partilhada, substituindo as 4 implementações inconsistentes existentes" | ✓ SATISFIED (at Phase 85's contractually-scoped level) | Backend consolidation (the "4 implementações... espalhadas pelo backend" per the phase's own goal wording) is complete: private `computeRisco` + all 3 backend `Evento` ad-hoc implementations now share `RiscoPrazoService`. See note below re: frontend agenda page. |

No orphaned requirements: `.planning/REQUIREMENTS.md` traceability table maps only `NOTF-22` to Phase 85, and the plan's frontmatter declares exactly `[NOTF-22]` — full match, nothing declared-but-unclaimed.

**Note on requirement scope vs. literal text (not a phase gap):** NOTF-22's literal text mentions "agenda" as a consumer that should show the same verdict. `web/src/app/(dashboard)/agenda/page.tsx:166` still computes its own client-side, case-sensitive `prioridade === "ALTA"` verdict (confirmed by direct read), unaffected by this phase and not consuming the now-enriched `risco` field from `/eventos/upcoming`. This is **not** a gap in this phase's execution — `85-CONTEXT.md`'s own "Deferred Ideas" section explicitly and pre-emptively scoped the frontend agenda page out of Phase 85 ("fora do âmbito desta fase; considerar em milestone futura"), and the ROADMAP's own Phase 85 Success Criteria are explicitly limited to backend implementations ("as 4 implementações inconsistentes hoje espalhadas **pelo backend**"). None of Phases 86-89 currently on the roadmap target this frontend file either, so the milestone-level literal fulfillment of NOTF-22 (including "agenda") remains open as an unscheduled follow-up — informational only, does not block this phase.

### Anti-Patterns Found

None. Scanned all 3 phase-touched files (`RiscoPrazoService.java`, `RiscoPrazoServiceTest.java`, `ResourceController.java` diff since `f29a3f6`) for `TBD`/`FIXME`/`XXX`/`TODO`/`HACK`/`PLACEHOLDER`/empty-return stubs — zero matches. No debt-marker gate triggered.

### Warnings (non-blocking, carried forward from the phase's own 3-iteration code review — independently re-confirmed against current code, not just trusted from the review report)

| # | Finding | Severity | Current Status |
|---|---------|----------|-----------------|
| 1 | No automated controller-level test exists for `agendaUrgentesCount`/`prazosCriticosCount`/`getUpcomingEventos` wiring — only the pure-unit `RiscoPrazoServiceTest` exists anywhere in the backend | ⚠️ WARNING | Still open by design. Independently confirmed: `find src/test -name "*.java"` returns exactly one file; `grep -rl "@WebMvcTest\|@SpringBootTest" src/` returns nothing anywhere in the codebase. Deferred across all 3 fix iterations for a consistent, legitimate reason (no Spring controller-test-slice precedent exists yet to extend) — tracked as a standalone follow-up in `85-REVIEW-FIX.iter3.md`, not silently dropped |
| 2 | `computeRiscoEvento(LocalDateTime,String)` 2-arg overload and the `Objects.requireNonNull(hoje,...)` guard have no direct unit test | ℹ️ INFO | Still open (IN-02/IN-03 in `85-REVIEW.iter3.md`). Low risk today (no call site passes null `hoje`); relevant mainly for Phase 88, which doesn't exist yet |
| 3 | `createPrazo`/`togglePrazoConcluido` still recompute identical risk 2-3 times per request | ℹ️ INFO | Pre-existing pattern, explicitly preserved "by design" per `85-01-PLAN.md` Task 2 — not a regression introduced by this phase |

## Human Verification Required

### 1. ALTA-priority `Evento` with null `dataFim` — should it count as urgent/critical?

**Test:** Create (or review existing) an `Evento` with `prioridade="ALTA"` and `dataFim=null`. Check whether it is counted in the `/dashboard` `prazos_vencer` KPI (`agendaUrgentesCount`) and the `/processos/dashboard` `prazos_criticos_count` KPI.
**Expected:** Confirm with the product owner whether such an event SHOULD count as urgent (restoring pre-phase behavior, which counted every non-concluded ALTA event regardless of date) or SHOULD NOT count (the new, consolidated behavior — `RiscoPrazoService.computeRiscoEvento(null, "ALTA", hoje)` returns `"ok"`, so it is silently excluded).
**Why human:** This is a genuine product/business ambiguity, not a code-correctness bug — the code review process itself (`85-REVIEW.md`, WR-02) explicitly declined to pick a side and flagged its own fix as `requires human verification`. It is a real, demonstrable behavior reversal for a plausible data shape (an open-ended high-priority reminder with no end date) versus the pre-phase logic. Three review/fix iterations passed without this specific question ever being answered by a human — only documented with an in-code comment (`isEventoCritico`, `ResourceController.java:2774-2786`) pointing at exactly this ambiguity.

## Gaps Summary

No BLOCKER-level gaps. All 5 PLAN must-have truths and all 3 ROADMAP success criteria are independently verified against the actual current codebase — not just against `85-01-SUMMARY.md`'s narrative, which in fact under-describes the final state (the SUMMARY was written before 3 rounds of code review/auto-fix meaningfully evolved the `Evento` call sites, added a null-guard, added named constants, and fixed a case-normalization bug). I independently re-ran the characterization test suite (15/15 passing) and a full compile (BUILD SUCCESS) rather than trusting any prior report's claims, and cross-checked all 6 review-fix commit hashes against git history.

The phase is functionally complete and the single shared source of truth (`RiscoPrazoService`) genuinely replaces all 4 backend-side inconsistent implementations. The reason this is not a clean `passed` is a single, still-open, explicitly-flagged **product decision** (not a code defect) that the executor and 3 rounds of code review correctly declined to resolve unilaterally: whether an `ALTA`-priority `Evento` with no `dataFim` should count as urgent in the two dashboard KPIs. This must go to a human before the phase can be considered fully closed, per the review's own `requires human verification` flag that was never subsequently answered.

Separately (informational, not blocking): the milestone-level literal text of NOTF-22 mentions "agenda" as a consumer of the unified verdict, but the frontend agenda page (`web/src/app/(dashboard)/agenda/page.tsx`) still computes its own independent, case-sensitive verdict — this was a deliberate, pre-documented scope decision in `85-CONTEXT.md` (backend-only phase), consistent with the ROADMAP's own Phase 85 Success Criteria, and is not currently scheduled in any of Phases 86-89.

---

*Verified: 2026-07-08T16:43:01Z*
*Verifier: Claude (gsd-verifier)*
