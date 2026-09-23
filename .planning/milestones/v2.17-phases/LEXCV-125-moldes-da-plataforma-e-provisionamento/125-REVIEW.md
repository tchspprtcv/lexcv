---
phase: LEXCV-125-moldes-da-plataforma-e-provisionamento
reviewed: 2026-09-21T00:00:00Z
depth: deep
files_reviewed: 17
files_reviewed_list:
  - backend/src/main/java/com/lexcv/models/Role.java
  - backend/src/main/java/com/lexcv/models/TenantRole.java
  - backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java
  - backend/src/main/java/com/lexcv/repositories/RoleRepository.java
  - backend/src/main/java/com/lexcv/services/SetupService.java
  - backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java
  - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
  - backend/src/main/java/com/lexcv/dtos/MoldeCreateRequest.java
  - backend/src/main/java/com/lexcv/dtos/MoldesConsolaResponse.java
  - backend/src/main/java/com/lexcv/dtos/MoldesUpdateRequest.java
  - backend/src/main/java/com/lexcv/dtos/MoldeProvisionResponse.java
  - backend/migrations/126-add-tenant-role-tables.sql
  - backend/migrations/README.md
  - backend/src/test/java/com/lexcv/services/SetupServiceInstanciacaoMoldesTest.java
  - backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerMoldesTest.java
  - backend/src/test/java/com/lexcv/controllers/PlatformAdminControllerTest.java
  - web/src/types/platform-moldes.ts
  - web/src/hooks/use-platform-moldes.ts
  - web/src/schemas/moldes.ts
  - web/src/app/(dashboard)/plataforma/moldes/page.tsx
  - web/src/app/(dashboard)/plataforma/moldes/criar-molde-panel.tsx
  - web/src/app/(dashboard)/plataforma/page.tsx
  - web/scripts/verify-consola-moldes.mjs
  - web/package.json
findings:
  critical: 1
  warning: 2
  info: 1
  total: 4
status: issues_found
---

# Phase LEXCV-125: Code Review Report

**Reviewed:** 2026-09-21
**Depth:** deep
**Files Reviewed:** 23 (backend main + tests + migrations, frontend types/hooks/schemas/pages/scripts)
**Status:** issues_found

## Summary

Phase 125 adds the `t_role.instanciavel` flag, the dormant `t_tenant_role`/`t_tenant_role_permission` schema, `SetupService.instanciarMoldes` (snapshot-copy provisioning), and the `PlatformAdminController` `/platform/moldes` GET/PUT/POST surface plus the `/plataforma/moldes` console.

Backend correctness is solid: the compile succeeds (`mvn -DskipTests compile`), the new Mockito test classes (`SetupServiceInstanciacaoMoldesTest`, `PlatformAdminControllerMoldesTest`) pass and genuinely exercise the claimed invariants (snapshot-not-reference via `assertNotSame`/set mutation, `PLATAFORMA_ADMIN` exclusion via a simulated SQL-filter regression, the four-layer defense-in-depth, and the `@PreAuthorize` gate via a real AOP proxy rather than reflection). The `t_tenant_role_permission` join table is genuinely separate from `t_role_permission`, so editing a molde's permissions in `updateMoldes` cannot reach already-instantiated `TenantRole` rows through JPA — the MOLD-03 snapshot invariant holds. The migration is correctly idempotent and the `instanciavel` default is closed (false) so pre-existing rows land on the safe side until the seeder converges on next boot.

The one real defect found is on the frontend: the `/plataforma/moldes` console's render-time state-reset pattern silently discards an operator's unsaved permission-matrix edits whenever the shared TanStack Query cache produces a new `data` payload for a reason other than the matrix's own save — most directly, using "Criar Molde" while the matrix has an unsaved diff. There are also two backend robustness/quality issues: an overly broad `DataIntegrityViolationException` catch that now also swallows failures from molde instantiation and mislabels them as an email conflict, and an unvalidated duplicate-`id` case in the `PUT /platform/moldes` batch payload that silently drops entries instead of rejecting the request.

## Critical Issues

### CR-01: Creating a molde silently discards unsaved permission-matrix edits

**File:** `web/src/app/(dashboard)/plataforma/moldes/page.tsx:107-119` combined with `web/src/hooks/use-platform-moldes.ts:45-58`

**Issue:** The matrix screen keeps local, uncommitted edits in `localPermissoes` and re-derives them from the query payload only when the payload reference changes:

```tsx
const [appliedData, setAppliedData] = React.useState<MoldesConsola | null>(null);
const [localPermissoes, setLocalPermissoes] = React.useState<LocalPermissoes | null>(null);

const data = moldes.data ?? null;
if (data && data !== appliedData) {
  setAppliedData(data);
  setLocalPermissoes(construirEstadoLocal(data));
}
```

This reset is unconditional — it does not check whether there is a pending, unsaved diff (`existeDiff`/`moldesAlterados`) before overwriting `localPermissoes`. It also runs on every render of `MoldesPlataformaContent`, regardless of whether the matrix `<Card>` or the `<CriarMoldePanel>` is currently shown (`isFormOpen` only toggles which JSX branch renders; the parent component, and this state, stay mounted).

`useCreateMolde` (`use-platform-moldes.ts:45-58`) invalidates the exact same query key (`MOLDES_LIST_KEY`) on success:

```ts
onSuccess: async () => {
  await queryClient.invalidateQueries({ queryKey: MOLDES_LIST_KEY });
},
```

