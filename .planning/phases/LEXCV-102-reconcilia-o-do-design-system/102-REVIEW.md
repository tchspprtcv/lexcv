---
phase: LEXCV-102-reconcilia-o-do-design-system
reviewed: 2026-07-16T01:52:00Z
depth: standard
files_reviewed: 14
files_reviewed_list:
  - web/package.json
  - web/src/app/(dashboard)/clientes/page.tsx
  - web/src/app/(dashboard)/settings/page.tsx
  - web/src/app/providers.tsx
  - web/src/components/shared/dashboard-shell.tsx
  - web/src/components/ui/alert-dialog.tsx
  - web/src/components/ui/breadcrumb.tsx
  - web/src/components/ui/button.tsx
  - web/src/components/ui/calendar.tsx
  - web/src/components/ui/card.tsx
  - web/src/components/ui/dialog.tsx
  - web/src/components/ui/popover.tsx
  - web/src/components/ui/sheet.tsx
  - web/src/components/ui/table.tsx
findings:
  critical: 0
  warning: 2
  info: 3
  total: 5
status: issues_found
---

# Phase LEXCV-102: Code Review Report

**Reviewed:** 2026-07-16T01:52:00Z
**Depth:** standard
**Files Reviewed:** 14
**Status:** issues_found

## Summary

Reviewed the 13 hand-rolled shadcn primitives reconciled in this phase (alert-dialog, breadcrumb, button, calendar, card, dialog, popover, sheet, table) plus the four call sites of the new Tooltip rollout (`providers.tsx`, `dashboard-shell.tsx`, `clientes/page.tsx`, `settings/page.tsx`) and `package.json`. Confirmed the actual diff against `7f970e4^..HEAD` for every file to separate genuinely new code from pre-existing content the required-reading set happened to include.

No BLOCKER-tier defects found. Rule-C identity colors (button `neutral-*` palette, badge variants — badge.tsx itself is untouched in this diff) are unchanged. The `--card`/`--popover` CSS-variable elevation swap is real: `--card`/`--popover` differ from `--background` in dark mode (`oklch(0.205 0 0)` vs `#020617`), so the described elevation effect is genuine, not a no-op. The `rounded-none` → `rounded-lg` tokenization is currently a visual no-op (global `--radius: 0rem`), which is expected/intentional (future-proofs for a later radius change) rather than a defect. The `buttonVariants` dedup between `button.tsx`/`calendar.tsx` is clean: single source of truth, no orphaned duplicate CVA definition, no dangling `cva` import left in `calendar.tsx`. The `Slot`/`SlotPrimitive` alias unification is correct and verified at runtime (`Slot.Root === Slot.Slot` in the installed `radix-ui` package). Moving `shadcn` to `devDependencies` is safe: it's only consumed via `@import "shadcn/tailwind.css"` in `globals.css` (resolved at build time through the package's `exports` map to `dist/tailwind.css`), and `web/Dockerfile`'s `pnpm install --frozen-lockfile` stage installs devDependencies before `pnpm build` runs.

Two real gaps were found, both directly caused by this phase's own changes: an accessibility inconsistency where the Tooltip/`aria-label` rollout for the sidebar logout button covers the desktop `<aside>` but was not applied to the identical button in the mobile `<Sheet>` drawer, and an incomplete tokenization in `popover.tsx`, which is the only reconciled surface primitive still hardcoding `bg-white` for light mode instead of migrating to the `bg-popover` token like its dialog/alert-dialog/card/sheet siblings did.

## Warnings

### WR-01: Mobile drawer logout button has no Tooltip and no accessible name (desktop counterpart does)

**File:** `web/src/components/shared/dashboard-shell.tsx:240` (compare to the desktop version at line 157)
**Issue:** This phase's Tooltip rollout added `<Tooltip>`/`<TooltipTrigger asChild>`/`<TooltipContent>` plus `aria-label="Terminar sessão"` to the desktop sidebar logout button (line 157). The functionally-identical logout button rendered inside the mobile `<Sheet>` drawer (line 240) was left untouched — it still has neither a Tooltip nor an `aria-label`, so on mobile this icon-only button (`<LogOut className="h-4 w-4" />` with no visible text) has no accessible name for screen readers, and no hover-affordance for sighted mouse users who open the drawer on a larger viewport. This is an inconsistency introduced by this exact phase (the diff shows the desktop button was edited, the mobile one was not), not a pre-existing issue.
**Fix:**
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

### WR-02: `popover.tsx` is the only reconciled surface primitive not fully migrated to the elevation token

