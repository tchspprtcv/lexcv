# Phase 109 — UI Review

**Audited:** 2026-07-17
**Baseline:** 109-UI-SPEC.md (approved 2026-07-17)
**Screenshots:** partially captured — see note below

Dev server was running at `http://localhost:3000` (HTTP 200). The public `/login` route was
screenshotted successfully (desktop, 1440x900) as evidence the build serves correctly. The 3
touched surfaces are all gated: `/setup` returns HTTP 404 in this environment (the wizard is
already initialized and self-blocks re-entry per its own "impede reinicializações" contract —
correct backend behavior, but it means the Progress-bar UI cannot be visually re-verified this
session), and the authenticated dashboard shell (topbar `UserMenu`, sidebar `UserMenu`, mobile
Sheet `UserMenu`, `NotificationBell` badge) sits behind an external IGRP SSO redirect (the
`/login` page is a single "Autenticar" button to a third-party identity provider, not a
scriptable email/password form), which is out of reach for a one-shot CLI screenshot in this
session. **This audit is therefore evidence-based on source code + the existing 109-REVIEW.md /
109-REVIEW-FIX.md code-review trail, not live-rendered screenshots of the 3 changed surfaces.**

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Locked copy reproduced exactly, but "Perfil" and "Configurações" now resolve to the identical `/settings` destination (`/profile` is a bare redirect) — two menu items promising different destinations, delivering the same one. |
| 2. Visuals | 2/4 | `UserMenu`'s 3 bespoke `<button>` triggers have zero `aria-label` and drop the app's standard `focus-visible` ring (unlike the project's own DropdownMenu precedent, `data-table-view-options.tsx`, which wraps `Button` and inherits both); `NotificationBell`'s Bell trigger also has no `aria-label`. |
| 3. Color | 3/4 | Dark-mode Badge fix is genuinely correct and necessary (verified via `cn()`/`tailwind-merge` group-override logic) — but the pre-existing `bg-slate-400` + white text error-state color (~1.9:1 contrast) still fails WCAG AA and ships unchanged. |
| 4. Typography | 4/4 | Zero new sizes/weights introduced; all reused values match the declared Body/Label roles and the project's pre-existing, spec-documented sub-12px/`font-bold` exceptions. |
| 5. Spacing | 4/4 | 1:1 verified against the spec's exact declared values (`mr-2`=8px=sm token, `w-56` fixed content width, unchanged Badge/Progress positioning) — zero new spacing values. |
| 6. Experience Design | 2/4 | The newly-extracted `sidebar-nav.tsx`'s "Suporte" link has no `onClick={onNavigate}` (unlike its 2 sibling links in the same component) — tapping it on mobile leaves the Sheet drawer open; this is the exact drawer-doesn't-close bug class this phase's own code-review pass (WR-01/WR-03) fixed everywhere else in the file. |

**Overall: 18/24**

---

## Top 3 Priority Fixes

1. **`UserMenu` triggers have no accessible name and no visible focus indicator** (`web/src/components/shared/user-menu.tsx:41`, `:55`) — keyboard users tabbing through the topbar/sidebar/mobile-Sheet get no visual focus ring on the account menu (all 3 instances), and screen-reader users hear either nothing or bare initials, never "menu de utilizador." Fix: add `aria-label="Menu do utilizador"` to both `<button>` variants, and add `outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300` (the same treatment `buttonVariants` gives every other interactive control in the app) — or better, wrap the trigger content in the shared `Button` component with `asChild`, matching the project's own DropdownMenu precedent (`data-table-view-options.tsx`), which gets both properties for free.

2. **"Suporte" link doesn't close the mobile drawer** (`web/src/components/shared/sidebar-nav.tsx:73-79`) — it's the only link in `SidebarNav` missing `onClick={onNavigate}` (the primary nav loop at line 42 and the `/settings` link at line 62 both have it). Since it's also a dead `href="#"`, tapping it on mobile does nothing visible and leaves the Sheet stuck open, with no indication anything happened. Fix: add `onClick={onNavigate}` at minimum; ideally wire the link to a real destination or `disabled`-style it since it's a known non-functional placeholder (already tracked as IN-04 in `109-REVIEW.md`, but that review scored it Info/deferred — from a UI-interaction-flow standpoint this is a live mobile-drawer bug, not just dead code).

3. **`NotificationBell`'s Bell trigger has no `aria-label`** (`web/src/components/shared/notification-bell.tsx:82-98`) — the header's other icon-only button (hamburger menu, `dashboard-shell.tsx:116`, `aria-label="Abrir menu"`) correctly follows the project's icon-only-button convention; the Bell button, sitting one element away in the same header and directly touched by this phase's Badge migration, does not. Fix: add `aria-label="Notificações"` to the `<Button>` at line 82.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

- **Compliant:** All locked copy reproduced verbatim — `"Perfil"` (`user-menu.tsx:74`), `"Configurações"` (`:79`), `"Terminar sessão"` (`:85`), Badge's `"9+"`/raw count/`"!"` (`notification-bell.tsx:95`, unchanged), Progress section's `"Progresso"`/percentage label and the 3 legend lines reused verbatim (`setup/page.tsx:268-276`, matching the UI-SPEC's exact recommended block).
- **Finding (new, not in UI-SPEC or code review):** `"Perfil"` (`user-menu.tsx:74`, `<Link href="/profile">`) and `"Configurações"` (`:77-80`, `<Link href="/settings">`) are two separate, adjacent menu items, but `/profile` is a bare `redirect("/settings")` (`web/src/app/(dashboard)/profile/page.tsx:1-5`) — both items currently land on the identical page. This was an explicit, approved UI-SPEC decision ("reusing it as-is is correct; do not invent a new route"), so it is not an execution defect, but it is a real copy-vs-behavior mismatch a user will notice the first time they click both items and land on the same screen twice.
- No generic `Submit`/`Click Here`/`OK` patterns introduced by this phase's 3 files.

### Pillar 2: Visuals (2/4)

- **Finding — no `aria-label`, anywhere, in `user-menu.tsx`.** Grepped the file for `aria-label`: zero matches. Both trigger variants (`topbar`, `sidebar`) are plain `<button type="button">` elements with no accessible-name attribute. The `topbar` variant additionally hides its only text content (`hidden sm:block`, line 42) below the `sm` breakpoint, meaning on mobile the trigger's accessible name falls back to whatever's inside the avatar `<div>` — either an `<img alt="Avatar">` or bare initials text (e.g. "JD") — never anything that communicates "this opens a menu."
- **Finding — no focus-visible ring on any of the 3 `UserMenu` triggers.** The shared `Button` component (`web/src/components/ui/button.tsx:8`) bakes in `outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300` for every button in the app. `user-menu.tsx`'s bespoke `<button>` elements (lines 41, 55) don't reuse `Button` and carry none of that treatment — keyboard-focusing the account menu shows only the browser's native default outline (inconsistent styling vs. every other control) or, depending on browser/OS defaults, a much weaker indicator. This is directly comparable to the project's own prior (and only) `DropdownMenu` consumer, `data-table-view-options.tsx:40-49`, which wraps its trigger in `<Button variant="outline" size="sm" aria-label="Colunas visíveis">` — getting both the `aria-label` and the focus ring for free. `UserMenu`, built as the second consumer of the same primitive, diverges from its own cited analog on both counts.
- **Finding — `NotificationBell`'s Bell trigger has no `aria-label`** (`notification-bell.tsx:82-98`). Pre-existing (not introduced by NTF-29's Badge swap), but directly adjacent to the touched markup, and inconsistent with the header's other icon-only button (`dashboard-shell.tsx:112-119`, `aria-label="Abrir menu"`), which does it correctly.
- **Compliant:** No layout shift — Badge keeps its exact `absolute -top-0.5 -right-0.5 h-4 w-4` position (`notification-bell.tsx:91`); `UserMenu` triggers preserve their exact prior avatar/name markup pixel-for-pixel; Progress card keeps its exact prior container. Visual hierarchy of the 3 surfaces is otherwise unchanged from pre-phase state — no new focal-point problems introduced.

### Pillar 3: Color (3/4)

- **Verified correct — dark-mode Badge fix (the item this audit was specifically asked to check).** `notification-bell.tsx:92`: `unread.isError ? "bg-slate-400 dark:bg-slate-400" : "bg-red-500 dark:bg-red-500"`. This is genuinely necessary, not cosmetic: `<Badge>` is invoked with no `variant` prop, so it defaults to `variant: "secondary"` (`badge.tsx:26`), which carries `dark:bg-neutral-800` (`badge.tsx:14`). `cn()` is `twMerge(clsx(...))` (`lib/utils.ts`), and `tailwind-merge` deduplicates conflicting classes *within the same modifier+property group* — `dark:bg-neutral-800` and `dark:bg-red-500` are both `dark:` + background-color, so without an explicit `dark:bg-red-500`/`dark:bg-slate-400` in the override string, there would be nothing in that group for `twMerge` to dedupe against, and `dark:bg-neutral-800` would survive in the final class list, silently winning in dark mode regardless of what the light-mode-only `bg-red-500`/`bg-slate-400` classes say. Adding the explicit `dark:` counterparts forces `twMerge` to keep only the last-specified class in that group (the intended red/slate), which is exactly what was applied. Confirmed correct.
- **Carried-over finding (pre-existing, unchanged by this phase):** the error-state badge color, `bg-slate-400` with `text-white` (`notification-bell.tsx:91-92`), is roughly a 1.9:1 contrast ratio — well under WCAG AA's 4.5:1 (or even the 3:1 large-text threshold). This ships unchanged from before the phase and isn't part of NTF-29's locked scope, but it's now sitting right next to a review comment ("code-review-fixed dark-mode color bug") that could give false confidence the badge's color story is fully resolved — it's only the *dark-mode parity* bug that's fixed, not the underlying low-contrast error state.
- **Compliant:** `Progress`'s `bg-primary` fill (`progress.tsx:24`) is an approved, documented extension of the existing Accent(10%) reservation list, not a new accent surface. No new hardcoded hex/rgb values introduced in any of the 3 touched files (`#020617`/`#04091a` in `dashboard-shell.tsx` are pre-existing, reused verbatim).

### Pillar 4: Typography (4/4)

- Grepped `user-menu.tsx`, `notification-bell.tsx`, `setup/page.tsx` for size/weight classes: `text-sm`, `text-xs`, `text-[10px]`, `text-[11px]` (sizes) and `font-semibold`, `font-medium`, `font-bold` (weights) — all reused verbatim from the pre-phase markup, matching the UI-SPEC's declared Body(14px/400)/Label(12px/600) roles plus the project's long-standing, spec-documented sub-12px micro-text and avatar-initials `font-bold` exceptions (`109-UI-SPEC.md` Typography section, "Pre-existing, out-of-scope note").
- Zero new type roles or weight values introduced by NTF-28/29/30. `DropdownMenuItem` renders at its own shipped `text-sm` (unweighted), matching Body exactly, per contract.

### Pillar 5: Spacing (4/4)

- `DropdownMenuItem` icon-to-label gap: `mr-2` (`user-menu.tsx:78`, `:84`) = 8px = declared `sm` token exactly, per spec.
- `DropdownMenuContent className="w-56"` (`:72`) matches the spec's explicit fixed-width override (224px) exactly.
- Badge positioning/size unchanged: `absolute -top-0.5 -right-0.5 h-4 w-4` (`notification-bell.tsx:91`) is byte-for-byte what the spec locked.
- Progress card container spacing (`space-y-3 border border-slate-200 bg-slate-50 p-5`, `setup/page.tsx:267`) is the exact pre-existing Checklist container, unchanged, per spec's explicit allowance.
- No new arbitrary spacing values (`[...px]`/`[...rem]`) introduced in any of the 3 files beyond what already existed pre-phase.

### Pillar 6: Experience Design (2/4)

- **Finding — "Suporte" link breaks the mobile-drawer-close contract this phase itself introduced elsewhere.** `sidebar-nav.tsx:73-79`: `<Link href="#" className="...">` has no `onClick` at all, while its sibling links two blocks up (`:39-54` nav loop, `:60-72` Configurações) both correctly wire `onClick={onNavigate}`. Combined with `href="#"` doing nothing, a mobile user tapping "Suporte" gets a Sheet drawer that silently stays open — the exact defect class (drawer-doesn't-close) that this phase's code-review pass (WR-01, per `109-REVIEW.md`/`109-REVIEW-FIX.md`) explicitly fixed for every other link in this same component. This is tracked as `IN-04` in `109-REVIEW.md` but classified there as an Info-level "non-functional placeholder link" finding (code-quality lens); from a UI/interaction lens this is a live, user-facing drawer-state bug, which is why it's called out here as a Warning-weight finding rather than cosmetic debt.
- **Compliant — Progress phase derivation.** `wizardPhase`/`wizardProgress` (`setup/page.tsx:49-51`) correctly derives from existing `form.formState.isSubmitting`/`successMessage` with no new state, exactly as locked. Verified the failed-submission fallback path: `onSubmit` clears both `serverError`/`successMessage` at the top, and a thrown error only sets `serverError` — `successMessage` stays `null`, `isSubmitting` returns to `false`, so `wizardPhase` correctly falls back to `"idle"` (33%) after a failed attempt, matching the spec's documented reasoning exactly.
- **Compliant — no double-submit window.** The submit button's `disabled` (`setup/page.tsx:304`) is `form.formState.isSubmitting || wizardPhase !== "idle"`, closing the post-success redirect window (this was WR-02 in the code-review fix pass, already verified applied).
- **Not a defect (locked, intentional):** "Terminar sessão" has no confirmation dialog — explicitly locked by both `109-CONTEXT.md` and `109-UI-SPEC.md` as an unconfirmed, easily-reversible action; correctly implemented as `variant="default"`, not `"destructive"` (`user-menu.tsx:83`).
- **Out of this phase's scope, noted for completeness only:** `dashboard-shell.tsx`'s `useMe()` + `hasPermission()` pattern still has no loading guard (deferred explicitly in `109-CONTEXT.md`); the header search input remains presentation-only (`IN-05`, pre-existing, untouched by NTF-28/29/30).

---

## Registry Safety

Not applicable — `components.json` exists (shadcn initialized), but `109-UI-SPEC.md`'s Registry Safety table lists only `shadcn official` blocks (`DropdownMenu`, `Badge`, `Progress`, all already installed since Phase 101, zero new `add` commands this phase). No third-party registries are declared, so the registry-flag audit gate does not apply. Registry audit: 0 third-party blocks checked, no flags possible.

---

## Files Audited

- `web/src/components/shared/user-menu.tsx` (new file, this phase)
- `web/src/components/shared/dashboard-shell.tsx` (3 `UserMenu` call sites consolidated)
- `web/src/components/shared/sidebar-nav.tsx` (new file, extracted this phase's code-review pass)
- `web/src/components/shared/notification-bell.tsx` (Badge migration, lines 88-98)
- `web/src/app/setup/page.tsx` (Progress migration, lines 267-277)
- `web/src/components/ui/dropdown-menu.tsx`, `web/src/components/ui/badge.tsx`, `web/src/components/ui/progress.tsx`, `web/src/components/ui/button.tsx` (primitives, read for contract/consistency verification)
- `web/src/components/shared/data-table/data-table-view-options.tsx` (cited analog — prior `DropdownMenu` consumer, used to benchmark `UserMenu`'s accessibility/consistency gaps)
- `web/src/app/(dashboard)/profile/page.tsx` (verified `/profile` redirect target for the Copywriting finding)
- `.planning/phases/LEXCV-109-notifica-es-settings-setup-wizard/109-CONTEXT.md`, `109-UI-SPEC.md`, `109-REVIEW.md`, `109-REVIEW-FIX.md` (baseline + prior review trail)
- `.planning/ui-reviews/109-20260717-215934/login-desktop.png` (only screenshot captured this session — public `/login` route; the 3 changed surfaces are gated behind SSO auth / already-initialized-setup-block, see note at top)
