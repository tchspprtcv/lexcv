# ALCv — Roadmap Tecnológico

- **Documento:** Roadmap Tecnológico
- **Produto:** ALCv — Plataforma Institucional de Gestão Jurídica
- **Destinatário:** Entidade contratante (escritório de advogados ou instituição)
- **Última versão entregue:** v2.16, a 30 de julho de 2026
- **Data de referência:** agosto de 2026

> **Aviso essencial — nenhum prazo deste documento é uma data contratada.**
>
> A última versão entregue foi a **v2.16**, a 30 de julho de 2026. À data de
> referência deste documento **não existe nenhum ciclo de desenvolvimento seguinte
> formalmente aberto**. Daqui decorre o seguinte, que se aplica a **todas as linhas
> de todas as tabelas** da secção 4:
>
> - todos os prazos indicados são **estimativas de esforço**, expressas em **semanas
>   de trabalho contadas a partir do arranque do próximo ciclo** — nunca em datas de
>   calendário;
> - a **data de arranque do próximo ciclo ainda não está decidida**, pelo que nenhuma
>   estimativa deste documento pode ser convertida numa data;
> - a coluna **Confiança na estimativa** exprime o **julgamento da equipa técnica**
>   sobre o peso que cada estimativa merece; é uma apreciação, não uma medição;
> - qualquer compromisso de data exige acordo prévio por escrito.

---

## 1. Objetivo deste documento

Este documento apresenta, de forma aberta, **o que está previsto acontecer a seguir
no ALCv**: as correções planeadas, os trabalhos de fiabilidade e as evoluções de
produto em ponderação.

Não é uma lista de promessas. É o registo honesto do que a equipa sabe hoje sobre o
trabalho que falta fazer, com indicação clara de quanta confiança merece cada
estimativa apresentada.

Inclui deliberadamente as correções por fazer. Um roadmap que só mostra
funcionalidades novas esconde aquilo que mais interessa ao escritório que já usa o
sistema.

---

## 2. Como ler as tabelas

**Coluna «Estado»**

| Valor | Significado |
|---|---|
| Correção pronta, por integrar | A correção já foi escrita, mas ainda não está incorporada na versão distribuída; falta integrá-la e reconfirmá-la |
| Correção por fazer | Problema identificado e localizado; a correção ainda não foi escrita |
| Previsto | Trabalho analisado e considerado necessário; ainda não planeado ao detalhe |
| Depende de decisão | Não avança enquanto o escritório ou a direção do produto não decidir |
| Adiado por decisão | Analisado e conscientemente deixado para mais tarde |

**Coluna «Esforço estimado»**

Indica **semanas de trabalho**, contadas a partir do arranque do próximo ciclo de
desenvolvimento — cuja data ainda não está decidida. Cada célula está marcada como
«Estimativa» precisamente para que não seja lida como data de entrega. As linhas que
dependem de uma decisão por tomar não têm estimativa, e dizem-no.

**Coluna «Confiança na estimativa»**

Julgamento da equipa técnica sobre o peso que cada estimativa merece. Não é uma
garantia nem o resultado de uma medição.

| Valor | Significado |
|---|---|
| Alta | Âmbito delimitado e confirmado no próprio sistema; a estimativa depende sobretudo da disponibilidade da equipa |
| Média | Âmbito conhecido, mas o trabalho ainda não foi planeado ao detalhe; a estimativa pode variar de forma significativa |
| Baixa | Depende de decisão por tomar ou de análise ainda por fazer; o esforço só poderá ser estimado depois dessa decisão |

---

## 3. Ponto de partida — o que já está entregue

Para contexto, e porque condiciona o que vem a seguir, estas capacidades **estão
entregues e em funcionamento**:

- Gestão de **clientes**, **processos** (com **partes**, **fases** e **movimentações**),
  **agenda** e **prazos**, **documentos**, **honorários** e **pareceres**.
- **Isolamento de dados entre escritórios**, com auditoria dedicada concluída.
- **Planos e limite de utilizadores por escritório**, com bloqueio na criação de
  utilizadores acima do limite contratado e indicador «X/Y utilizadores».
- **Consola de plataforma**: criar, listar, suspender e reativar escritórios, e
  **relatório de utilização** com utilizadores ativos por escritório.
- **Pesquisa global** transversal a clientes, processos, documentos e pareceres.

Tudo o que aparece nas tabelas seguintes é trabalho **por fazer**.

---

