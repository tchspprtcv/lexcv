---
phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico
reviewed: 2026-07-08T16:45:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/RiscoPrazoService.java
  - backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
findings:
  critical: 0
  warning: 3
  info: 3
  total: 6
status: issues_found
---

# Phase LEXCV-85: Code Review Report

**Reviewed:** 2026-07-08T16:45:00Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

Re-review (iteration 2) of the `RiscoPrazoService` consolidation, performed after the iteration-1 fix pass (`02c4c71`, `c72f1bd`, `1a96c4c` on top of the original `53fc3ff`/`5e3efcf`). This pass treated the codebase adversarially rather than validating the prior review/fix reports' own claims:

- Confirmed `git status` is clean for all three files and HEAD (`1a96c4c`) is exactly the state reviewed below — nothing uncommitted.
- Re-ran `cd backend && mvn -o test -Dtest=RiscoPrazoServiceTest`: **BUILD SUCCESS, 15/15 pass** (unchanged from iteration 1).
- Ran `cd backend && mvn -o compile`: **BUILD SUCCESS** — the full module still compiles cleanly with the constants/null-guard changes.
- Grepped `ResourceController.java` for any residual `"vencido"`/`"proximo"`/`"ok"` string literal outside comments — found none. Iteration-1's WR-03 (magic strings) is genuinely and completely fixed; confirmed via `git show 1a96c4c` that the substitution was a clean, mechanical, literal-for-constant swap with no logic changes.
- Manually re-verified iteration-1's WR-01 fix (`Objects.requireNonNull(hoje, ...)`): present, correctly placed as the first statement of the real 3-arg `computeRisco`, doesn't affect any existing 2-arg call site (none of which ever pass null). Genuinely fixed.
- Re-examined iteration-1's WR-02 (undated ALTA-priority events silently excluded from a dashboard KPI): the applied fix is a comment only (by design — explicitly deferred to a human product decision, flagged `requires human verification` in the fix report). Confirmed the comment is accurate and the filter logic is byte-identical to what iteration 1 reviewed. However, tracing the same `computeRiscoEvento(dataFim, ...)` call pattern to its *second*, independent use site turned up an identical, completely undocumented instance of the same gap (see WR-01 below) — the iteration-1 fix addressed only one of the two affected KPIs, so the "requires human verification" ask is currently incomplete.
- Traced `Evento.prioridade` — the field both dashboard KPIs now key their 3-vs-7-day threshold off of — back to its write paths (`createEvento`/`updateEvento`, read in full) and confirmed it has zero validation anywhere: not in the entity, not in either controller method, not at the DB level (this project has no SQL migration files at all; schema comes entirely from Hibernate `ddl-auto`, confirmed via a repo-wide glob for `*.sql`). This is unlike `Prazo.prioridade`, which `createPrazo` validates against an allow-list (`ResourceController.java:1461-1466`). This directly interacts with this phase's own design choice to key the shared threshold table off a case-insensitive `"ALTA"` string match (see WR-02 below).

No security vulnerabilities, crashes, or data-loss risks were found. `RiscoPrazoService` itself is now solid: pure, stateless, tenant-agnostic, with a correct null-guard and named constants — a clean, complete piece of infrastructure. The remaining issues are dashboard-KPI correctness gaps at the `Evento` integration boundary (real, but bounded to miscounted dashboard tallies, not data corruption or security exposure), plus code-quality follow-ups carried over from iteration 1 that remain valid and unaddressed.

Note: `ResourceController.java` contains substantial unrelated code (Decisões/Testemunhas/Factos, honorário idempotent creation, documentos-por-cliente, etc.) that this phase's commits never touched — out of scope and not commented on below.

## Warnings

