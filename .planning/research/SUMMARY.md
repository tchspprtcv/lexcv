# Project Research Summary

**Project:** LexCV — v2.13 Refactor UI/UX (shadcn/ui)
**Domain:** Formal shadcn/ui CLI adoption + design-system reconciliation on an existing production multi-tenant legal practice management platform (two Next.js 16 / Tailwind v4 apps: dashboard `web/` + landing `webpage/`) that already hand-rolled 15 shadcn-lookalike components on top of Radix
**Researched:** 2026-07-15
**Confidence:** HIGH

## Executive Summary

This milestone is not a greenfield shadcn adoption — it is a **retrofit** onto a codebase that has spent five prior milestones (v1.1–v2.10) hand-building 15 Radix-based primitives (`button`, `dialog`, `alert-dialog`, `card`, `table`, `sheet`, `toast`, `badge`, `input`, `label`, `popover`, `radio-group`, `switch`, `textarea`, `toaster`) that visually resemble shadcn/ui but were never scaffolded by its CLI and have accumulated undocumented drift (a project-specific `badge` `gray` variant, hardcoded `rounded-none`/`neutral-*`/`slate-*` literals instead of semantic tokens, an institutional blue accent that exists nowhere as a token). The single highest-leverage — and highest-risk — decision is how the CLI is initialized: **shadcn's `init` defaulted to Base UI primitives instead of Radix this very month (July 2026)**, so `init` must be run with the explicit `-b radix` (or `--base radix`) flag in both apps, or every newly-scaffolded component (Tabs, Select, DropdownMenu, Command, Checkbox, etc. — 15 primitives PROJECT.md lists as missing) would compose via Base UI's `render` prop instead of the `asChild`/`Slot` pattern used pervasively today, splitting the codebase into two incompatible component ecosystems.

The recommended approach is a strict **foundation-first sequence**: (1) run `shadcn init -b radix` in `web/` first, resolve style/token/radius decisions there, then hand-copy the resolved `components.json` and merged `globals.css` tokens into `webpage/` rather than re-running the wizard independently (the two apps have no shared workspace to keep them in sync automatically); (2) consolidate a full semantic token set in `globals.css` (today only `--background`/`--foreground` exist) while explicitly restoring the two already-shipped hex values and setting `--radius: 0` and the institutional blue as `--primary`, since CLI defaults are rounded and neutral; (3) decide and execute a Radix-package-identity strategy (unified `radix-ui` package vs. the 15 existing files' scoped `@radix-ui/react-*` imports — a February 2026 breaking change) rather than leaving a silent dual dependency tree; (4) add the ~15 missing primitives (Select, Tabs, DropdownMenu, Command, Tooltip, Form, Checkbox, Avatar, Separator, Skeleton, Progress, Calendar, Breadcrumb, Accordion, NavigationMenu, plus `Empty`, a good fit found this session) net-new, with zero collision risk; (5) reconcile the 15 pre-existing hand-rolled files one at a time via `add <name> --diff` (never blind `--overwrite`), preserving custom variants and institutional styling; (6) only then roll out per-module visual work (Dashboard → Clientes+Processos combined Tabs migration → Agenda → Documentos/Financeiro → Pareceres → Notificações/Settings/Setup → `webpage/` refinement), each module reusing a single shared DataTable pattern (`@tanstack/react-table`, a genuinely new dependency) rather than five independent builds.

The main risks, all well-understood with low-cost mitigations: silent CSS theme corruption from `init` appending a second `:root`/`.dark` block that overrides institutional colors without looking like it in a naive diff; blind component overwrites destroying the `gray` badge variant and other undocumented custom behavior with no automated visual-regression tests to catch it; losing the project's own already-shipped, non-shadcn-standard UX investments (the 7-tab RBAC-conditional overflow-scroll pattern on Clientes/Processos fichas, the dual card+table responsive list pattern used on all 7 list-bearing modules, the already-correct `Popover`-based notification bell) by following generic library docs literally instead of re-implementing the same behavior on new primitives; and scope creep from features that look like "just add the CLI component" but actually require new backend data or out-of-scope dependencies (Recharts charts needing time-series KPI data that doesn't exist yet, third-party Stepper/blocks marketplaces explicitly excluded by PROJECT.md). None of this requires new tooling beyond the official CLI and `@tanstack/react-table`/`sonner`; the milestone is explicitly **not** a structural redesign, and the biggest work is disciplined reconciliation and sequencing, not net-new architecture.

