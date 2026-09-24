# LexCV — Documento de Requisitos de Produto (PRD)

- **Documento:** Requisitos de Produto
- **Produto:** LexCV — Plataforma Institucional de Gestão Jurídica
- **Destinatário:** Entidade contratante (escritório de advogados ou instituição)
- **Versão do produto descrita:** v2.16, encerrada a 30 de julho de 2026
- **Data de emissão:** agosto de 2026

> Este documento descreve **o que a plataforma faz**. Funcionalidades ainda não
> construídas não são descritas. Onde algo é limitação conhecida, está dito de
> forma explícita.

---

## 1. Objetivo do produto

O LexCV é uma plataforma institucional de gestão jurídica para Cabo Verde, com
toda a linguagem do produto em português e a terminologia corrente da prática
jurídica: cliente, processo, parte, fase, movimentação, evento, prazo,
honorário, parecer e documento.

O objetivo é permitir que um escritório ou instituição gira o ciclo completo de
processos jurídicos — cliente, processo, prazos, documentos e financeiro — num
único painel, com isolamento rigoroso por escritório. Uma mesma instalação serve
vários escritórios, sem que nenhum veja os dados dos restantes.

---

## 2. Utilizadores-alvo

A plataforma reconhece **cinco papéis**. Quatro operam dentro de um escritório;
o quinto pertence a quem opera a plataforma e é transversal.

### 2.1 Papéis do escritório

| Papel | Perfil típico | O que faz |
|---|---|---|
| **ADMIN** | Sócio ou responsável do escritório | Acesso a todos os módulos. Gere os utilizadores do seu escritório e consulta a matriz de controlo de acesso. |
| **ADVOGADO** | Advogado com processos atribuídos | Gere clientes, processos (incluindo criação e formalização), agenda, documentos e pareceres. Consulta o financeiro. |
| **TECNICO** | Técnico jurídico | Gere a agenda. Consulta clientes, processos, documentos, financeiro e pareceres. |
| **ASSISTENTE** | Assistente administrativo | Gere clientes. Consulta processos, agenda, documentos e pareceres. |

A coluna "Perfil típico" é uma correspondência de leitura entre a função
exercida no escritório e o papel do sistema. O que determina o acesso de cada
utilizador é o conjunto de permissões do papel que lhe for atribuído, e não o
cargo que ocupa.

### 2.2 Papel da plataforma

| Papel | Perfil típico | O que faz |
|---|---|---|
| **PLATAFORMA_ADMIN** | Operador do LexCV | Cria e lista escritórios, ajusta plano e limite de utilizadores, suspende escritórios, consulta o relatório de utilização e define a matriz de permissões da plataforma. Não tem qualquer permissão sobre dados de clientes, processos ou documentos de nenhum escritório. |

O papel **PLATAFORMA_ADMIN** vive num escritório reservado ("LexCV") e opera
exclusivamente pela consola `/plataforma`. A suspensão de um escritório corta as
sessões já abertas desse escritório em poucos segundos.

### 2.3 Matriz de permissões por papel

As permissões seguem a convenção `âmbito:ação`. O vocabulário de ações tem quatro
termos — `view`, `create`, `edit` e `manage` — que correspondem, na prática, a
consultar (`view`), criar (`create`), criar e alterar (`edit`) e governar o
âmbito, incluindo as operações mais sensíveis (`manage`). Esta correspondência é
a leitura de uso das quatro ações; o nome técnico é o que o sistema regista em
cada permissão.

**As quatro ações não existem em todos os âmbitos.** Cada âmbito define apenas as
ações de que necessita, pelo que o total de permissões atribuíveis não resulta da
multiplicação do número de âmbitos pelo número de ações. O sistema define
**20 permissões atribuíveis**, distribuídas pelos sete âmbitos de negócio e pelos
dois âmbitos de administração. É este conjunto que governa a autorização. O ecrã
de Controlo de Acesso apresenta o catálogo de permissões agrupado por âmbito,
para consulta pelo administrador do escritório.

| Âmbito | ADMIN | ADVOGADO | TECNICO | ASSISTENTE |
|---|---|---|---|---|
| Clientes | consultar, editar | consultar, editar | consultar | consultar, editar |
| Processos | consultar, criar, editar, gerir | consultar, criar, editar, gerir | consultar | consultar |
| Agenda | consultar, editar | consultar, editar | consultar, editar | consultar |
| Documentos | consultar, editar | consultar, editar | consultar | consultar |
| Financeiro | consultar, editar, gerir | consultar | consultar | — |
| Pareceres | consultar, criar, editar, gerir | consultar, criar, editar | consultar | consultar |
| Notificações | consultar | consultar | consultar | consultar |
| Gestão de utilizadores | sim | — | — | — |
| Matriz de controlo de acesso | consulta | — | — | — |

