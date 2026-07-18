---
phase: LEXCV-110-refinamento-da-landing-webpage
reviewed: 2026-07-18T01:00:00Z
depth: standard
files_reviewed: 5
files_reviewed_list:
  - webpage/src/components/ui/sheet.tsx
  - webpage/src/components/site-header.tsx
  - webpage/src/components/ui/badge.tsx
  - webpage/src/components/hero-section.tsx
  - webpage/src/components/contact-section.tsx
findings:
  critical: 0
  warning: 0
  info: 7
  total: 7
status: issues_found
---

# Phase LEXCV-110: Code Review Report

**Reviewed:** 2026-07-18T01:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Iteration 2. Re-reviewed the full 5-file set fresh (not a rubber-stamp of the prior report) and specifically verified the WR-01 fix landed in commit `27b45fb` (`webpage/src/components/site-header.tsx`, +9/-0).

**WR-01 verification result: fix is correct and resolves the reported defect.** The new `useEffect` registers a `window.matchMedia("(min-width: 768px)")` `"change"` listener that calls `setOpen(false)` whenever `e.matches` becomes `true`, with a proper cleanup (`removeEventListener`) on unmount and an empty dependency array (registers once, no stale-closure risk — `setOpen` is a stable `useState` setter). Traced through the scenario from the original finding: mobile drawer open (`open === true`) below the `md` breakpoint → viewport crosses 768px upward → `matchMedia` fires with `matches: true` → `setOpen(false)` → `Sheet`'s `onOpenChange` prop is wired to `setOpen`, so the Radix `Dialog` closes → duplicated desktop nav/CTA + stray overlay no longer coexists with the visible desktop layout. The fix does not resize-close in the other direction (`matches: false` is ignored), which is correct — the drawer should only ever be force-closed, never force-opened. Confirmed `tsc --noEmit` and `eslint` both pass clean on all 5 files with this change in place, and confirmed via `git diff` that the change touches only lines 21-28 (the new effect) with no other edits.

While tracing the fix I found one new, narrower edge case it introduces (IN-07 below) and one pre-existing-style quality nit in the same new code (IN-06). Neither rises to Warning: both are minor, low-probability-of-observation issues that don't reproduce the duplicated-UI bug WR-01 described, and neither causes a crash, data loss, or broken core functionality.

The other 4 files (`sheet.tsx`, `badge.tsx`, `hero-section.tsx`, `contact-section.tsx`) are unchanged since the iteration-1 review (confirmed via `git log` — no commits touch them since `1643350`/`d0b464a`/`bd9e015`), so the 5 previously-reported Info items were re-verified against current file contents and remain valid as-is (renumbered IN-01 through IN-05 below for a self-contained artifact). No Critical or Warning issues were found in this pass — no injection, XSS, hardcoded-secret, or crash-risk patterns in any of the 5 files, and no unresolved logic errors.

## Info

### IN-01: Redundant `dark:bg-popover` class (dead code)

**File:** `webpage/src/components/ui/sheet.tsx:55`
**Issue:** `className` includes both `bg-popover` and `dark:bg-popover`. `bg-popover` maps to a CSS variable (`--popover`) that already resolves to a different value under `.dark`, so the `dark:` variant re-declares the exact same utility with no behavioral effect — inert, copy-paste leftover.
**Fix:** Drop the redundant `dark:bg-popover` (or, if the file is meant to stay byte-identical to the `web/` source it was verbatim-copied from, note it as accepted debt).

### IN-02: `sm:max-w-sm` is a no-op given the fixed `w-72` width

**File:** `webpage/src/components/site-header.tsx:61`
**Issue:** `className="w-72 sm:max-w-sm"` sets an unconditional `width: 18rem` (288px) via `w-72`, and `max-w-sm` caps width at `24rem` (384px) from the `sm` breakpoint up. Since 288px < 384px, the `max-width` constraint can never bind — dead weight.
**Fix:** Either drop `sm:max-w-sm` or replace `w-72` with a percentage-based width if a genuine mobile/tablet cap is intended.

### IN-03: Mobile nav Sheet has no `SheetDescription` / `aria-describedby`

**File:** `webpage/src/components/site-header.tsx:61-62`
**Issue:** `SheetContent` renders a visually-hidden `SheetTitle` but no `SheetDescription`, so Radix's `aria-describedby` on the dialog content points at an id with no corresponding element in the DOM. Functionally harmless in the installed Radix version, but a minor screen-reader UX gap.
**Fix:** Add `<SheetDescription className="sr-only">Links de navegação do site</SheetDescription>` as a child of `SheetContent`, or explicitly set `aria-describedby={undefined}` to document the omission as intentional.

### IN-04: `Badge` eyebrow padding doesn't match this phase's own approved Spacing Scale

