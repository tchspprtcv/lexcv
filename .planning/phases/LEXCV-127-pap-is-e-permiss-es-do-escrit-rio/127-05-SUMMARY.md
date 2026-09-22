---
phase: LEXCV-127-pap-is-e-permiss-es-do-escrit-rio
plan: 05
subsystem: api
tags: [spring-boot, jpa, rbac, multi-tenant, java]

# Dependency graph
requires:
  - phase: LEXCV-127-pap-is-e-permiss-es-do-escrit-rio (Plano 04)
    provides: OfficeRolesController create/rename/delete for office-scoped TenantRole, and the renaming capability that exposed the 409 trapdoor this plan closes
provides:
  - "AdminController.createUser/updateUser assign office roles by TenantRole id, never by global role name"
  - "resolverPapeisEscritorioPorId: single resolution point for tenantRoleIds (malformed/foreign/reserved/empty guards)"
  - "derivarMirrorGlobalDePapeis: t_user_role mirror derived from TenantRole.moldeId provenance, never from a name"
  - "UserResponse.tenant_role_ids: stable assignment key surviving a role rename, alongside the existing display-only roles (names)"
affects: [LEXCV-127-pap-is-e-permiss-es-do-escrit-rio (Plano 08 - user form role picker consumes tenant_role_ids)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Validate-then-resolve id list: parse ALL submitted ids to UUID before touching any second repository (roleRepository), so a malformed id short-circuits with zero extra lookups"
    - "Provenance-derived legacy mirror: t_user_role.role_id populated from TenantRole.moldeId, never from a possibly-renamed TenantRole.nome, kept solely for NotificacaoService/AlertasDiariosJob reversibility"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/AdminController.java
    - backend/src/main/java/com/lexcv/dtos/UserResponse.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerAtribuicaoPapeisEscritorioTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java
    - backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java

key-decisions:
  - "Assignment resolves tenantRoleIds only against tenantRoleRepository.findByTenantId(principal.getTenantId()) -- never findById -- so a foreign-tenant id is absent by construction, not by a comparison someone could forget (same technique as Plano 03's updateRbac)"
  - "A stale client sending the old \"roles\" body key is refused with 400 naming tenantRoleIds, on both createUser and updateUser -- never silently ignored, so assignment never silently no-ops"
  - "resolverTenantRolesOuErro (the name-based 409-on-partial-mapping wrapper) is deleted entirely, not deprecated -- ResolucaoPapeisService.resolverPapeisDeEscritorio and MapeamentoParcialPapeisException are untouched and still serve MigracaoPapeisEscritorioService"
  - "UUID parsing of all submitted ids happens before the single roleRepository.findByNome(PLATAFORMA_ADMIN) lookup, so a malformed-id request touches only the tenant-role list, never roleRepository"

patterns-established:
  - "tenant_role_ids (assignment key, survives rename) vs roles (display names, current at fetch-time) are two independent fields on UserResponse answering different questions -- populated together in listUsers and createUser's 201 projection"

requirements-completed: [PAPEL-06, PAPEL-07, PAPEL-09]

# Metrics
duration: 15min
completed: 2026-09-22
---

# Phase 127 Plan 05: Assignment by Office Role Id Summary

**AdminController.createUser/updateUser assign TenantRole by id instead of resolving global role names, closing the rename-triggered 409 trapdoor Decisão 6 identified, with a provenance-derived t_user_role mirror for reversibility**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-09-22T09:12:56-01:00 (first task commit)
- **Completed:** 2026-09-22T09:27:20-01:00 (last task commit)
- **Tasks:** 3 (all `type="auto"`, no checkpoints)
- **Files modified:** 5

## Accomplishments
- `createUser`/`updateUser` now require `tenantRoleIds` (a list of `TenantRole` ids scoped to the caller's own tenant) instead of `roles` (global role names); a body still sending `roles` is refused with 400 naming `tenantRoleIds`, never silently ignored.
- A renamed office role assigns cleanly with no 409 — the exact regression Decisão 6 exists to close, proven by `createUser_comPapelRenomeado_atribuiSemConflitoEDerivaMirrorPorProveniencia` and its `updateUser` counterpart.
- `t_user_role` keeps being populated as a **derived, provenance-based mirror** (via `TenantRole.moldeId` → `roleRepository.findById`), never from a name the caller typed — preserving `NotificacaoService`'s ADMIN fan-out and `AlertasDiariosJob` per Phase 126's reversibility guarantee.
- Foreign-tenant ids, malformed ids, reserved-role ids (by literal name AND by molde provenance), and empty selections are each refused before any write — PAPEL-07 and PAPEL-09 hold on the third door onto `PLATAFORMA_ADMIN`.
- `UserResponse.tenant_role_ids` gives the edit form a stable id-based key to pre-select a user's current office roles, independent of `roles` (display names, which the app and `usePermissions`/`useMe` consumers still read).
- The now-dead name-based wrapper (`resolverTenantRolesOuErro` / `ResolucaoTenantRolesOuErro` / its 409 translation of `MapeamentoParcialPapeisException`) is removed from `AdminController`; the underlying service method and exception are untouched and still serve `MigracaoPapeisEscritorioService`.
- Completed the plan's mandatory ripple: converted every `"roles"` body in `AdminControllerPlataformaAdminContencaoTest` (the Phase 119 PLATAFORMA_ADMIN containment suite, PAPEL-09) and `AdminControllerLimiteUtilizadoresTest` (the Phase 117 user-limit suite) to `tenantRoleIds` fixtures, preserving every original guarantee via the new path.

## Task Commits

Each task was committed atomically:

1. **Task 1: Resolve assignment by office role id, with a derived global-role mirror** - `2911fc9e` (feat)
2. **Task 2: Same conversion for updateUser, and remove the dead name-based path** - `33577281` (feat)
3. **Fix (found while verifying Task 3's malformed-id acceptance criterion): defer plataforma-molde lookup until all ids parse** - `74eeaf76` (fix)
4. **Task 3: Rewrite the assignment test around ids and renaming (+ mandatory ripple)** - `48478734` (test)

**Plan metadata:** _pending — this commit_ (docs: complete plan)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - `resolverPapeisEscritorioPorId` + `derivarMirrorGlobalDePapeis` helpers; `createUser`/`updateUser` rewritten to assign by id; dead name-based wrapper removed
- `backend/src/main/java/com/lexcv/dtos/UserResponse.java` - added `tenant_role_ids` (Set\<UUID\>), documented against the pre-existing `roles` (names) field
- `backend/src/test/java/com/lexcv/controllers/AdminControllerAtribuicaoPapeisEscritorioTest.java` - fully rewritten around the id-based contract (14 tests, was 6)
- `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java` - ripple: `"roles"` bodies → `tenantRoleIds` fixtures (14 tests, same count)
- `backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java` - ripple: `corpoValido()` → `tenantRoleIds` fixture (9 tests, same count)

## Decisions Made
- **Assignment resolution is validate-then-resolve, two passes:** first parse every submitted id to `UUID` (touching only `tenantRoleRepository.findByTenantId`), then resolve the `PLATAFORMA_ADMIN` molde id once, then walk the parsed ids against the tenant's role map. This makes a malformed-id request touch exactly one repository, never `roleRepository` — found and fixed while verifying Task 3's own acceptance criterion, before the assignment test was written against it.
- **The legacy `roles` field is a hard refusal, not a silent no-op**, on both `createUser` and `updateUser` — T-127-28 (Information Disclosure: a stale client's payload silently doing nothing).
- **Case 1/4 of `AdminControllerPlataformaAdminContencaoTest`** (createUser/updateUser PLATAFORMA_ADMIN containment) dropped their `verify(roleRepository, never()).findByNome("PLATAFORMA_ADMIN")` sub-assertion — under the id contract, that lookup is now unconditional (it resolves the reserved molde id for the provenance guard) rather than a lookup of an attacker-controlled name. The core guarantee each case proves — 403, reserved-role message, no save — is unchanged and re-asserted; only the now-inapplicable implementation-detail assertion was dropped. Documented here per the mandatory_ripple instruction not to relax assertions silently.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Malformed-id parsing deferred to occur before any `roleRepository` call**
- **Found during:** Task 3 (verifying the plan's own acceptance criterion for the malformed-id test case)
- **Issue:** The Task 1 implementation resolved the `PLATAFORMA_ADMIN` molde id via `roleRepository.findByNome` unconditionally, before validating that submitted ids even parse as `UUID` — violating Task 3's stated acceptance criterion ("no repository call is made beyond the tenant role list" for a malformed id).
- **Fix:** Split `resolverPapeisEscritorioPorId` into two passes — parse all ids to `UUID` first (returning 400 on the first failure, having touched only `tenantRoleRepository.findByTenantId`), then resolve the reserved-molde id once, then walk the parsed ids.
- **Files modified:** `backend/src/main/java/com/lexcv/controllers/AdminController.java`
- **Verification:** `AdminControllerAtribuicaoPapeisEscritorioTest.createUser_comIdMalformado_devolve400ENuncaConsultaORoleRepository` asserts `verify(roleRepository, never()).findByNome(any())`; full suite green.
- **Committed in:** `74eeaf76`

---

**Total deviations:** 1 auto-fixed (1 bug fix, found via the plan's own stated acceptance criterion)
**Impact on plan:** Necessary for correctness against the plan's own stated contract. No scope creep.

## Issues Encountered
None beyond the deviation above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Backend contract is ready for Plano 08's frontend work: the user form's role picker can now list the office's own `TenantRole` rows (current names) and pre-select by `tenant_role_ids`, per UI-SPEC §2.
- Full backend suite green: 352 tests, 0 failures, 0 errors. `spotbugs:check` clean. No migration added.

---
*Phase: LEXCV-127-pap-is-e-permiss-es-do-escrit-rio*
*Completed: 2026-09-22*

## Self-Check: PASSED

All key files confirmed present on disk (AdminController.java, UserResponse.java, all 3 test files,
this SUMMARY.md) and all 4 task/fix commits (`2911fc9e`, `33577281`, `74eeaf76`, `48478734`) confirmed
in `git log`.
