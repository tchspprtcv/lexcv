# Milestones

## v2.5 Módulo de Parecer Jurídico (Shipped: 2026-06-30)

**Phases completed:** 4 phases (61–64), 7 plans

**Key accomplishments:**

- API completa do ciclo de vida do parecer jurídico: Solicitação → Elaboração (versionamento imutável com anexo opcional via StorageService) → Aprovação interna opcional (ADMIN) → Entrega (advogado responsável ou ADMIN, irreversível)
- Scope RBAC dedicado `pareceres:view/create/edit/manage`, seedado por role e espelhado no frontend `permissions.ts`
- Auditoria automática reutilizando `AuditLog` existente em todos os 5 pontos de transição de estado
- Pesquisa avançada combinando texto livre (ILIKE sobre conteúdo da versão mais recente) com filtros de cliente/advogado/status/data
- 5 rondas de code review com correções aplicadas e re-verificadas: 2 IDOR cross-tenant críticos, 1 race condition, 1 mismatch de permissão que tornava inalcançável um ramo de autorização, 1 bug de JOIN que excluía resultados válidos da pesquisa

**Known gaps at close:**

- **Backend-only** — nenhuma UI frontend foi construída para o módulo de pareceres. Decisão explícita e repetida em todas as 4 fases, mas significa que o módulo ainda não é utilizável através da aplicação LexCV, apenas via chamada API direta. Auditoria da milestone classificou como `tech_debt` (não bloqueante). Recomendação: milestone v2.6 dedicada à UI.
- `versaoFinalId` (campo de vínculo à versão entregue) só é visível no JSON genérico das respostas GET existentes, sem vista dedicada "parecer entregue"
- Comparação visual (diff) entre versões não implementada — apenas listagem/detalhe sequencial
- Sem índice full-text dedicado (tsvector/trigram) — `ILIKE` nativo suficiente para o volume atual

Ver `.planning/v2.5-MILESTONE-AUDIT.md` e `.planning/milestones/v2.5-ROADMAP.md` para detalhes completos.

## v2.4 Ficha de Cliente (Shipped: 2026-06-30)

**Phases completed:** 4 phases (57–60), 14 plans, ~30 tasks

**Key accomplishments:**

- Numeração sequencial automática de clientes (CLI-0001) por tenant, com formulário dinâmico que adapta os campos a Particular ou Empresa
- Procuração obrigatória com aviso visual não-bloqueante ("Procuração em falta"), upload/substituição via MinIO
- Intake completo do caso na ficha do cliente: advogados e administrativos ligados a Users do sistema, documentos entregues/a tratar, deslocações, e honorários propostos
- Ficha imprimível de alta fidelidade ao formulário físico do escritório, com CSS de impressão A4 e botão de impressão directa
- Auditoria de integração entre fases (pós-execução) detectou um mismatch sistémico snake_case/camelCase entre backend e frontend que invalidava 9 dos 19 requisitos (dados gravados correctamente mas nunca visíveis no ecrã) e uma fuga de password hash em 2 endpoints novos — ambos corrigidos antes do fecho do milestone

**Known gaps at close:**

- Correcção da auditoria foi verificada estaticamente (mapeamento campo-a-campo + builds limpos), não testada ao vivo contra backend+DB+MinIO — recomenda-se smoke test antes de produção
- Itens não-bloqueantes adiados: allowlist de tipo de ficheiro na procuração, formatação de moeda na ficha impressa, ficha sem ponto de acesso em mobile (ver STATE.md Pending Todos)

---

## v2.3 Responsividade App (Shipped: 2026-06-21)

**Phases completed:** 4 phases (53–56), 8 plans

**Key accomplishments:**

- Shell responsivo: sidebar drawer overlay com Sheet + hamburger `md:hidden`, auto-close via `useEffect([pathname])`, BottomNav `fixed bottom-0 md:hidden` com filtragem por permissões
- Dual-view lists: mobile cards (`md:hidden`) + desktop tables (`hidden md:block`) em Clientes, Agenda, Documentos e Financeiro — CSS puro, sem rerenders
- Horizontal scroll em tabelas complexas: `overflow-x-auto` + `min-w-[400/480px]` em Partes e Fases do processo
- Single-column forms: 24x `grid-cols-1 md:grid-cols-2` em 12 ficheiros; bottom-sheet dialogs `max-sm:fixed max-sm:bottom-0`; touch targets 48px (`max-sm:h-12` + `h-12 w-12` em mobile card buttons)
- Adaptive dashboard KPI grid `grid-cols-1 sm:grid-cols-2 lg:grid-cols-4` + bloco "Hoje" `md:hidden` com eventos do dia na Agenda

