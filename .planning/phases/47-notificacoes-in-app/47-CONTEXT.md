# Phase 47: Notificações In-App - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase adiciona notificações in-app ao header da aplicação: um badge com contagem de eventos e prazos não concluídos nos próximos 7 dias, e um painel Popover com a lista de eventos próximos com links para o detalhe do processo. O Bell icon já existe no `dashboard-shell.tsx` como botão estático — será transformado num componente de notificações funcional.

**Fora do âmbito desta fase:** notificações push/email, recorrência de eventos (Phase 48), drag & drop (Phase 49).

</domain>

<decisions>
## Implementation Decisions

### Backend Endpoint & Dados
- Endpoint dedicado `GET /api/v1/eventos/upcoming?days=7` no `ResourceController` (semântico, fácil de testar)
- Slim DTO de resposta: apenas `id`, `titulo`, `dataInicio`, `processoId`, `categoria` — só o necessário para o painel
- Tenant scoping via `getTenantId()` — padrão estabelecido no projeto
- Filtro `concluido=false` aplicado no backend — dados limpos desde a origem

### Badge + Painel UI
- `Popover` (floating, fecha ao clicar fora) — padrão de notificações web; verificar se existe em `components/ui/popover.tsx`
- Badge só aparece quando count > 0 — não polui o header quando não há eventos próximos
- Máximo de 10 itens no painel + link "Ver agenda" para `/agenda`
- Ordenação por `dataInicio` ascendente — o mais urgente aparece primeiro

### Cache & Refresh
- `staleTime: 60_000` (1 minuto) — equilibra frescura vs número de requests
- `onSuccess` do hook `useConcluirEvento` (se existir) invalida `["eventos", "upcoming"]` para contagem atualizar imediatamente
- Badge oculto durante loading inicial — sem flash de "0" ao carregar
- Erro silencioso: se o endpoint falhar, badge não aparece — sem disrupção da UI principal

### Claude's Discretion
- Estrutura exata do componente `NotificationBell` (componente separado em `components/shared/` ou inline em `dashboard-shell.tsx`)
- Se `Popover` não existir, instalar via `pnpm dlx shadcn@latest add popover` ou usar `div` + state
- Hook `useUpcomingEventos` em `web/src/hooks/use-eventos.ts` ou novo ficheiro

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Bell` icon — já importado em `dashboard-shell.tsx` linha 8, usado na linha 168 como botão estático
- `Button` variant="ghost" — já usado no Bell button existente
- `useMe()` — já usado no shell para dados do utilizador
- `apiFetch<T>()` de `@/lib/api` — padrão de fetch do projeto
- TanStack Query `useQuery` — padrão estabelecido para data fetching

### Established Patterns
- Hooks em `web/src/hooks/use-*.ts` com `useQuery` + `apiFetch`
- `staleTime` em todos os hooks existentes (15_000 ou 10_000)
- Padrão de invalidação em `onSuccess` nos hooks de mutação

### Integration Points
- `web/src/components/shared/dashboard-shell.tsx` linha ~165-169 — substituir Bell button estático pelo componente `NotificationBell`
- `web/src/hooks/use-eventos.ts` (ou novo hook file) — adicionar `useUpcomingEventos()`
- `backend/.../controllers/ResourceController.java` — adicionar endpoint `GET /eventos/upcoming`
- `web/src/components/ui/popover.tsx` — verificar existência; instalar se necessário

</code_context>

<specifics>
## Specific Ideas

- O endpoint backend pode usar JPQL: `SELECT e FROM Evento e WHERE e.tenantId = :tenantId AND e.concluido = false AND e.dataInicio BETWEEN :now AND :future ORDER BY e.dataInicio ASC`
- O componente de badge pode ser um `<span>` com `absolute` positioning sobre o Bell icon (padrão notification bubble)
- Verificar se `useConcluirEvento` existe no projeto para adicionar invalidação; se não existir, a invalidação será na próxima stale check

</specifics>

<deferred>
## Deferred Ideas

- Notificações push (browser) ou email — fora do âmbito v2.1 in-app only
- Marcar notificação como "lida" individualmente — fora do âmbito desta fase
- Configuração de tempo de antecedência (1h, 1 dia) por utilizador — fora do âmbito

</deferred>
