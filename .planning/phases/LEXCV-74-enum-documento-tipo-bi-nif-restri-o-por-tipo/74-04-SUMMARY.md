---
phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo
plan: 04
subsystem: ui
tags: [zod, react-hook-form, forms, validation, clientes]

# Dependency graph
requires:
  - phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo (plans 01-03)
    provides: getDocumentoTipoOptions/toDocumentoTipo, per-tipo documento_tipo restriction in clienteFormSchema, editar/page.tsx legacyDocumentoTipo detection + banner + onSubmit carve-out
provides:
  - buildClienteFormSchema(allowedLegacyDocumentoTipo?) factory exempting exactly one legacy value from the per-tipo membership superRefine check
  - editar/page.tsx resolver rebuilt via useMemo from buildClienteFormSchema(legacyDocumentoTipo ?? undefined), making the pre-existing onSubmit legacy carve-out reachable
  - regression test (clientes.legacy-documento-tipo.test.ts) covering legacy-pass, new-invalid-reject, create-path-strict, and valid-in-set-pass
affects: [75-unificar-view-edit-cliente]

# Tech tracking
tech-stack:
  added: []
  patterns: [schema-factory-parameterization]

key-files:
  created:
    - web/src/schemas/clientes.legacy-documento-tipo.test.ts
  modified:
    - web/src/schemas/clientes.ts
    - "web/src/app/(dashboard)/clientes/[id]/editar/page.tsx"

key-decisions:
  - "Parameterized the shared Zod schema instead of moving the membership check into onSubmit — keeps 74-CONTEXT.md's locked decision that the check lives in the Zod schema, and closes the gap with a minimal, localized change."
  - "clienteFormSchema stays as a static export (buildClienteFormSchema() with no arg) so novo/page.tsx needs zero changes — no legacy exemption exists for fresh records, matching CLI-24 intent."

patterns-established:
  - "Schema-factory parameterization: when one consumer needs a per-instance validation exemption while others need the strict default, wrap the shared z.object(...).superRefine(...) in a factory function and keep a no-arg static export for the strict path."

requirements-completed: [CLI-24]

# Metrics
duration: ~25min
completed: 2026-07-03
---

# Phase 74 Plan 04: Legacy documento_tipo save-path gap closure Summary

**Parameterized `clienteFormSchema` into `buildClienteFormSchema(allowedLegacyDocumentoTipo?)` and wired `editar/page.tsx`'s resolver to it, making the previously-dead `onSubmit` legacy carve-out reachable so a cliente with an invalid legacy `documento_tipo` can be saved unchanged.**

## Performance

- **Duration:** ~25 min
- **Tasks:** 3 completed
- **Files modified:** 3 (1 new test file, 2 modified)

## Accomplishments
- Closed CR-01 / Truth #10: editing a cliente whose loaded `documento_tipo` is invalid for its `tipo`, left untouched, now saves successfully instead of being blocked by the shared Zod schema before `onSubmit` ever runs.
- The on-screen banner's promise ("guarde sem alterar este campo para manter o valor legado") is now true at runtime.
- `novo/page.tsx` (create path) is untouched and still uses the static, strict `clienteFormSchema` export — confirmed via grep, no legacy exemption for fresh records.
- Added a regression test (`clientes.legacy-documento-tipo.test.ts`) with 4 cases (A: legacy exempted, B: new invalid rejected, C: create-path strict, D: valid in-set still passes) — all 4 executed and passed against the real compiled schema (see Issues Encountered for how, since no test runner is installed).

## Task Commits

Each task was committed atomically:

1. **Task 1: Parameterize clienteFormSchema into buildClienteFormSchema(allowedLegacyDocumentoTipo?)** - `ddc9102` (fix)
2. **Task 2: Wire editar/page.tsx resolver to the parameterized schema** - `e5b0968` (fix)
3. **Task 3: Add regression test proving legacy-pass and new-invalid-reject** - `f825b2e` (test)

## Files Created/Modified
- `web/src/schemas/clientes.ts` - Refactored into `buildClienteFormSchema(allowedLegacyDocumentoTipo?)`; the per-tipo membership `ctx.addIssue` is now additionally guarded by `data.documento_tipo !== allowedLegacyDocumentoTipo`. `clienteFormSchema` remains exported as `buildClienteFormSchema()` (no arg), identical behavior to before for all existing consumers.
- `web/src/app/(dashboard)/clientes/[id]/editar/page.tsx` - Imports `buildClienteFormSchema` instead of the static `clienteFormSchema`; resolver is now `zodResolver(schema)` where `schema = useMemo(() => buildClienteFormSchema(legacyDocumentoTipo ?? undefined), [legacyDocumentoTipo])`. The existing `onSubmit` legacy carve-out (verbatim preservation of `legacyDocumentoTipo`) is unchanged and now reachable.
- `web/src/schemas/clientes.legacy-documento-tipo.test.ts` - New regression test file (vitest-style, matching sibling `cliente-documento-tipo.test.ts` convention), 4 test cases.

## Decisions Made
- Kept the membership check inside the Zod schema (per 74-CONTEXT.md line 32 lock) rather than relocating it to `onSubmit` — parameterizing the factory was the minimal change that satisfies both the lock and the gap closure.
- No new dependencies added; `vitest` remains uninstalled (explicitly out of scope per plan) — the enforceable automated gate was `tsc --noEmit`, supplemented by manually executing the 4 assertions.

## Deviations from Plan

None - plan executed exactly as written for all 3 tasks' `<action>` blocks.

### Auto-fixed Issues

None.

## Issues Encountered
- The worktree had no `web/node_modules` (git-ignored, not populated in this isolated worktree), so `npx tsc` initially returned npm's "this is not the tsc command you are looking for" stub message, which produced a false-positive "no errors" read on the grep-based gates. Fixed by creating an NTFS junction from the worktree's `web/node_modules` to the main repo's already-installed `web/node_modules` (read-only reuse, no reinstall, nothing committed — junction is git-ignored). After that, `npx tsc --noEmit` ran for real and confirmed all three plan-specified gates pass cleanly (only the known pre-existing `Cannot find module 'vitest'` errors in the two `.test.ts` files, identical to the sibling test's existing gap).
- Per the plan's Task 3 fallback instruction (no vitest runner installed), the 4 regression assertions were executed via a throwaway Node script (written to the scratchpad directory, not committed) that transpiles `clientes.ts` and `cliente-documento-tipo.ts` on the fly using the project's own installed `typescript` package and runs `buildClienteFormSchema(...).safeParse(...)` against the four cases. All 4 assertions (A/B/C/D) passed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness
- CLI-24's frontend validation gap for the editing flow is closed; Phase 75 (unify view/edit component) can build on `buildClienteFormSchema` directly if it needs the same legacy-exemption behavior in a merged component.
- No blockers or concerns for subsequent phases.

---
*Phase: 74-enum-documento-tipo-bi-nif-restri-o-por-tipo*
*Completed: 2026-07-03*
