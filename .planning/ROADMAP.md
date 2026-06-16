# Roadmap: LexCV

## Milestones

- ✅ **v1.0 MVP** - Phases 1-6 (shipped 2026-05-26) — archive: `.planning/milestones/v1.0-ROADMAP.md`
- ✅ **v1.1 UI/UX Alignment** - Phases 7-10 (shipped 2026-05-27) — archive: `.planning/milestones/v1.1-ROADMAP.md`
- ✅ **v1.2 Utilizador** - Phase 11 (shipped 2026-05-27) — archive: `.planning/milestones/v1.2-ROADMAP.md`
- ✅ **v1.3 Security Check** - Phases 12-17 (shipped 2026-06-03) — archive: `.planning/milestones/v1.3-ROADMAP.md`
- ✅ **v1.4 Melhoria módulo clientes** - Phases 18-21 (shipped 2026-06-03) — archive: `.planning/milestones/v1.4-ROADMAP.md`
- ⏸ **v1.5 Melhoria funcionalidades processos** - Phases 22-27 (paused/deferred)
- ⏸ **v1.6 Melhoria nfeature de gestão de clientes** - Phases 28-31 (paused/deferred)
- 🏃 **v1.7 Melhoria no modulo de gestao e acompanhamento de processos** - Phases 32-36 (active)

## Phases

<details>
<summary>✅ v1.0 MVP (Phases 1-6) - SHIPPED 2026-05-26</summary>

### Phase 1: Foundation (UI + Mock API + Auth)

**Goal**: Frontend Next.js (App Router) com shadcn/ui e TanStack Query, dashboard navegável e mock API `/api/v1` com login JWT simples e dados seed.
**Depends on**: Nothing (first phase)
**Requirements**: [AUTH-01, AUTH-02, AUTH-03, TEN-01, RBAC-01, NAV-01, DSH-01]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue autenticar e manter sessão (mock JWT) e obter `/auth/me`
  2. Dashboard com sidebar permite navegar para páginas base dos módulos do MVP
  3. KPI cards do dashboard renderizam dados consumindo `/api/v1/dashboard`
  4. Mock API aplica isolamento por tenant e retorna dados seed coerentes com o ERD

**Plans**: 3 plans

Plans:

- [x] 01-01: Scaffold Next.js + shadcn/ui + TanStack Query + estrutura de pastas do SPEC
- [x] 01-02: Mock API `/api/v1` (auth + dashboard) + seed fixtures (tenant/users)
- [x] 01-03: Layout do dashboard + RBAC de navegação (feature flags por role)

### Phase 2: Clientes

**Goal**: Módulo de clientes completo (lista, filtros, create/edit/delete, detalhe + conta corrente) consumindo o mock `/api/v1/clientes`.
**Depends on**: Phase 1
**Requirements**: [CLI-01, CLI-02, CLI-03]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue listar clientes com filtros por nome/nif e paginação (se existir)
  2. Utilizador consegue criar/editar/remover cliente via formulários com Zod + RHF
  3. Detalhe do cliente mostra conta corrente consumindo `/clientes/{id}/conta-corrente`

**Plans**: 2 plans

Plans:

- [x] 02-01: UI + hooks React Query + schemas Zod para Clientes
- [x] 02-02: Endpoints mock de Clientes + Conta Corrente e integração no UI

### Phase 3: Processos

**Goal**: Módulo de processos com CRUD e sub-recursos (partes, fases, movimentações) alinhados ao contrato.
**Depends on**: Phase 2
**Requirements**: [PRC-01, PRC-02, PRC-03, PRC-04, PRC-05]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue criar processo associado a cliente e listar/abrir detalhe
  2. Detalhe do processo mostra partes, fases e movimentações carregadas via sub-recursos
  3. Utilizador consegue adicionar partes, fases e movimentações via UI

**Plans**: 3 plans

Plans:

- [x] 03-01: UI + hooks + schemas para Processos
- [x] 03-02: Sub-recursos: Partes e Fases (UI + mock endpoints)
- [x] 03-03: Sub-recursos: Movimentações (UI + mock endpoints)

### Phase 4: Agenda

**Goal**: Módulo de agenda/eventos com filtros críticos e CRUD, suportando eventos por processo e tarefas/prazos.
**Depends on**: Phase 3
**Requirements**: [AGD-01, AGD-02]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue filtrar eventos por período e por processo
  2. Utilizador consegue criar/editar/remover evento e marcar concluído

**Plans**: 2 plans

Plans:

- [x] 04-01: UI de lista/calendário + filtros críticos com React Query
- [x] 04-02: Mock endpoints de Eventos + integração de CRUD/conclusão

### Phase 5: Documentos

**Goal**: Gestão documental mínima: listagem, upload multipart e download.
**Depends on**: Phase 4
**Requirements**: [DOC-01, DOC-02, DOC-03]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue listar documentos por processo/cliente
  2. Upload funciona com `multipart/form-data` e UI exibe estado de progresso/sucesso
  3. Download de documento funciona via endpoint dedicado

**Plans**: 2 plans

Plans:

- [x] 05-01: UI + hooks + schemas para Documentos
- [x] 05-02: Mock endpoints de upload/download (simulação) + fixtures

### Phase 6: Financeiro

**Goal**: Financeiro básico ligado a processos/clientes: honorários e pagamentos, conta corrente por cliente.
**Depends on**: Phase 5
**Requirements**: [FIN-01, FIN-02, FIN-03]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue criar honorário por processo e registrar pagamentos
  2. UI lista pagamentos de um honorário e reflete totais vindos do backend (sem cálculos locais complexos)
  3. Conta corrente por cliente é acessível e consistente com pagamentos/honorários no mock

**Plans**: 2 plans

Plans:

- [x] 06-01: UI + hooks + schemas para Honorários e Pagamentos
- [x] 06-02: Mock endpoints de Financeiro + integração com cliente/processo

</details>

<details>
<summary>✅ v1.1 UI/UX Alignment (Phases 7-10) - SHIPPED 2026-05-27</summary>

### Phase 7: Shell & Design System

**Goal**: Implementar layout base (sidebar + top app bar) e componentes reutilizáveis para suportar as telas Figma.
**Depends on**: Phase 6
**Success Criteria** (what must be TRUE):

  1. Sidebar e top app bar replicam o padrão visual do Figma em todas as páginas do dashboard
  2. Componentes base (badge, table, pagination, avatar/menu) estão disponíveis e aplicados nos módulos

**Plans**: 2 plans

Plans:

- [x] 07-01: Refatorar DashboardShell (sidebar/topbar) + tokens de layout (spacing/cores)
- [x] 07-02: Criar/ajustar componentes UI (badge, table, pagination, dropdown menu) e aplicar padrões

### Phase 8: Dashboard + Clientes + Processos (UI)

**Goal**: Ajustar telas para aproximar o layout e densidade visual dos mockups.
**Depends on**: Phase 7
**Success Criteria** (what must be TRUE):

  1. Dashboard exibe KPI cards, painel de urgências, tabela de processos recentes e atividade recente (layout Figma)
  2. Clientes tem bento stats, filtros e tabela com badges/ações/paginação no padrão Figma
  3. Processos tem stats cards, callout “próximas audiências”, filtros e tabela no padrão Figma

**Plans**: 2 plans

Plans:

- [x] 08-01: Dashboard institucional (KPIs + listas) + widgets laterais
- [x] 08-02: Clientes e Processos (tabelas + filtros + badges + ações) alinhados ao Figma

### Phase 9: Agenda & Prazos (UI)

**Goal**: Implementar layout de agenda no padrão Figma (calendário mensal + próximos eventos + mini stats).
**Depends on**: Phase 8
**Success Criteria** (what must be TRUE):

  1. Agenda apresenta calendário mensal (grid) e sidebar “Próximos eventos” como no mockup
  2. Ações principais (novo evento, navegar mês, ver lista) estão visíveis e acessíveis

