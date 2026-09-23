# Phase 125: Moldes da Plataforma e Provisionamento - Context

**Gathered:** 2026-09-20
**Status:** Ready for planning
**Mode:** Smart discuss (corrida autónoma — respostas recomendadas aceites, 4 áreas)

<domain>
## Phase Boundary

Nasce o mecanismo de "instanciar cópia de um molde": a plataforma passa a declarar quais papéis são moldes e a geri-los numa consola própria, e provisionar um escritório novo cria-lhe automaticamente uma cópia própria de cada molde.

Dentro do âmbito: as tabelas `t_tenant_role` e `t_tenant_role_permission`, a marca de instanciabilidade nos moldes, a instanciação dentro de `SetupService.provisionTenant`, os endpoints `/platform/moldes` e o ecrã `/plataforma/moldes`.

**Fora do âmbito, e esta fronteira é o que mantém a fase segura:** esta fase não altera **nenhuma** resolução de autoridade. `t_user_role` continua a apontar para papéis globais, o `JwtAuthenticationFilter` continua a resolver permissões como hoje, e o `PUT /admin/rbac` continua gated a `PLATAFORMA_ADMIN`. Os papéis instanciados nascem correctos e dormentes; quem os passa a usar é a Phase 126 (migração) e a Phase 127 (CRUD e leitura). Também fora: tenants que já existem (Phase 126) e auditoria (Phase 128).
</domain>

<decisions>
## Implementation Decisions

### Modelo de dados dos moldes e papéis instanciados
- Os moldes vivem em `t_role`, que ganha uma coluna de instanciabilidade. Os 4 papéis (ADMIN, ADVOGADO, TECNICO, ASSISTENTE) já lá estão com os seus conjuntos de permissões; uma tabela nova de moldes duplicaria essas linhas e exigiria backfill sem ganho. `PLATAFORMA_ADMIN` fica marcado como não instanciável.
- `t_tenant_role` é única por `(tenant_id, nome)`, consistente com as restantes constraints por tenant do projeto (ex. `(tenant_id, documento_numero)`). Unicidade global impediria dois escritórios de terem ambos um papel "ADVOGADO", o que é exactamente o ponto do marco.
- O papel instanciado guarda a proveniência num `molde_id` nullable. Serve para mostrar a origem no UI e para distinguir papel próprio de papel instanciado. Não cria dependência viva — é snapshot, e o `molde_id` é histórico, não referência de leitura.
- Coluna `sistema` boolean, true nos papéis instanciados de molde. A Phase 127 precisa dela para decidir o que um escritório pode apagar.

### Momento e transaccionalidade da instanciação
- A instanciação corre dentro de `SetupService.provisionTenant`, na mesma transacção que cria o Tenant e o utilizador ADMIN inicial. Se a instanciação falhar, o tenant não nasce meio-feito. Um job posterior deixaria uma janela com tenant sem papéis.
- **`provisionTenant` continua a atribuir o papel GLOBAL ADMIN ao administrador inicial.** Não troca para o papel instanciado nesta fase. Trocar aqui criaria dois caminhos de resolução de autoridade em simultâneo — tenants novos por papel de escritório, tenants antigos por papel global — precisamente a confusão que a Phase 126 existe para eliminar de uma vez.
- O tenant reservado "ALCv" (Phase 119) não recebe moldes instanciados. É tenant de plataforma, opera exclusivamente pelo `PLATAFORMA_ADMIN` e fora do sistema RBAC scoped por tenant; instanciar-lhe papéis contradiria a decisão bloqueada na Phase 119.
- Tenants que já existem antes desta fase ficam sem papéis instanciados até à Phase 126. Isso é deliberado e inofensivo enquanto nada lê `t_tenant_role` para autorizar.

### Consola de moldes
- Rota nova `/plataforma/moldes`, construída no molde de `/plataforma/relatorio` (que já existe com `page.tsx` + `columns.tsx`).
- A edição de um molde é uma matriz de checkboxes permissão × molde, reaproveitando a forma já validada do `RbacTab`, mas contra moldes em vez de papéis globais.
- A criação de um molde novo é um painel inline, seguindo `criar-tenant-panel.tsx`.
- **Ao gravar um molde, o ecrã avisa explicitamente que a alteração não chega a escritórios já provisionados.** Esta é a consequência directa da decisão de snapshot. Se o UI não o disser, quem administra a plataforma vai assumir propagação — é a expectativa por omissão de qualquer pessoa — e só descobrirá o contrário quando um cliente reclamar.