Notas:

- O **ADMIN** detém todas as permissões atribuíveis definidas no sistema. As
  quatro ações (`view`, `create`, `edit`, `manage`) não existem em todos os
  âmbitos: nos âmbitos Clientes, Agenda e Documentos, a ação `manage` não está
  definida e a ação `edit` é a mais forte disponível; no âmbito Notificações,
  apenas a ação `view` está definida.
- No âmbito **Financeiro**, a ação gerir (`manage`) é a que autoriza as operações
  mais sensíveis do âmbito, entre as quais a eliminação de um honorário ou de um
  pagamento. Esta ação **nunca chegou a ser atribuída a nenhum papel**, em nenhuma
  versão do produto, por lapso de configuração de permissões: existe no sistema,
  mas ninguém a detém — nem o ADMIN. Com a próxima atualização da instalação passa a estar atribuída
  **exclusivamente ao ADMIN**, e nenhum outro papel a detém. É por essa
  atualização que o catálogo de permissões atribuíveis passa de 19 para as 20
  descritas acima. Ver §3.6.
- A matriz acima é a configuração de origem da plataforma. É **fixa por
  plataforma**: o administrador do escritório vê-a, mas a sua alteração está
  reservada ao PLATAFORMA_ADMIN.
- A verificação é feita **em duas camadas** — no interface, para esconder o que o
  utilizador não pode usar, e no servidor, que recusa o pedido mesmo que o
  interface seja contornado.

---

## 3. Módulos e funcionalidades

### 3.1 Painel

- Indicadores de atividade do escritório.
- Prazos urgentes.
- Processos recentes.
- Atividade recente.
- Estados de carregamento e de lista vazia próprios, sem ecrãs em branco.

Existe ainda um **painel específico de processos**, com a leitura agregada da
carteira processual.

### 3.2 Clientes

- Lista com pesquisa, filtros, ordenação, paginação, escolha de colunas e
  exportação.
- Registo de **Particular** ou **Empresa**, com formulário que se adapta ao tipo
  escolhido.
- Numeração sequencial por escritório (formato `CLI-0001`).
- **NIF obrigatório** para ambos os tipos, com validação de 9 dígitos aplicada no
  interface e no servidor.
- Documento de identificação por tipo de cliente: Particular (CNI, BI ou
  Passaporte); Empresa (Registo Comercial).
- Marcação de **cliente avençado**.
- Ficha do cliente organizada em separadores:
  - **Dados** — identificação, morada, dados da triagem inicial, advogados e
    administrativos atribuídos, procuração, honorários propostos.
  - **Contactos e Notas**.
  - **Processos** — processos do cliente.
  - **Pareceres** — pareceres do cliente.
  - **Documentos Entregues** — carregamento real de ficheiros.
  - **Documentos a Tratar**.
  - **Deslocações**.
- Edição em contexto na própria ficha, com alternância entre consultar e editar.
- **Ficha de Cliente imprimível**, reproduzindo o formulário institucional.
- **Fusão de clientes duplicados**.
- Criação de processo ou de parecer diretamente a partir da ficha, já com o
  cliente preenchido.

### 3.3 Processos

- Lista com pesquisa, filtro por estado sempre visível, filtros avançados,
  ordenação, paginação e escolha de colunas. (A exportação de processos está
  prevista: o botão existe no ecrã, desativado, e a funcionalidade ainda não foi
  construída.)
- Dados do processo, incluindo **juízo** (texto livre) e **origem** (Petição
  Inicial ou Notificações Avulsas), obrigatória na triagem inicial e imutável
  após a formalização.
- Verificação de conflitos na triagem inicial, com decisão registada em
  auditoria.
- Ficha do processo organizada em separadores:
  - **Linha Temporal** — movimentações e evolução do processo.
  - **Partes**.
  - **Fases**.
  - **Decisões** — data, tipo (Despacho, Decisão Interlocutória, Sentença,
    Acórdão), resumo e anexo.
  - **Factos** — descrição, data e ordem reordenável.
  - **Testemunhas** — nome, contacto, tipo (Autor ou Réu) e notas.
  - **Documentos**.
  - **Auditoria** — visível apenas a quem detém `processos:manage`.
- **Formalização** do processo (triagem para ativo), que cria automaticamente o
  honorário associado, sempre com valor por preencher.
- **Reatribuição de responsável**, com alerta para o novo responsável.
- **Termo de Honorários imprimível**, combinando cliente, processo e honorário. A
  impressão é bloqueada enquanto o valor total do honorário estiver em branco.

### 3.4 Agenda

- Visão unificada de **eventos** e **prazos**.
- Filtros por processo, categoria e estado.
- Criação, edição e marcação de conclusão.
- Seletor de data com calendário em português.
- Destaque de **prazos críticos** segundo um critério único, partilhado por toda
  a aplicação.

