# Avaliação — equipa (`lexcv-redator`, `lexcv-editor`, `lexcv-revisor-qualidade`) · Dossiê de documentação T-007 / T-008 / T-009
**Data:** 21 de agosto de 2026 · **Nota final da equipa: 7.9 / 10**

> **Desvio ao meu próprio contrato, declarado.** O `<entrega>` manda
> `<AAAA-MM-DD>-<agente>-<slug>.md`, um agente por ficheiro. Isto é um portão
> conjunto sobre um dossiê único: separar em três ficheiros parte a análise de
> coerência do conjunto, que é metade do que foi pedido. Uso `equipa` no lugar de
> `<agente>` e dou nota autónoma a cada um. Fica assinalado, não escondido.

| Agente | Nota | Veredicto |
|---|---|---|
| `lexcv-redator` (3 instâncias, 2 rondas) | **8.5** | ACEITE COM CORREÇÕES |
| `lexcv-editor` (3 instâncias) | **7.6** | ACEITE COM CORREÇÕES |
| `lexcv-revisor-qualidade` (2 instâncias) | **7.5** | ACEITE COM CORREÇÕES |
| **Equipa (ponderada 3/3/2 por volume)** | **7.9** | **DOSSIÊ RETIDO — uma correção de uma linha** |

---

## 1. `lexcv-redator` — 8.5 / 10

### Cumprimento do contrato
| Item exigido pelo `<entrega>` | Estado |
|---|---|
| O `.md` como fonte, nos destinos | CUMPRIDO — seis `.md` em `business/documentacao/` |
| O binário derivado, mesmo nome base, mesma pasta | CUMPRIDO — seis `.docx`, todos com data de modificação **posterior** ao respetivo `.md` (ver §5) |
| Nunca editar o binário diretamente | CUMPRIDO — o conteúdo dos `.docx` reproduz o dos `.md`, incluindo as correções da ronda 2 |
| Nenhuma capacidade descrita sem estar entregue | PARCIAL — uma afirmação sobrevive falsa (ver Problema 1) |
| Secção `Assumido` preenchida a sério | CUMPRIDO — designações «amigáveis» declaradas como invenção própria; exposição de correções de segurança devolvida ao dono |
| Linha nova para `documentacao/` no `business/README.md` (T-009) | CUMPRIDO — `business/README.md:15` |
| Roteiro em tabela (T-009) | CUMPRIDO — 7 tabelas + Anexo A de rastreabilidade |

### Notas por dimensão
Pesos com a **Ancoragem a dobrar**, conforme a rubrica (`lexcv-redator` → dominante Ancoragem):
25/60/20/15/10, normalizados para 19.2 / 46.2 / 15.4 / 11.5 / 7.7 %.

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 19.2% | 9 | Não decidiu o que não lhe compete e devolveu-o: as designações «amigáveis» das versões e a exposição a cliente das correções de segurança ficaram **por decidir pelo dono**. E recusou prosa que não passou verificação — retirou «exportação» de Processos, onde o botão existe mas é `disabled` (`web/src/app/(dashboard)/processos/page.tsx:258`), mantendo-a em Financeiro, onde é real (`financeiro/page.tsx:60,183`). Discriminar entre dois botões com o mesmo rótulo é exatamente o trabalho. |
| Ancoragem em evidência | 46.2% | 8 | Amostrei 15 afirmações; 14 confirmam ao ficheiro e à linha (ver §4). Uma falha, e é material: `requisitos-produto.md:70`. |
| Completude do entregável | 15.4% | 9 | Seis `.md` + seis `.docx` + a linha do `README`. O único item por entregar do contrato é a nota de revisão por advogado, que não se aplica: nenhum destes seis é minuta. |
| Honestidade sobre lacunas | 11.5% | 9 | `registo-de-alteracoes.md:957-958` regista v1.5 e v1.6 como *«(sem entrega registada)»* em vez de as inventar. A coluna «Origem da data» distingue etiqueta de versão, registo de planeamento e arquivo. E o Anexo A.5 fecha com *«Esta contagem é aferida à data de emissão e deve ser reconfirmada antes de cada instalação»* — uma contagem que se declara perecível. |
| Clareza | 7.7% | 9 | A caixa de topo do Roteiro é inequívoca: *«nenhum prazo deste documento é uma data contratada»*, e explica porquê — «não existe nenhum ciclo de desenvolvimento seguinte formalmente aberto». |
| **Ponderada** | | **8.5** | |