### WR-01: The iteration-1 fix for the null-`dataFim`/ALTA corner case only covers one of its two identical occurrences

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2861-2870` (undocumented occurrence); the sibling comment is at `2742-2750`
**Issue:** Iteration 1's WR-02 established that `computeRiscoEvento(e.getDataFim(), e.getPrioridade())` returns `"ok"` for any event with a null `dataFim` regardless of priority, so an ALTA-priority event created with no end date is silently excluded from a dashboard count. The applied fix was, deliberately, a documentation-only comment above `agendaUrgentesCount` (lines 2742-2750) flagging this for human product sign-off, since changing the behavior is a product decision, not a code-correctness bug. That comment is accurate and still present. But the *exact same* `riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade())` pattern, over the *same* `eventoRepository.findByTenantIdAndConcluido(tenantId, false)` source list, is independently used again at lines 2864-2870 inside `getProcessosDashboard` to compute `prazosCriticosCount` (the `/processos/dashboard` `prazos_criticos_count` KPI) — and this second call site has no comment, no cross-reference, and was never mentioned in the iteration-1 review or fix report. A human reading only the `agendaUrgentesCount` note would reasonably assume the corner case is scoped to the `/dashboard` `prazos_vencer` metric; in reality the identical undated-ALTA-event undercount also silently affects the separate `/processos/dashboard` "prazos críticos" metric, and nobody has been asked to sign off on that half of it.
**Fix:** Resolve the shared root cause once (e.g., extract a private `isEventoCritico(Evento e)` helper used by both call sites, with a single comment above the helper), or at minimum duplicate the existing comment above the `prazosCriticosCount` loop so the human-verification request explicitly covers both KPIs:
```java
// NOTA (WR-02, 85-REVIEW.md): mesma ressalva de agendaUrgentesCount (linha ~2742) —
// eventos ALTA com dataFim nula avaliam para "ok" e por isso NÃO contam aqui também.
List<Evento> eventos = eventoRepository.findByTenantIdAndConcluido(tenantId, false);
for (Evento e : eventos) {
    String risco = riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade());
    ...
