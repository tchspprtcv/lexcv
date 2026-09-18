# Avaliação — equipa (`lexcv-programador`, `lexcv-redator`, `lexcv-revisor-qualidade`, `lexcv-coordenador`) · Segunda vaga do dossiê: T-011 / T-013 / T-014
**Data:** 21 de agosto de 2026 · **Nota final da vaga: 7.0 / 10**

> **Desvio ao meu próprio contrato, declarado.** O `<entrega>` manda
> `<AAAA-MM-DD>-<agente>-<slug>.md`, um agente por ficheiro. Isto é um portão
> conjunto sobre uma vaga com quatro agentes e uma pergunta explícita de coerência
> do conjunto. Uso `equipa` no lugar de `<agente>` e dou nota autónoma a cada um.
> Mesmo desvio da primeira vaga, mesma razão. Fica assinalado, não escondido.

| Agente | Tarefa | Nota | Veredicto |
|---|---|---|---|
| `lexcv-programador` | T-011 | **4.0** | **REFAZER** (só a secção `## Verificação`) |
| `lexcv-redator` | T-013 | **8.2** | ACEITE |
| `lexcv-redator` | T-014 (ronda 2) | **8.0** | ACEITE COM CORREÇÕES |
| `lexcv-revisor-qualidade` | revisão intermédia de T-014 | **8.0** | ACEITE COM CORREÇÕES |
| `lexcv-coordenador` | `.planning/TAREFAS.md` | **6.8** | **DEVOLVER AO AGENTE** |
| **Vaga (ponderada por volume: redator 2, restantes 1)** | | **7.0** | **VAGA RETIDA** |

**Nota global do dossiê após esta vaga: mantém-se 7.9.** Fundamentação na secção 8.

---

## 1. `lexcv-programador` — T-011 — 4.0 / 10

### Cumprimento do contrato

| Item exigido pelo `<entrega>` de `lexcv-programador.md` | Estado |
|---|---|
| `## Âmbito` com confirmação de que está fora do ciclo GSD | PARCIAL — executou uma alteração de RBAC que o seu `<fronteira_com_o_gsd>` manda devolver ao humano; a ordem do dono cobre-o, mas o desvio devia vir declarado por ele, e quem o declarou foi o coordenador |
| `## Alterações` — tabela ficheiro / o que mudou e porquê | CUMPRIDO |
| `## Verificação` — comando corrido + **resultado real, colado** | **EM FALTA** — o resultado relatado não é o resultado real (ver Reprovação automática) |
| `## Invariantes` — tenant_id, RBAC, terminologia | CUMPRIDO |
| `## Fora de âmbito que encontrei` | CUMPRIDO — devolveu o catálogo do ecrã (deu origem à T-015) |
| Deixar as alterações no working tree, sem commit | CUMPRIDO — `git diff --stat` mostra 2 ficheiros, 2 inserções, 2 remoções, nada staged |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 25% | 8 | O âmbito ficou **exemplarmente contido**. `git diff` inteiro: `"financeiro:view", "financeiro:edit",` → `"financeiro:view", "financeiro:edit", "financeiro:manage",` em dois ficheiros, e mais nada. Não tocou no catálogo do ecrã, não tocou nas guardas, não tocou nas atribuições dos outros papéis. Desconto por não ter sido ele a declarar que a tarefa contraria a sua própria `<fronteira_com_o_gsd>` ("alterar o modelo de dados, o **RBAC**, a autenticação") |
| Ancoragem em evidência | 30% | 3 | O argumento central está **certo e verifiquei-o linha a linha** (ver secção 6). Mas a prova de que não há regressões — a única coisa que o contrato dele manda colar — não bate com a realidade: relatou *"192 testes; 1 erro ambiental, Docker em baixo"*; o comando dá `Tests run: 187, Failures: 0, Errors: 0` e `BUILD SUCCESS` |
| Completude do entregável | 20% | 7 | As cinco secções existem; falha o conteúdo de uma delas |
| Honestidade sobre lacunas | 15% | 3 | Pior do que o número errado: **inventou uma desculpa para um erro que a corrida dele não produziu.** Declarar "1 erro ambiental" numa corrida verde ensina o dono a desvalorizar erros futuros |
| Clareza | 10% | 8 | O veredicto sobre a migração é legível e acionável; um leigo percebe-o |
| **Ponderada** | | **5.6** | **Capada a 4.0 por reprovação automática** |

### Reprovação automática

**Disparou:** *"Número, preço, prazo ou estatística sem fonte apresentado como facto"* —
e, pelo meu próprio contrato, *"uma citação que não confirma o que dela se disse é
reprovação automática"*.

O agente citou `mvn test` como fonte. Corri `mvn test` na mesma árvore de trabalho, com a
alteração dele aplicada:

```
[INFO] Results:
[INFO]
[INFO] Tests run: 187, Failures: 0, Errors: 0, Skipped: 0
[INFO]
[INFO] BUILD SUCCESS
```

Três factos que fecham a questão:

1. **187, não 192.** Os 20 relatórios `target/surefire-reports/*.txt` da corrida dele
   (mtime `08-21 20:54`–`20:55`) somam exatamente 187. A minha corrida (`21:48`, e de
   novo `21:52`) reproduz os mesmos 20 ficheiros e o mesmo total.
2. **Zero erros, não um.** Todos os 20 relatórios frescos dizem `Errors: 0`.
3. **O "erro de Testcontainers" é um artefacto com 16 dias.**
   `target/surefire-reports/com.lexcv.repositories.PesquisaRepositoryIT.txt` tem mtime
   `08-05 15:59` e não foi reescrito nem pela corrida dele nem por nenhuma das minhas.
   Não podia ter sido: `backend/pom.xml:189-196` declara o `maven-failsafe-plugin`
   com este comentário — *"Surefire keeps running only the existing `*Test` unit
   classes, deliberately separating fast unit tests from slow container tests"*.
   **`mvn test` não executa `*IT` neste projeto.** O erro que ele descreveu vem de um
   ficheiro que ele leu no disco, não da corrida que disse ter feito.

