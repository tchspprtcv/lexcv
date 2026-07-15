---
phase: LEXCV-101-funda-o-cli-init-e-design-tokens
reviewed: 2026-07-15T00:00:00Z
depth: standard
files_reviewed: 34
files_reviewed_list:
  - web/components.json
  - web/package.json
  - web/src/app/globals.css
  - web/src/app/layout.tsx
  - web/src/components/ui/accordion.tsx
  - web/src/components/ui/alert-dialog.tsx
  - web/src/components/ui/avatar.tsx
  - web/src/components/ui/breadcrumb.tsx
  - web/src/components/ui/button.tsx
  - web/src/components/ui/calendar.tsx
  - web/src/components/ui/checkbox.tsx
  - web/src/components/ui/command.tsx
  - web/src/components/ui/dialog.tsx
  - web/src/components/ui/dropdown-menu.tsx
  - web/src/components/ui/empty.tsx
  - web/src/components/ui/input-group.tsx
  - web/src/components/ui/label.tsx
  - web/src/components/ui/native-select.tsx
  - web/src/components/ui/navigation-menu.tsx
  - web/src/components/ui/popover.tsx
  - web/src/components/ui/progress.tsx
  - web/src/components/ui/radio-group.tsx
  - web/src/components/ui/select.tsx
  - web/src/components/ui/separator.tsx
  - web/src/components/ui/sheet.tsx
  - web/src/components/ui/skeleton.tsx
  - web/src/components/ui/sonner.tsx
  - web/src/components/ui/switch.tsx
  - web/src/components/ui/tabs.tsx
  - web/src/components/ui/tooltip.tsx
  - web/src/hooks/use-toast.ts
  - webpage/components.json
  - webpage/package.json
  - webpage/src/app/globals.css
findings:
  critical: 0
  warning: 3
  info: 5
  total: 8
status: issues_found
---

# Phase LEXCV-101: Code Review Report

**Reviewed:** 2026-07-15T00:00:00Z
**Depth:** standard
**Files Reviewed:** 34
**Status:** issues_found

## Summary

Reviewed the shadcn/ui CLI initialization (`-b radix`) in `web/` and `webpage/`, the new semantic design tokens in both `globals.css` files, the 17 newly-added shadcn primitives, the unified `radix-ui` import migration across 8 previously hand-rolled components, and the Toast → Sonner swap (`use-toast.ts`, `sonner.tsx`, deletion of `toast.tsx`/`toaster.tsx`).

