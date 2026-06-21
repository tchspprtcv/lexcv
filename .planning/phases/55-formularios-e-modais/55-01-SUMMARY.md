---
phase: 55-formularios-e-modais
plan: "01"
subsystem: frontend-forms
tags: [mobile, tailwind, responsive, forms]
dependency_graph:
  requires: []
  provides: [mobile-single-column-forms]
  affects: [clientes, processos, financeiro, settings, profile]
tech_stack:
  added: []
  patterns: [mobile-first responsive grid with md: breakpoint instead of sm:]
key_files:
  modified:
    - web/src/app/(dashboard)/clientes/novo/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx
    - web/src/app/(dashboard)/processos/novo/page.tsx
    - web/src/app/(dashboard)/processos/[id]/editar/page.tsx
    - web/src/app/(dashboard)/processos/[id]/page.tsx
    - web/src/app/(dashboard)/financeiro/novo/page.tsx
    - web/src/app/(dashboard)/financeiro/[id]/page.tsx
    - web/src/app/(dashboard)/clientes/page.tsx
    - web/src/app/(dashboard)/processos/page.tsx
    - web/src/app/(dashboard)/settings/page.tsx
    - web/src/components/profile/user-profile-form.tsx
    - web/src/components/profile/user-password-form.tsx
decisions:
  - "Use md: breakpoint (768px) instead of sm: (640px) to trigger two-column layout, giving mobile users single-column forms"
metrics:
  duration: 5m
  completed: "2026-06-21"
---

# Phase 55 Plan 01: Mobile Single-Column Forms Summary

**One-liner:** Replace `sm:grid-cols-2` with `grid-cols-1 md:grid-cols-2` across 12 form files so mobile screens (< 768px) render form fields in single column.

## What Was Done

Performed a bulk textual substitution in 12 frontend files — all form pages and listing/detail pages that used the `sm:grid-cols-2` Tailwind class. The old pattern triggered a two-column layout at 640px (sm breakpoint), which is too narrow for comfortable form use on phones. The new pattern enforces single-column layout below 768px (md breakpoint) and preserves the two-column desktop layout above it.

Total replacements: 24 occurrences across 12 files (12 files changed, 24 insertions, 24 deletions).

## Verification Results

- `grep -rn "sm:grid-cols-2"` in target directories: 0 matches in the 12 target files (remaining matches are in `dashboard/page.tsx` KPI grid and `clientes/merge/page.tsx`, both correctly excluded per plan scope)
- `grid-cols-1 md:grid-cols-2` pattern count: 24 occurrences (>= 22 required)
- `pnpm lint`: pre-existing errors in `use-toast.ts` (3x `@typescript-eslint/no-explicit-any`) and `<img>` warnings unrelated to this plan — no new errors introduced

## Deviations from Plan

None — plan executed exactly as written. Pre-existing lint errors in `use-toast.ts` are out of scope and were not touched.

## Known Stubs

None.

## Threat Flags

None — purely Tailwind className changes, no new network surface or auth paths introduced.

## Self-Check: PASSED

- All 12 target files modified and committed (hash: 4d2c08e)
- Zero `sm:grid-cols-2` in target files confirmed
- 24 new pattern occurrences confirmed
- No primitive UI files (dialog.tsx, input.tsx, button.tsx) modified
