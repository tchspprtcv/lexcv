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
- ✓ Painel de Utilizador (perfil, edição de nome, telefone, e-mail) — v1.2
- ✓ Migração total para backend real em Java Spring Boot 3.4.1 + PostgreSQL — v1.2 (backend)
- ✓ Database seeder real com fixtures dinâmicas coerentes — v1.2 (backend)
- ✓ Recálculo automático de Conta Corrente em base de dados — v1.2 (backend)
- ✓ Integração via Next.js proxy reescrito e desvio de API mock — v1.2 (backend)

### Active

- [ ] Auditoria de controle de acesso e validação de inputs no backend Spring Boot
- [ ] Verificação de XSS e exposição de dados no frontend Next.js
- [ ] Auditoria de autenticação, tokens JWT e políticas de segurança
- [ ] Varredura de segredos hardcoded, tokens vazados e configuração de variáveis de ambiente
- [ ] Análise de dependências desatualizadas com CVEs conhecidas
- [ ] Relatório estruturado (Resumo Executivo + Matriz de Riscos + Plano de Remediação)

## Current Milestone: v1.3 Security Check

**Goal:** Executar uma auditoria de segurança rigorosa no código-fonte e nas configurações do projeto, identificando vulnerabilidades proativamente e gerando um plano de remediação acionável baseado em OWASP Top 10.

**Target features:**
- Auditoria de endpoints Spring Boot (controle de acesso, sanitização de inputs, tratamento de exceções)
- Verificação de componentes frontend Next.js contra XSS e exposição de dados
- Auditoria de autenticação JWT e políticas de segurança do banco de dados
- Varredura de segredos hardcoded e configuração de variáveis de ambiente
- Análise de dependências com CVEs públicas conhecidas
- Geração de relatório acionável com Matriz de Riscos e Plano de Remediação

### Out of Scope

- Integração real com Keycloak — adiar até existir backend de autenticação institucional
- Regras de negócio avançadas (cálculo de honorários, prazos jurídicos, workflows) — responsabilidade do backend
- Contabilidade completa/ERP — fora do MVP
- Mobile app nativo — Web/PWA primeiro; desktop via Tauri numa fase posterior

## Context

- Referência funcional e técnica do frontend: `.trae/documents/SPEC.md`
- Contrato e convenções REST para o mock: `.trae/documents/API-Design.md` (base `/api/v1`)
- Modelo relacional (fonte de verdade para entidades): `.trae/documents/ERD.sql`
- Backend real: Spring Boot 3.4.1 (Java 17+, Maven) ligado ao PostgreSQL local (`localhost:5432/lexcv`)

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
| Migração para backend real em Java Spring Boot + PostgreSQL | Produção real, persistência física e segurança JWT stateless ativa | ✓ Good |
| Integração via Next.js rewrites proxy e desvio de pasta mock | Acoplamento transparente mantendo hooks de dados e contratos de URL intactos | ✓ Good |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-05-27 after início do milestone v1.3 (Security Check)*
