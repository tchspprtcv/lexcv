---
gsd_state_version: 1.0
milestone: v1.8
milestone_name: Deployment para VPS
status: planning
last_updated: "2026-06-16T22:44:14.849Z"
last_activity: 2026-06-16
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-13)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 34 — processos   timeline e auditoria

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-06-16 — Milestone v1.8 started

## Accumulated Context

### Roadmap Evolution

- Phase 11 added: Painel de Utilizador
- Phase 17 added: Ações UI com controlo por permissions
- Phase 32 added: Processos - Intake e Conflict Check
- Phase 33 added: Processos - Workflow, Gates e Prazos
- Phase 34 added: Processos - Timeline e Auditoria
- Phase 35 added: Processos - Governanca Documental e Retencao
- Phase 36 added: Processos - Dashboards e KPI Executivo

### Decisions

Decisions são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v1.0) Mock API dentro do Next.js (route handlers) em `/api/v1/*`
- (v1.1) UI institucional alinhada ao Figma (top bar + sidebar + páginas-chave)
- (v1.1) Aplicação de layout "Anti-Safe Harbor" (sharp edges, cores ousadas) e Dark/Light mode com `next-themes`.
- [Phase ?]: ConflictCheckDecisaoRequest extracted to separate file (Java requires one public top-level type per file)
- [32-02]: ConflictCheckDecisao TypeScript interface uses camelCase (tenantId, processoId, createdAt) to match Spring Boot Jackson default serialization
- [32-02]: conflictNivelToVariant/conflictNivelToLabel placed in lib/conflict-check.ts as single source of truth for Step 2, Step 3, and detail page
- [32-03]: Wizard uses local step state — no router.push between steps; all 3 steps live on /processos/novo
- [32-03]: estado excluded from intake payload (destructured as _estado) — backend enforces TRIAGEM
- [32-03]: Formalizar disabled check: !decisao.data || nivel==='impeditivo' — UI reflects backend enforcement without re-implementing business logic
- [33-02]: Prazo interface omits processo_id/tenant_id (snake_case) — backend GET /prazos response map does not include those keys; only camelCase fields returned
- [33-02]: prazosRiscoToVariant/prazosRiscoToLabel placed in lib/prazos.ts as single source of truth for risco->badge mapping (analog to lib/conflict-check.ts)
- [33-02]: useExecutarTransicao invalidates both workflow and movimentacoes caches on success for immediate refresh
- [34-01]: AuditLog.processoId is nullable (no nullable=false) to accommodate document-level audit events not linked to a processo
- [34-01]: ConflictCheckDecisao timeline timestamp uses createdAt (LocalDateTime), not dataDecisao (LocalDate) — avoids ClassCastException in sort
- [34-01]: getTimeline() never queries auditLogRepository — operational history (Timeline) vs compliance trail (Audit) are separate surfaces
- [34-02]: useAuditLog staleTime set to 30_000 (vs 15_000 for useTimeline) — audit log is compliance trail; longer cache reduces API calls without staleness impact
- [34-02]: Timeline invalidation in useAddProcessoMovimentacao added as sequential await (not merged into Promise.all) — minimal change preserving existing code structure

### Pending Todos

None yet.

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-06-16T19:15:00Z
Stopped at: Completed Phase 34, Plan 02 — TypeScript types (TimelineItem, TimelineItemType, AuditLogEntry), useTimeline and useAuditLog hooks, extended mutation invalidations
Resume file: None

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
