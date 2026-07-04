---
phase: LEXCV-75-componente-nico-view-edit
reviewed: 2026-07-04T00:00:00Z
depth: standard
files_reviewed: 2
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
findings:
  critical: 0
  warning: 5
  info: 4
  total: 9
status: issues_found
---

# Phase LEXCV-75: Code Review Report

**Reviewed:** 2026-07-04T00:00:00Z
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found

## Summary

Reviewed the merged `clientes/[id]/page.tsx` (view/edit toggle replacing the deleted `/editar` route) and `clientes/page.tsx` (list page). No BLOCKER-level authz bypass was found: every CRUD affordance in the detail page consistently gates on `canEditClientes && editable` (never `||`, never `editable` alone), and the backend independently enforces `@PreAuthorize("hasAuthority('clientes:edit')")` on all mutating endpoints (contactos, notas, advogados, administrativos, procuração, cliente PUT/DELETE), so the client-side gating is defense-in-depth rather than the actual security boundary. No dangling references to the deleted `/clientes/[id]/editar` route were found in the frontend routing/links.

However, there are real state-leakage and edge-case bugs around the edit-mode toggle: several sub-components (`ClienteContactosCard`, `ClienteNotasCard`, `ResponsaveisCard`) keep their own local "is this row/modal being edited" state that is never reset when the parent's `isEditing` flips from true to false (via Save or Cancel). This can leave stale, unreachable-but-rendered edit UI, or unintentionally re-expose editing controls if `editable` becomes true again later while stale local state is still set. There's also a latent race in the CSV import flow and a couple of code-quality issues (magic numbers, a stale docblock reference to the deleted `editar/page.tsx`).

## Warnings

### WR-01: Sub-card local edit state not reset when parent view/edit mode is exited

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1234-1261` (`ClienteContactosCard`), `:1426-1454` (`ClienteNotasCard`)
**Issue:** `ClienteContactosCard` and `ClienteNotasCard` each hold their own `editingId`/`editTipo`/`editValor` (or `editTitulo`/`editConteudo`) state, entirely decoupled from the parent's `isEditing` prop (passed down as `editable`). When the user clicks "Editar" on a contact/note row (`onStartEdit`), then the parent-level "Cancelar" or "Guardar" is clicked (which only calls `setIsEditing(false)` in the parent — it does not touch these children's local state), the child component still has `editingId` set to a row id. The gating `canEditClientes && editable ? (...) : null` at line 1366/1545 does correctly hide the Guardar/Cancelar/edit-mode buttons for that row once `editable` becomes false — the inputs stop being clickable — but the row is still rendered in "read" branch (since `canEditClientes && editable` is false, the ternary falls to the non-editing render), so this specific case is visually recoverable. The real bug is that if the user re-enters edit mode (`isEditing` true again) without navigating away, the row silently reopens with `editingId` still set to whatever it was before Cancel — the component was never told editing was aborted, so leftover `editTipo`/`editValor`/`editTitulo`/`editConteudo` (which may not match the current server data if a save happened elsewhere) can resurface as prefilled, possibly stale, edit inputs the next time the row's `isEditing = editingId === c.id` check is true again as soon as the parent is back in edit mode — without the user ever clicking "Editar" again.
**Fix:** Reset the child's local editing state (`setEditingId(null)` etc.) when `editable` transitions to `false`, e.g. via a `useEffect`:
```tsx
React.useEffect(() => {
  if (!editable) {
    setEditingId(null);
    setEditTipo("");
    setEditValor("");
  }
}, [editable]);
```
Apply the analogous effect in `ClienteNotasCard`.

### WR-02: `ResponsaveisCard` add-modal state not reset on parent cancel

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1097-1098`
**Issue:** `modalOpen`/`selectedUserId` in `ResponsaveisCard` are local state uncoupled from `editable`. If a user opens the "Adicionar" dialog, then (in another tab/element) the parent's edit mode is cancelled, the dialog can remain open with `canEditClientes && editable` now false — but nothing closes it defensively; the Button that opens it is gated, but if it was already open when `editable` flips, the `Dialog` itself isn't gated on `editable`, so it stays visibly open with the "Adicionar" action still wired to `onAdd`, which internally re-checks `canEditClientes || editable` (line 1106) and would silently no-op. This isn't an authz bypass (the guard exists), but it's a confusing UX where a modal for an action that's no longer permitted stays open with no explanation.
**Fix:** Close the modal when `editable` goes false:
```tsx
React.useEffect(() => {
  if (!editable) setModalOpen(false);
}, [editable]);
```

