# Phase 83: Frontend — Tipos, Schemas e Hooks - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — smart discuss skipped)

<domain>
## Phase Boundary

A camada de dados do frontend conhece os campos e entidades novos com tipagem e validação corretas, e o mapeamento camelCase/snake_case está coberto para todos eles antes de qualquer UI ser construída.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Todas as decisões de implementação ficam ao critério de Claude — fase de infraestrutura pura (todos os critérios de sucesso são técnicos: tipos/schemas/hooks existem, mapeamento é correto). Usar os critérios de sucesso do ROADMAP.md e o contrato real construído na Phase 81 (não apenas o que a investigação de arquitetura previu) como fonte de verdade:

- **A fonte de verdade para o shape exato de request/response de cada endpoint é o código atual de `backend/src/main/java/com/lexcv/controllers/ResourceController.java`** (12 endpoints Decisão/Facto/Testemunha da Phase 81 + o wiring de `juizo`/`origem`), não a descrição de alto nível em `.planning/research/ARCHITECTURE.md`, que pode divergir em detalhes de implementação
- **Prevenção da recorrência do bug de mapeamento (já visto 3 vezes neste projeto):** `normalizeProcesso()`/`toProcessoApiPayload()` em `use-processos.ts` devem ser atualizados na MESMA revisão que os novos tipos são definidos — não como um passo separado assumido. O critério de aceitação para esta tarefa deve incluir um teste de round-trip (criar/atualizar via hook → refetch → confirmar que `juizo`/`origem` sobrevivem), não apenas `tsc`/build limpo
- `origem` no `schemas/processos.ts` deve ser promovido de `optionalTrimmedString` (ou equivalente) para um `z.enum(["PETICAO_INICIAL", "NOTIFICACOES_AVULSAS"])` obrigatório, com labels traduzidos ("Petição Inicial"/"Notificações Avulsas") para exibição na UI (Phase 84 consome isto)
- `TipoDecisao`/`TipoTestemunha` seguem o mesmo padrão de enum-com-label já usado no projeto (ex.: `documento_tipo` em clientes) — valores ASCII estáveis no wire, labels em português apenas na camada de apresentação
- Os 4 hooks por entidade (list/create/update/delete) seguem exatamente a convenção já usada por `useProcessoPartes`/`useAddProcessoParte`/etc. em `use-processos.ts`, incluindo `queryKey: ["processos", "<subresource>", id]` e invalidação de cache no `onSuccess`

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `web/src/hooks/use-processos.ts` — `useProcessoFases`/`useAddProcessoFase`/etc. (e o quarteto Partes) são o template exato a replicar 3x (Decisão/Facto/Testemunha)
- `web/src/types/processos.ts` e `web/src/schemas/processos.ts` — já têm o padrão estabelecido para `ProcessoFase`/`ProcessoParte`
- `backend/src/main/java/com/lexcv/controllers/ResourceController.java` — fonte de verdade viva para os 12 endpoints construídos na Phase 81 (ler diretamente, não confiar apenas em PATTERNS.md/ARCHITECTURE.md antigos)

### Established Patterns
- Este projeto já corrigiu 3 bugs de mismatch de campo frontend/backend nesta sessão (incluindo um `fase_id`/`nome` na própria página de processos) — a causa raiz recorrente é assumir o shape do payload em vez de o ler diretamente do controller

### Integration Points
- Nenhuma UI consome estes hooks ainda (Phase 84) — esta fase só precisa que os hooks compilem e funcionem corretamente contra a API real, verificável via chamadas diretas/testes de round-trip, não via renderização

</code_context>

<specifics>
## Specific Ideas

Nenhuma ideia específica adicional além das decisões já capturadas acima.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope (infrastructure-only, no grey areas to resolve).

</deferred>
