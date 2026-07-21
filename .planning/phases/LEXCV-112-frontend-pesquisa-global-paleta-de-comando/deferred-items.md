# Phase 112 — Deferred Items (out of scope, logged not fixed)

Found while running `web`'s full-project `tsc --noEmit`/`pnpm lint` to verify Plan 112-01's
5 new files did not introduce regressions. None of the items below are caused by, or touch,
any file this plan created (`web/src/types/search.ts`, `web/src/lib/use-debounced-value.ts`,
`web/src/hooks/use-global-search.ts`, `web/src/lib/search-recents.ts`,
`web/src/lib/highlight-match.tsx`) — confirmed by targeted grep of the raw tool output for
each of the 5 filenames (zero matches). Per the executor's scope-boundary rule, these are
logged here rather than fixed.

## 1. `vitest` referenced but never installed (pre-existing, Phase 74/83)

`tsc --noEmit` at the whole-project level fails with:
```
src/hooks/use-processos.round-trip.test.ts(1,38): error TS2307: Cannot find module 'vitest' ...
src/lib/cliente-documento-tipo.test.ts(1,38): error TS2307: Cannot find module 'vitest' ...
src/schemas/clientes.legacy-documento-tipo.test.ts(1,38): error TS2307: Cannot find module 'vitest' ...
```
`vitest` is not present anywhere in `web/package.json` (confirmed by grep). These 3 test
files were added in `f07fe89` (Phase 83-02) and `f825b2e` (Phase 74) — long before Phase 112 —
and were apparently written against an intended `vitest` setup that was never actually
installed/configured. Not touched by this plan. Recommend a dedicated fix (either add
`vitest` as a devDependency + config, or remove/relocate these 3 files if the intended test
runner changed) in a future phase.

## 2. Pre-existing `pnpm lint` errors/warnings, unrelated files (6 errors, 17 warnings)

Running `pnpm lint` at the whole-project level (first time dependencies were installed in
this worktree — `node_modules` did not exist until this plan's execution) surfaced 23
pre-existing problems, all in files this plan never touches:
`agenda/novo/page.tsx`, `clientes/[id]/ficha/page.tsx`, `clientes/[id]/page.tsx`,
`clientes/novo/page.tsx`, `documentos/novo/page.tsx`, `pareceres/nova/page.tsx`,
`processos/[id]/page.tsx`, `processos/[id]/termo-honorarios/page.tsx`,
`processos/novo/page.tsx`, `settings/page.tsx`, `user-profile-form.tsx`,
`dashboard-shell.tsx`, `data-table/data-table.tsx`, `user-menu.tsx`. Categories seen:
`react-hooks/set-state-in-effect` (6 errors — "Calling setState synchronously within an
effect"), `react-hooks/incompatible-library` (React Compiler skip warnings for
`form.watch()`/`useReactTable()`), `@next/next/no-img-element`, 2 unused-var warnings, 1
unused-eslint-disable-directive warning, 1 refs-during-render error
(`documentos/novo/page.tsx`). None reference any of this plan's 5 files. Likely surfaced now
because this is the first time `pnpm lint`/React Compiler's ESLint plugin has actually run
against a fully-installed `node_modules` in this specific worktree — the underlying code
issues themselves are pre-existing and out of this plan's scope. Recommend a dedicated
lint-debt cleanup phase.