### WR-03: Intake list edits (`documentosEntregues`/`documentosATratar`/`deslocacoes`) are mutated live in state even while dialogs for adding items remain open across an aborted edit

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:174-185, 233-252`
**Issue:** `addDocEntreModal`, `addDocATratarModal`, `addDeslocacaoModal` and their staged `newDocEntre`/`newDocATratar`/`newDeslocacao` draft objects are declared at the top level of `ClienteDetailContent`, not scoped to the `isEditing` block. They are never reset on `onCancel` (line 292-301) or on successful submit (`onSubmit`, line 254-290) — only the confirm handlers reset their own draft after a successful "Confirmar" click. If a user opens "Adicionar Documento Entregue", types a description, then clicks the parent's "Cancelar" button (aborting the whole edit), the modal draft (`newDocEntre`) is not cleared. Re-entering edit mode later leaves the stale draft in the closed dialog's inputs, ready to be silently added if the user clicks "Adicionar" → dialog opens pre-filled with old text from a previous, cancelled edit session.
**Fix:** Reset all three drafts (and close their modals) in `onCancel` and after a successful `onSubmit`:
```tsx
const onCancel = () => {
  if (cliente.data) {
    form.reset(buildDefaultValues(cliente.data));
    setDocumentosEntregues(cliente.data.documentos_entregues ?? []);
    setDocumentosATratar(cliente.data.documentos_a_tratar ?? []);
    setDeslocacoes(cliente.data.deslocacoes ?? []);
  }
  setNewDocEntre({ descricao: "", data: "" });
  setNewDocATratar({ descricao: "" });
  setNewDeslocacao({ descricao: "", local: "", data: "" });
  setAddDocEntreModal(false);
  setAddDocATratarModal(false);
  setAddDeslocacaoModal(false);
  setServerError(null);
  setIsEditing(false);
};
```

### WR-04: `pendingTipo` confirmation dialog can leak across cancel and remains mounted for non-editing viewers

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:146-167, 949-962`
**Issue:** `pendingTipo` is set by `onTipoChange`, which is only reachable from the `tipo` `RadioGroup` rendered inside the `isEditing` block. It is cleared on confirm/cancel of its own dialog, and it can only be set while editing — so this is lower severity than WR-01–03, but the dialog `<Dialog open={!!pendingTipo} ...>` at line 949 is rendered unconditionally at the bottom of `ClienteDetailContent`, outside of any `isEditing` guard. If `onCancel` (parent Cancelar button) is clicked while `pendingTipo` is non-null (e.g., user changed tipo, dialog popped up asking to confirm, then user hits the outer "Cancelar" button instead of resolving the inner dialog — reachable since the outer Cancelar button is not disabled while the inner confirm dialog is open), `onCancel` does not clear `pendingTipo`. The confirm dialog will remain open, displaying stale "Mudar tipo de cliente" text, overlaying a page that has now exited edit mode and reset the form underneath it. Confirming it at that point calls `confirmTipoChange()`, which calls `form.setValue(...)` on a form that is no longer being displayed/edited (view mode) — the mutation silently changes in-memory form state with no visible effect, but leaves the form in a dirty, inconsistent state relative to what's displayed, and if the user re-enters edit mode via "Editar" immediately after, `form.reset` is not re-run (only re-run in the `cliente.data` effect, not on `isEditing` toggle), so the unintended `tipo` change from the abandoned dialog surfaces in the reopened edit form.
**Fix:** Clear `pendingTipo` in `onCancel`, and/or gate the outer Cancelar button so it can't be triggered while the inner confirmation dialog is open:
```tsx
const onCancel = () => {
  ...
  setPendingTipo(null);
  setIsEditing(false);
};
```

### WR-05: CSV import in `clientes/page.tsx` runs sequentially with awaited mutations inside a loop, but `createCliente` mutation shares a single `useCreateCliente()` instance across all iterations — no request cancellation/guard against duplicate submits if the user re-triggers import mid-flight

