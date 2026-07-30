---
phase: 120-frontend-consola-de-administra-o-de-tenants
plan: 05
subsystem: ui
tags: [nextjs, react, tanstack-query, typescript, rbac, multi-tenant, verification-gate]

# Dependency graph
requires:
  - phase: 120-03
    provides: "useTenantsAdmin/useCreateTenant/useUpdateTenant/useSetTenantAtivo hooks and TenantAdminSummary/TenantUpdateRequest/TenantAtivoRequest types this plan's page.tsx wires up end-to-end"
  - phase: 120-04
    provides: "columns({ onEdit, onToggleAtivo })/TENANT_RESERVADO factory and CriarTenantPanel presentational form this plan composes into the actual route"
provides:
  - "web/src/app/(dashboard)/plataforma/page.tsx: the composed /plataforma screen -- RBAC guard, searchable tenant list (desktop DataTable + mobile cards), inline Criar Tenant panel toggle, Editar Tenant Dialog (plano/limiteUtilizadores), and a single AlertDialog whose title/description/button color switch on tenant.ativo for Suspender vs. Reativar"
  - "web/scripts/verify-consola-tenants.mjs + package.json's verify:consola-tenants script: a 10-assertion, dependency-free structural gate over the 4 consola files, counter-proof confirmed (forced tabIndex removal produces FAIL + exit 1, revert restores PASS + exit 0)"
  - "REQUIREMENTS.md PROV-02 and PROV-05 marked Complete -- the last 2 of this milestone's PROV requirements to close, since this is the first plan where /plataforma is an actual reachable route with a working suspend UI"
affects: [120-06]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Dialog/AlertDialog local form state reset via the existing null<->tenant conditional render (no useEffect, no manual key/epoch counter) -- the dialog-open state always cycles through null between any two opens, so `{tenant ? <Form tenant={tenant} /> : null}` already fully unmounts/remounts on every open, including reopening the same tenant after Cancel"
    - "A single AlertDialog instance branches its entire content (title/description/button label/button color) on tenant.ativo, rather than two separate AlertDialog trees -- one controlled-open boolean, one content component"

key-files:
  created:
    - web/src/app/(dashboard)/plataforma/page.tsx
    - web/scripts/verify-consola-tenants.mjs
  modified:
    - web/package.json

key-decisions:
  - "Editar Tenant Dialog's local form state (plano/limite) resets on every open via the existing null-to-tenant conditional render, not via useEffect or a manual key/epoch counter -- see Decisions Made"
  - "Plan 05 is the plan that runs requirements mark-complete for PROV-02 and PROV-05, closing them for the first time in this milestone -- see Decisions Made"

requirements-completed: [PROV-02, PROV-03, PROV-04, PROV-05]

# Metrics
duration: 30min
completed: 2026-07-29
---

# Phase 120 Plan 05: Compose the Tenant Admin Console Screen + Executable Verification Gate Summary

**`/plataforma` assembled from Plan 03/04 pieces into one screen (RBAC guard, searchable list, inline create panel, Editar Dialog, Suspender/Reativar AlertDialog) plus a 10-assertion dependency-free structural gate (`verify:consola-tenants`) with a confirmed counter-proof**

## Performance

- **Duration:** 30 min
- **Started:** 2026-07-29T13:44:00Z
- **Completed:** 2026-07-29T14:14:00Z
- **Tasks:** 3 completed
- **Files modified:** 3 (2 created, 1 modified)

## Accomplishments

