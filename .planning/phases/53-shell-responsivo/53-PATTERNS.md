# Phase 53: Shell Responsivo - Pattern Map

**Mapped:** 2026-06-21
**Files analyzed:** 3 (1 modify, 1 new shadcn primitive, 1 possible new component)
**Analogs found:** 3 / 3

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `web/src/components/shared/dashboard-shell.tsx` | component (shell) | request-response | self (already exists, full read done) | exact |
| `web/src/components/ui/sheet.tsx` | ui primitive | event-driven | `web/src/components/ui/dialog.tsx` | role-match |
| `web/src/components/shared/bottom-nav.tsx` (new, optional inline) | component | event-driven | `web/src/components/shared/dashboard-shell.tsx` NAV block | partial |

---

## Pattern Assignments

### `web/src/components/shared/dashboard-shell.tsx` (modify)

**Analog:** self — full file already read (lines 1–199).

**Existing imports pattern** (lines 1–19):
```tsx
"use client";

import Link from "next/link";
import { NotificationBell } from "@/components/shared/notification-bell";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import * as React from "react";
import {
  Building2,
  Calendar,
  FileText,
  Home,
  LifeBuoy,
  LogOut,
  Scale,
  Search,
  Settings,
  Users,
  Wallet,
} from "lucide-react";
```

**Additions needed to imports:**
- Add `Menu` to the lucide-react destructure block (hamburger icon)
- Add `Sheet, SheetContent, SheetTrigger` from `@/components/ui/sheet` once the primitive exists
- Optionally add `X` from lucide-react for sheet close button (or rely on SheetClose)

**State pattern to add** (after `const me = useMe();`, line 51):
```tsx
const [drawerOpen, setDrawerOpen] = React.useState(false);
```
Close drawer on route change by adding `pathname` to a useEffect dependency:
```tsx
React.useEffect(() => {
  setDrawerOpen(false);
}, [pathname]);
```

**Active state pattern** (lines 79–95) — copy verbatim for BottomNav:
```tsx
const active = pathname === item.href || (item.href === "/processos" && pathname.startsWith("/processos/dashboard"));
const Icon = item.icon;
// active classes:
"bg-blue-600/10 text-blue-400 dark:bg-blue-500/10 dark:text-blue-400 shadow-[inset_2px_0_0_0_theme(colors.blue.500)]"
// inactive classes:
"text-slate-400 hover:bg-slate-900 hover:text-slate-200 dark:hover:bg-slate-900/50"
// icon active color:  "text-blue-500"
// icon inactive color: "text-slate-500"
```

**Sidebar aside element** (lines 72–149) — the desktop sidebar to hide on mobile:
```tsx
<aside className="w-[270px] flex-shrink-0 text-white bg-slate-950 dark:bg-[#04091a] border-r border-slate-900/50 dark:border-slate-800/50 flex flex-col z-20">
```
Add `hidden md:flex` to this className so it disappears on mobile:
```tsx
<aside className="hidden md:flex w-[270px] flex-shrink-0 text-white bg-slate-950 ...">
```

**Top bar header** (lines 152–193) — mobile adaptations:

Current institution name block (line 161):
```tsx
<div className="text-[13px] font-medium text-slate-500 dark:text-slate-400 hidden md:flex items-center gap-2">
```
Already uses `hidden md:flex` — institution name already hidden on mobile. For mobile layout, add hamburger button before the search input that is only visible on mobile (`md:hidden`).

Search input wrapper (lines 153–159): hide on mobile:
```tsx
<div className="flex-1 max-w-md relative group hidden md:flex">
```

**User card pattern** (lines 124–148) — reuse inside Sheet drawer:
```tsx
<div className="mt-auto p-4">
  <div className="flex items-center gap-3 rounded-lg bg-slate-900/50 dark:bg-slate-900/30 px-3 py-3 border border-slate-800/50">
    {/* avatar, name, role, logout button — copy verbatim */}
  </div>
</div>
```

---

### `web/src/components/ui/sheet.tsx` (new, via `npx shadcn add sheet`)

**Analog:** `web/src/components/ui/dialog.tsx`

**Structural pattern** (dialog.tsx lines 1–13):
```tsx
"use client";

import * as React from "react";
import * as DialogPrimitive from "@radix-ui/react-dialog";
import { X } from "lucide-react";
import { cn } from "@/lib/utils";

const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogPortal = DialogPrimitive.Portal;
const DialogClose = DialogPrimitive.Close;
```
Sheet follows the same Radix UI primitive wrapping approach — shadcn CLI generates it with `@radix-ui/react-dialog` (Sheet IS a dialog variant). The CLI output will follow this exact pattern with `SheetContent` accepting a `side` prop (`"left"` for drawer).

