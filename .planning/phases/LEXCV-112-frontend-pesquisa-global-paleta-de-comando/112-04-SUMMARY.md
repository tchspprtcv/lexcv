---
phase: 112-frontend-pesquisa-global-paleta-de-comando
plan: "04"
subsystem: ui
tags: [nextjs, react, useSearchParams, clientes, processos, search]

# Dependency graph
requires:
  - phase: 112 (same phase, decoupled by contract)
    provides: "The ?q= URL contract the future GlobalSearchDialog's 'Ver todos' links will use to navigate here (this plan's Wave 1 slot has no hard file dependency on the palette component itself)"
provides:
  - "Clientes list page (/clientes) reads ?q= from the URL and seeds the existing draftQuery -> filters.q -> GET /clientes?q= path"
  - "Processos list page (/processos) reads ?q= from the URL and seeds the existing draftQuery -> filters.q -> GET /processos?q= path"
  - "Re-navigation to a new ?q= while already on either page re-seeds the search box (supports 'Ver todos' clicked from the same list page)"
affects: [112-01 (GlobalSearchDialog component, consumes this ?q= contract), 112-02, 112-03 (Documentos/Pareceres equivalents, different filter shapes per 112-PATTERNS.md)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "URL-param-seeds-local-draft-state without useEffect: a companion `lastSeededQ` state plus a conditional setState call in the render body (React's documented 'adjusting state during render' idiom) replaces the naive useEffect seed, to satisfy this codebase's react-hooks/set-state-in-effect ESLint rule"

key-files:
  created: []
  modified:
    - "web/src/app/(dashboard)/clientes/page.tsx"
    - "web/src/app/(dashboard)/processos/page.tsx"

key-decisions:
  - "Replaced the plan's literal useEffect-based seed with a render-phase conditional setState (tracked via lastSeededQ), because the useEffect version trips react-hooks/set-state-in-effect (error) in this codebase's ESLint config — behavior (seed-once, re-seed-on-new-?q=, never fight user edits) is identical."
  - "Corrected a worktree branch base drift (reset to the correct up-to-date commit) before making any file edits, since the assigned worktree branch was created from a stale base predating all of Phase 111 and Phase 112 planning."

requirements-completed: [SRCH-09]

# Metrics
duration: ~20min
completed: 2026-07-21
---

# Phase 112 Plan 04: Seed ?q= into Clientes/Processos list pages Summary

**Clientes and Processos list pages now read `?q=` from the URL and seed their existing `draftQuery` search box via a render-phase state adjustment (not `useEffect`, to satisfy `react-hooks/set-state-in-effect`), reusing the already-working `GET /clientes|/processos?q=` filters with zero new plumbing.**

## Performance

- **Duration:** ~20 min (includes an unplanned worktree base-drift recovery and a full `pnpm install`, since this worktree had no `node_modules` at all)
- **Completed:** 2026-07-21T16:52:00Z
- **Tasks:** 2/2 completed
- **Files modified:** 2

## Accomplishments
- `/clientes?q=<termo>` pre-fills the Clientes search box and filters the list to it via the existing `draftQuery` → `filters.q` → `GET /clientes?q=` path, without discarding NIF/tipo/ativo/localidade/date-range filters
- `/processos?q=<termo>` pre-fills the Processos search box and filters the list to it via the existing `draftQuery` → `filters.q` → `GET /processos?q=` path, without discarding estado/tribunal/area/cliente filters
- Navigating to a new `?q=` while already on either page re-seeds the box (the palette's future "Ver todos" link works even when clicked from the same list page)
- Zero new filter state, hooks, or backend changes — both pages build/lint clean

## Task Commits

Each task was committed atomically:

1. **Task 1: Seed ?q= into Clientes list draftQuery** - `6533fbe` (feat)
2. **Task 2: Seed ?q= into Processos list draftQuery** - `c429040` (feat)

**Plan metadata:** (this SUMMARY's own commit, created immediately after this file)

## Files Created/Modified
- `web/src/app/(dashboard)/clientes/page.tsx` - Reads `?q=` via `useSearchParams`; seeds `draftQuery` through a render-phase conditional (`lastSeededQ` tracking state), not an effect
- `web/src/app/(dashboard)/processos/page.tsx` - Identical shape applied; reads `?q=`, seeds `draftQuery` via the same render-phase pattern

## Decisions Made
- Used React's "adjusting state during render" idiom (a companion `lastSeededQ` state, compared against the extracted `seededQ` string, with `setDraftQuery`/`setLastSeededQ` called conditionally in the render body) instead of the plan's literal `useEffect` shape. This preserves all three plan truths (seed-on-load, re-seed-on-new-`?q=`, never touch other draft* filters) while passing `react-hooks/set-state-in-effect`, which the plan's exact prescribed code would have failed.
- Import placement: grouped `next/navigation`'s `useSearchParams` with the existing `next/link` import at the top of each file, matching the Next.js-imports-together convention already visible in `dashboard-shell.tsx`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Corrected worktree branch base drift before any edits**
- **Found during:** Mandatory first-action worktree branch check
- **Issue:** This worktree's branch (`worktree-agent-ae787a4c130605201`) was created from a stale base commit (`70ff067`, "chore: remove root v2.13-MILESTONE-AUDIT.md") that predates all of Phase 111 (backend pesquisa global) and every commit of Phase 112 planning — `.planning/phases/` didn't exist at all at that commit, so `112-04-PLAN.md`/`112-CONTEXT.md` were unreadable. This reproduces a lesson already documented in `PROJECT.md`'s Key Decisions ("Um agente executor spawnado com isolamento de worktree apontou para um checkout desatualizado sem os commits de planeamento recentes").
- **Fix:** Confirmed the worktree branch had zero unique commits (`git log master..HEAD` empty) and that the correct tip (`cbd1625`, containing all of Phase 111 + Phase 112 planning) was a strict, non-diverging descendant of the stale base — also independently confirmed since a sibling worktree agent (112-01) had already adopted the same commit. Re-asserted the mandatory worktree-agent-* branch/clean-tree safety checks, then ran `git reset --hard cbd1625`.
- **Files modified:** None (git ref move only; no working-tree diff from the reset itself)
- **Verification:** Post-reset `git log --oneline -3` showed HEAD at `cbd1625`; `.planning/phases/LEXCV-112-frontend-pesquisa-global-paleta-de-comando/112-04-PLAN.md` and `112-CONTEXT.md` became readable.
- **Committed in:** N/A (git ref update, not a file commit)

**2. [Rule 1 - Bug] Replaced useEffect-based seed with a render-phase conditional update**
- **Found during:** Task 1 verification (`pnpm exec eslint`)
- **Issue:** The plan's literally-prescribed code (`React.useEffect(() => { if (seededQ) setDraftQuery(seededQ); }, [seededQ]);`) triggers `react-hooks/set-state-in-effect` (severity: error, part of `eslint-config-next`) in both files — this codebase's lint gate flags synchronous `setState` calls inside effect bodies as a hard error, not a warning.
- **Fix:** Replaced the effect with React's documented "adjusting state during render" pattern: a companion `lastSeededQ` state tracks the last value seeded, and `setLastSeededQ`/`setDraftQuery` are called conditionally in the render body (`if (seededQ && seededQ !== lastSeededQ)`) — React explicitly supports this as a bail-out-and-re-render mechanism with no flash of stale UI. Behavior is unchanged from the plan's intent: seeds once per distinct `?q=` value, re-seeds on navigation to a new `?q=`, never overwrites the user's own typed edits.
- **Files modified:** `web/src/app/(dashboard)/clientes/page.tsx`, `web/src/app/(dashboard)/processos/page.tsx`
- **Verification:** `pnpm exec eslint` scoped to both files: 0 errors (was 2 errors — 1 per file — before the fix). `pnpm exec tsc --noEmit`: introduces no new errors (3 pre-existing, unrelated errors remain — see Issues Encountered).
- **Committed in:** `6533fbe` (Task 1), `c429040` (Task 2) — folded into each task's own commit, not a separate fix commit.

---

**Total deviations:** 2 auto-fixed (1 Rule 3 - blocking git-state fix, 1 Rule 1 - bug fix), plus 1 environment-setup action (below, not a numbered deviation rule).
**Impact on plan:** No scope creep. Both files match the plan's intended behavior and acceptance criteria exactly; the only departure from the plan's literal text is the effect-vs-render-phase mechanism, required to pass this codebase's own lint gate.

## Issues Encountered

- **Missing `node_modules` in this worktree.** `web/node_modules` did not exist at all (worktrees don't carry gitignored directories), so the first `pnpm exec tsc --noEmit` call silently produced a misleading placeholder ("This is not the tsc command you are looking for") instead of real output — not a real "0 errors" result. Ran `pnpm install` (all already-declared `package.json` dependencies, no new/unverified packages, so the package-install exclusion in Rule 3 doesn't apply) before trusting any verification output afterward.
- **Bash-tool `grep` produced false negatives** on parenthesized/quoted patterns (e.g. `searchParams.get("q")`) in this Windows/rtk-proxied shell, even though the target text was confirmed present moments later via the dedicated Grep tool. Switched to the Grep tool for all acceptance-criteria pattern checks going forward in this session.
- **Pre-existing, unrelated `tsc --noEmit` project-wide failures (3 errors, out of scope):** `Cannot find module 'vitest'` in `src/hooks/use-processos.round-trip.test.ts`, `src/lib/cliente-documento-tipo.test.ts`, `src/schemas/clientes.legacy-documento-tipo.test.ts`. `vitest` is not declared anywhere in `package.json`; these test files date to Phase 74/83-02, long before Phase 112, and are unrelated to `clientes/page.tsx`/`processos/page.tsx`. Per the deviation rules' scope boundary, not fixed — logged below.
- **Pre-existing, unrelated full-project `pnpm lint` output (6 errors / 17 warnings across 14 other files, out of scope):** mix of `@next/next/no-img-element`, `react-hooks/incompatible-library`, `react-hooks/set-state-in-effect` (in files this plan never touched, e.g. `clientes/[id]/page.tsx`, `dashboard-shell.tsx`, `processos/[id]/page.tsx`), `@typescript-eslint/no-unused-vars`, `react-hooks/refs`. Confirmed pre-existing and unrelated (neither `clientes/page.tsx` nor `processos/page.tsx` appear in the list); not fixed, out of scope for this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Both Clientes and Processos list pages now honor `?q=` from the URL, ready to receive links from the command-palette component this phase is building elsewhere (a sibling Wave 1 plan).
- `SRCH-09` is only half-closed by this plan: Documentos and Pareceres (the other two lists the requirement covers) are explicitly out of this plan's `files_modified` scope. Per `112-PATTERNS.md`, they need materially different treatment — Documentos has no `q` filter of any kind today (would need a new client-side filter), and Pareceres has a two-mode filter shape (`ParecerPesquisaFilters.texto` behind a togglable panel, not a single `filters.q`). Whichever plan covers those two pages should be checked before considering `SRCH-09` fully done.
- No blockers for merge: both files typecheck/lint clean in isolation (own-file scope); the only friction encountered was environmental (missing `node_modules`, a stale worktree branch base) and both were resolved without touching unrelated code.
- Deferred, unrelated technical debt discovered along the way (not part of this plan's scope, not fixed): missing `vitest` devDependency causing 3 pre-existing `tsc` failures; several pre-existing `react-hooks/set-state-in-effect`/`@next/next/no-img-element` lint errors in other files. Both should be tracked separately from Phase 112.

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/clientes/page.tsx`
- FOUND: `web/src/app/(dashboard)/processos/page.tsx`
- FOUND: commit `6533fbe`
- FOUND: commit `c429040`

---
*Phase: 112-frontend-pesquisa-global-paleta-de-comando*
*Completed: 2026-07-21*
