---
phase: LEXCV-101-funda-o-cli-init-e-design-tokens
reviewed: 2026-07-16T00:00:00Z
depth: standard
files_reviewed: 34
files_reviewed_list:
  - web/components.json
  - web/package.json
  - web/src/app/globals.css
  - web/src/app/layout.tsx
  - web/src/components/ui/accordion.tsx
  - web/src/components/ui/alert-dialog.tsx
  - web/src/components/ui/avatar.tsx
  - web/src/components/ui/breadcrumb.tsx
  - web/src/components/ui/button.tsx
  - web/src/components/ui/calendar.tsx
  - web/src/components/ui/checkbox.tsx
  - web/src/components/ui/command.tsx
  - web/src/components/ui/dialog.tsx
  - web/src/components/ui/dropdown-menu.tsx
  - web/src/components/ui/empty.tsx
  - web/src/components/ui/input-group.tsx
  - web/src/components/ui/label.tsx
  - web/src/components/ui/native-select.tsx
  - web/src/components/ui/navigation-menu.tsx
  - web/src/components/ui/popover.tsx
  - web/src/components/ui/progress.tsx
  - web/src/components/ui/radio-group.tsx
  - web/src/components/ui/select.tsx
  - web/src/components/ui/separator.tsx
  - web/src/components/ui/sheet.tsx
  - web/src/components/ui/skeleton.tsx
  - web/src/components/ui/sonner.tsx
  - web/src/components/ui/switch.tsx
  - web/src/components/ui/tabs.tsx
  - web/src/components/ui/tooltip.tsx
  - web/src/hooks/use-toast.ts
  - webpage/components.json
  - webpage/package.json
  - webpage/src/app/globals.css
findings:
  critical: 0
  warning: 0
  info: 6
  total: 6
status: issues_found
---

# Phase LEXCV-101: Code Review Report (Re-review after fix pass)

**Reviewed:** 2026-07-16T00:00:00Z
**Depth:** standard
**Files Reviewed:** 34
**Status:** issues_found

## Summary

This is a re-review of the same 34-file scope after `101-REVIEW-FIX.md` iteration 1, which addressed 2 of the 3 Warnings from the prior review (`WR-01`, `WR-03`) and deliberately skipped one (`WR-02`). All 5 prior Info-level findings were out of the fix pass's declared scope and were left untouched. This pass re-verifies the two fixes end-to-end (not just re-reading the diff), re-validates the WR-02 skip rationale against the actual roadmap text, and re-scans the full file set for anything the fix pass itself might have introduced.

**WR-01 (`empty.tsx`) — verified FIXED.** `EmptyDescription` now renders a `<p>` (`empty.tsx:73`), matching its declared `React.ComponentProps<"p">` type and no longer diverging from its own type contract. Confirmed there are zero existing consumers of `EmptyDescription` anywhere in `web/src` (repo-wide grep), so this is a pure type/implementation correction with no call-site regression risk. Commit `70a68d0` touches only this one file/line, as claimed.

**WR-03 (`calendar.tsx`) — verified FIXED as a mitigation (root cause intentionally left in place, correctly).** The `ButtonWithRef` unsafe cast (`calendar.tsx:51-53`) is unchanged, and per the fix report this was deliberate: `button.tsx` is one of the exact 14 components named in `ROADMAP.md:316` ("Phase 102: Reconciliação do Design System" — `button`, `dialog`, `alert-dialog`, `card`, `table`, `sheet`, `badge`, `input`, `label`, `popover`, `radio-group`, `switch`, `textarea`), confirmed by direct read of the roadmap. Editing `button.tsx` now (e.g., adding `React.forwardRef`) would front-run that phase's planned reconciliation work. Instead, a second `useEffect` (`calendar.tsx:243-253`) was added to `CalendarDayButton` that `console.warn`s in dev mode if `ref.current` is still `null` after mount — converting a previously silent regression mode into a loud one. Traced the underlying assumption itself (not just accepted the comment): `button.tsx:39` (`export function Button({ className, variant, size, asChild, ...props }: ButtonProps)`) does not destructure `ref`, so under React 19's ref-as-prop model, `ref` remains in `...props` and is spread onto `<Comp {...props} />` (`button.tsx:42-46`), where `Comp` is either the host `"button"` or `SlotPrimitive.Slot` — both accept the ref correctly. Confirmed the installed React version is `19.2.4` (`web/package.json:23,25`), so the assumption the code and comment rely on is currently true, not just "plausible." Commit `97e1fbd` touches only this one file, as claimed.

