---
phase: LEXCV-127-pap-is-e-permiss-es-do-escrit-rio
reviewed: 2026-09-22T00:00:00Z
depth: deep
files_reviewed: 33
files_reviewed_list:
  - backend/src/main/java/com/lexcv/config/JwtAuthenticationFilter.java
  - backend/src/main/java/com/lexcv/config/UserPrincipal.java
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java
  - backend/src/main/java/com/lexcv/controllers/ParecerController.java
  - backend/src/main/java/com/lexcv/dtos/OfficeRbacResponse.java
  - backend/src/main/java/com/lexcv/dtos/OfficeRbacUpdateRequest.java
  - backend/src/main/java/com/lexcv/dtos/PapelCreateRequest.java
  - backend/src/main/java/com/lexcv/dtos/PapelRenameRequest.java
  - backend/src/main/java/com/lexcv/dtos/RbacResponse.java
  - backend/src/main/java/com/lexcv/dtos/UserResponse.java
  - backend/src/main/java/com/lexcv/repositories/UserRepository.java
  - backend/src/main/java/com/lexcv/services/ResolucaoPapeisService.java
  - backend/src/main/java/com/lexcv/services/VerificacaoDerivaPapeisService.java
  - backend/src/test/java/com/lexcv/config/UserPrincipalCatalogoSyncTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerAtribuicaoPapeisEscritorioTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerLimiteUtilizadoresTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerPlataformaAdminContencaoTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacCatalogoTest.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacEscritorioTest.java
  - backend/src/test/java/com/lexcv/controllers/OfficeRolesControllerTest.java
  - backend/src/test/java/com/lexcv/controllers/ParecerControllerEntregaProveniencaTest.java
  - backend/src/test/java/com/lexcv/repositories/UserRepositoryContagemPapeisIT.java
  - backend/src/test/java/com/lexcv/services/ResolucaoPapeisServiceTest.java
  - web/package.json
  - web/scripts/verify-bloqueio-rbac.mjs
  - web/scripts/verify-papeis-escritorio.mjs
  - web/src/app/(dashboard)/settings/criar-papel-panel.tsx
  - web/src/app/(dashboard)/settings/merge-local-papeis.test.ts
  - web/src/app/(dashboard)/settings/merge-local-papeis.ts
  - web/src/app/(dashboard)/settings/page.tsx
  - web/src/app/(dashboard)/settings/papel-acoes-menu.tsx
  - web/src/hooks/use-admin.ts
  - web/src/schemas/papeis-escritorio.ts
  - web/src/types/admin-users.ts
  - web/src/types/office-rbac.ts
findings:
  critical: 1
  warning: 2
  info: 1
  total: 4
status: issues_found
---

# Phase LEXCV-127: Code Review Report

**Reviewed:** 2026-09-22
**Depth:** deep
**Files Reviewed:** 33 (backend) + frontend settings/RBAC surface
**Status:** issues_found

## Summary

This phase hands office administrators full CRUD over their own tenant's `TenantRole` rows
(`OfficeRolesController`) and rewrites `AdminController#getRbac/updateRbac` to be tenant-scoped
instead of platform-scoped. The cross-tenant boundary is handled very carefully and consistently:
every handler in `OfficeRolesController` and every tenant-scoped path in `AdminController`
resolves ids exclusively against a map built from `findByTenantId(callerTenant)`, so an id from
another tenant is simply absent (404, never 403 — no enumeration oracle), and this is proven
behaviorally (not just by code reading) in `OfficeRolesControllerTest` and
`AdminControllerRbacEscritorioTest`. `PLATAFORMA_ADMIN` containment (PAPEL-09) is applied with a
consistent triple guard (raw name, `ROLE_`-prefixed name, provenance via `moldeId`) on every new
surface — create, rename, delete, list, and the by-id assignment path in `createUser`/
`updateUser` — and is well tested. The `updateRbac` floor-lock correctly prevents the office
administrator role from losing `rbac:manage`/`users:manage` or being stripped down below those
two authorities, discriminated by `moldeId` (survives renaming), and this is proven with a
dedicated test that constructs a tampered state to show the check isn't just implied by the
superset comparison. `ResolucaoPapeisService`/`UserPrincipal.moldeIds`/
`VerificacaoDerivaPapeisService` genuinely keep the "one query per request, no memoization"
invariant, and the deriva-zero verifier's "antes" snapshot is proven to stay independent of the
resolver under test. The frontend `mesclarEstadoLocal` id-keyed merge is well-designed and its
test suite specifically proves the failure mode (indexing by name instead of id) that would
silently drop unsaved edits.

However, one gap runs directly through the phase's own stated top risk: **nothing anywhere in
this phase prevents an office from ending up with zero users holding the protected
(`moldeId`-linked ADMIN) `TenantRole`.** The floor-lock in `updateRbac` protects the role's
*permission set*; the delete guard in `OfficeRolesController` protects the role's *existence*;
but the *assignment* path (`AdminController#updateUser` via `tenantRoleIds`) has no equivalent
check, and — unlike `deleteUser`, which explicitly blocks self-deletion — `updateUser` has no
self-demotion guard either. A sole office administrator can lock their own office out of
`/api/v1/admin/**` in a single request. This is a BLOCKER given the explicit review priority and
because it is a real, reachable, single-request operational lockout, not a theoretical edge case.