### Reprovação automática
**Nenhuma.** O caso-limite é `requisitos-produto.md:70`. Analisei-o contra o
critério «número **sem fonte** apresentado como facto» e **não dispara**: o
número 19 tem fonte real e correta (`DatabaseSeeder.java:312-324`, 19 chaves em
`permKeys`). O defeito é de *atribuição* — o número certo colado ao sítio errado —
e é grave, mas não é invenção. Trata-se como achado, não como reprovação.

### O que merece ser dito
Três coisas separam este trabalho de um trabalho meramente competente:

1. **Corrigiu duas premissas erradas do próprio dono, com prova.** O bug de fuso
   horário da Agenda, dado como aberto, já estava fechado em `master`. E a
   instrução «4 migrações obrigatórias» estava a menos: são **5**. Verifiquei —
   `backend/migrations/` tem 13 ficheiros, e `125-convert-tenant-logo-data-url-to-text.sql:4`
   declara-se a si próprio *«This is a REQUIRED manual production migration script»*.
   O Anexo A.5 não se limita a somar 5: separa-as em dois grupos (as que impedem o
   arranque sob `ddl-auto: validate`, e a `120b`, que só é obrigatória sobre base
   de dados com escritórios pré-existentes). Distinção que muda a lista de
   verificação de instalação.
2. **Encontrou um erro numérico que nenhum revisor apanhou, e que jogava a favor
   do fornecedor.** A decisão de produto está pendente desde a **v2.10**, não a
   v2.11. Confirmei: `.planning/STATE.md:174` — *«Items acknowledged and deferred
   at milestone v2.10 close on 2026-07-10»*, e `v2.11-MILESTONE-AUDIT.md:68`
   descreve-a como arrastada *«across 3 milestones now»*. O texto anterior
   encurtava a pendência num ciclo inteiro. Um agente que corrige um número
   contra o interesse de quem o paga é raro o suficiente para se registar.
3. **Rastreou uma designação que não estava onde seria óbvio.** A coluna de
   designações originais bate com os cabeçalhos de `MILESTONES.md` em todas as
   linhas — exceto v1.9, cujo cabeçalho é o inútil `## v1.9 v1.9` (`:256`). O valor
   «Melhoria Módulo Agendamento» vem da anotação da etiqueta git (`git tag -n99 v1.9`).
   Foi preciso ir procurar noutro sítio; foi.

### Problemas
#### [GRAVE] O número de permissões do ecrã de Controlo de Acesso está errado
> «A matriz de controlo de acesso da aplicação apresenta **19 permissões
> atribuíveis**, distribuídas pelos sete âmbitos de negócio e pelos dois âmbitos
> de administração.» — `requisitos-produto.md:70`

**Porque é problema:** são dois factos diferentes colados num só. O conjunto
*atribuível* tem 19 chaves (`DatabaseSeeder.java:312-324`). O **ecrã** apresenta
**17**: é alimentado por `systemPermissions` (`AdminController.java:374-390`),
que enumerei uma a uma — 2 Clientes, 2 Processos, 2 Agenda, 2 Documentos,
2 Financeiro, 4 Pareceres, 1 Notificações, 1 RBAC, 1 Utilizadores. Faltam-lhe
`processos:create` e `processos:manage`. Confirmei o caminho até ao ecrã:
`web/src/app/(dashboard)/settings/page.tsx:193-194` consome `useAdminRbac()` e
lê `rbacData.systemPermissions`.

O erro não é isolado. Encadeia-se:
- `requisitos-produto.md:64-65` reforça a atribuição errada — *«o nome técnico é
  o que consta da matriz de controlo de acesso apresentada na aplicação»*;
- a tabela de `requisitos-produto.md:76` atribui a Processos «consultar, criar,
  editar, gerir» — as duas ações que o cliente **não** vai encontrar nesse ecrã;
- `especificacao-arquitetura.md:172` diz a mesma coisa de forma correta —
  *«O conjunto efetivamente atribuível é de 19 permissões»* — pelo que os dois
  documentos do mesmo dossiê discordam sobre o significado do mesmo número.
  Pela tabela de severidade do próprio revisor, isto é `ALTO`: contradição entre
  documentos de `business/`.
- e o erro passou ao binário: `requisitos-produto.docx` contém «19 permissões».

O agravante é de processo, não de redação: este facto **já estava apurado** dentro
da equipa. `TAREFAS.md`, T-011, «Achado ligado, encontrado na ronda 2»:
*«o catálogo RBAC de `AdminController.java:375-391` declara 17 definições de
permissão contra as 19 de `permKeys`»*. A equipa tinha o facto em mãos na ronda 2
e não o ligou à frase que o dossiê estava a publicar.

