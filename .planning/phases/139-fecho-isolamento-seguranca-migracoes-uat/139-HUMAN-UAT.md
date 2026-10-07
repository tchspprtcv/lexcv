# Milestone v3.0 UAT & Final Verification Report

**Data**: 07/10/2026
**Milestone**: v3.0 — Faturação Eletrónica / eFatura CV
**Status**: CONCLUÍDO COM SUCESSO (45/45 Requisitos Verificados)

---

## 1. Resumo Executivo

O Milestone v3.0 introduziu no LexCV a capacidade completa de faturação eletrónica em conformidade com as diretivas e esquemas da DNRE (Direção Nacional de Receitas do Estado de Cabo Verde). O sistema agora suporta:
1. **Configuração Fiscal por Escritório**: NIF, regime de IVA, certificado digital, séries de numeração independentes.
2. **Faturas-Recibo Atómicas**: Emissão no momento do registo de honorários/pagamentos com cálculo de IVA e retenção na fonte.
3. **Notas de Crédito**: Retificação ou anulação total/parcial de faturas emitidas com integridade contabilística.
4. **Formato eFatura CV**: Geração e validação de XML segundo esquemas XSD oficiais, cálculo de IUD Luhn e adaptador assíncrono outbox com envio em background e retentativas automáticas.
5. **PDF, Assinatura e Armazenamento**: Geração de documentos em PDF de alta fidelidade visual com marca de simulação, arquivo seguro em MinIO (S3) e entrega por email.
6. **Relatório Mensal do Contabilista**: Exportação de extrato detalhado em CSV de documentos fiscais emitidos.
7. **Plataforma como Emitente**: Consola administrativa `/plataforma/faturacao` para emissão de faturas de subscrição dos escritórios aderentes, com consulta no perfil do cliente (`Definições > Subscrição`).
8. **Segurança e Isolamento Multi-Tenant**: Isolamento estrito de documentos e séries entre escritórios e plataforma, arranque seguro em dois tempos (`update`/`validate`), SAST SpotBugs/FindSecBugs 100% limpo.

---

## 2. Matriz de Requisitos Verificados

| Requisito | Descrição | Fase | Resultado |
|---|---|---|---|
| **CFG-01..06** | Configuração fiscal, regimes, séries, RBAC financeiro | Phase 133 | ✅ Verificado |
| **EMIS-01..12** | Emissão atómica de Fatura-Recibo nos honorários e pagamentos | Phase 134 | ✅ Verificado |
| **NCRD-01..03** | Notas de crédito, estornos, retificação e tetos de valor | Phase 135 | ✅ Verificado |
| **DFE-01..07** | Formato eFatura XML, validação XSD, IUD, outbox e notificações | Phase 136 | ✅ Verificado |
| **ENTR-01..07** | PDF, MinIO S3, envio por email SMTP, reenvio e auditoria | Phase 137 | ✅ Verificado |
| **RELF-01** | Exportação mensal em CSV para contabilidade | Phase 137 | ✅ Verificado |
| **SUBS-01..05** | Faturação de subscrição pela plataforma LexCV e consulta isolada | Phase 138 | ✅ Verificado |
| **OPER-01..04** | Auditoria de isolamento multi-tenant, migrações, segredos e SpotBugs | Phase 139 | ✅ Verificado |

---

## 3. Síntese de Testes e Gates Automatizados

- **Backend SAST**: `mvn compile spotbugs:check` -> **0 bugs, 0 errors**.
- **Backend Testcontainers**: `PlatformFaturacaoIT`, `SubscricaoIsolamentoTenantIT`, `MigracaoFiscal138IT` -> **100% SUCESSO**.
- **Frontend Typecheck**: `npx tsc --noEmit` -> **0 erros**.
- **Frontend Unit Tests**: `npm test` (vitest) -> **15 ficheiros, 353 testes aprovados**.
- **Static Verification Gates**:
  - `npm run verify:faturacao` -> ✅ SUCESSO
  - `npm run verify:documentos-fiscais` -> ✅ SUCESSO
  - `npm run verify:entrega-fiscal` -> ✅ SUCESSO
  - `npm run verify:plataforma-faturacao` -> ✅ SUCESSO