```

### WR-02: `Evento.prioridade` is unvalidated free text, yet this phase now lets it silently swap a dashboard KPI's effective time window between 3 and 7 days

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2276-2317` (no validation in `createEvento`/`updateEvento`), consumed at `2755` and `2866`; threshold logic in `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java:29`
**Issue:** `Prazo.prioridade` is validated against `Set.of("ALTA", "MEDIA", "BAIXA")` in `createPrazo` (lines 1461-1466), rejecting anything else with a 400. `Evento.prioridade` has no equivalent check in `createEvento` or `updateEvento` (confirmed by reading both methods in full — `updateEvento`'s partial-update logic at lines 2305-2314 copies `payload.getPrioridade()` verbatim whenever non-null, with no allow-list check) and no DB-level constraint either. Before this phase, `prazosCriticosCount` ignored `prioridade` entirely (fixed 7-day window for every event, confirmed via `git diff 2ce48f7..HEAD`). After this phase, `prazosCriticosCount` — like `agendaUrgentesCount` — derives its effective threshold directly from `"ALTA".equalsIgnoreCase(prioridade)` (`RiscoPrazoService.java:29`): exactly `"ALTA"` (any case) gets the wide 7-day window, and *anything else* — a typo, trailing whitespace, a legacy/free-text value like `"Urgente"` or `"Alta prioridade"` — silently falls back to the strict 3-day window, with no error, no warning, and no way for the caller to know the event is being scored on the wrong table. This is a new coupling this phase introduces between a completely unvalidated free-text column and dashboard KPI correctness for `prazosCriticosCount` specifically (`agendaUrgentesCount` had a narrower version of this exposure pre-phase, since it already gated purely on the same string match, just without a date component).
**Fix:** Reuse the same allow-list already used for `Prazo.prioridade`, mirroring `createPrazo:1461-1466`:
```java
Set<String> prioridadesValidas = Set.of("ALTA", "MEDIA", "BAIXA");
if (evento.getPrioridade() != null && !prioridadesValidas.contains(evento.getPrioridade().toUpperCase())) {
    return ResponseEntity.badRequest().body(Map.of(
            "message", "prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"));
}
```
applied in both `createEvento` and the `payload.getPrioridade() != null` branch of `updateEvento`.

### WR-03: No automated test coverage for the controller call sites with intentionally-changed behavior (still open)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (`agendaUrgentesCount` 2751-2759, `prazosCriticosCount` 2861-2870, `getUpcomingEventos` 2239-2264)
**Issue:** Restating iteration-1's WR-04, confirmed still accurate and still unaddressed. This was knowingly deferred per the fix report (not a fresh miss), but it remains a real, currently-open gap: `RiscoPrazoServiceTest` is still the only test file anywhere in the backend and covers only the pure service, not how the controller wires it in. Combined with WR-01 and WR-02 above, there are now at least three untested behaviors that a future edit could silently break while the entire test suite (still just these same 15 tests) stays green: the null-`dataFim` exclusion, the free-text-`prioridade` fallback, and the `dataFim`-vs-`dataInicio` field choice itself.
**Fix:** When the first `@WebMvcTest`/`@SpringBootTest` controller-test slice is built for this codebase, include fixture `Evento` rows for: (a) ALTA priority + null `dataFim`, (b) a malformed/non-standard `prioridade` string, and (c) the day-boundary cases already covered for `Prazo` in `RiscoPrazoServiceTest`, applied against both dashboard KPI endpoints.

## Info

### IN-01: Duplicate risk computation persists in `createPrazo` and `togglePrazoConcluido`

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1476, 1496` (createPrazo, 2x) and `1523-1524, 1536` (togglePrazoConcluido, 3x)
**Issue:** `togglePrazoConcluido`'s triple identical call to `riscoPrazoService.computeRisco(prazo.getDataLimite(), prazo.getPrioridade())` was already flagged in iteration 1 (IN-01) as intentionally preserved per the plan; still present and unchanged, now referencing the `RiscoPrazoService` constants but still three separate calls with identical arguments. `createPrazo` has the same pattern at smaller scale — `risco` is computed once at line 1476 to derive `escalonado`, then recomputed from `saved` at line 1496 purely for the response body — which was not called out in iteration 1 and has no "preserve as-is" note in the plan, so it's a low-value straggler the phase's own consolidation goal was meant to catch.
**Fix:**
```java
String risco = riscoPrazoService.computeRisco(payload.dataLimite(), prioridade);
boolean escalonado = RiscoPrazoService.PROXIMO.equals(risco) || RiscoPrazoService.VENCIDO.equals(risco);
// ... build and save prazo ...
response.put("risco", risco); // reuse instead of recomputing from `saved`
```

### IN-02: `computeRiscoEvento(LocalDateTime, String)` 2-arg overload still has no direct test

**File:** `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java`
**Issue:** Restating iteration-1's IN-02, confirmed still true: the suite directly tests `computeRisco(LocalDate, String)`'s 2-arg overload (lines 83-91), but only ever calls the 3-arg form of `computeRiscoEvento`. The 2-arg `computeRiscoEvento(LocalDateTime, String)` overload — a one-line delegation at `RiscoPrazoService.java:43-45` — still has zero direct test, breaking the otherwise-consistent 2-arg/3-arg symmetry across the other three public methods.
**Fix:** `assertEquals("vencido", service.computeRiscoEvento(LocalDateTime.now().minusDays(1), "MEDIA"));`

### IN-03: The new `Objects.requireNonNull(hoje, ...)` guard has no test coverage

**File:** `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java` (missing); guard is at `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java:25`
**Issue:** Iteration 1's WR-01 fix added `Objects.requireNonNull(hoje, "hoje não pode ser nulo")` as the first line of the 3-arg `computeRisco`, specifically to fail fast for the Phase 88 scheduled job this service exists for. None of the 15 existing tests exercise this path (re-confirmed by reading the full test file end to end) — a future edit that accidentally removes or weakens the guard (plausible, since nothing currently calls it with `null`, making it look like unreachable defensive code to an unwary editor) would compile and pass the entire suite undetected, silently reintroducing the exact `NullPointerException` risk WR-01 was meant to close.
**Fix:**
```java
@Test
void computeRisco_hojeNulo_lancaNullPointerException() {
    assertThrows(NullPointerException.class,
            () -> service.computeRisco(HOJE, "ALTA", null));
}
```

---

_Reviewed: 2026-07-08T16:45:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
