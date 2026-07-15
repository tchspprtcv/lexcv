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
- ✓ "Documentos a Tratar" e "Deslocações" relocalizados dos seus próprios separadores (antes viviam dentro do tab "Dados"), mantendo comportamento atual (gated por `isEditing`, sem campo novo) — v2.8 (Phase 78)
- ✓ "Documentos Entregues" passa a upload real via novo endpoint `GET /clientes/{id}/documentos` (tenant-scoped, espelha `/processos/{id}/documentos`), reutilizando sistema genérico `Documento`/`useUploadDocumentoComProgresso`, combobox de tipo (datalist nativo), RBAC `documentos:view/edit`; secção antiga de texto removida por completo, coluna órfã sem migração — v2.8 (Phase 79, verificação visual/UAT ao vivo pendente — ver 79-HUMAN-UAT.md)
- ✓ Campo Juízo (texto livre) e Origem (`Petição Inicial | Notificações Avulsas`, obrigatório no intake, imutável após formalização) nos Dados do Processo — v2.9 (Phase 80/81/84)
- ✓ Sub-secções Decisões (data, tipo enum `Despacho|Decisão Interlocutória|Sentença|Acórdão`, resumo, anexo via upload multipart num só passo), Factos (descrição, data, `ordem` reordenável) e Testemunhas (nome, contacto, tipo `Autor|Réu`, notas) — entidades novas próprias, distintas de Partes, com CRUD completo e verificação dupla de posse (tenant + processoId) — v2.9 (Phase 80/81/84)
- ✓ Aba "Documentos" dedicada na ficha do processo (upload/listagem/download/remoção via `GET /processos/{id}/documentos`) — v2.9 (Phase 84)
- ✓ Criação automática e idempotente de Honorário ao formalizar processo (TRIAGEM→ATIVO), `valorTotal` sempre `null` (nunca pré-preenchido) — v2.9 (Phase 82)
- ✓ Termo de Honorários imprimível (`[id]/termo-honorarios`, padrão CSS-print de `clientes/[id]/ficha`), combinando Cliente+Processo+Honorário, com bloqueio de impressão quando `valorTotal` ainda está em branco — v2.9 (Phase 84)
- ✓ Partes e Fases (existentes desde v1.0) refatoradas para o mesmo padrão lista+Dialog "Adicionar" das 4 abas novas, por consistência visual — v2.9 (Phase 84, extensão de âmbito pedida explicitamente pelo utilizador)
- ✓ Lógica de "prazo crítico" consolidada numa única fonte partilhada (`RiscoPrazoService`), substituindo as 4 implementações inconsistentes anteriores — v2.10 (Phase 85)
- ✓ Sistema de notificações persistido (entidade `Notificacao` backend, API de listagem/marcar-lida/marcar-todas, targeting por entidade diretamente ligada + ADMIN) — v2.10 (Phase 86)
- ✓ Alerta de entrada de nova fase no processo — v2.10 (Phase 87)
- ✓ Alerta de novo documento em processo/cliente — v2.10 (Phase 87)
- ✓ Alerta de processo atribuído + fluxo de reatribuição de responsável (`PUT /processos/{id}/atribuir`, controlo `ReatribuirResponsavelControl`) — v2.10 (Phase 87)
- ✓ Alerta de parecer atribuído — v2.10 (Phase 87)
- ✓ Alerta de prazos de processos e calendário crítico + prazos de honorários, via job diário `@Scheduled` (`AlertasDiariosJob`, cron 06:00 `Atlantic/Cape_Verde`, idempotência edge-triggered por `categoria`) — v2.10 (Phase 88)
- ✓ Sino reescrito para consumir `Notificacao` persistida (contador com polling 30s + refocus, dropdown de 10 com clique fundido marcar+navegar, marcar-todas) e página dedicada `/notificacoes` (filtros categoria + lida/não-lida, paginação real, acesso só via link do sino) — v2.10 (Phase 89)
- ✓ SpotBugs/FindSecBugs a correr limpo contra bytecode JDK 23 (versões atualizadas, exclusões e correções defensivas comprometidas) — v2.11 (Phase 90)
- ✓ Infraestrutura de testes de integração Testcontainers+PostgreSQL (primeira vez neste backend), cobrindo a query nativa de `Notificacao` e o lock de concorrência de `numeroVersao`, com gate de CI (`deploy.yml` job `test`) | v2.11 (Phase 91)
- ✓ Agenda consolidada com `RiscoPrazoService` para Prazos e Eventos — fim da 5ª implementação divergente de "prazo crítico" (endpoint `/eventos/upcoming` órfão removido) — v2.11 (Phase 92)
- ✓ Preferências de notificação por utilizador (silenciar categorias, `PRAZO_VENCIDO` sempre entregue, job diário respeita o silenciamento) — v2.11 (Phase 93)
- ✓ Correção de bug pré-existente: destinatário simultaneamente membro de equipa e ADMIN já não colide na constraint de dedup — v2.11 (Phase 94)
- ✓ Notificações de processo (fase/documento/atribuição) alcançam toda a equipa de advogados/administrativos do cliente, não só o responsável único — v2.11 (Phase 95)
- ✓ Snooze de lembrete de prazo (presets 1/3/7 dias, reaparece automaticamente, sem duplicação pelo job diário) — v2.11 (Phase 96)
- ✓ Auditoria final de milestone: isolamento de tenant confirmado nas 3 novas superfícies de notificação; UAT ao vivo fechado para 8 fases históricas (~40 cenários); labels `DocumentoTipo` traduzidas + testes de NIF adicionados; bloqueio ambiental `MINIO_ENDPOINT` genuinamente resolvido pela primeira vez neste projeto; 1 gap de integração residual (divergência `dataInicio`/`dataFim` em `isEventoCritico`) encontrado e corrigido no próprio fecho — v2.11 (Phase 97)
- ✓ Endpoint público não-autenticado `GET /api/v1/public/branding` (`{nome, logoDataUrl}` via DTO explícito, allowlist exact-literal) — v2.12 (Phase 98)
- ✓ Nova app Next.js 16 standalone `webpage/` servindo a landing page pública em `/` (Hero, Funcionalidades, Confiança Institucional, Contacto, dark/light mode, gate de setup sem acoplamento de autenticação, fetch de branding server-to-server) — v2.12 (Phase 99)
- ✓ Routing e deployment de `webpage/` em dev/prod/Hostinger via Next.js Multi-Zones (`assetPrefix`), 3 fontes de configuração Caddy consistentes, novo artefacto `webpage` no pipeline CI/CD, verificado com `docker compose up` completo — v2.12 (Phase 100)

