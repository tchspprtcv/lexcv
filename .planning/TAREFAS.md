# Tarefas — ALCv
**Atualizado:** 2026-08-22 · **Próximo passo:** T-028 — commitar a alteração ao `DatabaseSeeder`, de que dependem três promessas já escritas no dossiê

Lista mantida pelo `lexcv-coordenador`. Ver `.claude/agents/lexcv-coordenador.md`.
Semeada a partir de verificação direta do repositório — nenhuma tarefa aqui é
suposição.

## Em curso
| ID | Tarefa | Dono | Estado | Depende | Nota | Ronda |
|---|---|---|---|---|---|---|
| T-022 | Guarda auto-executável no `125` + prova em 3 cenários | `lexcv-programador` | EM CURSO | — | — | 1 |
| T-027 | Parametrizar `docker-compose.hostinger.yml` sem mudar o comportamento | `lexcv-programador` | EM CURSO | — | — | 1 |
| T-010 | Corrigir `CLAUDE.md` e `DEPLOYMENT.md` — raiz de erros propagados | `lexcv-programador` | EM CURSO | — | — | 1 |
| T-025 | Mapa de portos expostos por caminho de instalação (investigação) | `lexcv-programador` | EM CURSO | — | — | 1 |

## Bloqueadas — à espera de ti
| ID | Tarefa | Pergunta que precisa de resposta |
|---|---|---|
| T-002 | Reconciliar as 3 cópias da especificação do módulo de parecer | Qual das três é a canónica? |
| T-006 | Commitar a reorganização de `business/` | Commito? Há um rename staged desde 2026-08-21 |
| T-025 | O `postgres` e o `minio` ficam publicados no anfitrião pelo caminho de instalação que o `DEPLOYMENT.md` manda usar? | O `DEPLOYMENT.md` manda `-f docker-compose.yml -f docker-compose.prod.yml`, mas o `prod.yml` só faz `ports: !reset []` ao **backend** — o `postgres` (`5433:5432`) e o `minio` (`9000`, `9001`) do ficheiro base continuam publicados. Se for esse o caminho real, a consola do MinIO é alcançável pela 9001 direta, contornando a autenticação básica do Caddy. Qual dos dois ficheiros é o autoritativo para produção? |
| T-021 | **O perfil `prod` nunca é ativado — `application-prod.yml` é ficheiro morto** | Achado por dois agentes em separado e confirmado por mim: `grep "SPRING_PROFILES_ACTIVE\|profiles" docker-compose*.yml backend/Dockerfile` = **zero**. Não é só o `ddl-auto`: `server.error.include-message: never` não está ativo (a produção devolve mensagens de exceção nas respostas de erro) e `forward-headers-strategy: framework` também não — e o comentário do próprio `application-prod.yml` diz que o bloqueio de login em `AuthController.login` depende dele para obter o IP real através do Caddy. Sem perfil, `getRemoteAddr()` devolve o IP do contentor Caddy para toda a gente e o bloqueio por tentativas colapsa num balde global. Ativo o perfil? |
| T-022 | A `125` destrói mesmo — **provado por execução**. Ponho guarda no SQL? | Já não é raciocínio: corrido verbatim contra PostgreSQL 18.4 numa base descartável, apagou **34.432 caracteres** de logótipo e saiu com **código 0**. Erro real: `function lo_get(text) does not exist`, engolido pelo `EXCEPTION WHEN OTHERS`. A defesa é hoje 100% documental — depende de o operador ler o aviso. Um `IF (SELECT data_type ...) <> 'oid' THEN RAISE EXCEPTION` no topo tornava-a auto-executável. É alteração de lógica SQL e não a faço sem a tua palavra |
| T-027 | `docker-compose.hostinger.yml` fica preso no estágio 1 da instalação | As linhas 62-63 fixam `SEED_ENABLED: true` e `ddl-auto: update` como **literais**, logo essa instalação nunca chega a `validate` e volta a semear a cada arranque. O `docker-compose.yml` já foi parametrizado (`${...:-update}`), o do Hostinger não. Parametrizo-o também? |
| T-028 | A alteração ao `DatabaseSeeder` tem de ser commitada e entregue | Três passagens do dossiê prometem que a eliminação "fica operacional com a próxima atualização". Se a linha do `permKeys` não for commitada e entregue, os documentos passam a prometer algo que não acontece. É a única dependência viva das redações |
| T-023 | A base Hostinger é nova ou herdada? Uma query responde | Decide se a passagem para `validate` arranca. `SELECT format_type(atttypid, atttypmod) FROM pg_attribute WHERE attrelid = 't_tenant'::regclass AND attname = 'logo_data_url';` → `text` arranca; `oid` exige correr a `125` primeiro. Na mesma passagem: `SELECT extname FROM pg_extension;` — se o volume foi recriado, as extensões de pesquisa desapareceram e a Pesquisa Global rebenta em execução, sem sinal no arranque |
| T-024 | Eliminar honorário ou pagamento não deixa rasto auditável | Nenhuma das duas eliminações escreve em auditoria, e a §4.5 do PRD só lista processo e documentos. Um ADMIN elimina um valor financeiro sem rasto. Um contratante institucional tende a questionar isto |
| T-015 | Acrescentar `financeiro:manage` ao catálogo do ecrã de Controlo de Acesso | O catálogo de `AdminController.java:374-391` mostra 17 permissões contra as 20 que existem. Acrescento a `financeiro:manage`? Torna-a visível e comutável para ADVOGADO/TECNICO por um PLATAFORMA_ADMIN — é exposição de superfície de autorização, não a decido sozinho. Ligada a T-011 |

