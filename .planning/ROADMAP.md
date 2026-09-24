# Roadmap: LexCV — v2.18 Rebrand ALCv → LexCV

**Milestone:** v2.18
**Granularity:** standard
**Phases:** 129–132 (continuing from v2.17's last phase, 128 — numbering never restarts)

## Phases

- [x] **Phase 129: Identidade e Documentação** - Documentação técnica ativa, manual do utilizador e documentação comercial passam a referir "LexCV"
- [ ] **Phase 130: Frontend — Marca LexCV** - Ambas as apps frontend (`web/`, `webpage/`) e os scripts de verificação manual passam a mostrar/referir "LexCV"
- [ ] **Phase 131: Backend — Tenant Reservado LexCV** - Tenant reservado da plataforma renomeado no código, com migração SQL documentada para bases de dados já provisionadas
- [ ] **Phase 132: Configuração** - `pom.xml` e ficheiros `docker-compose*.yml` passam a referir "LexCV"

## Phase Details

### Phase 129: Identidade e Documentação
**Goal**: Toda a documentação técnica ativa, o manual do utilizador (e os scripts que o geram) e a documentação comercial referem o produto como "LexCV", não "ALCv"
**Depends on**: Nothing (first phase, pura substituição textual)
**Requirements**: IDENT-01, IDENT-02, IDENT-03
**Success Criteria** (what must be TRUE):
  1. `CLAUDE.md`, `DEPLOYMENT.md`, `backend/migrations/README.md` e `.trae/documents/SPEC.md` referem "LexCV"; uma busca por "alcv" (case-insensitive) nesses ficheiros não devolve nenhuma ocorrência de marca
  2. `docs/MANUAL_DO_UTILIZADOR.md` e os scripts geradores (`docs/html/build.py`, `docs/pptx/build.js`, `docs/pptx/theme.js`) referem "LexCV"; regenerar `docs/MANUAL_DO_UTILIZADOR.html`/`.pptx` a partir desses scripts produz saída com "LexCV" e sem erros
  3. `business/README.md`, todo o conteúdo em `business/documentacao/*` e `business/propostas/*`, e `business/scripts/gerar-docx.py` referem "LexCV"
**Plans**: TBD

### Phase 130: Frontend — Marca LexCV
**Goal**: Todo o texto de marca visível ao utilizador nas apps `web/` e `webpage/`, e os scripts de verificação manual do frontend, usam "LexCV"
**Depends on**: Nothing (independente de Phase 129 e 131 — ficheiros distintos)
**Requirements**: FRONT-01, FRONT-02, FRONT-03
**Success Criteria** (what must be TRUE):
  1. App `web/` mostra "LexCV" em título de página, metadata, e em todo texto de marca visível (`dashboard-shell.tsx`, `layout.tsx`, `setup/page.tsx`, `settings/page.tsx`, ficha de cliente/processo, `plataforma/*`)
  2. App `webpage/` (landing pública) mostra "LexCV" nos componentes/libs de marca (`brand-mark.tsx`, `site-footer.tsx`, `branding.ts`, `contacto.ts`, `layout.tsx`)
  3. `pnpm build` corre limpo em `web/` e em `webpage/` depois das alterações
  4. Os 3 scripts de verificação manual (`web/scripts/verify-consola-tenants.mjs`, `verify-papeis-escritorio.mjs`, `verify-relatorio-utilizacao.mjs`) referem "LexCV" nos seus textos/logs e continuam a correr sem erro
**Plans**: TBD
**UI hint**: yes

### Phase 131: Backend — Tenant Reservado LexCV
**Goal**: O tenant reservado da plataforma passa a chamar-se "LexCV" (não "ALCv") em todo o código backend, com uma migração SQL documentada para renomear a linha já existente em instalações já provisionadas
**Depends on**: Nothing (independente de Phase 129/130 — código backend distinto; BACK-01/BACK-02 mantidos juntos por tocarem a mesma área)
**Requirements**: BACK-01, BACK-02
**Success Criteria** (what must be TRUE):
  1. `DatabaseSeeder`, `PublicController`, `AuthController`, `PlatformAdminController`, `Tenant`, `TenantRepository` e `MigracaoPapeisEscritorioService` referem "LexCV" como nome do tenant reservado, não "ALCv"
  2. Os testes associados (`AuthControllerTenantSuspensoTest`, `PlatformAdminControllerTest`, `PublicControllerTest`, `DatabaseSeederPlataformaAdminTest`, `MigracaoPapeisEscritorioServiceTest`, `SetupServiceInstanciacaoMoldesTest`) passam com a nova string, e `mvn test` corre limpo no backend
  3. Um arranque do backend com seed contra uma base de dados limpa cria o tenant reservado já como "LexCV"
  4. Existe uma nova migração SQL em `backend/migrations/` que renomeia a linha de tenant "ALCv" já existente em bases de dados de instalações já provisionadas, com entrada correspondente em `backend/migrations/README.md`
**Plans**: TBD

### Phase 132: Configuração
**Goal**: Os ficheiros de configuração e build do projeto (Maven, Docker Compose) referem "LexCV"
**Depends on**: Nothing (independente das restantes fases — ficheiros de configuração, não código de aplicação)
**Requirements**: CONFIG-01
**Success Criteria** (what must be TRUE):
  1. `backend/pom.xml` (`<description>`) refere "LexCV"
  2. `docker-compose.hostinger.yml` e quaisquer outros ficheiros `docker-compose*.yml` que contenham a string de marca referem "LexCV"
  3. `mvn -DskipTests package` continua a funcionar sem erros depois da alteração ao `pom.xml`
**Plans**: TBD

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|-----------------|--------|-----------|
| 129. Identidade e Documentação | 1/1 | Complete | 2026-09-24 |
| 130. Frontend — Marca LexCV | 0/TBD | Not started | - |
| 131. Backend — Tenant Reservado LexCV | 0/TBD | Not started | - |
| 132. Configuração | 0/TBD | Not started | - |

## Coverage

- v1 requirements: 9 total
- Mapped to phases: 9/9 ✓
- Unmapped: 0

| Requirement | Phase |
|-------------|-------|
| IDENT-01 | 129 |
| IDENT-02 | 129 |
| IDENT-03 | 129 |
| FRONT-01 | 130 |
| FRONT-02 | 130 |
| FRONT-03 | 130 |
| BACK-01 | 131 |
| BACK-02 | 131 |
| CONFIG-01 | 132 |

---
*Roadmap created: 2026-09-24*
