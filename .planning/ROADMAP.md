# Roadmap: LexCV — Marco v3.0 Faturação Eletrónica (eFatura CV)

## Overview

Este marco dá ao LexCV faturação eletrónica no formato eFatura de Cabo Verde, com dois emitentes: cada escritório fatura os seus clientes (uma Fatura-Recibo por pagamento de honorários) e a plataforma LexCV fatura os escritórios pela subscrição. A ligação real à DNRE fica fora do v3.0: o formato é o exato, a comunicação passa por um adaptador com implementação simulada, e tudo o que é simulado é marcado inequivocamente como "sem validade fiscal".

A sequência segue a cadeia de dependências do próprio dinheiro. Primeiro a fundação fiscal (dados do emitente, ativação por escritório, parâmetros com vigência, numeração sem lacunas), porque nada pode ser emitido sem ela. Depois a emissão atómica da Fatura-Recibo no registo do pagamento, que é o núcleo e a fase de maior risco. A seguir, em paralelo possível, a Nota de Crédito (única forma de correção) e o formato eFatura com o adaptador simulado. Só depois o PDF, o armazenamento e o email, que dependem de um documento já emitido e comunicado. A plataforma como segundo emitente reutiliza o motor já provado e introduz a primeira superfície de leitura entre tenants. A última fase fecha com a auditoria de isolamento, segurança, migrações e UAT.

## Phases

**Phase Numbering:**

- Continua a numeração do marco anterior: o v2.18 terminou na Phase 132. Este marco começa na **Phase 133**.
- Fases inteiras (133, 134, ...): trabalho planeado deste marco.
- Fases decimais (133.1, ...): inserções urgentes pós-planeamento, se necessário.

- [x] **Phase 133: Fundação Fiscal** - Escritório regista os dados fiscais, ativa a faturação e dispõe de parâmetros com vigência e numeração sequencial sem lacunas (completed 2026-10-04)
- [ ] **Phase 134: Fatura-Recibo Atómica nos Honorários** - Registar um pagamento emite, na mesma operação, uma Fatura-Recibo imutável, com pré-visualização, retenção e listagem
- [ ] **Phase 135: Nota de Crédito** - Única forma de corrigir uma Fatura-Recibo, com reversão coerente de saldo, honorário, KPI e alerta
- [ ] **Phase 136: Formato eFatura e Adaptador Simulado** - Cada documento gera XML DFE validado, com IUD, e é comunicado em segundo plano a um adaptador simulado
- [ ] **Phase 137: PDF, Armazenamento, Email e Relatório** - Documentos têm PDF e XML descarregáveis, são enviados por email ao cliente e exportados em CSV mensal
- [ ] **Phase 138: Plataforma como Emitente** - A LexCV fatura as subscrições dos escritórios e cada escritório consulta as suas faturas em modo só de leitura
- [ ] **Phase 139: Fecho — Isolamento, Segurança, Migrações e UAT** - Auditoria final de isolamento por tenant, credenciais, SAST, migrações em ambos os modos e UAT

## Phase Details

### Phase 133: Fundação Fiscal

**Goal**: Cada escritório pode registar os seus dados fiscais e ativar a faturação de forma segura, e o sistema dispõe de parâmetros fiscais com vigência e de uma numeração sequencial sem lacunas, sem que nada mude para os escritórios que não ativarem.
**Depends on**: Nada (primeira fase do marco; continua da Phase 132 do v2.18)
**Requirements**: CFG-01, CFG-02, CFG-03, CFG-04, CFG-05, CFG-06
**Success Criteria** (what must be TRUE):

  1. Administrador com `financeiro:manage` regista e edita em Definições → Faturação o NIF (rejeitado se não tiver 9 dígitos ou o primeiro não estiver entre 1 e 9), firma, morada e regime de IVA (normal, ou isento com motivo)
  2. A faturação só pode ser ativada com os dados fiscais completos; depois do primeiro documento emitido não pode ser desligada e o NIF do emitente deixa de ser editável
  3. Com a faturação desligada, registar um pagamento de honorários comporta-se exatamente como antes, sem documento fiscal
  4. A taxa de IVA (15%) e a retenção sugerida (20%) existem como parâmetros com data de vigência na base de dados, não como constantes no código
  5. Dois pedidos concorrentes para a mesma série (emitente, tipo, ano civil) recebem números consecutivos sem lacunas nem duplicados, e a numeração reinicia a 1 em cada ano civil
  6. Administrador liga ou desliga o envio automático de faturas por email (desligado por omissão) e só consegue ligá-lo depois de aceitar explicitamente o aviso de que os documentos simulados não têm validade fiscal

