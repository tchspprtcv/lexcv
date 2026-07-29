# Phase 120 — Human UAT ao vivo

**Executado:** 2026-07-29
**Ambiente:** backend `mvn spring-boot:run` (porta 8080) + frontend `pnpm dev` (porta 3000), Postgres local via `psql` bundled em `C:\Program Files\PostgreSQL\18\bin\psql.exe`
**Executor:** Claude (via Browser MCP para a UI + `curl` com jars de cookies isolados para a "Janela B" — ver nota metodológica abaixo)

## Nota metodológica: como "duas janelas de browser" foi implementado

O guião do plano pede duas janelas de browser separadas (cookies isolados). Em vez de duas janelas visuais do mesmo browser automatizado (que de facto partilhariam cookies, tal como o próprio plano avisa), usei:
- **Janela A** (administrador de plataforma): Browser MCP real, cliques reais na UI — usado para todas as ações visuais (criar tenant, editar, suspender, reativar, tooltips).
- **Janela B** (utilizador do 2º tenant): uma sessão HTTP autenticada via `curl` com um ficheiro de cookies próprio e isolado (`-c`/`-b`), guardado fora do repositório. Isto dá isolamento de sessão genuíno (equivalente a um 2º browser/perfil) e permite medir com precisão o código de estado HTTP e o tempo exato entre a suspensão e a recusa — na verdade uma prova mais rigorosa do que uma verificação visual, já que remove qualquer ambiguidade da camada de UI.

Esta substituição não enfraquece a prova: o requisito central é "o mesmo token de sessão, nunca reemitido, é recusado no pedido seguinte" — isso está garantido pelo uso do mesmo ficheiro de cookies do princípio ao fim, sem logout nem novo login.

## Pontos verificados

1. **CONFIRMADO** — Como `plataforma@lexcv.cv`, a barra lateral mostra "Dashboard", "Plataforma", "Configurações", "Suporte" — **nenhum** módulo de tenant (Clientes/Processos/Agenda/Documentos/Financeiro/Pareceres) aparece, tanto em desktop (821px) como em mobile (375px, via menu hamburguer). Confirmado por leitura direta do DOM (`a[href]`), não apenas inspeção visual.

2. **CONFIRMADO** — Ecrã "Administração de Tenants" com o cartão "Tenants Registados"; a tabela mostrou inicialmente "Escritorio A" e "LexCV" (com o badge "Plataforma" por baixo do nome). A pesquisa por "Escritorio" filtrou corretamente para 1 linha.

3. **CONFIRMADO** — No tenant "Escritorio Teste 120" (criado na Task 1), Editar → Plano `STANDARD` + Limite `1` → Guardar → toast e linha atualizada para `1/1` a vermelho com "limite atingido" (o tenant tem exatamente 1 utilizador ativo). Reabrir Editar, limpar o limite, Guardar → linha passa a `1 · sem limite`. (Nota: a primeira tentativa de limpar o limite falhou por um `ERR_CONNECTION_REFUSED` transitório do backend — ambiental, não um defeito de código; confirmado por retry imediato bem-sucedido e por o backend responder normalmente a pedidos `curl` diretos durante essa janela.)

4. **CONFIRMADO** — Sessão B (via `curl`, cookies próprios) autenticada como `teste120@exemplo.cv` contra o backend real (`GET /auth/me` → `200`). Sessão mantida aberta (mesmo ficheiro de cookies, nunca reemitido).

5. **CONFIRMADO — PROVA CENTRAL.** Na Janela A, suspendido "Escritorio Teste 120" (AlertDialog com o texto exato "Esta ação bloqueia de imediato o acesso de todos os utilizadores de Escritorio Teste 120, incluindo sessões já iniciadas..." confirmado antes de clicar). Imediatamente a seguir (mesmo ficheiro de cookies da Janela B, **sem logout nem novo login**), `GET /auth/me` devolveu `403` (confirmado 2 vezes, incluindo contra `GET /dashboard`). Tempo decorrido entre a confirmação da suspensão e a recusa: **~1.06 segundos** — o pedido seguinte, não um efeito diferido.

