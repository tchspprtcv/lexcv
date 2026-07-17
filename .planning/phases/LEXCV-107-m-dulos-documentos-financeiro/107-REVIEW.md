---
phase: LEXCV-107-m-dulos-documentos-financeiro
reviewed: 2026-07-17T00:00:00Z
depth: standard
files_reviewed: 9
files_reviewed_list:
  - web/src/components/shared/combobox.tsx
  - web/src/app/(dashboard)/documentos/page.tsx
  - web/src/app/(dashboard)/documentos/novo/page.tsx
  - web/src/app/(dashboard)/documentos/[id]/page.tsx
  - web/src/app/(dashboard)/financeiro/page.tsx
  - web/src/app/(dashboard)/financeiro/novo/page.tsx
  - web/src/app/(dashboard)/financeiro/[id]/page.tsx
  - web/src/app/(dashboard)/processos/[id]/page.tsx (ProcessoDocumentosTab only)
  - web/src/app/(dashboard)/clientes/[id]/page.tsx (ClienteDocumentosEntreguesTab only)
findings:
  critical: 1
  warning: 4
  info: 2
  total: 7
status: issues_found
---

# Phase 107: Módulos Documentos + Financeiro — Code Review Report

**Reviewed:** 2026-07-17
**Depth:** standard
**Files Reviewed:** 9
**Status:** issues_found

## Summary

Phase 107 migrated three visual patterns across Documentos + Financeiro: the official `Progress` bar (replacing 3 duplicated hand-rolled progress divs), `NativeSelect`/`Select` for real-enum and list-filter fields, and — the phase's centerpiece — a brand-new shared `Combobox` (Popover+Command) composition used in 4 call sites (2 closed-searchable list filters in Documentos, 2 creatable `Documento.tipo` fields in the Processo/Cliente document-upload dialogs). The 6 bundled `permissions.isFetched` RBAC-race fixes were also verified and are all correctly applied, with no remaining instances of the old `!permissions.isLoading` gate pattern in either module.

The `Progress`/`NativeSelect`/`Select`/RBAC migrations are clean and match the established patterns from Phases 103/105/106 with no defects found. The new `Combobox`, however, has a significant behavioral gap in its **creatable** mode: it only commits a value to the bound field when the user explicitly clicks (or keyboard-selects) a list item — there is no commit-on-close/blur path. Combined with the fact that the frontend has no document-update endpoint, this means a user who types a new `Documento.tipo` value and then closes the popover any other way (clicking the dialog's own "Confirmar" button, clicking outside, Tab, Escape) silently uploads the document with the previous/empty tipo instead of what they typed, with zero error feedback. Three further, real (though lower-severity) defects were found in the same component and its call sites: a keyboard-selection ambiguity when two options share a label (plausible for client names), a stale-search-text bug on the two persistently-mounted filter instances, and a lost "disable while uploading" behavior for the tipo field (the component has no `disabled` prop at all). The Documentos list filters also regressed a piece of existing functionality — the ability to clear a single filter independently — that the Financeiro Select migration in the very same phase preserved via a `"todos"` sentinel.

## Narrative Findings (AI reviewer)

### Critical Issues

#### CR-01: Creatable Combobox silently discards typed text that isn't explicitly selected — silent `Documento.tipo` data loss with no in-app remedy

**File:** `web/src/components/shared/combobox.tsx:46-119` (root cause)
**Also affects call sites:**
- `web/src/app/(dashboard)/processos/[id]/page.tsx:2595-2605` (`ProcessoDocumentosTab`)
- `web/src/app/(dashboard)/clientes/[id]/page.tsx:1327-1337` (`ClienteDocumentosEntreguesTab`)

**Issue:**
The bound `value`/`onChange` pair is only ever updated inside `commit()` (line 66-70), which is only called from a `CommandItem`'s `onSelect` — i.e., only when the user explicitly clicks an existing option or the synthetic `Usar "{query}"` create item. There is no `onBlur` handler on `CommandInput`, and `<Popover open={open} onOpenChange={setOpen}>` has no side effect that commits the in-progress `query` when the popover closes for any other reason (clicking outside, pressing Escape, Tab-ing away, or clicking a sibling button such as the dialog's "Confirmar").

