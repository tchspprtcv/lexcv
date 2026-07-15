# Architecture Research

**Domain:** shadcn/ui CLI integration into an existing two-app Next.js 16 (App Router, Tailwind v4) monorepo-style repo
**Researched:** 2026-07-15
**Confidence:** HIGH for CLI mechanics (verified against current shadcn/ui CLI source via Context7, 2026-07-15 snapshot) / HIGH for repo facts (read directly from working tree) / MEDIUM for exact CLI flag defaults at execution time (upstream CLI evolves; re-verify with `npx shadcn@latest init --help` when the Foundation phase actually runs)

## Correction to Milestone Framing (load-bearing for Q2)

The milestone context states `web/` and `webpage/` are "both pnpm workspace members." **This is not what the repository currently contains.** Verified directly:

- No root `package.json` and no root `pnpm-workspace.yaml` exist anywhere in the repo root.
- `web/pnpm-lock.yaml` (192KB) and `webpage/pnpm-lock.yaml` (140KB) are two **independent** lockfiles — not a single workspace lockfile.
- `webpage/pnpm-workspace.yaml` exists, but it is a **single-package** workspace file used only to scope a pnpm 11 supply-chain guard (`minimumReleaseAgeExclude: [electron-to-chromium]`, per the Phase 100 decision log) — it is not evidence of a multi-package monorepo.
- `.github/workflows/deploy.yml` builds three fully separate Docker contexts: `context: ./backend`, `context: ./web`, `context: ./webpage`. Each app's `Dockerfile` does `COPY package.json pnpm-lock.yaml ./` (and, for webpage, its own single-package `pnpm-workspace.yaml`) and installs independently. Neither Dockerfile's build context can see files outside its own app directory.

**Conclusion:** `web/` and `webpage/` are two fully standalone Next.js apps that happen to live in the same git repo, not pnpm workspace members today. This directly changes the cost/benefit of a shared `packages/ui` package (see Integration Points below) — it is not a config tweak, it is a new structural investment.

## Standard Architecture

### System Overview (target end-state for this milestone)

