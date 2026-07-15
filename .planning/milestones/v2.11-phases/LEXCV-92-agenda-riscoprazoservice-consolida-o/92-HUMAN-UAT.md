---
status: partial
phase: 92-agenda-riscoprazoservice-consolida-o
source: [92-VERIFICATION.md]
started: 2026-07-13T18:00:00.000Z
updated: 2026-07-13T18:00:00.000Z
---

## Current Test

[awaiting human/live testing — blocked by the project's known, pre-existing MINIO_ENDPOINT environment gap (same blocker as Phases 87/89), not a code defect]

## Tests

### 1. "Urgentes" counter on Agenda reflects backend risco, not the old prioridade proxy
expected: The Agenda page's "Urgentes" KPI count changes based on `risco === "proximo" || "vencido"` for both Prazos and Eventos, matching Dashboard/RiscoPrazoService, not the removed `prioridade === "ALTA"` heuristic.
result: [pending]

### 2. No network calls to the removed /eventos/upcoming endpoint
expected: Browser network tab shows zero requests to `/api/v1/eventos/upcoming` when using the Agenda page.
result: [pending]

## Summary

total: 2
passed: 0
issues: 0
pending: 2
skipped: 0
blocked: 0

## Gaps

Both items are blocked by the same confirmed environment limitation as Phase 91 (MINIO_ENDPOINT required env var with no default, blocking local backend startup) — not a functional gap in this phase's code. All static/code-level checks (8/8 must-haves, compile, build, typecheck, lint, grep-confirmed removal of all client-side risk computation) passed. Consistent with the user's decision on Phase 91 to continue without live validation for this recurring, pre-existing blocker.
