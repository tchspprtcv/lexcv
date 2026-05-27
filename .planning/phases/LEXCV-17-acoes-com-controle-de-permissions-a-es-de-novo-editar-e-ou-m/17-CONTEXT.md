# Phase 17: Acoes UI com controlo por permissions - Context

**Gathered:** 2026-05-27
**Status:** Ready for planning

<domain>
## Phase Boundary

Aplicar controlo consistente de visibilidade e acesso no frontend para menus, acoes de novo/editar, acoes de tabela, dropdowns e acoes internas dos modulos, respeitando a permission efetiva do utilizador para `view`, `create`, `edit` e `manage`.

</domain>

<decisions>
## Implementation Decisions

### Matriz de Superficies UI
- **D-01:** A cobertura desta fase e transversal ao modulo: sidebar, CTAs principais, acoes de tabela, dropdowns, botoes de detalhe/edicao e acoes internas de sub-recursos devem respeitar permissions.
- **D-02:** Em ecras de detalhe, acoes que alteram dados seguem a mesma regra do modulo: leitura usa `view`; mutacao usa permission mutavel (`edit`, `create` quando existir, ou `manage`).
- **D-03:** Menus de contexto e dropdowns devem mostrar apenas as acoes permitidas; nao devem expor opcoes proibidas por defeito.

### Semantica de Permissions
- **D-04:** O frontend deve assumir `view` como permissao de leitura/visibilidade e `edit` como guarda-chuva mutavel quando o backend ainda nao expuser chaves mais finas.
- **D-05:** Quando um modulo passar a ter `create` separado de `edit`, a permission mais especifica vence: `create` governa acoes de novo/criacao; `edit` governa alteracoes; na ausencia de chaves finas, cai para `edit`.
- **D-06:** `manage` sobrepoe `edit` em areas administrativas e deve ser tratada como o nivel mais forte para acoes de gestao.

### UX Sem Acesso
- **D-07:** Acoes sem permissao devem ser escondidas por padrao; o utilizador nao deve ver botoes ou menus que nao pode usar.
- **D-08:** Mesmo em contexto ja aberto, a regra preferencial continua a ser esconder a acao em vez de mostrar disabled.
- **D-09:** Quando o utilizador abrir uma pagina por URL sem permissao suficiente, a UI deve bloquear com um estado claro de acesso negado, sem expor acoes nem dados indevidos.

### Abstracao e Reutilizacao
- **D-10:** O projeto deve ter um helper central de permissions no dominio de auth, consumido localmente por hooks/componentes/paginas, para evitar `includes()` espalhados e regras divergentes.
- **D-11:** A adocao deve ser cross-modulo, reutilizavel em Processos, Clientes, Agenda, Documentos, Financeiro e areas administrativas quando aplicavel.
- **D-12:** O mapeamento semantico de `view` / `create` / `edit` / `manage` deve ficar centralizado num util de auth/permissions, nao dentro de cada modulo.

### Claude's Discretion
Sem itens em aberto. O planeamento pode decidir nomes concretos do helper, assinatura das funcoes e estrategia de migracao incremental, desde que preserve as decisoes acima.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Planeamento e requisitos
- `.planning/ROADMAP.md` — define o objetivo e a dependencia da Phase 17.
- `.planning/PROJECT.md` — fixa as restricoes de RBAC no UI e o principio de frontend passivo.
- `.planning/REQUIREMENTS.md` — contem os requisitos de RBAC e navegacao ja validados (`RBAC-01`, `NAV-01`, `PRC-12`, `PRC-13`).
- `.planning/phases/15-appsec-rbac/15-CONTEXT.md` — estabelece a linha de continuidade da enforcement de permissions do backend para a UI.

### Auth e permissions
- `web/src/hooks/use-me.ts` — fonte do estado autenticado e das `permissions` consumidas no frontend.
- `web/src/types/auth.ts` — contrato de `MeResponse` com lista de `permissions`.
- `web/src/server/mock-db.ts` — dicionario atual de permissions e semantica por modulo, incluindo `:view`, `:edit`, `users:manage` e `rbac:manage`.

### Shell e pontos de integracao
- `web/src/components/shared/dashboard-shell.tsx` — padrao atual de gating na navegacao principal por `requiredPermission`.
- `web/src/app/(dashboard)/settings/page.tsx` — exemplo atual de areas administrativas guiadas por `users:manage` e `rbac:manage`.
- `web/src/app/(dashboard)/processos/page.tsx` — referencia recente de CTA controlado por `processos:edit`.
- `web/src/app/(dashboard)/processos/[id]/page.tsx` — referencia recente de gating em detalhe e sub-recursos do modulo.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `useMe()` em `web/src/hooks/use-me.ts`: centraliza o fetch de `/auth/me` e expoe a lista de permissions.
- `DashboardShell` em `web/src/components/shared/dashboard-shell.tsx`: ja possui o padrao `requiredPermission` para itens de navegacao.
- `settings/page.tsx`: mostra um padrao simples de derivacao de flags como `hasUsersManage` e `hasRbacManage`.
- Paginas de `processos`: ja tem checks locais como `canEditProcessos`, uteis como ponto de partida para extrair o helper comum.

### Established Patterns
- O frontend usa React Query + hooks para estado autenticado; decisions de accesso devem partir de `me.data?.permissions`.
- O gating visual atual e heterogeneo: a sidebar usa filtro declarativo, enquanto paginas internas usam checks inline.
- O mock e o backend atual trabalham sobretudo com pares `:view` / `:edit`, e administracao usa `:manage`.

### Integration Points
- Navegacao principal em `DashboardShell`.
- Paginas de listagem com CTA de criacao.
- Paginas de detalhe/edicao com acoes internas e sub-recursos.
- Areas administrativas em `settings/page.tsx`.

</code_context>

<specifics>
## Specific Ideas

- A regra desejada e esconder acoes proibidas por padrao, inclusive dropdowns e menus contextuais.
- O helper de permissions deve suportar evolucao futura para `create` separado sem quebrar modulos que ainda so expoem `edit`.
- A fase deve sair do estado de correcoes pontuais em `processos` para um padrao reutilizavel no resto da app.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 17-Acoes UI com controlo por permissions*
*Context gathered: 2026-05-27*
