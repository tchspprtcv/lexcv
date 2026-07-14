# Phase 93: NOTF-24 — Preferências de Notificação por Utilizador - Context

**Gathered:** 2026-07-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Cada utilizador pode silenciar, só para si próprio, as categorias de notificação que não lhe interessam, com pelo menos uma categoria crítica sempre entregue e sem escape possível pelo job diário.

</domain>

<decisions>
## Implementation Decisions

### Granularidade
- Silenciamento por categoria individual — as 9 categorias existentes (`FASE_ENTRADA`, `DOCUMENTO_NOVO`, `PROCESSO_ATRIBUIDO`, `PARECER_ATRIBUIDO`, `PRAZO_PROXIMO`, `PRAZO_VENCIDO`, `EVENTO_PROXIMO`, `EVENTO_VENCIDO`, `HONORARIO_ATRASADO`), cada uma com o seu próprio toggle
- Reutilizar `NOTIFICACAO_CATEGORIA_OPTIONS` (`web/src/lib/notificacao-categoria.ts`) como fonte de verdade da lista/labels PT — não duplicar a enumeração

### Categorias não-silenciáveis
- Apenas `PRAZO_VENCIDO` é sempre entregue e não aparece como opção de silenciamento na UI; uma tentativa direta via API de a silenciar é rejeitada/ignorada pelo backend
- As restantes 8 categorias (incluindo `HONORARIO_ATRASADO` e `EVENTO_VENCIDO`) são silenciáveis

### Choke point de aplicação (arquitetura já pesquisada, locked)
- O guard de silenciamento vive dentro de `NotificacaoService.criar(...)` — o único ponto de escrita de toda a subsistema — não em cada método `notificar*` individualmente
- Isto garante que o job diário (`AlertasDiariosJob`, que chama `criar()` diretamente, sem passar pelos 4 métodos `notificar*`) também respeita o silenciamento, sem precisar de alteração própria

### UI
- Localização: página `/settings` já existente (`web/src/app/(dashboard)/settings/page.tsx`) — adicionar uma nova secção/tab de preferências de notificação, sem criar uma página nova
- Lista de toggles reutiliza `NOTIFICACAO_CATEGORIA_OPTIONS`, com `PRAZO_VENCIDO` omitido da lista (não-silenciável)

### Claude's Discretion
- Nome exato da tabela/entidade nova (ex.: `NotificacaoPreferencia`) e forma exata dos endpoints (`GET/PUT/DELETE /notificacoes/preferencias/{categoria}` vs. um único endpoint que aceita a lista completa) — seguir o padrão REST já usado no resto do `NotificacaoController`
- Se a ausência de linha = "entregar" (default-on) e presença de linha = "silenciado" — este é o design já recomendado pela pesquisa da milestone (padrão `ClienteAdvogado`/`ClienteAdministrativo`: presença de junção = ativo)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/lib/notificacao-categoria.ts` — `NOTIFICACAO_CATEGORIA_OPTIONS`, `categoriaToLabel`, já exaustivos e tipados contra `NotificacaoCategoria`
- `web/src/app/(dashboard)/settings/page.tsx` — página de definições já existente, ponto de integração natural
- `NotificacaoService.criar(...)` (`backend/src/main/java/com/lexcv/services/NotificacaoService.java`) — choke point único de escrita, hoje sem qualquer verificação de preferências
- Padrão de tabela de junção tenant-scoped já usado por `ClienteAdvogado`/`ClienteAdministrativo` — reutilizar a mesma convenção para `NotificacaoPreferencia (tenant_id, user_id, categoria)`

### Established Patterns
- Todas as 9 categorias já enumeradas e com labels PT em `web/src/lib/notificacao-categoria.ts` (fonte de verdade única, sem duplicação)
- `NotificacaoService.criar` é chamado tanto pelos 4 métodos `notificar*` como diretamente por `AlertasDiariosJob` — qualquer guard colocado aqui cobre ambos os caminhos automaticamente

### Integration Points
- `NotificacaoService.criar(...)` — adicionar guard clause de verificação de preferência antes de persistir
- `NotificacaoController` (`backend/src/main/java/com/lexcv/controllers/`) — novos endpoints de preferências
- `web/src/app/(dashboard)/settings/page.tsx` — nova secção de preferências
- Novo hook TanStack Query (padrão `useNotificacoes`) para ler/escrever preferências

</code_context>

<specifics>
## Specific Ideas

Nenhuma além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- Matriz de preferências categoria×canal — fora de âmbito, já registado em REQUIREMENTS.md Out of Scope (prematuro com um único canal in-app)

</deferred>
