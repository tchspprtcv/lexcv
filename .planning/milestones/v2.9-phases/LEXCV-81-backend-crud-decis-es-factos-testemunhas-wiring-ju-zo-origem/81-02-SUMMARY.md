---
phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
plan: "02"
subsystem: api
tags: [spring-boot, processo, decisao, testemunha, multipart-upload, rest-api]

# Dependency graph
requires:
  - phase: LEXCV-80-fundacoes-processo-juizo-origem-entidades-decisao-facto-testemunha
    provides: "Decisao/Testemunha entities, TipoDecisao/TipoTestemunha enums, DecisaoRepository/TestemunhaRepository (schema already landed, no migration needed here)"
provides:
  - "GET/POST/PUT/DELETE /processos/{id}/decisoes — 4 endpoints, POST accepts optional multipart file upload creating a Documento internally and linking via Decisao.documentoId (no pre-existing-document picker)"
  - "GET/POST/PUT/DELETE /processos/{id}/testemunhas — 4 plain-CRUD endpoints"
  - "decisaoRepository/testemunhaRepository added to ResourceController's constructor-injected field list"
affects: [LEXCV-81-03, LEXCV-83-frontend-types-schemas-hooks, LEXCV-84-frontend-ui]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Double-check ownership pattern (parent Processo tenant check + child.getProcessoId().equals(id) re-check) applied to every PUT/DELETE on a Processo child entity — copied verbatim from ProcessoFase, not the simpler Parte/Movimentacao single-check pattern (PROC-17)"
    - "Create-time multipart upload with server-derived processoId/tenantId (never client-supplied) closes the IDOR vector for internally-created Documento rows linked from a child entity"

key-files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "updateDecisao deliberately never copies payload.getDocumentoId() — the anexo can only be attached at creation time via multipart upload in this phase; re-attaching/replacing via PUT is out of scope"
  - "Testemunha.tipo binds directly via @RequestBody Jackson deserialization (no manual enum parsing) since it's not a manually-parsed multipart param like Decisão's tipo/data"

patterns-established:
  - "DECISÕES / FACTOS / TESTEMUNHAS section header inserted after MOVIMENTACOES and before TIMELINE in ResourceController.java — Facto's endpoints (81-03) will extend this same section"

requirements-completed: [PROC-07, PROC-08, PROC-12, PROC-17]

# Metrics
duration: 8min
completed: 2026-07-07
---

# Phase LEXCV-81 Plan 02: Decisão + Testemunha CRUD Summary

**8 new REST endpoints (`GET/POST/PUT/DELETE /processos/{id}/decisoes` and `/testemunhas`) added to `ResourceController.java`, with Decisão's create accepting a direct multipart file upload that builds the `Documento` internally (no pre-existing-document picker), and every write endpoint enforcing the `ProcessoFase`-style double-check ownership pattern (parent tenant + child `processoId` re-check).**

## Performance

- **Duration:** 8 min
- **Started:** 2026-07-07T19:42:27Z (approx, after 81-01 completion)
- **Completed:** 2026-07-07T19:50:00Z
- **Tasks:** 2
- **Files modified:** 1

## Accomplishments
- `listDecisoes`/`createDecisao`/`updateDecisao`/`deleteDecisao` — full Decisão CRUD; `createDecisao` accepts `multipart/form-data` with an optional `file` param, uploads via `storageService.upload(...)`, builds and saves a `Documento` (processoId/tenantId taken from the already tenant-checked path, never client-supplied), and links it via `decisao.setDocumentoId(savedDoc.getId())`
- `listTestemunhas`/`createTestemunha`/`updateTestemunha`/`deleteTestemunha` — full Testemunha CRUD, plain `@RequestBody`-bound JSON, no special field parsing
- Every `PUT`/`DELETE` (4 total) validates parent `Processo` tenant ownership AND re-checks the child entity's `processoId` against the path `{id}` before any mutation — confirmed by direct code inspection (`grep -n "getProcessoId().equals(id)"` shows all 4 write endpoints)
- `decisaoRepository`/`testemunhaRepository` added to the constructor-injected field list; `@RequiredArgsConstructor` auto-wires both, no explicit constructor changes needed

## Task Commits

Each task was committed atomically:

