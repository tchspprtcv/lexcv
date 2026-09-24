# Requirements: LexCV — v2.18 Rebrand ALCv → LexCV

**Defined:** 2026-09-24
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

Requisitos para o marco v2.18. Cada um mapeia para fases do roadmap.

### Identidade e Documentação (IDENT)

- [ ] **IDENT-01**: Documentação técnica ativa (`CLAUDE.md`, `DEPLOYMENT.md`, `backend/migrations/README.md`, `.trae/documents/SPEC.md`) refere o produto como "LexCV"
- [ ] **IDENT-02**: Manual do utilizador (`docs/MANUAL_DO_UTILIZADOR.md`/`.html`/`.pptx` e os scripts que os geram — `docs/html/build.py`, `docs/pptx/build.js`, `docs/pptx/theme.js`) refere o produto como "LexCV"
- [ ] **IDENT-03**: Documentação comercial (`business/README.md`, `business/documentacao/*`, `business/propostas/*`, `business/scripts/gerar-docx.py`) refere o produto como "LexCV"

### Frontend (FRONT)

- [ ] **FRONT-01**: App `web/` — títulos de página, metadata, e todo texto de marca visível ao utilizador (`dashboard-shell.tsx`, `layout.tsx`, `setup/page.tsx`, `settings/page.tsx`, ficha de cliente/processo, `plataforma/*`) usa "LexCV"
- [ ] **FRONT-02**: App `webpage/` (landing pública) — componentes e libs de marca (`brand-mark.tsx`, `site-footer.tsx`, `branding.ts`, `contacto.ts`, `layout.tsx`) usam "LexCV"
- [ ] **FRONT-03**: Scripts de verificação manual (`web/scripts/verify-consola-tenants.mjs`, `verify-papeis-escritorio.mjs`, `verify-relatorio-utilizacao.mjs`) usam "LexCV" nos textos/logs

### Backend e Dados (BACK)

- [ ] **BACK-01**: Tenant reservado da plataforma renomeado de "ALCv" para "LexCV" no código (`DatabaseSeeder`, `PublicController`, `AuthController`, `PlatformAdminController`, `Tenant`, `TenantRepository`, `MigracaoPapeisEscritorioService`) e nos testes associados (`AuthControllerTenantSuspensoTest`, `PlatformAdminControllerTest`, `PublicControllerTest`, `DatabaseSeederPlataformaAdminTest`, `MigracaoPapeisEscritorioServiceTest`, `SetupServiceInstanciacaoMoldesTest`)
- [ ] **BACK-02**: Migração SQL manual documentada em `backend/migrations/` para renomear a linha de tenant "ALCv" já existente em bases de dados de instalações já provisionadas, com entrada correspondente em `backend/migrations/README.md`

### Configuração (CONFIG)

- [ ] **CONFIG-01**: `backend/pom.xml` (`<description>`) e `docker-compose.hostinger.yml` (e demais compose files com a string) usam "LexCV"

## Out of Scope

Explicitamente excluído deste marco. Documentado para evitar scope creep.

| Item | Razão |
|------|-------|
| Arquivo histórico de marcos (`.planning/milestones/*`, `.planning/research/*`, `.planning/MILESTONES.md`, `.planning/RETROSPECTIVE.md`) | Registo do que já aconteceu — reescrever apagaria o histórico real de decisões já enviadas. Decisão explícita do utilizador. |
| Domínio real de produção / DNS (`alcv.tech` em `.env.example`) | Decisão de infraestrutura/DNS separada de uma renomeação de marca no código. Decisão explícita do utilizador. |
| Nome do repositório GitHub / identificador remoto | Fora de âmbito de uma renomeação de código e documentação. |
| Nova identidade visual (logo, paleta de cores) | Pedido do utilizador foi apenas a substituição textual da marca "ALCv" → "LexCV", não um redesign visual. |

## Traceability

Preenchido durante a criação do roadmap.

| Requirement | Phase | Status |
|-------------|-------|--------|
| IDENT-01 | — | Pending |
| IDENT-02 | — | Pending |
| IDENT-03 | — | Pending |
| FRONT-01 | — | Pending |
| FRONT-02 | — | Pending |
| FRONT-03 | — | Pending |
| BACK-01 | — | Pending |
| BACK-02 | — | Pending |
| CONFIG-01 | — | Pending |

**Coverage:**
- v1 requirements: 9 total
- Mapped to phases: 0
- Unmapped: 9 ⚠️ (roadmap ainda por criar)

---
*Requirements defined: 2026-09-24*
*Last updated: 2026-09-24 after initial definition (milestone v2.18)*
