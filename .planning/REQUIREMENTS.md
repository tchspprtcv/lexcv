# Requirements: LexCV — Marco v3.0 Faturação Eletrónica (eFatura CV)

**Defined:** 2026-10-04
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

Pesquisa de suporte: `.planning/research/SUMMARY.md` (e `STACK.md`, `FEATURES.md`, `ARCHITECTURE.md`,
`PITFALLS.md`). As fontes primárias do eFatura (`efatura.cv`) estavam bloqueadas no ambiente da
pesquisa — o formato assenta no pacote XSD oficial 2024-05-27 lido em cópias públicas e tem de ser
confirmado contra o Manual Técnico vigente antes de fechar a fase do adaptador.

## Decisões de âmbito (tomadas pelo utilizador em 2026-10-04)

- Dois emitentes: cada escritório fatura os seus clientes; a plataforma LexCV fatura os escritórios
- Uma Fatura-Recibo por pagamento, emitida na mesma operação; correção só por Nota de Crédito
- Ligação ao eFatura: formato exato + adaptador com implementação simulada; ligação real fora do v3.0
- Faturação ativada por escritório (desligada até o escritório a ligar; irreversível após o 1.º documento)
- Com a faturação ativa, o pagamento só aceita a data de hoje (hora de Cabo Verde)
- Valores de honorários e pagamentos são com IVA incluído
- Envio automático de email ao cliente desligado por omissão (documentos simulados sem validade fiscal)
- Permissões: reutilizar `financeiro:view/edit/manage`, sem scope novo
- A Nota de Crédito reabre o honorário (volta a contar como por pagar)
- O escritório vê na app as faturas de subscrição que a LexCV lhe emite
- Regras fiscais (IVA 15%, retenção) como dados parametrizáveis; **validação por contabilista antes
  de planear a fase de emissão**

## v3.0 Requirements

### Configuração Fiscal

- [x] **CFG-01**: Administrador do escritório (com `financeiro:manage`) regista e edita os dados fiscais do escritório — NIF, firma, morada e regime de IVA (normal, ou isento com motivo) — em Definições → Faturação, com o NIF validado (9 dígitos, primeiro entre 1 e 9)
- [x] **CFG-02**: Administrador do escritório ativa a faturação apenas quando os dados fiscais estão completos; depois do primeiro documento emitido, a faturação não pode ser desligada e o NIF do emitente não pode ser alterado
- [x] **CFG-03**: Enquanto a faturação de um escritório estiver desligada, registar um pagamento comporta-se exatamente como hoje (sem documento fiscal)
- [x] **CFG-04**: A taxa de IVA e a taxa de retenção sugerida são parâmetros com data de vigência (semeados com 15% e 20%), nunca constantes no código
- [x] **CFG-05**: Cada documento recebe um número sequencial na sua série (emitente, tipo de documento, ano civil), sem lacunas nem duplicados, mesmo com pedidos concorrentes; a numeração reinicia em cada ano
- [x] **CFG-06**: Administrador do escritório liga ou desliga o envio automático de faturas por email ao cliente (desligado por omissão), aceitando explicitamente o aviso de que os documentos simulados não têm validade fiscal

### Emissão de Fatura-Recibo

