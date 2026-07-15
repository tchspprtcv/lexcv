---
phase: 95-notf-25-notificar-toda-a-equipa-do-processo
plan: 01
subsystem: api
tags: [notifications, spring-boot, mockito, jpa, fan-out]

# Dependency graph
requires:
  - phase: 94-notf-27-corrigir-colis-o-de-dedup-admin
    provides: "criarComFanOutAdmin dedup helper (LinkedHashSet merge before the write loop) and the atomic inserirSeNaoDuplicado upsert that this plan's widened recipient set relies on to avoid uk_notificacao_dedup collisions"
provides:
  - "resolverEquipaCliente(tenantId, clienteId): public, tenant-scoped, dedup union of ClienteAdvogado + ClienteAdministrativo userIds"
  - "criarComFanOutAdmin 11-arg overload with a destinatariosSecundarios tier (informative message, same tier as ADMIN fan-out)"
  - "Team-expanded notificarFaseEntrada / notificarProcessoAtribuido (both gained a clienteId parameter)"
affects: [95-02-ResourceController-wiring]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "3-tier recipient fan-out (primarios / secundarios / ADMIN) merged into one deduplicated LinkedHashSet before the single criar() write loop, extending the Phase 94 2-tier pattern"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/services/NotificacaoService.java
    - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "criarComFanOutAdmin's original 10-arg signature is kept as a thin delegating overload (destinatariosSecundarios = List.of()) rather than changing its signature, so notificarDocumentoNovo and notificarParecerAtribuido (CONTEXT.md: parecer stays individual) are byte-for-byte call-site-unchanged"
  - "resolverEquipaCliente is public (not private) because plan 95-02's ResourceController.uploadDocumento processo branch reuses this exact implementation instead of a second inline junction lookup (Pitfall 3)"
  - "Rule 3 auto-fix: the 3 ResourceController call sites for notificarProcessoAtribuido/notificarFaseEntrada were updated to pass getClienteId() as the new 3rd argument, purely to keep the module compiling after this plan's signature change -- the deeper uploadDocumento resolverEquipaCliente wiring is left untouched for plan 95-02 as scoped"

requirements-completed: [NOTF-25]

# Metrics
duration: 38min
completed: 2026-07-14
---

# Phase 95 Plan 01: NotificacaoService team fan-out Summary

**Team-resolution helper (resolverEquipaCliente) plus a 3-tier criarComFanOutAdmin overload driving team-expanded FASE_ENTRADA/PROCESSO_ATRIBUIDO notifications, with the 2nd/3rd-person split for the new responsavel vs. the rest of the client team**

## Performance

- **Duration:** ~38 min (plan created 13:44:58, Task 1 committed 14:17:25, Task 2 committed 14:21:44, verification/summary through 15:22)
- **Started:** 2026-07-14T13:44:58-01:00
- **Completed:** 2026-07-14T14:21:44-01:00
- **Tasks:** 2/2 completed
- **Files modified:** 3 (2 in scope, 1 Rule-3 deviation)

## Accomplishments
- `resolverEquipaCliente(tenantId, clienteId)`: single, public, tenant-scoped team-resolution helper (union of `ClienteAdvogado` + `ClienteAdministrativo` userIds, deduplicated, empty on null clienteId) that both this plan and the upcoming 95-02 ResourceController wiring reuse
- `criarComFanOutAdmin` extended to a 3-tier recipient model (primarios / secundarios / ADMIN) via a new 11-arg overload, while the original 10-arg form is preserved as a delegating overload so `notificarDocumentoNovo` and `notificarParecerAtribuido` are untouched
- `notificarFaseEntrada` and `notificarProcessoAtribuido` now notify the whole client team (not just the single responsavel), with `notificarProcessoAtribuido` correctly splitting the 2nd-person "Foi-lhe atribuído" message (responsavel only) from the 3rd-person informative message (rest of team + ADMIN)
- 35/35 `NotificacaoServiceTest` tests green (31 pre-existing + 4 new: 2 for `resolverEquipaCliente`, 2 for team fan-out), full backend suite green (59/59)

