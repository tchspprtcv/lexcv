# Phase 110 — UI Review

**Audited:** 2026-07-18
**Baseline:** 110-UI-SPEC.md (approved 2026-07-17, design contract for Tabs/Select-equivalent LDG-17 Sheet mobile nav + LDG-18 Hero/Contacto Card/Badge recomposition)
**Screenshots:** not captured (no dev server running on 3001/3000/5173/8080 at audit time — code-only audit). The orchestrator ran a live in-browser human-verify checkpoint earlier this session (mobile 390×844 / desktop 1280×800, light + dark) which is folded into this audit's evidence base per the task brief, but no fresh screenshots were captured by this agent.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All copy matches the UI-SPEC contract exactly (Entrar, Pedir Demonstração, Abrir menu, NAV_LINKS labels); zero generic-label or empty/error-copy violations found. |
| 2. Visuals | 3/4 | Icon-only controls are all properly labelled, but the reused `Card`'s `hover:shadow-md` now fires over the entire non-interactive Hero/Contacto block, implying false clickability. |
| 3. Color | 3/4 | Accent (blue) correctly confined to the 3 declared elements; but Hero/Contacto's `Card` now exposes the pre-existing zero-contrast dark-mode surface (Card bg == page bg) on prominent above-the-fold content for the first time. |
| 4. Typography | 4/4 | Exactly 4 sizes / exactly 2 weights in rendered output, matching the spec table precisely; heading semantics (raw `<h1>`/`<h2>`, no `CardTitle`) correctly preserved. |
| 5. Spacing | 2/4 | Provable spec-vs-code drift: Badge eyebrow ships at `px-2.5 py-0.5`, not the UI-SPEC's declared `px-3 py-1`, creating a visible padding mismatch against TrustSection's/FeaturesSection's still-manual eyebrow spans on the same scroll. |
| 6. Experience Design | 3/4 | Mobile nav interaction is fully functional and human-verified, but the resize-auto-close fix (WR-01) introduces an unaddressed keyboard-focus-stranding edge case, and the Sheet ships without a `SheetDescription`. |

**Overall: 19/24**

---

## Top 3 Priority Fixes

1. **Badge eyebrow padding drift (`px-2.5 py-0.5` vs. spec's `px-3 py-1`)** — `webpage/src/components/hero-section.tsx:16`, `webpage/src/components/contact-section.tsx:12` — User impact: scrolling from Hero down to TrustSection/FeaturesSection (both still on the old manual `px-3 py-1` span), the eyebrow pill visibly shrinks and re-grows — a small but real, side-by-side-visible inconsistency in a component pattern meant to read as identical across all 4 landing sections. Fix: add `px-3 py-1` to both `Badge` `className` overrides (`className="px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em]"`).

2. **`Card`'s `hover:shadow-md` applies false-affordance elevation to the entire Hero/Contacto block** — `webpage/src/components/ui/card.tsx:10` (shared class), consumed by `webpage/src/components/hero-section.tsx:14` and `webpage/src/components/contact-section.tsx:10` — User impact: hovering anywhere over the Hero or Contacto section (previously free-floating content, now a bordered `Card`) triggers a shadow-elevation transition as if the whole block were a single clickable target, when only the 1-2 buttons inside actually are. This is a new, phase-introduced affordance signal that didn't exist before (TrustSection/FeaturesSection's grid-item `Card`s are small, single-purpose icon+title+description tiles where hover-elevation reads more naturally; Hero/Contacto's is a large, multi-element block). Fix: override with `className="hover:shadow-none"` on the Hero/Contacto `Card` instances specifically.

3. **Keyboard focus can be stranded when the Sheet auto-closes on a live breakpoint crossing** — `webpage/src/components/site-header.tsx:21-28` (new `matchMedia` resize-close effect) interacting with `:53-59` (hamburger trigger, `md:hidden`) — User impact: a keyboard-only or screen-reader user with the mobile drawer open who then resizes/rotates past 768px triggers `setOpen(false)`; Radix's default `onCloseAutoFocus` tries to return focus to the trigger button, which is simultaneously `display:none` at that exact width, so focus silently falls to `document.body` and the user must re-tab from the top of the page. Fix: add an `onCloseAutoFocus` handler on `SheetContent` that calls `event.preventDefault()` and redirects focus to a stable, always-visible landmark (e.g. a `headerRef`/`BrandMark` wrapper) when `window.matchMedia("(min-width: 768px)").matches`, per the code-review's own suggested patch (110-REVIEW.md IN-07).

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

- Verified every copy string against the UI-SPEC Copywriting Contract table by direct file read:
  - `webpage/src/components/site-header.tsx:55` — `aria-label="Abrir menu"` — exact match.
  - `webpage/src/components/site-header.tsx:62` — `<SheetTitle className="sr-only">Menu</SheetTitle>` — matches the spec's explicit "Menu" (or "Navegação") allowance.
  - `webpage/src/components/site-header.tsx:12-16` — `NAV_LINKS` (`Funcionalidades`/`#funcionalidades`, `Confiança`/`#confianca`, `Contacto`/`#contacto`) — identical strings to the pre-existing hardcoded anchors, correctly extracted into a single shared array (no duplicate list).
  - `webpage/src/components/hero-section.tsx:29,32` — `Entrar` / `Ver Funcionalidades` — unchanged, matches spec.
  - `webpage/src/components/contact-section.tsx:23` — `Pedir Demonstração` — unchanged, matches spec.
  - `webpage/src/components/ui/sheet.tsx:67` — sr-only `Fechar` on the Sheet's close button — correctly localized to Portuguese (consistent with the project's domain-language convention per `CLAUDE.md`), not left as the English "Close" a naive verbatim copy might have shipped.
