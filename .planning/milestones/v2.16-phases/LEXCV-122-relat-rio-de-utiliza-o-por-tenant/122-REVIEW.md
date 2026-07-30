---
phase: LEXCV-122-relat-rio-de-utiliza-o-por-tenant
reviewed: 2026-07-30T12:00:00Z
depth: deep
files_reviewed: 6
files_reviewed_list:
  - backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java
  - web/package.json
  - web/scripts/verify-relatorio-utilizacao.mjs
  - web/src/app/(dashboard)/plataforma/page.tsx
  - web/src/app/(dashboard)/plataforma/relatorio/columns.tsx
  - web/src/app/(dashboard)/plataforma/relatorio/page.tsx
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase 122: Code Review Report

**Reviewed:** 2026-07-30T12:00:00Z (ronda 1) / verificação de ronda 2 em 2026-07-30
**Depth:** deep
**Files Reviewed:** 6
**Status:** issues_found (0 bloqueadores — ver Veredito Final)

## Summary

Revisão de profundidade "deep" aos 6 ficheiros da Fase 122 (Relatório de Utilização por Tenant), com análise cruzada aos ficheiros não listados mas necessários para verificar as afirmações da fase: `PlatformAdminController.java`, `TenantAdminSummaryResponse.java`, `Tenant.java`, `TenantRepository.java`, `UserRepository.java`, `UserPrincipal.java`, `AuthController.java`, `use-me.ts`, `use-platform-admin.ts`, `types/platform-admin.ts`, `types/auth.ts`, `plataforma/columns.tsx` (o ficheiro-irmão, factory function), `dashboard-shell.tsx`, `proxy.ts`, `card.tsx`, `badge.tsx`, `lib/utils.ts` (`cn`/`twMerge`) e `data-table.tsx`. Também corri `node scripts/verify-relatorio-utilizacao.mjs` (15/15 PASS) e confirmei via `git log` que o único ficheiro de produção do backend tocado por commits da Fase 122 é o ficheiro de teste — nenhuma alteração a `PlatformAdminController.java`/DTOs/entidades nesta fase, confirmando a afirmação do contexto da fase.

Não encontrei nenhum BLOCKER. A implementação é sólida e reutiliza corretamente padrões já validados nas Fases 119/120 (guarda de página, contagem via `countByTenantIdAndAtivoTrue`, badges, `tenantInitials`). Os achados abaixo são 2 Warnings sobre a fiabilidade do próprio gate `verify-relatorio-utilizacao.mjs` — relevantes porque esta fase, por bloqueio de ferramenta no UAT humano visual, depende deste gate como a principal rede de segurança automatizada — e 3 Info sobre duplicação/acoplamento que não constituem bugs atuais.

Respostas diretas às 6 perguntas do contexto da fase:

1. **Guarda de papel em `relatorio/page.tsx` falha fechado?** Sim. `if (!me.isFetched) return null;` resolve sempre antes do teste de papel (`relatorio/page.tsx:40-42`), byte a byte o mesmo padrão de `plataforma/page.tsx:79-81` (WR-03 da Fase 120). Tracei os 3 estados possíveis de `useMe()` (a carregar, erro, sucesso sem papel) e em todos os 3 o resultado é `AccessDeniedState`, nunca o conteúdo protegido — confirmei que `isFetched` fica `true` mesmo após erro (TanStack Query v5: `errorUpdateCount > 0` conta como fetched), e mesmo nesse caminho `me.data` continua `undefined`, logo `!me.data?.roles?.includes(...)` continua `true`. Confirmei também, subindo à cadeia de autenticação (`AuthController.getMe:214`, `UserPrincipal.getRoles()` vs `getAuthorities()`), que o backend expõe `roles` sem o prefixo `ROLE_`, exatamente o que o guard do cliente espera — as duas camadas (frontend `includes("PLATAFORMA_ADMIN")` e backend `hasRole('PLATAFORMA_ADMIN')`) estão de facto alinhadas, não há um "sempre nega mesmo ao dono certo" escondido aqui.
2. **`columns.tsx` como array estático — risco de fuga de estado?** Não, é seguro precisamente pela razão que o contexto da fase aponta. `relatorioColumns` (relatorio/columns.tsx:35-119) é um `const` de módulo, sem closures sobre estado de componente — cada `cell`/`header` só lê `row.original`/`column`, parâmetros frescos por render do próprio TanStack Table. O estado mutável de UI (sorting, columnVisibility) vive em `useState` dentro de cada instância de `DataTable` (data-table.tsx:53-55), nunca no objeto `columnDef` partilhado — logo mesmo reutilizar a mesma referência de array entre múltiplas instâncias de `DataTable` seria seguro. Nenhum finding aqui.
3. **Números de "utilizadores ativos" — alguma divergência da fonte única?** Não encontrei nenhum recálculo no cliente. `relatorio/columns.tsx:79` (`accessorFn: (tenant) => tenant.utilizadoresAtivos`) e `relatorio/page.tsx:121,167` usam sempre `tenant.utilizadoresAtivos` tal como chega da API; confirmei na origem que esse campo é escrito uma única vez, em `PlatformAdminController.toSummary():198`, chamando `userRepository.countByTenantIdAndAtivoTrue(tenant.getId())` — o mesmo método que o comentário em `UserRepository.java:32-38` documenta como "ÚNICA fonte de verdade" partilhada pelas Fases 117/120/122. Nenhum finding aqui.
4. **O novo teste de regressão é tautológico?** Não. Confirmei lendo `PlatformAdminController.listTenants()` (linhas 106-113) que não existe nenhuma condição de filtro sobre `ativo` no pipeline atual — logo `listTenants_incluiTenantSuspensoComEstadoAtivoFalseNaResposta` (PlatformAdminControllerTest.java:333-356) prova uma propriedade real: se um refactor futuro introduzir `.filter(Tenant::getAtivo)`, `corpo.size()` deixaria de ser 2 e `findFirst().orElseThrow()` lançaria, falhando o teste. Não é fraco.
5. **Fuga de dados entre tenants / ADMIN normal alcançar a rota?** Não encontrei nenhuma. `PlatformAdminController` está gated à classe com `@PreAuthorize("hasRole('PLATAFORMA_ADMIN')")` (linha 53) — o Grupo B de testes já prova com um proxy AOP real que um `ROLE_ADMIN` recebe `AccessDeniedException` antes de tocar nos repositórios (`listTenants_comRoleAdminDeTenantNormalERecusadoAntesDeAlcancarOsRepositorios`). `TenantAdminSummaryResponse` (DTO) omite deliberadamente NIF/email/telefone/logo — só os 6 campos que a UI precisa. Confirmei também que não existe nenhum `layout.tsx` específico de `/plataforma` que pudesse divergir do gate de `/plataforma/relatorio`, e que `proxy.ts` (edge middleware) só trata do redirecionamento do assistente de setup, nunca de papéis — a única fronteira de autorização real continua a ser o `@PreAuthorize` de classe no backend, com o guard de página como espelho de UX, consistente com o resto do codebase.
6. **"Ver Relatório" introduziu regressão no "Criar Tenant" ou no layout do cabeçalho?** Não encontrei nenhuma regressão funcional. `onClick={() => setIsFormOpen(true)}` continua a aparecer exatamente uma vez (plataforma/page.tsx:176), as classes `bg-blue-600 hover:bg-blue-700 text-white` do botão "Criar Tenant" continuam intactas (linha 177), e o `CardHeader` continua com 2 filhos diretos e `justify-between` (o botão único de antes tornou-se um `<div className="flex items-center gap-2">` com 2 botões). Confirmei em `lib/utils.ts` que `cn()` usa `twMerge(clsx(...))`, pelo que adicionar `flex-wrap` à classe já existente do `CardHeader` (que a base do componente já sobrepunha com `flex-col`→`flex-row` antes desta fase) resolve corretamente o conflito de utilitários, sem quebrar o padrão já em produção desde a Fase 120. A única ressalva é cosmética e não verificável por código — ver IN-03 abaixo.

