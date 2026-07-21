# Roadmap: LexCV

## Milestones

- ✅ **v2.0 Módulo Financeiro** — Phases 43–46 (complete 2026-06-18)
- ✅ **v2.1 Agenda Avançada** — Phases 47–49 (complete 2026-06-18)
- ✅ **v2.2 Document Storage MinIO** — Phases 50–52 (complete 2026-06-19)
- ✅ **v2.3 Responsividade App** — Phases 53–56 (complete 2026-06-21)
- ✅ **v2.4 Ficha de Cliente** — Phases 57–60 (complete 2026-06-30)
- ✅ **v2.5 Módulo de Parecer Jurídico** — Phases 61–64 (complete 2026-06-30)
- ✅ **v2.6 Módulo de Parecer Jurídico — UI** — Phases 65–69 (complete 2026-07-01)
- ✅ **v2.7 Melhoria Gestão de Clientes** — Phases 70–73.1 (complete 2026-07-02)
- ✅ **v2.8 Refatoração Ficha de Cliente** — Phases 74–79 (complete 2026-07-06)
- ✅ **v2.9 Melhoria Módulo Processos** — Phases 80–84 (complete 2026-07-08)
- ✅ **v2.10 Notificações e Alertas** — Phases 85–89 (complete 2026-07-10)
- ✅ **v2.11 Auditoria Técnica e Notificações Avançadas** — Phases 90–97 (complete 2026-07-14)
- ✅ **v2.12 Landing Page** — Phases 98–100 (complete 2026-07-15)
- ✅ **v2.13 Refactor UI/UX (shadcn/ui)** — Phases 101–110 (complete 2026-07-18)
- 📋 **v2.14 UI/UX Melhorias** — Phases 111–115 (planned)

## Phases

<details>
<summary>✅ v2.0 Módulo Financeiro (Phases 43–46) - SHIPPED 2026-06-18</summary>

### Phase 43: Data Layer + Backend Endpoints

**Goal**: O contrato de dados entre frontend e backend está correto (camelCase) e o CRUD completo de honorários e pagamentos está disponível via API
**Depends on**: Nothing (first phase of milestone)
**Requirements**: FIN-01, FIN-02, FIN-03, FIN-04, FIN-05, FIN-06
**Plans**: 2/2 — completed 2026-06-18

### Phase 44: Status + KPIs

**Goal**: A página financeiro apresenta o estado calculado de cada honorário e um resumo financeiro em cards no topo
**Depends on**: Phase 43
**Requirements**: FIN-07, FIN-08, FIN-09, FIN-10
**Plans**: 2/2 — completed 2026-06-18

### Phase 45: Filtros + Edit/Delete UI

**Goal**: O utilizador pode filtrar a lista de honorários e executar ações de edição e eliminação diretamente na UI
**Depends on**: Phase 44
**Requirements**: FIN-11, FIN-12, FIN-13, FIN-14, FIN-15, FIN-16
**Plans**: 2/2 — completed 2026-06-18

### Phase 46: CSV Export

**Goal**: O utilizador pode exportar a lista de honorários (com filtros aplicados) para um ficheiro CSV
**Depends on**: Phase 45
**Requirements**: FIN-17
**Plans**: 1/1 — completed 2026-06-18

</details>

### ✅ v2.1 Agenda Avançada (SHIPPED 2026-06-18)

**Milestone Goal:** Adicionar notificações in-app de eventos próximos, suporte a eventos recorrentes e drag & drop no calendário para mover eventos.

- [x] **Phase 47: Notificações In-App** - Badge no header com contagem de eventos próximos e painel de notificações
- [x] **Phase 48: Recorrência de Eventos** - Criar, listar, exibir e apagar eventos com regras de recorrência (completed 2026-06-18)
- [x] **Phase 49: Drag & Drop no Calendário** - Arrastar eventos para nova data com atualização imediata via API (completed 2026-06-18)

### ✅ v2.2 Document Storage MinIO (SHIPPED 2026-06-19)

**Milestone Goal:** Migrar o armazenamento de documentos do filesystem local para MinIO (object storage S3-compatible), atualizar o componente de upload no frontend e configurar o deploy no Hostinger VPS.

- [x] **Phase 50: Backend MinIO Integration** - Spring Boot integrado ao MinIO via AWS S3 SDK; upload, download pré-assinado e delete no bucket (completed 2026-06-19)
- [x] **Phase 51: Frontend Upload Component** - Barra de progresso, drag-and-drop, preview inline e download via URL pré-assinada (completed 2026-06-19)
- [x] **Phase 52: Deploy MinIO no Hostinger** - Serviço MinIO no Docker Compose prod, credenciais via env vars, CI/CD atualizado e consola via Caddy (completed 2026-06-19)

<details>
<summary>✅ v2.3 Responsividade App (Phases 53–56) — SHIPPED 2026-06-21</summary>

- [x] **Phase 53: Shell Responsivo** — Hamburger drawer, top bar compacta e bottom navigation em mobile (completed 2026-06-21)
- [x] **Phase 54: Listas e Tabelas** — Cards empilhados em listas simples e scroll horizontal em tabelas complexas (completed 2026-06-21)
- [x] **Phase 55: Formulários e Modais** — Coluna única, bottom-sheet/full-screen e touch targets 48px em mobile (completed 2026-06-21)
- [x] **Phase 56: Dashboard e Calendário** — KPI grid adaptável e vista diária por defeito no calendário em mobile (completed 2026-06-21)

See archive: [milestones/v2.3-ROADMAP.md](milestones/v2.3-ROADMAP.md)

</details>

<details>
<summary>✅ v2.4 Ficha de Cliente (Phases 57–60) — SHIPPED 2026-06-30</summary>

- [x] **Phase 57: Backend Schema + API** — Extensão da entidade Cliente com novos campos, geração de numero_cliente, endpoints atualizados (completed 2026-06-30)
- [x] **Phase 58: Formulário Dinâmico** — Formulário frontend com seletor de tipo, campos demográficos/empresa, flag avençado e exibição do número (completed 2026-06-30)
- [x] **Phase 59: Procuração + Intake** — Upload obrigatório de procuração e secção de intake (advogados, administrativos, docs, deslocações, honorários propostos) (completed 2026-06-30)
- [x] **Phase 60: Ficha Imprimível** — Vista dedicada que reproduz a ficha real do escritório com botão de impressão (completed 2026-06-30)

Auditoria de integração pós-execução encontrou um mismatch snake_case/camelCase (9/19 requisitos afectados) e uma fuga de password hash — ambos corrigidos antes do fecho. Ver archive para detalhes completos.

See archive: [milestones/v2.4-ROADMAP.md](milestones/v2.4-ROADMAP.md)

</details>

<details>
<summary>✅ v2.5 Módulo de Parecer Jurídico (Phases 61–64) - SHIPPED 2026-06-30</summary>

API backend completa para o ciclo Solicitação → Elaboração → Aprovação → Entrega, com auditoria automática e pesquisa avançada. Backend-only por decisão explícita — UI frontend fica para milestone futura (v2.6). Ver Scope Note no archive.

