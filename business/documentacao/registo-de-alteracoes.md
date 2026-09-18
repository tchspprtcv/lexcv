# ALCv — Registo de Alterações

**Produto:** ALCv — plataforma de gestão de escritórios e instituições jurídicas (Cabo Verde)
**Documento:** Registo de alterações por versão (*changelog*)
**Data de emissão:** 21 de agosto de 2026
**Última versão coberta:** v2.16 (30 de julho de 2026)

---

## Como ler este documento

Este registo lista, por ordem cronológica inversa (a versão mais recente primeiro),
o que foi entregue em cada versão do ALCv. Cada versão está dividida em três
categorias:

- **Novas funcionalidades** — capacidades que não existiam antes.
- **Melhorias** — comportamento que já existia e passou a funcionar melhor.
- **Correções** — defeitos identificados e resolvidos.

Quando uma versão fechou com limitações conhecidas e registadas, essas limitações
aparecem numa secção própria, **Limitações registadas no fecho**. Optámos por as
expor em vez de as omitir.

### Designações das versões

Cada versão é identificada pela sua designação e pelo número técnico
correspondente — por exemplo, «v2.16 — Distribuição Multi-Escritório e Faturação
por Utilizadores».

A designação técnica original de cada versão — a que consta dos registos internos
do projeto — está indicada na tabela-resumo em anexo, para quem precise de cruzar
este documento com esses registos. O documento complementar, **Relatório de
Versão**, usa exatamente as mesmas designações.

### Origem dos dados

Todo o conteúdo deste documento provém dos registos internos de planeamento e
execução do projeto:

- o registo de fecho de cada versão, com a respetiva data de entrega;
- a lista de capacidades validadas, com indicação da versão que as entregou;
- os arquivos de âmbito, de requisitos e de datas de cada versão;
- as etiquetas de versão do repositório de código, que confirmaram uma a uma as
  datas de entrega.

### Nota metodológica sobre datas

Em regra, quando existe etiqueta de versão no repositório de código, a data
indicada é a dessa etiqueta. Há etiqueta para as versões v1.0, v1.1, v1.2, v1.8,
v1.9 e v2.3 a v2.16. A v1.0 é a única exceção, pela razão explicada no fim desta
nota.

Para as versões **v1.3, v1.4, v1.7, v2.0, v2.1 e v2.2 não existe etiqueta**; nesses
casos indicamos a data de fecho registada nos documentos internos de planeamento, e
assinalamo-lo junto à data.

O histórico do repositório contém 2070 registos de alteração e começa a 27 de maio
de 2026, com um registo cujo assunto é precisamente o arquivo das versões v1.0 a
v1.2. Ou seja, **o desenvolvimento da v1.0 é anterior ao histórico disponível** e a
sua data provém apenas do registo interno de marcos.

---

## v2.16 — Distribuição Multi-Escritório e Faturação por Utilizadores

**Data de entrega:** 30 de julho de 2026
**Âmbito:** 8 fases, 27 planos, 58 tarefas

### Novas funcionalidades

- Plano comercial e limite de utilizadores por escritório passam a ser dados
  guardados na plataforma.
- A criação de um novo utilizador é recusada quando o escritório atinge o seu
  limite de utilizadores ativos.
- Indicador "X/Y utilizadores" na gestão de utilizadores, com três estados de
  mensagem e explicação visível sobre o botão desativado quando o limite é
  atingido.
- Novo papel **PLATAFORMA_ADMIN**, com um escritório reservado ("ALCv"),
  distinto do papel ADMIN de cada escritório.
- Provisionamento de um novo escritório com o respetivo ADMIN inicial, sem
  depender do assistente de primeira instalação.
- Nova **consola interna de administração da plataforma**: criar escritório,
  listar escritórios com o respetivo grau de utilização, ajustar plano e limite,
  suspender e reativar.
- Suspensão de um escritório com efeito imediato — uma sessão já aberta é cortada
  em cerca de um segundo, sem necessidade de terminar e reiniciar sessão.
- Novo **relatório interno de utilização por escritório**, com nome, plano, limite
  e número de utilizadores ativos, incluindo escritórios suspensos, para suportar a
  faturação manual.

### Melhorias

- A matriz de permissões deixa de ser editável por cada escritório e passa a ser
  gerida centralmente pela plataforma. O ADMIN de escritório vê agora uma
  indicação "Gerido pela Plataforma" em vez do botão de gravação.
- Reforço das barreiras que impediam um ADMIN de escritório de escalar os seus
  próprios privilégios até ao novo papel de plataforma.
- A página institucional pública passa a apresentar sempre a marca genérica ALCv,
  deixando de assumir a existência de um único escritório.
- Auditoria dedicada de isolamento de dados sobre as três novas superfícies
  introduzidas nesta versão, concluída antes da entrada de um segundo escritório
  pagante.
- O indicador "X/Y utilizadores" passou a consumir a contagem calculada pelo
  servidor, em vez de a recalcular no navegador.

### Correções

- Uma recusa de autorização passou a devolver corretamente o estado "acesso
  proibido" em vez de um erro genérico de servidor, em toda a aplicação.
- Identificada uma falha real de funcionamento nas gravações de dados de
  escritório, causada por uma migração de base de dados pendente. Resolvida no
  ambiente de desenvolvimento pela execução da migração; ver **Limitações
  registadas no fecho**.
- A mensagem de erro apresentada ao utilizador quando o limite é atingido deixou
  de mostrar um prefixo técnico.
- Passou a existir tratamento adequado de pedidos mal formados.

### Limitações registadas no fecho

