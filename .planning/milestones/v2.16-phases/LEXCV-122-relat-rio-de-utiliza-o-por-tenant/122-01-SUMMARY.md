---
phase: 122-relat-rio-de-utiliza-o-por-tenant
plan: 01
subsystem: ui
tags: [nextjs, react, tanstack-table, shadcn, tenant-admin, read-only-report]

# Dependency graph
requires:
  - phase: 120-consola-de-administra-o-de-tenants
    provides: "useTenantsAdmin() hook, TenantAdminSummary type, plataforma/columns.tsx's nome/plano/utilizadores/estado cell renderers, plataforma/page.tsx's page-guard and Card/search/mobile-card shape — all reused verbatim, zero modification"
  - phase: 117-limite-de-utilizadores-por-tenant
    provides: "UserRepository.countByTenantIdAndAtivoTrue as the sole active-user-count source of truth, surfaced through utilizadoresAtivos"
provides:
  - "web/src/app/(dashboard)/plataforma/relatorio/columns.tsx — relatorioColumns, a static 4-column ColumnDef array (nome/plano/utilizadores/estado), the first non-factory columns file in this codebase"
  - "web/src/app/(dashboard)/plataforma/relatorio/page.tsx — the /plataforma/relatorio route itself: PLATAFORMA_ADMIN-gated, read-only, search + mobile-cards + desktop DataTable, confirmed present in `pnpm build`'s route table"
affects: [122-03 (adds the "Ver Relatório" entry link on /plataforma), 122-04 (live UAT of this screen), 123 (ISOL-04 dedicated isolation audit names "the usage report" as one of its three audit targets)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Plain static array export for a columns.tsx file (export const relatorioColumns: ColumnDef<T>[] = [...]), instead of the factory-function shape all 6 other columns.tsx files in the codebase use — legitimate because a 100%-read-only screen has no row callbacks to thread through a factory"
    - "Composite screen assembly from 2 existing precedents: plataforma/page.tsx's guard+Card+search+mobile/desktop-split body, and processos/dashboard/page.tsx's satellite back-arrow header — recombined, not redesigned"

key-files:
  created:
    - web/src/app/(dashboard)/plataforma/relatorio/columns.tsx
    - web/src/app/(dashboard)/plataforma/relatorio/page.tsx
  modified: []

key-decisions:
  - "relatorioColumns exported as a plain array, not a factory — first of its kind in the codebase; PATTERNS.md's own 'No Analog Found' section flagged this rather than silently claiming an exact precedent"
  - "Guard order (!me.isFetched resolves before the PLATAFORMA_ADMIN role check) copied structurally from /plataforma to avoid reintroducing WR-03 (Phase 120 code review) — verified by replicating the exact index-comparison technique web/scripts/verify-consola-tenants.mjs already applies to /plataforma"
  - "h1 uses font-semibold, not the app's usual font-bold — the one disclosed, orchestrator-force-approved Dimension-4 typography exception recorded in 122-UI-SPEC.md, implemented as specified rather than 'corrected' back to font-bold"
  - "Doc-comments in both new files are written in prose and deliberately avoid literally reproducing banned code tokens (e.g. the factory-function signature, the reserved-tenant literal, aggregation method names) — precedent from three prior Phase 119/120/121 incidents of self-referential comments tripping grep-based verify gates"
  - "requirements mark-complete NOT run for UTIL-01, despite it being listed in this plan's frontmatter — the screen isn't reachable via any link yet (Plan 03's job); mirrors the Phase 120 Plan 02/03/04 precedent for PROV-02/PROV-05"
  - "Ran `pnpm install` (web/) mid-plan to sync node_modules with an already-resolved pnpm-lock.yaml entry (vitest@4.1.10, declared in package.json since commit 241f06f9 but missing from node_modules) — a pre-existing environment drift unrelated to this plan's own files, blocking `tsc --noEmit` from exiting 0. Confirmed zero change to package.json/pnpm-lock.yaml before and after, so this was a lockfile-consistent sync, not a new/unverified install"

patterns-established:
  - "First read-only-screen columns.tsx in the codebase: a plain array is legitimate once there are zero row callbacks, but every future factory-shaped columns.tsx should stay a factory unless it's equally callback-free"

requirements-completed: []

# Metrics
duration: ~20min
completed: 2026-07-30
---

# Phase 122 Plan 01: Relatório de Utilização — Read-Only Report Route Summary

**New `/plataforma/relatorio` route + static-array column defs, showing nome/plano/limite/utilizadores-ativos per tenant (including suspended ones) via the existing Phase 120 `GET /platform/tenants` endpoint — zero backend changes, zero mutations, zero new dependencies.**

## Performance

- **Duration:** ~20 min
- **Started:** ~2026-07-30T02:54:00Z
- **Completed:** 2026-07-30T03:14:27Z
- **Tasks:** 2 completed
- **Files modified:** 2 (both new)

## Accomplishments

- Built the substance of Phase 122's ROADMAP success criteria 1-3 entirely in the frontend: a new route showing, per tenant, nome/plano/limiteUtilizadores/utilizadoresAtivos, gated to `PLATAFORMA_ADMIN`, with suspended tenants kept visible and their state identified via the same `Badge` convention `/plataforma` already uses.
- `relatorioColumns` is the first plain-array `columns.tsx` export in the codebase (the other 6 — clientes/processos/pareceres/financeiro/documentos/plataforma — are all factories), a deliberate, disclosed simplification since this screen has no row actions to thread through a factory.
- Confirmed via `pnpm build`'s own route table that `/plataforma/relatorio` compiles and registers as a static route, not just that the files parse.
- Utilizadores count is visible on both desktop (DataTable column) and mobile (a new `pl-[52px]`-indented line replacing the action-icon row that `/plataforma`'s own mobile cards have and this read-only screen doesn't need) — closing the one gap PATTERNS.md flagged (`/plataforma`'s mobile cards never show this figure at all).
- Found and fixed one pre-existing, task-unrelated environment blocker (`vitest` missing from `node_modules` despite being fully resolved in `pnpm-lock.yaml`) that was preventing `tsc --noEmit` from exiting 0 for any file in the project, not just this plan's new files.

## Task Commits

Each task was committed atomically:

1. **Task 1: Criar relatorio/columns.tsx** - `f18bd049` (feat)
2. **Task 2: Criar relatorio/page.tsx** - `07f52e95` (feat)

**Plan metadata:** committed together with this SUMMARY, STATE.md, and ROADMAP.md (see final commit in this session).

_Note: Plan 122-02's own commit (`3144d6df`, a backend-only test addition) landed on the shared working tree between these two commits — expected and harmless, since both plans run independently in the same Wave 1 with zero file overlap (backend/src/test vs. web/src), confirmed by `git show --stat` on each of my own commits individually (1 file each)._

## Files Created/Modified

- `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx` (new, 119 lines) — `relatorioColumns: ColumnDef<TenantAdminSummary>[]`, a static array of 4 column defs (nome/plano/utilizadores/estado), all 4 cells copied verbatim from `plataforma/columns.tsx`; `PLANO_BADGE_VARIANT` redeclared byte-identical (it's a private, non-exported const in the source file); `TENANT_RESERVADO` imported from `../columns` rather than redeclared.
- `web/src/app/(dashboard)/plataforma/relatorio/page.tsx` (new, 192 lines) — the route itself: `RelatorioUtilizacaoPage` (guard only) + `RelatorioUtilizacaoContent` (search state, `useTenantsAdmin()`, byte-identical `tenantsFiltrados` name-substring memo, satellite header, Card with search/loading/error, mobile-cards/desktop-DataTable split).

## Decisions Made

See `key-decisions` in frontmatter above for the full list with rationale. The two most consequential: (1) keeping `relatorioColumns` a plain array instead of "fixing" it to match the other 6 factory-shaped `columns.tsx` files — the codebase's first genuinely callback-free list screen; (2) copying `/plataforma`'s exact `!me.isFetched`-before-role-check guard order rather than the single-condition `isFetched && !canX` pattern used elsewhere in the app, to avoid reintroducing the WR-03 fail-open bug for this new route.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Synced node_modules with an already-locked vitest dependency**
- **Found during:** Task 1 (`pnpm exec tsc --noEmit` verification step)
- **Issue:** `tsc --noEmit` failed with `TS2307: Cannot find module 'vitest'` / `'vitest/config'` across 4 pre-existing test files unrelated to this plan's changes. `vitest@4.1.10` was declared in `package.json` and fully resolved in `pnpm-lock.yaml` (confirmed via direct grep) but absent from `node_modules` — a stale local install, not a missing/unverified package.
- **Fix:** Ran `pnpm install` in `web/` to sync `node_modules` against the existing lockfile. This is a lockfile-consistent sync (the package was already vetted and committed to `pnpm-lock.yaml` by an earlier commit, `241f06f9`), not a new dependency installation, so the package-legitimacy concern behind the "no auto-install" exclusion in Rule 3 does not apply here.
- **Files modified:** none tracked by git — `node_modules/` is gitignored; confirmed `git status --short web/package.json web/pnpm-lock.yaml` showed no changes before or after.
- **Verification:** `pnpm exec tsc --noEmit` went from 4 errors (all in `vitest`-dependent files, zero in this plan's new files) to `TypeScript: No errors found`, confirmed twice (once after Task 1, once again after Task 2).
- **Committed in:** n/a (no trackable file changes; `node_modules` is gitignored).

---

**Total deviations:** 1 auto-fixed (1 blocking, environment-only).
**Impact on plan:** No scope creep — the fix touched zero files this plan's diff includes, and both `package.json`/`pnpm-lock.yaml` are byte-identical before and after. Necessary only to let the plan's own `tsc --noEmit` verification gate report accurately.

## Issues Encountered

None beyond the one documented deviation above (which is an environment fix, not a design or logic problem).

## User Setup Required

None - no external service configuration required. (The `node_modules` sync above is a one-time local dev-environment fix, not a persistent setup requirement — any future clean checkout following the committed `pnpm-lock.yaml` will install `vitest` correctly via a normal `pnpm install`.)

## Next Phase Readiness

- `/plataforma/relatorio` is fully built, gated, and verified (`pnpm lint`/`tsc --noEmit`/`pnpm build` all exit 0, route confirmed in the build's route table) — but **not yet reachable** from anywhere in the app, by design. Plan 03 (`122-03-PLAN.md`, wave 2, depends on this plan) adds the "Ver Relatório" link to `/plataforma`'s `CardHeader`.
- Plan 122-02 (backend regression test, running concurrently) completed independently with zero file overlap and zero conflicts — confirmed via clean interleaved commit history (`f18bd049` → `3144d6df` → `07f52e95`).
- `UTIL-01` remains intentionally open: it only closes once the report is genuinely reachable and live-verified (Plans 03/04), not from this plan's route-building step alone.
- No blockers or concerns raised by this plan.

---
*Phase: 122-relat-rio-de-utiliza-o-por-tenant*
*Completed: 2026-07-30*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/plataforma/relatorio/columns.tsx`
- FOUND: `web/src/app/(dashboard)/plataforma/relatorio/page.tsx`
- FOUND: commit `f18bd049` (Task 1)
- FOUND: commit `07f52e95` (Task 2)
