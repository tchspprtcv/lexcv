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
- 🚧 **v2.8 Refatoração Ficha de Cliente** — Phases 74–79 (in progress)


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

See archive: [milestones/v2.4-ROADMAP.md](milestones/v2.4-ROADMAP.md) · [milestones/v2.4-MILESTONE-AUDIT.md](milestones/v2.4-MILESTONE-AUDIT.md)

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

### 🚧 v2.8 Refatoração Ficha de Cliente (In Progress)

**Milestone Goal:** Transformar a ficha de cliente no formulário central de pesquisa de informação relacionada ao cliente — unificando visualização/edição num único componente e adicionando separadores (tabs) que cobrem processos, pareceres e documentos, seguindo a disposição visual de processos.

#### Phase 74: Enum `documento_tipo` (BI/NIF/Restrição por Tipo)
**Goal**: O tipo de documento de identificação do cliente reflete corretamente as opções válidas por tipo de cliente, em backend e frontend
**Depends on**: Nothing (first phase of milestone)
**Requirements**: CLI-20, CLI-21, CLI-22, CLI-23, CLI-24
**Success Criteria** (what must be TRUE):
  1. Utilizador vê `BI` como opção de tipo de documento para cliente Particular
  2. Utilizador já não vê `NIF` como opção de tipo de documento (removido do enum)
  3. Ao criar/editar cliente Particular, o dropdown de tipo de documento mostra apenas CNI/BI/Passaporte
  4. Ao criar/editar cliente Empresa, o dropdown de tipo de documento mostra apenas Registo Comercial
  5. Submeter uma combinação inválida (ex.: Empresa com tipo de documento de Particular) é rejeitada pelo backend com erro claro
**Plans**: 5 plans (2 gap-closure)
- [x] 74-01-PLAN.md — Backend enum (BI/-NIF), tipo×documento_tipo validation, defensive NIF cleanup SQL
- [x] 74-02-PLAN.md — Frontend DocumentoTipo type + shared cliente-documento-tipo options module
- [x] 74-03-PLAN.md — Filtered dropdown + Zod validation in both cliente form pages
- [x] 74-04-PLAN.md — Gap closure: parameterize clienteFormSchema so legacy documento_tipo can be saved on edit (CR-01)
- [x] 74-05-PLAN.md — Gap closure: backend updateCliente tolerates unchanged legacy documento_tipo (skip validation only when field is byte-for-byte unchanged from stored entity)

#### Phase 75: Componente Único View/Edit
**Goal**: A ficha de cliente é uma única página que alterna entre modo leitura e edição, sem rota dedicada de edição
**Depends on**: Phase 74
**Requirements**: CLI-12, CLI-13, CLI-14
**Success Criteria** (what must be TRUE):
  1. Utilizador visualiza os dados do cliente em `/clientes/[id]` em modo leitura por defeito
  2. Utilizador clica "Editar" e os mesmos campos tornam-se editáveis na mesma página (sem navegação)
  3. Em modo leitura, controlos de edição (inputs/selects/guardar/cancelar/adicionar/remover) estão inativos ou ocultos
  4. Aceder a `/clientes/[id]/editar` deixa de existir como rota separada
**Plans**: 3 plans (2 waves)
- [x] 75-01-PLAN.md — Merge edit-mode logic + isEditing toggle into [id]/page.tsx; delete /editar route (Wave 1)
- [x] 75-02-PLAN.md — Add editable prop to the 4 inline sub-cards, AND-gate CRUD affordances (Wave 2)
- [x] 75-03-PLAN.md — Repoint the 2 Editar pencil links in clientes list page to /clientes/[id] (Wave 1)
**UI hint**: yes

#### Phase 76: Separadores — Dados, Contactos e Notas
**Goal**: A ficha de cliente organiza a informação em separadores, com identificação incluída no card "Dados" principal
**Depends on**: Phase 75
**Requirements**: CLI-15, CLI-18, CLI-19
**Success Criteria** (what must be TRUE):
  1. Utilizador vê 7 separadores na ficha do cliente: Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações
  2. Separador "Dados" apresenta NIF, tipo de documento e número de documento como parte do card principal, respeitando toggle view/edit
  3. Separador "Contactos e Notas" apresenta os mesmos cards de Contactos e Notas anteriormente na página principal, agora isolados no seu separador
**Plans**: 1 plan (1 wave)
- [x] 76-01-PLAN.md — Tab shell (7 botões, estilo processos), conteúdo real para Dados (com sub-secção Identificação) e Contactos e Notas, placeholders "Em breve" para os outros 5
**UI hint**: yes

#### Phase 77: Separadores — Processos e Pareceres
**Goal**: O utilizador consulta os processos e pareceres do cliente diretamente a partir da ficha do cliente
**Depends on**: Phase 76
**Requirements**: CLI-16, CLI-17
**Success Criteria** (what must be TRUE):
  1. Separador "Processos" lista os processos associados ao cliente (via `useProcessos({cliente_id})`)
  2. Separador "Pareceres" lista os pareceres associados ao cliente (via `usePareceres({clienteId})`)
**Plans**: 1 plan (1 wave)
- [x] 77-01-PLAN.md — Separadores Processos e Pareceres: sub-componentes ClienteProcessosTab/ClienteParecerTab (lazy-mount, listagem compacta 4 colunas, badge de estado, navegação para detalhe)
**UI hint**: yes

#### Phase 78: Separadores — Documentos a Tratar e Deslocações
**Goal**: As listas de documentos a tratar e deslocações do cliente ficam isoladas nos seus próprios separadores, mantendo o comportamento atual
**Depends on**: Phase 76
**Requirements**: CLI-30, CLI-31
**Success Criteria** (what must be TRUE):
  1. Separador "Documentos a Tratar" mantém a lista de texto (descrição+data) atual, agora isolada no seu separador
  2. Separador "Deslocações" mantém a lista de texto (descrição/local/data) atual, agora isolada no seu separador
**Plans**: 1 plan (1 wave)
- [x] 78-01-PLAN.md — Relocate "Documentos a Tratar" + "Deslocações" JSX from the "Dados" tab into their own tab branches (isEditing-gated, replacing PlaceholderEmBreve); extend the dialog-reset useEffect per-dialog
**UI hint**: yes

#### Phase 79: Documentos Entregues — Upload Real
**Goal**: O separador "Documentos Entregues" passa a gerir ficheiros carregados de facto, reutilizando o sistema genérico de Documentos
**Depends on**: Phase 76
**Requirements**: CLI-25, CLI-26, CLI-27, CLI-28, CLI-29
**Success Criteria** (what must be TRUE):
  1. Utilizador carrega um ficheiro no separador "Documentos Entregues" via o sistema genérico `Documento`/`/documentos/upload` associado ao `clienteId`
  2. Separador lista os documentos já carregados para o cliente (via novo endpoint de listagem por cliente)
  3. Ao carregar um documento, o campo "tipo" é um combobox que permite escolher um tipo existente ou escrever um novo
  4. Registos antigos de "documentos entregues" (texto sem ficheiro) deixam de ser editáveis na nova UI, sem processo de migração
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
| 78. Separadores — Documentos a Tratar e Deslocações | v2.8 | 1/1 | Complete   | 2026-07-06 |
| 79. Documentos Entregues — Upload Real | v2.8 | 0/? | Not started | - |

**Next:** Phase 77 planned (1 plan, 1 wave). Run `/gsd:execute-phase 77`.
