---
phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
plan: "01"
subsystem: api
tags: [spring-boot, processo, validation, rest-api]

# Dependency graph
requires:
  - phase: LEXCV-80-fundacoes-processo-juizo-origem-entidades-decisao-facto-testemunha
    provides: "Processo.juizo/origem columns, OrigemProcesso enum (schema already landed, no migration needed here)"
provides:
  - "origem required at /processos/intake (server-side 422 gate, independent of frontend validation)"
  - "origem required in CAMPOS_MINIMOS_POR_TIPO for every tipo_processo (including default), enforced by formalizarProcesso"
  - "juizo persisted via PUT /processos/{id}; origem deliberately excluded from that same endpoint (immutable after intake)"
  - "juizo/origem surfaced in GET /processos enriched list response (previously only visible on GET /processos/{id})"
affects: [LEXCV-81-02, LEXCV-81-03, LEXCV-83-frontend-types-schemas-hooks, LEXCV-84-frontend-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "422 UNPROCESSABLE_ENTITY with { message, camposEmFalta: [...] } is the standard shape for both intake-time and formalizar-time missing-field validation"
    - "Immutable-after-create fields (estado, now origem) are excluded from an update endpoint's field-copy list with an explicit `// X is intentionally excluded: ...` comment, not rejected with a 400"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "origem violations return HTTP 422 (not 400) at intake, matching formalizarProcesso's existing convention for missing-required-field responses, rather than inventing a new error shape"
  - "PUT /processos/{id} silently ignores any origem value in the payload rather than rejecting the request with 400 — matches the existing estado-exclusion precedent in the same method"

patterns-established: []

requirements-completed: [PROC-02, PROC-03, PROC-04, PROC-05]

# Metrics
duration: 12min
completed: 2026-07-07
---

# Phase LEXCV-81 Plan 01: Wiring Juízo/Origem into Processo lifecycle Summary

**`origem` is now a server-enforced required field at intake and formalização (422 gate, every `tipo_processo` including `default`), `juizo` is persisted via update while `origem` is made immutable post-intake, and both fields now appear in the `GET /processos` list response, not just the detail view.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-07-07T18:29:00-01:00 (approx, after 81-01-PLAN.md commit)
- **Completed:** 2026-07-07T18:38:06-01:00
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- `POST /processos/intake` rejects a missing `origem` with HTTP 422 (`camposEmFalta: ["origem"]`) before any persistence occurs, closing a gap where the endpoint validated nothing server-side (PITFALLS.md Pitfall 2)
- `CAMPOS_MINIMOS_POR_TIPO` requires `origem` for all 7 `tipo_processo` keys (civel, penal, laboral, administrativo, familia, comercial, default), and `formalizarProcesso`'s campos-mínimos gate now enforces it via a `case "origem"` switch arm
- `updateProcesso` persists `juizo` from the payload while deliberately never overwriting `origem` (silently ignored, no 400), matching the existing `estado`-exclusion convention
- `listProcessos`'s hand-built enriched map now includes `juizo`/`origem` for every processo in the list, preventing the "field visible on detail but missing from list" recurrence flagged by milestone research (ARCHITECTURE.md Anti-Pattern 4)

## Task Commits

Each task was committed atomically:

1. **Task 1: origem obrigatório em createProcessoIntake + CAMPOS_MINIMOS_POR_TIPO + formalizarProcesso gate** - `47cbcd6` (feat)
2. **Task 2: updateProcesso (juizo persisted, origem excluded) + listProcessos enriched map (juizo/origem added)** - `97dc5a2` (feat)

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - `CAMPOS_MINIMOS_POR_TIPO` extended with `"origem"` on all 7 keys; `formalizarProcesso` switch gained a `case "origem"` arm; `createProcessoIntake` gained a pre-persist 422 gate on null `origem`; `updateProcesso` now copies `juizo` and explicitly excludes `origem`; `listProcessos`'s enriched `LinkedHashMap` builder gained `juizo`/`origem` entries adjacent to `tribunal`

## Decisions Made
- origem violations at intake return HTTP 422 (not 400), reusing `formalizarProcesso`'s existing error shape (`message` + `camposEmFalta`) rather than inventing a new one — consistent error contract across both validation points
- `updateProcesso` never rejects a payload containing a different `origem`; it simply never applies it (silent no-op), matching the pre-existing `estado`-exclusion pattern in the same method rather than adding new 400-on-mismatch logic

## Deviations from Plan

None - plan executed exactly as written. Both edit points matched the plan's `<interfaces>` reference code verbatim (verified via Read before editing); no drift from the Phase 80 landing.

## Issues Encountered

**Live round-trip verification (Task 2) was attempted but could not complete authentication.** The plan's Task 2 `<action>` specifies a live HTTP round-trip (login as `admin@lexcv.cv`/`admin123`, then exercise intake/update/list) with an explicit fallback: "If local Postgres/MinIO are unreachable... fall back to `mvn -DskipTests package` success as the acceptance gate."

What was done:
- Confirmed local PostgreSQL reachable (`localhost:5432`, TCP probe succeeded)
- Started the backend locally via `mvn spring-boot:run` with MinIO env vars supplied at the process level (same fallback approach as Phase 80, `backend/.env` not edited) — startup succeeded cleanly (`Started BackendApplication`, `MinIO bucket 'lexcv-documentos' verified.`), confirming both Task 1 and Task 2's compiled code loads and runs with no Spring context errors
- Attempted `POST /api/v1/auth/login` with the documented default admin credentials (`admin@lexcv.cv` / `admin123`) — received `401 Credenciais inválidas`. The `DatabaseSeeder` skips seeding entirely once any tenant/user/cliente row exists (`DatabaseSeeder.java:54`), and this local database already contains data from prior phase work and/or real project use (the repo root contains real business documents from active use of this LexCV instance) — the admin password may have been changed since the original seed, or a different account is now the active admin
- Did not attempt further password guesses: `AuthController` has a 5-attempt lockout (15 min) per email+session, and this is a live development database with real data, not an ephemeral test fixture — brute-forcing credentials against it was judged out of scope and risky
- Stopped the backend cleanly after confirming the build/startup gate (killed the JVM process bound to port 8080; confirmed via failed `curl` connection afterward)

**Resolution:** Fell back to the plan's explicitly documented fallback gate — `mvn -DskipTests package` exits 0 (confirmed after both tasks) — plus a live Spring Boot startup confirming the changed code initializes correctly against real PostgreSQL/MinIO. The specific request/response assertions (422 on missing origem, 201 with origem echoed, origem-unchanged-after-PUT, juizo/origem present in list) were verified by direct code review against the plan's `<acceptance_criteria>` instead of live HTTP calls. This mirrors the fallback convention already established in Phase 80's plan/summary.

**Not a blocker for this plan or subsequent waves** — Waves 2 and 3 of Phase 81 modify non-overlapping regions of the same file and do not depend on this live round-trip having executed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ResourceController.java` changes are confined to `CAMPOS_MINIMOS_POR_TIPO`, `formalizarProcesso`'s switch, `createProcessoIntake`, `updateProcesso`, and `listProcessos`'s enriched-map builder — no unrelated methods touched, leaving clean non-overlapping regions for Wave 2 (81-02) and Wave 3 (81-03) to edit the same file next
- Recommend a live end-to-end round-trip test of the intake/update/list origem-juizo behavior be performed manually or in CI with valid credentials for this database before the milestone's final UAT, since this plan's live check was blocked by credential mismatch rather than confirmed passing

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/controllers/ResourceController.java
- FOUND: .planning/phases/LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem/81-01-SUMMARY.md
- FOUND commit: 47cbcd6
- FOUND commit: 97dc5a2

---
*Phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem*
*Completed: 2026-07-07*
