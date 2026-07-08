---
phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico
reviewed: 2026-07-08T15:00:17Z
depth: standard
files_reviewed: 3
files_reviewed_list:
  - backend/src/main/java/com/lexcv/services/RiscoPrazoService.java
  - backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java
  - backend/src/main/java/com/lexcv/controllers/ResourceController.java
findings:
  critical: 0
  warning: 4
  info: 2
  total: 6
status: issues_found
---

# Phase LEXCV-85: Code Review Report

**Reviewed:** 2026-07-08T15:00:17Z
**Depth:** standard
**Files Reviewed:** 3
**Status:** issues_found

## Summary

Reviewed the `RiscoPrazoService` extraction (Plan 01 of Phase LEXCV-85): a new pure, injectable `@Service` that consolidates the "prazo crítico" (ok/proximo/vencido) verdict previously duplicated as a private method in `ResourceController` plus three ad-hoc `Evento`-based implementations. Verified the mechanical correctness of the extraction directly:

- Confirmed via `git diff` against the immediate pre-phase commit that exactly the documented 11 call sites (8 `Prazo`-based, 3 `Evento`-based) were repointed and the private `computeRisco` method was deleted, with no other lines touched in `ResourceController.java`.
- Ran `cd backend && mvn -o test -Dtest=RiscoPrazoServiceTest`: **BUILD SUCCESS, 15/15 tests pass**, confirming the characterization test suite genuinely exercises and locks in the threshold table (day-boundary cases at 0/3/7/8 days, null handling, `equalsIgnoreCase`, and the `LocalDateTime`→`LocalDate` conversion semantics for `Evento`).
- Grepped the full controller for any residual `"vencido"`/`"proximo"` risk logic outside the new service's call sites — none found, so the stated goal ("no duplicate copy of the threshold logic remains in the controller") is genuinely met.
- Confirmed `Evento.dataFim`/`dataInicio`/`prioridade` are nullable columns with no controller-side validation forcing their presence, which is directly relevant to one of the findings below.

No security vulnerabilities, crashes, or data-loss risks were found in the reviewed code — `RiscoPrazoService` is a pure, stateless, tenant-agnostic function exactly as designed, and the mechanical repointing is byte-identical to the prior behavior for all `Prazo`-based call sites, as intended. The issues below are real but moderate: a latent null-argument gap in the new service's forward-looking 3-arg overload, an unaddressed edge case in one of the two intentionally-changed `Evento` KPIs, residual magic-string duplication the consolidation didn't reach, and some test/duplication follow-ups.

Note: `ResourceController.java` contains substantial unrelated code (Decisões/Testemunhas/Factos, honorário idempotent creation, documentos-por-cliente, etc.) that is pre-existing and untouched by this phase's commits (`53fc3ff`, `5e3efcf`) — confirmed via a scoped `git diff` of just those two commits. That code is out of scope for this review and is not commented on below.

## Warnings

### WR-01: Missing null-guard on the `hoje` parameter — latent NPE for the very next phase that depends on it

**File:** `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java:16-22`
**Issue:** `computeRisco(LocalDate dataLimite, String prioridade, LocalDate hoje)` calls `dataLimite.isBefore(hoje)` (line 18) with no validation that `hoje` is non-null. Today this never manifests — every call site in `ResourceController.java` uses the 2-arg overload, which always supplies `LocalDate.now()` (never null). But the class's own comment states this 3-arg overload exists specifically "usado pela Phase 88 para determinismo em testes" (used by Phase 88 for deterministic tests), i.e. its entire reason for existing is to let a *future, external caller* inject an arbitrary reference date. `computeRiscoEvento(LocalDateTime, String, LocalDate hoje)` (lines 30-32) has the identical exposure since it delegates straight into `computeRisco` with whatever `hoje` it's given. A null (or accidentally-swapped) `hoje` argument from Phase 88's scheduled job will throw an unguarded `NullPointerException`.
**Fix:**
```java
public String computeRisco(LocalDate dataLimite, String prioridade, LocalDate hoje) {
    java.util.Objects.requireNonNull(hoje, "hoje não pode ser nulo");
    if (dataLimite == null) return "ok";
    if (dataLimite.isBefore(hoje)) return "vencido";
    long diasRestantes = ChronoUnit.DAYS.between(hoje, dataLimite);
    int limiarProximo = "ALTA".equalsIgnoreCase(prioridade) ? 7 : 3;
    return diasRestantes <= limiarProximo ? "proximo" : "ok";
}
```