See archive: [milestones/v2.5-ROADMAP.md](milestones/v2.5-ROADMAP.md) · [v2.5-MILESTONE-AUDIT.md](../v2.5-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.6 Módulo de Parecer Jurídico — UI (Phases 65–69) — SHIPPED 2026-07-01</summary>

Interface frontend completa para o Módulo de Parecer Jurídico, sobre a API já entregue no v2.5: `/pareceres` lista (dual-view, badges, filtros), detalhe com timeline imutável de versões, criação de solicitação, elaboração de versões (resumo + anexo obrigatório na UI), entrega irreversível com confirmação e vista dedicada "Parecer Entregue" (fecha o gap PARC-09 do audit v2.5), pesquisa avançada, e RBAC espelhado em toda a UI (incluindo verificação de instância advogado-responsável/ADMIN). Auditoria de milestone encontrou e corrigiu um bug de routing pré-existente desde a v2.5 (`pesquisar()` inacessível em runtime — extraído para `ParecerPesquisaController`, commit 657bcbc). NOTF-05/06/07 removidas do âmbito v1 (sem sistema de notificações genérico no backend). Ver Scope Note no archive.

- [x] Phase 65: Fundação — Listagem e Detalhe (PARC-11, PARC-12) — 2/2 plans, completed 2026-07-01
- [x] Phase 66: Criação de Solicitação (PARC-13) — 1/1 plan, completed 2026-07-01
- [x] Phase 67: Elaboração e Versionamento (PARV-05, PARV-06) — 1/1 plan, completed 2026-07-01
- [x] Phase 68: Entrega, Vista de Entregue e RBAC (PARC-14, PARC-15, PARC-16) — 1/1 plan, completed 2026-07-01
- [x] Phase 69: Pesquisa Avançada (PARS-03) — 1/1 plan, completed 2026-07-01

See archive: [milestones/v2.6-ROADMAP.md](milestones/v2.6-ROADMAP.md) · [milestones/v2.6-MILESTONE-AUDIT.md](milestones/v2.6-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.7 Melhoria Gestão de Clientes (Phases 70–73.1) — SHIPPED 2026-07-02</summary>

Simplificação e aplanamento do modelo de identificação e contactos de clientes (remoção de `dados_tipo` JSON em prol de colunas diretas). Backend flat-column model + `REG_COMERCIAL` (Phase 70), tipos TypeScript e schema Zod aplanados com NIF obrigatório (Phase 71), formulários de criação/edição com labels dinâmicas Nome/Nome Comercial e Morada/Sede (Phase 72), página de detalhe e ficha imprimível atualizadas (Phase 73). Auditoria de milestone encontrou um gap no CLI-05 (NIF podia ser sobrescrito silenciosamente por um campo legado, sem validação server-side) — fechado pela Phase 73.1 (inserida), cujo próprio code review encontrou e corrigiu uma regressão adicional (validação JPA-lifecycle bloqueando saves não relacionados em registos legados). Re-auditoria confirmou 7/7 requisitos satisfeitos. Ver Scope Note no archive.

- [x] Phase 70: Backend refactoring & Seeder Alignment (CLI-06, CLI-09) — 1/1 plan, completed 2026-07-01
- [x] Phase 71: Frontend Types, Schema & API Integration (CLI-05, CLI-06) — 2/2 plans, completed 2026-07-01
- [x] Phase 72: Form Refactoring (Create & Edit) (CLI-05, CLI-07, CLI-08, CLI-09, CLI-10) — 1/1 plan, completed 2026-07-02
- [x] Phase 73: Detail Page & Printable Ficha Update (CLI-11) — 1/1 plan, completed 2026-07-02
- [x] Phase 73.1: Fechar gap CLI-05 (INSERTED, gap closure) — 1/1 plan, completed 2026-07-02

See archive: [milestones/v2.7-ROADMAP.md](milestones/v2.7-ROADMAP.md) · [milestones/v2.7-MILESTONE-AUDIT.md](milestones/v2.7-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.8 Refatoração Ficha de Cliente (Phases 74–79) — SHIPPED 2026-07-06</summary>

Ficha de cliente unificada (view/edit num só componente via toggle Editar, rota `/editar` removida — Phase 75) e reestruturada em 7 separadores estilo botões-toggle de processos (Phase 76): Dados (identificação isolada como sub-secção), Contactos e Notas, Processos/Pareceres (Phase 77, wiring de baixo risco contra hooks já existentes), Documentos a Tratar/Deslocações (Phase 78, relocalização pura), Documentos Entregues (Phase 79, upload real via novo endpoint `GET /clientes/{id}/documentos`). Enum `documento_tipo` restruturado antes de tudo (Phase 74: `BI` adicionado, `NIF` removido, restrito por tipo de cliente, com 2 rondas de gap-closure para preservar valores legados). Auditoria de fase (79) encontrou e fechou 3 bugs críticos: incompatibilidade `cliente_id`/`clienteId` que quebrava silenciosamente a associação de uploads, link de download incorreto, falta de validação de posse de tenant no upload. Auditoria de milestone: 20/20 requisitos satisfeitos, integração cross-phase totalmente ligada, sem gaps críticos.

- [x] Phase 74: Enum `documento_tipo` (BI/NIF/Restrição por Tipo) (CLI-20 a CLI-24) — 5 plans (2 gap-closure), completed 2026-07-04
- [x] Phase 75: Componente Único View/Edit (CLI-12, CLI-13, CLI-14) — 3 plans (2 waves), completed 2026-07-04
- [x] Phase 76: Separadores — Dados, Contactos e Notas (CLI-15, CLI-18, CLI-19) — 1 plan, completed 2026-07-05
- [x] Phase 77: Separadores — Processos e Pareceres (CLI-16, CLI-17) — 1 plan, completed 2026-07-05
- [x] Phase 78: Separadores — Documentos a Tratar e Deslocações (CLI-30, CLI-31) — 1 plan, completed 2026-07-06
- [x] Phase 79: Documentos Entregues — Upload Real (CLI-25 a CLI-29) — 2 plans (2 waves), completed 2026-07-06

See archive: [milestones/v2.8-ROADMAP.md](milestones/v2.8-ROADMAP.md) · [milestones/v2.8-MILESTONE-AUDIT.md](milestones/v2.8-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.9 Melhoria Módulo Processos (Phases 80–84) — SHIPPED 2026-07-08</summary>

Módulo de processos aprofundado com dados jurídicos estruturados: `Processo.juizo`/`origem` (Phase 80 fundação de dados), CRUD completo e seguro de Decisões/Factos/Testemunhas com dupla verificação de posse tenant+processoId (Phase 81, 12 endpoints), criação automática e idempotente de Honorário na formalização com `valorTotal` sempre em branco (Phase 82, isolada por concentrar o risco financeiro), tipos/schemas/hooks frontend com prova executável de round-trip fechando a 4ª recorrência do bug de mapeamento camelCase/snake_case (Phase 83), e UI completa — intake com Origem obrigatória, Juízo editável, 4 abas novas (Decisões/Factos/Testemunhas/Documentos), Termo de Honorários imprimível com bloqueio quando o valor está em branco, e Partes/Fases refatoradas para o mesmo padrão visual (Phase 84, extensão de âmbito pedida pelo utilizador). Auditoria de milestone encontrou e fechou na mesma sessão 2 bugs de integração cross-phase: `GET /honorarios?processo_id=X` e `GET /documentos?processo_id=X` ignoravam o filtro no backend, devolvendo dados de todo o tenant.

- [x] Phase 80: Fundações — Processo.juizo/origem + Entidades Decisão/Facto/Testemunha (PROC-01, PROC-06, PROC-09, PROC-11) — 1/1 plan, completed 2026-07-07
- [x] Phase 81: Backend — CRUD Decisões/Factos/Testemunhas + Wiring Juízo/Origem (PROC-02 a PROC-05, PROC-07, PROC-08, PROC-10, PROC-12, PROC-17) — 3/3 plans, completed 2026-07-07
- [x] Phase 82: Backend — Criação Automática de Honorário na Formalização (PROC-14) — 1/1 plan, completed 2026-07-07
- [x] Phase 83: Frontend — Tipos, Schemas e Hooks (camada de integração, sem requisito dedicado) — 2/2 plans, completed 2026-07-07
- [x] Phase 84: Frontend — UI (Intake, Dados, Sub-secções, Documentos, Termo de Honorários) (PROC-13, PROC-15, PROC-16) — 5/5 plans, completed 2026-07-08

See archive: [milestones/v2.9-ROADMAP.md](milestones/v2.9-ROADMAP.md) · [milestones/v2.9-MILESTONE-AUDIT.md](milestones/v2.9-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.10 Notificações e Alertas (Phases 85–89) - SHIPPED 2026-07-10</summary>

**Milestone Goal:** Substituir o sino puramente calculado (que só lê eventos de agenda) por um sistema de notificações persistido no backend, orientado por perfil/permissão, cobrindo prazos de processos, entrada de fases, documentos, atribuições e pareceres.

A pesquisa de arquitetura desta milestone identificou duas fundações obrigatórias e mutuamente paralelizáveis, das quais todo o resto depende: a consolidação da lógica de "prazo crítico" (Phase 85) e o esqueleto de entidade/API de notificações (Phase 86). Os 4 alertas disparados por evento (Phase 87) só precisam da Phase 86 para existir. O job diário (Phase 88) é o único ponto que depende estritamente de ambas as fundações, porque a sua finalidade explícita é reutilizar a lógica de risco já consolidada em vez de introduzir uma 5ª implementação divergente. A reatribuição de responsável (NOTF-17) é construída na Phase 87 junto do seu próprio alerta (NOTF-18) e da respetiva interface — nunca separada — porque o alerta não faz sentido sem o fluxo dedicado que o desencadeia. O sino e a página `/notificacoes` (Phase 89) fecham a milestone consumindo a API já pronta desde a Phase 86.

#### Phase 85: Consolidação da Lógica de "Prazo Crítico"

**Goal**: Dashboard, agenda e (mais tarde) notificações partilham uma única fonte de verdade para decidir se um prazo ou evento está "próximo" ou "vencido", eliminando as 4 implementações inconsistentes hoje espalhadas pelo backend.
**Depends on**: Nothing (primeira fase da milestone; paralelizável com Phase 86)
**Requirements**: NOTF-22
**Success Criteria** (what must be TRUE):
  1. Um novo serviço injetável `RiscoPrazoService` calcula o risco (ok/próximo/vencido) para `Prazo` e, com um método análogo, para `Evento` — substituindo por completo o método privado `computeRisco()` e as 3 implementações ad-hoc distintas hoje baseadas em `Evento` (dashboard, `/processos/dashboard`, `/eventos/upcoming`)
  2. Todos os pontos de consumo já existentes (dashboard KPI, listagem/criação/conclusão de prazos, listagem de processos enriquecida) continuam a devolver exatamente os mesmos resultados de antes da refatoração, para os mesmos dados — zero regressão observável
  3. `Evento` passa a ser avaliado pela mesma tabela de limiares (7 dias se prioridade ALTA, 3 dias caso contrário) já usada por `Prazo`, em vez das 3 janelas fixas e inconsistentes usadas hoje
**Plans**: 1 plan
- [x] 85-01-PLAN.md — Extrair RiscoPrazoService (@Service injetável) + repontar os 8 call sites de Prazo e os 3 blocos de Evento em ResourceController, apagando o método privado computeRisco

#### Phase 86: Infraestrutura de Notificações — Entidade, API e Targeting

**Goal**: Existe uma API de notificações persistidas, funcional e segura — cada notificação é dirigida apenas à entidade diretamente ligada mais ADMIN, nunca em massa por permissão de visualização, com estado lido/não-lido isolado por destinatário.
**Depends on**: Nothing (primeira fase da milestone; paralelizável com Phase 85)
**Requirements**: NOTF-14
**Success Criteria** (what must be TRUE):
  1. Existe uma tabela `t_notificacao` persistida (com migração manual documentada em `backend/migrations/`) e os endpoints `GET /notificacoes` (com filtros por categoria/estado lida e paginação), `GET /notificacoes/unread-count`, `PATCH /notificacoes/{id}/lida` e `POST /notificacoes/ler-todas` funcionam, todos scoped por tenant E por destinatário
  2. Dois utilizadores de teste no mesmo tenant recebem listas de notificações independentes entre si; marcar uma notificação como lida por um utilizador nunca afeta o estado da mesma notificação para outro destinatário
  3. Uma notificação dirigida a "ADMIN" gera uma linha própria por cada ADMIN atual do tenant no momento da criação (fan-out), cada uma com o seu próprio estado de leitura independente
  4. O novo scope `notificacoes:view` está seedado para todos os perfis (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) tanto no backend (`DatabaseSeeder`) como no frontend (`KNOWN_SCOPES`)
**Plans**: 3 plans (3 waves)
- [x] 86-01-PLAN.md — Camada de dados: entidade `Notificacao` (uma linha por destinatário) + `NotificacaoRepository` dual-scoped (tenant + destinatário) + migração manual `t_notificacao` com índice composto
- [x] 86-02-PLAN.md — `NotificacaoService` (choke point único `criar` + fan-out ADMIN) + teste Mockito a provar isolamento entre 2 destinatários e fan-out por ADMIN (Critérios de Sucesso 2 e 3)
- [x] 86-03-PLAN.md — `NotificacaoController` (4 endpoints dual-scoped: listar com filtros/paginação, unread-count, marcar lida, ler-todas) + seed do scope `notificacoes:view` para os 4 perfis (backend + frontend `KNOWN_SCOPES`)

#### Phase 87: Alertas de Eventos — Fase, Documento, Atribuição e Parecer

**Goal**: O sistema notifica automaticamente o destinatário certo sempre que um processo muda de fase, um novo documento é adicionado, um processo é atribuído/reatribuído através de um novo formulário dedicado, ou um parecer é atribuído a um advogado.
**Depends on**: Phase 86
**Requirements**: NOTF-15, NOTF-16, NOTF-17, NOTF-18, NOTF-19
**Success Criteria** (what must be TRUE):
  1. Quando um processo entra numa nova fase, o responsável do processo (e ADMIN) recebe uma notificação com link direto para o processo
  2. Quando um novo documento é adicionado a um processo (ou a um cliente sem processo associado), o destinatário correto — responsável do processo, ou equipa de advogados/administrativos do cliente quando não há processo — recebe uma notificação (mais ADMIN)
  3. Utilizador com permissão adequada reatribui o responsável de um processo através de um novo formulário/interface dedicado na ficha do processo (o backend valida que o novo responsável pertence ao tenant, tal como já acontece na criação); o novo responsável (e ADMIN) recebe de imediato uma notificação de atribuição
  4. Advogado atribuído a um parecer — na criação ou numa reatribuição posterior — recebe uma notificação de atribuição (mais ADMIN)
**Plans**: 4 plans (3 waves)
- [x] 87-01-PLAN.md — NotificacaoService: 4 métodos notificar* + overload notificarAdmins com actor-exclusion (fundação; testes Mockito)
- [x] 87-02-PLAN.md — ResourceController: gatilhos FASE_ENTRADA/DOCUMENTO_NOVO/PROCESSO_ATRIBUIDO + novo endpoint PUT /processos/{id}/atribuir (manage-gated)
- [x] 87-03-PLAN.md — ParecerController: gatilhos PARECER_ATRIBUIDO na criação e na reatribuição
- [x] 87-04-PLAN.md — Frontend: hook useReatribuirResponsavel + controlo Reatribuir (Dialog→AlertDialog) na ficha do processo
**UI hint**: yes

#### Phase 88: Verificação Diária de Prazos e Honorários

**Goal**: Um job agendado diário deteta transições de risco em prazos de processos, eventos de calendário crítico e honorários sem pagamento total, notificando o responsável apenas quando o estado efetivamente muda — nunca repetidamente a cada execução para um item já notificado nesse estado.
**Depends on**: Phase 85, Phase 86
**Requirements**: NOTF-20, NOTF-21, NOTF-23
**Success Criteria** (what must be TRUE):
  1. Um job `@Scheduled` (cron diário, fuso `Atlantic/Cape_Verde`) corre uma vez por dia, itera todos os tenants de forma explícita (sem depender de sessão/JWT), e uma exceção não tratada num tenant ou entidade não impede a execução dos restantes tenants nem das execuções futuras do job
  2. Quando um prazo de processo ou um evento de calendário crítico muda de estado de risco (ok→próximo ou próximo→vencido) desde a última verificação, o responsável do processo (e ADMIN) recebe exatamente uma notificação nova; correr o job duas vezes seguidas sobre os mesmos dados não gera notificações duplicadas
  3. Quando o honorário de um processo atinge N dias sem pagamento total desde `dataAcordo` (e apenas quando `valorTotal` já foi preenchido), o responsável do processo (e ADMIN) recebe uma notificação; honorários com `valorTotal` ainda `null` são ignorados sem gerar erro
  4. A verificação usa exclusivamente o `RiscoPrazoService` da Phase 85 para prazos e eventos — nenhuma cópia adicional da lógica de limiares é introduzida no job
**Plans**: 2 plans (2 waves)
- [x] 88-01-PLAN.md — Contratos: método existence-check em NotificacaoRepository (backbone da idempotência) + findByProcessoIdIn em HonorarioRepository (batch, anti-N+1) + SchedulingConfig (@EnableScheduling isolado)
- [x] 88-02-PLAN.md — AlertasDiariosJob (@Scheduled cron diário, fuso Atlantic/Cape_Verde): varre todos os tenants sem SecurityContext, alerta prazos/eventos via RiscoPrazoService e honorários >=30 dias, idempotente por nível (edge-triggered) com isolamento de falhas por tenant/entidade — TDD (teste Mockito primeiro)

#### Phase 89: Sino e Página de Notificações

**Goal**: O utilizador consegue ver, consultar e gerir as suas notificações diretamente na interface da aplicação — contador no sino, lista rápida com atalhos, histórico completo com filtros, e marcação de leitura individual ou em massa.
**Depends on**: Phase 86 (beneficia de Phase 87/88 existirem para uma demonstração end-to-end realista, mas os endpoints de listagem/marcar-lida já são testáveis contra dados semeados manualmente assim que a Phase 86 estiver pronta)
**Requirements**: NOTF-08, NOTF-09, NOTF-10, NOTF-11, NOTF-12, NOTF-13
**Success Criteria** (what must be TRUE):
  1. O sino no topo da aplicação mostra um contador de notificações não lidas, atualizado automaticamente por polling (30-60s), incluindo uma atualização imediata ao voltar a focar a aba do browser
  2. Ao abrir o sino, o utilizador vê uma lista das notificações recentes, cada uma com link direto para a entidade relacionada (processo, documento, parecer ou honorário, conforme a categoria)
  3. O utilizador marca uma notificação individual como lida, ou todas de uma vez, e o contador do sino atualiza-se de imediato, sem esperar pelo próximo ciclo de polling
  4. O utilizador acede a `/notificacoes` e consulta o histórico completo, filtrável por categoria e por estado lida/não-lida
**Plans**: 4 plans (3 waves)
- [x] 89-01-PLAN.md — Camada de dados: tipos Notificacao + mapa categoria->label/variant + 4 hooks (polling opt-in, invalidação de prefixo ["notificacoes"])
- [x] 89-02-PLAN.md — Sino reescrito: badge com polling 30s+refocus, dropdown de 10, clique=marcar-lida+navegar, marcar-todas, footer "Ver todas as notificações"
- [x] 89-03-PLAN.md — Página /notificacoes: filtros (categoria + chips 3-vias lida/não-lida), linhas com marcar-uma/marcar-todas, paginação funcional, RBAC gate
- [x] 89-04-PLAN.md — Checkpoint humano end-to-end (contador+polling+refocus, clique fundido, filtros, paginação, invalidação cross-surface)
**UI hint**: yes

See archive: [milestones/v2.10-ROADMAP.md](milestones/v2.10-ROADMAP.md) · [milestones/v2.10-MILESTONE-AUDIT.md](milestones/v2.10-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.11 Auditoria Técnica e Notificações Avançadas (Phases 90–97) — SHIPPED 2026-07-14</summary>

Fechou a dívida técnica acumulada do projeto (SAST contra JDK 23, primeira infraestrutura de testes de integração Testcontainers+PostgreSQL, consolidação da Agenda com `RiscoPrazoService`, UAT ao vivo pendente de 8 fases históricas) e expandiu o sistema de notificações com preferências por utilizador, alcance de equipa e snooze. A cadeia de notificações (Phases 93–96) foi sequencial por colidir mecanicamente em `NotificacaoService.java`: NOTF-24 (mute) primeiro para que o alargamento de destinatários da NOTF-25 herdasse o gate de silenciamento; NOTF-27 (dedup ADMIN) antes da NOTF-25 porque o alargamento de destinatários tornaria esse bug pré-existente quase certo; NOTF-26 (snooze) por último, a mais aditiva. A auditoria de milestone (Phase 97) resolveu genuinamente o bloqueio ambiental `MINIO_ENDPOINT` pela primeira vez neste projeto (não apenas documentou um workaround), fechou UAT ao vivo para ~40 cenários das 8 fases cobertas, e encontrou+corrigiu na própria sessão 1 gap de integração residual (`isEventoCritico` divergindo em `dataInicio`/`dataFim` — a última implementação de "prazo crítico" ainda inconsistente, deferida pela Phase 92 e nunca apanhada pelo âmbito da Phase 97 até o integration-checker do fecho de milestone). 15/15 requisitos satisfeitos.

- [x] Phase 90: SpotBugs/SAST — Commit e Verificação (SAST-01) — 1/1 plan, completed 2026-07-13
- [x] Phase 91: Infraestrutura de Testes de Integração (Testcontainers) (TEST-01, TEST-02, TEST-03) — 3/3 plans, completed 2026-07-13
- [x] Phase 92: Agenda ↔ RiscoPrazoService — Consolidação (AGD-34, AGD-35) — 2/2 plans, completed 2026-07-13
- [x] Phase 93: NOTF-24 — Preferências de Notificação por Utilizador (NOTF-24) — 4/4 plans, completed 2026-07-14
- [x] Phase 94: NOTF-27 — Corrigir Colisão de Dedup ADMIN (NOTF-27) — 1/1 plan, completed 2026-07-14
- [x] Phase 95: NOTF-25 — Notificar Toda a Equipa do Processo (NOTF-25) — 2/2 plans, completed 2026-07-14
- [x] Phase 96: NOTF-26 — Snooze de Lembrete de Prazo (NOTF-26) — 4/4 plans, completed 2026-07-14
- [x] Phase 97: Auditoria de Milestone — Dívida Técnica e UAT Pendente (AUD-01, AUD-02, AUD-03, AUD-04, AUD-05) — 4/4 plans, completed 2026-07-14

See archive: [milestones/v2.11-ROADMAP.md](milestones/v2.11-ROADMAP.md) · [milestones/v2.11-MILESTONE-AUDIT.md](milestones/v2.11-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.12 Landing Page (Phases 98–100) — SHIPPED 2026-07-15</summary>

Primeira rota pública real da aplicação. Duas fases mutuamente paralelizáveis e sem dependência entre si (Phase 98 backend + Phase 99 `webpage/`), seguidas por uma fase de routing/deployment (Phase 100) que tocou deliberadamente por último a área (`Caddyfile`, `Caddyfile.prod`, heredoc em `docker-compose.hostinger.yml`) responsável por 4 commits de correção de bugs em produção na mesma sessão. Verificação ao vivo via `docker compose up` (não apenas checks isolados) encontrou e corrigiu 3 bugs reais: um fetch com URL relativo que desativaria silenciosamente o gate `/setup` em todos os ambientes; uma anotação `@Transactional` em falta que crashava a leitura do logo via Hibernate/PostgreSQL; e um bug pré-existente de falta de passthrough de env vars para o Caddy em produção. 16/16 requisitos satisfeitos.

- [x] Phase 98: Backend — Endpoint Público de Branding (LP-01, LP-02) — 1/1 plan, completed 2026-07-15
- [x] Phase 99: webpage/ — Nova App Next.js de Landing (LP-03 a LP-12) — 4/4 plans, completed 2026-07-15
- [x] Phase 100: Infraestrutura — Routing e Deployment (LP-13 a LP-16) — 4/4 plans, completed 2026-07-15

See archive: [milestones/v2.12-ROADMAP.md](milestones/v2.12-ROADMAP.md) · [milestones/v2.12-MILESTONE-AUDIT.md](milestones/v2.12-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.13 Refactor UI/UX (shadcn/ui) (Phases 101–110) — SHIPPED 2026-07-18</summary>

Retrofit sobre 15 primitivos hand-rolled (Radix por baixo, nunca passados pela CLI shadcn) acumulados desde a v1.1, cobrindo tanto a app interna `web/` como a landing pública `webpage/`. Fundação (101) e Reconciliação (102) terminaram ambas antes de qualquer fase de módulo, evitando o estado "app visivelmente meio-migrada"; o padrão DataTable (104) foi construído uma única vez e reutilizado por 5 listas; Clientes+Processos (105) foram entregues em conjunto para nunca deixar as duas fichas inconsistentes entre si; as restantes fases de módulo (106–109) e o refinamento da `webpage/` (110) correram sem dependência mútua real. 33/33 requisitos satisfeitos, 10/10 fases com pipeline completo (revisão de código + verificação goal-backward + auditoria de UI de 6 pilares) fechado. A auditoria de milestone (cross-phase integration) encontrou e corrigiu diretamente 1 gap real (guard RBAC obsoleto em `notificacoes/page.tsx`) e sinalizou 1 gap de âmbito estreito como dívida técnica não-bloqueante (uma 4ª barra de progresso de upload em Pareceres, fora do âmbito declarado tanto da Phase 107 como da 108).

- [x] Phase 101: Fundação — CLI Init e Design Tokens (FND-01 a FND-08) — 5/5 plans, completed 2026-07-15
- [x] Phase 102: Reconciliação do Design System (DSR-01 a DSR-03) — 4/4 plans, completed 2026-07-16
- [x] Phase 103: Módulo Dashboard (DASH-01, DASH-02) — 1/1 plan, completed 2026-07-16
- [x] Phase 104: Padrão DataTable Partilhado (DTB-01 a DTB-03) — 6/6 plans, completed 2026-07-16
- [x] Phase 105: Módulos Clientes + Processos (CLP-01 a CLP-05) — 6/6 plans, completed 2026-07-16
- [x] Phase 106: Módulo Agenda (AGD-36, AGD-37) — 4/4 plans, completed 2026-07-16
- [x] Phase 107: Módulos Documentos + Financeiro (DOF-01, DOF-02) — 6/6 plans, completed 2026-07-17
- [x] Phase 108: Módulo Pareceres (PARC-18 a PARC-20) — 4/4 plans, completed 2026-07-17
- [x] Phase 109: Notificações / Settings / Setup Wizard (NTF-28 a NTF-30) — 3/3 plans, completed 2026-07-17
- [x] Phase 110: Refinamento da Landing webpage/ (LDG-17, LDG-18) — 3/3 plans, completed 2026-07-18

See archive: [milestones/v2.13-ROADMAP.md](milestones/v2.13-ROADMAP.md) · [milestones/v2.13-REQUIREMENTS.md](milestones/v2.13-REQUIREMENTS.md) · [milestones/v2.13-MILESTONE-AUDIT.md](milestones/v2.13-MILESTONE-AUDIT.md)

</details>

### 📋 v2.14 UI/UX Melhorias (Planned)

**Milestone Goal:** Fechar gaps de UX deixados pela v2.13 (pesquisa decorativa, sem filtro de estado) e dar o próximo passo na linguagem visual (ícones, cantos arredondados) — uma segunda fase de refinamento orientada a comportamento, não só a troca de biblioteca de componentes.

A pesquisa de milestone (alvo #1, confiança HIGH) confirma que a pesquisa global cross-entity já tem toda a base técnica instalada (primitivos `cmdk`/`CommandDialog` desde a Phase 107, dois precedentes de pesquisa `ILIKE` tenant-scoped já no backend) — falta só ligar as peças. O backend (Phase 111) tem de existir e estar testado (isolamento de tenant, RBAC por branch de entidade, ranking exato/prefixo antes de substring) antes do frontend (Phase 112) o consumir — a mesma sequência já usada neste projeto para Pareceres (v2.5→v2.6) e Notificações (Phase 86→89), e evita construir UI contra um contrato de dados ainda instável. O filtro de estado de Processos (Phase 113) é aditivo e tecnicamente independente da pesquisa (módulo e ficheiros distintos), mas agrupado tematicamente com as Phases 111-112 por ambos fecharem os mesmos gaps de UX identificados no fecho da v2.13. As duas fases de linguagem visual fecham a milestone: cantos arredondados (Phase 114, mudança de um único token CSS já isolado em `web/src/app/globals.css`) corre antes do levantamento de ícones (Phase 115) para que a verificação visual não tenha de rever os mesmos ecrãs duas vezes, e para que o levantamento de ícones já cubra os botões novos introduzidos pelas Phases 111-114 (trigger de pesquisa, filtro de estado). Targets #2-#5 não tiveram research dedicado (fora do âmbito desta ronda de pesquisa) — as Phases 113-115 aplicam padrões já estabelecidos no código (Select de filtros da Phase 106, `lucide-react`/`Tooltip` já instalados) com risco avaliado como baixo.

- [x] **Phase 111: Backend — Pesquisa Global Cross-Entity (API)** - Endpoint novo, seguro e bem ordenado (Cliente/Processo/Documento/Parecer, tenant+RBAC por branch) (completed 2026-07-21)
- [x] **Phase 112: Frontend — Pesquisa Global (Paleta de Comando)** - Paleta Ctrl+K/⌘K no topbar, ligada ao endpoint da Phase 111 (completed 2026-07-21)
- [x] **Phase 113: Processos — Filtro por Estado** - Controlo dedicado para filtrar a lista de Processos por estado (completed 2026-07-21)
- [ ] **Phase 114: Linguagem Visual — Cantos Arredondados (`--radius`)** - Reversão deliberada do radius reto da v2.13
- [ ] **Phase 115: Linguagem Visual — Ícones + Filtros Ícone-Only** - Ícones em todos os botões; filtros aplicar/limpar/exportar passam a ícone-only com tooltip

#### Phase 111: Backend — Pesquisa Global Cross-Entity (API)

**Goal**: Existe um endpoint backend que devolve, de forma segura e corretamente ordenada, resultados de Clientes, Processos, Documentos e Pareceres do tenant do utilizador autenticado — a fundação sobre a qual a experiência de pesquisa do utilizador (Phase 112) é construída.
**Depends on**: Nothing (primeira fase da milestone)
**Requirements**: SRCH-01, SRCH-02, SRCH-06, SRCH-07
**Success Criteria** (what must be TRUE):
  1. Uma chamada autenticada a `GET /api/v1/pesquisa?q=<termo>` devolve resultados de Cliente, Processo, Documento e ParecerSolicitacao pertencentes ao tenant do utilizador, cada resultado identificável pelo seu tipo (SRCH-01)
  2. Dois tenants com registos e termos de pesquisa coincidentes nunca veem resultados um do outro em nenhum dos 4 tipos — cada sub-query parte do `tenant_id` da própria entidade, nunca de um filtro aplicado depois de obter os dados (SRCH-07)
  3. Correspondências exatas ou por prefixo em identificadores estruturados (`numero_cliente`, `numero_processo`, `nif`, `documento_numero`) aparecem ordenadas antes de correspondências por substring simples, no mesmo conjunto de resultados (SRCH-02)
  4. Um tipo de entidade só é consultado quando o utilizador autenticado detém o scope de visualização correspondente (`clientes:view`/`processos:view`/`documentos:view`/`pareceres:view`) — nunca obtido e escondido depois; dados de `Honorario`/financeiro nunca surgem em nenhum resultado, independentemente do perfil do utilizador (SRCH-06)
**Plans**: 2 plans (2 waves)
- [x] 111-01-PLAN.md — Data layer: 4 queries nativas pesquisarGlobal (tenant-first, unaccent+ILIKE, ranking exato/prefixo>substring, LIMIT 5) + migração unaccent/pg_trgm + Testcontainers IT de isolamento cross-tenant e ranking
- [x] 111-02-PLAN.md — API: ResultadoPesquisaDto + PesquisaController (GET /api/v1/pesquisa, isAuthenticated, RBAC por ramo, sem ramo Honorario, validação de q) + teste de matriz RBAC

#### Phase 112: Frontend — Pesquisa Global (Paleta de Comando)

**Goal**: O utilizador encontra e navega para qualquer Cliente/Processo/Documento/Parecer do seu tenant a partir de qualquer página, através de uma paleta de pesquisa acessível pelo topbar ou por atalho de teclado.
**Depends on**: Phase 111 (o contrato do backend tem de estar estável e testado antes da UI o consumir)
**Requirements**: SRCH-03, SRCH-04, SRCH-05, SRCH-08, SRCH-09, SRCH-10, SRCH-11
**Success Criteria** (what must be TRUE):
  1. Utilizador abre a paleta de pesquisa a partir do campo já existente no topbar ou via atalho Ctrl+K/⌘K, a partir de qualquer página autenticada (SRCH-05)
  2. Ao escrever 2 ou mais caracteres, a pesquisa dispara automaticamente ao fim de ~300ms de pausa (debounce), sem um pedido novo a cada tecla premida (SRCH-03)
  3. Os resultados aparecem agrupados por tipo de entidade, cada um com um subtítulo desambiguador (ex: número + NIF para Cliente) e com o texto correspondente à pesquisa destacado visualmente; clicar num resultado navega para a rota de detalhe existente e fecha a paleta (SRCH-04, SRCH-11)
  4. O utilizador vê 3 estados distintos consoante o momento: antes de escrever (últimos registos visitados, guardados apenas no cliente/sessão, nunca no servidor), a carregar, e sem resultados (SRCH-08, SRCH-10)
  5. Cada grupo de resultados mostra um link "Ver todos" que abre a lista completa desse tipo, já filtrada pelo termo pesquisado (SRCH-09)
**Plans**: 5 plans (3 waves)
- [x] 112-01-PLAN.md — Fundação: tipo ResultadoPesquisa, useDebouncedValue (300ms), hook useGlobalSearch (/pesquisa, ≥2 chars), helpers search-recents (sessionStorage) + highlightMatch
- [x] 112-02-PLAN.md — GlobalSearchDialog: paleta Ctrl+K/⌘K (CommandDialog, shouldFilter=false, resultados agrupados, destaque, recentes, estados, "Ver todos", navegação guardada por isInternalLinkUrl)
- [x] 112-03-PLAN.md — Topbar: trigger desktop (fake-input + kbd hint) + trigger ícone mobile, monta GlobalSearchDialog uma vez (SRCH-05)
- [x] 112-04-PLAN.md — Semear ?q= nas listas Clientes + Processos (SRCH-09)
- [x] 112-05-PLAN.md — Semear ?q= em Documentos (novo filtro client-side) + Pareceres (via pesquisa avançada) (SRCH-09)
**UI hint**: yes

#### Phase 113: Processos — Filtro por Estado

**Goal**: O utilizador filtra a lista de Processos por estado, sem perder os outros filtros já aplicados.
**Depends on**: Nothing (paralelizável com as Phases 111-112 — módulo e ficheiros distintos da pesquisa global)
**Requirements**: PEST-01
**Success Criteria** (what must be TRUE):
  1. Utilizador seleciona um estado num controlo dedicado na lista de Processos e a lista atualiza para mostrar apenas processos nesse estado
  2. Utilizador limpa o filtro de estado e a lista volta a mostrar todos os processos, respeitando os outros filtros já aplicados
  3. O filtro de estado funciona em conjunto com os filtros já existentes na lista de Processos, sem se substituírem mutuamente
**Plans**: 1 plan (1 wave)
- [x] 113-01-PLAN.md — Promover o filtro de Estado (NativeSelect) do painel avançado colapsado para a barra principal sempre visível (+ rebalancear a grelha avançada para lg:col-span-4)
**UI hint**: yes

#### Phase 114: Linguagem Visual — Cantos Arredondados (`--radius`)

**Goal**: A aplicação deixa de ter cantos retos e passa a ter cantos arredondados, de forma consistente em todos os componentes e em ambos os temas — reversão deliberada da identidade "documento institucional" estabelecida na v2.13.
**Depends on**: Nothing tecnicamente (mudança de um único token CSS já isolado) — sequenciada depois das Phases 111-113 nesta milestone para que a verificação visual cubra também os ecrãs novos que essas fases introduzem (diálogo de pesquisa, filtro de estado), evitando duplicar o QA visual quando a Phase 115 (ícones) tocar os mesmos ecrãs
**Requirements**: RAD-01
**Success Criteria** (what must be TRUE):
  1. O token `--radius` (e os derivados `--radius-sm/md/lg/xl`) deixa de ser `0` e passa a um valor arredondado, definido uma única vez e consumido por todos os componentes que já usam a variável
  2. Cartões, botões, inputs, badges, diálogos e a sidebar mostram cantos arredondados de forma visualmente consistente, tanto no tema claro como no escuro
  3. Todos os ecrãs da aplicação — incluindo os novos introduzidos pelas Phases 111-113 desta milestone (paleta de pesquisa, filtro de estado) — mostram o mesmo raio de canto consistente, sem exceções visuais nem cantos retos remanescentes
**Plans**: 1 plan (1 wave)
- [ ] 114-01-PLAN.md — Flip `--radius` 0rem→0.5rem em ambas as apps (web + webpage) + remover os ~264 (web) + 7 (webpage) overrides `rounded-none` ilegítimos, preservando as 6 exceções legítimas; primitivo Card da webpage → `rounded-lg`
**UI hint**: yes

#### Phase 115: Linguagem Visual — Ícones em Todos os Botões + Filtros Ícone-Only

**Goal**: Todos os botões da aplicação comunicam a sua ação através de um ícone consistente, e os botões de ação de filtro em todos os módulos tornam-se ícone-only com tooltip — o próximo passo da linguagem visual, depois de fechados os gaps de UX (Phases 111-113) e da fundação de cantos arredondados (Phase 114).
**Depends on**: Phase 111, Phase 112, Phase 113, Phase 114 (o levantamento de ícones tem de cobrir os botões novos introduzidos por todas as fases anteriores desta milestone — trigger de pesquisa, filtro de estado — e correr depois da Phase 114 evita duplicar o QA visual)
**Requirements**: ICON-01, FICO-01
**Success Criteria** (what must be TRUE):
  1. Todo o botão de ação primária ou secundária na aplicação — incluindo os introduzidos por esta milestone (paleta de pesquisa, filtro de estado) — apresenta um ícone consistente com a sua ação (ICON-01)
  2. Botões de ação de filtro (aplicar/limpar/exportar) em Clientes, Processos, Agenda, Documentos e Financeiro passam a mostrar apenas o ícone, sem texto visível ao lado (FICO-01)
  3. Ao passar o rato sobre um botão de filtro ícone-only, aparece um tooltip a identificar a ação, reutilizando o primitivo `Tooltip` já instalado desde a v2.13 (FICO-01)
  4. Um utilizador que navegue por teclado (foco, sem rato) ainda consegue identificar a ação de cada botão ícone-only através de um nome acessível (`aria-label`), preservando a acessibilidade já corrigida noutros pontos da aplicação (FICO-01)
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 43. Data Layer + Backend Endpoints | v2.0 | 2/2 | Complete | 2026-06-18 |
| 44. Status + KPIs | v2.0 | 2/2 | Complete | 2026-06-18 |
| 45. Filtros + Edit/Delete UI | v2.0 | 2/2 | Complete | 2026-06-18 |
| 46. CSV Export | v2.0 | 1/1 | Complete | 2026-06-18 |
| 47. Notificações In-App | v2.1 | 2/2 | Complete | 2026-06-18 |
| 48. Recorrência de Eventos | v2.1 | 2/2 | Complete | 2026-06-18 |
| 49. Drag & Drop no Calendário | v2.1 | 1/1 | Complete | 2026-06-18 |
| 50. Backend MinIO Integration | v2.2 | 2/2 | Complete | 2026-06-19 |
| 51. Frontend Upload Component | v2.2 | 1/1 | Complete | 2026-06-19 |
| 52. Deploy MinIO no Hostinger | v2.2 | 1/1 | Complete | 2026-06-19 |
| 53. Shell Responsivo | v2.3 | 2/2 | Complete | 2026-06-21 |
| 54. Listas e Tabelas | v2.3 | 3/3 | Complete | 2026-06-21 |
| 55. Formulários e Modais | v2.3 | 2/2 | Complete | 2026-06-21 |
| 56. Dashboard e Calendário | v2.3 | 1/1 | Complete | 2026-06-21 |
| 57. Backend Schema + API | v2.4 | 2/2 | Complete | 2026-06-30 |
| 58. Formulário Dinâmico | v2.4 | 4/4 | Complete | 2026-06-30 |
| 59. Procuração + Intake | v2.4 | 6/6 | Complete | 2026-06-30 |
| 60. Ficha Imprimível | v2.4 | 2/2 | Complete | 2026-06-30 |
| 61. Data Layer + Backend CRUD | v2.5 | 2/2 | Complete   | 2026-06-30 |
| 62. Elaboração e Versionamento | v2.5 | 2/2 | Complete   | 2026-06-30 |
| 63. Aprovação e Entrega | v2.5 | 1/1 | Complete   | 2026-06-30 |
| 64. Auditoria e Pesquisa Avançada | v2.5 | 2/2 | Complete   | 2026-07-01 |
| 65. Fundação — Listagem e Detalhe | v2.6 | 2/2 | Complete    | 2026-07-01 |
| 66. Criação de Solicitação | v2.6 | 1/1 | Complete    | 2026-07-01 |
| 67. Elaboração e Versionamento | v2.6 | 1/1 | Complete    | 2026-07-01 |
| 68. Entrega, Vista de Entregue e RBAC | v2.6 | 1/1 | Complete    | 2026-07-01 |
| 69. Pesquisa Avançada | v2.6 | 1/1 | Complete    | 2026-07-01 |
| 70. Backend refactoring & Seeder Alignment | v2.7 | 1/1 | Complete | 2026-07-01 |
| 71. Frontend Types, Schema & API Integration | v2.7 | 2/2 | Complete | 2026-07-01 |
| 72. Form Refactoring (Create & Edit) | v2.7 | 1/1 | Complete | 2026-07-02 |
| 73. Detail Page & Printable Ficha Update | v2.7 | 1/1 | Complete | 2026-07-02 |
| 73.1. Fechar gap CLI-05 (gap closure) | v2.7 | 1/1 | Complete | 2026-07-02 |
| 74. Enum `documento_tipo` (BI/NIF/Restrição por Tipo) | v2.8 | 5/5 | Complete    | 2026-07-04 |
| 75. Componente Único View/Edit | v2.8 | 3/3 | Complete    | 2026-07-04 |
| 76. Separadores — Dados, Contactos e Notas | v2.8 | 1/1 | Complete    | 2026-07-05 |
| 77. Separadores — Processos e Pareceres | v2.8 | 1/1 | Complete    | 2026-07-05 |
| 78. Separadores — Documentos a Tratar e Deslocações | v2.8 | 1/1 | Complete    | 2026-07-06 |
| 79. Documentos Entregues — Upload Real | v2.8 | 2/2 | Complete    | 2026-07-06 |
| 80. Fundações — Processo.juizo/origem + Entidades | v2.9 | 1/1 | Complete    | 2026-07-07 |
| 81. Backend — CRUD + Wiring Juízo/Origem | v2.9 | 3/3 | Complete    | 2026-07-07 |
| 82. Backend — Honorário Automático | v2.9 | 1/1 | Complete    | 2026-07-07 |
| 83. Frontend — Tipos, Schemas e Hooks | v2.9 | 2/2 | Complete    | 2026-07-07 |
| 84. Frontend — UI (Intake, Dados, Abas, Termo) | v2.9 | 5/5 | Complete    | 2026-07-08 |
| 85. Consolidação da Lógica de "Prazo Crítico" | v2.10 | 1/1 | Complete    | 2026-07-08 |
| 86. Infraestrutura de Notificações — Entidade, API e Targeting | v2.10 | 3/3 | Complete    | 2026-07-08 |
| 87. Alertas de Eventos — Fase, Documento, Atribuição e Parecer | v2.10 | 4/4 | Complete    | 2026-07-09 |
| 88. Verificação Diária de Prazos e Honorários | v2.10 | 2/2 | Complete    | 2026-07-09 |
| 89. Sino e Página de Notificações | v2.10 | 4/4 | Complete    | 2026-07-10 |
| 90. SpotBugs/SAST — Commit e Verificação | v2.11 | 1/1 | Complete    | 2026-07-13 |
| 91. Infraestrutura de Testes de Integração (Testcontainers) | v2.11 | 3/3 | Complete    | 2026-07-13 |
| 92. Agenda ↔ RiscoPrazoService — Consolidação | v2.11 | 2/2 | Complete    | 2026-07-13 |
| 93. NOTF-24 — Preferências de Notificação por Utilizador | v2.11 | 4/4 | Complete    | 2026-07-14 |
| 94. NOTF-27 — Corrigir Colisão de Dedup ADMIN | v2.11 | 1/1 | Complete    | 2026-07-14 |
| 95. NOTF-25 — Notificar Toda a Equipa do Processo | v2.11 | 2/2 | Complete    | 2026-07-14 |
| 96. NOTF-26 — Snooze de Lembrete de Prazo | v2.11 | 4/4 | Complete    | 2026-07-14 |
| 97. Auditoria de Milestone — Dívida Técnica e UAT Pendente | v2.11 | 4/4 | Complete    | 2026-07-14 |
| 98. Backend — Endpoint Público de Branding | v2.12 | 1/1 | Complete   | 2026-07-15 |
| 99. webpage/ — Nova App Next.js de Landing | v2.12 | 4/4 | Complete   | 2026-07-15 |
| 100. Infraestrutura — Routing e Deployment | v2.12 | 4/4 | Complete   | 2026-07-15 |
| 101. Fundação — CLI Init e Design Tokens | v2.13 | 5/5 | Complete    | 2026-07-15 |
| 102. Reconciliação do Design System | v2.13 | 4/4 | Complete    | 2026-07-16 |
| 103. Módulo Dashboard | v2.13 | 1/1 | Complete    | 2026-07-16 |
| 104. Padrão DataTable Partilhado | v2.13 | 6/6 | Complete    | 2026-07-16 |
| 105. Módulos Clientes + Processos | v2.13 | 6/6 | Complete    | 2026-07-16 |
| 106. Módulo Agenda | v2.13 | 4/4 | Complete    | 2026-07-16 |
| 107. Módulos Documentos + Financeiro | v2.13 | 6/6 | Complete    | 2026-07-17 |
| 108. Módulo Pareceres | v2.13 | 4/4 | Complete    | 2026-07-17 |
| 109. Notificações / Settings / Setup Wizard | v2.13 | 3/3 | Complete    | 2026-07-17 |
| 110. Refinamento da Landing (webpage/) | v2.13 | 3/3 | Complete    | 2026-07-18 |
| 111. Backend — Pesquisa Global Cross-Entity (API) | v2.14 | 2/2 | Complete    | 2026-07-21 |
| 112. Frontend — Pesquisa Global (Paleta de Comando) | v2.14 | 5/5 | Complete    | 2026-07-21 |
| 113. Processos — Filtro por Estado | v2.14 | 1/1 | Complete    | 2026-07-21 |
| 114. Linguagem Visual — Cantos Arredondados (--radius) | v2.14 | 0/1 | Planned | - |
| 115. Linguagem Visual — Ícones + Filtros Ícone-Only | v2.14 | 0/TBD | Not started | - |

**Next:** Milestone v2.14 (UI/UX Melhorias) — ROADMAP criado: 5 fases (111–115), 15/15 requisitos mapeados (SRCH-01 a SRCH-11, PEST-01, ICON-01, RAD-01, FICO-01), cobertura 100%. Run `/gsd:plan-phase 111` to begin execution (aguarda aprovação do roadmap).