## Ronda 2 — Verificação da Correção

Uma correção resolveu ambos os Warnings num único commit: `66bfac72`. Não modifiquei este ficheiro (REVIEW.md) durante a correção — o orquestrador trata da atualização.

- **WR-01/WR-02 — CONFIRMADO-RESOLVIDO, com uma correção adicional durante a implementação.** A correção aplicada difere ligeiramente da sugestão literal deste documento: em vez de terminar a fatia no índice do próprio texto do botão (`plataformaPage.indexOf("Ver Relatório"/"Criar Tenant", ...)`), usa `plataformaPage.indexOf("</Button>", ...)` como fim do bloco. Motivo: a primeira tentativa de implementação, terminando a fatia no primeiro `">"` a seguir a `"<Button"`, apanhou acidentalmente o `"=>"` do próprio `onClick={() => setIsFormOpen(true)}` como falso fecho de tag — um bug real introduzido pela primeira tentativa de correção, encontrado e corrigido antes do commit final através de uma prova negativa deliberada (alterar a classe do botão "Criar Tenant" para uma cor diferente, confirmar que a asserção `entrada-ordem-e-criar-tenant-intocado` falha corretamente, depois reverter). Usar `"</Button>"` como delimitador de fim evita esta classe de problema por completo, já que não depende de contar `">"` soltos dentro de expressões JS. Re-executado `node scripts/verify-relatorio-utilizacao.mjs`: 15/15 PASS. `pnpm lint`: 0 erros.
- Nenhum dos 3 achados Info (IN-01/IN-02/IN-03) foi corrigido nesta ronda — todos permanecem exatamente como descritos abaixo, com a mesma disposição ("melhoria futura", "acompanhamento cosmético pendente de UAT visual"), consistente com a sua própria classificação como não-bloqueadores.

## Warnings

### WR-01: Asserção "temOutline" do gate não está isolada ao botão que diz proteger (RESOLVIDO — ver Ronda 2)

**File:** `web/scripts/verify-relatorio-utilizacao.mjs:246` (bloco `entrada-ver-relatorio`, linhas 239-250)

**Issue:** A asserção `entrada-ver-relatorio` alega provar que o botão "Ver Relatório" usa `variant="outline"`, mas o predicado testa a string inteira do ficheiro (`plataformaPage.includes('variant="outline"')`), não uma fatia à volta do próprio botão. `plataforma/page.tsx` tem outra ocorrência legítima e não relacionada de `variant="outline"` — o `<Badge variant="outline">Plataforma</Badge>` do tenant reservado (linha 223, e repetido no card mobile na linha ~222). Isto significa que esta asserção continuaria a passar (`PASS`) mesmo que um refactor futuro removesse `variant="outline"` do botão "Ver Relatório" (por exemplo, trocando para `variant="secondary"`) — o gate daria falso positivo exatamente na regressão que diz vigiar. Isto é particularmente relevante nesta fase porque o UAT humano visual (H1-H6) não foi concluído, e este gate ficou a ser a principal rede de segurança automatizada substituta.

**Fix:** Isolar a procura ao botão específico antes de testar `variant="outline"`, por exemplo ancorando a partir do `<Button` mais próximo antes do texto "Ver Relatório":
```js
const idxCopy = plataformaPage.indexOf("Ver Relatório");
const idxBotao = plataformaPage.lastIndexOf("<Button", idxCopy);
const blocoBotaoVerRelatorio = idxBotao !== -1 ? plataformaPage.slice(idxBotao, idxCopy) : "";
const temOutline = blocoBotaoVerRelatorio.includes('variant="outline"');
```

