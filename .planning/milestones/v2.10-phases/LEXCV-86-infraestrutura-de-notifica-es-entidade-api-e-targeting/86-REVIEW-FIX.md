---
phase: LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting
fixed_at: 2026-07-08T22:40:33Z
review_path: .planning/phases/LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting/86-REVIEW.md
iteration: 3
findings_in_scope: 4
fixed: 3
skipped: 1
status: partial
---

# Phase 86: Code Review Fix Report

**Fixed at:** 2026-07-08T22:40:33Z
**Source review:** .planning/phases/LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting/86-REVIEW.md
**Iteration:** 3 (final — auto-fix loop stops after this pass regardless of remaining findings)

**Summary:**
- Findings in scope: 4 (0 critical, 4 warning — `fix_scope: critical_warning`; the 9 Info findings, IN-01 through IN-09, were left out of scope by design)
- Fixed: 3
- Skipped: 1

## Fixed Issues

### WR-02: `notificarAdmins` fan-out is still not `@Transactional`

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
**Commit:** `429767e`
**Applied fix:** Added `@Transactional` to package-private `notificarAdmins(...)` (the `Transactional` import was already present, used by the sibling `marcarLida`/`marcarTodasLidas` methods). Now a mid-loop `IllegalArgumentException` from `criar(...)` (e.g. an oversized `titulo` supplied by a future Phase 87 caller) rolls back rows already saved for earlier admins in the same fan-out, instead of leaving a partial ADMIN notification set — matching the reviewer's exact suggested fix verbatim, since the current code matched the reviewed context exactly.

### WR-03: `requireMaxLength`'s 5 call sites had test coverage for only 1 field

