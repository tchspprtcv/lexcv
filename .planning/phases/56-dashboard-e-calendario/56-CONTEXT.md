# Phase 56: Dashboard e Calendário - Context

**Gathered:** 2026-06-21
**Status:** Ready for planning
**Source:** Roadmap + autonomous planning (--skip-research)

<domain>
## Phase Boundary

O dashboard e o calendário de agenda adaptam-se a mobile:
- KPI cards do dashboard: grid responsivo 1→2→4 colunas (mobile→tablet→desktop)
- Calendário de agenda: vista diária por defeito em mobile; mensal/semanal em tablet e desktop mantêm-se

</domain>

<decisions>
## Implementation Decisions

### DASH-01: KPI Grid adaptável
- Padrão: `grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4` no container dos KPI cards
- Localização: `web/src/app/(dashboard)/dashboard/page.tsx` — o grid de KPI cards
- Se o grid atual já tem `grid-cols-4` ou `sm:grid-cols-2 lg:grid-cols-4`, apenas adicionar `grid-cols-1` para mobile
- Se usa `sm:grid-cols-2`, trocar por `grid-cols-1 sm:grid-cols-2`
- Não tocar no conteúdo dos cards — apenas no container grid

### CAL-01: Calendário vista diária por defeito em mobile
- O calendário em `agenda/page.tsx` usa `react-big-calendar` (ou similar) com prop `defaultView`
- Padrão: ler `window.innerWidth` (ou `useMediaQuery` hook) ao montar e definir `defaultView="day"` se < 640px, senão `defaultView="month"`
- Alternativa CSS-only: não é possível para vista de calendário — requer lógica JS
- Estado do calendário (view atual) já provavelmente é `useState` em `agenda/page.tsx`
- Verificar como o calendário é inicializado antes de implementar

### Regressão Desktop
- Ambas as alterações são aditivas — desktop não é afectado (grid 4 colunas mantém-se, calendário mantém defaultView)

</decisions>

<code_context>
## Existing Code Insights

### Dashboard KPI grid
- `web/src/app/(dashboard)/dashboard/page.tsx` — contém o grid de KPI cards
- Verificar o className do container grid e quantas colunas tem actualmente
- Os cards podem ser componentes como `<KPICard>` ou divs inline

### Agenda calendário
- `web/src/app/(dashboard)/agenda/page.tsx` — o calendário
- A Phase 49 (Drag & Drop) e Phase 48 (Recorrência) modificaram este ficheiro
- O componente de calendário é provavelmente `react-big-calendar` ou similar
- Verificar: (1) qual é o `defaultView` actual, (2) se há `useState` para view, (3) se há import de `useEffect`

</code_context>

<specifics>
## Specific Ideas

- Para `useMediaQuery`: verificar se há um hook existente no codebase (`hooks/use-media-query.ts` ou similar) antes de criar um novo
- Alternativa sem hook: inicializar `useState` com `typeof window !== 'undefined' && window.innerWidth < 640 ? "day" : "month"` — simples e sem SSR issues se o componente for "use client"
- Para dashboard, verificar se `sm:grid-cols-2` já existe (Phase 55 pode ter tocado nessa linha se estava em formulário)

</specifics>

<deferred>
## Deferred Ideas

- Calendário swipeable entre dias em mobile — fora de scope (gestos)
- Mini-calendário de navegação em mobile — fora de scope
- Lista de eventos em vez de grid no calendário mobile — fora de scope (Phase 54 Agenda já tem cards)
- Persistir preferência de vista do calendário em localStorage — fora de scope

</deferred>
