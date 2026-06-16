---
status: partial
phase: 33-processos-workflow-gates-e-prazos
source: [33-VERIFICATION.md]
started: 2026-06-16T00:00:00Z
updated: 2026-06-16T00:00:00Z
---

## Current Test

[awaiting human testing — requires running backend + PostgreSQL + seeded users]

## Tests

### 1. Workflow card visual
expected: O card de workflow mostra estado/responsavel/proximo passo corretos e apenas as transicoes permitidas pelo backend para o estado atual.
result: [pending]

### 2. Justificativa Dialog gate
expected: Transicoes criticas (suspender/encerrar/reabrir) abrem Dialog; justificativa < 10 chars e bloqueada (RHF + backend 400); valida -> 200.
result: [pending]

### 3. Permission gate
expected: Botoes de transicao critica desabilitados (com motivo) para utilizadores sem processos:manage; backend devolve 403 a chamada direta.
result: [pending]

### 4. Illegal transition 409
expected: POST /processos/{id}/transicao/encerrar num processo em TRIAGEM (acao nao permitida) devolve 409.
result: [pending]

### 5. Prazos risco + toggle
expected: Badges de risco (ok/proximo/vencido) corretos por data_limite; toggle de concluido mantem o risco coerente; "Novo Prazo" cria e atualiza.
result: [pending]

### 6. Listing signals
expected: Listagem mostra Resp.: + badge de risco (so proximo/vencido) + indicador de escalonado, consistente com o detalhe.
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