- Três guiões de migração de base de dados desta versão têm de ser executados
  manualmente sobre a base de dados antes da entrada em serviço, por não existir
  executor automático de migrações no projeto. Um deles foi executado apenas no
  ambiente de desenvolvimento; sem ele, a primeira suspensão de um escritório
  pré-existente falha. É uma pré-condição de instalação, não um defeito do
  produto.
- A bateria automática de verificação foi concluída. A verificação interativa em
  navegador de seis cenários adicionais não pôde ser realizada por um bloqueio da
  ferramenta de automação de navegador — não por defeito do produto.

---

## v2.15 — Reposicionamento Institucional (SIJ)

**Data de entrega:** 27 de julho de 2026
**Âmbito:** 1 fase, 1 plano, 3 tarefas

### Melhorias

- O enquadramento institucional do ALCv passou a descrever a plataforma como
  alinhada ao ecossistema do **SIJ (Sistema Judicial de Cabo Verde)**, na página
  pública e na especificação do produto.
- A identidade do escritório de demonstração pré-carregado foi generalizada
  (nome, NIF, tipo de entidade, correio eletrónico e telefone).

### Correções

- A auditoria desta versão descobriu que o mesmo campo de nome de escritório era
  servido não só à página pública mas também a todos os utilizadores autenticados
  — visível no cabeçalho da aplicação e em dois documentos imprimíveis (Ficha de
  Cliente e Termo de Honorários). Os dois fluxos ficaram corrigidos pela mesma
  alteração.

### Nota de âmbito

Esta versão corrigiu apenas posicionamento e texto institucional. **Nenhuma
integração técnica com o SIJ foi construída**, e nenhuma está planeada — o
registo interno classifica-a explicitamente como fora de âmbito.

---

## v2.14 — Melhorias de Interface e Pesquisa Global

**Data de entrega:** 22 de julho de 2026
**Âmbito:** 6 fases, 23 planos, 49 tarefas

### Novas funcionalidades

- **Pesquisa global** transversal a clientes, processos, documentos e pareceres,
  acessível por atalho de teclado (Ctrl+K / ⌘K), com agrupamento por tipo,
  destaque do texto encontrado e histórico de pesquisas recentes da sessão.
- A pesquisa ordena resultados exatos e por prefixo antes dos resultados por
  correspondência parcial, e ignora acentos.
- Ligação "Ver todos" que transporta o termo pesquisado para cada uma das quatro
  listas.
- Criação de processo e de parecer diretamente a partir da ficha do cliente, com
  o cliente já preenchido e bloqueado no formulário.

### Melhorias

- O campo de pesquisa decorativo no topo foi substituído por um acionador real da
  pesquisa global, com equivalente em ecrã móvel.
- O filtro por estado na lista de processos foi promovido do painel de filtros
  avançados, que estava recolhido, para a barra de filtros sempre visível.
- Cantos arredondados repostos em toda a aplicação e no sítio institucional,
  incluindo a limpeza de 271 sobreposições que estavam a mascarar a alteração.
- Ícones em todos os botões da aplicação; os botões de filtro (aplicar, limpar,
  exportar) passaram a ícone com explicação em Clientes, Processos, Agenda,
  Documentos e Financeiro.
- Filtros do módulo de pareceres alinhados aos mesmos controlos já usados em
  Processos e Clientes.

### Correções

- Corrigida a ligação do botão "Entrar" do sítio institucional público para o
  ecrã de início de sessão da aplicação.
- Corrigido um defeito real detetado apenas em teste com a aplicação a correr: a
  mensagem "cliente já não está disponível" nunca chegava a ser apresentada.

### Limitações registadas no fecho

- Dois critérios de sucesso relativos ao aspeto visual dos cantos arredondados
  ficaram apenas parcialmente confirmados em verificação ao vivo.

---

## v2.13 — Renovação do Sistema de Design da Interface

**Data de entrega:** 18 de julho de 2026
**Âmbito:** 10 fases, 42 planos, 86 tarefas

### Novas funcionalidades

- Novo seletor de datas em calendário, com idioma português e semana a começar ao
  domingo, aplicado aos campos de data e hora da Agenda.
- Novo campo de escolha pesquisável, em dois modos: lista fechada para os filtros
  de Documentos e lista com criação livre para o tipo de documento.
- Padrão único de tabela partilhado — com ordenação, paginação e escolha de
  colunas visíveis — adotado pelas cinco listas principais: Clientes, Processos,
  Pareceres, Financeiro e Documentos.
- Navegação móvel funcional no sítio institucional público, que antes não tinha
  nenhuma.

### Melhorias

- Adoção formal de uma biblioteca de componentes de interface, com conjunto
  completo de cores e estilos padronizados nas duas aplicações, preservando a
  identidade institucional.
- Ficha de Cliente (7 separadores) e Ficha de Processo (8 separadores) migradas
  de botões manuais para separadores acessíveis por teclado.
- Estados de carregamento e de "sem dados" oficiais no Painel (indicadores,
  Atividade Recente, Prazos Urgentes, Processos Recentes).
- Elevação visual das superfícies em modo escuro (cartões, caixas de diálogo,
  tabelas).
- Explicações em texto sobre ícones sem legenda, na barra lateral e nas ações de
  linha de Clientes, Definições, Processos e Pareceres.
- Menu de utilizador único, consolidando três menus duplicados; contador de
  notificações não lidas e barra de progresso do assistente de primeira
  instalação normalizados.
- Histórico de versões de parecer passa a recolher as versões antigas.

### Correções

- **Vulnerabilidade real de injeção de fórmula na exportação para CSV**
  (Financeiro e Clientes) identificada e corrigida, incluindo duas regressões
  introduzidas pela primeira tentativa de correção.
