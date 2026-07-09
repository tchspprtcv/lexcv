# Phase 88: Verificação Diária de Prazos e Honorários - Context

**Gathered:** 2026-07-09
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — no UI, all 4 success criteria are system-behavior, not user-facing UX). This is the highest-risk phase in the milestone per `.planning/research/PITFALLS.md` (concentrates 5 of 11 critical pitfalls) and `.planning/research/ARCHITECTURE.md` (first `@Scheduled`/cross-tenant background job in this codebase) — CONTEXT.md is unusually prescriptive here to close every open design question before planning starts, rather than leave them as "Claude's Discretion."

<domain>
## Phase Boundary

Um job agendado diário deteta transições de risco em prazos de processos, eventos de calendário crítico e honorários sem pagamento total, notificando o responsável apenas quando o estado efetivamente muda — nunca repetidamente a cada execução para um item já notificado nesse estado. Depende de `RiscoPrazoService` (Phase 85, já existe) e `NotificacaoService.criar(...)` (Phase 86, já existe). É a primeira execução de código em background/cross-tenant deste código-base — nenhum ponto de entrada existente (HTTP request) serve de modelo direto para "iterar todos os tenants sem sessão."

</domain>

<decisions>
## Implementation Decisions

### Agendamento (`@Scheduled`)
- Novo pacote `backend/src/main/java/com/lexcv/jobs/` (primeiro do seu tipo) — não colocar num controller nem serviço existente.
- `@EnableScheduling` numa nova classe de configuração dedicada (ex.: `SchedulingConfig`), não anexado a `BackendApplication` diretamente.
- Cron: `0 0 6 * * *` com `zone = "Atlantic/Cape_Verde"` explícito — **obrigatório**, não confiar no fuso horário do container (research confirmou que o container provavelmente corre em UTC, Cabo Verde é UTC-1; sem `zone` explícito o job dispara à hora errada). 06:00 escolhido para que as notificações já existam quando os utilizadores começam o dia de trabalho.
- Usar `cron`, nunca `fixedRate`/`fixedDelay` — research confirmou que `fixedRate`/`fixedDelay` medem a partir do arranque da aplicação, não de um horário fixo, e este projeto reinicia o container em cada deploy (`docker-compose.prod.yml`, `restart: unless-stopped`), o que faria o job disparar a cada redeploy em vez de uma vez por dia.

### Iteração cross-tenant (sem `SecurityContextHolder`)
- O job NUNCA chama `getTenantId()` nem qualquer método que dependa de `SecurityContextHolder` — essa thread não tem sessão/JWT, e reutilizar esse padrão causa `NullPointerException` imediato.
- Iterar `tenantRepository.findAll()` (ou equivalente) explicitamente; passar `tenantId` como parâmetro explícito em todas as chamadas subsequentes — nunca `ThreadLocal` nem um principal sintético.
- Cada tenant tem o seu próprio bloco `try/catch` — uma exceção não tratada num tenant não pode impedir a verificação dos restantes tenants nem das execuções futuras do job (research confirmou, via issue tracker do Spring Framework, que uma exceção não tratada num método `@Scheduled` pode cancelar silenciosamente todas as execuções futuras desse método, não só a atual).
- Dentro de cada tenant, cada entidade (prazo/evento/honorário) individual também tem isolamento de falha — uma entidade com dados inconsistentes não deve impedir a verificação das restantes entidades do mesmo tenant.

### Idempotência e re-notificação (edge-triggered, não level-triggered)
- **Decisão de design central**: `Notificacao` (Phase 86) não tem nenhum campo dedicado a "nível de risco" — usar valores distintos de `categoria` para cada nível, em vez de adicionar uma coluna nova:
  - Prazo de processo: `categoria = "PRAZO_PROXIMO"` e `categoria = "PRAZO_VENCIDO"` (dois valores distintos, não um "PRAZO_CRITICO" genérico)
  - Evento de calendário: `categoria = "EVENTO_PROXIMO"` e `categoria = "EVENTO_VENCIDO"`
  - Honorário: `categoria = "HONORARIO_ATRASADO"` (um único nível — ver secção Honorários abaixo)
- **Mecanismo de idempotência**: antes de criar uma notificação para uma entidade que atingiu um nível de risco, o job verifica se já existe uma `Notificacao` para exatamente `(tenantId, destinatarioId, entidadeTipo, entidadeId, categoria)` — se existir, não cria nova notificação (já foi notificado para este nível exato); se não existir, cria (é a primeira vez que esta entidade cruza para este nível, para este destinatário).
- Isto satisfaz os dois requisitos do critério de sucesso 2 automaticamente: (a) correr o job duas vezes sobre os mesmos dados não duplica (a consulta de existência encontra a notificação da primeira corrida), e (b) uma mudança real de estado (`próximo`→`vencido`) gera exatamente uma notificação nova (a consulta por `categoria = "PRAZO_VENCIDO"` não encontra nada, mesmo que já exista uma para `"PRAZO_PROXIMO"`).
- Um prazo que volta de `vencido` para `ok` (ex.: prazo estendido) não gera nenhuma notificação de "voltou ao normal" — fora de âmbito, não pedido pelos requisitos.
- `RiscoPrazoService` (Phase 85) devolve `"ok"|"proximo"|"vencido"` — o job só cria notificação quando o valor é `"proximo"` ou `"vencido"`, nunca para `"ok"`.

