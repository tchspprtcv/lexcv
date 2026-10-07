---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 04
subsystem: backend/migrations
tags: [migration, postgresql, testcontainers, schema-parity]
requires: [137-02]
provides:
  - backend/migrations/137-entrega-documento-fiscal.sql (t_entrega_email_fiscal + t_documento_fiscal_pdf)
  - README rows (Path A, Path B row 23, re-run table, status table)
  - MigracaoFiscal137IT (parity, idempotency, 23505, DEFAULT 0, no DDL on second boot)
affects: [OPER-02 verification, deploy of any validate install]
tech-stack:
  added: []
  patterns: [CREATE TABLE/INDEX IF NOT EXISTS only, schema-per-test parity IT against Hibernate]
key-files:
  created:
    - backend/migrations/137-entrega-documento-fiscal.sql
    - backend/src/test/java/com/lexcv/repositories/MigracaoFiscal137IT.java
  modified:
    - backend/migrations/README.md
decisions:
  - "No FK, no CHECK, no DROP; reenvios INTEGER DEFAULT 0 NOT NULL is the only default"
  - "The second-boot test replaces the Hibernate-created public tables with the script-created ones before running the migrator. It is pinned to run last with @Order."
metrics:
  duration: ~15min
  completed: 2026-10-07
  tasks: 2
  files: 3
---

# Phase 137 Plan 04: Migration for the email-delivery and fiscal PDF tables Summary

This plan adds one idempotent hand-written script, `137-entrega-documento-fiscal.sql`. It creates `t_entrega_email_fiscal` and `t_documento_fiscal_pdf`, with their three named unique keys and two indexes. `MigracaoFiscal137IT` proves on PostgreSQL 16 that the script produces the same schema as Hibernate.

## Tasks

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Migration script + README rows (same commit) | e17a9b3 | 137-entrega-documento-fiscal.sql, README.md |
| 2 | MigracaoFiscal137IT | 4cd5ece | MigracaoFiscal137IT.java |

## Details

- **Script header:** follows the 136 layout: required for validate installs, needs 133–136 first, then Why, Design, Backfill (none) and Idempotent sections.
- **Column types:** match the entities exactly. Times are `TIMESTAMP(6) WITH TIME ZONE`, `versao` is `BIGINT`, `tamanho_bytes` is `BIGINT`, and the varchar lengths are 32/254/500/64/512/64/20.
- **README changes:**
  - A Path A note.
  - Path B row 23, marked "Yes — CREATE TABLE/INDEX IF NOT EXISTS".
  - A re-run table row; the count changed from "12 of 22" to "13 of 23".
  - A status row, "Pending, new in this phase (v3.0, Phase 137)"; the outstanding count changed from 14 to 15.

## Verification

- Task 1 grep gate: PASS (≥4 `IF NOT EXISTS`, ≥3 README references, no REFERENCES/DROP/CHECK).
- `mvn -Dmaven.compiler.release=21 verify -Dit.test=MigracaoFiscal137IT ...`: **6 tests, 0 failures, 0 errors, 0 skipped** (Testcontainers ran).
  - (a) Applying 133..137 and then 137 again gives the same columns as Hibernate (16 + 8 columns), and the second run changes nothing.
  - (b) The unique keys and indexes are identical by name and definition. Neither schema has a CHECK or FK.
  - (c) A second delivery row for the same document fails with 23505 (`uk_entrega_email_fiscal_documento`).
  - (d) A second PDF row for the same document fails with 23505, and so does a repeated `object_key`.
  - (e) `reenvios` defaults to 0, in both schemas.
  - (f) A second boot on `update` over the script-created tables emits no DDL for either table.

## Deviations from Plan

- **Second-boot test is stronger than the 136 analog.** The 136 version runs the migrator over the Hibernate-created schema. This version first drops the two `public` tables and recreates them from the script, so the test really covers "over the migrated schema". It is ordered last so tests (a)/(b) still compare against Hibernate's own tables.
- **TDD note:** the IT passed on its first run. Task 1 had already written the script, so a red run was not possible; this is expected for a parity proof.

## Self-Check: PASSED
