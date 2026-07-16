---
phase: LEXCV-102-reconcilia-o-do-design-system
reviewed: 2026-07-16T02:20:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - web/package.json
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/settings/page.tsx
  - web/src/app/providers.tsx
  - web/src/components/shared/dashboard-shell.tsx
  - web/src/components/ui/alert-dialog.tsx
  - web/src/components/ui/breadcrumb.tsx
  - web/src/components/ui/button.tsx
  - web/src/components/ui/calendar.tsx
  - web/src/components/ui/card.tsx
  - web/src/components/ui/dialog.tsx
  - web/src/components/ui/popover.tsx
  - web/src/components/ui/sheet.tsx
  - web/src/components/ui/table.tsx
findings:
  critical: 0
  warning: 0
  info: 3
  total: 3
status: issues_found
---

# Phase LEXCV-102: Code Review Report (re-review after fix pass)

**Reviewed:** 2026-07-16T02:20:00Z
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found (Info-only; both prior Warnings resolved)

## Summary

This is a re-review of the same 14-file scope as `102-REVIEW.md` (2026-07-16T01:52:00Z), performed after the fix pass recorded in `102-REVIEW-FIX.md`. Both in-scope findings from the prior review — WR-01 (mobile drawer logout button missing Tooltip/`aria-label`) and WR-02 (`popover.tsx` hardcoded `bg-white` instead of `bg-popover`) — were re-verified directly against the two fix commits (`96d3c24`, `b7019d3`) and against the current file contents.

**WR-01 — verified fixed, correct and complete.** `web/src/components/shared/dashboard-shell.tsx`'s mobile `<Sheet>` drawer logout button (now lines 240-247) is wrapped in `<Tooltip><TooltipTrigger asChild>...<TooltipContent>` and carries `aria-label="Terminar sessão"`, exactly mirroring the desktop sidebar's logout button (lines 155-162) token-for-token (same label text, same icon, same handler). `Tooltip`/`TooltipTrigger`/`TooltipContent` were already imported in the file, so the diff is minimal (`+8/-3`) and self-contained. `npx tsc --noEmit` and `npx eslint` were run against the file: zero new type errors, zero new lint errors attributable to this change (the file's pre-existing `@next/next/no-img-element` ×5 and `react-hooks/set-state-in-effect` ×1 warnings predate this phase — confirmed by diffing against `7f970e4^`, the commit before phase start — and are unrelated to the fix).

**WR-02 — verified fixed, correct and complete.** `web/src/components/ui/popover.tsx:22` now reads `bg-popover` instead of `bg-white` for the light-mode class, bringing `PopoverContent` in line with `card.tsx`/`dialog.tsx`/`alert-dialog.tsx`/`sheet.tsx`, all of which resolve elevation through the `--popover`/`--card` CSS variables. The redundant `dark:bg-popover` (IN-01, out of scope for this fix pass) was correctly left untouched per the fix report's stated rationale. Diff is a single-line change (`+1/-1`). `npx eslint` on the file: no issues found.

**No new issues were introduced by either fix.** Both diffs are minimal and surgical (11 total lines changed across both commits), touch only the specific className/JSX identified in the original findings, and were confirmed not to affect any other call site (`grep`-level check: `bg-white` is now used nowhere else in `popover.tsx`; no other logout button in `dashboard-shell.tsx` was regressed). A fresh full-file re-read of all 14 files in scope, plus `tsc --noEmit` and `eslint` runs across every file in the list, surfaced nothing beyond what's listed below.

The three Info-level findings from the original review were explicitly out of `fix_scope: critical_warning` and were correctly left untouched. They are re-confirmed present below at their current (unchanged) locations for completeness, since this is a full re-review rather than a diff-only pass.

## Info

### IN-01: Redundant `dark:bg-card` / `dark:bg-popover` variants (unchanged, not in fix scope)

**File:** `web/src/components/ui/card.tsx:10`, `web/src/components/ui/dialog.tsx:41`, `web/src/components/ui/alert-dialog.tsx:39`, `web/src/components/ui/sheet.tsx:55`
**Issue:** `--card` and `--popover` are defined for both `:root` and `.dark` in `globals.css`, so the bare `bg-card`/`bg-popover` utility already resolves correctly per theme. Each of these four files still repeats the identical class under `dark:` (e.g. `bg-card ... dark:bg-card`), which has no visual effect and reads as an intentional dark-specific override that doesn't exist. Confirmed still present, byte-for-byte identical to the previous review.
**Fix:** Drop the redundant `dark:bg-card` / `dark:bg-popover` segments, e.g. in `card.tsx`: `"rounded-lg border border-slate-200 bg-card text-slate-950 shadow-sm transition-all hover:shadow-md dark:border-slate-800 dark:text-slate-50"`.

### IN-02: Dead cache-invalidation code in `onLogout` (pre-existing, unchanged)

**File:** `web/src/components/shared/dashboard-shell.tsx:71-79`
**Issue:** `await import("@tanstack/react-query")` dynamically imports the module and discards the result — it doesn't call `queryClient.clear()` or anything else, despite the comment implying cache invalidation happens here. Harmless in practice because the following `window.location.href` triggers a full page reload, but the dead import and misleading comment should be removed. Confirmed still present at the same lines; untouched by the WR-01 fix (which only edited the mobile drawer's JSX further down in the same file).
**Fix:**
```tsx
const onLogout = async () => {
  await clearTokens();
  window.location.href = "/login";
};
```

### IN-03: Tooltip on `disabled` trigger won't show (Radix limitation, unchanged)

**File:** `web/src/app/(dashboard)/clientes/page.tsx:640-656`
**Issue:** The "Eliminar" button is wrapped in `<TooltipTrigger asChild>` and also carries `disabled={del.isPending}`. Native `disabled` buttons don't fire the pointer/focus events Radix's `Tooltip` relies on to open, so while `del.isPending` is `true` the tooltip silently won't appear. Impact is minor (transient, in-flight-mutation only) and this file/finding is outside the scope of the WR-01/WR-02 fix pass. Confirmed still present, unchanged.
**Fix:** No action required for this transient case; if the same pattern (Tooltip wrapping a conditionally-disabled Button) recurs with a longer-lived disabled state, wrap the disabled element in a non-interactive `<span>` as the `TooltipTrigger`'s child instead, per Radix's documented workaround.

---

_Reviewed: 2026-07-16T02:20:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
