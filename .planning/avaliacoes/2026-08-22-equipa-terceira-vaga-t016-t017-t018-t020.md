# Avaliação — equipa (`lexcv-programador`, `lexcv-redator`, `lexcv-revisor-qualidade`, `lexcv-coordenador`) · Terceira vaga do dossiê: T-016 / T-017 / T-018 / T-020

**Data:** 22 de agosto de 2026 · **Nota final da vaga: 5.9 / 10** · **Veredicto: VAGA RETIDA**

| Agente | Entrega | Nota | Veredicto |
|---|---|---|---|
| `lexcv-programador` | `DEPLOYMENT.md`, `backend/migrations/README.md`, banner do `125`, `gerar-docx.py` | **7.6** | ACEITE COM CORREÇÕES |
| `lexcv-redator` | PRD §3.6/§4.1/§2.3, arquitetura §5.2/§8.1/§9.5/§11.8, roteiro §4.2/A.5 | **4.0** | REFAZER (§3.6) |
| `lexcv-revisor-qualidade` | contestação BLOQUEANTE à §3.6 | **4.0** | REFAZER (o achado) |
| `lexcv-coordenador` | decisões T-018/T-020, arbitragem da §3.6, `.planning/TAREFAS.md` | **6.5** | DEVOLVER AO AGENTE |

**Nota global do dossiê após três vagas: desce de 7.9 para 7.6.** Fundamentação na secção 6.

**O achado que retém a vaga:** a §3.6 do PRD afirma que a eliminação de honorários
"existe no produto desde a v2.16 … e não é posterior a ela" — é falso, e contradiz
diretamente o `registo-de-alteracoes.md:701` e o `relatorio-de-versao.md:613`, que
vendem a mesma capacidade como benefício entregue na **v2.0, 18 de junho de 2026**.
A arbitragem acertou no mecanismo e errou na data, e a verificação de sincronia
encontrou o sintoma e leu-o como aprovação.

---

## 0. O que verifiquei, e como

Não avaliei a partir de descrições. Corri o que era corrível e abri o que era citado.

| Verificação | Resultado |
|---|---|
| `SPRING_PROFILES_ACTIVE` em todo o repositório | **Zero ocorrências** em `docker-compose.yml`, `docker-compose.prod.yml`, `docker-compose.hostinger.yml`, `backend/Dockerfile` e `.github/workflows/deploy.yml`. Confirmado — `application-prod.yml` é ficheiro morto |
| `application.yml` vs `application-prod.yml` | `include-message: always` vs `never`; `ddl-auto: update` vs `validate`; `forward-headers-strategy` só no perfil morto. As três consequências da T-021 confirmam-se |
| `125` — números de linha do banner | `:115` = `lo_get(r.logo_data_url)`; `:118` = `EXCEPTION WHEN OTHERS`; `:142` = `DROP COLUMN`; `:143` = `RENAME COLUMN`. **4/4 exatos** |
| `Tenant.java:37-38` | `@Column(name = "logo_data_url", columnDefinition = "text")`, sem `@Lob`. **Exato** |
| `STATE.md:143/144/145` | `74`, `117`, `120` — **exatos, linha a linha**. E `91`, `93`, `96`, `111`, `125` não têm mesmo rasto nenhum no `STATE.md` |
| `120-add-tenant-ativo.sql:33` | `ALTER TABLE t_tenant ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;` sem `IF NOT EXISTS`. **Exato** |
| Inventário de 13 migrações | `ls backend/migrations/*.sql` = **13**. Confirmado |
| PostgreSQL 18.4 em `localhost:5432` | **Confirmado** — `psql (PostgreSQL) 18.4`, servidor a escutar (PID 6940). O ensaio do `125` é real (ver §1) |
| `ResourceController.java:3055` / `:3074` | `@PreAuthorize("hasAuthority('financeiro:manage')")` sobre `DeleteMapping("/honorarios/{id}")` e `.../pagamentos/{id}`. **Exatos, e iguais em `v2.16`** |
| Arbitragem: `git show v2.16:DatabaseSeeder.java` | `permKeys` = `financeiro:view`, `financeiro:edit`. **Sem `financeiro:manage`.** O mecanismo da arbitragem confirma-se |
| **`git show v2.3:…`** (não feito por ninguém) | Os dois `DeleteMapping` **já lá estão, já guardados por `financeiro:manage`**, e o `permKeys` da v2.3 **já não a tem**. `v2.3` = 21 de junho de 2026. Também na v2.9 e na v2.13 |
| Determinismo do `gerar-docx.py` | **Corri duas vezes.** MD5 dos seis idênticos entre as duas corridas — e **idênticos aos seis binários já em disco**. Determinismo e sincronia provados em simultâneo |
| Fidelidade palavra a palavra | Contagem de tokens `.md` vs `.docx`, por documento: 3537/3537, 6131/6131, 5676/5676, 2554/2554, 3218/3218, 1286/1286. **Zero perdas.** As 13 "diferenças" que o meu comparador acusou são artefacto do meu próprio *stripping* de `_` (`PLATAFORMA_ADMIN`) |
| Espaços múltiplos | `--verificar` = `OK` nos seis. Zero |
| Convergência tipográfica | Os seis: `8.50×11.00in`, `PORTRAIT`, `Calibri/11.0`, `author='LexCV — business/scripts/gerar-docx.py'`. **Uma família, provada por construção** |
| Sincronia por conteúdo | `requisitos-produto.docx` contém "existe no produto desde a v2.16" e "inacessível a todos os utilizadores"; `especificacao-arquitetura.docx` contém "A configuração própria de produção não é ativada pela instalação"; `roadmap-tecnologico.docx` contém "Flyway ou Liquibase" e "essa configuração nunca chega a ser ativada". **Os `.docx` carregam esta ronda** |
| `A.5 (detalhe)` órfão | Zero remissões pendentes em `business/`. `Anexo A.5` continua a existir e resolve |
| **`docker-compose.yml` / `.hostinger.yml`: chaves do arranque em dois tempos** | `SPRING_JPA_HIBERNATE_DDL_AUTO` **não existe** no `docker-compose.yml`; `SEED_ENABLED` é literal `"true"` em ambos; nenhum dos dois tem `env_file:`. **O procedimento do `DEPLOYMENT.md` não funciona pela via que ele próprio indica em primeiro lugar** (ver §1) |

