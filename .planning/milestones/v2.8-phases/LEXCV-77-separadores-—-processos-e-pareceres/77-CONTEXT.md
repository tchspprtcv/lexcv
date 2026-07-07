# Phase 77: Separadores — Processos e Pareceres - Context

**Gathered:** 2026-07-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Os separadores "Processos" e "Pareceres" na ficha do cliente (introduzidos como placeholders "Em breve" na Phase 76) ganham conteúdo real: listagem compacta dos processos e pareceres associados ao cliente atual, reutilizando os hooks já existentes (`useProcessos({cliente_id})`, `usePareceres({clienteId})`) sem qualquer alteração de backend. Não cobre criação de novos processos/pareceres a partir da ficha, nem edição inline.

</domain>

<decisions>
## Implementation Decisions

### Conteúdo & Layout
- Separador "Processos": versão compacta com colunas Número, Estado (badge), Área Jurídica, Data de Início — sem tribunal/legal-hold.
- Separador "Pareceres": versão compacta com colunas Número/Título, Estado (badge), Advogado Responsável, Data de Criação.
- Clicar numa linha de Processo ou Parecer navega para a página de detalhe existente (`/processos/[id]` / `/pareceres/[id]`) — sem preview inline.
- Sem CTAs de criação ("Novo Processo"/"Nova Solicitação") nesta fase — só listagem, evita duplicar fluxos de criação já existentes.

### Estados & Estrutura Visual
- Estado vazio: mensagem simples centrada (ex.: "Nenhum processo associado a este cliente."), mesmo idioma visual do placeholder "Em breve" da Phase 76.
- Estado de loading: skeleton/spinner simples, consistente com o padrão já usado noutras listas da app.
- Lista usa o componente `Table` do shadcn (`TableHeader`/`TableBody`/`TableRow`/`TableCell`) — mesma consistência visual de `/processos` e `/pareceres`.
- Fetch lazy: `useProcessos`/`usePareceres` só disparam quando o respetivo separador é ativado (`enabled: tab === "processos"` / `enabled: tab === "pareceres"`), evitando chamadas desnecessárias ao abrir a ficha.

### Claude's Discretion
- Nome exato de variáveis/estado interno.
- Texto exato das mensagens de estado vazio (copy livre, tom institucional em português).
- Ordenação exata da lista (mais recente primeiro é o padrão razoável, seguir o que os hooks já retornam por defeito).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useProcessos({ cliente_id })` (`web/src/hooks/use-processos.ts`) — hook já existente, aceita filtro `cliente_id` (snake_case).
- `usePareceres({ clienteId })` (`web/src/hooks/use-pareceres.ts`) — hook já existente, aceita filtro `clienteId` (camelCase) — nota: nomenclatura de filtro é inconsistente entre os dois hooks (pré-existente, não é escopo desta fase corrigir).
- Componente `Table`/`TableHeader`/`TableBody`/`TableRow`/`TableCell` (shadcn) já usado em `/processos/page.tsx` e `/pareceres/page.tsx` — reutilizar exatamente.
- Badges de estado (`Badge` com variantes coloridas) já usados nas listas de processos/pareceres — reutilizar o mesmo mapeamento de cor por estado.

### Established Patterns
- TanStack Query com `enabled` condicional é o padrão já usado noutras partes da app para fetch lazy/condicional.
- `Link` do Next.js para navegação para páginas de detalhe é o padrão em toda a app.

### Integration Points
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` — os dois `PlaceholderEmBreve` (Processos, Pareceres) da Phase 76 são substituídos por conteúdo real dentro dos respetivos ramos condicionais de tab.

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica além do já documentado — replicar o estilo de tabela já usado nas páginas `/processos` e `/pareceres`, em versão compacta (menos colunas).

</specifics>

<deferred>
## Deferred Ideas

- Conteúdo real dos separadores Documentos a Tratar e Deslocações — Phase 78.
- Conteúdo real do separador Documentos Entregues (upload real) — Phase 79.
- CTAs de criação de processo/parecer a partir da ficha do cliente — fora de âmbito desta milestone.

</deferred>
