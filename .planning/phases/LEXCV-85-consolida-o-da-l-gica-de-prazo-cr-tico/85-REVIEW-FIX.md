---
phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico
fixed_at: 2026-07-08T16:22:45Z
review_path: .planning/phases/LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico/85-REVIEW.md
iteration: 3
findings_in_scope: 2
fixed: 1
skipped: 1
status: partial
---

# Phase LEXCV-85: Code Review Fix Report

**Fixed at:** 2026-07-08T16:22:45Z
**Source review:** .planning/phases/LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico/85-REVIEW.md
**Iteration:** 3

**Summary:**
- Findings in scope: 2 (WR-01, WR-02 — Critical: 0, so scope is both Warning findings from the iteration-3 re-review; Info findings IN-01/IN-02/IN-03 excluded per `fix_scope: critical_warning`)
- Fixed: 1
- Skipped: 1

**Note:** This is the final allowed auto-fix iteration (3 of 3). Per orchestrator instruction, the auto-fix loop stops after this pass regardless of remaining findings; WR-02 (below) and the three Info findings from `85-REVIEW.md` (IN-01, IN-02, IN-03 — out of `fix_scope: critical_warning`, never attempted by this or any prior iteration) remain open and require manual follow-up.

## Fixed Issues

### WR-01: `Evento.prioridade` validation checks case-insensitively but persists the raw, unnormalized value — inconsistent with `Prazo` and breaks a real case-sensitive frontend consumer

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `6adc5e3`
**Applied fix:** Confirmed current code matched the review's description exactly (line numbers, comment text, and logic all matched `85-REVIEW.md` verbatim — `createEvento:2283-2292`, `updateEvento:2310-2329`, `createPrazo:1475` reference). Applied the review's suggested normalize-on-write pattern to both `Evento` write paths, mirroring `createPrazo`'s existing `payload.prioridade().toUpperCase()` persistence pattern:
- `createEvento`: the allow-list check now computes `prioridadeUpper = evento.getPrioridade().toUpperCase()` once, validates against it, and calls `evento.setPrioridade(prioridadeUpper)` before saving, so the persisted value is always one of the three canonical uppercase strings, never the caller's raw casing.
- `updateEvento`: same normalize-then-set pattern applied inside the existing `if (payload.getPrioridade() != null)` guard, preserving partial-update semantics (normalized and set only when the caller actually supplies a value). The old plain assignment at the former line 2329 (`evento.setPrioridade(payload.getPrioridade())`, no `.toUpperCase()`) was removed and replaced with a short comment pointing back to where `prioridade` is now validated/normalized/set, so the partial-update field list doesn't silently look like `prioridade` was forgotten.

Both edits extend the existing WR-02 (iteration-2) traceability comments in place rather than replacing them, adding a note on *why* normalization matters: the case-sensitive `prioridade === "ALTA"` check in `web/src/app/(dashboard)/agenda/page.tsx:166` that this iteration's review identified as the concrete consumer this gap breaks. No behavior change to `RiscoPrazoService` or any other call site — `computeRisco`/`computeRiscoEvento` already compared case-insensitively, so this fix only changes what gets persisted, closing the gap between the backend's own tolerant KPI math and the frontend's case-sensitive counter.
**Verification:** Tier 1 — re-read both modified methods in full after editing; allow-list check, normalization, and save/return logic all intact, no unrelated lines touched. Tier 2 — ran `mvn -o compile` in the isolated worktree: **BUILD SUCCESS** (exit code 0, no errors introduced).

## Skipped Issues

### WR-02: No automated test coverage for the controller-level risk/validation behavior this phase built — still open, and the untested surface has grown across two fix iterations

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (`isEventoCritico`/`agendaUrgentesCount`, `prazosCriticosCount`, `getUpcomingEventos`, `createEvento`/`updateEvento` `prioridade` allow-list); test file `backend/src/test/java/com/lexcv/services/RiscoPrazoServiceTest.java`
**Reason:** skipped: same infrastructure-deferral reason as iteration-1's WR-04 and iteration-2's WR-03, independently re-verified before skipping rather than assumed from the prior fix reports. Re-confirmed directly this iteration: a fresh listing of `backend/src/test` still returns only `RiscoPrazoServiceTest.java` (plain JUnit, no Spring context), and a fresh grep for `@WebMvcTest`/`@SpringBootTest` across the entire `backend/` tree returns zero matches — there is still no Spring controller-test precedent anywhere in this codebase to extend. The review's own Fix guidance remains explicitly conditional on that infrastructure existing first: *"When the first `@WebMvcTest`/`@SpringBootTest` controller-test slice is built for this codebase, include fixture `Evento` rows for: ..."* Standing up that first slice (test `@Configuration`, mocked/seeded repositories, `@PreAuthorize`/security-context/tenant setup for a large, multi-tenant controller) is a nontrivial infrastructure addition, not a targeted, atomically-committable code fix — forcing an ad-hoc version now risks diverging from whatever shape the eventual first controller-test slice takes, which is the same judgment call both prior iterations made. No source change was applied for this finding.

**Since this is the final auto-fix iteration (3 of 3), this finding will not be revisited by the auto-fix loop.** It should be tracked as a standalone follow-up task (standing up the first `@WebMvcTest`/`@SpringBootTest` slice for `ResourceController`) rather than left as an implicit recurring review finding. The concrete test cases the reviewer specified should be the first fixtures added once that slice exists: (a) ALTA priority + null `dataFim`, (b) a malformed `prioridade` string expecting 400, (c) a valid-but-non-uppercase `prioridade` such as `"alta"` asserting the persisted/returned entity's `prioridade` is `"ALTA"` (this case would have caught this iteration's WR-01 before it shipped), and (d) the day-boundary cases already covered for `Prazo` in `RiscoPrazoServiceTest`, applied against both dashboard KPI endpoints and `getUpcomingEventos`.
**Original issue:** Restating iteration-1's WR-04 / iteration-2's WR-03, confirmed still accurate and still growing in scope: at iteration 1, three controller-level behaviors were untested; two fix passes later, a fourth (the `prioridade` allow-list added in iteration 2, plus its case-normalization fix added in this iteration) is now also untested, with no controller-level regression test in place to catch a future regression in any of them.

---

_Fixed: 2026-07-08T16:22:45Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
