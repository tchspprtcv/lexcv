---
phase: 133-funda-o-fiscal
plan: 03
subsystem: backend-fiscal
tags: [fiscal, efatura, numeracao, concurrency, testcontainers, postgresql]
requires:
  - "133-01: SerieFiscal, SerieFiscalRepository (criarSeNaoExiste, bloquear, definirLockTimeoutLocal), AmbienteFiscal, TipoDocumentoFiscal, RecusaFiscalException, Clock bean"
provides:
  - "NumeracaoService.proximoNumero(UUID tenantId, TipoDocumentoFiscal tipo, AmbienteFiscal ambiente) -- @Transactional(MANDATORY)"
  - "record NumeroFiscalAtribuido(UUID serieId, String serieCodigo, int ano, long numero, LocalDate dataEmissao)"
  - "Error contract: RecusaFiscalException 503 SERIE_INDISPONIVEL when the series lock is not obtained within 5s"
affects:
  - "Phase 134 PagamentoFaturadoService (calls proximoNumero as the LAST lock of the payment transaction)"
  - "Phase 135 NotaCreditoService"
  - "Phase 138 platform emitter (tenantId is a parameter, never read from SecurityContext)"
tech-stack:
  added: []
  patterns:
    - "Gapless counter: transaction-local lock_timeout -> INSERT ON CONFLICT DO NOTHING -> SELECT FOR UPDATE -> increment via dirty checking in the caller's transaction"
    - "Fiscal year from injected Clock in Atlantic/Cape_Verde"
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/NumeroFiscalAtribuido.java
    - backend/src/main/java/com/lexcv/services/fiscal/NumeracaoService.java
    - backend/src/test/java/com/lexcv/services/fiscal/NumeracaoServiceTest.java
    - backend/src/test/java/com/lexcv/services/fiscal/NumeracaoServiceConcorrenciaIT.java
  modified: []
decisions:
  - "proximoNumero is Propagation.MANDATORY: number and document commit or roll back together; the series lock must be the last lock taken (conta corrente first, series last)"
  - "Lock-failure exceptions caught: Spring PessimisticLockingFailureException plus jakarta PessimisticLockException/LockTimeoutException -> 503 SERIE_INDISPONIVEL"
  - "Lock timeout covered on both paths: new series (B waits on the uncommitted unique-index tuple in INSERT ON CONFLICT) and existing series (B waits in SELECT FOR UPDATE)"
metrics:
  duration: "~20 min"
  completed: 2026-10-04
  tasks: 2
  files: 4
---

# Phase 133 Plan 03: Gapless Fiscal Numbering (NumeracaoService) Summary

`NumeracaoService.proximoNumero` gives each series (tenant, document type, Cabo Verde civil year, environment) consecutive numbers with no gaps and no duplicates. It does this with a row lock (`SELECT ... FOR UPDATE`) inside the caller's transaction (`MANDATORY`), after a 5s transaction-local `lock_timeout` and an `INSERT ... ON CONFLICT DO NOTHING`. Nine Testcontainers scenarios ran green against real PostgreSQL 16.

## What was built

- **`NumeroFiscalAtribuido`**: a record with `(serieId, serieCodigo, ano, numero, dataEmissao)`. Its javadoc says the number only becomes final when the caller commits. A rollback frees it.
- **`NumeracaoService`** (`@Service`, constructor `(SerieFiscalRepository, Clock)`), in this order:
  1. null-checks the three arguments;
  2. takes the date and year from `LocalDate.now(clock.withZone(Atlantic/Cape_Verde))`;
  3. builds the series code with `SerieFiscal.gerarCodigo`;
  4. calls `definirLockTimeoutLocal`, then `criarSeNaoExiste`, then `bloquear`;
  5. increments `ultimoNumero` (no save and no separate transaction).

  If the lock times out it throws `RecusaFiscalException(503, "SERIE_INDISPONIVEL", ...)` and logs only tenantId/tipo/ano. If the series is missing after the insert it throws `IllegalStateException`. The class javadoc explains why SEQUENCE, MAX()+1, JVM monitors and a separate transaction (REQUIRES_NEW) are ruled out. It also documents the lock order for Phase 134+, the UNIQUE safety net in Phase 134 and that the service never reads the SecurityContext.

## Verification

