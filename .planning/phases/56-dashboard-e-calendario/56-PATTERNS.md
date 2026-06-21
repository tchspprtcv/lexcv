# Phase 56: Dashboard e Calendário - Pattern Map

**Mapeado:** 2026-06-21
**Ficheiros analisados:** 2 ficheiros a modificar
**Análogos encontrados:** 2 / 2

---

## File Classification

| Ficheiro a modificar | Role | Data Flow | Análogo mais próximo | Qualidade |
|---|---|---|---|---|
| `web/src/app/(dashboard)/dashboard/page.tsx` | component | request-response | si mesmo (modificação) | exact |
| `web/src/app/(dashboard)/agenda/page.tsx` | component | request-response | si mesmo (modificação) | exact |

---

## Descobertas Críticas

### DASH-01: Grid de KPIs

**Localização exata:** `dashboard/page.tsx`, linha **218**, função `DashboardKpis`

**Classe atual:**
```tsx
// linha 218
return <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{cards}</div>;
```

**Análise do comportamento atual:**
- `grid` — 1 coluna em mobile (< 640 px) ✓
- `sm:grid-cols-2` — 2 colunas a partir de 640 px ✓
- `lg:grid-cols-4` — 4 colunas a partir de 1024 px ✓
- **Breakpoint `md` (768 px) não coberto** — salta de 2 para 4 colunas, criando cards demasiado estreitos em tablets portrait

**Mudança mínima para DASH-01** — adicionar `md:grid-cols-2` e promover `lg` para `xl`:
```tsx
// ANTES (linha 218)
return <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">{cards}</div>;

// DEPOIS
return <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{cards}</div>;
```

> Justificação: `sm:grid-cols-2` já cobre 640–1024 px com 2 colunas; mover o salto para 4 colunas para `xl` (1280 px) mantém os cards confortáveis em tablets landscape (768–1279 px). Alternativa mais explícita se 3 colunas em `md` for desejada:
> ```tsx
> "grid gap-4 sm:grid-cols-2 md:grid-cols-2 lg:grid-cols-4"
> ```
> (idêntico ao primeiro mas declaração explícita — preferir a versão `xl` acima, que é mais limpa.)

---

### CAL-01: Vista padrão do Calendário em mobile

**Biblioteca de calendário:** **Nenhuma biblioteca externa** (react-big-calendar, FullCalendar, etc.).
O calendário é uma implementação **custom** em JSX puro, construída com `buildMonthGrid()` (linha 503–520) e renderizada através de um `div.grid.grid-cols-7` (linha 352).

**Não existe `defaultView` prop** — o componente tem apenas uma vista: a mensal. A navegação é por mês (`cursorMonthOverride`, linha 78–79).

**Estado de vista atual:** não existe `useState` para a vista — só existe para o mês corrente:
```tsx
// linha 78-79
const [cursorMonthOverride, setCursorMonthOverride] = React.useState<Date | null>(null);
const cursorMonth = cursorMonthOverride ?? initialMonth;
```

**Não existe nenhum `useMediaQuery` no projecto** — o hook precisa de ser criado.

**Padrão de detecção mobile existente no ficheiro** — o ficheiro `agenda/page.tsx` já usa `md:hidden` para mostrar/ocultar conteúdo por breakpoint CSS (linha 300):
```tsx
// linha 299-326 — bloco mobile-only de próximos eventos
{upcoming.length > 0 && (
  <div className="md:hidden space-y-3">
    ...
  </div>
)}
```

**Mudança mínima para CAL-01** — adicionar uma vista "dia/lista" para mobile, controlada por estado de vista:

**Opção A (recomendada) — hook `useMediaQuery` + estado de vista:**

Criar `web/src/hooks/use-media-query.ts`:
```typescript
"use client";
import * as React from "react";

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = React.useState(false);

  React.useEffect(() => {
    const mql = window.matchMedia(query);
    setMatches(mql.matches);
    const handler = (e: MediaQueryListEvent) => setMatches(e.matches);
    mql.addEventListener("change", handler);
    return () => mql.removeEventListener("change", handler);
  }, [query]);

  return matches;
}
```

