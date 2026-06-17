---
phase: 40-mapeamento-e-alinhamento-casing-agenda
plan: "01"
subsystem: agenda
tags: [agenda, events, refactor, casing, camelcase]
dependency_graph:
  requires: [Phase 39]
  provides: [camelcase-eventos-data-layer]
  affects: [web/]
tech_stack:
  added: []
  patterns: [camelCase properties, local ISO-8601 formatting, timezone offset stripping]
key_files:
  modified:
    - web/src/types/eventos.ts
    - web/src/schemas/eventos.ts
    - web/src/hooks/use-eventos.ts
    - web/src/app/(dashboard)/agenda/page.tsx
    - web/src/app/(dashboard)/agenda/[id]/page.tsx
    - web/src/app/(dashboard)/agenda/[id]/editar/page.tsx
    - web/src/app/(dashboard)/agenda/novo/page.tsx
    - web/src/app/(dashboard)/dashboard/page.tsx
    - web/tsconfig.json
decisions:
  - "TypeScript interfaces, Zod schemas, React Query hooks and agenda pages aligned to camelCase to match Jackson serialization"
  - "Timezone offset details stripped from date inputs and serialized as YYYY-MM-DDTHH:mm:ss to prevent Jackson/Spring Boot LocalDateTime parsing exceptions"
  - "Private _api-backup route handlers folder excluded from TypeScript compilation in tsconfig.json to prevent compilation errors from unused backup code"
metrics:
  duration: "~30 minutes"
  completed: "2026-06-17"
  tasks_completed: 4
  tasks_total: 4
  files_created: 0
  files_modified: 9
---

# Phase 40 Plan 01: Mapeamento e Alinhamento Casing (Agenda) Summary

**One-liner:** Refatoração completa do data layer e das páginas do módulo de agenda do LexCV de snake_case para camelCase, com tratamento de fuso horário em campos de data, alinhando com a serialização padrão Jackson/Spring Boot do backend.

## What Was Built

### Task 1 — Refatoração de Tipos e Interfaces (`web/src/types/eventos.ts`)

- Atualizados os tipos de `Evento`, `EventoCreateRequest` e `EventoUpdateRequest` para usarem camelCase nos campos:
  - `data_inicio` -> `dataInicio`
  - `data_fim` -> `dataFim`
  - `processo_id` -> `processoId`
  - `tenant_id` -> `tenantId`

### Task 2 — Refatoração dos Zod Schemas (`web/src/schemas/eventos.ts`)

- Atualizados `eventoFormSchema` e `eventoFiltroSchema` para usarem chaves camelCase (`processoId`, `dataInicio`, `dataFim`) e tipos correspondentes de `EventoFormValues` e `EventoFiltroValues`.

### Task 3 — Refatoração de Hooks e Query Keys (`web/src/hooks/use-eventos.ts`)

- Atualizado `useEventos`, `useCreateEvento`, `useUpdateEvento`, `useToggleEventoConcluido` e `useSetEventoConcluido` para passar parâmetros camelCase na URL e enviar corpos JSON em camelCase para a API.
- Implementada a função `normalizeDateParam` que normaliza as datas cortando as informações de offset/fuso horário (e.g. `.slice(0, 19)`).

### Task 4 — Refatoração das Páginas de Agenda, Dashboard e Tsconfig

- **Agenda List (`agenda/page.tsx`)**: Atualizada para referenciar `e.dataInicio` e `e.processoId`.
- **Agenda Detail (`agenda/[id]/page.tsx`)**: Atualizada para ler `evento.data.dataInicio`, `evento.data.dataFim` e `evento.data.processoId`.
- **Agenda Edit (`agenda/[id]/editar/page.tsx`)**: Atualizados os defaultValues do React Hook Form, o reset no `useEffect`, a serialização no `onSubmit` (cortando o offset usando `.slice(0, 19)`), e os mapeamentos no formulário HTML.
- **Agenda Novo (`agenda/novo/page.tsx`)**: Idêntico à página de edição, o formulário de criação de novos eventos foi alinhado ao camelCase e formata as datas no formato LocalDateTime esperado (`YYYY-MM-DDTHH:mm:ss`).
- **Dashboard Page (`dashboard/page.tsx`)**: Corrigidos erros de compilação nos widgets de prazos urgentes substituindo `e.processo_id` e `e.data_inicio` por `e.processoId` e `e.dataInicio`.
- **TypeScript Configuration (`tsconfig.json`)**: Adicionada a pasta `src/app/_api-backup` na diretiva `exclude` para evitar que rotas legadas e não utilizadas (com tipagem antiga) quebrem o build do Next.js.

## Deviations from Plan

Nenhuma desviação — o plano foi executado na íntegra. Os erros do compilador Next.js na página do Dashboard e na pasta de backup foram identificados e corrigidos durante a execução da Task 4.

## Commits

As alterações serão submetidas no fechamento da fase.

## Self-Check: PASSED

- Todas as interfaces de tipo compilam e usam camelCase: PASS
- Os schemas Zod validam inputs camelCase: PASS
- `pnpm --filter web build` corre com sucesso: PASS
