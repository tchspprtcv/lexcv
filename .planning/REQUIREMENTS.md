# Requirements: LexCV

**Defined:** 2026-06-21
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.

## v2.3 Requirements

### Layout & Navegação

- [x] **NAV-01**: Utilizador pode abrir/fechar sidebar via botão hambúrguer em mobile (drawer overlay)
- [x] **NAV-02**: Sidebar fecha automaticamente ao navegar para outra página em mobile
- [x] **NAV-03**: Top bar em mobile mostra apenas botão de menu, nome da instituição e ações essenciais (notificações, perfil)
- [x] **NAV-04**: Bottom navigation bar disponível em mobile com acesso rápido aos 5 módulos principais

### Conteúdo — Tabelas e Listas

- [x] **TAB-01**: Listas simples (clientes, documentos, financeiro, agenda) mostram cards empilhados em mobile
- [x] **TAB-02**: Tabelas complexas (partes do processo, movimentações, fases) têm scroll horizontal em mobile

### Formulários e Modais

- [x] **FORM-01**: Formulários fluem em coluna única (100% largura) em mobile
- [x] **FORM-02**: Dialogs/modais abrem como bottom-sheet ou full-screen em mobile
- [x] **FORM-03**: Todos os inputs e botões têm altura mínima de 48px (touch target)

### Dashboard

- [x] **DASH-01**: KPI cards do dashboard adaptam-se a grid 1 coluna em mobile, 2 em tablet, 4 em desktop

### Agenda / Calendário

- [x] **CAL-01**: Calendário mostra vista diária por defeito em mobile, com navegação entre dias; vistas semanal/mensal disponíveis em tablet/desktop

## Futuras (v3+)

### Notificações e Offline

- **NOTIF-01**: Notificações push nativas em mobile (PWA)
- **OFFLINE-01**: Modo offline com cache de dados recentes

## Out of Scope

| Feature | Reason |
|---------|--------|
| App nativa iOS/Android | Web/PWA primeiro; nativo numa fase muito posterior |
| PWA (service worker, installable) | Requer trabalho adicional de infra; responsividade é prioridade |
| Gestos swipe avançados | Complexidade elevada; não é bloqueante para usabilidade |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| NAV-01 | Phase 53 | Complete |
| NAV-02 | Phase 53 | Complete |
| NAV-03 | Phase 53 | Complete |
| NAV-04 | Phase 53 | Complete |
| TAB-01 | Phase 54 | Complete |
| TAB-02 | Phase 54 | Complete |
| FORM-01 | Phase 55 | Complete |
| FORM-02 | Phase 55 | Complete |
| FORM-03 | Phase 55 | Complete |
| DASH-01 | Phase 56 | Complete |
| CAL-01 | Phase 56 | Complete |

**Coverage:**
- v2.3 requirements: 11 total
- Mapped to phases: 11 (100%)
- Unmapped: 0

---
*Requirements defined: 2026-06-21*
*Last updated: 2026-06-21 — traceability filled (Phases 53–56)*
