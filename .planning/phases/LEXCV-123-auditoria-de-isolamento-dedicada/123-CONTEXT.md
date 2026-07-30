# Phase 123: Auditoria de Isolamento Dedicada - Context

**Gathered:** 2026-07-30
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — user pre-authorized Claude to decide grey areas ("o claude decide as opções e avança")

<domain>
## Phase Boundary

Última fase do marco v2.16. Não tem "UI hint" no ROADMAP.md (ao contrário das Fases 120/121/122) — é puramente uma auditoria de documentação, sem trabalho de UI ou de código de produção esperado à partida. O objetivo não é encontrar coisas novas do zero, é **confirmar e documentar com veredito explícito** que as 3 superfícies novas desta milestone (consola de tenants, relatório de utilização, bloqueio de RBAC) não deixam nenhum tenant ver ou influenciar dados de outro — antes de o utilizador poder provisionar em segurança um 2º tenant pagante real.

Investigação dedicada (esta sessão, antes de escrever este ficheiro) já confirmou, por leitura direta e fresca do código, que os 2 critérios de sucesso desta fase estão estruturalmente satisfeitos:

- **Critério 1 (endpoints gated, nunca tenant-scoped comuns)**: `PlatformAdminController` inteiro tem só o gate de classe `hasRole('PLATAFORMA_ADMIN')`, sem exceções. Grep exaustivo confirma que nenhuma outra controller (`AdminController`, `ResourceController`) escreve `Tenant`. `TenantAdminSummaryResponse` (o DTO partilhado pela consola e pelo relatório) só expõe 6 campos, nenhum sensível.
- **Critério 2 (bloqueio de RBAC sem via de contorno)**: `AdminController.updateRbac` é o único call site HTTP-alcançável que escreve `Role`/`Permission` em toda a base de código. `PlatformAdminController` não tem nenhum endpoint de RBAC próprio.

**Achado que precisa de ser documentado explicitamente, não apenas descartado em silêncio**: existe um handler morto `web/src/app/_api-backup/v1/admin/rbac/route.ts` que, à primeira vista, pareceria uma via de contorno tenant-facing para escrever RBAC (só verifica `hasRole("ADMIN")`, sem distinção de PLATAFORMA_ADMIN). Confirmado inalcançável por 2 razões independentes: (1) o prefixo `_` em `_api-backup` opta a subárvore inteira fora do sistema de rotas do Next.js App Router; (2) mesmo ignorando (1), `apiFetch` (o único cliente HTTP desta app) nunca chama rotas internas Next.js, só o backend Spring externo via Caddy. Este achado deve entrar na auditoria como "traçado e descartado", no mesmo espírito de transparência de `121-ISOL-AUDIT.md`, não silenciosamente ignorado por ser "óbvio".

</domain>

<decisions>
## Formato: reutilizar os 2 precedentes já citados pelo próprio ROADMAP

`121-ISOL-AUDIT.md` (Fase 121) já existe precisamente para esta fase citar em vez de repetir a varredura de raiz (a própria fase 121 já o diz na sua linha 13). `97-01-SUMMARY.md` (AUD-01, v2.11) é o precedente que o ROADMAP cita explicitamente ("no espírito da AUD-01"). Reutilizar literalmente o mesmo formato de tabela de veredito (`Query/Guard | Scope confirmed | Verdict`), a secção de comandos de reprodução, e um veredito explícito `COVERED` ou lista de fixes por superfície — não inventar um formato novo.

## Ficheiro de saída: `123-ISOL-AUDIT.md`

Mesmo sufixo do precedente direto (`121-ISOL-AUDIT.md`), novo número de fase. Sem alterações de código de produção esperadas — esta fase é, por natureza, uma fase de confirmação e documentação, tal como a Fase 121 tratou ISOL-01/ISOL-02.

## Decisão explícita sobre o Pitfall 1 (`findByXxxId` sem `tenantId`) — RESOLVIDA nesta fase, não deixada pendente outra vez

