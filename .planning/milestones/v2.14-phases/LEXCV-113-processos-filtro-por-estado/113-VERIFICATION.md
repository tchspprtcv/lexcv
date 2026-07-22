---
phase: 113-processos-filtro-por-estado
verified: 2026-07-21T22:30:00Z
status: passed
score: 3/3 success criteria verified (5/5 PLAN must-haves truths verified)
overrides_applied: 0
---

# Phase 113: Processos — Filtro por Estado Verification Report

**Phase Goal:** O utilizador filtra a lista de Processos por estado, sem perder os outros filtros já aplicados.
**Verified:** 2026-07-21T22:30:00Z
**Status:** passed
**Re-verification:** No — initial verification

## Adversarial Starting Position

Starting hypothesis: the phase goal was NOT achieved and SUMMARY.md/113-REVIEW.md claims were unverified narrative. This report documents independent, first-hand codebase evidence gathered to falsify that hypothesis — not a restatement of the SUMMARY/REVIEW text. Every claim below was checked against the actual current file (`git show`, direct `Read`, `grep`, `tsc`, `eslint`), not inferred from planning documents.

## Goal Achievement

### Observable Truths (ROADMAP.md Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Utilizador seleciona um estado num controlo dedicado na lista de Processos e a lista atualiza para mostrar apenas processos nesse estado | ✓ VERIFIED | `web/src/app/(dashboard)/processos/page.tsx:220-239` — dedicated `NativeSelect` (6 options: Todos/TRIAGEM/ATIVO/SUSPENSO/ENCERRADO/CONCLUIDO) renders unconditionally (confirmed: this JSX block sits at lines 220-239, entirely outside the `{advancedOpen ? (...) : null}` conditional, which only starts at line 269 — i.e. NOT gated by the collapsed panel). `onApply` (lines 106-116) commits `estado: draftEstado.trim()` into `filters`. `useProcessos(filters)` (line 55) → `buildProcessosSearch()` (`web/src/hooks/use-processos.ts:145-156`) sets `estado` on the query string → `GET /processos?estado=X`. Backend `ResourceController.listProcessos` (`backend/.../ResourceController.java:936-982`) reads `@RequestParam(required=false) String estado`, normalizes it, and filters the tenant-scoped `Processo` list by exact (case/whitespace-insensitive) match. Full chain traced end-to-end, not stubbed. |
| 2 | Utilizador limpa o filtro de estado e a lista volta a mostrar todos os processos, respeitando os outros filtros já aplicados | ✓ VERIFIED | Selecting "Todos" (`value=""`) in the Estado control sets `draftEstado` to `""` without touching `draftTribunal`/`draftArea`/`draftClienteId`/`draftQuery`. Clicking "Aplicar" (`onApply`, lines 106-116) recommits **all** current draft values together — since only `draftEstado` changed, the other filters' current values pass through unchanged. Backend: `estadoNorm == null \|\| estadoNorm.isEmpty() → return true` (line 962) removes the estado restriction while the other `.filter()` predicates (tribunal/area/cliente_id/q, lines 958-981) keep applying independently. `git show 3c4f277` confirms `onApply`/`onClear` function bodies are entirely outside this phase's diff hunks — this behavior is unchanged, byte-for-byte, by the relocation. |
| 3 | O filtro de estado funciona em conjunto com os filtros já existentes na lista de Processos, sem se substituírem mutuamente | ✓ VERIFIED | Backend applies `cliente_id`, `estado`, `tribunal`, `area_juridica`, `q` as independently chained `.filter()` predicates on the same tenant-scoped stream (`ResourceController.java:955-982`) — pure AND composition, no filter excludes or overwrites another. Frontend `buildProcessosSearch()` (`use-processos.ts:145-156`) sets every non-empty filter field on the same `URLSearchParams` simultaneously. Confirmed structurally — none of the 5 filters are mutually exclusive. |

**Score:** 3/3 truths verified

