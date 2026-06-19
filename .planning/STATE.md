---
gsd_state_version: 1.0
milestone: v2.2
milestone_name: Document Storage MinIO
status: planning
stopped_at: "Defining requirements"
last_updated: "2026-06-19T00:00:00.000Z"
last_activity: 2026-06-19 — Milestone v2.2 started
progress:
  total_phases: 0
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-19)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Milestone v2.2 — Document Storage MinIO

## Current Position

Phase: Not started (defining requirements)
Plan: —
Status: Defining requirements
Last activity: 2026-06-19 — Milestone v2.2 started

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

## Accumulated Context

### Decisions

Decisions são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v2.1) Recorrência infinita fora de scope — data de fim obrigatória
- (v1.8) Caddy como reverse proxy com HTTPS automático — continuar o mesmo padrão para MinIO console
- (v1.8) Docker Compose + GitHub Actions → GHCR → SSH VPS — padrão de deploy a estender para MinIO

### Pending Todos

None yet.

### Blockers/Concerns

- Migração de ficheiros existentes (uploads/ → MinIO) não está em scope; ficheiros históricos ficarão apenas no filesystem até decisão futura.
