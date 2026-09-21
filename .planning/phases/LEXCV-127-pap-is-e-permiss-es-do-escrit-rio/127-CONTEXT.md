# Phase 127: Papéis e Permissões do Escritório - Context

**Gathered:** 2026-09-21
**Status:** Ready for planning
**Mode:** Smart discuss (corrida autónoma). Não é infraestrutura — é a fase que entrega o valor do marco ao cliente, e a que inverte uma garantia anterior.

<domain>
## Phase Boundary

O administrador de um escritório passa a gerir por inteiro os papéis do seu próprio escritório — listar, criar, renomear, editar permissões, apagar, atribuir a utilizadores — sem depender da plataforma para nada disto, e com a certeza de que nada do que faz alcança outro tenant.

Dentro do âmbito: `GET/PUT /admin/rbac` tenant-scoped sob `hasAuthority('rbac:manage')`, o CRUD de papéis próprios, o ecrã de Definições reescrito para editável, e as guardas de isolamento e de `PLATAFORMA_ADMIN` nos caminhos novos.

Fora do âmbito: auditoria (Phase 128).
</domain>

<decisions>
## Implementation Decisions

### Decisão 1 — Esta fase inverte deliberadamente a Fase 121, e tem de o fazer por escrito

A Fase 121 fechou `PUT /admin/rbac` a `PLATAFORMA_ADMIN` e substituiu o botão de gravar por um badge "Gerido pela Plataforma". Fê-lo por uma razão que era **correcta na altura**: `Role` e `Permission` eram tabelas globais sem `tenant_id`, pelo que um ADMIN de escritório a gravar aquela matriz reescrevia as mesmas linhas de que dependiam todos os outros escritórios.

Essa razão desapareceu. As Fases 125 e 126 deram a cada escritório os seus próprios papéis em `t_tenant_role`, e a resolução de autoridade já lê de lá. Gravar a matriz deixou de tocar em linhas partilhadas.

**Isto não é "desfazer" a Fase 121 — é cumprir a condição que a tornava temporária.** O comentário `ISOL-03` em `AdminController.updateRbac` explica exactamente porquê o gate existe; ao removê-lo, a substituição tem de explicar porque é que já não é preciso, ou o próximo leitor vai assumir que alguém o apagou por engano.

### Decisão 2 — Dois pré-requisitos herdados que bloqueiam a renomeação (PAPEL-04)

**a) `ParecerController:423` e `:494`.** Ambos fazem `principal.getRoles().contains("ADMIN")`, a decidir quem pode entregar um parecer e criar uma versão. Hoje funcionam porque `TenantRole.nome` é cópia literal do nome do molde. **No instante em que esta fase entregar a renomeação, um escritório que renomeie o seu papel de administrador perde, sem aviso nenhum, a capacidade de entregar pareceres.**

A Fase 126 não os converteu por razão técnica explícita: `principal.getRoles()` devolve `Set<String>` de nomes, não entidades, logo resolver por proveniência exigiria que o `UserPrincipal` passasse a transportar `moldeId`. **Essa alteração ao contrato do principal pertence a esta fase** — é aqui que a renomeação se torna possível, logo é aqui que a dívida vence.

**b) O gate `verify:bloqueio-rbac`.** Tem 12 asserções escritas para provar que um ADMIN de escritório **não** consegue gravar a matriz. Esta fase entrega o oposto. O gate tem de ser **reescrito para provar a nova garantia**, não apagado nem deixado a falhar. Uma das suas asserções (`A02`) protege a Regra dos Hooks — que `useMe()` é chamado antes do primeiro early-return no `RbacTab` — e essa continua a valer independentemente de quem pode gravar. Perder essa asserção na reescrita seria trocar uma protecção real por nada.

### Decisão 3 — `rbac:manage` passa finalmente a governar alguma coisa

`CATL-04` está mapeado a esta fase. A permissão existe no catálogo desde sempre, abre o separador no frontend (`can.manage("rbac")`), e até hoje não governa nada: o endpoint de escrita exige `hasRole('PLATAFORMA_ADMIN')`. Quem a recebesse via o separador e levava 403 ao gravar.

`PUT /admin/rbac` passa a `@PreAuthorize("hasAuthority('rbac:manage')")`. Nota para o planeamento: `UserPrincipal.create` não prefixa permissões com `ROLE_`, ao contrário dos papéis — `hasAuthority` é a forma correcta, `hasRole` não funcionaria.

### Decisão 4 — O que um escritório pode e não pode apagar

- Um papel **atribuído a pelo menos um utilizador** não pode ser apagado (PAPEL-05). A verificação é por contagem, não por tentativa-e-erro sobre uma violação de chave estrangeira.
- O papel de administrador do próprio escritório não pode ser apagado nem despojado das permissões que o tornam administrador (PAPEL-08). O discriminador é a proveniência (`moldeId` do molde ADMIN), não o nome — precisamente porque o nome passa a ser editável nesta fase.
- `TenantRole.sistema` marca os papéis instanciados de molde. Existe desde a Fase 125 exactamente para esta fase decidir o que é apagável.

### Decisão 5 — Isolamento é o requisito, não um efeito colateral

PAPEL-07 pede que nenhuma acção de um escritório alcance outro. Isso não se prova com um comentário: prova-se com um teste que monta dois tenants, grava no primeiro e assere que o segundo não mudou. A Fase 121 deixou um precedente de como se prova um gate a sério (proxy real `AuthorizationManagerBeforeMethodInterceptor` + `ProxyFactory`, nunca reflexão sobre a anotação) e a Fase 126 deixou o precedente de provar isolamento multi-tenant em teste.