**Como corrigir:** uma substituição, mais a regeneração de um `.docx`. Texto
proposto para `requisitos-produto.md:70` — o redator que decida a redação final:

> O conjunto de permissões atribuíveis é de **19**, distribuídas pelos sete
> âmbitos de negócio e pelos dois âmbitos de administração. O ecrã de Controlo
> de Acesso apresenta atualmente **17** dessas permissões: as ações `criar` e
> `gerir` do âmbito Processos existem e são aplicadas, mas ainda não constam da
> lista apresentada nesse ecrã.

E ajustar `:64-65`, trocando «o nome técnico é o que consta da matriz de controlo
de acesso apresentada na aplicação» por «o nome técnico é o da convenção
`âmbito:ação` usada pela plataforma».

#### [BAIXO] Data da v1.0 diverge da etiqueta, embora declarada
> «| v1.0 | Versão Mínima Viável (MVP) | MVP | 26/05/2026 | Registo de marcos |»

A etiqueta `v1.0` tem data `2026-05-27`; `MILESTONES.md:302` diz
`Shipped: 2026-05-26`. A coluna «Origem da data» declara qual foi usada, o que
salva a afirmação. Fica registado porque o total «65 dias» depende dela e nenhum
leitor externo vai reconciliar as duas.

### Melhorias sugeridas
- Aplicar a correção de `requisitos-produto.md:70` e `:64-65`, e **regerar apenas**
  `requisitos-produto.docx`. Nada mais precisa de ser tocado.
- Depois de aplicada, verificar que `especificacao-arquitetura.md:172` e
  `requisitos-produto.md:70` dizem a mesma coisa sobre o mesmo número.

### Melhorias no próprio agente (não contam para a nota)
- O `<verdade_antes_de_prosa>` manda verificar a capacidade. Não manda verificar
  **onde o cliente a vai ver**. Foi essa a diferença entre 19 e 17. Sugiro
  acrescentar: *«Quando descreveres uma contagem que o cliente possa conferir no
  ecrã, verifica o que o ecrã renderiza, não só o que a base de dados guarda.»*
- O contrato já obriga a `Assumido`. Falta-lhe o simétrico: uma linha a exigir
  que achados de código levantados por outro agente na mesma ronda sejam
  confrontados com o texto próprio antes de fechar. O facto do T-011 estava
  escrito e não foi cruzado.

---

## 2. `lexcv-editor` — 7.6 / 10

### Cumprimento do contrato
| Item exigido pelo `<entrega>` | Estado |
|---|---|
| Editar o mesmo `.md` no sítio, nunca uma cópia | CUMPRIDO — não há ficheiros duplicados em `business/documentacao/` |
| Não inventar conteúdo | **PARCIAL** — ver Problema 1 |
| Não verificar factos contra o código | **PARCIAL** — mesma causa |
| Marcar sem corrigir o que não pode resolver | CUMPRIDO, e bem — devolveu listas de dúvidas em vez de as fechar |
| Não gerar binários | CUMPRIDO |
| Terminologia do domínio | CUMPRIDO |

### Notas por dimensão
Pesos com a **Fidelidade a dobrar** (rubrica: `lexcv-editor` → dominante
Fidelidade): 50/30/20/15/10 → 40 / 24 / 16 / 12 / 8 %.

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 40% | 6.5 | Preencheu uma coluna factual inteira — «Designação técnica original» — que exige ir a `MILESTONES.md` e à anotação de etiquetas git buscar valores. Isso é apurar facto, e o contrato exclui-o em duas linhas separadas («Não inventas conteúdo» e «Não verificas factos contra o código»). O parágrafo de enquadramento acrescentado tem o mesmo problema de fronteira, em grau menor. |
| Ancoragem em evidência | 24% | 8 | Onde amostrei, a coluna bate: v2.16 «Distribuição Multi-Tenant e Faturação por Utilizadores» = `MILESTONES.md:3`; v1.7 «Melhoria no modulo de gestao e acompanhamento de processos [sic]» = `:284`, gralha incluída. *Sourced*, não inventada. Foi por isso que não disparei reprovação automática. |
| Completude do entregável | 16% | 8 | O trabalho está no ficheiro e é verificável. Não pude auditar o formato exato da resposta final (não a tenho), pelo que não a pontuo nem a favor nem contra. |
| Honestidade sobre lacunas | 12% | 9 | É a melhor parte. Devolveu **dúvidas de facto** em vez de as resolver, e a prova é indireta mas sólida: o revisor conseguiu **desmentir** um achado do editor. Só se desmente uma dúvida que foi apresentada como dúvida. Um editor que tivesse «corrigido» por conta própria não deixaria nada para desmentir. |
| Clareza | 8% | 9 | O aviso de estimativas promovido a caixa de topo é a decisão editorial mais valiosa do dossiê. |
| **Ponderada** | | **7.6** | |

