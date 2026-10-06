---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 06
subsystem: backend-fiscal-outbox
tags: [fiscal, efatura, outbox, postgresql, skip-locked, concurrency, multi-tenant]
requires: ["136-02"]
provides:
  - "FilaComunicacaoFiscal (@Repository class): reclamar(agora, leaseAte, lote) and registarResultado(id, tenantId, versao, estado, codigo, mensagem, proxima, concluido, agora), both Propagation.MANDATORY"
  - "ComunicacaoFiscalTransacoes (@Service): reclamar(lote, lease), carregarSnapshot(tenantId, documentoId) readOnly, gravarXml(...) insert-only, registarResultado(item, estado, codigo, mensagem, proxima)"
  - "ComunicacaoReclamada record (id, tenantId, documentoFiscalId, ambiente, tentativas, versao, reprocessamentos)"
  - "SnapshotComunicacao record (documento, linha, xmlExistente, iudOrigem, numeroFormatadoOrigem)"
affects: [136-13, 136-14, 136-15]
tech-stack:
  added: []
  patterns:
    - "Single-statement claim: CTE SELECT ... FOR UPDATE SKIP LOCKED + UPDATE ... FROM ... RETURNING, sorted by created_at,id in Java"
    - "Typed native parameters via NativeQuery.setParameter(name, value, StandardBasicTypes.X) so NULL Instants/Strings bind with a type"
key-files:
  created:
    - backend/src/main/java/com/lexcv/repositories/FilaComunicacaoFiscal.java
    - backend/src/main/java/com/lexcv/services/fiscal/ComunicacaoReclamada.java
    - backend/src/main/java/com/lexcv/services/fiscal/SnapshotComunicacao.java
    - backend/src/main/java/com/lexcv/services/fiscal/ComunicacaoFiscalTransacoes.java
    - backend/src/test/java/com/lexcv/services/fiscal/FilaComunicacaoFiscalIT.java
  modified: []
decisions:
  - "The claim is one statement (CTE with FOR UPDATE SKIP LOCKED feeding UPDATE ... RETURNING) instead of SELECT then UPDATE ... WHERE id IN (...): same locks and semantics, one round trip. RETURNING order is not guaranteed, so rows are sorted by created_at, id in Java"
  - "carregarSnapshot returns empty when the document or its line does not exist in the claimed row's tenant; the first line (numero_linha order) is the snapshot line"
  - "gravarXml returns the row that won for the document; empty only on an IUD collision with another document (caller treats as transient)"
  - "EntityManager via @PersistenceContext field (SerieFiscalRepositoryCustomImpl precedent) rather than constructor injection"
metrics:
  duration: "~30 min"
  completed: 2026-10-06
  tasks: 2
  files: 5
---

# Phase 136 Plan 06: Outbox DB side (claim, lease, versao guard) Summary

The fiscal communication queue now has its database side, and it is proven on real PostgreSQL.

- **Claiming rows:** `FilaComunicacaoFiscal` claims due `PENDENTE` rows across all tenants, oldest first. A NULL `proxima_tentativa_em` counts as due, so the backlog left by Phases 134 and 135 is picked up. The claim uses `FOR UPDATE SKIP LOCKED` and sets a lease, counts the attempt, stamps `ultima_tentativa_em` and bumps `versao`.
- **Recording results:** a result is written only while the row still has the claimed `versao` and belongs to the claimed tenant.
- **Short transactions:** `ComunicacaoFiscalTransacoes` holds each short transaction as a public method on its own bean. These are claim, tenant-scoped read-only snapshot, insert-only XML write and result recording. None of them builds XML or calls a gateway.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | FilaComunicacaoFiscal native SQL + records + ComunicacaoFiscalTransacoes | a6225ec |
| 2 | FilaComunicacaoFiscalIT on PostgreSQL | 31fe7b4 |

## Verification