**Plans**: 8 plans (4 waves)

Plans:

- [x] 133-01-PLAN.md — Wave 1: fiscal enums (21 motivos de isenção, AmbienteFiscal.SIMULADO), RecusaFiscalException + handler, Clock, entidades/repositórios t_configuracao_fiscal/t_parametro_fiscal/t_serie_fiscal, script 133 + README + IT de paridade
- [x] 133-02-PLAN.md — Wave 2: ParametroFiscalService (vigência), seed IVA 15 / retenção 20, NIF demo válido, rótulo financeiro:manage, gate sem constantes (CFG-04)
- [x] 133-03-PLAN.md — Wave 2: NumeracaoService (FOR UPDATE, ON CONFLICT, lock_timeout, MANDATORY, ano em Atlantic/Cape_Verde) + IT de concorrência Testcontainers (CFG-05)
- [x] 133-04-PLAN.md — Wave 2: AuditoriaFiscalService + ConfiguracaoFiscalService + DTOs (dados, ativar/desativar, bloqueio do NIF, email com aceitação)
- [x] 133-05-PLAN.md — Wave 3: FaturacaoController /api/v1/faturacao (financeiro:manage na classe) + guarda CFG-03 + SpotBugs
- [x] 133-06-PLAN.md — Wave 1: apiFetch ApiError aditivo (status/code/campo, opt-out de toast), tipos, schema Zod, hooks use-faturacao
- [x] 133-07-PLAN.md — Wave 2: separador Faturação em Definições, formulário de dados fiscais, séries só de leitura
- [x] 133-08-PLAN.md — Wave 4: cartões Ativação e Email, verify:faturacao, verificação humana ponta a ponta

**UI hint**: yes

Notas da fase:

- Cria tabelas: entrega o script idempotente em `backend/migrations/` e a linha em `backend/migrations/README.md` nesta mesma fase (obrigação por fase; OPER-02 é só verificado no fim).
- RBAC reutiliza `financeiro:*`; sem scope novo. Back-end e UI têm de concordar (`@PreAuthorize` e `hasScopedPermission`).
- A numeração é o contrato mais difícil de corrigir depois: provar a concorrência com teste de integração em PostgreSQL real (Testcontainers), não com mocks.

### Phase 134: Fatura-Recibo Atómica nos Honorários

**Goal**: Num escritório com faturação ativa, registar um pagamento de honorários emite na mesma operação uma Fatura-Recibo fiscalmente coerente e imutável, e o utilizador vê, confere e consulta esses documentos.
**Depends on**: Phase 133
**Requirements**: EMIS-01, EMIS-02, EMIS-03, EMIS-04, EMIS-05, EMIS-06, EMIS-07, EMIS-08, EMIS-09, EMIS-10, EMIS-11, EMIS-12
**Success Criteria** (what must be TRUE):

  1. Registar um pagamento com faturação ativa grava, na mesma transação, o pagamento, a Fatura-Recibo e a atualização da conta corrente; se uma das partes falhar, nenhuma fica gravada, e submeter o mesmo pedido duas vezes (duplo clique, repetição) produz um único pagamento e um único documento
  2. Antes de confirmar, o utilizador vê a pré-visualização (adquirente, base, IVA, retenção, total) e só emite depois de confirmar; o valor pago (IVA incluído) é decomposto em base e IVA segundo o regime do escritório, ou mostra o motivo de isenção num escritório isento
  3. O utilizador pode aplicar retenção na fonte (taxa sugerida editável, sobre a base sem IVA); o documento mostra o valor retido e o líquido recebido e a conta corrente é creditada do total; o método de pagamento aparece mapeado para o meio de pagamento eFatura
  4. A emissão é recusada com mensagem clara quando a data não é a de hoje (hora de Cabo Verde; sem data assume hoje) ou quando o cliente não tem NIF cabo-verdiano válido, nome ou morada (até 100 caracteres), indicando o que falta corrigir
  5. A Fatura-Recibo guarda emitente e adquirente tal como no momento da emissão e nenhum ecrã ou endpoint a edita ou apaga; um pagamento faturado não pode ser apagado, apagar o cliente, o processo ou o honorário a que pertence é recusado e fundir clientes preserva a ligação dos documentos
  6. Utilizador com `financeiro:view` lista os documentos fiscais do seu escritório (filtros por cliente, período, tipo, estado) e abre o detalhe, sem nunca ver os de outro escritório; pagamentos anteriores à ativação aparecem como "sem documento fiscal" e não são faturados retroativamente

