# ALCv — Relatório de Versão

**Produto:** ALCv — plataforma de gestão de escritórios e instituições jurídicas (Cabo Verde)
**Documento:** Relatório de versão para o cliente contratante (*release notes*)
**Data de emissão:** 21 de agosto de 2026
**Última versão coberta:** v2.16 (30 de julho de 2026)

---

## Para que serve este documento

O ALCv evoluiu ao longo de **25 versões entregues em 65 dias**, entre 26 de maio
e 30 de julho de 2026. Este relatório percorre essas versões, da mais recente para
a mais antiga, e responde a uma pergunta de cada vez: **o que é que passou a ser
possível fazer?**

O foco é o benefício para quem usa a plataforma no dia a dia:

- o **advogado**, que trabalha processos, prazos e pareceres;
- o **escritório**, enquanto equipa que partilha clientes e informação;
- o **gestor do escritório**, que controla acessos, utilizadores e faturação.

Para o detalhe item a item — funcionalidades novas, melhorias e correções por
versão — consulte o documento complementar, o **Registo de Alterações**.

Todas as afirmações deste relatório correspondem a funcionalidade efetivamente
entregue e registada nos documentos internos de execução do projeto. Onde uma
versão fechou com uma limitação conhecida, essa limitação é referida.

### Designações das versões

Cada versão é identificada pela sua designação e pelo número técnico
correspondente. São as mesmas designações usadas no **Registo de Alterações**,
cuja tabela-resumo indica, ao lado de cada uma, a designação técnica original —
para quem precise de cruzar estes documentos com os registos internos do projeto.

A frase em itálico sob cada título é a leitura de benefício dessa versão, e não um
nome alternativo.

---

## Panorama — quatro andamentos em 65 dias

1. **Fundação (v1.0 – v1.2)** — os seis módulos base e a passagem de protótipo a
   servidor real.
2. **Solidez (v1.3 – v1.9)** — segurança, isolamento entre escritórios,
   profundidade na gestão de processos e clientes, e entrada em serviço
   permanente.
3. **Alargamento (v2.0 – v2.9)** — financeiro, agenda avançada, telemóvel, ficha
   de cliente, parecer jurídico e aprofundamento do processo.
4. **Maturidade (v2.10 – v2.16)** — notificações, coerência de interface, presença
   pública e, por fim, a capacidade de servir vários escritórios a partir de uma
   só instalação.

Um padrão atravessa todas elas: **em quase todas as versões, a auditoria de fecho
encontrou defeitos e eles foram corrigidos antes da entrega**, não depois. Vários
desses defeitos eram anteriores à própria versão em que foram encontrados. É esse
o processo que sustenta o que este relatório afirma.

O percurso versão a versão começa aqui.

---

## v2.16 — Distribuição Multi-Escritório e Faturação por Utilizadores
**30 de julho de 2026** · *O ALCv passa a servir vários escritórios*

**Em resumo:** até esta versão, cada escritório precisava da sua própria
instalação da plataforma. A partir daqui, um escritório novo é criado a partir de
uma consola, sem instalação, e o número de utilizadores que tem passa a ser um
dado controlado — e faturável.

### Para o gestor da plataforma

- **Passou a ser possível criar um escritório novo sem instalar nada.** Existe
  agora uma consola interna onde se cria o escritório e o respetivo administrador
  inicial, se consulta a lista de escritórios com o grau de utilização de cada um,
  se ajusta o plano e o limite de utilizadores, e se suspende ou reativa um
  escritório.
- **Suspender um escritório tem efeito imediato.** Quem estiver com sessão aberta
  perde o acesso em cerca de um segundo — não é preciso esperar que termine a
  sessão. Este comportamento foi medido e comprovado com duas sessões
  independentes, no ambiente de desenvolvimento. Depende da pré-condição de
  instalação descrita no fim desta secção.
- **Passou a existir um relatório de utilização por escritório** — nome, plano,
  limite contratado e utilizadores ativos, incluindo escritórios suspensos — que
  permite emitir a faturação sem consultar a base de dados.

### Para o gestor do escritório

- **O limite de utilizadores contratado é agora aplicado pela plataforma.** Ao
  tentar criar um utilizador acima do limite, a criação é recusada com uma
  mensagem clara.
- **Passou a ver quantos utilizadores tem e quantos pode ter.** A gestão de
  utilizadores mostra um indicador "X/Y utilizadores" e, quando o limite é
  atingido, o botão de criação fica desativado com a explicação visível — por
  rato e por teclado.

### Para o escritório

- **A matriz de permissões deixou de poder ser alterada por engano.** Passa a ser
  gerida centralmente pela ALCv, garantindo que a alteração feita por um
  escritório nunca afeta outro. Quem tenha o papel de administrador vê agora a
  indicação "Gerido pela Plataforma".
