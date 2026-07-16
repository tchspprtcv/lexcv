# Phase 102 — UI Review

**Post-audit fix applied (2026-07-16):** Priority Fix #1 (`MoreVertical` navigate icon-buttons in `processos/page.tsx:381` and `pareceres/page.tsx:469` had zero accessible name) was fixed immediately after this audit — both wrapped in `Tooltip`/`TooltipTrigger asChild`/`TooltipContent` with `aria-label="Ver detalhes"`, matching the pattern already used for the `Eye` icon in `clientes/page.tsx`. Verified live in browser (hover shows "Ver detalhes" tooltip) and via `pnpm build` (clean). Commit `ddd06f8`. Priority Fixes #2 (redundant `dark:bg-card`/`dark:bg-popover` classes) and #3 (Tooltip-on-disabled-trigger limitation, `window.confirm()` vs `AlertDialog` for delete) remain open — both are low-risk/cosmetic or represent a larger scope change than this phase's reconciliation goal covers; left for a future pass.

**Audited:** 2026-07-16
**Baseline:** 102-UI-SPEC.md (approved design contract)
**Screenshots:** captured (login page only, light + dark, via CLI `playwright screenshot` against the running dev server at `localhost:3003`); authenticated interior routes (`/clientes`, `/settings`) were **not** independently screenshotted this audit (no automated login flow available in the CLI-only screenshot path) — corroborated instead by the mandatory human visual checkpoint recorded verbatim in `102-04-SUMMARY.md` (live browser, authenticated, `getComputedStyle`/LAB readings, accessibility-tree `aria-label` reads), which this audit treats as supporting evidence, not a substitute for the independent code-level verification performed below.

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | All 5 tooltip copies match the UI-SPEC contract verbatim across every in-scope call site; zero generic labels introduced. |
| 2. Visuals | 3/4 | DSR-03 scope fully wrapped, but `MoreVertical` navigate icon-buttons in `processos`/`pareceres` ship with zero accessible name (no aria-label/tooltip/text). |
| 3. Color | 3/4 | Reconciliation itself is exact (zero Rule-C drift, correct Rule-B elevation) but this phase's own diffs left redundant no-op `dark:bg-card`/`dark:bg-popover` classes in 4 files. |
| 4. Typography | 4/4 | Not exercised this phase, as contracted; confirmed only 3 sizes / 2 weights across the 13 reconciled files, no regression. |
| 5. Spacing | 4/4 | Not exercised this phase, as contracted; only one pre-existing arbitrary value (`min-h-[60px]` in `textarea.tsx`, Rule-C, untouched). |
| 6. Experience Design | 3/4 | Tooltip provider + build gate solid, but disabled-trigger tooltip gap (IN-03) and native `window.confirm()` destructive-action pattern (not the reconciled `AlertDialog`) remain. |

**Overall: 21/24**

---

## Top 3 Priority Fixes

1. **Icon-only `MoreVertical` row-navigate buttons have no accessible name** (`web/src/app/(dashboard)/processos/page.tsx:381`, `web/src/app/(dashboard)/pareceres/page.tsx:469`) — a screen-reader user tabbing through the processos/pareceres list table reaches a button Radix/the DOM cannot name (no `aria-label`, no visible text, no `Tooltip`). This phase's own broadened DSR-03 grep discovered these two call sites and explicitly classified them "out of scope" because they navigate rather than perform an "explicit action" — a defensible reading of the requirement text, but the shipped result is still an unlabeled interactive control. Concrete fix: wrap both in the same `Tooltip`/`aria-label="Ver detalhes"` pattern already used for every other `Eye`/row-view affordance in the app (2-line change per file, zero risk, consistent with the pattern this phase just established everywhere else).

