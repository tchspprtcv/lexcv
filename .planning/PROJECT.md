# LexCV

## What This Is

LexCV é uma plataforma institucional de gestão jurídica para Cabo Verde (ecossistema NOSi), focada em centralizar clientes, processos, agenda/prazos, documentos e financeiro básico. O produto é multi-entidade (multi-tenant) e desenhado para operação segura, com frontend Web responsivo como primeira entrega.

## Core Value

Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## Requirements

### Validated

- ✓ MVP Web (Next.js App Router) com mock backend `/api/v1` e seed multi-tenant — v1.0
- ✓ Autenticação JWT mock (login/refresh/me) e sessão no frontend — v1.0
- ✓ Dashboard com KPIs básicos — v1.0
- ✓ Clientes (CRUD + filtros + conta corrente) — v1.0
- ✓ Processos (CRUD + partes + fases + movimentações) — v1.0
- ✓ Agenda/Eventos (CRUD + filtros críticos + concluir) — v1.0
- ✓ Documentos (listagem + upload/download + delete) — v1.0
- ✓ Financeiro (honorários + pagamentos + impacto na conta corrente) — v1.0
- ✓ RBAC básico (ex.: Financeiro visível para ADMIN/TECNICO) — v1.0
- ✓ UI/UX alinhado ao Figma (Dashboard, Clientes, Processos, Agenda) — v1.1
- ✓ Layout institucional padronizado (sidebar + top app bar) — v1.1
- ✓ Componentes UI reutilizáveis (badges, tabelas) para consistência visual — v1.1
- ✓ Melhoria no modulo de gestao e acompanhamento de processos (intake, conflict check, workflow, timeline, auditoria, governanca documental, dashboards) — v1.7
- ✓ Deployment para VPS — Dockerfiles multi-stage, Docker Compose 4 serviços, Caddy HTTPS automático, CI/CD GitHub Actions → GHCR → SSH VPS — v1.8 (implantado via Hostinger VPS Connector)
- ✓ Melhoria Módulo Agendamento — alinhamento camelCase, validações robustas e visão unificada no calendário com filtros por processo/categoria/status — v1.9
- ✓ Responsividade App — shell mobile com drawer/hamburger/bottom-nav, mobile cards em todas as listas, scroll horizontal em tabelas complexas, formulários coluna única, bottom-sheet dialogs, 48px touch targets, KPI grid adaptável — v2.3

### Active

- Numeração sequencial de clientes (`numero_cliente`, ex: CLI-0001), por tenant — v2.4
- Tipo de cliente Particular vs. Empresa com formulário dinâmico — v2.4
- Campos demográficos para Particular (idade, sexo, nacionalidade, BI/Pass) — v2.4
- Dados de entidade coletiva para Empresa (nome comercial, NIF, sede, representante legal, cargo) — v2.4
- Procuração obrigatória para todos os clientes (upload de documento) — v2.4
- Flag "Cliente Avençado" visível na ficha e listagens — v2.4
- Campos de intake: descrição do caso, advogados atribuídos (nome, cédula, contacto), administrativos atribuídos — v2.4
- Documentos entregues vs. a tratar (por cliente) — v2.4
- Deslocações a realizar (por cliente) — v2.4
- Honorários propostos no intake (totalidade, por extenso, previsão) — v2.4
- Vista de Ficha Cliente imprimível (reproduz formulário real do escritório) — v2.4

## Current Milestone: v2.4 Ficha de Cliente

**Goal:** Adaptar o módulo de clientes para espelhar a ficha real do escritório, com tipo de cliente (Particular/Empresa), numeração sequencial automática e ficha imprimível.

**Target features:**
- Numeração sequencial de clientes gerada automaticamente no backend, por tenant
- Formulário dinâmico por tipo de cliente (Particular vs. Empresa/Coletivo)
- Campos demográficos (Particular) e dados de entidade coletiva (Empresa)
- Procuração obrigatória para todos os clientes
- Flag "Cliente Avençado"
- Campos de intake: descrição do caso, advogados e administrativos atribuídos
- Documentos entregues vs. a tratar, deslocações a realizar
- Honorários propostos no intake
- Vista de Ficha Cliente imprimível

### Out of Scope

- Integração real com Keycloak — adiar até existir backend de autenticação institucional
- Regras de negócio avançadas (cálculo de honorários, prazos jurídicos, workflows) — responsabilidade do backend
- Contabilidade completa/ERP — fora do MVP
- Mobile app nativo — Web/PWA primeiro; desktop via Tauri numa fase posterior
- Notificações push / email — apenas in-app neste milestone
- Recorrência infinita (sem data de fim) — requer paginação especial, adiado
- Editar todas as instâncias futuras de uma série — apenas "esta instância" ou "toda a série"

