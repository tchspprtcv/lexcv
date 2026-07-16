---
phase: 106-m-dulo-agenda
reviewed: 2026-07-16T23:30:14Z
depth: standard
files_reviewed: 6
files_reviewed_list:
  - web/src/components/shared/date-picker-field.tsx
  - web/src/app/(dashboard)/agenda/novo/page.tsx
  - web/src/app/(dashboard)/agenda/[id]/editar/page.tsx
  - web/src/schemas/eventos.ts
  - web/src/app/(dashboard)/agenda/page.tsx
  - web/src/app/(dashboard)/agenda/[id]/page.tsx
findings:
  critical: 1
  warning: 0
  info: 1
  total: 2
status: issues_found
---

# Phase 106: Code Review Report (re-review after fix pass, iteration 1)

**Reviewed:** 2026-07-16T23:30:14Z
**Depth:** standard
**Files Reviewed:** 6
**Status:** issues_found

## Summary

Re-reviewed Phase 106 after the fix pass (commits `4504a96` WR-01, `b60b942` WR-02, `6fbd2d1` WR-03) applied against the 3 Warning-level findings from the prior `106-REVIEW.md`. Verified each fix against the actual diff (`git show`), re-ran `npx tsc --noEmit` project-wide (reproduces only the same 3 pre-existing unrelated `vitest` module-resolution errors, no new errors), confirmed the installed `zod` version (`4.4.3`), traced `react-day-picker`'s (`9.14.0`) single-select toggle semantics in `node_modules`, and confirmed the backend's `PUT /eventos/{id}` partial-update semantics (`ResourceController.java:2585-2618`) are compatible with the new edit-form payload shape.

**WR-02 and WR-03 are correctly and fully resolved, with no new regressions.** **WR-01's originally-reported defect (typing a time before a date is picked being silently discarded) is fixed as described, but the fix introduces a new, more severe Critical regression** in the same function: clicking the already-selected day a second time in `DatePickerField`'s calendar (a normal, plausible user action) now silently overwrites the field's value with **today's date** instead of leaving it unchanged, with no warning and no way to undo before the popover closes. This affects `dataInicio`, `dataFim`, and `recurrenceEndDate` in both the create and edit Evento forms — i.e. legal-deadline / court-date fields (`Prazo Fatal`, `Audiência`, etc.). See CR-01 below.

## Critical Issues

### CR-01: WR-01's fix introduces silent date corruption to "today" when the user clicks the already-selected calendar day (new regression, not present before this fix pass)

**File:** `web/src/components/shared/date-picker-field.tsx:42-47` (the shared `commit()` function, changed by commit `4504a96`), reachable from every call site: `web/src/app/(dashboard)/agenda/novo/page.tsx:191,205,252` (`dataInicio`, `dataFim`, `recurrenceEndDate`), `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx:251,265` (`dataInicio`, `dataFim`)

**Issue:** The WR-01 fix changed `commit()` from:
```tsx
if (!nextDate) return;
```
to:
```tsx
const base = nextDate ?? new Date();
```
This does fix the originally-reported bug (typing a time before any date is picked, so `dateValue` is `undefined`, no longer silently no-ops). However, `commit()` is *also* invoked from the `Calendar`'s `onSelect` handler (line 71-74: `onSelect={(d) => { commit(d, timePart); setOpen(false); }}`), and the `Calendar` is rendered with `mode="single"` and no `required` prop (`date-picker-field.tsx:68-78`). Per `react-day-picker@9.14.0`'s own selection logic (`node_modules/react-day-picker/dist/esm/selection/useSingle.js:19-24`):
```js
if (!required && selected && selected && isSameDay(triggerDate, selected)) {
  // If the date is the same, clear the selection.
  newDate = undefined;
}
```
clicking the **currently-selected day a second time** calls `onSelect(undefined, ...)` — this is react-day-picker's documented toggle-off behavior for optional single-select (`required?: false` → `onSelect?: OnSelectHandler<Date | undefined>`, confirmed in `node_modules/react-day-picker/dist/esm/types/props.d.ts:599-606`). Before this fix pass, `commit(undefined, timePart)` hit the `if (!nextDate) return;` early-return, so toggling off was a harmless no-op (the popover closed, the field kept its previous value). **After this fix**, the same call now falls through to `base = nextDate ?? new Date()` → `new Date()` (today), so the field is silently overwritten with **today's date** (keeping only the previously-selected time-of-day, for `withTime` fields).

Concretely: a user editing/creating an evento with `dataInicio` set 3 months out (e.g. an `Audiência` or `Prazo Fatal`), reopens the date picker to double-check the date, sees the already-highlighted day, and clicks it again (a completely ordinary "confirm/close" interaction, or a simple misclick) — the field's value is silently replaced with today's date the instant the popover closes (`setOpen(false)` runs unconditionally, regardless of whether `nextDate` was defined). There is no error, no confirmation, and no visual feedback other than the trigger button's label quietly changing to today's date the next time it's rendered/reopened. If the user doesn't notice before submitting, a legal deadline is silently corrupted to today's date. This is a genuinely new failure mode — it did not exist prior to the WR-01 fix (the previous no-op only ever preserved state; it never wrote a wrong value).

