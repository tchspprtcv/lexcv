# Phase 64: Auditoria e Pesquisa Avançada - Context

**Gathered:** 2026-06-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Todas as ações relevantes sobre pareceres ficam auditadas automaticamente e qualquer parecer pode ser encontrado por texto livre combinado com filtros. Última fase da milestone v2.5 — backend apenas, sem UI frontend (mesma decisão das Fases 62-63).

</domain>

<decisions>
## Implementation Decisions

### Mecanismo de Auditoria
- Registo direto: chamada a `auditLogRepository.save(...)` em cada endpoint de transição do `ParecerController` (criar solicitação, atribuir advogado, criar versão, aprovar, entregar) — sem aspeto/interceptor, mesmo padrão manual já usado para `transicao_estado`/`documento_download` no resto do código
- Valores de `acao`: `parecer_criar`, `parecer_atribuir`, `parecer_versao_criar`, `parecer_aprovar`, `parecer_entregar` (strings livres, consistente com o padrão existente)
- `entidadeTipo`/`entidadeId`: `parecer_solicitacao` + ID da solicitação para ações de solicitação (criar, atribuir, entregar); `parecer_versao` + ID da versão para criação/aprovação de versão
- `autorId`: sempre o utilizador autenticado (`UserPrincipal.getUserId()`), nunca nulo nestas ações

### Pesquisa Textual
- Mecanismo: `ILIKE '%termo%'` nativo do Postgres sobre `ParecerVersao.conteudo` (JOIN com `ParecerSolicitacao`) — evita motor de busca externo, conforme já decidido em REQUIREMENTS.md
- Escopo: procura apenas no `conteudo` da versão mais recente de cada solicitação (não em todas as versões históricas) — evita duplicados nos resultados
- Endpoint: `GET /api/v1/pareceres/pesquisa?texto=&clienteId=&advogadoId=&status=&dataInicio=&dataFim=`, tenant-scoped, retorna lista de `ParecerSolicitacao` com metadados
- Sem índice `tsvector`/full-text dedicado nesta fase (volume ainda baixo); índice trigram fica como melhoria futura

### Query e Permissões
- Implementação: `@Query` JPQL customizada em `ParecerSolicitacaoRepository` com parâmetros opcionais (padrão `:param IS NULL OR campo = :param`), mais eficiente que filtragem em stream para uma query com JOIN + LIKE
- Scope de permissão: `pareceres:view` (mesmo scope da listagem simples da Fase 61 — pesquisa é uma variante de leitura)
- `dataInicio`/`dataFim` filtram por `createdAt` da solicitação (data de criação do pedido, não data de entrega)
- Resultado vazio: retorna lista vazia (200 OK), não 404

### Claude's Discretion
Nenhuma resposta "You decide" foi necessária — todas as 12 questões (3 áreas × 4) foram aceites com as respostas recomendadas.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AuditLog`/`AuditLogRepository` (`backend/src/main/java/com/lexcv/models/AuditLog.java`, `repositories/AuditLogRepository.java`) — entidade já existente, reutilizada sem alterações de schema
- `ParecerSolicitacao`, `ParecerVersao`, `ParecerSolicitacaoRepository`, `ParecerVersaoRepository`, `ParecerController` (Fases 61-63) — todos os endpoints de transição já existem, só precisam da chamada de auditoria adicionada
- Exemplos de uso existente de `AuditLog` no código (ex.: `transicao_estado`, `documento_download`, `conflict_check_decisao`) — padrão de referência direto para os novos valores de `acao`

### Established Patterns
- `AuditLog` é `@PrePersist` timestamp automático, `processoId` sempre nulo para pareceres (nullable, não relacionado a Processo diretamente)
- Filtros opcionais em queries JPQL: usar padrão `(:param IS NULL OR campo = :param)` para parâmetros opcionais combináveis

### Integration Points
- Todos os 5 endpoints de transição do `ParecerController` precisam de uma linha adicional de `auditLogRepository.save(...)` após a operação principal
- Novo método em `ParecerSolicitacaoRepository` para a query de pesquisa combinada (JPQL com JOIN a `ParecerVersao`)
- Nenhuma alteração a `web/src/lib/permissions.ts` necessária (scope `pareceres:view` já existe desde a Fase 61)

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência específica adicional — segue convenções já estabelecidas nas Fases 61-63 e no `AuditLog` existente no resto do codebase.

</specifics>

<deferred>
## Deferred Ideas

- Índice full-text dedicado (tsvector/trigram) para pesquisa — melhoria futura se o volume justificar, já listado em REQUIREMENTS.md
- Pesquisa em todas as versões históricas (não só a mais recente) — decisão deliberada de escopo, evita duplicados

</deferred>
