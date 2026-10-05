---
phase: 136-formato-efatura-e-adaptador-simulado
plan: 02
subsystem: backend-persistence
tags: [fiscal, efatura, outbox, migration, check-constraint, immutability, testcontainers, tdd]
requires: []
provides:
  - "EstadoComunicacaoFiscal: PENDENTE, ACEITE_SIMULADO, REJEITADO, ERRO + reprocessavel()/terminal()"
  - "ComunicacaoFiscal outbox fields: leaseAte, ultimaTentativaEm, ultimoErro(500), ultimoErroCodigo(64), concluidoEm, reprocessamentos (NOT NULL DEFAULT 0)"
  - "CHECK ck_comunicacao_fiscal_autorizado_producao (estado <> 'AUTORIZADO' OR ambiente = 'PRODUCAO') in entity (@Check) and script"
  - "Index idx_comunicacao_fiscal_estado_proxima (estado, proxima_tentativa_em)"
  - "DocumentoFiscalXml (@Immutable, t_documento_fiscal_xml) + DocumentoFiscalXmlRepository.inserirSeAusente / findByTenantIdAndDocumentoFiscalId"
  - "backend/migrations/136-efatura-comunicacao.sql (idempotent) + README row 22"
affects: [136-06, 136-07, 136-13, 136-14, 136-15]
tech-stack:
  added: []
  patterns:
    - "DB-level prohibition of a state with no Java constant (CHECK only; no AUTORIZADO/PRODUCAO enum values)"
    - "Insert-only satellite via native INSERT ... ON CONFLICT DO NOTHING without conflict target (covers both unique keys)"
key-files:
  created:
    - backend/src/main/java/com/lexcv/models/DocumentoFiscalXml.java
    - backend/src/main/java/com/lexcv/repositories/DocumentoFiscalXmlRepository.java
    - backend/migrations/136-efatura-comunicacao.sql
    - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal136IT.java
  modified:
    - backend/src/main/java/com/lexcv/models/EstadoComunicacaoFiscal.java
    - backend/src/main/java/com/lexcv/models/ComunicacaoFiscal.java
    - backend/src/test/java/com/lexcv/repositories/DocumentoFiscalImutabilidadeTest.java
    - backend/migrations/README.md
    - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal134IT.java
    - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal135IT.java
decisions:
  - "inserirSeAusente is @Modifying + @Transactional (REQUIRED), same idiom as NotificacaoRepository.inserirSeNaoDuplicado: it joins the caller's transaction or opens a short one"
  - "Script 136 header and README row state that even on ddl-auto=update the CHECK reaches an existing t_comunicacao_fiscal only through script 136 (Hibernate creates CHECKs only with the table)"
  - "MigracaoFiscal134IT's CHECK test was renamed to unicoCheckEOAutorizadoProducaoSemCheckNasColunasDeEnum and now asserts, in public and the script schema, that the only CHECK across the three tables is ck_comunicacao_fiscal_autorizado_producao"
metrics:
  duration: "~40 min"
  completed: 2026-10-05
  tasks: 2
  files: 10
---

# Phase 136 Plan 02: Outbox schema, AUTORIZADO⇒PRODUCAO CHECK and XML satellite Summary

The schema for the Phase 136 outbox is in place, both in the entities and in an idempotent manual script.

- **`t_comunicacao_fiscal`** has new columns for the lease, the last attempt, the last sanitised error and its code, the terminal timestamp and the reprocess counter. It also has a new index on `(estado, proxima_tentativa_em)`.
- **`ck_comunicacao_fiscal_autorizado_producao`** is a CHECK constraint. PostgreSQL itself refuses `AUTORIZADO` on any row whose `ambiente` is not `PRODUCAO` (SQLState 23514). Neither value exists in Java.
- **`t_documento_fiscal_xml`** is a new insert-only table: at most one XML row per document, with a unique 45-character IUD. Its repository can only insert-if-absent and read by tenant.

## Tasks

| Task | Name | Commits |
|------|------|---------|
| 1 | New states, outbox columns + CHECK, insert-only DocumentoFiscalXml (pinned) | e823884 (RED), c949e90 (GREEN) |
| 2 | Idempotent migration 136 + README row, 134/135/136 parity ITs | 3b08a87 |

## Verification

**DocumentoFiscalImutabilidadeTest:** 12 tests, green. Two are new:
- `documentoFiscalXmlRepositoryEInsertOnly`: exact method set; native, `@Modifying` INSERT ending in `ON CONFLICT DO NOTHING`, with no `DO UPDATE` and no delete.
- `estadosDeComunicacaoSaoExatamenteOsDoV30`: exact `values()`; `AmbienteFiscal` is still SIMULADO only; checks the `reprocessavel` and `terminal` sets.

`DocumentoFiscalXml` was added to the `@Immutable`/no-setter check, and `DocumentoFiscalXmlRepository` to the narrow-repository, forbidden-prefix and tenant-finder pins. The `ComunicacaoFiscalRepository` set is unchanged.

