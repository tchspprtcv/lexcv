# ALCv — Especificação de Arquitetura

- **Documento:** Especificação de Arquitetura (descrição técnica para o contratante)
- **Produto:** ALCv — Plataforma Institucional de Gestão Jurídica
- **Destinatário:** Entidade contratante e respetivo responsável de sistemas
- **Versão do produto descrita:** v2.16, encerrada a 30 de julho de 2026
- **Data de emissão:** agosto de 2026

> Este documento descreve a arquitetura em texto, sem diagramas. Destina-se a um
> leitor técnico do lado do contratante — responsável de sistemas, auditor ou
> consultor — e não à equipa de desenvolvimento. O ALCv é uma plataforma
> institucional de gestão jurídica para Cabo Verde (clientes, processos, agenda
> e prazos, documentos e honorários), que serve vários escritórios a partir da
> mesma instalação.

---

## 1. Visão geral

O ALCv é composto por **seis serviços** que correm em conjunto no mesmo
servidor, cada um em contentor próprio, ligados por uma rede privada interna.
Apenas um deles está exposto à Internet.

| # | Serviço | Papel |
|---|---|---|
| 1 | **caddy** | Porta de entrada. Termina o HTTPS e encaminha cada pedido para o serviço certo. É o único serviço exposto à Internet. |
| 2 | **webpage** | Página pública institucional, servida na raiz do domínio. |
| 3 | **frontend** | Aplicação de gestão (a plataforma propriamente dita), acedida pelos utilizadores autenticados. |
| 4 | **backend** | Servidor de negócio. Concentra todas as regras, validações e verificações de acesso, e disponibiliza-as à aplicação de gestão. |
| 5 | **postgres** | Base de dados relacional. Guarda todos os dados de negócio. |
| 6 | **minio** | Armazenamento de objetos. Guarda os ficheiros carregados (documentos e anexos de parecer). |

Os nomes da coluna "Serviço" são os nomes técnicos dos contentores, tal como
aparecem na configuração da instalação. No restante do documento, `frontend`
designa a aplicação de gestão e `backend` designa o servidor de negócio.

Nenhum destes serviços, exceto o `caddy`, precisa de estar acessível a partir do
exterior.

---

## 2. Camada de apresentação

### 2.1 Aplicação de gestão (`frontend`)

- Construída em **Next.js 16** (App Router) com **React 19**, em TypeScript.
- Renderização mista: parte no servidor, parte no navegador.
- Interface responsiva, com modo claro e escuro.
- Sistema de componentes único, assente em primitivos acessíveis (menus,
  separadores, diálogos, calendário, paleta de comandos, tabelas).
- Toda a obtenção de dados passa por uma camada única de acesso à API, com
  colocação em cache, revalidação e tratamento uniforme de erros.
- Os formulários validam os dados no navegador antes de submeter. Esta validação
  é uma conveniência para o utilizador — **a validação que conta é a do
  servidor**, que é sempre repetida.

### 2.2 Página pública (`webpage`)

- Aplicação **Next.js 16** separada e independente da aplicação de gestão.
- Serve o sítio institucional na raiz do domínio.
- Não partilha sessão nem estado de autenticação com a aplicação de gestão.
- Lê a identificação de marca da plataforma através de um único ponto público de
  leitura, sem autenticação e sem cookies.
- As duas aplicações coexistem no mesmo domínio através do mecanismo de
  multi-zonas do Next.js, que atribui a cada uma um prefixo próprio para os seus
  ficheiros estáticos, evitando colisões.

---

## 3. Camada de aplicação — servidor de negócio (`backend`)

- **Spring Boot 3.4.1** sobre **Java 23**, construído com Maven.
- Expõe uma API REST sob o prefixo `/api/v1`.
- Organização interna por responsabilidade: controladores (pontos de entrada
  HTTP), serviços (regras de negócio), repositórios (acesso a dados), modelos
  (entidades) e objetos de transferência de dados.