### Decisão 6 — A atribuição de papéis a utilizadores passa a ser por identidade, não por nome

Encontrado no mapeamento de padrões desta fase, e é uma colisão entre duas alterações que ninguém tinha ligado:

`AdminController.createUser`/`updateUser` recebem hoje **nomes de papéis globais** (`roleRepository.findByNome`) e a Fase 126 fê-los resolver o `TenantRole` equivalente por nome, via `resolverPapeisDeEscritorio`. A correcção da revisão da Fase 126 fez esse método **lançar excepção em mapeamento parcial** — decisão correcta, que fechou um crítico onde permissões desapareciam em silêncio.

Junte-se a isso a renomeação que esta fase entrega: um escritório renomeia "ADVOGADO" para "Advogado Sénior", e a partir daí atribuir esse papel a um utilizador resolve o papel global "ADVOGADO", não encontra `TenantRole` homónimo, e devolve **409**. A gestão de utilizadores parte por causa de uma renomeação feita noutro ecrã.

**Decisão:** a atribuição passa a referir papéis do escritório por **id**, não por nome. É o modelo correcto agora — um administrador escolhe de entre os papéis do seu próprio escritório, que é precisamente o que os requisitos PAPEL-01 e PAPEL-06 descrevem. O caminho por nome global deixa de ser usado para atribuição.

Isto tem consequência no frontend: o formulário de utilizador passa a listar os papéis do escritório (com os seus nomes actuais, editáveis) em vez dos nomes globais. Planear em conjunto, não como afterthought — um sem o outro deixa a gestão de utilizadores quebrada.

### Claude's Discretion
A forma dos endpoints de CRUD, a organização do ecrã reescrito, e a estrutura dos testes ficam ao critério do executor, desde que as seis decisões acima sejam respeitadas.

### Correcção factual para o planeamento
`UserPrincipal.create` tem **dois** sítios de chamada em produção — `JwtAuthenticationFilter` e `VerificacaoDerivaPapeisService` (duas vezes). O `AuthController` **não** o chama directamente, ao contrário do que o levantamento inicial supôs. Vários testes chamam-no e precisam de acompanhar qualquer alteração de assinatura.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ResolucaoPapeisService` (Phase 126) — `resolverNomesPapeis`, `resolverPermissoesEfectivas`, `temPapelDeMolde(user, nomeMolde)` (proveniência, sobrevive a renomeação), `resolverPapeisDeEscritorio(tenantId, papeisGlobais)` (lança `MapeamentoParcialPapeisException` em mapeamento parcial), e o ponto único `usaPapeisDeEscritorio`, que desde a correcção da revisão discrimina por deter `PLATAFORMA_ADMIN` e não por ausência de dados
- `TenantRole` — `tenantId`, `nome`, `moldeId` (proveniência, não navegável), `sistema`, `permissions` (EAGER), igualdade por `(tenantId, nome)`
- `TenantRoleRepository` — `findByTenantId`, `findByTenantIdAndNome`, `countByMoldeId`
- O ecrã `/plataforma/moldes` (Phase 125) — a matriz permissão×papel já existe, com `scope="col"`, `<th scope="row">` + `text-left`, `aria-label` por checkbox, e a fusão de edições não gravadas em `merge-local-permissoes.ts`. O ecrã do escritório é o mesmo problema com outro âmbito; reaproveitar em vez de reinventar
- `web/scripts/verify-consola-moldes.mjs` — 16 asserções, o padrão de gate estrutural a seguir para a reescrita do `verify:bloqueio-rbac`

### Established Patterns
- `@PreAuthorize` de método sobrepõe-se ao de classe, nunca se soma (documentado no `AdminController` pela Phase 121)
- Testes Mockito; gates provados com proxy real
- `ddl-auto: update` em dev, script manual obrigatório em produção. Próximo número livre: **128**
- Frontend: TanStack Query + `apiFetch`, react-hook-form + Zod, primitivos shadcn já presentes

### Integration Points
- `AdminController.getRbac`/`updateRbac` — passam a tenant-scoped
- `web/src/app/(dashboard)/settings/page.tsx` — o `RbacTab` e o badge "Gerido pela Plataforma"
- `web/src/hooks/use-admin.ts#useAdminRbac`
- `ParecerController:423,494` e `UserPrincipal` — a dívida da Decisão 2a
- `web/scripts/verify-bloqueio-rbac.mjs` — a reescrita da Decisão 2b
</code_context>

<specifics>
## Specific Ideas

Sete migrações manuais já estão pendentes em produção e esta fase pode acrescentar a oitava. Se conseguir entregar os requisitos sem alterar o esquema, é preferível — o custo de operação já é alto.

O ecrã do escritório deve dizer com clareza o que é um papel instanciado de molde e o que é um papel próprio, porque as regras do que se pode apagar diferem entre os dois e um administrador que não veja a diferença vai achar o sistema arbitrário.
</specifics>

<deferred>
## Deferred Ideas

- Verificação por permissão em vez de proveniência nos sítios de lógica de negócio de processos (`ResourceController`) — muda quem pode ser responsável de processo, decisão de produto
- Remover `t_user_role.role_id` depois de a conversão estabilizar — fase própria, depois do marco
- Tecto de permissões por plano (`TenantPlano`) — TECT-01/TECT-02
</deferred>