Two lower-severity issues round out the findings: a benign but unhandled race between assigning a
user to a role and deleting that same role (`OfficeRolesController#deleteRole` vs
`AdminController#updateUser`), and the previously-known cosmetic doc-comment drift (route named
`/admin/rbac/papeis` in three doc-comments where the real route is `/admin/rbac/roles`) — exactly
the three instances already known, no additional occurrences found.

## Critical Issues

### CR-01: An office can end up with zero users holding the protected administrator role — including full, single-request self-lockout

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:367-471` (handler
`updateUser`), specifically the `tenantRoleIds` branch at lines 427-445, using
`resolverPapeisEscritorioPorId` (lines 181-234)

**Issue:** Three independent mechanisms protect the office administrator role in this phase:

1. `OfficeRolesController#deleteRole` refuses to delete the role that is provenance-protected
   (`moldeId` == the global `ADMIN` role's id) — protects *existence*.
2. `AdminController#updateRbac` refuses to save a permission set for the protected role that
   drops `rbac:manage`/`users:manage` — protects the role's *permission floor*.
3. `AdminController#deleteUser` refuses to let a principal delete their own user account — a
   partial self-lockout guard, but only for account deletion.

There is no fourth mechanism protecting *assignment*: nothing prevents `updateUser`'s
`tenantRoleIds` branch (lines 427-445) from being used to strip the protected role away from the
last (or only) user who holds it. `resolverPapeisEscritorioPorId` (lines 181-234) validates that
submitted ids exist in the caller's tenant and are not the `PLATAFORMA_ADMIN` role — it does not
check, for the *outgoing* set being replaced, whether the role being removed is the
`moldeId`-protected administrator role and whether the target user is its last holder.

Concretely, in a single-admin office:

1. The founding/sole administrator, authenticated with a valid session that currently grants
   `users:manage` (via the protected `TenantRole`), calls
   `PUT /api/v1/admin/users/{ownUserId}` with `tenantRoleIds` set to some other, non-protected
   role they created (or simply omitting the protected role's id).
2. `@PreAuthorize("hasAuthority('users:manage')")` on the class passes — it is evaluated against
   the *current* token, before the update.
3. `resolverPapeisEscritorioPorId` only checks that the submitted ids exist in-tenant and are not
   `PLATAFORMA_ADMIN`; it has no concept of "this removes the last assignment of the protected
   role."
4. `user.setTenantRoles(...)` / `userRepository.save(user)` proceeds. The protected `TenantRole`
   row itself still exists (with `rbac:manage`/`users:manage` intact — `updateRbac`'s floor-lock
   is not violated, because no one touched the role's permission set), but **no user in the
   tenant holds it any more**.
5. On the caller's very next request, `JwtAuthenticationFilter` re-resolves authorities from the
   database (by design, "immediate, not next login" — `JwtAuthenticationFilter:79-91`) and the
   caller no longer has `users:manage`/`rbac:manage`. Nobody in the office can reach
   `/api/v1/admin/**` (including the very RBAC screen that would let them undo this) any more —
   this is exactly the "silent self-lockout (403 without explanation)" that PAPEL-08's own
   rationale (`AdminController.java:33-43`) says must never happen, reintroduced through the one
   path (`tenantRoleIds` assignment) that the phase's guards don't cover.

This is also reachable without self-targeting: any user whose *own* role independently carries
`users:manage` (a custom, non-protected role that was granted that permission via `updateRbac`)
can call `updateUser` against the one other user who holds the protected administrator role and
remove it from them, with no check that this zeroes out that role's assignment count. No test in
`AdminControllerAtribuicaoPapeisEscritorioTest`, `AdminControllerRbacEscritorioTest`, or
`OfficeRolesControllerTest` exercises this scenario — the floor-lock and delete-count tests both
only cover the two mechanisms that already exist (permission-set floor, and role deletion), not
assignment removal.

**Fix:** Add a fourth guard, symmetric to the delete-count guard in `OfficeRolesController`, in
`AdminController#updateUser`'s `tenantRoleIds` branch (and, for completeness, anywhere else
`user.setTenantRoles(...)` can be called): before persisting, if the user currently holds the
`moldeId`-protected role and the new `tenantRoles` set does not contain it, check whether removing
it would leave `userRepository.countByTenantRolesId(protectedRoleId)` at zero (excluding the user
being updated) and refuse with 409 if so — the same "count, not FK failure" discipline
`OfficeRolesController#deleteRole` already uses. Example shape:

```java
// after resolving `tenantRoles` in updateUser, before user.setTenantRoles(...):
Integer adminMoldeId = roleRepository.findByNome("ADMIN").map(Role::getId).orElse(null);
if (adminMoldeId != null) {
    boolean detinhaAntes = user.getTenantRoles().stream()
            .anyMatch(tr -> adminMoldeId.equals(tr.getMoldeId()));
    boolean deteraDepois = tenantRoles.stream()
            .anyMatch(tr -> adminMoldeId.equals(tr.getMoldeId()));
    if (detinhaAntes && !deteraDepois) {
        long outrosDetentores = tenantRoles.stream() // no-op; need the OLD protected role id
                .anyMatch(tr -> adminMoldeId.equals(tr.getMoldeId())) ? 0 : 0;
        // count holders of the specific protected TenantRole excluding this user
        UUID protectedRoleId = user.getTenantRoles().stream()
                .filter(tr -> adminMoldeId.equals(tr.getMoldeId()))
                .map(TenantRole::getId).findFirst().orElse(null);
        if (protectedRoleId != null
                && userRepository.countByTenantRolesId(protectedRoleId) <= 1) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message",
                    "Não é possível remover o papel de administrador do escritório do último "
                    + "utilizador que o detém."));
        }
    }
}
```

(Adjust to the codebase's actual style/placement; the essential fix is: count-based "last holder"
check on the *outgoing* protected-role removal, mirroring the existing delete-count guard.)

## Warnings

### WR-01: Unhandled race between assigning a user to a role and deleting that role

**File:** `backend/src/main/java/com/lexcv/controllers/OfficeRolesController.java:253-287`
(`deleteRole`), in combination with `AdminController.java:427-445` (`updateUser`'s
`tenantRoleIds` branch)

**Issue:** `deleteRole`'s guard order is: cross-tenant check → provenance check → assignment count
(`userRepository.countByTenantRolesId(id) > 0` → 409) → `tenantRoleRepository.deleteById(id)`.
This is deliberately a count-based check, not a try/catch around the delete (the doc-comment at
lines 240-246 explicitly rejects the "catch DataIntegrityViolationException on the FK" approach
that `createRole`/`renameRole` use for the *unique-name* constraint). Between the count check and
`deleteById`, a concurrent `PUT /api/v1/admin/users/{id}` call with this role's id in
`tenantRoleIds` can insert a new `t_user_tenant_role` row (via `User`, the owning side of that
join table). `deleteById` then executes against a row that a FK constraint now references,
producing an uncaught `DataIntegrityViolationException` → an unhandled 500 instead of the 409 the
count check was designed to produce. Low likelihood (requires two concurrent admin actions on the
same role), but the resulting failure mode is worse (unstructured 500) than the one the count
check exists to prevent, and there is no `@Transactional`/lock/retry to close the window.

**Fix:** Either wrap the final `deleteById` in a narrow `try/catch (DataIntegrityViolationException)`
that returns the same 409 assignment-conflict response (cheap, consistent with the pattern already
used for name-uniqueness elsewhere in this class), or re-check the count inside a single
`@Transactional` method with appropriate isolation. A `try/catch` fallback is the lower-risk fix
given the existing count check already covers the common case.

### WR-02: `settings/page.tsx` tab visibility still checks the literal role name "ADMIN"

**File:** `web/src/app/(dashboard)/settings/page.tsx:86`

**Issue:** `const isAdmin = me?.roles?.includes("ADMIN");` is used (ORed with
`can.manage("users")`/`can.manage("rbac")`) to decide whether to show the "Gestão de
Utilizadores"/"Controlo de Acesso (RBAC)" tabs. `me.roles` are the *effective names* returned by
`ResolucaoPapeisService.resolverNomesPapeis`, which are exactly the names PAPEL-04 makes
renameable. After an office renames its administrator role, `isAdmin` silently becomes `false` for
that principal. This is not a security gap (both flags are ORed with the real
`can.manage(...)` permission checks, which do not depend on the role's name), and the tabs would
still render correctly for any renamed-admin user because `can.manage("users")` is independently
true — but it is dead/misleading logic left over from before PAPEL-04, inconsistent with the
provenance-based discipline (`moldeId`, never name) that the rest of this phase is careful to
apply everywhere else (`ParecerController`, `OfficeRolesController`, `AdminController`'s
`getRbac`/`updateRbac`/`resolverPapeisEscritorioPorId`).

**Fix:** Either remove the `isAdmin` fallback entirely (the `can.manage(...)` checks already cover
the intended visibility), or, if a defense-in-depth OR is wanted, derive it from something
provenance-based rather than the literal string `"ADMIN"`.

## Info

### IN-01: Three doc-comments still reference the superseded route `/admin/rbac/papeis`

**Files:**
- `backend/src/main/java/com/lexcv/dtos/PapelCreateRequest.java:9`
- `backend/src/main/java/com/lexcv/dtos/PapelRenameRequest.java:7`
- `backend/src/main/java/com/lexcv/repositories/UserRepository.java:41`

**Issue:** The real, implemented route is `/api/v1/admin/rbac/roles` (see
`OfficeRolesController.java:65`), not `/api/v1/admin/rbac/papeis`. These three doc-comments were
not updated to match. Purely cosmetic — no code or test depends on the stale string — and this is
the exact, already-known set of three instances; no further occurrences were found in the phase's
diff.

**Fix:** Update the three doc-comments to `/api/v1/admin/rbac/roles`.

---

_Reviewed: 2026-09-22_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