- Persistência por JPA/Hibernate.
- Áreas funcionais servidas: clientes, processos e respetivas partes, fases,
  movimentações, decisões, factos e testemunhas; eventos e prazos; documentos;
  honorários e pagamentos; indicadores do painel; pareceres; notificações;
  pesquisa global; administração do escritório; administração da plataforma;
  arranque inicial; e leitura pública de marca.
- **Trabalho agendado:** um processamento diário corre às 06:00 no fuso
  `Atlantic/Cape_Verde` e gera os alertas de prazos, eventos e honorários. É
  tolerante a falhas por entidade — um erro numa entidade não interrompe o
  processamento das restantes — e não repete alertas já emitidos.

---

## 4. Comunicação entre o navegador e o servidor

O navegador **nunca contacta diretamente o serviço `backend`**.

1. A aplicação de gestão declara uma reescrita de rota: todos os pedidos para
   `/api/v1/...` são reencaminhados internamente para o endereço do serviço
   `backend`.
2. Para o navegador, a API vive na **mesma origem** que a aplicação.
3. O endereço interno do `backend` é configuração de servidor e nunca chega ao
   navegador.

Consequências práticas:

- Não há partilha de recursos entre origens diferentes no caminho normal de
  utilização, o que reduz a superfície de ataque.
- Os cookies de sessão são cookies de primeira parte, o que os torna imunes às
  políticas de bloqueio de cookies de terceiros.
- Em produção, o `caddy` faz o encaminhamento equivalente: os pedidos para
  `/api/*` vão para o `backend`; a raiz `/` e os estáticos da página pública vão
  para o `webpage`; todo o restante vai para o `frontend`.

---

## 5. Autenticação

### 5.1 Mecanismo

- A entrada faz-se por correio eletrónico e palavra-passe, num único ponto de
  autenticação.
- Em caso de sucesso, o servidor emite dois testemunhos (*tokens*) assinados —
  um de acesso e um de renovação, este de duração mais longa — e entrega-os ao
  navegador **em cookies `httpOnly`**. A validade de cada testemunho é
  configurável; a duração dos cookies que os transportam está fixada no código,
  em 24 horas e 30 dias respetivamente (ver pontos 9.2 e 11.7).
- Um cookie `httpOnly` **não é legível pelo JavaScript da página**. Um script
  malicioso injetado na página não consegue ler o testemunho de sessão.
- Não existe qualquer testemunho guardado em `localStorage`, `sessionStorage` ou
  em variáveis de JavaScript.
- Em cada pedido, um filtro do servidor lê o cookie, valida a assinatura e o
  prazo de validade do testemunho e estabelece a identidade do utilizador para
  esse pedido.
- O testemunho transporta a identidade do utilizador, o seu escritório e as suas
  permissões.
- As palavras-passe são guardadas com **BCrypt**, algoritmo de derivação lenta e
  com sal; não são recuperáveis, apenas verificáveis.

### 5.2 Proteções adicionais

- **Limitação de tentativas de entrada:** tentativas falhadas conduzem a um
  bloqueio temporário. A contagem combina o endereço de correio eletrónico com o
  endereço de origem do pedido **tal como o servidor o observa**. Em produção, o
  pedido chega ao servidor de negócio através da porta de entrada, pelo que o
  endereço observado é o desta e não o do cliente: na prática, o bloqueio atua
  hoje **por conta** e não por origem. Trava a insistência sobre uma conta; não
  distingue atacantes entre si. O tratamento do endereço reencaminhado está
  previsto na configuração de produção que não chega a ser ativada — ver ponto
  11.8.
- **Sessão sem estado no servidor:** o servidor não mantém sessão em memória, o
  que evita ataques de fixação de sessão.
- **Cabeçalhos de segurança** ativos, incluindo a recusa de apresentação da
  aplicação dentro de molduras (`frame`) de outros sítios.
- **Origens permitidas** para pedidos entre origens são configuradas
  explicitamente na instalação.
- **Pontos de acesso sem autenticação** limitados a uma lista fixa e curta:
  entrada, renovação, saída, estado e inicialização do arranque, e leitura
  pública de marca. Todo o restante exige autenticação.
