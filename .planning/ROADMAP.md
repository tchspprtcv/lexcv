# Roadmap: LexCV

## Milestones

- ✅ **v2.0 Módulo Financeiro** — Phases 43–46 (complete 2026-06-18)
- ✅ **v2.1 Agenda Avançada** — Phases 47–49 (complete 2026-06-18)
- ✅ **v2.2 Document Storage MinIO** — Phases 50–52 (complete 2026-06-19)
- ✅ **v2.3 Responsividade App** — Phases 53–56 (complete 2026-06-21)
- ✅ **v2.4 Ficha de Cliente** — Phases 57–60 (complete 2026-06-30)
- ✅ **v2.5 Módulo de Parecer Jurídico** — Phases 61–64 (complete 2026-06-30)
- ✅ **v2.6 Módulo de Parecer Jurídico — UI** — Phases 65–69 (complete 2026-07-01)
- ✅ **v2.7 Melhoria Gestão de Clientes** — Phases 70–73.1 (complete 2026-07-02)
- ✅ **v2.8 Refatoração Ficha de Cliente** — Phases 74–79 (complete 2026-07-06)
- 🚧 **v2.9 Melhoria Módulo Processos** — Phases 80–84 (in progress)


## Phases

<details>
<summary>✅ v2.0 Módulo Financeiro (Phases 43–46) - SHIPPED 2026-06-18</summary>

### Phase 43: Data Layer + Backend Endpoints
**Goal**: O contrato de dados entre frontend e backend está correto (camelCase) e o CRUD completo de honorários e pagamentos está disponível via API
**Depends on**: Nothing (first phase of milestone)
**Requirements**: FIN-01, FIN-02, FIN-03, FIN-04, FIN-05, FIN-06
**Plans**: 2/2 — completed 2026-06-18

### Phase 44: Status + KPIs
**Goal**: A página financeiro apresenta o estado calculado de cada honorário e um resumo financeiro em cards no topo
**Depends on**: Phase 43
**Requirements**: FIN-07, FIN-08, FIN-09, FIN-10
**Plans**: 2/2 — completed 2026-06-18

### Phase 45: Filtros + Edit/Delete UI
**Goal**: O utilizador pode filtrar a lista de honorários e executar ações de edição e eliminação diretamente na UI
**Depends on**: Phase 44
**Requirements**: FIN-11, FIN-12, FIN-13, FIN-14, FIN-15, FIN-16
**Plans**: 2/2 — completed 2026-06-18

### Phase 46: CSV Export
**Goal**: O utilizador pode exportar a lista de honorários (com filtros aplicados) para um ficheiro CSV
**Depends on**: Phase 45
**Requirements**: FIN-17
**Plans**: 1/1 — completed 2026-06-18

</details>

### ✅ v2.1 Agenda Avançada (SHIPPED 2026-06-18)

**Milestone Goal:** Adicionar notificações in-app de eventos próximos, suporte a eventos recorrentes e drag & drop no calendário para mover eventos.

- [x] **Phase 47: Notificações In-App** - Badge no header com contagem de eventos próximos e painel de notificações
- [x] **Phase 48: Recorrência de Eventos** - Criar, listar, exibir e apagar eventos com regras de recorrência (completed 2026-06-18)
- [x] **Phase 49: Drag & Drop no Calendário** - Arrastar eventos para nova data com atualização imediata via API (completed 2026-06-18)

### ✅ v2.2 Document Storage MinIO (SHIPPED 2026-06-19)

**Milestone Goal:** Migrar o armazenamento de documentos do filesystem local para MinIO (object storage S3-compatible), atualizar o componente de upload no frontend e configurar o deploy no Hostinger VPS.

