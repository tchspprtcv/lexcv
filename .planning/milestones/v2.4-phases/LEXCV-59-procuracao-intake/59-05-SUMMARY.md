---
phase: LEXCV-59-procuracao-intake
plan: "05"
subsystem: web-frontend
tags: [clientes, procuracao, advogados, administrativos, upload, rbac]
dependency-graph:
  requires:
    - 59-03 (backend procuracao + advogados/administrativos endpoints)
    - 59-04 (frontend types, hooks: useUploadProcuracao, useDownloadProcuracao, useDeleteProcuracao, useClienteAdvogados, useAddAdvogado, useRemoveAdvogado, useClienteAdministrativos, useAddAdministrativo, useRemoveAdministrativo)
  provides:
    - Procuração upload/view/replace/remove UI on cliente detail page
    - Advogados/Administrativos list + add (modal) + remove UI on cliente detail page
  affects:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"
tech-stack:
  added: []
  patterns:
    - FileDropZone (web/src/components/shared/file-drop-zone.tsx) reused for procuracao upload/replace
    - Dialog (web/src/components/ui/dialog.tsx) reused for add-advogado/administrativo modal
    - Parameterized ResponsaveisCard component shared by Advogados and Administrativos cards (DRY: single implementation, hooks passed as props)
    - useAdminUsers (web/src/hooks/use-admin.ts) reused as the candidate-user picker source for the add modal
key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"
decisions:
  - "Reused existing useAdminUsers hook (typed against MockUser but calling the real /admin/users endpoint) as the user picker source for advogados/administrativos modal, instead of creating a new utilizadores hook — avoids duplicating an admin-list fetch."
  - "ResponsaveisCard is a single generic component parameterized by useList/useAdd/useRemove hooks, rendered twice (Advogados, Administrativos) to avoid duplicating list/add/remove UI logic."
  - "Procuração card shows FileDropZone for initial upload when no procuracao_key, and a smaller 'Substituir ficheiro' FileDropZone alongside View/Remove buttons when one exists."
metrics:
  duration: "~25min"
  completed: 2026-06-30
---

# Phase 59 Plan 05: Procuração + Advogados/Administrativos UI Summary

Added procuração upload/view/replace/remove and advogados/administrativos list/add/remove cards to the cliente detail page (`clientes/[id]/page.tsx`), wired to the hooks added in plan 59-04 and the backend endpoints added in plan 59-03.

## What was built

- **ProcuracaoCard**: shows upload dropzone when no procuração exists; once `procuracao_key` is set, shows a "Carregada" badge, "Ver / Download" button (fetches presigned URL via `useDownloadProcuracao` and opens in a new tab), a "Remover" button (confirms then calls `useDeleteProcuracao`), and a secondary dropzone to replace the file (`useUploadProcuracao`).
- **ResponsaveisCard** (generic, used for both Advogados and Administrativos): lists current users with name/email, a "Remover" button per row, and an "Adicionar" button that opens a `Dialog` with a `<select>` populated from `useAdminUsers()` filtered to exclude already-assigned users. Selecting a user and confirming calls the appropriate `useAdd*` hook; closing/cancelling resets state.
- Both new sections respect `canEditClientes` — view-only users see the lists but no upload/add/remove controls.
- Inserted as a new `grid gap-4 lg:grid-cols-3` row below the existing Contactos/Notas row on the cliente detail page.

## Deviations from Plan

None — plan 59-05-PLAN.md was not present on disk in this worktree (only 59-01 through 59-04 SUMMARY.md and 59-CONTEXT.md existed after merging master), so this plan was executed based on the task description provided in the orchestrator prompt (success criteria: procuração card, advogados card, administrativos card) cross-referenced against the hooks/types delivered in 59-04 and the backend endpoints delivered in 59-03.

### Auto-fixed Issues

**1. [Rule 3 - Blocking] No dedicated "utilizadores" hook existed for the add-advogado/administrativo picker**
- **Found during:** Implementing ResponsaveisCard's add modal
- **Issue:** The files_to_read list referenced an admin users endpoint reference, but no `useUtilizadores` hook existed in the codebase.
- **Fix:** Reused the existing `useAdminUsers()` hook from `web/src/hooks/use-admin.ts`, which already calls the real `/admin/users` backend endpoint (its TypeScript types reference `MockUser` from the legacy mock server but the runtime call is unaffected since `apiFetch` is generic).
- **Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx` (consumer only, no hook changes)
- **Commit:** 0844d1c

## Known Stubs

None — all three cards (Procuração, Advogados, Administrativos) are wired to live hooks/endpoints with no hardcoded/mock data paths.

## Build Verification

Build verification skipped — no `node_modules` present in this worktree (pnpm workspace did not propagate to the git worktree). Verified via grep-based checks instead:
- Confirmed all 9 newly-imported hooks (`useAddAdministrativo`, `useAddAdvogado`, `useClienteAdministrativos`, `useClienteAdvogados`, `useDeleteProcuracao`, `useDownloadProcuracao`, `useRemoveAdministrativo`, `useRemoveAdvogado`, `useUploadProcuracao`) are both imported and referenced in the page.
- Confirmed `Dialog`, `FileDropZone`, `Label`, `Badge`, `useAdminUsers` imports resolve to existing files read directly during this session.
- Flagging for orchestrator re-verification (`pnpm build` / `pnpm tsc --noEmit`) after merge into a tree with installed dependencies.

## Self-Check: PASSED

- FOUND: web/src/app/(dashboard)/clientes/[id]/page.tsx (modified, 883 lines, was 602)
- FOUND commit 0844d1c: feat(59-05): add procuracao, advogados, administrativos cards to cliente detail
