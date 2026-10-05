---
phase: 135-nota-de-cr-dito
plan: 01
subsystem: backend-fiscal
tags: [fiscal, efatura, nota-de-credito, jpa, immutable, testcontainers]
requires:
  - "Phase 134: DocumentoFiscal (@Immutable), DocumentoFiscalRepository (narrow, pinned), DocumentoFiscalRepositoryIT harness"
provides:
  - "MotivoNotaCredito { ANULACAO_TOTAL, CORRECAO_VALOR, ERRO_DADOS_CLIENTE, OUTRO } with rotulo()/porNome() + MotivoNotaCreditoConverter (varchar, no CHECK)"
  - "DocumentoFiscal.documentoOrigemId / motivoCodigo / motivoTexto (all updatable=false) + idx_documento_fiscal_tenant_origem"
  - "DocumentoFiscalRepository: findByTenantIdAndDocumentoOrigemIdOrderByDataEmissaoDescNumeroDesc, findByTenantIdAndIdIn, existsByTenantIdAndPagamentoIdAndTipo"
affects: [135-02, 135-03, 135-04, 135-05, 135-06, 136]
tech-stack:
  added: []
  patterns:
    - "NC reuses pagamento_id for its own estorno Pagamento: NOT NULL + UNIQUE kept, estorno cannot be re-invoiced"
key-files:
  created:
    - backend/src/main/java/com/lexcv/models/MotivoNotaCredito.java
    - backend/src/main/java/com/lexcv/models/MotivoNotaCreditoConverter.java
    - backend/src/test/java/com/lexcv/models/MotivoNotaCreditoTest.java
  modified:
    - backend/src/main/java/com/lexcv/models/DocumentoFiscal.java
    - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalRepository.java
    - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalImutabilidadeTest.java
    - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalRepositoryIT.java
decisions:
  - "No pagamento_estorno_id column: an NC's pagamento_id is its own negative estorno payment (design resolution in plan 01/CONTEXT)"
  - "IT NC fixture is a thin nc(...) wrapper over the existing doc(...) builder (extends the helper instead of forking it)"
metrics:
  duration: "~12 min"
  completed: 2026-10-05
  tasks: 2
  files: 7
---

# Phase 135 Plan 01: NC persistence foundation Summary

This plan adds the `MotivoNotaCredito` enum (stored as varchar through a converter), three immutable NC columns plus an index on `DocumentoFiscal`, and three tenant-scoped NC finders on the narrow repository. Repository ITs run against real PostgreSQL.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | MotivoNotaCredito enum, converter and test | b6f50e6 (RED), a0c32d3 (GREEN) |
| 2 | DocumentoFiscal NC columns, repository finders, pinned tests and repository IT | c9f93c3 |

## Verification

- `MotivoNotaCreditoTest`: 9 tests, `MetodoPagamentoTest`: 10 tests. Both green.
- `DocumentoFiscalImutabilidadeTest`: 10 tests, green. The pinned set now has the three new names; the check itself was not relaxed. `AuditLogImutabilidadeTest`: 6 tests, green.
- `DocumentoFiscalRepositoryIT` (failsafe, Testcontainers actually ran): 18 run, 0 failures, 0 errors, 0 skipped. The 5 new cases cover:
  - NCs by origem: newest first, tenant-isolated
  - `findByTenantIdAndIdIn`: tenant-isolated
  - estorno probe: checks both tipo and tenant
  - motivo: stored as the enum name and read back as the enum; FR rows keep NULL
  - a second document on an estorno's `pagamento_id` fails on `uk_documento_fiscal_pagamento`
- Acceptance greps:
  - the `pagamento_id` column is unchanged
  - there is no `pagamento_estorno_id`
  - `motivo_texto` has length 200
  - there are no setters

## Deviations from Plan

None. The plan was executed as written.

## Known Stubs

None.

## Self-Check: PASSED

- All created and modified files are present.
- Commits b6f50e6, a0c32d3 and c9f93c3 are present in `git log`.
