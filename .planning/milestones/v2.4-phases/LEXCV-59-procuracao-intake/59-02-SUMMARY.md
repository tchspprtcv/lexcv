---
phase: "59"
plan: "02"
subsystem: backend-models
tags: [jpa, entities, repositories, junction-table, user-extension]
dependency_graph:
  requires: []
  provides:
    - ClienteAdvogado entity (t_cliente_advogado)
    - ClienteAdministrativo entity (t_cliente_administrativo)
    - ClienteAdvogadoRepository
    - ClienteAdministrativoRepository
    - UserRepository.findByTenantIdAndRoleName
    - UserRepository.findByTenantIdAndRoleNameIn
  affects:
    - backend/src/main/java/com/lexcv/models/User.java
    - backend/src/main/java/com/lexcv/repositories/UserRepository.java
tech_stack:
  added: []
  patterns:
    - JPA junction entity with @UniqueConstraint (mirrors ClienteContacto pattern)
    - Spring Data @Query with @Param for role-filtered user queries
key_files:
  created:
    - backend/src/main/java/com/lexcv/models/ClienteAdvogado.java
    - backend/src/main/java/com/lexcv/models/ClienteAdministrativo.java
    - backend/src/main/java/com/lexcv/repositories/ClienteAdvogadoRepository.java
    - backend/src/main/java/com/lexcv/repositories/ClienteAdministrativoRepository.java
  modified:
    - backend/src/main/java/com/lexcv/models/User.java
    - backend/src/main/java/com/lexcv/repositories/UserRepository.java
decisions:
  - "Added numeroCedula to User entity (not junction) per planning decision D-06 / research recommendation"
  - "Used simple junction entity pattern (not @ManyToMany on Cliente) mirroring ClienteContacto"
  - "Role name comparison uses r.nome in JPQL matching the seeded values: ADVOGADO, ASSISTENTE, TECNICO"
metrics:
  duration: "~8 minutes"
  completed: "2026-06-29"
  tasks_completed: 2
  tasks_total: 2
  files_created: 4
  files_modified: 2
---

# Phase 59 Plan 02: Junction Entities and UserRepository Extensions Summary

**One-liner:** JPA junction entities for advogado/administrativo assignment with role-filtered UserRepository queries.

## What Was Built

Added the data model foundation for assigning advogados and administrativos to clientes:

1. **User.java** — Added nullable `numeroCedula` field (`@Column(name = "numero_cedula")`) to support requirement INT-02 (advogado cédula number display).

2. **ClienteAdvogado.java** — New entity mapping `t_cliente_advogado` with `@UniqueConstraint(columnNames = {"cliente_id", "user_id"})`. Fields: id (UUID), clienteId, userId, tenantId, createdAt. Uses `@PrePersist` for timestamp.

3. **ClienteAdministrativo.java** — Same structure as ClienteAdvogado but for `t_cliente_administrativo` table (administrativos with ASSISTENTE/TECNICO roles).

4. **ClienteAdvogadoRepository.java** — Spring Data repository with: `findByClienteIdAndTenantId`, `findByClienteIdAndUserIdAndTenantId`, `deleteByClienteIdAndUserIdAndTenantId`.

5. **ClienteAdministrativoRepository.java** — Same three methods on `ClienteAdministrativo`.

6. **UserRepository.java** — Extended with two JPQL `@Query` methods:
   - `findByTenantIdAndRoleName(tenantId, roleName)` — filters users by single role (for ADVOGADO lookup)
   - `findByTenantIdAndRoleNameIn(tenantId, roleNames)` — filters users by multiple roles (for ASSISTENTE/TECNICO lookup)

## Verification

Both tasks verified with `mvn -DskipTests package` — BUILD SUCCESS.

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints introduced in this plan. Security-sensitive role validation (T-59-02, T-59-03) is deferred to Wave 2 endpoints per threat register disposition.

## Task Commits

| Task | Description | Commit |
|------|-------------|--------|
| 1 | Add numeroCedula to User and create junction entities | 1bab712 |
| 2 | Create junction repositories and extend UserRepository | d39278e |

## Self-Check: PASSED

- [x] backend/src/main/java/com/lexcv/models/ClienteAdvogado.java — exists
- [x] backend/src/main/java/com/lexcv/models/ClienteAdministrativo.java — exists
- [x] backend/src/main/java/com/lexcv/repositories/ClienteAdvogadoRepository.java — exists
- [x] backend/src/main/java/com/lexcv/repositories/ClienteAdministrativoRepository.java — exists
- [x] User.java contains numeroCedula — confirmed
- [x] UserRepository contains findByTenantIdAndRoleName — confirmed
- [x] Commits 1bab712 and d39278e exist
