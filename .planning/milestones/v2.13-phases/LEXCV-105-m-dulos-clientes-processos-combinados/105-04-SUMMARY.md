---
phase: 105-m-dulos-clientes-processos-combinados
plan: 04
subsystem: web/clientes (secondary pages)
tags: [nativeselect, breadcrumb, clientes, shadcn-migration]
dependency-graph:
  requires: []
  provides:
    - "Clientes secondary pages (list/novo/merge) on NativeSelect"
    - "Breadcrumb on clientes/novo and clientes/merge"
  affects:
    - web/src/app/(dashboard)/clientes/page.tsx
    - web/src/app/(dashboard)/clientes/novo/page.tsx
    - web/src/app/(dashboard)/clientes/merge/page.tsx
tech-stack:
  added: []
  patterns:
    - "NativeSelect (size=default) as the single substitute for RHF-bound and controlled <select> elements, zero className overrides"
    - "Breadcrumb (Clientes -> current page) inserted directly under the h1, net-new, existing Voltar Button untouched"
key-files:
  created: []
  modified:
    - web/src/app/(dashboard)/clientes/page.tsx
    - web/src/app/(dashboard)/clientes/novo/page.tsx
    - web/src/app/(dashboard)/clientes/merge/page.tsx
decisions:
  - "Left the 5 pre-existing Input-element focus-visible:ring-blue-500 occurrences in clientes/page.tsx untouched — out of scope for CLP-03 (select-only migration), see Deviations"
metrics:
  duration: "~18 min"
  completed: 2026-07-16
---

# Phase 105 Plan 04: Clientes Secondary Pages — NativeSelect + Breadcrumb Summary

NativeSelect replaces all 6 native `<select>` elements across the Clientes list/create/merge pages, and a net-new Breadcrumb (Clientes → current page) is added to the two form/action pages; `pnpm build` confirmed green.

## What Was Built

- **`clientes/page.tsx`** (Task 1): the 2 list-filter selects (`draftTipo`, `draftAtivo`) migrated from raw `<select className="h-10 ... rounded-none ... focus-visible:ring-blue-500">` to `<NativeSelect value={...} onChange={...} size="default">`. No Breadcrumb added (list page, out of the 6-page CLP-05 scope).
- **`clientes/novo/page.tsx`** (Task 2): the 2 RHF-bound selects (`documento_tipo`, `ramo_atividade`) migrated to `<NativeSelect id="..." size="default" {...form.register("...")}>`; the shared `const selectClassName` declaration deleted (the sibling `const textareaClassName`, still used by the `<textarea>`, was kept). A net-new `Breadcrumb` (`Clientes` → `Novo Cliente`) was inserted directly under the `<h1>`, above the existing subtitle `<p>`. The existing "Voltar" `Button` was left untouched.
- **`clientes/merge/page.tsx`** (Task 3): the 2 controlled selects (`primaryId`, `secondaryId`) migrated to `<NativeSelect value={...} onChange={...} size="default">` (this file never had a `selectClassName` constant to delete). A net-new `Breadcrumb` (`Clientes` → `Merge`, the short form per the Copywriting Contract) was inserted under the `<h1>`.

## Deviations from Plan

### Auto-fixed Issues

None — plan tasks executed as written for the code changes themselves.

### Verification-gap findings (documented, not fixed — out of scope)

**1. [Scope boundary] Task 1's automated verify gate (`grep -c "ring-blue-500" ... | grep -qx 0`) checks the whole file, not just the 2 selects**
- **Found during:** Task 1 verification
- **Issue:** `clientes/page.tsx` has 5 pre-existing `focus-visible:ring-blue-500` occurrences on `Input` elements (the free-text search box, the NIF filter, the localidade filter, and the two date-range inputs) that are unrelated to the `<select>`→`NativeSelect` migration this plan covers. The plan's literal verify command greps the entire file, so it can never return `0` once these unrelated `Input` occurrences are counted, even though both target `<select>` elements are correctly migrated with zero leftover className.
- **Resolution:** Confirmed via targeted `grep -n` that all 5 remaining `ring-blue-500` hits are on `<Input ...>` elements, none on `<select>`/`NativeSelect`. `Input` styling is not part of CLP-03's scope (NativeSelect only, per `105-CONTEXT.md`/`105-UI-SPEC.md` Component Inventory) and was not touched, per the deviation-rules scope boundary ("Only auto-fix issues DIRECTLY caused by the current task's changes... pre-existing... unrelated files [surface] are out of scope").
- **Files affected:** None modified beyond the plan's own scope.
- **Commit:** `3a53792` (Task 1) — no separate fix commit needed.

**2. [Environment] `pnpm build`/`pnpm lint` required local `node_modules` + `web/.env.local`, neither present in the fresh worktree**
- **Found during:** Task 3 verification
- **Issue:** This worktree had no `node_modules` (per the documented Phase 101 lesson: worktree installs don't propagate to the main checkout, and vice versa — a fresh worktree has none until installed) and no `.env.local` (gitignored, machine-specific).
- **Resolution:** Ran `pnpm install --prefer-offline` locally in the worktree (resolved entirely from the local pnpm store, no new downloads, ~1 min) and supplied `BACKEND_API_ORIGIN=http://localhost:8080 NEXT_PUBLIC_API_BASE_PATH=/api/v1` inline as env vars for the build invocation (matching the committed `.env.example` values). `pnpm build` completed successfully (`Compiled successfully`, TypeScript clean, all 24 routes generated) — no new type errors. `pnpm lint` was also run; the only finding on any of this plan's 3 files is a single pre-existing `react-hooks/incompatible-library` warning in `clientes/novo/page.tsx` at the pre-existing `form.watch("tipo")` line (128), unrelated to this plan's changes (not introduced, not touched by this plan).
- **Files affected:** None (verification-only; `node_modules`/build output are gitignored, confirmed via `git status --short`).
- **Commit:** N/A (no code change required).

## Known Stubs

None.

## Threat Flags

None — no new trust-boundary surface introduced; the 3 files retain identical form-field bindings (`register()`/controlled `value`/`onChange`) and navigation targets (`/clientes` only), consistent with `105-04-PLAN.md`'s threat model (T-105-04, T-105-SC, both `accept`).

## Verification

- `grep -c "<select"` returns 0 in all 3 files; `NativeSelect` present in all 3.
- `clientes/novo/page.tsx`: `const selectClassName` deleted, `const textareaClassName` retained, `BreadcrumbList` present.
- `clientes/merge/page.tsx`: `BreadcrumbList` present.
- `cd web && pnpm build` — green, no new type errors, all 24 routes generated.
- `cd web && pnpm lint` on the 3 touched files — 0 errors; 1 pre-existing warning (unrelated line, `clientes/novo/page.tsx:128`).

## Self-Check: PASSED

- FOUND: `web/src/app/(dashboard)/clientes/page.tsx`
- FOUND: `web/src/app/(dashboard)/clientes/novo/page.tsx`
- FOUND: `web/src/app/(dashboard)/clientes/merge/page.tsx`
- FOUND commit `3a53792` (Task 1)
- FOUND commit `60d9e4f` (Task 2)
- FOUND commit `7790bbd` (Task 3)
