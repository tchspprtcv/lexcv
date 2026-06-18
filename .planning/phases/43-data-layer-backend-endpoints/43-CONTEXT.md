# Phase 43: Data Layer + Backend Endpoints - Context

**Gathered:** 2026-06-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Esta fase entrega o contrato de dados correto entre frontend e backend: migra os tipos TypeScript e hooks do módulo financeiro de snake_case para camelCase (alinhando com a serialização padrão Jackson/Spring Boot), e adiciona os endpoints CRUD em falta no backend (`GET /honorarios/{id}`, `PUT /honorarios/{id}`, `DELETE /honorarios/{id}`, `DELETE /pagamentos/{id}`).

**Fora do âmbito desta fase:** UI de edição/eliminação (Phase 45), status badges (Phase 44), filtros (Phase 45).

</domain>

<decisions>
## Implementation Decisions

### camelCase Migration Scope
- Verificar primeiro se o backend já retorna camelCase ou snake_case (grep por Jackson NamingStrategy config no backend)
- Migração atómica: renomear todos os campos de uma vez (não usar adaptadores intermédios) — mesmo padrão da migração agenda v1.9
- Atualizar também os schemas Zod em `web/src/schemas/financeiro.ts` para alinhar com os novos nomes de campos
- Remover `tenantId` do tipo `Honorario` frontend (nunca necessário na UI); manter `createdAt` em camelCase

### Backend New Endpoints
- Adicionar todos os novos endpoints em `ResourceController.java` (padrão atual do projeto)
- `DELETE /honorarios/{id}`: rejeitar com 409 Conflict se o honorário tiver pagamentos — mensagem de erro clara
- `PUT /honorarios/{id}`: campos editáveis são `valorTotal`, `descricao`, `dataAcordo` — `processoId` não é editável após criação
- `DELETE /pagamentos/{id}`: subtrair `valorPago` do saldo da conta corrente (operação simétrica ao POST /pagamentos)

### Frontend Integration
- `useHonorario(id)` já existe — apenas corrigir os tipos para camelCase (sem novo hook)
- Adicionar `useUpdateHonorario()` e `useDeleteHonorario()` em `web/src/hooks/use-financeiro.ts`
- Adicionar `useDeletePagamento()` em `web/src/hooks/use-financeiro.ts`; deve invalidar cache de pagamentos e conta-corrente
- Nesta fase apenas adicionar hooks e endpoints — a UI de edição/eliminação é entregue na Phase 45

### Claude's Discretion
- Estrutura exata dos novos hooks (padrão `useMutation` com `onSuccess` invalidation)
- Ordem de operações na reversão do saldo ao apagar pagamento

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useHonorario(id)` em `use-financeiro.ts` — reutilizar, apenas atualizar tipos
- `useCreatePagamento()` — padrão a seguir para `useDeletePagamento()`
- `useCreateHonorario()` — padrão a seguir para `useUpdateHonorario()` e `useDeleteHonorario()`
- Tipos em `web/src/types/financeiro.ts` — migrar todos para camelCase
- Schemas Zod em `web/src/schemas/financeiro.ts` — atualizar field names

### Established Patterns
- TanStack Query mutations com `onSuccess: () => queryClient.invalidateQueries()`
- `apiFetch<T>()` de `@/lib/api` para todas as chamadas API
- `@PreAuthorize("hasAuthority('financeiro:...')")` no backend para RBAC
- Todos os endpoints financeiros em `ResourceController.java` (linhas 1754-1810)
- Tenant scoping via `getTenantId()` em todos os endpoints

### Integration Points
- `web/src/types/financeiro.ts` — tipos de resposta da API
- `web/src/schemas/financeiro.ts` — schemas Zod para forms
- `web/src/hooks/use-financeiro.ts` — todos os hooks de data fetching
- `backend/.../controllers/ResourceController.java` — adicionar 4 novos endpoints
- `backend/.../models/Pagamento.java` — verificar campos e JPA annotations
- Páginas financeiro que consomem os tipos: `page.tsx`, `[id]/page.tsx`, `novo/page.tsx`

</code_context>

<specifics>
## Specific Ideas

- Verificar se existe `application.yml` com `spring.jackson.property-naming-strategy: SNAKE_CASE` — se sim, é isso que causa o snake_case no frontend; se não, o backend já retorna camelCase e só os tipos TS é que estão errados
- O hook `useHonorario` já chama `GET /honorarios/{id}` — confirmar que o endpoint existe no backend (suspeita: não existe, frontend obtém 404)

</specifics>

<deferred>
## Deferred Ideas

- UI de edição de honorário (botão edit + form modal) — Phase 45
- Botão delete na página de detalhe — Phase 45
- Status badge de pagamento — Phase 44

</deferred>