## Backlog
| ID | Tarefa | Dono previsto | Evidência |
|---|---|---|---|
| T-001 | Manual não cobre Pareceres, Notificações nem a consola Plataforma | `lexcv-redator` | `grep -ci` no manual: `parecer`=0, `notificac`=0, `plataforma`=1 — mas os 3 ecrãs existem em `web/src/app/(dashboard)/` e os marcos v2.5, v2.6, v2.10, v2.11 e v2.16 estão em `MILESTONES.md` |
| T-004 | `plano-financeiro.docx` não tem `.md` fonte | `lexcv-redator` | `business/propostas/` — viola a regra da fonte única do `business/README.md` |
| T-005 | `ficha-cliente.docx` e `termo-honorarios.docx` não têm `.md` fonte | `lexcv-redator` | `business/contratos/` — mesma violação; são minutas, exigem nota de revisão por advogado |
| T-012 | Cookies de sessão: `Secure` por definir e duração fixada no código | ciclo GSD | `AuthController.java:47` → `.secure(false) // Set to true in prod (HTTPS)`. E `createCookie(..., 86400)` / `(..., 2592000)` nas 4 invocações — 24h e 30d literais, que não acompanham `JWT_ACCESS_EXPIRATION_MS`/`JWT_REFRESH_EXPIRATION_MS` |

## Concluídas
| ID | Tarefa | Dono | Nota | Fechada em |
|---|---|---|---|---|
| T-007 | Documentos de conceção (Termo de Abertura, PRD, Arquitetura) | `lexcv-redator` | 7.9 | 2026-08-21 |
| T-011 | Semear `financeiro:manage` e atribuí-la só ao ADMIN | `lexcv-programador` | 4.0 | 2026-08-21 |
| T-013 | Adotar as designações das versões como oficiais | `lexcv-redator` | 8.2 | 2026-08-21 |
| T-014 | Dois caminhos de instalação, cada um com a sua lista de migrações | `lexcv-redator` | 8.0 | 2026-08-21 |
| T-019 | Contradição arquitetura↔roteiro sobre migrações; 2 `.docx` regerados | `lexcv-redator` | — | 2026-08-21 |
| T-016 | Arranque em dois tempos no `DEPLOYMENT.md` + iniciativa no roteiro | `lexcv-programador` + `lexcv-redator` | 7.6 | 2026-08-22 |
| T-017 | PRD §3.6 descreve a eliminação de honorários e pagamentos | `lexcv-redator` | 4.0 → corrigido | 2026-08-22 |
| T-018 | `backend/migrations/README.md` + aviso no `125` + roteiro encurtado | `lexcv-programador` + `lexcv-redator` | 7.6 | 2026-08-22 |
| T-020 | Conversor único versionado (A4), 6 `.docx` deterministas | `lexcv-programador` | 7.6 | 2026-08-22 |
| T-026 | `backend/.env` — verificação de exposição de credencial | `lexcv-coordenador` | — | 2026-08-22 |
| T-008 | Documentos de evolução (Changelog, Release Notes) | `lexcv-redator` | 7.9 | 2026-08-21 |
| T-009 | Roadmap Tecnológico + registo de `documentacao/` no `business/README.md` | `lexcv-redator` | 7.9 | 2026-08-21 |

