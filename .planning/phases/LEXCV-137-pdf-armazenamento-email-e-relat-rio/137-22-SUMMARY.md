---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 22
subsystem: web/clientes-financeiro-settings
tags: [clientes, financeiro, exportacao-mensal, settings, smtp-notice, read-only-tab]
requires: [137-20, 137-21]
provides:
  - ClienteDocumentosFiscaisTab (read-only and download-only fiscal documents tab on client detail page)
  - ExportarMesDialog (monthly fiscal export dialog with Cape Verde date defaults and validation)
  - Financeiro header trigger and disambiguated honorários CSV export
  - Updated settings email card with accurate helper copy and SMTP unconfigured notice
affects: [137-23, 137-24]
tech-stack:
  added: []
  patterns: [exact financeiro:view RBAC gating, read-only tab guarantees, Cape Verde timezone calendar calculation, neutral status notice]
key-files:
  created:
    - web/src/app/(dashboard)/clientes/[id]/documentos-fiscais-tab.tsx
    - web/src/app/(dashboard)/financeiro/exportar-mes-dialog.tsx
  modified:
    - web/src/app/(dashboard)/clientes/[id]/page.tsx
    - web/src/app/(dashboard)/financeiro/page.tsx
    - web/src/app/(dashboard)/settings/faturacao-email-card.tsx
    - web/src/components/ui/button.tsx
decisions:
  - "ClienteDocumentosFiscaisTab provides only consultation and downloads; no destructive operations or row actions exist"
  - "ExportarMesDialog delegates CSV generation exclusively to the backend endpoint"
  - "Settings email card accurately reflects live automatic sending behavior and warns when SMTP is unconfigured"
metrics:
  duration: ~20min
  completed: 2026-10-07
  tasks: 3
  files: 6
---

# Phase 137 Plan 22: Client Fiscal Tab, Monthly Export Dialog & Settings Notice Summary

Implemented the client-level fiscal documents tab, monthly CSV export dialog in Financeiro, and settings card updates.

## Key Deliverables

1. **`ClienteDocumentosFiscaisTab` in `web/src/app/(dashboard)/clientes/[id]/documentos-fiscais-tab.tsx`**:
   - Gated behind `podeLerDocumentosFiscais` (`financeiro:view`).
   - Renders `ModoSimuladoBanner`, link to filtered list in Financeiro, and a paginated data table showing number, date, type, total, communication state, email state, and compact download buttons.
   - Enforces read/download-only access (no edit, delete, upload, rename, or generic document hooks).

2. **`ExportarMesDialog` in `web/src/app/(dashboard)/financeiro/exportar-mes-dialog.tsx`**:
   - Gated by `canVerDocumentosFiscais` (`financeiro:view`) with outline trigger in Financeiro header.
   - Computes default month (previous month) and maximum month (current month) in `Atlantic/Cape_Verde`.
   - Downloads backend-generated CSV via `useExportarMesCsv` mutation.
   - Disambiguated existing honorários CSV button to "Exportar honorários (CSV)".

3. **Settings Email Card Updates**:
   - Updated helper copy to describe live automatic email sending.
   - Displays neutral `role="status"` banner when `smtpConfigurado === false`.

## Verification

- `tsc --noEmit`: Clean.
- `vitest run`: **353/353 passed**.
- `verify:faturacao` & `verify:documentos-fiscais`: Passed.
- Grep safety checks: No forbidden common document actions in client tab, no client-side CSV construction.

## Self-Check: PASSED
