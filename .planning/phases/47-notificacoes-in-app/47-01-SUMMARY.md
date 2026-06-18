---
phase: "47-notificacoes-in-app"
plan: 1
subsystem: backend
tags: [evento, agenda, notificacoes, endpoint, slim-dto]
dependency_graph:
  requires: []
  provides: [GET /api/v1/eventos/upcoming]
  affects: [ResourceController, agenda:view scope]
tech_stack:
  added: []
  patterns: [in-memory filter, slim DTO via Map.of + stream]
key_files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
decisions:
  - "Usou filtragem in-memory (padrão existente de listEventos) em vez de JPQL para manter consistência"
  - "days limitado a 30 por segurança — parâmetro controlado pelo servidor"
  - "Map.of com fallback para string vazia em campos nullable (processoId, tipo) para evitar NullPointerException em Map.of"
metrics:
  duration: "5min"
  completed: "2026-06-18"
  tasks_completed: 1
  tasks_total: 1
  files_modified: 1
---

# Phase 47 Plan 01: Backend GET /eventos/upcoming Summary

**One-liner:** Endpoint slim GET /eventos/upcoming com filtragem in-memory por tenant, janela de N dias, ordenado por dataInicio, máximo 10 itens.

## What Was Built

Adicionado o método `getUpcomingEventos` ao `ResourceController` imediatamente antes do endpoint `GET /eventos/{id}`.

- Rota: `GET /api/v1/eventos/upcoming?days=N` (default 7, max 30)
- Autorização: `@PreAuthorize("hasAuthority('agenda:view')")`
- Validação: `days <= 0` retorna HTTP 400 com mensagem em português
- Filtragem: tenantId do contexto de segurança, `concluido=false`, `dataInicio` entre `now` e `now+days`
- Ordenação: `dataInicio` ascendente
- Limite: 10 itens máximo
- Resposta slim: `{ id, titulo, dataInicio (ISO-8601), processoId (string ou ""), tipo (string ou "") }`

## Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Implementar GET /eventos/upcoming | 690f52c | ResourceController.java |

## Deviations from Plan

None — plano executado exatamente como escrito.

## Self-Check: PASSED

- [x] `690f52c` existe em git log
- [x] `ResourceController.java` contém `@GetMapping("/eventos/upcoming")`
- [x] Build Maven concluiu com BUILD SUCCESS (sem output = sucesso com -q)
