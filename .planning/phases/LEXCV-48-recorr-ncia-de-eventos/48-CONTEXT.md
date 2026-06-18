# Phase 48: Recorrência de Eventos - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase adiciona suporte a eventos recorrentes ao módulo de agenda. O utilizador pode criar um evento com regra de recorrência (Diária, Semanal, Mensal) e uma data de fim obrigatória. O backend armazena o evento master com os campos de recorrência e expande as instâncias virtuais na resposta do `GET /eventos`. O calendário exibe todas as instâncias nas datas corretas com um indicador visual. Ao apagar, surge um diálogo com duas opções: "Apagar esta instância" (adiciona à lista de exceções do master) ou "Apagar toda a série" (deleta o master).

**Fora do âmbito desta fase:** drag & drop (Phase 49), recorrência infinita (sem data de fim), edição de instâncias futuras em bloco, notificações push.

</domain>

<decisions>
## Implementation Decisions

### Modelo de Dados (Backend)
- Adicionar 3 colunas à entidade `Evento` via `ddl-auto=update` (migração automática no arranque):
  - `recurrence_rule` — String nullable, valores `DAILY | WEEKLY | MONTHLY`, null = evento normal
  - `recurrence_end_date` — LocalDate nullable, data de fim das instâncias (obrigatória quando rule != null)
  - `recurrence_exceptions` — String nullable, datas excluídas separadas por vírgula (ex: `2026-07-04,2026-07-11`)
- Sem tabela de instâncias separada — instâncias são objetos virtuais expandidos em memória

### Expansão de Instâncias (Backend)
- `GET /eventos` (com ou sem filtros `dataInicio`/`dataFim`) expande instâncias em memória depois de carregar os masters do DB:
  1. Para cada Evento com `recurrenceRule != null`, iterar datas desde `dataInicio` até `recurrenceEndDate` com o intervalo correto (1 dia / 7 dias / 1 mês)
  2. Criar objetos `Evento` virtuais copiando todos os campos do master, atualizando `dataInicio`/`dataFim` para a data da instância, e adicionando campo `recurrenceInstanceDate` na resposta JSON
  3. Saltar datas em `recurrenceExceptions`
  4. Saltar instâncias fora do window `dataInicio`/`dataFim` pedido
  5. NÃO incluir o master na lista final — apenas as instâncias expandidas (o master é apenas armazenamento)
  6. Se não há filtro de datas, expandir num janela razoável (ex: ±1 ano a partir de hoje) para não gerar instâncias infinitas
- A expansão usa `Evento::setDataInicio`, `Evento::setDataFim` com offsets calculados — sem persistir nada
- O campo `id` da instância virtual é o mesmo `id` do master (para actions como toggle concluido funcionar)
- Adicionar campo `isRecurrenceInstance` (boolean) ao DTO/resposta para o frontend distinguir

### Deleção (Backend)
- Manter `DELETE /eventos/{id}` existente (deleta o master + efetivamente todas as instâncias)
- Adicionar `DELETE /eventos/{id}/instances?date=YYYY-MM-DD` — adiciona a data a `recurrenceExceptions` do master (sem deletar nada do DB)
- A lógica "apagar toda a série" usa o endpoint existente `DELETE /eventos/{id}`
- A lógica "apagar esta instância" usa o novo endpoint `DELETE /eventos/{id}/instances?date=YYYY-MM-DD`

### Frontend — Tipos
- Adicionar a `Evento`: `recurrenceRule?: 'DAILY' | 'WEEKLY' | 'MONTHLY'`, `recurrenceEndDate?: string`, `recurrenceExceptions?: string`, `isRecurrenceInstance?: boolean`
- Adicionar a `EventoCreateRequest` e `EventoUpdateRequest`: os mesmos campos opcionais
- Adicionar hook `useDeleteEventoInstance(id: number)` para o novo endpoint

