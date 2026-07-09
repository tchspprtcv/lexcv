---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
plan: 02
subsystem: api
tags: [notifications, spring-boot, rest-api, multi-tenant, java, rbac]

# Dependency graph
requires:
  - phase: 87-01
    provides: "NotificacaoService wrapper methods (notificarFaseEntrada, notificarDocumentoNovo, notificarProcessoAtribuido) — single-call composition of criar()+notificarAdmins() with correct null-guards and actor exclusion"
provides:
  - "FASE_ENTRADA trigger wired into ResourceController.createProcessoFase (notifies responsavel + ADMIN, linkUrl with ?tab=fases)"
  - "DOCUMENTO_NOVO trigger wired into ResourceController.uploadDocumento, fired only for genuinely new uploads (replaceId == null), with processo > cliente precedence and actor (uploader) exclusion"
  - "PROCESSO_ATRIBUIDO trigger wired into ResourceController.createProcesso (initial assignment) and into the new PUT /processos/{id}/atribuir endpoint (reassignment)"
  - "New PUT /processos/{id}/atribuir endpoint, gated by processos:manage, validating both processo tenant ownership (404) and new responsavelId tenant ownership (400, verbatim copy of createProcesso's check)"
affects: [87-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Controller trigger = thin call to a Plan-87-01 NotificacaoService.notificar* wrapper immediately after the existing repository save(), never a direct notificacaoRepository write"
    - "New write endpoints that mutate a tenant-owned relationship (responsavelId) copy the existing createProcesso tenant-validation block verbatim rather than re-deriving it"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "DOCUMENTO_NOVO fires only when replaceId == null — a version replacement of an existing document is not a 'novo documento' per NOTF-16's wording"
  - "Document upload notification precedence is if/else if (processo wins over cliente) — a document linked to both never double-notifies the cliente team"
  - "New reassignment endpoint gated by processos:manage (not processos:edit), per CONTEXT.md — reassignment is a management action distinct from general field edits on /processos"
  - "atribuirResponsavel requires a non-blank responsavelId (400 otherwise) — no 'unassign' path exists in this phase"
  - "No notification sent to the previous responsavel on reassignment — out of scope per REQUIREMENTS.md"
  - "FASE_ENTRADA and PROCESSO_ATRIBUIDO do not exclude the acting user (per CONTEXT.md); only DOCUMENTO_NOVO does in this plan's scope"

patterns-established:
  - "Trigger insertion point = immediately after the pre-existing repository .save(...) call, before the method's return — diff stays purely additive, no existing validation/storage logic touched"

requirements-completed: [NOTF-15, NOTF-16, NOTF-17, NOTF-18]

# Metrics
duration: 9min
completed: 2026-07-09
---

# Phase 87 Plan 02: ResourceController Notification Triggers + Reassignment Endpoint Summary

**3 notification triggers (FASE_ENTRADA, DOCUMENTO_NOVO, PROCESSO_ATRIBUIDO) wired into ResourceController's existing save points, plus a new `processos:manage`-gated `PUT /processos/{id}/atribuir` endpoint that both reassigns and notifies.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-07-09T06:31:25-01:00 (after base commit `cbc2507`)
- **Completed:** 2026-07-09T06:40:49-01:00
- **Tasks:** 3 completed
- **Files modified:** 1

## Accomplishments
- `ResourceController` now injects `NotificacaoService` and fires 3 of the 4 Phase 87 notification categories at their real trigger points, without touching any pre-existing validation/storage logic.
- `createProcessoFase` notifies the processo's `responsavelId` (if any) + ADMIN whenever a fase is added, with a `?tab=fases` deep-link (frontend wiring for the link is explicitly Plan 87-04's job, not this plan's).
- `uploadDocumento` notifies the correct audience for a genuinely new upload (`replaceId == null`): the processo's responsavel when linked to a processo (precedence over cliente), or the cliente's advogado/administrativo team (tenant-scoped junction tables) when linked only to a cliente — always excluding the uploader.
- `createProcesso` notifies the initial `responsavelId` (if set) on creation; a brand-new `PUT /processos/{id}/atribuir` endpoint (mirroring `ParecerController.atribuirAdvogado`'s structural shape) lets a manager reassign a processo's responsavel later, re-validating tenant ownership on both the processo and the new responsavel before saving and notifying.

## Task Commits

Each task was committed atomically:

1. **Task 1: Injetar NotificacaoService + gatilho FASE_ENTRADA em createProcessoFase (NOTF-15)** - `c88b012` (feat)
2. **Task 2: Gatilho DOCUMENTO_NOVO em uploadDocumento com branching processo/cliente + exclusão de ator (NOTF-16)** - `dfcfb2e` (feat)
   - `51d3bd8` (docs) — logged a pre-existing, unrelated SpotBugs/JDK23 tooling incompatibility discovered while verifying this task (see Deviations)
3. **Task 3: Gatilho PROCESSO_ATRIBUIDO em createProcesso + novo endpoint PUT /processos/{id}/atribuir (NOTF-17, NOTF-18)** - `9e128bb` (feat)

**Plan metadata:** this commit (docs: complete plan)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - Injected `NotificacaoService`; added 3 notification trigger call sites (`createProcessoFase`, `uploadDocumento`, `createProcesso`); added new `atribuirResponsavel` endpoint (`PUT /processos/{id}/atribuir`).
- `.planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/deferred-items.md` - New file logging the pre-existing SpotBugs/JDK23 tooling gap (not a code change).

## Decisions Made
See `key-decisions` in frontmatter. All decisions were explicitly locked by the plan/CONTEXT.md — no discretionary calls were needed beyond exact copy/message wording, which followed the plan's literal action steps.

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written for all 3 tasks. Every signature, splice point, and message string was followed verbatim from the plan's `<action>` blocks.

### Out-of-Scope Discovery (logged, not fixed)

**1. [Scope Boundary] SpotBugs/FindSecBugs cannot run in this environment**
- **Found during:** Task 2 verification (`mvn -f backend/pom.xml -q spotbugs:check`)
- **Issue:** `spotbugs-maven-plugin:4.8.3.1` fails with `NoClassesFoundToAnalyzeException` — its ASM class reader cannot parse Java 23 bytecode ("class file major version 67"), failing even on framework classpath entries (`BCryptPasswordEncoder`, `CrudRepository`, `JwtParser`, AWS SDK, `BindingResult`) before it can analyze any application code. This is a pre-existing plugin/JDK-version incompatibility unrelated to any code in this plan — it would fail identically for any change to any file in the project.
- **Action:** Not auto-fixed (upgrading the SpotBugs plugin is an unrelated build-config/tooling change, out of scope for a controller-notification-wiring task). Logged to `deferred-items.md` in the phase directory per the Scope Boundary rule.
- **Substitute verification used:** `mvn -f backend/pom.xml -q -DskipTests compile` (passed after every task) plus targeted `grep` against each task's stated acceptance criteria, and manual review against the plan's `<threat_model>` (T-87-02-01 through T-87-02-05, all mitigations present: `processos:manage` gate, dual tenant-ownership checks, tenant-scoped team lookups, no cross-tenant data in messages, actor exclusion on the document trigger).

---

**Total deviations:** 0 auto-fixed; 1 out-of-scope discovery logged (pre-existing environment/tooling gap, not introduced by this plan).
**Impact on plan:** None on functional correctness — all acceptance criteria were independently verified via compile + grep + manual threat-model review.

## Issues Encountered
None beyond the SpotBugs environment gap documented above.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- All 3 wired triggers + the new reassignment endpoint are ready for Plan 87-04's frontend work: the `Reatribuir` Dialog+AlertDialog control can call `PUT /processos/{id}/atribuir` with `{ responsavelId }` and expect `ResponseEntity.ok(<raw Processo>)` on success, or a `{ message }` 400/404 body on failure (exact error strings match `createProcesso`'s existing ones, so any frontend error-display logic already written for that endpoint works unchanged).
- The `?tab=fases` linkUrl on FASE_ENTRADA notifications is correct backend copy today but is inert until Plan 87-04/89 wires `useSearchParams()`-driven tab initialization on the ficha do processo — not a blocker for this plan, called out explicitly so it isn't mistaken for a bug later.
- No blockers. `mvn -f backend/pom.xml -q -DskipTests compile` passes with all 3 tasks' changes combined.

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
- FOUND: `.planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/deferred-items.md`
- FOUND commit: `c88b012` (feat, Task 1)
- FOUND commit: `dfcfb2e` (feat, Task 2)
- FOUND commit: `51d3bd8` (docs, deferred-items)
- FOUND commit: `9e128bb` (feat, Task 3)

---
*Phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer*
*Completed: 2026-07-09*
