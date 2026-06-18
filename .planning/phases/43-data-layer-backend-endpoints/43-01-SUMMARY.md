---
phase: 43-data-layer-backend-endpoints
plan: "01"
subsystem: frontend/financeiro
tags: [camelCase, types, hooks, forms, financeiro]
dependency_graph:
  requires: []
  provides: [financeiro-types-camelcase, financeiro-hooks-mutations]
  affects: [web/src/types/financeiro.ts, web/src/schemas/financeiro.ts, web/src/hooks/use-financeiro.ts]
tech_stack:
  added: []
  patterns: [camelCase field alignment with Spring Boot Jackson defaults]
key_files:
  created: []
  modified:
    - web/src/types/financeiro.ts
    - web/src/schemas/financeiro.ts
    - web/src/hooks/use-financeiro.ts
    - web/src/app/(dashboard)/financeiro/page.tsx
    - web/src/app/(dashboard)/financeiro/[id]/page.tsx
    - web/src/app/(dashboard)/financeiro/novo/page.tsx
decisions:
  - Kept processo_id as query param name in useHonorarios URL (backend param name, out of scope)
  - Kept Processo.cliente_id as-is (Processo type migration is out of scope for this plan)
  - Removed created_at column and row entirely (field does not exist on Honorario JPA model)
metrics:
  duration: "~10 minutes"
  completed: "2026-06-18"
  tasks_completed: 2
  tasks_total: 2
---

# Phase 43 Plan 01: Financeiro camelCase Migration Summary

Migrated the financeiro module frontend from incorrect snake_case field names to camelCase, aligning the TypeScript contract with what the Spring Boot backend actually serializes. Added three new mutation hooks required by Plan 02.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Migrate TypeScript types and Zod schemas to camelCase | b6199d0 | financeiro.ts, financeiro.ts (schemas) |
| 2 | Update hooks and page components to camelCase + add new mutation hooks | e8dc45b | use-financeiro.ts, 3 page components |

## What Was Built

**Types (web/src/types/financeiro.ts):**
- `Honorario`: id, processoId, valorTotal, descricao?, dataAcordo? — tenant_id and created_at removed
- `HonorarioCreateRequest`: processoId, valorTotal, descricao?, dataAcordo?
- `HonorarioUpdateRequest` (new): valorTotal, descricao?, dataAcordo?
- `Pagamento`: id, honorarioId, valorPago, dataPagamento, metodo? — tenant_id removed
- `PagamentoCreateRequest`: honorarioId, valorPago, dataPagamento?, metodo?

**Schemas (web/src/schemas/financeiro.ts):**
- `honorarioFormSchema`: keys renamed processoId, valorTotal, dataAcordo
- `pagamentoFormSchema`: keys renamed valorPago, dataPagamento

**Hooks (web/src/hooks/use-financeiro.ts):**
- Fixed `useCreatePagamento` onSuccess: `variables.honorario_id` → `variables.honorarioId`
- Added `useUpdateHonorario`: PUT /honorarios/${id}, invalidates list + detail
- Added `useDeleteHonorario`: DELETE /honorarios/${id}, invalidates list
- Added `useDeletePagamento`: DELETE /pagamentos/${pagamentoId}, invalidates pagamentos + conta-corrente

**Pages:**
- `financeiro/page.tsx`: camelCase field access, removed "Criado" column (created_at doesn't exist)
- `financeiro/[id]/page.tsx`: camelCase fields, removed "Criado" dt/dd row, form fields renamed
- `financeiro/novo/page.tsx`: form defaultValues, payload, register calls, and htmlFor all camelCase

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — all field references are wired to real API data.

## Threat Flags

None — no new network endpoints or auth paths introduced. tenantId removed from Honorario interface per T-43-01-01 mitigation.

## Self-Check: PASSED

- web/src/types/financeiro.ts: exists, contains processoId, valorTotal, honorarioId, valorPago
- web/src/schemas/financeiro.ts: exists, contains processoId, valorPago keys
- web/src/hooks/use-financeiro.ts: exports useUpdateHonorario, useDeleteHonorario, useDeletePagamento
- pnpm build: exits 0, all financeiro routes compiled successfully
- Commits b6199d0 and e8dc45b verified in git log