- [x] **EMIS-01**: Com a faturação ativa, registar um pagamento de honorários emite uma Fatura-Recibo na mesma operação — se uma das partes falhar, nem o pagamento, nem o documento, nem a atualização da conta corrente ficam gravados
- [x] **EMIS-02**: Antes de confirmar o pagamento, o utilizador vê uma pré-visualização da Fatura-Recibo (adquirente, base, IVA, retenção, total) e só emite depois de confirmar
- [x] **EMIS-03**: A Fatura-Recibo decompõe o valor pago (IVA incluído) em base tributável e IVA segundo o regime do escritório; num escritório isento, mostra o motivo de isenção em vez de IVA
- [x] **EMIS-04**: O utilizador pode aplicar retenção na fonte a um pagamento (opcional, taxa sugerida editável, sobre a base sem IVA); o documento mostra o valor retido e o valor líquido recebido, e a conta corrente é creditada do total
- [x] **EMIS-05**: Com a faturação ativa, um pagamento com data diferente de hoje (hora de Cabo Verde) é recusado com uma mensagem clara; sem data, assume a de hoje
- [x] **EMIS-06**: A emissão é recusada, indicando o que falta corrigir, quando o cliente não tem NIF cabo-verdiano válido, nome ou morada (até 100 caracteres)
- [x] **EMIS-07**: Submeter o mesmo pagamento duas vezes (duplo clique, repetição de pedido) produz um único pagamento e uma única Fatura-Recibo
- [x] **EMIS-08**: A Fatura-Recibo guarda os dados do emitente e do adquirente tal como estavam no momento da emissão; alterar depois o cliente ou o escritório não a muda, e nenhum ecrã ou endpoint permite editá-la ou apagá-la
- [x] **EMIS-09**: Um pagamento faturado não pode ser apagado, e apagar o cliente, o processo ou o honorário a que pertence é recusado; fundir clientes preserva a ligação dos documentos ao cliente resultante
- [x] **EMIS-10**: O método de pagamento registado é mapeado para o meio de pagamento correspondente do eFatura (dinheiro, transferência, cheque, etc.)
- [x] **EMIS-11**: Utilizador com `financeiro:view` lista os documentos fiscais do seu escritório (filtros por cliente, período, tipo e estado) e abre o detalhe de cada um, sem nunca ver documentos de outro escritório
- [x] **EMIS-12**: Pagamentos registados antes da ativação não são faturados retroativamente e aparecem identificados como "sem documento fiscal"

### Nota de Crédito

- [x] **NCRD-01**: Utilizador com `financeiro:manage` emite uma Nota de Crédito total ou parcial sobre uma Fatura-Recibo, com motivo obrigatório e referência à fatura original, numerada na sua própria série
- [x] **NCRD-02**: A soma das Notas de Crédito de uma fatura nunca excede o valor original, e não é possível creditar uma Nota de Crédito nem um documento de outro emitente
- [x] **NCRD-03**: A Nota de Crédito reverte o valor de forma coerente no saldo da conta corrente, no total pago do honorário, no KPI mensal do dashboard e no alerta de honorário em atraso — o honorário pode voltar a contar como por pagar

### Formato eFatura e Comunicação

- [ ] **DFE-01**: Cada documento emitido gera o XML no formato eFatura (DFE), validado contra o esquema oficial antes de ser dado como pronto
- [ ] **DFE-02**: Cada documento recebe um IUD de 45 caracteres com a estrutura oficial, marcado como ambiente de teste enquanto a ligação for simulada
- [x] **DFE-03**: A comunicação com o eFatura passa por um adaptador com interface única; o modo é escolhido por configuração do deployment (no v3.0 só "simulado" existe) e um modo desconhecido impede o arranque
- [x] **DFE-04**: Cada documento mostra o seu estado de comunicação (pendente, aceite em simulação, rejeitado, erro), atualizado em segundo plano depois da emissão, sem atrasar o registo do pagamento; falhas transitórias são retentadas automaticamente
- [x] **DFE-05**: Utilizador com `financeiro:edit` reprocessa a comunicação de um documento em erro
- [ ] **DFE-06**: Um documento simulado nunca aparece como autorizado pela DNRE — estado, série, IUD, ecrãs, PDF e email marcam-no inequivocamente como "simulação, sem validade fiscal"
- [x] **DFE-07**: Uma falha persistente de comunicação gera uma notificação in-app para os responsáveis do escritório

### PDF e Entrega

