---
phase: LEXCV-103-m-dulo-dashboard
fixed_at: 2026-07-16T09:25:19Z
review_path: .planning/phases/LEXCV-103-m-dulo-dashboard/103-REVIEW.md
iteration: 2
findings_in_scope: 1
fixed: 1
skipped: 0
status: all_fixed
---

# Phase LEXCV-103: Code Review Fix Report

**Fixed at:** 2026-07-16T09:25:19Z
**Source review:** .planning/phases/LEXCV-103-m-dulo-dashboard/103-REVIEW.md
**Iteration:** 2

**Summary:**
- Findings in scope: 1 (0 Critical, 1 Warning; fix_scope = critical_warning)
- Fixed: 1
- Skipped: 0

This is the second fix pass against this phase. The re-review in the current `103-REVIEW.md` re-confirmed all four findings from iteration 1 (CR-01, WR-01, WR-02, WR-03) as correctly fixed, and surfaced one new Warning (also labeled WR-01 in the current review, since it's a fresh document) introduced by iteration 1's own WR-01 fix. That new warning is fixed in this pass. The 7 Info-level items in the current review are out of `fix_scope` (`critical_warning`) and were not attempted.

## Fixed Issues

### WR-01: Combining `processos` and `clientes` error states in `RecentProcessosCardWithClientes` hides otherwise-valid processos data when only the enrichment `clientes` query fails

**Files modified:** `web/src/app/(dashboard)/dashboard/page.tsx`
**Commit:** `4334986`
**Applied fix:** Read the current source at `RecentProcessosCardWithClientes` (lines 472-487) and confirmed it matched the review's cited code exactly: `isError={processos.isError || clientes.isError}`. Changed this to `isError={processos.isError}` so a failure of the secondary `clientes` enrichment query no longer suppresses an already-successful `processos` table render. `clientes.isError` still degrades the "Cliente" column to `"—"` per row via the existing `clienteNomeById?.get(p.cliente_id) ?? "—"` fallback (unchanged), preserving the original graceful-degradation behavior the review asked to restore. `isLoading` was left untouched (`processos.isLoading || clientes.isLoading`, from iteration 1's WR-03 fix) since that combination is correct and not part of this finding.

**Verification:** Re-read the modified section post-edit (Tier 1, passed — fix text present, surrounding code intact, `clientes` variable still used by both `clienteNomeById` and `isLoading` so no new unused-variable issue). Ran a project-scoped `tsc --noEmit -p tsconfig.json` inside the isolated fix worktree (via a temporary `node_modules` junction to the main repo's install, removed afterward) — zero errors in `dashboard/page.tsx`; the only reported errors were the 3 pre-existing `Cannot find module 'vitest'` errors in unrelated `*.test.ts` files, matching what the review's own `tsc` run had already noted as pre-existing (Tier 2, passed).

## Skipped Issues

None — the single in-scope finding (WR-01) was fixed. The 7 Info-level items (IN-01 through IN-07) were out of `fix_scope` (`critical_warning`) per the task instructions and were not attempted:
- IN-01: `isSameCalendarDay` uses browser-local timezone instead of Cabo Verde's — cosmetic, low severity.
- IN-02: `RecentProcessosCard`'s error branch uses a fixed generic string instead of `error.message`, inconsistent with its sibling components.
- IN-03, IN-05, IN-06: carried-over, unchanged tech debt already documented in prior reviews (unreachable empty branch, hardcoded KPI trend badges, minor JSX indentation).
- IN-04: carried-over, unchanged — `AtividadeRecenteCard`'s loading skeleton borrows `useDashboardKpis`'s loading state.
- IN-07: `RecentProcessosCard`'s table has no dedicated loading skeleton (pre-existing gap, not part of this pass's fixes).

## Prior Iteration Summary (Iteration 1)

For full detail see the git history of this file (commit that introduced iteration 1's report) or commits `575acbf`, `36968bf`, `d4d3be8`, `99e0e58`. Iteration 1 fixed all 4 in-scope findings from the original `103-REVIEW.md`:

- **CR-01 — fixed** (`575acbf`): Changed the top-of-page RBAC gate from `if (!permissions.isLoading && !canViewAny)` to `if (permissions.isFetched && !canViewAny)`, eliminating a false "Acesso negado" flash on first render.
- **WR-01 — fixed** (`36968bf`): Added `.isError` branches to `DashboardKpis`, `PrazosUrgentesCard`, and `RecentProcessosCard` so query errors are no longer rendered identically to "no data." (This fix introduced the new WR-01 finding fixed in this iteration-2 pass.)
- **WR-02 — fixed** (`d4d3be8`): Sorted `urgentEventos` ascending by `dataInicio` before taking the top two, and replaced the hardcoded "HOJE" badge with a real `isSameCalendarDay` check plus a `pt-CV` short date fallback.
- **WR-03 — fixed** (`99e0e58`): Combined `processos.isLoading || clientes.isLoading` in `RecentProcessosCardWithClientes` so the table no longer shows false "—" cliente cells while `clientes` is still in flight after `processos` resolved.

All 4 iteration-1 fixes were re-verified as correct by the current (iteration-2 source) `103-REVIEW.md` re-review before this fix pass began.

---

_Fixed: 2026-07-16T09:25:19Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 2_
