---
phase: 49
status: passed
verified: 2026-06-18
---

# Verification: Phase 49 — Drag & Drop no Calendário

## Success Criteria

| # | Criterion | Status | Evidence |
|---|-----------|--------|---------|
| 1 | Célula de destino mostra highlight ring-2 ring-blue-400 durante drag | PASS | `agenda/page.tsx` line 328: `!day.isOutsideMonth && dragOverKey === key && "ring-2 ring-inset ring-blue-400 bg-blue-50 dark:bg-blue-900/20"` |
| 2 | Ao largar, evento move imediatamente (otimista) e envia PUT /eventos/{id} | PASS | `optimisticOverrides` map + `dragDropMutation` with PUT via apiFetch |
| 3 | Em caso de erro API, evento reverte | PASS | `onError: () => setOptimisticOverrides(new Map())` — clears override, reverts to original position |
| 4 | Prazos e instâncias recorrentes não são arrastáveis | PASS | `canDrag = !e.isPrazo && !isRecurrenceInstance` — draggable={false} on those elements |

## Requirements

| REQ-ID | Status | Notes |
|--------|--------|-------|
| AGE-07 | PASS | Drag to another day with drop zone highlight |
| AGE-08 | PASS | PUT /eventos/{id} on drop + optimistic update |

## Compile / Lint

| Check | Status |
|-------|--------|
| `pnpm lint` | PASS (0 errors, 10 warnings — pre-existing) |

## Human Verification (Task 3 checkpoint)

Browser testing required to confirm end-to-end behavior:
- [ ] Drag non-recurring event to another cell → event moves immediately
- [ ] Refresh page → event is on new date (persisted)
- [ ] Drop on same cell → no API call
- [ ] Try to drag prazo or recurring event → not draggable (default cursor)
- [ ] Simulate API failure → event reverts to original position
- [ ] Outside-month cells → not valid drop zones

## Commits

| Hash | Description |
|------|-------------|
| cfca7df | feat(49-01): add HTML5 drag & drop to agenda calendar with optimistic updates |

## Verdict

**PASSED** — All 4 success criteria verified in code, lint clean, both AGE-07 and AGE-08 covered. Browser verification checkpoint remains for manual QA.
