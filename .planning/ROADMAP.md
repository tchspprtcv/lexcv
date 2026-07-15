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
- 🚧 **v2.13 Refactor UI/UX (shadcn/ui)** — Phases 101–110 (roadmap criado, planeamento pendente)

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

### 🚧 v2.13 Refactor UI/UX (shadcn/ui) (Phases 101–110) — Em Curso

**Milestone Goal:** Auditar e refatorar visualmente toda a plataforma (app interna `web/` + landing `webpage/`) seguindo estritamente os padrões oficiais do shadcn/ui, preservando a identidade institucional já validada (sidebar/topbar/cores alinhadas ao Figma desde a v1.1), com fundação de design system formalizada primeiro.

A pesquisa de arquitetura desta milestone confirma que este não é um adoption greenfield mas um retrofit sobre 15 primitivos hand-rolled (Radix por baixo, nunca passados pela CLI) acumulados desde a v1.1, com drift não documentado (variante `gray` do badge, literais `neutral-*`/`slate-*` em vez de tokens semânticos). A ordenação das 10 fases segue rigorosamente a recomendação da pesquisa: Fundação (101) e Reconciliação (102) têm de terminar ambas antes de qualquer fase de módulo — o estado "app visivelmente meio-migrada" é o próprio pitfall identificado a evitar; o padrão DataTable (104) é construído uma única vez, como fase própria, em vez de ser reinventado 5 vezes por módulo; Clientes e Processos (105) são combinados numa só fase porque partilham deliberadamente o mesmo padrão de botões-toggle e a sua migração para `Tabs` tem de ser entregue em conjunto, nunca isoladamente, sob pena de uma janela de inconsistência visível; as restantes fases de módulo (106–109) e o refinamento da `webpage/` (110) não têm dependências mútuas entre si — todas dependem apenas de 101/102 estarem concluídas — pelo que podem ser executadas em paralelo, com a ordem 106→109 refletindo apenas risco/novidade de primitivos decrescente, não uma cadeia de bloqueio real.

#### Phase 101: Fundação — CLI Init e Design Tokens

