# Phase 105: Módulos Clientes + Processos (combinados) - Context

**Gathered:** 2026-07-16
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode)

<domain>
## Phase Boundary

A Ficha de Cliente (`clientes/[id]/page.tsx`) e a Ficha de Processo (`processos/[id]/page.tsx`) usam `Tabs` reais e acessíveis em vez de botões-toggle manuais; os `<select>` nativos em ambos os módulos usam `NativeSelect`; `Avatar` representa advogados/administrativos/testemunhas; `Breadcrumb` substitui os cabeçalhos ad-hoc. Entregues em conjunto (nunca isoladamente) para nunca deixar as duas fichas visivelmente inconsistentes entre si. Cobre CLP-01, CLP-02, CLP-03, CLP-04, CLP-05.

</domain>

<decisions>
## Implementation Decisions

### Mecânica da Migração para Tabs
- Comportamento de montagem: usar o padrão Radix `TabsContent` (desmonta separadores inativos) sem `forceMount` — replica o comportamento atual de mount/unmount condicional
- Separadores condicionados por RBAC (Processos/Pareceres na Ficha de Cliente; Auditoria na Ficha de Processo): omitir o `TabsTrigger` por completo quando sem permissão — igual ao comportamento atual dos botões
- Sincronização `?tab=` da Ficha de Processo: preservar via `Tabs value={tab}` controlado, mantendo o `useSearchParams`/`useEffect` de sincronização existente
- Variante do `TabsList`: `default` (visual segmentado, mais próximo do grupo de botões `variant="secondary"/"outline"` atual), não `line`
- `overflow-x-auto` (Clientes) e `flex flex-wrap` (Processos) — preservar o comportamento responsivo específico de cada ficha tal como está hoje, não uniformizar entre as duas

### Migração de `<select>` Nativo
- `NativeSelect` uniformemente em todos os casos — substituto direto para campos ligados a `form.register()`/RHF; não usar `Select` (Radix combobox) nesta fase
- Eliminar ambos os `selectClassName` divergentes (Clientes `rounded-none`, Processos `rounded-md`) — o estilo próprio do `NativeSelect` passa a ser a única fonte de verdade
- `size="default"` em todos os casos novos
- Âmbito: TODOS os `<select>` nativos em Clientes e Processos — não só as fichas `[id]/page.tsx`, mas também `clientes/page.tsx`/`processos/page.tsx` (filtros de lista), `clientes/novo`, `processos/novo`, `processos/[id]/editar`, `clientes/merge`

### Tratamento visual com Avatar
- Entidades com `Avatar`: Advogados, Administrativos (ambos via `ResponsaveisCard` na Ficha de Cliente) e Testemunhas (tabela na Ficha de Processo) — nomeados explicitamente em CLP-04. Partes não incluídas.
- Conteúdo: iniciais via `AvatarFallback`, reutilizando a mesma lógica de derivação de iniciais já estabelecida nos chips de "Nome" de Clientes (Phase 104, `clientes/columns.tsx`)
- Tamanho: `sm` (compacto, cabe nas linhas de card/tabela existentes sem alterar significativamente a altura de linha)
- Tabela de Testemunhas: avatar+nome só na célula "Nome"; colunas Tipo/Contacto/Ações inalteradas

### Breadcrumb e Âmbito (expandido após discussão)
- `Breadcrumb`/`BreadcrumbList`/`BreadcrumbItem`/`BreadcrumbLink`(`asChild` com `Link`)/`BreadcrumbSeparator`/`BreadcrumbPage` substitui o `<div>+Link+"/"` em **todas** as páginas do módulo com o mesmo padrão: `clientes/[id]/page.tsx`, `processos/[id]/page.tsx`, `clientes/novo/page.tsx`, `processos/novo/page.tsx`, `processos/[id]/editar/page.tsx`, `clientes/merge/page.tsx` — decisão explícita do utilizador de expandir além do texto literal do critério de sucesso #5 (que só nomeava as 2 fichas)
- Inconsistência do `h1` (font-semibold em Clientes vs font-bold em Processos): reconciliar para `font-bold` em ambas as fichas — correção lateral explicitamente autorizada
- Aba "Documentos" da Ficha de Processo: migrar para o padrão `DataTable` partilhado (Phase 104), incluindo `columns.tsx` próprio — decisão explícita do utilizador de expandir além do âmbito original de CLP-01..05 (que não mencionava DataTable). Reaplicar o mesmo padrão `getRowId`, sem `getFilteredRowModel()`, badges reutilizando o vocabulário fechado do `Badge` já estabelecido
- Abas Partes e Fases da Ficha de Processo (hoje tabelas HTML `<table>` simples): migrar para os primitivos `Table`/`TableHeader`/`TableRow`/`TableCell` reconciliados (Phase 102) — decisão explícita do utilizador de expandir além do âmbito original. NÃO usar o padrão `DataTable` completo (sort/paginação/toolbar) aqui — são listas tipicamente pequenas por processo, sem paginação real necessária; só a troca do elemento `<table>` bruto pelos primitivos `Table` reconciliados, preservando toda a lógica de Dialog "Adicionar"/RBAC/edição inline existente