### WR-02: Asserção "temClassesIntactas" do gate também não está isolada ao botão "Criar Tenant" (RESOLVIDO — ver Ronda 2)

**File:** `web/scripts/verify-relatorio-utilizacao.mjs:261` (bloco `entrada-ordem-e-criar-tenant-intocado`, linhas 252-263)

**Issue:** Mesmo padrão de WR-01. A asserção alega provar que a classe `"bg-blue-600 hover:bg-blue-700 text-white"` do botão "Criar Tenant" foi preservada, mas testa a string inteira do ficheiro. Essa mesma sequência de classes aparece também, verbatim, no botão "Guardar" do `EditarTenantForm` (`plataforma/page.tsx:513`: `<Button type="submit" className="bg-blue-600 hover:bg-blue-700 text-white" ...>`). Um refactor que alterasse ou removesse essa classe especificamente do botão "Criar Tenant" (por exemplo, trocando a cor) continuaria a passar este gate, porque o botão "Guardar" — completamente alheio a esta fase — mantém a mesma substring.

**Fix:** Isolar a fatia ao botão "Criar Tenant" antes de verificar as classes, por exemplo ancorando a partir de `setIsFormOpen(true)`:
```js
const idxSetIsFormOpen = plataformaPage.indexOf("setIsFormOpen(true)");
const idxBotaoCriar = plataformaPage.lastIndexOf("<Button", idxSetIsFormOpen);
const idxFimBotaoCriar = plataformaPage.indexOf("Criar Tenant", idxSetIsFormOpen);
const blocoBotaoCriar = plataformaPage.slice(idxBotaoCriar, idxFimBotaoCriar);
const temClassesIntactas = blocoBotaoCriar.includes("bg-blue-600 hover:bg-blue-700 text-white");
```

## Info

### IN-01: Mapeamento plano→variant do Badge duplicado em 4 locais (2 novos nesta fase)

**File:** `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx:12-16`, `web/src/app/(dashboard)/plataforma/relatorio/page.tsx:139-146`

**Issue:** O par `STARTER→gray / STANDARD→purple / ENTERPRISE→amber` já existia duplicado entre `plataforma/columns.tsx:20-24` (`PLANO_BADGE_VARIANT`, um `Record<TenantPlano, ...>` exaustivo) e o ternário inline em `plataforma/page.tsx:227-234` (mobile). Esta fase acrescenta mais 2 cópias: `relatorio/columns.tsx:12-16` repete o mesmo `Record` verbatim, e `relatorio/page.tsx:139-146` repete o mesmo ternário inline. Isto passa a 4 pontos a manter em sincronia. A assimetria importa: as 2 cópias em `Record<TenantPlano, ...>` dão erro de compilação TypeScript se um novo valor for acrescentado a `TenantPlano` sem atualizar o mapa; as 2 cópias em ternário (ambos os `page.tsx`, mobile) não — um 4º plano cairia silenciosamente no último ramo (`"amber"`) sem qualquer aviso do compilador.

**Fix:** Extrair um único helper partilhado (ex.: `web/src/lib/tenant-plano-badge.ts`, ao lado de `tenant-initials.ts`) exportando o `Record<TenantPlano, "gray" | "purple" | "amber">`, e importar esse helper nos 4 locais em vez de o reimplementar.

### IN-02: `relatorio/columns.tsx` e `relatorio/page.tsx` acoplados ao módulo de escrita `../columns` só para reaproveitar uma constante

**File:** `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx:10`, `web/src/app/(dashboard)/plataforma/relatorio/page.tsx:17`

