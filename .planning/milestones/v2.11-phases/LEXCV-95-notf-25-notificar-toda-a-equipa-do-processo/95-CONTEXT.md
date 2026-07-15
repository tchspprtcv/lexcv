# Phase 95: NOTF-25 — Notificar Toda a Equipa do Processo - Context

**Gathered:** 2026-07-14
**Status:** Ready for planning

<domain>
## Phase Boundary

Eventos de processo (entrada de fase, novo documento, atribuição) deixam de notificar apenas o responsável único, passando a alcançar toda a equipa de advogados/administrativos ligada ao cliente do processo.

</domain>

<decisions>
## Implementation Decisions

### Âmbito de "equipa"
- Equipa do processo é derivada transitivamente da equipa do CLIENTE via `Processo.clienteId` → `ClienteAdvogado`/`ClienteAdministrativo` (tabelas já existentes) — sem nova tabela `ProcessoEquipa`
- Cobertura automática e retroativa: processos já existentes herdam a equipa do seu cliente sem migração de dados

### Job diário — fora de âmbito
- `AlertasDiariosJob` (categorias `PRAZO_PROXIMO`, `PRAZO_VENCIDO`, `EVENTO_PROXIMO`, `EVENTO_VENCIDO`, `HONORARIO_ATRASADO`) mantém-se inalterado — continua a notificar apenas `responsavelId`
- Esta fase cobre apenas os 4 gatilhos de evento (`FASE_ENTRADA`, `DOCUMENTO_NOVO`, `PROCESSO_ATRIBUIDO`, e explicitamente NÃO `PARECER_ATRIBUIDO`, ver abaixo)
- Alargar o job diário à equipa fica registado como candidato de âmbito futuro, não desta milestone

### PARECER_ATRIBUIDO — mantém-se individual
- `notificarParecerAtribuido` mantém o comportamento atual (só o advogado atribuído + fan-out ADMIN) — NÃO alarga à equipa do cliente
- Razão: "atribuição de parecer" é semanticamente uma decisão de um advogado específico assumir o trabalho; alargar à equipa diluiria esse sinal

### Gatilhos afetados por esta fase
- `notificarFaseEntrada` — passa a notificar toda a equipa do cliente do processo (hoje só `responsavelId`)
- `notificarDocumentoNovo` (ramo processo) — passa a notificar toda a equipa do cliente do processo em vez de só `responsavelId` (nota: o ramo cliente já notifica a equipa completa desde antes desta fase — esta é a inconsistência que a pesquisa identificou entre os dois ramos)
- `notificarProcessoAtribuido` — o novo responsável recebe cópia em 2ª pessoa ("foi-lhe atribuído"), o resto da equipa recebe cópia em 3ª pessoa informativa
- `notificarParecerAtribuido` — SEM alteração (ver acima)

### Claude's Discretion
- Nome exato do método/helper que resolve a equipa (ex.: `resolverEquipaProcesso`) e onde vive dentro de `NotificacaoService`
- Detalhes de implementação da consulta `Processo.clienteId` → `ClienteAdvogadoRepository`/`ClienteAdministrativoRepository`

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ClienteAdvogado`/`ClienteAdministrativo` (tabelas de junção tenant-scoped já existentes) — mesmo padrão já usado para o ramo cliente de `notificarDocumentoNovo`
- `criarComFanOutAdmin` (Phase 94) — helper já existente que funde destinatários primários + fan-out ADMIN num único `LinkedHashSet` deduplicado antes do loop de criação — a expansão de equipa desta fase deve alimentar este mesmo helper com o conjunto alargado de destinatários primários, não criar um caminho paralelo

### Established Patterns
- O ramo cliente de `notificarDocumentoNovo` já resolve a equipa completa do cliente (`ResourceController.java`, uploadDocumento) — replicar exatamente este padrão para o ramo processo, em vez de inventar um novo

### Integration Points
- `NotificacaoService.notificarFaseEntrada`, `notificarDocumentoNovo` (ramo processo), `notificarProcessoAtribuido` — trocar `List.of(responsavelId)` (ou equivalente) pelo conjunto alargado de equipa
- `NotificacaoService.criarComFanOutAdmin` — já deduplicada e já corrigida (Phase 94) para o bug de colisão ADMIN; esta fase é o motivo pelo qual a Phase 94 teve de vir antes (mais destinatários por evento = maior probabilidade de colisão)
- `Processo.clienteId` → `ClienteAdvogadoRepository.findByTenantIdAndClienteId`/`ClienteAdministradorRepository` equivalente

</code_context>

<specifics>
## Specific Ideas

Nenhuma além das decisões acima.

</specifics>

<deferred>
## Deferred Ideas

- Alargar o fan-out de equipa às categorias do job diário (`AlertasDiariosJob`) — fora de âmbito desta milestone, já registado em REQUIREMENTS.md Out of Scope
- Alargar `PARECER_ATRIBUIDO` à equipa do cliente — decisão explícita de manter individual, não revisitar sem novo pedido

</deferred>
