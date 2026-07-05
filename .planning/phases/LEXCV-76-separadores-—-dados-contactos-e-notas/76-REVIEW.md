---
phase: LEXCV-76-separadores-—-dados-contactos-e-notas
reviewed: 2026-07-05T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
findings:
  critical: 1
  warning: 2
  info: 2
  total: 5
status: issues_found
---

# Phase LEXCV-76: Code Review Report

**Reviewed:** 2026-07-05T00:00:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Reviewed the restructured `clientes/[id]/page.tsx` after it was split into 7 button-toggle tabs (Dados, Contactos e Notas, Processos, Pareceres, Documentos Entregues, Documentos a Tratar, Deslocações), with NIF/documento_tipo/documento_numero consolidated into a new "Identificação" sub-section inside "Dados", and 5 placeholder tabs added for future phases.

The relocation of the Phase 74 `legacyDocumentoTipo` carve-out (NIF/documento_tipo/documento_numero fields, the amber legacy-value banner, and their `form.register`/`form.watch` bindings) is intact and correct — no field became unreachable or duplicated, and the `isEditing`/`editable` gating chain (Phase 75) still functions correctly across every card and sub-component in every tab.

However, the tab restructuring introduces a real state-consistency bug: three "add" dialogs (Documentos Entregues / Documentos a Tratar / Deslocações) that live inside the "Dados" tab's JSX now have their `open` boolean state hoisted to the parent component, while the `<Dialog>` JSX itself is unmounted whenever the user switches away from the "Dados" tab. Before this phase, the "Dados" content (including these dialogs) was always mounted, so this failure mode was unreachable; the tab restructuring makes it reachable. There is also a UX/correctness gap where clicking "Guardar" while parked on a non-"Dados" tab mid-edit can trigger validation errors that render nowhere on screen.

## Critical Issues

### CR-01: Add-dialogs re-open unexpectedly (with stale draft text) after a tab round-trip during edit

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:184-195, 791, 855, 906`
**Issue:**
`addDocEntreModal`, `addDocATratarModal`, and `addDeslocacaoModal` (plus their paired draft-value state `newDocEntre`/`newDocATratar`/`newDeslocacao`) are declared at the `ClienteDetailContent` level (lines 184-195), so they survive unmount/remount of any tab. But the `<Dialog>` elements that read/write them (lines 791, 855, 906) live inside the `tab === "dados"` branch (lines 442-1007).

Reachable sequence:
1. User clicks "Editar", stays on "Dados", clicks "Adicionar" under "Documentos Entregues" → `addDocEntreModal` becomes `true`, dialog opens; user types a description into `newDocEntre.descricao`.
2. User clicks the "Contactos e Notas" tab button (or any other tab) without closing the dialog. The entire `tab === "dados"` JSX subtree — including the mounted `<Dialog>` — unmounts. Because Radix `Dialog` is declaratively controlled by `open={addDocEntreModal}`, unmounting the tree does not reset that boolean; it simply stops rendering.
3. User clicks back to the "Dados" tab. The subtree remounts with `addDocEntreModal` still `true`, so the dialog **reopens automatically** with no user action, still showing the draft text typed in step 1.

This is a real, user-visible correctness bug: an "Adicionar Documento Entregue" (or "a Tratar"/"Deslocação") modal will spontaneously pop back open when the user merely browses tabs while editing, with previously abandoned draft input still present. It also means a half-filled draft can silently reappear and be confirmed later, adding a record the user thought they'd dismissed.

Before Phase 76, this was unreachable because the "Dados" content (and thus these dialogs) was always mounted — there was no tab to switch away to. The tab restructuring newly exposes this latent state-lifecycle mismatch.

**Fix:** Reset the three modal-open booleans (and their draft objects) whenever the tab changes away from `"dados"`, mirroring the existing `useEffect` pattern already used for `ResponsaveisCard`/`ClienteContactosCard`/`ClienteNotasCard` (lines 1211-1213, 1355-1361, 1557-1563):
```tsx
React.useEffect(() => {
  if (tab !== "dados") {
    setAddDocEntreModal(false);
    setAddDocATratarModal(false);
    setAddDeslocacaoModal(false);
    setNewDocEntre({ descricao: "", data: "" });
    setNewDocATratar({ descricao: "" });
    setNewDeslocacao({ descricao: "", local: "", data: "" });
  }
}, [tab]);
```
Place this alongside the other state declarations in `ClienteDetailContent`.

## Warnings

### WR-01: "Guardar" is reachable from every tab, but Dados-tab validation errors are invisible outside the Dados tab

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:359-367, 442-1007`
**Issue:** The header's "Guardar" button (`form.handleSubmit(onSubmit)`, line 364) is rendered outside the tab-conditional block and stays visible/enabled on every tab while `isEditing` is true. All `form.register`/`Controller`-bound fields and their inline error messages (`form.formState.errors.*`), however, only render inside `tab === "dados"` (lines 442-1007). If a user switches to, say, "Contactos e Notas" mid-edit and clicks "Guardar" while an invalid field exists (e.g. malformed NIF, or a `documento_numero` without `documento_tipo`), `handleSubmit` will short-circuit the submit and populate `form.formState.errors`, but the user sees no error anywhere on screen — no toast is raised for client-side validation failures (only `onSubmit`'s catch block raises a toast, and that only runs when validation passes). The user is left with a silently-unresponsive "Guardar" button and no feedback about why nothing happened.
**Fix:** Either (a) redirect the user to the "Dados" tab automatically when validation fails (e.g., in an `onError` callback passed to `form.handleSubmit(onSubmit, onError)` that calls `setTab("dados")`), or (b) surface a toast summarizing that there are unresolved errors on the "Dados" tab. Example:
```tsx
<Button
  type="button"
  onClick={form.handleSubmit(onSubmit, () => {
    setTab("dados");
    toast.error("Existem campos por corrigir no separador Dados.");
  })}
  disabled={isSaving}
>
  {isSaving ? "A guardar..." : "Guardar"}
</Button>
```