- [x] **Phase 50: Backend MinIO Integration** - Spring Boot integrado ao MinIO via AWS S3 SDK; upload, download pré-assinado e delete no bucket (completed 2026-06-19)
- [x] **Phase 51: Frontend Upload Component** - Barra de progresso, drag-and-drop, preview inline e download via URL pré-assinada (completed 2026-06-19)
- [x] **Phase 52: Deploy MinIO no Hostinger** - Serviço MinIO no Docker Compose prod, credenciais via env vars, CI/CD atualizado e consola via Caddy (completed 2026-06-19)

<details>
<summary>✅ v2.3 Responsividade App (Phases 53–56) — SHIPPED 2026-06-21</summary>

- [x] **Phase 53: Shell Responsivo** — Hamburger drawer, top bar compacta e bottom navigation em mobile (completed 2026-06-21)
- [x] **Phase 54: Listas e Tabelas** — Cards empilhados em listas simples e scroll horizontal em tabelas complexas (completed 2026-06-21)
- [x] **Phase 55: Formulários e Modais** — Coluna única, bottom-sheet/full-screen e touch targets 48px em mobile (completed 2026-06-21)
- [x] **Phase 56: Dashboard e Calendário** — KPI grid adaptável e vista diária por defeito no calendário em mobile (completed 2026-06-21)

See archive: [milestones/v2.3-ROADMAP.md](milestones/v2.3-ROADMAP.md)

</details>

<details>
<summary>✅ v2.4 Ficha de Cliente (Phases 57–60) — SHIPPED 2026-06-30</summary>

- [x] **Phase 57: Backend Schema + API** — Extensão da entidade Cliente com novos campos, geração de numero_cliente, endpoints atualizados (completed 2026-06-30)
- [x] **Phase 58: Formulário Dinâmico** — Formulário frontend com seletor de tipo, campos demográficos/empresa, flag avençado e exibição do número (completed 2026-06-30)
- [x] **Phase 59: Procuração + Intake** — Upload obrigatório de procuração e secção de intake (advogados, administrativos, docs, deslocações, honorários propostos) (completed 2026-06-30)
- [x] **Phase 60: Ficha Imprimível** — Vista dedicada que reproduz a ficha real do escritório com botão de impressão (completed 2026-06-30)

Auditoria de integração pós-execução encontrou um mismatch snake_case/camelCase (9/19 requisitos afectados) e uma fuga de password hash — ambos corrigidos antes do fecho. Ver archive para detalhes completos.

