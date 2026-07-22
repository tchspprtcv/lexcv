# Phase 115 — Deferred Items

Out-of-scope findings surfaced during plan execution, logged per the executor's scope-boundary
rule (pre-existing issues unrelated to the current task's changes are not auto-fixed).

## From Plan 115-04

- **`web/src/app/(dashboard)/processos/novo/page.tsx:121`** — `@typescript-eslint/no-unused-vars`
  warning on `_estado` (`const { estado: _estado, ...intakeValues } = values;`). Pre-existing
  (confirmed via `git diff`: line 121 is outside every hunk touched by 115-04). The underscore-prefix
  convention signals "intentionally unused" but this repo's ESLint config has no
  `varsIgnorePattern`/`destructuredArrayIgnorePattern` for destructuring, so it still warns.
  Non-blocking (warning, not error); candidate one-line ESLint-config fix for a future phase.
- **`tsc --noEmit` (web/)** — 3 pre-existing `TS2307: Cannot find module 'vitest'` errors in
  `src/hooks/use-processos.round-trip.test.ts`, `src/lib/cliente-documento-tipo.test.ts`,
  `src/schemas/clientes.legacy-documento-tipo.test.ts`. Confirms STATE.md's existing note that
  `vitest` was never added as a devDependency even though these test files were authored against
  it (see Phase 112 "WR-05 deferred, needs vitest"). Unrelated to 115-04's 4 modified files (all
  zero-error). Not fixed — installing a new test-runner devDependency is an architectural/tooling
  decision outside a UI icon-only phase's scope.