- **Um administrador de escritório não consegue escalar os seus próprios
  privilégios** até ao papel de administração da plataforma. As barreiras foram
  desenhadas e testadas explicitamente para isso.
- Foi realizada uma **auditoria dedicada ao isolamento de dados** sobre as três
  superfícies novas desta versão, concluída antes de entrar em serviço um segundo
  escritório pagante.

*Limitações registadas:* três atualizações à base de dados introduzidas por esta
versão têm de ser aplicadas manualmente antes da entrada em serviço, porque a
plataforma ainda não as aplica sozinha. Uma delas só foi aplicada no ambiente de
desenvolvimento; enquanto não for aplicada numa instalação já existente, a
primeira suspensão de um escritório criado antes desta versão falha. É uma
pré-condição de instalação, não um defeito do produto. Além disso, a verificação
automática ficou concluída, mas seis cenários de verificação manual em navegador
não puderam ser executados por um bloqueio da ferramenta de automação — também
aqui, não por defeito do produto.

---

## v2.15 — Reposicionamento Institucional (SIJ)
**27 de julho de 2026** · *Enquadramento institucional correto*

**Em resumo:** a forma como o ALCv se apresenta publicamente passou a estar
correta e a deixar de expor a identidade de terceiros.

- **A plataforma passou a apresentar-se como alinhada ao ecossistema do SIJ
  (Sistema Judicial de Cabo Verde)**, na página pública e na documentação do
  produto.
- **Deixou de haver identidade de terceiros nos documentos gerados por qualquer
  instalação nova.** A auditoria desta versão descobriu que o nome que aparecia na
  página pública era o mesmo que aparecia no cabeçalho da aplicação a cada
  utilizador autenticado — e, mais importante, em dois documentos imprimíveis
  entregues a clientes: a Ficha de Cliente e o Termo de Honorários. Uma única
  alteração corrigiu os dois canais de origem, e com eles todos os seis pontos
  onde o nome era apresentado. Instalações já em serviço antes desta versão
  exigem uma atualização pontual do registo do escritório.

*Nota importante:* esta versão corrigiu apenas o posicionamento institucional.
**Não foi construída nenhuma integração técnica com o SIJ**, e nenhuma está
planeada.

---

## v2.14 — Melhorias de Interface e Pesquisa Global
**22 de julho de 2026** · *Encontrar qualquer coisa em segundos*

**Em resumo:** deixou de ser preciso saber em que módulo está a informação para a
encontrar.

### Para o advogado

- **Passou a ser possível pesquisar tudo a partir de qualquer ecrã**, com um
  atalho de teclado (Ctrl+K, ou ⌘K), sem interromper o trabalho. A pesquisa cobre
  clientes, processos, documentos e pareceres em simultâneo.
- **A pesquisa perdoa acentos e mostra primeiro o que interessa** — resultados
  exatos e por início de palavra antes dos parciais — com o termo destacado e os
  resultados agrupados por tipo.
- **As últimas pesquisas ficam à mão** durante a sessão de trabalho.
- **"Ver todos" leva o termo pesquisado para a lista completa** de cada módulo,
  sem ter de escrever outra vez.
- **Passou a ser possível abrir um processo ou um parecer a partir da ficha do
  cliente**, já com o cliente preenchido e bloqueado no formulário — menos passos
  e menos hipótese de o associar ao cliente errado.

### Para o escritório

- **Filtrar processos por estado deixou de exigir abrir o painel de filtros
  avançados** — o controlo está agora sempre visível.
- Todos os botões da aplicação passaram a ter ícone, e os botões de filtro
  (aplicar, limpar, exportar) tornaram-se mais compactos em Clientes, Processos,
  Agenda, Documentos e Financeiro, com explicação ao passar o rato.
- **O botão "Entrar" do sítio público passou a funcionar corretamente**,
  encaminhando para o ecrã de início de sessão.

*Limitação registada:* dois critérios de sucesso relativos ao aspeto visual dos
cantos arredondados ficaram apenas parcialmente confirmados em verificação ao
vivo.

---

## v2.13 — Renovação do Sistema de Design da Interface
**18 de julho de 2026** · *Uma interface consistente em todo o produto*

**Em resumo:** os ecrãs deixaram de ser cada um à sua maneira. Foi a maior
intervenção de interface do projeto — 10 fases e 86 tarefas.

### Para quem usa a aplicação todos os dias

- **As cinco listas principais passaram a comportar-se da mesma forma.** Clientes,
  Processos, Pareceres, Financeiro e Documentos partilham agora a mesma tabela,
  com ordenação por coluna, paginação e escolha das colunas que quer ver.
- **Escolher datas na Agenda passou a ser feito num calendário**, em português e
  com a semana a começar ao domingo, em vez de escrever a data à mão.
- **Filtrar documentos passou a ser feito por um campo pesquisável**, e o tipo de
  documento aceita valores novos sem esperar por uma alteração no sistema.
