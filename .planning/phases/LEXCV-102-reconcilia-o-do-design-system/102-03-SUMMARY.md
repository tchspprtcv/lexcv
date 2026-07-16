---
phase: LEXCV-102-reconcilia-o-do-design-system
plan: 03
subsystem: ui
tags: [tooltip, radix-ui, accessibility, aria-label, shadcn, nextjs]

# Dependency graph
requires:
  - phase: LEXCV-101-funda-o-cli-init-e-design-tokens
    provides: tooltip.tsx primitive (Tooltip/TooltipContent/TooltipProvider/TooltipTrigger), installed but not yet mounted/consumed
provides:
  - Single global TooltipProvider mount (700ms delay) in web/src/app/providers.tsx
  - Portuguese tooltips + matching aria-labels on all clientes row-action icon-only buttons (desktop + mobile)
  - Portuguese tooltips + matching aria-labels on settings user-table row-action icon-only buttons (Edit/Trash2)
  - Portuguese tooltip + aria-label on sidebar footer LogOut icon button (desktop + mobile drawer)
  - Broadened-grep evidence that no other explicit-action icon-only row button remains unwrapped app-wide (DSR-03 closure)
affects: [102-04, any future phase touching clientes/page.tsx, settings/page.tsx, dashboard-shell.tsx, or providers.tsx]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Double-asChild nesting: TooltipTrigger asChild > Button asChild > Link — each asChild merges one level of props/ref, verified compiling via pnpm build"
    - "Single-level nesting: TooltipTrigger asChild > Button (plain onClick, no asChild) — used for Trash2/Edit/LogOut actions"

key-files:
  created: []
  modified:
    - web/src/app/providers.tsx
    - web/src/app/(dashboard)/clientes/page.tsx
    - web/src/app/(dashboard)/settings/page.tsx
    - web/src/components/shared/dashboard-shell.tsx

key-decisions:
  - "DSR-03 'ícones da sidebar colapsada' interpreted as the sidebar-footer LogOut icon-only button (desktop + mobile drawer duplicate) — the dashboard has no literal icon-only collapsed-rail mode; dashboard-shell.tsx and bottom-nav.tsx always render icon+label. This is a recorded, evidence-based reading of the requirement text, not a silent assumption."
  - "Broadened grep (Edit|Eye|Pencil|Trash2|Printer against h-8/h-9 icon sizings) across ALL web/src/app/(dashboard)/**/page.tsx confirms exactly two explicit-action row-action surfaces in scope: clientes/page.tsx and settings/page.tsx — the earlier narrow grep in 102-PATTERNS.md had omitted 'Edit' and thus missed settings/page.tsx."
  - "processos/pareceres MoreVertical navigate-to-detail links, agenda Chevron prev/next, processos disabled pagination placeholder, processos/dashboard back-nav arrow, and notificacoes' already-labelled Check button are confirmed OUT of DSR-03 scope — none are explicit-action icon buttons."

requirements-completed: [DSR-02, DSR-03]

# Metrics
duration: 20min
completed: 2026-07-16
---

# Phase 102 Plan 03: Tooltip Rollout (DSR-03) Summary

**Single global TooltipProvider (700ms delay) mounted in providers.tsx; every literal icon-only row-action button in DSR-03 scope (clientes desktop/mobile, settings user-table, sidebar LogOut) now shows a Portuguese hover/focus tooltip and carries a matching aria-label.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-07-15T23:56Z (approx, first providers.tsx edit)
- **Completed:** 2026-07-16T00:14:05-01:00
- **Tasks:** 2/2 completed
- **Files modified:** 4

## Accomplishments

