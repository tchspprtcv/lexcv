# Phase 33: Processos - Workflow, Gates e Prazos - Context

**Gathered:** 2026-06-15
**Status:** Ready for planning

<domain>
## Phase Boundary

Estruturar o acompanhamento do processo com uma maquina de estados explicita (transicoes permitidas + gates de validacao), responsavel atual e proximo passo, e prazos operacionais com prioridade, risco e escalonamento simples. Estende o ciclo de vida iniciado na Phase 32 (TRIAGEM -> ATIVO) para o workflow processual completo. Cobre PRC-27 e AGD-22.

**Inclui:** maquina de estados no backend (fonte de verdade), gates por transicao, responsavel no processo, derivacao de proximo passo/obrigatorios por estado, entidade Prazo com risco derivado e escalonamento, e a superficie UI (card de workflow no detalhe, lista de prazos com badges de risco, sinalizacao na listagem).

**Nao inclui:** timeline unificada e trilha auditavel completa (Phase 34), governanca documental/retencao (Phase 35), dashboards/KPI (Phase 36). Motor de notificacoes por email fica fora (apenas sinalizacao + flag).

</domain>

<decisions>
## Implementation Decisions

### Maquina de estados & transicoes (PRC-27)
- Estados e transicoes permitidas definidos no **backend** como fonte de verdade (mapa estado -> transicoes permitidas), espelhados no frontend.
- Conjunto de estados reusa o campo `Processo.estado`: `TRIAGEM -> ATIVO -> SUSPENSO (reversivel para ATIVO) -> ENCERRADO`, com reabertura permitida (ENCERRADO -> ATIVO) sob transicao critica.
- Gates: obrigatorios por transicao validados no **backend** (ex.: responsavel definido antes de ativar; justificativa obrigatoria para suspender/encerrar/reabrir).
- Cada transicao de estado cria uma `Movimentacao` (de -> para, autor, justificativa), reutilizando a trilha existente. (A transicao TRIAGEM->ATIVO continua a passar pelo gate de conflito da Phase 32.)

### Responsavel & proximo passo (PRC-27, UI)
- Responsavel atual modelado como campo `responsavel_id` (FK User) no `Processo`, alteravel por transicao/acao dedicada.
- "Proximo passo" e obrigatorios por estado derivados do mapa de estado no backend (o backend devolve as transicoes possiveis e os obrigatorios por estado), nao hardcoded no frontend.
- RBAC: transicoes normais exigem `processos:edit`; transicoes criticas (encerrar, reabrir) exigem `processos:manage`. Backend `@PreAuthorize` e frontend `hasScopedPermission` devem concordar.
- UI: seccao/card de workflow no detalhe (estado atual, responsavel, proximo passo, acao de transicao) + coluna de responsavel na listagem.

### Prazos operacionais, SLA & risco (AGD-22)
- Nova entidade `Prazo` (campos: tenant_id, processo_id, descricao, data_limite, prioridade, responsavel_id, concluido) ligada ao processo — evita sobrecarregar `Evento`.
- Nivel de risco derivado no **backend** a partir de `data_limite` vs data atual + prioridade: `ok` / `proximo` / `vencido`.
- Escalonamento simples: sinalizacao visual + flag `escalonado` quando o prazo esta `proximo`/`vencido` (sem motor de notificacoes/email nesta fase).
- O `Prazo` e a fonte no contexto do processo; pode opcionalmente espelhar um `Evento` de agenda, sem duplicar a regra de risco.

### Superficie UI & consistencia
- Card de workflow no detalhe com as acoes de transicao; transicoes que exigem justificativa abrem um Dialog com campo de justificativa (RHF + Zod).
- Lista de prazos no detalhe com badges de risco (`ok`/`proximo`/`vencido`), ordenada por `data_limite`.
- Listagem de processos sinaliza risco de prazo + responsavel + estado.
- Reutilizar os componentes de badge (v1.1 + padrao de variantes da Phase 32), Dialog e formularios RHF+Zod existentes; nao criar componentes novos quando ja existe analogo.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Processo` ja tem `estado` (base da maquina de estados) e `cliente_id`; adicionar `responsavel_id`.
- `Movimentacao` (`backend/.../models/Movimentacao.java`) ja tem `processo_id` e `prazo_id` — base para registar transicoes e ligar prazos.
- `Evento` (`backend/.../models/Evento.java`) tem `prioridade`, `data_inicio/fim`, `concluido` — referencia para o espelho de agenda do Prazo.
- `ProcessoFase` existe para fases; o workflow de estados e distinto (estado do processo).
- ResourceController (~1000 linhas) concentra CRUD de processos/eventos/movimentacoes; novos endpoints de transicao e prazos seguem o mesmo padrao `getTenantId()` + `@PreAuthorize`.
- Phase 32: scopes `processos:create`/`processos:manage` ja seedados; padrao de badge por nivel (`lib/conflict-check.ts`) e de bloqueio server-side a reutilizar para risco de prazo e gates de transicao.

### Established Patterns
- Backend Spring Boot multi-tenant (`tenant_id` em todas as queries), RBAC method-level, business rules no backend.
- Frontend Next.js + TanStack Query (`use-*.ts`), RHF+Zod, badges/Dialog reutilizaveis; `hasScopedPermission` espelha scopes.
- JPA `ddl-auto=update` cria novas tabelas/colunas (Prazo, responsavel_id) — sem migration files.

### Integration Points
- Detalhe do processo (`web/src/app/(dashboard)/processos/[id]/page.tsx`) recebe o card de workflow e a lista de prazos.
- Listagem (`web/src/app/(dashboard)/processos/page.tsx`) recebe colunas/indicadores de responsavel, estado e risco de prazo.
- Hooks em `web/src/hooks/use-processos.ts`; helper de risco analogo a `lib/conflict-check.ts`.

</code_context>

<specifics>
## Specific Ideas

- O mapa de estados/transicoes e os obrigatorios por gate devem ser uma fonte de verdade unica no backend, exposta ao frontend (o frontend nao deve duplicar/hardcodear as regras de transicao).
- A derivacao de risco do prazo (`ok`/`proximo`/`vencido`) e calculada no backend e partilhada com o frontend como nivel, analogamente aos 4 niveis de conflito da Phase 32.
- Transicoes criticas (encerrar/reabrir) exigem justificativa e `processos:manage`; o enforcement e server-side.

</specifics>

<deferred>
## Deferred Ideas

- Timeline unificada e trilha auditavel completa dos eventos sensiveis -> Phase 34.
- Motor de notificacoes/email para escalonamento de prazos -> futuro.
- Estados/transicoes configuraveis por tenant (em vez de definidos no backend) -> futuro.
- Governanca documental e retencao -> Phase 35.
- Dashboards de backlog/SLA/risco -> Phase 36 (esta fase expoe os dados; os paineis agregados vem depois).

</deferred>
