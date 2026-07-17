# Phase 110: Refinamento da Landing (webpage/) - Pattern Map

**Mapped:** 2026-07-17
**Files analyzed:** 5 (2 new primitives, 3 modified components)
**Analogs found:** 5 / 5

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|--------------------|------|-----------|-----------------|----------------|
| `webpage/src/components/ui/sheet.tsx` | component (ui primitive) | event-driven (open/close state) | `web/src/components/ui/sheet.tsx` | exact (verbatim copy source) |
| `webpage/src/components/ui/badge.tsx` | component (ui primitive) | transform (props → styled span) | `web/src/components/ui/badge.tsx` | exact (verbatim copy source) |
| `webpage/src/components/site-header.tsx` | component (nav/header) | event-driven (Sheet open/close + click-to-close nav) | `web/src/components/shared/dashboard-shell.tsx` + `web/src/components/shared/sidebar-nav.tsx` | role-match (app-shell drawer, not marketing header, but same Sheet+onNavigate mechanics) |
| `webpage/src/components/hero-section.tsx` | component (marketing section) | transform (static content recomposition) | `webpage/src/components/trust-section.tsx` | exact (same directory, same Card composition convention) |
| `webpage/src/components/contact-section.tsx` | component (marketing section) | transform (static content recomposition) | `webpage/src/components/trust-section.tsx` | exact (same directory, same Card composition convention) |

**Dependency check (verbatim-copy prerequisite):** `webpage/package.json` already declares `class-variance-authority@^0.7.1`, `radix-ui@^1.6.2`, `clsx@^2.1.1`, `tailwind-merge@^3.3.1`, `lucide-react@^0.543.0` — identical majors/ranges to what `web/`'s `sheet.tsx`/`badge.tsx` require. `webpage/src/lib/utils.ts` is byte-identical in behavior to `web/`'s (`cn = (...inputs) => twMerge(clsx(inputs))`). Both primitives can be copied with zero import-path or dependency changes.

---

## Pattern Assignments

### `webpage/src/components/ui/sheet.tsx` (new file — verbatim copy)

**Analog:** `web/src/components/ui/sheet.tsx` (132 lines, full file — already read in its entirety, no further reads needed)