- Corrigido um defeito crítico anterior a esta versão: a verificação de permissões
  usava um estado errado e provocava um "Acesso negado" momentâneo a todos os
  utilizadores no primeiro carregamento. Corrigido no Painel e, depois, nos dez
  ecrãs de Clientes e Processos e nas quatro páginas da Agenda.
- A lista de Documentos mostrava identificadores internos em vez dos nomes.
- Barra de separadores da Ficha de Processo não quebrava linha em ecrã móvel.
- Cancelar uma alteração de tipo de cliente já confirmada apagava silenciosamente
  um tipo de documento antigo numa gravação posterior.
- Corrigida uma ordem incorreta de declaração de estilos que afetava o modo
  escuro.
- Corrigidos o tamanho mínimo de texto em etiquetas e o alinhamento do cabeçalho
  da coluna "Ações".

### Limitações registadas no fecho

- A verificação ao vivo da matriz de permissões para os papéis ADVOGADO e
  ASSISTENTE não foi concluída por instabilidade do ambiente de navegador; foi
  aceite com base em análise de código e na confirmação já feita para ADMIN e
  TECNICO.
- Foi descoberto e sinalizado em separado um defeito grave, anterior a esta
  versão, no carregamento de documentos. **Está registado e continua em aberto** —
  ver Roteiro Tecnológico, item A.1.

---

## v2.12 — Sítio Institucional Público

**Data de entrega:** 15 de julho de 2026
**Âmbito:** 3 fases, 9 planos, 21 tarefas

### Novas funcionalidades

- Nova aplicação autónoma que serve a **página institucional pública** completa:
  Apresentação, Funcionalidades, Confiança Institucional e Contacto, com modo
  claro e escuro.
- Novo ponto de acesso público, sem autenticação, que devolve apenas o nome e o
  logótipo do escritório — através de um objeto de resposta próprio, nunca o
  registo completo do escritório, para não expor dados pessoais.

### Melhorias

- Encaminhamento e publicação completos em ambiente de desenvolvimento, de
  produção e no alojamento contratado, com as três fontes de configuração do
  servidor de entrada mantidas coerentes entre si.
- Novo artefacto do sítio institucional publicado no processo automático de
  integração e entrega contínua.

### Correções

- A verificação com o sistema completo em execução — e não apenas por partes
  isoladas — encontrou e corrigiu dois defeitos reais: uma leitura de endereço
  que desativaria silenciosamente o redirecionamento para a primeira instalação
  em todos os ambientes, e uma falha que fazia a leitura do logótipo do escritório
  terminar em erro.
- Corrigido um defeito de publicação anterior a esta versão: faltava a passagem de
  variáveis de ambiente ao servidor de entrada na configuração de produção.

---

## v2.11 — Auditoria Técnica e Notificações Avançadas

**Data de entrega:** 14 de julho de 2026
**Âmbito:** 8 fases, 21 planos, 39 tarefas

> **Nota de fonte:** o registo interno de marcos fechou esta versão **sem qualquer
> lista de realizações**. O conteúdo abaixo provém da lista interna de capacidades
> validadas, que atribui oito entradas a esta versão, uma por fase (fases 90 a
> 97).

### Novas funcionalidades

- **Preferências de notificação por utilizador** — cada utilizador pode silenciar
  categorias de notificação. Os avisos de prazo vencido são sempre entregues,
  mesmo com a categoria silenciada, e o processamento diário respeita o
  silenciamento.
- **Adiamento de lembrete de prazo**, com opções de 1, 3 ou 7 dias, reaparecendo
  automaticamente no fim do período e sem duplicação pelo processamento diário.

### Melhorias

- As notificações de processo (entrada de fase, novo documento, atribuição)
  passaram a alcançar toda a equipa de advogados e administrativos ligada ao
  cliente, e não apenas o responsável único.
- A Agenda passou a usar a mesma fonte única de cálculo de risco de prazo já
  usada pelos restantes módulos, terminando com a quinta implementação divergente
  do conceito de "prazo crítico".
- A análise automática de segurança do código passou a correr sem falhas.
- Primeira infraestrutura de testes de integração contra uma base de dados real
  neste projeto, com verificação obrigatória no processo automático de
  verificação de cada alteração.

### Correções

- Corrigido um defeito anterior a esta versão: um destinatário que fosse
  simultaneamente membro da equipa e ADMIN provocava um conflito na regra de
  eliminação de duplicados.
- Corrigida uma divergência entre datas de início e de fim no cálculo de evento
  crítico, detetada no próprio fecho da versão.
- As designações de tipo de documento passaram a ser apresentadas traduzidas em
  vez do valor bruto.

### Nota de qualidade

- A auditoria final desta versão confirmou o isolamento de dados entre escritórios
  nas três novas superfícies de notificação e fechou a verificação com utilizador
  de oito fases históricas, cerca de 40 cenários.

---

## v2.10 — Notificações e Alertas

**Data de entrega:** 10 de julho de 2026
**Âmbito:** 5 fases, 14 planos, 29 tarefas

### Novas funcionalidades

- **Primeiro sistema de notificações persistido** do produto, com listagem, marcar
  como lida, marcar todas como lidas e contagem de não lidas — sempre delimitado
  por escritório e por destinatário.
- Quatro alertas desencadeados por acontecimento: entrada de nova fase no
  processo, novo documento, processo atribuído e parecer atribuído.
- **Novo fluxo de reatribuição de responsável do processo**, que não existia
  antes, com o respetivo controlo na interface.
- **Processamento diário automático** (às 06:00, hora de Cabo Verde) que analisa o
  risco de prazos, eventos e honorários, com isolamento de falhas em quatro
  camadas e sem repetir a notificação do mesmo nível de risco.
- Nova página dedicada de histórico de notificações, com filtros por categoria e
  por estado de leitura, e paginação real.

