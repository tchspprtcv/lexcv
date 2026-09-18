# ALCv — Termo de Abertura do Projeto

- **Documento:** Termo de Abertura do Projeto (Project Charter)
- **Produto:** ALCv — Plataforma Institucional de Gestão Jurídica
- **Destinatário:** Entidade contratante (escritório de advogados ou instituição)
- **Versão do produto descrita:** v2.16, encerrada a 30 de julho de 2026
- **Data de emissão:** agosto de 2026

---

## 1. Enquadramento

O ALCv é uma plataforma institucional de gestão jurídica para Cabo Verde,
desenhada para a realidade institucional cabo-verdiana e para a terminologia
corrente do SIJ (Sistema Judicial de Cabo Verde). **Não existe integração técnica
com o SIJ — ver ponto 5.2.** Centraliza clientes, processos, agenda e prazos,
documentos e a gestão financeira básica de honorários.

A plataforma serve vários escritórios em simultâneo: cada escritório opera sobre
os seus próprios dados, num ambiente isolado dos restantes escritórios servidos
pela mesma instalação.

Toda a linguagem do produto — ecrãs, campos, mensagens — está em português, com
a terminologia corrente da prática jurídica: **cliente**, **processo**,
**parte**, **fase**, **movimentação**, **evento**, **prazo**, **honorário**,
**parecer**, **documento**.

---

## 2. Objetivo do projeto

Dotar a entidade contratante de um único painel onde o ciclo completo de um
processo é gerido de ponta a ponta, com isolamento rigoroso por escritório:

> **cliente → processo → prazos → documentos → financeiro**

---

## 3. Valor comercial

- **Fim da dispersão documental.** A ficha do cliente, a ficha do processo, os
  documentos e o termo de honorários deixam de viver em pastas de rede,
  folhas de cálculo e caixas de correio separadas.
- **Prazos sob controlo.** O critério de "prazo crítico" é único e partilhado
  por toda a aplicação — o mesmo prazo é classificado da mesma forma na agenda,
  no painel e nas notificações.
- **Rastreabilidade.** Transições de estado de processo, decisões de verificação
  de conflitos, descarregamento e eliminação de documentos ficam registados em
  histórico de auditoria.
- **Documentos formais gerados pelo sistema.** A Ficha de Cliente e o Termo de
  Honorários são produzidos a partir dos dados já introduzidos, em formato
  próprio para impressão.
- **Governação de acessos.** Cada utilizador vê apenas o que o seu papel
  permite, com a mesma regra aplicada no interface e no servidor.
- **Isolamento entre escritórios.** Cada registo pertence a um escritório e as
  leituras e escritas são delimitadas por essa fronteira.

---

## 4. Metas principais

| # | Meta | Situação |
|---|---|---|
| 1 | Gestão completa de clientes, com ficha institucional imprimível | Entregue |
| 2 | Gestão de processos com partes, fases, movimentações, decisões, factos e testemunhas | Entregue |
| 3 | Agenda unificada de eventos e prazos, com destaque de prazos críticos | Entregue |
| 4 | Repositório de documentos com carregamento, descarregamento e remoção | Entregue |
| 5 | Honorários e pagamentos com reflexo na conta corrente do cliente | Entregue |
| 6 | Módulo de Parecer Jurídico com versionamento imutável e entrega irreversível | Entregue |
| 7 | Sistema de notificações em aplicação, com alertas diários de prazos | Entregue |
| 8 | Pesquisa global sobre clientes, processos, documentos e pareceres | Entregue |
| 9 | Controlo de acessos por papel, espelhado no interface e no servidor | Entregue |
| 10 | Operação com vários escritórios na mesma instalação, com consola de administração da plataforma | Entregue |
| 11 | Instalação em contentores, com HTTPS automático e **publicação contínua de imagens** (a instalação no servidor é um passo manual — ver Especificação de Arquitetura, ponto 9.4) | Entregue |

O produto foi desenvolvido por marcos sucessivos. O primeiro conjunto funcional
foi encerrado em 26 de maio de 2026 (v1.0) e o marco mais recente descrito neste
documento foi encerrado em 30 de julho de 2026 (v2.16).

---

## 5. Âmbito

### 5.1 Incluído

- **Clientes** — registo de particulares e empresas, numeração sequencial por
  escritório, NIF obrigatório, contactos, notas, advogados e administrativos
  atribuídos, procuração, documentos entregues e a tratar, deslocações,
  honorários propostos, conta corrente e ficha imprimível. Inclui fusão de
  clientes duplicados.
- **Processos** — dados do processo, juízo, origem, partes, fases,
  movimentações, decisões, factos, testemunhas, documentos e auditoria.
  Formalização do processo (triagem para ativo) com criação automática do
  honorário associado e termo de honorários imprimível.
