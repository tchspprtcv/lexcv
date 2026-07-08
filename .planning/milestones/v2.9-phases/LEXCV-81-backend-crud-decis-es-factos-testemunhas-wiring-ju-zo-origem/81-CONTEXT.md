# Phase 81: Backend — CRUD Decisões/Factos/Testemunhas + Wiring Juízo/Origem - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning

<domain>
## Phase Boundary

A API expõe CRUD completo e seguro para Decisões, Factos e Testemunhas, e os campos Juízo/Origem estão totalmente integrados no ciclo de vida do Processo (criação, edição, intake e listagem).

</domain>

<decisions>
## Implementation Decisions

### Reordenação de Factos
- O endpoint `POST /processos/{id}/factos` ignora qualquer `ordem` vindo do payload do cliente e calcula automaticamente `max(ordem)+1` por `processo_id` no servidor (append semantics)
- O endpoint `PUT /processos/{id}/factos/{factoId}` aceita um novo valor de `ordem` explícito no payload, permitindo ao utilizador reordenar depois de criado (a UI da Phase 84 enviará PUTs individuais, ex. drag-and-drop)
- Esta decisão evita depender de uma restrição de unicidade `(processo_id, ordem)` a nível de base de dados, que não foi adicionada na Phase 80 (ver 80-REVIEW.md IN-02) — a invariante fica inteiramente a cargo deste controller

### Imutabilidade de Origem
- `updateProcesso` (`PUT /processos/{id}`) lê o payload recebido mas **nunca escreve** em `processo.origem`, mesmo que o campo venha preenchido no corpo do pedido — silenciosamente ignorado, sem erro 400
- A resposta de `updateProcesso` continua a incluir `origem` para leitura (o campo é visível, apenas não editável — consistente com PROC-05)
- `origem` só é gravável através de `POST /processos/intake` (onde passa a ser obrigatório — PROC-03/PROC-04)

### Claude's Discretion
Todas as restantes decisões de implementação ficam ao critério de Claude, guiadas pelos critérios de sucesso do ROADMAP.md, REQUIREMENTS.md (PROC-02 a PROC-05, PROC-07, PROC-08, PROC-10, PROC-12, PROC-17) e pela investigação de milestone já feita (`.planning/research/ARCHITECTURE.md`, `.planning/research/PITFALLS.md`):

- Os 12 endpoints (`GET/POST/PUT/DELETE` para `/decisoes`, `/factos`, `/testemunhas`) vivem dentro do `ResourceController.java` existente (não um controller novo — `ParecerPesquisaController` não é precedente organizacional, resolveu uma colisão de rotas específica)
- Cada operação de escrita revalida o tenant do processo pai E o `processoId` da entidade filha (padrão `ProcessoFase`, não o padrão mais simples de `Parte`/`Movimentacao`) — esta é a implementação direta de PROC-17
- O endpoint de criação de Decisão aceita upload multipart direto (cria o `Documento` internamente e associa-o via `Decisao.documentoId`), replicando o padrão de validação de posse de tenant já usado no upload de `POST /documentos/upload` (Phase 79 desta plataforma)
- `juizo`/`origem` devem ser persistidos e devolvidos por `createProcesso`, `updateProcesso`, `createProcessoIntake`, e adicionados ao mapa enriquecido construído manualmente por `listProcessos` (risco de integração mais alto identificado pela investigação de arquitetura — este endpoint constrói a resposta como `LinkedHashMap`, não serializa a entidade diretamente)
- `origem` torna-se obrigatório em `POST /processos/intake` (atualmente não valida nada) e em `CAMPOS_MINIMOS_POR_TIPO` para TODAS as chaves, incluindo `"default"` (ver `ResourceController.java` linha ~72-80 para a estrutura atual do mapa)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — `ProcessoFase` endpoints (~linha 1541-1621) são o padrão exato de double-check (tenant do processo pai + `processoId` da entidade filha) a replicar para Decisão/Facto/Testemunha
- `ResourceController.java` linha 72-80 — `CAMPOS_MINIMOS_POR_TIPO`, o mapa estático que precisa de `origem` adicionado a cada entrada
- `ResourceController.java` linha 985 (`updateProcesso`) e linha 1024 (`createProcessoIntake`) — pontos de entrada onde `juizo`/`origem` devem ser lidos/persistidos
- `POST /documentos/upload` — padrão de validação de posse de tenant para `clienteId`/`processoId` (Phase 79) a replicar para o `documentoId` criado internamente pelo endpoint de criação de Decisão

### Established Patterns
- Entidades filhas (`Decisao`, `Facto`, `Testemunha`) já existem desde a Phase 80, sem `tenant_id` próprio — isolamento de tenant é sempre transitivo via o `Processo` pai, verificado no controller
- `listProcessos` constrói a resposta manualmente como `Map`/`LinkedHashMap` em vez de serializar a entidade Processo diretamente — este é o ponto onde novos campos são frequentemente esquecidos (já aconteceu antes neste projeto)

### Integration Points
- Frontend (Phase 83/84) só pode começar depois destes 12 endpoints + wiring de `juizo`/`origem` estarem estáveis — nenhuma mudança de contrato depois desta fase

</code_context>

<specifics>
## Specific Ideas

Nenhuma ideia específica adicional além das decisões já capturadas acima e no ROADMAP.md.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
