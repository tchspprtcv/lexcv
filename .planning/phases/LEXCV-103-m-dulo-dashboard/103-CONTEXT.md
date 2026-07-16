# Phase 103: Módulo Dashboard - Context

**Gathered:** 2026-07-16
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode)

<domain>
## Phase Boundary

Os estados de loading e vazio do Dashboard usam os primitivos oficiais `Skeleton`/`Empty` (adicionados na Phase 101) em vez de texto ad hoc — o módulo com menor necessidade de primitivos novos, servindo para validar visualmente a nova camada de tokens antes dos módulos mais profundos a adotarem. Cobre DASH-01, DASH-02.

</domain>

<decisions>
## Implementation Decisions

### Estados de Loading (Skeleton)
- Forma do Skeleton para os KPI cards: bloco retangular a imitar a forma real do card (ícone + número + label), não uma barra genérica
- "Atividade Recente": mostrar 3-4 linhas de skeleton enquanto carrega
- Sem animação de fade customizada na transição skeleton→conteúdo real (comportamento nativo do React)

### Estados Vazios (Empty)
- Ícone neutro relacionado no componente Empty (sem cor de destaque)
- Texto do Empty state: mensagem curta em português, tom institucional consistente (ex.: "Sem atividade recente")

### Claude's Discretion
- Escolha exata do ícone (lucide-react) para cada estado vazio
- Detalhes de layout do Skeleton dentro de cada card

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Skeleton`/`Empty` já existem em `web/src/components/ui/` desde a Phase 101
- `web/src/app/(dashboard)/dashboard/page.tsx` — KPI cards já usam `Card`/`CardContent`/`Badge`, texto "A carregar..." bare em pelo menos 2 locais (KPI cards, Atividade Recente)

### Established Patterns
- `Card`/`CardContent` já reconciliados na Phase 102 (tokens `--card`, `rounded-lg`)
- `Tooltip` já disponível se necessário para ícones informativos

### Integration Points
- `web/src/app/(dashboard)/dashboard/page.tsx` (KPI cards + Atividade Recente)

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

Nenhuma — discussão não saiu do âmbito da fase.

</deferred>