### Melhorias

- Quatro cálculos inconsistentes de "prazo crítico", espalhados pelo sistema,
  foram consolidados num único serviço partilhado, eliminando o risco de o
  painel, a agenda e as notificações discordarem entre si.
- O sino de notificações foi reescrito: contador com atualização periódica e ao
  regressar ao separador, e clique único que marca como lida e navega.

### Correções

- Corrigido um defeito anterior a esta versão que provocava perda de dados numa
  atualização parcial de parecer.
- Corrigido um bloqueio que impedia utilizadores não-ADMIN de usar o seletor de
  reatribuição.
- Refinada, ao longo de três rondas de endurecimento, uma classe de contorno de
  validação de endereço.

---

## v2.9 — Aprofundamento do Módulo de Processos

**Data de entrega:** 8 de julho de 2026
**Âmbito:** 5 fases, 12 planos, 25 tarefas

### Novas funcionalidades

- Novos campos no processo: **Juízo** (texto livre) e **Origem** (Petição Inicial
  ou Notificações Avulsas), esta última obrigatória na abertura e imutável após a
  formalização.
- Nova secção **Decisões** — data, tipo (Despacho, Decisão Interlocutória,
  Sentença, Acórdão), resumo e anexo carregado num único passo.
- Nova secção **Factos** — descrição, data e ordem reordenável.
- Nova secção **Testemunhas** — nome, contacto, tipo (Autor ou Réu) e notas.
- Novo separador **Documentos** na ficha do processo, com carregamento,
  listagem, descarregamento e remoção.
- **Termo de Honorários imprimível**, combinando dados de cliente, processo e
  honorário, com bloqueio efetivo da impressão enquanto o valor total estiver por
  preencher.
- Criação automática de um honorário vazio na primeira formalização do processo
  (passagem de Triagem a Ativo), sem valor pré-preenchido e sem criar duplicados
  em caso de repetição.

### Melhorias

- Juízo e Origem passaram a constar também na listagem de processos, e não apenas
  no detalhe.
- Partes e Fases, existentes desde a v1.0, foram uniformizadas para o mesmo padrão
  visual das quatro secções novas.
- Todas as escritas nas novas secções verificam duplamente a posse: escritório e
  processo de origem.

### Correções

- Corrigidos dois defeitos de integração: os filtros por processo nas listas de
  honorários e de documentos eram ignorados pelo servidor, devolvendo dados de
  todo o escritório em vez dos do processo pedido.

---

## v2.8 — Reestruturação da Ficha de Cliente

**Data de entrega:** 6 de julho de 2026
**Âmbito:** 6 fases (74 a 79), 13 planos, 26 tarefas

### Novas funcionalidades

- Ficha de cliente reestruturada em **sete separadores**: Dados (com identificação
  e conta corrente), Contactos e Notas, Processos, Pareceres, Documentos
  Entregues, Documentos a Tratar e Deslocações.
- "Documentos Entregues" deixou de ser uma lista de texto e passou a ser
  **carregamento real de ficheiros**, através de um novo ponto de acesso de
  documentos por cliente, delimitado por escritório.

### Melhorias

- As páginas de detalhe e de edição do cliente foram unificadas num único ecrã com
  alternância Editar / Guardar / Cancelar; a rota de edição separada foi removida.
- Tipo de documento reestruturado: BI adicionado, NIF removido, opções filtradas
  por tipo de cliente (Particular: CNI, BI ou Passaporte; Empresa: apenas Registo
  Comercial), validado no ecrã e no servidor, preservando valores antigos não
  conformes quando a edição não os altera.
- Os separadores Processos e Pareceres carregam os dados apenas quando são
  abertos, com as permissões respetivas espelhadas na interface.

### Correções

- A auditoria de fecho encontrou e corrigiu três defeitos críticos antes da
  entrega: uma incompatibilidade de nome de campo que quebrava silenciosamente
  **toda** a associação de documentos ao cliente, uma ligação de descarregamento
  incorreta, e a falta de validação de posse de escritório no carregamento de
  ficheiros.

### Limitações registadas no fecho

- Três fases com verificação estática completa mas verificação com utilizador
  pendente, por indisponibilidade de ambiente na sessão.
- A limpeza de dados associada tem de ser executada manualmente antes ou durante a
  publicação, por não existir executor automático de migrações no projeto.
- Dois campos ficam órfãos por decisão deliberada de corte limpo.

---

## v2.7 — Simplificação da Gestão de Clientes

**Data de entrega:** 2 de julho de 2026
**Âmbito:** 5 fases (70 a 73.1, incluindo uma fase de fecho de lacuna), 6 planos

### Melhorias

- Modelo de identificação do cliente aplanado — remoção completa do bloco de dados
  variável por tipo, em favor de campos diretos.
- **NIF passou a ser obrigatório** para Particular e Empresa, com validação de
  nove dígitos aplicada no ecrã e no servidor.
- Formulários de criação e de edição com designações dinâmicas conforme o tipo de
  cliente: "Nome" ou "Nome Comercial", "Morada" ou "Sede".
- Novo tipo de documento **Registo Comercial** para Empresa.
- Página de detalhe e ficha imprimível atualizadas, sem campos em branco
  remanescentes dos campos de Empresa descontinuados.

### Correções

- A auditoria encontrou uma lacuna: o NIF podia ser silenciosamente substituído
  por lógica antiga, sem validação no servidor. Corrigido numa fase de fecho de
  lacuna, cuja própria revisão detetou e corrigiu ainda outra regressão — uma
  validação que estava a bloquear gravações não relacionadas em registos antigos
  com NIF inválido.