## Key Findings

### Recommended Stack

The core adoption is the `shadcn` CLI package itself (v4.13.0, published as `shadcn` not the deprecated `shadcn-ui`), run with `-b radix` to keep 100% composition parity with the 9 `@radix-ui/react-*` packages and the pervasive `asChild` pattern already in both apps. Tailwind v4 is already correctly in place (CSS-first, no `tailwind.config.ts`) in both apps and needs no change beyond token consolidation. `tw-animate-css` replaces the deprecated `tailwindcss-animate` plugin. For the 15 missing primitives, each `shadcn add <component>` pulls in its own small Radix package (`-select`, `-tabs`, `-dropdown-menu`, `-tooltip`, `-checkbox`, `-avatar`, `-separator`, `-accordion`, `-navigation-menu`) plus `cmdk` (Command) and `react-day-picker` (Calendar — **must be re-pinned to `9.14.0` immediately after `add calendar`**, since the registry currently resolves to a broken v10 with an open, unresolved upstream issue). `@tanstack/react-table` is a genuinely new dependency required only for the DataTable pattern (not an installable shadcn component, a copy-paste recipe). `sonner` replaces the deprecated `Toast`/`@radix-ui/react-toast`.

**Core technologies:**
- `shadcn` CLI `4.13.0` with `-b radix` flag — official scaffolding, forced onto Radix to match all 15 existing hand-rolled primitives (default changed to Base UI this month; getting this wrong splits the codebase into two composition idioms)
- Tailwind CSS v4 (already installed, `^4`) — CSS-first theming; `components.json`'s `tailwind.config` field must stay empty string, no `tailwind.config.js`/`.ts` should ever be created
- `tw-animate-css@1.4.0` — CSS-first, drop-in replacement for the deprecated `tailwindcss-animate` JS plugin
- `@tanstack/react-table` (new dep) — required only for the shared DataTable pattern, not a CLI-installable component
- `sonner` (new dep, replaces `@radix-ui/react-toast`) — official toast successor; near-zero call-site diff since the app's own `toast.success()`/`toast.error()` wrapper already mirrors Sonner's API
- `react-day-picker` pinned to `9.14.0` (not `@latest`) — the `calendar` registry item currently requests a broken v10

**Explicitly avoid:** plain `init` (no `-b` flag, defaults to Base UI), `pnpm dlx skills add shadcn/ui` (explicitly out of scope per PROJECT.md — unverified external tooling), blind `add --overwrite` on any of the 15 existing files, `react-day-picker@latest`, and creating any `tailwind.config.js`/`.ts` file.

### Expected Features

**Must have (table stakes, P1):**
- CLI init with `-b radix` + all ~15 missing primitives added (Select, Tabs, DropdownMenu, Command, Tooltip, Form, Checkbox, Avatar, Separator, Skeleton, Progress, Calendar, Breadcrumb, Accordion, NavigationMenu, `Empty`) — unblocks everything else
- Shared DataTable pattern (`@tanstack/react-table` + existing `Table` primitive), built once and reused across Clientes/Processos/Pareceres/Financeiro/Documentos lists — sort, filter toolbar, official `Pagination`
- Clientes ficha + Processos ficha migrated together (single combined item, not two) from manual toggle-button tabs to real `Tabs` — this is a genuine, verifiable accessibility gap (no `role="tablist"`/`aria-selected` today), not a style preference
- `NativeSelect`/`Select` swap for every raw `<select className={selectClassName}>` across Clientes/Processos/Setup forms
- `Skeleton` loading states + `Empty` zero-result states standardized across list/tab screens (replacing bare `"A carregar..."` text and ad hoc `"Nenhum ... registado."` messages)
- Official `Pagination` component for `/notificacoes` and any other server-paginated list
- Sonner swap for deprecated Toast, sequenced as its own small foundation item
- `webpage/` mobile navigation (currently **zero nav on mobile** — a real functional gap, not cosmetic), reusing the existing hand-rolled `Sheet`

