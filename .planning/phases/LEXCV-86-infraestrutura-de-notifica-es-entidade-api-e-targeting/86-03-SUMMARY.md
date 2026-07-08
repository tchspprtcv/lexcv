---
phase: 86-infraestrutura-de-notificacoes-entidade-api-e-targeting
plan: 3
subsystem: api
tags: [spring-boot, spring-security, rbac, rest, jpa-pageable, notificacoes]

# Dependency graph
requires:
  - phase: 86 (Plan 86-01)
    provides: "NotificacaoRepository.buscarPorFiltros / countByTenantIdAndDestinatarioIdAndLidaFalse dual-scoped finders"
  - phase: 86 (Plan 86-02)
    provides: "NotificacaoService.marcarLida / marcarTodasLidas as the sole write path for the subsystem"
provides:
  - "REST API surface /api/v1/notificacoes: GET (list, filter+paginate), GET /unread-count, PATCH /{id}/lida, POST /ler-todas"
  - "notificacoes:view RBAC scope seeded to ADMIN/ADVOGADO/TECNICO/ASSISTENTE, listed in AdminController.getRbac() systemPermissions, registered in frontend KNOWN_SCOPES"
affects: [87-infraestrutura-de-notificacoes-alertas-eventos, 89-infraestrutura-de-notificacoes-sino-e-pagina]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dedicated single-purpose controller extraction (ParecerPesquisaController precedent) instead of growing the ~2900-line ResourceController"
    - "Dual-scoping (tenantId + userId) for per-recipient-private resources — first occurrence in this codebase; getTenantId()/getUserId() both read from UserPrincipal via SecurityContextHolder, never from request input"
    - "Controller never calls notificacaoRepository directly for writes — both mutating endpoints delegate to NotificacaoService, preserving it as the sole write path across the whole subsystem"
    - "404-not-403 on cross-recipient access to avoid existence leak across the recipient boundary (matches existing cross-tenant 404 convention)"

key-files:
  created:
    - backend/src/main/java/com/lexcv/controllers/NotificacaoController.java
  modified:
    - backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java
    - backend/src/main/java/com/lexcv/controllers/AdminController.java
    - web/src/lib/permissions.ts

key-decisions:
  - "notificacoes:view granted identically to all 4 roles (no per-role differentiation), because per-recipient filtering already isolates each user to their own notifications"
  - "UserPrincipal.java hardcoded ADMIN bonus-permission list intentionally left untouched — ADMIN already receives the scope via upsertRolePermissions(\"ADMIN\", permissionMap.values()), so adding it there would be redundant"

patterns-established:
  - "Pattern: read endpoints query the repository directly; write endpoints go through the service — this is now the second controller (after NotificacaoService's own internal use) proving out that write-choke-point convention end-to-end via HTTP"

requirements-completed: [NOTF-14]

# Metrics
duration: 13min
completed: 2026-07-08
---

# Phase 86 Plan 3: NotificacaoController REST API + notificacoes:view RBAC plumbing Summary

**Dedicated `NotificacaoController` exposing 4 dual-scoped (tenant + recipient) endpoints over `/api/v1/notificacoes`, backed entirely by Plan 86-01's repository (reads) and Plan 86-02's service (writes), with `notificacoes:view` seeded to all 4 roles and wired into the Admin Settings RBAC screen and the frontend scope registry.**

## Performance

- **Duration:** ~13 min
- **Started:** 2026-07-08T20:58:00Z (approx.)
- **Completed:** 2026-07-08T21:11:00Z
- **Tasks:** 2
- **Files modified:** 4 (1 created, 3 modified) + 1 deferred-items log