- A reauditoria confirmou sete de sete requisitos satisfeitos, sem bloqueios
  remanescentes.

### Limitações registadas no fecho

- Alguns tipos de documento apresentados como valor bruto em vez de designação
  traduzida (cosmético; resolvido na v2.11).
- Nenhum teste automatizado cobria os quatro cenários de validação de NIF
  introduzidos (resolvido na v2.11).

---

## v2.6 — Módulo de Parecer Jurídico (Interface)

**Data de entrega:** 1 de julho de 2026
**Âmbito:** 5 fases, 6 planos, 15 tarefas

### Novas funcionalidades

- **Interface completa do módulo de pareceres**, construída sobre a fundação
  entregue na v2.5: lista com duas vistas, distintivos e filtros; detalhe com histórico
  imutável de versões; criação de solicitação; pesquisa avançada.
- Formulário "Nova Versão" na página do parecer, que permite ao advogado
  responsável ou ao ADMIN submeter versões sucessivas e imutáveis (resumo mais
  anexo obrigatório), com barra de progresso.
- Ação irreversível **"Entregar Parecer"**, com confirmação e seleção da versão a
  entregar.
- Novo cartão de resumo "Parecer Entregue", só de leitura, para as solicitações
  concluídas.

### Melhorias

- Permissões espelhadas em toda a interface, incluindo a verificação de que o
  utilizador é o advogado responsável pela solicitação em causa ou ADMIN.
- Uniformização tipográfica dos títulos em todo o módulo de pareceres, fechando
  uma lacuna que se repetia havia três fases.

### Correções

- Corrigido um defeito bloqueante de validação de formulário no campo de
  prioridade.
- A auditoria desta versão encontrou e corrigiu um defeito de encaminhamento
  anterior a esta versão: a pesquisa de pareceres, entregue na v2.5, estava
  inacessível em funcionamento real.

---

## v2.5 — Módulo de Parecer Jurídico (Fundação)

**Data de entrega:** 30 de junho de 2026
**Âmbito:** 4 fases (61 a 64), 7 planos

### Novas funcionalidades

- **Ciclo de vida completo do parecer jurídico:** Solicitação → Elaboração
  (versionamento imutável, com anexo opcional) → Aprovação interna opcional
  (ADMIN) → Entrega (advogado responsável ou ADMIN, irreversível).
- A solicitação regista cliente, descrição, data, prazo desejado e urgência, pode
  ser associada a um processo existente, e tem estado (Pendente, Em Elaboração,
  Em Revisão, Concluído).
- Cada versão regista número sequencial, autor e data de criação.
- Novo conjunto de permissões dedicado ao módulo — consultar, criar, editar e
  gerir pareceres — atribuído por papel e espelhado na aplicação.
- **Pesquisa avançada**, combinando texto livre no conteúdo da versão mais recente
  com filtros por cliente, advogado, estado e data.

### Melhorias

- Registo automático de auditoria, reutilizando o mecanismo existente, nos cinco
  pontos de transição de estado.

### Correções

- Cinco rondas de revisão de código, com correções aplicadas e reverificadas:
  dois acessos indevidos a dados de outro escritório (críticos), uma condição de
  corrida, uma incompatibilidade de permissão que tornava inalcançável um ramo de
  autorização, e um defeito de junção que excluía resultados válidos da pesquisa.

### Limitações registadas no fecho

- **Apenas fundação, sem interface.** Decisão explícita e repetida nas quatro
  fases: o módulo ficou utilizável apenas ao nível do servidor, sem ecrãs na
  aplicação. Resolvido na v2.6.
- A comparação lado a lado entre versões não foi implementada — apenas listagem e
  detalhe sequencial. **Continua por implementar.**
- Sem índice de texto integral dedicado; a pesquisa nativa foi considerada
  suficiente para o volume da altura.

---

## v2.4 — Ficha de Cliente

**Data de entrega:** 30 de junho de 2026
**Âmbito:** 4 fases (57 a 60), 14 planos, cerca de 30 tarefas

### Novas funcionalidades

- **Numeração sequencial automática de clientes** (formato CLI-0001), por
  escritório.
- Formulário dinâmico que adapta os campos ao tipo de cliente, Particular ou
  Empresa — campos demográficos para Particular; nome comercial, NIF, sede,
  representante legal e cargo para Empresa.
- **Procuração obrigatória** para todos os clientes, com aviso visual não
  bloqueante ("Procuração em falta") e carregamento ou substituição do ficheiro.
- Distintivo "Cliente Avençado" visível na ficha e nas listagens.
- **Acolhimento** completo do processo (apresentado na plataforma como «Intake»)
  registado na própria ficha do cliente:
  descrição, advogados atribuídos (nome, cédula e contacto), administrativos
  atribuídos, documentos entregues e a tratar, deslocações a realizar, e
  honorários propostos (valor, valor por extenso e previsão).
- **Ficha de cliente imprimível** de alta fidelidade ao formulário físico do
  escritório, com formatação para A4 e botão de impressão direta.

### Correções

- A auditoria de integração posterior à execução detetou uma incompatibilidade
  sistémica de convenção de nomes entre o servidor e a aplicação, que invalidava
  9 de 19 requisitos — os dados eram gravados corretamente mas nunca apareciam no
  ecrã. Corrigida antes do fecho.
- Detetada e corrigida uma fuga do resumo criptográfico (*hash*) da senha em dois
  pontos de acesso novos.

### Limitações registadas no fecho

- A correção da auditoria foi verificada estaticamente, campo a campo, e não
  testada ao vivo contra o sistema completo.
- Adiados: restrição de tipos de ficheiro aceites na procuração, formatação de
  moeda na ficha impressa e acesso à ficha em ecrã móvel.

---