## 4. Roadmap

> Todas as células da coluna **Esforço estimado** estão marcadas como «Estimativa»:
> são semanas de trabalho a partir de um arranque ainda por datar, e não datas de
> entrega.

### 4.1 Correções planeadas

Problemas já identificados no sistema, com impacto para o escritório.

| Iniciativa | O que muda para o escritório | Origem / evidência | Estado | Esforço estimado (semanas de trabalho) | Confiança na estimativa |
|---|---|---|---|---|---|
| Carregamento de documentos novos | O carregamento de um **documento** novo — na ficha do **processo**, na ficha do **cliente** ou no ecrã de documentos — volta a funcionar. É hoje a limitação mais grave em aberto | Registo interno de pendências, julho de 2026 (ver Anexo A.1) | Correção pronta, por integrar | Estimativa: menos de 1 semana, mais a reconfirmação em ambiente de teste | Média |
| Atribuição de **advogado** ao pedido de **parecer** | Quem não é administrador passa a poder escolher o advogado responsável ao criar um parecer, e deixa de ver a mensagem de erro que hoje surge sempre que abre o módulo | Registo interno de pendências, julho de 2026 (ver Anexo A.2) | Correção por fazer | Estimativa: 1 semana | Alta |
| Prioridade por omissão do **parecer** | Um parecer submetido sem alterar a prioridade passa a ficar como «Média», e não como «Alta». Elimina uma distorção silenciosa na lista de prioridades | Registo interno de pendências, julho de 2026 (ver Anexo A.3) | Correção pronta, por integrar | Estimativa: menos de 1 semana | Alta |
| Ligação direta na notificação de **documento** novo | Clicar na notificação de documento novo abre diretamente o separador de documentos do processo ou do cliente, em vez da ficha genérica. Reduz passos no uso diário | Registo interno de pendências, item classificado como melhoria menor (ver Anexo A.4) | Correção por fazer | Estimativa: menos de 1 semana | Alta |

### 4.2 Fiabilidade e operação

Trabalho que não acrescenta ecrãs, mas reduz o risco de cada atualização do sistema.

| Iniciativa | O que muda para o escritório | Origem / evidência | Estado | Esforço estimado (semanas de trabalho) | Confiança na estimativa |
|---|---|---|---|---|---|
| Automatizar as atualizações da base de dados | As atualizações do sistema deixam de depender da execução manual de instruções na base de dados. Reduz o risco de indisponibilidade e de erro humano em cada nova versão | Existem instruções de atualização da base de dados por executar manualmente, e não existe mecanismo automático que as aplique. O conjunto a aplicar **difere entre uma instalação de raiz** (base de dados nova) **e uma instalação sobre base de dados já em uso** — no caminho de raiz, a instrução `125` não se executa. A lista de verificação autoritativa e atualizada é mantida no repositório, em `backend/migrations/README.md` (ver Anexo A.5) | Previsto | Estimativa: 2 a 3 semanas | Média |
| Adotar uma ferramenta de migrações da base de dados (Flyway ou Liquibase) | A base de dados passa a ser atualizada por um mecanismo próprio, que regista em cada ambiente o que já foi aplicado e aplica o que falta, pela ordem certa. É esta a via para fixar a produção, de forma definitiva, no modo de verificação: em cada arranque a base de dados passa a ser **verificada** contra o modelo do sistema, em vez de ser **alterada automaticamente** por ele | Concretiza a iniciativa anterior. Existe no repositório uma configuração de produção que impõe o modo de verificação (`application-prod.yml`), mas **essa configuração nunca chega a ser ativada**: verificou-se em agosto de 2026 que nenhum dos três ficheiros de instalação, nem a imagem do sistema, nem o processo automático de entrega a seleciona. A produção corre sempre com a configuração por defeito, cujo modo é o de alteração automática do esquema. Não há, por isso, uma sobreposição pontual a remover: há uma configuração de produção que nunca foi ligada e que é hoje um ficheiro sem efeito. A indicação de modo de alteração automática presente no ficheiro de instalação em uso (`docker-compose.hostinger.yml`) é redundante — repete o valor que já vigorava. Foi entretanto formalizado um procedimento manual de arranque em dois tempos — criar o esquema e só depois passar ao modo de verificação —, documentado em `DEPLOYMENT.md`; é paliativo e depende de ser executado corretamente em cada instalação. O trabalho consiste em fixar um ponto de partida (*baseline*) do esquema atual e converter em migrações versionadas os ficheiros de migração hoje existentes em `backend/migrations/` (contagem em `backend/migrations/README.md`; ver Anexo A.5) | Previsto | Estimativa: 2 a 3 semanas | Média |
| Ambiente de validação prévia | Cada entrega passa a ser validada num ambiente igual ao real antes de chegar ao escritório, o que reduz o risco de surpresas depois da atualização | Padrão recorrente nas retrospetivas de **pelo menos seis dos treze ciclos** já realizados (v2.8, v2.9, v2.11, v2.13, v2.14 e v2.16): verificações que só encerram com o sistema a correr, e instabilidade das ferramentas de validação visual em dois ciclos consecutivos (ver Anexo A.6) | Previsto | Estimativa: 2 a 4 semanas | Média |
| Alargar a cobertura de testes automáticos | Reduz o risco de retrocessos: as funcionalidades já corrigidas ficam protegidas contra voltarem a falhar numa versão futura | A infraestrutura de testes automáticos já existe desde a versão v2.11; permanece registada pelo menos uma correção sem teste que a proteja (ver Anexo A.7) | Previsto | Estimativa: trabalho contínuo, com uma primeira fase de 2 semanas | Média |

