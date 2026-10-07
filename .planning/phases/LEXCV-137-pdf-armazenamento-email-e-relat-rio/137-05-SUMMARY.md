---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 05
subsystem: backend/fiscal rules
tags: [email-delivery, backoff, csv, formula-injection, pure-functions]
requires: [137-02]
provides:
  - RegrasEntregaEmail {NAO_CONFIGURADO, estadoApresentado, reenviavel, emailValido, ultimoErroVisivel}
  - BackoffEntregaEmail.atraso(1..4) = 1m, 5m, 15m, 1h
  - CsvFiscal {BOM, SEPARADOR, FIM_LINHA, textoLivre, estruturado, valor, data, linha}
affects: [137-11, 137-12, 137-14, 137-17, 137-18]
tech-stack:
  added: []
  patterns: [final utility class with private ctor, fixed backoff table guarded by MAX_TENTATIVAS]
key-files:
  created:
    - backend/src/main/java/com/lexcv/services/fiscal/RegrasEntregaEmail.java
    - backend/src/test/java/com/lexcv/services/fiscal/RegrasEntregaEmailTest.java
    - backend/src/main/java/com/lexcv/services/fiscal/BackoffEntregaEmail.java
    - backend/src/test/java/com/lexcv/services/fiscal/BackoffEntregaEmailTest.java
    - backend/src/main/java/com/lexcv/fiscal/csv/CsvFiscal.java
    - backend/src/test/java/com/lexcv/fiscal/csv/CsvFiscalTest.java
  modified: []
decisions:
  - "emailValido also rejects ':', '(', ')', '[', ']', '\\', '\"', Unicode spaces, '..' in the domain and a domain starting or ending with '.'. This is conservative, and the adapter re-parses strictly."
  - "A value with a leading CR is guarded first, then quoted (\"'\\rX\"). This matches the web guard followed by the web escape."
  - "BackoffEntregaEmail has a package-private tamanhoTabela() so the test can pin the table size to MAX_TENTATIVAS - 1"
metrics:
  duration: ~15min
  completed: 2026-10-07
  tasks: 3
  files: 6
---

# Phase 137 Plan 05: Shared email-delivery rules, backoff and CSV cells Summary

This plan adds three pure, test-first rule classes that later plans share:
- **`RegrasEntregaEmail`**: the delivery state to display, whether a resend is allowed, recipient validation, and when the last error is visible.
- **`BackoffEntregaEmail`**: a fixed retry delay table of 1 min, 5 min, 15 min and 1 h, capped at 5 attempts.
- **`CsvFiscal`**: CSV cells for Excel PT, with the formula guard applied only to free-text cells.

## Tasks

| # | Task | RED | GREEN |
|---|------|-----|-------|
| 1 | RegrasEntregaEmail | 12b262e | a491fea |
| 2 | BackoffEntregaEmail | 5da2bf5 | d21f991 |
| 3 | CsvFiscal | e25c7b2 | d424c68 |

## Verification

- Each RED run was a compile failure (the class under test did not exist yet).
- `RegrasEntregaEmailTest`: **50/50** (parameterized).
- `BackoffEntregaEmailTest`: **10/10**.
- `CsvFiscalTest`: **14/14**. This includes runs with the default Locale set to US, GERMANY and FRANCE; the original Locale is restored afterwards.
- `mvn -Dmaven.compiler.release=21 -DskipTests compile spotbugs:check`: clean.

## Deviations from Plan

None in substance. The recipient check rejects a few more characters than the behavior list names; the plan asks for a "deliberately conservative" check, and these are listed under decisions. Other additions are tests only: NC amount `-1234,50` under foreign locales, `1E+3` → `1000,00`, and HALF_EVEN `0.125` → `0,12`.

## TDD Gate Compliance

There is a `test(...)` commit before each `feat(...)` commit, for all three tasks.

## Self-Check: PASSED