- **Suspensão imediata:** quando um escritório é suspenso, as sessões já abertas
  desse escritório deixam de funcionar em poucos segundos.
- **HTTPS obrigatório na fronteira:** o `caddy` obtém e renova automaticamente o
  certificado junto da Let's Encrypt e redireciona o tráfego HTTP para HTTPS.

---

## 6. Controlo de acessos (RBAC)

### 6.1 Modelo

O controlo de acessos assenta em três conceitos:

- **Papel** — ADMIN, ADVOGADO, TECNICO, ASSISTENTE e PLATAFORMA_ADMIN.
- **Permissão** — uma cadeia no formato `âmbito:ação`.
- **Atribuição** — cada papel detém um conjunto de permissões.

Os âmbitos existentes são: `clientes`, `processos`, `agenda`, `documentos`,
`financeiro`, `pareceres`, `notificacoes`, mais dois âmbitos de administração,
`users` e `rbac`.

O vocabulário de ações tem quatro termos: `view`, `create`, `edit` e `manage`.
Nem todos os âmbitos definem os quatro: cada âmbito declara apenas as ações de
que necessita. O conjunto efetivamente atribuível é de **20 permissões**.

Exemplos reais: `clientes:view`, `processos:edit`, `processos:manage`,
`financeiro:edit`, `financeiro:manage`, `pareceres:create`.

### 6.2 Aplicação em duas camadas

- **No servidor** — cada ponto de entrada da API declara a permissão exigida. A
  verificação é feita antes de o método correr. Um pedido sem a permissão
  necessária é recusado, independentemente de como foi construído.
- **No interface** — a mesma lógica é espelhada para esconder menus, botões e
  páginas que o utilizador não pode usar, e para mostrar um ecrã de acesso
  negado quando o endereço é acedido diretamente.

Na prática, o interface é conveniência e **o servidor é a autoridade**: as duas
camadas têm de declarar a mesma regra, e é a verificação do servidor que decide.
A formulação é a leitura do modelo de segurança, não um princípio formalmente
declarado no produto.

### 6.3 Encadeamento de ações

**No interface**, uma ação mais forte satisfaz uma mais fraca: quem detém
`manage` satisfaz `edit`, quem detém `edit` satisfaz `create`, e qualquer uma
satisfaz `view`. Este encadeamento é uma conveniência de apresentação, que evita
esconder um ecrã a quem tem, de facto, poder para o usar.

**No servidor, a verificação é de correspondência exata**: cada ponto de entrada
exige literalmente a permissão que declara, sem qualquer expansão de ações mais
fortes para mais fracas. Por essa razão, os papéis são configurados com todas as
ações de que necessitam declaradas explicitamente, e não por derivação.

### 6.4 Restrições por papel

Dois conjuntos de operações são protegidos por **papel** e não por permissão de
âmbito:

- A administração do escritório (gestão de utilizadores) exige o papel **ADMIN**.
- A administração da plataforma (criação e gestão de escritórios, definição da
  matriz de permissões) exige o papel **PLATAFORMA_ADMIN**.

O papel PLATAFORMA_ADMIN não detém, deliberadamente, **nenhuma permissão de
âmbito**.
Não consegue ler clientes, processos, documentos nem qualquer outro dado de
negócio de nenhum escritório. Opera apenas pelos seus pontos de administração
próprios.

---

## 7. Isolamento entre escritórios

Este é o limite de segurança mais importante do sistema.

### 7.1 Modelo de dados

- Cada entidade de negócio transporta uma coluna de identificação do escritório
  proprietário do registo (na base de dados, `tenant_id`).
- As restrições de unicidade são declaradas **por escritório**, e não
  globalmente. Por exemplo, o par (escritório, número de documento) é único —
  dois escritórios distintos podem ter clientes com o mesmo número de documento.

### 7.2 Modelo de execução

- O escritório do pedido é derivado da identidade autenticada, a partir do
  testemunho de sessão. **Nunca é aceite como parâmetro enviado pelo cliente.**