**Action:** Copy the entire file verbatim. No changes needed — imports (`radix-ui`'s `Dialog as DialogPrimitive`, `lucide-react`'s `X`, `@/lib/utils`'s `cn`) all resolve identically in `webpage/`.

**Full source** (`web/src/components/ui/sheet.tsx` lines 1-13, showing imports + root re-exports):
```typescript
"use client";

import * as React from "react";
import { Dialog as DialogPrimitive } from "radix-ui";
import { X } from "lucide-react";

import { cn } from "@/lib/utils";

const Sheet = DialogPrimitive.Root;
const SheetTrigger = DialogPrimitive.Trigger;
const SheetClose = DialogPrimitive.Close;
const SheetPortal = DialogPrimitive.Portal;
```

**Side variants** (lines 30-37) — `side="right"` is the variant this phase uses (per UI-SPEC's header-layout resolution):
```typescript
type Side = "top" | "right" | "bottom" | "left";

const sideVariants: Record<Side, string> = {
  top: "inset-x-0 top-0 border-b data-[state=closed]:slide-out-to-top data-[state=open]:slide-in-from-top",
  right: "inset-y-0 right-0 h-full border-l data-[state=closed]:slide-out-to-right data-[state=open]:slide-in-from-right",
  bottom: "inset-x-0 bottom-0 border-t data-[state=closed]:slide-out-to-bottom data-[state=open]:slide-in-from-bottom",
  left: "inset-y-0 left-0 h-full border-r data-[state=closed]:slide-out-to-left data-[state=open]:slide-in-from-left",
};
```

**`SheetTitle` export** (lines 94-105) — required per UI-SPEC's a11y note (a visually-hidden `<SheetTitle className="sr-only">` must be the first child of `SheetContent` in `site-header.tsx`, since Radix `Dialog` needs `aria-labelledby`):
```typescript
function SheetTitle({
  className,
  ...props
}: React.ComponentProps<typeof DialogPrimitive.Title>) {
  return (
    <DialogPrimitive.Title
      data-slot="sheet-title"
      className={cn("text-lg font-semibold leading-none tracking-tight", className)}
      {...props}
    />
  );
}
```

**Exports** (lines 120-131) — all 10 exports must be copied even though `site-header.tsx` only consumes `Sheet`, `SheetTrigger`, `SheetContent`, `SheetTitle` (keep the file identical to source for future reuse):
```typescript
export {
  Sheet,
  SheetClose,
  SheetContent,
  SheetDescription,
  SheetFooter,
  SheetHeader,
  SheetOverlay,
  SheetPortal,
  SheetTitle,
  SheetTrigger,
};
```

---

### `webpage/src/components/ui/badge.tsx` (new file — verbatim copy)

**Analog:** `web/src/components/ui/badge.tsx` (37 lines, full file — already read in its entirety)

**Action:** Copy the entire file verbatim.

**Full source:**
```typescript
import * as React from "react";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-neutral-900 text-neutral-50 dark:bg-neutral-50 dark:text-neutral-900",
        secondary:
          "border-transparent bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-50",
        outline:
          "border-neutral-200 text-neutral-900 dark:border-neutral-800 dark:text-neutral-50",
        blue: "border-transparent bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-400",
        green: "border-transparent bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-400",
        amber: "border-transparent bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400",
        red: "border-transparent bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-400",
        purple: "border-transparent bg-purple-100 text-purple-700 dark:bg-purple-500/20 dark:text-purple-400",
        gray: "border-transparent bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-400",
      },
    },
    defaultVariants: {
      variant: "secondary",
    },
  },
);

export function Badge({
  className,
  variant,
  ...props
}: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" className={cn(badgeVariants({ variant }), className)} {...props} />;
}
```

**Consumption note for Hero/Contacto (not part of this file, but governs how it's used):** `defaultVariants.variant` is already `"secondary"` — CONTEXT.md's lock on `variant="secondary"` matches the component default, so callers may omit the prop entirely or pass it explicitly for clarity. UI-SPEC mandates overriding the base `text-xs font-medium` (12px/500) via `className` at each call site to preserve the pre-existing eyebrow's `text-sm font-semibold uppercase tracking-[0.2em]` (14px/600) — e.g. `<Badge variant="secondary" className="text-sm font-semibold uppercase tracking-[0.2em]">`.

---

### `webpage/src/components/site-header.tsx` (modified — controller/nav component)

**Analog (structural, for the Sheet mechanics only):** `web/src/components/shared/dashboard-shell.tsx` + `web/src/components/shared/sidebar-nav.tsx`

**Current file in full** (`webpage/src/components/site-header.tsx`, 27 lines — already read, this is the base to modify):
```typescript
import { BrandMark } from "@/components/brand-mark";
import { ThemeToggle } from "@/components/theme-toggle";
import { Button } from "@/components/ui/button";
import type { BrandingResponse } from "@/types/branding";

export function SiteHeader({ branding }: { branding: BrandingResponse }) {
  return (
    <header className="sticky top-0 z-10 h-16 border-b border-slate-200 bg-white/80 backdrop-blur-md dark:border-slate-800 dark:bg-[#020617]/80">
      <div className="mx-auto flex h-full max-w-7xl items-center gap-4 px-6">
        <BrandMark branding={branding} />
        <div className="ml-auto flex items-center gap-6">
          <nav className="hidden md:flex items-center gap-6">
            <a href="#funcionalidades" className="text-sm font-semibold text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400">Funcionalidades</a>
            <a href="#confianca" className="text-sm font-semibold text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400">Confiança</a>
            <a href="#contacto" className="text-sm font-semibold text-slate-600 hover:text-blue-600 dark:text-slate-300 dark:hover:text-blue-400">Contacto</a>
          </nav>
          <div className="flex items-center gap-3">
            <ThemeToggle />
            <Button asChild variant="secondary" className="rounded-none">
              <a href="/login">Entrar</a>
            </Button>
          </div>
        </div>
      </div>
    </header>
  );
}
```
Note the current file has no `"use client"` directive — adding `useState` for Sheet `open` means this file must become a Client Component (add `"use client";` as the first line, matching `dashboard-shell.tsx` line 1 and `sidebar-nav.tsx` line 1).

**Hamburger trigger pattern to copy** (`web/src/components/shared/dashboard-shell.tsx` lines 112-119) — this is the `aria-label="Abrir menu"` convention CONTEXT.md explicitly names; adapt the raw `<button>` to a `SheetTrigger asChild` wrapper instead of an imperative `setDrawerOpen(true)` (this phase uses controlled-or-uncontrolled Sheet, executor's call per UI-SPEC's 36px sizing exception):
```typescript
<button
  type="button"
  onClick={() => setDrawerOpen(true)}
  className="md:hidden flex items-center justify-center h-9 w-9 rounded-md text-slate-500 hover:text-slate-900 dark:hover:text-slate-100 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
  aria-label="Abrir menu"
>
  <Menu className="h-5 w-5" />
</button>
```
UI-SPEC mandates keeping the `h-9 w-9` (36px) sizing — do not switch to a 44px touch target; this matches `ThemeToggle`'s own `h-9 w-9` in the same header row (`webpage/src/components/theme-toggle.tsx` line 16).

**Sheet wiring pattern** (`web/src/components/shared/dashboard-shell.tsx` lines 39-45, 89-91) — `useState` + `Sheet open/onOpenChange` + `SheetContent side=`:
```typescript
export function DashboardShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();

  const me = useMe();
  const [drawerOpen, setDrawerOpen] = React.useState(false);
  // ...
  <Sheet open={drawerOpen} onOpenChange={setDrawerOpen}>
    <SheetContent side="left" className="w-[270px] p-0 bg-slate-950 ...">
```
Adapt: `side="right"` (not `"left"` — UI-SPEC's explicit choice, trigger and drawer share the right side of the header), width class `w-72 sm:max-w-sm` (not `w-[270px]` — UI-SPEC's explicit sizing for this shorter 4-item nav).

**Click-to-close pattern — copy this, NOT the `useEffect(pathname)` pattern** (`web/src/components/shared/sidebar-nav.tsx` lines 39-53):
```typescript
<Link
  key={item.href}
  href={item.href}
  onClick={onNavigate}
  className={cn(
    "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-all duration-200",
    active
      ? "bg-blue-600/10 text-blue-400 ..."
      : "text-slate-400 hover:bg-slate-900 hover:text-slate-200 ...",
  )}
>
```
Adapt for `site-header.tsx`: use plain `<a href={link.href} onClick={() => setOpen(false)}>` (anchor tags, not Next `Link` — these are same-page `#anchor` links, not route links) — direct inline `onClick` per CONTEXT.md's explicit lock, no `onNavigate` prop indirection needed since this Sheet has exactly one internal consumer (no desktop/mobile dual-render split like `dashboard-shell.tsx`'s `SidebarNav` reuse).

**Anti-pattern — explicitly do NOT copy** (`web/src/components/shared/dashboard-shell.tsx` lines 54-56):
```typescript
React.useEffect(() => {
  setDrawerOpen(false);
}, [pathname]);
```
CONTEXT.md and this phase's UI-SPEC are explicit: this `useEffect(pathname)` pattern would never fire here because the 3 nav links are same-page anchors (`#funcionalidades`, `#confianca`, `#contacto`) — `pathname` never changes on an anchor click. This was WR-01 in `109-REVIEW.md` (`Verified via git show d86d8fc` — `onNavigate` fired directly from each `Link`'s `onClick`, not from a `pathname`-watching effect). Phase 110 must use the direct-`onClick` approach for the same underlying reason WR-01 fixed it in `web/`.

**"Entrar" CTA — also closes Sheet on click, and gets `hidden md:inline-flex` at the header level** (per UI-SPEC's header-layout resolution): the existing header-level `Button` (site-header.tsx line 19-21) needs `hidden md:inline-flex` added to its `className` so it doesn't duplicate the Sheet's own "Entrar" item on mobile; the Sheet's internal "Entrar" button/link additionally calls `setOpen(false)` on click, same as the 3 anchor links.

---

### `webpage/src/components/hero-section.tsx` (modified — marketing section, Card/Badge recomposition)

**Analog:** `webpage/src/components/trust-section.tsx` (36 lines, full file — already read)

**Current file in full** (`webpage/src/components/hero-section.tsx`, 32 lines — base to modify):
```typescript
import { ArrowRight } from "lucide-react";
import { BrandMark } from "@/components/brand-mark";
import { Button } from "@/components/ui/button";
import type { BrandingResponse } from "@/types/branding";

export function HeroSection({ branding }: { branding: BrandingResponse }) {
  return (
    <section className="border-b border-slate-200 py-12 dark:border-slate-800 md:py-16 lg:py-16">
      <div className="mx-auto max-w-3xl px-6">
        <BrandMark branding={branding} className="mb-6" />
        <div className="mb-4 h-px w-12 bg-blue-600 dark:bg-blue-400" />
        <span className="inline-flex items-center border border-slate-300 px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em] text-slate-500 dark:border-slate-700 dark:text-slate-400">
          PLATAFORMA DE GESTÃO JURÍDICA
        </span>
        <h1 className="mt-6 text-5xl font-semibold tracking-tight text-slate-900 dark:text-slate-50">
          Gestão jurídica completa para a sua instituição
        </h1>
        <p className="mt-6 text-base text-slate-600 dark:text-slate-300">
          Clientes, processos, prazos e documentos — tudo num único painel, com isolamento total por tenant.
        </p>
        <div className="mt-8 flex flex-wrap items-center gap-4">
          <Button asChild variant="secondary" className="rounded-none">
            <a href="/login">Entrar</a>
          </Button>
          <Button asChild variant="ghost" className="rounded-none">
            <a href="#funcionalidades">Ver Funcionalidades<ArrowRight className="h-4 w-4" /></a>
          </Button>
        </div>
      </div>
    </section>
  );
}
```

**Card composition pattern to copy** (`webpage/src/components/trust-section.tsx` lines 22-29):
```typescript
<Card key={title}>
  <CardHeader>
    <Icon className="h-6 w-6 text-slate-900 dark:text-slate-100" />
    <CardTitle className="text-2xl">{title}</CardTitle>
    <CardDescription className="text-base">{desc}</CardDescription>
  </CardHeader>
</Card>
```
**Critical adaptation — do NOT swap `<h1>` for `CardTitle`:** UI-SPEC is explicit that `CardTitle` renders an `<h3>` with baked-in `leading-none` — using it for the Hero's page-level `<h1>` would demote heading semantics (accessibility/SEO regression) and break the 1.2 line-height target. Use `Card`/`CardHeader`/`CardContent` purely for layout/spacing wrapper divs; keep the raw `<h1 className="mt-6 text-5xl font-semibold ...">` tag exactly as it exists today, just relocated inside `CardContent` (or `CardHeader`, executor's discretion per CONTEXT.md).

**Imports to add:**
```typescript
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
```
(`CardTitle`/`CardDescription` are NOT needed here — those are for `trust-section.tsx`'s grid-item titles, not for Hero's raw `<h1>`/`<p>`.)

**Scope boundary (from UI-SPEC):** only `Badge`+`<h1>`+`<p>`+2 CTA `Button`s move inside the `Card`. `BrandMark` and the blue accent `<div className="mb-4 h-px w-12 bg-blue-600 ...">` stay outside/above the `Card`, unchanged.

**Eyebrow → Badge migration** (current line 12-14, replace `<span>` with `<Badge>`):
```typescript
// Before:
<span className="inline-flex items-center border border-slate-300 px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em] text-slate-500 dark:border-slate-700 dark:text-slate-400">
  PLATAFORMA DE GESTÃO JURÍDICA
</span>
// After (pattern):
<Badge variant="secondary" className="text-sm font-semibold uppercase tracking-[0.2em]">
  PLATAFORMA DE GESTÃO JURÍDICA
</Badge>
```

---

### `webpage/src/components/contact-section.tsx` (modified — marketing section, Card/Badge recomposition)

**Analog:** `webpage/src/components/trust-section.tsx` (same as Hero — identical Card composition target) and `webpage/src/components/hero-section.tsx` post-migration (same Card+Badge shape, per CONTEXT.md's "no visual difference between Hero's Card and Contacto's Card" lock).

**Current file in full** (`webpage/src/components/contact-section.tsx`, 25 lines — base to modify):
```typescript
import { Mail } from "lucide-react";
import { Button } from "@/components/ui/button";

export function ContactSection() {
  return (
    <section id="contacto" className="border-t border-slate-200 py-12 dark:border-slate-800 md:py-16 lg:py-16">
      <div className="mx-auto max-w-3xl px-6 text-center">
        <span className="inline-flex items-center border border-slate-300 px-3 py-1 text-sm font-semibold uppercase tracking-[0.2em] text-slate-500 dark:border-slate-700 dark:text-slate-400">
          CONTACTO
        </span>
        <h2 className="mt-4 text-2xl font-semibold text-slate-900 dark:text-slate-50">Pronto para começar?</h2>
        <p className="mt-6 text-base text-slate-600 dark:text-slate-300">
          Fale com a nossa equipa e conheça a plataforma em detalhe.
        </p>
        <div className="mt-8 flex justify-center">
          <Button asChild className="rounded-none">
            <a href="mailto:contacto@lexcv.cv?subject=Pedido%20de%20Demonstra%C3%A7%C3%A3o%20%E2%80%94%20LexCV">
              Pedir Demonstração<Mail className="h-4 w-4" />
            </a>
          </Button>
        </div>
      </div>
    </section>
  );
}
```

**Same Card/Badge migration pattern as Hero** (see above) applies verbatim: `<span>` → `<Badge variant="secondary" className="text-sm font-semibold uppercase tracking-[0.2em]">`, everything from Badge through the single CTA `Button` moves inside `Card`/`CardHeader`/`CardContent`. **Critical note repeated for this file:** Contacto uses `<h2>`, not `<h1>` — same rule applies, do not swap for `CardTitle` (would demote to `<h3>` and it's already an `<h2>`, one level below Hero's `<h1>` — swapping would create a duplicate ambiguity, not a promotion/demotion of the same magnitude, but still wrong per UI-SPEC's explicit instruction).

**Imports to add:**
```typescript
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader } from "@/components/ui/card";
```

---

## Shared Patterns

### Card composition (layout wrapper, not heading replacement)
**Source:** `webpage/src/components/trust-section.tsx` lines 22-29 (Card/CardHeader/CardTitle/CardDescription grid-item pattern)
**Apply to:** `hero-section.tsx`, `contact-section.tsx` — but only the `Card`/`CardHeader`/`CardContent` wrapper structure, never `CardTitle`/`CardDescription` (those are for repeated grid items with `<h3>` semantics; Hero/Contacto have unique page-level `<h1>`/`<h2>` headings that must stay raw tags).
```typescript
<Card>
  <CardHeader>
    {/* Badge here */}
  </CardHeader>
  <CardContent>
    {/* h1/h2, p, CTA buttons here — executor's call on Header vs Content split */}
  </CardContent>
</Card>
```

### Eyebrow → Badge migration (identical in both Hero and Contacto)
**Source:** current manual `<span>` markup in both files (see per-file sections above) + `web/src/components/ui/badge.tsx`
**Apply to:** `hero-section.tsx`, `contact-section.tsx`
```typescript
<Badge variant="secondary" className="text-sm font-semibold uppercase tracking-[0.2em]">
  {/* eyebrow text unchanged */}
</Badge>
```
Both call sites use the exact same variant + override className — CONTEXT.md's explicit "mesmo componente/variant do Hero" consistency lock.

### Sheet open/close + click-to-close-on-anchor (site-header.tsx only, but the mechanics generalize)
**Source:** `web/src/components/shared/dashboard-shell.tsx` lines 39-45, 89-91, 112-119 (Sheet wiring + hamburger trigger) and `web/src/components/shared/sidebar-nav.tsx` lines 39-53 (click-to-close via direct `onClick`, NOT `useEffect(pathname)`)
**Apply to:** `site-header.tsx` only (the only file in this phase with a Sheet)
```typescript
const [open, setOpen] = React.useState(false);
// ...
<Sheet open={open} onOpenChange={setOpen}>
  <SheetTrigger asChild>
    <button type="button" className="md:hidden flex items-center justify-center h-9 w-9 ..." aria-label="Abrir menu">
      <Menu className="h-5 w-5" />
    </button>
  </SheetTrigger>
  <SheetContent side="right" className="w-72 sm:max-w-sm">
    <SheetTitle className="sr-only">Menu</SheetTitle>
    {NAV_LINKS.map((link) => (
      <a key={link.href} href={link.href} onClick={() => setOpen(false)}>{link.label}</a>
    ))}
  </SheetContent>
</Sheet>
```
**Do not copy** `dashboard-shell.tsx`'s `React.useEffect(() => setDrawerOpen(false), [pathname])` (lines 54-56) — this was the WR-01 anti-pattern class fixed in Phase 109 (`109-REVIEW.md`), and is doubly inapplicable here since all 3 links are same-page anchors that never change `pathname`.

### `aria-label="Abrir menu"` string convention
**Source:** `web/src/components/shared/dashboard-shell.tsx` line 116
**Apply to:** `site-header.tsx`'s hamburger trigger — exact string match required per UI-SPEC's copywriting contract.

---

## No Analog Found

None — all 5 files have a usable analog (2 exact verbatim-copy sources, 3 role/structural matches within the same codebase).

---

## Metadata

**Analog search scope:** `webpage/src/components/` (all 8 existing `.tsx` files + `ui/` subdirectory), `web/src/components/ui/{sheet,badge}.tsx`, `web/src/components/shared/{dashboard-shell,sidebar-nav}.tsx`, `webpage/src/components/theme-toggle.tsx`, `webpage/src/lib/utils.ts`, `webpage/package.json`, `.planning/phases/LEXCV-109-notifica-es-settings-setup-wizard/109-REVIEW.md`
**Files scanned:** 11
**Pattern extraction date:** 2026-07-17