**Should have (differentiators, P2, optional):**
- `Breadcrumb`, `Tooltip`, `Avatar` cosmetic swaps for hand-built equivalents (breadcrumb `<div>` + literal `/`, icon-only buttons, plain-text person rows)
- `webpage/` Hero/Contact restructured around `Card`/`Badge` composition to match the already-idiomatic `TrustSection`
- `ScrollArea` in the notification bell list; `Combobox` for the tipo-de-documento free-text field

**Defer (explicitly out of this milestone):**
- Recharts-backed `Chart` for Dashboard trend visualization — blocked on a backend time-series KPI endpoint that doesn't exist; conflicts with "visual-only" framing
- Real trend-delta computation for KPI badges (currently hardcoded `+12%` etc.) — business-logic work, not visual refactor
- `NavigationMenu` mega-menu for `webpage/` — only worth it if nav grows beyond 3 flat anchor links
- Bulk row-selection/actions on any DataTable — no bulk actions exist in the product today, don't invent UI for a non-existent capability
- Any third-party "shadcn blocks"/Stepper marketplace — no official Stepper exists and none is needed; PROJECT.md explicitly excludes unverified external packages

### Architecture Approach

`web/` and `webpage/` are two fully independent Next.js apps (separate lockfiles, separate Docker build contexts, no root `pnpm-workspace.yaml`) — not true pnpm workspace members despite milestone framing suggesting otherwise. The recommended structure keeps **two independent `components.json`** (one per app), explicitly rejecting shadcn's official `packages/ui` monorepo pattern for this milestone: the prerequisite work (root workspace, merged lockfiles, rewritten Docker build contexts touching the CI pipeline just stabilized in the prior v2.12/Phase 100 milestone) far outweighs the sharing benefit, since `webpage/` only has 2 hand-rolled files today and needs almost none of the 15 new primitives targeted at `web/`. `globals.css` changes are additive (the CLI's CSS updater merges token declarations by name, doesn't truncate), but the two pre-existing tokens (`--background`/`--foreground`) will have their values silently overwritten by the CLI's default `baseColor` unless restored immediately post-init.

**Major components:**
1. `components.json` (new, per app) — CLI configuration; must specify `-b radix`, `"style": "new-york"`, `"baseColor": "neutral"`, empty `tailwind.config`, and identical `aliases` in both apps
2. `globals.css` (modified, per app) — gains the full shadcn semantic token set (`--primary`, `--secondary`, `--muted`, `--accent`, `--destructive`, `--border`, `--input`, `--ring`, `--card`, `--popover`, `--radius`) merged additively; `--background`/`--foreground` restored to shipped hex, `--radius` set to `0` and `--primary` set to the institutional blue deliberately, not left at CLI defaults
3. `components/ui/*` (14 existing files unchanged pending reconciliation + ~15 new CLI-scaffolded files) — one-directional import boundary: `components/shared/*` imports from `ui/*`, never the reverse
4. `components/shared/*` (e.g. `dashboard-shell.tsx`) — highest hardcoded-color risk surface (`bg-slate-950`, `text-blue-400` literals); deliberately left untouched by the Foundation phase, normalized only when a module phase explicitly touches that file

### Critical Pitfalls