### 4.3 Evolução do produto e do modelo comercial

Trabalho condicionado por decisões que ainda não foram tomadas.

| Iniciativa | O que muda para o escritório | Origem / evidência | Estado | Esforço estimado (semanas de trabalho) | Confiança na estimativa |
|---|---|---|---|---|---|
| Histórico mensal de utilização | Passa a ser possível ver o **pico** de utilizadores ativos em cada mês, e não apenas o número atual. Permite faturar por pico mensal, se for essa a opção | Proposta de distribuição multi-escritório e faturação, secção 5.3 — o relatório entregue mostra apenas o valor instantâneo (ver Anexo A.8) | Previsto | Estimativa: 1 a 2 semanas | Média |
| Emissão automática de faturas | A fatura deixa de ser emitida manualmente a partir do relatório de utilização | Proposta de distribuição multi-escritório e faturação, secção 5.3 — a integração de cobrança automática foi deliberadamente deixada fora do âmbito (ver Anexo A.9) | Depende de decisão | Sem estimativa enquanto a decisão não for tomada | Baixa |
| Registo autónomo de novos escritórios | Um escritório passaria a poder registar-se sozinho, sem intervenção comercial. A recomendação atual é **manter** o registo assistido | Proposta de distribuição multi-escritório e faturação, secção 8, decisão 1 — em aberto (ver Anexo A.10) | Depende de decisão | Sem estimativa enquanto a decisão não for tomada | Baixa |
| **Papéis** configuráveis por escritório | Cada escritório poderia definir as suas próprias regras de permissões. Hoje as regras são definidas centralmente pela plataforma, por razões de segurança entre escritórios | Proposta de distribuição multi-escritório e faturação, secção 4.2, ponto 4 — evolução maior, não solicitada até à data (ver Anexo A.11) | Depende de decisão | Sem estimativa enquanto a decisão não for tomada | Baixa |
| Endereço próprio por escritório | Cada escritório teria um subdomínio ou domínio próprio de acesso. Não é necessário para o funcionamento nem para o isolamento de dados | Proposta de distribuição multi-escritório e faturação, secção 8, decisão 3 — adiamento recomendado e aceite (ver Anexo A.12) | Adiado por decisão | Sem estimativa; só será estimado se e quando o tema for retomado | Baixa |

---

## 5. Decisões em aberto que condicionam o roadmap

Estes pontos **não são trabalho técnico**: são decisões que travam ou destravam
linhas inteiras das tabelas acima.

| # | Decisão | Quem decide | O que fica bloqueado |
|---|---|---|---|
| 1 | Um **evento** de prioridade alta **sem data de início** deve contar como urgente nos indicadores do painel? Hoje não conta, por opção deliberada (ver Anexo A.13) | Direção do escritório | O comportamento dos indicadores «prazos a vencer» e «prazos críticos» do painel |
| 2 | Cobrança manual a partir do relatório de utilização, ou integração de cobrança automática? | Direção comercial | «Emissão automática de faturas» (4.3) |
| 3 | Registo de novos escritórios assistido ou autónomo? | Direção comercial | «Registo autónomo de novos escritórios» (4.3) |
| 4 | Data de arranque do próximo ciclo de desenvolvimento | Direção do projeto | **Todas** as estimativas deste documento |

