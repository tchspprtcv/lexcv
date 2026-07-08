---
status: partial
phase: 81-backend-crud-decis-es-factos-testemunhas-wiring-ju-zo-origem
source: [81-VERIFICATION.md]
started: 2026-07-07T21:30:00Z
updated: 2026-07-07T21:30:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. POST /api/v1/processos/intake without 'origem' in body
expected: HTTP 422 with camposEmFalta containing 'origem', no row persisted
result: [pending]

### 2. POST /api/v1/processos/intake with valid origem, then PUT /api/v1/processos/{id} with a different origem + a juizo value, then GET both /processos/{id} and /processos (list)
expected: origem unchanged after PUT (immutability), juizo updated, and GET /processos (list) surfaces juizo/origem matching the detail view
result: [pending]

### 3. Full lifecycle (create -> list -> update -> cross-processo 404 -> delete) for Decisão (incl. multipart file upload), Testemunha, and Facto, using two different processo ids to prove the double-check ownership pattern rejects a mismatched processoId/childId pair with 404
expected: Create succeeds (201), list shows the new row, update succeeds (200), a PUT/DELETE using a different processo's id in the path but the first processo's child id returns 404, delete succeeds (200) and the row disappears from a subsequent list
result: [pending]

### 4. Two near-simultaneous POST /api/v1/processos/{id}/factos requests with a client-forged 'ordem': 999 in both payloads
expected: Both succeed with distinct, server-computed sequential ordem values (not 999); no 409 under normal (non-adversarial-timing) concurrency; a genuine same-millisecond race should still produce two valid ordem values without a duplicate, given the synchronized block + unique-constraint backstop
result: [pending]

### 5. Run backend/migrations/81-add-facto-ordem-unique-constraint.sql against a database where ddl-auto=validate (i.e. a prod-like environment) and confirm createFacto/updateFacto return 409 (not 500) on a genuine (processo_id, ordem) collision post-migration
expected: The manual SQL script applies cleanly and the unique constraint is enforced identically to how it already is in dev (where ddl-auto=update auto-created it)
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