**Issue:** Ambos importam `TENANT_RESERVADO` de `"../columns"` (`plataforma/columns.tsx`), o ficheiro `"use client"` que também define `TenantAcoesCell`, e importa `Tooltip`/`Pencil`/`Unlock`/`Button` — toda a árvore de dependências das ações de escrita (editar/suspender) de que este ecrã, deliberadamente 100% leitura, não precisa. Isto não é um bug funcional (o próprio docstring de `relatorio/columns.tsx:20-24` já explica que a duplicação célula-a-célula é intencional, e o restante código deste ficheiro reaproveita conscientemente `../columns` só pela constante), mas acopla o grafo de módulos do relatório ao ecrã de escrita — uma alteração ou erro introduzido em `TenantAcoesCell` (nunca renderizado aqui) poderia, em cenários de bundling menos favoráveis ao tree-shaking, ainda assim ser arrastado para o bundle do relatório.

**Fix:** Mover `TENANT_RESERVADO` (e potencialmente o mapa de IN-01) para um módulo partilhado sem JSX nem dependências de UI (ex.: `web/src/lib/tenant-labels.ts`), importado por `plataforma/columns.tsx`, `plataforma/page.tsx`, `relatorio/columns.tsx` e `relatorio/page.tsx` por igual — desacopla totalmente o relatório do módulo de escrita.

### IN-03: Grupo de 2 botões no `CardHeader` sem safeguard de quebra próprio em ecrãs muito estreitos

**File:** `web/src/app/(dashboard)/plataforma/page.tsx:163,168-182`

**Issue:** `flex-wrap` foi acrescentado ao `CardHeader` (linha 163) para permitir que o bloco de título e o grupo de botões quebrem para linhas separadas em ecrãs estreitos — correto e consistente com `twMerge`. Mas o `<div className="flex items-center gap-2">` que agora contém 2 botões com rótulo completo ("Ver Relatório" e "Criar Tenant", ambos com ícone + texto, linhas 168-182) não tem, ele próprio, nenhuma classe de quebra — em larguras muito estreitas (telemóvel pequeno) os 2 botões ficam lado a lado nessa mesma linha, sem encolherem nem empilharem independentemente do bloco de título. Não é possível confirmar visualmente nesta fase (H1-H6 bloqueado por ferramenta, conforme o contexto da fase), por isso fica registado como item de acompanhamento cosmético, não como bug confirmado.

**Fix:** Quando o UAT visual humano for possível, testar a ~360px de largura; se necessário, considerar `flex-wrap` também no `div` interno, ou abreviar/ocultar o rótulo de texto de um dos botões abaixo de `sm:`.

## Veredito Final

**Fase 122 (Relatório de Utilização por Tenant) está APROVADA — sem bloqueadores.**

- 0 achados Critical, nesta ronda ou em qualquer anterior.
- 0 Warnings bloqueadores em aberto: WR-01 e WR-02 estão ambos resolvidos e reverificados, com prova negativa confirmando que o gate agora deteta genuinamente as regressões que diz vigiar.
- 0 itens Info que exijam correção antes do fecho: IN-01/IN-02/IN-03 são melhorias de manutenibilidade/acoplamento e um item cosmético pendente de UAT visual — nenhum é um defeito atual, todos ficam registados para acompanhamento futuro.
- Gates de regressão re-executados de forma independente e verdes: `node scripts/verify-relatorio-utilizacao.mjs` (15/15), `pnpm lint` (0 erros).
- Esta fase teve o seu UAT visual humano (H1-H6) bloqueado por uma questão de ferramenta (Browser MCP), não do produto — decisão explícita e datada do utilizador em `122-HUMAN-UAT.md` de aceitar a evidência disponível (gates automatizados + prova HTTP real via A1-A3, incluindo a descoberta e correção de uma regressão real de migração pendente) e prosseguir. Esta revisão de código funcionou deliberadamente como rede de segurança adicional dado esse contexto, e não encontrou nenhum indício de defeito nas partes que o UAT visual não pôde confirmar.

Não é necessária mais nenhuma iteração de correção para esta fase.

---

_Reviewed: 2026-07-30T12:00:00Z (ronda 1)_
_Verificação de ronda 2: 2026-07-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
