# Phase 135: Nota de Crédito - Context

**Gathered:** 2026-10-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Utilizador com `financeiro:manage` corrige uma Fatura-Recibo emitida (Phase 134), total ou
parcialmente, emitindo uma Nota de Crédito (tipo NC, ambiente SIMULADO) imutável, numerada na sua
série própria, que reverte o valor de forma coerente em conta corrente, total pago do honorário, KPI
mensal e alerta de honorário em atraso. Fora: XML/IUD/comunicação (136 — mas a NC também cria a linha
`t_comunicacao_fiscal` PENDENTE, tal como a FR), PDF/email (137), NC de subscrição (138).
Requisitos NCRD-01..03.

</domain>

<decisions>
## Implementation Decisions

### Modelo
- A NC cria um `Pagamento` NEGATIVO (estorno) no mesmo honorário, identificado por `DocumentoFiscal.pagamento_estorno_id` (pesquisa C2 opção A); as quatro leituras de "pago" (`Honorario.totalPago` @Formula, saldo da `ContaCorrente`, `calculateMensalReceived`, alerta `HONORARIO_ATRASADO` em `AlertasDiariosJob`) ficam coerentes sem lógica duplicada
- Condições do estorno: só criado pelo `NotaCreditoService`, nunca por `POST /pagamentos`; nunca gera Fatura-Recibo (`UNIQUE(pagamento_estorno_id)`); não é apagável; "faturado" = existe documento com `pagamento_id = id` OU `pagamento_estorno_id = id`; o DTO expõe `estorno` e a UI mostra "Estorno (NC n.º …)"
- Série própria por (emitente, NC, ano, ambiente): ex. `SIM-NC-2026` via `NumeracaoService` (tipo NC já existe no enum `TipoDocumentoFiscal`)
- NC parcial: o utilizador indica o valor a creditar (IVA incluído); base/IVA calculados com a taxa de IVA da FR ORIGINAL (snapshot, não a vigente hoje) e a mesma lógica de arredondamento `CalculoFiscal`; retenção proporcional à da FR original; NC total credita exatamente os valores remanescentes
- Motivo: lista curta mapeada para `IssueReasonCode` (Anulação total, Correção de valor, Erro nos dados do cliente, Outro) + texto livre obrigatório; o mapeamento exato de códigos XSD é confirmado na 136
- Documento NC: `@Immutable`, snapshot próprio (emitente/adquirente copiados da FR original — o adquirente da NC é o da FR), referência `documento_origem_id` à FR

### Regras
- Teto cumulativo: soma dos totais das NC de uma FR ≤ total da FR; verificado sob o lock de configuração do tenant (serializa todas as emissões do tenant) + lock da FR de origem se necessário; recusa 409/422 com código claro (ex. `NC_EXCEDE_ORIGINAL`)
- Não se credita uma NC (`NC_SOBRE_NC`) nem um documento de outro emitente (404 cross-tenant)
- Estorno e NC não podem ser apagados (guardas 409 já existentes estendidas)
- Só `financeiro:manage` (backend `@PreAuthorize` + frontend `hasScopedPermission` exato); data = hoje em `Atlantic/Cape_Verde`
- Idempotência: `chaveIdempotencia` no corpo, `UNIQUE(tenant_id, chave_idempotencia)`, ciclo de vida da chave igual ao corrigido na 134 (CR-02: a chave pertence ao conteúdo do pedido e sobrevive a falhas ambíguas)
- Ordem de locks igual à 134: configuração → cliente → processo → conta corrente → série
- Requer faturação ativa (a NC é um documento fiscal); auditoria `AuditLog` como na emissão da FR

### Efeitos
- KPI mensal: o estorno conta no mês de emissão da NC (visão de caixa); corrigir `calculateMensalReceived` (compara só o mês sem o ano, `LocalDate.now()` sem zona, sem null-check) NESTA fase
- Honorário reabre (volta a "por pagar") e pode voltar a disparar `HONORARIO_ATRASADO` (decisão do marco)
- Teste de coerência: após NC total e parcial, saldo, `totalPago`, contribuição ao KPI e condição do alerta concordam

### UI
- Botão "Emitir Nota de Crédito" no detalhe da FR (`/financeiro/documentos-fiscais/[id]`), visível só com `financeiro:manage` e só em documentos FR com valor creditável > 0; diálogo com Total/Parcial, valor (parcial), motivo (select) + texto, pré-visualização calculada no backend, confirmação
- Detalhe da FR mostra as NC emitidas e o valor ainda creditável; detalhe da NC mostra ligação à FR original
- Lista de pagamentos do honorário: o estorno aparece como "Estorno (NC n.º …)", sem botão de apagar
- Lista de documentos fiscais mostra NC (filtro tipo passa a ter FR e NC)

### Claude's Discretion
- Nomes de DTOs/endpoints (ex.: `POST /api/v1/documentos-fiscais/{id}/notas-credito/pre-visualizacao` e `POST /api/v1/documentos-fiscais/{id}/notas-credito`), divisão em planos

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 134: `PagamentoFaturadoService` (padrão de transação, locks, idempotência, auditoria), `ComposicaoFaturaRecibo`, `CalculoFiscal`, `PreVisualizacaoFaturaService`, `DocumentoFiscal`/`DocumentoFiscalLinha` `@Immutable`, `ComunicacaoFiscal`, `DocumentoFiscalService`/`DocumentoFiscalController`, guardas 409, `FixturaEmissaoFiscal` (testes), web `lib/idempotencia.ts`, `lib/erros-emissao.ts`, preview dialog
- Phase 133: `NumeracaoService`, `ConfiguracaoFiscalService` lock, `ParametroFiscalService`

### Established Patterns
- Locks: configuração → cliente → processo → conta corrente → série; o primeiro read de uma linha bloqueada é o próprio lock (OSIV)
- Migração manual idempotente + linha README no mesmo commit; enums varchar com `@Convert` + `length`
- CFG-03: `registarPagamentoLegado` congelado por SHA — não tocar

### Integration Points
- `ResourceController.calculateMensalReceived` (~3247-3263), `AlertasDiariosJob` (~276-277), `Honorario.totalPago` @Formula
- `deletePagamento` guard (estender a `pagamento_estorno_id`)

</code_context>

<specifics>
## Specific Ideas

- Pesquisa: `.planning/research/SUMMARY.md` C2 e Phase 135; `PITFALLS.md` P-13, P-14
- Validação do contabilista pendente cobre também prazo/regras de NC (A4)

</specifics>

<deferred>
## Deferred Ideas

- NC sobre faturas de subscrição — fase 138
- Prazo máximo de emissão de NC — aguarda contabilista/Manual Técnico

</deferred>