- `NumeracaoServiceTest` (Mockito): **8/8 pass**. Covers the MANDATORY annotation (checked by reflection), the call order via InOrder (lock timeout, then insert, then lock), 4 -> 5 with the counter updated, the year boundary (00:30 UTC on 1 Jan is 2025-12-31, 01:30 UTC is 2026), SERIE_INDISPONIVEL when the timeout hits either `bloquear` or `criarSeNaoExiste`, NPE on null arguments with no repository calls, and IllegalStateException when the series is missing.
- `NumeracaoServiceConcorrenciaIT`: **9/9 pass, actually run** with failsafe against `postgres:16-alpine` (Docker available; `~/.docker-java.properties` api.version=1.44), took 17.8 s:
  1. `concorrenciaMesmaSerie`: 8 concurrent transactions got 1..8, with 1 row and `ultimo_numero` = 8;
  2. `primeiroUsoConcorrente`: 8 concurrent first uses of a new series got 1..8, and 0 rows before became exactly 1 row after;
  3. `rollbackNaoDeixaLacuna`: rolled back 1, then got 1, then 2;
  4. `isolamentoEntreTenants`: 2 tenants with 4 interleaved threads each, each got 1..4;
  5. `reinicioAnual`: 2026 ended at 3 and `SIM-FR-2027` started at 1;
  6. `fronteiraDeAnoEmCaboVerde`: 2027-01-01T00:30Z gave 2026 / 2026-12-31 / `SIM-FR-2026`, and 01:30Z gave 2027;
  7. `foraDeTransacaoRecusa`: `IllegalTransactionStateException` and no series row created;
  8. `lockTimeoutFalhaRapido` (new series): B fails with SERIE_INDISPONIVEL in 4–15 s, and A's commit leaves `ultimo_numero` = 1;
  9. `lockTimeoutFalhaRapidoComSerieExistente`: the same check for the FOR UPDATE path.
- Full unit suite `mvn test`: 467 tests, 0 failures, 0 errors.
- `mvn -DskipTests compile spotbugs:check`: clean.
- Acceptance greps:
  - `Propagation.MANDATORY` and `SERIE_INDISPONIVEL` are present.
  - Outside comments there are 0 matches for `synchronized|REQUIRES_NEW|nextval|MAX\(|LocalDate\.now\(\)`.
  - No `SecurityContextHolder`.
  - The IT has 9 `@Test` (>= 8), contains `postgres:16-alpine`, `IllegalTransactionStateException`, `SERIE_INDISPONIVEL` and `2027-01-01T00:30:00Z`, has no `SpringBootTest`, and is 369 lines (>= 150).

CFG-05 is **fully delivered** (the concurrency proof ran green against real PostgreSQL) and is marked Complete in REQUIREMENTS.md.

## Commits

| Task | Commit | Message |
|------|--------|---------|
| 1 (RED) | 5ac1fc5 | test(133-03): add failing unit tests for NumeracaoService contract |
| 1 (GREEN) | 91a6065 | feat(133-03): NumeracaoService gapless fiscal numbering per series |
| 2 | 58259c0 | test(133-03): real-PostgreSQL concurrency IT for NumeracaoService |

## Deviations from Plan

**1. [Rule 2 - Missing coverage] Extra IT scenario `lockTimeoutFalhaRapidoComSerieExistente`**
- **Found during:** Task 2
- **Issue:** Scenario 8 as specified uses a new series. There, B blocks on the uncommitted unique-index tuple in `INSERT ... ON CONFLICT`, not in `SELECT ... FOR UPDATE`. That leaves the steady-state lock path (series already exists) unproven.
- **Fix:** Added a 9th scenario that commits one number first and then holds the FOR UPDATE lock. Both paths reach SERIE_INDISPONIVEL through the 5s `lock_timeout`.
- **Commit:** 58259c0

**2. [Plan wording] The IT javadoc avoids the literal `@SpringBootTest`** so the acceptance grep "SpringBootTest returns nothing" holds. The rationale is still there in words.

Otherwise the plan was executed as written. Scenario 1 commits number 1 sequentially first, so that its 7 concurrent callers compete purely on FOR UPDATE. Scenario 2 covers the concurrent first insert.

## TDD Gate Compliance

RED `test(133-03)` 5ac1fc5 (compile failure: the service did not exist yet), then GREEN `feat(133-03)` 91a6065. No refactor was needed.

## Known Stubs

None. No production consumer yet, by design (Phase 134 calls `proximoNumero` from the payment transaction).

## Self-Check: PASSED

- FOUND: backend/src/main/java/com/lexcv/services/fiscal/NumeroFiscalAtribuido.java
- FOUND: backend/src/main/java/com/lexcv/services/fiscal/NumeracaoService.java
- FOUND: backend/src/test/java/com/lexcv/services/fiscal/NumeracaoServiceTest.java
- FOUND: backend/src/test/java/com/lexcv/services/fiscal/NumeracaoServiceConcorrenciaIT.java
- FOUND commits: 5ac1fc5, 91a6065, 58259c0
