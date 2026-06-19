# Roadmap: LexCV

## Milestones

- ✅ **v2.0 Módulo Financeiro** — Phases 43–46 (complete 2026-06-18)
- ✅ **v2.1 Agenda Avançada** — Phases 47–49 (complete 2026-06-18)
- 🔄 **v2.2 Document Storage MinIO** — Phases 50–52 (active)

## Phases

<details>
<summary>✅ v2.0 Módulo Financeiro (Phases 43–46) - SHIPPED 2026-06-18</summary>

### Phase 43: Data Layer + Backend Endpoints
**Goal**: O contrato de dados entre frontend e backend está correto (camelCase) e o CRUD completo de honorários e pagamentos está disponível via API
**Depends on**: Nothing (first phase of milestone)
**Requirements**: FIN-01, FIN-02, FIN-03, FIN-04, FIN-05, FIN-06
**Success Criteria** (what must be TRUE):
  1. O frontend envia e recebe campos camelCase (`processoId`, `valorTotal`, `dataAcordo`, `honorarioId`, `valorPago`, `dataPagamento`) sem erros de serialização
  2. Os tipos TypeScript `Honorario`, `Pagamento`, `HonorarioCreateRequest`, `PagamentoCreateRequest` não contêm campos snake_case
  3. Utilizador pode consultar um honorário individual via `GET /honorarios/{id}` com tenant scoping correto
  4. Utilizador com `financeiro:edit` pode editar um honorário via `PUT /honorarios/{id}`
  5. Utilizador com `financeiro:manage` pode apagar um honorário (sem pagamentos) e um pagamento com reversão de saldo
**Plans**: 2 plans
Plans:
- [x] 43-01-PLAN.md — Frontend camelCase migration: types, schemas, hooks, page components
- [x] 43-02-PLAN.md — Backend missing endpoints: GET/PUT/DELETE /honorarios/{id}, DELETE /pagamentos/{id}
**UI hint**: yes

### Phase 44: Status + KPIs
**Goal**: A página financeiro apresenta o estado calculado de cada honorário e um resumo financeiro em cards no topo
**Depends on**: Phase 43
**Requirements**: FIN-07, FIN-08, FIN-09, FIN-10
**Success Criteria** (what must be TRUE):
  1. Cada honorário na lista mostra um badge com estado: `Pendente`, `Parcialmente Pago` ou `Pago`, com cores distintas
  2. O estado é calculado corretamente: Pendente = 0 pagamentos; Parcialmente Pago = total pago < valorTotal; Pago = total pago >= valorTotal
  3. A página financeiro exibe quatro cards no topo: total faturado, total recebido, em dívida, receita do mês corrente
  4. Os valores dos cards são derivados dos dados já carregados — sem pedido HTTP adicional
**Plans**: 2/2
**UI hint**: yes

### Phase 45: Filtros + Edit/Delete UI
**Goal**: O utilizador pode filtrar a lista de honorários e executar ações de edição e eliminação diretamente na UI
**Depends on**: Phase 44
**Requirements**: FIN-11, FIN-12, FIN-13, FIN-14, FIN-15, FIN-16
**Success Criteria** (what must be TRUE):
  1. Utilizador pode filtrar a lista de honorários por processo, por status e por intervalo de datas (dataAcordo de/até), individualmente ou em combinação
  2. Utilizador com `financeiro:edit` pode abrir um formulário de edição de honorário e guardar alterações
  3. Utilizador com `financeiro:manage` pode apagar um honorário com diálogo de confirmação; a ação falha se o honorário tiver pagamentos
  4. Utilizador com `financeiro:manage` pode apagar um pagamento com diálogo de confirmação; o saldo é revertido na conta corrente do cliente
**Plans**: 2/2
**UI hint**: yes

### Phase 46: CSV Export
**Goal**: O utilizador pode exportar a lista de honorários (com filtros aplicados) para um ficheiro CSV
**Depends on**: Phase 45
**Requirements**: FIN-17
**Success Criteria** (what must be TRUE):
  1. Existe um botão "Exportar CSV" na página financeiro que gera e descarrega um ficheiro `.csv`
  2. O CSV contém os campos: id, processo, cliente, valorTotal, totalPago, estado, dataAcordo
  3. Quando filtros estão ativos, o CSV exporta apenas os honorários correspondentes aos filtros aplicados
**Plans**: 1/1
**UI hint**: yes

</details>

### ✅ v2.1 Agenda Avançada (SHIPPED 2026-06-18)

**Milestone Goal:** Adicionar notificações in-app de eventos próximos, suporte a eventos recorrentes e drag & drop no calendário para mover eventos.

