---
phase: 95-notf-25-notificar-toda-a-equipa-do-processo
plan: 02
subsystem: api
tags: [notifications, spring-boot, resource-controller, fan-out]

# Dependency graph
requires:
  - phase: 95-01
    provides: "resolverEquipaCliente(tenantId, clienteId) team-resolution helper, criarComFanOutAdmin 11-arg secundarios overload, team-expanded notificarFaseEntrada/notificarProcessoAtribuido signatures (clienteId parameter)"
provides:
  - "uploadDocumento's processo branch wired to resolverEquipaCliente, so DOCUMENTO_NOVO reaches the whole client team (not just responsavel) for both the processo and cliente branches"
  - "Verified/confirmed createProcesso, atribuirResponsavel, createProcessoFase call sites correctly pass getClienteId() to the team-expanded NotificacaoService API (already wired by 95-01's Rule 3 deviation)"
  - "Two NOTF-25 scope decisions durably recorded in PROJECT.md Key Decisions (daily job stays responsavelId-only; PARECER_ATRIBUIDO stays individual)"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ResourceController call sites reuse NotificacaoService.resolverEquipaCliente instead of inlining a second junction-repository lookup — single team-resolution implementation across both uploadDocumento branches"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - .planning/PROJECT.md

key-decisions:
  - "uploadDocumento processo branch: resolverEquipaCliente(tenantId, proc.getClienteId()) result plus responsavelId (if present) merged into one ArrayList<UUID> dests, mirroring the pre-existing cliente branch pattern exactly"
  - "cliente branch (~2780-2789) left byte-for-byte unchanged, as locked by 95-CONTEXT.md"
  - "NOTF-25 scope decisions (daily job out of scope; parecer stays individual) recorded as two new rows in PROJECT.md's Key Decisions table, satisfying success criteria 4 and 5"

requirements-completed: [NOTF-25]

# Metrics
duration: 5min
completed: 2026-07-14
---

# Phase 95 Plan 02: ResourceController team fan-out wiring Summary

**Wired uploadDocumento's processo branch through NotificacaoService.resolverEquipaCliente (closing the last of 4 ResourceController call sites for NOTF-25) and recorded the two NOTF-25 scope decisions (daily job, parecer) in PROJECT.md**

## Performance

- **Duration:** ~5 min (plan start 15:28:31Z, Task 1 committed 14:30:48-01:00, Task 2 committed 14:32:10-01:00)
- **Started:** 2026-07-14T15:28:31Z
- **Completed:** 2026-07-14T15:33:04Z
- **Tasks:** 2/2 completed
- **Files modified:** 2

## Accomplishments
- Confirmed (via grep + line-level read) that plan 95-01's Rule 3 compile-fix deviation had already correctly wired 3 of the 4 required call sites (`createProcesso`, `atribuirResponsavel`, `createProcessoFase`) to pass `getClienteId()` to the team-expanded `notificarFaseEntrada`/`notificarProcessoAtribuido` signatures — no re-edit needed, verified argument order and values match the plan's `<interfaces>` spec exactly
- Wired the 4th and final call site, `uploadDocumento`'s processo branch: it now resolves the full client team via `notificacaoService.resolverEquipaCliente(tenantId, proc.getClienteId())` and adds `responsavelId` when present, instead of notifying only the responsavel — matching the pre-existing cliente-branch pattern and reusing the single shared team-resolution helper rather than inlining a second junction lookup
- Recorded both NOTF-25 scope decisions (`AlertasDiariosJob` stays responsavelId-only; `PARECER_ATRIBUIDO` stays individual) as new rows in PROJECT.md's Key Decisions table, closing NOTF-25 success criteria 4 and 5
- Full backend suite green: 59/59 tests pass, `mvn -DskipTests package` and `mvn test` both BUILD SUCCESS

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire the four ResourceController call sites for team fan-out** - `77412e6` (feat)
2. **Task 2: Record the NOTF-25 scope decisions in PROJECT.md** - `e4198c5` (docs)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - `uploadDocumento`'s processo branch now builds `dests` from `resolverEquipaCliente(tenantId, proc.getClienteId())` plus `responsavelId` when present, instead of `responsavelId`-only; the 3 pre-wired call sites (createProcesso/atribuirResponsavel/createProcessoFase) and the cliente branch were verified unchanged/correct
- `.planning/PROJECT.md` - Two new rows appended to the Key Decisions table recording the NOTF-25 daily-job-out-of-scope and parecer-stays-individual decisions

## Decisions Made
- Mirrored the exact pattern already used by the cliente branch (`ArrayList<UUID>` accumulation, null-guard on `proc`, conditional `responsavelId` add) for the processo branch, rather than introducing a different accumulation style
- No changes made to the 3 already-correct call sites — re-verified against this plan's acceptance criteria instead of blindly re-applying edits, per the orchestrator's `<known_partial_overlap>` guidance

## Deviations from Plan

None - plan executed exactly as written. The only notable pre-condition was that 3 of 4 call sites (createProcesso, atribuirResponsavel, createProcessoFase) were already correctly wired by plan 95-01's own Rule 3 compile-fix deviation; this plan's Task 1 verified their correctness against its acceptance criteria rather than re-editing them, and completed the one remaining site (uploadDocumento's processo branch) plus the resolverEquipaCliente reuse as scoped.

## Issues Encountered

**Worktree behind local master (environmental, not code):** At session start, this worktree's `HEAD` (`eed883e`, matching `origin/master`) was missing Phases 90-95, including both this plan file and 95-01's work. Verified via `git merge-base --is-ancestor HEAD master` that a fast-forward was safe (worktree HEAD was a strict ancestor of local `master`, zero uncommitted worktree changes present), then fast-forwarded (`git merge --ff-only master`) to `13dbf33` before starting any work. This matches the orchestrator's `<parallel_execution>` note.

**cwd-drift / worktree-path verification (tooling only, not code):** Following the explicit warning from 95-01's executor about `mvn` silently running against the main repo checkout via a shared `.git` directory, all `mvn` invocations in this session used an explicit `-f "$WT/backend/pom.xml"` path derived from `git rev-parse --show-toplevel` run fresh in the worktree, and `git rev-parse --show-toplevel` was cross-checked before each build/test run to confirm it resolved inside the worktree, not the main repo. No drift was observed in this session.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- NOTF-25 delivery side complete: all 4 ResourceController call sites (createProcesso, atribuirResponsavel, createProcessoFase, uploadDocumento processo branch) now feed the full client team into notifications, closing success criteria 1 and 2 (delivery side)
- NOTF-25 scope decisions (criteria 4, 5) durably recorded in PROJECT.md
- Full backend test suite green (59/59); no regressions to `AlertasDiariosJob` or `ParecerController`'s `PARECER_ATRIBUIDO` trigger (both untouched, as required)
- Live/E2E verification (actual notification delivery to multiple team members via a running backend) remains blocked by the pre-existing `MINIO_ENDPOINT` environmental blocker noted throughout the v2.10/v2.11 milestones — not introduced by this plan; the fan-out logic itself is proven at the service layer by 95-01's tests and at the controller-compile/call-site level by this plan's grep-verified acceptance criteria

---
*Phase: 95-notf-25-notificar-toda-a-equipa-do-processo*
*Completed: 2026-07-14*

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
- FOUND: `.planning/PROJECT.md`
- FOUND: `.planning/phases/LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo/95-02-SUMMARY.md`
- FOUND commit: `77412e6` (Task 1)
- FOUND commit: `e4198c5` (Task 2)
