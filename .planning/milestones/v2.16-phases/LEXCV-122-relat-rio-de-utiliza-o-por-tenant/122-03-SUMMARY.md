---
phase: 122-relat-rio-de-utiliza-o-por-tenant
plan: 03
subsystem: ui
tags: [nextjs, react, cardheader, structural-gate, tenant-admin, node-verify-script]

# Dependency graph
requires:
  - phase: 122-01-relat-rio-de-utiliza-o-por-tenant
    provides: "/plataforma/relatorio route (page.tsx + columns.tsx) that this plan's link points to and this plan's gate asserts against"
  - phase: 120-consola-de-administra-o-de-tenants
    provides: "verify-consola-tenants.mjs's stripComments/sliceBetweenMarkers helpers and reporting-loop shape, reused verbatim for the new gate; processos/page.tsx's secondary-before-primary two-button CardHeader precedent"
provides:
  - "web/src/app/(dashboard)/plataforma/page.tsx — CardHeader now has a 2-button cluster: outline 'Ver Relatório' (new, links to /plataforma/relatorio) before the unchanged accent 'Criar Tenant'"
  - "web/scripts/verify-relatorio-utilizacao.mjs — 15-assertion Node-only structural gate covering the report route, its columns, and the new entry link/non-regression"
  - "web/package.json — verify:relatorio-utilizacao script entry"
  - "UTIL-01 closed in REQUIREMENTS.md — the report is now reachable with one click from /plataforma"
