# Phase 43: Data Layer + Backend Endpoints - Research

**Researched:** 2026-06-18
**Domain:** Spring Boot Jackson serialization + TypeScript type migration + REST CRUD endpoints
**Confidence:** HIGH

## Summary

A causa raiz do problema snake_case no módulo financeiro foi confirmada: **não existe nenhuma NamingStrategy Jackson configurada no backend**. O `application.yml` não contém `spring.jackson.property-naming-strategy` e não há nenhuma classe `@Configuration` Jackson no projeto. Isto significa que o backend Jackson usa o comportamento padrão — serializa campos Java camelCase diretamente como camelCase JSON. Os modelos JPA `Honorario` e `Pagamento` têm campos Java camelCase (`processoId`, `valorTotal`, `honorarioId`, `valorPago`, `dataPagamento`) que já são serialized como `processoId`, `valorTotal`, etc. O problema está inteiramente nos **tipos TypeScript e schemas Zod do frontend**, que usam snake_case incorretamente.

Os quatro endpoints em falta (`GET /honorarios/{id}`, `PUT /honorarios/{id}`, `DELETE /honorarios/{id}`, `DELETE /pagamentos/{id}`) foram confirmados por inspecção direta do `ResourceController.java`. O hook `useHonorario(id)` já existe e chama `GET /honorarios/{id}`, que actualmente retorna 404.

O padrão de tenant scoping para honorários/pagamentos é indireto: sem `tenant_id` nos modelos `Honorario` ou `Pagamento`, o scoping é feito via `Processo.tenantId`. Todos os novos endpoints devem verificar que o processo associado ao honorário pertence ao tenant corrente.

**Primary recommendation:** Corrigir os tipos TypeScript (snake_case → camelCase) atomicamente, depois adicionar os 4 endpoints no ResourceController seguindo o padrão de tenant scoping via processo já estabelecido.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Tipo TypeScript camelCase | Frontend | — | Tipos residem em `web/src/types/financeiro.ts` |
| Schemas Zod camelCase | Frontend | — | Schemas residem em `web/src/schemas/financeiro.ts` |
| Hooks TanStack Query (update/delete) | Frontend | — | `use-financeiro.ts` |
| GET /honorarios/{id} | API/Backend | — | ResourceController, tenant scoping via processo |
| PUT /honorarios/{id} | API/Backend | — | ResourceController, financeiro:edit |
| DELETE /honorarios/{id} | API/Backend | — | ResourceController, financeiro:manage, 409 se tem pagamentos |
| DELETE /pagamentos/{id} | API/Backend | — | ResourceController, financeiro:manage, reverter saldo ContaCorrente |

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**camelCase Migration Scope**
- Verificar primeiro se o backend já retorna camelCase ou snake_case (grep por Jackson NamingStrategy config no backend)
- Migração atómica: renomear todos os campos de uma vez (não usar adaptadores intermédios) — mesmo padrão da migração agenda v1.9
- Atualizar também os schemas Zod em `web/src/schemas/financeiro.ts` para alinhar com os novos nomes de campos
- Remover `tenantId` do tipo `Honorario` frontend (nunca necessário na UI); manter `createdAt` em camelCase

**Backend New Endpoints**
- Adicionar todos os novos endpoints em `ResourceController.java` (padrão atual do projeto)
- `DELETE /honorarios/{id}`: rejeitar com 409 Conflict se o honorário tiver pagamentos — mensagem de erro clara
- `PUT /honorarios/{id}`: campos editáveis são `valorTotal`, `descricao`, `dataAcordo` — `processoId` não é editável após criação
- `DELETE /pagamentos/{id}`: subtrair `valorPago` do saldo da conta corrente (operação simétrica ao POST /pagamentos)

**Frontend Integration**
- `useHonorario(id)` já existe — apenas corrigir os tipos para camelCase (sem novo hook)
- Adicionar `useUpdateHonorario()` e `useDeleteHonorario()` em `web/src/hooks/use-financeiro.ts`
- Adicionar `useDeletePagamento()` em `web/src/hooks/use-financeiro.ts`; deve invalidar cache de pagamentos e conta-corrente
- Nesta fase apenas adicionar hooks e endpoints — a UI de edição/eliminação é entregue na Phase 45

