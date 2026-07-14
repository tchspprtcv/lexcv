---
phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
plan: 03
subsystem: api
tags: [spring-boot, notifications, rest, rbac, tenant-isolation]

# Dependency graph
requires:
  - phase: 93-01
    provides: "NotificacaoPreferencia entity, CategoriaNotificacao enum, NotificacaoPreferenciaRepository"
  - phase: 93-02
    provides: "NotificacaoService.silenciarCategoria/reativarCategoria/listarCategoriasSilenciadas + mute guard inside criar()"
provides:
  - "GET /api/v1/notificacoes/preferencias — self-service read of caller's muted categories"
  - "PUT /api/v1/notificacoes/preferencias/{categoria} — mute a category, 400 on PRAZO_VENCIDO/unknown"
  - "DELETE /api/v1/notificacoes/preferencias/{categoria} — unmute a category (idempotent)"
affects: [93-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Controller endpoints delegate entirely to NotificacaoService (no direct repository writes from the controller), preserving the file's existing sole-write-path convention"
    - "IllegalArgumentException from the service layer translated to 400 Bad Request with Map.of(\"message\", ...) body, matching the existing listar() badRequest pattern in the same file"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java

key-decisions:
  - "Endpoint shape: GET/PUT/DELETE /preferencias/{categoria} (3 verbs, category-in-path) chosen over a single endpoint accepting the full list — matches the plan's Claude's Discretion note and mirrors the existing REST style already used in this controller (PATCH /{id}/lida)"
  - "Response key \"silenciadas\" (list of muted category names) — frontend (93-04) derives each toggle's state by checking list membership rather than the API returning a full 9-entry on/off map"

patterns-established:
  - "Preference toggle endpoints are self-service only: userId/tenantId always derive from getTenantId()/getUserId() (JWT), never accepted from path/query/body — same dual-scoping discipline as the rest of NotificacaoController"

requirements-completed: [NOTF-24]

# Metrics
duration: ~10min
completed: 2026-07-14
---

# Phase 93 Plan 03: NOTF-24 Preferences REST Endpoints Summary

**3 self-service REST endpoints (`GET`/`PUT`/`DELETE /api/v1/notificacoes/preferencias`) added to `NotificacaoController`, delegating entirely to the Plan 93-02 service methods, with PRAZO_VENCIDO/unknown-category mute attempts translated to 400**

## Performance

- **Duration:** ~10 min
- **Completed:** 2026-07-14T10:45:41Z
- **Tasks:** 1 completed
- **Files modified:** 1

## Accomplishments
- `GET /preferencias` returns `{"silenciadas": [...]}` — the caller's own muted category names, dual-scoped by `getTenantId()`/`getUserId()` from the JWT
- `PUT /preferencias/{categoria}` mutes a category by delegating to `NotificacaoService.silenciarCategoria(...)`; catches the service's `IllegalArgumentException` (thrown for `PRAZO_VENCIDO` or an unrecognized category) and returns `400 Bad Request` with a `message` body — Success Criterion 2
- `DELETE /preferencias/{categoria}` unmutes a category via `NotificacaoService.reativarCategoria(...)`; idempotent, matching the service's derived-delete semantics — Success Criterion 4
- All 3 endpoints gated `@PreAuthorize("hasAuthority('notificacoes:view')")` (self-service scope reused, no new RBAC scope introduced) and never read `userId` from the request — verified by both the plan's automated grep assertion and manual read of the diff

## Task Commits

Each task was committed atomically:

1. **Task 1: Endpoints GET/PUT/DELETE /preferencias** - `6e8ca1c` (feat)

_Note: no plan-metadata commit is included here — the orchestrator commits STATE.md/ROADMAP.md separately after this SUMMARY lands._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` - added `listarPreferencias()` (GET), `silenciar()` (PUT), `reativar()` (DELETE), plus `PutMapping`/`DeleteMapping`/`List` imports

## Decisions Made
None beyond what was already locked in 93-CONTEXT.md and the plan itself — executed as specified:
- Reused `notificacoes:view` scope, no new RBAC scope
- No direct repository access added to the controller — all 3 endpoints delegate to `NotificacaoService`, preserving the class doc-comment's "sole write path" guarantee
- Did not touch the 4 pre-existing endpoints (`listar`, `contarNaoLidas`, `marcarLida`, `marcarTodasLidas`)

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

**Worktree behind master (same pre-condition documented in 93-02-SUMMARY.md):** This worktree's branch (`worktree-agent-a6e8a6d441efc16a0`) had no `.planning/phases/` directory at all when execution started — it was several commits behind `master`, which already contained Plan 93-01's and 93-02's artifacts. Verified with `git merge-base --is-ancestor HEAD master` (HEAD was a strict ancestor of master, working tree clean, zero divergent commits), then ran `git merge --ff-only master` before reading any plan files. Re-verified the worktree-agent branch safety assertion after the fast-forward. Not a plan deviation — the documented pre-condition from the execution prompt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- Plan 93-04 (frontend `/settings` preferences UI) can now call `GET/PUT/DELETE /api/v1/notificacoes/preferencias(/{categoria})` directly — contract is: `GET` returns `{"silenciadas": string[]}`, `PUT`/`DELETE` return `{"categoria": string, "silenciada": boolean}`, `PUT` returns 400 with `{"message": string}` for `PRAZO_VENCIDO`/unknown categories
- No blockers. `cd backend && mvn -q -DskipTests compile` succeeds; source-assertion verification script (7 checks: GET/PUT/DELETE mappings, service delegation, dual-scoping, RBAC gating count, 400 rejection, no body-userId) passes 8/8.

---
*Phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador*
*Completed: 2026-07-14*

## Self-Check: PASSED

All modified files confirmed on disk (`NotificacaoController.java` — verified via `cat -n` re-read showing the 3 new endpoints); task commit (`6e8ca1c`) confirmed in `git log`; `93-03-SUMMARY.md` confirmed on disk.
