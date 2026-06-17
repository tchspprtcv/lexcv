---
gsd_state_version: 1.0
milestone: v1.10
milestone_name: Finalização Módulo Agendamento
status: planning
stopped_at: "Started Milestone v1.10"
last_updated: "2026-06-17T17:06:06.608Z"
last_activity: 2026-06-17 — Milestone v1.9 completed and archived
progress:
  total_phases: 3
  completed_phases: 3
  total_plans: 3
  completed_plans: 3
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-16)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 42 — Filtros, Categorias e Visão Unificada

## Deferred Items

Items resolved on 2026-06-17:

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 37: docker compose startup + Caddy routing + volume persistence | COMPLETED — deployed via Hostinger VPS connector on HTTP |
| verification_gap | Phase 38: live HTTPS via Let's Encrypt on real domain | SKIPPED — user opted to use public IP address directly |
| verification_gap | Phase 39: end-to-end CI/CD pipeline trigger | COMPLETED — GitHub workflow builds/pushes public images; deployment triggered via VPS API |

Known deferred items: 0 (all resolved)

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-06-17 — Milestone v1.10 started

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
- [38-01]: Caddyfile.prod uses {$DOMAIN_NAME} placeholder with no explicit TLS block — Caddy provisions Let's Encrypt automatically when a hostname (not :80) is configured
- [39-01]: IMAGE_TAG=latest on VPS (not SHA) — docker-compose.prod.yml default is :latest and :latest is always updated by the push step; no need for SHA-based rollback on first deploy
- [39-01]: No --force-recreate in compose up — minimizes downtime per CONTEXT.md decision

### Pending Todos

None yet.

### Blockers/Concerns

None yet.

## Session Continuity

Last session: 2026-06-16T23:55:00.000Z
Stopped at: Completed Phase 39, Plan 01 (tasks 1-2) — GitHub Actions CI/CD workflow deploy.yml and DEPLOYMENT.md secrets section; checkpoint:human-verify awaiting pipeline end-to-end validation
Resume file: None

## Operator Next Steps

- Start the next milestone with /gsd-new-milestone
