---
phase: 135-nota-de-cr-dito
plan: 02
subsystem: backend-fiscal
tags: [fiscal, migration, ddl-validate, testcontainers, nota-de-credito]
requires:
  - "135-01: DocumentoFiscal NC columns + idx_documento_fiscal_tenant_origem"
  - "Phase 134: 134-create-documento-fiscal-tables.sql + MigracaoFiscal134IT harness"
provides:
  - "backend/migrations/135-add-nota-credito-documento-fiscal.sql (idempotent, additive, no CHECK/FK/backfill)"
  - "README rows: Path A note, Path B row 21, re-run safety (11 of 21), known execution status (13 outstanding)"
  - "MigracaoFiscal134IT now proves 134+135 == Hibernate; MigracaoFiscal135IT (4 tests)"
affects: [135-13, 136]
tech-stack:
  added: []
  patterns:
    - "Per-test scratch schema dropped in finally, class-level NOT_SUPPORTED so a refused INSERT does not poison a shared transaction"
key-files:
  created:
    - backend/migrations/135-add-nota-credito-documento-fiscal.sql
    - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal135IT.java
  modified:
    - backend/migrations/README.md
    - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal134IT.java
decisions:
  - "Column total recounted from Hibernate output: t_documento_fiscal 40 -> 43 (14 linha + 10 comunicacao unchanged), so 43 + 14 + 10 = 67 is the correct assertion"
  - "aplicarScriptEmTransacao (t_conta_corrente index branches) still applies only 134: the 135 script is unrelated to that block"
  - "README Path A list also gets a 135 note (redundant-but-harmless), so that checklist stays complete"
metrics:
  duration: "~10 min"
  completed: 2026-10-05
  tasks: 2
  files: 4
---

# Phase 135 Plan 02: Migration 135 for Nota de Crédito columns Summary

This plan adds an idempotent manual script, `135-add-nota-credito-documento-fiscal.sql`. It adds three nullable columns and one index, and changes no constraint. The README inventory rows are in the same commit. ITs against real PostgreSQL prove three things: 134 plus 135 equals the Hibernate schema, the script can be re-run, and applying it to a database that already holds Fatura-Recibo rows leaves them untouched.

## Tasks

| Task | Name | Commit |
|------|------|--------|
| 1 | Migration script 135 and README rows | 5045780 |
| 2 | MigracaoFiscal134IT deliberate update and new MigracaoFiscal135IT | 8f8702b |

## Verification

- `mvn -Dmaven.compiler.release=21 -Dtest=NenhumTeste -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=MigracaoFiscal133IT,MigracaoFiscal134IT,MigracaoFiscal135IT verify`: BUILD SUCCESS.
  - 133 IT: 8 tests. 134 IT: 6 tests. 135 IT: 4 tests.
  - 0 failures, 0 errors, 0 skipped. Testcontainers actually ran.
- Column count, recounted as the plan-checker asked: the Hibernate `information_schema` total on `public` is 67 = 43 (`t_documento_fiscal`) + 14 (`t_documento_fiscal_linha`) + 10 (`t_comunicacao_fiscal`). Before this phase the figure was 40 + 14 + 10, and the 3 NC columns account for the difference.
- What MigracaoFiscal135IT covers:
  - **Idempotence:** applying 135 twice on top of 134 raises no error, and columns, uniques and indexes equal Hibernate's.
  - **New column types:** the 3 new columns are nullable and typed as Hibernate types them, and `pagamento_id` is still NOT NULL.
  - **Existing data:** a populated FR row keeps every original column identical and gets NULL in the NC columns. An NC row can then be inserted. A second document on the same estorno `pagamento_id` fails on `uk_documento_fiscal_pagamento`.
  - **Text limit:** a `motivo_texto` of 201 characters is rejected; 200 is accepted.
- Acceptance greps on non-comment lines of the script:
  - 4 `IF NOT EXISTS`
  - 0 `DROP NOT NULL`, `CHECK` or `REFERENCES`
  - the README names the file 4 times

## Deviations from Plan

None. The plan was executed as written. The one addition is the README Path A note; the "Adding a new migration" checklist calls for it.

## Known Stubs

None.

## Self-Check: PASSED

- The script, the README rows and both ITs are present.
- Commits 5045780 and 8f8702b are present in `git log`.
