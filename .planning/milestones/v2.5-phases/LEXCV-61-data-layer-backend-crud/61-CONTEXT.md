# Phase 61: Data Layer + Backend CRUD - Context

**Gathered:** 2026-06-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Existe uma base de dados e API funcionais para criar, atribuir e listar solicitações de parecer, com RBAC dedicado. Cobre apenas o backend (entidades, repositórios, controller, permissões) — sem UI frontend e sem versionamento de conteúdo (Fase 62) ou integração de auditoria (Fase 64).

</domain>

<decisions>
## Implementation Decisions

### Modelagem de Dados
- Campo de urgência: `prioridade` (String, default `"MEDIA"`) — reutiliza o mesmo padrão já usado em `Evento`/`Prazo`
- Campo de status: `status` (String) com valores `PENDENTE`, `EM_ELABORACAO`, `EM_REVISAO`, `CONCLUIDO`, default `PENDENTE`
- Prazo desejado: campo `prazo` (`LocalDate`, nullable)
- Identificador: UUID interno gerado automaticamente — sem numeração sequencial visível tipo `PAR-0001` (parecer não é entidade legal numerada como Cliente)

### Endpoints e Contrato API
- Novo `ParecerController.java` dedicado — não adicionar a `ResourceController.java` (já ~2300 linhas)
- Base path: `/api/v1/pareceres/solicitacoes`
- Resposta: retornar a entidade diretamente (sem DTO layer), mesmo padrão usado por Cliente/Processo
- Listagem sem paginação nesta fase — lista simples filtrada por tenant, mesmo padrão de Cliente/Processo

### Regras de Atribuição e Validação
- `advogadoId` (UUID, FK para `User`) é obrigatório no momento da atribuição, mas opcional na criação (pode criar sem atribuir)
- Backend valida que o `User` atribuído tem role `ADVOGADO` antes de aceitar a atribuição
- Reatribuição permitida em qualquer status exceto `CONCLUIDO` (parecer entregue não muda mais de responsável)
- `clienteId` é obrigatório na criação (toda solicitação pertence a um cliente)

### RBAC e Transições de Status
- Scopes: `pareceres:view` (listar/ver), `pareceres:create` (criar), `pareceres:edit` (atribuir/editar), `pareceres:manage` (reservado para aprovação/entrega na Fase 63)
- Perfis: ADMIN recebe todos os scopes; ADVOGADO recebe view+create+edit; TECNICO/ASSISTENTE recebem apenas view (seguindo padrão de financeiro/processos)
- Status muda automaticamente para `EM_ELABORACAO` quando um advogado é atribuído; `PENDENTE` é o default sem atribuição
- Esta fase NÃO integra com `AuditLog` ainda — fica reservado para a Fase 64 (PARA-01); foco aqui é só CRUD

### Claude's Discretion
Nenhuma resposta "You decide" foi necessária — todas as 16 questões (4 áreas × 4) foram aceites com as respostas recomendadas.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Cliente` (`backend/src/main/java/com/lexcv/models/Cliente.java`) — FK target via `clienteId`
- `User` + `Role` (`User.java`, `Role.java`) — advogado responsável é um `User` com role `ADVOGADO`, sem entidade separada
- `Processo` (`Processo.java`) — FK opcional via `processoId`
- Padrão de permissões `scope:action` em `Permission.java` + seeding em `DatabaseSeeder.java` (linhas ~295-341)

### Established Patterns
- Entidades JPA: Lombok `@Builder`/`@Getter`/`@Setter`, `@Id @GeneratedValue(strategy = GenerationType.UUID)`, `tenantId` obrigatório, `@PrePersist` para timestamps
- Controllers: `@PreAuthorize("hasAuthority('scope:action')")` inline por endpoint, sem service layer (queries diretas via repository + filtragem em stream)
- Status/prioridade como `String` livre (não enum Java) — ver `Evento.prioridade`, `Prazo.prioridade = "MEDIA"`, `Processo.estado`

### Integration Points
- `Permission`/`DatabaseSeeder.java` — adicionar `pareceres:view/create/edit/manage` à lista de scopes e às `upsertRolePermissions` de ADMIN/ADVOGADO/TECNICO/ASSISTENTE
- Novo `ParecerSolicitacaoRepository` seguindo padrão de `ClienteRepository`/`ProcessoRepository`
- `web/src/lib/permissions.ts` deve espelhar os novos scopes (fora do escopo desta fase backend, mas necessário antes da Fase 62 ter UI)

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência específica adicional além das decisões acima — segue convenções já estabelecidas no codebase (Cliente/Processo como modelo de referência).

</specifics>

<deferred>
## Deferred Ideas

- Numeração sequencial visível para pareceres (tipo `PAR-0001`) — pode ser reconsiderado em milestone futura se houver necessidade de referência legal formal
- Integração com AuditLog — Fase 64
- DTO layer / paginação — não necessário nesta fase, alinhado com padrão existente de Cliente/Processo

</deferred>
