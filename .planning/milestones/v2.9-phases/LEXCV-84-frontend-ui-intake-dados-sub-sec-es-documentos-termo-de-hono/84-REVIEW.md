---
phase: LEXCV-84-frontend-ui-intake-dados-sub-sec-es-documentos-termo-de-hono
reviewed: 2026-07-08T00:00:00Z
depth: deep
files_reviewed: 6
files_reviewed_list:
  - web/src/app/(dashboard)/processos/novo/page.tsx
  - web/src/app/(dashboard)/processos/[id]/editar/page.tsx
  - web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx
  - web/src/app/(dashboard)/processos/[id]/page.tsx
  - web/src/schemas/processos.ts
  - web/src/types/processos.ts
findings:
  critical: 1
  warning: 4
  info: 4
  total: 9
status: issues_found
---

# Phase LEXCV-84: Code Review Report

**Reviewed:** 2026-07-08T00:00:00Z
**Depth:** deep
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Reviewed the five 84-0x plans' combined output in `processos/[id]/page.tsx` (2557 lines — the file all five plans touch) plus the intake wizard, edit page, termo-honorários print route, and the shared Zod/type contracts. Cross-checked every mutation call against `web/src/hooks/use-processos.ts` to verify payload shapes match the declared `*CreateRequest`/`*UpdateRequest` types, and pulled the installed `@tanstack/query-core@5.100.14` source to verify the Termo de Honorários' 3-hook loading gate doesn't race (it doesn't — `shouldFetchOptionally` computes an optimistic `fetchStatus: "fetching"` synchronously in the same render where a previously-disabled query becomes enabled, so `isLoading` is accurate).

Specific items from the review brief, confirmed correct:
- **Decisão multipart upload** (`useAddDecisao` in `use-processos.ts`) genuinely builds a `FormData` with `data`/`tipo`/`resumo`/`file` fields and POSTs it as `multipart/form-data`, matching the Phase 81/83 contract; update (no file) correctly uses `PUT` + JSON.
- **Facto `ordem`**: create never sends `ordem` (`FactoCreateRequest` has no such field); edit sends the operator-edited value verbatim via `FactoUpdateRequest.ordem`. Matches the documented server-computes-on-create contract — but see WR-04 below for a validation gap on that same field.
- **Testemunha `z.preprocess` fix**: the `tipo` preprocess only maps the native `<select>`'s `""` sentinel to `undefined` before validating against `tipoTestemunhaSchema.optional()`; any other value still goes through normal enum validation, so no real validation errors are swallowed. The `resolver: ... as any` cast is a type-level workaround only (same pattern as `prazoForm`), not a runtime behavior change.
- **RBAC**: all "Adicionar"/"Editar"/"Apagar" affordances across Partes, Fases, Decisões, Factos and Testemunhas are gated on `canEditProcessos`; the Documentos tab is correctly gated on the distinct `canEditDocumentos` scope; the Termo de Honorários "Imprimir" button uses a real `disabled` prop (forwarded to the native `<button disabled>` by the shared `Button` primitive), not just CSS styling.

Issues found below are mostly narrow but real: one broken save path on the Fases tab, a chunk of dead Movimentação-form code left over from before this phase whose associated query still gates the whole page's error/loading state, an inconsistency in dialog-state hygiene between the 84-03 (Partes/Fases) and 84-04/84-05 (Decisões/Testemunhas/Factos) plans, and a missing client-side validation guard on the Facto `ordem` input.

## Critical Issues

