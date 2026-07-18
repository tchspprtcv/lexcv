---
phase: 106-m-dulo-agenda
fixed_at: 2026-07-16T23:20:29Z
review_path: .planning/phases/LEXCV-106-m-dulo-agenda/106-REVIEW.md
iteration: 1
findings_in_scope: 3
fixed: 3
skipped: 0
status: all_fixed
---

# Phase 106: Code Review Fix Report

**Fixed at:** 2026-07-16T23:20:29Z
**Source review:** .planning/phases/LEXCV-106-m-dulo-agenda/106-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 3 (Critical + Warning; Info findings IN-01/IN-02 excluded per default fix_scope)
- Fixed: 3
- Skipped: 0

## Fixed Issues

### WR-01: DatePickerField's time input silently no-ops until a date has been selected

**Files modified:** `web/src/components/shared/date-picker-field.tsx`
**Commit:** 4504a96
**Applied fix:** `commit()` now falls back to `new Date()` when `nextDate` is `undefined` instead of returning early, so typing a time before a date is picked is no longer silently discarded. Matches the review's suggested patch exactly.

### WR-02: DatePickerField has no id/name passthrough, breaking `<Label htmlFor>` association

**Files modified:** `web/src/components/shared/date-picker-field.tsx`, `web/src/app/(dashboard)/agenda/novo/page.tsx`, `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx`
**Commit:** b60b942
**Applied fix:** Added an optional `id` prop to `DatePickerField`, applied to the trigger `Button`; the time `Input` derives `${id}-time`. Passed `id="dataInicio"` / `id="dataFim"` / `id="recurrenceEndDate"` from all call sites in both the create and edit Evento forms (the edit form only has `dataInicio`/`dataFim`, consistent with WR-03's finding that it has no recurrence UI).

### WR-03: Shared eventoFormSchema's recurrence validation can make the edit form permanently unsavable

**Files modified:** `web/src/schemas/eventos.ts`, `web/src/app/(dashboard)/agenda/[id]/editar/page.tsx`
**Commit:** 6fbd2d1
**Applied fix:** Adapted from the review's suggested patch, which did not compile as written. The project uses **zod v4.4.3**, which throws a runtime error (`.omit() cannot be used on object schemas containing refinements`) when `.omit()` is called on a schema that already has `.refine()`/`.superRefine()` attached — the review's suggested `eventoFormSchema.innerType().omit({...})` doesn't work under this zod version (`innerType()` doesn't even exist on the chained-refinement result in v4). Instead: extracted a refinement-free `eventoBaseObjectSchema`; `eventoFormSchema` (create form) now derives from it with both the `dataFim >= dataInicio` refine and the recurrence `superRefine`; `eventoEditFormSchema` derives from it by `.omit({recurrenceRule: true, recurrenceEndDate: true})` then re-applying only the `dataFim >= dataInicio` refine (still relevant since both fields remain in the edit form). Updated `agenda/[id]/editar/page.tsx` to use `eventoEditFormSchema`/`EventoEditFormValues` for its resolver, `defaultValues`, `form.reset`, `onSubmit`, and `onInvalid` typing, and removed the now-unused `recurrenceRule`/`recurrenceEndDate` fields from its `form.reset()` call (this form never reads or submits them).

Verified via a standalone runtime script (not committed) that: the create schema still requires `recurrenceEndDate` when recurring and still rejects `dataFim < dataInicio`; the edit schema accepts a valid payload with no recurrence fields present and still rejects `dataFim < dataInicio`; the edit schema's shape has no `recurrenceRule`/`recurrenceEndDate` keys.

## Skipped Issues

None — all 3 in-scope findings were fixed.

## Verification

- **Tier 1 (all 3 commits):** each modified file was re-read after editing to confirm the fix text was present and surrounding code intact.
- **Tier 2 — TypeScript (`npx tsc --noEmit`, project-wide):** baseline (pre-fix) showed 3 pre-existing errors, all `TS2307: Cannot find module 'vitest'` in unrelated `*.test.ts` files. After each of the 3 commits, `tsc --noEmit` was re-run and showed the same 3 pre-existing errors and no new ones (one intermediate type error — a `readonly` tuple from `path: ["dataFim"] as const` being incompatible with zod's mutable `PropertyKey[]` — was caught by this check during WR-03's fix, corrected before committing, and reverified clean).
- **Final full-project `pnpm lint`:** 6 errors / 18 warnings total across the project, but **0 errors and 0 warnings** in all 4 files this fix pass touched (`date-picker-field.tsx`, `schemas/eventos.ts`, `agenda/novo/page.tsx`, `agenda/[id]/editar/page.tsx`), except one pre-existing `react-hooks/incompatible-library` warning on an unmodified line (`agenda/novo/page.tsx:65`, `form.watch("recurrenceRule")`) confirmed unchanged from the pre-fix commit. All other errors/warnings are in files untouched by this fix pass.
- **Final full-project `pnpm build`:** could not be run to completion inside the isolated git worktree used mid-session — Turbopack panicked on the `node_modules` Windows junction used to make the worktree's dependencies available (`Symlink [project]/node_modules is invalid, it points out of the filesystem root`), an artifact of the isolation mechanism, not of the code changes. After the cleanup tail fast-forwarded `master` and the worktree was torn down, `pnpm build` was re-run in the primary working tree (native `node_modules`, no junction) and **completed successfully** — `Compiled successfully in 19.6s`, TypeScript pass finished clean, all 24 routes (including `/agenda/novo` and `/agenda/[id]/editar`) generated without error. `pnpm lint` was also re-run in the primary working tree post-merge and reproduced the identical pre-existing 6 errors / 18 warnings in files untouched by this fix pass, with 0 new errors/warnings in any of the 4 files this fix pass modified.

---

_Fixed: 2026-07-16T23:20:29Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