- Mounted a single `TooltipProvider` in `web/src/app/providers.tsx` with `delayDuration={700}` explicitly set (tooltip.tsx defaults to 0/instant — the explicit override was required, not optional).
- Wrapped all 6 icon-only row-action buttons in `clientes/page.tsx` (desktop `ClienteRow`: Eye/Printer/Pencil/Trash2; mobile card: Eye/Pencil) in `Tooltip`/`TooltipTrigger`/`TooltipContent`, each carrying a matching `aria-label`.
- Wrapped both icon-only row-action buttons in `settings/page.tsx`'s user-management table (Edit always-rendered, Trash2 conditionally rendered via `{user.id !== currentUserId && ...}`) — the RBAC/identity conditional was preserved verbatim.
- Wrapped the sidebar footer `LogOut` icon button in `dashboard-shell.tsx` at both the desktop aside (line ~154→160) and the mobile drawer duplicate (line ~234→243), each with `Terminar sessão` tooltip copy and matching `aria-label`.
- Ran a broadened coverage grep (`Edit|Eye|Pencil|Trash2|Printer` against `h-8 w-8 p-0`/`h-9 w-9 p-0` sizing) across every `web/src/app/(dashboard)/**/page.tsx` and confirmed the only two explicit-action icon-only row-action surfaces app-wide are `clientes/page.tsx` and `settings/page.tsx` — no other file needed a Tooltip.
- `pnpm build` passes green — the double-asChild `TooltipTrigger asChild > Button asChild > Link` nesting (used for Eye/Printer/Pencil, which navigate) compiles and typechecks correctly alongside the single-level `TooltipTrigger asChild > Button` nesting (used for Trash2/Edit/LogOut, which have plain `onClick`).

## Recorded Design Decision (restated per plan's <output> requirement)

**DSR-03 "ícones da sidebar colapsada" interpretation:** The dashboard has NO literal "collapsed sidebar showing only icons" mode — `dashboard-shell.tsx` and `bottom-nav.tsx` ALWAYS render icon+label together, never an icon-only rail. DSR-03's phrase "ícones da sidebar colapsada" is therefore interpreted, deliberately and on the evidence, as the ONE genuinely icon-only control in the sidebar chrome: the sidebar-footer `LogOut` button (desktop + mobile-drawer duplicate). This is a recorded, evidence-based reading of the requirement text, not a silent assumption, and is captured here per the plan's explicit instruction to persist it in this SUMMARY for the phase audit trail.

## Broadened-Grep Coverage Result (DSR-03 closure evidence)

Searched `Edit|Eye|Pencil|Trash2|Printer` combined with `h-8 w-8 p-0`/`h-9 w-9 p-0` icon sizing across every `web/src/app/(dashboard)/**/page.tsx`. Matches found in 7 files total for the sizing pattern; only `clientes/page.tsx` and `settings/page.tsx` contain in-scope explicit-action icon buttons (now both wrapped). The residual matches in the other 5 files were individually inspected and confirmed out of scope:

| File | Icon-only button found | Verdict |
|------|------------------------|---------|
| `processos/page.tsx:206` | `Download` (disabled) | out of scope — disabled pagination-area placeholder, not Edit/Eye/Pencil/Trash2/Printer |
| `processos/page.tsx:381` | `MoreVertical` (Button asChild > Link) | out of scope — navigate-to-detail affordance, not an explicit-action icon |
| `processos/dashboard/page.tsx:30` | `ArrowLeft` | out of scope — back-nav arrow |
| `pareceres/page.tsx:469` | `MoreVertical` (Button asChild > Link) | out of scope — navigate-to-detail affordance |
| `notificacoes/page.tsx:312` | `Check` ("Marcar como lida") | out of scope — already carries `aria-label` + native `title` |
| `agenda/page.tsx:198,219` | `ChevronLeft`/`ChevronRight` | out of scope — calendar month prev/next nav |
| `documentos/page.tsx` | (no icon-only Trash2/Edit button — delete action renders visible text "Apagar") | out of scope — not an icon-only button |

No explicit-action icon-only row button remains unwrapped outside the recorded out-of-scope set. DSR-03 is closed.

## Task Commits

Each task was committed atomically:

1. **Task 1: Mount a single TooltipProvider in providers.tsx with delayDuration={700}** - `95d6074` (feat)
2. **Task 2: Wrap icon-only row-action buttons (clientes + settings) and sidebar LogOut in Tooltip, add aria-labels, re-grep for coverage, verify build** - `3500713` (feat)