- **A Ficha de Cliente (7 separadores) e a Ficha de Processo (8 separadores)
  passaram a ser navegáveis por teclado**, com separadores a sério em vez de
  botões.
- **Os ecrãs passaram a dizer quando estão a carregar e quando não há dados**, em
  vez de mostrarem espaço vazio — a começar pelo Painel.
- **Os ícones sem legenda passaram a explicar-se** ao passar o rato, na barra
  lateral e nas ações de linha.
- **O modo escuro passou a distinguir corretamente as superfícies** — cartões,
  caixas de diálogo e tabelas.
- **O histórico de versões de um parecer passou a recolher as versões antigas**,
  deixando visível o que interessa.
- **O sítio institucional público ganhou navegação em telemóvel**, que
  simplesmente não existia.

### Correções com impacto direto

- **Foi encontrada e corrigida uma vulnerabilidade real na exportação para CSV**
  de Financeiro e Clientes, que permitiria a execução de fórmulas na folha de
  cálculo aberta a partir do ficheiro exportado.
- **Deixou de aparecer um "Acesso negado" momentâneo** ao entrar na aplicação. O
  defeito afetava todos os utilizadores no primeiro carregamento e foi corrigido
  no Painel, nos dez ecrãs de Clientes e Processos e nas quatro páginas da Agenda.
- **A lista de Documentos passou a mostrar nomes** em vez de identificadores
  internos.
- Corrigido o desaparecimento silencioso de um tipo de documento antigo ao
  cancelar uma alteração de tipo de cliente.

*Limitações registadas:* a verificação ao vivo das permissões dos papéis ADVOGADO
e ASSISTENTE não foi concluída por instabilidade do ambiente de teste; e foi
detetado e sinalizado em separado um defeito grave, anterior a esta versão, no
carregamento de documentos, que **está registado e continua em aberto** — ver
Roteiro Tecnológico, item A.1.

---

## v2.12 — Sítio Institucional Público
**15 de julho de 2026** · *O ALCv passa a ter presença pública*

**Em resumo:** o produto ganhou uma cara para o exterior, sem abrir qualquer porta
para os dados do escritório.

- **Passou a existir uma página institucional pública** — Apresentação,
  Funcionalidades, Confiança Institucional e Contacto — com modo claro e escuro,
  servida por uma aplicação separada da aplicação de trabalho.
- **A página mostra a marca sem expor dados.** Apenas o nome e o logótipo são
  disponibilizados publicamente, através de uma resposta construída para o efeito
  — nunca o registo completo do escritório. Nenhum dado pessoal fica acessível
  sem autenticação.
- A validação foi feita com o sistema completo em execução, e não por partes
  isoladas. Revelou dois defeitos reais, corrigidos antes da entrega, que uma
  verificação isolada não teria apanhado.

---

## v2.11 — Auditoria Técnica e Notificações Avançadas
**14 de julho de 2026** · *Notificações que respeitam o utilizador*

**Em resumo:** o sistema de alertas deixou de ser tudo ou nada.

*Nota de fonte:* o registo interno de marcos fechou esta versão **sem qualquer
lista de realizações**. O que se segue provém da lista interna de capacidades
validadas, que atribui oito entradas a esta versão, uma por fase.

### Para o advogado

- **Passou a ser possível silenciar categorias de notificação** que não lhe
  interessam — com uma exceção deliberada: **um prazo vencido é sempre entregue**,
  mesmo com a categoria silenciada. A rotina diária respeita a mesma regra.
- **Passou a ser possível adiar um lembrete de prazo** por 1, 3 ou 7 dias. O
  lembrete reaparece automaticamente no fim do período, e a rotina diária não o
  duplica entretanto.

### Para o escritório

- **As notificações de processo passaram a chegar a toda a equipa** de advogados e
  administrativos ligada ao cliente — entrada de fase, novo documento, atribuição
  — e não apenas ao responsável único. Deixou de haver informação **de processo**
  retida numa só pessoa. Os avisos diários de risco de prazo, evento e honorário
  continuam dirigidos ao responsável do processo e ao administrador.
- **A Agenda passou a concordar com os restantes módulos sobre o que é um prazo
  crítico.** Era a quinta forma diferente de fazer o mesmo cálculo; passou a haver
  uma só.

### Garantias de qualidade acrescentadas nesta versão

- Análise automática de segurança do código a correr sem falhas.
- Primeira infraestrutura de testes contra uma base de dados real, integrada nas
  verificações obrigatórias de cada alteração.
- Verificação com utilizador fechada retroativamente para oito versões
  anteriores, cerca de 40 cenários.

---

## v2.10 — Notificações e Alertas
**10 de julho de 2026** · *O escritório passa a ser avisado*

**Em resumo:** o sino de notificações deixou de ser um contador de eventos de
agenda e passou a ser um sistema real de alertas.