**Plans**: 1 plan

Plans:

- [x] 09-01: Agenda (calendário mensal + próximos eventos + mini stats) alinhada ao Figma

### Phase 10: implementar dark/light mode e ajuste no layout das paginas

**Goal**: Implementar o sistema de Dark/Light mode e ajustar o layout das páginas para o design Anti-Safe Harbor (sharp edges, cores específicas).
**Depends on**: Phase 9
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue alternar entre light e dark mode.
  2. Componentes e layouts aplicam estilos apropriados baseados no theme (ex: sharp edges, no default bento).

**Plans**: 1 plan

Plans:

- [x] 10-01: Instalar next-themes, configurar ThemeProvider, refatorar páginas (Dashboard, Clientes, Processos, Agenda) para suportar dark mode e design sharp.

</details>

<details>
<summary>✅ v1.2 Utilizador (Phase 11) - SHIPPED 2026-05-27</summary>

### Phase 11: Painel de Utilizador

**Goal**: Desenvolver o painel de utilizador logado. Deve permitir editar foto, nome e outros dados alteráveis.
**Depends on**: Phase 10
**Success Criteria** (what must be TRUE):

  1. O utilizador consegue aceder à página do seu perfil/definições.
  2. O formulário permite editar a foto, nome e outros dados.
  3. A submissão guarda e reflete as alterações no UI (mock ou backend integrado).

**Plans**: 1 plan

Plans:

- [x] 11-01: Painel de Utilizador e mock API

</details>

<details>
<summary>✅ v1.3 Security Check (Phases 12-17) - SHIPPED 2026-06-03</summary>

### Phase 12: Infra & Security Tooling

**Goal**: Fix risky configs, secure secrets, and enable security scanning
**Depends on**: Phase 11
**Requirements**: [INFRA-01, INFRA-02, INFRA-03]
**Success Criteria** (what must be TRUE):

  1. DB and JWT secrets are loaded via environment variables
  2. `ddl-auto` is disabled and `include-message` is never for production profile
  3. SAST/SCA tools can run without failing the build

**Plans**: 1 plans

Plans:

- [x] 12-01: Configurar .env, profiles e plugins de segurança no Maven/npm

### Phase 13: Data Isolation & RLS

**Goal**: Prevent cross-tenant data leakage and implement Row-Level Security
**Depends on**: Phase 12
**Requirements**: [TENANT-01, TENANT-02, TENANT-03]
**Success Criteria** (what must be TRUE):

  1. All `ResourceController` endpoints enforce `tenant_id`
  2. `AdminController` only manages users for its tenant
  3. RLS is active in PostgreSQL

**Plans**: 1 plans

Plans:

- [x] 13-01: Refatorar repositories e endpoints para isolamento de tenant

### Phase 14: Auth & Session Hardening

**Goal**: Secure authentication flows and session management
**Depends on**: Phase 13
**Requirements**: [AUTH-01, AUTH-02, AUTH-03, AUTH-04]
**Success Criteria** (what must be TRUE):

  1. JWT is stored in `HttpOnly` cookies, not `localStorage`
  2. `/auth/login` blocks brute-force attempts
  3. Refresh tokens rotate upon use
  4. Users cannot set weak passwords

**Plans**: 2 plans

Plans:

- [x] 14-01: Migrar auth storage para HttpOnly Cookies e Rate Limitend & Backend)
- [x] 14-02: Rate limit, rotação de tokens e regras de senha forte

### Phase 15: AppSec & RBAC

**Goal**: Enforce access control, validate inputs, and secure headers
**Depends on**: Phase 14
**Requirements**: [APPSEC-01, APPSEC-02, APPSEC-03, APPSEC-04]
**Success Criteria** (what must be TRUE):

  1. `@PreAuthorize` prevents unauthorized access to endpoints
  2. `@Valid` rejects malformed requests
  3. Uploads reject dangerous file types and prevent path traversal
  4. Security headers (CSP, HSTS) are present in responses