**Goal**: `web/` e `webpage/` têm uma fundação de design system corretamente inicializada (scaffolded pela CLI oficial, base Radix, tokenizada, com todos os primitivos que as fases seguintes vão precisar) sem que nenhuma página visível mude ainda.
**Depends on**: Nothing (first phase of milestone)
**Requirements**: FND-01, FND-02, FND-03, FND-04, FND-05, FND-06, FND-07, FND-08
**Success Criteria** (what must be TRUE):
  1. `web/components.json` existe, gerado por `shadcn init -b radix` (nunca o default Base UI introduzido este mês), com aliases/estilo consistentes, e os 9 pacotes `@radix-ui/react-*`/`radix-ui` já em uso continuam a compor via `asChild` sem quebrar nenhum ficheiro existente.
  2. `webpage/components.json` existe com as mesmas respostas de configuração copiadas manualmente do `web/` — nunca gerado por um `init` independente nessa app, para as duas não divergirem sem um workspace partilhado a mantê-las sincronizadas.
  3. `globals.css` de ambas as apps contém o conjunto completo de tokens semânticos shadcn (`--primary`, `--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, `--radius`) mesclados aditivamente — confirmado que existe exatamente um bloco `:root`/`.dark` (não dois, silenciosamente sobrepostos) — com `--background`/`--foreground` restaurados aos valores hex já validados e `--radius`/`--primary` definidos deliberadamente para a identidade institucional, nunca deixados no default do CLI.
  4. Os ~15 primitivos em falta (Select, NativeSelect, Tabs, DropdownMenu, Command, Tooltip, Checkbox, Avatar, Separator, Skeleton, Progress, Calendar, Breadcrumb, Accordion, NavigationMenu, Empty) existem em `web/src/components/ui/` e importam/compilam sem erro; `react-day-picker` está fixado em `9.14.0` (não `@latest`) logo após `add calendar`; existe uma decisão explícita e aplicada de identidade de pacote Radix (`shadcn migrate radix` corrido, ou estado de ponte documentado), sem estado dual silencioso entre componentes antigos e novos.
  5. `tailwindcss-animate` foi removido e substituído por `tw-animate-css`; `<Toaster />` do `sonner` está montado na raiz de `web/` (e `webpage/` se aplicável), `toast.tsx`/`toaster.tsx`/`@radix-ui/react-toast` foram removidos por completo, e as chamadas `toast.success()`/`toast.error()` já existentes continuam a funcionar sem alteração de call-site.
**Plans**: 5 plans (4 waves)
- [x] 101-01-PLAN.md — Package legitimacy gate (blocking-human) for all net-new phase packages
- [x] 101-02-PLAN.md — web/: shadcn init -b radix, semantic tokens in globals.css, tw-animate-css swap
- [ ] 101-03-PLAN.md — web/: add the ~16 missing primitives + pin react-day-picker@9.14.0
- [ ] 101-04-PLAN.md — webpage/: mirror components.json + token block + tw-animate-css (FND-08 n/a)
- [ ] 101-05-PLAN.md — web/: Sonner adoption (contract preserved) + shadcn migrate radix

*Nota para planeamento:* nomes de flags/presets da CLI shadcn estão em mudança ativa (confirmada pelo menos uma renomeação nos últimos 8 meses) — re-verificar com `npx shadcn@latest init --help`/`--dry-run` no momento real de execução, em vez de confiar cegamente na sintaxe exata desta pesquisa.

#### Phase 102: Reconciliação do Design System

**Goal**: Os 14 componentes hand-rolled existentes estão reconciliados com o registo oficial sem perder nenhuma variante/prop customizada, e nenhum dos 38 ficheiros consumidores existentes quebra.
**Depends on**: Phase 101
**Requirements**: DSR-01, DSR-02, DSR-03
**Success Criteria** (what must be TRUE):
  1. Cada um dos 14 componentes (`button`, `dialog`, `alert-dialog`, `card`, `table`, `sheet`, `badge`, `input`, `label`, `popover`, `radio-group`, `switch`, `textarea`) foi reconciliado individualmente via `add <component> --diff` — nunca overwrite cego de uma vez só — com as variantes/props customizadas preservadas (ex.: variante `gray` do badge, tratamento de `showCloseButton` do dialog).
  2. `pnpm build`/typecheck de `web/` passa sem erros novos, confirmando que as 93 ocorrências de import destes 14 componentes (38 ficheiros) continuam a compilar e a passar typecheck depois da reconciliação.
  3. Botões icon-only em toda a app (ícones da sidebar colapsada, ações de linha icon-only) mostram um `Tooltip` ao passar o rato/foco, com um único `TooltipProvider` montado na raiz da app.
**Plans**: TBD

#### Phase 103: Módulo Dashboard

**Goal**: Os estados de loading e vazio do Dashboard usam os primitivos oficiais `Skeleton`/`Empty` em vez de texto ad hoc — o módulo com menor necessidade de primitivos novos, servindo para validar visualmente a nova camada de tokens antes dos módulos mais profundos a adotarem.
**Depends on**: Phase 102
**Requirements**: DASH-01, DASH-02
**Success Criteria** (what must be TRUE):
  1. Os KPI cards e a secção "Atividade Recente" do Dashboard mostram placeholders `Skeleton` enquanto os dados carregam, substituindo por completo o texto "A carregar...".
  2. Qualquer secção do Dashboard sem dados (ex.: sem atividade recente) mostra o componente `Empty` em vez de uma mensagem ad hoc.
**Plans**: TBD

#### Phase 104: Padrão DataTable Partilhado

**Goal**: Existe um único padrão DataTable reutilizável, construído sobre o `Table` já reconciliado, adotado pelas 5 listas que precisam dele sem duplicar os filtros já servidos pelo backend via TanStack Query.
**Depends on**: Phase 102 (paralelizável com a Phase 103 — ambas dependem apenas da reconciliação do `Table`, sem dependência mútua entre si)
**Requirements**: DTB-01, DTB-02, DTB-03
**Success Criteria** (what must be TRUE):
  1. `@tanstack/react-table` está adicionado como dependência e existe um padrão partilhado único (`columns.tsx` + `data-table.tsx` + toolbar de filtro + `DataTablePagination`/`DataTableViewOptions`) construído uma vez sobre o `Table` existente, nunca reinventado por módulo.
  2. As listas desktop (ramo `hidden md:block`) de Clientes, Processos, Pareceres, Financeiro e Documentos usam o padrão DataTable partilhado (ordenação por coluna, toolbar de filtro), continuando a usar exatamente os mesmos filtros server-side já servidos via TanStack Query — sem duplicar filtragem client-side.
  3. `/notificacoes` e qualquer outra lista paginada no servidor usa o componente oficial `Pagination` em vez de um pager customizado.
**Plans**: TBD

*Nota para planeamento:* é uma receita de composição, não um componente instalável — integrar com os filtros TanStack Query já existentes (sem duplicar) precisa de verificação no momento da implementação, por cada ecrã, dado o shape de filtro real de cada um.

#### Phase 105: Módulos Clientes + Processos (combinados)

**Goal**: A Ficha de Cliente e a Ficha de Processo usam `Tabs` reais e acessíveis em vez de botões-toggle manuais, e os seus formulários/listagens usam `Select`/`Avatar`/`Breadcrumb` oficiais — entregues em conjunto, nunca isoladamente, para nunca deixar as duas fichas visivelmente inconsistentes entre si.
**Depends on**: Phase 101, Phase 104 (precisa dos primitivos Tabs/Select/Avatar/Breadcrumb da Fundação; sequenciada depois do padrão DataTable por recomendação da pesquisa — não por dependência de ficheiros, já que a migração de listas [DTB-02] e a migração das fichas [CLP-01/02] tocam páginas distintas — apenas para evitar ter a lista e a ficha do mesmo módulo em fluxo de migração simultaneamente)
**Requirements**: CLP-01, CLP-02, CLP-03, CLP-04, CLP-05
**Success Criteria** (what must be TRUE):
  1. Os 7 separadores da Ficha de Cliente estão implementados com `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent` (semântica real `role="tablist"`/`aria-selected`, fechando o gap de acessibilidade do padrão de botões-toggle atual), preservando a contagem de separadores condicional por RBAC e o `overflow-x-auto` em mobile.
  2. A Ficha de Processo (Partes/Fases/Decisões/Factos/Testemunhas/Documentos) usa o mesmo padrão `Tabs`, entregue na mesma fase que a Ficha de Cliente — nunca isoladamente.
  3. Todos os `<select className={selectClassName}>` nativos em formulários de Clientes/Processos foram substituídos por `NativeSelect`/`Select`.
  4. `Avatar` é usado para representar advogados/administrativos/testemunhas em listagens e pickers de ambos os módulos.
  5. Os cabeçalhos das fichas de Cliente e Processo usam `Breadcrumb` em vez do `<div>`+`Link`+"/" atual.
**Plans**: TBD

*Nota para planeamento:* o comportamento de overflow dos separadores condicionados por RBAC precisa de verificação funcional real contra as 4 roles (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) em larguras de mobile reais — não é uma tarefa de "consultar a documentação", é uma tarefa de "testar contra a matriz RBAC real desta app".

#### Phase 106: Módulo Agenda

**Goal**: Os inputs de data dos formulários de Agenda usam o `Calendar` oficial e os filtros usam `Select`, sem alterar a vista de calendário mensal existente.
**Depends on**: Phase 102 (só precisa dos primitivos Calendar/Select da Fundação e da reconciliação de base; independente do padrão DataTable e da fase Clientes+Processos — paralelizável com as Phases 103–105 e 107–109 assim que a Phase 102 estiver concluída)
**Requirements**: AGD-36, AGD-37
**Success Criteria** (what must be TRUE):
  1. Os inputs de data dos formulários de criar/editar prazo usam o `Calendar` (shadcn/react-day-picker), sem alterar a vista de calendário mensal já existente.
  2. Os filtros de categoria/status da Agenda usam `Select`.
**Plans**: TBD

#### Phase 107: Módulos Documentos + Financeiro

**Goal**: O upload de documentos usa `Progress` oficial e os formulários de tipo/honorário/pagamento usam `Select`.
**Depends on**: Phase 102 (só precisa dos primitivos Progress/Select da Fundação; paralelizável com as restantes fases de módulo assim que a Phase 102 estiver concluída)
**Requirements**: DOF-01, DOF-02
**Success Criteria** (what must be TRUE):
  1. O upload de documentos usa o componente oficial `Progress` em vez da UI de progresso customizada existente.
  2. Os formulários de tipo de documento/honorário/pagamento usam `Select`.
**Plans**: TBD

#### Phase 108: Módulo Pareceres

**Goal**: Os formulários de Pareceres usam `Select`, a timeline usa `Tooltip` e o histórico de versionamento colapsa versões antigas via `Accordion`.
**Depends on**: Phase 102 (só precisa dos primitivos Select/Tooltip/Accordion da Fundação; paralelizável com as restantes fases de módulo assim que a Phase 102 estiver concluída)
**Requirements**: PARC-18, PARC-19, PARC-20
**Success Criteria** (what must be TRUE):
  1. Os campos de formulário de Pareceres usam `Select`.
  2. Os eventos da timeline de Pareceres mostram um `Tooltip`.
  3. O histórico de versionamento usa `Accordion` para colapsar versões antigas.
**Plans**: TBD

#### Phase 109: Notificações / Settings / Setup Wizard

**Goal**: O novo menu de utilizador da topbar, o contador do sino e o wizard de setup usam primitivos oficiais, sem tocar no `Popover` do sino que já está correto.
**Depends on**: Phase 102 (só precisa dos primitivos DropdownMenu/Badge/Progress da Fundação; menor superfície de todas as fases de módulo, mantida por último na narrativa por ser a mais aditiva/segura — paralelizável com as restantes fases de módulo assim que a Phase 102 estiver concluída)
**Requirements**: NTF-28, NTF-29, NTF-30
**Success Criteria** (what must be TRUE):
  1. O novo menu de utilizador na topbar usa `DropdownMenu` (o `Popover` do sino de notificações mantém-se inalterado — nunca convertido para `DropdownMenu`, um anti-padrão de acessibilidade conhecido para listas com múltiplos controlos).
  2. O contador de não-lidas do sino usa o componente oficial `Badge` em vez do `<span>` manual.
  3. O wizard `/setup` mostra um indicador de progresso linear baseado em `Progress` (sem Stepper de terceiros).
**Plans**: TBD

#### Phase 110: Refinamento da Landing (webpage/)

**Goal**: A landing pública tem navegação mobile funcional e as secções Hero/Contacto seguem a composição `Card`/`Badge` já idiomática do `TrustSection`.
**Depends on**: Phase 101 (o `components.json` da `webpage/` é copiado manualmente durante a Fundação; zero dependência de qualquer fase de módulo do `web/` — pode correr em paralelo com qualquer uma das Phases 103–109 assim que a Phase 101 estiver concluída)
**Requirements**: LDG-17, LDG-18
**Success Criteria** (what must be TRUE):
  1. O `SiteHeader` mostra navegação mobile funcional via o `Sheet` reutilizado (atualmente zero navegação em mobile — um gap funcional real, não cosmético).
  2. As secções Hero e Contacto estão recompostas com `Card`/`Badge`, replicando o padrão já idiomático do `TrustSection`.
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
| 101. Fundação — CLI Init e Design Tokens | v2.13 | 2/5 | In Progress|  |
| 102. Reconciliação do Design System | v2.13 | 0/TBD | Not started | - |
| 103. Módulo Dashboard | v2.13 | 0/TBD | Not started | - |
| 104. Padrão DataTable Partilhado | v2.13 | 0/TBD | Not started | - |
| 105. Módulos Clientes + Processos | v2.13 | 0/TBD | Not started | - |
| 106. Módulo Agenda | v2.13 | 0/TBD | Not started | - |
| 107. Módulos Documentos + Financeiro | v2.13 | 0/TBD | Not started | - |
| 108. Módulo Pareceres | v2.13 | 0/TBD | Not started | - |
| 109. Notificações / Settings / Setup Wizard | v2.13 | 0/TBD | Not started | - |
| 110. Refinamento da Landing (webpage/) | v2.13 | 0/TBD | Not started | - |

**Next:** Phase 101 (Fundação — CLI Init e Design Tokens) planeada — 5 planos em 4 waves (101-01 gate → 101-02 → {101-03, 101-04} → 101-05), FND-01..FND-08 cobertos. Run `/gsd:execute-phase 101` to build.