### Para o advogado

- **Passou a ser avisado quando algo acontece no seu trabalho**, e não só quando
  consulta a agenda: entrada de nova fase num processo, novo documento, processo
  atribuído e parecer atribuído.
- **Passou a receber um aviso diário de risco**, gerado automaticamente todas as
  manhãs às 06:00 (hora de Cabo Verde), sobre prazos, eventos e honorários em
  risco. O sistema não repete o mesmo aviso para o mesmo nível de risco.
- **Passou a haver um histórico completo de notificações** numa página própria,
  com filtros por categoria e por estado de leitura, e paginação — deixou de ser
  preciso apanhar o aviso a tempo.
- **Um clique no sino marca como lida e leva ao sítio certo**, num só passo.

### Para o escritório

- **Passou a ser possível reatribuir o responsável de um processo** através de um
  fluxo dedicado, que não existia antes, com aviso automático ao novo responsável.
- **Cada notificação passou a ser dirigida a quem lhe diz respeito** — a pessoa
  diretamente ligada, mais o administrador. Não havia difusão em massa por
  permissão de leitura, e o estado "lida" de um utilizador nunca afetava o de
  outro.
- **O painel, a agenda e as notificações deixaram de poder discordar** sobre o que
  é um prazo crítico: quatro cálculos diferentes foram substituídos por um só.

---

## v2.9 — Aprofundamento do Módulo de Processos
**8 de julho de 2026** · *O processo passa a guardar a substância do dossiê*

**Em resumo:** o processo deixou de ser uma capa com partes e fases e passou a
guardar a substância jurídica do dossiê.

### Para o advogado

- **Passou a registar Decisões no processo** — data, tipo (Despacho, Decisão
  Interlocutória, Sentença ou Acórdão), resumo e o documento em anexo, tudo num
  único passo, sem ter de carregar o ficheiro antes e associá-lo depois.
- **Passou a registar Factos**, com descrição, data e ordem que pode reordenar
  para construir a narrativa do processo.
- **Passou a registar Testemunhas**, com nome, contacto, tipo (Autor ou Réu) e
  notas — como entidade própria, distinta das partes do processo.
- **Passou a ter um separador de Documentos dentro do próprio processo**, com
  carregamento, listagem, descarregamento e remoção.
- **Passou a registar o Juízo e a Origem do processo.** A Origem (Petição Inicial
  ou Notificações Avulsas) é obrigatória na abertura e fica fixa após a
  formalização, para não ser alterada depois do facto. Ambos os campos aparecem
  também na lista de processos.

### Para o escritório

- **Passou a ser possível gerar o Termo de Honorários a partir do processo**, já
  combinado com os dados do cliente e do honorário, pronto a imprimir. A impressão
  fica **bloqueada** — não apenas assinalada — enquanto o valor total estiver por
  preencher, para não sair do escritório um termo incompleto.
- **Formalizar um processo passou a criar automaticamente o honorário
  correspondente**, sempre em branco, para que o valor seja uma decisão explícita
  e nunca um valor herdado por engano. Repetir a operação não cria duplicados.
- **Passou a ser impossível ver dados de um processo a partir de outro.** Todas as
  gravações nas secções novas verificam duas vezes a quem pertencem: escritório e
  processo.

### Correção com impacto direto

- **Filtrar honorários ou documentos por processo passou a funcionar.** Até esta
  versão o filtro era ignorado pelo servidor e devolvia os registos de todo o
  escritório.

---

## v2.8 — Reestruturação da Ficha de Cliente
**6 de julho de 2026** · *A ficha de cliente ganha estrutura*

**Em resumo:** a ficha de cliente deixou de ser um ecrã longo e passou a ser um
dossiê organizado.

### Para quem atende o cliente

- **A ficha passou a estar organizada em sete separadores:** Dados (com
  identificação e conta corrente), Contactos e Notas, Processos, Pareceres,
  Documentos Entregues, Documentos a Tratar e Deslocações.
- **Consultar e editar passou a ser feito no mesmo ecrã**, com um botão de
  Editar / Guardar / Cancelar. Deixou de existir uma página de edição separada
  para onde era preciso navegar.
- **Passou a ver os processos e os pareceres do cliente sem sair da ficha**,
  carregados apenas quando abre o separador — e apenas se tiver permissão para os
  ver.
- **"Documentos Entregues" passou a aceitar ficheiros reais.** Antes era uma lista
  de texto escrita à mão; agora carrega o documento, com tipo, e fica associado ao
  cliente.

### Para o escritório

- **O tipo de documento de identificação passou a ser coerente com o tipo de
  cliente.** Um Particular pode ter CNI, BI ou Passaporte; uma Empresa apenas
  Registo Comercial. A regra é aplicada no ecrã e confirmada no servidor. Fichas
  antigas com valores não conformes mantêm-se intactas enquanto não forem
  alteradas.