**Lacuna que declaro:** não consegui determinar o *commit* que introduziu a guarda
`financeiro:manage` nos dois `DeleteMapping`. Não há etiquetas `v2.0`, `v2.1` nem
`v2.2`, e `git log -S` está inutilizado neste ambiente pelo filtro rtk — limitação
que o próprio `TAREFAS.md` documenta na T-008. O que fica provado é o limite
superior: **v2.3, 21 de junho de 2026**. E o registo de alterações da própria
equipa coloca a capacidade na **v2.0, 18 de junho de 2026**.

---

## 1. `lexcv-programador` — `DEPLOYMENT.md`, `migrations/README.md`, banner do `125`, `gerar-docx.py` — 7.6 / 10

### Cumprimento do contrato

| Item exigido pelo `<entrega>` | Estado |
|---|---|
| `## Âmbito` com confirmação de estar fora do ciclo GSD | CUMPRIDO — infraestrutura, *scripts* e documentação de operação; zero linhas de Java ou de `web/` |
| `## Alterações` (ficheiro / o que mudou e porquê) | CUMPRIDO |
| `## Verificação` — comando corrido + resultado real colado | **CUMPRIDO com excelência** no `125`; **EM FALTA** no `DEPLOYMENT.md` (ver achado ALTO) |
| `## Invariantes` (tenant, RBAC, terminologia) | CUMPRIDO — o `125` teve **zero SQL alterado**, só comentário. É a fronteira certa |
| `## Fora de âmbito que encontrei` | CUMPRIDO — o perfil de produção morto e a re-execução destrutiva do `125` subiram para T-021/T-022 |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 25% | 8 | Não invadiu o GSD. O `125` foi tocado **só em comentário** — 56 linhas inseridas, zero SQL modificado (`git diff` confirma `+56 -0`). Desconto: as escolhas de **formato de papel** e de **orientação** dos seis documentos de cliente são decisões editoriais, não de *tooling*, e foram tomadas dentro do *script* sem as devolver |
| Ancoragem em evidência | 30% | 7 | 7/7 citações de linha que amostrei estão **exatas** (`125:115/118/142/143`, `Tenant.java:37-38`, `120:33`, `STATE.md:143-145`). O ensaio do `125` é real: confirmei `psql (PostgreSQL) 18.4` a escutar em `localhost:5432`. Contra isto: a instrução central do `DEPLOYMENT.md` foi escrita **sem abrir os ficheiros `docker-compose` de que depende** — pelo mesmo agente que, na mesma vaga, provou o perfil morto abrindo exatamente esses ficheiros |
| Completude do entregável | 20% | 6 | Os quatro artefactos existem e funcionam, mas o T-016 entrega um procedimento operacional que **não se executa como está escrito** |
| Honestidade sobre lacunas | 15% | 9 | Exemplar. Escreveu os dois *caveats* do ensaio **nos dois ficheiros**, à letra: *"a corrida foi em PostgreSQL 18.4 … enquanto as imagens de instalação são `postgres:16-alpine`"* e *"o fixture reproduziu a forma da coluna … não um esquema completo gerado pelo Hibernate"*. E encerra: *"A proibição não depende de nada disso."* Corrigiu ainda uma imprecisão do seu próprio README (era resolução de função, não *cast*). Por declarar: a circularidade do `verificar()` e a contradição `autofit` |
| Clareza | 10% | 9 | O `backend/migrations/README.md` é o melhor artefacto de todo o dossiê: banner de paragem, dois caminhos, tabela de re-execução, estado por base de dados, e um "como correr" com `-v ON_ERROR_STOP=1` |
| **Ponderada** | | **7.6** | |

### Reprovação automática
**Nenhuma.** A instrução falhada do `DEPLOYMENT.md` é uma afirmação sobre *instalação*,
não sobre o que o produto faz, e por isso não dispara o critério — mas fica a um passo dele.

### Problemas

#### [ALTO] O arranque em dois tempos não se executa pela via que o próprio documento indica primeiro

> "Set these on the `backend` service (via `.env`, or directly in the compose file)"
> — `DEPLOYMENT.md`, Stage 2

**Porque é problema.** O bloco de comandos da mesma secção prescreve
`docker compose -f docker-compose.yml -f docker-compose.prod.yml up -d backend`.
Nesse caminho:

- `docker-compose.yml` **não tem** `SPRING_JPA_HIBERNATE_DDL_AUTO` no bloco
  `environment:` e **não tem** `env_file:`. Pôr a chave no `.env` serve para
  substituição de variáveis dentro do ficheiro Compose, **não é injetada no
  contentor**. O passo 2 não tem efeito nenhum.
- `SEED_ENABLED` é literal — `docker-compose.yml:63` = `"true"`,
  `docker-compose.hostinger.yml:62` = `true`. Não é `${SEED_ENABLED}`. Não há
  `.env` que o desligue.

Ou seja: o operador segue o procedimento, reinicia, vê o *backend* arrancar, dá
a instalação por concluída — e continua em `update` com a semeadura ligada. E o
documento diz, em maiúsculas de significado, *"A instalação não está completa até
o backend ter arrancado com sucesso em `validate`"*. O arranque com sucesso passa
a **provar o contrário do que o documento diz que prova**.