### WR-02: `agendaUrgentesCount` silently drops undated ALTA-priority events from the urgent KPI

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:2742-2750`
**Issue:** Before this phase, `agendaUrgentesCount` counted every non-concluded event with `prioridade="ALTA"` regardless of any date — the old filter (`"ALTA".equalsIgnoreCase(e.getPrioridade())`) never read `dataFim`/`dataInicio` at all. The rewrite to `riscoPrazoService.computeRiscoEvento(e.getDataFim(), e.getPrioridade())` is the documented, intentional change for this phase (85-01-PLAN.md, Site 6). However, `Evento.dataFim` is a nullable column (`Evento.java:34-35`) and neither `createEvento` nor `updateEvento` require it to be set (they only compare `dataFim` to `dataInicio` when *both* are present). Since `computeRiscoEvento(null, prioridade, hoje)` short-circuits to `"ok"` (via `computeRisco(null, ...)` returning `"ok"` for any null date), an ALTA-priority event with no `dataFim` now **always** evaluates to `"ok"` and is silently excluded from the `/dashboard` `prazos_vencer` count — a full reversal from the prior behavior for that specific, plausible data shape (e.g. an open-ended high-priority reminder with only a start time). This corner case isn't discussed in the phase docs (which focus on the "no window vs. fixed window" difference) and isn't covered by any test.
**Fix:** Make an explicit product decision for this corner case and encode it — e.g. treat `ALTA` + null `dataFim` as inherently urgent (mirroring the old behavior for that one case), or explicitly accept the new behavior with a code comment and a regression test asserting it.

### WR-03: Risk-value comparisons remain duplicated as magic strings across the controller

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:947-950, 1477, 1523-1524, 2747, 2858` (values defined in `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java:21`)
**Issue:** `RiscoPrazoService` consolidates the *computation* of risk, but its three possible outputs (`"ok"`, `"proximo"`, `"vencido"`) are still hand-typed as string literals at every call site that branches on the result. This phase adds two brand-new raw-string comparison sites (`agendaUrgentesCount`, `prazosCriticosCount`), growing rather than shrinking the number of places a typo (e.g. `"vencid0"`) would compile cleanly and silently break only that one call site — exactly the class of drift the phase's own consolidation was meant to eliminate.
**Fix:** Expose the three values as constants (or a small enum) on `RiscoPrazoService`, e.g. `public static final String VENCIDO = "vencido";`, and have every call site reference `RiscoPrazoService.VENCIDO` instead of retyping the literal.

### WR-04: No automated test coverage for the controller call sites with intentionally-changed behavior

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (`agendaUrgentesCount` 2742-2750, `prazosCriticosCount` 2852-2861, `getUpcomingEventos` 2261)
**Issue:** `RiscoPrazoServiceTest` — confirmed to be the only test file anywhere in the backend (`backend/src/test/**/*.java` resolves to exactly this one file) — thoroughly tests the pure `RiscoPrazoService` functions, but nothing exercises how `ResourceController` wires those functions into the three call sites whose behavior this phase deliberately changed. A future edit that swaps `dataFim` for `dataInicio`, inverts the `"proximo"/"vencido"` check, or reintroduces a fixed window would compile and pass the full existing test suite undetected.
**Fix:** Track as a follow-up (a `@WebMvcTest`/`@SpringBootTest` slice covering `/dashboard` and `/processos/dashboard` KPI counts with fixture `Evento` rows spanning null/near/far dates and mixed priorities) rather than blocking this phase on it, since no controller-test precedent exists yet in this codebase.

## Info

### IN-01: `togglePrazoConcluido` recomputes the identical risk three times

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java:1519-1536`
**Issue:** `riscoPrazoService.computeRisco(prazo.getDataLimite(), prazo.getPrioridade())` is called twice on lines 1523-1524 (to derive `nowEscalonado`) and a third time on line 1536 (for the response body), all with identical arguments. This redundancy pre-dates the phase and was explicitly preserved "by design" per `85-01-PLAN.md` (Task 2, step 2: "linhas 1530 e 1531 chamam computeRisco duas vezes... PRESERVAR essa redundância"), so it isn't a regression — but it's a leftover instance of exactly the kind of duplicated risk computation this phase's consolidation set out to remove.
**Fix:**
```java
String risco = riscoPrazoService.computeRisco(prazo.getDataLimite(), prazo.getPrioridade());
boolean nowEscalonado = !nowConcluido && ("proximo".equals(risco) || "vencido".equals(risco));
prazo.setEscalonado(nowEscalonado);
Prazo saved = prazoRepository.save(prazo);
...
response.put("risco", risco);
```

### IN-02: `computeRiscoEvento(LocalDateTime, String)` 2-arg overload has no direct test

**File:** `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java`
**Issue:** The suite directly tests the `computeRisco(LocalDate, String)` 2-arg overload (lines 84-91: null case and stable "yesterday" case), but never directly calls the analogous `computeRiscoEvento(LocalDateTime, String)` 2-arg overload — only its 3-arg counterpart is exercised (lines 97-118). The delegation is a one-line pass-through so the risk is low, but one of the four public methods is left without a direct test, breaking the otherwise-consistent 2-arg/3-arg test symmetry.
**Fix:** Add a test mirroring `computeRisco2Args_dataOntem_retornaVencido`, e.g. `assertEquals("vencido", service.computeRiscoEvento(LocalDateTime.now().minusDays(1), "MEDIA"));`.

---

_Reviewed: 2026-07-08T15:00:17Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
