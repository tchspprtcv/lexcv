---
phase: 33-processos-workflow-gates-e-prazos
plan: "02"
subsystem: ui
tags: [typescript, tanstack-query, zod, react-hook-form, prazos, workflow, processos]

dependency_graph:
  requires:
    - plan: 33-01
      provides: "Backend endpoints for workflow/transicao/prazos; WorkflowResponse DTO contract; Prazo entity response shape"
  provides:
    - "PrazoRisco, PrazoPrioridade, TransicaoAcao union types"
    - "WorkflowResponse, TransicaoInfo, Prazo, PrazoCreateRequest, TransicaoRequest TypeScript interfaces"
    - "Processo interface enriched with responsavel_id, responsavel_nome, risco_mais_critico, tem_prazo_escalonado"
    - "transicaoJustificativaFormSchema (min 10) and prazoFormSchema Zod schemas"
    - "lib/prazos.ts — single source of truth for risco->badge mapping"
    - "useWorkflow, useExecutarTransicao, usePrazos, useCreatePrazo, useTogglePrazoConcluido hooks"
  affects:
    - 33-03 (UI cards for workflow and prazos consume these types/hooks/helper)

tech-stack:
  added: []
  patterns:
    - "lib/prazos.ts: Record-based risco->variant/label map (same pattern as lib/conflict-check.ts)"
    - "useWorkflow/usePrazos: useQuery with enabled-window-check and staleTime 15000"
    - "useExecutarTransicao: useMutation POST /transicao/{acao} invalidating list+detail+workflow+movimentacoes"
    - "useTogglePrazoConcluido: optimistic setQueryData replacing prazo by id in prazos cache"
    - "ProcessoApi extended with listing-signal fields; normalizeProcesso passes them through"

key-files:
  created:
    - web/src/lib/prazos.ts
  modified:
    - web/src/types/processos.ts
    - web/src/schemas/processos.ts
    - web/src/hooks/use-processos.ts

key-decisions:
  - "Prazo interface omits processo_id and tenant_id (snake_case) — backend response map returns only id, descricao, dataLimite, prioridade, responsavelId, concluido, escalonado, risco, createdAt (VERIFIER W2)"
  - "PrazoRisco type declared before Processo interface so risco_mais_critico field can use it inline"
  - "useExecutarTransicao invalidates both workflow and movimentacoes caches on success — ensures workflow card and timeline both refresh after a state transition"
  - "useTogglePrazoConcluido uses optimistic setQueryData (not invalidate) for immediate toggle feedback"

patterns-established:
  - "All risco->badge mapping must use prazosRiscoToVariant/prazosRiscoToLabel from lib/prazos.ts — no inline mapping in UI components"
  - "Hooks for sub-resources follow [processos, <subresource>, processoId] queryKey pattern"

requirements-completed: [PRC-27, AGD-22]

duration: ~15min
completed: "2026-06-15"
---

# Phase 33 Plan 02: Frontend Data Layer — Workflow e Prazos Summary

**TypeScript types, Zod schemas, lib/prazos.ts helper, and five TanStack Query hooks wiring the Plan 01 workflow/prazos endpoints to the frontend with correct cache invalidation.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-06-15T13:50:00Z
- **Completed:** 2026-06-15T14:05:00Z
- **Tasks:** 2
- **Files modified:** 3 modified, 1 created

## Accomplishments

- Types for Prazo, WorkflowResponse, TransicaoInfo, and all request interfaces mirror the Plan 01 backend contract exactly (camelCase where Spring/Jackson serializes camelCase; snake_case listing-signal fields where the backend returns them in the enriched listProcessos LinkedHashMap)
- Zod schemas enforce justificativa min(10) and required prazo fields; reuse existing optionalTrimmedString
- lib/prazos.ts established as single source of truth for risco->badge variant/label (ok->green, proximo->amber, vencido->red)
- Five hooks cover all five Plan 01 endpoints: GET workflow, POST transicao, GET prazos list, POST prazo, PATCH prazo concluido toggle

## Task Commits

1. **Task 1: Tipos + schemas + helper lib/prazos.ts** - `8462206` (feat)
2. **Task 2: Hooks TanStack Query para workflow, transicao e prazos** - `ed1d47f` (feat)

## Files Created/Modified

- `web/src/types/processos.ts` — Added PrazoRisco/PrazoPrioridade/TransicaoAcao unions; WorkflowResponse, TransicaoInfo, Prazo, PrazoCreateRequest, TransicaoRequest interfaces; Processo extended with responsavel_id, responsavel_nome, risco_mais_critico, tem_prazo_escalonado
- `web/src/schemas/processos.ts` — Added transicaoJustificativaFormSchema (min 10) and prazoFormSchema with exported inferred types
- `web/src/lib/prazos.ts` — Created; exports prazosRiscoToVariant and prazosRiscoToLabel with Record-based maps
- `web/src/hooks/use-processos.ts` — Extended ProcessoApi and normalizeProcesso; added useWorkflow, useExecutarTransicao, usePrazos, useCreatePrazo, useTogglePrazoConcluido

## Decisions Made

- Prazo interface omits processo_id and tenant_id snake_case fields — the backend GET /prazos response map does not include those keys (verifier W2 compliance)
- PrazoRisco type declared at file top (before Processo interface) so risco_mais_critico can reference it without forward-declaration issues
- useExecutarTransicao invalidates both ["processos","workflow",id] and ["processos","movimentacoes",id] caches on transition success, ensuring workflow card and movimentacoes timeline both reflect the new state immediately

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

None.

## Known Stubs

None — this plan delivers pure data-layer contracts (types, schemas, hooks, helper). UI rendering is Plan 03.

## Threat Surface Scan

No new network endpoints, auth paths, or trust-boundary crossings introduced (T-33F-02 satisfied: risco mapping in lib/prazos.ts never recomputes ok/proximo/vencido — it only maps the string the backend returns to a badge variant/label).

## Self-Check

Files exist:
- [x] web/src/lib/prazos.ts
- [x] web/src/types/processos.ts (modified)
- [x] web/src/schemas/processos.ts (modified)
- [x] web/src/hooks/use-processos.ts (modified)

Commits:
- [x] 8462206 — Task 1
- [x] ed1d47f — Task 2

TypeScript: no new errors (pnpm exec tsc --noEmit clean)
Lint: 0 errors, 6 pre-existing warnings in unrelated files

## Self-Check: PASSED
