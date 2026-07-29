# Phase 118 — Human UAT ao vivo

**Executado:** 2026-07-29
**Ambiente:** backend `mvn spring-boot:run` (porta 8080) + frontend `pnpm dev` (porta 3000), Postgres local via `psql` bundled em `C:\Program Files\PostgreSQL\18\bin\psql.exe`
**Executor:** Claude (via Browser MCP — navegação real, cliques reais, foco de teclado real, não apenas leitura de código)

## Pontos verificados

1. **CONFIRMADO** — `GET /api/v1/auth/me` ao vivo, contra o backend real (autenticado via CLI com `admin@lexcv.cv`), devolve `"tenant_plano":"STANDARD","tenant_limite_utilizadores":5` no JSON depois de configurado o tenant no limite. Confirmado por `curl` direto e por `fetch()` dentro da própria página.

2. **CONFIRMADO** — Com o tenant exatamente no limite (`plano=STANDARD`, `limite_utilizadores=5`, 5 utilizadores ativos), a aba "Gestão de Utilizadores" mostra o texto exato `"5/5 utilizadores · limite atingido"` junto ao botão "Novo Utilizador", legível sem passar o rato.

3. **CONFIRMADO** — O botão "Novo Utilizador" está nativamente `disabled` (`button.disabled === true`) e com opacidade reduzida (`opacity: 0.5`) no estado no limite.

4. **CONFIRMADO** — Tooltip com rato: ao fazer hover sobre o botão desativado e aguardar o `delayDuration` (700ms), aparece um elemento `role="tooltip"` com o texto exato `"Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga."`. **Este é o ponto mais importante da verificação** — primeira confirmação real, neste codebase, de um tooltip a disparar sobre um `Button` nativamente `disabled`, fechando a dívida documentada desde a Phase 102 (v2.13).

5. **CONFIRMADO** — Tooltip com teclado: ao focar diretamente o `<span tabIndex="0" data-slot="tooltip-trigger">` que envolve o botão desativado (equivalente a chegar lá via `Tab`), o mesmo `role="tooltip"` com o mesmo texto aparece — veredito separado do ponto 4, confirmando que a técnica do wrapper funciona tanto por rato como por teclado.

6. **CONFIRMADO** — Com `limite_utilizadores = NULL` (reposto via `psql`, seguido de navegação para fora e de volta à aba para forçar refetch — ver nota sobre `Ctrl+Shift+R` abaixo), o contador lê apenas `"5 utilizadores"` (sem barra, sem "limite atingido") e o botão "Novo Utilizador" volta a `disabled: false`.

7. **CONFIRMADO** — Com `limite_utilizadores = 7` (acima da contagem atual de 5), o contador lê `"5/7 utilizadores"` na cor cinzenta (`text-slate-500`, classe `text-xs text-slate-500 dark:text-slate-400`, não `destructive`), botão continua `disabled: false`.

8. **CONFIRMADO** — Com o formulário "Novo Utilizador" aberto e preenchido (nome/email/password válidos, sem submeter), o limite foi baixado via `psql` para igualar a contagem atual (simulando a corrida de duas abas), e só depois o formulário foi submetido. Resultado:
   - Toast local: `"Erro" + "Limite de utilizadores atingido para o vosso plano."` — **sem** o prefixo `API 409:`.
   - Banner de erro dentro do formulário: `"Limite de utilizadores atingido para o vosso plano."` — também limpo.
   - Toast automático genérico do `apiFetch` também apareceu, com o texto `"Erro 409: Limite de utilizadores atingido para o vosso plano."` — **este prefixo é esperado e documentado** como comportamento pré-existente para todos os erros da aplicação (não é um defeito desta fase; ver nota no plano).
   - A página não rebentou — o formulário continuou aberto e utilizável, sem tela em branco.
   - Confirmado via `psql` que nenhum utilizador `teste.uat118@lexcv.cv` foi de facto criado (`count = 0`) — o backend rejeitou corretamente a escrita.

