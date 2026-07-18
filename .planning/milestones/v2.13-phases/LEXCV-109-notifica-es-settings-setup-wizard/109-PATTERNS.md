# Phase 109: Notificações / Settings / Setup Wizard - Pattern Map

**Mapped:** 2026-07-17
**Files analyzed:** 4 (3 modified + 1 new shared component)
**Analogs found:** 4 / 4

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `web/src/components/shared/user-menu.tsx` (new) | component (dropdown trigger+content) | event-driven (click → navigate/logout) | `web/src/components/shared/data-table/data-table-view-options.tsx` (structural: only real `DropdownMenu` consumer in-repo) + `web/src/components/shared/dashboard-shell.tsx` lines 135-164 (trigger visuals + `onLogout`/initials logic to extract) | role-match (structural) / exact (content-to-extract) |
| `web/src/components/shared/dashboard-shell.tsx` (modified, 3 call sites) | component (app shell / nav) | request-response (nav links) + event-driven (logout) | itself (pre-edit) — 3 near-duplicate inline blocks being consolidated into `UserMenu` | exact (refactor-in-place) |
| `web/src/components/shared/notification-bell.tsx` (modified, lines 88-95 only) | component (status indicator) | transform (unread count → badge text/color) | itself, line 36 — `<Badge variant={categoriaToBadgeVariant(n.categoria)}>` (same-file existing `Badge` usage) | exact |
| `web/src/app/setup/page.tsx` (modified, lines 261-270) | component (wizard step indicator) | transform (form/submit state → percentage) | `web/src/app/(dashboard)/documentos/novo/page.tsx` lines 181-189 (`Progress` + label/percentage row, upload-progress usage) | role-match (same primitive, different signal source) |

## Pattern Assignments

### `web/src/components/shared/user-menu.tsx` (new component, event-driven)

