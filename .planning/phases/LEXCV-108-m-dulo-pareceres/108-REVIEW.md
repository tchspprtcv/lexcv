---
phase: LEXCV-108-m-dulo-pareceres
reviewed: 2026-07-17T16:10:00Z
depth: quick
files_reviewed: 5
files_reviewed_list:
  - web/src/app/(dashboard)/pareceres/page.tsx
  - web/src/app/(dashboard)/pareceres/nova/page.tsx
  - web/src/app/(dashboard)/pareceres/[id]/page.tsx
  - web/src/hooks/use-pareceres.ts
  - web/src/lib/pareceres.ts
findings:
  critical: 0
  warning: 0
  info: 5
  total: 5
status: clean
---

# Phase LEXCV-108: Code Review Report (Iteration 3 — Final Confirmation Pass)

**Reviewed:** 2026-07-17T16:10:00Z
**Depth:** quick
**Files Reviewed:** 5
**Status:** clean

## Summary

This is the final, capped iteration of the fix/re-review loop. Commit `7055e0c` claimed to fix
WR-08 (the last open Warning from iteration 2 — `EntregarParecerDialog`'s manually-picked
`selectedVersaoIdState` silently outliving cancel/reopen instead of resetting to the current
newest version). This pass is scoped as requested: verify the WR-08 diff line by line for
correctness and regressions, then a sanity pass over the rest of the module (only one commit,
touching one file with a single line inserted, landed since iteration 2's review — `108-REVIEW.md`
reviewed at `2026-07-17T15:00:00Z` against `1d319c0`; `7055e0c` is the only commit on top of it).

**WR-08 verification result: fixed correctly, no regression.** The applied diff is exactly the
one-line fix suggested in iteration 2:

```tsx
<AlertDialog
  open={confirmOpen}
  onOpenChange={(next) => {
    if (entregar.isPending) return;
    setConfirmOpen(next);
    if (!next) setSelectedVersaoId(null);   // <-- added by 7055e0c
  }}
>
```

Traced both requested regression angles explicitly:

1. **Does it wrongly reset state while the dialog is still open?** No. The reset is gated behind
   `if (!next)`, so it only fires when Radix invokes `onOpenChange(false)` — i.e. on Escape, on
   clicking `AlertDialogCancel` (which internally calls the context's `onOpenChange(false)`, since
   `AlertDialogCancel` is Radix's `AlertDialogPrimitive.Cancel`), or on any other close path. The
   opening path (`onOpenChange(true)`, fired when the trigger is clicked) only calls
   `setConfirmOpen(true)` and never touches `selectedVersaoIdState` — so a value the user is
   actively picking mid-dialog is never clobbered. (Outside-pointer-down dismissal isn't in play
   here: `AlertDialogContent`'s Radix primitive doesn't expose `onPointerDownOutside` at all, as
   already established by the unrelated prior fix in commit `615166b`, so Escape/Cancel/Action are
   the only close vectors.)

2. **Does it interfere with the pre-existing `entregar.isPending` guard?** No. `if
   (entregar.isPending) return;` is still the first statement in the handler, unchanged and
   untouched by the new line — while a delivery is in flight, the entire handler short-circuits
   before either `setConfirmOpen` or the new reset can run, exactly matching the guard's original
   intent (don't let Escape/Cancel interrupt an in-flight "Entregar" call). `AlertDialogCancel`
   (`disabled={entregar.isPending}`) and the `NativeSelect` (`disabled={entregar.isPending}`) remain
   correctly disabled during the pending window as well, so there's no path to trigger the reset
   while a request is outstanding.

One adjacent, pre-existing (not introduced by this fix) behavior worth noting for completeness but
not worth a new finding: on a *successful* delivery, `handleEntregar` calls `setConfirmOpen(false)`
directly rather than through `onOpenChange`, so `selectedVersaoIdState` is technically not reset on
that path. This is harmless in practice — `useEntregarParecer`'s `onSuccess` awaits invalidation of
the `["pareceres","detail", id]` query before `mutateAsync` resolves, so by the time
`setConfirmOpen(false)` runs, `parecer.data.status` has already flipped to `CONCLUIDO`, which flips
`isConcluido`/`showEntregarTrigger` to `false` and unmounts `EntregarParecerDialog` (and its local
state) entirely on the same re-render. There is no reachable "reopen with stale selection" window
on the success path.

No other issues were introduced since iteration 2. A sanity grep for debug artifacts, dangerous
functions, and hardcoded secrets across all five files in scope returned nothing. The Info-level
items below (IN-01 through IN-05) are carried forward unchanged from iteration 2 — they were
intentionally out of scope for the Critical/Warning-only fix passes and remain present in the code
as of this review. No Critical or Warning findings remain open.

## Info

### IN-01: Duplicated `formatDate`/`statusVariant` instead of reusing `@/lib/pareceres`

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:67-85`

**Issue:** `[id]/page.tsx` still re-implements byte-for-byte identical `formatDate` and
`statusVariant` locally instead of importing them from `@/lib/pareceres` (only `formatDateTime` is
genuinely new to this file). Both copies remain in sync as of this review, but the duplication is
a latent risk if either copy is edited again in isolation.

**Fix:** Import `formatDate`/`statusVariant` from `@/lib/pareceres` here too, keeping only the new
`formatDateTime` local.

### IN-02: Resolver type-checking bypassed with `as any`

**File:** `web/src/app/(dashboard)/pareceres/nova/page.tsx:53-55`

**Issue:**
```tsx
const form = useForm<ParecerCreateFormValues>({
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  resolver: zodResolver(parecerCreateFormSchema) as any,
```
Still present, working around the `z.input`/`z.output` mismatch from `optionalTrimmedString`'s
`.transform()`.

**Fix:** Type the form with the schema's *input* type instead, e.g.
`useForm<z.input<typeof parecerCreateFormSchema>>(...)`.

### IN-03: Repeated `undefined as unknown as FileList` casts

**File:** `web/src/app/(dashboard)/pareceres/[id]/page.tsx:380,424,427`

**Issue:** The same double-cast is still repeated three times to represent "no file selected."

**Fix:** Factor out a small typed helper (e.g. `const EMPTY_FILE_LIST = undefined as unknown as
FileList;`) to avoid repeating the unsafe cast at each call site.

### IN-04: `usePermissions()` invoked twice per page (parent guard + content component)

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:27,43`, `web/src/app/(dashboard)/pareceres/nova/page.tsx:30,47`, `web/src/app/(dashboard)/pareceres/[id]/page.tsx:89`

**Issue:** Each page still calls `usePermissions()` once in the outer guard component and again in
the inner content component. Harmless (TanStack Query dedupes `["auth","me"]`), but an
easy-to-avoid redundant hook call.

**Fix:** Not required; consider computing permissions once and passing down, as already done for
`ParecerDetailContent`'s `permissions` prop.

### IN-05: Residual un-paired `<label>` elements for Select-based fields in "Pesquisa Avançada"

**File:** `web/src/app/(dashboard)/pareceres/page.tsx:262-264,282-284,302-305`

**Issue:** The "Cliente", "Advogado", and "Estado" fields in the Pesquisa Avançada panel still use
bare `<label>` tags with no `htmlFor`, wrapping the custom `Select` component rather than a native
input:
```tsx
<label className="text-[11px] font-bold tracking-wider uppercase text-slate-500 dark:text-slate-400">
  Cliente
</label>
<Select value={pesquisaClienteId} onValueChange={setPesquisaClienteId}>...</Select>
```
This was out of WR-07's original scope (which only covered the free-text input and the two date
inputs), so it remains unfixed.

**Fix:** Radix `Select` doesn't take a plain `htmlFor` target the same way a native input does; use
`aria-label` on the `SelectTrigger`, or wrap the trigger with `aria-labelledby` pointing at an `id`
on the label, e.g. `<label id="pesquisa-cliente-label">Cliente</label>` +
`<SelectTrigger aria-labelledby="pesquisa-cliente-label">`.

---

_Reviewed: 2026-07-17T16:10:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: quick_
_Iteration: 3 (final, capped; supersedes iteration 2)_