affects: [122-04 (live UAT of the reachable report), 123 (ISOL-04 dedicated isolation audit names "o relatório de utilização" as one of its three audit targets)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "5th Node-only structural verify script in this codebase (after verify-juizo-origem-roundtrip, verify-limite-utilizadores-indicator, verify-consola-tenants, verify-bloqueio-rbac), same stripComments/sliceBetweenMarkers/reporting-loop shape reused verbatim"
    - "Secondary-before-primary two-button CardHeader cluster (outline button first, accent button second, both wrapped in a shared flex gap-2 div) — same ordering convention as processos/page.tsx's Dashboard/Novo Processo pair"

key-files:
  created:
    - web/scripts/verify-relatorio-utilizacao.mjs
  modified:
    - web/src/app/(dashboard)/plataforma/page.tsx
    - web/package.json

key-decisions:
  - "CardHeader gained flex-wrap + gap-3 (not just a second button) — Phase 120 never needed to wrap this header because it only ever held one button; 3 elements (title block + 2 buttons) now need to be able to break onto their own line on narrow viewports"
  - "Ver Relatório button uses plain outline variant, not accent-blue — keeps Criar Tenant as the CardHeader's sole focal point, per Phase 120's already-established single-accent rule"
  - "verify-relatorio-utilizacao.mjs's 15 assertions read all 4 target files (relatorio/columns.tsx, relatorio/page.tsx, plataforma/page.tsx, dashboard-shell.tsx) through the same stripComments normalization before any predicate runs, exactly mirroring verify-consola-tenants.mjs, so a comment mentioning a searched token can never produce a false pass"
  - "Negative-proof used 3 minimal one-line regressions (add a .filter( call, swap font-semibold->font-bold, add a stray string literal) rather than deleting blocks — each isolated exactly 1 of 15 assertions to FAIL, confirming gate precision rather than an all-or-nothing signal"
  - "requirements mark-complete run for UTIL-01 in this plan (not 01 or 02) — this is the first plan in Phase 122 that makes the report genuinely reachable by a real user action (one click from /plataforma), which is what UTIL-01's own wording requires beyond the route existing or a backend test passing"

patterns-established:
  - "Any future secondary/report-style entry point added to an existing CardHeader with a single existing button should follow this same shape: wrap both buttons in a flex items-center gap-2 div, add flex-wrap + gap-3 to the CardHeader itself, secondary-before-primary."

requirements-completed: [UTIL-01]

# Metrics
duration: ~23min
completed: 2026-07-30
---

# Phase 122 Plan 03: Ver Relatório Entry Link + Structural Verify Gate Summary

**Wired a one-click "Ver Relatório" entry point into `/plataforma`'s CardHeader and shipped `verify:relatorio-utilizacao`, a 15-assertion Node-only structural gate proving the report's reachability, column shape, and non-regression against Phase 120's nav/CTA guarantees — closing `UTIL-01`.**

## Performance

- **Duration:** ~23 min
- **Started:** ~2026-07-30T03:22:00Z
- **Completed:** 2026-07-30T03:45:42Z
- **Tasks:** 2 completed
- **Files modified:** 3 (1 new, 2 modified)

## Accomplishments

- `/plataforma/relatorio` (built by Plan 01) is now reachable with one click: a neutral `outline` "Ver Relatório" button sits to the left of the unchanged "Criar Tenant" CTA in `/plataforma`'s `CardHeader`, matching `processos/page.tsx`'s exact secondary-before-primary two-button precedent.
- `CardHeader`'s `className` gained `flex-wrap` + `gap-3` so the title block and the now-two-button cluster can wrap onto their own line on narrow viewports — a consideration Phase 120 never had to solve with only one button.
- Zero regressions on the existing Phase 120 gate: `pnpm verify:consola-tenants` still passes all 12 of its own assertions against this same, now-edited file.
- Shipped `web/scripts/verify-relatorio-utilizacao.mjs`, the codebase's 5th Node-only structural verify script, with 15 assertions spanning `relatorio/columns.tsx`, `relatorio/page.tsx`, `plataforma/page.tsx`, and a non-regression check against `dashboard-shell.tsx`.
- Proved the new gate fails with precision, not just as a binary signal: 3 independent one-line regressions (chosen from 3 different assertion families) each isolated exactly 1 of 15 assertions to `FAIL` while the other 14 stayed `PASS`, then were fully reverted via `git checkout --`.
- `UTIL-01` is now closed in `REQUIREMENTS.md` — the report is genuinely reachable, not just built.

## Task Commits

Each task was committed atomically:

1. **Task 1: Acrescentar o botão Ver Relatório ao CardHeader de /plataforma** - `151f4a2a` (feat)
2. **Task 2: Criar o gate de origem verify-relatorio-utilizacao.mjs com 15 assertions** - `8b7ed743` (feat)

**Plan metadata:** committed together with this SUMMARY, STATE.md, ROADMAP.md, and REQUIREMENTS.md (see final commit in this session).

## Files Created/Modified

- `web/src/app/(dashboard)/plataforma/page.tsx` (modified) — `CardHeader` now wraps a `flex items-center gap-2` div holding the new outline "Ver Relatório" `Link` button (icon `FileChartColumn`) before the unchanged "Criar Tenant" button; `CardHeader` className gained `flex-wrap gap-3`; new imports `Link` (`next/link`) and `FileChartColumn` (`lucide-react`, alphabetically ordered into the existing icon import).
- `web/scripts/verify-relatorio-utilizacao.mjs` (new, 15 assertions) — Node-only (`node:fs/promises`, `node:path`, `node:url`), zero dependencies; reuses `stripComments`/`sliceBetweenMarkers` verbatim from `verify-consola-tenants.mjs`; reads and normalizes all 4 target files once, then runs 15 independent predicates, printing `PASS <id>` / `FAIL <id> — <motivo>` and exiting 1 on any failure.
- `web/package.json` (modified) — added `"verify:relatorio-utilizacao": "node scripts/verify-relatorio-utilizacao.mjs"` after `verify:bloqueio-rbac`; zero changes to `dependencies`/`devDependencies`; `pnpm-lock.yaml` untouched (confirmed via `git status --short`).

## Decisions Made

See `key-decisions` in frontmatter for the full list with rationale. The most consequential: reserving the `outline` variant (not accent-blue) for "Ver Relatório" to preserve "Criar Tenant" as the CardHeader's sole focal point (Phase 120's single-accent rule), and running `requirements mark-complete` for `UTIL-01` in this plan specifically — the first point in Phase 122 where the report is reachable by an actual click, not just present as an unlinked route (Plan 01) or covered by a backend test alone (Plan 02).

