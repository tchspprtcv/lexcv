---
phase: 105-m-dulos-clientes-processos-combinados
plan: 01
subsystem: ui
tags: [shadcn, tabs, native-select, avatar, breadcrumb, react-hook-form, clientes]

# Dependency graph
requires:
  - phase: 101-foundation
    provides: Tabs, NativeSelect, Avatar, Breadcrumb primitives installed (unused-in-anger until this phase)
  - phase: 102-design-system-reconciliation
    provides: reconciled Table/Card/Dialog primitives already consumed by ClienteProcessosTab/ClienteParecerTab
provides:
  - Ficha de Cliente (clientes/[id]/page.tsx) tab bar migrated from manual Button-toggle array to accessible Tabs/TabsList/TabsTrigger/TabsContent
  - All 5 native <select> occurrences in the file replaced with NativeSelect (RHF-bound and controlled variants)
  - Avatar+AvatarFallback (initials) added to ResponsaveisCard rows (Advogados + Administrativos)
  - Breadcrumb header replacing the ad hoc <div>+Link+"/" nav
affects: [105-02-ficha-processo, 105-03, 105-04, 105-05, 105-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Tabs value={tab} onValueChange wraps the existing tab/setTab useState<TabKey> as a controlled Radix Tabs root, with RBAC-gated TabsTrigger omitted (not disabled) when the permission check fails"
    - "TabsContent value=\"dados\" carries its own className=\"space-y-4\" to preserve the original outer space-y-4 gap between the Dados tab's internal Card blocks, since the tab bar's own space-y-4 wrapper now only has a single Tabs child"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/[id]/page.tsx"

key-decisions:
  - "The plan's must_haves text claimed 'exactly 6' <select> occurrences; a fresh grep found only 5 real <select> JSX elements plus 1 textual mention inside a code comment (which also matches the literal string '<select'). Migrated all 5 real elements to NativeSelect and reworded the comment (removing the literal '<select>' substring) so the plan's own automated gate (grep -c \"<select\" must equal 0) passes without a false positive from documentation text."
  - "TabsContent value=\"dados\" was given className=\"space-y-4\" (not present in the plan's literal action text) to preserve the exact same 16px vertical gap between the tab's 3 sibling Card blocks that the outer wrapping div previously supplied via its own space-y-4 before the Tabs refactor collapsed it to a single child."

requirements-completed: [CLP-01, CLP-03, CLP-04, CLP-05]

# Metrics
duration: ~50min
completed: 2026-07-16
---

# Phase 105 Plan 01: Ficha de Cliente shadcn Migration Summary

**Ficha de Cliente (`clientes/[id]/page.tsx`) migrated from manual Button-toggle tabs to accessible Radix Tabs, all 5 native `<select>` elements to `NativeSelect`, `Avatar` initials added to Advogados/Administrativos rows, and the header nav replaced with `Breadcrumb`.**

## Performance

- **Duration:** ~50 min
- **Tasks:** 2/2 completed
- **Files modified:** 1

## Accomplishments
- Header `<div>+Link+"/"` replaced with `Breadcrumb`/`BreadcrumbList`/`BreadcrumbLink(asChild Link)`/`BreadcrumbSeparator`/`BreadcrumbPage`, preserving the exact existing fallback chain (`numero_cliente ?? nome ?? "…"`)
- All 5 native `<select>` elements (documento_tipo, ramo_atividade — both RHF-bound; ResponsaveisCard's user picker, contact-tipo create/edit — both controlled) replaced with `NativeSelect size="default"`; `selectClassName` const deleted, `textareaClassName` left untouched
- `Avatar size="sm"` + `AvatarFallback` (initials via a new `deriveInitials` helper, reusing `clientes/columns.tsx`'s exact derivation logic) added to `ResponsaveisCard` rows — shared by both Advogados and Administrativos call sites
- Tab bar (7 triggers: Dados, Contactos e Notas, Processos*, Pareceres*, Documentos Entregues, Documentos a Tratar, Deslocações — `*` RBAC-gated) migrated from `variant={tab === "x" ? "secondary" : "outline"}` `Button` array to `Tabs`/`TabsList variant="default"`/`TabsTrigger`/`TabsContent`, controlled via the existing `tab`/`setTab` `useState<TabKey>`
- RBAC-gated `processos`/`pareceres` `TabsTrigger`s omitted (not disabled) when `canViewProcessos`/`canViewPareceres` is false; each `TabsContent` keeps its own inner `AccessDeniedState` fallback, so gated content can never render from tab state alone (T-105-01 threat mitigation)
- `overflow-x-auto` responsive wrapper preserved verbatim around the new `TabsList`
- `pnpm build` green (Turbopack compile + full TypeScript pass, 24/24 static pages generated) with zero new type errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Header Breadcrumb + NativeSelect (5 selects) + Avatar (ResponsaveisCard)** - `79cb124` (feat)
2. **Task 2: Tabs migration (7 triggers, 2 RBAC-gated, overflow-x-auto preserved)** - `a5ebd96` (feat)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/[id]/page.tsx` - Ficha de Cliente: Breadcrumb header, NativeSelect (5x), Avatar (ResponsaveisCard), Tabs/TabsList/TabsTrigger/TabsContent (7 tabs, 2 RBAC-gated)

## Decisions Made
- Migrated all 5 actual `<select>` elements found in the file (not 6 as the plan's must_haves text stated) — see Deviations below for the discrepancy and how the automated gate was still satisfied.
- Added `className="space-y-4"` to `TabsContent value="dados"` only, to preserve original vertical spacing between that tab's 3 sibling Card blocks (the other 6 tabs each hold a single top-level element/ternary, so no equivalent spacing wrapper was needed there).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] 105-PATTERNS.md referenced by the plan does not exist**
- **Found during:** Task 1 (read_first step)
- **Issue:** Both tasks' `<read_first>` sections point to `105-PATTERNS.md` for before/after code excerpts ("Use those directly — no codebase exploration needed"), but the file was never created (confirmed via `git log --all` — no commit ever touched it) alongside the other 105-*.md phase docs.
- **Fix:** Read the actual `clientes/[id]/page.tsx` source directly, plus the 4 target UI primitives (`tabs.tsx`, `native-select.tsx`, `avatar.tsx`, `breadcrumb.tsx`) and `clientes/columns.tsx`'s initials-derivation logic, to reconstruct the exact before/after transformations called for by `105-UI-SPEC.md` and the plan's own `<action>` text (which is self-contained and detailed enough to execute without the missing pattern file).
- **Files modified:** N/A (read-only investigation)
- **Verification:** All automated gates in both tasks passed; `pnpm build` green.
- **Committed in:** N/A (no file change required for this fix — informational deviation only)

**2. [Rule 1 - Bug] Plan's "exactly 6 `<select>`" count was off by one; a code comment also literally contained "<select>"**
- **Found during:** Task 1 (enumerating `<select>` occurrences per the plan's own instruction to grep the file)
- **Issue:** `grep -c "<select"` on the pre-migration file returns 6, matching the plan's must_haves claim — but one of those 6 matches is a code comment (`// this into the native <select>, the browser would fall back...`), not a real JSX element. Only 5 real `<select>` elements exist in source (documento_tipo, ramo_atividade, ResponsaveisCard picker, contact-tipo create, contact-tipo edit). Left as-is, this comment would have caused Task 1's own automated gate (`grep -c "<select" | grep -qx 0`) to fail after migration, since the literal string would still be present.
- **Fix:** Migrated all 5 real `<select>` elements to `NativeSelect`, and reworded the comment to "native select element"/"first option" (removing the literal `<select>`/`<option>` substrings) so it no longer trips the completeness gate, while preserving its original explanatory meaning unchanged.
- **Files modified:** `web/src/app/(dashboard)/clientes/[id]/page.tsx`
- **Verification:** `grep -c "<select" ...` returns 0 post-migration; full Task 1 gate (`GATE_PASS`) confirmed.
- **Committed in:** `79cb124` (Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking-missing-reference, 1 bug/gate-correctness)
**Impact on plan:** Both deviations were necessary to complete the plan's own stated verification gates correctly; no scope creep — the actual set of `<select>` elements converted (5) matches 100% of the real native selects that existed in the file (CLP-03 scope), the 6th "occurrence" was never a real element to migrate.

## Issues Encountered
- The worktree had no `node_modules` installed (fresh worktree checkout), so `pnpm build`/`pnpm lint` initially failed with "Cannot find module". Ran `pnpm install` (existing lockfile-pinned dependencies only, no new packages) to restore the environment before running verification gates — matches the known Phase 101 lesson that worktrees don't inherit `node_modules`.
- `pnpm build` initially failed with `Error: BACKEND_API_ORIGIN is required` (env validation in `next.config.ts`). Supplied `BACKEND_API_ORIGIN=http://localhost:8080 NEXT_PUBLIC_API_BASE_PATH=/api/v1` inline (matching `web/.env.example`) for the build-verification run only; no `.env.local` file was created or committed.
- `pnpm lint` reports 4 pre-existing issues in this file (3x `react-hooks/set-state-in-effect` in `ResponsaveisCard`/`ClienteContactosCard`/`ClienteNotasCard`'s reset-on-`editable`-change effects, 1x `react-hooks/incompatible-library` on `form.watch("tipo")`). Confirmed via diff against the pre-Task-1 commit (`006f2ae`) that these are byte-identical pre-existing patterns at different line offsets, not introduced by this plan's changes — left untouched per the deviation rules' scope boundary (pre-existing issues unrelated to this task's Tabs/NativeSelect/Avatar/Breadcrumb changes).

## Next Phase Readiness
- CLP-01 (Cliente half), CLP-03 (Cliente file), CLP-04 (Cliente), CLP-05 (Cliente ficha) all satisfied for this file; `pnpm build` green.
- Ready for the Ficha de Processo companion plan (105-02) to land in parallel/next — both fichas must ship in the same phase per CONTEXT.md to avoid a visible inconsistency window.
- No blockers.

---
*Phase: 105-m-dulos-clientes-processos-combinados*
*Completed: 2026-07-16*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/clientes/[id]/page.tsx`
- FOUND: `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/105-01-SUMMARY.md`
- FOUND commit: `79cb124` (Task 1)
- FOUND commit: `a5ebd96` (Task 2)
