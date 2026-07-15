# Pitfalls Research

**Domain:** Retrofitting the official shadcn/ui CLI onto an existing production Next.js app that already has hand-rolled shadcn-lookalike components (LexCV `web/` + `webpage/`, v2.13 milestone)
**Researched:** 2026-07-15
**Confidence:** HIGH for CLI mechanics (verified against official docs + GitHub issues + Feb/Mar 2026 changelogs), HIGH for codebase-specific findings (verified by reading actual source), MEDIUM for migration-specific UX regressions (community-reported, cross-checked with 2+ sources)

## Project Baseline (verified by direct inspection, not assumed)

These facts materially change the risk profile versus a generic "add shadcn to existing project" guide, and are referenced throughout:

- **No `components.json` in `web/` or `webpage/`.** Confirmed absent in both. First `init` run is genuinely first-run, not a re-run.
- **`web/` has 15 hand-rolled `components/ui/*.tsx` files** (alert-dialog, badge, button, card, dialog, input, label, popover, radio-group, sheet, switch, table, textarea, toast, toaster), all built directly on individually-installed `@radix-ui/react-*` packages (not the unified `radix-ui` package — see Pitfall 3).
- **Color tokens are NOT semantic.** `globals.css` in both apps defines only `--background`/`--foreground` (2 variables, plain hex, no `hsl()` wrapper). None of shadcn's standard semantic tokens exist yet: `--primary`, `--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, `--radius`, chart/sidebar tokens. Every hand-rolled component instead hardcodes raw Tailwind color-scale utilities directly in `cva()` variants (`bg-neutral-900`, `border-slate-200`, `bg-red-500`, etc.) — and **inconsistently**: `badge.tsx` uses the `neutral-*` scale while `card.tsx`/`toast.tsx` use `slate-*`. This is a pre-existing internal inconsistency the milestone must resolve, not a clean baseline to "preserve."
- **`rounded-none` is a deliberate, hardcoded design choice** in `card.tsx` and `dialog.tsx` (sharp corners = institutional identity). Official shadcn templates default to a rounded `--radius` token (`0.625rem`/`rounded-lg`/`rounded-md`). This is a direct visual collision waiting to happen.
- **`badge.tsx` has a project-specific `gray` variant** not present in official shadcn Badge, used in 3 files (`notificacoes/page.tsx`, `dashboard/page.tsx`, `processos/page.tsx`).
- **93 import occurrences across 38 files** reference `@/components/ui/{button,dialog,badge,card,table,sheet,toast}` — this is the blast radius for any CLI overwrite of those files.
- **`web/` and `webpage/` are two independent Next.js apps, not a true pnpm workspace.** No root `pnpm-workspace.yaml`, no root `package.json` — each app has its own `pnpm-lock.yaml` and its own `pnpm-workspace.yaml` (webpage's only exists for an unrelated `minimumReleaseAgeExclude` supply-chain pin, per `PROJECT.md` Key Decisions v2.12/Phase 100). Both use an identical `@/*` → `./src/*` tsconfig alias and an identical `cn()` in `lib/utils.ts`. This means the "monorepo path alias drift" risk from shadcn's official monorepo doc (which assumes `apps/*` + `packages/ui` with `@workspace/ui/*` aliases) does **not** apply here — but a *simpler*, still-real risk does: two fully independent `shadcn init` wizard runs with no shared config, easy to answer differently (style, base color, CSS variable naming) with nobody catching it until visual QA.
- **The "7-tab" ficha-de-cliente pattern is a flex row of plain `<Button variant={tab === x ? "secondary" : "outline"}>` elements** (`web/src/app/(dashboard)/clientes/[id]/page.tsx`), wrapped in `overflow-x-auto` for horizontal scroll, with tabs conditionally rendered per RBAC (`canViewProcessos`, `canViewPareceres`). It has **zero ARIA tab semantics today** (no `role="tablist"/"tab"`, no `aria-selected`) — Key Decisions explicitly chose this over shadcn `Tabs` "porque nunca foi inicializado" (Tabs was never initialized), not for a technical reason.
- **The dual-view responsive pattern is real and repeated 7 times**: every list page (`clientes`, `processos`, `agenda`, `financeiro`, `documentos`, `pareceres`, `notificações`) wraps a hand-rolled card list in `md:hidden` and the shadcn-style `<Table>` in `hidden md:block` as two independent sibling render branches (verified directly in `clientes/page.tsx` lines 416-476), not a single responsive table.
- **`sheet.tsx` was hand-written, not CLI-generated**, explicitly because "CLI `npx shadcn` exige setup interativo" (Key Decisions, v2.3) — it will look like an official component to future readers but has never been diffed against the registry.

## Critical Pitfalls

### Pitfall 1: `shadcn init` silently corrupts the existing dark/light theme by appending a second `:root`/`.dark` block

**What goes wrong:**
`shadcn@latest init` on an existing Next.js project does not surgically merge CSS variables — it writes its own `:root`/`.dark` blocks (and, for the App Router, its own `@theme inline` mapping) into `globals.css`, typically appended after existing content. Because CSS custom properties cascade "last wins," a second `:root { --background: ... }` block silently overrides the first at runtime while the original block remains visually present in the diff, making a `git diff` look like a pure addition when it is actually also a behavioral override. Community reports (GitHub issue #2791, "init command overwrites global.css"; issue #4845, default Next.js `:root` colors interfering with the theme) confirm this is a known, unresolved, closed-as-stale issue — no CLI-side protection exists.

**Why it happens:**
The CLI's `init` is designed for greenfield projects where `globals.css` is still the framework boilerplate. It has no way to detect "this file has already been hand-tuned and shipped to production" — it treats any existing `:root`/`.dark` block as safe to append alongside.

**How to avoid:**
- Before running `init`, snapshot `globals.css` (git commit, or copy to scratch) in both `web/` and `webpage/`.
- Run `init` with `--dry-run` first (CLI v4, March 2026) to preview the exact CSS diff before anything is written — do not skip this step even though `--dry-run` is new and unfamiliar.
- After `init` runs, manually inspect `globals.css` for **duplicate `:root`/`.dark` blocks** — if the CLI appended a second block, hand-merge into a single block that defines the full semantic token set (`--primary`, `--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, `--radius`) while keeping the two already-shipped values (`--background: #f8fafc` / `#020617`, `--foreground: #020617` / `#f8fafc`) as the source of truth, not the CLI's generated defaults.
- Since `--radius` doesn't exist yet in this project but `rounded-none` is hardcoded everywhere, explicitly set `--radius: 0rem` (or the deliberate sharp-corner value) in the merged block — do not accept the CLI's default rounded value.
- Visually diff both themes (light+dark) against the Figma reference before merging to main, on both `web/` and `webpage/` separately (they will each get their own `init` run).

**Warning signs:**
- `git diff` on `globals.css` shows two `:root { ... }` blocks or two `.dark { ... }` blocks after `init`.
- Background/foreground/border colors look "slightly off" (not obviously broken) after adding the first new component — this is the append-and-override failure mode, not a total breakage.
- Any hex value appearing where an `hsl(...)`-wrapped or bare CSS variable was expected (CLI templates increasingly emit `hsl(...)`-wrapped values; mixing formats inside `oklch`/`hsl`-based Tailwind v4 theme functions silently produces `transparent` or wrong colors rather than an error).

**Phase to address:**
Foundation phase (shadcn CLI init) — must be the very first ticket, gated on a manual visual sign-off against the Figma reference before any per-module refactor phase begins.

---

### Pitfall 2: `shadcn add <component>` overwrites a hand-rolled file whose prop API has silently drifted from the official one, and 38 files import it

**What goes wrong:**
Current CLI behavior (verified against `ui.shadcn.com/docs/cli` and GitHub issues #931, #7739, #7672, #7794): running `add` on a component that already exists at the resolved path throws unless `-o/--overwrite` (or in some recent versions, an interactive "already exists, overwrite?" prompt) is used. If overwrite is accepted — which is easy to do reflexively when the CLI is "just installing button" — the file is replaced wholesale. Concretely verified drift in this codebase:
- `badge.tsx` has a **project-specific `gray` variant** (used in 3 files) that does not exist in the official Badge; overwriting deletes it, causing a TypeScript compile error at every call site (`variant="gray"` no longer assignable) — the *good* outcome, since it fails loudly at build time.
- `button.tsx` in this project has variants `default | secondary | outline | ghost | link` and sizes `default | sm | lg | icon`. The current official button.tsx (`new-york` style) adds a `destructive` variant and an `xs` size, and its variants are built on `bg-primary`/`text-primary-foreground` semantic tokens that don't exist yet in this project's `globals.css` (see Pitfall 1) — if `--primary` isn't defined, the "default" button variant silently renders unstyled/transparent instead of erroring.
- `dialog.tsx` in this project has no `showCloseButton` prop; the current official version defaults `DialogContent`'s close button to visible via a `showCloseButton` prop. Not breaking by itself, but any call site that manually renders its own close affordance inside `DialogContent` (worth auditing) would end up with two close buttons after an overwrite.
- 93 total import occurrences across 38 files touch just the 7 components enumerated in this research (`button`, `dialog`, `badge`, `card`, `table`, `sheet`, `toast`) — a blind `--overwrite` across the whole `components/ui/` directory is a wide blast radius with no automated regression coverage (no visual/E2E tests in this repo per `PROJECT.md`).

**Why it happens:**
Fourteen/fifteen components were hand-written over multiple milestones (v1.1 through v2.9) by matching *visual output*, not by running the CLI and then customizing — so each one accumulated small, undocumented deviations from upstream (a variant added here, a class removed there) with no record of what changed or why.

**How to avoid:**
- Never run `add --overwrite` blind. For every one of the 15 existing files, first run `shadcn diff <component>` (or CLI v4's `add --diff <component>` / `--dry-run`) to see the exact delta between the hand-rolled version and the current registry version before deciding whether to accept it.
- Treat each of the 15 files as requiring a **manual reconciliation pass**, not an overwrite: keep project-specific variants (`gray` badge, any custom sizes), adopt upstream's semantic-token-based styling only once the token set from Pitfall 1 is merged, and grep for every call site of a prop/variant before removing it.
- Use `shadcn info` (CLI v4) to get a quick inventory of what's "installed" vs. registry-current before starting the reconciliation sweep.
- Do the reconciliation component-by-component in small, individually-reviewable diffs (one PR per component or small group), not "add all 15 at once."

**Warning signs:**
- TypeScript errors immediately after an `add --overwrite` are the *safe* failure (caught at build). The dangerous case is when the new file compiles fine but silently drops a class/behavior with no type signal — e.g., an unstyled/transparent button because `--primary` isn't defined yet.
- Visual QA on any page using `Badge`, `Button`, `Dialog`, or `Card` shows unexpected default (rounded, un-themed, or missing variant) styling after a component add.

**Phase to address:**
Per-module refactor phases, but the *reconciliation protocol* (diff-first, never blind-overwrite, grep call sites before removing a variant) must be established as a rule in the foundation phase so every subsequent phase follows it.

---

### Pitfall 3: Radix package identity mismatch — new components import from the unified `radix-ui` package, old ones from scoped `@radix-ui/react-*` packages

**What goes wrong:**
As of the shadcn `new-york` style change documented in the **February 2026** changelog (verified via `ui.shadcn.com/docs/changelog/2026-02-radix-ui` — this is very recent and post-dates most training data), CLI-generated components now import Radix primitives from a single unified `radix-ui` package:
```
- import * as DialogPrimitive from "@radix-ui/react-dialog"
+ import { Dialog as DialogPrimitive } from "radix-ui"
```
This project's 15 hand-rolled components (confirmed in `package.json`: `@radix-ui/react-alert-dialog`, `-dialog`, `-label`, `-popover`, `-radio-group`, `-slot`, `-switch`, `-toast`) all use the old scoped-package import style. If `shadcn init` (which will default to `new-york` style unless explicitly told otherwise) is run now and new components are added, the project ends up with **two parallel Radix dependency trees** — the old scoped packages (still required by the 15 unmigrated files) and the new unified `radix-ui` package (required by anything the CLI adds from here on) — installed side by side. This inflates bundle size, risks two different pinned Radix internal versions behaving subtly differently for the same primitive (e.g., Dialog focus-trap edge cases), and is exactly the kind of two-tone hybrid state that undermines the milestone's stated goal of *consistency*.

**Why it happens:**
The CLI made this a **breaking change to what "new" components import** without touching files it didn't generate. There is no automatic detection of "this file was hand-written before the unification and should also be migrated."

**How to avoid:**
- Decide explicitly, as part of the foundation phase, whether to run `shadcn@latest migrate radix` (the CLI's official migration command for exactly this situation) **before or immediately after** `init`, so that the 15 existing files and every newly-added component agree on the same Radix import convention from day one, rather than reconciling it component-by-component later.
- If migrating existing files to `radix-ui` isn't feasible in the foundation phase (e.g., it's bundled with the reconciliation work from Pitfall 2), at minimum **pin and document** which convention each file uses, and do not let the two conventions coexist past the milestone's end — track it as an explicit checklist item, not an assumption that "it still works so it's fine."
- After migrating, run `pnpm dedupe`/check `pnpm-lock.yaml` for the old scoped `@radix-ui/react-*` packages and remove ones no longer imported anywhere, to avoid shipping dead dependencies.

**Warning signs:**
- `package.json` (after any CLI `add`) contains both `radix-ui` and multiple `@radix-ui/react-*` entries with no clear plan to consolidate.
- Two different Dialog/Popover/Switch implementations behave subtly differently under the same test (e.g., focus return target after close) because they're wired to different underlying Radix versions.

**Phase to address:**
Foundation phase — this is a one-time, project-wide decision (migrate now vs. document-and-defer) that every later per-module phase inherits; deciding it late means redoing work in every module that already got new components.

---

### Pitfall 4: Tailwind v4 CSS-first config is correctly assumed by the CLI, but training-data-era mental models (tailwind.config.js + darkMode array) can still leak into hand-written follow-up code

**What goes wrong:**
Verified against official docs (`ui.shadcn.com/docs/tailwind-v4`) and the `components.json` monorepo schema guidance: Tailwind v4 shadcn projects use `@theme`/`@theme inline` **in CSS**, not `tailwind.config.js`. `components.json`'s `tailwind.config` field is meant to be left as an **empty string** for v4 projects (there is nothing to point it at), and `darkMode: ["class"]` no longer lives in a JS config — it's expressed via `@custom-variant dark (&:is(.dark *));` in CSS, which **this project already has correctly** in both `globals.css` files. The actual risk isn't the CLI getting this wrong (it correctly detects v4/zero-config here, since neither app has a `tailwind.config.js` — only `postcss.config.mjs`) — the risk is a developer (or an AI assistant drawing on stale training data) manually "fixing" what looks like a missing config by creating a `tailwind.config.js` with a `darkMode` array during the milestone, which does nothing in v4 (silently ignored) and creates a confusing, dead file that looks authoritative but has zero effect, wasting debugging time when dark mode "isn't working" for an unrelated reason.
Also verified: the official docs do **not** specify a default `--radius` value in the excerpt reviewed — this must be independently decided (see Pitfall 1) rather than assumed from older v3-era guides that show `--radius: 0.5rem` as gospel.

**Why it happens:**
Most existing shadcn tutorials, and a large fraction of any LLM's training data, predate the Tailwind v4 CSS-first model and still show `tailwind.config.js` + `darkMode: ["class"]` as the standard setup. `web/CLAUDE.md`/`web/AGENTS.md` already warn generically about Next.js 16 training-data staleness; the same caveat applies specifically to Tailwind v4 config conventions and was not previously called out for this milestone.

**How to avoid:**
- Explicitly confirm, as a foundation-phase checklist item, that `components.json`'s `tailwind.config` is left empty (`""`) for both `web/` and `webpage/`, and that no `tailwind.config.js`/`.ts` file is created by any subsequent step (manual or CLI).
- Anyone (human or agent) touching Tailwind/dark-mode config during this milestone should re-read `ui.shadcn.com/docs/tailwind-v4` (or the CSS-first sections of the installed `next`/`tailwindcss` docs) rather than pattern-matching from memory, exactly as `web/AGENTS.md` already mandates for Next.js 16 itself.
- Do not "port" `darkMode: ["class"]` into a new config file — the existing `@custom-variant dark (&:is(.dark *));` line already does this and should be left untouched.

**Warning signs:**
- A `tailwind.config.js`/`.ts` file appears in `web/` or `webpage/` for the first time during this milestone — this is itself a red flag given both currently ship with zero-config Tailwind v4.
- Dark mode toggling appears broken and someone's first instinct is to look for `darkMode` in a JS config file instead of the `@custom-variant` line in `globals.css`.

**Phase to address:**
Foundation phase — a one-line checklist ("no tailwind.config.js added; components.json tailwind.config is empty string") that any later phase can quickly verify hasn't regressed.

---

### Pitfall 5: Two independent `shadcn init` runs (`web/` + `webpage/`) drift apart with nobody coordinating the answers

**What goes wrong:**
`web/` and `webpage/` are two standalone Next.js apps (no shared workspace root, confirmed no root `package.json`/`pnpm-workspace.yaml`), each requiring its own `components.json`. Because there is no shared configuration to inherit from, running `shadcn init` twice — once per app, likely at different times or by different people/sessions — creates a real chance of picking different answers to the same interactive prompts (style: `new-york` vs `default`; base color; CSS variables on/off; icon library). Since both apps currently share an *identical* minimal `globals.css` (byte-for-byte the same 2-variable theme), this is the moment that identity could fork for the first time — directly working against the milestone's explicit goal of visual consistency between the dashboard and the landing page.

**Why it happens:**
Nothing in the CLI or in this repo enforces "these two apps must answer identically." `shadcn`'s own monorepo guidance (`ui.shadcn.com/docs/monorepo`) explicitly warns that even in a true workspace, "you need to ensure you have the same `style`, `iconLibrary` and `baseColor` in both `components.json` files" — and this repo doesn't even have the true-workspace scaffolding that guidance assumes, making it *easier*, not harder, to drift, since there's no shared `packages/ui` to force reuse.

**How to avoid:**
- Run `web/`'s `init` first (it's the app with the richer, already-shipped component surface and the Figma-validated identity), let the reconciliation from Pitfall 1/2 settle there, then **copy the resolved `components.json` answers and the final merged `globals.css` token block into `webpage/` manually** rather than re-running the interactive wizard independently and hoping it lands the same way.
- After both are initialized, diff `web/components.json` against `webpage/components.json` field-by-field (`style`, `baseColor`, `cssVariables`, `iconLibrary`, `aliases`) as an explicit foundation-phase verification step.
- Since both apps already have identical `@/*` tsconfig aliases and identical `lib/utils.ts`, keep the `aliases` section of both `components.json` files identical too — there's no reason for them to diverge given the current setup.

**Warning signs:**
- `web/components.json` and `webpage/components.json` show different `style` or `baseColor` values after both are initialized.
- Landing page (`webpage/`) components look visually "close but not quite" to the dashboard (`web/`) — different border radius, different button hover shade — despite both claiming to follow the same design system.

**Phase to address:**
Foundation phase — must explicitly sequence `web/` init before `webpage/` init (not "do both in parallel") and include a field-by-field `components.json` comparison as a done-criterion.

---

### Pitfall 6: Migrating the 7-tab ficha-de-cliente toggle pattern to Radix `Tabs` drops the mobile horizontal-scroll behavior that made it usable in the first place

**What goes wrong:**
The current implementation (verified directly, `clientes/[id]/page.tsx`) wraps a `flex gap-2 w-max` row of buttons in a manually-added `overflow-x-auto` container specifically so all 7 (RBAC-conditional) tab buttons remain reachable on narrow viewports without wrapping awkwardly. Official shadcn `TabsList` defaults to `inline-flex ... items-center justify-center rounded-md bg-muted p-1` with **no built-in horizontal-scroll affordance** — it assumes a small, fixed number of tabs that fit on one line. A straight swap to shadcn `Tabs`/`TabsList`/`TabsTrigger` without re-adding `overflow-x-auto` (and testing at the actual mobile breakpoint with all 7 tabs visible, since visibility is RBAC-dependent and can't be verified with a single test account) will silently reintroduce the exact overflow/clipping problem the v2.3 responsiveness milestone already solved for this page.

**Why it happens:**
Copy-pasting the shadcn docs' `Tabs` example (which typically shows 2-4 static tabs) doesn't surface the overflow problem because the demo never has enough tabs to overflow. The RBAC-conditional tab count (`canViewProcessos`/`canViewPareceres` can each independently hide a tab) makes this an easy thing to miss in a quick visual check with an ADMIN test account, since ADMIN sees all 7 and the layout may look fine, while a role missing one or two permissions shows a different (and untested) tab count.

**How to avoid:**
- When migrating, explicitly re-add `overflow-x-auto` (or equivalent) to whatever wraps `TabsList`, and functionally test at 375px width with each of the RBAC roles that hide different tabs (ADVOGADO, TECNICO, ASSISTENTE), not just ADMIN.
- Radix `Tabs.Trigger` already renders as a real `<button>` with `role="tab"`/`aria-selected` wired up automatically — do not fight this by using `asChild` to force the exact same visual markup as today's plain `<Button>` toggle; instead restyle `TabsTrigger` directly via `className`/CVA so the accessibility wiring survives untouched (see Pitfall 8).
- Preserve the controlled `value`/`onValueChange` pattern already used (`tab`/`setTab` state) — this maps directly onto Radix `Tabs`' controlled mode, so the underlying state management doesn't need to change, only the rendering layer.

**Warning signs:**
- After migration, tabs wrap to a second line or get clipped/hidden on a real mobile viewport, especially for a role that sees fewer than 7 tabs (different flex-wrap behavior at different item counts).
- Keyboard arrow-key navigation (new behavior Radix adds for free) scrolls the tab strip in a jarring way if the scroll container and Radix's roving-tabindex focus-then-scroll-into-view interact badly — test keyboard nav explicitly, since today's implementation has none to compare against.

**Phase to address:**
Per-module refactor phase covering Clientes (ficha) — should NOT be bundled into the foundation phase, since it's module-specific UI, but the foundation phase should first establish that `Tabs` is available and RBAC-conditional-trigger patterns are documented once, so this phase (and the equivalent Processos ficha, which already replicates the same visual pattern per v2.9 Key Decisions) doesn't reinvent it twice.

---

### Pitfall 7: Adopting shadcn `Table`/`DataTable` docs literally replaces the dual-view (card+table) pattern instead of only reskinning the desktop half

**What goes wrong:**
Every list page in this app (`clientes`, `processos`, `agenda`, `financeiro`, `documentos`, `pareceres`, `notificações` — verified 7 modules) already implements the v2.3 "Responsividade App" decision as two independent sibling render branches: a hand-rolled card list under `md:hidden`, and a `<Table>` under `hidden md:block` (confirmed directly in `clientes/page.tsx`). shadcn's own `Table`/`DataTable` documentation and most community `DataTable` recipes (TanStack Table-based) assume a **single responsive table** achieved via horizontal scroll (`overflow-x-auto` wrapping one `<table>`) — they do not model a "completely different markup below `md`" pattern, because most shadcn example apps don't have this constraint. A developer following the docs literally, especially when introducing `DataTable`'s sorting/filtering/pagination features, is likely to consolidate the two branches into one scrollable table "to reduce duplication," which silently drops the mobile card UX that v2.3 delivered specifically because a horizontally-scrolling table is a worse mobile experience for these dense entity lists (clientes, processos) than a card layout.

**Why it happens:**
The dual-view pattern is *not* a shadcn convention — it's this project's own pre-existing decision (documented in `PROJECT.md` Key Decisions, v2.3), and shadcn's docs have no opinion on it one way or the other. Nothing in the shadcn migration flow warns "you have a custom responsive pattern here, don't discard it," so the risk is entirely about the implementer's judgment call while following generic library guidance.

**How to avoid:**
- Explicitly scope any `Table`/`DataTable` adoption to **only the `hidden md:block` desktop branch** of each list page; do not touch the `md:hidden` card branch's markup, only its underlying data shape/hooks if those are being refactored for other reasons.
- If `DataTable`'s sorting/filtering is desired, share the *state* (sort key, filter value) between both branches via the same hook/URL params, but keep two separate render trees — mirroring exactly what's there today, just with the desktop `<table>` internals modernized.
- Treat "should the dual-view pattern itself be reconsidered" as an explicit, separate product decision requiring sign-off — not something that happens as a side effect of a `Table` component swap.

**Warning signs:**
- A PR touching a list page's desktop table also modifies or deletes the `md:hidden` card block "for consistency."
- Mobile viewport testing on a list page shows a horizontally-scrolling table where a card list used to be.

**Phase to address:**
Per-module refactor phases (one per list-bearing module) — the foundation phase should state the rule once ("dual-view is preserved, only desktop `<table>` internals get modernized") so each of the 7 module phases inherits it rather than re-deciding it.

---

### Pitfall 8: Swapping the hand-rolled `Dialog`/`Sheet` for shadcn's risks losing project-specific props, not Radix accessibility (which was never actually at risk)

**What goes wrong:**
This project's `dialog.tsx` is already built directly on `@radix-ui/react-dialog` (confirmed by reading the file) — it is not a from-scratch modal. This means the underlying focus-trap, ESC-to-close, click-outside, and ARIA role/labelling behavior is **already** Radix's, identical to what an official shadcn `Dialog` would provide, because both are thin styling wrappers over the same primitive. The real regression risk when "swapping to shadcn's Dialog" is therefore not a loss of Radix accessibility guarantees — it's (a) losing project-specific styling decisions expressed as raw classes rather than tokens (e.g., `rounded-none`, `dark:bg-[#020617]` hardcoded hex instead of a `--popover`/`--card` token, the `sr-only` "Fechar" label on the close button, which is a nice existing a11y touch that must be preserved verbatim if the close button markup is replaced), and (b) the `sheet.tsx` file, which was **hand-written to mimic `dialog.tsx`'s pattern specifically because the CLI was never run** (Key Decisions, v2.3) — it has never been diffed against an actual Sheet registry entry and may have accumulated its own undocumented drift the same way the other 14 components did.

**Why it happens:**
"Accessibility regression" is the headline fear stated in the milestone context, but because this codebase already builds directly on Radix primitives rather than custom-rolling modal behavior, the actual risk surface is narrower and different from the generic warning — it's prop/class parity, not ARIA/focus-trap parity. Teams that assume "the whole component might be unsafe accessibility-wise" can waste effort re-verifying things (focus trap, ESC handling) that were never actually at risk, while missing the real, narrower risk (dropped `sr-only` labels, dropped custom close-button positioning, undiffed `sheet.tsx`).

**How to avoid:**
- Do not re-litigate Radix's focus-trap/ARIA behavior for `Dialog`/`AlertDialog`/`Popover`/`Switch`/`RadioGroup` — it doesn't change by adopting the CLI's version, since both wrap the same primitive. Spend the verification budget instead on: (1) diffing `dialog.tsx` against the current registry version to catch dropped props (`showCloseButton`) or dropped `sr-only` text, and (2) running `sheet.tsx` through `shadcn diff sheet` (once `Sheet` is actually added via the CLI for the first time) specifically because it was never CLI-verified and is the one hand-rolled file with no registry counterpart to have ever been checked against.
- For any component that genuinely lacks a Radix primitive underneath in the *target* shadcn version (rare, but check `Command`, `Calendar`, which are new additions per the milestone's feature list and are **not** currently in the 15-file inventory) — these are the ones warranting an actual fresh accessibility audit, since there's no existing Radix-backed baseline to compare against.
- Keep the `sr-only`/visually-hidden "Fechar" close-button label pattern (already present) as a required checklist item on every Dialog/Sheet/AlertDialog touched during the refactor.

**Warning signs:**
- A "migrated" Dialog/Sheet has no visually-hidden text on its icon-only close button (regression from what's there today).
- Time being spent manually re-testing keyboard Tab-trapping or ESC-to-close on components that were already Radix-based before the milestone — this is very likely wasted effort; that behavior didn't change.
- `Command`, `Calendar`, `NavigationMenu` (new to this project per the feature list) ship without any keyboard-navigation smoke test, since — unlike the other 15 — there's no existing hand-rolled Radix-backed version to fall back on for comparison.

**Phase to address:**
Foundation phase should establish the "diff-first, don't re-verify what's already Radix-backed" rule and specifically flag `Command`/`Calendar`/`NavigationMenu`/`Tabs`/`Select`/`DropdownMenu`/`Tooltip`/`Form`/`Checkbox`/`Avatar`/`Accordion` (the components listed in `PROJECT.md`'s target features as *missing* from the current 15) as the ones needing a genuine first-time accessibility check, since they have no existing Radix-backed baseline in this codebase to compare against. Per-module phases then only need the narrower diff-based check for the 15 that already exist.

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|-----------------|------------------|
| Running `add --overwrite` on all 15 existing components at once instead of one-by-one reconciliation | Fast, looks like "done" quickly | Untracked loss of the `gray` badge variant and any other undocumented custom variant; no way to bisect which component broke a page | Never for this milestone — no automated visual regression tests exist to catch it |
| Leaving `web/` and `webpage/` on different `components.json` `style`/`baseColor` answers "to fix later" | Unblocks parallel work on both apps immediately | Visual identity forks between dashboard and landing page, directly contradicting the milestone's stated goal | Never — reconcile before either app's per-module phase starts |
| Keeping both `@radix-ui/react-*` scoped packages and the new unified `radix-ui` package installed side-by-side "until everything is migrated" | Avoids a disruptive migration step up front | Bundle bloat, two Radix version trees, subtle primitive-behavior drift between old and new components | Acceptable only as a short, explicitly-tracked bridge state within the foundation phase — not something that should still be true at milestone close |
| Using `rounded-none` hardcoded per-component instead of a `--radius: 0` token during transition | No CSS variable work needed to keep today's sharp-corner look | New CLI-added components default to a rounded token unless every one is manually patched, guaranteeing visual inconsistency until each is touched | Acceptable briefly during foundation phase only if `--radius` is defined in the very same PR |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|-----------------|-------------------|
| `shadcn init` on Tailwind v4 project | Assuming it needs/creates a `tailwind.config.js` with `darkMode: ["class"]` (v3-era mental model) | Confirm `components.json`'s `tailwind.config` stays `""`; dark mode is the existing `@custom-variant dark (&:is(.dark *));` line in CSS |
| `shadcn add <component>` on a file that already exists | Reflexively passing `--overwrite`/`-y` to unblock the CLI without reviewing the diff | Run `diff`/`--dry-run` first; reconcile manually component-by-component |
| Two independent apps (`web/`, `webpage/`) both running `init` | Running both wizards independently and assuming they'll land on the same answers | Init `web/` first, then hand-copy its resolved `components.json`/theme tokens into `webpage/` |
| Radix package unification (Feb 2026 shadcn change) | Adding new components (unified `radix-ui` import) alongside untouched old ones (`@radix-ui/react-*` import) with no plan to reconcile | Run `shadcn migrate radix` as an explicit, tracked foundation-phase step, not an afterthought |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|-----------------|
| Two Radix dependency trees (`radix-ui` unified + multiple `@radix-ui/react-*`) left installed simultaneously | Larger client JS bundle for dialogs/popovers/dropdowns; duplicate primitive code shipped twice | Complete the `migrate radix` step and prune unused scoped packages from `package.json`/lockfile in the same phase, not deferred | Noticeable in bundle-size CI checks (if added) or Lighthouse JS-payload audits; not urgent at current scale but compounds with every new component added under the old convention |
| `DataTable` with client-side sort/filter/pagination added to already-large list pages (clientes, processos) without checking existing server-side filtering | Duplicate filtering logic (client re-filters what the server already filtered), stale/incorrect row counts | Wire `DataTable` state to the same TanStack Query filters already used by the hook, don't re-implement filtering client-side | Breaks first at the module with the most rows (likely `processos` or `documentos`) once real tenant data volume exceeds a single page |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Copy-pasting a shadcn `Command`/`Combobox` example that fetches data client-side into a picker previously backed by a permission-gated hook (e.g., the `GET /users` picker from Phase 87) | Could reintroduce the ADMIN-only-endpoint RBAC bug pattern already fixed once in this codebase (Key Decisions v2.10, Phase 87) if a new component swap bypasses the existing scoped hook | Any new shadcn `Command`/`Combobox`/`Select` replacing an existing picker must keep calling the same RBAC-scoped hook/endpoint, never a fresh fetch written from a shadcn example |
| Treating visual refactor phases as "frontend-only, no RBAC review needed" | The RBAC-conditional 7-tab pattern (Pitfall 6) and RBAC-scoped list filters (Pitfall 7) are both UI-layer expressions of backend authorization; a careless swap can visually restore access to a tab/row a role shouldn't see if the permission check isn't ported over 1:1 | Every per-module phase's done-criteria must explicitly re-verify the same `permissions.can.view/edit/manage` gating exists post-refactor, not just "looks the same" |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-------------------|
| 7-tab toggle row overflow reintroduced on mobile after `Tabs` migration | Advogado/Técnico/Assistente users with fewer visible tabs (RBAC-dependent) hit a broken/clipped tab bar that ADMIN testing wouldn't catch | Test tab overflow at all RBAC role levels, not just ADMIN, at 375px width |
| Dual-view list pages consolidated into one scrollable table | Mobile users lose the card-based glanceable list (nome, badges, quick actions) in favor of a cramped horizontally-scrolling table, a UX regression from the already-shipped v2.3 responsiveness work | Keep card view and table view as separate render branches; only modernize the desktop table's internals |
| Partial/incremental token adoption (some components on `bg-primary`, others still on hardcoded `bg-neutral-900`) visible mid-milestone | Visually inconsistent app during the transition window — buttons and badges looking like they're from two different design systems simultaneously | Sequence the foundation phase so the full semantic token set is defined and visually verified *before* any per-module phase starts swapping individual components, minimizing the "half-migrated" window |

## "Looks Done But Isn't" Checklist

- [ ] **`shadcn init` completed:** Often "done" the moment the command exits with no errors — verify no duplicate `:root`/`.dark` blocks landed in `globals.css`, and that light+dark mode still visually match the Figma reference on both `web/` and `webpage/`.
- [ ] **Component added via CLI:** Often looks done because the file compiles — verify every custom variant/prop that existed in the hand-rolled version (e.g., `badge`'s `gray` variant) still exists, and grep all call sites of that component for anything the new version dropped.
- [ ] **`--radius`/token set merged:** Often looks done because colors render — verify `rounded-none` (or the deliberately chosen `--radius` value) is consistent across *all* components, old and newly-added, not just the ones touched so far.
- [ ] **Radix package migration:** Often looks done because the app still runs — verify `package.json` doesn't contain both `radix-ui` and lingering unused `@radix-ui/react-*` entries.
- [ ] **`web/`/`webpage/` consistency:** Often looks done because both apps "use shadcn now" — verify `components.json` in both apps agree field-by-field (style, baseColor, cssVariables, iconLibrary, aliases).
- [ ] **7-tab / dual-view migrations:** Often looks done because it renders correctly for an ADMIN test account on desktop — verify at every RBAC role and at mobile viewport width before calling a module's refactor phase complete.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|----------------|-----------------|
| Duplicate `:root`/`.dark` blocks after `init` | LOW | Manually merge the two blocks into one, keeping the original shipped hex values as source of truth; re-run visual diff against Figma |
| Blind `--overwrite` wiped a custom variant (e.g., badge `gray`) | LOW–MEDIUM | TypeScript will fail the build at every call site immediately — re-add the variant to the new file's `cva()` config, re-run `pnpm build`/`pnpm lint` |
| Both apps drifted on `components.json` answers | MEDIUM | Re-run `init --force` on the later app using the earlier app's resolved answers, or hand-edit `components.json` to match field-by-field, then re-diff `globals.css` tokens |
| Two Radix dependency trees left unreconciled past milestone close | MEDIUM | Run `shadcn migrate radix` retroactively across all remaining scoped-package files, then prune `package.json`/lockfile in a dedicated cleanup phase |
| Mobile tab/table regression shipped to production | MEDIUM–HIGH (production legal-practice app, real users) | Revert the specific module's markup change (dual-view/tabs are isolated per-module, not shared infrastructure, so a targeted revert is low-blast-radius), re-test at all RBAC roles + mobile before re-shipping |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|-------------------|---------------|
| 1. `init` corrupts theme via duplicate CSS blocks | Foundation (shadcn CLI init) | `git diff globals.css` shows exactly one `:root`/`.dark` block each; visual diff against Figma light+dark |
| 2. `add --overwrite` drops custom variants/props | Foundation (protocol) + every per-module phase (execution) | `shadcn diff <component>` reviewed before each overwrite; grep of all call sites for removed variants/props; `pnpm build` passes |
| 3. Radix package identity split (unified vs. scoped) | Foundation | `package.json` contains either all-unified or a documented, time-boxed bridge state; no untracked mix at milestone close |
| 4. Tailwind v4 config misunderstanding (stale `tailwind.config.js` mental model) | Foundation | No `tailwind.config.js`/`.ts` file created; `components.json` `tailwind.config` is `""` in both apps |
| 5. `web/`/`webpage/` `components.json` drift | Foundation | Field-by-field diff of both `components.json` files matches |
| 6. 7-tab pattern loses mobile overflow on `Tabs` migration | Per-module (Clientes ficha; Processos ficha follows same pattern) | Manual test at 375px width across ADVOGADO/TECNICO/ASSISTENTE/ADMIN roles |
| 7. Dual-view list pattern collapsed into single table | Foundation (rule stated once) + every per-module list phase (execution) | `md:hidden` card branch untouched in diff; mobile viewport test still shows cards, not a scrolling table |
| 8. Dialog/Sheet swap loses project-specific props, not Radix a11y | Foundation (rule: diff-first, don't re-verify Radix internals) + per-module phases touching Dialog/Sheet/AlertDialog | `sr-only` close labels present; `shadcn diff dialog`/`sheet` reviewed; genuinely new components (Command/Calendar/NavigationMenu/Tabs/Select/DropdownMenu/Tooltip/Form/Checkbox/Avatar/Accordion) get a first-time keyboard/ARIA smoke test since no existing baseline exists |

## Sources

- [shadcn/ui — Tailwind v4](https://ui.shadcn.com/docs/tailwind-v4) — CSS-first `@theme`/`@theme inline` config, HIGH confidence (official docs)
- [shadcn/ui — Monorepo](https://ui.shadcn.com/docs/monorepo) — per-workspace `components.json`, style/baseColor/iconLibrary consistency warning, empty `tailwind.config` for v4, HIGH confidence (official docs)
- [shadcn/ui — CLI](https://ui.shadcn.com/docs/cli) — `init`/`add` flags (`-y`, `-o/--overwrite`), HIGH confidence (official docs)
- [shadcn/ui — Changelog: Unified Radix UI Package (Feb 2026)](https://ui.shadcn.com/docs/changelog/2026-02-radix-ui) — `radix-ui` unified import for `new-york` style, `migrate radix` command, HIGH confidence (official changelog, very recent — post-training-data)
- [shadcn/ui — Changelog: CLI v4 (Mar 2026)](https://ui.shadcn.com/docs/changelog/2026-03-cli-v4) — `--dry-run`/`--diff`/`--view` flags, `shadcn info`/`shadcn docs` commands, `init --monorepo`, presets, HIGH confidence (official changelog, very recent)
- [GitHub shadcn-ui/ui Issue #2791 — "init command overwrites global.css"](https://github.com/shadcn-ui/ui/issues/2791) — MEDIUM confidence (community-reported, closed as stale, no maintainer fix, but consistent with docs' described append behavior)
- [GitHub shadcn-ui/ui Issue #4845 — "globals.css contains default NextJS CSS variables that interfere with the theme"](https://github.com/shadcn-ui/ui/issues/4845) — MEDIUM confidence (community-reported, cross-checked, consistent failure mode with #2791)
- [GitHub shadcn-ui/ui Issue #931, Discussion #7739, Issues #7672/#7794 — overwrite/prompt/flag behavior](https://github.com/shadcn-ui/ui/issues/931) — MEDIUM confidence (community-reported CLI behavior/bug reports across versions)
- [Easton — "shadcn/ui and Radix: How to Maintain Accessibility When Customizing Components"](https://eastondev.com/blog/en/posts/dev/20260330-shadcn-radix-accessibility/) — MEDIUM confidence (single source, directionally consistent with Radix's documented ARIA/keyboard behavior)
- Direct source inspection (HIGH confidence, primary evidence): `web/package.json`, `webpage/package.json`, `web/src/app/globals.css`, `webpage/src/app/globals.css`, `web/src/components/ui/{button,dialog,badge,card,toast,input}.tsx`, `web/tsconfig.json`, `webpage/tsconfig.json`, `web/src/lib/utils.ts`, `webpage/src/lib/utils.ts`, `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `.planning/PROJECT.md`

---
*Pitfalls research for: shadcn/ui CLI retrofit onto existing hand-rolled component library (LexCV v2.13)*
*Researched: 2026-07-15*
