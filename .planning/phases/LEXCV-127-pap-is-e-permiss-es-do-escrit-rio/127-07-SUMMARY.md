---
phase: 127-pap-is-e-permiss-es-do-escrit-rio
plan: 07
subsystem: ui
tags: [react, nextjs, tanstack-query, react-hook-form, zod, rbac, office-roles, accessibility]

# Dependency graph
requires:
  - phase: 127-pap-is-e-permiss-es-do-escrit-rio (plan 06)
    provides: office-rbac types, criarPapelSchema/renomearPapelSchema, id-keyed
      merge-local-papeis.ts, and the five office-RBAC hooks in use-admin.ts
      (useOfficeRbac/useSaveOfficeRbac/useCreateOfficeRole/useRenameOfficeRole/
      useDeleteOfficeRole)
provides:
  - "web/src/app/(dashboard)/settings/criar-papel-panel.tsx: presentational
    create-role panel (react-hook-form + zodResolver, no mutation of its own)"
  - "web/src/app/(dashboard)/settings/papel-acoes-menu.tsx: kebab DropdownMenu
    with Renomear + either a destructive Apagar Papel item or a static,
    always-visible refusal row (protected role / assigned-users count)"
  - "web/src/app/(dashboard)/settings/page.tsx: RbacTab rewritten into the
    office's own editable permission x role matrix (create/rename/delete/save),
    isPlatformAdmin apparatus fully removed; UserManagementTab retyped off
    MockUser/MockRole/MockPermission onto AdminUser/string[]"
  - "web/src/hooks/use-admin.ts: deprecated useAdminRbac/RbacResponse shim
    removed now that nothing imports it"
affects: [127-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Server-computed protegido/podeApagar flags drive UI locks, never a
      client-side name comparison -- role names are editable this phase"
    - "Floor-lock computed against the fetched (persisted) permission set on
      the role object itself, never against local edit state"

key-files:
  created:
    - web/src/app/(dashboard)/settings/criar-papel-panel.tsx
    - web/src/app/(dashboard)/settings/papel-acoes-menu.tsx
  modified:
    - web/src/app/(dashboard)/settings/page.tsx
    - web/src/hooks/use-admin.ts

key-decisions:
  - "UserManagementTab's editingUser/selectedRoles/selectedPermissions were
    retyped fully off MockUser/MockRole/MockPermission onto AdminUser/
    string[] rather than kept as MockUser/MockRole per the plan's literal
    wording -- AdminUser has no password field, so keeping editingUser typed
    as Partial<MockUser> could not typecheck against the real AdminUser
    values useAdminUsers() returns. This closes this file's @/server/mock-db
    import entirely; it does not close the LAST mock-db import in the
    codebase (33 files under web/src/app/_api-backup/ still import it, per
    CLAUDE.md's documented legacy/ignore status), only the last one in this
    file and the last one outside _api-backup."
  - "Floor-lock reads papel.permissoes (the fetched/persisted set already on
    each OfficePapel from the query) directly per checkbox, rather than
    precomputing a separate Set per role -- simpler and still O(module size)
    per cell, matching the UI-SPEC's stated intent without adding a new memo."
  - "papeis/permissoes derived arrays wrapped in their own useMemo (keyed on
    data) rather than left as plain `data?.x ?? []` -- ESLint's
    react-hooks/exhaustive-deps flagged the unmemoized version as changing
    every render and destabilizing the papeisAlterados/modulos memos
    downstream; fixed to keep lint at the pre-existing 0-error baseline."

requirements-completed: [PAPEL-01, PAPEL-02, PAPEL-03, PAPEL-04, PAPEL-05, PAPEL-08, PAPEL-09]

# Metrics
duration: 14min
completed: 2026-09-22
---

# Phase 127 Plan 07: Office RBAC Console (RbacTab Rewrite) Summary

**Rewrote `RbacTab` from a read-only four-role matrix gated by `isPlatformAdmin`/literal-`"ADMIN"` into the office's own editable permission x role console — dynamic role columns, create/rename/delete, `protegido`-based floor-lock — per the approved 127-UI-SPEC.md.**

## Performance

- **Duration:** ~14 min
- **Started:** 2026-09-22T09:39Z (immediately after 127-06)
- **Completed:** 2026-09-22T09:53Z
- **Tasks:** 2/2 completed
- **Files modified:** 4 (2 created, 2 modified)

## Accomplishments
- The Controlo de Acesso tab now renders however many roles the office actually
  has, however they're named, as matrix columns — the four-role hardcode and
  the entire `isPlatformAdmin` branch (badge, tooltip, disabled matrix,
  duplicated warning paragraph) are gone.
- An administrator can create a role, rename any role via a focused `Dialog`,
  delete an unassigned unprotected role via `AlertDialog`, and save permission
  changes, all from this one tab — none of that was possible before this plan.
