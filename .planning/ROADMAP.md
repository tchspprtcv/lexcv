# Roadmap: LexCV

## Milestones

- ✅ **v2.0 Módulo Financeiro** — Phases 43–46 (complete 2026-06-18)
- ✅ **v2.1 Agenda Avançada** — Phases 47–49 (complete 2026-06-18)
- ✅ **v2.2 Document Storage MinIO** — Phases 50–52 (complete 2026-06-19)
- ✅ **v2.3 Responsividade App** — Phases 53–56 (complete 2026-06-21)
- ✅ **v2.4 Ficha de Cliente** — Phases 57–60 (complete 2026-06-30)
- ✅ **v2.5 Módulo de Parecer Jurídico** — Phases 61–64 (complete 2026-06-30)

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

**Next:** Run `/gsd:plan-phase 61` to begin Phase 61 planning.