## Verification Results

### The 15 new assertions (`pnpm verify:relatorio-utilizacao`)

| # | Assertion ID | Verdict |
|---|---------------|---------|
| 1 | `colunas-array-estatico` | PASS |
| 2 | `colunas-quatro-ids` | PASS |
| 3 | `colunas-sem-celula-de-acoes` | PASS |
| 4 | `colunas-nome-reservado-importado` | PASS |
| 5 | `utilizadores-sem-recalculo` | PASS |
| 6 | `estado-badge-suspenso` | PASS |
| 7 | `relatorio-guard-falha-fechado` | PASS |
| 8 | `relatorio-sem-filtro-de-estado` | PASS |
| 9 | `relatorio-h1-semibold` | PASS |
| 10 | `relatorio-mobile-mostra-utilizadores` | PASS |
| 11 | `relatorio-sem-mutacoes` | PASS |
| 12 | `entrada-ver-relatorio` | PASS |
| 13 | `entrada-ordem-e-criar-tenant-intocado` | PASS |
| 14 | `sem-segundo-item-de-nav` | PASS |
| 15 | `sem-export-csv-nem-kpis` | PASS |

15/15 PASS, 0 FAIL, exit code 0.

### Negative proof (mandatory precision check)

| Assertion targeted | Regression introduced | Result | Reversion confirmed |
|---|---|---|---|
| `utilizadores-sem-recalculo` (A05) | Added `const regressaoTesteA05 = [tenant].filter((t) => t.id);` inside the `utilizadores` cell in `relatorio/columns.tsx` | Only `utilizadores-sem-recalculo` → `FAIL`; other 14 stayed `PASS` | `git checkout -- relatorio/columns.tsx`; file byte-identical to pre-regression state, confirmed via `git status --short` (clean) |
| `relatorio-h1-semibold` (A09) | Swapped `font-semibold` → `font-bold` on the `<h1>` in `relatorio/page.tsx` | Only `relatorio-h1-semibold` → `FAIL`; other 14 stayed `PASS` | `git checkout -- relatorio/page.tsx`; clean |
| `sem-segundo-item-de-nav` (A14) | Added `const regressaoTesteA14 = "/plataforma/relatorio";` to `dashboard-shell.tsx` | Only `sem-segundo-item-de-nav` → `FAIL`; other 14 stayed `PASS` | `git checkout -- dashboard-shell.tsx`; clean |

After all 3 reversions: `git status --short` on the 3 affected files produced no output (clean tree), and `pnpm verify:relatorio-utilizacao` re-ran to 15/15 `PASS` again.

### Pre-existing gates (non-regression)

| Gate | Result |
|---|---|
| `verify:juizo-origem` | PASS (exit 0) |
| `verify:limite-utilizadores` | 8/8 PASS (exit 0) |
| `verify:consola-tenants` | 12/12 PASS (exit 0) — proves Task 1's edit to `plataforma/page.tsx` broke none of Phase 120's own assertions on this same file |
| `verify:bloqueio-rbac` | 12/12 PASS (exit 0) |

### Project-wide checks

