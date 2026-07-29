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
- ✅ **v2.14 UI/UX Melhorias** — Phases 111–115.1 (complete 2026-07-22)
- ✅ **v2.15 Reposicionamento SIJ** — Phase 116 (complete 2026-07-27)
- 🚧 **v2.16 Distribuição Multi-Tenant e Faturação por Utilizadores** — Phases 117–123 (in progress)

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

<details>
<summary>✅ v2.14 UI/UX Melhorias (Phases 111–115.1) — SHIPPED 2026-07-22</summary>

Pesquisa global funcional cross-entity (backend `GET /api/v1/pesquisa` tenant+RBAC por ramo de entidade + paleta de comando Ctrl+K/⌘K no frontend, Phases 111-112); filtro de Estado promovido para a barra sempre visível em Processos (Phase 113); token `--radius` revertido de reto para arredondado em ambos os apps com sweep de 271 overrides ilegítimos (Phase 114); ícones em todos os botões + filtros ícone-only em 5 módulos (Phase 115). Fase inserida pós-fecho (115.1, urgente) resolveu 3 gaps reportados pelo utilizador imediatamente após o fecho funcional — criação de Processo/Parecer a partir da ficha de cliente, correção do redirect "Entrar" do `webpage/` público, alinhamento visual dos filtros de Pareceres — com UAT ao vivo a encontrar e corrigir 1 bug real que 3 rondas de revisão de código estática não apanharam. 15/15 requisitos formais satisfeitos + 3 itens informais da Phase 115.1. Auditoria de milestone (re-auditada após a inserção da 115.1): 0 regressões cross-phase, status `tech_debt` (sem bloqueios). Ver Scope Note no archive.

- [x] Phase 111: Backend — Pesquisa Global Cross-Entity (API) (SRCH-01, SRCH-02, SRCH-06, SRCH-07) — 2/2 plans, completed 2026-07-21
- [x] Phase 112: Frontend — Pesquisa Global (Paleta de Comando) (SRCH-03 a SRCH-05, SRCH-08 a SRCH-11) — 5/5 plans, completed 2026-07-21
- [x] Phase 113: Processos — Filtro por Estado (PEST-01) — 1/1 plan, completed 2026-07-21
- [x] Phase 114: Linguagem Visual — Cantos Arredondados (`--radius`) (RAD-01) — 1/1 plan, completed 2026-07-21
- [x] Phase 115: Linguagem Visual — Ícones + Filtros Ícone-Only (ICON-01, FICO-01) — 11/11 plans, completed 2026-07-22
- [x] Phase 115.1: Melhorias Técnicas — Ficha de Cliente, Login do Webpage e Filtros de Pareceres (INSERTED, sem REQ-IDs formais) — 3/3 plans, completed 2026-07-22