- Todas as leituras e escritas são filtradas por esse identificador.
- Operações sobre entidades filhas (por exemplo, uma decisão dentro de um
  processo) fazem dupla verificação de posse: confirma-se o escritório e
  confirma-se a entidade-pai.
- A pesquisa global aplica o mesmo filtro, por tipo de entidade, combinado com as
  permissões do utilizador.

### 7.3 Verificação

Foi executada uma auditoria de isolamento dedicada às superfícies de
administração de plataforma introduzidas na v2.16, antes da entrada de um
segundo escritório pagante. As superfícies de notificação foram objeto de
auditoria equivalente na v2.11.

### 7.4 Limitação assumida

O isolamento é **lógico**, não físico: todos os escritórios partilham a mesma
base de dados e o mesmo depósito de ficheiros, sendo separados por identificador
e por filtragem obrigatória em todos os acessos. Não existe base de dados
separada por escritório.

---

## 8. Persistência

### 8.1 Base de dados relacional (`postgres`)

- **PostgreSQL 16**.
- Guarda todos os dados de negócio: escritórios, utilizadores, papéis,
  permissões, clientes e respetivos contactos, notas, advogados e
  administrativos; processos, partes, fases, movimentações, decisões, factos e
  testemunhas; eventos e prazos; metadados de documentos; honorários,
  pagamentos e conta corrente; pareceres e suas versões; notificações e
  preferências; registo de auditoria; e definições de sistema.
- Utiliza extensões de pesquisa textual para tolerância a acentos e a
  correspondências parciais.
- Os dados residem num volume dedicado e persistente, independente do ciclo de
  vida dos contentores.
- **Evolução do esquema no arranque.** Em produção, o esquema é hoje **alterado
  automaticamente** pelo sistema no arranque, para o ajustar ao modelo de dados
  da versão instalada. Não é apenas verificado. Existe um modo de verificação —
  em que o arranque falha e nomeia o objeto divergente, em vez de mexer na base
  de dados — e existe caminho documentado para lá chegar, num arranque em dois
  tempos; mas esse modo é um **passo de instalação a executar**, e não o estado
  por omissão. Ver pontos 9.5 e 11.8, e a iniciativa de adoção de uma ferramenta
  de migrações no Roteiro Tecnológico, ponto 4.2, que é a via para o tornar
  definitivo.

### 8.2 Armazenamento de ficheiros (`minio`)

- Serviço de **armazenamento de objetos** compatível com o protocolo S3.
- Guarda os ficheiros carregados: documentos de cliente e de processo e anexos de
  versões de parecer.
- A chave de cada objeto é composta pelo identificador do escritório, pelo
  identificador do documento e pelo nome do ficheiro. O nome do ficheiro é
  saneado no carregamento, para impedir a manipulação do caminho de gravação.
- O descarregamento é feito por **ligação assinada e com validade limitada**,
  emitida pelo servidor no momento do pedido. Não existe acesso público direto
  aos ficheiros.
- Os objetos residem num volume dedicado e persistente.
- Nota de configuração: subsiste na definição de serviços um volume herdado de
  gravação em disco (`uploads`) da implementação anterior ao armazenamento de
  objetos. O código atual do servidor não escreve nesse volume — todo o
  carregamento e descarregamento passa pelo armazenamento de objetos.

---

## 9. Instalação e operação em contentores

### 9.1 Composição

A instalação é descrita por ficheiros de composição Docker:

- **Base** — define os seis serviços, a rede privada e os volumes persistentes.
- **Produção** — sobreposição que ativa o domínio real, o HTTPS automático e,
  opcionalmente, o uso de imagens pré-construídas em vez de compilação local.
- **Instalação alojada** — sobreposição específica para o alojamento em uso.

Os volumes persistentes são: dados da base de dados, dados do armazenamento de
objetos, certificados e configuração do `caddy`, e o volume herdado de
carregamentos.

As aplicações são construídas em imagens **multi-fase**, o que mantém a imagem
final reduzida, sem ferramentas de compilação.

### 9.2 Configuração