**File:** `web/src/components/ui/popover.tsx:22`
**Issue:** The stated goal of this phase was to replace flat hardcoded surface colors with the `--card`/`--popover` tokens so light/dark elevation is driven by CSS variables. `card.tsx`, `dialog.tsx`, `alert-dialog.tsx`, and `sheet.tsx` were all migrated for *both* light and dark (`bg-card`/`bg-popover` used unconditionally, with a — redundant, see IN-01 — `dark:` repeat). `popover.tsx` only migrated the dark side (`dark:bg-popover`) and left the light-mode class hardcoded as `bg-white`:
```tsx
"z-50 rounded-md border border-slate-200 bg-white shadow-md outline-none dark:border-slate-800 dark:bg-popover",
```
Today this is visually identical (`:root { --popover: oklch(1 0 0); }` is pure white), but it's an inconsistent application of the phase's own reconciliation goal — if `--popover`'s light value is ever tuned independently of pure white (e.g. a subtle off-white to differentiate from card), `Popover` will silently drift out of sync with `Dialog`/`AlertDialog`/`Sheet`/`Card`.
**Fix:**
```tsx
"z-50 rounded-md border border-slate-200 bg-popover shadow-md outline-none dark:border-slate-800 dark:bg-popover",
```
(or simply drop the `dark:` variant entirely once merged, matching the pattern in `card.tsx`/`dialog.tsx`.)

## Info

### IN-01: Redundant `dark:bg-card` / `dark:bg-popover` variants

**File:** `web/src/components/ui/card.tsx:10`, `web/src/components/ui/dialog.tsx:41`, `web/src/components/ui/alert-dialog.tsx:39`, `web/src/components/ui/sheet.tsx:55`
**Issue:** `--card` and `--popover` are defined in both `:root` and `.dark` in `globals.css` (light: `oklch(1 0 0)`, dark: `oklch(0.205 0 0)`), so the bare utility (`bg-card`, `bg-popover`) already resolves to the correct color per theme without a `dark:` variant. Each of these four files repeats the identical class under `dark:` (e.g. `bg-card ... dark:bg-card`), which has no effect beyond the base class and reads as if there were an intentional dark-specific override when there isn't one. Low risk, but likely to confuse the next person who edits these files expecting the `dark:` class to do something.
**Fix:** Drop the redundant `dark:bg-card` / `dark:bg-popover` segments, e.g. in `card.tsx`:
```tsx
"rounded-lg border border-slate-200 bg-card text-slate-950 shadow-sm transition-all hover:shadow-md dark:border-slate-800 dark:text-slate-50"
```

### IN-02: Dead cache-invalidation code in `onLogout` (pre-existing, not introduced by this phase)

**File:** `web/src/components/shared/dashboard-shell.tsx:71-79`
**Issue:** Not part of this phase's diff, but present in a file under review:
```tsx
const onLogout = async () => {
  await clearTokens();

  // Invalidamos a cache do React Query para forçar a verificação de estado e limpar dados
  await import("@tanstack/react-query");
  // Mas não podemos chamar hook dentro de função callback.
  // ...
  window.location.href = "/login";
};
```
`await import("@tanstack/react-query")` dynamically imports the module and discards the result — it does not call `queryClient.clear()` or anything else. The comment claims cache invalidation happens here; it doesn't. In practice this is harmless because the following `window.location.href` triggers a full page reload that wipes all in-memory state anyway, but the dead import and misleading comment should be removed for clarity.
**Fix:** Remove the dead import and comment, or actually invalidate the query cache if that's the real intent:
```tsx
const onLogout = async () => {
  await clearTokens();
  window.location.href = "/login";
};
```

### IN-03: Tooltip on `disabled` trigger won't show (Radix limitation, newly exposed by this rollout)

**File:** `web/src/app/(dashboard)/clientes/page.tsx:640-656`
**Issue:** The "Eliminar" button is wrapped in `<TooltipTrigger asChild>` and also carries `disabled={del.isPending}`. Native `disabled` buttons don't fire pointer/focus events, which Radix's `Tooltip` relies on to open — so while `del.isPending` is `true` the tooltip silently won't appear on hover. The window is small (only during the in-flight delete mutation) so impact is minor, but it's a direct side effect of the new Tooltip wiring and worth being aware of if the same pattern (Tooltip wrapping a conditionally-disabled Button) is copied elsewhere with a longer-lived disabled state.
**Fix:** No action required for this transient case; if this pattern recurs with a longer-disabled trigger, wrap the disabled element in a non-interactive `<span>` as the `TooltipTrigger`'s child instead of putting `disabled` directly on the trigger, per Radix's documented workaround.

---

_Reviewed: 2026-07-16T01:52:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
