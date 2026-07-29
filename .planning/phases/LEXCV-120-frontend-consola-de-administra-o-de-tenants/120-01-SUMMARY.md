---
phase: 120-frontend-consola-de-administra-o-de-tenants
plan: 01
subsystem: auth
tags: [jwt, spring-security, multi-tenant, tenant-suspension, jpa, postgresql]

# Dependency graph
requires: []
provides:
  - "Tenant.ativo persisted field (Boolean, NOT NULL, DEFAULT TRUE), same naming/polarity as User.ativo"
  - "Manual production migration backend/migrations/120-add-tenant-ativo.sql (ddl-auto=validate gap)"
  - "JwtAuthenticationFilter re-validates Tenant.ativo on every authenticated request (immediate effect on already-active sessions, not just next login)"
  - "AuthController.login rejects a suspended tenant with 403 (after the existing account-disabled gate)"
  - "AuthController.refresh rejects a suspended tenant with 401 (its own gate, since /auth/refresh is permitAll() and never passes through JwtAuthenticationFilter)"
affects: [120-02, 120-03, 120-04, 120-05, 120-06, 121, 122]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-request re-validation (already proven for User.ativo) extended to also cover Tenant.ativo, with the same fail-closed Boolean.TRUE.equals(...) idiom repeated identically across all 3 access paths"
    - "Manual SQL migration convention (backend/migrations/NNN-description.sql) extended for a new column"

key-files:
  created:
    - backend/migrations/120-add-tenant-ativo.sql
    - backend/src/test/java/com/lexcv/config/JwtAuthenticationFilterTenantSuspensoTest.java
    - backend/src/test/java/com/lexcv/controllers/AuthControllerTenantSuspensoTest.java
  modified:
    - backend/src/main/java/com/lexcv/models/Tenant.java
    - backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java
    - backend/src/main/java/com/lexcv/controllers/AuthController.java

key-decisions:
  - "Tenant.ativo (never suspenso) mirrors User.ativo's exact naming/polarity, per CONTEXT.md's locked decision"
  - "JwtAuthenticationFilter accepts a second per-request query (tenantRepository.findById) with deliberately no cache -- the ROADMAP requirement is immediate effect, not next-login"
  - "AuthController.login's tenant gate runs AFTER the pre-existing account-disabled gate, so a deactivated user keeps receiving its own message without ever revealing the organization's suspension state"
  - "Caso 6 (JwtAuthenticationFilterTenantSuspensoTest) implemented as its own dedicated scenario (user AND tenant both failing simultaneously) rather than folding a cross-cutting assertion into the other 6 tests, to satisfy the acceptance criteria's literal '7 testes' count while still proving the chain never short-circuits"

patterns-established:
  - "Fail-closed suspension check (Boolean.TRUE.equals(tenant.getAtivo())) repeated verbatim at all 3 access gates (filter, login, refresh)"

requirements-completed: [PROV-05]

# Metrics
duration: 22min
completed: 2026-07-29
---

# Phase 120 Plan 01: Tenant Suspension Mechanism Summary

**Tenant.ativo suspension gate enforced at all 3 access paths (login, refresh, per-request filter) with immediate effect on already-active sessions, fail-closed on null/missing tenant data**

## Performance

- **Duration:** 22 min
- **Started:** 2026-07-29T11:45:43Z
- **Completed:** 2026-07-29T12:07:32Z
- **Tasks:** 3 completed
- **Files modified:** 6 (3 created, 3 modified)