- Toda a configuração sensível entra por variáveis de ambiente: credenciais da
  base de dados, segredo de assinatura dos testemunhos, prazos de validade dos
  testemunhos, origens permitidas, domínio e credenciais do armazenamento de
  objetos. Nota: a duração dos cookies que transportam os testemunhos está
  atualmente fixada no código — 24 horas para o cookie de acesso e 30 dias para o
  de renovação — e **não acompanha** estas variáveis. Ver ponto 11.7.
- O servidor **não tem valores por omissão** para estes parâmetros: uma
  configuração incompleta impede o arranque, em vez de o deixar arrancar em
  estado inseguro.

### 9.3 Requisitos de instalação

- Servidor Ubuntu 22.04+ ou Debian 12+.
- Docker Engine 24+ e Docker Compose v2.
- Nome de domínio com registo A a apontar para o endereço público do servidor,
  com propagação de DNS concluída.
- Portas 80 e 443 abertas. A porta 80 é necessária para a emissão e renovação do
  certificado.

### 9.4 Integração e publicação de imagens

O fluxo automatizado tem duas etapas:

1. **Barreira de testes.** A cada entrega na linha principal e a cada pedido de
   integração, correm os testes do servidor — testes unitários e testes de
   integração executados contra uma base de dados PostgreSQL real, criada e
   destruída para o efeito — seguidos da análise estática de segurança. Se esta
   etapa falhar, o processo para.
2. **Construção e publicação.** Apenas nas entregas na linha principal, as três
   imagens (servidor, aplicação de gestão e página pública) são construídas e
   publicadas num registo privado de imagens. Cada imagem é etiquetada com a
   versão mais recente e com o identificador exato da revisão de código, o que
   permite reverter para uma versão anterior.

**A instalação no servidor é um passo operacional deliberado**, executado por
quem opera a plataforma: obter as imagens publicadas e reiniciar os serviços. O
fluxo automatizado publica as imagens; não as instala sozinho no servidor de
produção.

### 9.5 Evolução do esquema de dados

No arranque, o sistema compara o esquema da base de dados com o modelo de dados
da versão instalada. **Por omissão, ajusta o esquema automaticamente à diferença
que encontra — e é este o comportamento em vigor em produção.** O modo
alternativo, de verificação apenas, existe e é alcançável: o procedimento de
instalação versionado com o código (`DEPLOYMENT.md`) descreve um arranque em dois
tempos, cuja primeira fase cria o esquema e cuja segunda fase deixa a instalação
em verificação. É um passo de instalação, executado e confirmado por quem instala,
e não uma garantia dada pelo sistema. Ver ponto 11.8.

O ajuste automático não cobre tudo. Existem migrações SQL numeradas, guardadas
com o código, para alterações que não podem ser inferidas automaticamente — nomeadamente a ativação das extensões de
pesquisa textual e a criação de restrições de unicidade e de novas colunas de
escritório. **Estas migrações são aplicadas manualmente** durante a instalação ou
a atualização.

**O conjunto a aplicar não é o mesmo numa instalação de raiz e numa instalação
sobre uma base de dados já em uso** — e pelo menos uma delas é destrutiva se for
executada no caminho errado. A lista por caminho, com a classificação de cada
instrução e a prova em que assenta, consta da lista de verificação mantida no
repositório em `backend/migrations/README.md` (ver Roteiro Tecnológico, Anexo
A.5), e deve ser consultada antes de qualquer instalação ou atualização.

### 9.6 Certificados

O `caddy` obtém e renova os certificados automaticamente. Os certificados são
guardados num volume dedicado, que não deve ser apagado.

---

## 10. Qualidade e verificação

- **Análise estática de segurança** do código do servidor (SpotBugs com
  FindSecBugs), executada automaticamente na barreira de testes e sem alertas
  pendentes.
- **Testes automatizados** no servidor — cobrindo, entre outros, o bloqueio por
  tentativas de entrada, a suspensão de escritório, o limite de utilizadores, a
  contenção do papel de plataforma, a validação de NIF, o carregamento de
  documentos, os alertas diários e a pesquisa — incluindo testes de integração
  contra base de dados real.
