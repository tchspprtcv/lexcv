---
phase: LEXCV-121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac
reviewed: 2026-07-29T22:48:25Z
depth: deep
files_reviewed: 5
files_reviewed_list:
  - backend/src/main/java/com/lexcv/controllers/AdminController.java
  - backend/src/test/java/com/lexcv/controllers/AdminControllerRbacAutorizacaoTest.java
  - web/package.json
  - web/scripts/verify-bloqueio-rbac.mjs
  - web/src/app/(dashboard)/settings/page.tsx
findings:
  critical: 1
  warning: 3
  info: 2
  total: 6
status: issues_found
---

# Phase LEXCV-121: Code Review Report

**Reviewed:** 2026-07-29T22:48:25Z (round 1) / round-2 verification 2026-07-29
**Depth:** deep
**Files Reviewed:** 5
**Status:** issues_found (0 blocking — see Final Verdict)

## Summary

This review scoped the exact `09d3883d^..HEAD` diff for the 5 listed files (confirmed via `git diff --stat`) and additionally traced cross-file dependencies not in the file list: `PlatformAdminController.java`, `DatabaseSeeder.java`, `SecurityConfig.java`, `UserPrincipal.java`, `JwtAuthenticationFilter.java`, `AuthController.java`, `use-me.ts`, `use-permissions.ts`, `use-admin.ts`, `dashboard-shell.tsx`, `tooltip.tsx`, `badge.tsx`, `providers.tsx`, and `.github/workflows/deploy.yml`.

**The core authorization mechanism is sound and genuinely well-proven, not just asserted.** I confirmed empirically, not just by reading:
- `mvn -o test -Dtest=AdminControllerRbacAutorizacaoTest` → `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`.
- `node scripts/verify-bloqueio-rbac.mjs` → all 11 assertions `PASS` against the real current file.
- The RED commit (`af396a0b`) message documents the exact 3 failing cases before the fix, and the GREEN commit (`cd19ce54`) diff is an 8-line, comment+annotation-only change — nothing else in `updateRbac`'s body moved.
- Spring's "most-specific-annotation-wins" (never AND, never OR) semantics for mixed class/method `@PreAuthorize` is correctly exploited: `AdminControllerRbacAutorizacaoTest`'s two directional tests jointly rule out all three alternative hypotheses (AND-combination, OR-combination, class-only) — Direction 1 alone doesn't (a caller satisfying only the class-level check is denied under both "AND" and "override" hypotheses), but Direction 2 does (a caller satisfying only the method-level check is granted, which is only possible under "override" — both "AND" and "class-only" predict denial). No custom `MethodSecurityExpressionHandler`/`RoleHierarchy`/`GrantedAuthorityDefaults` bean exists anywhere in the app, so the test's manually-assembled `AuthorizationManagerBeforeMethodInterceptor.preAuthorize()` proxy is not a shortcut — it is the same default machinery Spring Boot wires up in production. `AdminController` has no interfaces, so CGLIB proxying was already forced by the pre-existing class-level annotation; adding a method-level annotation doesn't change proxy type. No `@Transactional` exists anywhere on `AdminController`, so the `@Transactional`-ordering concern flagged in the phase brief does not apply to this class. `getRbac` is confirmed byte-for-byte untouched by the diff.
- The test correctly avoids the exact `ROLE_`-prefix pitfall present in the sibling `PlatformAdminControllerTest.autenticarComoRoles` (which builds unprefixed `SimpleGrantedAuthority("ADMIN")`, making its own negative-ADMIN-role tests pass for the wrong reason) — `AdminControllerRbacAutorizacaoTest.autenticarComoAuthorities` is called exclusively with pre-prefixed `"ROLE_ADMIN"` / `"ROLE_PLATAFORMA_ADMIN"` strings.

**However, closing the write path surfaced one real gap the phase didn't account for: nobody can safely read the RBAC matrix before writing it as the platform admin (CR-01), the RBAC matrix UI still lets a non-platform-admin "edit" cells that can never be saved (WR-01), the new gate script (and its three siblings) are not run by CI (WR-02), and there's a same-file variable-shape inconsistency worth cleaning up (WR-03).**

## Round 2 — Fix Verification

A fix pass produced 4 commits addressing CR-01, WR-01, WR-03, and IN-01:
`31619be9` (CR-01), `4a27c109` (WR-01), `be384624` (WR-03), `b7e72100` (IN-01). WR-02 and
IN-02 were deliberately left unfixed (see their entries below).