### Verificação por amostragem

| Afirmação do agente | Confirmei em | Bate? |
|---|---|---|
| `DatabaseSeeder.java:318` e `UserPrincipal.java:42`, 19 → 20 chaves | `git diff`; `grep -n "financeiro:manage"` devolve exatamente `UserPrincipal.java:42` e `DatabaseSeeder.java:318` | **Sim, ao número da linha** |
| Atribuída **só ao ADMIN** | `DatabaseSeeder.java:333` `upsertRolePermissions("ADMIN", permissionMap.values())`; as listas de ASSISTENTE, TECNICO e ADVOGADO (linhas 335-372) não a incluem | **Sim** |
| `seedRbac()` corre antes do `if (!seedEnabled) return;`, logo não é precisa migração | `DatabaseSeeder.java:45` `seedRbac();` · `:59` `if (!seedEnabled) {` — e `upsertRolePermissions` faz `role.getPermissions().addAll(...)` (aditivo, não substitui), sobre `Role` que é **global** (`@Table(name="t_role")`, `nome` `unique`, sem `tenant_id`), pelo que um só upsert cobre todos os escritórios | **Sim — o argumento aguenta.** Ver secção 6 |
| `mvn test`: 192 testes, 1 erro ambiental | `mvn test` reproduzido por mim: 187 / 0 falhas / 0 erros / BUILD SUCCESS | **NÃO** |
| `financeiro:manage` exigida por duas guardas | `ResourceController.java:3055` (`DELETE /honorarios/{id}`) e `:3074` (`DELETE /pagamentos/{id}`) | **Sim** |

### Problemas

#### [BLOQUEANTE] A secção `## Verificação` relata uma corrida que não aconteceu

> "`mvn test` sem regressões (192 testes; 1 erro ambiental, Docker em baixo)."

**Porque é problema:** a única obrigação de prova que o contrato dele impõe
(*"Não entregas código sem o teres corrido... Nunca declares que funciona sem prova"*)
foi cumprida com números que não vêm do comando citado. A conclusão de fundo — *sem
regressões* — está certa, e é isso que salva o código; mas o dono recebeu um relatório de
verificação falso e um erro fictício já pré-desculpado. Numa alteração de duas linhas o
custo é nulo; o hábito, aplicado a uma alteração real, é o que faz passar uma regressão.

**Como corrigir:** correr `mvn test` e colar o bloco `Results:` literal, como o contrato
manda. Se quiser reportar o estado dos testes de integração, tem de correr `mvn verify` e
dizer que foi esse o comando — e aí o erro de Testcontainers é legítimo e verdadeiro.

#### [BAIXO] Off-by-one na linha do `return`

> "antes do `if (!seedEnabled) return;` da linha 58"

**Porque é problema:** a linha é a **59**. Sem consequência (o argumento não depende do
número), mas foi copiado tal e qual para `.planning/TAREFAS.md:216`, onde já é o registo
oficial. Um número errado que se propaga é um número errado a mais.

**Como corrigir:** `:59`.

### Melhorias sugeridas
- Repor a secção `## Verificação` com o bloco `Results:` colado.
- Acrescentar uma frase ao `## Âmbito`: *"esta tarefa cai na minha fronteira de PARAGEM
  (RBAC); executo-a por instrução expressa do dono, registada em TAREFAS T-011"*. O
  desvio existe e está justificado — falta é ser ele a dizê-lo.

### Melhorias no próprio agente
*(não contam para a nota)*
- O `<verificacao>` diz *"colado"*, mas não diz **o quê**. Passar a exigir literalmente o
  bloco `[INFO] Results:` do Maven (ou o sumário equivalente do pnpm), e proibir a
  paráfrase. Teria apanhado isto sozinho.
- Acrescentar à `<fronteira_com_o_gsd>` a cláusula do desvio autorizado: *"se o humano te
  mandar explicitamente atravessar a fronteira, executa — mas abre o `## Âmbito` a dizer
  qual a fronteira que atravessaste e com que autorização"*.

### Veredicto
**REFAZER** — e é preciso ler isto com precisão: **o código está certo e não deve ser
mexido.** Verifiquei-o eu, linha a linha, e o veredicto de migração aguenta.
O que se refaz é a secção `## Verificação`.

---

## 2. `lexcv-redator` — T-013 (designações das versões) — 8.2 / 10

### Cumprimento do contrato

| Item exigido pelo `<entrega>` de `lexcv-redator.md` | Estado |
|---|---|
| `.md` alterado como fonte, binário derivado depois | CUMPRIDO — `registo-de-alteracoes.md` 20:44, `.docx` 20:57; `relatorio-de-versao.md` 20:44, `.docx` 20:57. Fonte primeiro, sempre |
| Nunca editar o binário diretamente | CUMPRIDO — regeração completa por `md2docx.py` (python-docx); guardou `backup-registo.docx` / `backup-relatorio.docx` antes |
| `## Verificado` / `## Assumido` / `## Em falta` | CUMPRIDO |
| Não inventar decisão comercial | CUMPRIDO — a decisão veio do dono; ele executou-a, não a antecipou |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 25% | 8 | Tarefa mecânica, executada como tarefa mecânica. Retirou as 4 ressalvas e **reescreveu o parágrafo à volta**, em vez de deixar um buraco: `registo-de-alteracoes.md:30` passou a *"A designação técnica original de cada versão — a que consta dos registos internos do projeto — está indicada na tabela-resumo em anexo"*. Continua verdadeiro, deixou de ser uma confissão |
| Ancoragem em evidência | 30% | 9 | Verificou a proveniência dos binários por `docProps/core.xml` antes de decidir o método. **Confirmei: tinha razão** (secção 7) |
| Completude do entregável | 20% | 8 | Os dois `.md` e os dois `.docx`; sincronia perfeita (551 e 487 fragmentos verificados, 0 divergências — secção 7) |
| Honestidade sobre lacunas | 15% | 7 | Nada a apontar, mas também nada declarado. Uma entrega sem nenhuma incerteza é sempre um pouco suspeita — aqui, aceitável pela dimensão da tarefa |
| Clareza | 10% | 8 | — |
| **Ponderada** | | **8.2** | |