- Grepped `webpage/src` for generic-label anti-patterns (`Submit`, `Click Here`, `OK`, `Cancel`, `>Save<`) and empty/error-copy patterns (`No data`, `went wrong`, `try again`) — zero matches. Correctly so: UI-SPEC marks empty/error states `n/a` for this static-marketing phase, and no such states were introduced.
- No deviation found. This is the one pillar where the implementation is genuinely exceeds-contract clean — score reflects that, not an averaged-up default.

### Pillar 2: Visuals (3/4)

- Focal point preserved: Hero `<h1>` + CTA pair remains the primary anchor; Badge eyebrow and blue accent rule remain secondary, per UI-SPEC's Visual Hierarchy section. Confirmed by reading `hero-section.tsx` in full — the accent `<div className="mb-4 h-px w-12 bg-blue-600...">` and `BrandMark` correctly stay outside/above the new `Card`, exactly as the spec's Card composition boundary requires.
- Icon-only buttons are all properly labelled: hamburger (`aria-label="Abrir menu"`, `site-header.tsx:55`), Sheet close (`sr-only` `Fechar` span, `sheet.tsx:67`), `ThemeToggle` (`sr-only` `Alternar tema` span, `theme-toggle.tsx:21`). No unlabelled icon-only control found anywhere in the touched or adjacent files.
- **Finding (WARNING):** `Card`'s shared `hover:shadow-md` (`card.tsx:10`) now wraps a large, mostly non-interactive region in both Hero and Contacto (previously this class only applied to TrustSection's/FeaturesSection's small grid-item tiles, where hover-elevation is a more conventional "hoverable card" signal). Hovering over empty space inside the Hero/Contacto `Card` — not over either button — still triggers the shadow transition, implying the whole block is clickable when it isn't. This is a new signal-vs-noise regression introduced specifically by this phase's Card-wrapping of Hero/Contacto (a real UX affordance concern, independent of whether the UI-SPEC pre-blessed it as an "accepted trade-off" — the adversarial audit standard requires surfacing it regardless of prior sign-off).
- No other visual-hierarchy defects found (font-size/weight differentiation between eyebrow/body/heading is correct and consistent, see Pillar 4).

### Pillar 3: Color (3/4)