## Context

- Referência funcional e técnica do frontend: `.trae/documents/SPEC.md`
- Contrato e convenções REST para o mock: `.trae/documents/API-Design.md` (base `/api/v1`)
- Modelo relacional (fonte de verdade para entidades): `.trae/documents/ERD.sql`
- Backend alvo: Spring Boot (frontend deve permanecer “passivo”, apenas apresentar dados e executar ações)

## Constraints

- **Stack**: Next.js App Router + TypeScript strict + Tailwind + shadcn/ui
- **Data Fetching**: TanStack Query para toda interação com API (sem `useEffect` para chamadas de negócio)
- **Forms**: React Hook Form + Zod (sem `any`)
- **Multi-tenant**: não expor `tenant_id` em URLs; contexto injetado via JWT/header
- **Segurança**: não logar tokens; evitar armazenar segredo em client; respeitar RBAC no UI

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Mock API dentro do Next.js (route handlers) em `/api/v1/*` | Acelerar desenvolvimento do UI sem depender do backend Spring | ✓ Good |
| Dashboard-first para validar arquitetura | Validar navegação e módulos cedo | ✓ Good |
| Fixtures/seed alinhadas ao ERD | Facilitar prototipagem e UAT inicial | ✓ Good |
| Frontend “burro”: sem regras de negócio | Evitar deriva de contrato e duplicação | ✓ Good |
| UI institucional alinhada ao Figma (top bar + sidebar + páginas-chave) | Consistência visual e usabilidade institucional | ✓ Good |
| Caddy como reverse proxy com HTTPS automático | Zero config TLS — Let's Encrypt provisionado automaticamente quando domínio real configurado | ✓ Good |
| GHCR como container registry | Gratuito, integrado GitHub Actions, sem serviço externo | ✓ Good |
| Next.js output: standalone | Imagem Docker sem node_modules — runtime mínimo com node server.js | ✓ Good |
| docker-compose.prod.yml como override | Separação dev/prod sem duplicar o compose base | ✓ Good |
| Validação de intervalo de datas (dataFim >= dataInicio) no cliente e servidor | Garantir integridade dos dados e evitar intervalos negativos | ✓ Good |
| Redirecionamento de prazos no calendário para o detalhe do processo | Prazos não possuem visualização individual de detalhes; ligá-los ao processo associado | ✓ Good |
| `md:` (768px) como breakpoint mobile/desktop, `max-sm:` para bottom-sheet | Consistência com shadcn/ui defaults e Tailwind breakpoints | ✓ Good |
| sheet.tsx criado manualmente (sem CLI interativo) | CLI `npx shadcn` exige setup interativo; seguiu padrão de dialog.tsx com @radix-ui/react-dialog | ✓ Good |
| Dual-view pattern CSS puro (`hidden md:block` / `md:hidden`) | Sem JS branching, sem rerenders — simples e performante | ✓ Good |
| React fragments obrigatórios em siblings dentro de ternário JSX | Bug descoberto em Phase 54 — sibling divs sem wrapper causam erro de parse | ✓ Good |

## Current State

**Shipped:** v2.3 (2026-06-21) — Responsividade App. LexCV totalmente responsivo em mobile/tablet: shell com drawer/hamburger/bottom-nav, mobile card lists em 4 módulos, scroll horizontal em tabelas complexas, formulários coluna única, bottom-sheet dialogs, 48px touch targets, KPI grid adaptável (1→2→4 colunas), bloco "Hoje" em Agenda mobile.

**v2.2** (2026-06-19) — Document Storage MinIO. Backend migrado de filesystem para MinIO (AWS S3 SDK), upload com barra de progresso e drag-and-drop, downloads via URL pré-assinada, serviço MinIO no Docker Compose prod com Caddy.

**v2.1** (2026-06-18) — Agenda Avançada. Notificações in-app (badge + popover), recorrência de eventos (DAILY/WEEKLY/MONTHLY), drag & drop no calendário com atualização otimista.

**v2.0** (2026-06-18) — Módulo Financeiro. Migração camelCase completa, CRUD honorários/pagamentos, status badges, 4 KPI cards, filtros, CSV export.

**Current focus:** Planning next milestone

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? -> Move to Out of Scope with reason
2. Requirements validated? -> Move to Validated with phase reference
3. New requirements emerged? -> Add to Active
4. Decisions to log? -> Add to Key Decisions
5. "What This Is" still accurate? -> Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check - still the right priority?
3. Audit Out of Scope - reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-29 — Milestone v2.4 started (Ficha de Cliente)*