**Primary structural analog:** `web/src/components/shared/data-table/data-table-view-options.tsx` (the only existing `DropdownMenu` consumer in `web/src` — confirmed via repo-wide grep; this is the real first-of-kind precedent, more concrete than `109-CONTEXT.md`'s "zero consumers" claim, which was written before/without this file in scope). Use it for **how to wire `DropdownMenuTrigger asChild` around an existing interactive-looking element**, and **content extraction:** dashboard-shell.tsx lines 135-164/220-249/294-311 for the exact avatar/name JSX and `onLogout`/initials logic to preserve verbatim inside the trigger.

**Imports pattern** (from `data-table-view-options.tsx` lines 1-13, adapted):
```typescript
"use client";

import Link from "next/link";
import { LogOut, Settings } from "lucide-react";

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import type { MeResponse } from "@/types/auth";
```
(`Link`/`Settings`/`LogOut` already imported today in `dashboard-shell.tsx` lines 3/13/18 — reuse verbatim per `109-CONTEXT.md`'s lock; no new icon needed for "Perfil", per UI-SPEC.)

**Trigger composition pattern** (`data-table-view-options.tsx` lines 36-53) — the load-bearing precedent for wrapping a *non-Button* element as `asChild`:
```tsx
<DropdownMenu>
  <DropdownMenuTrigger asChild>
    <Button ...>
      <SlidersHorizontal className="h-4 w-4" />
    </Button>
  </DropdownMenuTrigger>
  <DropdownMenuContent align="end" className="w-[180px]">
    ...
  </DropdownMenuContent>
</DropdownMenu>
```
Apply the same `<DropdownMenu><DropdownMenuTrigger asChild><button type="button" ...>{existing avatar+name JSX}</button></DropdownMenuTrigger><DropdownMenuContent .../></DropdownMenu>` shape. Since the existing trigger is a `<div>`/`<Link>` combo rather than a single `Button`, wrap it in one non-navigating `<button type="button" className="flex items-center gap-3 ...">` (or `<div role="button" tabIndex={0}>`) per UI-SPEC's Component Inventory recommendation — do not keep the inner `<Link href="/settings">`s, since the whole block stops navigating directly.

**Content-to-extract from `dashboard-shell.tsx`** (topbar instance, lines 294-311 — same shape repeats at 135-164 and 220-249):
```tsx
<div className="flex items-center gap-3 pl-1">
  <div className="text-right leading-tight hidden sm:block">
    <Link href="/settings" className="text-sm font-semibold text-slate-900 dark:text-slate-100 hover:underline">{me.data?.nome ?? "—"}</Link>
    <div className="text-[11px] text-slate-500 dark:text-slate-400 font-medium">{(me.data?.roles?.[0] ?? "—").toString()}</div>
  </div>
  <Link href="/settings" className="h-9 w-9 rounded-full bg-gradient-to-tr from-blue-600 to-indigo-500 flex items-center justify-center text-xs font-bold text-white shadow-sm ring-2 ring-white dark:ring-[#020617] overflow-hidden hover:opacity-90 transition-opacity">
    {me.data?.avatar_url ? (
      <img src={me.data.avatar_url} alt="Avatar" className="w-full h-full object-cover" />
    ) : (
      (me.data?.nome ?? "U").split(" ").slice(0, 2).map((p) => p[0]).join("").toUpperCase()
    )}
  </Link>
</div>
```
Sidebar variant (lines 135-154, square avatar `rounded-md bg-slate-800`) and the adjacent logout button to fold into the menu (lines 155-162):
```tsx
<Tooltip>
  <TooltipTrigger asChild>
    <Button type="button" variant="ghost" aria-label="Terminar sessão" className="h-8 w-8 p-0 text-slate-400 hover:text-white hover:bg-slate-800 transition-colors" onClick={onLogout}>
      <LogOut className="h-4 w-4" />
    </Button>
  </TooltipTrigger>
  <TooltipContent>Terminar sessão</TooltipContent>
</Tooltip>
```
The `onClick={onLogout}` here is exactly what becomes `<DropdownMenuItem onSelect={onLogout}>` — `onLogout` itself (dashboard-shell.tsx lines 71-79) is passed through unchanged as a prop, not re-implemented.

**Core menu-items pattern** (per UI-SPEC Component Inventory, locked order Perfil → Configurações → separator → Terminar sessão):
```tsx
<DropdownMenuContent className="w-56" align={variant === "topbar" ? "end" : "start"}>
  <DropdownMenuItem asChild>
    <Link href="/profile">Perfil</Link>
  </DropdownMenuItem>
  <DropdownMenuItem asChild>
    <Link href="/settings">
      <Settings className="mr-2 h-4 w-4" />
      Configurações
    </Link>
  </DropdownMenuItem>
  <DropdownMenuSeparator />
  <DropdownMenuItem variant="default" onSelect={onLogout}>
    <LogOut className="mr-2 h-4 w-4" />
    Terminar sessão
  </DropdownMenuItem>
</DropdownMenuContent>
```
(`DropdownMenuItem`'s `variant` prop, `"default" | "destructive"`, is defined in `web/src/components/ui/dropdown-menu.tsx` lines 61-82 — explicitly use `"default"`, per UI-SPEC's Color contract reservation of `destructive` for irreversible actions only.)

**Error handling / validation:** none needed — this is a pure presentational client component, no async/try-catch of its own (`onLogout` itself already handles its own flow in `dashboard-shell.tsx`, unchanged).

---

### `web/src/components/shared/dashboard-shell.tsx` (modified, 3 call sites → `UserMenu` consumer)

**Analog:** itself, pre-edit — the file already has the "same block repeated 3×" pattern for `NAV` filtering (`hasPermission(me.data?.permissions, item.requiredPermission)`, lines 89 and 174, unchanged) and for the avatar/name/logout block (lines 135-164, 220-249, 294-311, being consolidated).

**Replacement pattern per call site:**
```tsx
{/* topbar, replaces lines 294-311 */}
<UserMenu variant="topbar" me={me.data} onLogout={onLogout} />

{/* desktop sidebar footer, replaces the div at lines 136-163 (keeps outer bg-slate-900/50 wrapper) */}
<div className="flex items-center gap-3 rounded-lg bg-slate-900/50 dark:bg-slate-900/30 px-3 py-3 border border-slate-800/50">
  <UserMenu variant="sidebar" me={me.data} onLogout={onLogout} />
</div>

{/* mobile Sheet footer, replaces lines 221-248, identical shape */}
```
`Tooltip`/`TooltipContent`/`TooltipTrigger` imports (lines 26) become unused once both sidebar/Sheet logout buttons are removed — remove the import if nothing else in the file still uses `Tooltip` (grep the file first; as of this read, `Tooltip` is used **only** at the 2 logout-button sites being removed, so the import should be dropped).

**Error handling:** none new — `onLogout` (lines 71-79) is unchanged, just passed as a prop instead of wired to an inline `onClick`.

---

### `web/src/components/shared/notification-bell.tsx` (modified, lines 88-95 only)

**Analog:** itself, line 36 — the file already imports and uses `Badge` for the category chip:
```tsx
<Badge variant={categoriaToBadgeVariant(n.categoria)} className="flex-shrink-0">
  {categoriaToLabel(n.categoria)}
</Badge>
```
This confirms the import (`import { Badge } from "@/components/ui/badge";`, line 8) is already present — no new import required for the counter migration.

**Current code to replace** (lines 87-95):
```tsx
{showBadge && (
  <span
    className={`absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full text-[10px] font-bold text-white flex items-center justify-center leading-none ${
      unread.isError ? "bg-slate-400" : "bg-red-500"
    }`}
  >
    {unread.isError ? "!" : count > 9 ? "9+" : count}
  </span>
)}
```

**Target pattern** (per UI-SPEC Component Inventory, `Badge` from `web/src/components/ui/badge.tsx`, default/`"secondary"` variant as base, overridden via `className`):
```tsx
{showBadge && (
  <Badge
    className={cn(
      "absolute -top-0.5 -right-0.5 h-4 w-4 rounded-full p-0 flex items-center justify-center text-[10px] font-bold leading-none text-white border-transparent",
      unread.isError ? "bg-slate-400" : "bg-red-500",
    )}
  >
    {unread.isError ? "!" : count > 9 ? "9+" : count}
  </Badge>
)}
```
Note: `cn` is not currently imported in this file (confirmed — imports are `Bell/Check/CheckCheck` from lucide-react, `Link`, `React`, `NotificacaoSnoozeControl`, `Badge`, `Button`, `Popover*`, `toast`, notificacao hooks/lib/types; no `@/lib/utils` import). Add `import { cn } from "@/lib/utils";` (pattern used project-wide, e.g. `dashboard-shell.tsx` line 29) — or use a plain template string as the current `<span>` does, since `Badge`'s own `badgeVariants` base classes and the override use disjoint Tailwind property groups here (`bg-neutral-100`→`bg-red-500`/`bg-slate-400` is same-property so `cn`/`twMerge` resolution is what makes this correct per UI-SPEC Verified-against-source finding #4 — prefer `cn` over a raw template string for correctness).

**Badge component definition reference** (`web/src/components/ui/badge.tsx` lines 6-29, `"secondary"` is `defaultVariants`):
```typescript
const badgeVariants = cva(
  "inline-flex items-center rounded-full border px-2.5 py-0.5 text-xs font-medium",
  {
    variants: {
      variant: {
        secondary: "border-transparent bg-neutral-100 text-neutral-900 dark:bg-neutral-800 dark:text-neutral-50",
        red: "border-transparent bg-red-100 text-red-700 dark:bg-red-500/20 dark:text-red-400",
        // ... other variants, `red` explicitly rejected per 109-CONTEXT.md (too soft)
      },
    },
    defaultVariants: { variant: "secondary" },
  },
);
```

**Error handling / validation:** none — `showBadge`/`unread.isError`/`count` logic (lines 50-52) is unchanged, only the rendered element changes.

---

### `web/src/app/setup/page.tsx` (modified, lines 261-270 → `Progress` block)

**Analog:** `web/src/app/(dashboard)/documentos/novo/page.tsx` lines 181-189 — the only other in-repo consumer of `Progress`, showing the established "label+percentage row above the bar" composition:
```tsx
{progresso !== null ? (
  <div className="space-y-1">
    <div className="flex justify-between text-xs text-neutral-500">
      <span>A enviar...</span>
      <span>{progresso}%</span>
    </div>
    <Progress value={progresso ?? 0} />
  </div>
) : null}
```
(imported there via `import { Progress } from "@/components/ui/progress";`)

**`Progress` primitive definition** (`web/src/components/ui/progress.tsx` lines 8-29) — confirms `value` is 0-100, fill is `bg-primary` (no override needed, per UI-SPEC Color contract):
```tsx
function Progress({ className, value, ...props }: React.ComponentProps<typeof ProgressPrimitive.Root>) {
  return (
    <ProgressPrimitive.Root
      data-slot="progress"
      className={cn("relative flex h-1.5 w-full items-center overflow-x-hidden rounded-full bg-muted", className)}
      {...props}
    >
      <ProgressPrimitive.Indicator
        data-slot="progress-indicator"
        className="size-full flex-1 bg-primary transition-all"
        style={{ transform: `translateX(-${100 - (value || 0)}%)` }}
      />
    </ProgressPrimitive.Root>
  );
}
```

**Current code to replace** (`setup/page.tsx` lines 261-270):
```tsx
<div className="space-y-3 border border-slate-200 bg-slate-50 p-5 dark:border-slate-800 dark:bg-[#020617]">
  <div className="text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
    Checklist
  </div>
  <div className="space-y-3 text-sm leading-6 text-slate-600 dark:text-slate-400">
    <p>1. Criação do primeiro tenant institucional.</p>
    <p>2. Criação do utilizador administrador com password hasheada.</p>
    <p>3. Fecho definitivo do wizard após sucesso.</p>
  </div>
</div>
```

**Target pattern** (per UI-SPEC Component Inventory, exact recommended shape, phase derived from existing `form.formState.isSubmitting`/`successMessage` — no new business state):
```tsx
const wizardPhase: "idle" | "submitting" | "success" =
  successMessage ? "success" : form.formState.isSubmitting ? "submitting" : "idle";
const wizardProgress = wizardPhase === "success" ? 100 : wizardPhase === "submitting" ? 66 : 33;

// ...

<div className="space-y-3 border border-slate-200 bg-slate-50 p-5 dark:border-slate-800 dark:bg-[#020617]">
  <div className="flex items-center justify-between text-xs font-semibold uppercase tracking-[0.22em] text-slate-500 dark:text-slate-400">
    <span>Progresso</span>
    <span>{wizardProgress}%</span>
  </div>
  <Progress value={wizardProgress} />
  <div className="space-y-3 text-sm leading-6 text-slate-600 dark:text-slate-400">
    <p>1. Criação do primeiro tenant institucional.</p>
    <p>2. Criação do utilizador administrador com password hasheada.</p>
    <p>3. Fecho definitivo do wizard após sucesso.</p>
  </div>
</div>
```
New import required (confirmed absent today — current imports at top of file are `Button`, `Card*`, `Input`, `Label`, `apiFetch`, `setupSchema`, types only):
```typescript
import { Progress } from "@/components/ui/progress";
```

**Error handling:** unchanged — `serverError` (lines 281-285) stays its own separate block; `wizardPhase` deliberately does not add a distinct "error" branch (a failed submit leaves `isSubmitting=false`/`successMessage=null`, falling back to `"idle"`/33%, per UI-SPEC).

---

## Shared Patterns

### `cn()` utility for Tailwind class-conflict-safe overrides
**Source:** `web/src/lib/utils.ts` lines 4-6 (`twMerge(clsx(inputs))`)
**Apply to:** `notification-bell.tsx`'s `Badge` className override (new import needed there); already used throughout `dashboard-shell.tsx` (line 29) and available for `user-menu.tsx` if conditional classes are needed on the trigger wrapper.

### `DropdownMenuTrigger asChild` wrapping a non-`Button` element
**Source:** `web/src/components/shared/data-table/data-table-view-options.tsx` lines 36-53 (the sole existing `DropdownMenu` consumer)
**Apply to:** `user-menu.tsx` — same `asChild` mechanics apply whether the child is a `Button` (existing analog) or a plain `<button>`/`<div role="button">` wrapping the avatar+name block (this phase's actual need); `DropdownMenuContent` `align`/`className="w-..."` override pattern (`align="end" className="w-[180px]"` there → `align={variant === "topbar" ? "end" : "start"} className="w-56"` here) carries over directly.

### Radix default collision handling (no hardcoded `side`)
**Source:** `notification-bell.tsx`'s `Popover`/`PopoverContent` (no `side` prop, relies on `avoidCollisions`) and `data-table-view-options.tsx`'s `DropdownMenuContent` (same, no `side` prop)
**Apply to:** `UserMenu`'s `DropdownMenuContent` in both sidebar-footer instances — do not add `side="top"`, let Radix auto-flip.

### Icon-adjacent-to-label spacing (`mr-2 h-4 w-4`)
**Source:** `dashboard-shell.tsx` nav items, e.g. line 103 `<Icon className={cn("h-4 w-4", ...)} />` inside a `gap-3` flex row
**Apply to:** `DropdownMenuItem`'s `Settings`/`LogOut` icons in `user-menu.tsx` (`mr-2 h-4 w-4`, per UI-SPEC Component Inventory, not `gap-3` since `DropdownMenuItem` already ships its own `gap-2` on the flex row — an explicit `mr-2` on the icon is the correct/consistent choice per UI-SPEC's Spacing Scale note).

## No Analog Found

None. All 4 files/components have a workable in-repo analog (structural or same-file), per the table above — no file requires falling back to RESEARCH.md/UI-SPEC-only guidance.

## Metadata

**Analog search scope:** `web/src/components/shared/`, `web/src/components/ui/` (`dropdown-menu.tsx`, `badge.tsx`, `progress.tsx`, `select.tsx`), `web/src/app/(dashboard)/documentos/novo/page.tsx`, `web/src/app/setup/page.tsx`, `web/src/app/(dashboard)/profile/page.tsx`, `web/src/hooks/use-me.ts`, `web/src/lib/utils.ts`
**Files scanned:** 11 read/grepped (dashboard-shell.tsx, notification-bell.tsx, setup/page.tsx, dropdown-menu.tsx, badge.tsx, progress.tsx, select.tsx, data-table-view-options.tsx, documentos/novo/page.tsx, profile/page.tsx, use-me.ts, utils.ts)
**Pattern extraction date:** 2026-07-17

**Correction to 109-CONTEXT.md/109-UI-SPEC.md's "zero DropdownMenu consumers" claim:** a repo-wide grep for `DropdownMenuTrigger asChild` found one existing consumer, `web/src/components/shared/data-table/data-table-view-options.tsx` (column-visibility toggle, `DropdownMenuCheckboxItem`-based). This does not change the phase's scope or decisions — it is not a user-menu-shaped consumer and doesn't cover `Link`-based `DropdownMenuItem asChild` navigation — but it is a stronger structural analog than "no precedent" for the Radix wiring mechanics (`asChild` trigger, `DropdownMenuContent` `align`/width override), and is cited above accordingly.
