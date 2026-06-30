---
phase: "57"
plan: "02"
subsystem: backend/controllers
tags: [jpa, controller, numero-cliente, tenant-isolation, synchronized]
dependency_graph:
  requires: [57-01]
  provides: [createCliente with CLI-XXXX generation, updateCliente with avencado+dadosTipo]
  affects: [frontend clientes API consumers]
tech_stack:
  added: []
  patterns: [synchronized JVM lock for sequential number generation, String.format CLI-%04d]
key_files:
  created: []
  modified:
    - backend/src/main/java/com/lexcv/controllers/ResourceController.java
decisions:
  - "synchronized(ClienteRepository.class) used as JVM-level lock — acceptable for single-instance deployment per CONTEXT.md D-06"
  - "numeroCliente and numeroSequencial NOT included in updateCliente — immutable once assigned at creation per D-03"
  - "java.util.Optional used inline (no import needed) to avoid ambiguity with any existing Optional imports"
metrics:
  duration: "~5 minutes"
  completed: "2026-06-29"
  tasks_completed: 1
  tasks_total: 2
  files_created: 0
  files_modified: 1
---

# Phase 57 Plan 02: Controller Wiring Summary

One-liner: ResourceController extended with tenant-scoped CLI-XXXX sequence generation in createCliente and avencado/dadosTipo field persistence in updateCliente.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Wire numero_cliente generation and extend updateCliente | 3a23b9e | ResourceController.java |

## Task 2 — Pending Human Verification

Task 2 is a `checkpoint:human-verify` — execution paused awaiting manual curl verification.

## What Was Built

### Task 1 — ResourceController changes

**createCliente (POST /api/v1/clientes):**
- Added `synchronized (ClienteRepository.class)` block after `setTenantId` and before `save`
- Calls `clienteRepository.findMaxNumeroSequencialByTenantId(getTenantId())` to get current max
- Computes `nextSeq = maxSeq + 1` (orElse(0) handles no-existing-clients case)
- Sets `cliente.setNumeroSequencial(nextSeq)` and `cliente.setNumeroCliente(String.format("CLI-%04d", nextSeq))`
- Client-supplied numeroCliente/numeroSequencial are silently overwritten (T-57-04 mitigated)

**updateCliente (PUT /api/v1/clientes/{id}):**
- Added `cliente.setAvencado(payload.getAvencado())` after `setDetalhesAdicionais`
- Added `cliente.setDadosTipo(payload.getDadosTipo())` immediately after
- numeroCliente and numeroSequencial intentionally omitted — immutable per D-03

**GET endpoints:** No changes needed — JPA entity serialization already includes all new fields.

## Verification

- `mvn compile` exits 0 (no output = success)
- grep confirms all 5 patterns present: `CLI-%04d`, `findMaxNumeroSequencialByTenantId`, `setDadosTipo`, `setAvencado`, `synchronized`

## Deviations from Plan

None — plan executed exactly as written.

## Threat Surface Scan

No new network endpoints. T-57-04 (server overwrite of client-supplied sequence) and T-57-05 (tenant scoping on GET) mitigated as designed. T-57-06 (JVM-only lock) accepted per plan.

## Known Stubs

None.

## Self-Check: PASSED

- ResourceController.java modified: EXISTS
- Commit 3a23b9e: EXISTS
- grep CLI-%04d: MATCH
- grep findMaxNumeroSequencialByTenantId: MATCH
- grep setDadosTipo: MATCH
- grep setAvencado: MATCH
- grep synchronized: MATCH