**Plans**: 1 plans

Plans:

- [x] 15-01: AppSec & RBAC enforcementers, validação de inputs e HTTP headers

### Phase 16: Notificação e Feedback (Toaster)

**Goal**: Implementar sistema de notificações globais (Toaster) para feedback visual de Sucesso e Erro em toda a aplicação.
**Depends on**: Phase 15
**Requirements**: []
**Success Criteria** (what must be TRUE):

  1. Configuração global do Toaster Provider no layout raiz da aplicação (`App.tsx` ou `layout.tsx`).
  2. Implementação visual com ícones e tons adequados (verde/sucesso, vermelho/erro), com animações e auto-fecho (3 a 5 seg).
  3. Criação de um utilitário/hook (ex: `toast.success()`) de chamada simplificada.
  4. Interceptor global de erros da API para exibir toasts automáticos em falhas HTTP 400/500.

**Plans**: 1 plans

Plans:

- [x] 16-01: Adicionar biblioteca (ex: sonner), configurar provider global, criar hook customizado e interceptor de fetch/axios.

### Phase 17: Ações UI com controlo por permissions

**Goal:** Garantir que ações de novo, editar e menus só aparecem quando a permission efetiva do utilizador permitir `create`, `edit`, `view` ou `gerir`.
**Requirements**: TBD
**Depends on:** Phase 16
**Plans:** 1 plans

Plans:

- [x] 17-01: Ações UI com controlo por permissions

</details>

<details>
<summary>✅ v1.4 Melhoria módulo clientes (Phases 18-21) - SHIPPED 2026-06-03</summary>

### Phase 18: Clientes — Filtros avançados e UX/Performance

**Goal**: Melhorar a listagem de clientes com filtros avançados, pesquisa multi-campo e estados de carregamento/erro/vazio consistentes, reduzindo fricção e melhorando performance.
**Depends on**: Phase 17
**Requirements**: [CLI-21, CLI-26]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue filtrar por campos adicionais (ex.: tipo, estado, localidade) e pesquisar por múltiplos campos com debounce
  2. A listagem tem loading/empty/error states consistentes e UX refinada (sem flicker; feedback claro)
  3. A performance percebida melhora (ex.: menos renderizações desnecessárias; paginação/virtualização quando aplicável)

**Plans**: 1 plans

Plans:

- [x] 18-01: Clientes — Filtros avançados e UX/Performance

### Phase 19: Clientes — Contactos e Notas

**Goal**: Introduzir sub-recursos de Cliente (contactos e notas) no detalhe do cliente, com CRUD mínimo e integração no mock `/api/v1`.
**Depends on**: Phase 18
**Requirements**: [CLI-22]
**Success Criteria** (what must be TRUE):

  1. Detalhe do cliente permite listar/adicionar/editar/remover contactos
  2. Detalhe do cliente permite listar/adicionar/editar/remover notas
  3. As operações usam React Query + RHF/Zod e seguem padrões de toast/erro da app

**Plans**: 1 plans

Plans:

- [x] 19-01: Clientes — Contactos e Notas

### Phase 20: Clientes — Import/Export (CSV)

**Goal**: Permitir importação e exportação de clientes em CSV (respeitando filtros aplicados), com validação e feedback no UI.
**Depends on**: Phase 19
**Requirements**: [CLI-23, CLI-24]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue exportar CSV a partir da listagem de clientes (com filtros aplicados)
  2. Utilizador consegue importar CSV e ver resultados (sucessos/erros) com mensagens claras
  3. Importação não bloqueia o UI (feedback de progresso/estado) e segue convenções de segurança/validação

**Plans**: 1 plans

Plans:

- [x] 20-01: Clientes — Import/Export (CSV)

### Phase 21: Clientes — Merge de duplicados

