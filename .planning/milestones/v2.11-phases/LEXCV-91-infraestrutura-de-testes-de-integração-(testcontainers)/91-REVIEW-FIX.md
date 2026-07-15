---
phase: LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)
fixed_at: 2026-07-13T20:00:00Z
review_path: .planning/phases/LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)/91-REVIEW.md
iteration: 2
findings_in_scope: 1
fixed: 1
skipped: 0
status: all_fixed
---

# Phase LEXCV-91: Code Review Fix Report

**Fixed at:** 2026-07-13T20:00:00Z
**Source review:** .planning/phases/LEXCV-91-infraestrutura-de-testes-de-integração-(testcontainers)/91-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope (this iteration): 1 (WR-01; fix_scope=critical_warning excludes IN-01/IN-02,
  which remain intentionally unresolved carry-forwards from the prior review)
- Fixed: 1
- Skipped: 0

This is the re-review's fix pass. The prior iteration's five WARNING findings (also numbered
WR-01 through WR-05 in that earlier REVIEW.md revision) were already fixed and committed — see
"Iteration 1 (prior fix pass)" below for that history, carried forward for context. This
iteration addresses the single new WARNING (WR-01 in the current REVIEW.md) that surfaced while
the reviewer was re-verifying the previous WR-04 fix.

## Fixed Issues

### WR-01: Migration script's duplicate-constraint safety check is unreliable because `ParecerVersao`'s `@UniqueConstraint` has no explicit name

**Files modified:** `backend/src/main/java/com/lexcv/models/ParecerVersao.java`,
`backend/migrations/91-add-parecer-versao-unique-constraint.sql`
**Commit:** 3859df3
**Applied fix:** Read both files fresh against the current code state before editing (matched
what the review cited). In `ParecerVersao.java`, added an explicit `name =
"uk_parecer_versao_solicitacao_numero"` to the `@UniqueConstraint` on the `@Table` annotation,
matching the naming convention already used by `Notificacao.java`'s `uk_notificacao_dedup` and
the name the migration script's `ALTER TABLE ... ADD CONSTRAINT` statement already assumes. This
closes the gap where a `ddl-auto=update` environment (dev/CI) would otherwise get this
constraint under a Hibernate/Postgres auto-generated name instead of the name the manual
production migration script expects.

Also updated the migration script's own "check before running" guidance, per the fix
suggestion's second part: replaced the literal-name-based check (`\d t_parecer_versao` /
searching for the specific constraint name) with a query that checks by column set instead
(`SELECT conname FROM pg_constraint WHERE conrelid = 't_parecer_versao'::regclass AND contype =
'u';`), since Postgres allows multiple differently-named unique constraints on the same column
set — the old guidance's premise (that a duplicate-constraint error would occur) was not
actually true for a same-columns-different-name collision, which is exactly the case this
finding describes.

**Verification:** Tier 1 (re-read both files, confirmed both edits present and surrounding code
intact) plus a partial Tier 2 (standalone `javac` parse of the modified `.java` file; all
reported errors were classpath/symbol-resolution only — missing Lombok/Jakarta/Spring
dependencies from the no-classpath invocation — with no syntax errors, confirming the edit did
not break the file structurally). No project-wide Java build was run (out of scope per
verification_strategy — full builds/tests are the verifier phase's job).

## Skipped Issues

None — the single in-scope finding (WR-01) was fixed.

**Note on out-of-scope findings:** `IN-01` (duplicated Testcontainers boilerplate across
`*IT` classes) and `IN-02` (CI jobs missing `timeout-minutes`) remain open in the current
REVIEW.md. Both are Info-severity and explicitly out of scope for `fix_scope=critical_warning`
in this run, same as in iteration 1. They require a separate `fix_scope=all` run (or manual
follow-up) to address.

---

## Iteration 1 (prior fix pass — carried forward for context)

**Fixed at:** 2026-07-13T17:00:00Z
**Findings in scope:** 5 (WR-01 through WR-05 of the pre-re-review REVIEW.md revision)
**Fixed:** 5 / **Skipped:** 0

- **WR-01 (flaky sleep-based ordering assertion)** — fixed in
  `backend/src/test/java/com/lexcv/repositories/NotificacaoRepositoryIT.java` (commit `5f4bc15`).
  Removed `Thread.sleep` calls; replaced with flush + native per-row `UPDATE ... SET created_at`
  + `entityManager.clear()` before the native ordering query.
- **WR-02 (test datasource masking missing `@ServiceConnection`)** — fixed in
  `backend/src/test/resources/application.properties` (commit `7b4a840`). Pointed the datasource
  at a non-resolvable host so a missing `@ServiceConnection` fails fast.
- **WR-03 (`ExecutorService.shutdown()` leaks threads on timeout)** — fixed in
  `backend/src/test/java/com/lexcv/repositories/ParecerVersaoConcorrenciaIT.java` (commit
  `3e793ac`). Replaced `shutdown()` with `shutdownNow()` + `awaitTermination(5, SECONDS)` in the
  `finally` block.
- **WR-04 (missing production migration for the `ParecerVersao` unique constraint)** — fixed by
  adding `backend/migrations/91-add-parecer-versao-unique-constraint.sql` (commit `ec37b4e`),
  following the `81`/`82`/`88` precedent. (Re-verifying this fix in the re-review is what
  surfaced the current iteration's WR-01, above.)
- **WR-05 (CI gating job only ran post-merge, not on PRs)** — fixed in `.github/workflows/deploy.yml`
  (applied by the orchestrator post user-approval, since CI/CD changes were blocked by the
  auto-mode permission classifier as requiring explicit confirmation). Added a `pull_request`
  trigger alongside `push`, and gated `build-and-push` with `if: github.event_name == 'push'` so
  fork PRs cannot trigger an image push (commit `c8a2a9a`).

---

_Fixed: 2026-07-13T20:00:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
