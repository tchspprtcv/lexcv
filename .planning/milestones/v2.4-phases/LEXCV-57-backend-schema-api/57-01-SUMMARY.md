---
phase: "57"
plan: "01"
subsystem: backend/models
tags: [jpa, entity, repository, enum, json-converter]
dependency_graph:
  requires: []
  provides: [TipoCliente enum, DadosTipo POJO, DadosTipoConverter, extended Cliente entity, MAX+1 repository query]
  affects: [backend/controllers/ResourceController.java]
tech_stack:
  added: [DadosTipoConverter (JPA AttributeConverter + Jackson ObjectMapper)]
  patterns: [JPA @Convert for JSON TEXT column, tenant-scoped JPQL MAX query]
key_files:
  created:
    - backend/src/main/java/com/lexcv/models/TipoCliente.java
    - backend/src/main/java/com/lexcv/models/DadosTipo.java
    - backend/src/main/java/com/lexcv/models/DadosTipoConverter.java
  modified:
    - backend/src/main/java/com/lexcv/models/Cliente.java
    - backend/src/main/java/com/lexcv/repositories/ClienteRepository.java
decisions:
  - "DadosTipo fields all nullable — no type enforcement at entity layer (controller handles validation per D-05)"
  - "DadosTipoConverter silently returns null on serialize/deserialize failure (safe degradation for corrupted rows)"
  - "tipo field stays as String per D-04 — TipoCliente enum used in controller logic only"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-29"
  tasks_completed: 2
  tasks_total: 2
  files_created: 3
  files_modified: 2
---

# Phase 57 Plan 01: Backend Schema - Client Entity Extension Summary

One-liner: JPA entity extension adding numero_cliente generation fields, avencado flag, and JSON-backed dados_tipo column via DadosTipoConverter; repository gains tenant-scoped MAX+1 query.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create TipoCliente enum, DadosTipo POJO, DadosTipoConverter | aeb6809 | TipoCliente.java, DadosTipo.java, DadosTipoConverter.java |
| 2 | Extend Cliente entity and ClienteRepository | 054bd51 | Cliente.java, ClienteRepository.java |

## What Was Built

### Task 1 — New model files
- `TipoCliente.java`: Plain enum with `PARTICULAR` and `EMPRESA` values. Mirrors `DocumentoTipo` pattern.
- `DadosTipo.java`: Lombok `@Data @Builder` POJO with 9 nullable fields covering Particular (idade, sexo, nacionalidade, biPassaporte) and Empresa (nomeComercial, nif, sede, representanteLegal, cargoRepresentante). `@JsonInclude(NON_NULL)` suppresses null fields from JSON output.
- `DadosTipoConverter.java`: `@Converter` implementing `AttributeConverter<DadosTipo, String>`. Uses a static `ObjectMapper` for serialize/deserialize. Returns null on null input or exception (safe degradation).

### Task 2 — Entity and repository extensions
- `Cliente.java`: Added 4 new fields after `detalhesAdicionais`:
  - `numeroSequencial` (Integer, column `numero_sequencial`)
  - `numeroCliente` (String length 20, column `numero_cliente`)
  - `avencado` (Boolean, column `avencado`)
  - `dadosTipo` (DadosTipo, column `dados_tipo` TEXT, `@Convert(DadosTipoConverter.class)`)
- `ClienteRepository.java`: Added `findMaxNumeroSequencialByTenantId` with JPQL `SELECT MAX(c.numeroSequencial) FROM Cliente c WHERE c.tenantId = :tenantId` returning `Optional<Integer>`.

## Verification

- `mvn compile` exits 0 with no errors after both tasks.
- All 5 files exist at expected paths.
- `numero_sequencial` and `dadosTipo` fields present in Cliente.java.
- `findMaxNumeroSequencialByTenantId` present in ClienteRepository.java.

## Deviations from Plan

None — plan executed exactly as written.

## Threat Surface Scan

No new network endpoints introduced. Trust boundary mitigations as planned:
- T-57-01: `findMaxNumeroSequencialByTenantId` scoped strictly by `:tenantId` — no global MAX possible.
- T-57-02: `DadosTipoConverter` deserializes only to typed `DadosTipo` POJO; no `@JsonTypeInfo` or polymorphic handling.
- T-57-03: Accepted — TEXT column, no DB-side JSON execution risk.

## Self-Check: PASSED

- TipoCliente.java: EXISTS
- DadosTipo.java: EXISTS
- DadosTipoConverter.java: EXISTS
- Cliente.java (modified): EXISTS
- ClienteRepository.java (modified): EXISTS
- Commit aeb6809: EXISTS
- Commit 054bd51: EXISTS
