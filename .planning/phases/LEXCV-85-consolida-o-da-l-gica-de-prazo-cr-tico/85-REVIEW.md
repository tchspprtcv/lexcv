---
phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico
reviewed: 2026-07-08T18:30:00Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/RiscoPrazoService.java
  - backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase LEXCV-85: Code Review Report

**Reviewed:** 2026-07-08T18:30:00Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

Re-review (iteration 3) of the `RiscoPrazoService` consolidation, performed after the iteration-2 fix pass (`830f427`, `e6c2cfa`, on top of `02c4c71`/`c72f1bd`/`1a96c4c`/`5e3efcf`/`53fc3ff`). No `<structural_findings>` pre-pass was supplied for this task; every finding below comes from direct adversarial reading of the three files in scope, cross-referenced against this phase's own planning artifacts (`85-CONTEXT.md`, `85-PATTERNS.md`, `85-01-SUMMARY.md`) and git history, not from validating the prior reviews' own claims.

Verification performed directly, independent of the prior review/fix reports' narration:
- `git status --porcelain` on all three files: clean. `HEAD` = `e6c2cfa`, matching the tip of the iteration-2 fix pass exactly — nothing uncommitted, nothing reviewed here is stale.
- `mvn -o compile`: **BUILD SUCCESS**.
- `mvn -o test -Dtest=RiscoPrazoServiceTest`: **BUILD SUCCESS, 15/15 pass**, unchanged from both prior iterations.
- `find backend/src/test -name '*.java'`: `RiscoPrazoServiceTest.java` is still the **only** test file anywhere in the backend.
- Re-verified iteration-2's WR-01 fix (shared `isEventoCritico(Evento e)` helper): both `agendaUrgentesCount` (line 2782) and the `prazosCriticosCount` block inside `getProcessosDashboard` (line 2891) now call `this::isEventoCritico` against the same helper, which carries a single consolidated comment covering both KPIs. Genuinely fixed, no residual duplicate logic, no third undocumented occurrence found anywhere else in the file (confirmed via a full-file grep for `computeRiscoEvento`).
- Re-verified iteration-2's WR-02 fix (`Evento.prioridade` allow-list validation in `createEvento`/`updateEvento`): the validation itself is present, correctly scoped (guarded by `payload.getPrioridade() != null` in `updateEvento` to preserve partial-update semantics), and rejects any value that doesn't case-insensitively match `ALTA`/`MEDIA`/`BAIXA` with a 400. However, tracing exactly what gets *persisted* after that check passes (not just what the review/fix reports asserted) surfaced a genuinely new gap that neither prior iteration caught — see WR-01 below: the check normalizes case only for the membership test, not for the value written to the entity, unlike the pre-existing `createPrazo` pattern it claims to mirror.
- Re-confirmed iteration-2's WR-03 (test coverage) is still unaddressed — the fix report explicitly skipped it again for the same infrastructure reason as iteration 1. Independently confirmed the untested surface area has not shrunk: the brand-new `prioridade` allow-list checks in `createEvento`/`updateEvento` (added by this same fix pass) are themselves not covered by any test, alongside the pre-existing gaps — see WR-02 below.

No security vulnerabilities, crashes, or data-loss risks were found. `RiscoPrazoService` itself remains solid: pure, stateless, tenant-agnostic, correctly null-guarded, with named constants consistently referenced at every call site — confirmed via a full-file grep for stray `"ok"`/`"proximo"`/`"vencido"` literals in `ResourceController.java` (none found outside comments). The issues below are: one new, real data-consistency gap at the `Evento`/`RiscoPrazoService` integration boundary introduced by the very fix meant to close a different gap; the long-standing controller-level test-coverage deferral (now covering a larger surface than when first flagged, two fix iterations in); and three previously-reported, still-open Info items.

Note: `ResourceController.java` contains substantial unrelated code (Decisões/Testemunhas/Factos, honorário idempotent creation, documentos-por-cliente, etc.) that this phase's commits never touched — out of scope and not commented on below.

## Warnings

