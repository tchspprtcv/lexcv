# Phase 63: Aprovação e Entrega - Context

**Gathered:** 2026-06-30
**Status:** Ready for planning

<domain>
## Phase Boundary

O parecer pode ser revisto internamente antes de ser entregue, e uma vez entregue fica disponível para consulta pela equipa/cliente. Cobre apenas backend — sem UI frontend (mesma decisão da Fase 62). Não inclui auditoria automática (Fase 64).

</domain>

<decisions>
## Implementation Decisions

### Aprovação Interna
- Apenas ADMIN pode aprovar (não existe role SUPERVISOR no LexCV; ADVOGADO já é o autor da versão, não faz sentido auto-aprovar) — usa o scope `pareceres:manage` reservado na Fase 61 exatamente para isto
- Modelagem: campo `aprovado` (Boolean, default `false`) + `aprovadoPorId` (UUID, nullable) + `aprovadoEm` (LocalDateTime, nullable) na entidade `ParecerVersao` — é um campo de estado separado do conteúdo imutável da versão, não gera nova versão
- Endpoint: `PUT /api/v1/pareceres/solicitacoes/{id}/versoes/{versaoId}/aprovar`, muda o status da solicitação para `EM_REVISAO` se ainda estiver `PENDENTE`/`EM_ELABORACAO`
- Aprovação é opcional: o endpoint de entrega (PARC-08) NÃO exige aprovação prévia, consistente com a decisão da Fase 61 ("pode haver fluxo adicional de revisão... conforme necessidade e política do escritório")

### Entrega Final e Visibilidade
- Endpoint de entrega: `PUT /api/v1/pareceres/solicitacoes/{id}/entregar?versaoFinalId={versaoId}` — marca a versão indicada como versão final e muda o status da solicitação para `CONCLUIDO`
- Quem pode entregar: advogado responsável ou ADMIN, scope `pareceres:manage`
- Campo de versão final: `versaoFinalId` (UUID, nullable) na própria `ParecerSolicitacao`, apontando para a `ParecerVersao` escolhida
- Visibilidade pós-entrega: "disponível para consulta pela equipa/cliente" = qualquer utilizador com `pareceres:view` pode consultar/descarregar; não existe portal de cliente externo no LexCV — "cliente" aqui significa a equipa interna que gere aquele cliente. Sem novo endpoint público nesta fase (reutiliza os endpoints GET já existentes das Fases 61/62)
- Entrega é irreversível: uma vez `CONCLUIDO`, não há endpoint para reverter o status, consistente com a Fase 61 (reatribuição de advogado já bloqueada em `CONCLUIDO`)

### Claude's Discretion
Nenhuma resposta "You decide" foi necessária — todas as 8 questões (2 áreas × 4) foram aceites com as respostas recomendadas.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ParecerSolicitacao`, `ParecerVersao`, `ParecerVersaoRepository`, `ParecerController` (Fases 61-62) — base para os novos endpoints
- Scope `pareceres:manage` já existe no `Permission`/`DatabaseSeeder` (seedado só para ADMIN desde a Fase 61) — não precisa de nova migração de RBAC
- Padrão de tenant-scoping e validação de `advogadoId`/ownership já estabelecido em `ParecerController.java`

### Established Patterns
- Endpoints de transição de estado: `PUT /{id}/atribuir` (Fase 61) é o modelo direto para `/aprovar` e `/entregar`
- Campos de auditoria leve (quem fez o quê e quando) já existem como padrão: `criadoPorId`/`createdAt` em `ParecerVersao` — `aprovadoPorId`/`aprovadoEm` segue o mesmo padrão
- Bloqueio de transição em `CONCLUIDO`: já implementado para reatribuição de advogado na Fase 61, replicar a mesma guarda para qualquer escrita pós-entrega

### Integration Points
- `ParecerVersaoRepository` — precisa de um `findById` simples (provavelmente já herdado de `JpaRepository`) para localizar a versão a aprovar/entregar
- `ParecerSolicitacaoRepository` — `save()` para persistir `versaoFinalId` e novo `status`
- Nenhuma alteração a `web/src/lib/permissions.ts` necessária (scope `pareceres:manage` já mirrorado desde a Fase 61)

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência específica adicional — segue convenções já estabelecidas nas Fases 61-62.

</specifics>

<deferred>
## Deferred Ideas

- Portal de cliente externo para consulta de pareceres entregues — fora de escopo, não existe ainda no LexCV
- Notificação ao cliente quando o parecer é entregue — já listado em REQUIREMENTS.md como Future Requirement (fora de escopo deste milestone)
- Reversão de status CONCLUIDO — decisão deliberada de irreversibilidade

</deferred>
