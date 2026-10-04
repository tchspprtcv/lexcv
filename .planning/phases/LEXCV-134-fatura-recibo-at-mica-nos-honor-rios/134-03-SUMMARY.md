---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 03
subsystem: backend-fiscal
tags: [fiscal, efatura, repositories, immutable, tenant-isolation, locks, testcontainers]
requires:
  - "134-01: DocumentoFiscal/DocumentoFiscalLinha (@Immutable), ComunicacaoFiscal, uk on t_conta_corrente.cliente_id"
provides:
  - "DocumentoFiscalRepository (narrow Repository): save, findByIdAndTenantId, findByTenantIdAndChaveIdempotencia, findByTenantIdAndPagamentoIdIn, existsByTenantIdAnd{PagamentoId,ClienteId,ProcessoId,HonorarioId}, buscar(tenantId, clienteId String, tipo, estado, de, ate, Pageable)"
  - "DocumentoFiscalLinhaRepository: save, findByTenantIdAndDocumentoFiscalIdOrderByNumeroLinhaAsc"
  - "DocumentoFiscalLigacaoClienteRepository.repontarCliente(tenantId, antigo, novo) -- the only UPDATE on t_documento_fiscal"
  - "ComunicacaoFiscalRepository: save, findByTenantIdAndDocumentoFiscalId, findByTenantIdAndDocumentoFiscalIdIn"
  - "ContaCorrenteRepository.bloquearPorCliente + criarSeNaoExiste (ON CONFLICT (cliente_id) DO NOTHING)"
  - "ClienteRepository/ProcessoRepository.bloquearPorIdETenant; ProcessoRepository.clienteIdPorIdETenant; ConfiguracaoFiscalRepository.ativaPorTenant"
affects: [134-04, 134-05, 134-06, 134-08, 134-09, 134-10, 136]
tech-stack:
  added: []
  patterns:
    - "Narrow fiscal repositories (Repository marker only) with method sets pinned by reflection"
    - "Tenant-first native listing with CAST(:p AS text) IS NULL optional filters and LEFT JOIN to the communication satellite"
    - "Scalar pre-lock reads (clienteIdPorIdETenant, ativaPorTenant) so OSIV never holds an unlocked instance of a row that is locked later"
key-files:
  created:
    - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalRepository.java
    - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalLinhaRepository.java
    - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalLigacaoClienteRepository.java
    - backend/src/main/java/com/lexcv/repositories/ComunicacaoFiscalRepository.java
    - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalImutabilidadeTest.java
    - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalRepositoryIT.java
  modified:
    - backend/src/main/java/com/lexcv/repositories/ContaCorrenteRepository.java
    - backend/src/main/java/com/lexcv/repositories/ClienteRepository.java
    - backend/src/main/java/com/lexcv/repositories/ProcessoRepository.java
    - backend/src/main/java/com/lexcv/repositories/ConfiguracaoFiscalRepository.java
decisions:
  - "buscar binds clienteId as a String (CAST(CAST(:clienteId AS text) AS uuid)); callers pass clienteId.toString() or null"
  - "ContaCorrente has no tenant_id, so bloquearPorCliente is keyed by cliente only; the caller must already hold the tenant-scoped cliente lock (R-01 order)"
  - "The tenantId pin in DocumentoFiscalImutabilidadeTest accepts @Param(\"tenantId\"), a compiled parameter name, or a derived finder whose name contains TenantId with a UUID parameter"
metrics:
  duration: "~20 min"
  completed: 2026-10-04
  tasks: 2
  files: 10
---

# Phase 134 Plan 03: Fiscal data-access layer Summary

This plan adds the fiscal data-access layer. The repositories are narrow and tenant-first: a fiscal document or line can only be inserted and read. The one exception is the pinned native `repontarCliente` UPDATE, used by client merge. The plan also adds the R-01 row-lock finders on cliente, processo and conta corrente. Reflection/source pins prove the immutability and 13 Testcontainers tests prove the queries.

## What was built