1. **Task 1: Decisão CRUD — GET/POST(multipart)/PUT/DELETE /processos/{id}/decisoes** - `f2c6304` (feat)
2. **Task 2: Testemunha CRUD — GET/POST/PUT/DELETE /processos/{id}/testemunhas** - `b0f4af2` (feat)

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` - Added `decisaoRepository`/`testemunhaRepository` fields; new `DECISÕES / FACTOS / TESTEMUNHAS` section (inserted after `createMovimentacao`, before the `TIMELINE` section) with 8 new `@RestController` methods

## Decisions Made
- `updateDecisao` intentionally excludes `documentoId` from the payload copy — matches the plan's explicit scope boundary (anexo attach is create-only in this phase)
- No manual enum-parsing try/catch needed for `Testemunha.tipo` in `createTestemunha`/`updateTestemunha` since it's bound via `@RequestBody` (Jackson handles invalid enum values with a default 400), unlike Decisão's manually-parsed multipart `tipo`/`data` params which required explicit `DateTimeParseException`/`IllegalArgumentException` catches

## Deviations from Plan

None - plan executed exactly as written. Both tasks matched the plan's `<interfaces>` reference code (Analog 1/2/3) verbatim, verified via Read before editing.

## Issues Encountered

**Live round-trip verification (Task 2) could not complete authentication, same constraint documented by 81-01.** The plan's Task 2 `<action>` specifies a live HTTP round-trip (login as `admin@lexcv.cv`/`Pa$$w0rd`, exercise create/list/update/cross-processo-404/delete for both Decisão and Testemunha) with an explicit fallback clause: "If local Postgres/MinIO are unreachable... fall back to `mvn -DskipTests package` success as the acceptance gate."

81-01-SUMMARY.md already documented that this local database's `admin@lexcv.cv`/`Pa$$w0rd` login returns `401 Credenciais inválidas` because `DatabaseSeeder` skipped seeding (the DB already contains real project data, not an ephemeral fixture) and `AuthController` enforces a 5-attempt lockout per email+session. Per this plan's `<known_note>`, guessing alternate credentials was judged out of scope and risky (lockout), so no new login attempt was made — repeating the same known-failing attempt would only consume lockout budget with no new information.

**Resolution:** Used the plan's explicitly documented fallback gate:
- `mvn -DskipTests package` exits 0 after both tasks (confirmed twice, once per task)
- Direct code review against every `<acceptance_criteria>` bullet: confirmed via `grep -n "getProcessoId().equals(id)"` that all 4 new write endpoints (`updateDecisao`, `deleteDecisao`, `updateTestemunha`, `deleteTestemunha`) contain the child `processoId`-equals-path-`id` re-check, in addition to the parent tenant check already visible in each method body; confirmed `createDecisao`'s multipart branch contains `documentoRepository.save(` followed by `decisao.setDocumentoId(savedDoc.getId())`
- The concrete cross-processo negative-test HTTP assertion (mismatched `{id}`/`{childId}` pair returning 404 at runtime) was **not** executed live in this session — it remains verified by source-level double-check pattern inspection only, consistent with 81-01's precedent and this plan's own fallback clause

**Not a blocker for this plan or Wave 3 (81-03)** — 81-03 (Facto CRUD) modifies the same file's DECISÕES/FACTOS/TESTEMUNHAS section but is independent, additive work; it does not depend on this plan's live round-trip having executed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- `ResourceController.java`'s new `DECISÕES / FACTOS / TESTEMUNHAS` section is ready for Wave 3 (81-03) to extend with Facto's endpoints (which additionally need `ordem`-sequencing logic per the plan's stated scope)
- Recommend a live end-to-end round-trip test of the Decisão/Testemunha create/list/update/cross-processo-404/delete lifecycle be performed manually or in CI with valid credentials for this database before the milestone's final UAT, since both this plan's and 81-01's live checks were blocked by the same credential mismatch rather than confirmed passing
- Carrying forward the same recommendation logged in 81-01-SUMMARY.md: this local dev database needs either a reset to trigger reseeding, or the current admin password needs to be documented/rotated, so future phases in this milestone (81-03, 82, 83, 84) can perform live verification instead of falling back to code review every time

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/controllers/ResourceController.java
- FOUND: .planning/phases/LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem/81-02-SUMMARY.md
- FOUND commit: f2c6304
- FOUND commit: b0f4af2

---
*Phase: LEXCV-81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem*
*Completed: 2026-07-07*