### Active

(Nenhum requisito ativo — a definir na próxima milestone via `/gsd-new-milestone`)

### Out of Scope

- Integração real com Keycloak — adiar até existir backend de autenticação institucional
- Regras de negócio avançadas (cálculo de honorários, prazos jurídicos, workflows) — responsabilidade do backend
- Contabilidade completa/ERP — fora do MVP
- Mobile app nativo — Web/PWA primeiro; desktop via Tauri numa fase posterior
- Notificações push / email externas — mantém-se in-app apenas (decisão confirmada na v2.10, reconfirmada na v2.11)
- Recorrência infinita (sem data de fim) — requer paginação especial, adiado
- Editar todas as instâncias futuras de uma série — apenas "esta instância" ou "toda a série"
- Novo campo de data de vencimento em `Honorario` — alerta de prazo de honorário usa dias sem pagamento total desde `dataAcordo` em vez de uma data explícita
- Expansão do fan-out de equipa (NOTF-25) às categorias do job diário (`PRAZO_*`, `EVENTO_*`, `HONORARIO_ATRASADO`) — deliberadamente fora de âmbito da v2.11, `AlertasDiariosJob` mantém-se `responsavelId`-only; candidato de âmbito futuro
- Matriz de preferências categoria×canal (NOTF-24) — prematuro com um único canal de entrega (in-app); só revisitar se email/push for adicionado
- Onboarding self-service multi-instituição, slug/subdomínio por tenant — decisão confirmada na v2.12; este deployment continua a servir uma única instituição, o wizard `/setup` mantém-se singleton

## Context

