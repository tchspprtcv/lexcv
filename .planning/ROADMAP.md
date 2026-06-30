# Roadmap: LexCV

## Milestones

- ✅ **v2.0 Módulo Financeiro** — Phases 43–46 (complete 2026-06-18)
- ✅ **v2.1 Agenda Avançada** — Phases 47–49 (complete 2026-06-18)
- ✅ **v2.2 Document Storage MinIO** — Phases 50–52 (complete 2026-06-19)
- ✅ **v2.3 Responsividade App** — Phases 53–56 (complete 2026-06-21)
- 🔄 **v2.4 Ficha de Cliente** — Phases 57–60 (active)

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

### v2.4 Ficha de Cliente (active)

- [ ] **Phase 57: Backend Schema + API** — Extensão da entidade Cliente com novos campos, geração de numero_cliente, endpoints atualizados
- [x] **Phase 58: Formulário Dinâmico** — Formulário frontend com seletor de tipo, campos demográficos/empresa, flag avençado e exibição do número (completed 2026-06-30)
- [ ] **Phase 59: Procuração + Intake** — Upload obrigatório de procuração e secção de intake (advogados, administrativos, docs, deslocações, honorários propostos)
- [ ] **Phase 60: Ficha Imprimível** — Vista dedicada que reproduz a ficha real do escritório com botão de impressão

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

### Phase 53: Shell Responsivo
**Goal**: O layout principal da aplicação adapta-se a ecrãs mobile — a sidebar fixa de 270px dá lugar a um drawer overlay controlado por hamburger, a top bar simplifica-se, e uma bottom navigation bar oferece acesso direto aos módulos
**Depends on**: Phase 52 (milestone v2.2 completo)
**Requirements**: NAV-01, NAV-02, NAV-03, NAV-04
**Success Criteria** (what must be TRUE):
  1. Em mobile (< 768px), a sidebar não está visível por defeito; o utilizador toca no ícone hamburger na top bar e a sidebar abre como drawer overlay sobre o conteúdo sem deslocar o layout
  2. Ao navegar para qualquer página via link na sidebar em mobile, o drawer fecha automaticamente sem necessitar de clique adicional
  3. A top bar em mobile mostra apenas o ícone hamburger, o nome/logotipo da instituição e os ícones de notificações e perfil — sem outros elementos
  4. Uma bottom navigation bar fixa exibe os 5 módulos principais (Dashboard, Clientes, Processos, Agenda, Financeiro/Documentos) e é visível em todas as páginas em mobile
**Plans**: 2 plans
Plans:
- [x] 53-01-PLAN.md — shadcn Sheet install + DashboardShell responsiva (hamburger, drawer, top bar mobile)
- [x] 53-02-PLAN.md — BottomNav component + padding do conteúdo
**UI hint**: yes

### Phase 54: Listas e Tabelas
**Goal**: As listagens de dados adaptam o seu formato ao tamanho do ecrã — listas simples mostram cards empilhados em mobile e tabelas complexas têm scroll horizontal
**Depends on**: Phase 53
**Requirements**: TAB-01, TAB-02
**Success Criteria** (what must be TRUE):
  1. Em mobile, as páginas Clientes, Documentos, Financeiro e Agenda substituem a tabela por cards empilhados verticalmente com os campos essenciais visíveis e acções acessíveis por toque
  2. Em desktop, as mesmas páginas continuam a mostrar a tabela completa sem alteração ao comportamento existente
  3. Em mobile, as tabelas de Partes do Processo, Movimentações e Fases têm scroll horizontal nativo, com todas as colunas acessíveis por deslize — sem conteúdo cortado nem overflow no layout
**Plans**: TBD
**UI hint**: yes

