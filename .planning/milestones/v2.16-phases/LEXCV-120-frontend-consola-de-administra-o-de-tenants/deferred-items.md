# Deferred Items — Phase 120

## Plan 120-03

### Pre-existing, out-of-scope: `npx tsc --noEmit -p web/tsconfig.json` reports 3 unrelated errors

- **Category:** tooling / pre-existing environment gap (not a code defect, not introduced by this plan)
- **Found during:** Task 1 verify step (`cd web && npx tsc --noEmit -p tsconfig.json`)
- **Detail:** The raw command reports exactly 3 errors, all `TS2307: Cannot find module 'vitest'`, in:
  - `web/src/hooks/use-processos.round-trip.test.ts`
  - `web/src/lib/cliente-documento-tipo.test.ts`
  - `web/src/schemas/clientes.legacy-documento-tipo.test.ts`
- **Why out of scope:** These are "durable spec" files deliberately committed without `vitest` as a
  dependency, per explicit precedent recorded in `83-02-SUMMARY.md` ("Verificação de round-trip real
  implementada como script Node puro... em vez de instalar vitest — repo continua sem test runner
  (precedente Phase 74/82)") — i.e. the project has decided, across 3 prior phases (74, 82, 83), not
  to install a test runner yet, and these specs exist only for when one eventually is. None of the 3
  files were touched by, or are related to, this plan's task set (types, hooks, nav item).
- **Confirmation this plan introduces zero new type errors:** `pnpm build` (the authoritative
  gate per this plan's own `<verification>` section, item 3) runs Next.js's own TypeScript check and
  passes cleanly with exit 0 both before and after all 3 tasks — Next's type-check does not walk
  these orphaned spec files the same way a raw project-wide `tsc --noEmit` does. The literal 3-error
  count was identical before Task 1's edits, after Task 1, after Task 2, and after Task 3 (verified
  by re-running the raw command at each step) — i.e. this plan's own files (`auth.ts`,
  `platform-admin.ts`, `use-platform-admin.ts`, `dashboard-shell.tsx`) never appear in the error list.
- **Action:** Not fixed (installing `vitest` would reverse a standing, explicit project decision —
  out of scope for a data-layer plan). No action needed unless/until the project decides to adopt a
  frontend test runner.
