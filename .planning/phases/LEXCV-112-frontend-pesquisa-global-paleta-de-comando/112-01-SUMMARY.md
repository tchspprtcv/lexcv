---
phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando
plan: "01"
subsystem: ui
tags: [nextjs, react, tanstack-query, typescript, sessionStorage, search]

# Dependency graph
requires:
  - phase: LEXCV-111-backend-pesquisa-global-cross-entity-api
    provides: "Stable GET /api/v1/pesquisa?q= endpoint returning ResultadoPesquisaDto[] (tipo/id/titulo/subtitulo/rota), tenant/RBAC-filtered, TERMO_MIN_LENGTH=2"
provides:
  - "ResultadoPesquisa TS type mirroring ResultadoPesquisaDto 1:1 (subtitulo optional)"
  - "Generic useDebouncedValue<T>(value, delayMs=300) hook"
  - "useGlobalSearch(termo) TanStack Query hook, enabled-gated at >= 2 trimmed chars, calls GET /pesquisa?q="
  - "search-recents.ts: readRecents()/pushRecent() sessionStorage-backed recents, cap 5, dedupe by (tipo,id), all 4 entity types eligible"
  - "highlight-match.tsx: highlightMatch(text, query) bold-substring React renderer with plain-text fallback"
affects: [112-02 (search palette dialog that composes these units)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Generic debounce hook extracted from repeated inline setTimeout/clearTimeout effect (clientes/processos pages)"
    - "sessionStorage-backed client state (first use in this codebase) — read/write wrapped in try/catch, best-effort, never throws into UI"
    - "Bold-substring highlight returning React.ReactNode via child-array composition (never dangerouslySetInnerHTML)"

key-files:
  created:
    - web/src/types/search.ts
    - web/src/lib/use-debounced-value.ts
    - web/src/hooks/use-global-search.ts
    - web/src/lib/search-recents.ts
    - web/src/lib/highlight-match.tsx
  modified: []

key-decisions:
  - "All 4 entity types (cliente/processo/documento/parecer) are eligible for recents — documento has a real detail route (/documentos/[id]/page.tsx), per 112-CONTEXT.md's correction of an earlier false premise"
  - "recents live in sessionStorage only, never localStorage, never sent to the server (SRCH-10 confidentiality choice for a shared institutional workstation)"

patterns-established:
  - "sessionStorage recents: JSON array, cap RECENTS_CAP, dedupe-then-unshift-then-slice on write, defensive try/catch on both read and write"
  - "highlightMatch: case-insensitive indexOf against original-cased text, slice-and-wrap in <strong>, plain-text fallback when no literal match (no accent-aware matching attempted)"

requirements-completed: [SRCH-03, SRCH-10, SRCH-11]

# Metrics
duration: ~25min
completed: 2026-07-21
---

# Phase 112 Plan 01: Pesquisa Global — Data + Pure-Logic Foundation Summary

**TanStack Query search hook + generic debounce hook + sessionStorage recents + bold-substring highlighter, all typed against Phase 111's shipped `ResultadoPesquisaDto` contract, zero new dependencies.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-07-21T16:49:59Z
- **Tasks:** 3/3 completed
- **Files modified:** 5 created, 0 modified

## Accomplishments
- `ResultadoPesquisa` TS type mirrors the backend's `ResultadoPesquisaDto` record field-for-field (`subtitulo` correctly marked optional, since `montarSubtituloCliente` and the documento/parecer branches can emit `null`)
- Generic `useDebouncedValue<T>` hook extracted from the two near-identical inline 300ms debounce effects already duplicated in `clientes/page.tsx` and `processos/page.tsx`
- `useGlobalSearch(termo)` fires `GET /api/v1/pesquisa?q=` (URL-encoded) only when the trimmed term is `>= 2` chars, mirroring the backend's own `TERMO_MIN_LENGTH`, following the `usePesquisarPareceres` precedent exactly
- `search-recents.ts` — the first `sessionStorage` consumer in this codebase — provides capped (5), deduped (`tipo`+`id`), most-recent-first recent-visit tracking across all 4 entity types, never `localStorage`, never transmitted
- `highlight-match.tsx` — the first bold-substring-highlight renderer in this codebase — case-insensitive literal match, `<strong>`-only (no color/underline/`dangerouslySetInnerHTML`), plain-text fallback when the backend's `unaccent`-normalized match has no literal substring equivalent

## Task Commits

Each task was committed atomically:

1. **Task 1: ResultadoPesquisa type + useDebouncedValue hook** - `a987d2e` (feat)
2. **Task 2: useGlobalSearch TanStack Query hook** - `9248068` (feat)
3. **Task 3: search-recents (sessionStorage) + highlightMatch helpers** - `9b1d2d2` (feat)

_No TDD tasks in this plan — all `type="auto"`, no `tdd="true"` flag._

## Files Created/Modified
- `web/src/types/search.ts` - `PesquisaResultadoTipo` union + `ResultadoPesquisa` interface (mirrors `ResultadoPesquisaDto`)
- `web/src/lib/use-debounced-value.ts` - generic 300ms `useDebouncedValue<T>` client hook
- `web/src/hooks/use-global-search.ts` - `useGlobalSearch(termo)`, enabled-gated TanStack Query wrapper around `GET /pesquisa?q=`
- `web/src/lib/search-recents.ts` - `readRecents()`/`pushRecent()`, sessionStorage-backed, cap 5, dedupe by `(tipo,id)`, all 4 tipos eligible
- `web/src/lib/highlight-match.tsx` - `highlightMatch(text, query)`, bold-substring React renderer, plain fallback on no match

## Decisions Made
None new — this plan implements decisions already locked in `112-CONTEXT.md` (all 4 entity types eligible for recents; sessionStorage over localStorage; bold-only highlight). No deviation from those locked choices.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Worktree branch was based on a stale commit missing this plan's own files**
- **Found during:** Startup, before Task 1 (attempting to read `112-01-PLAN.md`/`112-CONTEXT.md`)
- **Issue:** This worktree's branch (`worktree-agent-a74ade450155e6a6c`) was created from commit `70ff067`, which predates the 5 commits on `master`/main-repo detached-HEAD that created the entire Phase 112 planning directory (`.planning/phases/LEXCV-112-.../`). `.planning/phases/` did not exist at all in this worktree — a missing-referenced-file blocking condition, and the exact recurring failure mode already documented in `PROJECT.md`'s Key Decisions ("Um agente executor spawnado com isolamento de worktree apontou para um checkout desatualizado sem os commits de planeamento recentes").
- **Fix:** Verified (a) HEAD was correctly on the `worktree-agent-*` branch, not detached/protected (per `<worktree_branch_check>`); (b) this branch had zero unique commits of its own (`git log cbd1625..HEAD` empty); (c) `cbd1625` (the main repo's current detached-HEAD tip, containing the full Phase 112 plan set) is a direct descendant of this branch's HEAD (`git merge-base --is-ancestor HEAD cbd1625` true). This is a safe fast-forward "base drift" correction, explicitly sanctioned by `<worktree_branch_check>`'s own instructions once the branch-identity assertion passes. Ran `git reset --hard cbd1625`; re-verified branch identity unchanged afterward (still `worktree-agent-a74ade450155e6a6c`, not detached/protected). Did not touch `master` or sibling worktree branches (each is an independent ref).
- **Files affected:** none (branch ref only — no working-tree files existed to lose, since this branch had never diverged)
- **Verification:** `git ls-tree -r cbd1625 -- .planning/phases/LEXCV-112.../` confirmed all 8 phase files present before resetting; post-reset `git symbolic-ref HEAD` confirmed correct branch.

**2. [Rule 3 - Blocking] `web/node_modules` did not exist in this worktree, and the shell-proxy hook (RTK) silently fabricated a false-positive `tsc` result**
- **Found during:** Task 1 verification (`pnpm exec tsc --noEmit`)
- **Issue:** The condensed tool output reported "TypeScript: No errors found," but cross-checking the RTK proxy's own raw tee log (`~/AppData/Local/rtk/tee/*_tsc.log`) showed the actual command output was an npx placeholder error ("This is not the tsc command you are looking for") — `web/node_modules` had never been installed in this worktree, so no real `tsc` binary existed to run. The condensed summary did not reflect the real (failing) command outcome at all.
- **Fix:** Re-ran all verification-critical commands via `rtk proxy <cmd>` (the documented raw/unfiltered escape hatch) instead of trusting condensed summaries. Ran `pnpm install` in `web/` (standard lockfile-restore bootstrap — `pnpm-lock.yaml` unchanged, "resolved 647, reused 647," zero new/unverified packages; explicitly distinct from the Rule-3-excluded "install a new package" case) to hydrate `node_modules` for real. Re-ran `tsc --noEmit` and `pnpm lint` via `rtk proxy` and confirmed via targeted grep that none of this plan's 5 files appear in either's (real) error/warning output.
- **Files affected:** none source-level; `web/node_modules` created (gitignored, not committed)
- **Verification:** Raw `rtk proxy pnpm exec tsc --noEmit` and `rtk proxy pnpm lint` output captured directly; grepped for each of the 5 new filenames — zero matches in both. Remaining (pre-existing, unrelated) errors logged to `deferred-items.md` per the scope-boundary rule rather than fixed.

---

**Total deviations:** 2 auto-fixed (both Rule 3 - blocking issues, both environment/tooling rather than code-logic problems)
**Impact on plan:** Neither deviation touched this plan's actual deliverables or logic — both were prerequisites for being able to execute and genuinely verify the plan at all. No scope creep into the plan's own file set.

## Issues Encountered

Two pre-existing, out-of-scope problems were discovered while running the whole-project `tsc`/`lint` verification (not introduced by, and not touching, this plan's 5 files) and logged to `.planning/phases/LEXCV-112-frontend-pesquisa-global-paleta-de-comando/deferred-items.md` per the scope-boundary rule rather than fixed:
1. Three `.test.ts` files (Phase 74/83) import `vitest`, which is not installed/configured anywhere in `web/package.json` — whole-project `tsc --noEmit` fails with `TS2307` for these 3 files only.
2. Whole-project `pnpm lint` (the first time it has run against a fully-installed `node_modules` in this specific worktree) surfaced 23 pre-existing problems (6 errors, 17 warnings — mostly `react-hooks/set-state-in-effect` and React Compiler "incompatible library" skip-warnings) across 14 files unrelated to this plan.

Neither blocks this plan's own acceptance criteria (all 5 new files are error/warning-free in both tools) — see `deferred-items.md` for full detail and file-by-file breakdown.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 112-02 (the `GlobalSearchDialog` command palette) can now import and compose all 5 units built here (`ResultadoPesquisa`, `useDebouncedValue`, `useGlobalSearch`, `readRecents`/`pushRecent`, `highlightMatch`) exactly per the `<interfaces>` contract locked in `112-01-PLAN.md`. No blockers for 112-02. The two deferred pre-existing issues (vitest gap, lint debt) are unrelated to this dependency chain and do not block any Phase 112 plan.

---
*Phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando*
*Completed: 2026-07-21*
