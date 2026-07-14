# Phase 94: NOTF-27 — Corrigir Colisão de Dedup ADMIN - Context

**Gathered:** 2026-07-14
**Status:** Ready for planning
**Mode:** Auto-generated (bug-fix phase — smart discuss skipped, fully specified by milestone research)

<domain>
## Phase Boundary

Notificar um destinatário que é simultaneamente membro de equipa/responsável e ADMIN nunca falha por colisão da constraint `uk_notificacao_dedup` — um bug pré-existente desde a Phase 88 (v2.10), agravado pelo alargamento de destinatários que a Phase 95 (NOTF-25) está para introduzir.

</domain>

<decisions>
## Implementation Decisions

### Causa raiz e correção (research ARCHITECTURE.md/PITFALLS.md — locked)
- `notificarDocumentoNovo`/`notificarParecerAtribuido` (e potencialmente outros métodos `notificar*`) chamam `criar()` uma vez para o destinatário primário e depois `notificarAdmins()` para o fan-out de ADMIN, sem verificar sobreposição entre os dois conjuntos
- Quando o destinatário primário é ele próprio ADMIN, ambas as chamadas tentam criar uma linha `Notificacao` para o mesmo `(tenant, destinatario, entidade, categoria)`, colidindo com o constraint único `uk_notificacao_dedup` (adicionado na Phase 88) e lançando `DataIntegrityViolationException` não apanhada
- Correção: fundir os conjuntos de destinatários (primário + ADMINs) num único `LinkedHashSet` antes do loop de criação, eliminando duplicados por construção, em vez de duas chamadas separadas e não coordenadas
- Isto é um bug pré-existente e já ativo hoje (não introduzido por esta milestone) — a Phase 95 (NOTF-25) vai alargar o número de destinatários por evento, tornando a colisão passar de "rara" a "quase certa", daí esta fase ter de vir antes

### Claude's Discretion
- Detalhes exatos de implementação (onde construir o `LinkedHashSet`, se um helper privado partilhado faz sentido entre os métodos afetados) ficam ao critério do planeador/executor, seguindo o padrão já estabelecido no código

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `NotificacaoService.criar(...)` — choke point único de escrita (já modificado nas Phases 93 para o guard de silenciamento)
- `notificarAdmins(...)` — método existente de fan-out para ADMINs

### Established Patterns
- Todos os métodos `notificar*` seguem o mesmo padrão: chamar `criar()` para o(s) destinatário(s) direto(s), depois `notificarAdmins()` para o fan-out

### Integration Points
- `NotificacaoService.notificarDocumentoNovo`, `notificarParecerAtribuido` (e outros métodos `notificar*` relevantes) — fundir destinatário(s) primário(s) + ADMINs antes do loop de criação
- Deve ser corrigido antes da Phase 95 (NOTF-25), que alarga o pool de destinatários por evento

</code_context>

<specifics>
## Specific Ideas

Nenhuma além das decisões acima — âmbito técnico bem definido pela pesquisa da milestone (PITFALLS.md Pitfall 2, o achado de maior severidade do relatório).

</specifics>

<deferred>
## Deferred Ideas

None — bug-fix phase, escopo fechado.

</deferred>
