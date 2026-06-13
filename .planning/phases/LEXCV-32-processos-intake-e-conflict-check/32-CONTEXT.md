# Phase 32: Processos - Intake e Conflict Check - Context

**Gathered:** 2026-06-13
**Status:** Ready for planning

<domain>
## Phase Boundary

Formalizar a abertura de processos com um intake estruturado e um conflict check **bloqueante** antes da criacao formal do matter/processo. O processo passa a ter um ciclo de vida pre-formal (`triagem`) que so transita para ativo apos uma decisao de conflito registada. Cobre os requisitos INT-01 e CFL-01.

**Inclui:** modelo de intake, representacao do potencial cliente, mecanismo e classificacao do conflict check, registo de decisao, bloqueio da formalizacao, e a superficie UI (fluxo de novo processo, badges de estado, visibilidade do resultado).

**Nao inclui:** workflow processual completo com multiplos estados/gates (Phase 33), timeline/auditoria (Phase 34), governanca documental (Phase 35), dashboards/KPI (Phase 36).

</domain>

<decisions>
## Implementation Decisions

### Modelo de Intake (dados & ciclo de vida)
- Potencial cliente representado reusando `Cliente` com um estado/flag (`lead` -> `ativo` na abertura formal), evitando entidade duplicada.
- Processo pre-formal representado por um `Processo` criado em estado `triagem`, formalizado por transicao (reusa o campo `estado` existente).
- Campos minimos do intake sao um conjunto **fixo por `tipo_processo`**, definido no backend e validado no momento da formalizacao.
- Campos de intake persistidos no proprio `Processo` (campos adicionais, sem tabela 1-1).

### Conflict Check (mecanismo & classificacao)
- Ambito da pesquisa: cliente + partes relacionadas + parte contraria + assunto, pesquisando contra clientes/partes existentes no tenant.
- Tipo de correspondencia: exato (NIF) + aproximado (nome similar), reutilizando a logica do conflict check visual ja especificado em CLI-34 (v1.6).
- Classificacao do resultado em 4 niveis: `sem conflito` / `potencial` / `sanavel` / `impeditivo`.
- Execucao: endpoint backend dedicado devolve os matches + nivel sugerido; a decisao final e sempre humana (nao automatica).

### Decisao & Bloqueio (gating + RBAC)
- Registo da decisao inclui: decisor, data, nivel final, justificativa (texto) e referencia opcional a evidencia.
- RBAC: correr o conflict check exige `processos:create`; decidir/formalizar exige `processos:manage`. Backend (`@PreAuthorize`) e frontend (`hasScopedPermission`) devem concordar.
- Bloqueio: a formalizacao (transicao `triagem` -> `ativo`) e bloqueada no **backend** ate existir uma decisao de conflito registada; o UI desabilita a acao e mostra o motivo.
- Conflito `impeditivo` bloqueia a abertura; `sanavel`/`potencial` permitem prosseguir mas exigem justificativa para override (registado).

### Superficie UI
- Intake vive no modulo Processos como fluxo "Novo processo" por passos (intake -> conflict check -> abertura).
- Estado de triagem/decisao visivel tanto na **listagem** (badge de estado) como no **detalhe** (seccao dedicada com resultado do conflict check e decisao).
- Consistencia garantida reutilizando os componentes de badge existentes (v1.1), com os mesmos estados entre listagem e detalhe.
- Matches de conflito apresentados como lista (nivel + entidade + link para o registo) com a acao de decisao inline.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Processo` (`backend/.../models/Processo.java`) ja tem `estado`, `cliente_id`, `tipo_processo`, `area_juridica` — base para o ciclo de vida `triagem`.
- `Parte` (`backend/.../models/Parte.java`) modela partes do processo (nome, tipo) — fonte para pesquisa de parte contraria.
- `Cliente`, `ClienteContacto`, `ClienteNota` e repositorios existentes — base para representar potencial cliente e para o ambito do conflict check.
- `ResourceController` (~1000 linhas) concentra o CRUD de processos+partes; novos endpoints de intake/conflict check devem seguir o mesmo padrao e `getTenantId()`.
- Conflict check visual ja previsto em CLI-34 (v1.6) — reutilizar a heuristica de match exato/aproximado de NIF/nome.

### Established Patterns
- Backend Spring Boot, multi-tenant por `tenant_id` em todas as queries; RBAC method-level `@PreAuthorize("hasAuthority('<scope>:<action>')")`.
- Frontend Next.js App Router, TanStack Query (`use-*.ts` hooks) via `apiFetch`, RHF + Zod, badges/tabelas reutilizaveis (v1.1).
- `web/src/lib/permissions.ts#hasScopedPermission` espelha os scopes do backend (manage>edit>create>view).

### Integration Points
- Modulo Processos (`web/src/app/(dashboard)/processos`) — listagem e detalhe recebem badge de estado e seccao de conflito.
- Novos endpoints sob `/api/v1` (intake, conflict-check, decisao) no `ResourceController`.
- Seed (`DatabaseSeeder`) — possivel definicao de campos minimos por `tipo_processo`.

</code_context>

<specifics>
## Specific Ideas

- Os 4 niveis de conflito (`sem conflito`/`potencial`/`sanavel`/`impeditivo`) e a politica de override por nivel devem ser fonte de verdade unica partilhada entre backend e frontend.
- A transicao `triagem` -> `ativo` e o ponto unico de enforcement do bloqueio — nao duplicar a regra em multiplos sitios.

</specifics>

<deferred>
## Deferred Ideas

- Workflow processual com multiplos estados e gates por transicao -> Phase 33.
- Timeline unificada e trilha auditavel dos eventos sensiveis -> Phase 34.
- Campos minimos de intake configuraveis por tenant (em vez de fixos por tipo) -> futuro.
- Retencao/legal hold da evidencia de conflito -> Phase 35.

</deferred>
