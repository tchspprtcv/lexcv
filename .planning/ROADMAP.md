# Roadmap: ALCv — Marco v2.17 RBAC por Escritório

## Overview

Este marco substitui o modelo actual de papéis globais fixos-por-plataforma por papéis próprios de cada escritório, instanciados como cópia (snapshot) dos moldes que a plataforma define. A sequência é deliberadamente conservadora: primeiro o catálogo de permissões sai do código para a base de dados (nada pode ler/escrever moldes ou papéis de escritório antes disso existir); depois a plataforma ganha os moldes e o mecanismo de instanciação, provado primeiro no caminho de menor risco (provisionamento de um escritório novo); só then a migração reaproveita esse mesmo mecanismo para reconverter, sem perda nem ganho de acesso, todos os escritórios já existentes; só depois disso é seguro abrir os ecrãs de escritório para edição; e a auditoria fecha o marco registando cada alteração de papel e de atribuição de forma imutável.

## Phases

**Phase Numbering:**
- Continua a numeração do marco anterior — a última fase concluída foi a 123 (mais a inserção pós-auditoria 124 do v2.16). Este marco começa em **Phase 124**.
- Fases inteiras (124, 125, ...): trabalho planeado deste marco.
- Fases decimais (124.1, 124.2, ...): inserções urgentes pós-planeamento, se necessário.

- [x] **Phase 124: Catálogo de Permissões em Base de Dados** - O catálogo de permissões deixa de ser uma lista embutida no controller e passa a viver em `t_permission`, semeado e actualizado com segurança a cada arranque (completed 2026-09-20)
- [x] **Phase 125: Moldes da Plataforma e Provisionamento** - `PLATAFORMA_ADMIN` gere moldes de papel em `/plataforma`; todo escritório novo nasce com cópias próprias desses moldes (completed 2026-09-21)
- [x] **Phase 126: Migração de Papéis Existentes** - Cada escritório já existente é convertido para papéis próprios, com verificação formal de que ninguém ganha nem perde acesso (completed 2026-09-21)
- [x] **Phase 127: Papéis e Permissões do Escritório** - Administrador de escritório cria, edita, renomeia, apaga e atribui os papéis do seu próprio escritório, com isolamento total de outros tenants e do `PLATAFORMA_ADMIN` (completed 2026-09-22)
- [ ] **Phase 128: Auditoria de Atribuições de Papéis** - Toda a alteração de papel e de atribuição fica registada de forma imutável e consultável por escritório

## Phase Details

### Phase 124: Catálogo de Permissões em Base de Dados
**Goal**: O catálogo de permissões (nome técnico, rótulo, descrição, categoria) é servido a partir de `t_permission`, não de uma lista Java hardcoded, e sobrevive a qualquer reinício sem apagar atribuições existentes.
**Depends on**: Nada (primeira fase do marco; continua directamente da Phase 123 do marco anterior)
**Requirements**: CATL-01, CATL-02, CATL-03
**Success Criteria** (what must be TRUE):
  1. O catálogo de permissões devolvido pela aplicação reflecte linhas de `t_permission` (rótulo, descrição, categoria incluídos), não a lista Java hardcoded que existia em `AdminController.getRbac`
  2. Reiniciar o backend semeia permissões novas e actualiza rótulo/descrição/categoria das existentes, sem apagar nenhuma atribuição já persistida (`t_role_permission`, `t_user_permission`)
  3. As permissões reservadas à plataforma (as que definem `PLATAFORMA_ADMIN`) nunca aparecem em nenhum catálogo servido a um escritório
**Plans**: 2 plans

Plans:
- [x] 124-01-PLAN.md — Colunas de catálogo em `Permission` + script manual de migração + `seedRbac()` com catálogo declarativo de 20 permissões em upsert não-destrutivo
- [x] 124-02-PLAN.md — `getRbac()` serve `systemPermissions` a partir de `t_permission`, excluindo reservadas à plataforma e sem-rótulo, com contrato de resposta inalterado