See archive: [milestones/v2.14-ROADMAP.md](milestones/v2.14-ROADMAP.md) · [milestones/v2.14-REQUIREMENTS.md](milestones/v2.14-REQUIREMENTS.md) · [milestones/v2.14-MILESTONE-AUDIT.md](milestones/v2.14-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.15 Reposicionamento SIJ (Phase 116) — SHIPPED 2026-07-27</summary>

Milestone pequena e de baixo risco, entregue como fase única — correção de posicionamento institucional, não desenvolvimento de funcionalidade nova. Removeu todas as referências vivas a "NOSi" (agência de TI/transformação digital do governo cabo-verdiano) do produto e substituiu pelo enquadramento correto: o LexCV serve o ecossistema do SIJ (Sistema Judicial de Cabo Verde) — em `PROJECT.md`, na landing pública (`webpage/trust-section.tsx`), em `.trae/documents/SPEC.md` e no tenant de demonstração seedado (`DatabaseSeeder.java`: nome/NIF/tipoEntidade/email/telefone). A auditoria de integração da milestone encontrou um raio de impacto maior do que o originalmente identificado: o nome "NOSi" era servido não só pelo endpoint público `GET /api/v1/public/branding` mas também por `GET /api/v1/auth/me` a qualquer utilizador autenticado (app shell + 2 documentos legais imprimíveis) — corrigido automaticamente pela mesma alteração de dados seed. 4/4 requisitos satisfeitos, 0 gaps de integração cross-phase. Ver Scope Note no archive.

- [x] Phase 116: Reposicionamento Institucional — Fim das Referências a NOSi (SIJ-01, SIJ-02, SIJ-03, SIJ-04) — 1/1 plan, completed 2026-07-27

See archive: [milestones/v2.15-ROADMAP.md](milestones/v2.15-ROADMAP.md) · [milestones/v2.15-REQUIREMENTS.md](milestones/v2.15-REQUIREMENTS.md) · [milestones/v2.15-MILESTONE-AUDIT.md](milestones/v2.15-MILESTONE-AUDIT.md)

</details>

### 🚧 v2.16 Distribuição Multi-Tenant e Faturação por Utilizadores (Phases 117–123) — Em Curso

**Milestone Goal:** Evoluir de "1 deployment por escritório" para uma instância partilhada multi-tenant, com limite de utilizadores por tenant e suporte a faturação manual por utilização — reabrindo deliberadamente a decisão da v2.12 de não ter provisionamento multi-tenant. Base: `proposta_multitenancy_distribuicao_faturacao.md` (28 jul 2026).

As 15 requisitos desta milestone dividem-se em 4 blocos (PLAN/PROV/ISOL/UTIL) com uma ordem de dependência real, não arbitrária — a mesma ordem sugerida pela proposta, mas com o bloco ISOL desdobrado em duas fases por causa de uma dependência que a própria proposta cria: ISOL-04 (a auditoria) só pode auditar superfícies que ainda não existem no início da milestone. PLAN (117-118) entrega valor imediato com risco zero de isolamento entre tenants — só existe um tenant até esta fase terminar, por isso divide-se em backend (limite aplicado em `AdminController.createUser`) e frontend (indicador "X/Y"), seguindo o mesmo padrão backend-antes-de-frontend já usado no projeto. PROV (119-120) introduz o papel `PLATAFORMA_ADMIN` e a capacidade de provisionar tenants adicionais, também dividida em backend (papel, tenant reservada "LexCV", serviço de provisionamento que contorna o gate singleton do `SetupService` só neste novo caminho — PROV-01/PROV-06) e frontend (a consola onde essa capacidade é de facto usada para criar/listar/ajustar/suspender tenants — PROV-02 a PROV-05), o mesmo padrão já usado em Pesquisa Global (Phases 111→112) e Notificações (Phases 86→89). A Phase 121 (fechar as suposições de tenant única + bloquear `PUT /api/v1/admin/rbac`) corre imediatamente a seguir à Phase 120, nunca depois de UTIL ou da auditoria — é a correção mais importante identificada pela proposta (secção 7): sem ela, dois tenants partilhados interferem-se um ao outro através de um ecrã de configurações aparentemente inofensivo, e esse risco só passa a existir a partir do momento em que a Phase 120 torna possível criar um 2º tenant real (ver nota de Risco na própria Phase 121). UTIL (122) e a auditoria de isolamento dedicada (123, ISOL-04) fecham a milestone por último, nesta ordem e não antes, porque a Phase 123 tem de auditar precisamente as duas superfícies mais recentes — a consola de tenants (120) e o relatório de utilização (122) — no espírito da AUD-01 da v2.11 (veredito explícito por superfície, não apenas uma checklist). `StorageService` (particionamento por `tenantId` já existente) e o padrão de iteração cross-tenant do `AlertasDiariosJob` (reaproveitado conceptualmente pelo relatório da Phase 122) foram confirmados pela proposta como já corretos — nenhuma fase desta milestone precisa de os alterar.

#### Phase 117: Backend — Limite de Utilizadores por Tenant

**Goal**: O backend aplica um limite de utilizadores ativos por tenant — `POST /api/v1/admin/users` recusa criar mais um utilizador quando o tenant já está no limite do seu plano, e desativar alguém liberta a vaga de imediato.
**Depends on**: Nothing (primeira fase da milestone)
**Requirements**: PLAN-01, PLAN-02, PLAN-04
**Success Criteria** (what must be TRUE):

  1. `Tenant` tem os novos campos `plano` e `limiteUtilizadores` persistidos (migração aplicada; tenants existentes recebem um valor por omissão sensato, sem quebrar o arranque da aplicação)
  2. `AdminController.createUser` (`POST /api/v1/admin/users`) devolve `409` com uma mensagem clara quando o tenant já tem `limiteUtilizadores` utilizadores com `ativo=true` — e continua a criar normalmente abaixo do limite
  3. Desativar um utilizador (`PUT /api/v1/admin/users/{id}` com `ativo=false`) liberta imediatamente uma vaga — uma criação imediatamente a seguir já não é bloqueada pelo `409`
  4. A contagem de "utilizadores ativos" usada para o limite é uma única função/consulta reutilizável, não duplicada — as Phases 120 e 122 vão reutilizá-la para a consola de tenants e o relatório de utilização

**Plans**: 2 plans (2 waves)

- [x] 117-01-PLAN.md — Camada de dados: enum `TenantPlano`, campos `plano`/`limiteUtilizadores` em `Tenant`, contagem reutilizável `UserRepository.countByTenantIdAndAtivoTrue` (fonte única para as Phases 120/122) e migração manual `117-add-tenant-plano-limite-utilizadores.sql` com backfill `ENTERPRISE`/`NULL` do tenant existente
- [x] 117-02-PLAN.md — Enforcement em `AdminController.createUser`: `409 CONFLICT` no limite, `201` abaixo, `null` sem limite, vaga libertada de imediato ao desativar — provado por teste Mockito de 4 casos escrito primeiro, mais gate de regressão/SAST e verificação das mitigações STRIDE

#### Phase 118: Frontend — Indicador de Utilizadores no Limite

**Goal**: A aba "Gestão de Utilizadores" das Definições mostra a ocupação do plano do tenant e impede visualmente ultrapassar o limite antes mesmo de chamar a API.
**Depends on**: Phase 117
**Requirements**: PLAN-03
**Success Criteria** (what must be TRUE):

  1. A aba "Gestão de Utilizadores" (`settings/page.tsx`, `UserManagementTab`) mostra "X/Y utilizadores" com base nos utilizadores ativos e no `limiteUtilizadores` do tenant
  2. O botão "Novo Utilizador" fica desativado quando X=Y, com indicação visual do motivo (tooltip ou texto junto ao contador)
  3. O `409` devolvido pelo backend (Phase 117), caso ainda assim ocorra (ex.: duas abas abertas em simultâneo), é apresentado como toast claro, sem crash da UI — nunca confiar só na validação visual do lado do cliente

**Plans**: 3 plans (3 waves)

- [x] 118-01-PLAN.md — Backend: expor `tenant_plano`/`tenant_limite_utilizadores` no `GET /auth/me` (2 campos no `UserResponse` + 2 setters dentro do `ifPresent` ja existente, zero queries novas), provado por 4 testes Mockito + gate de regressao/SAST/STRIDE
- [x] 118-02-PLAN.md — Frontend: `MeResponse` estendido, toast local sem prefixo `API NNN:` (409 limpo), e o indicador "X/Y utilizadores" com botao "Novo Utilizador" desativado no limite + tooltip que dispara via `<span tabIndex={0}>` — com gate executavel `pnpm verify:limite-utilizadores` (8 assercoes)
- [x] 118-03-PLAN.md — Verificacao humana ao vivo: contador nos 3 estados, tooltip por rato e por teclado sobre o botao desativado, e toast do 409 forcado; base de dados reposta no fim

**UI hint**: yes

#### Phase 119: Backend — Papel de Administrador de Plataforma e Provisionamento

**Goal**: Existe um papel `PLATAFORMA_ADMIN`, distinto do `ADMIN` de cada escritório, associado a uma tenant reservada "LexCV", com uma capacidade de backend para criar tenants adicionais sem depender do wizard `/setup` — que se mantém singleton, reservado só ao arranque inicial da própria plataforma.
**Depends on**: Phase 118
**Requirements**: PROV-01, PROV-06
**Success Criteria** (what must be TRUE):

  1. Existe um papel `PLATAFORMA_ADMIN` seedado (`DatabaseSeeder`), distinto de `ADMIN`, e uma tenant reservada "LexCV" à qual os utilizadores desse papel pertencem
  2. Um novo método de serviço de backend cria um `Tenant` + o respetivo utilizador `ADMIN` inicial, reutilizando a validação já existente em `SetupService.initializeSystem`, sem depender de `SystemSetting.initialized`
  3. `POST /api/v1/setup/initialize` continua a devolver erro se chamado uma segunda vez — a nova capacidade de criar tenants é um caminho de código distinto, gated a `PLATAFORMA_ADMIN`, nunca reaproveita o endpoint público de `/setup`
  4. Um utilizador com o papel `ADMIN` de um tenant normal não tem `hasRole('PLATAFORMA_ADMIN')` e recebe `403` ao tentar invocar a nova capacidade de criação de tenants

**Plans**: 4 plans

Plans:
**Wave 1**

- [x] 119-01-PLAN.md — Seed do papel `PLATAFORMA_ADMIN` (zero permissões), da tenant reservada "LexCV" e do utilizador bootstrap, incondicional e idempotente, mais `TenantRepository.findByNome`
- [x] 119-02-PLAN.md — `SetupService.provisionTenant` (devolve a `Tenant` criada, sem dependência de `SystemSetting`) e o DTO `TenantProvisionResponse`
- [ ] 119-03-PLAN.md — Contenção do papel de plataforma em `AdminController`: um ADMIN de escritório não pode atribuir, promover, ver nem alterar `PLATAFORMA_ADMIN`

**Wave 2** *(blocked on Wave 1 completion)*

- [ ] 119-04-PLAN.md — `PlatformAdminController` (`POST /api/v1/platform/tenants`), mapeamento `AccessDeniedException` -> 403, e regressão do gate singleton de `/setup/initialize`

#### Phase 120: Frontend — Consola de Administração de Tenants

**Goal**: O administrador de plataforma tem um ecrã interno, não público, onde cria novos tenants (com o respetivo ADMIN inicial), lista todos os tenants existentes e a sua utilização, ajusta plano/limite de qualquer um, e suspende quem não pague — bloqueando-lhe o acesso de imediato.
**Depends on**: Phase 119
**Requirements**: PROV-02, PROV-03, PROV-04, PROV-05
**Success Criteria** (what must be TRUE):

  1. Ecrã interno, acessível só a `PLATAFORMA_ADMIN` (nunca ao `ADMIN` de um tenant normal), cria um novo tenant preenchendo nome + dados do utilizador ADMIN inicial, reutilizando o serviço de backend da Phase 119
  2. O mesmo ecrã lista todos os tenants existentes, mostrando o número de utilizadores ativos de cada um
  3. Administrador de plataforma edita `plano`/`limiteUtilizadores` de qualquer tenant a partir desse ecrã
  4. Administrador de plataforma alterna o estado suspenso/ativo de um tenant a partir desse ecrã; um tenant suspenso deixa imediatamente de conseguir autenticar-se ou continuar a usar uma sessão já ativa

**Plans**: TBD
**UI hint**: yes

#### Phase 121: Fechar Suposições de Tenant Única + Bloqueio de RBAC

**Goal**: Nenhuma superfície pública ou administrativa do produto continua a assumir que existe apenas um tenant — a landing pública mostra sempre a marca genérica LexCV, e a gestão de permissões por papel deixa de ser editável por cada escritório na interface, passando a ser uma operação fixa de plataforma. Esta fase corre imediatamente a seguir à Phase 120, nunca depois da Phase 122/123 — ver Risco abaixo.
**Depends on**: Phase 120
**Requirements**: ISOL-01, ISOL-02, ISOL-03
**Success Criteria** (what must be TRUE):

  1. `GET /api/v1/public/branding` deixa de depender de `TenantRepository.findFirstByOrderByCreatedAtAsc()` para decidir que marca mostrar — devolve sempre a marca genérica LexCV, independentemente de quantos tenants reais existirem
  2. Uma pesquisa dedicada ao código de produção (não só o call site já sinalizado em `PublicController.getBranding`) confirma que nenhum outro caminho resolve "a" tenant por heurística de "mais antiga" quando existir mais de um tenant real
  3. `PUT /api/v1/admin/rbac` deixa de aceitar chamadas de um `ADMIN` de tenant (`403`) — só `PLATAFORMA_ADMIN` pode alterar o mapeamento de permissões por papel
  4. A aba "Controlo de Acesso (RBAC)" das Definições (`settings/page.tsx`, `RbacTab`) deixa de expor a ação de gravar a um `ADMIN` de tenant na interface, evitando um `403` confuso na UI

**Risco**: ISOL-03 (bloqueio do RBAC) é o item de maior risco identificado pela proposta (secção 7) — sem ele, dois tenants partilhados no mesmo deployment interferem-se um ao outro através de um ecrã de configurações aparentemente inofensivo. Esse risco só passa a existir a partir do momento em que a Phase 120 torna possível criar um 2º tenant real; por isso esta fase corre imediatamente a seguir, antes de UTIL (122) e da própria auditoria (123). Se a execução ou o deployment não seguirem esta ordem estrita — por exemplo, se a Phase 120 for posta em produção e usada para provisionar um 2º tenant real antes desta Phase 121 estar também em produção — essa janela de interferência fica genuinamente aberta. Não provisionar um 2º tenant pagante real através da consola da Phase 120 antes desta fase (121) estar concluída e implantada.
**Plans**: TBD
**UI hint**: yes

#### Phase 122: Relatório de Utilização por Tenant

**Goal**: O administrador de plataforma consulta um relatório interno, por tenant, com nome/plano/limite contratado/utilizadores ativos agora — a base factual para emitir a fatura manual de cada escritório.
**Depends on**: Phase 121
**Requirements**: UTIL-01
**Success Criteria** (what must be TRUE):

  1. Um ecrã de relatório, acessível só a `PLATAFORMA_ADMIN`, mostra na interface, para cada tenant: nome, plano, limite de utilizadores contratado, e utilizadores ativos neste momento
  2. Os números apresentados usam a mesma contagem de "utilizador ativo" da Phase 117 (`ativo=true`) — uma única fonte de verdade, nunca um cálculo paralelo
  3. Tenants suspensos (Phase 120) continuam visíveis no relatório com o seu estado identificado, em vez de desaparecerem da lista

**Plans**: TBD
**UI hint**: yes

#### Phase 123: Auditoria de Isolamento Dedicada

**Goal**: Uma auditoria de isolamento dedicada — no espírito da AUD-01 da v2.11 — confirma que as novas superfícies multi-tenant (consola de administração de tenants, relatório de utilização, bloqueio de RBAC) não deixam nenhum tenant ver ou influenciar dados de outro, antes de o utilizador provisionar um 2º tenant pagante real através da consola da Phase 120. `StorageService` (particionamento por `tenantId` já existente) confirmado fora de âmbito — sem alterações necessárias.
**Depends on**: Phase 122
**Requirements**: ISOL-04
**Success Criteria** (what must be TRUE):

  1. A consola de administração de tenants (Phase 120) e o relatório de utilização (Phase 122) são auditados e confirmados a expor dados exclusivamente através de endpoints gated a `PLATAFORMA_ADMIN`, nunca através de um endpoint tenant-scoped comum
  2. O bloqueio de `PUT /api/v1/admin/rbac` (Phase 121) é confirmado sem via de contorno — nenhum outro endpoint tenant-facing continua a permitir escrever `Role`/`Permission`
  3. A auditoria produz um veredito explícito por superfície (COVERED, ou lista de fixes aplicados), documentado antes de se considerar segura a criação de um 2º tenant pagante real fora de teste

**Plans**: TBD

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
| 114. Linguagem Visual — Cantos Arredondados (--radius) | v2.14 | 1/1 | Complete    | 2026-07-22 |
| 115. Linguagem Visual — Ícones + Filtros Ícone-Only | v2.14 | 11/11 | Complete    | 2026-07-22 |
| 115.1. Melhorias Técnicas (INSERTED) | v2.14 | 3/3 | Complete    | 2026-07-22 |
| 116. Reposicionamento Institucional — Fim das Referências a NOSi | v2.15 | 1/1 | Complete    | 2026-07-27 |
| 117. Backend — Limite de Utilizadores por Tenant | v2.16 | 2/2 | Complete   | 2026-07-29 |
| 118. Frontend — Indicador de Utilizadores no Limite | v2.16 | 3/3 | Complete   | 2026-07-29 |
| 119. Backend — Papel de Administrador de Plataforma e Provisionamento | v2.16 | 2/4 | In Progress|  |
| 120. Frontend — Consola de Administração de Tenants | v2.16 | 0/TBD | Not started | - |
| 121. Fechar Suposições de Tenant Única + Bloqueio de RBAC | v2.16 | 0/TBD | Not started | - |
| 122. Relatório de Utilização por Tenant | v2.16 | 0/TBD | Not started | - |
| 123. Auditoria de Isolamento Dedicada | v2.16 | 0/TBD | Not started | - |
