---
phase: 87-alertas-de-eventos-fase-documento-atribui-o-e-parecer
fixed_at: 2026-07-09T11:23:58Z
review_path: .planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/87-REVIEW.md
iteration: 1
findings_in_scope: 5
fixed: 5
skipped: 0
status: all_fixed
---

# Phase 87: Code Review Fix Report

**Fixed at:** 2026-07-09T11:23:58Z
**Source review:** .planning/phases/LEXCV-87-alertas-de-eventos-fase-documento-atribui-o-e-parecer/87-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 5 (2 critical, 3 warning; IN-01 excluded — fix_scope is `critical_warning`)
- Fixed: 5
- Skipped: 0

Each fix was verified with a re-read of the modified region (Tier 1) plus a
real compile/typecheck (Tier 2): `mvn -o compile` for backend changes (offline,
cached deps, confirmed clean before and after every backend commit) and
`npx tsc --noEmit -p tsconfig.json` for frontend changes (baseline captured
before any edits: 3 pre-existing `vitest` module-resolution errors in
unrelated `*.test.ts` files, unchanged after every frontend commit). For
WR-02 the existing `NotificacaoServiceTest` was additionally re-run and
passes.

All fixes were made in an isolated git worktree/branch and fast-forwarded
onto `master` (see commit hashes below) — no rebase or history rewrite.

## Fixed Issues

### CR-01: Stale (deleted-user) responsavelId/team reference turns a successful write into a false 500

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`
**Commit:** `c529b57`
**Commit status:** fixed
**Applied fix:** Added `@Slf4j` to `ResourceController` and wrapped the three
previously-unguarded `notificacaoService.notificar*` calls (one in
`createProcessoFase`, two in `uploadDocumento` — the processo-responsável
branch and the cliente-team branch) in `try { ... } catch (IllegalArgumentException ex) { log.warn(...); }`,
exactly per the review's suggested pattern. A stale destinatario (referencing
a deleted `User`) now logs a warning instead of turning an already-persisted
fase/documento write into a false 500.

### CR-02: New "Reatribuir Responsável" control is non-functional for ADVOGADO (and any non-ADMIN processos:manage) users

**Files modified:** `backend/src/main/java/com/lexcv/dtos/UserSummaryResponse.java` (new), `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, `web/src/hooks/use-users.ts` (new), `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** `8122c7d`
**Commit status:** fixed: requires human verification
**Applied fix:** Added `GET /api/v1/users` (`ResourceController.listTenantUsers`),
returning a minimal `UserSummaryResponse {id, nome}` (deliberately excludes
roles/permissions/email/telefone to avoid over-disclosure to non-admins).
Added `web/src/hooks/use-users.ts` (`useTenantUsers`) and switched
`processos/[id]/page.tsx` off `useAdminUsers()`/`/admin/users` onto it —
this fixes not only `ReatribuirResponsavelControl` (the primary target) but
also the pre-existing "Novo Prazo" responsável selector and the
`userNomeById` name-lookup map on the same page, which shared the identical
root cause (both consumed the same `adminUsers` query, renamed to
`tenantUsers` at all 7 call sites in this file).

**Requires human verification because:** the permission gating this new
endpoint is a judgment call I made, not something the review specified
verbatim (it said "e.g. ... gated by a permission ADVOGADO already holds").
I chose `processos:view` — the same permission that already gates the whole
processo-detail page these pickers live on (confirmed via
`ProcessoDetailPage`'s `canViewProcessos` check and `DatabaseSeeder.seedRbac()`:
ASSISTENTE/TECNICO/ADVOGADO all hold `processos:view`), reasoning that no one
who can't already reach this page should gain new access, and that exposing
only `{id, nome}` company-directory-style data to any tenant member who can
view a processo is low-sensitivity. Please confirm `processos:view` is the
intended scope (vs., say, requiring `processos:manage` to match the
reassignment endpoint itself) before this ships.

### WR-01: No server-side no-op guard on reassignment — reassigning to the same user still fires "you were assigned" notifications

**Files modified:** `backend/src/main/java/com/lexcv/controllers/ResourceController.java`, `backend/src/main/java/com/lexcv/controllers/ParecerController.java`
**Commit:** `b3532c5`
**Commit status:** fixed: requires human verification
**Applied fix:** `atribuirResponsavel` now short-circuits with
`if (responsavelId.equals(processo.getResponsavelId())) return ResponseEntity.ok(processo);`
right after the existing tenant-ownership validation and before
mutate/save/notify, matching the review's suggested snippet exactly.
`ParecerController.atribuirAdvogado` (called out in the same finding's Issue
text as sharing the identical gap) got an analogous guard:
`if (advogadoId.equals(solicitacao.getAdvogadoId()) && "EM_ELABORACAO".equals(solicitacao.getStatus())) return ResponseEntity.ok(solicitacao);`.

**Requires human verification because:** the `ParecerController` guard's
exact condition is my own extension, not literally specified by the review
(which only gave a snippet for `ResourceController`). I added the status
check because `atribuirAdvogado` unconditionally forces status to
`EM_ELABORACAO` and writes an audit-log row regardless of whether
`advogadoId` changed — reassigning the same advogado while the status is
already `EM_ELABORACAO` is a true no-op, but reassigning the same advogado
while status is e.g. `EM_REVISAO` is a real transition (send back for
rework) that should still notify. Please confirm this compound condition
matches the intended workflow semantics.

### WR-02: notificarProcessoAtribuido's ADMIN broadcast is not internally null-safe

**Files modified:** `backend/src/main/java/com/lexcv/services/NotificacaoService.java`
**Commit:** `7bdcf7f`
**Commit status:** fixed: requires human verification
**Applied fix:** Added an early `if (responsavelId == null) { return; }` guard
at the top of `notificarProcessoAtribuido`, before any string construction or
the `criar(...)`/`notificarAdmins(...)` calls — matching the review's
suggested fix. Both existing call sites (`createProcesso`,
`atribuirResponsavel`) already externally guarded against null, so behavior
is unchanged for them; this only changes behavior for a hypothetical future
caller that invokes the method directly with a null `responsavelId`.

**Requires human verification because:** this is a null-handling/state-guard
change per the review's own classification, and while the existing
`notificarProcessoAtribuido_responsavelNaoNulo_...` unit test still passes
(confirming the non-null path is unaffected), no test exercises the new
null-path early-return itself.

### WR-03: Tab state only reads ?tab= once at mount — breaks the FASE_ENTRADA deep-link on same-route navigation

**Files modified:** `web/src/app/(dashboard)/processos/[id]/page.tsx`
**Commit:** `396ab8d`
**Commit status:** fixed: requires human verification
**Applied fix:** Added a `React.useEffect(() => { ... }, [searchParams])` in
`ProcessoDetailContent` that re-reads `searchParams.get("tab")` and calls
`setTab` when it differs from the current tab, in addition to the existing
mount-time `useState` initializer — matching the review's suggested fix
verbatim. Added `// eslint-disable-next-line react-hooks/exhaustive-deps`
on the dependency array, following the identical existing convention already
used elsewhere in this codebase (`pareceres/nova/page.tsx:75`) for
intentionally-partial `useEffect` dependency arrays.

**Requires human verification because:** this is a React state-sync fix
(exactly the "bad state handling" category) verified only via `tsc --noEmit`
(structural/type correctness) — there is no browser/e2e test in this
codebase to confirm the tab visually switches on a same-route `?tab=`
navigation.

---

_Fixed: 2026-07-09T11:23:58Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
