---
phase: LEXCV-114-linguagem-visual-cantos-arredondados-radius
reviewed: 2026-07-21T23:15:26Z
depth: standard
files_reviewed: 32
files_reviewed_list:
  - web/src/app/globals.css
  - webpage/src/app/globals.css
  - webpage/src/components/ui/card.tsx
  - web/src/app/(dashboard)/agenda/page.tsx
  - web/src/app/(dashboard)/clientes/[id]/page.tsx
  - web/src/app/(dashboard)/clientes/columns.tsx
  - web/src/app/(dashboard)/clientes/novo/page.tsx
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/dashboard/page.tsx
  - web/src/app/(dashboard)/documentos/columns.tsx
  - web/src/app/(dashboard)/documentos/page.tsx
  - web/src/app/(dashboard)/financeiro/columns.tsx
  - web/src/app/(dashboard)/financeiro/page.tsx
  - web/src/app/(dashboard)/notificacoes/page.tsx
  - web/src/app/(dashboard)/pareceres/[id]/page.tsx
  - web/src/app/(dashboard)/pareceres/columns.tsx
  - web/src/app/(dashboard)/pareceres/nova/page.tsx
  - web/src/app/(dashboard)/pareceres/page.tsx
  - web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx
  - web/src/app/(dashboard)/processos/[id]/editar/page.tsx
  - web/src/app/(dashboard)/processos/[id]/page.tsx
  - web/src/app/(dashboard)/processos/[id]/termo-honorarios/page.tsx
  - web/src/app/(dashboard)/processos/columns.tsx
  - web/src/app/(dashboard)/processos/dashboard/page.tsx
  - web/src/app/(dashboard)/processos/novo/page.tsx
  - web/src/app/(dashboard)/processos/page.tsx
  - web/src/app/(dashboard)/settings/page.tsx
  - web/src/app/setup/page.tsx
  - webpage/src/components/contact-section.tsx
  - webpage/src/components/hero-section.tsx
  - webpage/src/components/site-footer.tsx
  - webpage/src/components/site-header.tsx
findings:
  critical: 0
  warning: 2
  info: 1
  total: 3
status: issues_found
---

# Phase LEXCV-114: Code Review Report

**Reviewed:** 2026-07-21T23:15:26Z
**Depth:** standard
**Files Reviewed:** 32
**Status:** issues_found

## Summary

This phase applies a single mechanical transformation across 32 files: flip `--radius` from `0rem` to `0.5rem` in both `globals.css` files, and strip literal `rounded-none` Tailwind overrides that were masking the token everywhere except a documented 6-line/4-exception allowlist.

**Mechanical-consistency verification (all passed):**
- `grep -rn "rounded-none" web/src webpage/src` returns exactly the expected 6 lines, all in the 3 allowed exception files (`calendar.tsx` ×3, `input-group.tsx` ×2, `tabs.tsx` ×1). No file outside the allowlist retains a bare `rounded-none`, and none of the 32 reviewed files show a missed/incomplete strip.
- `web/src/components/ui/checkbox.tsx` (`rounded-[4px]`) and the two `web/src/app/(dashboard)/processos/[id]/page.tsx` mobile bottom-sheet `DialogContent` blocks (`max-sm:rounded-t-xl max-sm:rounded-b-none`, count = 2; `rounded-none shadow-2xl` count = 0) are all confirmed intact and correctly scoped — the illegitimate bare `rounded-none` prefix was removed from both dialogs, the legitimate breakpoint-scoped exception was not touched.
- `webpage/src/components/ui/card.tsx` was correctly changed from a primitive-level `rounded-none` to `rounded-lg` (replaced, not merely deleted), matching `web/`'s already-correct `Card`. No consumer (`hero-section.tsx`, `contact-section.tsx`, `features-section.tsx`, `trust-section.tsx`) overrides this with its own radius class, so none of them regress to sharp corners.
- `web/src/app/(dashboard)/settings/page.tsx`'s 4 Cards were special-cased from `rounded-none lg:rounded-xl` to plain `rounded-xl` (not a blind delete) — verified this is a deliberate, documented decision (114-01-PLAN.md/114-UI-SPEC.md), not an accidental behavior change; the Card primitive's own `cn(baseClasses, className)` ordering means `rounded-xl` correctly wins over the primitive default at every breakpoint.
- No malformed className strings found anywhere in the diff: no double spaces, no dangling `word:` fragments, no broken JSX. Ran `tsc --noEmit` on both `web/` and `webpage/` and `eslint` on every changed `.tsx` file — zero new errors attributable to this diff (the only findings were 3 pre-existing `vitest` type-resolution errors and a handful of pre-existing `react-hooks/set-state-in-effect` / unused-variable warnings, all on lines this diff never touched — out of scope, confirmed by direct line comparison against `178f742`).