### 3.5 Documentos

- Lista com pesquisa, filtros, ordenação, paginação e escolha de colunas.
- Carregamento de ficheiros com barra de progresso.
- Descarregamento e remoção.
- Classificação por tipo (campo livre pesquisável, sem lista fechada) e por nível
  de confidencialidade.
- Ligação ao **cliente** ou ao **processo**.
- Descarregamentos e eliminações registados em auditoria.

### 3.6 Financeiro

- **Honorários** — registo, valor total, descrição e data de acordo.
- **Pagamentos** — registo por honorário, com valor, data e método.
- Impacto direto na **conta corrente** do cliente.
- Lista com ordenação, paginação, escolha de colunas e exportação.
- **Eliminação de honorários e de pagamentos** — reservada ao perfil **ADMIN**.
  É a ação gerir do âmbito Financeiro que a autoriza, e nenhum outro papel a
  detém (ver §2.3). Aos restantes papéis a operação não é sequer apresentada no
  ecrã, e o servidor recusa-a mesmo que o interface seja contornado.

> **Operação existente, mas nunca acessível.** A eliminação de honorários e de
> pagamentos **existe no produto desde as primeiras versões do módulo
> Financeiro** — foi construída com a v2.0, de 18 de junho de 2026, e nunca foi
> retirada desde então. Sucede que a ação gerir do âmbito Financeiro, que é a
> única que a autoriza, **nunca chegou a ser atribuída a nenhum papel** — nem ao
> ADMIN. Por esse lapso de configuração de permissões, a operação **nunca esteve
> acessível a nenhum utilizador**, em momento algum de todo o histórico
> documentado do produto: não é apresentada no ecrã a ninguém, e o servidor
> recusa-a mesmo a quem contorne o interface. O lapso está corrigido, e a
> operação **fica operacional, para o perfil ADMIN, com a próxima
> atualização da instalação**. O que se descreve a seguir até ao fim desta secção
> é o comportamento da operação a partir desse momento. O restante da secção —
> honorários, pagamentos, conta corrente e listas — descreve funcionamento em
> vigor, sem ressalva.

Uma vez acessível, as duas eliminações não obedecem à mesma regra:

- **Eliminar um honorário** só é possível enquanto o honorário não tiver
  pagamentos registados. Havendo pelo menos um pagamento, a operação é recusada
  e o motivo é apresentado ao utilizador: *"Não é possível eliminar um honorário
  com pagamentos registados"*. Para eliminar o honorário, é necessário eliminar
  antes, um a um, os pagamentos que lhe estão associados. Esta salvaguarda
  protege o histórico financeiro do processo.
- **Eliminar um pagamento** não tem salvaguarda equivalente: um pagamento pode
  ser eliminado a qualquer momento, sem condição prévia. Ao eliminá-lo, o valor
  pago é revertido na **conta corrente** do cliente, sendo subtraído ao saldo —
  o inverso exato do que sucede quando o pagamento é registado.

**Limite conhecido na eliminação de um pagamento.** A eliminação do pagamento e
a correção do saldo da conta corrente não estão ligadas entre si. Se a correção
do saldo não for possível no momento, o pagamento é eliminado à mesma e o
utilizador não é avisado dessa falha: a ocorrência fica, na maioria dos casos,
no registo técnico da plataforma, ao dispor de quem a opera. Nesse caso, o saldo
da conta corrente do cliente permanece acima do total efetivamente recebido — o
sistema regista como pago um valor que já não tem pagamento correspondente.
Recomenda-se, por isso, conferir a conta corrente do cliente depois de eliminar
um pagamento.

### 3.7 Pareceres jurídicos

- Solicitação de parecer, com cliente e advogado responsável.
- **Versionamento imutável**: cada nova versão exige anexo e não altera as
  anteriores.
- Aprovação e **entrega irreversível**, com vista dedicada "Parecer Entregue".
- Histórico completo, com as versões antigas recolhidas.
- Linha temporal do parecer.
- Pesquisa avançada específica de pareceres.
- Regra adicional: certas operações exigem ser o advogado responsável pelo
  parecer ou ter perfil de administração.

### 3.8 Notificações

- Notificações **dentro da aplicação**, persistidas no sistema.
- Nove categorias: entrada em nova fase, novo documento, processo atribuído,
  parecer atribuído, prazo próximo, prazo vencido, evento próximo, evento
  vencido e honorário em atraso.
- Sino com contador de não lidas, lista das mais recentes e opção de marcar todas
  como lidas.
- Página dedicada de notificações, com filtros por categoria e por estado de
  leitura, e paginação.
- **Alertas diários automáticos** de prazos, eventos e honorários, executados às
  06:00 no fuso `Atlantic/Cape_Verde`, sem repetições indevidas.
