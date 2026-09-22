---
phase: 127-pap-is-e-permiss-es-do-escrit-rio
plan: 08
subsystem: ui
tags: [rbac, next.js, react-query, structural-gate, multi-tenant]

# Dependency graph
requires:
  - phase: 127-05
    provides: "backend tenantRoleIds contract on POST/PUT /admin/users, 400 refusal of legacy roles field, tenant_role_ids on the read model"
  - phase: 127-06
    provides: "AdminUser/AdminUserSavePayload types, useOfficeRbac()/useAdminUsers() hooks"
  - phase: 127-07
    provides: "rewritten RbacTab (office role console) that this plan's gate now covers"
provides:
  - "UserManagementTab role picker assigns office roles by id (PAPEL-06), closing the frontend half of Decisao 6"
  - "verify:papeis-escritorio structural gate (18 assertions) proving the office CAN edit its own roles, replacing the retired verify:bloqueio-rbac (12 assertions proving it could NOT)"
  - "Recorded, pending live human verification checklist for the whole phase (task 3, checkpoint:human-verify)"
affects: [128, any-future-rbac-work]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Structural source gates (Node, zero deps) rewritten via git mv + full predicate rewrite to preserve history when a guarantee inverts, per 127-CONTEXT.md Decisao 2b"
    - "Rules-of-Hooks gate assertion generalised from a named hook to 'every use[A-Z]... call precedes the first early return', proven via stripComments + regex index comparison"

key-files:
  created:
    - web/scripts/verify-papeis-escritorio.mjs
  modified:
    - web/src/app/(dashboard)/settings/page.tsx
    - web/package.json
  deleted:
    - web/scripts/verify-bloqueio-rbac.mjs (renamed via git mv, history preserved)

key-decisions:
  - "Default role selection on 'Novo Utilizador' picks the office's first fetched papel id (officePapeis[0]?.id) rather than a hardcoded name, since the four-role union no longer exists"
  - "Gate assertion 'hooks-antes-do-primeiro-early-return' uses a generic /\\buse[A-Z]\\w*/ scan rather than a hardcoded hook-name list, so it also catches useState/useMemo/useForm calls, not just the office-RBAC mutation hooks"
  - "'guardar-alteracoes-incondicional' and 'matriz-sem-branch-readonly' assert absence of isPlatformAdmin/PLATAFORMA_ADMIN/isDisabled rather than presence of a specific new gate variable, since the rewritten button is gated only by the dirty-check (existeDiff), not any role/permission ternary"

requirements-completed: [PAPEL-04, PAPEL-06, CATL-04]

duration: ~50min (exact start not captured; see Issues Encountered)
completed: 2026-09-22
---

# Phase 127 Plan 08: Office role picker by id + rewritten structural gate Summary

**UserManagementTab now assigns the office's own roles by id via a dynamic checkbox list (PAPEL-06), and `verify:bloqueio-rbac` was renamed to `verify:papeis-escritorio` and rewritten from 12 assertions proving a read-only RBAC screen to 18 assertions proving the opposite: an editable, provenance-locked, id-keyed console.**

## Performance

- **Duration:** ~50 min (approximate — start timestamp was not captured at the very beginning of this run; completion is precise)
- **Completed:** 2026-09-22T11:05Z
- **Tasks:** 2 automated tasks executed and committed; 1 checkpoint:human-verify task recorded as pending (see below)
- **Files modified:** 3 (`web/src/app/(dashboard)/settings/page.tsx`, `web/package.json`, `web/scripts/verify-papeis-escritorio.mjs` created via rename)

## Accomplishments

- The user-management role picker is no longer a hardcoded `ADMIN/TECNICO/ADVOGADO/ASSISTENTE` button grid. It is a scrollable checkbox list over `useOfficeRbac().data?.papeis`, showing each role's current name plus a provenance badge ("Predefinido"/"Criado por si"), selecting by `id`, and submitting `tenantRoleIds` — matching the backend contract from Plan 05 exactly (the backend refuses any body containing `roles` with 400).
- The at-least-one-role guard (`toggleRole`) is preserved verbatim in behavior, only its comparison changed from name literals to ids.
- The gate `verify:bloqueio-rbac` (12 assertions, proved a read-only RBAC screen) was renamed via `git mv` to `verify:papeis-escritorio` and rewritten to 18 assertions proving the new, editable, provenance-locked console — the single most important one (`matriz-bloqueio-por-proveniencia`) proves no `"ADMIN"` name-literal lock remains anywhere in the RbacTab block.
- The Rules-of-Hooks protection (old `A02`) survives, generalised from "useMe() precedes the first early return" to "every hook call in the block precedes the first early return" — verified against all 16 hook call sites currently in `RbacTab` (5 query/mutation hooks, 6 `useState`, 4 `useMemo`, 1 `useForm`).
- The gate was demonstrated failing for real: reintroduced `role === "ADMIN"`, observed `FAIL matriz-bloqueio-por-proveniencia` with exit code 1, reverted (confirmed clean diff), re-ran green.

