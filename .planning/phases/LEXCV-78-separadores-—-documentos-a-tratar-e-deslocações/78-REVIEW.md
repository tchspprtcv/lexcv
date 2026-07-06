---
phase: LEXCV-78-separadores-—-documentos-a-tratar-e-deslocações
reviewed: 2026-07-06T00:00:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
findings:
  critical: 0
  warning: 0
  info: 2
  total: 2
status: issues_found
---

# Phase LEXCV-78: Code Review Report

**Reviewed:** 2026-07-06T00:00:00Z
**Depth:** standard
**Files Reviewed:** 1
**Status:** issues_found

## Summary

Reviewed the relocation of the "Documentos a Tratar" and "Deslocações" blocks from the `dados` tab's `isEditing`-gated "Intake do Caso" card into their own `documentosATratar` and `deslocacoes` tab branches, and the widening of the Phase 76 CR-01 dialog-reset `useEffect` from one shared condition into three independent per-dialog conditions.

Traced all three call sites that can flip `isEditing` to `false` (`onSubmit`, `onCancel`, and the "Editar" button which only ever sets it to `true`) — both `onSubmit` and `onCancel` explicitly and unconditionally reset all three dialogs' open/draft state directly, independent of the `tab`-keyed `useEffect`. This means the save/cancel path is safe regardless of which tab the user is on when they save or cancel (the top action bar with Guardar/Cancelar is rendered outside the tab body and stays visible across all tabs while editing).

Traced the widened `useEffect` (lines 211-224): the three conditions (`tab !== "dados"`, `tab !== "documentosATratar"`, `tab !== "deslocacoes"`) are mutually exclusive against the tab that currently matches, so navigating between any two of the three tabs resets exactly the one dialog whose tab was left, and never resets the dialog belonging to the tab just entered. No dialog loses its reset and no dialog double-resets under any tab transition, including tab transitions that happen while `isEditing` is `false` (the effect is unconditional on `tab`, running regardless of edit mode, which is harmless since the dialogs are only ever opened while editing).

Verified no duplicate/orphaned JSX: grep for the two relocated blocks' distinctive strings ("Documentos a Tratar", "Deslocações", their empty-state copy) shows each block appears exactly once in the file — no leftover copy remains inside the "Dados" tab's Intake card.

Verified the `isEditing` gate was applied identically to both relocated blocks: both `documentosATratar` and `deslocacoes` tab branches follow the exact same `isEditing ? (<Card>...) : null` pattern (lines 960-1012 and 1014-1089), so both are fully hidden in read mode, consistent with the stated intent that this is unchanged, intentional behavior carried over from the original Intake card gating.

No bugs, security issues, or state/handler duplication found. Two minor documentation/comment quality items are noted below as Info.

## Info

### IN-01: Comment above the reset useEffect still describes the pre-widening (single-dialog) behavior

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:204-210`
**Issue:** The comment block introducing the `useEffect` still reads as if only one dialog ("The three 'Adicionar' dialogs above are only rendered inside the 'Dados' tab's JSX...") is being described, then references "the reset effect used for AdvogadosResponsaveisCard-style sub-components below" as an analogy. After the phase 78 widening, two of the three dialogs are now rendered in their own dedicated tabs (`documentosATratar`, `deslocacoes`), not inside "Dados". The comment was not updated to reflect that the effect now independently guards three different tabs rather than gating everything on leaving a single tab. This is not a functional bug, but it will mislead a future maintainer who reads the comment without re-deriving the three-way condition logic below it.
**Fix:**
```ts
// Documentos Entregues (rendered in the "Dados" tab's Intake card) and the two dialogs now
// hoisted into their own "Documentos a Tratar" / "Deslocações" tabs are each unmounted when the
// user is not on their respective tab. A controlled `open={true}` left over from a previous visit
// would otherwise reopen the dialog — with stale draft text — the moment the user navigates back.
// Close and clear each dialog independently whenever the user is off its owning tab.
```

### IN-02: `documentosATratar`/`deslocacoes`/`documentosEntregues` list rows key off array index

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:874, 995, 1068`
**Issue:** All three "Adicionar"-backed lists (`documentosEntregues.map((doc, index) => ... key={index}`, `documentosATratar.map((doc, index) => ... key={index}`, `deslocacoes.map((d, index) => ... key={index}`) key list items by their array index rather than a stable identifier. Combined with the "Remover" handlers that filter by index (`prev.filter((_, i) => i !== index)`), deleting a row from the middle of the list causes React to reuse DOM nodes/state for the wrong logical item during the re-render (most visible if any row ever grows input state, e.g. inline editing were added later). This pattern predates phase 78 and is unchanged by the relocation, but since two of the three lists were touched (moved) in this phase it's worth flagging while the code is in view.
**Fix:** Since these entries have no natural unique id from the backend (they are plain value objects), consider generating a stable client-side id on add (e.g. `crypto.randomUUID()`) and keying/filtering by that id instead of index:
```ts
setDocumentosATratar((prev) => [...prev, { ...newDocATratar, _cid: crypto.randomUUID() }]);
// ...
key={doc._cid}
onClick={() => setDocumentosATratar((prev) => prev.filter((d) => d._cid !== doc._cid))}
```

---

_Reviewed: 2026-07-06T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
