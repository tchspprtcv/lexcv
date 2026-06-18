# Phase 49: Drag & Drop no Calendário - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase adiciona drag & drop ao calendário mensal: o utilizador pode arrastar um evento (não recorrente, não prazo) para outra célula do mesmo mês e a data é persistida via `PUT /eventos/{id}`. A atualização é otimista — o calendário move o evento imediatamente e reverte em caso de erro API. Eventos recorrentes e prazos não são arrastáveis.

**Fora do âmbito:** drag entre meses, drag de prazos, drag de instâncias recorrentes, touch/mobile drag, drag para reordenar dentro do mesmo dia.

</domain>

<decisions>
## Implementation Decisions

### Abordagem Técnica
- **HTML5 Drag & Drop API** — sem biblioteca externa; suportado nativamente em todos os browsers modernos
- **Frontend-only** — o backend já tem `PUT /eventos/{id}` que aceita campos incluindo `dataInicio` e `dataFim`; sem alterações no backend
- **Único ficheiro alterado:** `web/src/app/(dashboard)/agenda/page.tsx`

### Estado de Drag
- `dragState: { eventId: number; originalDataInicio: string; originalDataFim: string } | null` — via `React.useState`
- `dragOverKey: string | null` — chave da célula de destino actualmente hovered, para highlight visual
- `optimisticOverrides: Map<number, string>` — mapa de eventId → novo dataInicio (substitui data no render sem alterar o cache TanStack Query)

### Draggable Events
- Apenas eventos com `!e.isPrazo && !e.isRecurrenceInstance` são arrastáveis
- No pill: `draggable={canDrag}` e `onDragStart`, `onDragEnd` handlers
- `draggable={false}` em prazos e instâncias recorrentes (cursor: default)
- Em `onDragStart`: chamar `e.dataTransfer.setData('text/plain', String(event.id))` e set `dragState`

### Drop Zones (células do calendário)
- Cada `<div>` de célula do calendário recebe: `onDragOver={e => { e.preventDefault(); setDragOverKey(key); }}`, `onDragLeave={() => setDragOverKey(null)}`, `onDrop={handleDrop}`
- Highlight visual: quando `dragOverKey === key`, adicionar classe `ring-2 ring-blue-400 bg-blue-50 dark:bg-blue-900/20` à célula
- Não usar `day.isOutsideMonth` como drop zone (ignorar drops em dias fora do mês corrente)

### Optimistic Update
- Em `onDrop`: compute o novo `dataInicio` preservando a hora original e mudando apenas a data (parse `originalDataInicio`, substituir Y-M-D com a data da célula de destino)
- Adicionar ao `optimisticOverrides` o `eventId → newDataInicio`; limpar `dragOverKey` e `dragState`
- Em `allUnifiedEvents`: para cada evento, verificar se existe override — se sim, usar o override como `dataInicio` (e ajustar `dataFim` proporcionalmente)
- Chamar `updateEvento.mutate({ dataInicio: newDataInicio, dataFim: newDataFim })` (hook `useUpdateEvento` já existe)
- `onSuccess`: limpar o override, chamar `queryClient.invalidateQueries(["eventos","list"])`
- `onError`: remover override do map (reverte otimisticamente), mostrar toast de erro via `apiFetch` error pattern existente

### Hook para PUT
- `useUpdateEvento(id)` já existe em `use-eventos.ts` — reutilizar directamente; instanciar por cada evento no drag ou usar um único `useMutation` com `variables.id`
- Para simplificar (sem instanciar N hooks): criar um único `useMutation` directo no componente da agenda que toma `{ id, dataInicio, dataFim }` e chama `apiFetch`; invalida `["eventos","list"]` on success

### Restrições e UX
- Cursor `grab` em eventos arrastáveis (`cursor-grab` Tailwind class)
- Durante o drag: `cursor-grabbing` (via `dragState !== null`)
- Eventos fora do mês visível (isOutsideMonth): não são drop zones (células `opacity-50` — ignorar drop)
- Se o evento for largado na mesma célula de onde foi arrastado: ignorar (não chamar API)

</decisions>

<canonical_refs>
## Canonical References

- `web/src/app/(dashboard)/agenda/page.tsx` — o único ficheiro a modificar; ler todo antes de planear
- `web/src/hooks/use-eventos.ts` — padrão existente de useUpdateEvento(id) e apiFetch
- `web/src/lib/api.ts` — apiFetch wrapper
- `.planning/REQUIREMENTS.md` — AGE-07, AGE-08
- `.planning/ROADMAP.md` — Phase 49 success criteria

</canonical_refs>

<specifics>
## Specific Ideas

### Compute new dates preserving time
```ts
function moveDatePreservingTime(originalISO: string, newDateKey: string): string {
  // originalISO: "2026-07-10T14:00:00", newDateKey: "2026-07-15"
  return newDateKey + originalISO.slice(10); // replace date part
}
```

### Single useMutation for drag-drop
```ts
const dragDropMutation = useMutation({
  mutationFn: ({ id, dataInicio, dataFim }: { id: number; dataInicio: string; dataFim?: string }) =>
    apiFetch<Evento>(`/eventos/${id}`, { method: 'PUT', body: JSON.stringify({ dataInicio, dataFim }) }),
  onSuccess: () => {
    setOptimisticOverrides(new Map());
    queryClient.invalidateQueries({ queryKey: ['eventos', 'list'] });
  },
  onError: () => {
    setOptimisticOverrides(new Map()); // revert
    // toast shown by apiFetch error handler automatically
  },
});
```

### Highlight drop zone
```tsx
className={cn(
  "min-h-[120px] bg-white dark:bg-[#020617] p-2 transition-colors",
  !day.isOutsideMonth && dragOverKey === key && "ring-2 ring-inset ring-blue-400 bg-blue-50 dark:bg-blue-900/20",
  day.isOutsideMonth && "bg-slate-50/50 dark:bg-slate-900/20 opacity-50",
)}
```

</specifics>

<deferred>
## Deferred Ideas

- Drag entre meses (precisaria de navegação de mês inline) — adiado
- Touch/mobile drag — HTML5 D&D não funciona em touch; requer biblioteca como dnd-kit — adiado
- Drag de instâncias recorrentes — conflitua com série; adiado
- Drop em dias fora do mês — navegação implicita, adiado

</deferred>