### Endpoints e autoridade
- `GET /platform/moldes`, `PUT /platform/moldes` e `POST /platform/moldes` no `PlatformAdminController`, que já tem `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` ao nível da classe — o gate é herdado, sem anotação adicional por método (o padrão que a Phase 119 documentou nesse ficheiro).
- `PUT /admin/rbac` não muda nesta fase. CATL-04 e a autoridade `rbac:manage` são da Phase 127.
- `PLATAFORMA_ADMIN` nunca é instanciável, nunca aparece como molde editável, e nunca é atribuível a partir de superfície de escritório. As guardas das Phases 119 e 121 transportam-se para os caminhos novos.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `SetupService.provisionTenant` (backend/src/main/java/com/lexcv/services/SetupService.java:103) — `@Transactional`, valida, cria Tenant, cria User ADMIN com `Set.of(adminRole)` obtido de `roleRepository.findByNome("ADMIN")`. É o ponto de inserção da instanciação.
- `PlatformAdminController` (linha 53) — `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` de classe, com `POST /tenants`, `GET /tenants`, `PUT /tenants/{id}` como analogias directas de forma para os endpoints novos.
- `web/src/app/(dashboard)/plataforma/relatorio/` — `page.tsx` + `columns.tsx`, a rota mais recente da consola; analogia estrutural para `/plataforma/moldes`.
- `web/src/app/(dashboard)/plataforma/criar-tenant-panel.tsx` — painel inline de criação já existente.
- O `RbacTab` em `web/src/app/(dashboard)/settings/page.tsx` — a matriz de checkboxes permissão×papel, com o detalhe (descoberto na Phase 124) de que a ordem dos módulos vem da ordem de chegada de `systemPermissions`, agora governada pela coluna `ordem`.
- `Permission` já traz de 124 os campos `rotulo`, `descricao`, `modulo`, `ordem`, `reservadaPlataforma` — a consola de moldes consome-os directamente, e é para as fases 125/127 que o mecanismo de `reservadaPlataforma` foi criado.

### Established Patterns
- Entidades JPA com Lombok `@Builder`/`@Getter`/`@Setter`; `ManyToMany` com `@JoinTable` como em `Role.permissions` e `User.roles`.
- `ddl-auto: update` cria colunas e tabelas novas em dev; produção exige script manual em `backend/migrations/` mais linha no inventário do `README.md`, em ordem numérica ascendente.
- Frontend: TanStack Query em `use-*.ts`, `apiFetch` como único wrapper, componentes shadcn em `components/ui/`.
- Testes backend em Mockito; a Phase 119 estabeleceu o padrão de provar um `@PreAuthorize` com um proxy real (`AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory`) em vez de ler a anotação por reflexão.

### Integration Points
- `SetupService.provisionTenant` → instanciação dos moldes
- `PlatformAdminController` → endpoints `/platform/moldes`
- `web/src/hooks/use-platform-*.ts` (hooks da consola da Phase 120) → hooks novos para moldes
- `web/src/app/(dashboard)/plataforma/page.tsx` → ponto de entrada "Ver Moldes", como já faz com "Ver Relatório"
</code_context>

<specifics>
## Specific Ideas

O aviso de não-propagação no ecrã de moldes não é copy decorativo — é a única coisa que impede a decisão de snapshot de se tornar uma surpresa desagradável. Deve dizer quantos escritórios já instanciaram aquele molde e que nenhum deles será afectado.

Nota herdada da Phase 124 que condiciona esta fase: `getRbac` filtra permissões sem rótulo e por `reservadaPlataforma`, mas `rolePermissions` continua sem filtro, e a resolução de autoridade no JWT também. Enquanto nenhuma permissão do catálogo estiver marcada como reservada, isto é inerte. **Se esta fase marcar alguma permissão do catálogo como reservada, essa divergência deixa de ser latente** — ver o comentário deixado no ponto de divergência em `AdminController.getRbac()` pela revisão da Phase 124 (achado WR-03).
</specifics>

<deferred>
## Deferred Ideas

- Sincronização molde → papel instanciado, com marcação de desvio — ponderada e rejeitada na abertura do marco; snapshot é a decisão
- Tecto de permissões por plano (`TenantPlano`) — requisitos futuros TECT-01/TECT-02
- Corrigir a corrida de check-then-act no arranque do seeder — risco residual aceite e documentado na Phase 124 (achado WR-02), candidato a fase própria
</deferred>
