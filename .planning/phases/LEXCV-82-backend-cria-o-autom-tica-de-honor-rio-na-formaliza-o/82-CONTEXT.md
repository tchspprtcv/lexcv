# Phase 82: Backend — Criação Automática de Honorário na Formalização - Context

**Gathered:** 2026-07-07
**Status:** Ready for planning

<domain>
## Phase Boundary

Formalizar um processo (TRIAGEM→ATIVO) cria automaticamente e de forma segura um registo de Honorário associado, sem nunca preencher um valor financeiro sem confirmação explícita do utilizador.

</domain>

<decisions>
## Implementation Decisions

### Hook point e transação
- `formalizarProcesso()` em `ResourceController.java` (linha ~1196) já é `@Transactional` — a criação do Honorário deve acontecer dentro deste mesmo método, imediatamente após `processo.setEstado("ATIVO")` e antes/junto do `processoRepository.save(processo)` (linha ~1249-1250), reaproveitando a transação existente em vez de introduzir uma nova
- `honorarioRepository` já está injetado no controller (linha 60) e `HonorarioRepository.findByProcessoId(UUID)` já existe (usado noutros endpoints) — nenhuma alteração de repositório é necessária

### Idempotência
- Antes de criar, verificar `honorarioRepository.findByProcessoId(id)` — se já existir pelo menos um Honorário para este processo, NÃO criar outro (idempotente em retries/replays), independentemente do guard de estado (que já bloqueia re-formalizações de processos não-TRIAGEM, mas não protege contra uma race condition ou um estado de dados inconsistente)

### Correção de conflito com a investigação de arquitetura
- **`ARCHITECTURE.md` (investigação de milestone) sugeriu `valorTotal=0` como valor inicial — esta sugestão está desatualizada e NÃO deve ser seguida.** A decisão final, confirmada em REQUIREMENTS.md (PROC-14) e no ROADMAP.md desta fase, é que `valorTotal` começa sempre `null` (nunca `BigDecimal.ZERO`, nunca pré-preenchido a partir de `Cliente.honorariosPropostos`). `Honorario.valorTotal` já é uma coluna nullable (`@Column(name = "valor_total")`, sem `nullable = false`), pelo que isto não requer nenhuma alteração de schema
- Campos a preencher no Honorário auto-criado: `processoId` (o `id` do processo formalizado), `valorTotal = null`, `dataAcordo = LocalDate.now()`. `descricao` fica `null` (sem valor por omissão inventado). Não existem campos `clienteId`/`estado` na entidade `Honorario` hoje — não inventar nenhum

### Claude's Discretion
Todas as restantes decisões de implementação ficam ao critério de Claude, guiadas pelos critérios de sucesso do ROADMAP.md e REQUIREMENTS.md (PROC-14 apenas — esta é a única requirement desta fase).

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ResourceController.java:1193-1251` (`formalizarProcesso`) — método completo já lido; já `@Transactional`, já tem `@PreAuthorize("hasAuthority('processos:manage')")`, já valida estado/campos mínimos/conflict-check antes da transição
- `HonorarioRepository.java` — já expõe `findByProcessoId(UUID)`, suficiente para a verificação de idempotência
- `Honorario.java` — entidade já existe com `processoId` (UUID, not-null FK), `valorTotal` (BigDecimal, nullable), `descricao` (String, nullable), `dataAcordo` (LocalDate, nullable), `totalPago` (campo calculado read-only via `@Formula`)

### Established Patterns
- `Honorario` não tem `tenant_id` próprio nem `clienteId` — apenas `processo_id`; qualquer isolamento de tenant já é feito transitivamente via o `Processo` (mesmo padrão de Decisão/Facto/Testemunha da Phase 80/81)

### Integration Points
- Nenhum novo endpoint necessário — a criação acontece como efeito colateral de `POST /processos/{id}/formalizar`, endpoint já existente
- Sem impacto no frontend desta fase (Phase 83/84 tratam da UI do módulo de processos; a criação automática do Honorário é inteiramente transparente ao chamador — a resposta de `formalizarProcesso` continua a devolver o `Processo` atualizado, não o Honorário criado)

</code_context>

<specifics>
## Specific Ideas

Nenhuma ideia específica adicional além das decisões já capturadas acima.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>
