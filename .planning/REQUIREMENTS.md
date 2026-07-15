# Requirements: LexCV — Milestone v2.12

**Defined:** 2026-07-15
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

Requisitos desta milestone (v2.12 — Landing Page). Cada um mapeia para exatamente uma fase do roadmap.

### Backend (Endpoint Público de Branding)

- [ ] **LP-01**: Existe um novo endpoint público `GET /api/v1/public/branding` que devolve exclusivamente `nome` e `logoDataUrl` da tenant (nunca NIF, email, telefone ou qualquer outro campo), via uma DTO explícita — nunca a entidade `Tenant` diretamente
- [ ] **LP-02**: O endpoint está registado na allowlist do `SecurityConfig` como uma entrada exata (nunca um wildcard tipo `/api/v1/public/**`)

### Webpage (Nova App Next.js Standalone)

- [ ] **LP-03**: Existe uma nova aplicação Next.js standalone `webpage/` (independente da app `web/` existente), que serve a landing page pública
- [ ] **LP-04**: Utilizador (autenticado ou não) que visita `/` vê sempre a landing page completa — nunca é redirecionado automaticamente para `/dashboard` ou `/login` só por ter sessão ativa
- [ ] **LP-05**: Se o sistema ainda não foi inicializado (wizard `/setup` nunca correu), visitar `/` redireciona para `/setup`
- [ ] **LP-06**: Quando o sistema está inicializado, a landing mostra o nome e o logo da tenant (via o endpoint da LP-01)
- [ ] **LP-07**: A landing tem uma secção Hero com título, subtítulo e proposta de valor
- [ ] **LP-08**: A landing tem uma secção de Funcionalidades/Módulos, destacando Clientes, Processos, Agenda/Prazos, Documentos, Financeiro e Notificações
- [ ] **LP-09**: A landing tem uma secção de Prova Social/Confiança Institucional, com mensagens verificáveis (isolamento de dados multi-tenant, RBAC, auditoria, ecossistema NOSi/Cabo Verde) — nunca testemunhos ou logótipos de clientes fabricados
- [ ] **LP-10**: A landing tem uma secção de Contacto/Pedir Demonstração com um link `mailto:` fixo (não sourced de `Tenant.email`/`telefone`)
- [ ] **LP-11**: O CTA principal "Entrar" é um link `<a href="/login">` simples (nunca `next/link`/navegação client-side, por ser cross-zone), visível no topo e no fundo da página
- [ ] **LP-12**: A landing é responsiva e suporta dark/light mode, reutilizando os componentes shadcn/ui e convenções Tailwind já usados em `web/`

### Infraestrutura (Routing e Deployment)

- [ ] **LP-13**: A app `webpage/` usa o padrão Next.js Multi-Zones (`assetPrefix` próprio) para que os seus assets `_next/static/*` nunca colidam com os de `web/` sob o mesmo domínio
- [ ] **LP-14**: O Caddy roteia `/` (e o `assetPrefix` da webpage) para o novo container `webpage`; todas as outras rotas (`/login`, `/dashboard`, `/setup`, `/api/*`) continuam a ir para `web/`/`backend/` inalteradas — atualizado consistentemente nas 3 fontes de configuração existentes (`Caddyfile`, `Caddyfile.prod`, o heredoc inline em `docker-compose.hostinger.yml`)
- [ ] **LP-15**: Existe um novo serviço `webpage` nos 3 ficheiros docker-compose (dev/prod/hostinger), com um Dockerfile próprio (build multi-stage, `output: standalone`, mesmo padrão de `web/Dockerfile`)
- [ ] **LP-16**: O pipeline de CI/CD (`deploy.yml`) constrói e publica a imagem `webpage` como um 3º artefacto, ao lado de `backend`/`web`

## v2 Requirements

Nenhum item diferido nesta milestone — os candidatos P2 identificados na pesquisa (imagem OG dinâmica personalizada, screenshots reais da UI) ficam registados como ideias futuras, não requisitos formais, dado o âmbito pequeno e focado desta milestone.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Onboarding self-service multi-instituição, slug/subdomínio por tenant | Decisão explícita do utilizador — este deployment continua a servir uma única instituição; o wizard `/setup` mantém-se singleton |
| Logos/testemunhos de clientes na secção de prova social | Estruturalmente impossível de sourced honestamente — não existe registo cross-tenant/cross-deployment em nenhuma parte da stack |
| Backend de captura de leads persistido (CRM-like) para "Pedir demonstração" | Âmbito novo significativo (entidade, controller, RBAC, proteção anti-spam) — não é um detalhe de landing page; o link `mailto:` fixo cobre a necessidade atual |
| Página de preços pública / self-serve signup ou trial | Provisionamento de novas instituições continua manual/institucional, fora desta milestone |
| Blog/CMS, live chat, alternância multi-idioma, tour interativo de produto, página de comparação com concorrentes, analytics/cookie-consent | Todos desproporcionais ou inconsistentes com o âmbito real desta milestone (pesquisa FEATURES.md) |
| Favicon/OG-image/robots.txt próprios da `webpage/` | Decisão explícita do utilizador — sem requisito de SEO nesta milestone; usa os ficheiros estáticos já servidos por `web/`, evita branches extra de routing no Caddy |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| LP-01 | Phase 98 | Pending |
| LP-02 | Phase 98 | Pending |
| LP-03 | Phase 99 | Pending |
| LP-04 | Phase 99 | Pending |
| LP-05 | Phase 99 | Pending |
| LP-06 | Phase 99 | Pending |
| LP-07 | Phase 99 | Pending |
| LP-08 | Phase 99 | Pending |
| LP-09 | Phase 99 | Pending |
| LP-10 | Phase 99 | Pending |
| LP-11 | Phase 99 | Pending |
| LP-12 | Phase 99 | Pending |
| LP-13 | Phase 100 | Pending |
| LP-14 | Phase 100 | Pending |
| LP-15 | Phase 100 | Pending |
| LP-16 | Phase 100 | Pending |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 16 (100%)
- Unmapped: 0

**Phase summary:**
- Phase 98 (Backend — Endpoint Público de Branding): LP-01, LP-02 — parallelizable with Phase 99
- Phase 99 (webpage/ — Nova App Next.js de Landing): LP-03 to LP-12 — parallelizable with Phase 98
- Phase 100 (Infraestrutura — Routing e Deployment): LP-13 to LP-16 — depends on Phase 98 and Phase 99

---
*Requirements defined: 2026-07-15*
*Last updated: 2026-07-15 after roadmap creation (100% coverage, 3 phases: 98-100)*