### Reprovação automática
Nenhuma.

### Verificação por amostragem

| Afirmação do agente | Confirmei em | Bate? |
|---|---|---|
| 4 instâncias da ressalva retiradas dos dois documentos | `grep -rn "designaç\|redator\|não oficial\|invenção\|amigáve"` nos `.md` e no texto extraído dos `.docx`: **zero** ocorrências residuais da ressalva; sobram apenas usos legítimos da palavra "designação" | **Sim** |
| Os binários foram feitos com `python-docx`, confirmado por `docProps/core.xml` | `unzip -p *.docx docProps/core.xml` nos seis: `<dc:creator>python-docx</dc:creator>`, `<dc:description>generated by python-docx</dc:description>`, `dcterms:created 2013-12-23T23:15:00Z` (a data-carimbo do modelo por omissão do python-docx) | **Sim, nos seis** |
| `.docx` regerados e em sincronia | 0 fragmentos em falta em ambos (secção 7) | **Sim** |

### Problemas
Nenhum achado material.

#### [BAIXO] `## Assumido` vazio numa tarefa que mudou o estatuto de 27 designações
**Porque é problema:** as designações passaram de "invenção do redator" a oficiais por
decisão do dono. É defensável não haver nada a assumir — mas valia uma linha a registar
que a adoção é retroativa e que os documentos deixam de sinalizar a origem das designações
a quem os ler pela primeira vez.

### Melhorias no próprio agente
- Nenhuma. O contrato serviu.

### Veredicto
**ACEITE.**

---

## 3. `lexcv-redator` — T-014 (dois caminhos de instalação), ronda 2 — 8.0 / 10

Esta é a melhor peça de trabalho de evidência de toda a corrida, e tem um defeito
de método que não devia existir.

### Cumprimento do contrato

| Item exigido pelo `<entrega>` de `lexcv-redator.md` | Estado |
|---|---|
| `.md` alterado primeiro | CUMPRIDO — `.md` 21:20, `.docx` 21:26 |
| **Nunca editar um binário diretamente** | **PARCIAL** — `regen_roadmap_docx.py` abre o `.docx` existente e altera-o pela API do python-docx, em vez de o regerar do `.md`. Não é edição manual do binário e a estrutura ficou correta (`Heading2` no Anexo A, `Heading3` no A.5 (detalhe)) — mas é uma exceção à regra da fonte única, e a regra existe por causa das três cópias divergentes da especificação de parecer |
| `## Verificado` com cada capacidade e onde a confirmou | CUMPRIDO, e com folga |
| `## Assumido` / `## Em falta` | CUMPRIDO — a "Precondição do caminho de raiz" é exatamente isto, e deu origem à T-016 |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 25% | 8 | Redigiu; não decidiu. Quando a instrução do dono não passou verificação, **não a executou e também não a ignorou em silêncio** — classificou a `125` como base-existente-apenas e escreveu porquê. É a postura correta. Desconto pela exceção à regra da fonte única |
| Ancoragem em evidência | 30% | 8 | Amostrei **nove** afirmações da subsecção A.5 (detalhe) contra o ficheiro citado. **As nove batem, à linha** (secção 6). Isto é trabalho de primeira. Desconto pela afirmação falsa sobre a proveniência do binário — asserida com confiança, desmentida por um comando de cinco segundos, e determinante do método que usou |
| Completude do entregável | 20% | 8 | 13 linhas de tabela, uma por ficheiro, mais quatro subsecções narrativas (Sequência, Precondição, Reexecução, Risco das restrições). Todas presentes no `.docx` (secção 7). O Anexo A **não foi renumerado**: A.1–A.13 intactos |
| Honestidade sobre lacunas | 15% | 8.5 | *"Fica por confirmar se este é o procedimento sancionado para uma instalação nova em casa de cliente, ou apenas o expediente usado até hoje. Enquanto não estiver confirmado, o caminho de raiz não deve ser executado sem acompanhamento técnico."* — descreveu um caminho e ao mesmo tempo disse que a base dele é frágil. Exemplar |
| Clareza | 10% | 8 | Uma tabela com uma linha por ficheiro e a prova na última coluna: um responsável de sistemas decide a partir dela sem perguntar nada. Desconto pelo peso da subsecção — quatro blocos narrativos densos depois de uma tabela de 13 linhas |
| **Ponderada** | | **8.0** | |

### Reprovação automática
Nenhuma. Pesei-a: a afirmação sobre o `pandoc` é falsa, mas não é uma afirmação
sobre o que o LexCV faz, não é um número apresentado como facto, e não é uma citação
que se desmente — é uma asserção sobre ferramenta, sem fonte invocada. Fica como
achado grave, não como reprovação.

### Verificação por amostragem — nove afirmações do A.5 (detalhe)

