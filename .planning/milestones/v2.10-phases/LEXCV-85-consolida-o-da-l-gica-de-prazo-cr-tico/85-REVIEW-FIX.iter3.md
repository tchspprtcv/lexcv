---
phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico
fixed_at: 2026-07-08T15:46:47Z
review_path: .planning/phases/LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico/85-REVIEW.md
iteration: 2
findings_in_scope: 3
fixed: 2
skipped: 1
status: partial
---

# Phase LEXCV-85: Code Review Fix Report

**Fixed at:** 2026-07-08T15:46:47Z
**Source review:** .planning/phases/LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico/85-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 3 (WR-01, WR-02, WR-03 — Critical: 0, so scope is all 3 Warning findings from the iteration-2 re-review; Info findings IN-01/IN-02/IN-03 excluded per `fix_scope: critical_warning`)
- Fixed: 2
- Skipped: 1

## Fixed Issues

### WR-01: The iteration-1 fix for the null-`dataFim`/ALTA corner case only covers one of its two identical occurrences

**Status:** fixed
**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `830f427`
**Applied fix:** Took the review's preferred option (extract a shared helper) rather than the "at minimum duplicate the comment" fallback. Extracted a new private `isEventoCritico(Evento e)` helper — carrying a single consolidated comment documenting the ALTA+null-`dataFim` corner case — and rewired both call sites to use it via method reference:
- `agendaUrgentesCount` (the `/dashboard` `prazos_vencer` KPI, originally lines 2751-2759): now `eventoRepository.findByTenantIdAndConcluido(tenantId, false).stream().filter(this::isEventoCritico).count()`.
- The previously-undocumented `prazosCriticosCount` loop inside `getProcessosDashboard` (the `/processos/dashboard` `prazos_criticos_count` KPI, originally lines 2861-2870): rewritten from a manual `for` loop with a local `eventos` variable (confirmed unused elsewhere in the method after the loop) to the same one-line stream form, sharing the identical helper.

This closes the exact gap the finding described: previously only `agendaUrgentesCount` had a human-verification comment, and the byte-identical pattern in `prazosCriticosCount` had none. Now there is exactly one comment, attached to the one helper both KPIs call, so a human reviewing it knows unambiguously that the corner case affects both dashboard metrics. Comment text was updated in place to reference both KPIs by name and to point the test-coverage follow-up at WR-03 (this iteration's numbering) instead of the stale WR-04 (iteration-1 numbering).
**Verification:** Re-read both modified regions (Tier 1: helper present, both call sites reference `this::isEventoCritico`, no stray duplicate logic left, comment reads correctly). Ran `mvn -o compile` (Tier 2): BUILD SUCCESS — confirmed via compiled-class mtime landing after the edit's save time, i.e. the edited source actually compiled clean. Also re-ran `mvn -o test -Dtest=RiscoPrazoServiceTest` afterward as an extra regression check (not required, since this finding never touches `RiscoPrazoService.java`): still passes, exit code 0.

### WR-02: `Evento.prioridade` is unvalidated free text, yet this phase now lets it silently swap a dashboard KPI's effective time window between 3 and 7 days

**Status:** fixed
**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `e6c2cfa`
**Applied fix:** Reused the exact allow-list pattern already present in `createPrazo` (`Set.of("ALTA", "MEDIA", "BAIXA")`, case-insensitive match via `.toUpperCase()`, `400` with the same Portuguese message) and applied it to both `Evento` write paths named in the finding:
- `createEvento`: validation added right after the existing date-order check, before `evento.setTenantId(...)`/save.
- `updateEvento`: validation added guarded by `if (payload.getPrioridade() != null)` — i.e. only runs when the caller actually supplies a new `prioridade`, matching the method's existing partial-update semantics (an omitted field must not be treated as invalid input) — placed before the partial-update block that copies `payload` fields onto the fetched `evento`.

Scope was kept to validation only, exactly as the review's Fix snippet shows (it cites `createPrazo:1461-1466`, the validation lines, not `createPrazo`'s separate `.toUpperCase()` normalization-on-save at line ~1475); no change was made to how the value is persisted, since `RiscoPrazoService.computeRisco`'s threshold check is already `"ALTA".equalsIgnoreCase(...)`, so case is not a correctness concern once the value is constrained to the three valid words.
**Verification:** Re-read both modified methods (Tier 1: allow-list check present in both, correctly scoped to the partial-update guard in `updateEvento`, no unrelated lines touched). Ran `mvn -o compile` (Tier 2): BUILD SUCCESS, 105 source files recompiled with no errors (one pre-existing, unrelated deprecation warning in `MinioConfig.java`, present before this change).

## Skipped Issues

### WR-03: No automated test coverage for the controller call sites with intentionally-changed behavior (still open)

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (`agendaUrgentesCount`, `prazosCriticosCount`, `getUpcomingEventos`); test file `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java`
**Reason:** skipped: reviewer's own Fix guidance is explicitly conditional on future infrastructure, not a directive to build it now. Verbatim from `85-REVIEW.md`: "When the first `@WebMvcTest`/`@SpringBootTest` controller-test slice is built for this codebase, include fixture `Evento` rows for: ..." This is the same situation iteration 1's equivalent finding (WR-04) was in, and nothing about the underlying constraint has changed since: `RiscoPrazoServiceTest` (confirmed, re-read end to end this iteration) is still the only test file anywhere in the backend, it is a plain-JUnit unit test with no Spring context, and there is still no `@WebMvcTest`/`@SpringBootTest` precedent anywhere in this codebase to extend. Standing up the first Spring controller-test slice — test `@Configuration`, mocked/seeded repositories, `@PreAuthorize`/security-context setup for a multi-tenant controller — is a nontrivial infrastructure addition, not a targeted code fix appropriate for adaptive, atomic application in this pass. Forcing an ad-hoc version of that infrastructure now would risk duplicating (and likely diverging from) whatever shape the eventual first controller-test slice takes. No source change was applied for this finding; it remains tracked as a follow-up.
**Original issue:** Restating iteration-1's WR-04, confirmed still accurate: with WR-01 and WR-02 now fixed in this pass, there remain at least three untested controller-level behaviors that a future edit could silently break while the full test suite (still the same 15 `RiscoPrazoServiceTest` cases) stays green — the null-`dataFim` exclusion (now centralized in `isEventoCritico`, but still untested at the controller level), the free-text-`prioridade` fallback (now guarded by the new allow-list, but the guard itself has no test), and the `dataFim`-vs-`dataInicio` field choice in `getUpcomingEventos` vs. the two KPI counters.

---

_Fixed: 2026-07-08T15:46:47Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
