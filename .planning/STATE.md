---
gsd_state_version: 1.0
milestone: v2.6
milestone_name: Módulo de Parecer Jurídico — UI
status: ready_to_plan
last_updated: 2026-07-01T11:45:19.702Z
last_activity: 2026-07-01 — Roadmap v2.6 criado (5 fases, 65–69, 12/12 requirements mapeados)
progress:
  total_phases: 5
  completed_phases: 0
  total_plans: 0
  completed_plans: 2
  percent: 0
stopped_at: Phase 65 complete (2/2) — ready to discuss Phase 66
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-07-01)

**Core value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.
**Current focus:** Phase 66 — criação de solicitação

## Current Position

Phase: 66 of 69 (criação de solicitação)
Plan: Not started
Status: Ready to plan
Last activity: 2026-07-01

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 2
- Average duration: —
- Total execution time: —

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 58 | 4 | ~70 min | ~17 min |
| 59 | 6 | ~90 min | ~15 min |
| 60 | 2 | ~25 min | ~12 min |
| 65 | 2 | - | - |

## Accumulated Context

### Decisions

Decisões são registadas em PROJECT.md (Key Decisions).
Recent decisions affecting current work:

- (v2.5 audit) Milestone v2.5 foi deliberadamente backend-only nas 4 fases — módulo de pareceres inutilizável via app até v2.6 entregar a UI. Ver `.planning/v2.5-MILESTONE-AUDIT.md`.
- (v2.6 research) Nenhuma nova dependência necessária — stack existente (TanStack Query, react-hook-form+zod, upload de Documentos) cobre 100% do milestone.
- (v2.6 research) Risco principal: repetir o defeito de casing camelCase/snake_case do v2.4 — entidades Parecer não têm `@JsonProperty`, logo JSON é 100% camelCase; deve ser confirmado por trace de resposta real antes de escrever tipos (Phase 65).
- (v2.6 research) RBAC de `aprovar` requer `pareceres:manage`; `entregar`/`criarVersao` requerem `pareceres:edit` + verificação de instância (ADMIN ou advogado responsável) que `hasScopedPermission` sozinho não expressa — tratado explicitamente na Phase 68.
- (v2.6 roadmap) PARV-05 torna o anexo de versão **obrigatório na UI** — mais restritivo que o backend, que trata como opcional. Decisão desta milestone, não do backend.
- (v2.6 roadmap) Aprovação interna (ADMIN, `pareceres:manage`) fica fora de âmbito nesta milestone (confirmado explicitamente pelo utilizador) — v2.6 cobre criação de versão + entrega direta + vista de entregue. Deferred para v2.7 (PARC-17).
- (v2.6 roadmap) 5 fases derivadas de 12 requisitos, ordem estrita de dependência: (65) fundação read-only, (66) criação de solicitação, (67) versionamento, (68) entrega+vista-entregue+RBAC (fase de maior risco), (69) pesquisa avançada. NOTF-05/06/07 distribuídas pelas fases correspondentes ao evento que as dispara, em vez de uma fase de notificações isolada.

### Pending Todos

- Plan Phase 53 (Shell Responsivo): dashboard-shell.tsx + sidebar + top bar + bottom nav
- Phase 59 follow-up (non-blocking): add content-type allowlist to POST /clientes/{id}/procuracao (59-SECURITY.md WR-02 — currently accepts arbitrary file types)
- Phase 59 follow-up (non-blocking, code review warnings): N+1 query in listClienteAdvogados/Administrativos; duplicate snake_case/camelCase fields in web/src/types/clientes.ts from merge artifacts; untyped date strings in intake list items — see 59-REVIEW.md
- Phase 60 follow-up (non-blocking, code review warnings): "Honorários — Totalidade" not formatted via formatMoneyCVE on ficha print page; isDadosTipoParticular type guard uses key-presence heuristic instead of isEmpresa discriminator; inconsistent blank-placeholder idiom — see 60-REVIEW.md
- Phase 60 follow-up (non-blocking, UX gap noted in 60-SECURITY.md): mobile card view in clientes listing has no ficha/Printer entry point (desktop-only for now)
- Pre-existing app-wide tenantId/createdAt-style snake_case/camelCase mismatches outside v2.4's scope were identified but intentionally not touched (out of milestone scope, broader blast radius) — candidate for a future cleanup phase.
- Run `/gsd:plan-phase 65` to begin planning Phase 65 of milestone v2.6

### Blockers/Concerns

None.

## Deferred Items

Items acknowledged and deferred at milestone v2.4 close on 2026-06-30:

| Category | Item | Status |
|----------|------|--------|
| verification_gap | Phase 50 (50-VERIFICATION.md) — pre-existing from v2.2, not part of v2.4 | human_needed |
| verification_gap | Phase 51 (51-VERIFICATION.md) — pre-existing from v2.2, not part of v2.4 | human_needed |

Items deferred at milestone v2.5 close (2026-06-30), scoped for v2.6/v2.7:

| Category | Item | Status |
|----------|------|--------|
| scope | UI frontend do módulo de pareceres — v2.5 foi backend-only | addressed_by_v2.6 |
| feature | PARC-17 Aprovação interna (ADMIN) na UI | deferred_to_v2.7 |
| feature | PARV-07 Diff/comparação entre versões | deferred_to_v2.7 |
| feature | PARV-08 Editor de texto formatado (rich text) | deferred_to_v2.7 |

## Operator Next Steps

- Run `/gsd:plan-phase 65` to plan Phase 65 (Fundação — Listagem e Detalhe) of milestone v2.6

</content>