### Claude's Discretion
- Nomes exatos dos ficheiros/componentes novos (ex.: `processos/documentos-columns.tsx` ou nome equivalente para o novo DataTable da aba Documentos do processo)
- Ordem exata de migração das 6 páginas de Breadcrumb dentro do plano de execução

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/ui/tabs.tsx` — `Tabs, TabsList, TabsTrigger, TabsContent, tabsListVariants` (variant `"default"|"line"`), já existe desde a Phase 101
- `web/src/components/ui/native-select.tsx` — `NativeSelect, NativeSelectOptGroup, NativeSelectOption`, wrapper fino sobre `<select>` real com chevron, `size?: "sm"|"default"`, compatível com `register()` do RHF
- `web/src/components/ui/avatar.tsx` — `Avatar, AvatarImage, AvatarFallback, AvatarGroup, AvatarGroupCount, AvatarBadge`, `size?: "default"|"sm"|"lg"`
- `web/src/components/ui/breadcrumb.tsx` — `Breadcrumb, BreadcrumbList, BreadcrumbItem, BreadcrumbLink, BreadcrumbPage, BreadcrumbSeparator, BreadcrumbEllipsis` (sem Radix, composável, `BreadcrumbLink` suporta `asChild`)
- `web/src/components/shared/data-table/` (Phase 104) — `DataTable`, `DataTableColumnHeader`, `DataTablePagination`, `DataTableViewOptions`, todos reutilizáveis para a nova aba Documentos do processo
- Lógica de derivação de iniciais já usada em `clientes/columns.tsx` (chips "TS"/"FH")

### Established Patterns
- `clientes/[id]/page.tsx`: `TabKey` union de 7 valores, estado `tab`/`setTab`, botões `variant={tab === "X" ? "secondary" : "outline"}`, envolvidos em `overflow-x-auto`; 2 de 7 tabs gated por `canViewProcessos`/`canViewPareceres`; conteúdo despachado por cadeia `tab === "x" ? (...) : ...`
- `processos/[id]/page.tsx`: `TabKey` união de 8 valores + `TAB_KEYS` array, sincronização com `?tab=` via `useSearchParams`, `flex flex-wrap` (sem scroll horizontal), 1 de 8 tabs (Auditoria) gated por `canManageProcessos`; existe um segundo padrão de chips de filtro (`selectedTipos`, `aria-pressed`) dentro da aba Timeline — não confundir com a tab bar principal, não faz parte desta migração
- RBAC: `usePermissions().can.view/edit/manage(scope)` é o padrão dominante; uma única exceção lê `.roles.includes("ADMIN")` diretamente em `ClienteParecerTab` (linha ~1150) — não tocar, fora de âmbito
- Os dois `selectClassName` (Clientes `rounded-none` vs Processos `rounded-md`) nunca foram partilhados entre os dois ficheiros

### Integration Points
- `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `web/src/app/(dashboard)/processos/[id]/page.tsx` (fichas principais)
- `web/src/app/(dashboard)/clientes/novo/page.tsx`, `web/src/app/(dashboard)/processos/novo/page.tsx`, `web/src/app/(dashboard)/processos/[id]/editar/page.tsx`, `web/src/app/(dashboard)/clientes/merge/page.tsx` (breadcrumb + selects)
- `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/processos/page.tsx` (filtros de lista — selects)
- `ResponsaveisCard` (dentro de `clientes/[id]/page.tsx`) — Avatar para advogados/administrativos
- Tabela de Testemunhas (dentro de `processos/[id]/page.tsx`) — Avatar na célula Nome
- Nova aba Documentos do processo — novo `columns.tsx` + `<DataTable>`, reaproveitando `web/src/hooks/use-documentos.ts` filtrado por `processo_id`

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- Migrar o `Select` (Radix combobox) em vez de `NativeSelect` para pickers de entidades (advogado/responsável) — considerado e explicitamente descartado nesta fase a favor de uniformidade com `NativeSelect`
- Uniformizar o comportamento responsivo da tab bar entre as duas fichas (`overflow-x-auto` vs `flex-wrap`) — mantido como está, cada ficha preserva o seu próprio padrão responsivo

</deferred>
