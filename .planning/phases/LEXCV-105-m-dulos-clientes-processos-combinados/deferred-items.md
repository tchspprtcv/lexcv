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

## From UI audit (105-UI-REVIEW.md)

| Item | File | Evidence | Status |
|------|------|----------|--------|
| Declared 2-weight typography cap (400/600) is enforced only on the single `<h1>` per ficha the UI-SPEC explicitly locked; the surrounding page content in both `clientes/[id]/page.tsx` and `processos/[id]/page.tsx` ships `font-medium` (500) and `font-bold` (700) at dozens of pre-existing sites (Badges, TableHeads), plus off-scale `text-[10px]`/`text-[11px]` sizes | `web/src/app/(dashboard)/clientes/[id]/page.tsx` (~14-15 `font-bold`/`font-medium` sites), `web/src/app/(dashboard)/processos/[id]/page.tsx` (~20+15 sites) | Confirmed via UI audit's own framing: this predates Phase 105 in the vast majority of cited lines (the phase only touched the Tabs/Select/Avatar/Breadcrumb/Table portions of these 2000+-line files, not their pre-existing Badge/TableHead styling). Matches the exact precedent already logged in `PROJECT.md`'s Phase 104 Key Decisions: "the UI-SPEC's 2-weight (400/600) contract was scoped explicitly to header labels, not cell body content, and this pattern was ported from pre-existing per-screen styling rather than invented this phase." | Pre-existing, out of scope — a full font-weight reconciliation across both fichas' entire Badge/TableHead surface would be a dedicated design-system cleanup phase, not an audit-fix-sized change. |
| `processos/[id]/documentos-columns.tsx` renders raw byte counts ("2458624 bytes") while the sibling `clientes/[id]/page.tsx` Documentos Entregues tab (same phase) renders human-readable KB/MB via `formatDocumentoSize()` | `web/src/app/(dashboard)/processos/[id]/documentos-columns.tsx:171` | Confirmed inherited verbatim from Phase 104's `documentos/columns.tsx:212`, per `105-PATTERNS.md`'s explicit "model directly on Phase 104" instruction for this new file — an intentional pattern-match choice, not an oversight. | Deferred — reconciling would mean diverging from the established Phase 104 pattern this file was deliberately modeled on; candidate for a future cross-phase Documentos formatting consistency pass. |
