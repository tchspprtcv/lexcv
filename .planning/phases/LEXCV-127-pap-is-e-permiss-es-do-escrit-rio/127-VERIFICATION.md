---
phase: 127-pap-is-e-permiss-es-do-escrit-rio
verified: 2026-09-22T10:15:00Z
status: human_needed
score: 5/5 roadmap success criteria verified; 10/10 requirements (PAPEL-01..09, CATL-04) satisfied
overrides_applied: 0
human_verification:
  - test: "Full 12-step live UAT checklist recorded verbatim in 127-08-SUMMARY.md (tab shows office roles with provenance badges, protected-role lock icon, save round-trip, unsaved-edit survival across a rename, create/delete a role, assignment count reflected in the delete-refusal message, protected-role kebab menu text, RENAME THE ADMIN ROLE AND CONFIRM NO SELF-LOCKOUT, user-form role picker pre-ticks/refuses last-role removal, keyboard accessibility, delete-dialog visual parity with /plataforma)"
    expected: "All 12 steps pass against a running backend + real Postgres/MinIO + browser session; step 9 (rename own admin role, reload, confirm /api/v1/admin still reachable) is the single most important live check for this phase's core promise"
    why_human: "Requires a running backend, reachable PostgreSQL/MinIO, and an interactive browser session — none of which this verification pass can drive. All static/automated proxies for these 12 steps are green (18/18 verify:papeis-escritorio gate, 352/352 backend tests including an explicit floor-lock/isolation/live-session-effect suite, tsc/lint/build/test all clean)."
---

# Phase 127: Papéis e Permissões do Escritório Verification Report