Reproduction: an operator toggles several checkboxes in the matrix (unsaved — `existeDiff === true`, `moldesAlterados` non-empty), then, without saving, clicks "Criar Molde" and successfully creates a new molde. `useCreateMolde`'s `onSuccess` invalidates and refetches `MOLDES_LIST_KEY`; the response necessarily differs from the cached payload (it now contains the new molde), so TanStack Query's default structural sharing does **not** preserve the old `data` reference. On the next render, `data !== appliedData` is true, and `localPermissoes` is silently rebuilt from the fresh server payload — the previously toggled, unsaved checkboxes are gone with no warning, no confirmation, and no toast. The operator only discovers this if they happen to notice the matrix looks "reset" after closing the create panel.

This directly contradicts the screen's own non-propagation guarantees UX (three layers of "your changes are safe until you explicitly save") by applying an *implicit* discard of not-yet-saved changes through an unrelated action on the same page.

**Fix:** Guard the render-time reset so it never overwrites a pending diff, or merge instead of replace. E.g.:

```tsx
const data = moldes.data ?? null;
if (data && data !== appliedData) {
  setAppliedData(data);
  if (!existeDiffAgainst(appliedData, localPermissoes)) {
    setLocalPermissoes(construirEstadoLocal(data));
  }
  // else: keep local edits, but re-key against the new molde list (e.g. reconcile
  // ids/permissions that still exist) and surface a toast/banner telling the operator
  // that the underlying data changed while they were editing.
}
```
At minimum, scope the `useCreateMolde` cache invalidation so it doesn't clobber an in-flight matrix edit — e.g. warn before allowing "Criar Molde" to open while `existeDiff` is true, or preserve `localPermissoes` across the create flow explicitly.

## Warnings

### WR-01: `DataIntegrityViolationException` catch in `createTenant` now also swallows molde-instantiation failures and mislabels them

**File:** `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java:99-116`

**Issue:** `createTenant`'s catch block was written (Phase 119/120) specifically for the admin-email TOCTOU race in `provisionTenant`'s pre-check, and always converts `DataIntegrityViolationException` into the fixed message `"Já existe um utilizador com este email."`. Phase 125 added `instanciarMoldes(tenant.getId())` inside the same `@Transactional` method, each iteration calling `tenantRoleRepository.save(tenantRole)` — another `DataIntegrityViolationException`-capable call now running inside the exact try/catch boundary this handler wraps. Any constraint violation surfacing from molde instantiation (e.g. a future regression that lets two moldes share a `nome`, or an unrelated `t_tenant_role`/`t_tenant_role_permission` FK issue) will be caught here and reported to the caller — and logged/observed — as an email conflict, which is misleading during incident diagnosis and masks the real failure mode.

**Fix:** Either narrow this catch to the specific case it was written for (e.g. inspect the constraint name / cause chain before choosing the message), or add a distinct catch/rethrow inside `instanciarMoldes`/`provisionTenant` for `t_tenant_role` violations with its own message, so a violation unrelated to `adminEmail` doesn't get mapped to that string:

```java
} catch (DataIntegrityViolationException ex) {
    String constraint = rootConstraintName(ex); // helper inspecting SQLException/getCause()
    if ("uk_user_email".equals(constraint) /* or similar */) {
        return ResponseEntity.badRequest().body(Map.of("message", "Já existe um utilizador com este email."));
    }
    throw ex; // let the generic 500 handler report it instead of mislabeling it
}
```

### WR-02: `PUT /platform/moldes` silently drops duplicate-id entries instead of rejecting them

**File:** `backend/src/main/java/com/lexcv/controllers/PlatformAdminController.java:263-296`

**Issue:** `updateMoldes` resolves the request body into `Map<Role, Set<Permission>> resolvido` keyed by the `Role` entity (`@EqualsAndHashCode(of = "nome")` on `Role`). The method's own doc-comment claims "valida TODAS as entradas antes de gravar qualquer uma" (validate-then-write, nothing partially applied). If the request body contains two entries with the same `id` (e.g. a buggy client, or a retried/duplicated request), `roleRepository.findById(id)` returns the same managed `Role` for both, and `resolvido.put(role, permissoesResolvidas)` silently overwrites the first entry's resolved permission set with the second's — no validation error is raised, and only the last entry's permissions are ultimately saved. This is inconsistent with the method's own stated all-or-nothing validation discipline, and could surprise a caller who expects either both entries to be honored or a 400.

**Fix:** Detect duplicate ids before building `resolvido` and reject with 400:

```java
Set<Integer> idsVistos = new HashSet<>();
for (MoldesUpdateRequest.MoldePermissoesEntry entrada : request.getMoldes()) {
    if (entrada.getId() != null && !idsVistos.add(entrada.getId())) {
        return ResponseEntity.badRequest().body(Map.of("message", "Molde duplicado no pedido: " + entrada.getId()));
    }
    ...
}
```

## Info

### IN-01: `TenantRoleRepository.findByTenantId` / `findByTenantIdAndNome` are unused

**File:** `backend/src/main/java/com/lexcv/repositories/TenantRoleRepository.java:10-11`

**Issue:** Only `countByMoldeId` is called anywhere in `backend/src/main`. `findByTenantId` and `findByTenantIdAndNome` are unreferenced. This appears to be deliberate forward-looking scaffolding for Phase 126/127 (per the phase's own scope notes, `t_tenant_role` rows are dormant until then), so this is not a defect — but flagging it for visibility since unused public repository methods are otherwise a standard dead-code signal, and it's worth confirming at Phase 126/127 kickoff that these are the methods actually needed (rather than leftover guesses).

**Fix:** No action required now; revisit when Phase 126/127 wires up the first reader of `t_tenant_role`. If they remain unused after that phase, remove them.

---

_Reviewed: 2026-09-21_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
