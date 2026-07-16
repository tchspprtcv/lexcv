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
