---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
plan: 01
subsystem: api

tags: [notifications, spring-boot, mockito, tdd, java, service-layer]

# Dependency graph
requires:
  - phase: 86-infraestrutura-de-notificacoes
    provides: "Notificacao entity, NotificacaoService.criar(...) (single write path), package-private notificarAdmins(7-arg) fan-out, NotificacaoRepository/UserRepository"
provides:
  - "notificarFaseEntrada(tenantId, processoId, responsavelId, numeroProcesso, nomeFase, linkUrl) — NOTF-15"
  - "notificarDocumentoNovo(tenantId, documentoId, destinatarios, nomeDocumento, linkUrl, atorId) — NOTF-16"
  - "notificarProcessoAtribuido(tenantId, processoId, responsavelId, numeroProcesso, linkUrl) — NOTF-18"
  - "notificarParecerAtribuido(tenantId, solicitacaoId, advogadoId, linkUrl, atorId) — NOTF-19"
  - "notificarAdmins 8-arg overload with excluirUserId (actor exclusion in ADMIN fan-out); 7-arg version now delegates to it"
affects: [87-02, 87-03]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "notificar* wrapper composition: each public method = null-guarded criar() for primary recipient + notificarAdmins() fan-out, never a direct notificacaoRepository.save()"
    - "Actor-exclusion overload (notificarAdmins 8-arg) added alongside the existing 7-arg version via internal delegation, avoiding a breaking signature change to the Phase 86 method call sites"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/services/NotificacaoService.java
    - backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java

key-decisions:
  - "notificarAdmins 7-arg (pre-existing, Phase 86) now delegates to the new 8-arg overload with excluirUserId=null, rather than duplicating fan-out logic — keeps the pre-existing test at line ~146 passing unmodified"
  - "notificarDocumentoNovo dedups the destinatarios Collection via LinkedHashSet (preserves insertion order for deterministic assertions) — covers a user being both ClienteAdvogado and ClienteAdministrativo for the same cliente"
  - "No dedup between the primary recipient and the ADMIN fan-out in notificarDocumentoNovo/notificarParecerAtribuido — if the primary recipient is also an ADMIN they receive 2 rows; this preserves notificarAdmins' Phase-86-fixed independence and is documented in-code as an intentional non-fix"
  - "FASE_ENTRADA and PROCESSO_ATRIBUIDO do not exclude the actor (per CONTEXT.md); only DOCUMENTO_NOVO and PARECER_ATRIBUIDO do"

patterns-established:
  - "Wrapper method shape: build titulo/mensagem strings first, null-guard the primary criar() call, always call notificarAdmins() afterward (with or without excluirUserId depending on category)"

requirements-completed: [NOTF-15, NOTF-16, NOTF-18, NOTF-19]

# Metrics
duration: 9min
completed: 2026-07-09
---

# Phase 87 Plan 01: NotificacaoService Event Wrappers Summary

**Four public `notificar*` convenience methods on `NotificacaoService` (FASE_ENTRADA, DOCUMENTO_NOVO, PROCESSO_ATRIBUIDO, PARECER_ATRIBUIDO) plus an actor-exclusion overload of `notificarAdmins`, all built via strict RED/GREEN TDD with Mockito.**

## Performance

- **Duration:** 9 min
- **Started:** 2026-07-09T06:18:28-01:00 (after phase plan commit)
- **Completed:** 2026-07-09T06:27:24-01:00
- **Tasks:** 2 completed
- **Files modified:** 2

## Accomplishments
- `NotificacaoService` now exposes the 4 controller-facing wrapper methods that Plans 87-02/87-03 will call — each a single line at the call site instead of inline fan-out logic.
- Added an `excluirUserId` overload of `notificarAdmins` (8 args) so DOCUMENTO_NOVO and PARECER_ATRIBUIDO can exclude the acting user from the ADMIN fan-out without duplicating the loop; the pre-existing 7-arg method now delegates to it.
- Null-guarded both process-side wrappers (`notificarFaseEntrada`, `notificarProcessoAtribuido`) against a `null` `responsavelId` — a very common state pre-Phase-87 since only `createProcesso` sets it today.
- 9 new Mockito tests added (4 for Task 1, 5 for Task 2), all executed as genuine RED before GREEN; 9 pre-existing Phase 86 tests remain green throughout — final suite is 18/18 green.

## Task Commits

Each task followed the RED → GREEN TDD cycle with its own commits:

