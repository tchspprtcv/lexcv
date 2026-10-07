---
phase: 137-pdf-armazenamento-email-e-relat-rio
plan: 21
subsystem: web/documentos-fiscais
tags: [documentos-fiscais, detail-view, list-view, email-delivery, download-buttons, dialog]
requires: [137-20]
provides:
  - EntregaEmailCard (detail card rendering delivery state, recipient, attempts, timestamps and last error per UI-SPEC 1b)
  - ReenviarEmail (confirmation dialog with exact financeiro:edit gate, double-submit guard, lifted open state, UI-SPEC 1d copy)
  - Document detail page wiring (PDF/XML download buttons, EntregaEmailCard)
  - Document list page wiring (Email status column with EntregaEmailBadge, compact Ações column with download buttons)
affects: [137-22, 137-23]
tech-stack:
  added: []
  patterns: [lifted dialog state, exact RBAC gating, status badge integration, TanStack Table columns]
key-files:
  created:
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/entrega-email-card.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/reenviar-email.tsx
  modified:
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/[id]/page.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/columns.tsx
    - web/src/app/(dashboard)/financeiro/documentos-fiscais/page.tsx
decisions:
  - "EntregaEmailCard is read-only and displays only backend-sanitized delivery fields"
  - "ReenviarEmail uses exact podeReenviarEmail (financeiro:edit) and backend reenviavel flag, preventing double-clicks and Esc/overlay closure while pending"
  - "Detail header positions DescarregarDocumentoBotoes alongside existing credit note issuance"
  - "List columns include Email status badge and compact PDF/XML action buttons"
metrics:
  duration: ~25min
  completed: 2026-10-07
  tasks: 2
  files: 5
---

# Phase 137 Plan 21: Document Detail & List UI Integration Summary

Integrated Phase 137 delivery tracking, manual resending dialog, and PDF/XML download buttons into the fiscal document list and detail screens.

## Key Deliverables

1. **`EntregaEmailCard`**:
   - Renders "Entrega por email" card under communication card.
   - Shows badge, recipient, attempt count with helper text, timestamps formatted in `Atlantic/Cape_Verde`, and neutral status banner for errors.
   - Displays resend trigger only for users with exact `financeiro:edit` permission and when `entregaEmail.reenviavel` is true.

2. **`ReenviarEmail` Dialog**:
   - Implements full confirmation dialog with lifted state to prevent unmounting during close animations.
   - Guarded against double-clicks and keyboard dismissal during submission.
   - Provides clear inline error feedback matching UI-SPEC codes and returns focus to card heading upon completion.

3. **Detail and List Wiring**:
   - Detail page (`documentos-fiscais/[id]/page.tsx`): Download buttons in header, delivery card inserted before links.
   - List page (`documentos-fiscais/columns.tsx`, `page.tsx`): Added Email badge column, compact download buttons column, updated description.

## Verification

- `tsc --noEmit`: Clean.
- `vitest run`: **353/353 passed**.
- `verify:documentos-fiscais`: Passed.
- Lint and security checks clean (no `dangerouslySetInnerHTML`).

## Self-Check: PASSED
