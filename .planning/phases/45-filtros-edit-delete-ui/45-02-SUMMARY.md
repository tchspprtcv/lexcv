---
phase: 45-filtros-edit-delete-ui
plan: "02"
subsystem: frontend/financeiro
tags: [honorario, edit, delete, dialog, alert-dialog, permissions]
dependency_graph:
  requires: []
  provides: [honorarioUpdateSchema, alert-dialog-component, edit-honorario-dialog, delete-honorario-alertdialog, delete-pagamento-alertdialog]
  affects: [web/src/app/(dashboard)/financeiro/[id]/page.tsx, web/src/schemas/financeiro.ts]
tech_stack:
  added: ["@radix-ui/react-alert-dialog@1.1.17"]
  patterns: [react-hook-form + zodResolver, AlertDialog confirmation pattern, Dialog edit form pattern]
key_files:
  created:
    - web/src/components/ui/alert-dialog.tsx
  modified:
    - web/src/schemas/financeiro.ts
    - web/src/app/(dashboard)/financeiro/[id]/page.tsx
    - web/package.json
    - web/pnpm-lock.yaml
decisions:
  - "alert-dialog criado manualmente (sem components.json) usando @radix-ui/react-alert-dialog instalado via pnpm, seguindo o padrão do dialog.tsx existente"
  - "variant destructive ausente no Button do projeto — botão Apagar usa className inline bg-red-600"
metrics:
  duration: "~15 min"
  completed: "2026-06-18"
  tasks_completed: 2
  files_changed: 5
---

# Phase 45 Plan 02: Edit Dialog + Delete Confirmations (Honorário Detail) Summary

Edit Dialog (FIN-14) e AlertDialogs de eliminação (FIN-15, FIN-16) adicionados à página de detalhe do honorário, com gating por permissões e validação Zod.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Instalar alert-dialog e adicionar honorarioUpdateSchema | da09e38 | alert-dialog.tsx, schemas/financeiro.ts, package.json, pnpm-lock.yaml |
| 2 | Edit Dialog + Delete AlertDialogs em [id]/page.tsx | ccfc9b9 | financeiro/[id]/page.tsx |

## What Was Built

**Task 1:**
- Instalado `@radix-ui/react-alert-dialog@1.1.17` via pnpm
- Criado `web/src/components/ui/alert-dialog.tsx` com todos os exports necessários (AlertDialog, AlertDialogTrigger, AlertDialogContent, AlertDialogHeader, AlertDialogFooter, AlertDialogTitle, AlertDialogDescription, AlertDialogAction, AlertDialogCancel)
- Adicionado `honorarioUpdateSchema` e `HonorarioUpdateFormValues` em `schemas/financeiro.ts`, reutilizando as primitivas `moneyString`, `optionalTrimmedString` e `optionalDateString` já existentes

**Task 2:**
- `canManageFinanceiro = permissions.can.manage("financeiro")` declarado e passado como prop
- Dialog de edição (FIN-14): botão "Editar" gated por `canEditFinanceiro`, form pré-preenchido com react-hook-form + zodResolver, usa `useUpdateHonorario`, fecha no sucesso, mostra erros de servidor no campo root
- AlertDialog de eliminação de honorário (FIN-15): botão "Apagar" gated por `canManageFinanceiro`, confirmação obrigatória, erros inline (incluindo 409), `router.push('/financeiro')` no sucesso
- AlertDialog por linha de pagamento (FIN-16): botão "Apagar" gated por `canManageFinanceiro`, usa `deletePagamento.mutate(...)` (não mutateAsync) para evitar unhandled promise rejection

## Deviations from Plan

**1. [Rule 1 - Bug] Instalação do alert-dialog via pnpm em vez de shadcn CLI**
- **Found during:** Task 1
- **Issue:** O projeto não tem `components.json` — o comando `pnpm dlx shadcn@latest add alert-dialog` fica interativo e não pode ser executado de forma autónoma
- **Fix:** Instalado `@radix-ui/react-alert-dialog` diretamente via `pnpm add` e criado o componente manualmente seguindo o padrão estabelecido pelo `dialog.tsx` do projeto
- **Files modified:** web/src/components/ui/alert-dialog.tsx, web/package.json, web/pnpm-lock.yaml
- **Commit:** da09e38

**2. [Rule 1 - Bug] Variant "destructive" não existe no Button deste projeto**
- **Found during:** Task 2 (build falhou com TypeScript error)
- **Issue:** O `Button` do projeto só suporta: default, secondary, outline, ghost, link — não tem variant "destructive"
- **Fix:** Substituído `variant="destructive"` por `className="bg-red-600 hover:bg-red-700 text-white"` no botão "Apagar honorário"
- **Files modified:** web/src/app/(dashboard)/financeiro/[id]/page.tsx
- **Commit:** ccfc9b9

## Known Stubs

Nenhum stub identificado. Todos os componentes adicionados estão ligados a hooks reais (`useUpdateHonorario`, `useDeleteHonorario`, `useDeletePagamento`).

## Threat Flags

Nenhuma superfície nova não mapeada. As três operações (PUT /honorarios/:id, DELETE /honorarios/:id, DELETE /pagamentos/:id) estavam já no threat model do plano.

## Self-Check: PASSED
