---
phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando
plan: "03"
subsystem: ui
tags: [nextjs, react, topbar, command-palette, typescript, use-sync-external-store]

# Dependency graph
requires:
  - phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando (Plan 02)
    provides: "GlobalSearchDialog (zero-prop, self-contained command palette) + SEARCH_OPEN_EVENT window-event constant"
provides:
  - "Topbar desktop fake-input trigger (button, near-identical visual to the old decorative <Input>) with a platform-aware ⌘K/Ctrl K kbd hint"
  - "Topbar mobile Search icon trigger (md:hidden, 36px, matches the hamburger-button convention)"
  - "Single mounted <GlobalSearchDialog /> in dashboard-shell.tsx, backing both triggers plus the dialog's own Ctrl+K/⌘K listener"
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "React.useSyncExternalStore (subscribe, getSnapshot, getServerSnapshot) for reading a client-only/navigator-derived value without a useState+useEffect pair — avoids this repo's react-hooks/set-state-in-effect ESLint error while still guaranteeing the SSR/first-hydration render matches the server default and only upgrades post-mount"

key-files:
  created: []
  modified:
    - web/src/components/shared/dashboard-shell.tsx

key-decisions:
  - "Kbd shortcut-hint platform detection implemented via useSyncExternalStore instead of the plan's literal useState+mount-effect wording — the effect-based version trips this repo's react-hooks/set-state-in-effect ESLint error (same constraint 112-02 already hit twice); useSyncExternalStore achieves the identical SSR-safe contract (stable default at hydration, real value only after mount) with zero setState-in-effect calls, matching React's own docs pattern for reading browser APIs like navigator.onLine"

patterns-established: []

requirements-completed: [SRCH-05]

# Metrics
duration: ~12min
completed: 2026-07-21
---

# Phase 112 Plan 03: Frontend — Pesquisa Global (Paleta de Comando) — Topbar Wiring Summary

**Decorative topbar `<Input>` replaced with a fake-input trigger button (⌘K/Ctrl K hint) plus a mobile Search icon trigger, both dispatching `SEARCH_OPEN_EVENT` to the single mounted `GlobalSearchDialog`.**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-07-21T17:34:49Z
- **Tasks:** 1/1 completed
- **Files modified:** 1

## Accomplishments
- Desktop topbar trigger: the decorative `<Input placeholder="Pesquisar processos, entidades...">` (no `onChange`, purely visual) is gone — replaced by a `<button>` carrying the exact same visual classes (`pl-9 bg-slate-100/50 dark:bg-slate-900/50 border border-slate-200 dark:border-slate-800 focus-visible:ring-1 focus-visible:ring-blue-500/50 rounded-full text-sm h-9 shadow-sm transition-all`, full width, left-aligned placeholder-style text), same left-aligned `Search` icon with `group-focus-within:text-blue-500`, plus a trailing `<kbd>` showing "Ctrl K" (default/non-mac) or "⌘K" (detected mac/iOS)
- Mobile topbar trigger: a `Search` icon button (`md:hidden h-9 w-9 rounded-md`, `aria-label="Pesquisar"`) added to the action group immediately before `<ThemeToggle />`, copying the existing hamburger button's shape verbatim so mobile users always have a visible way to open search (Ctrl+K is desktop-only, per `112-CONTEXT.md`)
- `<GlobalSearchDialog />` mounted exactly once (zero props) in the topbar action group, right after `<NotificationBell />` — same "owns its own state, mounted once" relationship the shell already has with `NotificationBell`
- Both triggers call a shared `openSearch()` helper (`window.dispatchEvent(new Event(SEARCH_OPEN_EVENT))`); combined with the dialog's own global Ctrl+K/⌘K `keydown` listener (Plan 112-02), all three entry points now drive one shared open-state — realizing SRCH-05 in full ("open from the topbar field or via Ctrl+K/⌘K, from any authenticated page")
- Now-unused `Input` import removed; `Search` (lucide) import retained since both new triggers use it

## Task Commits

Each task was committed atomically:

1. **Task 1: Replace decorative input with desktop trigger + kbd hint, add mobile trigger, mount GlobalSearchDialog once** - `a0d4552` (feat)

_No TDD tasks in this plan — single `type="auto"` task, no `tdd="true"` flag._

## Files Created/Modified
- `web/src/components/shared/dashboard-shell.tsx` - decorative `<Input>` replaced by desktop trigger button + kbd hint; mobile `Search` icon trigger added to the action group; `<GlobalSearchDialog />` mounted once; unused `Input` import removed

## Decisions Made
- Kbd shortcut-hint platform detection uses `React.useSyncExternalStore` rather than `useState` + a mount-only `useEffect` — see Deviations below for the full rationale (ESLint error avoidance while preserving the exact SSR-safe behavioral contract the plan specified).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Replaced useState+useEffect kbd-hint platform detection with useSyncExternalStore**
- **Found during:** Task 1 verification (`pnpm lint`)
- **Issue:** The plan's action text specified `const [shortcutLabel, setShortcutLabel] = React.useState("Ctrl K")` plus a mount-only `React.useEffect(() => { if (...) setShortcutLabel("⌘K"); }, [])`. Implemented literally, this called `setShortcutLabel` synchronously inside a `useEffect` body — flagged as an **error** by this repo's `react-hooks/set-state-in-effect` ESLint rule ("Calling setState synchronously within an effect can trigger cascading renders"). This is the same rule 112-02-SUMMARY.md's two deviations already hit; here it would have raised the project-wide lint baseline from the documented 6 errors to 7, failing this task's own `pnpm lint` acceptance criterion ("no new errors").
- **Fix:** Replaced the `useState`+`useEffect` pair with `React.useSyncExternalStore(subscribeToNothing, getShortcutLabelSnapshot, getShortcutLabelServerSnapshot)`. `getShortcutLabelServerSnapshot` always returns `"Ctrl K"` (used for SSR and the hydrating client render, guaranteeing no mismatch); `getShortcutLabelSnapshot` reads `navigator.platform`/`navigator.userAgent` and is only consulted by React after mount, exactly matching the plan's required behavioral contract ("First paint is 'Ctrl K' on both server and client, then upgrades post-mount — no mismatch") without any `setState` call inside an effect body. This is React's own documented pattern for subscribing to browser APIs (e.g. `navigator.onLine`) safely across SSR/hydration.
- **Files modified:** `web/src/components/shared/dashboard-shell.tsx`
- **Verification:** Re-ran `pnpm lint` (raw JSON via `eslint --format json`) — `dashboard-shell.tsx`'s error count dropped from 2 (both `react-hooks/set-state-in-effect`, one on my new code at the-then line 59, one pre-existing at the-then line 71) to 1 (only the pre-existing `setDrawerOpen(false)` effect, untouched by this plan). Full-project `pnpm lint` re-confirmed the exact pre-existing baseline: "6 errors, 17 warnings in 14 files" (matching 112-02-SUMMARY.md's documented baseline verbatim). `tsc --noEmit` and `pnpm build` both pass with zero new issues.
- **Committed in:** `a0d4552` (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug surfaced by this project's own stricter-than-default ESLint config, not a functional/behavioral defect — the shipped behavior is identical to what the plan specified)
**Impact on plan:** No change to the plan's described UX, contract, or visual outcome — the kbd hint still defaults to "Ctrl K" everywhere on first paint and upgrades to "⌘K" only after mount on Mac/iOS, exactly as specified. The fix is purely an internal implementation-detail swap confined to this one file.

## Issues Encountered

None beyond the one deviation documented above. Confirmed pre-existing and out of scope (not touched by this plan, per the Scope Boundary rule):
- `dashboard-shell.tsx`'s own pre-existing `React.useEffect(() => { setDrawerOpen(false); }, [pathname])` also trips `react-hooks/set-state-in-effect` (1 of the repo's 6 baseline errors) — present before this plan's edit, unrelated to the search-palette wiring, left untouched.
- The 2 pre-existing `@next/next/no-img-element` warnings on the tenant-logo `<img>` tags in this same file — unrelated to this plan's changes, left untouched.
- The pre-existing, out-of-scope `vitest`-import `tsc` failures (3 test files) and the pre-existing whole-project lint baseline, both already logged in `112-01-SUMMARY.md`/`112-02-SUMMARY.md` and `deferred-items.md`, were re-confirmed unchanged.

## User Setup Required

None - no external service configuration required. Zero new dependencies (per the plan's own threat-model note T-112-SC: GlobalSearchDialog and lucide `Search` already existed from Plan 112-02 / earlier phases).

## Next Phase Readiness

Phase 112's SRCH-05 requirement (open the search palette from the topbar field, the mobile icon, or Ctrl+K/⌘K, from any authenticated dashboard page) is now fully wired end-to-end: Plan 112-01 (types/hooks/utilities) → Plan 112-02 (GlobalSearchDialog component) → Plan 112-03 (topbar integration, this plan). No blockers for the phase's remaining wave (if any — this was the last plan listed in wave 3 per this plan's own frontmatter). The 4 list-page `?q=` seeding work described in `112-CONTEXT.md`/`112-PATTERNS.md` (clientes/processos/documentos/pareceres reading `useSearchParams` on mount) is tracked separately and was not part of this plan's `files_modified` scope (only `dashboard-shell.tsx`).

## Self-Check: PASSED

- FOUND: `web/src/components/shared/dashboard-shell.tsx` (modified, confirmed via direct read post-edit)
- FOUND commit `a0d4552` in `git log --oneline`

No missing items.

---
*Phase: LEXCV-112-frontend-pesquisa-global-paleta-de-comando*
*Completed: 2026-07-21*
