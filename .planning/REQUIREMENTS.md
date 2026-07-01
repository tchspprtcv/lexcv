# Requirements: LexCV — Milestone v2.6 Módulo de Parecer Jurídico — UI

**Defined:** 2026-07-01
**Core Value:** Permitir que uma instituição gerencie o ciclo completo de processos jurídicos num único painel, com isolamento rigoroso por tenant.

**Context:** v2.5 entregou a API completa do Módulo de Parecer Jurídico (12 endpoints `/api/v1/pareceres/*`), mas **backend-only** — nenhuma UI frontend foi construída (ver `.planning/v2.5-MILESTONE-AUDIT.md`). Esta milestone fecha esse gap: torna o ciclo solicitação → versionamento → entrega → pesquisa/auditoria utilizável através da aplicação LexCV. Não há novo trabalho de backend em âmbito — apenas consumo do que já existe.

## v1 Requirements (v2.6)

### Lista e Detalhe

- [ ] **PARC-11**: Utilizador pode ver lista de solicitações de parecer (`/pareceres`) com tabela/cards (dual-view), status badges e filtros (status, advogado, cliente/processo)
- [ ] **PARC-12**: Utilizador pode ver detalhe de uma solicitação, incluindo timeline/histórico imutável de versões (autor, data, conteúdo/anexo)
- [ ] **PARC-13**: Utilizador pode criar uma solicitação de parecer via formulário (vínculo a cliente, vínculo opcional a processo, atribuição de advogado responsável via user-picker)

### Elaboração e Versionamento

- [ ] **PARV-05**: Advogado responsável pode criar uma nova versão via formulário com resumo (campo `conteúdo` tratado como resumo textual, não o parecer completo) e **anexo obrigatório** (decisão desta milestone — mais restritivo que o backend, que trata o anexo como opcional)
- [ ] **PARV-06**: Upload de anexo de versão reutiliza o componente de upload já existente no módulo Documentos (progress bar, drag-and-drop, MinIO-backed)

### Entrega e Vista de Entregue

- [ ] **PARC-14**: Utilizador autorizado (advogado responsável ou ADMIN) pode marcar a entrega de uma versão como final, com diálogo de confirmação que enfatiza a irreversibilidade da ação
- [ ] **PARC-15**: Utilizador pode consultar uma vista dedicada "Parecer Entregue" (versão final referenciada por `versaoFinalId`, data/autor de entrega, anexo) — resolve o gap PARC-09 identificado no audit da v2.5, onde `versaoFinalId` existia apenas como campo bruto sem vista consumidora

### Pesquisa Avançada

- [ ] **PARS-03**: Utilizador pode pesquisar pareceres na UI combinando texto livre com filtros (cliente, advogado, status, data), espelhando o endpoint `pesquisar()` já construído no backend (v2.5/Phase 64)

### Notificações

- [ ] **NOTF-05**: Utilizador recebe notificação in-app (reaproveitando o sistema de notificações da v2.1) quando lhe é atribuído um parecer
- [ ] **NOTF-06**: Utilizador recebe notificação in-app quando uma nova versão é criada num parecer que acompanha (atribuído ou autor de solicitação)
- [ ] **NOTF-07**: Utilizador recebe notificação in-app quando um parecer é entregue

### RBAC

- [ ] **PARC-16**: Ações da UI (criar solicitação, criar versão, entregar) são visíveis/ativas apenas conforme `hasScopedPermission(perms, "pareceres", action)` em `web/src/lib/permissions.ts`, espelhando os `@PreAuthorize` do backend — incluindo o caso não-uniforme de `entregar`/nova versão exigirem também verificação de instância (ADMIN ou advogado responsável), que `hasScopedPermission` sozinho não expressa

## v2 Requirements (deferred to v2.7+)

### Aprovação Interna

- **PARC-17**: Ação de aprovação interna (ADMIN) na UI — backend já suporta (`pareceres:manage`), mas fora de âmbito nesta milestone (confirmado explicitamente); v2.6 cobre apenas criação de versão + entrega direta + vista de entregue

### Diferenciadores

- **PARV-07**: Diff/comparação entre versões (texto simples, linha-a-linha — não redline ao nível de cláusula)
- **PARV-08**: Editor de texto formatado (rich text) para o conteúdo da versão — depende de decisão de formato de armazenamento (Markdown vs. HTML) ainda não tomada

### Futuro Consideração (v3+)

- **PARC-18**: Ficha de parecer imprimível (seguindo o precedente da Ficha Cliente imprimível v2.4) — apenas se houver sinal real de necessidade do escritório
- **PARC-19**: Distinção visual "versão mais recente" vs. "versão entregue", caso possam divergir (requer confirmar invariante do backend)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Co-edição em tempo real de versões (estilo Google Docs) | Contradiz o modelo de versionamento sequencial e imutável do backend — exigiria um modelo de dados completamente diferente |
| Edição/eliminação de versões submetidas | Backend impõe imutabilidade por desenho (defensabilidade legal do histórico) |
| Reversão/undo da entrega | Backend modela a entrega como irreversível deliberadamente (não é uma omissão) |
| Diff ao nível de cláusula (redline, estilo CLM) | Custo de engenharia desproporcional para esta milestone — a prioridade é existir UI, não um comparador jurídico de nível Ironclad |
| Roteamento de aprovação externo / assinatura eletrónica | Aprovação do backend é interna/ADMIN-only; fora do âmbito atual (sem Keycloak, sem notificações por email — ver PROJECT.md) |
| Motor de workflow de aprovação configurável por tenant | Backend suporta exatamente um gate de aprovação opcional (ADMIN); não existe entidade para um motor configurável |
| Novo componente de upload/preview construído do zero | Reutilizar o componente existente do módulo Documentos evita duplicar lógica de progress/drag-and-drop já madura |

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PARC-11 | Phase 65 | Pending |
| PARC-12 | Phase 65 | Pending |
| PARC-13 | Phase 66 | Pending |
| NOTF-05 | Phase 66 | Pending |
| PARV-05 | Phase 67 | Pending |
| PARV-06 | Phase 67 | Pending |
| NOTF-06 | Phase 67 | Pending |
| PARC-14 | Phase 68 | Pending |
| PARC-15 | Phase 68 | Pending |
| PARC-16 | Phase 68 | Pending |
| NOTF-07 | Phase 68 | Pending |
| PARS-03 | Phase 69 | Pending |

**Coverage:**
- v1 requirements: 12 total
- Mapped to phases: 12
- Unmapped: 0 ✓

---
*Requirements defined: 2026-07-01*
*Last updated: 2026-07-01 after roadmap creation (5 phases, 65–69)*
</content>