---
## Detalhe

### T-001 — Pôr o manual do utilizador a par dos marcos entregues
**Dono:** `lexcv-redator` · **Estado:** BACKLOG · **Depende:** —
**Entrega esperada:** `.docs/manual_utilizador_lexcv.md` atualizado, com as secções
em falta escritas no mesmo formato "Guia N: …" das existentes.
**Evidência de que é preciso:** o manual tem 5 guias e para na Ficha de Cliente
(~v2.4). Desde então entraram Parecer Jurídico (v2.5, v2.6), Notificações (v2.10,
v2.11) e a consola de Plataforma (v2.16), todas com ecrã próprio em
`web/src/app/(dashboard)/`. Nenhuma está no manual.
**Fora de âmbito:** não reescrever os 5 guias existentes.
**Histórico:**
- 2026-08-21 criada

### T-002 — Reconciliar a especificação do módulo de parecer
**Dono:** — · **Estado:** BLOQUEADA
**Entrega esperada:** um `.md` canónico em `business/especificacoes/`, as outras
versões marcadas como históricas.
**Evidência:** três ficheiros do mesmo documento, nenhum com `.md` fonte —
`especificacao-parecer-juridico.docx`, `.pdf`, e
`especificacao-parecer-juridico-2026-06-30.pdf`.
**Pergunta ao humano:** qual é a canónica? Sem isso, extrair o Markdown da errada
propaga o erro em vez de o corrigir.
**Histórico:**
- 2026-08-21 criada, bloqueada à nascença

