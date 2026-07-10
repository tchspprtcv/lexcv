# Deferred Items — Phase 86

Out-of-scope discoveries logged during execution (per executor SCOPE BOUNDARY rule).
Not fixed — pre-existing, unrelated to the files this phase's plans modify.

## Plan 86-03

- **Pre-existing ESLint debt in `web/`** (discovered while running `pnpm lint` to verify the
  `permissions.ts` `KNOWN_SCOPES` addition — first `pnpm install` in this worktree, so this is
  likely the first time lint has been run against a clean install in a while): 5 errors, 17
  warnings across 12 files, none of which this plan touches. Top offenders: `@next/next/no-img-element`
  (8x, e.g. `src/components/shared/dashboard-shell.tsx`), `react-hooks/incompatible-library` (5x),
  `react-hooks/set-state-in-effect` (4x, e.g. `src/app/(dashboard)/clientes/[id]/page.tsx`),
  `@typescript-eslint/no-unused-vars` (2x), `react-hooks/refs` (1x). `web/src/lib/permissions.ts`
  itself has zero lint issues — the `KNOWN_SCOPES` addition is clean. Out of scope for this plan;
  not fixed. Candidate for a dedicated lint-debt cleanup pass in a future milestone.
