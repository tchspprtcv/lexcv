# Phase 104: Padrão DataTable Partilhado - Context

**Gathered:** 2026-07-16
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode)

<domain>
## Phase Boundary

Existe um único padrão DataTable reutilizável, construído sobre o `Table` já reconciliado (Phase 102), adotado pelas 5 listas que precisam dele (Clientes, Processos, Pareceres, Financeiro, Documentos) sem duplicar os filtros já servidos pelo backend via TanStack Query. Cobre DTB-01, DTB-02, DTB-03.

</domain>

<decisions>
## Implementation Decisions

### Arquitetura do Padrão DataTable
- Localização: `web/src/components/shared/data-table/` (novo diretório), reutilizável pelos 5 ecrãs
- Integração de filtros: DataTable só faz ordenação/paginação client-side sobre dados já filtrados pelo servidor; filtros existentes (pesquisa, estado, etc.) continuam geridos pelos hooks `use-*` atuais, sem duplicar em `getFilteredRowModel()`
- Sem row-selection/checkboxes — sem ações em massa no produto hoje (decisão já registada em REQUIREMENTS.md Out of Scope)

### Adoção nos 5 Ecrãs
- Construir o padrão + adotar nos 5 ecrãs (Clientes, Processos, Pareceres, Financeiro, Documentos) na mesma fase, não faseado para depois
- Vista mobile (cards empilhados) inalterada — só o ramo desktop (`hidden md:block`) migra para o DataTable partilhado
- Todas as colunas visíveis ordenáveis por omissão, exceto onde não faz sentido (ex.: coluna de ações)

### Nova Dependência e Verificação
- `@tanstack/react-table` precisa do mesmo checkpoint de legitimidade de pacotes da Fase 101 (sonda npm + aprovação humana antes de instalar)
- Checkpoint visual humano obrigatório no fecho da fase (build+typecheck automáticos + verificação visual de ordenação/paginação/toolbar nos 5 ecrãs, ambos os temas)

### Claude's Discretion
- Nomes exatos dos ficheiros/componentes dentro de `data-table/`
- Shape exato de `columns.tsx` por ecrã

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/ui/table.tsx` já reconciliado (Phase 102, tokens `--card`, `rounded-lg`)
- Padrão dual-view (`hidden md:block` desktop / `md:hidden` mobile cards) já estabelecido desde v2.3 em Clientes/Processos/Pareceres/Financeiro/Documentos
- Hooks `use-clientes`, `use-processos`, `use-pareceres`, `use-honorarios`/financeiro, `use-documentos` já servem filtros server-side via TanStack Query

### Established Patterns
- Fase 101's package-legitimacy gate (`pnpm view` probe + sign-off humano) — reutilizar o mesmo protocolo para `@tanstack/react-table`
- Checkpoint visual humano (browser + getComputedStyle) — padrão estabelecido nas Fases 101/102

### Integration Points
- `web/package.json` (nova dependência)
- 5 páginas de lista: `web/src/app/(dashboard)/{clientes,processos,pareceres,financeiro,documentos}/page.tsx`

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — discussão não saiu do âmbito da fase.

</deferred>
