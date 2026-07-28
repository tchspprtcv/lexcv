# Requirements: LexCV

**Defined:** 2026-07-28
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

**Milestone:** v2.16 — Distribuição Multi-Tenant e Faturação por Utilizadores. Base: `proposta_multitenancy_distribuicao_faturacao.md` (28 jul 2026).

## v1 Requirements

Requisitos para a milestone v2.16. Cada um mapeia para uma fase do roadmap.

### PLAN — Limite de Utilizadores

- [ ] **PLAN-01**: Tenant tem campos `plano` e `limite_utilizadores`
- [ ] **PLAN-02**: Criar utilizador é bloqueado (409) quando o tenant atinge `limite_utilizadores` (conta só `ativo=true`)
- [ ] **PLAN-03**: Frontend mostra "X/Y utilizadores" e desativa "novo utilizador" no limite
- [ ] **PLAN-04**: Desativar utilizador liberta vaga imediatamente no limite

### PROV — Provisionamento Multi-Tenant

- [ ] **PROV-01**: Papel `PLATAFORMA_ADMIN`, distinto do `ADMIN` de cada escritório, associado a uma tenant reservada "LexCV"
- [ ] **PROV-02**: Administrador de plataforma cria um novo tenant + utilizador ADMIN inicial, num ecrã interno não público
- [ ] **PROV-03**: Administrador de plataforma lista todos os tenants e vê utilizadores ativos por tenant
- [ ] **PROV-04**: Administrador de plataforma ajusta `plano`/`limite_utilizadores` de qualquer tenant
- [ ] **PROV-05**: Administrador de plataforma suspende um tenant que não pague (bloqueia acesso)
- [ ] **PROV-06**: Wizard `/setup` deixa de ser singleton — fica só para o arranque inicial; tenants seguintes usam o fluxo de administrador de plataforma

### ISOL — Fechar Suposições de Tenant Única

- [ ] **ISOL-01**: Landing pública mostra sempre marca genérica LexCV (deixa de tentar mostrar branding "da" tenant)
- [ ] **ISOL-02**: Nenhum caminho de código assume "a" tenant (`findFirstByOrderByCreatedAtAsc` ou equivalente) quando existir mais de uma tenant real
- [ ] **ISOL-03**: `PUT /api/v1/admin/rbac` deixa de ser editável por tenant — gestão de permissões por papel passa a ser fixa para toda a plataforma
- [ ] **ISOL-04**: Auditoria de isolamento dedicada cobre as novas superfícies (ecrã de tenants, relatório de utilização, bloqueio RBAC) antes de existir um 2º tenant pagante real

### UTIL — Relatório de Utilização/Faturação

- [ ] **UTIL-01**: Relatório interno (só administrador de plataforma) mostra, por tenant: nome, plano, limite contratado, utilizadores ativos agora

## v2 Requirements

Diferido para uma milestone futura. Reconhecido mas fora do roadmap atual.

### Distribuição

- **ONBOARD-01**: Onboarding self-service público (escritório regista-se e paga sem intervenção)
- **ONBOARD-02**: Subdomínio/domínio próprio por escritório

### Faturação

- **UTIL-02**: Pico de utilizadores ativos por mês no relatório (útil só se decidido faturar pelo pico mensal em vez do valor instantâneo)
- **UTIL-03**: Integração automática de cobrança (Stripe ou equivalente) — confirmar primeiro cobertura para Cabo Verde/escudo
- **PLAN-05**: Entidade `Subscription` separada com histórico de mudanças de plano ao longo do tempo

## Out of Scope

Explicitamente excluído. Documentado para prevenir scope creep.

| Feature | Reason |
|---------|--------|
| `tenant_id` em `Role`/`Permission` (RBAC verdadeiramente por-escritório) | Ninguém pediu esta funcionalidade ainda; mudança maior de schema. A v2.16 vai no sentido contrário — torna RBAC fixo por plataforma, não editável por tenant |
| Descontinuar o modelo isolado por cliente | O modelo dedicado (VPS/Postgres/MinIO próprios, `docker-compose` atual) mantém-se disponível como opção "enterprise/dedicado" para clientes que exijam infraestrutura própria — não é substituído pelo modelo partilhado |

## Traceability

Que fases cobrem que requisitos. Preenchido durante a criação do roadmap.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PLAN-01 | TBD | Pending |
| PLAN-02 | TBD | Pending |
| PLAN-03 | TBD | Pending |
| PLAN-04 | TBD | Pending |
| PROV-01 | TBD | Pending |
| PROV-02 | TBD | Pending |
| PROV-03 | TBD | Pending |
| PROV-04 | TBD | Pending |
| PROV-05 | TBD | Pending |
| PROV-06 | TBD | Pending |
| ISOL-01 | TBD | Pending |
| ISOL-02 | TBD | Pending |
| ISOL-03 | TBD | Pending |
| ISOL-04 | TBD | Pending |
| UTIL-01 | TBD | Pending |

**Coverage:**
- v1 requirements: 15 total
- Mapped to phases: 0
- Unmapped: 15 ⚠️ (preenchido pelo roadmapper)

---
*Requirements defined: 2026-07-28*
*Last updated: 2026-07-28 after initial definition*