**Failsafe on real PostgreSQL 16 (Testcontainers, Docker up):** 18 tests, 0 failures, 0 errors, 0 skipped.

`MigracaoFiscal134IT` (6 tests):
- Hibernate column count is `43 + 14 + 16`.
- `idx_comunicacao_fiscal_estado_proxima` is present.
- The only CHECK is `ck_comunicacao_fiscal_autorizado_producao`, in both schemas.

`MigracaoFiscal135IT` (4 tests): the idempotency test now applies 134, 135 and 136 before comparing with Hibernate.

`MigracaoFiscal136IT` (8 tests):
- **(a)** 134 + 135 + 136, then 136 again, matches Hibernate on columns (16 + 11), unique constraints and indexes.
- **(b)** The CHECK has the same `pg_get_constraintdef` in `public` and in the script schema.
- **(c)** `UPDATE` to AUTORIZADO, and a direct INSERT of AUTORIZADO, both fail with `23514` in both schemas. `ACEITE_SIMULADO`, `ERRO` and `REJEITADO` are accepted. `PRODUCAO` + `AUTORIZADO` is accepted, which proves the rule is exact.
- **(d)** A 134-era PENDENTE row survives 136 unchanged. It gets `reprocessamentos = 0` and NULL in the new columns, and the CHECK is created on the populated table.
- **(e)** A second document row is refused (`uk_documento_fiscal_xml_documento`), a repeated IUD is refused (`uk_documento_fiscal_xml_iud`), and an IUD longer than 45 characters is refused.
- **(e')** `inserirSeAusente` returns 1, then 0 for the same document and 0 for the same IUD. The finder returns every column, and another tenant gets nothing.
- **(f)** `xml` is `text`, and `reprocessamentos` is `integer NOT NULL DEFAULT 0` in both schemas.
- **(g)** A second `update` boot emits no DDL for either table.

**Acceptance greps:**
- `AUTORIZADO` appears 0 times outside comments in the enum, and `AmbienteFiscal` is unchanged.
- The check name, `idx_comunicacao_fiscal_estado_proxima`, `@Immutable` and `ON CONFLICT DO NOTHING` each appear exactly once.
- There are 0 `public void set` methods.
- There are 6 `ADD COLUMN IF NOT EXISTS` lines and 1 `CREATE TABLE IF NOT EXISTS t_documento_fiscal_xml`.
- The README has 4 `136-efatura-comunicacao` mentions, in the same commit as the script.
- `43 + 14 + 10` appears 0 times, and `"23514"` appears 3 times.

**Full backend `mvn verify`** (run after this plan's commits, `-Dspotbugs.skip=true`): 0 failures, 0 errors and 0 skipped.
- Surefire ran 1024 tests.
- Failsafe ran 131 ITs. All fiscal ITs ran on real PostgreSQL, including `DocumentoFiscalRepositoryIT` and the emission/NC ITs that build `ComunicacaoFiscal` rows.

SpotBugs `compile spotbugs:check` was run separately after Task 1 and is clean.

## TDD Gate Compliance

- **Task 1:** RED `test(136-02)` e823884 failed to compile on the missing `DocumentoFiscalXml`. GREEN `feat(136-02)` c949e90.
- **Task 2:** the script and the ITs were written together and committed together in 3b08a87, so there is no separate RED commit. The ITs passed on their first run.

## Deviations from Plan

**1. [Rule 1 - Acceptance consistency] Javadoc reworded so that literal-count greps (== 1) stay exact**
- **Found during:** Task 1.
- **Issue:** The check name, `@Immutable` and `ON CONFLICT DO NOTHING` were also quoted in the Javadoc, so each counted twice.
- **Fix:** Rephrased the Javadoc. The code is unchanged.
- **Commit:** c949e90.

**2. [Rule 2 - Correctness] Migration README also states that 136 is needed on `ddl-auto: update` installs for the CHECK**
- **Found during:** Task 2.
- **Issue:** Hibernate never adds a CHECK constraint to an existing table. Phase 134/135 installs would therefore get the columns but not the DFE-06 guarantee unless 136 runs.
- **Fix:** Added this to the script header and to README row 22. Also updated:
  - the Path A note
  - the re-run safety table (12 of 22)
  - the outstanding list (14)
- **Commit:** 3b08a87.

## Threat Flags

None. T-136-04, T-136-05, T-136-06 and T-136-07 are mitigated as planned.

## Self-Check: PASSED

- FOUND: backend/migrations/136-efatura-comunicacao.sql
- FOUND: backend/src/main/java/com/lexcv/models/DocumentoFiscalXml.java
- FOUND: backend/src/main/java/com/lexcv/repositories/DocumentoFiscalXmlRepository.java
- FOUND: backend/src/test/java/com/lexcv/repositories/MigracaoFiscal136IT.java
- FOUND: commits e823884, c949e90, 3b08a87
