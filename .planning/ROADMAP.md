# Roadmap: LexCV

## Milestones

- ✅ **v2.0 Módulo Financeiro** — Phases 43–46 (complete 2026-06-18)
- ✅ **v2.1 Agenda Avançada** — Phases 47–49 (complete 2026-06-18)
- ✅ **v2.2 Document Storage MinIO** — Phases 50–52 (complete 2026-06-19)
- ✅ **v2.3 Responsividade App** — Phases 53–56 (complete 2026-06-21)
- ✅ **v2.4 Ficha de Cliente** — Phases 57–60 (complete 2026-06-30)
- ✅ **v2.5 Módulo de Parecer Jurídico** — Phases 61–64 (complete 2026-06-30)
- ✅ **v2.6 Módulo de Parecer Jurídico — UI** — Phases 65–69 (complete 2026-07-01)
- ⬜ **v2.7 Melhoria Gestão de Clientes** — Phases 70–73 (active)


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

### v2.7 Melhoria Gestão de Clientes

**Milestone Goal:** Simplificar e aplanar o modelo de identificação e contactos de clientes particulares e empresas (remoção de `dados_tipo` JSON em prol de colunas diretas da tabela).

- [ ] **Phase 70: Backend refactoring & Seeder Alignment** - Adicionar REG_COMERCIAL a DocumentoTipo, remover dadosTipo e conversor de Cliente, e corrigir o DatabaseSeeder (CLI-06, CLI-09)
- [ ] **Phase 71: Frontend Types, Schema & API Integration** - Aplanar tipos TypeScript, remover dados_tipo e tornar NIF obrigatório com validação de 9 dígitos no Zod schema (CLI-05, CLI-06)
- [ ] **Phase 72: Form Refactoring (Create & Edit)** - Simplificar os formulários de criação e edição com campos planos e labels dinâmicas para Morada/Sede (CLI-05, CLI-07, CLI-08, CLI-09, CLI-10)
- [ ] **Phase 73: Detail Page & Printable Ficha Update** - Adaptar a página de detalhe e a ficha impressa para a estrutura simplificada (CLI-11)

**UI hint**: yes

### Phase 70: Backend refactoring & Seeder Alignment
**Goal**: O backend armazena a identificação do cliente exclusivamente em colunas planas (sem `dados_tipo` JSON), suporta `REG_COMERCIAL` como tipo de documento para Empresa, e o DatabaseSeeder gera dados de seed consistentes com o novo modelo
**Depends on**: Nothing (first phase of milestone)
**Requirements**: CLI-06, CLI-09
**Plans**: 1 plan
- [ ] 70-01-PLAN.md — Remove dados_tipo JSON model, add REG_COMERCIAL enum, align DatabaseSeeder (CLI-06, CLI-09)

### Phase 71: Frontend Types, Schema & API Integration
**Goal**: Os tipos TypeScript do cliente refletem o modelo aplanado do backend (sem `dados_tipo`), e o Zod schema exige NIF obrigatório com validação de 9 dígitos numéricos
**Depends on**: Phase 70
**Requirements**: CLI-05, CLI-06
**Plans**: 0/1 — not started

### Phase 72: Form Refactoring (Create & Edit)
**Goal**: Os formulários de criação e edição de cliente usam campos planos (sem seletor de card JSON) com labels dinâmicas ("Morada" para Particular, "Sede" para Empresa) e validação de NIF/REG_COMERCIAL
**Depends on**: Phase 71
**Requirements**: CLI-05, CLI-07, CLI-08, CLI-09, CLI-10
**Plans**: 0/2 — not started

### Phase 73: Detail Page & Printable Ficha Update
**Goal**: A página de detalhe do cliente e a ficha imprimível apresentam apenas a estrutura de dados simplificada, sem referências ao antigo `dados_tipo`
**Depends on**: Phase 72
**Requirements**: CLI-11
**Plans**: 0/1 — not started

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
| 70. Backend refactoring & Seeder Alignment | v2.7 | 0/1 | Pending | — |
| 71. Frontend Types, Schema & API Integration | v2.7 | 0/1 | Pending | — |
| 72. Form Refactoring (Create & Edit) | v2.7 | 0/2 | Pending | — |
| 73. Detail Page & Printable Ficha Update | v2.7 | 0/1 | Pending | — |

**Next:** Milestone v2.7 active. Run `/gsd-discuss-phase 70` to plan the first phase.

</content>
