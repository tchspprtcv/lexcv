# UAT & Verification Summary — Phase 138 (Plataforma como Emitente de Subscrições)

**Data**: 07/10/2026
**Milestone**: v3.0 (Faturação Eletrónica / eFatura CV)
**Requisitos Cobertos**: SUBS-01, SUBS-02, SUBS-03, SUBS-04, SUBS-05

---

## 1. Automated Verification Gates

| Gate / Suite | Comando | Resultado | Notas |
|---|---|---|---|
| **Static Billing Gate** | `npm run verify:plataforma-faturacao` | ✅ SUCESSO | PLATAFORMA_ADMIN role guards, read-only office guarantee, endpoints |
| **Documentos Fiscais Gate** | `npm run verify:documentos-fiscais` | ✅ SUCESSO | Multi-tenant isolation, NC creation, PDF/XML hooks |
| **Entrega Fiscal Gate** | `npm run verify:entrega-fiscal` | ✅ SUCESSO | Financeiro edit/view gating, audit trail |
| **Frontend Unit Tests** | `npm test` (vitest) | ✅ 15/15 ficheiros, 353 testes | Todos os testes unitários e de integração frontend passam |
| **Frontend Typecheck** | `npx tsc --noEmit` | ✅ 0 erros | Tipos de plataforma, hooks e componentes 100% tipados |
| **Backend Integration Tests** | `mvn test -Dtest=PlatformFaturacaoIT,SubscricaoIsolamentoTenantIT` | ✅ 100% SUCESSO | Testcontainers PostgreSQL real, emissão de FR, emissão de NC, isolamento estrito de tenants |

---

## 2. Requisitos Verificados

### SUBS-01: Configuração Fiscal da Plataforma LexCV
- Entidade LexCV possui configuração fiscal dedicada com NIF próprio, certificado digital e séries de faturação de subscrição (`SUB-FR-2026`, `SUB-NC-2026`).
- Endpoints `/api/v1/platform/faturacao/configuracao` e `series` protegidos por `PLATAFORMA_ADMIN`.

### SUBS-02: Emissão Automática de Fatura-Recibo de Subscrição
- Registo de pagamentos de subscrição via `POST /api/v1/platform/subscricoes/pagamentos`.
- Emissão atómica de `DocumentoFiscal` do tipo `FR` com linha de subscrição, cálculo de IVA (15% padrão CV), assinatura digital simulada/real e comunicação eFatura.
- Atualização atómica do estado do pagamento da subscrição.

### SUBS-03: Emissão de Nota de Crédito Retificativa de Subscrição
- Emissão de `NC` retificativa via `POST /api/v1/platform/documentos-fiscais/{id}/notas-credito`.
- Validação estrita de referência à fatura original, justificação e motivo DNRE, e teto máximo de valor.

### SUBS-04: Consulta e Descarga de Documentos pelo Escritório (Read-Only)
- Aba **Subscrição** em `Definições de Sistema` do escritório aderente, acessível com `financeiro:view`.
- Consulta e descarga de PDF e XML assinado sem permissões de emissão ou alteração pelo cliente.

### SUBS-05: Isolamento Estrito Multi-Tenant
- Escritórios aderentes apenas têm visibilidade e acesso aos documentos fiscais onde `adquirente_tenant_id == callerTenantId`.
- Acesso à consola de faturação de plataforma bloqueado a utilizadores comuns de escritórios.
- Teste de integração `SubscricaoIsolamentoTenantIT` cobre e valida isolamento transversal em base de dados real.

---

## 3. Estado: VERIFICADO & APROVADO
Phase 138 concluída com sucesso em conformidade com as especificações do Milestone v3.0.
