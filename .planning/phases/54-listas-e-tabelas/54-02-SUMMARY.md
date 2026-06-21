---
phase: "54-listas-e-tabelas"
plan: 02
subsystem: "frontend/ui"
tags: ["mobile", "responsive", "clientes", "agenda", "cards"]
dependency_graph:
  requires: ["54-01"]
  provides: ["mobile-cards-clientes", "mobile-events-agenda"]
  affects: ["web/src/app/(dashboard)/clientes/page.tsx", "web/src/app/(dashboard)/agenda/page.tsx"]
tech_stack:
  added: []
  patterns: ["md:hidden / hidden md:block responsive split", "inline mobile card list without new component (YAGNI)"]
key_files:
  modified:
    - web/src/app/(dashboard)/clientes/page.tsx
    - web/src/app/(dashboard)/agenda/page.tsx
decisions:
  - "Used c.ativo (boolean) for badge state instead of estadoConta string (not present in Cliente type)"
  - "Agenda mobile list placed above calendar legend using md:hidden — avoids duplicating sidebar component"
  - "No new components created — mobile cards implemented inline (YAGNI)"
metrics:
  duration: "~10 min"
  completed: "2026-06-21"
  tasks_completed: 3
  files_modified: 2
---

# Phase 54 Plan 02: Mobile Cards for Clientes and Agenda Summary

**One-liner:** Responsive split added to Clientes (md:hidden card list + hidden md:block table) and Agenda (md:hidden upcoming events above calendar).

## What Was Built

### Task 1: Mobile cards in clientes/page.tsx

The existing `<div className="overflow-hidden"><Table>...</Table></div>` block was wrapped with `<div className="hidden md:block">`. Immediately before it (still inside `<CardContent className="p-0">`), a `<div className="md:hidden divide-y divide-slate-200 dark:divide-slate-800">` block was added that maps over `clientes.data` and renders a card per client.

Each card shows:
- Avatar with initials (blue, same styling as desktop table)
- Nome as a bold link to `/clientes/[id]`
- NIF (if present), 11px subdued text
- Ativo/Inativo badge using `Badge variant="green"` / `variant="gray"` (from `c.ativo` boolean)
- Telefone (if present)
- Ver button (Eye icon, 36x36 touch target) and, if `canEditClientes`, Editar button (Pencil icon)

Note: The `Cliente` type has `ativo?: boolean`, not `estadoConta: string`. The badge uses `c.ativo` accordingly.

### Task 2: Responsive visibility in agenda/page.tsx

The Agenda page already uses `grid gap-6 lg:grid-cols-[1fr_360px]` — the right sidebar with "Proximos Eventos" stacks below the calendar on mobile. To avoid users having to scroll past the full 7-column calendar grid to see upcoming events, a `md:hidden` upcoming events block was added **above** the calendar (between the legend chips and the calendar Card). It renders the same card style as the existing sidebar using `getCategoria()` and `processoLabelById`, with category color, time, titulo, and processo label. This block is only shown when `upcoming.length > 0`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] estadoConta field does not exist on Cliente type**
- **Found during:** Task 1
- **Issue:** Plan specified `badge for estadoConta` but the `Cliente` interface has no `estadoConta` field — only `ativo?: boolean`
- **Fix:** Used `c.ativo` boolean with `variant="green"` for active / `variant="gray"` for inactive
- **Files modified:** web/src/app/(dashboard)/clientes/page.tsx
- **Commit:** 1d90992

## Verification Results

- `grep -c "md:hidden" clientes/page.tsx` → 1 (passes)
- `grep -c "hidden md:block" clientes/page.tsx` → 1 (passes)
- `grep -c "md:hidden" agenda/page.tsx` → 1 (passes)
- Lint: no errors in modified files (pre-existing errors in use-toast.ts and dashboard-shell are unrelated)

## Known Stubs

None. Cards display live data from the same `clientes.data` and `filteredEvents` arrays used by the desktop views.

## Threat Flags

No new trust boundaries. Cards render the same data as the desktop table, gated by the same `canEditClientes` permission already in scope.

## Self-Check: PASSED

- web/src/app/(dashboard)/clientes/page.tsx — modified, committed at 1d90992
- web/src/app/(dashboard)/agenda/page.tsx — modified, committed at 1d90992
- Commit 1d90992 exists in git log