### PLAN Frontmatter Must-Haves (113-01-PLAN.md)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Estado control (NativeSelect, 6 options) visible in main filter bar without opening `advancedOpen` panel | ✓ VERIFIED | Lines 220-239, sibling of the Pesquisar wrapper and "Filtros" button, both direct children of the always-rendered `<div className="flex flex-wrap items-end gap-2">` (line 205), which closes at line 249 — well before the `{advancedOpen ? (...) : null}` block starts (line 269). |
| 2 | Estado control sits between Pesquisar field and "Filtros" button | ✓ VERIFIED | Pesquisar wrapper closes at line 219; Estado wrapper is lines 220-239; "Filtros" `<Button>` starts at line 240 — exact locked position from 113-CONTEXT.md ("logo a seguir ao campo de pesquisa, antes do botão 'Filtros'"). |
| 3 | Selecting an estado + "Aplicar" still filters the list (draft + Aplicar model unchanged) | ✓ VERIFIED | `value={draftEstado}` / `onChange={(e) => setDraftEstado(e.target.value)}` (lines 226-227) unchanged; `onApply` commits `estado: draftEstado.trim()` (line 111) unchanged. `grep -c 'setFilters'` shows the only 4 call sites are: initial `useState`, the query-debounce effect (q only), `onApply`, `onClear` — no new `onChange`-triggered immediate-apply was added for Estado. |
| 4 | "Limpar" still resets estado to Todos together with the other filters | ✓ VERIFIED | `onClear` (lines 118-133): `setDraftEstado("")` (line 120) plus full `filters` reset including `estado: ""` (line 126) — unchanged. |
| 5 | Remaining advanced filters (Tribunal/Área jurídica/Cliente) stay in `advancedOpen` panel, fill 12-col grid, no dangling column | ✓ VERIFIED | `{advancedOpen ? (...) : null}` (lines 269-318) contains exactly 3 children, each `lg:col-span-4` (lines 271, 284, 297) inside `lg:grid-cols-12` (line 270) → 3×4=12, exact fit, zero `lg:col-span-3` remaining anywhere in the file. |