### Correções com impacto direto

- A auditoria de fecho encontrou e corrigiu, antes da entrega, três defeitos
  críticos: **a associação de documentos ao cliente estava a falhar em silêncio**,
  a ligação de descarregamento estava incorreta, e faltava a validação de que o
  cliente pertencia ao escritório de quem carregava o ficheiro.

*Limitações registadas:* três fases com verificação estática completa mas
verificação com utilizador pendente; e uma limpeza de dados que tem de ser
executada manualmente antes ou durante a publicação.

---

## v2.7 — Simplificação da Gestão de Clientes
**2 de julho de 2026** · *Identificação do cliente simplificada*

**Em resumo:** menos campos, menos ambiguidade, e o NIF passou a ser obrigatório e
validado.

- **O NIF passou a ser obrigatório**, tanto para Particular como para Empresa,
  com validação de nove dígitos aplicada no ecrã **e** confirmada no servidor —
  não basta contornar o formulário.
- **O formulário passou a falar a língua do tipo de cliente:** o mesmo campo
  chama-se "Nome" para um Particular e "Nome Comercial" para uma Empresa; "Morada"
  para um, "Sede" para outra.
- **Passou a existir o tipo de documento Registo Comercial** para entidades
  coletivas.
- **A ficha impressa deixou de ter espaços em branco** deixados por campos de
  Empresa que já não se usam.

### Correção com impacto direto

- **O NIF podia ser silenciosamente substituído** por informação antiga, sem
  qualquer validação no servidor. Foi detetado pela auditoria e corrigido numa
  fase adicional — cuja própria revisão apanhou ainda outro efeito colateral, que
  estava a bloquear gravações não relacionadas em fichas antigas.

*Limitações registadas:* alguns tipos de documento continuavam a ser apresentados
com o valor bruto em vez da designação traduzida, e nenhum teste automatizado
cobria os quatro cenários de validação de NIF introduzidos. Ambos os pontos foram
resolvidos na v2.11.

---

## v2.6 — Módulo de Parecer Jurídico (Interface)
**1 de julho de 2026** · *O parecer jurídico passa a ser usável*

**Em resumo:** o módulo de parecer, construído na versão anterior, passou a estar
acessível através da aplicação.

### Para o advogado

- **Passou a poder trabalhar pareceres sem sair do ALCv.** Existe agora uma lista
  com filtros, uma página de detalhe com o histórico completo de versões, e um
  formulário de criação de solicitação.
- **Passou a submeter versões sucessivas do parecer** — resumo mais o anexo
  obrigatório — com barra de progresso durante o envio. Cada versão fica imutável.
- **Passou a poder entregar o parecer**, escolhendo qual das versões é a final. A
  ação é irreversível e pede confirmação explícita.
- **Um parecer entregue passou a ter uma vista de resumo própria**, só de leitura.
- **Passou a poder pesquisar pareceres** por conteúdo e por filtros.

### Para o escritório

- **Só o advogado responsável pela solicitação, ou um administrador, pode
  submeter versões e entregar.** A verificação é feita para aquela solicitação em
  concreto, não apenas pelo papel do utilizador.

### Correção com impacto direto

- A auditoria desta versão descobriu que **a pesquisa de pareceres entregue na
  versão anterior estava inacessível em funcionamento real**. Foi corrigida.

---

## v2.5 — Módulo de Parecer Jurídico (Fundação)
**30 de junho de 2026** · *O ciclo completo do parecer jurídico*

**Em resumo:** o ALCv passou a suportar o ciclo completo do parecer, com
rastreabilidade a sério — mas ainda sem interface (ver nota no fim).

### O que o ciclo garante

- **Solicitação → Elaboração → Aprovação interna (opcional) → Entrega.** A
  solicitação regista cliente, descrição, data, prazo desejado e urgência, pode
  ser associada a um processo, e tem estado próprio (Pendente, Em Elaboração, Em
  Revisão, Concluído).
- **Cada versão do parecer é imutável e fica registada** com número sequencial,
  autor e data. Nenhuma versão anterior pode ser reescrita.
- **A entrega é irreversível** e só pode ser feita pelo advogado responsável ou
  por um administrador.
- **Cada uma das cinco transições de estado fica registada em auditoria**
  automaticamente.
- **Pesquisa por texto no conteúdo do parecer**, combinada com filtros por
  cliente, advogado, estado e data.
- **Permissões próprias do módulo** — consultar, criar, editar e gerir pareceres —
  atribuídas por papel.

### Qualidade

- Cinco rondas de revisão de código encontraram e corrigiram, entre outros, **dois
  acessos indevidos a dados de outro escritório**, classificados como críticos.

*Limitações registadas:* esta versão entregou apenas a fundação, sem interface —
decisão deliberada, resolvida na v2.6. **A comparação lado a lado entre versões
de um parecer não foi implementada e continua por implementar** — existe apenas a
listagem e o detalhe sequencial.