- Referência funcional e técnica do frontend: `.trae/documents/SPEC.md`
- Contrato e convenções REST para o mock: `.trae/documents/API-Design.md` (base `/api/v1`)
- Modelo relacional (fonte de verdade para entidades): `.trae/documents/ERD.sql`
- Backend alvo: Spring Boot (frontend deve permanecer “passivo”, apenas apresentar dados e executar ações)
- Estado pós-v2.11 (2026-07-14): sistema de notificações agora cobre preferências por utilizador (mute por categoria, Phase 93), alcance de equipa (fase/documento/atribuição chegam a toda a equipa do cliente, Phase 95) e snooze de lembretes de prazo (Phase 96), sobre a fundação persistida da v2.10. `web/src/app/(dashboard)/agenda/page.tsx` já não calcula o seu próprio veredito de "prazo crítico" — consome o campo `risco` do backend para Prazos e Eventos (Phase 92), fechando a última implementação divergente (incluindo uma última divergência residual, `isEventoCritico`'s uso de `dataFim` em vez de `dataInicio`, encontrada e corrigida no fecho da própria milestone). Backend passou a ter, pela primeira vez, testes de integração reais contra PostgreSQL via Testcontainers (Phase 91), com gate de CI. SpotBugs/FindSecBugs corre limpo contra JDK 23 (Phase 90). Continua sem WebSocket/SSE em todo o projeto — notificações usam polling (30s) via TanStack Query.
- ~~Bloqueio ambiental recorrente conhecido: `MINIO_ENDPOINT` não é substituído a partir de `backend/.env` nesta sessão/ambiente, impedindo o arranque completo do contexto Spring~~ — **RESOLVIDO (2026-07-14, v2.11 Phase 97 AUD-04):** `backend/.env` local passou a fornecer `MINIO_ENDPOINT`/credenciais reais contra um container `lexcv_minio` a correr, o contexto Spring arranca por completo (`MinioConfig.s3Client()` já não lança a exceção `IllegalArgumentException` de carácter ilegal), e `backend/.env.example` já documenta todas as variáveis `MINIO_*` necessárias para qualquer ambiente futuro. Resolução de ambiente/configuração, não uma correção de código — nunca foi um bug de código; ver `.planning/milestones/v2.10-MILESTONE-AUDIT.md` para o histórico do bloqueio.
- ~~Limitação de infraestrutura pré-existente, projeto inteiro: nenhum H2/Testcontainers existe neste backend~~ — **RESOLVIDO (v2.11 Phase 91, TEST-01/02/03):** Testcontainers PostgreSQL real + gate de CI (`deploy.yml` job `test`) agora cobrem a native query `buscarPorFiltros` (`NotificacaoRepositoryIT`) e o lock de concorrência `numeroVersao` (`ParecerVersaoConcorrenciaIT`). Execução local nesta sessão ficou bloqueada por uma incompatibilidade Docker Desktop 4.80/Testcontainers 1.20.4 (npipe) — confirmada independentemente, não um defeito de código; deverá passar em CI (`ubuntu-latest`).

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
| Ficha de cliente reestruturada em 7 separadores estilo botões-toggle (não `Tabs` do shadcn), replicando o padrão já usado em processos | Consistência visual pedida explicitamente pelo utilizador; evita introduzir um componente novo (shadcn `Tabs` nunca foi inicializado no projeto) | ✓ Good |
| `useProcessos`/`usePareceres`/`useDocumentos` não ganharam um parâmetro `enabled` externo para fetch lazy por separador — em vez disso, cada separador usa um sub-componente que só monta quando ativo (`ClienteProcessosTab`, `ClienteParecerTab`, `ClienteDocumentosEntreguesTab`) | Nenhum destes hooks tinha essa opção; adicionar `enabled` a três hooks core arriscava efeitos colaterais nos restantes call sites. Sub-componente lazy-mount é mais isolado e replica o padrão já usado para os cards de Contactos/Notas | ✓ Good |
| Novo endpoint `GET /clientes/{id}/documentos` em vez de corrigir o `GET /documentos` genérico (que ignora `cliente_id`/`processo_id` mesmo hoje sendo construídos pelo hook frontend) | Corrigir o endpoint genérico alargaria o raio de impacto para a página standalone `/documentos` e para o uso do endpoint por processos, fora do âmbito da milestone. O gap no `GET /documentos` fica registado como dívida técnica pré-existente, não introduzida por esta fase | ✓ Good (dívida técnica pré-existente, fora do âmbito) |
| `POST /documentos/upload` passou a validar que `clienteId`/`processoId` (quando fornecidos) pertencem ao tenant do chamador antes de persistir | Code review da Phase 79 encontrou que o endpoint aceitava estes IDs sem verificação de posse — não era uma fuga de leitura (o lado de leitura já revalida o tenant independentemente), mas era uma lacuna real de integridade referencial/autorização. Corrigido com o mesmo padrão já usado no `responsavelId` de `createProcesso` | ✓ Good |
| `Decisao`/`Facto`/`Testemunha` mirram o shape magro de `Parte.java` (Integer identity id, `processo_id` FK, sem coluna `tenant_id` própria) | Isolamento de tenant transitivo via o `Processo` pai, verificado no controller — mesmo padrão já usado por `ProcessoFase`/`Movimentacao`; evita duplicar a coluna em três tabelas novas | ✓ Good |
| Endpoints de escrita em Decisão/Facto/Testemunha replicam o padrão de dupla verificação de `ProcessoFase` (tenant do processo pai + `processoId` da entidade filha), não o padrão mais simples de `Parte` | Pesquisa de milestone identificou risco de IDOR se o padrão mais simples fosse copiado, dado que os IDs destas entidades são inteiros sequenciais adivinháveis | ✓ Good |
| `Facto.ordem` calculado no servidor (`max(ordem)+1`, `synchronized`) na criação; aceite explicitamente do cliente apenas na atualização (reordenação) | Decisão de discussão de fase — evita depender de uma restrição de unicidade a nível de BD não adicionada inicialmente; a reordenação continua possível via edição | ✓ Good |
| `Honorario` auto-criado na formalização tem `valorTotal` sempre `null`, nunca copiado de `Cliente.honorariosPropostos` (uma estimativa por cliente, não por processo) | Risco de maior severidade identificado pela pesquisa de milestone — popular automaticamente um valor financeiro real sem confirmação explícita do utilizador seria um erro de segurança financeira grave | ✓ Good |
| `@UniqueConstraint(processo_id)` em `Honorario` e `@UniqueConstraint(processo_id, ordem)` em `Facto`, cada uma com script de migração manual em `backend/migrations/` | Code review encontrou que a verificação de idempotência "check-then-act" a nível de aplicação (sem `synchronized`/constraint) não protege contra uma race condition genuína entre pedidos concorrentes; o projeto não tem Flyway/Liquibase, pelo que a migração fica documentada como passo manual de deploy (mesmo padrão do script `74-cleanup-nif-documento-tipo.sql`) | ✓ Good |
| Ficha do processo — Partes e Fases (existentes) refatoradas para o mesmo padrão lista+Dialog "Adicionar" das 4 abas novas | Pedido explícito do utilizador durante a discussão da Phase 84, para consistência visual em toda a ficha; extensão de âmbito deliberada além dos critérios de sucesso originais do ROADMAP | ✓ Good |
| Auditoria de milestone encontrou 2 bugs de integração cross-phase — `GET /honorarios?processo_id=X` e `GET /documentos?processo_id=X` ignoravam o filtro no backend, devolvendo dados de todo o tenant em vez de apenas do processo | Cada lado "parecia correto" isoladamente (o hook envia o parâmetro, o endpoint aceita GET) — só visível ao verificar o contrato completo entre fases, exatamente a classe de problema que a auditoria de milestone existe para apanhar. Corrigido na mesma sessão: `listHonorarios` ganhou filtro opcional por `processo_id`; a aba Documentos passou a usar o endpoint `GET /processos/{id}/documentos` já existente (espelhando o padrão já usado para `cliente_id`) | ✓ Good (corrigido na mesma sessão, commits 2ce48f7/380d435) |
| (v2.10) Polling simples (30-60s) via TanStack Query em vez de WebSocket/SSE/tempo real | Reaproveita o padrão já usado em toda a app; zero infraestrutura de push nova — nenhuma existia no projeto | ✓ Good |
| (v2.10) Notificações persistidas numa entidade backend própria, com estado lido/não-lido, em vez de efémeras/computadas a pedido | Permite histórico consultável e página dedicada `/notificacoes`; o `NotificationBell` v2.1 não tinha nenhum estado persistido | ✓ Good |
| (v2.10) Alvo de cada notificação = só a entidade diretamente ligada (responsável do processo, advogado do parecer, equipa do cliente via `ClienteAdvogado`/`ClienteAdministrativo`) + ADMIN — nunca notificação em massa por permissão de visualização | Evita ruído (ex.: todo TECNICO com `documentos:view` a ser notificado de cada upload no tenant inteiro) | ✓ Good |
| (v2.10) Lógica de "prazo crítico" consolidada numa única fonte partilhada, substituindo as 4 implementações inconsistentes existentes (dashboard, sino v2.1, página de agenda, endpoint `/prazos`) | Reduz o risco de dashboard/agenda/notificações discordarem sobre o que conta como "crítico" | ✓ Good |
| (v2.10) Reatribuição de responsável de processo passa a ter um fluxo próprio (novo endpoint); hoje `responsavelId` só é definível na criação | "Processo atribuído" só faz sentido como evento repetível se a atribuição puder mudar depois da criação | ✓ Good |
| (v2.10) Alerta de honorário usa dias sem pagamento total desde `dataAcordo`, sem novo campo de data de vencimento | `Honorario` não tem hoje nenhum campo de vencimento; adicionar um exigiria desenhar UI de preenchimento fora do pedido original | ✓ Good |
| (v2.10, Phase 87) `ParecerController.updateSolicitacao` corrigido para nunca sobrescrever `prazo`/`prioridade` com `null` num update parcial | Revisão de código da Phase 87 encontrou este bug de perda de dados pré-existente (não introduzido por esta fase) ao ler o ficheiro por inteiro para verificar os gatilhos de notificação — corrigido na mesma sessão | ✓ Good (commit `ce6d1f0`) |
| (v2.10, Phase 87) Notificações por-destinatário isoladas com try/catch individual (nunca reverte a ação de negócio já persistida nem bloqueia o fan-out de ADMIN) | 3 rondas de revisão de código encontraram sucessivamente que um destinatário inválido/apagado podia (a) causar um 500 numa operação já bem-sucedida, depois (b) suprimir silenciosamente o fan-out de ADMIN garantido — cada gatilho de notificação isola agora a falha ao nível do destinatário individual | ✓ Good |
| (v2.10, Phase 87) Novo endpoint `GET /users` (gated `processos:view`, devolve apenas `{id, nome}`) substitui `GET /admin/users` nos seletores de atribuição da ficha do processo | O picker de reatribuição usava um endpoint ADMIN-only, deixando ADVOGADO (que tem `processos:manage`) com uma lista vazia e o submit permanentemente desativado — mesmo padrão de bug já existente no picker de "Novo Prazo", corrigido para ambos na mesma revisão | ✓ Good (commit `8122c7d`) |
| (v2.10, Phase 88) `AlertasDiariosJob` nunca chama `getTenantId()`/`SecurityContextHolder` — itera `tenantRepository.findAll()` explicitamente, com `tenantId` passado como parâmetro em toda a cadeia de chamadas | Primeira thread de background deste projeto; reutilizar o padrão de resolução de tenant baseado em sessão causaria `NullPointerException` imediato | ✓ Good |
| (v2.10, Phase 88) Isolamento de falha em 4 camadas (job completo / por tenant / por categoria de alerta / por entidade individual), todas a capturar `Throwable` (não só `Exception`) | 3 rondas de revisão de código foram sucessivamente apertando esta garantia — uma exceção não tratada nas camadas internas não pode impedir a verificação dos restantes tenants/categorias/entidades, nem cancelar silenciosamente execuções futuras do job (comportamento documentado do Spring `@Scheduled`) | ✓ Good |
| (v2.10, Phase 88) Idempotência por `categoria` distinta por nível de risco (`PRAZO_PROXIMO`/`PRAZO_VENCIDO`/etc.), reforçada por unique constraint na BD (`uk_notificacao_dedup`) | `Notificacao` não tinha campo de "nível de risco" dedicado; usar `categoria` evita migração de schema maior e dá à Phase 89 categorias de filtro naturais. Constraint DB adicionada pela revisão de código como reforço de defesa em profundidade, além da verificação de existência já feita pela aplicação | ✓ Good |
| (v2.10, Phase 89) `useNotificacoes(filters, { poll })` — um único hook de lista serve o dropdown do sino (polling) e a página `/notificacoes` (sem polling), em vez de dois hooks quase-duplicados | UI-SPEC.md fixou este contrato antes de qualquer superfície consumidora ser construída, evitando divergência entre as duas implementações | ✓ Good |
| (v2.10, Phase 89) Invalidação de prefixo top-level `["notificacoes"]` (não `["notificacoes","list"]`) no `onSuccess` de ambas as mutações de marcar-lida | Um único `invalidateQueries` apanha o contador do sino E as duas queries de lista (dropdown+página) simultaneamente — satisfaz "atualização imediata cross-surface" sem escrita otimista manual | ✓ Good |
| (v2.10, Phase 89) `isInternalLinkUrl` reescrito de verificação por posição de carácter para verificação baseada em parser (`new URL(url, sentinel).origin === sentinel`) | 3 rondas de revisão de código encontraram sucessivamente novos desvios de bypass (protocol-relative, backslash, caracteres de controlo TAB/LF/CR) numa abordagem por enumeração de caracteres — a abordagem baseada em parser fecha a classe inteira de bypass em vez de mais um caso específico | ✓ Good |
| (v2.10, Phase 89) Sino v2.1 (`useUpcomingEventos`) substituído por completo — Agenda mantém a sua própria página, mas o sino deixa de ser sobre eventos e passa a ser sobre `Notificacao` genérica | Decisão travada em CONTEXT.md; o sino tornar-se-ia inconsistente com a página `/notificacoes` se continuasse a mostrar uma fonte de dados diferente | ✓ Good |
| (v2.10, Phase 89) Sem novo item de navegação na sidebar/bottom-nav para `/notificacoes` — acesso exclusivamente via link "Ver todas as notificações" no dropdown do sino | Decisão travada em CONTEXT.md; evita adicionar navegação permanente para uma página secundária | ✓ Good |
| (v2.11, Phase 91, TEST-03) CI (`deploy.yml`) passa a correr um job `test` (`mvn verify` — surefire unit + failsafe Testcontainers `*IT` — e `mvn spotbugs:check`) que bloqueia `build-and-push` via `needs: test` | `ubuntu-latest` já traz Docker para o Testcontainers arrancar sem configuração extra; o CI deste repositório nunca correu testes nem SpotBugs, pelo que os novos testes de integração (91-01/91-02) e a configuração SpotBugs da Phase 90 apodreceriam silenciosamente sem este gate (PITFALLS.md Pitfall 7). `dependency-check:check` (OWASP) deliberadamente NÃO foi ligado ao CI nesta milestone — descarrega o dataset NVD completo (lento, exige chave de API/estratégia de cache) e está fora do âmbito específico de TEST-03 | ✓ Good |
| NOTF-25: expansão de fan-out de equipa cobre apenas os 3 gatilhos de evento (FASE_ENTRADA, DOCUMENTO_NOVO, PROCESSO_ATRIBUIDO); `AlertasDiariosJob` (PRAZO_*, EVENTO_*, HONORARIO_ATRASADO) mantém-se responsavelId-only | Deliberadamente fora de âmbito para v2.11 per CONTEXT.md e REQUIREMENTS.md Out of Scope; registado como candidato de âmbito futuro, não como requisito confirmado | Deferred (job team-expansion) |
| NOTF-25: PARECER_ATRIBUIDO mantém-se individual (só o advogado atribuído + fan-out ADMIN), NÃO alarga à equipa do cliente | Atribuição de parecer é semanticamente a decisão de um advogado específico assumir o trabalho; alargar diluiria o sinal | By design |
| (v2.11, Phase 97, AUD-04) Bloqueio ambiental recorrente `MINIO_ENDPOINT` (presente em v2.8/v2.9/v2.10) fechado como resolução de ambiente/configuração, não como correção de código | `backend/.env` local passou a apontar para um container `lexcv_minio` real com credenciais válidas; `backend/.env.example` já documentava as variáveis `MINIO_*` necessárias — o bloqueio persistia apenas por o ambiente local não ter o container/credenciais provisionados nas sessões anteriores | ✓ Good (fechado 2026-07-14; ver STATE.md Blockers/Concerns) |
| (v2.11, Phase 97, AUD-01/02/03/05) Auditoria final da milestone consolidada: isolamento de tenant confirmado (verdito COVERED, zero fixes) nas 3 novas superfícies de notificação (preferências, equipa, snooze); UAT ao vivo consolidado em `97-UAT.md` (~38 cenários das 8 fases 75/76/79/81/82/84/85/89, todos com veredito explícito — 2 itens transportados em aberto: migração vs. `ddl-auto=validate` nas Phases 81/82, decisão de produto da Phase 85); dívidas menores (labels `DocumentoTipo`, testes de NIF ausentes) fechadas; nova auditoria de código (fresca, não repetição da lista já conhecida) sobre as superfícies de notificação/preferências/equipa/snooze não encontrou gaps novos | Consolida os resultados das plans 97-01/02/03/04 num único registo, incluindo a correção de duas linhas de Deferred Items em STATE.md que atribuíam indevidamente itens das Phases 86/87 ao âmbito de AUD-02 (nenhuma das duas fases está entre as 8 cobertas por AUD-02) | ✓ Good (ver STATE.md Pending Todos/Deferred Items para o detalhe completo por fase) |
| (v2.12, Phase 99) Fetch de branding server-side (Server Component), nunca client-side | Evita CORS por completo (fetch nunca corre no browser) — resolve definitivamente a preocupação de CORS que o endpoint público da Phase 98 teria exigido configurar | ✓ Good |
| (v2.12, Phase 100) Next.js Multi-Zones (`assetPrefix` próprio só na app não-default) para coexistência `webpage/`+`web/` sob o mesmo domínio Caddy | Padrão documentado oficialmente pelo Next.js para este caso exato; `web/` não precisa de nenhuma alteração | ✓ Good |
| (v2.12, Phase 100) As 3 fontes de configuração Caddy (`Caddyfile`, `Caddyfile.prod`, heredoc em `docker-compose.hostinger.yml`) tratadas como independentes, nunca DRY — o heredoc do Hostinger usa apenas literais hardcoded, zero caracteres `$` | Esta área já causou 4 commits de correção de bugs em produção na mesma sessão (Docker Compose interpola `$VAR` em todo o YAML, incluindo dentro do heredoc, antes do Caddy o ver) | ✓ Good |
| (v2.12, Phase 100) `docker-compose.hostinger.yml`'s MinIO console route (`/minio-console*`) não foi replicada do `Caddyfile.prod` — deixada deliberadamente em aberto | Ambas as correções óbvias (montar `Caddyfile.prod` como ficheiro; adicionar o hash bcrypt literal no heredoc) já tinham sido tentadas e revertidas nesta mesma sessão; um hash bcrypt não pode satisfazer a regra "zero `$`" do heredoc por construção | Deferred (ver v2.12-MILESTONE-AUDIT.md) |
| (v2.12, Phase 100, orquestrador) `webpage/pnpm-workspace.yaml` com `minimumReleaseAgeExclude` escopado a um único pacote (`electron-to-chromium`), não um `minimumReleaseAge: 0` global | Autorização explícita do utilizador foi para uma exceção estreita; pnpm 11+ moveu esta config de `.npmrc` (agora só auth/registry) para `pnpm-workspace.yaml` | ✓ Good |

## Current State

**Shipped:** v2.12 (2026-07-15) — Landing Page. Primeira rota pública real da aplicação: novo endpoint público não-autenticado `GET /api/v1/public/branding` (`{nome, logoDataUrl}` via DTO explícito, allowlist exact-literal no `SecurityConfig`, Phase 98); nova app Next.js 16 standalone `webpage/` servindo a landing completa em `/` — Hero, Funcionalidades/Módulos, Confiança Institucional, Contacto, dark/light mode, gate de setup próprio sem acoplamento de autenticação, fetch de branding server-to-server (Phase 99); routing e deployment completos via Next.js Multi-Zones (`assetPrefix`) em dev/prod/Hostinger, 3 fontes de configuração Caddy mantidas consistentes, novo artefacto `webpage` no CI/CD, verificado com `docker compose up` completo em vez de apenas checks isolados (Phase 100). 16/16 requisitos satisfeitos. Verificação ao vivo (não apenas estática) encontrou e corrigiu 3 bugs reais na própria sessão: um fetch com URL relativo que desativaria silenciosamente o gate de redirect `/setup` em todos os ambientes; uma anotação `@Transactional` em falta que crashava a leitura do logo da tenant via Hibernate/PostgreSQL; e um bug de deployment pré-existente (anterior a esta milestone) de falta de passthrough de variáveis de ambiente para o Caddy em produção. Status: `tech_debt` — sem bloqueios; a única dívida residual não fechada é o routing da consola MinIO no Hostinger, deliberadamente deferido com evidência concreta de que as duas correções óbvias já tinham sido tentadas e revertidas nesta mesma sessão. Ver `.planning/milestones/v2.12-MILESTONE-AUDIT.md`.

<details>
<summary>Histórico anterior (v1.0–v2.11)</summary>

**v2.11** (2026-07-14) — Auditoria Técnica e Notificações Avançadas. Fechou a dívida técnica acumulada e expandiu o sistema de notificações: SpotBugs/FindSecBugs corrigido contra JDK 23 (Phase 90); primeira infraestrutura de testes de integração real (Testcontainers+PostgreSQL) do projeto, com gate de CI (Phase 91); Agenda consolidada com `RiscoPrazoService` para Prazos e Eventos, fechando a 5ª implementação divergente de "prazo crítico" (Phase 92); preferências de notificação por utilizador (Phase 93); correção de um bug pré-existente de colisão de dedup ADMIN (Phase 94); notificações de processo alargadas a toda a equipa do cliente (Phase 95); snooze de lembrete de prazo (Phase 96); auditoria final de milestone — isolamento de tenant confirmado, UAT ao vivo fechado para 8 fases históricas (~40 cenários), labels `DocumentoTipo` traduzidas + testes de NIF, e o bloqueio ambiental `MINIO_ENDPOINT` genuinamente resolvido pela primeira vez neste projeto (Phase 97). 15/15 requisitos satisfeitos. Ver `.planning/milestones/v2.11-MILESTONE-AUDIT.md`.
**v2.10** (2026-07-10) — Notificações e Alertas. Sistema de notificações persistido construído do zero: `RiscoPrazoService` consolida 4 implementações inconsistentes de "prazo crítico" (Phase 85); entidade `Notificacao` + API completa com targeting estrito por entidade+ADMIN (Phase 86); 4 alertas disparados por evento — fase, documento, atribuição de processo (com novo fluxo de reatribuição), parecer (Phase 87); primeiro job `@Scheduled`/cross-tenant do projeto, verificação diária de prazos/eventos/honorários com isolamento de falha em 4 camadas e idempotência edge-triggered (Phase 88); sino reescrito + página `/notificacoes` dedicada, consumindo tudo o que as fases anteriores construíram (Phase 89). 16/16 requisitos satisfeitos, 0 gaps de integração cross-phase encontrados pela auditoria de milestone (apenas 2 avisos não-bloqueantes). Ver `.planning/milestones/v2.10-MILESTONE-AUDIT.md`.
**v2.9** (2026-07-08) — Melhoria Módulo Processos. Módulo de processos aprofundado com dados jurídicos estruturados: Juízo/Origem no card Dados, sub-secções Decisões/Factos/Testemunhas (entidades próprias com verificação dupla de posse), aba Documentos dedicada, criação automática e idempotente de Honorário na formalização (`valorTotal` sempre em branco), Termo de Honorários imprimível com bloqueio quando o valor ainda não foi preenchido, e Partes/Fases refatoradas para o mesmo padrão visual das abas novas. Auditoria de milestone encontrou e fechou na mesma sessão 2 bugs de integração cross-phase (filtros `processo_id` ignorados pelo backend em `/honorarios` e `/documentos`). 17/17 requisitos satisfeitos.
**v2.8** (2026-07-06) — Refatoração Ficha de Cliente. Ficha de cliente unificada e reestruturada em 7 separadores; enum `documento_tipo` restrito por tipo de cliente; "Documentos Entregues" passa a upload real.
**v2.7** (2026-07-02) — Melhoria Gestão de Clientes. Simplificação e aplanamento do modelo de identificação de clientes, NIF obrigatório validado em ambas as camadas.
**v2.6** (2026-07-01) — Módulo de Parecer Jurídico UI. Interface frontend completa sobre a API do v2.5.
**v2.5** (2026-06-30) — Módulo de Parecer Jurídico (backend-only). API completa para o ciclo Solicitação → Elaboração → Aprovação interna opcional → Entrega.
**v2.4** (2026-06-30) — Ficha de Cliente. Numeração sequencial automática (CLI-0001), formulário dinâmico Particular/Empresa, procuração obrigatória com aviso não-bloqueante, intake completo.
**v2.3** (2026-06-21) — Responsividade App. LexCV totalmente responsivo em mobile/tablet.
**v2.2** (2026-06-19) — Document Storage MinIO.
**v2.1** (2026-06-18) — Agenda Avançada.
**v2.0** (2026-06-18) — Módulo Financeiro.

Ver `.planning/MILESTONES.md` para histórico completo desde v1.0.

</details>

**Current focus:** v2.12 shipped e arquivada — a aguardar definição da próxima milestone via `/gsd-new-milestone`.

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
*Last updated: 2026-07-15 — milestone v2.12 (Landing Page) shipped*
