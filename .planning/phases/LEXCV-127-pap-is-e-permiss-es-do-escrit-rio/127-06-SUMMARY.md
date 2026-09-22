---
phase: 127-pap-is-e-permiss-es-do-escrit-rio
plan: 06
subsystem: ui
tags: [typescript, zod, tanstack-query, rbac, office-roles]

# Dependency graph
requires:
  - phase: 127-pap-is-e-permiss-es-do-escrit-rio (plans 02-05)
    provides: OfficeRbacResponse/OfficeRbacUpdateRequest/PapelCreateRequest/PapelRenameRequest
      DTOs, /api/v1/admin/rbac and /api/v1/admin/rbac/roles endpoints, tenantRoleIds-based
      user create/update
provides:
  - "web/src/types/office-rbac.ts: OfficeRbac/OfficePapel/OfficePermissao + the three request
    types, mirroring the backend DTOs field-for-field with UUID-string role ids"
  - "web/src/types/admin-users.ts: AdminUser/AdminUserSavePayload mirroring UserResponse"
  - "web/src/schemas/papeis-escritorio.ts: criarPapelSchema/renomearPapelSchema, no uppercase
    transform, reserved-name refine matching the backend's case-insensitive guard"
  - "web/src/app/(dashboard)/settings/merge-local-papeis.ts: id-keyed unsaved-edit merge
    generalized from Phase 125's moldes merge to three mutations (create/rename/delete), with
    a unit test proving renaming one role never loses another role's unsaved edits"
  - "web/src/hooks/use-admin.ts: useOfficeRbac/useSaveOfficeRbac/useCreateOfficeRole/
    useRenameOfficeRole/useDeleteOfficeRole, all invalidate-only (no setQueryData), no mock-db
    import; useAdminUsers/useAdminSaveUser retyped against AdminUser/AdminUserSavePayload"
