---
status: in-progress
phase: 135-nota-de-cr-dito
source: [135-13-PLAN.md Task 1, Task 2]
started: 2026-10-05T09:36:00Z
updated: 2026-10-05T09:42:00Z
---

# Phase 135: Gate and live end-to-end verification record

## Gate (Task 1)

All commands were run from a clean tree on 2026-10-05. Docker was available (`~/.docker-java.properties` api.version=1.44), so every Testcontainers IT ran against a real PostgreSQL 16.

| Gate | Result |
|------|--------|
| `mvn -Dmaven.compiler.release=21 verify` | BUILD SUCCESS. Surefire **1007** tests, 0 failures, 0 errors, 0 skipped. Failsafe **122** tests, 0 / 0 / 0. |
| `mvn -q -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check` | exit 0 |
| web `pnpm exec vitest run` | 12 files, **244** tests, all passed |
| web `pnpm exec tsc --noEmit` | clean |
| web `pnpm lint` | 0 errors (20 pre-existing warnings in unrelated files) |
| `pnpm verify:faturacao` / `pnpm verify:documentos-fiscais` | OK / OK |
| `pnpm build` (BACKEND_API_ORIGIN, NEXT_PUBLIC_API_BASE_PATH inline) | success; `/financeiro/documentos-fiscais` (static) and `/financeiro/documentos-fiscais/[id]` (dynamic) listed |
| `git diff --quiet HEAD -- web/package-lock.json` | unchanged |

**Fiscal ITs (failsafe, executed, 0 skipped):**

| IT | Tests |
|----|-------|
| MigracaoFiscal133IT | 8 |
| MigracaoFiscal134IT | 6 |
| MigracaoFiscal135IT | 4 |
| DocumentoFiscalRepositoryIT | 18 |
| PagamentoFaturadoServiceIT | 11 |
| PagamentoFaturadoConcorrenciaIT | 4 |
| GuardasDocumentoFiscalConcorrenciaIT | 11 |
| NotaCreditoServiceIT | 10 |
| NotaCreditoConcorrenciaIT | 3 |
