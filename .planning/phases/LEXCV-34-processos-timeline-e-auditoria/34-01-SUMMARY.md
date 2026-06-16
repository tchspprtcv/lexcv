---
phase: 34-processos-timeline-e-auditoria
plan: "01"
subsystem: api
tags: [spring-boot, jpa, audit-log, timeline, rbac, multi-tenant]

# Dependency graph
requires:
  - phase: 33-processos-workflow-gates-e-prazos
    provides: executarTransicao endpoint that creates Movimentacao with tipo=TRANSICAO_ESTADO; PrazoRepository and UserRepository already injected in ResourceController
  - phase: 32-processos-intake-e-conflict-check
    provides: ConflictCheckDecisao entity + ConflictCheckDecisaoRepository; registarDecisaoConflito endpoint pattern
provides:
  - AuditLog JPA entity mapping to t_audit_log with nullable processo_id, auto-timestamp via @PrePersist, Long PK
  - AuditLogRepository with findByTenantIdAndProcessoIdOrderByTimestampDesc named query
  - TimelineItemDto record (tipo, id, timestamp, titulo, descricao, autorNome)
  - Movimentacao.autorId nullable UUID column
  - GET /processos/{id}/timeline (processos:view) aggregating 4 source entities in timestamp DESC order
  - GET /processos/{id}/audit (processos:manage) returning AuditLog entries enriched with autorNome
  - resolveAutorNome() private helper for tenant-scoped user name resolution
  - Audit injection at 4 sensitive write points: executarTransicao, registarDecisaoConflito, downloadDocumento, deleteDocumento
affects:
  - 34-02 (frontend data layer — useTimeline, useAuditLog hooks consume these endpoints)
  - 34-03 (UI tab restructuring — Timeline tab replaces Movimentacoes tab)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "In-memory timeline aggregation from heterogeneous JPA entities with Comparator.comparing().reversed()"
    - "AuditLog entity with nullable processoId for non-processo-scoped document events"
    - "resolveAutorNome() helper pattern for tenant-scoped user display name resolution"
    - "Manual auditLogRepository.save() before response return for repudiation resistance"

key-files:
  created:
    - backend/src/main/java/com/lexcv/models/AuditLog.java
    - backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java
    - backend/src/main/java/com/lexcv/dtos/TimelineItemDto.java
  modified:
    - backend/src/main/java/com/lexcv/models/Movimentacao.java
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java

key-decisions:
  - "AuditLog.processo_id is nullable (no nullable=false) to accommodate document-level audit events (downloadDocumento, deleteDocumento) where the documento may not be linked to a processo — Pitfall #2 from RESEARCH.md"
  - "ConflictCheckDecisao timeline timestamp uses createdAt (LocalDateTime), not dataDecisao (LocalDate) — avoids ClassCastException in sort and preserves system-recorded ordering — Pitfall #3 from RESEARCH.md"
  - "Timeline endpoint never queries auditLogRepository — getTimeline() aggregates only operational entities (Movimentacao, Evento, Documento, ConflictCheckDecisao); audit data is exclusively in /audit — T-34-05 threat mitigation"
  - "Audit save placed before the response return in downloadDocumento() and before documentoRepository.delete() in deleteDocumento() so the record is written even if downstream processing fails — T-34-03 threat mitigation"

patterns-established:
  - "Timeline aggregation: build List<TimelineItemDto> from each source, sort with Comparator.nullsLast(reverseOrder()) — handle nulls gracefully"
  - "Audit injection: resolve UserPrincipal from SecurityContextHolder immediately before AuditLog.builder().save() — never accept author from request payload"
  - "Tenant-scoped audit query: always filter by tenantId AND processoId — Pitfall #5 from RESEARCH.md"

requirements-completed: [PRC-28, AUD-02]

# Metrics
duration: 20min
completed: 2026-06-16
---

# Phase 34 Plan 01: Processos Timeline e Auditoria — Backend Summary

