# Requirements: LexCV — v2.10 Notificações e Alertas

**Defined:** 2026-07-08
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

Requirements para esta milestone. Cada uma mapeia para uma fase do roadmap.

Numeração de `NOTF-` continuada a partir do histórico do projeto: `NOTF-01` (v1.2, genérico, nunca implementado) e `NOTF-05/06/07` (v2.6, notificações de parecer, deferidas por falta de backend genérico — exatamente o que esta milestone constrói). `NOTF-02/03/04` nunca foram usados.

### Notificações

- [ ] **NOTF-08**: Utilizador vê um contador de notificações não lidas no sino, atualizado por polling (30-60s)
- [ ] **NOTF-09**: Utilizador abre o sino e vê uma lista das notificações recentes, cada uma com link direto para a entidade relacionada
- [ ] **NOTF-10**: Utilizador marca uma notificação individual como lida
- [ ] **NOTF-11**: Utilizador marca todas as notificações como lidas de uma vez
- [ ] **NOTF-12**: Utilizador acede a uma página dedicada `/notificacoes` com histórico completo
- [ ] **NOTF-13**: Utilizador filtra o histórico de notificações por categoria e por estado lida/não-lida
- [ ] **NOTF-14**: Sistema dirige cada notificação apenas à entidade diretamente ligada (responsável do processo, advogado do parecer, equipa do cliente) mais ADMIN — nunca em massa por permissão de visualização
- [ ] **NOTF-15**: Responsável do processo é alertado quando o processo entra numa nova fase
- [ ] **NOTF-16**: Responsável do processo (ou equipa de advogados/administrativos do cliente, quando o documento não está ligado a nenhum processo) é alertado quando um novo documento é adicionado
- [ ] **NOTF-17**: Utilizador com permissão adequada pode reatribuir o responsável de um processo através de um novo fluxo dedicado (hoje só é definido na criação)
- [ ] **NOTF-18**: Utilizador é alertado quando é definido ou reatribuído como responsável de um processo
- [ ] **NOTF-19**: Advogado é alertado quando lhe é atribuído um parecer
- [ ] **NOTF-20**: Responsável do processo é alertado quando um prazo do processo muda de estado de risco (próximo/vencido), via verificação diária
- [ ] **NOTF-21**: Responsável do processo é alertado quando um evento de calendário crítico muda de estado de risco (próximo/vencido), via verificação diária
- [ ] **NOTF-22**: Dashboard, agenda e notificações mostram sempre o mesmo veredito de "prazo crítico" — lógica consolidada numa única fonte partilhada, substituindo as 4 implementações inconsistentes existentes
- [ ] **NOTF-23**: Responsável do processo é alertado quando o honorário do processo atinge N dias sem pagamento total desde a data do acordo, via verificação diária

## v2 Requirements

Reconhecidas mas fora do roadmap atual.

### Notificações

- **NOTF-24**: Preferências de notificação por utilizador (silenciar categorias específicas)
- **NOTF-25**: Notificar toda a equipa de advogados/administrativos atribuídos a um processo (não só o responsável único) quando eventos relevantes acontecem
- **NOTF-26**: Permitir adiar/silenciar (snooze) um lembrete de prazo recorrente sem desativar a categoria inteira

## Out of Scope

Excluídas explicitamente. Documentadas para prevenir scope creep.

| Feature | Reason |
|---------|--------|
| Notificações push / email externas | Mantém-se in-app apenas — decisão confirmada na v2.10; ver PROJECT.md Out of Scope |
| Novo campo de data de vencimento em `Honorario` | Alerta usa dias sem pagamento total desde `dataAcordo` em vez de uma data explícita — evita desenhar UI de preenchimento fora do pedido original |
| Notificar o responsável anterior quando um processo é reatribuído | Mantém o modelo "só o dono atual" consistente com NOTF-14; o histórico de reatribuição fica no AuditLog, não como notificação |
| WebSocket/SSE (tempo real) | Polling (30-60s) reaproveita o padrão TanStack Query já usado em toda a app — zero infraestrutura de push nova |

## Traceability

Preenchida durante a criação do roadmap.

| Requirement | Phase | Status |
|-------------|-------|--------|
| NOTF-08 | — | Pending |
| NOTF-09 | — | Pending |
| NOTF-10 | — | Pending |
| NOTF-11 | — | Pending |
| NOTF-12 | — | Pending |
| NOTF-13 | — | Pending |
| NOTF-14 | — | Pending |
| NOTF-15 | — | Pending |
| NOTF-16 | — | Pending |
| NOTF-17 | — | Pending |
| NOTF-18 | — | Pending |
| NOTF-19 | — | Pending |
| NOTF-20 | — | Pending |
| NOTF-21 | — | Pending |
| NOTF-22 | — | Pending |
| NOTF-23 | — | Pending |

**Coverage:**
- v1 requirements: 16 total
- Mapped to phases: 0 (aguarda roadmap)
- Unmapped: 16 ⚠️ (esperado antes da criação do roadmap)

---
*Requirements defined: 2026-07-08*
*Last updated: 2026-07-08 after initial definition*
