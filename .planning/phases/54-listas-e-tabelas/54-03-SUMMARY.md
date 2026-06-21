---
phase: "54-listas-e-tabelas"
plan: 03
subsystem: "frontend/ui"
tags: ["mobile", "responsive", "documentos", "financeiro", "badge", "TAB-01"]
dependency_graph:
  requires: ["54-02"]
  provides: ["TAB-01-complete"]
  affects: ["web/src/app/(dashboard)/documentos/page.tsx", "web/src/app/(dashboard)/financeiro/page.tsx"]
tech_stack:
  added: []
  patterns: ["hidden md:block / md:hidden responsive split", "Badge component for status indicators"]
key_files:
  created: []
  modified:
    - "web/src/app/(dashboard)/documentos/page.tsx"
    - "web/src/app/(dashboard)/financeiro/page.tsx"
decisions:
  - "Documentos mobile cards include delete button by reusing useDeleteDocumento hook directly (same pattern as DocumentoRow), rather than omitting it"
  - "Financeiro mobile cards compute status via calcHonorarioStatus(h.totalPago, h.valorTotal) — consistent with desktop table, no new status field dependency"
  - "Desktop statusBadgeClass spans in Financeiro left untouched; Badge component used only in mobile cards"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-21"
  tasks_completed: 3
  files_modified: 2
---

# Phase 54 Plan 03: Mobile Cards for Documentos and Financeiro Summary

One-liner: Mobile card views added to Documentos (nome, tipo Badge, processo, data, download, apagar) and Financeiro (processo, cliente, valor, Badge de estado green/blue/amber, data) completing TAB-01 coverage across all four list pages.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Mobile cards for documentos/page.tsx | 5466016 | documentos/page.tsx |
| 2 | Mobile cards for financeiro/page.tsx with Badge | 5466016 | financeiro/page.tsx |
| 3 | Lint verification and SUMMARY | 5466016 | 54-03-SUMMARY.md |

## What Was Built

### Documentos page (`documentos/page.tsx`)
- Desktop table wrapped in `<div className="hidden md:block">` — no visual change on ≥768px
- New `<div className="md:hidden divide-y ...">` with `DocumentoMobileCard` component per document
- Mobile card fields: nome (link to detail), tipo (Badge variant="blue"), processoId, createdAt formatted with `pt-CV` locale, download link (`/api/v1/documentos/{id}/download`), apagar button (if `canEditDocumentos`)
- Delete logic: new `DocumentoMobileCard` component uses `useDeleteDocumento(id)` hook directly — same pattern as `DocumentoRow`, no duplication of business logic
- `Badge` imported from `@/components/ui/badge`

### Financeiro page (`financeiro/page.tsx`)
- Desktop table wrapped in `<div className="hidden md:block">` — no visual change on ≥768px
- New `<div className="md:hidden divide-y ...">` with inline card per honorário
- Mobile card fields: processo label (numero ?? titulo ?? fallback), clienteNome, valorTotal formatted as CVE currency, Badge de estado (green=Pago, blue=Parcialmente Pago, amber=Pendente), dataAcordo
- Status computed via `calcHonorarioStatus(h.totalPago, h.valorTotal)` — consistent with desktop
- Desktop `statusBadgeClass` spans left intact; `<Badge>` used only in mobile cards
- `Badge` imported from `@/components/ui/badge`

## TAB-01 Coverage Audit (complete)

| Page | Plan | Status |
|------|------|--------|
| Clientes | 54-02 | Delivered |
| Agenda | 54-02 | Delivered |
| Documentos | 54-03 | Delivered |
| Financeiro | 54-03 | Delivered |

TAB-01 is fully complete across all four pages.

## TAB-02 Coverage Audit (complete)

| Feature | Plan | Status |
|---------|------|--------|
| Partes em Processos | 54-01 | Delivered |
| Fases em Processos | 54-01 | Delivered |

## Deviations from Plan

None — plan executed exactly as written. The `DocumentoMobileCard` approach (reusing the hook) was explicitly anticipated by the plan's note about checking for available delete logic in page scope; `useDeleteDocumento` was already imported at the page module level, making it straightforward to use.

## Known Stubs

None. All card fields are wired to real data from TanStack Query hooks.

## Self-Check: PASSED

- `grep -c "md:hidden" documentos/page.tsx` → 1
- `grep -c "hidden md:block" documentos/page.tsx` → 1
- `grep -c "md:hidden" financeiro/page.tsx` → 1
- `grep -c "hidden md:block" financeiro/page.tsx` → 1
- `grep -c "Badge" financeiro/page.tsx` → 5
- Commit 5466016 exists: feat(54): mobile cards for Documentos and Financeiro lists (TAB-01)
- Lint: no errors in documentos/page.tsx or financeiro/page.tsx (pre-existing errors in unrelated files scoped out)