- **Adiamento** de lembrete de prazo, com predefinições de 1, 3 e 7 dias.
- **Preferências por utilizador**: qualquer categoria pode ser silenciada, com
  uma exceção deliberada — **prazo vencido é sempre entregue**.
- As notificações de processo alcançam toda a equipa de advogados e
  administrativos associada ao cliente, não apenas o responsável.
- Limitação conhecida: os alertas gerados pelo processamento diário
  (prazos, eventos e honorários) são dirigidos ao responsável, não à equipa
  alargada.

### 3.9 Pesquisa global

- Pesquisa transversal a **clientes, processos, documentos e pareceres**.
- Acessível por `Ctrl+K` (ou `⌘K`), com resultados agrupados por tipo e destaque
  do texto encontrado.
- Ordenação de resultados por correspondência exata, depois por prefixo, depois
  por ocorrência parcial; insensível a acentos.
- Os resultados respeitam o escritório do utilizador e as suas permissões por
  tipo de entidade.
- "Ver todos" transporta o termo pesquisado para a lista correspondente.

### 3.10 Definições

Organizadas em separadores, visíveis conforme a permissão:

- **O Meu Perfil**.
- **Segurança** — alteração de palavra-passe.
- **Gestão de Utilizadores** — criar, editar, ativar/desativar e remover
  utilizadores do escritório, com indicador "X/Y utilizadores" e recusa de
  criação ao atingir o limite do plano. Um administrador não pode remover a sua
  própria conta.
- **Controlo de Acesso (RBAC)** — consulta da matriz de permissões, identificada
  como gerida pela plataforma.
- **Notificações** — preferências de silenciamento por categoria.

### 3.11 Consola de plataforma

Exclusiva do papel PLATAFORMA_ADMIN:

- Criar um escritório novo, já com o seu administrador inicial.
- Listar escritórios.
- Ajustar plano e limite de utilizadores.
- Suspender e reativar escritórios.
- **Relatório de utilização** — nome, plano, limite e utilizadores ativos por
  escritório, para suportar a faturação. É um relatório de leitura, sem qualquer
  alteração de dados.

### 3.12 Página pública

- Sítio institucional autónomo, servido na raiz do domínio.
- Secções de apresentação, funcionalidades, confiança institucional e contacto.
- Modo claro e modo escuro.
- Navegação adaptada a telemóvel.
- Ligação de entrada para a aplicação.

---

## 4. Requisitos transversais

### 4.1 Autenticação e sessão

- Entrada por correio eletrónico e palavra-passe.
- A sessão é mantida por cookies que os scripts da página não conseguem ler.
- Renovação automática de sessão.
- Palavras-passe guardadas apenas em forma cifrada (algoritmo BCrypt).
- Proteção contra tentativas repetidas de entrada, com bloqueio temporário por
  conta. O endereço de origem entra na contagem, mas em produção o servidor
  observa o endereço da porta de entrada e não o do cliente.

### 4.2 Isolamento entre escritórios

- Cada registo pertence a um escritório.
- Todas as leituras e escritas são delimitadas pelo escritório do utilizador
  autenticado.
- As restrições de unicidade são por escritório: por exemplo, o mesmo número de
  documento pode existir em escritórios diferentes, mas não duas vezes no mesmo.

### 4.3 Primeira instalação

- Assistente de arranque que só está disponível enquanto o sistema não estiver
  inicializado, com indicador de progresso.
- Instalações não inicializadas são encaminhadas automaticamente para esse
  assistente.

### 4.4 Interface

- Web responsiva:
  - menu lateral em ecrã grande;
  - gaveta e navegação inferior em telemóvel;
  - listas em cartões nos ecrãs pequenos;
  - formulários em coluna única;
  - alvos de toque adequados.
- Modo claro e modo escuro.
- Sistema de componentes único e consistente em toda a aplicação.
- Padrão único de tabela para as listas principais, com ordenação, paginação e
  escolha de colunas.

### 4.5 Auditoria

Ficam registados em histórico, com autor e momento:

- transições de estado de processo;
- decisões de verificação de conflitos;
- descarregamento de documento;
- eliminação de documento.

---

## 5. Limites conhecidos

- Não existe integração técnica com o SIJ.
- Não há envio de notificações por correio eletrónico nem push.
- Não há cálculo automático de honorários nem cômputo automático de prazos
  processuais.
- Não há módulo de contabilidade nem ERP.
- Não existe aplicação móvel nativa.
- Séries de eventos sem data de fim não são suportadas; a edição de uma série
  aplica-se a "esta instância" ou a "toda a série".
- O alerta de honorário em atraso conta os dias sem pagamento total desde a data
  de acordo; não existe campo de data de vencimento próprio.

---

> **Nota:** este documento descreve capacidades de software e não constitui
> parecer jurídico. Qualquer utilização contratual carece de revisão por
> advogado.