### Honorários (lógica própria, fora do `RiscoPrazoService`)
- Critério de sucesso 4 é explícito: `RiscoPrazoService` só cobre prazos e eventos — honorários usam a sua própria lógica simples dentro do job (dias sem pagamento desde `dataAcordo`), não uma extensão do `RiscoPrazoService`.
- Limiar: **30 dias** sem pagamento total desde `dataAcordo` (`ChronoUnit.DAYS.between(honorario.getDataAcordo(), hoje) >= 30`). Ajustável no futuro, mas 30 é o valor de partida.
- Condição: só considerar honorários com `valorTotal != null` (honorários recém-criados na formalização do processo têm `valorTotal = null` até serem preenchidos — Phase 82 decision) **e** `totalPago < valorTotal` (ainda não pago integralmente). Um honorário com `valorTotal == null` é ignorado silenciosamente, nunca gera erro.
- Uma única categoria (`HONORARIO_ATRASADO`) — não há uma progressão de múltiplos níveis como prazo/evento, o requisito pede apenas "atinge N dias", um único cruzamento.

### Destinatário
- Todos os 3 tipos de alerta notificam o `responsavelId` do `Processo` associado (+ ADMIN, via `NotificacaoService.notificarAdmins` já existente) — reaproveitar exatamente o padrão de resolução de destinatário já estabelecido na Phase 87 (`notificarFaseEntrada` etc.), não inventar um novo.
- Se `Processo.responsavelId` for `null` (processo sem responsável atribuído), notificar apenas ADMIN — mesmo padrão null-safe já usado em `NotificacaoService` desde a Phase 87.
- Honorário liga-se a `Processo` via `processoId` — o responsável a notificar é o `responsavelId` desse processo.

### Escopo do job (uma única classe, múltiplos métodos internos, não múltiplos jobs)
- Um único `@Scheduled` trigger com uma classe `AlertasDiariosJob` que internamente chama 3 métodos de verificação (prazos, eventos, honorários) — não 3 triggers `@Scheduled` independentes. Reduz o número de pontos de configuração de cron e mantém a garantia de "corre uma vez por dia" num único lugar.

### Claude's Discretion
- Nome exato dos métodos internos do job.
- Estrutura exata da query que itera prazos/eventos/honorários por tenant (batch vs. streaming) — otimizar para evitar N+1 mas sem sobre-engenharia para o volume atual do projeto.
- Formato exato do texto/título de cada notificação — frases curtas em português, consistentes com o tom já usado nas notificações da Phase 87.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `RiscoPrazoService` (Phase 85) — `computeRisco(Prazo, hoje)` e `computeRiscoEvento(Evento, hoje)`, ambos com overload de 3 args aceitando uma data injetável (construído especificamente a pensar nesta fase — permite testes determinísticos sem depender de `LocalDate.now()`).
- `NotificacaoService.criar(...)` e `NotificacaoService.notificarAdmins(...)` (Phase 86/87) — único ponto de escrita, já com validação de tenant/destinatário e fan-out de ADMIN.
- `Tenant.java` + `TenantRepository.java` já existem — usar para a iteração cross-tenant explícita.
- Padrão de notificação estabelecido na Phase 87 (`notificarFaseEntrada`, `notificarProcessoAtribuido`, etc.) para o formato de chamada a `NotificacaoService`.

### Established Patterns
- `Notificacao` (Phase 86): `id, tenantId, destinatarioId, categoria, entidadeTipo, entidadeId, titulo, mensagem, linkUrl, lida, createdAt` — sem campo de "nível de risco" dedicado, daí a decisão de usar `categoria` para distinguir níveis.
- `Honorario`: `id, processoId, valorTotal (nullable), descricao, dataAcordo, totalPago` — sem campo de data de vencimento (decisão já tomada na definição de requisitos: usar dias desde `dataAcordo`).
- Nenhum `@Scheduled`/`@EnableScheduling` existe hoje neste código-base — esta fase introduz o primeiro.
- Nenhuma infraestrutura de teste de concorrência (H2/Testcontainers) existe — testes do job devem ser JUnit puro/Mockito, não testes de integração contra Postgres real.

### Integration Points
- Job novo consome `RiscoPrazoService` (Phase 85), `NotificacaoService` (Phase 86/87), `TenantRepository`, `PrazoRepository`, `EventoRepository` (a confirmar nome exato), `HonorarioRepository`, `ProcessoRepository`.
- Nenhuma UI consome isto ainda — as notificações criadas por este job aparecem no sino/página da Phase 89.

</code_context>

<specifics>
## Specific Ideas

Nenhuma adicional além do já capturado nas Decisões acima — esta fase tem um design excecionalmente detalhado devido ao seu perfil de risco elevado.

</specifics>

<deferred>
## Deferred Ideas

- Notificar quando um prazo/evento volta a "ok" depois de estar "próximo"/"vencido" — não pedido, fora de âmbito.
- Limiar de honorário configurável por tenant/admin — 30 dias fixo por agora.
- `ShedLock` ou qualquer mecanismo de lock distribuído — desnecessário hoje (deployment de container único confirmado em `docker-compose.prod.yml`), reconsiderar apenas se a arquitetura mudar para múltiplas instâncias.

</deferred>