---

## v2.4 — Ficha de Cliente
**30 de junho de 2026** · *A ficha do escritório passa para dentro do sistema*

**Em resumo:** o formulário em papel do escritório passou a existir na plataforma,
e continua a poder ser impresso tal como era.

### Para quem atende o cliente

- **Cada cliente passou a ter um número próprio**, atribuído automaticamente e em
  sequência (CLI-0001), por escritório.
- **O formulário adapta-se ao tipo de cliente.** Para Particular pede os dados
  demográficos; para Empresa pede nome comercial, NIF, sede, representante legal e
  cargo.
- **A procuração passou a ser exigida a todos os clientes**, com um aviso visível
  "Procuração em falta" que assinala sem impedir o trabalho, e carregamento ou
  substituição do ficheiro.
- **Passou a ser possível registar todo o acolhimento do processo (apresentado na
  plataforma como «Intake») na própria ficha:** descrição, advogados atribuídos
  (nome, cédula, contacto),
  administrativos atribuídos, documentos entregues e a tratar, deslocações a
  realizar e honorários propostos, incluindo o valor por extenso.
- **Um cliente com avença passou a estar identificado** por distintivo na ficha e
  nas listagens.

### Para o escritório

- **Passou a existir uma ficha de cliente imprimível fiel ao formulário físico**,
  formatada para A4, com impressão direta a partir do ecrã.

### Correções com impacto direto

- A auditoria posterior à execução detetou que **os dados eram gravados
  corretamente mas nunca apareciam no ecrã** — uma incompatibilidade que
  invalidava 9 dos 19 requisitos da versão. Corrigida antes da entrega.
- **Foi detetada e eliminada uma exposição do resumo criptográfico (*hash*) da
  senha** em dois pontos de acesso novos.

*Limitações registadas:* a correção foi verificada campo a campo, mas não testada
com o sistema completo em execução; ficaram adiados a restrição de tipos de
ficheiro na procuração, a formatação de moeda na ficha impressa e o acesso à
ficha em telemóvel.

---

## v2.3 — Utilização em Ecrã Móvel
**21 de junho de 2026** · *O ALCv passa a funcionar no telemóvel*

**Em resumo:** deixou de ser preciso estar ao computador para consultar o
essencial.

### Para o advogado fora do escritório

- **Passou a ter os eventos do dia logo no topo da Agenda** em telemóvel, num
  bloco "Hoje".
- **Passou a navegar entre os cinco módulos principais por uma barra inferior**,
  que só mostra o que tem permissão para ver.
- **O menu lateral passou a abrir em gaveta** e a fechar-se sozinho assim que
  navega — sem ficar a tapar o conteúdo.
- **As listas de Clientes, Agenda, Documentos e Financeiro passaram a ser cartões
  legíveis** em ecrã pequeno, em vez de tabelas cortadas.
- **As tabelas que têm mesmo de ser tabelas** — Partes e Fases do processo —
  ganharam deslocamento horizontal.
- **Os formulários passaram a uma só coluna**, as caixas de diálogo sobem do fundo
  do ecrã, e os botões respeitam o tamanho mínimo recomendado para toque.

---

## v2.2 — Armazenamento Dedicado de Documentos
**19 de junho de 2026** *(data do registo interno de planeamento)* · *Documentos fora do disco do servidor*

**Em resumo:** os documentos do escritório deixaram de viver no disco do servidor
da aplicação.

- **Os ficheiros passaram a ser guardados em armazenamento de objetos dedicado**,
  com carregamento, descarregamento e remoção. O acesso a cada ficheiro é feito
  por ligação assinada, gerada no momento do pedido.
- **Carregar um documento passou a ser uma operação visível e confortável:** barra
  de progresso, arrastar e largar, e pré-visualização.

---

## v2.1 — Agenda Avançada
**18 de junho de 2026** *(data do registo interno de planeamento)* · *A agenda deixa de exigir que se lembre de tudo*

**Em resumo:** a agenda deixou de exigir que se lembre de tudo.

- **Passou a ser avisado de eventos próximos dentro da própria aplicação**, com
  contador no cabeçalho e painel de notificações.
- **Passou a criar eventos recorrentes** com regra de repetição, em vez de os
  registar um a um.
- **Passou a mover um evento arrastando-o no calendário**, com a alteração
  gravada de imediato.

---

## v2.0 — Módulo Financeiro
**18 de junho de 2026** *(data do registo interno de planeamento)* · *O escritório passa a gerir o seu dinheiro na plataforma*

**Em resumo:** o escritório passou a ver e a gerir o seu dinheiro na plataforma.

### Para o gestor do escritório

- **Passou a gerir honorários e pagamentos**.
- **Passou a ver o estado de cada honorário calculado automaticamente**, sem o
  determinar de cabeça.
