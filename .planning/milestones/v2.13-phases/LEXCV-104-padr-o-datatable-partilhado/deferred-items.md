# Phase 104 — Deferred Items (out of scope for this phase's tasks)

## Pre-existing `tsc --noEmit` failures unrelated to DataTable work

Discovered while running the Task 1 verification command (`pnpm --dir web exec tsc --noEmit`) in plan 104-02.

```
src/hooks/use-processos.round-trip.test.ts(1,38): error TS2307: Cannot find module 'vitest' or its corresponding type declarations.
src/lib/cliente-documento-tipo.test.ts(1,38): error TS2307: Cannot find module 'vitest' or its corresponding type declarations.
src/schemas/clientes.legacy-documento-tipo.test.ts(1,38): error TS2307: Cannot find module 'vitest' or its corresponding type declarations.
```

- **Root cause:** `vitest` was never added as a dependency in `web/package.json`/`web/pnpm-lock.yaml`, but these 3 test files import it.
- **Pre-existing confirmation:** `git log --oneline -1 -- <these 3 files>` resolves to commit `80cb859` ("feat(97-02): translate DocumentoTipo enum to Portuguese labels"), long before Phase 104. Not introduced by `@tanstack/react-table`/`pagination.tsx` additions.
- **Scope boundary:** none of these 3 files are in 104-02's `files_modified` list. Per executor SCOPE BOUNDARY rule, out-of-scope pre-existing failures are logged here, not fixed.
- **Recommendation:** a future phase/todo should either add `vitest` as a devDependency (if these tests are meant to run) or remove/relocate these orphaned test files if the test runner was never wired up.

## UI-audit findings not fixed inline (104-UI-REVIEW.md)

Discovered by `gsd-ui-auditor`'s retroactive 6-pillar review. The 3 priority fixes (Documentos name resolution, Badge `text-[10px]` overrides, Ações header alignment) and the Documentos Download-Tooltip gap were fixed directly (commit `474f4f0`). The following were left for a future phase/cleanup pass since they require either a new component design or a broader cross-screen consistency sweep beyond this phase's scope:

- **Processos' `<DataTable>` has no `hidden md:block` mobile gate**, unlike Clientes/Pareceres/Financeiro/Documentos (all four wrap `<DataTable>` + a dedicated `md:hidden` card branch). Processos never had a mobile-card view before this phase (documented in `104-03-SUMMARY.md`), but this phase added a real toolbar + functional pagination footer that now renders unguarded at all viewport widths. Needs a `ProcessoMobileCard` component (mirroring `ClienteMobileCard`'s pattern) — a small design task in its own right, not a one-line fix.
- **Gray-family split across the 5 "shared pattern" `columns.tsx` files**: Clientes/Processos/Pareceres use `slate-*` for body/link text; Financeiro/Documentos use `neutral-*`. Same split exists for the primary-identity-link hover idiom (bold+color-swap vs. medium+underline-only). A future consistency pass should pick one convention and apply it across all 5.
- **Row-hover gives a false "whole row is clickable" signal** (`table.tsx`'s `hover:bg-muted/50` applies uniformly regardless of sortability, while the sort button's own hover is cancelled via `hover:bg-transparent`) — a shared-component-level fix affecting all 5 screens.
- **Off-scale spacing in 2 shared files**: `data-table.tsx`'s toolbar wrapper uses `py-3` (12px) and `data-table-column-header.tsx`'s sort button uses `gap-1.5` (6px), both off the declared 7-point scale (nearest are 8/16 and 4/8 respectively). Narrow surface area (2 instances, 2 files), not visibly broken.
