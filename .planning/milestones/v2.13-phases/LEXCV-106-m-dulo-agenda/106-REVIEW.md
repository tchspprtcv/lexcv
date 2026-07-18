---
phase: 106-m-dulo-agenda
reviewed: 2026-07-16T23:59:00Z
depth: standard
files_reviewed: 1
files_reviewed_list:
  - web/src/components/shared/date-picker-field.tsx
findings:
  critical: 0
  warning: 0
  info: 1
  total: 1
status: issues_found
---

# Phase 106: Code Review Report (re-review after fix pass, iteration 3 — FINAL)

**Reviewed:** 2026-07-16T23:59:00Z
**Depth:** standard
**Files Reviewed:** 1 (targeted re-review of `web/src/components/shared/date-picker-field.tsx`, per this iteration's explicit scope; iteration 3 of a max-3 fix/re-review loop)
**Status:** issues_found (one carried-forward, non-blocking Info item only — no Critical or Warning findings remain)

## Summary

This is the final re-review of Phase 106 (Módulo Agenda), scoped exclusively to confirming resolution of **CR-01** (silent date corruption to "today" on calendar reclick), the sole Critical finding from iteration 2 (`106-REVIEW.md`, previous version). The fix — adding `required` to the `<Calendar mode="single" required ...>` element (`date-picker-field.tsx:70`) — landed in commit `248a3e1`.

**Verdict: CR-01 is fully resolved, with no new issues introduced by the fix.** Verification performed:

1. **Root-cause confirmation in `react-day-picker@9.14.0` source** (`node_modules/react-day-picker/dist/esm/selection/useSingle.js:19-24`):
   ```js
   const select = (triggerDate, modifiers, e) => {
       let newDate = triggerDate;
       if (!required && selected && selected && isSameDay(triggerDate, selected)) {
           // If the date is the same, clear the selection.
           newDate = undefined;
       }
       ...
       onSelect?.(newDate, triggerDate, modifiers, e);
       return newDate;
   };
   ```
   With `required` now `true`, the `!required` operand is `false`, so the entire `if` short-circuits and `newDate` is *never* set to `undefined` — regardless of whether the clicked day is the same as the currently-selected day. `onSelect` is therefore always invoked with a defined `Date` on every click, including a reclick of the already-selected day.

2. **Call-site consequence** (`date-picker-field.tsx:72-75`): `onSelect={(d) => { commit(d, timePart); setOpen(false); }}` — `d` is now always defined, so `commit()`'s `const base = nextDate ?? new Date()` (the WR-01 fix, line 43) takes the `nextDate` branch and reconstructs the *same* `datePart` the field already held. Reclicking the selected day is now a true no-op for the stored value (identical date, unchanged `timePart`), matching the original pre-WR-01 behavior (`if (!nextDate) return;`) — just enforced at the picker-library level instead of via the app's now-removed guard. The previously reachable "silently overwritten with today's date" path (`d === undefined` → `base = new Date()`) is no longer reachable from the `Calendar`'s `onSelect` at all.

3. **Type-safety check (discriminated union):** `react-day-picker`'s `DayPickerProps` is a union including `PropsSingle` (`mode: "single"; required?: false; onSelect?: OnSelectHandler<Date | undefined>`) and `PropsSingleRequired` (`mode: "single"; required: true; selected: Date | undefined; onSelect?: OnSelectHandler<Date>`), per `node_modules/react-day-picker/dist/esm/types/props.d.ts`. Adding the literal `required` prop moves the JSX call site's inferred type to `PropsSingleRequired`, so the `onSelect` callback parameter `d` is now typed `Date` (not `Date | undefined`) — passing a `Date` into `commit(nextDate: Date | undefined, ...)` is a trivial, always-valid widening, not a narrowing/assertion. `selected` remains typed `Date | undefined` in *both* union members, so `selected={dateValue}` (itself `Date | undefined` from `parseDateOnly`) is unaffected. Ran `npx tsc --noEmit` project-wide: reproduces only the same 3 pre-existing, unrelated `TS2307: Cannot find module 'vitest'` errors in `*.test.ts` files — **zero new type errors**. Also ran `npx eslint src/components/shared/date-picker-field.tsx`: clean, no issues.

4. **Blast radius:** Grepped the codebase for `<Calendar` usages — the shared `Calendar` wrapper (`components/ui/calendar.tsx`) is consumed only by `date-picker-field.tsx`. No other call site could be affected by this change.

5. **No UX regression from making the day "un-deselectable" via reclick:** This component never exposed a "clear the date" affordance in the first place — there is no clear/X button in the `PopoverContent`, and `onChange` is typed `(v: string) => void` (never called with `undefined`). The one genuinely optional/clearable field that uses this component, `recurrenceEndDate`, is cleared by the parent form conditionally unmounting the entire `<Controller>`/`DatePickerField` block when `recurrenceRule` reverts to `"NONE"` (`web/src/app/(dashboard)/agenda/novo/page.tsx:245-259`), not by reclicking a day in the calendar. Even *before* the WR-01 fix pass began (i.e., in the original, non-regressed code), reclicking the selected day was already a harmless no-op via `if (!nextDate) return;` — so `required` simply restores that exact original, intended behavior via the library's own selection-mode gate, rather than reintroducing any previously-available "deselect" capability that a user might now miss. Net UX for the end user is unchanged from the original (pre-bug) design.

6. **Live-browser verification** described in the fix report (click a day, reclick the same day, confirm the trigger label still shows the originally-picked date) is fully consistent with and explained by the source-level trace above.

**CR-01: RESOLVED.** No new Critical or Warning issues found in this file during this iteration.

## Info

### IN-01: DatePickerField's time `<Input>` still has no associated `<label>` (carried forward, unchanged, non-blocking)

**File:** `web/src/components/shared/date-picker-field.tsx:82-89`
**Issue:** Unchanged since iteration 2's review. The time `<Input>` (rendered when `withTime`) receives a derived `id` (`${id}-time`, from WR-02) but no `<label htmlFor="...-time">` element exists anywhere (checked call sites in `agenda/novo/page.tsx` and `agenda/[id]/editar/page.tsx` again — still absent). The time input remains unlabelled for assistive technology, reachable only via visual proximity to the adjacent date button's label. This fix iteration was correctly scoped to CR-01 only and did not touch this area; the item is not a regression, just a pre-existing, previously-flagged gap that remains open.
**Fix:** Add a visually-hidden label, e.g. `<label htmlFor={id ? \`${id}-time\` : undefined} className="sr-only">Hora</label>`, or set `aria-label="Hora"` directly on the `Input`. Deferred — recommended as a follow-up, not blocking.

---

## Final Verdict for Phase 106

- **CR-01 (Critical):** RESOLVED. Verified at the `react-day-picker` source level, the type-system level, and consistent with the reported live-browser confirmation. No new regression introduced by the `required` prop.
- **WR-01, WR-02, WR-03 (Warnings, iteration 2):** Previously confirmed resolved, no new issues (see prior iteration's detailed verification, retained for context — this iteration re-confirms nothing in that area was disturbed by the CR-01 fix).
- **IN-01 (Info):** Still open, accessibility-only, non-blocking, unchanged by this fix.

**Overall: Phase 106's code review is APPROVED — no Critical or Warning findings remain.** One deferred, non-blocking Info item (IN-01) is tracked for a future pass. This closes the fix/re-review loop at iteration 3 with a clean bill of health modulo that single cosmetic accessibility gap.

_Note (process, non-scored):_ the `required` fix landed bundled inside commit `248a3e1`, whose message ("eliminate +1h UTC-shift bug in Evento date/time round-trips") does not mention the CR-01 fix it also contains. This is a minor commit-hygiene observation, not a code defect in the reviewed file, and does not affect this verdict.

---

_Reviewed: 2026-07-16T23:59:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
