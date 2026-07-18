# Phase 102: Reconciliação do Design System - Pattern Map

**Mapped:** 2026-07-15
**Files analyzed:** 18 (13 reconciled components + calendar.tsx + breadcrumb.tsx + package.json + providers.tsx + 1 row-action consumer file used as the Tooltip rollout template)
**Analogs found:** 18 / 18 — this phase is self-referential: every file being modified already exists in the repo, so the "analog" for each is almost always **itself, as currently written** (reconciliation, not net-new creation). Cross-file analogs are used only for the Tooltip rollout (new markup) and for the `buttonVariants` de-duplication (calendar.tsx → button.tsx).

## Important framing for this phase

Unlike a typical "new file, find an analog" pattern map, every one of the 13 components already exists and is being **edited in place** via `shadcn add <component> --diff`. The "pattern to copy from" is therefore the component's **own current file content** (reproduced in full below, since the planner must diff against it) plus the **reconciliation rule** from `102-UI-SPEC.md` that decides what changes and what doesn't. Two genuinely new patterns are introduced this phase:

1. **Tooltip wrapping** around existing icon-only `Button asChild` elements — analog is `web/src/components/ui/tooltip.tsx` (already installed, Phase 101) + the existing row-action markup in `clientes/page.tsx`.
2. **`buttonVariants` export/import** — analog is the duplicated CVA config currently living in both `button.tsx` and `calendar.tsx`.

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `web/src/components/ui/button.tsx` | component (primitive) | request-response (event handler + render) | itself (current content below) | exact — reconcile in place |
| `web/src/components/ui/card.tsx` | component (primitive) | request-response (render only) | itself (current content below) | exact — reconcile in place |
| `web/src/components/ui/badge.tsx` | component (primitive) | request-response (render only) | itself (current content below) | exact — reconcile in place |
| `web/src/components/ui/dialog.tsx` | component (primitive, Radix wrapper) | event-driven (open/close state) | itself (current content below) | exact — reconcile in place |
| `web/src/components/ui/alert-dialog.tsx` | component (primitive, Radix wrapper) | event-driven (open/close state) | itself (current content below) | exact — reconcile in place |
| `web/src/components/ui/table.tsx` | component (primitive) | request-response (render only) | itself (current content below) | exact — reconcile in place |
| `web/src/components/ui/sheet.tsx` | component (primitive, Radix wrapper) | event-driven (open/close state) | itself (current content below) | exact — reconcile in place, structure untouched per CONTEXT.md |
| `web/src/components/ui/input.tsx` | component (primitive, form) | request-response (render only, Rule C) | itself (current content below) | exact — no change expected beyond audit |
| `web/src/components/ui/label.tsx` | component (primitive, form, Radix wrapper) | request-response (render only, Rule C) | itself (current content below) | exact — no change expected beyond audit |
| `web/src/components/ui/popover.tsx` | component (primitive, Radix wrapper) | event-driven (open/close state) | itself (current content below) | exact — reconcile in place |
| `web/src/components/ui/radio-group.tsx` | component (primitive, form, Radix wrapper) | event-driven (checked state, Rule C) | itself (current content below) | exact — no change expected beyond audit |
| `web/src/components/ui/switch.tsx` | component (primitive, form, Radix wrapper) | event-driven (checked state, Rule C) | itself (current content below) | exact — no change expected beyond audit |
| `web/src/components/ui/textarea.tsx` | component (primitive, form) | request-response (render only, Rule C) | itself (current content below) | exact — no change expected beyond audit |
| `web/src/app/providers.tsx` | provider | event-driven (context mount) | itself (current content below) — **not** `web/src/app/layout.tsx` (see note) | exact — additive `TooltipProvider` wrap |
| `web/package.json` | config | batch (one-line dependency-block move) | itself (current content below) | exact |
| `web/src/components/ui/calendar.tsx` | component (consumer of button.tsx) | request-response (render only) | `web/src/components/ui/button.tsx` (source of truth for `buttonVariants`) | exact — dedupe, don't reconcile against registry |
| `web/src/components/ui/breadcrumb.tsx` | component (primitive) | request-response (render only) | `web/src/components/ui/button.tsx` (target `Slot` aliasing convention) | exact — aliasing fix only |
| Icon-only Tooltip consumers (row actions + sidebar) | component (page-level consumer) | event-driven (hover/focus → tooltip open) | `web/src/app/(dashboard)/clientes/page.tsx` (`ClienteRow`, lines 590-625) + `web/src/components/shared/dashboard-shell.tsx` (LogOut button, lines 154 & 234) | exact — these ARE the target files to wrap |