### WR-02: `ClienteDetailContent` has grown into a ~950-line component mixing tab-shell, form, and three intake sub-flows

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:110-1061`
**Issue:** `ClienteDetailContent` (lines 110-1061) already carried substantial complexity from Phase 75 (form state, legacy-documento_tipo carve-out, three intake-list mini-CRUDs). Phase 76 adds a fourth axis (tab state) on top without extracting the "Dados" tab body (roughly lines 442-1007, ~565 lines) into its own component. The result is a single function component that owns: form lifecycle, three modal/list intake flows, tipo-change confirmation dialog, and now tab navigation — all interleaved in one JSX tree. This raises the risk of exactly the kind of cross-cutting state-lifecycle bug flagged in CR-01, and will keep compounding as future phases add real content to the five placeholder tabs.
**Fix:** Consider extracting the "Dados" tab body (and its associated local dialog state) into a dedicated `DadosTab` component that receives `form`, `isEditing`, the intake-list state/handlers, and `legacyDocumentoTipo` as props. This is not required for this phase but should be considered before more real content lands in the placeholder tabs.

## Info

### IN-01: Five placeholder tab branches are visually/behaviorally identical — acceptable but worth confirming intent

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1027-1036, 1063-1074`
**Issue:** `tab === "processos" | "pareceres" | "documentosEntregues" | "documentosATratar" | "deslocacoes"` all render the same `<PlaceholderEmBreve />` with no differentiation (not even which tab is active in the copy). This matches the PATTERNS.md-sanctioned design (a shared placeholder component), so it is not a defect, but note that a future phase implementing one of these tabs must remember to add a new conditional arm rather than accidentally leaving it caught by a catch-all — there currently is no catch-all (the chain ends in explicit `: null`), so a forgotten arm would silently render nothing rather than the placeholder or new content. Low risk given the explicit exhaustive chain, but worth a mental note.
**Fix:** No action required now. When implementing a real tab, double check the specific `tab === "..."` arm is replaced rather than left to fall through.

### IN-02: `legacyDocumentoTipo` is not reset in `onCancel`

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:308-324`
**Issue:** `onCancel` resets form values and the three intake lists but does not call `setLegacyDocumentoTipo(null)` or recompute it. In practice this is harmless today because `legacyDocumentoTipo` is derived purely from `cliente.data` (server state) in the `useEffect` at lines 222-241, and `cliente.data` does not change merely by cancelling an edit, so the previously-computed value remains correct. Flagging only because it's a slightly fragile implicit invariant (relies on the effect's dependency array staying `[cliente.data, form, buildDefaultValues]` and never being recomputed from form state) — a future edit that tries to derive `legacyDocumentoTipo` from form values instead of `cliente.data` would silently break this.
**Fix:** No change required. If this logic is ever touched again, consider adding a comment noting the invariant that `legacyDocumentoTipo` must only ever be derived from server data (`cliente.data`), never from live form values.

---

_Reviewed: 2026-07-05T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