1. **`shadcn init` silently corrupts the theme by appending a second `:root`/`.dark` block** that overrides institutional colors via CSS cascade while looking like a pure addition in `git diff` — mitigate with `--dry-run` first, then manually verify exactly one `:root`/`.dark` block exists and restore/verify hex values against Figma before merging.
2. **Blind `add --overwrite` wipes undocumented custom variants and props** (the `gray` badge variant used in 3 files, `dialog.tsx`'s missing `showCloseButton` handling) across 93 import occurrences in 38 files — always `add <component> --diff` first, reconcile component-by-component in small reviewable diffs, never all 15 at once.
3. **Radix package identity split** — CLI-generated components since Feb 2026 import from the unified `radix-ui` package while all 15 existing hand-rolled files import scoped `@radix-ui/react-*` packages — decide explicitly (run `shadcn migrate radix` now vs. tracked bridge state) in the foundation phase, don't let it linger past milestone close.
4. **Two independent `init` runs drift apart** — `web/` and `webpage/` have no shared config to inherit from; sequence `web/` first, then hand-copy its resolved answers into `webpage/` rather than re-running the wizard independently.
5. **Module-specific UX regressions from following generic docs literally**: migrating the 7-tab ficha to `Tabs` without re-adding `overflow-x-auto` (breaks at RBAC roles with fewer visible tabs, untestable with ADMIN-only QA); consolidating the dual card+table responsive pattern (7 modules) into one scrollable table "to reduce duplication" (a project-specific decision, not a shadcn convention); converting the already-correct `Popover`-based notification bell to `DropdownMenu` (a known a11y anti-pattern for multi-control list items).

## Implications for Roadmap

Based on combined research, suggested phase structure:

### Phase 1: Foundation — CLI Init & Design Tokens
**Rationale:** Every other phase depends on this; it is the single highest-leverage and highest-risk decision point (Base UI vs. Radix default, theme corruption risk).
**Delivers:** `components.json` in `web/` (init first) then `webpage/` (hand-copied answers), full semantic token set merged into both `globals.css` files with institutional colors/radius/accent restored deliberately, ~15 missing primitives added net-new, explicit Radix-package-identity decision (migrate vs. tracked bridge), `react-day-picker` re-pinned to `9.14.0`, no `tailwind.config.js` created.
**Addresses:** Foundation item from FEATURES.md MVP definition.
**Avoids:** Pitfalls 1 (theme corruption), 3 (Radix package split), 4 (Tailwind v4 config misunderstanding), 5 (web/webpage drift).

### Phase 2: Design System Reconciliation
**Rationale:** Must complete before any per-module visual work starts, or the app ships in a visibly half-migrated state (some components on tokens, some still hardcoded) — this is explicitly called out as a UX pitfall to avoid.
**Delivers:** Each of the 14 existing hand-rolled files (button, dialog, alert-dialog, card, table, sheet, badge, input, label, popover, radio-group, switch, textarea, toaster) diffed against the current registry and reconciled one-by-one (custom variants like `gray` badge preserved, semantic tokens adopted now that they exist); Sonner swap-in replacing Toast/`toaster.tsx`, re-mounted at both apps' root layouts.
**Avoids:** Pitfall 2 (blind overwrite data loss), Pitfall 8 (losing project-specific props while mistakenly re-verifying Radix a11y that was never at risk).

### Phase 3: Dashboard Module
**Rationale:** Lowest primitive need of any module (mostly `Skeleton`/`Empty`/`Badge`, all already exist or are trivial); best low-risk phase to visually validate the new token layer before deeper modules commit to it.
**Delivers:** KPI card loading skeletons, `Empty` zero-result states, cosmetic Badge/Card token adoption.
**Uses:** `Skeleton`, `Empty`, `Badge`, `Card` from Foundation.

### Phase 4: Shared DataTable Pattern
**Rationale:** FEATURES/ARCHITECTURE both flag this as the single largest net-new lift and insist it be built once, generically, before being applied across 5 screens — treat as its own phase/step rather than reinventing it per module.
**Delivers:** `@tanstack/react-table` added as a dependency; one shared `columns.tsx`/`data-table.tsx`/toolbar/pagination recipe composed on the existing `Table` primitive; explicitly scoped to only the `hidden md:block` desktop branch of list pages, wired to the same server-side TanStack Query filters already in use (not duplicated client-side filtering).
**Implements:** DataTable architecture pattern from FEATURES.md/ARCHITECTURE.md.
**Avoids:** Pitfall 7 (dual-view pattern collapse), the Performance Trap of duplicate client/server filtering.

### Phase 5: Clientes + Processos Modules (combined)
**Rationale:** Both fichas use the identical hand-rolled toggle-button tab pattern by deliberate prior design decision; migrating only one breaks intentional visual consistency, so this must ship as one roadmap item, not two.
**Delivers:** `Tabs` migration for both fichas (preserving RBAC-conditional trigger count, controlled `value`/`onValueChange`, re-added `overflow-x-auto`, tested at 375px across ADVOGADO/TECNICO/ASSISTENTE/ADMIN roles); `NativeSelect`/`Select` swap for raw `<select>` instances; `Avatar` for person-representing rows; DataTable adoption on both list screens.
**Avoids:** Pitfall 6 (mobile tab overflow regression), Anti-Pattern 3 (reopening the tab-pattern decision without cause beyond CLI availability — this migration is independently justified by the a11y gap, not merely "Tabs now exists").

### Phase 6: Agenda Module
**Rationale:** Narrow, well-scoped primitive need (Calendar for date-picker form inputs only); the existing hand-rolled month-grid view is a distinct, richer component explicitly out of scope.
**Delivers:** `Calendar`/`react-day-picker` wired into date-picker form inputs (`agenda/novo`, `agenda/[id]/editar`); `Select` for categoria/status filters.
**Avoids:** Conflating "add Calendar primitive" with "replace the Agenda month view" (explicitly out of "not a redesign" scope).

### Phase 7: Documentos + Financeiro Modules
**Rationale:** Similar primitive needs (Select, Progress, DataTable adoption), modest scope each, safe to combine.
**Delivers:** `Progress` for upload progress (replacing existing custom progress UI), `Select` for tipo/honorário/pagamento forms, DataTable adoption on both list screens.

### Phase 8: Pareceres Module
**Rationale:** Modest, well-scoped primitive need (Select, Tooltip, Accordion for versioning history).
**Delivers:** `Select`, `Tooltip` on timeline events, `Accordion` for versioning collapse, DataTable adoption.

### Phase 9: Notificações / Settings / Setup Wizard
**Rationale:** Smallest surface area, safe to do last; the notification bell is already correctly implemented (`Popover`), so this phase is mostly additive (topbar user-menu, breadcrumbs, wizard progress).
**Delivers:** Official `Pagination` on `/notificacoes`; `DropdownMenu` for a new topbar user-menu (not the bell); unread-badge swapped to official `Badge`; `Breadcrumb`/`NavigationMenu` for Settings sub-navigation; `Progress`-based linear step indicator for `/setup` (no Stepper component exists officially or is needed).
**Avoids:** Anti-Feature of replacing the correct `Popover`-based bell with `DropdownMenu`; Anti-Feature of installing a third-party Stepper package.

### Phase 10: `webpage/` Landing Refinement
**Rationale:** Independent `components.json`, no dependency on `web/`'s per-module phases — can run in parallel with any module phase after Foundation/Reconciliation complete, not necessarily last in sequence.
**Delivers:** Mobile navigation for `SiteHeader` (currently **zero nav on mobile**, reusing existing `Sheet`); Hero/Contact restructured around `Card`/`Badge`/`Avatar` composition matching the already-idiomatic `TrustSection`; optional `NavigationMenu` if a dropdown structure emerges.
**Avoids:** Adopting third-party "shadcn blocks" marketplaces for marketing sections (no official equivalent exists — original composition from atomic primitives only).

### Phase Ordering Rationale

- Foundation and Reconciliation must both complete before any module phase starts, because the "half-migrated, visually inconsistent app" state is itself flagged as a UX pitfall — this is the strongest ordering constraint in the research.
- The DataTable pattern is deliberately its own phase (not folded into the first module that needs it) because building it once and reusing it across 5 screens is repeatedly emphasized across FEATURES.md and ARCHITECTURE.md as the correct sequencing to avoid 5 independent, subtly-inconsistent implementations.
- Clientes and Processos are combined into a single phase because their tab-migration is explicitly required to ship together (identical pattern, deliberate prior consistency decision) — splitting them risks a visible inconsistency window.
- Module order after that (Agenda → Documentos/Financeiro → Pareceres → Notificações/Settings/Setup) follows ARCHITECTURE.md's scaling table, roughly ascending by primitive-set novelty and descending by usage frequency/risk, ending with the smallest, safest surface area.
- `webpage/` refinement is placed last only for narrative clarity — it has no technical dependency on `web/`'s module phases and can be parallelized once Foundation is done, which the roadmapper should feel free to do if resourcing allows.

### Research Flags

Needs research during planning (`/gsd:plan-phase --research-phase <N>`):
- **Phase 1 (Foundation):** CLI flag names/preset taxonomy are under active churn (confirmed renamed at least once in the last 8 months: "new-york"/"default" → "nova"/"sera" + "base"/"radix"); re-verify with `npx shadcn@latest init --help`/`--dry-run` at actual execution time rather than trusting this research's exact flag syntax. Also needs a fresh decision on the Radix-package-migration strategy (`migrate radix` now vs. bridge state).
- **Phase 4 (Shared DataTable Pattern):** It's a recipe, not an installable component — integrating it with the existing TanStack Query server-side filters (not duplicating client-side) needs implementation-time verification per screen's actual filter shape.
- **Phase 5 (Clientes + Processos):** RBAC-conditional tab-overflow behavior needs functional verification across all 4 roles at real mobile viewport widths — not a "research the docs" task, but a "test against this app's actual RBAC matrix" task that planning should call out explicitly.

Phases with standard, well-documented patterns (skip research-phase):
- **Phase 3 (Dashboard):** Skeleton/Empty are simple, official, drop-in components with no known gotchas.
- **Phase 6 (Agenda):** Calendar/react-day-picker version pin is already resolved by this research (`9.14.0`); date-picker-only scope is narrow and low-risk.
- **Phase 9 (Notificações/Settings/Setup):** No official Stepper exists and none is needed — `Progress`-based linear indicator is trivial; DropdownMenu/Breadcrumb are standard patterns.
- **Phase 10 (webpage/):** Composition from already-verified atomic primitives (Card/Badge/Sheet), no official marketing blocks to evaluate.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Verified live against official ui.shadcn.com docs/changelogs, npm registry, and GitHub issues this session (July 2026) — this area changed significantly in the last 8 months, so training data alone would have been wrong on several points (CLI package name, Base UI default, react-day-picker v10 break). |
| Features | HIGH for component APIs/patterns (verified live), MEDIUM-HIGH for LexCV-specific fit (grounded by direct reads of actual current source: `clientes/[id]/page.tsx`, `dashboard/page.tsx`, `notification-bell.tsx`, `site-header.tsx`, `package.json`, `components/ui/` directory listings). |
| Architecture | HIGH for CLI mechanics (verified against current shadcn CLI source via Context7) and repo facts (read directly from working tree); MEDIUM for exact CLI flag/preset naming stability at execution time given confirmed active churn. |
| Pitfalls | HIGH for CLI mechanics and codebase-specific findings (verified by reading actual source: 93 import occurrences across 38 files, `badge.tsx`'s `gray` variant, `globals.css`'s 2-variable-only token set); MEDIUM for migration-specific UX regression claims (community-reported GitHub issues, cross-checked with 2+ sources). |

**Overall confidence:** HIGH

### Gaps to Address

- **CLI flag/preset naming may have shifted again** between this research (2026-07-15) and actual Foundation-phase execution — re-verify with `npx shadcn@latest init --help` and `--dry-run` before running `init` for real, rather than trusting exact flag syntax from this document.
- **Radix package migration strategy (unified `radix-ui` vs. scoped `@radix-ui/react-*`) is not yet decided** — flagged as an explicit foundation-phase decision point, not resolved by this research; needs a deliberate choice (migrate now vs. tracked bridge state) during roadmap/phase planning.
- **Zod v4 compatibility with shadcn's current `Field`/Form docs** (which demonstrate v3 idioms) needs verification at the point `add form` is actually run — flagged as MEDIUM confidence in STACK.md/FEATURES.md, not fully resolved.
- **Login/auth page patterns were not researched this pass** (FEATURES.md flags this as a gap in coverage, not a finding) — low priority since login is typically a simple form, but worth a quick look if a Settings/Setup/Auth-adjacent phase touches it.
- **Whether the classic `Command`+`Popover` Combobox recipe or the newer Base UI-specific `base/combobox` variant applies** depends on the `-b radix` decision being fully honored throughout — should resolve cleanly given the Radix choice, but worth a sanity check if a Combobox is actually built (currently deferred/P3 anyway).
- **Exact blast radius of the `webpage/` `button.tsx`/`card.tsx` re-add via CLI** (already near-identical to canonical output, low risk) was assessed as safe but not diffed line-by-line in this research pass — a quick `--diff` before touching is still warranted per the general reconciliation protocol.

## Sources

### Primary (HIGH confidence)
- https://ui.shadcn.com/docs/cli — CLI flags, `-o/--overwrite`/`--diff`/`--dry-run` behavior
- https://ui.shadcn.com/docs/tailwind-v4 — Tailwind v4 CSS-first config, `@theme inline`, empty `tailwind.config` for v4
- https://ui.shadcn.com/docs/monorepo — official `packages/ui` pattern, per-workspace `components.json` field consistency requirement
- https://ui.shadcn.com/docs/components-json and https://ui.shadcn.com/schema.json — full `components.json` field/enum reference
- https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default — Base UI becoming the new `init` default this month, `-b radix` opt-out, `asChild`→`render`
- https://ui.shadcn.com/docs/changelog/2026-02-radix-ui — unified `radix-ui` package import change, `migrate radix` command
- https://ui.shadcn.com/docs/changelog/2026-03-cli-v4 — `--dry-run`/`--diff`/`--view`, `shadcn info`/`shadcn docs`, `init --monorepo`, preset system
- https://ui.shadcn.com/docs/changelog/2025-12-shadcn-create — style-preset taxonomy (Vega/Nova/Maia/Lyra/Mira) introduction
- Full official component docs pages fetched live (Data Table, Tabs, Chart, Dropdown Menu, Forms/React Hook Form, Pagination, Combobox, Base UI Combobox, Command, Select, Native Select, Calendar, Toast/Sonner, Skeleton, Empty, Scroll Area, Breadcrumb, Accordion, Tooltip, Navigation Menu, Sidebar, Blocks index, Components index)
- shadcn/ui CLI source (`init.ts`, `get-project-info.ts`, `preflight-init.ts`, `update-css.ts`) fetched via Context7, 2026-07-15 snapshot
- npm registry live version checks for all relevant packages
- Direct repository inspection: `web/package.json`, `webpage/package.json`, both `globals.css` files, all 15 `web/src/components/ui/*.tsx` files, both `tsconfig.json` files, `web/src/lib/utils.ts`, `webpage/src/lib/utils.ts`, `.github/workflows/deploy.yml`, both `Dockerfile`s, `dashboard-shell.tsx`, `notification-bell.tsx`, `clientes/[id]/page.tsx`, `clientes/page.tsx`, `dashboard/page.tsx`, `agenda/page.tsx`, `site-header.tsx`/`hero-section.tsx`/`trust-section.tsx`/`contact-section.tsx`, `.planning/PROJECT.md`, absence of root `pnpm-workspace.yaml`/`package.json`

### Secondary (MEDIUM confidence)
- https://github.com/shadcn-ui/ui/issues/10914 — open bug, `react-day-picker@10.0.1` build failure in Calendar component
- https://github.com/shadcn-ui/ui/issues/2791 and #4845 — `init` overwriting/interfering with existing `globals.css`, closed as stale but consistent with documented append behavior
- https://github.com/shadcn-ui/ui/issues/931, discussions #7739, #7672/#7794 — `add` overwrite/skip/prompt behavior across CLI versions
- https://github.com/shadcn-ui/ui/discussions/9562 — Radix→Base UI migration guide, breaking-change inventory
- https://github.com/shadcn-ui/ui/discussions/3263 and #6353 — confirms no official Stepper block has ever shipped despite repeated requests
- radix-ui/primitives #855/#1155/#2359 and mui/base-ui #4822 — confirms both Radix `Tabs.Content` and Base UI `Tabs.Panel` unmount inactive panels by default
- eastondev.com blog post on shadcn/Radix accessibility customization (single source, directionally consistent with Radix's documented behavior)

### Tertiary (LOW confidence, used only to confirm absence of an official equivalent)
- shadcnblocks.com, shadcndesign.com, shadcnuikit.com, shadcnstudio.com — third-party marketing block/stepper registries, cited only to demonstrate they are explicitly out of scope per PROJECT.md, not as recommendations

---
*Research completed: 2026-07-15*
*Ready for roadmap: yes*