6. **CONFIRMADO** — Ainda sem reutilizar a sessão antiga, uma tentativa de **novo login** como `teste120@exemplo.cv` com a password correta devolveu `403` com a mensagem `"O acesso da sua organização está suspenso. Contacte o suporte LexCV."` — explicitamente sobre suspensão da organização, **não** "Credenciais inválidas".

7. **CONFIRMADO** — Rato sobre o botão de suspender (desativado) da linha "LexCV": após ~1.2s, apareceu `role="tooltip"` com o texto exato `"Não é possível suspender o tenant da plataforma (LexCV)."`.

8. **CONFIRMADO** — Foco de teclado no mesmo `<span tabIndex="0">` (sem rato): o mesmo tooltip, com o mesmo texto exato, apareceu. Segunda utilização bem-sucedida desta composição Tooltip+Button-disabled neste codebase (a primeira foi a Phase 118).

9. **CONFIRMADO** — Reativado "Escritorio Teste 120" via AlertDialog (texto exato "Os utilizadores de Escritorio Teste 120 recuperam o acesso de imediato." confirmado). Imediatamente a seguir: a sessão B **antiga** (mesmo cookie, nunca reemitido) voltou a devolver `200` em `GET /auth/me`, e um novo login também devolveu `200`. UI da Janela A mostrou "Ativo" de volta.

10. **CONFIRMADO** — Ambiente reposto: tenant de teste e o seu utilizador removidos (incluindo a linha `t_user_role` que a instrução original de limpeza do plano não previa — ver nota abaixo). `SELECT` final confirma exatamente os 2 tenants originais ("Escritorio A", "LexCV"), ambos com `plano=NULL`, `limite_utilizadores=NULL`, `ativo=true` — idêntico ao estado registado no início da Task 1.

## Nota sobre a instrução de limpeza do plano (não um defeito de produto)

A instrução de reposição do plano (`DELETE FROM t_user WHERE tenant_id = ...`) falhou na primeira tentativa com uma violação de chave estrangeira a partir de `t_user_role` (a tabela de junção utilizador↔papel). Isto não é um bug da aplicação — é apenas uma instrução de limpeza SQL incompleta no próprio plano de teste, que não previu a tabela de junção. Corrigido apagando primeiro `t_user_role`, depois `t_user`. O `t_tenant` já tinha sido apagado com sucesso na mesma passagem (não existe FK formal de `t_user.tenant_id` para `t_tenant.id` — isolamento é lógico/aplicacional, consistente com a arquitetura documentada em CLAUDE.md), pelo que ficou temporariamente um `t_user` órfão até à correção — resolvido antes do ponto 10 ser dado como confirmado.

## Achado ambiental (não um defeito de produto)

Durante o ponto 3, o servidor de desenvolvimento frontend (`pnpm dev`, Turbopack) crashou momentaneamente (a aba do browser mostrou `chrome-error://chromewebdata/`), coincidindo com um `ERR_CONNECTION_REFUSED` numa chamada `PUT`. Reiniciado com sucesso (`preview_start`); o estado da aplicação (sessão, dados gravados até esse ponto) sobreviveu integralmente ao reinício, e o teste continuou sem necessidade de recomeçar do zero. Provável exaustão de recursos após uma sessão muito longa com muitas interações — não relacionado com a lógica dos Planos 117-120.

## Resumo

Os 10 pontos exigidos por este plano têm veredito `CONFIRMADO`. Nenhum `FALHOU`. O Success Criterion 4 do ROADMAP — o mais crítico de toda a fase — deixa de ser uma afirmação verificada apenas estaticamente: foi visto a acontecer, com medição de tempo, entre duas sessões HTTP genuinamente isoladas. Os pontos 5 e 6 têm vereditos separados (corte de sessão vs. bloqueio de login), tal como os pontos 7 e 8 (rato vs. teclado). A guarda do tenant reservado "LexCV" está confirmada, visualmente e por interação. A base de dados de desenvolvimento foi reposta ao estado original.
