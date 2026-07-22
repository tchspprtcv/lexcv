---
phase: 112-frontend-pesquisa-global-paleta-de-comando
plan: "05"
subsystem: ui
tags: [nextjs, react, useSearchParams, documentos, pareceres, search]

# Dependency graph
requires:
  - phase: 111-backend-pesquisa-global-cross-entity-api
    provides: "Stable GET /api/v1/pesquisa?q= contract (ResultadoPesquisaDto[]) — not consumed directly by this plan, but the ?q= URL contract this plan seeds from is the palette's navigation target for this same contract"
provides:
  - "Documentos gains a genuinely new client-side name filter (nomeFiltro), seeded from ?q=, narrowing the already-fetched list by d.nome"
  - "Pareceres seeds ?q= into the existing advanced-search path (pesquisaTexto/pesquisaFilters/pesquisaSubmitted/pesquisaOpen), opening the panel and activating search"
  - "Both pages re-seed correctly on repeated same-page navigation to a new ?q= value"
affects: [documentos, pareceres, global-search-dialog, command-palette]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "React 'adjust state during render' idiom (guarded by a *SeedKey state value compared against the extracted searchParams value) used instead of useEffect+setState for URL-param seeding — required to satisfy the react-hooks/set-state-in-effect lint rule now enforced in this codebase's eslint-config-next version, while still re-seeding on same-page navigation to a new ?q="

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/documentos/page.tsx"
    - "web/src/app/(dashboard)/pareceres/page.tsx"

key-decisions:
  - "Render-time conditional setState (seed-key pattern), not useEffect, for both pages' ?q= seeding — auto-fixed after ESLint's react-hooks/set-state-in-effect flagged the effect-based version the plan's <action> text illustrated"
  - "Documentos: genuinely new client-side-only nome filter added (no q existed anywhere for this page before); useDocumentos/DocumentosListFilters/backend untouched"
  - "Pareceres: seeding targets only the pesquisa* advanced-search state, never the unrelated simple filters/setFilters (list-mode) state"

patterns-established:
  - "URL-param seeding into freely-user-editable local state: track a `<field>SeedKey` state initialized to null, and during render (not in an effect) do `if (seededQ && seededQ !== fieldSeedKey) { setFieldSeedKey(seededQ); setField(seededQ); ... }` — reseeds on a new URL value while never fighting the user's subsequent edits on unrelated re-renders"

requirements-completed: [SRCH-09]

# Metrics
duration: ~30min (estimated)
completed: 2026-07-21
---

# Phase 112 Plan 05: Documentos + Pareceres `?q=` Seeding Summary

**Documentos gets a new client-side name filter and Pareceres seeds its advanced-search panel, both driven by `useSearchParams().get("q")`, closing the two "gap" list pages the global-search palette's "Ver todos" links need (SRCH-09).**

## Performance

- **Duration:** ~30 min (estimated — includes worktree base-drift diagnosis/recovery, see Issues Encountered)
- **Completed:** 2026-07-21T16:54:00Z
- **Tasks:** 2/2 completed
- **Files modified:** 2

## Accomplishments
- `documentos/page.tsx`: new `nomeFiltro` state, seeded from `?q=`, drives a `documentosVisiveis` `useMemo` (case-insensitive `d.nome` match) that both the `DataTable` and the mobile card list now consume instead of `list.data` directly — a genuinely new display-side filter, since this page had no free-text filter of any kind before.
- `pareceres/page.tsx`: `?q=` now seeds the existing dual-mode advanced-search state (`pesquisaTexto`, `pesquisaFilters`, `pesquisaSubmitted`, `pesquisaOpen`) instead of the unrelated simple `filters` state, mirroring the page's own `onPesquisar` submit handler and opening the panel so the pre-filled text is visible.
- Both pages re-seed correctly if the user navigates to a *different* `?q=` value while already on the page (not just on first mount).

## Task Commits

Each task was committed atomically:

1. **Task 1: Add a client-side name filter to Documentos, seeded from ?q=** - `ff73b4a` (feat)
2. **Task 2: Seed ?q= into the Pareceres advanced-search path** - `089921d` (feat)

_No TDD tasks in this plan; no refactor commits needed._

## Files Created/Modified
- `web/src/app/(dashboard)/documentos/page.tsx` - Added `nomeFiltro`/`nomeFiltroSeedKey` state, seed-on-render logic, a visible `Input` in the Filtros card, and a `documentosVisiveis` `useMemo` now feeding the `DataTable`/mobile card map.
- `web/src/app/(dashboard)/pareceres/page.tsx` - Added `useSearchParams`, `pesquisaSeedKey` state, and render-time seeding of the four `pesquisa*` setters (texto/filters/submitted/open) from `?q=`.

## Decisions Made
- Used the render-time "adjust state during render" pattern (React's own documented idiom for resetting/seeding state when a derived key changes) instead of `useEffect` for both pages' seeding logic — see Deviations below for why.
- Documentos' new filter is purely client-side (operates on already-fetched, already-tenant-scoped `list.data`); no changes to `useDocumentos`, `DocumentosListFilters`, or any backend file, per the plan's explicit gap note and threat-model disposition (T-112-13, accepted).
- Pareceres seeding never touches `filters`/`setFilters` (the unrelated simple-list state) — confirmed by grep after implementation.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced `useEffect`-based `?q=` seeding with a render-time conditional (both files)**
- **Found during:** Task 1 (Documentos), then proactively applied to Task 2 (Pareceres) once the pattern was confirmed
- **Issue:** The plan's `<action>` text illustrates seeding via `React.useEffect(() => { if (seededQ) setNomeFiltro(seededQ); }, [seededQ]);` (and the four-setter equivalent for Pareceres). Scoped `eslint` run on the edited file caught a real, newly-enforced rule: `react-hooks/set-state-in-effect` — "Calling setState() directly within an effect" is now an error in this codebase's `eslint-config-next` version (confirmed also flagging 5 *other*, pre-existing, unrelated files across the codebase — see Issues Encountered).
- **Fix:** Replaced the effect in both files with React's own documented "adjust state during render" idiom: a `<field>SeedKey` state (`nomeFiltroSeedKey` / `pesquisaSeedKey`) initialized to `null`, and a plain `if (seededQ && seededQ !== <field>SeedKey) { set<field>SeedKey(seededQ); set<Field>(seededQ); ... }` block called directly in the render body (not inside `useEffect`). This still re-seeds correctly on first mount and on any subsequent navigation to a *different* `?q=` value, without fighting the user's own edits on unrelated re-renders, and does not trigger the lint rule (which only targets `useEffect`/`useLayoutEffect` bodies).
- **Files modified:** `web/src/app/(dashboard)/documentos/page.tsx`, `web/src/app/(dashboard)/pareceres/page.tsx`
- **Verification:** `pnpm exec eslint` scoped to both files → "No issues found" (previously: 1 error). Full `pnpm build` (Next's own type-check) passed with both `/documentos` and `/pareceres` routes compiling.
- **Committed in:** `ff73b4a` (Task 1), `089921d` (Task 2)

**2. [Rule 3 - Blocking] `pnpm install` in `web/` (missing `node_modules`) and created `web/.env.local`**
- **Found during:** Pre-task verification setup
- **Issue:** This worktree's `web/` had no `node_modules` at all (fresh worktree checkout — `node_modules` is gitignored and does not propagate between worktrees, a known pre-existing pattern per `PROJECT.md`'s Phase-101 lesson). `pnpm build` also failed with `Error: BACKEND_API_ORIGIN is required` because `web/.env.local` (gitignored, never committed) did not exist in this worktree.
- **Fix:** Ran `pnpm install` in `web/`; created `web/.env.local` with the same dev values documented in `web/.env.example` (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`). Both are gitignored (`.env*` / `node_modules` in `.gitignore`) — neither was staged or committed; `git status` remained limited to the 2 intended files throughout.
- **Files modified:** none tracked by git (both are gitignored, dev-environment-only)
- **Verification:** `pnpm build` subsequently succeeded end-to-end.
- **Committed in:** N/A (gitignored, not committed by design)

---

**Total deviations:** 2 auto-fixed (1 bug/lint-rule fix applied to both task files, 1 blocking dev-environment setup)
**Impact on plan:** Both were necessary for the plan's own stated verification commands (`tsc`/`lint`/`build`) to run at all or to pass cleanly. No scope creep — no files outside the plan's declared `files_modified` were changed.

## Issues Encountered

- **Worktree base drift (environment-level, not a plan/code issue):** At session start, this worktree's branch (`worktree-agent-ae8e8dd1183416fb7`) was created from a stale `origin/master` that predates the entire v2.14 milestone — it had neither the Phase 111 backend implementation nor any Phase 112 planning docs (`.planning/phases/LEXCV-112-.../112-05-PLAN.md` did not exist on disk). The actual current planning state lived only in the main checkout's detached HEAD (`cbd1625`), never pushed to `origin/master`. Confirmed via `git worktree list`/`git for-each-ref` that the two sibling wave agents (112-01, 112-04) had independently hit and already self-corrected the identical issue on their own branches. Recovered by running `git reset --hard cbd1625` on this worktree's own branch only (sanctioned by the executor protocol's `<worktree_branch_check>` step for exactly this "base drift" scenario, only after confirming HEAD was on the correct `worktree-agent-*` branch, with zero unique commits that would be lost). No impact on sibling worktrees (each has its own branch ref).
- **Pre-existing, out-of-scope: standalone `pnpm exec tsc --noEmit` fails project-wide** — 3 errors, all `Cannot find module 'vitest'` in `src/hooks/use-processos.round-trip.test.ts`, `src/lib/cliente-documento-tipo.test.ts`, `src/schemas/clientes.legacy-documento-tipo.test.ts`. `vitest` is not present in `package.json` at all; these 3 test files predate Phase 112 by many commits (confirmed via `git log`). Out of scope per the executor's scope-boundary rule (pre-existing, unrelated files) — not fixed. Worked around by validating the plan's actual files via scoped `eslint` (clean) and the full `pnpm build` (Next's own build-time type-check, which does not reach these orphaned test files and passed cleanly, including both edited routes).
- **Pre-existing, out-of-scope: project-wide `pnpm lint` reports 6 errors / 17 warnings across 14 *other* files** (none touched by this plan) — `@next/next/no-img-element` (7), `react-hooks/incompatible-library` (6), `react-hooks/set-state-in-effect` (5, in `clientes/[id]/page.tsx`, `dashboard-shell.tsx`, `processos/[id]/page.tsx`, etc.), `@typescript-eslint/no-unused-vars` (2), `react-hooks/refs` (1). This is pre-existing technical debt surfaced by the same newly-enforced `eslint-config-next` ruleset that this plan's own auto-fix (Deviation 1) addressed locally; it is out of scope for this plan (none of the flagged files are in `files_modified`) and is not fixed here. Worth flagging as a candidate for a dedicated lint-debt cleanup phase, given `react-hooks/set-state-in-effect` alone recurs in at least 3 more files beyond this plan's own instance.

## User Setup Required

None - no external service configuration required. (`web/.env.local` was created with the same placeholder dev values already documented in `web/.env.example`; a real deployment already has its own environment configuration per `CLAUDE.md`.)

## Next Phase Readiness

- Documentos and Pareceres are now correctly seeded consumers of the `?q=` URL contract; combined with Plan 112-04 (Clientes/Processos, same contract) this closes SRCH-09 across all 4 list pages once the palette component itself (other 112 plans) links to them.
- No blockers. The two out-of-scope findings above (pre-existing `vitest` gap, pre-existing lint debt) are informational only and do not block this plan or Phase 112's other plans, none of which touch the same files.

---
*Phase: 112-frontend-pesquisa-global-paleta-de-comando*
*Completed: 2026-07-21*