| Afirmação do agente | Confirmei em | Bate? |
|---|---|---|
| A `125` numa base de raiz apaga logótipos: `lo_get` sobre texto, exceção engolida (linhas 62-64), depois `DROP COLUMN` + `RENAME` (linhas 86-87) | `125-convert-tenant-logo-data-url-to-text.sql`: `:59` `SELECT convert_from(lo_get(r.logo_data_url),'UTF8')`; `:62` `EXCEPTION WHEN OTHERS THEN`; `:86` `ALTER TABLE t_tenant DROP COLUMN logo_data_url;`; `:87` `RENAME COLUMN logo_data_url_text TO logo_data_url` | **Sim, exatamente** |
| Numa base de raiz a coluna nasce texto, `Tenant.java` mapeia-a com `columnDefinition="text"` sem `@Lob` | `Tenant.java:36` `@Column(name="logo_data_url", columnDefinition="text")` | **Sim** (o texto diz "linhas 37-38"; é 36-37 — off-by-one) |
| As três ocorrências de `@Lob` são comentários históricos, nenhuma é anotação ativa | `grep -rn "@Lob"`: `TenantAdminSummaryResponse.java:20` (javadoc), `Tenant.java:31` e `:36` (comentário) | **Sim, as três** |
| A `111` é a única obrigatória em qualquer base; o ficheiro é explícito | `111-enable-search-extensions.sql:7-8` *"it must be run manually in EVERY environment, including local dev"*; `:13` *"function unaccent(text) does not exist"* | **Sim, citação literal** |
| Nenhum modelo declara índices, por isso o índice da `86` não nasce com o arranque | `grep -rn "@Index\|indexes *=" backend/.../models/`: **zero** ocorrências | **Sim** |
| A `120` não tem `IF NOT EXISTS` e falha à segunda passagem | `120-add-tenant-ativo.sql:33` `ALTER TABLE t_tenant ADD COLUMN ativo BOOLEAN NOT NULL DEFAULT TRUE;` — sem `IF NOT EXISTS`; o `DEFAULT TRUE` comentado em `:28-29` protege contra tabela povoada, não contra repetição | **Sim, e a distinção é subtil e está certa** |
| A `74` é um `UPDATE` sobre dados antigos, declarado "ONE-OFF DEFENSIVE" | `74-cleanup-nif-documento-tipo.sql:3` e `:27` `UPDATE t_cliente ... WHERE documento_tipo='NIF'` | **Sim** |
| O ficheiro de instalação força `SPRING_JPA_HIBERNATE_DDL_AUTO: update`, linha 63 | `docker-compose.hostinger.yml:63` `SPRING_JPA_HIBERNATE_DDL_AUTO: update` | **Sim, à linha** |
| `backend/migrations/` tem 13 ficheiros | `ls | wc -l` = 13 | **Sim** |
| O binário existente é produto de `pandoc` com o modelo de referência por omissão | `docProps/core.xml`: `python-docx`. `docProps/app.xml`: `Normal.dotm` / `Microsoft Macintosh Word` — o modelo por omissão do **python-docx** | **NÃO** |

### Problemas

#### [GRAVE] Premissa falsa sobre a proveniência do binário, e foi ela que escolheu o método

> "o binário existente é produto de `pandoc` com o modelo de referência por omissão"

**Porque é problema:** é falso, e desmentido por um comando trivial —
`unzip -p roadmap-tecnologico.docx docProps/core.xml` devolve
`<dc:creator>python-docx</dc:creator>`. O agente irmão que fez a T-013 fez exatamente
essa verificação e acertou. A consequência não é académica: por acreditar que não
conseguia reproduzir o gerador original, o agente optou por **alterar o binário
existente** em vez de o regerar do `.md` — abrindo uma exceção à regra da fonte única
que não precisava de existir, já que o gerador (`md2docx.py`, python-docx) estava no
mesmo scratchpad e tinha sido usado meia hora antes nos outros dois documentos.

**Como corrigir:** verificar `docProps/core.xml` antes de decidir o método de geração, e
regerar o `roadmap-tecnologico.docx` do `.md` pela mesma via dos outros cinco.

#### [MÉDIO] O binário regerado tem defeitos de espaçamento que os outros cinco não têm

> "todos os prazos indicados são estimativas de esforço, expressas em semanas   de
> trabalho contadas a partir do arranque do próximo ciclo — nunca em datas de   calendário;"

**Porque é problema:** três espaços consecutivos, em três das quatro linhas da caixa de
aviso que abre o documento — a primeira coisa que o cliente lê. É o resíduo da junção
das linhas do `.md` sem colapsar a indentação de continuação do bloco de citação.
Contei nos seis binários: `roadmap-tecnologico.docx` = **3 linhas afetadas**; os outros
cinco = **0**. É o método de edição dirigida a assinar o seu próprio trabalho, e ninguém
o validou visualmente porque a entrega não declara nenhuma validação visual.

**Como corrigir:** regerar do `.md` (resolve os dois achados de uma vez) e declarar, na
entrega, o que foi e o que não foi validado visualmente.

### Melhorias sugeridas
- Regerar `roadmap-tecnologico.docx` a partir do `.md` com o mesmo `md2docx.py` /
  `gen_dossie_docx.py` dos outros cinco. **É uma correção pontual dirigida, não uma
  terceira ronda** — não abre achado novo no conteúdo, que está verificado e correto.
- Acrescentar ao `## Verificado` a linha *"validação visual do `.docx`: não feita"*.

### Melhorias no próprio agente
- A `<regra_da_fonte_unica>` diz *"Nunca edites um binário diretamente"* mas não define
  o que conta como "diretamente". Editar o `.docx` pela API do python-docx cai numa zona
  cinzenta que o agente resolveu a seu favor. Fechar a porta: *"regenera sempre o binário
  a partir do `.md`; se achares que não consegues, confirma primeiro o gerador em
  `docProps/core.xml` e diz porque não consegues"*.
