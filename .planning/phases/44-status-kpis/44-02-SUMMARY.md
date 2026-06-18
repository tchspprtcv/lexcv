---
phase: "44-status-kpis"
plan: 02
subsystem: frontend
tags: [financeiro, kpi, status-badge, typescript]
dependency_graph:
  requires: []
  provides: [honorario-status-badge, financeiro-kpi-cards]
  affects: [web/src/types/financeiro.ts, web/src/app/(dashboard)/financeiro/page.tsx]
tech_stack:
  added: []
  patterns: [derived-kpi, status-badge-tailwind]
key_files:
  modified:
    - web/src/types/financeiro.ts
    - web/src/app/(dashboard)/financeiro/page.tsx
decisions:
  - KPI cards renderizam com zeros durante loading (lista vazia) sem spinner extra — comportamento aceitável conforme plano
  - Badge de estado usa span+Tailwind inline sem componente Badge externo — consistente com instrução do plano
metrics:
  duration: "~10min"
  completed: "2026-06-18"
  tasks_completed: 2
  files_modified: 2
---

# Phase 44 Plan 02: Status Badge e KPI Cards no Financeiro — Summary

Badge de estado amber/azul/verde por honorário e quatro cards KPI (Faturado, Recebido, Dívida, Mês) derivados de `useHonorarios()` sem fetch adicional.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Adicionar totalPago ao tipo Honorario | a41cbd9 | web/src/types/financeiro.ts |
| 2 | KPI cards e badge de estado na página financeiro | 9e9694f | web/src/app/(dashboard)/financeiro/page.tsx |

## What Was Built

**Task 1 — `web/src/types/financeiro.ts`**
- Adicionado campo `totalPago: number` à interface `Honorario` imediatamente após `valorTotal`
- `HonorarioCreateRequest` e `HonorarioUpdateRequest` não alterados (campo é read-only do backend)

**Task 2 — `web/src/app/(dashboard)/financeiro/page.tsx`**
- Helper `calcHonorarioStatus(totalPago, valorTotal)` que devolve `"Pendente" | "Parcialmente Pago" | "Pago"`
- Mapa `statusBadgeClass` com classes Tailwind: amber para Pendente, blue para Parcialmente Pago, green para Pago
- Cálculo de quatro KPIs dentro de `FinanceiroContent` derivados de `list = honorarios.data ?? []`:
  - `kpiFaturado` = sum(valorTotal)
  - `kpiRecebido` = sum(totalPago)
  - `kpiDivida` = kpiFaturado - kpiRecebido
  - `kpiMes` = sum(totalPago) de honorários com `dataAcordo` no ano-mês corrente
- Grid de 4 KPI cards (`grid-cols-2 sm:grid-cols-4`) com `Card/CardHeader/CardTitle/CardContent` e `formatMoneyCVE`
- Coluna "Estado" adicionada ao `<thead>` e célula `<td>` com `<span>` badge em cada linha do `<tbody>`

## Deviations from Plan

None — plano executado exactamente como escrito.

## Verification

- `pnpm tsc --noEmit`: passou sem erros (ambas as tarefas)
- `pnpm build`: build completo bem-sucedido, página `/financeiro` compilada sem erros de tipo

## Known Stubs

None — todos os valores são derivados dos dados reais de `useHonorarios()`.

## Threat Flags

None — nenhuma nova superfície de segurança introduzida. Os KPI cards ficam atrás do gate `financeiro:view` já existente.

## Self-Check: PASSED

- Commits a41cbd9 e 9e9694f existem no histórico git
- web/src/types/financeiro.ts contém `totalPago: number`
- web/src/app/(dashboard)/financeiro/page.tsx contém `calcHonorarioStatus`, `statusBadgeClass`, `kpiFaturado`, `kpiRecebido`, `kpiDivida`, `kpiMes`, grid de 4 cards e coluna Estado