### Reprovação automática
**Nenhuma**, e por pouco. O critério é «editor a inventar conteúdo». Verifiquei
os valores da coluna contestada contra a fonte antes de decidir: todos batem,
incluindo o caso difícil (v1.9, cujo cabeçalho em `MILESTONES.md:256` é o inútil
`## v1.9 v1.9`, e cujo nome real está na anotação da etiqueta). **Foi invasão de
âmbito, não invenção.** A distinção é o que separa 7.6 de 4.

### O que merece ser dito
- **A harmonização dos títulos de versão é real e verifiquei-a eu.** Extraí os
  26 cabeçalhos `## v` de `registo-de-alteracoes.md` e de `relatorio-de-versao.md`
  e corri `diff`: **idênticos, byte a byte**. Não aceitei o relato do revisor;
  refiz o teste.
- **O tratamento das estimativas está completo, não amostrado.** Extraí as 12
  linhas das três tabelas da secção 4 do Roteiro e li célula a célula: 8 começam
  por «Estimativa:», as outras 4 dizem «Sem estimativa» — e são exatamente as 4
  de estado «Depende de decisão» ou «Adiado por decisão». Não há uma única célula
  de esforço sem qualificação. É trabalho de forma que muda o risco contratual do
  documento.

### Problemas
#### [GRAVE] Preencheu uma coluna de facto, que é trabalho do redator
> Coluna «Designação técnica original», `registo-de-alteracoes.md:939-963`

**Porque é problema:** a coluna tem 27 células com nomes técnicos que só existem
em `MILESTONES.md` e em anotações de etiquetas git. Populá-la é investigação de
facto. O contrato do editor fecha essa porta duas vezes, e o desenho da
ferramenta reforça-o — *«não tens `Write`, só `Edit`. Foi de propósito.»* A
consequência não foi teórica: esta coluna gerou **dois achados do revisor**, ou
seja, custou tempo de portão a jusante. O editor resolveu um problema de
apresentação criando um problema de verificação.

**Como corrigir:** nada a fazer neste dossiê — a coluna está correta e verificada,
retirá-la agora seria perda líquida. A correção é de comportamento, na próxima
tarefa: marcar a coluna em falta na secção «Marcado para o redator» e devolvê-la.

### Melhorias sugeridas
- Nenhuma sobre o texto. O trabalho de forma está bom e verificado.

### Melhorias no próprio agente (não contam para a nota)
- O `<o_que_marcas_sem_corrigir>` lista o que se marca: parágrafo oco, afirmação
  duvidosa, contradição, secção em falta. **Não lista «coluna ou campo por
  preencher»** — e foi precisamente essa a lacuna por onde o editor saiu do
  âmbito. Acrescentar uma quinta alínea: *«Célula, coluna ou campo vazio cujo
  preenchimento exija consultar código, marcos ou histórico — marca, não
  preenchas, mesmo que o valor pareça óbvio.»*
- Acrescentar ao `<limite_de_intervencao>` o teste operacional: *«Se para
  escrever a alteração tiveste de abrir um ficheiro fora de `business/`,
  ultrapassaste a fronteira.»* É verificável, ao contrário de «preservas a
  intenção».

---

## 3. `lexcv-revisor-qualidade` — 7.5 / 10

### Cumprimento do contrato
| Item exigido pelo `<entrega>` | Estado |
|---|---|
| Não escrever ficheiros; devolver na resposta | CUMPRIDO |
| Não reescrever | CUMPRIDO — não tem `Edit` nem `Write`; os achados foram aplicados pelo redator |
| Cada achado com evidência (ficheiro:linha ou marco) | CUMPRIDO em geral; uma falha (ver Problema 2) |
| Ordenar por severidade | CUMPRIDO — contagens reportadas por nível |
| Não inventar problemas | PARCIAL — um achado não reproduz |
| Detetar contradição **entre** documentos de `business/` | **EM FALTA** — ver Problema 1 |
| Declarar o âmbito não coberto | CUMPRIDO |