9. **CONFIRMADO** — Reposição: `plano` e `limite_utilizadores` repostos via `psql` para os valores **originais realmente observados** no arranque desta sessão (`NULL`/`NULL` — não `ENTERPRISE`/`NULL` como o plano assumia; ver nota abaixo). Confirmado por `SELECT` e por recarregamento da página (via navegação, não hard-reload — ver nota) mostrando o contador de volta a `"5 utilizadores"` e o botão ativo.

## Nota importante: valores originais divergentes do assumido pelo plano

O plano assumia que o backfill da migração da Phase 117 tinha deixado `plano='ENTERPRISE'`/`limite_utilizadores=NULL` no tenant existente. O `SELECT` inicial desta sessão mostrou, na realidade, `plano=NULL`/`limite_utilizadores=NULL` — porque este projeto não usa Flyway/Liquibase (migrações são scripts SQL manuais, por convenção documentada em `backend/migrations/`), e o `ddl-auto=update` do Hibernate em dev cria as colunas automaticamente mas **não corre o `UPDATE` de backfill do script manual**. Isto não é um defeito de código — é o comportamento esperado desta base de dados de desenvolvimento específica, que nunca teve o script `117-add-tenant-plano-limite-utilizadores.sql` corrido manualmente contra ela. A reposição no ponto 9 usou os valores **realmente originais** (`NULL`/`NULL`), não os assumidos pelo plano, para não deixar a base de dados num estado diferente de como a encontrei.

## Achado independente, fora do âmbito desta fase: navegação "fria" fica presa num spinner infinito

Durante a preparação desta verificação, descobri e confirmei (múltiplas vezes, com reinícios limpos do servidor `pnpm dev`) que **navegar diretamente para qualquer rota autenticada** (URL colada na barra de endereços, recarregamento completo `Ctrl+Shift+R`, ou um link direto/favorito) **fica presa para sempre num spinner de carregamento** — o `<main>` nunca troca o fallback do `<Suspense>` (em `web/src/app/(dashboard)/layout.tsx`) pelo conteúdo real, mesmo com uma sessão válida e mesmo com o servidor a responder `200` no lado do servidor. Reproduzido em `/settings`, `/dashboard` e `/clientes` — não é específico desta fase nem desta feature. **Navegação por clique dentro da aplicação (client-side) funciona perfeitamente sempre** — foi assim que toda a verificação acima foi feita (nunca por `navigate()`/URL direto, sempre por clique real nos links da sidebar).

Isto é significativo porque o próprio plano desta fase (passo 6) pede um `Ctrl+Shift+R` para testar o estado "sem limite" — não foi possível fazer literalmente isso; usei em alternativa "navegar para fora e voltar por clique" (que força o mesmo remount + refetch da query `["auth","me"]`, já que `staleTime` é 60s e o tempo decorrido excedeu isso). O resultado observado (ponto 6) é o mesmo que um hard-reload teria mostrado, apenas alcançado por um caminho que não está bloqueado.

Não corrigi este bug — é claramente pré-existente (afeta `(dashboard)/layout.tsx`, não qualquer ficheiro tocado pela Phase 118) e fora do âmbito de PLAN-03. Foi sinalizado como tarefa de acompanhamento separada (task_08e7aed2) para investigação e correção dedicadas, dado tratar-se de um bug severo e de fácil reprodução (qualquer utilizador que recarregue a página ou abra um link direto para a app ficaria preso indefinidamente).

## Resumo

Os 9 pontos exigidos por este plano têm veredito `CONFIRMADO`. Nenhum `FALHOU`. As Success Criteria 2 e 3 da Phase 118 deixam de ser afirmações de renderização apenas verificadas estaticamente — foram vistas a funcionar ao vivo. O tooltip sobre o botão desativado dispara por rato e por teclado, fechando efetivamente a dívida da Phase 102. A base de dados de desenvolvimento foi reposta ao estado original.