This round independently re-read every diff, re-ran the full backend suite (`mvn test`:
179/179) and SpotBugs (0 findings) directly, re-ran `node scripts/verify-bloqueio-rbac.mjs`
(11/11 PASS) and `pnpm lint` (0 errors), and — because CR-01 changes a live HTTP contract this
phase's own `121-HUMAN-UAT.md` had already recorded a value for — restarted the local backend
dev server with the fixed code and re-ran the full 4-code HTTP battery live rather than trusting
the commit alone: `GET /admin/rbac` now returns `200` for `plataforma@lexcv.cv` (was `403`
pre-fix), all 3 other codes unchanged (`ADMIN` GET `200`, `ADMIN` PUT `403`, `plataforma` PUT
`200`), and the persisted matrix is still byte-identical to the original pre-battery baseline
even after this second round of no-op writes. `121-HUMAN-UAT.md` was corrected in place with an
explicit addendum documenting the change and the re-verification (commit `ff72c38`), rather than
silently rewritten as if the original record had always shown `200`.

- **CR-01 — CONFIRMED-RESOLVED.** `getRbac` now carries `@PreAuthorize("hasRole('ADMIN') or
  hasRole('PLATAFORMA_ADMIN')")`, a pure read-side widening — the handler body, including the
  deliberate `PAPEL_PLATAFORMA` row exclusion, is untouched. Three new/replaced tests in
  `AdminControllerRbacAutorizacaoTest.java` prove, via the same real AOP-proxy pattern as the
  rest of the file: the annotation's exact literal value (reflection), a `ROLE_PLATAFORMA_ADMIN`-
  only caller now gets a real `200` through the proxy, and a `ROLE_ADMIN`-only caller still does
  (non-regression). `AdminControllerPlataformaAdminContencaoTest`'s Caso 6
  (`getRbac_naoExpoePapelDePlataforma`, response-content filtering, not authorization) was
  re-run unmodified and stays green, confirming the two concerns are properly decoupled. Live
  HTTP re-verification (above) confirms this isn't just a unit-test claim.
- **WR-01 — CONFIRMED-RESOLVED.** Deliberately did *not* apply the literal one-line suggestion
  below (`isDisabled = role === "ADMIN" || !isPlatformAdmin"`), which would have coupled
  `checked={isAssigned || isDisabled}` incorrectly — every checkbox in every non-ADMIN column
  would render as checked for a non-platform-admin viewer regardless of actual assignment, a
  worse bug than the one being fixed. The applied fix introduces a separate `isAdminRow` so
  `checked` stays tied only to the ADMIN column's real semantics, while `isDisabled` (styling +
  the `handleCheckboxChange` early return) independently covers the new read-only case. Verified
  by re-reading the diff line-by-line; `verify-bloqueio-rbac.mjs` A11 (ADMIN-row immutability)
  still passes, confirming no regression to the one case this fix had to avoid disturbing.
- **WR-03 — CONFIRMED-RESOLVED, via the opposite rename from this review's own suggestion.**
  This review's fix example named `RbacTab`'s full-query-object `me` as the rename target. The
  fix instead renamed `UserManagementTab`'s `me` → `meData`, leaving `RbacTab` byte-for-byte
  untouched — correctly reasoned: `verify-bloqueio-rbac.mjs`'s A03/A04 assertions match
  `RbacTab`'s `me.isFetched` by exact substring, so renaming that side would have silently broken
  a working, unrelated gate for a cosmetic fix. Same disambiguation outcome (two components no
  longer share an identifier for two different shapes), zero collateral risk. Good catch by the
  fix pass, not a deviation to flag.
- **WR-02 — deliberately unchanged.** Confirmed still true: `.github/workflows/deploy.yml` has no
  frontend job. Correctly deferred rather than folded into this phase's own fix pass — this is a
  pre-existing, project-wide gap spanning at least 4 phases' worth of `verify:*` scripts, not
  something Phase 121's diff introduced or worsened. Tracked as a separate follow-up (session
  task `task_3b2eae90`) rather than silently dropped.