- **Testes** na aplicação de gestão, executados localmente; não integram a
  barreira automática.
- **Verificação de estilo** na aplicação de gestão.
- **Revisão de código** e **verificação de aceitação com utilizador** por cada
  marco entregue.
- **Auditoria por marco**, com registo escrito das divergências encontradas e do
  respetivo encerramento.

A cobertura de testes não é uniforme por área: existem áreas cobertas apenas ao
nível do controlador, sem teste de integração contra base de dados. O Roteiro
Tecnológico, Anexo A.1, identifica um caso concreto.

---

## 11. Pontos de atenção

Registados aqui por transparência, para decisão do contratante:

1. **Isolamento lógico e não físico.** Ver ponto 7.4.
2. **Volume de carregamentos herdado.** Ver ponto 8.2. Não afeta o funcionamento,
   mas deve ser retirado da composição numa limpeza futura.
3. **Consola do armazenamento de objetos.** Na configuração de produção, a
   consola do `minio` está publicada num caminho do domínio, protegida por
   autenticação básica. As credenciais têm de ser definidas com o mesmo rigor das
   restantes.
4. **Atributo `Secure` dos cookies de sessão.** A definição dos cookies de sessão
   marca-os como `httpOnly`, mas não define o atributo `Secure`. Em produção, o
   tráfego é servido exclusivamente por HTTPS através do `caddy`, mas recomenda-se
   ativar explicitamente esse atributo antes da entrada em serviço.
5. **Migrações SQL manuais.** Ver ponto 9.5. A instalação e cada atualização
   exigem confirmar, migração a migração, quais se aplicam ao caminho em causa —
   instalação de raiz ou instalação sobre base de dados já em uso. A omissão da
   migração das extensões de pesquisa faz falhar a pesquisa global; e há pelo
   menos uma migração que, executada numa instalação de raiz, apaga em silêncio
   os logótipos já carregados. A lista por caminho consta da lista de
   verificação mantida no repositório em `backend/migrations/README.md` (ver
   Roteiro Tecnológico, Anexo A.5), e é aí que deve ser confirmada antes de
   cada instalação.
6. **Instalação não automática.** Ver ponto 9.4. A publicação das imagens é
   automática; a sua instalação no servidor é um passo manual.
7. **Duração dos cookies de sessão fixada no código.** A validade dos testemunhos
   entra por variável de ambiente, mas a duração dos cookies que os transportam
   está escrita no código — 24 horas para o de acesso, 30 dias para o de
   renovação. Encurtar a validade do testemunho de acesso por configuração não
   encurta o cookie. Recomenda-se passar também estas durações para configuração.
8. **A configuração própria de produção não é ativada pela instalação.** O código
   inclui um conjunto de definições destinado exclusivamente a produção, mas
   nenhum dos ficheiros de instalação, nem a imagem do sistema, nem o processo
   automático de entrega o seleciona. A produção corre, por isso, com as
   definições por omissão. Verificado em agosto de 2026. Três consequências, todas
   com o mesmo remédio:
   - **O esquema da base de dados é alterado automaticamente no arranque**, em vez
     de ser apenas verificado. Ver pontos 8.1 e 9.5. É a consequência mais
     relevante, por incidir sobre a integridade dos dados.
   - **O bloqueio por tentativas de entrada não distingue a origem do pedido.**
     Ver ponto 5.2.
   - **As respostas de erro do servidor são mais detalhadas** do que o previsto
     para produção.

   Existe já um paliativo documentado para a primeira — o arranque em dois tempos
   do ponto 9.5 —, que depende de ser executado corretamente em cada instalação.
   A correção de fundo é ativar as definições de produção na instalação, e é
   também o que a iniciativa de adoção de uma ferramenta de migrações do Roteiro
   Tecnológico, ponto 4.2, vem fixar de forma definitiva.

---

> **Nota:** este documento descreve a arquitetura de software e não constitui
> parecer jurídico nem certificação de conformidade com qualquer norma de
> proteção de dados. Qualquer utilização contratual carece de revisão por
> advogado.