- **Passou a ter um resumo financeiro em cartões no topo da página**, com os
  indicadores principais à vista.
- **Passou a filtrar a lista de honorários** e a **editar cada honorário na
  sua ficha**.
- **Passou a exportar a lista para CSV** — respeitando os filtros que aplicou,
  não a lista inteira.

*Limitação registada:* a esta versão foi também atribuída a possibilidade de
**eliminar** um honorário ou um pagamento. Essa operação foi construída, mas
ficou guardada por uma permissão que não chegou a ser atribuída a nenhum papel,
nem ao ADMIN. Nunca esteve, por isso, ao alcance de nenhum utilizador: quem usa
a plataforma desde junho de 2026 dispôs sempre de tudo o resto deste módulo, mas
**em momento algum dispôs da eliminação**. O lapso está corrigido e a operação
fica operacional, para o perfil ADMIN, com a próxima atualização.

---

## v1.9 — Melhoria do Módulo de Agendamento
**17 de junho de 2026** · *Agenda e prazos numa vista só*

**Em resumo:** eventos e prazos deixaram de viver em sítios diferentes.

- **Passou a ver eventos e prazos no mesmo calendário mensal**, com filtros por
  processo, categoria e estado.
- **Deixaram de aparecer datas trocadas.** O tratamento de fuso horário nos
  campos de data foi corrigido em todo o módulo.
- **Um intervalo de datas inválido passou a ser recusado com uma mensagem clara**,
  no ecrã e no servidor, em vez de falhar em silêncio ou com um erro genérico.
- Os ecrãs passaram a indicar quando estão a carregar.

---

## v1.8 — Publicação em Servidor Próprio
**16 de junho de 2026** · *O ALCv passa a estar em serviço permanente*

**Em resumo:** o produto deixou de correr apenas em máquinas de desenvolvimento.

### Para o escritório

- **A plataforma passou a estar acessível em servidor próprio, com HTTPS.** Os
  certificados são emitidos e renovados automaticamente — não há risco de
  expirarem por esquecimento.
- **As atualizações passaram a ser publicadas por um processo automático.** Uma
  correção deixa de depender de uma intervenção manual, o que reduz o tempo entre
  detetar um problema e o resolver em serviço.
- **Os dados persistem entre reinícios** — base de dados e documentos ficam em
  volumes dedicados, com limites de recursos e reinício automático em produção.
- **A plataforma corre sem privilégios de administrador e sem palavras-passe
  embutidas** nas imagens de execução.
- Existe um manual de operação da publicação, para que a operação não dependa da
  memória de uma pessoa.

*Limitação registada:* três itens ficaram adiados por exigirem verificação com o
servidor em funcionamento.

---

## v1.7 — Gestão e Acompanhamento de Processos
**16 de junho de 2026** *(data do registo interno de marcos)* · *Do primeiro contacto ao processo formal*

**Em resumo:** o processo passou a ter um antes — e a ter memória.

### Para o advogado

- **Passou a poder registar um potencial cliente e iniciar o acompanhamento antes
  da abertura formal do processo**, com os campos mínimos exigidos pelo tipo de
  processo em questão.
- **Passou a ver a história completa do processo numa linha de tempo unificada**,
  apresentada por omissão ao abrir o processo, com filtros. Substituiu o antigo
  separador de movimentações.

### Para o escritório

- **A verificação de conflito de interesses passou a ser obrigatória e
  estruturada** — por cliente, partes relacionadas, parte contrária e assunto. O
  sistema **impede a abertura formal do processo** enquanto não existir uma
  decisão registada. Deixa de depender da memória de quem abre o processo.
- **O processo passou a ser gerido por estados definidos**, com acompanhamento dos
  prazos operacionais associados a cada um.
- **Passou a existir uma trilha de auditoria consultável**, num separador próprio
  e com acesso controlado por permissões — quem fez o quê e quando.
- Governança documental e retenção, e painéis com indicadores executivos, foram
  igualmente entregues nesta versão.

A auditoria de fecho registou as cinco fases verificadas e concluídas, sem lacunas
significativas.

---

## v1.5 e v1.6 — sem entrega registada

Estas duas numerações não correspondem a nenhuma versão entregue. Não têm etiqueta
de versão nem constam do registo de marcos, e nenhuma das suas fases chegou a ser
executada.

Existem requisitos redigidos para ambas — sete para a v1.5 e quatro para a v1.6 —
arquivados dentro do documento de requisitos da v1.7, todos com estado "planeado"
e nenhum concluído. Um arquivo de planeamento anterior regista-as como suspensas
ou adiadas: *v1.5 Melhoria funcionalidades processos* e *v1.6 Melhoria nfeature de
gestão de clientes*.

Parte do que estava previsto foi coberto mais tarde — a gestão de processos pela
v1.7 e pela v2.9, a gestão de clientes pela v2.4, v2.7 e v2.8 — mas **não existe
registo formal que confirme essa correspondência**. Registamos a lacuna em vez de
a preencher com suposições.