### Phase 55: Formulários e Modais
**Goal**: Todos os formulários e diálogos da aplicação são utilizáveis com os dedos em mobile — coluna única, touch targets adequados e modais que não sobrepõem o teclado virtual
**Depends on**: Phase 53
**Requirements**: FORM-01, FORM-02, FORM-03
**Success Criteria** (what must be TRUE):
  1. Em mobile, todos os formulários (criação/edição de clientes, processos, eventos, honorários, documentos) fluem em coluna única a 100% da largura — nenhum campo fica lado a lado em ecrãs < 768px
  2. Em mobile, os diálogos/modais abrem como bottom-sheet (deslizando de baixo para cima) ou ocupam o ecrã inteiro, em vez do estilo centered dialog de desktop
  3. Todos os inputs de texto, selects, botões de ação e ícones interativos têm altura mínima de 48px, verificável inspecionando o CSS aplicado ou tocando sem errar o alvo
**Plans**: TBD
**UI hint**: yes

### Phase 56: Dashboard e Calendário
**Goal**: O dashboard e o calendário de agenda apresentam o conteúdo num formato optimizado para mobile, com o grid de KPIs adaptável e a vista diária como ponto de entrada em ecrãs pequenos
**Depends on**: Phase 54
**Requirements**: DASH-01, CAL-01
**Success Criteria** (what must be TRUE):
  1. Em mobile (< 640px), os KPI cards do dashboard exibem-se em 1 coluna; em tablet (640px–1024px), em 2 colunas; em desktop (> 1024px), em 4 colunas — sem overflow nem cards cortados em nenhum breakpoint
  2. Em mobile, o calendário da Agenda abre por defeito na vista diária, mostrando os eventos do dia corrente num layout vertical legível com toque
  3. Em tablet e desktop, o calendário mantém o comportamento actual (vista mensal/semanal por defeito) sem regressão
**Plans**: TBD
**UI hint**: yes

### Phase 57: Backend Schema + API
**Goal**: A entidade Cliente no backend suporta todos os novos campos da ficha de escritório — numero_cliente gerado automaticamente por tenant, tipo de cliente, dados demográficos do particular e dados da entidade coletiva — e os endpoints CRUD refletem esse schema
**Depends on**: Phase 56 (milestone v2.3 completo)
**Requirements**: PERF-01, PERF-03, PERF-04, PART-01, PART-02, EMP-01
**Success Criteria** (what must be TRUE):
  1. Ao criar um cliente via `POST /api/v1/clientes`, o backend gera automaticamente um `numero_cliente` no formato CLI-XXXX, único por tenant, sem input do utilizador
  2. O endpoint aceita e persiste `tipo_cliente` (enum PARTICULAR / EMPRESA), `avencado` (boolean), e os campos demográficos (`idade`, `sexo`, `nacionalidade`, `biPassaporte`) para clientes do tipo PARTICULAR
  3. O endpoint aceita e persiste os campos de entidade coletiva (`nomeComercial`, `nif`, `sede`, `representanteLegal`, `cargoRepresentante`) para clientes do tipo EMPRESA
  4. `GET /api/v1/clientes` e `GET /api/v1/clientes/{id}` retornam todos os novos campos, com tenant scoping correto em todas as operações
**Plans**: 2 plans
Plans:
- [ ] 57-01-PLAN.md — Entity + Repository: TipoCliente enum, DadosTipo POJO, DadosTipoConverter, Cliente new fields, ClienteRepository MAX+1 query
- [ ] 57-02-PLAN.md — Controller: numero_cliente generation in createCliente, new field assignment in updateCliente

### Phase 58: Formulário Dinâmico
**Goal**: O formulário de criação e edição de cliente adapta os seus campos ao tipo de cliente selecionado, exibe o numero_cliente gerado e permite marcar o cliente como avençado
**Depends on**: Phase 57
**Requirements**: PERF-02, PERF-03, PERF-04, EMP-02
**Success Criteria** (what must be TRUE):
  1. O formulário tem um seletor de tipo (Particular / Empresa); ao mudar o tipo, os campos específicos trocam dinamicamente — campos demográficos visíveis para Particular, campos de entidade coletiva visíveis para Empresa, nunca os dois em simultâneo
  2. Após criar um cliente, o seu numero_cliente (ex: CLI-0001) é visível na listagem de clientes e no cabeçalho da ficha individual, em destaque
  3. O formulário inclui uma checkbox ou toggle "Cliente Avençado"; quando ativado, a ficha e a listagem exibem um badge identificador
  4. Todos os novos campos passam pela validação Zod antes de submeter — campos obrigatórios por tipo estão assinalados e bloqueiam a submissão se vazios