### Claude's Discretion
- Estrutura exata dos novos hooks (padrão `useMutation` com `onSuccess` invalidation)
- Ordem de operações na reversão do saldo ao apagar pagamento

### Deferred Ideas (OUT OF SCOPE)
- UI de edição de honorário (botão edit + form modal) — Phase 45
- Botão delete na página de detalhe — Phase 45
- Status badge de pagamento — Phase 44
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| FIN-01 | Frontend types usa camelCase (processoId, valorTotal, dataAcordo, honorarioId, valorPago, dataPagamento) | Confirmado: backend já serializa camelCase; só os tipos TS e schemas Zod precisam de ser corrigidos |
| FIN-02 | Schemas Zod alinhados com camelCase | Confirmado: `financeiro.ts` usa snake_case (`processo_id`, `valor_total`, etc.) — todos precisam de ser atualizados |
| FIN-03 | GET /honorarios/{id} com tenant scoping | Endpoint inexistente confirmado; padrão de scoping via processo estabelecido |
| FIN-04 | PUT /honorarios/{id} para edição (financeiro:edit) | Endpoint inexistente; campos editáveis: valorTotal, descricao, dataAcordo |
| FIN-05 | DELETE /honorarios/{id} com validação 409 se tem pagamentos (financeiro:manage) | Endpoint inexistente; PagamentoRepository.findByHonorarioId() disponível para verificação |
| FIN-06 | DELETE /pagamentos/{id} com reversão de saldo ContaCorrente (financeiro:manage) | Endpoint inexistente; padrão de reversão simétrico ao POST /pagamentos (subtrair em vez de adicionar) |
</phase_requirements>

## Standard Stack

### Core (já existente no projeto — sem novas instalações)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot / Jackson | 3.4.1 | JSON serialization | Projeto existente; sem NamingStrategy configurada → camelCase por omissão |
| TanStack Query | v5 (pnpm) | Data fetching e mutations | Padrão do projeto |
| Zod | v3 (pnpm) | Schema validation para forms | Padrão do projeto |

**Nenhuma nova dependência necessária nesta fase.**

## Package Legitimacy Audit

Nenhum novo pacote a instalar nesta fase. Audit não aplicável.

## Architecture Patterns

### Tenant Scoping Indireto (padrão existente para Honorario/Pagamento)

O modelo `Honorario` não tem `tenant_id`. O tenant scoping é feito verificando se o `Processo` associado pertence ao tenant corrente:

```java
// Source: ResourceController.java lines 1780-1789 (padrão existente)
Honorario hon = honorarioRepository.findById(id).orElse(null);
if (hon == null) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
}
Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
if (processo == null || !processo.getTenantId().equals(getTenantId())) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
}
```

Este padrão DEVE ser seguido em todos os novos endpoints.

### ContaCorrente Saldo Reversal (padrão para DELETE /pagamentos/{id})

O POST /pagamentos adiciona o valorPago ao saldo. O DELETE deve subtrair:

```java
// Source: ResourceController.java lines 1805-1816 (padrão POST — inverter para DELETE)
ContaCorrente cc = contaCorrenteRepository.findByClienteId(clienteId)
    .orElseGet(() -> contaCorrenteRepository.save(
        ContaCorrente.builder().clienteId(clienteId).saldo(BigDecimal.ZERO).build()
    ));
// POST: cc.setSaldo(cc.getSaldo().add(pag.getValorPago()));
// DELETE: cc.setSaldo(cc.getSaldo().subtract(pag.getValorPago()));
contaCorrenteRepository.save(cc);
```

### TanStack Query Mutation Pattern (padrão do projeto)

```typescript
// Source: use-financeiro.ts lines 68-84 (useCreatePagamento como modelo)
export function useDeletePagamento() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (pagamentoId: number) =>
      apiFetch<void>(`/pagamentos/${pagamentoId}`, { method: "DELETE" }),
    onSuccess: async (_data, _pagamentoId) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ["honorarios", "pagamentos"] }),
        queryClient.invalidateQueries({ queryKey: ["clientes", "conta-corrente"] }),
      ]);
    },
  });
}
```

