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

### Active

(None — iniciar próximo milestone)

### Out of Scope

- Integração real com Keycloak — adiar até existir backend de autenticação institucional
- Regras de negócio avançadas (cálculo de honorários, prazos jurídicos, workflows) — responsabilidade do backend
- Contabilidade completa/ERP — fora do MVP
- Mobile app nativo — Web/PWA primeiro; desktop via Tauri numa fase posterior

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

---
*Last updated: 2026-05-27 after conclusão do milestone v1.1 (UI/UX)*