### Notas por dimensão
Pesos com a **Ancoragem a dobrar** (rubrica: `lexcv-revisor-qualidade` →
dominante Ancoragem): 19.2 / 46.2 / 15.4 / 11.5 / 7.7 %.

| Dimensão | Peso | Nota | Justificação (com citação) |
|---|---|---|---|
| Fidelidade à função | 19.2% | 9 | Reportou e bloqueou, não corrigiu. E fez a coisa mais difícil do papel: **desmentiu um achado do editor** em vez de o aceitar por deferência. Um revisor que valida achados alheios por economia deixa de ser portão. |
| Ancoragem em evidência | 46.2% | 6.5 | O método é bom e verifiquei-o (ver §4). Mas o portão deixou passar o único erro factual vivo do dossiê, **tendo o facto em mãos na mesma ronda**. E um dos seus achados não reproduz. |
| Completude do entregável | 15.4% | 7 | Declarou o que não cobriu, o que vale. Mas `requisitos-produto.md:70` estava dentro do âmbito e não foi gateado. E uma instância só entregou depois de lhe ser pedido um relatório parcial — um portão que é preciso ir buscar não é um portão. |
| Honestidade sobre lacunas | 11.5% | 9 | Declarar por escrito o âmbito que não se cobriu é a virtude mais difícil deste papel, e foi exercida. |
| Clareza | 7.7% | 8 | Achados com ficheiro e linha, severidade e sugestão de redação. Desconto pelo custo de obtenção. |
| **Ponderada** | | **7.5** | |

### Reprovação automática
**Nenhuma.** Testei o critério mais provável — «citação que não confirma o que
dela se disse». As citações do revisor que consegui rastrear confirmam. A falha
do *off-by-one* (Problema 2) é o inverso: um achado cuja **evidência não se
sustenta**, o que a rubrica não classifica como reprovação, mas o contrato do
próprio revisor condena («Não inventes problemas para justificar a revisão»).

### O que merece ser dito
**O teste de hipótese contrária foi feito a sério, e refiz-o.** Antes de validar
a frase *«deixa de ver a mensagem de erro que hoje surge sempre que abre o
módulo»* (`roadmap-tecnologico.md:106`), o revisor foi verificar se o erro
chegaria sequer ao ecrã. Confirmei em `web/src/lib/api.ts:41-44`: o `toast.error`
só dispara quando o estado **não** é 401 nem 403. Um 403 seria silenciado e a
frase seria falsa. Mas `STATE.md`, Blockers/Concerns v2.13/Phase 108, regista
*«spurious 500 error toast on every Pareceres page load»* — 500 não é filtrado,
logo a frase sobrevive. Procurar ativamente a prova de que a afirmação que se está
prestes a aprovar é **falsa** é o que distingue um revisor de um leitor atento.

### Problemas
#### [GRAVE] Deixou passar o único erro factual vivo, tendo o facto em mãos
> `requisitos-produto.md:70` — «A matriz de controlo de acesso **da aplicação**
> apresenta 19 permissões atribuíveis»

**Porque é problema:** na mesma ronda 2, o revisor apurou e fez registar em
`TAREFAS.md` T-011 que *«o catálogo RBAC de `AdminController.java:375-391`
declara 17 definições de permissão contra as 19 de `permKeys`»* — e que *«o ecrã
de Controlo de Acesso mostra menos duas permissões do que existem»*. O facto
correto foi apurado, escrito, e encaminhado para o backlog de código; a frase do
documento que ele contradiz ficou por gatear. É um erro de ligação, não de
investigação, e é o mais caro que este papel pode cometer: o portão inspecionou a
fechadura e não olhou para a porta.

Agrava-se por ser também um achado `ALTO` por omissão: o método do revisor manda
expressamente *«Verifica contradições entre documentos de `business/`»*, e
`especificacao-arquitetura.md:172` diz corretamente *«O conjunto efetivamente
atribuível é de 19 permissões»* enquanto o PRD atribui as mesmas 19 ao ecrã. Dois
documentos do mesmo dossiê, o mesmo número, dois significados. Não foi levantado.

**Como corrigir:** o achado tem de ser emitido agora, com a redação de
substituição proposta na secção 1.

#### [MÉDIO] Achado de localização de migrações que não reproduz
> Alegado: as migrações `117`/`120` não estariam em `STATE.md:144-145`;
> a `74` estaria confirmada em `:143`.