- [ ] **ENTR-01**: Cada Fatura-Recibo e Nota de Crédito tem um PDF gerado no servidor a partir dos dados guardados no documento, com os elementos legais (emitente, adquirente, NIFs, série e número, IUD, data, base, IVA ou motivo de isenção, retenção, total) e a marca de simulação
- [ ] **ENTR-02**: Utilizador com `financeiro:view` descarrega o PDF e o XML de um documento do seu escritório, e cada descarga fica registada na auditoria
- [ ] **ENTR-03**: Com o envio automático ligado, o cliente recebe por email o documento em PDF e XML, enviado só depois de o documento estar emitido e comunicado, nunca dentro da operação de registo do pagamento
- [ ] **ENTR-04**: Utilizador vê o estado de entrega por email de cada documento e, com `financeiro:edit`, reenvia-o manualmente
- [ ] **ENTR-05**: Uma falha persistente de envio de email gera uma notificação in-app para os responsáveis do escritório
- [ ] **ENTR-06**: Sem SMTP configurado, o sistema arranca e emite normalmente, mostrando o envio como "não configurado"
- [ ] **ENTR-07**: Os documentos fiscais de um cliente aparecem na sua ficha, apenas para consulta e descarga, sem poderem ser apagados como documentos comuns

### Relatório Fiscal

- [ ] **RELF-01**: Utilizador com `financeiro:view` exporta, por mês, um CSV com os documentos emitidos e as respetivas bases, IVA, retenções e totais, para entregar ao contabilista

### Faturação da Plataforma

- [ ] **SUBS-01**: `PLATAFORMA_ADMIN` regista os dados fiscais da LexCV (NIF, firma, morada, regime) na consola `/plataforma`; a emissão de faturas de subscrição fica bloqueada enquanto estiverem incompletos
- [ ] **SUBS-02**: `PLATAFORMA_ADMIN` regista em `/plataforma` um pagamento de subscrição recebido de um escritório (valor, data, método, período coberto), o que emite na mesma operação uma Fatura-Recibo da LexCV para os dados fiscais do escritório
- [ ] **SUBS-03**: `PLATAFORMA_ADMIN` lista as faturas de subscrição emitidas, descarrega o PDF e emite Notas de Crédito sobre elas
- [ ] **SUBS-04**: O escritório recebe a fatura de subscrição por email e consulta as suas faturas de subscrição em Definições (só leitura), sem acesso a documentos emitidos a outros escritórios
- [ ] **SUBS-05**: As séries e a numeração da LexCV são independentes das de qualquer escritório

### Segurança e Operação

- [ ] **OPER-01**: Documentos fiscais, séries e configurações ficam isolados por tenant — uma auditoria com dois escritórios e a plataforma confirma que nenhum endpoint devolve ou altera dados de outro tenant
- [ ] **OPER-02**: Instalações existentes arrancam com as novas tabelas tanto em `ddl-auto=update` como em `validate`, com scripts de migração idempotentes registados em `backend/migrations/README.md`
- [ ] **OPER-03**: Credenciais SMTP e configuração eFatura vêm só de variáveis de ambiente, nunca aparecem em logs, respostas de erro ou base de dados, e estão documentadas em `.env.example`, nos ficheiros compose e em `deploy.yml`
- [ ] **OPER-04**: SpotBugs/FindSecBugs continua limpo sem novas exclusões para código próprio (parsing de XML endurecido contra XXE, PDF sem acesso a recursos externos)

## Future Requirements

Reconhecidos, fora do v3.0. Candidatos ao marco de ligação real ao eFatura ou posteriores.

### Ligação Real ao eFatura

- **EFAT-01**: Assinatura XAdES-BES dos documentos com certificado ICP-CV
- **EFAT-02**: Envio e autorização real em tempo real na DNRE (OAuth2, homologação do LexCV como software, `Software.Code`)
- **EFAT-03**: Custódia de credenciais e certificados por escritório, com armazenamento cifrado
- **EFAT-04**: Modo de contingência (emissão offline com comunicação até 5 dias úteis)
- **EFAT-05**: Ativação do modo real por emitente, em conjunção com o modo do deployment
- **EFAT-06**: QR code para a consulta pública do documento no portal eFatura

### Documentos e Casos Adicionais