### CR-01: Fases "Guardar" can silently no-op / fail when the row's dropdown was never touched

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:474-484` (handler) and `:1717-1741` (render)

**Issue:** Each Fase row's status `<select>` displays `faseDraftStatus[f.id] ?? f.status` (line 1719) — i.e. it falls back to the fase's current server-side status when the user hasn't touched that row's dropdown. But `onUpdateFaseStatus` only reads the draft, with no such fallback:

```ts
const onUpdateFaseStatus = async (faseId: number) => {
  const status = faseDraftStatus[faseId];              // undefined if row never touched
  const payload: ProcessoFaseUpdateRequest = { status }; // { status: undefined }
  try {
    await updateFaseStatus.mutateAsync({ faseId, payload });
    ...
```

If a user clicks "Guardar" on a row whose dropdown they never changed (e.g. clicking the wrong row's button, or re-confirming a value that already matches what's displayed), `faseDraftStatus[faseId]` is `undefined`. `JSON.stringify({ status: undefined })` produces `"{}"`, so the PUT body is empty. Depending on backend handling this either 400s (shown to the user as the generic "Erro ao atualizar status da fase" toast, i.e. a broken save with no clear reason) or is accepted as a no-op that doesn't actually reflect what's visibly selected — either way the "Guardar" affordance next to a per-row select is broken for the common case of a row whose select was never interacted with.

**Fix:** Mirror the same fallback used by the `<select>`'s `value`:

```ts
const onUpdateFaseStatus = async (faseId: number, currentStatus: ProcessoFaseStatus) => {
  const status = faseDraftStatus[faseId] ?? currentStatus;
  const payload: ProcessoFaseUpdateRequest = { status };
  ...
};
// call site: onClick={() => onUpdateFaseStatus(f.id, f.status)}
```

## Warnings

### WR-01: Dead Movimentação add-form, and its query still gates the whole detail page

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:230, 242, 284-286, 302-306, 486-503`

**Issue:** `movForm`, `addMov` (`useAddProcessoMovimentacao`), `onSubmitMov`, and `movServerError` are all defined but never rendered anywhere in the JSX — there is no "Movimentações" tab, dialog, or button that calls `onSubmitMov` or exposes `movForm`. This predates Phase 84 (confirmed via `git show` on the pre-84 commit — identical dead code already existed), but the whole file was rewritten across 5 plans in this phase without it being noticed or cleaned up.

More importantly, `useProcessoMovimentacoes(id)` (`movimentacoes` — the *read* query backing the never-rendered form) is folded into the page's top-level gate:

```ts
const isLoading = processo.isLoading || clientes.isLoading || partes.isLoading || fases.isLoading || movimentacoes.isLoading;
const isError   = processo.isError   || clientes.isError   || partes.isError   || fases.isError   || movimentacoes.isError;
```

This `isError`/`isLoading` pair blocks rendering of the *entire* page (Dados, Conflict Check, Workflow, Prazos, all tabs) — not just a Movimentações section. If `/processos/{id}/movimentacoes` ever errors (permissions edge case, transient 5xx, etc.), the whole processo detail page collapses into a blank generic error message even though Workflow/Partes/Fases/Decisões/etc. may have loaded fine, and even though the data that failed to load is never shown to the user in the first place.

**Fix:** Remove the dead `movForm`/`addMov`/`onSubmitMov`/`movServerError` code (or wire up the missing UI, if a "Registar Movimentação" affordance was actually intended for this phase — check `84-UI-SPEC.md`/`84-PATTERNS.md` for whether it was in scope). Either way, drop `movimentacoes.isLoading`/`movimentacoes.isError` from the top-level gate — that resource isn't needed to render anything currently on the page.

### WR-02: Partes/Fases "Adicionar" dialogs don't reset stale form input on reopen

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:1546-1550` (Partes trigger), `:1648-1652` (Fases trigger) vs. `:1758-1767` (Decisões trigger using `onOpenAddDecisao`)

**Issue:** The Decisões/Testemunhas/Factos "Adicionar" `DialogTrigger` buttons all call a dedicated `onOpenAddX()` handler on click that explicitly resets the form and server-error state before opening (e.g. `onClick={onOpenAddDecisao}` → `decisaoForm.reset(...); setDecisaoServerError(null);`). The Partes and Fases `DialogTrigger` buttons (84-03) have no `onClick` handler at all:

```tsx
<DialogTrigger asChild>
  <Button type="button" variant="outline" size="sm" className="rounded-none">
    Adicionar Parte
  </Button>
</DialogTrigger>
```

If a user opens "Adicionar Parte", types a name, then clicks "Cancelar" (which only calls `setAddParteModal(false)`, not `parteForm.reset(...)`), the next time they reopen "Adicionar Parte" the previously-typed, unsaved text is still sitting in the form (Radix `DialogContent` un-mounts on close, but `parteForm`'s internal RHF state is not tied to that mount cycle — it's a `useForm` instance scoped to the parent component, so its values persist independently of the dialog's DOM lifecycle). Same issue for Fases' "Nome da fase" field. This is a real cross-plan consistency gap for the exact scenario the review brief calls out (item 2): 84-03's Dialog conversion didn't carry over the reset-on-open discipline that 84-04/84-05 established.

**Fix:** Add `onClick` handlers to the Partes/Fases `DialogTrigger` buttons (or `onOpenChange` on the `Dialog`) that call `parteForm.reset({ tipo: undefined, nome: "", nif: undefined }); setParteServerError(null);` / `faseForm.reset({ nome: "" }); setFaseServerError(null);` before opening, matching the `onOpenAddDecisao`/`onOpenAddTestemunha`/`onOpenAddFacto` pattern.

### WR-03: Fases status `<select>` remains interactive for view-only users

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:1717-1741`

**Issue:** The per-row status `<select>` has no `disabled` prop tied to `canEditProcessos` — only the adjacent "Guardar" button is disabled for view-only users (`disabled={!canEditProcessos || updateFaseStatus.isPending}`). Every other tab in this file hides the entire edit affordance from view-only users (the `Dialog`/`DialogTrigger` for Partes/Fases/Decisões/Testemunhas/Factos, and the Editar/Apagar buttons in each table row, are all wrapped in `canEditProcessos ? ... : null`). Here, a view-only user can still open and change the dropdown — it just does nothing when they try to save it — which is an inconsistent, confusing UX compared to the rest of the page (and, combined with CR-01, means the one interactive control visible to a view-only user is also the one with a broken save path for edit-capable users).

**Fix:** Add `disabled={!canEditProcessos}` to the `<select>` for consistency with every other edit affordance on this page.

### WR-04: Facto `ordem` input has no client-side validation (accepts negative/non-integer values)

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:1972-1981`

**Issue:** The `ordem` field is deliberately kept outside the Zod-validated `factoForm` (per the in-code comment) and is driven entirely by plain `useState<number>`:

```tsx
<input
  id="facto_ordem"
  type="number"
  min={1}
  ...
  value={factoOrdemDraft}
  onChange={(e) => setFactoOrdemDraft(Number(e.target.value) || 1)}
/>
```

`min={1}` is only an HTML hint (not enforced by React on programmatic value changes), and the `onChange` handler's `Number(e.target.value) || 1` only catches `0`/`NaN`/empty string — a value like `-5` is truthy and passes straight through, and non-integers (`1.5`) are accepted too since there's no `step` constraint or integer check. This value is submitted verbatim as `FactoUpdateRequest.ordem` with zero client-side guard, relying entirely on the backend to reject invalid values.

**Fix:** Clamp/validate in the `onChange` handler, e.g. `Math.max(1, Math.trunc(Number(e.target.value) || 1))`, or fold `ordem` into a proper Zod-validated field (`z.number().int().min(1)`) instead of bypassing the form's validation layer entirely.

## Info

### IN-01: `decisao` vs `decisoes` naming is easy to confuse

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:225, 244`

**Issue:** `decisao` (singular — `useConflictCheckDecisao`, the Conflict Check decision record) and `decisoes` (plural — `useDecisoes`, the list of case Decisões shown in the Decisões tab) are two entirely different domain entities that happen to differ only by one letter, in a 2500+ line component with many other similarly-named locals (`decisaoForm`, `decisaoServerError`, `addDecisao`, `updateDecisao`, `deleteDecisao`, `decisaoData` in the sibling `novo/page.tsx`). No functional bug was found from this (usages are correctly scoped throughout), but it's a maintainability trap for future edits to this file.

**Fix:** Consider renaming the Conflict Check variable to `conflictDecisao`/`conflictCheckDecisao` to visually disambiguate from the Decisões-tab state.

### IN-02: Inconsistent floating-promise handling on click handlers

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:1110, 383` vs. `:1737, 1895, 1902, 2044, 2051, 2221, 2228`

**Issue:** Some `onClick` handlers explicitly wrap the async call with `void` (`onClick={() => void onToggleConcluido(p.id, !p.concluido)}` at line 1110, `void onSubmitTransicaoDirecta(t.acao)` at line 383), while others in the same file invoke the async handler directly with no `void` (`onClick={() => onUpdateFaseStatus(f.id)}`, `onClick={() => onDeleteDecisao(d.id)}`, `onClick={() => onDeleteTestemunha(t.id)}`, `onClick={() => onDeleteFacto(f.id)}`). Not a runtime bug (every one of these functions catches its own errors internally), but it's an inconsistent pattern within one file that a `no-floating-promises` lint rule would flag if ever enabled.

**Fix:** Pick one convention (prefer `void fn()`) and apply it uniformly.

### IN-03: Decisão file input has no `accept` restriction

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:1823-1828`

**Issue:** The Decisão "Anexo" `<input type="file">` has no `accept` attribute, unlike the Documentos tab's `FileDropZone` which restricts to `accept="image/*,application/pdf,application/msword,...,.txt"`. Client-side `accept` is only a UX hint (not a security boundary — the backend must validate regardless), so this isn't a vulnerability, but it's an inconsistent affordance between two file-upload entry points in the same file/phase.

**Fix:** Apply the same (or an appropriately scoped) `accept` value to the Decisão file input for UX parity, if the backend's accepted types for Decisão attachments match Documentos.

### IN-04: Top-level loading/error gate blocks the whole page behind data for tabs the user hasn't opened

**File:** `web/src/app/(dashboard)/processos/[id]/page.tsx:283-286`

**Issue:** `isLoading`/`isError` (gating the Dados card, Conflict Check, Workflow, Prazos, and every tab) require `partes`, `fases`, and `movimentacoes` to all resolve, even though the default tab is Timeline and none of those three resources are needed to render it (Partes/Fases have their own tab-local `isLoading`/`isError` checks that would work fine standalone). Not a v1-scope performance issue since no algorithmic problem exists, but it does mean a slow/erroring Fases or Movimentações fetch will blank the entire page rather than just its own tab.

**Fix:** Consider narrowing the top-level gate to only `processo`/`clientes` (needed for the Dados card lookups), and let each tab manage its own loading/error state, consistent with how Decisões/Testemunhas/Factos/Workflow/Prazos/Timeline/Auditoria already do it.

---

_Reviewed: 2026-07-08T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
