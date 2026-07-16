---
phase: 106-m-dulo-agenda
reviewed: 2026-07-16T00:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - web/src/components/shared/date-picker-field.tsx
  - web/src/app/(dashboard)/agenda/page.tsx
  - web/src/app/(dashboard)/agenda/[id]/page.tsx
  - web/src/app/(dashboard)/agenda/novo/page.tsx
  - web/src/app/(dashboard)/agenda/[id]/editar/page.tsx
findings:
  critical: 0
  warning: 3
  info: 2
  total: 5
status: issues_found
---

# Phase 106: Code Review Report

**Reviewed:** 2026-07-16
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Reviewed the 5 files touched by Phase 106 (shared `DatePickerField`, Agenda list filters, and the create/edit/detail Evento pages). The RBAC `isFetched` race fix was applied consistently and correctly across all 4 Agenda pages, and the previously-fixed date-only UTC/local timezone bug (`parseDateOnly`) is confirmed correctly implemented — no regression there. No security vulnerabilities, injection vectors, or hardcoded secrets were found.

Three Warning-level issues were found, all stemming from gaps in the new `DatePickerField` component's contract (no `id`/`name` passthrough) and a cross-file schema/UI mismatch introduced by the edit form's deliberate omission of recurrence UI. Two Info-level code-quality items are also noted. Per this review's known context, the already-fixed date-only off-by-one bug is not re-flagged, and the separately-tracked +1h `.toISOString()` timezone bug in `onSubmit` (documented in `deferred-items.md`, confirmed pre-existing and out of this phase's scope) is not re-flagged as new — see reference in WR-03's related-issue note below for context only.

## Warnings

### WR-01: DatePickerField's time input silently no-ops until a date has been selected

**File:** `web/src/components/shared/date-picker-field.tsx:40-41,79-84`
**Issue:** `commit(nextDate, nextTime)` returns immediately if `nextDate` is falsy (`if (!nextDate) return;`). The time `<Input>`'s `onChange` calls `commit(dateValue, e.target.value)`, passing the *current* `dateValue` (derived from `parseDateOnly(value)`) as `nextDate`. When no date has been picked yet (`value` is `""`, the default for a new `dataInicio`/`dataFim` field), `dateValue` is `undefined`, so typing a time is a complete no-op: `onChange` (the RHF field setter) is never called, and on the next render the time input's `value` prop reverts to the derived default (`"00:00"`), erasing whatever the user just typed with no error or visual feedback. Users who try to set the time before picking the date lose their input silently.
**Fix:** Either disable/hide the time `Input` until a date is selected, or make `commit` tolerant of a not-yet-selected date by falling back to "today" so the time keystroke is not discarded:
```tsx
function commit(nextDate: Date | undefined, nextTime: string) {
  const base = nextDate ?? new Date();
  const pad = (n: number) => String(n).padStart(2, "0");
  const datePart = `${base.getFullYear()}-${pad(base.getMonth() + 1)}-${pad(base.getDate())}`;
  onChange(withTime ? `${datePart}T${nextTime}` : datePart);
}
```

### WR-02: DatePickerField has no `id`/`name` passthrough, breaking `<Label htmlFor>` association

**File:** `web/src/components/shared/date-picker-field.tsx:27-35` (component signature has no `id`/`name`/`aria-*` prop)
**Also affects:** `web/src/app/(dashboard)/agenda/novo/page.tsx:185-197` (`dataInicio`), `:199-211` (`dataFim`), `:246-258` (`recurrenceEndDate`); `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx:248-260` (`dataInicio`), `:262-274` (`dataFim`)
**Issue:** Every call site keeps `<Label htmlFor="dataInicio">Início</Label>` (etc.) immediately before the `Controller`-wrapped `<DatePickerField>`, but `DatePickerField` never renders an element with `id="dataInicio"` (it has no `id` prop at all — the trigger is a plain `<Button type="button">` and the optional `<Input type="time">` also gets no `id`). This is a regression versus the previous native `<Input id="dataInicio" type="datetime-local">`: the label is now visually adjacent but not programmatically associated with any focusable control, so clicking the label no longer focuses/opens the picker, and screen readers no longer announce the field name when the trigger button receives focus.
**Fix:** Add an `id` prop to `DatePickerField` and apply it to the trigger `Button` (and forward a derived id to the time `Input`, e.g. `${id}-time`):
```tsx
export function DatePickerField({
  id,
  value,
  onChange,
  withTime = false,
}: { id?: string; value: string | undefined; onChange: (v: string) => void; withTime?: boolean }) {
  ...
  <Button id={id} type="button" ...>
  ...
  {withTime ? <Input id={id ? `${id}-time` : undefined} type="time" ... /> : null}
```
and pass `id="dataInicio"` / `id="dataFim"` / `id="recurrenceEndDate"` from each call site.