### T-003 — Resolver as duas apresentações
**Dono:** — · **Estado:** CONCLUÍDA
**Evidência:** `business/apresentacoes/apresentacao-alcv.pptx` (versionado no git)
e `apresentacao-lexcv.pptx` (não versionado). Conteúdos não comparados.
**Pergunta ao humano:** "ALCV" é um cliente, uma marca anterior, ou lixo?
**Histórico:**
- 2026-08-21 criada, bloqueada à nascença
- 2026-09-24 resolvida durante o marco v2.18 (Rebrand ALCv → LexCV): conteúdo dos dois ficheiros
  comparado diretamente — `apresentacao-alcv.pptx` (21 slides, "ALCV" em todo o lado) e
  `apresentacao-lexcv.pptx` (20 slides, zero menções a "ALCv", mesmo conteúdo já rebrandado).
  Resposta: "ALCV" é a marca anterior deste produto, não um cliente nem lixo — confirmado pelo
  próprio texto do deck ("ALCV é uma plataforma web multi-tenant para escritórios de advocacia em
  Cabo Verde..."). `apresentacao-lexcv.pptx` já era a substituta completa. `apresentacao-alcv.pptx`
  removida (`git rm`) por decisão explícita do utilizador; recuperável no histórico git.

### T-004 — Extrair `.md` fonte do plano financeiro
**Dono:** `lexcv-redator` · **Estado:** BACKLOG
**Entrega esperada:** `business/propostas/plano-financeiro.md`, e o `.docx`
passa a derivado.
**Evidência:** `business/README.md` exige `.md` como fonte; este não tem.
**Nota de risco:** contém números financeiros. O `lexcv-revisor-qualidade` vai
exigir fonte para cada um — se não existir, sobem para a secção "Assumido".
**Histórico:**
- 2026-08-21 criada

### T-005 — Extrair `.md` fonte das minutas
**Dono:** `lexcv-redator` · **Estado:** BACKLOG
**Entrega esperada:** `ficha-cliente.md` e `termo-honorarios.md` em
`business/contratos/`, ambos com a nota de revisão por advogado no fim.
**Evidência:** mesma violação da regra da fonte única.
**Fora de âmbito:** não alterar substância jurídica ao extrair. Extração fiel.
**Histórico:**
- 2026-08-21 criada

### T-007 — Documentos de conceção do ALCv
**Dono:** `lexcv-redator` · **Estado:** ATRIBUÍDA · **Depende:** —
**Entrega esperada:** três `.md` + três `.docx` em `business/documentacao/`:
`termo-de-abertura.md`, `requisitos-produto.md`, `especificacao-arquitetura.md`.
**Evidência de que é preciso:** pedido direto do dono do projeto (2026-08-21).
Não existe hoje nenhum documento de apresentação do projeto para cliente
contratante — `business/` só tem propostas, minutas e especificações de um módulo.
**Fora de âmbito:** changelog, release notes e roadmap (T-008, T-009).
**Histórico:**
- 2026-08-21 criada e despachada
- 2026-08-21 entregue: 3 `.md` + 3 `.docx`. O redator recusou duas afirmações
  por não passarem verificação — retirou "exportação" de Processos (o botão
  existe mas está `disabled`) e recusou descrever instalação automática no
  servidor (`deploy.yml` não tem job de deploy por SSH). Levantou 3 achados
  fora de âmbito: cookie de sessão com `.secure(false)`, `DEPLOYMENT.md`
  desatualizado (diz SSH e ramo `main`; é `master` e não há), e `CLAUDE.md`
  desatualizado (diz `uploads/`; o backend usa só MinIO).
- 2026-08-21 edição de forma: papéis técnicos associados aos nomes de negócio,
  interpretações do redator marcadas como interpretações
- 2026-08-21 revisão: 1 BLOQUEANTE, 2 graves, 4 menores. **A arquitetura ganhou
  a disputa contra o `CLAUDE.md`** sobre o armazenamento — é o `CLAUDE.md` que
  está errado (ver T-010). BLOQUEANTE: a nota "o ADMIN detém todas as permissões
  de âmbito" é falsa — `financeiro:manage` é exigida por duas guardas e nenhum
  papel a detém. A matriz estava certa; era a nota que mentia
- 2026-08-21 ronda 2: 7/7 achados aplicados, `.docx` regerados. O redator
  encontrou ainda a divergência 17-vs-19 do catálogo RBAC (ver T-011)
- 2026-08-21 correção pontual dirigida (não é 3.ª ronda): `:70` atribuía as 19
  permissões à matriz **da aplicação**, mas o ecrã só apresenta 17. Achado do
  `lexcv-avaliador`; texto de substituição já apurado por ele
- 2026-08-21 **CONCLUÍDA** — 3 frases corrigidas, todas a atribuir ao sistema o
  que era do ecrã. Redigidas para se manterem verdadeiras antes e depois de a
  T-011 fechar. `requisitos-produto.docx` regerado; os outros cinco intactos

### T-008 — Documentos de evolução do ALCv
**Dono:** `lexcv-redator` · **Estado:** ATRIBUÍDA · **Depende:** —
**Entrega esperada:** `registo-de-alteracoes.md` (changelog técnico por versão) e
`relatorio-de-versao.md` (release notes orientadas a benefício), mais os `.docx`.
**Evidência:** `MILESTONES.md` tem 20 marcos com data de shipping; 19 tags git
corroboram as datas. Nada disso está legível para um cliente.
**Risco conhecido:** `git log` neste ambiente é filtrado pelo hook rtk e devolve
~50 commits em vez de 2070. Datar por `git tag` / `git rev-list`, nunca por `git log`.
**Histórico:**
- 2026-08-21 criada e despachada
- 2026-08-21 entregue: 2 `.md` + 2 `.docx`, 27 versões cobertas. Datou v1.3/v1.4
  pelos ficheiros de arquivo, v2.0–v2.2 pelo `ROADMAP.md`, e registou v1.5/v1.6
  como "sem entrega registada" em vez de inventar. Retirou uma afirmação sua
  sobre RLS ao nível da base de dados depois de `grep` não devolver nada.
  **Por decidir pelo dono:** as designações "amigáveis" das versões são invenção
  do redator; e a exposição a cliente de correções de segurança (v2.4, v2.5, v2.13).
- 2026-08-21 edição de forma: 25 títulos de versão harmonizados entre os dois
  documentos, aviso das designações promovido ao topo de ambos
- 2026-08-21 revisão: 1 BLOQUEANTE, 3 graves, 6 médios, 2 baixos. Achado mais
  grave: a v2.16 omitia a única limitação de fecho com consequência funcional
  (3 migrações manuais; sem a `120b`, a primeira suspensão de um escritório
  pré-existente devolve 500 em produção) e dava como "Corrigida" uma coisa
  corrigida só em desenvolvimento
- 2026-08-21 ronda 2: 12/12 achados aplicados, `.docx` regerados
- 2026-08-21 **CONCLUÍDA** — `lexcv-avaliador` 7.9, sem BLOQUEANTE pendente

### T-009 — Roadmap Tecnológico
**Dono:** `lexcv-redator` · **Estado:** ATRIBUÍDA · **Depende:** —
**Entrega esperada:** `roadmap-tecnologico.md` + `.docx`, conteúdo em TABELA, e
uma linha nova para `documentacao/` na tabela de estrutura do `business/README.md`.
**Evidência:** `.planning/ROADMAP.md` está 100% concluído (v2.16, 8/8 fases) — não
há roadmap futuro escrito em lado nenhum. O trabalho por fazer só existe disperso
em `STATE.md` (Pending Todos, Blockers) e `RETROSPECTIVE.md`.
**Nota de risco:** tudo o que for futuro é estimativa. Tem de vir marcado como tal.
**Histórico:**
- 2026-08-21 criada e despachada
- 2026-08-21 entregue: `.md` + `.docx` (7 tabelas, Anexo A de rastreabilidade) e
  a linha nova no `business/README.md`. **Corrigiu uma premissa errada minha:**
  o bug de fuso horário na Agenda que eu dei como aberto já está fechado em
  `master` — confirmou-o em `use-eventos.ts`, `agenda/page.tsx`, `agenda/novo`
  e `agenda/[id]/editar`. Reconfirmou no código os restantes itens abertos.
  Achado novo: `backend/migrations/` tem 13 scripts e nenhum executor, com
  `ddl-auto: validate` em produção.
- 2026-08-21 edição de forma: aviso de estimativas promovido a caixa antes de
  qualquer conteúdo, prefixo "Estimativa:" em cada célula, jargão empurrado para
  o Anexo A
- 2026-08-21 revisão: 2 BLOQUEANTES, ambos números errados. "3 migrações
  obrigatórias" subcontava (falta a `125`, que corrige uma falha de produção e
  não tem rasto no `STATE.md`); "cinco ciclos consecutivos" era falso — os
  ciclos citados têm quatro interrupções