**Plans**: 14 plans (8 waves)

Plans:

- [x] 134-01-PLAN.md — Wave 1: entidades @Immutable DocumentoFiscal/DocumentoFiscalLinha + ComunicacaoFiscal (PENDENTE), script 134 + README linha 20 (+ índice único guardado em t_conta_corrente.cliente_id), MigracaoFiscal134IT
- [ ] 134-02-PLAN.md — Wave 1 (TDD): CalculoFiscal (vetores HALF_UP + propriedade), ValidacaoEmissao (422 com campo), MetodoPagamento → meio eFatura, textos controlados
- [ ] 134-03-PLAN.md — Wave 2: repositórios estreitos (sem delete/update), repontarCliente nativo, locks cliente/processo/CC, ativaPorTenant; DocumentoFiscalImutabilidadeTest + DocumentoFiscalRepositoryIT
- [ ] 134-04-PLAN.md — Wave 3: PagamentoRequest, ComposicaoFaturaRecibo (pura, partilhada), PreVisualizacaoFaturaService readOnly + estado de emissão
- [ ] 134-05-PLAN.md — Wave 3: DocumentoFiscalService (listar, detalhe, referências por pagamento, existePara*, repontarCliente MANDATORY) + DTOs de leitura
- [ ] 134-06-PLAN.md — Wave 4: PagamentoFaturadoService @Transactional (ordem de locks configuração → cliente → processo → CC → série, idempotência sob o lock), evento de auditoria
- [ ] 134-07-PLAN.md — Wave 5: ITs Testcontainers — atomicidade/rollback, paridade da pré-visualização, snapshot, dois emitentes, concorrência e corrida da mesma chave
- [ ] 134-08-PLAN.md — Wave 5: delegação em createPagamento (ramo desligado verbatim + hash), guarda CFG-03 evoluída, lista de pagamentos com documento fiscal
- [ ] 134-09-PLAN.md — Wave 5: DocumentoFiscalController (estado-emissao, pre-visualizacao financeiro:edit, lista/detalhe financeiro:view) + teste de autorização real
- [ ] 134-10-PLAN.md — Wave 6: guardas 409 (pagamento faturado, cliente, processo, honorário), fusão com repontamento, IT de corrida/sem deadlock/sem órfãos
- [ ] 134-11-PLAN.md — Wave 6: web — tipos, schema com método/retenção, chave de idempotência com fallback, interpretação de erros, hooks
- [ ] 134-12-PLAN.md — Wave 7: web — formulário com faturação ativa + diálogo de pré-visualização, coluna "Documento fiscal", 409 inline
- [ ] 134-13-PLAN.md — Wave 7: web — página Documentos fiscais (filtros, paginação servidor) e detalhe só de leitura
- [ ] 134-14-PLAN.md — Wave 8: gate verify:documentos-fiscais, suite completa com ITs, VALIDATION assinada, verificação ponta a ponta (checkpoint)

**UI hint**: yes

