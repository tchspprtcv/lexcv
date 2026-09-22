---
phase: 128-auditoria-de-atribui-es-de-pap-is
plan: 07
subsystem: api
tags: [spring-security, preauthorize, pagination, audit-log, rbac, testcontainers]

# Dependency graph
requires:
  - phase: 128-01
    provides: "AuditLogRepository.buscarEventosRbac (tenant-scoped, paginated, RBAC-only native query)"
  - phase: 128-02
    provides: "AuditoriaRbacService.listar and AuditoriaRbacEntradaDto (resolved read shape)"
provides:
  - "GET /api/v1/admin/rbac/auditoria: paginated, tenant-scoped RBAC audit query behind hasAuthority('rbac:manage')"
  - "AuditoriaRbacController: a dedicated GET-only controller, class-level gate, no tenant/utilizador/papel parameter can widen the query beyond the caller's own tenant"
  - "AuditLogRepositoryIT: SQL-level two-tenant isolation and filter proof for buscarEventosRbac (compiled, not executed locally)"
affects: [128-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Real @PreAuthorize proxy (AuthorizationManagerBeforeMethodInterceptor + ProxyFactory) to prove hasAuthority gates behaviorally, not by annotation reflection -- same idiom as OfficeRolesControllerTest/AdminControllerRbacAutorizacaoTest"
    - "Bounds-checked page/size with the NotificacaoController.listar envelope (content/totalElements/totalPages/page/size), reused verbatim for a second paginated read endpoint"

key-files:
  created:
    - backend/src/main/java/com/lexcv/controllers/AuditoriaRbacController.java
    - backend/src/test/java/com/lexcv/controllers/AuditoriaRbacControllerTest.java
    - backend/src/test/java/com/lexcv/repositories/AuditLogRepositoryIT.java
  modified: []

key-decisions:
  - "Backend filter contract is papelId (UUID), not the UI-SPEC's proposed name-based 'papel' filter -- see 'UI-SPEC Reconciliation' below. This follows the Plan 02/01 interfaces (AuditoriaRbacService.listar(tenantId, utilizadorAlvoId, papelId, pageable) and AuditLogRepository.buscarEventosRbac(tenantId, String, String, Pageable)), which are id-stable and survive a role rename; a name-based filter would not."
  - "AuditLogRepositoryIT could not be executed locally (Docker npipe blocker, stated up front in phase_context) -- verified by mvn test-compile only, exactly as the plan instructed. This is not a deviation; it is the plan's own accepted limitation."

requirements-completed: [AUDT-03, AUDT-04]

# Metrics
duration: 35min
completed: 2026-09-22
---

# Phase 128 Plan 07: AuditoriaRbacController (RBAC audit query endpoint) Summary

**`GET /api/v1/admin/rbac/auditoria` — a new, GET-only, class-gated (`hasAuthority('rbac:manage')`) controller that pages through a tenant's own RBAC audit events, tenant always resolved from `UserPrincipal` never a request parameter, proven with a real `@PreAuthorize` proxy at the controller level and a Testcontainers SQL-level IT (compiled, execution deferred).**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-09-22T16:05:00Z (approx)
- **Completed:** 2026-09-22T16:39:46Z
- **Tasks:** 2
- **Files modified:** 3 (3 created, 0 modified)

## Accomplishments

- `AuditoriaRbacController` (`@RestController`, `@RequestMapping("/api/v1/admin/rbac/auditoria")`, class-level `@PreAuthorize("hasAuthority('rbac:manage')")`): single `GET ""` handler `listar(utilizadorAlvoId, papelId, page, size)`. Tenant resolved exclusively from `SecurityContextHolder` → `UserPrincipal.getTenantId()`; the handler signature carries no tenant parameter at all (verified by reflection over `Method.getParameters()` in the test). Bounds copied verbatim from `NotificacaoController.listar` (`page >= 0`, `1 <= size <= 100`, same 400 message). Response envelope is the same `Map.of("content", "totalElements", "totalPages", "page", "size")` shape as `NotificacaoController.listar`.
- `AuditoriaRbacControllerTest` (13 tests): real `@PreAuthorize` proxy proof (`rbac:manage` succeeds; `users:manage` alone and no authority at all both throw `AccessDeniedException` before the service is ever touched); tenant-from-principal isolation (`service.listar` called with the caller's tenant, never with a second tenant, `verify(..., never())`); filter pass-through (both null and both populated); pagination bounds (`page=-1`, `size=0`, `size=101` all 400, service not called); default `page=0,size=20` captured as `PageRequest.of(0, 20)`; response body has exactly the five contracted keys; a reflection scan confirming the class declares no `@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping`.
- `AuditLogRepositoryIT` (5 tests, Testcontainers, `@DataJpaTest` + `@AutoConfigureTestDatabase(replace = NONE)` idiom copied from `NotificacaoRepositoryIT`): proves against real PostgreSQL that `buscarEventosRbac` (a) isolates strictly by `tenant_id` even when a second tenant reuses the exact same user/role UUIDs, returning tenant A's 3 RBAC rows newest-first and excluding both tenant B's row and a `processo` event; (b) the target-user filter returns only that user's `atribuicao_papel` row; (c) the role-id filter returns both the role's own `papel_escritorio` row and its `atribuicao_papel` row (the `detalhe->>'papelId'` match); (d) tenant B with no filters sees exactly its own 1 row; (e) `size=2` over 3 rows yields `totalPages=2`. Not executed in this environment (Docker npipe blocker, stated in the plan up front) — verified with `mvn test-compile` only.

## Task Commits

Each task was committed atomically:

1. **Task 1: AuditoriaRbacController with gate, bounds and tenant-from-principal** - `6258abae` (feat)
2. **Task 2: SQL-level two-tenant isolation and filter IT for buscarEventosRbac** - `8f406210` (test)

_Note: Task 1 is TDD-flagged in the plan. The test file was written together with the implementation (both new, with the plan's own `<behavior>` bullets as the exhaustive spec) and iterated to green before the commit — no separate failing-test-first commit, following the same rationale already recorded in 128-02-SUMMARY.md for self-contained new units with no pre-existing partial implementation to drive incrementally. Every behavior bullet has a dedicated assertion, run and made to pass before committing._

## Files Created/Modified

- `backend/src/main/java/com/lexcv/controllers/AuditoriaRbacController.java` - the new GET-only, tenant-scoped, `rbac:manage`-gated RBAC audit query endpoint
- `backend/src/test/java/com/lexcv/controllers/AuditoriaRbacControllerTest.java` - 13 tests, one per behavior bullet, real `@PreAuthorize` proxy
- `backend/src/test/java/com/lexcv/repositories/AuditLogRepositoryIT.java` - 5 SQL-level isolation/filter tests against real PostgreSQL (Testcontainers), compiled but not executed locally

## Decisions Made

- Backend filter contract kept as `papelId` (UUID), per Plan 02/01's already-locked interfaces, rather than the UI-SPEC's proposed name-based `papel` filter — see "UI-SPEC Reconciliation" below.
- `AuditLogRepositoryIT` verified by `mvn test-compile` only, per the plan's explicit instruction (Docker npipe blocker, stated in `phase_context` before this plan started, not discovered mid-execution).

## UI-SPEC Reconciliation (Critical Invariant 5)

`128-UI-SPEC.md`'s "Data Contract Assumptions" section proposed a role filter by **name** (`papel`, exact match) and a recommended path `GET /admin/rbac/audit?page&size&utilizadorAlvoId&papel`. The actual backend, built against the already-locked Plan 01/02 interfaces, differs in two ways the frontend (Plan 08) must build against as reality, not against the UI-SPEC's assumption:

1. **Path:** `GET /api/v1/admin/rbac/auditoria` (not `/admin/rbac/audit`), as fixed by the plan's `<interfaces>` block and Plan 01's route-collision note (does not collide with `/api/v1/admin/rbac` or `/api/v1/admin/rbac/roles`).
2. **Role filter:** `papelId` (a `UUID`), not `papel` (a name string). This is deliberate, not an oversight — `AuditLogRepository.buscarEventosRbac`'s role filter matches by `TenantRole.id`, both directly (`papel_escritorio` rows) and via the `detalhe->>'papelId'` JSON key (`atribuicao_papel` rows), specifically so that a role query keeps returning correct results after the role is renamed. The UI-SPEC's own "Known limitation" note (§ Data Contract Assumptions, point 4) already anticipated that the role-filter dropdown sources its options from the office's *live* roles (`useOfficeRbac().papeis`) — those options already carry `id`, so Plan 08 can send `papelId` instead of a name with no loss of UI capability, and gains the rename-survival property the UI-SPEC's own limitation note wished for.

The response envelope (`content`/`totalElements`/`totalPages`/`page`/`size`) and the `AuditoriaRbacEntradaDto` field set (`id`, `timestamp`, `acao`, `categoria`, `autorNome`, `alvoId`, `alvoNome`, `papelId`, `papelNome`, `nomeAntigo`, `nomeNovo`, `permissoesAdicionadas`, `permissoesRemovidas`, `motivo`) already match the UI-SPEC's contract exactly (both were written against the same Plan 02 DTO, which predates the UI-SPEC's own drafting per its "Data Contract Assumptions" framing).

## Deviations from Plan

None beyond the two auto-fixed issues below, both caught by running the plan's own acceptance-criteria greps rather than assuming correctness.

### Auto-fixed Issues

**1. [Rule 1 - Bug] `principal.getTenantId()` literal missing from the file (grep gate)**
- **Found during:** Task 1, acceptance-criteria verification
- **Issue:** The handler was first written as `getPrincipal().getTenantId()`, which does not contain the literal substring `principal.getTenantId()` that the plan's `key_links` pattern (`principal\.getTenantId\(\)`) and acceptance criteria grep for — `getPrincipal().` breaks the match at the `.` boundary.
- **Fix:** Introduced a local `UserPrincipal principal = getPrincipal();` and called `principal.getTenantId()`, matching the literal the plan's own interface pattern requires while keeping the same tenant-from-principal behavior.
- **Files modified:** `backend/src/main/java/com/lexcv/controllers/AuditoriaRbacController.java`
- **Commit:** `6258abae` (fixed before this commit was made; no separate commit needed)

**2. [Rule 1 - Bug] Initial bash-tool grep invocations reported false negatives**
- **Found during:** Task 1, acceptance-criteria verification
- **Issue:** Running `grep -F "..."` through the Bash tool against strings containing parentheses/quotes produced empty results even though the Grep tool subsequently confirmed the same literals were present — a shell-quoting artifact of the Bash tool, not a real absence.
- **Fix:** Re-ran every acceptance-criteria check with the Grep tool (which handles the patterns correctly) instead of bash `grep`, per this plan's own "Tooling note" in `<success_criteria>`.
- **Files modified:** None (verification-only; no code was wrongly "fixed" in response to the false negative).
- **Commit:** N/A (no code change)

---

**Total deviations:** 2 auto-fixed (1 bug in the handler, 1 verification-tooling false negative)
**Impact on plan:** Both caught before either task's commit landed; neither commit ever contained the broken/unverified state. No scope creep.

## Issues Encountered

None beyond the two auto-fixed issues above.

## Verification Evidence

- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=AuditoriaRbacControllerTest,AuditLogImutabilidadeTest` — **Tests run: 19 (13 + 6), Failures: 0, Errors: 0, BUILD SUCCESS** (Task 1's own verify command).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test-compile` — exit 0, no output (Task 2's verify command; `AuditLogRepositoryIT` compiles).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test -Dtest=AuditoriaRbacControllerTest,AuditoriaRbacServiceTest,AuditLogImutabilidadeTest` (plan-level `<verification>`) — **Tests run: 31 (13 + 12 + 6), Failures: 0, Errors: 0, BUILD SUCCESS**.
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test` (full suite) — **Tests run: 413, Failures: 0, Errors: 0, BUILD SUCCESS**. Baseline stated in `phase_context` was 400; 413 = 400 + 13 new `AuditoriaRbacControllerTest` tests (`AuditLogRepositoryIT` does not run under `test`, only under `verify`/Failsafe, and was not executed here per the Docker npipe blocker).
- `JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml spotbugs:check` — **BugInstance size is 0, Error size is 0, BUILD SUCCESS**.
- Acceptance-criteria greps (Task 1, via the Grep tool after the Bash-tool false negative was caught): `@PreAuthorize("hasAuthority('rbac:manage')")` present; `/api/v1/admin/rbac/auditoria` present; `principal.getTenantId()` present; `PostMapping`/`PutMapping`/`PatchMapping`/`DeleteMapping` all absent.
- Acceptance-criteria checks (Task 2): `@Testcontainers` present in `AuditLogRepositoryIT`; `buscarEventosRbac` appears 11 times (≥5 required); file is 193 lines (≥80 required); `mvn test-compile` exits 0.
- Stub scan (`TODO`/`FIXME`/"coming soon"/"not available"/"placeholder", case-insensitive) over the three new files — no real matches (the only hits were the Portuguese word "método"/"metodo" containing the substring "todo", a false positive of the case-insensitive pattern, not a stub marker).
- Threat surface scan: no new surface beyond what `128-07-PLAN.md`'s own `<threat_model>` (T-128-32 through T-128-37) already covers — this plan implements exactly the endpoint that threat register describes, nothing additional.

## User Setup Required

None — no external service configuration required. `AuditLogRepositoryIT` still needs to run somewhere with Docker available (CI, or a developer machine with Docker Desktop/Engine running) before its 5 assertions are proven against real PostgreSQL; this is the plan's own stated, accepted limitation, not a new setup requirement.

## Next Phase Readiness

- `GET /api/v1/admin/rbac/auditoria` is live and ready for Plan 08 (frontend) to consume: response envelope and `AuditoriaRbacEntradaDto` field set already match `128-UI-SPEC.md`'s Data Contract Assumptions, except the role filter, which Plan 08 must implement as `papelId` (UUID) — see "UI-SPEC Reconciliation" above.
- `AuditLogRepositoryIT` is ready to run in any Docker-capable environment (CI or a developer machine with Docker running) to close out the SQL-level proof; no code changes are anticipated to be needed for it to pass, since it exercises the same `buscarEventosRbac` query already covered logically by the controller-level mocked tests.
- No blockers for Plan 08.

---
*Phase: 128-auditoria-de-atribui-es-de-pap-is*
*Completed: 2026-09-22*

## Self-Check: PASSED

All 3 created source files verified present on disk. All three task/summary commit hashes (`6258abae`, `8f406210`, `65b28178`) verified present in `git log --oneline --all`. No missing items.