## v2.3 — Utilização em Ecrã Móvel

**Data de entrega:** 21 de junho de 2026
**Âmbito:** 4 fases (53 a 56), 8 planos

### Novas funcionalidades

- **Barra de navegação inferior** em ecrã móvel, com acesso rápido aos cinco
  módulos principais, filtrada pelas permissões do utilizador.
- Menu lateral em gaveta sobreposta, aberto por botão de menu, que fecha
  automaticamente ao navegar para outra página.
- Bloco "Hoje", exclusivo do ecrã móvel, com os eventos do dia na Agenda.

### Melhorias

- Listas de Clientes, Agenda, Documentos e Financeiro apresentam cartões
  empilhados em ecrã móvel e tabelas em ecrã grande.
- Tabelas complexas — Partes e Fases do processo — ganharam deslocamento
  horizontal.
- Formulários em coluna única no ecrã móvel (24 conversões em 12 ficheiros).
- Caixas de diálogo passam a subir do fundo do ecrã; áreas de toque com 48 pixels.
- Grelha de indicadores do painel adaptável ao tamanho do ecrã.

---

## v2.2 — Armazenamento Dedicado de Documentos

**Data de entrega:** 19 de junho de 2026 *(data do registo de planeamento; sem etiqueta de versão)*
**Âmbito:** 3 fases (50 a 52)

### Melhorias

- Armazenamento de documentos migrado do sistema de ficheiros local para
  armazenamento de objetos dedicado, com carregamento, descarregamento por
  ligação assinada e remoção.
- Componente de carregamento renovado no ecrã: barra de progresso, arrastar e
  largar, pré-visualização e descarregamento por ligação assinada.
- Serviço de armazenamento publicado no ambiente de produção, com credenciais em
  variáveis de ambiente, processo automático de entrega atualizado e consola
  acessível através do servidor de entrada.

---

## v2.1 — Agenda Avançada

**Data de entrega:** 18 de junho de 2026 *(data do registo de planeamento; sem etiqueta de versão)*
**Âmbito:** 3 fases (47 a 49)

### Novas funcionalidades

- **Notificações na aplicação** para eventos próximos, com contador no cabeçalho e
  painel de notificações.
- **Eventos recorrentes** — criar, listar, apresentar e eliminar eventos com regra
  de recorrência.
- **Arrastar e largar no calendário** para mover um evento para nova data, com
  atualização imediata.

---

## v2.0 — Módulo Financeiro

**Data de entrega:** 18 de junho de 2026 *(data do registo de planeamento; sem etiqueta de versão)*
**Âmbito:** 4 fases (43 a 46), 7 planos

### Novas funcionalidades

- Gestão completa de **honorários e pagamentos**, assegurada ao nível do servidor.
- Estado calculado de cada honorário apresentado na página de Financeiro.
- Resumo financeiro em cartões de indicadores no topo da página.
- Filtros sobre a lista de honorários, e ações de edição e eliminação na ficha
  de cada honorário.
- **Exportação para CSV** da lista de honorários, respeitando os filtros
  aplicados.

> **Nota posterior — a eliminação nunca chegou a funcionar.** As ações de
> eliminar um honorário e de eliminar um pagamento foram construídas e anunciadas
> com esta versão, mas ficaram guardadas por uma permissão — a ação gerir do
> âmbito Financeiro — que não chegou a ser atribuída a nenhum papel, nem ao
> ADMIN. Por esse lapso de configuração, ambas **permaneceram inacessíveis a
> todos os utilizadores desde esta versão até hoje**: nunca foram apresentadas no
> ecrã a ninguém, e o servidor recusa-as mesmo a quem contorne o interface. O
> lapso está corrigido e as duas operações ficam operacionais, para o perfil
> ADMIN, com a próxima atualização. As restantes ações desta versão — filtros,
> edição e exportação — funcionaram desde o início.

### Melhorias

- Correção do contrato de dados entre a aplicação e o servidor.

---

## v1.9 — Melhoria do Módulo de Agendamento

**Data de entrega:** 17 de junho de 2026
**Âmbito:** 3 fases (40 a 42), 3 planos, 10 tarefas

> **Nota de fonte:** o registo de marcos identifica esta versão apenas como
> "v1.9", sem nome. O nome usado aqui — *Melhoria Módulo Agendamento* — é o que
> consta do arquivo de planeamento da própria versão.

### Novas funcionalidades

- **Vista unificada de eventos e prazos** no calendário mensal da agenda, com
  filtros avançados por processo, categoria e estado.

### Melhorias

- Alinhamento completo da camada de dados e das páginas do módulo de agenda com a
  convenção de nomes do servidor, incluindo tratamento de fuso horário nos campos
  de data.
- Indicadores de carregamento dinâmicos.

### Correções

- Validação robusta de intervalos de datas, na aplicação e no servidor, com
  tratamento de erros de leitura de data e mensagens de erro detalhadas no ecrã.

---

## v1.8 — Publicação em Servidor Próprio

**Data de entrega:** 16 de junho de 2026
**Âmbito:** 3 fases (37 a 39), 4 planos, cerca de 9 tarefas

### Novas funcionalidades

- Imagens de execução em várias etapas para o servidor e para a aplicação, ambas
  a correr sem privilégios de administrador e sem segredos embutidos.
- Orquestração com quatro serviços — base de dados, servidor, aplicação e servidor
  de entrada — com volumes persistentes e configuração de produção própria, com
  políticas de reinício e limites de recursos.
- **HTTPS automático** com certificados emitidos e renovados automaticamente,
  parametrizado por nome de domínio.
- Processo automático de integração e entrega contínua: construção, publicação no
  registo de imagens e instalação por ligação segura ao servidor.