### Anti-Patterns to Avoid
- **Adaptadores snake_case intermédios:** A migração é atómica — não usar transformações parciais ou aliases. Mudar todos os campos de uma vez.
- **Tenant scoping por campo direto:** Honorario/Pagamento não têm tenant_id — não tentar adicionar esse campo. O scoping é via processo.
- **`processoId` editável via PUT:** O processo associado a um honorário não deve ser alterável após criação.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Verificar pagamentos antes de DELETE honorário | Lógica custom | `pagamentoRepository.findByHonorarioId(id).isEmpty()` | Repository já existe e tem o método |
| Tenant scoping para honorário | Novo campo tenant_id no modelo | Verificação via `processo.getTenantId()` | Padrão estabelecido no projeto; DDL-auto=update mas adicionar coluna é risco desnecessário |

## Common Pitfalls

### Pitfall 1: Assumir que o backend retorna snake_case
**What goes wrong:** Adicionar transformações no frontend sem verificar a causa raiz.
**Why it happens:** Os tipos TS usam snake_case, então assume-se que o backend serializa snake_case.
**How to avoid:** Verificado: sem `spring.jackson.property-naming-strategy` em application.yml e sem classe Jackson Config → backend JÁ retorna camelCase. A correção é APENAS nos tipos TS e schemas Zod.
**Warning signs:** Se após migrar os tipos ainda houver erros de serialização, verificar se existe alguma anotação `@JsonProperty` nos modelos JPA.

### Pitfall 2: Cache invalidation incompleta no DELETE /pagamentos
**What goes wrong:** O hook `useDeletePagamento` invalida apenas o cache de pagamentos, deixando o saldo da conta corrente stale no frontend.
**How to avoid:** Invalidar AMBOS: `["honorarios", "pagamentos", honorarioId]` e `["clientes", "conta-corrente"]` — igual ao `useCreatePagamento`.
**Warning signs:** Saldo exibido na UI não atualiza após deletar pagamento.

### Pitfall 3: PUT /honorarios/{id} aceitar processoId no body
**What goes wrong:** Endpoint atualiza processoId se vier no body do request.
**How to avoid:** O handler deve ignorar processoId do body — apenas atualizar `valorTotal`, `descricao`, `dataAcordo` a partir do body, nunca `processoId`.

### Pitfall 4: DELETE /honorarios sem verificar pagamentos
**What goes wrong:** Honorário com pagamentos é apagado, orphaning pagamentos na base de dados.
**How to avoid:** Verificar `pagamentoRepository.findByHonorarioId(id)` antes de deletar — retornar 409 Conflict se não estiver vazio.

### Pitfall 5: Honorario modelo não tem `createdAt`
**What goes wrong:** O tipo `Honorario` frontend menciona `createdAt`, mas o modelo JPA `Honorario.java` não tem esse campo.
**Root cause:** O modelo JPA foi verificado — não tem `created_at`/`createdAt`. A decisão CONTEXT.md de "manter `createdAt` em camelCase" implica que pode precisar de ser adicionado ao modelo ou removido do tipo frontend.
**How to avoid:** Verificar se o campo existe em produção. Se não existir no modelo, remover do tipo TS em vez de adicionar ao modelo JPA (para evitar migração DDL).

## Code Examples

### Campos actuais vs. campos correctos

**`web/src/types/financeiro.ts` — estado atual (ERRADO):**
```typescript
// snake_case incorrecto — backend retorna camelCase
export interface Honorario {
  id: number;
  tenant_id: string;   // remover (nunca necessário na UI)
  processo_id: string; // → processoId
  valor_total: number; // → valorTotal
  descricao?: string;
  data_acordo?: string; // → dataAcordo
  created_at: string;   // → createdAt (verificar se campo existe no modelo JPA)
}
```

**`web/src/types/financeiro.ts` — estado correto:**
```typescript
export interface Honorario {
  id: number;
  processoId: string;
  valorTotal: number;
  descricao?: string;
  dataAcordo?: string;
  // createdAt?: string; // apenas se campo existir no modelo JPA
}

export interface HonorarioCreateRequest {
  processoId: string;
  valorTotal: number;
  descricao?: string;
  dataAcordo?: string;
}

export interface Pagamento {
  id: number;
  honorarioId: number;
  valorPago: number;
  dataPagamento: string;
  metodo?: string;
}

export interface PagamentoCreateRequest {
  honorarioId: number;
  valorPago: number;
  dataPagamento?: string;
  metodo?: string;
}
```

