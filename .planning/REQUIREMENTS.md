# Requirements: LexCV

**Defined:** 2026-05-27
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.

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

## Out of Scope

| Feature | Reason |
|---------|--------|
| Regras de negócio avançadas no frontend | Backend é a fonte de verdade; evitar duplicação e deriva |
| Contabilidade/ERP completo | Complexidade alta fora do MVP |
| Multi-tenant exposto em URL | Requisito de segurança e design: resolver via JWT/header |

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
