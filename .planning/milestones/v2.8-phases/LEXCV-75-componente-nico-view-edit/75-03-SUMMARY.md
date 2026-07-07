---
phase: 75-componente-nico-view-edit
plan: 03
subsystem: ui
tags: [nextjs, react, clientes, routing]

# Dependency graph
requires:
  - phase: 75-01
    provides: "Deletion of the /clientes/[id]/editar route and merge of view/edit into /clientes/[id]"
provides:
  - "clientes/page.tsx list-page Editar pencil links repointed to /clientes/[id]"
affects: [75-remaining-plans]

# Tech tracking
tech-stack:
  added: []
  patterns: []

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/page.tsx"

key-decisions:
  - "Kept the Pencil icon affordance in both mobile-card and desktop-row views (no removal), per plan scope — it's an additional entry point into the unified page, not a page of its own."

patterns-established: []

requirements-completed: [CLI-14]

# Metrics
duration: 5min
completed: 2026-07-04
---

# Phase 75 Plan 03: Repoint clientes list Editar links Summary

**Both Editar pencil-icon links in the clientes list (mobile card + desktop row) now point to `/clientes/[id]` instead of the deleted `/clientes/[id]/editar` route.**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-07-04
- **Completed:** 2026-07-04
- **Tasks:** 1 completed
- **Files modified:** 1

## Accomplishments
- Mobile/card list Editar pencil link (`web/src/app/(dashboard)/clientes/page.tsx`, was line 450) now reads `href={\`/clientes/${encodeURIComponent(c.id)}\`}`
- Desktop row Editar pencil link (same file, was line 597) now reads `href={\`/clientes/${encodeURIComponent(cliente.id)}\`}`
- Confirmed zero remaining `/editar` references in `clientes/page.tsx`
- Confirmed `Pencil` icon and `canEditClientes` guards preserved unchanged around both sites

## Task Commits

Each task was committed atomically:

1. **Task 1: Repoint the two Editar pencil links to /clientes/[id]** - `027693a` (fix)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/page.tsx` - Both Editar pencil-icon `Link href` targets stripped of the `/editar` suffix; now point at the unified `/clientes/[id]` detail page.

## Decisions Made
None - followed plan as specified.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

`pnpm tsc --noEmit` could not run in this worktree because `node_modules` is not installed here (no dependency install step was run for this isolated worktree). Verification was instead done by:
1. `grep` confirming zero `/editar` matches remain in the file (matches the plan's automated check).
2. Structural comparison — the two edited lines now use the exact same `href={\`/clientes/${encodeURIComponent(...)}\`}` pattern as the three pre-existing, already-correct `Eye`/view links in the same file (lines 416, 541, 582), which are known to type-check today since they're unchanged. The edit only removes a static string suffix from a template literal; no type shape changed.
Recommend running `cd web && pnpm install && pnpm tsc --noEmit` in an environment with dependencies installed to get a full compiler confirmation before merging, though risk of a regression here is effectively nil given the nature of the change.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- CLI-14's link-cleanup portion is complete: no internal link in the codebase now points at the removed `/clientes/[id]/editar` route from this file.
- Manual/human verification (clicking both pencil icons end-to-end in a running app) remains deferred, as noted in the plan's `<verification>` block.

---
*Phase: 75-componente-nico-view-edit*
*Completed: 2026-07-04*