**Como corrigir.** Ou (a) acrescentar `SPRING_JPA_HIBERNATE_DDL_AUTO:
${SPRING_JPA_HIBERNATE_DDL_AUTO:-update}` e `SEED_ENABLED: ${SEED_ENABLED:-true}`
ao `environment:` dos dois ficheiros Compose, e então a frase fica verdadeira; ou
(b) retirar "via `.env`" e escrever que a alteração é **no ficheiro Compose**,
nomeando a linha exata de cada um. E acrescentar o passo de confirmação que o
procedimento hoje não tem: `docker compose … config | grep -E "DDL_AUTO|SEED"`
antes de reiniciar, e `docker compose exec backend env | grep …` depois.

#### [ALTO] A família convergiu para papel Carta, num dossiê institucional cabo-verdiano

> `PAGINA_LARGURA = Inches(8.5)` / `PAGINA_ALTURA = Inches(11)` — `gerar-docx.py:81-82`

**Porque é problema.** 8.5×11 in é **US Letter**. A norma em Cabo Verde, como em
todo o espaço lusófono e europeu, é **A4** (8.27×11.69 in). Os seis documentos
saem em Carta. O próprio *docstring* regista que uma das três famílias anteriores
era *"uma variante A4-paisagem a 10pt"* — a convergência **afastou-se** da norma
local, e escolheu o valor por omissão do modelo `python-docx` sem que ninguém
tenha posto a pergunta. Para um documento que a entidade contratante vai imprimir,
isto é substantivo, não cosmético: margens erradas e um sinal visível de origem.
Está declarado no `business/README.md` ("Carta retrato"), o que evita a acusação
de omissão — mas declarar uma escolha não é justificá-la.

**Como corrigir.** `PAGINA_LARGURA = Mm(210)`, `PAGINA_ALTURA = Mm(297)`,
regenerar os seis, e registar a decisão no `business/README.md`. É uma alteração
de duas linhas e uma corrida do *script* — a regra da fonte única, aliás, torna-a
trivial, o que é precisamente o valor do T-020.

#### [ALTO] O roteiro passou a retrato, e era paisagem por causa das tabelas

**Porque é problema.** Calculei a distribuição de larguras que o `escrever_tabela`
produz para a tabela 4 do roteiro (6 colunas, 4 linhas, célula maior com **1250
caracteres**), com a largura útil real de 6.6 in:

| Coluna | Máx. carateres | Largura atribuída | ≈ carateres por linha a 10pt |
|---|---|---|---|
| Iniciativa | 73 | 0.71 in | ~8 |
| O que muda para o escritório | 375 | 1.62 in | ~19 |
| Origem / evidência | 1248 | 2.79 in | ~33 |
| **Estado** | 8 | **0.40 in** | **~5** |
| Esforço estimado (semanas) | 65 | 0.67 in | ~8 |
| **Confiança na estimativa** | 23 | **0.40 in** | **~5** |

"Previsto" (8 carateres) não cabe numa linha de 5. "Confiança na estimativa"
empilha-se em cinco linhas de cabeçalho. A célula de 1248 carateres a 33
carateres por linha dá ~38 linhas — uma linha de tabela com cerca de 13 cm de
altura, numa página de 27.9 cm. A tabela 7 tem uma célula de **1475** carateres.
A orientação paisagem existia por causa disto, e foi removida sem medida
compensatória.

Agrava: `gerar-docx.py:426` faz `tab.autofit = True` **e** `:449`
`celula.width = Twips(larguras[ci])`. São diretivas contraditórias — com
`tblLayout` em auto, o Word recalcula as larguras a partir do conteúdo e a
amortização por raiz quadrada, cuidadosamente construída, pode nem se aplicar.
O agente declarou honestamente que *"a APARÊNCIA dos .docx não é confirmável
aqui"* — mas esta consequência era determinável por aritmética, sem Word.

**Como corrigir.** Permitir uma secção em paisagem por documento (um marcador no
`.md`, ou uma exceção nomeada no *script* para `roadmap-tecnologico.md`), ou
partir as duas tabelas largas em duas de três colunas. E decidir entre `autofit`
e larguras explícitas — não os dois.

#### [MÉDIO] O `--verificar` valida o escritor contra o mesmo analisador que quer validar

> `esperado = contar_esperado(blocos)` … `problemas = verificar(destino, esperado)` — `gerar-docx.py:652-662`

**Porque é problema.** `contar_esperado` e `analisar` são a mesma passagem de
*parsing*. Se o analisador ler mal o Markdown — perder uma tabela, tomar uma
linha de dados por separador — o "esperado" erra na mesma direção e a verificação
passa. O que o modo `--verificar` prova é *round-trip*, não fidelidade. A única
verificação genuinamente independente é a de espaços múltiplos. Não está declarado.

**Como corrigir.** Contar tabelas/títulos/listas por expressão regular sobre o
`.md` cru, independente do analisador; e integrar no `--verificar` a comparação
de contagem de palavras `.md` ↔ `.docx` que eu tive de fazer à parte — é dez
linhas e transforma a alegação "fidelidade palavra a palavra" de afirmação de
sessão em invariante do *script*.

#### [BAIXO] Auto-referência errada no *docstring*

> "Ver ESPECIFICACAO no fim deste ficheiro." — `gerar-docx.py:27`

A `ESPECIFICACAO` está na **linha 69**, perto do topo; o ficheiro tem 682 linhas.