2. **Redundant no-op `dark:bg-card`/`dark:bg-popover` classes left in 4 reconciled files** (`card.tsx:10`, `dialog.tsx:41`, `alert-dialog.tsx:39`, `sheet.tsx:55`) — `--card`/`--popover` already resolve per-theme via `:root`/`.dark` in `globals.css`, so the explicit `dark:` variant is dead weight that reads as an intentional per-theme override that doesn't exist. This was flagged as IN-01 in **both** `102-REVIEW.md` and its re-review, and left unfixed both times (correctly deferred as Info-level, but it is a real, easily-fixable piece of imprecision introduced by this phase's own reconciliation work, not inherited debt). Fix: drop the redundant `dark:` segment in all 4 files, e.g. `card.tsx` → `"rounded-lg border border-slate-200 bg-card text-slate-950 shadow-sm transition-all hover:shadow-md dark:border-slate-800 dark:text-slate-50"`.

3. **Tooltip silently disappears on a disabled trigger, and the destructive-delete flow bypasses the newly-reconciled `AlertDialog`** (`web/src/app/(dashboard)/clientes/page.tsx:640-656`) — the "Eliminar" button is wrapped in `TooltipTrigger asChild` while also carrying `disabled={del.isPending}`; native `disabled` buttons don't fire the pointer/focus events Radix's Tooltip needs, so the tooltip silently won't show during an in-flight delete (documented as IN-03, low-impact/transient). Separately, `onDelete` (line 549) uses browser-native `window.confirm("Remover este cliente?")` rather than the app's own reconciled `AlertDialog` component — an inconsistency now that `AlertDialog` ships with a correct, tokenized, on-brand confirmation surface. Fix: for the transient tooltip case, no action required per Radix's own documented workaround unless the disabled window lengthens; for the confirmation pattern, migrate destructive-delete call sites to `AlertDialog` in a follow-up phase for visual/interaction consistency with the design system this phase just reconciled.

---

## Detailed Findings

### Pillar 1: Copywriting (4/4)

This phase's only copy surface is the Tooltip rollout (DSR-03) — no new CTA/empty/error/destructive-confirmation copy ships this phase, matching the UI-SPEC's Copywriting Contract table (all "N/A").

- Verified every declared tooltip copy against the actual rendered strings:
  - `"Ver detalhes"` — `clientes/page.tsx:465` (mobile), `:611` (desktop) ✓
  - `"Editar"` — `clientes/page.tsx:476` (mobile), `:636` (desktop); `settings/page.tsx:470` ✓
  - `"Eliminar"` — `clientes/page.tsx:654`; `settings/page.tsx:484` ✓
  - `"Imprimir"` — `clientes/page.tsx:625` ✓
  - `"Terminar sessão"` — `dashboard-shell.tsx:161` (desktop aside), `:246` (mobile drawer) ✓
- Every `TooltipContent` string is matched by an identical `aria-label` on the same trigger element — spot-checked all 9 call sites, zero mismatches.
- `sr-only` "Fechar" close-button label preserved verbatim in both `dialog.tsx:49` and `sheet.tsx:67` (byte-identical Portuguese text, untouched by the surface-token reconciliation).
- No generic English labels (`Submit`, `Click Here`, `OK`) found anywhere in `web/src/app`/`web/src/components` — app-wide grep returned zero matches, consistent with the existing Portuguese-first copy convention.
- IN-02's misleading code comment (`dashboard-shell.tsx:74`, "Invalidamos a cache" above a dead `await import(...)` that invalidates nothing) is source-code hygiene, not user-facing copy — not scored here.

No issues found against the contracted scope. Score: 4/4.

### Pillar 2: Visuals (3/4)

