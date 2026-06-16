# Requirements: LexCV

**Defined:** 2026-05-27
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.

## v1.8 Requirements (Deployment para VPS)

### Containerização e Docker Compose
- [x] **DEP-01**: Admin/Dev consegue configurar os Dockerfiles para o frontend Next.js e o backend Spring Boot com builds multi-stage otimizados.
- [ ] **DEP-02**: Admin/Dev consegue definir e levantar um ecossistema multi-container usando Docker Compose que inclui Next.js, Spring Boot, PostgreSQL e um Reverse Proxy (Caddy/Nginx), com persistência de dados do PostgreSQL através de volumes.

### Reverse Proxy e Redirecionamento
- [ ] **DEP-03**: Admin/Dev consegue configurar o Caddy/Nginx para receber pedidos HTTP/HTTPS na VPS e encaminhá-los internamente para o frontend Next.js (port 3000) e o backend Spring Boot (port 8080/api/v1).

### CI/CD e Deployment Contínuo
- [ ] **DEP-04**: Admin/Dev consegue automatizar o processo de deployment na VPS Hostinger via GitHub Actions ao fazer push para o branch `main`, utilizando chaves SSH seguras.

### Segurança e Variáveis de Ambientais
- [ ] **DEP-05**: Admin/Dev consegue isolar todas as credenciais sensíveis (passwords da BD, segredos JWT) na VPS através de variáveis de ambiente configuradas no ficheiro `.env`.

## v1.7 Requirements (Melhoria no modulo de gestao e acompanhamento de processos)

### Intake e Abertura

- [x] **INT-01**: Utilizador consegue registar um potencial cliente e iniciar um intake estruturado antes da abertura formal do processo, com campos minimos obrigatorios por tipo de matter/processo.
- [x] **CFL-01**: Utilizador consegue executar um conflict check estruturado por cliente, partes relacionadas, parte contraria e assunto, e o sistema bloqueia a abertura formal do processo ate existir uma decisao registada.

### Workflow e Acompanhamento

- [x] **PRC-27**: Utilizador consegue gerir o processo por estados definidos, com gates, responsaveis e validacoes minimas por transicao.
- [x] **AGD-22**: Utilizador consegue acompanhar prazos operacionais do processo com SLA, prioridade, risco e escalonamento simples quando um prazo estiver proximo ou vencido.

### Timeline e Auditoria

- [x] **PRC-28**: Utilizador consegue consultar uma timeline unificada do processo com movimentacoes, tarefas, documentos, eventos e decisoes ordenadas por data.
- [x] **AUD-02**: Utilizador consegue consultar a trilha auditavel de eventos sensiveis do processo, incluindo consulta, alteracao, exportacao, download e eliminacao.

### Governanca Documental

- [ ] **DOC-11**: Utilizador consegue classificar documentos do processo por categoria, confidencialidade, versao e metadados obrigatorios.
- [ ] **DOC-12**: Utilizador consegue aplicar regras de retencao e legal hold por processo/documento, impedindo descarte ou eliminacao indevida enquanto existir bloqueio ativo.

### Monitorizacao e KPI

- [ ] **MON-01**: Utilizador consegue acompanhar um painel operacional com backlog por responsavel, prazos criticos, processos sem atualizacao e carga por tipo de processo.
- [ ] **KPI-01**: Utilizador consegue acompanhar um painel executivo com tempos medios, conflitos detetados, conformidade documental e exposicao operacional/financeira por carteira.

## v1.6 Requirements (Melhoria nfeature de gestão de clientes)

### Clientes (Enriquecimento, Financeiro e Timeline)

- [ ] **CLI-31**: Enriquecimento cadastral de Clientes: Adicionar campos `categoria`, `ramo_atividade`, `documento_tipo`, `documento_numero` e `detalhes_adicionais` no modelo e formulários.
- [ ] **CLI-32**: Perfil Financeiro e Faturação: Adicionar preferências de faturamento por cliente, incluindo `valor_hora` (taxa horária), `avenca_mensal`, `moeda` e `iban`.
- [ ] **CLI-33**: Linha de Tempo de Atividades do Cliente: Timeline unificada no detalhe do cliente que agrega processos, notas, contactos, documentos e registros financeiros.
- [ ] **CLI-34**: Conflict Check Visual: Validação visual no formulário para alertar se existir correspondência exata ou aproximada de NIF ou nome semelhante no sistema.

