---
phase: "59"
plan: "01"
subsystem: backend-models
tags: [jpa, json-columns, attribute-converter, entity-extension]
dependency_graph:
  requires: []
  provides: [Cliente.procuracaoKey, Cliente.descricaoCaso, Cliente.documentosEntregues, Cliente.documentosATratar, Cliente.deslocacoes, Cliente.honorariosPropostos]
  affects: [t_cliente table, Wave 2 endpoints]
tech_stack:
  added: []
  patterns: [AttributeConverter, TypeReference, Jackson ObjectMapper]
key_files:
  created:
    - backend/src/main/java/com/lexcv/models/DocumentoEntregue.java
    - backend/src/main/java/com/lexcv/models/DocumentoATratar.java
    - backend/src/main/java/com/lexcv/models/Deslocacao.java
    - backend/src/main/java/com/lexcv/models/HonorariosPropostos.java
    - backend/src/main/java/com/lexcv/models/DocumentosEntreguesConverter.java
    - backend/src/main/java/com/lexcv/models/DocumentosATratarConverter.java
    - backend/src/main/java/com/lexcv/models/DeslocacoesConverter.java
    - backend/src/main/java/com/lexcv/models/HonorariosPropostosConverter.java
  modified:
    - backend/src/main/java/com/lexcv/models/Cliente.java
decisions:
  - HonorariosPropostos.total uses BigDecimal (not String) per planning decision D-13
  - List converters use TypeReference<List<T>> for Jackson deserialization
  - Single-object HonorariosPropostosConverter mirrors DadosTipoConverter structure exactly
metrics:
  duration: "~10 minutes"
  completed: "2026-06-29"
  tasks_completed: 2
  files_changed: 9
---

# Phase 59 Plan 01: Backend Entity Foundation Summary

**One-liner:** Four POJO/converter pairs for JSON intake columns and Cliente entity extended with procuracaoKey, descricaoCaso, and four @Convert-annotated JSON fields — all compiling with mvn -DskipTests package.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Create POJO classes and AttributeConverters | e255151 | 8 new Java files |
| 2 | Extend Cliente.java with Phase 59 fields | a6d6797 | Cliente.java |

## What Was Built

**Task 1** — Created four POJO classes following the DadosTipo pattern exactly:
- `DocumentoEntregue` (descricao, data)
- `DocumentoATratar` (descricao)
- `Deslocacao` (descricao, local, data)
- `HonorariosPropostos` (total as BigDecimal, totalPorExtenso, previsao)

All annotated with Lombok `@Data @NoArgsConstructor @AllArgsConstructor @Builder @JsonInclude(NON_NULL)`.

Four `AttributeConverter` implementations:
- `DocumentosEntreguesConverter`, `DocumentosATratarConverter`, `DeslocacoesConverter` — list converters using `TypeReference<List<T>>` in convertToEntityAttribute
- `HonorariosPropostosConverter` — single-object converter mirroring `DadosTipoConverter`

All handle null/blank gracefully, returning null on error.

**Task 2** — Extended `Cliente.java` with six new fields:
- `procuracaoKey` (`procuracao_key` column, String)
- `descricaoCaso` (`descricao_caso` TEXT column, String)
- `documentosEntregues` (@Convert to DocumentosEntreguesConverter)
- `documentosATratar` (@Convert to DocumentosATratarConverter)
- `deslocacoes` (@Convert to DeslocacoesConverter)
- `honorariosPropostos` (@Convert to HonorariosPropostosConverter)

Added `java.util.List` import. Lombok `@Getter @Setter` on the class auto-generates accessors for all new fields.

## Deviations from Plan

None — plan executed exactly as written.

## Threat Compliance

T-59-01 (procuracaoKey write-protection via PUT): Noted. `procuracaoKey` was added as a field on the entity. The Wave 2 plan for the PUT endpoint handler must NOT call `setProcuracaoKey()` — that enforcement belongs to Plan 02/03 (endpoint wiring). This plan only establishes the persistence layer.

## Known Stubs

None — no UI or data rendering involved in this plan.

## Threat Flags

None — this plan only adds JPA entity fields and POJO classes. No new network endpoints or trust boundaries introduced.

## Self-Check: PASSED

- e255151 exists in git log
- a6d6797 exists in git log
- All 8 new Java files created in backend/src/main/java/com/lexcv/models/
- Cliente.java contains procuracaoKey, descricaoCaso, documentosEntregues, documentosATratar, deslocacoes, honorariosPropostos
- mvn -DskipTests package exits 0
