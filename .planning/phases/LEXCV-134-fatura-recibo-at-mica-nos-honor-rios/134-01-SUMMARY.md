---
phase: 134-fatura-recibo-at-mica-nos-honor-rios
plan: 01
subsystem: backend-fiscal
tags: [fiscal, efatura, jpa, immutable, migration, testcontainers]
requires:
  - "Phase 133: TipoDocumentoFiscal/AmbienteFiscal/RegimeIva + converters, MigracaoFiscal133IT harness, CapturaMetadataHibernate"
provides:
  - "DocumentoFiscal (t_documento_fiscal): @Immutable flat snapshot of emitente/adquirente, every column updatable=false, no setters"
  - "DocumentoFiscalLinha (t_documento_fiscal_linha): @Immutable single line"
  - "ComunicacaoFiscal (t_comunicacao_fiscal): mutable satellite with @Version, one per document"
  - "EstadoComunicacaoFiscal { PENDENTE } + EstadoComunicacaoFiscalConverter"
  - "backend/migrations/134-create-documento-fiscal-tables.sql + README Path B row 20 + guarded uk_conta_corrente_cliente"
  - "MigracaoFiscal134IT (6 tests: parity, idempotency, no CHECK, no DDL on 2nd boot, CC index branches, per-tenant uniqueness)"
affects: [134-03, 134-04, 134-05, 134-06, 136]
tech-stack:
  added: []
  patterns:
    - "Immutable fiscal entity: @Immutable + updatable=false on every @Column + @Getter/@Builder only (protected no-args, private all-args)"
    - "Script DO block guarding a unique index via pg_index (indisunique, indnkeyatts = 1) with a duplicate pre-check that aborts before creating anything"
    - "Scratch-schema script runs inside TransactionTemplate with SET LOCAL search_path (reverts on commit or rollback)"
key-files:
  created:
    - backend/src/main/java/com/lexcv/models/EstadoComunicacaoFiscal.java
    - backend/src/main/java/com/lexcv/models/EstadoComunicacaoFiscalConverter.java
    - backend/src/main/java/com/lexcv/models/DocumentoFiscal.java
    - backend/src/main/java/com/lexcv/models/DocumentoFiscalLinha.java
    - backend/src/main/java/com/lexcv/models/ComunicacaoFiscal.java
    - backend/migrations/134-create-documento-fiscal-tables.sql
    - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal134IT.java
  modified:
    - backend/migrations/README.md
decisions:
  - "DocumentoFiscal.metodoPagamento is a String (MetodoPagamento.name()), so the schema does not depend on the enum from plan 02"
  - "README also updated in Re-run safety (10 of 20) and Known execution status (12 outstanding), not only Path A and row 20, so the checklist stays internally consistent"
  - "The script's t_conta_corrente block uses to_regclass (respects search_path) and skips when any non-partial single-column unique index on cliente_id exists, which covers Hibernate's unique constraint on a fresh install"
metrics:
  duration: "~15 min"
  completed: 2026-10-04
  tasks: 2
  files: 8
---

# Phase 134 Plan 01: Persistence model of the Fatura-Recibo Summary

Three new tables: an immutable `t_documento_fiscal` snapshot (flat emitente/adquirente columns, tenant-scoped unique keys on series number and idempotency key, globally unique `pagamento_id`), an immutable single-line `t_documento_fiscal_linha`, and a mutable `t_comunicacao_fiscal` satellite (with `@Version`) whose rows are created PENDENTE. They come with an idempotent manual script (which also adds a guarded unique index on `t_conta_corrente.cliente_id`) that a Testcontainers IT proves equal to the Hibernate schema.

## What was built