### Melhorias sugeridas
1. Corrigir a via do passo 2 no `DEPLOYMENT.md` **e** acrescentar as duas chaves
   ao `environment:` dos ficheiros Compose — sem isso o T-016 não entrega nada.
2. A4 nos seis, e a decisão escrita no `business/README.md`.
3. Paisagem para o roteiro, ou tabelas partidas.
4. Meter a contagem de palavras no `--verificar`.

### Melhorias no próprio agente *(não contam para a nota)*
O `<verificacao>` lista `mvn test`, `spotbugs`, `pnpm build` — todos de código
compilado. Não cobre o caso desta vaga: **documentação de operação**. Acrescentar
uma linha: *"Se entregas um procedimento de instalação, corre `docker compose …
config` e confirma que as chaves que mandas alterar existem mesmo no serviço."*
O agente foi impecável a verificar SQL e binários, e não verificou o único
artefacto para o qual o contrato não lhe dava um comando.

---

## 2. `lexcv-redator` — PRD §3.6, arquitetura §5.2/§8.1/§9.5/§11.8, roteiro §4.2/A.5 — 4.0 / 10

### Cumprimento do contrato

| Item exigido pelo `<entrega>` | Estado |
|---|---|
| `## Ficheiros` | CUMPRIDO |
| `## Verificado` — cada capacidade + onde a confirmou | **PARCIAL** — a capacidade central da §3.6 foi descrita com uma data que não foi verificada em lado nenhum |
| `## Assumido` | **EM FALTA no que importava.** "existe no produto desde a v2.16 … e não é posterior a ela" está escrito no documento como facto, não como assunção |
| `## Em falta` | CUMPRIDO |
| Zero jargão técnico ao cliente | **CUMPRIDO com excelência** — ver abaixo |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 25% | 8 | Não revê, não decide, não inventa preço. E a disciplina de linguagem é o melhor do dossiê: a §11.8 descreve um perfil Spring inativo sem escrever "perfil", "Spring" nem `ddl-auto` — *"O código inclui um conjunto de definições destinado exclusivamente a produção, mas nenhum dos ficheiros de instalação … o seleciona"* |
| Ancoragem em evidência | 30% | 3 | O `<verdade_antes_de_prosa>` obriga: *"Antes de descrever qualquer capacidade do produto, verifica-a em `.planning/MILESTONES.md` ou no código."* A data da §3.6 não foi verificada nem à primeira ("depois da v2.16") nem à segunda ("desde a v2.16"). Bastava `git show v2.3:` — o mesmo comando que o coordenador correu, uma etiqueta antes. Em contrapartida, o "Limite conhecido" da §3.6 é **exemplar**: *"a ocorrência fica, na maioria dos casos, no registo técnico"* — o "na maioria dos casos" corresponde exatamente ao `catch (DataAccessException)` de `ResourceController.java:3098`, que não apanha tudo. Isso é leitura de código a sério |
| Completude do entregável | 20% | 7 | Todas as secções pedidas existem e resolvem; nenhuma remissão órfã. Mas a §3.6 obriga a nova ronda |
| Honestidade sobre lacunas | 15% | 4 | Recebeu uma premissa do coordenador, foi contestado com prova, recebeu uma segunda premissa e escreveu-a a afirmar mais do que a primeira: *"foi construída e lançada com essa versão, **e não é posterior a ela**"*. Depois de ter sido apanhado uma vez na mesma frase, a resposta correta era o `## Assumido`, não uma negação enfática |
| Clareza | 10% | 9 | A §11.8 organiza um achado técnico em três consequências com um remédio comum, e distingue paliativo de correção de fundo. Um contratante decide a partir dali |
| **Ponderada** | | (6.0) | **anulada pela reprovação automática** |

### Reprovação automática
**DISPARA.** Critério: *"Afirmação sobre o que o LexCV faz **não verificada**
contra código ou `MILESTONES.md`"*. A frase *"A eliminação de honorários e de
pagamentos existe no produto desde a v2.16"* é uma afirmação sobre o produto,
apresentada como facto, não verificada, **falsa**, e em contradição direta com
dois documentos irmãos da mesma pasta. Nota limitada a **4.0**.

### Verificação por amostragem

| Afirmação do agente | Confirmei em | Bate? |
|---|---|---|
| "Eliminar um honorário só é possível enquanto não tiver pagamentos registados … *Não é possível eliminar um honorário com pagamentos registados*" | `ResourceController.java:3065-3068` — `HttpStatus.CONFLICT` com a mensagem à letra | **Sim** |
| "Eliminar um pagamento não tem salvaguarda equivalente … o valor pago é revertido na conta corrente" | `ResourceController.java:3088-3095` — `cc.setSaldo(cc.getSaldo().subtract(pag.getValorPago()))` | **Sim** |
| "o pagamento é eliminado à mesma e o utilizador não é avisado" | `catch (DataAccessException ex) { log.warn(...) }` e a eliminação prossegue | **Sim** |
| "A ação gerir do âmbito Financeiro … não chegou a ser atribuída a nenhum papel — nem ao ADMIN" | `git show v2.16:DatabaseSeeder.java` → `permKeys` sem `financeiro:manage` | **Sim** |
| **"existe no produto desde a v2.16 … e não é posterior a ela"** | `git show v2.3:ResourceController.java` → os dois `DeleteMapping` já lá estão, já guardados. `git tag` → não há v2.0/v2.1/v2.2. `registo-de-alteracoes.md:701` → v2.0, 18 de junho | **NÃO** |
| "o servidor recusa-a mesmo que o interface seja contornado" | `@PreAuthorize` de método com `@EnableMethodSecurity` | **Sim** |

Cinco em seis. A sexta é a que sustenta a secção inteira.

### Problemas

#### [BLOQUEANTE] A §3.6 data a capacidade na v2.16, e o próprio dossiê data-a na v2.0