- Manual de operação da publicação, cobrindo firewall, variáveis de ambiente,
  arranque dos serviços e segredos necessários.

### Limitações registadas no fecho

- Três itens adiados por exigirem verificação em servidor em funcionamento.

---

## v1.7 — Gestão e Acompanhamento de Processos

**Data de entrega:** 16 de junho de 2026 *(data do registo de marcos; sem etiqueta de versão)*
**Âmbito:** 5 fases (32 a 36), 11 planos, 14 tarefas

### Novas funcionalidades

- **Acolhimento estruturado** — registo de potencial cliente e início do processo
  antes da abertura formal, com campos mínimos obrigatórios por tipo de processo.
- **Verificação de conflito de interesses** estruturada, por cliente, partes
  relacionadas, parte contrária e assunto. O sistema **bloqueia a abertura formal
  do processo** até existir uma decisão registada.
- **Gestão do processo por estados definidos**, com acompanhamento dos prazos
  operacionais associados.
- **Cronologia unificada do processo**, apresentada como separador por omissão,
  com barra de filtros.
- **Trilha de auditoria** consultável, num separador próprio com controlo de
  permissões.
- Governança documental e retenção.
- Painéis e indicadores executivos.

### Melhorias

- O separador de movimentações foi substituído pela cronologia unificada.

### Nota de qualidade

- A auditoria desta versão registou as cinco fases verificadas e concluídas, sem
  lacunas significativas nem dívida técnica.

---

## v1.5 e v1.6 — sem entrega registada

Estas duas numerações **não correspondem a nenhuma versão entregue**. Não têm
etiqueta de versão nem constam do registo de marcos, e nenhuma das suas fases
chegou a ser executada.

Existem requisitos redigidos para ambas — sete para a v1.5 e quatro para a v1.6 —
arquivados dentro do documento de requisitos da v1.7, todos com estado "planeado"
e nenhum concluído. Um arquivo de planeamento anterior regista-as como suspensas
ou adiadas:

- **v1.5 Melhoria funcionalidades processos** — fases 22 a 27, suspensa/adiada.
- **v1.6 Melhoria nfeature de gestão de clientes** [sic] — fases 28 a 31,
  suspensa/adiada.

Parte do âmbito pretendido acabou por ser coberto por versões posteriores — a
gestão de processos pela v1.7 e pela v2.9, e a gestão de clientes pela v2.4, v2.7
e v2.8 — mas **não existe registo formal que confirme essa correspondência**.
Registamos a lacuna em vez de a preencher com suposições.

---

## v1.4 — Melhoria do Módulo de Clientes

**Data de entrega:** 3 de junho de 2026 *(data do arquivo de planeamento; sem etiqueta de versão)*
**Âmbito:** 4 fases (18 a 21)

### Novas funcionalidades

- **Contactos e Notas** do cliente como sub-recursos com gestão própria no
  detalhe.
- **Importação de clientes por CSV**, com validação e comunicação de erros.
- **Exportação de clientes por CSV**, respeitando os filtros aplicados.
- **Deteção e fusão de clientes duplicados**, com critérios automáticos por NIF,
  correio eletrónico e telefone, num fluxo guiado.

### Melhorias

- Filtros avançados (por exemplo, tipo, estado e localidade) e pesquisa
  simultânea por nome, NIF, telefone e correio eletrónico na lista de clientes.
- Estados de carregamento consistentes, estados vazios e paginação na listagem.

---

## v1.3 — Reforço de Segurança

**Data de entrega:** 3 de junho de 2026 *(data do arquivo de planeamento; sem etiqueta de versão)*
**Âmbito:** 6 fases (12 a 17)

### Novas funcionalidades

- Sistema de mensagens de notificação e retorno global na aplicação.
- Ações da interface passam a ser controladas por permissões, de forma consistente.

### Melhorias

- Reforço de configuração, gestão de segredos e cabeçalhos de segurança.
- **Isolamento de dados entre escritórios** reforçado, em fase dedicada ao tema.
- Reforço da autenticação e da gestão de sessão.
- Aplicação efetiva do controlo de acessos por papel no servidor.
- Ferramentas de análise de segurança introduzidas na infraestrutura do projeto.

---

## v1.2 — Painel de Utilizador e Passagem a Servidor Real

**Data de entrega:** 27 de maio de 2026
**Âmbito:** 1 fase, 1 plano

### Novas funcionalidades

- **Painel de utilizador (perfil)**, com edição dos próprios dados.

### Melhorias

- **Migração do servidor simulado para um servidor real**, com base de dados
  relacional, e integração através de reencaminhamento de pedidos.
- Dados iniciais reais, com registos coerentes e recálculo da conta corrente.

---

## v1.1 — Alinhamento de Interface

**Data de entrega:** 27 de maio de 2026
**Âmbito:** 4 fases (7 a 10), 6 planos

### Novas funcionalidades

- Novo esqueleto de aplicação: barra lateral escura com estados ativo e inativo, e
  barra superior com pesquisa, identificação da instituição e ações de utilizador.
- **Modo claro e modo escuro.**

### Melhorias

- Componentes de interface reutilizáveis — distintivos, tabelas, paginação — e
  respetiva aplicação nas páginas principais.
- Ajuste visual de identidade institucional: contornos definidos e contraste
  elevado.

---

## v1.0 — Versão Mínima Viável (MVP)

**Data de entrega:** 26 de maio de 2026 *(data do registo de marcos; a etiqueta de versão correspondente é de 27 de maio de 2026)*
**Âmbito:** 6 fases (1 a 6), 14 planos

### Novas funcionalidades

- **Aplicação Web completa** com os seis módulos base: Painel, Clientes,
  Processos, Agenda, Documentos e Financeiro.