**FilaComunicacaoFiscalIT:** 13 tests, 0 failures, 0 errors, 0 skipped. Testcontainers `postgres:16-alpine` was executed. The tests cover:
- **Claiming:** rows with a NULL next attempt are claimed oldest first. The values after the claim are checked by JDBC: tentativas 1, versao 1, `lease_ate` = now + 5 min, `ultima_tentativa_em` = now. The batch limit is respected.
- **Rows not claimed:** future, `ACEITE_SIMULADO`, `REJEITADO` and `ERRO` rows are never claimed. A row with `proxima` = now is claimed. The future row is claimed once the clock reaches it.
- **Concurrency:** two concurrent claims are disjoint. Thread A holds an open transaction with 3 rows while the main thread claims the other 3. Their union covers all 6, and a third claim returns nothing.
- **Lease and versao guard:**
  - A claimed row is not reclaimable inside the lease. One second after the lease ends, it is reclaimed with tentativas 2 and versao + 1.
  - The old versao then records 0 rows and the new one records 1.
- **Tenant guard:** a wrong tenantId records 0 rows.
- **Result columns:**
  - A terminal result sets `concluido_em`, clears `lease_ate` and truncates the message to 500 and the code to 64. That row is never reclaimed.
  - A `PENDENTE` result keeps `concluido_em` null, sets `proxima_tentativa_em`, and is reclaimed at that time.
- **Suspended tenant:** a row of a tenant with `t_tenant.ativo = false` is claimed (`suspensoNaoESaltado...`).
- **XML write:** calling `gravarXml` twice returns the first row both times (same IUD, sha, xml and `gerado_em`) and leaves one row. An IUD already used by another document returns empty.
- **Snapshot:**
  - An NC snapshot has an empty `iudOrigem` before the FR has XML and the FR's IUD afterwards, plus the FR's number.
  - An FR snapshot has no origin fields.
  - A snapshot read with another tenant, or for an unknown document, is empty.

**Other checks:**
- `mvn -DskipTests compile spotbugs:check` is clean.
- DocumentoFiscalImutabilidadeTest still passes.
- `ComunicacaoFiscalRepository.java` is unchanged.

**Acceptance greps in `FilaComunicacaoFiscal.java`:**
- `FOR UPDATE SKIP LOCKED` appears once.
- `proxima_tentativa_em IS NULL` appears once.
- `versao = :versao` appears once.
- `tenant_id = :tenantId` appears once.
- `Propagation.MANDATORY` appears twice.

## Deviations from Plan

**1. [Rule 1 - Robustness] Claim as a single CTE statement**
- **Found during:** Task 1.
- **Plan:** a SELECT ... FOR UPDATE SKIP LOCKED followed by `UPDATE ... WHERE id IN (:ids) RETURNING`.
- **Fix:** One `WITH devidas AS (SELECT ... FOR UPDATE SKIP LOCKED) UPDATE ... FROM devidas RETURNING ...`. Locking, ordering and lease semantics are the same, without a second round trip or an empty `IN ()` case. `created_at` is returned too, so the result list is sorted oldest first.
- **Commit:** a6225ec.

**2. [Rule 3 - Blocking] Typed native parameter binding**
- **Issue:** Hibernate binds untyped NULLs in native queries without a type, which PostgreSQL rejects for timestamptz.
- **Fix:** All parameters are bound with `StandardBasicTypes`.
- **Commit:** a6225ec.

Javadoc was also reworded so that the literal `FOR UPDATE SKIP LOCKED` and `Propagation.MANDATORY` appear only in the code, which keeps the acceptance greps exact.

## Notes for downstream plans

- **136-13 (processor):**
  1. `transacoes.reclamar(lote, lease)`.
  2. For each item, `carregarSnapshot(item.tenantId(), item.documentoFiscalId())`.
  3. Build and validate the XML outside any transaction.
  4. `gravarXml(...)`. If it returns empty, treat as transient.
  5. Call the gateway outside any transaction.
  6. `registarResultado(item, estado, codigoFixo, mensagemFixa, proxima)`. A return of 0 means the lease was lost: log it and do not notify.
- **136-14 (reprocess):** reset the row with its own tenant-scoped update. `FilaComunicacaoFiscal` does not provide one.

## Threat Flags

None. T-136-21 through T-136-24 are mitigated as planned:
- T-136-21: tenant-scoped after the claim, with a wrong-tenant IT.
- T-136-22: SKIP LOCKED, lease and versao, with a concurrency IT.
- T-136-23: attempt counted at claim time, expired lease reclaimable, NULL treated as due.
- T-136-24: truncation.

## Self-Check: PASSED

- FOUND: all 5 files listed in key-files
- FOUND: commits a6225ec, 31fe7b4