## Task Commits

Each automated task was committed atomically:

1. **Task 1: The user form assigns office roles by id** - `f56b4f07` (feat)
2. **Task 2: Rewrite the structural gate to prove the new guarantee** - `f83ca00c` (test)

Task 3 (`checkpoint:human-verify`) is not committed as code — it produced no file changes (verification-only). Its 12-step checklist is recorded below as **pending**.

**Plan metadata commit:** to be created after this SUMMARY is committed (see final_commit step).

## Files Created/Modified

- `web/src/app/(dashboard)/settings/page.tsx` — `UserManagementTab`'s role picker rewritten (checkbox list, id-keyed selection, `tenantRoleIds` payload, edit form seeds from `tenant_role_ids`); unused `Check` icon import removed.
- `web/package.json` — `verify:bloqueio-rbac` script renamed to `verify:papeis-escritorio`.
- `web/scripts/verify-papeis-escritorio.mjs` — renamed from `verify-bloqueio-rbac.mjs` (git history preserved) and rewritten: 18 assertions (2 survivors, 6 inversions of the retired guarantee, 10 new-guarantee assertions), plus a second marker-based extractor for the `UserManagementTab` block.

## Decisions Made

- **Default role on "Novo Utilizador":** picks `officePapeis[0]?.id` (falls back to empty array if the office somehow has zero roles yet) instead of a hardcoded `"ASSISTENTE"`, since that name is no longer guaranteed to exist or be stable.
- **`hooks-antes-do-primeiro-early-return` predicate:** implemented as a generic `/\buse[A-Z]\w*/g` scan over the comment-stripped `RbacTab` block rather than a hardcoded list of hook names, so it automatically covers `useState`/`useMemo`/`useForm` as well as the five office-RBAC query/mutation hooks — this is what makes the assertion "cannot be satisfied by accident" per the plan's framing, since any newly-added hook anywhere in the block after an early return would also be caught.
- **Absence-based predicates for `guardar-alteracoes-incondicional` and `matriz-sem-branch-readonly`:** rather than asserting presence of some specific new gating variable, both assert the *absence* of `isPlatformAdmin`/`PLATAFORMA_ADMIN`/`isDisabled` — because the rewritten Save button and matrix genuinely have no role-based gate left at all (only the dirty-check `existeDiff` and the floor-lock `isLockedFloor`), an absence check is the more precise, harder-to-fake proof than trying to match a specific replacement pattern.
- **Note-box normalisation includes stripping `<strong>`/`</strong>`:** the Copywriting Contract's first sentence is interrupted mid-sentence by a `</strong>` closing tag in the actual JSX (`<strong>O papel...escritório</strong> mantém...`), so the `nota-permanente-do-papel-protegido` predicate strips those tags before whitespace-normalising and comparing, extending (not replacing) the old `A08` normalisation technique.

## Deviations from Plan

None — plan executed exactly as written. No Rule 1/2/3 auto-fixes were needed; `UserManagementTab` already had no `@/server/mock-db` import or `Mock*` type usage before this plan started (Plan 07 had already cleaned the whole file), so that part of Task 1's action was a no-op confirmed by grep, not skipped.

## Issues Encountered

- **Start timestamp not captured.** The `record_start_time` step (`PLAN_START_TIME`/`PLAN_START_EPOCH`) was not run before beginning file reads. Duration above is an approximation based on the volume of work performed, not a computed delta. This does not affect correctness of any deliverable, only the precision of the reported duration metric.
- **`git diff --stat web/package.json ...`** must be run from the worktree root, not from `web/`, or `git` reports "unknown revision or path not in the working tree" (relative-path resolution against the wrong cwd). Not a defect, just a tooling note for future executors in this same worktree.

## User Setup Required

None — no external service configuration required.

## Pending Human Verification (Task 3 — checkpoint:human-verify)

This plan ends with a `checkpoint:human-verify` gate. Per the execution instructions for this run (autonomous milestone run, no human present to click through a browser), **this step was not exercised** — it requires a running backend against a reachable PostgreSQL/MinIO and a real browser session, neither of which this agent can drive interactively end-to-end with a human observer. All automated proxies for it were run and are green (`node web/scripts/verify-papeis-escritorio.mjs` exits 0; backend suite 352/352 green; frontend lint/build/test green). The 12-step live checklist from `127-08-PLAN.md` Task 3 is recorded here **verbatim, unexecuted, as a pending checklist** for a human (or a future live-verification agent) to walk through against a real database:

