---
phase: 41-valida-o-de-intervalo-e-tratamento-de-erros
plan: "01"
subsystem: agenda
tags: [agenda, validation, dates, backend, spring-boot]
dependency_graph:
  requires: [Phase 40]
  provides: [robust-date-range-validation]
  affects: [web/, backend/]
tech_stack:
  added: []
  patterns: [Zod object refinements, LocalDateTime parsing validation, JSON error toasts]
key_files:
  modified:
    - web/src/schemas/eventos.ts
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
    - web/src/lib/api.ts
decisions:
  - "Zod schema updated with a refinement block to prevent dataFim being before dataInicio"
  - "Spring Boot ResourceController updated to reject event creation and updates if the resulting date range is invalid (dataFim before dataInicio), returning HTTP 400"
  - "Robust ISO-8601 parsing implemented for listEventos query parameters, returning BAD_REQUEST (HTTP 400) if date values are invalid instead of silently ignoring errors"
  - "apiFetch modified to parse JSON error responses and display custom backend error messages in toast notifications"
metrics:
  duration: "~15 minutes"
  completed: "2026-06-17"
  tasks_completed: 3
  tasks_total: 3
  files_created: 0
  files_modified: 3
---

# Phase 41 Plan 01: Validação de Intervalo e Tratamento de Erros Summary

**One-liner:** Validação robusta de intervalos de datas no frontend e no backend, com tratamento de exceções de parsing de data no Spring Boot e exibição de toasts de erro detalhados no Next.js.

## What Was Built

### Task 1 — Zod Date Range Validation (`web/src/schemas/eventos.ts`)

- Adicionado refinamento `.refine()` no `eventoFormSchema` para verificar se `dataFim` >= `dataInicio`. Caso contrário, retorna a mensagem `"A data de fim não pode ser anterior à data de início"` associada ao campo `dataFim`.

### Task 2 — Backend Range Validation and Query Parameter Parsing (`ResourceController.java`)

- **List (`/eventos`)**: Agora valida se os parâmetros de consulta `dataInicio` e `dataFim` são ISO-8601 válidos. Se o parsing lançar uma exceção, retorna HTTP 400 (`BAD_REQUEST`) com mensagem informativa. Se ambos forem válidos e `dataFim` for anterior a `dataInicio`, também retorna HTTP 400.
- **Create (`POST /eventos`)**: Valida se `dataFim` é anterior a `dataInicio`. Em caso positivo, rejeita a criação com HTTP 400 e a mensagem `"A data de fim não pode ser anterior à data de início"`.
- **Update (`PUT /eventos/{id}`)**: Mesma validação acima, combinando os valores informados no payload com os já existentes no banco de dados para evitar faixas de datas inválidas.

### Task 3 — Toast Error Message Improvement (`web/src/lib/api.ts`)

- Atualizada a função `apiFetch` para que, ao receber um erro do servidor (HTTP status diferente de 200/204/401/403), tente interpretar o corpo como JSON e extrair os campos `.message` ou `.error`. Isso garante que as mensagens detalhadas retornadas pelo Spring Boot apareçam diretamente nos alertas visualizados pelo usuário.

## Deviations from Plan

Nenhuma desviação — o plano foi executado de forma idêntica à proposta original.

## Commits

As alterações serão submetidas no fechamento da fase.

## Self-Check: PASSED

- Zod schema valida intervalos: PASS
- Modificações no Spring Boot compilam com sucesso: PASS
- `apiFetch` manipula e exibe erros detalhados em toasts: PASS
