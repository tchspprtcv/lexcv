# Roadmap: LexCV

## Milestones

- ✅ **v1.0 MVP** - Phases 1-6 (shipped 2026-05-26) — archive: `.planning/milestones/v1.0-ROADMAP.md`
- ✅ **v1.1 UI/UX Alignment** - Phases 7-10 (shipped 2026-05-27) — archive: `.planning/milestones/v1.1-ROADMAP.md`
- ✅ **v1.2 Utilizador** - Phase 11 (shipped 2026-05-27) — archive: `.planning/milestones/v1.2-ROADMAP.md`
- 🏃 **v1.3 Security Check** - Phases 12-15 (active)

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
<summary>🏃 v1.3 Security Check (Phases 12-15)</summary>

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

</details>

## Progress

**Execution Order:**
Phases executam em ordem numérica: 7 → 8 → 9 → 10 → 11 → 12 → 13 → 14 → 15

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