- The protected administrator role is now locked by the server-computed
  `protegido` flag (floor-lock: permissions it already holds are locked
  checked, permissions it doesn't hold stay grantable) instead of a literal
  `role === "ADMIN"` string comparison that would have silently stopped
  protecting the role the moment an office renamed it.
- Unsaved checkbox edits survive creating, renaming and deleting *another*
  role, via `mesclarEstadoLocal`'s id-keyed reconciliation (plan 06) wired
  into the render-time reconciliation pattern already proven in
  `plataforma/moldes/page.tsx`.
- Closed the five residual `tsc` errors 127-06 intentionally left in place
  (`page.tsx` UserManagementTab typed against the closed `MockUser`/
  `MockRole`/`MockPermission` unions colliding with the real `AdminUser`
  shape) and removed the deprecated `useAdminRbac`/`RbacResponse` shim from
  `use-admin.ts` now that `RbacTab` no longer needs it.

## Task Commits

Each task was committed atomically:

1. **Task 1: Presentational create panel and role-actions menu** - `7d52d5ca` (feat)
2. **Task 2: Rewrite RbacTab as the office role console** - `4511acf3` (feat)

**Plan metadata:** (this commit) `docs(127-07): complete office RBAC console plan`

## Files Created/Modified
- `web/src/app/(dashboard)/settings/criar-papel-panel.tsx` - presentational
  create-role panel: `react-hook-form` + `zodResolver(criarPapelSchema)`,
  module-grouped permission checklist reused from `CriarMoldePanel`, owns no
  mutation/query — parent (`RbacTab`) owns the mutation, toast and panel close
- `web/src/app/(dashboard)/settings/papel-acoes-menu.tsx` - kebab
  `DropdownMenu`: Renomear item, separator, then either a destructive Apagar
  Papel item (`podeApagar`) or a static always-visible `<div>` explaining why
  not (protected role vs. assigned-user count) — never a disabled item with a
  tooltip, per UI-SPEC §7's accessibility reasoning
- `web/src/app/(dashboard)/settings/page.tsx` - `RbacTab` rewritten in place
  (declaration kept as `function RbacTab() {` per the plan's explicit
  instruction, for plan 08's structural gate extractor); `UserManagementTab`
  retyped off `MockUser`/`MockRole`/`MockPermission` onto `AdminUser`/
  `string[]`; its role picker and `systemPermissions` source switched from
  `useAdminRbac().systemPermissions` to `useOfficeRbac().permissoes`
  (unchanged hardcoded 4-role grid otherwise — that dynamic picker is plan
  08's scope, per PAPEL-06)
- `web/src/hooks/use-admin.ts` - removed the deprecated `useAdminRbac`/
  `RbacResponse` shim (127-06 Deviation 1) now that no file imports it

## Decisions Made
See `key-decisions` in frontmatter: (1) `UserManagementTab` retyped fully off
Mock* types onto `AdminUser`/`string[]` rather than partially kept, because
`AdminUser` genuinely lacks the `password` field `MockUser` requires; (2)
floor-lock reads `papel.permissoes` directly per cell instead of a
precomputed Set; (3) `papeis`/`permissoes` wrapped in their own `useMemo` to
satisfy `react-hooks/exhaustive-deps` on the downstream memos.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Retyped `UserManagementTab` fully off `MockUser`/`MockRole`/`MockPermission` instead of partially keeping `MockUser`/`MockRole`**
- **Found during:** Task 2, `pnpm exec tsc --noEmit`
- **Issue:** The plan's action text says "`UserManagementTab` still uses
  `MockUser`/`MockRole` until plan 08; keep only what that tab needs." But
  127-06's SUMMARY ("Next Phase Readiness") explicitly names the exact five
  residual `tsc` errors this plan must close, and states they require
  retyping `editingUser`/`selectedRoles`/`selectedPermissions` away from all
  three Mock types — including `MockUser`. Keeping `editingUser: Partial<MockUser>`
  cannot typecheck against the real `AdminUser` objects `useAdminUsers()`
  returns (`AdminUser` has no `password` field, which `MockUser` requires),
  so satisfying both the plan's literal wording and a clean `tsc`/`build`
  simultaneously was not possible.
- **Fix:** `editingUser` retyped `Partial<AdminUser>`, `selectedRoles`/
  `selectedPermissions` retyped `string[]`, `handleEditClick`/`toggleRole`/
  `togglePermission` signatures updated to match. The hardcoded 4-role button
  grid and its literal string values are unchanged — only the state's type
  changed, not its behavior. `MockPermission` was already correctly identified
  by the plan as removable; this extends the same fix to `MockUser`/`MockRole`
  for internal consistency (mixing a closed Mock union with an open `string[]`
  in the same component would have been a worse compromise).
- **Files modified:** `web/src/app/(dashboard)/settings/page.tsx`
- **Verification:** `pnpm exec tsc --noEmit` clean (0 errors, down from 5);
  `pnpm build` succeeds; `pnpm lint` stays at the pre-existing 0-error/20-warning
  baseline.
- **Committed in:** `4511acf3` (Task 2 commit)

**2. [Rule 1 - Bug] Wrapped `papeis`/`permissoes` derived arrays in their own `useMemo`**
- **Found during:** Task 2, `pnpm lint`
- **Issue:** `const papeis = data?.papeis ?? []` (and the `permissoes`
  equivalent) creates a new array reference on every render whenever `data`
  is null, which ESLint's `react-hooks/exhaustive-deps` flagged as
  destabilizing the `modulos` and `papeisAlterados` `useMemo`s that depend on
  them — two new warnings not present in the pre-existing lint baseline.
- **Fix:** Wrapped both in `React.useMemo(() => data?.x ?? [], [data])`.
- **Files modified:** `web/src/app/(dashboard)/settings/page.tsx`
- **Verification:** `pnpm lint` returned to the exact pre-existing 20-warning/
  0-error baseline (confirmed diff-free against Task 1's post-commit lint run).
- **Committed in:** `4511acf3` (Task 2 commit)

---

**Total deviations:** 2 auto-fixed (both Rule 1 — bug fixes required to reach
a green build/lint, not scope creep).
**Impact on plan:** No functional scope creep. Deviation 1 is a necessary
correction of an internally-inconsistent plan instruction (see 127-06's own
SUMMARY for the source of the conflict); deviation 2 is a routine lint fix.

## Issues Encountered
- `web/scripts/verify-bloqueio-rbac.mjs` (`pnpm run verify:bloqueio-rbac`)
  now fails 11 of its 12 assertions (only `A09-hasrbacmanage-inalterado`
  passes), exactly as the plan anticipated. That script asserts the OLD
  read-only, `isPlatformAdmin`-gated behavior this plan replaces (presence of
  `useMe()` inside the block, the `"Gerido pela Plataforma"` badge/tooltip,
  the literal `if (role === "ADMIN") return;` guard, `apiFetch("/admin/rbac"`
  called directly, `useAdminRbac()`). Its `extractRbacTabBlock` helper (which
  locates the tab by the literal `"function RbacTab()"` marker up to the next
  `"\nfunction "`) still works correctly against the rewritten file — it did
  not throw, confirming the declaration form `function RbacTab() {` was kept
  exactly as instructed, which is what plan 08's structural gate depends on.
  Per the plan's own verification section, this gate is not edited here;
  plan 08 rewrites it.
- No other verify gate regressed: `verify:juizo-origem`,
  `verify:limite-utilizadores`, `verify:consola-tenants`,
  `verify:relatorio-utilizacao`, and `verify:consola-moldes` all pass in full
  (5/5 assertions each where applicable).
- `pnpm exec tsc --noEmit` is clean (0 errors) — confirmed both immediately
  after Task 2's rewrite and again after the lint-driven `useMemo` fix.
- `pnpm build` succeeds (Turbopack compile + TypeScript + static generation
  for all 33 routes, including `/settings`).
- `pnpm test` is green: 5 test files, 30 tests (unchanged from 127-06 — this
  plan added no new test files, per its own scope).
- `git diff --stat web/package.json web/pnpm-lock.yaml` is empty across both
  tasks — no new dependency, no new shadcn component, confirming the
  UI-SPEC's Registry Safety gate and the threat register's `T-127-SC` mitigation.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 08 can now rewrite `web/scripts/verify-bloqueio-rbac.mjs` against the
  new office-console shape (it currently fails 11/12 assertions by design,
  documented above) and can extend its own structural gate using the
  `"function RbacTab()"` → next `"\nfunction "` extraction pattern, confirmed
  still working against this rewrite.
- Plan 08's dynamic role picker (PAPEL-06) can consume `useOfficeRbac()` for
  the available-roles list and `AdminUser.tenant_role_ids`/
  `AdminUserSavePayload.tenantRoleIds` (already typed and wired into
  `UserManagementTab`'s state as of this plan) — `UserManagementTab`'s role
  picker itself is still the hardcoded 4-button grid; only its underlying
  state types changed in this plan, not its UI, per this plan's explicit
  scope boundary.
- No stubs, no placeholder data, no mock-db import left in
  `settings/page.tsx` (`grep -c "@/server/mock-db"` → 0 in this file); the
  33 remaining project-wide `@/server/mock-db` imports are all under
  `web/src/app/_api-backup/`, the pre-backend mock API routes CLAUDE.md
  documents as legacy/ignore-unless-migrating.

---
*Phase: 127-pap-is-e-permiss-es-do-escrit-rio*
*Completed: 2026-09-22*
