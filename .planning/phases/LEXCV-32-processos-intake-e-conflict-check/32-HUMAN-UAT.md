---
status: partial
phase: 32-processos-intake-e-conflict-check
source: [32-VERIFICATION.md]
started: 2026-06-13T00:00:00Z
updated: 2026-06-13T00:00:00Z
---

## Current Test

[awaiting human testing — requires running backend + PostgreSQL + seeded users]

## Tests

### 1. End-to-end intake wizard smoke test
expected: Em /processos/novo, o utilizador percorre os 3 passos (Intake -> Conflict Check -> Abertura); o processo nasce em estado TRIAGEM e so formaliza apos decisao registada.
result: [pending]

### 2. Impeditivo bloqueia formalizacao
expected: Quando a decisao de conflito e `impeditivo`, o botao Formalizar fica desabilitado (UI) e o endpoint /formalizar devolve 409 (backend).
result: [pending]

### 3. Campos minimos em falta -> 422
expected: Formalizar um processo a que faltam campos minimos do tipo devolve 422 com a lista `camposEmFalta`; a UI mostra a mensagem inline.
result: [pending]

### 4. Badge e filtro EM TRIAGEM na listagem
expected: A listagem de processos mostra o badge roxo "EM TRIAGEM" e o filtro por estado "Em triagem" funciona.
result: [pending]

### 5. Isolamento multi-tenant no conflict check
expected: O conflict check so devolve correspondencias de clientes/partes do tenant atual; nenhum dado de outro tenant aparece.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
