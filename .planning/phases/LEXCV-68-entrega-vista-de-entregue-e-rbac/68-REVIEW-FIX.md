---
phase: 68-entrega-vista-de-entregue-e-rbac
fixed_at: 2026-07-01T00:00:00Z
review_path: .planning/phases/LEXCV-68-entrega-vista-de-entregue-e-rbac/68-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 68: Code Review Fix Report

**Fixed at:** 2026-07-01
**Source review:** .planning/phases/LEXCV-68-entrega-vista-de-entregue-e-rbac/68-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (Critical + Warning; Info items IN-01/IN-02/IN-03 out of scope for this run)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: Entrega dialog can be confirmed with a stale/empty version list if `versoes` hasn't loaded yet

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** f3a7b00
**Applied fix:** `showEntregarTrigger` now also requires `!versoes.isLoading`, matching the exact fix suggested in the review:
```tsx
const showEntregarTrigger =
  !permissions.isLoading &&
  !versoes.isLoading &&
  canEditPareceres &&
  isResponsavelOuAdmin &&
  !isConcluido;
```
This prevents the "Entregar Parecer" trigger (and thus the dialog) from becoming visible while the version list is still in flight, eliminating the empty-selector window described in the finding.

### WR-02: Escape key / overlay click can dismiss the entrega AlertDialog mid-mutation

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** f3a7b00
**Applied fix:** Added an `onOpenChange` guard on `AlertDialog` that ignores close attempts while `entregar.isPending`, plus explicit `onEscapeKeyDown`/`onPointerDownOutside` handlers on `AlertDialogContent` that call `preventDefault()` during the mutation:
```tsx
<AlertDialog
  open={confirmOpen}
  onOpenChange={(next) => {
    if (entregar.isPending) return;
    setConfirmOpen(next);
  }}
>
  ...
  <AlertDialogContent
    onEscapeKeyDown={(e) => { if (entregar.isPending) e.preventDefault(); }}
    onPointerDownOutside={(e) => { if (entregar.isPending) e.preventDefault(); }}
  >
```
Verified `AlertDialogContent` (in `web/src/components/ui/alert-dialog.tsx`) spreads `...props` onto the underlying Radix `AlertDialogPrimitive.Content`, so these handlers are correctly forwarded. This closes the dismiss-while-pending window on both the top-level open state and the Radix dismissal events, in addition to the already-disabled Cancel button.

**Correction (post-fix, caught by `tsc --noEmit` in commit `615166b`):** the `onPointerDownOutside` handler above does not actually type-check — this project's `@radix-ui/react-alert-dialog` version explicitly `Omit`s `onPointerDownOutside`/`onInteractOutside` from `AlertDialogContentProps` (Radix deliberately disallows outside-click dismissal for alert dialogs by design, so no guard was needed for that vector in the first place). The code-fixer's worktree run couldn't catch this because `node_modules` wasn't available there for type-checking (Tier 1 verification only). The prop was removed; `onEscapeKeyDown` (which IS supported) was kept with an explicit `KeyboardEvent` type instead of implicit `any`. WR-02 remains fixed — the escape-key vector was the real one; the pointer-down-outside vector was never actually dismissable in this Radix version.

### WR-03: `ParecerEntregueBlock` can't distinguish "still loading" from "version genuinely missing"

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** f3a7b00
**Applied fix:** Threaded `versoes.isLoading` down to `ParecerEntregueBlock` as a new `isLoading` prop (the caller at the `isConcluido` branch already has access to `versoes.isLoading` from the parent `useParecerVersoes` query) and branched the render on it before falling back to a not-found state:
```tsx
{isLoading ? (
  <p className="text-sm text-slate-500 dark:text-slate-400">A carregar versão final...</p>
) : !versaoFinal ? (
  <p className="text-sm text-red-600">Não foi possível localizar a versão final entregue.</p>
) : (
  /* ... */
)}
```
This is not currently reachable given today's backend guarantees, but hardens the component against future pagination/soft-delete changes to `versoes`, per the review's own note.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-01_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