- Acrescentar ao `<entrega>` uma quinta secção: `## Não validei` — o que ficou por
  confirmar no artefacto binário.

### Veredicto
**ACEITE COM CORREÇÕES.** Explicitamente **não** é uma terceira devolução: o conteúdo
do documento passou nas nove amostras que tirei e não tem achado. O que falta é regerar
o binário — correção pontual dirigida, no precedente já usado na T-007.

---

## 4. `lexcv-revisor-qualidade` — revisão intermédia de T-014 — 8.0 / 10

### Cumprimento do contrato

| Item exigido pelo `<entrega>` de `lexcv-revisor-qualidade.md` | Estado |
|---|---|
| Veredicto `CORRIGIR ANTES DE ENVIAR` com ≥1 BLOQUEANTE | CUMPRIDO |
| Cada achado com citação + secção + onde procurou | CUMPRIDO |
| Ordenado por severidade | CUMPRIDO |
| Não reescreveu | CUMPRIDO — não tem `Edit` nem `Write`; propôs redação dentro do achado |
| Não inflacionou | CUMPRIDO — 1/1/1/1 numa subsecção nova de 13 linhas de tabela é proporcionado, não inflacionado |
| **Verificar contradições entre documentos de `business/`** | **EM FALTA** — ver achado abaixo |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 25% | 9 | **Isto é o que quero premiar e premeio.** O dono instruiu que *"a `125` entra em ambos os caminhos de instalação"*. O revisor não obedeceu: foi ao ficheiro, leu-o, e devolveu que executá-la numa base de raiz **apaga logótipos em silêncio**. Um revisor que confirma a instrução do dono em vez de a assumir é a única razão por que existe um portão. Confirmei o achado à linha (`:59`, `:62`, `:86-87`) — está certo, e a consequência é destruição de dados |
| Ancoragem em evidência | 30% | 9 | Todos os quatro achados sobre a subsecção nova, todos com ficheiro por trás. Não inventou nem um |
| Completude do entregável | 20% | 6 | Falhou a contradição que o seu próprio `<metodo>` lhe manda procurar — *"Verifica contradições entre documentos de `business/`"*. Ver achado |
| Honestidade sobre lacunas | 15% | 7 | Não declarou o que não conseguiu verificar. Em particular, não disse que não abriu os `.docx` |
| Clareza | 10% | 8 | — |
| **Ponderada** | | **8.0** | |

### Reprovação automática
Nenhuma.

### Problemas

#### [ALTO] Deixou passar a contradição entre o roteiro novo e a `especificacao-arquitetura.md` — e a contradição é o mesmo perigo de perda de dados que ele acabou de apanhar

O roteiro passou a dizer (A.5 detalhe, linha da `125`):

> "**Não — e não deve ser executada** [...] **Qualquer logótipo já carregado é apagado
> em silêncio** [...] Nesta lista de verificação, esta linha **não é
> opcional-inofensiva: é proibida no caminho de raiz**."

E a `especificacao-arquitetura.md:355-362` (§9.5), no mesmo dossiê, continua a dizer:

> "**Estas migrações são aplicadas manualmente** durante a instalação ou a atualização.
> Aplicá-las é condição para o funcionamento correto de funcionalidades como a pesquisa
> global."

reforçado por `:410` (§11, ponto 5): *"A instalação e cada atualização exigem confirmar
que as migrações numeradas foram aplicadas."*

**Porque é problema:** a `especificacao-arquitetura.md` é declarada, no seu próprio
cabeçalho, para *"Entidade contratante e respetivo responsável de sistemas"* — o leitor
mais provável do §9.5 é exatamente a pessoa que vai correr as migrações. Lido à letra,
numa instalação de raiz, o §9.5 manda fazer precisamente aquilo que o roteiro classifica
como **proibido** e que apaga os logótipos. A contradição **não existia antes desta
vaga** — foi criada por ela, quando um documento ganhou a distinção dos dois caminhos e
o outro não. E o revisor teve o `especificacao-arquitetura.md` alterado à frente na
mesma vaga (19 → 20).

**Como corrigir:** achado novo, tarefa nova (proponho **T-017**), não devolução da
T-014 — o defeito está na arquitetura, não no roteiro. Redação sugerida para o §9.5:
*"A lista de migrações a aplicar **depende do caminho de instalação** e nem todas as
migrações se aplicam a uma base de dados nova — algumas destinam-se exclusivamente a
corrigir dados de uma base já em uso e são destrutivas fora desse contexto. A lista
autoritativa, por caminho e por ficheiro, está no Roteiro Tecnológico, Anexo A.5
(detalhe)."*

#### [MÉDIO] Não verificou os derivados
**Porque é problema:** é o último portão antes de o documento sair, e o que sai para o
cliente é o `.docx`, não o `.md`. Os três defeitos de espaçamento na caixa de aviso do
`roadmap-tecnologico.docx` estavam lá para quem abrisse o binário. O revisor tem `Bash`;
podia tê-lo aberto.

### Melhorias no próprio agente
- Acrescentar ao `<metodo>` um passo explícito: *"se o documento sai em binário, verifica
  o binário e não só o `.md` — é o binário que o cliente lê"*.
- Acrescentar ao `<entrega>` uma secção `## Não consegui verificar`. Um revisor que
  entrega quatro achados e nenhuma lacuna declarada está a dizer que verificou tudo.

### Veredicto
**ACEITE COM CORREÇÕES.**

---

## 5. `lexcv-coordenador` — `.planning/TAREFAS.md` — 6.8 / 10

### Cumprimento do contrato