- 2026-08-21 ronda 2: o redator foi além do pedido — apurou **5** migrações
  obrigatórias e não 4, **seis** ciclos e não cinco, e encontrou um terceiro
  erro numérico que nenhum revisor apanhou (a decisão de produto está pendente
  desde a **v2.10**, não v2.11 — o texto encurtava a pendência um ciclo inteiro,
  a favor do fornecedor). `.docx` regerado
- 2026-08-21 **CONCLUÍDA** — `lexcv-avaliador` 7.9, sem BLOQUEANTE pendente

### T-016 — Procedimento de instalação: arranque em dois tempos
**Dono:** `lexcv-programador` (DEPLOYMENT.md) + `lexcv-redator` (roteiro) · **Estado:** EM CURSO
**Decisão (2026-08-22):** o dono respondeu "não sei" e **delegou-ma — a decisão é
minha, não dele.** Procedimento sancionado: 1º arranque com `update`+`SEED_ENABLED=true`
para criar esquema e povoar; depois `validate`+`SEED_ENABLED=false` em regime
permanente, e **confirma-se que arranca em `validate` antes de dar a instalação por
concluída**. Formaliza o que já se faz e acrescenta o passo 2, que é o que falta.
**Evidência:** `application.yml:19` = `update`; `application-prod.yml:21` = `validate`;
`docker-compose.hostinger.yml:63` sobrepõe `update` **em produção**, anulando o perfil.
**Achado por responder:** flipar uma instalação Hostinger já a correr para `validate`
é seguro? Se não for, há deriva entre entidades e esquema — problema maior que a documentação.
**Histórico:**
- 2026-08-22 decidida por mim, por delegação, e despachada