**CSS cascade fix verification (explicitly requested):** Confirmed by reading actual declaration order (not just diffing hex strings) in both files. In `web/src/app/globals.css`, `:root { ... }` is declared at lines 51-84 and `.dark { ... }` at lines 86-118 — `:root` now precedes `.dark`. Same order in `webpage/src/app/globals.css` (`:root` at 50-83, `.dark` at 85-117). Because `:root` and `.dark` have identical specificity (0-1-0) and both match `<html class="dark">` simultaneously, source order is the tie-breaker; with `.dark` now declared *after* `:root`, `.dark`'s values correctly win when dark mode is active, and `:root` alone applies when it isn't (since `.dark` doesn't match at all in that case). Cross-checked with `providers.tsx` (`next-themes` with `attribute="class"`) and confirmed no other `:root`/`.dark` blocks exist anywhere else in the repo that could reintroduce the same class of bug. **The fix is correct and complete in both files.**

No critical/security issues were found (no secrets, no `eval`/`innerHTML`/dangerous-function usage, no empty catch blocks, no leftover imports of the deleted `toast.tsx`/`toaster.tsx`, no residual direct `@radix-ui/*` imports left over from the migration in `web/src`). The `toast.success()`/`toast.error()` call-site contract is preserved correctly — verified against all 147 call sites across 27 files; none pass an options shape incompatible with sonner's `ExternalToast`.

The issues found are all Warning/Info-level: a couple of genuine implementation-vs-intent mismatches in newly added files, an unfinished/partial migration of some legacy primitives to the new semantic tokens (acknowledged in-code as explicitly out of scope for this phase, but still worth tracking), and some minor dead-code/consistency nits.

## Warnings

### WR-01: `EmptyDescription` is typed as a paragraph but renders a `<div>`

**File:** `web/src/components/ui/empty.tsx:71-82`
**Issue:** `EmptyDescription` is declared as `React.ComponentProps<"p">` (i.e. it advertises paragraph semantics/props) but its implementation renders a `<div>`, unlike every sibling in this file (`EmptyTitle`, `EmptyContent`, `EmptyHeader` are consistently typed *and* rendered as `<div>`). This is very likely a copy-paste artifact from `EmptyContent`. Not a crash, but it's a real type/implementation mismatch: any consumer relying on the declared type for a text/description block gets a `<div>` instead of a `<p>`, which affects default semantics/style inheritance (`text-sm/relaxed` etc. still work, but accessibility tooling and any global `p { ... }` selectors elsewhere in the app will not apply here as expected).
**Fix:**
```tsx
function EmptyDescription({ className, ...props }: React.ComponentProps<"p">) {
  return (
    <p
      data-slot="empty-description"
      className={cn(
        "text-sm/relaxed text-muted-foreground [&>a]:underline [&>a]:underline-offset-4 [&>a:hover]:text-primary",
        className
      )}
      {...props}
    />
  )
}
```

### WR-02: Half-migrated design tokens leave two disconnected color systems

**File:** `web/src/components/ui/alert-dialog.tsx:39`, `web/src/components/ui/dialog.tsx:41`, `web/src/components/ui/sheet.tsx:55`, `web/src/components/ui/popover.tsx:22`, `web/src/components/ui/switch.tsx:16`, `web/src/components/ui/radio-group.tsx:29`, `web/src/components/ui/button.tsx:12-17`
**Issue:** These 7 files were touched in this phase *only* to swap the import (`@radix-ui/react-*` → the unified `radix-ui` package); their Tailwind classes were left untouched (confirmed via `git diff 525eac9..HEAD` — each file has a 1-2 line diff, import-only). They still hardcode the old Tailwind neutral/slate palette and even a magic hex literal (`dark:bg-[#020617]` in both `dialog.tsx` and `alert-dialog.tsx`, which duplicates — without referencing — the new `--background` dark-mode token defined in `globals.css`). Meanwhile every other primitive touched/added in this same "design tokens" phase (`accordion.tsx`, `command.tsx`, `dropdown-menu.tsx`, `select.tsx`, `tabs.tsx`, `tooltip.tsx`, `navigation-menu.tsx`, etc.) consistently uses the new semantic tokens (`bg-popover`, `text-popover-foreground`, `bg-muted`, `ring-foreground/10`, etc.). Net effect: the app now has two parallel, disconnected color systems. If/when the design tokens are retuned (e.g. `--popover` or `--background` dark values change), `Dialog`/`AlertDialog`/`Sheet`/`Popover`/`Switch`/`RadioGroup`/`Button` will silently *not* follow, producing visual drift and inconsistent theming with zero compile-time signal. This is explicitly flagged as intentionally out-of-scope for Phase 101 in the `calendar.tsx` comment, but it is a real, provable follow-up risk that should be tracked rather than forgotten.
**Fix:** Track as an explicit follow-up item (it may already be in `deferred-items.md` — please confirm) to re-skin these 7 components onto the semantic tokens, e.g. replace `bg-white ... dark:bg-[#020617]` with `bg-popover text-popover-foreground`, `border-neutral-300 ... dark:border-neutral-700` with `border-input`, etc., matching the pattern already used in `select.tsx`/`dropdown-menu.tsx`.

### WR-03: Unsafe double type-cast to attach a ref to a non-forwardRef `Button`

**File:** `web/src/components/ui/calendar.tsx:47-53, 238`
**Issue:**
```ts
const ButtonWithRef = Button as unknown as React.ForwardRefExoticComponent<
  ButtonProps & React.RefAttributes<HTMLButtonElement>
>
```
`Button` (`button.tsx:39`) is a plain function component that does not destructure or explicitly type a `ref` prop; the cast forces TypeScript to treat it as if it were built with `React.forwardRef`, which it is not. The code comment explains this relies on React 19's "ref-as-prop" behavior forwarding `ref` through `{...props}` to the host `<button>`/`Slot` element regardless — which is plausible, but it is an *undocumented, implicit* runtime assumption hidden behind an `as unknown as` cast, i.e. exactly the "defeats type safety" pattern this review is asked to flag. If `Button` is ever refactored to explicitly destructure/allowlist its props (a very natural refactor, e.g. to add prop validation or default values), `ref` would silently stop reaching the host element, breaking the calendar's roving-tabindex keyboard focus management (`CalendarDayButton`'s `useEffect` that calls `ref.current?.focus()`) with no type error to catch the regression — only a runtime a11y/UX bug discovered later.
**Fix:** Either give `Button` (`button.tsx`) an explicit `React.forwardRef` signature so the ref path is statically verified, or, if `button.tsx` must stay untouched per phase scope, isolate the assumption behind a narrower, explicitly-commented helper and add a runtime dev-mode warning (or a unit/interaction test asserting focus moves to the day button) so a future `Button` refactor that breaks ref passthrough is caught by tests rather than silently.

## Info

### IN-01: Dead `cn-toast` class name in Sonner Toaster config

**File:** `web/src/components/ui/sonner.tsx:40-44`
**Issue:** `toastOptions.classNames.toast: "cn-toast"` applies a CSS class that is not defined anywhere in the repo (confirmed via repo-wide search — the only occurrence of `cn-toast` is this one line). It is a no-op today.
**Fix:** Either remove the dead `classNames` override, or add the intended `.cn-toast` rule to `globals.css` if custom styling was actually meant to land here.

### IN-02: `useToast()` hook and bare `toast()` wrapper are unused dead code

**File:** `web/src/hooks/use-toast.ts:19-21, 40-45`
**Issue:** Across all 27 files that import from `@/hooks/use-toast`, every call site uses `toast.success(...)` or `toast.error(...)`; the bare `toast(message, options)` function and the `useToast()` hook have zero call sites in the codebase. This is intentional per the in-file comment (kept as a forward-compatibility shim), so this is a low-priority note rather than a defect, but it's worth flagging explicitly since "unused export" is otherwise a code-quality signal reviewers should not silently pass over.
**Fix:** No action required if the compatibility-shim rationale is accepted; consider a one-line note in `101-PATTERNS.md`/`deferred-items.md` cross-referencing this so a future contributor doesn't independently "clean up" what looks like dead code.

### IN-03: Inconsistent `Slot` primitive aliasing style

**File:** `web/src/components/ui/breadcrumb.tsx:4, 48`, `web/src/components/ui/button.tsx:1, 40`
**Issue:** `breadcrumb.tsx` does `import { Slot } from "radix-ui"` then uses `Slot.Root`, while `button.tsx` does `import { Slot as SlotPrimitive } from "radix-ui"` then uses `SlotPrimitive.Slot`. Both resolve to the exact same component (`@radix-ui/react-slot` exports `Slot` and `Root` as aliases of one another), so this is functionally harmless, but it's an avoidable inconsistency introduced by the same migration pass, and it diverges from the `XPrimitive.Root` convention used everywhere else in the migrated files (`AlertDialogPrimitive.Root`, `DialogPrimitive.Root`, etc.).
**Fix:** Standardize on one alias/property pair (e.g. `SlotPrimitive` + `.Root`) across both files.

### IN-04: `buttonVariants` CVA config duplicated verbatim between `button.tsx` and `calendar.tsx`

**File:** `web/src/components/ui/calendar.tsx:21-45`, `web/src/components/ui/button.tsx:7-31`
**Issue:** `calendar.tsx` copy-pastes the entire `buttonVariants` CVA definition from `button.tsx` (byte-for-byte identical Tailwind class strings) because `button.tsx` doesn't export its internal `buttonVariants`. This is already called out in an in-file comment as a "documented, bounded duplication," so it's not a surprise to the next reader, but it is still a maintenance liability — any future restyle of `Button` (`button.tsx`) will not propagate to the calendar nav/day buttons unless someone remembers to update this second copy.
**Fix:** Export `buttonVariants` from `button.tsx` and import it in `calendar.tsx` instead of duplicating the CVA config, once `button.tsx` is back in scope for editing.

### IN-05: `shadcn` CLI package declared under `dependencies` instead of `devDependencies`

**File:** `web/package.json:27` (inside the `dependencies` block, lines 12-32)
**Issue:** `"shadcn": "^4.13.0"` is a CLI/build-time tool (used to scaffold components and via the `@import "shadcn/tailwind.css"` build-time CSS import) — its sibling build tools `tailwindcss` and `@tailwindcss/postcss` are correctly placed under `devDependencies` (lines 33-42), but `shadcn` is not. This unnecessarily ships a CLI package (and its transitive dependency tree) into production installs in any deployment pipeline that runs `pnpm install --prod`.
**Fix:** Move `"shadcn": "^4.13.0"` from `dependencies` to `devDependencies` in `web/package.json`.

---

_Reviewed: 2026-07-15T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