> "A eliminação de honorários e de pagamentos **existe no produto desde a v2.16**:
> foi construída e lançada com essa versão, e não é posterior a ela."
> — `requisitos-produto.md` §3.6

Contra, no mesmo `business/documentacao/`:

> "Filtros sobre a lista de honorários e ações de edição e **eliminação**
> diretamente na lista." — `registo-de-alteracoes.md:701`, sob
> **`## v2.0 — Módulo Financeiro`**, *"Data de entrega: 18 de junho de 2026"*

> "**Passou a filtrar a lista de honorários e a editar ou eliminar diretamente
> nela**, sem abrir cada registo." — `relatorio-de-versao.md:613`, secção v2.0,
> *"Para o gestor do escritório"*

E no código: os dois `DeleteMapping`, já guardados por `financeiro:manage`, estão
em `v2.3` (21 de junho de 2026), e o `permKeys` dessa etiqueta já não a tinha.
Também em `v2.9` e `v2.13`. Não há etiquetas v2.0–v2.2 para testar mais atrás.

**Porque é problema.** O leitor é o mesmo, a pasta é a mesma. Ou a eliminação é
de v2.0 e nunca funcionou — e então as notas de versão prometeram-lhe, há dois
meses, um benefício que nunca existiu —, ou é de v2.16 e o registo de alterações
mente. Um contratante que leia os três encontra a contradição em cinco minutos, e
a partir daí desconfia dos três.

**Como corrigir.** Substituir por uma formulação que a evidência sustenta:
*"A eliminação de honorários e de pagamentos está no produto desde as primeiras
versões do módulo Financeiro. Sucede que a ação gerir do âmbito Financeiro, única
que a autoriza, nunca foi atribuída a nenhum papel — pelo que a operação, apesar
de instalada, permaneceu inacessível a todos os utilizadores, incluindo o ADMIN.
O lapso está corrigido e a operação fica disponível, para o perfil ADMIN, com a
próxima atualização."* Sem número de versão, que é o que não está estabelecido —
ou com "desde a v2.3, e possivelmente desde a v2.0", que é o que está.

#### [BLOQUEANTE, fora do âmbito do redator] O registo de alterações e as notas de versão continuam a vender a capacidade sem ressalva

Nenhum dos dois foi tocado nesta ronda. Ambos descrevem, na v2.0, uma eliminação
que — segundo a §3.6 que a mesma equipa acabou de escrever — nunca esteve
acessível a ninguém. **A tarefa para isto não existe.** Sobe para o coordenador
(§4), não para o redator: o T-017 só lhe deu o PRD.

### Melhorias sugeridas
1. Reescrever o parágrafo de ressalva da §3.6 sem número de versão não provado.
2. Levar para o `## Assumido` qualquer datação de versão que não tenha uma
   etiqueta git aberta por trás.

### Melhorias no próprio agente *(não contam para a nota)*
O `<verdade_antes_de_prosa>` diz "verifica em `MILESTONES.md` ou no código". Ambas
as fontes descrevem **o presente**. Nenhuma responde a *"desde quando"*.
Acrescentar: *"Uma afirmação sobre **quando** algo passou a existir verifica-se em
`git show <tag>:<ficheiro>` na etiqueta anterior à alegada — nunca no `MILESTONES.md`,
que regista intenção, e nunca no `git log`, que aqui vem filtrado."* Este agente
errou duas vezes a mesma frase porque o contrato não lhe diz como se prova uma data.

---

## 3. `lexcv-revisor-qualidade` — a contestação da §3.6 — 4.0 / 10

### Cumprimento do contrato

| Item exigido pelo `<entrega>` | Estado |
|---|---|
| `## Veredicto` | CUMPRIDO — `CORRIGIR ANTES DE ENVIAR`, e estava certo em bloquear |
| Cada achado com evidência (citação + onde procurou) | CUMPRIDO na forma — apresentou `git show v2.16` e a data 2026-06-18 |
| Não reescreveu | CUMPRIDO — não tem `Edit` nem `Write`; propôs em texto |
| Não inventou problemas | CUMPRIDO — o problema era real |
| Classificação `CONFIRMADO`/`PARCIAL`/`NÃO ENCONTRADO`/`CONTRADITO` correta | **EM FALTA** — deu `CONFIRMADO` a uma conclusão que a sua própria evidência não continha |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação |
|---|---|---|---|
| Fidelidade à função | 25% | 9 | Fez exatamente o que lhe compete: apanhou uma afirmação falsa do redator num documento a caminho do cliente, bloqueou, não reescreveu. Se não tivesse contestado, "foi construída depois da v2.16" tinha ido para o cliente |
| Ancoragem em evidência | 30% | 2 | A prova era verdadeira e a conclusão não decorria dela. Provou que **os pontos de eliminação existem** e concluiu que **a eliminação funciona**. São duas proposições diferentes, separadas por uma guarda `@PreAuthorize` que ele não abriu. E a resposta estava, escrita, no ficheiro de trabalho da própria equipa: a T-011 do `TAREFAS.md`, fechada no dia anterior, chama-se literalmente *"`financeiro:manage` exigida mas sem titular"* |
| Completude do entregável | 20% | 6 | Achado formatado e com severidade; mas a coluna "Estado" da tabela de verificação factual atribuiu confirmação a uma inferência |
| Honestidade sobre lacunas | 15% | 3 | O contrato é explícito: *"`NÃO ENCONTRADO` significa 'não consigo confirmar', não 'é falso'"* — o espírito da regra vale nos dois sentidos, e aqui foi violado no sentido contrário: "encontrei o *endpoint*" foi tratado como "confirmei o comportamento". Nenhuma reserva declarada |
| Clareza | 10% | 8 | O achado era legível e acionável — tanto que **foi acionado**, e essa é parte do problema |
| **Ponderada** | | (5.4) | **anulada pela reprovação automática** |

