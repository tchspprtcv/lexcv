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
- ✓ Numeração sequencial de clientes (`numero_cliente`, ex: CLI-0001), por tenant — v2.4
- ✓ Tipo de cliente Particular vs. Empresa com formulário dinâmico — v2.4
- ✓ Campos demográficos para Particular (idade, sexo, nacionalidade, BI/Pass) — v2.4
- ✓ Dados de entidade coletiva para Empresa (nome comercial, NIF, sede, representante legal, cargo) — v2.4
- ✓ Procuração obrigatória para todos os clientes (upload de documento, aviso não-bloqueante) — v2.4
- ✓ Flag "Cliente Avençado" visível na ficha e listagens — v2.4
- ✓ Campos de intake: descrição do caso, advogados atribuídos (nome, cédula, contacto), administrativos atribuídos — v2.4
- ✓ Documentos entregues vs. a tratar (por cliente) — v2.4
- ✓ Deslocações a realizar (por cliente) — v2.4
- ✓ Honorários propostos no intake (totalidade, por extenso, previsão) — v2.4
- ✓ Vista de Ficha Cliente imprimível (reproduz formulário real do escritório) — v2.4
- ✓ Módulo de Parecer Jurídico — backend API (solicitação, versionamento imutável, aprovação/entrega, auditoria automática, pesquisa avançada), scope RBAC `pareceres:view/create/edit/manage` — v2.5 (backend-only)
- ✓ Módulo de Parecer Jurídico — UI frontend completa: rotas `/pareceres` (lista dual-view, detalhe+timeline, criação, versionamento com anexo obrigatório, entrega irreversível, vista "Parecer Entregue", pesquisa avançada), hooks TanStack Query, RBAC espelhado (incluindo verificação de instância advogado-responsável/ADMIN) — v2.6
- ✓ NIF obrigatório para Particular e Empresa (validação de 9 dígitos, enforced client-side e server-side) — v2.7
- ✓ Simplificação de dados de identificação (remoção total do card JSON `dados_tipo`, backend e frontend) — v2.7
- ✓ Uso do campo `nome` da tabela cliente para nome (Particular) e nome comercial (Empresa), com labels dinâmicas — v2.7
- ✓ Uso do campo `morada` da tabela cliente para morada (Particular) e sede (Empresa), com labels dinâmicas — v2.7
- ✓ Campo `documento_tipo` para Empresa com valor `REG_COMERCIAL`, número guardado em `documento_numero` — v2.7
- ✓ Formulários de criação e edição de cliente adaptados para campos planos com labels dinâmicas — v2.7
- ✓ Detalhe do cliente e ficha impressa adaptados para a estrutura de dados simplificada — v2.7
- ✓ Enum `documento_tipo` com `BI` (removido `NIF`), opções filtradas por tipo de cliente (Particular: CNI/BI/Passaporte; Empresa: só REG_COMERCIAL), validado em frontend e backend, com preservação de valores legados não conformes em edições que não os alteram — v2.8 (Phase 74)
- ✓ `/clientes/[id]` e `/clientes/[id]/editar` unificados num único componente com toggle Editar/Guardar/Cancelar; rota `/editar` removida por completo; sub-componentes (Contactos, Notas, Advogados/Administrativos, Procuração) gated por `canEditClientes && editable` — v2.8 (Phase 75, verificação visual/UAT ao vivo pendente — ver 75-HUMAN-UAT.md)
- ✓ Ficha de cliente reestruturada em 7 separadores (estilo botões toggle de processos); identificação (NIF/tipo/número) isolada como sub-secção "Identificação" no card "Dados"; Contactos e Notas isolados no seu próprio separador; 5 separadores ainda não implementados mostram placeholder "Em breve" — v2.8 (Phase 76, verificação visual/UAT ao vivo pendente — ver 76-HUMAN-UAT.md)
- ✓ Separadores "Processos" e "Pareceres" da ficha de cliente ligados aos hooks existentes (`useProcessos({cliente_id})`, `usePareceres({clienteId})`), com fetch lazy via montagem condicional, permissões `processos:view`/`pareceres:view` espelhadas no frontend — v2.8 (Phase 77)

### Active

- [ ] "Documentos Entregues" passa a upload real (reutilizando sistema genérico `Documento`/`clienteId`), com combobox de tipo (escolher existente ou escrever novo)

## Current Milestone: v2.8 Refatoração Ficha de Cliente