**Score:** 5/5 must-haves truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `web/src/app/(dashboard)/processos/page.tsx` | Estado `NativeSelect` relocated to always-visible main bar (`w-40 max-w-full` wrapper), removed from `advancedOpen` panel; 3 remaining advanced fields at `lg:col-span-4` | ✓ VERIFIED | File exists, substantive (full working component, not stub), wired (state/handlers/hook all consumed). `grep -c 'w-40 max-w-full'` = 1 (line 220, distinct from search field's `w-[320px] max-w-full`). Independently re-ran `pnpm exec tsc --noEmit` (only 3 pre-existing, unrelated `Cannot find module 'vitest'` errors in `*.test.ts` files — zero errors in this file) and `pnpm exec eslint "src/app/(dashboard)/processos/page.tsx"` (`No issues found`) myself in this session, not trusting the SUMMARY's claim. |

### Key Link Verification

| From | To | Via | Status | Details |
|------|-----|-----|--------|---------|
| Estado `NativeSelect` (main bar) | `draftEstado` state | `value={draftEstado}` / `onChange={(e) => setDraftEstado(e.target.value)}` | ✓ WIRED | Line 226-227. `grep -c 'value={draftEstado}'` = 1 (exactly one binding site in the whole file — no duplication, no orphaned second copy). |
| `onApply` | `filters.estado` → `useProcessos(filters)` → `GET /processos?estado=` | `estado: draftEstado.trim()` commit on Aplicar | ✓ WIRED | Line 111. `grep -c 'estado: draftEstado.trim()'` = 1. Traced through `use-processos.ts:145-181` (`buildProcessosSearch` → `apiFetch<ProcessoApi[]>`) into `ResourceController.java:936-982` (`@RequestParam String estado`, tenant-scoped filter). Not a stub fetch — a real network call against a real DB-backed repository (`processoRepository.findByTenantId`). |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|---------------------|--------|
| `<DataTable columns={processoColumns} data={processos.data} .../>` (line 337) | `processos.data` (from `useProcessos(filters)`) | `apiFetch<ProcessoApi[]>('/processos' + buildProcessosSearch(filters))` → `ResourceController.listProcessos` → `processoRepository.findByTenantId(tenantId)` filtered by estado/tribunal/area/cliente/q | Yes — real JPA-repository query, tenant-scoped, filtered server-side | ✓ FLOWING |

### Behavioral Spot-Checks

No dev server/browser available in this environment (confirmed no runnable entry point to exercise interactively). Static-equivalent checks performed instead, each independently re-run by the verifier (not sourced from SUMMARY.md claims):

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| File typechecks clean | `cd web && pnpm exec tsc --noEmit` | 3 pre-existing, unrelated `vitest` module-resolution errors in `*.test.ts` files; 0 errors in `processos/page.tsx` | ✓ PASS |
| File lints clean | `cd web && pnpm exec eslint "src/app/(dashboard)/processos/page.tsx"` | `No issues found` | ✓ PASS |
| Diff is pure relocation (no logic touched) | `git show 3c4f277 -- "web/src/app/(dashboard)/processos/page.tsx"` | 23 insertions / 23 deletions: one 20-line Estado block moved verbatim (only wrapper className changed `lg:col-span-3` → `w-40 max-w-full`) + 3 standalone `lg:col-span-3`→`lg:col-span-4` className swaps. Zero touches to `onApply`/`onClear`/`draftEstado`/`useProcessos` bodies. | ✓ PASS |
| Acceptance-criteria greps (from PLAN) all hold | `grep -c` for `w-40 max-w-full`(1), `lg:col-span-4`(3), `lg:col-span-3`(0), `<NativeSelect`(2), `value="TRIAGEM"`(1), `value={draftEstado}`(1), `estado: draftEstado.trim()`(1) | All exact matches to PLAN's stated expected counts | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|--------------|--------|----------|
| PEST-01 | 113-01-PLAN.md | Utilizador filtra a lista de Processos por estado através de um controlo dedicado | ✓ SATISFIED | Dedicated, always-visible control implemented and fully wired (see Truths 1-3 above). REQUIREMENTS.md already marks PEST-01 `[x]` / `Complete`, matched by independent code evidence, not just document cross-reference. |

No orphaned requirements — REQUIREMENTS.md maps only PEST-01 to Phase 113, and the plan declares exactly `requirements: [PEST-01]`.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | None found | — | `grep` for `TBD\|FIXME\|XXX`, `TODO\|HACK\|PLACEHOLDER`, empty-return stubs (`return null\|return {}\|return []\|=> {}`), and empty `onClick` handlers all returned zero matches in the modified file. The only "placeholder" string hits are legitimate HTML `placeholder="..."` input attributes, not stub markers. |

Code review (113-REVIEW.md, `status: clean`, closed 2026-07-21T21:15:00Z) independently found 0 critical findings against the actual diff. It surfaced 4 additional findings from its mandatory full-file read (standard depth) — all 4 confirmed pre-existing and unrelated to this phase's relocation:
- **WR-01** (warning): `proximasAudiencias` KPI counter missing a lower-date-bound check (`processos/page.tsx:90-97`) — a real bug, but in an unrelated dashboard-card computation untouched by commit `3c4f277`. Correctly triaged out of this phase's single-file relocation scope.
- **IN-01/IN-02/IN-03** (info): pre-existing, codebase-wide label-association gap, single-value empty-state message, and a UX note about Estado now sitting beside an auto-applying search field — all pre-existing patterns, not introduced or worsened by this phase's diff.

None of these 4 findings block Phase 113's goal or its 3 success criteria; the review's own closure note explicitly confirms "the relocation itself... is confirmed clean on all 4 requested dimensions — 0 findings against the diff."

### Human Verification Required

None required as a blocking gap. Reasoning: all 3 ROADMAP success criteria are functional/behavioral claims (filter-selection → list update; clear → list resets while respecting other filters; multi-filter AND composition), and each was traced end-to-end through actual source code (frontend state → hook → query param → backend filter → tenant-scoped DB-backed repository), not inferred from documentation. The visual-quality dimension for this narrow relocation was already gated pre-implementation by this project's UI-SPEC + checker workflow (`113-UI-SPEC.md`, Checker Sign-Off: all 6 dimensions PASS or FLAG-fixed), and the code review independently confirmed JSX structural integrity, DOM order, and tab order match the intended visual order.

Non-blocking recommendation (not a gap, does not affect status): before considering the wider v2.14 milestone's visual QA fully closed, a quick live-browser smoke-test of the `w-40` (160px) Estado box at the three longest option labels ("Em triagem"/"Encerrado"/"Concluído") and of `flex-wrap` behavior at narrow viewport widths would be prudent — this is standard UI-diligence, not evidence of a missed truth for this phase's stated goal.

### Gaps Summary

None. All 3 ROADMAP success criteria and all 5 PLAN must-haves truths are independently verified against the current codebase (not SUMMARY.md narrative): the Estado filter control is a dedicated, always-visible `NativeSelect` sitting between the search field and the "Filtros" toggle; selecting a value and clicking "Aplicar" filters the tenant-scoped process list to that estado end-to-end (frontend state → REST query param → backend filter → DB-backed repository); clearing the estado value (via "Todos") returns to showing all processos while the other active filters (tribunal/área/cliente/pesquisa) continue to apply unchanged; and all 5 filters compose via independent AND predicates on both the frontend query-string builder and the backend filter chain, with no mutual override. The commit diff (`3c4f277`) is confirmed, line-by-line, to be a pure JSX relocation — zero state, handler, or backend logic was touched. `tsc --noEmit` and `eslint` were independently re-run against the file in this verification session and are both clean.

---

_Verified: 2026-07-21T22:30:00Z_
_Verifier: Claude (gsd-verifier)_