**File:** `web/src/app/(dashboard)/clientes/page.tsx:132-199`
**Issue:** `onImportFile` is `async` and loops `for (let i = 0; i < rows.length; i++) { await createCliente.mutateAsync(...) }`. There is no guard preventing the user from selecting a new CSV file (re-triggering `onImportFile`) while a previous import is still in-flight (the file `<input>` `onChange` at line 217 does not check an "importing" flag, and `onImportCsv`/the input's `click()` are not disabled during an active import). Two concurrent `onImportFile` calls would both use the same `createCliente` mutation object, interleaving `mutateAsync` calls and toasts, making the final success/failure toast unreliable and potentially double-creating clientes if the CSV is re-selected accidentally.
**Fix:** Add an `isImporting` state flag, set it before the loop, disable the "Importar CSV" button and prevent re-invoking `onImportCsv`/the file input handler while true:
```tsx
const [isImporting, setIsImporting] = React.useState(false);
const onImportFile = async (file: File) => {
  if (!canCreateClientes || isImporting) return;
  setIsImporting(true);
  try {
    // ...existing loop...
  } finally {
    setIsImporting(false);
  }
};
```

## Info

### IN-01: Stale docblock reference to the deleted `editar/page.tsx`

**File:** `web/src/schemas/clientes.ts:25-29`
**Issue:** The `buildClienteFormSchema` docblock says: "Usado pela página de edição para permitir que um `documento_tipo` legado ... seja guardado sem alterações — ver banner em editar/page.tsx." The dedicated `/clientes/[id]/editar` page no longer exists (merged into `[id]/page.tsx`); the banner referenced is now in `[id]/page.tsx` (lines 556-568 of the reviewed file). This is a leftover reference from before the phase-75 merge and will mislead future readers trying to find `editar/page.tsx`.
**Fix:** Update the comment to reference `clientes/[id]/page.tsx` (view/edit toggle) instead of the deleted route.

### IN-02: `documentosEntregues`/`documentosATratar`/`deslocacoes` list rendering uses array index as React `key`

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:747, 801, 871`
**Issue:** `.map((doc, index) => (<li key={index} ...>` — using the array index as key for a mutable, reorderable-by-deletion list is a known React anti-pattern that can cause incorrect DOM diffing (e.g., input focus/values bleeding into the wrong row) when an item is removed and later items shift index. Not currently causing a visible bug because rows are pure `<li>` display with no internal input state, but any future addition of inline editing to these rows would immediately surface stale state bugs from this key choice.
**Fix:** Key by a stable identity if available, or a generated id assigned when the item is added:
```tsx
setDocumentosEntregues((prev) => [...prev, { ...newDocEntre, _key: crypto.randomUUID() }]);
```
and key on `doc._key` (excluding it from the submitted payload), or at minimum document the limitation.

### IN-03: Magic string duplication for `documento_tipo` derivation scattered across component

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:189, 199, 215, 628, 631, 634, 638-639`
**Issue:** The `data.documento_tipo ?? data.documentoTipo ?? "..."` fallback pattern (handling both snake_case and camelCase API fields) is repeated at least 7 times across `buildDefaultValues`, the load effect, and the read-only `<dl>` rendering, for `documento_tipo`, `documento_numero`, `ramo_atividade`, and `detalhes_adicionais`. Any future change to the API's field naming convention requires updating all call sites consistently; a missed occurrence would silently regress to displaying "—" for one field while others still resolve.
**Fix:** Extract small normalizer helpers once (e.g. `normalizeCliente(data: Cliente)` returning consistently-cased fields) and reuse them in both `buildDefaultValues` and the read-only view render.

### IN-04: `nome.split(" ")` initials computation duplicated between `clientes/page.tsx` mobile card view and `ClienteRow`

**File:** `web/src/app/(dashboard)/clientes/page.tsx:408, 513-519`
**Issue:** The initials-from-name logic (`split(" ").filter(Boolean).slice(0, 2).map(...).join("").toUpperCase()`) is duplicated verbatim between the inline mobile-card rendering and the `ClienteRow` component for desktop. Any bug fix or i18n tweak (e.g., handling hyphenated names) must be applied in two places.
**Fix:** Extract to a small shared helper, e.g. `getInitials(nome: string): string`, used by both render paths.

---

_Reviewed: 2026-07-04T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
