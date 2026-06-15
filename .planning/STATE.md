---
gsd_state_version: 1.0
milestone: v1.7
milestone_name: Melhoria no modulo de gestao e acompanhamento de processos
status: executing
stopped_at: Completed Phase 33, Plan 02 — frontend data layer for workflow and prazos (types, schemas, lib/prazos.ts, hooks)
last_updated: "2026-06-15T14:05:00Z"
last_activity: 2026-06-15
progress:
  total_phases: 5
  completed_phases: 1
  total_plans: 6
  completed_plans: 5
  percent: 20
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-13)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 33 — Processos - Workflow, Gates e Prazos

## Current Position

Phase: 33 (Processos - Workflow, Gates e Prazos) — EXECUTING
Plan: 3 of 3
Status: Ready to execute
Last activity: 2026-06-15 -- Phase 33 Plan 02 complete (frontend types, schemas, lib/prazos.ts, TanStack Query hooks)

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

### Pending Todos

None yet.

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-06-15T14:05:00Z
Stopped at: Completed Phase 33, Plan 02 — frontend data layer for workflow and prazos (types, schemas, lib/prazos.ts, hooks)
Resume file: None

## Operator Next Steps

- Planear a Phase 32 (/gsd-plan-phase 32) e iniciar execucao do milestone v1.7
