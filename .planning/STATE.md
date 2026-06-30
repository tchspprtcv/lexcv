---
gsd_state_version: 1.0
milestone: v2.5
milestone_name: Modulo de Parecer Juridico
status: executing
last_updated: "2026-06-30T23:48:41.552Z"
last_activity: 2026-06-30 -- Phase 63 planning complete
progress:
  total_phases: 4
  completed_phases: 2
  total_plans: 5
  completed_plans: 4
  percent: 50
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-21)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 62 — Elaboração e Versionamento

## Current Position

Phase: 62 (Elaboração e Versionamento) — EXECUTING
Plan: 1 of 2
Status: Ready to execute
Last activity: 2026-06-30 -- Phase 63 planning complete

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
- (v2.4/audit) Milestone audit found snake_case/camelCase JSON contract mismatch breaking 9/19 requirements at the display layer (data persisted correctly, never rendered) + a password-hash leak on 2 new endpoints. Fixed via scoped @JsonProperty annotations (not a global naming-strategy change, to limit blast radius) — see v2.4-MILESTONE-AUDIT.md remediation section, commit ac5ae75.
- (v2.5 roadmap) Reutilizar Cliente, User+role ADVOGADO, AuditLog e StorageService existentes — apenas ParecerSolicitacao (t_parecer_solicitacao) e ParecerVersao (t_parecer_versao) são entidades novas, ambas tenant-scoped
- (v2.5 roadmap) 4 fases derivadas de 17 requisitos: (61) data layer + CRUD + RBAC, (62) elaboração/versionamento, (63) aprovação/entrega, (64) auditoria + pesquisa avançada — cada fase entrega um incremento verificável de ponta a ponta
- (v2.5 roadmap) Decisão de implementação (ControllerC dedicado `ParecerController` vs. extensão do `ResourceController` ~2300 linhas) deixada para o plano da Phase 61 — não é uma decisão de roadmap

### Pending Todos

- Plan Phase 53 (Shell Responsivo): dashboard-shell.tsx + sidebar + top bar + bottom nav
- Phase 59 follow-up (non-blocking): add content-type allowlist to POST /clientes/{id}/procuracao (59-SECURITY.md WR-02 — currently accepts arbitrary file types)
- Phase 59 follow-up (non-blocking, code review warnings): N+1 query in listClienteAdvogados/Administrativos; duplicate snake_case/camelCase fields in web/src/types/clientes.ts from merge artifacts; untyped date strings in intake list items — see 59-REVIEW.md
- Phase 60 follow-up (non-blocking, code review warnings): "Honorários — Totalidade" not formatted via formatMoneyCVE on ficha print page; isDadosTipoParticular type guard uses key-presence heuristic instead of isEmpresa discriminator; inconsistent blank-placeholder idiom — see 60-REVIEW.md
- Phase 60 follow-up (non-blocking, UX gap noted in 60-SECURITY.md): mobile card view in clientes listing has no ficha/Printer entry point (desktop-only for now)
- **Recommended before production**: live smoke test of the 5 flows broken by the audit (create Particular/Empresa client, upload procuração, fill intake fields, print ficha) against a running backend+DB+MinIO stack — the fix was statically verified (field-by-field JSON key trace + clean builds) but not live-tested in this session.
- Pre-existing app-wide tenantId/createdAt-style snake_case/camelCase mismatches outside v2.4's scope were identified but intentionally not touched (out of milestone scope, broader blast radius) — candidate for a future cleanup phase.
- Milestone v2.4 ready for /gsd:audit-milestone and /gsd:complete-milestone (if not already run)
- Milestone v2.5: run /gsd:plan-phase 61 to begin planning Data Layer + Backend CRUD

### Blockers/Concerns

None.

## Deferred Items

Items acknowledged and deferred at milestone v2.4 close on 2026-06-30:

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 50 (50-VERIFICATION.md) — pre-existing from v2.2, not part of v2.4 | human_needed |
| verification_gap | Phase 51 (51-VERIFICATION.md) — pre-existing from v2.2, not part of v2.4 | human_needed |

## Operator Next Steps

- Run `/gsd:plan-phase 61` to plan Phase 61 (Data Layer + Backend CRUD) of milestone v2.5