## Accomplishments
- `NotificacaoController` (`@RequestMapping("/api/v1/notificacoes")`) with all four endpoints from Success Criterion 1: `GET ""` (categoria/lida filters + pagination via `PageRequest.of(page, size)`, no `Sort`), `GET /unread-count`, `PATCH /{id}/lida`, `POST /ler-todas` — every one gated by `@PreAuthorize("hasAuthority('notificacoes:view')")` and scoped by both `getTenantId()` and the newly-added `getUserId()` helper.
- Read endpoints (`listar`, `contarNaoLidas`) call `NotificacaoRepository` directly; the two mutating endpoints (`marcarLida`, `marcarTodasLidas`) delegate entirely to `NotificacaoService` (Plan 86-02) — the controller contains zero direct repository writes, preserving the service as the subsystem's sole write path.
- `marcarLida` returns `404 Notificação não encontrada` (never `403`) when the id belongs to a different `destinatario` in the same tenant, since `NotificacaoService.marcarLida` returns an empty `Optional` in that case — no existence leak across the recipient boundary.
- `notificacoes:view` seeded in `DatabaseSeeder.seedRbac()` for ADMIN (via the existing `permissionMap.values()` call), ASSISTENTE, TECNICO, and ADVOGADO (identical grant across all 4 roles, per the locked CONTEXT.md decision).
- `notificacoes:view` added to `AdminController.getRbac()`'s `systemPermissions` (módulo `"Notificações"`) so it is visible and toggleable on the Admin Settings RBAC screen, not just seeded silently.
- `"notificacoes"` registered in `web/src/lib/permissions.ts`'s `KNOWN_SCOPES`, so `hasScopedPermission(perms, "notificacoes", "view")` resolves correctly against the backend-issued permission set.

## Task Commits

Each task was committed atomically:

1. **Task 1: NotificacaoController — four dual-scoped endpoints** - `70da997` (feat)
2. **Task 2: Seed notificacoes:view scope (backend RBAC seed + Admin RBAC screen + frontend registry)** - `f734ad4` (feat)

_No plan-metadata commit yet — this SUMMARY.md commit is the metadata commit for this plan (parallel worktree execution; orchestrator handles STATE.md/ROADMAP.md after the wave)._

## Files Created/Modified
- `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java` - New dedicated controller; 4 endpoints, dual tenant+recipient scoping, reads via repository, writes via service, 404-not-403 on cross-recipient access
- `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java` - `notificacoes:view` added to `permKeys` and to `ASSISTENTE`/`TECNICO`/`ADVOGADO` grant lists (ADMIN already covered)
- `backend/src/main/java/com/lexcv/controllers/AdminController.java` - `notificacoes:view` `PermissionDefDto` entry added to `getRbac()`'s `systemPermissions` (módulo `"Notificações"`)
- `web/src/lib/permissions.ts` - `"notificacoes"` added to `KNOWN_SCOPES`
- `.planning/phases/LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting/deferred-items.md` - New; logs pre-existing unrelated web lint debt discovered during verification (not fixed, out of scope)

## Decisions Made
- Followed PLAN.md's task instructions (write endpoints delegate to `NotificacaoService`) rather than the now-outdated direct-repository-write pattern shown in `86-PATTERNS.md` — PLAN.md's `<interfaces>` section explicitly supersedes that older pattern given the added dependency on Plan 86-02.
- Left `UserPrincipal.java`'s hardcoded ADMIN bonus-permission list untouched, per plan instruction — ADMIN already gets `notificacoes:view` through the normal per-role seeding path, so adding it there would be redundant.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Ran `pnpm install` in `web/` to unblock the plan's mandated `pnpm lint` verification**
- **Found during:** Task 2 verification
- **Issue:** This worktree's `web/` directory had no `node_modules` at all (fresh worktree checkout never had install run), so `pnpm lint` failed with `ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL Command "eslint" not found` — blocking the acceptance criterion "`pnpm lint` (web) still passes / no TypeScript error introduced by the registry addition."
- **Fix:** Ran `pnpm install --frozen-lockfile` (hydrates the already-committed, already-locked `pnpm-lock.yaml` — not installing any new/unverified package, so this is not a Rule-3-excluded package-manager-install case). Install succeeded against the existing lockfile; `pnpm lint` then ran successfully.
- **Files modified:** None tracked (only populates the gitignored `node_modules/`).
- **Verification:** `pnpm lint` ran to completion; confirmed via targeted grep that `web/src/lib/permissions.ts` (the only file this plan touched in `web/`) has zero lint findings.
- **Committed in:** N/A (no file changes to commit — environment setup only).

