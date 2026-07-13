# Requirements: LexCV — Milestone v2.11

**Defined:** 2026-07-12
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos (cliente → processo → prazos → documentos → financeiro) num único painel, com isolamento rigoroso por tenant.

## v1 Requirements

Requisitos desta milestone (v2.11 — Auditoria Técnica e Notificações Avançadas). Cada um mapeia para exatamente uma fase do roadmap.

### SAST

- [x] **SAST-01**: Análise SpotBugs/FindSecBugs corre sem erros contra bytecode JDK 23 (versões já corrigidas e ficheiro de exclusões já presentes no working tree, não committed — falta verificar e commitar)

### TEST

- [x] **TEST-01**: Teste de integração (Testcontainers+PostgreSQL) cobre a query nativa `buscarPorFiltros` de `Notificacao` (Phase 86, risco de dialecto nunca verificado contra Postgres real)
- [x] **TEST-02**: Teste de integração cobre o lock de concorrência de `numeroVersao` em `ParecerVersao` (Phase 87, risco de concorrência nunca verificado)
- [x] **TEST-03**: Decisão registada e aplicada sobre se o CI (`.github/workflows/deploy.yml`) passa a correr `mvn test`/`spotbugs:check`

### AGD

- [x] **AGD-34**: Prazos na Agenda usam o campo `risco` já calculado pelo backend (`RiscoPrazoService`) em vez de recomputar um veredito próprio no cliente
- [x] **AGD-35**: Eventos na Agenda refletem o mesmo veredito de risco que o resto do sistema (decisão explícita + implementação — `GET /eventos` não devolve `risco` hoje)

### NOTF

- [ ] **NOTF-24**: Utilizador pode silenciar categorias de notificação específicas para si próprio, exceto categorias críticas não-silenciáveis (mínimo: `PRAZO_VENCIDO`)
- [ ] **NOTF-25**: Notificações de eventos do processo (fase, documento, atribuição) chegam a toda a equipa (advogados/administrativos do cliente), não só ao responsável único (`responsavelId`)
- [ ] **NOTF-26**: Utilizador pode adiar (snooze) um lembrete de prazo por um período pré-definido, reaparecendo automaticamente depois do período
- [ ] **NOTF-27**: Corrigir bug pré-existente — notificar um destinatário que é simultaneamente membro de equipa e ADMIN não deve falhar (500) por colisão do constraint `uk_notificacao_dedup`

### AUD

- [ ] **AUD-01**: Auditoria de isolamento de tenant nas novas superfícies desta milestone (preferências de notificação, resolução de equipa, snooze)
- [ ] **AUD-02**: Fecho das UAT/verificações ao vivo pendentes nas fases 75, 76, 79, 81, 82, 84, 85, 89
- [ ] **AUD-03**: Fecho de dívidas menores conhecidas (labels de enum `DocumentoTipo` não traduzidas, testes de validação de NIF ausentes)
- [ ] **AUD-04**: Resolução ou contorno documentado do bloqueio ambiental `MINIO_ENDPOINT` que impediu verificação ao vivo em milestones anteriores
- [ ] **AUD-05**: Nova auditoria ao código para descobrir gaps técnicas ainda não documentadas em `STATE.md`/`PROJECT.md`

## v2 Requirements

Nenhum item novo diferido nesta milestone — os únicos candidatos v2 conhecidos (NOTF-24/25/26) foram promovidos a v1 acima, por decisão explícita do utilizador.

## Out of Scope

| Feature | Reason |
|---------|--------|
| Extensão do fan-out de equipa (NOTF-25) às categorias do job diário (`PRAZO_*`, `EVENTO_*`, `HONORARIO_ATRASADO`) | Pesquisa (ARCHITECTURE.md/PITFALLS.md) identificou como decisão em aberto, não uma exigência confirmada — decisão explícita fica para a fase de planeamento/discussão do NOTF-25, podendo ser promovida se aprovada nessa altura |
| Nova tabela `ProcessoEquipa` independente da equipa do cliente | Pesquisa não encontrou evidência de que a equipa de um processo precise divergir da equipa do seu cliente; NOTF-25 reutiliza `ClienteAdvogado`/`ClienteAdministrativo` transitivamente via `Processo.clienteId` |
| Matriz de preferências categoria×canal (NOTF-24) | Prematuro com um único canal de entrega (in-app, polling); só revisitar se email/push for adicionado no futuro |
| Teste HTTP end-to-end completo de RBAC/tenant (além dos 2 riscos nomeados TEST-01/02) | Exigiria uma fonte de propriedades de stub dedicada para contornar o bloqueio `MINIO_ENDPOINT`; não necessário para os 2 riscos concretamente identificados nesta milestone |
| Correção retroativa do mismatch camelCase/snake_case pré-existente em campos como `tenantId`/`createdAt` | Fora do âmbito desta milestone (dívida técnica já reconhecida e deliberadamente isolada desde v2.4) |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| SAST-01 | Phase 90 | Complete |
| TEST-01 | Phase 91 | Complete |
| TEST-02 | Phase 91 | Complete |
| TEST-03 | Phase 91 | Complete |
| AGD-34 | Phase 92 | Complete |
| AGD-35 | Phase 92 | Complete |
| NOTF-24 | Phase 93 | Pending |
| NOTF-27 | Phase 94 | Pending |
| NOTF-25 | Phase 95 | Pending |
| NOTF-26 | Phase 96 | Pending |
| AUD-01 | Phase 97 | Pending |
| AUD-02 | Phase 97 | Pending |
| AUD-03 | Phase 97 | Pending |
| AUD-04 | Phase 97 | Pending |
| AUD-05 | Phase 97 | Pending |

**Coverage:**
- v1 requirements: 15 total
- Mapped to phases: 15/15 ✓
- Unmapped: 0

**Phase order note:** Phases 90 (SAST), 91 (Testcontainers) and 92 (Agenda) have no file overlap with each other or with the notification track and are mutually parallelizable. Phases 93→94→95→96 (NOTF-24 → NOTF-27 → NOTF-25 → NOTF-26) are a hard sequential chain — all collide on `NotificacaoService.java` and its test file. Phase 97 (cross-cutting audit) runs last, after all other phases, per this project's established retrospective pattern.

---
*Requirements defined: 2026-07-12*
*Last updated: 2026-07-12 after roadmap creation (milestone v2.11) — 8 phases (90–97), 100% coverage*