### T-017 — PRD descreve a eliminação de honorários e pagamentos
**Dono:** `lexcv-redator` · **Estado:** EM CURSO · **Depende:** T-011
**Decisão do dono (2026-08-22):** descrever.
**Evidência:** deixou de ser inalcançável com a T-011. `ResourceController.java:3055`
recusa com 409 eliminar honorário com pagamentos registados; `:3074` não tem
salvaguarda equivalente, reverte o valor na conta corrente e **elimina à mesma se
a reversão do saldo falhar**, com o utilizador não informado.
**Histórico:**
- 2026-08-22 criada por decisão do dono e despachada

### T-018 — A lista de migrações passa para o repositório
**Dono:** `lexcv-programador` (README + `125`) + `lexcv-redator` (encurtar o roteiro) · **Estado:** EM CURSO
**Decisão (2026-08-22):** **minha, por delegação do dono.** A salvaguarda sai do
documento de cliente mas **não** desaparece de onde o operador trabalha.
**Princípio que passa a valer:** a contagem de migrações vive num sítio só, e esse
sítio é o repositório. Documentos de cliente remetem, não duplicam.
**Histórico:**
- 2026-08-22 decidida por mim, por delegação, e despachada

### Nota — as quatro decisões desta vaga são minhas
T-022, T-027, T-010 e T-025 foram decididas **por mim (coordenador)**, ao abrigo do
"continuar" do dono. **Não são decisões dele** e não devem ser lidas como tal. O
"continuar" foi autorização para avançar no que não depende das perguntas em aberto,
não resposta a nenhuma delas. Mantidas bloqueadas por serem do dono: T-021 (ativar o
perfil `prod`), T-023 (queries contra produção), T-015, T-024, T-002, T-003. Mantidas
em aberto por envolverem commits, que o dono não pediu: T-028, T-006.

### T-020 — Conversor único e versionado
**Dono:** `lexcv-programador` · **Estado:** BACKLOG · **Depende:** T-016, T-017, T-018
**Decisão (2026-08-22):** **minha, por delegação do dono.** Um só script versionado,
que regenera os seis `.docx` numa passagem, com dependências declaradas, nomeado no
`business/README.md` como a única via sancionada.
**Evidência:** os 6 binários vêm de três famílias de estilo `python-docx` diferentes,
nenhuma versionada — vivem no scratchpad e morrem com a sessão.
**Histórico:**
- 2026-08-22 criada; espera que os `.md` estabilizem

### Nota de arbitragem — a eliminação de honorários (2026-08-22)
Três versões estiveram em cima da mesa e **nenhuma das duas primeiras estava certa**:
- **eu** disse ao redator que a capacidade "só se tornou alcançável nesta corrida e
  não está comprometida em git" — errado na letra;
- o **redator** escreveu que "foi construída depois da v2.16" — errado;
- o **revisor** provou que os pontos existem desde 2026-06-18 e estão na tag `v2.16`,
  e concluiu que "já está a correr nas instalações existentes" — **também errado**.

Arbitrei com prova própria: `git show v2.16:...DatabaseSeeder.java` mostra `permKeys`
com apenas `financeiro:view` e `financeiro:edit`, enquanto
`git show v2.16:...ResourceController.java` mostra os dois `DeleteMapping` a exigir
`financeiro:manage`. **A funcionalidade foi lançada na v2.16 mas era inalcançável por
todos, incluindo o ADMIN.** Fica operacional com a próxima atualização.
Lição: um revisor que contesta com prova pode estar a provar a coisa errada. A prova
dele (os endpoints existem) era verdadeira; a conclusão (logo funcionam) não seguia.

**Adenda (2026-08-22) — a minha arbitragem também estava errada, e o avaliador
apanhou-a.** Verifiquei depois: `git show v2.3:...DatabaseSeeder.java | grep -c
"financeiro:manage"` = **0**, com o `DeleteMapping` já guardado por ela. Igual na
v2.4. Ou seja, a capacidade **nasceu morta na v2.0/v2.3 e assim ficou todo o
histórico** — dizer "lançada na v2.16" era uma meia-verdade que apontava para o
sítio errado. Acertei no mecanismo e parei na etiqueta que confirmava a história
que eu já tinha na cabeça. A data que o revisor trazia (2026-06-18) era a mais
próxima da verdade das três, e eu descartei-a junto com a inferência errada dele.