**2. [Out-of-scope discovery, not fixed] Pre-existing web lint debt (5 errors, 17 warnings across 12 unrelated files)**
- **Found during:** Task 2 verification (`pnpm lint` run, first time in this fresh worktree)
- **Issue:** `pnpm lint` reported issues in 12 files this plan does not touch (`dashboard-shell.tsx`, several `(dashboard)/*` pages, `user-profile-form.tsx`) — `@next/next/no-img-element` (8x), `react-hooks/incompatible-library` (5x), `react-hooks/set-state-in-effect` (4x), `@typescript-eslint/no-unused-vars` (2x), `react-hooks/refs` (1x).
- **Fix:** None — out of scope per SCOPE BOUNDARY (pre-existing, unrelated to this plan's files). Logged to `.planning/phases/LEXCV-86-infraestrutura-de-notifica-es-entidade-api-e-targeting/deferred-items.md`.
- **Files modified:** `deferred-items.md` only (documentation).
- **Committed in:** `f734ad4` (part of Task 2 commit).

---

**Total deviations:** 2 (1 blocking auto-fix, 1 out-of-scope discovery logged and deferred)
**Impact on plan:** No scope creep — the `pnpm install` was environment setup required to actually run the plan's own mandated verification step; the pre-existing lint debt was observed, not touched, and does not affect this plan's correctness or the `permissions.ts` change (confirmed zero findings in that file).

## Issues Encountered
- A transient/flaky `grep` result was observed twice during ad-hoc verification (bash `grep -c` returning `0` on a pattern that a repeat invocation and the dedicated `Grep` tool both confirmed was present multiple times — e.g. `"notificacoes:view"` in `DatabaseSeeder.java`, `hasAuthority('notificacoes:view')` in the new controller). Re-verified every affected check via the `Grep` tool and/or a repeated `grep` call before relying on any result; all final confirmations were consistent and positive. Not a code issue — appears to be tooling/filesystem flakiness in this environment, not a defect in the delivered code.
- My own initial doc-comment draft in `NotificacaoController.java` explained what NOT to do by naming the repository's save method directly, which would have tripped the plan's own automated verification grep (the one checking the controller never calls the repository's save method). Caught and reworded before the file was ever committed — the committed code never contained this issue.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- The full `/api/v1/notificacoes` REST surface now exists and compiles, scoped correctly by tenant + recipient, with `notificacoes:view` seeded and RBAC-visible — Phase 87 (event-triggered alerts) and Phase 89 (bell + `/notificacoes` page) can now build against a real, dual-scoped API instead of a stub.
- No blockers. Live HTTP round-trip / RBAC Settings-screen UI verification not performed in this session (static/compile-level verification only, consistent with prior phases in this milestone) — candidate for the milestone-level UAT tracking already used elsewhere in this project (see `PROJECT.md` Pending Todos for the established pattern).

---
*Phase: 86-infraestrutura-de-notificacoes-entidade-api-e-targeting*
*Completed: 2026-07-08*

## Self-Check: PASSED

- FOUND: `backend/src/main/java/com/lexcv/controllers/NotificacaoController.java`
- FOUND: `backend/src/main/java/com/lexcv/seed/DatabaseSeeder.java`
- FOUND: `backend/src/main/java/com/lexcv/controllers/AdminController.java`
- FOUND: `web/src/lib/permissions.ts`
- FOUND commit: `70da997` (Task 1)
- FOUND commit: `f734ad4` (Task 2)
- Verified: file scanned and cleaned of an accidental invisible soft-hyphen character (U+00AD) flagged by the write-hook injection scanner before this SUMMARY was committed; re-scanned clean for all common zero-width/BOM/bidi-control characters.