**Correction to the phase brief's file list:** the prompt says "TooltipProvider mount" belongs in `web/src/app/layout.tsx`. Direct reading of both files shows `layout.tsx` renders `<Providers>{children}</Providers>` and does not itself hold any context providers — `web/src/app/providers.tsx` is the actual (and only) client-side providers wrapper, already containing `QueryClientProvider`/`NextThemesProvider`. `102-UI-SPEC.md` (lines 135-143) confirms the mount point is `providers.tsx`, not `layout.tsx`. Treat `providers.tsx` as the file to edit; `layout.tsx` itself needs no change.

**Row-action icon-only buttons — actual location found:** grep for `Eye|Pencil|Trash2|Printer` across `web/src/app/(dashboard)/**/page.tsx` surfaces exactly one page with this literal icon-only-row-action pattern: `web/src/app/(dashboard)/clientes/page.tsx` (mobile card view lines 455-467, desktop `ClienteRow` lines 590-625). `processos/page.tsx` and `pareceres/page.tsx` use a different pattern (`MoreVertical` dropdown-trigger, not bare icon buttons) and are **out of this phase's DSR-03 scope** as literally written — `102-UI-SPEC.md`'s own copy table (line 122) cites `clientes/page.tsx` as the confirmed grep source for the Eye/Pencil/Trash2 pattern; `processos/page.tsx`/`pareceres/page.tsx`/`documentos/page.tsx` were named in CONTEXT.md/UI-SPEC prose as illustrative but do not contain this exact icon set today — verify at execution time whether the executor should extend to `MoreVertical` dropdown items too, or strictly limit to the literal Eye/Pencil/Trash2/Printer buttons that exist (recommend the latter, matching UI-SPEC's explicit scope note "não alargar a mais superfícies").

---

## Pattern Assignments

### `web/src/components/ui/button.tsx` (component, Rule C — preserve identity)

**Current full content (18 lines omitted from excerpt below are the file's entirety — read again in full during execution to diff against CLI output):**

```typescript
import * as React from "react";
import { Slot as SlotPrimitive } from "radix-ui";
import { cva, type VariantProps } from "class-variance-authority";

import { cn } from "@/lib/utils";

const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-[color,box-shadow] disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0 outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300",
  {
    variants: {
      variant: {
        default: "bg-neutral-900 text-neutral-50 hover:bg-neutral-900/90",
        secondary: "bg-neutral-100 text-neutral-900 hover:bg-neutral-100/80",
        outline:
          "border border-neutral-200 bg-white hover:bg-neutral-100 text-neutral-900 dark:border-neutral-800 dark:bg-neutral-950 dark:hover:bg-neutral-800 dark:text-neutral-50",
        ghost: "hover:bg-neutral-100 text-neutral-900 dark:hover:bg-neutral-800 dark:text-neutral-50",
        link: "text-neutral-900 underline-offset-4 hover:underline dark:text-neutral-50",
      },
      size: {
        default: "h-9 px-4 py-2",
        sm: "h-8 rounded-md px-3 text-xs",
        lg: "h-10 rounded-md px-8",
        icon: "h-9 w-9",
      },
    },
    defaultVariants: { variant: "default", size: "default" },
  },
);

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean;
}

export function Button({ className, variant, size, asChild, ...props }: ButtonProps) {
  const Comp = asChild ? SlotPrimitive.Slot : "button";
  return <Comp className={cn(buttonVariants({ variant, size, className }))} data-slot="button" {...props} />;
}
```

**Reconciliation rule (Rule C — never substitute colors):** all 5 variant classes and 4 size classes above are byte-for-byte preserved regardless of what `add button --diff` proposes. Do **not** adopt the upstream registry's `bg-primary`-based `default` variant, its new `destructive` variant, or its `xs` size — per `102-UI-SPEC.md`'s Color section, `--primary` institutional blue is reserved for a closed list that excludes `button.tsx` entirely.

**Required addition this phase (per CONTEXT.md "Rigor de Verificação"):** export `buttonVariants` so `calendar.tsx` can import it instead of duplicating:
```typescript
export { Button, buttonVariants };
// or: export function buttonVariants(...) — add to the existing export list
```
Also standardize the `Slot` aliasing to match whatever convention is chosen for `breadcrumb.tsx` (see IN-04 in `101-REVIEW.md`) — pick one of `SlotPrimitive`/`.Slot` (current `button.tsx` style) or `Slot`/`.Root` (current `breadcrumb.tsx` style) and apply it to both files identically.

**Known consumer to protect:** `web/src/components/ui/calendar.tsx:51-53` casts `Button as unknown as React.ForwardRefExoticComponent<...>` to attach a ref for roving-tabindex focus — `button.tsx` remains a plain function component (not `forwardRef`) per `101-REVIEW.md` WR-03's confirmed-safe rationale (React 19 forwards `ref` through the props spread regardless). Do not add `React.forwardRef` to `button.tsx` as a side effect of this reconciliation unless deliberately updating `calendar.tsx`'s cast in the same change.

---

### `web/src/components/ui/card.tsx` (component, Rule A radius + Rule B dark surface)

**Current full content:**
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
// CardHeader / CardTitle / CardDescription / CardContent / CardFooter unchanged below (card.tsx:18-56)
```

**Concrete substitutions (per 102-UI-SPEC.md Color table row `card`):**
- `rounded-none` → `rounded-lg` (Rule A — resolves to `0` via `--radius: 0rem` in `globals.css:75`, identical pixel today)
- `bg-white` → `bg-card` (Rule A/exact match — `--card` light = `oklch(1 0 0)` = white)
- `dark:bg-[#020617]` → `dark:bg-card` (Rule B — **visibly different**: `--card` dark = `oklch(0.205 0 0)`, a lighter gray than `#020617`; this is the intentional elevation fix, flag at the human checkpoint)
- Preserve `hover:shadow-md`, border treatment, and `text-slate-950`/`dark:text-slate-50` (not part of UI-SPEC's called-out delta; keep as-is unless the diff maps these onto `text-card-foreground`, which would also be an acceptable Rule-A-equivalent substitution since `--card-foreground` resolves to the same neutral scale)

---

### `web/src/components/ui/badge.tsx` (component, Rule C — all 9 variants preserved)

**Current full content (30 lines, entire file):**
```typescript
const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
  {
    variants: {
      variant: {
        default: "border-transparent bg-neutral-900 text-neutral-50 dark:bg-neutral-50 dark:text-neutral-900",
        secondary: "border-transparent bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-50",
        outline: "border-neutral-200 text-neutral-900 dark:border-neutral-800 dark:text-neutral-50",
        blue: "border-transparent bg-blue-100 text-blue-700 dark:bg-blue-500/20 dark:text-blue-400",
        green: "border-transparent bg-emerald-100 text-emerald-700 dark:bg-emerald-500/20 dark:text-emerald-400",
        amber: "border-transparent bg-amber-100 text-amber-700 dark:bg-amber-500/20 dark:text-amber-400",
        red: "border-transparent bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-400",
        purple: "border-transparent bg-purple-100 text-purple-700 dark:bg-purple-500/20 dark:text-purple-400",
        gray: "border-transparent bg-neutral-100 text-neutral-700 dark:bg-neutral-800 dark:text-neutral-400",
      },
    },
    defaultVariants: { variant: "secondary" },
  },
);

export function Badge({ className, variant, ...props }: React.ComponentProps<"span"> & VariantProps<typeof badgeVariants>) {
  return <span data-slot="badge" className={cn(badgeVariants({ variant }), className)} {...props} />;
}
```

**Reconciliation rule:** `default`/`secondary`/`outline` neutral values AND all 6 custom color variants (`blue`, `green`, `amber`, `red`, `purple`, `gray`) preserved verbatim. `rounded-full` is already correct (not `rounded-none`), no radius change needed. This is the exact variant list the planner must never drop.

**Grep-verified `gray` call sites (all 3 confirmed present, re-verify post-reconciliation with the same grep):**
```
web/src/app/(dashboard)/notificacoes/page.tsx
web/src/app/(dashboard)/dashboard/page.tsx
web/src/app/(dashboard)/processos/page.tsx
```
Concrete usage example (`processos/page.tsx`, badge with dynamic variant): `<Badge variant={... : "gray"} className="rounded-none font-bold tracking-wide">{tipo || "—"}</Badge>` (same pattern in `clientes/page.tsx:574`, for the "PARTICULAR"/"EMPRESA"/other tipo badge) — note call sites also apply `className="rounded-none ..."` directly on `Badge` instances; since `Badge` itself is `rounded-full` (a Rule-C shape, unrelated to `--radius`), these call-site `rounded-none` overrides are pre-existing, unrelated debt (badges rendering as rounded-rects, not pills) — out of this phase's declared scope (badge itself, not call sites, is what's reconciled), but worth flagging as a note, not silently "fixing" without a decision.

---

### `web/src/components/ui/dialog.tsx` (component, Rule A radius + Rule B dark surface)

**Current full content (114 lines) — key excerpt, `DialogContent` (lines 30-54):**
```typescript
function DialogContent({ className, children, ...props }: React.ComponentProps<typeof DialogPrimitive.Content>) {
  return (
    <DialogPortal>
      <DialogOverlay />
      <DialogPrimitive.Content
        data-slot="dialog-content"
        className={cn(
          "fixed left-[50%] top-[50%] z-50 grid w-full max-w-lg translate-x-[-50%] translate-y-[-50%] gap-4 bg-white p-6 shadow-2xl duration-200 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0 data-[state=closed]:zoom-out-95 data-[state=open]:zoom-in-95 data-[state=closed]:slide-out-to-left-1/2 data-[state=closed]:slide-out-to-top-[48%] data-[state=open]:slide-in-from-left-1/2 data-[state=open]:slide-in-from-top-[48%] dark:bg-[#020617] rounded-none",
          className,
        )}
        {...props}
      >
        {children}
        <DialogPrimitive.Close className="absolute right-4 top-4 rounded-sm opacity-70 ring-offset-white transition-opacity hover:opacity-100 focus:outline-none focus:ring-2 focus:ring-neutral-950 focus:ring-offset-2 disabled:pointer-events-none data-[state=open]:bg-neutral-100 data-[state=open]:text-neutral-500 dark:ring-offset-neutral-950 dark:focus:ring-neutral-300 dark:data-[state=open]:bg-neutral-800 dark:data-[state=open]:text-neutral-400">
          <X className="h-4 w-4" />
          <span className="sr-only">Fechar</span>
        </DialogPrimitive.Close>
      </DialogPrimitive.Content>
    </DialogPortal>
  );
}
```

**Concrete substitutions:**
- `rounded-none` → `rounded-lg` (Rule A)
- `dark:bg-[#020617]` → `dark:bg-popover` (Rule B — `--popover` dark = `oklch(0.205 0 0)`, same elevation fix as `card`)
- `bg-white` → `bg-popover` (Rule A/exact match, light `--popover` = white)
- **Preserve verbatim:** `sr-only`"Fechar"` text (line 49) — this is the accessibility label; do not drop or re-translate.
- **Do not add** `showCloseButton` prop unless a grep of call sites shows a manual close-button conflict (per `102-UI-SPEC.md` line 96) — default to today's always-visible close button.

**Import/structure pattern (lines 1-12, applies to all Radix-wrapper components in this phase):**
```typescript
"use client";
import * as React from "react";
import { Dialog as DialogPrimitive } from "radix-ui";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogPortal = DialogPrimitive.Portal;
const DialogClose = DialogPrimitive.Close;
```
This "unified `radix-ui` package, aliased `<Component> as <Component>Primitive`" import shape is the shared convention across `dialog.tsx`/`alert-dialog.tsx`/`sheet.tsx`/`popover.tsx`/`radio-group.tsx`/`switch.tsx`/`label.tsx` — already consistent (Pitfall 3 already resolved for `web/` per Phase 101). No import-style change needed in this phase, only color/radius literals.

---

### `web/src/components/ui/alert-dialog.tsx` (component, Rule A + Rule B + Rule C on Action/Cancel)

**`AlertDialogContent` (lines 28-48) — same substitutions as `dialog.tsx`:** `rounded-none`→`rounded-lg`, `dark:bg-[#020617]`→`dark:bg-popover`, `bg-white`→`bg-popover`.

**`AlertDialogAction`/`AlertDialogCancel` (lines 96-124) — Rule C, preserve verbatim:**
```typescript
function AlertDialogAction({ className, ...props }: React.ComponentProps<typeof AlertDialogPrimitive.Action>) {
  return (
    <AlertDialogPrimitive.Action
      className={cn(
        "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-[color,box-shadow] disabled:pointer-events-none disabled:opacity-50 outline-none focus-visible:ring-2 focus-visible:ring-neutral-950 dark:focus-visible:ring-neutral-300 h-9 px-4 py-2 bg-neutral-900 text-neutral-50 hover:bg-neutral-900/90",
        className,
      )}
      {...props}
    />
  );
}
```
Do not retarget `bg-neutral-900` to `--primary`/`--destructive` — call sites needing a destructive-red confirm already pass their own override `className`.

---

### `web/src/components/ui/popover.tsx` (component, Rule B dark surface only)

**Current full content, `PopoverContent` (lines 12-34):**
```typescript
const PopoverContent = React.forwardRef<
  React.ElementRef<typeof PopoverPrimitive.Content>,
  React.ComponentPropsWithoutRef<typeof PopoverPrimitive.Content>
>(({ className, align = "end", sideOffset = 8, ...props }, ref) => (
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
**Substitution:** `dark:bg-slate-950` → `dark:bg-popover` (Tailwind `slate-950` hex is literally `#020617`, confirmed identical to `--background` dark — same elevation-fix reasoning as card/dialog). Note this file already uses `React.forwardRef` (unlike `button.tsx`) — no ref-forwarding concern here. Preserve border/shadow/animation classes and `rounded-md` (already token-friendly, not `rounded-none`, no change needed).

---

### `web/src/components/ui/table.tsx` (component, Rule B optional per Claude's Discretion)

**Current full content (86 lines) — `TableFooter`/`TableRow` (lines 31-52):**
```typescript
export function TableFooter({ className, ...props }: React.ComponentProps<"tfoot">) {
  return <tfoot data-slot="table-footer" className={cn("border-t bg-neutral-50 font-medium dark:bg-neutral-900/30", className)} {...props} />;
}
export function TableRow({ className, ...props }: React.ComponentProps<"tr">) {
  return (
    <tr
      data-slot="table-row"
      className={cn(
        "border-b transition-colors hover:bg-neutral-50/60 data-[state=selected]:bg-neutral-50 dark:hover:bg-neutral-900/20 dark:data-[state=selected]:bg-neutral-900/30",
        className,
      )}
      {...props}
    />
  );
}
```
**Optional substitution:** `bg-neutral-50`/`dark:bg-neutral-900/30` → `bg-muted`/`hover:bg-muted/50` — low risk, verify visually at checkpoint if applied. **Preserve verbatim:** `TableCaption`/`TableHead`/`TableCell` neutral-scale text colors (Rule C, no accent tint on table chrome).

---

### `web/src/components/ui/sheet.tsx` (structurally frozen, cosmetic-only per CONTEXT.md)

**`SheetContent` (lines 43-72), the one block with color literals:**
```typescript
className={cn(
  "fixed z-50 bg-white shadow-lg transition ease-in-out data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:duration-300 data-[state=open]:duration-500 dark:bg-neutral-950",
  sideVariants[side],
  className,
)}
```
**Substitution:** `bg-white` → `bg-popover` (exact match, no visual change). `dark:bg-neutral-950` is a **third**, distinct value from both `--background` and `--card`/`--popover` — Claude's Discretion whether to converge to `dark:bg-popover` (recommended, consistency with dialog/card) or leave as literal; confirm final choice at the checkpoint. **Do not** re-scaffold via `add sheet --overwrite` — hand-edit only the color literals, preserve `sr-only` "Fechar" label (line 67) and `sideVariants` structure verbatim.

---

### `web/src/components/ui/input.tsx`, `label.tsx`, `radio-group.tsx`, `switch.tsx`, `textarea.tsx` (Rule C, no color/radius change)

All five files' current full content was read in full this pass (each ≤ 40 lines). No excerpts needed beyond confirming: none contain `rounded-none` (they're already `rounded-md`/`rounded-full`, unaffected by the `--radius:0rem` token since `rounded-md` still resolves to a real (albeit visually-zero) value via `--radius-md`), and none contain a `dark:bg-[#020617]`/`dark:bg-slate-950`-style magic-hex surface color (their surfaces are `bg-white`/`dark:bg-neutral-950`/`border-neutral-200`/`dark:border-neutral-800`, all Rule-C neutral-scale values per `102-UI-SPEC.md`'s table). Executor should still run `add <component> --diff` on each per the diff-first protocol (a genuinely dropped a11y prop is possible even with no color delta), but expect the diff to show no color/radius changes to accept.

---

### `web/src/app/providers.tsx` (provider, event-driven context mount)

**Current full content (32 lines, entire file) — target insertion point:**
```typescript
"use client";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ThemeProvider as NextThemesProvider } from "next-themes";
import * as React from "react";

export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = React.useState(() => new QueryClient({ defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } } }));

  return (
    <QueryClientProvider client={client}>
      <NextThemesProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
        {children}
      </NextThemesProvider>
    </QueryClientProvider>
  );
}
```
**Required addition (per 102-UI-SPEC.md lines 135-143):** add `import { TooltipProvider } from "@/components/ui/tooltip";` and wrap `{children}` inside `NextThemesProvider` with:
```tsx
<TooltipProvider delayDuration={700}>
  {children}
</TooltipProvider>
```
`delayDuration={700}` is **required explicitly** — `tooltip.tsx:9` defaults it to `0` (instant), not the `700ms` CONTEXT.md decided on. Omitting this prop silently ships instant tooltips.

---

### `web/package.json` (config, one-line move)

**Current relevant excerpt (lines 12-32, `dependencies` block containing the misplaced entry, and lines 33-42, correctly-placed `devDependencies`):**
```json
"dependencies": {
  ...
  "shadcn": "^4.13.0",
  ...
},
"devDependencies": {
  "@tailwindcss/postcss": "^4",
  "@types/node": "^20",
  "@types/react": "^19",
  "@types/react-dom": "^19",
  "eslint": "^9",
  "eslint-config-next": "16.2.6",
  "tailwindcss": "^4",
  "typescript": "^5"
}
```
**Fix:** remove `"shadcn": "^4.13.0"` from `dependencies`, add it to `devDependencies` alphabetically (between `eslint-config-next` and `tailwindcss`). Confirmed by `101-REVIEW.md` IN-06 and `101-UI-REVIEW.md` Priority Fix #3 — this is the exact, already-diagnosed one-line fix.

---

### `web/src/components/ui/calendar.tsx` (dedupe `buttonVariants`, lines 21-45 + 51-53)

**Current state to remove (the duplicated CVA config, lines 21-45):** byte-for-byte identical to `button.tsx`'s `buttonVariants` (confirmed in this pass — both cva configs match exactly). Also remove the explanatory comment block (lines 16-20) once the duplication is resolved.

**Target pattern:**
```typescript
import { Button, buttonVariants, type ButtonProps } from "@/components/ui/button"
// ... remove the local `const buttonVariants = cva(...)` block entirely
```
**Preserve:** the `ButtonWithRef` cast (lines 51-53) and its usage in `CalendarDayButton` — this is unrelated to the duplication (it's a ref-forwarding workaround, not a styling duplication) and stays exactly as-is per `101-REVIEW.md` WR-03's confirmed-safe rationale. Do not remove the dev-mode warning `useEffect` (lines 243-253) as part of this file touch unless also fixing `IN-01` (unnecessary `eslint-disable-next-line react-hooks/exhaustive-deps` on line 252, flagged in `101-REVIEW.md`) — that's an optional bonus cleanup, not required by CONTEXT.md.

---

### `web/src/components/ui/breadcrumb.tsx` (Slot aliasing fix, lines 2 & 48)

**Current:**
```typescript
import { Slot } from "radix-ui"
// ...
const Comp = asChild ? Slot.Root : "a"
```
**vs. `button.tsx`'s current:**
```typescript
import { Slot as SlotPrimitive } from "radix-ui";
// ...
const Comp = asChild ? SlotPrimitive.Slot : "button";
```
**Fix:** standardize on one alias/property pair across both files (per CONTEXT.md: "uniformizar"). Recommend adopting `button.tsx`'s `SlotPrimitive`/`.Slot` convention in `breadcrumb.tsx` (or the reverse — either direction is acceptable, just make them match). This was flagged as `IN-04` in `101-REVIEW.md` and the Pillar-2 Info finding in `101-UI-REVIEW.md`.

---

### Tooltip rollout — icon-only sidebar + row-action buttons (DSR-03)

**Analog 1 — sidebar footer `LogOut` button, `web/src/components/shared/dashboard-shell.tsx:154` (desktop) and `:234` (mobile drawer, identical markup duplicated):**
```tsx
<Button type="button" variant="ghost" className="h-8 w-8 p-0 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors" onClick={onLogout}>
  <LogOut className="h-4 w-4" />
</Button>
```
**Target pattern (wrap with Tooltip, add aria-label):**
```tsx
<Tooltip>
  <TooltipTrigger asChild>
    <Button
      type="button"
      variant="ghost"
      aria-label="Terminar sessão"
      className="h-8 w-8 p-0 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors"
      onClick={onLogout}
    >
      <LogOut className="h-4 w-4" />
    </Button>
  </TooltipTrigger>
  <TooltipContent>Terminar sessão</TooltipContent>
</Tooltip>
```
Import: `import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";` (matches the existing `tooltip.tsx` export list exactly — no `TooltipProvider` import needed here since it's mounted once globally in `providers.tsx`).

**Analog 2 — row actions, `web/src/app/(dashboard)/clientes/page.tsx` `ClienteRow` (desktop table, lines 590-625) and mobile card (lines 455-467):**
```tsx
<Button asChild size="sm" variant="ghost" className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
  <Link href={`/clientes/${encodeURIComponent(cliente.id)}`}>
    <Eye className="h-4 w-4" />
  </Link>
</Button>
```
**Target pattern — note the double-`asChild` nesting (`TooltipTrigger asChild` wrapping a `Button asChild` wrapping a `Link`), which Radix supports (each `asChild` only merges one level of props/ref):**
```tsx
<Tooltip>
  <TooltipTrigger asChild>
    <Button asChild size="sm" variant="ghost" aria-label="Ver detalhes" className="h-9 w-9 p-0 text-slate-500 hover:text-blue-600 dark:hover:text-blue-400 transition-colors">
      <Link href={`/clientes/${encodeURIComponent(cliente.id)}`}>
        <Eye className="h-4 w-4" />
      </Link>
    </Button>
  </TooltipTrigger>
  <TooltipContent>Ver detalhes</TooltipContent>
</Tooltip>
```
The plain-`Button` delete action (no `asChild`, lines 613-624, `onClick={onDelete}`) only needs single-level `TooltipTrigger asChild` wrapping:
```tsx
<Tooltip>
  <TooltipTrigger asChild>
    <Button type="button" size="sm" variant="ghost" aria-label="Eliminar" className="h-9 w-9 p-0 text-slate-500 hover:text-red-600 dark:hover:text-red-400 transition-colors" onClick={onDelete} disabled={del.isPending}>
      <Trash2 className="h-4 w-4" />
    </Button>
  </TooltipTrigger>
  <TooltipContent>Eliminar</TooltipContent>
</Tooltip>
```

**Copy table (from 102-UI-SPEC.md, authoritative):**

| Icon | Tooltip copy |
|---|---|
| `Eye` | "Ver detalhes" |
| `Pencil` | "Editar" |
| `Trash2` | "Eliminar" |
| `Printer` | "Imprimir" |
| Sidebar footer `LogOut` (`dashboard-shell.tsx:154,234`) | "Terminar sessão" |
| Mobile hamburger `Menu` (`dashboard-shell.tsx:244-251`) | Optional, already has `aria-label="Abrir menu"` — skip unless an equivalent desktop trigger is found |

---

## Shared Patterns

### Radix unified-package import convention (already established, Phase 101)
**Source:** every Radix-wrapper file in `web/src/components/ui/` (`dialog.tsx:4`, `alert-dialog.tsx:4`, `popover.tsx:3`, `radio-group.tsx:4`, `switch.tsx:4`, `label.tsx:2`, `tooltip.tsx:4`)
**Apply to:** no new files this phase; confirm no reconciliation step reintroduces a scoped `@radix-ui/react-*` import.
```typescript
import { Dialog as DialogPrimitive } from "radix-ui";
```

### `cn()` utility for class merging
**Source:** `web/src/lib/utils.ts` (imported identically in all 13 components: `import { cn } from "@/lib/utils";`)
**Apply to:** every component touched this phase — always merge `className` last via `cn(...)`, never string-concatenate.

### `data-slot` attribute convention
**Source:** every component in `web/src/components/ui/*` already carries `data-slot="<component-name>"` on its root element (e.g. `data-slot="card"`, `data-slot="badge"`, `data-slot="dialog-content"`). Preserve on every reconciled file — this is the shadcn/registry convention already correctly present, not something to add or remove.

### `--radius` token propagation
**Source:** `web/src/app/globals.css:42-48` (`@theme inline` block) + `:root` line 75 (`--radius: 0rem`)
```css
--radius-sm: calc(var(--radius) * 0.6);
--radius-md: calc(var(--radius) * 0.8);
--radius-lg: var(--radius);
```
**Apply to:** every `rounded-none` → `rounded-lg` substitution (button is Rule C so exempt; card/dialog/alert-dialog get this). `rounded-lg` resolves through `--radius-lg` → `var(--radius)` → `0rem`, visually identical to today's `rounded-none` but token-driven going forward.

### Elevated-surface tokens (`--card`/`--popover`) vs. flat `--background`
**Source:** `web/src/app/globals.css` — light (`:root`, lines 54-57): `--card: oklch(1 0 0)`, `--popover: oklch(1 0 0)` (both = white, matching `bg-white` exactly). Dark (`.dark`, lines 89-92): `--card: oklch(0.205 0 0)`, `--popover: oklch(0.205 0 0)` (a visibly lighter gray than `--background: #020617`).
**Apply to:** `card.tsx`, `dialog.tsx`, `alert-dialog.tsx`, `popover.tsx`, optionally `sheet.tsx` — every one of these currently hardcodes `dark:bg-[#020617]`/`dark:bg-slate-950` (both literally `#020617`, i.e. today's flat `--background` dark value) instead of the token. This is Rule B: a real, expected visual improvement (adds elevation contrast in dark mode), not a bug — call it out at the mandatory human visual checkpoint.

### TooltipProvider — global mount, single instance
**Source:** `web/src/components/ui/tooltip.tsx:8-19` (already installed, Phase 101)
```typescript
function TooltipProvider({ delayDuration = 0, ...props }: React.ComponentProps<typeof TooltipPrimitive.Provider>) {
  return <TooltipPrimitive.Provider data-slot="tooltip-provider" delayDuration={delayDuration} {...props} />;
}
```
**Apply to:** mount once in `web/src/app/providers.tsx` with `delayDuration={700}` explicit override — every consumer (`Tooltip`/`TooltipTrigger`/`TooltipContent`) elsewhere in the app relies on this single provider, do not add a second nested `TooltipProvider` anywhere.

---

## No Analog Found

None — all 18 files in scope have a direct, exact analog (their own current content, or the specific cross-reference file named above). No net-new component pattern is being introduced from scratch this phase.

## Metadata

**Analog search scope:** `web/src/components/ui/*.tsx` (all 30 files enumerated for completeness), `web/src/app/providers.tsx`, `web/src/app/layout.tsx`, `web/package.json`, `web/src/components/shared/dashboard-shell.tsx`, `web/src/app/(dashboard)/{clientes,processos,pareceres,documentos}/page.tsx`, `web/src/app/globals.css`
**Files scanned:** 34 (13 target components + calendar.tsx + breadcrumb.tsx + tooltip.tsx + providers.tsx + layout.tsx + package.json + dashboard-shell.tsx + globals.css + 4 list-page consumer files + badge `gray` call-site grep across 3 files)
**Pattern extraction date:** 2026-07-15
