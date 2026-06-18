---
phase: "47-notificacoes-in-app"
plan: 2
subsystem: frontend
tags: [notifications, popover, radix-ui, tanstack-query]
dependency_graph:
  requires: []
  provides: [NotificationBell, useUpcomingEventos, Popover]
  affects: [dashboard-shell, use-eventos]
tech_stack:
  added: ["@radix-ui/react-popover@1.1.17"]
  patterns: [shadcn-popover, tanstack-query-hook]
key_files:
  created:
    - web/src/components/ui/popover.tsx
    - web/src/components/shared/notification-bell.tsx
  modified:
    - web/src/types/eventos.ts
    - web/src/hooks/use-eventos.ts
    - web/src/components/shared/dashboard-shell.tsx
decisions:
  - "Popover usa @radix-ui/react-popover seguindo padrão shadcn já adotado no projeto"
  - "Badge oculto quando isLoading=true para evitar flash de 0 durante fetch inicial"
  - "Lista truncada em 10 itens via .slice(0,10) para não sobrecarregar o painel"
metrics:
  duration: "~12 min"
  completed: "2026-06-18"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 5
---

# Phase 47 Plan 02: NotificationBell Frontend Summary

**One-liner:** Popover de notificações com badge no header mostrando eventos próximos dos próximos 7 dias via `useUpcomingEventos` hook e `@radix-ui/react-popover`.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Instalar @radix-ui/react-popover, criar popover.tsx e UpcomingEvento type | 20445aa | popover.tsx, eventos.ts, package.json, pnpm-lock.yaml |
| 2 | Hook useUpcomingEventos + NotificationBell + integração no shell | 54e7f99 | use-eventos.ts, notification-bell.tsx, dashboard-shell.tsx |

## What Was Built

- **`web/src/components/ui/popover.tsx`** — Componente Popover/PopoverTrigger/PopoverContent seguindo o padrão shadcn, baseado em `@radix-ui/react-popover`.
- **`web/src/types/eventos.ts`** — Interface `UpcomingEvento` adicionada (id, titulo, dataInicio, processoId, tipo).
- **`web/src/hooks/use-eventos.ts`** — Hook `useUpcomingEventos(days=7)` adicionado com queryKey `["eventos","upcoming"]` e staleTime de 60 segundos. Invalidação de `["eventos","upcoming"]` adicionada ao `onSuccess` de `useToggleEventoConcluido` e `useSetEventoConcluido`.
- **`web/src/components/shared/notification-bell.tsx`** — Componente `NotificationBell` com badge vermelho (oculto quando count=0 ou durante loading), Popover com lista de até 10 eventos, links para `/processos/{processoId}` e rodapé "Ver agenda" para `/agenda`.
- **`web/src/components/shared/dashboard-shell.tsx`** — Botão Bell estático substituído por `<NotificationBell />`. Import de `Bell` de lucide-react removido (não mais necessário no ficheiro).

## Deviations from Plan

None — plano executado exatamente como escrito.

## Verification

- `pnpm tsc --noEmit` — sem erros após Task 1.
- `pnpm build` — compilado com sucesso sem erros TypeScript após Task 2.

## Known Stubs

None — `useUpcomingEventos` chama o endpoint real `/api/v1/eventos/upcoming?days=7`. O badge e a lista mostram dados reais quando o backend (47-01) estiver disponível. Enquanto o endpoint não existir, o hook retorna erro silencioso (apiFetch surfece como toast) e o badge permanece oculto.

## Threat Flags

Nenhum novo surface de segurança além dos documentados no plano. `apiFetch` usa `credentials:"include"` para autenticação via cookie JWT. `processoId` vem do backend já scoped por tenant.

## Self-Check: PASSED

- web/src/components/ui/popover.tsx — FOUND
- web/src/components/shared/notification-bell.tsx — FOUND
- web/src/hooks/use-eventos.ts (useUpcomingEventos) — FOUND
- web/src/types/eventos.ts (UpcomingEvento) — FOUND
- web/src/components/shared/dashboard-shell.tsx (NotificationBell) — FOUND
- Commit 20445aa — FOUND
- Commit 54e7f99 — FOUND