- [x] **Phase 47: Notificações In-App** - Badge no header com contagem de eventos próximos e painel de notificações
- [x] **Phase 48: Recorrência de Eventos** - Criar, listar, exibir e apagar eventos com regras de recorrência (completed 2026-06-18)
- [x] **Phase 49: Drag & Drop no Calendário** - Arrastar eventos para nova data com atualização imediata via API (completed 2026-06-18)

### v2.2 Document Storage MinIO

**Milestone Goal:** Migrar o armazenamento de documentos do filesystem local para MinIO (object storage S3-compatible), atualizar o componente de upload no frontend e configurar o deploy no Hostinger VPS.

- [x] **Phase 50: Backend MinIO Integration** - Spring Boot integrado ao MinIO via AWS S3 SDK; upload, download pré-assinado e delete no bucket (completed 2026-06-19)
- [x] **Phase 51: Frontend Upload Component** - Barra de progresso, drag-and-drop, preview inline e download via URL pré-assinada (completed 2026-06-19)
- [x] **Phase 52: Deploy MinIO no Hostinger** - Serviço MinIO no Docker Compose prod, credenciais via env vars, CI/CD atualizado e consola via Caddy (completed 2026-06-19)

## Phase Details

### Phase 47: Notificações In-App
**Goal**: O utilizador vê no header a contagem de eventos e prazos nos próximos 7 dias e pode aceder ao painel de notificações para ver a lista completa com links diretos
**Depends on**: Phase 46 (milestone v2.0 completo)
**Requirements**: AGE-01, AGE-02
**Success Criteria** (what must be TRUE):
  1. O header da aplicação exibe um ícone/badge com o número de eventos e prazos não concluídos nos próximos 7 dias; o badge desaparece quando não há eventos próximos
  2. Clicar no badge abre um painel/dropdown que lista os eventos próximos com título, data e categoria
  3. Cada item no painel tem um link que navega para o detalhe do processo associado ao evento
  4. O backend expõe `GET /api/v1/eventos/upcoming?days=7` que retorna apenas eventos não concluídos dentro do intervalo, com tenant scoping correto
  5. A contagem atualiza automaticamente quando o utilizador conclui um evento (sem reload manual)
**Plans**: TBD
**UI hint**: yes

### Phase 48: Recorrência de Eventos
**Goal**: O utilizador pode criar eventos com regra de recorrência (diária, semanal ou mensal) e o calendário exibe todas as instâncias geradas; ao apagar, o utilizador escolhe entre apagar esta instância ou toda a série
**Depends on**: Phase 47
**Requirements**: AGE-03, AGE-04, AGE-05, AGE-06
**Success Criteria** (what must be TRUE):
  1. O formulário de criação de evento tem uma secção de recorrência com opções: Nenhuma, Diária, Semanal, Mensal; quando selecionada uma opção, o campo "data de fim da recorrência" torna-se obrigatório
  2. Ao submeter, o backend armazena a regra de recorrência e `GET /eventos` dentro de um intervalo de datas inclui as instâncias expandidas correspondentes
  3. O calendário apresenta as instâncias recorrentes nas suas datas corretas com um indicador visual (ícone ou badge) que as distingue dos eventos normais
  4. Ao tentar apagar um evento recorrente, surge um diálogo com duas opções: "Apagar esta instância" e "Apagar toda a série"; ambas as ações refletem-se imediatamente no calendário
**Plans**: 2 plans
- [x] 48-01-PLAN.md — Backend: Evento recurrence columns, instance expansion, instance soft-delete endpoint
- [x] 48-02-PLAN.md — Frontend: types, hooks, Zod schema, Recorrência form, calendar indicator, delete dialog
**UI hint**: yes

### Phase 49: Drag & Drop no Calendário
**Goal**: O utilizador pode arrastar um evento no calendário para outro dia do mesmo mês, e a nova data é persistida via API com feedback visual imediato
**Depends on**: Phase 48
**Requirements**: AGE-07, AGE-08
**Success Criteria** (what must be TRUE):
  1. Ao arrastar um evento para outra célula do calendário, a célula de destino indica visualmente que é uma zona de largar válida (highlight)
  2. Ao largar o evento na nova data, o calendário move o evento imediatamente (atualização otimista) e envia `PUT /eventos/{id}` com a nova data
  3. Se o pedido API falhar, o evento reverte para a data original e é mostrada uma mensagem de erro ao utilizador
  4. Eventos recorrentes não são arrastáveis (ou mostram uma mensagem de bloqueio), pois mover uma instância individualmente conflitua com a lógica de série
**Plans**: 1 plan
- [x] 49-01-PLAN.md — Drag & drop no calendário mensal: estado, override otimista, mutação PUT, pills arrastáveis + drop zones (agenda/page.tsx)
**UI hint**: yes

