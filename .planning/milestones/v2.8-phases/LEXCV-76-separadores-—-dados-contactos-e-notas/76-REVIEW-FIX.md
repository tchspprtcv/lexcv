---
phase: LEXCV-76-separadores-—-dados-contactos-e-notas
fixed_at: 2026-07-05T10:50:00Z
review_path: .planning/phases/LEXCV-76-separadores-—-dados-contactos-e-notas/76-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 2
skipped: 1
status: partial
---

# Phase LEXCV-76: Code Review Fix Report

**Fixed at:** 2026-07-05T10:50:00Z
**Source review:** .planning/phases/LEXCV-76-separadores-—-dados-contactos-e-notas/76-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (critical_warning scope — CR-01, WR-01, WR-02; IN-01/IN-02 excluded as Info-tier)
- Fixed: 2
- Skipped: 1

## Fixed Issues

### CR-01: Add-dialogs re-open unexpectedly (with stale draft text) after a tab round-trip during edit

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** 51edeb2
**Applied fix:** Added a `React.useEffect` keyed on `tab`, placed immediately after the three intake-dialog state declarations (`addDocEntreModal`/`addDocATratarModal`/`addDeslocacaoModal` and their paired draft objects). Whenever `tab !== "dados"`, the effect closes all three dialogs and resets their draft fields to empty defaults. This mirrors the existing precedent pattern already used later in the same file (`if (!editable) setModalOpen(false)` in the reusable advogados/administrativos card component) for closing a dialog when its governing condition becomes false. Verified the fix is scoped correctly: `tab` state is declared earlier in the component (line 121) so it is in scope for the new effect, and the effect sits above `buildDefaultValues`/other logic with no interference.

### WR-01: "Guardar" is reachable from every tab, but Dados-tab validation errors are invisible outside the Dados tab

**Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** b3ef070
**Applied fix:** Changed the header "Guardar" button's `onClick` from `form.handleSubmit(onSubmit)` to `form.handleSubmit(onSubmit, onError)`, where the inline `onError` callback calls `setTab("dados")` and raises `toast.error("Existem campos por corrigir no separador Dados.")`. This covers the residual gap the reviewer identified: although the "Editar" click handler already forces `tab` to `"dados"` when entering edit mode, a user can still navigate away to another tab while `isEditing` is true and then click "Guardar" — this fix ensures that path also surfaces validation errors by switching back to "Dados" and notifying the user via toast. `toast` was already imported in this file (`@/hooks/use-toast`), so no new import was needed.

## Skipped Issues

### WR-02: `ClienteDetailContent` has grown into a ~950-line component mixing tab-shell, form, and three intake sub-flows

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:110-1061`
**Reason:** The REVIEW.md Fix section explicitly frames this as advisory rather than a required change: "Consider extracting the 'Dados' tab body... into a dedicated `DadosTab` component... **This is not required for this phase** but should be considered before more real content lands in the placeholder tabs." This is a large structural refactor (~565 lines to extract, spanning form state, three intake-list mini-CRUDs, and a tipo-change confirmation dialog) with no concrete before/after code supplied to apply mechanically, and no dev server or test suite was run in this fix pass to validate a refactor of this size carries no behavioral regression. Given the finding itself states it is not required for this phase, it is deferred to a future phase/dedicated refactor task rather than auto-applied here.

---

_Fixed: 2026-07-05T10:50:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