Em `agenda/page.tsx`, dentro de `AgendaPageContent`:
```tsx
// adicionar no topo da função AgendaPageContent
const isMobile = useMediaQuery("(max-width: 767px)");
const [view, setView] = React.useState<"month" | "day">("month");

// inicializar vista padrão com base no dispositivo
React.useEffect(() => {
  setView(isMobile ? "day" : "month");
}, [isMobile]);
```

Depois condicionalmente renderizar a grelha de mês vs. lista diária de eventos:
```tsx
{view === "month" ? (
  <div className="grid grid-cols-7 ...">
    {/* calendário mensal existente */}
  </div>
) : (
  <DayListView events={filteredEvents} cursorMonth={cursorMonth} />
)}
```

**Opção B (mais simples) — CSS puro, sem novo hook:**
Ocultar a grelha mensal em mobile e mostrar apenas a lista `upcoming` que já existe:
```tsx
// Envolver o Card do calendário (linha 328) com:
<div className="hidden md:block">
  <Card ...> {/* grelha mensal */} </Card>
</div>

// O bloco md:hidden de "Próximos Eventos" (linha 299) já existe e serve como vista mobile
```
> Esta opção requer zero código novo mas não dá ao utilizador mobile uma vista diária navegável — apenas os próximos 4 eventos fixos.

---

## Shared Patterns

### Padrão de state com React.useState
**Fonte:** `agenda/page.tsx` linhas 43–58
**Aplicar a:** estado de vista `view` no CAL-01

```tsx
const [selectedProcessoId, setSelectedProcessoId] = React.useState<string>("");
const [cursorMonthOverride, setCursorMonthOverride] = React.useState<Date | null>(null);
```

Todos os estados locais usam `React.useState` com import `* as React from "react"` — seguir o mesmo padrão para o novo estado `view`.

### Padrão de condicional por permissão + responsive
**Fonte:** `agenda/page.tsx` linha 299–326
**Aplicar a:** toggle de vista mobile no CAL-01

```tsx
{upcoming.length > 0 && (
  <div className="md:hidden space-y-3">
    ...
  </div>
)}
```

O projecto já usa classes Tailwind `md:hidden` / `hidden md:block` para responsividade sem JS — é a abordagem mais consistente com o código existente.

### Padrão de imports do hook
**Fonte:** `dashboard/page.tsx` linha 11–15
```tsx
import { useClientes } from "@/hooks/use-clientes";
import { useDashboardKpis } from "@/hooks/use-dashboard-kpis";
```
Novo hook `useMediaQuery` deve residir em `web/src/hooks/use-media-query.ts` e ser importado como `@/hooks/use-media-query`.

---

## No Analog Found

| Ficheiro | Role | Data Flow | Razão |
|---|---|---|---|
| `web/src/hooks/use-media-query.ts` | hook | event-driven | Não existe nenhum hook de media query no projecto |

---

## Resumo de Alterações por Requisito

| Requisito | Ficheiro | Linha | Alteração |
|---|---|---|---|
| DASH-01 | `dashboard/page.tsx` | 218 | `lg:grid-cols-4` → `xl:grid-cols-4` (1 palavra) |
| CAL-01 (opção B CSS) | `agenda/page.tsx` | 328 | Envolver Card do calendário em `<div class="hidden md:block">` |
| CAL-01 (opção A JS) | `web/src/hooks/use-media-query.ts` | novo | Criar hook `useMediaQuery` (~15 linhas) |
| CAL-01 (opção A JS) | `agenda/page.tsx` | ~38 | Adicionar `isMobile`, estado `view`, `useEffect`, renderização condicional |

---

## Metadata

**Escopo de busca de análogos:** `web/src/`
**Ficheiros analisados:** 2
**Hooks existentes verificados:** `use-clientes`, `use-dashboard-kpis`, `use-eventos`, `use-processos`, `use-permissions`, `use-toast` — nenhum é de media query
**Componentes UI verificados:** `alert-dialog`, `badge`, `button`, `card`, `dialog`, `input`, `label`, `popover`, `sheet`, `table`, `textarea`, `toast`, `toaster` — sem utilitários de responsividade JS