A decisão n.º 1 está registada como pendente desde o fecho da versão **v2.10**, a 10 de
julho de 2026, e continua sem resposta: foi reconfirmada em aberto no fecho da v2.11 e
mantém-se assim à data deste documento. É de resposta rápida e destrava um comportamento
visível todos os dias no painel.

---

## 6. Nota final sobre a natureza deste documento

Reafirma-se, para evitar qualquer leitura incorreta:

1. **À data de referência deste documento não existe um plano de desenvolvimento
   futuro formalmente aprovado para o ALCv.** O ciclo anterior fechou a 30 de julho
   de 2026, integralmente concluído, com a entrega da versão v2.16.
2. Todas as iniciativas listadas foram reunidas a partir de registos internos de
   pendências, de retrospetivas de ciclos anteriores e de uma proposta estratégica já
   redigida — **não a partir de um roadmap aprovado**, porque não existe nenhum.
3. **Todos os prazos são estimativas de esforço**, em semanas de trabalho a contar do
   arranque de um ciclo cuja data ainda não está fixada. Nenhum deles constitui
   compromisso de entrega.
4. Nenhuma das iniciativas da secção 4 está disponível hoje no produto. As
   capacidades existentes estão listadas, e apenas essas, na secção 3.

---

## Anexo A — Rastreabilidade

Referências internas de cada linha das tabelas, para verificação pela equipa técnica.

