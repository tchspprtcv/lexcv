# Phase 86: Infraestrutura de Notificações — Entidade, API e Targeting - Context

**Gathered:** 2026-07-08
**Status:** Ready for planning
**Mode:** Auto-generated (infrastructure phase — smart discuss skipped, no UI/user-facing behavior in success criteria; the bell/page UI that consumes this API is Phase 89)

<domain>
## Phase Boundary

Existe uma API de notificações persistidas, funcional e segura — cada notificação é dirigida apenas à entidade diretamente ligada mais ADMIN, nunca em massa por permissão de visualização, com estado lido/não-lido isolado por destinatário. Esta fase entrega a entidade backend, o serviço de resolução de destinatários, a API REST, e o RBAC — nenhuma UI consome isto ainda (Phase 89). Nenhum gatilho real de negócio (fase/documento/atribuição/parecer/prazo) cria notificações ainda (Phases 87/88) — esta fase só prova que a infraestrutura funciona com dados semeados manualmente/via testes.

</domain>

<decisions>
## Implementation Decisions

### Claude's Discretion
Todas as escolhas de implementação ficam ao critério do Claude — fase de infraestrutura pura. Usar o objetivo da fase, os critérios de sucesso do ROADMAP, e `.planning/research/ARCHITECTURE.md` (secção sobre `Notificacao`/`NotificacaoService`/`NotificacaoController`) para orientar decisões. Pontos já resolvidos pela pesquisa de arquitetura da milestone, a respeitar:

- **Entidade `Notificacao` (`t_notificacao`)**: uma linha por `(evento, destinatário)` — nunca uma linha partilhada com visibilidade calculada em tempo de leitura. `tenant_id` como coluna própria de primeira classe (não derivada transitivamente), espelhando a convenção de todas as outras entidades do projeto. Referência polimórfica à entidade de origem via par `entidade_tipo` (String) + `entidade_id` (String) — mirror direto do padrão já usado por `AuditLog`, o único precedente existente no código para referenciar entidades com tipos de chave primária mistos (UUID em `Processo`/`Cliente`, Integer em `Prazo`/`ParecerSolicitacao`). Texto de exibição (`titulo`/`mensagem`/`linkUrl`) desnormalizado no momento da escrita — mantém a leitura por polling uma única query plana sem joins.
- **Fan-out de ADMIN**: uma notificação dirigida a "ADMIN" gera uma linha própria por cada ADMIN atual do tenant no momento da criação (não uma linha partilhada com uma flag "é admin"). Isto é o que os critérios de sucesso 2 e 3 exigem explicitamente — estado de leitura tem de ser independente por destinatário.
- **`NotificacaoService`**: novo serviço com um único ponto de escrita (`criar(...)`) — nenhum outro código deve chamar `notificacaoRepository.save(...)` diretamente. Nesta fase, o serviço só precisa de existir e funcionar corretamente contra chamadas de teste/seed manual — os gatilhos de negócio reais (fase/documento/atribuição/parecer em Phase 87; prazos/honorários em Phase 88) ainda não existem.
- **`NotificacaoController`**: novo controller dedicado (não adicionado ao `ResourceController` já com ~2900 linhas) — segue o precedente já estabelecido pela extração de `ParecerPesquisaController`. Endpoints: `GET /notificacoes` (filtros categoria/lida + paginação), `GET /notificacoes/unread-count`, `PATCH /notificacoes/{id}/lida`, `POST /notificacoes/ler-todas`.
- **Autorização de leitura/escrita**: cada query/mutação tem de filtrar por `tenant_id` E `destinatario_id` — nunca só tenant. Este é o primeiro recurso "privado por utilizador" do projeto (todos os outros são partilhados por tenant, visíveis a qualquer utilizador com o scope de permissão certo); não copiar o padrão de autorização de nenhum outro endpoint sem adicionar esta segunda dimensão.
- **Migração manual**: `backend/migrations/86-<slug>.sql`, seguindo o precedente já estabelecido (`81-*.sql`, `82-*.sql`) — projeto não tem Flyway/Liquibase, `ddl-auto=validate` em produção não cria a tabela sozinho.
- **RBAC**: novo scope `notificacoes:view` seedado para os 4 perfis (ADMIN/ADVOGADO/TECNICO/ASSISTENTE) — todos veem as SUAS PRÓPRIAS notificações (o filtro por destinatário já garante isolamento, não é preciso um scope diferenciado por perfil como nos outros módulos). Adicionar a `DatabaseSeeder.seedRbac()` e a `web/src/lib/permissions.ts`'s `KNOWN_SCOPES`.
- **Testes**: seguir o precedente da Phase 85 — este é o segundo ficheiro de teste do backend. Dado que esta fase introduz autorização dupla (tenant+destinatário) e fan-out, testes automatizados que provem isolamento entre 2 utilizadores de teste (critério de sucesso 2) são altamente valiosos aqui, não opcionais.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `AuditLog.java` — único precedente de referência polimórfica a entidades (`entidade_tipo`/`entidade_id` como Strings), reutilizado diretamente para `Notificacao`.
- `ParecerPesquisaController` — precedente de extração de um controller dedicado fora do `ResourceController` monolítico.
- `RiscoPrazoService.java` (Phase 85, já mergeada) — precedente imediato de `@Service` injetável seguindo `StorageService`/`SetupService`.
- `backend/migrations/81-*.sql`, `82-*.sql` — padrão de script de migração manual a seguir para `t_notificacao`.

### Established Patterns
- Toda a autorização hoje é `@PreAuthorize("hasAuthority('scope:action')")` a nível de método + verificação de `tenant_id` dentro do método. Esta fase precisa do padrão adicional (tenant + destinatário) pela primeira vez.
- `DatabaseSeeder.seedRbac()` (linhas ~293-349) é onde novos scopes são registados e atribuídos a roles.
- `web/src/lib/permissions.ts` `KNOWN_SCOPES` array espelha os scopes do backend — comentário no ficheiro já diz "mirrored from backend DatabaseSeeder.seedRbac()".

### Integration Points
- Nenhum gatilho de negócio ainda liga a esta infraestrutura (isso é Phase 87/88) — esta fase é independentemente testável com notificações criadas manualmente/via teste.
- Consumido por Phase 89 (sino + página `/notificacoes`, via os endpoints REST desta fase) e por Phase 87/88 (via `NotificacaoService.criar(...)`).

</code_context>

<specifics>
## Specific Ideas

Nenhuma — fase de infraestrutura pura, sem requisitos de UX. `.planning/research/ARCHITECTURE.md` já especifica o desenho exato (entidade, serviço, controller, fan-out, migração) — seguir essa recomendação diretamente.

</specifics>

<deferred>
## Deferred Ideas

- Gatilhos reais de negócio (entrada de fase, documento novo, atribuição, parecer) — Phase 87.
- Job diário de prazos/honorários — Phase 88.
- Sino, página `/notificacoes`, filtros na UI — Phase 89.

</deferred>
