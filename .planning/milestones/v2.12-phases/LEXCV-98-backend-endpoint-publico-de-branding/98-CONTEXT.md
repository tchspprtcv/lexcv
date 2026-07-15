# Phase 98: Backend — Endpoint Público de Branding - Context

**Gathered:** 2026-07-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Criar um endpoint público não-autenticado `GET /api/v1/public/branding` que devolve exclusivamente `nome` e `logoDataUrl` da tenant singleton via um DTO explícito de cópia campo-a-campo, registado na allowlist do `SecurityConfig` como entrada literal exacta — nunca wildcard.

Esta fase adiciona apenas um novo controller/DTO isolado; nenhum código existente (`AuthController`, `SetupController`, `UserPrincipal`, scopes `@PreAuthorize`) é modificado. Paralelizável com Phase 99 (`webpage/`).

</domain>

<decisions>
## Implementation Decisions

### Empty-System Behavior
- Quando não existe tenant (sistema não inicializado): retornar **404 Not Found** com body `{"message": "Sistema não inicializado."}` — semântica REST limpa (o recurso não existe); o gate de setup-status de `webpage/` já redireciona para `/setup` antes que este endpoint seja alcançado em fluxo normal
- `logoDataUrl` null (tenant existe, mas logo não foi definido no setup): retornar JSON null explícito (`"logoDataUrl": null`) — consistente com o padrão já usado em `UserResponse.tenant_logo_data_url`

### Controller Organization
- Novo `PublicController.java` standalone (não adicionado ao `SetupController`) — segue o precedente de controllers estreitos e de propósito único (SetupController, AuthController, ParecerPesquisaController)
- Mapeamento da classe: `@RequestMapping("/api/v1/public")`
- Endpoint: `@GetMapping("/branding")` — path completo resultante: `/api/v1/public/branding`

### DTO
- Nova classe `TenantPublicInfoResponse` com Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor` (mesmo padrão de UserResponse, SetupStatusResponse)
- Campos: `String nome` e `String logoDataUrl` — exatamente estes dois, nunca `nif`, `email`, `telefone`, `tipoEntidade`, `id`, `createdAt`
- Serialização: camelCase nativo de Java → JSON `"nome"` + `"logoDataUrl"` (sem `@JsonProperty` adicional, sem snake_case manual)

### Repository
- Adicionar `Optional<Tenant> findFirstByOrderByCreatedAtAsc()` a `TenantRepository.java` — derived query Spring Data, sem JPQL manual; devolve a tenant mais antiga (singleton no modelo actual)

### Security
- `SecurityConfig.permitAll()` ganha exactamente uma nova entrada literal `"/api/v1/public/branding"` — nunca wildcard tipo `"/api/v1/public/**"`, para que nenhum endpoint futuro sob esse prefixo fique pré-autorizado silenciosamente

### Claude's Discretion
- Nome do método no controller: `getBranding()` ou `getPublicBranding()` — qualquer um é aceitável
- Testes: se a estrutura de testes existente o permitir, um teste Mockito simples confirmando que o controller devolve 404 quando o repositório devolve Optional.empty() é desejável mas não bloqueante (esta fase não tem requisito TEST-* dedicado)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Tenant.java` — entidade JPA com campos `nome` (String, NOT NULL), `logoDataUrl` (String @Lob, nullable), mais `nif`/`tipoEntidade`/`email`/`telefone` que NÃO devem aparecer na resposta pública
- `TenantRepository.java` — actualmente apenas herda de JpaRepository; precisa de `findFirstByOrderByCreatedAtAsc()` adicionado
- `SetupController.java` — modelo de controller narrow+público: `@RestController @RequestMapping @RequiredArgsConstructor`, dependências via constructor injection, `ResponseEntity<?>` tipado
- `UserResponse.java` — modelo DTO: Lombok `@Data @Builder @NoArgsConstructor @AllArgsConstructor`, campos com nomes Java directos, `null` para campos não definidos
- Padrão de cópia explícita em `AuthController.getMe()`: builder setter-a-setter, nunca serialização directa da entidade

### Established Patterns
- SecurityConfig usa array de strings literais exactas no `requestMatchers(...).permitAll()` — nunca wildcards
- Mensagens de erro em português: `Map.of("message", "...")` com `ResponseEntity.status(HttpStatus.NOT_FOUND).body(...)`
- DTOs usam Lombok; nunca se serializa a entidade JPA directamente para JSON

### Integration Points
- `SecurityConfig.securityFilterChain()` — adicionar a nova rota ao array de `requestMatchers(...).permitAll()`
- `TenantRepository.java` — adicionar o método derived query
- `dtos/` — nova classe `TenantPublicInfoResponse.java`
- `controllers/` — nova classe `PublicController.java`

</code_context>

<specifics>
## Specific Ideas

- Quando tenant não encontrado: `ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Sistema não inicializado."))` — idêntico ao padrão de erro já usado em SetupController e ResourceController
- Quando tenant encontrado: `TenantPublicInfoResponse.builder().nome(t.getNome()).logoDataUrl(t.getLogoDataUrl()).build()` — cópia explícita getter-para-setter, nunca retornar `t` directamente
- O endpoint é stateless e não depende de SecurityContextHolder — idêntico ao SetupController (que também não lê contexto de segurança)

</specifics>

<deferred>
## Deferred Ideas

- Endpoint adicional `GET /api/v1/public/setup-status` consolidando o status do sistema — não faz parte do âmbito desta fase (SetupController já o expõe em `/api/v1/setup/status`)
- Caching do resultado de branding (a tenant raramente muda) — prematuridade; não há requisito de performance nesta milestone
- Suporte multi-tenant público (múltiplos tenants por slug/subdomínio) — explicitamente fora de âmbito na v2.12 (PROJECT.md Out of Scope)

</deferred>
