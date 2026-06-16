# Milestones

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
