---
phase: LEXCV-82-backend-cria-o-autom-tica-de-honor-rio-na-formaliza-o
plan: "01"
subsystem: api
tags: [spring-boot, processo, honorario, financeiro, idempotency]

# Dependency graph
requires:
  - phase: LEXCV-80-fundacoes-processo-juizo-origem-entidades-decisao-facto-testemunha
    provides: "Honorario entity + HonorarioRepository (already existed prior to v2.9, unchanged)"
provides:
  - "formalizarProcesso() auto-creates a Honorario (processoId, valorTotal=null, dataAcordo=today) inside its existing @Transactional block, guarded by honorarioRepository.findByProcessoId(id).isEmpty() so retries/replays never duplicate it"
affects: [LEXCV-83-frontend-types-schemas-hooks, LEXCV-84-frontend-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Idempotency guard via repository existence-check (findByX(id).isEmpty()) placed independently of and in addition to a pre-existing state guard, to protect a side-effect from transaction retries/replays"
    - "Auto-created financial placeholder rows (Honorario) hardcode monetary fields to null server-side rather than ever reading a soft-estimate field (Cliente.honorariosPropostos) from a related entity"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "Idempotency guard (honorarioRepository.findByProcessoId(id).isEmpty()) is a separate, independent check from the pre-existing estado != TRIAGEM guard -- not merged into it, so the intent (protect against retry/replay of the same transaction) is not confused with the state-machine guard's own purpose"
  - "valorTotal is set to the literal null via the builder, never read from Cliente.honorariosPropostos -- confirmed via full-file grep that honorariosPropostos is not referenced anywhere in ResourceController.java"

patterns-established: []

requirements-completed: [PROC-14]

# Metrics
duration: 12min
completed: 2026-07-07
---

# Phase LEXCV-82 Plan 01: Backend — Criação Automática de Honorário na Formalização Summary

**`formalizarProcesso()` now auto-creates an empty Honorario placeholder (valorTotal=null) the first time a processo transitions TRIAGEM→ATIVO, guarded by an independent existence-check so retries never create a duplicate.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-07T21:45:38Z
- **Completed:** 2026-07-07T21:57:00Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- `formalizarProcesso()` creates exactly one `Honorario` per processo (processoId, valorTotal=null, dataAcordo=today, descricao left unset/null) the first time it transitions TRIAGEM→ATIVO, inside the method's existing `@Transactional` block
- The creation is idempotent: `honorarioRepository.findByProcessoId(id).isEmpty()` is checked independently of (in addition to) the pre-existing `estado != TRIAGEM` guard, so even if the transaction were retried/replayed the Honorario would not be duplicated
- `valorTotal` is hardcoded to the literal `null` — confirmed by grep that `Cliente.honorariosPropostos` is never referenced anywhere in `ResourceController.java`, closing PITFALLS.md Pitfall 5 (money pre-fill risk) before it could be introduced
- Response contract of `POST /processos/{id}/formalizar` is unchanged — it still returns the updated `Processo`, not the `Honorario`

## Task Commits

Each task was committed atomically:

1. **Task 1: Criação idempotente de Honorario dentro de formalizarProcesso** - `169c840` (feat)
2. **Task 2: Verificação de idempotência (double-formalize) e money-safety em runtime** - no code changes (verification-only task; see Issues Encountered)

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - `formalizarProcesso()` gained a 12-line insertion between the existing `processo.setEstado("ATIVO");` and `return ResponseEntity.ok(processoRepository.save(processo));` lines: an idempotency-guarded `Honorario` creation. No other method touched; no new imports (both `Honorario` and `LocalDate` were already reachable).

## Decisions Made
- Idempotency guard kept fully independent of the estado guard (not folded into the same `if`), per 82-CONTEXT.md's explicit instruction, so a future reader cannot mistake the estado guard alone for protecting against duplication
- `descricao` intentionally left unset (Lombok builder default `null`) rather than given any placeholder string

## Deviations from Plan

None — plan executed exactly as written. The `<interfaces>` block's exact current source matched the live file verbatim (re-verified via Read/grep before editing), so the edit was a precise, minimal insertion with no drift.

## Issues Encountered

**Task 2's live authenticated HTTP round-trip could not complete, for the same reason documented in Phase 80/81 plans/summaries.** The plan's Task 2 `<action>` specifies a live double-formalize test (login as `admin@lexcv.cv`/`admin123`, formalize twice, inspect `t_honorario` for duplicates) with an explicit fallback clause: "If local Postgres/MinIO are unreachable in the execution environment, fall back to `mvn -DskipTests package` success plus a live `mvn spring-boot:run` startup confirmation."

What was done:
- `mvn -DskipTests package` — exits 0, confirmed after Task 1's edit
- Started the backend locally via `mvn spring-boot:run` with MinIO env vars supplied at the process level (same fallback approach as Phase 80/81, `backend/.env` not edited) — startup succeeded cleanly (`HikariPool-1 - Start completed`, `Tomcat started on port 8080`, `Started BackendApplication in 16.563 seconds`), confirming Task 1's compiled code loads and the Spring context (including the modified `formalizarProcesso` bean graph) initializes with no errors against real PostgreSQL
- Attempted `POST /api/v1/auth/login` with the documented default admin credentials (`admin@lexcv.cv` / `admin123`) — received `401 Credenciais inválidas`, the same outcome documented in 81-01-SUMMARY.md and 81-02-SUMMARY.md (this local database already contains real project data from active use, not a fresh seed; the admin password has likely diverged from the default)
- Did not attempt further password guesses or brute-force, consistent with the lockout-avoidance rationale already established in Phase 81's summaries
- Stopped the backend cleanly afterward (`taskkill` on the JVM PID bound to port 8080)

**Resolution:** Fell back to the plan's explicitly documented fallback gate — `mvn -DskipTests package` exits 0, plus a live, clean `mvn spring-boot:run` startup against real PostgreSQL. The specific runtime assertions (exactly-one-Honorario-per-processo after double-formalize, `valorTotal` JSON `null`, zero rows from the `GROUP BY processo_id HAVING COUNT(*) > 1` detection query) were verified by direct code review against the plan's `<acceptance_criteria>` instead of live HTTP/SQL calls:
- The idempotency guard (`honorarioRepository.findByProcessoId(id).isEmpty()`) unconditionally wraps every `Honorario` creation in this method, and is the only code path in the entire file that calls `honorarioRepository.save(...)` from within `formalizarProcesso` — a second call to the same processo id can only re-enter this method if `processo.getEstado()` were still `"TRIAGEM"`, but the first call's `processo.setEstado("ATIVO")` + `processoRepository.save(processo)` (unconditional, runs every time) already blocks that via the pre-existing estado guard at the top of the method. Both guards independently prevent duplication, satisfying T-82-01's mitigation.
- `grep -n "honorariosPropostos"` across the full file returns zero matches, and `grep -n "\.valorTotal("` returns exactly one match (`\.valorTotal(null)` inside the new block), directly confirming T-82-02's mitigation (no numeric literal or `Cliente.honorariosPropostos` value is ever passed to `valorTotal`).

**Not a blocker** — this mirrors the fallback convention already established and accepted as non-blocking in 80-01, 81-01, 81-02, and 81-03 summaries, all against the same live development database.

## User Setup Required

None — no external service configuration required.

## Next Phase Readiness

- `ResourceController.java`'s change is confined entirely to `formalizarProcesso()`'s body, between two pre-existing statements — no other method touched, leaving a clean, non-overlapping edit for any future phase that also touches this file
- Recommend a live end-to-end double-formalize test (as specified in Task 2) be performed manually with valid credentials for this database before the milestone's final UAT, since the automated live check was blocked by credential mismatch rather than confirmed passing — same recommendation already standing from Phase 81
- Phase 82 is the only phase carrying requirement PROC-14; with this plan complete, PROC-14 is fully satisfied and the v2.9 milestone's remaining work (Phases 83-84) has no further backend dependency on this phase

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/controllers/ResourceController.java
- FOUND: .planning/phases/LEXCV-82-backend-cria-o-autom-tica-de-honor-rio-na-formaliza-o/82-01-SUMMARY.md
- FOUND commit: 169c840

---
*Phase: LEXCV-82-backend-cria-o-autom-tica-de-honor-rio-na-formaliza-o*
*Completed: 2026-07-07*
