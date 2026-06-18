# Requirements — v2.1 Agenda Avançada

> Milestone goal: Adicionar notificações in-app de eventos próximos, suporte a eventos recorrentes e drag & drop no calendário.

---

## v2.1 Requirements

### Notificações In-App

- [ ] **AGE-01**: O header da aplicação exibe um badge com a contagem de eventos e prazos nos próximos 7 dias (apenas não concluídos)
- [ ] **AGE-02**: Ao clicar no badge, abre um painel/dropdown que lista os eventos e prazos próximos com data, título e link para o detalhe do processo

### Recorrência de Eventos

- [x] **AGE-03**: Utilizador pode criar um evento com regra de recorrência: diária, semanal ou mensal, com data de fim obrigatória
- [x] **AGE-04**: O backend armazena a regra de recorrência e expande as instâncias ao listar eventos (`GET /eventos` inclui instâncias geradas dentro do intervalo pedido)
- [x] **AGE-05**: O calendário exibe as instâncias de eventos recorrentes nas datas corretas, distinguindo-as visualmente dos eventos normais (ícone ou badge)
- [x] **AGE-06**: Ao apagar um evento recorrente, o utilizador escolhe entre "Apagar esta instância" ou "Apagar toda a série"

### Drag & Drop no Calendário

- [ ] **AGE-07**: Utilizador pode arrastar um evento no calendário para outro dia do mesmo mês para mover a data
- [ ] **AGE-08**: Ao largar o evento num novo dia, a data é atualizada via `PUT /eventos/{id}` e o calendário atualiza imediatamente

---

## Future Requirements (Deferred)

- Notificações push / email — requer infraestrutura de backend adicional (SMTP, FCM)
- Recorrência sem data de fim (infinita) — requer paginação especial de instâncias
- Editar todas as instâncias futuras de uma série — padrão "edit from here" complexo
- Drag & drop entre meses — requer vista multi-mês ou navegação inline

---

## Out of Scope

- Email ou push notifications — apenas in-app neste milestone
- Recorrência infinita — obrigatório ter data de fim
- Editar instâncias futuras em bloco — apenas esta ou toda a série
- Drag & drop de prazos — prazos são ligados a fases de processo (data gerida pelo processo)
- Módulo financeiro avançado — entregue em v2.0

---

## Traceability

| REQ-ID | Phase | Plan |
|--------|-------|------|
| AGE-01 | Phase 47 | TBD |
| AGE-02 | Phase 47 | TBD |
| AGE-03 | Phase 48 | TBD |
| AGE-04 | Phase 48 | TBD |
| AGE-05 | Phase 48 | TBD |
| AGE-06 | Phase 48 | TBD |
| AGE-07 | Phase 49 | TBD |
| AGE-08 | Phase 49 | TBD |
