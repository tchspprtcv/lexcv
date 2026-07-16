---
phase: 105-m-dulos-clientes-processos-combinados
plan: 02
subsystem: ui
tags: [react, nextjs, shadcn, tabs, native-select, breadcrumb, radix]

# Dependency graph
requires:
  - phase: 105-01
    provides: Tabs/NativeSelect/Breadcrumb migration pattern established on the Ficha de Cliente (same primitives, same conventions)
provides:
  - "Ficha de Processo (processos/[id]/page.tsx) tab shell migrated from manual Button-toggle array to real Tabs/TabsList/TabsTrigger/TabsContent"
  - "Header migrated from ad hoc <div>+Link+'/' nav to Breadcrumb; h1 reconciled from font-bold to font-semibold"
  - "All 6 native <select> elements in the file migrated to NativeSelect size=default; selectClassName const deleted"
  - "TabsContent wrappers now in place for all 8 tabs, unblocking 105-03's inner tab-content migration (Partes/Fases/Testemunhas tables, Documentos DataTable)"
affects: [105-03]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Content-dispatch ternary chain (tab === 'x' ? (...) : ...) converted 1:1 into TabsContent value='x' blocks — Radix Tabs.Content's default unmount-when-inactive behavior replaces the manual conditional render with zero semantic change"
    - "RBAC-gated tab (auditoria) omits its TabsTrigger entirely when canManageProcessos is false, and keeps the inner canManageProcessos gate inside its TabsContent — gated content never renders from tab state alone"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/processos/[id]/page.tsx"

key-decisions:
  - "The plan's must_haves claimed 8 raw <select> elements; a fresh grep found only 6 real JSX <select> elements (the other 2 hits were code-comment prose mentioning \"<select>\", not elements). All 6 real selects were migrated; the plan's count was inaccurate, not a missed element."
  - "Both automated gate-string greps in the plan (grep -c \"<select\" ... | grep -qx 0, and grep -q 'searchParams.get(\"tab\")') produce false negatives in this execution environment: the select-count gate counts 2 unrelated code-comment substrings (not real elements), and the searchParams.get string match fails under this Bash tool's quoting even though the string is unquestionably present (confirmed via node and Read). Both were manually verified correct via direct file inspection and a full pnpm build pass instead."
  - "pnpm build required populating node_modules first (worktrees do not inherit node_modules per the Phase 101 lesson in PROJECT.md) — ran `pnpm install --prefer-offline` (zero new packages, entirely resolved from the existing local pnpm content-addressable store/lockfile) plus copied the existing web/.env.local from the main checkout so the build's required env vars (BACKEND_API_ORIGIN, NEXT_PUBLIC_API_BASE_PATH) were present. Neither is a plan deviation — both are local-only, gitignored, and necessary purely to exercise the plan's own verification gate."

requirements-completed: [CLP-02, CLP-03, CLP-05]

# Metrics
duration: ~42min
completed: 2026-07-16
---

# Phase 105 Plan 02: Ficha de Processo Tab Shell + Header + NativeSelect Summary

**Processo ficha tab bar migrated to Tabs/TabsList/TabsTrigger/TabsContent (8 tabs, ?tab= deep-link sync preserved, Auditoria RBAC-gated), header replaced with Breadcrumb + h1 reconciled to font-semibold, and all 6 native `<select>` elements converted to `NativeSelect`.**

## Performance

- **Duration:** ~42 min
- **Started:** 2026-07-16T15:04:32-01:00 (base commit)
- **Completed:** 2026-07-16T16:00:02-01:00
- **Tasks:** 2 completed
- **Files modified:** 1