### Schemas Zod — campos a renomear

```typescript
// web/src/schemas/financeiro.ts — estado correto
export const honorarioFormSchema = z.object({
  processoId: z.string().trim().min(1, "O processo é obrigatório"),
  valorTotal: moneyString,
  descricao: optionalTrimmedString,
  dataAcordo: optionalDateString,
});

export const pagamentoFormSchema = z.object({
  valorPago: moneyString,
  dataPagamento: optionalDateString,
  metodo: optionalTrimmedString,
});
```

### Endpoint GET /honorarios/{id}

```java
// Source: padrão de listHonorarioPagamentos (ResourceController.java lines 1778-1790)
@PreAuthorize("hasAuthority('financeiro:view')")
@GetMapping("/honorarios/{id}")
public ResponseEntity<?> getHonorario(@PathVariable Integer id) {
    Honorario hon = honorarioRepository.findById(id).orElse(null);
    if (hon == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
    }
    Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
    }
    return ResponseEntity.ok(hon);
}
```

### Endpoint PUT /honorarios/{id}

```java
@PreAuthorize("hasAuthority('financeiro:edit')")
@PutMapping("/honorarios/{id}")
public ResponseEntity<?> updateHonorario(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
    Honorario hon = honorarioRepository.findById(id).orElse(null);
    if (hon == null) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
    }
    Processo processo = processoRepository.findById(hon.getProcessoId()).orElse(null);
    if (processo == null || !processo.getTenantId().equals(getTenantId())) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Honorário não encontrado"));
    }
    // Atualizar apenas campos editáveis — processoId não é editável
    // Campos: valorTotal, descricao, dataAcordo
    return ResponseEntity.ok(honorarioRepository.save(hon));
}
```

### Endpoint DELETE /honorarios/{id}

```java
@PreAuthorize("hasAuthority('financeiro:manage')")
@DeleteMapping("/honorarios/{id}")
public ResponseEntity<?> deleteHonorario(@PathVariable Integer id) {
    Honorario hon = honorarioRepository.findById(id).orElse(null);
    // ... tenant scoping via processo ...
    List<Pagamento> pagamentos = pagamentoRepository.findByHonorarioId(id);
    if (!pagamentos.isEmpty()) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(Map.of("message", "Não é possível eliminar um honorário com pagamentos registados"));
    }
    honorarioRepository.deleteById(id);
    return ResponseEntity.noContent().build();
}
```

### Endpoint DELETE /pagamentos/{id}

```java
@PreAuthorize("hasAuthority('financeiro:manage')")
@DeleteMapping("/pagamentos/{id}")
public ResponseEntity<?> deletePagamento(@PathVariable Integer id) {
    Pagamento pag = pagamentoRepository.findById(id).orElse(null);
    // ... tenant scoping via honorario → processo ...
    // Reverter saldo
    try {
        UUID clienteId = processo.getClienteId();
        ContaCorrente cc = contaCorrenteRepository.findByClienteId(clienteId).orElse(null);
        if (cc != null) {
            cc.setSaldo(cc.getSaldo().subtract(pag.getValorPago()));
            contaCorrenteRepository.save(cc);
        }
    } catch (Exception ignored) {}
    pagamentoRepository.deleteById(id);
    return ResponseEntity.noContent().build();
}
```

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| snake_case no frontend (estado actual) | camelCase alinhado com Jackson padrão | Elimina erros de serialização; alinha com agenda v1.9 |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | O modelo `Honorario` não tem `createdAt` — o tipo frontend deve remover esse campo em vez de adicionar ao modelo JPA | Pitfall 5 / Code Examples | Se `createdAt` existir na BD (de uma migração manual), o campo deve ser adicionado ao modelo e mantido no tipo TS |
| A2 | O `useCreateHonorario` envia `processo_id` no body em snake_case — backend aceita porque @RequestBody Honorario usa o campo `processoId` e Jackson desserializa `processo_id` como falha silenciosa (campo fica null) ou funciona por acidente | Standard Stack | Testar após migração para confirmar que POST /honorarios ainda funciona com o payload camelCase |