- **IN-01 — CONFIRMED-RESOLVED.** The always-visible banner now includes the platform-wide-lock
  sentence, gated on `!isPlatformAdmin` (mirroring the Tooltip's own visibility condition, so a
  platform admin who actually *can* save never sees text telling them they can't).
  `verify-bloqueio-rbac.mjs` A08 (exact Tooltip phrase, unrelated element) still passes.
- **IN-02 — deliberately unchanged.** Pre-existing, explicitly out of scope per this project's own
  CLAUDE.md guidance on the legacy `web/src/server/` mock system. No action taken, consistent
  with its own "out of scope for this phase" framing.

Cross-cutting: none of the 4 fix commits touch tenant-scoping or `tenant_id` derivation, and none
weaken any authorization boundary — CR-01's widening is read-only, over platform-structural
(non-tenant) data, for the platform's own highest-privilege role.

## Critical Issues

### CR-01: The only role now authorized to write the RBAC matrix cannot read it first — real data-loss risk on the next legitimate use of this endpoint (RESOLVED — see Round 2)

**File:** `backend/src/main/java/com/lexcv/controllers/AdminController.java:28` (class-level gate), `:346-389` (`getRbac`, unguarded at method level), `:398-432` (`updateRbac`, new method-level gate), cross-referenced with `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java:428-454` (`seedUtilizadorPlataforma`)

**Issue:** `updateRbac` now requires `hasRole('PLATAFORMA_ADMIN')` (line 398), which — per the method-level-wins semantics proven in the test file — completely replaces the class-level `hasRole('ADMIN')` (line 28) for this one method. `getRbac` (lines 346-389) has no method-level override, so it is still gated exclusively by the class-level `hasRole('ADMIN')`.

`DatabaseSeeder.seedUtilizadorPlataforma` (line 440) seeds the one real platform-admin user with `.roles(Set.of(plataformaAdminRole))` — **only** `PLATAFORMA_ADMIN`, never `ADMIN`. `PlatformAdminController` (the platform admin's other surface) has no RBAC-related endpoint at all — only `/tenants` CRUD.

Put together: the platform admin is the *only* caller who can now reach `PUT /api/v1/admin/rbac`, and is simultaneously the *only* caller among the two roles who **cannot** reach `GET /api/v1/admin/rbac` (blocked by the class-level `hasRole('ADMIN')`, which their sole role never satisfies). Before this phase, both endpoints shared one gate (`hasRole('ADMIN')`), so whoever could write could always read the same way first. This phase deliberately narrowed the writer but left the class-level reader gate as-is (per `121-CONTEXT.md`, correctly, per the test `getRbac_continuaSemAnotacaoDeMetodo`) — but nobody widened the reader to match the new writer, and nothing else in the codebase fills that gap.

This is not just an inconvenience: `updateRbac`'s persistence is **replace, not merge**, per-role:
```java
Role role = roleRepository.findByNome(roleName).orElse(null);
if (role != null) {
    ...
    role.setPermissions(permissions);   // <-- full replacement of this role's permission set
    roleRepository.save(role);
}
```
(`AdminController.java:418-427`). Any role key the caller includes in the request has its **entire** permission set overwritten with exactly what was submitted. The frontend's own `RbacTab` protects a tenant ADMIN from this by always seeding `effectiveRolePermissions` from a prior `GET` (`useAdminRbac()`) before allowing `PUT` — but the frontend hides the entire RBAC tab from a platform admin (`hasRbacManage = can.manage("rbac") || isAdmin` is `false` for a role-only `PLATAFORMA_ADMIN`, confirmed by reading `use-permissions.ts`/`settings/page.tsx`), so that safety net is unreachable to them by design. The only way a platform admin can ever legitimately exercise the new write permission is a direct API call with no built-in requirement (and, after this diff, no *possible* path via this controller) to first fetch the current state. The first real-world use of this endpoint by its sole authorized caller is one incomplete payload away from silently deleting another role's existing permissions for every tenant on the platform.

**Fix:** Give the platform admin a read path that matches the new write path, without reopening the write path to tenant admins. Cheapest correct option — broaden `getRbac`'s authorization to accept either role, leaving its body (including the deliberate `PAPEL_PLATAFORMA` row exclusion at lines 352-356) untouched:
```java
@PreAuthorize("hasRole('ADMIN') or hasRole('PLATAFORMA_ADMIN')")
@GetMapping("/rbac")
public ResponseEntity<?> getRbac() {
    ...
}
```
Alternatively, add a dedicated read endpoint to `PlatformAdminController` (already `hasRole('PLATAFORMA_ADMIN')`-gated at the class level) that delegates to the same query logic. Either way, add a test proving a `PLATAFORMA_ADMIN`-only caller can reach the chosen read path — the current suite only proves they can reach the write path.

## Warnings

### WR-01: RBAC matrix checkboxes stay fully interactive for non-platform-admin viewers even though the Save button is hidden (RESOLVED — see Round 2)

**File:** `web/src/app/(dashboard)/settings/page.tsx:813-814`, `:949-960`

**Issue:** The new `isPlatformAdmin` flag (line 768) only gates which element renders in the `CardHeader` (Save button vs. Badge+Tooltip, lines 867-891). It is never consulted by `handleCheckboxChange`:
```js
const handleCheckboxChange = (role: MockRole, permKey: MockPermission) => {
  if (role === "ADMIN") return; // Admin permissions are immutable (always enabled)
  ...
  setLocalRolePermissions({ ...base, [role]: nextPerms });
};
```
or by the checkbox's `disabled` computation in the table body:
```jsx
const isAssigned = (effectiveRolePermissions[role] || []).includes(perm.key);
const isDisabled = role === "ADMIN";
...
<input type="checkbox" checked={isAssigned || isDisabled} disabled={isDisabled}
       onChange={() => handleCheckboxChange(role, perm.key)} ... />
```
A tenant ADMIN (who still sees this tab via the `isAdmin` fallback in `hasRbacManage`) can click every non-ADMIN-role checkbox and watch it visually toggle via `localRolePermissions` — a purely local, never-submitted state change, since the only element that could ever call `handleSave` no longer renders for them. This directly undercuts the phase's own stated goal ("the RBAC tab ... replacing it with a neutral Badge+Tooltip for everyone else") — the tab still *looks* editable for a tenant ADMIN, just silently non-functional, and any local edits vanish on remount with no warning. Neither `verify-bloqueio-rbac.mjs`'s 11 assertions nor the recorded human-UAT checkpoints (which covered button absence, tooltip open behavior, matrix rendering, mobile layout, and platform-admin tab invisibility) exercised this specific interaction.

**Fix:** Extend the disabled condition to also cover the non-platform-admin case, e.g.:
```js
const isDisabled = role === "ADMIN" || !isPlatformAdmin;
```
and early-return from `handleCheckboxChange` when `!isPlatformAdmin`, so the matrix visibly reads as read-only (dimmed, `cursor-not-allowed`) for anyone who cannot save it.

### WR-02: The new gate script (and its 3 siblings) are never executed by CI (deliberately unchanged — see Round 2)

**File:** `web/package.json:9-13`, `.github/workflows/deploy.yml` (entire file)

**Issue:** `.github/workflows/deploy.yml`'s only job that runs before `build-and-push` is `test`, which runs exclusively `mvn -B verify` and `mvn -B spotbugs:check` inside `working-directory: backend`. There is no frontend job, no `pnpm install`, no `pnpm lint`, no `pnpm build`, and no invocation of any `pnpm verify:*` script anywhere in the workflow file. There is also no `.husky/` directory, no `lint-staged`, and no other workflow file in `.github/workflows/` (confirmed by directory listing). This means `verify:bloqueio-rbac` (this phase's new 11-assertion gate) — along with `verify:juizo-origem`, `verify:limite-utilizadores`, and `verify:consola-tenants` from earlier phases — is reachable only by a developer remembering to run it manually. A future edit to `RbacTab` that silently reintroduces an unconditional Save button, or breaks the hook-ordering/ternary structure the gate checks, produces no CI signal at all; the "gate" is real today only because it was run once during this phase's own development.

**Fix:** Add a frontend job to `deploy.yml` (or a step in the existing `test` job) that runs before `build-and-push`, e.g.:
```yaml
  test-frontend:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: pnpm/action-setup@v4
      - uses: actions/setup-node@v4
        with: { node-version: 20, cache: pnpm, cache-dependency-path: web/pnpm-lock.yaml }
      - working-directory: web
        run: |
          pnpm install --frozen-lockfile
          pnpm lint
          pnpm build
          pnpm verify:juizo-origem
          pnpm verify:limite-utilizadores
          pnpm verify:consola-tenants
          pnpm verify:bloqueio-rbac
```
and add `test-frontend` to `build-and-push`'s `needs:`.

### WR-03: `me` holds a different shape under the same name in two components of the same file (RESOLVED — see Round 2)

**File:** `web/src/app/(dashboard)/settings/page.tsx:201` vs. `:762`

**Issue:** `UserManagementTab` destructures `const { data: me } = useMe();` (line 201), so `me` there is the `MeResponse` payload directly (`me?.tenant_limite_utilizadores`, etc.). `RbacTab`, added/touched by this phase, instead binds the whole query object: `const me = useMe();` (line 762), so `me` there requires `.data`/`.isFetched` (`me.data?.roles`, `me.isFetched`). Both are individually correct and type-check, and the pattern `const me = useMe()` does have precedent elsewhere (`dashboard-shell.tsx:83`) — but having both shapes coexist under the identical identifier `me` inside one file is a copy-paste trap: lifting `me?.roles?.includes(...)` from `RbacTab` into `UserManagementTab` (or vice versa) compiles under `noImplicitAny`-off configurations in some cases and fails silently/confusingly in others.

**Fix:** Rename the `RbacTab` binding for clarity, e.g. `const meQuery = useMe();` with `meQuery.isFetched` / `meQuery.data?.roles`, or destructure it the same way as `UserManagementTab` (`const { data: me, isFetched: meIsFetched } = useMe();`).

## Info

### IN-01: Static "Nota Importante" banner in RbacTab wasn't updated to mention the new platform-wide lock (RESOLVED — see Round 2)

**File:** `web/src/app/(dashboard)/settings/page.tsx:907-912`

**Issue:** The persistent blue info banner still only explains why the ADMIN column is locked ("O perfil de administrador (ADMIN) possui acessos totais..."). The new "this whole matrix is platform-managed now" message only exists inside the Tooltip (lines 886-889), which is discoverable only on hover/focus of the Badge — a user who never interacts with the Badge (e.g., just skims the always-visible banner) won't learn why edits don't persist.

**Fix:** Consider folding a short sentence into the always-visible banner for non-platform-admins, not just the on-hover Tooltip, especially given WR-01 above (the matrix still visually accepts clicks).

### IN-02: `RbacTab`/`UserManagementTab` still type real backend responses with legacy `@/server/mock-db` types (deliberately unchanged — see Round 2)

**File:** `web/src/app/(dashboard)/settings/page.tsx:46` (`import type { MockUser, MockRole, MockPermission } from "@/server/mock-db"`), used at `:769` (`Record<MockRole, MockPermission[]>`)

**Issue:** Pre-existing (not introduced by this diff — confirmed absent from the `git diff` hunks), but `CLAUDE.md` explicitly flags `web/src/server/` as the superseded pre-backend mock implementation and says "Don't build new features against them." The RBAC-tab code this phase touched still leans on `MockRole`/`MockPermission` for its real, backend-sourced data shapes.

**Fix:** Out of scope for this phase to fix wholesale, but worth a follow-up ticket to move these type aliases to `web/src/types/` (e.g., alongside `Role` in `types/auth.ts`) so `web/src/server/` can eventually be deleted per its own "legacy/ignore" status.

## Final Verdict

**Phase 121 (fechar suposições de tenant única + bloqueio de RBAC) is APPROVED — no blockers.**

- 0 open Critical findings: CR-01 is resolved and proven both by a new AOP-proxy unit test and by
  a live HTTP re-verification against the running backend with the fixed code.
- 0 open blocking Warnings: WR-01 and WR-03 are fully resolved, each catching and avoiding a
  worse bug than the one this review originally flagged (WR-01's `checked`-state coupling; WR-03's
  gate-script-breaking rename direction). WR-02 is a real, correctly-deferred, pre-existing
  project-wide gap — not introduced by this phase, tracked as a separate follow-up.
- 0 open blocking Info items: IN-01 is resolved; IN-02 is a deliberate, documented non-fix
  consistent with this project's own legacy-code guidance.
- Regression gates independently re-run and green: backend unit suite (179/179), SpotBugs/
  FindSecBugs (0 findings), frontend gate script (11/11), frontend lint (0 errors) — all run
  directly, not just taken from the fix pass's own report.
- This phase's own live-UAT record (`121-HUMAN-UAT.md`) was corrected in place after CR-01
  changed a live HTTP contract the UAT had already recorded a pre-fix value for — the correction
  is itself live-re-verified, not assumed from the fix commit.

No further fix iteration is warranted for this phase.

---

_Reviewed: 2026-07-29T22:48:25Z (round 1)_
_Round-2 verification: 2026-07-29_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
