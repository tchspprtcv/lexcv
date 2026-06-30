---
gsd_state_version: 1.0
milestone: v2.4
milestone_name: Ficha de Cliente
status: executing
last_updated: "2026-06-30T13:10:00.000Z"
last_activity: 2026-06-30 -- Phase 59 complete (verified, security-audited); Phase 60 not yet started
progress:
  total_phases: 14
  completed_phases: 13
  total_plans: 31
  completed_plans: 29
  percent: 93
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-21)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 60 — ficha-imprimivel (not yet started)

## Current Position

Phase: 59 (procuracao-intake) — COMPLETE
Plan: 6 of 6
Status: Phase 59 complete — goal verified (5/5 must-haves), security audited (13/13 threats closed, 1 non-blocking WARNING logged for follow-up)
Last activity: 2026-06-30 -- Phase 59 closed out (verification + security audit + tracking update)

Progress: [█████░░░░░] 50%

## Performance Metrics

**Velocity:**

- Total plans completed: 0
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 58 | 4 | ~70 min | ~17 min |
| 59 | 6 | ~90 min | ~15 min |

## Accumulated Context

### Decisions

Decisões são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v2.2) MinIO como object storage para documentos — substituiu filesystem local
- (v2.2) Caddy handle_path para MinIO console — strips prefix corretamente
- (v1.1) shadcn/ui + Tailwind como sistema de design — breakpoints md/lg a usar consistentemente
- (v2.3) Sheet (shadcn) para drawer sidebar em mobile — já disponível em components/ui/
- (v2.3) Fases 54 e 55 paralelas (dependem ambas de Phase 53) — granularidade standard, 4 fases derivadas de 11 requirements em 5 categorias
- (v2.4/59) Procuração não bloqueia submit — D-01: aviso visual ("Procuração em falta") em vez de validação bloqueante
- (v2.4/59) Advogados/administrativos ligados a Users do sistema via tabelas de junção tenant-scoped (t_cliente_advogado, t_cliente_administrativo) com validação de papel server-side
- (v2.4/59) Listas de intake (docs entregues/a tratar, deslocações, honorários propostos) como colunas JSON em t_cliente, mesmo padrão de dados_tipo da Phase 57

### Pending Todos

- Plan Phase 53 (Shell Responsivo): dashboard-shell.tsx + sidebar + top bar + bottom nav
- Phase 59 follow-up (non-blocking): add content-type allowlist to POST /clientes/{id}/procuracao (59-SECURITY.md WR-02 — currently accepts arbitrary file types)
- Phase 59 follow-up (non-blocking, code review warnings): N+1 query in listClienteAdvogados/Administrativos; duplicate snake_case/camelCase fields in web/src/types/clientes.ts from merge artifacts; untyped date strings in intake list items — see 59-REVIEW.md

### Blockers/Concerns

None.