- Focal point / hierarchy: unaffected by this phase (no layout changes); login card, dashboard sidebar/table hierarchy confirmed unchanged via direct screenshot (login, light+dark) and code read.
- Icon-only + aria-label/tooltip pairing — **fully compliant for every DSR-03-declared surface**: verified 9/9 wrapped call sites in `clientes/page.tsx`, `settings/page.tsx`, `dashboard-shell.tsx` (desktop + mobile-drawer logout).
- **Gap found by independent grep** (not just trusting the SUMMARY's own coverage table): `web/src/app/(dashboard)/processos/page.tsx:381` and `web/src/app/(dashboard)/pareceres/page.tsx:469` render a bare `<Button asChild size="sm" variant="ghost" ...><Link ...><MoreVertical className="h-4 w-4" /></Link></Button>` — no `aria-label`, no visible text, no `Tooltip`. This is a real icon-only-button-without-accessible-name instance in the shipped app. The phase's own 102-03-SUMMARY.md enumerates this exact pair as "out of scope — navigate-to-detail affordance, not an explicit-action icon," which is a legitimate scope-narrowing decision for DSR-03's literal text, but from a pure Visuals-pillar standpoint (icon-only buttons should be paired with an accessible name) it is a live gap, not a false positive.
- `notificacoes/page.tsx:312`'s `Check` button correctly retains a pre-existing `aria-label`+`title` pair (out of phase scope, already compliant, verified).
- `agenda/page.tsx:198,219` `ChevronLeft`/`ChevronRight` calendar-nav buttons remain unlabeled — same class of gap as the `MoreVertical` case, also out of DSR-03's declared scope but a real accessibility debt visible in the current build.

Score reflects a phase that fully executed its contracted scope but left a handful of icon-only-button accessible-name gaps elsewhere in the same pages it touched. Score: 3/4.

### Pillar 3: Color (3/4)

This is the pillar the UI-SPEC itself calls "the section that governs the actual reconciliation work," so it received the deepest independent verification (not just re-reading the SUMMARY narrative):

- **Rule C (component identity) verified byte-for-byte preserved**, read directly from the current files:
  - `button.tsx`: all 5 variants (`default: bg-neutral-900`, `secondary`, `outline`, `ghost`, `link`) and 4 sizes unchanged — confirmed no `bg-primary` adoption.
  - `badge.tsx`: all 9 variants present (`default`/`secondary`/`outline`/`blue`/`green`/`amber`/`red`/`purple`/`gray`), `gray` value byte-identical (`bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-400`).
  - `alert-dialog.tsx`: `AlertDialogAction`'s `bg-neutral-900`/`AlertDialogCancel`'s outline styling preserved verbatim, not retargeted to `--primary`/`--destructive`.
  - `input.tsx`/`label.tsx`/`radio-group.tsx`/`switch.tsx`/`textarea.tsx`: confirmed unchanged, all neutral-scale values intact.
- **Rule B (dark-mode elevation) verified correctly applied and independently reproduced visually**: `card.tsx`/`dialog.tsx`/`alert-dialog.tsx`/`popover.tsx`/`sheet.tsx` now resolve through `bg-card`/`bg-popover` (`oklch(0.205 0 0)` dark), not the flat `#020617`/`slate-950` magic-hex that previously gave zero elevation contrast. Confirmed by direct grep (`bg-\[#020617\]`, `dark:bg-slate-950` → zero matches anywhere in `web/src/components/ui/`) and by an independent CLI screenshot of `/login` in `--color-scheme=dark`: the card surface renders visibly lighter than the pure-black page background, corroborating the human checkpoint's `getComputedStyle` reading (~7.8% lightness vs. page `rgb(2,6,23)`).
- **Accent (`--primary`) discipline confirmed**: grep for `text-primary|bg-primary|border-primary` across `web/src/components/ui/*.tsx` returns exactly 6 hits, all in files **outside** the 13-component reconciliation scope (`avatar.tsx`, `calendar.tsx`, `checkbox.tsx`, `empty.tsx`, `native-select.tsx`, `progress.tsx`) — zero accent leakage into any Rule-C file, exactly per the spec's closed-list rule.
- **table.tsx**'s optional Rule-B convergence applied correctly (`bg-muted/50` footer/hover, `bg-muted` selected), `TableHead`/`TableCell`/`TableCaption` text colors (Rule C) untouched.
- **Deduction — issues introduced by this phase's own diffs, not inherited debt**: `card.tsx:10`, `dialog.tsx:41`, `alert-dialog.tsx:39`, `sheet.tsx:55` all carry a redundant `dark:bg-card`/`dark:bg-popover` segment that has zero visual effect (the bare token already resolves per-theme). This was flagged twice in the project's own code-review cycle (`102-REVIEW.md` IN-01, re-confirmed present in the re-review) and left unfixed both times. It's cosmetic, not a rendering bug, but it is a real, currently-shipping imprecision this phase's reconciliation work produced.
- Secondary observation (pre-existing, not phase-102-introduced, noted for completeness): `card.tsx`/`dialog.tsx`/`alert-dialog.tsx`/`popover.tsx`/`sheet.tsx` use the `slate-*` Tailwind scale for borders/text, while `button.tsx`/`input.tsx`/`badge.tsx`/`textarea.tsx` use `neutral-*` for the equivalent role — two different gray scales doing the same semantic job. Locked out of this phase's scope by the UI-SPEC's own "what must NOT change: border treatment" clause, so not counted against this phase's score, but worth flagging for a future consolidation phase.

Score: 3/4 — the reconciliation itself is materially correct and well-verified, but the redundant dark-class imprecision is this phase's own defect, not someone else's debt, and keeps this from a clean 4.

### Pillar 4: Typography (4/4)

Contracted as "not newly exercised this phase" — verified that claim rather than accepting it:

- `grep -rohn "text-\(xs|sm|base|lg|xl|2xl|...\)"` across the 13 reconciled files surfaces exactly 3 sizes in use: `text-xs` (badge, calendar day, tooltip), `text-sm` (body/description text), `text-lg` (dialog/alert-dialog/sheet titles) — well within the abstract ≤4-size guideline and unchanged from Phase 101's baseline.
- Font weights: exactly 2 in use (`font-medium`, `font-semibold`) — matches the ≤2-weight guideline.
- `Tooltip`'s own content renders at `text-xs` (`tooltip.tsx:45`), matching the UI-SPEC's explicit claim that it introduces no new type step.
- No `text-2xl`/`text-3xl` Heading/Display role appears in any of the 13 files (those live at page-level, untouched, per spec).

No regression, contract claim independently verified true. Score: 4/4.

### Pillar 5: Spacing (4/4)

Contracted as "not newly exercised this phase" — verified rather than assumed:

- `grep -rn "\[.*px\]|\[.*rem\]"` across the 13 reconciled files returns exactly one hit: `textarea.tsx:10`'s `min-h-[60px]` — a pre-existing, Rule-C, functional min-height (not a layout-spacing token), untouched by this phase's diffs.
- Icon-only touch targets retain their pre-existing sizes (`h-8 w-8`, `h-9 w-9`, `h-12 w-12` per call site) after the Tooltip wrapper was added — confirmed the wrapper adds zero dimensional change (Tooltip/TooltipTrigger render no extra box in the DOM layout since `asChild`/plain-trigger composition doesn't inject a wrapping element with its own padding).
- `rounded-none` → `rounded-lg` swap on `card`/`dialog`/`alert-dialog` correctly resolves to `0` via `--radius: 0rem` — confirmed zero visual pixel change (verified in both the light and dark login screenshots: corners remain sharp in both).

No issues found. Score: 4/4.

### Pillar 6: Experience Design (3/4)

- **`TooltipProvider` mount**: confirmed exactly one mount, in `providers.tsx:30`, with `delayDuration={700}` explicit (not relying on `tooltip.tsx`'s own `0`-default) — matches the UI-SPEC's explicit requirement verbatim.
- **Build gate**: independently re-ran `pnpm build` (not just trusting the SUMMARY's claim) — exit 0, all 24 routes compiled/typechecked cleanly, confirming no regression from any of the 3 plans' changes merged together.
- **Disabled-state coverage**: `del.isPending` correctly disables the Eliminar button during an in-flight delete (`clientes/page.tsx:649`) — but the Radix Tooltip won't fire on a native-`disabled` trigger, so the tooltip silently disappears for the (short) duration of the mutation (IN-03, documented, low-impact).
- **Destructive-action confirmation**: `onDelete` (`clientes/page.tsx:549`) uses `window.confirm(...)`, the browser's native dialog, rather than the app's own now-reconciled `AlertDialog` — a design-system consistency gap (not introduced by this phase, but notable given this very phase just finished tokenizing `AlertDialog`'s dark-surface elevation).
- **Dead code / misleading comment** (IN-02, `dashboard-shell.tsx:74-76`): `await import("@tanstack/react-query")` discards its result and does nothing; the following `window.location.href` reload masks the no-op. Harmless today, but misleading to a future maintainer.
- **Accessibility state coverage for DSR-03's own declared scope**: fully verified compliant (aria-label present and text-matched on every declared tooltip trigger; RBAC-conditional Trash2 in `settings/page.tsx:472` preserved verbatim during the wrap).
- **Human checkpoint quality**: the recorded verdict in `102-04-SUMMARY.md` is unusually rigorous (live `getComputedStyle`/LAB-value readings, accessibility-tree `aria-label` reads, both themes, 7 explicit acceptance checks) rather than a rubber-stamp — this is corroborating evidence of real verification, not just a claimed one, and materially raises confidence in the Color/Visuals findings above.

Score reflects a phase that executed its own verification gates rigorously, but the shipped app still carries the disabled-tooltip edge case and the native-confirm/AlertDialog inconsistency. Score: 3/4.

---

## Registry Safety

`web/components.json` exists (shadcn initialized), but `102-UI-SPEC.md`'s Registry Safety table lists **only** `shadcn official` for both rows in scope (13 reconciled components + `Tooltip`) — the milestone's `REQUIREMENTS.md` explicitly excludes third-party registries for its entire scope. Per the registry-audit trigger condition (only runs when a third-party registry is listed), no `shadcn view`/`diff` suspicious-pattern scan was executed.

Registry audit: 0 third-party blocks in scope, no flags.

---

## Files Audited

- `web/src/components/ui/button.tsx`
- `web/src/components/ui/badge.tsx`
- `web/src/components/ui/input.tsx`
- `web/src/components/ui/label.tsx`
- `web/src/components/ui/radio-group.tsx`
- `web/src/components/ui/switch.tsx`
- `web/src/components/ui/textarea.tsx`
- `web/src/components/ui/calendar.tsx`
- `web/src/components/ui/breadcrumb.tsx`
- `web/src/components/ui/card.tsx`
- `web/src/components/ui/popover.tsx`
- `web/src/components/ui/dialog.tsx`
- `web/src/components/ui/alert-dialog.tsx`
- `web/src/components/ui/table.tsx`
- `web/src/components/ui/sheet.tsx`
- `web/src/components/ui/tooltip.tsx`
- `web/src/app/providers.tsx`
- `web/src/app/globals.css`
- `web/src/components/shared/dashboard-shell.tsx`
- `web/src/app/(dashboard)/clientes/page.tsx`
- `web/src/app/(dashboard)/settings/page.tsx`
- `web/src/app/(dashboard)/processos/page.tsx` (spot-checked for out-of-scope icon-button gap)
- `web/src/app/(dashboard)/pareceres/page.tsx` (spot-checked for out-of-scope icon-button gap)
- `web/src/app/(dashboard)/agenda/page.tsx` (spot-checked)
- `web/src/app/(dashboard)/notificacoes/page.tsx` (spot-checked)
- `web/src/app/(dashboard)/processos/dashboard/page.tsx` (spot-checked)
- `web/package.json`
- `web/components.json`
- `.planning/phases/LEXCV-102-reconcilia-o-do-design-system/102-UI-SPEC.md`
- `.planning/phases/LEXCV-102-reconcilia-o-do-design-system/102-CONTEXT.md`
- `.planning/phases/LEXCV-102-reconcilia-o-do-design-system/102-01-SUMMARY.md` through `102-04-SUMMARY.md`
- `.planning/phases/LEXCV-102-reconcilia-o-do-design-system/102-REVIEW.md`
- `.planning/phases/LEXCV-102-reconcilia-o-do-design-system/102-REVIEW-FIX.md`

**Independent verification performed (not just re-reading SUMMARY claims):** `pnpm build` re-run (green, 24/24 routes); direct `Read` of all 16 `components/ui` files in scope; grep-based regression checks for magic hex, scoped-radix imports, `bg-primary` leakage, arbitrary spacing, font-size/weight distribution; CLI screenshot capture of `/login` in light and dark `--color-scheme` (dev server at `localhost:3003`) as independent visual corroboration of the Rule-B dark-elevation change; broadened icon-only-button grep across all `(dashboard)/**/page.tsx` files to independently confirm (and extend) the DSR-03 coverage table.