1. The tab shows the office's own roles as matrix columns, each with a provenance badge ("Predefinido" / "Criado por si") and a user-count badge. There is no "Gerido pela Plataforma" badge anywhere. — **NOT VERIFIED LIVE**
2. The administrator role's column carries a lock icon; its already-checked boxes are disabled, and an unchecked box in that same column can still be ticked. — **NOT VERIFIED LIVE**
3. Tick a box on a non-protected role. "Guardar Alterações" becomes enabled. Save — a success toast appears and the checkbox stays ticked after the list refreshes. — **NOT VERIFIED LIVE**
4. Without saving, tick a box on one role, then rename a DIFFERENT role via its kebab → Renomear. After the rename succeeds, the unsaved tick is still there. — **NOT VERIFIED LIVE**
5. Create a role ("Recepção") with a couple of permissions. It appears as a new column, named exactly as typed — not uppercased — with the "Criado por si" badge and "0 utilizadores". — **NOT VERIFIED LIVE**
6. Open the kebab on "Recepção" → Apagar Papel → confirm. The column disappears. — **NOT VERIFIED LIVE**
7. Assign "Recepção" to a user in Gestão de Utilizadores, save, return to Controlo de Acesso: its badge now reads "1 utilizador". Open its kebab: instead of a delete item there is a static explanatory line naming the count and pointing at Gestão de Utilizadores. — **NOT VERIFIED LIVE**
8. Open the kebab on the administrator role: it offers Renomear, and instead of a delete item shows "Papel protegido: administrador do escritório." — **NOT VERIFIED LIVE**
9. Rename the administrator role (e.g. to "Direção"). Then reload the page and confirm you can still open Definições, still see both tabs, still edit the matrix, and still manage users — this is the self-lockout check, and it is the single most important step here. — **NOT VERIFIED LIVE**
10. In Gestão de Utilizadores, open a user for editing: the "Papéis do Escritório" list shows the office's current role names (including any you renamed) with their provenance badges, and the user's current roles are pre-ticked. Try to untick the last remaining one — it must refuse. — **NOT VERIFIED LIVE**
11. Tab through the matrix with the keyboard: every checkbox is reachable and toggles with Space; the lock icon's tooltip opens on focus. — **NOT VERIFIED LIVE**
12. Compare the delete confirmation dialog side by side with the tenant-suspend dialog in `/plataforma` (light and dark) and confirm the destructive red reads as the same convention (UI-SPEC Visual-QA watch item 2). — **NOT VERIFIED LIVE**

**This checklist, along with the equivalent pending checklists from Phases 124 and 125, needs to be walked through by a human (or a dedicated live-verification session with a running dev stack) before Phase 127 can be considered fully closed out**, per this plan's `<checkpoint_handling>` instructions. Everything a source-level structural gate and automated test suite can prove about this phase is proven and green; browser-rendered behavior against a real database is not.

## Next Phase Readiness

- All 6 `verify:*` gates in `web/package.json` are green, including the rewritten `verify:papeis-escritorio` (18/18 PASS).
- `pnpm -C web exec tsc --noEmit`: clean. `pnpm -C web lint`: 0 errors (20 pre-existing warnings, none introduced by this plan). `pnpm -C web test`: 30/30 passed. `pnpm -C web build`: succeeds.
- Backend cross-check (`JAVA_HOME="/c/Program Files/Java/jdk-23" mvn -f backend/pom.xml test`): 352/352 passed, unchanged — this plan did not touch the backend.
- `git diff --stat web/pnpm-lock.yaml`: empty — no dependency drift.
- Phase 127 is code-complete and gate-complete. The one open item is the live human verification recorded above (all 12 steps), which is the phase's actual closing condition per `127-08-PLAN.md`'s `<success_criteria>`.

## Self-Check: PASSED

- `web/src/app/(dashboard)/settings/page.tsx`: FOUND
- `web/package.json`: FOUND
- `web/scripts/verify-papeis-escritorio.mjs`: FOUND
- `web/scripts/verify-bloqueio-rbac.mjs`: correctly ABSENT (renamed)
- Commit `f56b4f07`: FOUND in `git log --oneline --all`
- Commit `f83ca00c`: FOUND in `git log --oneline --all`

---
*Phase: 127-pap-is-e-permiss-es-do-escrit-rio*
*Completed: 2026-09-22*