affects: [127-07, 127-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "id-keyed unsaved-edit merge (Record<string-uuid, Set<string>>), generalized from Phase
      125's moldeId-keyed version to survive create/rename/delete instead of create-only"
    - "TanStack mutation hooks that only ever invalidateQueries, never setQueryData, matching
      web/src/hooks/use-platform-moldes.ts conventions"

key-files:
  created:
    - web/src/types/office-rbac.ts
    - web/src/types/admin-users.ts
    - web/src/schemas/papeis-escritorio.ts
    - web/src/app/(dashboard)/settings/merge-local-papeis.ts
    - web/src/app/(dashboard)/settings/merge-local-papeis.test.ts
  modified:
    - web/src/hooks/use-admin.ts

key-decisions:
  - "Kept a deprecated, structurally-typed useAdminRbac/RbacResponse shim in use-admin.ts
    (queryKey [\"admin\",\"rbac\",\"legacy\"]) solely so settings/page.tsx's still-unrewritten
    RbacTab import resolves at build time; plan 07 removes it when it rewrites that screen"
  - "office-rbac.ts and platform-moldes.ts are deliberately NOT merged despite structural
    overlap (both have a permissao/papel-list shape) because their id types differ (UUID
    string vs global integer Role id) and merging would let one substitute for the other"

requirements-completed: [PAPEL-01, PAPEL-02, PAPEL-03, PAPEL-04, PAPEL-05, PAPEL-06]

# Metrics
duration: 12min
completed: 2026-09-22
---

# Phase 127 Plan 06: Frontend Contract Layer for Office RBAC Summary

**Typed office-RBAC contract (UUID-string role ids), Zod validation schema, an id-keyed
unsaved-edit merge generalized to create/rename/delete, and a `use-admin.ts` rewrite with five
new TanStack RBAC hooks — no mock-db import, no `setQueryData`.**

## Performance

- **Duration:** 12 min
- **Started:** 2026-09-22T09:28Z (immediately after 127-05)
- **Completed:** 2026-09-22T09:40Z
- **Tasks:** 2/2 completed
- **Files modified:** 6 (5 created, 1 modified)

## Accomplishments
- `web/src/types/office-rbac.ts` and `admin-users.ts` give the component work in plan 07 a
  compiler-checked contract instead of reading backend Java, verified line-by-line against the
  shipped `OfficeRbacResponse`/`OfficeRbacUpdateRequest`/`PapelCreateRequest`/
  `PapelRenameRequest`/`UserResponse` DTOs and the real `OfficeRolesController` route mapping
  (`/api/v1/admin/rbac/roles`, not the stale `/rbac/papeis` mentioned in some plan-02
  doc-comments).
- `merge-local-papeis.ts` proves, with a dedicated unit test, that renaming a role never
  discards a different role's unsaved permission edits — the one new failure mode this screen
  introduces relative to Phase 125's moldes screen, which had no rename.
- `use-admin.ts` no longer imports the superseded mock module; the office-RBAC mutation surface
  (create/rename/delete role, save the permission matrix) is now a set of TanStack hooks that
  invalidate the query cache, closing the pre-existing inconsistency where `useAdminSaveRbac`
  existed unused while `RbacTab` called `apiFetch` directly.

## Task Commits

Each task was committed atomically:

1. **Task 1: Types, Zod schema, and the id-keyed merge with its unit test** - `c4e3e577` (feat)
2. **Task 2: Rewrite use-admin.ts around the office RBAC endpoints** - `6f00a102` (feat)

**Plan metadata:** (this commit) `docs(127-06): complete frontend contract layer plan`

## Files Created/Modified
- `web/src/types/office-rbac.ts` - `OfficePermissao`/`OfficePapel`/`OfficeRbac` +
  `OfficeRbacUpdateRequest`/`PapelCreateRequest`/`PapelRenameRequest`, UUID-string role ids
- `web/src/types/admin-users.ts` - `AdminUser`/`AdminUserSavePayload`, distinguishing display
  `roles` from assignment-key `tenant_role_ids`
- `web/src/schemas/papeis-escritorio.ts` - `criarPapelSchema`/`renomearPapelSchema`, reserved
  platform name guard, no uppercase transform
- `web/src/app/(dashboard)/settings/merge-local-papeis.ts` - id-keyed
  `construirEstadoLocal`/`mesclarEstadoLocal` generalized to create/rename/delete
- `web/src/app/(dashboard)/settings/merge-local-papeis.test.ts` - 5 vitest cases, including the
  rename-preserves-a-different-role's-edits case
- `web/src/hooks/use-admin.ts` - rewritten: `useAdminUsers`/`useAdminSaveUser`/
  `useAdminDeleteUser` retyped, five new office-RBAC hooks added, deprecated `useAdminRbac`
  shim retained for build compatibility (see Deviations)

## Decisions Made
- The merge helper is a fresh file (`merge-local-papeis.ts`) rather than a literal import of
  `merge-local-permissoes.ts`, per UI-SPEC §6's explicit instruction ("reuse by pattern, not by
  literal import") — the two are keyed on structurally different id types (UUID string vs.
  global integer `Role.id`) and merging them would let one substitute for the other by accident.
- `office-rbac.ts`'s header comment states outright that `protegido` must never be re-derived
  from `nome` client-side, mirroring the backend's own doc-comment on `OfficeRbacResponse`,
  because renaming a role is new in this phase and a name-based re-derivation would silently
  break the moment an office renames its admin role.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Kept a deprecated `useAdminRbac`/`RbacResponse` shim in `use-admin.ts`**
- **Found during:** Task 2, `pnpm -C web build`
- **Issue:** The plan's task 2 action replaces `RbacResponse`/`useAdminRbac`/`useAdminSaveRbac`
  outright. Doing so unmodified breaks the Turbopack build with a hard module-resolution error
  (`Export useAdminRbac doesn't exist in target module`) from `settings/page.tsx`, which still
  imports it for the pre-127 `RbacTab` (that screen is rewritten in plan 07, not this one). This
  is a harness/bundler-level blocker, not a legitimate typecheck signal — it stops the build
  before TypeScript ever gets to evaluate the file's actual content.
- **Fix:** Added a `@deprecated` `useAdminRbac()`/`RbacResponse` shim, typed structurally (no
  import from the superseded mock module, honoring the plan's invariant), under a distinct
  query key (`["admin","rbac","legacy"]`) so it never collides with the real `OFFICE_RBAC_KEY`
  cache entry. This is precisely the escape hatch the plan's own acceptance criteria names:
  *"If `settings/page.tsx` fails to build because it still imports the removed `useAdminRbac`,
  keep a thin deprecated re-export ONLY until plan 07 lands, and record that in the SUMMARY."*
- **Files modified:** `web/src/hooks/use-admin.ts`
- **Verification:** `pnpm -C web build`'s Turbopack bundling stage now succeeds (moves past
  module resolution into TypeScript type-checking — see Issues Encountered for the residual,
  plan-07-scoped type errors that stage still surfaces).
- **Committed in:** `6f00a102` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking, explicitly pre-authorized by the plan's own
acceptance criteria).
**Impact on plan:** No scope creep — the shim is deprecated, documented, and removed by plan 07.

## Issues Encountered
- `pnpm -C web build` does NOT fully pass at this stage, exactly as the plan anticipated ("Note
  `pnpm build` may fail at this stage if `settings/page.tsx` still consumes the old hook
  shape"). With the deprecated shim in place, Turbopack bundling succeeds, but the subsequent
  TypeScript type-check step fails with 5 errors, all in `settings/page.tsx`, all stemming from
  that file's local state still being typed against the mock module's closed `MockUser`/
  `MockRole`/`MockPermission` unions colliding with the now-correctly-typed `AdminUser`
  (`roles`/`permissions`: `string[]`) returned by the retyped `useAdminUsers`:
  - `page.tsx:527` — `AdminUser` not assignable to `MockUser` (missing `password` field)
  - `page.tsx:699,703,955,973` — `string` not assignable to `MockPermission`
  None of these five errors originate in any file this plan modified or created; `pnpm exec tsc
  --noEmit` confirms they are the only errors in the whole project, and specifically that the
  three Pareceres pages and `clientes/[id]/page.tsx` (all direct consumers of the retyped
  `useAdminUsers`) typecheck cleanly. This is the expected, plan-acknowledged boundary between
  this plan (contract layer) and plan 07 (the screen rewrite that retypes `settings/page.tsx`'s
  local state and removes both the `MockUser`-shaped state and the deprecated `useAdminRbac`
  shim together).
- `pnpm -C web lint` is clean (0 errors) both before and after Task 2 — the 19 warnings present
  are pre-existing and unrelated to any file this plan touched (React Compiler
  incompatible-library notices, `<img>` LCP warnings elsewhere in the app).
- `pnpm -C web test` is green: 5 test files, 30 tests (25 pre-existing + 5 new
  `merge-local-papeis` cases).
- All six `verify:*` gates pass (`verify:juizo-origem`, `verify:limite-utilizadores`,
  `verify:consola-tenants`, `verify:bloqueio-rbac`, `verify:relatorio-utilizacao`,
  `verify:consola-moldes`) — none of them touch files this plan modified, and none regressed.
- `git diff --stat web/package.json web/pnpm-lock.yaml` is empty — no new dependency.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 07 (RbacTab rewrite) can now consume `useOfficeRbac`, `useSaveOfficeRbac`,
  `useCreateOfficeRole`, `useRenameOfficeRole`, `useDeleteOfficeRole`, `mesclarEstadoLocal`/
  `construirEstadoLocal`, `criarPapelSchema`/`renomearPapelSchema`, and the `OfficeRbac`/
  `OfficePapel` types directly — no further type or hook work needed before assembling the
  screen per UI-SPEC.
- Plan 07 MUST remove the deprecated `useAdminRbac`/`RbacResponse` shim from `use-admin.ts` in
  the same change that rewrites `settings/page.tsx`'s `RbacTab`, and MUST retype that file's
  `UserManagementTab` local state (`editingUser`, `selectedRoles`, `selectedPermissions`) away
  from `MockUser`/`MockRole`/`MockPermission` — those are the five residual `tsc` errors this
  plan intentionally leaves in place (see Issues Encountered).
- Plan 08 (user role picker) can consume `AdminUser.tenant_role_ids` and
  `AdminUserSavePayload.tenantRoleIds` directly, plus the same office-RBAC hooks for the
  available-roles list.

---
*Phase: 127-pap-is-e-permiss-es-do-escrit-rio*
*Completed: 2026-09-22*

## Self-Check: PASSED

All 6 created/modified files verified present on disk; both task commits (`c4e3e577`,
`6f00a102`) verified present in `git log`.
