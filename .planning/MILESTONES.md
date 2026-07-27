# Milestones

## v2.15 Reposicionamento SIJ (Shipped: 2026-07-27)

**Phases completed:** 1 phases, 1 plans, 3 tasks

**Key accomplishments:**

- Copy institucional (landing pública `trust-section.tsx` + `SPEC.md`) e identidade do tenant de demonstração seedado migradas de "NOSi" para "ecossistema do SIJ (Sistema Judicial de Cabo Verde)"; SIJ-01 verificado formalmente sem necessidade de edição adicional.

---

## v2.14 UI/UX Melhorias (Shipped: 2026-07-22)

**Phases completed:** 6 phases, 23 plans, 49 tasks

**Key accomplishments:**

- Four tenant-scoped, accent-folded, exact-match-first native `pesquisarGlobal` queries (Cliente/Processo/Documento/ParecerSolicitacao) plus the `unaccent`/`pg_trgm` extension migration and a Testcontainers IT proving zero cross-tenant leakage against real PostgreSQL
- `GET /api/v1/pesquisa` dedicated controller merging 4 tenant-scoped `pesquisarGlobal` repository calls into one flat `ResultadoPesquisaDto` list, gated per entity type via a net-new programmatic `hasAuthority` helper so a scopeless caller gets 200+[] never 403, with zero Honorario/financeiro branch and a 7-test Mockito RBAC-matrix proof
- Self-contained Ctrl+K/⌘K command palette (cmdk `Command`/`CommandDialog`, `shouldFilter={false}`) consuming Phase 111's `GET /api/v1/pesquisa`, with tipo-grouped bold-highlighted results, sessionStorage recents across all 4 entity types, and permission-gated loading skeletons.
- Decorative topbar `<Input>` replaced with a fake-input trigger button (⌘K/Ctrl K hint) plus a mobile Search icon trigger, both dispatching `SEARCH_OPEN_EVENT` to the single mounted `GlobalSearchDialog`.
- Relocated the existing Estado NativeSelect filter from inside the collapsed "Filtros" advanced panel to the always-visible main filter bar on the Processos list page, closing PEST-01 with a pure JSX move (zero logic/state/handler changes).
- Flipped `--radius` from `0rem` to `0.5rem` in both apps and swept 271 hardcoded `rounded-none` className overrides (271 occurrences across 29 files) that were silently overriding the token via `tailwind-merge`, so the rounded-corner look is now actually visible everywhere except 4 documented legitimate exceptions.
- Whole-app build+lint icon-import gate green, do-not-touch guard clean, and all 11 FICO-01/ICON-01 live interactive acceptance criteria independently verified against a running app — phase ready to close.

---

## v2.13 Refactor UI/UX (shadcn/ui) (Shipped: 2026-07-18)

**Phases completed:** 10 phases, 42 plans, 86 tasks

**Key accomplishments:**