### Phase 50: Backend MinIO Integration
**Goal**: O backend armazena, serve e elimina ficheiros de documentos no MinIO em vez do filesystem local, com isolamento por tenant e downloads seguros via URLs pré-assinadas
**Depends on**: Phase 49 (milestone v2.1 completo)
**Requirements**: MIN-01, MIN-02, MIN-03, MIN-04
**Success Criteria** (what must be TRUE):
  1. Um ficheiro carregado para um processo é armazenado no bucket MinIO sob o prefixo `{tenant_id}/{documento_id}/{filename}` e não cria nenhum ficheiro no filesystem do container
  2. O utilizador clica em "Descarregar" e recebe uma URL pré-assinada temporária que permite download direto do objeto no MinIO sem autenticação adicional
  3. Ao apagar um documento, o objeto correspondente desaparece do bucket MinIO (verificável via consola MinIO ou API S3)
  4. Documentos de um tenant nunca são acessíveis através de prefixos de outro tenant — o isolamento é garantido pelo prefixo de path, não por bucket separado
**Plans**: 2 plans
Plans:
- [x] 50-01-PLAN.md — Infrastructure: pom.xml BOM, MinioProperties, MinioConfig (S3Client + S3Presigner), StorageService, StorageUnavailableException, application.yml, .env.example
- [x] 50-02-PLAN.md — Endpoint migration: uploadDocumento, downloadDocumento, deleteDocumento refactored to use StorageService; binary stream replaced by presigned URL JSON response

### Phase 51: Frontend Upload Component
**Goal**: O componente de upload de documentos oferece feedback visual durante a transferência, suporta drag-and-drop, mostra preview inline de imagens e PDFs, e inicia downloads via URL pré-assinada
**Depends on**: Phase 50
**Requirements**: MIN-05, MIN-06, MIN-07, MIN-08
**Success Criteria** (what must be TRUE):
  1. Ao selecionar ou largar um ficheiro, uma barra de progresso mostra a percentagem de upload em tempo real até 100%
  2. Clicar em "Descarregar" num documento existente abre a URL pré-assinada retornada pelo backend diretamente no browser — nenhum ficheiro passa pelo servidor Next.js
  3. O utilizador pode arrastar um ficheiro do sistema operativo para a zona de upload e o ficheiro é aceite da mesma forma que clicando para selecionar
  4. Antes de confirmar o upload, imagens (PNG, JPG, GIF) e PDFs mostram uma pré-visualização inline na interface
**Plans**: TBD
**UI hint**: yes

### Phase 52: Deploy MinIO no Hostinger
**Goal**: O MinIO está a correr no Hostinger VPS como serviço Docker Compose com storage persistente, credenciais seguras via env vars, pipeline CI/CD atualizado e consola de administração acessível via Caddy
**Depends on**: Phase 50
**Requirements**: MIN-09, MIN-10, MIN-11, MIN-12
**Success Criteria** (what must be TRUE):
  1. O `docker-compose.prod.yml` inclui um serviço `minio` com volume nomeado persistente; após restart do compose, os objetos existentes no bucket continuam acessíveis
  2. Nenhuma credencial MinIO (`MINIO_ROOT_USER`, `MINIO_ROOT_PASSWORD`, nome do bucket) está hardcoded em ficheiros versionados — todos os valores são injetados via variáveis de ambiente
  3. Executar o workflow de deploy no GitHub Actions faz pull e restart do serviço MinIO juntamente com backend e frontend, sem passos manuais adicionais
  4. A consola de administração MinIO está acessível via HTTPS num subpath ou subdomínio protegido pelo Caddy, com acesso restrito por credenciais
**Plans**: TBD

## Progress

**Execution Order:** 47 → 48 → 49 → 50 → 51 → 52

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 43. Data Layer + Backend Endpoints | v2.0 | 2/2 | Complete | 2026-06-18 |
| 44. Status + KPIs | v2.0 | 2/2 | Complete | 2026-06-18 |
| 45. Filtros + Edit/Delete UI | v2.0 | 2/2 | Complete | 2026-06-18 |
| 46. CSV Export | v2.0 | 1/1 | Complete | 2026-06-18 |
| 47. Notificações In-App | v2.1 | 2/2 | Complete | 2026-06-18 |
| 48. Recorrência de Eventos | v2.1 | 2/2 | Complete | 2026-06-18 |
| 49. Drag & Drop no Calendário | v2.1 | 1/1 | Complete | 2026-06-18 |
| 50. Backend MinIO Integration | v2.2 | 2/2 | Complete   | 2026-06-19 |
| 51. Frontend Upload Component | v2.2 | 1/1 | Complete   | 2026-06-19 |
| 52. Deploy MinIO no Hostinger | v2.2 | 1/1 | Complete   | 2026-06-19 |