### WR-01: `Evento.prioridade` validation checks case-insensitively but persists the raw, unnormalized value — inconsistent with `Prazo` and breaks a real case-sensitive frontend consumer

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2283-2292` (`createEvento`), `2310-2329` (`updateEvento`); contrast at `1475` (`createPrazo`)
**Issue:** The iteration-2 fix for `Evento.prioridade` validation (commit `e6c2cfa`) explicitly frames itself as mirroring `createPrazo`'s allow-list pattern (`createPrazo:1461-1466`). It mirrors the *rejection* half of that pattern but not the *normalization* half. `createPrazo` does both: it validates, then stores the normalized value — `String prioridade = payload.prioridade() != null ? payload.prioridade().toUpperCase() : "MEDIA";` (line 1475) is what actually gets persisted onto the `Prazo` entity. `createEvento` and `updateEvento` only do the first half:
```java
// createEvento, line 2287
if (evento.getPrioridade() != null && !prioridadesValidas.contains(evento.getPrioridade().toUpperCase())) {
    return ResponseEntity.badRequest()...
}
evento.setTenantId(getTenantId());
return ResponseEntity.status(HttpStatus.CREATED).body(eventoRepository.save(evento));  // evento.prioridade saved verbatim
```
```java
// updateEvento, line 2329
if (payload.getPrioridade() != null) evento.setPrioridade(payload.getPrioridade());  // no .toUpperCase()
```
`.toUpperCase()` is called only inside the `Set.contains(...)` check, never on the value actually written to the entity. A client submitting `"alta"`, `"Alta"`, or `"aLtA"` passes validation cleanly (case-insensitive match) and that exact casing is what lands in the `t_evento.prioridade` column — confirmed by reading `Evento.java`: plain Lombok `@Getter`/`@Setter`, no custom setter, no `@PrePersist` normalization, nothing that would upper-case the value on the way in.

This isn't just a cosmetic inconsistency between the two entities' write paths. `RiscoPrazoService.computeRisco`/`computeRiscoEvento` itself is unaffected (its threshold check is `"ALTA".equalsIgnoreCase(prioridade)`, so the *backend* KPI math this phase set out to consolidate stays correct regardless of case). But this phase's own `85-CONTEXT.md` documents a second, real consumer of `Evento.prioridade` that this phase deliberately did **not** touch: the frontend agenda page, which unifies `Evento`+`Prazo` client-side and computes its own "urgentes" count with a case-*sensitive* comparison — confirmed directly: `web/src/app/(dashboard)/agenda/page.tsx:166`: `const urgentes = active.filter((e) => e.prioridade === "ALTA").length;`. Any `Evento` created or edited through `createEvento`/`updateEvento` with a valid-but-non-uppercase `prioridade` (e.g. `"Alta"`) will now silently pass backend validation, be counted correctly by `RiscoPrazoService`'s own KPIs, and simultaneously be **silently excluded** from this specific frontend counter — a real, demonstrable behavior gap that the iteration-2 fix's own reasoning ("case is not a correctness concern once the value is constrained to the three valid words") did not account for, because it only considered `RiscoPrazoService`'s own case-insensitive consumer, not the pre-existing case-sensitive one this same phase's context docs already flagged.
**Fix:** Normalize on write, exactly like `createPrazo` already does, in both methods:
```java
// createEvento, replacing the validation block at 2286-2290:
Set<String> prioridadesValidas = Set.of("ALTA", "MEDIA", "BAIXA");
if (evento.getPrioridade() != null) {
    String prioridadeUpper = evento.getPrioridade().toUpperCase();
    if (!prioridadesValidas.contains(prioridadeUpper)) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", "prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"));
    }
    evento.setPrioridade(prioridadeUpper);
}
```
```java
// updateEvento, replacing lines 2313-2319 and the assignment at 2329:
if (payload.getPrioridade() != null) {
    Set<String> prioridadesValidas = Set.of("ALTA", "MEDIA", "BAIXA");
    String prioridadeUpper = payload.getPrioridade().toUpperCase();
    if (!prioridadesValidas.contains(prioridadeUpper)) {
        return ResponseEntity.badRequest().body(Map.of(
                "message", "prioridade inválida. Valores aceites: ALTA, MEDIA, BAIXA"));
    }
    evento.setPrioridade(prioridadeUpper);
}
```
(and remove the now-redundant plain assignment at the old line 2329).

### WR-02: No automated test coverage for the controller-level risk/validation behavior this phase built — still open, and the untested surface has grown across two fix iterations

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — `isEventoCritico`/`agendaUrgentesCount` (2774-2784), `prazosCriticosCount` (2889-2892), `getUpcomingEventos` (2241-2264), `createEvento`/`updateEvento` `prioridade` allow-list (2283-2290, 2310-2319); test file `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java`
**Issue:** Restating iteration-1's WR-04 / iteration-2's WR-03, confirmed still accurate: `RiscoPrazoServiceTest` remains the only test file anywhere in the backend, and it is a plain-JUnit unit test with no Spring context — it cannot and does not exercise how `ResourceController` wires `RiscoPrazoService` into its call sites. This was knowingly deferred twice now (both fix reports cite the same, valid reason: no `@WebMvcTest`/`@SpringBootTest` precedent exists yet in this codebase, and standing one up is legitimate infrastructure work, not a targeted fix). It remains a real, currently open gap, and it has not gotten smaller: at the time of iteration 1's finding, three behaviors were untested (the null-`dataFim` exclusion, the `dataFim`-vs-`dataInicio` field choice, and an as-yet-nonexistent `prioridade` allow-list). Two fix passes later, the `prioridade` allow-list now exists in `createEvento`/`updateEvento` (iteration-2's WR-02 fix) and is itself completely untested — including, concretely, the exact case-normalization gap this iteration's WR-01 identifies, which a controller-level test asserting on the *persisted* entity (not just the HTTP status code) would have caught immediately.
**Fix:** When the first `@WebMvcTest`/`@SpringBootTest` controller-test slice is built for this codebase, include fixture `Evento` rows for: (a) ALTA priority + null `dataFim`, (b) a malformed/non-standard `prioridade` string (expect 400), (c) a valid-but-non-uppercase `prioridade` such as `"alta"` (expect 201/200 **and** assert the persisted/returned entity's `prioridade` is `"ALTA"`, which would currently fail and catch WR-01), and (d) the day-boundary cases already covered for `Prazo` in `RiscoPrazoServiceTest`, applied against both dashboard KPI endpoints and `getUpcomingEventos`.

## Info

### IN-01: Duplicate risk computation persists in `createPrazo` and `togglePrazoConcluido` (still open)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1476, 1496` (`createPrazo`, 2x) and `1523-1524, 1536` (`togglePrazoConcluido`, 3x)
**Issue:** Restating iteration-2's IN-01, confirmed still true and unchanged: `togglePrazoConcluido` calls `riscoPrazoService.computeRisco(prazo.getDataLimite(), prazo.getPrioridade())` three times with identical arguments (lines 1523, 1524, 1536); `createPrazo` calls the analogous 2-arg overload twice (line 1476, then recomputed from `saved` at line 1496 purely for the response body). Both call sites use the 2-arg overload, which internally defaults `hoje = LocalDate.now()` on every invocation — so beyond the wasted computation, there is a theoretical (extremely narrow, not practically exploitable) inconsistency window if two of these calls straddle a midnight boundary within the same request, since each call gets its own independent `LocalDate.now()` snapshot rather than a single shared reference date. Low real-world severity, but it is exactly the kind of duplicated risk computation this phase's consolidation was meant to eliminate, and reusing one computed value would remove both the waste and the theoretical inconsistency at once.
**Fix:**
```java
// createPrazo
String risco = riscoPrazoService.computeRisco(payload.dataLimite(), prioridade);
boolean escalonado = RiscoPrazoService.PROXIMO.equals(risco) || RiscoPrazoService.VENCIDO.equals(risco);
// ... build and save prazo ...
response.put("risco", risco); // reuse instead of recomputing from `saved`

// togglePrazoConcluido
String risco = riscoPrazoService.computeRisco(prazo.getDataLimite(), prazo.getPrioridade());
boolean nowEscalonado = !nowConcluido && (RiscoPrazoService.PROXIMO.equals(risco) || RiscoPrazoService.VENCIDO.equals(risco));
// ...
response.put("risco", risco);
```

