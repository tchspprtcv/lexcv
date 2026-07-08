---
phase: LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico
fixed_at: 2026-07-08T15:15:32Z
review_path: .planning/phases/LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico/85-REVIEW.md
iteration: 1
findings_in_scope: 4
fixed: 3
skipped: 1
status: partial
---

# Phase LEXCV-85: Code Review Fix Report

**Fixed at:** 2026-07-08T15:15:32Z
**Source review:** .planning/phases/LEXCV-85-consolida-o-da-l-gica-de-prazo-cr-tico/85-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 4 (WR-01, WR-02, WR-03, WR-04 — Critical: 0, so scope is all 4 Warning findings; Info findings IN-01/IN-02 excluded per `fix_scope: critical_warning`)
- Fixed: 3
- Skipped: 1

## Fixed Issues

### WR-01: Missing null-guard on the `hoje` parameter — latent NPE for the very next phase that depends on it

**Status:** fixed
**Files modified:** `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java`
**Commit:** `02c4c71`
**Applied fix:** Added `Objects.requireNonNull(hoje, "hoje não pode ser nulo")` (with a new `import java.util.Objects;`) as the first statement of the 3-arg `computeRisco(LocalDate, String, LocalDate)` overload — the "real" implementation that `computeRiscoEvento(LocalDateTime, String, LocalDate)` also delegates into. A null (or accidentally-swapped) `hoje` argument from a future caller, e.g. Phase 88's scheduled job, now fails fast with a clear message instead of throwing an unguarded `NullPointerException` at `dataLimite.isBefore(hoje)`. Matches the review's suggested fix as-is; current code matched the reviewed snippet exactly.
**Verification:** Re-read the modified method (Tier 1: fix present, surrounding logic intact). Ran `mvn -o test -Dtest=RiscoPrazoServiceTest` (Tier 2): BUILD SUCCESS, 15/15 pass unchanged — none of the existing tests pass a null `hoje` to the 3-arg overload, so no behavior regressed.

### WR-02: `agendaUrgentesCount` silently drops undated ALTA-priority events from the urgent KPI

**Status:** fixed: requires human verification
**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `c72f1bd`
**Applied fix:** The review's Fix explicitly frames this as needing "an explicit product decision" between two options: (a) treat `ALTA` + null `dataFim` as inherently urgent (restoring pre-phase behavior for that one data shape), or (b) accept the new behavior explicitly via a code comment plus a regression test. Because option (a) changes what a user sees in the `/dashboard` `prazos_vencer` KPI count — a product-visible behavior choice, not a pure code-correctness bug — I did not unilaterally pick a side. I applied the non-behavior-changing half of option (b): added an explicit comment directly above `agendaUrgentesCount` documenting the corner case (ALTA-priority event + null `dataFim` → `computeRiscoEvento` returns `"ok"` → excluded from the urgent count), stating this reverses the pre-phase behavior for that shape, and describing exactly how to flip it if product decides the corner case should count as urgent. No logic/condition was changed.

The "regression test asserting it" half of option (b) was intentionally **not** added: `agendaUrgentesCount` is a private controller method, and the only test file in this backend (`RiscoPrazoServiceTest`) is a plain-JUnit unit test with no Spring context — covering this call site would require the first `@WebMvcTest`/`@SpringBootTest` controller-test slice in the codebase, which is precisely the infrastructure WR-04 (this same review) explicitly recommends deferring rather than building now.

**A human should still confirm** whether the current behavior (undated ALTA events excluded from the urgent count) is the intended product behavior, or whether option (a) should be implemented instead. This is flagged as `fixed: requires human verification` rather than plain `fixed` because the underlying behavior question — not just the code — needs sign-off.
**Verification:** Re-read the modified method (Tier 1: comment present, filter logic byte-identical to before). Ran `mvn -o compile` (Tier 2): BUILD SUCCESS.

### WR-03: Risk-value comparisons remain duplicated as magic strings across the controller

**Status:** fixed
**Files modified:** `backend/src/main/java/com/lexcv/services/RiscoPrazoService.java`, `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `1a96c4c`
**Applied fix:** Added `public static final String OK = "ok"`, `PROXIMO = "proximo"`, `VENCIDO = "vencido"` to `RiscoPrazoService`, and updated its own `computeRisco` return statements to use them (so the constants are the actual single source of truth, not just a parallel copy of the literals). Replaced every magic-string risk comparison/assignment in `ResourceController.java` with references to `RiscoPrazoService.OK/.PROXIMO/.VENCIDO`:
- `risco_mais_critico`/`tem_prazo_escalonado` computation (originally ~947-950)
- `createPrazo`'s `escalonado` derivation (originally ~1477)
- `togglePrazoConcluido`'s `nowEscalonado` derivation (originally ~1523-1524) — preserved the existing duplicated-call structure verbatim (that redundancy is intentionally kept per IN-01 / `85-01-PLAN.md` and is out of this finding's scope)
- `agendaUrgentesCount` (the phase's new call site, originally ~2747)
- `prazosCriticosCount` (the phase's new call site, originally ~2858)

Confirmed via `grep` that no `"proximo"`/`"vencido"` string-literal comparison remains anywhere in `ResourceController.java` after the change. Line numbers shifted slightly (+9) by the time this fix landed because the WR-02 commit above added a 9-line comment earlier in the same file; all five call sites were re-located by content search before editing, not by the review's original line numbers.
**Verification:** Re-read every modified region (Tier 1: all five call sites now reference the constants, no stray literals left). Ran `mvn -o compile` then `mvn -o test -Dtest=RiscoPrazoServiceTest` (Tier 2): BUILD SUCCESS, 15/15 pass unchanged — constants carry identical string values to the literals they replaced, so no behavior changed.

## Skipped Issues

### WR-04: No automated test coverage for the controller call sites with intentionally-changed behavior

**File:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (`agendaUrgentesCount`, `prazosCriticosCount`, `getUpcomingEventos`)
**Reason:** skipped: reviewer's own Fix guidance explicitly recommends deferral, not a code change now. Verbatim from 85-REVIEW.md: "Track as a follow-up (a `@WebMvcTest`/`@SpringBootTest` slice covering `/dashboard` and `/processos/dashboard` KPI counts with fixture `Evento` rows spanning null/near/far dates and mixed priorities) rather than blocking this phase on it, since no controller-test precedent exists yet in this codebase." Standing up the first Spring controller-test slice (test `@Configuration`, mocked/seeded repositories, `@PreAuthorize`/security-context setup) is a nontrivial infrastructure addition that the reviewer explicitly scoped out of this phase and out of this fix pass. No source change was applied; this is tracked here as a follow-up item rather than forced into an ad-hoc test that would duplicate future infra work.
**Original issue:** `RiscoPrazoServiceTest` (confirmed the only test file in the backend) thoroughly tests the pure `RiscoPrazoService` functions, but nothing exercises how `ResourceController` wires those functions into the three call sites whose behavior this phase deliberately changed. A future edit that swaps `dataFim` for `dataInicio`, inverts the `"proximo"/"vencido"` check, or reintroduces a fixed window would compile and pass the full existing test suite undetected.

---

_Fixed: 2026-07-08T15:15:32Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