**Pior:** a minha verificação de contaminação (`grep "eliminar um honorário\|
eliminação de honorários"`) deu um único ficheiro e eu tomei isso como aprovação.
Era o sintoma. As promessas estão redigidas de outra forma — `registo-de-alteracoes.md:701`
("ações de edição e eliminação diretamente na lista") e `relatorio-de-versao.md:613`
("Passou a filtrar a lista de honorários e a editar ou eliminar diretamente nela") —
e **vendem ao cliente, como benefício entregue em junho de 2026, um botão que nunca
funcionou**. Um `grep` estreito que devolve pouco não é prova de que não há nada.

### T-026 — `backend/.env` nunca foi exposto — CONCLUÍDA
**Dono:** `lexcv-coordenador` (verificação, não produção) · **Estado:** CONCLUÍDA
**Veredicto: limpo. Nenhuma credencial foi alguma vez commitada, nenhuma rotação
é precisa.** Três verificações independentes:
- `git check-ignore -v backend/.env` → `backend/.gitignore:4:*.env` (coberto)
- `git ls-files --error-unmatch backend/.env` → não trackeado
- `git rev-list --all --objects | grep '\.env'` → devolve **só** `.env.example`,
  `backend/.env.example`, `web/.env.example`, `webpage/.env.example`. Zero ficheiros
  `.env` reais em toda a história de 2070 commits.
O `.gitignore` da raiz cobre também `.env` e `.env.*` com exceção para `.env.example`.
**Histórico:**
- 2026-08-22 verificada e fechada na mesma corrida