### IN-02: `computeRiscoEvento(LocalDateTime, String)` 2-arg overload still has no direct test (still open)

**File:** `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java`; overload at `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java:43-45`
**Issue:** Restating iteration-2's IN-02, re-confirmed by reading the full test file end to end (15 tests, enumerated): the suite directly exercises `computeRisco(LocalDate, String)`'s 2-arg overload (`computeRisco2Args_dataNull_retornaOk`, `computeRisco2Args_dataOntem_retornaVencido`), but every `computeRiscoEvento` test call passes all 3 args (`dataEvento, prioridade, HOJE`). The one-line delegation at lines 43-45 remains untested directly, breaking the otherwise-consistent 2-arg/3-arg symmetry across the class's four public methods.
**Fix:**
```java
@Test
void computeRiscoEvento2Args_dataOntem_retornaVencido() {
    assertEquals("vencido", service.computeRiscoEvento(LocalDateTime.now().minusDays(1), "MEDIA"));
}
```

### IN-03: `Objects.requireNonNull(hoje, ...)` guard still has no test coverage (still open)

**File:** `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java` (missing); guard at `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java:25`
**Issue:** Restating iteration-2's IN-03, re-confirmed: none of the 15 existing tests pass `null` as the `hoje` argument to the 3-arg `computeRisco`/`computeRiscoEvento` overloads. The guard exists specifically to fail fast for a future caller (Phase 88's scheduled job) that might pass a null reference date — but with zero test coverage, a future edit that accidentally weakens or removes `Objects.requireNonNull(hoje, ...)` would compile and pass the entire suite undetected, silently reintroducing the exact `NullPointerException` risk this guard was added to close.
**Fix:**
```java
@Test
void computeRisco_hojeNulo_lancaNullPointerException() {
    assertThrows(NullPointerException.class,
            () -> service.computeRisco(HOJE, "ALTA", null));
}
```
(requires adding `import static org.junit.jupiter.api.Assertions.assertThrows;`)

---

_Reviewed: 2026-07-08T18:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