**UI hint**: yes

### Phase 59: Procuração + Intake
**Goal**: A ficha de cliente tem uma secção de procuração com upload obrigatório e uma secção de intake onde o utilizador regista a descrição do caso, advogados, administrativos, documentos, deslocações e honorários propostos
**Depends on**: Phase 58
**Requirements**: PROC-01, PROC-02, INT-01, INT-02, INT-03, INT-04, INT-05, INT-06, INT-07
**Success Criteria** (what must be TRUE):
  1. Não é possível guardar um cliente sem um documento de procuração associado — o formulário bloqueia a submissão e indica claramente o campo em falta
  2. Na ficha do cliente existe um botão para visualizar a procuração existente (abre URL pré-assinada MinIO) e outro para substituir o ficheiro por uma nova versão
  3. A secção de intake permite registar: descrição do caso (textarea), lista de advogados com nome + cédula + contacto (linhas adicionáveis), lista de administrativos (linhas adicionáveis), lista de documentos entregues, lista de documentos a tratar, lista de deslocações a realizar, e honorários propostos (valor total, valor por extenso, previsão)
  4. Cada lista do intake (advogados, docs, deslocações) tem botões para adicionar e remover entradas individualmente sem perder as restantes
  5. Todas as secções de intake são persistidas via API e carregadas ao abrir a ficha do cliente
**UI hint**: yes

### Phase 60: Ficha Imprimível
**Goal**: O utilizador acede a uma vista dedicada da ficha de cliente que reproduz o formato real do formulário do escritório e pode enviá-la para impressão com um único clique
**Depends on**: Phase 59
**Requirements**: FICH-01, FICH-02
**Success Criteria** (what must be TRUE):
  1. Existe um botão "Ficha do Cliente" ou "Imprimir Ficha" na ficha do cliente que abre uma rota dedicada (ex: `/clientes/{id}/ficha`) com o layout do formulário real do escritório
  2. A vista de ficha apresenta todos os campos preenchidos — numero_cliente, tipo, dados demográficos/empresa, procuração, intake completo — organizados no mesmo layout visual do formulário em papel
  3. Ao clicar em "Imprimir", o browser abre o diálogo de impressão com CSS de impressão aplicado: sem sidebar, sem header de navegação, sem botões de ação, apenas o conteúdo da ficha
  4. Em impressão, a ficha ocupa corretamente páginas A4 — sem conteúdo cortado entre páginas e com margens adequadas
**UI hint**: yes
**Plans**: 2 plans
Plans:
- [ ] 60-01-PLAN.md — Estender tipos + criar página ficha imprimível (nova rota, layout 8 secções, CSS A4, guard de permissão)
- [ ] 60-02-PLAN.md — Adicionar pontos de acesso à ficha (botão na página de detalhe + ícone Printer na listagem)

## Progress

**Execution Order:** 47 → 48 → 49 → 50 → 51 → 52 → 53 → 54 → 55 → 56 → 57 → 58 → 59 → 60

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
| 53. Shell Responsivo | v2.3 | 2/2 | Complete   | 2026-06-21 |
| 54. Listas e Tabelas | v2.3 | 3/3 | Complete | 2026-06-21 |
| 55. Formulários e Modais | v2.3 | 2/2 | Complete | 2026-06-21 |
| 56. Dashboard e Calendário | v2.3 | 1/1 | Complete   | 2026-06-21 |
| 57. Backend Schema + API | v2.4 | 0/? | Not started | - |
| 58. Formulário Dinâmico | v2.4 | 0/? | Not started | - |
| 59. Procuração + Intake | v2.4 | 0/? | Not started | - |
| 60. Ficha Imprimível | v2.4 | 0/? | Not started | - |