Text-level execution of the sweep is correct and faithful to its own specification. The issues below are about the **visual outcome** of that mechanically-correct sweep, which the phase's own summary (`114-01-SUMMARY.md`) states was never actually verified ("Visual QA ... was not performed here ... explicitly deferred to a downstream verifier/HUMAN-UAT step") — i.e., these are exactly the class of regression that text-based verification (grep/tsc/lint) cannot catch and a screenshot-based pass has not yet caught either.

## Warnings

### WR-01: Removing `rounded-none` turns every Badge from rectangular into a full pill — a much larger, unverified visual change the phase's own QA plan says not to expect

**File:** `web/src/components/ui/badge.tsx:7,36`
**Issue:**
`badgeVariants` (line 7) has `rounded-full` baked into its base class list, and `Badge` renders `cn(badgeVariants({ variant }), className)` (line 36) — the call site's `className` is merged in **after** the base classes. `cn()` is `twMerge(clsx(inputs))` (`web/src/lib/utils.ts:5`), and `rounded-full`/`rounded-none` are the same tailwind-merge conflict group, so whichever appears later wins. I confirmed this empirically against the project's own installed `tailwind-merge`:

```
twMerge("... rounded-full ...", "... rounded-none font-bold tracking-wide")
// => "... rounded-none font-bold tracking-wide"   (rounded-full is dropped)
```