- **Narrow repositories.** `DocumentoFiscalRepository`, `DocumentoFiscalLinhaRepository`, `DocumentoFiscalLigacaoClienteRepository` and `ComunicacaoFiscalRepository` extend only `Repository<T, ID>`. They have no `JpaRepository`/`CrudRepository`/`JpaSpecificationExecutor`, so `delete*` and bulk saves do not exist at compile time.
- **Listing (`buscar`).** A native `Page` query with a `countQuery`:
  - `tenant_id` is the first, non-optional predicate.
  - The filters for cliente, tipo, estado (via `LEFT JOIN t_comunicacao_fiscal` on document and tenant), `de` and `ate` are optional, using the `CAST(...) IS NULL OR` idiom.
  - Order is `data_emissao DESC, ano DESC, numero DESC`.
  - Every value is bound with `@Param`; nothing is concatenated.
- **`repontarCliente`.** The only write on `t_documento_fiscal` besides INSERT. It moves only `cliente_id`, scoped by tenant; the `adquirente_*` snapshot columns are never touched.
- **Lock finders (R-01: configuração → cliente → processo → conta corrente → série).**
  - `ClienteRepository.bloquearPorIdETenant` and `ProcessoRepository.bloquearPorIdETenant`
  - `ContaCorrenteRepository.bloquearPorCliente`, plus the race-free `criarSeNaoExiste`
  - Each javadoc states the order and the "first read of the row in the transaction" rule.
- **Scalar reads.** `ProcessoRepository.clienteIdPorIdETenant` and `ConfiguracaoFiscalRepository.ativaPorTenant` read a value without loading the entity before its lock.

## Verification

- `compile spotbugs:check` passes.
- `mvn -Dmaven.compiler.release=21 -Dtest=DocumentoFiscalImutabilidadeTest,AuditLogImutabilidadeTest -Dit.test=DocumentoFiscalRepositoryIT verify`:
  - `DocumentoFiscalImutabilidadeTest` 10/10, `AuditLogImutabilidadeTest` 6/6
  - `DocumentoFiscalRepositoryIT` 13/13 on `postgres:16-alpine`, 0 skipped
- **What the IT covers:**
  - cross-tenant `findByIdAndTenantId` returns empty, and `buscar` never returns another tenant's documents even when the cliente id is the same
  - each filter, both null and set; `estado` excludes documents with no communication row
  - inclusive `de`/`ate` bounds
  - pagination: 25 documents give 10 per page, a total of 25 and 3 pages; ordering is checked
  - a dirty change made by reflection and flushed leaves the row unchanged
  - `repontarCliente` moves only tenant A's documents and leaves the snapshot intact
  - `exists*` results are scoped by tenant
  - `criarSeNaoExiste` called twice creates one row
  - lock finders return empty for a foreign tenant
  - `ativaPorTenant` returns empty, false or true as expected
  - unique `(tenant_id, chave_idempotencia)` and global `pagamento_id` raise `DataIntegrityViolationException` on commit
- **Acceptance greps:** no `JpaRepository|CrudRepository|JpaSpecificationExecutor` in the fiscal repositories; 0 `@Modifying` in `DocumentoFiscalRepository` and `DocumentoFiscalLinhaRepository`; `countQuery` is present; no parameter concatenation; every required lock and scalar method name is present.

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | bf3c52e | Narrow fiscal repositories and R-01 lock finders |
| 2 | f4523bb | DocumentoFiscalImutabilidadeTest + DocumentoFiscalRepositoryIT |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Acceptance] Reworded javadoc that tripped the forbidden-base grep**
- **Found during:** Task 1
- **Issue:** The `DocumentoFiscalRepository` javadoc named `JpaRepository`/`CrudRepository`/`JpaSpecificationExecutor` to explain why they are not used. The acceptance grep must return nothing, even for comments.
- **Fix:** Reworded the comment so it keeps the meaning without naming the types. The fix was amended into the Task 1 commit before anything else was built on it.
- **Commit:** bf3c52e

## TDD Gate Compliance

Task 2 is marked `tdd="true"`, but its subject (the repositories) was built in Task 1, as the plan orders. So both test classes passed on their first run, and there is a single `test(134-03)` commit and no separate failing-test commit. The tests are regression pins and a proof against real PostgreSQL, not a driver for new code.

## Known Stubs

None.

## Self-Check: PASSED

- FOUND: all 10 key files
- FOUND: bf3c52e, f4523bb