- `web/src/app/(dashboard)/plataforma/page.tsx` created (25th route in `pnpm build`'s output, up from Plan 04's 24-route baseline): page-level RBAC mirror (`me.isFetched && !me.data?.roles?.includes("PLATAFORMA_ADMIN")` → `AccessDeniedState`), a `Tenants Registados` Card with client-side name search feeding both the shared `DataTable` (desktop, `hidden md:block`) and stacked cards (mobile, `md:hidden`, including its own `tabIndex={0}`-wrapped disabled-Suspender guard for the reserved "LexCV" row -- not just the desktop columns cell), and the `CriarTenantPanel` inline-toggle wired to `useCreateTenant` with cache invalidation and a success toast, zero `window.location.reload`
- `EditarTenantDialog`/`EditarTenantForm`: a `Dialog` (not `AlertDialog`) with exactly 2 fields -- Plano (`NativeSelect`, 3 raw enum options) and Limite de Utilizadores (`Input type="number"`, empty = `null` = sem limite, client-side `>= 1` integer validation before ever calling the API) -- committed together via a single `PUT /platform/tenants/{id}`
- A single `AlertDialog` whose `AlteracaoEstadoConteudo` content branches entirely on `tenant.ativo`: Suspender (`bg-red-600 hover:bg-red-700 text-white`, "esta ação bloqueia de imediato... incluindo sessões já iniciadas") vs. Reativar (`bg-emerald-600 hover:bg-emerald-700 text-white`, "recuperam o acesso de imediato") -- never folded into the Editar Dialog, confirmed by the gate's own `dialogos-separados` assertion
- `web/scripts/verify-consola-tenants.mjs` (Node-only, zero dependencies, `stripComments` + block/marker-slicing techniques copied from the Phase 118 precedent) implements all 10 required assertions against the 4 consola files; registered as `verify:consola-tenants` in `package.json` immediately after `verify:limite-utilizadores`; the required counter-proof was executed live (temporarily removing `tabIndex={0}` from `columns.tsx`'s reserved-tenant guard span produced `FAIL tooltip-span-wrapper` + exit 1; reverting restored all 10 `PASS` + exit 0, with `git diff --stat` confirming `columns.tsx` carries zero residual diff)
- `npx tsc --noEmit` (3 pre-existing, unrelated `vitest`-module errors only, documented since Phase 120-03/04), `pnpm lint` (0 errors), and `pnpm build` (25 routes) all pass cleanly after every one of the 3 tasks; `package.json`/`pnpm-lock.yaml` dependency counts confirmed identical to `git show HEAD` (28 = 28) -- zero new dependencies, only the one new `scripts` entry

## Task Commits

Each task was committed atomically:

1. **Task 1: Ecra /plataforma — guard, lista, pesquisa, cards mobile e painel de criacao** - `1f87be3` (feat)
2. **Task 2: Dialog de edicao e AlertDialogs de suspender/reativar** - `e46bf0b` (feat)
3. **Task 3: Gate executavel verify:consola-tenants** - `85d1141` (test)

**Plan metadata:** (recorded in the next commit, after this SUMMARY)

## Files Created/Modified

- `web/src/app/(dashboard)/plataforma/page.tsx` (474 lines) - `PlataformaPage` (RBAC guard) → `PlataformaPageContent` (list/search/create-panel/dialog state) → `EditarTenantDialog`/`EditarTenantForm` (edit dialog) → `AlteracaoEstadoConteudo` (shared suspend/reactivate AlertDialog content)
- `web/scripts/verify-consola-tenants.mjs` (232 lines) - 10-assertion structural gate; top-of-file comment enumerates the 3 claims it cannot prove (tooltip actually renders, suspension actually cuts a live session, badges render with the right colors) -- reserved for the Plan 06 human checkpoint
- `web/package.json` - `+1` line: `"verify:consola-tenants": "node scripts/verify-consola-tenants.mjs"`, no dependency changes

## Decisions Made

- **Editar Tenant Dialog form-state reset without `useEffect`:** `tenantEmEdicao` always transitions through `null` between any two "Editar" clicks (the Dialog is modal and must close before a different row can be selected), so `{tenant ? <EditarTenantForm tenant={tenant} /> : null}` inside the always-mounted `EditarTenantDialog`/`Dialog` shell already fully unmounts and remounts `EditarTenantForm` on every single open -- including reopening the *same* tenant right after a Cancel. This gives fresh `useState(tenant.plano)`/`useState(tenant.limiteUtilizadores)` initial values sourced from the just-selected tenant with no risk of the `react-hooks/set-state-in-effect` antipattern this codebase has repeatedly hit and fixed elsewhere (dashboard-shell.tsx's own render-time `prevPathname` adjustment is the established local precedent for preferring this over an effect). No `key` prop or open-counter was needed once this was traced through -- simpler than the plan's own read-first analog (`financeiro/[id]/page.tsx`'s `useEffect(() => { editForm.reset(...) }, [honorario.data])`), because that analog's form stays mounted across data refetches of the *same* record, whereas this dialog's content is only ever mounted while a tenant is selected.
- **Ran `requirements mark-complete` for PROV-02/03/04/05** (all 4 from this plan's own frontmatter): `PROV-03`/`PROV-04` were already `[x]` Complete (set by Plan 02) and the tool correctly reported them as `already_complete` (no-op, no regression risk) rather than double-registering them; `PROV-02`/`PROV-05` were genuinely newly closed by this plan -- the first plan where `/plataforma` is an actual reachable route (closing PROV-02's "num ecrã interno" requirement) with a working Suspender/Reativar AlertDialog (closing PROV-05). This is the intended completion Plans 02/03/04 each explicitly deferred to, not a repeat of the premature-completion regression (commit `cd45fcf9`) those plans were careful to avoid.
- **Reused the exact reserved-tenant guard phrase and Tooltip+`<span tabIndex={0}>` composition in the mobile card block, verbatim from `columns.tsx` (Plan 04)**, rather than inventing separate mobile copy -- the plan's own Task 1 action explicitly requires the guard to exist in both presentation modes, and duplicating the exact same string keeps the two surfaces provably in sync (the gate's `guarda-tenant-reservado` assertion only checks `columns.tsx`, but a human reading both files side by side would immediately notice any drift).
- **Added a mobile-only "Sem resultados para os filtros aplicados." fallback** when the client-side name filter matches zero tenants, reusing the shared `DataTable`'s own built-in fallback copy verbatim. UI-SPEC frames the zero-match-after-search case as already covered "for free" by `DataTable`, but that coverage is desktop-only (`DataTable` renders `hidden md:block`); without this addition, searching to zero matches on a phone would render a silently blank card list instead of an explanatory message. No new copy was invented -- the exact existing string was reused for mobile parity.
- **Manually corrected `REQUIREMENTS.md`'s traceability table row for `PROV-05`** after `requirements mark-complete` ran: the automated tool updated the top checklist's `PROV-05` checkbox to `[x]` correctly, but left the traceability table's `Status` cell with its old, longer "Pending (mecanismo de aplicação backend pronto...)" annotation instead of replacing it with a bare `Complete` (it did correctly simplify `PROV-02`'s row). Fixed by hand to match the plain `Complete` style already used by every other closed row in that table (`PROV-01`, `PROV-03`, `PROV-04`, `PROV-06`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Mobile card list had no fallback message for zero search results**
- **Found during:** Task 1 (mobile card block implementation)
- **Issue:** The plan's own UI-SPEC explicitly reasons that the zero-match-after-search case is covered "for free" by the shared `DataTable`'s built-in `"Sem resultados para os filtros aplicados."` fallback -- but that component only renders in the `hidden md:block` (desktop) branch. The `md:hidden` (mobile) branch had no equivalent, so searching to zero matches on a phone would silently render an empty white area with no explanation, which is a usability correctness gap for the exact feature ("a pesquisa por nome filtra a lista... em desktop e em mobile") this plan's own `must_haves.truths` requires.
- **Fix:** Added a conditional fallback inside the `md:hidden` block, reusing the identical existing copy from `data-table.tsx` (`"Sem resultados para os filtros aplicados."`) rather than inventing new wording.
- **Files modified:** `web/src/app/(dashboard)/plataforma/page.tsx`
- **Verification:** Manually traced the render path for `tenantsFiltrados.length === 0`; confirmed the fallback text matches `web/src/components/shared/data-table/data-table.tsx`'s own fallback string verbatim, so both presentation modes now behave identically on a zero-match search.
- **Committed in:** `1f87be3` (Task 1 commit)

**2. [Rule 1 - Bug] `requirements mark-complete`'s automated traceability-table rewrite left a stale annotation on the PROV-05 row**
- **Found during:** Post-Task-3 state/requirements updates
- **Issue:** After running `requirements mark-complete PROV-02 PROV-03 PROV-04 PROV-05`, the top checklist correctly flipped `PROV-02`/`PROV-05` to `[x]`, but the traceability table's `PROV-05` row kept its old, longer "Pending (mecanismo de aplicação backend pronto — Plan 01 — mas a capacidade de um administrador de plataforma efetivamente suspender via endpoint/UI só fecha com os Plans 02/05)" text in the Status column instead of being replaced with a plain `Complete`, inconsistent with every other closed row in the same table (including `PROV-02`, which the same tool call DID simplify correctly).
- **Fix:** Manually edited the `PROV-05` row's Status cell to `Complete`, matching the table's established style.
- **Files modified:** `.planning/REQUIREMENTS.md`
- **Verification:** Re-read the file after the edit; confirmed `PROV-05 | Phase 120 | Complete` now reads identically in shape to the `PROV-01`/`PROV-02`/`PROV-03`/`PROV-04`/`PROV-06` rows.
- **Committed in:** final metadata commit (this plan's docs commit, alongside SUMMARY/STATE/ROADMAP)

---

**Total deviations:** 2 auto-fixed (1 Rule 2 - missing critical UX coverage on mobile, 1 Rule 1 - bug in tooling output requiring a manual correction).
**Impact on plan:** Both fixes are small, additive, and non-functional to the plan's core deliverable -- no scope creep, no architectural change.

## Issues Encountered

- **Pre-existing, out-of-scope:** raw `cd web && npx tsc --noEmit -p tsconfig.json` reports the same 3 errors (`Cannot find module 'vitest'`) documented in `120-03-SUMMARY.md`/`120-04-SUMMARY.md`, in 3 "durable spec" files committed across Phases 74/83/97, deliberately without `vitest` installed. Neither file this plan touches was involved; the count was 3 before Task 1 and after every subsequent task. `pnpm build` (the authoritative gate per this plan's own `<verification>` section) passed cleanly (exit 0, 25 routes) both before this plan started and after all 3 tasks. Not fixed (installing `vitest` reverses a standing, explicit project decision, out of scope here).
- **`gsd-sdk query state.record-metric`/`state.add-decision`'s documented positional-argument form did not work as literally written** in the executor role prompt's own `<state_updates>` step (`gsd-sdk query state.record-metric "${PHASE}" "${PLAN}" "${DURATION}" "${TASK_COUNT}" "${FILE_COUNT}"` failed with `"phase, plan, and duration required"`); the flag-based form documented in `execute-plan.md` (`--phase --plan --duration --tasks --files`, and `--summary-file`/`--rationale-file` for decisions) worked correctly. Also, `state.add-decision`'s own output auto-prepends `- [Phase {N}]: ` to the summary text -- including that same literal prefix in my own summary-file input produced a doubled `[Phase 120]: [Phase 120]: ...` line, caught and hand-corrected in `STATE.md` immediately after. Neither is a defect in this plan's own deliverable; both are tooling-usage notes for future executor sessions.
- **`gsd-sdk query state.update-progress` returned `{"updated": false, "reason": "Progress field not found in STATE.md"}`** -- this project's `STATE.md` has been trimmed extensively across multiple milestone closes (per its own repeated "trimmed per STATE.md size constraint" notes) and apparently no longer carries whatever literal progress-bar text pattern this command looks for in the document body (the YAML frontmatter's own `progress:` block is unaffected and remains accurate via `state.advance-plan`/`state.record-metric`). Not fixed -- restoring a removed progress-bar convention is out of scope for a frontend composition plan; `Current Position`/`Performance Metrics` remain correct via the commands that did succeed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 06 (`120-06-PLAN.md`, `autonomous: false`) is the phase's final plan: a live human checkpoint against real backend+frontend confirming the one claim no source-level gate can prove -- that suspending a 2nd real tenant cuts an already-open session on that tenant's very next request, without logout or re-login (`JwtAuthenticationFilter`'s per-request `Tenant.ativo` re-check, Phase 120 Plan 01). This plan's own `verify:consola-tenants` gate explicitly documents (top-of-file comment) that it cannot prove this, nor that the tooltip visibly renders, nor that badges render with the right colors -- exactly the 3 items Plan 06 exists to close live.
- `REQUIREMENTS.md`'s `PROV-02`/`PROV-03`/`PROV-04`/`PROV-05` are now all `[x]` Complete, with the traceability table's `PROV-05` row corrected to match. `PROV-01`/`PROV-06` (Phase 119) were already complete. All 6 PROV requirements for this milestone are now closed; only `ISOL-*` (Phase 121), `UTIL-01` (Phase 122) remain open in the v2.16 roadmap.
- `/plataforma` is reachable end-to-end from a fresh login as `plataforma@lexcv.cv` (seeded in Phase 119): the "Plataforma" nav item (Phase 120-03), the list/search/create screen and the edit/suspend/reactivate dialogs (this plan) are all wired to the real backend endpoints (Phase 120-01/02). Plan 06 needs a running backend + Postgres + web frontend, plus the ability to provision a genuine 2nd tenant through the UI itself (the exact live-fire test this milestone has been building toward).

---
*Phase: 120-frontend-consola-de-administra-o-de-tenants*
*Completed: 2026-07-29*

## Self-Check: PASSED

Both claimed created files confirmed present on disk (`web/src/app/(dashboard)/plataforma/page.tsx`, `web/scripts/verify-consola-tenants.mjs`), plus this SUMMARY.md. All 3 claimed commit hashes (`1f87be3`, `e46bf0b`, `85d1141`) confirmed present in `git log --oneline --all`. Re-ran `pnpm verify:consola-tenants` (10/10 PASS, exit 0), `pnpm lint` (0 errors, exit 0), and `pnpm build` (25 routes, exit 0) -- all clean. `git diff --stat -- web/package.json` re-confirmed only the one new `scripts` entry; dependency count re-confirmed identical to `git show HEAD` (28 = 28).
