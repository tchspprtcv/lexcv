---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
plan: 03
subsystem: api
tags: [notifications, spring-boot, java, controller-wiring, pareceres]

# Dependency graph
requires:
  - phase: 87-01 (same phase, plan 01)
    provides: "NotificacaoService.notificarParecerAtribuido(tenantId, solicitacaoId, advogadoId, linkUrl, atorId) — actor-excluding wrapper for PARECER_ATRIBUIDO"
provides:
  - "ParecerController wired to NotificacaoService — advogado atribuído a um parecer (na criação ou reatribuição) é notificado, ADMIN incluído no fan-out, ator sempre excluído"
affects: [87-VERIFICATION, v2.10-MILESTONE-AUDIT]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Controller trigger call sites reuse the principal already extracted for the pre-existing AuditLog write (no duplicate SecurityContextHolder extraction) — same pattern documented in 87-PATTERNS.md Shared Pattern #4"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ParecerController.java

key-decisions:
  - "Notification call placed after the AuditLog save and before the return in both methods, per plan's exact splice-point instruction — keeps the audit trail and the notification trigger visually adjacent"
  - "createSolicitacao guards the call with saved.getAdvogadoId() != null (mirrors the pre-existing advogado-at-creation branch at lines 143-151); atribuirAdvogado has no such guard since advogadoId is already validated non-null earlier in that method"
  - "atribuirAdvogado intentionally does not read the previous advogadoId before it is overwritten — preserves the Out-of-Scope decision that only the new advogado is notified on reassignment"

patterns-established: []

requirements-completed: [NOTF-19]

# Metrics
duration: 8min
completed: 2026-07-09
---

# Phase 87 Plan 03: ParecerController Notification Triggers Summary

**Wired the pre-built `notificarParecerAtribuido` wrapper into both parecer-assignment call sites (`createSolicitacao` and `atribuirAdvogado`), closing the NOTF-19 gap deferred since v2.6.**

## Performance

- **Duration:** 8 min
- **Started:** 2026-07-09T06:31:25-01:00 (after worktree base sync)
- **Completed:** 2026-07-09T06:38:22-01:00
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- `ParecerController` now injects `NotificacaoService` and fires `PARECER_ATRIBUIDO` at both points where an advogado becomes responsible for a parecer solicitation: creation-time assignment (`createSolicitacao`, when `advogadoId` is supplied in the request body) and explicit reassignment (`atribuirAdvogado`, `PUT .../{id}/atribuir`).
- Both call sites reuse the `principal` already extracted for the existing `AuditLog` write — no duplicate `SecurityContextHolder` extraction — and pass `principal.getUserId()` as `atorId`, so the acting user is excluded from both the primary notification and the ADMIN fan-out (per `notificarParecerAtribuido`'s own actor-exclusion contract from Plan 87-01).
- `atribuirAdvogado` was left deliberately unchanged in its read pattern — it still never reads the previous `advogadoId` before overwriting it, so a reassignment notifies only the new advogado, never the one being replaced (Out of Scope decision, REQUIREMENTS.md).

## Task Commits

Each task was committed atomically:

1. **Task 1: Injetar NotificacaoService + gatilho na criação (createSolicitacao)** - `de5ae25` (feat)
2. **Task 2: Gatilho na reatribuição (atribuirAdvogado)** - `0242b42` (feat)

**Plan metadata:** (this commit — SUMMARY.md)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/ParecerController.java` — added `NotificacaoService` import + constructor-injected field; added the guarded `notificarParecerAtribuido(...)` call in `createSolicitacao` (after save + AuditLog, guarded by `saved.getAdvogadoId() != null`); added the unguarded `notificarParecerAtribuido(...)` call in `atribuirAdvogado` (after save + AuditLog, before the `ResponseEntity.ok(saved)` return).

## Decisions Made
- Notification calls placed immediately after the pre-existing `AuditLog` write and before the method's `return`, exactly matching the plan's specified splice points — no alternative ordering considered, since both audit and notification logically belong right after the state-changing `save()`.
- No new tenant/role validation was added at either call site — `advogadoId` is already validated (tenant membership + `ADVOGADO` role via `validateAdvogado(...)`) upstream in both methods before the notification call is reached, matching threat register item T-87-03-01.

## Deviations from Plan

None — both tasks' `<action>` steps were followed verbatim: import placement, field placement, and both splice points match the plan's `<interfaces>` and per-task instructions exactly. `atribuirAdvogado`'s `mvn -f backend/pom.xml -q spotbugs:check` acceptance criterion could not be satisfied as literally worded due to a pre-existing environment limitation (see Issues Encountered below) — this is not a code deviation, since no source change was made to work around it.

## Issues Encountered

**SpotBugs cannot run in this environment (pre-existing, unrelated to this plan's diff).** `mvn -f backend/pom.xml -q spotbugs:check` (Task 2's acceptance criterion) crashes with `NoClassesFoundToAnalyzeException` — the plugin's bundled ASM class reader throws `IllegalArgumentException: Unsupported class file major version 67` on every class it tries to load, including framework classes never touched by this plan (`Pageable`, `BCryptPasswordEncoder`, `CrudRepository`, `JwtParser`, `S3Presigner$Builder`, `BindingResult`). Root cause: `pom.xml` targets `java.version=23` (class file major version 67), and `spotbugs-maven-plugin` 4.8.3.1 predates full Java 23 bytecode support. This is a project-wide tooling/JDK version mismatch, not something introduced by this plan's 2-line diff — it fails identically regardless of which source file is analyzed. Logged to `deferred-items.md` (out of scope per SCOPE BOUNDARY; fixing it requires a project-wide SpotBugs version bump or JDK toolchain pin, outside this plan's `<files>` scope). Verified correctness instead via `mvn -f backend/pom.xml -q -DskipTests compile` (passed, both tasks) and targeted `grep` confirming both call sites, the injected field, and the absence of any new previous-`advogadoId` read.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness
- All 4 of Phase 87's NOTF wrappers built in Plan 87-01 are now consumed: Plan 87-02 (concurrently executed in a separate worktree, `ResourceController.java` — FASE_ENTRADA/DOCUMENTO_NOVO/PROCESSO_ATRIBUIDO) and this plan (PARECER_ATRIBUIDO). No further backend wiring work remains for the notification triggers themselves.
- Live HTTP round-trip verification (creating a solicitação with `advogadoId` set, and calling `PUT /pareceres/solicitacoes/{id}/atribuir`, then confirming the resulting `Notificacao` rows) is deferred to human UAT per the plan's own `<verification>` section — credential-lockout constraint already registered in STATE.md for this milestone.
- The SpotBugs/Java-23 tooling gap (see Issues Encountered) is a candidate for a future milestone's housekeeping — it currently blocks the SAST acceptance criterion for every plan in this phase, not just this one.

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
- FOUND: `.planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/deferred-items.md`
- FOUND commit: `de5ae25` (feat, Task 1)
- FOUND commit: `0242b42` (feat, Task 2)

---
*Phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer*
*Completed: 2026-07-09*