**AuditLog JPA entity, AuditLogRepository, TimelineItemDto record, Movimentacao.autorId, GET /timeline (processos:view), GET /audit (processos:manage), and audit injection at 4 sensitive write points in ResourceController**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-06-16T18:24:00Z
- **Completed:** 2026-06-16T18:44:25Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- New `AuditLog` JPA entity (`t_audit_log`) with nullable `processo_id`, `Long` PK, and `@PrePersist` auto-timestamp — zero pom.xml changes
- New `AuditLogRepository` with `findByTenantIdAndProcessoIdOrderByTimestampDesc` for compliant tenant-scoped audit queries
- New `TimelineItemDto` top-level Java record with discriminator field `tipo` and common fields shared across all source entity types
- `Movimentacao.autorId` nullable UUID field added; `ddl-auto=update` handles migration automatically
- `GET /processos/{id}/timeline` aggregates Movimentacao, Evento, Documento, ConflictCheckDecisao in-memory, sorts by timestamp DESC with null-safety, requires `processos:view`
- `GET /processos/{id}/audit` returns AuditLog entries enriched with `autorNome` resolved from UserRepository, requires `processos:manage`
- `resolveAutorNome()` private helper enforces tenant boundary when resolving user display names
- Four audit injection points: executarTransicao (author tracked + audit saved), registarDecisaoConflito (audit after save), downloadDocumento (auth context added + audit before response), deleteDocumento (auth context added + audit before delete)

## Task Commits

Each task was committed atomically:

1. **Task 1: AuditLog entity, AuditLogRepository, TimelineItemDto record, Movimentacao.autorId** - `92a19f8` (feat)
2. **Task 2: ResourceController — auditLogRepository injection, getTimeline(), getAuditLog(), 4 audit points** - `b29f83e` (feat)

**Plan metadata:** (to be added after SUMMARY commit)

## Files Created/Modified
- `backend/src/main/java/com/lexcv/models/AuditLog.java` — New JPA entity; @Table(name="t_audit_log"), @GeneratedValue(IDENTITY) Long id, nullable processo_id, @PrePersist timestamp
- `backend/src/main/java/com/lexcv/repositories/AuditLogRepository.java` — JpaRepository<AuditLog,Long> with findByTenantIdAndProcessoIdOrderByTimestampDesc
- `backend/src/main/java/com/lexcv/dtos/TimelineItemDto.java` — Top-level Java record: tipo, id, timestamp (LocalDateTime), titulo, descricao, autorNome
- `backend/src/main/java/com/lexcv/models/Movimentacao.java` — Added `@Column(name="autor_id") UUID autorId` after prazoId
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — Added auditLogRepository field, resolveAutorNome(), getTimeline(), getAuditLog(), 4 audit saves at write points

## Decisions Made
- `AuditLog.processoId` is nullable (no `nullable=false` in `@Column`) to accommodate document events not linked to a processo — consistent with RESEARCH.md Pitfall #2 analysis
- `ConflictCheckDecisao` uses `createdAt` (LocalDateTime) as timeline timestamp, not `dataDecisao` (LocalDate) — prevents compile error and ensures chronological correctness (RESEARCH.md Pitfall #3)
- `getTimeline()` never queries `auditLogRepository` — separation of operational history (Timeline) from compliance trail (Audit) is a security design decision per T-34-05

## Deviations from Plan

None — plan executed exactly as written. EventoRepository.findByTenantIdAndProcessoId was confirmed to exist (A1 assumption from RESEARCH.md verified), so no additional EventoRepository change was needed.

## Issues Encountered
None. Both compilations passed on first attempt.

## User Setup Required
None — no external service configuration required. JPA `ddl-auto=update` will create `t_audit_log` table and `autor_id` column on `t_movimentacao` on next backend startup.

## Threat Surface Scan
All new endpoints are within the STRIDE threat register defined in the plan:
- GET /processos/{id}/timeline — covered by T-34-02 (tenant guard) and T-34-05 (no audit data exposed)
- GET /processos/{id}/audit — covered by T-34-01 (@PreAuthorize processos:manage) and T-34-02 (tenant guard)
- All audit injection points — covered by T-34-03 (repudiation resistance) and T-34-04 (autorId from SecurityContext)

No new threat surface outside the plan's threat model.

## Next Phase Readiness
- Wave 2 (34-02): Frontend data layer can now add `useTimeline(id)` and `useAuditLog(id)` TanStack Query hooks consuming the two new endpoints
- Wave 3 (34-03): UI tab restructuring can replace the Movimentacoes tab with the Timeline tab and add conditional Auditoria tab
- Backend API contract is stable: no changes to endpoint signatures expected in subsequent waves

---
*Phase: 34-processos-timeline-e-auditoria*
*Completed: 2026-06-16*
