# Phase 124: Catálogo de Permissões em Base de Dados - Context

**Gathered:** 2026-09-20
**Status:** Ready for planning
**Mode:** Infraestrutura — áreas cinzentas saltadas (refactor de onde os dados vivem, sem comportamento novo visível)

<domain>
## Phase Boundary

O catálogo de permissões (nome técnico, rótulo, descrição, categoria) passa a ser servido a partir de `t_permission` em vez da lista Java hardcoded em `AdminController.getRbac`, e sobrevive a qualquer reinício do backend sem apagar atribuições já persistidas.

Dentro do âmbito: as colunas descritivas em `Permission`, a semeadura idempotente do catálogo, a origem dos dados que `GET /admin/rbac` devolve no campo `systemPermissions`, e a exclusão das permissões reservadas à plataforma.

Fora do âmbito: papéis por escritório (Phase 127), moldes (Phase 125), alterar quem pode escrever a matriz (Phase 127, CATL-04). Esta fase não muda nenhuma autoridade nem nenhum gate — muda apenas de onde vem a descrição do catálogo.
</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Todas as escolhas de implementação ficam ao critério do executor — fase de pura infraestrutura. Guiar-se pelo objectivo da fase, pelos critérios de sucesso do ROADMAP e pelas convenções já estabelecidas no codebase (Lombok `@Builder`/`@Data` nas entidades, repositórios Spring Data, testes Mockito).

### Invariantes que não podem quebrar
- A semeadura tem de ser **upsert**, nunca replace: `t_role_permission` e `t_user_permission` referenciam `t_permission.id`, logo apagar e reinserir linhas do catálogo destruiria atribuições reais de utilizadores em produção. O padrão `findByNome(...).orElseGet(save(...))` já existente em `DatabaseSeeder.seedRbac` é o ponto de partida correcto.
- As permissões que definem o `PLATAFORMA_ADMIN` não podem aparecer em nenhum catálogo servido a um escritório. Notar que hoje esse papel tem colecção de permissões **deliberadamente vazia** (Phase 119) — a reserva é por papel, não por permissão, e o desenho novo tem de decidir como marca "reservada" sem inverter essa decisão.
- O contrato de resposta de `GET /admin/rbac` (campos `rolePermissions` e `systemPermissions`, com `PermissionDefDto` de 4 campos) é consumido por `useAdminRbac` e pelo `RbacTab`. Mudar a origem dos dados não deve exigir alteração do frontend nesta fase.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DatabaseSeeder.seedRbac()` (backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:311) — já faz find-or-create idempotente de cada `Permission` por nome; é o sítio natural para a semeadura do catálogo enriquecido
- `DatabaseSeeder.upsertRolePermissions()` (linha 377) — `addAll` que nunca remove; o comentário da Phase 119 explica porquê (uma injecção de permissões persistiria para sempre sem reparação no arranque seguinte)
- `PermissionRepository.findByNome` — lookup já usado em `AdminController.updateRbac`
- `RbacResponse.PermissionDefDto` — o DTO de 4 campos (nome, rótulo, descrição, categoria) que a lista hardcoded já produz; a entidade tem de passar a poder preenchê-lo

### Established Patterns
- Entidades JPA com Lombok `@Builder`/`@Getter`/`@Setter`, `@EqualsAndHashCode(of = "nome")` em `Permission` e `Role`
- Schema por `ddl-auto: update` — colunas novas em `t_permission` são criadas pelo Hibernate no arranque; não há Flyway
- Testes backend em Mockito (~88 testes), mais integração com Testcontainers PostgreSQL

### Integration Points
- `AdminController.getRbac` (~linha 357) — a lista Java de `Arrays.asList(new PermissionDefDto(...))` de 17 entradas que esta fase substitui
- `web/src/hooks/use-admin.ts#useAdminRbac` e o `RbacTab` em `web/src/app/(dashboard)/settings/page.tsx` — consumidores do contrato; não devem precisar de alteração
</code_context>

<specifics>
## Specific Ideas

**Divergência real encontrada no scout, que esta fase tem de resolver em vez de herdar:** `DatabaseSeeder.seedRbac` semeia **20** chaves de permissão, mas a lista hardcoded de `getRbac` descreve apenas **17**. As três ausentes — `processos:create`, `processos:manage`, `financeiro:manage` — existem em `t_permission` e estão atribuídas a papéis reais (`ADVOGADO` tem `processos:create` e `processos:manage`; `ADMIN` tem as três), mas nunca apareceram na matriz RBAC do frontend porque a lista que a alimenta não as menciona.

Ao passar o catálogo para a base de dados, estas três deixam de ser invisíveis por acidente. A fase tem de tomar uma decisão explícita sobre cada uma — dar-lhe rótulo, descrição e categoria próprios, ou marcá-la como não oferecível — e não deixar que apareçam sem rótulo no ecrã do escritório.

Consequência para a Phase 127: se estas três passarem a ser oferecíveis, um administrador de escritório poderá compor papéis com granularidade que hoje não existe no UI. Isso é ganho, não risco, mas deve ser deliberado.
</specifics>

<deferred>
## Deferred Ideas

- Tecto de permissões por plano de subscrição (`TenantPlano`) — fora do marco por decisão explícita; ver REQUIREMENTS.md, requisitos futuros TECT-01/TECT-02
- Permitir que um escritório crie permissões novas — fora de âmbito permanente: o catálogo é da plataforma
</deferred>
