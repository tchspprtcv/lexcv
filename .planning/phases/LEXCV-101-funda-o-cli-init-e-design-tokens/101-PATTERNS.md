# Phase 101: Fundação — CLI Init e Design Tokens - Pattern Map

**Mapped:** 2026-07-15
**Files analyzed:** 27 (2 config, 2 CSS, 2 package.json, ~16 new primitives, 6 toast→Sonner files, 1 hook)
**Analogs found:** 21 / 27 (exact/role-match) — 6 have no direct analog (net-new component types)

## Scope note

This phase is CLI-driven scaffolding, not hand-authoring from scratch. `shadcn init`/`add` generates most new files' *initial* content from the official registry — the "pattern to copy" here is less "port this logic" and more "match these structural conventions the CLI must land on, and these exact values that must survive/be set deliberately." Every existing hand-rolled primitive in `web/src/components/ui/*` already follows the current shadcn source conventions (`data-slot` attributes, CVA, `cn()`), so the primary job for the executor is: (1) restore/set the token values researched in UI-SPEC.md immediately after `init`, (2) verify newly `add`-ed files land in the same `data-slot`/CVA idiom already established, (3) preserve the exact toast copy contract through the Sonner swap.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `web/components.json` | config | n/a (static config) | *(none exists yet)* — target shape per `STACK.md` lines 122-143 | none (first-run) |
| `webpage/components.json` | config | n/a (static config) | `web/components.json` (must be hand-copied, not re-wizarded — Pitfall 5) | exact (once `web/` is done) |
| `web/src/app/globals.css` | config/style-tokens | transform (CSS cascade) | itself (existing file, additive merge) | exact |
| `webpage/src/app/globals.css` | config/style-tokens | transform (CSS cascade) | `web/src/app/globals.css` (byte-identical today, keep in lockstep) | exact |
| `web/package.json` | config | n/a | itself (existing deps block) | exact |
| `webpage/package.json` | config | n/a | itself (existing deps block) | exact |
| `web/src/components/ui/select.tsx` | component | event-driven | `web/src/components/ui/dialog.tsx` (Portal + Content + `data-[state]` animation idiom) | role-match |
| `web/src/components/ui/native-select.tsx` | component | request-response (form field) | ad hoc `selectClassName` native `<select>` in `web/src/app/(dashboard)/clientes/novo/page.tsx` lines 31-32, 236-247 | exact |
| `web/src/components/ui/tabs.tsx` | component | event-driven | `web/src/components/ui/radio-group.tsx` (controlled single-selection, `data-[state=checked]`) | role-match |
| `web/src/components/ui/dropdown-menu.tsx` | component | event-driven | `web/src/components/ui/popover.tsx` (Portal + Trigger + Content, align/sideOffset) | role-match |
| `web/src/components/ui/command.tsx` | component | event-driven | *(none — `cmdk`-based, no existing search/palette component)* | none |
| `web/src/components/ui/tooltip.tsx` | component | event-driven | `web/src/components/ui/popover.tsx` (Provider/Trigger/Content shape) | role-match |
| `web/src/components/ui/checkbox.tsx` | component | event-driven | `web/src/components/ui/switch.tsx` (Radix Root+Thumb/Indicator, `data-[state=checked]`) | role-match |
| `web/src/components/ui/avatar.tsx` | component | transform | `web/src/components/ui/card.tsx` (plain `data-slot` div, `cn()` only, no variants) | role-match |
| `web/src/components/ui/separator.tsx` | component | transform | `web/src/components/ui/card.tsx` (plain `data-slot` div) | role-match |
| `web/src/components/ui/skeleton.tsx` | component | transform | `web/src/components/ui/badge.tsx` (simple `cva`-free / `cn()`-merged div) | role-match |
| `web/src/components/ui/progress.tsx` | component | transform | `web/src/components/ui/switch.tsx` (Radix Root+Indicator with `data-state`) | role-match |
| `web/src/components/ui/calendar.tsx` | component | event-driven | `web/src/components/ui/button.tsx` (day-cell buttons reuse `buttonVariants`) | partial |
| `web/src/components/ui/breadcrumb.tsx` | component | transform | *(none — no existing nav/breadcrumb list component)* | none |
| `web/src/components/ui/accordion.tsx` | component | event-driven | `web/src/components/ui/alert-dialog.tsx` (Radix compound, `data-[state]` animate-in/out) | role-match |
| `web/src/components/ui/navigation-menu.tsx` | component | event-driven | *(none — `dashboard-shell.tsx`'s sidebar/topbar nav is hand-rolled, not Radix, and is out of scope this phase)* | none |
| `web/src/components/ui/empty.tsx` | component | transform | ad hoc empty-state markup in `web/src/app/(dashboard)/pareceres/page.tsx` lines 385-403 | partial |
| `web/src/components/ui/form.tsx` *(if added per STACK.md; not in UI-SPEC's 15-item list — verify against CONTEXT.md scope before building)* | component | event-driven | react-hook-form usage in `clientes/novo/page.tsx` (manual `register`/`formState.errors`, no `FormField` wrapper yet) | partial |
| `web/src/components/ui/sonner.tsx` | component/provider | event-driven | `web/src/components/ui/toaster.tsx` (the file it replaces — same mount-once-near-root shape) | exact (predecessor) |
| `webpage/src/components/ui/sonner.tsx` | component/provider | event-driven | `web/src/components/ui/toaster.tsx` (copied pattern; `webpage/` has no toast today) | role-match |
| `web/src/hooks/use-toast.ts` | hook | event-driven | itself — must keep `toast.success`/`toast.error` API (lines 219-224) while internals delegate to `sonner` | exact (contract-preserving) |
| `web/src/app/layout.tsx` | layout | event-driven (provider mount) | itself — `<Toaster />` import swaps source (line 5, 36) | exact |
| `webpage/src/app/layout.tsx` | layout | event-driven (provider mount) | `web/src/app/layout.tsx`'s `<Toaster />` mount (webpage has none yet, lines 1-27) | role-match |
| `web/src/components/ui/toast.tsx` (superseded) | component | event-driven | n/a — removed once `sonner` replaces `@radix-ui/react-toast` (per FND-08 / STACK.md) | n/a (deletion) |
| `web/src/components/ui/toaster.tsx` (superseded) | component | event-driven | n/a — removed, replaced by `sonner.tsx` | n/a (deletion) |

## Pattern Assignments

### `web/components.json` / `webpage/components.json` (config)

**No existing analog** — neither app has a `components.json` today (verified absent). Use the exact shape researched in `STACK.md` (this is the CLI's own expected/generated shape for this repo's setup, not invented):

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

Critical values (per CONTEXT.md decisions + PITFALLS.md):
- `"tailwind": { "config": "" }` — **must stay empty string**, never let a `tailwind.config.{js,ts}` get created (Pitfall 4, both apps are Tailwind v4 CSS-first, verified zero `tailwind.config.*` exist today).
- Run `init -b radix` explicitly (never plain `init`, which now defaults to Base UI per STACK.md's "Critical timing note").
- `web/` first; `webpage/components.json` is a **manual copy** of `web/`'s resolved answers, not a second wizard run (CONTEXT.md decision, Pitfall 5). Diff both files field-by-field (`style`, `baseColor`, `cssVariables`, `iconLibrary`, `aliases`) before considering this done.
- `--dry-run` before the real `init` (CONTEXT.md decision) to confirm only one `:root`/`.dark` block would result.

Both apps' `tsconfig.json` (`web/tsconfig.json` lines 21-23, `webpage/tsconfig.json` lines 21-23) already define `"@/*": ["./src/*"]` — matches the default aliases above with zero remapping needed.

---

### `web/src/app/globals.css` / `webpage/src/app/globals.css` (config/style-tokens, transform)

**Analog:** itself — current content (both apps byte-identical):

```css
@import "tailwindcss";
@plugin "tailwindcss-animate";

@custom-variant dark (&:is(.dark *));

@theme inline {
  --color-background: var(--background);
  --color-foreground: var(--foreground);
  --font-sans: var(--font-geist-sans);
  --font-mono: var(--font-geist-mono);
}

:root {
  --background: #f8fafc;
  --foreground: #020617;
}

.dark {
  --background: #020617;
  --foreground: #f8fafc;
}

body {
  background: var(--background);
  color: var(--foreground);
  font-family: var(--font-sans), Arial, Helvetica, sans-serif;
}
```

**Required post-`init` restoration** (PITFALLS.md Pitfall 1, ARCHITECTURE.md Pattern 1/3, UI-SPEC.md Color section — exact hex values, not invented):

```css
:root {
  --background: #f8fafc;   /* restore if CLI's neutral baseColor overwrote this */
  --foreground: #020617;
  --radius: 0rem;          /* deliberate sharp-corner identity, not CLI's rounded default */
  --primary: #2563eb;      /* institutional blue-600, NOT the default Button variant color */
  --primary-foreground: #ffffff;
}
.dark {
  --background: #020617;
  --foreground: #f8fafc;
  --primary: #3b82f6;      /* blue-500 dark-mode tone */
}
```

**Also required (`@plugin` → `@import` swap, FND-06):**
```css
/* Replace: */
@plugin "tailwindcss-animate";
/* With: */
@import "tw-animate-css";
```

**Verification checklist per file:** exactly one `:root` block and one `.dark` block after merge (`git diff` review, not two silently overlapping ones); every *other* new semantic token the CLI adds (`--secondary`, `--muted`, `--accent`, `--border`, `--input`, `--ring`, `--card`, `--popover`) is net-new and can keep the `neutral` baseColor defaults — do not hand-tune those.

---

### `web/package.json` / `webpage/package.json` (config)

**Analog:** itself — current dependency block conventions (`web/package.json` lines 12-34, `webpage/package.json` lines 11-22). Both already list Radix packages as flat `^x.y.z` entries and no dev-only CSS plugin wrapper beyond `tailwindcss`.

**Changes this phase:**
- Remove: `"tailwindcss-animate": "^1.0.7"` (both apps)
- Add: `"tw-animate-css": "1.4.0"`, `"sonner"` (both apps — `webpage/` currently has zero toast library, this is its first)
- Add per new primitive: `@radix-ui/react-select`, `-tabs`, `-dropdown-menu`, `-tooltip`, `-checkbox`, `-avatar`, `-separator`, `-progress`, `-accordion`, `-navigation-menu`, `cmdk`, `react-day-picker`, `date-fns` (`web/` only, unless `webpage/` also gets primitives added — see UI-SPEC.md/ARCHITECTURE.md, `webpage/`'s primitive need is near-zero this phase)
- **Immediately re-pin** `"react-day-picker": "9.14.0"` after `shadcn add calendar` (registry resolves to broken `@latest` v10 — STACK.md/PITFALLS.md, confirmed open upstream issue #10914)
- Per CONTEXT.md/PITFALLS.md Pitfall 3: run `shadcn migrate radix` — this will replace the 8 existing scoped `@radix-ui/react-*` deps' *usages* with the unified `radix-ui` package; verify `package.json` doesn't end this phase with both `radix-ui` and now-unused scoped entries lingering (prune via `pnpm dedupe`/manual removal).

---

### New Radix overlay primitives: `select.tsx`, `dropdown-menu.tsx`, `tooltip.tsx` (component, event-driven)

**Analog:** `web/src/components/ui/dialog.tsx` (compound Portal pattern) and `web/src/components/ui/popover.tsx` (simpler Trigger+Content pattern)

**Imports pattern** (`dialog.tsx` lines 1-7):
```typescript
"use client";

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";

import { cn } from "@/lib/utils";
```
Note: per Pitfall 3, newly CLI-added components will import from the unified `radix-ui` package instead (`import { Dialog as DialogPrimitive } from "radix-ui"`), not the scoped `@radix-ui/react-dialog` shown above — that's expected and correct for net-new files.

**Portal + overlay + animation pattern** (`dialog.tsx` lines 14-28, `popover.tsx` lines 12-34):
```typescript
function DialogOverlay({ className, ...props }: React.ComponentProps<typeof DialogPrimitive.Overlay>) {
  return (
    <DialogPrimitive.Overlay
      data-slot="dialog-overlay"
      className={cn(
        "fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
        className,
      )}
      {...props}
    />
  );
}
```
```typescript
// popover.tsx — simpler Trigger+Portal+Content shape (closer match for Select/DropdownMenu/Tooltip)
const PopoverContent = React.forwardRef<...>(({ className, align = "end", sideOffset = 8, ...props }, ref) => (
  <PopoverPrimitive.Portal>
    <PopoverPrimitive.Content
      ref={ref}
      align={align}
      sideOffset={sideOffset}
      className={cn(
        "z-50 rounded-md border border-slate-200 bg-white shadow-md outline-none dark:border-slate-800 dark:bg-slate-950",
        "data-[state=open]:animate-in data-[state=closed]:animate-out",
        "data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
        "data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95",
        "data-[side=bottom]:slide-in-from-top-2 data-[side=left]:slide-in-from-right-2",
        "data-[side=right]:slide-in-from-left-2 data-[side=top]:slide-in-from-bottom-2",
        className,
      )}
      {...props}
    />
  </PopoverPrimitive.Portal>
));
```

**Close-button a11y pattern to preserve** (`dialog.tsx` lines 47-50 — PITFALLS.md Pitfall 8, keep on any new component with a dismiss affordance):
```typescript
<DialogPrimitive.Close className="...">
  <X className="h-4 w-4" />
  <span className="sr-only">Fechar</span>
</DialogPrimitive.Close>
```

**Radius/rounding note:** current hand-rolled overlays hardcode `rounded-none` (see `dialog.tsx` line 41, `alert-dialog.tsx` line 39). New CLI-scaffolded overlays will use `rounded-md`/`rounded-lg` by default unless the `--radius: 0rem` token (set above) is honored via the semantic `rounded-*` utility classes shadcn generates — do not hand-hardcode `rounded-none` into new files; let the token drive it.

---

### `select.tsx` — also cross-reference `native-select.tsx` (component, request-response)

**Analog (exact):** ad hoc native `<select>` pattern already used throughout the app, e.g. `web/src/app/(dashboard)/clientes/novo/page.tsx`:

**Shared className constant** (lines 31-32):
```typescript
const selectClassName =
  "flex h-9 w-full rounded-none border border-neutral-200 bg-white px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 disabled:cursor-not-allowed disabled:opacity-50 dark:border-neutral-800 dark:bg-neutral-950 dark:focus-visible:ring-neutral-300";
```

**Usage pattern** (lines 236-247):
```typescript
<select
  id="documento_tipo"
  className={selectClassName}
  {...form.register("documento_tipo")}
>
  <option value="">Nenhum</option>
  {options.map((opt) => (
    <option key={opt.value} value={opt.value}>
      {opt.label}
    </option>
  ))}
</select>
```

This exact pattern (native `<select>` + `selectClassName` string, `react-hook-form` `register()`) is repeated across 19 files (`clientes/[id]`, `notificacoes`, `agenda`, `clientes/merge`, `processos/[id]`, `processos/novo`, `financeiro`, `clientes/page`, `pareceres/[id]`, `clientes/novo`, `documentos/novo`, `pareceres`, `pareceres/nova`, `financeiro/novo`, `processos/page`, `agenda/novo`, `agenda/[id]/editar`, `processos/[id]/editar`, and `schemas/processos.ts`). `native-select.tsx` should formalize exactly this into a reusable primitive with the same `focus-visible:ring-*`/`disabled:*` classes, `rounded-none`→token-driven radius, and `data-slot="native-select"`. Do not touch the 19 call sites in this phase — Foundation only adds the primitive file.

---

### `checkbox.tsx`, `progress.tsx` (component, event-driven) — Radix Root+Indicator shape

**Analog:** `web/src/components/ui/switch.tsx` (full file, 27 lines):
```typescript
"use client";

import * as React from "react";
import * as SwitchPrimitive from "@radix-ui/react-switch";

import { cn } from "@/lib/utils";

export function Switch({ className, ...props }: React.ComponentProps<typeof SwitchPrimitive.Root>) {
  return (
    <SwitchPrimitive.Root
      data-slot="switch"
      className={cn(
        "peer inline-flex h-5 w-9 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 focus-visible:ring-offset-2 focus-visible:ring-offset-white disabled:cursor-not-allowed disabled:opacity-50 data-[state=checked]:bg-neutral-900 data-[state=unchecked]:bg-neutral-200 dark:focus-visible:ring-neutral-300 dark:focus-visible:ring-offset-neutral-950 dark:data-[state=checked]:bg-neutral-50 dark:data-[state=unchecked]:bg-neutral-800",
        className,
      )}
      {...props}
    >
      <SwitchPrimitive.Thumb
        data-slot="switch-thumb"
        className="pointer-events-none block h-4 w-4 rounded-full bg-white shadow-lg ring-0 transition-transform data-[state=checked]:translate-x-4 data-[state=unchecked]:translate-x-0 dark:bg-neutral-950"
      />
    </SwitchPrimitive.Root>
  );
}
```
Also see `radio-group.tsx` (39 lines) for the sibling `Root`+`Item`+`Indicator` variant of the same idiom (controlled single-choice, `data-[state=checked]` styling on the item itself rather than a thumb). `Checkbox`/`Progress` should follow whichever of these two shapes their Radix primitive matches (`Checkbox` → Root+Indicator like `RadioGroupItem`; `Progress` → Root+Indicator-as-fill-bar like `Switch`'s Thumb).

---

### `avatar.tsx`, `separator.tsx`, `skeleton.tsx` (component, transform — pure presentational)

**Analog:** `web/src/components/ui/card.tsx` (simplest `data-slot` pattern, no CVA, no Radix):
```typescript
export function Card({ className, ...props }: React.ComponentProps<"div">) {
  return (
    <div
      data-slot="card"
      className={cn(
        "rounded-none border border-slate-200 bg-white text-slate-950 shadow-sm transition-all hover:shadow-md dark:border-slate-800 dark:bg-[#020617] dark:text-slate-50",
        className,
      )}
      {...props}
    />
  );
}
```
`Skeleton` in particular should replace the ad hoc `animate-pulse` divs noted in ARCHITECTURE.md's Dashboard scaling row — not touched this phase, but the primitive should be built ready for that later swap. `Avatar` (Radix-backed, has Root/Image/Fallback) is closer to the `switch.tsx`/`radio-group.tsx` compound shape than to plain `card.tsx`; `Separator` and `Skeleton` are true zero-Radix presentational divs matching `card.tsx` exactly.

---

### `accordion.tsx` (component, event-driven)

**Analog:** `web/src/components/ui/alert-dialog.tsx` (full compound pattern with `data-[state]` animate-in/out, 138 lines) — same Radix compound-primitive-with-animation idiom applies (`AlertDialogContent` lines 28-48 show the `data-[state=open]:animate-in data-[state=closed]:animate-out` convention to reuse for `AccordionContent`).

---

### `command.tsx`, `breadcrumb.tsx`, `navigation-menu.tsx` (component) — no analog

These three have **no existing counterpart** in either app:
- `command.tsx` wraps `cmdk` (not Radix) — nothing in `web/src/components/ui/*` uses a non-Radix headless library today. Treat as a genuinely new pattern; follow the shadcn registry output's own conventions directly since there's no local precedent to reconcile against. Per PITFALLS.md Pitfall 8, this is one of the components warranting a first-time keyboard/ARIA smoke test (no existing Radix-backed baseline to fall back on).
- `breadcrumb.tsx` — no nav/breadcrumb list exists; `dashboard-shell.tsx`'s sidebar/topbar (hardcoded `slate-*`/`blue-*`, out of scope this phase) is the only nav-adjacent code and is not a usable analog for a `<nav aria-label="breadcrumb">` list component.
- `navigation-menu.tsx` — same story; likely only needed in `webpage/` (landing nav), which currently has no nav component at all (only `button.tsx`/`card.tsx` hand-rolled).

---

### `empty.tsx` (component, transform)

**Analog (partial):** ad hoc empty-state markup in `web/src/app/(dashboard)/pareceres/page.tsx` lines 385-403:
```typescript
<div className="p-6 text-sm text-slate-500">
  {searchActive ? (
    <>
      <p className="font-medium text-slate-700 dark:text-slate-300">
        Nenhum resultado encontrado
      </p>
      <p className="mt-1">
        Não foram encontrados pareceres para os critérios indicados. Tente ajustar o texto ou os
        filtros de pesquisa.
      </p>
    </>
  ) : (
    <>
      <p className="font-medium text-slate-700 dark:text-slate-300">
        Nenhuma solicitação de parecer encontrada
      </p>
      <p className="mt-1">Ajuste os filtros ou aguarde a criação de novas solicitações.</p>
    </>
  )}
</div>
```
This is the closest existing "shape" (heading + body text, conditional copy) that a formal `Empty`/`EmptyTitle`/`EmptyDescription` primitive should be able to express, but it is not itself a reusable component — treat as inspiration for the primitive's default typography (matches Typography contract: `font-medium` heading over `text-sm` body), not a file to modify this phase.

---

### Toast → Sonner swap (shared pattern, event-driven)

**Analog:** `web/src/components/ui/toaster.tsx` (the file being replaced, full 41 lines) — mounts `<ToastProvider>` + maps `toasts` to `<Toast>` elements with a variant-driven icon (`CheckCircle2`/`AlertCircle`).

**Contract that MUST survive unchanged** (`web/src/hooks/use-toast.ts` lines 219-224 — this is the load-bearing part, per UI-SPEC.md Copywriting Contract and FND-08):
```typescript
// Utilitários de chamada simplificados (Sucesso e Erro)
toast.success = (message: React.ReactNode, options?: Partial<Toast>) =>
  toast({ title: "Sucesso", description: message, variant: "default", ...options });

toast.error = (message: React.ReactNode, options?: Partial<Toast>) =>
  toast({ title: "Erro", description: message, variant: "destructive", ...options });
```
All 29 existing `toast.success(...)`/`toast.error(...)` call sites across the app must keep compiling and rendering with title **"Sucesso"** / **"Erro"** respectively after this file's internals are rewired onto `sonner`'s `toast()`/`<Toaster />` API instead of `@radix-ui/react-toast`. The safest approach (implied by "must continue to compile") is keeping `@/hooks/use-toast`'s public shape (`useToast()`, `toast`, `toast.success`, `toast.error`) as a thin wrapper delegating to `sonner`, rather than touching any of the 29 call sites.

**Layout mount pattern to replicate into `webpage/`** (`web/src/app/layout.tsx` lines 1-41, Toaster import at line 5, mount at line 36):
```typescript
import { Toaster } from "@/components/ui/toaster";
...
<Providers>
  {children}
  <Toaster />
</Providers>
```
`webpage/src/app/layout.tsx` (currently 27 lines, no `Toaster` at all — confirmed via grep, zero matches) needs this same import+mount pair added for the first time, using its own new `webpage/src/components/ui/sonner.tsx` (`webpage/` gets no legacy `toast.tsx`/`toaster.tsx` to migrate from, since it never had one).

**Toast variant/color contract to preserve visually** (`web/src/components/ui/toaster.tsx` lines 22-24, `toast.tsx` lines 27-41 — success = emerald left-border/icon, destructive = red left-border/icon): the new `sonner.tsx` wrapper (shadcn's official `sonner` component, themed via `next-themes`) should be configured so `toast.success`/`toast.error` still render with an equivalent success(green)/destructive(red) visual distinction, since that's part of the existing UX contract even though it isn't literal copy.

---

## Shared Patterns

### `data-slot` + `cn()` convention (apply to every new primitive)
**Source:** `web/src/components/ui/button.tsx` line 44, `card.tsx` lines 8/21/31/41/49/54, `switch.tsx` lines 14/22
```typescript
<Comp
  className={cn(buttonVariants({ variant, size, className }))}
  data-slot="button"
  {...props}
/>
```
Every hand-rolled component tags its root element `data-slot="<component-name>"`. New CLI-scaffolded components already do this by default (current shadcn source convention) — verify it's present, don't strip it.

### CVA variant pattern (apply to any new primitive with visual variants: select trigger sizes, badge-adjacent, progress states)
**Source:** `web/src/components/ui/button.tsx` lines 7-31, `badge.tsx` lines 6-29
```typescript
const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium ...",
  {
    variants: { variant: { default: "...", secondary: "...", outline: "...", ghost: "...", link: "..." }, size: { default: "...", sm: "...", lg: "...", icon: "..." } },
    defaultVariants: { variant: "default", size: "default" },
  },
);
```

### Radix compound overlay pattern (apply to select, dropdown-menu, tooltip, accordion, calendar's internal popover-like pieces)
**Source:** `web/src/components/ui/dialog.tsx` lines 14-54, `alert-dialog.tsx` lines 12-48 — `Portal` wrapping `Overlay` + `Content`, `data-[state=open]:animate-in data-[state=closed]:animate-out` + `fade-in-0`/`fade-out-0`/`zoom-in-95`/`zoom-out-95` class vocabulary (all provided by `tw-animate-css` after the plugin swap — verify the animation utility names still resolve post-swap).

### Sharp-corner identity (`--radius: 0rem`)
**Source:** `card.tsx` line 10, `dialog.tsx` line 41, `alert-dialog.tsx` line 39 — all hardcode `rounded-none` today. Once the `--radius: 0rem` token exists (see globals.css section above), new CLI-scaffolded components using semantic `rounded-*` classes will automatically render sharp-cornered without per-component hardcoding — this is the mechanism that prevents the "some rounded, some sharp" drift PITFALLS.md warns about.

### Institutional accent color — reserved list (do not apply broadly)
**Source:** UI-SPEC.md Color section — `--primary: #2563eb` is reserved for: sidebar active-nav indicator/icon/label tint, user-avatar gradient badge, global search focus ring, Dashboard KPI positive/urgent highlighting. **Not** the default `Button` variant (stays `bg-neutral-900`/`bg-neutral-100`, per `button.tsx` lines 12-13 — do not let the CLI's semantic `bg-primary` default color leak into `Button`'s `default` variant if/when that file is ever reconciled in Phase 102).

### Toast copy contract
**Source:** `web/src/hooks/use-toast.ts` lines 219-224 — `"Sucesso"` / `"Erro"` titles, must survive the Sonner swap verbatim (see Toast → Sonner section above).

## No Analog Found

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `web/src/components/ui/command.tsx` | component | event-driven | `cmdk`-based; no non-Radix headless library used anywhere in this codebase today |
| `web/src/components/ui/breadcrumb.tsx` | component | transform | No nav/breadcrumb list component exists in either app |
| `web/src/components/ui/navigation-menu.tsx` | component | event-driven | No Radix nav component exists; `dashboard-shell.tsx`'s hand-rolled sidebar/topbar is out of scope and structurally unrelated (not Radix-based) |
| `web/components.json` / `webpage/components.json` | config | n/a | First-ever `components.json` in either app — use STACK.md's researched target shape instead of a local analog |

Partial-analog files worth flagging to the planner (weak but real precedent, listed above in Pattern Assignments, not repeated here): `calendar.tsx` (button.tsx day-cell reuse only), `empty.tsx` (pareceres.tsx ad hoc empty state), `form.tsx` (manual react-hook-form usage, no wrapper yet — and note this file isn't in UI-SPEC's explicit 15-primitive list, only in STACK.md's research; confirm with CONTEXT.md scope before building it).

## Metadata

**Analog search scope:** `web/src/components/ui/*.tsx` (14 files, all read), `webpage/src/components/ui/*.tsx` (2 files, diffed byte-identical to `web/`'s), `web/src/app/globals.css` + `webpage/src/app/globals.css` (both read in full), `web/package.json` + `webpage/package.json` (both read in full), `web/tsconfig.json` + `webpage/tsconfig.json` (both read in full), `web/src/lib/utils.ts`, `web/src/hooks/use-toast.ts` (full), `web/src/app/layout.tsx` + `webpage/src/app/layout.tsx` (both full), targeted greps across `web/src/app/(dashboard)/**` for native-`<select>` usage (19 files) and empty-state copy (1 file, `pareceres/page.tsx`).
**Files scanned:** ~30 (direct reads) + grep sweeps across `web/src`.
**Pattern extraction date:** 2026-07-15

---
*Pattern map for: Phase 101 — Fundação — CLI Init e Design Tokens, LexCV v2.13 milestone*