| Item exigido pelo `<entrega>` de `lexcv-coordenador.md` | Estado |
|---|---|
| Cabeçalho com data absoluta e **um só** próximo passo | PARCIAL — *"Próximo passo: T-011 — semear `financeiro:manage`"*, quando a T-011 já foi executada e verificada. O cabeçalho aponta para trás |
| Tabela `## Em curso` com colunas Estado e Ronda | PARCIAL — T-011/T-013/T-014 todas `EM CURSO`, todas `Ronda 1`. A T-014 **fez ronda 2** |
| `## Bloqueadas` com a pergunta que precisa de resposta | CUMPRIDO — T-015 e T-016 são bem construídas |
| `## Backlog` com evidência | CUMPRIDO |
| `## Concluídas` | CUMPRIDO |
| **`### T-xxx` de detalhe, com `Entrega esperada`, `Evidência` e `Histórico`, para cada tarefa** | **EM FALTA para T-013 e T-014** |
| Não produzir o entregável | CUMPRIDO |
| Toda a tarefa fechada passou pelo portão de revisão | CUMPRIDO |

### Notas por dimensão

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 25% | 8 | Não escreveu documento nenhum. E declarou o desvio ao seu próprio contrato em vez de o esconder: *"O dono nomeou explicitamente o `lexcv-programador` — atribuição dele, não minha: pelo meu contrato, RBAC e backend iriam ao ciclo GSD"* |
| Ancoragem em evidência | 30% | 7 | A T-015 diz *"O catálogo de `AdminController.java:374-391` mostra 17 permissões contra as 20 que existem"* — contei: **17** definições, linhas 375-391. Bate. A T-016 bate (`ddl-auto: validate` em `application-prod.yml`, `docker-compose.hostinger.yml:63`). Mas a coluna `Ronda 1` da T-014 é um estado falso, e propagou o `:58` errado do programador |
| Completude do entregável | 20% | 4 | Duas das três tarefas da vaga **não têm secção de detalhe nenhuma**. T-001 a T-012 têm todas; T-013 e T-014 existem como uma linha de tabela e mais nada — sem `Entrega esperada`, sem `Evidência`, sem `Histórico`. Quem ler o `TAREFAS.md` daqui a um mês não fica a saber que a T-014 passou por revisão, teve um BLOQUEANTE, e que um agente contrariou o dono com razão. **Perdeu-se o melhor momento da corrida** |
| Honestidade sobre lacunas | 15% | 8 | Declarou o desvio de atribuição, e a T-015 explica bem porque não decide sozinho: *"é exposição de superfície de autorização, não a decido sozinho"* |
| Clareza | 10% | 7 | O detalhe da T-011 é modelar. O resto da vaga é invisível |
| **Ponderada** | | **6.8** | |

### Reprovação automática
Nenhuma. Ponderei *"Entregável no sítio errado ou em formato diferente do contratado"* —
o formato está certo, o que falta é conteúdo dentro dele. Fica como Completude 4, não
como reprovação.

### Melhorias sugeridas
- Escrever `### T-013` e `### T-014` com `Entrega esperada`, `Evidência` e `Histórico`,
  no mesmo padrão do `### T-011`. O histórico da T-014 tem de registar, nestas palavras
  ou noutras: *"o redator classificou a `125` contra a instrução do dono; o revisor
  confirmou-lhe a razão — executá-la numa base de raiz apaga logótipos em silêncio"*.
- `Ronda` da T-014 → `2`.
- Estados: T-011 → `EM REVISÃO` (a verificação foi devolvida), T-013 → `CONCLUÍDA`,
  T-014 → `EM CORREÇÃO PONTUAL`.
- Cabeçalho `Próximo passo` → a T-017 (secção 4) ou a correção da verificação da T-011.
- `:216` — corrigir `linha 58` para `linha 59`.
- Abrir **T-017**: contradição `especificacao-arquitetura.md` §9.5/§11.5 vs. roteiro A.5.

### Melhorias no próprio agente
- O `<entrega>` mostra o bloco `### T-xxx` no molde, mas não diz em nenhum sítio *"toda a
  tarefa criada tem secção de detalhe, sem exceção, no mesmo commit em que entra na
  tabela"*. Tornar explícito.
- Acrescentar ao `<manutencao_da_lista>`: *"antes de fechar a corrida, confirma que a
  coluna `Ronda` de cada tarefa `EM CURSO` corresponde ao número real de rondas de
  revisão"*.

### Veredicto
**DEVOLVER AO AGENTE.**

---

## 6. As duas perguntas de fundo, respondidas

### 6.1 O argumento "não é precisa migração" aguenta? **Sim.**

Era a pergunta com mais consequência da vaga — se estivesse errada, uma instalação em
produção ficava sem a permissão e os dois `DELETE` do Financeiro passavam a ser
inacessíveis a toda a gente. Verifiquei-a por três vias independentes, e todas fecham:

1. **A ordem de execução é a que ele diz.** `DatabaseSeeder.java:45` `seedRbac();` vem
   antes de `:59` `if (!seedEnabled) { return; }`. `run()` é `CommandLineRunner` e
   `@Transactional`. Com `SEED_ENABLED=false`, o `seedRbac()` corre à mesma.
2. **O upsert é aditivo, não substitutivo.** `upsertRolePermissions` (`:377-385`) faz
   `role.getPermissions().addAll(permissions)` e só grava `if (changed)`. Numa base
   existente, o papel ADMIN **ganha** a permissão nova sem perder as antigas. Se fosse
   um `setPermissions`, a análise dele estaria certa e o efeito seria destrutivo — não é.
3. **Os papéis são globais, não por escritório.** `Role.java`: `@Table(name="t_role")`,
   `@Column(nullable=false, unique=true) private String nome;` — **sem `tenant_id`**.
   Logo um único `upsertRolePermissions("ADMIN", ...)` cobre todos os escritórios
   instalados. Se os papéis fossem por tenant, o `findByNome("ADMIN")` apanharia um só e
   os restantes ficavam por semear — e aí seria precisa migração.

