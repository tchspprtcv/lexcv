# Phase 89: Sino e Página de Notificações - Context

**Gathered:** 2026-07-10
**Status:** Ready for planning

<domain>
## Phase Boundary

O utilizador consegue ver, consultar e gerir as suas notificações diretamente na interface da aplicação — contador no sino, lista rápida com atalhos, histórico completo com filtros, e marcação de leitura individual ou em massa. Última fase da milestone — consome a API já construída na Phase 86 (`GET /notificacoes`, `GET /notificacoes/unread-count`, `PATCH /notificacoes/{id}/lida`, `POST /notificacoes/ler-todas`) e beneficia de dados reais gerados pelas Phases 87/88. Não cria nenhum endpoint novo — é 100% frontend.

</domain>

<decisions>
## Implementation Decisions

### Sino (Bell)
- Substituir completamente a vista atual "Próximos eventos" (v2.1, baseada em `useUpcomingEventos`) pela lista de `Notificacao` persistidas — a Agenda mantém a sua própria página; o sino passa a ser sobre notificações genéricas, não especificamente sobre eventos.
- Dropdown mostra as 10 notificações mais recentes (mesmo limite já usado hoje).
- Clicar numa notificação no dropdown marca-a como lida automaticamente E navega para a entidade relacionada (`linkUrl`) — ação única, sem passo de confirmação.
- Link "Ver todas as notificações" no fundo do dropdown, que navega para `/notificacoes` — sem novo item fixo na sidebar.
- Botão "Marcar todas como lidas" disponível no dropdown do sino.

### Polling
- Intervalo de 30 segundos (`refetchInterval: 30_000`).
- Esta query precisa de um override específico de `refetchOnWindowFocus: true` — a app tem este valor `false` globalmente (confirmado em `web/src/app/providers.tsx` pela pesquisa da milestone), pelo que sem o override explícito o contador não atualiza ao voltar a focar a aba.
- Usar `refetchInterval` nativo do TanStack Query — nunca um `setInterval` manual.

### Página `/notificacoes`
- Lista simples paginada, mais recentes primeiro — sem agrupamento por data.
- Dois filtros: select de categoria (todos os valores de `categoria` já usados: `FASE_ENTRADA`, `DOCUMENTO_NOVO`, `PROCESSO_ATRIBUIDO`, `PARECER_ATRIBUIDO`, `PRAZO_PROXIMO`, `PRAZO_VENCIDO`, `EVENTO_PROXIMO`, `EVENTO_VENCIDO`, `HONORARIO_ATRASADO`) + toggle lida/não-lida.
- Botão "Marcar todas como lidas" também disponível nesta página (mesma ação/hook que no sino).
- Acesso apenas via link "Ver todas" no dropdown do sino — sem item fixo na sidebar (evita adicionar navegação permanente para uma página secundária).

### Claude's Discretion
- Nome exato dos componentes/hooks novos.
- Estrutura exata da paginação (cursor vs. offset) — seguir o padrão já estabelecido pela Phase 86's `GET /notificacoes` (que já suporta paginação via `Pageable`).
- Texto/copy exato de estados vazios ("Sem notificações"), erros, e labels de filtro.
- Mapeamento de `categoria` para um label traduzido em português amigável ao utilizador (ex.: `"PRAZO_VENCIDO"` → "Prazo vencido") — necessário para os filtros e para exibição, seguir o padrão de mapas de label já usado no projeto (ex.: `documento_tipo`, `tipo_processo`).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/shared/notification-bell.tsx` — componente atual a substituir/estender; já tem a estrutura de popover/dropdown a reaproveitar.
- API já pronta desde a Phase 86: `GET /notificacoes` (filtros categoria/lida + paginação), `GET /notificacoes/unread-count`, `PATCH /notificacoes/{id}/lida`, `POST /notificacoes/ler-todas`.
- Este é o primeiro uso de `refetchInterval` no projeto — confirmar que não há nenhum outro hook a usar polling antes de assumir um padrão existente (não há, confirmado na pesquisa da milestone).

### Established Patterns
- `web/src/app/providers.tsx` define `refetchOnWindowFocus: false` globalmente — a nova query de notificações precisa de um override local.
- Página de listagem com filtros já existe noutros módulos (ex.: Documentos, Processos) — seguir a mesma estrutura de estado local + query params para os filtros.
- Mapas de label PT já existem para outros enums do projeto — seguir o mesmo padrão para `categoria`.

### Integration Points
- `DashboardShell`/top app bar — onde o sino já vive hoje.
- Rota nova `/notificacoes` (App Router) — página dedicada, dentro do route group `(dashboard)`.
- RBAC: `notificacoes:view` já seedado para os 4 perfis desde a Phase 86 — todos os utilizadores autenticados podem aceder.

</code_context>

<specifics>
## Specific Ideas

Nenhuma adicional além do já capturado nas Decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- Preferências de notificação por utilizador — fora de âmbito (REQUIREMENTS.md v2/NOTF-24).
- Snooze/silenciar notificações recorrentes — fora de âmbito (REQUIREMENTS.md v2/NOTF-26).
- Agrupamento por data na página de histórico — considerado, mas simplificado para lista plana nesta milestone.

</deferred>