## Files Created/Modified

- `web/src/app/providers.tsx` - Added `TooltipProvider` import + mount (wraps `{children}` inside `NextThemesProvider`) with `delayDuration={700}` explicit
- `web/src/app/(dashboard)/clientes/page.tsx` - Added Tooltip import; wrapped 6 icon-only row-action buttons (desktop Eye/Printer/Pencil/Trash2, mobile Eye/Pencil) with Tooltip + aria-label
- `web/src/app/(dashboard)/settings/page.tsx` - Added Tooltip import; wrapped user-table Edit (always) and Trash2 (conditional on `user.id !== currentUserId`) buttons with Tooltip + aria-label
- `web/src/components/shared/dashboard-shell.tsx` - Added Tooltip import; wrapped both LogOut buttons (desktop aside + mobile Sheet drawer) with Tooltip "Terminar sessão" + aria-label

## Decisions Made

- Followed the plan's recorded DSR-03 scope interpretation exactly (sidebar-footer LogOut = "ícones da sidebar colapsada"); see Recorded Design Decision section above.
- Used the broadened grep (including `Edit`, not just `Eye|Pencil|Trash2|Printer`) exactly as instructed by the plan, confirming settings/page.tsx's Edit/Trash2 buttons as the second in-scope surface the earlier narrow grep in 102-PATTERNS.md had missed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Created missing `web/.env.local` to unblock the `pnpm build` verification gate**
- **Found during:** Task 2 (build verification step)
- **Issue:** `pnpm build` failed immediately with `Error: BACKEND_API_ORIGIN is required` — `web/.env.local` did not exist in this isolated worktree checkout (per `CLAUDE.md`, `next.config.ts` throws at startup if `BACKEND_API_ORIGIN`/`NEXT_PUBLIC_API_BASE_PATH` are missing). This is a local dev-environment file, not project source, and blocked the plan's own required build gate.
- **Fix:** Created `web/.env.local` with the two values documented in `web/.env.example` (`BACKEND_API_ORIGIN=http://localhost:8080`, `NEXT_PUBLIC_API_BASE_PATH=/api/v1`). This file is `.gitignore`d (`.env*` except `.env.example`) and was never staged/committed — confirmed via `git status --short` showing no untracked `.env.local` after the build passed.
- **Files modified:** `web/.env.local` (gitignored, not committed)
- **Verification:** `pnpm build` re-run afterward exited 0, all 24 routes generated successfully.
- **Committed in:** N/A (gitignored local file, intentionally not committed)

---

**Total deviations:** 1 auto-fixed (1 blocking, environment-only, zero source-code impact)
**Impact on plan:** No scope creep — purely unblocked the plan's own mandated build-verification gate in an isolated worktree checkout that lacked the local dev env file present in the main checkout.

## Issues Encountered

- Bash's `grep` (GNU grep 3.0 via the RTK proxy hook) intermittently failed to match a literal fixed-string pattern containing `{700}` (e.g., `grep -F -q "delayDuration={700}"` returned exit 1 despite the exact substring being present and byte-verified via `xxd`), while the dedicated `Grep` tool (ripgrep-based) matched correctly on the first attempt. Worked around by using the `Grep` tool for all pattern-based verification in this plan instead of Bash `grep` with brace-containing patterns. No impact on the actual code changes — purely a verification-tooling quirk.

## Next Phase Readiness

- DSR-02 and DSR-03 both satisfied by this plan (row-action icon-only buttons carry both Tooltip and aria-label; sidebar LogOut likewise).
- `pnpm build` green — safe for the 102-04 mandatory human visual checkpoint (light+dark, hover/focus 700ms delay confirmation) to proceed.
- No blockers for 102-01/102-02 (parallel plans in this wave) — zero file overlap confirmed, and this plan's `git diff` touched only the 4 files declared in its own frontmatter.

---
*Phase: LEXCV-102-reconcilia-o-do-design-system*
*Completed: 2026-07-16*
