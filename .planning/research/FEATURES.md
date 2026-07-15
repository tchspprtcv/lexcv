
# Feature Research

**Domain:** shadcn/ui-driven UI/UX refactor of an existing multi-tenant legal practice management app (LexCV) — internal dashboard `web/` + public landing `webpage/`
**Researched:** 2026-07-15
**Confidence:** HIGH for component APIs/patterns (verified live against ui.shadcn.com this session, July 2026) / MEDIUM-HIGH for LexCV-specific fit (grounded by reading actual current source) / flagged inline where lower

> Supersedes the v2.12-dated `FEATURES.md` previously at this path (that research covered the `webpage/` landing-page content milestone; this one is scoped entirely to the new v2.13 shadcn/ui UI/UX refactor milestone).

## Critical Cross-Cutting Finding (read this before the per-area tables)

**shadcn/ui defaulted to Base UI on new `init` this month (July 2026 changelog: "New projects default to Base UI").** Base UI replaces Radix's `asChild` composition prop with a `render` prop. Radix is not deprecated and remains fully supported via an explicit CLI flag (`shadcn init -b radix`), but it is no longer the default. This matters enormously for LexCV specifically:

- `web/package.json` already depends directly on `@radix-ui/react-dialog`, `@radix-ui/react-alert-dialog`, `@radix-ui/react-popover`, `@radix-ui/react-radio-group`, `@radix-ui/react-switch`, `@radix-ui/react-slot`, `@radix-ui/react-toast` — every hand-built primitive in `web/src/components/ui/` (dialog, alert-dialog, sheet, popover, radio-group, switch) is Radix-based.
- The codebase uses the Radix `asChild` composition pattern pervasively already (`<Button asChild><Link href="...">...</Link></Button>` appears dozens of times across Clientes/Processos/Dashboard/webpage).
- If the shadcn CLI is initialized with its new default (Base UI) and used to add new primitives (Tabs, Select, DropdownMenu, Command, Tooltip, Checkbox, Avatar, Separator, Skeleton, Progress, Calendar, Breadcrumb, Accordion, NavigationMenu — all currently missing per PROJECT.md), those new components would compose via `render` instead of `asChild`, producing a **mixed-primitive-library codebase** with two different composition idioms living side by side.
- **This is a foundation-phase decision, not mine to make, but it gates every item below**: initializing with `shadcn init -b radix` keeps 100% API/composition consistency with everything already built and is the only choice that doesn't silently introduce a second paradigm. Flagging as HIGH confidence, directly sourced from the official [July 2026 Base UI changelog](https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default).
- The changelog also surfaces a `pnpm dlx skills add shadcn/ui` migration skill for progressively moving components from Radix to Base UI — this is almost certainly the exact mechanism PROJECT.md's "Fora de âmbito" line is pre-emptively excluding ("instalação de skills/pacotes externos não verificados (ex.: 'skills add shadcn/ui') — apenas a CLI oficial `shadcn@latest`"). That decision is validated by this research: it correctly avoids an unverified/fast-moving mechanism in favor of the stable, explicit `-b radix` init flag.

**Toast is officially deprecated in favor of Sonner** ("The toast component has been deprecated. Use the sonner component instead" — `ui.shadcn.com/docs/components/toast`). LexCV's `web/src/components/ui/toast.tsx` + `toaster.tsx` + `@radix-ui/react-toast` are the old, deprecated shape. Notably, the app's own `useToast`/`toast` wrapper (`toast.success(...)`, `toast.error(...)`) already mirrors Sonner's exact call-site API (`toast.success()`, `toast.error()`), which makes a swap to Sonner a **near-zero-diff call-site migration** — only the underlying hook implementation and the root `<Toaster />` mount need to change; every existing `toast.success("...")` call in Clientes/Processos/Pareceres/Documentos forms keeps working unmodified.

**Official blocks (`ui.shadcn.com/blocks`) are app/dashboard-oriented only** — categories confirmed: Dashboard, Sidebar (variants), Login, Signup, Calendar. **There is no official marketing/landing category** (no Hero, Features grid, Testimonials, Pricing, Contact/CTA blocks on ui.shadcn.com). All "shadcn hero/marketing blocks" surfaced by search (shadcnblocks.com, shadcndesign.com, shadcnuikit.com, shadcnstudio.com) are **third-party paid/community registries**, explicitly out of scope per PROJECT.md's "apenas a CLI oficial" constraint. This directly shapes the `webpage/` recommendation below: compose landing sections from official atomic primitives (Button, Card, Badge, Avatar, Separator, NavigationMenu) rather than importing a "block."

---

## Feature Landscape

### Table Stakes (Do This)

