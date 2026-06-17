---
phase: 42-filtros-categorias-e-vis-o-unificada
plan: "01"
subsystem: agenda
tags: [agenda, filters, unified-view, deadlines, processes]
dependency_graph:
  requires: [Phase 41]
  provides: [unified-agenda-calendar-view]
  affects: [web/, backend/]
tech_stack:
  added: []
  patterns: [unified list mapping, local filter predicates, loading spinners, conditional route navigation]
key_files:
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - web/src/types/processos.ts
    - web/src/hooks/use-processos.ts
    - web/src/app/(dashboard)/agenda/page.tsx
decisions:
  - "Added backend GET /prazos endpoint to return all deadlines of the current tenant, exposing the mapped processoId"
  - "Mapped Prazo objects into virtual Evento structures inside the frontend monthly calendar to render them uniformly"
  - "Added selectedProcessoId, selectedCategoria, selectedConcluido React states to implement client-side interactive filtering"
  - "Linked virtual deadline elements to their process detail pages (/processos/{id}) and standard events to their details pages (/agenda/{id})"
  - "Replaced static loading placeholders in the monthly calendar with dynamic Loader2 animated spinner component"
metrics:
  duration: "~20 minutes"
  completed: "2026-06-17"
  tasks_completed: 4
  tasks_total: 4
  files_created: 0
  files_modified: 4
---

# Phase 42 Plan 01: Filtros, Categorias e Visão Unificada Summary

**One-liner:** Exposição global de prazos no backend, unificação de eventos e prazos no calendário mensal da agenda com filtros avançados e spinners de carregamento dinâmico.

## What Was Built

### Task 1 — Global Backend deadines endpoint (`ResourceController.java`)

- Adicionado endpoint `@GetMapping("/prazos")` (`listAllPrazos`) que retorna a lista de todos os prazos (`Prazo`) do tenant, populando o campo `processoId` nos objetos retornados.

### Task 2 — Frontend Types & Hooks Refactoring

- **Processos Types (`web/src/types/processos.ts`)**: Adicionados os atributos opcionais `processoId?: string` e `tenantId?: string` na interface de tipos `Prazo`.
- **Processos Hooks (`web/src/hooks/use-processos.ts`)**: Criado o hook `useAllPrazos` para consultar o endpoint global `/prazos`.

### Task 3 — Agenda page filters and unified view (`web/src/app/(dashboard)/agenda/page.tsx`)

- **State**: Adicionados hooks `useState` para controlar os filtros de:
  - `Processo` (filtrando por ID de processo)
  - `Categoria` (filtrando por Categoria de Evento: prazos, audiências, diligências, reuniões)
  - `Estado` (filtrando por Concluído ou Pendente)
- **Unificação**: Criado o array memorizado `allUnifiedEvents` que combina o resultado de `useEventos` e `useAllPrazos`. Os prazos são mapeados como estruturas virtuais com tipo `"PRAZO"`.
- **Filtros e Renderização**: O calendário mensal renderiza o array filtrado `filteredEvents` no grid de dias. Os prazos redirecionam o clique para a página do processo (`/processos/[id]`) e os eventos padrão redirecionam para a página de detalhes do evento (`/agenda/[id]`).
- **Loaders e Erros**: Substituída a string estática "A carregar..." por uma animação centralizada contendo a insígnia `Loader2` de carregamento dinâmico.

## Deviations from Plan

Nenhuma desviação — todas as metas foram totalmente atingidas.

## Commits

As alterações serão submetidas no fechamento da fase.

## Self-Check: PASSED

- API `/prazos` compila e expõe dados: PASS
- Zod, Next.js e TypeScript compilam com sucesso: PASS
- Calendário unifica prazos e eventos respondendo aos filtros: PASS
