---
phase: 65-funda-o-listagem-e-detalhe
fixed_at: 2026-07-01T11:40:00Z
review_path: .planning/phases/LEXCV-65-funda-o-listagem-e-detalhe/65-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 65: Code Review Fix Report

**Fixed at:** 2026-07-01T11:40:00Z
**Source review:** .planning/phases/LEXCV-65-funda-o-listagem-e-detalhe/65-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (Warning; Critical scope had 0 findings; Info out of scope per run request)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: `usePareceres` list loading/error incorrectly coupled to auxiliary `useClientes` query

**Files modified:** `web/src/app/(dashboard)/pareceres/page.tsx`
**Commit:** 4f994d2
**Applied fix:** Changed `isLoading`/`isError` to be derived strictly from `pareceres.isLoading`/`pareceres.isError`, removing the `clientes.isLoading`/`clientes.isError` coupling. `clienteNomeById.get(...) ?? s.clienteId` fallback (already present) now correctly handles the case where `clientes.data` is undefined or the clientes query failed.

### WR-02: No loading guard on `useAdminUsers()` before building `userNomeById` in detail page

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** c6be09e
**Applied fix:** Added a `resolveUserNome(userId)` helper that returns `"—"` while `adminUsers.isLoading` is true, and falls back to `userNomeById.get(userId) ?? userId` once loaded. Replaced both name-resolution call sites (advogado field in the metadata card, and `autorNome` in the versions timeline) to use this helper, eliminating the transient flash of raw UUIDs before names resolve.

### WR-04: `AnexoLink` double-toast risk on anexo download error

**Files modified:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx`
**Commit:** 42da94a
**Applied fix:** Removed the redundant `toast.error(msg)` call in `AnexoLink`'s `onDownload` catch block, since `apiFetch` already surfaces a toast for all non-401/403 error responses. The catch block is now a no-op comment noting this; `download.isPending` state (driving the button's disabled/label state) is still correctly managed by `useMutation` regardless. Removed the now-unused `toast` import from `@/hooks/use-toast`.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-01T11:40:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