## v1.5 Requirements (Melhoria funcionalidades processos)

### Processos (Melhorias)

- [ ] **PRC-21**: Pesquisa multi-campo e filtros avançados na listagem de processos (com UX/performance)
- [ ] **PRC-22**: Anexos por processo (upload/listagem/download/apagar) com controlo por permissions
- [ ] **PRC-23**: Checklist de tarefas por processo (CRUD + estado), com seleção de relacionamentos (FKs) quando aplicável
- [ ] **PRC-24**: Timeline de eventos/movimentações do processo (visão consolidada e navegável)
- [ ] **PRC-25**: Workflows/estados com validações/transições básicas (produtividade e consistência)
- [ ] **AGD-21**: Integração forte Processos ↔ Agenda (prazos/alertas/templates simples)
- [ ] **PRC-26**: Exportar resumo do processo (PDF/CSV)

## v1.4 Requirements (Melhoria módulo clientes)

### Clientes (Melhorias)

- [x] **CLI-21**: Filtros avançados (ex.: tipo, estado, localidade) + pesquisa multi-campo consistente (nome/NIF/telefone/email) com debounce
- [x] **CLI-22**: Sub-recursos de Cliente: contactos e notas (CRUD) no detalhe do cliente
- [x] **CLI-23**: Importação de clientes (CSV) com validação e feedback de erros/sucesso
- [x] **CLI-24**: Exportação de clientes (CSV) respeitando filtros aplicados
- [x] **CLI-25**: Detecção e merge de clientes duplicados (heurísticas por NIF/email/telefone) com fluxo guiado
- [x] **CLI-26**: UX/Performance na listagem (loading states consistentes, estados vazios, paginação/virtualização quando aplicável)

## v1.1 Requirements (UI/UX Alignment)

### Shell (Sidebar + Top Bar)

- [x] **UX-01**: Sidebar escura com estados (ativo/inativo) e ícones consistente com os mockups Figma
- [x] **UX-02**: Top app bar com pesquisa global, instituição/tribunal, ações rápidas e perfil do utilizador
- [x] **UX-03**: Componentes UI reutilizáveis (badges, tabelas, paginação, avatar/menu) no padrão visual do Figma

### Dashboard

- [x] **DSH-11**: Dashboard institucional com KPI cards e layout em grid (como no Figma)
- [x] **DSH-12**: Painel “Prazos urgentes” e CTA para agenda completa (como no Figma)
- [x] **DSH-13**: Tabela “Processos recentes” e painel “Atividade recente” (como no Figma)

### Clientes

- [x] **CLI-11**: Clientes com bento stats (total/singulares/coletivas/processos ativos) no topo
- [x] **CLI-12**: Filtros de nome e NIF com botões de “Filtros avançados” e “Limpar”
- [x] **CLI-13**: Tabela de clientes com avatar iniciais, badges de tipo, ações e paginação (como no Figma)

### Processos

- [x] **PRC-11**: Processos com stats cards e callout “Próximas audiências” (como no Figma)
- [x] **PRC-12**: Barra de filtros (tribunal/estado) e ação “Novo Processo” (como no Figma)
- [x] **PRC-13**: Tabela de processos com badges de área/estado e ações (como no Figma)

### Agenda

- [x] **AGD-11**: Agenda mensal em grid com navegação (mês/anterior/próximo/hoje) no padrão Figma
- [x] **AGD-12**: Sidebar “Próximos eventos” com cards por categoria (como no Figma)
- [x] **AGD-13**: Mini-stats semanal (prazos ativos/audiências/urgentes) no padrão Figma

<details>
<summary>✅ v1.0 Requirements (MVP Institucional) — concluído</summary>

### Autenticação & Tenant

- [x] **AUTH-01**: Utilizador consegue autenticar via email/password (login) e receber `access_token` + `refresh_token`
- [x] **AUTH-02**: Utilizador consegue renovar sessão via refresh token
- [x] **AUTH-03**: Frontend consegue obter o utilizador atual via `/auth/me` (inclui roles e tenant)
- [x] **TEN-01**: Contexto multi-tenant é aplicado automaticamente nas respostas do mock (sem `tenant_id` em rotas)

