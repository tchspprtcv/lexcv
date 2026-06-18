---
phase: 45-filtros-edit-delete-ui
plan: "01"
subsystem: frontend/financeiro
tags: [filters, client-side, honorarios, financeiro]
dependency_graph:
  requires: []
  provides: [filtros-honorarios-client-side]
  affects: [web/src/app/(dashboard)/financeiro/page.tsx]
tech_stack:
  added: []
  patterns: [React.useState for filter state, derived filteredList via chained .filter()]
key_files:
  modified:
    - web/src/app/(dashboard)/financeiro/page.tsx
decisions:
  - Filtros client-side sobre dados já carregados (sem novas chamadas à API), conforme decisão em CONTEXT.md
  - KPIs usam list (dados completos) para não distorcer totais ao filtrar
  - filtroStatus tipado como union literal para type-safety
metrics:
  duration: ~8 min
  completed: "2026-06-18T16:00:53Z"
  tasks_completed: 1
  tasks_total: 1
  files_changed: 1
---

# Phase 45 Plan 01: Filtros Client-Side Honorários Summary

Barra de filtros client-side adicionada à lista de honorários com quatro filtros combinados (processo, estado, data de, data até) e botão condicional "Limpar filtros".

## Tasks

| # | Nome | Commit | Status |
|---|------|--------|--------|
| 1 | Adicionar estado de filtros e lista filtrada em FinanceiroContent | a62496e | Concluído |

## What Was Built

Adicionada barra de filtros em `web/src/app/(dashboard)/financeiro/page.tsx`:

- Quatro estados React: `filtroProcesso` (string), `filtroStatus` (union literal), `filtroDataDe` (string), `filtroDataAte` (string)
- `filteredList` derivado de `honorarios.data` com AND lógico dos filtros ativos
- `list` mantido separado para os KPIs (Total Faturado, Total Recebido, Em Dívida, Receita do Mês) — não afetados pelos filtros
- Barra de filtros entre os cards de KPI e o card da tabela: select de processo (populado de `processos.data`), select de estado com os três valores possíveis, dois inputs `type="date"`
- Botão "Limpar filtros" visível apenas quando pelo menos um filtro está ativo
- Mensagem diferenciada: "Nenhum honorário encontrado." (lista vazia) vs "Nenhum honorário corresponde aos filtros aplicados." (filtros sem resultados)
- Tabela renderiza `filteredList.map(...)` em vez de `honorarios.data.map(...)`

## Verification

- `pnpm build` passou sem erros de TypeScript nem de compilação
- `filtroStatus` tipado como `"" | "Pendente" | "Parcialmente Pago" | "Pago"` (não string genérico)
- KPIs usam `list` (honorarios.data ?? []), não `filteredList`

## Deviations from Plan

Nenhuma — plano executado exatamente como escrito.

## Known Stubs

Nenhum.

## Threat Flags

Nenhuma nova superfície identificada além do threat model do plano. Filtros são estado local; nenhum dado adicional exposto ao browser.

## Self-Check: PASSED

- `web/src/app/(dashboard)/financeiro/page.tsx` modificado: FOUND
- Commit a62496e: FOUND (`git log --oneline -1` → `a62496e feat(45-01): ...`)
