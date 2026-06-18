---
phase: "44-status-kpis"
plan: "01"
subsystem: "backend/financeiro"
tags: ["honorario", "formula", "jpa", "financeiro"]
dependency_graph:
  requires: []
  provides: ["Honorario.totalPago via @Formula"]
  affects: ["GET /honorarios"]
tech_stack:
  added: ["org.hibernate.annotations.Formula"]
  patterns: ["JPA @Formula computed field", "@JsonProperty READ_ONLY"]
key_files:
  modified:
    - backend/src/main/java/com/lexcv/models/Honorario.java
decisions:
  - "Usar @Formula (Hibernate) sem @Transient — Hibernate preenche campos @Formula no SELECT, @Transient excluiria o campo do ORM"
  - "Nenhuma alteração necessária em ResourceController: findByProcessoId é método derivado Spring Data que carrega entidade completa"
  - "@JsonProperty(READ_ONLY) para mitigar T-44-02 (injecção de totalPago via POST/PUT)"
metrics:
  duration: "5m"
  completed_date: "2026-06-18"
  tasks_completed: 2
  tasks_total: 2
  files_changed: 1
requirements:
  - FIN-07
  - FIN-08
---

# Phase 44 Plan 01: Add totalPago to Honorario Summary

**One-liner:** Campo `totalPago` calculado via `@Formula` Hibernate (subquery SQL sobre `t_pagamento`) adicionado à entidade `Honorario`, serializado automaticamente em `GET /honorarios`.

## What Was Built

Adicionado o campo `totalPago: BigDecimal` à entidade JPA `Honorario` usando `@org.hibernate.annotations.Formula`. O Hibernate injeta o valor via subquery SQL no momento do SELECT, sem necessitar de coluna real na tabela. O campo é marcado `@JsonProperty(READ_ONLY)` para impedir que clientes injetem valores forjados em POST/PUT.

O endpoint `GET /honorarios` (ResourceController ~linha 1754) já devolve `List<Honorario>` usando `honorarioRepository.findByProcessoId(p.getId())` — método derivado Spring Data que gera SELECT completo, incluindo campos `@Formula`. Nenhuma alteração foi necessária no controller ou repositório.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Adicionar @Formula totalPago à entidade Honorario | 09ea4ac | Honorario.java |
| 2 | Verificar serialização no endpoint GET /honorarios | 09ea4ac | (nenhuma alteração necessária) |

## Deviations from Plan

Nenhum — plano executado exatamente como escrito. A Task 2 não requereu modificação de código: a inspeção do `HonorarioRepository` confirmou que `findByProcessoId` é um método derivado do Spring Data (sem `@Query` customizada), garantindo que o Hibernate executa o SELECT completo com a subquery `@Formula`.

## Verification

- Build Maven sem erros: `mvn -DskipTests package -q` terminou sem output de erro.
- `Honorario.java` contém `@Formula` com subquery sobre `t_pagamento`.
- `HonorarioRepository.findByProcessoId` não tem projeção parcial.

## Threat Model Coverage

| Threat ID | Mitigation Aplicada |
|-----------|---------------------|
| T-44-01 | Aceite — subquery usa `honorario_id = id` (coluna local); tenant isolation mantida pelo filtro de processos no controller |
| T-44-02 | Mitigado — `@JsonProperty(access = READ_ONLY)` impede injeção de `totalPago` via POST/PUT |

## Self-Check: PASSED

- `backend/src/main/java/com/lexcv/models/Honorario.java` modificado e commitado (09ea4ac)
- Build Maven limpo confirmado