### Reprovação automática
**DISPARA.** Mesmo critério: *"Afirmação sobre o que o LexCV faz não verificada
contra código."* A frase *"logo já está a correr nas instalações existentes"* é
uma afirmação sobre o comportamento do produto, apresentada dentro de um achado
`BLOQUEANTE` como conclusão provada, e é falsa. Nota limitada a **4.0**.

### O modo de falha, nomeado

Isto **não** é o revisor preguiçoso que não verifica. É o oposto, e é mais
perigoso: um revisor que verifica, acerta na prova, e depois faz o salto que a
prova não autoriza — e a autoridade da prova verdadeira transporta-se para a
conclusão falsa. Toda a gente a jusante confia, porque *houve* `git show`.

O custo mediu-se: a arbitragem que se seguiu partiu deste achado, corrigiu-lhe o
mecanismo, e ficou com a data **errada na direção oposta** (v2.16 em vez de junho).
A data que o revisor trazia — **2026-06-18** — é a data que o `registo-de-alteracoes.md`
atribui à v2.0 e é, das três em cima da mesa, **a mais próxima da verdade**. Foi
descartada junto com a inferência errada. Achado verdadeiro, conclusão falsa,
e a conclusão falsa afundou o achado verdadeiro.

**A regra que faltava:** num sistema com RBAC em duas camadas — que o `CLAUDE.md`
declara como invariante de projeto — verificar um *endpoint* **nunca** é verificar
uma capacidade. São sempre dois ficheiros: a guarda e o semeador.

### Melhorias sugeridas
Refazer o achado com a segunda metade da prova, e reclassificar a data: a
capacidade é anterior à v2.16.

### Melhorias no próprio agente *(não contam para a nota)*
Acrescentar ao `<metodo>` um quinto passo, obrigatório:
*"Uma capacidade guardada por `@PreAuthorize('<escopo>:<ação>')` só está
`CONFIRMADO` depois de confirmares que algum papel detém essa autoridade em
`DatabaseSeeder.java#permKeys` e em `UserPrincipal.java`. O ponto existir é
`PARCIAL`, nunca `CONFIRMADO`."* E uma segunda: *"Antes de contestares uma data,
lê o `.planning/TAREFAS.md` — um achado já fechado sobre o mesmo assunto muda a
tua conclusão."*

---

## 4. `lexcv-coordenador` — decisões T-018/T-020, arbitragem, `.planning/TAREFAS.md` — 6.5 / 10

*(Avaliado a pedido expresso do próprio, e sem contenção. Duas das quatro decisões
desta vaga foram suas, por delegação do dono.)*

### Cumprimento do contrato

| Item exigido pelo `<entrega>` | Estado |
|---|---|
| `.planning/TAREFAS.md` atualizado no sítio, nunca recriado | CUMPRIDO — IDs e histórico preservados |
| Cabeçalho com data absoluta + próximo passo | **PARCIAL** — data **desatualizada** (ver achado) |
| Tabelas Em curso / Bloqueadas / Backlog / Concluídas | CUMPRIDO |
| Bloco `### T-xxx` de Detalhe **por tarefa** | **EM FALTA** — 8 tarefas sem bloco nenhum |
| `Estado` coerente entre tabela e detalhe | **EM FALTA** — 7 divergências |
| Nota, ronda e achado mais grave registados no corpo | PARCIAL — T-019 fechada sem nota |
| Não produziu o entregável | CUMPRIDO — despachou tudo |
| Toda a tarefa fechada passou pelo portão | CUMPRIDO |
| Um só próximo passo | CUMPRIDO na forma, **discutível na escolha** |
| Nunca despachar tarefa com dependência por satisfazer | **VIOLADO** — T-020 |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 25% | 7 | Não escreveu documentos nem código; delegou tudo. Contra: o T-020 declara `Depende: T-016,T-017,T-018` e foi corrido com as três `EM REVISÃO` — o contrato diz *"Nunca despaches uma tarefa com dependência por satisfazer"*, com exemplo idêntico. Correu bem por sorte (os `.docx` saem byte a byte do `.md` atual, confirmei), mas uma devolução de qualquer das três obrigava a regerar os seis |
| Ancoragem em evidência | 30% | 5.5 | A arbitragem é metodicamente exemplar e **reproduzi-a**: `permKeys` da v2.16 sem `financeiro:manage`, `DeleteMapping` a exigi-la. A T-021 vai além do óbvio e está toda correta (`include-message: always` vs `never`; `forward-headers-strategy` só no perfil morto; a consequência no `getRemoteAddr()`). Contra, e é pesado: a premissa original era falsa; a substituta **também**; e a arbitragem parou na etiqueta que confirmava a história já formada, com a data do revisor (18 de junho, que é a do próprio registo de alterações) em cima da mesa e por reconciliar |
| Completude do entregável | 20% | 5 | Oito tarefas sem bloco de Detalhe — T-013, T-014, T-015, T-021, T-022, T-023, T-024, T-025 — incluindo o maior achado da vaga e duas já `CONCLUÍDA`, cujo histórico se perde. Sete `Estado:` divergentes. Cabeçalho com data de ontem. **É o mesmo defeito que valeu 6.8 na vaga anterior**: o conteúdo melhorou muito, o campo não |
| Honestidade sobre lacunas | 15% | 9 | Notável, e raro. Escreveu o próprio erro no ficheiro que mantém: *"**eu** disse ao redator que a capacidade 'só se tornou alcançável nesta corrida e não está comprometida em git' — errado na letra"*. E deixou por resolver, à vista: *"flipar uma instalação Hostinger já a correr para `validate` é seguro?"*. Não escondeu nada |
| Clareza | 10% | 8 | A "Nota de arbitragem" é um artefacto útil por si: três versões, quem errou em quê, e a lição — *"um revisor que contesta com prova pode estar a provar a coisa errada"* |
| **Ponderada** | | **6.5** | |

