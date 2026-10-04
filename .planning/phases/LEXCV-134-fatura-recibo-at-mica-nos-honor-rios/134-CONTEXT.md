# Phase 134: Fatura-Recibo Atómica nos Honorários - Context

**Gathered:** 2026-10-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Num escritório com faturação ativa (Phase 133), registar um pagamento de honorários emite na mesma
transação uma Fatura-Recibo (tipo FR, ambiente SIMULADO) fiscalmente coerente e imutável; o
utilizador pré-visualiza antes de emitir, e consulta/lista os documentos. Inclui guardas de
eliminação, fusão de clientes, idempotência e regras de data/adquirente. Fora: Nota de Crédito (135),
XML/IUD/comunicação (136), PDF/email (137), plataforma (138). Requisitos EMIS-01..12.

**Gate do contabilista:** o utilizador decidiu avançar com as regras abaixo parametrizadas e validar
com o contabilista depois (registado em STATE.md Pending Todos) — antes de ativar faturação real.

</domain>

<decisions>
## Implementation Decisions

### Fluxo de emissão (UI)
- Pré-visualização calculada no BACKEND num endpoint sem efeitos (`POST /api/v1/faturacao/pre-visualizacao`) — fonte única do cálculo; frontend só apresenta (decisão "frontend burro")
- Emissão no formulário de pagamento existente em `web/src/app/(dashboard)/financeiro/[id]/page.tsx`: com faturação ativa, "Registar" abre diálogo de confirmação com a pré-visualização; com faturação desligada, comportamento atual inalterado
- Retenção: checkbox "Aplicar retenção na fonte" + taxa pré-preenchida pelo parâmetro `RETENCAO_SUGERIDA` (20), editável (0 < taxa ≤ 100)
- Método de pagamento obrigatório com faturação ativa; tabela fixa no backend `metodo` atual → código de meio de pagamento eFatura (dinheiro, transferência, cheque, cartão/multibanco, outro); códigos exatos UN/ECE D19B confirmados na 136

### Documento e cálculo
- Uma linha por Fatura-Recibo; descrição controlada "Honorários por serviços jurídicos — Processo n.º {numero}" (nunca a descrição livre do honorário — sigilo profissional)
- Arredondamento: `base = round(total / (1 + iva/100), 2, HALF_UP)`; `iva = total − base`; `retencao = round(base × taxa/100, 2, HALF_UP)`; `liquidoRecebido = total − retencao`; regime ISENTO: base = total, IVA = 0, motivo de isenção no documento. Valor pago é sempre IVA incluído (decisão do marco). Taxa IVA lida de `ParametroFiscalService.valorVigenteHoje(IVA_TAXA_NORMAL)`
- Snapshot em colunas planas em `t_documento_fiscal` (emitente: NIF, firma, morada, localidade, regime, motivo; adquirente: NIF, nome, morada, cliente_id) + `t_documento_fiscal_linha`; entidade `@Immutable`, repositório estreito sem `delete*` (padrão AuditLog/AuditLogRepository)
- Estado de comunicação em tabela satélite `t_comunicacao_fiscal` com linha `PENDENTE` criada na mesma transação (consumida na 136); documento guarda `ambiente` (SIMULADO)
- Conta corrente creditada do TOTAL (dinheiro + imposto retido)

### Guardas e atomicidade
- Idempotência: `chaveIdempotencia` (UUID gerado com `crypto.randomUUID()` ao abrir o diálogo) no corpo; `UNIQUE(tenant_id, chave_idempotencia)` em `t_documento_fiscal`; mesma chave → devolve o mesmo resultado; mesma chave com payload diferente → 409 `CHAVE_REUTILIZADA`
- Com faturação ativa, `POST /pagamentos` delega num `PagamentoFaturadoService` `@Transactional` (pagamento + lock da conta corrente + `NumeracaoService.proximoNumero` + documento + linha + comunicação PENDENTE, tudo ou nada; ordem de locks: configuração → conta corrente → série); com faturação desligada, o caminho atual de `createPagamento` fica EXATAMENTE como está (CFG-03). O teste-guarda `FaturacaoDesligadaPagamentoInalteradoTest` (133-05) tem de ser atualizado deliberadamente para permitir só a delegação condicional, mantendo a prova de que o ramo desligado não muda
- Data: com faturação ativa, `dataPagamento` omitida = hoje (`Clock` em `Atlantic/Cape_Verde`); data diferente → 422 `DATA_PAGAMENTO_RETROATIVA`
- Adquirente: recusa 422 com campo em falta quando o cliente não tem NIF `^[1-9]\d{8}$`, nome (3–150) ou morada (≤100)
- Guardas 409: apagar pagamento faturado; apagar cliente, processo ou honorário com documentos fiscais
- Fusão de clientes: `cliente_id` dos documentos passa para o cliente resultante (UPDATE nativo da coluna de ligação; snapshot do adquirente inalterado) — verificar por IT que funciona com `@Immutable`

