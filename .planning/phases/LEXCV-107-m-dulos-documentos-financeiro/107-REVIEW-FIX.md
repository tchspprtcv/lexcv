---
phase: LEXCV-107-m-dulos-documentos-financeiro
fixed_at: 2026-07-17T02:45:00Z
review_path: .planning/phases/LEXCV-107-m-dulos-documentos-financeiro/107-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 107: Módulos Documentos + Financeiro — Code Review Fix Report

**Fixed at:** 2026-07-17T02:45:00Z
**Source review:** .planning/phases/LEXCV-107-m-dulos-documentos-financeiro/107-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (1 Critical, 4 Warning — CR-01, WR-01, WR-02, WR-03, WR-04; Info findings excluded per default `critical_warning` fix scope)
- Fixed: 5
- Skipped: 0

## Fixed Issues

### CR-01: Creatable Combobox silently discards typed text that isn't explicitly selected

**Files modified:** `web/src/components/shared/combobox.tsx`
**Commit:** `a082695`
**Applied fix:** Added a `handleOpenChange(next)` handler passed to `<Popover onOpenChange>`. When the popover closes (`next === false`) without an explicit selection, and the component is `creatable` with a non-empty `trimmedQuery` that differs from the current `value`, the typed text is now committed via `onChange(trimmedQuery)` before closing. `query` is also reset unconditionally on every open/close transition inside this same handler, which resolves WR-02 in the same commit (see below). This closes the silent `Documento.tipo` data-loss path in both `ProcessoDocumentosTab` and `ClienteDocumentosEntreguesTab` (clicking "Confirmar", clicking outside, Tab, or Escape after typing a new tipo now correctly captures the typed value instead of uploading with a stale/empty `tipo`).

### WR-02: `query` search-box state never reset when popover closes without a commit

**Files modified:** `web/src/components/shared/combobox.tsx`
**Commit:** `a082695` (bundled with CR-01, per the review's own note that a single fix resolves both)
**Applied fix:** Same `handleOpenChange` handler unconditionally calls `setQuery("")` on every open/close transition, so stale search text can no longer resurface on reopen for the two always-mounted Documentos list filter Comboboxes.

### WR-01: `CommandItem value={option.label}` breaks cmdk's keyboard-selection tracking when two options share a label

**Files modified:** `web/src/components/shared/combobox.tsx`
**Commit:** `f4f5a0e`
**Applied fix:** Changed `<CommandItem value={option.label} ...>` to `<CommandItem value={option.value} ...>`, keeping `option.label` as the rendered child text only. Verified this is safe because `<Command shouldFilter={false}>` means cmdk never uses this `value` for its own text-matching (manual filtering is already done separately against `option.label`). This fixes ambiguous keyboard-selection/highlighting when two options (e.g. two clients) share an identical label.

### WR-03: Documentos list filters lost the ability to clear a single filter independently

**Files modified:** `web/src/app/(dashboard)/documentos/page.tsx`
**Commit:** `6742fe0`
**Applied fix:** Added a leading `{ value: "", label: "Todos os processos" }` / `{ value: "", label: "Todos os clientes" }` sentinel option to `processoOptions`/`clienteOptions`, mirroring the `"todos"` sentinel pattern used by `financeiro/page.tsx`'s `Select` migration in this same phase. Verified via source assertion (no code change needed in `Combobox` itself) that this does not weaken the non-creatable "must select an existing option" semantics: the sentinel is a genuine, ordinary `options` entry rendered as a normal `CommandItem` — selecting it calls `commit("")` exactly like any other option, and `showCreateItem` remains gated on `creatable` (false here), so arbitrary free-text entry is still impossible. `Combobox`'s existing `options.find((o) => o.value === value)` selection logic already treats `value === ""` as any other option match, so the sentinel displays as a normal selected label once chosen (not placeholder-grey), consistent with the Select-based sentinel pattern. Downstream, `useDocumentos`'s `buildDocumentosSearch`/query-key logic already treats an empty/whitespace `processo_id`/`cliente_id` as "no filter" via existing `.trim()` truthy checks, so no hook changes were needed.

### WR-04: `Combobox` has no `disabled` prop — "disable while uploading" behavior silently dropped

**Files modified:** `web/src/components/shared/combobox.tsx`, `web/src/app/(dashboard)/processos/[id]/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/page.tsx`
**Commit:** `6c399d2`
**Applied fix:** Added an optional `disabled?: boolean` prop (default `false`) to `Combobox`, applied to the trigger `Button`'s `disabled` attribute and additionally short-circuiting `handleOpenChange` (returns early when `disabled`) so the popover cannot be toggled open programmatically while disabled. Wired `disabled={upload.isPending}` at both call sites (`ProcessoDocumentosTab`, `ClienteDocumentosEntreguesTab`'s tipo `Combobox`), restoring parity with the sibling `FileDropZone` in the same dialogs.

## Skipped Issues

None — all in-scope findings were fixed.

---

_Fixed: 2026-07-17T02:45:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