### WR-03: Shared `eventoFormSchema`'s recurrence validation can make the edit form permanently unsavable for some eventos

**File:** `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx:85-113` (form + `form.reset`, no recurrenceRule/recurrenceEndDate UI anywhere in this file) cross-referenced with `web/src/schemas/eventos.ts:37-45` (`superRefine`)
**Issue:** `agenda/[id]/editar/page.tsx` reuses `eventoFormSchema` (shared with the create form), whose `superRefine` requires `recurrenceEndDate` whenever `recurrenceRule !== "NONE"`. The edit form's `form.reset()` (line 110-111) still populates both `recurrenceRule` and `recurrenceEndDate` from `evento.data`, but this form has **no UI field for either** (by design, per `106-CONTEXT.md`'s deferred-items note). The backend's `Evento` entity (`backend/.../models/Evento.java:46-50`) has no `NOT NULL`/check constraint tying `recurrence_end_date` to `recurrence_rule`, so it is possible (e.g. via direct API/DB access, a future recurrence-editing feature, or partial data) for an evento to have `recurrenceRule` set and `recurrenceEndDate` null. Editing *any* such evento through this form will always fail validation on submit — `onInvalid` (lines 138-147) surfaces a generic banner ("Não foi possível guardar: A data de fim da recorrência é obrigatória.") referencing a field that doesn't exist anywhere in the visible form, with no way for the user to resolve it. The form becomes permanently unusable for that record via the UI.
**Fix:** Either (a) strip `recurrenceRule`/`recurrenceEndDate` out of the values passed to the edit form's resolver before validation (e.g. validate against a `.omit({ recurrenceRule: true, recurrenceEndDate: true })` variant of the schema in the edit form), or (b) don't carry `recurrenceRule`/`recurrenceEndDate` into `form.reset()` at all for this form, since they are never read or submitted:
```tsx
// schemas/eventos.ts
export const eventoEditFormSchema = eventoFormSchema.innerType().omit({
  recurrenceRule: true,
  recurrenceEndDate: true,
});
```

## Info

### IN-01: Dead branch in `buildMonthGrid` (pre-existing, in a reviewed file)

**File:** `web/src/app/(dashboard)/agenda/page.tsx:569-570`
**Issue:** `if (end.getDay() === 6 && days.length === 42) return days; return days;` — both branches return the identical `days` value, making the `if` a no-op. This predates Phase 106 (the monthly grid was explicitly left byte-for-byte unchanged per `106-CONTEXT.md`), but it's still present in a file this phase modified.
**Fix:** Remove the dead conditional: `return days;`.

### IN-02: Inconsistent RBAC prop-passing pattern across the 4 Agenda pages

**File:** `web/src/app/(dashboard)/agenda/novo/page.tsx:26-48`, `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx:46-82`
**Issue:** `agenda/page.tsx` and `agenda/[id]/page.tsx` compute `canCreateAgenda`/`canEditAgenda` once in the outer gate component and pass it down as a prop to the `*Content` component. `agenda/novo/page.tsx` and `agenda/[id]/editar/page.tsx` instead call `usePermissions()` a second time inside the nested `*Content` component and recompute `canCreateAgenda`/`canEditAgenda` independently. Functionally harmless (the underlying query is cached), but it's an inconsistent pattern across files touched in the same phase, and duplicates the permission-derivation logic.
**Fix:** Pass `canCreateAgenda`/`canEditAgenda` down as a prop from the outer gate component, matching the pattern already used in `agenda/page.tsx`/`agenda/[id]/page.tsx`.

---

_Reviewed: 2026-07-16_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