### T-019 — Contradição entre a arquitetura e o roteiro sobre migrações
**Dono:** `lexcv-redator` · **Estado:** EM CURSO · **Depende:** T-014
**Entrega esperada:** §9.5 e §11.5 da `especificacao-arquitetura.md` alinhados com
os dois caminhos de instalação; `roadmap-tecnologico.docx` e
`especificacao-arquitetura.docx` regerados com o conversor `python-docx`.
**Evidência:** achado do `lexcv-avaliador` no portão final. **A contradição foi
criada por esta vaga.** A arquitetura diz ao responsável de sistemas do cliente
que "aplicá-las é condição para o funcionamento correto"; o roteiro diz que a
`125` numa base de raiz apaga logótipos em silêncio. É o mesmo leitor.
**Segundo problema:** o `roadmap-tecnologico.docx` foi regerado por edição
dirigida do OOXML, sob o pressuposto errado de que o original era `pandoc`. Os
seis binários têm `<dc:creator>python-docx</dc:creator>`. É o único dos seis com
defeitos de espaçamento — 3 linhas na caixa de aviso de abertura.
**Histórico:**
- 2026-08-21 criada a partir do portão final e despachada
- 2026-08-21 **CONCLUÍDA** — §9.5 deixou de afirmar o universal ("aplicá-las é
  condição para o funcionamento correto") e passa a remeter para o Anexo A.5
  (detalhe); §11.5 idem, nomeando o risco. Verifiquei: a frase universal saiu, e
  as remissões para A.5 (detalhe) e A.1 resolvem. Os 3 defeitos de espaçamento
  desapareceram e os 6 binários estão a zero — causa-raiz encontrada (o gerador
  removia `^>\s?`, um só espaço, e juntava continuações indentadas com `>   `).
  **Achado novo:** três famílias de conversor, nenhuma versionada (ver T-020).

### T-010 — Pôr `CLAUDE.md` e `DEPLOYMENT.md` a par do código
**Dono:** `lexcv-programador` · **Estado:** BACKLOG · **Depende:** —
**Entrega esperada:** as três afirmações falsas corrigidas, sem alterar mais nada.
**Evidência:** apurada pelo `lexcv-revisor-qualidade` durante o T-007. Ver a linha
no Backlog. Nota: foi o `CLAUDE.md` que perdeu a disputa contra a documentação
nova, não o contrário — o backend usa exclusivamente MinIO desde a v2.2.
**Histórico:**
- 2026-08-21 criada a partir de achado de revisão

### T-011 — `financeiro:manage` exigida mas sem titular
**Dono:** `lexcv-programador` · **Estado:** EM CURSO · **Depende:** —
**Decisão do dono (2026-08-21):** **semear** a permissão e atribuí-la só ao ADMIN.
O dono nomeou explicitamente o `lexcv-programador` — atribuição dele, não minha:
pelo meu contrato, RBAC e backend iriam ao ciclo GSD.
**Evidência:** ver linha no Backlog. **Não despachar ao `lexcv-programador`** —
RBAC e backend são território do GSD.
**Achado ligado, encontrado na ronda 2:** o catálogo RBAC de
`AdminController.java:375-391` declara **17** definições de permissão contra as
**19** de `permKeys` — faltam-lhe `processos:create` e `processos:manage`. Não
afeta a autorização (o catálogo é só de apresentação), mas o ecrã de Controlo de
Acesso mostra menos duas permissões do que existem.
**Consequência a jusante:** a escolha entre semear `financeiro:manage` ou baixar
as guardas para `financeiro:edit` **muda a matriz do PRD §2.3**. Decidir antes de
o dossiê sair para cliente, ou o documento fica desatualizado à nascença.
**Histórico:**
- 2026-08-21 criada a partir de achado de revisão
- 2026-08-21 código aplicado: `DatabaseSeeder.java:318` e `UserPrincipal.java:42`,
  19 → 20 chaves. O `UserPrincipal` não estava no meu enunciado — é uma segunda
  cópia da mesma lista, mantida em sincronia por um comentário no próprio
  ficheiro, e foi a dessincronização das duas que criou este defeito. `mvn test`
  sem regressões (192 testes; 1 erro ambiental, Docker em baixo).
  **Migração: NÃO é precisa** — `seedRbac()` corre na linha 45, antes do
  `if (!seedEnabled) return;` da linha 58, logo é semeado em todos os arranques
  mesmo com `SEED_ENABLED=false`; e só se inserem linhas em tabelas existentes,
  pelo que `ddl-auto: validate` não é obstáculo.
  **Por decidir (T-015):** o catálogo do ecrã não foi tocado — divergência
  agrava de 17-contra-19 para 17-contra-20.
- 2026-08-21 **CONCLUÍDA com nota 4.0** — o código está certo e o `lexcv-avaliador`
  reconfirmou o argumento da migração de forma independente (mais dois pilares que
  o próprio agente não invocou: `upsertRolePermissions` é aditivo, e `Role` é
  global, sem `tenant_id`, logo um upsert cobre todos os escritórios). **A nota
  baixa é do relatório, não da alteração.** O agente relatou "192 testes, 1 erro
  ambiental de Testcontainers"; o avaliador correu `mvn test` três vezes e obteve
  **187 testes, 0 falhas, 0 erros, BUILD SUCCESS**. O erro que o agente desculpou
  vinha de um relatório surefire velho de 08-05, de uma classe que o `pom.xml`
  liga ao failsafe e que o `mvn test` nem sequer executa. Relatório de verificação
  falso sobre um resultado que era, na verdade, melhor do que o relatado.

### T-012 — Cookies de sessão sem `Secure` e com duração fixada no código
**Dono:** ciclo GSD · **Estado:** BACKLOG · **Depende:** —
**Evidência:** ver linha no Backlog. Os documentos de cliente já declaram o
`Secure` como ponto de atenção conhecido — está documentado, não escondido.
**Histórico:**
- 2026-08-21 criada a partir de achado de revisão

### T-006 — Commitar a reorganização de `business/`
**Dono:** — · **Estado:** BLOQUEADA
**Evidência:** `git status` mostra `R ALCV_Apresentacao.pptx ->
business/apresentacoes/apresentacao-alcv.pptx` staged, mais 10 caminhos novos
por versionar.
**Pergunta ao humano:** commito a reorganização e a equipa de agentes?
**Histórico:**
- 2026-08-21 criada, bloqueada à nascença
