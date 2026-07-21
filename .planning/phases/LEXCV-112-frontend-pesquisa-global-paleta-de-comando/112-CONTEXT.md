# Phase 112: Frontend — Pesquisa Global (Paleta de Comando) - Context

**Gathered:** 2026-07-21
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous)

<domain>
## Phase Boundary

O utilizador encontra e navega para qualquer Cliente/Processo/Documento/Parecer do seu tenant a partir de qualquer página, através de uma paleta de pesquisa acessível pelo topbar ou por atalho de teclado. Consome o contrato estável da Phase 111 (`GET /api/v1/pesquisa?q=`); nenhuma alteração ao backend nesta fase.

</domain>

<decisions>
## Implementation Decisions

### Trigger & Layout
- Botão-trigger que abre `CommandDialog` (não input inline com dropdown) — substitui o `<Input>` decorativo do topbar (`dashboard-shell.tsx:121-127`)
- Listener global de Ctrl+K/⌘K montado uma vez em `dashboard-shell.tsx`, mesmo padrão self-contained do `NotificationBell`
- Em mobile, ícone/botão sempre visível (topbar mobile ou drawer) — Ctrl+K é um extra desktop, não a via principal em mobile
- Fecha com Esc **e** clique fora (comportamento standard do Dialog/Radix já suportado por `CommandDialog`)

### Resultados & Agrupamento
- Ordem fixa dos grupos: Clientes, Processos, Documentos, Pareceres (mesma ordem da sidebar)
- Ícone por tipo reutiliza exatamente os da sidebar: `Users` (Clientes), `Scale` (Processos), `FileText` (Documentos), `ScrollText` (Pareceres)
- Mostra todos os resultados que o backend devolve (máx. 5/tipo, já limitado server-side) — sem truncar mais no frontend
- Link "Ver todos" por grupo pré-preenche o termo pesquisado — lê `?q=` do URL no mount de cada lista e semeia o estado de filtro já existente (confirmado: `Clientes` já tem `filters.q` em `clientes/page.tsx:63,82`; replicar o mesmo padrão em Processos/Documentos/Pareceres)

### Estados (vazio/loading/sem-resultados) & Recentes
- "Visitar um registo" = navegação para `/clientes/[id]`, `/processos/[id]` ou `/pareceres/[id]`; Documento não tem rota de detalhe própria, fica fora da lista de recentes
- Recentes guardados em `sessionStorage` (session-only, nunca persiste entre sessões — cumpre SRCH-10 e é mais conservador que `localStorage` num posto de trabalho partilhado)
- Loading: linhas skeleton por grupo, reutilizando o primitivo `Skeleton` (já usado no Dashboard desde a Phase 103)
- Sem resultados: reutiliza `Empty`/`EmptyTitle`/`EmptyDescription` com mensagem simples

### Ranking, Destaque & Navegação por Teclado
- Destaque do texto correspondente em negrito (não cor de fundo nem sublinhado)
- Destaque aplica-se a título **e** subtítulo (o subtítulo contém frequentemente o identificador estruturado pesquisado)
- Nenhum indicador durante os ~300ms de debounce — só mostra "a carregar" quando o pedido real está em curso
- Navegação por setas/Enter vem grátis do `Command`/cmdk (mesma base já usada e testada em `combobox.tsx`) — só ligar os `onSelect` corretamente

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/ui/command.tsx` — `CommandDialog`/`CommandGroup`/`CommandItem`/`CommandEmpty` (cmdk-based, instalado na v2.13 Phase 107, hoje só consumido indiretamente por `Combobox`)
- `web/src/components/shared/combobox.tsx:107` — precedente confirmado de `<Command shouldFilter={false}>`; replicar exatamente (sem isto, o ranking do backend seria re-ordenado ou escondido pelo scorer fuzzy do cmdk)
- `Skeleton`, `Empty`/`EmptyHeader`/`EmptyMedia`/`EmptyTitle`/`EmptyDescription` — já usados em `app/(dashboard)/dashboard/page.tsx`
- Ícones Lucide já mapeados por entidade em `dashboard-shell.tsx`'s `NAV` (`Users`, `Scale`, `FileText`, `ScrollText`)
- `ClientesListFilters` já tem campo `q` (`clientes/page.tsx:63,82`) — padrão a replicar nas outras 3 listas

### Established Patterns
- Hooks TanStack Query em `web/src/hooks/use-*.ts`, todos consumindo `apiFetch` (`web/src/lib/api.ts`)
- Gate de permissões deve usar `permissions.isFetched`, nunca `!permissions.isLoading` — bug recorrente já corrigido em várias fases (103/105/v2.13-audit); aplicar desde já nesta fase nova
- `NotificationBell` é o precedente de componente global self-contained montado uma vez em `dashboard-shell.tsx`

### Integration Points
- `dashboard-shell.tsx:121-127` — o `<Input>` decorativo a substituir pelo trigger
- 4 páginas de lista (`clientes/page.tsx`, `processos/page.tsx`, `documentos/page.tsx`, `pareceres/page.tsx`) — cada uma precisa ler `?q=` do URL via `useSearchParams` no mount e semear o filtro `q` já existente no seu estado

</code_context>

<specifics>
## Specific Ideas

Contrato do backend a consumir (Phase 111, estável): `GET /api/v1/pesquisa?q=<termo>` devolve `ResultadoPesquisaDto[]` — `{ tipo, id, titulo, subtitulo, rota }`, já limitado a 5 por tipo, já ordenado (exato/prefixo antes de substring), já filtrado por RBAC/tenant no servidor. O frontend não faz nenhuma lógica de permissão adicional — mostra exatamente o que o endpoint devolver.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
