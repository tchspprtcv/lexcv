---
phase: LEXCV-117-backend-limite-de-utilizadores-por-tenant
verified: 2026-07-29T15:00:00Z
status: passed
score: 12/12 must-haves verified
overrides_applied: 0
---

# Phase 117: Backend — Limite de Utilizadores por Tenant Verification Report

**Phase Goal:** O backend aplica um limite de utilizadores ativos por tenant — `POST /api/v1/admin/users` recusa criar mais um utilizador quando o tenant já está no limite do seu plano, e desativar alguém liberta a vaga de imediato.
**Verified:** 2026-07-29T15:00:00Z
**Status:** passed
**Re-verification:** No — initial verification

**Verification note:** This phase went through 3 code-review rounds after initial execution (round 1: `3bf122d` CR-01 critical reactivation-bypass fix + `2dcb10c` WR-01 comment fix; round 2: `f2f9bf0` WR-03 + `d0a859a` IN-04 + `e316d57` IN-03 docs; round 3: re-confirmation only, 0 code changes). This report verifies the **current HEAD** (post all fixes), independently re-deriving every conclusion — it does not take 117-01-SUMMARY.md, 117-02-SUMMARY.md, or 117-REVIEW.md on faith. Every code excerpt below was read directly from the live files; every test/build/SAST claim was re-executed fresh in this session (not copied from prior reports); every cited commit hash was independently confirmed to exist via `git show`.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | [ROADMAP SC1] `Tenant` has `plano`/`limiteUtilizadores` persisted fields; migration applies matching columns; existing tenant gets a non-blocking default | VERIFIED | `Tenant.java:35-45` — `@Enumerated(EnumType.STRING)` `plano` (col `plano`) + nullable `Integer limiteUtilizadores` (col `limite_utilizadores`). `migrations/117-add-tenant-plano-limite-utilizadores.sql` — 2× `ALTER TABLE t_tenant ADD COLUMN` with matching names/types + `UPDATE t_tenant SET plano='ENTERPRISE' WHERE plano IS NULL` (idempotent backfill), `limite_utilizadores` deliberately left NULL = unlimited for the one real tenant |
| 2 | [PLAN-01] `limiteUtilizadores == null` is the sole "no limit" representation — no sentinel (-1/MAX_VALUE) anywhere | VERIFIED | Field is `Integer` (wrapper) with no `nullable=false`/default; grepped codebase for `Integer.MAX_VALUE`/`limiteUtilizadores == -1` — 0 matches. Business-rule comment at `Tenant.java:39-43` states the contract explicitly |
| 3 | [ROADMAP SC4] Active-user count is a single reusable function, never duplicated; Phases 120/122 can reuse it | VERIFIED | `UserRepository.java:38` — `long countByTenantIdAndAtivoTrue(UUID tenantId);` is the only method with this name in the interface (derived query, DB-backed). `AdminController` is its only current consumer, via one shared private helper (see #12) |
| 4 | [PLAN-01] No endpoint serializes the raw `Tenant` entity — the new billing fields are never exposed to any client | VERIFIED | `grep "ResponseEntity.ok(tenant)\|body(tenant)"` across `controllers/` → 0 matches. `PublicController.getBranding()` maps explicitly to `TenantPublicInfoResponse` (nome+logoDataUrl only). `AuthController.getMe()`/`updateMe()` build `UserResponse` field-by-field, never touching `plano`/`limiteUtilizadores`. `grep "limiteUtilizadores\|plano"` across `dtos/` → 0 matches |
| 5 | [ROADMAP SC2] `POST /api/v1/admin/users` returns `409 CONFLICT` with `"Limite de utilizadores atingido para o vosso plano."` once the tenant's active users reach `limiteUtilizadores` | VERIFIED | `AdminController.java:89-99` (helper) + `:137-143` (call site in `createUser`). Message string appears exactly once in the file (`grep` confirmed). Proven by test `createUser_noLimiteDevolve409ENaoGravaNada` — re-run fresh this session, passing |
| 6 | [PLAN-02] Below the limit, `POST /api/v1/admin/users` still returns `201 CREATED` with the standard `UserResponse` — zero regression on the happy path | VERIFIED | `AdminController.java:151-175`, unchanged shape. Proven by `createUser_abaixoDoLimiteDevolve201EGravaUmaVez` — re-run fresh, passing |
| 7 | [PLAN-02] A tenant with `limiteUtilizadores == NULL` is never blocked | VERIFIED | `AdminController.java:91` — `tenant.getLimiteUtilizadores() != null` guard short-circuits before the count query. Proven by `createUser_limiteNuloNuncaBloqueiaENaoExecutaContagem`, which asserts `countByTenantIdAndAtivoTrue` is `never()` invoked — re-run fresh, passing |
| 8 | [ROADMAP SC3 / PLAN-04] Deactivating a user immediately frees a slot — the count is read live on every request, never cached | VERIFIED | No caching/memoization anywhere in the call chain; each request re-queries `countByTenantIdAndAtivoTrue`. Proven by `createUser_contagemAoVivoLibertaVagaAposDesativacao`, a chained-mock test (`3L, 2L`) asserting `CONFLICT` then `CREATED` across two consecutive calls on the same controller instance — re-run fresh, passing |
| 9 | [PLAN-02] The tenant used in the check is always `principal.getTenantId()` — never a tenant id read from the request body | VERIFIED | `grep 'body.get("tenant_id")\|body.get("tenantId")\|containsKey("tenant_id")'` on `AdminController.java` → 0 matches. Both call sites of the helper (`:139`, `:213`) pass `principal.getTenantId()` explicitly; the helper itself takes `tenantId` as a parameter and never reads `body` |
| 10 | [PLAN-02] The limit check runs after all existing format validations and before `userRepository.save()` | VERIFIED | `createUser`: check block at `:131-143`, positioned after the `roles.isEmpty()` early-return (`:127-129`) and before `User.builder()` (`:151`) |
| 11 | [PLAN-02] `AdminController` keeps class-level `@PreAuthorize("hasRole('ADMIN')")` — this phase introduces no new authorization surface | VERIFIED | `AdminController.java:28` — annotation present, exactly once, unchanged; no method-level `@PreAuthorize` added |
| 12 | [Derived — not in original plan frontmatter, added by code-review CR-01; essential to actual goal achievement] Reactivating a disabled user (`ativo` false→true via `PUT /api/v1/admin/users/{id}`) is also subject to the same limit — closes a trivial "create with `ativo:false`, then reactivate" bypass of the entire feature | VERIFIED (fixed) | `AdminController.java:200-219` — `updateUser`'s `ativo` block now: (a) validates the value is a `Boolean` before unboxing (`:204-206`, fixes a null-triggered NPE — IN-04), then (b) on a `false→true` transition only, calls the same shared `limiteUtilizadoresExcedido` helper (`:212-217`) before `user.setAtivo(...)`. Proven by 3 dedicated tests re-run fresh this session: `updateUser_reativarNoLimiteDevolve409ENaoGravaNada`, `updateUser_reativarAbaixoDoLimiteDevolve200EGravaUmaVez`, `updateUser_editarUtilizadorAtivoSemTocarEmAtivoNuncaVerificaLimite` — all passing. Repo-wide audit of every `User.ativo` write path (`setAtivo(`/`.ativo(` grep) confirms no other reachable path exists: `DatabaseSeeder`/`SetupService` only run pre-tenant-existing (first-run/seed, out of this phase's declared boundary per 117-CONTEXT.md), and `AuthController.getMe()`'s `.ativo(true)` is a DTO default on a read-only response, never persisted (`AuthController` never calls `userRepository.save()` with a hardcoded `ativo` value) |