Notas da fase:

- **Gate antes de planear:** validação por contabilista das regras fiscais (decomposição de IVA com valor incluído, arredondamentos, isenção, retenção sobre a base) antes de `/gsd:plan-phase 134`. As regras ficam como dados parametrizáveis.
- Fase mais extensa do marco (12 requisitos). Não foi dividida porque todos os requisitos são facetas do mesmo fluxo atómico (emitir, proteger, consultar), e partir a transação de emissão, a imutabilidade ou as guardas de eliminação em fases distintas deixaria janelas com documentos editáveis ou apagáveis. O planeamento deve decompô-la em várias ondas de planos.
- Cria tabelas: script idempotente em `backend/migrations/` e linha no `README.md` na mesma fase. Toda a leitura e escrita de documentos filtra por `tenant_id`.
- Os valores de pagamento e honorário passam a ser lidos como IVA incluído; o estado do documento de comunicação nasce "pendente" (a comunicação em si chega na Phase 136).

### Phase 135: Nota de Crédito

**Goal**: Utilizador autorizado corrige uma Fatura-Recibo emitida, total ou parcialmente, através de uma Nota de Crédito que reverte o valor de forma coerente em todo o sistema.
**Depends on**: Phase 134
**Requirements**: NCRD-01, NCRD-02, NCRD-03
**Success Criteria** (what must be TRUE):

  1. Utilizador com `financeiro:manage` emite uma Nota de Crédito total ou parcial sobre uma Fatura-Recibo, com motivo obrigatório e referência à fatura original, numerada na série própria das Notas de Crédito
  2. A soma das Notas de Crédito de uma fatura nunca excede o valor original, e o sistema recusa creditar uma Nota de Crédito ou um documento de outro emitente
  3. Após a Nota de Crédito, o saldo da conta corrente, o total pago do honorário, o KPI mensal do dashboard e o alerta de honorário em atraso refletem a reversão de forma coerente, e o honorário pode voltar a contar como por pagar

**Plans**: TBD
**UI hint**: yes

Notas da fase:

- Pode correr em paralelo com a Phase 136 (ambas dependem só da 134).
- Cria tabelas ou colunas: script idempotente em `backend/migrations/` e linha no `README.md` na mesma fase.
- Os quatro pontos de reversão (conta corrente, honorário, KPI, alerta) devem partilhar uma única fonte de cálculo, para não repetir a divergência de implementações já vista noutros marcos.

### Phase 136: Formato eFatura e Adaptador Simulado

**Goal**: Cada documento emitido produz um XML no formato eFatura exato, com IUD, e é comunicado em segundo plano a um adaptador de interface única com implementação simulada, sempre identificado como sem validade fiscal.
**Depends on**: Phase 134
**Requirements**: DFE-01, DFE-02, DFE-03, DFE-04, DFE-05, DFE-06, DFE-07
**Success Criteria** (what must be TRUE):

  1. Cada documento emitido tem um XML eFatura (DFE) validado contra o esquema oficial antes de ser dado como pronto, e um IUD de 45 caracteres com a estrutura oficial, marcado como ambiente de teste
  2. O modo do adaptador é escolhido por configuração do deployment; no v3.0 só existe "simulado", e um modo desconhecido impede o arranque da aplicação
  3. Cada documento mostra o estado de comunicação (pendente, aceite em simulação, rejeitado, erro), atualizado em segundo plano sem atrasar o registo do pagamento, com retentativa automática das falhas transitórias; utilizador com `financeiro:edit` reprocessa um documento em erro
  4. Nenhum documento simulado aparece como autorizado pela DNRE: estado, série, IUD e ecrãs marcam-no inequivocamente como "simulação, sem validade fiscal" (PDF e email herdam esta marca na Phase 137)
  5. Uma falha persistente de comunicação gera uma notificação in-app para os responsáveis do escritório

**Plans**: TBD
**UI hint**: yes

Notas da fase:

- **Gate antes de fechar a fase:** verificação do formato contra as fontes primárias (Manual Técnico vigente e XSD em `efatura.cv`), que estavam bloqueadas durante a pesquisa. O formato atual assenta no pacote XSD de 2024-05-27 lido em cópias públicas.
- O parsing e a validação de XML têm de ser endurecidos contra XXE desde o início (verificado em OPER-04).
- Cria tabelas ou colunas: script idempotente em `backend/migrations/` e linha no `README.md` na mesma fase.
- A comunicação corre fora da transação do pagamento (a decisão de mecanismo, por exemplo tarefa agendada com retentativa, fica para o planeamento).

### Phase 137: PDF, Armazenamento, Email e Relatório

**Goal**: Cada Fatura-Recibo e Nota de Crédito tem um PDF fiel aos dados guardados, descarregável com o XML, entregue por email ao cliente quando configurado, consultável na ficha do cliente e exportável em resumo mensal para o contabilista.
**Depends on**: Phase 134, Phase 135, Phase 136 (o email só sai depois de o documento estar emitido e comunicado; o PDF inclui a Nota de Crédito e a marca de simulação)
**Requirements**: ENTR-01, ENTR-02, ENTR-03, ENTR-04, ENTR-05, ENTR-06, ENTR-07, RELF-01
**Success Criteria** (what must be TRUE):

  1. Cada Fatura-Recibo e Nota de Crédito tem um PDF gerado no servidor a partir dos dados guardados no documento, com todos os elementos legais (emitente, adquirente, NIFs, série e número, IUD, data, base, IVA ou motivo de isenção, retenção, total) e a marca de simulação
  2. Utilizador com `financeiro:view` descarrega o PDF e o XML dos documentos do seu escritório, e cada descarga fica registada na auditoria
  3. Com o envio automático ligado, o cliente recebe por email o PDF e o XML, só depois de o documento estar emitido e comunicado e nunca dentro da operação de registo do pagamento; o utilizador vê o estado de entrega e, com `financeiro:edit`, reenvia manualmente
  4. Sem SMTP configurado a aplicação arranca e emite normalmente, mostrando o envio como "não configurado"; uma falha persistente de envio gera uma notificação in-app para os responsáveis do escritório
  5. Os documentos fiscais de um cliente aparecem na sua ficha apenas para consulta e descarga, sem poderem ser apagados como documentos comuns
  6. Utilizador com `financeiro:view` exporta, por mês, um CSV com os documentos emitidos e respetivas bases, IVA, retenções e totais

**Plans**: TBD
**UI hint**: yes

Notas da fase:

- **Gate humano antes de ligar um SMTP real:** checkpoint obrigatório (`safety.always_confirm_external_services`); até lá o email corre sem servidor real ("não configurado") ou contra um capturador local.
- O PDF não pode aceder a recursos externos (verificado em OPER-04). Os ficheiros guardam-se em MinIO via `StorageService`, com descarga por URL pré-assinado, seguindo o padrão já existente.
- Cria tabelas ou colunas (estado de entrega, tentativas): script idempotente em `backend/migrations/` e linha no `README.md` na mesma fase.
- Credenciais SMTP só por variáveis de ambiente, documentadas em `.env.example`, compose e `deploy.yml` nesta fase (verificado em OPER-03).

### Phase 138: Plataforma como Emitente

**Goal**: A LexCV emite Faturas-Recibo e Notas de Crédito aos escritórios pela subscrição, a partir da consola `/plataforma`, e cada escritório consulta as suas faturas de subscrição sem aceder a nada de outros escritórios.
**Depends on**: Phase 137 (reutiliza motor de emissão, formato, PDF e email já provados)
**Requirements**: SUBS-01, SUBS-02, SUBS-03, SUBS-04, SUBS-05
**Success Criteria** (what must be TRUE):

  1. `PLATAFORMA_ADMIN` regista os dados fiscais da LexCV (NIF, firma, morada, regime) em `/plataforma`, e a emissão de faturas de subscrição fica bloqueada enquanto estiverem incompletos
  2. `PLATAFORMA_ADMIN` regista um pagamento de subscrição de um escritório (valor, data, método, período coberto), o que emite na mesma operação uma Fatura-Recibo da LexCV para os dados fiscais do escritório
  3. `PLATAFORMA_ADMIN` lista as faturas de subscrição emitidas, descarrega o PDF e emite Notas de Crédito sobre elas
  4. As séries e a numeração da LexCV são independentes das de qualquer escritório
  5. O escritório recebe a fatura de subscrição por email e consulta as suas faturas de subscrição em Definições, só em leitura, e **um escritório nunca vê, por nenhum endpoint, documentos emitidos a outro escritório nem documentos que o próprio escritório emitiu aos seus clientes misturados com os da plataforma** (critério de isolamento: a leitura cruzada só devolve documentos em que o escritório autenticado é o adquirente e a LexCV é o emitente)

