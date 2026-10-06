---
status: in-progress
phase: 136-formato-efatura-e-adaptador-simulado
source: [136-16-PLAN.md Task 1, Task 2, Task 3]
started: 2026-10-06T17:58:00Z
updated: 2026-10-06T18:03:00Z
---

# Phase 136: Gate, live end-to-end verification and primary-source gate record

## Gate (Task 1)

I ran every command from a clean tree on 2026-10-06. Docker was available (`~/.docker-java.properties` api.version=1.44), so every Testcontainers IT ran against a real PostgreSQL 16.

| Gate | Result |
|------|--------|
| `mvn -Dmaven.compiler.release=21 verify` | BUILD SUCCESS. Surefire **1207** tests, 0 failures, 0 errors, 0 skipped. Failsafe **159** tests, 0 / 0 / 0. |
| `mvn -q -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check` | exit 0 |
| `mvn -q -Dmaven.compiler.release=21 -DskipTests package` | exit 0; `backend-0.0.1-SNAPSHOT.jar` contains the 22 vendored `xsd/efatura/*.xsd` |
| web `pnpm exec vitest run` | 14 files, **318** tests, all passed |
| web `pnpm exec tsc --noEmit` | clean |
| web `pnpm lint` | 0 errors (20 pre-existing warnings in unrelated files) |
| `pnpm verify:faturacao` / `pnpm verify:documentos-fiscais` | OK / OK (the latter includes the Phase 136 checks: neutral badge, card, reprocess `financeiro:edit`, banner, no authorisation wording) |
| `pnpm build` (BACKEND_API_ORIGIN, NEXT_PUBLIC_API_BASE_PATH inline) | success; `/financeiro/documentos-fiscais` (static) and `/financeiro/documentos-fiscais/[id]` (dynamic) listed |
| `git diff --quiet HEAD -- web/package-lock.json` | unchanged |

**Fiscal ITs (failsafe, executed, 0 skipped):**

| IT | Tests |
|----|-------|
| MigracaoFiscal133IT | 8 |
| MigracaoFiscal134IT | 6 |
| MigracaoFiscal135IT | 4 |
| MigracaoFiscal136IT | 8 |
| DocumentoFiscalRepositoryIT | 18 |
| PagamentoFaturadoServiceIT | 11 |
| PagamentoFaturadoConcorrenciaIT | 4 |
| GuardasDocumentoFiscalConcorrenciaIT | 11 |
| NotaCreditoServiceIT | 11 |
| NotaCreditoConcorrenciaIT | 3 |
| FilaComunicacaoFiscalIT | 13 |
| ReprocessarComunicacaoIT | 8 |
| FiscalOutboxJobIT | 6 |
| FiscalOutboxJobFalhasForcadasIT (extra, from 136-15) | 1 |
