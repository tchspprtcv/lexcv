# Deferred Items — Phase 105

Items discovered during execution that are out of scope for the current task/plan
(pre-existing, not introduced by this plan's changes) and therefore not auto-fixed
per the Scope Boundary rule.

## From 105-02 (Ficha de Processo — Tab shell + header + selects)

| Item | File | Evidence | Status |
|------|------|----------|--------|
| `const textareaClassName` declared but never referenced anywhere in the file (`@typescript-eslint/no-unused-vars`) | `web/src/app/(dashboard)/processos/[id]/page.tsx:172` | Confirmed present, already unused, in the pre-plan base commit (`006f2ae`) — line 165 in that revision. Not introduced by 105-02; the plan explicitly directed retaining this const (its sibling `selectClassName` was deleted since all consuming `<select>` elements were migrated to `NativeSelect`, but `textareaClassName`'s own consuming `<textarea>` elements are out of scope for this plan). | Pre-existing, out of scope — candidate for a future cleanup pass (likely the `<textarea>` elements that once used it were themselves migrated/removed in an earlier phase, orphaning the const). |
| `react-hooks/set-state-in-effect`: `setTab(p as TabKey)` called synchronously inside a `useEffect` (the `?tab=` deep-link re-sync effect) | `web/src/app/(dashboard)/processos/[id]/page.tsx:254` (was line 247 pre-edit) | Confirmed present, unchanged, in the pre-plan base commit (`006f2ae`) — this is the "WR-03 (Phase 87 code review)" effect, a deliberate, documented design decision to re-sync `tab` state when `?tab=` changes post-mount (e.g. following a notification deep-link). 105-02 did not touch this effect at all (Task 2's edits start ~1000 lines further down, at the tab bar). | Pre-existing, out of scope, and by-design (see the effect's own docblock) — not a defect to fix under this plan. |

Both findings are `eslint` warnings/errors surfaced by `pnpm lint`, not `pnpm build`/`tsc` failures — the build itself is green.