## Open Questions

1. **Campo `createdAt` no modelo Honorario**
   - What we know: O tipo frontend actual tem `created_at`; o modelo JPA `Honorario.java` não tem esse campo.
   - What's unclear: O campo existe na tabela `t_honorario` em produção? Foi adicionado manualmente?
   - Recommendation: Remover `createdAt` do tipo TS. Se necessário no futuro, adicionar ao modelo JPA com `@Column(name="created_at", insertable=false, updatable=false)` e `@CreationTimestamp`.

2. **Query param `processo_id` vs `processoId` em GET /honorarios**
   - What we know: `useHonorarios` envia `?processo_id=...` como query param. O endpoint `listHonorarios` não filtra por `processo_id` — retorna todos os honorários do tenant.
   - What's unclear: Se o endpoint deve ser atualizado para aceitar `processoId` como query param.
   - Recommendation: Fora do âmbito desta fase — o filtro por processoId no GET /honorarios é um comportamento separado. Manter o query param como está.

## Environment Availability

Step 2.6: SKIPPED — fase é puramente alterações de código (tipos TS, schemas Zod, endpoints Java). Sem novas dependências externas.

## Validation Architecture

### Test Framework
| Property | Value |
|----------|-------|
| Framework | Maven Surefire (backend JUnit 5); pnpm lint (frontend) |
| Config file | backend/pom.xml; web/package.json |
| Quick run command | `mvn test -pl backend` |
| Full suite command | `mvn test -pl backend && pnpm --dir web lint` |

### Phase Requirements → Test Map
| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| FIN-01 | Tipos TS sem snake_case | manual/lint | `pnpm --dir web build` | ❌ Wave 0 |
| FIN-02 | Schemas Zod com camelCase | manual | verificação visual dos schemas | ❌ Wave 0 |
| FIN-03 | GET /honorarios/{id} retorna 404 se tenant errado | manual | curl com token de outro tenant | ❌ sem infra de testes backend |
| FIN-04 | PUT /honorarios/{id} não altera processoId | manual | curl teste | ❌ |
| FIN-05 | DELETE /honorarios com pagamentos retorna 409 | manual | curl teste | ❌ |
| FIN-06 | DELETE /pagamentos reverte saldo | manual | verificar ContaCorrente após delete | ❌ |

### Wave 0 Gaps
- Frontend: `pnpm build` serve como smoke test de tipos TS (erros de compilação TypeScript surgem no build)
- Backend: sem framework de testes automatizados estabelecido — testes manuais via curl/Postman

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | JWT em httpOnly cookies — existente |
| V4 Access Control | yes | `@PreAuthorize("hasAuthority('financeiro:view/edit/manage')")` |
| V5 Input Validation | yes | Validação manual no handler (verificar processoId não null, valorTotal > 0) |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Acesso cross-tenant a honorários | Information Disclosure | Tenant scoping via processo.getTenantId() em cada endpoint |
| DELETE de recurso de outro tenant | Tampering | Mesma verificação de tenant antes de qualquer delete |
| Saldo negativo em ContaCorrente | Tampering | Aceitar saldo negativo (reversão legítima); não é um bug de segurança |

## Sources

### Primary (HIGH confidence)
- `backend/src/main/resources/application.yml` — confirmado: sem Jackson NamingStrategy
- `backend/src/main/java/com/lexcv/models/Honorario.java` — campos Java verificados
- `backend/src/main/java/com/lexcv/models/Pagamento.java` — campos Java verificados
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — endpoints existentes verificados
- `web/src/types/financeiro.ts` — tipos snake_case actuais verificados
- `web/src/schemas/financeiro.ts` — schemas snake_case verificados
- `web/src/hooks/use-financeiro.ts` — hooks existentes verificados

### Secondary (MEDIUM confidence)
- Padrão de migração agenda v1.9 mencionado em STATE.md — "camelCase migration pattern established for agenda module"

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — verificado diretamente nos ficheiros fonte
- Architecture: HIGH — padrão de tenant scoping verificado no código existente
- Pitfalls: HIGH — baseado em inspeção direta do código (sem NamingStrategy confirmado por grep)

**Research date:** 2026-06-18
**Valid until:** 2026-07-18 (código estável; sem dependências de versão)
