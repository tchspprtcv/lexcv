---
phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
plan: 03
subsystem: ui
tags: [typescript, zod, react-hook-form, clientes, documento_tipo]

# Dependency graph
requires:
  - phase: 74-02
    provides: "web/src/lib/cliente-documento-tipo.ts — getDocumentoTipoOptions(tipo) / toDocumentoTipo(value, tipo) shared module, DocumentoTipo union {BI, CNI, PASSAPORTE, REG_COMERCIAL}"
provides:
  - "Zod schema (web/src/schemas/clientes.ts) validates documento_tipo against the shared per-tipo option set instead of a hardcoded NIF branch"
  - "novo/page.tsx and [id]/editar/page.tsx document-type dropdowns filtered by selected tipo via getDocumentoTipoOptions"
  - "Both pages' onSubmit call the shared two-argument toDocumentoTipo(value, tipo)"
  - "confirmTipoChange in both pages clears documento_tipo/documento_numero when the current value becomes invalid for the newly selected tipo"
affects: ["76 (Dados-card identification UI will consume the same filtered-dropdown pattern)"]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared per-tipo option lookup consumed identically by Zod superRefine and by two sibling form pages — single source of truth pattern extended from lib module (74-02) into schema + UI consumers (74-03)"

key-files:
  created: []
  modified:
    - web/src/schemas/clientes.ts
    - web/src/app/(dashboard)/clientes/novo/page.tsx
    - web/src/app/(dashboard)/clientes/[id]/editar/page.tsx

key-decisions:
  - "confirmTipoChange invalidity check reuses toDocumentoTipo(currentValue, pendingTipo) === undefined as the single signal for clearing documento_tipo/documento_numero, avoiding a second hand-rolled membership check"
  - "Created a temporary local web/.env.local (gitignored, not committed) solely to run pnpm build for verification; removed it before finishing since it is not part of this plan's file scope"

patterns-established: []

requirements-completed: [CLI-22, CLI-23, CLI-24]

# Metrics
duration: ~25min
completed: 2026-07-03
---

# Phase 74 Plan 03: Wire Cliente Forms + Zod Schema to Shared documento_tipo Module Summary

**Both cliente form pages and the Zod schema now source `documento_tipo` options and validation exclusively from the Plan 02 shared module — hardcoded NIF-branch validation and duplicated `DOCUMENTO_TIPOS`/`toDocumentoTipo` removed, dropdowns filtered live by selected `tipo`, and an invalidating tipo switch clears the stale document fields.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 3 completed
- **Files modified:** 3