| Check | Result |
|---|---|
| `pnpm lint` | 0 errors, 18 pre-existing warnings (unrelated files, out of this plan's scope) — exit 0 |
| `pnpm exec tsc --noEmit` | "TypeScript: No errors found" — exit 0 |
| `pnpm test` (vitest) | 3 files, 20/20 tests passed |
| `pnpm build` | Compiled successfully; build's own route table lists `○ /plataforma/relatorio` as a static route |

### `CardHeader` conceptual diff

**Before** (single button, no wrap):
```tsx
<CardHeader className="flex flex-row items-center justify-between space-y-0">
  <div>...</div>
  <Button onClick={() => setIsFormOpen(true)} className="bg-blue-600 ...">
    <Plus className="h-4 w-4" /> Criar Tenant
  </Button>
</CardHeader>
```

**After** (2-button cluster, wrap-capable):
```tsx
<CardHeader className="flex flex-row flex-wrap items-center justify-between gap-3 space-y-0">
  <div>...</div>
  <div className="flex items-center gap-2">
    <Button asChild variant="outline" className="text-xs py-1.5 px-3 h-auto flex items-center gap-1.5">
      <Link href="/plataforma/relatorio">
        <FileChartColumn className="h-4 w-4" /> Ver Relatório
      </Link>
    </Button>
    <Button onClick={() => setIsFormOpen(true)} className="bg-blue-600 ...">
      <Plus className="h-4 w-4" /> Criar Tenant
    </Button>
  </div>
</CardHeader>
```

`git diff --name-only` from the pre-plan baseline (`48364858`, end of Plan 01) to this plan's final task commit lists exactly 3 files: `web/package.json`, `web/scripts/verify-relatorio-utilizacao.mjs`, `web/src/app/(dashboard)/plataforma/page.tsx`. No `pnpm-lock.yaml`, no `dashboard-shell.tsx`, no `sidebar-nav.tsx`, nothing under `backend/`.

## Deviations from Plan

None - plan executed exactly as written. All 15 assertions passed on first implementation (no fix-and-retry cycles needed), all 4 pre-existing gates and the full lint/tsc/test/build suite passed with zero regressions, and the 3 negative-proof regressions behaved exactly as predicted (each isolating precisely 1 assertion).

## Issues Encountered

None.

## Why this plan — not 01 or 02 — closes UTIL-01

`REQUIREMENTS.md`'s `UTIL-01` requires an internal report, reachable only by `PLATAFORMA_ADMIN`, showing 4 fields per tenant. Plan 01 built the route and its columns but left it deliberately unlinked from anywhere in the app (its own SUMMARY documents this explicitly: "not yet reachable from anywhere in the app, by design"). Plan 02 added only a backend regression test proving suspended tenants remain in the API response — no UI change at all. Neither plan ran `requirements mark-complete` for `UTIL-01`, mirroring the same disposition the Phase 120 Plan 02/03/04 sequence used for `PROV-02`/`PROV-05` (only closing those once the actual screen existed, not when the underlying pieces were merely built) — and the exact regression `commit cd45fcf9` had to correct after an earlier premature closure of `PROV-05`.

This plan (03) is the one that makes the report **reachable**: a real `PLATAFORMA_ADMIN` user can now get from `/plataforma` to `/plataforma/relatorio` with a single click, which is what "relatório... que o administrador de plataforma consulta" concretely requires beyond the route merely existing. `requirements mark-complete UTIL-01` was run only after every gate above (the new 15-assertion gate, all 4 pre-existing gates, lint, tsc, test, build) confirmed green — the same "close only once truly done" discipline the two prior plans in this phase established.

## User Setup Required

None - no external service configuration required. Zero new dependencies; `pnpm-lock.yaml` untouched.

## Next Phase Readiness

- `UTIL-01` is closed. All 3 tasks/plans of Phase 122's substantive work (route + columns, backend regression test, entry link + gate) are complete.
- Plan 04 (live UAT / human checkpoint) is the natural next step — the new gate's own doc comment explicitly defers what it cannot prove (route resolves in-browser, click navigates, badges/colors render, a real non-`PLATAFORMA_ADMIN` user gets a real 403, displayed numbers match real DB users) to that checkpoint.
- No blockers or concerns raised by this plan.

---
*Phase: 122-relat-rio-de-utiliza-o-por-tenant*
*Completed: 2026-07-30*

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/plataforma/page.tsx` (modified, contains `href="/plataforma/relatorio"`)
- FOUND: `web/scripts/verify-relatorio-utilizacao.mjs`
- FOUND: `web/package.json` contains `verify:relatorio-utilizacao`
- FOUND: commit `151f4a2a` (Task 1)
- FOUND: commit `8b7ed743` (Task 2)
- CONFIRMED: `UTIL-01` marked `Complete` in `.planning/REQUIREMENTS.md`