```
┌───────────────────────────────────────────────────────────────────────────┐
│  repo root (no shared workspace today — each app self-contained)          │
├───────────────────────────────┬───────────────────────────────────────────┤
│  web/ (dashboard app)          │  webpage/ (public landing app)             │
│  ├─ components.json  (NEW)    │  ├─ components.json  (NEW)                 │
│  ├─ src/app/globals.css       │  ├─ src/app/globals.css                    │
│  │   (Tailwind v4 @theme,     │  │   (Tailwind v4 @theme, byte-identical   │
│  │    tokens EXTENDED here)   │  │    today — extend in lockstep)          │
│  ├─ src/lib/utils.ts (cn())   │  ├─ src/lib/utils.ts (cn()) — already      │
│  │   already canonical        │  │   canonical, untouched                  │
│  ├─ src/components/ui/*.tsx   │  ├─ src/components/ui/*.tsx                │
│  │   14 existing + ~15 new    │  │   2 existing (button, card) — CLI-      │
│  │   CLI-scaffolded           │  │   regenerate or leave, no new needs     │
│  └─ src/components/shared/*   │  └─ src/components/* (marketing sections)  │
├───────────────────────────────┴───────────────────────────────────────────┤
│  CI/CD: .github/workflows/deploy.yml — 3 independent build-push-action    │
│  blocks (context: ./web, ./webpage). UNCHANGED by this milestone if the   │
│  "two components.json, no shared package" path is taken (recommended).    │
└───────────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Current State (verified) |
|-----------|-----------------|---------------------------|
| `web/src/app/globals.css` | Tailwind v4 CSS-first theme config (`@theme inline`, `:root`/`.dark` variables) | Only defines `--background`/`--foreground` + font vars. No `--primary`/`--secondary`/`--muted`/`--accent`/`--destructive`/`--border`/`--input`/`--ring`/`--card`/`--popover`/`--radius` — the full shadcn semantic token set is **absent**, not just unthemed. |
| `web/src/lib/utils.ts` | `cn()` helper (`clsx` + `tailwind-merge`) | Byte-for-byte matches canonical shadcn CLI output already. Zero risk on `init`. |
| `web/src/components/ui/*.tsx` | Hand-rolled Radix-based primitives (14 files: alert-dialog, badge, button, card, dialog, input, label, popover, radio-group, sheet, switch, table, textarea, toast, toaster) | All built on `@radix-ui/react-*` (already installed), use `data-slot` attributes and CVA — structurally matches the **current** (data-slot era) shadcn source, not the older forwardRef era. Colors are hardcoded Tailwind palette utilities (`neutral-900`, `slate-950`, `blue-600`) instead of semantic tokens, because those tokens don't exist yet. |
| `web/src/components/shared/dashboard-shell.tsx` | App shell: sidebar, topbar, mobile drawer (`Sheet`), bottom-nav | Hardcodes `bg-slate-950`, `text-blue-400`, `bg-blue-600/10` etc. directly for the "Anti-Safe Harbor" identity — none of this is token-driven today. This is the biggest visual-identity-preservation risk surface, not `globals.css`. |
| `webpage/src/app/globals.css`, `webpage/src/lib/utils.ts`, `webpage/src/components/ui/{button,card}.tsx` | Same Tailwind v4 CSS-first setup, same `cn()`, and a **byte-identical** `button.tsx` to `web/`'s | Confirms both apps were hand-authored against the same (uninitialized) shadcn conventions from the start — no drift yet, cheap to keep in sync manually. |
| `.github/workflows/deploy.yml` | CI: test → build-and-push 3 independent Docker images | `context: ./web`/`./webpage`, no shared context. A shared `packages/ui` would require changing this. |

## Recommended Project Structure

### Q2 — Two `components.json` (recommended) vs. shared `packages/ui` (rejected for this milestone)

**Decision: keep two independent `components.json` + `ui/` folders, one per app. Do not introduce `packages/ui` in this milestone.**

This matches what PROJECT.md's target features literally say: *"inicializar shadcn CLI oficialmente (`components.json`) em `web/` e `webpage/`"* — plural config files, not a shared package. Concrete rationale:

| | Two `components.json` (recommended) | Shared `packages/ui` (shadcn's official monorepo pattern) |
|---|---|---|
| **Prerequisite work** | None — each app already has its own `package.json`/lockfile/tsconfig with `@/*` → `./src/*` aliases already matching shadcn's default alias shape | Must first create root `package.json` (with `"workspaces"`/pnpm equivalent) + root `pnpm-workspace.yaml` (`packages: [web, webpage, packages/*]`), **merge** `web/pnpm-lock.yaml` + `webpage/pnpm-lock.yaml` into one root lockfile, add `packages/ui/{package.json, components.json, src/components, src/lib/utils.ts, src/styles/globals.css}` |
| **CI/Docker impact** | Zero. `context: ./web` / `context: ./webpage` in `deploy.yml` keep working unmodified | Both Dockerfiles must change build `context` from `./web`/`./webpage` to repo root (`context: .`, add `dockerfile: web/Dockerfile`) so `COPY . .` can see `../packages/ui`; both `deps` stages must `COPY` the root `pnpm-workspace.yaml` and run a workspace-aware install; both apps' `next.config.ts` likely need `transpilePackages: ["@workspace/ui"]`. This touches the exact pipeline that Phase 100 just finished hardening (3 Caddy config sources, Multi-Zones `assetPrefix`) — high blast radius for a milestone explicitly scoped as "not a redesign." |
| **Component alias plumbing** | Default shadcn aliases work as-is: `"ui": "@/components/ui"`, `"utils": "@/lib/utils"` — no change to `tsconfig.json` paths | Requires cross-package aliases (`"ui": "@workspace/ui/components"`, `"utils": "@workspace/ui/lib/utils"`) plus package.json `imports`/`exports` maps in the new `packages/ui`, per shadcn's own documented monorepo `components.json` shape |
| **Duplication cost today** | Low: `webpage/` currently has only 2 UI files (`button.tsx`, `card.tsx`), and `button.tsx` is already byte-identical to `web/`'s. `webpage/` needs almost none of the 15 new primitives targeted for `web/` (Select/Tabs/DropdownMenu/Command/Form/Table-heavy modules don't exist on a static marketing page) | N/A — this is the whole point of a shared package, but the two apps' actual current+planned component needs barely overlap, so the sharing benefit is small |
| **Sync mechanism** | Manual: when a primitive changes in both apps (rare — only Button/Card apply to both), re-run `npx shadcn add <name> --overwrite` in the second app, or hand-copy the file | Automatic via `workspace:*` dependency — but only pays off once 3+ apps or heavy component churn exists |
| **Reversibility** | Fully reversible; each `components.json` is app-local, safe to add/remove independently | Harder to reverse once lockfiles are merged and Dockerfiles rewritten |

**When to revisit:** if a third internal app is added, or if `webpage/` starts needing the same heavy primitive set as `web/` (Select, Tabs, Form, Table), promote to `packages/ui` in a dedicated future milestone — not as a side effect of this one.

### Recommended file layout after Foundation phase

```
web/
├── components.json              # NEW — style: nova (or new-york-v4 legacy, see Patterns), base: radix
├── src/
│   ├── app/globals.css          # MODIFIED — additive tokens merged in by `shadcn init`
│   ├── lib/utils.ts             # UNCHANGED (already canonical)
│   └── components/
│       ├── ui/                  # 14 existing files UNCHANGED (unless explicit CLI re-add) +
│       │                        #   ~15 NEW: select, tabs, dropdown-menu, command, tooltip,
│       │                        #   form, checkbox, avatar, separator, skeleton, progress,
│       │                        #   calendar, breadcrumb, accordion, navigation-menu
│       └── shared/               # UNCHANGED by Foundation; touched later, per-module, to swap
│                                 #   hardcoded slate-*/blue-* utilities for semantic tokens
│                                 #   ONLY where a module phase explicitly does so
webpage/
├── components.json              # NEW — same base/style choice as web/ for visual consistency
├── src/
│   ├── app/globals.css          # MODIFIED in lockstep with web/'s token additions
│   └── components/ui/
│       ├── button.tsx           # OPTION: re-add via CLI (`shadcn add button --overwrite`) —
│       │                        #   safe, since it's already near-identical to canonical output
│       └── card.tsx             # same treatment
```

### Structure Rationale

- **`components.json` per app, not shared:** matches the literal target feature text in PROJECT.md and avoids restructuring a CI/Docker pipeline that was only just stabilized in the immediately-prior milestone (v2.12, Phase 100).
- **`globals.css` changes stay additive:** the CLI's CSS updater (`update-css.ts`) merges at the declaration level via a PostCSS AST — it replaces individual `--variable: value;` lines and `@apply` bodies, it does not delete or replace unrelated content. Only `--background` and `--foreground` will have their *values* touched (name collision with the CLI's baseColor palette); every other token it adds (`--primary`, `--card`, etc.) is net-new.
- **`components/shared/*` deliberately left out of Foundation:** these are the highest-hardcoded-color files (`dashboard-shell.tsx` especially) and are exactly where "preserve identity, not a redesign" risk concentrates. Token normalization there should be a deliberate, reviewed per-module change, not an automatic side effect of running `shadcn init`.

## Architectural Patterns

### Pattern 1: Tailwind v4 CSS-first token merge on `shadcn init`

**What:** Current shadcn CLI (verified against the CLI's own `get-project-info.ts` and `preflight-init.ts` via Context7) detects Tailwind v4 by an **empty** `tailwind.config` field in `components.json` and requires only a CSS file (no `tailwind.config.ts`) — exactly this repo's setup. It reads `web/src/app/globals.css`, confirms it has `@import "tailwindcss"`, and merges shadcn's token block into the existing `@theme inline` / `:root` / `.dark` rules using a PostCSS-based updater that **replaces matching declarations by name, does not truncate/overwrite the file.**

**When to use:** Run once per app, at the very start of the Foundation phase, before any module work.

**Trade-offs:** Safe and additive for all-new tokens. The two pre-existing tokens (`--background`, `--foreground`) WILL have their light/dark hex values overwritten by the CLI's chosen `baseColor` (default `"neutral"`, oklch-based) unless immediately restored post-init. This is a 4-line diff (2 values × light/dark), trivially caught in `git diff` right after running `init` — not a real risk if reviewed, but will silently regress the exact institutional colors (`#f8fafc`/`#020617`) if the init commit is not diffed carefully.

**Example (concrete restoration step after `init`):**
```css
/* After `shadcn init`, verify these two blocks still read exactly: */
:root {
  --background: #f8fafc;   /* restore if CLI replaced with oklch neutral */
  --foreground: #020617;
}
.dark {
  --background: #020617;
  --foreground: #f8fafc;
}
```

### Pattern 2: Explicit `--base radix` on init (critical, non-obvious)

**What:** The current shadcn CLI (v3.x line, confirmed via the CLI's `init.ts` option schema) supports **two component-primitive backends**: `radix` (`@radix-ui/react-*`, what this repo already uses in all 14 hand-rolled primitives) and `base` (the newer Base UI library). Critically, `-d/--defaults` and the CLI's own preset defaults resolve to `base: "base"` (Base UI) with the `"nova"`/`"base-nova"` preset — **not** Radix. `-y/--yes` alone does not force a base; it only skips confirmation prompts using whatever base is otherwise selected/defaulted.

**When to use:** Every `init` and every `add` invocation in this repo, for both apps.

**Trade-offs:** Get this wrong once and every newly-scaffolded component (Select, Tabs, DropdownMenu, etc.) will be built on a *different, incompatible* underlying primitives library than the 14 existing hand-rolled components — two parallel component ecosystems in one `ui/` folder, silent architectural drift, and wasted new dependencies (`@base-ui/react` alongside the already-installed `@radix-ui/react-*` packages) that don't interoperate.

**Example:**
```bash
# Correct — matches existing @radix-ui/react-* dependencies already in package.json
npx shadcn@latest init --base radix --yes

