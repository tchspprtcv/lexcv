# Phase 78: Separadores — Documentos a Tratar e Deslocações - Context

**Gathered:** 2026-07-06
**Status:** Ready for planning

<domain>
## Phase Boundary

As secções "Documentos a Tratar" e "Deslocações" — hoje ainda dentro do branch de tab "dados" (introduzidas antes das Phases 76/77, mantidas lá por decisão explícita da Phase 76) — são relocalizadas para os seus próprios separadores (`documentos-a-tratar`, `deslocacoes`), substituindo os respetivos `PlaceholderEmBreve`. É uma relocalização pura de JSX/estado existente, sem alteração de comportamento, campos ou dados.

</domain>

<decisions>
## Implementation Decisions

### Relocalização e Gating de Edição
- As secções movem-se tal e qual (mesma JSX, mesmos hooks/estado local `documentosATratar`/`deslocacoes`, `newDocATratar`/`newDeslocacao`, `addDocATratarModal`/etc.) — só muda a localização física no ficheiro (novo branch de tab em vez de dentro de "dados").
- O botão "Adicionar" de ambas as listas continua gated por `canEditClientes && editable` (mesmo padrão herdado das Phases 75/76).
- **Correção de scope face ao texto do roadmap:** "Documentos a Tratar" tem hoje apenas o campo `descricao` (confirmado por leitura direta do código — `newDocATratar: { descricao: string }`), não "descrição+data" como o texto do roadmap sugere. Este comportamento atual mantém-se sem alteração; NÃO se adiciona um campo `data` nesta fase (seria uma funcionalidade nova, fora de âmbito).
- O `useEffect` que fecha/reseta os diálogos de intake quando `tab !== "dados"` (introduzido na Phase 76, fix CR-01) é estendido para também fechar/resetar os diálogos "Adicionar Documento a Tratar" e "Adicionar Deslocação" quando o separador ativo deixar de ser o respetivo tab (`documentos-a-tratar` / `deslocacoes`).

### Estado Vazio & Visibilidade em Modo Leitura
- **Correção pós-pattern-mapping:** a leitura direta do código (78-PATTERNS.md, Secção F) confirmou que a secção inteira "Intake do Caso" (que contém ambas as listas) está hoje gated por um único `{isEditing ? (...) : null}` — em modo leitura, NADA é mostrado hoje, nem sequer uma lista read-only. Isto contradiz a suposição inicial (lista sempre visível, só Adicionar/Remover gated). Como o objetivo literal da fase é "mantendo o comportamento atual", a decisão corrigida é: preservar exatamente este comportamento existente — ambas as secções continuam totalmente ocultas em modo leitura (dentro do `isEditing ? (...) : null` já existente), sem introduzir uma vista read-only nova. Isto NÃO é uma regressão a corrigir nesta fase — é o comportamento atual a preservar tal e qual, conforme o boundary explícito da fase.
- Mensagens de estado vazio (visíveis apenas em modo edição, como hoje) mantêm-se inalteradas: "Nenhum documento a tratar registado." (Documentos a Tratar) e o equivalente já existente para Deslocações.
- Ambas as listas continuam a fazer parte do payload de "Guardar" do formulário principal (staged localmente em `useState`, só persistem no submit) — comportamento inalterado; o botão "Guardar" no cabeçalho persiste tudo independentemente do separador ativo.

### Claude's Discretion
- Nome exato de variáveis internas (mantêm-se como já existem no código).
- Ordem de relocalização (Documentos a Tratar primeiro ou Deslocações primeiro) — sem impacto observável.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` — secções "Documentos a Tratar" (linhas ~890-940) e "Deslocações" (linhas ~941-1000+) já existem, atualmente dentro do branch `tab === "dados"`; estado local (`documentosATratar`, `deslocacoes`, `newDocATratar`, `newDeslocacao`, `addDocATratarModal`, dialog de deslocação) já implementado e funcional.
- `PlaceholderEmBreve` (Phase 76) — componente atualmente renderizado nos branches `tab === "documentos-a-tratar"` e `tab === "deslocacoes"`, a substituir pelo conteúdo real.
- `useEffect` de reset de diálogos de intake ao mudar de tab (Phase 76, fix CR-01) — já existe no ficheiro, só precisa de ser estendido para cobrir estes 2 diálogos adicionais.

### Established Patterns
- Padrão de lista `ul`/`li` com botão "Adicionar" (Dialog) + botão de remover inline (✕) — já estabelecido, reutilizar tal e qual.
- Gating `canEditClientes && editable` já é o padrão consistente em toda a página desde a Phase 75.

### Integration Points
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` — único ficheiro afetado; move JSX entre branches condicionais do mesmo componente, sem tocar hooks/mutations subjacentes.

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência visual específica — resultado deve ser visualmente idêntico ao conteúdo atual, apenas movido para o separador correto.

</specifics>

<deferred>
## Deferred Ideas

- Conteúdo real do separador Documentos Entregues (upload real) — Phase 79.
- Adicionar campo `data` a "Documentos a Tratar" — não pedido, fora de âmbito desta milestone.

</deferred>