**Score:** 12/12 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/lexcv/models/TenantPlano.java` | Enum, `STARTER`/`STANDARD`/`ENTERPRISE`, min 5 lines | ✓ VERIFIED | 7 lines, exact 3-constant enum, no methods/fields, structural analog of `DocumentoTipo` |
| `backend/src/main/java/com/lexcv/models/Tenant.java` | `plano`+`limiteUtilizadores` fields, `limite_utilizadores` column | ✓ VERIFIED | Fields present at `:35-45` with correct annotations and business-rule comment |
| `backend/src/main/java/com/lexcv/repositories/UserRepository.java` | `countByTenantIdAndAtivoTrue` reusable count | ✓ VERIFIED | Present at `:38`, sole definition, documented as single source of truth |
| `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql` | 2× `ADD COLUMN` + backfill | ✓ VERIFIED | Present, correct header convention, correct SQL (see Truth #1); registered in `.planning/STATE.md` Pending Todos (`STATE.md:130`) as a required manual pre-deploy step |
| `backend/src/main/java/com/lexcv/controllers/AdminController.java` | 409 enforcement, message string | ✓ VERIFIED | Helper `limiteUtilizadoresExcedido` (`:89-99`) + 2 call sites (`createUser` `:139`, `updateUser` `:213`); message appears exactly once |
| `backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java` | 4 documented behaviors, min 90 lines | ✓ VERIFIED | 267 lines, 9 `@Test` methods (4 original `createUser` cases + 3 `updateUser` reactivation cases from CR-01 + 1 `ativo:false` case from WR-03 + 1 null-`ativo` case from IN-04), all independently re-run and green this session |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| `Tenant.java` | `TenantPlano.java` | `@Enumerated(EnumType.STRING)` field | WIRED | `Tenant.java:35-37` |
| `migrations/117-...sql` | `Tenant.java` | Matching `@Column(name=...)` strings | WIRED | Both use the literal string `limite_utilizadores` and `plano` |
| `AdminController.createUser` | `UserRepository.countByTenantIdAndAtivoTrue` | Shared private helper `limiteUtilizadoresExcedido(principal.getTenantId())` | WIRED (pattern reinterpreted — see note) | **Note:** 117-02-PLAN.md's frontmatter `key_links.pattern` (`countByTenantIdAndAtivoTrue\(principal\.getTenantId\(\)\)`) matched the *original* implementation, inline in `createUser`. Round-1 review (CR-01) extracted the check into a private helper `limiteUtilizadoresExcedido(UUID tenantId)` (`:89-99`) to close a critical reactivation bypass (see Truth #12). The literal regex no longer appears verbatim, but I independently traced the call graph: `limiteUtilizadoresExcedido(` has exactly 2 call sites in the file (`:139` in `createUser`, `:213` in `updateUser`), **both** passing `principal.getTenantId()`; inside the helper, `countByTenantIdAndAtivoTrue(tenantId)` (`:92`) and `tenantRepository.findById(tenantId)` (`:90`) consume that same value unmodified — 0 alternate call sites, 0 body-sourced tenant ids anywhere. The must-have's actual intent (tenant isolation, principal-only source) is fully preserved and now enforced at *two* call sites instead of one |
| `AdminController.updateUser` | `limiteUtilizadoresExcedido` helper | Reactivation guard `novoAtivo && !Boolean.TRUE.equals(user.getAtivo())` | WIRED | `:212-217` — did not exist in the original Plan 02 scope; added specifically by CR-01 to close the bypass. Proven by 2 dedicated tests (409 at limit, 200 below) |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `AdminController.limiteUtilizadoresExcedido` | `utilizadoresAtivos` | `userRepository.countByTenantIdAndAtivoTrue(tenantId)` — Spring Data JPA derived query (no `@Query` body; auto-generated `SELECT COUNT(u) FROM User u WHERE u.tenantId=?1 AND u.ativo=true`) against the real `t_user` table | Yes — genuine DB-backed `COUNT`, not hardcoded/static | FLOWING |
| `AdminController.limiteUtilizadoresExcedido` | `tenant.getLimiteUtilizadores()` | `tenantRepository.findById(tenantId)` — standard `JpaRepository` lookup against `t_tenant` | Yes | FLOWING |

### Behavioral Spot-Checks

This codebase has no MockMvc/`@SpringBootTest` HTTP harness (confirmed convention, documented in both plans) — Mockito-driven direct controller invocation is the established way to prove backend behavior here. All checks below were re-executed fresh in this verification session (not copied from SUMMARY.md/117-REVIEW.md):

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| 409-at-limit / 201-below / NULL-bypass / live-recount (`createUser`, 4 cases) + reactivation-limit (`updateUser`, 3 cases) + `ativo:false` no-check (1 case) + null-`ativo` guard (1 case) | `mvn test -Dtest=AdminControllerLimiteUtilizadoresTest` | 9 tests, 0 failures, 0 errors | PASS |
| Full backend regression suite unaffected | `mvn clean test` (fresh clean build, not relying on stale `target/`) | 93 tests across 8 classes, 0 failures, 0 errors, 0 skipped (verified by summing all `target/surefire-reports/*.txt`, not by log text alone) | PASS |
| SAST stays clean | `mvn clean test spotbugs:check` (chained, exit 0) | Exit code 0, no `BugInstance`/failure output | PASS |
| Backend still packages | `mvn -DskipTests package` | Exit code 0; `backend-0.0.1-SNAPSHOT.jar` (69.3 MB) produced | PASS |
| No new dependency introduced | `git diff --stat e0dc400^ HEAD -- backend/pom.xml` | Empty diff | PASS |
| `spotbugs-exclude.xml` untouched (no suppressions added to hide findings) | `git diff e0dc400^ HEAD -- backend/spotbugs-exclude.xml` | Empty diff | PASS |
| All 9 phase commits genuinely exist with matching content | `git show <hash>` for `e0dc400, 24ee81a, b300f4f, c2525a8, 3bf122d, 2dcb10c, f2f9bf0, d0a859a, e316d57` | All 9 found; diffs match their stated commit messages/scope | PASS |
| Diff scope matches declared `files_modified` exactly | `git diff --stat e0dc400^ HEAD -- backend/ .planning/STATE.md` | Exactly 7 files: `AdminController.java`, `Tenant.java`, `TenantPlano.java`, `UserRepository.java`, `AdminControllerLimiteUtilizadoresTest.java`, migration `.sql`, `.planning/STATE.md` — no scope creep | PASS |

### Probe Execution

SKIPPED — no `scripts/*/tests/probe-*.sh` convention exists in this repository (`find . -path '*/scripts/*/tests/probe-*.sh'` → 0 results), and neither plan nor either SUMMARY declares a probe.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| PLAN-01 | 117-01 | `Tenant` tem campos `plano` e `limite_utilizadores` | ✓ SATISFIED | Truths #1, #2, #3, #4 |
| PLAN-02 | 117-02 | Criar utilizador é bloqueado (409) quando o tenant atinge `limite_utilizadores` (conta só `ativo=true`) | ✓ SATISFIED | Truths #5, #6, #7, #9, #10, #11 |
| PLAN-04 | 117-02 | Desativar utilizador liberta vaga imediatamente no limite | ✓ SATISFIED | Truth #8, reinforced by Truth #12 (the symmetric reactivation case) |

**Orphaned requirements check:** REQUIREMENTS.md's Phase-mapping table lists exactly `PLAN-01`, `PLAN-02`, `PLAN-04` against Phase 117 (`PLAN-03` correctly maps to Phase 118, the frontend indicator — not orphaned, just out of this phase's scope). Both plans' frontmatter (`requirements: [PLAN-01]` and `requirements: [PLAN-02, PLAN-04]`) together cover exactly this set. **0 orphaned requirements.**

### Anti-Patterns Found

No blocking or warning-level debt markers. `grep`-style scans for `TBD|FIXME|XXX|TODO|HACK|PLACEHOLDER` in all 6 phase-modified files returned only false positives (Portuguese words `todos`/`metodo` containing the substring `todo`, case-insensitively). The items below are pre-existing, already-triaged code-review observations (117-REVIEW.md), independently re-confirmed by direct file inspection this session — informational only, none block the phase goal:

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AdminController.java` | 89-99 (comment 81-88) | Count-then-compare is not atomic (no lock/`@Version`/DB constraint); reachable from both `createUser` and `updateUser` reactivation | Info (accepted risk — `T-117-07` disposition in `117-02-PLAN.md`) | Under concurrent requests near the limit, up to N extra users could theoretically be created; explicitly accepted for a low-volume, ADMIN-only, manually-billed endpoint |
| `migrations/117-add-tenant-plano-limite-utilizadores.sql` | 28-29 | `ALTER TABLE ... ADD COLUMN` without `IF NOT EXISTS` (non-idempotent) | Info (matches every other script in `backend/migrations/`, repo-wide convention) | Re-running against an already-migrated DB fails with "column already exists"; no data-corruption risk |
| `migrations/117-add-tenant-plano-limite-utilizadores.sql` | 29 | No DB-level `CHECK` against negative/zero `limite_utilizadores` | Info (deferred to Phase 120 — no write-path for this column exists yet; `grep 'setLimiteUtilizadores'` → 0 matches) | Theoretical only today |
| `AdminController.java` | 90-91 | `limiteUtilizadoresExcedido` fails open if the caller's own tenant row is missing | Info (deliberate, documented in 117-CONTEXT.md; deferred to Phase 119/120 tenant lifecycle work) | Currently unreachable — no tenant-deletion capability exists yet in this codebase |
| `AdminController.java` | 137 | `createUser`'s `(Boolean) body.get("ativo")` cast isn't hardened the way `updateUser`'s now is; a non-null, non-Boolean value throws `ClassCastException`, surfaced as a generic 500 by `GlobalExceptionHandler` | Info (pre-existing pattern predating Phase 117, matches every other unchecked `Map` cast in this controller — `nome`/`email`/`telefone`/`roles`/`permissions`) | Malformed request → generic 500 instead of a clean 400; not a Phase 117 regression, not this phase's scope to fix |

### Human Verification Required

None. This is a backend-only phase (explicitly scoped that way in `117-CONTEXT.md`'s `<domain>` section) with no UI, no visual/real-time behavior, and no external-service integration. Every observable truth is provable — and was independently re-proven this session — via automated Mockito tests plus direct source inspection. Neither `117-01-PLAN.md` nor `117-02-PLAN.md` contains any `<verify><human-check>` blocks to harvest.

### Gaps Summary

No gaps. All 4 ROADMAP Success Criteria and all 3 requirement IDs (PLAN-01, PLAN-02, PLAN-04) are satisfied by the current HEAD, independently re-verified rather than taken from SUMMARY.md/117-REVIEW.md claims:

- The data layer (`TenantPlano`, `Tenant.plano`/`limiteUtilizadores`, `UserRepository.countByTenantIdAndAtivoTrue`, the migration script) exists, is substantive, and is wired exactly as specified.
- `POST /api/v1/admin/users` enforces the limit correctly (409 at capacity, 201 below, unconditional bypass on `NULL`, live uncached recount after deactivation) — all 4 original behaviors proven by dedicated, independently-passing tests.
- Critically, the phase's initial implementation (as described by 117-02-SUMMARY.md, written before code review) had a real, exploitable gap that would have undermined the entire feature: an admin at capacity could create a user with `"ativo": false` (which never counted against the limit) and then immediately reactivate them via `PUT /api/v1/admin/users/{id}` with `{"ativo": true}` — a path that originally had **zero** limit enforcement. This was caught by code review (CR-01) and fixed by routing both `createUser` and the reactivation branch of `updateUser` through one shared, well-tested helper. I independently confirmed this fix is present, correct, and covered by 3 new tests, and audited every other `User.ativo` write path in the codebase (seed/setup-time paths only, out of this phase's declared scope) to confirm no other bypass surface exists.
- Full regression suite (93/93), SpotBugs/FindSecBugs (0 findings), and packaging (`mvn -DskipTests package`) were all re-run fresh in this session, not copied from prior reports, and all passed.
- The two PLAN-02 `key_links` whose literal regex patterns no longer match verbatim (due to the CR-01 helper-extraction refactor) were resolved by call-graph tracing rather than blind literal grep — the underlying tenant-isolation guarantee they were meant to verify is intact and, if anything, strengthened.
- Remaining code-review observations (WR-01/WR-02/IN-01/IN-02/IN-05) are pre-existing, already-triaged, explicitly accepted or deferred-by-design items — none block this phase's goal, and none were newly discovered by this verification pass.

---

_Verified: 2026-07-29T15:00:00Z_
_Verifier: Claude (gsd-verifier)_
