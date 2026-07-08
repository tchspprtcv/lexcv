# Phase 85: Consolidação da Lógica de "Prazo Crítico" - Context

**Gathered:** 2026-07-08
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — smart discuss skipped, pure backend refactor with zero user-facing behavior)

<domain>
## Phase Boundary

Dashboard, agenda e (mais tarde) notificações partilham uma única fonte de verdade para decidir se um prazo ou evento está "próximo" ou "vencido", eliminando as 4 implementações inconsistentes hoje espalhadas pelo backend. Um novo serviço injetável `RiscoPrazoService` substitui `ResourceController.computeRisco()` e as 3 implementações ad-hoc distintas baseadas em `Evento` (dashboard KPI, `/eventos/upcoming`, página de agenda no frontend). Zero mudança visível ao utilizador nesta fase — puro refactor de backend, consumido apenas internamente até a Phase 88 (job diário) e Phase 89 (notificações) existirem.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Todas as escolhas de implementação ficam ao critério do Claude — fase de infraestrutura pura, sem áreas cinzentas de UX/comportamento a decidir. Usar o objetivo da fase, os critérios de sucesso do ROADMAP, e as convenções do código para orientar decisões:
- `RiscoPrazoService` deve ser um `@Service` injetável (não um utilitário estático) — sem precedente de utilitário estático neste código-base, e métodos estáticos não são mockáveis com a stack de testes atual (achado da pesquisa de arquitetura).
- Mover `computeRisco(LocalDate dataLimite, String prioridade)` verbatim de `ResourceController` para o novo serviço, preservando a assinatura e o comportamento exato (vencido / próximo / ok), para garantir zero regressão nos pontos de consumo existentes.
- Adicionar um método análogo para `Evento` (ex.: `computeRiscoEvento`) que reutilize a mesma tabela de limiares (7 dias se prioridade ALTA, 3 dias caso contrário) em vez das 3 janelas fixas/inconsistentes usadas hoje em `Evento.dataFim` (dashboard KPI) e `Evento.dataInicio` (`/eventos/upcoming`).
- Repontar todos os consumidores existentes (dashboard KPI, listagem/criação/conclusão de prazos, listagem de processos enriquecida, `/eventos/upcoming`) para o serviço novo, apagando `computeRisco()` do `ResourceController` por completo — não deixar as duas versões coexistirem.
- Não alterar a página de agenda no frontend nesta fase (a unificação client-side de `Evento`+`Prazo` fica como está) — o objetivo desta fase é só a fonte de verdade no backend; consumo pelo frontend é decisão de fases futuras.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ResourceController.computeRisco(LocalDate dataLimite, String prioridade)` — implementação atual a mover verbatim (linha ~1397-1404 conforme exploração de código).
- Padrão de `@Service` injetável já estabelecido no projeto (ex.: services existentes em `backend/src/main/java/com/lexcv/services/`).

### Established Patterns
- `Prazo` entity já usa `computeRisco()` para os endpoints `/processos/{id}/prazos` e `/prazos`.
- `Evento` é avaliado hoje em 3 locais distintos e inconsistentes: dashboard KPI (`prazos_criticos_count`, sobre `Evento.dataFim`, janela fixa de 7 dias, ignora `prioridade`), `/eventos/upcoming` (sobre `Evento.dataInicio`, janela configurável, ignora `prioridade`), e a página de agenda no frontend (unifica `Evento`+`Prazo` no cliente, conta `prioridade === "ALTA"` na janela de 7 dias, ignorando o `risco`/`escalonado` calculado no backend).
- Nenhuma das 3 implementações de `Evento` usa a tabela de limiares de `computeRisco()` (7d ALTA / 3d outros) — cada uma tem a sua própria janela fixa.

### Integration Points
- Dashboard KPI (`ResourceController.java`, bloco `prazos_criticos_count`)
- `/eventos/upcoming` (`ResourceController.getUpcomingEventos`)
- Listagem de processos enriquecida (`risco_mais_critico`/`tem_prazo_escalonado`)
- Endpoints de `Prazo` (`/processos/{id}/prazos`, `/prazos`)
- Consumido por fases futuras: Phase 88 (job diário) e Phase 89 (notificações) dependem deste serviço já existir e ser estável.

</code_context>

<specifics>
## Specific Ideas

Nenhuma — fase de infraestrutura pura, sem requisitos de UX. A pesquisa de arquitetura (`.planning/research/ARCHITECTURE.md`) já especifica a extração exata a fazer; seguir essa recomendação diretamente.

</specifics>

<deferred>
## Deferred Ideas

- Atualizar a página de agenda no frontend para consumir a lógica consolidada (hoje unifica `Evento`+`Prazo` no cliente com a sua própria regra) — fora do âmbito desta fase; considerar em milestone futura se a inconsistência entre o veredito do frontend e o backend consolidado se tornar visível ao utilizador.

</deferred>
