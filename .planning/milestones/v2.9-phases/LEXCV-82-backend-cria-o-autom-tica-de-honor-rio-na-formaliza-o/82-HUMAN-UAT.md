---
status: partial
phase: 82-backend-cria-o-autom-tica-de-honor-rio-na-formaliza-o
source: [82-VERIFICATION.md]
started: 2026-07-07T22:45:00Z
updated: 2026-07-07T22:45:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Formalize a real processo (TRIAGEM→ATIVO) via POST /api/v1/processos/{id}/formalizar against a running backend with valid credentials, then immediately open /financeiro and /financeiro/{honorarioId} in a browser
expected: Both pages render without crashing; the auto-created Honorário's total shows 'A confirmar' (not a thrown TypeError, not a blank/broken page); the 'Editar' dialog on the detail page opens with an empty (not literal 'null') Valor Total field
result: [pending]

### 2. Formalize the same processo a second time (retry/replay), then query SELECT processo_id, COUNT(*) FROM t_honorario GROUP BY processo_id HAVING COUNT(*) > 1 against the dev DB
expected: Second call returns 409; the detection query returns zero rows; exactly one Honorario row exists for that processo_id with valorTotal literally JSON null
result: [pending]

### 3. Fire two genuinely concurrent POST /processos/{id}/formalizar requests for the same processo id and confirm only one Honorario row is persisted, with no unhandled 500
expected: Exactly one Honorario row survives; either both requests succeed (one creates, one silently no-ops after DataIntegrityViolationException) or the second is rejected cleanly by the pre-existing estado guard — no stack trace, no duplicate row
result: [pending]

### 4. Run backend/migrations/82-add-honorario-processo-unique-constraint.sql against a database where ddl-auto=validate (i.e. a prod-like environment) and confirm the constraint applies cleanly without a pre-existing duplicate-processo_id violation blocking it
expected: The manual SQL script applies without error, and the unique constraint is enforced identically to how it is auto-created in dev (ddl-auto=update)
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
