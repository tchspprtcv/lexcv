---
status: partial
phase: 85-consolida-o-da-l-gica-de-prazo-cr-tico
source: [85-VERIFICATION.md]
started: 2026-07-08T16:45:17Z
updated: 2026-07-08T16:45:17Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. ALTA-priority Evento com dataFim nulo — deve contar como urgente nos KPIs do dashboard?
expected: Decisão do product owner sobre se um `Evento` de prioridade ALTA sem `dataFim` definido deve contar como "urgente/crítico" em `agendaUrgentesCount` e `prazosCriticosCount`. Antes desta fase, `agendaUrgentesCount` não tinha nenhuma verificação de data (comportamento implícito antigo); a consolidação em `RiscoPrazoService` exige uma data para calcular o risco, pelo que estes eventos passaram a ser excluídos de ambos os KPIs. O código atual documenta esta lacuna num comentário (`ResourceController.java:2774-2786`) mas não a resolve — 3 rondas de revisão de código não chegaram a uma decisão, porque é uma decisão de produto, não um defeito técnico.
result: [pending]

## Summary

total: 1
passed: 0
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps
