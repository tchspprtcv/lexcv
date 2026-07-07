---
phase: LEXCV-80-funda-es-processo-juizo-origem-entidades-decis-o-facto-teste
plan: "01"
subsystem: database
tags: [jpa, hibernate, spring-data, postgresql, java]

# Dependency graph
requires: []
provides:
  - "Processo.juizo (String) and Processo.origem (OrigemProcesso, EnumType.STRING) columns"
  - "OrigemProcesso enum (PETICAO_INICIAL, NOTIFICACOES_AVULSAS)"
  - "TipoDecisao enum (DESPACHO, DECISAO_INTERLOCUTORIA, SENTENCA, ACORDAO)"
  - "TipoTestemunha enum (AUTOR, REU)"
  - "Decisao entity (t_decisao) + DecisaoRepository"
  - "Facto entity (t_facto) + FactoRepository"
  - "Testemunha entity (t_testemunha) + TestemunhaRepository"
affects: ["LEXCV-81 (backend CRUD)", "LEXCV-82 (Honorário auto-creation)", "LEXCV-83 (frontend types/hooks)", "LEXCV-84 (frontend UI)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Lean child-entity shape mirroring Parte.java: Integer IDENTITY id, processo_id FK, no own tenant_id column"
    - "Enum-as-string storage (@Enumerated(EnumType.STRING)) mirroring Cliente.documentoTipo"
    - "Minimal Spring Data JPA repositories (findByProcessoId derived queries only, no service layer)"

key-files:
  created:
    - backend/src/main/java/com/lexcv/models/OrigemProcesso.java
    - backend/src/main/java/com/lexcv/models/TipoDecisao.java
    - backend/src/main/java/com/lexcv/models/TipoTestemunha.java
    - backend/src/main/java/com/lexcv/models/Decisao.java
    - backend/src/main/java/com/lexcv/models/Facto.java
    - backend/src/main/java/com/lexcv/models/Testemunha.java
    - backend/src/main/java/com/lexcv/repositories/DecisaoRepository.java
    - backend/src/main/java/com/lexcv/repositories/FactoRepository.java
    - backend/src/main/java/com/lexcv/repositories/TestemunhaRepository.java
  modified:
    - backend/src/main/java/com/lexcv/models/Processo.java

key-decisions:
  - "No deviations from plan -- all enum constant values, entity shapes, and repository methods matched the plan's interfaces section exactly"

patterns-established:
  - "Decisao/Facto/Testemunha carry zero tenant_id column by design -- tenant isolation enforced transitively via parent Processo, to be applied at controller layer in Phase 81"
  - "documentoId on Decisao is a raw nullable UUID FK column with no JPA relationship mapping and no ownership validation at this layer -- Phase 81 controller must validate tenant+processo ownership before persisting"

requirements-completed: [PROC-01, PROC-06, PROC-09, PROC-11]

# Metrics
duration: 15min
completed: 2026-07-07
---

# Phase 80 Plan 01: Fundações — Processo.juizo/origem + Entidades Decisão/Facto/Testemunha Summary

**Added `juizo`/`origem` columns to `Processo` plus three new lean JPA entities (`Decisao`, `Facto`, `Testemunha`) with their enums and repositories, verified via a live Spring Boot startup against local PostgreSQL with `ddl-auto=update`.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-07-07T11:49:00-01:00
- **Completed:** 2026-07-07T11:56:00-01:00
- **Tasks:** 3 completed
- **Files modified:** 10 (9 created, 1 modified)

## Accomplishments
- `Processo` entity now persists a free-text `juizo` column and an `OrigemProcesso`-enum-backed `origem` column, following the exact `Cliente.documentoTipo` enum-as-string precedent
- Three new enums (`OrigemProcesso`, `TipoDecisao`, `TipoTestemunha`) created with the exact locked constant values from milestone requirements gathering
- Three new lean JPA entities (`Decisao`, `Facto`, `Testemunha`) mirror `Parte.java`'s shape exactly: `Integer` IDENTITY id, `processo_id` FK, zero `tenant_id` column
- Three minimal Spring Data JPA repositories created, each with `findByProcessoId`; `FactoRepository` additionally exposes `findByProcessoIdOrderByOrdemAsc` for Phase 81's ordered listing needs
- Live application startup verified against local PostgreSQL (`SEED_ENABLED=false`, MinIO vars supplied as process-level env vars): `Started BackendApplication` confirmed in the log, zero `SchemaManagementException`/`PropertyAccessException`/schema-error occurrences — the new `t_decisao`/`t_facto`/`t_testemunha` tables and `t_processo.juizo`/`t_processo.origem` columns applied cleanly via `ddl-auto=update` without breaking existing `Processo` persistence

## Task Commits

Each task was committed atomically:

1. **Task 1: Create OrigemProcesso/TipoDecisao/TipoTestemunha enums and extend Processo with juizo/origem** - `78f74e6` (feat)
2. **Task 2: Create Decisao, Facto and Testemunha entities mirroring Parte.java's lean shape** - `899f8fd` (feat)
3. **Task 3: Create the three repositories and verify the app starts cleanly with the new schema applied** - `ef36373` (feat)

**Plan metadata:** (this commit, following SUMMARY.md creation)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/OrigemProcesso.java` - New enum: PETICAO_INICIAL, NOTIFICACOES_AVULSAS
- `backend/src/main/java/com/lexcv/models/TipoDecisao.java` - New enum: DESPACHO, DECISAO_INTERLOCUTORIA, SENTENCA, ACORDAO
- `backend/src/main/java/com/lexcv/models/TipoTestemunha.java` - New enum: AUTOR, REU
- `backend/src/main/java/com/lexcv/models/Processo.java` - Added bare `juizo` (String) field and `@Enumerated(EnumType.STRING)` `origem` (OrigemProcesso) field
- `backend/src/main/java/com/lexcv/models/Decisao.java` - New entity: Integer id, processoId, data, tipo (TipoDecisao), resumo, nullable documentoId (raw FK to t_documento)
- `backend/src/main/java/com/lexcv/models/Facto.java` - New entity: Integer id, processoId, descricao, data, ordem (nullable=false)
- `backend/src/main/java/com/lexcv/models/Testemunha.java` - New entity: Integer id, processoId, nome, contacto, tipo (TipoTestemunha), notas
- `backend/src/main/java/com/lexcv/repositories/DecisaoRepository.java` - JpaRepository<Decisao, Integer> + findByProcessoId
- `backend/src/main/java/com/lexcv/repositories/FactoRepository.java` - JpaRepository<Facto, Integer> + findByProcessoId + findByProcessoIdOrderByOrdemAsc
- `backend/src/main/java/com/lexcv/repositories/TestemunhaRepository.java` - JpaRepository<Testemunha, Integer> + findByProcessoId

## Decisions Made
None - followed plan as specified. All enum constant values, field shapes, annotations and repository methods matched the plan's interfaces section verbatim.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. Local PostgreSQL was reachable (confirmed via TCP probe to `localhost:5432` before running the live-startup check), so the full live-startup verification from Task 3's `<verify>` block ran successfully rather than falling back to the `mvn -DskipTests package`-only gate. MinIO environment variables (missing from the local `backend/.env` — a pre-existing environment gap unrelated to this phase) were supplied as process-level env vars for the one-off verification run only, per the plan's explicit instruction; `backend/.env` itself was not edited.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- All 10 files from this plan's changeset compile and the application starts cleanly with the new schema applied via `ddl-auto=update`
- `Decisao`, `Facto`, `Testemunha` entities and repositories are ready for Phase 81 to build CRUD controller endpoints on top of, including the mandatory double tenant/processoId ownership check (parent `Processo` tenant check + child `processoId` re-check) flagged in this plan's threat model (T-80-01) for every future write path
- `Decisao.documentoId` is a raw nullable FK column with zero validation at this layer — Phase 81's endpoint that sets this field MUST validate the referenced `Documento` belongs to the same tenant AND the same `processo` before persisting (T-80-03, flagged as deferred-but-tracked in this plan's threat model)
- No blockers or concerns for Phase 81

---
*Phase: LEXCV-80-funda-es-processo-juizo-origem-entidades-decis-o-facto-teste*
*Completed: 2026-07-07*

## Self-Check: PASSED

All 9 created files verified present on disk; all 3 task commits (78f74e6, 899f8fd, ef36373) verified present in git history.