- **Agenda** — eventos e prazos, com filtros por processo, categoria e estado, e
  marcação de conclusão.
- **Documentos** — carregamento, listagem, descarregamento e remoção, com
  classificação por tipo e nível de confidencialidade, ligados ao cliente ou ao
  processo.
- **Financeiro** — honorários e pagamentos, com impacto na conta corrente do
  cliente e exportação de listagens.
- **Pareceres** — solicitação, versionamento imutável, aprovação, entrega
  irreversível, pesquisa avançada e histórico.
- **Notificações** — alertas em aplicação sobre entrada em nova fase, novo
  documento, processo atribuído, parecer atribuído, prazos e eventos próximos ou
  vencidos e honorários em atraso, com preferências de silenciamento por
  utilizador.
- **Pesquisa global** — pesquisa transversal a clientes, processos, documentos e
  pareceres, acessível por atalho de teclado.
- **Definições** — perfil, segurança, gestão de utilizadores, consulta da matriz
  de controlo de acesso e preferências de notificação.
- **Página pública** — sítio institucional de apresentação, servido na raiz do
  domínio.
- **Instalação e operação** — instalação em contentores num servidor próprio ou
  alugado, com certificado HTTPS renovado automaticamente.

### 5.2 Fora de âmbito

Os pontos seguintes **não** fazem parte do produto e não estão planeados:

- **Integração técnica com o SIJ.** O ALCv descreve-se como alinhado ao
  ecossistema do SIJ, mas não existe nem está planeada qualquer troca de dados,
  interface ou autenticação com o sistema judicial.
- Contabilidade completa ou ERP.
- Aplicação móvel nativa. A abordagem é Web responsiva.
- Notificações por correio eletrónico ou push. As notificações são apenas dentro
  da aplicação.
- Integração com Keycloak ou outro fornecedor de identidade institucional.
- Cálculo automático de honorários e cômputo automático de prazos processuais.

---

## 6. Quem beneficia

| Beneficiário | Papel no sistema | Ganho principal |
|---|---|---|
| Sócio ou responsável do escritório | ADMIN | Visão consolidada da carteira, do estado dos processos e da situação financeira |
| Advogado | ADVOGADO | Ficha de processo completa, prazos destacados, pareceres versionados |
| Técnico jurídico | TECNICO | Agenda e prazos sob gestão direta, consulta de processos e financeiro |
| Assistente administrativo | ASSISTENTE | Registo e atualização de clientes, consulta de processos, agenda e documentos |
| Operador da plataforma | PLATAFORMA_ADMIN | Criação de novos escritórios, limites de utilizadores e relatório de utilização para suporte à faturação |
| Cliente do escritório | — (não é utilizador da plataforma) | Documentos formais coerentes (ficha de cliente, termo de honorários) e resposta mais rápida sobre o estado do processo |

A coluna "Papel no sistema" indica a correspondência habitual entre a função
exercida no escritório e o papel atribuído na plataforma. É uma leitura de
conveniência: o que determina o acesso de cada utilizador é o conjunto de
permissões do papel que lhe for atribuído, e não o cargo que ocupa.

---

## 7. Pressupostos e restrições

- **Ligação à Internet.** A plataforma é servida por Web; os utilizadores
  necessitam de ligação para aceder.
- **Servidor dedicado.** A instalação requer um servidor com capacidade para
  executar contentores (Docker) e um nome de domínio apontado para esse
  servidor.
- **Limite de utilizadores por plano.** Cada escritório tem um plano e um limite
  de utilizadores ativos; ao atingir o limite, a criação de novos utilizadores é
  recusada até o plano ser ajustado.
- **A matriz de permissões é gerida pela plataforma.** O administrador do
  escritório consulta a matriz de controlo de acesso, mas não a altera.
- **Faturação manual.** O relatório de utilização por escritório suporta a
  faturação, que é executada fora do sistema.

---

## 8. Critérios de aceitação do projeto

1. Um utilizador com o papel adequado consegue percorrer o ciclo completo:
   registar cliente, abrir processo, formalizá-lo, marcar prazos, carregar
   documentos, registar honorários e pagamentos.
2. Um utilizador sem a permissão correspondente não acede ao módulo, quer pelo
   menu quer por endereço direto.
3. Os dados de um escritório não são visíveis a partir de outro escritório.
4. A Ficha de Cliente e o Termo de Honorários imprimem com os dados corretos.
5. Os alertas de prazo chegam ao destinatário certo e respeitam as preferências
   de silenciamento.

---

## 9. Governação do documento

Este termo de abertura descreve o produto tal como existe à data de emissão,
na versão v2.16.
Alterações de âmbito, de plano ou de limite de utilizadores são acordadas por
escrito entre as partes.

> **Nota:** este documento tem natureza técnica e comercial. Não constitui
> parecer jurídico. Qualquer utilização contratual carece de revisão por
> advogado.