- Package legitimacy gate cleared: all 15 net-new npm packages verified legitimate (real publishers, no typosquats) and approved for install
- shadcn CLI formally initialized in web/ on the Radix base (preset Vega), full semantic token set merged into a single, correctly-ordered :root/.dark block with the institutional colors restored, tw-animate-css swapped in, and a genuine CSS-cascade tie-break bug (wrong `.dark`/`:root` declaration order) found by human visual QA and fixed before sign-off.
- Sonner replaces the deprecated Radix Toast stack behind an unchanged toast.success("Sucesso")/toast.error("Erro") contract (richColors green/red), and `shadcn migrate radix` unifies all 8 remaining hand-rolled components plus the 16 new primitives onto the single `radix-ui` package — closing the milestone's last dual-Radix-tree bridge state in `web/`.
- Whole-app `pnpm build` gate green (badge-gray surface enumerated across 8 files, magic-hex removed, radix unified, radius tokenized, tooltip provider confirmed) plus a live browser + getComputedStyle human sign-off confirming the Rule-B dark-mode elevation change renders correctly and no Rule-C identity/DSR-03 tooltip regression occurred — Phase 102 closes with DSR-01/02/03 satisfied.
- Wired the already-installed `Skeleton`/`Empty` primitives into the institutional Dashboard's KPI cards, Atividade Recente, Prazos Urgentes, and Processos Recentes — replacing a discarded `.isLoading` field and one ad hoc "Sem urgências." string with the official loading/zero-data primitives.
- Package legitimacy gate cleared: @tanstack/react-table approved (same maintainer/org as already-trusted @tanstack/react-query)
- Shared TanStack Table v8 DataTable composition (client-side sort/paginate only, never re-filters) plus the official shadcn Pagination primitive, both built once under `web/src/components/shared/data-table/` for the 5 list-screen adoption plans to consume
- Holistic build + regression-grep gate green; human visual checkpoint performed live across all 5 screens in both light and dark themes — phase approved, closing DTB-01/02/03
- Partes/Fases/Testemunhas tabs converted from raw `<table>` markup to reconciled `Table` primitives (Testemunhas gains an `Avatar` in the Nome cell), and the Documentos tab's `<ul>` list replaced by the shared `DataTable` fed by a new `documentos-columns.tsx` factory.
- Holistic build/lint/regression sweep passed; live human checkpoint found and fixed 2 real regressions (a mobile tab-overflow bug and a repeat of Phase 103's `isLoading` RBAC race, now fixed across all 10 Clientes/Processos page guards); ADVOGADO/ASSISTENTE-specific role checks could not be completed live due to browser-tooling instability late in the session — documented transparently below
- First Popover+Calendar date-picker composition in the project (Portuguese locale, Sunday-first), plus Radix Select migration of the 3 Agenda list filters and the isFetched RBAC race fix on the list/detail pages.
- Popover+Calendar DatePickerField (first in project) driving Agenda's date/time fields, Radix Select list filters, and a bundled RBAC isFetched fix across all 4 Agenda pages — verified live in both themes and across 2 roles.
- Progress/NativeSelect/Select migrations plus a new shared creatable+closed-list Combobox (Popover+Command) across Documentos and Financeiro — verified live, with a severe pre-existing upload bug discovered and flagged separately.
- Build/lint/regression gate plus an 8-point live UAT of Select/NativeSelect/Tooltip/Accordion across the Pareceres module — 6 of 8 checks fully live-verified with concrete evidence, the remaining 2 confirmed via source/pattern analysis after a Browser-pane environment issue emerged mid-session.
- Build/lint regression gate plus a live+source-verified UAT of DropdownMenu/Badge/Progress across the topbar, notification bell, and setup wizard — with the session's recurring Browser-pane instability finally root-caused to a real, pre-existing, unrelated hydration bug rather than accepted as unexplained flakiness.
- Ran the combined Wave-1 (110-01 + 110-02) change set through a holistic build/lint gate and a human-verified mobile-nav + Card/Badge visual checkpoint on `webpage/`'s dev server (port 3001); both passed with no fixes required.

---

## v2.12 Landing Page (Shipped: 2026-07-15)

**Phases completed:** 3 phases, 9 plans, 21 tasks

**Key accomplishments:**

- Novo endpoint público não-autenticado `GET /api/v1/public/branding` (`{nome, logoDataUrl}`), DTO explícito nunca a entidade `Tenant`, allowlist exact-literal no `SecurityConfig` — zero superfície de fuga de PII
- Nova aplicação Next.js 16 standalone `webpage/` servindo a landing page completa (Hero, Funcionalidades, Confiança Institucional, Contacto), dark/light mode, gate de setup próprio sem acoplamento de autenticação, fetch de branding server-to-server (estruturalmente livre de CORS)
- Routing e deployment completos em dev/prod/Hostinger via padrão Next.js Multi-Zones (`assetPrefix`), 3 fontes de configuração Caddy mantidas consistentes, novo artefacto `webpage` publicado no pipeline CI/CD
- Verificação `docker compose up` ao vivo (não apenas checks isolados) encontrou e corrigiu 2 bugs reais em runtime: um fetch com URL relativo que desativaria silenciosamente o gate de redirect `/setup` em todos os ambientes, e uma anotação `@Transactional` em falta que crashava a leitura do logo da tenant via Hibernate/PostgreSQL
- Um bug de deployment pré-existente (anterior a esta milestone) — falta de passthrough de variáveis de ambiente para o Caddy no override de produção — foi também encontrado e corrigido via code review durante esta milestone

---

## v2.11 Auditoria Técnica e Notificações Avançadas (Shipped: 2026-07-14)

**Phases completed:** 8 phases, 21 plans, 39 tasks

**Key accomplishments:**

- (none recorded)

---

## v2.10 Notificações e Alertas (Shipped: 2026-07-10)

**Phases completed:** 5 phases, 14 plans, 29 tasks

**Key accomplishments:**

- Consolidated 4 inconsistent "prazo crítico" computations scattered across the backend into a single shared `RiscoPrazoService`, eliminating the risk of dashboard/agenda/notifications ever disagreeing on what counts as critical.
- Built the project's first persisted notification entity and REST API from scratch (`Notificacao`/`NotificacaoService`/`NotificacaoController`) — strict per-recipient + per-tenant scoping, ADMIN fan-out on creation, no mass-broadcast-by-permission path.
- Wired 4 event-triggered alert types into existing controllers (fase entrada, novo documento, processo atribuído, parecer atribuído), including a brand-new processo reatribuição flow (`PUT /processos/{id}/atribuir` + `ReatribuirResponsavelControl` UI) that didn't exist before this milestone.
- Shipped the codebase's first `@Scheduled`/cross-tenant background job (`AlertasDiariosJob`) — daily 06:00 scan of prazo/evento/honorário risk with 4-layer failure isolation and edge-triggered idempotent notifications (never re-notifying the same risk level twice).
- Rewrote the notification sino (contador with polling+refocus, fused mark-read+navigate) and shipped a new dedicated `/notificacoes` history page (category + read-state filters, real pagination), fully replacing the old eventos-only bell.
- Milestone-wide adversarial code review (3 iterations per phase, every phase) caught and fixed 2 genuine pre-existing bugs unrelated to this milestone's own scope (a `ParecerController` partial-update data-loss bug, an ADMIN-only `/users` endpoint silently blocking non-admin reassignment pickers) plus a same-origin URL bypass class refined across 3 successive hardening rounds — zero critical/blocker findings remained open at close.

---

## v2.9 Melhoria Módulo Processos (Shipped: 2026-07-08)

**Phases completed:** 5 phases, 12 plans, 25 tasks

**Key accomplishments:**

- Added `juizo`/`origem` columns to `Processo` plus three new lean JPA entities (`Decisao`, `Facto`, `Testemunha`) with their enums and repositories, verified via a live Spring Boot startup against local PostgreSQL with `ddl-auto=update`.
- `origem` is now a server-enforced required field at intake and formalização (422 gate, every `tipo_processo` including `default`), `juizo` is persisted via update while `origem` is made immutable post-intake, and both fields now appear in the `GET /processos` list response, not just the detail view.
- 8 new REST endpoints (`GET/POST/PUT/DELETE /processos/{id}/decisoes` and `/testemunhas`) added to `ResourceController.java`, with Decisão's create accepting a direct multipart file upload that builds the `Documento` internally (no pre-existing-document picker), and every write endpoint enforcing the `ProcessoFase`-style double-check ownership pattern (parent tenant + child `processoId` re-check).
- Facto CRUD (GET/POST/PUT/DELETE `/processos/{id}/factos`) with server-computed, processo-scoped, concurrency-safe `ordem` on create and explicit client-controlled `ordem` on update — the phase's final 4 of 12 endpoints.
- `formalizarProcesso()` now auto-creates an empty Honorario placeholder (valorTotal=null) the first time a processo transitions TRIAGEM→ATIVO, guarded by an independent existence-check so retries never create a duplicate.
- TypeScript types and Zod schemas for Decisao/Facto/Testemunha plus Processo.juizo/origem, with origem promoted to a required enum only in the intake flow and three new PT label-map files.
- 12 hooks TanStack Query novos (list/create/update/delete x Decisão/Testemunha/Facto) e mapeamento juizo/origem centralizado num módulo partilhado, com prova de round-trip real executável via Node puro que importa esse mesmo módulo.
- Closed the two small-field gaps blocking the rest of Phase 84: Origem is now a required, validated field on the intake wizard's step 1 (switched from `processoFormSchema` to `processoIntakeFormSchema`), and Juízo is now editable on `processos/[id]/editar/page.tsx` next to Tribunal/Área Jurídica.
- New printable `/processos/{id}/termo-honorarios` route combining Cliente + Processo + Honorário data, with a hard print-block (not just a warning) when `valorTotal` is null.
- Extended processos/[id]/page.tsx's TabKey to 8 values, added read-only Juízo/Origem rows and a conditional Gerar Termo de Honorários button to the Dados card, and refactored the Partes/Fases tabs from a side-by-side grid form to the Dialog "Adicionar" pattern.
- Decisões and Testemunhas tab bodies in `processos/[id]/page.tsx`, using the Dialog Adicionar/Editar pattern with the 8 already-built Phase 83 hooks — Decisão's create form additionally carries a native file input for a single-step multipart anexo upload.
- Factos tab (list/create/edit-with-reorder/delete) and Documentos tab (upload-with-progress/list/download/delete) fill the last two `null` placeholders in `processos/[id]/page.tsx`, closing the sequential 84-03/84-04/84-05 chain on that file.

---

## v2.8 Refatoração Ficha de Cliente (Shipped: 2026-07-06)

**Phases completed:** 6 phases (74–79), 13 plans, 26 tasks

**Key accomplishments:**

- Enum `documento_tipo` restruturado: `BI` adicionado, `NIF` removido por completo (corte limpo), opções filtradas por tipo de cliente (Particular: CNI/BI/Passaporte; Empresa: só Registo Comercial), validado em frontend e backend — incluindo preservação de valores legados não conformes em edições que não os alteram (2 rondas de gap closure)
- `/clientes/[id]` e `/clientes/[id]/editar` unificados num único componente com toggle Editar/Guardar/Cancelar; rota `/editar` removida por completo
- Ficha de cliente reestruturada em 7 separadores (estilo botões-toggle de processos): Dados (com identificação e conta-corrente), Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações
- Separadores Processos e Pareceres ligados aos hooks já existentes (`useProcessos`/`usePareceres`), com fetch lazy via sub-componentes de montagem condicional e permissões `processos:view`/`pareceres:view` espelhadas no frontend
- "Documentos Entregues" passa de lista de texto para upload real de ficheiros, via novo endpoint `GET /clientes/{id}/documentos` (tenant-scoped) e reaproveitamento total do sistema genérico de `Documento` — incluindo combobox de tipo (datalist nativo) e RBAC `documentos:view/edit`
- Auditoria de fase (Phase 79) encontrou e fechou 3 bugs críticos antes do fecho da milestone: incompatibilidade de nomes de campo no upload (`cliente_id` vs `clienteId`, que quebrava silenciosamente toda a associação de documentos ao cliente), link de download incorreto, e falta de validação de posse de tenant em `clienteId`/`processoId` no upload

**Known gaps at close:**

- 3 fases (75, 76, 79) com verificação estática 100% completa mas UAT ao vivo pendente (sem ambiente de browser/BD disponível nesta sessão) — ver `*-HUMAN-UAT.md` de cada fase
- `backend/migrations/74-cleanup-nif-documento-tipo.sql` é um script de execução manual (sem runner de migração no projeto) — deve ser corrido manualmente antes/durante o deploy
- `Cliente.documentosEntregues` (coluna backend) e `DocumentoEntregue` (tipo frontend) ficam órfãos por decisão deliberada (corte limpo, CLI-29), mesmo padrão usado para `dados_tipo` na v2.7

Ver `.planning/milestones/v2.8-MILESTONE-AUDIT.md`, `.planning/milestones/v2.8-ROADMAP.md` e `.planning/milestones/v2.8-REQUIREMENTS.md` para detalhes completos.

---

## v2.7 Melhoria Gestão de Clientes (Shipped: 2026-07-02)

**Phases completed:** 5 phases (70–73.1, includes 1 gap-closure insertion), 6 plans

**Key accomplishments:**

- Backend `Cliente` entity aplanado — remoção completa do card JSON `dados_tipo` (POJO + AttributeConverter), tipo de documento `REG_COMERCIAL` adicionado para Empresa, DatabaseSeeder alinhado
- Tipos TypeScript e Zod schema aplanados no frontend, com NIF obrigatório (regex de 9 dígitos) aplicado a Particular e Empresa
- Formulários de criação e edição de cliente com labels dinâmicas ("Nome"/"Nome Comercial", "Morada"/"Sede") consoante o tipo, campo NIF promovido de "(Legado)" a identificação primária
- Página de detalhe e ficha imprimível atualizadas para a estrutura simplificada — remoção dos campos de Empresa descontinuados (Nome Comercial, Representante Legal, Cargo) da ficha, sem placeholders em branco
- Auditoria de milestone encontrou um gap de integração no CLI-05 (NIF podia ser sobrescrito silenciosamente por lógica legada, sem validação server-side) — fechado por uma fase de gap-closure inserida (73.1), cujo próprio code review apanhou e corrigiu uma regressão adicional (validação JPA-lifecycle bloqueando `save()` não relacionados para clientes legados com NIF inválido) antes do deploy
- Re-auditoria confirmou 7/7 requisitos satisfeitos, zero blockers remanescentes

**Known gaps at close:**

- REG_COMERCIAL e outros valores de `DocumentoTipo` mostrados como string bruta do enum em vez de label traduzida na página de detalhe/ficha — cosmético, não bloqueante
- Nenhum teste automatizado cobre os 4 cenários de validação de NIF introduzidos na Phase 73.1

Ver `.planning/milestones/v2.7-MILESTONE-AUDIT.md` e `.planning/milestones/v2.7-ROADMAP.md` para detalhes completos.

## v2.6 Módulo de Parecer Jurídico — UI (Shipped: 2026-07-01)

**Phases completed:** 5 phases, 6 plans, 15 tasks

**Key accomplishments:**

- 1. [Rule 3 - blocking issue] `zodResolver` type mismatch with `z.default()` on `prioridade`
- "Nova Versão" form on the parecer detail page lets the advogado responsável or ADMIN submit successive immutable versions (resumo + required anexo) via a new `useCreateParecerVersao` XHR-multipart hook with progress bar, reusing the Documentos upload pattern.
- Wires the irreversible "Entregar Parecer" action (AlertDialog + version selector + useEntregarParecer PUT), adds a "Parecer Entregue" read-only summary card for CONCLUIDO solicitações, and closes the 3-phase-recurring CardTitle typography gap across the whole /pareceres module.

---

## v2.5 Módulo de Parecer Jurídico (Shipped: 2026-06-30)

**Phases completed:** 4 phases (61–64), 7 plans

**Key accomplishments:**

- API completa do ciclo de vida do parecer jurídico: Solicitação → Elaboração (versionamento imutável com anexo opcional via StorageService) → Aprovação interna opcional (ADMIN) → Entrega (advogado responsável ou ADMIN, irreversível)
- Scope RBAC dedicado `pareceres:view/create/edit/manage`, seedado por role e espelhado no frontend `permissions.ts`
- Auditoria automática reutilizando `AuditLog` existente em todos os 5 pontos de transição de estado
- Pesquisa avançada combinando texto livre (ILIKE sobre conteúdo da versão mais recente) com filtros de cliente/advogado/status/data
- 5 rondas de code review com correções aplicadas e re-verificadas: 2 IDOR cross-tenant críticos, 1 race condition, 1 mismatch de permissão que tornava inalcançável um ramo de autorização, 1 bug de JOIN que excluía resultados válidos da pesquisa

**Known gaps at close:**

- **Backend-only** — nenhuma UI frontend foi construída para o módulo de pareceres. Decisão explícita e repetida em todas as 4 fases, mas significa que o módulo ainda não é utilizável através da aplicação LexCV, apenas via chamada API direta. Auditoria da milestone classificou como `tech_debt` (não bloqueante). Recomendação: milestone v2.6 dedicada à UI.
- `versaoFinalId` (campo de vínculo à versão entregue) só é visível no JSON genérico das respostas GET existentes, sem vista dedicada "parecer entregue"
- Comparação visual (diff) entre versões não implementada — apenas listagem/detalhe sequencial
- Sem índice full-text dedicado (tsvector/trigram) — `ILIKE` nativo suficiente para o volume atual

Ver `.planning/v2.5-MILESTONE-AUDIT.md` e `.planning/milestones/v2.5-ROADMAP.md` para detalhes completos.

## v2.4 Ficha de Cliente (Shipped: 2026-06-30)

**Phases completed:** 4 phases (57–60), 14 plans, ~30 tasks

**Key accomplishments:**

- Numeração sequencial automática de clientes (CLI-0001) por tenant, com formulário dinâmico que adapta os campos a Particular ou Empresa
- Procuração obrigatória com aviso visual não-bloqueante ("Procuração em falta"), upload/substituição via MinIO
- Intake completo do caso na ficha do cliente: advogados e administrativos ligados a Users do sistema, documentos entregues/a tratar, deslocações, e honorários propostos
- Ficha imprimível de alta fidelidade ao formulário físico do escritório, com CSS de impressão A4 e botão de impressão directa
- Auditoria de integração entre fases (pós-execução) detectou um mismatch sistémico snake_case/camelCase entre backend e frontend que invalidava 9 dos 19 requisitos (dados gravados correctamente mas nunca visíveis no ecrã) e uma fuga de password hash em 2 endpoints novos — ambos corrigidos antes do fecho do milestone

**Known gaps at close:**

- Correcção da auditoria foi verificada estaticamente (mapeamento campo-a-campo + builds limpos), não testada ao vivo contra backend+DB+MinIO — recomenda-se smoke test antes de produção
- Itens não-bloqueantes adiados: allowlist de tipo de ficheiro na procuração, formatação de moeda na ficha impressa, ficha sem ponto de acesso em mobile (ver STATE.md Pending Todos)

---

## v2.3 Responsividade App (Shipped: 2026-06-21)

**Phases completed:** 4 phases (53–56), 8 plans

**Key accomplishments:**

- Shell responsivo: sidebar drawer overlay com Sheet + hamburger `md:hidden`, auto-close via `useEffect([pathname])`, BottomNav `fixed bottom-0 md:hidden` com filtragem por permissões
- Dual-view lists: mobile cards (`md:hidden`) + desktop tables (`hidden md:block`) em Clientes, Agenda, Documentos e Financeiro — CSS puro, sem rerenders
- Horizontal scroll em tabelas complexas: `overflow-x-auto` + `min-w-[400/480px]` em Partes e Fases do processo
- Single-column forms: 24x `grid-cols-1 md:grid-cols-2` em 12 ficheiros; bottom-sheet dialogs `max-sm:fixed max-sm:bottom-0`; touch targets 48px (`max-sm:h-12` + `h-12 w-12` em mobile card buttons)
- Adaptive dashboard KPI grid `grid-cols-1 sm:grid-cols-2 lg:grid-cols-4` + bloco "Hoje" `md:hidden` com eventos do dia na Agenda

---

## v1.9 v1.9 (Shipped: 2026-06-17)

**Phases completed:** 3 phases, 3 plans, 10 tasks

**Key accomplishments:**

- Refatoração completa do data layer e das páginas do módulo de agenda do LexCV de snake_case para camelCase, com tratamento de fuso horário em campos de data, alinhando com a serialização padrão Jackson/Spring Boot do backend.
- Validação robusta de intervalos de datas no frontend e no backend, com tratamento de exceções de parsing de data no Spring Boot e exibição de toasts de erro detalhados no Next.js.
- Exposição global de prazos no backend, unificação de eventos e prazos no calendário mensal da agenda com filtros avançados e spinners de carregamento dinâmico.

---

## v1.8 Deployment para VPS (Shipped: 2026-06-16)

**Phases completed:** 3 phases (37-39), 4 plans, ~9 tasks

**Key accomplishments:**

- Dockerfiles multi-stage para Spring Boot (Maven → JRE Alpine) e Next.js (pnpm → standalone runner), ambos com non-root appuser e zero secrets em camadas de imagem
- Docker Compose com 4 serviços (postgres, backend, frontend, caddy), volumes nomeados persistentes (lexcv_pgdata, lexcv_uploads) e override de produção com restart policies e resource limits
- Caddy como reverse proxy com HTTPS automático via Let's Encrypt — Caddyfile.prod parametrizado por DOMAIN_NAME
- GitHub Actions CI/CD: build + push para GHCR com cache Maven/pnpm, deploy via SSH com appleboy/ssh-action na VPS Hostinger
- DEPLOYMENT.md runbook VPS completo com firewall, env setup, docker compose, e secrets GitHub Actions documentados

**Known deferred items at close:** 3 (runtime verification requires live VPS — see STATE.md)

---

## v1.7 Melhoria no modulo de gestao e acompanhamento de processos (Shipped: 2026-06-16)

**Phases completed:** 5 phases, 11 plans, 14 tasks

**Key accomplishments:**

- One-liner:
- One-liner:
- One-liner:
- One-liner:
- TypeScript types, Zod schemas, lib/prazos.ts helper, and five TanStack Query hooks wiring the Plan 01 workflow/prazos endpoints to the frontend with correct cache invalidation.
- Task 1 — shadcn Dialog + Textarea install
- AuditLog JPA entity, AuditLogRepository, TimelineItemDto record, Movimentacao.autorId, GET /timeline (processos:view), GET /audit (processos:manage), and audit injection at 4 sensitive write points in ResourceController
- TypeScript types (TimelineItem, TimelineItemType, AuditLogEntry) added to processos.ts; useTimeline and useAuditLog TanStack Query hooks added to use-processos.ts with timeline cache invalidation wired into both mutation hooks
- Tab system restructured in page.tsx: Timeline is default tab with dot-and-line feed and filter bar; Auditoria tab added with RBAC gate; Movimentacoes tab removed

---

## v1.0 MVP (Shipped: 2026-05-26)

**Phases completed:** 6 phases, 14 plans, 0 tasks

**Key accomplishments:**

- MVP web completo (Dashboard, Clientes, Processos, Agenda, Documentos, Financeiro) com mock API `/api/v1`
- Autenticação mock (login/refresh/me) e seed multi-tenant
- Navegação institucional com RBAC básico no UI

---

## v1.1 UI/UX Alignment (Shipped: 2026-05-27)

**Phases completed:** 4 phases, 6 plans, 0 tasks

**Key accomplishments:**

- Novo DashboardShell (sidebar escura + top app bar) alinhado ao Figma
- Componentes UI base (badge/table/pagination) e aplicação nas páginas principais
- Dark/Light mode e ajuste visual “Anti-Safe Harbor” (sharp edges, high contrast)

---

## v1.2 Utilizador (Shipped: 2026-05-27)

**Phases completed:** 1 phases, 1 plans, 0 tasks

**Key accomplishments:**

- Painel de utilizador (perfil) com edição de dados no UI
- Migração para backend real (Spring Boot + PostgreSQL) e integração via rewrites
- Seed real com fixtures coerentes e recálculo de conta corrente
