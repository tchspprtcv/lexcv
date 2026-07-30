---
phase: LEXCV-117-backend-limite-de-utilizadores-por-tenant
reviewed: 2026-07-29T02:38:52Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/TenantPlano.java
  - backend/src/main/java/com/lexcv/models/Tenant.java
  - backend/src/main/java/com/lexcv/repositories/UserRepository.java
  - backend/migrations/117-add-tenant-plano-limite-utilizadores.sql
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase LEXCV-117-backend-limite-de-utilizadores-por-tenant: Code Review Report

**Reviewed:** 2026-07-29T02:38:52Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found (0 blocking — see Final Verdict)

## Summary

This is round 3, the final re-review of this fix loop, covering the same 6 files after three fix cycles:

- **Round 1** — `3bf122d fix(117): CR-01 enforce user limit on reactivation via updateUser`, `2dcb10c fix(117): WR-01 correct N-concurrent-request framing in limit-check comment`.
- **Round 2 (produced the previous 117-REVIEW.md this pass overwrites)** — a full re-review that reconfirmed CR-01/WR-01/WR-02 and surfaced three new findings: WR-03 (`createUser` blocked legitimate `ativo:false` creations once a tenant was at capacity), IN-03 (two doc comments still said the limit was enforced only by `createUser`), and IN-04 (`updateUser` NPE'd on an explicit `"ativo": null` instead of failing cleanly).
- **Round 2 fixes (verified in this pass)** — `f2f9bf0 fix(117): WR-03 only check user limit in createUser when new user will actually be ativo`, `d0a859a fix(117): IN-04 validate ativo is a Boolean in updateUser before unboxing to avoid NPE on null`, `e316d57 docs(117): IN-03 update stale limite comments to name shared helper and both call sites`.

This pass independently re-derives every conclusion rather than trusting the prior write-up: I re-read all 6 files in full, diffed each of the three round-2 commits individually (`git show f2f9bf0`, `git show d0a859a`, `git show e316d57`) plus the full cumulative diff since the original `feat(117-02)` commit (`git diff c2525a8..HEAD`), re-ran the automated test suite and SpotBugs, and repeated the repo-wide audit for other `User.ativo` write paths.

**WR-03 — verified FIXED.** `AdminController.createUser` now computes `boolean ativoInicial = body.get("ativo") == null || (Boolean) body.get("ativo");` at `AdminController.java:137` *before* calling the limit helper, and only calls `limiteUtilizadoresExcedido(principal.getTenantId())` when `ativoInicial` is `true` (`:138-143`); the same variable is reused for `User.builder()...ativo(ativoInicial)` at `:158`, so the value that was checked and the value that gets persisted can never diverge. The dedicated test `createUser_comAtivoFalseNuncaVerificaLimiteEDevolve201` (`AdminControllerLimiteUtilizadoresTest.java:170-186`) proves it: it deliberately leaves `tenantRepository.findById` and `userRepository.countByTenantIdAndAtivoTrue` unstubbed and asserts both are `never()` invoked, plus asserts `201 CREATED` and exactly one `save()`. This is a load-bearing assertion, not a vacuous one — the helper's first statement is unconditionally `tenantRepository.findById(tenantId)`, so if the `ativoInicial` guard were ever removed, `verify(tenantRepository, never()).findById(any())` would fail immediately regardless of what the tenant/count stubs return.

**IN-04 — verified FIXED.** `AdminController.updateUser`'s `ativo` handling (`:200-219`) now guards the unboxing with `if (!(body.get("ativo") instanceof Boolean novoAtivo)) { return ResponseEntity.badRequest()...; }` (`:201-206`) before the CR-01 reactivation check and `user.setAtivo(novoAtivo)`. An explicit `"ativo": null` payload (valid JSON; Jackson keeps the key in the `Map` with a `null` value) now returns a clean `400` instead of throwing `NullPointerException`. Test `updateUser_comAtivoNullDevolve400ENaoGravaNada` (`:252-266`) proves this and additionally asserts `save`, `tenantRepository.findById`, and `countByTenantIdAndAtivoTrue` are all `never()` called — the malformed request is rejected before it can reach (and pay the cost of) the limit-check logic. The pattern-matching `instanceof Boolean novoAtivo` with flow-scoping past a returning `if` requires Java 16+; this backend runs Java 23 (`mvn -v` confirms `23.0.2`), and the successful compile + green test run confirm it behaves as intended.

**IN-03 — verified FIXED.** `git show e316d57` is a pure documentation diff (2 files, 8 insertions / 6 deletions, zero non-comment lines touched). `Tenant.java:39-43` and `UserRepository.java:32-37` now both read "...Consumido por `AdminController.limiteUtilizadoresExcedido`, chamado a partir de `createUser`... e de `updateUser` (reativação, false -> true)..." instead of naming `createUser` alone — both comments now match what the code has actually done since the CR-01 fix.

**CR-01 and WR-01 (round 1) — unaffected, still valid.** The shared helper's body (`AdminController.java:89-99`) is byte-for-byte identical across all three round-2 commits — none of `f2f9bf0`/`d0a859a`/`e316d57` touch it. The reactivation guard added by CR-01 (`:207-217`) is likewise untouched by round 2 except for the IN-04 guard now sitting immediately above it in the same `if (body.containsKey("ativo"))` block. Both dispositions from the previous review stand without needing re-litigation.

**Regression check — clean.**
- `cd backend && mvn test -Dtest=AdminControllerLimiteUtilizadoresTest`: **9 tests, 0 failures, 0 errors, BUILD SUCCESS** (4 original `createUser` cases + 3 CR-01 `updateUser` cases + 1 WR-03 case + 1 IN-04 case).
- `cd backend && mvn test` (full unit suite): **93 tests, 0 failures, 0 errors, BUILD SUCCESS** — exactly the round-2 baseline of 91 plus the 2 new test methods added by the WR-03 and IN-04 fix commits, confirming nothing was silently dropped or weakened elsewhere in the suite (`PesquisaControllerTest`'s intentional `unaccent`-extension-missing branch-isolation log line is expected local-environment noise, not a failure — that test still reports `11/11` green).
- `cd backend && mvn spotbugs:check`: **0 bug instances, 0 errors, BUILD SUCCESS** — SAST stays clean.
- Repo-wide re-audit of every write to `User.ativo` (`grep -rn "setAtivo(|\.ativo("` under `backend/src/main/java`): still exactly `AdminController.createUser` (`:158`, gated by the WR-03 fix), `AdminController.updateUser` (`:218`, gated by the CR-01/IN-04 fix), `SetupService.initializeSystem` and `DatabaseSeeder` (first-run/seed-only, provision a brand-new tenant with `limiteUtilizadores` unset), and three `Cliente.setAtivo(...)` calls in `ResourceController` (an unrelated entity). I additionally re-read `AuthController.java` in full this round to independently re-verify its `.ativo(true)` at `:160`: it is a hard-coded default for the `UserResponse` DTO inside `getMe()`, immediately overwritten a few lines later by the real user's data via `userRepository.findById(...).ifPresent(...)` — there is no self-registration endpoint in `AuthController`, so it cannot create or activate a `User` row. No new bypass surface has appeared since round 2's equivalent audit.
- `git status --porcelain backend/`: clean — this review made no source edits.

One new, non-blocking observation surfaced while tracing the IN-04 fix's edges (see IN-05 below): the analogous cast in `createUser` (`:137`) was not hardened the same way, but it was not part of what WR-03 changed (the expression is unchanged from before Phase 117 even started — WR-03 only relocated it into the `ativoInicial` variable) and it matches every other unchecked `Map` cast already in this controller (`nome`, `email`, `telefone`, `roles`, `permissions`), so it is filed as low-severity Info rather than reopening WR-03 or IN-04.

IN-01 and IN-02 are carried forward unchanged from the round-2 review, per this round's explicit brief — nothing material has changed for either (re-confirmed independently: `grep -rn 'setLimiteUtilizadores' backend/src/main/java` still returns zero matches, and the helper's `tenant != null` fail-open branch at `AdminController.java:90-91` is untouched by any of the three round-2 commits).

## Final Verdict

**Phase 117 (backend limite de utilizadores por tenant) is APPROVED — no blockers.**

- 0 Critical findings, this round or any prior round.
- 0 open *actionable* Warnings: the only two Warnings on record (WR-01, WR-02, below) are consciously accepted trade-offs with an explicit "no action required" disposition dating to the original plan (`117-02-PLAN.md` T-117-07) and to this repo's existing migration convention, respectively — not defects awaiting a code change. WR-03, the one round-2 Warning that *was* actionable, is now verified fixed and is no longer open.
- 0 open *actionable* Info items requiring a code change this phase: IN-01 and IN-02 are explicitly deferred by design to Phases 119/120 (self-service provisioning/limits console); IN-03 and IN-04 are now verified fixed; IN-05 is a new, optional, low-severity note about a pre-existing pattern, not a regression.
- Full fix history verified present and coherent in current `HEAD` (`e316d57`): `3bf122d`, `2dcb10c` (round 1) + `f2f9bf0`, `d0a859a`, `e316d57` (round 2) — 5 commits, all confirmed via `git show`/cumulative diff to do exactly what their messages say and nothing more.
- Automated gates are green: unit suite (93/93), SpotBugs/FindSecBugs (0 findings), and the feature's own 9-case test file, all re-run fresh during this review rather than taken on faith.

No further fix iteration is warranted for this phase; the remaining items below are recorded for traceability (several are cited by ID directly in source comments) and for the Phase 119/120 teams, not as pre-conditions for shipping Phase 117.

## Warnings

### WR-01: Count-then-compare is not atomic — accepted risk, shared by two call sites (unchanged this round)

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:89-99` (comment at `:81-88`)

**Issue:** The tenant lookup, the count query, and the eventual `save()` remain three separate, uncoordinated statements — no lock, no `@Version`, no DB constraint. Accepted for `createUser` alone as `T-117-07` in `117-02-PLAN.md`; the CR-01 fix (round 1) routed `updateUser` reactivations through the same non-atomic helper, so the race is reachable from both endpoints, including cross-endpoint (a concurrent `createUser` and a concurrent reactivating `updateUser` racing each other). None of the round-2 fix commits (`f2f9bf0`/`d0a859a`/`e316d57`) touch the helper body or add a third call site, so this disposition is unchanged from the round-2 review.

**Fix:** No action required — a knowingly accepted trade-off for a single-office, ADMIN-only, manually-billed endpoint, per the plan's explicit decision not to add `@Transactional`/locks/DB constraints. Re-confirm once Phase 119/120 make provisioning/limits self-service, as the comment itself already flags.

### WR-02: Migration's `ADD COLUMN` statements remain non-idempotent (unchanged this round)

**File:** `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql:28-29`

**Issue:**
```sql
ALTER TABLE t_tenant ADD COLUMN plano VARCHAR(255);
ALTER TABLE t_tenant ADD COLUMN limite_utilizadores INTEGER;
```
Still lack `IF NOT EXISTS`, unlike the idempotent `UPDATE ... WHERE plano IS NULL` immediately below. The file is byte-for-byte unchanged since round 1 (confirmed: `git diff c2525a8..HEAD` shows zero hunks for this file). A second manual run against an already-migrated database fails both statements with "column already exists" — no data corruption, but a re-run risk given this repo's migrations are 100% manual.

**Fix:** No action required this phase — matches the no-`IF NOT EXISTS` convention of every other script in `backend/migrations/`. If the convention is ever revisited, do it repo-wide:
```sql
ALTER TABLE t_tenant ADD COLUMN IF NOT EXISTS plano VARCHAR(255);
ALTER TABLE t_tenant ADD COLUMN IF NOT EXISTS limite_utilizadores INTEGER;
```

## Info

### IN-01: No DB-level guard against a negative/zero `limite_utilizadores` sentinel (deferred, unchanged)

**File:** `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql:29`

**Issue:** Unchanged since the original review. `Tenant.java:39-43`'s own comment is emphatic that `null` is the only valid "no limit" value and a sentinel like `-1`/`MAX_VALUE` must never be used, but nothing in the schema enforces that. Re-confirmed this round: `grep -rn 'setLimiteUtilizadores' backend/src/main/java` still returns zero matches — no application code writes this column yet, so the risk remains theoretical today.

**Fix:** No action needed now. When a write-path for `limiteUtilizadores` is introduced (Phase 120), add `CHECK (limite_utilizadores IS NULL OR limite_utilizadores >= 0)` in that phase's migration, or validate the range in the service layer.

### IN-02: Limit check fails open if the caller's own tenant row is missing (deferred, unchanged)

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:90-91`

**Issue:** `Tenant tenant = tenantRepository.findById(tenantId).orElse(null); if (tenant != null && tenant.getLimiteUtilizadores() != null) { ... }` — if the tenant row referenced by the authenticated principal doesn't exist, the check is silently skipped and the operation proceeds unbounded. Deliberate, documented decision (`117-CONTEXT.md`) since before either endpoint was gated. Confirmed unchanged: none of the three round-2 commits touch these two lines.

**Fix:** No action needed now, per the original decision. Re-examine once Phase 119/120 introduce tenant lifecycle operations (suspension/deletion) that could make a dangling `tenantId` reachable in practice.

### IN-05: `createUser`'s `ativo` cast is not hardened the same way `updateUser`'s now is (new, low severity, non-blocking)

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:137`

**Issue:**
```java
boolean ativoInicial = body.get("ativo") == null || (Boolean) body.get("ativo");
```
If the request supplies `"ativo"` as a non-null, non-boolean JSON value (e.g. `"ativo": "sim"` or `"ativo": 1`), the short-circuit `||` avoids the null case but the `(Boolean)` cast still throws `ClassCastException` for anything that isn't actually a `Boolean`, which is caught only by `GlobalExceptionHandler`'s catch-all `@ExceptionHandler(Exception.class)` (surfaced as a generic `500`). This is the same category of defect IN-04 just fixed for `updateUser`'s equivalent field — but this exact expression is *not* new: `git show f2f9bf0` confirms WR-03 only relocated it from an inline `.ativo(body.get("ativo") == null || (Boolean) body.get("ativo"))` in the builder into the `ativoInicial` local variable; the cast's safety is byte-for-byte unchanged from before Phase 117 started. It is also consistent with every other unvalidated `Map` cast already present in this controller (`(String) body.get("nome")`, `(String) body.get("email")`, `(String) body.get("telefone")`, the `roles`/`permissions` list-element casts, etc.) — none of which either round-1 or round-2 flagged, since both were full-file re-reviews that had equal visibility into this pattern both before and after the WR-03 fix. Filed here only because IN-04 made the asymmetry between the two sibling methods concrete; it is not a regression and does not need to block this phase.

**Fix:** Optional, low-priority, and best done together with a broader input-validation pass on this controller rather than in isolation (the same gap exists for `nome`/`email`/`telefone`/`roles`/`permissions`). If addressed standalone, mirror the IN-04 pattern:
```java
Object ativoRaw = body.get("ativo");
if (ativoRaw != null && !(ativoRaw instanceof Boolean)) {
    return ResponseEntity.badRequest().body(Map.of("message", "O campo ativo deve ser um valor booleano."));
}
boolean ativoInicial = ativoRaw == null || (Boolean) ativoRaw;
```

---

_Reviewed: 2026-07-29T02:38:52Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