**Goal:** Transformar a ficha de cliente no formulário central de pesquisa de informação relacionada ao cliente — unificando visualização/edição num único componente e adicionando separadores (tabs) que cobrem processos, pareceres e documentos, seguindo a disposição visual de processos.

**Target features:**
- Unificação view/edit num único componente com toggle "Editar" (diverge deliberadamente do padrão de processos, que usa páginas separadas — decisão explícita do utilizador)
- 7 tabs: Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações
- Identificação (NIF + documento_tipo + documento_numero) movida para o card "Dados" principal
- `documento_tipo`: adicionar `BI`, remover `NIF` (corte limpo), filtrar por tipo de cliente, validado em ambas as camadas
- "Documentos Entregues" migrado de lista de texto para upload real via sistema genérico de `Documento`, com combobox de tipo livre

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
| `numero_cliente` formato CLI-0001, gerado por MAX(numero_sequencial)+1 por tenant, sincronizado em bloco synchronized no controller | Evitar UUID exposto ao utilizador; numeração legível e sequencial sem precisar de uma sequence dedicada na BD | ✓ Good |
| `dados_tipo` como coluna JSON única em `t_cliente` (POJO + AttributeConverter), em vez de colunas separadas por campo | Evita migração de schema a cada novo campo de tipo; mesmo padrão reutilizado em Phase 59 para listas de intake | ✓ Good |
| Procuração não bloqueia submit — aviso visual em vez de validação bloqueante | Realidade do escritório: clientes às vezes só assinam procuração depois da primeira reunião | ✓ Good |
| Advogados/administrativos ligados a Users do sistema (não texto livre) via tabelas de junção tenant-scoped | Permite reutilizar RBAC existente e evita dados duplicados/inconsistentes | ✓ Good |
| `@JsonProperty` cirúrgico por campo em vez de `spring.jackson.property-naming-strategy` global | Auditoria de milestone encontrou backend a emitir camelCase e frontend a ler snake_case nos campos novos do v2.4 — corrigir globalmente teria alto raio de impacto sobre fluxos já em produção (alguns campos pré-existentes como `tenantId`/`createdAt` já têm a mesma inconsistência fora do âmbito desta milestone) | ✓ Good (mitigação cirúrgica; mismatch pré-existente fora do v2.4 fica como dívida técnica para limpeza futura) |
| Nenhuma nova dependência frontend para o módulo de pareceres — reuso total de padrões existentes (Documentos upload, Processos timeline, Clientes user-picker) | Pesquisa de milestone confirmou que toda a UI necessária já tinha um padrão análogo no código; evita fragmentação de bibliotecas | ✓ Good |
| Anexo de versão obrigatório na UI (mais restritivo que o backend, que trata como opcional) | Decisão explícita do utilizador — resumo (`conteúdo`) sem documento anexo não tem valor prático no fluxo real do escritório | ✓ Good |
| Aprovação interna (ADMIN) explicitamente fora do âmbito da v2.6 | Backend já suporta (`pareceres:manage`), mas utilizador confirmou que v2.6 deve cobrir apenas criação de versão + entrega direta + vista de entregue; aprovação fica para v2.7 | ✓ Good (PARC-17 deferred) |
| NOTF-05/06/07 (notificações in-app de atribuição/versão/entrega) removidas do âmbito v1 da v2.6 | Descoberto durante planeamento da Phase 66 que o `NotificationBell` existente (v2.1) só mostra eventos da Agenda — não existe entidade/tabela de notificações genérica no backend; implementar como especificado exigiria trabalho de backend fora do âmbito desta milestone | ✓ Good (evitou expansão de âmbito não autorizada; requer milestone futura dedicada) |
| Execução direta no working tree (sem `isolation="worktree"`) para os executores de plano | Um agente executor spawnado com isolamento de worktree apontou para um checkout desatualizado sem os commits de planeamento recentes, bloqueando a execução; a execução direta funcionou de forma fiável em todas as 5 fases | ✓ Good |
| `pesquisar()` extraído para `ParecerPesquisaController` dedicado (`@RequestMapping("/api/v1/pareceres/pesquisa")`) | Auditoria de integração da milestone encontrou que o método vivia dentro de `ParecerController` (mapeado a `/api/v1/pareceres/solicitacoes`), e o Spring concatena mapeamentos de classe+método independentemente de barra inicial — a rota real nunca correspondeu à documentada, tornando toda a Pesquisa Avançada (Phase 69) inacessível em runtime apesar de passar toda a revisão estática. Bug pré-existente desde a v2.5 (Phase 64), só detectado nesta auditoria de milestone | ✓ Good (corrigido na mesma sessão, commit 657bcbc) |
| `dados_tipo` (coluna JSON única, decisão da v2.4) removida por completo — identificação de cliente aplanada em colunas diretas (`nif`, `documento_tipo`, `documento_numero`) | Reversão deliberada da decisão de v2.4: o padrão JSON-por-tipo mostrou-se mais difícil de validar/manter do que colunas planas para este caso específico (identificação, campo de baixa cardinalidade) — os outros usos de `@Convert`/JSON (documentos, deslocações, honorários) permanecem inalterados | ✓ Good |
| Campo `nif` dedicado passa a única fonte de verdade, substituindo a lógica legada de sincronização a partir de `documento_tipo`/`documento_numero` (frontend E backend) | Auditoria de milestone (v2.7) encontrou um bug de sobrescrita silenciosa: o campo NIF validado podia ser substituído por um valor não validado do campo legado. Fase de fecho de gap (73.1) removeu a lógica em ambas as camadas | ✓ Good |
| `jakarta.persistence.validation.mode: none` no `application.yml`, mantendo `@Valid` ao nível do controller | Adicionar Bean Validation (`@NotBlank`/`@Pattern`) a `Cliente.nif` ativou also a validação JPA-lifecycle (`@PrePersist`/`@PreUpdate`) em todos os `save()`, incluindo operações não relacionadas (upload de procuração, merge de clientes) que não tocam `nif` — quebraria clientes legados com NIF inválido. Code review da Phase 73.1 apanhou isto antes do deploy | ✓ Good |
| `updateCliente` só valida `documento_tipo`/`documento_numero` contra a restrição por tipo quando o valor recebido difere do valor já guardado (`documentoTipoUnchanged` via `Objects.equals`) | Auditoria de fase (Phase 74) encontrou que resubmeter um valor legado inalterado (ex.: Empresa com CNI, permitido antes da v2.8) era rejeitado pelo backend mesmo depois do frontend passar a preservá-lo corretamente — violava a decisão explícita de não forçar migração retroativa de dados. `createCliente` mantém-se totalmente estrito (sem entidade existente para comparar) | ✓ Good |