# Wrong — silently pulls in Base UI instead of Radix
npx shadcn@latest init --defaults
```

*(Confidence: HIGH on the mechanism per current CLI source read via Context7; MEDIUM on exact flag names remaining stable by execution time — re-verify `npx shadcn@latest init --help` output when the Foundation phase actually runs, since this CLI area is under active naming churn: e.g., "new-york"/"default" styles from the 2023-era CLI have already been superseded by the "nova"/"sera" + "base"/"radix" preset system this milestone will encounter.)*

### Pattern 3: `--radius` and `--primary` must be set deliberately, not left at preset defaults

**What:** The repo's own history (v1.1 Phase 10: *"design Anti-Safe Harbor (sharp edges, cores específicas)"*) already hardcodes `rounded-none` overrides in `dialog.tsx` and uses `blue-600`/`blue-500` ad hoc as the institutional accent color throughout `dashboard-shell.tsx` — but neither "sharp edges" nor "institutional blue" exist as a token today. The CLI's default preset (`nova`) ships a non-zero `--radius` (~0.625rem, rounded) and a neutral `baseColor` (no blue). Left untouched, every newly CLI-scaffolded component (Select, Tabs, etc.) will render with rounded corners and a neutral/gray active-state color that visually clashes with the rest of the already-sharp, blue-accented app.

**When to use:** Immediately after `init`, before adding any new primitive.

**Trade-offs:** A few extra minutes of manual token editing in Foundation avoids every subsequent module phase having to override radius/accent per-component ad hoc (which is exactly the inconsistency this milestone exists to remove).

**Example:**
```css
:root {
  --radius: 0rem;                 /* matches existing rounded-none identity */
  --primary: oklch(...)/#2563eb;  /* matches existing hardcoded blue-600 accent */
  --primary-foreground: #ffffff;
}
```

### Pattern 4: `sheet.tsx` is already CLI-shape-compatible — normalize, don't re-scaffold

**What:** Direct comparison of `web/src/components/ui/sheet.tsx` against the current shadcn registry source (fetched via Context7) shows the **same** component shape: `Sheet`/`SheetTrigger`/`SheetClose`/`SheetPortal`/`SheetOverlay`/`SheetContent`/`SheetHeader`/`SheetFooter`/`SheetTitle`/`SheetDescription`, all built on `@radix-ui/react-dialog` (current shadcn also builds `Sheet` on Radix Dialog, not `vaul`/Drawer — that's a separate `drawer.tsx` component this repo doesn't have and doesn't need). The only differences are cosmetic: hardcoded `bg-white`/`dark:bg-neutral-950`, `ring-neutral-950`, `text-neutral-500` instead of the semantic `bg-background`, `ring-ring`, `text-muted-foreground` — because those tokens didn't exist when it was written.

**When to use:** Leave `sheet.tsx` (and `dialog.tsx`, same situation) as-is structurally in the Foundation phase. Optionally fold a one-line-per-file cosmetic normalization (swap hardcoded `neutral-*`/`white` literals for the new semantic classes) into the same Foundation phase's token-consolidation work, since by then the tokens exist and the swap is a pure find-replace with no behavior change.

**Trade-offs:** Re-scaffolding via `shadcn add sheet --overwrite` adds no capability (already equivalent) and risks losing the app-specific `className` compositions already passed at call sites (e.g., `dashboard-shell.tsx`'s `<SheetContent side="left" className="w-[270px] p-0 bg-slate-950...">`) if the regenerated base variant structure shifts even slightly — those overrides are merged via `cn()` today regardless of base implementation, so keeping the hand-written file is strictly lower-risk than regenerating for zero gain.

**Note on the original decision rationale:** the logged reason ("CLI exige setup interativo") is checked against the current CLI and found **outdated as a blocker for future work** — `shadcn init`/`add` both support fully non-interactive execution today (`-y/--yes` defaults to `true`; `add <component> --overwrite` skips the overwrite prompt). This doesn't retroactively matter for `sheet.tsx` (already correct), but it does mean nothing blocks running `init` non-interactively now, which is exactly what the Foundation phase should do.

### Pattern 5: `table.tsx` ≠ `DataTable` — don't conflate the two in module phases

**What:** `web/src/components/ui/table.tsx` (used already in `clientes/[id]/page.tsx`, `processos/page.tsx`, `financeiro/page.tsx`, `documentos/page.tsx`, `pareceres/page.tsx`) is the plain semantic-HTML wrapper set shadcn ships (`Table`/`TableHeader`/`TableBody`/`TableRow`/`TableHead`/`TableCell`/`TableCaption`, all `data-slot`-tagged) — it matches canonical output exactly and needs no change. Shadcn's documented "Data Table" (sortable/filterable/paginated) is a separate **pattern**, not a CLI-added file — it requires adding `@tanstack/react-table` as a new dependency (not currently installed) and building a composition on top of the existing `Table` primitive.

**When to use:** Only introduce the `@tanstack/react-table` DataTable pattern if a module phase *explicitly* calls for client-side sort/filter/pagination beyond what the existing hand-rolled `useState` filters already provide (confirmed present in `clientes/page.tsx`, `processos/page.tsx` today).

**Trade-offs:** Treating "the lists need Table" as satisfied by the *existing* `table.tsx` (zero new work) is very different from "the lists need a DataTable" (new dependency + real component-architecture work) — conflating the two would silently expand scope in a milestone explicitly bounded as "not a redesign."

## Data Flow

### CLI scaffolding flow (Foundation phase)

```
npx shadcn@latest init --base radix --yes   (run in web/, then webpage/)
    ↓
