# Phase 117: Backend — Limite de Utilizadores por Tenant - Context

**Gathered:** 2026-07-28
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — user pre-authorized Claude to decide grey areas ("o claude decide as opções e avança")

<domain>
## Phase Boundary

Backend-only. `Tenant` gets two new persisted fields (`plano`, `limiteUtilizadores`). `POST /api/v1/admin/users` enforces the limit (409 when at capacity, counting only `ativo=true` users). Deactivating a user frees a slot immediately (no new logic needed — enforcement is checked live on each create, not cached). No frontend, no platform-admin role yet (that's Phase 119) — the fields exist and are enforced, but nothing in this phase provides a UI/API to *set* plano/limiteUtilizadores for a tenant after creation (that arrives with the Phase 120 console). Migration must give the existing single production tenant a default that never locks it out.

</domain>

<decisions>
## Implementation Decisions

### Contrato de Dados (Tenant.plano/limiteUtilizadores)
- `plano` é um Java enum `TenantPlano` (`STARTER`, `STANDARD`, `ENTERPRISE`), persistido com `@Enumerated(EnumType.STRING)` — mesma convenção já usada para `DocumentoTipo` (enum simples, sem métodos) em vez de string livre sem validação
- `limiteUtilizadores` é `Integer` **nullable** — `null` significa "sem limite" (plano Enterprise "por acordo", per proposta secção 5.3); um número aplica o limite exato. Não usar sentinel mágico (`-1`/`MAX_VALUE`) — `null` é auto-descritivo e é o idiomático em Java/JPA para "sem valor"
- Colunas snake_case consistentes com o resto da entidade (`tipo_entidade`, `logo_data_url`): `plano`, `limite_utilizadores`
- Migração dá ao tenant único já existente `plano=ENTERPRISE`, `limiteUtilizadores=NULL` — nunca bloquear o único tenant real em produção como efeito colateral desta fase

### Comportamento de Enforcement
- Verificação do limite corre em `AdminController.createUser`, depois das validações de formato já existentes (nome/email/password/roles), imediatamente antes do `userRepository.save()` — não gastar a query de contagem em pedidos já inválidos por outro motivo
- Contagem = `count(ativo=true)` do tenant do chamador (`principal.getTenantId()`), comparado com `limiteUtilizadores` antes de persistir o novo utilizador (o novo utilizador ainda não conta para si próprio)
- `limiteUtilizadores == null` → bypassa a verificação por completo (sem limite)
- Erro: `409 CONFLICT` com o mesmo formato `Map.of("message", "...")` já usado em todo o `AdminController` (não introduzir um novo formato de erro); mensagem: "Limite de utilizadores atingido para o vosso plano." (texto sugerido pela própria proposta, secção 5.2)
- A contagem "utilizadores ativos" é um único método reutilizável (`UserRepository.countByTenantIdAndAtivoTrue` ou equivalente) — Success Criteria 4 da fase exige isto explicitamente, para reuso nas Phases 120/122

### Migração
- Script manual em `backend/migrations/117-add-tenant-plano-limite-utilizadores.sql`, seguindo a convenção já estabelecida (ficheiros `NNN-descricao.sql`, ex. `96-add-notificacao-snoozed-until.sql`) — `ddl-auto=update` cobre dev automaticamente mas prod usa `validate`, exigindo o script

### Claude's Discretion
- Nome exato do método de contagem reutilizável e a sua localização (`UserRepository` vs. um serviço dedicado) — implementação interna, sem impacto de contrato
- Ordem exata dos novos campos no builder/DTO — segue o padrão já existente na classe

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AdminController.createUser` (`backend/src/main/java/com/lexcv/controllers/AdminController.java:66-127`) — já usa `Map.of("message", ...)` para todos os erros de validação; o novo 409 replica este padrão
- `User.ativo` (`Boolean`, default `true`) já existe — a contagem de "ativos" não precisa de nenhuma mudança de schema em `User`
- `DocumentoTipo` (`backend/src/main/java/com/lexcv/models/DocumentoTipo.java`) — precedente direto de enum simples persistido por nome

### Established Patterns
- Migrações manuais em `backend/migrations/NNN-descricao.sql`, nunca Flyway/Liquibase (decisão confirmada em PROJECT.md Key Decisions)
- `AdminController` é `@PreAuthorize("hasRole('ADMIN')")` a nível de classe — este endpoint já está protegido; o novo limite é uma verificação de negócio adicional, não uma mudança de autorização
- Colunas snake_case via `@Column(name = "...")`, campos Java camelCase — `Tenant.java` já segue isto (`tipoEntidade`→`tipo_entidade`)

### Integration Points
- `Tenant` entity (`backend/src/main/java/com/lexcv/models/Tenant.java`) — só ficheiro tocado além do `AdminController` e do novo enum
- Nenhuma mudança em `TenantRepository`, `SetupService` ou qualquer endpoint público nesta fase — isso é Phase 119/121

</code_context>

<specifics>
## Specific Ideas

Nenhuma referência específica adicional além do que já está no ROADMAP.md e na proposta original (`proposta_multitenancy_distribuicao_faturacao.md`, secções 5.1-5.2).

</specifics>

<deferred>
## Deferred Ideas

- Ecrã/endpoint para editar `plano`/`limiteUtilizadores` de um tenant — Phase 120 (PROV-04), fora do âmbito desta fase
- Entidade `Subscription` com histórico de mudanças de plano — v2 Requirements, fora do roadmap atual

</deferred>