**Fix:** Distinguish "no date has ever been chosen yet" (the time-input scenario WR-01 targeted) from "the user explicitly toggled off the current selection via the calendar" (which should be a no-op, matching the pre-fix behavior for that specific path). Do not conflate both cases behind one `nextDate ?? new Date()` fallback in a function shared by both call sites. For example, keep the calendar's `onSelect` guarding against `undefined` (ignore toggle-off, or re-open with the prior date reselected) while only defaulting to `new Date()` from the time `<Input>`'s `onChange`:
```tsx
<Calendar
  mode="single"
  selected={dateValue}
  onSelect={(d) => {
    if (!d) return; // ignore toggle-off — do not silently reset to today
    commit(d, timePart);
    setOpen(false);
  }}
  locale={pt}
  weekStartsOn={0}
  autoFocus
/>
...
<Input
  id={id ? `${id}-time` : undefined}
  type="time"
  className="w-24 shrink-0"
  value={timePart}
  onChange={(e) => commit(dateValue ?? new Date(), e.target.value)} // fallback only here
/>
```
and simplify `commit()` back to requiring a defined date (or keep the `nextDate ?? new Date()` fallback purely as documentation that this function should never be called with `undefined` from the calendar path).

**WR-01 status:** Partially resolved — the originally-reported symptom (time keystroke silently discarded) is fixed, but the fix as applied introduces this new Critical regression via the same shared function. Needs another fix iteration.

## Info

### IN-01: DatePickerField's time `<Input>` still has no associated `<label>` (residual a11y gap, unchanged by WR-02's id passthrough)

**File:** `web/src/components/shared/date-picker-field.tsx:81-88`; call sites `web/src/app/(dashboard)/agenda/novo/page.tsx:185-197,199-211`, `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx:245-257,259-271`
**Issue:** WR-02's fix correctly restores `<Label htmlFor="dataInicio">`/`<Label htmlFor="dataFim">` association with the date-picker trigger `Button` (`id={id}`), and the time `Input` now receives a derived id (`${id}-time`) — but no `<label htmlFor="dataInicio-time">` element exists anywhere, so the time input itself remains unlabelled for assistive technology (it's only reachable/understandable via visual proximity to the date button and its own label). This isn't a new regression (the time input had no `id` and no label before WR-02 either — it's simply an incomplete fix of the underlying a11y gap in the composite field), but it's worth flagging since WR-02 was specifically about label association.
**Fix:** Add a visually-hidden label for the time input, e.g. `<label htmlFor={id ? \`${id}-time\` : undefined} className="sr-only">Hora</label>` next to it, or set `aria-label="Hora"` directly on the `Input`.

---

## WR-02 and WR-03 verification detail (no new issues)

**WR-02 (DatePickerField id/name passthrough):** Confirmed correct. `id` prop added to the component signature, applied to the trigger `Button` (which spreads `...props` including `id` onto the native `<button>`/Radix `Slot`, per `web/src/components/ui/button.tsx:39-48` — no forwarding gap). All 5 call sites (`dataInicio`/`dataFim` in both create and edit forms, `recurrenceEndDate` in create) now pass matching `id` values that line up with their pre-existing `<Label htmlFor>`. `git diff` for `b60b942` shows no unintended changes elsewhere. Resolved.

**WR-03 (edit form schema mismatch):** Confirmed correct and sound. The fixer's deviation from the review's originally-suggested `eventoFormSchema.innerType().omit({...})` patch was necessary and well-reasoned — `zod@4.4.3` (confirmed installed version) throws when `.omit()` is called on a schema carrying `.refine()`/`.superRefine()`. The implemented approach (refinement-free `eventoBaseObjectSchema`, with `eventoFormSchema` and `eventoEditFormSchema` each deriving from it independently) is a clean, idiomatic solution. Verified:
- `eventoEditFormSchema`'s shape genuinely excludes `recurrenceRule`/`recurrenceEndDate` (`.omit()` on a plain `ZodObject`, not a `ZodEffects` — valid in v4).
- The shared `dataFimRefinementOptions.path` (a mutable `string[]`, not `as const`) avoids the readonly-tuple TS error the fix report mentions.
- `agenda/[id]/editar/page.tsx` correctly switched its resolver, `defaultValues`, `form.reset`, `onSubmit`, and `onInvalid` typing to `eventoEditFormSchema`/`EventoEditFormValues`, and no longer carries `recurrenceRule`/`recurrenceEndDate` through `form.reset()` (verified via `git show 6fbd2d1`).
- Checked the backend's `PUT /eventos/{id}` (`ResourceController.java:2556-2621`): it is a partial update (`if (payload.getX() != null) evento.setX(...)`) for every field including `recurrenceRule`/`recurrenceEndDate`, so the edit form's payload never including those keys does **not** wipe them server-side — an evento's recurrence metadata survives edits made through this form, consistent with the documented product constraint (recurrence can only be set at creation). No new data-loss risk introduced by removing the "accidental" validation block.
- `npx tsc --noEmit` (project-wide, re-run independently for this review) reproduces only the same 3 pre-existing `TS2307: Cannot find module 'vitest'` errors in unrelated `*.test.ts` files — no new type errors.
- No other consumer of `eventoFormSchema`/`EventoFormValues` exists in the codebase that could have been broken by the schema split (`grep` confirms only `agenda/novo/page.tsx` and `agenda/[id]/editar/page.tsx` import these symbols, and each now imports the correct one).

Resolved, no new issues.

---

_Reviewed: 2026-07-16T23:30:14Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
