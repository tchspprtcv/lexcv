---
phase: 67-elabora-o-e-versionamento
fixed_at: 2026-07-01T15:10:00Z
review_path: .planning/phases/LEXCV-67-elabora-o-e-versionamento/67-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 67: Code Review Fix Report

**Fixed at:** 2026-07-01T15:10:00Z
**Source review:** .planning/phases/LEXCV-67-elabora-o-e-versionamento/67-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (Critical: 0, Warning: 3; Info out of scope for this run)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: `form.reset` clears RHF state but not the underlying native file input

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** c78d449
**Applied fix:** Added a `fileInputKey` state counter incremented on successful submit, keyed onto `FileDropZone` via a `key` prop. This force-remounts `FileDropZone` (and its uncontrolled native `<input type="file">`) after each successful version submission, guaranteeing the native input's stale `.files` reference is cleared alongside the RHF `file` field reset.

### WR-02: `isResponsavelOuAdmin`/`showNovaVersaoForm` computed without checking `permissions.isLoading`

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** 979cf8e
**Applied fix:** Added `!permissions.isLoading` as an explicit precondition to `showNovaVersaoForm`, and added a loading-skeleton branch (matching the existing `animate-pulse` pattern used elsewhere on the page) that renders in place of the Nova Versão / "já entregue" card while `permissions.isLoading` is true. This prevents the card from popping in/out as `/auth/me` resolves after the parecer detail query.

### WR-03: XHR upload has no timeout/ontimeout handling, causing hung uploads to never settle the mutation

**Files modified:** `web/src/hooks/use-pareceres.ts`
**Commit:** 681bdd7
**Applied fix:** Added `xhr.timeout = 60_000` and an `xhr.ontimeout` handler that rejects the promise with a clear Portuguese-language error message ("Tempo limite excedido ao enviar o ficheiro. Tente novamente."), so a stalled request settles the mutation and the submit button recovers instead of staying stuck on "A submeter...". Note: `use-documentos.ts`'s `useUploadDocumentoComProgresso` has the same pre-existing gap and was left untouched per the finding's own recommendation to track it as a separate shared follow-up rather than fix only the pareceres copy in this run.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-01T15:10:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
