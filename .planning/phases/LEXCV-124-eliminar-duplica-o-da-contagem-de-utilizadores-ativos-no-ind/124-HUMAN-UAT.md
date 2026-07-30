# Phase 124 — Human UAT ao vivo

**Executado:** 2026-07-30
**Ambiente:** backend `mvn spring-boot:run` (porta 8080) + frontend `pnpm dev` (porta 3000), Postgres local via `psql` bundled em `C:\Program Files\PostgreSQL\18\bin\psql.exe`
**Executor:** Claude (via Browser MCP — navegação real, cliques reais, JavaScript apenas para leitura/inspeção, nunca para acionar mutações)

## Contexto

`124-VERIFICATION.md` (gsd-verifier) reportou `human_needed` com um único item pendente: a confirmação visual ao vivo dos 3 estados do indicador "X/Y utilizadores" + reatividade, deliberadamente deferida no `<human-check>` de `124-02-PLAN.md` para este UAT de fim de fase. Toda a restante prova (estrutural, testes, gates) já estava `VERIFIED`.

## Bloqueio inicial de ferramenta (resolvido)

A primeira tentativa de navegação (`tabId` reutilizado de uma tab antiga) falhou com o mesmo erro já documentado na Fase 122 — **"the Browser pane is currently hidden/not displayed"** — em `get_page_text`, `read_page` e `resize_window`. Diferente da Fase 122, desta vez a recuperação foi bem-sucedida: abrir uma **tab nova** (`tabs_create`) e navegar aí resolveu o bloqueio de imediato. Não foi necessário aceitar a evidência estrutural por si só — a confirmação ao vivo foi concluída com sucesso.

## Pontos verificados

1. **CONFIRMADO — Estado 1 (sem limite).** Com `t_tenant.limite_utilizadores = NULL` (estado real de "Escritorio A" no início desta sessão), autenticado como `admin@lexcv.cv`, em Definições → "Gestão de Utilizadores": o indicador mostra **"5 utilizadores"**, sem barra, e o botão "Novo Utilizador" está visualmente ativo (confirmado por screenshot).

2. **CONFIRMADO — Estado 2 (dentro do limite).** Com `limite_utilizadores` ajustado para `7` via `psql` (acima da contagem real de 5 utilizadores ativos) e a página recarregada por navegação em clique (Dashboard → Configurações → Gestão de Utilizadores, para forçar o refetch de `["auth","me"]` sem hard-reload): o indicador mostra **"5/7 utilizadores"**, cinzento, botão ativo (confirmado por screenshot).

3. **CONFIRMADO — Estado 3 (no limite), incluindo tooltip.** Com `limite_utilizadores` ajustado para `5` (igual à contagem real) e a mesma navegação de refetch: o indicador mostra **"5/5 utilizadores · limite atingido"**. Confirmado por inspeção directa do DOM (não apenas visual): `label.className === "text-xs font-semibold text-red-600 dark:text-red-400"` e `button.disabled === true` para "Novo Utilizador". O tooltip, disparado por `hover` real sobre o botão desativado, tem `role="tooltip"` com o texto exacto **"Limite de utilizadores atingido. Desative um utilizador para libertar uma vaga."**

4. **CONFIRMADO — Reatividade.** Editado o utilizador "Teste Advogado" (Editar → desligar o interruptor "Status de Utilizador", agora "DESATIVADO" → "Guardar Utilizador"), com cliques reais via `computer` (não `javascript_tool`, ver nota abaixo). Voltando ao separador "Gestão de Utilizadores" (sem novo login, sem hard-reload — apenas navegação em clique dentro da mesma sessão): o utilizador aparece "Desativado" na tabela, e o indicador desceu de **"5/5 utilizadores · limite atingido"** para **"4/5 utilizadores"** (sem "· limite atingido", cinzento) — confirma que o contador reflecte `tenant_utilizadores_ativos` vindo do backend, não um valor congelado no cliente.

## Nota sobre uma tentativa falhada (não um defeito de produto)

A primeira tentativa do ponto 4 usou `javascript_tool` para invocar `.click()` diretamente no botão "Guardar Utilizador" via DOM — isto **não** acionou corretamente o gestor de eventos do React (muito provavelmente disparou uma submissão nativa de formulário em vez do `onClick` sintético), e a alteração não foi guardada (confirmado: o utilizador continuava "Ativo" e o contador continuava "5/5" depois). Corrigido repetindo a interação com a ferramenta `computer` (clique real, sintético, tal como um utilizador humano faria) — desta vez a alteração foi guardada corretamente. Isto está consistente com a proibição do próprio `javascript_tool` ("para depuração, nunca para acionar mutações de UI") e não revela nenhum problema no código da aplicação.

## Observação de UX pré-existente, não relacionada com esta fase

Depois de gravar uma edição de utilizador (ambas as tentativas, a falhada e a bem-sucedida), o separador ativo de Definições volta a "O Meu Perfil" em vez de permanecer em "Gestão de Utilizadores". Isto é um comportamento pré-existente do fluxo de gravação do diálogo de edição (não tocado por esta fase — `git diff` confirma zero alterações a qualquer lógica de gestão de separadores), não uma regressão desta fase. Não impede a verificação: bastou clicar novamente em "Gestão de Utilizadores" para confirmar o contador atualizado.

## Reposição do ambiente

`limite_utilizadores` reposto a `NULL` e `teste.advogado@lexcv.cv` reativado via `psql`, ambos confirmados por `SELECT` final: `Escritorio A` e `LexCV` exactamente como estavam no início desta sessão (`plano=STARTER`, `limite_utilizadores=NULL`, 5 e 1 utilizadores ativos respectivamente).

## Resumo

Os 4 pontos exigidos pelo `<human-check>` de `124-02-PLAN.md` têm veredito `CONFIRMADO`. Nenhum `FALHOU`. O Critério de Sucesso 3 do ROADMAP da Fase 124 deixa de depender apenas de prova estrutural — foi visto a acontecer, incluindo o estado mais crítico visualmente (vermelho, botão desativado, tooltip) e a reatividade ao desativar um utilizador. A base de dados de desenvolvimento foi reposta ao estado original.

---
*Phase: 124-eliminar-duplica-o-da-contagem-de-utilizadores-ativos-no-ind*
*Completed: 2026-07-30*
