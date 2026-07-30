---
phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen
plan: 03
subsystem: auth
tags: [rbac, authorization, spring-boot, mockito, tdd, multi-tenant]

# Dependency graph
requires: []
provides:
  - "Four containment guards in AdminController closing the PLATAFORMA_ADMIN self-escalation vector: createUser/updateUser reject the role with 403 before any role lookup; getRbac hides it from a tenant's Settings/RBAC screen; updateRbac extends the ADMIN-immutability continue to also cover PLATAFORMA_ADMIN"
  - "AdminControllerPlataformaAdminContencaoTest -- 8 Mockito cases proving the four rejections plus non-regression of all four tenant roles (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) across createUser/updateUser/updateRbac"
  - "Explicit CONFIRMADO verdicts for all 5 STRIDE items this plan owns (T-119-01/02/03/19/SC), with line-number evidence that each guard precedes its corresponding lookup"
affects: [119-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single private constant (PAPEL_PLATAFORMA) as the sole source of the reserved-role literal inside a controller, referenced by every guard -- avoids string-literal drift between the four call sites and the seed/PreAuthorize values defined in sibling plans"
    - "Pre-lookup rejection: a guard that inspects the raw request list and returns 403 BEFORE any repository lookup runs, proven by verify(repository, never()).methodName(...) rather than just asserting the final HTTP status"
    - "TDD RED/GREEN committed as two separate atomic commits (test then feat), matching the convention already established in Phases 117/118/119-01/119-02"

key-files:
  created:
    - backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java
  modified:
    - backend/src/main/java/com/lexcv/controllers/AdminController.java

key-decisions:
  - "Guard implemented as a full pre-scan of the entire roles list (for-loop with early return) rather than checking only the first element -- Caso 2 proves a mixed list (ADVOGADO + PLATAFORMA_ADMIN) is still rejected, closing the obvious bypass a first-element-only check would have left open"
  - "Comparison is exact String equality (PAPEL_PLATAFORMA.equals(rObj)), never equalsIgnoreCase/contains -- matches the codebase's existing exact-match role lookup (roleRepository.findByNome), so a case-varied or padded role name simply resolves to no Role and is silently dropped by the pre-existing ifPresent(...), never reaching the database in an unexpected way (T-119-20, accepted by design in the plan's threat model)"
  - "getRbac filtering done via a `continue` inside the existing for-loop (not a stream `.filter(...)`) -- smaller diff, consistent with the plan's explicit preference"
  - "updateRbac's existing ADMIN-immutability check extended in place (single if-condition with `||`) rather than a second separate guard -- keeps the one existing comment/condition as the single source of the 'these roles cannot be edited here' rule"

patterns-established:
  - "Reserved-role containment: any future reserved/system role added to this codebase's role table should get the same four-guard treatment in whichever controller resolves role names from request bodies -- reject-before-lookup on write paths, filter-on-read on list/RBAC endpoints"

requirements-completed: [PROV-01]

# Metrics
duration: ~15min
completed: 2026-07-29
---

# Phase 119 Plan 03: PLATAFORMA_ADMIN Containment Guards Summary

**Four surgical guards in `AdminController` (createUser/updateUser reject with 403 before role lookup, getRbac hides the role, updateRbac refuses to let its permissions be rewritten) close the self-escalation vector a tenant `ADMIN` would otherwise have once `PLATAFORMA_ADMIN` exists — proven by 8 new Mockito cases plus the untouched Phase 117 user-limit suite.**

## Performance

- **Duration:** ~15 min (commit span 08:01:05Z -> 08:04:34Z ~3.5 min for the TDD RED/GREEN pair; total includes upfront reading of PLAN/PROJECT/STATE/CONTEXT/PATTERNS/CLAUDE.md, source-file discovery, and the Task 2 verification gate: full suite, SpotBugs, package)
- **Started:** 2026-07-29T07:54:07Z (immediately after 119-02 completion)
- **Completed:** 2026-07-29T08:08:36Z
- **Tasks:** 2 (Task 1 executed as TDD: RED + GREEN commits; Task 2 verification-only, zero code changes needed)
- **Files modified:** 2 (1 created, 1 modified)

## Accomplishments
- `AdminController.createUser` and `updateUser` now reject any request whose `roles` list contains `"PLATAFORMA_ADMIN"` with `403 FORBIDDEN`, **before** the role-resolution loop runs (`roleRepository.findByNome` is never called for that value — proven by `verify(roleRepository, never()).findByNome("PLATAFORMA_ADMIN")` in Caso 1 and `verify(roleRepository, never()).findByNome("PLATAFORMA_ADMIN")` in Caso 7). The guard scans the **entire** submitted list, not just the first element (Caso 2: `["ADVOGADO", "PLATAFORMA_ADMIN"]` still rejected), and in `updateUser` the `return` happens before the method's unconditional trailing `userRepository.save(user)`, so no in-memory mutation (name/email/etc.) from the same request is ever persisted alongside a rejected role change.
- `AdminController.getRbac` now skips any `Role` named `PLATAFORMA_ADMIN` when building the `rolePermissions` map returned to a tenant's Settings/RBAC screen — proven by Caso 6, which seeds `roleRepository.findAll()` with the four tenant roles **plus** a `PLATAFORMA_ADMIN` role and asserts the returned map has exactly 4 entries and no `PLATAFORMA_ADMIN` key.
- `AdminController.updateRbac`'s pre-existing `"ADMIN".equals(roleName)` immutability `continue` now also covers `PLATAFORMA_ADMIN` — proven by Caso 7 (`verify(roleRepository, never()).save(any())` when the request body targets `PLATAFORMA_ADMIN`). This specifically matters because `DatabaseSeeder.upsertRolePermissions` only ever performs `addAll(...)` and never removes permissions, so any permission injected into this role via this endpoint would otherwise persist forever, surviving every subsequent restart with no self-healing reseed.
- Casos 3, 5, and 8 prove createUser, updateUser, and updateRbac continue to work exactly as before for the four tenant roles (`ADVOGADO`, `TECNICO`, `ASSISTENTE` respectively) — `HttpStatus.CREATED`/`OK` plus `verify(..., times(1)).save(any())` in each case. The pre-existing Phase 117 suite (`AdminControllerLimiteUtilizadoresTest`, 9 cases) was re-run and remains fully green, confirming the user-limit enforcement is untouched.
- All 5 STRIDE items this plan owns (T-119-01, T-119-02, T-119-03, T-119-19, T-119-SC) verified CONFIRMADO with line-number/diff evidence (see Decisions Made below and the verdict table).

## Task Commits

Each task was committed atomically:

1. **Task 1 (TDD, RED): failing tests for PLATAFORMA_ADMIN containment guards** - `a12cc39` (test)
2. **Task 1 (TDD, GREEN): PLATAFORMA_ADMIN containment guards in AdminController** - `253e4ef` (feat)
3. **Task 2: verification only** - no code changes required; full suite/SpotBugs/package all passed on first run, so no additional commit was needed beyond this plan's metadata commit.

_Note: Task 1 has two commits per this codebase's established TDD convention (RED then GREEN, e.g. Phases 117/118/119-01/119-02) — RED was confirmed as 5/8 genuine assertion failures (the rejection/filtering cases) with the other 3/8 already passing (existing tenant-role paths, unaffected by the fix) before GREEN was written._

## Files Created/Modified
- `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java` (new, 235 lines) - 8 Mockito test cases, `@ExtendWith(MockitoExtension.class)`, controller instantiated directly via its `@RequiredArgsConstructor`-generated constructor, no Spring context, `SecurityContextHolder` populated/cleared exactly as in `AdminControllerLimiteUtilizadoresTest`
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - adds `PAPEL_PLATAFORMA` constant + four guards (createUser, updateUser, getRbac, updateRbac); +46/-2 lines; zero other methods touched

## Decisions Made
- Followed the plan's exact placement instructions for all four guards (immediately after `rolesList`/inside the `for (Role role : roles)` loop/extending the existing `"ADMIN".equals(roleName)` condition) rather than restructuring any method.
- Guard is a pre-scan of the **entire** roles list with an early `return`, not a check against only the first element — this is what makes Caso 2 (mixed legitimate + reserved role) meaningful rather than trivially passing.
- Comparison is exact `String.equals`, never `equalsIgnoreCase`/`contains` — matches `roleRepository.findByNome`'s own exact-match semantics (a case-varied or padded role name already resolves to no `Role` and fails closed via the pre-existing `ifPresent(...)`, without needing the new guard to normalize anything). This mirrors the plan's own T-119-20 disposition (`accept`, by design).
- `getRbac`'s filter uses a `continue` inside the existing `for` loop rather than converting to a `.stream().filter(...)` — smaller diff, per the plan's explicit preference.
- `updateRbac`'s immutability check was extended in place (`"ADMIN".equals(roleName) || PAPEL_PLATAFORMA.equals(roleName)`) rather than adding a second, separate `if`/`continue` — keeps a single condition as the one source of "these roles are immutable via this endpoint."

## STRIDE Mitigation Verdicts (Task 2)

| Threat ID | Verdict | Evidence |
|-----------|---------|----------|
| **T-119-01** (Elevation of Privilege — atribuição do papel de plataforma) | **CONFIRMADO** | `createUser`'s guard (`AdminController.java:138`) precedes its first `roleRepository.findByNome(roleName)` call (line 147); `updateUser`'s guard (line 259) precedes its first `findByNome` call (line 268) — both guards run strictly before any role-lookup or persistence. Casos 1, 2, 4 each assert `verify(userRepository, never()).save(any())`, proving no user is created or promoted when `PLATAFORMA_ADMIN` is requested alone (Caso 1), mixed with a legitimate role (Caso 2), or via update of an existing account including self-promotion (Caso 4). |
| **T-119-02** (Tampering — reescrita das permissões do papel de plataforma) | **CONFIRMADO** | The `continue` condition in `updateRbac` (line 367) now covers `PAPEL_PLATAFORMA` alongside `"ADMIN"`. Caso 7 proves `verify(roleRepository, never()).save(any())` and `verify(roleRepository, never()).findByNome("PLATAFORMA_ADMIN")` when the request body targets `PLATAFORMA_ADMIN`. This matters specifically because `DatabaseSeeder.upsertRolePermissions` only ever does `role.getPermissions().addAll(...)` and **never removes** — an injected permission on this role would otherwise persist forever, with no self-healing reseed on any future restart. |
| **T-119-03** (Information Disclosure — papel visível no ecrã de Definições) | **CONFIRMADO** | The `continue` in `getRbac` (line 315) skips any `Role` named `PLATAFORMA_ADMIN`. Caso 6 stubs `roleRepository.findAll()` with the four tenant roles plus a `PLATAFORMA_ADMIN` role, then asserts `assertEquals(4, body.getRolePermissions().size())` and `assertFalse(body.getRolePermissions().containsKey("PLATAFORMA_ADMIN"))`. |
| **T-119-19** (Denial of Service — regressão dos caminhos legítimos) | **CONFIRMADO** | `git diff` confirms the class-level `@PreAuthorize("hasRole('ADMIN')")` (line 28) is byte-for-byte unchanged, and `"ADMIN".equals(roleName)` (line 367) remains present in `updateRbac`. Casos 3, 5, 8 prove `createUser`/`updateUser`/`updateRbac` continue to work exactly as before for tenant roles (`ADVOGADO`, `TECNICO`, `ASSISTENTE` respectively). The pre-existing Phase 117 suite (`AdminControllerLimiteUtilizadoresTest`, 9 cases) was re-run in isolation and as part of the full suite — still 9/9 green, confirming the user-limit enforcement is unaffected by these changes. |
| **T-119-SC** (Tampering — cadeia de fornecimento) | **CONFIRMADO (n/a)** | `git diff --name-only -- backend/pom.xml backend/src/main/java/com/lexcv/config/UserPrincipal.java backend/src/main/java/com/lexcv/dtos/RbacResponse.java backend/src/main/java/com/lexcv/services/` returns zero lines across both commits of this plan. Zero new dependencies; the Package Legitimacy Gate does not apply. |

## Deviations from Plan

None - plan executed exactly as written. Task 2 required no code changes: the full unit-test suite, SpotBugs/FindSecBugs, and the packaging step all passed on the first run with no findings.

## Issues Encountered

- The user's global `rtk` shell hook intercepts/rewrites piped Bash commands and silently truncated the output of `mvn test 2>&1 | tail -N` / `... | tee file | tail -N` down to a fraction of the real log (117 lines instead of the full run), and separately caused a plain piped `grep -c` chain against `AdminController.java` to under-count matches to zero. Neither was a real code or test problem — resolved by (a) redirecting `mvn` output directly to a file with `>`/`2>&1` (no pipe) and reading/grepping that file afterward with the dedicated Read/Grep tools, and (b) using the dedicated Grep tool instead of piped Bash `grep` for all source-content verification, per this plan's `<known_environment_note>`. All verification numbers in this SUMMARY were produced via that approach and independently cross-checked (e.g. 111 tests before this plan + 8 new = 119 total, matching the suite's own aggregate line).
- The first full-suite `mvn test` Bash call was cut off by the default 2-minute tool timeout before completion; re-run with an explicit longer timeout and file redirection completed normally in ~15s (BUILD SUCCESS, 119/119).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 04 (`PlatformAdminController`, Wave 2, `depends_on: [119-02]`) is not blocked by this plan technically, but its own threat model (T-119-04) explicitly notes its `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` gate "só vale enquanto um `ADMIN` de escritorio nao conseguir atribuir-se o papel (T-119-01)" — that precondition is now satisfied. Success Criterion 4 of Phase 119 ("nenhum caminho de `/api/v1/admin/**` permite a um `ADMIN` de escritorio obter, promover ou observar o papel de plataforma") holds structurally as of this plan.
- Full backend test suite: 119/119 tests green (111 pre-existing + 8 new from this plan), 0 regressions, `BUILD SUCCESS`. `mvn spotbugs:check`: 0 findings, `BUILD SUCCESS`. `mvn -DskipTests package`: exit 0, artifact produced.
- `backend/pom.xml`, `UserPrincipal.java`, `RbacResponse.java`, and everything under `services/` confirmed untouched (`git diff --name-only` empty for all) — this plan's diff is exactly the two files declared in its `files_modified` frontmatter.

---
*Phase: 119-backend-papel-de-administrador-de-plataforma-e-provisionamen*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/controllers/AdminController.java`
- FOUND: `backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java`
- FOUND: `.planning/phases/LEXCV-119-backend-papel-de-administrador-de-plataforma-e-provisionamen/119-03-SUMMARY.md`
- FOUND commit: `a12cc39`
- FOUND commit: `253e4ef`
