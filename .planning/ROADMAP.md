# Roadmap: v2.0 Módulo Financeiro

## Overview

Completa e corrige o módulo financeiro existente em quatro fases: primeiro alinha o data layer com a serialização Jackson e fecha os endpoints em falta no backend; depois adiciona lógica de status e KPIs no frontend; a seguir entrega filtros e as ações de edição/eliminação na UI; por último adiciona a exportação CSV. Cada fase é verificável de forma independente antes de avançar.

## Milestones

- 🚧 **v2.0 Módulo Financeiro** — Phases 43–46 (in progress)

## Phases

- [ ] **Phase 43: Data Layer + Backend Endpoints** - Migração camelCase e endpoints CRUD em falta
- [ ] **Phase 44: Status + KPIs** - Cálculo de estado do honorário e cards de resumo financeiros
- [ ] **Phase 45: Filtros + Edit/Delete UI** - Filtros na lista e ações de edição/eliminação
- [ ] **Phase 46: CSV Export** - Exportação da lista de honorários para CSV

## Phase Details

### Phase 43: Data Layer + Backend Endpoints
**Goal**: O contrato de dados entre frontend e backend está correto (camelCase) e o CRUD completo de honorários e pagamentos está disponível via API
**Depends on**: Nothing (first phase of milestone)
**Requirements**: FIN-01, FIN-02, FIN-03, FIN-04, FIN-05, FIN-06
**Success Criteria** (what must be TRUE):
  1. O frontend envia e recebe campos camelCase (`processoId`, `valorTotal`, `dataAcordo`, `honorarioId`, `valorPago`, `dataPagamento`) sem erros de serialização
  2. Os tipos TypeScript `Honorario`, `Pagamento`, `HonorarioCreateRequest`, `PagamentoCreateRequest` não contêm campos snake_case
  3. Utilizador pode consultar um honorário individual via `GET /honorarios/{id}` com tenant scoping correto
  4. Utilizador com `financeiro:edit` pode editar um honorário via `PUT /honorarios/{id}`
  5. Utilizador com `financeiro:manage` pode apagar um honorário (sem pagamentos) e um pagamento com reversão de saldo
**Plans**: TBD
**UI hint**: yes

### Phase 44: Status + KPIs
**Goal**: A página financeiro apresenta o estado calculado de cada honorário e um resumo financeiro em cards no topo
**Depends on**: Phase 43
**Requirements**: FIN-07, FIN-08, FIN-09, FIN-10
**Success Criteria** (what must be TRUE):
  1. Cada honorário na lista mostra um badge com estado: `Pendente`, `Parcialmente Pago` ou `Pago`, com cores distintas
  2. O estado é calculado corretamente: Pendente = 0 pagamentos; Parcialmente Pago = total pago < valorTotal; Pago = total pago >= valorTotal
  3. A página financeiro exibe quatro cards no topo: total faturado, total recebido, em dívida, receita do mês corrente
  4. Os valores dos cards são derivados dos dados já carregados — sem pedido HTTP adicional
**Plans**: TBD
**UI hint**: yes

### Phase 45: Filtros + Edit/Delete UI
**Goal**: O utilizador pode filtrar a lista de honorários e executar ações de edição e eliminação diretamente na UI
**Depends on**: Phase 44
**Requirements**: FIN-11, FIN-12, FIN-13, FIN-14, FIN-15, FIN-16
**Success Criteria** (what must be TRUE):
  1. Utilizador pode filtrar a lista de honorários por processo, por status e por intervalo de datas (dataAcordo de/até), individualmente ou em combinação
  2. Utilizador com `financeiro:edit` pode abrir um formulário de edição de honorário e guardar alterações
  3. Utilizador com `financeiro:manage` pode apagar um honorário com diálogo de confirmação; a ação falha se o honorário tiver pagamentos
  4. Utilizador com `financeiro:manage` pode apagar um pagamento com diálogo de confirmação; o saldo é revertido na conta corrente do cliente
**Plans**: TBD
**UI hint**: yes

### Phase 46: CSV Export
**Goal**: O utilizador pode exportar a lista de honorários (com filtros aplicados) para um ficheiro CSV
**Depends on**: Phase 45
**Requirements**: FIN-17
**Success Criteria** (what must be TRUE):
  1. Existe um botão "Exportar CSV" na página financeiro que gera e descarrega um ficheiro `.csv`
  2. O CSV contém os campos: id, processo, cliente, valorTotal, totalPago, estado, dataAcordo
  3. Quando filtros estão ativos, o CSV exporta apenas os honorários correspondentes aos filtros aplicados
**Plans**: TBD
**UI hint**: yes

## Progress

**Execution Order:** 43 → 44 → 45 → 46

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 43. Data Layer + Backend Endpoints | 0/TBD | Not started | - |
| 44. Status + KPIs | 0/TBD | Not started | - |
| 45. Filtros + Edit/Delete UI | 0/TBD | Not started | - |
| 46. CSV Export | 0/TBD | Not started | - |
