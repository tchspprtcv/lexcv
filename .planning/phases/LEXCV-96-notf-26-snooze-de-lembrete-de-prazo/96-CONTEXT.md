# Phase 96: NOTF-26 — Snooze de Lembrete de Prazo - Context

**Gathered:** 2026-07-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Utilizador pode adiar um lembrete de prazo por um período pré-definido, e a notificação reaparece automaticamente como não lida quando esse período termina, sem ser recriada prematuramente pelo job diário.

</domain>

<decisions>
## Implementation Decisions

### Durações de snooze
- Presets fixos: 1/3/7 dias, via `Popover` + `RadioGroup` (componentes já instalados) — sem date picker personalizado

### PRAZO_VENCIDO não pode ser adiado
- `PRAZO_VENCIDO` (a única categoria não-silenciável do NOTF-24) também não pode ser adiada — mesmo raciocínio de segurança jurídica: um prazo já vencido nunca pode desaparecer da vista do utilizador
- Aplicar a mesma verificação já usada em `CategoriaNotificacao.isSilenciavel()` (Phase 93) para bloquear o snooze desta categoria — reutilizar o flag existente em vez de criar um segundo mecanismo de exceção paralelo, se a semântica for a mesma (adiável = silenciável)

### Arquitetura (research ARCHITECTURE.md/PITFALLS.md — locked)
- `Notificacao.snoozedUntil` (nova coluna nullable) — toggle de visibilidade na mesma linha, ortogonal a `lida` e ao dedup por `categoria` de `AlertasDiariosJob` — nenhuma mudança na constraint `uk_notificacao_dedup` nem na lógica de idempotência do job
- Apenas as queries de contador/badge/lista-não-lida (sino) filtram por `snoozedUntil`; a página `/notificacoes` (histórico completo) permanece sem filtro, para que itens adiados continuem visíveis/pesquisáveis
- Correr o job diário durante o período de snooze não deve recriar nem duplicar a notificação adiada — a idempotência por categoria do job já cobre isto por construção (a linha já existe), só é preciso não estragar isso com a nova coluna

### Claude's Discretion
- Nome exato do endpoint (`PATCH /notificacoes/{id}/snooze` vs. outro verbo/rota) — seguir o padrão REST já usado no resto do `NotificacaoController`
- Detalhes de implementação do componente de UI (onde exatamente o `Popover`+`RadioGroup` de snooze aparece na lista/dropdown existente)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `CategoriaNotificacao.isSilenciavel()` (Phase 93) — já existe o flag por categoria que marca `PRAZO_VENCIDO` como exceção; reutilizar para bloquear snooze também, se a semântica coincidir
- `Popover`+`RadioGroup` (já instalados, `web/src/components/ui/`) — mesmos componentes recomendados pela pesquisa para os presets de snooze
- `useNotificacoes(filters, {poll})` (Phase 89) — hook único que já serve tanto o dropdown do sino como a página `/notificacoes`; a nova coluna `snoozedUntil` só deve afetar as queries de contador/badge/lista-não-lida, não o histórico completo

### Established Patterns
- `AlertasDiariosJob`'s idempotência edge-triggered por `(tenant, destinatario, entidade, categoria)` já garante que a notificação adiada não é recriada — não precisa de lógica nova no job

### Integration Points
- `Notificacao` (entidade) — nova coluna `snoozedUntil` (nullable), migração manual (padrão já estabelecido: `backend/migrations/`)
- `NotificacaoService` — novo método `snooze(...)` mirando o shape de `marcarLida`
- `NotificacaoController` — novo endpoint `PATCH /notificacoes/{id}/snooze`
- Queries de contador/badge/lista-não-lida em `NotificacaoRepository` — adicionar predicado de tempo sobre `snoozedUntil`

</code_context>

<specifics>
## Specific Ideas

Nenhuma além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

None — âmbito fechado pelas decisões acima.

</deferred>