### Frontend — Formulário (Criação/Edição)
- No schema Zod `eventoSchema`: campo `recurrenceRule` opcional com `.enum(['NONE', 'DAILY', 'WEEKLY', 'MONTHLY'])`, e `recurrenceEndDate` com `.superRefine()` que o torna obrigatório quando `recurrenceRule != 'NONE'`
- Na página de criação de evento (`/agenda/novo` ou o Dialog existente): adicionar secção "Recorrência" com Select (Nenhuma/Diária/Semanal/Mensal) e, quando não Nenhuma, mostrar DateInput para data de fim
- Não enviar campos de recorrência quando `recurrenceRule === 'NONE'`

### Frontend — Calendário
- As instâncias expandidas chegam com `isRecurrenceInstance: true` — mostrar um ícone `↻` (ou similar) no pill do evento no calendário
- A lógica atual `eventosByDay` em `agenda/page.tsx` já funciona corretamente para instâncias (cada instância tem o `dataInicio` correto)

### Frontend — Diálogo de Delete
- Na página de detalhe do evento (`/agenda/[id]/page.tsx`), ao clicar "Apagar":
  - Se `evento.recurrenceRule` for null/undefined: comportamento atual (AlertDialog simples)
  - Se `evento.recurrenceRule` for definido E `isRecurrenceInstance` for true: AlertDialog com dois botões: "Apagar esta instância" e "Apagar toda a série"
  - "Apagar esta instância": chama `useDeleteEventoInstance(id).mutate({ date: evento.dataInicio.slice(0,10) })` — navega para `/agenda` on success
  - "Apagar toda a série": chama `useDeleteEvento(id).mutate()` — navega para `/agenda` on success

### Claude's Discretion
- Se não existir página `/agenda/[id]/page.tsx`, criar com os campos relevantes
- Ícone exato para indicador de recorrência no calendário (sug: `⟳` ou `●` colorido diferente)
- Estrutura exata do Zod superRefine para a validação condicional de `recurrenceEndDate`

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `backend/src/main/java/com/lexcv/models/Evento.java` — Entidade JPA atual (sem recorrência)
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` (linhas 1470–1630) — Endpoints agenda existentes
- `web/src/types/eventos.ts` — Tipos TypeScript atuais
- `web/src/hooks/use-eventos.ts` — Hooks TanStack Query existentes
- `web/src/app/(dashboard)/agenda/page.tsx` — Calendário (buildMonthGrid, eventosByDay, dayEvents rendering)
- `web/src/schemas/` — Padrão Zod + react-hook-form existente
- `.planning/REQUIREMENTS.md` — AGE-03 a AGE-06
- `.planning/ROADMAP.md` — Phase 48 success criteria

</canonical_refs>

<specifics>
## Specific Ideas

### Backend — expansão de instâncias
```java
// Dentro de listEventos(), após aplicar os filtros normais:
List<Evento> expanded = new ArrayList<>();
for (Evento e : eventos) {
    if (e.getRecurrenceRule() == null) {
        expanded.add(e);
        continue;
    }
    // expand instances...
    LocalDateTime cursor = e.getDataInicio();
    Set<String> exceptions = parseExceptions(e.getRecurrenceExceptions());
    while (!cursor.toLocalDate().isAfter(e.getRecurrenceEndDate())) {
        String dateKey = cursor.toLocalDate().toString();
        if (!exceptions.contains(dateKey)) {
            if (withinWindow(cursor, start, end)) {
                Evento instance = copyWithNewDates(e, cursor);
                expanded.add(instance);
            }
        }
        cursor = advance(cursor, e.getRecurrenceRule());
    }
}
return ResponseEntity.ok(expanded);
```

### Frontend — indicador visual no calendário
No pill do evento em `agenda/page.tsx`:
```tsx
{e.isRecurrenceInstance && <span className="mr-1">↻</span>}
```

</specifics>

<deferred>
## Deferred Ideas

- Recorrência sem data de fim (infinita) — requer paginação especial, adiado para v3+
- Editar todas as instâncias futuras ("from here") — padrão complexo, adiado
- Arrastar instâncias recorrentes — bloqueado em Phase 49 (recurrentes não são arrastáveis)
- Suporte iCal/RRULE standard — overkill para esta fase

</deferred>
