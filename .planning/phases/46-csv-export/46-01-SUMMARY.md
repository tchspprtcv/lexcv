---
phase: 46-csv-export
plan: "01"
subsystem: frontend/financeiro
tags: [csv-export, financeiro, client-side, download]
dependency_graph:
  requires: []
  provides: [FIN-17]
  affects: [web/src/app/(dashboard)/financeiro/page.tsx]
tech_stack:
  added: []
  patterns: [client-side CSV generation, Blob/URL.createObjectURL, UTF-8 BOM]
key_files:
  created: []
  modified:
    - web/src/app/(dashboard)/financeiro/page.tsx
decisions:
  - Função exportHonorariosCsv adicionada ao nível do módulo (fora de componentes) para facilitar testes e manter JSX limpo
  - Botão "Exportar CSV" colocado dentro de FinanceiroContent (onde filteredList está disponível) e não em FinanceiroPage
  - canViewFinanceiro propagado como prop para FinanceiroContent para guardar o botão de exportação
metrics:
  duration: "5 min"
  completed: "2026-06-18"
  tasks_completed: 1
  tasks_total: 1
  files_modified: 1
---

# Phase 46 Plan 01: CSV Export para Financeiro — Summary

Client-side CSV export com BOM UTF-8 para a página de honorários, exportando filteredList com 7 campos e compatibilidade Excel garantida.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Adicionar exportHonorariosCsv e botão Exportar CSV | 3323c79 | web/src/app/(dashboard)/financeiro/page.tsx |

## What Was Built

Dois acréscimos ao ficheiro `web/src/app/(dashboard)/financeiro/page.tsx`:

1. **Função `exportHonorariosCsv`** (nível de módulo, após `calcHonorarioStatus`):
   - Parâmetros: `rows: HonorarioRow[]`, `processoById: Map<string, ProcessoRef>`, `clienteNomeById: Map<string, string>`
   - Cabeçalho CSV: `ID, Processo, Cliente, Valor Total, Total Pago, Estado, Data do Acordo`
   - Escape de campos com vírgulas, aspas ou newlines (RFC 4180)
   - Prefixo BOM UTF-8 (`﻿`) para compatibilidade com Excel no Windows
   - Download via `Blob` + `URL.createObjectURL` + elemento `<a>` programático
   - Nome do ficheiro: `honorarios-YYYY-MM-DD.csv` com data atual

2. **Botão "Exportar CSV"** no header da página:
   - Visível apenas quando `filteredList.length > 0`
   - Guardado por `canViewFinanceiro` (quem pode ver pode exportar)
   - `variant="outline"` alinhado com o botão "Limpar filtros" existente
   - Envolto num `div` com `flex items-center gap-2` juntamente com "Novo honorário"

## Deviations from Plan

Nenhum — plano executado exatamente como escrito.

## Verification

`pnpm build` concluiu sem erros TypeScript nem de compilação. Todas as 25 rotas compiladas corretamente incluindo `/financeiro`.

## Known Stubs

Nenhum — a função exporta dados reais de `filteredList` que é alimentada pelos hooks `useHonorarios`, `useProcessos` e `useClientes`.

## Threat Flags

Nenhum — sem nova superfície de rede, auth ou acesso a ficheiros no servidor. A exportação é inteiramente client-side.

## Self-Check: PASSED

- [x] `web/src/app/(dashboard)/financeiro/page.tsx` modificado e existente
- [x] Commit `3323c79` existe em `git log`
- [x] `pnpm build` passou sem erros
- [x] `exportHonorariosCsv` presente no ficheiro com prefixo BOM
- [x] Botão "Exportar CSV" condicionado a `filteredList.length > 0`