- **Clientes** — criação, consulta, edição, eliminação, filtros e conta corrente.
- **Processos** — criação, consulta, edição, eliminação, mais partes, fases e
  movimentações.
- **Agenda e eventos** — gestão completa, filtros de criticidade e conclusão de
  evento.
- **Documentos** — listagem, carregamento, descarregamento e remoção.
- **Financeiro** — honorários e pagamentos, com impacto na conta corrente do
  cliente.
- **Painel** com indicadores básicos.
- Autenticação com início de sessão, renovação de sessão e leitura do utilizador
  autenticado.
- **Controlo de acessos por papel** na interface (por exemplo, o módulo
  Financeiro visível apenas para ADMIN e TECNICO).
- Estrutura multi-escritório presente desde a origem, nos dados iniciais.

### Nota de âmbito

Nesta versão a autenticação e o acesso aos dados eram simulados dentro da própria
aplicação. A passagem a servidor real ocorreu na v1.2.

---

## Anexo — Tabela-resumo das versões

A coluna **Designação** contém a designação de cada versão. A coluna
**Designação técnica original** contém o nome tal como consta dos registos
internos do projeto, para efeitos de cruzamento. Reproduzimo-lo literalmente: as
gralhas e a acentuação em falta de alguns desses nomes são as do próprio registo,
assinaladas com *[sic]*.

| Versão | Designação | Designação técnica original | Data | Origem da data |
|---|---|---|---|---|
| v2.16 | Distribuição Multi-Escritório e Faturação por Utilizadores | Distribuição Multi-Tenant e Faturação por Utilizadores | 30/07/2026 | Etiqueta de versão |
| v2.15 | Reposicionamento Institucional (SIJ) | Reposicionamento SIJ | 27/07/2026 | Etiqueta de versão |
| v2.14 | Melhorias de Interface e Pesquisa Global | UI/UX Melhorias | 22/07/2026 | Etiqueta de versão |
| v2.13 | Renovação do Sistema de Design da Interface | Refactor UI/UX (shadcn/ui) | 18/07/2026 | Etiqueta de versão |
| v2.12 | Sítio Institucional Público | Landing Page | 15/07/2026 | Etiqueta de versão |
| v2.11 | Auditoria Técnica e Notificações Avançadas | Auditoria Técnica e Notificações Avançadas | 14/07/2026 | Etiqueta de versão |
| v2.10 | Notificações e Alertas | Notificações e Alertas | 10/07/2026 | Etiqueta de versão |
| v2.9 | Aprofundamento do Módulo de Processos | Melhoria Módulo Processos | 08/07/2026 | Etiqueta de versão |
| v2.8 | Reestruturação da Ficha de Cliente | Refatoração Ficha de Cliente | 06/07/2026 | Etiqueta de versão |
| v2.7 | Simplificação da Gestão de Clientes | Melhoria Gestão de Clientes | 02/07/2026 | Etiqueta de versão |
| v2.6 | Módulo de Parecer Jurídico (Interface) | Módulo de Parecer Jurídico — UI | 01/07/2026 | Etiqueta de versão |
| v2.5 | Módulo de Parecer Jurídico (Fundação) | Módulo de Parecer Jurídico | 30/06/2026 | Etiqueta de versão |
| v2.4 | Ficha de Cliente | Ficha de Cliente | 30/06/2026 | Etiqueta de versão |
| v2.3 | Utilização em Ecrã Móvel | Responsividade App | 21/06/2026 | Etiqueta de versão |
| v2.2 | Armazenamento Dedicado de Documentos | Document Storage MinIO | 19/06/2026 | Registo de planeamento |
| v2.1 | Agenda Avançada | Agenda Avançada | 18/06/2026 | Registo de planeamento |
| v2.0 | Módulo Financeiro | Módulo Financeiro | 18/06/2026 | Registo de planeamento |
| v1.9 | Melhoria do Módulo de Agendamento | Melhoria Módulo Agendamento | 17/06/2026 | Etiqueta de versão |
| v1.8 | Publicação em Servidor Próprio | Deployment para VPS | 16/06/2026 | Etiqueta de versão |
| v1.7 | Gestão e Acompanhamento de Processos | Melhoria no modulo de gestao e acompanhamento de processos [sic] | 16/06/2026 | Registo de marcos |
| v1.6 | *(sem entrega registada)* | Melhoria nfeature de gestão de clientes [sic] | — | — |
| v1.5 | *(sem entrega registada)* | Melhoria funcionalidades processos | — | — |
| v1.4 | Melhoria do Módulo de Clientes | Melhoria módulo clientes | 03/06/2026 | Arquivo de planeamento |
| v1.3 | Reforço de Segurança | Security Check | 03/06/2026 | Arquivo de planeamento |
| v1.2 | Painel de Utilizador e Passagem a Servidor Real | Utilizador | 27/05/2026 | Etiqueta de versão |
| v1.1 | Alinhamento de Interface | UI/UX Alignment | 27/05/2026 | Etiqueta de versão |
| v1.0 | Versão Mínima Viável (MVP) | MVP | 26/05/2026 | Registo de marcos |

**Total:** 25 versões entregues em 65 dias, entre 26 de maio e 30 de julho de 2026.

O último registo de alteração no repositório é de 5 de agosto de 2026. É posterior
à v2.16 e **não pertence a nenhuma versão entregue** — fica, por isso, fora deste
registo.

---

*Documento gerado a partir dos registos internos de planeamento e execução do
projeto ALCv. A designação técnica original de cada versão, indicada na terceira
coluna da tabela-resumo, permite cruzar este documento com esses registos. O
documento complementar, **Relatório de Versão**, cobre as mesmas versões pela
ótica do benefício.*