### Phase 125: Moldes da Plataforma e Provisionamento
**Goal**: A plataforma passa a gerir os moldes de papel numa consola própria, e todo o mecanismo de "instanciar cópia de um molde" nasce e é provado primeiro no caminho de menor risco — um escritório novo — antes de ser reutilizado pela migração.
**Depends on**: Phase 124 (moldes compõem-se a partir do catálogo de permissões já em base de dados)
**Requirements**: MOLD-01, MOLD-02, MOLD-03, MOLD-04
**Success Criteria** (what must be TRUE):
  1. `PLATAFORMA_ADMIN` consulta e edita os moldes de papel existentes através da consola `/plataforma`
  2. `PLATAFORMA_ADMIN` cria um molde novo, que passa a estar disponível para instanciação em qualquer escritório provisionado a partir desse momento
  3. Provisionar um escritório novo instancia automaticamente uma cópia própria de cada molde actual, pronta a ser atribuída de imediato
  4. Editar um molde depois de já ter sido instanciado não tem qualquer efeito sobre os papéis já copiados para escritórios existentes (cópia é snapshot, não referência viva)
**Plans**: TBD
**UI hint**: yes

Plans:
- [x] 125-01: TBD

### Phase 126: Migração de Papéis Existentes
**Goal**: Todo escritório que já existe antes deste marco é convertido, por script manual, para papéis próprios — sem que um único utilizador ganhe ou perca uma única permissão efectiva.
**Depends on**: Phase 125 (reutiliza o mesmo mecanismo de instanciação de molde já provado no provisionamento de tenants novos)
**Requirements**: MIGR-01, MIGR-02, MIGR-03
**Success Criteria** (what must be TRUE):
  1. Correr a migração instancia os moldes actuais em cada tenant já existente e reaponta cada `t_user_role` para o papel de escritório equivalente, preservando exactamente as atribuições anteriores
  2. Uma verificação pós-migração compara, utilizador a utilizador, o conjunto de permissões efectivas antes e depois da migração, e falha de forma visível perante qualquer divergência
  3. `backend/migrations/README.md` e a secção de arranque em duas fases do `DEPLOYMENT.md` documentam o lugar exacto deste script na sequência de deploy
**Plans**: 5 plans (4 waves)

Plans:
- [x] 126-01-PLAN.md — Associação `User` → `TenantRole` em `t_user_tenant_role` (coexistindo com `t_user_role`), script manual `127` e documentação de deploy (MIGR-03)
- [x] 126-02-PLAN.md — Resolvedor único de autoridade (colapsando a união triplicada) e verificação de deriva zero capaz de reprovar (MIGR-02)
- [x] 126-03-PLAN.md — Conversão convergente por tenant reutilizando `instanciarMoldes`, runner de arranque e verificação na mesma transacção
- [x] 126-04-PLAN.md — Cutover de leitura em `JwtAuthenticationFilter`, `AuthController` e `AdminController`, mais o caminho de escrita de papéis que tem de acompanhar
- [x] 126-05-PLAN.md — Os três sítios de lógica de negócio passam de comparação por nome para proveniência (`TenantRole.moldeId`)

### Phase 127: Papéis e Permissões do Escritório
**Goal**: O administrador de um escritório gere por inteiro os papéis do seu próprio escritório — sem depender da plataforma para nada disto — com a certeza absoluta de que nada do que faz alcança outro tenant ou o papel da própria plataforma.
**Depends on**: Phase 126 (só é seguro tornar o ecrã editável depois de cada escritório já ter os seus próprios papéis migrados)
**Requirements**: PAPEL-01, PAPEL-02, PAPEL-03, PAPEL-04, PAPEL-05, PAPEL-06, PAPEL-07, PAPEL-08, PAPEL-09, CATL-04
**Success Criteria** (what must be TRUE):
  1. Administrador de escritório vê, cria, renomeia, edita as permissões e atribui os papéis do seu próprio escritório (e só desse) através de `GET/PUT /admin/rbac` tenant-scoped, gated por `hasAuthority('rbac:manage')`
  2. Alterar as permissões de um papel tem efeito na sessão já aberta dos utilizadores afectados, sem exigir novo login
  3. Nenhuma acção de um administrador de escritório tem qualquer efeito visível ou persistido sobre os papéis, permissões ou atribuições de outro escritório
  4. Um papel atribuído a um utilizador não pode ser apagado; um papel sem atribuições pode; o papel de administrador do próprio escritório nunca pode ser apagado nem despojado das permissões que o tornam administrador
  5. `PLATAFORMA_ADMIN` nunca é listado, nunca é atribuível e é inalcançável a partir de qualquer ecrã ou endpoint de escritório