**data-slot attributes pattern** — all shadcn components in this codebase use `data-slot="..."` on the inner element (e.g. `data-slot="dialog-content"`). Sheet will use `data-slot="sheet-content"`.

**Overlay animation pattern** (dialog.tsx lines 21–23):
```tsx
className={cn(
  "fixed inset-0 z-50 bg-black/50 data-[state=open]:animate-in data-[state=closed]:animate-out data-[state=closed]:fade-out-0 data-[state=open]:fade-in-0",
  className,
)}
```
Sheet overlay uses same `data-[state=*]:animate-*` Tailwind classes.

**Export pattern** (dialog.tsx lines 102–113): named exports only, no default export. Sheet will export: `Sheet, SheetClose, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetOverlay, SheetPortal, SheetTitle, SheetTrigger`.

**Installation command:**
```bash
npx shadcn add sheet
```
This installs `@radix-ui/react-dialog` (already a dep via dialog.tsx) and writes `web/src/components/ui/sheet.tsx` following the same conventions.

---

### `web/src/components/shared/bottom-nav.tsx` (new — may be inlined in dashboard-shell or extracted)

**Analog:** NAV array + active link block in `web/src/components/shared/dashboard-shell.tsx` (lines 37–44 and 82–96).

**NAV slice for bottom nav** (lines 37–44, first 5 items):
```tsx
const BOTTOM_NAV = NAV.slice(0, 5);
// Dashboard, Clientes, Processos, Agenda, Documentos
```

**Active state to replicate** (lines 79 and 88–89):
```tsx
const active = pathname === item.href || (item.href === "/processos" && pathname.startsWith("/processos/dashboard"));
// active item color: text-blue-400
// inactive item color: text-slate-400
```

**Structural pattern for bottom nav item** (derive from lines 82–96):
```tsx
<Link
  key={item.href}
  href={item.href}
  className={cn(
    "flex flex-col items-center gap-1 px-3 py-2 text-xs font-medium transition-colors",
    active ? "text-blue-400" : "text-slate-400"
  )}
>
  <Icon className={cn("h-5 w-5", active ? "text-blue-500" : "text-slate-500")} />
  <span>{item.label}</span>
</Link>
```

**Positioning and visibility:**
```tsx
<nav className="fixed bottom-0 left-0 right-0 z-50 flex md:hidden bg-slate-950 border-t border-slate-800/60 pb-safe">
```
`pb-safe` or `pb-[env(safe-area-inset-bottom)]` for iOS home bar clearance.

---

## Shared Patterns

### `cn()` conditional classes
**Source:** `web/src/components/shared/dashboard-shell.tsx` line 25 + usage throughout
**Apply to:** all new JSX in this phase
```tsx
import { cn } from "@/lib/utils";
// usage:
className={cn("base-classes", condition && "conditional-class")}
```

### Dark mode color tokens
**Source:** `web/src/components/shared/dashboard-shell.tsx` lines 72, 88–89, 125
**Apply to:** Sheet drawer content, BottomNav background
```
bg-slate-950 dark:bg-[#04091a]          — sidebar/drawer background
border-slate-900/50 dark:border-slate-800/50  — borders
text-slate-400 / hover:text-slate-200   — inactive nav items
text-blue-400                            — active nav items
bg-blue-600/10 dark:bg-blue-500/10      — active nav item background
```

### `"use client"` directive
**Source:** every component in `web/src/components/`
**Apply to:** all new component files
Must be first line before imports.

### Permission-gated nav filtering
**Source:** `web/src/components/shared/dashboard-shell.tsx` lines 78
**Apply to:** BottomNav — filter by `hasPermission` same as sidebar:
```tsx
{NAV.slice(0, 5)
  .filter((item) => hasPermission(me.data?.permissions, item.requiredPermission))
  .map((item) => { ... })}
```

### `useMe()` hook
**Source:** `web/src/components/shared/dashboard-shell.tsx` line 27 + 51
**Apply to:** BottomNav if it needs user permissions (or receive `permissions` as prop)
```tsx
import { useMe } from "@/hooks/use-me";
const me = useMe();
```

---

## No Analog Found

None — all files have strong analogs in the codebase.

---

## Metadata

**Analog search scope:** `web/src/components/`, `web/src/hooks/`, `web/src/lib/`
**Files scanned:** 4 (dashboard-shell.tsx, dialog.tsx, notification-bell glob, shared glob)
**Pattern extraction date:** 2026-06-21
