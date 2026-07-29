---
phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac
plan: 02
subsystem: ui
tags: [react, tanstack-query, shadcn-ui, badge, tooltip, rbac, gate-script, node-verify]

# Dependency graph
requires:
  - phase: 121-01
    provides: "Backend lock (method-level @PreAuthorize(\"hasRole('PLATAFORMA_ADMIN')\") on AdminController.updateRbac) that this frontend change stops exposing a broken action for"
  - phase: 120-frontend-consola-de-administra-o-de-tenants
    provides: "useMe()/Badge/Tooltip role-mirror idiom (dashboard-shell.tsx:91, plataforma/page.tsx:78-89) and the verify-consola-tenants.mjs gate-script style (stripComments, block-extraction-by-marker, PASS/FAIL-per-assertion) this plan reuses verbatim"
provides:
  - "RbacTab's 'Guardar Regras' button hidden from any non-PLATAFORMA_ADMIN viewer, replaced by a neutral outline Badge ('Gerido pela Plataforma') + keyboard-and-hover-accessible Tooltip explanation"
  - "pnpm verify:bloqueio-rbac — 11-assertion (A01-A11) source gate covering useMe() hook placement/ordering, isPlatformAdmin fail-closed derivation, conditional Save-button gating, Badge+Tooltip structural/copy contract, and 3 non-regression guards"
  - "Proven negative-control for the gate: hardcoding the render condition to `true` deterministically fails exactly A05/A06 while all 9 other assertions stay green"