### Autorização (RBAC) & Navegação

- [x] **RBAC-01**: Rotas e ações no UI são ocultadas conforme roles retornadas pelo backend
- [x] **NAV-01**: Existe dashboard com sidebar e rotas base para: Clientes, Processos, Agenda, Documentos, Financeiro
- [x] **DSH-01**: Dashboard exibe KPIs básicos consumindo `/dashboard`

### Clientes

- [x] **CLI-01**: Utilizador consegue listar clientes com filtros por `nome` e `nif`
- [x] **CLI-02**: Utilizador consegue criar/editar/remover cliente
- [x] **CLI-03**: Utilizador consegue ver detalhe do cliente (inclui conta corrente)

### Processos

- [x] **PRC-01**: Utilizador consegue listar processos e abrir detalhe
- [x] **PRC-02**: Utilizador consegue criar/editar/remover processo associado a um cliente
- [x] **PRC-03**: Utilizador consegue listar/criar partes do processo (sub-recurso)
- [x] **PRC-04**: Utilizador consegue listar/adicionar/atualizar fases do processo (sub-recurso)
- [x] **PRC-05**: Utilizador consegue listar/criar movimentações de um processo (sub-recurso)

### Agenda / Eventos

- [x] **AGD-01**: Utilizador consegue listar eventos com filtros críticos (dataInicio/dataFim, processoId, concluido)
- [x] **AGD-02**: Utilizador consegue criar/editar/remover evento e marcar como concluído

### Documentos

- [x] **DOC-01**: Utilizador consegue listar documentos e filtrar por processo/cliente
- [x] **DOC-02**: Utilizador consegue fazer upload via `multipart/form-data` para `/documentos/upload`
- [x] **DOC-03**: Utilizador consegue fazer download via `/documentos/{id}/download`

### Financeiro

- [x] **FIN-01**: Utilizador consegue criar honorário associado a um processo
- [x] **FIN-02**: Utilizador consegue registrar pagamentos e listar pagamentos por honorário
- [x] **FIN-03**: Utilizador consegue ver conta corrente por cliente

## v2 Requirements (Futuro)

- **AUTH-10**: Integração real com Keycloak (SSO institucional)
- **PLAT-01**: Empacotamento Desktop (Tauri) e PWA com capacidades offline
- **DOC-10**: Gestão avançada de versões, revisão e permissões por documento
- **NOTF-01**: Notificações (in-app/email) e preferências
- **AUD-01**: Painel de auditoria completo (filtros e detalhe por entidade)
- **RISK-01**: Deteccao automatica de comportamento anomalo e scoring de risco por processo/cliente
- **OPS-01**: Sugestoes automáticas de proximos passos e benchmarking entre equipas/unidades

## Out of Scope

| Feature | Reason |
|---------|--------|
| Regras de negócio avançadas no frontend | Backend é a fonte de verdade; evitar duplicação e deriva |
| Contabilidade/ERP completo | Complexidade alta fora do MVP |
| Multi-tenant exposto em URL | Requisito de segurança e design: resolver via JWT/header |
| Alertas preditivos e scoring automatizado | Exigem historico maior e regras mais maduras antes de entregar valor confiavel |
| Automacao documental avancada com IA | Fora do objetivo deste milestone; primeiro consolidar processos, auditoria e governanca |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| AUTH-01 | Phase 1 | Complete |
| AUTH-02 | Phase 1 | Complete |
| AUTH-03 | Phase 1 | Complete |
| TEN-01 | Phase 1 | Complete |
| RBAC-01 | Phase 1 | Complete |
| NAV-01 | Phase 1 | Complete |
| DSH-01 | Phase 1 | Complete |
| CLI-01 | Phase 2 | Complete |
| CLI-02 | Phase 2 | Complete |
| CLI-03 | Phase 2 | Complete |
| PRC-01 | Phase 3 | Complete |
| PRC-02 | Phase 3 | Complete |
| PRC-03 | Phase 3 | Complete |
| PRC-04 | Phase 3 | Complete |
| PRC-05 | Phase 3 | Complete |
| AGD-01 | Phase 4 | Complete |
| AGD-02 | Phase 4 | Complete |
| DOC-01 | Phase 5 | Complete |
| DOC-02 | Phase 5 | Complete |
| DOC-03 | Phase 5 | Complete |
| FIN-01 | Phase 6 | Complete |
| FIN-02 | Phase 6 | Complete |
| FIN-03 | Phase 6 | Complete |

