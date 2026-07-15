---
phase: LEXCV-94-notf-27-corrigir-colis-o-de-dedup-admin
fixed_at: 2026-07-14T16:15:00Z
review_path: .planning/phases/LEXCV-94-notf-27-corrigir-colis-o-de-dedup-admin/94-REVIEW.md
iteration: 2
findings_in_scope: 2
fixed: 2
skipped: 0
status: all_fixed
---

# Phase LEXCV-94: Code Review Fix Report

**Fixed at:** 2026-07-14T16:15:00Z
**Source review:** .planning/phases/LEXCV-94-notf-27-corrigir-colis-o-de-dedup-admin/94-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 2 (CR-01, WR-01 — `fix_scope: critical_warning`; IN-01..IN-05 remain excluded)
- Fixed: 2
- Skipped: 0

This is the second fix pass for this phase. Iteration 1 (see commits `f839243`/`a34b5b1`/`7fd8bb0`,
documented in the iteration-1 section of git history for this file) replaced
`NotificacaoRepository`'s `save()`/`saveAndFlush()` call with the atomic
`inserirSeNaoDuplicado(...)` native upsert to fix a transaction-poisoning race. The re-review found
that fix itself introduced a new, more severe regression — the subject of this iteration's CR-01
below.

## Fixed Issues

### CR-01: `inserirSeNaoDuplicado` has no active transaction on most real call paths — throws `TransactionRequiredException` at runtime

**Files modified:**
- `backend/src/main/java/com/lexcv/repositories/NotificacaoRepository.java`
- `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java`

**Commit:** `6ac8725`

**Applied fix:** Added `@Transactional` (from `org.springframework.transaction.annotation`) directly
on `NotificacaoRepository.inserirSeNaoDuplicado(...)`, exactly as 94-REVIEW.md's Fix section
specified — not on `NotificacaoService.criar()`/`criarComFanOutAdmin()`. The review's rationale
for this placement was verified against the current code and confirmed still applicable:
`criarComFanOutAdmin()` calls `criar(...)` via self-invocation (`this.criar(...)` within the same
class), which bypasses Spring's proxy-based `@Transactional` advice on the service entirely, so
annotating `criar()` would have been inert on that call path. A repository interface method, in
contrast, always goes through Spring Data JPA's own dedicated proxy regardless of how the caller
reached it, so `@Transactional` here takes effect uniformly. Default propagation (`REQUIRED`) joins
an already-open ambient transaction where one exists (`atribuirResponsavel`, `ParecerController`)
and opens its own short-lived transaction otherwise (`createProcesso`, `createProcessoFase`,
`uploadDocumento`, the entirety of `AlertasDiariosJob`), which was the actual gap: none of those
four call sites had any ambient transaction, so every one of them would throw
`TransactionRequiredException` at runtime before this fix.

Added two new tests to `NotificacaoRepositoryIT` (the existing Testcontainers-backed integration
test class for this repository) that specifically prove the fix rather than just re-exercising
the query's SQL behavior:
- `inserirSeNaoDuplicado_semTransacaoAmbienteDoChamador_insereComSucesso`
- `inserirSeNaoDuplicado_semTransacaoAmbienteDoChamador_duplicadoDevolveZeroLinhas`

Both are annotated `@Transactional(propagation = Propagation.NOT_SUPPORTED)` at the method level.
This is necessary because `@DataJpaTest` wraps every test method in its own ambient transaction by
default (rolled back afterwards) — which would silently mask a missing `@Transactional` on the
repository method itself, since every *other* test in this class already runs inside that ambient
ambient transaction and would pass regardless of whether this fix is present.
`Propagation.NOT_SUPPORTED` suspends that ambient test transaction for just these two methods,
mirroring the real call paths that have no ambient transaction of their own (`AlertasDiariosJob`,
`createProcesso`/`createProcessoFase`/`uploadDocumento`). Without the `@Transactional` fix on the
repository method, both new tests would fail with `TransactionRequiredException` instead of
asserting successfully; because `Propagation.NOT_SUPPORTED` suspends the test's own transaction
rather than rolling it back, both tests explicitly `deleteById(...)` the row(s) they insert to avoid
leaving residue in the Testcontainers Postgres instance shared by the rest of the class.

