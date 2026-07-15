# Deferred Items — Phase 101 (Fundação — CLI Init e Design Tokens)

Out-of-scope discoveries logged during plan execution, per the executor's Scope Boundary rule (only auto-fix issues directly caused by the current task's changes; pre-existing issues in unrelated files are logged here, not fixed).

## 101-03: `tsc --noEmit` reports 3 pre-existing `vitest` module-resolution errors

- **Found during:** Task 3 (whole-project typecheck gate)
- **Files:** `web/src/hooks/use-processos.round-trip.test.ts`, `web/src/lib/cliente-documento-tipo.test.ts`, `web/src/schemas/clientes.legacy-documento-tipo.test.ts`
- **Error:** `TS2307: Cannot find module 'vitest' or its corresponding type declarations.`
- **Root cause:** These 3 test files (created in commit `80cb859`, Phase 97-02, v2.11 milestone — unrelated to v2.13's shadcn CLI foundation work) import from `vitest`, but `vitest` is not listed as a dependency anywhere in `web/package.json`. Pre-existing gap, not introduced or touched by this plan.
- **Why deferred, not fixed:** Out of scope per the Scope Boundary rule — this plan (101-03) only adds shadcn UI primitives; it does not touch test infrastructure or these 3 files. Fixing would require adding a `vitest` dependency and possibly a test-runner config, which is a larger, unrelated infrastructure decision.
- **Recommendation:** A future phase (or a dedicated test-infra task) should either add `vitest` as a dev dependency (if these tests are meant to run) or remove/relocate the files if they're stale artifacts. Until then, `tsc --noEmit` will always report these 3 errors regardless of any v2.13 UI work.
