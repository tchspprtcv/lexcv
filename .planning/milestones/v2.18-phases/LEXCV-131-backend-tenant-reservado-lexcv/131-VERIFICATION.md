---
phase: 131-backend-tenant-reservado-lexcv
verified: 2026-09-24
status: passed
score: 4/4 roadmap success criteria verified (automated evidence)
overrides_applied: 0
---

# Phase 131: Backend — Tenant Reservado LexCV Verification Report

**Phase Goal:** O tenant reservado da plataforma passa a chamar-se "LexCV" em todo o código
backend, com uma migração SQL documentada para bases de dados já provisionadas.
**Verified:** 2026-09-24
**Status:** passed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | `DatabaseSeeder`, `PublicController`, `AuthController`, `PlatformAdminController`, `Tenant`, `TenantRepository`, `MigracaoPapeisEscritorioService` referem "LexCV" | ✓ VERIFIED | `grep -rn "ALCv"` nos 7 ficheiros devolve zero resultados; `TENANT_RESERVADO` em `PlatformAdminController.java` e `MigracaoPapeisEscritorioService.java` confirmados como `"LexCV"` |
| 2 | Testes associados passam, `mvn test` limpo | ✓ VERIFIED | 6 ficheiros de teste alvo: 57/57 passam (surefire reports). Suite completa do backend: 432/432 passam, 0 falhas, 0 erros |
| 3 | Arranque com seed contra BD limpa cria o tenant reservado como "LexCV" | ✓ VERIFIED | Log de execução dos testes confirma: `Tenant reservada 'LexCV' (id=42e38c49-...) saltada -- nao recebe papeis de escritorio por construcao` — `DatabaseSeeder.seedTenantPlataforma()` cria a linha já como "LexCV" numa BD H2/mock de teste |
| 4 | Nova migração SQL renomeia a linha "ALCv" existente em BDs já provisionadas, documentada em `backend/migrations/README.md` | ✓ VERIFIED | `backend/migrations/131-rename-tenant-reservado-lexcv.sql` criado (`UPDATE t_tenant SET nome = 'LexCV' WHERE nome = 'ALCv'`, idempotente); `README.md` atualizado (Path A skip list, tabela Path B linha 18/18, re-run safety 8/18, "Known execution status" 10 pendentes) |

**Score:** 4/4 ROADMAP success criteria verified by grep, test run output, and file inspection.

### Additional Notes

- **Limitação de ambiente:** este container não tem JDK 23 instalado (nem acesso de rede a
  distribuições de JDK para o instalar — `api.adoptium.net` bloqueado pela política de rede do
  ambiente). `mvn test`/`spotbugs:check` correram com `-Dmaven.compiler.release=21` só na linha de
  comando; `pom.xml` não foi alterado e continua a declarar Java 23 (CONFIG-01/Phase 132 é o
  âmbito correto para qualquer alteração ao `pom.xml`). Isto não afeta a validade das alterações
  de código — são substituições de string literal, sem uso de nenhuma feature de linguagem
  específica de Java 23 — mas fica registado porque `mvn test` não correu com o JDK real declarado
  pelo projeto nesta sessão. Recomenda-se confirmar em CI (que usa JDK 23 real).
- O nome do método de teste `migrar_tenantReservadaALCv_ehSaltadaSemLerUtilizadoresNemInstanciarMoldes`
  foi renomeado para `migrar_tenantReservadaLexCV_...` para consistência, não é apenas o literal de
  string que mudou.

## Human Verification Required

Nenhuma — verificação por `mvn test`/`spotbugs:check` determinísticos e grep; nenhuma alteração
requer inspeção visual/browser (não há UI nesta fase).