`121-ISOL-AUDIT.md` e `121-CONTEXT.md` nomeiam explicitamente esta fase como dona de uma decisão pendente: vários métodos de repositório (ex. `ProcessoRepository.findByClienteId`, catalogados em `.planning/research/PITFALLS.md` Pitfall 1) não têm parâmetro `tenantId` na própria query — são seguros hoje só porque cada call site re-verifica separadamente que a entidade-pai pertence ao tenant do chamador antes de usar o resultado.

**Decisão**: aceitar a dupla verificação como mitigação suficiente por agora, documentando isto como um **risco residual conhecido e aceite** (não "corrigido", não "fora de âmbito silenciosamente ignorado"). Razões:
1. É um padrão pré-existente há várias milestones, não introduzido nem agravado especificamente por esta milestone (o risco teórico é "qualquer tenant vs qualquer tenant", já existente mesmo antes de qualquer trabalho multi-tenant explícito).
2. Cada call site já confirmado, em investigações anteriores desta sessão, a fazer a reverificação corretamente — zero indícios de um call site que a tenha esquecido.
3. Corrigir isto por completo exigiria alterar a assinatura de ~11 métodos de repositório e todos os seus call sites — um refactor grande, com risco de regressão real, claramente fora do âmbito desta milestone (que é sobre as 3 superfícies novas, não uma auditoria de segurança total e histórica do codebase).
4. Não bloqueia a criação seguro de um 2º tenant pagante real — o risco já existe hoje independentemente de quantos tenants reais existirem, e nenhuma das 3 superfícies novas desta milestone o agrava.

Esta fase deve registar esta decisão explicitamente no `123-ISOL-AUDIT.md`, com uma recomendação clara de que uma eliminação completa deste padrão exigiria uma fase dedicada de refactor de repositórios — matéria para uma milestone futura, não para agora.

## Claude's Discretion
- Estrutura exata das secções dentro de `123-ISOL-AUDIT.md` (uma secção por critério de sucesso, ou por superfície) — seguir o que ler melhor dado o conteúdo, inspirado nos 2 precedentes.
- Se a auditoria precisa de qualquer prova ao vivo por HTTP (reutilizando o que já foi confirmado nas Fases 120/121/122) ou se a evidência estática + reconfirmação de leitura direta é suficiente — decidir com base no que a investigação encontrar durante o planeamento, sem assumir que precisa de repetir todo o UAT visual das fases anteriores.

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Formato de tabela de veredito de `97-01-SUMMARY.md` (AUD-01) e `121-ISOL-AUDIT.md` — reutilizar diretamente
- Toda a evidência já reunida nas Fases 119-122 sobre os 3 pontos de bloqueio de tenant suspenso, o gate de `PlatformAdminController`, e o bloqueio de `updateRbac` — esta fase confirma/cita, não redescobre do zero

### Established Patterns
- Auditoria = confirmação + documentação, não necessariamente novo código
- Achados "traçados e descartados" (como o handler morto em `_api-backup`) entram explicitamente na auditoria, não são silenciosamente omitidos

### Integration Points
- Novo ficheiro: `.planning/phases/LEXCV-123-auditoria-de-isolamento-dedicada/123-ISOL-AUDIT.md`
- Ficheiros a citar/reconfirmar por leitura direta (não necessariamente modificar): `PlatformAdminController.java`, `AdminController.java` (`updateRbac`/`getRbac`), `AlertasDiariosJob.java`, `AuthController.java`, `JwtAuthenticationFilter.java`, `PublicController.java`, `web/src/app/_api-backup/v1/admin/rbac/route.ts` (confirmar morto), `.planning/research/PITFALLS.md`

</code_context>

<specifics>
## Specific Ideas

Nenhuma além do que está capturado acima e nos Critérios de Sucesso do ROADMAP.md para a Fase 123.

</specifics>

<deferred>
## Deferred Ideas

- Refactor completo dos métodos de repositório do Pitfall 1 para incluir `tenantId` na própria query — matéria para uma milestone futura, explicitamente fora do âmbito da v2.16 (ver decisão acima).
- Remoção do handler morto `_api-backup` — confirmado inalcançável e inofensivo; não há necessidade funcional de o remover nesta fase, mas pode ser referido como limpeza técnica opcional para uma fase futura de manutenção.

</deferred>