**Porque é problema:** verifiquei diretamente. `.planning/STATE.md` tem
`74-cleanup-nif-documento-tipo.sql` na linha **143**, `117-add-tenant-plano-limite-utilizadores.sql`
na **144**, `120-add-tenant-ativo.sql` na **145** e `120b-backfill-tenant-plano.sql`
na **146** — exatamente como o Anexo A.5 do Roteiro as cita. As quatro citações
resolvem. Ou a evidência do revisor estava errada, ou foi lida contra um estado
anterior do ficheiro sem o declarar. O contrato dele é explícito: *«Um achado sem
evidência não é um achado»* e *«Inflacionar achados é tão mau como falhá-los.»*

**Como corrigir:** ao citar linha, citar também o estado do ficheiro contra o
qual se leu, ou reconfirmar antes de emitir.

#### [MÉDIO] Entrega que foi preciso ir buscar
> Uma das instâncias só produziu depois de lhe ser pedido um relatório parcial.

**Porque é problema:** o valor de um portão é ser previsível. Um revisor que
exige ser interrompido para entregar transfere para o humano o custo de gestão
que o agente devia absorver — e neste caso o humano estava a gerir uma trava
anti-ciclo com contagem de rondas.

**Como corrigir:** ver a proposta de alteração ao contrato, abaixo.

### Melhorias sugeridas
- Emitir o achado sobre `requisitos-produto.md:70` com a redação de substituição,
  para que a correção seja aplicada sem uma ronda completa.

### Melhorias no próprio agente (não contam para a nota)
- O `<metodo>` percorre o documento e vai ao código. **Falta-lhe o sentido
  inverso**: cada facto novo descoberto durante a revisão deve ser cruzado contra
  o texto que se está a gatear. Foi exatamente essa passagem que faltou.
  Acrescentar: *«Todo o facto que apurares durante a revisão — mesmo que o
  encaminhes para outra tarefa — tem de ser procurado no documento antes de
  fechares o veredicto.»*
- Acrescentar às `<regras>`: *«Citação de linha caduca. Reconfirma cada
  `ficheiro:linha` contra o estado atual antes de emitir o achado.»*
- Acrescentar ao `<entrega>` um ponto de controlo intermédio: *«Se ao fim de
  metade do âmbito ainda não tiveres veredicto, emite o que tens como relatório
  parcial, sem esperar que to peçam.»*

---

## 4. Verificação por amostragem — 15 afirmações rastreadas à fonte

| Afirmação do agente | Confirmei em | Bate? |
|---|---|---|
| «`backend/migrations/` — 13 ficheiros» (A.5) | `ls backend/migrations/` → 13 | **Sim** |
| «5 identificadas como obrigatórias» (A.5) | `STATE.md:143,144,145,146` + `125-*.sql:4` | **Sim** |
| A `125` é obrigatória e não consta do registo de pendências | `125-convert-tenant-logo-data-url-to-text.sql:4` — *«REQUIRED manual production migration script»*; ausente de `STATE.md` | **Sim** |
| «pendente desde o fecho da versão v2.10, a 10 de julho de 2026» | `STATE.md:174` — *«deferred at milestone v2.10 close on 2026-07-10»* | **Sim** |
| «arrastada across 3 milestones now» (A.13) | `v2.11-MILESTONE-AUDIT.md:68` | **Sim** |
| A.13 — origem na Phase 85, que pertence à v2.10 | `v2.10-ROADMAP.md:170`, `v2.10-MILESTONE-AUDIT.md:67`, `STATE.md:141` e `:178` | **Sim** |
| A.1 — `Documento.java` não segue o padrão de `ParecerVersao.java` | `Persistable` ocorre 0× em `Documento.java`, 2× em `ParecerVersao.java:5,19`; não existe `DocumentoRepositoryIT` | **Sim** |
| A.2 — `useAdminUsers()` sem guarda em 3 ficheiros de Pareceres | `pareceres/page.tsx:84`, `nova/page.tsx:72`, `[id]/page.tsx:144` | **Sim** |
| A.3 — prioridade sem valor inicial no elemento de seleção | `pareceres/nova/page.tsx:249-253` (sem `defaultValue`, 1.ª opção `ALTA`) vs. formulário `:64` `prioridade: "MEDIA"` | **Sim** |
| Matriz de permissões por papel (PRD §2.3) | `DatabaseSeeder.java:333-367`, linha a linha, 4 papéis × 9 âmbitos | **Sim** |
| «19 permissões atribuíveis» **na matriz da aplicação** | `permKeys` = 19 (`DatabaseSeeder.java:312-324`), mas o ecrã lê `systemPermissions` = **17** (`AdminController.java:374-390` → `settings/page.tsx:194`) | **NÃO** |
| Datas v1.9 / v2.12 / v2.16 | `git log -1 --format=%ai` → 2026-06-17 / 2026-07-15 / 2026-07-30 | **Sim** |
| «25 versões entregues em 65 dias, entre 26 de maio e 30 de julho» | 26/05 → 30/07 = 65 dias; 27 linhas menos 2 «sem entrega registada» | **Sim** |
| «O último registo de alteração no repositório é de 5 de agosto de 2026» | `git log -1` → `2026-08-05 af0ec7c0` | **Sim** |
| «Adiamento de lembrete com predefinições de 1, 3 e 7 dias» | `notificacao-snooze-control.tsx:15-18` | **Sim** |
| «24 horas e 30 dias» para os cookies | `AuthController.java:138-139` — `86400` / `2592000` | **Sim** |