affects: [121-03, 121-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Client-side role-mirror condition (`me.isFetched && (me.data?.roles?.includes(\"PLATAFORMA_ADMIN\") ?? false)`) reused verbatim from dashboard-shell.tsx/plataforma/page.tsx precedent, explicitly commented as a UX mirror, never the authorization boundary"
    - "Fourth executable source-gate script in the pnpm verify:* family (Phase 118 verify:limite-utilizadores-indicator, Phase 120 verify:consola-tenants, now Phase 121 verify:bloqueio-rbac) — same stripComments()+block-extraction-by-marker+PASS/FAIL-per-id technique"
    - "Negative-control proof as a required, written-up step for any gate claiming behavioral coverage — hardcoding the guarding condition to `true` is a smaller, more surgical mutation than deleting the whole alternate JSX branch, isolating exactly which assertions are sensitive to the regression being guarded against"

key-files:
  created:
    - web/scripts/verify-bloqueio-rbac.mjs
  modified:
    - web/src/app/(dashboard)/settings/page.tsx
    - web/package.json

key-decisions:
  - "Negative-proof technique: hardcoded the ternary's condition literal `isPlatformAdmin` to `true` (rather than deleting the whole alternate Badge/Tooltip JSX branch) — a smaller, surgical mutation that isolates exactly the 2 assertions tied to conditional gating (A05, A06), leaving the other 9 structure/copy/non-regression assertions unaffected, which is stronger evidence the gate's failures are precise rather than a blunt all-or-nothing signal"

requirements-completed: [ISOL-03]

# Metrics
duration: ~35min (continuation session)
completed: 2026-07-29
---

# Phase 121 Plan 02: Ocultar "Guardar Regras" no RBAC para ADMIN de Tenant + Gate `verify:bloqueio-rbac` Summary

**RbacTab's "Guardar Regras" button is now conditionally rendered only for `PLATAFORMA_ADMIN`; every tenant `ADMIN` instead sees a neutral `Badge` ("Gerido pela Plataforma") with a keyboard-and-hover `Tooltip`, backed by a new 11-assertion `pnpm verify:bloqueio-rbac` source gate whose sensitivity was proven with a real negative control.**

## Performance

- **Duration:** ~35 min (this continuation session: state verification, negative-proof, Task 2 commit, close-out)
- **Completed:** 2026-07-29T21:05:41Z
- **Tasks:** 2/2 completed
- **Files modified:** 3 (1 created, 2 modified)

**Session note:** This plan was executed across two sessions. Task 1 (`settings/page.tsx` conditional render) was implemented and committed (`5e6da543`) in a prior session that was then interrupted by a transient API error (529 Overloaded) partway through writing Task 2's gate script — not a defect in the approach. This continuation session found Task 2's artifacts (`verify-bloqueio-rbac.mjs`, the `package.json` script entry) already fully written and passing on disk but uncommitted, independently re-verified them, performed the plan-mandated negative-proof step (not yet done by the prior session), committed Task 2, and closed out the plan.

## Accomplishments

- Closed the frontend half of ISOL-03 (ROADMAP Phase 121 Success Criterion 4): a tenant `ADMIN` opening Definições → Controlo de Acesso (RBAC) no longer sees any Save action that would produce a confusing `403` — replaced by a self-explanatory, neutral `Badge`+`Tooltip`.
- Added `pnpm verify:bloqueio-rbac`, the fourth script in this project's executable-gate family, with 11 named source assertions (`A01`-`A11`) proving: `useMe()` is called before `RbacTab`'s first early return (Rules-of-Hooks safety), `isPlatformAdmin` is derived fail-closed (`me.isFetched` checked before the role), the Save button is inside the ternary's true-branch (not unconditional), the Badge/Tooltip structural contract (`TooltipTrigger asChild` → `<span tabIndex={0}>` → `<Badge>`, in that order) and its exact copy match the UI-SPEC verbatim, and 3 non-regression guards (`hasRbacManage` unchanged, `handleSave`/`useAdminRbac()` still present, the ADMIN-row-immutability guard in the permission matrix untouched).
- **Performed and recorded the required negative-proof** (see dedicated section below): confirmed the gate is not satisfiable by inspection alone — it has real teeth against the specific regression it is meant to catch.

## Task Commits

Each task was committed atomically:

1. **Task 1: Trocar o botão Guardar Regras por Badge + Tooltip para quem não é PLATAFORMA_ADMIN** - `5e6da543` (feat) — committed in the prior (interrupted) session
2. **Task 2: Gate executável pnpm verify:bloqueio-rbac com 11 asserções de origem** - `8e3422e` (feat) — committed this session

**Plan metadata:** _pending — committed together with this SUMMARY per the atomic close-out protocol_

## Negative-Proof (Gate Sensitivity Test)

Per the plan's explicit requirement ("prova de que o gate tem dentes"), the gate's sensitivity was tested with a real, temporary regression rather than accepted on inspection alone:

1. **Degrade:** In `RbacTab`, changed `{isPlatformAdmin ? (` to `{true ? (` — the Button now renders unconditionally, exactly the regression the gate exists to catch. This is a deliberately minimal mutation: it removes the literal substring `isPlatformAdmin ?` from the source without touching anything else (imports, the Badge/Tooltip alternate branch's JSX, hook placement, or any of the 3 non-regression guards).
2. **Ran `pnpm verify:bloqueio-rbac` against the degraded code — result:**
   ```
   PASS A01-usemme-dentro-do-rbactab
   PASS A02-hook-antes-do-primeiro-early-return
   PASS A03-isplatformadmin-com-isfetched-e-papel
   PASS A04-isfetched-antes-do-papel
   FAIL A05-guardar-regras-sob-condicao — no bloco, o indice de 'isPlatformAdmin ?' e menor que o indice de 'Guardar Regras' (o botao esta dentro do ramo verdadeiro do ternario, nao incondicional)
   FAIL A06-badge-outline-com-rotulo — no bloco, variant="outline" e "Gerido pela Plataforma" existem ambos, e o rotulo aparece depois de "isPlatformAdmin ?" (esta no ramo falso do ternario)
   PASS A07-span-tabindex-dentro-do-tooltiptrigger
   PASS A08-texto-exato-do-tooltip
   PASS A09-hasrbacmanage-inalterado
   PASS A10-handlesave-e-leitura-intactos
   PASS A11-matriz-admin-imutavel-inalterada
   ```
   Exit code 1, exactly 2 failures — **A05 fails for the precise, expected reason** (the button is no longer gated by the ternary condition), and **A06 fails as a direct logical consequence** (its own predicate also depends on locating `isPlatformAdmin ?`'s index). The other 9 assertions — which test unrelated concerns (hook ordering, the fail-closed derivation line itself, Badge/Tooltip structural ordering and exact copy, and the 3 non-regression guards) — correctly remained green, since none of those source facts were touched by this specific mutation. This is stronger evidence than an all-red failure would have been: it shows the gate's failures are precisely targeted at the regression, not a blunt, over-sensitive tripwire.
3. **Restored** the exact original line (`{isPlatformAdmin ? (`) immediately after capturing the result above.
4. **Re-ran `pnpm verify:bloqueio-rbac` post-restore — result:** all 11 `PASS`, exit code 0 (byte-identical output to the pre-degrade baseline run).
5. **Confirmed zero residual diff:** `git diff -- "web/src/app/(dashboard)/settings/page.tsx"` returned empty after the restore — the degrade-and-restore cycle left no trace on disk.

## Files Created/Modified

- `web/scripts/verify-bloqueio-rbac.mjs` - New. Pure Node (`node:fs/promises`, `node:path`, `node:url`), zero dependencies. Reads `settings/page.tsx` as text, strips comments (`stripComments()`, identical technique to `verify-consola-tenants.mjs`), extracts the `RbacTab` function block by marker (`function RbacTab()` → next `\nfunction `), and runs the 11 named assertions described above, printing `PASS <id>` / `FAIL <id> — <reason>` per assertion and exiting 0/1.
- `web/package.json` - Added `"verify:bloqueio-rbac": "node scripts/verify-bloqueio-rbac.mjs"` immediately after `verify:consola-tenants` in the `scripts` block. No other change (confirmed: 2 insertions, 1 deletion, the deletion being only the prior line's trailing-comma reformatting, not a real removal — `dependencies`/`devDependencies` untouched).
- `web/src/app/(dashboard)/settings/page.tsx` - (Task 1, committed in the prior session, re-verified unchanged this session) `RbacTab` now calls `useMe()` immediately after `useAdminRbac()` and derives `const isPlatformAdmin = me.isFetched && (me.data?.roles?.includes("PLATAFORMA_ADMIN") ?? false);` above the first early return. The `CardHeader`'s Save button is now wrapped in `{isPlatformAdmin ? (<Button>...) : (<Tooltip>...<Badge variant="outline">...Gerido pela Plataforma</Badge>...</Tooltip>)}`.

## Decisions Made

- Chose the hardcode-to-`true` technique for the negative-proof over deleting the whole alternate JSX branch — see `key-decisions` in frontmatter. This produced a precise 2-assertion failure (A05, A06) rather than a broad collapse across most of the 11 assertions, which is better evidence that the gate's per-assertion granularity is meaningful and not just a monolithic pass/fail signal.

## Deviations from Plan

None - plan executed exactly as written. Task 1's implementation (already committed) and Task 2's gate script (already fully written on disk from the prior session) both matched the plan's `<interfaces>`/`121-UI-SPEC.md` contracts exactly — no bugs, missing functionality, or blockers required Rule 1-4 intervention this session. The only outstanding plan requirement not yet satisfied at the start of this session was the negative-proof step and the commit/close-out, both completed above.

## Issues Encountered

- The prior execution session was interrupted by a transient API error (529 Overloaded) partway through Task 2, after the gate script and `package.json` entry had already been fully written to disk but before either was committed and before the negative-proof step was performed. This session independently re-verified (via `git status`/`git diff`) that the on-disk state exactly matched what was expected before proceeding, re-ran the gate to confirm the pre-existing 11/11 PASS baseline, then completed the remaining plan requirements (negative-proof, commit, SUMMARY, state updates). No code was redone; Task 1 was not touched.

## User Setup Required

None - no external service configuration required.

## Known Stubs

None. The Badge/Tooltip replacement content is fully static, correct copy (verbatim from `121-UI-SPEC.md`'s Copywriting Contract) — not a placeholder pending a future data source.

## Next Phase Readiness

- ISOL-03's frontend half (this plan) is complete: `RbacTab` no longer exposes a Save action to any non-`PLATAFORMA_ADMIN` viewer, satisfying ROADMAP Phase 121 Success Criterion 4 at the code level, verifiable by `pnpm verify:bloqueio-rbac` rather than only by reading the source.
- **Known, accepted limitation (not a bug, deliberately out of scope per `121-CONTEXT.md`/UI-SPEC Interaction Note 8):** a tenant `ADMIN` can still toggle a non-`ADMIN` role's checkbox in local component state (`localRolePermissions`), with no way to persist it now that the Save button is gone for them. This is the intended effect of hiding only the Save action (not also disabling matrix interactivity) and is unchanged by this plan.
- This gate's own header explicitly documents what it cannot prove (deferred to Plan 04's human checkpoint per `121-CONTEXT.md`/the plan's own `<action>`): that the Tooltip visually appears on real hover and keyboard focus, that the Badge renders with correct neutral colors in a real browser, and that the backend genuinely returns `403` to a tenant `ADMIN` calling `PUT /admin/rbac` directly (a separate backend test, already covered by Plan 01's `AdminControllerRbacAutorizacaoTest`).
- Plan-level verification fully re-confirmed this session: `pnpm verify:bloqueio-rbac` (11 PASS, exit 0), `pnpm lint` (exit 0, run twice — once as a pre-degrade baseline, once fresh post-restore), `pnpm build` (exit 0, all 25 routes generated successfully), `hasRbacManage` non-goal preserved (grep-confirmed), `git diff web/package.json` shows exactly the one intended script line.
- Ready for Plan 03 (ISOL-01 confirmation / ISOL-02 audit write-up) and Plan 04 (live-UAT checkpoint, including the real-browser Tooltip/Badge check and the live `403` confirmation) — both already exist as PLAN.md files in this phase directory.

---
*Phase: 121-fechar-suposi-es-de-tenant-nica-bloqueio-de-rbac*
*Completed: 2026-07-29*

## Self-Check: PASSED

- FOUND: `web/scripts/verify-bloqueio-rbac.mjs`
- FOUND: `web/src/app/(dashboard)/settings/page.tsx`
- FOUND: `web/package.json`
- FOUND: `5e6da543` (Task 1 commit, in `git log --oneline --all`)
- FOUND: `8e3422e` (Task 2 commit, in `git log --oneline --all`)
- FOUND: `verify:bloqueio-rbac` script entry present in `web/package.json`
- Re-confirmed: `pnpm verify:bloqueio-rbac` 11/11 PASS, exit 0; `pnpm lint` exit 0; `pnpm build` exit 0; negative-proof recorded above with exact PASS/FAIL output.