**File:** `webpage/src/components/hero-section.tsx:16`, `webpage/src/components/contact-section.tsx:12` (base class in `webpage/src/components/ui/badge.tsx:7`)
**Issue:** `110-UI-SPEC.md`'s Spacing Scale documents `px-3 py-1` for "Badge/eyebrow padding". Both call sites override font-size/weight (`text-sm font-semibold`) but not padding, so `Badge`'s un-overridden base class (`px-2.5 py-0.5`) is what actually ships, not the documented `px-3 py-1`.
**Fix:** Add `px-3 py-1` to the `className` override on both `Badge` instances, or update `110-UI-SPEC.md` to reflect the accepted default padding.

### IN-05: Reused `Card`'s `hover:shadow-md` now applies to a large, non-interactive content block

**File:** `webpage/src/components/hero-section.tsx:14`, `webpage/src/components/contact-section.tsx:10` (shared class in `webpage/src/components/ui/card.tsx`, not in this iteration's file set)
**Issue:** `Card`'s shared classes include `transition-all hover:shadow-md`. In Hero/Contacto, `Card` now wraps the entire eyebrow+heading+CTA block, so hovering anywhere over that large region triggers a shadow-elevation change even though the block itself isn't clickable — implying interactivity where none exists. Accepted trade-off per `110-UI-SPEC.md`, not a defect to fix under this phase's scope.
**Fix (optional):** Override with `className="hover:shadow-none"` on the Hero/Contacto `Card` instances only, if this reads as misleading in practice.

### IN-06: New matchMedia breakpoint (`768px`) hardcodes a magic number that duplicates the Tailwind `md` token

**File:** `webpage/src/components/site-header.tsx:22`
**Issue:** The WR-01 fix's `window.matchMedia("(min-width: 768px)")` re-encodes Tailwind's `md` breakpoint (currently 768px by default, per `webpage/src/app/globals.css` — no custom breakpoint overrides found) as a separate literal, independent from the `md:` utility classes used throughout the same file (`hidden md:flex`, `hidden md:inline-flex`, `md:hidden`). If the design system's breakpoint scale is ever customized (a `@theme` breakpoint override, or Tailwind config change), this JS literal would silently drift out of sync with the CSS breakpoint it's meant to track, reintroducing the exact duplicated-nav bug WR-01 just fixed — but only in JS, invisible to anyone auditing the Tailwind classes.
**Fix:** Extract the value to a named constant colocated with a comment tying it to Tailwind's `md` breakpoint, e.g.:
```tsx
// Keep in sync with Tailwind's `md:` breakpoint (globals.css / tailwind theme).
const MD_BREAKPOINT_QUERY = "(min-width: 768px)";
...
const mq = window.matchMedia(MD_BREAKPOINT_QUERY);
```
or centralize in a shared `src/lib/breakpoints.ts` if other components grow the same need.

### IN-07: Auto-close-on-resize can leave keyboard focus stranded because the trigger becomes hidden at the exact same breakpoint

**File:** `webpage/src/components/site-header.tsx:21-28` (new effect) interacting with `:53-60` (trigger button, `className="md:hidden ..."`)
**Issue:** Radix `Dialog.Content`'s default `onCloseAutoFocus` behavior returns focus to the element that had focus when the dialog opened (normally the `SheetTrigger` button) once the dialog closes, regardless of whether the close was user-initiated or programmatic (`setOpen(false)`). The new matchMedia handler only calls `setOpen(false)` at exactly the same viewport-width crossing (`>= 768px`) where the trigger button itself gains the `md:hidden` (`display:none`) class. A `.focus()` call on a `display:none` element is a browser-spec no-op, so for a keyboard-only user who has the drawer open and then crosses the breakpoint (browser resize, zoom, DevTools responsive-mode drag, orientation change on a foldable), focus silently falls through to `document.body` instead of landing on a sensible, visible element — the user's keyboard focus position is lost and they must re-tab from the top of the page. This is a new reachable state directly created by the WR-01 fix (previously the bug meant the drawer just stayed open with focus intact inside it); it does not crash or corrupt anything, and only affects keyboard/screen-reader users during a live breakpoint crossing while the drawer is open, so it is Info- rather than Warning-level.
**Fix:** Use Radix's `onCloseAutoFocus` escape hatch on `SheetContent` to suppress the default focus-return when the trigger is about to be hidden, and let focus land somewhere intentional instead:
```tsx
<SheetContent
  side="right"
  className="w-72 sm:max-w-sm"
  onCloseAutoFocus={(event) => {
    if (window.matchMedia("(min-width: 768px)").matches) {
      event.preventDefault();
      // trigger is display:none at this breakpoint; focus a stable landmark instead
      headerRef.current?.focus();
    }
  }}
>
```

---

_Reviewed: 2026-07-18T01:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