**Plans**: TBD
**UI hint**: yes

Notas da fase:

- **Esta é a primeira superfície de leitura entre tenants do marco** (escritório a ler documentos em que é adquirente e a plataforma é emitente). O critério 5 é obrigatório e deve ter teste automatizado com dois escritórios e a plataforma, não só verificação manual.
- **Dependência de dados reais para o UAT:** NIF, morada e regime fiscal reais da LexCV têm de estar disponíveis antes do UAT desta fase.
- Cria tabelas ou colunas: script idempotente em `backend/migrations/` e linha no `README.md` na mesma fase.
- A emissão e as Notas de Crédito de plataforma ficam restritas a `PLATAFORMA_ADMIN`, inalcançáveis a partir de qualquer endpoint de escritório.

### Phase 139: Fecho — Isolamento, Segurança, Migrações e UAT

**Goal**: O marco fecha com prova de que nenhum dado fiscal atravessa tenants, as instalações existentes arrancam com as novas tabelas em ambos os modos de esquema, as credenciais não vazam e a análise estática continua limpa.
**Depends on**: Phase 138 (e, por consequência, todas as anteriores)
**Requirements**: OPER-01, OPER-02, OPER-03, OPER-04
**Success Criteria** (what must be TRUE):

  1. Uma auditoria com dois escritórios e a plataforma confirma que nenhum endpoint de documentos fiscais, séries ou configurações devolve ou altera dados de outro tenant
  2. Uma instalação existente arranca com as novas tabelas tanto com `ddl-auto=update` como com `validate`, e todos os scripts de migração do marco são idempotentes e estão registados em `backend/migrations/README.md`
  3. Credenciais SMTP e configuração eFatura vêm só de variáveis de ambiente, nunca aparecem em logs, respostas de erro ou base de dados, e estão documentadas em `.env.example`, nos ficheiros compose e em `deploy.yml`
  4. `mvn spotbugs:check` passa limpo sem novas exclusões para código próprio, com parsing de XML endurecido contra XXE e geração de PDF sem acesso a recursos externos

**Plans**: TBD

Notas da fase:

- OPER-02 e OPER-03 são verificados aqui, mas a obrigação de entregar o script de migração e a documentação de variáveis aplica-se a cada fase que cria tabelas ou variáveis (133 a 138). Esta fase audita e fecha lacunas; não é onde se começa a escrever migrações.
- Inclui o UAT ao vivo do marco, com os dados fiscais reais da LexCV disponibilizados para a Phase 138.

## Progress

**Execution Order:**
Fases executam-se por ordem numérica: 133 → 134 → {135, 136 em paralelo possível} → 137 → 138 → 139.

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 133. Fundação Fiscal | 8/8 | Complete   | 2026-10-04 |
| 134. Fatura-Recibo Atómica nos Honorários | 1/14 | In progress | - |
| 135. Nota de Crédito | 0/TBD | Not started | - |
| 136. Formato eFatura e Adaptador Simulado | 0/TBD | Not started | - |
| 137. PDF, Armazenamento, Email e Relatório | 0/TBD | Not started | - |
| 138. Plataforma como Emitente | 0/TBD | Not started | - |
| 139. Fecho — Isolamento, Segurança, Migrações e UAT | 0/TBD | Not started | - |
