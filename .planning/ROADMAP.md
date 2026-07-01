# Roadmap: LexCV

## Milestones

- ✅ **v2.0 Módulo Financeiro** — Phases 43–46 (complete 2026-06-18)
- ✅ **v2.1 Agenda Avançada** — Phases 47–49 (complete 2026-06-18)
- ✅ **v2.2 Document Storage MinIO** — Phases 50–52 (complete 2026-06-19)
- ✅ **v2.3 Responsividade App** — Phases 53–56 (complete 2026-06-21)
- ✅ **v2.4 Ficha de Cliente** — Phases 57–60 (complete 2026-06-30)
- ✅ **v2.5 Módulo de Parecer Jurídico** — Phases 61–64 (complete 2026-06-30)
- 🚧 **v2.6 Módulo de Parecer Jurídico — UI** — Phases 65–69 (in progress)

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

### 🚧 v2.6 Módulo de Parecer Jurídico — UI (In Progress)

**Milestone Goal:** Entregar a interface frontend para o módulo de Parecer Jurídico, tornando utilizável através da aplicação LexCV o ciclo completo (solicitação → elaboração/versionamento → entrega → pesquisa/auditoria) já implementado como API no v2.5. Pura integração frontend sobre um backend já auditado — sem novo trabalho de backend em âmbito.

#### Phase 65: Fundação — Listagem e Detalhe
**Goal**: Utilizador consegue observar o estado de qualquer solicitação de parecer através da aplicação — lista, detalhe e histórico de versões — sobre uma camada de tipos verificada contra o contrato JSON real do backend (evitando o defeito camelCase/snake_case do v2.4).
**Depends on**: Nothing (first phase of milestone; consome API já entregue no v2.5)
**Requirements**: PARC-11, PARC-12
**Success Criteria** (what must be TRUE):
  1. Utilizador com permissão `pareceres:view` vê `/pareceres` com tabela (desktop) e cards (mobile) das solicitações, com status badges e filtros por status, advogado e cliente/processo
  2. Utilizador consegue abrir o detalhe de uma solicitação (`/pareceres/[id]`) e ver todos os campos vindos do backend correctamente (nenhum campo em branco por mismatch de casing)
  3. Utilizador vê, no detalhe, uma timeline imutável das versões existentes (autor, data, conteúdo/anexo) mesmo que ainda não exista nenhuma versão
  4. Item de navegação "Pareceres" aparece na sidebar apenas para utilizadores com `pareceres:view`, seguindo o padrão RBAC de nav já usado noutros módulos
**Plans**: 2 plans

Plans:
- [x] 65-01-PLAN.md — Data foundation: types (pure camelCase), Zod schema stubs, TanStack Query hooks, sidebar nav item (wave 1)
- [x] 65-02-PLAN.md — Read-only UI: /pareceres list (dual-view, badges, filters) + /pareceres/[id] detail with version timeline (wave 2)

#### Phase 66: Criação de Solicitação
**Goal**: Utilizador consegue iniciar o ciclo de vida de um parecer diretamente na aplicação, atribuindo um advogado responsável.
**Depends on**: Phase 65
**Requirements**: PARC-13
**Success Criteria** (what must be TRUE):
  1. Utilizador com permissão `pareceres:create` consegue criar uma solicitação via formulário, vinculando um cliente (obrigatório) e opcionalmente um processo
  2. Utilizador consegue atribuir um advogado responsável através de um seletor de utilizadores (user-picker), reutilizando o padrão de vínculo a Users já usado na Ficha Cliente
  3. Após submissão, a nova solicitação aparece imediatamente na lista de `/pareceres` (invalidação de cache correta)
**Plans**: 1 plan
- [x] 66-01-PLAN.md — Formulário de criação de solicitação (schema + useCreateParecer + página /pareceres/nova + CTA na lista)
**Note (2026-07-01):** NOTF-05 (notificação in-app de atribuição) removida do âmbito — não existe sistema de notificações genérico no backend (o `NotificationBell` da v2.1 é específico da Agenda); ver v2 Requirements/Notificações em REQUIREMENTS.md.