## Accomplishments
- `web/src/schemas/clientes.ts` superRefine no longer branches on `documento_tipo === "NIF"` (NIF is a dedicated field now); instead validates `documento_tipo` membership against `getDocumentoTipoOptions(data.tipo)` from the shared module.
- `novo/page.tsx` and `[id]/editar/page.tsx` both: deleted the local `DOCUMENTO_TIPOS` array + single-argument `toDocumentoTipo`; import `getDocumentoTipoOptions`/`toDocumentoTipo` from `@/lib/cliente-documento-tipo`; render the document-type `<select>` as `Nenhum` + `getDocumentoTipoOptions(form.watch("tipo")).map(...)`; call the two-argument `toDocumentoTipo(values.documento_tipo, values.tipo)` in `onSubmit`; and clear `documento_tipo`/`documento_numero` inside `confirmTipoChange` when the current value is no longer valid for the newly confirmed tipo.
- `editar/page.tsx`'s dual snake_case/camelCase form-reset read (`cliente.data.documento_tipo ?? cliente.data.documentoTipo ?? ""`) was preserved unchanged — verified present after edit.
- Full-project `npx tsc --noEmit` has zero errors related to `documento_tipo`/`toDocumentoTipo` call sites (the only remaining error, a `Cannot find module 'vitest'` in `cliente-documento-tipo.test.ts`, is a pre-existing gap from Plan 02, confirmed identical before and after this plan's changes via `git stash`).
- `pnpm build` succeeds end-to-end (Next.js 16 / Turbopack production build, all 23 routes compiled).

## Task Commits

Each task was committed atomically:

1. **Task 1: Update Zod schema — remove NIF branch, add per-tipo documento_tipo validation** - `35970ba` (feat)
2. **Task 2: Wire novo/page.tsx to shared module — filtered dropdown + clear-on-switch + onSubmit call site** - `49f1ba8` (feat)
3. **Task 3: Wire [id]/editar/page.tsx to shared module — mirror novo/page.tsx** - `129bcc1` (feat)

## Files Created/Modified
- `web/src/schemas/clientes.ts` - Imports `getDocumentoTipoOptions`; superRefine's NIF-specific 9-digit branch removed, replaced with a membership check against the shared per-tipo option set; the pre-existing documento_numero-required branch is unchanged.
- `web/src/app/(dashboard)/clientes/novo/page.tsx` - Local `DOCUMENTO_TIPOS`/`toDocumentoTipo` deleted; imports shared module; dropdown renders `Nenhum` + mapped `getDocumentoTipoOptions(form.watch("tipo"))` options; `onSubmit` calls `toDocumentoTipo(values.documento_tipo, values.tipo)`; `confirmTipoChange` clears `documento_tipo`/`documento_numero` via `form.setValue` when the current value is invalid for the new tipo.
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` - Identical refactor to `novo/page.tsx`; dual-read form-reset line at (now) line 140 preserved verbatim.

## Decisions Made
- Reused `toDocumentoTipo(currentValue, pendingTipo) === undefined` as the sole invalidity signal in `confirmTipoChange` in both pages, rather than writing a second bespoke membership check — keeps the "clear if invalid" logic anchored to the same shared-module contract used everywhere else.
- Verified the pre-existing `vitest` module-resolution error in `cliente-documento-tipo.test.ts` (created in Plan 02, not runnable per that plan's no-new-installs threat-model constraint) is unchanged by this plan's work, via `git stash`/`tsc --noEmit`/`git stash pop` before/after comparison — confirms this plan introduced zero new type errors.
- Created and then removed a local, gitignored `web/.env.local` purely to exercise `pnpm build` for verification (the build requires `BACKEND_API_ORIGIN`/`NEXT_PUBLIC_API_BASE_PATH` at build time); not committed, not part of this plan's file scope.

## Deviations from Plan

None - plan executed exactly as written. All three tasks matched their described actions and acceptance criteria; no additional files were touched beyond the plan's `files_modified` list.

## Issues Encountered
- Worktree HEAD was initially on a stale commit (`6ca8487`) predating Wave 1's merge — `.planning/phases/LEXCV-74-.../` and `web/src/lib/cliente-documento-tipo.ts` did not exist at that commit. Per the worktree branch-check protocol, verified the intended base `33e5e4497f3fba97b9575aee2d72d742fe1da135` genuinely contained Wave 1's merged work, then ran `git reset --hard 33e5e4497f3fba97b9575aee2d72d742fe1da135` to correct the worktree before starting any task work.
- `node_modules` was not present in the worktree at start; ran `pnpm install` (no lockfile/package.json changes) to get a working `tsc`/`eslint`/`next build`.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Phase 74 (all 3 plans: 74-01 backend, 74-02 frontend contract, 74-03 frontend wiring) is now complete for CLI-22/CLI-23/CLI-24. Both cliente form pages and the Zod schema consume a single shared source of truth for `documento_tipo` options, matching the backend's per-tipo restriction from Plan 01.
- Phase 76 (Dados-card identification UI) can reuse the same `getDocumentoTipoOptions`/`toDocumentoTipo` pattern now proven in both `novo` and `editar` pages, without re-deriving the filtering logic.
- No blockers.

---
*Phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo*
*Completed: 2026-07-03*
