# Stack Research: shadcn/ui CLI Adoption

**Domain:** Formal shadcn/ui CLI adoption in an existing two-app Next.js 16 / Tailwind v4 codebase (LexCV `web/` + `webpage/`)
**Researched:** 2026-07-15
**Confidence:** HIGH (verified against ui.shadcn.com official docs/changelogs, npm registry, and GitHub issues — this area changed significantly in the last 8 months and training data alone would have been wrong on several points below)

## Critical timing note

**shadcn's CLI defaults changed *this month* (July 2026).** As of the `2026-07-base-ui-default` changelog, `pnpm dlx shadcn@latest init` now defaults new projects to **Base UI** primitives instead of Radix. This repo already has 9 `@radix-ui/react-*` runtime dependencies and every hand-rolled `web/src/components/ui/*` primitive is built on Radix's `asChild`/`Slot` composition pattern (verified in `button.tsx`). Running plain `init` today would silently start scaffolding Base UI components (different composition API — render props, no `asChild`, no `data-[state=...]` selectors) next to the existing Radix ones. **This is the single highest-risk gotcha for this milestone** and must be called out explicitly, not left to CLI defaults.

## Recommended Stack

### Core Technologies

| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| `shadcn` (CLI package) | `4.13.0` (current on npm, `@latest` tag) | Official scaffolding CLI (`init`/`add`/`diff`) | This is the actual product being adopted this milestone. Note: the CLI package is published as `shadcn`, **not** `shadcn-ui` (that name is deprecated/legacy, matches project's stated exclusion of unverified "skills" tooling) |
| `-b radix` flag on `init` | n/a | Forces Radix-based primitives instead of the new Base UI default | Matches 9 existing `@radix-ui/react-*` deps and every hand-rolled component's `asChild` pattern already in use across ~15 modules. Radix is explicitly stated as "not being deprecated" — every future shadcn component ships for both libraries |
| Tailwind CSS v4 | `^4` (already installed) | CSS-first theming, no `tailwind.config.ts` | Already in place in both apps via `@tailwindcss/postcss`; shadcn's `init` fully supports v4 today and, per `components.json` schema, expects `tailwind.config` to be **left blank** for v4 projects |
| `tw-animate-css` | `1.4.0` | CSS-first replacement for the `tailwindcss-animate` plugin | Official shadcn docs: "shadcn/ui has deprecated `tailwindcss-animate` in favor of `tw-animate-css`... new projects have `tw-animate-css` installed by default." Pure `@import`, no JS plugin loader needed — fits Tailwind v4's CSS-first model exactly |

### Supporting Libraries (component-by-component, for the 15 missing primitives)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `@radix-ui/react-select` | `2.3.3` | Select primitive | `shadcn add select` |
| `@radix-ui/react-tabs` | `1.1.17` | Tabs primitive | `shadcn add tabs` — note: project has a deliberate decision to keep the client-detail page on hand-rolled toggle buttons, not `Tabs` (see PROJECT.md Key Decisions); only add where a *new* tabbed UI is genuinely wanted |
| `@radix-ui/react-dropdown-menu` | `2.1.20` | DropdownMenu primitive | `shadcn add dropdown-menu` |
| `@radix-ui/react-tooltip` | `1.2.12` | Tooltip primitive | `shadcn add tooltip` |
| `@radix-ui/react-checkbox` | `1.3.7` | Checkbox primitive | `shadcn add checkbox` |
| `@radix-ui/react-avatar` | `1.2.2` | Avatar primitive | `shadcn add avatar` |
| `@radix-ui/react-separator` | `1.1.11` | Separator primitive | `shadcn add separator` |
| `@radix-ui/react-progress` | `1.1.12` | Progress primitive | `shadcn add progress` |
| `@radix-ui/react-accordion` | `1.2.16` | Accordion primitive | `shadcn add accordion` |
| `@radix-ui/react-navigation-menu` | `1.2.18` | NavigationMenu primitive | `shadcn add navigation-menu` — likely only needed in `webpage/` (landing nav), `web/` already has a bespoke sidebar/topbar (explicitly preserved, out of scope for redesign) |
| `cmdk` | `1.1.1` | Command palette primitive (no Radix equivalent) | `shadcn add command` |
| `react-day-picker` | **pin to `9.14.0`, NOT `@latest`** | Calendar primitive | See "What NOT to Use" below — the registry currently requests `@latest`, which resolves to a broken v10 |
| `date-fns` | `4.4.0` | Date formatting for Calendar | Installed automatically alongside `react-day-picker` by `shadcn add calendar` |
| Skeleton, Breadcrumb | n/a (no new dependency) | Pure Tailwind/`Slot`-based components | Both ship as plain `.tsx` files with zero extra npm packages — `shadcn add skeleton breadcrumb` just drops files using deps you already have (`@radix-ui/react-slot`, `cva`, `cn`) |
| Form (react-hook-form integration) | n/a (no *new* dependency — you already have `react-hook-form ^7.62.0`, `@hookform/resolvers ^5.2.2`, `zod ^4.1.5`) | Accessible field wiring around your existing RHF+Zod stack | `shadcn add form` — note current shadcn docs describe **zod v3** in their Form examples (`docs/forms/react-hook-form`); this repo is on `zod ^4.1.5`. Verify the generated `zodResolver` call compiles against Zod 4's API before wiring it into real forms (Zod 4 changed some error-map internals) |

### Development Tools

| Tool | Purpose | Notes |
|------|---------|-------|
| `pnpm dlx shadcn@latest init -b radix` | One-time formal adoption per app | Run once in `web/`, once in `webpage/` (two independent pnpm projects — see Monorepo Decision below). Non-interactive equivalent: add `-y` (accept defaults) or answer prompts explicitly for style/base-color |
| `pnpm dlx shadcn@latest add <component> --diff` | Preview before touching existing files | Use this **every time** before adding a component whose name collides with an existing hand-rolled file (button, card, dialog, alert-dialog, input, label, popover, radio-group, sheet, switch, table, textarea, toast) |
| `pnpm dlx shadcn@latest add <component> --dry-run` | Preview file list/deps without writing anything | Cheaper first pass than `--diff` when you just want to see what would be touched |

## Installation

```bash
# In web/ (existing 14 hand-rolled primitives + Radix deps already present)
cd web
pnpm dlx shadcn@latest init -b radix

# In webpage/ (only button.tsx/card.tsx hand-rolled so far)
cd ../webpage
pnpm dlx shadcn@latest init -b radix

# Then, per app, add only the NEW primitives (do not touch existing hand-rolled files yet — see Migration Risk)
pnpm dlx shadcn@latest add select tabs dropdown-menu command tooltip checkbox avatar separator progress accordion breadcrumb skeleton form

# Calendar needs a version pin BEFORE/AFTER add (registry currently requests react-day-picker@latest, which is broken — see below)
pnpm dlx shadcn@latest add calendar
pnpm add react-day-picker@9.14.0   # re-pin immediately after, per-app

# Animation plugin swap (do in both apps' package.json + globals.css)
pnpm remove tailwindcss-animate
pnpm add tw-animate-css
# globals.css: replace `@plugin "tailwindcss-animate";` with `@import "tw-animate-css";`

# webpage/ only — will pull in the Radix deps web/ already has, on first add
pnpm dlx shadcn@latest add button card   # --diff first, since hand-rolled versions already exist
```

## Alternatives Considered

| Recommended | Alternative | When to Use Alternative |
|-------------|-------------|--------------------------|
| `init -b radix` | `init` (Base UI, new July-2026 default) | Only for a brand-new app with zero existing Radix investment. Migrating this repo's 15 modules to Base UI's render-prop composition (no `asChild`, `checked` strict-boolean Checkbox, array-only `ToggleGroup` value, Floating UI instead of Radix's `data-[state]` selectors) is a large, unrequested architectural change — explicitly not what this milestone asked for |
| Per-app `components.json` (one in `web/`, one in `webpage/`) | Shared internal `packages/ui` workspace package (shadcn's official monorepo pattern, ui.shadcn.com/docs/monorepo) | Only if the repo is *actually* restructured into a real pnpm workspace first (root `pnpm-workspace.yaml` + `apps/`/`packages/` layout). Verified: this repo has **no root `pnpm-workspace.yaml` and no root `package.json`** — `web/` and `webpage/` are two fully independent pnpm projects (separate lockfiles, `webpage/pnpm-workspace.yaml` is scoped only to itself for an unrelated supply-chain reason) that happen to live in one git repo, not a technical monorepo. Retrofitting real workspace tooling is a legitimate future option (would remove component/token duplication permanently) but is a separate, larger infrastructure decision outside "formally adopt the CLI" |
| `pnpm dlx shadcn@latest` (official CLI, run per-app) | `pnpm dlx skills add shadcn/ui` (shadcn's own new skill-based migration/adoption tool, mentioned in the 2026-07 changelog) | Never for this milestone — PROJECT.md explicitly excludes "instalação de skills/pacotes externos não verificados" as an out-of-scope decision. Do not use it even though shadcn's own docs now suggest it |
| `--diff` review before `add` | Blind `add --overwrite` | Never on the 14 already-hand-rolled `web/src/components/ui/*` files without reading the diff first — see Migration Risk below |

## What NOT to Use

| Avoid | Why | Use Instead |
|-------|-----|--------------|
| Plain `pnpm dlx shadcn@latest init` (no `-b` flag) | Defaults to Base UI as of the 2026-07 changelog — would scaffold a second, incompatible primitive library alongside your 9 existing `@radix-ui/*` packages | `init -b radix` |
| `tailwindcss-animate` (currently installed, loaded via `@plugin "tailwindcss-animate"` in `globals.css`) | Officially deprecated by shadcn in favor of a CSS-first package; it still technically works today (Tailwind v4's `@plugin` directive can load legacy JS-config plugins), but every new shadcn component generated from here on assumes `tw-animate-css`'s animation utility names/keyframes are present | `tw-animate-css` via `@import "tw-animate-css";` in `globals.css` |
| `react-day-picker@latest` (what the shadcn `calendar` registry item currently pins to `@latest`) | v10.0.0 was released with breaking changes to the `classNames`/`table` type shape; confirmed **open, unresolved GitHub issue** shadcn-ui/ui#10914 ("calendar component build failure with react-day-picker v10+"), opened June 2026 | Pin to `react-day-picker@9.14.0` (last stable v9.x) immediately after running `shadcn add calendar`, in both `web/` and `webpage/` if either needs it |
| `pnpm dlx skills add shadcn/ui` | Explicitly out of scope per this milestone's PROJECT.md ("Fora de âmbito... instalação de skills/pacotes externos não verificados") — this is shadcn's own new AI-agent migration skill, not the CLI itself | `pnpm dlx shadcn@latest init` / `add` only |
| Running `add button card dialog alert-dialog input label popover radio-group sheet switch table textarea toast` with `--overwrite` as a first step | These 14 files already carry custom styling/props battle-tested across ~15 modules (e.g. `button.tsx`'s hardcoded `neutral-900`/`neutral-50` palette, not shadcn's semantic `bg-primary` tokens). A silent overwrite would revert real, in-production visual identity that PROJECT.md explicitly says must be preserved | Run `add <name> --diff` first (or `--dry-run`), review manually, and hand-merge only what's genuinely missing (e.g. new variant, better a11y attribute) — treat the CLI output as a reference diff, not a source of truth to blindly apply |
| Assuming `shadcn diff <component>` exists as a standalone top-level command (older docs/training data pattern) | Confirmed: in the current CLI (v4.x), diffing is a flag on `add` (`add <component> --diff`), not a separate top-level `diff` subcommand | `shadcn add <component> --diff` |

## Stack Patterns by Variant

**If adding a component that already exists hand-rolled (button, card, dialog, alert-dialog, input, label, popover, radio-group, sheet, switch, table, textarea, toast):**
- Run `add <name> --diff` first, read the output
- The CLI's own non-interactive behavior: files with **identical** content are auto-skipped ("Skipped N files... use --overwrite to overwrite"); files that **differ** prompt "The file xxx already exists. Would you like to overwrite?" unless `-o/--overwrite` (force yes) or a skip flag is passed
- Because your hand-rolled files intentionally deviate from upstream (custom color literals, `sheet.tsx` built manually per your own Key Decisions log — "CLI `npx shadcn` exige setup interativo; seguiu padrão de dialog.tsx"), treat every prompt as "no" by default and hand-port only specific missing pieces (e.g., a new size variant) into the existing file

**If adding a genuinely new component (Select, Tabs, DropdownMenu, Command, Tooltip, Form, Checkbox, Avatar, Separator, Skeleton, Progress, Calendar, Breadcrumb, Accordion, NavigationMenu):**
- Safe to `add` directly, no existing file to collide with
- Immediately re-pin `react-day-picker` after `add calendar` (see above)
- For `add form`, double check the generated code's Zod usage against your `zod ^4.1.5` (docs currently describe zod v3 patterns)

**If working in `webpage/` (only `button.tsx`/`card.tsx` hand-rolled so far):**
- Same `-b radix` init is still correct — `webpage/` already depends on `@radix-ui/react-slot`, `class-variance-authority`, `tailwindcss-animate`, matching `web/`'s conventions, just fewer components deep
- Both apps' `tsconfig.json` already use `@/*` → `./src/*` and both have `src/components/ui/*` + `src/lib/utils.ts` in the same relative locations — the shadcn Next.js default aliases (see below) will resolve correctly in **both** apps with zero remapping
- `webpage`'s own `pnpm-workspace.yaml` (added in Phase 100 for a `minimumReleaseAgeExclude` scoping reason) is **not** a shadcn monorepo workspace — it's an unrelated pnpm supply-chain config local to that single package. Don't conflate the two when deciding aliasing

**If later deciding to de-duplicate `web/` and `webpage/` component trees:**
- That requires first creating a real root `pnpm-workspace.yaml` + moving both apps under e.g. `apps/`, then following ui.shadcn.com/docs/monorepo's `packages/ui` pattern with matching `style`/`iconLibrary`/`baseColor` in both `components.json` files
- Treat as a separate future milestone, not part of "formally adopt the CLI"

## `components.json` — expected shape for this repo (per app)

Because Tailwind v4 is already in place (no `tailwind.config.ts` in either app) and both apps' `tsconfig.json` already define `"@/*": ["./src/*"]`, the CLI-generated file should look like this in **both** `web/` and `webpage/` — no alias customization needed to match existing conventions:

```json
{
  "$schema": "https://ui.shadcn.com/schema.json",
  "style": "new-york",
  "rsc": true,
  "tsx": true,
  "tailwind": {
    "config": "",
    "css": "src/app/globals.css",
    "baseColor": "neutral",
    "cssVariables": true
  },
  "aliases": {
    "components": "@/components",
    "utils": "@/lib/utils",
    "ui": "@/components/ui",
    "lib": "@/lib",
    "hooks": "@/hooks"
  },
  "iconLibrary": "lucide"
}
```

Notes:
- `"style": "new-york"` is the correct choice to visually match the existing hand-rolled `button.tsx` (rounded-md, `h-9`/`px-4`, `gap-2`, focus-visible ring) — this legacy value still resolves internally to `radix-vega` (see Version Compatibility)
- `"baseColor": "neutral"` matches the literal `neutral-900`/`neutral-50`/`neutral-100` classes already hardcoded throughout `button.tsx` and the other 13 primitives
- `"cssVariables": true` is the shadcn default and is **recommended**, but flag this as an integration item: today's `globals.css` only defines `--background`/`--foreground` (mapped via `@theme inline`), not the full shadcn semantic token set (`--primary`, `--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, `--radius`, etc.). `init` will add the missing tokens to `globals.css`; the *existing* 14 hand-rolled components will keep using their hardcoded `neutral-*` literals until each is deliberately migrated to the new semantic classes during the module-by-module visual audit — the two systems can coexist visually (since `neutral` base color ≈ the same literal palette) but should not be treated as already unified
- `webpage/` has no `hooks/` directory yet — that's fine, the CLI only creates the folder when a component that needs it (e.g., a future `use-mobile` hook) is actually added

## Version Compatibility

| Package A | Compatible With | Notes |
|-----------|------------------|-------|
| `shadcn@4.13.0` (CLI) | Tailwind v4, Next.js 16, React 19 | Confirmed current; CLI auto-detects Tailwind v4 and leaves `tailwind.config` blank in generated `components.json` |
| `components.json` `tailwind.config` field | Tailwind v4 projects | Per official schema/docs: **leave blank** for v4 (no `tailwind.config.ts` needed — this repo already has none, only `@tailwindcss/postcss` + CSS-first `globals.css`, which is exactly what the CLI expects) |
| `components.json` `style` field | `"new-york"` (legacy) resolves to `"radix-vega"` internally | The style taxonomy changed to `{library}-{style}` (e.g. `radix-vega`, `base-vega`, `radix-nova`...) as of the Dec-2025 `shadcn create` release; old `new-york`/`default`/`new-york-v4` values still resolve correctly for backward compat, so explicitly requesting `new-york` during `init` is fine |
| `react-day-picker@10.x` | shadcn `calendar` registry item | **Currently broken** (open issue #10914) — do not let `pnpm add` resolve to `^10` for this package until upstream fixes are confirmed |
| `zod@4.1.5` (already installed) | shadcn's `Form` component docs | Docs currently show `zod` v3 idioms; test the generated `zodResolver`/schema wiring against v4 before shipping a real form |
| `tw-animate-css@1.4.0` | Tailwind v4 `@import` (no plugin config) | Drop-in for `@plugin "tailwindcss-animate"` — same animate-in/out class vocabulary (`accordion-down`, etc.), pure CSS, no JS plugin system dependency |
| Existing `tsconfig.json` `@/*` → `./src/*` in both apps | shadcn Next.js App Router defaults | Already an exact match — no alias remapping needed. Default `components.json` aliases (`@/components`, `@/lib/utils`, `@/components/ui`, `@/lib`, `@/hooks`) will resolve correctly against both apps' existing folder layout with zero changes |

## Sources

- https://ui.shadcn.com/docs/tailwind-v4 — Tailwind v4 init behavior, `@theme inline` pattern, migration notes (HIGH confidence, official docs)
- https://ui.shadcn.com/docs/monorepo — official monorepo guidance, `packages/ui` pattern, per-workspace `components.json` requirement (HIGH confidence, official docs)
- https://ui.shadcn.com/docs/components-json — full field reference for `components.json` (HIGH confidence, official docs)
- https://ui.shadcn.com/docs/cli — CLI flags for `init`/`add`, `--overwrite`/`--diff`/`--dry-run` behavior (HIGH confidence, official docs)
- https://ui.shadcn.com/schema.json — raw JSON Schema for `components.json`, confirms `style` enum values (`default`, `new-york`, `radix-vega`...`base-rhea`) (HIGH confidence, primary source)
- https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default — Base UI becoming default, `-b radix` opt-out flag, "no migration required" confirmation (HIGH confidence, official changelog, dated this month)
- https://ui.shadcn.com/docs/changelog/2025-12-shadcn-create — style-preset taxonomy (Vega/Nova/Maia/Lyra/Mira) introduction (MEDIUM-HIGH, official changelog + corroborated by WebSearch)
- https://github.com/shadcn-ui/ui/discussions/9562 — Radix→Base UI migration guide, breaking-change inventory (MEDIUM, community discussion, cross-checked against official changelog's "no forced migration" stance)
- https://ui.shadcn.com/docs/forms/react-hook-form — Form + RHF + Zod integration pattern (MEDIUM, docs show zod v3 idiom, flagged as a compatibility check item against this repo's zod v4)
- https://ui.shadcn.com/docs/components/command — `cmdk` dependency confirmation (MEDIUM, version not stated on page, cross-checked via npm)
- https://ui.shadcn.com/r/styles/new-york/calendar.json — registry manifest confirming `react-day-picker@latest` + `date-fns` deps and `button` registry dependency (HIGH confidence, primary source)
- npm registry (`npm view <pkg> version`) — current versions for all `@radix-ui/react-*`, `cmdk`, `react-day-picker`, `date-fns`, `tailwindcss-animate`, `tw-animate-css`, `shadcn` (HIGH confidence, primary source, checked live)
- https://github.com/shadcn-ui/ui/issues/10914 — open bug, `react-day-picker@10.0.1` build failure in Calendar component (HIGH confidence for "issue exists and is open", primary source; MEDIUM confidence on exact resolution status since not confirmed closed)
- https://github.com/shadcn-ui/ui/discussions/7739 — `add` command overwrite/skip/prompt behavior details (MEDIUM, community discussion corroborating official `--overwrite`/`-y` flag docs)
- Repo inspection: `web/package.json`, `webpage/package.json`, `web/src/app/globals.css`, `web/src/components/ui/button.tsx`, `web/src/lib/utils.ts`, `web/tsconfig.json`, `webpage/tsconfig.json`, `webpage/src/lib/utils.ts`, absence of root `pnpm-workspace.yaml`/`package.json`, presence of independent `web/pnpm-lock.yaml` + `webpage/pnpm-lock.yaml` (HIGH confidence, direct file reads)

---
*Stack research for: shadcn/ui CLI formal adoption, LexCV v2.13 milestone*
*Researched: 2026-07-15*
