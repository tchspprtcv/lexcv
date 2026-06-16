# Phase 34: Processos - Timeline e Auditoria - Context

**Gathered:** 2026-06-16
**Status:** Ready for planning

<domain>
## Phase Boundary

Unificar a leitura historica do processo numa timeline agregada e navegavel, e tornar a operacao rastreavel com uma trilha auditavel de eventos sensiveis. Cobre PRC-28 e AUD-02.

**Inclui:** endpoint backend `/processos/{id}/timeline` que agrega movimentacoes, transicoes de estado, eventos, documentos e decisao de conflict check em ordem cronologica; nova entidade `AuditLog` para registo de eventos sensiveis com autor/acao/alvo/timestamp; enriquecimento da `Movimentacao` com campo `autor_id`; superficie UI no detalhe do processo (tab "Timeline" substituindo "Movimentacoes", com filtros por tipo e periodo; tab "Auditoria" separado, acesso restrito a `processos:manage`).

**Nao inclui:** governanca documental e retencao (Phase 35), dashboards/KPI (Phase 36), notificacoes por evento auditavel (futuro), visualizacao de audit trail por entidade nao-processo (AUD-01 futuro).

</domain>

<decisions>
## Implementation Decisions

### Timeline Data Model & Aggregation
- Backend single endpoint `GET /processos/{id}/timeline` agrega todos os tipos em ordem cronologica — sorting cross-type e mais limpo server-side; consistente com o padrao de workflow (backend como fonte de verdade).
- Tipos incluidos na timeline: movimentacoes + transicoes de estado (registadas como Movimentacao pela Phase 33) + eventos + documentos + decisao de conflict check — cobre o PRC-28 na totalidade ("movimentacoes, tarefas, documentos, eventos e decisoes").
- `Movimentacao` recebe campo `autor_id` (FK User, nullable) — necessario para atribuicao no feed de timeline; transicoes de estado ja criam Movimentacoes (Phase 33), o ator precisa de ser registado.
- Tab "Movimentacoes" substituido por tab "Timeline" no detalhe do processo — a timeline agrega movimentacoes e outros tipos, tornando o tab de movimentacoes autonomo redundante.

### Audit Log Design (AUD-02)
- Nova entidade `AuditLog` (tenant_id, processo_id, acao, entidade_tipo, entidade_id, autor_id, timestamp) — separacao de responsabilidades: historico operacional (Movimentacao) vs. trilha de conformidade (AuditLog).
- Eventos sensiveis a registar: transicoes de estado + decisao de conflict check + download de documento + eliminacao de documento (apenas eventos de alto risco; "processo view" excluido para evitar flooding).
- Logging manual em `ResourceController` com `auditLogRepository.save()` nos pontos sensiveis especificos — evita perda de contexto por AOP; explicito e facil de debugar.
- RBAC para audit trail: requer `processos:manage` — dados de conformidade devem ser restritos; utilizadores com `processos:view` acedem apenas a timeline operacional.

### Timeline & Audit UI
- Visual de timeline: feed vertical com dot-and-line (ponto de cor + linha de conexao, ancorado a esquerda), icone por tipo de evento — padrao standard para gestao de casos juridicos, substitui o card list do tab Movimentacoes.
- Filtros da timeline: multiselect chips por tipo (Movimentacao / Evento / Documento / Decisao / Transicao) + seletor de intervalo de datas — cobre SC3 ("tipo de evento, periodo e criticidade"); estado controlado, sem URL params.
- Audit trail: tab "Auditoria" separado do tab "Timeline" — audiencias diferentes (conformidade vs. operacional) e acesso ja restrito por RBAC (`processos:manage`).
- Ordem dos tabs: Timeline (primeiro, mais consultado em workflow juridico), Partes, Fases; tab Movimentacoes removido.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `Movimentacao` (`backend/.../models/Movimentacao.java`) — ja tem `processo_id`, `tipo`, `descricao`, `data`, `prazo_id`; adicionar `autor_id` (FK User, nullable).
- `ResourceController` — endpoints `GET/POST /processos/{id}/movimentacoes` existentes (linhas 1306, 1316); novos endpoints de timeline e audit seguem o mesmo padrao `getTenantId()` + `@PreAuthorize`.
- `MovimentacaoRepository` — existente em `repositories/`; `auditLogRepository` a criar com padrao analogo.
- `Processo` detail page (`web/src/app/(dashboard)/processos/[id]/page.tsx`) — ja tem tab system com estado local; `type TabKey` a estender com "timeline" e "auditoria".
- Badges existentes (Phase 32/33): `Badge` com `variant` por tipo — reutilizar para iconografia e cor por tipo de evento.
- Hooks TanStack Query existentes em `use-processos.ts` — novos `useTimeline(id)` e `useAuditLog(id)` seguem o mesmo padrao `useQuery`.

### Established Patterns
- Backend Spring Boot multi-tenant (`tenant_id` em todos os queries), RBAC method-level `@PreAuthorize`.
- Frontend Next.js + TanStack Query, badges/Dialog reutilizaveis, `hasScopedPermission` espelha scopes backend.
- JPA `ddl-auto=update` cria novas colunas/tabelas (AuditLog, autor_id em Movimentacao) sem migration files.
- Controlled state no detail page para dialogs e tabs — nao usar URL params para filtros locais.

### Integration Points
- Detalhe do processo (`web/src/app/(dashboard)/processos/[id]/page.tsx`) — recebe tabs Timeline e Auditoria; `type TabKey` estendido.
- `web/src/types/processos.ts` — novos tipos `TimelineItem`, `TimelineItemType`, `AuditLogEntry`.
- `web/src/hooks/use-processos.ts` — novos hooks `useTimeline`, `useAuditLog`.
- Backend `ResourceController` — novos endpoints `GET /processos/{id}/timeline` e `GET /processos/{id}/audit` com `@PreAuthorize` correto.

</code_context>

<specifics>
## Specific Ideas

- O endpoint `/timeline` deve devolver uma lista unificada com campo `tipo` discriminador (`movimentacao` / `evento` / `documento` / `decisao` / `transicao`) e campos comuns (`id`, `timestamp`, `titulo`, `descricao?`, `autor_nome?`), mais campos especificos por tipo — permite ao frontend renderizar sem precisar de mapear ids para cada subtipo.
- O tab "Auditoria" so aparece no UI se o utilizador tem `processos:manage`; para outros, o tab e invisivel (nao apenas disabled).
- Filtros de timeline sao aplicados client-side sobre os dados ja carregados — evita re-fetch por cada mudanca de filtro.

</specifics>

<deferred>
## Deferred Ideas

- Audit trail por entidade nao-processo (clientes, documentos globais) -> AUD-01 futuro.
- Notificacoes por evento auditavel (email/in-app) -> futuro.
- Export da timeline/audit log para PDF/CSV -> PRC-26 (planeado mas nao neste milestone).
- Configuracao de retencao dos registos de auditoria -> Phase 35 (governanca documental).

</deferred>