Concretely, in both `ProcessoDocumentosTab` and `ClienteDocumentosEntreguesTab`:
1. User opens "Adicionar Documento", picks a file.
2. User clicks the Tipo combobox and types e.g. `"Procuração Especial"` but — reasonably, since this looks like a normal text field — does not click the `Usar "Procuração Especial"` suggestion.
3. User clicks "Confirmar" directly. The click's `mousedown`/outside-click first dismisses the Popover (discarding the typed-but-uncommitted `query` with no trace), then the button's `onClick` fires `onConfirmarUpload`, which reads `novoTipo` — still `""` (or whatever it was before this edit) — from component state.
4. `upload.mutateAsync({ file, tipo: novoTipo.trim(), processo_id/cliente_id })` fires with the wrong/empty `tipo`. The upload succeeds (HTTP 2xx once the backend bug is fixed — see deferred-items.md), the success toast fires, and the document is now permanently miscategorized.

There is no `useUpdateDocumento`/PATCH hook in `web/src/hooks/use-documentos.ts` — once uploaded, `Documento.tipo` cannot be edited from the UI. The only recovery is delete + re-upload (requires edit/delete permission and the user noticing the mistake at all, which nothing in the UI surfaces).

**Fix:** Commit the typed value when the popover closes without an explicit selection, e.g.:
```tsx
function handleOpenChange(next: boolean) {
  if (!next && creatable && trimmedQuery && trimmedQuery !== value) {
    onChange(trimmedQuery);
  }
  setOpen(next);
  setQuery("");
}

// ...
<Popover open={open} onOpenChange={handleOpenChange}>
```
This also resolves WR-02 below (the `query` reset now happens unconditionally on every close, not only on `commit()`).

---

### Warnings

#### WR-01: `CommandItem value={option.label}` breaks cmdk's single-item selection tracking when two options share a label

**File:** `web/src/components/shared/combobox.tsx:106`
**Issue:** cmdk (`node_modules/.../cmdk/dist/index.mjs`) tracks the "currently selected/highlighted" item as a single string (`state.value`), matched against each item's own `value` prop via `state.value === itemValue`. Because `value={option.label}` is used here (not the guaranteed-unique `option.value`), two options with an identical label will both simultaneously satisfy `aria-selected="true"`/`data-selected="true"` whenever either is the tracked selection. Pressing Enter then dispatches `onSelect` on whichever of the two DOM nodes `document.querySelector('[aria-selected="true"]')` returns first — not necessarily the one the user arrow-keyed to.

This is a real risk for the Documentos list Cliente filter (`clienteOptions`, labeled by `c.nome` — two clients sharing a full name is a completely ordinary occurrence in a legal-practice client base) and, less likely but not impossible, for the Processo filter if two processos ever share the same fallback label.

Selecting by mouse click is unaffected (the `onSelect={() => commit(option.value)}` closure captures the correct `option.value` regardless of the `value` prop collision), so this only affects keyboard navigation/selection.

**Fix:** Use the unique identifier for cmdk's own item identity, keep the label only as display text:
```tsx
<CommandItem
  key={option.value}
  value={option.value}
  data-checked={option.value === value}
  onSelect={() => commit(option.value)}
>
  {option.label}
</CommandItem>
```
(Safe here because `shouldFilter={false}` means cmdk never uses `value` for its own text matching — filtering is already done manually against `option.label` above.)

#### WR-02: `query` search-box state is never reset when the popover closes without a commit — stale search text/list reappears on reopen

**File:** `web/src/components/shared/combobox.tsx:46-47, 73` (state declarations + `Popover onOpenChange={setOpen}`)
**Issue:** `query` is only cleared inside `commit()`. If the user types a search term and then closes the popover any other way (click outside, Escape), `query` keeps its stale value. Because `Combobox`'s `open`/`query` state lives in the `Combobox` component itself (not inside the conditionally-mounted `PopoverContent`/`Command` tree), this state survives across open/close cycles for any `Combobox` instance that is **not** itself unmounted between uses.

This concretely affects the two Documentos list filter instances (`web/src/app/(dashboard)/documentos/page.tsx:130-140` Processo, `:151-161` Cliente), which are rendered directly on the page (not inside a `Dialog`, so they never unmount): typing a search term, abandoning it (click elsewhere), then reopening the same combobox later in the session shows the old leftover search text and the correspondingly-filtered (possibly single-item or empty) list instead of the full option list.

(The two creatable instances in `ProcessoDocumentosTab`/`ClienteDocumentosEntreguesTab` happen to escape this in practice today only because they live inside a `DialogContent`, which Radix fully unmounts on close, incidentally resetting the `Combobox`'s local state along with it — this is not a property of `Combobox` itself and would break the moment it's reused inside any always-mounted container.)

**Fix:** See CR-01's fix — resetting `query` unconditionally in the `onOpenChange` handler fixes both issues at once.

#### WR-03: Documentos list filters lost the ability to clear a single field independently (regression vs. the previous free-text `Input`, and inconsistent with Financeiro's own Select migration in this same phase)

