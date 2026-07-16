---
phase: 105-m-dulos-clientes-processos-combinados
plan: 05
subsystem: ui
tags: [nativeselect, breadcrumb, react-hook-form, shadcn, processos]

# Dependency graph
requires:
  - phase: 101-foundation
    provides: NativeSelect and Breadcrumb primitives (installed, unused-in-anger until Phase 105)
provides:
  - Processos list filters (draftEstado, draftClienteId) migrated to NativeSelect
  - processos/novo's 4 RHF-bound selects migrated to NativeSelect + net-new 2-level Breadcrumb (Processos -> Novo Processo)
  - processos/[id]/editar's 1 RHF-bound select migrated to NativeSelect + net-new 3-level Breadcrumb (Processos -> {numero} -> Editar), replacing the old single-level "Voltar ao detalhe" link
affects: [105-01, 105-02, 105-03, 105-04, 105-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "NativeSelect drop-in swap for controlled/RHF-bound <select>: size=\"default\" plus className=\"w-full\" to preserve the original h-10/h-9 w-full layout (NativeSelect's own wrapper div defaults to w-fit)"
    - "Net-new Breadcrumb inserted directly above the existing header block, using BreadcrumbLink asChild wrapping next/link Link"

key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/processos/page.tsx
    - web/src/app/(dashboard)/processos/novo/page.tsx
    - web/src/app/(dashboard)/processos/[id]/editar/page.tsx

key-decisions:
  - "Added className=\"w-full\" to every NativeSelect call site in this plan (not in the plan's literal action text) — NativeSelect's wrapper div defaults to w-fit while the original <select> elements were h-9/h-10 w-full inside grid/space-y-2 layout slots; without w-full the control would shrink to its content width, a visible layout regression versus the Input siblings in the same rows. Rule 1 (auto-fix bug) applied."
  - "processos/novo's <h1 className=\"text-2xl font-bold text-slate-900 dark:text-white\">Novo Processo</h1> deliberately left unchanged per UI-SPEC Scope note #4 (out of scope for the h1-weight fix, which only covers the 2 ficha detail pages)."
  - "processos/[id]/editar's <h1 className=\"text-2xl font-semibold\">Editar processo</h1> required no change — already font-semibold, no text-slate-900 override present to drop."

requirements-completed: [CLP-03, CLP-05]

# Metrics
duration: ~25min
completed: 2026-07-16
---

# Phase 105 Plan 05: Processos Secondary Pages — NativeSelect + Breadcrumb Summary

**Migrated all 7 `<select>` occurrences across processos/page.tsx, processos/novo/page.tsx, and processos/[id]/editar/page.tsx to NativeSelect, and added a net-new 2-level Breadcrumb on the create form plus a net-new 3-level Breadcrumb (replacing the old single-level "Voltar ao detalhe" link) on the edit form.**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-07-16T15:55:00Z (approx, worktree spawn)
- **Completed:** 2026-07-16T16:32:46Z
- **Tasks:** 3
- **Files modified:** 3

## Accomplishments
- Both list-filter `<select>`s on `processos/page.tsx` (draftEstado, draftClienteId) now render as `NativeSelect size="default"`, dropping the stray `focus-visible:ring-blue-500` inline styling.
- All 4 RHF-bound `<select>`s on `processos/novo/page.tsx` (cliente_id, tipo_processo, origem, nivel_final) migrated to `NativeSelect`; `const selectClassName` deleted; a net-new 2-level `Breadcrumb` (`Processos` -> `Novo Processo`) added below the page header.
- The 1 RHF-bound `<select>` on `processos/[id]/editar/page.tsx` (cliente_id) migrated to `NativeSelect`; `const selectClassName` deleted; the old single-level "Voltar ao detalhe" link replaced with a net-new 3-level `Breadcrumb` (`Processos` -> `{processo.data?.numero}` link back to the ficha -> `Editar`), reusing the already-loaded `useProcesso(id)` with zero new fetches.
- `pnpm build` and `pnpm lint` both pass clean across all 3 files touched by this plan.

## Task Commits

Each task was committed atomically:

1. **Task 1: processos/page.tsx list filters -> NativeSelect** - `5ec8c4b` (feat)
2. **Task 2: processos/novo/page.tsx -> NativeSelect (x4) + Breadcrumb (h1 left as-is)** - `bf7d4f0` (feat)
3. **Task 3: processos/[id]/editar/page.tsx -> NativeSelect + 3-level Breadcrumb** - `c7503ea` (feat)

_No plan-metadata commit — orchestrator owns STATE.md/ROADMAP.md updates after all wave agents complete._

## Files Created/Modified
- `web/src/app/(dashboard)/processos/page.tsx` - draftEstado and draftClienteId list filters swapped from native `<select>` to `NativeSelect size="default" className="w-full"`
- `web/src/app/(dashboard)/processos/novo/page.tsx` - 4 RHF-bound selects swapped to `NativeSelect`; `const selectClassName` deleted; net-new 2-level Breadcrumb added; h1 `font-bold text-slate-900` intentionally preserved
- `web/src/app/(dashboard)/processos/[id]/editar/page.tsx` - 1 RHF-bound select swapped to `NativeSelect`; `const selectClassName` deleted; single-level "Voltar ao detalhe" link replaced with net-new 3-level Breadcrumb reusing `useProcesso(id)`

## Decisions Made
- Added `className="w-full"` to every `NativeSelect` call site in this plan. The plan's action text said only to drop the inline className without specifying a replacement, but `NativeSelect`'s wrapper `<div>` defaults to `w-fit` while every original `<select>` here was `w-full` inside a grid/stacked form layout. Without `w-full`, each select would visually shrink to its content width — a layout regression next to the full-width `Input` siblings in the same rows/columns. Classified as Rule 1 (auto-fix bug), not a plan deviation requiring escalation, since the plan's own scope note #5 anticipates callers using `w-fit`/sizing utilities "where a specific layout constraint requires" one.
- `processos/novo`'s `<h1>` was left exactly as-is (`font-bold text-slate-900 dark:text-white`) per UI-SPEC Scope note #4 — the h1-weight fix in this phase is locked to only the 2 ficha detail pages, not the `novo` intake wizard. Verified via the Task 2 gate's explicit assertion (`grep -q 'font-bold text-slate-900'`).
- `processos/[id]/editar`'s `<h1>` needed no change — confirmed already `font-semibold` with no `text-slate-900`/`dark:text-white` override to drop, matching the UI-SPEC's own prediction for this file.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Added `className="w-full"` to all 7 NativeSelect call sites**
- **Found during:** Task 1 (list filters)
- **Issue:** `NativeSelect`'s wrapper `<div>` defaults to `w-fit` (see `web/src/components/ui/native-select.tsx`); every `<select>` being replaced in this plan was originally `h-9`/`h-10 w-full`, filling its grid column or form-field slot. A literal className-drop-only swap (as the plan's action text describes) would silently shrink every select to fit its content, breaking visual parity with sibling `Input` fields in the same rows.
- **Fix:** Added `className="w-full"` to the `NativeSelect` element at every one of the 7 call sites across the 3 files.
- **Files modified:** `web/src/app/(dashboard)/processos/page.tsx`, `web/src/app/(dashboard)/processos/novo/page.tsx`, `web/src/app/(dashboard)/processos/[id]/editar/page.tsx`
- **Verification:** `pnpm build` succeeds; task grep gates (which don't check width classes) still pass; visual layout parity preserved by inspection of the JSX structure (each select sits in the same `w-full`/grid-column-constrained slot as before).
- **Committed in:** `5ec8c4b`, `bf7d4f0`, `c7503ea` (part of each respective task commit)

---

**Total deviations:** 1 auto-fixed (1 Rule 1 - bug, applied consistently across all 3 task commits)
**Impact on plan:** Necessary for visual/layout correctness; no scope creep — the fix stays within the same 7 call sites the plan already targeted.

## Issues Encountered
- The worktree had no `node_modules` and no `web/.env.local` (both gitignored, not present in a fresh worktree checkout). Ran `pnpm install --frozen-lockfile` and copied `web/.env.example` to `web/.env.local` (default `http://localhost:8080` / `/api/v1` values) to unblock `pnpm build`/`pnpm lint`. Neither file is tracked by git — no commit needed, and none was made.
- `105-PATTERNS.md` (referenced in the plan's context block and files_to_read list) does not exist in `.planning/phases/LEXCV-105-m-dulos-clientes-processos-combinados/` — only `105-CONTEXT.md` and `105-UI-SPEC.md` are present. Proceeded using the plan's own detailed inline action/read_first descriptions plus `105-CONTEXT.md`/`105-UI-SPEC.md`, which fully covered the NativeSelect/Breadcrumb patterns needed. Not blocking; no fix attempted since this is documentation the plan references but doesn't require this executor to create.

## User Setup Required

None - no external service configuration required. (The `web/.env.local` file created during execution is a local dev convenience using the same placeholder values as `web/.env.example`; it is gitignored and was not committed.)

## Next Phase Readiness
- CLP-03 (Processos secondary pages) and CLP-05 (Processos form/edit breadcrumbs) are closed for this plan's 3 files. Combined with the ficha-page work in 105-02/105-03 and the Clientes-side work in 105-01/105-04, this closes out the full 27-occurrence `<select>` sweep and the 6-page Breadcrumb sweep once all wave plans land.
- `pnpm build` is green with all 3 files in this plan included; no new TypeScript or lint errors introduced.
- No blockers for 105-06 or downstream phases.

---
*Phase: 105-m-dulos-clientes-processos-combinados*
*Completed: 2026-07-16*