This means every one of the 36 `<Badge ... className="rounded-none ...">` instances removed by this sweep (e.g. `clientes/columns.tsx:153,158`, `dashboard/page.tsx:258` + 3 more KPI badges, `processos/[id]/page.tsx` legal-hold/conflict/workflow/prazo badges, `processos/columns.tsx`, `pareceres/columns.tsx`, `documentos/columns.tsx`, `financeiro/columns.tsx`, `processos/novo/page.tsx`, `processos/dashboard/page.tsx`, `agenda/page.tsx`'s "BREVEMENTE" badge, etc.) was rendering **rectangular/sharp**, not pill-shaped, before this phase — and will now render as a **full pill** everywhere, across effectively every list/table/detail screen in the app. That's the single largest-blast-radius visual change in this entire diff, yet `114-UI-SPEC.md`'s component tier table and QA checklist both explicitly say "Badges remain pill-shaped ... unaffected, do not expect a visual change here" — which is empirically false. (Separately, `ROADMAP.md`'s Phase 114 Success Criterion 2 does list "badges" among the elements that should show "cantos arredondados," so the pill outcome plausibly is the intended direction — but that only sharpens the point: the phase's own verification documentation will actively mislead whoever performs the still-pending visual QA pass into not scrutinizing exactly this change.)
**Fix:** Before closing this phase, explicitly verify (screenshot or live render) that full-pill badges are the desired outcome across all 7 badge variants (`default/secondary/outline/blue/green/amber/red/purple/gray`) and both themes, then correct `114-UI-SPEC.md`'s QA checklist line from "unaffected, do not expect a visual change" to something like:
```diff
- [ ] Badges remain pill-shaped (unaffected, do not expect a visual change here)
+ [ ] Badges are now full pill-shaped everywhere (changed from rectangular — verify this is the intended look across all variants/themes)
```
If the pill shape turns out to be undesired for specific badges, the targeted fix is a per-call-site class such as `rounded-md` (not re-adding `rounded-none`, which would reintroduce the sharp-corner regression this phase exists to remove).

### WR-02: Icon-wrapper squares, avatar-initials squares, and raw toggle-pill `<button>`s have no primitive to "fall back to" — they stay sharp-cornered even after the sweep, contradicting Success Criterion 3

**File:** `web/src/app/(dashboard)/dashboard/page.tsx:140,148,156,255,275,295,315`, `web/src/app/(dashboard)/clientes/columns.tsx:140`, `web/src/app/(dashboard)/clientes/page.tsx:461`, `web/src/app/(dashboard)/processos/page.tsx:164,176`, `web/src/app/(dashboard)/processos/dashboard/page.tsx:71,87`, `web/src/app/(dashboard)/notificacoes/page.tsx:173-181`, `web/src/app/(dashboard)/processos/[id]/page.tsx:1290-1383` (5 pill buttons)
**Issue:** The sweep's stated mechanism (114-UI-SPEC.md "Critical Scope Finding") is "remove the literal `rounded-none` override so the element falls back to the primitive's own already-correct, already-token-derived default." That's true for elements routed through a styled primitive (`Button`, `Input`, `Card`, `Badge`, `Textarea`, `SelectTrigger`, `Dialog`) whose own base class list is merged via `cn()`. It is **not** true for two categories of elements this sweep also touched:
1. Plain `<div className="h-10 w-10 rounded-none bg-blue-50 ...">` icon-wrapper / avatar-initials squares (13 occurrences across `dashboard/page.tsx`, `clientes/columns.tsx`, `clientes/page.tsx`, `processos/page.tsx`, `processos/dashboard/page.tsx` — file:line list above) — no wrapping component, no other radius class in the string.
2. Raw native `<button>` toggle-pills in `notificacoes/page.tsx` (the "read/unread" filter chips, 1 source line rendering N chips) and `processos/[id]/page.tsx` (5 hardcoded timeline-type filter chips: movimentação/transição/evento/documento/decisão) — also plain `<button>`, not the shadcn `Button` component, so no `cn(base, className)` merge and no default radius class either.

A `<div>`/`<button>`'s native default border-radius is already `0`, identical to what `rounded-none` was explicitly setting — so deleting the token is a complete no-op both before and after the flip for all ~19 of these elements. Several appear prominently on the two screens listed *first* in the UI-SPEC's own "representative screens" table (`/dashboard` KPI cards and activity icons, `/clientes` list-row avatar initials) and on screen #4 (`/processos/[id]`, the "heaviest single screen" per that same table). They will still show hard 0px corners, directly contradicting `ROADMAP.md` Phase 114 Success Criterion 3: "Todos os ecrãs ... sem exceções visuais nem cantos retos remanescentes" (no screen should show a remaining sharp corner outside the 4 named exceptions — none of these ~19 elements are among them).
**Fix:** Add an explicit token-derived radius class to each of these elements, e.g.:
```diff
- <div className="h-10 w-10 bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center border border-blue-100 dark:border-blue-500/20">
+ <div className="h-10 w-10 rounded-lg bg-blue-50 dark:bg-blue-500/10 flex items-center justify-center border border-blue-100 dark:border-blue-500/20">
```
```diff
- className={active ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 h-8 px-3 text-xs" : "..."}
+ className={active ? "bg-slate-900 text-white dark:bg-slate-100 dark:text-slate-900 rounded-md h-8 px-3 text-xs" : "... rounded-md ..."}
```
(analogously for the other icon-wrapper occurrences, including the 3 `iconWrapperClassName` string constants at `dashboard/page.tsx:140,148,156` consumed by `<div className={entry.iconWrapperClassName}>` at line 196, and the other 4 timeline-filter buttons in `processos/[id]/page.tsx`). If any of these were deliberately meant to stay square, document them as additional named legitimate exceptions in `114-UI-SPEC.md` so a future audit doesn't re-flag them as an incomplete sweep.

## Info

### IN-01: Sweep leaves 61 now-empty `className=""` / `triggerClassName=""` attributes behind

**File:**
- `web/src/app/(dashboard)/clientes/[id]/page.tsx:699,788,796,804,919,977,986,996,1307,1355,1365,1543` (12) + `triggerClassName=""` at `:1336`
- `web/src/app/(dashboard)/processos/[id]/page.tsx:1058,1254,1555,1568,1579,1589,1602,1609,1662,1677,1692,1699,1779,1829,1858,1865,1949,1966,2011,2018,2102,2119,2155,2169,2187,2194,2400,2445,2452,2573,2623,2633` (32) + `triggerClassName=""` at `:2604`
- `web/src/app/(dashboard)/clientes/page.tsx:265,269,277` (3)
- `web/src/app/(dashboard)/dashboard/page.tsx:433,582` (2)
- `web/src/app/(dashboard)/pareceres/nova/page.tsx:112,214` (2)
- `web/src/app/(dashboard)/processos/novo/page.tsx:225,462,776` (3)
- `web/src/app/(dashboard)/processos/[id]/editar/page.tsx:232` (1)
- `webpage/src/components/hero-section.tsx:28,31` (2)
- `webpage/src/components/site-footer.tsx:12` (1)
- `webpage/src/components/contact-section.tsx:22` (1)

Total: 59 `className=""` + 2 `triggerClassName=""` = 61, across 10 files.
**Issue:** Whenever `rounded-none` was the *only* class in a string, the 3-step removal regex correctly collapses it to `className=""` (or `triggerClassName=""`) rather than leaving a dangling attribute — this is syntactically valid and confirmed harmless (`tsc`/`eslint` clean; none of `Input`, `Button`, `Card`, `Badge`, `NativeSelect`, or `combobox.tsx`'s `triggerClassName` prop use a JS default-parameter value for `className` that an explicit empty string would defeat — all route through `cn(base, className)` where `""` and `undefined` behave identically). Purely cosmetic leftover, not a functional defect.
**Fix:** Optional follow-up cleanup — drop the now-empty attribute entirely for readability, e.g.:
```diff
- <Card className="">
+ <Card>
```
Not worth a dedicated pass on its own; fold into the next time any of these 10 files is touched.

---

_Reviewed: 2026-07-21T23:15:26Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