**15 de 16 verificações passam.** A densidade de ancoragem deste dossiê é alta e
não é decorativa: onde fui verificar, encontrei o que estava prometido.

---

## 5. Verificação dos derivados — os `.docx` **estão** regenerados

Não me fiei no relato. Li as datas de modificação e extraí o texto de
`word/document.xml` de cada um dos seis binários.

| Documento | `.md` | `.docx` | Derivado posterior? |
|---|---|---|---|
| `registo-de-alteracoes` | 17:23:36 | 17:32:41 | Sim |
| `relatorio-de-versao` | 17:31:39 | 17:32:46 | Sim |
| `requisitos-produto` | 19:10:26 | 19:13:11 | Sim |
| `termo-de-abertura` | 19:11:33 | 19:13:07 | Sim |
| `especificacao-arquitetura` | 19:12:43 | 19:13:14 | Sim |
| `roadmap-tecnologico` | 19:18:35 | 19:19:34 | Sim |

A data ordenada não prova conteúdo, por isso procurei as marcas da ronda 2
**dentro** dos binários:

- `roadmap-tecnologico.docx` contém «5 identificadas como obrigatórias» (2×),
  «120b» (1×), «v2.10» (6×), «A.1» (10×) e «Estimativa:» (**8×** — o mesmo número
  exato de células qualificadas no `.md`);
- `registo-de-alteracoes.docx` e `relatorio-de-versao.docx` contêm cada um a
  remissão «A.1»;
- `requisitos-produto.docx` contém «19 permissões» — ou seja, **o erro do
  Problema 1 também está no binário**, e o `.docx` terá de ser regerado com a
  correção.

**Conclusão: a regra da fonte única foi respeitada nesta ronda.** Os seis
binários refletem os `.md` atuais. A desatualização que ocorreu uma vez não se
repetiu.

---

## 6. Coerência do conjunto — as três remissões resolvem

**As três remissões apontam ao mesmo sítio e esse sítio existe:**

| Origem | Texto | Destino |
|---|---|---|
| `registo-de-alteracoes.md:268` | «ver Roteiro Tecnológico, item A.1» | `roadmap-tecnologico.md:177` |
| `relatorio-de-versao.md:232` | «ver Roteiro Tecnológico, item A.1» | idem |
| `especificacao-arquitetura.md:391` | «O Roteiro Tecnológico, Anexo A.1, identifica um caso concreto» | idem |

**A numeração do Anexo A não foi mexida.** Enumerei as entradas: A.1 a A.13,
sequenciais, sem saltos nem repetições. As alterações da ronda 2 concentraram-se
**dentro** de A.5 (a contagem de migrações, que passou a 5 em dois grupos) e de
A.13 (a origem v2.10) — nenhuma acrescentou ou removeu uma entrada, pelo que
nenhum identificador se deslocou. É por isso que `registo-de-alteracoes.docx` e
`relatorio-de-versao.docx`, gerados às 17:32 e portanto **antes** da última
edição do Roteiro (19:18), continuam a remeter corretamente: o alvo A.1 não se
moveu.

**A remissão é semanticamente correta, e não apenas sintaticamente.** As três
origens falam de um defeito grave e ainda aberto no carregamento de documentos;
A.1 documenta exatamente isso — `STATE.md`, Blockers/Concerns, v2.13/Phase 107 —
e verifiquei o facto no código: `Documento.java` não implementa `Persistable`,
`ParecerVersao.java:19` implementa, e não existe teste de integração
correspondente. A correção está mesmo fora da linha principal, tal como o dossiê
afirma.

**Coerência de números entre documentos:** uma discordância, já descrita —
`especificacao-arquitetura.md:172` («conjunto efetivamente atribuível é de 19»)
contra `requisitos-produto.md:70` («a matriz da aplicação apresenta 19»). É a
mesma causa do Problema 1 e resolve-se com a mesma linha.

