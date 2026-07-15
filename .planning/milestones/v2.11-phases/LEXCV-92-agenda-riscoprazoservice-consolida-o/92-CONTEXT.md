# Phase 92: Agenda ↔ RiscoPrazoService — Consolidação - Context

**Gathered:** 2026-07-13
**Status:** Ready for planning

<domain>
## Phase Boundary

A página de Agenda deixa de calcular o seu próprio veredito de "prazo crítico" e passa a confiar inteiramente no `RiscoPrazoService` já usado pelo resto do sistema, tanto para Prazos como para Eventos.

</domain>

<decisions>
## Implementation Decisions

### Risco de Eventos
- `GET /eventos` (endpoint principal usado pela Agenda) passa a devolver o campo `risco` por evento, calculado no backend via `riscoPrazoService.computeRiscoEvento(...)` — o mesmo método já usado em `/eventos/upcoming`
- `web/src/app/(dashboard)/agenda/page.tsx` passa a usar o `risco` devolvido pelo backend, tanto para Prazos (já devolvido hoje, apenas descartado no mapeamento `allUnifiedEvents`) como para Eventos (novo campo)
- Nenhum cálculo de "prazo crítico"/risco permanece no frontend depois desta fase — fecha a "5ª implementação divergente" mencionada no âmbito da milestone

### Endpoint legado
- `GET /eventos/upcoming` é removido nesta fase — ficou sem consumidores desde que a Phase 89 (v2.10) substituiu o sino de eventos pelo sino de `Notificacao` genérica; mantê-lo seria dívida técnica adicional contrariando o propósito desta fase
- Confirmar (grep) que nenhum código frontend ainda chama `/eventos/upcoming` antes de remover o endpoint backend

### Claude's Discretion
- Forma exata de expor `risco` no payload de `GET /eventos` (campo direto vs. objeto aninhado) — seguir o padrão já usado por Prazos na mesma resposta
- Se a resposta de `GET /eventos` precisa de ajuste de tipo/schema no frontend (`web/src/types/`) — replicar o padrão já usado para o campo `risco` de Prazos

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RiscoPrazoService.computeRiscoEvento(dataInicio, prioridade)` já existe e já é usado em `ResourceController.getUpcomingEventos` (linha ~2503) — reutilizar diretamente na listagem principal
- Prazos já devolvem `risco` corretamente no backend (`ResourceController.java` linhas ~1640, 1663, 1720, 1760) — o problema é apenas que `agenda/page.tsx` descarta esse campo ao construir `allUnifiedEvents`

### Established Patterns
- `RiscoPrazoService` (Phase 85, v2.10) é a única fonte partilhada de lógica de "prazo crítico" — dashboard, backend de eventos/prazos e o job diário já a consomem

### Integration Points
- `ResourceController.listEventos` (`@GetMapping("/eventos")`, linha ~2356) — adicionar `risco` ao payload de cada evento devolvido
- `ResourceController.getUpcomingEventos` (`@GetMapping("/eventos/upcoming")`, linha ~2482) — remover nesta fase (endpoint órfão pós-Phase 89)
- `web/src/app/(dashboard)/agenda/page.tsx` — `allUnifiedEvents` deixa de recalcular risco/prioridade-como-proxy e passa a usar o campo `risco` do backend para Prazos E Eventos

</code_context>

<specifics>
## Specific Ideas

Nenhuma além das decisões acima — âmbito técnico bem definido pela pesquisa da milestone (ARCHITECTURE.md/PITFALLS.md) e pelas decisões desta discussão.

</specifics>

<deferred>
## Deferred Ideas

- Auditoria mais ampla de consistência entre Dashboard/Agenda/notificações além do que AGD-34/35 cobrem — fora de âmbito, candidato para a Phase 97 (auditoria de milestone) se aplicável

</deferred>
