# Requirements: LexCV — Milestone v2.5 Módulo de Parecer Jurídico

Adaptado de `Especificacao_Tecnica_Modulo_Parecer_Juridico.docx`, reutilizando entidades e padrões já existentes no LexCV: `Cliente` (t_cliente), `User`+role `ADVOGADO` (t_user/t_role), `AuditLog` (t_audit_log), `StorageService`/`Documento` (padrão de anexos versionados), `Processo` (t_processo, vínculo opcional).

## v2.5 Requirements

### Solicitação

- [ ] **PARC-01**: Utilizador pode criar uma solicitação de parecer (cliente, descrição, data, prazo desejado, urgência)
- [ ] **PARC-02**: Solicitação pode ser associada opcionalmente a um Processo existente
- [ ] **PARC-03**: Utilizador pode atribuir (ou reatribuir) um advogado responsável (User com role ADVOGADO) à solicitação
- [ ] **PARC-04**: Solicitação tem status (PENDENTE, EM_ELABORACAO, EM_REVISAO, CONCLUIDO)
- [ ] **PARC-05**: Utilizador pode listar e filtrar solicitações (por cliente, advogado, status)
- [ ] **PARC-06**: Utilizador pode ver detalhe de uma solicitação com todas as suas versões

### Elaboração e Versionamento

- [ ] **PARV-01**: Advogado responsável pode criar nova versão do parecer (conteúdo + anexo opcional)
- [ ] **PARV-02**: Cada versão regista número sequencial, autor e data de criação
- [ ] **PARV-03**: Utilizador pode consultar e comparar versões anteriores do mesmo parecer
- [ ] **PARV-04**: Anexo de versão reutiliza StorageService (mesmo padrão de Documentos)

### Aprovação e Entrega

- [ ] **PARC-07**: Supervisor/ADMIN pode marcar uma versão como aprovada internamente (passo opcional antes da entrega)
- [ ] **PARC-08**: Utilizador pode marcar a versão final como entregue, concluindo a solicitação
- [ ] **PARC-09**: Parecer entregue fica disponível para consulta/download pelo cliente/equipa

### Auditoria e Pesquisa

- [ ] **PARA-01**: Todas as ações relevantes (criar, atribuir, editar versão, aprovar, entregar) geram registo em `AuditLog` existente (`entidadeTipo`: `parecer_solicitacao`/`parecer_versao`)
- [ ] **PARS-01**: Utilizador pode pesquisar pareceres por texto livre no conteúdo
- [ ] **PARS-02**: Pesquisa combina texto livre com filtros (cliente, advogado, status, data)

### RBAC

- [ ] **PARC-10**: Novo scope `pareceres:view/create/edit/manage` seeded em `Permission`/`DatabaseSeeder`, aplicado via `@PreAuthorize`, e espelhado em `web/src/lib/permissions.ts`

## Future Requirements (deferred)

- Notificações por e-mail ao cliente quando o parecer é entregue — fora de escopo (PROJECT.md já exclui notificações push/email neste ciclo de milestones; apenas in-app se necessário)
- Comparação visual (diff) entre versões do parecer — pode ser adicionado depois se houver necessidade real
- Indexação full-text dedicada (ex.: motor de busca externo) — pesquisa v1 usa busca textual nativa da BD (Postgres `ILIKE`/`tsvector`), evolução para motor especializado fica para milestone futura se o volume justificar

## Out of Scope

- Criar novas entidades Cliente/Advogado/Auditoria duplicadas — o documento genérico da especificação propunha isto, mas o LexCV já tem `Cliente`, `User`+`Role`, e `AuditLog`; reutilizar evita deriva de dados e duplicação de RBAC
- Gestão genérica de clientes, faturamento, prazos judiciais não ligados a pareceres — confirmado fora de escopo pela spec original
- Fluxo de aprovação multi-nível / workflow configurável — aprovação interna é um passo simples (aprovado/não aprovado por um único supervisor), não um motor de workflow

## Traceability

| Requirement | Phase | Status |
|-------------|-------|--------|
| PARC-01 | Phase 61 | Pending |
| PARC-02 | Phase 61 | Pending |
| PARC-03 | Phase 61 | Pending |
| PARC-04 | Phase 61 | Pending |
| PARC-05 | Phase 61 | Pending |
| PARC-06 | Phase 61 | Pending |
| PARC-10 | Phase 61 | Pending |
| PARV-01 | Phase 62 | Pending |
| PARV-02 | Phase 62 | Pending |
| PARV-03 | Phase 62 | Pending |
| PARV-04 | Phase 62 | Pending |
| PARC-07 | Phase 63 | Pending |
| PARC-08 | Phase 63 | Pending |
| PARC-09 | Phase 63 | Pending |
| PARA-01 | Phase 64 | Pending |
| PARS-01 | Phase 64 | Pending |
| PARS-02 | Phase 64 | Pending |

**Coverage:** 17/17 v2.5 requirements mapped ✓
