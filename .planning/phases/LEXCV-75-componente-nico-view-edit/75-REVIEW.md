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
  warning: 0
  info: 5
  total: 5
status: issues_found
---

# Phase LEXCV-75: Code Review Report (Re-review)

**Reviewed:** 2026-07-04T00:00:00Z
**Depth:** standard
**Files Reviewed:** 2
**Status:** issues_found (info only — all 5 prior WARNINGs confirmed fixed; 4 prior INFO items remain open, unaddressed by this round's commits)

## Summary

Re-review of the prior round's findings (WR-01 through WR-05, plus IN-01 through IN-04) against the current state of `web/src/app/(dashboard)/clientes/[id]/page.tsx` and `web/src/app/(dashboard)/clientes/page.tsx`. All 5 WARNING-level findings were traced to dedicated fix commits and independently verified against the current file contents — each is complete, correctly scoped, and addresses the root cause. No new BLOCKER or WARNING issues were introduced by the fix commits themselves. The four prior INFO items were not addressed by this round (not required per the fix scope) and remain open; they are re-listed below for tracking continuity rather than as new findings.

## Warnings — All Confirmed Fixed

### WR-01: Sub-card local edit state not reset when parent view/edit mode is exited — FIXED

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1259-1265` (`ClienteContactosCard`), `:1461-1467` (`ClienteNotasCard`)
**Fix commit:** `c473d49`
**Verification:** Both components now have a `React.useEffect` keyed on `editable` that clears `editingId`/`editTipo`/`editValor` (resp. `editTitulo`/`editConteudo`) whenever `editable` transitions to `false`:
```tsx
React.useEffect(() => {
  if (!editable) {
    setEditingId(null);
    setEditTipo("");
    setEditValor("");
  }
}, [editable]);
```
This fires on both Cancel and successful Save (both flow through the parent's `isEditing` state, which is passed down as `editable`), so stale row-edit state can no longer resurface on re-entering edit mode. Confirmed correct — no residual gap.

### WR-02: `ResponsaveisCard` add-modal state not reset on parent cancel — FIXED

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:1115-1117`
**Fix commit:** `23c34f8`
**Verification:** `React.useEffect(() => { if (!editable) setModalOpen(false); }, [editable])` was added. `modalOpen` is forced closed the moment `editable` goes false, closing the gap where the dialog could remain visibly open with no longer-permitted actions. Confirmed correct.

### WR-03: Intake list add-dialogs/drafts not reset on cancel or after submit — FIXED

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:284-290` (`onSubmit`), `:298-314` (`onCancel`)
**Fix commit:** `22e619a`
**Verification:** Both `onSubmit`'s success path and `onCancel` now reset `newDocEntre`/`newDocATratar`/`newDeslocacao` to their empty shapes and force `addDocEntreModal`/`addDocATratarModal`/`addDeslocacaoModal` to `false`. Stale, pre-filled drafts can no longer resurface across an aborted or completed edit session. Confirmed correct.

### WR-04: `pendingTipo` confirmation dialog not cleared by outer Cancelar — FIXED

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:311` (`onCancel`)
**Fix commit:** `22e619a` (same commit as WR-03)
**Verification:** `onCancel` now includes `setPendingTipo(null)`. The `pendingTipo` dialog (rendered unconditionally at `[id]/page.tsx:962-975`, outside the `isEditing` guard) is correctly dismissed alongside the rest of the form reset when the outer Cancelar button is clicked, eliminating the stale-dialog-over-reset-form scenario. Confirmed correct.

### WR-05: CSV import concurrent re-invocation race — FIXED

**File:** `web/src/app/(dashboard)/clientes/page.tsx:74` (state), `:128-135` (guards), `:202-204` (`finally` reset)
**Fix commit:** `cc3ae97`
**Verification:** `isImporting` state is checked in both `onImportCsv` and `onImportFile` before proceeding, set to `true` before the async work starts, and reset in a `finally` block that covers every exit path of `onImportFile` — including the early `return` at line 146 for a missing `nome` column, and any thrown error from `createCliente.mutateAsync`. The trigger button and hidden file `<input>` are both `disabled={isImporting}`. This closes the double-import / interleaved-mutation race. Confirmed correct.

**No new issues were introduced by any of the five fix commits.** Each fix is a minimal, targeted `useEffect`/state-reset/guard addition, consistent with the existing patterns already present elsewhere in the same file (e.g., the `ClienteContactosCard` reset effect was reused near-verbatim for `ClienteNotasCard` and `ResponsaveisCard`).

## Info

The following four INFO items from the prior review round were not in scope for this round's required fixes and remain open in the current code. Re-listed here for tracking continuity, not as new findings.

### IN-01: Stale docblock reference to the deleted `editar/page.tsx` — STILL OPEN

**File:** `web/src/schemas/clientes.ts:27`
**Issue:** `buildClienteFormSchema`'s docblock still reads "ver banner em editar/page.tsx", but `/clientes/[id]/editar` was deleted in this phase (merged into `[id]/page.tsx`, commit `fcf5dc8`). The banner it refers to now lives in `[id]/page.tsx:575-581`.
**Fix:** Update the comment to reference `clientes/[id]/page.tsx` instead of the deleted route.

### IN-02: Intake list rendering uses array index as React `key` — STILL OPEN

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:761, 815, 885`
**Issue:** `documentosEntregues`/`documentosATratar`/`deslocacoes` `.map((doc, index) => (<li key={index} ...>` still keys by array index. Currently benign (rows have no internal input state), but any future addition of inline editing to these rows would surface stale-state bugs from index-based keys on deletion/reorder.
**Fix:** Assign a stable id when an item is added (e.g. `crypto.randomUUID()`) and key on that instead of `index`.

### IN-03: Magic string duplication for `documento_tipo`/`documento_numero`/`ramo_atividade`/`detalhes_adicionais` snake/camel fallback — STILL OPEN

**File:** `web/src/app/(dashboard)/clientes/[id]/page.tsx:189-202, 215, 641-652`
**Issue:** The `data.documento_tipo ?? data.documentoTipo ?? "..."`-style fallback (handling both API casing conventions) is still repeated across `buildDefaultValues`, the load effect, and the read-only `<dl>` render.
**Fix:** Extract a `normalizeCliente(data: Cliente)` helper returning consistently-cased fields, reused by both the form-default builder and the read-only view.

### IN-04: Initials computation duplicated between mobile card and `ClienteRow` — STILL OPEN

**File:** `web/src/app/(dashboard)/clientes/page.tsx:421, 526-532`
**Issue:** The `nome.split(" ").filter(Boolean).slice(0, 2).map(...).join("").toUpperCase()` initials logic is still duplicated verbatim between the inline mobile-card rendering and `ClienteRow`.
**Fix:** Extract to a shared `getInitials(nome: string): string` helper used by both render paths.

---

_Reviewed: 2026-07-04T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
