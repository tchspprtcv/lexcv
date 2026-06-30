---
gsd_state_version: 1.0
milestone: v2.4
milestone_name: Ficha de Cliente
status: milestone_complete
last_updated: "2026-06-30T14:30:00.000Z"
last_activity: 2026-06-30 -- Phase 60 complete (verified, security-audited); all 4 phases of milestone v2.4 done
progress:
  total_phases: 15
  completed_phases: 15
  total_plans: 33
  completed_plans: 31
  percent: 100
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-21)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Milestone v2.4 (Ficha de Cliente) complete — ready for milestone audit/completion

## Current Position

Phase: 60 (ficha-imprimivel) — COMPLETE
Plan: 2 of 2
Status: Phase 60 complete — goal verified (8/8 must-haves), security audited (7/7 threats closed), code reviewed (0 critical)
Last activity: 2026-06-30 -- Phase 60 closed out (verification + security audit + code review); milestone v2.4 fully delivered

Progress: [██████████] 100%

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
| 60 | 2 | ~25 min | ~12 min |

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
- (v2.4/60) Ficha imprimível de alta fidelidade ao formulário físico — rota dedicada /clientes/[id]/ficha, @media print scoped via dangerouslySetInnerHTML de CSS estático, window.print() sem dependência de PDF externa
- (v2.4/60) Acesso à ficha via botão na ficha de detalhe + ícone na listagem desktop (kebab menu original substituído por botão directo Printer, consistente com padrão Eye/Pencil/Trash2 existente)

### Pending Todos

- Plan Phase 53 (Shell Responsivo): dashboard-shell.tsx + sidebar + top bar + bottom nav
- Phase 59 follow-up (non-blocking): add content-type allowlist to POST /clientes/{id}/procuracao (59-SECURITY.md WR-02 — currently accepts arbitrary file types)
- Phase 59 follow-up (non-blocking, code review warnings): N+1 query in listClienteAdvogados/Administrativos; duplicate snake_case/camelCase fields in web/src/types/clientes.ts from merge artifacts; untyped date strings in intake list items — see 59-REVIEW.md
- Phase 60 follow-up (non-blocking, code review warnings): "Honorários — Totalidade" not formatted via formatMoneyCVE on ficha print page; isDadosTipoParticular type guard uses key-presence heuristic instead of isEmpresa discriminator; inconsistent blank-placeholder idiom — see 60-REVIEW.md
- Phase 60 follow-up (non-blocking, UX gap noted in 60-SECURITY.md): mobile card view in clientes listing has no ficha/Printer entry point (desktop-only for now)
- Milestone v2.4 ready for /gsd:audit-milestone and /gsd:complete-milestone

### Blockers/Concerns

None.
