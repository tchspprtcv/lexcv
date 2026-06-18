# Requirements — v2.0 Módulo Financeiro

> Milestone goal: Completar e corrigir o módulo financeiro com migração camelCase, CRUD completo, KPIs, filtros, status de pagamento e exportação CSV.

---

## v2.0 Requirements

### Migração camelCase (Data Layer)

- [ ] **FIN-01**: O frontend usa campos camelCase (`processoId`, `valorTotal`, `dataAcordo`, `honorarioId`, `valorPago`, `dataPagamento`) consistentes com a serialização Jackson/Spring Boot
- [ ] **FIN-02**: Os tipos TypeScript `Honorario`, `Pagamento`, `HonorarioCreateRequest`, `PagamentoCreateRequest` usam camelCase sem campos snake_case

### Backend — Endpoints em falta

- [ ] **FIN-03**: Utilizador pode buscar um único honorário via `GET /honorarios/{id}` com tenant scoping correto
- [ ] **FIN-04**: Utilizador com permissão `financeiro:edit` pode editar um honorário via `PUT /honorarios/{id}` (valorTotal, descricao, dataAcordo)
- [ ] **FIN-05**: Utilizador com permissão `financeiro:manage` pode apagar um honorário via `DELETE /honorarios/{id}` (apenas se sem pagamentos)
- [ ] **FIN-06**: Utilizador com permissão `financeiro:manage` pode apagar um pagamento via `DELETE /pagamentos/{id}` com reversão do saldo na conta corrente

### Status do Honorário

- [ ] **FIN-07**: O sistema calcula automaticamente o estado de cada honorário: `Pendente` (0 pagamentos), `Parcialmente Pago` (pagamentos < valorTotal), `Pago` (pagamentos >= valorTotal)
- [ ] **FIN-08**: A lista de honorários exibe um badge de status por honorário com cores distintas

### KPIs Financeiros

- [ ] **FIN-09**: A página financeiro exibe cards de resumo no topo: total faturado, total recebido, em dívida, receita do mês corrente
- [ ] **FIN-10**: Os KPIs são calculados no frontend a partir dos dados já carregados (sem endpoint dedicado)

### Filtros e Pesquisa

- [ ] **FIN-11**: Utilizador pode filtrar a lista de honorários por processo (dropdown/autocomplete)
- [ ] **FIN-12**: Utilizador pode filtrar a lista de honorários por status (Pendente / Parcialmente Pago / Pago)
- [ ] **FIN-13**: Utilizador pode filtrar a lista de honorários por intervalo de datas (dataAcordo de/até)

### Edit/Delete na UI

- [ ] **FIN-14**: Utilizador com permissão `financeiro:edit` pode editar um honorário a partir da página de detalhe
- [ ] **FIN-15**: Utilizador com permissão `financeiro:manage` pode apagar um honorário com diálogo de confirmação
- [ ] **FIN-16**: Utilizador com permissão `financeiro:manage` pode apagar um pagamento com diálogo de confirmação

### Relatórios / Export

- [ ] **FIN-17**: Utilizador pode exportar a lista de honorários (com filtros aplicados) para CSV com campos: id, processo, cliente, valorTotal, totalPago, estado, dataAcordo

---

## Future Requirements (Deferred)

- Notificações de pagamentos em atraso (email/in-app)
- Dashboard financeiro com gráficos de receita mensal
- Fatura / recibo PDF gerado pelo sistema
- Integração contabilística / exportação para ERP

---

## Out of Scope

- Contabilidade completa / módulo ERP
- Cálculo automático de honorários (% do processo, tarifário)
- Pagamentos parcelados automáticos / planos de pagamento
- Integração com gateway de pagamento externo
- Finalização Módulo Agendamento (v1.10) — notificações, recorrência, drag & drop — adiado para milestone dedicado

---

## Traceability

| REQ-ID | Phase | Plan |
|--------|-------|------|
| FIN-01 | Phase 43 | TBD |
| FIN-02 | Phase 43 | TBD |
| FIN-03 | Phase 43 | TBD |
| FIN-04 | Phase 43 | TBD |
| FIN-05 | Phase 43 | TBD |
| FIN-06 | Phase 43 | TBD |
| FIN-07 | Phase 44 | TBD |
| FIN-08 | Phase 44 | TBD |
| FIN-09 | Phase 44 | TBD |
| FIN-10 | Phase 44 | TBD |
| FIN-11 | Phase 45 | TBD |
| FIN-12 | Phase 45 | TBD |
| FIN-13 | Phase 45 | TBD |
| FIN-14 | Phase 45 | TBD |
| FIN-15 | Phase 45 | TBD |
| FIN-16 | Phase 45 | TBD |
| FIN-17 | Phase 46 | TBD |