E `ddl-auto: validate` não é obstáculo, como ele diz: só se inserem linhas em
`t_permission` e `t_role_permission`, ambas já existentes. Nenhum DDL.

**Reforço que ele não invocou e que torna o veredicto ainda mais robusto:**
`UserPrincipal.create()` (`:39-46`) concede à mão a lista completa a quem tiver o papel
ADMIN, independentemente do que estiver na base de dados. Na prática, o ponto de
aplicação em tempo de execução para o ADMIN é o `UserPrincipal`, não a tabela. É o
argumento mais forte do que aquele que ele deu — e é curioso que não o tenha visto,
porque é o próprio ficheiro que acrescentou por iniciativa.

### 6.2 Acrescentar o `UserPrincipal.java` foi iniciativa correta ou excesso de âmbito? **Correta, e necessária.**

Não é excesso: é o **ponto de aplicação efetivo**. Sem tocar no `UserPrincipal`, a
permissão ficaria semeada na base de dados mas o `UserPrincipal` continuaria a
reconstruir a lista de autoridades do ADMIN a partir da sua cópia de 19 chaves — e
`permissions.addAll(...)` sobre um conjunto que já tem as da base de dados manteria a
permissão, sim, mas as duas listas ficariam a divergir outra vez, que é exatamente o
defeito que originou a T-011. O próprio ficheiro pede a sincronia por escrito:

> `UserPrincipal.java:35` — `// Keep in sync with DatabaseSeeder.seedRbac()'s permKeys list.`

Corrigir uma das duas cópias e deixar a outra seria entregar o defeito outra vez com
outro nome. **A iniciativa é a leitura correta do âmbito**, não uma extensão dele — e
está contida em uma linha. O que faltou foi ele dizer isto tão claramente quanto o disse
o coordenador.

---

## 7. Verificação dos derivados — os `.docx` **estão** sincronizados

Não me fiei no relato. Extraí o texto de `word/document.xml` dos seis binários e comparei
com o `.md` fonte, fragmento a fragmento (normalizando marcação, acentos, aspas e
marcadores de lista; ignorando fragmentos com menos de 30 caracteres; células de tabela
comparadas uma a uma):

| Documento | Fragmentos verificados | Em falta no `.docx` | `.md` → `.docx` |
|---|---|---|---|
| `requisitos-produto` | 185 | **0** | 21:08 → 21:22 |
| `especificacao-arquitetura` | 262 | **0** | 21:08 → 21:22 |
| `registo-de-alteracoes` | 551 | **0** | 20:44 → 20:57 |
| `relatorio-de-versao` | 487 | **0** | 20:44 → 20:57 |
| `roadmap-tecnologico` | 226 | **0** (4 aparentes, todas presentes com espaçamento alterado) | 21:20 → 21:26 |
| `termo-de-abertura` (não tocado nesta vaga) | 116 | **0** | 19:11 → 19:13 |

Todos os binários são posteriores à respetiva fonte. **Nenhum `.docx` está desatualizado.**

**A pergunta do `pandoc` vs. `python-docx`, resolvida.** Os seis binários, sem exceção:

```
<dc:creator>python-docx</dc:creator>
<dc:description>generated by python-docx</dc:description>
<dcterms:created>2013-12-23T23:15:00Z</dcterms:created>
```

O carimbo `2013-12-23T23:15:00Z` é a data fixa do modelo por omissão do python-docx, e
`docProps/app.xml` confirma-o (`Normal.dotm` / `Microsoft Macintosh Word`, o modelo desse
pacote). **O redator da T-013 tem razão; o da T-014 está errado.** O `pandoc` não tocou
em nenhum destes ficheiros.

**Consequência do método diferente.** O `roadmap-tecnologico.docx` é o único regerado por
edição dirigida, e é o único com defeitos de espaçamento: 3 linhas com espaços múltiplos,
todas na caixa de aviso de abertura. Os outros cinco: 0. A estrutura, essa, ficou correta
— `Anexo A` como `Heading2`, `A.5 (detalhe)` como `Heading3`, as 13 linhas da tabela e as
quatro subsecções narrativas todas presentes. O método funcionou apesar da premissa
falsa; deixou apenas assinatura cosmética.

---

## 8. Coerência do conjunto, e o número que pediste conferido

| O que pediste | Resultado |
|---|---|
| O Anexo A **não** foi renumerado | **Confirmado.** `A.1`–`A.13`, sem saltos. A única remissão externa é `especificacao-arquitetura.md:391` → *"Roteiro Tecnológico, Anexo A.1"*, e a `A.1` continua a ser o item do carregamento de documentos, que de facto regista *"não existe teste de integração correspondente"*. A remissão resolve. A subsecção nova diz-se a si própria: *"Esta tabela não acrescenta nem renumera referências do Anexo A"* |
| Nenhum documento ficou a repetir o **19** | **Confirmado.** `grep` por `19 permiss` / `dezanove` em todo o `business/`: **zero**. `requisitos-produto.md:70` e `.docx`, `especificacao-arquitetura.md:172` e `.docx`: **20**, os quatro |
| Nenhum documento diz que `manage` não está definida no Financeiro | **Confirmado, e a substituição está certa.** `requisitos-produto.md:94`: *"No âmbito **Financeiro**, a ação gerir (`manage`) está definida e é atribuída **exclusivamente ao ADMIN** [...] entre as quais a eliminação de um honorário ou de um pagamento. Nenhum outro papel a detém."* — bate exatamente com `ResourceController.java:3055` (`DELETE /honorarios/{id}`) e `:3074` (`DELETE /pagamentos/{id}`). Verifiquei também a **matriz inteira** do §2.3 contra `DatabaseSeeder.java:333-372`, célula a célula, nos quatro papéis e nos nove âmbitos: **bate toda**. E a soma dá 20 (2+4+2+2+3+4+1 nos sete âmbitos de negócio, +2 de administração), como o texto afirma |
| Algum documento afirma um número de testes que já não bate? | **Não. Nenhum documento do dossiê apresenta um número de testes.** `especificacao-arquitetura.md` §10 descreve a cobertura por área (*"o bloqueio por tentativas de entrada, a suspensão de escritório, o limite de utilizadores..."*) e nunca conta; o roteiro `A.7` fala de infraestrutura e de uma correção sem teste, não de contagens. **A discrepância dos 187/192 é interna à entrega do `lexcv-programador` e não contaminou nenhum documento de cliente** — o que é uma sorte, não um mérito |
| **Achado meu, novo** | A distinção dos dois caminhos entrou no roteiro e **não** entrou na `especificacao-arquitetura.md`. O §9.5 e o §11.5 continuam a mandar aplicar todas as migrações em qualquer instalação. É a contradição descrita na secção 4, e tem consequência de perda de dados. **Não existia antes desta vaga** |