### Consulta
- Nova página `/financeiro/documentos-fiscais` (separador/botão no topo do Financeiro), gated `financeiro:view` em ambas as camadas
- Lista: filtros cliente, período, tipo, estado; paginação server-side; padrão `DataTable` partilhado (`web/src/components/shared/data-table/`)
- Detalhe `/financeiro/documentos-fiscais/[id]` com snapshot, valores, estado de comunicação, ligação a pagamento/honorário; marca "Simulação — sem validade fiscal" visível
- Lista de pagamentos do honorário ganha coluna "Documento fiscal" (n.º da FR ou "Sem documento fiscal")

### Claude's Discretion
- Nomes de DTOs/serviços, divisão em planos/ondas, formato de número apresentado (ex.: `SIM-FR-2026/1`)
- Teste de serviço com dois emitentes (tenant escritório + outro tenant) para provar a parametrização do núcleo desde já (pesquisa C12)

</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 133: `NumeracaoService.proximoNumero` (MANDATORY, lock de série), `ConfiguracaoFiscalService` (lock de configuração — CR-01), `ParametroFiscalService`, `RecusaFiscalException` + handler `{message, code, campo}`, `ClockConfig`, `AuditoriaFiscalService`, `ApiError`/`semToastParaStatus` em `apiFetch`
- `ResourceController.createPagamento` (~3039-3081), `deletePagamento` (~3156-3186), `listHonorarioPagamentos`, `mergeClientes`, `deleteCliente`, `deleteProcesso`
- `AuditLog`/`AuditLogRepository` como modelo de entidade imutável e repositório estreito
- `DataTable` partilhado, `Combobox`, `DatePickerField`, `Dialog`/`AlertDialog`

### Established Patterns
- `Honorario`/`Pagamento` sem `tenant_id` (isolamento transitivo via Processo) — entidades fiscais novas levam `tenant_id` próprio; repositórios só com finders por tenant
- `RecusaTransacional` / exceção dentro de `@Transactional` para rollback
- Migração manual idempotente + linha no README no mesmo commit; enums como varchar com `@Convert` e `length` (lição 133 WR-01)

### Integration Points
- `web/src/app/(dashboard)/financeiro/[id]/page.tsx` (form de pagamento), `web/src/hooks/use-financeiro*.ts`, `web/src/types/financeiro.ts`
- `Honorario.totalPago` @Formula (inalterado nesta fase)

</code_context>

<specifics>
## Specific Ideas

- Pesquisa: `.planning/research/SUMMARY.md` (Phase 134, C2, C8, C11), `ARCHITECTURE.md` §3–§4, `PITFALLS.md` P-02, P-03, P-05, P-06, P-10, P-19..P-21
- Exemplo de referência (a validar): 120 000,00 CVE IVA incluído, retenção 20% → base 104 347,83; IVA 15 652,17; retenção 20 869,57; líquido 99 130,43; conta corrente +120 000,00

</specifics>

<deferred>
## Deferred Ideas

- Clientes não residentes / consumidor final — futuro (DOCX-02)
- Despesas/provisões fora da base de IVA — futuro (DOCX-03)

</deferred>

<research_resolutions>
## Resolutions of 134-RESEARCH.md open questions (orchestrator, 2026-10-04, research recommendations adopted)

1. Lock order extended to: configuração → cliente → processo → conta corrente → série; merge and the cliente/processo/pagamento deletes lock cliente/processo first. An IT must prove no deadlock and no orphan document.
2. Overpayment beyond the honorário total keeps current behaviour (allowed); flagged for the accountant validation already pending in STATE.md.
3. Emission writes an `AuditLog` event (fiscal audit, same pattern as AuditoriaFiscalService).
4. Adquirente `localidade` snapshotted as a nullable column.
5. Pré-visualização gated by `financeiro:edit` (same as registering a payment).
</research_resolutions>
