---
phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando
plan: "02"
subsystem: ui
tags: [nextjs, react, cmdk, command-palette, typescript, rbac, tanstack-query]

# Dependency graph
requires:
  - phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando (Plan 01)
    provides: "ResultadoPesquisa type, useDebouncedValue, useGlobalSearch, readRecents/pushRecent, highlightMatch"
provides:
  - "GlobalSearchDialog — self-contained Ctrl+K/⌘K command palette component (zero props, mounted once)"
  - "SEARCH_OPEN_EVENT exported window-event constant for cross-component open triggers"
affects: ["112-03 (mounts GlobalSearchDialog once in dashboard-shell.tsx and adds the desktop/mobile trigger buttons)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Self-contained global dialog shell (own open-state + global keydown listener + custom window-event listener), mirroring NotificationBell's mounted-once shape"
    - "Command shouldFilter={false} to preserve backend-computed ranking, copying combobox.tsx's established workaround for cmdk's default fuzzy scorer"
    - "State transitions (query reset) driven from onOpenChange rather than a useEffect, to satisfy this repo's react-hooks/set-state-in-effect lint rule (configured as an error, not just a warning)"

key-files:
  created:
    - web/src/components/shared/global-search-dialog.tsx
  modified: []

key-decisions:
  - "Query reset on dialog close moved into an onOpenChange wrapper instead of a useEffect — the plan's own task text explicitly named this as an acceptable alternative, and the effect-based version tripped this repo's react-hooks/set-state-in-effect ESLint error"
  - "Recents list read directly at render time instead of React.useMemo keyed on `open` — avoids an exhaustive-deps warning about an 'unnecessary' dependency ESLint can't know is an intentional cache-invalidation key; sessionStorage reads are cheap enough (5-item cap) that memoization isn't needed"

patterns-established:
  - "ResultRow: shared icon+title+subtitle row renderer parameterized by already-rendered ReactNode (not raw strings) — callers explicitly choose whether to pipe text through highlightMatch (real results) or render plain (recents, nothing 'matched' yet)"

requirements-completed: [SRCH-03, SRCH-04, SRCH-05, SRCH-08, SRCH-09, SRCH-10, SRCH-11]

# Metrics
duration: ~30min
completed: 2026-07-21
---

# Phase 112 Plan 02: Pesquisa Global — Paleta de Comando (GlobalSearchDialog) Summary

**Self-contained Ctrl+K/⌘K command palette (cmdk `Command`/`CommandDialog`, `shouldFilter={false}`) consuming Phase 111's `GET /api/v1/pesquisa`, with tipo-grouped bold-highlighted results, sessionStorage recents across all 4 entity types, and permission-gated loading skeletons.**

## Performance

- **Duration:** ~30 min
- **Completed:** 2026-07-21T17:18:00Z
- **Tasks:** 2/2 completed
- **Files modified:** 1 created, 0 modified

## Accomplishments
- `GlobalSearchDialog` — a zero-prop, self-contained component mirroring `NotificationBell`'s "owns its own state" shape: a global `Ctrl+K`/`⌘K` `keydown` listener (with `preventDefault` so the browser default doesn't fire underneath) plus a `SEARCH_OPEN_EVENT` window-event listener so Plan 112-03's topbar triggers can open it without prop-drilling
- Search wiring: `useDebouncedValue(query, 300)` → `useGlobalSearch(debouncedQuery)`, rendered inside `<Command shouldFilter={false}>` so cmdk's fuzzy scorer never re-ranks or hides the backend's already-correctly-ranked results (T-112-06)
- Four distinct, mutually-exclusive `CommandList` states: pre-query (sessionStorage recents when present, else an `Empty` "comece a escrever" state), permission-gated loading skeletons (`permissions.isFetched && can.view(scope)`, never `!isLoading`), no-results (`Empty`/`EmptyTitle`/`EmptyDescription`, not `CommandEmpty`, per the locked UI-SPEC decision), and an inline error message
- Results grouped by `tipo` in the locked order (Clientes → Processos → Documentos → Pareceres), each item showing the entity icon + bold-highlighted title/subtitle (`highlightMatch`) and a per-group "Ver todos os {Entidade}" link to `/{segment}?q=<termo>`
- Navigation guarded by `isInternalLinkUrl(rota)` before `router.push` (T-112-04, open-redirect mitigation reusing the same hardened WHATWG-parser guard `notification-bell.tsx` already uses); `pushRecent` records all 4 entity types on selection

## Task Commits

Each task was committed atomically:

1. **Task 1: GlobalSearchDialog shell, open-state, keydown/open-event, search wiring, grouped results + navigation** - `108d8cc` (feat)
2. **Task 2: Pre-query (recents), loading (permission-gated skeletons), no-results, and error states** - `12d1184` (feat)

_No TDD tasks in this plan — both `type="auto"`, no `tdd="true"` flag._

## Files Created/Modified
- `web/src/components/shared/global-search-dialog.tsx` - self-contained `GlobalSearchDialog` command palette + exported `SEARCH_OPEN_EVENT` constant

## Decisions Made
- Query reset on close moved from a `useEffect` into an `onOpenChange` wrapper (`handleOpenChange`) — see Deviations below; the plan's own task text explicitly permitted either approach
- Recents read directly at render time instead of memoized — see Deviations below

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Moved close-triggered query reset out of a `useEffect` into `onOpenChange`**
- **Found during:** Task 2 verification (`pnpm lint`)
- **Issue:** The first implementation reset `query` to `""` inside `React.useEffect(() => { if (!open) setQuery(""); }, [open])`. This repo's ESLint config flags that pattern as an **error** (`react-hooks/set-state-in-effect` — "Calling setState synchronously within an effect can trigger cascading renders"), which would have failed Task 2's own `pnpm lint` acceptance criterion ("no new lint errors for the file").
- **Fix:** Replaced the effect with a `handleOpenChange(next: boolean)` function passed to `CommandDialog`'s `onOpenChange` prop; it calls `setOpen(next)` and, only when closing, `setQuery("")`. The plan's task text explicitly named this as a sanctioned alternative ("Reset `query` to "" whenever the dialog transitions to closed (either a separate effect keyed on `open`, or inside `onOpenChange`)").
- **Files modified:** `web/src/components/shared/global-search-dialog.tsx`
- **Verification:** Re-ran `pnpm lint`; the file's ESLint JSON entry shows `errorCount: 0, warningCount: 0` (confirmed by parsing the raw ESLint JSON report directly, not just the condensed summary).
- **Committed in:** `12d1184` (Task 2 commit)

**2. [Rule 1 - Bug] Replaced `useMemo`-keyed-on-`open` recents read with a plain render-time read**
- **Found during:** Task 2 verification (`pnpm lint`)
- **Issue:** `const recents = React.useMemo(() => readRecents(), [open])` triggered a `react-hooks/exhaustive-deps` warning ("React Hook React.useMemo has an unnecessary dependency: 'open'"), since `readRecents()` doesn't reference `open` — the intentional "refresh whenever the dialog reopens" semantic isn't something ESLint's static dependency analysis can recognize.
- **Fix:** Removed the `useMemo`; `recents` is now computed directly during render (`const recents = readRecents();`). `readRecents()` reads a `sessionStorage` value capped at 5 items and does a small `JSON.parse` — cheap enough on every render that memoization added no measurable benefit, and this still guarantees the list reflects the latest state every time the dialog is open.
- **Files modified:** `web/src/components/shared/global-search-dialog.tsx`
- **Verification:** Same `pnpm lint` re-run as above — 0 warnings for this file.
- **Committed in:** `12d1184` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 - bugs surfaced by this project's own stricter-than-default ESLint config, not functional/behavioral defects)
**Impact on plan:** Neither change altered the plan's described UX or contracts — both are implementation-detail fixes required to pass Task 2's own `pnpm lint` acceptance criterion. No scope creep; both fixes use alternatives the plan itself already anticipated or that are strictly internal to this one file.

## Issues Encountered

None beyond the two deviations documented above. The pre-existing, out-of-scope `vitest`-import `tsc` failures (3 test files, unrelated to this plan) and the pre-existing whole-project lint baseline (6 errors/17 warnings across 14 other files) — both already logged in `112-01-SUMMARY.md`'s deviations and `deferred-items.md` — were re-confirmed unchanged and untouched by this plan's own verification runs.

## User Setup Required

None - no external service configuration required. Zero new dependencies (`command`, `dialog`, `empty`, `skeleton`, `lucide-react` were all already installed since Phase 101/107), per the plan's own threat-model note (T-112-SC).

## Next Phase Readiness

Plan 112-03 can now `import { GlobalSearchDialog, SEARCH_OPEN_EVENT } from "@/components/shared/global-search-dialog"`, mount `<GlobalSearchDialog />` once in `dashboard-shell.tsx` (zero props, same relationship as `<NotificationBell />`), and add the desktop/mobile trigger buttons that call `window.dispatchEvent(new Event(SEARCH_OPEN_EVENT))` on click. No blockers. The component's own Ctrl+K/⌘K listener and all four UI states are already fully wired and independently verified (`tsc --noEmit`, `pnpm lint`, `pnpm build` all pass with zero issues attributable to this file).

## Self-Check: PASSED

- FOUND: `web/src/components/shared/global-search-dialog.tsx`
- FOUND: `.planning/phases/LEXCV-112-frontend-pesquisa-global-paleta-de-comando/112-02-SUMMARY.md`
- FOUND commit `108d8cc` (Task 1) in `git log --all`
- FOUND commit `12d1184` (Task 2) in `git log --all`

No missing items.

---
*Phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando*
*Completed: 2026-07-21*