1. **Task 1: Overload notificarAdmins (actor exclusion) + wrappers do lado processo**
   - `73ca8e7` (test) — RED: 4 failing tests + no-op stub methods (compilable, zero behavior)
   - `94a12e3` (feat) — GREEN: full implementation; 13/13 tests pass
2. **Task 2: Wrappers com exclusão de ator (DOCUMENTO_NOVO, PARECER_ATRIBUIDO)**
   - `60c02f8` (test) — RED: 5 failing tests + no-op stub methods
   - `721a8e7` (feat) — GREEN: full implementation; 18/18 tests pass

**Plan metadata:** (this commit, docs: complete plan — see final commit below)

_Note: both tasks are TDD (`tdd="true"`); each has exactly one test→feat commit pair, no refactor commit was needed since GREEN implementations required no cleanup._

## TDD Gate Compliance

Verified via `git log --oneline`:
- Task 1: `test(87-01)` at `73ca8e7` precedes `feat(87-01)` at `94a12e3` — RED before GREEN confirmed.
- Task 2: `test(87-01)` at `60c02f8` precedes `feat(87-01)` at `721a8e7` — RED before GREEN confirmed.
- Both RED commits were confirmed via `mvn test` output showing the new tests failing with "zero interactions" (stub methods were genuinely no-op, not just uncompiled) before the corresponding GREEN commit.

## Files Created/Modified
- `backend/src/main/java/com/lexcv/services/NotificacaoService.java` — added `notificarAdmins` 8-arg overload (actor exclusion), refactored 7-arg version to delegate to it, added `notificarFaseEntrada`, `notificarProcessoAtribuido`, `notificarDocumentoNovo`, `notificarParecerAtribuido`.
- `backend/src/test/java/com/lexcv/services/NotificacaoServiceTest.java` — added 9 new Mockito tests covering fan-out exclusion, null-guard, actor exclusion (primary + admin fan-out), and destinatarios dedup.

## Decisions Made
- **`notificarAdmins` 7-arg delegates to 8-arg with `excluirUserId=null`** instead of duplicating the loop body — keeps the pre-existing Phase 86 test at line ~146 passing without modification, and gives a single source of truth for the fan-out loop.
- **Dedup via `LinkedHashSet`** in `notificarDocumentoNovo` rather than `HashSet` — preserves insertion order so test assertions on the exact sequence of `save()` calls are deterministic.
- **No dedup between primary recipient and ADMIN fan-out** — documented in-code as intentional (matches the plan's explicit instruction not to "fix" this pre-existing Phase 86 `notificarAdmins` independence).
- **`"(sem número)"` fallback** when `numeroProcesso` is null in both process-side wrappers, so message construction never produces a literal `"null"` substring.

## Deviations from Plan

None — plan executed exactly as written. Both tasks' `<action>` steps (signatures, composition logic, categoria/entidadeTipo constants, message copy) were followed verbatim from the plan and its Interfaces/Pattern Map sections.

One clarifying note (not a deviation): the plan's `must_haves.truths` states the null-responsavelId-no-exception guarantee applies to **both** `notificarFaseEntrada` and `notificarProcessoAtribuido`, but Task 1's `<behavior>` list only specifies a dedicated test case for `notificarFaseEntrada`'s null-responsavelId path (matching exactly what was implemented). `notificarProcessoAtribuido` uses the textually identical `if (responsavelId != null) { criar(...) }` guard pattern, so the guarantee holds by code symmetry with the tested case, even though no separate unit test exercises `notificarProcessoAtribuido(..., null, ...)` directly. Flagging for visibility, not treating as a gap requiring extra scope — the plan's own behavior list did not request that specific test.

## Issues Encountered
None.

## User Setup Required
None — no external service configuration required.

## Next Phase Readiness
- `NotificacaoService` now has all 4 categories' composition logic centralized and tested; Plans 87-02/87-03 can call `notificarFaseEntrada`/`notificarDocumentoNovo`/`notificarProcessoAtribuido`/`notificarParecerAtribuido` directly from their controller trigger points (`createProcessoFase`, upload endpoint, `createProcesso` + new reassignment endpoint, `ParecerController.createSolicitacao`/`atribuirAdvogado`) without needing any further changes to `NotificacaoService`.
- No blockers. `mvn -f backend/pom.xml -q -DskipTests compile` confirms the full backend still compiles — no existing call site was broken by the `notificarAdmins` signature change (the pre-existing 7-arg signature is untouched; only its body changed to delegate).

---
*Phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer*
*Completed: 2026-07-09*