**Goal**: Detectar e fundir clientes duplicados com fluxo guiado (regras por NIF/email/telefone) e integração no mock.
**Depends on**: Phase 20
**Requirements**: [CLI-25]
**Success Criteria** (what must be TRUE):

  1. UI apresenta candidatos a duplicado e permite selecionar registos a fundir
  2. Fluxo de merge preserva campos e resolve conflitos de forma explícita no UI
  3. A operação de merge é refletida na listagem e no detalhe do cliente com consistência

**Plans**: 1 plans

Plans:

- [x] 21-01: Clientes — Merge de duplicados

</details>

<details>
<summary>⏸ v1.5 Melhoria funcionalidades processos (Phases 22-27)</summary>

### Phase 22: Processos — Pesquisa/Filtros e UX/Performance

**Goal**: Melhorar a listagem de processos com pesquisa multi-campo, filtros avançados, sorting e UX/performance (prioridade produtividade).
**Depends on**: Phase 21
**Requirements**: [PRC-21]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue pesquisar por múltiplos campos do processo e filtrar por estados/tribunal/área (ou equivalentes do modelo)
  2. Listagem tem loading/empty/error states consistentes e feedback claro
  3. A performance percebida melhora (redução de renders e UI responsiva com datasets maiores)

**Plans**: 1 plans

Plans:

- [ ] 22-01: Processos — Pesquisa/Filtros e UX/Performance

### Phase 23: Processos — Anexos

**Goal**: Adicionar anexos por processo (upload/listagem/download/apagar) com controlo por permissions e integração com Documentos quando aplicável.
**Depends on**: Phase 22
**Requirements**: [PRC-22]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue anexar ficheiros a um processo e ver a lista de anexos
  2. Download funciona e apagar respeita permissions
  3. Upload tem feedback (progresso/sucesso/erro) consistente com o toaster

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 23 to break down)

### Phase 24: Processos — Checklist

**Goal**: Checklist/to-dos por processo com CRUD e estado, focado em produtividade; suportar seleção de relacionamentos (FKs) quando aplicável.
**Depends on**: Phase 23
**Requirements**: [PRC-23]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue criar/editar/remover tarefas e marcar concluído por processo
  2. UI suporta seleção de referências (FKs) com selects/pickers quando existir (ex.: vincular a evento/documento)
  3. A checklist é visível no detalhe do processo e atualiza sem refresh

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 24 to break down)

### Phase 25: Processos — Timeline

**Goal**: Timeline consolidada de eventos/movimentações/anexos/checklist (quando aplicável) para navegação rápida.
**Depends on**: Phase 24
**Requirements**: [PRC-24]
**Success Criteria** (what must be TRUE):

  1. Timeline mostra itens ordenados por data com metadados e links
  2. A timeline permite abrir rapidamente detalhe (ex.: evento, documento, movimentação)
  3. A UX é consistente (chips/ícones/estados) e suporta datasets maiores

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 25 to break down)

### Phase 26: Processos — Workflows/Estados

**Goal**: Implementar um modelo simples de estados e transições com validações por estado, reduzindo erros e aumentando produtividade.
**Depends on**: Phase 25
**Requirements**: [PRC-25]
**Success Criteria** (what must be TRUE):

  1. Existem estados definidos e transições permitidas (mínimo viável)
  2. UI aplica validações básicas conforme estado (ex.: campos obrigatórios/ações bloqueadas)
  3. Mudança de estado é auditável no UI (via timeline/movimentação)

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 26 to break down)

### Phase 27: Processos — Prazos/Agenda + Export

**Goal**: Reforçar integração Processos ↔ Agenda (prazos/alertas/templates simples) e exportar resumo do processo (PDF/CSV).
**Depends on**: Phase 26
**Requirements**: [AGD-21, PRC-26]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue criar prazos/eventos a partir do processo com UX rápida
  2. Existem alertas/indicadores mínimos de prazos (ex.: próximos/atrasados)
  3. Utilizador consegue exportar um resumo do processo (PDF/CSV) com dados principais

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 27 to break down)

</details>

<details>
<summary>⏸ v1.6 Melhoria nfeature de gestão de clientes (Phases 28-31)</summary>

