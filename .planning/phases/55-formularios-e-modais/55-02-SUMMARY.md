---
phase: 55
plan: "02"
subsystem: frontend/mobile-ux
tags: [mobile, dialogs, touch-targets, accessibility]
key-files:
  modified:
    - web/src/app/(dashboard)/financeiro/[id]/page.tsx
    - web/src/app/(dashboard)/processos/[id]/page.tsx
    - web/src/app/(dashboard)/clientes/novo/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
    - web/src/app/(dashboard)/processos/novo/page.tsx
    - web/src/app/(dashboard)/financeiro/novo/page.tsx
    - web/src/components/profile/user-profile-form.tsx
decisions:
  - "Bottom-sheet classes applied only to form DialogContent, not AlertDialogContent or simple confirm dialogs"
  - "Touch target classes (max-sm:h-12 max-sm:text-base) applied to form inputs in creation/edit pages, not to filter/search inputs like timeline date pickers"
metrics:
  completed: "2026-06-21"
---

# Phase 55 Plan 02: Bottom-sheet Dialogs Mobile + 48px Touch Targets Summary

Bottom-sheet dialog positioning for mobile viewports and 48px minimum touch targets on creation/edit form inputs across the LexCV frontend.

## Tasks Completed

### Task 1: Bottom-sheet dialogs (FORM-02)

Added mobile bottom-sheet classes to 3 form `DialogContent` components:

- `financeiro/[id]/page.tsx` — "Editar honorário" dialog (1 DialogContent)
- `processos/[id]/page.tsx` — Justificativa/transition dialog and Novo Prazo dialog (2 DialogContent)

Classes added: `max-sm:fixed max-sm:bottom-0 max-sm:left-0 max-sm:right-0 max-sm:top-auto max-sm:translate-x-0 max-sm:translate-y-0 max-sm:rounded-t-xl max-sm:rounded-b-none max-sm:w-full max-sm:max-w-none`

`AlertDialogContent` components (confirm/delete dialogs) were intentionally left unmodified.

### Task 2: 48px touch targets (FORM-03)

Added `max-sm:h-12 max-sm:text-base` to `<Input` elements and `max-sm:min-h-[48px]` to `<Button type="submit"` elements across 5 files:

- `clientes/novo/page.tsx` — 8 inputs + 1 submit button
- `clientes/[id]/editar/page.tsx` — 8 inputs + 1 submit button
- `processos/novo/page.tsx` — 6 inputs (intake form + decisao form) + 1 submit button
- `financeiro/novo/page.tsx` — 3 inputs + 1 submit button
- `components/profile/user-profile-form.tsx` — 3 inputs (nome, email, telefone) + 1 submit button

Timeline date filter inputs in `processos/[id]/page.tsx` were intentionally excluded (they are filter controls, not form creation inputs).

## Deviations from Plan

**1. [Rule 1 - Bug] Duplicate className prop on documento_numero Input in clientes/novo**
- **Found during:** Build verification after Task 2
- **Issue:** The script inserted a second `className` prop on the `documento_numero` Input, resulting in a TypeScript "JSX elements cannot have multiple attributes with the same name" error
- **Fix:** Merged both class values into a single `className` prop and removed the duplicate
- **Files modified:** `web/src/app/(dashboard)/clientes/novo/page.tsx`

## Self-Check: PASSED

- Build succeeded (`pnpm build`) with no TypeScript errors
- Commit `17d52e9` contains all 7 modified files
- `grep -c "max-sm:fixed"` returns >= 1 in both dialog target files