**Coerência de títulos:** as 26 designações de versão de `registo-de-alteracoes.md`
e `relatorio-de-versao.md` são idênticas byte a byte (`diff` limpo). Os dois
documentos são legíveis lado a lado sem que o leitor tropece num nome diferente
para a mesma versão.

**Coerência de jargão:** os termos técnicos que sobrevivem — `frontend`,
`backend`, `tenant_id` — estão confinados a `especificacao-arquitetura.md`, que os
define expressamente em `:28-35` como nomes que «aparecem na configuração da
instalação». `Multi-Tenant` e `Deployment` só aparecem em
`registo-de-alteracoes.md:941,959`, dentro da coluna de designações originais,
declarada como reprodução literal. Nenhum jargão solto nos restantes quatro
documentos.

---

## 7. Veredicto de fecho

### **DOSSIÊ RETIDO — por uma linha, não por uma ronda**

Cinco dos seis documentos podem sair para cliente hoje. O sexto,
`requisitos-produto.md`, tem uma afirmação falsa sobre o produto na secção que um
cliente contratante lê com mais atenção — a matriz de permissões — e essa
afirmação está também no `.docx`.

**O que retém, exatamente:**
1. `requisitos-produto.md:70` e `:64-65` — o número 19 atribuído ao ecrã de
   Controlo de Acesso, que apresenta 17. Corrigir e **regerar apenas**
   `requisitos-produto.docx`.

**Como fechar sem disparar a trava anti-ciclo.** Isto **não deve ser encaminhado
como terceira devolução ao redator** — transformaria a tarefa em `BLOQUEADA` por
uma frase cuja correção já está escrita, e o custo cairia sobre o dono por um
defeito de contagem de rondas, não de qualidade. Recomendo tratá-lo como
**correção pontual dirigida**, com o texto de substituição já fixado na secção 1,
aplicada e verificada contra `AdminController.java:374-390`. Se o processo não
admitir correção fora de ronda, então a decisão de contornar a trava é do dono e
tem de ser tomada explicitamente — não a tomo por ele.

**Uma decisão do dono que continua em aberto, e que não é defeito de ninguém.**
`TAREFAS.md` T-011 assinala corretamente que a escolha entre semear
`financeiro:manage` ou baixar as duas guardas de `ResourceController.java:3055,3074`
para `financeiro:edit` **muda a matriz do PRD §2.3**. Se a decisão for «semear»,
passam a ser 20 permissões e `requisitos-produto.md` fica desatualizado no dia em
que sair. Confirmei que o risco é real e que hoje as duas operações — eliminar
honorário e eliminar pagamento — estão inalcançáveis por qualquer papel. Confirmei
também que **o PRD não promete essas operações**: `requisitos-produto.md:190-193`
descreve registo de honorários e pagamentos, nunca eliminação. Não há promessa
falsa; há apenas uma matriz que pode envelhecer. É uma decisão a tomar, não um
achado a corrigir.

### Correspondência com a rubrica
| Agente | Nota | Faixa | Veredicto |
|---|---|---|---|
| `lexcv-redator` | 8.5 | 7–8 | ACEITE COM CORREÇÕES |
| `lexcv-editor` | 7.6 | 7–8 | ACEITE COM CORREÇÕES |
| `lexcv-revisor-qualidade` | 7.5 | 7–8 | ACEITE COM CORREÇÕES |
| **Equipa** | **7.9** | 7–8 | **ACEITE COM CORREÇÕES** |

---

## 8. Nota lateral ao `lexcv-coordenador` (fora do âmbito pedido, mas material)

`TAREFAS.md` está desalinhado com o estado real e isso tem consequência direta na
trava anti-ciclo. O ficheiro regista **T-007 «EM REVISÃO», ronda 1**, **T-009
«EM REVISÃO», ronda 1** e **T-008 «DEVOLVIDA», ronda 2** — enquanto o percurso
efetivo colocou os três na ronda 2. A trava que decide entre «mais uma ronda» e
`BLOQUEADA` lê esta contagem. Um contador errado numa direção deixa passar uma
quarta ronda; na outra, bloqueia trabalho que ainda tinha margem. Antes de
qualquer decisão de fecho, a contagem de rondas devia ser reposta a partir do
histórico real. Não pontuo o coordenador aqui — não foi o que me foi pedido — mas
não podia gatear uma decisão de fecho e omitir que o registo que a sustenta está
desatualizado.
