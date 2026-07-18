---
phase: LEXCV-110-refinamento-da-landing-webpage
reviewed: 2026-07-18T00:00:00Z
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
  warning: 1
  info: 5
  total: 6
status: issues_found
---

# Phase LEXCV-110: Code Review Report

**Reviewed:** 2026-07-18T00:00:00Z
**Depth:** standard
**Files Reviewed:** 5
**Status:** issues_found

## Summary

Reviewed the mobile Sheet nav drawer added to `SiteHeader` (LDG-17) and the Hero/Contacto recomposition onto a Card+Badge pattern (LDG-18) in `webpage/` (the standalone marketing app — no auth/tenant/data concerns apply). Verified against `tsc --noEmit` (clean) and `eslint` (clean) for all 5 files, and cross-checked `Card`/`Button`/`cn`/`BrandMark` (the components these files call) for defects they might introduce.

Both known anti-patterns called out for this phase were correctly avoided:
- `site-header.tsx` closes the drawer via a direct `onClick={() => setOpen(false)}` on every nav link/CTA (lines 59, 66) — it does **not** use the `usePathname`/`useEffect` pattern that was flagged as an anti-pattern in an earlier phase.
- `hero-section.tsx`/`contact-section.tsx` use `Card`/`CardHeader`/`CardContent` as pure layout wrappers and keep the real `<h1>`/`<h2>` as raw tags — neither uses `CardTitle` (which would render an `<h3>` and demote the page heading).

No Critical/Blocker issues were found — no injection, XSS, hardcoded-secret, or crash-risk patterns in any of the 5 files. One genuine unhandled-edge-case Warning was found (Sheet does not close itself when the viewport is resized past the `md` breakpoint while open), plus several minor Info-level items (dead/redundant Tailwind classes, a missing `SheetDescription`, and one documented-spec-vs-code padding drift in the new `Badge` usage).

## Warnings

### WR-01: Mobile Sheet drawer does not close when the viewport crosses the `md` breakpoint while open

**File:** `webpage/src/components/site-header.tsx:19-52`
**Issue:** `open`/`setOpen` state is only ever changed by the trigger click, `onOpenChange`, or an item's `onClick`. There is no listener for viewport-width changes. If a user opens the drawer below `md` (768px) and then the viewport grows past `md` without the drawer being explicitly closed (window resize, DevTools responsive-mode drag, tablet rotation, split-screen/foldable resize), `open` remains `true`. At that point the desktop `<nav>` (`hidden md:flex`) and the desktop "Entrar" button (`hidden md:inline-flex`) both become visible again *while the Sheet's overlay + panel + its own "Entrar" CTA are still rendered on top of them* (`SheetContent`'s `open` state is independent of the `md:` CSS breakpoints gating the trigger/desktop nav). The result is a genuinely broken UI state: duplicated nav links and duplicated "Entrar" CTAs visible simultaneously, with the drawer's overlay still capturing pointer events over the desktop layout.
**Fix:** Add a `matchMedia` listener (not a `pathname` effect — this is a distinct, legitimate use of `useEffect` and does not reintroduce the previously-fixed anti-pattern) that force-closes the sheet once the viewport reaches the desktop breakpoint:
```tsx
React.useEffect(() => {
  const mq = window.matchMedia("(min-width: 768px)");
  const handleChange = (e: MediaQueryListEvent) => {
    if (e.matches) setOpen(false);
  };
  mq.addEventListener("change", handleChange);
  return () => mq.removeEventListener("change", handleChange);
}, []);
```

## Info

### IN-01: Redundant `dark:bg-popover` class (dead code)

**File:** `webpage/src/components/ui/sheet.tsx:55`
**Issue:** `className` includes both `bg-popover` and `dark:bg-popover`. `bg-popover` maps to a CSS variable (`--popover`) that already resolves to a different value under `.dark`, so the `dark:` variant re-declares the exact same utility with no behavioral effect — it's inert, copy-paste leftover.
**Fix:** Drop the redundant `dark:bg-popover` (or, if the file is meant to stay byte-identical to the `web/` source it was verbatim-copied from, note it as accepted debt rather than something to "fix" independently in `webpage/`).

### IN-02: `sm:max-w-sm` is a no-op given the fixed `w-72` width

**File:** `webpage/src/components/site-header.tsx:52`
**Issue:** `className="w-72 sm:max-w-sm"` sets an unconditional `width: 18rem` (288px) via `w-72`, and `max-w-sm` caps width at `24rem` (384px) from the `sm` breakpoint up. Since 288px < 384px, the `max-width` constraint can never bind — the class combination is dead weight. (This mirrors the shadcn default `w-3/4 sm:max-w-sm`, where `w-3/4` can exceed 384px on larger phones, making the cap meaningful there; that reasoning does not carry over to a fixed `w-72`.)
**Fix:** Either drop `sm:max-w-sm` or replace `w-72` with a percentage-based width if a genuine mobile/tablet cap is intended.

### IN-03: Mobile nav Sheet has no `SheetDescription` / `aria-describedby`

**File:** `webpage/src/components/site-header.tsx:52-53`
**Issue:** `SheetContent` renders a visually-hidden `SheetTitle` but no `SheetDescription`, so Radix's `aria-describedby` on the dialog content points at an id with no corresponding element in the DOM. Functionally harmless in the installed Radix version (verified in `@radix-ui/react-dialog@1.1.x`'s compiled output — the dev-mode "missing Description" warning path is now a no-op `WarningProvider`), but it is a minor screen-reader UX gap: users get no supplementary context beyond the sr-only "Menu" title.
**Fix:** Add a visually-hidden description, e.g. `<SheetDescription className="sr-only">Links de navegação do site</SheetDescription>` as the second child of `SheetContent`, or explicitly set `aria-describedby={undefined}` on `SheetContent` to document the omission as intentional.