---

## v1.4 — Melhoria do Módulo de Clientes
**3 de junho de 2026** *(data do arquivo de planeamento)* · *Gestão de clientes em escala*

**Em resumo:** o módulo de clientes passou a aguentar uma carteira a sério.

- **Passou a encontrar um cliente escrevendo qualquer coisa que saiba dele** —
  nome, NIF, telefone ou correio eletrónico — com filtros avançados por tipo,
  estado e localidade.
- **Passou a registar contactos e notas do cliente** como informação estruturada
  na ficha, em vez de texto solto.
- **Passou a importar uma carteira de clientes a partir de um ficheiro CSV**, com
  validação e indicação dos erros encontrados.
- **Passou a exportar a lista para CSV**, respeitando os filtros aplicados.
- **Passou a detetar e a fundir clientes duplicados**, com sugestões automáticas
  por NIF, correio eletrónico e telefone, num fluxo guiado.
- A listagem ganhou estados de carregamento consistentes, estados vazios e
  paginação.

---

## v1.3 — Reforço de Segurança
**3 de junho de 2026** *(data do arquivo de planeamento)* · *Segurança tratada como requisito de base*

**Em resumo:** a versão em que a plataforma deixou de ser um protótipo do ponto de
vista de segurança.

### Para o escritório

- **O isolamento de dados entre escritórios foi reforçado**, numa fase dedicada a
  esse tema. É a barreira de que dependem todas as versões seguintes, e voltou a
  ser auditada na v2.11 e na v2.16.
- **A autenticação e a gestão de sessão foram reforçadas.**
- **As permissões passaram a ser verificadas no servidor**, não apenas escondidas
  no ecrã. Esconder um botão deixou de ser a única proteção.
- **As ações da interface passaram a ser controladas por permissões de forma
  consistente** em toda a aplicação.
- **A aplicação passou a dar retorno visível ao utilizador** — sucesso e erro —
  através de um sistema de mensagens global.
- Configuração, gestão de segredos e cabeçalhos de segurança revistos, e
  ferramentas de análise automática de segurança introduzidas no projeto.

---

## v1.2 — Painel de Utilizador e Passagem a Servidor Real
**27 de maio de 2026** · *Fim do protótipo*

**Em resumo:** os dados passaram a ser reais.

- **A plataforma passou a assentar num servidor e numa base de dados reais**, em
  substituição da simulação usada até então. É esta a versão em que o ALCv deixou
  de ser uma maqueta funcional.
- **Cada utilizador passou a ter o seu painel de perfil**, com edição dos próprios
  dados.
- Os dados iniciais passaram a ser coerentes, incluindo o recálculo da conta
  corrente.

---

## v1.1 — Alinhamento de Interface
**27 de maio de 2026** · *Identidade visual institucional*

**Em resumo:** o produto passou a parecer uma ferramenta institucional.

- **Passou a existir um esqueleto de aplicação consistente** — barra lateral
  escura com indicação clara do módulo ativo, e barra superior com pesquisa,
  identificação da instituição e ações de utilizador.
- **Passou a haver modo claro e modo escuro.**
- Componentes reutilizáveis — distintivos, tabelas, paginação — aplicados às
  páginas principais, para que ecrãs diferentes deixem de parecer produtos
  diferentes.

---

## v1.0 — Versão Mínima Viável (MVP)
**26 de maio de 2026** *(data do registo interno de marcos)* · *A primeira versão utilizável*

**Em resumo:** o dia em que o ciclo cliente → processo → prazo → documento →
honorário passou a existir num só sítio.

- **Clientes** — criar, consultar, editar, eliminar, filtrar, e ver a conta
  corrente.
- **Processos** — criar, consultar, editar e eliminar, com partes, fases e
  movimentações.
- **Agenda** — gerir eventos, filtrar por criticidade e marcar como concluídos.
- **Documentos** — listar, carregar, descarregar e remover.
- **Financeiro** — honorários e pagamentos, com impacto direto na conta corrente
  do cliente.
- **Painel** com os indicadores base.
- **Início de sessão e permissões por papel** desde o primeiro dia — o módulo
  Financeiro, por exemplo, visível apenas para ADMIN e TECNICO.
- **A estrutura multi-escritório existe desde a origem do produto**, não foi
  acrescentada mais tarde.

*Nota:* nesta versão a autenticação e o acesso aos dados eram simulados dentro da
própria aplicação. A passagem a servidor real ocorreu na v1.2.

---

*Documento destinado a clientes contratantes do ALCv. O detalhe item a item por
versão está no **Registo de Alterações**, que cobre exatamente as mesmas versões e
usa as mesmas designações. Ambos os documentos derivam dos registos internos de
planeamento e execução do projeto.*
