# Phase 111: Backend — Pesquisa Global Cross-Entity (API) - Context

**Gathered:** 2026-07-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Existe um endpoint backend que devolve, de forma segura e corretamente ordenada, resultados de Clientes, Processos, Documentos e Pareceres do tenant do utilizador autenticado — a fundação sobre a qual a experiência de pesquisa do utilizador (Phase 112) é construída. Backend apenas; nenhuma UI nesta fase.

</domain>

<decisions>
## Implementation Decisions

### Contrato de Resposta
- Endpoint: `GET /api/v1/pesquisa?q=<termo>` — rota em português, consistente com o resto da API deste projeto (`/clientes`, `/processos`, `/pareceres`); o precedente mais próximo (`ParecerPesquisaController`) já usa "Pesquisa" no nome
- Resposta: lista plana discriminada por tipo, um novo `record ResultadoPesquisaDto` (tipo, id, titulo, subtitulo, rota), mirroring o padrão já existente de `TimelineItemDto` — nunca um objeto pré-agrupado no backend; o agrupamento por tipo acontece no frontend (Phase 112)
- Limite: 5 resultados por tipo de entidade nesta fase
- Novo `PesquisaController` dedicado (`backend/.../controllers/`), nunca um método adicional no já enorme `ResourceController`
- **Nomenclatura**: `PesquisaController`/`ResultadoPesquisaDto`, não `SearchController`/`SearchResultDto` — CLAUDE.md exige nomes em português para entidades, rotas e DTOs (`TimelineItemDto` é uma exceção pré-existente no código antigo, não um precedente a seguir para código novo)

### Ranking & Matching
- Sem resultados mas query válida (>=2 chars) → 200 OK, lista vazia (nunca erro)
- Case-insensitive e sem distinguir acentos via `unaccent()` + `ILIKE` — fecha o gap real de nomes portugueses ("Conceição" vs "Conceicao") identificado no research
- Query com menos de 2 caracteres → também validada no backend (não confiar só no debounce do frontend); devolve lista vazia
- Correspondências exatas/prefixo em identificadores estruturados (numero_cliente, numero_processo, NIF, documento_numero) sempre ordenadas antes de correspondências por substring simples
- Desempate entre resultados do mesmo nível de ranking: mais recente primeiro (campo de data mais relevante por entidade)

### Erros e Validação
- Query em falta ou vazia (`?q=` ou sem `q`) → 200 OK, lista vazia (mesmo tratamento que <2 chars, nunca 400)
- Utilizador sem nenhum dos 4 scopes de visualização → 200 OK, lista vazia (nenhum ramo é consultado; nunca 403 — mais simples para o componente de pesquisa no frontend)
- Query truncada a um máximo razoável (200 caracteres) antes de ser usada em `ILIKE`, defesa básica contra abuso
- RBAC verificado por ramo de entidade dentro do handler (`hasAuthority(auth, "<scope>:view")` por tipo), nunca um `@PreAuthorize` genérico único — cada tipo só é consultado se o utilizador tiver o scope correspondente
- Tenant isolation: cada sub-query parte do próprio `tenant_id` da entidade; `Honorario`/financeiro nunca é consultado por este endpoint (não tem `tenant_id` próprio — ver PITFALLS.md)
- Testcontainers IT obrigatório nesta fase: 2 tenants com tokens de pesquisa coincidentes, asserting zero cross-tenant leakage em todos os 4 tipos — replica o padrão já existente em `NotificacaoRepositoryIT`
- Teste de matriz por role (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) confirmando que nenhum campo de `Honorario` aparece em nenhuma resposta

### Claude's Discretion
- Nome exato dos campos internos do `SearchResultDto` (além de tipo/id/titulo/subtitulo/rota)
- Estrutura exata das 4 queries nativas por entidade (seguindo o idioma já estabelecido em `ParecerSolicitacaoRepository.pesquisar()`/`NotificacaoRepository.buscarPorFiltros()`)
- Nome do ficheiro de migração (padrão `{fase}-{descricao-kebab}.sql`, ex: `111-enable-search-extensions.sql`)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ParecerSolicitacaoRepository.pesquisar()` e `NotificacaoRepository.buscarPorFiltros()` — precedentes diretos de query nativa `ILIKE` tenant-scoped a replicar
- `TimelineItemDto` — precedente direto de DTO discriminado por tipo a espelhar para `SearchResultDto`
- `NotificacaoRepositoryIT` — precedente direto de teste de integração Testcontainers a replicar para o novo IT

### Established Patterns
- Toda entidade tem `tenant_id` próprio EXCETO `Honorario` (só FK transitiva via `Processo`) — nunca incluir Honorario nesta pesquisa
- RBAC via `@PreAuthorize("hasAuthority('<scope>:<action>')")` por endpoint; scopes existentes: `clientes:view`, `processos:view`, `documentos:view`, `pareceres:view`
- `ParecerPesquisaController` já documenta (no seu próprio comentário de cabeçalho) um bug histórico de concatenação de path class-level/method-level — evitar repetir

### Integration Points
- Novo `PesquisaController` em `backend/src/main/java/com/lexcv/controllers/`
- Novo `ResultadoPesquisaDto` (record) em `backend/src/main/java/com/lexcv/dtos/`
- Nova migração SQL em `backend/migrations/` habilitando `unaccent`+`pg_trgm`
- Novo teste IT em `backend/src/test/.../` replicando `NotificacaoRepositoryIT`

</code_context>

<specifics>
## Specific Ideas

Ver `.planning/research/{STACK,ARCHITECTURE,PITFALLS}.md` — research aprofundado e implementation-ready para esta fase específica, com exemplos de código e citações file:line.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