**Phase Goal:** O administrador de um escritório gere por inteiro os papéis do seu próprio escritório — sem depender da plataforma para nada disto — com a certeza absoluta de que nada do que faz alcança outro tenant ou o papel da própria plataforma.
**Verified:** 2026-09-22
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Office admin sees/creates/renames/edits-permissions/assigns roles of own office (and only that office) via `GET/PUT /admin/rbac` tenant-scoped, gated by `hasAuthority('rbac:manage')` | VERIFIED | `AdminController.java:521,631` both annotated `@PreAuthorize("hasAuthority('rbac:manage')")`; `getRbac` reads `tenantRoleRepository.findByTenantId(principal.getTenantId())` only (no `roleRepository.findAll()`); `OfficeRolesController` (new class, class-level `hasAuthority('rbac:manage')` gate) handles create/rename/delete at `/api/v1/admin/rbac/roles`. Proven by `AdminControllerRbacEscritorioTest` (11 cases), `OfficeRolesControllerTest` (15 cases), `AdminControllerRbacAutorizacaoTest` (14 cases, real `ProxyFactory`+`AuthorizationManagerBeforeMethodInterceptor` proxy, not annotation reflection). |
| 2 | Permission edits take effect on live sessions without new login | VERIFIED | `JwtAuthenticationFilter.doFilterInternal` re-resolves `roles`/`permissions`/`moldeIds` from the DB-loaded `User` (EAGER `tenantRoles`) on **every** authenticated request, with an explicit comment stating "deliberadamente sem memorização". `AdminControllerRbacEscritorioTest.updateRbac_efeitoImediatoNumaSessaoJaAbertaSemRelogin` feeds the exact `TenantRole` captured from `save()` into a real `ResolucaoPapeisService.resolverPermissoesEfectivas` call and asserts the new permission is present — not a description, an executed assertion. |
| 3 | No office action has any visible/persisted effect on another office | VERIFIED | `updateRbac`/`OfficeRolesController` resolve ids exclusively against a map built from `tenantRoleRepository.findByTenantId(principal.getTenantId())` — a foreign-tenant id is absent by construction, never reachable by branch logic. `AdminControllerRbacEscritorioTest.updateRbac_idDeOutroTenantERecusadoENuncaAlcancaOOutroTenant` and `OfficeRolesControllerTest`'s rename/delete cross-tenant cases assert **404** (never 403, closing an enumeration probe) and assert the other tenant's `TenantRole` object is byte-for-byte unchanged and `tenantRoleRepository.findByTenantId(OUTRO_TENANT_ID)` is `never()` called. |
| 4 | Assigned role cannot be deleted; unassigned can; own-office admin role can never be deleted nor stripped of admin-making permissions | VERIFIED | `OfficeRolesController.deleteRole` refuses via `userRepository.countByTenantRolesId(id) > 0` (a real COUNT query, never a caught FK violation or `isEmpty()` list load) and independently refuses admin-molde-provenance roles unconditionally. `updateRbac`'s floor-lock (two independent checks: superset rule + explicit `rbac:manage`/`users:manage` presence check) blocks stripping the protected role even against hand-edited stored state, pinned by `AdminControllerRbacEscritorioTest` Casos 7-9. |
| 5 | `PLATAFORMA_ADMIN` never listed, never assignable, unreachable from any office screen/endpoint | VERIFIED | Triple guard (raw name, `ROLE_`-prefixed name, molde-id provenance) present in `getRbac`'s filter, `updateRbac`'s per-entry check, and `OfficeRolesController.deleteRole`. `AdminControllerPlataformaAdminContencaoTest` (Phase 119's 14-case containment suite) ported and preserved in full — none weakened; `updateRbac`'s PLATAFORMA_ADMIN refusal was actually *strengthened* from a silent `continue`/200 to an explicit 403. |

**Score:** 5/5 roadmap success criteria verified.

### Requirements Coverage

| Requirement | Description | Status | Evidence |
|---|---|---|---|
| PAPEL-01 | Office sees only its own roles | SATISFIED | `getRbac` tenant-scoped read; `AdminControllerRbacEscritorioTest.getRbac_devolveApenasPapeisDoChamador` |
| PAPEL-02 | Create a role choosing name + permissions | SATISFIED | `OfficeRolesController.createRole`; `OfficeRolesControllerTest` |
| PAPEL-03 | Permission edit takes effect on live session | SATISFIED | See Truth #2 above |
| PAPEL-04 | Rename a role | SATISFIED | `OfficeRolesController.renameRole`; `ParecerController` converted to provenance so a renamed admin role keeps delivering pareceres (`ParecerControllerEntregaProveniencaTest`, 6 cases) |
| PAPEL-05 | Delete refused by live assignment, allowed when unassigned | SATISFIED | `countByTenantRolesId` guard, see Truth #4 |
| PAPEL-06 | Assign roles by id, at create and edit | SATISFIED | `AdminController.createUser`/`updateUser` require `tenantRoleIds`, refuse legacy `roles` with 400; `UserManagementTab` role picker submits `tenantRoleIds`; `AdminControllerAtribuicaoPapeisEscritorioTest` (14 cases) |
| PAPEL-07 | Isolation — no cross-tenant effect | SATISFIED | See Truth #3 |
| PAPEL-08 | Admin role undeletable/unstrippable, by provenance | SATISFIED | See Truth #4; discriminator is `TenantRole.moldeId` against the global "ADMIN" `Role` id everywhere, never `nome` |
| PAPEL-09 | `PLATAFORMA_ADMIN` unreachable from any office surface | SATISFIED | See Truth #5 |
| CATL-04 | `rbac:manage` actually governs writes | SATISFIED | `updateRbac`/`OfficeRolesController` gated by `hasAuthority('rbac:manage')`, no `hasRole('PLATAFORMA_ADMIN')` remains on any office-facing write path |

**Note:** `.planning/REQUIREMENTS.md` still shows `[ ]` (unchecked) and "Pending" for all ten of these requirement rows, contradicting the evidence above and ROADMAP.md's "Phase 127 ... completed 2026-09-22" line. This is a documentation-bookkeeping gap only — no code or test evidence contradicts SATISFIED — but the tracking doc should be updated so a future reader doesn't misread it as undone work. (Info-level, not a blocker.)

### Self-Lockout Chain (explicit walk, per verification brief)

| Check | Result | Evidence |
|---|---|---|
| (a) No live `hasRole('ADMIN')` remains on any office-facing handler in `AdminController`/`OfficeRolesController` | VERIFIED | `grep -n "@PreAuthorize"` on both files: only `hasAuthority('users:manage')` (class) and `hasAuthority('rbac:manage')` (both RBAC methods, and `OfficeRolesController`'s class gate). Remaining `hasRole('PLATAFORMA_ADMIN')` textual mentions are all inside comments explaining `PlatformAdminController`'s (a different, platform-only class) gate — none are live annotations on these two classes. |
| (b) Gates are `hasAuthority`, never `hasRole`, since permissions aren't `ROLE_`-prefixed | VERIFIED | Confirmed by direct read of the annotations (above) and by `AdminControllerRbacAutorizacaoTest`'s explicit `ROLE_rbac:manage` / `ROLE_users:manage` denial cases (proving the `hasRole` form would NOT work, by testing it fails). |
| (c) Floor-lock guarantees the protected role can never lose `users:manage`/`rbac:manage`, in `PUT /admin/rbac` AND everywhere else | VERIFIED for `updateRbac` (the only write path to a `TenantRole`'s `permissions` collection found in the codebase — `OfficeRolesController`'s rename touches only `nome`, its delete/create never touch an existing protected role's permission set). Two independent checks (superset rule + explicit authority-presence check) pinned by `AdminControllerRbacEscritorioTest` Casos 7-9, including a case (Caso 9) specifically designed to catch stored state mutated outside the application. No second write path to `TenantRole.permissions` was found by inspection of `OfficeRolesController`/`AdminController`/`MigracaoPapeisEscritorioService`. |

### Name-Comparison Debt

| Site | Before | After | Status |
|---|---|---|---|
| `ParecerController:434` (deliver-parecer guard) | `principal.getRoles().contains("ADMIN")` | `resolucaoPapeisService.temPapelDeMolde(principal, NOME_MOLDE_ADMIN)` | PAID — provenance-based |
| `ParecerController:509` (create-version guard) | same | same | PAID |
| `web/src/app/(dashboard)/settings/page.tsx` floor-lock in `RbacTab` | n/a (new) | reads `papel.protegido` (server-computed) | PAID — never re-derives from `nome` client-side |
| `web/src/app/(dashboard)/settings/page.tsx:86` `isAdmin = me?.roles?.includes("ADMIN")` | — | OR-combined with `can.manage(...)` for tab-visibility fallback only, not the floor-lock computation | Acceptable — this is a UI tab-visibility fallback, not a security gate; the actual authorization is server-side `hasAuthority`, and `can.manage("rbac"/"users")` (permission-based) already covers a renamed-admin session even if `isAdmin` goes false |

All backend `"ADMIN"` string literal occurrences (`grep -rn '"ADMIN"' backend/src/main/java/com/lexcv/controllers/`) resolve to `roleRepository.findByNome("ADMIN")` — i.e., looking up the *global molde's id* for provenance comparison — never a name-equality check on a `TenantRole` or principal role set.

### Role Assignment by Id — Backend/Frontend Agreement

| Check | Result |
|---|---|
| `createUser`/`updateUser` require `tenantRoleIds`, refuse a body still containing `roles` (400, naming `tenantRoleIds`) | VERIFIED — read directly in `AdminController.java:269-278` and `:419-424` |
| Ids resolved only against the caller's own tenant's `TenantRole` list (never `findById`) | VERIFIED — `resolverPapeisEscritorioPorId` |
| Foreign-tenant / malformed / `PLATAFORMA_ADMIN`-provenance ids refused before any write | VERIFIED — `AdminControllerAtribuicaoPapeisEscritorioTest` (14 cases) |
| `t_user_role` mirror still populated (reversibility) | VERIFIED — `derivarMirrorGlobalDePapeis`, provenance-derived (`moldeId` → `roleRepository.findById`), never from a possibly-renamed name |
| `UserManagementTab` submits `tenantRoleIds` | VERIFIED — `page.tsx:318` |
| Payload shapes match | VERIFIED — `AdminUserSavePayload.tenantRoleIds: string[]` (frontend) vs `body.get("tenantRoleIds")` (backend), both UUID-string-keyed |

### Phase 121 Retirement

The `ISOL-03` comment block at `AdminController.java:602-624` (and mirrored in `OfficeRolesController.java:602-619` region... actually in the same file's `updateRbac` doc-comment) is **replaced, not deleted**: it restates Phase 121's original reasoning (global `Role`/`Permission`, no `tenant_id`), explains why Phases 125/126 closed that gap, and explicitly instructs that any future widening of the handler back to global tables must restore the old gate in the same edit. VERIFIED by direct read.

### The Rewritten Gate

| Check | Result |
|---|---|
| `web/package.json` `verify:papeis-escritorio` entry present, no stale `verify:bloqueio-rbac` entry | VERIFIED |
| `web/scripts/verify-bloqueio-rbac.mjs` absent (renamed via `git mv`, history preserved) | VERIFIED |
| Gate runs and passes 18/18 assertions against the current codebase | VERIFIED — ran directly: `PASS` × 18, exit 0 |
| Rules-of-Hooks assertion (`hooks-antes-do-primeiro-early-return`) is genuinely enforceable | VERIFIED by construction — generic `/\buse[A-Z]\w*/g` scan over the comment-stripped block compared by index against the first early return, not a hardcoded hook name |
| Gate can genuinely fail (not a tautology) | VERIFIED by spot-check — reintroduced a `role === "ADMIN"` literal into the `RbacTab` block, re-ran the gate: `FAIL matriz-bloqueio-por-proveniencia`, exit 1; reverted via `git checkout --`, confirmed `git status` clean, re-ran: 18/18 PASS again |

### Containment and Isolation Suites Intact

| Suite | Expected cases | Actual cases (grep `@Test`) | Status |
|---|---|---|---|
| `AdminControllerPlataformaAdminContencaoTest` (Phase 119, PAPEL-09) | 14 | 14 | VERIFIED, none dropped; `updateRbac`'s refusal strengthened (silent-continue/200 → explicit 403), documented in-line as a strengthening |
| `AdminControllerLimiteUtilizadoresTest` (Phase 117) | 9 | 9 | VERIFIED, ripple-only change (fixture converted to `tenantRoleIds`, same case count) |

### Schema Change

No new file under `backend/migrations/` attributable to Phase 127 (`git log --oneline -- backend/migrations/` shows the last additions from Phase 126-01 (script 127) and earlier). VERIFIED — no schema change in this phase.

### Health / Full Suite Results

| Check | Result |
|---|---|
| `mvn test` (JDK 23) | **352/352 passed**, 0 failures, 0 errors |
| `mvn spotbugs:check` | Clean — 0 bugs, 0 errors |
| `pnpm exec tsc --noEmit` | Clean — 0 errors |
| `pnpm lint` | 0 errors, 20 pre-existing warnings (unrelated `<img>`/react-hooks-incompatible-library notices, none introduced by this phase) |
| `pnpm test` (vitest) | **30/30 passed**, 5 test files |
| `pnpm build` | Succeeds — all 33 routes built, including `/settings` |
| `verify:juizo-origem` | PASS |
| `verify:limite-utilizadores` | PASS (9/9) |
| `verify:consola-tenants` | PASS (12/12) |
| `verify:papeis-escritorio` | PASS (18/18) |
| `verify:relatorio-utilizacao` | PASS (15/15) |
| `verify:consola-moldes` | PASS (15/15) |

Testcontainers ITs (`UserRepositoryContagemPapeisIT`) not run locally — known Docker npipe blocker, not a phase-introduced defect; the file compiles and no unit-test regression resulted from its addition.

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`HACK`/`PLACEHOLDER` markers found in any file touched by this phase (`AdminController.java`, `OfficeRolesController.java`, `ParecerController.java`, `settings/page.tsx`, `criar-papel-panel.tsx`, `papel-acoes-menu.tsx`, `use-admin.ts`). No stub returns, no hardcoded empty arrays feeding rendered data, no disabled-item-with-tooltip anti-pattern (the delete-refusal is a static explanatory row per UI-SPEC's own accessibility reasoning, not a disabled button).

One doc-only drift (already flagged by the phase's own summaries and confirmed here): `PapelCreateRequest`/`PapelRenameRequest`/`UserRepository` doc-comments (written in plan 02) reference `/api/v1/admin/rbac/papeis`, while the real, shipped route is `/api/v1/admin/rbac/roles`. Confirmed the frontend (`use-admin.ts`) calls the real route (`/admin/rbac/roles`), not the stale documented one. Cosmetic only — ℹ️ Info, not a blocker, consistent with the brief's guidance.

### Human Verification Required

### 1. Full live UAT walkthrough (12 steps, recorded in 127-08-SUMMARY.md)

**Test:** Walk through the 12-step checklist verbatim against a running backend + real Postgres/MinIO + browser (role matrix rendering, protected-role lock icon and disabled-checked-boxes-but-tickable-unchecked-boxes, save round-trip with toast, unsaved-edit survival across an unrelated rename, create/delete a role end-to-end, assignment-count-driven delete refusal text, protected-role kebab text, **rename the office's own admin role and confirm no self-lockout on reload**, user-form role picker pre-selection and last-role-removal refusal, keyboard accessibility, visual parity of the delete confirmation dialog with `/plataforma`'s tenant-suspend dialog).
**Expected:** All 12 steps pass, with step 9 (the self-lockout check) being the load-bearing one for this phase's central promise.
**Why human:** Requires a running application stack and an interactive browser session that this static/automated verification pass cannot drive. All automated proxies for every one of these 12 behaviors are green (structural gate, unit/behavioral test suites including an explicit `updateRbac_efeitoImediatoNumaSessaoJaAbertaSemRelogin` and floor-lock tests, tsc/lint/build/test), so this is recorded as **owed live confirmation**, not a discovered gap — consistent with the verification brief's explicit guidance for this phase.

### Gaps Summary

No gaps found. All 5 ROADMAP success criteria and all 10 mapped requirements (PAPEL-01..09, CATL-04) are backed by direct code evidence (read, not summary-trusted) and by tests/gates that were actually executed during this verification pass, including a demonstrated-failing spot-check of the rewritten structural gate. The only open item is the pre-recorded, not-yet-executed live human UAT checklist from 127-08-SUMMARY.md, which this phase's own plan explicitly deferred to end-of-phase/post-autonomous-run human verification. Status is `human_needed` rather than `passed` solely because that checklist is outstanding — not because any automated check failed.

Minor documentation-only items noted for cleanup (non-blocking): `.planning/REQUIREMENTS.md` checkboxes/status table for PAPEL-01..09/CATL-04 not updated to reflect completion; three doc-comments (plan-02-authored) reference a stale `/admin/rbac/papeis` path instead of the shipped `/admin/rbac/roles`.

---

*Verified: 2026-09-22*
*Verifier: Claude (gsd-verifier)*