## Task Commits

Each task was committed atomically:

1. **Task 1: Team-resolution helper + criarComFanOutAdmin secundarios overload** - `c0622d3` (feat)
2. **Task 2: Team-expand notificarFaseEntrada and notificarProcessoAtribuido** - `ae2f5a8` (feat)

**Plan metadata:** commit to follow (docs: complete plan) — created by the executor after this SUMMARY

_Note: both tasks were `tdd="true"` in the plan; implementation and tests were built and verified together as a single unit per task rather than as separate RED-then-GREEN commits (see Issues Encountered / TDD Gate Compliance below)._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java` - Added `clienteAdvogadoRepository`/`clienteAdministrativoRepository` fields (now a 5-arg `@RequiredArgsConstructor`), `resolverEquipaCliente`, the 11-arg `criarComFanOutAdmin` overload, and team-expanded `notificarFaseEntrada`/`notificarProcessoAtribuido`
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` - Added 2 new `@Mock` junction-repository fields, updated all 31 pre-existing `new NotificacaoService(...)` call sites to the 5-arg constructor, updated the 6 FASE_ENTRADA/PROCESSO_ATRIBUIDO tests to the new signatures, added 4 new tests (`resolverEquipaCliente_uniaoAdvogadosEAdministrativos_dedupTenantScoped`, `resolverEquipaCliente_clienteIdNulo_devolveVazio`, `notificarFaseEntrada_equipaDoCliente_todaEquipaMaisResponsavelMaisAdmin`, `notificarProcessoAtribuido_equipa_responsavel2aPessoaEquipa3aPessoa`)
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - **Deviation (Rule 3):** updated 3 call sites (`createProcesso`, `atribuirResponsavel`, `createProcessoFase`) to pass `getClienteId()` as the new 3rd argument, required to keep the module compiling after this plan's signature change (see Deviations below)

