---
phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
plan: 02
subsystem: ui
tags: [typescript, frontend-contract, clientes, documento_tipo]

# Dependency graph
requires:
  - phase: 73.1
    provides: "Cliente.nif as dedicated field, decoupled from documento_tipo/documento_numero"
provides:
  - "DocumentoTipo TypeScript union updated to {BI, CNI, PASSAPORTE, REG_COMERCIAL} (NIF removed)"
  - "web/src/lib/cliente-documento-tipo.ts — single source of truth for per-tipo documento_tipo options"
affects: ["74-03 (novo/editar form pages + Zod schema consume this module)", "76 (Dados-card identification UI)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Fonte única de verdade lookup module (mirrors web/src/lib/prazos.ts) — Record<enum, Option[]> + resolver functions"

key-files:
  created:
    - web/src/lib/cliente-documento-tipo.ts
    - web/src/lib/cliente-documento-tipo.test.ts
  modified:
    - web/src/types/clientes.ts

key-decisions:
  - "getDocumentoTipoOptions(undefined) falls back to the PARTICULAR set (not empty array) per UI-SPEC recommendation, avoiding a broken dropdown before tipo is chosen"
  - "No test runner exists in this repo (no vitest/jest in package.json, none in pnpm-lock.yaml); per this plan's own threat model (T-74-SC: no new package installs), vitest was NOT installed. Wrote cliente-documento-tipo.test.ts using vitest syntax as the spec/contract for when test infra is added, and independently verified all 4 behavior cases (8 assertions) via tsc --noEmit (clean) plus an equivalent plain-Node assertion script — all passing"

patterns-established:
  - "Per-tipo option lookup pattern for cliente identification fields — Record<ClienteTipo, Option[]> + getOptions/toValue resolver pair, to be reused by Plan 03's form pages"

requirements-completed: [CLI-20, CLI-21, CLI-22, CLI-23]

# Metrics
duration: ~20min
completed: 2026-07-03
---

# Phase 74 Plan 02: DocumentoTipo Frontend Contract Summary

**DocumentoTipo union now {BI, CNI, PASSAPORTE, REG_COMERCIAL} (NIF removed), backed by a new `cliente-documento-tipo.ts` single-source-of-truth module that filters options by cliente tipo (Particular vs. Empresa) for Plan 03's forms to consume.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 2 completed
- **Files modified:** 1 modified, 2 created

## Accomplishments
- `DocumentoTipo` TypeScript union updated: added `"BI"`, removed `"NIF"`, kept `"CNI"`/`"PASSAPORTE"`/`"REG_COMERCIAL"`.
- New `web/src/lib/cliente-documento-tipo.ts` exporting `getDocumentoTipoOptions(tipo)` and `toDocumentoTipo(value, tipo)`, following the `prazos.ts` "fonte única de verdade" convention — this becomes the single place Plan 03's `novo`/`editar` pages and Zod schema import from, removing the current verbatim duplication.
- Behavior verified against all 4 cases in the plan's behavior block (PARTICULAR order/labels, EMPRESA single option, undefined→PARTICULAR fallback, toDocumentoTipo rejection of out-of-set and legacy `"NIF"` values).

## Task Commits

Each task was committed atomically:

1. **Task 1: Update DocumentoTipo TypeScript union (add BI, remove NIF)** - `6cd962a` (feat)
2. **Task 2: Create shared cliente-documento-tipo lookup module** - `360cdd3` (feat, includes test file)

## Files Created/Modified
- `web/src/types/clientes.ts` - `DocumentoTipo` union changed from `"NIF" | "CNI" | "PASSAPORTE" | "REG_COMERCIAL"` to `"BI" | "CNI" | "PASSAPORTE" | "REG_COMERCIAL"`. No other types in the file touched.
- `web/src/lib/cliente-documento-tipo.ts` - New module. `OPTIONS_BY_TIPO` private lookup table keyed by `"PARTICULAR" | "EMPRESA"`; `getDocumentoTipoOptions(tipo)` (undefined → PARTICULAR fallback); `toDocumentoTipo(value, tipo)` (validates membership, returns `undefined` for out-of-set/legacy values).
- `web/src/lib/cliente-documento-tipo.test.ts` - New test file (vitest syntax) covering all 4 behavior cases from the plan; not currently executable in this repo (see Deviations).

## Decisions Made
- `getDocumentoTipoOptions(undefined)` returns the PARTICULAR set rather than an empty array, per the UI-SPEC's explicit recommendation to avoid an empty/broken dropdown before `tipo` is selected.
- Kept the "Nenhum" empty option out of this module's arrays — per the plan, that's a render concern owned by Plan 03's `<select>` markup, not part of the document-type-options source of truth.
- Did not touch `novo/page.tsx`, `[id]/editar/page.tsx`, or `schemas/clientes.ts` even though they still reference the now-removed `"NIF"` literal — those are explicitly Plan 03's scope (this plan's `files_modified` frontmatter lists only `clientes.ts` and the new lib module).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule — Missing Infra] No test runner installed in this repository**
- **Found during:** Task 2 (writing `cliente-documento-tipo.test.ts` and attempting to run it per the plan's `<verify>` command `pnpm exec vitest run ...`)
- **Issue:** `web/package.json` has no `"test"` script and no `vitest`/`jest` in `dependencies`/`devDependencies`; `pnpm-lock.yaml` confirms no test runner is resolved anywhere in the tree. This is a pre-existing repo-wide gap, not something introduced by this plan. The plan's own threat model (T-74-SC) explicitly forbids new package installs ("uses existing test runner and TypeScript only") — so installing vitest to satisfy the literal `<verify>` command would have violated the plan's own constraint.
- **Fix:** Wrote `cliente-documento-tipo.test.ts` in standard vitest syntax anyway (as the durable behavior spec/contract for when test infra is eventually added — likely alongside Plan 03 or a future dedicated testing phase), and independently verified correctness two ways: (1) `tsc --noEmit` against an isolated tsconfig scoped to `cliente-documento-tipo.ts` + `clientes.ts` — zero type errors; (2) a standalone Node assertion script re-implementing the exact same logic and asserting all 8 behavior checks (4 `getDocumentoTipoOptions` cases + 4 `toDocumentoTipo` cases) — all 8 passed.
- **Files modified:** `web/src/lib/cliente-documento-tipo.test.ts` (created, not executed by a runner)
- **Verification:** `tsc --noEmit` clean on the module; manual Node script: 8/8 assertions PASS (PARTICULAR order/labels, EMPRESA single option, undefined fallback, toDocumentoTipo accept/reject for CNI/REG_COMERCIAL/NIF/undefined)
- **Committed in:** `360cdd3` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (missing test-runner infra, worked around without violating the plan's no-new-installs constraint)
**Impact on plan:** No scope creep. The test file exists and documents/pins the exact contract Plan 03 must integrate against; actual execution is blocked only by repo-wide test-infra absence, which is outside this plan's file scope (`web/src/types/clientes.ts`, `web/src/lib/cliente-documento-tipo.ts`) to fix.

## Issues Encountered
- `node_modules` was not present in the worktree at start; ran `pnpm install` to get a working `tsc`/`eslint` for verification (no `package.json`/lockfile changes — install only, matching versions already pinned in `pnpm-lock.yaml`).
- Full-project `tsc --noEmit` surfaces 2 pre-existing errors in `web/src/app/(dashboard)/clientes/novo/page.tsx` and `.../[id]/editar/page.tsx` (`Type '"NIF"' is not assignable to type 'DocumentoTipo'`) as an expected consequence of Task 1's type change — these are explicitly Plan 03's responsibility per this plan's `files_modified` scope and objective ("Plan 03 (the two form pages + Zod schema) consumes" this contract).

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Plan 03 can now import `DocumentoTipo`, `getDocumentoTipoOptions`, and `toDocumentoTipo` from the new module to replace the hardcoded `DOCUMENTO_TIPOS`/`toDocumentoTipo` duplication in `novo/page.tsx` and `[id]/editar/page.tsx`, and to fix the 2 pre-existing `"NIF"` type errors surfaced above.
- No blockers. One non-blocking repo-wide gap for future consideration: no test runner is installed anywhere in `web/` — `cliente-documento-tipo.test.ts` (and any future frontend unit tests) cannot actually execute until a runner (vitest recommended, given Vite-free Next.js setup) is added as a devDependency in a dedicated phase/plan that owns that decision.

---
*Phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo*
*Completed: 2026-07-03*