**File:** `web/src/app/(dashboard)/documentos/page.tsx:124-166`
**Issue:** Before this phase, `processo_id`/`cliente_id` were plain `<Input>` fields — a user could clear just one of them (select-all + delete) and click "Filtrar" to filter by only the other. The new `Combobox` options list has no blank/"Todos" entry and the trigger has no clear ("×") affordance, so once a Processo or Cliente is selected there is no way to deselect it except the page-wide "Limpar" button, which resets **both** filters and the date-range fields.

This is also an internal inconsistency: `web/src/app/(dashboard)/financeiro/page.tsx:213-225` migrated its Processo/Estado filters to `Select` in this exact same phase and deliberately added a `"todos"` sentinel item precisely to preserve this same "clear one filter" capability (see `107-03-SUMMARY.md`'s "Filter sentinel pattern" decision) — the Documentos Combobox migration did not carry the equivalent affordance over.

**Fix:** Add a leading sentinel option (mirroring the Financeiro pattern) to both `processoOptions`/`clienteOptions`, e.g.:
```tsx
const processoOptions = React.useMemo(
  () => [
    { value: "", label: "Todos os processos" },
    ...(processos.data ?? []).map((p) => ({ value: p.id, label: p.numero ?? p.titulo ?? p.id })),
  ],
  [processos.data],
);
```
(with the Combobox's non-creatable `options.find` logic already treating `value === ""` as any other option — `placeholder` would then only be used before first interaction). Alternatively, add a small clear icon to the `Combobox` trigger when `value` is truthy.

#### WR-04: `Combobox` has no `disabled` prop — the tipo field's "disable while uploading" behavior was silently dropped by the migration

**File:** `web/src/components/shared/combobox.tsx:23-45` (prop signature — no `disabled`)
**Also affects:** `web/src/app/(dashboard)/processos/[id]/page.tsx:2595-2605`, `web/src/app/(dashboard)/clientes/[id]/page.tsx:1327-1337`
**Issue:** The pre-migration `<input list=... disabled={upload.isPending} ...>` disabled the tipo field while an upload was in flight. The migration to `Combobox` dropped this — `Combobox` has no `disabled` prop for a call site to wire up — while the sibling `FileDropZone` in the same dialog still correctly receives `disabled={upload.isPending}` (line 2588 / 1320). The user can now reopen the tipo popover and change the value while a request is in flight (impact is limited since the "Confirmar" submit button itself is still correctly disabled via `disabled={!novoFicheiro || upload.isPending}`, so no duplicate submission is possible — but it is a real, silently-dropped piece of the previous UX contract).

**Fix:** Add an optional `disabled?: boolean` prop, applied to the trigger `Button` (and ideally short-circuiting `PopoverTrigger`/`onOpenChange`):
```tsx
<Button id={id} type="button" variant="outline" role="combobox" aria-expanded={open}
  disabled={disabled} className={...}>
```
and pass `disabled={upload.isPending}` at both call sites.

---

### Info

#### IN-01: Combobox trigger lacks `aria-controls`/`aria-haspopup="listbox"` wiring to the popover listbox

**File:** `web/src/components/shared/combobox.tsx:74-88`
**Issue:** The trigger `Button` sets `role="combobox"` and `aria-expanded`, but not `aria-controls` (pointing at the `CommandList`'s id) or `aria-haspopup="listbox"`. This matches the common shadcn/ui reference Combobox example (not a phase-specific regression), but full WAI-ARIA 1.2 combobox conformance would wire these up.
**Fix:** Forward `CommandList`'s generated id via `aria-controls` on the trigger, and add `aria-haspopup="listbox"`.

#### IN-02: `Progress value={progresso ?? 0}` fallback is unreachable dead code at all 3 call sites

**File:** `web/src/app/(dashboard)/documentos/novo/page.tsx:187`, `web/src/app/(dashboard)/processos/[id]/page.tsx:2613`, `web/src/app/(dashboard)/clientes/[id]/page.tsx:1345`
**Issue:** All three occurrences of `<Progress value={progresso ?? 0} />` are rendered only inside a `{progresso !== null ? (...) : null}` guard, so `progresso` is already guaranteed non-null (and typed `number`) at that point — the `?? 0` fallback can never execute. Harmless, but slightly misleading defensive code that implies a null case which cannot occur.
**Fix:** `<Progress value={progresso} />` (drop the `?? 0`), or type the guard so TypeScript itself narrows `progresso` to `number` inside the branch.

---

_Reviewed: 2026-07-17_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
