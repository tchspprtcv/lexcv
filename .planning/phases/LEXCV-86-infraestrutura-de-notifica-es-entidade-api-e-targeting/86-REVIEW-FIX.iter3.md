---
phase: LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting
fixed_at: 2026-07-08T22:17:57Z
review_path: .planning/phases/LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting/86-REVIEW.md
iteration: 2
findings_in_scope: 2
fixed: 1
skipped: 1
status: partial
---

# Phase 86: Code Review Fix Report

**Fixed at:** 2026-07-08T22:17:57Z
**Source review:** .planning/phases/LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting/86-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 2 (0 critical, 2 warning — `fix_scope: critical_warning`; the 7 Info findings were left out of scope by design)
- Fixed: 1
- Skipped: 1

## Fixed Issues

### WR-01: The WR-01 fix's own validation logic has zero test coverage for any of its failure paths

**Files modified:** `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
**Commit:** `66ad927`
**Applied fix:** Extended the reviewer's two example tests into three, closing all three coverage gaps named in the Issue text rather than just the two shown in the abbreviated code sample:
- `criar_destinatarioDeOutroTenant_lancaIllegalArgumentException` — exactly the reviewer's snippet: stubs `userRepository.findById(DESTINATARIO_A)` to return a user belonging to a *different* tenant, asserts `criar()` throws `IllegalArgumentException`, and verifies `save()` is never called. Proves the IDOR-adjacent tenant-ownership check.
- `criar_tituloExcede255Caracteres_lancaIllegalArgumentException` — exactly the reviewer's snippet: asserts a 256-character `titulo` throws `IllegalArgumentException`.
- `criar_camposObrigatoriosEmBranco_lancaIllegalArgumentException` — added beyond the literal snippet, since the Issue text explicitly named this as a third, separate uncovered gap ("No test asserts it throws for a null/blank `categoria`/`titulo`/`mensagem`/`entidadeTipo`/`entidadeId`"): exercises all five `requireNonBlank` call sites individually (null `categoria`, blank `titulo`, empty `mensagem`, null `entidadeTipo`, whitespace-only `entidadeId`), each asserted to throw `IllegalArgumentException`, plus a final `verify(notificacaoRepository, never()).save(any())`. Testing each field individually (rather than a single representative field) matters here because `requireNonBlank` is invoked once per field from a shared private helper — a regression that silently deletes just one of the five call sites would not be caught by a test that only exercises a different field.

**Verification:** `mvn -o test-compile` (whole backend) — clean, no errors. `mvn -o test -Dtest=NotificacaoServiceTest` — **8/8 passing** (5 pre-existing + 3 new), confirmed via the surefire report (`Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`). No production code was changed — `NotificacaoService.criar()` itself was already correct per iteration 1's WR-01 fix; this pass only added the missing test coverage the iteration-2 review flagged.

## Skipped Issues

### WR-02: First native query + `Pageable` + `countQuery` combination in the codebase has never been run against a real database

**File:** `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java:16-39`
**Reason:** Not applied — both options in the reviewer's suggested fix fall outside an automated code-fix pass rather than being a source change that can be adapted and committed:
1. **Manual round trip against a dev database** (curl/Postman across all four filter combinations, with seeded rows, recording the result) is a live/end-to-end verification action, not a source-code edit. It requires a reachable Postgres instance with the migration applied, seeded tenant/user/notification rows, a running Spring Boot process, and a valid authenticated session to call the `@PreAuthorize`-gated endpoint — this is explicitly out of this fixer's scope ("end-to-end testing is handled by the verifier phase later").
2. **Introducing `@DataJpaTest`** was offered by the reviewer only as a conditional, longer-term alternative ("if the project ever adopts an embedded-database test dependency") — confirmed via `backend/pom.xml` that no H2/Testcontainers dependency exists in this project today. Adding one is a real architectural decision (which embedded database, and whether it can faithfully execute this query's Postgres-specific `CAST(:param AS text)`/`CAST(:param AS boolean)` null-guard idiom — a test running against a DB that silently diverges from real Postgres behavior here would be worse than the current honest "untested" state, since it would look like coverage without actually proving anything about production behavior). This mirrors the precedent set by iteration 1's WR-04 fix, which explicitly declined the more invasive of two reviewer-endorsed options and left that class of decision for a human to make deliberately.

**Original issue:** The repository's own comment states this is the first use of Spring Data `Pageable`/`Page` in this backend, and the specific combination used (native query + a hand-written `countQuery` + `Pageable`) has no working precedent anywhere in this codebase to have been copied from. There is no `@DataJpaTest`/H2/Testcontainers infrastructure in this project, so `buscarPorFiltros` has never executed against a real Postgres instance in any automated test — only through mocks that don't validate real SQL. No concrete SQL defect was found by the reviewer; this is a verification-gap finding, not a proven functional defect.

**Recommended next step (for a human, or the verifier phase):** Run one manual round trip against a dev database with seeded rows covering all four filter combinations (no filters; `categoria` only; `lida` only; both) via curl/Postman against `GET /api/v1/notificacoes`, and record the result in the phase's verification artifacts; or make an explicit, deliberate decision to adopt an embedded-database test dependency for this and future native-query repositories, rather than have an automated fixer make that call unilaterally.

## Verification Summary

- `mvn -o test-compile` (whole backend, main + test sources): BUILD SUCCESS after the WR-01 fix.
- `mvn -o test -Dtest=NotificacaoServiceTest`: **8/8 tests passing**, 0 failures, 0 errors (surefire report confirmed independently, not just re-read from console output).
- The full backend suite (`mvn -o test`) was not re-run in this pass — the change was scoped to a single test file, and iteration 2's `86-REVIEW.md` already independently confirmed 20/20 passing on the pre-fix codebase (5 `NotificacaoServiceTest` + 15 `RiscoPrazoServiceTest`).
- WR-02 remains an open, unresolved verification gap — see Skipped Issues above. It was not silently dropped: it requires either a manual live-database check or a deliberate test-infrastructure decision, both of which are outside this pass's scope.
- Out-of-scope findings (IN-01 through IN-07, all Info-severity) were intentionally left untouched per `fix_scope: critical_warning`.

---

_Fixed: 2026-07-08T22:17:57Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
