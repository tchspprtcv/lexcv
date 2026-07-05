---
status: partial
phase: 76-separadores-dados-contactos-e-notas
source: [76-VERIFICATION.md]
started: 2026-07-04T00:00:00Z
updated: 2026-07-04T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Click-through dos 7 separadores e tab por defeito
expected: "Dados" ativo por defeito; ao clicar em cada um dos 7 separadores, os 5 ainda não implementados mostram "Em breve" instantaneamente, sem spinner nem chamada de rede
result: [pending]

### 2. Erros de validação visíveis ao mudar de separador
expected: Em modo edição, ao invalidar um campo obrigatório e sair de "Dados" para outro separador, clicar "Guardar" volta automaticamente a "Dados" com o erro inline + toast visível
result: [pending]

### 3. Reset dos diálogos de intake ao trocar de separador
expected: Abrir um diálogo "Adicionar" (Documentos Entregues/A Tratar/Deslocações) em modo edição, escrever um rascunho, sair de "Dados" e voltar — o diálogo não reabre com texto residual
result: [pending]

### 4. Scroll horizontal da fila de separadores em mobile
expected: Em largura mobile, os 7 botões de separador fazem scroll horizontal (overflow-x-auto) em vez de quebrar em várias linhas
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
