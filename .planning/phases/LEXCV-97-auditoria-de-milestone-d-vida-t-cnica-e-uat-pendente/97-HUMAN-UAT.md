---
status: partial
phase: 97-auditoria-de-milestone-d-vida-t-cnica-e-uat-pendente
source: [97-VERIFICATION.md]
started: 2026-07-14T21:15:00.000Z
updated: 2026-07-14T21:30:00.000Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Print-preview layout / no-crash render checks (Phase 82/84)
expected: `/processos/{id}/termo-honorarios` produces a sane A4 print layout (Phase 84 scenario 3); a formalized processo's `/financeiro` page renders `valorTotal` as blank/"A confirmar" (not the literal string "null") and does not crash (Phase 82 scenario 1).
result: [pending]

### 2. Cross-surface real-time reflection + WR-01/WR-02 visuals (Phase 89)
expected: Marking a notification read from `/notificacoes` updates the bell dropdown badge/list without a manual refresh (scenario 8); a synthetic non-clickable notification renders as plain text, not a link (WR-01 same-origin link-guard, scenario 10); paginating the notification list transitions without a visible flash (WR-02, scenario 11). All three are already backed by 89-VERIFICATION.md's 16/16 behavioral tests — only the live rendered/timing behavior remains open.
result: [pending]

### 3. RBAC second-user walkthrough (Phase 84 scenario 7)
expected: A `processos:view`-only user sees no CRUD affordances anywhere on the ficha; a `processos:edit`-without-`documentos:edit` user sees full CRUD on other tabs but a read-only Documentos list. Requires a second, differently-scoped login not available to any automated executor in this session.
result: [pending]

## Summary

total: 3
passed: 0
issues: 0
pending: 3
skipped: 0
blocked: 0

## Gaps

Item 1 from the original verifier list ("Registo Comercial" visual spot-check) was closed during this verification pass — see `97-VERIFICATION.md`'s Post-Verification Addendum: no `REG_COMERCIAL`-typed test client exists in this dev DB, but the label map was confirmed correct by direct source read and the `getDocumentoTipoLabel` wiring was confirmed live-correct by rendering a different client (PASSAPORTE → "Passaporte", translated).

The 3 remaining items are all pre-existing NEEDS-HUMAN-VISUAL carries from `97-UAT.md` (itself closing UAT gaps from phases 82/84/89) — none indicate a functional defect; all are backed by passing code-level/behavioral evidence and only the live rendered/timing/second-session behavior remains unobserved. This matches the phase goal's own explicit allowance: UAT items may be "fechado ou explicitamente contornado... com a razão registada explicitamente" rather than requiring live closure of every single item.