- Accent usage confirmed confined to the 3 declared elements: `hero-section.tsx:13` (blue accent rule), `site-header.tsx:40,69` (nav link hover states, both desktop and drawer), `features-section.tsx:27` (feature icon color, out of this phase's scope, unchanged). Zero new blue-accented elements introduced on `Badge` (`variant="secondary"`, confirmed neutral `bg-neutral-100`/`dark:bg-neutral-800` tones at `badge.tsx:13`) or `Button` (`bg-neutral-900`/`bg-neutral-100`, `button.tsx:12-13`) — matches UI-SPEC's explicit prohibition on new blue-accented elements.
- `grep -rn "text-primary\|bg-primary\|border-primary"` across `webpage/src` → 0 matches, consistent with the app-wide convention (documented in UI-SPEC Divergence Notes) of using hardcoded `blue-600`/`blue-400` Tailwind utility classes rather than the `--primary` token directly in TSX.
- Hardcoded hex confined to `card.tsx:10` (`dark:bg-[#020617]`) and `site-header.tsx:31` (`dark:bg-[#020617]/80`) — both pre-existing patterns explicitly carved out of this phase's scope per UI-SPEC Divergence Note §3 (do not reconcile `card.tsx`). Not a new violation.
- **Finding (WARNING):** In dark mode, `Card`'s background hex (`#020617`) is byte-identical to `--background`'s dark value (`#020617`, confirmed in `globals.css`), so elevation for the Hero/Contacto `Card`s comes entirely from `border-slate-800` + `shadow-sm`, with zero background-fill contrast against the page. This was already true for TrustSection/FeaturesSection before this phase, but Phase 110 is the first time this weak-contrast Card treatment is applied to the page's most prominent above-the-fold content (Hero) rather than only a below-the-fold supporting grid. In dark mode this makes the Hero's Card boundary read as a thin outline rather than a filled surface — a real secondary-surface legibility weakness now given first-screen prominence, not merely a repeated known issue.
- No other color violations found; 60/30/10 distribution is broadly respected (dominant background, card/white secondary surface, blue accent kept minimal).

### Pillar 4: Typography (4/4)

- Rendered font sizes across all touched files, independently grepped: `text-sm` (eyebrow override + nav links, 14px), `text-base` (body paragraphs, 16px), `text-2xl` (Contacto `<h2>`, 24px), `text-5xl` (Hero `<h1>`, 48px) — exactly the 4 sizes the UI-SPEC's Typography table declares, no more, no fewer. (`text-xs`/`text-lg` appear only in the un-overridden `Badge`/`SheetTitle` base classes — both are either overridden by `className` at every call site, or rendered `sr-only` and thus invisible — correctly excluded from the phase's typography budget per the spec's own carve-out.)
- Rendered font weights: `font-semibold` (600) used consistently for headings, eyebrow override, and nav links; `font-medium` (500) appears only in inherited `Button`/`Badge` component internals (`button.tsx:8`, `badge.tsx:7`), never as a phase-introduced content decision — matches the spec's explicit "exactly 2 weights" carve-out for primitive internals.
- Heading semantics correctly preserved: independently confirmed via direct file read that `hero-section.tsx:21` and `contact-section.tsx:17` both keep raw `<h1>`/`<h2>` tags with their original classes; `CardTitle` is imported nowhere in either file (also confirmed via grep, 0 matches) — no heading-demotion regression.
- Badge className override (`text-sm font-semibold uppercase tracking-[0.2em]`) correctly reskins the component's smaller 12px/500 default back to the pre-existing 14px/600 eyebrow look at both call sites (`hero-section.tsx:16`, `contact-section.tsx:12`) — the one part of the Badge migration the spec called "not left to discretion," and it was executed correctly for size/weight (only padding was missed — see Pillar 5).
- No deviation found in this pillar; genuinely a clean pass, not an averaged score.

### Pillar 5: Spacing (2/4)

- **Finding (BLOCKER-adjacent, scored as the pillar's driving defect):** `webpage/src/components/hero-section.tsx:16` and `webpage/src/components/contact-section.tsx:12` override `Badge`'s className with `text-sm font-semibold uppercase tracking-[0.2em]` only — padding is never touched. `Badge`'s base cva string (`webpage/src/components/ui/badge.tsx:7`) bakes in `px-2.5 py-0.5` (10px/2px), which therefore ships unchanged at both call sites. This directly contradicts the UI-SPEC's own Spacing Scale table, which explicitly declares: `sm | 8px | px-3 py-1 Badge/eyebrow padding, tight inline spacing`. The previous manual `<span>` eyebrow this replaced was hardcoded to exactly `px-3 py-1` (12px/4px) — so this is a measurable size *regression*, not just a documentation gap: the new Badge pill is visibly smaller/tighter than the eyebrow it replaced, and than the still-untouched `TrustSection`("CONFIANÇA INSTITUCIONAL")/`FeaturesSection`("MÓDULOS") eyebrows sitting a few hundred pixels below it on the same page, using the exact same visual pattern with different actual padding.
- **Finding (minor, corroborating):** `webpage/src/components/site-header.tsx:61` — `className="w-72 sm:max-w-sm"` — `sm:max-w-sm` (24rem/384px cap) can never bind because `w-72` unconditionally sets 288px, which is already below the cap. Confirmed via arithmetic: 288px < 384px at every viewport ≥ the `sm` breakpoint. This is dead, no-op spacing declaration — not a visual defect on its own, but combined with the Badge padding miss above, it indicates this phase's spacing values were not rigorously checked against their actual rendered effect before shipping.
- Remaining spacing classes across all 3 touched files (`px-6`, `mt-6`, `mt-8`, `gap-4`, `gap-6`, `mt-4`, `mt-10`, `px-3 py-2` on drawer links, `gap-1` on the drawer nav column) are all valid multiples of 4 and match the UI-SPEC's declared scale tokens (`md`=16px, `lg`=24px, `xl`=32px). No arbitrary bracket-value spacing (`[Npx]`/`[Nrem]`) found in any touched file.
- Because the Badge padding drift is a directly observable, side-by-side visual inconsistency (not merely a paperwork mismatch against an internal doc) and is compounded by a demonstrably dead spacing class in the same plan's deliverable, this pillar is scored 2/4 — "notable gaps, contract partially met" — rather than 3/4.

### Pillar 6: Experience Design (3/4)

- Core interaction fully implemented and functioning: `Sheet` open/close state (`site-header.tsx:19`), every drawer item (3 `NAV_LINKS` anchors + Entrar CTA) calls `onClick={() => setOpen(false)}` directly (`:68`, `:75`) — correctly avoiding the `usePathname`/`useEffect(pathname)` anti-pattern class from Phase 109's WR-01 lesson (`usePathname` confirmed absent via grep). This was also human-verified live this session (390×844 mobile, all 4 drawer items closing on click, no duplicate "Entrar").
- The code-review-fix loop (110-REVIEW-FIX.md, commit `27b45fb`) caught and fixed a real bug post-hoc: without the added `matchMedia("(min-width: 768px)")` resize-close effect (`site-header.tsx:21-28`), a user who opens the drawer on mobile and then resizes/rotates past the `md` breakpoint would see the desktop nav render underneath a still-open Sheet overlay — a genuine duplicate-UI defect. Good defensive engineering, correctly scoped (only force-closes, never force-opens).
- **Finding (WARNING, unaddressed as of this audit):** That same fix introduces a new edge case (110-REVIEW.md IN-07, independently confirmed in this audit by reading `site-header.tsx:21-28` alongside the trigger at `:53-59`): Radix `Dialog`'s default `onCloseAutoFocus` tries to return focus to the previously-focused `SheetTrigger` button when the drawer force-closes, but at the exact viewport width that triggers the auto-close, that same trigger has just gained `md:hidden` (`display:none`). Focusing a `display:none` element is a browser no-op, so a keyboard/screen-reader user resizing/rotating past the breakpoint with the drawer open loses focus to `document.body` and must re-tab from the top. This is a real, reachable accessibility regression for a non-trivial (if narrow) user path, and remains unfixed in the shipped code.
- **Finding (INFO, minor completeness gap):** `SheetContent` renders a visually-hidden `SheetTitle` (`:62`) but no `SheetDescription`, leaving Radix's `aria-describedby` pointed at a non-existent element (110-REVIEW.md IN-03, confirmed absent via direct read of `site-header.tsx`). Functionally harmless in the currently-installed Radix version but a minor screen-reader completeness gap that a more careful a11y pass would have closed alongside the `SheetTitle` addition.
- No loading/error/empty-state gaps found — correctly out of scope for a static marketing phase with no data-fetching introduced.
- No destructive actions in scope, so no confirmation-dialog gap applies.
- Given the core flow is genuinely solid and human-verified, but two concrete a11y edge-defects (one newly introduced, one pre-existing gap) remain unaddressed, this pillar is scored 3/4 rather than 4/4.

---

## Fixes Applied Post-Audit

- **Priority Fix #1 (Badge eyebrow padding)** — **FIXED** in commit `31d8ac4`. Added `px-3 py-1` to both `Badge` `className` overrides (`hero-section.tsx:16`, `contact-section.tsx:12`). Verified via computed `getComputedStyle` in-browser: both eyebrows now render `paddingLeft/Right: 12px`, `paddingTop/Bottom: 4px`, matching the UI-SPEC's Spacing Scale exactly. `pnpm build` re-verified clean.
- **Priority Fix #2 (Card `hover:shadow-md` false affordance)** — **Deferred, not fixed.** `card.tsx` is explicitly out of scope for Phase 110 per `110-UI-SPEC.md`'s Divergence Notes and `110-CONTEXT.md`'s locked decision to replicate `TrustSection`'s Card treatment verbatim, hover included. Overriding it now would contradict that explicit decision rather than fix a phase-introduced defect.
- **Priority Fix #3 (keyboard focus stranding on resize-close)** — **Deferred, not fixed.** Already logged as `110-REVIEW.md` IN-07 (Info-level, out of the code-review `--fix` scope which covers Critical+Warning only). Narrow edge case (keyboard/screen-reader user resizing past `md` while the drawer is open); left as documented debt.
- 5 minor recommendations (dark-mode Card zero-contrast, dead `sm:max-w-sm`, redundant `dark:bg-popover`, missing `SheetDescription`, `matchMedia` magic number) — deferred, all pre-existing-pattern or Info-level per `110-REVIEW.md`, consistent with this project's convention of not fixing every Info-level finding within a phase's own scope.

---

## Registry Safety

`webpage/components.json` exists (shadcn initialized, `radix-vega` preset). Per `110-UI-SPEC.md`'s own Registry Safety table, no third-party registries are declared for this phase — both `Sheet` and `Badge` were introduced via a verbatim in-repo file copy from `web/src/components/ui/{sheet,badge}.tsx` (already vetted through Phase 101's package-legitimacy gate), not via `npx shadcn add --registry` or any external fetch. No `shadcn view`/`diff` audit applies.

Registry audit: 0 third-party blocks checked, no flags (in-repo verbatim copy only, no CLI registry fetch this phase).

---

## Files Audited

- `webpage/src/components/site-header.tsx`
- `webpage/src/components/hero-section.tsx`
- `webpage/src/components/contact-section.tsx`
- `webpage/src/components/trust-section.tsx` (analog reference, unmodified)
- `webpage/src/components/features-section.tsx` (accent-usage cross-check, unmodified)
- `webpage/src/components/ui/sheet.tsx` (new this phase)
- `webpage/src/components/ui/badge.tsx` (new this phase)
- `webpage/src/components/ui/card.tsx` (unmodified, out of scope per UI-SPEC — read for context)
- `webpage/src/components/ui/button.tsx` (unmodified — read for context)
- `webpage/src/components/theme-toggle.tsx` (unmodified — read for context)
- `webpage/src/app/globals.css` (spot-checked for token/breakpoint definitions)
- `webpage/components.json` (registry safety check)
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-UI-SPEC.md`
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-CONTEXT.md`
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-PATTERNS.md`
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-01-PLAN.md` / `110-01-SUMMARY.md`
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-02-PLAN.md` / `110-02-SUMMARY.md`
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-03-PLAN.md` / `110-03-SUMMARY.md`
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-REVIEW.md`
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-REVIEW-FIX.md`
- `.planning/phases/LEXCV-110-refinamento-da-landing-webpage/110-VERIFICATION.md`