---

## v1.9 v1.9 (Shipped: 2026-06-17)

**Phases completed:** 3 phases, 3 plans, 10 tasks

**Key accomplishments:**

- Refatoração completa do data layer e das páginas do módulo de agenda do LexCV de snake_case para camelCase, com tratamento de fuso horário em campos de data, alinhando com a serialização padrão Jackson/Spring Boot do backend.
- Validação robusta de intervalos de datas no frontend e no backend, com tratamento de exceções de parsing de data no Spring Boot e exibição de toasts de erro detalhados no Next.js.
- Exposição global de prazos no backend, unificação de eventos e prazos no calendário mensal da agenda com filtros avançados e spinners de carregamento dinâmico.

---

## v1.8 Deployment para VPS (Shipped: 2026-06-16)

**Phases completed:** 3 phases (37-39), 4 plans, ~9 tasks

**Key accomplishments:**

- Dockerfiles multi-stage para Spring Boot (Maven → JRE Alpine) e Next.js (pnpm → standalone runner), ambos com non-root appuser e zero secrets em camadas de imagem
- Docker Compose com 4 serviços (postgres, backend, frontend, caddy), volumes nomeados persistentes (lexcv_pgdata, lexcv_uploads) e override de produção com restart policies e resource limits
- Caddy como reverse proxy com HTTPS automático via Let's Encrypt — Caddyfile.prod parametrizado por DOMAIN_NAME
- GitHub Actions CI/CD: build + push para GHCR com cache Maven/pnpm, deploy via SSH com appleboy/ssh-action na VPS Hostinger
- DEPLOYMENT.md runbook VPS completo com firewall, env setup, docker compose, e secrets GitHub Actions documentados

**Known deferred items at close:** 3 (runtime verification requires live VPS — see STATE.md)

---

## v1.7 Melhoria no modulo de gestao e acompanhamento de processos (Shipped: 2026-06-16)

**Phases completed:** 5 phases, 11 plans, 14 tasks

**Key accomplishments:**

- One-liner:
- One-liner:
- One-liner:
- One-liner:
- TypeScript types, Zod schemas, lib/prazos.ts helper, and five TanStack Query hooks wiring the Plan 01 workflow/prazos endpoints to the frontend with correct cache invalidation.
- Task 1 — shadcn Dialog + Textarea install
- AuditLog JPA entity, AuditLogRepository, TimelineItemDto record, Movimentacao.autorId, GET /timeline (processos:view), GET /audit (processos:manage), and audit injection at 4 sensitive write points in ResourceController
- TypeScript types (TimelineItem, TimelineItemType, AuditLogEntry) added to processos.ts; useTimeline and useAuditLog TanStack Query hooks added to use-processos.ts with timeline cache invalidation wired into both mutation hooks
- Tab system restructured in page.tsx: Timeline is default tab with dot-and-line feed and filter bar; Auditoria tab added with RBAC gate; Movimentacoes tab removed

---

## v1.0 MVP (Shipped: 2026-05-26)

**Phases completed:** 6 phases, 14 plans, 0 tasks

**Key accomplishments:**

- MVP web completo (Dashboard, Clientes, Processos, Agenda, Documentos, Financeiro) com mock API `/api/v1`
- Autenticação mock (login/refresh/me) e seed multi-tenant
- Navegação institucional com RBAC básico no UI

---

## v1.1 UI/UX Alignment (Shipped: 2026-05-27)

**Phases completed:** 4 phases, 6 plans, 0 tasks

**Key accomplishments:**

- Novo DashboardShell (sidebar escura + top app bar) alinhado ao Figma
- Componentes UI base (badge/table/pagination) e aplicação nas páginas principais
- Dark/Light mode e ajuste visual “Anti-Safe Harbor” (sharp edges, high contrast)

---

## v1.2 Utilizador (Shipped: 2026-05-27)

**Phases completed:** 1 phases, 1 plans, 0 tasks

**Key accomplishments:**

- Painel de utilizador (perfil) com edição de dados no UI
- Migração para backend real (Spring Boot + PostgreSQL) e integração via rewrites
- Seed real com fixtures coerentes e recálculo de conta corrente