See archive: [milestones/v2.4-ROADMAP.md](milestones/v2.4-ROADMAP.md) · [milestones/v2.4-MILESTONE-AUDIT.md](milestones/v2.4-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.5 Módulo de Parecer Jurídico (Phases 61–64) - SHIPPED 2026-06-30</summary>

API backend completa para o ciclo Solicitação → Elaboração → Aprovação → Entrega, com auditoria automática e pesquisa avançada. Backend-only por decisão explícita — UI frontend fica para milestone futura (v2.6). Ver Scope Note no archive.

See archive: [milestones/v2.5-ROADMAP.md](milestones/v2.5-ROADMAP.md) · [v2.5-MILESTONE-AUDIT.md](../v2.5-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.6 Módulo de Parecer Jurídico — UI (Phases 65–69) — SHIPPED 2026-07-01</summary>

Interface frontend completa para o Módulo de Parecer Jurídico, sobre a API já entregue no v2.5: `/pareceres` lista (dual-view, badges, filtros), detalhe com timeline imutável de versões, criação de solicitação, elaboração de versões (resumo + anexo obrigatório na UI), entrega irreversível com confirmação e vista dedicada "Parecer Entregue" (fecha o gap PARC-09 do audit v2.5), pesquisa avançada, e RBAC espelhado em toda a UI (incluindo verificação de instância advogado-responsável/ADMIN). Auditoria de milestone encontrou e corrigiu um bug de routing pré-existente desde a v2.5 (`pesquisar()` inacessível em runtime — extraído para `ParecerPesquisaController`, commit 657bcbc). NOTF-05/06/07 removidas do âmbito v1 (sem sistema de notificações genérico no backend). Ver Scope Note no archive.

- [x] Phase 65: Fundação — Listagem e Detalhe (PARC-11, PARC-12) — 2/2 plans, completed 2026-07-01
- [x] Phase 66: Criação de Solicitação (PARC-13) — 1/1 plan, completed 2026-07-01
- [x] Phase 67: Elaboração e Versionamento (PARV-05, PARV-06) — 1/1 plan, completed 2026-07-01
- [x] Phase 68: Entrega, Vista de Entregue e RBAC (PARC-14, PARC-15, PARC-16) — 1/1 plan, completed 2026-07-01
- [x] Phase 69: Pesquisa Avançada (PARS-03) — 1/1 plan, completed 2026-07-01

See archive: [milestones/v2.6-ROADMAP.md](milestones/v2.6-ROADMAP.md) · [milestones/v2.6-MILESTONE-AUDIT.md](milestones/v2.6-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.7 Melhoria Gestão de Clientes (Phases 70–73.1) — SHIPPED 2026-07-02</summary>

Simplificação e aplanamento do modelo de identificação e contactos de clientes (remoção de `dados_tipo` JSON em prol de colunas diretas). Backend flat-column model + `REG_COMERCIAL` (Phase 70), tipos TypeScript e schema Zod aplanados com NIF obrigatório (Phase 71), formulários de criação/edição com labels dinâmicas Nome/Nome Comercial e Morada/Sede (Phase 72), página de detalhe e ficha imprimível atualizadas (Phase 73). Auditoria de milestone encontrou um gap no CLI-05 (NIF podia ser sobrescrito silenciosamente por um campo legado, sem validação server-side) — fechado pela Phase 73.1 (inserida), cujo próprio code review encontrou e corrigiu uma regressão adicional (validação JPA-lifecycle bloqueando saves não relacionados em registos legados). Re-auditoria confirmou 7/7 requisitos satisfeitos. Ver Scope Note no archive.

- [x] Phase 70: Backend refactoring & Seeder Alignment (CLI-06, CLI-09) — 1/1 plan, completed 2026-07-01
- [x] Phase 71: Frontend Types, Schema & API Integration (CLI-05, CLI-06) — 2/2 plans, completed 2026-07-01
- [x] Phase 72: Form Refactoring (Create & Edit) (CLI-05, CLI-07, CLI-08, CLI-09, CLI-10) — 1/1 plan, completed 2026-07-02
- [x] Phase 73: Detail Page & Printable Ficha Update (CLI-11) — 1/1 plan, completed 2026-07-02
- [x] Phase 73.1: Fechar gap CLI-05 (INSERTED, gap closure) — 1/1 plan, completed 2026-07-02

See archive: [milestones/v2.7-ROADMAP.md](milestones/v2.7-ROADMAP.md) · [milestones/v2.7-MILESTONE-AUDIT.md](milestones/v2.7-MILESTONE-AUDIT.md)

</details>

<details>
<summary>✅ v2.8 Refatoração Ficha de Cliente (Phases 74–79) — SHIPPED 2026-07-06</summary>

Ficha de cliente unificada (view/edit num só componente via toggle Editar, rota `/editar` removida — Phase 75) e reestruturada em 7 separadores estilo botões-toggle de processos (Phase 76): Dados (identificação isolada como sub-secção), Contactos e Notas, Processos/Pareceres (Phase 77, wiring de baixo risco contra hooks já existentes), Documentos a Tratar/Deslocações (Phase 78, relocalização pura), Documentos Entregues (Phase 79, upload real via novo endpoint `GET /clientes/{id}/documentos`). Enum `documento_tipo` restruturado antes de tudo (Phase 74: `BI` adicionado, `NIF` removido, restrito por tipo de cliente, com 2 rondas de gap-closure para preservar valores legados). Auditoria de fase (79) encontrou e fechou 3 bugs críticos: incompatibilidade `cliente_id`/`clienteId` que quebrava silenciosamente a associação de uploads, link de download incorreto, falta de validação de posse de tenant no upload. Auditoria de milestone: 20/20 requisitos satisfeitos, integração cross-phase totalmente ligada, sem gaps críticos.

- [x] Phase 74: Enum `documento_tipo` (BI/NIF/Restrição por Tipo) (CLI-20 a CLI-24) — 5 plans (2 gap-closure), completed 2026-07-04
- [x] Phase 75: Componente Único View/Edit (CLI-12, CLI-13, CLI-14) — 3 plans (2 waves), completed 2026-07-04
- [x] Phase 76: Separadores — Dados, Contactos e Notas (CLI-15, CLI-18, CLI-19) — 1 plan, completed 2026-07-05
- [x] Phase 77: Separadores — Processos e Pareceres (CLI-16, CLI-17) — 1 plan, completed 2026-07-05
- [x] Phase 78: Separadores — Documentos a Tratar e Deslocações (CLI-30, CLI-31) — 1 plan, completed 2026-07-06
- [x] Phase 79: Documentos Entregues — Upload Real (CLI-25 a CLI-29) — 2 plans (2 waves), completed 2026-07-06

See archive: [milestones/v2.8-ROADMAP.md](milestones/v2.8-ROADMAP.md) · [milestones/v2.8-MILESTONE-AUDIT.md](milestones/v2.8-MILESTONE-AUDIT.md)

</details>

### 🚧 v2.9 Melhoria Módulo Processos (In Progress)

**Milestone Goal:** Aprofundar o módulo de processos com dados jurídicos estruturados (Juízo, origem/tramitação), sub-secções de Decisões/Factos/Testemunhas, aba de documentos dedicada e criação automática do contrato de honorário na formalização — seguindo padrões internacionais de gestão processual.

É um milestone de "aplicar padrão existente a um módulo novo": todas as sete funcionalidades mapeiam diretamente para padrões já entregues e validados no v2.4 (Ficha Cliente imprimível) e v2.8 (upload de Documentos, abas lazy-mount, entidades filhas estilo `ClienteContacto`/`ClienteNota`). Nenhuma dependência, biblioteca ou padrão arquitetural novo é necessário. A ordem das fases segue uma cadeia de dependência estrita — fundação de dados → endpoints backend → tipos/hooks frontend → UI frontend — replicando a disciplina de sequenciamento já usada no v2.8 (Phase 74→75), para que os valores de enum (`TipoDecisao`, `Testemunha.tipo`) não mudem depois dos schemas Zod já estarem escritos. A criação automática de Honorário (Phase 82) foi isolada da Phase 81 por ser independentemente paralelizável (nenhuma dependência nas três entidades novas) e por concentrar o risco financeiro/idempotência mais sensível do milestone (flagged pela pesquisa como o pitfall de maior severidade).

#### Phase 80: Fundações — Processo.juizo/origem + Entidades Decisão/Facto/Testemunha
**Goal**: A estrutura de dados jurídicos do processo (Juízo, Origem, Decisões, Factos, Testemunhas) existe na base de dados, estável e pronta para os endpoints e a UI construírem sobre ela, sem qualquer mudança visível para o utilizador ainda.
**Depends on**: Nothing (first phase of milestone)
**Requirements**: PROC-01, PROC-06, PROC-09, PROC-11
**Success Criteria** (what must be TRUE):
  1. A entidade `Processo` tem uma coluna `juizo` (texto livre) e uma coluna `origem` (enum `OrigemProcesso`: Petição Inicial | Notificações Avulsas)
  2. Existem três entidades novas — `Decisao` (data, tipo enum `TipoDecisao`: Despacho | Decisão Interlocutória | Sentença | Acórdão, resumo, anexo opcional), `Facto` (descrição, data, ordem por processo), `Testemunha` (nome, contacto, tipo enum: Autor | Réu, notas) — cada uma com FK `processo_id`, sem coluna `tenant_id` própria (isolamento transitivo via processo pai, mesmo padrão de `Parte`)
  3. Cada entidade nova tem um repositório Spring Data JPA correspondente
  4. A aplicação arranca e persiste corretamente as tabelas novas (`ddl-auto=update`), sem quebrar nenhum fluxo existente de Processo
**Plans**: 1 plan
Plans:
- [x] 80-01-PLAN.md — Enums (OrigemProcesso/TipoDecisao/TipoTestemunha) + Processo.juizo/origem + Decisao/Facto/Testemunha entities + repositories

#### Phase 81: Backend — CRUD Decisões/Factos/Testemunhas + Wiring Juízo/Origem
**Goal**: A API expõe CRUD completo e seguro para Decisões, Factos e Testemunhas, e os campos Juízo/Origem estão totalmente integrados no ciclo de vida do Processo (criação, edição, intake e listagem).
**Depends on**: Phase 80
**Requirements**: PROC-02, PROC-03, PROC-04, PROC-05, PROC-07, PROC-08, PROC-10, PROC-12, PROC-17
**Success Criteria** (what must be TRUE):
  1. Endpoints `GET/POST/PUT/DELETE /processos/{id}/decisoes`, `/factos`, `/testemunhas` (12 endpoints) funcionam sob os scopes `processos:view`/`processos:edit` já existentes, cada operação de escrita revalida tenant do processo pai E `processoId` da entidade filha (padrão `ProcessoFase`, não o padrão simples de `Parte`)
  2. O endpoint de criação de Decisão aceita upload multipart direto (cria o `Documento` internamente e associa-o), não um seletor de documento pré-existente
  3. Factos podem ser reordenados (campo `ordem` scoped por `processo_id`, não global)
  4. `juizo`/`origem` são persistidos e devolvidos por `createProcesso`, `updateProcesso`, `createProcessoIntake` e aparecem no mapa enriquecido devolvido por `listProcessos`
  5. `origem` é validada como obrigatória tanto em `POST /processos/intake` (que hoje não valida nada) como em `CAMPOS_MINIMOS_POR_TIPO` para todos os valores de `tipo_processo`
**Plans**: 3 plans
Plans:
- [ ] 81-01-PLAN.md — Wiring juizo/origem no ciclo de vida do Processo (intake obrigatorio, update imutavel, listProcessos enriquecido)
- [ ] 81-02-PLAN.md — CRUD Decisao (upload multipart) + Testemunha, com double-check tenant/processoId (PROC-17)
- [ ] 81-03-PLAN.md — CRUD Facto com ordem server-computed/reordenavel, double-check tenant/processoId (PROC-17)

#### Phase 82: Backend — Criação Automática de Honorário na Formalização
**Goal**: Formalizar um processo (TRIAGEM→ATIVO) cria automaticamente e de forma segura um registo de Honorário associado, sem nunca preencher um valor financeiro sem confirmação explícita do utilizador.
**Depends on**: Phase 80 (não depende de Phase 81 — trabalho paralelizável)
**Requirements**: PROC-14
**Success Criteria** (what must be TRUE):
  1. Ao formalizar um processo, um registo de Honorário é criado automaticamente e associado ao processo, dentro de uma transação (`@Transactional`)
  2. Repetir a formalização (retry/replay) não duplica o Honorário — existe uma verificação de existência explícita (`findByProcessoId`) antes da criação, independente do guard de estado
  3. O `valorTotal` do Honorário criado automaticamente começa sempre `null` — nunca é pré-preenchido a partir de `Cliente.honorariosPropostos`
**Plans**: TBD

#### Phase 83: Frontend — Tipos, Schemas e Hooks
**Goal**: A camada de dados do frontend conhece os campos e entidades novos com tipagem e validação corretas, e o mapeamento camelCase/snake_case está coberto para todos eles antes de qualquer UI ser construída.
**Depends on**: Phase 81, Phase 82
**Requirements**: (suporte a PROC-01 a PROC-14, sem requisito dedicado — camada de integração)
**Success Criteria** (what must be TRUE):
  1. `types/processos.ts` inclui `Processo.juizo`/`origem` e os tipos `Decisao`/`Facto`/`Testemunha`
  2. `schemas/processos.ts` inclui `decisaoFormSchema`/`factoFormSchema`/`testemunhaFormSchema`, e `origem` é um `z.enum(...)` obrigatório (não mais `optionalTrimmedString`)
  3. `use-processos.ts` ganha o quarteto de hooks (list/create/update/delete) para cada entidade nova, seguindo a convenção `queryKey` já usada (`["processos", "<subresource>", id]`)
  4. `normalizeProcesso()`/`toProcessoApiPayload()` mapeiam `juizo`/`origem` corretamente — verificado por um teste de round-trip com refresh, não apenas por build limpo (previne a 4ª recorrência do bug de mapeamento já visto 3 vezes neste projeto)
**Plans**: TBD

#### Phase 84: Frontend — UI (Intake, Dados, Sub-secções, Documentos, Termo de Honorários)
**Goal**: O utilizador consegue registar e consultar Juízo/Origem, gerir Decisões/Factos/Testemunhas, aceder a uma aba de Documentos dedicada, e gerar o Termo de Honorários impresso — tudo a partir da ficha do processo.
**Depends on**: Phase 83
**Requirements**: PROC-13, PROC-15, PROC-16
**Success Criteria** (what must be TRUE):
  1. O passo 1 do intake exige a escolha de Origem (Petição Inicial | Notificações Avulsas); depois de formalizado, o campo é visível na ficha mas não editável
  2. Juízo é visível e editável no card "Dados" da ficha do processo, ao lado de Tribunal/Área Jurídica
  3. A ficha do processo ganha quatro abas novas no grupo de botões-toggle já existente — Decisões, Factos, Testemunhas, Documentos — cada uma permitindo listar/criar/editar/remover (Documentos: upload/listagem/download/remoção via `GET /processos/{id}/documentos` já existente)
  4. Uma nova rota `[id]/termo-honorarios` gera um documento imprimível (clone do padrão CSS-print de `clientes/[id]/ficha`) combinando dados de Cliente, Processo e Honorário
  5. Gerar o Termo de Honorários bloqueia ou avisa claramente quando o `valorTotal` do Honorário ainda está em branco, em vez de imprimir campos vazios
**Plans**: TBD
**UI hint**: yes

## Progress

| Phase | Milestone | Plans Complete | Status | Completed |
|-------|-----------|----------------|--------|-----------|
| 43. Data Layer + Backend Endpoints | v2.0 | 2/2 | Complete | 2026-06-18 |
| 44. Status + KPIs | v2.0 | 2/2 | Complete | 2026-06-18 |
| 45. Filtros + Edit/Delete UI | v2.0 | 2/2 | Complete | 2026-06-18 |
| 46. CSV Export | v2.0 | 1/1 | Complete | 2026-06-18 |
| 47. Notificações In-App | v2.1 | 2/2 | Complete | 2026-06-18 |
| 48. Recorrência de Eventos | v2.1 | 2/2 | Complete | 2026-06-18 |
| 49. Drag & Drop no Calendário | v2.1 | 1/1 | Complete | 2026-06-18 |
| 50. Backend MinIO Integration | v2.2 | 2/2 | Complete | 2026-06-19 |
| 51. Frontend Upload Component | v2.2 | 1/1 | Complete | 2026-06-19 |
| 52. Deploy MinIO no Hostinger | v2.2 | 1/1 | Complete | 2026-06-19 |
| 53. Shell Responsivo | v2.3 | 2/2 | Complete | 2026-06-21 |
| 54. Listas e Tabelas | v2.3 | 3/3 | Complete | 2026-06-21 |
| 55. Formulários e Modais | v2.3 | 2/2 | Complete | 2026-06-21 |
| 56. Dashboard e Calendário | v2.3 | 1/1 | Complete | 2026-06-21 |
| 57. Backend Schema + API | v2.4 | 2/2 | Complete | 2026-06-30 |
| 58. Formulário Dinâmico | v2.4 | 4/4 | Complete | 2026-06-30 |
| 59. Procuração + Intake | v2.4 | 6/6 | Complete | 2026-06-30 |
| 60. Ficha Imprimível | v2.4 | 2/2 | Complete | 2026-06-30 |
| 61. Data Layer + Backend CRUD | v2.5 | 2/2 | Complete   | 2026-06-30 |
| 62. Elaboração e Versionamento | v2.5 | 2/2 | Complete   | 2026-06-30 |
| 63. Aprovação e Entrega | v2.5 | 1/1 | Complete   | 2026-06-30 |
| 64. Auditoria e Pesquisa Avançada | v2.5 | 2/2 | Complete   | 2026-07-01 |
| 65. Fundação — Listagem e Detalhe | v2.6 | 2/2 | Complete    | 2026-07-01 |
| 66. Criação de Solicitação | v2.6 | 1/1 | Complete    | 2026-07-01 |
| 67. Elaboração e Versionamento | v2.6 | 1/1 | Complete    | 2026-07-01 |
| 68. Entrega, Vista de Entregue e RBAC | v2.6 | 1/1 | Complete    | 2026-07-01 |
| 69. Pesquisa Avançada | v2.6 | 1/1 | Complete    | 2026-07-01 |
| 70. Backend refactoring & Seeder Alignment | v2.7 | 1/1 | Complete | 2026-07-01 |
| 71. Frontend Types, Schema & API Integration | v2.7 | 2/2 | Complete | 2026-07-01 |
| 72. Form Refactoring (Create & Edit) | v2.7 | 1/1 | Complete | 2026-07-02 |
| 73. Detail Page & Printable Ficha Update | v2.7 | 1/1 | Complete | 2026-07-02 |
| 73.1. Fechar gap CLI-05 (gap closure) | v2.7 | 1/1 | Complete | 2026-07-02 |
| 74. Enum `documento_tipo` (BI/NIF/Restrição por Tipo) | v2.8 | 5/5 | Complete    | 2026-07-04 |
| 75. Componente Único View/Edit | v2.8 | 3/3 | Complete    | 2026-07-04 |
| 76. Separadores — Dados, Contactos e Notas | v2.8 | 1/1 | Complete    | 2026-07-05 |
| 77. Separadores — Processos e Pareceres | v2.8 | 1/1 | Complete    | 2026-07-05 |
| 78. Separadores — Documentos a Tratar e Deslocações | v2.8 | 1/1 | Complete    | 2026-07-06 |
| 79. Documentos Entregues — Upload Real | v2.8 | 2/2 | Complete    | 2026-07-06 |
| 80. Fundações — Processo.juizo/origem + Entidades | v2.9 | 1/1 | Complete    | 2026-07-07 |
| 81. Backend — CRUD + Wiring Juízo/Origem | v2.9 | 0/TBD | Not started | - |
| 82. Backend — Honorário Automático | v2.9 | 0/TBD | Not started | - |
| 83. Frontend — Tipos, Schemas e Hooks | v2.9 | 0/TBD | Not started | - |
| 84. Frontend — UI (Intake, Dados, Abas, Termo) | v2.9 | 0/TBD | Not started | - |

**Next:** Milestone v2.9 roadmap created 2026-07-07. Run `/gsd:plan-phase 80` to start planning the first phase.
