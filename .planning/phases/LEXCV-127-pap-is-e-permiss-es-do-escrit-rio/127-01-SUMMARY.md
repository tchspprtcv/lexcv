---
phase: 127-pap-is-e-permiss-es-do-escrit-rio
plan: 01
subsystem: auth
tags: [spring-security, jwt, rbac, multi-tenant, java]

# Dependency graph
requires:
  - phase: 126-migracao-papeis-escritorio
    provides: ResolucaoPapeisService (resolverNomesPapeis, resolverPermissoesEfectivas, temPapelDeMolde(User, String), usaPapeisDeEscritorio single discriminator), TenantRole.moldeId provenance field
provides:
  - UserPrincipal.moldeIds -- molde provenance carried on the security principal, resolved once per request
  - ResolucaoPapeisService.resolverMoldeIds(User) -- third resolver parcel, same shape as resolverNomesPapeis/resolverPermissoesEfectivas
  - ResolucaoPapeisService.temPapelDeMolde(UserPrincipal, String) -- provenance overload for sites that only hold the principal, not a loaded User
  - ParecerController's deliver-parecer and create-version guards converted from literal-name to provenance
affects: [127-02, 127-03, 127-04, 127-05, 127-06, 127-07, 127-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Molde provenance resolved exactly once per request in JwtAuthenticationFilter, alongside roles/permissions, then carried on UserPrincipal for business-logic sites that only have the principal (no loaded User) -- avoids a second per-request query"
    - "UserPrincipal.create() signature change ripples through exactly two production call sites (JwtAuthenticationFilter, VerificacaoDerivaPapeisService x2) and one direct test call site, verified by full-module compile"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/ParecerControllerEntregaProveniencaTest.java
  modified:
    - backend/src/main/java/com/lexcv/config/UserPrincipal.java
    - backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java
    - backend/src/main/java/com/lexcv/services/ResolucaoPapeisService.java
    - backend/src/main/java/com/lexcv/services/VerificacaoDerivaPapeisService.java
    - backend/src/main/java/com/lexcv/controllers/ParecerController.java
    - backend/src/test/java/com/lexcv/config/UserPrincipalCatalogoSyncTest.java
    - backend/src/test/java/com/lexcv/services/ResolucaoPapeisServiceTest.java

key-decisions:
  - "resolverMoldeIds' office branch skips null moldeId (a root-created office role never claims provenance); its global-fallback branch maps Role::getId directly, since a global role IS its own molde"
  - "temPapelDeMolde(UserPrincipal, String) fails closed (false) on unresolvable molde name or null/empty moldeIds -- never fails open"
  - "capturarAntes passes Set.of() for moldeIds (it measures the pre-conversion global-roles world by design, never routes through ResolucaoPapeisService); verificarSemDeriva passes resolverMoldeIds(user) since it already goes through the resolver"

requirements-completed: [PAPEL-04, PAPEL-08]

# Metrics
duration: ~55min (session spanned one rate-limit interruption/reset between Task 1 edits and Task 1 commit; net active work across two task commits)
completed: 2026-09-21
---

# Phase 127 Plan 01: Molde Provenance on UserPrincipal Summary

**UserPrincipal now carries `moldeIds` (resolved once per request by JwtAuthenticationFilter via a new `ResolucaoPapeisService.resolverMoldeIds`), and both `ParecerController` ADMIN guards decide by that provenance instead of comparing `principal.getRoles().contains("ADMIN")` — so a renamed office ADMIN role keeps the ability to deliver pareceres and create versions.**

## Performance

- **Duration:** ~55 min active work (session included one rate-limit interruption/reset between finishing Task 1's edits and committing them; no work was lost, verification was re-run before committing per the coordinator's instruction)
- **Completed:** 2026-09-21
- **Tasks:** 2/2
- **Files modified:** 7 (6 modified + 1 new test file)

## Accomplishments
- `UserPrincipal` gained a `moldeIds` field (`@Builder.Default` to `Set.of()`), populated as the third parcel in `JwtAuthenticationFilter` alongside `roles`/`permissions` — no new per-request query, since it comes from the same already-loaded `User` whose `tenantRoles` is `FetchType.EAGER`.
- `ResolucaoPapeisService.resolverMoldeIds(User)` mirrors `resolverNomesPapeis`'s structure exactly, routing through the same `usaPapeisDeEscritorio` discriminator, with the office branch skipping null `moldeId` and the global-fallback branch mapping `Role::getId` directly.
- `ResolucaoPapeisService.temPapelDeMolde(UserPrincipal, String)` overload added for call sites that only hold the principal (no loaded `User`) — fails closed on an unresolved molde name or an empty/null `moldeIds`.
- Both `ParecerController` sites (`entregarSolicitacao:423`, `createVersao:494`) converted from `principal.getRoles().contains("ADMIN")` to `resolucaoPapeisService.temPapelDeMolde(principal, NOME_MOLDE_ADMIN)` — the 403 status and both message strings are byte-for-byte unchanged.
- Both production call sites of `VerificacaoDerivaPapeisService.UserPrincipal.create` updated to compile against the new signature: `capturarAntes` passes `Set.of()` (by design — it measures the pre-conversion world, which has no office-molde provenance to measure), `verificarSemDeriva` passes `resolucaoPapeisService.resolverMoldeIds(user)` (it already routes through the resolver).
- New behavioral test `ParecerControllerEntregaProveniencaTest` (6 cases: 3 scenarios × 2 endpoints) proves the regression this task exists to prevent: a principal whose `moldeIds` contains the ADMIN molde id but whose `roles` set contains only a renamed office role name is still allowed to deliver a parecer and create a version.

## Task Commits

Each task was committed atomically:

1. **Task 1: Provenance on the principal, resolved once in the filter** - `e46f7ff3` (feat)
2. **Task 2: ParecerController's two name-based guards become provenance-based** - `d4024108` (feat)

**Plan metadata:** (this commit, following the summary)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/config/UserPrincipal.java` - Added `moldeIds` field (`@Builder.Default`) and `create()` parameter
- `backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` - Resolves `moldeIds` as the third parcel alongside roles/permissions, passes it to `UserPrincipal.create`
- `backend/src/main/java/com/lexcv/services/ResolucaoPapeisService.java` - Added `resolverMoldeIds(User)` and `temPapelDeMolde(UserPrincipal, String)` overload
- `backend/src/main/java/com/lexcv/services/VerificacaoDerivaPapeisService.java` - Updated both `UserPrincipal.create` call sites for the new signature (`Set.of()` at `capturarAntes`, `resolverMoldeIds` at `verificarSemDeriva`)
- `backend/src/main/java/com/lexcv/controllers/ParecerController.java` - Added `NOME_MOLDE_ADMIN` constant; both ADMIN guards now call `temPapelDeMolde(principal, NOME_MOLDE_ADMIN)`
- `backend/src/test/java/com/lexcv/config/UserPrincipalCatalogoSyncTest.java` - Updated the one direct `UserPrincipal.create` test call site for the new parameter
- `backend/src/test/java/com/lexcv/services/ResolucaoPapeisServiceTest.java` - Added 5 new cases: `resolverMoldeIds` office branch (null `moldeId` skipped), global fallback, and 3 cases for the `temPapelDeMolde(UserPrincipal, String)` overload
- `backend/src/test/java/com/lexcv/controllers/ParecerControllerEntregaProveniencaTest.java` - New file, 6 test methods proving provenance survives a role rename on both endpoints, plus the unchanged-refusal case

## Decisions Made
- Followed the plan's interface contract exactly: `resolverMoldeIds` mirrors `resolverNomesPapeis`'s shape and routes through the single `usaPapeisDeEscritorio` discriminator, never re-evaluating the condition inline.
- Left the `roles.contains("ADMIN")` hardcoded-catalogue block inside `UserPrincipal.create` untouched, per the plan's explicit instruction — it is the legacy global-role safety net pinned by `UserPrincipalCatalogoSyncTest`, and is redundant for the office path because `DatabaseSeeder` already gives the ADMIN molde the full catalogue.
- `ParecerControllerEntregaProveniencaTest` asserts `HttpStatus.CREATED` (not `OK`) for the two `createVersao` acceptance scenarios — the endpoint's actual success status, discovered by running the test (see Issues Encountered).

## Deviations from Plan

None requiring a rule — one self-corrected test-authoring mistake (see Issues Encountered) and one plan-defect observation (see below), neither required scope changes.

## Issues Encountered

- **Test authored against the wrong success status.** While writing `ParecerControllerEntregaProveniencaTest`, the two `criarVersao` acceptance-scenario tests initially asserted `HttpStatus.OK`. Running the test surfaced `expected: <200 OK> but was: <201 CREATED>` — `createVersao` returns 201 on success (it inserts a new `ParecerVersao` row), unlike `entregarSolicitacao` which returns 200. Fixed by asserting `HttpStatus.CREATED` for those two cases; re-ran and all 6 tests pass. Not a deviation from the plan (the plan didn't specify the exact status code), just a test-writing correction caught by actually running the test as required.

- **Plan-defect: a Task 1 acceptance-criteria grep overcounts by one, on a pre-existing comment.** The criterion `grep -F -c 'userRepository.findById' backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java` is exactly 1` does not hold literally — the count is 2. The second match is not a second query: it's inside a pre-existing comment at line 64 (`"...por isso o userRepository.findById(userId) da linha 45 ja a traz..."`) that already existed in the file before this plan touched it (verified against the file as read at the start of this task, before any edit). The actual invariant the criterion exists to protect — no second per-request user load introduced — holds: there is exactly one real call site, `User user = userRepository.findById(userId).orElse(null);` at line 44. I did not loosen or rewrite the criterion; I verified the underlying invariant directly by reading the code rather than trusting the naive grep count, per the plan's own instruction to prefer real verification over a contradicting gate.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The debt blocking PAPEL-04 (renaming a tenant's role) is closed: both `ParecerController` sites that used to depend on `TenantRole.nome` literally matching the molde name now survive a rename.
- `UserPrincipal.moldeIds` is now available as a building block for later plans in this phase (e.g. PAPEL-08's "cannot delete/strip the office admin role" guard, which the phase context names as discriminating by the same `moldeId` provenance).
- Full backend suite green at 313 tests (baseline 302 + 11 new from this plan). `mvn spotbugs:check` clean. No schema change, no new dependency.
- No blockers for subsequent plans in this phase.

---
*Phase: 127-pap-is-e-permiss-es-do-escrit-rio*
*Completed: 2026-09-21*

## Self-Check: PASSED

All key files verified present on disk (UserPrincipal.java, JwtAuthenticationFilter.java,
ResolucaoPapeisService.java, VerificacaoDerivaPapeisService.java, ParecerController.java,
UserPrincipalCatalogoSyncTest.java, ResolucaoPapeisServiceTest.java,
ParecerControllerEntregaProveniencaTest.java, this SUMMARY.md). Both task commits
(`e46f7ff3`, `d4024108`) verified present in `git log --oneline --all`.