**Verification performed:** `mvn -o compile` and `mvn -o test-compile` both succeeded (exit 0) for
the whole backend module, confirming the annotation change and the new IT test compile cleanly.
`mvn -o test -Dtest=NotificacaoServiceTest,AlertasDiariosJobTest`: 40/0/0/0 (Tests run/Failures/
Errors/Skipped) — the existing Mockito-based unit suites, which mock the repository entirely and
therefore cannot exercise this transaction-boundary bug either way, are unaffected. Full `mvn -o
test` for the whole backend module (Surefire-bound `*Test.java` only: `NotificacaoServiceTest`,
`AlertasDiariosJobTest`, `RiscoPrazoServiceTest`): 55/0/0/0, BUILD SUCCESS.

**Limitation — the new IT tests could not be executed in this sandbox:** no Docker daemon is
reachable here (`docker version` fails with "failed to connect to the docker API"), so
`NotificacaoRepositoryIT` (a `@Testcontainers`/`@DataJpaTest` suite requiring a real Postgres
container) — including the two new tests added for this fix — could not actually be run, only
compiled. This is the same limitation the iteration-1 fixer and this iteration's reviewer both
independently hit and documented. The fix and the two new tests were verified by static
inspection against Spring's documented `@Transactional(propagation = NOT_SUPPORTED)` test-method
behavior (Spring Framework reference docs, Testing chapter: a method-level `@Transactional` with
`NOT_SUPPORTED` propagation suspends/skips the ambient test-managed transaction for that specific
test method) and against the JPA specification requirement that `@Modifying` queries need an
active transaction. **Recommend running the full `NotificacaoRepositoryIT` suite (including the two
new tests) against real PostgreSQL via Testcontainers in an environment with Docker available
before this phase is considered fully verified** — this is the strongest possible confirmation
short of a production/staging smoke test that the fix actually eliminates the
`TransactionRequiredException` on the real call paths CR-01 traced through.

### WR-01: `AlertasDiariosJob.notificar()`'s `catch (DataIntegrityViolationException ex)` is now dead code, masking that the exception path it defends against can no longer occur

**Files modified:** `backend/src/main/java/com/lexcv/jobs/AlertasDiariosJob.java`

**Commit:** `9572030`

**Applied fix:** Chose the "remove the dead block" option (rather than "leave it as a documented
harmless backstop") from 94-REVIEW.md's Fix section, for the same reason the review itself flagged
as the actual risk: a comment describing an exception-handling race that can no longer occur is more
likely to mislead the next person investigating a duplicate-notification incident into thinking this
path is exercised and tested, than a removed block is to be missed. Removed the
`catch (DataIntegrityViolationException ex)` block and its log statement from `notificar(...)`, and
removed the now-unused `org.springframework.dao.DataIntegrityViolationException` import (verified via
grep that no other reference to that type remains in the file). Replaced the removed block with an
inline comment on the remaining `try` explaining the history (the backstop used to exist for a
check-then-act race against `uk_notificacao_dedup`, but since iteration 1's CR-01 fix,
`NotificacaoService.criar()` reports a dedup hit via a `0`-rows-affected / `Optional.empty()` return
instead of an exception, so there is no longer any exception for this method to catch on that path).

**Verification performed:** Grepped the modified file and its paired test (`AlertasDiariosJobTest`)
to confirm no remaining reference to `DataIntegrityViolationException` anywhere (import or catch
clause) and that no existing test asserted on that removed catch branch (none did — WR-01's own
review text already noted the branch was unreachable and therefore untested). `mvn -o test-compile`
succeeded. `mvn -o test -Dtest=NotificacaoServiceTest,AlertasDiariosJobTest`: 40/0/0/0. Full `mvn -o
test` (whole backend module, all three `*Test.java` classes): 55/0/0/0, BUILD SUCCESS — confirming
the import/catch removal did not affect any of `AlertasDiariosJobTest`'s existing 9 tests, including
the ones exercising per-admin/per-tenant/per-entidade failure isolation in the same method's
surrounding try/catch layers.

## Skipped Issues

None — both in-scope findings (CR-01, WR-01) were fixed. IN-01 through IN-05 remain out of scope for
this `critical_warning`-only pass and are unchanged from 94-REVIEW.md; see that file for their
descriptions if a future `--all`-scope pass wants to pick them up.

---

_Fixed: 2026-07-14T16:15:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
