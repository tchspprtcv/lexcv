---
status: partial
phase: 75-componente-nico-view-edit
source: [75-VERIFICATION.md]
started: 2026-07-04T00:00:00Z
updated: 2026-07-04T00:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Paridade visual do modo leitura
expected: Modo leitura (dl/dd grid) idêntico à página de detalhe pré-fase, sem regressão de layout
result: [pending]

### 2. Round-trip de guardar (Editar → Guardar)
expected: Página alterna para edição sem navegação; guardar faz round-trip real via PUT /clientes/{id}, mostra toast de sucesso e volta a modo leitura com dados atualizados
result: [pending]

### 3. Cancelar descarta alterações sem confirmação
expected: Formulário reverte para valores originais e volta a modo leitura, sem navegação nem dialog de confirmação
result: [pending]

### 4. Gating de CRUD nos sub-componentes (Contactos/Notas/Advogados/Administrativos/Procuração)
expected: Afordances Adicionar/Editar/Remover ocultas em modo leitura, visíveis em modo edição; Ver/Download da Procuração sempre visível em ambos os modos
result: [pending]

### 5. Links da lista de clientes apontam para a ficha unificada
expected: Ícone de lápis (mobile e desktop) leva a /clientes/[id] em modo leitura, com o toggle Editar disponível — não 404 nem rota /editar antiga
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