| Ref. | Origem verificada |
|---|---|
| A.1 | `.planning/STATE.md`, secção «Blockers/Concerns», entrada v2.13 / Phase 107. Confirmado em agosto de 2026 que a correção não consta da linha principal: `backend/src/main/java/com/lexcv/models/Documento.java` não segue o padrão já adotado por `ParecerVersao.java`, e não existe teste de integração correspondente |
| A.2 | `.planning/STATE.md`, secção «Blockers/Concerns», entrada v2.13 / Phase 108. Confirmado em agosto de 2026: `useAdminUsers()` continua a ser invocado sem guarda em três ficheiros de `web/src/app/(dashboard)/pareceres/`. Alternativa já existente no produto: `useTenantUsers()` |
| A.3 | `.planning/STATE.md`, secção «Pending Todos», entrada v2.13 / Phase 108. Confirmado em agosto de 2026: o campo de prioridade em `web/src/app/(dashboard)/pareceres/nova/page.tsx` não declara valor inicial no elemento de seleção, embora o formulário declare `MEDIA` |
| A.4 | `.planning/STATE.md`, «Deferred Items», categoria «cosmetic». Confirmado em agosto de 2026 em `ResourceController.java`: a notificação de documento novo usa `/processos/{id}` e `/clientes/{id}` sem o sufixo `?tab=documentos` |
| A.5 | `backend/migrations/` — instruções de atualização da base de dados que continuam a ser executadas manualmente, uma a uma. **Ausência de executor automático confirmada em agosto de 2026:** o projeto não usa Flyway nem Liquibase, e a evolução do esquema assenta apenas no comportamento automático do sistema no arranque. A configuração de produção que imporia o modo de verificação (`application-prod.yml`) **nunca é ativada por nenhum ficheiro de instalação**, pelo que o sistema corre sempre com a configuração por defeito, que altera o esquema automaticamente. Não se trata de uma sobreposição pontual, mas de uma configuração que nunca foi ligada. É esta ausência que constitui a dívida técnica e que justifica as duas iniciativas de §4.2: não existe registo de que instruções já correram em cada ambiente, pelo que cada atualização depende de verificação manual. **O conjunto a aplicar depende do caminho de instalação:** uma instalação de raiz executa um subconjunto muito menor do que uma instalação sobre base de dados já em uso, e a instrução `125` **não se executa** no caminho de raiz. **A lista de verificação autoritativa — com o detalhe por ficheiro, a ordem de execução e o estado de cada instrução — é mantida em `backend/migrations/README.md`, ao lado dos próprios ficheiros e versionada com o código. É essa a fonte a consultar antes de qualquer instalação ou atualização. Este documento remete para ela e não a duplica, precisamente para que não divirjam** |
| A.6 | `.planning/RETROSPECTIVE.md` — o ficheiro regista treze ciclos (v2.3, v2.4, v2.6, v2.7, v2.8, v2.9, v2.10, v2.11, v2.12, v2.13, v2.14, v2.15, v2.16). Padrão de verificações encerradas como `human_needed` por falta de ambiente a correr identificado em **pelo menos seis** deles: v2.8 (linha 209), v2.9 (linha 252), v2.11 (linhas 329 e 351), v2.13 (referida na linha 466), v2.14 (linha 466) e v2.16 (linhas 542 e 548). Seis é um mínimo apurado, não um total: a linha 209 descreve o padrão como repetido «em todos os ciclos até à data», mas os restantes ciclos não o registam em entrada própria e não são aqui contados. Instabilidade das ferramentas de validação visual em **dois ciclos consecutivos** — v2.13 e v2.14 (linha 466: «consistent with the same class of issue diagnosed in v2.13») |
| A.7 | `.planning/STATE.md`, «Deferred Items», linha 181 — Phase 87, terceiro dos três desvios ali registados: correção de perda de dados em atualização parcial de **parecer**, já corrigida no código (commit `ce6d1f0`), que permanece sem cobertura de teste automático e está assinalada como «remains OPEN as a standalone item» (os outros dois desvios da mesma entrada foram encerrados na v2.11). Infraestrutura de testes de integração (Testcontainers) existente desde v2.11 / Phase 91 — `.planning/STATE.md`, linha 149 |
| A.8 | `business/propostas/proposta-multitenancy-faturacao.md`, secção 5.3, que prevê o pico mensal de utilizadores ativos como opção «útil se decidires faturar pelo pico mensal em vez do valor instantâneo». Entregue em v2.16: `/plataforma/relatorio` com nome, plano, limite e utilizadores ativos — sem histórico nem pico mensal. Confirmado em agosto de 2026 em `backend/src/main/java/com/lexcv/dtos/TenantAdminSummaryResponse.java` e `PlatformAdminController.java`: o relatório expõe `utilizadoresAtivos` como contagem instantânea, sem qualquer campo de histórico ou de pico |
| A.9 | `business/propostas/proposta-multitenancy-faturacao.md`, secção 5.3, que exclui expressamente qualquer integração de cobrança do âmbito («sem integrar nenhum gateway de pagamento para já»), e secção 8, decisão 2 («relatório de uso + fatura manual, recomendado para já, vs. gateway de pagamento automático desde já»). A proposta recomenda ainda confirmar previamente a cobertura desses serviços em Cabo Verde |
| A.10 | `business/propostas/proposta-multitenancy-faturacao.md`, secção 8, decisão 1 (registo assistido vs. registo público autónomo), com recomendação expressa de manter o assistido. Confirmado por construir: `.planning/PROJECT.md`, lista de fora de âmbito, linha 121 |
| A.11 | `business/propostas/proposta-multitenancy-faturacao.md`, secção 4.2, ponto 4 — papéis verdadeiramente por escritório exigiriam associar cada papel a um escritório, mudança que a proposta classifica como maior e que «ninguém pediu ainda». Encerrado em v2.16 / Phase 121 com a gestão de papéis reservada à plataforma (`.planning/STATE.md`, linha 170) |
| A.12 | `business/propostas/proposta-multitenancy-faturacao.md`, secção 8, decisão 3, com recomendação de adiar o subdomínio por não ser necessário nem ao início de sessão nem ao isolamento de dados. Adiamento registado como aceite em `.planning/PROJECT.md`, lista de fora de âmbito, linha 121 |
| A.13 | Decisão em aberto n.º 1: `.planning/STATE.md`, «Pending Todos», linha 141, e «Deferred Items», linha 178 (tabela dos itens diferidos no fecho da v2.10). Origem: Phase 85, que pertence à v2.10 (`.planning/milestones/v2.10-ROADMAP.md`, linha 170); primeiro registo como decisão por tomar em `.planning/milestones/v2.10-MILESTONE-AUDIT.md`, linha 67. `.planning/milestones/v2.11-MILESTONE-AUDIT.md`, linha 68, descreve-a como arrastada «across 3 milestones now» |

---

**Verificado e encerrado, por isso ausente das tabelas:** o desvio de uma hora nas
datas de **eventos** da **agenda** em fusos horários negativos, registado como aberto
no registo interno de pendências (v2.13 / Phase 106), **já não se verifica na linha
principal** — confirmado em agosto de 2026 em `web/src/hooks/use-eventos.ts`,
`web/src/app/(dashboard)/agenda/page.tsx`, `agenda/novo/page.tsx` e
`agenda/[id]/editar/page.tsx`.