- **DOCX-01**: Outros tipos de documento (Fatura, Recibo, Nota de Débito, Talão de Venda)
- **DOCX-02**: Clientes não residentes e consumidor final sem NIF
- **DOCX-03**: Despesas reembolsáveis e provisões fora da base de IVA na fatura de honorários
- **DOCX-04**: Scope RBAC próprio `faturas:*`, com migração convergente dos papéis dos escritórios
- **DOCX-05**: Cancelamento direto de DFE nas condições que a DNRE admitir
- **DOCX-06**: Exportação SAF-T

## Out of Scope

| Feature | Reason |
|---------|--------|
| Pagamento online (Vinti4/SISP ou outro gateway) | O v3.0 regista pagamentos recebidos fora do sistema; cobrar online é um marco próprio |
| Email para notificações | O email existe só para documentos fiscais; as notificações continuam in-app (exclusão revista apenas parcialmente) |
| Editar ou apagar documentos fiscais emitidos | Ilegal; a única correção é a Nota de Crédito |
| Registar pagamento sem fatura num escritório com faturação ativa | Seria uma via de evasão à regra "uma Fatura-Recibo por pagamento" |
| Desligar a faturação depois do primeiro documento | Quebraria a continuidade da série e da obrigação fiscal |
| Faturação retroativa de pagamentos existentes | Decisão do utilizador; documentos datados no passado quebram a sequência cronológica |
| Motor automático de decisão de retenção/isenção | Regras finas não verificadas em fonte primária; a retenção é escolha explícita por pagamento |
| Taxas de IVA ou retenção no código | Mudam por lei; ficam como parâmetros com vigência |
| Contabilidade completa | O v3.0 emite documentos fiscais e um resumo mensal; não faz contabilidade |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| CFG-01 | Phase 133 | Complete |
| CFG-02 | Phase 133 | Complete |
| CFG-03 | Phase 133 | Complete |
| CFG-04 | Phase 133 | Complete |
| CFG-05 | Phase 133 | Complete |
| CFG-06 | Phase 133 | Complete |
| EMIS-01 | Phase 134 | Complete |
| EMIS-02 | Phase 134 | Complete |
| EMIS-03 | Phase 134 | Complete |
| EMIS-04 | Phase 134 | Complete |
| EMIS-05 | Phase 134 | Complete |
| EMIS-06 | Phase 134 | Complete |
| EMIS-07 | Phase 134 | Complete |
| EMIS-08 | Phase 134 | Complete |
| EMIS-09 | Phase 134 | Complete |
| EMIS-10 | Phase 134 | Complete |
| EMIS-11 | Phase 134 | Complete |
| EMIS-12 | Phase 134 | Complete |
| NCRD-01 | Phase 135 | Complete |
| NCRD-02 | Phase 135 | Complete |
| NCRD-03 | Phase 135 | Complete |
| DFE-01 | Phase 136 | Pending |
| DFE-02 | Phase 136 | Pending |
| DFE-03 | Phase 136 | Complete |
| DFE-04 | Phase 136 | Complete |
| DFE-05 | Phase 136 | Complete |
| DFE-06 | Phase 136 | Pending |
| DFE-07 | Phase 136 | Complete |
| ENTR-01 | Phase 137 | Pending |
| ENTR-02 | Phase 137 | Pending |
| ENTR-03 | Phase 137 | Pending |
| ENTR-04 | Phase 137 | Pending |
| ENTR-05 | Phase 137 | Pending |
| ENTR-06 | Phase 137 | Pending |
| ENTR-07 | Phase 137 | Pending |
| RELF-01 | Phase 137 | Pending |
| SUBS-01 | Phase 138 | Pending |
| SUBS-02 | Phase 138 | Pending |
| SUBS-03 | Phase 138 | Pending |
| SUBS-04 | Phase 138 | Pending |
| SUBS-05 | Phase 138 | Pending |
| OPER-01 | Phase 139 | Pending |
| OPER-02 | Phase 139 | Pending |
| OPER-03 | Phase 139 | Pending |
| OPER-04 | Phase 139 | Pending |

**Coverage:**

- v3.0 requirements: 45 total
- Mapped to phases: 45
- Unmapped: 0 ✓

---
*Requirements defined: 2026-10-04*
*Last updated: 2026-10-04 after roadmap creation (Phases 133-139)*