### Reprovação automática
**Nenhuma.** O entregável está no sítio e no formato contratado; as omissões de
Detalhe são incompletude, não formato errado.

### Problemas

#### [ALTO] A arbitragem parou na etiqueta conveniente

> "`git show v2.16:...DatabaseSeeder.java` mostra `permKeys` com apenas
> `financeiro:view` e `financeiro:edit` … **A funcionalidade foi lançada na v2.16
> mas era inalcançável por todos**"

**Porque é problema.** O mecanismo está certo e é uma boa peça de trabalho. A
datação não. Correr o mesmo comando em `v2.3` — uma etiqueta antes de metade das
que existem — dá o mesmo resultado: os dois `DeleteMapping` guardados por
`financeiro:manage`, e o `permKeys` sem ela. Também na v2.9 e na v2.13. E não
existem etiquetas v2.0–v2.2, pelo que a v2.3 é o limite superior, não a origem.

Havia ainda um sinal por reconciliar e que foi deitado fora com a conclusão errada
do revisor: **2026-06-18** é exatamente a data que o `registo-de-alteracoes.md`
atribui à v2.0. O revisor tinha a data certa dentro de um raciocínio errado; a
arbitragem corrigiu o raciocínio e substituiu a data certa por uma errada.

O padrão a nomear: verificou-se **até** a hipótese ficar confirmada, e não **para
além** dela. Uma etiqueta a mais fechava a questão.

**Como corrigir.** Reabrir a T-017 com a datação certa e mandar o redator
reescrever a ressalva sem número de versão não provado.

#### [ALTO] A verificação de sincronia encontrou a contradição e leu-a como aprovação

> "Já confirmei por mtime que os seis `.docx` são posteriores aos `.md`, e que
> **só o PRD menciona a eliminação**."

**Porque é problema.** Confirmo o facto — testei por conteúdo, não por data:
`requisitos-produto.docx` é o único dos seis que contém "eliminação de honorários".
Mas isso não é o âmbito bem delimitado; é o defeito. `registo-de-alteracoes.md:701`
e `relatorio-de-versao.md:613` descrevem a eliminação como benefício entregue na
v2.0, **sem ressalva nenhuma** — e é a mesma capacidade que a §3.6 acaba de
declarar inacessível a toda a gente desde sempre. O dossiê passou a dizer duas
coisas incompatíveis sobre o mesmo botão, em três documentos, na mesma pasta.

E não há tarefa aberta para isto. O T-017 foi enunciado como "o PRD passa a
descrever a eliminação"; o âmbito certo, depois da arbitragem, era "o dossiê
passa a ser coerente sobre a eliminação".

**Como corrigir.** Abrir tarefa para acrescentar ao `registo-de-alteracoes.md`
(v2.0 e v2.16) e ao `relatorio-de-versao.md` a mesma ressalva da §3.6, e revê-la
depois de a datação estar fixada. Sem isto a vaga não fecha.

#### [MÉDIO] `TAREFAS.md`: o mesmo campo desatualizado, segunda vaga seguida

- **Cabeçalho:** `**Atualizado:** 2026-08-21`, quando as entradas da própria vaga
  dizem `2026-08-22`.
- **Sete `Estado:` que contradizem as tabelas:** T-007/T-008/T-009 dizem
  `ATRIBUÍDA` e estão em Concluídas; T-011 diz `EM CURSO` e está fechada com 4.0;
  T-016/T-017/T-018 dizem `EM CURSO` e a tabela diz `EM REVISÃO`; T-019 diz
  `EM CURSO` e está fechada; **T-020 diz `BACKLOG` e "espera que os `.md`
  estabilizem"** — está entregue e em revisão.
- **Oito tarefas sem bloco de Detalhe:** T-013, T-014, T-015, T-021, T-022,
  T-023, T-024, T-025. A T-021 é o maior achado da vaga e vive numa linha de
  tabela. T-013 e T-014 estão `CONCLUÍDA` com nota e sem histórico nenhum.
- **T-019** fechada com `Nota: —`, contra *"Regista sempre no corpo da tarefa:
  nota, ronda, e o achado mais grave"*.

**Porque é problema.** Um leitor que abra o Detalhe da T-020 conclui que ainda
não arrancou. O `TAREFAS.md` é o único ficheiro de trabalho deste agente; a
métrica dele é este ficheiro estar certo.

**Como corrigir.** Uma passagem de coerência no fim de cada corrida: data do
cabeçalho, `Estado:` de cada Detalhe contra a tabela, e um bloco por cada ID que
apareça em qualquer tabela. É mecânico e é o trabalho.

#### [MÉDIO] "Próximo passo: T-015" com a T-021 em cima da mesa

> "**Próximo passo:** T-015 — dizer se o catálogo do ecrã de Controlo de Acesso
> passa a mostrar `financeiro:manage`"

Uma linha de catálogo de apresentação, à frente da T-021 — que o próprio
coordenador escreveu e que diz que a produção altera o esquema automaticamente em
cada arranque, devolve mensagens de exceção nas respostas de erro, e tem o
bloqueio de login colapsado num balde global. As três verifiquei e as três são
verdade. O contrato pede **uma** recomendação; esta é a errada.

### O que o coordenador fez bem, e que conta

Três coisas, e nenhuma é pequena:

1. **Recusou as duas versões e foi ao git.** Podia ter escolhido entre o redator
   e o revisor. Foi verificar, e o mecanismo que apurou está certo.
2. **A T-021 é trabalho de primeira.** Não parou no `ddl-auto`: seguiu o perfil
   morto até três consequências independentes, e ligou uma delas ao comentário do
   próprio `application-prod.yml`. Reproduzi as três.
3. **O princípio do T-018 é sólido e foi bem executado.** *"A contagem de
   migrações vive num sítio só, e esse sítio é o repositório."* Verifiquei o
   resultado: nenhuma remissão órfã, nenhuma duplicação, `Anexo A.5` resolve, e o
   `backend/migrations/README.md` é hoje o melhor artefacto do projeto. Das três
   decisões delegadas, esta é irrepreensível.

E a honestidade — pedir explicitamente que a premissa errada fosse pesada contra
si — é o que distingue este agente. Não altera a aritmética, mas fica registado:
o erro foi declarado pelo próprio antes de eu o encontrar.

### Melhorias no próprio agente *(não contam para a nota)*
1. Acrescentar ao `<manutencao_da_lista>`: *"No fim de cada corrida, verifica —
   (a) a data do cabeçalho é a de hoje; (b) cada ID que aparece numa tabela tem
   bloco `### T-xxx`; (c) o `Estado:` do bloco é igual ao da tabela."*
2. Acrescentar ao `<como_despachas>`: *"Quando dás uma premissa factual ao agente,
   marca-a como premissa e diz onde a verificaste. Uma premissa não verificada
   passada como facto é um erro teu que aparece na entrega dele."*
3. Acrescentar ao `<portao_de_revisao>`: *"Quando uma arbitragem alterar um facto
   já publicado, verifica que documento do dossiê o repete — e abre tarefa para
   cada um. Corrigir num documento e deixar nos outros cria contradição onde antes
   havia erro."*

---

## 5. Veredicto da vaga

**RETIDA.** Fecha quando estes três estiverem feitos:

| # | O que falta | Dono | Porquê é bloqueante |
|---|---|---|---|
| 1 | Reescrever a ressalva da §3.6 sem a datação "v2.16" | `lexcv-redator` | Afirmação falsa num documento de cliente, em contradição com dois documentos irmãos |
| 2 | Levar a mesma ressalva ao `registo-de-alteracoes.md` (v2.0 e v2.16) e ao `relatorio-de-versao.md` (v2.0) | `lexcv-redator`, tarefa a abrir pelo coordenador | Os dois vendem, hoje, uma capacidade que nunca esteve acessível |
| 3 | Corrigir a via do passo 2 no `DEPLOYMENT.md` **e** expor `SPRING_JPA_HIBERNATE_DDL_AUTO` / `SEED_ENABLED` no `environment:` dos ficheiros Compose | `lexcv-programador` | O procedimento não se executa como está escrito, e o "arranque com sucesso em `validate`" deixa de provar o que o documento diz que prova |

Fora do bloqueio, mas antes de o dossiê sair: **A4 em vez de Carta** nos seis, e
uma decisão sobre a orientação do roteiro. São uma corrida do *script*.

**Não bloqueia, mas é a decisão mais urgente do projeto:** a T-021. Nenhum
documento mente sobre isso — a arquitetura §11.8 e o roteiro §4.2 descrevem-no
com exatidão, o que é mérito desta vaga. Mas a instalação continua como está.

---

## 6. A nota global do dossiê depois de três vagas: **desce de 7.9 para 7.6**

**O que subiu.** Esta vaga entregou as três melhores peças de evidência de todo o
projeto. O `backend/migrations/README.md` transforma treze ficheiros soltos numa
lista de verificação com caminho, ordem, re-execução e estado por base de dados.
O ensaio do `125` é o único sítio em todo o dossiê onde alguém, tendo autorização
para escrever um *hedge*, foi antes buscar uma base de dados e **observou a
destruição** — 34.432 carateres apagados, código de saída 0 — e depois escreveu
os dois *caveats* que enfraquecem a própria prova. E o `gerar-docx.py` fecha, com
determinismo verificável, o buraco que produziu três famílias tipográficas
divergentes: reproduzi-o, e os seis binários em disco são byte a byte o que o
*script* produz a partir dos `.md` atuais. A arquitetura §11.8 e o roteiro §4.2
dizem hoje a verdade sobre a produção, e não diziam.

**O que desceu, e mais do que aquilo subiu.** O dossiê ganhou uma contradição
interna a três documentos sobre uma funcionalidade concreta, e a contradição foi
**introduzida por esta vaga**, não herdada. Passou o revisor (que errou a
conclusão), passou a arbitragem (que errou a data), e passou a verificação final
de sincronia (que encontrou o sintoma e o classificou como aprovação). Três
portões, um defeito, zero deteções. E é o segundo defeito consecutivo de datação
na mesma frase.

Isso obriga a uma leitura desagradável das duas vagas anteriores: o perfil de
produção morto tornou falsas afirmações que passaram o portão **duas vezes**. O
mérito de o encontrar é grande — três agentes, em separado — mas o que diz sobre
as revisões anteriores é que se verificou o que o documento **afirmava**, e não o
que a instalação **fazia**. O `CLAUDE.md:51` dizia "`validate` in prod" e toda a
gente o repetiu. A T-010 já nomeia essa linha como a raiz. Ficou por corrigir.

**7.6.** Sobe para **8.4** — acima dos 7.9 de partida — assim que os três pontos
do §5 fecharem, porque a substância que esta vaga acrescentou é, de longe, a mais
sólida do dossiê. É a datação e a coerência entre documentos que a estão a segurar,
e ambas são de meia hora de trabalho.