#### Data-heavy list/CRUD screens — Clientes, Processos, Pareceres, Financeiro, Documentos

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| DataTable pattern (TanStack Table + shadcn `<Table>`) for the main list screens (`/clientes`, `/processos`, `/pareceres`, `/financeiro`) | shadcn ships **no single installable DataTable component** — it's an official copy-paste recipe (`columns.tsx` + `data-table.tsx` + `DataTableColumnHeader`/`DataTablePagination`/`DataTableViewOptions`) built on `@tanstack/react-table`, which is a **new dependency** (confirmed absent from `web/package.json` today — only `@tanstack/react-query` is present) | MEDIUM | Composes on top of shadcn's existing `Table`/`TableBody`/`TableRow`/`TableCell` (already in `web/src/components/ui/table.tsx`) + needs `Button`, `DropdownMenu`, `Checkbox`, `Input` (Checkbox/DropdownMenu are on PROJECT.md's missing-primitive list). This is the single biggest net-new pattern the milestone needs to stand up once, then reuse across 5 screens. |
| Sortable column headers (`DataTableColumnHeader`, `getSortedRowModel`) | Users expect clicking "Nome"/"Estado"/"Data" to sort; today the tables (e.g. `ClienteProcessosTab`, `ClienteParecerTab`) are static, unsorted | LOW (once DataTable exists) | Purely additive on top of the DataTable foundation — no data-shape changes needed since sorting happens client-side per page. |
| Toolbar filtering (search input + faceted filters using existing dropdown of statuses) | Clientes/Processos/Pareceres/Financeiro all already have hand-rolled filter UIs (per PROJECT.md's history: "filtros críticos", "pesquisa avançada") — DataTable's toolbar recipe formalizes this into a consistent shape | MEDIUM | `getFilteredRowModel()`; faceted filters need `Popover`+`Command`+`Checkbox` (Command is on the missing list). Riskiest part is preserving server-side filter semantics already wired to TanStack Query hooks — client-side TanStack Table filtering must not silently duplicate/conflict with server-side query params already in use. |
| Row-selection + column-visibility toggle (`DataTableViewOptions`) | Standard DataTable affordance; useful for bulk actions if any exist (none currently in LexCV — see Anti-Features) | LOW-MEDIUM | Needs `Checkbox` (missing primitive) + `DropdownMenu` (missing primitive). Skip row-selection checkboxes on screens with no bulk action to keep scope honest (see Anti-Features). |
| Official `Pagination` component (`Pagination`, `PaginationContent`, `PaginationItem`, `PaginationLink`, `PaginationPrevious`, `PaginationNext`, `PaginationEllipsis`) for server-paginated lists | `/notificacoes` already implements "paginação real" per PROJECT.md (Phase 89) — almost certainly hand-rolled prev/next buttons today. This is exactly the kind of "amateur/inconsistent element" the milestone goal calls out. | LOW | Renders `<a>` by default; must swap to Next.js `<Link>`-based `PaginationLink` (documented pattern) to keep client-side routing. Good target for Notificações page + any other server-paginated list. |
| Dialog-based create/edit forms (`Dialog`, `DialogContent`, `DialogHeader`, `DialogTitle`, `DialogFooter`, `DialogTrigger`) | **Already in place and already idiomatic** — `dialog.tsx` exists, and Clientes/Processos already use exactly this pattern for "Adicionar" flows (Partes, Fases, Decisões, Factos, Testemunhas, Documentos a Tratar, Deslocações, Documento Entregue upload all use `Dialog`+`DialogTrigger`+`DialogFooter` today) | N/A — already done | This is a genuine strength to preserve, not rebuild. The only real gap is the raw `<select>`/`<textarea>` elements with hand-maintained Tailwind class strings inside those dialogs (see `selectClassName`/`textareaClassName` consts in `clientes/[id]/page.tsx`) — swap these for shadcn `Select`/`NativeSelect`/`Textarea` primitives once available, not a dialog-pattern change. |
| `NativeSelect` for short static option lists inside forms (tipo de documento, ramo de atividade, tipo de decisão, etc.) | Confirmed official component (`ui.shadcn.com/docs/components/native-select`): "a styled native HTML select element with consistent design system integration," explicitly positioned as the right choice over the popover-based `Select` for simple, short, mobile-friendly dropdowns — exactly what today's raw `<select className={selectClassName}>` instances are | LOW | Nearly a drop-in swap: same native semantics/keyboard/mobile behavior, just shadcn design tokens instead of a hand-maintained className string duplicated across files. Lower risk than migrating these same dropdowns to the heavier Radix/Base `Select`. |
| `Checkbox`, `Switch` for boolean toggles | `Switch` already exists (`avençado` toggle uses it correctly today); `Checkbox` does not exist yet and DataTable row-selection requires it | LOW | Standard Radix/Base primitive, no surprises. |

#### Ficha de Cliente 7-tab pattern (and Processos' equivalent Partes/Fases/Decisões/Factos/Testemunhas/Documentos tabs)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Migrate the manual toggle-`Button` tab bar to real shadcn `Tabs` (`Tabs`, `TabsList`, `TabsTrigger`, `TabsContent`) | **This is a genuine, verifiable accessibility gap today, not a style preference.** The current implementation (`clientes/[id]/page.tsx`) is a `<div className="flex gap-2">` of plain `<Button variant={tab===x?"secondary":"outline"}>` elements with a `tab` state ternary below — it has no `role="tablist"`/`role="tab"`/`role="tabpanel"`, no `aria-selected`, and no arrow-key roving-tabindex navigation. Radix/Base `Tabs` provides all of this for free, which is precisely the milestone's own stated goal ("substituir elementos amadores/inconsistentes por primitivos shadcn/Radix acessíveis"). | MEDIUM | See dedicated verdict below. |

**Dedicated verdict on the Tabs question:** The original PROJECT.md rationale for the manual pattern ("evita introduzir um componente novo; shadcn Tabs nunca foi inicializado no projeto") is **no longer valid as a blocker**, because this milestone's entire foundation phase is initializing the CLI and adding exactly this kind of missing primitive anyway — the cost of "introducing Tabs" is being paid regardless, for other components. Two things actually reduce risk further:
1. **Lifecycle compatibility is good, not bad.** Verified: both Radix `Tabs.Content` and Base UI `Tabs.Panel` **unmount inactive panels by default** (no `forceMount`/`keepMounted`) — i.e., the exact same "only the active tab's subtree exists in the DOM" semantics the current ternary-based `tab === "dados" ? (...) : ...` already relies on (including the `useEffect` that resets in-tab dialog state when navigating away from "documentosATratar"/"deslocacoes"). Migrating does not change this contract.
2. **Scope is 2x, not 1x**: Clientes' ficha AND Processos' ficha both use the identical hand-rolled pattern by deliberate design ("Partes e Fases... refatoradas para o mesmo padrão... para consistência visual" — PROJECT.md). Consistency demands migrating both together or neither; treat this as one roadmap item covering both fichas, not two.

Recommendation: **table-stakes for this milestone**, MEDIUM complexity, but do it as a single dedicated phase covering both fichas together, after `Tabs` is added to the primitive library in the foundation phase.

#### Dashboard KPIs

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Stat cards (icon + label + big number + trend badge) | **Already built and already structurally matches shadcn's own `dashboard-01` block `SectionCards` pattern** (Card → icon chip → trend Badge → uppercase label → large number). Confirmed by reading `dashboard/page.tsx`: KPI cards already compose `Card`/`CardContent`/`Badge` almost exactly like the official block. | N/A — mostly already done | Only real gap: the trend badges (`+12%`, `Estável`, `Urgente`, `+8%`) are **hardcoded strings**, not derived from `useDashboardKpis()` data (no delta/trend fields exist in the KPI payload). That's a data/business-logic gap, explicitly out of scope for a visual-only milestone — flag it as a known limitation, don't silently "fix" it under a UI refactor banner. |
| Loading skeleton for KPI cards / Atividade Recente / list tables while `isLoading` | Every list screen currently renders a bare `"A carregar..."` text string during loading (`isLoading ? <div>A carregar...</div> : ...` appears throughout Clientes/Processos/Pareceres/Documentos) | LOW | Official `Skeleton` component (on the missing-primitive list) is a trivial swap: `<Skeleton className="h-4 w-32" />` compositions matching each card/row shape. High visual-polish return for very low effort — good "quick win" candidate early in the module rollout. |
| `Empty` component for zero-result states | shadcn ships an official `Empty`/`EmptyHeader`/`EmptyMedia`/`EmptyTitle`/`EmptyDescription`/`EmptyContent` composition explicitly designed for "no data found in lists/tables," "notification centers with no items" — directly matches LexCV's many hand-rolled `"Nenhum ... registado."` one-line messages scattered across Clientes tabs, Processos tabs, Documentos, and the notification bell/page | LOW | Confirmed official component (not a recipe). Good candidate to standardize the ~10+ different "no data" messages currently written ad hoc as plain `<p>` tags. |

#### Notificações (bell + `/notificacoes` page)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Keep `Popover`-based bell — **do not switch to `DropdownMenu`** | Read `notification-bell.tsx` directly: it's **already built on shadcn's `Popover`/`PopoverTrigger`/`PopoverContent`** (already installed), not a hand-rolled dropdown. This is the *correct* choice already, not a gap: `DropdownMenu`'s `DropdownMenuItem` assumes simple `menuitem`-role entries and manages focus/typeahead accordingly — each notification row here has multiple independent interactive controls (mark-as-read button, snooze control, internal link), which is a known Radix/Base a11y anti-pattern inside `DropdownMenuItem`. `Popover` correctly leaves the internal content's semantics up to the app. | N/A — already correct | Table-stakes item here is *recognizing this is already right*, so the roadmap doesn't waste a phase "fixing" something that isn't broken. |
| Swap the manual unread-count `<span>` badge for the official `Badge` component | Currently a bespoke `absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full ...` span, functionally a badge but not using the `Badge` primitive already used everywhere else in the app | LOW | Purely cosmetic consistency win; trivial. |

#### Setup wizard / multi-step forms

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| shadcn's newer `<Field>`/`FieldLabel`/`FieldDescription`/`FieldError` composition (current official form-integration docs, `/docs/forms/react-hook-form`) as the presentational layer over the app's existing raw `react-hook-form` + `zod` usage | **Confirmed non-breaking**: shadcn's current docs explicitly frame `Field` as a purely presentational wrapper around `Controller`/`useForm`+`zodResolver` — "there's no wrapper abstraction requiring migration... you can adopt shadcn's Field components incrementally without refactoring existing form logic." LexCV's forms are 100% react-hook-form + zod already (`buildClienteFormSchema`, `zodResolver`, `useForm` throughout). | LOW-MEDIUM (per form, additive) | Note: shadcn's docs have moved away from the classic `Form`/`FormField`/`FormItem` naming I might otherwise assume from older training data — the current official surface is `Field`-based. Verify exact import names against the live CLI output at implementation time, not from memory. |
| `Progress` component for a simple linear "step X of N" indicator in `/setup` | Official primitive (on the missing-primitive list), simplest possible way to add a wizard-progress affordance | LOW | See Anti-Features for what NOT to reach for. |

#### Landing (`webpage/`)

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Mobile navigation menu for `SiteHeader` | Read `site-header.tsx` directly: nav links are `<nav className="hidden md:flex">` — **on mobile there is currently no navigation at all**, not even a hamburger fallback. This is a genuine, verifiable gap, not a style nitpick. | LOW-MEDIUM | LexCV's own `web/src/components/ui/sheet.tsx` already exists (built manually per Key Decisions log, "seguiu padrão de dialog.tsx com @radix-ui/react-dialog") — reuse it for a slide-in mobile nav instead of introducing anything new. Good "shared primitive across both apps" candidate if `webpage/` and `web/` end up sharing a `ui/` package, otherwise duplicate the same small file. |
| Keep `TrustSection`'s existing `Card`/`CardHeader`/`CardTitle`/`CardDescription` composition | Already idiomatic shadcn usage today — no change needed | N/A | Good reference example for how Hero/Contact sections should also be restructured (see Differentiators). |

---

### Differentiators (Nice Visual Upgrade, Optional)

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| Recharts-backed `Chart` (`ChartContainer`, `ChartConfig`, `ChartTooltip`, `ChartLegend`) for a Dashboard trend chart (e.g. an area chart of processos/honorários over time, mirroring the official `dashboard-01` block's `ChartAreaInteractive`) | Visually elevates the Dashboard from "cards + static list" to the same shape as shadcn's flagship dashboard block; shadcn explicitly does not lock you into an abstraction ("we do not wrap Recharts") | HIGH | **New dependency** (`recharts` — confirmed absent from `web/package.json` today). Needs a real time-series KPI endpoint from the backend (currently `useDashboardKpis()` returns point-in-time totals only, no history) — this is a genuine cross-layer dependency, likely too large for a "visual-only" milestone unless the backend already has the data. Flag as a strong candidate to **defer to a future milestone** rather than force into this one. |
| `ScrollArea` inside the notification bell's list (replacing the current `max-h-72 overflow-y-auto` on a plain `<ul>`) | Consistent cross-browser scrollbar styling | LOW | Cosmetic only; current native-scroll approach already works fine, so this is genuinely optional polish, not a fix. |
| Official `Combobox` (either the classic `Command`+`Popover` recipe, or the newer Base UI-powered `docs/components/base/combobox` with `ComboboxInput`/`ComboboxContent`/`ComboboxList`) for the "tipo de documento" free-text-or-pick field in Documentos Entregues upload | Today this is a raw `<input list="..."><datalist>` — functional and accessible, but visually inconsistent with the rest of the design system | MEDIUM | Two different official implementations exist depending on which primitive style (Radix-recipe vs Base UI component) the foundation phase settles on — confirm which is available once `-b radix` vs default is decided, since the Base UI-specific `/docs/components/base/combobox` variant is separate from the classic `Command`+`Popover` recipe. |
| `Breadcrumb` for ficha detail pages ("Clientes / [Nome do Cliente]", "Processos / [Número]") | Currently hand-built as a `<div>` with a `<Link>` + literal `/` character + current name (`clientes/[id]/page.tsx` header) — functional, but exactly the kind of ad hoc pattern that should become the official `Breadcrumb`/`BreadcrumbList`/`BreadcrumbItem`/`BreadcrumbLink`/`BreadcrumbPage`/`BreadcrumbSeparator` composition | LOW | Purely presentational upgrade of something that already works. |
| `Tooltip` on icon-only/collapsed-sidebar buttons | Accessibility/discoverability polish for any icon-only affordances (collapsed sidebar icons, icon-only row actions) | LOW | Requires `TooltipProvider` once at the root. |
| `Avatar` for advogado/administrativo pickers and any user-representing UI (Advogados/Administrativos cards in Clientes ficha, Testemunhas, notification "atribuído" flows) | Small but real visual upgrade over plain text names in lists | LOW | No functional change, cosmetic identity for "this row represents a person." |
| Restructure Hero/Contact sections in `webpage/` around `Card`/`Badge`/`Avatar`(for a testimonial-style trust element) composition, matching `TrustSection`'s already-good pattern | Visual consistency across all 4 landing sections (today Hero/Contact are plain `<section>`+`<div>` while Trust already uses `Card`) | LOW-MEDIUM | No official "Hero block" exists to copy (see cross-cutting finding) — this is genuinely original composition work using only atomic primitives, not a block-adoption task. |
| `NavigationMenu` for `webpage/`'s desktop nav (currently plain `<a>` anchor tags) | `NavigationMenu` is explicitly documented as being for marketing/website navigation (not app sidebars — see Anti-Features) — a legitimate, on-label use here, unlike in `web/`'s app shell | LOW-MEDIUM | Only worth it if a dropdown/mega-menu structure emerges; for 3 flat anchor links (`#funcionalidades`, `#confianca`, `#contacto`) plain styled links may remain simpler — judgment call, not obviously required. |

---

### Anti-Features (Don't Do This)

| Feature | Why It Seems Appealing | Why Problematic | Alternative |
|---------|------------------------|------------------|-------------|
| Adopting shadcn's official `Sidebar` block/component (`SidebarProvider`, `Sidebar`, `SidebarContent`, `SidebarMenu`, collapsible-icon mode) to replace `dashboard-shell.tsx`/`bottom-nav.tsx` | It's the "official" way to build an app shell in shadcn, and the milestone is about adopting official components | Confirmed via direct comparison: adopting it would require **replacing the current sidebar markup structure wholesale** and would conflict with the already-validated Figma-aligned sidebar/topbar design. PROJECT.md is explicit that this milestone is **not a redesign** ("Preservar identidade visual... redesenho estrutural do layout institucional" is out of scope). This is a textbook large-disruption-for-no-clear-benefit swap. | Keep `dashboard-shell.tsx`/`bottom-nav.tsx` exactly as they are; only spot-upgrade their *internals* with proper primitives where genuinely missing (e.g. `Tooltip` on collapsed icon buttons, `Sheet` already used correctly for mobile drawer). |
| Installing a third-party "shadcn Stepper" package/registry (ReUI, Shadcn Studio, allshadcn.com, shadcnblocks.com, etc.) for the Setup wizard | Multiple polished-looking multi-step-form component libraries exist and are heavily marketed as "shadcn stepper" | **Confirmed: shadcn/ui has no official Stepper/Steps/Wizard component** (verified against the full official components index — not present). Every "shadcn stepper" result is a third-party registry/community package, which directly violates PROJECT.md's explicit scope boundary ("apenas a CLI oficial `shadcn@latest`; fora de âmbito instalação de skills/pacotes externos não verificados"). | Hand-build the wizard's step indicator from official primitives already in scope: `Progress` for a simple linear bar, or a small custom "step pills" row (same visual idiom as the already-existing Clientes/Processos tab-toggle buttons) — no new dependency, no unverified package. |
| Replacing the already-correct `Popover`-based notification bell with `DropdownMenu` "for consistency with other menus" | `DropdownMenu` sounds like the more "correct" semantic component name for a bell menu | Would be a regression: `DropdownMenuItem` assumes single-action menu-item semantics and manages roving focus/typeahead on that assumption; the notification list has multiple independent interactive controls per row (mark-as-read, snooze, navigate) — a known accessibility anti-pattern for menu primitives | Leave the `Popover`-based implementation as-is; it's already the right primitive choice. |
| Adopting a general-purpose third-party "shadcn blocks" marketplace (shadcnblocks.com, shadcndesign.com, shadcnuikit.com) for the `webpage/` Hero/Features/Testimonials/Pricing sections | These sites have hundreds of polished, ready-made marketing sections that would visually "look like shadcn" | Confirmed these are **not part of `ui.shadcn.com`** — they're commercial/community registries outside the official CLI, explicitly excluded by PROJECT.md's scope boundary; they'd also introduce inconsistent code style/dependencies not vetted for this codebase | Compose landing sections from official atomic primitives only (`Button`, `Card`, `Badge`, `Avatar`, `Separator`, `NavigationMenu`) — more original work, but stays inside the explicit CLI-only constraint. |
| Adding row-selection checkboxes + bulk-action toolbar to every DataTable "because the recipe includes it" | The official DataTable recipe demonstrates row selection as a core feature, so it's tempting to include it everywhere for "completeness" | LexCV currently has **no bulk actions anywhere** (no bulk-delete clientes, no bulk-status-change processos, etc.) — shipping selection checkboxes with nothing to do with the selection is dead UI and scope creep beyond a visual refactor | Only add row-selection where a genuine bulk action exists today or is explicitly requested; otherwise ship DataTable without the selection column. |
| Migrating `webpage/`'s `SiteHeader` desktop nav or `web/`'s app sidebar links to `NavigationMenu` uniformly "since it's the official nav component" | Consistency instinct: use the same nav primitive everywhere | `NavigationMenu` is explicitly documented as designed for **website navigation with dropdown mega-menus**, not app sidebar/topbar navigation — using it inside `web/`'s dashboard shell would be a semantic misuse of the component, and the app shell is explicitly out of scope for restructuring anyway | Use `NavigationMenu` only inside `webpage/` (if/when a dropdown structure is actually needed); leave `web/`'s sidebar/topbar untouched. |
| Migrating the deprecated `Toast`/`@radix-ui/react-toast` to Sonner as a "quick foundation win" without checking `Toaster` placement across both apps | The call-site API match (`toast.success`, `toast.error`) makes it look trivially safe | Still requires removing `@radix-ui/react-toast` and its current `toast.tsx`/`toaster.tsx`, and re-mounting a new `<Toaster />` (from `sonner`) at the correct root layout(s) for **both** `web/` and (if used there) `webpage/` — a small foundation-scope task, not a zero-risk one; sequence it deliberately in the foundation phase, not as an incidental drive-by change inside an unrelated module phase | Treat as its own small, explicit foundation-phase item; verify `Toaster` mounts correctly in both apps' root layouts before touching call sites. |

---

## Feature Dependencies

```
[shadcn CLI init: `-b radix`]
    └──gates──> [All new primitives added via CLI keep `asChild` composition parity]
                    └──requires for──> [DataTable pattern] (Table + @tanstack/react-table + Checkbox + DropdownMenu + Input)
                    └──requires for──> [Tabs migration] (Tabs primitive)
                    └──requires for──> [Combobox] (Command + Popover, OR Base UI `base/combobox` — pick one, don't mix)
                    └──requires for──> [Mobile nav in webpage/] (reuses existing Sheet — no new primitive needed)

[Sonner adoption] ──replaces──> [Toast / @radix-ui/react-toast / toast.tsx / toaster.tsx]
    └──requires──> [Toaster re-mounted at root layout(s) of web/ (and webpage/ if used there)]

[Chart component] ──requires──> [recharts dependency] ──requires──> [Backend: time-series KPI endpoint]
    (currently absent; likely out of scope for a visual-only milestone — see Differentiators)

[Tabs migration: Clientes ficha] ──must ship together with──> [Tabs migration: Processos ficha]
    (both use the identical hand-rolled toggle-button pattern by deliberate design; migrating only one breaks the intentional visual consistency between them)

[DataTable pattern] ──enhances──> [Clientes list, Processos list, Pareceres list, Financeiro list, Documentos list]
    (build once as a shared pattern/recipe, then apply per screen — not 5 independent builds)

[NativeSelect swap] ──independent of──> [DataTable, Tabs, Chart]
    (can ship in parallel/early — lowest risk, no shared foundation dependency beyond CLI init itself)
```

### Dependency Notes

- **Everything in this document depends on the foundation phase's CLI init decision (`-b radix` vs default Base UI).** This is the single highest-leverage decision the Stack/Architecture researcher's foundation work makes — every component-level recommendation above assumes `-b radix` is chosen to preserve `asChild` parity with existing code. If the foundation phase chooses the new Base UI default instead, every "Complexity" rating above involving a *new* primitive (Tabs, DropdownMenu, Command, Checkbox, Select-family, Calendar, Breadcrumb, Accordion, NavigationMenu) should be reassessed upward, since it would introduce a second composition idiom into a codebase that currently has only one.
- **DataTable is the largest single lift and should be built once, generically, before being applied to 5 screens.** Treat "stand up the DataTable pattern" as its own roadmap phase/step, with the 5 screen-specific adoptions as smaller follow-on steps that reuse it.
- **Tabs migration only makes sense after Tabs exists in the foundation**, and must cover Clientes + Processos fichas together (see above) — don't split into two separate roadmap phases that could ship inconsistently.
- **Chart conflicts with "visual-only" milestone framing** if it requires new backend history data — flag this dependency explicitly to whoever scopes the roadmap so it isn't silently promised as an easy KPI upgrade.

---

## MVP Definition (for this milestone)

### Launch With (Foundation — must exist before any module work)

- [ ] `shadcn init` run for real in both `web/` and `webpage/` (`components.json` created), with the `-b radix` flag — preserves 100% composition-pattern parity with existing Radix-based primitives and the pervasive `asChild` usage already in the codebase
- [ ] Missing primitives added via CLI: `Select`, `NativeSelect`, `Tabs`, `DropdownMenu`, `Command`, `Tooltip`, `Checkbox`, `Avatar`, `Separator`, `Skeleton`, `Progress`, `Calendar`, `Breadcrumb`, `Accordion`, `NavigationMenu`, `Empty` — matches PROJECT.md's own list plus `Empty` (a genuinely good fit found this session, not on the original list)
- [ ] `@tanstack/react-table` added as a dependency; one shared DataTable recipe (`columns.tsx` pattern + `data-table.tsx` + toolbar/pagination helpers) built once
- [ ] Sonner swap-in for the deprecated Toast (own small foundation item, sequenced deliberately — see Anti-Features)

### Add After Foundation (per-module rollout, within this milestone)

- [ ] Clientes/Processos/Pareceres/Financeiro/Documentos lists migrated onto the shared DataTable pattern (sort + filter toolbar + official `Pagination`)
- [ ] Clientes ficha + Processos ficha both migrated from toggle-`Button` tabs to real `Tabs` (single combined roadmap item, not two)
- [ ] `NativeSelect`/`Select` swap for all raw `<select className={selectClassName}>` instances across Clientes/Processos/Setup forms
- [ ] `Skeleton` loading states + `Empty` zero-result states standardized across all list/tab screens
- [ ] `webpage/` mobile nav (reusing existing `Sheet`) + Hero/Contact restructured around `Card`/`Badge` composition to match `TrustSection`

### Future Consideration (explicitly defer)

- [ ] Recharts-backed `Chart` for Dashboard trend visualization — blocked on backend time-series KPI data, not just a frontend component swap
- [ ] Real trend-delta computation for KPI badges (`+12%` etc. are currently hardcoded) — business-logic/backend work, not visual refactor
- [ ] `NavigationMenu` mega-menu structure for `webpage/` — only worth it if nav grows beyond 3 flat anchor links
- [ ] Bulk row-selection/actions on any DataTable — no bulk actions exist in the product today; don't invent UI for a capability that doesn't exist

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|----------------------|----------|
| CLI init with `-b radix` + missing primitives | HIGH (unblocks everything else) | LOW | P1 |
| Shared DataTable pattern (build once) | HIGH | MEDIUM | P1 |
| DataTable adoption × 5 screens | HIGH | MEDIUM (per screen, after pattern exists) | P1 |
| Tabs migration (Clientes + Processos fichas, combined) | HIGH (real a11y fix) | MEDIUM | P1 |
| `NativeSelect`/`Select` swap on raw `<select>` | MEDIUM | LOW | P1 |
| Official `Pagination` on `/notificacoes` and any other server-paginated list | MEDIUM | LOW | P1 |
| `Skeleton` + `Empty` standardization | MEDIUM (polish) | LOW | P2 |
| Sonner swap for deprecated Toast | LOW-MEDIUM (mostly invisible to users, real for maintainability) | LOW-MEDIUM | P2 |
| `Breadcrumb`, `Tooltip`, `Avatar` cosmetic swaps | LOW-MEDIUM | LOW | P2 |
| `webpage/` mobile nav via `Sheet` | HIGH (real functional gap on mobile today) | LOW-MEDIUM | P1 |
| `webpage/` Hero/Contact restructuring | MEDIUM (visual consistency) | LOW-MEDIUM | P2 |
| Recharts `Chart` for Dashboard | MEDIUM-HIGH (visual wow factor) | HIGH (new dependency + backend data) | P3 / deferred |
| `Combobox` for tipo-de-documento field | LOW (already functional today) | MEDIUM | P3 |

**Priority key:** P1: do in this milestone. P2: do in this milestone if time allows, otherwise fine to slip. P3: explicitly defer to a future milestone.

---

## Reference: Official shadcn Blocks vs LexCV's Current Implementation

| Pattern | Official shadcn reference | LexCV today | Gap |
|---------|---------------------------|--------------|-----|
| Dashboard KPI + chart + table layout | `dashboard-01` block: `SectionCards` (stat cards) + `ChartAreaInteractive` + `DataTable` | Stat cards already match `SectionCards` shape closely; no chart; table uses plain `Table`, no sort/filter | Chart is the only structurally missing piece (and it's the deferred one); table needs the DataTable upgrade |
| App shell (sidebar + topbar) | `sidebar-01`..`sidebar-16` blocks, `SidebarProvider`/`Sidebar`/`SidebarContent` | Custom `dashboard-shell.tsx` + `bottom-nav.tsx`, validated against Figma | Deliberately **not** adopting the official block (see Anti-Features) — this is correct, not a gap |
| Login/auth pages | `login-01`..`login-05` blocks (muted background, form+image split, etc.) | Not read this session (out of this question's scope) — flagged as a gap in coverage, not a finding | Low priority for this research pass; likely low-risk if revisited, since login is typically a single simple form |
| Marketing landing page (hero/features/testimonials/pricing) | **None exist officially** | Hand-built Hero/Features/Trust/Contact in `webpage/` | Not a gap against shadcn (nothing to copy) — the work here is original composition from primitives, not block-adoption |

---

## Sources

**Official shadcn/ui documentation (fetched live this session, July 2026 — HIGH confidence):**
- [Data Table](https://ui.shadcn.com/docs/components/data-table) — recipe pattern, not an installable component; requires `@tanstack/react-table`
- [Tabs](https://ui.shadcn.com/docs/components/tabs) — API, Radix-based accessibility
- [Chart](https://ui.shadcn.com/docs/components/chart) — Recharts wrapper, `ChartContainer`/`ChartConfig`
- [Dropdown Menu](https://ui.shadcn.com/docs/components/dropdown-menu)
- [Forms (React Hook Form)](https://ui.shadcn.com/docs/forms/react-hook-form) — current `Field`-based composition, non-breaking over raw RHF+Zod
- [Pagination](https://ui.shadcn.com/docs/components/pagination)
- [Combobox](https://ui.shadcn.com/docs/components/combobox) and [Base UI Combobox](https://ui.shadcn.com/docs/components/base/combobox)
- [Command](https://ui.shadcn.com/docs/components/command)
- [Select](https://ui.shadcn.com/docs/components/select)
- [Native Select](https://ui.shadcn.com/docs/components/native-select)
- [Calendar](https://ui.shadcn.com/docs/components/calendar)
- [Toast (deprecated)](https://ui.shadcn.com/docs/components/toast) → [Sonner](https://ui.shadcn.com/docs/components/sonner)
- [Skeleton](https://ui.shadcn.com/docs/components/skeleton)
- [Empty](https://ui.shadcn.com/docs/components/empty)
- [Scroll Area](https://ui.shadcn.com/docs/components/scroll-area)
- [Breadcrumb](https://ui.shadcn.com/docs/components/breadcrumb)
- [Accordion](https://ui.shadcn.com/docs/components/accordion)
- [Tooltip](https://ui.shadcn.com/docs/components/tooltip)
- [Navigation Menu](https://ui.shadcn.com/docs/components/navigation-menu)
- [Sidebar](https://ui.shadcn.com/docs/components/sidebar)
- [Blocks index](https://ui.shadcn.com/blocks) — confirmed app/dashboard-only categories
- [Dashboard block](https://ui.shadcn.com/blocks/dashboard)
- [Components index](https://ui.shadcn.com/docs/components) — full official component inventory, confirms no Stepper/Steps/Wizard
- [July 2026 changelog: Base UI as default](https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default) — CLI default change, `-b radix` flag, `asChild`→`render`, `skills add shadcn/ui` migration tool

**Community/third-party (used only to confirm absence of an official equivalent — LOW confidence as sources, not used as recommendations):**
- [shadcn-ui/ui Discussion #3263: Multi-step form block request](https://github.com/shadcn-ui/ui/discussions/3263) and [#6353](https://github.com/shadcn-ui/ui/discussions/6353) — confirms Stepper is a repeatedly-requested but never-shipped official block
- shadcnblocks.com, shadcndesign.com, shadcnuikit.com, shadcnstudio.com — third-party marketing block/stepper registries, cited only to demonstrate they are *not* part of the official CLI (out of scope per PROJECT.md)
- [radix-ui/primitives #855, #1155, #2359](https://github.com/radix-ui/primitives) and [mui/base-ui #4822](https://github.com/mui/base-ui/issues/4822) — used to verify default mount/unmount behavior of Tabs content in both Radix and Base UI (both unmount inactive panels by default, matching LexCV's current ternary-based tab content pattern)

**LexCV source files read directly this session (grounding, not shadcn docs):**
- `.planning/PROJECT.md` — milestone scope, explicit out-of-scope decisions, prior Key Decisions log
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` — manual tab pattern, raw `<select>`/`<textarea>` styling, Dialog-based CRUD pattern, `toast.success`/`toast.error` call-site shape
- `web/src/app/(dashboard)/dashboard/page.tsx` — KPI stat card structure, hardcoded trend badges
- `web/src/components/shared/notification-bell.tsx` — confirmed already `Popover`-based, manual badge/scroll
- `webpage/src/components/hero-section.tsx`, `trust-section.tsx`, `contact-section.tsx`, `site-header.tsx` — confirmed no mobile nav, confirmed `TrustSection` already uses `Card` well
- `web/package.json` — confirmed current Radix packages (`@radix-ui/react-dialog`, `-alert-dialog`, `-popover`, `-radio-group`, `-slot`, `-switch`, `-toast`), confirmed absence of `@tanstack/react-table`, `recharts`, `cmdk`, `react-day-picker`, `sonner`
- `web/src/components/ui/` directory listing — confirmed exactly which primitives already exist (alert-dialog, badge, button, card, dialog, input, label, popover, radio-group, sheet, switch, table, textarea, toast, toaster)
- `webpage/src/components/ui/` directory listing — confirmed only `button.tsx`/`card.tsx` exist there today

---
*Feature research for: shadcn/ui UI/UX refactor milestone (LexCV v2.13)*
*Researched: 2026-07-15*