- **Entities** (`com.lexcv.models`): `DocumentoFiscal` (40 columns) and `DocumentoFiscalLinha` (14 columns) are `@Immutable`, have no setters, and every `@Column` is `updatable = false`. `ComunicacaoFiscal` (10 columns) is mutable with `@Version versao`, `tentativas` defaulting to 0 in the builder, and no DB defaults. Enum columns use `@Convert` with `length = 32` and no `columnDefinition` (WR-01). There is no `@PrePersist` and no `now()`: `emitidoEm`/`dataEmissao`/`createdAt` come from the service's `Clock`.
- **Script 134**: 3 `CREATE TABLE IF NOT EXISTS` and 5 `CREATE INDEX IF NOT EXISTS`, no CHECK and no DEFAULT. A `DO` block creates `uk_conta_corrente_cliente` only when `t_conta_corrente` exists without a single-column unique index on `cliente_id`. If any `cliente_id` is duplicated, it raises `Phase 134: t_conta_corrente tem cliente_id duplicado ...; resolva antes de continuar` before creating anything.
- **README**: Path A fresh-database bullet, Path B row 20, Re-run safety row, and a Known-execution-status row.
- **MigracaoFiscal134IT** (6 tests, all actually run against `postgres:16-alpine`):
  - column parity (64 columns, including `column_default`)
  - named UNIQUE parity
  - `pg_indexes` name and definition parity
  - idempotency
  - zero CHECK constraints
  - no DDL from the `SchemaMigrator` on a second `update` boot
  - both CC index branches: index created when missing; on duplicates, the script aborts, leaving no index and no fiscal tables
  - per-tenant uniqueness for `(tenant_id, serie_id, numero)` and `(tenant_id, chave_idempotencia)`

## Verification

- `mvn -Dmaven.compiler.release=21 -Dit.test=MigracaoFiscal134IT,MigracaoFiscal133IT verify`: 134IT 6/6, 133IT 8/8, 0 failures, 0 skipped
- `MotivoIsencaoIvaTest, SerieFiscalCodigoTest`: green
- `compile spotbugs:check`: passes
- Acceptance greps: `@Immutable` present in both entities; no `@Setter`/`@Data`; 0 `@Column(` lines without `updatable = false`; no `columnDefinition|@Enumerated|@Check|.now(` in the three entities; 10 `IF NOT EXISTS` in the script; no CHECK/DEFAULT outside comments; README matches 4 times and has one `| 20 |` row

## Commits

| Task | Commit | Description |
|------|--------|-------------|
| 1 | c4c664f | Immutable DocumentoFiscal/Linha, ComunicacaoFiscal, EstadoComunicacaoFiscal + converter |
| 2 | 33de541 | Script 134, README row 20, MigracaoFiscal134IT |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Acceptance] Reworded a code comment that tripped the forbidden-pattern grep**
- **Found during:** Task 1
- **Issue:** A comment in DocumentoFiscal.java mentioned `@Enumerated` and `columnDefinition`, which the acceptance grep (`columnDefinition|@Enumerated|...`) flags even in comments.
- **Fix:** Reworded the comment so it keeps the same meaning without those tokens.
- **Files modified:** backend/src/main/java/com/lexcv/models/DocumentoFiscal.java
- **Commit:** c4c664f

**2. [Rule 2 - Consistency] README Re-run safety and Known execution status tables also updated**
- **Found during:** Task 2
- **Issue:** The plan only asked for the Path A bullet and the Path B row 20. Without more changes, the "9 of 19" re-run count and the outstanding-scripts list would have been stale.
- **Fix:** Changed the count to "10 of 20" and added 134 to both tables (outstanding count 11 → 12).
- **Commit:** 33de541

TDD note (Task 2, `tdd="true"`): the IT is a parity proof over the entities from Task 1, so script, README and IT were committed together, as the plan requires (same-commit rule). There was no separate failing-test commit.

## Known Stubs

None. `EstadoComunicacaoFiscal` deliberately has only `PENDENTE`; Phase 136 adds the other states.

## Self-Check: PASSED

- FOUND: all 8 key files
- FOUND: c4c664f, 33de541