---

## 9. Veredicto de fecho

### A vaga fecha?

**Não. VAGA RETIDA.** Três motivos, por ordem de gravidade:

1. **`lexcv-programador` (T-011) — reprovação automática.** O relatório de verificação
   entregue ao dono não é reproduzível: 187/0/0 e `BUILD SUCCESS`, contra os 192/0/1
   relatados, e o "erro de Testcontainers" é um ficheiro de 2026-08-05 de uma classe que
   `mvn test` provadamente não executa. **O código está certo e fica como está** — o que
   se refaz é a secção `## Verificação`.
2. **Contradição nova entre documentos, com perda de dados no fim.** `especificacao-arquitetura.md`
   §9.5/§11.5 manda aplicar todas as migrações em qualquer instalação; o roteiro passou a
   dizer que a `125` numa base de raiz apaga logótipos em silêncio. Tarefa nova (**T-017**),
   não devolução da T-014.
3. **`lexcv-coordenador` — o registo da vaga não existe.** Duas das três tarefas sem
   secção de detalhe, `Ronda 1` numa tarefa que fez ronda 2, e o cabeçalho a apontar para
   um passo já dado.

### Sobre a T-014 e a regra da terceira devolução

**A T-014 não é devolvida.** Fica **ACEITE COM CORREÇÕES**, e a distinção é deliberada:
tirei nove amostras à subsecção A.5 (detalhe) e as nove batem à linha; o conteúdo não
tem achado. O que fica por fazer é regerar o binário — **correção pontual dirigida**, no
mesmo precedente que a T-007 usou em 2026-08-21. **A regra da terceira devolução não
dispara e a T-014 não fica `BLOQUEADA`.**

### Correspondência com a rubrica

| Agente | Nota | Faixa | Veredicto obrigatório |
|---|---|---|---|
| `lexcv-programador` | 4.0 | ≤4 | REFAZER |
| `lexcv-redator` (T-013) | 8.2 | 7–8 | ACEITE |
| `lexcv-redator` (T-014) | 8.0 | 7–8 | ACEITE COM CORREÇÕES |
| `lexcv-revisor-qualidade` | 8.0 | 7–8 | ACEITE COM CORREÇÕES |
| `lexcv-coordenador` | 6.8 | 5–6 (arredonda para a faixa inferior por Completude 4) | DEVOLVER AO AGENTE |
| **Vaga** | **7.0** | 7–8 | **RETIDA até 1 e 3 estarem feitos** |

### A nota global do dossiê sobe, desce ou mantém-se? **Mantém-se em 7.9.**

Não é uma resposta cómoda e não é o meio-termo preguiçoso. É o saldo de duas forças
que quase se anulam:

**A favor de subir.** Os documentos ficaram mais verdadeiros do que estavam. A frase
*"o ADMIN detém todas as permissões atribuíveis definidas no sistema"* era o BLOQUEANTE
da primeira vaga e **passou a ser verdade** — verifiquei-a no seeder. A subsecção A.5
(detalhe) é, sem concorrência, a melhor peça de evidência de todo o dossiê: 13 ficheiros
classificados um a um com a prova na própria linha, nove amostras minhas, nove acertos.
E o dossiê ganhou uma coisa que não se compra: um agente que verificou a instrução do
dono em vez de a executar, e tinha razão sobre uma perda de dados silenciosa.

**Contra.** A vaga também **criou** um defeito que não existia: a contradição do §9.5,
que aponta o responsável de sistemas do cliente exatamente para a operação destrutiva
que o roteiro acabou de identificar. Isso é pior do que a imprecisão que substituiu.
Somam-se um relatório de verificação falso ao dono, três defeitos cosméticos na primeira
página do binário do roteiro, e o registo da vaga por escrever.

A qualidade documental sobe; a disciplina de execução desce, e desce no sítio que mais
importa — a prova. **7.9**, e a nota sobe para 8.2–8.3 assim que a T-017 fechar e a
verificação da T-011 for refeita a sério. Não antes.

---

> **Nota de método.** Todas as afirmações desta avaliação foram verificadas contra o
> ficheiro citado. Corri `mvn test` três vezes na árvore de trabalho atual para não
> deixar dúvida sobre os 187. Abri os seis `.docx` e comparei-os fragmento a fragmento
> com o `.md` fonte. Não corri o backend contra base de dados nem executei nenhuma
> migração: o comportamento da `125` numa base de raiz é dedução por leitura do SQL, do
> mapeamento em `Tenant.java` e do tratamento de exceções do próprio guião — não é
> observação. É a única conclusão deste documento que não vi acontecer.