## Current State

**Shipped:** v2.7 (2026-07-02) — Melhoria Gestão de Clientes. Simplificação e aplanamento do modelo de identificação de clientes: remoção completa do card JSON `dados_tipo` (backend e frontend), tipo de documento `REG_COMERCIAL` para Empresa, NIF obrigatório (9 dígitos, validado client-side e server-side), formulários de criação/edição com labels dinâmicas ("Nome"/"Nome Comercial", "Morada"/"Sede"), e página de detalhe + ficha imprimível atualizadas. Auditoria de milestone encontrou e fechou um gap no NIF obrigatório (fase 73.1 inserida) — ver `.planning/milestones/v2.7-MILESTONE-AUDIT.md`.

**v2.6** (2026-07-01) — Módulo de Parecer Jurídico UI. Interface frontend completa sobre a API do v2.5.

**v2.5** (2026-06-30) — Módulo de Parecer Jurídico (backend-only). API completa para o ciclo Solicitação → Elaboração → Aprovação interna opcional → Entrega.

**v2.4** (2026-06-30) — Ficha de Cliente. Numeração sequencial automática (CLI-0001), formulário dinâmico Particular/Empresa, procuração obrigatória com aviso não-bloqueante, intake completo.

<details>
<summary>Histórico anterior (v1.0–v2.3)</summary>

**v2.3** (2026-06-21) — Responsividade App. LexCV totalmente responsivo em mobile/tablet.
**v2.2** (2026-06-19) — Document Storage MinIO.
**v2.1** (2026-06-18) — Agenda Avançada.
**v2.0** (2026-06-18) — Módulo Financeiro.

Ver `.planning/MILESTONES.md` para histórico completo desde v1.0.

</details>

**Current focus:** Milestone v2.8 (Refatoração Ficha de Cliente) — Phases 74–77 complete, 2 phases remaining (78–79).

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
*Last updated: 2026-07-05 — after Phase 77 (Separadores — Processos e Pareceres) completed in milestone v2.8*
