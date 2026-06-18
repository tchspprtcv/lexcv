# Phase 44: Status + KPIs - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase adiciona lógica de status por honorário (Pendente / Parcialmente Pago / Pago) com badge UI, e cards de KPI financeiro no topo da página financeiro (total faturado, total recebido, em dívida, receita do mês corrente). O cálculo é feito no frontend a partir dos dados carregados — sem endpoint HTTP adicional dedicado a KPIs.

**Fora do âmbito desta fase:** Filtros na lista (Phase 45), UI de edit/delete (Phase 45), exportação CSV (Phase 46).

</domain>

<decisions>
## Implementation Decisions

### Cálculo de Status do Honorário
- O status depende do total pago vs `valorTotal` — mas os pagamentos não são incluídos na lista `GET /honorarios`
- **Decisão:** Adicionar `totalPago: number` ao DTO de resposta do `GET /honorarios` no backend (campo computado via query SQL agregada ou carregado eager)
- Evita N+1 requests no frontend — uma única chamada já traz tudo o que é preciso para status e KPIs
- Este campo é read-only (calculado) — não faz parte de `HonorarioCreateRequest` nem `HonorarioUpdateRequest`
- Regras de status: `Pendente` = totalPago == 0; `Parcialmente Pago` = 0 < totalPago < valorTotal; `Pago` = totalPago >= valorTotal

### Badge UI
- Implementar como componente `HonorarioBadge` ou variante do `Badge` existente com mapeamento de cor por status
- Cores: `Pendente` → amarelo/amber; `Parcialmente Pago` → azul; `Pago` → verde
- Posição: coluna adicional na tabela de honorários em `page.tsx`

### KPI Cards
- Calcular 4 KPIs a partir dos dados de `useHonorarios()` (que agora inclui `totalPago`):
  - **Total Faturado**: `sum(valorTotal)` de todos os honorários
  - **Total Recebido**: `sum(totalPago)` de todos os honorários
  - **Em Dívida**: Total Faturado − Total Recebido
  - **Receita do Mês**: `sum(totalPago)` de honorários cujo `dataAcordo` está no mês corrente (ou sem `dataAcordo` excluídos)
- Cards posicionados antes da tabela de honorários, numa grid de 4 colunas
- Layout: usar componente `Card` existente com valor e rótulo

### Claude's Discretion
- Estrutura exata do componente badge (inline vs componente separado)
- Query SQL exata para agregar `totalPago` no backend (SUM subquery vs JOIN)
- Layout responsivo dos KPI cards (2 cols mobile / 4 cols desktop)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useHonorarios()` em `use-financeiro.ts` — reutilizar, mas o tipo `Honorario` e o hook precisam de ser atualizados para incluir `totalPago`
- `Badge` component de `@/components/ui/badge` — verificar se existe; se sim, usar variante; se não, usar `span` com classes Tailwind
- `Card`, `CardContent`, `CardHeader`, `CardTitle` — já importados em `page.tsx`, reutilizar para KPI cards
- `formatMoneyCVE()` — já existe em `page.tsx`, reutilizar para KPI values

### Established Patterns
- `@Column` com `@Formula` ou JPQL `SUM` subquery para campos computados no JPA
- Alternativa mais simples: adicionar `totalPago` ao endpoint via query nativa ou JPQL com `LEFT JOIN pagamentos`
- Tabela de honorários já existe em `page.tsx` — adicionar coluna de status à direita

### Integration Points
- `web/src/types/financeiro.ts` — adicionar `totalPago: number` à interface `Honorario`
- `backend/.../controllers/ResourceController.java` — `listHonorarios` endpoint (GET /honorarios) precisa de retornar `totalPago`
- `backend/.../dtos/` — verificar se existe `HonorarioResponse` DTO; se não, criar ou modificar mapeamento inline
- `web/src/app/(dashboard)/financeiro/page.tsx` — adicionar KPI cards e coluna de status

</code_context>

<specifics>
## Specific Ideas

- O backend atualmente retorna `List<Honorario>` (a entidade JPA diretamente) — para adicionar `totalPago` sem alterar a entidade, pode criar um `HonorarioResponse` record/DTO com os campos da entidade + `totalPago`, ou usar uma JPQL projeção
- Opção mais simples: adicionar `@Transient BigDecimal totalPago` à entidade `Honorario` e calcular com `@Formula("(SELECT COALESCE(SUM(p.valor_pago), 0) FROM t_pagamento p WHERE p.honorario_id = id)")` — solução em 1 ficheiro
- Verificar se `t_pagamento` é o nome correto da tabela JPA (pode ser `honorario_pagamentos` ou similar)

</specifics>

<deferred>
## Deferred Ideas

- Gráficos de receita mensal (Chart.js / Recharts) — fora do âmbito v2.0
- Notificações de honorários em atraso — fora do âmbito v2.0
- Status calculado no backend e persistido como campo — desnecessário dado que é derivável

</deferred>
