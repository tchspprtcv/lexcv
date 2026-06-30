# Roadmap: LexCV

## Milestones

- ✅ **v2.0 Módulo Financeiro** — Phases 43–46 (complete 2026-06-18)
- ✅ **v2.1 Agenda Avançada** — Phases 47–49 (complete 2026-06-18)
- ✅ **v2.2 Document Storage MinIO** — Phases 50–52 (complete 2026-06-19)
- ✅ **v2.3 Responsividade App** — Phases 53–56 (complete 2026-06-21)
- ✅ **v2.4 Ficha de Cliente** — Phases 57–60 (complete 2026-06-30)
- 🚧 **v2.5 Módulo de Parecer Jurídico** — Phases 61–64 (in progress)

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

### 🚧 v2.5 Módulo de Parecer Jurídico (IN PROGRESS)

**Milestone Goal:** Gerir o ciclo completo de pareceres jurídicos (solicitação → elaboração com versionamento → aprovação opcional → entrega), com auditoria automática e pesquisa avançada, reutilizando `Cliente`, `User`+role `ADVOGADO`, `AuditLog` e `StorageService` já existentes.

- [x] **Phase 61: Data Layer + Backend CRUD** - Entidades ParecerSolicitacao/ParecerVersao, scope RBAC `pareceres:*`, e CRUD completo de solicitações via API
 (completed 2026-06-30)
- [x] **Phase 62: Elaboração e Versionamento** - UI de criação/edição de versões com conteúdo, anexo e histórico de autor/data (completed 2026-06-30)
- [ ] **Phase 63: Aprovação e Entrega** - Fluxo de aprovação interna opcional e entrega final, com disponibilização para consulta/download
- [ ] **Phase 64: Auditoria e Pesquisa Avançada** - Integração com AuditLog em todos os pontos de escrita e pesquisa textual + filtros combinados

## Phase Details

### Phase 61: Data Layer + Backend CRUD
**Goal**: Existe uma base de dados e API funcionais para criar, atribuir e listar solicitações de parecer, com RBAC dedicado
**Depends on**: Nothing (first phase of milestone)
**Requirements**: PARC-01, PARC-02, PARC-03, PARC-04, PARC-05, PARC-06, PARC-10
**Success Criteria** (what must be TRUE):
  1. Utilizador com permissão `pareceres:create` pode criar uma solicitação de parecer com cliente, descrição, data, prazo desejado e urgência
  2. Solicitação pode ser opcionalmente associada a um Processo existente
  3. Utilizador pode atribuir/reatribuir um advogado responsável (User com role ADVOGADO) à solicitação
  4. Solicitação expõe um status (PENDENTE, EM_ELABORACAO, EM_REVISAO, CONCLUIDO) que reflete o seu progresso
  5. Utilizador pode listar e filtrar solicitações por cliente, advogado e status, e ver o detalhe de uma solicitação específica
**Plans**: 2 plans
  - [x] 61-01-PLAN.md — Entidade ParecerSolicitacao, repositório e seeding RBAC pareceres:*
  - [x] 61-02-PLAN.md — ParecerController CRUD + atribuição de advogado com validação de papel ADVOGADO

### Phase 62: Elaboração e Versionamento
**Goal**: O advogado responsável consegue elaborar o parecer em versões sucessivas, cada uma com conteúdo, anexo opcional e histórico rastreável
**Depends on**: Phase 61
**Requirements**: PARV-01, PARV-02, PARV-03, PARV-04
**Success Criteria** (what must be TRUE):
  1. Advogado responsável pode criar uma nova versão do parecer com conteúdo textual e anexo opcional
  2. Cada versão regista automaticamente número sequencial, autor e data de criação
  3. Utilizador pode consultar a lista de versões anteriores de uma solicitação e abrir cada uma para comparação
  4. Upload/download de anexos de versão usa o mesmo StorageService/padrão de Documentos já existente no LexCV
**Plans**: 2 plans
  - [x] 62-01-PLAN.md — Entidade ParecerVersao + repositório (numeração sequencial por solicitação)
  - [x] 62-02-PLAN.md — Endpoints /versoes no ParecerController (criar com multipart, listar, detalhe, download de anexo via StorageService)
**UI hint**: yes

### Phase 63: Aprovação e Entrega
**Goal**: O parecer pode ser revisto internamente antes de ser entregue, e uma vez entregue fica disponível para consulta pela equipa/cliente
**Depends on**: Phase 62
**Requirements**: PARC-07, PARC-08, PARC-09
**Success Criteria** (what must be TRUE):
  1. Utilizador com papel de supervisor/ADMIN pode marcar uma versão específica como aprovada internamente (passo opcional)
  2. Utilizador pode marcar a versão final como entregue, o que altera o status da solicitação para CONCLUIDO
  3. Parecer entregue fica visível/descarregável para consulta pela equipa (e, conforme RBAC, pelo cliente)
**Plans**: 1 plan
  - [ ] 63-01-PLAN.md — Campos aprovado/versaoFinalId nas entidades + endpoints /aprovar e /entregar no ParecerController
**UI hint**: yes

### Phase 64: Auditoria e Pesquisa Avançada
**Goal**: Todas as ações relevantes sobre pareceres ficam auditadas automaticamente e qualquer parecer pode ser encontrado por texto livre combinado com filtros
**Depends on**: Phase 63
**Requirements**: PARA-01, PARS-01, PARS-02
**Success Criteria** (what must be TRUE):
  1. Criar, atribuir, editar versão, aprovar e entregar um parecer geram automaticamente um registo em AuditLog (`entidadeTipo`: `parecer_solicitacao`/`parecer_versao`), visível no histórico/timeline já existente
  2. Utilizador pode pesquisar pareceres por texto livre no conteúdo das versões
  3. Pesquisa por texto livre pode ser combinada com filtros de cliente, advogado, status e data simultaneamente
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
| 63. Aprovação e Entrega | v2.5 | 0/1 | Not started | - |
| 64. Auditoria e Pesquisa Avançada | v2.5 | 0/TBD | Not started | - |

**Next:** Run `/gsd:plan-phase 61` to begin Phase 61 planning.
