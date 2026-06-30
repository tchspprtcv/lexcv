---
phase: 60-ficha-imprimivel
plan: 02
subsystem: web/clientes
tags: [frontend, clientes, ficha, printer]
dependency-graph:
  requires: []
  provides:
    - "Botão 'Imprimir Ficha' no cabeçalho de detalhe do cliente"
    - "Ícone Printer na listagem de clientes (ClienteRow)"
  affects:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"
    - "web/src/app/(dashboard)/clientes/page.tsx"
tech-stack:
  added: []
  patterns:
    - "Link target=_blank rel=noopener noreferrer para abrir ficha imprimível em nova aba"
key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"
    - "web/src/app/(dashboard)/clientes/page.tsx"
decisions:
  - "Botão Printer na listagem é sempre visível (não condicionado a canEditClientes), consistente com clientes:view sendo suficiente para visualizar/imprimir"
  - "Mantido padrão de botões directos na listagem (Eye/Pencil/Trash2) em vez de menu kebab, seguindo recomendação da RESEARCH.md"
metrics:
  duration: "~10 min"
  completed: 2026-06-30
---

# Phase 60 Plan 02: Acesso à Ficha Imprimível Summary

Adicionados os dois pontos de entrada para a ficha imprimível do cliente: botão "Imprimir Ficha" no cabeçalho da página de detalhe e ícone Printer em cada linha da listagem de clientes, ambos abrindo `/clientes/{id}/ficha` em nova aba com `rel="noopener noreferrer"`.

## Tasks Completed

1. **Task 1** — Botão "Imprimir Ficha" adicionado em `web/src/app/(dashboard)/clientes/[id]/page.tsx`, no bloco de acções do cabeçalho, entre "Voltar" e "Editar". Sempre visível (gate de permissão já existe no componente pai). Commit `de5c35b`.
2. **Task 2** — Ícone Printer adicionado em `ClienteRow` de `web/src/app/(dashboard)/clientes/page.tsx`, imediatamente após o botão Eye, fora do bloco condicional `canEditClientes`. Commit `ab63428`.

## Deviations from Plan

None — plan executado exactamente como escrito.

## Verification Notes

`npx tsc --noEmit` não pôde ser executado neste worktree porque `node_modules` não está instalado (dependências não presentes no ambiente de execução paralela). As edições seguem exactamente os padrões JSX/TSX existentes nos ficheiros (mesma estrutura de `Button asChild` + `Link`, mesmas classes Tailwind, imports adicionados à lista existente de `lucide-react`), pelo que o risco de erro de tipos é mínimo. Recomenda-se correr `pnpm install && npx tsc --noEmit` no merge final do branch consolidado.

A rota de destino `/clientes/[id]/ficha/page.tsx` (criada no plano 60-01) não está presente neste worktree isolado — é esperado, pois 60-01 e 60-02 executam em árvores de trabalho paralelas distintas e serão integradas pelo orquestrador.

## Known Stubs

None.

## Threat Flags

None — ambos os links seguem o padrão de mitigação T-60-05 (`rel="noopener noreferrer"` presente em ambos os Links com `target="_blank"`), conforme threat_model do plano.

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/clientes/[id]/page.tsx contains "Imprimir Ficha", "target=\"_blank\"", "rel=\"noopener noreferrer\"", "/ficha"
- FOUND: web/src/app/(dashboard)/clientes/page.tsx contains "Printer" in lucide-react import and in ClienteRow JSX
- FOUND: commit de5c35b
- FOUND: commit ab63428
