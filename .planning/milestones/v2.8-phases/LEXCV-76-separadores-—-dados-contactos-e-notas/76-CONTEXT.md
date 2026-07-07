# Phase 76: Separadores — Dados, Contactos e Notas - Context

**Gathered:** 2026-07-04
**Status:** Ready for planning

<domain>
## Phase Boundary

A ficha de cliente (`/clientes/[id]`, unificada na Phase 75) passa a organizar a informação em 7 separadores (tabs), estilo botões-toggle de processos. Esta fase constrói o SHELL completo com os 7 botões de separador, e implementa conteúdo real APENAS para "Dados" e "Contactos e Notas". Os outros 5 separadores (Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações) ficam visíveis e clicáveis mas mostram um placeholder "Em breve" até serem implementados nas Phases 77-79. A identificação (NIF+documento_tipo+documento_numero) passa a viver dentro do card "Dados" como sub-secção própria.

</domain>

<decisions>
## Implementation Decisions

### Placeholders dos Separadores Ainda Não Construídos
- Os 7 botões de separador aparecem todos já nesta fase — critério de sucesso da fase exige "utilizador vê 7 separadores".
- Separadores ainda não implementados (Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações) mostram mensagem simples "Em breve" centrada num `Card`, sem spinner nem chamadas de API.
- Os botões destes separadores ficam clicáveis (não `disabled`) — o utilizador navega livremente entre todos os 7, só o conteúdo é que ainda não existe.
- Este estado é temporário, só durante o desenvolvimento desta milestone (entre Phase 76 e Phase 79) — nunca chega a produção porque a milestone entrega tudo antes do deploy.

### Composição do Separador "Dados"
- O separador "Dados" agrupa tudo o que existe hoje na página EXCETO Contactos/Notas (separador próprio) e as 3 listas de documentos/deslocações (ganham separadores próprios nas Phases 78/79): identificação (nome/NIF/documento_tipo/documento_numero), conta-corrente, informações adicionais (ramo de atividade, detalhes adicionais), descrição do caso, honorários propostos, procuração, advogados, administrativos.
- Descrição do Caso e Honorários Propostos (que não têm separador dedicado nos 7 definidos pela milestone) ficam dentro do separador "Dados", como secção própria ("Intake do Caso").
- Advogados/Administrativos (responsáveis) mantêm-se no separador "Dados" — não têm separador dedicado nos 7 definidos.
- A identificação (NIF+tipo+número) aparece como secção distinta com sub-título "Identificação", dentro do mesmo card "Dados" (não misturada sem separação visual com os outros campos).

### Mecanismo dos Separadores & Interação com Editar
- Padrão técnico: réplica exata do padrão já usado em processos — `type TabKey`, `useState<TabKey>`, fila `flex flex-wrap` de componentes `Button` (`variant={tab === x ? "secondary" : "outline"}`), com um único `Card` renderizado condicionalmente por separador ativo. NÃO usa `Tabs`/`TabsList`/`TabsContent` do shadcn/Radix (não introduzido no projeto).
- Separador por defeito ao abrir a ficha: "Dados".
- Ao clicar "Editar" estando noutro separador (ex. "Contactos e Notas"), a UI muda automaticamente para o separador "Dados" (onde vive o formulário principal de edição).
- Em mobile, a fila de 7 botões de separador usa `overflow-x-auto` (scroll horizontal), consistente com o padrão já usado noutras tabelas complexas do projeto (v2.3 responsividade) — não usa `flex-wrap` simples.

### Claude's Discretion
- Nome exato da variável de estado (`tab`/`activeTab`, `TabKey` é sugestão).
- Texto exato do placeholder "Em breve" (copy livre, mantendo tom institucional em português).
- Ordem exata dos 7 botões na fila (seguir a ordem da lista: Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/(dashboard)/processos/[id]/page.tsx` — padrão de referência exato para os separadores: `type TabKey = "timeline" | "partes" | "fases" | "auditoria"`, `useState`, fila de `Button`s, `Card` condicional por tab.
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` (pós Phase 75) — componente único view/edit atual, todos os cards hoje na mesma página flat: Dados (dl/dd), Conta-corrente, Informações Adicionais, Contactos, Notas, Procuração, Advogados, Administrativos, e (em modo edição) Descrição do Caso, Honorários Propostos, 3 listas staged.

### Established Patterns
- `dl`/`dd` grid 3 colunas é o padrão de leitura em toda a app.
- Fila de botões toggle (não shadcn Tabs) já é o padrão estabelecido em processos, reutilizado aqui por consistência.
- `overflow-x-auto` em filas/tabelas complexas é o padrão de responsividade já usado desde a v2.3.

### Integration Points
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` recebe a nova estrutura de tabs, envolvendo o conteúdo existente sem alterar a lógica de dados subjacente (hooks, mutations inalterados).
- Os 5 separadores placeholder não fazem nenhuma chamada de API nesta fase.

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica além do já documentado — seguir exatamente o padrão visual de processos para os separadores.

</specifics>

<deferred>
## Deferred Ideas

- Conteúdo real dos separadores Processos e Pareceres — Phase 77.
- Conteúdo real dos separadores Documentos a Tratar e Deslocações — Phase 78.
- Conteúdo real do separador Documentos Entregues (upload real) — Phase 79.

</deferred>
