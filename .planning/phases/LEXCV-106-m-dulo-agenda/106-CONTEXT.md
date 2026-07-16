# Phase 106: Módulo Agenda - Context

**Gathered:** 2026-07-16
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous batch mode)

<domain>
## Phase Boundary

Os inputs de data dos formulários de criação/edição de evento da Agenda (`agenda/novo`, `agenda/[id]/editar`) usam o `Calendar` oficial (shadcn/react-day-picker), e os filtros da lista da Agenda usam `Select`. A vista de calendário mensal existente (grelha CSS com drag-and-drop) não é alterada. Cobre AGD-36, AGD-37.

**Esclarecimento de âmbito (decisão travada):** o texto do requisito AGD-36 ("formulários de criar/editar prazo") não corresponde a uma entidade separada — os ficheiros reais no módulo Agenda são `agenda/novo`/`agenda/[id]/editar` (entidade `Evento`, cujo `tipo` pode ser `PRAZO` entre outros). Existe uma entidade "Prazo" literal, distinta, mas vive em `processos/[id]/page.tsx` (Dialog "Novo Prazo", sem formulário de edição) — module Processos, fora do âmbito desta fase (que depende só da Phase 102, não da Phase 105).

</domain>

<decisions>
## Implementation Decisions

### Âmbito e Calendar
- Âmbito = ficheiros do módulo Agenda apenas: `agenda/novo/page.tsx`, `agenda/[id]/editar/page.tsx` (formulários) e `agenda/page.tsx` (filtros). O Dialog "Novo Prazo" em `processos/[id]/page.tsx` fica fora de âmbito (módulo diferente)
- `dataInicio`/`dataFim` (campos `datetime-local`, data+hora): compor `Popover`+`Calendar` (só data) + `Input type="time"` separado ao lado, sincronizados internamente num único valor `datetime-local` para o `react-hook-form`
- `recurrenceEndDate` (campo `date` puro, só visível quando recorrência ≠ "Nenhuma"): migração direta para `Popover`+`Calendar`+`Button`, seguindo o padrão oficial shadcn "Date Picker" — esta é a primeira composição Popover+Calendar no projeto, construída de raiz
- A vista de calendário mensal existente (`agenda/page.tsx`, grelha CSS + drag-and-drop, `buildMonthGrid`/`dayKey`/`cursorMonth`) não é tocada — é código completamente distinto dos filtros e formulários

### Select nos filtros + formulários
- Os 3 filtros da lista da Agenda (Processo, Categoria, Estado — `agenda/page.tsx`, partilham o botão "Limpar Filtros") migram para `Select` (Radix), correspondendo à redação literal de AGD-37 e sendo a primeira utilização deste padrão de filtro na app
- Os ~6 `<select>` nativos nos formulários criar/editar (Processo, Categoria/tipo, Prioridade, Recorrência, em `agenda/novo`+`agenda/[id]/editar`) migram para `NativeSelect` (não `Select`) por consistência com o padrão já estabelecido na Phase 105 (NativeSelect para campos ligados a `react-hook-form`, Select reservado para filtros de lista) — decisão explícita de expandir além do texto literal de AGD-37, que só menciona os filtros

### Correção de bug de RBAC (bundled)
- Os 4 ficheiros do módulo Agenda (`agenda/page.tsx:26`, `agenda/novo/page.tsx:31`, `agenda/[id]/page.tsx:62`, `agenda/[id]/editar/page.tsx:65`) usam ainda o padrão `!permissions.isLoading && !canX` já identificado e corrigido como bug nas Phases 103 e 105 (`isLoading` pode ser `false` antes da query de permissões arrancar, causando um flash de "Acesso negado"). Corrigir para `permissions.isFetched && !canX` nos 4 ficheiros, já que serão tocados de qualquer forma por esta fase

### Claude's Discretion
- Nomes exatos dos novos componentes/ficheiros (ex.: um `DatePicker`/`DateTimePicker` composto reutilizável em `web/src/components/shared/`, ou inline em cada formulário)
- Detalhes exatos da sincronização entre o `Input type="time"` e o `Calendar` para produzir o valor `datetime-local` final

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/components/ui/calendar.tsx` — `Calendar`/`CalendarDayButton`, envolve `react-day-picker` 9.14.0 (pinned desde a Phase 101), aceita toda a API do `DayPicker` (`mode`, `selected`, `onSelect`, `locale`, `disabled`, etc.)
- `web/src/components/ui/select.tsx` — `Select`/`SelectTrigger`/`SelectContent`/`SelectItem`/etc., wrapper Radix padrão
- `web/src/components/ui/native-select.tsx` — `NativeSelect`/`NativeSelectOption`/`NativeSelectOptGroup`, já usado no Dialog "Novo Prazo" (Processos) e em todo o padrão estabelecido na Phase 105
- `web/src/components/ui/popover.tsx` — existe, mas nunca combinado com `Calendar` até agora (só com lista de notificações e um `RadioGroup`) — esta fase cria o primeiro Date Picker composto do projeto

### Established Patterns
- Fase 105 estabeleceu: `NativeSelect` para campos RHF, `className="w-full"` sempre (o wrapper por omissão é `w-fit`, ao contrário do `Input`)
- Fase 103/105 estabeleceram a correção `permissions.isFetched && !canX` para o bug de race condition de RBAC
- Filtros da Agenda (`agenda/page.tsx`) usam estado React simples (`useState`), não `react-hook-form` — diferente dos formulários criar/editar

### Integration Points
- `web/src/app/(dashboard)/agenda/page.tsx` (filtros + vista de calendário, só os filtros mudam)
- `web/src/app/(dashboard)/agenda/novo/page.tsx`, `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx` (formulários)
- `web/src/schemas/eventos.ts` (`eventoFormSchema` valida `dataInicio`/`dataFim`/`recurrenceEndDate` como strings `new Date(v)`-parseable — não deve precisar de alteração se o valor final continuar a ser uma string de data válida)

</code_context>

<specifics>
## Specific Ideas

Nenhuma específica além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- Migrar o Dialog "Novo Prazo" em `processos/[id]/page.tsx` para `Calendar` — fora de âmbito desta fase (módulo Processos, não Agenda); candidato para uma fase futura se necessário
- Construir um formulário de "editar prazo" (não existe hoje, só criar + toggle concluído) — fora de âmbito, não é um requisito desta milestone

</deferred>