#### Phase 67: Elaboração e Versionamento
**Goal**: O advogado responsável consegue efetivamente elaborar o parecer através da aplicação, submetendo versões sucessivas e imutáveis com anexo.
**Depends on**: Phase 66
**Requirements**: PARV-05, PARV-06
**Success Criteria** (what must be TRUE):
  1. Advogado responsável consegue submeter uma nova versão através de um formulário com campo de resumo (`conteúdo`) e upload de anexo — e o formulário bloqueia a submissão se nenhum anexo for fornecido (anexo obrigatório na UI, mais restritivo que o backend)
  2. O upload de anexo reutiliza o componente já existente do módulo Documentos (barra de progresso, drag-and-drop, armazenamento MinIO), sem duplicar lógica
  3. Após submissão, a nova versão aparece imediatamente na timeline do detalhe da solicitação, em ordem sequencial, sem opção de editar/eliminar versões anteriores (imutabilidade visível na UI)
**Plans**: TBD
**Note (2026-07-01):** NOTF-06 removida do âmbito pela mesma razão indicada na Phase 66.

#### Phase 68: Entrega, Vista de Entregue e RBAC
**Goal**: Utilizador autorizado consegue concluir o ciclo do parecer marcando uma versão como entrega final — com clareza total sobre a irreversibilidade — e qualquer pessoa autorizada consegue depois consultar o parecer entregue como um documento final e íntegro, com todas as ações da interface a respeitar exatamente as mesmas regras RBAC do backend.
**Depends on**: Phase 67
**Requirements**: PARC-14, PARC-15, PARC-16
**Success Criteria** (what must be TRUE):
  1. Advogado responsável ou ADMIN consegue marcar uma versão como entrega final através de um diálogo de confirmação que enfatiza explicitamente que a ação é irreversível
  2. Após a entrega, a solicitação passa a expor uma vista dedicada "Parecer Entregue" (versão final via `versaoFinalId`, data/autor de entrega, anexo) — fechando o gap PARC-09 identificado na auditoria do v2.5
  3. Uma solicitação já entregue (`CONCLUIDO`) é tratada como só-leitura na UI (sem botões de nova versão/edição visíveis), independentemente de o backend garantir ou não essa restrição em todos os endpoints
  4. Botões de ação (criar solicitação, criar versão, entregar) só aparecem/ficam ativos conforme `hasScopedPermission(perms, "pareceres", action)` combinado com verificação de instância onde aplicável (ADMIN ou advogado responsável para versionar/entregar) — espelhando exatamente os `@PreAuthorize` do `ParecerController`
**Plans**: 1 plan

Plans:
- [x] 68-01-PLAN.md — Entrega irreversível (AlertDialog + selector de versão), bloco "Parecer Entregue", auditoria RBAC/só-leitura, e correções module-wide de CardTitle + timeline-dot

**UI hint**: yes
**Note (2026-07-01):** NOTF-07 removida do âmbito pela mesma razão indicada na Phase 66.

#### Phase 69: Pesquisa Avançada
**Goal**: Utilizador consegue localizar pareceres relevantes combinando texto livre e filtros estruturados, aproveitando a capacidade de pesquisa já construída (e não usada) no backend.
**Depends on**: Phase 68
**Requirements**: PARS-03
**Success Criteria** (what must be TRUE):
  1. Utilizador consegue pesquisar pareceres por texto livre combinado com filtros de cliente, advogado, status e data, numa página/vista dedicada
  2. Os resultados da pesquisa refletem exatamente o comportamento do endpoint `pesquisar()` do backend (mesma lógica de combinação texto+filtros), sem duplicar regras de negócio no frontend
  3. A pesquisa usa um namespace de cache próprio (TanStack Query), sem conflito com a lista simples de `/pareceres`
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
| 69. Pesquisa Avançada | v2.6 | 0/TBD | Not started | - |

**Next:** Run `/gsd:plan-phase 65` to begin Phase 65 planning.
</content>