**WR-02 — re-assessed, deliberate-skip rationale CONFIRMED to hold.** Re-read `ROADMAP.md` directly (not just trusted the fix report's citation): line 310-317 defines Phase 102 with the goal "Os 14 componentes hand-rolled existentes estão reconciliados com o registo oficial..." and explicitly lists `button`, `dialog`, `alert-dialog`, `sheet`, `popover`, `radio-group`, `switch` among its 14 named components — i.e. all 7 files WR-02 flagged. Independently re-ran `git diff 525eac9..HEAD` against all 7 files: each still shows only the 1-2 line Radix-import-path change (`button.tsx` is 2 lines: import + `Comp` assignment; the other 6 are 1 line each), confirming no re-skin occurred during or after the fix pass and the two disconnected color systems (hardcoded neutral/slate palette + `dark:bg-[#020617]` magic hex vs. the new semantic tokens) still exist exactly as originally described. The skip is correctly justified: this is a named, sequenced follow-up phase (not an ad-hoc TODO), and re-skinning these files now would risk conflicting edits when Phase 102 executes its own planned migration. No further action needed in this phase.

**New verification performed this pass (not present in the original review):** ran `npx tsc --noEmit` and `eslint` directly against all 34 in-scope files rather than relying on manual reading alone (the fix report itself notes `node_modules`/`tsc` were unavailable in the fixer's isolated worktree, so this closes that verification gap). `tsc` reports only the 3 pre-existing, already-documented `vitest`-resolution errors in unrelated test files (tracked in `deferred-items.md`, not part of this phase's scope) — no new type errors from either fix. `eslint` reports exactly one warning across the entire 34-file set, and it is new, introduced by the WR-03 fix itself (see IN-06 below).

No critical or security issues found. No Warnings remain open (0 outstanding — 2 fixed, 1 correctly deferred to a named future phase). 6 Info-level items remain, 5 carried over unchanged from the prior review (untouched by design, out of fix-pass scope) plus 1 new item this pass caught in the fix itself.

## Info

### IN-01: New — Unnecessary `eslint-disable` directive introduced by the WR-03 fix

**File:** `web/src/components/ui/calendar.tsx:252`
**Issue:** The new mount-effect added to fix WR-03 carries `// eslint-disable-next-line react-hooks/exhaustive-deps` above its empty `[]` dependency array. Running `eslint` directly on this file (not just reading it) shows this directive is a no-op: `react-hooks/exhaustive-deps` does not flag this effect at all, because the only value read inside it is `ref` — a `useRef` return value, which `eslint-plugin-react-hooks` already recognizes as referentially stable and exempts from the dependency requirement. Result: `Unused eslint-disable directive (no problems were reported from 'react-hooks/exhaustive-deps')`. This is the only lint warning anywhere across all 34 files reviewed in this phase (verified by running eslint against the full in-scope file list) — i.e. a warning that did not exist before this fix pass and was introduced by it. Harmless today (doesn't fail the `lint` script, which has no `--max-warnings` gate), but it is dead suppression code and would mask a real future exhaustive-deps violation on this same effect if one were ever introduced, since the disable comment would silently continue to apply.
**Fix:** Remove the now-unnecessary directive; the effect is already lint-clean without it:
```tsx
React.useEffect(() => {
  if (process.env.NODE_ENV !== "production" && ref.current === null) {
    console.warn(
      "[Calendar] CalendarDayButton did not receive a DOM ref from Button. " +
        "Button (button.tsx) may no longer forward `ref` through its props " +
        "spread to the host element -- roving-tabindex keyboard focus in " +
        "the calendar will silently stop working. See 101-REVIEW.md WR-03."
    )
  }
}, [])
```

### IN-02: Carried over — Dead `cn-toast` class name in Sonner Toaster config

**File:** `web/src/components/ui/sonner.tsx:42`
**Issue:** Unchanged since the prior review. `toastOptions.classNames.toast: "cn-toast"` applies a CSS class not defined anywhere in the repo (confirmed again via repo-wide search this pass). Still a no-op. Correctly out of fix-pass scope (Info-level, not selected for this iteration).
**Fix:** Either remove the dead `classNames` override, or add the intended `.cn-toast` rule to `globals.css` if custom styling was actually meant to land here.

### IN-03: Carried over — `useToast()` hook and bare `toast()` wrapper are unused dead code

**File:** `web/src/hooks/use-toast.ts:19-21, 40-45`
**Issue:** Unchanged since the prior review; file was not touched by the fix pass. Every real call site still uses `toast.success(...)`/`toast.error(...)`; `useToast()` and the bare `toast(message, options)` wrapper remain zero-call-site, intentional forward-compatibility shims per the in-file comment.
**Fix:** No action required if the compatibility-shim rationale is accepted; consider cross-referencing in `101-PATTERNS.md`/`deferred-items.md` so it isn't mistaken for accidental dead code later.

### IN-04: Carried over — Inconsistent `Slot` primitive aliasing style

**File:** `web/src/components/ui/breadcrumb.tsx:2, 48`, `web/src/components/ui/button.tsx:2, 40`
**Issue:** Unchanged since the prior review (re-confirmed this pass: `breadcrumb.tsx` still does `import { Slot } from "radix-ui"` / `Slot.Root`, while `button.tsx` still does `import { Slot as SlotPrimitive } from "radix-ui"` / `SlotPrimitive.Slot`). Functionally identical (both resolve to the same underlying component), but an avoidable inconsistency from the same migration pass.
**Fix:** Standardize on one alias/property pair (e.g. `SlotPrimitive` + `.Root`) across both files.

### IN-05: Carried over — `buttonVariants` CVA config duplicated verbatim between `button.tsx` and `calendar.tsx`

**File:** `web/src/components/ui/calendar.tsx:21-45`, `web/src/components/ui/button.tsx:7-31`
**Issue:** Unchanged since the prior review; `button.tsx` was not edited in this fix pass (confirmed: `button.tsx`'s only diff since the original review is the 2-line Radix-import change tied to the deferred WR-02 item, not the WR-03 fix). The byte-for-byte duplicated CVA config remains, still documented in-code as a bounded, intentional duplication pending Phase 102.
**Fix:** Export `buttonVariants` from `button.tsx` and import it in `calendar.tsx` instead of duplicating the CVA config, once `button.tsx` is back in scope for editing (Phase 102).

### IN-06: Carried over — `shadcn` CLI package declared under `dependencies` instead of `devDependencies`

**File:** `web/package.json:27` (inside the `dependencies` block, lines 12-32)
**Issue:** Unchanged since the prior review (re-confirmed this pass by re-reading `package.json`). `"shadcn": "^4.13.0"` is still listed under `dependencies` rather than `devDependencies`, alongside its build-tool siblings `tailwindcss`/`@tailwindcss/postcss`, which are correctly under `devDependencies`.
**Fix:** Move `"shadcn": "^4.13.0"` from `dependencies` to `devDependencies` in `web/package.json`.

---

_Reviewed: 2026-07-16T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