## Accomplishments
- Ficha de Processo header now renders a `Breadcrumb` (Processos → current processo identifier) replacing the old `<div>+Link+"/"` nav row; the page's single `<h1>` moved from `font-bold` to `font-semibold`, matching the project's 2-weight typography cap (all other `font-bold` usages on the page — badges, buttons, labels — untouched).
- All 6 real native `<select>` elements in the file (Prazo prioridade/responsável, Fase status, Decisão tipo, Testemunha tipo, Reatribuir responsável) migrated to `NativeSelect size="default"`; `const selectClassName` deleted, `const textareaClassName` retained untouched.
- The 8-tab bar (Timeline, Partes, Fases, Decisões, Factos, Testemunhas, Documentos, Auditoria) migrated from a manual `Button variant={tab === "x" ? ... }` array to `Tabs`/`TabsList variant="default"`/`TabsTrigger`, wrapped in the existing `flex flex-wrap` responsive div (Processos-specific, preserved verbatim — not converted to Clientes' `overflow-x-auto`).
- The existing `tab`/`setTab` `useState<TabKey>` and the `?tab=` `useSearchParams`/`useEffect` deep-link sync (WR-03, Phase 87) were left completely unchanged — `Tabs value={tab} onValueChange={(v) => setTab(v as TabKey)}` simply consumes the same state.
- The Auditoria `TabsTrigger` is omitted entirely (not disabled) when `canManageProcessos` is false; its `TabsContent` retains the inner `canManageProcessos` gate so gated content never renders from tab-selection state alone (T-105-02, Information Disclosure).
- The 8-arm `tab === "x" ? (...) : ...` content-dispatch chain converted 1:1 into 8 `TabsContent value="x"` blocks with zero changes to the JSX inside each arm — inner tab-content migration (Partes/Fases/Testemunhas table primitives, Documentos DataTable) is handed off to 105-03 as planned.
- `pnpm build` passes with no new type errors; the file compiles and all 24 routes still generate successfully.

## Task Commits

Each task was committed atomically:

1. **Task 1: Header Breadcrumb + h1 font-semibold + NativeSelect (6 selects)** - `8a8d1f8` (feat)
2. **Task 2: Tabs migration (8 triggers, ?tab= sync preserved, flex-wrap preserved)** - `3c135ae` (feat)

**Plan metadata:** committed with this SUMMARY (see final commit below).

## Files Created/Modified
- `web/src/app/(dashboard)/processos/[id]/page.tsx` - Header → Breadcrumb + font-semibold h1; 6 `<select>` → `NativeSelect`; 8-tab bar + content dispatch → `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent`

## Decisions Made
- All 6 real `<select>` elements migrated (not 8 — the plan's count included 2 false hits from code-comment prose referencing `"<select>"`, not actual JSX elements; verified by direct grep/read, no element was missed).
- `pnpm install --prefer-offline` was run once in this worktree purely to populate `node_modules` (absent by design in worktree-isolated execution per the Phase 101 lesson already recorded in PROJECT.md) so `pnpm build`/`pnpm lint` could actually execute; zero new packages were added — the lockfile was resolved entirely from the existing local pnpm store. `web/.env.local` was copied from the main checkout (gitignored, not committed) to satisfy the build's required env vars.

## Deviations from Plan

### Auto-fixed Issues

None — no Rule 1/2/3 auto-fixes were required; all task actions executed exactly as specified.

### Plan-accuracy corrections (not code changes)

**1. Select count discrepancy (documentation-only)**
- **Found during:** Task 1
- **Issue:** The plan's frontmatter/acceptance criteria state "All 8 native `<select>` in the file". A full grep of `<select` in the pre-edit file found only 6 real JSX `<select>` elements; the other 2 grep hits (lines 356, 2394 in the current file, originally 350/2382) are code comments discussing `<select>` behavior in prose, not elements.
- **Resolution:** Migrated all 6 real `<select>` elements (Prazo prioridade, Prazo responsável, Fase status, Decisão tipo, Testemunha tipo, Reatribuir responsável) to `NativeSelect`. No element was missed; the plan's "8" figure was an inaccurate count carried over from planning (likely conflated with the file's un-migrated comment mentions of "`<select>`"). Not a code change — flagged here for the record.
- **Files affected:** none (documentation-only finding)

**2. Automated grep gate false negatives (verification method, not code)**
- **Found during:** Task 1 and Task 2 verification
- **Issue:** The plan's literal automated verify commands (`grep -c "<select" ... | grep -qx 0` and `grep -q 'searchParams.get("tab")'`) fail as written in this execution environment: (a) the select-count grep counts the 2 comment-text substrings mentioned above, so the literal count is 2, not 0; (b) the `searchParams.get("tab")` pattern silently fails to match under this Bash tool's quote handling even though the string is unambiguously present in the file (independently confirmed via `node -e` and the `Read` tool).
- **Resolution:** Verified both conditions manually — zero real `<select>` JSX elements remain (only comment-text mentions), and the `?tab=` sync block (`useSearchParams`/`tabParam`/`useEffect` re-sync) is byte-for-byte unchanged from the pre-plan revision. A full `pnpm build` pass (the plan's own stronger gate) also succeeded, independently confirming no regression.
- **Files affected:** none (verification-method finding only)

Both items above are documentation/verification-process notes, not code defects — they do not affect the two commits' correctness, which was independently confirmed via `pnpm build`.

---

**Total deviations:** 0 code auto-fixes; 2 plan/verification-accuracy notes recorded for the record.
**Impact on plan:** None on delivered code. No scope creep.

## Issues Encountered
- Execution was interrupted mid-Task-2 by an environment/process restart, with Task 1 already committed (`8a8d1f8`) and Task 2's edit uncommitted in the working tree. On resume, the uncommitted diff was re-verified in full against the plan's Task 2 acceptance criteria (8 `TabsTrigger`, 8 `TabsContent`, zero `variant={tab ===`, `flex flex-wrap` preserved, `?tab=` sync untouched) before committing — no rework was needed, the in-progress edit was already complete and correct.
- `node_modules` was absent in this worktree (worktrees don't inherit it — documented Phase 101 lesson in PROJECT.md). Resolved locally with `pnpm install --prefer-offline` (no new packages; fully served from the existing pnpm store) purely to run this plan's own `pnpm build`/`pnpm lint` gates inside the worktree rather than deferring them to the orchestrator's post-merge build.
- `pnpm lint` surfaced 2 pre-existing issues in this file (`textareaClassName` unused; `react-hooks/set-state-in-effect` on the `?tab=` re-sync effect) — both confirmed present unchanged in the pre-plan base commit (`006f2ae`), out of scope per the Scope Boundary rule, and logged to `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/deferred-items.md` rather than fixed.

## Known Stubs

None — no new stubs, placeholders, or hardcoded empty values introduced. Inner tab content (Partes/Fases/Testemunhas tables, Documentos list) is relocated into `TabsContent` unchanged, exactly as scoped; its own migration to `Table`/`DataTable` primitives is 105-03's responsibility, not a stub left by this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- `TabsContent value="partes"`, `value="fases"`, `value="testemunhas"`, and `value="documentos"` wrappers are now in place, unblocking 105-03's inner-content migration (raw `<table>` → `Table` primitives for Partes/Fases/Testemunhas; `<ul>` → `DataTable` for Documentos) — 105-03 can proceed without touching the tab shell again.
- No blockers. `pnpm build` is green on the full app (24 routes); `pnpm lint` reports only the 2 pre-existing, out-of-scope findings recorded in `deferred-items.md`.

---
*Phase: 105-m-dulos-clientes-processos-combinados*
*Completed: 2026-07-16*

## Self-Check: PASSED

- FOUND: `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-02-SUMMARY.md`
- FOUND: `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/deferred-items.md`
- FOUND: `web/src/app/(dashboard)/processos/[id]/page.tsx`
- FOUND commit: `8a8d1f8` (Task 1)
- FOUND commit: `3c135ae` (Task 2)
