---
status: partial
phase: 93-notf-24-prefer-ncias-de-notifica-o-por-utilizador
source: [93-VERIFICATION.md]
started: 2026-07-13T21:00:00.000Z
updated: 2026-07-13T21:00:00.000Z
---

## Current Test

[awaiting human/live testing — blocked by the project's known, pre-existing MINIO_ENDPOINT environment gap, not a code defect]

## Tests

### 1. Settings tab shows 8 toggles, PRAZO_VENCIDO omitted
expected: `/settings` "Notificações" tab renders 8 category toggles (all except PRAZO_VENCIDO).
result: [pending]

### 2. Mute state persists across reload
expected: Toggling a category off, then reloading the page, shows it still off.
result: [pending]

### 3. Reactivation persists
expected: Toggling a muted category back on and reloading shows it on again.
result: [pending]

### 4. Cross-user isolation
expected: User A muting a category does not affect User B's preferences for the same category/tenant.
result: [pending]

### 5. Direct API attempt to mute PRAZO_VENCIDO returns 400
expected: `PUT /notificacoes/preferencias/PRAZO_VENCIDO` returns 400, not 200.
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps

All 5 items blocked by the same confirmed environment limitation as Phases 87/89/91/92 (MINIO_ENDPOINT required env var with no default, blocking local backend startup) — not a functional gap in this phase's code. All static/code-level checks (15/15 must-haves, the critical choke-point property, 29/29 unit tests, compile, build, typecheck, lint) passed. Consistent with the user's established decision to continue without live validation for this recurring, pre-existing blocker.