**Files modified:** `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
**Commit:** `dae0d14`
**Applied fix:** Added `criar_camposComTamanhoExcedido_lancaIllegalArgumentException`, inserted directly after the existing `criar_tituloExcede255Caracteres_...` test. Exercises the four previously-untested `requireMaxLength` call sites individually — oversized `categoria`, `entidadeTipo`, `entidadeId`, and `linkUrl` (each `"x".repeat(256)`) — each asserted to throw `IllegalArgumentException`, plus a final `verify(notificacaoRepository, never()).save(any())`. Traced `criar()`'s validation order manually to confirm each assertion trips on the intended field only (all fields preceding the oversized one in the five sequential `requireMaxLength` calls — categoria, titulo, entidadeTipo, entidadeId, linkUrl — are kept at valid length in each case), so this closes the exact blind-spot the reviewer identified rather than just adding a superficially-similar test. Applied the reviewer's suggested test body verbatim since it matched the current code precisely.

### WR-04: `GET /api/v1/notificacoes` still accepted an unbounded `size`

**Files modified:** `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java`
**Commit:** `c6baef6`
**Applied fix:** Changed the guard from `page < 0 || size < 1` to `page < 0 || size < 1 || size > 100`, and updated the 400 response message from "size deve ser >= 1" to "size deve estar entre 1 e 100". Confirmed via grep that no test in the suite asserts the old message text, so this is a safe, non-breaking change.

**Verification (all three fixes above):** Beyond the mandatory re-read of each modified section, ran the same scoped Maven checks the prior iteration established as this phase's verification precedent (offline mode, not the full suite):
- `mvn -o test-compile` (whole backend, main + test sources): **BUILD SUCCESS**, exit code 0 — confirms `NotificacaoService.java` (WR-02) and `NotificacaoController.java` (WR-04) both compile cleanly with their changes.
- `mvn -o test -Dtest=NotificacaoServiceTest`: **9/9 tests passing** (8 pre-existing + 1 new from WR-03), 0 failures, 0 errors, confirmed via the surefire summary (`Tests run: 9, Failures: 0, Errors: 0, Skipped: 0`).

None of these three findings involve ambiguous logic (WR-02 is a well-established annotation-based rollback boundary matching an existing sibling-method precedent in the same class; WR-03 is additive test coverage; WR-04 is a straightforward numeric bound), so none are flagged `requires human verification` — all three are fully verified and safe to carry forward as-is.

## Skipped Issues

### WR-01: First native query + `Pageable` + `countQuery` combination in the codebase is still unverified against a real PostgreSQL instance

**File:** `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:16-39`
**Reason:** Not applied — this is the third consecutive cycle raising the same gap, and the reviewer's own fix suggestion is explicitly a manual/human action, not a source-code change: "do at least one manual round trip against a dev database with seeded rows covering all four filter combinations... via curl/Postman against `GET /api/v1/notificacoes`, and record the result in the phase's verification artifacts." This requires a reachable Postgres instance with the migration applied, seeded tenant/user/notification rows, a running Spring Boot process, and an authenticated session against a `@PreAuthorize`-gated endpoint — live/end-to-end verification, not something an automated fixer commits as a diff. Re-confirmed via `backend/pom.xml` (grep for `h2`/`testcontainers`) that no embedded-database test dependency exists in this project, so the reviewer's longer-term alternative (`@DataJpaTest`) is also explicitly framed as "an explicit human/architecture decision, not something to bolt on unilaterally" — consistent with how iteration 2 skipped this exact finding (as `WR-02` in that iteration's numbering) for the same reason.
**Original issue:** `buscarPorFiltros` combines `nativeQuery = true`, a hand-written `countQuery`, and a `Pageable` argument — a combination with no working precedent elsewhere in the codebase — and has only ever executed through Mockito mocks that don't validate real SQL, never against actual PostgreSQL. Static analysis found no concrete defect in the SQL itself; this is a verification gap on the phase's riskiest read path, not a proven functional bug.

**Recommended next step (for a human, since this is the final auto-fix iteration):** Run one manual round trip against a dev database with seeded rows covering all four filter combinations (no filters; `categoria` only; `lida` only; both) via curl/Postman against `GET /api/v1/notificacoes`, and record the result in the phase's verification artifacts. This finding has now been raised in three consecutive review cycles without a human decision either way — it will keep resurfacing on future reviews of this file until either the manual verification is recorded or a deliberate architectural call is made on embedded-database testing.

## Remaining Out-of-Scope Findings (not attempted, by design)

`fix_scope: critical_warning` intentionally excludes all 9 Info-severity findings (IN-01 through IN-09) from this and prior iterations. Since this is the final allowed auto-fix iteration, they are listed here for visibility rather than silently dropped:

- **IN-01** — `createdAt` column missing `nullable = false` on the entity (migration already has `NOT NULL`); harmless today, cosmetic inconsistency.
- **IN-02** — ADMIN fan-out doesn't filter by `ativo`; may be intentional, needs an explicit human decision either way.
- **IN-03** — Frontend `KNOWN_SCOPES` registry has no consumers yet; expected, Phase 89 wires the UI.
- **IN-04** — `@Builder.Default` on `lida` only applies via the builder, not the other constructors; no live path exercises this today.
- **IN-05** — `getTenantId()`/`getUserId()` boilerplate duplicated across four controllers; pre-existing pattern, not introduced by this phase.
- **IN-06** — `notificarAdmins` has no null-check on its own `tenantId`, silently no-ops instead of failing loudly.
- **IN-07** — `criar()` doesn't constrain `linkUrl`'s scheme/format (only length); no reachable untrusted-input path today.
- **IN-08** — `Notificacao` JPA entity serialized directly as the API response instead of a DTO.
- **IN-09** — `notificacoes:view` gates both read and mark-read endpoints, a documented/deliberate deviation from the `scope:action` convention — not a defect.

## Verification Summary

- `mvn -o test-compile` (whole backend, main + test sources): BUILD SUCCESS after all three fixes.
- `mvn -o test -Dtest=NotificacaoServiceTest`: **9/9 tests passing**, 0 failures, 0 errors.
- The full backend suite (`mvn -o test`) was not re-run in this pass — changes were scoped to two production files (one annotation, one conditional) and one test file (one additive test method); iteration 2 already independently confirmed 20/20 passing pre-existing tests, and this pass's scoped run adds direct evidence for the touched test class.
- WR-01 remains an open, unresolved verification gap after three consecutive review cycles — see Skipped Issues above. Not silently dropped: it requires either a manual live-database check or a deliberate test-infrastructure decision, both outside an automated fixer's authority.
- This is the final allowed auto-fix iteration (3 of 3). The orchestrator will stop the auto-fix loop after this pass. WR-01 and all 9 Info findings remain for human follow-up; none are blocking (0 Critical findings across all three review cycles).

---

_Fixed: 2026-07-08T22:40:33Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 3_
