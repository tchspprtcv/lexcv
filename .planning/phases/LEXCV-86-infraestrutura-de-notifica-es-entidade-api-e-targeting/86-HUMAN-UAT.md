---
status: partial
phase: 86-infraestrutura-de-notifica-es-entidade-api-e-targeting
source: [86-VERIFICATION.md]
started: 2026-07-08T23:01:55Z
updated: 2026-07-08T23:01:55Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Round-trip ao vivo dos 4 endpoints de notificações contra base de dados real
expected: `NotificacaoRepository.buscarPorFiltros` é a primeira combinação native query + `Pageable` + `countQuery` deste código-base — nunca foi executada contra PostgreSQL real (o projeto não tem H2/Testcontainers). Confirmar manualmente: `GET /notificacoes` (com filtros/paginação), `GET /notificacoes/unread-count`, `PATCH /notificacoes/{id}/lida`, `POST /notificacoes/ler-todas` devolvem resultados corretos com dados semeados.
result: [pending]

### 2. Confirmação visual: notificacoes:view no ecrã Admin Settings RBAC
expected: O scope `notificacoes:view` aparece e é alternável (toggle) no ecrã de gestão de RBAC do Admin Settings, para os 4 perfis.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps
