---
phase: 120-frontend-consola-de-administra-o-de-tenants
plan: 03
subsystem: ui
tags: [nextjs, react, tanstack-query, typescript, rbac, multi-tenant]

# Dependency graph
requires:
  - phase: 120-02
    provides: "GET/PUT/PATCH /api/v1/platform/tenants{, /{id}, /{id}/ativo} endpoints and the TenantAdminSummaryResponse/TenantUpdateRequest DTO field names this plan's frontend types mirror byte-for-byte"
provides:
  - "web/src/types/platform-admin.ts: TenantPlano/TenantAdminSummary/TenantUpdateRequest/TenantAtivoRequest, reusing SetupInitializeRequest/SetupInitializeResponse from @/types/setup as-is for tenant creation (not redeclared)"
  - "Role union in web/src/types/auth.ts extended with PLATAFORMA_ADMIN"
  - "web/src/hooks/use-platform-admin.ts: useTenantsAdmin (list) + useCreateTenant/useUpdateTenant/useSetTenantAtivo (3 mutations), all invalidating one shared TENANTS_LIST_KEY instead of reloading the page"
  - "Role-gated \"Plataforma\" nav item in DashboardShell (both desktop aside and mobile Sheet SidebarNav call sites), visible only to PLATAFORMA_ADMIN, with sidebar-nav.tsx itself left untouched"
affects: [120-04, 120-05, 120-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Single shared TENANTS_LIST_KEY query-key constant referenced by 1 query + 3 mutations (use-clientes.ts's invalidateQueries idiom), instead of UserManagementTab's legacy full-page-reload pattern"
    - "Nav-item visibility gated by role (me.data.roles.includes(...)) via a derived array memoized and spliced onto NAV, since PLATAFORMA_ADMIN carries no scoped permissions for SidebarNav's existing requiredPermission/hasPermission filter to key off"

key-files:
  created:
    - web/src/types/platform-admin.ts
    - web/src/hooks/use-platform-admin.ts
  modified:
    - web/src/types/auth.ts
    - web/src/components/shared/dashboard-shell.tsx

key-decisions:
  - "useCreateTenant's mutation response typed as { id: string; nome: string } only, not a union with SetupInitializeResponse -- matches the plan's own literal documented HTTP contract for POST /platform/tenants (201, {id, nome}); SetupInitializeResponse is a different, unrelated shape ({initialized, message}) used only by the /setup wizard's status flow"
  - "2 top-of-file comments reworded to avoid literally spelling out this plan's own grep-based verify-gate strings (a full-page-reload mention in use-platform-admin.ts's top comment collided with its own window.location.reload/mock-db count=0 gate) -- same precedent as 119-04/120-01/120-02"
  - "Did not run requirements mark-complete for PROV-02/PROV-05 despite both being listed in this plan's own frontmatter requirements field -- REQUIREMENTS.md's traceability table already documents both as needing the actual UI screens (Plans 04/05/06), and commit cd45fcf9 (made ~23 min before this plan started) had just corrected an identical premature auto-check of PROV-05 caused by Plan 02's own mechanical requirements-mark-complete step; re-running it here with all 4 IDs would reintroduce that exact regression. PROV-03/PROV-04 (already [x] from Plan 02) were left untouched"

requirements-completed: []

# Metrics
duration: 22min
completed: 2026-07-29
---

# Phase 120 Plan 03: Frontend Data Layer for the Tenant Admin Console Summary

**4 platform-admin domain types mirroring Plan 02's DTOs, 4 TanStack Query hooks against `/platform/tenants`, and a role-gated "Plataforma" sidebar nav item -- all built ahead of the `/plataforma` screen itself (Plans 04/05)**

## Performance

- **Duration:** 22 min
- **Started:** 2026-07-29T12:42:19Z
- **Completed:** 2026-07-29T13:05:00Z
- **Tasks:** 3 completed
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments

- `web/src/types/platform-admin.ts` created with `TenantPlano`/`TenantAdminSummary`/`TenantUpdateRequest`/`TenantAtivoRequest`, field-for-field identical to Plan 02's `TenantAdminSummaryResponse`/`TenantUpdateRequest` backend DTOs; `web/src/types/auth.ts`'s `Role` union extended with `PLATAFORMA_ADMIN` so `me.data.roles.includes("PLATAFORMA_ADMIN")` type-checks
- `web/src/hooks/use-platform-admin.ts` created with 1 query (`useTenantsAdmin`) + 3 mutations (`useCreateTenant`, `useUpdateTenant`, `useSetTenantAtivo`) against the 4 Plan 02 endpoints, all 3 mutations invalidating a single shared `TENANTS_LIST_KEY` constant on success -- zero `window.location.reload()`, zero import from `@/server/mock-db`, zero toast calls from inside the hook
- `DashboardShell` now derives a `PLATAFORMA_ADMIN`-only "Plataforma" nav item (`Building2` icon, no `requiredPermission`) via `React.useMemo`, applied identically to both `SidebarNav` call sites (desktop `<aside>` and mobile `<Sheet>`) -- `sidebar-nav.tsx` itself is provably untouched (`git diff --quiet` confirmed)
- `pnpm build` (24 routes, the authoritative gate per this plan's own `<verification>` section) and `pnpm lint` (0 errors) both pass cleanly after all 3 tasks; zero new dependencies (`package.json`/`pnpm-lock.yaml` diffs empty)

## Task Commits

Each task was committed atomically:

1. **Task 1: Tipos do dominio de plataforma e alargamento da uniao Role** - `db417ab` (feat)
2. **Task 2: Hooks TanStack Query para os 4 endpoints de /platform/tenants** - `bdbe378` (feat)
3. **Task 3: Item de navegacao Plataforma gated por papel nos 2 call sites** - `f4619de` (feat)

**Plan metadata:** (recorded in the next commit, after this SUMMARY)

## Files Created/Modified

- `web/src/types/platform-admin.ts` - `TenantPlano` (3-literal union), `TenantAdminSummary` (6 fields), `TenantUpdateRequest`, `TenantAtivoRequest`; top comment documents `limiteUtilizadores: null` = "sem limite" and that `@/types/setup`'s create-tenant types are reused, not redeclared
- `web/src/types/auth.ts` - `Role` union extended to 5 members (`+ "PLATAFORMA_ADMIN"`), 1-line comment added, `MeResponse` untouched
- `web/src/hooks/use-platform-admin.ts` - `TENANTS_LIST_KEY` shared query-key constant; `useTenantsAdmin` (list), `useCreateTenant`, `useUpdateTenant`, `useSetTenantAtivo` (3 mutations, all invalidate-on-success)
- `web/src/components/shared/dashboard-shell.tsx` - module-level `platformNavItem` constant, `isPlatformAdmin` derivation + memoized `navItems`, both `SidebarNav` call sites switched from `nav={NAV}` to `nav={navItems}`

## Decisions Made

- `useCreateTenant`'s response type resolved to `{ id: string; nome: string }` only (not a union with `SetupInitializeResponse`, despite the plan's inline code sample showing one) -- this matches the plan's own `<interfaces>` section literal HTTP contract (`POST /platform/tenants` -> `201`, `{ id, nome }`) and the plan's own clarifying sentence immediately following the code sample ("Tipar a resposta como `{ id: string; nome: string }`"). `SetupInitializeResponse` (`{ initialized, message }`) is an unrelated shape used only by `/setup`'s status flow and would have been a genuine type mismatch here.
- Reworded the top-of-file comment in `use-platform-admin.ts` explaining why this hook never reloads the page, to describe the behavior ("recarregamento total do browser") instead of literally spelling out the disallowed API call -- the literal text would have tripped this same task's own `grep -c 'window.location.reload\|@/server/mock-db'` = 0 acceptance gate. Same class of self-referential collision documented in `120-02-SUMMARY.md`.
- Skipped `requirements mark-complete` for `PROV-02`/`PROV-05` (see Deviations below) -- left `REQUIREMENTS.md` exactly as Plan 02 left it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Own top-of-file comment collided with this same task's own automated verify gate**
- **Found during:** Task 2 (`use-platform-admin.ts` implementation)
- **Issue:** The top-of-file comment explaining why mutations invalidate the query cache instead of reloading the page originally spelled out the literal text `window.location.reload()`. Task 2's own automated `<verify>`/acceptance criteria require `grep -c 'window.location.reload\|@/server/mock-db'` against this exact file to equal `0` -- left as written, the explanatory comment would have failed this plan's own gate.
- **Fix:** Reworded the sentence to convey the identical property (this hook never triggers a full browser reload, unlike `UserManagementTab`'s legacy pattern) without using the literal `window.location.reload` string. The documented behavior is unchanged; only the wording avoiding the literal API-call text changed.
- **Files modified:** `web/src/hooks/use-platform-admin.ts`
- **Verification:** `grep -c 'window.location.reload\|@/server/mock-db'` returns `0` on the file; all other acceptance-criteria greps (4 exported hooks, 3 `invalidateQueries`, 1 `TENANTS_LIST_KEY` literal declaration, >=4 `apiFetch` calls, 0 `toast` calls) unaffected.
- **Committed in:** `bdbe378` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (Rule 1, a pre-emptive comment-wording collision with this plan's own literal-text verification gate -- the same class of issue documented in `119-04-SUMMARY.md`/`120-01-SUMMARY.md`/`120-02-SUMMARY.md`, this time caught before commit rather than after).
**Impact on plan:** Cosmetic only -- no functional/behavioral code was affected, no scope creep.

## Issues Encountered

- **Pre-existing, out-of-scope:** raw `cd web && npx tsc --noEmit -p tsconfig.json` reports 3 errors (`Cannot find module 'vitest'`) in 3 "durable spec" files committed across Phases 74/83/97, deliberately without `vitest` installed (explicit precedent: `83-02-SUMMARY.md` — "repo continua sem test runner"). None of the 3 files were touched by this plan; the count was identical (3) before Task 1, and after every subsequent task. `pnpm build` -- the authoritative gate per this plan's own `<verification>` section -- runs Next.js's own TypeScript check and passed cleanly (exit 0, 24 routes) both before this plan started and after all 3 tasks, since Next's build-time check does not walk these orphaned spec files the way a raw project-wide `tsc` does. Logged to `deferred-items.md`; not fixed (installing `vitest` would reverse a standing, explicit project decision and is out of scope for a data-layer plan).
- **Unrelated merge observed mid-plan:** commit `e4a2788` (`Merge branch 'claude/fervent-mestorf-d8e971'`, authored by the project owner) landed between this plan's Task 2 and Task 3 commits, touching only `web/src/app/(dashboard)/settings/page.tsx`. No overlap with any file this plan modifies; no conflict occurred; all 3 of this plan's commits applied cleanly.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 04 (column defs + create-tenant panel) and Plan 05 (compose `/plataforma`, close the phase's executable gate) can now import `TenantAdminSummary`/`TenantUpdateRequest`/`TenantAtivoRequest` from `@/types/platform-admin` and call `useTenantsAdmin`/`useCreateTenant`/`useUpdateTenant`/`useSetTenantAtivo` from `@/hooks/use-platform-admin` directly -- the data contract is fixed and type-checked against Plan 02's real backend DTOs.
- The "Plataforma" nav item is live for any `PLATAFORMA_ADMIN` user today, but currently points to a route that does not exist yet (`/plataforma`, shipped in Plan 05) -- clicking it before Plan 05 lands will 404. This is expected sequencing for this phase (data layer -> screen pieces -> composed screen), not a regression.
- `REQUIREMENTS.md`'s `PROV-02`/`PROV-05` checkboxes remain `[ ]` Pending exactly as Plan 02 left them (see Decisions Made) -- Plans 04-06 are still expected to close them.

---
*Phase: 120-frontend-consola-de-administra-o-de-tenants*
*Completed: 2026-07-29*

## Self-Check: PASSED

All 4 claimed created/modified source files confirmed present on disk (`web/src/types/platform-admin.ts`, `web/src/hooks/use-platform-admin.ts`, `web/src/types/auth.ts`, `web/src/components/shared/dashboard-shell.tsx`), plus this SUMMARY.md and `deferred-items.md`. All 3 claimed commit hashes (`db417ab`, `bdbe378`, `f4619de`) confirmed present in `git log --oneline --all`. `pnpm build` (24 routes) and `pnpm lint` (0 errors) re-confirmed clean; `git diff --quiet -- web/src/components/shared/sidebar-nav.tsx` re-confirmed exit 0 (untouched); `git diff --stat -- web/package.json` re-confirmed empty (zero new dependencies).