## v1.6: Melhoria nfeature de gestão de clientes

### Phase 28: Clientes — Enriquecimento cadastral

**Goal**: Adicionar campos cadastrais novos (`categoria`, `ramoAtividade`, `documentoTipo`, `documentoNumero`, `detalhesAdicionais`) no backend, base de dados e formulários frontend.
**Depends on**: Phase 21
**Requirements**: [CLI-31]
**Success Criteria** (what must be TRUE):

  1. O modelo `Cliente` no backend inclui as novas propriedades e a persistência JPA funciona (novas colunas criadas).
  2. O formulário de criação/edição no frontend inclui selects para categoria, ramo de atividade, documento e campo de texto para número de documento e detalhes.
  3. A alteração é salva com sucesso e refletida na página de detalhe do cliente.

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 28 to break down)

### Phase 29: Clientes — Perfil Financeiro e Faturação

**Goal**: Adicionar campos de perfil financeiro (`valorHora`, `avencaMensal`, `moeda`, `iban`) no modelo do cliente, com formulário e aba dedicada no detalhe do cliente e validações apropriadas.
**Depends on**: Phase 28
**Requirements**: [CLI-32]
**Success Criteria** (what must be TRUE):

  1. O cliente possui os campos `valorHora`, `avencaMensal`, `moeda` e `iban` persistidos no backend.
  2. Existe uma aba "Perfil Financeiro" no detalhe do cliente com formulário que permite visualizar e editar estas definições.
  3. O campo IBAN possui validação básica de formato no formulário.

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 29 to break down)

### Phase 30: Clientes — Linha de Tempo de Atividades

**Goal**: Implementar a Linha de Tempo de Atividades (Timeline) no detalhe do cliente, agregando processos, notas, contactos, financeiro (honorários/pagamentos) e documentos associados ordenados por data.
**Depends on**: Phase 29
**Requirements**: [CLI-33]
**Success Criteria** (what must be TRUE):

  1. Existe um endpoint `/clientes/{id}/timeline` que retorna a lista unificada de eventos ordenados por data decrescente.
  2. A interface do detalhe do cliente inclui uma aba "Histórico" que apresenta os eventos em um design de timeline premium com ícones específicos para cada tipo de atividade.
  3. Cada item de timeline permite navegar/linkar para o recurso correspondente (ex: abrir o detalhe do processo).

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 30 to break down)

### Phase 31: Clientes — Conflict Check Visual

**Goal**: Implementar a funcionalidade de Conflict Check visual e validação de NIF/IBAN na criação/edição de clientes para alertar o utilizador sobre duplicados ou potenciais conflitos de interesse.
**Depends on**: Phase 30
**Requirements**: [CLI-34]
**Success Criteria** (what must be TRUE):

  1. Ao preencher o NIF ou Nome no formulário de cliente, a aplicação dispara uma consulta para verificar duplicados e exibe um alerta inline claro e não-bloqueante se encontrar registos correspondentes.
  2. A validação do NIF para Cabo Verde (número de 9 dígitos e algoritmos básicos se aplicável) é imposta no frontend e backend.

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 31 to break down)

</details>

<details>
<summary>🏃 v1.7 Melhoria no modulo de gestao e acompanhamento de processos (Phases 32-36)</summary>

## v1.7: Melhoria no modulo de gestao e acompanhamento de processos

### Phase 32: Processos - Intake e Conflict Check

**Goal**: Formalizar a abertura de processos com intake estruturado e conflict check bloqueante antes da criacao formal do matter/processo.
**Depends on**: Phase 31
**Requirements**: [INT-01, CFL-01]
**Success Criteria** (what must be TRUE):

  1. Utilizador consegue registar um potencial cliente e preencher intake estruturado com os campos minimos definidos para o tipo de processo
  2. Abertura formal do processo fica bloqueada enquanto nao existir conflict check concluido com decisao registada
  3. O estado da triagem e da decisao de abertura fica visivel no UI e consistente entre listagem e detalhe