### IN-04: New `Badge` eyebrow padding doesn't match this phase's own approved Spacing Scale

**File:** `webpage/src/components/hero-section.tsx:16`, `webpage/src/components/contact-section.tsx:12` (base class in `webpage/src/components/ui/badge.tsx:7`)
**Issue:** `110-UI-SPEC.md`'s Spacing Scale table documents `px-3 py-1` for "Badge/eyebrow padding" (matching the original hand-rolled `<span>` this replaced), and explicitly calls out overriding `Badge`'s default type scale (`text-xs font-medium` → `text-sm font-semibold`) at both call sites. Both call sites do override font-size/weight, but neither overrides padding — `Badge`'s un-overridden base class (`px-2.5 py-0.5`, i.e. 10px/2px) is what actually ships, not the documented `px-3 py-1` (12px/4px). Small, but a real, provable drift between the approved design contract and the shipped markup.
**Fix:** If pixel parity with the spec matters, add `px-3 py-1` to the `className` override on both `Badge` instances; otherwise update `110-UI-SPEC.md` to reflect the accepted default padding.

### IN-05: Reused `Card`'s `hover:shadow-md` now applies to a large, non-interactive content block

**File:** `webpage/src/components/ui/card.tsx:10`, used by `webpage/src/components/hero-section.tsx:14` and `webpage/src/components/contact-section.tsx:10`
**Issue:** `Card`'s shared classes include `transition-all hover:shadow-md`, intended for `TrustSection`'s small grid-item cards. In Hero/Contacto, `Card` now wraps the entire eyebrow+heading+CTA block (a large fraction of the viewport), so hovering anywhere over that block triggers a shadow-elevation change even though the block itself isn't clickable — a hover affordance implying interactivity where none exists. This was an accepted trade-off per `110-UI-SPEC.md` (deliberately replicating `TrustSection`'s exact surface treatment), so it is not a defect to fix under this phase's scope, but worth flagging for future polish.
**Fix (optional, out of phase scope):** If this reads as misleading in practice, override with `className="hover:shadow-none"` (or a `Card`-variant prop) on the Hero/Contacto instances only.

---

_Reviewed: 2026-07-18T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