writes web/components.json (style/base/aliases/cssVariables)
    ↓
merges tokens into web/src/app/globals.css (@theme inline + :root/.dark)
    ↓ (git diff review — restore --background/--foreground, set --radius/--primary)
npx shadcn add select tabs dropdown-menu command tooltip form checkbox
        avatar separator skeleton progress calendar breadcrumb accordion navigation-menu
    ↓
writes new files into web/src/components/ui/*.tsx (pure adds, no collisions —
    none of these 15 names exist in the current 14-file ui/ folder)
    ↓
module phases import from @/components/ui/* as usual (existing alias, unchanged)
```

### Key Data Flows

1. **Token flow:** `globals.css` (`:root`/`.dark` custom properties) → `@theme inline` (maps `--background` → `--color-background` etc., making them available as Tailwind utility classes `bg-background`, `text-foreground`) → consumed by both CLI-scaffolded components (which use semantic classes like `bg-primary` out of the box) and, optionally, by existing hand-rolled components once normalized in a later cleanup pass.
2. **Per-app independence:** `web/` and `webpage/` each read their own `globals.css`/`components.json`/`lib/utils.ts` — there is no runtime or build-time sharing between them today (confirmed: separate lockfiles, separate Docker contexts, separate Next.js processes joined only via Caddy + Multi-Zones `assetPrefix` at the reverse-proxy layer). Keeping tokens *conceptually* in sync (same hex/oklch values in both files) is a manual discipline this milestone should establish, not something the tooling enforces.

## Scaling Considerations (module rollout order)

| Phase | What it needs from Foundation | New-vs-modified file impact |
|-------|-------------------------------|------------------------------|
| **Foundation** | N/A — this phase produces the primitives everything else needs | NEW: `web/components.json`, `webpage/components.json`, ~15 files in `web/src/components/ui/` (select, tabs, dropdown-menu, command, tooltip, form, checkbox, avatar, separator, skeleton, progress, calendar, breadcrumb, accordion, navigation-menu). MODIFIED: `web/src/app/globals.css`, `webpage/src/app/globals.css` (token additions + restoration of `--background`/`--foreground`), `package.json`/`pnpm-lock.yaml` in both apps (new Radix packages: `@radix-ui/react-select`, `-tabs`, `-dropdown-menu`, `-tooltip`, `-checkbox`, `-avatar`, `-separator`, `-accordion`, `-navigation-menu`; plus `cmdk`, `react-day-picker`, non-Radix `react-hook-form`-adjacent Form wiring which is already installed). Optionally MODIFIED: `webpage/src/components/ui/button.tsx`, `card.tsx` (re-added via CLI for provenance, low risk since already near-identical). |
| **Dashboard** | Skeleton (KPI loading — currently ad hoc `animate-pulse` divs), Badge/Card/Table (already exist) | Lowest primitive need of any module — good first module phase to validate the token layer visually with minimal risk before deeper modules commit to it. MODIFIED: dashboard page only. |
| **Clientes** | Select (replaces the `selectClassName`-styled native `<select>` used throughout the 7-tab ficha, confirmed in `clientes/[id]/page.tsx`), Avatar (client-initials circle, currently a hardcoded div), optionally Command/Combobox (advogado/administrativo user-pickers) | **Tabs is explicitly NOT needed here** — PROJECT.md already logged the decision to keep the 7-tab ficha as toggle-buttons, not Radix Tabs, for visual consistency with Processos; adding the Tabs primitive to the registry does not reopen that decision unless the user asks to. MODIFIED: `clientes/[id]/page.tsx` (heaviest native-`<select>` surface in the app), `clientes/novo/page.tsx`, `clientes/merge/page.tsx`. |
| **Processos** | Select (juízo/origem/tipo-decisão enums, currently native `<select>`), Table (already exists, no DataTable needed unless explicitly requested — see Pattern 5), Tooltip (risco-prazo badges) | MODIFIED: `processos/page.tsx`, `processos/[id]/page.tsx`, `processos/novo/page.tsx`, `processos/[id]/editar/page.tsx`. |
| **Agenda** | Calendar (react-day-picker) **for date-picker form inputs only** — the existing hand-rolled month-grid view (`grid-cols-7`, manual date math in `agenda/page.tsx`) is a distinct, richer component and stays untouched; Select (categoria/status filters); Popover (already used) | Do not conflate "add Calendar primitive" with "replace the Agenda month view" — confirmed the latter is fully custom and out of this milestone's "not a redesign" scope. MODIFIED: `agenda/novo/page.tsx`, `agenda/[id]/editar/page.tsx` (date inputs only). |
| **Documentos** | Progress (upload progress — `useUploadDocumentoComProgresso` hook name implies existing custom progress UI to migrate), Select (tipo combobox — Phase 79 decision used a native `datalist`, a candidate for Command/Combobox upgrade, but that's a scope call for the roadmap, not assumed here) | MODIFIED: `documentos/page.tsx`, `documentos/novo/page.tsx`. |
| **Financeiro** | Select (honorário/pagamento forms), Table/Badge (already exist) | MODIFIED: `financeiro/page.tsx`, `financeiro/[id]/page.tsx`, `financeiro/novo/page.tsx`. |
| **Pareceres** | Select, Tooltip (timeline events), Accordion (versioning history collapse candidate) | MODIFIED: `pareceres/page.tsx`, `pareceres/[id]/page.tsx`, `pareceres/nova/page.tsx`. |
| **Notificações / Settings / Setup wizard** | DropdownMenu is more relevant here (topbar avatar currently a plain `<Link>`, no menu) than for the notification bell, which **already uses `Popover`** (confirmed in `notification-bell.tsx` — not a gap); Breadcrumb/NavigationMenu are candidates for Settings sub-navigation (no breadcrumb component exists anywhere in the app today) | Smallest surface area, safe to do last. MODIFIED: `settings/page.tsx`, `components/shared/dashboard-shell.tsx` (if a user-menu is added), `app/setup/*`. |
| **webpage refinement** | Whatever subset of the same primitives the marketing sections need (likely just re-added Button/Card via CLI; Accordion if an FAQ pattern is added) | Independent `components.json`, no dependency on `web/`'s module phases — can run in parallel with any module phase after Foundation, not necessarily last. |

### Scaling Priorities

1. **First bottleneck: token/identity drift.** The real risk in this milestone isn't the CLI mechanics (well-defined, additive) — it's that `dashboard-shell.tsx` and other `components/shared/*` files hardcode `slate-*`/`blue-*` utilities directly rather than consuming tokens. If module phases each independently decide whether/how to normalize these, the "consistency" goal of the milestone will regress into a second inconsistency. Mitigate by deciding, in Foundation, a single explicit rule: *new* CLI-scaffolded components always use semantic tokens (`bg-primary`, etc.) by default (nothing to do — that's how the CLI generates them); *existing* hardcoded files are normalized only when a module phase explicitly touches that file for a stated reason, never as a blanket find-replace across the whole app in one commit.
2. **Second bottleneck: primitive scope creep.** `Select`, `Command`, `Calendar`, and `Table`→`DataTable` are all "just add the CLI component" until they're not (Command implies rebuilding pickers as comboboxes, DataTable implies a new dependency and real refactor). Each module phase should default to the narrowest primitive that satisfies the existing native-`<select>`/native-`<input type=date>` gap, and treat richer patterns (Combobox, DataTable) as separate, explicitly-scoped follow-up decisions.

## Anti-Patterns

### Anti-Pattern 1: Running `shadcn init`/`add` with CLI defaults unexamined

**What people do:** Run `npx shadcn@latest init -d` (or accept whatever the CLI's interactive/default prompt picks) trusting it will "just match" the existing setup.
**Why it's wrong:** Current CLI defaults resolve to the `base: "base"` (Base UI) primitives library and the `"nova"` preset's rounded, neutral-accent styling — both of which conflict with this repo's actual state (`@radix-ui/react-*` already installed everywhere; sharp-edged, blue-accented "Anti-Safe Harbor" identity already established). Silent drift here is exactly the kind of inconsistency this milestone exists to remove.
**Instead:** Always pass `--base radix` explicitly; always diff `globals.css` after `init` and restore `--background`/`--foreground`/set `--radius`/`--primary` deliberately before adding any component.

### Anti-Pattern 2: Treating "two `components.json`" as requiring `packages/ui` to "do it properly"

**What people do:** See shadcn's official monorepo docs (`apps/*` + `packages/ui` + root `pnpm-workspace.yaml`/`turbo.json`) and assume that's the "correct" way to run shadcn across two apps, then build a root workspace as a prerequisite.
**Why it's wrong:** That pattern exists for genuine monorepos with an established shared workspace and multiple apps that need heavy component overlap. This repo has neither today (no root workspace exists at all) — introducing one is a structural change to a CI/Docker pipeline that was only just stabilized (v2.12/Phase 100), for a sharing benefit that's minimal given `webpage/`'s actual (tiny, already near-duplicate) component footprint.
**Instead:** Two independent `components.json`, synced manually on the rare occasions both apps need the same primitive. Revisit only if a third app or heavy overlap emerges.

### Anti-Pattern 3: Reopening already-logged component decisions as a side effect of Foundation

**What people do:** Add the `Tabs` primitive in Foundation, then "since it exists now," swap the Clientes 7-tab ficha's toggle-button pattern over to it in the Clientes module phase.
**Why it's wrong:** PROJECT.md already logged an explicit decision (Phase 76) to keep the toggle-button pattern for visual consistency with Processos, specifically *because* Tabs wasn't initialized — but the reason given was consistency with an existing pattern, not merely CLI availability. Silently reopening this without a fresh user decision expands scope beyond "consistency of components, spacing, accessibility."
**Instead:** Foundation adds `Tabs` to the registry for use in NET NEW tabbed UI (if any module needs one); it does not, by itself, authorize revisiting already-shipped, explicitly-decided patterns.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| shadcn/ui registry (`ui.shadcn.com`) | `npx shadcn@latest init` / `add <component>` fetch registry JSON over the network at scaffold time | No runtime dependency — components are copied into the repo, not installed as a library. Requires network access at scaffold time only (CI never needs to reach the registry, since scaffolded files are committed). |
| npm registry (new Radix packages) | `pnpm install` picks up new `@radix-ui/react-select`, `-tabs`, `-dropdown-menu`, `-tooltip`, `-checkbox`, `-avatar`, `-separator`, `-accordion`, `-navigation-menu`, plus `cmdk` (Command) and `react-day-picker` (Calendar) | All are well-established, small, MIT-licensed packages consistent with the 7 Radix packages already in `web/package.json`. No SAST/license concerns beyond the existing pattern. |

### Internal Boundaries

| Boundary | Communication | Considerations |
|----------|----------------|-----------------|
| `web/components.json` ↔ `web/src/app/globals.css` | CLI reads/writes the CSS file named in `components.json`'s `tailwind.css` field | Must point to `src/app/globals.css` (the actual, already-Tailwind-v4 file) — verify this path in `components.json` immediately after `init`, since CLI auto-detection could pick a different candidate if one exists. |
| `web/` ↔ `webpage/` | None at build/runtime (separate lockfiles, separate Docker images, separate Next.js processes joined only via Caddy + Multi-Zones `assetPrefix` at the reverse-proxy layer) | Keeping their two `globals.css`/`components.json` *conceptually* aligned (same token values, same `--base radix` choice) is a manual discipline for this milestone, not something enforced by any shared config. |
| `components/ui/*` (CLI-owned) ↔ `components/shared/*` (hand-written app shells) | One-directional import only (`shared/*` imports from `ui/*`, never the reverse) | This is exactly the boundary where token-adoption risk concentrates (`dashboard-shell.tsx` hardcodes colors instead of consuming `ui/*`'s tokens) — normalize deliberately, per-module, not in Foundation. |
| Foundation phase ↔ every module phase | Module phases assume the ~15 new primitives already exist in `web/src/components/ui/` | Foundation must run to completion (both apps) before any module phase starts; a module phase discovering a missing primitive mid-flight should be treated as a Foundation gap, not patched ad hoc within the module phase. |

## Sources

- shadcn/ui CLI source (`packages/shadcn/src/commands/init.ts`, `src/utils/get-project-info.ts`, `src/preflights/preflight-init.ts`, `src/utils/updaters/update-css.ts`) — fetched via Context7 (`/shadcn-ui/ui`), 2026-07-15. HIGH confidence on mechanics; MEDIUM on flag/preset naming stability given active churn (multiple indexed versions from `shadcn@2.9.0` through `shadcn_3.5.0` show the preset system renamed at least once — "default"/"new-york" → "nova"/"sera" + "base"/"radix" — since older CLI docs).
- shadcn/ui official monorepo doc (`apps/v4/content/docs/(root)/monorepo.mdx`) — fetched via Context7. HIGH confidence on the documented `packages/ui` pattern itself; used here to establish why it's a mismatch for this repo's *current* state, not to recommend adopting it now.
- Direct repository reads (2026-07-15): `.planning/PROJECT.md`, `web/src/app/globals.css`, `webpage/src/app/globals.css`, `web/src/lib/utils.ts`, `web/src/components/ui/*.tsx` (all 14 files inventoried, `button.tsx`/`table.tsx`/`sheet.tsx`/`dialog.tsx` read in full), `webpage/src/components/ui/button.tsx`, `web/package.json`, `webpage/package.json`, `web/pnpm-lock.yaml`/`webpage/pnpm-lock.yaml` (existence/independence confirmed), `webpage/pnpm-workspace.yaml`, `web/tsconfig.json`, `webpage/tsconfig.json`, `web/postcss.config.mjs`, `web/next.config.ts`, `webpage/next.config.ts`, `.github/workflows/deploy.yml`, `web/Dockerfile`, `webpage/Dockerfile`, `web/src/components/shared/dashboard-shell.tsx`, `web/src/components/shared/notification-bell.tsx`, `web/src/app/providers.tsx`, `web/src/app/(dashboard)/clientes/[id]/page.tsx`, `web/src/app/(dashboard)/agenda/page.tsx`. HIGH confidence — these are primary-source facts about the actual codebase, not inference.

---
*Architecture research for: shadcn/ui CLI integration into LexCV's `web/` + `webpage/` frontend*
*Researched: 2026-07-15*