**Plans**: 3 plans

Plans:
**Wave 1**

- [x] 32-01-PLAN.md — Backend: entidade/decisao, RBAC, endpoints intake/conflict-check/decisao/formalizar com bloqueio server-side

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 32-02-PLAN.md — Frontend data layer: tipos, schema Zod, hooks TanStack Query, utilitario de nivel

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 32-03-PLAN.md — Frontend UI: wizard 3 passos, badge/filtro EM TRIAGEM, seccao conflict check no detalhe

### Phase 33: Processos - Workflow, Gates e Prazos

**Goal**: Estruturar o acompanhamento do processo com estados, gates de validacao, responsaveis e prazos operacionais com risco e escalonamento.
**Depends on**: Phase 32
**Requirements**: [PRC-27, AGD-22]
**Success Criteria** (what must be TRUE):

  1. Cada processo utiliza estados definidos com transicoes permitidas e validacoes minimas por gate
  2. O UI mostra responsavel atual, proximo passo e obrigatorios por estado para reduzir erros operacionais
  3. Prazos operacionais ficam associados ao processo com prioridade, risco e sinalizacao de proximidade ou atraso

**Plans**: 3 plans

Plans:
**Wave 1**

- [x] 33-01-PLAN.md — Backend: entidade Prazo, responsavel_id, DTOs, maquina de estados + gates server-side, endpoints workflow/transicao/prazos + enriquecimento da listagem

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 33-02-PLAN.md — Frontend data layer: tipos, schemas Zod (justificativa/prazo), lib/prazos.ts (risco->badge), hooks TanStack Query

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 33-03-PLAN.md — Frontend UI: instala Dialog+Textarea, Workflow card + Prazos card + Dialogs no detalhe, sinais de responsavel/risco na listagem

### Phase 34: Processos - Timeline e Auditoria

**Goal**: Unificar a leitura historica do processo e tornar a operacao rastreavel com timeline funcional e trilha auditavel de eventos sensiveis.
**Depends on**: Phase 33
**Requirements**: [PRC-28, AUD-02]
**Success Criteria** (what must be TRUE):

  1. Timeline do processo agrega movimentacoes, eventos, tarefas, documentos e decisoes em ordem cronologica navegavel
  2. Eventos auditaveis sensiveis ficam registados com utilizador, acao, alvo e timestamp
  3. Utilizador consegue filtrar a linha temporal por tipo de evento, periodo e criticidade

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 34 to break down)

### Phase 35: Processos - Governanca Documental e Retencao

**Goal**: Fortalecer a governanca documental do processo com classificacao, versao, confidencialidade, retencao e legal hold.
**Depends on**: Phase 34
**Requirements**: [DOC-11, DOC-12]
**Success Criteria** (what must be TRUE):

  1. Documentos do processo possuem categoria, confidencialidade, versao e metadados obrigatorios consistentes
  2. O sistema suporta regras de retencao e legal hold por processo/documento com bloqueio de eliminacao quando aplicavel
  3. O estado documental fica visivel no detalhe do processo e utilizavel em filtros/operacao

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 35 to break down)

### Phase 36: Processos - Dashboards e KPI Executivo

**Goal**: Expor acompanhamento operacional e executivo do modulo de processos com backlog, SLAs, risco e conformidade em paineis orientados a decisao.
**Depends on**: Phase 35
**Requirements**: [MON-01, KPI-01]
**Success Criteria** (what must be TRUE):

  1. Existe painel operacional com backlog por responsavel, prazos criticos e processos sem atualizacao recente
  2. Existe painel executivo com tempos medios, conflitos detetados, conformidade documental e exposicao por carteira
  3. Os indicadores usam dados reais do sistema e permitem navegar para a lista/detalhe correspondente

**Plans**: 0 plans

Plans:

- [ ] TBD (run /gsd-plan-phase 36 to break down)

</details>

## Progress