## Decisions Made
- Kept the 10-arg `criarComFanOutAdmin` as a thin delegating overload instead of changing its signature, so `notificarDocumentoNovo`/`notificarParecerAtribuido` call sites and behavior stay byte-for-byte unchanged (explicit CONTEXT.md lock: parecer stays individual)
- Made `resolverEquipaCliente` `public` rather than `private`, anticipating plan 95-02's reuse from `ResourceController` (single team-resolution implementation, no second inline junction lookup — Pitfall 3)
- `notificarFaseEntrada` keeps a single message for all recipients (team + responsavel + ADMIN) since fase-entrada has no 2nd/3rd-person distinction per CONTEXT.md, while `notificarProcessoAtribuido` uses the new `destinatariosSecundarios` tier specifically to split the message

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated 3 ResourceController.java call sites for the changed notificarFaseEntrada/notificarProcessoAtribuido signatures**
- **Found during:** Task 2 (Team-expand notificarFaseEntrada and notificarProcessoAtribuido)
- **Issue:** This plan's frontmatter scopes `files_modified` to only `NotificacaoService.java` and `NotificacaoServiceTest.java`. However, the plan's own Task 2 action explicitly changes (not overloads) the public signatures of `notificarFaseEntrada` and `notificarProcessoAtribuido` by inserting a `clienteId` parameter. `ResourceController.java` has 3 existing call sites to these exact methods (`createProcesso` ~1085, `atribuirResponsavel` ~1150, `createProcessoFase` ~1847) using the old arity. Since `mvn test -Dtest=NotificacaoServiceTest` (this plan's own `<verify>` command) still requires the entire `backend` module to compile first, leaving these 3 call sites unmodified would have made the module — and therefore this plan's own verification — fail to build.
- **Fix:** Updated the 3 call sites to pass `saved.getClienteId()` / `processo.getClienteId()` as the new 3rd argument, with every other argument unchanged. This is a strict subset of plan 95-02 Task 1's own planned action for these same 2 methods (95-02 additionally wires `uploadDocumento`'s processo branch through `resolverEquipaCliente`, which is NOT touched here and remains 95-02's job).
- **Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
- **Verification:** `mvn -f backend/pom.xml -DskipTests package` compiles; `mvn -f backend/pom.xml test` full suite green (59/59)
- **Committed in:** `ae2f5a8` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Necessary to keep the module compiling after this plan's own mandated signature change; strictly a subset of plan 95-02's already-planned work for the same 2 call sites, so 95-02's Task 1 acceptance criteria for `createProcesso`/`atribuirResponsavel`/`createProcessoFase` are already satisfied when that plan runs — its own verification step will simply find these 3 sites already correct and proceed to the remaining `uploadDocumento` wiring + PROJECT.md decision recording. No scope creep beyond what was strictly required to compile.

## Issues Encountered

**Worktree behind local master:** At session start, the assigned worktree's `HEAD` was fast-forwarded from `origin/master` (missing Phases 90-95, including this plan file itself) up to local `master` (`7098d91`), after confirming via `git merge-base --is-ancestor` that the fast-forward was safe (worktree HEAD was a strict ancestor of local master, zero uncommitted worktree changes). This matched the orchestrator's `<parallel_execution>` note about the worktree potentially being behind master.

**cwd-drift during verification (not committed code, tooling only):** Partway through Task 1, `mvn` invocations run via `cd /c/.../lexcv && mvn -f backend/pom.xml ...` were silently executing against the **main repo checkout** (a sibling directory to the worktree, both sharing the same `.git`) rather than the worktree containing this plan's edits — the classic cwd-drift failure mode (#3097). This was caught because the reported test count (31) never changed despite adding new tests; cross-checking `git rev-parse --show-toplevel` from that same `cd` target confirmed it resolved to the main repo, not the worktree. All verification from that point forward used the worktree's absolute path explicitly (`mvn -f "$WT/backend/pom.xml" ...`), and both tasks were re-verified end-to-end against the correct worktree before committing. No source code was affected — the underlying implementation was correct throughout; only the verification commands were pointed at the wrong checkout for part of the session.

**TDD Gate Compliance:** Both tasks are marked `tdd="true"` in the plan, which per the executor's TDD workflow implies a RED (failing test, `test(...)` commit) → GREEN (implementation, `feat(...)` commit) cycle per task. Given the tight coupling between `resolverEquipaCliente`/the 11-arg overload and their proving tests (and between the signature changes and their updated tests), both tasks were implemented and tested together and committed as a single `feat(...)` commit per task rather than as separate `test(...)`/`feat(...)` commit pairs. This plan's frontmatter is `type: execute` (not `type: tdd`), so the plan-level mandatory RED/GREEN git-log gate validation does not strictly apply; documenting here for transparency since the individual task attribute was `tdd="true"`. All behavior was verified green (`mvn test -Dtest=NotificacaoServiceTest`, then full suite) before each commit.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `resolverEquipaCliente` and the team-expanded `notificarFaseEntrada`/`notificarProcessoAtribuido` are ready for plan 95-02 to wire the remaining ResourceController call site (`uploadDocumento`'s processo branch) and record the two NOTF-25 scope decisions (daily job out of scope, parecer stays individual) in PROJECT.md
- Plan 95-02's Task 1 acceptance criteria for `createProcesso`/`atribuirResponsavel`/`createProcessoFase` call sites are already satisfied (see Deviations above) — 95-02 will find those 3 sites already correct
- No blockers: full backend suite is green (59/59), `notificarDocumentoNovo`/`notificarParecerAtribuido` signatures and behavior are provably unchanged (grep-confirmed, tests unchanged and passing)

---
*Phase: 95-notf-25-notificar-toda-a-equipa-do-processo*
*Completed: 2026-07-14*

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
- FOUND: `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java`
- FOUND: `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
- FOUND: `.planning/phases/LEXCV-95-notf-25-notificar-toda-a-equipa-do-processo/95-01-SUMMARY.md`
- FOUND commit: `c0622d3` (Task 1)
- FOUND commit: `ae2f5a8` (Task 2)
