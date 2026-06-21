# Phase 54: Listas e Tabelas - Context

**Gathered:** 2026-06-21
**Status:** Ready for planning
**Source:** Roadmap + autonomous planning (--skip-research)

<domain>
## Phase Boundary

As páginas de listagem da aplicação adaptam o seu formato ao ecrã. Em mobile (< 768px):
- Páginas com listas simples (Clientes, Documentos, Financeiro, Agenda) substituem a tabela por cards empilhados verticalmente
- Tabelas complexas dentro do detalhe de processos (Partes, Movimentações, Fases) ganham scroll horizontal nativo

Desktop permanece sem qualquer alteração.

</domain>

<decisions>
## Implementation Decisions

### Cards em Mobile (TAB-01)
- Padrão: mostrar cards com `md:hidden` em mobile e `hidden md:block` na tabela — CSS puro, sem JS
- Cards em mobile têm os campos essenciais (nome/título, status, data, acção principal) — não todos os campos
- Acções (ver, editar, apagar) ficam como botões ou link no card — touch-friendly (min 44px)
- Páginas alvo: Clientes (`/clientes`), Documentos (`/documentos`), Financeiro (`/financeiro`), Agenda (`/agenda`)

### Scroll Horizontal (TAB-02)
- Padrão: envolver as tabelas complexas em `<div className="overflow-x-auto">` — CSS puro, sem biblioteca
- Tabelas alvo: Partes do Processo, Movimentações, Fases (dentro de `/processos/[id]`)
- Manter colunas existentes — apenas adicionar scroll horizontal ao container
- Nenhuma coluna deve ter largura mínima excessiva em mobile (revisar `min-w-*` agressivos)

### Breakpoints e Padrão CSS
- Breakpoint de corte: `md` (768px) — consistente com Phase 53
- Padrão para cards: `<div className="block md:hidden">` para card, `<div className="hidden md:block">` para tabela
- Não criar componente genérico de card — implementar inline em cada página (YAGNI — 4 páginas distintas)

### Campos Essenciais por Card
- **Clientes**: nome, NIF/BI, telefone, badge estado de conta
- **Documentos**: nome do ficheiro, tipo, data de upload, processo associado, botão download
- **Financeiro (honorários)**: processo, valor, status (badge), data
- **Agenda (eventos)**: título, data/hora, categoria, status concluído

</decisions>

<code_context>
## Existing Code Insights

### Pages to Modify
- `web/src/app/(dashboard)/clientes/page.tsx` — tabela de clientes, TanStack Query `useClientes`
- `web/src/app/(dashboard)/documentos/page.tsx` — tabela de documentos, `useDocumentos`
- `web/src/app/(dashboard)/financeiro/page.tsx` — tabela de honorários, `useHonorarios`
- `web/src/app/(dashboard)/agenda/page.tsx` — lista de eventos, `useEventos`
- `web/src/app/(dashboard)/processos/[id]/page.tsx` — sub-tabelas de Partes, Movimentações, Fases

### Established Patterns
- Tabelas usam `<table className="w-full text-sm">` com `<thead>` e `<tbody>` — padrão consistente
- `Badge` component de `@/components/ui/badge` para status
- `Button` com `size="sm"` para acções em tabelas
- `cn()` utility importado em todas as páginas
- `Link` de next/link para navegação a partir de rows

### Key Data Types
- `ClienteResponse` — `id, nome, nif, telefone, email, estadoConta`
- `DocumentoResponse` — `id, nome, tipo, dataUpload, processoId, processoNumero`
- `HonorarioResponse` — `id, processoId, processoNumero, valor, status, dataCriacao`
- `EventoResponse` — `id, titulo, dataInicio, categoria, concluido, processoId`

</code_context>

<specifics>
## Specific Ideas

- Cards mobile devem ter `rounded-lg border border-slate-200 dark:border-slate-700 p-4` — padrão da UI existente
- Para ações no card: Link `<Button variant="ghost" size="sm">Ver</Button>` — same pattern as table rows
- Tabelas de scroll: `<div className="overflow-x-auto -mx-4 px-4">` para compensar padding do container pai
- Em `/processos/[id]`, as sub-tabelas já podem estar em `<div>` containers — verificar antes de modificar

</specifics>

<deferred>
## Deferred Ideas

- Infinite scroll / paginação em mobile — fora de scope deste milestone
- Gestos swipe em cards (para apagar/editar) — fora de scope
- Virtualização de listas longas — fora de scope
- Skeleton loading states específicos para cards — fora de scope (já existe spinner global)

</deferred>