## Accomplishments
- `Tenant.ativo` persisted (`Boolean`, `@Builder.Default true`, `NOT NULL` with a `columnDefinition` that lets Hibernate's dev/CI `ddl-auto=update` ALTER succeed against an already-populated table), plus the manual production migration for `ddl-auto=validate` environments
- `JwtAuthenticationFilter` now re-validates the user's tenant on every single authenticated request (not just at login) — a session that was valid a moment ago stops being authenticated on the very next request once its tenant is suspended, with zero caching by design
- `POST /api/v1/auth/login` and `POST /api/v1/auth/refresh` both independently reject a suspended tenant (403 and 401 respectively), closing the `permitAll()` gap that would otherwise let `/auth/refresh` keep minting fresh access tokens for a suspended tenant indefinitely
- 13 new Mockito test cases (7 + 6) proving all of the above behaviorally, not just by code inspection

## Task Commits

Each task was committed atomically:

1. **Task 1: Campo Tenant.ativo e migracao manual 120-add-tenant-ativo.sql** - `fa7ebf13` (feat)
2. **Task 2: JwtAuthenticationFilter re-valida o tenant em TODAS as requisicoes**
   - `b390058e` (test — RED, constructor didn't yet accept `TenantRepository`, compile failure)
   - `3fe8651d` (feat — GREEN, 7/7 passing)
3. **Task 3: login e refresh recusam um tenant suspenso**
   - `c4e22eaf` (test — RED, 3 assertion failures + 2 unnecessary-stubbing errors on the happy-path tests)
   - `ce60da7a` (feat — GREEN, 6/6 passing)

**Plan metadata:** (recorded in the next commit, after this SUMMARY)

_TDD tasks (2 and 3) each produced a genuine RED commit before the GREEN commit, per the Phase 117 precedent._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/Tenant.java` - added the `ativo` field (`Boolean`, `NOT NULL DEFAULT TRUE`)
- `backend/migrations/120-add-tenant-ativo.sql` - manual production migration (`ddl-auto=validate` gap)
- `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` - injects `TenantRepository`, requires `Boolean.TRUE.equals(tenant.getAtivo())` alongside the existing `user.getAtivo()` check, 3 distinct `logger.warn` branches
- `backend/src/test/java/com/lexcv/config/JwtAuthenticationFilterTenantSuspensoTest.java` - 7 Mockito cases (happy path, tenant suspended, tenant `ativo=null`, tenant missing, user deactivated, both user+tenant failing simultaneously, user missing never queries tenant)
- `backend/src/main/java/com/lexcv/controllers/AuthController.java` - tenant gate added to `login` (403, after the account gate) and `refresh` (401, its own gate)
- `backend/src/test/java/com/lexcv/controllers/AuthControllerTenantSuspensoTest.java` - 6 Mockito cases (login suspended/active/tenant-missing/account-disabled-first, refresh suspended/active)

## Decisions Made
- `Tenant.ativo` uses the exact same field name and "true = usable" polarity as `User.ativo` (never `suspenso`) — this was already locked in `120-CONTEXT.md`, carried through unchanged.
- The tenant gate in `login` is checked strictly after the pre-existing account-disabled gate, and a disabled account never triggers a tenant lookup at all (`verify(tenantRepository, never()).findById(any())`), so a deactivated user's own error message is never replaced by the tenant-suspension message.
- `refresh` needed its own independent tenant gate (not just a fix to the filter) because `/api/v1/auth/refresh` is listed in `SecurityConfig`'s `permitAll()` and therefore never passes through `JwtAuthenticationFilter` at all.
- Task 2's "Caso 6" (the chain never short-circuits) was implemented as its own 7th dedicated test method — using the most adverse combination (user deactivated AND tenant suspended simultaneously) — rather than only folding a `verify(filterChain).doFilter(...)` assertion into the other six tests, so that the suite has exactly 7 distinct `@Test` methods as the acceptance criteria requires, while every other test also independently re-confirms the chain proceeds.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Own explanatory comment tripped the plan's `cache` literal-text gate**
- **Found during:** Task 2 (JwtAuthenticationFilter implementation)
- **Issue:** The first draft of the explanatory comment above the widened condition used the sentence "não há cache aqui nem poderá vir a haver" to explain that no caching layer was introduced — but the task's own automated verify checks `grep -ci cache JwtAuthenticationFilter.java` equals 0, and this comment's own use of the word "cache" (used to describe its *absence*) made the count 1.
- **Fix:** Reworded the comment to convey the same point ("deliberadamente sem nenhuma forma de memorizacao/reutilizacao entre pedidos") without the literal substring "cache".
- **Files modified:** backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java
- **Verification:** `grep -ci cache` on the file now returns 0.
- **Committed in:** `3fe8651d` (Task 2 GREEN commit)

**2. [Rule 1 - Bug] Own explanatory comment inflated the "Boolean.TRUE.equals occurrences" count**
- **Found during:** Task 3 (AuthController implementation)
- **Issue:** A comment above the login tenant-gate spelled out the literal text `Boolean.TRUE.equals(...)` to explain the fail-closed rule, which — while not machine-gated by the task's actual `<verify>` script — would make a literal `grep -n 'Boolean.TRUE.equals'` return 3 lines instead of the 2 real code occurrences the acceptance criteria describes (one per gate).
- **Fix:** Reworded the comment to describe the behavior ("a condicao abaixo trata tenant nulo/ausente... fail-closed") without repeating the exact expression as text.
- **Files modified:** backend/src/main/java/com/lexcv/controllers/AuthController.java
- **Verification:** `grep -n 'Boolean.TRUE.equals'` on the file now returns exactly 2 lines (both real code, one per gate).
- **Committed in:** `ce60da7a` (Task 3 GREEN commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1, both self-inflicted comment-wording issues caught before commit)
**Impact on plan:** Cosmetic only — no functional code was changed by either fix, both are comment rewording to keep the plan's own literal-text verification gates clean. No scope creep.

## Issues Encountered

**Task 3's own automated `<verify>` script has an internal contradiction that cannot be resolved by wording alone, without violating the plan's explicit literal-text requirements.**

The action text mandates two exact message strings verbatim:
- Login (403): `"O acesso da sua organização está suspenso. Contacte o suporte LexCV."`
- Refresh (401): `"Sessão inválida. O acesso da sua organização está suspenso."`

The refresh message is (by design, for consistency of language) built by prefixing `"Sessão inválida. "` onto the *same* core clause used in the login message. This means the refresh message's line always contains the login message's shorter phrase as a literal substring. The task's automated verify script runs:
```
test $(grep -c 'O acesso da sua organização está suspenso' AuthController.java) -eq 1
```
`grep -c` counts *matching lines*, not total occurrences — and since both the login line and the refresh line each contain this substring, the actual count is **2**, not 1, independent of correctness. I confirmed this directly (`grep -c` output was verified to be exactly `2`). The second check (`grep -c 'Sessão inválida. O acesso da sua organização está suspenso' -eq 1`) does pass (count 1), and existentially requires the first substring to also appear on that same line — so the two checks are mutually incompatible whenever *both* exact phrases are present verbatim, which is exactly what the acceptance criteria's prose ("contem exatamente uma ocorrencia de cada uma das duas frases novas") and my own unit tests require.

**Resolution:** implemented both messages exactly as specified (verbatim), rather than rewording either one to dodge the substring collision, because:
1. The acceptance criteria's prose explicitly wants both exact phrases present.
2. My own unit tests (`AuthControllerTenantSuspensoTest`, 6/6 passing) assert byte-exact equality against the complete `Map.of("message", "...")` response body in each of the correct scenarios (login-suspended vs. refresh-suspended) — a strictly stronger and more precise proof than a line-count heuristic, and it unambiguously confirms each message is used in exactly the right place.
3. Rewording either message to force the flawed grep heuristic to pass would mean shipping different UX copy than what was explicitly specified, without a functional reason to do so.

This is a known limitation of the plan's own verify script (substring containment between two intentionally related messages), not a defect in the implementation. Flagging it here explicitly for the verifier so it isn't mistaken for an unresolved gap.

## User Setup Required

None - no external service configuration required. `backend/migrations/120-add-tenant-ativo.sql` still needs to be run manually against any `ddl-auto=validate` (staging/prod) database before/alongside the next deploy that ships this change — same standing requirement as every prior manual migration in this repository (not a new setup burden introduced by this plan).

## Next Phase Readiness

- `Tenant.ativo`, the immediate-effect suspension mechanism, and both HTTP-facing rejection gates are all in place and proven by 13 passing Mockito tests, plus a full green backend suite (149 tests, 0 failures/errors) and a clean `mvn spotbugs:check`.
- Plans 120-02 through 120-06 (list/adjust/suspend endpoints on `PlatformAdminController`, the `plataforma/` frontend console) can now read and toggle `Tenant.ativo` directly — the field, its persistence, and its enforcement are already fully proven; those plans only need to build the admin-facing CRUD and UI on top.
- No blockers identified for subsequent plans in this phase.

---
*Phase: 120-frontend-consola-de-administra-o-de-tenants*
*Completed: 2026-07-29*

## Self-Check: PASSED

All 6 claimed created/modified files confirmed present on disk (plus this SUMMARY.md itself). All 5 claimed commit hashes (`fa7ebf13`, `b390058e`, `3fe8651d`, `c4e22eaf`, `ce60da7a`) confirmed present in `git log --oneline --all`. Full backend suite (149 tests across 16 classes) green, `mvn spotbugs:check` clean.