**Plans**: 8 plans
**UI hint**: yes

Plans:
- [x] 127-01-PLAN.md — `UserPrincipal` ganha proveniência de molde; as duas guardas por nome de `ParecerController` passam a decidir por proveniência (dívida 2a)
- [x] 127-02-PLAN.md — DTOs do contrato de escritório, contagem de atribuições, e `/api/v1/admin` passa a ser guardado por autoridade (`users:manage` / `rbac:manage`) em vez do nome do papel ADMIN
- [x] 127-03-PLAN.md — `GET/PUT /admin/rbac` tenant-scoped sobre `t_tenant_role`, gate ISOL-03 reformado por escrito, regra de piso do papel de administrador, isolamento provado com dois tenants
- [x] 127-04-PLAN.md — `OfficeRolesController`: criar, renomear e apagar papéis próprios, com recusa por contagem (PAPEL-05) e por proveniência (PAPEL-08)
- [x] 127-05-PLAN.md — atribuição de papéis a utilizadores por id, espelho derivado de `t_user_role`, e `tenant_role_ids` no contrato de leitura (Decisão 6)
- [x] 127-06-PLAN.md — contratos de frontend: tipos, schema Zod, fusão de edições não gravadas por id, e reescrita de `use-admin.ts` sem `mock-db`
- [x] 127-07-PLAN.md — reescrita do `RbacTab` como consola editável de papéis do escritório, com painel de criação, diálogo de renomeação e confirmação de eliminação
- [x] 127-08-PLAN.md — selector de papéis por id em Gestão de Utilizadores, reescrita do gate `verify:bloqueio-rbac` para provar a nova garantia, e verificação humana ao vivo

### Phase 128: Auditoria de Atribuições de Papéis
**Goal**: Toda a alteração de papel e de atribuição de papel fica registada de forma permanente, com autoria e alvo, e é consultável — mas nunca editável — por escritório.
**Depends on**: Phase 127 (a auditoria regista os caminhos de escrita que essa fase cria)
**Requirements**: AUDT-01, AUDT-02, AUDT-03, AUDT-04
**Success Criteria** (what must be TRUE):
  1. Criar, alterar ou apagar um papel produz um registo de auditoria com autor, momento e o que mudou
  2. Atribuir ou remover um papel a um utilizador produz um registo de auditoria com autor, momento e utilizador alvo
  3. Administrador de escritório consulta o registo de auditoria do seu próprio escritório, e apenas desse
  4. Nenhum ecrã ou endpoint da aplicação permite editar ou apagar um registo de auditoria já criado
**Plans**: TBD
**UI hint**: yes

Plans:
- [ ] 128-01: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 124 → 125 → 126 → 127 → 128

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 124. Catálogo de Permissões em Base de Dados | 2/2 | Complete   | 2026-09-20 |
| 125. Moldes da Plataforma e Provisionamento | 6/6 | Complete   | 2026-09-21 |
| 126. Migração de Papéis Existentes | 5/5 | Complete   | 2026-09-21 |
| 127. Papéis e Permissões do Escritório | 8/8 | Complete   | 2026-09-22 |
| 128. Auditoria de Atribuições de Papéis | 0/TBD | Not started | - |

---
*Roadmap created: 2026-09-20*
*Granularity: standard (5 phases for 24 requirements)*