**Coverage:**
- v1 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0 ✓

---
*Requirements defined: 2026-05-26*
*Last updated: 2026-05-26 after conclusão do MVP (fases 1–6)*

</details>

## Traceability (v1.1)

| Requirement | Phase | Status |
|-------------|-------|--------|
| UX-01 | Phase 7 | Complete |
| UX-02 | Phase 7 | Complete |
| UX-03 | Phase 7 | Complete |
| DSH-11 | Phase 8 | Complete |
| DSH-12 | Phase 8 | Complete |
| DSH-13 | Phase 8 | Complete |
| CLI-11 | Phase 8 | Complete |
| CLI-12 | Phase 8 | Complete |
| CLI-13 | Phase 8 | Complete |
| PRC-11 | Phase 8 | Complete |
| PRC-12 | Phase 8 | Complete |
| PRC-13 | Phase 8 | Complete |
| AGD-11 | Phase 9 | Complete |
| AGD-12 | Phase 9 | Complete |
| AGD-13 | Phase 9 | Complete |

**Coverage:**
- v1.1 requirements: 15 total
- Mapped to phases: 15
- Unmapped: 0 ✓

## Traceability (v1.4)

| Requirement | Phase | Status |
|-------------|-------|--------|
| CLI-21 | Phase 18 | Complete |
| CLI-22 | Phase 19 | Complete |
| CLI-23 | Phase 20 | Complete |
| CLI-24 | Phase 20 | Complete |
| CLI-25 | Phase 21 | Complete |
| CLI-26 | Phase 18 | Complete |

**Coverage:**
- v1.4 requirements: 6 total
- Mapped to phases: 6
- Unmapped: 0 ✓

## Traceability (v1.5)

| Requirement | Phase | Status |
|-------------|-------|--------|
| PRC-21 | Phase 22 | Planned |
| PRC-22 | Phase 23 | Planned |
| PRC-23 | Phase 24 | Planned |
| PRC-24 | Phase 25 | Planned |
| PRC-25 | Phase 26 | Planned |
| AGD-21 | Phase 27 | Planned |
| PRC-26 | Phase 27 | Planned |

**Coverage:**
- v1.5 requirements: 7 total
- Mapped to phases: 7
- Unmapped: 0 ✓

## Traceability (v1.6)

| Requirement | Phase | Status |
|-------------|-------|--------|
| CLI-31 | Phase 28 | Planned |
| CLI-32 | Phase 29 | Planned |
| CLI-33 | Phase 30 | Planned |
| CLI-34 | Phase 31 | Planned |

**Coverage:**
- v1.6 requirements: 4 total
- Mapped to phases: 4
- Unmapped: 0 ✓

## Traceability (v1.7)

| Requirement | Phase | Status |
|-------------|-------|--------|
| INT-01 | Phase 32 | Complete |
| CFL-01 | Phase 32 | Complete |
| PRC-27 | Phase 33 | Planned |
| AGD-22 | Phase 33 | Planned |
| PRC-28 | Phase 34 | Planned |
| AUD-02 | Phase 34 | Planned |
| DOC-11 | Phase 35 | Planned |
| DOC-12 | Phase 35 | Planned |
| MON-01 | Phase 36 | Planned |
| KPI-01 | Phase 36 | Planned |

**Coverage:**
- v1.7 requirements: 10 total
- Mapped to phases: 10
- Unmapped: 0 ✓

## Traceability (v1.8)

| Requirement | Phase | Status |
|-------------|-------|--------|
| DEP-01 | Phase 37 | Planned |
| DEP-02 | Phase 37 | Planned |
| DEP-03 | Phase 38 | Planned |
| DEP-04 | Phase 39 | Planned |
| DEP-05 | Phase 37 | Planned |

**Coverage:**
- v1.8 requirements: 5 total
- Mapped to phases: 5
- Unmapped: 0 ✓