**Execution Order:**
Phases executam em ordem numérica: 7 → 8 → 9 → 10 → 11 → 12 → 13 → 14 → 15 → 16 → 17 → 18 → 19 → 20 → 21 → 22 → 23 → 24 → 25 → 26 → 27 → 28 → 29 → 30 → 31 → 32 → 33 → 34 → 35 → 36

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 1. Foundation (UI + Mock API + Auth) | v1.0 | 3/3 | Complete | 2026-05-26 |
| 2. Clientes | v1.0 | 2/2 | Complete | 2026-05-26 |
| 3. Processos | v1.0 | 3/3 | Complete | 2026-05-26 |
| 4. Agenda | v1.0 | 2/2 | Complete | 2026-05-26 |
| 5. Documentos | v1.0 | 2/2 | Complete | 2026-05-26 |
| 6. Financeiro | v1.0 | 2/2 | Complete | 2026-05-26 |
| 7. Shell & Design System | v1.1 | 2/2 | Complete | 2026-05-27 |
| 8. Dashboard + Clientes + Processos (UI) | v1.1 | 2/2 | Complete | 2026-05-27 |
| 9. Agenda & Prazos (UI) | v1.1 | 1/1 | Complete | 2026-05-27 |
| 10. Dark/Light Mode & Layout Adjustments | v1.1 | 1/1 | Complete | 2026-05-27 |
| 11. Painel de Utilizador | v1.2 | 1/1 | Complete | 2026-05-27 |
| 12. Infra & Security Tooling | v1.3 | 1/1 | Complete | 2026-05-27 |
| 13. Data Isolation & RLS | v1.3 | 1/1 | Complete | 2026-05-27 |
| 14. Auth & Session Hardening | v1.3 | 1/1 | Complete | 2026-05-27 |
| 15. AppSec & RBAC | v1.3 | 1/1 | Complete | 2026-05-27 |
| 16. Notificação e Feedback (Toaster) | v1.3 | 1/1 | Complete | 2026-05-27 |
| 17. Ações UI com controlo por permissions | v1.3 | 1/1 | Complete | 2026-06-03 |
| 18. Clientes — Filtros avançados e UX/Performance | v1.4 | 1/1 | Complete | 2026-06-03 |
| 19. Clientes — Contactos e Notas | v1.4 | 1/1 | Complete | 2026-06-03 |
| 20. Clientes — Import/Export (CSV) | v1.4 | 1/1 | Complete | 2026-06-03 |
| 21. Clientes — Merge de duplicados | v1.4 | 1/1 | Complete | 2026-06-03 |
| 22. Processos — Pesquisa/Filtros e UX/Performance | v1.5 | 0/1 | Paused | — |
| 23. Processos — Anexos | v1.5 | 0/0 | Paused | — |
| 24. Processos — Checklist | v1.5 | 0/0 | Paused | — |
| 25. Processos — Timeline | v1.5 | 0/0 | Paused | — |
| 26. Processos — Workflows/Estados | v1.5 | 0/0 | Paused | — |
| 27. Processos — Prazos/Agenda + Export | v1.5 | 0/0 | Paused | — |
| 28. Clientes — Enriquecimento cadastral | v1.6 | 0/0 | Planned | — |
| 29. Clientes — Perfil Financeiro e Faturação | v1.6 | 0/0 | Planned | — |
| 30. Clientes — Linha de Tempo de Atividades | v1.6 | 0/0 | Planned | — |
| 31. Clientes — Conflict Check Visual | v1.6 | 0/0 | Planned | — |
| 32. Processos - Intake e Conflict Check | v1.7 | 3/3 | Complete    | 2026-06-14 |
| 33. Processos - Workflow, Gates e Prazos | v1.7 | 3/3 | Complete   | 2026-06-16 |
| 34. Processos - Timeline e Auditoria | v1.7 | 0/0 | Planned | — |
| 35. Processos - Governanca Documental e Retencao | v1.7 | 0/0 | Planned | — |
| 36. Processos - Dashboards e KPI Executivo | v1.7 | 0/0 | Planned | — |
